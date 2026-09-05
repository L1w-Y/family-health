# AGENTS.md · AI 协作总纲

> 任何 AI（或人）在本仓库动手前，必须先读本文件 + 任务相关的契约章节。
> 本文件是项目唯一的工作规则来源；契约文档是唯一的产品事实来源。

## 1. 项目一句话

家庭健康管理 App：Android（Kotlin/Compose）+ 腾讯云开发 CloudBase（Go 云函数 + 托管 PostgreSQL + 云存储）。家庭内部使用，无账号体系（家庭口令 + 设备署名）。

## 2. 目录地图

```
Family-Health/
├── AGENTS.md            ← 本文件，工作规则
├── docs/                ← 契约文档（唯一事实来源），索引见 docs/00-索引.md
├── prototype/           ← 浏览器可交互原型（静态，python -m http.server 预览）
│                          页面/交互的参照实现，改 UI 时先看它
├── server/              ← Go 云函数（部署单元 = functions/ 下每个目录）
│   ├── functions/       ← auth / sync / import / export / backup，一函数一目录
│   ├── internal/        ← 共享领域包：model / validate / store / authn / httpx
│   └── scripts/         ← smoke.ps1 等端到端验证脚本
└── app/                 ← Android 工程（M2 创建，结构届时在本节补充）
```

## 3. 契约体系与同步规则（防文档-代码漂移）

**规则 1 · 契约先行**：任何"行为变更"（字段、枚举、协议、页面逻辑、业务规则）必须先改 `docs/` 对应契约，再写代码。纯实现细节（性能、重构、bug 修复且不改行为）可直接改代码。

**规则 2 · 双向锚点**：
- 代码侧：每个源文件头部注释标注契约出处，格式 `// 契约：docs/0X-*.md §Y`；
- 文档侧：`docs/00-索引.md` 的映射表记录每个契约条目对应的代码位置与测试位置。新增/移动模块时必须更新映射表。

**规则 3 · 任务完成定义（DoD）**：代码 + 受影响契约 + 映射表 + 验证脚本/测试，四者同状态才算完成。只改代码不动契约的提交视为未完成。

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

- CloudBase 环境：`health-care-d0gccnchda96e436a`（上海，PostgreSQL 共享实例，免费体验版）
- 服务端本地测试：`cd server && go test ./...`；结构检查：`go build ./...`
- 全链路冒烟：`server/scripts/smoke.ps1`（M1 交付后可用）
- 部署：tcb CLI（M1 首个函数部署时补充命令于此）

## 7. AI 工作指南

1. 接到任务 → 先定位契约章节（docs/00-索引.md）→ 读契约 → 读映射表中的既有代码 → 再动手。
2. 不跨模块复制逻辑；发现可复用逻辑时，提升到对应 internal 领域包，而不是粘贴。
3. 与既有约定冲突时，停下来在回复中指出，不要擅自另起一套。
4. 原型 `prototype/` 是 UI 参照，不是功能代码；不要 import 或搬运其 JS 到 app/。
