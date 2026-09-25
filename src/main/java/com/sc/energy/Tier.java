package com.sc.energy;

/**
 * Voltage tiers, per design doc 01_recipes.md §9.1/§9.2.
 * Buffer = 100 x voltage (kept in sync with the doc's stated formula).
 */
public enum Tier {

    LV(32, 3200),
    MV(128, 12800),
    HV(512, 51200),
    EV(2048, 204800);

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

    /** Maps an IC2 sink/source tier integer (1=LV, 2=MV, 3=HV, 4=EV) to our Tier. */
    public static Tier fromIc2Tier(int ic2Tier) {
        switch (ic2Tier) {
            case 1: return LV;
            case 2: return MV;
            case 3: return HV;
            default: return EV;
        }
    }

    /** IC2's own tier numbering (1-based), used when implementing IEnergySink/IEnergySource. */
    public int toIc2Tier() {
        return ordinal() + 1;
    }
}
