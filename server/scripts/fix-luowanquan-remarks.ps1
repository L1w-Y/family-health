# fix-luowanquan-remarks.ps1 · 把罗万全的「治疗/用药备注」从便签(notes)迁移到用药变化(med_changes)，
# 并将用药变化关联到对应复查事件(checkup_events.linked_event_id)；复查病情分析落到对应复查事件 note。
# 幂等：固定 Idempotency-Key，重复执行不产生重复数据。
# 注意：备注文本一律用单引号字符串（PowerShell 会把中文弯引号 “ ” 视作定界符）。
# 用法：& ./scripts/fix-luowanquan-remarks.ps1 -BaseUrl http://43.161.199.183 -FamilySecret 123123
param(
  [string]$BaseUrl = 'http://43.161.199.183',
  [string]$FamilySecret = '123123'
)
$ErrorActionPreference = 'Stop'

$ProfileId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3d0001'
$Ev0806    = '4aee1d32-f36c-48b9-912c-5ad7c010b7e0'   # 2026-08-06 复查
$Ev0904    = 'ef983d33-cc53-4b21-adcf-b7f045fd30ae'   # 2026-09-04 复查

function Body($obj) { return ,([System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 12 -Compress))) }
function Wr($table, $op, $row) { return @{ table = $table; op = $op; row = $row } }
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
Write-Host "== 鉴权 OK =="

# ===== 备注文本（单引号多行字符串） =====
$c1note = '初始用药方案（6 种西药 + 中药两日一剂）；当日停用厄贝沙坦片，为停用后第一天。
【治疗预期·6月】当前治疗需同时兼顾血糖、血压、尿蛋白和肾功能四项指标，相互之间存在制约，指标改善速度可能较为有限。这属于正常情况，需要足够的耐心和信心，坚持规律用药和定期复查。'

$c3note = '当日与李医生沟通，中药方加入夏枯草、桑寄生、野菊花。
【中药大黄用法】中药方含大黄 5 克。改善肾功能用法：有效成分是游离大黄蒽醌，致泻成分是结合大黄蒽醌；高温久煎可使致泻成分分解转化为改善肾功能的成分。故含大黄中药应充分熬煮——每剂第一次熬开后转小火再煮 40 分钟以上。
【大黄致泻提醒】5 克大黄大约增加一次大便，便前可能出现阵发性腹痛。此外，冰冻保存或气温较高环境下中药本身也易引起腹泻，需与大黄所致腹泻相区别，避免因保存不当导致腹泻而误判。'

$c5note = '九月方案：非布司他减量为一次 0.5 片；新增雷公藤多甙片（一天三次，一次四片，10mg/片，双倍剂量方案）；更换中药方。左氨氯地平（2.5mg）、阿司匹林（100mg）、非奈利酮（10mg）补充规格标注。
【九月治疗重点】加强控制肾炎，尽快降低尿蛋白。在原有药物基础上新增雷公藤多甙片：一天三次，一次四片（10mg/片），即说明书常规剂量的两倍，采用“双倍雷公藤多甙治疗慢性肾炎”方案。推荐使用湖北黄石飞云公司生产的雷公藤多甙片。
【雷公藤多甙片注意事项】1. 不良反应：可能出现恶心呕吐、食欲下降，少数人有轻微肝脏毒性。2. 应对：饭后服用以减轻胃肠不适；每日用量分 3 次服用；下个月复查一次肝功能，同时观察肾功能走向。3. 预期：一般肝功能正常，肾功能在中药诱导下不会明显升高；若肾炎得到控制，肌酐可能进一步下降甚至恢复正常。
【治疗预期·9月】若肾炎控制良好，待尿蛋白降至 700 附近时，肌酐有望完全恢复正常。'

$c6note = '【治疗重点·8月】7 月重点放在血糖控制上；8 月起治疗重心转移到肾功能和尿蛋白浓度的改善。目前仍保留控制血糖和血压的药物，同时已调整控制血压的治疗思路。请继续规律监测血压和血糖，做好记录。'

$ev0904note = '【病情趋势·9月】肾功能（肌酐）：7月8日171 → 8月6日143 → 9月4日128（正常值上限123），持续改善。血压：从150-160mmHg逐步下降至140附近甚至132附近。糖化血红蛋白：7月8日6.5% → 9月6.2%。尿蛋白（ACR）：1200 → 1500 → 1200，波动，仍处很高水平。
【病情判断·9月】综合尿蛋白较高而肌酐趋于正常的表现，判断为糖尿病合并慢性肾炎，推测病理类型可能为膜性肾病（若穿刺），而非单纯的糖尿病肾病。'

# ===== 1. 删除 9 条便签（原有医疗文本不是便签） =====
Write-Host '== 1. 删除便签 =='
Push $dev @(
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444401' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444402' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444403' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444404' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444405' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444406' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444407' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444408' }
  Wr 'notes' 'delete' @{ id = '44444444-4444-4444-8444-444444444409' }
) '删除便签' 'luowanquan-notes-rm-v1'

# ===== 2. 用药变化：写入备注 + 关联复查 =====
Write-Host '== 2. 用药变化（备注 + 关联复查） =='
Push $dev @(
  Wr 'med_changes' 'update' @{ id = '11111111-1111-4111-8111-111111111101'; profile_id = $ProfileId; effective_date = '2026-06-11'; note = $c1note; linked_event_id = $null }
  Wr 'med_changes' 'update' @{ id = '11111111-1111-4111-8111-111111111103'; profile_id = $ProfileId; effective_date = '2026-06-17'; note = $c3note; linked_event_id = $null }
  Wr 'med_changes' 'update' @{ id = '11111111-1111-4111-8111-111111111105'; profile_id = $ProfileId; effective_date = '2026-09-04'; note = $c5note; linked_event_id = $Ev0904 }
  Wr 'med_changes' 'insert' @{ id = '11111111-1111-4111-8111-111111111106'; profile_id = $ProfileId; effective_date = '2026-08-06'; note = $c6note; linked_event_id = $Ev0806 }
) '用药变化' 'luowanquan-medchanges-fix-v1'

# ===== 3. 复查事件：9/4 事件 note 落病情分析（整行 LWW，须带全业务列） =====
Write-Host '== 3. 复查事件 note =='
Push $dev @(
  Wr 'checkup_events' 'update' @{ id = $Ev0904; profile_id = $ProfileId; checkup_date = '2026-09-04'; hospital = $null; department = $null; note = $ev0904note; next_checkup_date = '2026-09-08'; medication_changes_note = $null }
) '复查事件 note' 'luowanquan-checkup-note-v1'

# ===== 4. 校验 =====
Write-Host '== 4. 校验 =='
$s = Invoke-RestMethod -Uri "$BaseUrl/sync?since=0&limit=500" -Headers @{ Authorization = "Bearer $($dev.token)" }
"notes(未删) = " + (($s.changes | Where-Object { $_.table -eq 'notes' -and -not $_.row.deleted }).Count)
"med_changes(未删) = " + (($s.changes | Where-Object { $_.table -eq 'med_changes' -and -not $_.row.deleted }).Count)
$s.changes | Where-Object { $_.table -eq 'med_changes' -and -not $_.row.deleted } | Sort-Object { $_.row.effective_date } | ForEach-Object { "{0} linked={1}" -f $_.row.effective_date, $_.row.linked_event_id }
"全部修正完成。"
