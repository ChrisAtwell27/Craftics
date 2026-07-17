<#
.SYNOPSIS
    Upload every Craftics shard to CurseForge.

.DESCRIPTION
    Craftics builds one jar per Minecraft version (Stonecutter shards). This uploads each
    one as its own CurseForge file, tagged with the single game version it was built for.

    Run this from your own machine - it needs network access to minecraft.curseforge.com.

    The API token is NOT stored in this file. Pass it in, or set CURSEFORGE_TOKEN:

        $env:CURSEFORGE_TOKEN = "<your token>"
        .\tools\publish-curseforge.ps1

    Dry run first (builds the request, uploads nothing):

        .\tools\publish-curseforge.ps1 -DryRun

.NOTES
    Release notes are read from CHANGELOG.md - the section for -ModVersion, up to the next
    version heading. Keep that heading exactly equal to the version you are publishing.
#>
[CmdletBinding()]
param(
    # Mod version to publish. Must match mod_version in gradle.properties AND a CHANGELOG heading.
    [string] $ModVersion = "0.3.0",

    # CurseForge numeric project id (About Project -> Project ID on the project page).
    [int] $ProjectId = 1494583,

    # alpha | beta | release
    [ValidateSet("alpha", "beta", "release")]
    [string] $ReleaseType = "beta",

    # Minecraft versions to publish. One jar per entry.
    [string[]] $GameVersions = @("1.21.1", "1.21.3", "1.21.4", "1.21.5"),

    # API token. Defaults to the CURSEFORGE_TOKEN environment variable.
    [string] $Token = $env:CURSEFORGE_TOKEN,

    # Build the request and report what WOULD be sent, without uploading.
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ApiBase = "https://minecraft.curseforge.com"

if (-not $Token -and -not $DryRun) {
    throw "No API token. Set `$env:CURSEFORGE_TOKEN or pass -Token. (Do not hardcode it in this file.)"
}

# --- Guard: the jar's internal version must match what we claim to be publishing. -------------
# A mismatch here is the bug that silently ships "0.2.10" to CurseForge while every client
# reports 0.2.9, breaking update checks for everyone. Cheap to check, expensive to miss.
$declared = (Select-String -Path (Join-Path $RepoRoot "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
if ($declared -ne $ModVersion) {
    throw "gradle.properties says mod_version=$declared but you are publishing $ModVersion. Bump one of them."
}

# --- Release notes: pull this version's section out of CHANGELOG.md ---------------------------
function Get-ChangelogSection {
    param([string] $Version)

    $lines = Get-Content (Join-Path $RepoRoot "CHANGELOG.md")
    $out = New-Object System.Collections.Generic.List[string]
    $inSection = $false

    foreach ($line in $lines) {
        # Version headings are a bare "0.2.10" on their own line.
        $isHeading = $line -match '^\d+\.\d+(\.\d+)?\s*$'
        if ($isHeading) {
            if ($inSection) { break }                       # next version - we're done
            if ($line.Trim() -eq $Version) { $inSection = $true; continue }
        }
        if ($inSection) { $out.Add($line) }
    }

    if ($out.Count -eq 0) {
        throw "No '$Version' section found in CHANGELOG.md. The heading must be exactly '$Version' on its own line."
    }
    return ($out -join "`n").Trim()
}

$changelog = Get-ChangelogSection -Version $ModVersion
Write-Host "Release notes: $($changelog.Split("`n").Count) lines from CHANGELOG.md" -ForegroundColor DarkGray

# --- Resolve CurseForge's internal game-version ids -------------------------------------------
# The upload API does not take "1.21.1" - it takes an integer id from its own table, which
# differs per game version. Look them up rather than hardcoding ids that rot.
function Get-GameVersionIds {
    param([string[]] $Names)

    $all = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/game/versions" `
        -Headers @{ "X-Api-Token" = $Token }

    $ids = @{}
    foreach ($name in $Names) {
        $match = $all | Where-Object { $_.name -eq $name } | Select-Object -First 1
        if (-not $match) {
            throw "CurseForge does not know a game version called '$name'. Check the exact spelling."
        }
        $ids[$name] = $match.id
    }
    return $ids
}

# --- Upload one shard -------------------------------------------------------------------------
function Publish-Shard {
    param(
        [string] $GameVersion,
        [int]    $GameVersionId
    )

    $jar = Join-Path $RepoRoot "versions/$GameVersion/build/libs/craftics-$ModVersion+$GameVersion.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing jar: $jar`nBuild it first:  ./gradlew :$GameVersion`:build"
    }

    # Naming convention: Craftics v0.2.10-1.21.1
    $displayName = "Craftics v$ModVersion-$GameVersion"

    $metadata = @{
        changelog     = $changelog
        changelogType = "markdown"
        displayName   = $displayName
        gameVersions  = @($GameVersionId)
        releaseType   = $ReleaseType
    } | ConvertTo-Json -Depth 5 -Compress

    $sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Host ""
    Write-Host "  $displayName" -ForegroundColor Cyan
    Write-Host "    jar    : $(Split-Path -Leaf $jar) (${sizeMb} MB)"
    Write-Host "    type   : $ReleaseType"
    Write-Host "    mcver  : $GameVersion (id $GameVersionId)"

    if ($DryRun) {
        Write-Host "    DRY RUN - not uploaded" -ForegroundColor Yellow
        return
    }

    Write-Host "    uploading..." -NoNewline

    $resp = Send-MultipartUpload `
        -Uri "$ApiBase/api/projects/$ProjectId/upload-file" `
        -Token $Token `
        -Metadata $metadata `
        -FilePath $jar

    Write-Host "`r    uploaded, file id $($resp.id)          " -ForegroundColor Green
}

<#
    POST a multipart/form-data upload (metadata field + file field).

    Written against raw HttpWebRequest rather than Invoke-RestMethod -Form, because -Form is
    PowerShell 7+ only and this repo is built on Windows PowerShell 5.1.

    The jar is ~190 MB, so it is streamed from disk in chunks instead of being read into memory.
    Critically, the file part is written as RAW BYTES: round-tripping a jar through a .NET string
    would mangle every byte outside the encoding's range and silently upload a corrupt archive.
#>
function Send-MultipartUpload {
    param(
        [Parameter(Mandatory)] [string] $Uri,
        [Parameter(Mandatory)] [string] $Token,
        [Parameter(Mandatory)] [string] $Metadata,
        [Parameter(Mandatory)] [string] $FilePath
    )

    Add-Type -AssemblyName "System.Net.Http" -ErrorAction SilentlyContinue

    $boundary = "----CrafticsBoundary{0:N}" -f ([guid]::NewGuid())
    $fileName = Split-Path -Leaf $FilePath
    $enc = [System.Text.Encoding]::UTF8
    $nl = "`r`n"

    # The two form parts, as byte blocks around the raw file body.
    $head = New-Object System.Text.StringBuilder
    [void]$head.Append("--$boundary$nl")
    [void]$head.Append("Content-Disposition: form-data; name=`"metadata`"$nl$nl")
    [void]$head.Append("$Metadata$nl")
    [void]$head.Append("--$boundary$nl")
    [void]$head.Append("Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"$nl")
    [void]$head.Append("Content-Type: application/java-archive$nl$nl")

    $headBytes = $enc.GetBytes($head.ToString())
    $tailBytes = $enc.GetBytes("$nl--$boundary--$nl")
    $fileInfo  = Get-Item $FilePath

    $req = [System.Net.HttpWebRequest]::Create($Uri)
    $req.Method = "POST"
    $req.ContentType = "multipart/form-data; boundary=$boundary"
    $req.Headers.Add("X-Api-Token", $Token)
    $req.AllowWriteStreamBuffering = $false          # don't buffer 190 MB in RAM
    $req.SendChunked = $false
    $req.KeepAlive = $true
    $req.Timeout = 20 * 60 * 1000                    # 20 min: these are big files
    $req.ReadWriteTimeout = 20 * 60 * 1000
    $req.ContentLength = $headBytes.Length + $fileInfo.Length + $tailBytes.Length

    $reqStream = $req.GetRequestStream()
    try {
        $reqStream.Write($headBytes, 0, $headBytes.Length)

        $fs = [System.IO.File]::OpenRead($FilePath)
        try {
            $buffer = New-Object byte[] (1MB)
            $sent = 0L
            while (($read = $fs.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $reqStream.Write($buffer, 0, $read)
                $sent += $read
                $pct = [math]::Floor(($sent / $fileInfo.Length) * 100)
                Write-Host "`r    uploading... $pct%" -NoNewline
            }
        } finally {
            $fs.Dispose()
        }

        $reqStream.Write($tailBytes, 0, $tailBytes.Length)
    } finally {
        $reqStream.Dispose()
    }

    try {
        $resp = $req.GetResponse()
    } catch [System.Net.WebException] {
        # CurseForge puts the actual reason (bad game version, duplicate file, ...) in the
        # response body, which PowerShell hides behind a bare "(400) Bad Request". Surface it.
        $body = ""
        if ($_.Exception.Response) {
            $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $sr.ReadToEnd()
            $sr.Dispose()
        }
        throw "Upload failed: $($_.Exception.Message)`n$body"
    }

    $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $json = $sr.ReadToEnd()
    $sr.Dispose()
    $resp.Dispose()

    return ($json | ConvertFrom-Json)
}

# --- Run --------------------------------------------------------------------------------------
Write-Host "Craftics -> CurseForge project $ProjectId" -ForegroundColor White
Write-Host "  version $ModVersion  |  $ReleaseType  |  $($GameVersions.Count) shard(s)" -ForegroundColor White

$ids = if ($DryRun -and -not $Token) {
    # Let a dry run work with no token at all, so the changelog/jar checks can be tested offline.
    $fake = @{}; foreach ($v in $GameVersions) { $fake[$v] = 0 }; $fake
} else {
    Get-GameVersionIds -Names $GameVersions
}

foreach ($v in $GameVersions) {
    Publish-Shard -GameVersion $v -GameVersionId $ids[$v]
}

Write-Host ""
if ($DryRun) {
    Write-Host "Dry run complete. Nothing was uploaded." -ForegroundColor Yellow
} else {
    Write-Host "All $($GameVersions.Count) shard(s) published." -ForegroundColor Green
}
