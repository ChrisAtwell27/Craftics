<#
.SYNOPSIS
    Push the most recent local Craftics build to the live server. Nothing else.

.DESCRIPTION
    The fast iteration loop: build, deploy, restart, confirm. No CurseForge, no Modrinth,
    no CHANGELOG, no version guards. Use tools/publish-curseforge.ps1 when you are actually
    shipping a release.

    It picks the NEWEST craftics-*+<GameVersion>.jar by write time rather than taking a
    version argument, so you never have to remember what you just built.

    Configure the target once:

        $env:CRAFTICS_SSH_TARGET = "chris@107.173.171.127"

    or put CRAFTICS_SSH_TARGET=... in the repo's .env (gitignored), which this reads.

    Typical use:

        .\tools\deploy-server.ps1                 # deploy whatever is already built
        .\tools\deploy-server.ps1 -Build          # gradle build first, then deploy
        .\tools\deploy-server.ps1 -DryRun         # show the plan, touch nothing
        .\tools\deploy-server.ps1 -NoRestart      # stage the jar, restart later yourself

.NOTES
    Why the jar goes to /mods and NOT data/craftics/mods:

    The craftics container runs MODPACK_PLATFORM=AUTO_CURSEFORGE with CF_FORCE_SYNCHRONIZE=true.
    That makes the itzg installer authoritative over /data/mods - on every start it reconciles
    that directory against the pack manifest and DELETES anything not in it. A jar copied
    straight into data/craftics/mods/ therefore survives only until the next restart.

    itzg copies the contents of /mods INTO /data/mods after the pack sync on every boot, so a
    jar staged there is reapplied instead of pruned. The compose service must mount it:

        volumes:
          - ./data/craftics:/data
          - ./downloads:/downloads
          - ./extra-mods/craftics:/mods        # <- required, or this script's jar vanishes

    One-time setup on the VPS:

        cd /opt/mcnet
        mkdir -p extra-mods/craftics
        chown -R 1000:1000 extra-mods
        docker compose up -d --force-recreate craftics
#>
[CmdletBinding()]
param(
    # Which shard the server runs. Only this one is deployed.
    [string] $GameVersion = "1.21.1",

    # ssh destination for the VPS. Defaults to CRAFTICS_SSH_TARGET (env or .env).
    [string] $SshTarget = $env:CRAFTICS_SSH_TARGET,

    # Compose project directory on the VPS.
    [string] $RemoteRoot = "/opt/mcnet",

    # Host dir mounted into the container at /mods. See .NOTES for why this is not data/*/mods.
    [string] $RemoteModsDir = "/opt/mcnet/extra-mods/craftics",

    # Compose service name.
    [string] $RemoteService = "craftics",

    # Explicit jar path. Overrides the newest-build search entirely.
    [string] $JarPath,

    # Run `./gradlew :<GameVersion>:build` before deploying.
    [switch] $Build,

    # Stage the jar but leave the container alone. Nothing goes live until you restart.
    [switch] $NoRestart,

    # Skip the 60s post-restart check that the jar survived the pack sync.
    [switch] $NoVerify,

    # Print the plan, change nothing local or remote.
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

# --- Load .env (gitignored) so the ssh target doesn't have to be exported each session --------
# Params bind BEFORE this body runs, so back-fill anything the caller left empty.
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
    if (-not $SshTarget) { $SshTarget = $env:CRAFTICS_SSH_TARGET }
}

if (-not $SshTarget) {
    throw "No ssh target. Set `$env:CRAFTICS_SSH_TARGET = 'chris@107.173.171.127' or pass -SshTarget."
}

Write-Host "Craftics deploy  |  shard $GameVersion  ->  $SshTarget" -ForegroundColor White

# --- Optional build ---------------------------------------------------------------------------
if ($Build) {
    $gradlew = Join-Path $RepoRoot "gradlew.bat"
    if (-not (Test-Path $gradlew)) { $gradlew = Join-Path $RepoRoot "gradlew" }
    Write-Host ""
    Write-Host "  building :${GameVersion}:build ..." -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host "    DRY RUN - not built" -ForegroundColor Yellow
    } else {
        Push-Location $RepoRoot
        try {
            & $gradlew ":${GameVersion}:build"
            if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }
        } finally {
            Pop-Location
        }
        Write-Host "    build ok" -ForegroundColor Green
    }
}

# --- Find the newest build ----------------------------------------------------------------------
# Newest by LastWriteTime, not by version string: after a rebuild the version often has not
# changed, and picking "highest version" would happily redeploy a stale jar from last week.
if ($JarPath) {
    if (-not (Test-Path $JarPath)) { throw "No such jar: $JarPath" }
    $jar = Get-Item $JarPath
} else {
    $libs = Join-Path $RepoRoot "versions/$GameVersion/build/libs"
    if (-not (Test-Path $libs)) {
        throw "No build output at $libs`nBuild it first:  .\tools\deploy-server.ps1 -Build"
    }
    # -sources.jar and -dev.jar are byproducts; shipping one of those to the server would
    # load a jar with no compiled classes and fail silently at runtime.
    $jar = Get-ChildItem -Path $libs -Filter "craftics-*.jar" |
           Where-Object { $_.Name -notmatch '-(sources|dev|javadoc|shadow)\.jar$' } |
           Sort-Object LastWriteTime -Descending |
           Select-Object -First 1
    if (-not $jar) {
        throw "No craftics-*.jar in $libs`nBuild it first:  .\tools\deploy-server.ps1 -Build"
    }
}

