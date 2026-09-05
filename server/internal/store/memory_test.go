package store

import (
	"context"
	"testing"

	"familyhealth/server/internal/model"
)

func newSeededMemory() *MemoryStore {
	m := NewMemoryStore()
	m.SeedFamily(model.Family{ID: "fam-1", Name: "测试家庭"})
	return m
}

func TestMemoryDeviceLifecycle(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	d := &model.Device{
		Base:        model.Base{ID: "dev-1", FamilyID: "fam-1", CreatedBy: "dev-1", Source: model.SourceApp, CreatedAt: 1, UpdatedAt: 1},
		Type:        model.DeviceMember,
		DisplayName: "爸爸",
		TokenHash:   "hash-1",
	}
	if err := m.CreateDevice(ctx, d); err != nil {
		t.Fatal(err)
	}
	got, err := m.FindDeviceByTokenHash(ctx, "hash-1")
	if err != nil || got.ID != "dev-1" {
		t.Fatalf("find by hash failed: %v %+v", err, got)
	}
	if _, err := m.FindDeviceByTokenHash(ctx, "nope"); err != ErrNotFound {
		t.Fatalf("expect ErrNotFound, got %v", err)
	}
	if err := m.TouchDevice(ctx, "dev-1", 99); err != nil {
		t.Fatal(err)
	}
	got, _ = m.FindDeviceByTokenHash(ctx, "hash-1")
	if got.LastSeenAt == nil || *got.LastSeenAt != 99 {
		t.Fatalf("touch failed: %+v", got.LastSeenAt)
	}
}

// insertTestRow 测试辅助：事务内取号并插入
func insertTestRow(t *testing.T, m *MemoryStore, table string, row model.JSONMap) int64 {
	t.Helper()
	var seq int64
	err := m.InTx(context.Background(), func(tx Tx) error {
		var err error
		seq, err = tx.NextSeq(context.Background())
		if err != nil {
			return err
		}
		row["seq"] = float64(seq)
		return tx.InsertRow(context.Background(), table, row)
	})
	if err != nil {
		t.Fatal(err)
	}
	return seq
}

func TestMemoryInsertAndListChanges(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	s1 := insertTestRow(t, m, "profiles", model.JSONMap{
		"id": "p1", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
		"name": "爷爷", "relation": "爷爷", "gender": "male",
	})
	insertTestRow(t, m, "profiles", model.JSONMap{
		"id": "p2", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(2), "updated_at": float64(2), "deleted": false,
		"name": "奶奶", "relation": "奶奶", "gender": "female",
	})
	changes, err := m.ListChanges(ctx, "fam-1", s1, 10)
	if err != nil || len(changes) != 1 || changes[0].Row["id"] != "p2" {
		t.Fatalf("incremental pull failed: %v %+v", err, changes)
	}
	// 家庭隔离
	changes, _ = m.ListChanges(ctx, "other-fam", 0, 10)
	if len(changes) != 0 {
		t.Fatalf("family isolation broken: %+v", changes)
	}
}

func TestMemoryProfileAndMedQueries(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	insertTestRow(t, m, "profiles", model.JSONMap{
		"id": "p1", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
		"name": "爷爷", "relation": "爷爷", "gender": "male",
	})
	ps, err := m.FindProfilesByName(ctx, "fam-1", "爷爷")
	if err != nil || len(ps) != 1 || ps[0].ID != "p1" {
		t.Fatalf("FindProfilesByName failed: %v %+v", err, ps)
	}
	if _, err := m.GetProfile(ctx, "fam-1", "nope"); err != ErrNotFound {
		t.Fatalf("expect ErrNotFound, got %v", err)
	}

	insertTestRow(t, m, "medication_items", model.JSONMap{
		"id": "m1", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
		"profile_id": "p1", "category": "long_term", "med_kind": "western",
		"name": "缬沙坦", "start_date": "2026-01-01",
	})
	meds, err := m.FindActiveMedsByName(ctx, "fam-1", "p1", "缬沙坦")
	if err != nil || len(meds) != 1 {
		t.Fatalf("FindActiveMedsByName failed: %v %+v", err, meds)
	}
	// end_date 非空 → 不再"进行中"
	insertTestRow(t, m, "medication_items", model.JSONMap{
		"id": "m2", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(2), "updated_at": float64(2), "deleted": false,
		"profile_id": "p1", "category": "long_term", "med_kind": "western",
		"name": "缬沙坦", "start_date": "2025-01-01", "end_date": "2026-01-01",
	})
	meds, _ = m.FindActiveMedsByName(ctx, "fam-1", "p1", "缬沙坦")
	if len(meds) != 1 {
		t.Fatalf("ended med must be excluded: %+v", meds)
	}
}

