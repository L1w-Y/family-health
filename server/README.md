# server · Go 服务

> 契约：`docs/04-技术选型.md` §2（部署形态与架构）、`docs/02-数据库与同步.md`（数据与协议）、`docs/03-导入格式-v1.md`（导入契约）。
> 工作规则见根 `AGENTS.md`。
> 部署形态：轻量应用服务器单机 Docker Compose（编排与运维脚本见 `deploy/`），2026-09-07 由 CloudBase 云托管迁入，后端代码零改动。

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

## 服务入口的运行时约定

每个服务是独立 `main` 包，编译为单个静态二进制，以 Docker 容器运行：

- 进程启动后连接 `DATABASE_URL`，随后 `net/http` 监听环境变量 **`PORT`**（部署注入 9000，缺省 8080）；
- 路由同时挂业务路径（`/auth`、`/sync`、`/api/v1/import`、`/backup`）与根路径 `/`，
  兼容代理「路径透传」与「去前缀」两种形态；
- 对外只暴露 Caddy（:80/:443），按路径前缀分发到各服务容器（见 `deploy/Caddyfile`）；
- backup 不经 Caddy 暴露，由宿主机 cron 每日经容器内网调用 `POST /backup`，
  `BACKUP_TOKEN`（Bearer）保护端点（见 `deploy/backup-trigger.sh`）；
- pg 容器不暴露宿主机端口，仅 Docker 内网可达。

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
| `DATABASE_URL` | PG 连接串（容器内网 `sslmode=disable`；本地调试另行指定） | 全部 |
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

## 部署（轻量应用服务器 · 自建 Docker Compose）

服务器：腾讯云轻量香港 2核1G（Ubuntu 24.04 + Docker）。一次部署 = 编译 → 上传 → compose up → 初始化 → 冒烟。

```powershell
# 1. 交叉编译（linux/amd64，输出到 build/<服务>/main）
$env:GOOS='linux'; $env:GOARCH='amd64'; $env:CGO_ENABLED='0'
go build -o build/auth/main   ./functions/auth
go build -o build/sync/main   ./functions/sync
go build -o build/import/main ./functions/import
go build -o build/backup/main ./functions/backup

# 2. 上传（build/ scripts/ deploy/ → 服务器 ~/family-health/，保持 build 层级）
scp -r build/auth build/sync build/import build/backup scripts deploy <user>@<服务器IP>:~/family-health/

# 3. 服务器上：构建并启动（首次会自动拉取 postgres:16-alpine / caddy:2-alpine）
ssh <user>@<服务器IP> "chmod +x ~/family-health/build/*/main && cd ~/family-health/deploy && docker compose up -d --build"

# 4. 初始化数据库（建表 + 家庭行；重置为干净态用 bash deploy/server-reset.sh）
ssh <user>@<服务器IP> "cd ~/family-health/deploy && docker compose exec -T pg psql -U postgres -d familyhealth -f /scripts/schema.sql && docker compose exec -T pg psql -U postgres -d familyhealth -f /scripts/init-family.sql"

# 5. 全链路冒烟（HTTP 验证期直接打 IP）
./scripts/smoke.ps1 -BaseUrl 'http://<服务器IP>' -FamilySecret '<家庭口令>'
```

运维要点：

- 密钥集中在 `deploy/.env`（不入库）：`PG_PASSWORD` / `FAMILY_ID` / `FAMILY_SECRET` / `TOKEN_SALT` / `BACKUP_TOKEN` / `SITE_ADDRESS`；
- HTTPS：`SITE_ADDRESS` 改为域名（DuckDNS 免费子域指向服务器 IP）后 `docker compose restart caddy`，自动签发/续期；
- 备份：cron 每日 03:30 触发（`server-reset.sh` 幂等安装），转储在 `deploy/backups/backups/`，滚动 30 天；
- 改家庭口令：改 `.env` 的 `FAMILY_SECRET` 后 `docker compose restart auth` 即可（库中不存口令哈希）；
- 更新服务：重新编译 → scp 对应 `build/<服务>/main`（注意 chmod +x）→ `docker compose up -d --build <服务>`。
