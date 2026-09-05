package model

// 契约：docs/02-数据库与同步.md §3.11 reminders、§3.14 notes

// ReminderType 提醒类型（便签提醒不在此表，由 Note.RemindAt 自驱动）
type ReminderType string

const (
	RemindMedication ReminderType = "medication"
	RemindMeasure    ReminderType = "measure"
	RemindCheckup    ReminderType = "checkup"
)

// Reminder 提醒（档案级时刻表；各设备同步后本地调度）
type Reminder struct {
	Base
	ProfileID   string           `json:"profile_id" db:"profile_id"`
	Type        ReminderType     `json:"type" db:"type"`
	Times       []string         `json:"times,omitempty" db:"times"` // jsonb ["08:00","20:00"]
	MeasureType *MeasurementType `json:"measure_type,omitempty" db:"measure_type"`
	AdvanceDays []int            `json:"advance_days,omitempty" db:"advance_days"` // jsonb [7,1,0]
	Enabled     bool             `json:"enabled" db:"enabled"`
}

// Note 便签（多条并存；可作待办；remind_at 自驱动本地通知）
type Note struct {
	Base
	ProfileID     string   `json:"profile_id" db:"profile_id"`
	Text          string   `json:"text" db:"text"`
	Done          bool     `json:"done" db:"done"`
	RemindAt      *int64   `json:"remind_at,omitempty" db:"remind_at"`
	TzOffsetMin   *int     `json:"tz_offset_min,omitempty" db:"tz_offset_min"`
	RemindTargets []string `json:"remind_targets,omitempty" db:"remind_targets"` // 设备 id 数组；空=全家
}
