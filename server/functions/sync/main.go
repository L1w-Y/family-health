// sync 云函数：增量同步下行 + 幂等上行。
// 契约：docs/02-数据库与同步.md §4（seq 增量、Idempotency-Key、LWW、用药写入路径规则）
// TODO M1：实现。下行 GET /sync?since=&limit=；上行 POST /sync（表白名单 + 写前取号 + 软删墓碑）。
package main

func main() {}
