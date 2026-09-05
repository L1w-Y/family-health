package main

// 上行写入（02 §4.2/§4.3）：
// 表白名单 → Idempotency-Key 幂等 → 逐条校验 → 单事务写前取号 → 追加型只增 / 可改表整行 LWW。
import (
	"encoding/json"
	"fmt"
	"net/http"

	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

// 操作名（02 §4.2；delete = 软删墓碑，02 §1）
const (
	opInsert = "insert"
	opUpdate = "update"
	opDelete = "delete"
)

type batchResponse struct {
	OK      bool                  `json:"ok"`
	Results []model.WriteResponse `json:"results"`
}

// handlePush 上行：单体 WriteRequest 或数组（写入批次共用 Idempotency-Key，02 §4.2）
func handlePush(w http.ResponseWriter, r *http.Request, st store.Store, c Config) {
	dev := verify(w, r, st)
	if dev == nil {
		return
	}
	idemKey := r.Header.Get("Idempotency-Key")
	if idemKey == "" {
		httpx.Fail(w, http.StatusUnprocessableEntity, "REQUIRED", "Idempotency-Key 请求头必填（02 §4.2）")
		return
	}
	// 幂等重放：直接返回首次结果，不产生重复数据（02 §4.2）
	if resp, ok, err := st.GetIdempotentResponse(r.Context(), dev.ID, idemKey); err == nil && ok {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("X-Idempotent-Replay", "true")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(resp)
		return
	}

	body, err := httpx.ReadBody(w, r, validate.MaxBodyBytes)
	if err != nil {
		httpx.Fail(w, http.StatusRequestEntityTooLarge, "LIMIT_EXCEEDED", "请求体超限")
		return
	}
	reqs, single, err := parseWriteRequests(body)
	if err != nil {
		httpx.Fail(w, http.StatusBadRequest, "SCHEMA", err.Error())
		return
	}

	results := make([]model.WriteResponse, 0, len(reqs))
	var respBytes []byte
	txErr := st.InTx(r.Context(), func(tx store.Tx) error {
		for i := range reqs {
			res, err := applyWrite(r, tx, dev, &reqs[i], c)
			if err != nil {
				return fmt.Errorf("第 %d 条（%s %s）：%w", i, reqs[i].Table, reqs[i].Op, err)
			}
			results = append(results, *res)
		}
		if single {
			respBytes, _ = json.Marshal(results[0])
		} else {
			respBytes, _ = json.Marshal(batchResponse{OK: true, Results: results})
		}
		// 幂等结果与业务写入同事务提交（02 §4.2：重试/双击不产生重复数据）
		return tx.PutIdempotentResponse(r.Context(), dev.ID, idemKey, respBytes, c.NowMs())
	})
	if txErr != nil {
		httpx.Fail(w, http.StatusUnprocessableEntity, "WRITE_REJECTED", txErr.Error())
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(respBytes)
}

// parseWriteRequests 单体或数组两种形态
func parseWriteRequests(body []byte) ([]model.WriteRequest, bool, error) {
	var single model.WriteRequest
	if err := json.Unmarshal(body, &single); err == nil && single.Table != "" {
		return []model.WriteRequest{single}, true, nil
	}
	var batch []model.WriteRequest
	if err := json.Unmarshal(body, &batch); err != nil {
		return nil, false, fmt.Errorf("JSON 解析失败：%v", err)
	}
	if len(batch) == 0 {
		return nil, false, fmt.Errorf("写入批次为空")
	}
	for i := range batch {
		if batch[i].Table == "" {
			return nil, false, fmt.Errorf("第 %d 条缺 table", i)
		}
	}
	return batch, false, nil
}

// applyWrite 单条写入：白名单 + op 分派
func applyWrite(r *http.Request, tx store.Tx, dev *model.Device, req *model.WriteRequest, c Config) (*model.WriteResponse, error) {
	if !store.KnownTable(req.Table) {
		return nil, fmt.Errorf("表 %q 不在同步白名单（02 §4.2）", req.Table)
	}
	kind, _ := store.TableKindOf(req.Table)
	switch req.Op {
	case opInsert:
		return applyInsert(r, tx, dev, req, c)
	case opUpdate:
		if kind != store.KindMutable {
			return nil, fmt.Errorf("表 %q 为追加型，只增不改（02 §4.2）", req.Table)
		}
		return applyUpdate(r, tx, dev, req, c)
	case opDelete:
		return applyDelete(r, tx, dev, req, c)
	default:
		return nil, fmt.Errorf("op 须为 insert/update/delete")
	}
}

// rowID 行主键：客户端生成 UUID（02 §1 主键决策）
func rowID(req *model.WriteRequest) (string, error) {
	id, _ := req.Row["id"].(string)
	if id == "" || !validate.IsUUID(id) {
		return "", fmt.Errorf("row.id 必填且为客户端生成 UUID（02 §1）")
	}
	return id, nil
}

func applyInsert(r *http.Request, tx store.Tx, dev *model.Device, req *model.WriteRequest, c Config) (*model.WriteResponse, error) {
	id, err := rowID(req)
	if err != nil {
		return nil, err
	}
	if err := validateRowForWrite(r, tx, dev, req, true); err != nil {
		return nil, err
	}
	seq, err := tx.NextSeq(r.Context())
	if err != nil {
		return nil, err
	}
	now := c.NowMs()
	row := req.Row
	row["family_id"] = dev.FamilyID
	row["created_by"] = dev.ID
	row["source"] = sourceOf(dev)
	row["created_at"] = now
	row["updated_at"] = now
	row["deleted"] = false
	row["seq"] = seq
	if err := tx.InsertRow(r.Context(), req.Table, row); err != nil {
		return nil, err
	}
	return &model.WriteResponse{OK: true, ID: id, Seq: seq}, nil
}

func applyUpdate(r *http.Request, tx store.Tx, dev *model.Device, req *model.WriteRequest, c Config) (*model.WriteResponse, error) {
	id, err := rowID(req)
	if err != nil {
		return nil, err
	}
	existing, err := tx.GetRow(r.Context(), req.Table, dev.FamilyID, id)
	if err != nil {
		return nil, fmt.Errorf("更新目标不存在：%w", err)
	}
	if del, _ := existing["deleted"].(bool); del {
		return nil, fmt.Errorf("行已删除（墓碑不可改）")
	}
	if err := validateRowForWrite(r, tx, dev, req, false); err != nil {
		return nil, err
	}
	seq, err := tx.NextSeq(r.Context())
	if err != nil {
		return nil, err
	}
	// 整行 LWW（02 §4.3 不合并字段）；family/created_by/created_at 由 store 保留库内原值
	row := req.Row
	row["family_id"] = dev.FamilyID
	row["updated_at"] = c.NowMs()
	row["seq"] = seq
	if err := tx.UpdateRow(r.Context(), req.Table, row); err != nil {
		return nil, err
	}
	return &model.WriteResponse{OK: true, ID: id, Seq: seq}, nil
}

func applyDelete(r *http.Request, tx store.Tx, dev *model.Device, req *model.WriteRequest, c Config) (*model.WriteResponse, error) {
	id, err := rowID(req)
	if err != nil {
		return nil, err
	}
	seq, err := tx.NextSeq(r.Context())
	if err != nil {
		return nil, err
	}
	if err := tx.SoftDeleteRow(r.Context(), req.Table, dev.FamilyID, id, seq, c.NowMs()); err != nil {
		return nil, err
	}
	return &model.WriteResponse{OK: true, ID: id, Seq: seq}, nil
}

func sourceOf(d *model.Device) model.Source {
	if d.Type == model.DeviceAPI {
		return model.SourceAPI
	}
	return model.SourceApp
}
