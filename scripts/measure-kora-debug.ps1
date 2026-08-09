<#
.SYNOPSIS
  Measure startup, idle CPU/memory, scrolling smoothness and thermal state for KoraDebug.

.DESCRIPTION
  This script is deliberately restricted to io.github.mkdevtests.kora.debug.
  It never installs an APK, clears application data, resets batterystats, or targets
  the release package. Results are written below komelia-app/build/perf by default.

  Startup runs use `am start -W -S`: the debug process is stopped, but its data and
  settings are preserved. The background test presses Home and brings KoraDebug back
  to the foreground when finished.

.EXAMPLE
  .\scripts\measure-kora-debug.ps1
  .\scripts\measure-kora-debug.ps1 -StartupRuns 5 -IdleSeconds 30
  .\scripts\measure-kora-debug.ps1 -Serial R52T604648B -SkipScroll
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 50)]
    [int]$StartupRuns = 10,

    [ValidateRange(5, 300)]
    [int]$IdleSeconds = 20,

    [ValidateRange(1, 20)]
    [int]$ScrollSwipes = 6,

    [switch]$SkipScroll,
    [string]$Serial,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$package = 'io.github.mkdevtests.kora.debug'
$activity = 'snd.komelia.MainActivity'
$component = "$package/$activity"
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $package.EndsWith('.debug', [StringComparison]::Ordinal)) {
    throw "Safety guard failed: benchmark package must end with .debug."
}

$branch = (& git -C $repoRoot rev-parse --abbrev-ref HEAD 2>$null).Trim()
if ($branch -eq 'main') {
    throw "Refusing to run from main. Switch to the dedicated debug/performance branch."
}

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) { $adb = $adbCommand.Source }
}
if (-not $adb -or -not (Test-Path -LiteralPath $adb)) {
    throw 'adb not found. Install Android platform-tools or add adb to PATH.'
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory, ValueFromRemainingArguments)]
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $adbArguments = @()
    if ($Serial) { $adbArguments += @('-s', $Serial) }
    $adbArguments += $Arguments
    # Windows PowerShell 5.1 wraps native stderr as ErrorRecord objects. Some
    # successful adb commands (notably `am start`) emit harmless warnings there,
    # so use the native exit code as the source of truth.
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $adb @adbArguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "adb failed ($exitCode): adb $($adbArguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

$deviceLines = & $adb devices
$connected = @($deviceLines | Select-String -Pattern '^([^\s]+)\s+device$')
if ($Serial) {
    if (-not ($connected -match "^$([regex]::Escape($Serial))\s+device$")) {
        throw "Device '$Serial' is not connected and authorized."
    }
} elseif ($connected.Count -ne 1) {
    throw "Expected exactly one connected device; found $($connected.Count). Pass -Serial when several are connected."
} else {
    $Serial = ($connected[0].Matches[0].Groups[1].Value)
}

