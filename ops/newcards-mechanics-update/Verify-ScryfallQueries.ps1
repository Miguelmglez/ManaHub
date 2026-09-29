param([string] $ReportPath = (Join-Path $PSScriptRoot ('reports\scryfall-queries-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')))

$ErrorActionPreference = 'Stop'
$checks = @(
    @{ section = 'Mana Base'; query = 't:land produces:B'; example = 'Swamp' },
    @{ section = 'Mana Base'; query = 'function:mana-rock'; example = 'Sol Ring' },
    @{ section = 'Plan Roles'; query = 'oracle:"creature dies"'; example = 'Blood Artist' },
    @{ section = 'Synergy'; query = 'oracle:"cards leave your graveyard"'; example = 'Spirit Mascot' },
    @{ section = 'Synergy'; query = 'keyword:harmonize'; example = "Roamer's Routine" },
    @{ section = 'Wizard'; query = 'keyword:flying'; example = 'Serra Angel' },
    @{ section = 'Wizard'; query = 'keyword:trample'; example = 'Colossal Dreadmaw' },
    @{ section = 'Remote keyword'; query = 'oracle:"empower Jace"'; example = "Protege's Awakening" }
)
$results = foreach ($check in $checks) {
    $query = '(' + $check.query + ') !"' + $check.example.Replace('"', '') + '"'
    $uri = 'https://api.scryfall.com/cards/search?q=' + [System.Uri]::EscapeDataString($query)
    try {
        $response = Invoke-RestMethod -Uri $uri -Headers @{ 'User-Agent' = 'ManaHubMechanicsAudit/1'; 'Accept' = 'application/json' } -TimeoutSec 30
        [pscustomobject]@{ section = $check.section; query = $check.query; example = $check.example; found = ($response.total_cards -gt 0); totalCards = $response.total_cards; status = 'verified' }
    } catch {
        [pscustomobject]@{ section = $check.section; query = $check.query; example = $check.example; found = $false; totalCards = 0; status = 'unavailable'; error = $_.Exception.Message }
    }
    Start-Sleep -Milliseconds 200
}
[System.IO.Directory]::CreateDirectory((Split-Path -Parent $ReportPath)) | Out-Null
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $ReportPath -Encoding utf8
$results | Format-Table section, query, example, found, totalCards, status
if ($results | Where-Object { $_.status -eq 'verified' -and -not $_.found }) { exit 1 }
