<#
.SYNOPSIS
    Upload every Craftics shard to CurseForge and Modrinth.

.DESCRIPTION
    Craftics builds one jar per Minecraft version (Stonecutter shards). This uploads each
    one as its own file per platform, tagged with the single game version it was built for.

    Run this from your own machine - it needs network access to minecraft.curseforge.com
    and api.modrinth.com.

    Tokens are NOT stored in this file. Set them in the environment:

        $env:CURSEFORGE_TOKEN  = "<curseforge api token>"
        $env:MODRINTH_TOKEN    = "<modrinth PERSONAL ACCESS TOKEN>"
        $env:MODRINTH_PROJECT  = "<modrinth project slug or id>"
        .\tools\publish-curseforge.ps1

    Modrinth note: the token must be a Personal Access Token (modrinth.com -> Settings ->
    Personal access tokens) with the "Create versions" scope. OAuth client id/secret pairs
    do NOT work against the upload API.

    Dry run first (builds the requests, uploads nothing):

        .\tools\publish-curseforge.ps1 -DryRun

    Skip a platform with -SkipCurseForge / -SkipModrinth.

.NOTES
    Release notes are read from CHANGELOG.md - the section for -ModVersion, up to the next
    version heading. Keep that heading exactly equal to the version you are publishing.
#>
[CmdletBinding()]
param(
    # Mod version to publish. Must match mod_version in gradle.properties AND a CHANGELOG heading.
    [string] $ModVersion = "0.3.1",

    # CurseForge numeric project id (About Project -> Project ID on the project page).
    [int] $ProjectId = 1494583,

    # alpha | beta | release
    [ValidateSet("alpha", "beta", "release")]
    [string] $ReleaseType = "beta",

    # Minecraft versions to publish. One jar per entry.
    [string[]] $GameVersions = @("1.21.1", "1.21.3", "1.21.4", "1.21.5"),

    # CurseForge API token. Defaults to the CURSEFORGE_TOKEN environment variable.
    [string] $Token = $env:CURSEFORGE_TOKEN,

    # Modrinth project slug or id (the tail of the project page URL).
    [string] $ModrinthProject = $env:MODRINTH_PROJECT,

    # Modrinth Personal Access Token with the "Create versions" scope.
    # NOT an OAuth client id/secret - those don't authenticate uploads.
    [string] $ModrinthToken = $env:MODRINTH_TOKEN,

    # Platform toggles: default publishes to both (Modrinth only when configured).
    [switch] $SkipCurseForge,
    [switch] $SkipModrinth,

    # Build the request and report what WOULD be sent, without uploading.
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ApiBase = "https://minecraft.curseforge.com"
$ModrinthApi = "https://api.modrinth.com/v2"
$UserAgent = "crackedgames/craftics publish script"

# --- Load .env (gitignored) so tokens don't have to be exported by hand each session. ---------
# Params default to $env:* values, which are bound BEFORE this body runs - so after loading .env
# into the process environment, back-fill any param the caller left empty from the fresh value.
$envFile = Join-Path $RepoRoot ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) { continue }
        $key = $trimmed.Substring(0, $eq).Trim()
        $val = $trimmed.Substring($eq + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
    if (-not $Token)           { $Token = $env:CURSEFORGE_TOKEN }
    if (-not $ModrinthProject) { $ModrinthProject = $env:MODRINTH_PROJECT }
    if (-not $ModrinthToken)   { $ModrinthToken = $env:MODRINTH_TOKEN }
}

