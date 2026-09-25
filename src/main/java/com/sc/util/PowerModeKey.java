package com.sc.util;

/**
 * Keys for the suit's power mode (armour screen, "Mode" tab): step to the next mode, or go straight
 * to one - pressed again while already in economy / combat, it goes back to normal.
 */
public enum PowerModeKey {

    CYCLE, ECONOMY, NORMAL, COMBAT;

    /** The mode this key selects (0 economy, 1 normal, 2 combat), or -1 for "next". */
    public int mode() {
        return ordinal() - 1;
    }

    /** The mode after pressing this key in `current`. */
    public int next(int current) {
        if (this == CYCLE) {
            return (current + 1) % 3;
        }
        return mode() == current && current != 1 ? 1 : mode();
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static PowerModeKey of(int ordinal) {
        PowerModeKey[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }
}
