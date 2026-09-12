package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"familyhealth/server/internal/authn"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

const testToken = "tok-sync-1"

func testServer(t *testing.T) (http.Handler, *store.MemoryStore) {
	t.Helper()
	st := store.NewMemoryStore()
	st.SeedFamily(model.Family{ID: "fam-1", Name: "家"})
	err := st.CreateDevice(t.Context(), &model.Device{
		Base:        model.Base{ID: "dev-1", FamilyID: "fam-1", CreatedBy: "dev-1", Source: model.SourceApp, CreatedAt: 1, UpdatedAt: 1},
		Type:        model.DeviceMember,
		DisplayName: "爸爸",
		TokenHash:   authn.Hash(testToken),
	})
	if err != nil {
		t.Fatal(err)
	}
	return NewHandler(st, nil), st
}

func do(t *testing.T, h http.Handler, method, path, body, idemKey string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+testToken)
	if idemKey != "" {
		req.Header.Set("Idempotency-Key", idemKey)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func pull(t *testing.T, h http.Handler, since string) model.PullResponse {
	t.Helper()
	rec := do(t, h, http.MethodGet, "/sync?since="+since+"&limit=500", "", "")
	if rec.Code != http.StatusOK {
		t.Fatalf("pull failed: %d %s", rec.Code, rec.Body.String())
	}
	var resp model.PullResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatal(err)
	}
	return resp
}

func TestPullEmpty(t *testing.T) {
	h, _ := testServer(t)
	resp := pull(t, h, "0")
	if len(resp.Changes) != 0 || resp.HasMore || resp.Next != 0 {
		t.Fatalf("expect empty: %+v", resp)
	}
}

func TestPushInsertAndPull(t *testing.T) {
	h, _ := testServer(t)
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"measurements","op":"insert","row":{
	  "id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","profile_id":"p1","type":"bp",
	  "measured_at":1755253800000,"tz_offset_min":480,"systolic":138,"diastolic":86}}`, "key-1")
	if rec.Code != http.StatusOK {
		t.Fatalf("push failed: %d %s", rec.Code, rec.Body.String())
	}
	var wr model.WriteResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &wr)
	if !wr.OK || wr.Seq != 1 {
		t.Fatalf("bad write response: %+v", wr)
	}
	resp := pull(t, h, "0")
	if len(resp.Changes) != 1 || resp.Changes[0].Table != "measurements" {
		t.Fatalf("pull missing row: %+v", resp)
	}
	row := resp.Changes[0].Row
	if row["family_id"] != "fam-1" || row["created_by"] != "dev-1" || row["source"] != "app" {
		t.Fatalf("server fields not stamped: %v", row)
	}
	if resp.Next != 1 {
		t.Fatalf("next should be 1: %+v", resp)
	}
	// since=1 后为空
	resp = pull(t, h, "1")
	if len(resp.Changes) != 0 {
		t.Fatalf("incremental should be empty: %+v", resp)
	}
}

func TestPushIdempotentReplay(t *testing.T) {
	h, _ := testServer(t)
	body := `{"table":"measurements","op":"insert","row":{
	  "id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","profile_id":"p1","type":"glucose",
	  "measured_at":1755253800000,"tz_offset_min":480,"glucose_mmol":6.1,"glucose_context":"fasting"}}`
	rec1 := do(t, h, http.MethodPost, "/sync", body, "key-dup")
	rec2 := do(t, h, http.MethodPost, "/sync", body, "key-dup")
	if rec1.Code != http.StatusOK || rec2.Code != http.StatusOK {
		t.Fatalf("codes: %d %d", rec1.Code, rec2.Code)
	}
	if rec2.Header().Get("X-Idempotent-Replay") != "true" {
		t.Fatal("expect replay header")
	}
	if rec1.Body.String() != rec2.Body.String() {
		t.Fatalf("replay must return first result:\n%s\n%s", rec1.Body.String(), rec2.Body.String())
	}
	resp := pull(t, h, "0")
	if len(resp.Changes) != 1 {
		t.Fatalf("duplicate submission created rows: %+v", resp)
	}
}

func TestPushRequiresIdempotencyKey(t *testing.T) {
	h, _ := testServer(t)
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"insert","row":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"x"}}`, "")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("expect 422, got %d", rec.Code)
	}
}

func TestPushWhitelistAndOps(t *testing.T) {
	h, _ := testServer(t)
	// 表白名单
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"devices","op":"insert","row":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"}}`, "k-a")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("devices must be rejected: %d", rec.Code)
	}
	// 追加型禁改
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"measurements","op":"update","row":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"}}`, "k-b")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("append-only update must be rejected: %d", rec.Code)
	}
	// 非 UUID 主键
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"insert","row":{"id":"x","name":"a","gender":"male"}}`, "k-c")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("non-uuid id must be rejected: %d", rec.Code)
	}
}

func TestPushUpdateLWWAndDelete(t *testing.T) {
	h, _ := testServer(t)
	do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"insert","row":{
	  "id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"爷爷","relation":"爷爷","gender":"male","notes":"旧"}}`, "k-1")
	// 整行 LWW：notes 未给 → 置空
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"update","row":{
	  "id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"爷爷","relation":"祖父","gender":"male"}}`, "k-2")
	if rec.Code != http.StatusOK {
		t.Fatalf("update failed: %d %s", rec.Code, rec.Body.String())
	}
	// 同一行 insert+update 后在增量流中只有最新版本（seq 刷新）
	resp := pull(t, h, "0")
	if len(resp.Changes) != 1 {
		t.Fatalf("expect 1 change (latest row version): %+v", resp)
	}
	row := resp.Changes[0].Row
	if row["relation"] != "祖父" || row["notes"] != nil {
		t.Fatalf("LWW whole-row wrong: %v", row)
	}
	// 软删 → 墓碑
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"delete","row":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"}}`, "k-3")
	if rec.Code != http.StatusOK {
		t.Fatalf("delete failed: %d", rec.Code)
	}
	resp = pull(t, h, "0")
	last := resp.Changes[len(resp.Changes)-1].Row
	if last["deleted"] != true {
		t.Fatalf("expect tombstone: %v", last)
	}
	// 墓碑不可改
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"profiles","op":"update","row":{
	  "id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"x","gender":"male"}}`, "k-4")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("update tombstone must be rejected: %d", rec.Code)
	}
}

