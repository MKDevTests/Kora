<#
.SYNOPSIS
  Export Kora's on-device logs to a timestamped folder for bug reports.

.DESCRIPTION
  Pulls the rolling log files (komelia.log + rotations), the last logcat
  snapshot and any crash report from the connected device, plus a fresh dump of
  the current logcat filtered to errors + KoraPerf. Everything lands in one
  timestamped folder so a bug can be attached in a single drag.

  Targets KoraDebug by default; pass -Release for the published Kora package.

.EXAMPLE
  .\scripts\export-kora-logs.ps1
  .\scripts\export-kora-logs.ps1 -Release
  .\scripts\export-kora-logs.ps1 -OutDir D:\bugs
#>
[CmdletBinding()]
param(
    [switch]$Release,
    [string]$OutDir = "$env:USERPROFILE\Desktop"
)

$ErrorActionPreference = 'Stop'

# --- locate adb -------------------------------------------------------------
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}
if (-not $adb -or -not (Test-Path $adb)) {
    Write-Error "adb not found. Install platform-tools or add adb to PATH."
    return
}

# --- device check -----------------------------------------------------------
$devices = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $devices) {
    Write-Error "No device connected (check 'adb devices' and USB debugging)."
    return
}

# --- package + paths --------------------------------------------------------
$pkg = if ($Release) { "io.github.mkdevtests.kora" } else { "io.github.mkdevtests.kora.debug" }
$logDir = "/sdcard/Android/data/$pkg/files/komelia/logs"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $OutDir "kora-logs-$stamp"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

Write-Host "Package : $pkg"
Write-Host "Source  : $logDir"
Write-Host "Dest    : $dest"
Write-Host ""

# --- pull the rolling log files --------------------------------------------
$files = & $adb shell "ls $logDir" 2>$null
if ($LASTEXITCODE -ne 0 -or -not $files) {
    Write-Warning "No log directory on device yet (open the app at least once)."
} else {
    foreach ($f in ($files -split "`r?`n" | Where-Object { $_ -match '\S' })) {
        $name = $f.Trim()
        & $adb pull "$logDir/$name" (Join-Path $dest $name) 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host "  pulled $name" }
    }
}

# --- fresh logcat dumps (whole buffer + errors/perf only) -------------------
& $adb logcat -d | Out-File -Encoding utf8 (Join-Path $dest "logcat-full.txt")
& $adb logcat -d AndroidRuntime:E KoraPerf:I "*:S" |
    Out-File -Encoding utf8 (Join-Path $dest "logcat-errors-perf.txt")
Write-Host "  captured logcat-full.txt + logcat-errors-perf.txt"

Write-Host ""
Write-Host "Done -> $dest"
# Reveal in Explorer for a one-drag attach.
Start-Process explorer.exe $dest
