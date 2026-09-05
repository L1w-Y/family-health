package validate

import (
	"strings"
	"testing"
	"time"
)

func init() {
	// 固定"当前时间"，未来日期用例才可重复（03 §3 容忍 +1 天时区误差）
	now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }
}

func validEnvelopeBody() string {
	return `{
	  "format": "family-health-import",
	  "version": 1,
	  "import_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
	  "profile_ref": {"name": "爷爷"},
	  "payload": {"type": "measurements", "items": [
	    {"type": "bp", "measured_at": "2026-08-15T07:30:00+08:00", "systolic": 138, "diastolic": 86}
	  ]}
	}`
}

func parse(t *testing.T, body string) (*Envelope, []Error, []Warning, error) {
	t.Helper()
	env, es, ws, err := ParseAndValidate([]byte(body))
	if err != nil {
		t.Fatalf("unexpected go-error: %v", err)
	}
	return env, es, ws, nil
}

func hasError(es []Error, path, code string) bool {
	for _, e := range es {
		if e.Code == code && (path == "" || e.Path == path) {
			return true
		}
	}
	return false
}

func TestEnvelopeValid(t *testing.T) {
	env, es, _, _ := parse(t, validEnvelopeBody())
	if len(es) != 0 {
		t.Fatalf("expect no errors, got %v", es)
	}
	if env.Format != FormatName || env.Version != 1 || env.Profile.Name != "爷爷" {
		t.Fatalf("envelope parsed wrong: %+v", env)
	}
}

func TestEnvelopeEmptyBody(t *testing.T) {
	_, es, _, _ := parse(t, "")
	if !hasError(es, "", CodeRequired) {
		t.Fatalf("expect REQUIRED, got %v", es)
	}
}

func TestEnvelopeOversize(t *testing.T) {
	body := strings.Repeat(" ", MaxBodyBytes+1)
	_, es, _, _ := parse(t, body)
	if !hasError(es, "", CodeLimitExceeded) {
		t.Fatalf("expect LIMIT_EXCEEDED, got %v", es)
	}
}

func TestEnvelopeBadJSON(t *testing.T) {
	_, es, _, _ := parse(t, "{not json")
	if !hasError(es, "", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
}

func TestEnvelopeTrailingGarbage(t *testing.T) {
	_, es, _, _ := parse(t, validEnvelopeBody()+" {}")
	if !hasError(es, "", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
}

func TestEnvelopeFormat(t *testing.T) {
	mutate := func(s string) string {
		return strings.Replace(validEnvelopeBody(), `"family-health-import"`, s, 1)
	}
	_, es, _, _ := parse(t, mutate(`"other-format"`))
	if !hasError(es, "format", CodeSchema) {
		t.Fatalf("expect format SCHEMA, got %v", es)
	}
	_, es, _, _ = parse(t, mutate(`""`))
	if !hasError(es, "format", CodeRequired) {
		t.Fatalf("expect format REQUIRED, got %v", es)
	}
}

func TestEnvelopeVersion(t *testing.T) {
	_, es, _, _ := parse(t, strings.Replace(validEnvelopeBody(), `"version": 1`, `"version": 2`, 1))
	if !hasError(es, "version", CodeSchema) {
		t.Fatalf("expect version SCHEMA, got %v", es)
	}
	_, es, _, _ = parse(t, strings.Replace(validEnvelopeBody(), `"version": 1,`, ``, 1))
	if !hasError(es, "version", CodeRequired) {
		t.Fatalf("expect version REQUIRED, got %v", es)
	}
}

func TestEnvelopeImportID(t *testing.T) {
	_, es, _, _ := parse(t, strings.Replace(validEnvelopeBody(),
		`"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"`, `"not-a-uuid"`, 1))
	if !hasError(es, "import_id", CodeSchema) {
		t.Fatalf("expect import_id SCHEMA, got %v", es)
	}
}

func TestEnvelopeProfileRef(t *testing.T) {
	// 缺失
	_, es, _, _ := parse(t, strings.Replace(validEnvelopeBody(), `{"name": "爷爷"}`, `{}`, 1))
	if !hasError(es, "profile_ref", CodeRequired) {
		t.Fatalf("expect profile_ref REQUIRED, got %v", es)
	}
	// 两者同给
	_, es, _, _ = parse(t, strings.Replace(validEnvelopeBody(), `{"name": "爷爷"}`,
		`{"id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", "name": "爷爷"}`, 1))
	if !hasError(es, "profile_ref", CodeSchema) {
		t.Fatalf("expect profile_ref SCHEMA, got %v", es)
	}
	// id 非法
	_, es, _, _ = parse(t, strings.Replace(validEnvelopeBody(), `{"name": "爷爷"}`, `{"id": "x"}`, 1))
	if !hasError(es, "profile_ref.id", CodeSchema) {
		t.Fatalf("expect profile_ref.id SCHEMA, got %v", es)
	}
}

func TestPayloadTypeEnum(t *testing.T) {
	_, es, _, _ := parse(t, strings.Replace(validEnvelopeBody(), `"type": "measurements"`, `"type": "unknown"`, 1))
	if !hasError(es, "payload.type", CodeEnum) {
		t.Fatalf("expect payload.type ENUM, got %v", es)
	}
	_, es, _, _ = parse(t, strings.Replace(validEnvelopeBody(), `"type": "measurements"`, `"type": ""`, 1))
	if !hasError(es, "payload.type", CodeRequired) {
		t.Fatalf("expect payload.type REQUIRED, got %v", es)
	}
}

func TestUnknownFieldsIgnored(t *testing.T) {
	// 03 §1 原则 3：未知字段忽略
	body := strings.Replace(validEnvelopeBody(), `"version": 1,`, `"version": 1, "future_field": {"x": 1},`, 1)
	_, es, _, _ := parse(t, body)
	if len(es) != 0 {
		t.Fatalf("unknown fields must be ignored, got %v", es)
	}
}