$installed = Invoke-Adb -Arguments @('shell', 'pm', 'path', $package) -AllowFailure
if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) {
    throw "KoraDebug is not installed ($package). Build and install the debug APK first."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "komelia-app\build\perf\$stamp"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Save-AdbText {
    param([string]$Name, [string[]]$Arguments)
    $content = Invoke-Adb -Arguments $Arguments
    $content | Out-File -Encoding utf8 (Join-Path $OutputDirectory $Name)
    return $content
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if (-not $Values -or $Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    return $sorted[[math]::Max(0, $index)]
}

function Get-CpuSample {
    param([int]$ProcessId, [int]$Seconds)
    $raw = Invoke-Adb -Arguments @('shell', 'top', '-b', '-d', '1', '-n', $Seconds, '-p', $ProcessId)
    $values = foreach ($line in $raw) {
        if ($line -match "^\s*$ProcessId\s+") {
            $parts = $line.Trim() -split '\s+'
            if ($parts.Count -gt 8 -and $parts[8] -match '^\d+([\.,]\d+)?$') {
                [double]($parts[8] -replace ',', '.')
            }
        }
    }
    $measure = $values | Measure-Object -Average -Minimum -Maximum
    return [ordered]@{
        samples = @($values)
        averagePercent = if ($measure.Count) { [math]::Round($measure.Average, 2) } else { $null }
        minimumPercent = if ($measure.Count) { $measure.Minimum } else { $null }
        maximumPercent = if ($measure.Count) { $measure.Maximum } else { $null }
    }
}

function Get-MemorySummary {
    param([string]$Suffix)
    $raw = Save-AdbText "meminfo-$Suffix.txt" @('shell', 'dumpsys', 'meminfo', $package)
    $totalLine = $raw | Select-String -Pattern '^\s*TOTAL\s+' | Select-Object -First 1
    $summaryLine = $raw | Select-String -Pattern 'TOTAL PSS:' | Select-Object -First 1
    return [ordered]@{
        totalLine = if ($totalLine) { $totalLine.Line.Trim() } else { $null }
        summaryLine = if ($summaryLine) { $summaryLine.Line.Trim() } else { $null }
    }
}

function Get-GfxSummary {
    param([string]$Suffix)
    $raw = Save-AdbText "gfxinfo-$Suffix.txt" @('shell', 'dumpsys', 'gfxinfo', $package)
    $patterns = @(
        'Total frames rendered', 'Janky frames:', 'Janky frames \(legacy\)',
        '50th percentile', '90th percentile', '95th percentile', '99th percentile',
        'Number Missed Vsync', 'Number High input latency', 'Number Slow UI thread',
        'Number Slow bitmap uploads', 'Number Slow issue draw commands'
    )
    $selected = foreach ($pattern in $patterns) {
        $match = $raw | Select-String -Pattern $pattern | Select-Object -First 1
        if ($match) { $match.Line.Trim() }
    }
    return @($selected)
}

Write-Host "KoraDebug performance measurement"
Write-Host "  Branch : $branch"
Write-Host "  Device : $Serial"
Write-Host "  Package: $package"
Write-Host "  Output : $OutputDirectory"

$device = [ordered]@{
    serial = $Serial
    model = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.model') | Select-Object -First 1).Trim()
    device = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.device') | Select-Object -First 1).Trim()
    android = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.release') | Select-Object -First 1).Trim()
    sdk = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk') | Select-Object -First 1).Trim()
}
$batteryBefore = Save-AdbText 'battery-before.txt' @('shell', 'dumpsys', 'battery')
$thermalBefore = Save-AdbText 'thermal-before.txt' @('shell', 'dumpsys', 'thermalservice')

Write-Host "`nStartup ($StartupRuns process-stop runs; app data preserved)..."
$startup = for ($run = 1; $run -le $StartupRuns; $run++) {
    $raw = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-S', '-n', $component)
    $total = [int](($raw | Select-String '^TotalTime:\s*(\d+)' | Select-Object -First 1).Matches.Groups[1].Value)
    $wait = [int](($raw | Select-String '^WaitTime:\s*(\d+)' | Select-Object -First 1).Matches.Groups[1].Value)
    $stateMatch = $raw | Select-String '^LaunchState:\s*(.+)' | Select-Object -First 1
    $row = [pscustomobject]@{
        run = $run
        totalTimeMs = $total
        waitTimeMs = $wait
        launchState = if ($stateMatch) { $stateMatch.Matches.Groups[1].Value.Trim() } else { $null }
    }
    Write-Host ("  {0,2}: {1,5} ms" -f $run, $total)
    $row
}
$startup | Export-Csv -NoTypeInformation -Encoding utf8 (Join-Path $OutputDirectory 'startup.csv')
$startupValues = @($startup.totalTimeMs)
$startupMeasure = $startupValues | Measure-Object -Average -Minimum -Maximum

Start-Sleep -Seconds 8
$pidValue = [int]((Invoke-Adb -Arguments @('shell', 'pidof', $package) | Select-Object -First 1).Trim())
Write-Host "`nForeground idle CPU ($IdleSeconds s)..."
$foregroundCpu = Get-CpuSample -ProcessId $pidValue -Seconds $IdleSeconds
$foregroundMemory = Get-MemorySummary 'foreground'

Write-Host "Background idle CPU ($IdleSeconds s)..."
Invoke-Adb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
Start-Sleep -Seconds 3
$pidValue = [int]((Invoke-Adb -Arguments @('shell', 'pidof', $package) | Select-Object -First 1).Trim())
$backgroundCpu = Get-CpuSample -ProcessId $pidValue -Seconds $IdleSeconds
$backgroundMemory = Get-MemorySummary 'background'

Invoke-Adb -Arguments @('shell', 'am', 'start', '-n', $component) | Out-Null
# Let the Home snapshot, network refresh and visible covers settle before the
# interaction benchmark. Otherwise startup image decoding dominates scrolling.
Start-Sleep -Seconds 8

$scrollGfx = $null
if (-not $SkipScroll) {
    Write-Host "Home scrolling ($ScrollSwipes up + $ScrollSwipes down)..."
    Invoke-Adb -Arguments @('shell', 'dumpsys', 'gfxinfo', $package, 'reset') | Out-Null
    1..$ScrollSwipes | ForEach-Object {
        Invoke-Adb -Arguments @('shell', 'input', 'swipe', '800', '1900', '800', '650', '350') | Out-Null
        Start-Sleep -Milliseconds 450
    }
    1..$ScrollSwipes | ForEach-Object {
        Invoke-Adb -Arguments @('shell', 'input', 'swipe', '800', '650', '800', '1900', '350') | Out-Null
        Start-Sleep -Milliseconds 450
    }
    Start-Sleep -Seconds 3
    $scrollGfx = Get-GfxSummary 'home-scroll'
}

$batteryAfter = Save-AdbText 'battery-after.txt' @('shell', 'dumpsys', 'battery')
$thermalAfter = Save-AdbText 'thermal-after.txt' @('shell', 'dumpsys', 'thermalservice')
$packageDump = Save-AdbText 'package.txt' @('shell', 'dumpsys', 'package', $package)

$summary = [ordered]@{
    timestamp = $stamp
    branch = $branch
    package = $package
    device = $device
    startup = [ordered]@{
        runs = $startup.Count
        minimumMs = $startupMeasure.Minimum
        medianMs = Get-Percentile $startupValues 50
        averageMs = [math]::Round($startupMeasure.Average, 1)
        p90Ms = Get-Percentile $startupValues 90
        maximumMs = $startupMeasure.Maximum
    }
    foregroundIdleCpu = $foregroundCpu
    backgroundIdleCpu = $backgroundCpu
    foregroundMemory = $foregroundMemory
    backgroundMemory = $backgroundMemory
    homeScrollGfx = $scrollGfx
    notes = @(
        'Debug-build results include debug logging, symbols and non-release code layout.',
        'The tablet may be USB-powered; short runs do not measure battery drain directly.',
        'No application data or Android batterystats were reset.'
    )
}
$summary | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')

Write-Host "`nSummary"
Write-Host "  Startup median : $($summary.startup.medianMs) ms"
Write-Host "  Startup p90    : $($summary.startup.p90Ms) ms"
Write-Host "  Foreground CPU : $($foregroundCpu.averagePercent)% avg, $($foregroundCpu.maximumPercent)% max"
Write-Host "  Background CPU : $($backgroundCpu.averagePercent)% avg, $($backgroundCpu.maximumPercent)% max"
if ($scrollGfx) { $scrollGfx | ForEach-Object { Write-Host "  $_" } }
Write-Host "  Results        : $OutputDirectory"
