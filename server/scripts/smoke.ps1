# smoke.ps1 · M1 验收脚本：curl 全链路（auth → import → sync）
# 契约：docs/00-索引.md §2 映射表；docs/03-导入格式-v1.md §7 示例
# 用法（部署后填好配置执行）：pwsh ./scripts/smoke.ps1
#   三个函数若触发器域名不同，用 -AuthBase / -SyncBase / -ImportBase 分别覆盖。
param(
  [string]$BaseUrl      = 'https://<HTTP触发器域名>',
  [string]$AuthBase     = '',
  [string]$SyncBase     = '',
  [string]$ImportBase   = '',
  [string]$FamilySecret = '<家庭口令>',
  [string]$DeviceName   = 'smoke-脚本',
  [string]$ProfileName  = '爷爷'
)
$ErrorActionPreference = 'Stop'
if ($AuthBase   -eq '') { $AuthBase   = $BaseUrl }
if ($SyncBase   -eq '') { $SyncBase   = $BaseUrl }
if ($ImportBase -eq '') { $ImportBase = $BaseUrl }

$script:Failures = 0
function Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }
function Assert($cond, $msg) {
  if ($cond) { Write-Host "  PASS: $msg" -ForegroundColor Green }
  else { $script:Failures++; Write-Host "  FAIL: $msg" -ForegroundColor Red }
}

# ===== 1. auth：口令换 token（02 §3.2） =====
Step '1. auth：口令换设备 token'
$authResp = Invoke-RestMethod -Method Post -Uri "$AuthBase/auth" -ContentType 'application/json' `
  -Body (@{ secret = $FamilySecret; display_name = $DeviceName } | ConvertTo-Json)
Assert ($authResp.ok -eq $true -and $authResp.token.StartsWith('fht_')) 'token 签发（fht_ 前缀）'
$Token = $authResp.token
$Headers = @{ Authorization = "Bearer $Token" }

# 口令错误必须 401
$wrong = try { Invoke-RestMethod -Method Post -Uri "$AuthBase/auth" -ContentType 'application/json' `
  -Body (@{ secret = 'wrong'; display_name = 'x' } | ConvertTo-Json) } catch { $_.Exception.Response }
Assert ($wrong -isnot [psobject] -or $authResp.ok) '口令错误被拒绝（HTTP 非 2xx）'

# ===== 1b. sync 上行：建立测试档案（import 依赖档案已存在，走真实 App 路径） =====
Step '1b. 上行建立测试档案'
$profileBody = @{
  table = 'profiles'; op = 'insert'
  row = @{ id = [guid]::NewGuid().ToString(); name = $ProfileName; relation = $ProfileName; gender = 'male'; notes = '' }
} | ConvertTo-Json -Depth 5
$pw = Invoke-RestMethod -Method Post -Uri "$SyncBase/sync" `
  -Headers ($Headers + @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }) `
  -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($profileBody))
Assert ($pw.ok -eq $true) '档案建立'

# ===== 2. import：提交复查事件（03 §7 示例） =====
Step '2. import：校验并写入一次复查（03 §7 示例）'
$importBody = Get-Content -Raw -Encoding UTF8 -Path "$PSScriptRoot/fixtures/checkup-sample.json"
$importId = ($importBody | ConvertFrom-Json).import_id
$importResp = Invoke-RestMethod -Method Post -Uri "$ImportBase/api/v1/import" `
  -Headers ($Headers + @{ 'Idempotency-Key' = $importId }) `
  -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($importBody))
Assert ($importResp.ok -eq $true) '导入成功'
Assert ($importResp.created.report_count -eq 3 -and $importResp.created.indicator_count -eq 9) 'created 计数（3 报告 / 9 指标）'
$importResp | ConvertTo-Json -Depth 6 | Write-Host

# ===== 2b. import 幂等：同一 import_id 重复提交不重复入库（03 §1 原则 2） =====
Step '2b. import 幂等重放'
$replay = Invoke-WebRequest -Method Post -Uri "$ImportBase/api/v1/import" `
  -Headers ($Headers + @{ 'Idempotency-Key' = $importId }) `
  -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($importBody))
