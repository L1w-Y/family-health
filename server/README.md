# server · Go 云函数

> 契约：`docs/04-技术选型.md` §2（五函数划分）、`docs/02-数据库与同步.md`（数据与协议）、`docs/03-导入格式-v1.md`（导入契约）。
> 工作规则见根 `AGENTS.md`。

## 目录

```
functions/   部署单元：一个目录 = 一个云函数，函数间禁止互相 import
  auth/      家庭口令 → 设备 token（契约：02 §3.2、§3.13）
  sync/      增量同步下行 + 幂等上行（契约：02 §4）
  import/    导入格式 v1 校验入库（契约：03 全文）
  export/    按成员导出 JSON + 附件打包（契约：02 §4.6）
  backup/    定时触发，pg_dump → 云存储（契约：04 §2；M1 必交付，免费版无数据回档）
internal/    共享领域包（只能被 functions 依赖，职责见各包 doc 注释）
scripts/     smoke.ps1 端到端验证（M1 验收标准）
```

## 新增一个云函数

1. `functions/<name>/main.go`，头部注释写契约出处；
2. 在 `docs/00-索引.md` 映射表加行；
3. 在 `scripts/smoke.ps1` 加对应验证用例。

## 本地开发

```powershell
cd server
go build ./...     # 结构检查
go test ./...      # 单元测试（validate 等纯逻辑包）
go vet ./...
```

函数入口的运行时约定（SCF Go 运行时 bootstrap 方式）在 M1 首个函数实现时确定并记录于此。

## 部署

M1 首次部署时将 tcb CLI 命令记录于此。
