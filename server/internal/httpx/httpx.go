// Package httpx HTTP 响应辅助：统一 JSON 输出与错误格式。
// 契约：docs/03-导入格式-v1.md §6（响应形态）；各函数复用同一错误结构。
package httpx

import (
	"encoding/json"
	"net/http"
)

// ErrorBody 统一错误响应
type ErrorBody struct {
	OK     bool `json:"ok"`
	Errors []struct {
		Path    string `json:"path,omitempty"`
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"errors"`
}

// JSON 以给定状态码输出 JSON
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// Fail 输出统一错误（code/message；http 状态码）
func Fail(w http.ResponseWriter, status int, code, message string) {
	var body ErrorBody
	body.OK = false
	body.Errors = append(body.Errors, struct {
		Path    string `json:"path,omitempty"`
		Code    string `json:"code"`
		Message string `json:"message"`
	}{Code: code, Message: message})
	JSON(w, status, body)
}
