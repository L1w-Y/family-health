# seed-demo.ps1 · 测试数据灌库：全部经真实 API 写入（auth → sync 批量上行 → import 导入）
# 覆盖功能点：多成员、血压(含心率可空/单日4次)、血糖(全场景)、复查事件(定量/定性/结论/用药备注)、
#             重点清单(别名归并)、长期/临时/中药用药、用药变化(含关联复查与改量链)、提醒、便签、今日用药、附件元数据
# 检测人与用户均为假名。清理：bash deploy/server-reset.sh 全量重置。
# 用法：& ./scripts/seed-demo.ps1 -BaseUrl http://43.161.199.183 -FamilySecret LuojiaHealth2026
param(
  [string]$BaseUrl = 'http://43.161.199.183',
  [string]$FamilySecret = 'LuojiaHealth2026'
)
$ErrorActionPreference = 'Stop'

# 逗号运算符阻止 PowerShell 展开 byte[]（否则 -Body 收到逐字节序列，请求体被毁）
function Body($obj) { return ,([System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 12 -Compress))) }
function Ms($d, $t) { [DateTimeOffset]::Parse("$d ${t}:00+08:00").ToUnixTimeMilliseconds() }
function Row($table, $row) { @{ table = $table; op = 'insert'; row = $row } }
function Uid() { [guid]::NewGuid().ToString() }

function Auth([string]$display) {
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth" -ContentType 'application/json; charset=utf-8' `
    -Body (Body @{ secret = $FamilySecret; display_name = $display })
  if (-not $r.ok) { throw "auth failed: $display" }
  return @{ token = $r.token; id = $r.device.id; name = $display }
}

function Push($dev, $items, [string]$label) {
  $h = @{ Authorization = "Bearer $($dev.token)"; 'Idempotency-Key' = (Uid) }
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/sync" -Headers $h `
    -ContentType 'application/json; charset=utf-8' -Body (Body $items)
  if (-not $r.ok) { throw "push $label failed: $($r | ConvertTo-Json -Depth 8 -Compress)" }
  Write-Host "  OK $label（$($items.Count) 行）"
}

function Import-Event($dev, [string]$importId, [string]$profileName, $event) {
  $envelope = @{
    format = 'family-health-import'; version = 1; import_id = $importId
    profile_ref = @{ name = $profileName }; payload = @{ type = 'checkup_event'; event = $event }
  }
  $h = @{ Authorization = "Bearer $($dev.token)"; 'Idempotency-Key' = $importId }
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/import" -Headers $h `
    -ContentType 'application/json; charset=utf-8' -Body (Body $envelope)
  if (-not $r.ok) { throw "import $profileName $($event.checkup_date) failed: $($r | ConvertTo-Json -Depth 8 -Compress)" }
  Write-Host "  OK 导入 $profileName $($event.checkup_date)（$($r.created.report_count) 报告 / $($r.created.indicator_count) 指标）"
  return $r.created.event_ids[0]
}

# ===== 1. 设备（用户，假名） =====
Write-Host '== 1. 注册设备 =='
$d1 = Auth '陈小枫'; $d2 = Auth '林小岚'; $d3 = Auth '周小航'
Write-Host "  OK $($d1.name) / $($d2.name) / $($d3.name)"

# ===== 2. 成员档案（被检测人，假名） =====
Write-Host '== 2. 成员档案 =='
$p1 = Uid; $p2 = Uid; $p3 = Uid
Push $d1 @(
  Row 'profiles' @{ id = $p1; name = '张国栋'; relation = '父亲'; gender = 'male'; birth_date = '1952-03-12'; notes = '高血压、2型糖尿病、慢性肾病（CKD）。青霉素过敏。2023 年冠脉支架一枚。' }
  Row 'profiles' @{ id = $p2; name = '李慧兰'; relation = '母亲'; gender = 'female'; birth_date = '1955-07-01'; notes = '高血压。无药物过敏史。' }
  Row 'profiles' @{ id = $p3; name = '陈启航'; relation = '本人'; gender = 'male'; birth_date = '1980-11-23'; notes = '' }
) '档案'

