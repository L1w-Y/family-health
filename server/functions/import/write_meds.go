package main

// 载荷 C 写入：一条 med_changes + 若干条目变更（03 §5；02 §3.9/§3.10）。
// 改量表达：stop 写 end_date + start 以 supersedes 指向被停条目（01 §5.2 规则 2/3）。
import (
	"context"
	"fmt"

	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

func planMedChanges(ctx context.Context, st store.Store, dev *model.Device, profile *model.Profile,
	cs *validate.MedChangePayload, c Config) (*writePlan, []validate.Error, error) {
	plan := &writePlan{}
	now := c.NowMs()
	p := "payload"

	// linked_event_id：UUID 须指向本家庭既有事件；"$event" 仅在同批含载荷 A 时可用——
	// v1 信封一次只载一种载荷（03 §2），本批无事件可引用 → 拒绝（README 决策记录）
	linkedEventID := cs.LinkedEventID
	if linkedEventID == validate.LinkedEventRefBatch {
		return nil, []validate.Error{{
			Path: p + ".linked_event_id", Code: validate.CodeSchema,
			Message: "\"$event\" 需同批载荷 A 创建的事件，v1 单载荷信封无法引用（03 §2/§5）",
		}}, nil
	}
	if linkedEventID != "" {
		if _, err := st.GetRow(ctx, "checkup_events", dev.FamilyID, linkedEventID); err != nil {
			return nil, []validate.Error{{
				Path: p + ".linked_event_id", Code: validate.CodeSchema,
				Message: "linked_event_id 指向的复查事件不存在",
			}}, nil
		}
	}

	// med_changes 批次行（03 §5：整批落库为一条 med_changes + 若干条目变更）
	changeRow := newRow(dev, now)
	changeRow["profile_id"] = profile.ID
	changeRow["effective_date"] = cs.EffectiveDate
	changeRow["note"] = strPtr(cs.ReasonNote)
	changeRow["linked_event_id"] = strPtr(linkedEventID)
	changeID := changeRow["id"].(string)
	plan.inserts = append(plan.inserts, plannedInsert{table: "med_changes", row: changeRow})
	plan.summary.ChangeIDs = []string{changeID}

	// stop：匹配当前进行中条目（03 §5：不唯一 → 拒绝）
	stoppedByName := map[string]string{} // name → id（供 supersedes 解析优先命中本批）
	for i, sp := range cs.Stop {
		item, verr := resolveStopTarget(ctx, st, dev.FamilyID, profile.ID, sp.Match, fmt.Sprintf("%s.stop[%d].match", p, i))
		if verr != nil {
			return nil, []validate.Error{*verr}, nil
		}
		endDate := sp.EndDate
		if endDate == "" {
			endDate = cs.EffectiveDate // 03 §5：end_date 缺省 = effective_date
		}
		// 整行更新：取现有行改 end_date（02 §3.9 停用 = 仅写 end_date）
		full, err := st.GetRow(ctx, "medication_items", dev.FamilyID, item.ID)
		if err != nil {
			return nil, nil, err
		}
		full["end_date"] = endDate
		plan.updates = append(plan.updates, plannedUpdate{table: "medication_items", row: full})
		stoppedByName[item.Name] = item.ID
		plan.summary.StoppedCount++
	}

	// start：新增条目，change_id 归属本批（01 §5.2 规则 3）
	for i, si := range cs.Start {
		row := newRow(dev, now)
		row["profile_id"] = profile.ID
		category := si.Category
		if category == "" {
			category = string(model.MedLong) // 03 §5 缺省 long_term
		}
		row["category"] = category
		kind := si.Kind
		if kind == "" {
			kind = string(model.KindWestern) // 03 §5 缺省 western
		}
		row["med_kind"] = kind
		row["name"] = si.Name
		row["dosage_text"] = si.DosageText
		if si.DoseQty != nil {
			row["dose_qty"] = *si.DoseQty
		}
		if si.DoseUnit != "" {
			row["dose_unit"] = si.DoseUnit
		}
		if si.DoseTimes != nil {
			row["dose_times_per_day"] = *si.DoseTimes
		}
		if len(si.DoseSlots) > 0 {
			slots := make([]any, len(si.DoseSlots))
			for j, s := range si.DoseSlots {
				slots[j] = s
			}
			row["dose_slots"] = slots
		}
		startDate := si.StartDate
		if startDate == "" {
			startDate = cs.EffectiveDate
		}
		row["start_date"] = startDate
		row["end_date"] = datePtr(si.EndDate)
		row["change_id"] = changeID
		if si.Supersedes != "" {
			supID, verr := resolveSupersedes(ctx, st, dev.FamilyID, profile.ID, si.Supersedes,
				stoppedByName, fmt.Sprintf("%s.start[%d].supersedes", p, i))
			if verr != nil {
				return nil, []validate.Error{*verr}, nil
			}
			row["supersedes_id"] = supID
		}
		plan.inserts = append(plan.inserts, plannedInsert{table: "medication_items", row: row})
		plan.summary.StartedCount++
	}
	return plan, nil, nil
}

// resolveStopTarget 停用目标匹配：id 直查；name 精确匹配进行中条目（03 §5）
func resolveStopTarget(ctx context.Context, st store.Store, familyID, profileID string,
	match validate.MatchRef, path string) (*model.MedicationItem, *validate.Error) {
	if match.ID != "" {
		item, err := st.GetMedicationItem(ctx, familyID, match.ID)
		if err != nil {
			return nil, &validate.Error{Path: path, Code: validate.CodeMatchNotFound, Message: "停用目标不存在"}
		}
		if item.ProfileID != profileID {
			return nil, &validate.Error{Path: path, Code: validate.CodeMatchNotFound, Message: "停用目标不属于本档案"}
		}
		if item.EndDate != nil {
			return nil, &validate.Error{Path: path, Code: validate.CodeMatchNotFound, Message: "停用目标已结束，不在进行中"}
		}
		return item, nil
	}
	items, err := st.FindActiveMedsByName(ctx, familyID, profileID, match.Name)
	if err != nil || len(items) == 0 {
		return nil, &validate.Error{Path: path, Code: validate.CodeMatchNotFound,
			Message: "按名称未匹配到进行中条目：" + match.Name}
	}
	if len(items) > 1 {
		return nil, &validate.Error{Path: path, Code: validate.CodeMatchAmbiguous,
			Message: "名称匹配到多条进行中条目，请改用 id：" + match.Name}
	}
	return items[0], nil
}

// resolveSupersedes 改量链目标（03 §5：可指向 stop 中条目；id 或 name）
func resolveSupersedes(ctx context.Context, st store.Store, familyID, profileID, ref string,
	stoppedByName map[string]string, path string) (string, *validate.Error) {
	if validate.IsUUID(ref) {
		if _, err := st.GetMedicationItem(ctx, familyID, ref); err != nil {
			return "", &validate.Error{Path: path, Code: validate.CodeMatchNotFound, Message: "supersedes 指向的条目不存在"}
		}
		return ref, nil
	}
	// 名称：优先本批 stop（03 §5 改量表达），再查库内进行中
	if id, ok := stoppedByName[ref]; ok {
		return id, nil
	}
	items, err := st.FindActiveMedsByName(ctx, familyID, profileID, ref)
	if err != nil || len(items) == 0 {
		return "", &validate.Error{Path: path, Code: validate.CodeMatchNotFound,
			Message: "supersedes 未匹配到被停条目：" + ref}
	}
	if len(items) > 1 {
		return "", &validate.Error{Path: path, Code: validate.CodeMatchAmbiguous,
			Message: "supersedes 名称不唯一，请改用 id：" + ref}
	}
	return items[0].ID, nil
}
