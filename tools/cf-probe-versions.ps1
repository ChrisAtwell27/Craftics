<#
    Read-only probe: dumps CurseForge's version-type groups and the entries in each, so we can
    see the exact names for the Environment group (Client/Server) that upload error 1021 is about.
    Uploads nothing. Run with your token set:

        $env:CURSEFORGE_TOKEN = "<your NEW token>"
        ./tools/cf-probe-versions.ps1
#>
$ErrorActionPreference = "Stop"
$ApiBase = "https://minecraft.curseforge.com"
$Token = $env:CURSEFORGE_TOKEN
if (-not $Token) { throw "Set `$env:CURSEFORGE_TOKEN first." }

# The version TYPES (groups): each has an id and a name like "Minecraft 1.21", "Modloader",
# "Environment". Error 1021 fires when a required group has no id in the upload.
$types = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/game/version-types" `
    -Headers @{ "X-Api-Token" = $Token }
Write-Host "=== VERSION TYPES (groups) ===" -ForegroundColor Cyan
$types | Sort-Object id | Format-Table id, name, slug -AutoSize

# All versions, so we can see which entries belong to the Environment group.
$all = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/game/versions" `
    -Headers @{ "X-Api-Token" = $Token }

Write-Host "=== Entries whose name looks like an environment (Client/Server) ===" -ForegroundColor Cyan
$all | Where-Object { $_.name -match "Client|Server" } |
    Select-Object id, name, gameVersionTypeID | Format-Table -AutoSize

Write-Host "=== Fabric / loader entries ===" -ForegroundColor Cyan
$all | Where-Object { $_.name -match "Fabric|Forge|Quilt|NeoForge" } |
    Select-Object id, name, gameVersionTypeID | Format-Table -AutoSize