# ===== 3. 测量（血压含心率可空/单日4次；血糖全场景） =====
Write-Host '== 3. 测量 =='
function M($profId, $d, $t, $sys, $dia, $hr) {
  $r = @{ id = (Uid); profile_id = $profId; type = 'bp'; measured_at = (Ms $d $t); tz_offset_min = 480; systolic = $sys; diastolic = $dia }
  if ($hr -ne $null) { $r.heart_rate_bpm = $hr }
  return Row 'measurements' $r
}
function G($profId, $d, $t, $mmol, $ctx) {
  Row 'measurements' @{ id = (Uid); profile_id = $profId; type = 'glucose'; measured_at = (Ms $d $t); tz_offset_min = 480; glucose_mmol = $mmol; glucose_context = $ctx }
}
Push $d1 @(
  M $p1 '2026-09-05' '07:32' 136 85 71
  M $p1 '2026-09-04' '19:20' 143 89 75
  M $p1 '2026-09-03' '07:15' 139 87 72
  M $p1 '2026-09-03' '12:40' 144 90 76
  M $p1 '2026-09-03' '19:05' 141 88 74
  M $p1 '2026-09-03' '21:30' 140 87 73
  M $p1 '2026-09-02' '07:28' 138 86 72
  M $p1 '2026-09-02' '19:02' 140 88 $null
  M $p1 '2026-08-28' '19:12' 145 90 76
  M $p1 '2026-08-20' '19:00' 142 89 73
  M $p1 '2026-08-18' '07:33' 137 84 71
  M $p1 '2026-08-16' '21:00' 135 84 $null
  M $p1 '2026-07-29' '07:30' 148 92 78
  M $p1 '2026-07-20' '19:08' 144 91 75
) '张国栋·血压'
Push $d2 @(
  G $p1 '2026-09-04' '07:10' 6.8 'fasting'
  G $p1 '2026-09-04' '09:40' 8.9 'after_meal_2h'
  G $p1 '2026-09-01' '21:30' 7.6 'bedtime'
  G $p1 '2026-08-30' '07:15' 7.1 'fasting'
  G $p1 '2026-08-22' '07:05' 6.5 'fasting'
  G $p1 '2026-08-12' '08:00' 5.9 'before_meal'
  G $p1 '2026-08-10' '22:10' 6.2 'random'
  G $p1 '2026-07-15' '07:12' 7.4 'fasting'
) '张国栋·血糖'
Push $d2 @(
  M $p2 '2026-09-04' '08:10' 128 78 69
  M $p2 '2026-09-01' '08:05' 131 80 70
) '李慧兰·血压'
Push $d3 @(
  M $p2 '2026-08-25' '08:12' 126 77 68
  M $p1 '2026-08-25' '07:40' 139 87 70
  M $p3 '2026-09-02' '22:10' 129 82 72
  M $p3 '2026-08-19' '22:05' 133 84 74
) '李慧兰/陈启航·血压'

