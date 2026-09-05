package main

// 导入管线（03 §2 HTTP 通道）：
// 限长 → validate.ParseAndValidate → 幂等键一致 → profile 解析 → import_id 幂等 →
// dry_run 短路 → 单事务写入 → §6.1 响应。
import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"time"

	"familyhealth/server/internal/authn"
	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

// Config 运行配置（测试可注入时钟）
type Config struct {
	NowMs func() int64
}

func (c *Config) withDefaults() Config {
	out := *c
	if out.NowMs == nil {
		out.NowMs = func() int64 { return time.Now().UnixMilli() }
	}
	return out
}

// createdSummary §6.1 created 字段（按载荷类型填充对应计数）
type createdSummary struct {
	EventIDs         []string `json:"event_ids,omitempty"`
	ReportCount      int      `json:"report_count,omitempty"`
	IndicatorCount   int      `json:"indicator_count,omitempty"`
	MeasurementCount int      `json:"measurement_count,omitempty"`
	ChangeIDs        []string `json:"change_ids,omitempty"`
	StartedCount     int      `json:"started_count,omitempty"`
	StoppedCount     int      `json:"stopped_count,omitempty"`
}

// importResponse §6.1 成功响应（含 dry_run）
type importResponse struct {
	OK       bool               `json:"ok"`
	ImportID string             `json:"import_id"`
	DryRun   bool               `json:"dry_run,omitempty"`
	Created  createdSummary     `json:"created"`
	Warnings []validate.Warning `json:"warnings"`
}

// NewHandler POST /api/v1/import（03 §2；App 粘贴通道复用同一管线）
func NewHandler(st store.Store, cfg *Config) http.Handler {
	if cfg == nil {
		cfg = &Config{}
	}
	c := cfg.withDefaults()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/import", func(w http.ResponseWriter, r *http.Request) {
		handleImport(w, r, st, c)
	})
	mux.HandleFunc("POST /", func(w http.ResponseWriter, r *http.Request) {
		handleImport(w, r, st, c)
	})
	return mux
}

func handleImport(w http.ResponseWriter, r *http.Request, st store.Store, c Config) {
	ctx := r.Context()
	// member（App 粘贴）与 api（外部脚本）同管线（03 §2）
	dev, err := authn.Verify(ctx, st, httpx.Bearer(r), model.DeviceMember, model.DeviceAPI)
	if err != nil {
		httpx.Fail(w, http.StatusUnauthorized, "UNAUTHORIZED", "无效或已吊销的令牌")
		return
	}
	body, err := httpx.ReadBody(nil, r, validate.MaxBodyBytes)
	if err != nil {
		httpx.Fail(w, http.StatusUnprocessableEntity, validate.CodeLimitExceeded, "单请求体积超限（≤512KB，03 §6.4）")
		return
	}
	env, verrs, warnings, _ := validate.ParseAndValidate(body)
	if len(verrs) > 0 {
		writeValidationErrors(w, verrs)
		return
	}
	// 幂等键一致（03 §2：Header 与体内 import_id 必须一致）
	if key := r.Header.Get("Idempotency-Key"); key != env.ImportID {
		httpx.Fail(w, http.StatusUnprocessableEntity, validate.CodeSchema,
			"Idempotency-Key 与体内 import_id 必须一致（03 §2）")
		return
	}

	// profile 解析（03 §2：id/name，歧义拒绝）
	profile, perr := resolveProfile(ctx, st, dev.FamilyID, env.Profile)
	if perr != nil {
		httpx.Fail(w, http.StatusUnprocessableEntity, perr.Code, perr.Message)
		return
	}

	// import_id 幂等（03 §1 原则 2）
	payloadHash := sha256hex(body)
	if rec, err := st.GetImportRecord(ctx, dev.FamilyID, env.ImportID); err == nil {
		if rec.PayloadHash != payloadHash {
			httpx.Fail(w, http.StatusUnprocessableEntity, "DUPLICATE_IMPORT_ID_FORMAT",
				"同一 import_id 提交了不同内容，拒绝（03 §6.3）")
			return
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("X-Idempotent-Replay", "true")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(rec.Response)
		return
	}

	// 生成写入计划（含 stop/supersedes/附件等读侧解析）
	plan, perrs, err := buildPlan(ctx, st, dev, profile, env, c)
	if err != nil {
		httpx.Fail(w, http.StatusInternalServerError, "INTERNAL", "导入处理失败")
		return
	}
	if len(perrs) > 0 {
		writeValidationErrors(w, perrs)
		return
	}

	resp := importResponse{
		OK: true, ImportID: env.ImportID, DryRun: env.DryRun,
		Created: plan.summary, Warnings: warnings,
	}
	if resp.Warnings == nil {
		resp.Warnings = []validate.Warning{}
	}
	respBytes, _ := json.Marshal(resp)

	// dry_run：只校验返回结果，不写库（03 §2）
	if env.DryRun {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respBytes)
		return
	}

	// 单事务整体写入（04 §4.3：失败即回滚，不写半截）
	txErr := st.InTx(ctx, func(tx store.Tx) error {
		if err := applyPlan(ctx, tx, plan, c.NowMs()); err != nil {
			return err
		}
		return tx.PutImportRecord(ctx, &store.ImportRecord{
			ImportID: env.ImportID, FamilyID: dev.FamilyID, DeviceID: dev.ID,
			PayloadHash: payloadHash, Response: respBytes, CreatedAt: c.NowMs(),
		})
	})
	if txErr != nil {
		httpx.Fail(w, http.StatusInternalServerError, "INTERNAL", "写入失败："+txErr.Error())
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(respBytes)
}

// resolveProfile 档案解析（03 §2：按名字在本家庭内解析，不唯一或不存在 → 拒绝）
func resolveProfile(ctx context.Context, st store.Store, familyID string, ref validate.ProfileRef) (*model.Profile, *validate.Error) {
	if ref.ID != "" {
		p, err := st.GetProfile(ctx, familyID, ref.ID)
		if err != nil {
			return nil, &validate.Error{Path: "profile_ref.id", Code: validate.CodeProfileNotFound, Message: "档案不存在"}
		}
		return p, nil
	}
	ps, err := st.FindProfilesByName(ctx, familyID, ref.Name)
	if err != nil || len(ps) == 0 {
		return nil, &validate.Error{Path: "profile_ref.name", Code: validate.CodeProfileNotFound, Message: "档案不存在：" + ref.Name}
	}
	if len(ps) > 1 {
		return nil, &validate.Error{Path: "profile_ref.name", Code: validate.CodeProfileAmbiguous, Message: "档案名不唯一，请改用 id：" + ref.Name}
	}
	return ps[0], nil
}

func writeValidationErrors(w http.ResponseWriter, es []validate.Error) {
	items := make([]httpx.ErrorItem, 0, len(es))
	for _, e := range es {
		items = append(items, httpx.ErrorItem{Path: e.Path, Code: e.Code, Message: e.Message})
	}
	httpx.FailErrors(w, http.StatusUnprocessableEntity, items)
}

func sha256hex(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}
