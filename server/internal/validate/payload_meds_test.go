package validate

import (
	"strings"
	"testing"
)

func medsBody(payloadInner string) string {
	return `{"format":"family-health-import","version":1,
	  "import_id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","profile_ref":{"name":"爷爷"},
	  "payload":{"type":"medication_changes",` + payloadInner + `}}`
}

func validMedsInner() string {
	return `"effective_date":"2026-08-15","reason_note":"复查后医生调整",
	  "stop":[{"match":{"name":"缬沙坦"},"end_date":"2026-08-15"}],
	  "start":[{"category":"long_term","name":"氯沙坦钾片","dosage_text":"每日一次，每次 50mg",
	    "start_date":"2026-08-15","supersedes":"缬沙坦"}]`
}

func TestMedsValid(t *testing.T) {
	_, es, _, _ := parse(t, medsBody(validMedsInner()))
	if len(es) != 0 {
		t.Fatalf("expect valid, got %v", es)
	}
}

func TestMedsEffectiveDate(t *testing.T) {
	_, es, _, _ := parse(t, medsBody(`"stop":[{"match":{"name":"缬沙坦"}}]`))
	if !hasError(es, "payload.effective_date", CodeRequired) {
		t.Fatalf("expect REQUIRED, got %v", es)
	}
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-8-5","stop":[{"match":{"name":"缬沙坦"}}]`))
	if !hasError(es, "payload.effective_date", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
}

func TestMedsEmptyChange(t *testing.T) {
	_, es, _, _ := parse(t, medsBody(`"effective_date":"2026-08-15"`))
	if !hasError(es, "payload", CodeRequired) {
		t.Fatalf("expect stop/start REQUIRED, got %v", es)
	}
}

func TestMedsLinkedEventID(t *testing.T) {
	_, es, _, _ := parse(t, medsBody(`"effective_date":"2026-08-15","linked_event_id":"x",
	  "stop":[{"match":{"name":"缬沙坦"}}]`))
	if !hasError(es, "payload.linked_event_id", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
	// "$event" 语法合法（存在性由导入层判定）
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","linked_event_id":"$event",
	  "stop":[{"match":{"name":"缬沙坦"}}]`))
	if len(es) != 0 {
		t.Fatalf("$event should pass syntax check, got %v", es)
	}
}

func TestMedsReasonNoteLength(t *testing.T) {
	_, es, _, _ := parse(t, medsBody(`"effective_date":"2026-08-15",
	  "reason_note":"`+strings.Repeat("由", 501)+`","stop":[{"match":{"name":"缬沙坦"}}]`))
	if !hasError(es, "payload.reason_note", CodeTooLong) {
		t.Fatalf("expect TOO_LONG, got %v", es)
	}
}

func TestMedsStopMatch(t *testing.T) {
	// match 缺失
	_, es, _, _ := parse(t, medsBody(`"effective_date":"2026-08-15","stop":[{}]`))
	if !hasError(es, "payload.stop[0].match", CodeRequired) {
		t.Fatalf("expect match REQUIRED, got %v", es)
	}
	// id/name 同给
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15",
	  "stop":[{"match":{"id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","name":"缬沙坦"}}]`))
	if !hasError(es, "payload.stop[0].match", CodeSchema) {
		t.Fatalf("expect match SCHEMA, got %v", es)
	}
	// id 非法
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","stop":[{"match":{"id":"x"}}]`))
	if !hasError(es, "payload.stop[0].match.id", CodeSchema) {
		t.Fatalf("expect match.id SCHEMA, got %v", es)
	}
	// end_date 非法
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15",
	  "stop":[{"match":{"name":"缬沙坦"},"end_date":"15/08/2026"}]`))
	if !hasError(es, "payload.stop[0].end_date", CodeSchema) {
		t.Fatalf("expect end_date SCHEMA, got %v", es)
	}
}

func TestMedsStartFields(t *testing.T) {
	// name 必填
	_, es, _, _ := parse(t, medsBody(`"effective_date":"2026-08-15","start":[{}]`))
	if !hasError(es, "payload.start[0].name", CodeRequired) {
		t.Fatalf("expect name REQUIRED, got %v", es)
	}
	// category 枚举
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","category":"forever"}]`))
	if !hasError(es, "payload.start[0].category", CodeEnum) {
		t.Fatalf("expect category ENUM, got %v", es)
	}
	// med_kind 枚举
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","med_kind":"herbal"}]`))
	if !hasError(es, "payload.start[0].med_kind", CodeEnum) {
		t.Fatalf("expect med_kind ENUM, got %v", es)
	}
	// 结构化一次用量必须为正数且有单位
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","dose_qty":0,"dose_unit":"片"}]`))
	if !hasError(es, "payload.start[0].dose_qty", CodeValueRange) {
		t.Fatalf("expect dose_qty VALUE_RANGE, got %v", es)
	}
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","dose_qty":1}]`))
	if !hasError(es, "payload.start[0].dose_unit", CodeRequired) {
		t.Fatalf("expect dose_unit REQUIRED, got %v", es)
	}
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","dose_unit":"片"}]`))
	if !hasError(es, "payload.start[0].dose_qty", CodeRequired) {
		t.Fatalf("expect dose_qty REQUIRED, got %v", es)
	}
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{
	  "name":"药","dose_qty":1,"dose_unit":"片","dose_times_per_day":3,"dose_slots":["morning","evening"]}]`))
	if !hasError(es, "payload.start[0].dose_times_per_day", CodeSchema) {
		t.Fatalf("expect dose times/slots SCHEMA, got %v", es)
	}
	// dose_slots 枚举与去重
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15",
	  "start":[{"name":"药","dose_slots":["morning","midnight"]}]`))
	if !hasError(es, "payload.start[0].dose_slots[1]", CodeEnum) {
		t.Fatalf("expect dose_slots ENUM, got %v", es)
	}
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15",
	  "start":[{"name":"药","dose_slots":["morning","morning"]}]`))
	if !hasError(es, "payload.start[0].dose_slots[1]", CodeSchema) {
		t.Fatalf("expect dose_slots dup SCHEMA, got %v", es)
	}
	// end_date 早于 start_date
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15",
	  "start":[{"name":"药","category":"temporary","start_date":"2026-08-15","end_date":"2026-08-01"}]`))
	if !hasError(es, "payload.start[0].end_date", CodeDateOrder) {
		t.Fatalf("expect end_date DATE_ORDER, got %v", es)
	}
	// start_date 非法
	_, es, _, _ = parse(t, medsBody(`"effective_date":"2026-08-15","start":[{"name":"药","start_date":"2026-02-30"}]`))
	if !hasError(es, "payload.start[0].start_date", CodeSchema) {
		t.Fatalf("expect start_date SCHEMA, got %v", es)
	}
}
