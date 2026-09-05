// Package authn 设备令牌鉴权：签发哈希入库、校验 Bearer token → 返回设备（含 family_id 与署名）。
// 契约：docs/02-数据库与同步.md §3.2 devices（设备即身份）、§3.13（api_tokens 已并入）
package authn

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"os"
	"time"

	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

var ErrUnauthorized = errors.New("authn: 无效或已吊销的令牌")

// TokenPrefix 令牌前缀（便于日志识别与泄漏扫描，不含机密性）
const TokenPrefix = "fht_"

// NewToken 生成设备令牌原文（32 字节随机，hex 编码）。原文只返回一次，不落库（02 §3.2）。
func NewToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return TokenPrefix + hex.EncodeToString(b), nil
}

// Hash 令牌哈希（sha256 + 环境级盐）。库中只存哈希，令牌原文不可还原。
func Hash(token string) string {
	return HashWithSalt(salt(), token)
}

// HashWithSalt 显式盐版本（测试用）
func HashWithSalt(salt, token string) string {
	sum := sha256.Sum256([]byte(salt + "|" + token))
	return hex.EncodeToString(sum[:])
}

// salt 环境级盐：生产必须配置 TOKEN_SALT；缺省值仅供本地开发
func salt() string {
	if s := os.Getenv("TOKEN_SALT"); s != "" {
		return s
	}
	return "family-health-dev-only-salt"
}

// Verify 校验令牌并返回设备：token 哈希查 devices，未删除、未吊销、type 命中 scope。
// 命中后尽力刷新 last_seen_at（02 §3.2 辅助管理），失败不影响鉴权结果。
func Verify(ctx context.Context, st store.Store, token string, scopes ...model.DeviceType) (*model.Device, error) {
	if token == "" {
		return nil, ErrUnauthorized
	}
	d, err := st.FindDeviceByTokenHash(ctx, Hash(token))
	if err != nil {
		return nil, ErrUnauthorized
	}
	if d.Deleted || d.RevokedAt != nil {
		return nil, ErrUnauthorized
	}
	if len(scopes) > 0 {
		ok := false
		for _, s := range scopes {
			if d.Type == s {
				ok = true
				break
			}
		}
		if !ok {
			return nil, ErrUnauthorized
		}
	}
	_ = st.TouchDevice(ctx, d.ID, time.Now().UnixMilli())
	return d, nil
}
