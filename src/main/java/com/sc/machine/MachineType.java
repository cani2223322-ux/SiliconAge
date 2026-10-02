package com.sc.machine;

import com.sc.energy.Tier;

/**
 * Every machine from the silicon chain (§3/§13) plus Ore Washer/Centrifuge (generic ore
 * infrastructure that §13.1 still gives EU/t for, and the implementation order in
 * 03_claude_code_prompt.md step 5 explicitly includes). EU/t is §13.1's table; ticks/defect
 * are per-RECIPE, not per-machine (§13.1 lists EU/t per machine, but §3's tick/defect numbers
 * vary per recipe on the same machine - e.g. Oxidation Furnace's own two recipes, oxidize vs
 * anneal, have different defect rates) - see MachineRecipe.
 *
 * Ore Washer and Centrifuge are registered here (so their block/TileEntity exist per step 5)
 * but start with zero recipes - their real recipe sets (one per applicable metal, §4/§11) are
 * step 11's job, not specific to the silicon chain.
 */
public enum MachineType {

    CRUSHER("Crusher", Tier.LV, 8, false),
    ORE_WASHER("OreWasher", Tier.LV, 10, false),
    BLAST_FURNACE("BlastFurnace", Tier.HV, 400, true),
    CHEM_REACTOR("ChemReactor", Tier.MV, 100, false),
    CVD_CHAMBER("CVDChamber", Tier.MV, 120, false),
    CZOCHRALSKI_PULLER("CzochralskiPuller", Tier.HV, 480, true),
    CZOCHRALSKI_PULLER_EV("CzochralskiPullerEV", Tier.EV, 1900, true),
    WIRE_SAW("WireSaw", Tier.MV, 60, false),
    OXIDATION_FURNACE("OxidationFurnace", Tier.MV, 90, false),
    PHOTORESIST_COATER("PhotoresistCoater", Tier.MV, 50, false),
    STEPPER("Stepper", Tier.HV, 450, true),
    STEPPER_EV("StepperEV", Tier.EV, 1800, true),
    ETCHING_BATH("EtchingBath", Tier.MV, 70, false),
    ION_IMPLANTER("IonImplanter", Tier.HV, 400, true),
    SPUTTERER("Sputterer", Tier.MV, 110, false),
    DICING_SAW("DicingSaw", Tier.LV, 20, false),
    PACKAGER("Packager", Tier.MV, 80, false),
    CENTRIFUGE("Centrifuge", Tier.MV, 100, false),

    // ---- §14 supply infrastructure (step 6) ----
    CHLOR_ALKALI_ELECTROLYZER("ChlorAlkaliElectrolyzer", Tier.MV, 90, false),
    AIR_SEPARATOR("AirSeparator", Tier.HV, 200, false),
    REFINERY("Refinery", Tier.HV, 250, false),
    ROLLING_MACHINE("RollingMachine", Tier.LV, 30, false),
    UPGRADE_STATION_MV("UpgradeStationMV", Tier.MV, 120, false),
    UPGRADE_STATION_HV("UpgradeStationHV", Tier.HV, 450, false),
    UPGRADE_STATION_EV("UpgradeStationEV", Tier.EV, 1800, false),
    KILN("Kiln", Tier.LV, 15, false),
    FLUID_CELL_FILLER("FluidCellFiller", Tier.LV, 10, false),
    BOILER_LV("BoilerLV", Tier.LV, 25, false),
    BOILER_MV("BoilerMV", Tier.MV, 90, false),
    /** Smelts what a furnace smelts, one piece at a time, twice as fast as a furnace; keeps the experience. */
    ELECTRIC_FURNACE("ElectricFurnace", Tier.LV, 4, false),
    /** Two pieces at once; heats up while it works: x1 cold, x3 hot (EU/t for both streams). */
    INDUCTION_FURNACE("InductionFurnace", Tier.MV, 24, false),
    /**
     * Matter Compressor: takes any item into a "mass" counter (TileEntityMachineSC.matterMass) and
     * presses a Compressed Matter Capsule out of every MATTER_PER_CAPSULE of it. Not a recipe
     * machine - it has a branch of its own in TileEntityMachineSC. Appended last: meta = ordinal.
     */
    MATTER_COMPRESSOR("MatterCompressor", Tier.IV, 2048, false);

    public final String displayName;
    public final Tier tier;
    public final int euPerTick;
    /** §13.3: only Blast Furnace, Czochralski Puller(+EV), Stepper(+EV), Ion Implanter accumulate heat. */
    public final boolean heatCapable;

    MachineType(String displayName, Tier tier, int euPerTick, boolean heatCapable) {
        this.displayName = displayName;
        this.tier = tier;
        this.euPerTick = euPerTick;
        this.heatCapable = heatCapable;
    }

    /**
     * Name for the GUI title and the handbook, resolved through the .lang files so it follows
     * the player's language. displayName stays the fallback - it doubles as the texture stem,
     * so it can't simply be replaced by a key.
     */
    public String localizedName() {
        return com.sc.manual.Lang.trOr("sc.machine." + name().toLowerCase(java.util.Locale.ROOT), displayName);
    }

    /** Smelts furnace recipes (FurnaceRecipes), not the mod's recipe list. */
    public boolean isSmelter() {
        return this == ELECTRIC_FURNACE || this == INDUCTION_FURNACE;
    }

    /** The Matter Compressor: any item becomes mass, mass becomes capsules (no RecipeRegistry recipes). */
    public boolean isCompressor() {
        return this == MATTER_COMPRESSOR;
    }

    /** Pieces a smelter works on at once (input slot i -> output slot i). */
    public int smeltStreams() {
        return this == INDUCTION_FURNACE ? 2 : this == ELECTRIC_FURNACE ? 1 : 0;
    }

    /** Matches the "machine<Name>Front"/"machineCasing<TIER>" textures pre-generated ahead of this step. */
    public String frontTexture() {
        return "machine" + displayName + "Front";
    }
}
