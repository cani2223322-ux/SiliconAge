package com.sc.energy;

/**
 * Voltage tiers, per design doc 01_recipes.md §9.1/§9.2, and three of the mod's own above EV:
 * IV (IC2's tier 5), QV - Quantum (16384) and XV - Exo (32768, IC2's tier 6).
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
    XV(32768, 3276800);

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

    /** Maps an IC2 sink/source tier integer (1=LV, 2=MV, 3=HV, 4=EV, 5=IV, 6+=XV) to our Tier. */
    public static Tier fromIc2Tier(int ic2Tier) {
        switch (ic2Tier) {
            case 1: return LV;
            case 2: return MV;
            case 3: return HV;
            case 4: return EV;
            case 5: return IV;
            default: return XV;
        }
    }

    /**
     * IC2's own tier numbering (1-based), used when implementing IEnergySink/IEnergySource. IC2's
     * tiers go x4 a step (tier 6 = 32768 EU/t), so QV's 16384 has no tier of its own there: it is
     * tier 6 as well - a QV block takes and gives packets up to 16384 all the same.
     */
    public int toIc2Tier() {
        return Math.min(ordinal() + 1, 6);
    }
}
