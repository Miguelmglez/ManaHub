$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'MechanicsUpdate.psm1') -Force
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('mechanics-update-' + [guid]::NewGuid().ToString('N'))
[System.IO.Directory]::CreateDirectory($testRoot) | Out-Null
try {
    $old = Join-Path $testRoot 'old.jsonl'
    $delta = Join-Path $testRoot 'delta.jsonl'
    $manifest = Join-Path $testRoot 'manifest.jsonl'
    [System.IO.File]::WriteAllLines($old, @('{"oracleId":"a","pipelineVersion":1}', '{"oracleId":"b","pipelineVersion":1}'))
    [System.IO.File]::WriteAllLines($delta, @('{"oracleId":"b","pipelineVersion":2}', '{"oracleId":"c","pipelineVersion":2}'))
    $count = Merge-MechanicsManifest -PreviousPath $old -DeltaPath $delta -DestinationPath $manifest
    if ($count -ne 3) { throw 'Full manifest did not retain unchanged cards.' }
    $rows = @([System.IO.File]::ReadLines($manifest) | ForEach-Object { $_ | ConvertFrom-Json })
    if (($rows | Where-Object oracleId -eq 'b').pipelineVersion -ne 2) { throw 'Delta did not replace an older card.' }
    $count = Merge-MechanicsManifest -PreviousPath $manifest -DeltaPath $delta -DestinationPath $manifest
    if ($count -ne 3) { throw 'Repeated execution changed manifest cardinality.' }
    $lockPath = Join-Path $testRoot 'run.lock'
    $lock = Enter-MechanicsRunLock $lockPath
    try {
        $blocked = $false
        try { $second = Enter-MechanicsRunLock $lockPath; $second.Dispose() } catch { $blocked = $true }
        if (-not $blocked) { throw 'Concurrent run was accepted.' }
    } finally { $lock.Dispose() }

    $jobState = Join-Path $testRoot 'job-state'
    $jobReports = Join-Path $testRoot 'reports'
    $cache = Join-Path $jobState 'cache'
    [System.IO.Directory]::CreateDirectory($cache) | Out-Null
    foreach ($prefix in @('oracle-cards', 'oracle-tags')) {
        foreach ($version in 1..3) {
            $file = Join-Path $cache "$prefix-$version.jsonl.gz"
            [System.IO.File]::WriteAllText($file, 'fixture')
            [System.IO.File]::SetLastWriteTimeUtc($file, [DateTime]::UtcNow.AddDays($version - 4))
        }
    }
    $config = Join-Path $testRoot 'config.json'
    @{ repository = $testRoot; stateDirectory = $jobState; reportDirectory = $jobReports } |
        ConvertTo-Json | Set-Content -LiteralPath $config
    $jobScript = Join-Path $PSScriptRoot 'Invoke-MechanicsUpdate.ps1'
    $generationFailed = $false
    try {
        & $jobScript -ConfigPath $config -PipelineInvoker {
            param($arguments)
            if ($arguments[1] -like '--args="run*') { throw 'Required source unavailable' }
        }
    } catch { $generationFailed = $true }
    if (-not $generationFailed -or (Test-Path (Join-Path $jobState 'pending.jsonl'))) {
        throw 'Source failure published a pending dataset.'
    }

    $uploadFailed = $false
    try {
        & $jobScript -ConfigPath $config -PipelineInvoker {
            param($arguments)
            if ($arguments[1] -like '--args="run*') {
                [System.IO.File]::WriteAllLines((Join-Path $jobState 'generated.jsonl'), @('{"oracleId":"a","pipelineVersion":2}'))
            } elseif ($arguments[1] -like '--args="upload*') { throw 'Partial upload failure' }
        }
    } catch { $uploadFailed = $true }
    if (-not $uploadFailed -or -not (Test-Path (Join-Path $jobState 'pending.jsonl')) -or
        (Test-Path (Join-Path $jobState 'manifest.jsonl'))) {
        throw 'Partial upload advanced the checkpoint or discarded pending data.'
    }

    $catalogFailed = $false
    try {
        & $jobScript -ConfigPath $config -PipelineInvoker {
            param($arguments)
            if ($arguments[1] -like '--args="catalog*') { throw 'Catalog source unavailable' }
        }
    } catch { $catalogFailed = $true }
    if (-not $catalogFailed -or -not (Test-Path (Join-Path $jobState 'pending.jsonl')) -or
        (Test-Path (Join-Path $jobState 'manifest.jsonl'))) {
        throw 'Catalog failure advanced the checkpoint or discarded pending data.'
    }
    if ((Get-ChildItem -LiteralPath $cache -File).Count -ne 6) {
        throw 'Failure removed last-known-good bulk files.'
    }

    $script:runCalledOnResume = $false
    & $jobScript -ConfigPath $config -PipelineInvoker {
        param($arguments)
        if ($arguments.Count -ne 2 -or $arguments[1] -notmatch '^--args="[^"]+"$') {
            throw 'Gradle application arguments were split or not wrapped for cmd.exe.'
        }
        if ($arguments[1] -like '--args="run*') { $script:runCalledOnResume = $true }
        if ($arguments[1] -like '--args="catalog*') {
            $reportOption = [regex]::Match($arguments[1], '--report ([^"]+)"')
            if (-not $reportOption.Success) { throw 'Catalog report argument missing.' }
            [System.IO.File]::WriteAllText($reportOption.Groups[1].Value, '{}')
        }
    }
    if ($script:runCalledOnResume -or (Test-Path (Join-Path $jobState 'pending.jsonl')) -or
        -not (Test-Path (Join-Path $jobState 'manifest.jsonl'))) {
        throw 'Restart did not resume the pending upload safely.'
    }
    if ((Get-ChildItem -LiteralPath $cache -File).Count -ne 4) {
        throw 'Successful checkpoint did not retain exactly two versions per bulk source.'
    }
    Write-Output 'Manifest, repeat, lock, source failure, partial upload, catalog failure, restart, cache retention: PASS'
} finally {
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
