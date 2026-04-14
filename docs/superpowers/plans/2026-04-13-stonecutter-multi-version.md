# Stonecutter Multi-Version Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Craftics from a single-version Fabric mod targeting MC 1.21.1 to a Stonecutter-based multi-version project that produces working jars for MC 1.21.1, 1.21.3, 1.21.4, and 1.21.5 from a single source tree.

**Architecture:** Use the Stonecutter Gradle plugin (kikugie, `dev.kikugie.stonecutter` 0.7.11, Groovy DSL) to maintain one source tree with preprocessor comments (`//? if`) for version-specific code. Per-version dep coordinates live in `versions/<mc>/gradle.properties`. Root `build.gradle` interpolates all coords from properties so the same script serves every shard. Active shard is switched via `./gradlew "Set active project to <version>"` followed by normal build/runClient tasks; `chiseledBuild` builds all shards in one pass and drops per-version jars in `build/libs/`.

**Tech Stack:** Fabric Loom 1.15.5, Stonecutter 0.7.11 (Groovy DSL), Java 21, Fabric API, owo-lib, Cardinal Components API, Player Animator.

**Context / Prior Work:** The dep cleanup (removal of GeckoLib and Pehkui, which were declared but unused/stale) is already done in this worktree. The migration starts from a clean 1.21.1 build.

**Known Dep Matrix (verified on Modrinth, 2026-04-13):**

| Dep | 1.21.1 | 1.21.3 | 1.21.4 | 1.21.5 |
|---|---|---|---|---|
| Yarn mappings | `1.21.1+build.3` | `1.21.3+build.1` (pick latest) | `1.21.4+build.8` | `1.21.5+build.1` |
| Fabric loader | `0.18.3` | `0.18.4` | `0.18.4` | `0.18.4` |
| Fabric API | `0.102.0+1.21.1` | `0.114.1+1.21.3` | `0.119.4+1.21.4` | `0.128.2+1.21.5` |
| owo-lib | `0.12.15.4+1.21` | `0.12.18+1.21.2` | `0.12.20+1.21.4` | `0.12.21+1.21.5` |
| CCA base/entity/world | `6.1.3` | `6.2.2` | `6.2.2` | `6.3.1` |
| Player Animator | `2.0.4+1.21.1` | TBD (confirmed to exist) | TBD | TBD |

(The Player Animator exact versions for 1.21.3/4/5 are fetched at execution time from Modrinth; the project is confirmed to publish builds for all four MC versions.)

**Known mapping drift to guard with `//?`:**
- `EntityAttributes.GENERIC_SCALE` → `EntityAttributes.SCALE` in 1.21.2+ (6 occurrences in `CombatManager.java`)
- `EntityAttributes.GENERIC_ATTACK_DAMAGE` → `EntityAttributes.ATTACK_DAMAGE` in 1.21.2+ (1 occurrence in `CombatManager.java`)

Further drift is expected to surface during per-shard builds and is handled via discovery tasks (Tasks 9-11). Each discovery task has a fixed workflow: build, read compiler error, add `//? if <=1.21.1 { old } else { new }` guard, rebuild, commit.

---

## Task 1: Verify Stonecutter plugin loads in Groovy DSL

**Why:** Stonecutter's official docs use Kotlin DSL. Groovy is supported but annotated as "limited". Before touching anything else, verify the plugin even loads in our Groovy setup. If it doesn't, the entire plan switches to converting the build to Kotlin DSL (out of scope for this plan — stop and re-plan).

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1.1: Add Stonecutter to settings.gradle**

Open `settings.gradle` and replace its entire contents with:

```groovy
pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.fabricmc.net/'
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id 'dev.kikugie.stonecutter' version '0.7.11'
}

stonecutter {
	create(rootProject) {
		versions '1.21.1', '1.21.3', '1.21.4', '1.21.5'
		vcsVersion '1.21.1'
	}
}

rootProject.name = "craftics"
```

- [ ] **Step 1.2: Verify plugin loads**

Run: `./gradlew help 2>&1 | tail -20`

Expected: no errors about "Plugin [id: 'dev.kikugie.stonecutter'] was not found" and no NullPointerException. A deprecation warning about Groovy DSL support is acceptable and expected.

If you see **"Plugin [id: 'dev.kikugie.stonecutter'] was not found"**: the version number `0.7.11` may be wrong. Run `curl -s https://plugins.gradle.org/m2/dev/kikugie/stonecutter/dev.kikugie.stonecutter.gradle.plugin/maven-metadata.xml | tail -20` to find the latest, update, retry.

If you see **class-cast errors from stonecutter internals complaining about Groovy**: stop. Do not proceed. Report back — the project needs a full Kotlin DSL conversion which is a separate, larger scope.

- [ ] **Step 1.3: Commit the scaffold**

```bash
git add settings.gradle
git commit -m "build: add Stonecutter plugin to settings.gradle"
```

---

## Task 2: Create stonecutter.gradle root orchestrator

