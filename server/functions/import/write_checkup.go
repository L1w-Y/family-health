package main

// 载荷 A 写入：复查事件 + 报告 + 指标（03 §3）+ 测量（03 §4）。
// 指标：定量/定性归类（03 §3.2）；canonical_name 按 watch_items 预解析（02 §3.8）。
import (
	"context"
	"fmt"
	"time"

	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

func planCheckup(ctx context.Context, st store.Store, dev *model.Device, profile *model.Profile,
	ev *validate.EventPayload, c Config) (*writePlan, []validate.Error, error) {
	plan := &writePlan{}
	now := c.NowMs()
	p := "payload.event"

	// 附件存在性（03 §3.1：引用已上传附件）并收集回填任务
	type attachBackfill struct {
		attachmentIDs []string
	}
	var backfills []attachBackfill
	for i, rep := range ev.Reports {
		if len(rep.AttachmentIDs) == 0 {
			backfills = append(backfills, attachBackfill{})
			continue
		}
		missing, err := st.MissingAttachments(ctx, dev.FamilyID, rep.AttachmentIDs)
		if err != nil {
			return nil, nil, err
		}
		if len(missing) > 0 {
			return nil, []validate.Error{{
				Path:    fmt.Sprintf("%s.reports[%d].attachment_ids", p, i),
				Code:    validate.CodeAttachNotFound,
				Message: fmt.Sprintf("附件未上传：%v（03 §3.1 附件须经独立通道先行上传）", missing),
			}}, nil
		}
		backfills = append(backfills, attachBackfill{attachmentIDs: rep.AttachmentIDs})
	}

	// 事件行（medication_changes_note 落 02 §3.5 专列：展示性备注，03 §3）
	eventRow := newRow(dev, now)
	eventRow["profile_id"] = profile.ID
	eventRow["checkup_date"] = ev.CheckupDate
	eventRow["hospital"] = strPtr(ev.Hospital)
	eventRow["department"] = strPtr(ev.Department)
	eventRow["note"] = strPtr(ev.Note)
	eventRow["next_checkup_date"] = datePtr(ev.NextCheckupDate)
	eventRow["medication_changes_note"] = strPtr(ev.MedicationChangesNote) // 02 §3.5 专列（展示性备注，03 §3）
	eventID := eventRow["id"].(string)
	plan.inserts = append(plan.inserts, plannedInsert{table: "checkup_events", row: eventRow})
	plan.summary.EventIDs = []string{eventID}

	for i, rep := range ev.Reports {
		reportRow := newRow(dev, now)
		reportRow["event_id"] = eventID
		reportRow["title"] = rep.Title
		reportDate := rep.ReportDate
		if reportDate == "" {
			reportDate = ev.CheckupDate // 03 §3.1 缺省 = 事件日
		}
		reportRow["report_date"] = reportDate
		reportRow["conclusion_text"] = strPtr(rep.ConclusionText)
		reportID := reportRow["id"].(string)
		plan.inserts = append(plan.inserts, plannedInsert{table: "reports", row: reportRow})
		plan.summary.ReportCount++

		// 附件归属回填（report_id）；UpdateRow 为整行覆盖语义，先取整行再改
		for _, aid := range backfills[i].attachmentIDs {
			attRow, err := st.GetRow(ctx, "attachments", dev.FamilyID, aid)
			if err != nil {
				return nil, nil, err
			}
			attRow["report_id"] = reportID
			plan.updates = append(plan.updates, plannedUpdate{table: "attachments", row: attRow})
		}

		for j, ind := range rep.Indicators {
			row := newRow(dev, now)
			row["report_id"] = reportID
			row["item_name"] = ind.ItemName
			setIndicatorValue(row, ind.Value)
			row["unit"] = strPtr(ind.Unit)
			row["reference_range"] = strPtr(ind.ReferenceRange)
			row["sort_order"] = j // 03 §3.2：数组顺序即报告原始顺序
			// 别名归一预解析（02 §3.8）
			if canonical, ok, err := st.ResolveCanonical(ctx, dev.FamilyID, profile.ID, ind.ItemName); err != nil {
				return nil, nil, err
			} else if ok {
				row["canonical_name"] = canonical
			}
			plan.inserts = append(plan.inserts, plannedInsert{table: "indicator_items", row: row})
			plan.summary.IndicatorCount++
		}
	}
	return plan, nil, nil
}

// setIndicatorValue 定量/定性归类（03 §3.2，判定实现见 validate.ClassifyIndicatorValue）
func setIndicatorValue(row model.JSONMap, v any) {
	num, text := validate.ClassifyIndicatorValue(v)
	if num != nil {
		row["value_numeric"] = *num
	}
	if text != nil {
		row["value_text"] = *text
	}
}

// planMeasurements 载荷 B：批量测量（03 §4）
func planMeasurements(dev *model.Device, profile *model.Profile,
	items []validate.MeasurementItem, c Config) (*writePlan, []validate.Error, error) {
	plan := &writePlan{}
	now := c.NowMs()
	for i, m := range items {
		t, tzOff, ok := parseISO(m.MeasuredAt)
		if !ok {
			return nil, []validate.Error{{
				Path: fmt.Sprintf("payload.items[%d].measured_at", i),
				Code: validate.CodeSchema, Message: "measured_at 解析失败",
			}}, nil
		}
		row := newRow(dev, now)
		row["profile_id"] = profile.ID
		row["type"] = m.Type
		row["measured_at"] = t.UnixMilli()
		row["tz_offset_min"] = tzOff
		if m.Systolic != nil {
			row["systolic"] = int(*m.Systolic)
		}
		if m.Diastolic != nil {
			row["diastolic"] = int(*m.Diastolic)
		}
		if m.HeartRateBpm != nil {
			row["heart_rate_bpm"] = int(*m.HeartRateBpm)
		}
		if m.GlucoseMmol != nil {
			row["glucose_mmol"] = *m.GlucoseMmol
		}
		row["glucose_context"] = strPtr(m.GlucoseCtx)
		row["note"] = strPtr(m.Note)
		plan.inserts = append(plan.inserts, plannedInsert{table: "measurements", row: row})
		plan.summary.MeasurementCount++
	}
	return plan, nil, nil
}

func parseISO(s string) (time.Time, int, bool) {
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		return time.Time{}, 0, false
	}
	_, off := t.Zone()
	return t.UTC(), off / 60, true
}
