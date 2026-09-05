package main

// 契约：docs/02-数据库与同步.md §3.2（设备即身份：口令换 token，只存哈希）
import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"net/http"
	"time"

	"familyhealth/server/internal/authn"
	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/idgen"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

// Config 运行配置（缺省读环境变量；测试可注入）
type Config struct {
	FamilySecret string // 家庭口令（env FAMILY_SECRET）
	FamilyID     string // 家庭 id（env FAMILY_ID，单家庭部署）
	NowMs        func() int64
}

func (c *Config) withDefaults() Config {
	out := *c
	if out.FamilySecret == "" {
		out.FamilySecret = httpx.EnvOr("FAMILY_SECRET", "")
	}
	if out.FamilyID == "" {
		out.FamilyID = httpx.EnvOr("FAMILY_ID", "")
	}
	if out.NowMs == nil {
		out.NowMs = func() int64 { return time.Now().UnixMilli() }
	}
	return out
}

type authRequest struct {
	Secret      string `json:"secret"`
	DisplayName string `json:"display_name"`
	DeviceType  string `json:"device_type,omitempty"` // member（默认）/ api
}

type authResponse struct {
	OK     bool          `json:"ok"`
	Token  string        `json:"token"`
	Device deviceSummary `json:"device"`
}

type deviceSummary struct {
	ID          string `json:"id"`
	FamilyID    string `json:"family_id"`
	Type        string `json:"type"`
	DisplayName string `json:"display_name"`
}

// NewHandler POST /auth（口令换 token；02 §3.2）
func NewHandler(st store.Store, cfg *Config) http.Handler {
	if cfg == nil {
		cfg = &Config{}
	}
	c := cfg.withDefaults()
	mux := http.NewServeMux()
	mux.HandleFunc("POST /auth", func(w http.ResponseWriter, r *http.Request) {
		handleAuth(w, r, st, c)
	})
	mux.HandleFunc("POST /", func(w http.ResponseWriter, r *http.Request) {
		handleAuth(w, r, st, c)
	})
	return mux
}

func handleAuth(w http.ResponseWriter, r *http.Request, st store.Store, c Config) {
	body, err := httpx.ReadBody(w, r, 16*1024)
	if err != nil {
		httpx.Fail(w, http.StatusBadRequest, "SCHEMA", "请求体读取失败")
		return
	}
	var req authRequest
	if err := json.Unmarshal(body, &req); err != nil {
		httpx.Fail(w, http.StatusBadRequest, "SCHEMA", "JSON 解析失败")
		return
	}
	if req.DisplayName == "" {
		httpx.Fail(w, http.StatusUnprocessableEntity, "REQUIRED", "display_name 必填（设备署名，02 §3.2）")
		return
	}
	devType := model.DeviceMember
	if req.DeviceType != "" {
		if req.DeviceType != string(model.DeviceMember) && req.DeviceType != string(model.DeviceAPI) {
			httpx.Fail(w, http.StatusUnprocessableEntity, "ENUM", "device_type 须为 member / api")
			return
		}
		devType = model.DeviceType(req.DeviceType)
	}

	// 口令统一 401，不暴露"家庭不存在/口令错误"差异（时序安全比较）
	if c.FamilySecret == "" || c.FamilyID == "" ||
		!secretEqual(req.Secret, c.FamilySecret) {
		httpx.Fail(w, http.StatusUnauthorized, "UNAUTHORIZED", "口令错误")
		return
	}
	fam, err := st.GetFamily(r.Context(), c.FamilyID)
	if err != nil {
		httpx.Fail(w, http.StatusUnauthorized, "UNAUTHORIZED", "口令错误")
		return
	}

	token, err := authn.NewToken()
	if err != nil {
		httpx.Fail(w, http.StatusInternalServerError, "INTERNAL", "令牌生成失败")
		return
	}
	now := c.NowMs()
	src := model.SourceApp
	if devType == model.DeviceAPI {
		src = model.SourceAPI
	}
	devID := idgen.UUID()
	d := &model.Device{
		Base: model.Base{
			ID: devID, FamilyID: fam.ID, CreatedBy: devID, Source: src,
			CreatedAt: now, UpdatedAt: now,
		},
		Type:        devType,
		DisplayName: req.DisplayName,
		TokenHash:   authn.Hash(token),
	}
	if err := st.CreateDevice(r.Context(), d); err != nil {
		httpx.Fail(w, http.StatusInternalServerError, "INTERNAL", "设备登记失败")
		return
	}
	// 令牌原文仅此一次返回（02 §3.2：原文不落库）
	httpx.JSON(w, http.StatusOK, authResponse{
		OK:    true,
		Token: token,
		Device: deviceSummary{
			ID: d.ID, FamilyID: d.FamilyID, Type: string(d.Type), DisplayName: d.DisplayName,
		},
	})
}

// secretEqual 常量时间比较（sha256 预散列避免长度侧信道）
func secretEqual(got, want string) bool {
	g := sha256.Sum256([]byte(got))
	ws := sha256.Sum256([]byte(want))
	return subtle.ConstantTimeCompare(g[:], ws[:]) == 1
}