**Why:** Stonecutter expects a `stonecutter.gradle` file at the root to configure chiseled (all-shard) tasks and apply per-shard settings. Without this, `chiseledBuild` won't exist and Stonecutter's task registration complains.

**Files:**
- Create: `stonecutter.gradle`

- [ ] **Step 2.1: Create stonecutter.gradle**

Create the file `stonecutter.gradle` in the repo root with exactly:

```groovy
plugins {
	id 'dev.kikugie.stonecutter'
}

stonecutter active '1.21.1' /* [SC] DO NOT EDIT */

// Chiseled tasks run once per configured version shard.
stonecutter.registerChiseled tasks.register('chiseledBuild', stonecutter.chiseled) {
	group = 'stonecutter'
	description = 'Builds all version shards'
	stonecutter.include ':build'
}

stonecutter.registerChiseled tasks.register('chiseledRunClient', stonecutter.chiseled) {
	group = 'stonecutter'
	description = 'Runs each version shard (sequential)'
	stonecutter.include ':runClient'
}
```

The `stonecutter active '1.21.1'` line is a **marker comment** that Stonecutter rewrites when you switch versions. Do not edit it manually — use the `Set active project to <version>` task.

- [ ] **Step 2.2: Verify Stonecutter discovers the file**

Run: `./gradlew tasks --group=stonecutter 2>&1 | tail -30`

Expected output contains:
- `chiseledBuild`
- `chiseledRunClient`
- One or more "Set active project to 1.21.X" tasks

If no tasks appear, the `stonecutter { create(rootProject) { ... } }` block in settings.gradle didn't take effect. Check for typos.

- [ ] **Step 2.3: Commit**

```bash
git add stonecutter.gradle
git commit -m "build: add stonecutter.gradle chiseled task orchestrator"
```

---

## Task 3: Create versions/ directory and per-shard gradle.properties

**Why:** Each MC version shard needs its own dep coordinates. Stonecutter looks for `versions/<ver>/gradle.properties` and overlays those onto the root `gradle.properties` when that shard is active.

**Files:**
- Create: `versions/1.21.1/gradle.properties`
- Create: `versions/1.21.3/gradle.properties`
- Create: `versions/1.21.4/gradle.properties`
- Create: `versions/1.21.5/gradle.properties`
- Modify: `gradle.properties` (strip version-specific keys)

- [ ] **Step 3.1: Strip version-specific keys from root gradle.properties**

Replace the entire contents of `gradle.properties` with:

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx2G
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot
org.gradle.parallel=true

# Mod Properties (shared across all shards)
mod_version=0.1.0
maven_group=com.crackedgames
archives_base_name=craftics

# WorldEdit — dev-only arena editor (modLocalRuntime, not shipped with the mod)
# For Minecraft 1.21.x, EngineHub publishes this artifact under mc1.21
worldedit_version=7.3.8
```

All version-specific keys (`minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`) move into per-shard files below.

- [ ] **Step 3.2: Create versions/1.21.1/gradle.properties**

```properties
# MC 1.21.1 shard
minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
loader_version=0.18.3
fabric_version=0.102.0+1.21.1

# Dep coordinates
owo_version=0.12.15.4+1.21
cca_version=6.1.3
player_anim_version=2.0.4+1.21.1
```

- [ ] **Step 3.3: Create versions/1.21.3/gradle.properties**

```properties
# MC 1.21.3 shard
minecraft_version=1.21.3
yarn_mappings=1.21.3+build.1
loader_version=0.18.4
fabric_version=0.114.1+1.21.3

# Dep coordinates
owo_version=0.12.18+1.21.2
cca_version=6.2.2
player_anim_version=2.0.4+1.21.3
```

Note: `yarn_mappings=1.21.3+build.1` is a placeholder — the actual latest build number must be confirmed at execution. Run `curl -s https://meta.fabricmc.net/v2/versions/yarn/1.21.3 | head -5` to find the latest build. Same for `player_anim_version`: confirm from `curl -s https://api.modrinth.com/v2/project/player-animator/version | grep '1.21.3'`.

- [ ] **Step 3.4: Create versions/1.21.4/gradle.properties**

```properties
# MC 1.21.4 shard
minecraft_version=1.21.4
yarn_mappings=1.21.4+build.8
loader_version=0.18.4
fabric_version=0.119.4+1.21.4

# Dep coordinates
owo_version=0.12.20+1.21.4
cca_version=6.2.2
player_anim_version=2.0.4+1.21.4
```

- [ ] **Step 3.5: Create versions/1.21.5/gradle.properties**

```properties
# MC 1.21.5 shard
minecraft_version=1.21.5
yarn_mappings=1.21.5+build.1
loader_version=0.18.4
fabric_version=0.128.2+1.21.5

# Dep coordinates
owo_version=0.12.21+1.21.5
cca_version=6.3.1
player_anim_version=2.0.4+1.21.5
```

- [ ] **Step 3.6: Commit**

