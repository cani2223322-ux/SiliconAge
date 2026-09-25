package com.sc.conduit;

/**
 * When an extracting connector works (Ender IO's redstone control): always, only with a redstone
 * signal at the bundle, only without one, or never. Ordinals are saved - only ever append.
 */
public enum RedstoneMode {

    ALWAYS, WITH_SIGNAL, WITHOUT_SIGNAL, NEVER;

    public static RedstoneMode of(int ordinal) {
        RedstoneMode[] v = values();
        return v[ordinal >= 0 && ordinal < v.length ? ordinal : 0];
    }

    public boolean allows(boolean powered) {
        switch (this) {
            case WITH_SIGNAL: return powered;
            case WITHOUT_SIGNAL: return !powered;
            case NEVER: return false;
            default: return true;
        }
    }

    public RedstoneMode step(int delta) {
        RedstoneMode[] v = values();
        return v[((ordinal() + delta) % v.length + v.length) % v.length];
    }
}
