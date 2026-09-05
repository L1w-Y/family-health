// auth 云函数：家庭口令校验 → 签发设备 token（哈希入库）。
// 契约：docs/02-数据库与同步.md §3.2 devices、§3.13 api_tokens（已并入）；docs/04-技术选型.md §2
// TODO M1：实现。输入 {secret, display_name}；输出 {token, device}；口令错误统一 401 不暴露细节。
package main

func main() {}