```bash
git add gradle.properties versions/
git commit -m "build: add per-version gradle.properties shards"
```

---

## Task 4: Rewrite build.gradle to interpolate all version-specific coords

**Why:** All dep coordinates must read from `project.<name>` so the same script serves every shard. Currently some coords are hardcoded (e.g., `0.12.15.4+1.21` for owo-lib, `6.1.3` for Cardinal Components).

**Files:**
- Modify: `build.gradle`

- [ ] **Step 4.1: Replace the dependencies block**

In `build.gradle`, replace the entire `dependencies { ... }` block with:

```groovy
dependencies {
	// Versions are read from versions/<mc>/gradle.properties via Stonecutter
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
	modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"

	// Fabric API
	modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

	// PlayerAnimator — player combat animations (JiJ bundled)
	include modImplementation("dev.kosmx.player-anim:player-animation-lib-fabric:${project.player_anim_version}")

	// owo-lib — UI framework, networking, config
	modImplementation "io.wispforest:owo-lib:${project.owo_version}"
	include "io.wispforest:owo-sentinel:${project.owo_version}"
	annotationProcessor "io.wispforest:owo-lib:${project.owo_version}"

	// Cardinal Components API — auto-save/sync data on entities/worlds
	include modImplementation("org.ladysnake.cardinal-components-api:cardinal-components-base:${project.cca_version}")
	include modImplementation("org.ladysnake.cardinal-components-api:cardinal-components-entity:${project.cca_version}")
	include modImplementation("org.ladysnake.cardinal-components-api:cardinal-components-world:${project.cca_version}")

	// WorldEdit — dev-only (modLocalRuntime, not shipped)
	// EngineHub publishes the 1.21.x artifact under mc1.21
	modLocalRuntime "com.sk89q.worldedit:worldedit-fabric-mc1.21:${project.worldedit_version}"
}
```

Notable changes from the previous block:
- `owo-lib` and `cardinal-components-api` coords now use `${project.<var>}` interpolation
- `player-animation-lib-fabric` coord also interpolated

No other block in `build.gradle` should need changes — the existing `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version` interpolations already worked.

- [ ] **Step 4.2: Verify the active (1.21.1) shard still configures**

Run: `./gradlew help 2>&1 | tail -20`

Expected: no "Could not get unknown property 'owo_version'" errors. If you see them, the active shard's gradle.properties is missing that key — check `versions/1.21.1/gradle.properties` exists and contains `owo_version=...`.

- [ ] **Step 4.3: Commit**

```bash
git add build.gradle
git commit -m "build: interpolate all dep coords from per-shard gradle.properties"
```

---

## Task 5: Template minecraft version constraint in fabric.mod.json

**Why:** `fabric.mod.json` currently hardcodes `"minecraft": "~1.21.1"` which will make the jar refuse to load on 1.21.3/4/5 at runtime. Stonecutter has a built-in `fabric.mod.json` rewriter for this exact case. Alternative: use the existing `processResources` filesMatching expand.

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `build.gradle`

- [ ] **Step 5.1: Template the minecraft version in fabric.mod.json**

In `src/main/resources/fabric.mod.json`, change line 37:

```json
"minecraft": "~1.21.1",
```

to:

```json
"minecraft": "${minecraft_dependency}",
```

- [ ] **Step 5.2: Extend processResources in build.gradle**

In `build.gradle`, find the existing `processResources` block:

```groovy
processResources {
	inputs.property "version", project.version

	filesMatching("fabric.mod.json") {
		expand "version": project.version
	}
}
```

Replace with:

```groovy
processResources {
	def mcDep = "~${project.minecraft_version}"

	inputs.property "version", project.version
	inputs.property "minecraft_dependency", mcDep

	filesMatching("fabric.mod.json") {
		expand(
			"version": project.version,
			"minecraft_dependency": mcDep
		)
	}
}
```

- [ ] **Step 5.3: Verify the template resolves for the active shard**

Run: `./gradlew processResources && cat build/resources/main/fabric.mod.json | grep minecraft`

Expected: `"minecraft": "~1.21.1"` (for the active 1.21.1 shard).

- [ ] **Step 5.4: Commit**

```bash
git add src/main/resources/fabric.mod.json build.gradle
git commit -m "build: template minecraft version in fabric.mod.json"
```

---

## Task 6: Baseline build of 1.21.1 shard

**Why:** Before touching any source, confirm the Stonecutter scaffold produces a byte-identical (or at least successful) build for the original target shard. This isolates build-config errors from source drift errors.

**Files:** (none modified)

- [ ] **Step 6.1: Confirm active shard is 1.21.1**

Run: `grep 'stonecutter active' stonecutter.gradle`

Expected: `stonecutter active '1.21.1'`

- [ ] **Step 6.2: Full build**

Run: `./gradlew build 2>&1 | tail -40`

Expected: `BUILD SUCCESSFUL`. Same warnings as before (owo-sentinel semver note, annotation processor release version note) are acceptable.

