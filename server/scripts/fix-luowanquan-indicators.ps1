# fix-luowanquan-indicators.ps1 · 修正罗万全既有复查指标（74 条）
# 1) 删除误录的「尿微量白蛋白 = >0.15」3 条（尿常规干化学半定量，与定量项混名）
# 2) 保留尿常规定性项（Neg/阴性）不改名、不改值
# 3) 按化验常识补 unit；修正明显损坏的 reference_range（比重 / ACR / 红细胞）
# indicator_items 为追加型表（02 §4.2 只增不改）→ 修正 = 软删旧行 + 新 id 插入；天然幂等（重跑无改动即不写）。
# 用法：& ./scripts/fix-luowanquan-indicators.ps1
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
$rows = @($pull.changes | Where-Object { $_.table -eq 'indicator_items' -and -not $_.row.deleted } | ForEach-Object { $_.row })
Write-Host "读取指标：$($rows.Count) 条"

$umol = "$([char]0x03BC)mol/L"   # μmol/L

# 按指标名补单位（该指标全部行）
$unitAll = @{
  '肌酐'         = $umol
  '尿素'         = 'mmol/L'
  '尿酸'         = $umol
  '钙'           = 'mmol/L'
  '磷'           = 'mmol/L'
  '钾'           = 'mmol/L'
  '钠'           = 'mmol/L'
  '氯'           = 'mmol/L'
  '胱抑素C1'     = 'mg/L'
  '甲状旁腺素'   = 'pg/mL'
  '糖化血红蛋白' = '%'
  '尿肌酐'       = 'mmol/L'
  '尿胆原'       = $umol
}

$ops = New-Object System.Collections.ArrayList
$log = New-Object System.Collections.ArrayList

foreach ($x in $rows) {
  $name = [string]$x.item_name
  $text = [string]$x.value_text

  # ① 删除误录的尿常规半定量项
  if ($name -eq '尿微量白蛋白' -and $text -eq '>0.15') {
    [void]$ops.Add(@{ table = 'indicator_items'; op = 'delete'; row = @{ id = $x.id } })
    [void]$log.Add(("DEL   {0} = {1}（report {2}）" -f $name, $text, $x.report_id))
    continue
  }

  # ② 单位
  $newUnit = $x.unit
  if ($unitAll.ContainsKey($name)) {
    $newUnit = $unitAll[$name]
  } elseif ($name -eq '葡萄糖' -and $null -ne $x.value_numeric) {
    $newUnit = 'mmol/L'          # 血浆生化血糖（尿常规定性行不加单位）
  } elseif ($name -eq '尿微量白蛋白' -and $null -ne $x.value_numeric) {
    $newUnit = 'mg/L'
  } elseif ($name -eq 'ACR') {
    $newUnit = 'mg/g'
  } elseif ($name -eq '红细胞') {
    $newUnit = '/HP'
  }

  # ③ 参考范围（仅修明显损坏/单位不一致的）
  $newRef = $x.reference_range
  if ($name -eq '比重') {
    $newRef = '1.005-1.030'      # 原 1.005-1030 / 1.005-1.000 丢小数点
  } elseif ($name -eq 'ACR') {
    $newRef = '0-30'             # 原 0-3.4 为 mg/mmol 参考，与本行 mg/g 数值不一致
  } elseif ($name -eq '红细胞') {
    $newRef = '0-3'
  }

  if ("$newUnit" -eq "$($x.unit)" -and "$newRef" -eq "$($x.reference_range)") { continue }

  $row = [ordered]@{
    id             = [guid]::NewGuid().ToString()
    report_id      = $x.report_id
    item_name      = $name
    value_numeric  = $x.value_numeric
    value_text     = $x.value_text
    unit           = $newUnit
    reference_range = $newRef
    sort_order     = $x.sort_order
    canonical_name = $x.canonical_name
  }
  [void]$ops.Add(@{ table = 'indicator_items'; op = 'insert'; row = $row })
  [void]$ops.Add(@{ table = 'indicator_items'; op = 'delete'; row = @{ id = $x.id } })
  [void]$log.Add(("FIX   {0}（值 {1}{2}）：单位 '{3}' -> '{4}'，参考 '{5}' -> '{6}'" -f `
    $name, $x.value_numeric, $text, $x.unit, $newUnit, $x.reference_range, $newRef))
}

$log | ForEach-Object { Write-Host $_ }
Write-Host "待写操作：$($ops.Count) 条"

if ($ops.Count -gt 0 -and -not $DryRun) {
  $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/sync" `
    -Headers @{ Authorization = "Bearer $tok"; 'Idempotency-Key' = [guid]::NewGuid().ToString() } `
    -ContentType 'application/json; charset=utf-8' -Body (Body @($ops))
  if (-not $res.ok) { throw 'push failed' }
  Write-Host '上行成功。'
} elseif ($DryRun) {
  Write-Host '（DryRun：未写入）'
} else {
  Write-Host '无改动，跳过。'
}
