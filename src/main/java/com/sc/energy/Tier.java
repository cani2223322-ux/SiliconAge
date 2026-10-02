package com.sc.energy;

/**
 * Voltage tiers, per design doc 01_recipes.md §9.1/§9.2, and the mod's own above EV:
 * IV (IC2's tier 5), QV - Quantum (16384), XV - Exo (32768, IC2's tier 6) and
 * SV - Singular (131072, IC2's tier 7).
 * Buffer = 100 x voltage (kept in sync with the doc's stated formula).
 * Appended last on purpose: block metadata and saved tiles hold the ordinal.
 */
public enum Tier {

    LV(32, 3200),
    MV(128, 12800),
    HV(512, 51200),
    EV(2048, 204800),
    IV(8192, 819200),
    QV(16384, 1638400),
    XV(32768, 3276800),
    SV(131072, 13107200);

    /**
     * SV blocks (storage, charge pad, XV-SV transformer, wireless Tx/Rx): their textures and
     * recipes are in, so they are shown. Set it back to false to hide the SV variants from the
     * creative tab / NEI and drop their recipes (the core - net, explosions, NBT - handles SV either way).
     */
    public static final boolean SV_CONTENT_READY = true;

    private static final Tier[] VALUES = values();

    private final int voltage;
    private final int buffer;

    Tier(int voltage, int buffer) {
        this.voltage = voltage;
        this.buffer = buffer;
    }

    public int getVoltage() {
        return voltage;
    }

    public int getBuffer() {
        return buffer;
    }

    /** How many tiers `other` exceeds this one by, or 0 if it doesn't. Used by ExplosionLogic (§9.3). */
    public int excessTiersOf(Tier other) {
        int diff = other.ordinal() - this.ordinal();
        return diff > 0 ? diff : 0;
    }

    /** The highest tier there is (the last one) - use this, not a named tier, for "accepts anything". */
    public static Tier max() {
        return VALUES[VALUES.length - 1];
    }

    /** Tier by ordinal, clamped to the valid range (NBT / metadata / sync values). */
    public static Tier byOrdinal(int ordinal) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, ordinal))];
    }

    /** The next tier up, or this one if it is already the top. */
    public Tier up() {
        return byOrdinal(ordinal() + 1);
    }

    /**
     * The highest tier an output-raising upgrade (Transformer upgrade in a storage / generator) may
     * lift the output to: XV - an XV storage with a Transformer upgrade in it (no effect before SV)
     * must not start sending SV into an XV network of an old world.
     */
    public static Tier outputRaiseCeiling() {
        return XV;   // for good: SV is reached on purpose (an XV-SV transformer, the adaptive one, the tokamak), never by old upgrades
    }

    /** This tier raised `steps` tiers by Transformer upgrades, at most outputRaiseCeiling() (never below itself). */
    public Tier raisedOutput(int steps) {
        int top = Math.max(ordinal(), outputRaiseCeiling().ordinal());
        return byOrdinal(Math.min(top, ordinal() + Math.max(0, steps)));
    }

    /** Whether this tier's own blocks (storage, pad, transformer, wireless) are shown in creative / NEI. */
    public boolean isContentReady() {
        return this != SV || SV_CONTENT_READY;
    }

    /** Maps an IC2 sink/source tier integer (1=LV, 2=MV, 3=HV, 4=EV, 5=IV, 6=XV, 7+=SV) to our Tier. */
    public static Tier fromIc2Tier(int ic2Tier) {
        switch (ic2Tier) {
            case 1: return LV;
            case 2: return MV;
            case 3: return HV;
            case 4: return EV;
            case 5: return IV;
            case 6: return XV;
            default: return ic2Tier >= 7 ? SV : LV;
        }
    }

    /**
     * IC2's own tier numbering (1-based), used when implementing IEnergySink/IEnergySource. IC2's
     * tiers go x4 a step (tier 6 = 32768 EU/t), so QV's 16384 has no tier of its own there: it is
     * tier 6 as well - a QV block takes and gives packets up to 16384 all the same. SV (131072)
     * is IC2's tier 7.
     */
    public int toIc2Tier() {
        switch (this) {
            case LV: return 1;
            case MV: return 2;
            case HV: return 3;
            case EV: return 4;
            case IV: return 5;
            case QV:
            case XV: return 6;
            default: return 7;
        }
    }
}
