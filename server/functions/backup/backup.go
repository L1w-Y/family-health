package main

// 备份核心逻辑：dump → 上传 → 滚动清理（保留 RetentionDays 天）。
// 存储与 dump 均为接口注入，单测不依赖真实 PG/云存储。
import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// Dumper 全库导出（生产：pg_dump custom 格式）
type Dumper interface {
	Dump(ctx context.Context, databaseURL, destPath string) error
}

// ExecDumper pg_dump -Fc（04 §2 架构：全量 dump）
type ExecDumper struct {
	Path string // pg_dump 可执行文件路径（env PG_DUMP_PATH，缺省 "pg_dump"）
}

func (e ExecDumper) Dump(ctx context.Context, databaseURL, destPath string) error {
	path := e.Path
	if path == "" {
		path = "pg_dump"
	}
	cmd := exec.CommandContext(ctx, path, "-Fc", "-d", databaseURL, "-f", destPath)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("pg_dump 失败: %v: %s", err, out)
	}
	return nil
}

// Uploader 对象存储抽象（云存储 SDK 接入前以本接口隔离；04 §2 云存储）
type Uploader interface {
	Upload(ctx context.Context, localPath, key string) error
	List(ctx context.Context, prefix string) ([]string, error)
	Delete(ctx context.Context, key string) error
}

// Config 运行配置
type Config struct {
	DatabaseURL   string
	RetentionDays int
	Uploader      Uploader
	Dumper        Dumper
	Now           func() time.Time
}

func (c *Config) withDefaults() Config {
	out := *c
	if out.RetentionDays <= 0 {
		out.RetentionDays = 30
	}
	if out.Now == nil {
		out.Now = time.Now
	}
	return out
}

// backupKey 当日备份对象键
func backupKey(t time.Time) string {
	return "backups/health-" + t.UTC().Format("2006-01-02") + ".pgdump"
}

// Result 备份结果
type Result struct {
	OK           bool     `json:"ok"`
	Key          string   `json:"key"`
	DeletedStale []string `json:"deleted_stale,omitempty"`
}

// Run 执行一次备份（幂等：同日重复执行覆盖同 key）
func Run(ctx context.Context, cfg *Config) (*Result, error) {
	c := cfg.withDefaults()
	key := backupKey(c.Now())

	tmp := filepath.Join(os.TempDir(), strings.ReplaceAll(key, "/", "-"))
	defer os.Remove(tmp)
	if err := c.Dumper.Dump(ctx, c.DatabaseURL, tmp); err != nil {
		return nil, err
	}
	if err := c.Uploader.Upload(ctx, tmp, key); err != nil {
		return nil, fmt.Errorf("上传失败: %w", err)
	}
	deleted, err := sweepStale(ctx, c.Uploader, c.Now(), c.RetentionDays)
	if err != nil {
		return nil, fmt.Errorf("滚动清理失败: %w", err)
	}
	return &Result{OK: true, Key: key, DeletedStale: deleted}, nil
}

// sweepStale 删除保留期之外的备份（04 §2：滚动 30 天）
func sweepStale(ctx context.Context, u Uploader, now time.Time, retentionDays int) ([]string, error) {
	keys, err := u.List(ctx, "backups/")
	if err != nil {
		return nil, err
	}
	cutoff := now.UTC().AddDate(0, 0, -retentionDays).Truncate(24 * time.Hour)
	var stale []string
	for _, k := range keys {
		d, ok := parseKeyDate(k)
		if !ok {
			continue // 非本函数管理的对象不碰
		}
		if d.Before(cutoff) {
			stale = append(stale, k)
		}
	}
	sort.Strings(stale)
	for _, k := range stale {
		if err := u.Delete(ctx, k); err != nil {
			return stale, fmt.Errorf("删除 %s 失败: %w", k, err)
		}
	}
	return stale, nil
}

// parseKeyDate 解析 backups/health-YYYY-MM-DD.pgdump
func parseKeyDate(key string) (time.Time, bool) {
	base := filepath.Base(key)
	if !strings.HasPrefix(base, "health-") || !strings.HasSuffix(base, ".pgdump") {
		return time.Time{}, false
	}
	d, err := time.ParseInLocation("2006-01-02", strings.TrimSuffix(strings.TrimPrefix(base, "health-"), ".pgdump"), time.UTC)
	if err != nil {
		return time.Time{}, false
	}
	return d, true
}
