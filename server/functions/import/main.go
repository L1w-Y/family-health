// import 云函数：导入格式 v1 校验入库；挂 HTTP 触发器供外部脚本直调。
// 契约：docs/03-导入格式-v1.md（全文）；docs/04-技术选型.md §4（单事务整体写入）
package main

import (
	"context"
	"log"
	"net/http"

	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/store"
)

func main() {
	pg, err := store.Connect(context.Background())
	if err != nil {
		log.Fatalf("import: 数据库未就绪：%v", err)
	}
	defer pg.Close()

	if err := httpx.ListenAndServe(NewHandler(pg, nil)); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
