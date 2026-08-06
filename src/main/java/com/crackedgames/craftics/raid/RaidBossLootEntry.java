package com.crackedgames.craftics.raid;

/** One weighted row of a raid boss's loot table. Item ids stay as strings so the
 *  parser never touches the item registry. */
public record RaidBossLootEntry(String itemId, int weight, int min, int max) {}
