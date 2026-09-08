# AGENTS.md · AI 协作总纲

> 任何 AI（或人）在本仓库动手前，必须先读本文件 + 任务相关的契约章节。
> 本文件是项目唯一的工作规则来源；契约文档是唯一的产品事实来源。

## 1. 项目一句话

家庭健康管理 App：Android（Kotlin/Compose）+ 自建服务端（腾讯云轻量服务器·香港，Docker Compose 单机：Go 服务 auth/sync/import/backup + PostgreSQL 16 + Caddy 反代）。家庭内部使用，无账号体系（家庭口令 + 设备署名）。

## 2. 目录地图

```
Family-Health/
├── AGENTS.md            ← 本文件，工作规则
├── docs/                ← 契约文档（唯一事实来源），索引见 docs/00-索引.md；运维见 docs/06-运维手册.md
├── prototype/           ← 浏览器可交互原型（静态，python -m http.server 预览）
│                          页面/交互的参照实现，改 UI 时先看它
├── server/              ← Go 服务端（部署单元 = functions/ 下每个目录）
│   ├── functions/       ← auth / sync / import / export / backup，一服务一目录
│   ├── internal/        ← 共享领域包：model / validate / store / authn / httpx
│   ├── deploy/          ← 自建部署：docker-compose / Caddyfile / 备份与重置脚本 / .env（密钥不入库）
│   ├── build/           ← 交叉编译产物（linux/amd64，不入库）
│   └── scripts/         ← schema.sql 建表、init-family.sql、smoke.ps1 冒烟、seed-demo.ps1 灌测试数据
└── app/                 ← Android 工程（Kotlin/Compose/Room，结构与构建见 app/README.md）
```

## 3. 契约体系与同步规则（防文档-代码漂移）

**规则 1 · 契约先行**：任何"行为变更"（字段、枚举、协议、页面逻辑、业务规则）必须先改 `docs/` 对应契约，再写代码。纯实现细节（性能、重构、bug 修复且不改行为）可直接改代码。

**规则 2 · 双向锚点**：
- 代码侧：每个源文件头部注释标注契约出处，格式 `// 契约：docs/0X-*.md §Y`；
- 文档侧：`docs/00-索引.md` 的映射表记录每个契约条目对应的代码位置与测试位置。新增/移动模块时必须更新映射表。

**规则 3 · 任务完成定义（DoD）**：代码 + 受影响契约 + 映射表 + 验证脚本/测试，四者同状态才算完成。只改代码不动契约的提交视为未完成。

**规则 3a · "行为变更"从宽认定**：凡是**用户可见**的变化——页面/入口的增删或迁移、新交互（手势、弹窗、提醒）、数据展示规则变化——都算行为变更，必须先改契约；只有纯视觉微调（间距/字号/颜色）才算实现细节。拿不准时按行为变更处理。

**规则 3b · 文档同步范围不止 docs/0X**：环境、部署形态、目录结构、构建方式变化时，`AGENTS.md`（§1/§2/§6）与对应 `README` 同属"受影响文档"，必须与契约同轮更新（2026-09 部署转折时曾因此漏更）。

**规则 3c · 探索性转折的契约时机**：平台限制等导致的被迫改道允许先探索，但转折开始时必须建"契约同步"TODO，落地当轮补齐全部受影响文档后才算完成。

**规则 3d · 里程碑漂移检查**：每个里程碑（或每轮批量改动）结束时，重读 `AGENTS.md` §1/§2/§6 与相关 README/契约对应章节，自问"这段还属实吗"，不属实当轮修。

## 4. 结构硬规则（防巨型文件、防耦合）

1. **部署级解耦**：一个云函数 = `functions/` 下一个独立目录，函数间禁止直接 import，共享逻辑只能进 `internal/` 领域包。
2. **实体级拆文件**：一个实体/一个职责一个文件；单文件超过约 300 行必须拆分。禁止 `utils.go` / `common.go` 这类垃圾抽屉——共享代码必须归入有名字的领域包。
3. **领域包单一职责**：`model`（数据结构，零逻辑）、`validate`（导入校验）、`store`（PG 访问）、`authn`（鉴权）、`httpx`（HTTP 辅助）。跨领域调用只能从上到下：functions → internal。
4. Android（届时）：按 feature 分包（records / meds / overview / mine / import），feature 内自含页面+状态，共享只有 design-system 与 sync-client 两个模块。

## 5. 术语表（代码标识符必须与契约中文对齐）

| 中文 | 代码标识 | 说明 |
|---|---|---|
| 成员档案 | Profile | 被记录的家庭成员 |
| 设备 | Device | 即身份；type=member/api |
| 复查事件 | CheckupEvent | 报告/用药变化的容器 |
| 指标项目 | IndicatorItem | 原文名存档，归并靠 WatchItem.aliases |
| 用药条目 | MedicationItem | 生命周期 start/end + supersedes |
| 用药变化 | MedChange | 调整批次，事由只在这里 |
| 今日用药 | DailyMedItem | 执行层清单，与方案无外键关联 |
| 便签 | Note | remind_at 自驱动本地提醒 |

## 6. 环境与命令

- 生产服务器：腾讯云轻量（香港）`43.161.199.183`（Ubuntu 24.04 + Docker；访问方式与密钥位置见 `docs/06-运维手册.md`）
- 服务端本地测试：`cd server && go test ./...`；结构检查：`go build ./...`
- 服务端部署：交叉编译 → scp → `docker compose up -d --build <服务>`（完整步骤见 server/README.md「部署」与 docs/06）
- 全链路冒烟：`server/scripts/smoke.ps1 -BaseUrl http://43.161.199.183 -FamilySecret <口令>`
- 灌测试数据：`server/scripts/seed-demo.ps1`（假名全覆盖）；清空重置：服务器上 `bash deploy/server-reset.sh`
- App 构建：`cd app && ./gradlew.bat assembleDebug`（环境要求见 app/README.md）

## 7. AI 工作指南

1. 接到任务 → 先定位契约章节（docs/00-索引.md）→ 读契约 → 读映射表中的既有代码 → 再动手。
2. 不跨模块复制逻辑；发现可复用逻辑时，提升到对应 internal 领域包，而不是粘贴。
3. 与既有约定冲突时，停下来在回复中指出，不要擅自另起一套。
4. 原型 `prototype/` 是 UI 参照，不是功能代码；不要 import 或搬运其 JS 到 app/。
