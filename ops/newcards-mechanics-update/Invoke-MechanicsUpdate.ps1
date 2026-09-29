param(
    [string] $ConfigPath = (Join-Path $PSScriptRoot 'config.json'),
    [switch] $DryRun,
    [scriptblock] $PipelineInvoker
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'MechanicsUpdate.psm1') -Force
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$repo = [System.IO.Path]::GetFullPath($config.repository)
$state = [System.IO.Path]::GetFullPath($config.stateDirectory)
$reportDirectory = [System.IO.Path]::GetFullPath($config.reportDirectory)
if ($DryRun) {
    Write-Output "Would run :tools:tag-pipeline from $repo using checkpoint $([System.IO.Path]::Combine($state, 'manifest.jsonl'))"
    return
}
[System.IO.Directory]::CreateDirectory($state) | Out-Null
[System.IO.Directory]::CreateDirectory($reportDirectory) | Out-Null
$lock = Enter-MechanicsRunLock (Join-Path $state 'run.lock')
$started = [DateTimeOffset]::Now
$report = [ordered]@{ startedAt = $started.ToString('o'); status = 'running'; resumedPending = $false; deltaRows = 0; manifestRows = 0; catalogReport = $null; cacheCleanupWarning = $null; error = $null }
$pending = Join-Path $state 'pending.jsonl'
$manifest = Join-Path $state 'manifest.jsonl'
$generated = Join-Path $state 'generated.jsonl'
$reportPath = Join-Path $reportDirectory ('run-' + $started.ToString('yyyyMMdd-HHmmss') + '.json')
$catalogReportPath = Join-Path $reportDirectory ('catalog-' + $started.ToString('yyyyMMdd-HHmmss') + '.json')
if ($null -eq $PipelineInvoker) {
    $PipelineInvoker = {
        param($arguments)
        Push-Location $repo
        try {
            & (Join-Path $repo 'gradlew.bat') @arguments
            if ($LASTEXITCODE -ne 0) { throw "Pipeline command failed: $LASTEXITCODE" }
        } finally { Pop-Location }
    }
}
try {
    if (-not (Test-Path -LiteralPath $pending)) {
        if (Test-Path -LiteralPath $generated) { Remove-Item -LiteralPath $generated -Force }
        $runSpec = 'run --out ' + $generated + ' --cache-dir ' + (Join-Path $state 'cache')
        if (Test-Path -LiteralPath $manifest) { $runSpec += ' --since ' + $manifest }
        & $PipelineInvoker @(':tools:tag-pipeline:run', ('--args="' + $runSpec + '"'))
        if (-not (Test-Path -LiteralPath $generated)) { throw 'Pipeline did not create its output.' }
        [System.IO.File]::Move($generated, $pending)
    } else {
        $report.resumedPending = $true
    }

    $report.deltaRows = @([System.IO.File]::ReadLines($pending) | Where-Object { $_.Length -gt 0 }).Count
    if ($report.deltaRows -gt 0) {
        $uploadArg = ':tools:tag-pipeline:run'
        $uploadOptions = '--args="upload --in ' + $pending + '"'
        & $PipelineInvoker @($uploadArg, $uploadOptions)
    }

    $catalogOptions = '--args="catalog --cache-dir ' + (Join-Path $state 'cache') + ' --report ' + $catalogReportPath + '"'
    & $PipelineInvoker @(':tools:tag-pipeline:run', $catalogOptions)
    if (-not (Test-Path -LiteralPath $catalogReportPath)) { throw 'Catalog command did not create its report.' }
    $report.catalogReport = $catalogReportPath

    $report.manifestRows = Merge-MechanicsManifest -PreviousPath $manifest -DeltaPath $pending -DestinationPath $manifest
    Remove-Item -LiteralPath $pending -Force
    $report.status = 'completed'
    try {
        $cacheDirectory = Join-Path $state 'cache'
        foreach ($pattern in @('oracle-cards-*.jsonl.gz', 'oracle-tags-*.jsonl.gz')) {
            Get-ChildItem -LiteralPath $cacheDirectory -Filter $pattern -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTimeUtc -Descending |
                Select-Object -Skip 2 |
                Remove-Item -Force
        }
    } catch {
        $report.cacheCleanupWarning = $_.Exception.Message
    }
} catch {
    $report.status = 'failed'
    $report.error = $_.Exception.Message
    throw
} finally {
    $report.completedAt = [DateTimeOffset]::Now.ToString('o')
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding utf8
    $lock.Dispose()
}
