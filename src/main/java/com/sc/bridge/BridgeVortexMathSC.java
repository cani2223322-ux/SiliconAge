package com.sc.bridge;

/**
 * The vortex's look in numbers (no client classes - the self-tests run them on the server): its colour by kind and
 * stability, how big it is while it opens / collapses, the wavy rim. client/BridgeVortexRendererSC draws with these.
 */
public final class BridgeVortexMathSC {

    /** Opening: from a point to full size in this many ticks (0.5 s); collapsing: to a point in this many ms. */
    public static final int OPEN_TICKS = 10, CLOSE_MS = 400;
    /** Stability bands: at / over STAB_OK the kind's own colour, under it towards yellow, under STAB_BAD orange. */
    public static final int STAB_OK = 60, STAB_BAD = BridgeMathSC.TURBULENCE;
    /** How far the rim waves (a share of the radius). */
    public static final float RIM_WAVE = 0.07F;

    private static final float[] GROUND = {0.35F, 1.0F, 0.55F}, SPACE = {0.58F, 0.5F, 1.0F};
    private static final float[] YELLOW = {1.0F, 0.88F, 0.3F}, ORANGE = {1.0F, 0.48F, 0.12F};

    private BridgeVortexMathSC() {
    }

    /** The kind's own colour (rgb 0-1): Ground green, Space blue-violet. */
    public static float[] kindColour(int kind) {
        return (kind == BridgeMathSC.SPACE ? SPACE : GROUND).clone();
    }

    /**
     * The vortex's colour at this stability: the kind's own at STAB_OK and over; between STAB_BAD and STAB_OK it slides
     * to yellow (fully yellow at STAB_BAD); under STAB_BAD orange.
     */
    public static float[] colour(int kind, int stability) {
        float[] k = kindColour(kind);
        if (stability >= STAB_OK) {
            return k;
        }
        if (stability < STAB_BAD) {
            return ORANGE.clone();
        }
        float f = (STAB_OK - stability) / (float) (STAB_OK - STAB_BAD);
        return new float[]{k[0] + (YELLOW[0] - k[0]) * f, k[1] + (YELLOW[1] - k[1]) * f, k[2] + (YELLOW[2] - k[2]) * f};
    }

    /** Brightness multiplier: 1 when stable; under STAB_BAD it flickers (0.6-1) - steps of 60 ms, the same for everyone. */
    public static float flicker(int stability, long timeMs) {
        if (stability >= STAB_BAD) {
            return 1F;
        }
        long h = (timeMs / 60) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return 0.6F + 0.4F * ((h & 0xFFFF) / 65535F);
    }

    /** The size (0-1) `ageTicks` after the opening: a point at 0, full at OPEN_TICKS, easing out, never past 1. */
    public static float openScale(float ageTicks) {
        if (ageTicks <= 0) {
            return 0F;
        }
        if (ageTicks >= OPEN_TICKS) {
            return 1F;
        }
        float t = 1F - ageTicks / OPEN_TICKS;
        return 1F - t * t * t;
    }

    /** The opening flash (0-1): bright at the birth, gone at OPEN_TICKS. */
    public static float openFlash(float ageTicks) {
        return ageTicks <= 0 ? 1F : ageTicks >= OPEN_TICKS ? 0F : 1F - ageTicks / OPEN_TICKS;
    }

    /** The size (0-1) `ms` into the collapse: full at 0, a point at CLOSE_MS (faster and faster). */
    public static float closeScale(float ms) {
        if (ms <= 0) {
            return 1F;
        }
        if (ms >= CLOSE_MS) {
            return 0F;
        }
        float t = ms / CLOSE_MS;
        return 1F - t * t;
    }

    /**
     * The rim's wave at angle `theta` (radians), time `t` (seconds): -1..1, smooth, the same at theta and theta + 2 pi
     * (whole harmonics), the same numbers for the same arguments (the seed shifts the phases, so two vortices differ).
     */
    public static float rimNoise(double theta, double t, long seed) {
        double s1 = (seed & 1023) * 0.0061, s2 = ((seed >> 10) & 1023) * 0.0073, s3 = ((seed >> 20) & 1023) * 0.0089;
        double v = 0.5 * Math.sin(3 * theta + 1.3 * t + s1) + 0.3 * Math.sin(5 * theta - 0.9 * t + s2) + 0.2 * Math.sin(8 * theta + 2.1 * t + s3);
        return (float) Math.max(-1, Math.min(1, v));
    }
}
