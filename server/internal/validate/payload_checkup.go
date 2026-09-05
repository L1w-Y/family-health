package validate

// 载荷 A 校验：复查事件 / 报告 / 指标（03 §3）
import "fmt"

// ClassifyIndicatorValue 定量/定性归类（03 §3.2：number=定量；string 可无损解析为数字按定量，
// 否则按定性存档）。导入写入路径与校验共用同一判定，防止两处漂移。
func ClassifyIndicatorValue(v any) (numeric *float64, text *string) {
	switch val := v.(type) {
	case float64:
		f := val
		return &f, nil
	case string:
		if f, ok := numericString(val); ok {
			return &f, nil
		}
		s := val
		return nil, &s
	}
	return nil, nil
}

func checkEventPayload(ev *EventPayload, es *errors) {
	if ev == nil {
		es.add("payload.event", CodeRequired, "checkup_event 载荷缺少 event 对象")
		return
	}
	p := "payload.event"

	var checkupDate = ""
	if ev.CheckupDate == "" {
		es.add(p+".checkup_date", CodeRequired, "checkup_date 必填")
	} else if t, ok := checkDate(es, p+".checkup_date", ev.CheckupDate); ok {
		checkNotFuture(es, p+".checkup_date", t)
		checkupDate = ev.CheckupDate
	}
	checkLen(es, p+".hospital", ev.Hospital, 50, "hospital")
	checkLen(es, p+".department", ev.Department, 50, "department")
	checkLen(es, p+".note", ev.Note, 2000, "note")
	checkLen(es, p+".medication_changes_note", ev.MedicationChangesNote, 2000, "medication_changes_note")

	if ev.NextCheckupDate != "" {
		if nt, ok := checkDate(es, p+".next_checkup_date", ev.NextCheckupDate); ok && checkupDate != "" {
			ct, _ := parseDate(checkupDate)
			if !nt.After(ct) {
				es.add(p+".next_checkup_date", CodeDateOrder, "next_checkup_date 须晚于 checkup_date")
			}
		}
	}

	if ev.Reports == nil {
		es.add(p+".reports", CodeRequired, "reports 必填（1~30 份，纯事件无报告不支持）")
		return
	}
	if len(ev.Reports) == 0 {
		es.add(p+".reports", CodeRequired, "reports 至少 1 份")
	} else if len(ev.Reports) > MaxReportsPerEvent {
		es.addf(p+".reports", CodeLimitExceeded, "reports 数量超限（≤%d/事件）", MaxReportsPerEvent)
	}
	for i := range ev.Reports {
		checkReport(&ev.Reports[i], fmt.Sprintf("%s.reports[%d]", p, i), checkupDate, es)
	}
}

func checkReport(r *ReportPayload, p, checkupDate string, es *errors) {
	if r.Title == "" {
		es.add(p+".title", CodeRequired, "title 必填")
	} else {
		checkLen(es, p+".title", r.Title, 100, "title")
	}
	if r.ReportDate != "" {
		checkDate(es, p+".report_date", r.ReportDate)
	} else if checkupDate == "" {
		// 缺省 = 事件 checkup_date（03 §3.1）；事件日期非法时无法缺省
		es.add(p+".report_date", CodeRequired, "report_date 缺省依赖合法的 checkup_date")
	}
	checkLen(es, p+".conclusion_text", r.ConclusionText, 5000, "conclusion_text")
	for j, aid := range r.AttachmentIDs {
		if !IsUUID(aid) {
			es.add(fmt.Sprintf("%s.attachment_ids[%d]", p, j), CodeSchema, "附件 id 须为 UUID")
		}
	}
	if len(r.Indicators) > MaxIndicatorsPerRep {
		es.addf(p+".indicators", CodeLimitExceeded, "indicators 数量超限（≤%d/报告）", MaxIndicatorsPerRep)
	}
	for j := range r.Indicators {
		checkIndicator(&r.Indicators[j], fmt.Sprintf("%s.indicators[%d]", p, j), es)
	}
}

func checkIndicator(in *IndicatorPayload, p string, es *errors) {
	if in.ItemName == "" {
		es.add(p+".item_name", CodeRequired, "item_name 必填")
	} else {
		checkLen(es, p+".item_name", in.ItemName, 100, "item_name")
	}
	switch v := in.Value.(type) {
	case nil:
		es.add(p+".value", CodeRequired, "value 必填")
	case float64:
		if v != v || v > 1e308 || v < -1e308 {
			es.add(p+".value", CodeSchema, "value 须为有限数字")
		}
	case string:
		if v == "" {
			es.add(p+".value", CodeRequired, "value 必填")
		} else if _, isNum := numericString(v); !isNum {
			// 定性值（可解析为数字的按定量处理，03 §3.2）
			checkLen(es, p+".value", v, 50, "value")
		}
	default:
		es.add(p+".value", CodeSchema, "value 须为 number 或 string")
	}
	checkLen(es, p+".unit", in.Unit, 30, "unit")
	checkLen(es, p+".reference_range", in.ReferenceRange, 50, "reference_range")
}