If the build fails: do NOT proceed. The Stonecutter scaffold is broken on the original shard. Debug the failure, which is almost certainly a missing property or typo in the new per-shard file.

- [ ] **Step 6.3: Verify output jar exists**

Run: `ls -lh build/libs/craftics-0.1.0.jar`

Expected: a file ~several MB in size.

- [ ] **Step 6.4: No commit — this is verification only.**

---

## Task 7: Add `//?` guards for known `EntityAttributes` drift

**Why:** `EntityAttributes.GENERIC_SCALE` and `GENERIC_ATTACK_DAMAGE` are renamed in MC 1.21.2+. These are the only pre-known drift points. All 7 occurrences are in `CombatManager.java`.

Stonecutter preprocessor syntax (Groovy-style comment blocks):
- `//? if <=1.21.1` applies to MC 1.21.1 and below
- `//? if >=1.21.2` applies to 1.21.2 and above
- A multi-line replacement block: `//? if >=1.21.2 {\n /*NEW CODE*/ \n //?} else {\n OLD_CODE \n //?}`

The pattern: the currently-active version keeps its code uncommented, and the inactive version sits inside `/* */` block comments that Stonecutter flips when you switch shards.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java` (7 locations)

- [ ] **Step 7.1: Guard line 1644 (GENERIC_ATTACK_DAMAGE)**

Find this line in `CombatManager.java`:

```java
                    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
```

Replace it (preserving surrounding context) with:

```java
                    //? if <=1.21.1 {
                    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
                    //?} else
                    /*net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);*/
```

Exact preprocessor syntax must be confirmed during execution — Stonecutter's block-comment convention is critical. If the syntax differs, consult `./gradlew help --task chiseledBuild` output or the docs fetched fresh during execution.

- [ ] **Step 7.2: Guard 6 occurrences of GENERIC_SCALE**

For each of lines 3923, 4178, 4186, 5067, 7276 (and any duplicates in the file after edits shift line numbers), find:

```java
EntityAttributes.GENERIC_SCALE
```

and replace each occurrence with a Stonecutter guard. Use `replace_all: true` with a `grep -n` pass first to confirm count, since Edit's `replace_all` would change every instance at once with the same guard. The simpler approach: switch the import or use a version-guarded type alias at the top of the file.

**Cleaner approach:** add a single guarded constant near the top of `CombatManager.java`:

```java
//? if <=1.21.1 {
private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> SCALE_ATTR =
    net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE;
private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> ATTACK_DAMAGE_ATTR =
    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE;
//?} else
/*
private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> SCALE_ATTR =
    net.minecraft.entity.attribute.EntityAttributes.SCALE;
private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> ATTACK_DAMAGE_ATTR =
    net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE;
*/
```

Then do one-shot search-and-replace:
- `EntityAttributes.GENERIC_SCALE` → `SCALE_ATTR`
- `EntityAttributes.GENERIC_ATTACK_DAMAGE` → `ATTACK_DAMAGE_ATTR`

This concentrates the version-specific code to one place, and the rest of the file becomes version-agnostic.

- [ ] **Step 7.3: Verify 1.21.1 shard still builds**

Run: `./gradlew build 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`.

If it fails with "cannot find symbol" on `SCALE_ATTR` or `ATTACK_DAMAGE_ATTR`, the Stonecutter preprocessor block didn't activate the 1.21.1 branch. Check that `stonecutter active '1.21.1'` is still set and re-verify the preprocessor syntax.

- [ ] **Step 7.4: Commit**

```bash
git add src/main/java/com/crackedgames/craftics/combat/CombatManager.java
git commit -m "combat: guard EntityAttributes references for multi-version"
```

---

## Task 8: Switch to 1.21.4 shard and build

**Why:** 1.21.4 has the cleanest known dep set (all deps have native 1.21.4 builds, no intermediate version stretching). Starting here isolates "Stonecutter version switching works" from "dep matrix for less-clean shards".

**Files:** (none pre-specified — discovery task)

- [ ] **Step 8.1: Switch active shard**

Run: `./gradlew "Set active project to 1.21.4"`

Expected: task succeeds, `stonecutter.gradle` is rewritten to `stonecutter active '1.21.4'`.

Verify: `grep 'stonecutter active' stonecutter.gradle` → `stonecutter active '1.21.4'`.

- [ ] **Step 8.2: Attempt build**

Run: `./gradlew build 2>&1 | tee /tmp/build-1214.log | tail -60`

Expected outcomes (in order of likelihood):
1. **Dependency resolution failure** — wrong version of a transitive. Fix the offending coord in `versions/1.21.4/gradle.properties`, rerun.
2. **Compiler errors from yarn mapping drift** — symbol not found, method signature changed, type incompatible. Add a `//? if <=1.21.1` / `//? else` guard at the offending line, rerun.
3. **Loom/loader version mismatch** — bump `loader_version` in `versions/1.21.4/gradle.properties` to the latest, rerun.
4. **Build succeeds** — proceed to Step 8.3.

