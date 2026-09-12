# backfill-med-doses.ps1 · 回填罗万全西药的 dose_qty / dose_unit / dose_times_per_day
# 配合 migrate-medication-stock-v2.sql（服务端已加 3 列）。经 /sync 更新（可改表整行 LWW，自动取号）。
# 用法：& ./scripts/backfill-med-doses.ps1 [-DryRun]
param(
  [string]$BaseUrl = 'http://43.161.199.183',
  [string]$FamilySecret = '123123',
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

function Body($obj) { return ,([System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 12 -Compress))) }

$r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth" -ContentType 'application/json; charset=utf-8' `
  -Body (Body @{ secret = $FamilySecret; display_name = '罗万全家人' })
if (-not $r.ok) { throw 'auth failed' }
$tok = $r.token

$pull = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=500" -Headers @{ Authorization = "Bearer $tok" }
$rows = @($pull.changes | Where-Object {
    $_.table -eq 'medication_items' -and -not $_.row.deleted -and $_.row.med_kind -eq 'western'
} | ForEach-Object { $_.row })
Write-Host "西药条目：$($rows.Count)"

# name → (每次数量, 单位, 一天次数)
$dose = @{
  '苯磺酸左氨氯地平片' = @(1, '片', 1)
  '阿司匹林肠溶片'     = @(1, '片', 1)
  '阿托伐他汀钙片'     = @(1, '片', 1)
  '非奈利酮片'         = @(1, '片', 1)
  '百令片'             = @(4, '片', 3)
  '醋酸地塞米松片'     = @(2, '片', 3)
  '叶酸片'             = @(2, '片', 2)
  '雷公藤多甙片'       = @(4, '片', 3)
}

$ops = New-Object System.Collections.ArrayList
foreach ($x in $rows) {
  $n = [string]$x.name
  $qty = $null; $unit = $null; $times = $null
  if ($n -eq '非布司他片') {
    $qty = if ([string]$x.start_date -eq '2026-09-04') { 0.5 } else { 1.0 }
    $unit = '片'; $times = 1
  } elseif ($dose.ContainsKey($n)) {
    $qty = $dose[$n][0]; $unit = $dose[$n][1]; $times = $dose[$n][2]
  } else {
    continue  # 中药或未收录的跳过
  }
  if ($null -ne $x.dose_qty) { continue }  # 已回填过则跳过

  $row = @{}
  $x.psobject.Properties | ForEach-Object { $row[$_.Name] = $_.Value }
  $row['dose_qty'] = $qty
  $row['dose_unit'] = $unit
  $row['dose_times_per_day'] = $times
  [void]$ops.Add(@{ table = 'medication_items'; op = 'update'; row = $row })
  Write-Host ("回填 {0} -> qty={1} unit={2} times={3}（start {4}）" -f $n, $qty, $unit, $times, $x.start_date)
}

Write-Host "待写：$($ops.Count) 条"
if ($ops.Count -gt 0 -and -not $DryRun) {
  $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/sync" `
    -Headers @{ Authorization = "Bearer $tok"; 'Idempotency-Key' = [guid]::NewGuid().ToString() } `
    -ContentType 'application/json; charset=utf-8' -Body (Body @($ops))
  if (-not $res.ok) { throw 'push failed' }
  Write-Host '上行成功。'
} elseif ($DryRun) {
  Write-Host '（DryRun：未写入）'
} else {
  Write-Host '无需写入。'
}
