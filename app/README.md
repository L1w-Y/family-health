# 家庭健康 Android App

Kotlin + Jetpack Compose（Material 3）单模块工程。**真实数据架构**：Room 本地镜像为单一事实源，
经同步引擎与自建服务端增量同步（契约：`docs/02-数据库与同步.md` §4；页面：`docs/05-页面结构与交互.md`）。

## 构建

```powershell
cd app
.\gradlew.bat assembleDebug
# 产物：build/outputs/apk/debug/family-health-debug.apk
```

- JDK：优先 `JAVA_HOME`；未设置时回退 Android Studio JBR（`gradle.properties` 的 `org.gradle.java.home`）。
- SDK：`local.properties` 的 `sdk.dir`（platform android-37、build-tools 36.0.0）。
- 版本：Gradle 9.5.0 / AGP 9.3.0 / Kotlin 2.2.10 / Compose BOM 2025.06.01 / Room 2.7.2 / KSP（`gradle/libs.versions.toml`）/ compileSdk 37 / minSdk 26。
- 注意 1：AGP 9.x 内置 Kotlin，**不要再**应用 `org.jetbrains.kotlin.android`（报 `extension 'kotlin' already registered`）。
- 注意 2：KSP 与 AGP 9 内置 Kotlin 共存需 `gradle.properties` 里 `android.disallowKotlinSourceSets=false`（已配置）。

## 数据架构（M2 后半段落地）

```
页面（feature/*） ← AppViewModel（StateFlow）
   ↑ 读取聚合                ↓ 写操作
Repository（13 表聚合为 UI 模型）  本地事务：业务行 + outbox 行
   ↑ Room（本地镜像，单一事实源）      ↓
SyncEngine：下行 GET /sync?since=lastSeq（事务应用）｜上行 outbox 按 idemKey 整批 POST /sync（失败重放）
   ↑ HttpSyncClient（auth/sync/import，统一错误抽取）
SessionStore：服务器/口令/token/deviceId/lastSeq
notif/：本机提醒（AlarmManager + 通知渠道 + 开机重排；便签时刻、测量每天、补药提醒）
```

写入规则（与服务端一致）：追加型表（测量等）"编辑"= 软删旧行 + 新 id 插入；可改表整行 LWW；
改量链先写 med_changes 再写 medication_items；NOT NULL 列必须给值。

## 模块地图（package）

```
com.family.health
├── FamilyHealthApp.kt            Application + AppContainer（手工装配：db/session/client/engine/repo/提醒）
├── MainActivity.kt               入口
├── data/
│   ├── model/Models.kt           UI 实体（对齐 docs/02 §3）
│   ├── db/                       Room：Entities / Daos / AppDatabase（13 同步表 + outbox + meta）
│   ├── repo/                     Repository（聚合流 + 全部写操作）/ Mappers（行 JSON ↔ 实体）
│   ├── sync/SyncEngine.kt        推拉调度、下行事务应用、上行重放
│   ├── session/                  SessionStore（配置与游标）
│   ├── AppViewModel.kt           状态装配 + 动作分发（页面不直接触库）
│   ├── MeasurementTimeBucket.kt  概览测量时间段（早晨/上午/下午/晚上；仅由 measured_at 推导）
│   ├── MedicationStock.kt        今日用药按药格和截止时间推算理论余量/补药时点
│   ├── Logic.kt / ImportParser.kt 指标匹配等纯逻辑 / 导入预览解析
├── syncclient/HttpSyncClient.kt  服务端 HTTP 客户端（docs/02 §3.2/§4、docs/03）
├── notif/                        LocalReminder / ReminderScheduler / ReminderReceiver（本机闹钟与通知）
├── ui/
│   ├── theme/Theme.kt            色板（主色 #086D7C）+ VisualTokens.kt（全局字级/圆角/间距）
│   ├── components/               design-system：卡片/chip/表单/滚轮日期时间选择器 等
│   └── AppRoot.kt                导航骨架：成员气泡条、底部 4 Tab + 中央＋、Toast、通知权限
└── feature/
    ├── setup/SetupScreen.kt      首配（家庭口令+署名；SERVER_URL 内置）
    ├── overview/                 概览：复查气泡/便签/今日测量时间网格（血糖另显场景）/今日用药
    ├── records/                  记录：复查段（重点指标表可增删+事件）、测量段（周期=近 N 天/自然月，日期分组明细表+血糖场景筛选+明细编辑）、详情下钻
    ├── meds/                     用药：结构化每次用量与一天次数/历史/记变化/今日用药搜索弹窗/分药格盘点
    ├── mine/                     我的：成员/设备/提醒/令牌/导出/服务器与账号/关于
    ├── notes/                    便签列表（可删除）+ 新建（滚轮时刻+单次/每天）
    └── entry/                    记血压/血糖（四列紧凑输入 + 小型场景滚轮；五列时间滚轮）、记复查（dry_run 预检流程）
```

## 与 docs/05 的对应

页面行为以 `docs/05-页面结构与交互.md` 为契约；本 README 只记工程结构，不重复逐页对照
（页面↔代码精确映射见 `docs/00-索引.md` 映射表）。

## 视觉验证

全 App 浅色视觉以 `docs/07-视觉样式.md` 为准；主题集中在 `ui/theme/Theme.kt`，
字级、间距和圆角在 `VisualTokens.kt`，共享显示组件在 `Components.kt`，表单与按钮在 `FormComponents.kt`。
布局、列宽、字段、交互及系统字体缩放保持既有约束。

验证：`scripts/verify-measurement-log.ps1`（日期分组单测 + APK 构建）、`scripts/verify-medication-stock.ps1`（药格截止时间与余量推算 + APK 构建）；
使用同一模拟器、同一成员和筛选条件，对比概览、复查、血压、血糖、用药、我的、录入表单截图。
固定列宽的今日用药表已有单行药名省略，长药名不能保证完整显示；
测量页紧凑筛选项和今日用药勾选区域有小于 48dp 的现有触控区域。
本轮保留这些布局约束，没有压缩字体缩放或新增裁切规则。