func TestMemoryTxRollback(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	err := m.InTx(ctx, func(tx Tx) error {
		seq, _ := tx.NextSeq(ctx)
		_ = tx.InsertRow(ctx, "profiles", model.JSONMap{
			"id": "px", "family_id": "fam-1", "created_by": "d", "source": "app",
			"created_at": float64(1), "updated_at": float64(1), "deleted": false, "seq": float64(seq),
			"name": "x",
		})
		return context.Canceled // 模拟失败
	})
	if err == nil {
		t.Fatal("expect error")
	}
	changes, _ := m.ListChanges(ctx, "fam-1", 0, 10)
	if len(changes) != 0 {
		t.Fatalf("rollback failed: %+v", changes)
	}
}

func TestMemoryUpdateRowLWW(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	insertTestRow(t, m, "profiles", model.JSONMap{
		"id": "p1", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
		"name": "爷爷", "relation": "爷爷", "gender": "male", "notes": "旧备注",
	})
	// 整行覆盖：未提供 notes → 置 nil（02 §4.3 不合并字段）
	err := m.InTx(ctx, func(tx Tx) error {
		seq, _ := tx.NextSeq(ctx)
		return tx.UpdateRow(ctx, "profiles", model.JSONMap{
			"id": "p1", "family_id": "fam-1", "updated_at": float64(2), "seq": float64(seq),
			"name": "爷爷", "relation": "祖父", "gender": "male",
		})
	})
	if err != nil {
		t.Fatal(err)
	}
	p, _ := m.GetProfile(ctx, "fam-1", "p1")
	if p.Relation != "祖父" || p.Notes != "" || p.CreatedBy != "dev-1" {
		t.Fatalf("LWW whole-row overwrite wrong: %+v", p)
	}
}

func TestMemoryIdempotency(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	_, ok, _ := m.GetIdempotentResponse(ctx, "dev-1", "k1")
	if ok {
		t.Fatal("expect miss")
	}
	_ = m.InTx(ctx, func(tx Tx) error {
		return tx.PutIdempotentResponse(ctx, "dev-1", "k1", []byte(`{"ok":true}`), 1)
	})
	b, ok, _ := m.GetIdempotentResponse(ctx, "dev-1", "k1")
	if !ok || string(b) != `{"ok":true}` {
		t.Fatalf("idempotency replay failed: %v %s", ok, b)
	}
}

func TestMemoryResolveCanonical(t *testing.T) {
	ctx := context.Background()
	m := newSeededMemory()
	insertTestRow(t, m, "watch_items", model.JSONMap{
		"id": "w1", "family_id": "fam-1", "created_by": "dev-1", "source": "app",
		"created_at": float64(1), "updated_at": float64(1), "deleted": false,
		"profile_id": "p1", "canonical_name": "ACR",
		"aliases": []any{"尿微量白蛋白/肌酐比", "ACR(尿)"}, "sort_order": float64(1),
	})
	c, ok, err := m.ResolveCanonical(ctx, "fam-1", "p1", "ACR(尿)")
	if err != nil || !ok || c != "ACR" {
		t.Fatalf("resolve failed: %v %v %q", err, ok, c)
	}
	if _, ok, _ := m.ResolveCanonical(ctx, "fam-1", "p1", "未知指标"); ok {
		t.Fatal("expect no match")
	}
}

func TestSchemaWhitelist(t *testing.T) {
	if !KnownTable("profiles") || KnownTable("devices") || KnownTable("import_records") {
		t.Fatal("sync whitelist wrong")
	}
	if k, _ := TableKindOf("measurements"); k != KindAppendOnly {
		t.Fatal("measurements must be append-only")
	}
	if k, _ := TableKindOf("profiles"); k != KindMutable {
		t.Fatal("profiles must be mutable")
	}
	if !IsJSONBColumn("watch_items", "aliases") || IsJSONBColumn("profiles", "name") {
		t.Fatal("jsonb metadata wrong")
	}
}
