package validate

import (
	"strings"
	"testing"
)

func measurementsBody(items string) string {
	return `{"format":"family-health-import","version":1,
	  "import_id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","profile_ref":{"name":"爷爷"},
	  "payload":{"type":"measurements","items":[` + items + `]}}`
}

func TestMeasurementsValid(t *testing.T) {
	body := measurementsBody(`
	  {"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":138,"diastolic":86,"heart_rate_bpm":72},
	  {"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":6.1,"glucose_context":"fasting"}`)
	_, es, ws, _ := parse(t, body)
	if len(es) != 0 || len(ws) != 0 {
		t.Fatalf("expect clean, got errors=%v warnings=%v", es, ws)
	}
}

func TestMeasurementsItemsRequired(t *testing.T) {
	body := `{"format":"family-health-import","version":1,
	  "import_id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","profile_ref":{"name":"爷爷"},
	  "payload":{"type":"measurements"}}`
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.items", CodeRequired) {
		t.Fatalf("expect items REQUIRED, got %v", es)
	}
	_, es, _, _ = parse(t, measurementsBody(""))
	if !hasError(es, "payload.items", CodeRequired) {
		t.Fatalf("expect items REQUIRED (empty), got %v", es)
	}
}

func TestMeasurementsLimit(t *testing.T) {
	items := make([]string, 501)
	for i := range items {
		items[i] = `{"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":6.1,"glucose_context":"random"}`
	}
	_, es, _, _ := parse(t, measurementsBody(strings.Join(items, ",")))
	if !hasError(es, "payload.items", CodeLimitExceeded) {
		t.Fatalf("expect LIMIT_EXCEEDED, got %v", es)
	}
}

func TestMeasurementTypeEnum(t *testing.T) {
	_, es, _, _ := parse(t, measurementsBody(
		`{"type":"weight","measured_at":"2026-08-15T07:30:00+08:00"}`))
	if !hasError(es, "payload.items[0].type", CodeEnum) {
		t.Fatalf("expect type ENUM, got %v", es)
	}
	_, es, _, _ = parse(t, measurementsBody(
		`{"measured_at":"2026-08-15T07:30:00+08:00"}`))
	if !hasError(es, "payload.items[0].type", CodeRequired) {
		t.Fatalf("expect type REQUIRED, got %v", es)
	}
}

func TestMeasuredAt(t *testing.T) {
	// 缺失
	_, es, _, _ := parse(t, measurementsBody(`{"type":"bp","systolic":120,"diastolic":80}`))
	if !hasError(es, "payload.items[0].measured_at", CodeRequired) {
		t.Fatalf("expect REQUIRED, got %v", es)
	}
	// 无偏移
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15 07:30","systolic":120,"diastolic":80}`))
	if !hasError(es, "payload.items[0].measured_at", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
	// 未来时间（now=2026-09-05T12:00Z）
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-09-05T20:00:01+08:00","systolic":120,"diastolic":80}`))
	if !hasError(es, "payload.items[0].measured_at", CodeDateFuture) {
		t.Fatalf("expect DATE_FUTURE, got %v", es)
	}
}

func TestBPRules(t *testing.T) {
	// 缺 systolic / diastolic
	_, es, _, _ := parse(t, measurementsBody(`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00"}`))
	if !hasError(es, "payload.items[0].systolic", CodeRequired) || !hasError(es, "payload.items[0].diastolic", CodeRequired) {
		t.Fatalf("expect systolic/diastolic REQUIRED, got %v", es)
	}
	// 越界（1380 手误，03 §4 区间语义）
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":1380,"diastolic":86}`))
	if !hasError(es, "payload.items[0].systolic", CodeValueRange) {
		t.Fatalf("expect VALUE_RANGE, got %v", es)
	}
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":39,"diastolic":86}`))
	if !hasError(es, "payload.items[0].systolic", CodeValueRange) {
		t.Fatalf("expect VALUE_RANGE low, got %v", es)
	}
	// 非整数
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":138.5,"diastolic":86}`))
	if !hasError(es, "payload.items[0].systolic", CodeSchema) {
		t.Fatalf("expect SCHEMA non-int, got %v", es)
	}
	// heart_rate 越界
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":138,"diastolic":86,"heart_rate_bpm":300}`))
	if !hasError(es, "payload.items[0].heart_rate_bpm", CodeValueRange) {
		t.Fatalf("expect heart_rate VALUE_RANGE, got %v", es)
	}
	// 高压<=低压：非阻断警告，不报错（03 §4 / §6.2）
	_, es, ws, _ := parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":80,"diastolic":86}`))
	if len(es) != 0 {
		t.Fatalf("warning must not block, got %v", es)
	}
	if len(ws) != 1 || ws[0].Code != WarnBpSysLteDia || ws[0].Path != "payload.items[0]" {
		t.Fatalf("expect BP_SYS_LTE_DIA warning, got %v", ws)
	}
}

func TestGlucoseRules(t *testing.T) {
	// 缺 glucose_mmol / context
	_, es, _, _ := parse(t, measurementsBody(`{"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00"}`))
	if !hasError(es, "payload.items[0].glucose_mmol", CodeRequired) ||
		!hasError(es, "payload.items[0].glucose_context", CodeRequired) {
		t.Fatalf("expect glucose REQUIRED x2, got %v", es)
	}
	// 越界
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":0.4,"glucose_context":"fasting"}`))
	if !hasError(es, "payload.items[0].glucose_mmol", CodeValueRange) {
		t.Fatalf("expect VALUE_RANGE low, got %v", es)
	}
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":40.1,"glucose_context":"fasting"}`))
	if !hasError(es, "payload.items[0].glucose_mmol", CodeValueRange) {
		t.Fatalf("expect VALUE_RANGE high, got %v", es)
	}
	// context 枚举
	_, es, _, _ = parse(t, measurementsBody(
		`{"type":"glucose","measured_at":"2026-08-15T07:35:00+08:00","glucose_mmol":6.1,"glucose_context":"after_lunch"}`))
	if !hasError(es, "payload.items[0].glucose_context", CodeEnum) {
		t.Fatalf("expect ENUM, got %v", es)
	}
}

func TestMeasurementNoteLength(t *testing.T) {
	_, es, _, _ := parse(t, measurementsBody(
		`{"type":"bp","measured_at":"2026-08-15T07:30:00+08:00","systolic":120,"diastolic":80,"note":"`+
			strings.Repeat("备", 501)+`"}`))
	if !hasError(es, "payload.items[0].note", CodeTooLong) {
		t.Fatalf("expect note TOO_LONG, got %v", es)
	}
}
