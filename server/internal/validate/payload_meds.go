package validate

// 载荷 C 校验：用药变更（03 §5）
import "fmt"

var doseSlotSet = map[string]bool{
	"morning": true, "noon": true, "evening": true, "bedtime": true,
}

// LinkedEventRefBatch 同批载荷 A 新建事件的占位引用（03 §5）
const LinkedEventRefBatch = "$event"

func checkMedChanges(cs *MedChangePayload, es *errors) {
	if cs == nil {
		es.add("payload", CodeRequired, "medication_changes 载荷缺少变更字段")
		return
	}
	p := "payload"
	if cs.EffectiveDate == "" {
		es.add(p+".effective_date", CodeRequired, "effective_date 必填")
	} else {
		checkDate(es, p+".effective_date", cs.EffectiveDate)
	}
	if cs.LinkedEventID != "" && cs.LinkedEventID != LinkedEventRefBatch && !IsUUID(cs.LinkedEventID) {
		es.add(p+".linked_event_id", CodeSchema, "linked_event_id 须为 UUID 或 \"$event\"")
	}
	checkLen(es, p+".reason_note", cs.ReasonNote, 500, "reason_note")

	if len(cs.Stop) == 0 && len(cs.Start) == 0 {
		es.add(p, CodeRequired, "stop 与 start 至少其一非空")
	}
	for i := range cs.Stop {
		checkStopItem(&cs.Stop[i], fmt.Sprintf("%s.stop[%d]", p, i), es)
	}
	for i := range cs.Start {
		checkStartItem(&cs.Start[i], fmt.Sprintf("%s.start[%d]", p, i), es)
	}
}

func checkStopItem(s *StopItem, p string, es *errors) {
	if s.Match.ID == "" && s.Match.Name == "" {
		es.add(p+".match", CodeRequired, "match 必填（id 或 name 二选一）")
	} else if !oneOf(s.Match.ID, s.Match.Name) {
		es.add(p+".match", CodeSchema, "match 的 id 与 name 只能二选一")
	} else if s.Match.ID != "" && !IsUUID(s.Match.ID) {
		es.add(p+".match.id", CodeSchema, "match.id 须为 UUID")
	}
	if s.EndDate != "" {
		checkDate(es, p+".end_date", s.EndDate)
	}
}

func checkStartItem(s *StartItem, p string, es *errors) {
	if s.Category != "" && s.Category != "long_term" && s.Category != "temporary" {
		es.add(p+".category", CodeEnum, "category 须为 long_term / temporary")
	}
	if s.Kind != "" && s.Kind != "western" && s.Kind != "tcm" {
		es.add(p+".med_kind", CodeEnum, "med_kind 须为 western / tcm")
	}
	if s.Name == "" {
		es.add(p+".name", CodeRequired, "name 必填")
	}
	structuredDose := s.DoseQty != nil || s.DoseUnit != "" || s.DoseTimes != nil
	if s.DoseQty != nil && *s.DoseQty <= 0 {
		es.add(p+".dose_qty", CodeValueRange, "dose_qty 须大于 0")
	}
	if structuredDose && s.DoseQty == nil {
		es.add(p+".dose_qty", CodeRequired, "结构化用量须提供 dose_qty")
	}
	if structuredDose && s.DoseUnit == "" {
		es.add(p+".dose_unit", CodeRequired, "结构化用量须提供 dose_unit")
	}
	if structuredDose && s.DoseTimes == nil {
		es.add(p+".dose_times_per_day", CodeRequired, "结构化用量须提供 dose_times_per_day")
	}
	if s.DoseTimes != nil && (*s.DoseTimes < 1 || *s.DoseTimes > 4) {
		es.add(p+".dose_times_per_day", CodeValueRange, "dose_times_per_day 须为 1–4")
	}
	seen := map[string]bool{}
	for j, slot := range s.DoseSlots {
		if !doseSlotSet[slot] {
			es.add(fmt.Sprintf("%s.dose_slots[%d]", p, j), CodeEnum, "dose_slots 须为 morning/noon/evening/bedtime 子集")
		} else if seen[slot] {
			es.add(fmt.Sprintf("%s.dose_slots[%d]", p, j), CodeSchema, "dose_slots 不允许重复")
		}
		seen[slot] = true
	}
	if s.DoseTimes != nil && *s.DoseTimes != len(s.DoseSlots) {
		es.add(p+".dose_times_per_day", CodeSchema, "一天次数必须与 dose_slots 数量一致")
	}
	startOK := true
	if s.StartDate != "" {
		_, startOK = parseDate(s.StartDate)
		if !startOK {
			checkDate(es, p+".start_date", s.StartDate)
		}
	}
	if s.EndDate != "" {
		if et, ok := checkDate(es, p+".end_date", s.EndDate); ok && s.StartDate != "" && startOK {
			st, _ := parseDate(s.StartDate)
			if et.Before(st) {
				es.add(p+".end_date", CodeDateOrder, "end_date 不得早于 start_date")
			}
		}
	}
	if s.Supersedes != "" && !IsUUID(s.Supersedes) {
		// 允许药品名指向 stop 中条目（03 §5 改量表达）；导入层再做存在性与唯一性判定
		checkLen(es, p+".supersedes", s.Supersedes, 100, "supersedes")
	}
}
