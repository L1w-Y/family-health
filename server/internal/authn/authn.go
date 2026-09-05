// Package authn 设备令牌鉴权：校验 Bearer token → 返回设备（含 family_id 与署名）。
// 契约：docs/02-数据库与同步.md §3.2 devices、§3.13 api_tokens（已并入）
package authn

import (
	"context"
	"errors"

	"familyhealth/server/internal/model"
)

var ErrUnauthorized = errors.New("authn: 无效或已吊销的令牌")

// Verify 校验令牌并返回设备。实现要点：token 哈希查 devices，未吊销、type 匹配 scope。
// TODO M1：实现（依赖 store）；只存哈希，原文不落库（02 §3.2）。
func Verify(ctx context.Context, token string, scope model.DeviceType) (*model.Device, error) {
	return nil, ErrUnauthorized
}

// Hash 令牌哈希（sha256 + 环境级盐）。
// TODO M1：实现。
func Hash(token string) string {
	return ""
}
