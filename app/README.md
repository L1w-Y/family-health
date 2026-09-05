# 家庭健康 Android App（M2 前半段）

Kotlin + Jetpack Compose（Material 3）单模块工程。本期为**假数据驱动的完整页面实现**：
视觉、文案、交互 1:1 参照 `prototype/`（禁止搬运其 JS），实体对齐 `docs/02-数据库与同步.md`。
Room 与网络层本期不实现，仅占位接口。

## 构建

```powershell
cd app
.\gradlew.bat assembleDebug
# 产物：build/outputs/apk/debug/family-health-debug.apk
```

- JDK：优先用 `JAVA_HOME`；未设置时 `gradlew.bat` 回退到 Android Studio 自带 JBR（已探测本机）。
  Gradle 守护进程 JDK 由 `gradle.properties` 的 `org.gradle.java.home` 指定。
- SDK：`local.properties` 的 `sdk.dir`（本机已装 platform android-37、build-tools 36.0.0）。
- 版本：Gradle 9.5.0 / AGP 9.3.0 / Kotlin（AGP 内置）/ Compose BOM 2025.06.01 / compileSdk 37 / minSdk 26。
- 注意：AGP 9.x 内置 Kotlin 支持，**不要再**应用 `org.jetbrains.kotlin.android` 插件（会报
  `extension 'kotlin' already registered`）。Compose 编译插件 `org.jetbrains.kotlin.plugin.compose` 正常单独应用。

## 模块地图（package）

```
com.family.health
├── MainActivity.kt               入口，edge-to-edge
├── data/
│   ├── model/Models.kt           实体（对齐 docs/02 §3，注释标注契约）
│   ├── DemoData.kt               假数据（prototype/data.js 1:1 移植，含导入示例）
│   ├── Logic.kt                  指标匹配/历史用药分段/下次复查（prototype 同款逻辑）
│   ├── ImportParser.kt           导入 JSON 解析（docs/03 格式，org.json）
│   └── AppViewModel.kt           单一状态源：StateFlow + 全部交互动作
├── syncclient/SyncClient.kt      架构占位：接口 + FakeSyncClient（契约 docs/02 §4）
├── ui/
│   ├── theme/Theme.kt            色板（prototype/styles.css 1:1，主色 #0F766E）
│   ├── components/               designsystem：卡片/分段/chip/标签/按钮/表单/步骤条/折线图
│   └── AppRoot.kt                导航骨架：成员切换条、底部 4 Tab + 中央＋、半屏弹层、Toast
└── feature/
    ├── overview/OverviewScreen.kt        概览三卡（便签/今日用药/下次复查）
    ├── records/                          记录 Tab：复查段、测量段、事件详情、报告详情、指标历史
    ├── meds/                             用药：方案、编辑、历史用药、记用药变化、今日用药管理（+共享件）
    ├── mine/MineScreens.kt               我的：成员/设备/提醒/令牌/导出/关于
    ├── trends/TrendsScreen.kt            趋势与对比（表格|折线、7/30/90 天）
    ├── notes/NotesScreen.kt              便签列表 + 新建
    └── entry/                            记血压/记血糖表单、记复查（导入）四步
```

## 页面 ↔ docs/05 章节对照

| 05 章节 | 页面 | 代码 |
|---|---|---|
| §1 导航 | 底部 4 Tab + 中央＋、成员切换条 | `ui/AppRoot.kt`（TabBar/MemberBar） |
| §2 ＋动作面板 | 半屏弹层（日常/阶段性两组） | `ui/AppRoot.kt`（showPlus Sheet） |
| §3 概览三卡 | 便签卡/今日用药卡/复查卡 | `feature/overview/OverviewScreen.kt` |
| §3 今日用药管理 | 增删改 + 编辑弹层 | `feature/meds/DailyManageScreen.kt`、`meds/DailyShared.kt` |
| §4 记录两段切换 | 复查/测量 SegControl | `feature/records/RecordsScreen.kt` |
| §4.1 事件/报告/指标历史 | 下钻链路 + 加入重点清单 | `records/Event|Report|Indicator*` |
| §4.2 血压日志表/血糖场景网格 | 周期翻页、当日明细、编辑/软删 | `records/RecordsScreen.kt` |
| §5 用药方案/临时/历史 | 当前方案卡、历史用药分段 | `meds/MedsScreen.kt`、`MedHistoryScreen.kt` |
| §5 记用药变化 | 核对式（停/改量/新增，supersedes 链） | `meds/MedChangeScreen.kt` |
| §6 我的及子页 | 六项全部子页 | `feature/mine/MineScreens.kt` |
| §7 趋势与对比 | 表格|折线切换、测量趋势 | `feature/trends/TrendsScreen.kt` |
| §8 录入表单 | 记血压/血糖、记复查导入、今日用药管理 | `feature/entry/*`、`meds/DailyShared.kt` |

## 假数据说明

- 三位成员（爷爷/奶奶/爸爸）全量移植 `prototype/data.js`：用药 7 条、用药变化 5 条、
  今日用药 5 条、复查事件 4 个（报告 9 份）、测量 26 条、便签 4 条、重点清单 3 项、提醒 3 套。
- 所有写操作（新增测量/便签/用药变化/今日用药/导入/档案/设备/提醒）即时回显，
  仅存内存，重启即重置（与 prototype 一致）。
- 数据切换：顶部成员切换条切换爷爷/奶奶/爸爸，全页面数据随之切换。

## 待接入（M2 后半段）

- `syncclient`：FakeSyncClient → 真同步（下行 `GET /sync?since=`、上行 Idempotency-Key）。
- Room 本地缓存（服务端为权威，本地只读缓存）。
- 本地通知调度（便签 remind_at、reminders 时刻表）。
- 原件照片拍照/相册上传与附件懒加载。