# ===== 4. 复查事件（import 导入：定量/定性/结论/用药备注/下次复查） =====
Write-Host '== 4. 复查事件 =='
$e1 = Import-Event $d1 '3fa9e85d-0000-4000-8000-0000000000e1' '张国栋' @{
  checkup_date = '2026-08-15'; hospital = '市人民医院'; department = '肾内科'
  note = '尿蛋白控制一般，医生嘱低盐饮食，三个月后复查。'
  next_checkup_date = '2026-11-15'
  medication_changes_note = '停缬沙坦 → 氯沙坦钾片；新增阿托伐他汀'
  reports = @(
    @{ title = '尿常规'; indicators = @(
      @{ item_name = '尿蛋白'; value = '1+'; reference_range = '阴性' },
      @{ item_name = '尿白细胞'; value = '阴性'; reference_range = '阴性' },
      @{ item_name = '尿微量白蛋白'; value = 156.3; unit = 'mg/L'; reference_range = '<30' },
      @{ item_name = '尿肌酐'; value = 8.82; unit = 'mmol/L' },
      @{ item_name = '尿微量白蛋白/肌酐比值(ACR)'; value = 201.5; unit = 'mg/g'; reference_range = '<30' }
    ) },
    @{ title = '肾功能+血糖'; indicators = @(
      @{ item_name = '血肌酐'; value = 132; unit = 'μmol/L'; reference_range = '41~81' },
      @{ item_name = '尿素氮'; value = 9.1; unit = 'mmol/L'; reference_range = '2.9~8.2' },
      @{ item_name = '估算肾小球滤过率'; value = 58; unit = 'ml/min' },
      @{ item_name = '糖化血红蛋白'; value = 7.2; unit = '%'; reference_range = '4.0~6.0' },
      @{ item_name = '空腹血糖'; value = 7.8; unit = 'mmol/L'; reference_range = '3.9~6.1' },
      @{ item_name = '血钙'; value = 2.31; unit = 'mmol/L'; reference_range = '2.11~2.52' }
    ) },
    @{ title = '肾脏B超'; conclusion_text = '双肾大小形态正常，实质回声增强。右肾囊肿约 8mm，建议定期复查。'; indicators = @() }
  )
}
$e2 = Import-Event $d1 '3fa9e85d-0000-4000-8000-0000000000e2' '张国栋' @{
  checkup_date = '2026-06-02'; hospital = '市人民医院'; department = '内分泌科'
  note = '血糖控制尚可，继续当前用药。遵医嘱配合中药调理。'
  next_checkup_date = '2026-08-15'
  medication_changes_note = '中药调理：益肾健脾方 7 剂'
  reports = @(
    @{ title = '糖尿病相关'; indicators = @(
      @{ item_name = '糖化血红蛋白'; value = 7.5; unit = '%'; reference_range = '4.0~6.0' },
      @{ item_name = '空腹血糖'; value = 7.0; unit = 'mmol/L'; reference_range = '3.9~6.1' }
    ) },
    @{ title = '尿微量白蛋白'; indicators = @(
      @{ item_name = '尿微量白蛋白'; value = 128; unit = 'mg/L'; reference_range = '<30' },
      @{ item_name = 'ACR(尿)'; value = 180; unit = 'mg/g'; reference_range = '<30' }
    ) }
  )
}
$e3 = Import-Event $d1 '3fa9e85d-0000-4000-8000-0000000000e3' '张国栋' @{
  checkup_date = '2026-03-05'; hospital = '市中医院'; department = '肾病科'
  note = '首次系统复查，建立基线。'; next_checkup_date = '2026-06-02'
  reports = @(
    @{ title = '肾功能'; indicators = @(
      @{ item_name = '血肌酐'; value = 120; unit = 'μmol/L'; reference_range = '41~81' },
      @{ item_name = '尿素氮'; value = 8.4; unit = 'mmol/L'; reference_range = '2.9~8.2' }
    ) },
    @{ title = '糖化血红蛋白'; indicators = @(
      @{ item_name = '糖化血红蛋白'; value = 7.8; unit = '%'; reference_range = '4.0~6.0' }
    ) }
  )
}
$e4 = Import-Event $d2 '3fa9e85d-0000-4000-8000-0000000000e4' '李慧兰' @{
  checkup_date = '2026-05-18'; hospital = '社区医院'; department = '全科'
  note = '血压控制平稳，半年后随访。'; next_checkup_date = '2026-11-18'
  reports = @(
    @{ title = '血常规'; indicators = @(
      @{ item_name = '血红蛋白'; value = 128; unit = 'g/L'; reference_range = '115~150' },
      @{ item_name = '白细胞'; value = 6.2; unit = '10⁹/L'; reference_range = '3.5~9.5' }
    ) }
  )
}

# ===== 5. 重点清单（别名归并：ACR 两名归一） =====
Write-Host '== 5. 重点清单 =='
Push $d1 @(
  Row 'watch_items' @{ id = (Uid); profile_id = $p1; canonical_name = 'ACR'; aliases = @('尿微量白蛋白/肌酐比值(ACR)', 'ACR(尿)'); canonical_unit = 'mg/g'; sort_order = 1 }
  Row 'watch_items' @{ id = (Uid); profile_id = $p1; canonical_name = '血肌酐'; aliases = @('血肌酐'); canonical_unit = 'μmol/L'; sort_order = 2 }
  Row 'watch_items' @{ id = (Uid); profile_id = $p1; canonical_name = '糖化血红蛋白'; aliases = @('糖化血红蛋白'); canonical_unit = '%'; sort_order = 3 }
) '重点清单'

