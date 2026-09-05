// Package store 托管 PostgreSQL 访问层：接口抽象 + 内存实现（单测）+ pgx/v5 实现（生产）。
// 契约：docs/04-技术选型.md §4（PG 落地要点）、docs/02-数据库与同步.md §4（seq 语义、幂等）
package store

import (
	"context"
	"errors"

	"familyhealth/server/internal/model"
)

// ErrNotFound 记录不存在
var ErrNotFound = errors.New("store: 记录不存在")

// ImportRecord 导入幂等记录（03 §1 原则 2：同 import_id 重复提交返回首次结果）
type ImportRecord struct {
	ImportID    string `json:"import_id"`
	FamilyID    string `json:"family_id"`
	DeviceID    string `json:"device_id"`
	PayloadHash string `json:"payload_hash"` // 请求体 sha256，同 id 不同体 → 拒绝（03 §6.3 DUPLICATE_IMPORT_ID_FORMAT）
	Response    []byte `json:"response"`     // 首次响应原文，重放时直接返回
	CreatedAt   int64  `json:"created_at"`
}

// Store 领域访问接口。functions 只依赖本接口；单测用内存实现，生产用 pgx 实现。
type Store interface {
	// 家庭与设备（02 §3.1/§3.2）
	GetFamily(ctx context.Context, id string) (*model.Family, error)
	CreateDevice(ctx context.Context, d *model.Device) error
	FindDeviceByTokenHash(ctx context.Context, hash string) (*model.Device, error)
	TouchDevice(ctx context.Context, id string, lastSeenAt int64) error

	// 档案与用药查询（导入解析用，03 §2/§5）
	GetProfile(ctx context.Context, familyID, id string) (*model.Profile, error)
	FindProfilesByName(ctx context.Context, familyID, name string) ([]*model.Profile, error)
	GetMedicationItem(ctx context.Context, familyID, id string) (*model.MedicationItem, error)
	FindActiveMedsByName(ctx context.Context, familyID, profileID, name string) ([]*model.MedicationItem, error)

	// 附件存在性（03 §3.1 attachment_ids 须引用已上传附件）
	MissingAttachments(ctx context.Context, familyID string, ids []string) (missing []string, err error)
	// GetRow 按 id 取白名单表行（family 隔离）
	GetRow(ctx context.Context, table, familyID, id string) (model.JSONMap, error)
	// 指标别名归一（02 §3.8：写入时预解析 canonical_name）
	ResolveCanonical(ctx context.Context, familyID, profileID, itemName string) (canonical string, ok bool, err error)

	// 幂等
	GetImportRecord(ctx context.Context, familyID, importID string) (*ImportRecord, error)
	GetIdempotentResponse(ctx context.Context, deviceID, key string) (resp []byte, ok bool, err error)

	// 同步下行（02 §4.1）：seq>since 的全部变更行（含墓碑），按 seq 升序，最多 limit 条
	ListChanges(ctx context.Context, familyID string, since int64, limit int) ([]model.Change, error)

	// InTx 单事务执行（04 §4：导入批次原子性，失败即回滚）
	InTx(ctx context.Context, fn func(tx Tx) error) error

	Close() error
}

// Tx 事务内操作。所有写入由实现方统一刷新 seq（02 §4.1）。
type Tx interface {
	// NextSeq 全局递增序号（实现：PG 序列 sync_seq；04 §4.2）
	NextSeq(ctx context.Context) (int64, error)
	// InsertRow 插入行；row 必须已含 id/family_id/created_by/source/时间戳/seq
	InsertRow(ctx context.Context, table string, row model.JSONMap) error
	// UpdateRow 整行覆盖业务列（02 §4.3 LWW：不合并字段，未提供的业务列置 NULL），
	// 通用列中仅 updated_at/seq 由 row 刷新，其余保留库内原值
	UpdateRow(ctx context.Context, table string, row model.JSONMap) error
	// GetRow 按 id 取行（family 隔离）
	GetRow(ctx context.Context, table, familyID, id string) (model.JSONMap, error)
	// SoftDeleteRow 软删墓碑（02 §1 删除决策）
	SoftDeleteRow(ctx context.Context, table, familyID, id string, seq, updatedAt int64) error

	PutImportRecord(ctx context.Context, rec *ImportRecord) error
	PutIdempotentResponse(ctx context.Context, deviceID, key string, resp []byte, createdAt int64) error
}
