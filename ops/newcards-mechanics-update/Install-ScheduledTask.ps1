param([string] $TaskName = 'ManaHub New Cards and Mechanics Update')

$ErrorActionPreference = 'Stop'
$madrid = [System.TimeZoneInfo]::FindSystemTimeZoneById('Romance Standard Time')
$local = [System.TimeZoneInfo]::Local
if ($local.Id -ne $madrid.Id) {
    throw "Set the Windows timezone to Madrid ($($madrid.Id)) before installing the daily 06:00 task. Current: $($local.Id)"
}
$script = Join-Path $PSScriptRoot 'Invoke-MechanicsUpdate.ps1'
$shell = (Get-Command pwsh.exe -ErrorAction Stop).Source
$action = New-ScheduledTaskAction -Execute $shell -Argument ('-NoProfile -NonInteractive -File "' + $script + '"')
$trigger = New-ScheduledTaskTrigger -Daily -At '06:00'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 4) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force -ErrorAction Stop | Out-Null
Get-ScheduledTask -TaskName $TaskName -ErrorAction Stop | Select-Object TaskName, State
