# smoke.ps1 · M1 验收脚本：curl 全链路（auth → import → sync）
# 契约：docs/00-索引.md §2 映射表；docs/03-导入格式-v1.md §7 示例
# 用法：配置下方变量后执行 ./scripts/smoke.ps1
# TODO M1：随函数实现逐步点亮各用例（当前为骨架）。

$ErrorActionPreference = 'Stop'

# ===== 配置 =====
$BaseUrl     = 'https://<HTTP触发器域名>'   # import/sync/auth 的 HTTP 触发器地址（M1 部署后填）
$FamilySecret = '<家庭口令>'
$DeviceName   = '爸爸'
$ProfileName  = '爷爷'

function Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }

# ===== 1. auth：口令换 token =====
Step '1. auth：口令换设备 token'
$authResp = $null
# $authResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth" -ContentType 'application/json' `
#   -Body (@{ secret = $FamilySecret; display_name = $DeviceName } | ConvertTo-Json)
# $Token = $authResp.token
# Write-Host "token 获取成功（不打印原文）"
$Token = '<待 M1 点亮后从上方获取>'
$Headers = @{ Authorization = "Bearer $Token" }

# ===== 2. import：提交复查事件（03 §7 示例） =====
Step '2. import：校验并写入一次复查'
# $importBody = Get-Content -Raw -Path "$PSScriptRoot/fixtures/checkup-sample.json"
# $importResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/import" -Headers $Headers `
#   -ContentType 'application/json' -Body $importBody
# $importResp | ConvertTo-Json -Depth 5

# ===== 2b. import 幂等：同一 import_id 重复提交不重复入库 =====
Step '2b. import 幂等重放'
# 重复上方请求，断言返回与首次一致

# ===== 2c. import dry_run：只校验不写库 =====
Step '2c. dry_run'
# 断言 ok=true 且 sync 中无新数据

# ===== 3. sync 下行：拉到刚导入的数据 =====
Step '3. sync 增量下行'
# $sync = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=500" -Headers $Headers
# 断言包含 checkup_events / reports / indicator_items，且 seq 递增

# ===== 4. sync 上行幂等：同一 Idempotency-Key 重放 =====
Step '4. 上行幂等'
# 断言不产生重复测量记录

Write-Host "`n全部用例通过（骨架待点亮）" -ForegroundColor Green