Assert ($replay.Headers['X-Idempotent-Replay'] -eq 'true') '重放标记'
Assert (($replay.Content | ConvertFrom-Json).created.report_count -eq 3) '重放返回首次结果'

# ===== 2c. import dry_run：只校验不写库（03 §2） =====
Step '2c. dry_run'
$dryBody = $importBody -replace '"import_id": "[^"]+"', '"import_id": "11111111-2222-3333-4444-555555555555"' `
  -replace '"profile_ref"', '"dry_run": true, "profile_ref"'
$dryResp = Invoke-RestMethod -Method Post -Uri "$ImportBase/api/v1/import" `
  -Headers ($Headers + @{ 'Idempotency-Key' = '11111111-2222-3333-4444-555555555555' }) `
  -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($dryBody))
Assert ($dryResp.ok -eq $true -and $dryResp.dry_run -eq $true) 'dry_run 通过校验'

# ===== 3. sync 下行：拉到刚导入的数据（02 §4.1） =====
Step '3. sync 增量下行'
$sync = Invoke-RestMethod -Uri "$SyncBase/sync?since=0&limit=500" -Headers $Headers
$tables = $sync.changes | ForEach-Object { $_.table } | Sort-Object -Unique
Assert ($tables -contains 'checkup_events' -and $tables -contains 'reports' -and $tables -contains 'indicator_items') "下行含三类表（实际：$($tables -join ',')）"
Assert ($sync.changes.Count -ge 13) "dry_run 未写库，下行含导入全部行（实际 $($sync.changes.Count) 行，期望 ≥13）"
$seqs = $sync.changes.row.seq
Assert (($seqs | Measure-Object -Maximum).Maximum -eq $sync.next) 'next = 最大 seq'

# ===== 4. sync 上行幂等：同一 Idempotency-Key 重放（02 §4.2） =====
Step '4. 上行幂等'
$writeBody = @{
  table = 'measurements'; op = 'insert'
  row = @{
    id = [guid]::NewGuid().ToString(); profile_id = ($sync.changes | Where-Object { $_.table -eq 'checkup_events' } | Select-Object -First 1).row.profile_id
    type = 'bp'; measured_at = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); tz_offset_min = 480
    systolic = 128; diastolic = 82
  }
} | ConvertTo-Json -Depth 5
$idemKey = [guid]::NewGuid().ToString()
$w1 = Invoke-RestMethod -Method Post -Uri "$SyncBase/sync" -Headers ($Headers + @{ 'Idempotency-Key' = $idemKey }) `
  -ContentType 'application/json' -Body $writeBody
$w2raw = Invoke-WebRequest -Method Post -Uri "$SyncBase/sync" -Headers ($Headers + @{ 'Idempotency-Key' = $idemKey }) `
  -ContentType 'application/json' -Body $writeBody
Assert ($w1.ok -eq $true) '上行写入成功'
Assert ($w2raw.Headers['X-Idempotent-Replay'] -eq 'true') '上行重放标记'
$measureCount = (Invoke-RestMethod -Uri "$SyncBase/sync?since=0&limit=500" -Headers $Headers).changes |
  Where-Object { $_.table -eq 'measurements' } | Measure-Object | Select-Object -ExpandProperty Count
Assert ($measureCount -eq 1) "重复提交未产生重复测量（实际 $measureCount 条）"

# ===== 5. backup（可选；BACKUP_TOKEN 配置时验证） =====
if ($env:BACKUP_TOKEN -and $env:BACKUP_BASE) {
  Step '5. backup 手动触发'
  $bk = Invoke-RestMethod -Method Post -Uri "$env:BACKUP_BASE/backup" -Headers @{ Authorization = "Bearer $env:BACKUP_TOKEN" }
  Assert ($bk.ok -eq $true -and $bk.key -like 'backups/health-*.pgdump') "备份完成：$($bk.key)"
}

Write-Host ''
if ($script:Failures -gt 0) {
  Write-Host "存在 $script:Failures 个失败用例" -ForegroundColor Red
  exit 1
}
Write-Host '全部用例通过' -ForegroundColor Green
