package model

// 契约：docs/02-数据库与同步.md §3.3 profiles

// Gender 性别
type Gender string

const (
	GenderMale    Gender = "male"
	GenderFemale  Gender = "female"
	GenderUnknown Gender = "unknown"
)

// Profile 成员档案（被记录者，数据归属主体）
type Profile struct {
	Base
	Name           string  `json:"name" db:"name"`
	Relation       string  `json:"relation" db:"relation"`
	Gender         Gender  `json:"gender" db:"gender"`
	BirthDate      *string `json:"birth_date,omitempty" db:"birth_date"` // ISO YYYY-MM-DD
	Notes          string  `json:"notes" db:"notes"`                     // 自由文本静态档案
	LinkedDeviceID *string `json:"linked_device_id,omitempty" db:"linked_device_id"`
}
