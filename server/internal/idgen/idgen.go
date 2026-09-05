// Package idgen 主键生成：UUID v4（客户端/服务端同形，02 §1 主键决策）。
package idgen

import (
	"crypto/rand"
	"fmt"
)

// UUID 生成 RFC 4122 v4 UUID 文本
func UUID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(fmt.Sprintf("idgen: 随机源不可用: %v", err))
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
