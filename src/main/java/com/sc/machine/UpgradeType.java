package com.sc.machine;

/**
 * Machine upgrades (IC2 style) - they go into a machine's four upgrade slots, and several of one
 * kind stack their effect. TODO(not in the design doc): the kinds and numbers are this mod's own,
 * the first three are IC2's (same values).
 */
public enum UpgradeType {

    /** Recipe time x0.7 per upgrade, energy per tick x1.6 (IC2's overclocker). */
    OVERCLOCKER("upgradeOverclocker"),
    /** Takes one tier higher voltage per upgrade without exploding (IC2's transformer upgrade). */
    TRANSFORMER("upgradeTransformer"),
    /** +10 000 EU of buffer per upgrade (IC2's energy storage upgrade). */
    ENERGY_STORAGE("upgradeEnergyStorage"),
    /** Pushes products (items and fluids) into every neighbouring inventory / tank that takes them. */
    EJECTOR("upgradeEjector"),
    /** Pulls ingredients (items and fluids) from every neighbouring inventory / tank. */
    PULLER("upgradePuller"),
    /** Heat-capable machines heat up 1 / (n+1) as fast. */
    HEAT_SINK("upgradeHeatSink"),
    /** Defect chance x0.5 per upgrade, energy per tick x1.25. */
    QUALITY("upgradeQuality"),
    /**
     * Universal transformer: one is enough - the machine (or field generator) takes any voltage,
     * LV to XV, without exploding. Appended last: the item's metadata is the ordinal.
     */
    UNIVERSAL_TRANSFORMER("upgradeUniversalTransformer");

    /** Effects stop growing past this many upgrades of one kind (IC2 lets a slot hold 64). */
    public static final int MAX_EFFECTIVE = 16;
    public static final int STORAGE_PER_UPGRADE = 10000;

    public final String textureName;

    UpgradeType(String textureName) {
        this.textureName = textureName;
    }

    public static UpgradeType byMeta(int meta) {
        UpgradeType[] v = values();
        return v[meta >= 0 && meta < v.length ? meta : 0];
    }
}
