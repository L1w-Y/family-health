package authn

import (
	"context"
	"testing"

	"familyhealth/server/internal/model"
	"familyhealth/server/internal/store"
)

func seededStore(t *testing.T, token string, revokedAt *int64) (*store.MemoryStore, *model.Device) {
	t.Helper()
	st := store.NewMemoryStore()
	st.SeedFamily(model.Family{ID: "fam-1", Name: "家"})
	d := &model.Device{
		Base:        model.Base{ID: "dev-1", FamilyID: "fam-1", CreatedBy: "dev-1", Source: model.SourceApp, CreatedAt: 1, UpdatedAt: 1},
		Type:        model.DeviceMember,
		DisplayName: "爸爸",
		TokenHash:   HashWithSalt("family-health-dev-only-salt", token),
		RevokedAt:   revokedAt,
	}
	if err := st.CreateDevice(context.Background(), d); err != nil {
		t.Fatal(err)
	}
	return st, d
}

func TestHashStableAndSalted(t *testing.T) {
	h1 := HashWithSalt("s1", "tok")
	h2 := HashWithSalt("s1", "tok")
	h3 := HashWithSalt("s2", "tok")
	if h1 != h2 || h1 == h3 || len(h1) != 64 {
		t.Fatalf("hash wrong: %s %s %s", h1, h2, h3)
	}
}

func TestNewToken(t *testing.T) {
	t1, err := NewToken()
	if err != nil || len(t1) != len(TokenPrefix)+64 {
		t.Fatalf("token wrong: %v %q", err, t1)
	}
	t2, _ := NewToken()
	if t1 == t2 {
		t.Fatal("tokens must be unique")
	}
}

func TestVerifyOK(t *testing.T) {
	st, d := seededStore(t, "tok-1", nil)
	got, err := Verify(context.Background(), st, "tok-1", model.DeviceMember)
	if err != nil || got.ID != d.ID {
		t.Fatalf("verify failed: %v %+v", err, got)
	}
	// last_seen_at 已刷新
	after, _ := st.FindDeviceByTokenHash(context.Background(), d.TokenHash)
	if after.LastSeenAt == nil {
		t.Fatal("last_seen_at not touched")
	}
}

func TestVerifyRejections(t *testing.T) {
	ctx := context.Background()
	st, _ := seededStore(t, "tok-1", nil)
	// 空令牌 / 错误令牌
	if _, err := Verify(ctx, st, "", model.DeviceMember); err != ErrUnauthorized {
		t.Fatalf("empty token must fail: %v", err)
	}
	if _, err := Verify(ctx, st, "wrong", model.DeviceMember); err != ErrUnauthorized {
		t.Fatalf("wrong token must fail: %v", err)
	}
	// scope 不匹配（member 令牌调 api 通道）
	if _, err := Verify(ctx, st, "tok-1", model.DeviceAPI); err != ErrUnauthorized {
		t.Fatalf("scope mismatch must fail: %v", err)
	}
	// 已吊销
	revoked := int64(123)
	st2, _ := seededStore(t, "tok-1", &revoked)
	if _, err := Verify(ctx, st2, "tok-1", model.DeviceMember); err != ErrUnauthorized {
		t.Fatalf("revoked must fail: %v", err)
	}
}
