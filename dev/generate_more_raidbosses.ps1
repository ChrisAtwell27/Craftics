$ErrorActionPreference = 'Stop'

$dir = Join-Path $PSScriptRoot '..\src\main\resources\data\craftics\raidbosses'
$dir = [System.IO.Path]::GetFullPath($dir)
if (-not (Test-Path $dir)) { throw "Raid boss directory not found: $dir" }

$existing = Get-ChildItem $dir -Filter '*.json' | ForEach-Object { $_.BaseName }

function Loot($a, $b, $c) {
    @(
        @{ item = 'minecraft:emerald'; weight = 8; min = 0; max = 100 },
        @{ item = $a; weight = 10; min = 8; max = 64 },
        @{ item = $b; weight = 9; min = 8; max = 64 },
        @{ item = $c; weight = 7; min = 8; max = 64 },
        @{ item = 'minecraft:diamond'; weight = 4; min = 1; max = 3 }
    )
}

$moves = @{
    storm = @('lightning_strike','sonic_boom','wing_buffet','phase_strike','void_gale','ground_pound')
    raider = @('bullet_storm','plant_mine','miners_fury','shield_bash','rampage','phase_strike')
    water = @('tidal_wave','trident_storm','riptide_charge','harpoon_pull','call_of_the_deep','fortify_shell')
    necro = @('raise_dead','gravefire_grid','hex_bolt','cursed_fog','decay_aura','vex_swarm')
    fire = @('fire_pillar','magma_eruption','fireball_barrage','fireball_rain','ground_slam','shield_bash')
    ice = @('blizzard','frost_arrow','ice_wall','avalanche','ground_pound','blink_step')
    beast = @('pounce_strike','gore_charge','rampage','swarm_call','ground_pound','tail_slam')
    web = @('web_spray','spawn_brood','venomous_bite','swarm_call','pounce_strike','entangle')
    stone = @('seismic_slam','tremor_stomp','boulder_toss','cave_in','rubble_toss','fortify_shell')
    void = @('void_beam','void_pull','null_burst','chorus_bomb','ender_roar','phase_strike')
    wither = @('wither_slash','skull_barrage','summon_wither_skeletons','gravefire_grid','decay_aura','fang_line')
    sand = @('sandstorm','sand_burial','curse_of_the_sands','rubble_toss','death_charge','shield_bash')
    blaze = @('summon_blaze_guard','fireball_rain','fire_pillar','magma_eruption','absorb_heat','tail_slam')
    ender = @('summon_endermites','chorus_bomb','void_pull','null_burst','ender_roar','phase_strike')
}

$obstacles = @{
    grave = @(
        @{ tile='grave'; count=@{min=4;max=8} },
        @{ tile='decay'; count=@{min=3;max=7} },
        @{ tile='soul_fire'; count=@{min=2;max=5} }
    )
    fire = @(
        @{ tile='lava'; count=@{min=2;max=4} },
        @{ tile='soul_fire'; count=@{min=3;max=6} },
        @{ tile='obstacle'; block='minecraft:basalt'; count=@{min=5;max=8} }
    )
    ice = @(
        @{ tile='powder_snow'; count=@{min=3;max=6} },
        @{ tile='ice'; count=@{min=4;max=8} },
        @{ tile='obstacle'; block='minecraft:packed_ice'; count=@{min=4;max=7} }
    )
    storm = @(
        @{ tile='mud'; count=@{min=3;max=6} },
        @{ tile='rubble'; block='minecraft:cobblestone'; count=@{min=2;max=5} },
        @{ tile='obstacle'; block='minecraft:deepslate_tiles'; count=@{min=4;max=7} }
    )
    void = @(
        @{ tile='sculk'; count=@{min=4;max=8} },
        @{ tile='obstacle'; block='minecraft:crying_obsidian'; count=@{min=4;max=7} },
        @{ tile='rubble'; block='minecraft:end_stone'; count=@{min=2;max=4} }
    )
    water = @(
        @{ tile='water'; count=@{min=4;max=8} },
        @{ tile='mud'; count=@{min=3;max=6} },
        @{ tile='obstacle'; block='minecraft:prismarine'; count=@{min=4;max=7} }
    )
    web = @(
        @{ tile='obstacle'; block='minecraft:cobweb'; count=@{min=5;max=9} },
        @{ tile='mud'; count=@{min=3;max=6} },
        @{ tile='rubble'; block='minecraft:mossy_cobblestone'; count=@{min=2;max=4} }
    )
    stone = @(
        @{ tile='rubble'; block='minecraft:tuff'; count=@{min=3;max=6} },
        @{ tile='obstacle'; block='minecraft:stone_bricks'; count=@{min=5;max=9} },
        @{ tile='mud'; count=@{min=2;max=4} }
    )
    jungle = @(
        @{ tile='mud'; count=@{min=4;max=8} },
        @{ tile='tall_grass'; count=@{min=4;max=7} },
        @{ tile='obstacle'; block='minecraft:moss_block'; count=@{min=4;max=7} }
    )
    sand = @(
        @{ tile='obstacle'; block='minecraft:cut_sandstone'; count=@{min=5;max=9} },
        @{ tile='rubble'; block='minecraft:sandstone'; count=@{min=3;max=6} },
        @{ tile='mud'; count=@{min=2;max=4} }
    )
    nether = @(
        @{ tile='soul_fire'; count=@{min=3;max=6} },
        @{ tile='obstacle'; block='minecraft:blackstone'; count=@{min=5;max=8} },
        @{ tile='decay'; count=@{min=3;max=6} }
    )
}