- [ ] **Step 8.3: Error-fix-rebuild loop**

For each compile error from Step 8.2:
1. Read the error and line number from `/tmp/build-1214.log`.
2. Open the file at the error location.
3. Decide: is this a rename (e.g., `Foo.BAR` → `Foo.BAZ`), a signature change (e.g., one parameter added), or an outright removal?
4. Wrap the affected code in a `//? if <=1.21.1 { OLD_CODE } else { NEW_CODE }` Stonecutter guard.
5. Rerun `./gradlew build`.
6. Repeat until the build succeeds.

**Do NOT** use broad `try/catch` blocks or reflection fallbacks to "handle" the drift at runtime. Stonecutter's whole purpose is compile-time version branching; runtime handling defeats the point.

- [ ] **Step 8.4: Verify output jar**

Run: `ls -lh build/libs/ && ls versions/1.21.4/build/libs/ 2>/dev/null`

Expected: a jar with a filename containing `1.21.4` or similar shard suffix. Stonecutter typically namespaces shard jars into `versions/<ver>/build/libs/`.

- [ ] **Step 8.5: Commit each guard as its own atomic commit**

For each distinct drift fix:
```bash
git add src/main/java/com/crackedgames/craftics/<file>.java
git commit -m "combat: guard <symbol name> for 1.21.2+ rename"
```

Many small commits are better than one big mystery commit — future version bumps benefit from being able to read the drift history.

---

## Task 9: Switch to 1.21.3 shard and build

**Why:** 1.21.3 sits between 1.21.1 and 1.21.4. Any drift that 1.21.4 required should also apply here (the Stonecutter guards with `<=1.21.1` / `else` should cover this automatically — 1.21.3 takes the `else` branch). If additional 1.21.3-specific issues exist, they're resolved here.

**Files:** (none pre-specified — discovery task)

- [ ] **Step 9.1: Switch active shard**

Run: `./gradlew "Set active project to 1.21.3"` and verify `stonecutter.gradle` flipped.

- [ ] **Step 9.2: Attempt build**

Run: `./gradlew build 2>&1 | tee /tmp/build-1213.log | tail -60`

Most drift should already be guarded by Task 8's work. Additional issues are most likely:
- owo-lib API changes between 1.21.4 and 1.21.3 (backward direction!)
- CCA 6.2.2 vs 6.1.3 differences

- [ ] **Step 9.3: Error-fix-rebuild loop**

Same pattern as Task 8.3. For cases where a three-way version split is needed (e.g., 1.21.1 uses X, 1.21.3 uses Y, 1.21.4+ uses Z), use:

```java
//? if <=1.21.1 {
/*OLD*/
//?} elif <=1.21.3 {
/*MID*/
//?} else
/*NEW*/
```

(Confirm `elif` syntax against current Stonecutter docs during execution — it may be spelled `else if` or similar.)

- [ ] **Step 9.4: Commit guards**

---

## Task 10: Switch to 1.21.5 shard and build

**Why:** 1.21.5 is the furthest from 1.21.1 and the most likely to surface drift the earlier shards missed.

**Files:** (none pre-specified — discovery task)

- [ ] **Step 10.1: Switch active shard**

Run: `./gradlew "Set active project to 1.21.5"` and verify.

- [ ] **Step 10.2: Attempt build**

Run: `./gradlew build 2>&1 | tee /tmp/build-1215.log | tail -80`

Expected additional drift areas (best guesses, confirm against errors):
- Item component API changes
- ParticleTypes renames
- DamageSource construction changes

- [ ] **Step 10.3: Error-fix-rebuild loop**

Same as Tasks 8.3 and 9.3.

- [ ] **Step 10.4: Commit guards**

---

## Task 11: Return to 1.21.1 and confirm it still builds

**Why:** Guards added for 1.21.3/4/5 must not have broken the 1.21.1 path. This is the regression check.

- [ ] **Step 11.1: Switch back to 1.21.1**

Run: `./gradlew "Set active project to 1.21.1"`

- [ ] **Step 11.2: Rebuild**

Run: `./gradlew build 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`.

If not, a guard was written backwards (e.g., `//? if >=1.21.2 { OLD }` when it should be `//? if <=1.21.1 { OLD }`). Find and fix.

- [ ] **Step 11.3: No commit — verification only.**

---

## Task 12: Run chiseledBuild to build all four shards

**Why:** The chiseled task is the whole point — one command, four jars.

- [ ] **Step 12.1: Run chiseledBuild**

Run: `./gradlew chiseledBuild 2>&1 | tail -30`

Expected: all four shards build successfully in sequence, each one taking ~1-2 minutes. Total time ~5-10 minutes.

- [ ] **Step 12.2: List the produced jars**

Run: `find . -name 'craftics-*.jar' -path '*build/libs/*' | sort`

Expected: at least four jars, one per shard, typically under `versions/<ver>/build/libs/`.

