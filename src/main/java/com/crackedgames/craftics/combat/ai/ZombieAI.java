package com.crackedgames.craftics.combat.ai;

/**
 * Zombie AI: Relentless horde fighter. Always moves + attacks in the same turn.
 * - HORDE BONUS: +1 attack power for each adjacent allied zombie/husk/drowned
 * - Beelines toward player with full speed, attacks on arrival
 * - Never retreats, never wastes a turn — always advancing
 *
 * The plain member of the {@link UndeadHordeAI} family (also reused by the
 * evoker's summoned vex).
 */
public class ZombieAI extends UndeadHordeAI {
}
