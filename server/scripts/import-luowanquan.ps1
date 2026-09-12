# import-luowanquan.ps1 · 罗万全 2026-06 ~ 2026-09 用药 / 血压 / 血糖 / 备注 导入
# 数据源：用户提供的 6 / 7 / 8-9 月文字记录逐项转录（详见文末「转录与口径说明」）。
# 通道：真实 API（auth → POST /sync 上行），追加型 measurements + 可改表 medication_items/med_changes/notes。
# 幂等：各批次使用固定 Idempotency-Key，重复执行不会产生重复数据。
# 用法：& ./scripts/import-luowanquan.ps1 -BaseUrl http://43.161.199.183 -FamilySecret 123123
param(
  [string]$BaseUrl = 'http://43.161.199.183',
  [string]$FamilySecret = '123123'
)
$ErrorActionPreference = 'Stop'

$ProfileId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3d0001'   # 罗万全（既有档案）
$Tz        = 480                                       # UTC+8

# 复查事件（既有）：用于 med_changes.linked_event_id
$Ev0904 = 'ef983d33-cc53-4b21-adcf-b7f045fd30ae'       # 2026-09-04

function Body($obj) { return ,([System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 12 -Compress))) }
function Ms($d, $t) { return [DateTimeOffset]::Parse("$d ${t}:00+08:00").ToUnixTimeMilliseconds() }
function Row($table, $row) { return @{ table = $table; op = 'insert'; row = $row } }
# 测量行确定性主键：index → UUID（保证重跑不产生新 ID）
function MUid([int]$i) { return ('33333333-3333-4333-8333-{0:000000000000}' -f $i) }

function Auth([string]$display) {
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth" -ContentType 'application/json; charset=utf-8' `
    -Body (Body @{ secret = $FamilySecret; display_name = $display })
  if (-not $r.ok) { throw "auth failed" }
  return @{ token = $r.token; id = $r.device.id }
}

function Push($dev, $items, [string]$label, [string]$idem) {
  $h = @{ Authorization = "Bearer $($dev.token)"; 'Idempotency-Key' = $idem }
  $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/sync" -Headers $h `
    -ContentType 'application/json; charset=utf-8' -Body (Body $items)
  if (-not $r.ok) { throw "push $label failed" }
  Write-Host ("  OK {0}（{1} 行）" -f $label, $items.Count)
}

$dev = Auth '罗万全家人'
Write-Host "== 鉴权 OK（device $($dev.id)）=="

# ===== 1. 用药变化（med_changes；须先于 medication_items 写入，供 change_id 引用） =====
Write-Host '== 1. 用药变化 =='
$c1 = '11111111-1111-4111-8111-111111111101'
$c2 = '11111111-1111-4111-8111-111111111102'
$c3 = '11111111-1111-4111-8111-111111111103'
$c4 = '11111111-1111-4111-8111-111111111104'
$c5 = '11111111-1111-4111-8111-111111111105'
Push $dev @(
  Row 'med_changes' @{ id = $c1; profile_id = $ProfileId; effective_date = '2026-06-11'; note = '初始用药方案（6 种西药 + 中药两日一剂）；当日停用厄贝沙坦片，为停用后第一天。' }
  Row 'med_changes' @{ id = $c2; profile_id = $ProfileId; effective_date = '2026-06-13'; note = '痛风性关节炎发作，临时加用醋酸地塞米松片（一天三次，一次两片），服用 6 月 13 日至 6 月 16 日。' }
  Row 'med_changes' @{ id = $c3; profile_id = $ProfileId; effective_date = '2026-06-17'; note = '当日与李医生沟通，中药方加入夏枯草、桑寄生、野菊花。' }
  Row 'med_changes' @{ id = $c4; profile_id = $ProfileId; effective_date = '2026-07-02'; note = '新增叶酸片（一天两次，一次两片，每次 10 毫克）。' }
  Row 'med_changes' @{ id = $c5; profile_id = $ProfileId; effective_date = '2026-09-04'; note = '九月方案：非布司他减量为一次 0.5 片；新增雷公藤多甙片（一天三次，一次四片，10mg/片，双倍剂量方案）；更换中药方。左氨氯地平（2.5mg）、阿司匹林（100mg）、非奈利酮（10mg）补充规格标注。'; linked_event_id = $Ev0904 }
) '用药变化' 'luowanquan-medchanges-v1'

