package com.crackedgames.craftics.combat;

public enum CombatPhase {
    PLAYER_TURN,
    ENEMY_TURN,
    ANIMATING,
    REACTING, // enemy fleeing mid-player-turn (e.g. passive mob hit)
    PLAYER_DYING, // death animation before game over
    GAME_OVER_FLIP, // holding while clients animate the coin-flip loss reveal
    LEVEL_COMPLETE,
    GAME_OVER
}
