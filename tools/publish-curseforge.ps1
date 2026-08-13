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

    Server deploy: after publishing, the shard matching -DeployGameVersion is pushed to the
    live Craftics backend over ssh and the container is restarted. Configure the target once:

        $env:CRAFTICS_SSH_TARGET = "chris@107.173.171.127"

    Leave it unset (or pass -SkipDeploy) and the deploy step is skipped with a notice.
    Use -DeployOnly to push a locally built jar without touching CurseForge or Modrinth.

.NOTES
    Release notes are read from CHANGELOG.md - the section for -ModVersion, up to the next
    version heading. Keep that heading exactly equal to the version you are publishing.

    IMPORTANT - why the deploy target is /mods and not /data/mods:
    The craftics container runs MODPACK_PLATFORM=AUTO_CURSEFORGE with CF_FORCE_SYNCHRONIZE=true.
    That makes the itzg installer authoritative over /data/mods: on every start it reconciles
    that directory against the pack manifest and deletes anything not in it. A jar copied
    straight into data/craftics/mods/ therefore survives only until the next restart.
    itzg's /mods directory is copied INTO /data/mods after the pack sync on every boot, so a
    jar staged there is reapplied instead of pruned. The compose service needs the mount:

        volumes:
          - ./data/craftics:/data
          - ./downloads:/downloads
          - ./extra-mods/craftics:/mods        # <- required for -Deploy to stick
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

    # --- Live server deploy ---------------------------------------------------------------
    # ssh destination for the VPS running the Craftics backend, e.g. "chris@107.173.171.127".
    # Defaults to the CRAFTICS_SSH_TARGET environment variable (or .env). Unset = deploy skipped.
    [string] $SshTarget = $env:CRAFTICS_SSH_TARGET,

    # Which shard actually runs on the server. Only this one gets deployed.
    [string] $DeployGameVersion = "1.21.1",

    # Compose project directory on the VPS.
    [string] $RemoteRoot = "/opt/mcnet",

    # Staging dir mounted into the container at /mods. See the .NOTES block above for why
    # this is NOT data/craftics/mods.
    [string] $RemoteModsDir = "/opt/mcnet/extra-mods/craftics",

    # Compose service name to restart once the jar is in place.
    [string] $RemoteService = "craftics",

    # Skip the deploy step even when SshTarget is configured.
    [switch] $SkipDeploy,

    # Deploy only: push the built jar to the server, no CurseForge, no Modrinth.
    [switch] $DeployOnly,

    # Build the request and report what WOULD be sent, without uploading.
    [switch] $DryRun
)

# -DeployOnly is shorthand for "skip both publish platforms".
if ($DeployOnly) {
    $SkipCurseForge = $true
    $SkipModrinth   = $true
}

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
    if (-not $SshTarget)       { $SshTarget = $env:CRAFTICS_SSH_TARGET }
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
        # Version headings are a bare "0.2.10" on their own line. Two to FOUR components: a
        # hotfix on top of a release gets a fourth ("0.3.6.1"), and with only three allowed here
        # such a heading was not recognised as a heading at all, so its section could never be
        # found and publishing that version died on "No '0.3.6.1' section found in CHANGELOG.md".
        $isHeading = $line -match '^\d+\.\d+(\.\d+){0,2}\s*$'
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

    $script:CurseForgeUploads++
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

    $script:ModrinthUploads++
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

# --- Deploy one shard to the live server ------------------------------------------------------
<#
    Push the built jar to the Craftics backend and restart the container.

    Ordering matters and is not arbitrary:

      1. prune old craftics-*.jar on the REMOTE first. Doing it after the copy would delete
         the jar we just uploaded, since the new one matches the same glob.
      2. scp the new jar in.
      3. restart ONLY the craftics service. `docker compose restart craftics` leaves velocity,
         hub, mariadb and redis untouched, so nobody outside craftics is disconnected.

    Uses scp/ssh from OpenSSH, which ships with Windows 10 1809+ and Windows 11. Key auth is
    assumed - the server has password auth disabled, so an agent or ~/.ssh/id_* must be present.
