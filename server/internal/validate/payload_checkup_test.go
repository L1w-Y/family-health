package validate

import (
	"strings"
	"testing"
)

// checkupBody 以 03 §7 完整示例为基底的合法载荷
func checkupBody() string {
	return `{
	  "format": "family-health-import",
	  "version": 1,
	  "import_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
	  "profile_ref": { "name": "爷爷" },
	  "payload": {
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
	          { "item_name": "尿微量白蛋白", "value": 156.3, "unit": "mg/L", "reference_range": "<30" }
	        ]},
	        { "title": "肾脏B超", "conclusion_text": "双肾大小形态正常。", "indicators": [] }
	      ]
	    }
	  }
	}`
}

func mutateCheckup(t *testing.T, old, new string) string {
	t.Helper()
	b := checkupBody()
	if !strings.Contains(b, old) {
		t.Fatalf("anchor not found: %q", old)
	}
	return strings.Replace(b, old, new, 1)
}

func TestCheckupValid(t *testing.T) {
	_, es, _, _ := parse(t, checkupBody())
	if len(es) != 0 {
		t.Fatalf("expect valid, got %v", es)
	}
}

func TestCheckupMissingEvent(t *testing.T) {
	body := `{"format":"family-health-import","version":1,"import_id":"f47ac10b-58cc-4372-a567-0e02b2c3d479",
	  "profile_ref":{"name":"爷爷"},"payload":{"type":"checkup_event"}}`
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event", CodeRequired) {
		t.Fatalf("expect event REQUIRED, got %v", es)
	}
}

func TestCheckupDate(t *testing.T) {
	// 缺失
	_, es, _, _ := parse(t, mutateCheckup(t, `"checkup_date": "2026-08-15"`, `"checkup_date": ""`))
	if !hasError(es, "payload.event.checkup_date", CodeRequired) {
		t.Fatalf("expect REQUIRED, got %v", es)
	}
	// 非真实日期
	_, es, _, _ = parse(t, mutateCheckup(t, `"2026-08-15"`, `"2026-02-30"`))
	if !hasError(es, "payload.event.checkup_date", CodeSchema) {
		t.Fatalf("expect SCHEMA, got %v", es)
	}
	// 未来日期（now=2026-09-05，容忍 +1 天）
	_, es, _, _ = parse(t, mutateCheckup(t, `"2026-08-15"`, `"2026-09-07"`))
	if !hasError(es, "payload.event.checkup_date", CodeDateFuture) {
		t.Fatalf("expect DATE_FUTURE, got %v", es)
	}
	// +1 天容忍边界内：放行
	_, es, _, _ = parse(t, mutateCheckup(t, `"2026-08-15"`, `"2026-09-06"`))
	if hasError(es, "payload.event.checkup_date", CodeDateFuture) {
		t.Fatalf("+1d tolerance should pass, got %v", es)
	}
}

func TestCheckupStringLengths(t *testing.T) {
	long51 := strings.Repeat("医", 51)
	_, es, _, _ := parse(t, mutateCheckup(t, `"市人民医院"`, `"`+long51+`"`))
	if !hasError(es, "payload.event.hospital", CodeTooLong) {
		t.Fatalf("expect hospital TOO_LONG, got %v", es)
	}
	long2001 := strings.Repeat("嘱", 2001)
	_, es, _, _ = parse(t, mutateCheckup(t, `"尿蛋白控制一般，医生嘱低盐饮食，三个月后复查"`, `"`+long2001+`"`))
	if !hasError(es, "payload.event.note", CodeTooLong) {
		t.Fatalf("expect note TOO_LONG, got %v", es)
	}
	long101 := strings.Repeat("常", 101)
	_, es, _, _ = parse(t, mutateCheckup(t, `"尿常规"`, `"`+long101+`"`))
	if !hasError(es, "payload.event.reports[0].title", CodeTooLong) {
		t.Fatalf("expect title TOO_LONG, got %v", es)
	}
	long5001 := strings.Repeat("所", 5001)
	_, es, _, _ = parse(t, mutateCheckup(t, `"双肾大小形态正常。"`, `"`+long5001+`"`))
	if !hasError(es, "payload.event.reports[1].conclusion_text", CodeTooLong) {
		t.Fatalf("expect conclusion_text TOO_LONG, got %v", es)
	}
}

func TestNextCheckupDateOrder(t *testing.T) {
	_, es, _, _ := parse(t, mutateCheckup(t, `"2026-11-15"`, `"2026-08-15"`))
	if !hasError(es, "payload.event.next_checkup_date", CodeDateOrder) {
		t.Fatalf("expect DATE_ORDER, got %v", es)
	}
}

func TestReportsLimits(t *testing.T) {
	// reports 缺失
	body := strings.Replace(checkupBody(), "\n\t      \"reports\": [", "\n\t      \"_reports\": [", 1)
	if body == checkupBody() {
		// 锚点缩进不确定时改用字段名替换
		body = strings.Replace(checkupBody(), `"reports": [`, `"_reports": [`, 1)
	}
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event.reports", CodeRequired) {
		t.Fatalf("expect reports REQUIRED, got %v", es)
	}
	// 31 份超限
	reps := make([]string, 31)
	for i := range reps {
		reps[i] = `{"title":"尿常规"}`
	}
	body = strings.Replace(checkupBody(), `"reports": [`, `"reports": [`+strings.Join(reps, ",")+`,`, 1)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports", CodeLimitExceeded) {
		t.Fatalf("expect reports LIMIT_EXCEEDED, got %v", es)
	}
}

