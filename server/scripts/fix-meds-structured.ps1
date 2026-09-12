# fix-meds-structured.ps1 · 为罗万全西药补结构化用量（dose_qty / dose_unit / dose_times_per_day）
# 背景：import-luowanquan.ps1 的西药只写了 dosage_text + dose_slots，缺结构化用量，
#       导致「今日用药」在 App 内无法添加（addable 过滤）、且 daily_med_items 上行被服务端拒绝。
# 通道：真实 API（auth → POST /sync update），medication_items 为可改表，整行 LWW 需全量业务列。
# 幂等：固定 Idempotency-Key，重复执行不产生副作用。
param(
  [string]$BaseUrl = 'http://43.161.199.183',
  [string]$FamilySecret = '123123'
)
$ErrorActionPreference = 'Stop'

$ProfileId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3d0001'

$c1 = '11111111-1111-4111-8111-111111111101'
$c2 = '11111111-1111-4111-8111-111111111102'
$c4 = '11111111-1111-4111-8111-111111111104'
$c5 = '11111111-1111-4111-8111-111111111105'
$m01 = '22222222-2222-4222-8222-222222222201'
$m02 = '22222222-2222-4222-8222-222222222202'
$m03 = '22222222-2222-4222-8222-222222222203'
$m04 = '22222222-2222-4222-8222-222222222204'
$m05 = '22222222-2222-4222-8222-222222222205'
$m06 = '22222222-2222-4222-8222-222222222206'
$m08 = '22222222-2222-4222-8222-222222222208'
$m09 = '22222222-2222-4222-8222-222222222209'
$m10 = '22222222-2222-4222-8222-222222222210'
$m11 = '22222222-2222-4222-8222-222222222211'

function Body($obj) { return ,([System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 12 -Compress))) }
function Row($row) { return @{ table = 'medication_items'; op = 'update'; row = $row } }
function Auth([string]$display) {
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth" -ContentType 'application/json; charset=utf-8' `
    -Body (Body @{ secret = $FamilySecret; display_name = $display })
  if (-not $r.ok) { throw "auth failed" }
  return @{ token = $r.token; id = $r.device.id }
}

$dev = Auth '罗万全家人'
Write-Host "== 鉴权 OK（device $($dev.id)）=="

# 每味西药：完整业务列 + 结构化用量（整行 LWW 需全量，缺列会被置 NULL）
$meds = @(
  @{ id=$m01; name='苯磺酸左氨氯地平片'; dosage_text='每次 1 片（2.5mg）'; qty=1; times=1; slots=@('morning'); start='2026-06-11'; change=$c1 }
  @{ id=$m02; name='阿司匹林肠溶片'; dosage_text='每次 1 片（100mg）'; qty=1; times=1; slots=@('morning'); start='2026-06-11'; change=$c1 }
  @{ id=$m03; name='阿托伐他汀钙片'; dosage_text='每次 1 片'; qty=1; times=1; slots=@('morning'); start='2026-06-11'; change=$c1 }
  @{ id=$m04; name='非奈利酮片'; dosage_text='每次 1 片（10mg）'; qty=1; times=1; slots=@('morning'); start='2026-06-11'; change=$c1 }
  @{ id=$m05; name='百令片'; dosage_text='每次 4 片'; qty=4; times=3; slots=@('morning','noon','evening'); start='2026-06-11'; change=$c1 }
  @{ id=$m06; name='非布司他片'; dosage_text='每次 1 片'; qty=1; times=1; slots=@('morning'); start='2026-06-11'; end='2026-09-03'; change=$c1 }
  @{ id=$m08; name='醋酸地塞米松片'; category='temporary'; dosage_text='每次 2 片'; qty=2; times=3; slots=@('morning','noon','evening'); start='2026-06-13'; end='2026-06-16'; change=$c2 }
  @{ id=$m09; name='叶酸片'; dosage_text='每次 2 片（10mg）'; qty=2; times=2; slots=@('morning','evening'); start='2026-07-02'; change=$c4 }
  @{ id=$m10; name='非布司他片'; dosage_text='每次 0.5 片（20mg）'; qty=0.5; times=1; slots=@('morning'); start='2026-09-04'; supersedes=$m06; change=$c5 }
  @{ id=$m11; name='雷公藤多甙片'; dosage_text='每次 4 片（10mg/片）'; qty=4; times=3; slots=@('morning','noon','evening'); start='2026-09-04'; change=$c5 }
)

$rows = @()
foreach ($m in $meds) {
  $row = @{
    id = $m.id; profile_id = $ProfileId
    category = $(if ($m.category) { $m.category } else { 'long_term' })
    med_kind = 'western'; name = $m.name; dosage_text = $m.dosage_text
    dose_qty = $m.qty; dose_unit = '片'; dose_times_per_day = $m.times
    dose_slots = $m.slots; start_date = $m.start; change_id = $m.change
  }
  if ($m.end) { $row.end_date = $m.end }
  if ($m.supersedes) { $row.supersedes_id = $m.supersedes }
  $rows += (Row $row)
}

$h = @{ Authorization = "Bearer $($dev.token)"; 'Idempotency-Key' = 'luowanquan-meds-structured-v1' }
$r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/sync" -Headers $h `
  -ContentType 'application/json; charset=utf-8' -Body (Body $rows)
if (-not $r.ok) { throw "push failed: $($r | ConvertTo-Json -Compress)" }
Write-Host "== OK：已补结构化用量（$($rows.Count) 味西药）=="

# 校验：拉取 medication_items 确认 dose_qty 已写入
$s = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=2000" -Headers @{ Authorization = "Bearer $($dev.token)" }
$meds2 = $s.changes | Where-Object { $_.table -eq 'medication_items' -and -not $_.row.deleted }
Write-Host "== 校验：当前用药条目 =="
$meds2 | Sort-Object { $_.row.name } | ForEach-Object {
  "{0}  {1}  剂量={2} 单位={3} 一天{4}次 时段={5}" -f `
    $_.row.name, $_.row.med_kind, $_.row.dose_qty, $_.row.dose_unit, $_.row.dose_times_per_day, ($_.row.dose_slots -join ',')
} | Write-Host