#>
function Publish-ToServer {
    param([string] $GameVersion)

    $jar = Join-Path $RepoRoot "versions/$GameVersion/build/libs/craftics-$ModVersion+$GameVersion.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing jar: $jar`nBuild it first:  ./gradlew :$GameVersion`:build"
    }

    $jarName = Split-Path -Leaf $jar
    $sizeMb  = [math]::Round((Get-Item $jar).Length / 1MB, 1)

    Write-Host ""
    Write-Host "  Craftics v$ModVersion-$GameVersion -> $SshTarget" -ForegroundColor Cyan
    Write-Host "    jar    : $jarName (${sizeMb} MB)"
    Write-Host "    dest   : ${RemoteModsDir}/"
    Write-Host "    restart: docker compose restart $RemoteService"

    if ($DryRun) {
        Write-Host "    DRY RUN - not deployed" -ForegroundColor Yellow
        return
    }

    # --- 1. prepare the remote staging dir and clear the previous build -----------------------
    # `set -e` so a failure here aborts before we upload on top of a broken state.
    $prep = "set -e; mkdir -p '$RemoteModsDir'; rm -f '$RemoteModsDir'/craftics-*.jar; echo prepared"
    Write-Host "    preparing remote..." -NoNewline
    $out = & ssh $SshTarget $prep 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Remote prepare failed (exit $LASTEXITCODE):`n$out"
    }
    Write-Host "`r    remote prepared            " -ForegroundColor DarkGray

    # --- 2. upload ----------------------------------------------------------------------------
    Write-Host "    uploading..." -NoNewline
    & scp -q "$jar" "${SshTarget}:${RemoteModsDir}/"
    if ($LASTEXITCODE -ne 0) {
        throw "scp failed (exit $LASTEXITCODE). Is the ssh key loaded and $RemoteModsDir writable?"
    }
    Write-Host "`r    uploaded                   " -ForegroundColor DarkGray

    # --- 3. verify the bytes actually landed --------------------------------------------------
    # A truncated scp is silent. Compare SHA-256 rather than trusting the exit code.
    $localHash = (Get-FileHash -Algorithm SHA256 -Path $jar).Hash.ToLower()
    $remoteHash = (& ssh $SshTarget "sha256sum '$RemoteModsDir/$jarName' | cut -d' ' -f1" 2>&1).Trim()
    if ($remoteHash -ne $localHash) {
        throw "Hash mismatch after upload.`n  local : $localHash`n  remote: $remoteHash"
    }
    Write-Host "    verified sha256 $($localHash.Substring(0,16))..." -ForegroundColor DarkGray

    # --- 4. restart just this backend ---------------------------------------------------------
    Write-Host "    restarting $RemoteService..." -NoNewline
    $restart = "cd '$RemoteRoot' && docker compose restart $RemoteService"
    $out = & ssh $SshTarget $restart 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Remote restart failed (exit $LASTEXITCODE):`n$out"
    }
    Write-Host "`r    restarted                  " -ForegroundColor Green

    # --- 5. confirm the jar survived the pack sync --------------------------------------------
    # This is the whole reason for the /mods mount. If AUTO_CURSEFORGE pruned it, it shows here
    # rather than as a mystery "my changes aren't live" an hour from now.
    Write-Host "    waiting 60s for the pack sync to finish..."
    Start-Sleep -Seconds 60
    $check = "ls -1 '$RemoteRoot/data/$RemoteService/mods/' 2>/dev/null | grep -c '^craftics-' || true"
    $count = (& ssh $SshTarget $check 2>&1).Trim()
    if ($count -eq "0") {
        Write-Host "    WARNING: no craftics-*.jar in data/$RemoteService/mods after restart." -ForegroundColor Red
        Write-Host "             The AUTO_CURSEFORGE sync most likely pruned it. Check that the" -ForegroundColor Red
        Write-Host "             compose service mounts ./extra-mods/craftics:/mods." -ForegroundColor Red
    } else {
        Write-Host "    confirmed live in data/$RemoteService/mods ($count jar)" -ForegroundColor Green
    }

    $script:ServerDeploys++
}