func TestReportTitleRequired(t *testing.T) {
	_, es, _, _ := parse(t, mutateCheckup(t, `"title": "尿常规"`, `"title": ""`))
	if !hasError(es, "payload.event.reports[0].title", CodeRequired) {
		t.Fatalf("expect title REQUIRED, got %v", es)
	}
}

func TestReportDateInvalid(t *testing.T) {
	body := mutateCheckup(t, `"title": "尿常规"`, `"title": "尿常规", "report_date": "2026-13-01"`)
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event.reports[0].report_date", CodeSchema) {
		t.Fatalf("expect report_date SCHEMA, got %v", es)
	}
}

func TestReportAttachmentIDFormat(t *testing.T) {
	body := mutateCheckup(t, `"title": "尿常规"`, `"title": "尿常规", "attachment_ids": ["bad-id"]`)
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event.reports[0].attachment_ids[0]", CodeSchema) {
		t.Fatalf("expect attachment_ids SCHEMA, got %v", es)
	}
}

func TestIndicatorsLimit(t *testing.T) {
	inds := make([]string, 201)
	for i := range inds {
		inds[i] = `{"item_name":"尿蛋白","value":"阴性"}`
	}
	body := mutateCheckup(t, `"indicators": [`, `"indicators": [`+strings.Join(inds, ",")+`,`)
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators", CodeLimitExceeded) {
		t.Fatalf("expect indicators LIMIT_EXCEEDED, got %v", es)
	}
}

func TestIndicatorFields(t *testing.T) {
	// item_name 缺失
	body := mutateCheckup(t, `{ "item_name": "尿蛋白", "value": "1+", "reference_range": "阴性" }`,
		`{ "item_name": "", "value": "1+" }`)
	_, es, _, _ := parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[0].item_name", CodeRequired) {
		t.Fatalf("expect item_name REQUIRED, got %v", es)
	}
	// value 缺失
	body = mutateCheckup(t, `{ "item_name": "尿蛋白", "value": "1+", "reference_range": "阴性" }`,
		`{ "item_name": "尿蛋白" }`)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[0].value", CodeRequired) {
		t.Fatalf("expect value REQUIRED, got %v", es)
	}
	// 定性值超长（≤50 字）
	body = mutateCheckup(t, `"value": "1+"`, `"value": "`+strings.Repeat("阳", 51)+`"`)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[0].value", CodeTooLong) {
		t.Fatalf("expect value TOO_LONG, got %v", es)
	}
	// unit 超长（≤30）
	body = mutateCheckup(t, `"unit": "mg/L"`, `"unit": "`+strings.Repeat("u", 31)+`"`)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[1].unit", CodeTooLong) {
		t.Fatalf("expect unit TOO_LONG, got %v", es)
	}
	// reference_range 超长（≤50）
	body = mutateCheckup(t, `"reference_range": "<30"`, `"reference_range": "`+strings.Repeat("<", 51)+`"`)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[1].reference_range", CodeTooLong) {
		t.Fatalf("expect reference_range TOO_LONG, got %v", es)
	}
	// value 类型非法（bool）
	body = mutateCheckup(t, `"value": "1+"`, `"value": true`)
	_, es, _, _ = parse(t, body)
	if !hasError(es, "payload.event.reports[0].indicators[0].value", CodeSchema) {
		t.Fatalf("expect value SCHEMA, got %v", es)
	}
}

func TestIndicatorNumericStringCoercion(t *testing.T) {
	// 03 §3.2：可无损解析为数字的 string 按定量处理 → 合法
	body := mutateCheckup(t, `"value": 156.3`, `"value": "156.30"`)
	_, es, _, _ := parse(t, body)
	if len(es) != 0 {
		t.Fatalf("numeric string should be quantitative, got %v", es)
	}
}

// TestFullExample 03 §7 完整示例必须通过（契约锚点用例）
func TestFullExample(t *testing.T) {
	full := `{
	  "format": "family-health-import",
	  "version": 1,
	  "import_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
	  "profile_ref": { "name": "爷爷" },
	  "payload": {
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
	        { "title": "肾脏B超",
	          "conclusion_text": "双肾大小形态正常，实质回声增强，右肾囊肿约 8mm。建议定期复查。",
	          "indicators": [] }
	      ]
	    }
	  }
	}`
	env, es, ws, _ := parse(t, full)
	if len(es) != 0 {
		t.Fatalf("03 §7 full example must pass, got %v", es)
	}
	if len(ws) != 0 {
		t.Fatalf("expect no warnings, got %v", ws)
	}
	if env.Payload.Event == nil || len(env.Payload.Event.Reports) != 3 {
		t.Fatalf("event payload parsed wrong: %+v", env.Payload.Event)
	}
}
