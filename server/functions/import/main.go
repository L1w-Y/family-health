// import 云函数：导入格式 v1 校验入库；挂 HTTP 触发器供外部脚本直调。
// 契约：docs/03-导入格式-v1.md（全文）；docs/04-技术选型.md §4（单事务整体写入）
// TODO M1：实现。管线：限长 → validate.ParseAndValidate → profile 解析 → 幂等（import_id）→
// dry_run 短路 → 单事务写入（事件/报告/指标/用药批次）→ §6.1 响应。
package main

func main() {}
