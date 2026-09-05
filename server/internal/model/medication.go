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
	DosageText   string      `json:"dosage_text" db:"dosage_text"`         // 中药在此写详细（剂数、煎服法）
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

// DailyMedItem 今日用药清单（执行层，与方案无外键关联）
type DailyMedItem struct {
	Base
	ProfileID      string   `json:"profile_id" db:"profile_id"`
	IsTCM          bool     `json:"is_tcm" db:"is_tcm"`
	Name           string   `json:"name" db:"name"`
	DoseText       string   `json:"dose_text,omitempty" db:"dose_text"`   // 西药：每次用量
	DoseSlots      []string `json:"dose_slots,omitempty" db:"dose_slots"` // 西药：时段
	StockQty       *float64 `json:"stock_qty,omitempty" db:"stock_qty"`
	StockUnit      string   `json:"stock_unit,omitempty" db:"stock_unit"`
	DailyQty       *float64 `json:"daily_qty,omitempty" db:"daily_qty"` // 西药：可服天数 = stock/daily
	TCMPacks       *float64 `json:"tcm_packs,omitempty" db:"tcm_packs"`
	TCMDaysPerPack *int     `json:"tcm_days_per_pack,omitempty" db:"tcm_days_per_pack"`
	TCMUsedDays    *int     `json:"tcm_used_days,omitempty" db:"tcm_used_days"`
}
