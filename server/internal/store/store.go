// Package store 托管 PostgreSQL 访问层：连接、全局 seq 取号、事务辅助。
// 契约：docs/04-技术选型.md §4（PG 落地要点）、docs/02-数据库与同步.md §4（seq 语义）
package store

import (
	"context"
	"database/sql"
	"os"
)

// DB 全局连接（云函数单并发模型下安全）
var DB *sql.DB

// Connect 按环境变量 DATABASE_URL 建立连接。
// TODO M1：连接池参数（云函数场景：max_open_conns=1~2）、sslmode、超时。
func Connect(ctx context.Context) error {
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		return sql.ErrConnDone
	}
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return err
	}
	if err := db.PingContext(ctx); err != nil {
		return err
	}
	DB = db
	return nil
}

// NextSeq 全局递增序号（02 §2 seq 语义；实现：CREATE SEQUENCE sync_seq）。
// TODO M1：实现，所有写入路径统一经此取号。
func NextSeq(ctx context.Context, tx *sql.Tx) (int64, error) {
	return 0, sql.ErrNoRows
}
