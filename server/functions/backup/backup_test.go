package main

import (
	"context"
	"os"
	"path/filepath"
	"sort"
	"testing"
	"time"
)

// fakeDumper 不落 PG：写固定内容充当 dump 产物
type fakeDumper struct{ content string }

func (f fakeDumper) Dump(_ context.Context, _, destPath string) error {
	return os.WriteFile(destPath, []byte(f.content), 0o644)
}

func fixedNow(y int, m time.Month, d int) func() time.Time {
	return func() time.Time { return time.Date(y, m, d, 3, 0, 0, 0, time.UTC) }
}

func seedBackups(t *testing.T, u *FSUploader, dates ...string) {
	t.Helper()
	for _, d := range dates {
		p := filepath.Join(t.TempDir(), "seed-"+d)
		if err := os.WriteFile(p, []byte("dump-"+d), 0o644); err != nil {
			t.Fatal(err)
		}
		if err := u.Upload(context.Background(), p, "backups/health-"+d+".pgdump"); err != nil {
			t.Fatal(err)
		}
	}
}

func TestBackupRunAndRetention(t *testing.T) {
	dir := t.TempDir()
	u := &FSUploader{Dir: dir}
	// 预置：31 天前（应删）、30 天前（边界保留）、10 天前（保留）、非备份对象（不碰）
	now := time.Date(2026, 9, 5, 3, 0, 0, 0, time.UTC)
	seedBackups(t, u, "2026-08-05", "2026-08-06", "2026-08-26")
	if err := os.MkdirAll(filepath.Join(dir, "backups"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "backups", "readme.txt"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	cfg := &Config{
		DatabaseURL: "postgres://x",
		Uploader:    u,
		Dumper:      fakeDumper{content: "fresh-dump"},
		Now:         func() time.Time { return now },
	}
	res, err := Run(context.Background(), cfg)
	if err != nil {
		t.Fatal(err)
	}
	if res.Key != "backups/health-2026-09-05.pgdump" {
		t.Fatalf("key wrong: %+v", res)
	}
	// 新备份已上传
	data, err := os.ReadFile(filepath.Join(dir, "backups", "health-2026-09-05.pgdump"))
	if err != nil || string(data) != "fresh-dump" {
		t.Fatalf("upload wrong: %v %q", err, data)
	}
	// 滚动清理：仅 2026-08-05 被删（>30 天）
	if len(res.DeletedStale) != 1 || res.DeletedStale[0] != "backups/health-2026-08-05.pgdump" {
		t.Fatalf("stale wrong: %+v", res.DeletedStale)
	}
	keys, _ := u.List(context.Background(), "backups/")
	sort.Strings(keys)
	expect := []string{
		"backups/health-2026-08-06.pgdump",
		"backups/health-2026-08-26.pgdump",
		"backups/health-2026-09-05.pgdump",
		"backups/readme.txt", // 非本函数管理对象不受影响
	}
	if len(keys) != len(expect) {
		t.Fatalf("remaining wrong: %v", keys)
	}
	for i := range expect {
		if keys[i] != expect[i] {
			t.Fatalf("remaining wrong: %v", keys)
		}
	}
}

func TestBackupKeyAndDateParse(t *testing.T) {
	k := backupKey(time.Date(2026, 1, 2, 0, 0, 0, 0, time.UTC))
	if k != "backups/health-2026-01-02.pgdump" {
		t.Fatalf("key: %s", k)
	}
	d, ok := parseKeyDate(k)
	if !ok || d.Format("2006-01-02") != "2026-01-02" {
		t.Fatalf("parse: %v %v", d, ok)
	}
	if _, ok := parseKeyDate("backups/readme.txt"); ok {
		t.Fatal("non-backup key must not parse")
	}
}

func TestCOSUploaderStub(t *testing.T) {
	c := &COSUploader{}
	if err := c.Upload(context.Background(), "a", "b"); err != errCOSNotConfigured {
		t.Fatalf("expect stub error, got %v", err)
	}
}
