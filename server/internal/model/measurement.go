package model

// 契约：docs/02-数据库与同步.md §3.4 measurements（追加型）

// MeasurementType 测量类型（心率不单列，作为血压附属 heart_rate_bpm）
type MeasurementType string

const (
	MeasureBP      MeasurementType = "bp"
	MeasureGlucose MeasurementType = "glucose"
)

// GlucoseContext 血糖测量场景
type GlucoseContext string

const (
	CtxFasting     GlucoseContext = "fasting"
	CtxBeforeMeal  GlucoseContext = "before_meal"
	CtxAfterMeal2h GlucoseContext = "after_meal_2h"
	CtxBedtime     GlucoseContext = "bedtime"
	CtxRandom      GlucoseContext = "random"
)

// Measurement 日常测量记录
type Measurement struct {
	Base
	ProfileID    string          `json:"profile_id" db:"profile_id"`
	Type         MeasurementType `json:"type" db:"type"`
	MeasuredAt   int64           `json:"measured_at" db:"measured_at"`
	TzOffsetMin  int             `json:"tz_offset_min" db:"tz_offset_min"`
	Systolic     *int            `json:"systolic,omitempty" db:"systolic"`
	Diastolic    *int            `json:"diastolic,omitempty" db:"diastolic"`
	HeartRateBpm *int            `json:"heart_rate_bpm,omitempty" db:"heart_rate_bpm"`
	GlucoseMmol  *float64        `json:"glucose_mmol,omitempty" db:"glucose_mmol"`
	GlucoseCtx   *GlucoseContext `json:"glucose_context,omitempty" db:"glucose_context"`
	Note         string          `json:"note,omitempty" db:"note"`
	Payload      JSONMap         `json:"payload,omitempty" db:"payload"` // 预留：二期新测量类型
}

// JSONMap 通用 jsonb 容器
type JSONMap map[string]any
