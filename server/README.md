# server · Go 云函数

> 契约：`docs/04-技术选型.md` §2（五函数划分）、`docs/02-数据库与同步.md`（数据与协议）、`docs/03-导入格式-v1.md`（导入契约）。
> 工作规则见根 `AGENTS.md`。

## 目录

```
functions/   部署单元：一个目录 = 一个云函数，函数间禁止互相 import
  auth/      家庭口令 → 设备 token（契约：02 §3.2、§3.13）
  sync/      增量同步下行 + 幂等上行（契约：02 §4）
  import/    导入格式 v1 校验入库（契约：03 全文）
  export/    按成员导出 JSON + 附件打包（契约：02 §4.6；M3 实现）
  backup/    定时触发，pg_dump → 云存储（契约：04 §2）
internal/    共享领域包（只能被 functions 依赖，职责见各包 doc 注释）
  model/     数据结构（零逻辑）      validate/ 导入格式 v1 校验（03 契约代码形态）
  store/     PG 访问层（接口 + 内存 + pgx）  authn/ 设备令牌（sha256+盐，只存哈希）
  httpx/     HTTP 辅助               idgen/   UUID v4
scripts/     schema.sql 建表脚本、smoke.ps1 端到端验证、fixtures/ 测试载荷
```

## 函数入口的运行时约定（M1 决策记录）

CloudBase Go 云函数采用 **custom runtime** 形态部署：

- 每个函数是独立 `main` 包，编译为单个二进制；
- 进程启动后连接 `DATABASE_URL`，随后 `net/http` 监听环境变量 **`PORT`**（缺省 8080）；
- 路由同时挂业务路径（`/auth`、`/sync`、`/api/v1/import`、`/backup`）与根路径 `/`，
  兼容触发器「路径透传」与「去前缀」两种形态，部署时无需额外配置；
- backup 由定时触发器周期调用 `POST /backup`，可用 `BACKUP_TOKEN`（Bearer）保护端点。

部署假设：CloudBase 支持 Go custom runtime（PORT 监听）或 SCF custom runtime 镜像部署；
若实际仅支持事件函数形态，则需在每个 main.go 外套一层 SCF bootstrap（handler 已按
`NewHandler(store, config)` 与 main 解耦，适配只动 main.go）。

## 依赖选型

唯一重依赖 **`github.com/jackc/pgx/v5`**（store 生产实现）。理由：

- 托管 PostgreSQL 的原生驱动，`pgxpool` 连接池 + 原生 jsonb/date/numeric 类型支持，
  与 02 文档表结构（jsonb、date、numeric 混用）匹配最好；
- `database/sql` + lib/pq 已维护模式，pgx 是当前 PG 生态事实标准；
- 单测不依赖它（内存实现覆盖全部逻辑），生产路径才触达。

其余均为标准库（authn 用 crypto/sha256 + 环境盐，不引 bcrypt——令牌为 256 位高熵随机串，
暴力破解无意义，盐仅防彩虹表/批量撞库）。

## 环境变量

| 变量 | 用途 | 函数 |
|---|---|---|
| `DATABASE_URL` | 托管 PG 连接串（sslmode=require） | 全部 |
| `FAMILY_SECRET` | 家庭口令 | auth |
| `FAMILY_ID` | 家庭 id（单家庭部署，02 §3.2 演进风险已知） | auth |
| `TOKEN_SALT` | 令牌哈希环境级盐（生产必配） | authn（全部） |
| `PORT` | 监听端口（平台注入） | 全部 |
| `BACKUP_TOKEN` | backup 端点保护（可选） | backup |
| `BACKUP_STORAGE` | `fs`（本地目录）/ 缺省 `cos`（预留） | backup |
| `BACKUP_FS_DIR` | fs 模式备份目录 | backup |
| `PG_DUMP_PATH` | pg_dump 路径（缺省 PATH 查找） | backup |
| `TCB_ENV_ID` / `COS_*` | COSUploader 接入时启用（预留） | backup |

## 实现决策记录（契约未明示处，供评审）

1. **可改表集合**：02 §4.3 明示 profiles/medication_items/reminders/watch_items；
   实现另含 `notes`（done 勾选）与 `daily_med_items`（余量手填），二者业务上必然可改，
   语义与 §4.3 整行 LWW 一致。`med_changes` 也允许 update（批次备注修正）。
2. **medication_changes_note 落库**：02 §3.5 已设专列，03 §3 的展示性备注原样落列。
3. **`linked_event_id: "$event"`**：03 §5 允许引用"同批载荷 A 新建事件"，但 03 §2
   信封一次只载一种载荷，本批不可能有事件 → 实现拒绝（SCHEMA）。若未来支持复合载荷再放开。
4. **sync 下行 `next`**：返回本次最后一行的 seq；无变更时回显 since。limit 上限 500。
5. **附件归属回填**：导入 `attachment_ids` 命中的附件在导入事务内回填 `report_id`
   （attachments 表 `report_id` 可空，因为附件先于报告上传——schema.sql 已按此落地）。
6. **备份上传**：`Uploader` 接口已抽象，`COSUploader` 为预留桩（返回未配置错误），
   SDK 接入方式待部署环境确认；`FSUploader` 可用于开发自验完整流程。
7. **错误码扩展**：03 §6.3 标注"常见 code"非穷举，实现补充
   `DATE_ORDER` / `VALUE_RANGE` / `TOO_LONG` / `MATCH_NOT_FOUND`，见 `internal/validate/errors.go`。

## 本地开发

```powershell
cd server
go build ./...     # 结构检查
go test ./...      # 单元测试（全部基于内存实现/纯逻辑，不依赖真实 PG）
go vet ./...
```

## 部署

```powershell
# 1. 建表（托管 PG 控制台 SQL 窗口或 psql）
psql $env:DATABASE_URL -f scripts/schema.sql

# 2. 各函数编译（示例：linux/amd64）
$env:GOOS='linux'; $env:GOARCH='amd64'
go build -o dist/auth      ./functions/auth
go build -o dist/sync      ./functions/sync
go build -o dist/import    ./functions/import
go build -o dist/backup    ./functions/backup

# 3. CloudBase 创建 Go custom runtime 函数，上传二进制，配置环境变量与触发器
#    （tcb CLI 具体命令在 M1 首次部署时补充于此）

# 4. 全链路冒烟
./scripts/smoke.ps1 -BaseUrl 'https://<触发器域名>' -FamilySecret '<家庭口令>'
```
