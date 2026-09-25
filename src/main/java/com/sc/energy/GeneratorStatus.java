package com.sc.energy;

import com.sc.manual.Lang;

/** Generator counterpart to MachineStatus (§15/§18.2) - see that enum for why this isn't a String. */
public enum GeneratorStatus {

    IDLE,
    GENERATING,
    BUFFER_FULL,
    NO_FUEL,
    NO_SUNLIGHT,
    IGNITING,
    BLANKET_DEPLETED,
    NO_BLANKET,
    NO_DEUTERIUM;

    public String localized() {
        return Lang.tr("sc.status.generator." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public static GeneratorStatus byOrdinal(int ordinal) {
        GeneratorStatus[] values = values();
        return values[ordinal >= 0 && ordinal < values.length ? ordinal : 0];
    }
}