$jarName = $jar.Name
$sizeMb  = [math]::Round($jar.Length / 1MB, 1)
$age     = [math]::Round(((Get-Date) - $jar.LastWriteTime).TotalMinutes, 1)

Write-Host ""
Write-Host "  $jarName" -ForegroundColor Cyan
Write-Host "    size   : ${sizeMb} MB"
Write-Host "    built  : $($jar.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))  (${age} min ago)"
Write-Host "    dest   : ${RemoteModsDir}/"
Write-Host "    restart: $(if ($NoRestart) { 'skipped (-NoRestart)' } else { "docker compose restart $RemoteService" })"

# A jar that is hours old is usually a forgotten build, not the change you just made.
if ($age -gt 60) {
    Write-Host "    NOTE: that build is over an hour old. Did you mean to pass -Build?" -ForegroundColor Yellow
}

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry run complete. Nothing was uploaded." -ForegroundColor Yellow
    return
}
# --- ssh/scp helper -------------------------------------------------------------------------
<#
    Run a remote command and return its combined output as plain text.

    Why this exists rather than a bare `& ssh ... 2>&1`:

    `docker compose` writes its progress lines ("Container mcnet-craftics Restarting") to
    STDERR, not stdout. Under Windows PowerShell, redirecting a native command's stderr with
    2>&1 turns each line into an ErrorRecord, and with $ErrorActionPreference = "Stop" at the
    top of this script that ErrorRecord becomes a TERMINATING error. The result was a script
    that threw NativeCommandError on a restart that had in fact succeeded.

    So: drop to 'Continue' around the native call, flatten every record to a string, and judge
    success on $LASTEXITCODE - the only thing that actually reports it.
#>
function Invoke-Ssh {
    param(
        [Parameter(Mandatory)] [string] $Target,
        [Parameter(Mandatory)] [string] $Command,
        [string] $What = "remote command"
    )

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = & ssh $Target $Command 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }

    $text = ($raw | ForEach-Object { $_.ToString() }) -join "`n"
    if ($code -ne 0) {
        throw "$What failed (ssh exit $code):`n$text"
    }
    return $text
}

# --- 0. pre-flight: is /mods actually mounted? --------------------------------------------------
# Without this mount nothing copies the staged jar into the container. The upload succeeds, the
# restart succeeds, and the server quietly keeps running the previous build - which is exactly
# how two stale jars accumulated in data/craftics/mods unnoticed. Fail here, loudly, instead.
Write-Host ""
Write-Host "    checking /mods mount..." -NoNewline
# `docker compose config` NORMALISES short-form volumes, so the compose file's
#     - ./extra-mods/craftics:/mods
# comes back as
#     - type: bind
#       source: /opt/mcnet/extra-mods/craftics
#       target: /mods
# Grepping for ':/mods' therefore never matched and this check failed on a mount that was
# demonstrably working. Ask the running container instead - what is actually mounted right
# now beats what the file appears to say - and fall back to parsing config for the case
# where the service is currently stopped.
$mountCheck = Invoke-Ssh -Target $SshTarget -What "Mount check" -Command @"
cd '$RemoteRoot'
CID=`$(docker compose ps -aq '$RemoteService' 2>/dev/null | head -1)
if [ -n "`$CID" ]; then
    if docker inspect -f '{{range .Mounts}}{{.Destination}}{{"\n"}}{{end}}' "`$CID" 2>/dev/null | grep -qx '/mods'; then
        echo MOUNT_OK
        exit 0
    fi
fi
if docker compose config 2>/dev/null | grep -qE '^[[:space:]]*target:[[:space:]]*/mods[[:space:]]*$|:/mods(:|`$)'; then
    echo MOUNT_OK
else
    echo MOUNT_MISSING
fi
"@
if ($mountCheck -notmatch 'MOUNT_OK') {
    throw @"
The craftics service does not mount a /mods directory, so a staged jar can never reach the
container. Add this line to the craftics service in $RemoteRoot/docker-compose.yml:

      - ./extra-mods/craftics:/mods

then run:

      cd $RemoteRoot && docker compose up -d --force-recreate $RemoteService
"@
}
Write-Host "`r    /mods mounted              " -ForegroundColor DarkGray

# --- 1. prepare both mod directories ------------------------------------------------------------
# Prune BEFORE uploading: the new jar matches the same craftics-*.jar glob, so pruning afterwards
# would delete what we just sent.
#
# Two directories, not one. The staging dir is what we upload into; data/<service>/mods is what
# the container actually loads. itzg COPIES /mods into /data/mods on boot but never removes what
# is already there, so old builds pile up and Fabric ends up with several versions of the same
# mod id. Clearing both is the only way a deploy is genuinely a replacement.
Write-Host "    preparing remote..." -NoNewline
$prep = @"
set -e
mkdir -p '$RemoteModsDir'
rm -f '$RemoteModsDir'/craftics-*.jar
rm -f '$RemoteRoot/data/$RemoteService/mods'/craftics-*.jar
echo ok
"@
[void](Invoke-Ssh -Target $SshTarget -Command $prep -What "Remote prepare")
Write-Host "`r    remote prepared            " -ForegroundColor DarkGray

