Set-StrictMode -Version Latest

function Merge-MechanicsManifest {
    param(
        [string] $PreviousPath,
        [Parameter(Mandatory)] [string] $DeltaPath,
        [Parameter(Mandatory)] [string] $DestinationPath
    )

    $rows = [ordered]@{}
    foreach ($path in @($PreviousPath, $DeltaPath)) {
        if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path)) { continue }
        foreach ($line in [System.IO.File]::ReadLines($path)) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $row = $line | ConvertFrom-Json -ErrorAction Stop
            if ([string]::IsNullOrWhiteSpace($row.oracleId)) { throw "Row without oracleId in $path" }
            $rows[$row.oracleId] = $line
        }
    }
    $directory = Split-Path -Parent $DestinationPath
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $temporary = "$DestinationPath.tmp.$([guid]::NewGuid().ToString('N'))"
    try {
        $orderedLines = foreach ($key in ($rows.Keys | Sort-Object -CaseSensitive)) { $rows[$key] }
        [System.IO.File]::WriteAllLines($temporary, [string[]]@($orderedLines), [System.Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $DestinationPath) {
            [System.IO.File]::Replace($temporary, $DestinationPath, "$DestinationPath.previous")
        } else {
            [System.IO.File]::Move($temporary, $DestinationPath)
        }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
    return $rows.Count
}

function Enter-MechanicsRunLock {
    param([Parameter(Mandatory)] [string] $Path)
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    try {
        return [System.IO.File]::Open($Path, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    } catch [System.IO.IOException] {
        throw 'A mechanics update is already running.'
    }
}

Export-ModuleMember -Function Merge-MechanicsManifest, Enter-MechanicsRunLock
