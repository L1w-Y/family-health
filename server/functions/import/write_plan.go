package main

// 写入计划：校验通过后把载荷翻译为行集合，再在单事务内统一取号落库。
// 契约：03 §1 原则 1（整体原子）；04 §4.3（单事务）。
import (
	"context"

	"familyhealth/server/internal/idgen"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
	"familyhealth/server/internal/validate"
)

// plannedInsert 待插入行（table + 业务列；通用列由 applyPlan 统一盖章）
type plannedInsert struct {
	table string
	row   model.JSONMap
}

// plannedUpdate 待整行更新（现有行已含全部列，业务字段已改好）
type plannedUpdate struct {
	table string
	row   model.JSONMap
}

type writePlan struct {
	inserts []plannedInsert
	updates []plannedUpdate
	summary createdSummary
}

// buildPlan 按载荷类型分派（读侧解析全部在此完成，失败不加任何写副作用）
func buildPlan(ctx context.Context, st store.Store, dev *model.Device, profile *model.Profile,
	env *validate.Envelope, c Config) (*writePlan, []validate.Error, error) {
	switch env.Payload.Type {
	case validate.PayloadCheckupEvent:
		return planCheckup(ctx, st, dev, profile, env.Payload.Event, c)
	case validate.PayloadMeasurements:
		return planMeasurements(dev, profile, env.Payload.Items, c)
	case validate.PayloadMedChanges:
		return planMedChanges(ctx, st, dev, profile, env.Payload.ChangeSet, c)
	}
	return nil, []validate.Error{{Path: "payload.type", Code: validate.CodeEnum, Message: "未知载荷类型"}}, nil
}

// applyPlan 事务内应用：统一盖通用列 + 取号（02 §2/§4.1）
func applyPlan(ctx context.Context, tx store.Tx, plan *writePlan, nowMs int64) error {
	for _, in := range plan.inserts {
		seq, err := tx.NextSeq(ctx)
		if err != nil {
			return err
		}
		in.row["seq"] = seq
		in.row["updated_at"] = nowMs
		if err := tx.InsertRow(ctx, in.table, in.row); err != nil {
			return err
		}
	}
	for _, up := range plan.updates {
		seq, err := tx.NextSeq(ctx)
		if err != nil {
			return err
		}
		up.row["seq"] = seq
		up.row["updated_at"] = nowMs
		if err := tx.UpdateRow(ctx, up.table, up.row); err != nil {
			return err
		}
	}
	return nil
}

// newRow 新行业务列 + 通用列骨架（seq/updated_at 由 applyPlan 填）
func newRow(dev *model.Device, nowMs int64) model.JSONMap {
	src := model.SourceApp
	if dev.Type == model.DeviceAPI {
		src = model.SourceAPI
	}
	return model.JSONMap{
		"id":         idgen.UUID(),
		"family_id":  dev.FamilyID,
		"created_by": dev.ID,
		"source":     string(src),
		"created_at": nowMs,
		"deleted":    false,
	}
}

// strPtr 空串 → nil（可空文本列）
func strPtr(s string) any {
	if s == "" {
		return nil
	}
	return s
}

// datePtr 空串 → nil（可空日期列）
func datePtr(s string) any { return strPtr(s) }
