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

        .\tools\deploy-server.ps1                       # deploy whatever is already built
        .\tools\deploy-server.ps1 -Build                # gradle build first, then deploy
        .\tools\deploy-server.ps1 -Build -SyncMods      # ...and match every other mod to the client
        .\tools\deploy-server.ps1 -DryRun               # show the plan, touch nothing
        .\tools\deploy-server.ps1 -NoRestart            # stage the jar, restart later yourself

    -SyncMods additionally reconciles the OTHER mods on the server against the CurseForge
    client instance (-InstanceMods, defaulting to the "Craftics x Cobblemon" instance):

      * reads each jar's fabric.mod.json and skips anything with environment = "client"
      * uploads only files whose SHA-256 differs from the server's copy
      * removes /data/mods jars that duplicate a staged mod id under an older filename
      * reports staged mods the client no longer has, deleting them only with -PruneServerMods

    It never touches the craftics jar itself - that is deployed from build/libs above, so the
    version you built is always the version that runs.

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

    # Also reconcile every OTHER mod on the server against the CurseForge client instance.
    [switch] $SyncMods,

    # The client instance's mods folder that -SyncMods treats as the source of truth.
    [string] $InstanceMods = "C:\Users\Chris\curseforge\minecraft\Instances\Craftics x Cobblemon\mods",

    # With -SyncMods, also DELETE staged mods that the client instance no longer has.
    # Off by default: the server legitimately runs mods the client never sees.
    [switch] $PruneServerMods,

    # Private key to unlock. Only used when a passphrase is available (see below).
    [string] $KeyPath = "$env:USERPROFILE\.ssh\id_ed25519",

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

# --- Scan the client instance (-SyncMods) -------------------------------------------------------
<#
    Read every jar in the CurseForge instance and decide which ones the SERVER needs.

    The decision is made from each jar's own fabric.mod.json "environment" field, not from its
    filename. "client" means the mod refuses to load server-side; anything else ("server" or
    "*", or the field being absent, which Fabric treats as "*") belongs on both.

    Two ids are excluded on purpose:
      craftics      - this script deploys that jar itself, from build/libs, a few lines above.
                      Taking the instance's copy instead would silently ship whatever version
                      the client happens to have, which is the bug this whole script exists to
                      avoid.
      crackedlobby  - the Fabric identity mod, built from the CrackedGamesLobbyPlugin repo and
                      deployed by ITS script. It is server-only and never appears in a client
                      instance, but excluding it by name documents that it is deliberate.
#>
$SKIP_IDS = @("craftics", "crackedlobby")

function Get-InstanceMods {
    param([Parameter(Mandatory)] [string] $Dir)

    if (-not (Test-Path $Dir)) { throw "Instance mods folder not found: $Dir" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $needed = @()
    $clientOnly = 0
    $unreadable = @()

    foreach ($f in Get-ChildItem -Path $Dir -Filter *.jar) {
        $zip = $null
        try {
            $zip = [IO.Compression.ZipFile]::OpenRead($f.FullName)
            $entry = $zip.Entries | Where-Object { $_.FullName -eq 'fabric.mod.json' } | Select-Object -First 1
            if (-not $entry) { $unreadable += "$($f.Name) (no fabric.mod.json)"; continue }

            $reader = New-Object IO.StreamReader($entry.Open())
            $meta = $reader.ReadToEnd() | ConvertFrom-Json
            $reader.Close()

            # Absent environment means "*" per the Fabric spec - both sides.
            $env_ = if ($meta.environment) { $meta.environment } else { '*' }
            if ($env_ -eq 'client') { $clientOnly++; continue }
            if ($SKIP_IDS -contains $meta.id) { continue }

            $needed += [pscustomobject]@{
                Id     = $meta.id
                File   = $f.Name
                Path   = $f.FullName
                Sha    = (Get-FileHash -Algorithm SHA256 -Path $f.FullName).Hash.ToLower()
            }
        } catch {
            $unreadable += "$($f.Name) ($($_.Exception.Message))"
        } finally {
            if ($zip) { $zip.Dispose() }
        }
    }

    if ($unreadable.Count) {
        Write-Host ""
        foreach ($u in $unreadable) { Write-Host "    SKIPPED (unreadable): $u" -ForegroundColor Yellow }
    }

    Write-Host ""
    Write-Host "  mod sync source: $Dir" -ForegroundColor Cyan
    Write-Host "    server-needed : $($needed.Count)"
    Write-Host "    client-only   : $clientOnly (excluded)"
    Write-Host "    skipped by id : $($SKIP_IDS -join ', ')"

    return $needed
}

# NOT named $instanceMods: PowerShell variable names are case-insensitive, so that would be
# the SAME variable as the -InstanceMods parameter and would blank the path before use.
$syncSet = @()
if ($SyncMods) {
    $syncSet = @(Get-InstanceMods -Dir $InstanceMods)
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

    # The script is base64-encoded and decoded on the far side.
    #
    # Two earlier approaches failed. Passing it as an argv element let Windows PowerShell 5.1
    # rewrite the quotes - open("/tmp/x") arrived as open(/tmp/x). Piping it to `bash -s` still
    # lost quotes somewhere in the native-command handoff, which showed up as
    # "syntax error: unexpected end of file" when a `for ... done` block had its closing quote
    # eaten and swallowed the `done`.
    #
    # Base64 ends that class of bug outright: the payload is [A-Za-z0-9+/=] only, so there is
    # no quote, space, backslash or dollar sign left for any shell or parser in the chain to
    # interpret. Whatever the script contains - nested quotes, here-docs, Python, backslashes -
    # arrives byte for byte.
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Command))

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = & ssh $Target "echo $b64 | base64 -d | bash" 2>&1
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

