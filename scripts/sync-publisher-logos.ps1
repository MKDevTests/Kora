<#
.SYNOPSIS
  Refills the bundled publisher logo pack from ComicRackCE, then shrinks it.

.DESCRIPTION
  Two traps this script exists to avoid, both of which already cost us once:

  1. Filenames. The upstream pack names a file after every alias it covers,
     separated by '#', and many carry accents or '&':
         "Glenat#Glénat#Glenat Italia#Glénat Spain#Éditions Glénat.png"
     The original import fetched these by download_url and got 41 zero-byte
     files -- every accented publisher, Glénat and Ankama included, silently
     rendered nothing. This script fetches by Git blob SHA instead: the URL
     carries no filename, so nothing can be mis-encoded.

  2. TLS. Avast's HTTPS scanning re-signs certificates with a CA that Python's
     ssl module rejects ("Basic Constraints of CA cert not marked critical"),
     so a Python fetcher fails here while PowerShell, which trusts the Windows
     certificate store, succeeds. That is why the download half is PowerShell
     and only the image resizing is Python.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/sync-publisher-logos.ps1
#>
param(
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $repoRoot "komelia-ui/src/commonMain/composeResources/files/publishers"
$api = "https://api.github.com/repos/maforget/ComicRackCE/contents/ComicRack/Output/Resources/Icons/Publishers"
$headers = @{ 'User-Agent' = 'kora-publisher-logo-sync' }

function Get-NormalizedName([string]$name) {
    ($name.ToLowerInvariant() -replace '[^a-z0-9]+', '_').Trim('_')
}

Write-Host "Listing upstream pack..."
$entries = Invoke-RestMethod -Uri $api -Headers $headers

# One entry can cover several publisher spellings; each becomes its own file.
$wanted = @{}
foreach ($e in $entries) {
    $stem = $e.name -replace '\.png$', ''
    foreach ($alias in $stem.Split('#')) {
        $key = Get-NormalizedName $alias
        if ($key -and -not $wanted.ContainsKey($key)) { $wanted[$key] = $e.sha }
    }
}

$have = @{}
Get-ChildItem -Path $dir -Filter *.png | ForEach-Object {
    # A zero-byte file is a failed download, not a logo: refetch it.
    if ($_.Length -gt 0) { $have[[IO.Path]::GetFileNameWithoutExtension($_.Name)] = $true }
}

$missing = $wanted.Keys | Where-Object { -not $have.ContainsKey($_) } | Sort-Object
Write-Host "upstream: $($entries.Count) files -> $($wanted.Count) aliases; local: $($have.Count); missing: $($missing.Count)"

if ($missing.Count -eq 0) { Write-Host "Pack is complete."; }
elseif ($WhatIf) { Write-Host "WhatIf: would fetch $($missing.Count) logo(s)."; $missing -join ' ' }
else {
    # Aliases share a blob; fetch each blob once.
    $blobs = @{}
    $written = 0
    foreach ($key in $missing) {
        $sha = $wanted[$key]
        if (-not $blobs.ContainsKey($sha)) {
            $blob = Invoke-RestMethod -Headers $headers `
                -Uri "https://api.github.com/repos/maforget/ComicRackCE/git/blobs/$sha"
            $blobs[$sha] = [Convert]::FromBase64String($blob.content)
        }
        [IO.File]::WriteAllBytes((Join-Path $dir "$key.png"), $blobs[$sha])
        $written++
    }
    Write-Host "Fetched $written logo(s) from $($blobs.Count) blob(s)."
}

if (-not $WhatIf) {
    Write-Host "Resizing to the size the UI actually draws..."
    python (Join-Path $PSScriptRoot "optimize-publisher-logos.py") --apply
}
