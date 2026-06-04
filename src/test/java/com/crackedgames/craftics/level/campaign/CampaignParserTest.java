package com.crackedgames.craftics.level.campaign;

import com.crackedgames.craftics.data.CampaignJsonLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the static {@link CampaignJsonLoader#parse(Identifier, JsonObject)} parser in
 * isolation — Gson + Identifier only, no Minecraft bootstrap (mirrors
 * {@code DialogueJsonLoaderTest}).
 */
class CampaignParserTest {

    private static JsonObject json(String s) {
        return new Gson().fromJson(s, JsonObject.class);
    }

    private final Identifier fid = Identifier.of("craftics", "campaigns/x");

    private Campaign parse(String s) {
        return CampaignJsonLoader.parseCampaign(fid, json(s));
    }

    @Test
    void parsesFullFormWithBranch() {
        Campaign c = parse("""
            {
              "id": "myaddon:descent",
              "display_name": "The Descent",
              "regions": [
                {
                  "id": "surface",
                  "display_name": "Surface",
                  "color": "§a",
                  "icon": "☘",
                  "map_color": "FF6BBF59",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood", "label": "The Wildwood" },
                    { "biome": "marsh" }
                  ]
                },
                {
                  "id": "depths",
                  "nodes": [
                    { "biome": "caverns" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": ["wildwood", "marsh"]
              }
            }""");

        assertNotNull(c);
        assertEquals("myaddon:descent", c.id());
        assertEquals("The Descent", c.displayName());
        assertEquals(2, c.regions().size());

        CampaignRegion surface = c.regions().get(0);
        assertEquals("surface", surface.id());
        assertEquals("Surface", surface.displayName());
        assertEquals("§a", surface.color());
        assertEquals("☘", surface.icon());
        assertEquals(0xFF6BBF59, surface.mapColor());

        // Node biome ids in declared order.
        assertEquals(List.of("village", "wildwood", "marsh"),
            surface.nodes().stream().map(CampaignNode::biomeId).toList());

        // A node's label override round-trips.
        assertEquals("The Wildwood", c.nodeOf("wildwood").labelOverride());

        // Branch references real biomes, so it is valid and the swap takes effect.
        assertTrue(c.isBranchValid());
        assertEquals(List.of("village", "marsh", "wildwood", "caverns"), c.orderedBiomeIds(1));
        assertEquals(List.of("village", "wildwood", "marsh", "caverns"), c.orderedBiomeIds(0));
    }

    @Test
    void parsesLinearFormWithoutBranch() {
        Campaign c = parse("""
            {
              "id": "myaddon:linear",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void labelOverrideRoundTrips() {
        Campaign c = parse("""
            {
              "id": "myaddon:labels",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "wildwood", "label": "The Wildwood" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals("The Wildwood", c.nodeOf("wildwood").labelOverride());
    }

    @Test
    void missingOptionalRegionFieldsDefaultSanely() {
        Campaign c = parse("""
            {
              "id": "myaddon:defaults",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        CampaignRegion region = c.regions().get(0);
        assertEquals("surface", region.displayName());
        assertEquals("§f", region.color());
        assertEquals("?", region.icon());
        assertEquals(0xFFFFFFFF, region.mapColor());
    }

    @Test
    void malformedMapColorDefaultsToWhite() {
        Campaign c = parse("""
            {
              "id": "myaddon:badcolor",
              "regions": [
                {
                  "id": "surface",
                  "map_color": "ZZZ",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(0xFFFFFFFF, c.regions().get(0).mapColor());
    }

    @Test
    void mapColorRoundTripsAsArgbInt() {
        Campaign c = parse("""
            {
              "id": "myaddon:color",
              "regions": [
                {
                  "id": "surface",
                  "map_color": "FF6BBF59",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(0xFF6BBF59, c.regions().get(0).mapColor());
    }

    @Test
    void sixDigitMapColorGetsOpaqueAlpha() {
        Campaign c = parse("""
            {
              "id": "myaddon:rgb",
              "regions": [
                {
                  "id": "surface",
                  "map_color": "6BBF59",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(0xFF6BBF59, c.regions().get(0).mapColor());
    }

    @Test
    void missingIdReturnsNull() {
        assertNull(parse("""
            {
              "regions": [
                { "id": "surface", "nodes": [ { "biome": "village" } ] }
              ]
            }"""));
    }

    @Test
    void blankIdReturnsNull() {
        assertNull(parse("""
            {
              "id": "   ",
              "regions": [
                { "id": "surface", "nodes": [ { "biome": "village" } ] }
              ]
            }"""));
    }

    @Test
    void missingRegionsReturnsNull() {
        assertNull(parse("{ \"id\": \"myaddon:noregions\" }"));
    }

    @Test
    void emptyRegionsReturnsNull() {
        assertNull(parse("{ \"id\": \"myaddon:emptyregions\", \"regions\": [] }"));
    }

    @Test
    void missingNodeBiomeReturnsNull() {
        // Policy: a node without a 'biome' is meaningless, so the whole campaign is skipped.
        assertNull(parse("""
            {
              "id": "myaddon:badnode",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "label": "no biome here" }
                  ]
                }
              ]
            }"""));
    }

    @Test
    void emptyNodesReturnsNull() {
        assertNull(parse("""
            {
              "id": "myaddon:emptynodes",
              "regions": [
                { "id": "surface", "nodes": [] }
              ]
            }"""));
    }

    @Test
    void malformedBranchFallsBackToLinear() {
        // swap not length-2 -> branch ignored, campaign still parses (linear).
        Campaign c = parse("""
            {
              "id": "myaddon:badbranch",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": ["wildwood"]
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    // --- Wrong-type and edge cases (FIX 1-3 hardening) ---

    @Test
    void objectDisplayNameFallsBackToIdInsteadOfThrowing() {
        // "display_name": {} is an author typo; getAsString() would throw on a JsonObject.
        // After the optString primitive guard it reads as absent and falls back to the id.
        Campaign c = parse("""
            {
              "id": "myaddon:objname",
              "display_name": {},
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals("myaddon:objname", c.displayName());
    }

    @Test
    void arrayIdReturnsNullInsteadOfThrowing() {
        // "id": [1,2] is an array; pre-fix getAsString() throws IllegalStateException and
        // escapes the parser. After FIX 1 the id reads as absent -> clean missing-id skip.
        assertNull(parse("""
            {
              "id": [1, 2],
              "regions": [
                { "id": "surface", "nodes": [ { "biome": "village" } ] }
              ]
            }"""));
    }

    @Test
    void arrayLabelOnNodeIsIgnoredInsteadOfThrowing() {
        // "label": ["a"] is an array; pre-fix getAsString() throws and escapes the parser.
        // After FIX 1 the label reads as absent (no override) and the campaign parses.
        Campaign c = parse("""
            {
              "id": "myaddon:arrlabel",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "wildwood", "label": ["a"] }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertNull(c.nodeOf("wildwood").labelOverride());
    }

    @Test
    void regionsNotAnArrayReturnsNull() {
        assertNull(parse("""
            {
              "id": "myaddon:badregions",
              "regions": "not an array"
            }"""));
    }

    @Test
    void nodesObjectNotArrayReturnsNull() {
        assertNull(parse("""
            {
              "id": "myaddon:badnodes",
              "regions": [
                { "id": "surface", "nodes": {} }
              ]
            }"""));
    }

    @Test
    void objectBranchRegionFallsBackToLinear() {
        // "region": {} reads as absent -> branch missing region -> ignored (linear).
        Campaign c = parse("""
            {
              "id": "myaddon:objbranchregion",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood" }
                  ]
                }
              ],
              "branch": {
                "region": {},
                "swap": ["village", "wildwood"]
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void stringSwapFallsBackToLinear() {
        // "swap": "notanarray" -> branch missing swap array -> ignored (linear).
        Campaign c = parse("""
            {
              "id": "myaddon:strswap",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": "notanarray"
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void hashPrefixedMapColorParses() {
        Campaign c = parse("""
            {
              "id": "myaddon:hashcolor",
              "regions": [
                {
                  "id": "surface",
                  "map_color": "#6BBF59",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(0xFF6BBF59, c.regions().get(0).mapColor());
    }

    @Test
    void whitespaceMapColorIsTrimmed() {
        Campaign c = parse("""
            {
              "id": "myaddon:wscolor",
              "regions": [
                {
                  "id": "surface",
                  "map_color": "  FF6BBF59  ",
                  "nodes": [
                    { "biome": "village" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(0xFF6BBF59, c.regions().get(0).mapColor());
    }

    @Test
    void duplicateRegionIdStillParsesFirstWins() {
        // Two regions share the id "surface". The campaign still parses; all nodes from both
        // regions are counted, and lookups resolve (first occurrence wins for region/node maps).
        Campaign c = parse("""
            {
              "id": "myaddon:dupregion",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" }
                  ]
                },
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "wildwood" }
                  ]
                }
              ]
            }""");

        assertNotNull(c);
        assertEquals(2, c.totalBiomeCount());
        assertNotNull(c.regionOf("village"));
        assertNotNull(c.regionOf("wildwood"));
        assertEquals("surface", c.regionOf("village").id());
    }

    // --- Segment-form swap parsing (two arrays of strings) ---

    @Test
    void segmentSwapFormParsesAndSwapsContiguousBlocks() {
        // "swap" as two arrays: [desert,jungle,forest] <-> [snowy,mountain] around the river pivot.
        Campaign c = parse("""
            {
              "id": "myaddon:overworld",
              "regions": [
                {
                  "id": "overworld",
                  "nodes": [
                    { "biome": "plains" },
                    { "biome": "desert" },
                    { "biome": "jungle" },
                    { "biome": "forest" },
                    { "biome": "river" },
                    { "biome": "snowy" },
                    { "biome": "mountain" },
                    { "biome": "cave" },
                    { "biome": "deep_dark" }
                  ]
                }
              ],
              "branch": {
                "region": "overworld",
                "swap": [["desert", "jungle", "forest"], ["snowy", "mountain"]]
              }
            }""");

        assertNotNull(c);
        assertTrue(c.isBranchValid());
        assertEquals(List.of("plains", "desert", "jungle", "forest", "river", "snowy", "mountain", "cave", "deep_dark"),
            c.orderedBiomeIds(0));
        assertEquals(List.of("plains", "snowy", "mountain", "river", "desert", "jungle", "forest", "cave", "deep_dark"),
            c.orderedBiomeIds(1));
    }

    @Test
    void malformedSegmentSwapMixedFormFallsBackToLinear() {
        // One element is an array, the other a string -> malformed mixed form -> linear.
        Campaign c = parse("""
            {
              "id": "myaddon:mixedswap",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "a" },
                    { "biome": "b" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": [["a"], "b"]
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void emptySegmentSwapFallsBackToLinear() {
        // An empty segment array -> malformed -> linear.
        Campaign c = parse("""
            {
              "id": "myaddon:emptyseg",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "a" },
                    { "biome": "b" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": [[], ["b"]]
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void nonStringEntryInSegmentFallsBackToLinear() {
        // A segment array holding a non-string entry -> malformed -> linear.
        Campaign c = parse("""
            {
              "id": "myaddon:nonstrseg",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "a" },
                    { "biome": "b" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": [["a", 42], ["b"]]
              }
            }""");

        assertNotNull(c);
        assertNull(c.branch());
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }

    @Test
    void branchNamingBiomeNotInRegionIsInvalidLinearFallback() {
        // The branch's region exists but names a biome ("marsh") that is not a node of it,
        // so the branch is invalid and the swap choice yields the same linear order.
        Campaign c = parse("""
            {
              "id": "myaddon:badbranchbiome",
              "regions": [
                {
                  "id": "surface",
                  "nodes": [
                    { "biome": "village" },
                    { "biome": "wildwood" }
                  ]
                }
              ],
              "branch": {
                "region": "surface",
                "swap": ["wildwood", "marsh"]
              }
            }""");

        assertNotNull(c);
        assertFalse(c.isBranchValid());
        assertEquals(c.orderedBiomeIds(0), c.orderedBiomeIds(1));
    }
}
