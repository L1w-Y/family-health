package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"familyhealth/server/internal/authn"
	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

func testServer(t *testing.T) (http.Handler, *store.MemoryStore) {
	t.Helper()
	st := store.NewMemoryStore()
	st.SeedFamily(model.Family{ID: "fam-1", Name: "家"})
	h := NewHandler(st, &Config{FamilySecret: "s3cret", FamilyID: "fam-1"})
	return h, st
}

func doAuth(t *testing.T, h http.Handler, body string) (int, map[string]any) {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/auth", bytes.NewReader([]byte(body)))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	var resp map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	return rec.Code, resp
}

func TestAuthSuccess(t *testing.T) {
	h, st := testServer(t)
	code, resp := doAuth(t, h, `{"secret":"s3cret","display_name":"爸爸"}`)
	if code != http.StatusOK || resp["ok"] != true {
		t.Fatalf("expect ok, got %d %v", code, resp)
	}
	token, _ := resp["token"].(string)
	if !strings.HasPrefix(token, authn.TokenPrefix) {
		t.Fatalf("token missing prefix: %q", token)
	}
	// 库中只有哈希，且哈希可鉴定（02 §3.2 原文不落库）
	dev, err := st.FindDeviceByTokenHash(t.Context(), authn.Hash(token))
	if err != nil || dev.DisplayName != "爸爸" || dev.Type != model.DeviceMember {
		t.Fatalf("device not persisted correctly: %v %+v", err, dev)
	}
	if dev.TokenHash == token {
		t.Fatal("raw token must not be stored")
	}
}

func TestAuthAPIDeviceType(t *testing.T) {
	h, st := testServer(t)
	code, resp := doAuth(t, h, `{"secret":"s3cret","display_name":"导入脚本","device_type":"api"}`)
	if code != http.StatusOK {
		t.Fatalf("expect ok, got %d %v", code, resp)
	}
	token, _ := resp["token"].(string)
	dev, _ := st.FindDeviceByTokenHash(t.Context(), authn.Hash(token))
	if dev.Type != model.DeviceAPI || dev.Source != model.SourceAPI {
		t.Fatalf("api device wrong: %+v", dev)
	}
}

func TestAuthWrongSecret(t *testing.T) {
	h, _ := testServer(t)
	code, _ := doAuth(t, h, `{"secret":"wrong","display_name":"爸爸"}`)
	if code != http.StatusUnauthorized {
		t.Fatalf("expect 401, got %d", code)
	}
}

func TestAuthValidation(t *testing.T) {
	h, _ := testServer(t)
	if code, _ := doAuth(t, h, `{"secret":"s3cret"}`); code != http.StatusUnprocessableEntity {
		t.Fatalf("missing display_name expect 422, got %d", code)
	}
	if code, _ := doAuth(t, h, `{"secret":"s3cret","display_name":"x","device_type":"robot"}`); code != http.StatusUnprocessableEntity {
		t.Fatalf("bad device_type expect 422, got %d", code)
	}
	if code, _ := doAuth(t, h, `{bad json`); code != http.StatusBadRequest {
		t.Fatalf("bad json expect 400, got %d", code)
	}
}
