// sync 云函数：增量同步下行 + 幂等上行。
// 契约：docs/02-数据库与同步.md §4（seq 增量、Idempotency-Key、LWW、追加型只增）
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
		log.Fatalf("sync: 数据库未就绪：%v", err)
	}
	defer pg.Close()

	if err := httpx.ListenAndServe(NewHandler(pg, nil)); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
