package model

// 契约：docs/02-数据库与同步.md §3.9 medication_items、§3.10 med_changes、§3.15 daily_med_items

// MedCategory 用药分类
type MedCategory string

const (
	MedLong MedCategory = "long_term"
	MedTemp MedCategory = "temporary"
)

// MedKind 中西药
type MedKind string

const (
	KindWestern MedKind = "western"
	KindTCM     MedKind = "tcm"
)

// 服用时段（dose_slots 子集）
const (
	SlotMorning = "morning"
	SlotNoon    = "noon"
	SlotEvening = "evening"
	SlotBedtime = "bedtime"
)

// MedicationItem 用药条目（生命周期：start → end；改量 = 结束旧条 + supersedes 新条）
type MedicationItem struct {
	Base
	ProfileID    string      `json:"profile_id" db:"profile_id"`
	Category     MedCategory `json:"category" db:"category"`
	Kind         MedKind     `json:"med_kind" db:"med_kind"`
	Name         string      `json:"name" db:"name"`
	DosageText   string      `json:"dosage_text" db:"dosage_text"`     // 中药在此写详细（剂数、煎服法）
	DoseQty      *float64    `json:"dose_qty,omitempty" db:"dose_qty"` // 西药：每个时段的一次用量
	DoseUnit     string      `json:"dose_unit,omitempty" db:"dose_unit"`
	DoseTimes    *int        `json:"dose_times_per_day,omitempty" db:"dose_times_per_day"`
	DoseSlots    []string    `json:"dose_slots,omitempty" db:"dose_slots"` // jsonb text[]
	StartDate    string      `json:"start_date" db:"start_date"`
	EndDate      *string     `json:"end_date,omitempty" db:"end_date"`
	SupersedesID *string     `json:"supersedes_id,omitempty" db:"supersedes_id"`
	ChangeID     *string     `json:"change_id,omitempty" db:"change_id"`
}

// MedChange 用药变化（一次调整的批次；事由只在这里）
type MedChange struct {
	Base
	ProfileID     string  `json:"profile_id" db:"profile_id"`
	EffectiveDate string  `json:"effective_date" db:"effective_date"`
	Note          string  `json:"note,omitempty" db:"note"`
	LinkedEventID *string `json:"linked_event_id,omitempty" db:"linked_event_id"`
}

// DailyMedItem 今日用药清单（执行层，关联方案并保存药格盘点事实）
type DailyMedItem struct {
	Base
	ProfileID        string             `json:"profile_id" db:"profile_id"`
	MedicationItemID string             `json:"medication_item_id" db:"medication_item_id"`
	StockBySlot      map[string]float64 `json:"stock_by_slot" db:"stock_by_slot"`
	StockCountedAt   int64              `json:"stock_counted_at" db:"stock_counted_at"`
	TZOffsetMin      int                `json:"tz_offset_min" db:"tz_offset_min"`
}