# ===== 2. 用药条目（medication_items） =====
Write-Host '== 2. 用药条目 =='
$m01 = '22222222-2222-4222-8222-222222222201'  # 苯磺酸左氨氯地平片
$m02 = '22222222-2222-4222-8222-222222222202'  # 阿司匹林肠溶片
$m03 = '22222222-2222-4222-8222-222222222203'  # 阿托伐他汀钙片
$m04 = '22222222-2222-4222-8222-222222222204'  # 非奈利酮片
$m05 = '22222222-2222-4222-8222-222222222205'  # 百令片
$m06 = '22222222-2222-4222-8222-222222222206'  # 非布司他片（旧）
$m07 = '22222222-2222-4222-8222-222222222207'  # 中药方（6 月方）
$m08 = '22222222-2222-4222-8222-222222222208'  # 醋酸地塞米松片（临时）
$m09 = '22222222-2222-4222-8222-222222222209'  # 叶酸片
$m10 = '22222222-2222-4222-8222-222222222210'  # 非布司他片（新，减量）
$m11 = '22222222-2222-4222-8222-222222222211'  # 雷公藤多甙片
$m12 = '22222222-2222-4222-8222-222222222212'  # 中药方（9 月方）
$tcmJun = '两日一剂，水煎服。黄芪60 大黄5 丹参30 红花15 五味子20 麦冬15 人参30 石膏30 黄连10 天花粉30 全蝎10 柴胡20 黄芩30 夏枯草30 桑寄生30 野菊花10（单位：克）'
$tcmSep = '两日一剂，水煎服。黄芪60 大黄5 丹参30 红花15 五味子20 人参20 麦冬15 川芎15 全蝎10 地龙20 土鳖虫15 三七20 葛根30 生地30 石斛30 芡实30（单位：克）'
Push $dev @(
  Row 'medication_items' @{ id = $m01; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '苯磺酸左氨氯地平片'; dosage_text = '每次 1 片（2.5mg）'; dose_slots = @('morning'); start_date = '2026-06-11'; change_id = $c1 }
  Row 'medication_items' @{ id = $m02; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '阿司匹林肠溶片'; dosage_text = '每次 1 片（100mg）'; dose_slots = @('morning'); start_date = '2026-06-11'; change_id = $c1 }
  Row 'medication_items' @{ id = $m03; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '阿托伐他汀钙片'; dosage_text = '每次 1 片'; dose_slots = @('morning'); start_date = '2026-06-11'; change_id = $c1 }
  Row 'medication_items' @{ id = $m04; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '非奈利酮片'; dosage_text = '每次 1 片（10mg）'; dose_slots = @('morning'); start_date = '2026-06-11'; change_id = $c1 }
  Row 'medication_items' @{ id = $m05; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '百令片'; dosage_text = '每次 4 片'; dose_slots = @('morning','noon','evening'); start_date = '2026-06-11'; change_id = $c1 }
  Row 'medication_items' @{ id = $m06; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '非布司他片'; dosage_text = '每次 1 片'; dose_slots = @('morning'); start_date = '2026-06-11'; end_date = '2026-09-03'; change_id = $c1 }
  Row 'medication_items' @{ id = $m07; profile_id = $ProfileId; category = 'long_term'; med_kind = 'tcm'; name = '中药方（6 月方）'; dosage_text = $tcmJun; dose_slots = @('morning','evening'); start_date = '2026-06-11'; end_date = '2026-09-03'; change_id = $c1 }
  Row 'medication_items' @{ id = $m08; profile_id = $ProfileId; category = 'temporary'; med_kind = 'western'; name = '醋酸地塞米松片'; dosage_text = '每次 2 片'; dose_slots = @('morning','noon','evening'); start_date = '2026-06-13'; end_date = '2026-06-16'; change_id = $c2 }
  Row 'medication_items' @{ id = $m09; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '叶酸片'; dosage_text = '每次 2 片（10mg）'; dose_slots = @('morning','evening'); start_date = '2026-07-02'; change_id = $c4 }
  Row 'medication_items' @{ id = $m10; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '非布司他片'; dosage_text = '每次 0.5 片（20mg）'; dose_slots = @('morning'); start_date = '2026-09-04'; supersedes_id = $m06; change_id = $c5 }
  Row 'medication_items' @{ id = $m11; profile_id = $ProfileId; category = 'long_term'; med_kind = 'western'; name = '雷公藤多甙片'; dosage_text = '每次 4 片（10mg/片）'; dose_slots = @('morning','noon','evening'); start_date = '2026-09-04'; change_id = $c5 }
  Row 'medication_items' @{ id = $m12; profile_id = $ProfileId; category = 'long_term'; med_kind = 'tcm'; name = '中药方（9 月方）'; dosage_text = $tcmSep; dose_slots = @('morning','evening'); start_date = '2026-09-04'; supersedes_id = $m07; change_id = $c5 }
) '用药条目' 'luowanquan-meds-v1'