$lootByTheme = @{
    storm = (Loot 'minecraft:lightning_rod' 'minecraft:copper_ingot' 'minecraft:amethyst_shard')
    raider = (Loot 'minecraft:crossbow' 'minecraft:iron_ingot' 'minecraft:gold_ingot')
    water = (Loot 'minecraft:prismarine_shard' 'minecraft:nautilus_shell' 'minecraft:kelp')
    necro = (Loot 'minecraft:bone' 'minecraft:rotten_flesh' 'minecraft:soul_soil')
    fire = (Loot 'minecraft:blaze_rod' 'minecraft:magma_cream' 'minecraft:nether_brick')
    ice = (Loot 'minecraft:packed_ice' 'minecraft:snowball' 'minecraft:arrow')
    beast = (Loot 'minecraft:leather' 'minecraft:beef' 'minecraft:raw_iron')
    web = (Loot 'minecraft:string' 'minecraft:spider_eye' 'minecraft:moss_block')
    stone = (Loot 'minecraft:stone_bricks' 'minecraft:tuff' 'minecraft:andesite')
    void = (Loot 'minecraft:ender_pearl' 'minecraft:chorus_fruit' 'minecraft:end_stone')
    wither = (Loot 'minecraft:wither_rose' 'minecraft:bone_block' 'minecraft:coal')
    sand = (Loot 'minecraft:sandstone' 'minecraft:gold_nugget' 'minecraft:bone')
    blaze = (Loot 'minecraft:blaze_powder' 'minecraft:coal' 'minecraft:obsidian')
    ender = (Loot 'minecraft:ender_pearl' 'minecraft:popped_chorus_fruit' 'minecraft:purpur_block')
}

$specs = @(
    @{p='Relic'; s='Stormcaller'; theme='storm'; entity='minecraft:evoker'; env='mountain'; obs='storm'},
    @{p='Gilded'; s='Marauder'; theme='raider'; entity='minecraft:pillager'; env='desert'; obs='sand'},
    @{p='Dusk'; s='Harbormaster'; theme='water'; entity='minecraft:drowned'; env='river'; obs='water'},
    @{p='Mirebone'; s='Prophet'; theme='necro'; entity='minecraft:witch'; env='plains'; obs='grave'},
    @{p='Emberhorn'; s='Warlord'; theme='fire'; entity='minecraft:piglin_brute'; env='crimson_forest'; obs='fire'},
    @{p='Frostveil'; s='Archon'; theme='ice'; entity='minecraft:stray'; env='snowy'; obs='ice'},
    @{p='Thundermaw'; s='Reaver'; theme='beast'; entity='minecraft:ravager'; env='mountain'; obs='storm'},
    @{p='Bloodvine'; s='Matriarch'; theme='web'; entity='minecraft:spider'; env='jungle'; obs='jungle'},
    @{p='Runic'; s='Stoneguard'; theme='stone'; entity='minecraft:vindicator'; env='cave'; obs='stone'},
    @{p='Abyssfang'; s='Corsair'; theme='void'; entity='minecraft:phantom'; env='outer_end_islands'; obs='void'},
    @{p='Sorrowglass'; s='Siren'; theme='void'; entity='minecraft:ghast'; env='soul_sand_valley'; obs='nether'},
    @{p='Ironshard'; s='Sentinel'; theme='stone'; entity='minecraft:iron_golem'; env='plains'; obs='stone'},
    @{p='Plaguebloom'; s='Sovereign'; theme='web'; entity='minecraft:cave_spider'; env='deep_dark'; obs='jungle'},
    @{p='Voidflame'; s='Jailer'; theme='blaze'; entity='minecraft:blaze'; env='basalt_deltas'; obs='fire'},
    @{p='Cindermaul'; s='Overseer'; theme='fire'; entity='minecraft:magma_cube'; env='nether_wastes'; obs='fire'},
    @{p='Mossfang'; s='Behemoth'; theme='beast'; entity='minecraft:zoglin'; env='forest'; obs='jungle'},
    @{p='Stormgrave'; s='Adjudicator'; theme='wither'; entity='minecraft:wither_skeleton'; env='deep_dark'; obs='grave'},
    @{p='Nightforge'; s='Exarch'; theme='fire'; entity='minecraft:piglin'; env='warped_forest'; obs='nether'},
    @{p='Dunescar'; s='Executioner'; theme='sand'; entity='minecraft:husk'; env='desert'; obs='sand'},
    @{p='Marrowthorn'; s='Invoker'; theme='necro'; entity='minecraft:evoker'; env='plains'; obs='grave'},
    @{p='Riftbound'; s='Lancer'; theme='ender'; entity='minecraft:enderman'; env='end_city'; obs='void'},
    @{p='Blackwater'; s='Predator'; theme='water'; entity='minecraft:guardian'; env='river'; obs='water'},
    @{p='Shattered'; s='Wayfinder'; theme='stone'; entity='minecraft:skeleton'; env='cave'; obs='stone'},
    @{p='Soulcoil'; s='Hierarch'; theme='blaze'; entity='minecraft:ghast'; env='soul_sand_valley'; obs='nether'},
    @{p='Brimstone'; s='Subjugator'; theme='fire'; entity='minecraft:blaze'; env='basalt_deltas'; obs='fire'},
    @{p='Frostspine'; s='Watcher'; theme='ice'; entity='minecraft:stray'; env='snowy'; obs='ice'},
    @{p='Tidewrath'; s='Mariner'; theme='water'; entity='minecraft:drowned'; env='river'; obs='water'},
    @{p='Galefang'; s='Skirmisher'; theme='storm'; entity='minecraft:phantom'; env='mountain'; obs='storm'},
    @{p='Echoing'; s='Dominion'; theme='void'; entity='minecraft:shulker'; env='deep_dark'; obs='void'},
    @{p='Basalt'; s='Widowqueen'; theme='web'; entity='minecraft:cave_spider'; env='basalt_deltas'; obs='nether'}
)

