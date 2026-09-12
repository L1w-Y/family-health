# 契约：docs/02-数据库与同步.md §3.9/§3.15/§4.2；docs/05-页面结构与交互.md §3/§7/§8
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\gradlew.bat testDebugUnitTest `
        --tests 'com.family.health.data.MedicationStockTest' `
        --tests 'com.family.health.data.DailyMedIdentityTest' `
        --tests 'com.family.health.data.sync.SyncPushPolicyTest' `
        --tests 'com.family.health.data.sync.DailyMedOutboxRepairTest' `
        assembleDebug
    if ($LASTEXITCODE -ne 0) { throw '今日用药库存/同步修复验证失败' }
} finally {
    Pop-Location
}