# ===== 6. 用药（长期西药/临时西药/中药；改量链 supersedes） =====
Write-Host '== 6. 用药与变化 =='
$m1 = Uid; $m2 = Uid; $m3 = Uid; $m4 = Uid; $m5 = Uid; $m6 = Uid; $m7 = Uid
$c1 = Uid; $c2 = Uid; $c3 = Uid; $c4 = Uid; $c5 = Uid
# 规则 3（01 §5.2）：change_id 必须已存在，先写用药变化；规则 2：supersedes 指向的条目须同批先写
Push $d1 @(
  Row 'med_changes' @{ id = $c1; profile_id = $p1; effective_date = '2026-08-15'; note = '复查后调整用药'; linked_event_id = $e1 }
  Row 'med_changes' @{ id = $c2; profile_id = $p1; effective_date = '2026-08-01'; note = '感冒，临时加药' }
  Row 'med_changes' @{ id = $c3; profile_id = $p1; effective_date = '2026-06-02'; note = '复查后中药调理'; linked_event_id = $e2 }
  Row 'med_changes' @{ id = $c4; profile_id = $p1; effective_date = '2025-11-20'; note = '初始建档' }
  Row 'med_changes' @{ id = $c5; profile_id = $p2; effective_date = '2025-06-10'; note = '初始建档' }
) '用药变化'
Push $d1 @(
  Row 'medication_items' @{ id = $m4; profile_id = $p1; category = 'long_term'; med_kind = 'western'; name = '缬沙坦'; dosage_text = '每次 80mg'; dose_slots = @('morning'); start_date = '2025-11-20'; end_date = '2026-08-15'; change_id = $c4 }
  Row 'medication_items' @{ id = $m2; profile_id = $p1; category = 'long_term'; med_kind = 'western'; name = '二甲双胍'; dosage_text = '每次 0.5g'; dose_slots = @('morning', 'evening'); start_date = '2025-11-20'; change_id = $c4 }
  Row 'medication_items' @{ id = $m1; profile_id = $p1; category = 'long_term'; med_kind = 'western'; name = '氯沙坦钾片'; dosage_text = '每次 50mg'; dose_slots = @('morning'); start_date = '2026-08-15'; supersedes_id = $m4; change_id = $c1 }
  Row 'medication_items' @{ id = $m3; profile_id = $p1; category = 'long_term'; med_kind = 'western'; name = '阿托伐他汀钙片'; dosage_text = '每次 20mg'; dose_slots = @('bedtime'); start_date = '2026-08-15'; change_id = $c1 }
  Row 'medication_items' @{ id = $m5; profile_id = $p1; category = 'temporary'; med_kind = 'western'; name = '连花清瘟胶囊'; dosage_text = '每次 4 粒'; dose_slots = @('morning', 'noon', 'evening'); start_date = '2026-08-01'; end_date = '2026-08-06'; change_id = $c2 }
  Row 'medication_items' @{ id = $m6; profile_id = $p1; category = 'temporary'; med_kind = 'tcm'; name = '益肾健脾方'; dosage_text = '7 剂，水煎服'; dose_slots = @('morning', 'evening'); start_date = '2026-06-02'; end_date = '2026-06-16'; change_id = $c3 }
  Row 'medication_items' @{ id = $m7; profile_id = $p2; category = 'long_term'; med_kind = 'western'; name = '苯磺酸氨氯地平片'; dosage_text = '每次 5mg'; dose_slots = @('morning'); start_date = '2025-06-10'; change_id = $c5 }
) '用药'

