package com.crackedgames.craftics.combat.ai.ally;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A pet that flies goes over obstacles instead of round them.
 *
 * <p>That is a parrot by nature, and any pet at all while its owner carries a Gravitite
 * Shovel. Before this, flight was only how a mob was drawn: a parrot hovered, and then walked
 * the long way round a wall like a wolf.
 */
class AllyFlightTest {

    /** 7 wide, 3 deep, open floor, with a wall across the whole of column 3. */
    private static GridArena walledArena() {
        GridTile[][] tiles = new GridTile[7][3];
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 3; z++) {
                tiles[x][z] = new GridTile(x == 3 ? TileType.OBSTACLE : TileType.NORMAL, null);
            }
        }
        return new GridArena(7, 3, tiles, BlockPos.ORIGIN, 1, new GridPos(0, 0));
    }

    /** A pet with Speed 6, standing on the west side of the wall. */
    private static CombatEntity pet(String type) {
        return new CombatEntity(5, type, new GridPos(1, 1), 10, 3, 0, 1, 1, 6);
    }

    @Test
    void aWalkingPetCannotCrossAWall() {
        CombatEntity wolf = pet("minecraft:wolf");

        assertTrue(AllyTargeting.pathTo(wolf, walledArena(), new GridPos(5, 1)).isEmpty());
    }

    @Test
    void aParrotFliesOverIt() {
        CombatEntity parrot = pet("minecraft:parrot");

        List<GridPos> path = AllyTargeting.pathTo(parrot, walledArena(), new GridPos(5, 1));

        assertFalse(path.isEmpty(), "a parrot should not need a way round");
        assertEquals(new GridPos(5, 1), path.get(path.size() - 1));
        assertTrue(path.stream().anyMatch(step -> step.x() == 3), "it goes over the wall");
    }

    @Test
    void aPetGivenFlightFliesOverItToo() {
        // What a Gravitite Shovel in its owner's pack does for a wolf.
        CombatEntity wolf = pet("minecraft:wolf");
        wolf.setGrantedFlight(true);

        List<GridPos> path = AllyTargeting.pathTo(wolf, walledArena(), new GridPos(5, 1));

        assertFalse(path.isEmpty());
        assertEquals(new GridPos(5, 1), path.get(path.size() - 1));
    }

    @Test
    void flightIsGoneAgainWhenTheShovelIs() {
        CombatEntity wolf = pet("minecraft:wolf");
        wolf.setGrantedFlight(true);
        wolf.setGrantedFlight(false);

        assertFalse(wolf.isFlying());
        assertTrue(AllyTargeting.pathTo(wolf, walledArena(), new GridPos(5, 1)).isEmpty());
    }

    @Test
    void aFlyingPetClosesOnAnEnemyBehindAWall() {
        CombatEntity parrot = pet("minecraft:parrot");
        GridArena arena = walledArena();
        CombatEntity zombie = new CombatEntity(9, "minecraft:zombie", new GridPos(6, 1), 20, 3, 0, 1, 1, 1);
        assertTrue(arena.placeEntity(zombie));

        // advance() is what every ally AI ends in: move toward the target, strike if it reaches.
        var action = AllyTargeting.advance(parrot, arena, zombie);

        var strike = assertInstanceOf(
            com.crackedgames.craftics.combat.ai.EnemyAction.MoveAndAttackMob.class, action);
        assertEquals(zombie.getEntityId(), strike.targetEntityId());
        assertFalse(strike.path().isEmpty(), "it had to cross the wall to get there");
    }
}