- [ ] **Step 12.3: No commit — verification only.**

---

## Task 13: Smoke-test runClient for each shard

**Why:** A successful compile does not mean the mod actually runs. runClient catches runtime init crashes (missing components, mixin target misses, resource loader errors).

**Files:** (none modified)

- [ ] **Step 13.1: runClient on 1.21.1**

Run: `./gradlew "Set active project to 1.21.1" runClient`

In the client:
1. Wait for main menu to load.
2. Create a new world with the "Craftics" preset.
3. Confirm you spawn into the hub without crashes.
4. Exit the client.

Any runtime crash: read the stacktrace, fix with a `//?` guard (likely a mixin target), commit.

- [ ] **Step 13.2: runClient on 1.21.3**

Same as 13.1 but switch shard first: `./gradlew "Set active project to 1.21.3" runClient`.

- [ ] **Step 13.3: runClient on 1.21.4**

Same pattern.

- [ ] **Step 13.4: runClient on 1.21.5**

Same pattern. 1.21.5 is the most likely to surface runtime issues due to being furthest from the original target.

- [ ] **Step 13.5: No commit — verification only. Any fixes during smoke testing were committed as they were made.**

---

## Task 14: Update README with multi-version support info

**Why:** The README still only lists 1.21.1 as the supported version. Update so future contributors know the project targets multiple MC versions and the build process differs.

**Files:**
- Modify: `README.md`

- [ ] **Step 14.1: Update the Requirements section**

In `README.md`, find:

```
- **Minecraft:** 1.21.1
```

Replace with:

```
- **Minecraft:** 1.21.1, 1.21.3, 1.21.4, 1.21.5
```

And find the `### Building from Source` section. Replace:

```bash
git clone <repository-url>
cd Craftics
./gradlew build
```

with:

```bash
git clone <repository-url>
cd Craftics

# Build a single version shard (default active version):
./gradlew build

# Switch active version:
./gradlew "Set active project to 1.21.4"

# Build all version shards in one pass:
./gradlew chiseledBuild
```

- [ ] **Step 14.2: Commit**

```bash
git add README.md
git commit -m "docs: document multi-version build commands"
```

---

## Task 15: Final verification pass

- [ ] **Step 15.1: Clean build for each shard**

Run:
```bash
./gradlew clean
./gradlew chiseledBuild
```

Expected: all four shards build from scratch successfully.

- [ ] **Step 15.2: Print final jar list**

Run: `find . -name 'craftics-*.jar' -path '*build/libs/*' | sort`

Expected: 4+ jars, one per shard.

- [ ] **Step 15.3: Summary report**

Produce a short summary:
- Which shards built
- Which smoke-tested cleanly
- Any known issues or deferred work

This summary gets pasted into the PR body when merging the worktree back to main.

---

## Not in scope (explicit non-goals)

- **NeoForge or Quilt support** — Fabric only.
- **MC 1.21.2** — skipped intentionally (odd release, barely any dep builds).
- **MC 1.21.6+ / 26.1** — requires dep matrix beyond what's verified here; add later if needed.
- **Kotlin DSL conversion** — stays on Groovy unless Task 1 forces otherwise.
- **CI workflow updates** — separate follow-up, not part of this plan.
- **Modrinth / CurseForge publish pipeline for all shards** — separate follow-up.

---

## Rollback plan

This plan runs in the `stonecutter-multi-version` worktree on branch `worktree-stonecutter-multi-version`. If the migration needs to be abandoned:

```bash
git worktree remove .claude/worktrees/stonecutter-multi-version
git branch -D worktree-stonecutter-multi-version
```

The main branch is untouched until the worktree is merged via PR.

---

## Progress / Resume point (paused 2026-04-13)

**Tasks complete: 1 through 7.** Branch `worktree-stonecutter-multi-version`, HEAD `a060e8b`.

| # | Task | Commit |
|---|---|---|
| — | Plan doc added | `9b765ff` |
| — | Pre-migration dep cleanup (GeckoLib + Pehkui removal, stale comments, JDK path) | `cffa31a` |
| 1 | Stonecutter plugin in settings.gradle | `29675e1` |
| 2 | stonecutter.gradle.kts with chiseled task orchestrator | `c7bc5cc` |
| 3 | Per-shard gradle.properties for 1.21.1/3/4/5 + .gitignore fix | `1f754d2` |
| 4 | build.gradle dep coord interpolation | `095b12d` |
| 5 | fabric.mod.json minecraft constraint templating | `8d40c13` |
| 6 | 1.21.1 baseline build verification | (no commit — verify only) |
| 7 | EntityAttributes SCALE/ATTACK_DAMAGE guards in CombatManager.java | `a060e8b` |

