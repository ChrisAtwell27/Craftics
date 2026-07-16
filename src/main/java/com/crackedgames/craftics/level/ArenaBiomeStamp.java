package com.crackedgames.craftics.level;

/**
 * The biome identity an arena was built for, used to validate the per-level arena cache.
 *
 * <p>Arenas are cached in save data keyed by level number alone and validated by this stamp, so
 * the stamp has to name the arena that was actually BUILT. The stamp used to be taken straight
 * from {@code BiomeTemplate.biomeId}, which ignores a level's arena override: the Pale Garden
 * (a {@code "forest/pale_garden"} sub-biome at forest's midpoint) was therefore stamped as plain
 * {@code "forest"}. On re-entry the check read {@code "forest".equals("forest")}, hit the cache,
 * and reused a scan of the stale plain-forest blocks, so the player fought Creakings standing in
 * an ordinary Dark Forest arena and {@code pale_garden.schem} was never loaded.
 *
 * <p>Kept free of Minecraft types so the cache rules stay unit-testable.
 */
public final class ArenaBiomeStamp {

    private ArenaBiomeStamp() {}

    /**
     * The biome id an arena for this level is actually built from: the level's arena override
     * when it has one, otherwise the biome the registry resolves for that level.
     *
     * @param registryBiomeId the {@code BiomeTemplate.biomeId} for the level
     * @param arenaBiomeOverride the level definition's arena override, or null/blank if none
     */
    public static String effectiveBiomeId(String registryBiomeId, String arenaBiomeOverride) {
        if (arenaBiomeOverride != null && !arenaBiomeOverride.isBlank()) {
            return arenaBiomeOverride;
        }
        return registryBiomeId;
    }

    /**
     * Whether a cached arena stamped {@code cachedStamp} may be reused for a level that now
     * wants {@code wantedStamp}.
     *
     * <p>A null cached stamp means the save predates stamping. Those are trusted rather than
     * invalidated, so an update doesn't force a mass rebuild of every arena in every save; they
     * self-correct the first time each level is rebuilt for any other reason.
     */
    public static boolean stampMatches(String cachedStamp, String wantedStamp) {
        if (cachedStamp == null) return true;
        return cachedStamp.equals(wantedStamp);
    }
}