# scp with the same stderr handling, since scp is a native command too and prints progress and
# host-key notices to stderr exactly the way docker compose does.
function Send-ScpFile {
    param(
        [Parameter(Mandatory)] [string] $LocalPath,
        [Parameter(Mandatory)] [string] $RemotePath
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & scp -q "$LocalPath" "${SshTarget}:${RemotePath}" 2>&1 | Out-Null
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($code -ne 0) { throw "scp failed (exit $code) sending $LocalPath -> $RemotePath" }
}

# --- Unlock the key once, via ssh-agent ---------------------------------------------------------
<#
    Why this is not "echo the passphrase at each prompt":

    OpenSSH reads passphrases from /dev/tty, deliberately, so they cannot be piped or supplied
    from an environment variable. There is no flag that changes it. The supported mechanism is
    SSH_ASKPASS: when SSH_ASKPASS_REQUIRE=force, ssh-add runs the named program and reads the
    passphrase from ITS stdout. We use that exactly once, to load the key into ssh-agent, after
    which every ssh and scp in this script authenticates from the agent in silence.

    That is strictly better than answering each prompt: -SyncMods does one scp per changed mod,
    so a full sync was asking ~45 times.

    The passphrase never touches disk. The helper .cmd contains no secret - it echoes an
    environment variable that exists only in this process and its children, and the variable is
    cleared in the finally block below.

    Set it in the repo's .env (which is gitignored):

        CRAFTICS_SSH_PASSPHRASE=your-passphrase

    Leave it unset and nothing here runs - ssh prompts as before. Worth being clear-eyed about
    the trade: a passphrase sitting in .env protects against very little that the key file alone
    does not, since anything that can read one can read the other. It buys convenience, not
    security. The genuinely better option is running `ssh-add` yourself once per boot and never
    storing the passphrase anywhere:

        Get-Service ssh-agent | Set-Service -StartupType Automatic
        Start-Service ssh-agent
        ssh-add "$env:USERPROFILE\.ssh\id_ed25519"
#>
function Initialize-SshAgent {
    $pass = $env:CRAFTICS_SSH_PASSPHRASE
    if (-not $pass) {
        Write-Host "    no CRAFTICS_SSH_PASSPHRASE in .env - ssh will prompt per connection" -ForegroundColor DarkGray
        return
    }

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # ssh-add -l: 0 = identities loaded, 1 = agent up but empty, 2 = no agent.
        $null = & ssh-add -l 2>&1
        $state = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }

    if ($state -eq 0) {
        Write-Host "    ssh key already loaded in the agent" -ForegroundColor DarkGray
        return
    }

    if ($state -eq 2) {
        try { Start-Service ssh-agent -ErrorAction Stop } catch {
            Write-Host "    ssh-agent is not running and could not be started; ssh will prompt." -ForegroundColor Yellow
            Write-Host "    Fix once, in an elevated shell:" -ForegroundColor Yellow
            Write-Host "      Get-Service ssh-agent | Set-Service -StartupType Automatic; Start-Service ssh-agent" -ForegroundColor Yellow
            return
        }
    }

    if (-not (Test-Path $KeyPath)) {
        Write-Host "    no key at $KeyPath; ssh will prompt." -ForegroundColor Yellow
        return
    }

    $askpass = Join-Path $env:TEMP "craftics-askpass.cmd"
    @('@echo off', 'echo %CRAFTICS_SSH_PASSPHRASE%') | Set-Content -Path $askpass -Encoding ASCII

    $savedAskpass = $env:SSH_ASKPASS
    $savedRequire = $env:SSH_ASKPASS_REQUIRE
    try {
        $env:SSH_ASKPASS         = $askpass
        $env:SSH_ASKPASS_REQUIRE = 'force'
        $env:DISPLAY             = if ($env:DISPLAY) { $env:DISPLAY } else { 'localhost:0' }

        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $out = & ssh-add $KeyPath 2>&1
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prev
        }

        if ($code -eq 0) {
            Write-Host "    ssh key unlocked into the agent - no further prompts" -ForegroundColor Green
        } else {
            Write-Host "    ssh-add failed; ssh will prompt per connection." -ForegroundColor Yellow
            Write-Host "    $(($out | ForEach-Object { $_.ToString() }) -join ' ')" -ForegroundColor DarkGray
        }
    } finally {
        Remove-Item $askpass -ErrorAction SilentlyContinue
        $env:SSH_ASKPASS         = $savedAskpass
        $env:SSH_ASKPASS_REQUIRE = $savedRequire
    }
}

