package com.sc.machine;

import com.sc.manual.Lang;

/**
 * §13's "поведение при ошибках" status line, as a value instead of a free-text English string.
 * Two reasons: a String can't ride the Container's int field sync (so the old status text never
 * updated on the client at all), and a key per state is what makes the line translatable.
 */
public enum MachineStatus {

    IDLE,
    PROCESSING,
    NO_POWER,
    OUTPUT_FULL,
    OVERHEATED,
    /** The power switch is off: no energy taken, no work. */
    DISABLED,
    /** Held by its redstone mode (a signal wanted, or one there that shouldn't be). */
    REDSTONE,
    /** The induction furnace keeping itself warm with nothing to smelt. */
    HEATING;

    public String localized() {
        return Lang.tr("sc.status.machine." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public static MachineStatus byOrdinal(int ordinal) {
        MachineStatus[] values = values();
        return values[ordinal >= 0 && ordinal < values.length ? ordinal : 0];
    }
}
