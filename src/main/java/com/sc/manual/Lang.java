package com.sc.manual;

import net.minecraft.util.StatCollector;

/**
 * Thin wrapper over the vanilla translator so every user-visible string in the mod - GUI
 * labels, machine status lines and the whole handbook - resolves through the .lang files and
 * therefore follows the player's chosen language (en_US and ru_RU both ship with the mod).
 */
public final class Lang {

    private Lang() {
    }

    public static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    /**
     * Translation with an explicit fallback, for text that already has a perfectly good English
     * source in code (a MachineType's displayName, say). A missing key then shows that instead
     * of a raw "sc.machine.crusher" leaking into the GUI.
     */
    public static String trOr(String key, String fallback) {
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : fallback;
    }
}
