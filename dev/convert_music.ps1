# Converts the supplied MP3 soundtrack files into OGG Vorbis for the Craftics music system.
# Minecraft's sound engine only plays OGG Vorbis; the source files are MP3.
# Re-run any time tracks are added/swapped. Keys here MUST match MusicTracks.java + sounds.json.

$ffmpeg = "C:\Users\Chris\Downloads\ffmpeg-8.1.1-essentials_build\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe"
$src    = "e:\Craftics\music"
$out    = "e:\Craftics\src\main\resources\assets\craftics\sounds\music"
New-Item -ItemType Directory -Force -Path $out | Out-Null

# source MP3 file name  ->  ogg key (used as craftics:music/<key>)
$map = [ordered]@{
    # --- biome battle ---
    "49. arena2.mp3"                        = "arena2"
    "22. Targeted.mp3"                      = "targeted"
    "40. Friend or Foe.mp3"                 = "friend_or_foe"
    "33. Cacti Canyon.mp3"                  = "cacti_canyon"
    "05. Frozen Fjord.mp3"                  = "frozen_fjord"
    "24. Flurry.mp3"                        = "flurry"
    "12. Squid Coast.mp3"                   = "squid_coast"
    "22. Nether Wastes.mp3"                 = "nether_wastes"
    "04. Basalt Deltas.mp3"                 = "basalt_deltas"
    "06. Crimson Forest.mp3"                = "crimson_forest"
    "09. Warped Forest.mp3"                 = "warped_forest"
    "07. Soulsand Valley.mp3"               = "soulsand_valley"
    "09. City.mp3"                          = "city"
    "07. End Wilds.mp3"                     = "end_wilds"
    "18. Astray Archipelago.mp3"            = "astray_archipelago"
    "24. Broken Heart of Ender.mp3"         = "broken_heart_of_ender"
    "19. Gale Sanctum.mp3"                  = "gale_sanctum"
    "25. Garrison.mp3"                      = "garrison"
    "31. Redstone Mines.mp3"                = "redstone_mines"
    "20. Crypt.mp3"                         = "crypt"
    # --- boss ---
    "25. Battle for Daylight.mp3"           = "battle_for_daylight"
    "51. Evoker.mp3"                        = "evoker"
    "18. Spider Den.mp3"                    = "spider_den"
    "40. Desert Temple.mp3"                 = "desert_temple"
    "07. River Lock.mp3"                    = "river_lock"
    "21. Rush.mp3"                          = "rush"
    "27. Soggy Swamp.mp3"                   = "soggy_swamp"
    "41. Necromancer.mp3"                   = "necromancer"
    "38. Redstone Monstrosity.mp3"          = "redstone_monstrosity"
    "05. Kermetic.mp3"                      = "kermetic"
    "23. Menta Menardi.mp3"                 = "menta_menardi"
    "10. Ghast.mp3"                         = "ghast"
    "11. Ancient.mp3"                       = "ancient"
    "03. Torn Acinder.mp3"                  = "torn_acinder"
    "12. Ship.mp3"                          = "ship"
    "50. Enderman.mp3"                      = "enderman"
    "21. Shattered.mp3"                     = "shattered"
    # --- events ---
    "06. Stuga.mp3"                         = "stuga"
    "23. Mary.mp3"                          = "mary"
    "25. Beach House.mp3"                   = "beach_house"
    "27. Hunters in a Horrendous Hurry.mp3" = "hunters"
    "17. Skogsstuga.mp3"                    = "skogsstuga"
    "15. Lost Settlement.mp3"               = "lost_settlement"
}

$ok = 0; $fail = 0
foreach ($entry in $map.GetEnumerator()) {
    $inFile  = Join-Path $src $entry.Key
    $outFile = Join-Path $out ($entry.Value + ".ogg")
    if (-not (Test-Path $inFile)) { Write-Host "MISSING SOURCE: $($entry.Key)"; $fail++; continue }
    & $ffmpeg -hide_banner -loglevel error -nostdin -y -i $inFile -vn -c:a libvorbis -q:a 5 -ar 44100 $outFile
    if (Test-Path $outFile) { $ok++ } else { Write-Host "FAILED: $($entry.Key)"; $fail++ }
}
Write-Host "DONE. converted=$ok failed=$fail total=$($map.Count)"