# --- 2. upload ----------------------------------------------------------------------------------
Write-Host "    uploading..." -NoNewline
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & scp -q "$($jar.FullName)" "${SshTarget}:${RemoteModsDir}/" 2>&1 | Out-Null
    $scpCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $prev
}
if ($scpCode -ne 0) {
    throw "scp failed (exit $scpCode). Is your ssh key loaded and $RemoteModsDir writable?"
}
Write-Host "`r    uploaded                   " -ForegroundColor DarkGray

# --- 3. verify, restart and confirm - in ONE ssh session ----------------------------------------
# Every ssh invocation re-prompts for the key passphrase when no agent is running, so four
# connections meant four prompts. Everything after the upload is independent of the local
# machine, so it runs as a single remote script instead.
#
# The hash comparison happens REMOTELY against a value passed in, which means a truncated
# upload aborts before the container is restarted rather than after.
$localHash = (Get-FileHash -Algorithm SHA256 -Path $jar.FullName).Hash.ToLower()

$remoteScript = @"
set -e
cd '$RemoteRoot'
ACTUAL=`$(sha256sum '$RemoteModsDir/$jarName' | cut -d' ' -f1)
if [ "`$ACTUAL" != "$localHash" ]; then
    echo "HASH_MISMATCH `$ACTUAL"
    exit 3
fi
echo "HASH_OK"
"@

if (-not $NoRestart) {
    $remoteScript += @"

docker compose restart '$RemoteService'
echo "RESTARTED"
"@
}

if (-not $NoRestart -and -not $NoVerify) {
    # The whole reason for the /mods mount: confirm AUTO_CURSEFORGE did not prune the jar back
    # out of /data/mods during the post-restart pack sync.
    $remoteScript += @"

sleep 60
COUNT=`$(ls -1 '$RemoteRoot/data/$RemoteService/mods/' 2>/dev/null | grep -c '^craftics-' || true)
echo "LIVE_COUNT `$COUNT"
"@
}

$stepLabel = if ($NoRestart) { "verifying upload..." } else { "verifying, restarting $RemoteService, confirming..." }
Write-Host "    $stepLabel"

$result = Invoke-Ssh -Target $SshTarget -Command $remoteScript -What "Remote verify/restart"

if ($result -match 'HASH_MISMATCH (\S+)') {
    throw "Hash mismatch after upload.`n  local : $localHash`n  remote: $($Matches[1])"
}
if ($result -notmatch 'HASH_OK') {
    throw "Remote never confirmed the upload hash. Raw output:`n$result"
}
Write-Host "    verified sha256 $($localHash.Substring(0,16))..." -ForegroundColor DarkGray

if ($NoRestart) {
    Write-Host ""
    Write-Host "Staged, not live. Restart when ready:" -ForegroundColor Yellow
    Write-Host "  ssh $SshTarget `"cd $RemoteRoot && docker compose restart $RemoteService`""
    return
}

Write-Host "    restarted" -ForegroundColor Green

if ($NoVerify) {
    Write-Host ""
    Write-Host "Deployed $jarName to $SshTarget." -ForegroundColor Green
    return
}

# --- 4. report ----------------------------------------------------------------------------------
if ($result -match 'LIVE_COUNT (\d+)') {
    $count = [int]$Matches[1]
} else {
    throw "Remote never reported a jar count. Raw output:`n$result"
}

Write-Host ""
if ($count -eq 0) {
    Write-Host "FAILED: no craftics-*.jar in data/$RemoteService/mods after the restart." -ForegroundColor Red
    Write-Host "The /mods copy-in did not happen. Recreate the service so the mount takes effect:" -ForegroundColor Red
    Write-Host "  ssh $SshTarget `"cd $RemoteRoot && docker compose up -d --force-recreate $RemoteService`"" -ForegroundColor Red
    exit 1
}

# More than one means an older build is still loaded alongside the new one. Fabric will pick
# whichever it resolves first, so the server may well be running last week's code while every
# other signal here says the deploy succeeded. Treat it as a failure, not a note.
if ($count -gt 1) {
    Write-Host "FAILED: $count craftics-*.jar files in data/$RemoteService/mods - duplicates." -ForegroundColor Red
    Write-Host "Fabric may load either one, so the running code is not determined by this deploy." -ForegroundColor Red
    Write-Host "Clear them and redeploy:" -ForegroundColor Red
    Write-Host "  ssh $SshTarget `"cd $RemoteRoot && docker compose stop $RemoteService && rm -f data/$RemoteService/mods/craftics-*.jar && docker compose up -d $RemoteService`"" -ForegroundColor Red
    exit 1
}

Write-Host "Deployed $jarName - live in data/$RemoteService/mods, single jar." -ForegroundColor Green