# ===== 3. 血压测量（type=bp） =====
# 行格式：'yyyy-MM-dd HH:mm systolic diastolic|note(可选)'
Write-Host '== 3. 血压测量 =='
$bpData = @(
  '2026-06-11 10:00 136 62|停用厄贝沙坦片第一天'
  '2026-06-13 10:00 143 67'
  '2026-06-13 11:00 156 74'
  '2026-06-16 15:00 157 67'
  '2026-06-17 09:00 179 70|当日与李医生沟通，中药加入夏枯草、桑寄生、野菊花'
  '2026-06-17 11:00 160 72'
  '2026-06-17 15:00 157 64'
  '2026-06-17 20:00 161 74'
  '2026-06-20 09:40 152 68'
  '2026-06-20 16:30 154 67'
  '2026-06-20 21:20 153 63'
  '2026-06-22 10:30 152 67'
  '2026-06-23 20:40 147 69'
  '2026-06-24 20:40 157 72'
  '2026-06-25 07:00 154 72'
  '2026-06-25 10:00 150 71'
  '2026-06-29 08:30 153 79'
  '2026-06-30 21:30 155 79'
  '2026-07-02 20:30 147 69|开始服用叶酸片，一天两次，一次10毫克'
  '2026-07-03 09:00 137 68'
  '2026-07-03 15:30 148 69'
  '2026-07-05 21:20 140 67'
  '2026-07-06 09:00 142 64'
  '2026-07-06 16:00 145 69'
  '2026-07-07 16:30 164 74'
  '2026-07-07 22:00 143 67'
  '2026-07-08 08:00 168 78|在医院测量，早餐空腹，未服用任何药物'
  '2026-07-08 11:00 150 68|在医院检查完，服用降压药后测量'
  '2026-07-08 16:30 145 65|12:00回到家中，服用叶酸片及其他日常中西药后'
  '2026-07-09 12:00 141 68|原文未标注时间，取 12:00'
  '2026-07-10 08:00 126 57|早上'
  '2026-07-10 15:00 150 71|下午'
  '2026-07-11 08:00 129 53|早上'
  '2026-07-11 15:00 146 71|下午'
  '2026-07-11 20:00 140 64|晚上'
  '2026-07-11 10:00 147 75|原文未标注时段，取 10:00'
  '2026-07-11 16:00 162 72|原文未标注时段，取 16:00'
  '2026-07-11 21:00 154 71|原文未标注时段，取 21:00'
  '2026-07-12 12:00 137 58|原文未标注时间，取 12:00'
  '2026-07-14 08:00 165 74|原文未标注时段，取 08:00'
  '2026-07-14 15:00 148 66|原文未标注时段，取 15:00'
  '2026-07-15 08:00 144 65|原文未标注时段，取 08:00'
  '2026-07-15 15:00 147 67|原文未标注时段，取 15:00'
  '2026-07-17 08:00 146 68|原文未标注时段，取 08:00'
  '2026-07-17 15:00 145 72|原文未标注时段，取 15:00'
  '2026-07-21 08:00 126 59|早上'
  '2026-07-21 15:00 141 67|下午'
  '2026-07-21 20:00 151 71|晚上'
  '2026-07-22 08:00 157 71|原文未标注时段，取 08:00'
  '2026-07-22 15:00 148 70|原文未标注时段，取 15:00'
  '2026-07-23 08:00 148 65|原文未标注时段，取 08:00'
  '2026-07-23 15:00 154 69|原文未标注时段，取 15:00'
  '2026-07-24 08:00 133 64|早上'
  '2026-07-24 15:00 160 74|下午'
  '2026-07-24 20:00 153 67|晚上'
  '2026-07-25 08:00 129 64|早上'
  '2026-07-25 15:00 150 69|下午'
  '2026-07-25 20:00 147 69|晚上'
  '2026-07-26 08:00 129 61|早上'
  '2026-07-26 15:00 138 70|下午'
  '2026-07-26 20:00 140 64|晚上'
  '2026-07-27 12:00 121 64|原文未标注时间，取 12:00'
  '2026-07-29 08:00 138 69|早上'
  '2026-07-29 15:00 137 65|下午'
  '2026-07-29 20:00 143 67|晚上'
  '2026-07-30 08:00 121 1|早上；原文如此（121/1），舒张压疑似笔误'
  '2026-07-30 15:00 153 74|下午'
  '2026-07-30 20:00 153 74|晚上'
  '2026-07-31 08:00 139 65|早上'
  '2026-07-31 15:00 155 67|下午'
  '2026-07-31 20:00 158 75|晚上'
  '2026-08-01 08:00 134 68|早上'
  '2026-08-01 15:00 152 67|下午'
  '2026-08-01 20:00 150 66|晚上'
  '2026-08-11 16:00 159 74'
  '2026-08-12 10:30 131 63'
  '2026-08-12 16:00 150 64'
  '2026-08-12 20:30 149 67'
  '2026-08-13 10:30 144 70'
  '2026-08-13 12:00 131 61'
  '2026-08-13 16:00 153 72'
  '2026-08-14 13:30 132 68'
  '2026-08-15 13:00 132 68'
  '2026-08-16 16:00 148 73'
  '2026-08-26 15:30 148 73'
  '2026-08-27 16:00 142 70'
  '2026-08-28 15:40 148 73'
  '2026-08-28 20:30 140 67'
  '2026-08-29 16:00 145 70'
)
$bpRows = @()
$idx = 0
foreach ($line in $bpData) {
  $idx++
  $parts = $line -split '\|', 2
  $f = $parts[0].Trim() -split '\s+'
  $row = @{ id = (MUid $idx); profile_id = $ProfileId; type = 'bp'; measured_at = (Ms $f[0] $f[1]); tz_offset_min = $Tz; systolic = [int]$f[2]; diastolic = [int]$f[3] }
  if ($parts.Count -gt 1 -and $parts[1].Trim() -ne '') { $row.note = $parts[1].Trim() }
  $bpRows += (Row 'measurements' $row)
}
Push $dev $bpRows '血压测量' 'luowanquan-bp-v1'