# ===== 7. 提醒（服药/测量/复查三类） =====
Write-Host '== 7. 提醒 =='
Push $d1 @(
  Row 'reminders' @{ id = (Uid); profile_id = $p1; type = 'medication'; times = @('08:00', '20:00'); enabled = $true }
  Row 'reminders' @{ id = (Uid); profile_id = $p1; type = 'measure'; times = @('19:00'); measure_type = 'bp'; enabled = $true }
  Row 'reminders' @{ id = (Uid); profile_id = $p1; type = 'checkup'; advance_days = @(7, 1, 0); enabled = $true }
  Row 'reminders' @{ id = (Uid); profile_id = $p2; type = 'medication'; times = @('08:00'); enabled = $true }
  Row 'reminders' @{ id = (Uid); profile_id = $p2; type = 'checkup'; advance_days = @(1, 0); enabled = $true }
) '提醒'

# ===== 8. 便签（普通/带提醒指定人/已完成） =====
Write-Host '== 8. 便签 =='
Push $d1 @(
  Row 'notes' @{ id = (Uid); profile_id = $p1; text = '这周有点感冒，注意观察血压变化。'; done = $false }
  Row 'notes' @{ id = (Uid); profile_id = $p1; text = '下周三上午去取药，顺便问医生二甲双胍要不要调整。'; done = $false; remind_at = (Ms '2026-09-11' '18:00'); tz_offset_min = 480; remind_targets = @($d2.id) }
  Row 'notes' @{ id = (Uid); profile_id = $p1; text = '复查报告已整理录入，原件照片 6 张已归档。'; done = $true }
  Row 'notes' @{ id = (Uid); profile_id = $p2; text = '降压药快吃完了，下周记得陪她去社区医院开药。'; done = $false; remind_at = (Ms '2026-09-08' '09:00'); tz_offset_min = 480; remind_targets = @() }
) '便签'

# ===== 9. 今日用药（西药余量/中药计数） =====
Write-Host '== 9. 今日用药 =='
Push $d1 @(
  Row 'daily_med_items' @{ id = (Uid); profile_id = $p1; is_tcm = $false; name = '氯沙坦钾片'; dose_text = '1 片'; dose_slots = @('morning'); stock_qty = 18; stock_unit = '片'; daily_qty = 1 }
  Row 'daily_med_items' @{ id = (Uid); profile_id = $p1; is_tcm = $false; name = '二甲双胍'; dose_text = '1 片'; dose_slots = @('morning', 'evening'); stock_qty = 42; stock_unit = '片'; daily_qty = 2 }
  Row 'daily_med_items' @{ id = (Uid); profile_id = $p1; is_tcm = $false; name = '阿托伐他汀钙片'; dose_text = '1 片'; dose_slots = @('bedtime'); stock_qty = 9; stock_unit = '片'; daily_qty = 1 }
  Row 'daily_med_items' @{ id = (Uid); profile_id = $p1; is_tcm = $true; name = '益肾健脾方'; tcm_packs = 5; tcm_days_per_pack = 2; tcm_used_days = 1 }
  Row 'daily_med_items' @{ id = (Uid); profile_id = $p2; is_tcm = $false; name = '苯磺酸氨氯地平片'; dose_text = '1 片'; dose_slots = @('morning'); stock_qty = 25; stock_unit = '片'; daily_qty = 1 }
) '今日用药'

# ===== 10. 附件元数据（归档到具体报告） =====
Write-Host '== 10. 附件元数据 =='
$sync = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=500" -Headers @{ Authorization = "Bearer $($d1.token)" }
$reports = $sync.changes | Where-Object { $_.table -eq 'reports' }
function ReportId($eventId, $title) { ($reports | Where-Object { $_.row.event_id -eq $eventId -and $_.row.title -eq $title } | Select-Object -First 1).row.id }
$att = @()
$n = 0
foreach ($x in @(@($e1, '尿常规', 2), @($e1, '肾功能+血糖', 1), @($e1, '肾脏B超', 3), @($e4, '血常规', 1))) {
  $rid = ReportId $x[0] $x[1]
  1..$x[2] | ForEach-Object {
    $n++
    $att += Row 'attachments' @{ id = (Uid); report_id = $rid; file_key = "seed/photo-$n.jpg"; mime = 'image/jpeg'; size_bytes = 245760; upload_state = 'done' }
  }
}
Push $d1 $att '附件元数据'

Write-Host "`n全部灌库完成。设备：陈小枫/林小岚/周小航；档案：张国栋/李慧兰/陈启航。"
