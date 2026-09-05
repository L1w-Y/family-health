// auth 云函数：家庭口令校验 → 签发设备 token（哈希入库）。
// 契约：docs/02-数据库与同步.md §3.2 devices、§3.13 api_tokens（已并入）；docs/04-技术选型.md §2
// 输入 {secret, display_name, device_type?}；输出 {token, device}；口令错误统一 401 不暴露细节。
package main

import (
	"context"
	"log"
	"net/http"

	"familyhealth/server/internal/httpx"
	"familyhealth/server/internal/store"
)

func main() {
	var st store.Store
	pg, err := store.Connect(context.Background())
	if err != nil {
		log.Printf("auth: DATABASE_URL 未就绪，退出：%v", err)
		log.Fatal(err)
	}
	st = pg
	defer st.Close()

	if err := httpx.ListenAndServe(NewHandler(st, nil)); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
