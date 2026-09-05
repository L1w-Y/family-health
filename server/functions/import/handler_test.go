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

const testToken = "tok-import-1"

func testServer(t *testing.T) (http.Handler, *store.MemoryStore) {
	t.Helper()
	st := store.NewMemoryStore()
	st.SeedFamily(model.Family{ID: "fam-1", Name: "家"})
	if err := st.CreateDevice(t.Context(), &model.Device{
		Base:        model.Base{ID: "dev-api", FamilyID: "fam-1", CreatedBy: "dev-api", Source: model.SourceAPI, CreatedAt: 1, UpdatedAt: 1},
		Type:        model.DeviceAPI,
		DisplayName: "导入脚本",
		TokenHash:   authn.Hash(testToken),
	}); err != nil {
		t.Fatal(err)
	}
	// 预置档案：爷爷
	seedRow(t, st, "profiles", model.JSONMap{
		"name": "爷爷", "relation": "爷爷", "gender": "male",
	}, "11111111-1111-4111-8111-111111111111")
	return NewHandler(st, nil), st
}

// seedRow 直接落一行（绕过 sync，测试前置数据）
func seedRow(t *testing.T, st *store.MemoryStore, table string, biz model.JSONMap, id string) {
	t.Helper()
	row := model.JSONMap{
		"id": id, "family_id": "fam-1", "created_by": "dev-api", "source": "api",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
	}
	for k, v := range biz {
		row[k] = v
	}
	if biz["profile_id"] == nil && table != "profiles" {
		row["profile_id"] = "11111111-1111-4111-8111-111111111111"
	}
	err := st.InTx(t.Context(), func(tx store.Tx) error {
		seq, _ := tx.NextSeq(t.Context())
		row["seq"] = float64(seq)
		return tx.InsertRow(t.Context(), table, row)
	})
	if err != nil {
		t.Fatal(err)
	}
}

