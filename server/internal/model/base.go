// Package model 数据结构定义（零逻辑）。一实体一文件。
// 契约：docs/02-数据库与同步.md §2（通用列）、§3（表定义）
package model

// Source 录入来源（02 §2 通用列）
type Source string

const (
	SourceApp Source = "app"
	SourceAPI Source = "api"
)

// Base 所有业务表的通用列（02 §2）
type Base struct {
	ID        string `json:"id" db:"id"`
	FamilyID  string `json:"family_id" db:"family_id"`
	CreatedBy string `json:"created_by" db:"created_by"`
	Source    Source `json:"source" db:"source"`
	CreatedAt int64  `json:"created_at" db:"created_at"`
	UpdatedAt int64  `json:"updated_at" db:"updated_at"`
	Deleted   bool   `json:"deleted" db:"deleted"`
	Seq       int64  `json:"seq" db:"seq"`
}
