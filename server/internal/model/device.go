package model

// 契约：docs/02-数据库与同步.md §3.1 families、§3.2 devices

// Family 家庭（协作组）
type Family struct {
	ID   string `json:"id" db:"id"`
	Name string `json:"name" db:"name"`
}

// DeviceType 设备类型：member=家人手机；api=HTTP 导入令牌（02 §3.13 api_tokens 并入）
type DeviceType string

const (
	DeviceMember DeviceType = "member"
	DeviceAPI    DeviceType = "api"
)

// Device 设备即身份（无账号体系）
type Device struct {
	Base
	Type        DeviceType `json:"type" db:"type"`
	DisplayName string     `json:"display_name" db:"display_name"`
	TokenHash   string     `json:"token_hash" db:"token_hash"`
	RevokedAt   *int64     `json:"revoked_at,omitempty" db:"revoked_at"`
	LastSeenAt  *int64     `json:"last_seen_at,omitempty" db:"last_seen_at"`
}