func doImport(t *testing.T, h http.Handler, body, idemKey string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/api/v1/import", strings.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+testToken)
	if idemKey != "" {
		req.Header.Set("Idempotency-Key", idemKey)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func envelope(importID, payload string) string {
	return `{"format":"family-health-import","version":1,"import_id":"` + importID + `",
	  "profile_ref":{"name":"爷爷"},"payload":` + payload + `}`
}

// 03 §7 完整示例
const sampleCheckupPayload = `{
  "type": "checkup_event",
  "event": {
    "checkup_date": "2026-08-15",
    "hospital": "市人民医院",
    "department": "肾内科",
    "note": "尿蛋白控制一般，医生嘱低盐饮食，三个月后复查",
    "next_checkup_date": "2026-11-15",
    "reports": [
      { "title": "尿常规", "indicators": [
        { "item_name": "尿蛋白", "value": "1+", "reference_range": "阴性" },
        { "item_name": "尿微量白蛋白", "value": 156.3, "unit": "mg/L", "reference_range": "<30" },
        { "item_name": "尿肌酐", "value": 8.82, "unit": "mmol/L" },
        { "item_name": "尿微量白蛋白/肌酐比值(ACR)", "value": 201.5, "unit": "mg/g", "reference_range": "<30" }
      ]},
      { "title": "肾功能+血糖", "indicators": [
        { "item_name": "血肌酐", "value": 132, "unit": "μmol/L", "reference_range": "41~81" },
        { "item_name": "尿素氮", "value": 9.1, "unit": "mmol/L", "reference_range": "2.9~8.2" },
        { "item_name": "糖化血红蛋白", "value": 7.2, "unit": "%", "reference_range": "4.0~6.0" },
        { "item_name": "空腹血糖", "value": 7.8, "unit": "mmol/L", "reference_range": "3.9~6.1" },
        { "item_name": "血钙", "value": 2.31, "unit": "mmol/L", "reference_range": "2.11~2.52" }
      ]},
      { "title": "肾脏B超", "conclusion_text": "双肾大小形态正常，实质回声增强，右肾囊肿约 8mm。建议定期复查。", "indicators": [] }
    ]
  }
}`

func tableRows(t *testing.T, st *store.MemoryStore, table string) []model.Change {
	t.Helper()
	changes, err := st.ListChanges(t.Context(), "fam-1", 0, 1000)
	if err != nil {
		t.Fatal(err)
	}
	var out []model.Change
	for _, ch := range changes {
		if ch.Table == table {
			out = append(out, ch)
		}
	}
	return out
}

func TestImportCheckupFullExample(t *testing.T) {
	h, st := testServer(t)
	// 别名归一：ACR 别名含原文指标名（02 §3.8）
	seedRow(t, st, "watch_items", model.JSONMap{
		"canonical_name": "ACR", "aliases": []any{"尿微量白蛋白/肌酐比值(ACR)"}, "sort_order": float64(1),
	}, "watch-1")

	body := envelope("f47ac10b-58cc-4372-a567-0e02b2c3d479", sampleCheckupPayload)
	rec := doImport(t, h, body, "f47ac10b-58cc-4372-a567-0e02b2c3d479")
	if rec.Code != http.StatusOK {
		t.Fatalf("import failed: %d %s", rec.Code, rec.Body.String())
	}
	var resp importResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if !resp.OK || len(resp.Created.EventIDs) != 1 || resp.Created.ReportCount != 3 || resp.Created.IndicatorCount != 9 {
		t.Fatalf("bad summary: %+v", resp.Created)
	}
	// 行落库：1 事件 + 3 报告 + 9 指标
	if len(tableRows(t, st, "checkup_events")) != 1 || len(tableRows(t, st, "reports")) != 3 ||
		len(tableRows(t, st, "indicator_items")) != 9 {
		t.Fatal("row counts wrong")
	}
	// note 与 medication_changes_note 各自落列、互不混入（02 §3.5 专列）
	// §7 示例未携带 medication_changes_note → 该列为空
	evRow := tableRows(t, st, "checkup_events")[0].Row
	if evRow["note"] != "尿蛋白控制一般，医生嘱低盐饮食，三个月后复查" ||
		(evRow["medication_changes_note"] != nil && evRow["medication_changes_note"] != "") {
		t.Fatalf("event note columns wrong: %v", evRow)
	}
	// 指标细节：定量/定性归类 + sort_order + canonical 预解析
	inds := tableRows(t, st, "indicator_items")
	var acr, protein model.JSONMap
	for _, r := range inds {
		switch r.Row["item_name"] {
		case "尿微量白蛋白/肌酐比值(ACR)":
			acr = r.Row
		case "尿蛋白":
			protein = r.Row
		}
	}
	if acr == nil || acr["canonical_name"] != "ACR" || acr["value_numeric"] != 201.5 {
		t.Fatalf("ACR row wrong: %v", acr)
	}
	if protein == nil || protein["value_text"] != "1+" || protein["value_numeric"] != nil {
		t.Fatalf("protein row wrong: %v", protein)
	}
	// 报告缺省日期 = 事件日（03 §3.1）
	reps := tableRows(t, st, "reports")
	for _, r := range reps {
		if r.Row["report_date"] != "2026-08-15" {
			t.Fatalf("report_date default wrong: %v", r.Row)
		}
	}
	// source=api（api 设备导入）
	events := tableRows(t, st, "checkup_events")
	if events[0].Row["source"] != "api" || events[0].Row["profile_id"] != "11111111-1111-4111-8111-111111111111" {
		t.Fatalf("event base fields wrong: %v", events[0].Row)
	}
}

func TestImportIdempotentReplay(t *testing.T) {
	h, st := testServer(t)
	body := envelope("f47ac10b-58cc-4372-a567-0e02b2c3d479", sampleCheckupPayload)
	rec1 := doImport(t, h, body, "f47ac10b-58cc-4372-a567-0e02b2c3d479")
	rec2 := doImport(t, h, body, "f47ac10b-58cc-4372-a567-0e02b2c3d479")
	if rec1.Code != http.StatusOK || rec2.Code != http.StatusOK {
		t.Fatalf("codes %d %d", rec1.Code, rec2.Code)
	}
	if rec2.Header().Get("X-Idempotent-Replay") != "true" || rec1.Body.String() != rec2.Body.String() {
		t.Fatal("replay must return first response")
	}
	if len(tableRows(t, st, "checkup_events")) != 1 {
		t.Fatal("duplicate import created rows")
	}
	// 同 id 不同体 → 拒绝（03 §6.3 DUPLICATE_IMPORT_ID_FORMAT）
	other := envelope("f47ac10b-58cc-4372-a567-0e02b2c3d479",
		`{"type":"measurements","items":[{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":120,"diastolic":80}]}`)
	rec3 := doImport(t, h, other, "f47ac10b-58cc-4372-a567-0e02b2c3d479")
	if rec3.Code != http.StatusUnprocessableEntity || !strings.Contains(rec3.Body.String(), "DUPLICATE_IMPORT_ID_FORMAT") {
		t.Fatalf("expect DUPLICATE_IMPORT_ID_FORMAT: %d %s", rec3.Code, rec3.Body.String())
	}
}

func TestImportDryRun(t *testing.T) {
	h, st := testServer(t)
	body := strings.Replace(
		envelope("aaaaaaa1-1111-4111-8111-111111111111", sampleCheckupPayload),
		`"profile_ref"`, `"dry_run":true,"profile_ref"`, 1)
	rec := doImport(t, h, body, "aaaaaaa1-1111-4111-8111-111111111111")
	if rec.Code != http.StatusOK {
		t.Fatalf("dry_run failed: %d %s", rec.Code, rec.Body.String())
	}
	var resp importResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if !resp.OK || !resp.DryRun || resp.Created.IndicatorCount != 9 {
		t.Fatalf("bad dry_run resp: %+v", resp)
	}
	if len(tableRows(t, st, "checkup_events")) != 0 {
		t.Fatal("dry_run must not write")
	}
	// dry_run 不占用 import_id：随后正式提交同 id 成功
	body2 := strings.Replace(body, `"dry_run":true,`, ``, 1)
	rec2 := doImport(t, h, body2, "aaaaaaa1-1111-4111-8111-111111111111")
	if rec2.Code != http.StatusOK {
		t.Fatalf("real import after dry_run failed: %d %s", rec2.Code, rec2.Body.String())
	}
}

func TestImportProfileResolution(t *testing.T) {
	h, st := testServer(t)
	// 同名第二个档案 → 歧义
	seedRow(t, st, "profiles", model.JSONMap{"name": "爷爷", "relation": "外公", "gender": "male"}, "22222222-2222-4222-8222-222222222222")
	payload := `{"type":"measurements","items":[{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":120,"diastolic":80}]}`
	rec := doImport(t, h, envelope("bbbbbbb1-1111-4111-8111-111111111111", payload), "bbbbbbb1-1111-4111-8111-111111111111")
	if rec.Code != http.StatusUnprocessableEntity || !strings.Contains(rec.Body.String(), "PROFILE_AMBIGUOUS") {
		t.Fatalf("expect PROFILE_AMBIGUOUS: %d %s", rec.Code, rec.Body.String())
	}
	// 用 id 精确指定 → 成功
	body := strings.Replace(envelope("bbbbbbb2-1111-4111-8111-111111111111", payload),
		`{"name":"爷爷"}`, `{"id":"11111111-1111-4111-8111-111111111111"}`, 1)
	rec = doImport(t, h, body, "bbbbbbb2-1111-4111-8111-111111111111")
	if rec.Code != http.StatusOK {
		t.Fatalf("by id failed: %d %s", rec.Code, rec.Body.String())
	}
	// 不存在的档案
	body = strings.Replace(envelope("bbbbbbb3-1111-4111-8111-111111111111", payload),
		`{"name":"爷爷"}`, `{"name":"不存在的人"}`, 1)
	rec = doImport(t, h, body, "bbbbbbb3-1111-4111-8111-111111111111")
	if rec.Code != http.StatusUnprocessableEntity || !strings.Contains(rec.Body.String(), "PROFILE_NOT_FOUND") {
		t.Fatalf("expect PROFILE_NOT_FOUND: %d %s", rec.Code, rec.Body.String())
	}
}

func TestImportIdempotencyKeyMismatch(t *testing.T) {
	h, _ := testServer(t)
	payload := `{"type":"measurements","items":[{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":120,"diastolic":80}]}`
	rec := doImport(t, h, envelope("ccccccc1-1111-4111-8111-111111111111", payload), "other-key")
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("expect 422, got %d", rec.Code)
	}
}

func TestImportMeasurementsWarnings(t *testing.T) {
	h, st := testServer(t)
	payload := `{"type":"measurements","items":[
	  {"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":80,"diastolic":86},
	  {"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":6.1,"glucose_context":"fasting"}]}`
	rec := doImport(t, h, envelope("ddddddd1-1111-4111-8111-111111111111", payload), "ddddddd1-1111-4111-8111-111111111111")
	if rec.Code != http.StatusOK {
		t.Fatalf("warnings must not block: %d %s", rec.Code, rec.Body.String())
	}
	var resp importResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if len(resp.Warnings) != 1 || resp.Warnings[0].Code != "BP_SYS_LTE_DIA" || resp.Created.MeasurementCount != 2 {
		t.Fatalf("bad warnings: %+v", resp)
	}
	// 时区偏移换算：+08:00 → 480 分钟
	ms := tableRows(t, st, "measurements")
	if ms[0].Row["tz_offset_min"] != float64(480) {
		t.Fatalf("tz offset wrong: %v", ms[0].Row)
	}
}

func TestImportValidationFailure(t *testing.T) {
	h, _ := testServer(t)
	payload := `{"type":"measurements","items":[{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":1380,"diastolic":86}]}`
	rec := doImport(t, h, envelope("eeeeeee1-1111-4111-8111-111111111111", payload), "eeeeeee1-1111-4111-8111-111111111111")
	if rec.Code != http.StatusUnprocessableEntity || !strings.Contains(rec.Body.String(), "VALUE_RANGE") {
		t.Fatalf("expect 422 VALUE_RANGE: %d %s", rec.Code, rec.Body.String())
	}
}

// 携带 medication_changes_note 时落专列（02 §3.5），不并入 note
func TestImportMedicationChangesNoteColumn(t *testing.T) {
	h, st := testServer(t)
	payload := strings.Replace(sampleCheckupPayload,
		`"next_checkup_date": "2026-11-15",`,
		`"next_checkup_date": "2026-11-15", "medication_changes_note": "停用缬沙坦，改服氯沙坦 50mg 每日一次",`, 1)
	body := envelope("99999991-1111-4111-8111-111111111111", payload)
	rec := doImport(t, h, body, "99999991-1111-4111-8111-111111111111")
	if rec.Code != http.StatusOK {
		t.Fatalf("import failed: %d %s", rec.Code, rec.Body.String())
	}
	evRow := tableRows(t, st, "checkup_events")[0].Row
	if evRow["medication_changes_note"] != "停用缬沙坦，改服氯沙坦 50mg 每日一次" {
		t.Fatalf("medication_changes_note not landed: %v", evRow)
	}
	note, _ := evRow["note"].(string)
	if strings.Contains(note, "用药备注") || strings.Contains(note, "缬沙坦") {
		t.Fatalf("note must not be merged: %v", note)
	}
}

func TestImportMedChanges(t *testing.T) {
	h, st := testServer(t)
	// 预置进行中条目：缬沙坦
	seedRow(t, st, "medication_items", model.JSONMap{
		"category": "long_term", "med_kind": "western", "name": "缬沙坦",
		"dosage_text": "每日一次", "start_date": "2026-01-01",
	}, "med-old")
	payload := `{"type":"medication_changes","effective_date":"2026-08-15","reason_note":"复查后医生调整",
	  "stop":[{"match":{"name":"缬沙坦"}}],
	  "start":[{"name":"氯沙坦钾片","dosage_text":"每日一次，每次 50mg","supersedes":"缬沙坦","dose_slots":["morning"]}]}`
	rec := doImport(t, h, envelope("fffffff1-1111-4111-8111-111111111111", payload), "fffffff1-1111-4111-8111-111111111111")
	if rec.Code != http.StatusOK {
		t.Fatalf("med changes failed: %d %s", rec.Code, rec.Body.String())
	}
	var resp importResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if resp.Created.StoppedCount != 1 || resp.Created.StartedCount != 1 || len(resp.Created.ChangeIDs) != 1 {
		t.Fatalf("bad summary: %+v", resp.Created)
	}
	// 旧条目已写 end_date = effective_date（缺省）
	old, err := st.GetMedicationItem(t.Context(), "fam-1", "med-old")
	if err != nil || old.EndDate == nil || *old.EndDate != "2026-08-15" {
		t.Fatalf("stop failed: %v %+v", err, old)
	}
	// 新条目：supersedes 链 + change_id 归属（01 §5.2）
	items := tableRows(t, st, "medication_items")
	var newItem model.JSONMap
	for _, r := range items {
		if r.Row["name"] == "氯沙坦钾片" {
			newItem = r.Row
		}
	}
	if newItem == nil || newItem["supersedes_id"] != "med-old" || newItem["change_id"] != resp.Created.ChangeIDs[0] {
		t.Fatalf("chain wrong: %v", newItem)
	}
	// 缺省值：category=long_term、med_kind=western、start_date=effective_date
	if newItem["category"] != "long_term" || newItem["med_kind"] != "western" || newItem["start_date"] != "2026-08-15" {
		t.Fatalf("defaults wrong: %v", newItem)
	}
	// med_changes.note = reason_note
	changes := tableRows(t, st, "med_changes")
	if len(changes) != 1 || changes[0].Row["note"] != "复查后医生调整" {
		t.Fatalf("med_changes wrong: %v", changes)
	}
}

func TestImportMedStopAmbiguous(t *testing.T) {
	h, st := testServer(t)
	seedRow(t, st, "medication_items", model.JSONMap{"category": "long_term", "name": "缬沙坦", "start_date": "2026-01-01"}, "m1")
	seedRow(t, st, "medication_items", model.JSONMap{"category": "long_term", "name": "缬沙坦", "start_date": "2026-02-01"}, "m2")
	payload := `{"type":"medication_changes","effective_date":"2026-08-15","stop":[{"match":{"name":"缬沙坦"}}]}`
	rec := doImport(t, h, envelope("88888881-1111-4111-8111-111111111111", payload), "88888881-1111-4111-8111-111111111111")
	if rec.Code != http.StatusUnprocessableEntity || !strings.Contains(rec.Body.String(), "MATCH_AMBIGUOUS") {
		t.Fatalf("expect MATCH_AMBIGUOUS: %d %s", rec.Code, rec.Body.String())
	}
	// 原子性：无写入
	if len(tableRows(t, st, "med_changes")) != 0 {
		t.Fatal("rejected import must not write")
	}
}

func TestImportUnauthorized(t *testing.T) {
	h, _ := testServer(t)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/import", strings.NewReader("{}"))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expect 401, got %d", rec.Code)
	}
}
