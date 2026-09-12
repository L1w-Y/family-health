# 契约：docs/05-页面结构与交互.md §4.2 测量分组验证
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\gradlew.bat testDebugUnitTest `
        --tests 'com.family.health.feature.records.MeasurementLogDataTest' `
        --tests 'com.family.health.data.MeasurementTimeBucketTest' `
        --tests 'com.family.health.feature.overview.OverviewMeasureDataTest' `
        --tests 'com.family.health.feature.entry.MeasureInputTest' `
        --tests 'com.family.health.util.DatesTest' `
        assembleDebug
    if ($LASTEXITCODE -ne 0) { throw '测量页验证失败' }
} finally {
    Pop-Location
}