if (-not $SkipCurseForge -and -not $Token -and -not $DryRun) {
    throw "No CurseForge API token. Set `$env:CURSEFORGE_TOKEN or pass -Token, or use -SkipCurseForge. (Do not hardcode it in this file.)"
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

    # Strip a UTF-8 BOM off the first line if Get-Content left one on (PS 5.1 does).
    if ($lines.Count -gt 0) { $lines[0] = $lines[0] -replace '^﻿', '' }

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

# CurseForge takes the raw text verbatim (changelogType "text" below, so every newline is kept).
# Modrinth's changelog field is ALWAYS markdown, where a lone newline collapses into a space and
# glues adjacent lines together. Append two trailing spaces to each line - the markdown hard-break -
# so every source line stays on its own line there too. Blank lines are left blank (a trailing-space
# blank line would render as a stray break).
$changelogMd = ($changelog -split "`n" | ForEach-Object {
    if ($_.Trim() -eq "") { "" } else { $_ + "  " }
}) -join "`n"

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

    # CurseForge requires at least one id from EACH grouping the project has enabled: a Minecraft
    # version, a modloader, AND an environment (Client/Server). Sending only the MC version, or even
    # MC + loader, fails with errorCode 1021 because the Environment group is still empty. All three
    # live in the same version table, so resolve them here and hand them back under reserved keys the
    # caller adds to every upload.
    #
    # Ids confirmed via tools/cf-probe-versions.ps1: Fabric=7499 (Modloader group 68441),
    # Client=9638 / Server=9639 (Environment group 75208). Resolved by name rather than hardcoded so
    # they survive CurseForge renumbering.
    $fabric = $all | Where-Object { $_.name -eq "Fabric" } | Select-Object -First 1
    if (-not $fabric) {
        throw "CurseForge did not return a 'Fabric' modloader version. The upload needs a loader id."
    }
    $ids["__loader__"] = $fabric.id

    # This mod runs on both sides, so tag both. Sending at least one Environment id is what actually
    # clears error 1021.
    $client = $all | Where-Object { $_.name -eq "Client" } | Select-Object -First 1
    $server = $all | Where-Object { $_.name -eq "Server" } | Select-Object -First 1
    if (-not $client -and -not $server) {
        throw "CurseForge returned no 'Client' or 'Server' environment version. Error 1021 needs one."
    }
    $envIds = @()
    if ($client) { $envIds += $client.id }
    if ($server) { $envIds += $server.id }
    $ids["__env__"] = $envIds

    return $ids
}

# --- Upload one shard -------------------------------------------------------------------------
function Publish-Shard {
    param(
        [string] $GameVersion,
        [int]    $GameVersionId,
        [int]    $LoaderId,
        [int[]]  $EnvIds
    )

    $jar = Join-Path $RepoRoot "versions/$GameVersion/build/libs/craftics-$ModVersion+$GameVersion.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing jar: $jar`nBuild it first:  ./gradlew :$GameVersion`:build"
    }

    # Naming convention: Craftics v0.2.10-1.21.1
    $displayName = "Craftics v$ModVersion-$GameVersion"

    $metadata = @{
        changelog     = $changelog
        # "text" keeps every newline literally - "markdown" collapsed lone newlines into spaces,
        # gluing the changelog into run-on paragraphs. We only need line breaks, not markdown.
        changelogType = "text"
        displayName   = $displayName
        gameVersions  = @($GameVersionId, $LoaderId) + $EnvIds
        releaseType   = $ReleaseType
    } | ConvertTo-Json -Depth 5 -Compress

    $sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Host ""
    Write-Host "  $displayName" -ForegroundColor Cyan
    Write-Host "    jar    : $(Split-Path -Leaf $jar) (${sizeMb} MB)"
    Write-Host "    type   : $ReleaseType"
    Write-Host "    mcver  : $GameVersion (id $GameVersionId)"
    Write-Host "    loader : Fabric (id $LoaderId)"
    Write-Host "    env    : Client/Server (ids $($EnvIds -join ', '))"

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

