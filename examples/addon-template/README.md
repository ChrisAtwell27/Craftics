# Craftics Example Addon

A starter template for building a [Craftics](https://github.com/ChrisAtwell27/Craftics)
addon. Copy this directory, rename it, and replace the examples with content for the
mod you want to integrate.

## What it shows

This template is a worked example of a Craftics compatibility addon. It makes content
from an existing mod, the [Aether](https://modrinth.com/mod/aether), playable in
Craftics' turn-based combat. That is what Craftics addons are for. It uses both
extension paths.

JSON datapacks, no code. Four files under
`src/main/resources/data/exampleaddon/craftics/` build a "Highlands" region:

- `environments/highlands.json` is a custom arena environment theme (floor, post, and
  light blocks).
- `biomes/highlands.json` is a custom biome, "Highlands".
- `paths/aether.json` is a biome path, "The Aether", that groups the biome into a
  region.
- `enemies/moa.json` adopts the Aether mod's Moa (`aether:moa`) as a Craftics enemy.

Java API. Two files under `src/main/java/com/example/exampleaddon/`:

- `ExampleCrafticsAddon.java` registers the Aether mod's `aether:zanite_sword` as a
  Craftics weapon. The item lookup is deferred to server start, because mod load order
  is undefined, and is skipped safely when the Aether mod is not installed.
- `MoaAI.java` is a custom combat AI for the Moa, registered with
  `CrafticsAPI.registerAI`. The Moa dashes in for melee attacks while healthy, then
  backs off and attacks at range once it drops below half health.

## What else you can integrate

This template covers weapons, enemies, and arena regions. The addon API can adopt much
more of another mod's content. Each of these has a `CrafticsAPI.register...` method,
and most also have a JSON datapack form under `data/<namespace>/craftics/`:

- Usable items (`registerUsableItem`): make a mod's consumable, throwable, or special
  item usable during a combat turn.
- Combat allies (`registerAlly`): make a mod's tameable creature fight alongside the
  player.
- Custom status effects (`registerEffect`): add a mod's effect as a combat status
  effect that ticks each round.
- Armor sets (`registerArmorSet`) and hybrid sets (`registerHybridSet`): give a mod's
  armor a Craftics set bonus.
- Trim patterns and materials (`registerTrimPattern`, `registerTrimMaterial`): give a
  mod's armor trims combat effects.
- Enchantments (`registerEnchantment`): give a mod's enchantment a passive combat stat
  bonus.
- Between-level events (`registerEvent`): add a custom event between arena levels.
- Equipment scanners (`registerEquipmentScanner`): pull combat stats from a mod's
  custom equipment slots, such as trinkets or curios.
- Custom enemy AI (`registerAI`): give an adopted enemy its own combat behavior. See
  `MoaAI.java` in this template.

See the Craftics modding guide for every registry and its JSON schema.

## Prerequisites

- JDK 21.
- Craftics. This addon depends on it at runtime.
- The Aether mod. This addon is a compatibility addon for it. Install the Aether mod
  to see the integration in action. Without it the addon still loads safely, but its
  content (the Zanite Sword weapon, the Moa enemy) has nothing to integrate.

## GitHub Packages access (required)

Craftics is published to GitHub Packages. Resolving it requires a GitHub Personal
Access Token, even for a public package. This is a GitHub Packages requirement, not a
Craftics one.

1. Create a classic token at <https://github.com/settings/tokens> with the
   `read:packages` scope.
2. Add it to your user-level Gradle properties, `~/.gradle/gradle.properties` (create
   the file if it does not exist):

   ```properties
   gpr.user=your-github-username
   gpr.key=ghp_yourtokenhere
   ```

   You can instead set the `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.

Do not put the token in this project's `gradle.properties`. It would be committed.

## Building

```sh
./gradlew build
```

The built addon jar is written to `build/libs/`.

## Running in a dev environment

```sh
./gradlew runClient
```

This launches Minecraft with Craftics and this addon loaded.

## Making it yours

1. Rename the `exampleaddon` id everywhere: `fabric.mod.json`, the Java package
   `com.example.exampleaddon`, the `gradle.properties` `maven_group` and
   `archives_base_name`, and the `data/exampleaddon/` resource folder.
2. Point the examples at the mod you want to integrate. Swap `aether:zanite_sword`,
   `aether:moa`, and the Highlands region for that mod's items, entities, and theme.
3. See the Craftics modding guide for the full API and JSON schema reference.
