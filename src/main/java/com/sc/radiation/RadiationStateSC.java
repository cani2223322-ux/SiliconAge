package com.sc.radiation;

/**
 * The client's copy of its own player's radiation numbers (RadiationNetSC). Plain fields - nothing
 * client-only here, so the message handler may touch it on either side.
 */
public final class RadiationStateSC {

    public static volatile float level, dose;
    public static volatile int protection, flags;
    /** System time of the last update: nothing heard for a few seconds (another server, radiation off) - hide. */
    public static volatile long updated;

    private RadiationStateSC() {
    }

    public static void set(float lvl, float d, int prot, int f) {
        level = lvl;
        dose = d;
        protection = prot;
        flags = f;
        updated = System.currentTimeMillis();
    }

    public static boolean fresh() {
        return System.currentTimeMillis() - updated < 4000;
    }

    /** The radiation that gets through the protection. */
    public static float through() {
        return level * (100 - protection) / 100F;
    }
}