Write-Host ""
Initialize-SshAgent

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

# --- 0b. reconcile the other mods against the client instance (-SyncMods) -----------------------
<#
    Upload only what differs. The comparison is SHA-256 of each file, asked of the server in one
    round trip, so an unchanged 40-mod pack costs one ssh call and zero transfers.

    $reconcile is the important output. It is a list of "<modid>=<filename>" pairs handed to the
    remote script later, and it exists because of how this server gets its mods:

      * AUTO_CURSEFORGE installs the PACK's mods into /data/mods on every boot, from the pinned
        pack zip - currently 0.3.3.1.
      * itzg then copies /mods over the top.

    If the client instance has a NEWER build of a pack mod, both land in /data/mods under
    different filenames, Fabric sees two jars declaring the same mod id, and the server refuses
    to start. Matching on filename cannot catch that; matching on mod id can. The remote step
    deletes any /data/mods jar whose id we are staging but whose filename differs.

    That reconciliation has to run on EVERY deploy, not once, because the pack reinstalls its
    copy each boot. The durable fix is publishing a pack version that matches the client - this
    keeps the server correct until then.
#>
$reconcile = @()

if ($SyncMods) {
    if (-not $syncSet.Count) {
        Write-Host "    mod sync: nothing to sync (0 server-needed mods found)" -ForegroundColor Yellow
    } else {
        Write-Host "    comparing $($syncSet.Count) mods against the server..." -NoNewline

        # One sha256sum over the glob rather than a shell loop: same output, nothing to quote.
        # `|| true` so an empty directory is a normal result rather than a non-zero exit.
        $remote = Invoke-Ssh -Target $SshTarget -What "Remote mod hash" -Command @"
cd '$RemoteModsDir' 2>/dev/null && sha256sum *.jar 2>/dev/null
true
"@
        $remoteHashes = @{}
        foreach ($line in ($remote -split "`n")) {
            if ($line -match '^([0-9a-f]{64})\s+(.+)$') { $remoteHashes[$Matches[2].Trim()] = $Matches[1] }
        }
        Write-Host "`r    server has $($remoteHashes.Count) staged mod(s)            " -ForegroundColor DarkGray

        $toUpload = @()
        foreach ($m in $syncSet) {
            $reconcile += "$($m.Id)=$($m.File)"
            if (-not $remoteHashes.ContainsKey($m.File))      { $toUpload += ,@($m, "new") }
            elseif ($remoteHashes[$m.File] -ne $m.Sha)        { $toUpload += ,@($m, "changed") }
        }

        # Staged files the client no longer has. Reported always; removed only on request,
        # because the server legitimately runs mods that never appear in a client instance -
        # fabricproxy-lite and crossstitch arrive via MODRINTH_PROJECTS, crackedlobby from the
        # other repo, and the craftics jar from this very script.
        # @() around both sides: with exactly one instance mod, $syncSet.File is a STRING,
        # and "string" + @("x") concatenates instead of building a list.
        $wanted = @($syncSet.File) + @($jarName)
        $orphans = @($remoteHashes.Keys | Where-Object { $wanted -notcontains $_ -and $_ -notlike "crackedlobby-*" })

        if (-not $toUpload.Count) {
            Write-Host "    all $($syncSet.Count) mods already match" -ForegroundColor Green
        } else {
            Write-Host "    uploading $($toUpload.Count) of $($syncSet.Count):"
            foreach ($pair in $toUpload) {
                $m, $why = $pair
                Write-Host ("      {0,-8} {1}" -f $why, $m.File)
                Send-ScpFile -LocalPath $m.Path -RemotePath "$RemoteModsDir/"
            }
            Write-Host "    uploaded" -ForegroundColor Green
        }

        if ($orphans.Count) {
            Write-Host ""
            foreach ($o in $orphans) { Write-Host "    ORPHAN (staged, not in client): $o" -ForegroundColor Yellow }
            if ($PruneServerMods) {
                $rmList = ($orphans | ForEach-Object { "'" + ($_ -replace "'", "'\''") + "'" }) -join " "
                [void](Invoke-Ssh -Target $SshTarget -What "Prune orphans" `
                    -Command "cd '$RemoteModsDir' && rm -f $rmList && echo pruned")
                Write-Host "    pruned $($orphans.Count) orphan(s) (-PruneServerMods)" -ForegroundColor Yellow
            } else {
                Write-Host "    left in place. Pass -PruneServerMods to delete them." -ForegroundColor DarkGray
            }
        }
    }
}

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

# Duplicate-mod-id reconciliation. Runs BEFORE the restart so the container never boots with
# two jars claiming the same id - Fabric treats that as fatal and the server simply will not
# come up. See the comment on $reconcile above for why the pack keeps recreating this.
#
# The id=filename pairs go to the server as a here-doc'd FILE rather than being interpolated
# into the python source. Mod filenames contain quotes, brackets and '+' characters, and
# embedding them in code is how you get a syntax error on someone else's machine at midnight.
if ($SyncMods -and $reconcile.Count) {
    $pairs = ($reconcile -join "`n")
    $remoteScript += @"

cat > /tmp/craftics-reconcile.txt <<'PAIRS_EOF'
$pairs
PAIRS_EOF

python3 - <<'RECONCILE_EOF'
import json, os, zipfile

wanted = {}
with open('/tmp/craftics-reconcile.txt') as fh:
    for line in fh:
        line = line.strip()
        if '=' in line:
            k, v = line.split('=', 1)
            wanted[k.strip()] = v.strip()

mods = '$RemoteRoot/data/$RemoteService/mods'
removed = []
if os.path.isdir(mods):
    for fn in sorted(os.listdir(mods)):
        if not fn.endswith('.jar'):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, fn)) as z:
                mid = json.loads(z.read('fabric.mod.json'))['id']
        except Exception:
            continue
        # Same mod id, different file: the pack's older copy. Remove it - /mods supplies ours.
        if mid in wanted and wanted[mid] != fn:
            os.remove(os.path.join(mods, fn))
            removed.append(mid + ': ' + fn + ' (superseded by ' + wanted[mid] + ')')

print('RECONCILED ' + str(len(removed)))
for r in removed:
    print('  removed ' + r)
RECONCILE_EOF
"@
}

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

if ($result -match 'RECONCILED (\d+)') {
    $n = [int]$Matches[1]
    if ($n -gt 0) {
        Write-Host "    reconciled $n duplicate mod id(s) in data/$RemoteService/mods:" -ForegroundColor Yellow
        foreach ($line in ($result -split "`n")) {
            if ($line -match '^\s+removed ') { Write-Host "      $($line.Trim())" -ForegroundColor Yellow }
        }
    }
}

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