# ===== 4. 血糖测量（type=glucose） =====
# 行格式：'yyyy-MM-dd HH:mm value context|note(可选)'
Write-Host '== 4. 血糖测量 =='
$gluData = @(
  '2026-06-11 10:00 7.8 after_meal_2h|用药第一天'
  '2026-06-17 17:30 6.2 before_meal|餐前'
  '2026-06-17 11:00 9.2 after_meal_2h|餐后'
  '2026-06-17 20:00 8.8 after_meal_2h|餐后'
  '2026-06-21 10:30 6.7 after_meal_2h|餐后'
  '2026-06-25 07:00 6.1 fasting|空腹'
  '2026-06-25 10:00 9.4 after_meal_2h|餐后'
  '2026-07-03 09:00 6.6 after_meal_2h|早饭后'
  '2026-07-06 16:00 8.4 before_meal|晚饭前'
  '2026-07-07 16:30 6.7 before_meal|下午饭前'
  '2026-07-08 07:00 6.8 fasting|早晨空腹，原文未标注时间，取 07:00'
  '2026-07-20 14:30 6.8 random'
  '2026-07-22 15:40 8.2 random'
  '2026-07-24 10:00 13 after_meal_2h|饭后血糖'
  '2026-07-26 09:00 7.2 after_meal_2h|饭后血糖'
  '2026-07-27 09:40 6.3 after_meal_2h|饭后血糖'
  '2026-07-30 09:00 5.9 fasting|空腹血糖'
  '2026-08-12 07:00 6.0 fasting|空腹'
  '2026-08-16 16:00 8.2 random'
  '2026-08-26 16:30 7.8 random'
  '2026-09-05 08:00 7.2 fasting|空腹'
)
$gluRows = @()
$gidx = 5000
foreach ($line in $gluData) {
  $gidx++
  $parts = $line -split '\|', 2
  $f = $parts[0].Trim() -split '\s+'
  $row = @{ id = (MUid $gidx); profile_id = $ProfileId; type = 'glucose'; measured_at = (Ms $f[0] $f[1]); tz_offset_min = $Tz; glucose_mmol = [double]$f[2]; glucose_context = $f[3] }
  if ($parts.Count -gt 1 -and $parts[1].Trim() -ne '') { $row.note = $parts[1].Trim() }
  $gluRows += (Row 'measurements' $row)
}
Push $dev $gluRows '血糖测量' 'luowanquan-glu-v1'

