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
    UNIVERSAL_TRANSFORMER("upgradeUniversalTransformer"),
    /** Generators only: output x1.5 and fuel x1.75 per upgrade (at most 4 count). */
    OVERDRIVE("upgradeOverdrive"),
    /** Generators only: fuel x0.7 and output x0.9 per upgrade (at most 4 count). */
    ECONOMIZER("upgradeEconomizer"),
    /** Field generator only: wireless charging x2 per upgrade (at most 4 count). */
    CHARGE_BOOSTER("upgradeChargeBooster"),
    /** Machines and generators: +8000 mB to every tank per upgrade (at most 4 count). */
    TANK_EXTENSION("upgradeTankExtension"),
    /**
     * RTGs and reactors only: a lead casing - no radiation gets out, but the output is 10% lower
     * and a fusion / exo reactor runs hotter (one is enough).
     */
    RAD_SHIELDING("upgradeRadShielding"),
    /**
     * Energy storages only (HV and up, not under IC2 without Industrial Upgrade): one more output
     * face each (at most 2 count), set with the mod's wrench. Appended last: meta = ordinal.
     */
    OUTPUT_SPLITTER("upgradeOutputSplitter"),
    /**
     * Energy storages only: the output goes up to what the neighbour on the output face takes,
     * at most two tiers over the storage's own (one is enough). Works under plain IC2 too.
     */
    ADAPTIVE_TRANSFORMER("upgradeAdaptiveTransformer");

    /** Output splitters: at most this many count (3 output faces in all). */
    public static final int MAX_OUTPUT_SPLITTERS = 2;
    /** The adaptive transformer raises the output at most this many tiers over the storage's own. */
    public static final int ADAPTIVE_MAX_RAISE = 2;

    /** Only energy storages take it (machines, generators and the field generator refuse it). */
    public boolean storageOnly() {
        return this == OUTPUT_SPLITTER || this == ADAPTIVE_TRANSFORMER;
    }

    public static final int MAX_TANK_UPGRADES = 4, TANK_PER_UPGRADE = 8000;
    /** EU to pour out a machine's or generator's tank: 1 per 10 mB (as the quarry's tanks). */
    public static final int CLEAR_MB_PER_EU = 10;

    public static final int MAX_CHARGE_BOOSTERS = 4;

    /** Only the field generator takes it (machines and generators refuse it). */
    public boolean fieldOnly() {
        return this == CHARGE_BOOSTER;
    }

    /** Overdrive / Economizer stop adding up past this many. */
    public static final int MAX_GENERATOR_EFFECTIVE = 4;

    /** Only generators take it (machines and the field generator refuse it). */
    public boolean generatorOnly() {
        return this == OVERDRIVE || this == ECONOMIZER || this == RAD_SHIELDING;
    }

    /** What a generator's upgrade slots take. */
    public boolean forGenerators() {
        return this == OVERDRIVE || this == ECONOMIZER || this == TRANSFORMER || this == ENERGY_STORAGE || this == TANK_EXTENSION
                || this == RAD_SHIELDING;
    }

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
