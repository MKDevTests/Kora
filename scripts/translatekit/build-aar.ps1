# Builds translate-kit's Android AAR and vendors it. Step 2 of 2.
#
#     scripts\translatekit\build-aar.ps1              arm64-v8a only (the tablet)
#     scripts\translatekit\build-aar.ps1 -BothAbis    plus x86_64, for an emulator
#
# Runs in PowerShell rather than WSL because this is a CMake/NDK build and
# local.properties points at the Windows SDK, whose NDK ships windows-x86_64
# toolchains only. See fetch-and-patch.sh, which must have run first.
#
# The native compile is the slow part: roughly ten minutes per ABI, which is why
# one ABI is the default. The tablet is arm64.

param(
    [switch]$BothAbis
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Src = Join-Path $Root "third_party\translatekit-src"
$Out = Join-Path $Root "third_party\translatekit"
$RequiredNdk = "28.2.13676358"

if (-not (Test-Path (Join-Path $Src "android\gradlew.bat"))) {
    throw "No translate-kit checkout at $Src. Run scripts/translatekit/fetch-and-patch.sh in WSL first."
}

# The engine submodule is what fetch-and-patch.sh pulls; without it CMake fails
# late, after configuring, with a message about TRANSLATEKIT_WITH_ENGINE.
if (-not (Test-Path (Join-Path $Src "third_party\translations\CMakeLists.txt"))) {
    throw "The Bergamot engine submodule is missing. Re-run scripts/translatekit/fetch-and-patch.sh."
}

$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$NdkPath = Join-Path $Sdk "ndk\$RequiredNdk"
if (-not (Test-Path $NdkPath)) {
    # Pinned in translate-kit's build.gradle, so an installed 27.x will not do:
    # Gradle resolves ndkVersion exactly and fails rather than falling back.
    Write-Host "NDK $RequiredNdk is not installed. Install it with:" -ForegroundColor Yellow
    Write-Host "    & `"$Sdk\cmdline-tools\latest\bin\sdkmanager.bat`" `"ndk;$RequiredNdk`""
    throw "missing NDK $RequiredNdk"
}

$abiArgs = @()
if (-not $BothAbis) { $abiArgs = @("-PtestAbi=arm64-v8a") }

Push-Location (Join-Path $Src "android")
try {
    $started = Get-Date
    & .\gradlew.bat :translate-kit:assembleRelease @abiArgs --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradle failed with $LASTEXITCODE" }
    $elapsed = (Get-Date) - $started
    Write-Host ("==> native build took {0:mm}m {0:ss}s" -f $elapsed)
} finally {
    Pop-Location
}

$aar = Get-ChildItem (Join-Path $Src "android\translate-kit\build\outputs\aar") -Filter "*release*.aar" |
    Select-Object -First 1
if (-not $aar) { throw "the build reported success but produced no AAR" }

New-Item -ItemType Directory -Force $Out | Out-Null
$dest = Join-Path $Out "translate-kit-android.aar"
Copy-Item $aar.FullName $dest -Force

# The licence has to travel with the binary: we are redistributing someone
# else's Apache-2.0 work inside our APK.
Copy-Item (Join-Path $Src "LICENSE") (Join-Path $Out "LICENSE") -Force
Copy-Item (Join-Path $Src "NOTICE") (Join-Path $Out "NOTICE") -Force
Copy-Item (Join-Path $Src "THIRD_PARTY_LICENSES.md") (Join-Path $Out "THIRD_PARTY_LICENSES.md") -Force

Write-Host ("==> {0} ({1:N1} MB)" -f $dest, ((Get-Item $dest).Length / 1MB)) -ForegroundColor Green