# --- Run --------------------------------------------------------------------------------------
Write-Host "Craftics publish  |  version $ModVersion  |  $ReleaseType  |  $($GameVersions.Count) shard(s)" -ForegroundColor White

# What each platform actually DID, not what it was asked to do. The closing "Publish complete."
# used to print whenever -DryRun was absent, so a run that skipped CurseForge by flag and skipped
# Modrinth for missing config announced success, in green, having uploaded nothing - and exited 0,
# so no caller could tell either. These track real uploads so the summary can only claim what
# happened.
$script:CurseForgeUploads = 0
$script:ModrinthUploads = 0
$script:ServerDeploys = 0
$script:CurseForgeState = "skipped (-SkipCurseForge)"
$script:ModrinthState = "skipped (-SkipModrinth)"
$script:DeployState = "skipped (-SkipDeploy)"

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
    $script:CurseForgeState = if ($DryRun) { "dry run" } else { "uploaded $script:CurseForgeUploads file(s)" }
}

if (-not $SkipModrinth) {
    if (-not $ModrinthProject) {
        $script:ModrinthState = "skipped (MODRINTH_PROJECT not set)"
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
        $script:ModrinthState = if ($DryRun) { "dry run" } else { "uploaded $script:ModrinthUploads file(s)" }
    }
}

# --- Deploy to the live server ------------------------------------------------------------
# Runs last on purpose: if a publish step throws, the running server is left alone rather than
# being restarted onto a build that never made it to the download page.
if (-not $SkipDeploy) {
    if (-not $SshTarget) {
        $script:DeployState = "skipped (CRAFTICS_SSH_TARGET not set)"
        Write-Host ""
        Write-Host "Server deploy skipped: set `$env:CRAFTICS_SSH_TARGET (or -SshTarget) to e.g. chris@107.173.171.127." -ForegroundColor Yellow
    } elseif ($GameVersions -notcontains $DeployGameVersion -and -not $DeployOnly) {
        # Deploying a shard that was never built or published this run would push a stale jar.
        $script:DeployState = "skipped ($DeployGameVersion not in -GameVersions)"
        Write-Host ""
        Write-Host "Server deploy skipped: $DeployGameVersion is not in the published set ($($GameVersions -join ', '))." -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "Server deploy  |  $SshTarget  |  shard $DeployGameVersion" -ForegroundColor White
        Publish-ToServer -GameVersion $DeployGameVersion
        $script:DeployState = if ($DryRun) { "dry run" } else { "deployed $script:ServerDeploys jar(s)" }
    }
}

Write-Host ""
Write-Host "  CurseForge : $script:CurseForgeState"
Write-Host "  Modrinth   : $script:ModrinthState"
Write-Host "  Server     : $script:DeployState"
Write-Host ""

if ($DryRun) {
    Write-Host "Dry run complete. Nothing was uploaded or deployed." -ForegroundColor Yellow
    return
}

# -DeployOnly deliberately publishes nothing, so judge it on the deploy alone.
if ($DeployOnly) {
    if ($script:ServerDeploys -eq 0) {
        Write-Host "NOTHING WAS DEPLOYED - the jar never reached the server." -ForegroundColor Red
        exit 1
    }
    Write-Host "Deploy complete. $script:ServerDeploys jar(s) live on $SshTarget." -ForegroundColor Green
    return
}

$uploaded = $script:CurseForgeUploads + $script:ModrinthUploads
if ($uploaded -eq 0) {
    # Loud, and a non-zero exit: a publish that published nothing is a failed publish, however
    # many of its steps were skipped on purpose. Silently succeeding here is how a release gets
    # marked "shipped" while the download page still serves the previous version.
    Write-Host "NOTHING WAS PUBLISHED - no file reached any platform." -ForegroundColor Red
    Write-Host "Drop -SkipCurseForge to publish to CurseForge, and set MODRINTH_PROJECT for Modrinth." -ForegroundColor Red
    exit 1
}

$deployNote = if ($script:ServerDeploys -gt 0) { " Server updated." } else { "" }
Write-Host "Publish complete. $uploaded file(s) uploaded.$deployNote" -ForegroundColor Green