if ($specs.Count -ne 30) { throw "Expected 30 specs, got $($specs.Count)" }

$newIds = @()
for ($i = 0; $i -lt $specs.Count; $i++) {
    $sp = $specs[$i]
    $id = ($sp.p + '_' + $sp.s).ToLowerInvariant()
    $id = ($id -replace '[^a-z0-9_]', '')
    $newIds += $id
}

$dupNew = $newIds | Group-Object | Where-Object { $_.Count -gt 1 }
if ($dupNew) { throw "Duplicate IDs in new set: $($dupNew.Name -join ', ')" }

$collide = $newIds | Where-Object { $existing -contains $_ }
if ($collide) { throw "IDs already exist: $($collide -join ', ')" }

for ($i = 0; $i -lt $specs.Count; $i++) {
    $sp = $specs[$i]
    $id = ($sp.p + '_' + $sp.s).ToLowerInvariant() -replace '[^a-z0-9_]', ''
    $name = "The $($sp.p) $($sp.s)"

    $attackCycle = @(6,7,8,9,10,11,12)
    $attack = $attackCycle[$i % $attackCycle.Count]
    $defense = 7 + ($i % 3)
    $range = if ($sp.theme -in @('storm','void','raider','ice','water','blaze','ender')) { 3 } elseif ($sp.theme -in @('fire','wither')) { 2 } else { 1 }
    $speed = if ($sp.theme -in @('web','beast','ender','storm')) { 3 } else { 2 }
    $hp = 1080 + ($i * 6)
    $bounty = 72 + ($i % 12)
    $weight = 8 + ($i % 2)

    $power = if ($i % 3 -eq 0) {
        @{ type='double_move' }
    } else {
        $buffs = @('regeneration','strength','resistance','speed','absorption')
        @{ type='buff'; effect=$buffs[$i % $buffs.Count]; amplifier= if ($i % 5 -eq 0) { 1 } else { 0 } }
    }

    $obj = [ordered]@{
        id = $id
        name = $name
        entity = $sp.entity
        hp = $hp
        attack = $attack
        defense = $defense
        range = $range
        speed = $speed
        moves = $moves[$sp.theme]
        power = $power
        environment = $sp.env
        bounty = $bounty
        loot = $lootByTheme[$sp.theme]
        obstacles = $obstacles[$sp.obs]
        weight = $weight
    }

    $path = Join-Path $dir ($id + '.json')
    $obj | ConvertTo-Json -Depth 12 | Set-Content -Path $path -NoNewline
}

Write-Output "Created $($specs.Count) new raid bosses."
$newIds | Sort-Object | ForEach-Object { Write-Output $_ }
