// Package httpx HTTP 辅助：统一 JSON 输出、错误格式、请求体限长读取、Bearer 提取。
// 契约：docs/03-导入格式-v1.md §6（响应形态）；docs/02-数据库与同步.md §3.2（Bearer 鉴权）
package httpx

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"strings"
)

// ErrBodyTooLarge 请求体超限（03 §6.4：单请求 ≤512KB）
var ErrBodyTooLarge = errors.New("httpx: 请求体超限")

// ErrorBody 统一错误响应
type ErrorBody struct {
	OK     bool        `json:"ok"`
	Errors []ErrorItem `json:"errors"`
}

// ErrorItem 单条错误（03 §6.3 形态）
type ErrorItem struct {
	Path    string `json:"path,omitempty"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// JSON 以给定状态码输出 JSON
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// Fail 输出统一错误（code/message；http 状态码）
func Fail(w http.ResponseWriter, status int, code, message string) {
	JSON(w, status, ErrorBody{OK: false, Errors: []ErrorItem{{Code: code, Message: message}}})
}

// FailErrors 输出带路径的多错误（03 §6.3）
func FailErrors(w http.ResponseWriter, status int, items []ErrorItem) {
	JSON(w, status, ErrorBody{OK: false, Errors: items})
}

// ReadBody 限长读取请求体；超过 max 返回 ErrBodyTooLarge
func ReadBody(w http.ResponseWriter, r *http.Request, max int64) ([]byte, error) {
	r.Body = http.MaxBytesReader(w, r.Body, max+1)
	b, err := io.ReadAll(r.Body)
	if err != nil {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			return nil, ErrBodyTooLarge
		}
		return nil, err
	}
	if int64(len(b)) > max {
		return nil, ErrBodyTooLarge
	}
	return b, nil
}

// Bearer 提取 Authorization: Bearer <token>（02 §3.2 / 03 §2）
func Bearer(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
}

// EnvOr 环境变量缺省值
func EnvOr(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

// ListenAndServe 云函数统一入口：监听 PORT（custom runtime 约定，缺省 8080）。
// 运行时选型记录见 server/README.md「函数入口的运行时约定」。
func ListenAndServe(handler http.Handler) error {
	port := EnvOr("PORT", "8080")
	return http.ListenAndServe(":"+port, handler)
}