# --- Upload one shard to Modrinth -------------------------------------------------------------
# One Modrinth version per shard. version_number must be unique per project; the jar's own
# "0.3.1+1.21.1" convention satisfies that and matches what clients see in-game.
function Publish-ShardModrinth {
    param([string] $GameVersion)

    $jar = Join-Path $RepoRoot "versions/$GameVersion/build/libs/craftics-$ModVersion+$GameVersion.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing jar: $jar`nBuild it first:  ./gradlew :$GameVersion`:build"
    }

    $displayName = "Craftics v$ModVersion-$GameVersion"

    $data = @{
        name           = $displayName
        version_number = "$ModVersion+$GameVersion"
        changelog      = $changelogMd
        # Fabric API is required at runtime; P7dR8mSH is its Modrinth project id.
        dependencies   = @(@{ project_id = "P7dR8mSH"; dependency_type = "required" })
        game_versions  = @($GameVersion)
        version_type   = $ReleaseType
        loaders        = @("fabric")
        featured       = $false
        project_id     = $ModrinthProject
        file_parts     = @("file")
        primary_file   = "file"
    } | ConvertTo-Json -Depth 5 -Compress

    $sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Write-Host ""
    Write-Host "  $displayName -> Modrinth" -ForegroundColor Cyan
    Write-Host "    jar    : $(Split-Path -Leaf $jar) (${sizeMb} MB)"
    Write-Host "    type   : $ReleaseType"
    Write-Host "    mcver  : $GameVersion"
    Write-Host "    loader : fabric"

    if ($DryRun) {
        Write-Host "    DRY RUN - not uploaded" -ForegroundColor Yellow
        return
    }

    Write-Host "    uploading..." -NoNewline

    $resp = Send-MultipartUpload `
        -Uri "$ModrinthApi/version" `
        -Token $ModrinthToken `
        -Metadata $data `
        -FilePath $jar `
        -AuthHeaderName "Authorization" `
        -JsonPartName "data"

    Write-Host "`r    uploaded, version id $($resp.id)          " -ForegroundColor Green
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
        [Parameter(Mandatory)] [string] $FilePath,
        # CurseForge: X-Api-Token + "metadata" part. Modrinth: Authorization + "data" part.
        [string] $AuthHeaderName = "X-Api-Token",
        [string] $JsonPartName = "metadata"
    )

    Add-Type -AssemblyName "System.Net.Http" -ErrorAction SilentlyContinue

    $boundary = "----CrafticsBoundary{0:N}" -f ([guid]::NewGuid())
    $fileName = Split-Path -Leaf $FilePath
    $enc = [System.Text.Encoding]::UTF8
    $nl = "`r`n"

    # The two form parts, as byte blocks around the raw file body.
    $head = New-Object System.Text.StringBuilder
    [void]$head.Append("--$boundary$nl")
    [void]$head.Append("Content-Disposition: form-data; name=`"$JsonPartName`"$nl$nl")
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
    $req.UserAgent = $UserAgent
    $req.Headers.Add($AuthHeaderName, $Token)
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
Write-Host "Craftics publish  |  version $ModVersion  |  $ReleaseType  |  $($GameVersions.Count) shard(s)" -ForegroundColor White

if (-not $SkipCurseForge) {
    Write-Host ""
    Write-Host "CurseForge project $ProjectId" -ForegroundColor White

    $ids = if ($DryRun -and -not $Token) {
        # Let a dry run work with no token at all, so the changelog/jar checks can be tested offline.
        $fake = @{}; foreach ($v in $GameVersions) { $fake[$v] = 0 }; $fake["__loader__"] = 0; $fake["__env__"] = @(0, 0); $fake
    } else {
        Get-GameVersionIds -Names $GameVersions
    }

    foreach ($v in $GameVersions) {
        Publish-Shard -GameVersion $v -GameVersionId $ids[$v] -LoaderId $ids["__loader__"] -EnvIds $ids["__env__"]
    }
}

if (-not $SkipModrinth) {
    if (-not $ModrinthProject) {
        Write-Host ""
        Write-Host "Modrinth skipped: set `$env:MODRINTH_PROJECT (or -ModrinthProject) to the project slug/id." -ForegroundColor Yellow
    } elseif (-not $ModrinthToken -and -not $DryRun) {
        throw "Modrinth project '$ModrinthProject' is set but MODRINTH_TOKEN is not. Create a Personal Access Token (Settings -> Personal access tokens, 'Create versions' scope) - an OAuth client id/secret will not work."
    } else {
        Write-Host ""
        Write-Host "Modrinth project $ModrinthProject" -ForegroundColor White
        foreach ($v in $GameVersions) {
            Publish-ShardModrinth -GameVersion $v
        }
    }
}

Write-Host ""
if ($DryRun) {
    Write-Host "Dry run complete. Nothing was uploaded." -ForegroundColor Yellow
} else {
    Write-Host "Publish complete." -ForegroundColor Green
}