func TestPushBatchAtomic(t *testing.T) {
	h, _ := testServer(t)
	// 批次内第二条非法 → 整批回滚
	rec := do(t, h, http.MethodPost, "/sync", `[
	  {"table":"profiles","op":"insert","row":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"爷爷","gender":"male"}},
	  {"table":"profiles","op":"insert","row":{"id":"not-uuid","name":"奶奶","gender":"female"}}
	]`, "k-batch")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("expect 422, got %d", rec.Code)
	}
	resp := pull(t, h, "0")
	if len(resp.Changes) != 0 {
		t.Fatalf("batch must be atomic: %+v", resp)
	}
}

func TestMedSupersedesChain(t *testing.T) {
	h, _ := testServer(t)
	// 旧条目
	do(t, h, http.MethodPost, "/sync", `{"table":"medication_items","op":"insert","row":{
	  "id":"11111111-1111-4111-8111-111111111111","profile_id":"p1","category":"long_term",
	  "med_kind":"western","name":"缬沙坦","start_date":"2026-01-01"}}`, "m-1")
	// supersedes 指向不存在条目 → 拒绝（01 §5.2 规则 2）
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"medication_items","op":"insert","row":{
	  "id":"22222222-2222-4222-8222-222222222222","profile_id":"p1","category":"long_term",
	  "med_kind":"western","name":"氯沙坦","start_date":"2026-08-15",
	  "supersedes_id":"33333333-3333-4333-8333-333333333333"}}`, "m-2")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("broken chain must be rejected: %d", rec.Code)
	}
	// 正确改量链 → 放行
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"medication_items","op":"insert","row":{
	  "id":"22222222-2222-4222-8222-222222222222","profile_id":"p1","category":"long_term",
	  "med_kind":"western","name":"氯沙坦","start_date":"2026-08-15",
	  "supersedes_id":"11111111-1111-4111-8111-111111111111"}}`, "m-3")
	if rec.Code != http.StatusOK {
		t.Fatalf("valid chain rejected: %d %s", rec.Code, rec.Body.String())
	}
}

func TestDailyMedicationStockFollowsMedicationSlots(t *testing.T) {
	h, _ := testServer(t)
	medicationID := "44444444-4444-4444-8444-444444444444"
	rec := do(t, h, http.MethodPost, "/sync", `{"table":"medication_items","op":"insert","row":{
	  "id":"`+medicationID+`","profile_id":"p1","category":"long_term","med_kind":"western",
	  "name":"白领片","start_date":"2026-01-01","dose_qty":4,"dose_unit":"颗","dose_times_per_day":3,
	  "dose_slots":["morning","noon","evening"]}}`, "stock-med")
	if rec.Code != http.StatusOK {
		t.Fatalf("structured medication rejected: %d %s", rec.Code, rec.Body.String())
	}
	rec = do(t, h, http.MethodPost, "/sync", `{"table":"medication_items","op":"insert","row":{
	  "id":"77777777-7777-4777-8777-777777777777","profile_id":"p1","category":"long_term","med_kind":"western",
	  "name":"次数冲突药","start_date":"2026-01-01","dose_qty":1,"dose_unit":"片","dose_times_per_day":3,
	  "dose_slots":["morning","evening"]}}`, "stock-med-mismatch")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("dose times/slots mismatch must be rejected: %d %s", rec.Code, rec.Body.String())
	}

	rec = do(t, h, http.MethodPost, "/sync", `{"table":"daily_med_items","op":"insert","row":{
	  "id":"55555555-5555-4555-8555-555555555555","profile_id":"p1",
	  "medication_item_id":"`+medicationID+`","stock_by_slot":{"morning":12,"noon":12,"evening":12},
	  "stock_counted_at":1789056000000,"tz_offset_min":480}}`, "stock-valid")
	if rec.Code != http.StatusOK {
		t.Fatalf("valid per-slot stock rejected: %d %s", rec.Code, rec.Body.String())
	}

	rec = do(t, h, http.MethodPost, "/sync", `{"table":"daily_med_items","op":"insert","row":{
	  "id":"66666666-6666-4666-8666-666666666666","profile_id":"p1",
	  "medication_item_id":"`+medicationID+`","stock_by_slot":{"bedtime":12},
	  "stock_counted_at":1789056000000,"tz_offset_min":480}}`, "stock-invalid-slot")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("stock outside medication slots must be rejected: %d %s", rec.Code, rec.Body.String())
	}
}

func TestUnauthorized(t *testing.T) {
	h, _ := testServer(t)
	req := httptest.NewRequest(http.MethodGet, "/sync?since=0", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expect 401, got %d", rec.Code)
	}
}
