package com.crackedgames.craftics.combat;

import java.util.Map;

/**
 * Single source of truth for goat-horn variant identification and instrument
 * lookup. Keeps Minecraft's vanilla {@code INSTRUMENT} data component access
 * (which has minor API drift across 1.21.1-1.21.5) isolated to one place.
 *
 * <p>Variant ids ({@code "ponder"}, {@code "sing"}, ...) match the strings
 * used by {@link GoatHornEffects} to dispatch combat effects.
 */
public final class HornVariants {

    private HornVariants() {}

    /** Vanilla goat-horn instrument identifier path -> our variant id. */
    private static final Map<String, String> INSTRUMENT_TO_VARIANT = Map.of(
        "ponder_goat_horn", "ponder",
        "sing_goat_horn",   "sing",
        "seek_goat_horn",   "seek",
        "feel_goat_horn",   "feel",
        "admire_goat_horn", "admire",
        "call_goat_horn",   "call",
        "yearn_goat_horn",  "yearn",
        "dream_goat_horn",  "dream"
    );

    /** Reverse map: our variant id -> vanilla instrument identifier path. */
    private static final Map<String, String> VARIANT_TO_INSTRUMENT = Map.of(
        "ponder", "ponder_goat_horn",
        "sing",   "sing_goat_horn",
        "seek",   "seek_goat_horn",
        "feel",   "feel_goat_horn",
        "admire", "admire_goat_horn",
        "call",   "call_goat_horn",
        "yearn",  "yearn_goat_horn",
        "dream",  "dream_goat_horn"
    );

    /** Variant id -> index in vanilla {@code SoundEvents.GOAT_HORN_SOUNDS}.
     *  Order matches vanilla {@code SoundEvents.registerGoatHornSounds()} —
     *  ponder, sing, seek, feel, admire, call, yearn, dream — and has been
     *  stable since goat horns were added. */
    private static final Map<String, Integer> VARIANT_TO_SOUND_INDEX = Map.of(
        "ponder", 0,
        "sing",   1,
        "seek",   2,
        "feel",   3,
        "admire", 4,
        "call",   5,
        "yearn",  6,
        "dream",  7
    );

    /** Look up the variant id for a vanilla instrument identifier path. */
    public static String variantForInstrumentPath(String path) {
        if (path == null) return null;
        return INSTRUMENT_TO_VARIANT.get(path);
    }

    /** Look up the vanilla instrument identifier path for a variant id. */
    public static String instrumentPathForVariant(String variantId) {
        if (variantId == null) return null;
        return VARIANT_TO_INSTRUMENT.get(variantId);
    }

    /**
     * Read the variant id from a goat horn ItemStack. Tries the vanilla
     * {@code INSTRUMENT} component first (works for any horn from any source),
     * then falls back to the legacy custom-name path for stacks created
     * before this overhaul.
     *
     * @return variant id, or null if this is not a recognized horn
     */
    public static String readVariant(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.getItem() != net.minecraft.item.Items.GOAT_HORN) return null;

        var entry = stack.get(net.minecraft.component.DataComponentTypes.INSTRUMENT);
        if (entry != null) {
            //? if <=1.21.4 {
            String variant = entry.getKey()
                .map(key -> variantForInstrumentPath(key.getValue().getPath()))
                .orElse(null);
            //?} else {
            /*String variant = entry.instrument().getKey()
                .map(key -> variantForInstrumentPath(key.getValue().getPath()))
                .orElse(null);*/
            //?}
            if (variant != null) return variant;
        }

        var name = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        if (name != null) {
            String stripped = name.getString().replaceAll("§.", "");
            return legacyNameToVariant(stripped);
        }

        return null;
    }

    /** Pre-overhaul horns had a colored display name like "§7Ponder Horn".
     *  Strip color codes, take the first word, lowercase it. */
    private static String legacyNameToVariant(String stripped) {
        if (stripped == null) return null;
        String s = stripped.trim();
        int space = s.indexOf(' ');
        if (space <= 0) return null;
        String first = s.substring(0, space).toLowerCase(java.util.Locale.ROOT);
        return INSTRUMENT_TO_VARIANT.containsValue(first) ? first : null;
    }

    /**
     * Set the {@code INSTRUMENT} data component on a goat horn ItemStack so
     * it identifies as the given variant. Silently no-ops if the variant id
     * is unrecognized or the instrument registry entry is missing.
     */
    public static void writeVariant(net.minecraft.item.ItemStack stack,
                                    String variantId,
                                    net.minecraft.registry.DynamicRegistryManager registries) {
        String path = instrumentPathForVariant(variantId);
        if (path == null) return;
        //? if <=1.21.1 {
        var instrumentRegistry = registries.get(net.minecraft.registry.RegistryKeys.INSTRUMENT);
        //?} else {
        /*var instrumentRegistry = registries.getOrThrow(net.minecraft.registry.RegistryKeys.INSTRUMENT);*/
        //?}
        var entryOpt = instrumentRegistry.getEntry(net.minecraft.util.Identifier.of("minecraft", path));
        if (entryOpt.isEmpty()) return;
        //? if <=1.21.4 {
        stack.set(net.minecraft.component.DataComponentTypes.INSTRUMENT, entryOpt.get());
        //?} else {
        /*stack.set(net.minecraft.component.DataComponentTypes.INSTRUMENT,
            new net.minecraft.component.type.InstrumentComponent(entryOpt.get()));*/
        //?}
    }

    /**
     * Look up the {@link net.minecraft.sound.SoundEvent} associated with the
     * stack's instrument variant. Uses the vanilla
     * {@code SoundEvents.GOAT_HORN_SOUNDS} list indexed by our known variant
     * order — version-independent, doesn't need registry access.
     */
    public static java.util.Optional<net.minecraft.sound.SoundEvent> soundFor(net.minecraft.item.ItemStack stack) {
        return soundForVariant(readVariant(stack));
    }

    /** Same as {@link #soundFor} but takes a variant id directly — useful at
     *  call sites that already resolved the variant and don't carry the stack. */
    public static java.util.Optional<net.minecraft.sound.SoundEvent> soundForVariant(String variantId) {
        if (variantId == null) return java.util.Optional.empty();
        Integer idx = VARIANT_TO_SOUND_INDEX.get(variantId);
        if (idx == null) return java.util.Optional.empty();
        var sounds = net.minecraft.sound.SoundEvents.GOAT_HORN_SOUNDS;
        if (idx < 0 || idx >= sounds.size()) return java.util.Optional.empty();
        return java.util.Optional.of(sounds.get(idx).value());
    }
}
