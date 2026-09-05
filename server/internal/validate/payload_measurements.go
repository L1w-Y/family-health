package validate

// 载荷 B 校验：批量测量（03 §4）
import "fmt"

var glucoseContexts = map[string]bool{
	"fasting": true, "before_meal": true, "after_meal_2h": true, "bedtime": true, "random": true,
}

func checkMeasurements(items []MeasurementItem, es *errors, ws *[]Warning) {
	if items == nil {
		es.add("payload.items", CodeRequired, "items 必填（1~500 条）")
		return
	}
	if len(items) == 0 {
		es.add("payload.items", CodeRequired, "items 至少 1 条")
	} else if len(items) > MaxMeasurements {
		es.addf("payload.items", CodeLimitExceeded, "measurements 批量超限（≤%d/批）", MaxMeasurements)
	}
	for i := range items {
		checkMeasurementItem(&items[i], fmt.Sprintf("payload.items[%d]", i), es, ws)
	}
}

func checkMeasurementItem(m *MeasurementItem, p string, es *errors, ws *[]Warning) {
	switch m.Type {
	case "bp":
		checkBP(m, p, es, ws)
	case "glucose":
		checkGlucose(m, p, es)
	case "":
		es.add(p+".type", CodeRequired, "type 必填")
	default:
		es.add(p+".type", CodeEnum, "type 须为 bp / glucose")
	}

	if m.MeasuredAt == "" {
		es.add(p+".measured_at", CodeRequired, "measured_at 必填")
	} else if t, _, ok := parseISOTime(m.MeasuredAt); !ok {
		es.add(p+".measured_at", CodeSchema, "measured_at 须为 ISO8601 带偏移（如 2026-08-15T07:30:00+08:00）")
	} else if t.After(now()) {
		es.add(p+".measured_at", CodeDateFuture, "不允许未来时间")
	}
	checkLen(es, p+".note", m.Note, 500, "note")
}

func checkBP(m *MeasurementItem, p string, es *errors, ws *[]Warning) {
	if m.Systolic == nil {
		es.add(p+".systolic", CodeRequired, "bp 必填 systolic")
	}
	if m.Diastolic == nil {
		es.add(p+".diastolic", CodeRequired, "bp 必填 diastolic")
	}
	sys, sysOK := checkIntRange(es, p+".systolic", m.Systolic, 40, 300, "systolic")
	dia, diaOK := checkIntRange(es, p+".diastolic", m.Diastolic, 40, 300, "diastolic")
	if m.HeartRateBpm != nil {
		checkIntRange(es, p+".heart_rate_bpm", m.HeartRateBpm, 20, 250, "heart_rate_bpm")
	}
	// 03 §4：高压>低压 否则非阻断警告（§6.2）
	if sysOK && diaOK && sys <= dia {
		*ws = append(*ws, Warning{Path: p, Code: WarnBpSysLteDia, Message: "收缩压不大于舒张压，请核对"})
	}
}

func checkGlucose(m *MeasurementItem, p string, es *errors) {
	if m.GlucoseMmol == nil {
		es.add(p+".glucose_mmol", CodeRequired, "glucose 必填 glucose_mmol")
	} else {
		checkNumRange(es, p+".glucose_mmol", m.GlucoseMmol, 0.5, 40, "glucose_mmol")
	}
	if m.GlucoseCtx == "" {
		es.add(p+".glucose_context", CodeRequired, "glucose 必填 glucose_context")
	} else if !glucoseContexts[m.GlucoseCtx] {
		es.add(p+".glucose_context", CodeEnum, "glucose_context 须为 fasting/before_meal/after_meal_2h/bedtime/random 之一")
	}
}
