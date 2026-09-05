package main

// HTTP 入口：POST /backup（定时触发器/手动）。可选 BACKUP_TOKEN 保护端点。
import (
	"net/http"
	"os"

	"familyhealth/server/internal/httpx"
)

// NewHandler POST /backup；BACKUP_TOKEN 非空时要求 Bearer 匹配（防公网触发器被刷）
func NewHandler(cfg *Config) http.Handler {
	mux := http.NewServeMux()
	handle := func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			httpx.Fail(w, http.StatusMethodNotAllowed, "METHOD", "仅支持 POST")
			return
		}
		if tok := os.Getenv("BACKUP_TOKEN"); tok != "" && httpx.Bearer(r) != tok {
			httpx.Fail(w, http.StatusUnauthorized, "UNAUTHORIZED", "BACKUP_TOKEN 不匹配")
			return
		}
		res, err := Run(r.Context(), cfg)
		if err != nil {
			httpx.Fail(w, http.StatusInternalServerError, "BACKUP_FAILED", err.Error())
			return
		}
		httpx.JSON(w, http.StatusOK, res)
	}
	mux.HandleFunc("/backup", handle)
	mux.HandleFunc("/", handle)
	return mux
}
