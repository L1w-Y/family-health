package model

// 契约：docs/02-数据库与同步.md §3.5 checkup_events、§3.6 reports、§3.7 indicator_items、§3.8 watch_items

// CheckupEvent 复查事件（容器：报告 + 备注 + 用药调整 + 下次复查）
type CheckupEvent struct {
	Base
	ProfileID             string  `json:"profile_id" db:"profile_id"`
	CheckupDate           string  `json:"checkup_date" db:"checkup_date"` // ISO YYYY-MM-DD
	Hospital              string  `json:"hospital,omitempty" db:"hospital"`
	Department            string  `json:"department,omitempty" db:"department"`
	Note                  string  `json:"note,omitempty" db:"note"`
	NextCheckupDate       *string `json:"next_checkup_date,omitempty" db:"next_checkup_date"`
	MedicationChangesNote string  `json:"medication_changes_note,omitempty" db:"medication_changes_note"` // 展示性备注（03 §3）；实际变更走载荷 C
}

// Report 检查报告（必须属于某复查事件）
type Report struct {
	Base
	EventID        string `json:"event_id" db:"event_id"`
	Title          string `json:"title" db:"title"`
	ReportDate     string `json:"report_date" db:"report_date"`
	ConclusionText string `json:"conclusion_text,omitempty" db:"conclusion_text"` // 描述性结论，不字段化
}

// IndicatorItem 指标项目（追加型；定量与定性并存）
type IndicatorItem struct {
	Base
	ReportID       string   `json:"report_id" db:"report_id"`
	ItemName       string   `json:"item_name" db:"item_name"` // 医院原文名，不归一改写
	ValueNumeric   *float64 `json:"value_numeric,omitempty" db:"value_numeric"`
	ValueText      *string  `json:"value_text,omitempty" db:"value_text"` // 定性值（"阴性"、"+"）
	Unit           string   `json:"unit,omitempty" db:"unit"`
	ReferenceRange string   `json:"reference_range,omitempty" db:"reference_range"` // 原文照录
	SortOrder      int      `json:"sort_order" db:"sort_order"`
	CanonicalName  *string  `json:"canonical_name,omitempty" db:"canonical_name"` // 写入时按 watch_items 预解析
}

// WatchItem 重点指标清单（每人一份；别名归并只在这里做）
type WatchItem struct {
	Base
	ProfileID     string   `json:"profile_id" db:"profile_id"`
	CanonicalName string   `json:"canonical_name" db:"canonical_name"`
	Aliases       []string `json:"aliases" db:"aliases"` // jsonb text[]
	CanonicalUnit string   `json:"canonical_unit,omitempty" db:"canonical_unit"`
	UnitFactors   JSONMap  `json:"unit_factors,omitempty" db:"unit_factors"` // {原文单位: 换算系数}
	SortOrder     int      `json:"sort_order" db:"sort_order"`
}
