package com.sc.energy;

/**
 * The 5 cable items from 01_recipes.md §9.1/§12.1 - 5 items across 4 EU-tiers (Copper has
 * both a Bare and an Insulated variant, both LV).
 */
public enum CableType {

    COPPER_BARE("cableCopperBare", "wireCopperLV", Tier.LV, 1, 1, false),
    COPPER_INSULATED("cableCopperInsulated", "wireCopperInsulatedLV", Tier.LV, 2, 1, true),
    SILVER("cableSilver", "wireSilverMV", Tier.MV, 2, 1, true),
    TUNGSTEN("cableTungsten", "wireTungstenHV", Tier.HV, 3, 2, true),
    SUPERCONDUCTOR("cableSuperconductor", "wireSuperconductorEV", Tier.EV, 4, 0, true);

    public final String textureName;
    /** OreDict name, §12.5. */
    public final String oreDictName;
    public final Tier tier;
    public final int maxAmps;
    public final int lossPerBlock;
    public final boolean insulated;

    CableType(String textureName, String oreDictName, Tier tier, int maxAmps, int lossPerBlock, boolean insulated) {
        this.textureName = textureName;
        this.oreDictName = oreDictName;
        this.tier = tier;
        this.maxAmps = maxAmps;
        this.lossPerBlock = lossPerBlock;
        this.insulated = insulated;
    }

    /** Max EU/t this cable can carry (§9.1's Max V x Max A). */
    public int maxThroughput() {
        return tier.getVoltage() * maxAmps;
    }
}