# ===== 5. 备注（notes；含中药大黄用法、治疗重点与病情分析） =====
Write-Host '== 5. 备注 =='
Push $dev @(
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444401'; profile_id = $ProfileId; text = '【中药大黄用法】中药方含大黄 5 克。改善肾功能用法：有效成分是游离大黄蒽醌，致泻成分是结合大黄蒽醌；高温久煎可使致泻成分分解转化为改善肾功能的成分。故含大黄中药应充分熬煮——每剂第一次熬开后转小火再煮 40 分钟以上。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444402'; profile_id = $ProfileId; text = '【大黄致泻提醒】5 克大黄大约增加一次大便，便前可能出现阵发性腹痛。此外，冰冻保存或气温较高环境下中药本身也易引起腹泻，需与大黄所致腹泻相区别，避免因保存不当导致腹泻而误判。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444403'; profile_id = $ProfileId; text = '【治疗预期·6月】当前治疗需同时兼顾血糖、血压、尿蛋白和肾功能四项指标，相互之间存在制约，指标改善速度可能较为有限。这属于正常情况，需要足够的耐心和信心，坚持规律用药和定期复查。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444404'; profile_id = $ProfileId; text = '【治疗重点·8月】7 月重点放在血糖控制上；8 月起治疗重心转移到肾功能和尿蛋白浓度的改善。目前仍保留控制血糖和血压的药物，同时已调整控制血压的治疗思路。请继续规律监测血压和血糖，做好记录。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444405'; profile_id = $ProfileId; text = '【病情趋势·9月】肾功能（肌酐）：7月8日171 → 8月6日143 → 9月4日128（正常值上限123），持续改善。血压：从150-160mmHg逐步下降至140附近甚至132附近。糖化血红蛋白：7月8日6.5% → 9月6.2%。尿蛋白（ACR）：1200 → 1500 → 1200，波动，仍处很高水平。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444406'; profile_id = $ProfileId; text = '【病情判断·9月】综合尿蛋白较高而肌酐趋于正常的表现，判断为糖尿病合并慢性肾炎，推测病理类型可能为膜性肾病（若穿刺），而非单纯的糖尿病肾病。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444407'; profile_id = $ProfileId; text = '【九月治疗重点】加强控制肾炎，尽快降低尿蛋白。在原有药物基础上新增雷公藤多甙片：一天三次，一次四片（10mg/片），即说明书常规剂量的两倍，采用“双倍雷公藤多甙治疗慢性肾炎”方案。推荐使用湖北黄石飞云公司生产的雷公藤多甙片。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444408'; profile_id = $ProfileId; text = '【雷公藤多甙片注意事项】1. 不良反应：可能出现恶心呕吐、食欲下降，少数人有轻微肝脏毒性。2. 应对：饭后服用以减轻胃肠不适；每日用量分 3 次服用；下个月复查一次肝功能，同时观察肾功能走向。3. 预期：一般肝功能正常，肾功能在中药诱导下不会明显升高；若肾炎得到控制，肌酐可能进一步下降甚至恢复正常。'; done = $false }
  Row 'notes' @{ id = '44444444-4444-4444-8444-444444444409'; profile_id = $ProfileId; text = '【治疗预期·9月】若肾炎控制良好，待尿蛋白降至 700 附近时，肌酐有望完全恢复正常。'; done = $false }
) '备注' 'luowanquan-notes-v1'

# ===== 6. 校验：拉取确认 =====
Write-Host '== 6. 校验 =='
$s = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=500" -Headers @{ Authorization = "Bearer $($dev.token)" }
$s.changes | Group-Object table | Select-Object Name, Count | Format-Table -AutoSize | Out-String | Write-Host
Write-Host '全部导入完成。'
