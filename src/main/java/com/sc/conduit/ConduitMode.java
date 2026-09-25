package com.sc.conduit;

/**
 * What one conduit does on one side of its bundle (Ender IO's connection modes).
 *
 * NORMAL: the default. Between conduits - connected. At a block it is the kind's default: a cable
 * both takes energy from and gives it to the block ("in / out"), a pipe or tube delivers into it
 * ("insert").
 * EXTRACT: takes out of the block only (energy, fluid, items).
 * INSERT: puts into the block only.
 * OFF: not connected at all.
 * BOTH: takes out of the block and puts into it (a cable's NORMAL; for pipes and tubes a mode of its own).
 * Ordinals are saved in the world - only ever append.
 */
public enum ConduitMode {

    NORMAL, EXTRACT, INSERT, OFF, BOTH;

    public static ConduitMode of(int ordinal) {
        ConduitMode[] v = values();
        return v[ordinal >= 0 && ordinal < v.length ? ordinal : 0];
    }

    /** The modes the connector menu's arrows step through (and a wrench cycles), in Ender IO's order. */
    public static ConduitMode[] choices(ConduitKind kind, boolean connector) {
        if (!connector) {
            return new ConduitMode[]{NORMAL, OFF};
        }
        return kind == ConduitKind.CABLE
                ? new ConduitMode[]{NORMAL, EXTRACT, INSERT, OFF}      // in/out, extract, insert, off
                : new ConduitMode[]{NORMAL, EXTRACT, BOTH, OFF};       // insert, extract, in/out, off
    }

    /** What this mode is on a connector of that kind, as the menu shows it (NORMAL / INSERT / BOTH folded together). */
    public ConduitMode canonical(ConduitKind kind) {
        if (this == BOTH && kind == ConduitKind.CABLE) {
            return NORMAL;
        }
        if (this == INSERT && kind != ConduitKind.CABLE) {
            return NORMAL;
        }
        return this;
    }

    /** Next (+1) or previous (-1) choice. */
    public ConduitMode step(ConduitKind kind, boolean connector, int delta) {
        ConduitMode[] c = choices(kind, connector);
        ConduitMode here = connector ? canonical(kind) : (this == OFF ? OFF : NORMAL);
        int i = 0;
        for (int k = 0; k < c.length; k++) {
            if (c[k] == here) {
                i = k;
            }
        }
        return c[((i + delta) % c.length + c.length) % c.length];
    }

    /** The next mode a wrench click gives. */
    public ConduitMode next(ConduitKind kind, boolean connector) {
        return step(kind, connector, 1);
    }

    /** Takes out of the block on that side. */
    public boolean extracts(ConduitKind kind) {
        if (kind == ConduitKind.CABLE) {
            return this == NORMAL || this == BOTH || this == EXTRACT;
        }
        return this == EXTRACT || this == BOTH;
    }

    /** Puts into the block on that side. */
    public boolean inserts(ConduitKind kind) {
        if (this == OFF || this == EXTRACT) {
            return false;
        }
        return true;                    // NORMAL, INSERT, BOTH - for every kind
    }

    /** Lang key suffix for the menu: sc.conduit.mode.<key>. */
    public String menuKey(ConduitKind kind, boolean connector) {
        if (!connector) {
            return this == OFF ? "off" : "linked";
        }
        switch (canonical(kind)) {
            case EXTRACT: return "extract";
            case INSERT: return "insert";
            case OFF: return "off";
            case BOTH: return "both";
            default: return kind == ConduitKind.CABLE ? "both" : "insert";
        }
    }
}
