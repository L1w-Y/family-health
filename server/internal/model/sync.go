package model

// 契约：docs/02-数据库与同步.md §4（同步协议）

// Change 一条同步变更（表 + 行）
type Change struct {
	Table string  `json:"table"`
	Row   JSONMap `json:"row"`
}

// PullRequest 增量拉取请求：GET /sync?since=&limit=
type PullRequest struct {
	Since int64 `json:"since"`
	Limit int   `json:"limit"`
}

// PullResponse 增量拉取响应（按 seq 升序；客户端推进 last_seq = next）
type PullResponse struct {
	Changes []Change `json:"changes"`
	Next    int64    `json:"next"`
	HasMore bool     `json:"has_more"`
}

// WriteRequest 上行写入（携带客户端生成 UUID 主键 + Idempotency-Key 头）
type WriteRequest struct {
	Table string  `json:"table"`
	Op    string  `json:"op"` // insert / update / delete(软删)
	Row   JSONMap `json:"row"`
}

// WriteResponse 上行写入响应
type WriteResponse struct {
	OK      bool   `json:"ok"`
	ID      string `json:"id,omitempty"`
	Seq     int64  `json:"seq,omitempty"`
	Message string `json:"message,omitempty"`
}