**State of each shard at the pause point:**
- `./gradlew :1.21.1:build` → BUILD SUCCESSFUL
- `./gradlew :1.21.3:build`, `:1.21.4:build`, `:1.21.5:build` → not yet attempted; expected to fail with API drift that Tasks 8-10 will fix
- `./gradlew build` (root, all shards) → known to fail on 1.21.5 with at least these symbols: `TypedActionResult`, `GenerationStep.Carver`, `net.minecraft.item.trim.*` package. Tasks 8-10 will discover and guard these. DO NOT run root `build` expecting success until Tasks 8-10 are done.

**Tasks remaining: 8 through 15.** Note the plan's Task 8 currently says to start with 1.21.4; that's still the recommendation (closest drift to 1.21.1, cleanest dep set, best environment to prove the workflow on).

### Important corrections to apply when resuming

The plan was written with some unverified Stonecutter syntax. Tasks 2 and 7 discovered the real API. When resuming, use these verified facts (they supersede the plan body above):

1. **Stonecutter 0.7 removed `registerChiseled` / `stonecutter.chiseled`.** The working pattern is in the committed `stonecutter.gradle.kts`:
   ```kotlin
   tasks.register("chiseledBuild") {
       group = "stonecutter"
       description = "..."
       dependsOn(stonecutter.tasks.named("build"))
   }
   stonecutter tasks {
       order("build")
       order("runClient")
   }
   ```
   `stonecutter.tasks.named("foo")` returns a lazy `MapProperty<String, TaskProvider<Task>>`. `order("foo")` enables `sc.lock` serialization across shards (load-bearing for `runClient` to prevent parallel game-client launches).

