package main

// 下行：GET /sync?since=&limit=（02 §4.1）；上行：POST /sync（02 §4.2/§4.3）
import (
	"net/http"
	"strconv"
	"time"

	"familyhealth/server/internal/authn"
	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

// Config 运行配置（测试可注入时钟）
type Config struct {
	NowMs func() int64
}

func (c *Config) withDefaults() Config {
	out := *c
	if out.NowMs == nil {
		out.NowMs = func() int64 { return time.Now().UnixMilli() }
	}
	return out
}

// NewHandler 路由：GET/POST /sync（兼挂根路径适配触发器路径形态）
func NewHandler(st store.Store, cfg *Config) http.Handler {
	if cfg == nil {
		cfg = &Config{}
	}
	c := cfg.withDefaults()
	mux := http.NewServeMux()
	handle := func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			handlePull(w, r, st)
		case http.MethodPost:
			handlePush(w, r, st, c)
		default:
			httpx.Fail(w, http.StatusMethodNotAllowed, "METHOD", "仅支持 GET/POST")
		}
	}
	mux.HandleFunc("/sync", handle)
	mux.HandleFunc("/", handle)
	return mux
}

// verify 鉴权：member（App）与 api（脚本）均可同步（02 §3.2）
func verify(w http.ResponseWriter, r *http.Request, st store.Store) *model.Device {
	d, err := authn.Verify(r.Context(), st, httpx.Bearer(r), model.DeviceMember, model.DeviceAPI)
	if err != nil {
		httpx.Fail(w, http.StatusUnauthorized, "UNAUTHORIZED", "无效或已吊销的令牌")
		return nil
	}
	return d
}

// handlePull 下行增量拉取（02 §4.1：seq 升序、含墓碑、since=0 即全量）
func handlePull(w http.ResponseWriter, r *http.Request, st store.Store) {
	dev := verify(w, r, st)
	if dev == nil {
		return
	}
	q := r.URL.Query()
	since, _ := strconv.ParseInt(q.Get("since"), 10, 64)
	limit, _ := strconv.Atoi(q.Get("limit"))
	if limit <= 0 || limit > 500 {
		limit = 500
	}
	// 多取 1 条判定 has_more
	changes, err := st.ListChanges(r.Context(), dev.FamilyID, since, limit+1)
	if err != nil {
		httpx.Fail(w, http.StatusInternalServerError, "INTERNAL", "同步查询失败")
		return
	}
	hasMore := len(changes) > limit
	if hasMore {
		changes = changes[:limit]
	}
	next := since
	if n := len(changes); n > 0 {
		next = seqOfRow(changes[n-1].Row)
	}
	if changes == nil {
		changes = []model.Change{}
	}
	httpx.JSON(w, http.StatusOK, model.PullResponse{Changes: changes, Next: next, HasMore: hasMore})
}

func seqOfRow(row model.JSONMap) int64 {
	switch v := row["seq"].(type) {
	case int64:
		return v
	case float64:
		return int64(v)
	}
	return 0
}
