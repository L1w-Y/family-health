// backup 云函数：定时触发（每日），pg_dump 全库 → 云存储，滚动保留 30 天。
// 契约：docs/04-技术选型.md §2；免费体验版无数据回档，本函数不可延期（M1 交付）。
package main

import (
	"log"
	"net/http"
	"os"

	"familyhealth/server/internal/httpx"
)

func main() {
	uploader, err := newUploaderFromEnv()
	if err != nil {
		log.Fatalf("backup: 存储配置未就绪：%v", err)
	}
	cfg := &Config{
		DatabaseURL:   os.Getenv("DATABASE_URL"),
		RetentionDays: 30,
		Uploader:      uploader,
		Dumper:        ExecDumper{Path: httpx.EnvOr("PG_DUMP_PATH", "pg_dump")},
	}
	if cfg.DatabaseURL == "" {
		log.Fatal("backup: DATABASE_URL 未配置")
	}
	if err := httpx.ListenAndServe(NewHandler(cfg)); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}

// newUploaderFromEnv 存储实现选择：
// BACKUP_STORAGE=fs（本地目录，开发/测试用，BACKUP_FS_DIR 指定目录）；
// 缺省 cos（云存储，SDK 接入待部署验证，见 server/README.md「待部署验证清单」）。
func newUploaderFromEnv() (Uploader, error) {
	if os.Getenv("BACKUP_STORAGE") == "fs" {
		dir := httpx.EnvOr("BACKUP_FS_DIR", "./backups")
		return &FSUploader{Dir: dir}, nil
	}
	return &COSUploader{}, nil
}