2. **Preprocessor comment syntax (Stonecutter 0.7.11, verified via jar bytecode introspection):**
   - Single-line: `//? if <=1.21.1` followed by the next line of code
   - Block opening: `//? if <=1.21.1 {`
   - Block closing: `//?}`
   - Else: `//?} else {`
   - Elif: `//?} elif >1.21.1 {` (token is `ELIF`)
   - Inactive branch wrapped in `/* ... */` block comments so the active branch is always plain code
   - Operators: `=`, `!=`, `<`, `>`, `<=`, `>=`, `~` (same minor), `^` (same major)
   - Version identifier: **none required** — the version on the left side of a comparison is implicit (the active shard's MC version). Write `//? if <=1.21.1`, not `//? if mc <=1.21.1`.
   - See the guarded constants block in `CombatManager.java` (~lines 91-101) for a working example.

3. **`./gradlew build` at the root invokes ALL shards simultaneously.** To build only the active shard, use `./gradlew :<version>:build` (e.g., `./gradlew :1.21.1:build`). The plan's Task 6 wording of "Run `./gradlew build`" is misleading and should be read as `:1.21.1:build`.

4. **WispForest publishes one owo-lib build for the 1.21.2/1.21.3 range** under the `+1.21.2` suffix. The `versions/1.21.3/gradle.properties` intentionally pins `owo_version=0.12.18+1.21.2` — do not "fix" it.

5. **Stonecutter auto-generates `stonecutter.gradle.kts`** (Kotlin DSL) on first run, even in an otherwise-Groovy project. Mixing one .kts file is fine; do not convert it to Groovy. The rest of the build stays Groovy.

### Known 1.21.4 drift discovered during a 2026-04-13 Task 8 attempt (rolled back)

An attempt at Task 8 was made and rolled back because the scope was 3-5x what the plan anticipated. The attempt ran `./gradlew :1.21.4:build`, found 9 initial errors, wrote 4 working guards that fixed those, then hit **35 more errors** underneath. The guards were never committed. The branch is back at `a11ba7b` (plus this updated resume note).

**The plan's estimate for Tasks 8-10 (30-60 min each) is wrong.** Realistic effort: **2-4 hours per shard**, possibly with overlap since many fixes will be shared across 1.21.3/4/5. All three shards together are likely a **full workday** of focused porting, not the "discovery loop" the plan implied.

**Initial 4 working guards (validated, then reverted — rewrite from this list when resuming):**

| File | Drift | Fix |
|---|---|---|
| `src/main/java/.../combat/TrimEffects.java` (lines 11-14) | `net.minecraft.item.trim.*` package moved | Guard imports: `<=1.21.1` uses `net.minecraft.item.trim.*`, else uses `net.minecraft.item.equipment.trim.*` (ArmorTrim, ArmorTrimMaterial, ArmorTrimPattern, ArmorTrimPatterns all renamed via package only) |
| `src/main/java/.../block/LevelSelectBlock.java` (lines 14, 30) | `net.minecraft.state.property.DirectionProperty` removed | Guard both the import and the `FACING` field declaration. Else branch: import `net.minecraft.state.property.EnumProperty`, declare as `EnumProperty<Direction> FACING`. The `Direction` import is already present. |
| `src/main/java/.../item/GuideBookItem.java` (line 7, lines 22-28) | `TypedActionResult<ItemStack>` consolidated into `ActionResult` | Add unconditional `import net.minecraft.util.ActionResult;`. Guard `TypedActionResult` import. Guard `use()` method: else branch returns `ActionResult` (not generic), body returns `ActionResult.SUCCESS`, `ItemStack` import becomes unused in else branch (unused-import warning only, not error). |
| `src/main/java/.../world/VoidChunkGenerator.java` (line 19, lines 45-50) | `ChunkGenerator.carve()` dropped the `GenerationStep.Carver carverStep` parameter | Guard `GenerationStep` import. Guard `carve()` method signature: else branch takes 6 params instead of 7, dropping the last one. Method body is identical (empty for void world). |

**Additional drift that surfaced AFTER those 4 fixes (uncommitted research — all in 1.21.2+):**

Symbol drift (affecting `CombatManager.java`, `TrimEffects.java`, `CrafticsMod.java`, `ArenaBuilder.java`, `ItemUseHandler.java`, `PlayerEntityMixin.java`, `VoidChunkGenerator.java`, and 4 network payload files):

- **`ArmorTrim.getPattern()` / `getMaterial()`** → record accessors `pattern()` / `material()` (used in TrimEffects line 88, 97 and CombatManager line 12424)
- **`DynamicRegistryManager.get(RegistryKey<Registry<T>>)`** → signature changed; likely `getOrThrow()` or a different overload. Affects 5+ call sites in CombatManager, ArenaBuilder (any `world.getRegistryManager().get(RegistryKeys.ENCHANTMENT)` etc.)
- **`Entity.teleport(ServerWorld, double, double, double, Set<Object>, float, float)`** → signature changed (at least 5 call sites in CombatManager, CrafticsMod). Unclear what the new form is — needs yarn 1.21.4 docs check on `Entity.teleport`.
- **`World.getGameRules()`** → removed from `World` interface. Check if it moved to `ServerWorld` or requires a different access pattern. Affects `PlayerEntityMixin.java` lines 34, 45.
- **`World.getTopY(...)`** → signature changed. Affects `ArenaBuilder.java` line 1666.
- **`World.playSound(...)`** → signature changed. Affects `ItemUseHandler.java` line 433. Error message mentioned `Reference<SoundEvent>` vs some other type.
- **`WorldChunk.setNeedsSaving(boolean)`** → method renamed. Affects `ArenaBuilder.java` line 1721.
- **`FallingBlockEntity.create(ServerWorld)` or similar `.create()` factory** → signature changed. Affects `CombatManager.java` line 7253.
- **`Vector3f` vs `int` mismatch** → some particle or math call argument type changed. Affects `CombatManager.java` line 10594.
- **`GenerationStep.Carver` additionally removes a declared method override** → `VoidChunkGenerator` should override `appendDebugHudText` instead of `getDebugHudText` in 1.21.2+ (method renamed on `ChunkGenerator`). Line 108.
- **4 network payload files** (`ExitCombatPayload`, `LoadingScreenPayload`, `PostLevelChoicePayload`, `VictoryChoicePayload`) have unknown "cannot find symbol" errors — likely `PacketByteBuf` / codec API changes. Needs investigation.
- **`CrafticsMod.java` lines 106-146** have 6 "cannot find symbol" errors of unknown cause — investigate before touching.

Many of these are shared across 1.21.3/4/5 (any change introduced in 1.21.2 persists in all later shards), so guards written for 1.21.4 should mostly satisfy 1.21.3 and 1.21.5 too. 1.21.5 will likely surface some additional drift on top.

**Suggested resumption strategy for Tasks 8-10 (when there's time):**

1. Start a single dedicated session; plan 2-4 hours minimum.
2. Re-apply the 4 validated guards from the table above as "Task 8 batch 1". Commit.
3. Fix drift in categories, not files — pick one drift category (e.g., `DynamicRegistryManager.get` replacement), research the 1.21.4 signature once, grep for all call sites, guard them all, commit as "batch 2". Repeat per category.
4. Don't bounce between shards. Stay on 1.21.4 until clean, THEN switch to 1.21.3 (should be a near-no-op if guards are correctly written with `<=1.21.1 / else`), then 1.21.5 (expect some additional unique drift).
5. Skip the dispatched-subagent review ceremony for the drift-fix loop — it's mechanical error-driven work, inline execution is much faster.

### What to do before resuming

1. `cd .claude/worktrees/stonecutter-multi-version` (or re-enter the worktree)
2. `git status` — should be empty
3. `grep 'stonecutter active' stonecutter.gradle.kts` — confirm 1.21.1 is the active shard
4. `./gradlew :1.21.1:build` — confirm baseline still works
5. Jump straight to Task 8 (start with 1.21.4)

### Orphaned state outside the worktree

The main worktree (`d:/_My Projects/Craftics/`) has 4 uncommitted files modified from this session's early exploratory work:
- `build.gradle`, `gradle.properties`, `src/main/java/.../CombatManager.java`, `src/main/resources/fabric.mod.json`

These edits were the dep cleanup and JDK path fix, which are **already committed in this worktree branch as `cffa31a`**. They are redundant in main and safe to `git restore` whenever someone gets back to main. They are not being actively carried forward.
