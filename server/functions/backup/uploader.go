package main

// 存储实现：FSUploader（本地目录，开发/测试）与 COSUploader（云存储，SDK 接入待部署确认）。
// 契约：docs/04-技术选型.md §2（云存储滚动 30 天）
import (
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"sort"
)

// FSUploader 本地文件系统实现：BACKUP_STORAGE=fs 时使用（开发自验、单测）
type FSUploader struct {
	Dir string
}

func (f *FSUploader) path(key string) string {
	return filepath.Join(f.Dir, filepath.FromSlash(key))
}

func (f *FSUploader) Upload(_ context.Context, localPath, key string) error {
	dest := f.path(key)
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	src, err := os.Open(localPath)
	if err != nil {
		return err
	}
	defer src.Close()
	out, err := os.Create(dest)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, src)
	return err
}

func (f *FSUploader) List(_ context.Context, prefix string) ([]string, error) {
	base := f.path(prefix)
	var keys []string
	entries, err := os.ReadDir(base)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	for _, e := range entries {
		if !e.IsDir() {
			keys = append(keys, prefix+e.Name())
		}
	}
	sort.Strings(keys)
	return keys, nil
}

func (f *FSUploader) Delete(_ context.Context, key string) error {
	err := os.Remove(f.path(key))
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

// COSUploader 云存储实现（预留）。
// 待部署决策：腾讯云 COS XML API（环境内直传，secret 注入）或 CloudBase 存储 SDK。
// 确定后在本类型内实现三个方法，Config/Run 逻辑不变。
// 需要的环境变量（README 已记录）：TCB_ENV_ID、COS_SECRET_ID、COS_SECRET_KEY、COS_BUCKET。
type COSUploader struct{}

var errCOSNotConfigured = errors.New(
	"backup: 云存储上传未配置（COSUploader 为预留接口，接入方式见 server/README.md「函数入口的运行时约定」）")

func (c *COSUploader) Upload(_ context.Context, _, _ string) error { return errCOSNotConfigured }
func (c *COSUploader) List(_ context.Context, _ string) ([]string, error) {
	return nil, errCOSNotConfigured
}
func (c *COSUploader) Delete(_ context.Context, _ string) error { return errCOSNotConfigured }

// 编译期接口断言
var (
	_ Uploader = (*FSUploader)(nil)
	_ Uploader = (*COSUploader)(nil)
)
