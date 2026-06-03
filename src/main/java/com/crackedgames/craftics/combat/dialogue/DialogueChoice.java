package com.crackedgames.craftics.combat.dialogue;

/** One selectable choice at the end of a dialogue. {@code action} is a named key
 *  resolved by {@code DialogueActions} on the server. */
public record DialogueChoice(String label, String action) {}
