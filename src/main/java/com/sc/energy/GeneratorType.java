package com.sc.energy;

/**
 * The 7 generators from 01_recipes.md §15/§18.2. Fluid fuel is looked up by name at runtime
 * (FluidRegistry.getFluid) rather than held as a Fluid reference, since this enum's constants
 * are initialized at class-load time - before ModFluids.init() runs in preInit.
 */
public enum GeneratorType {

    COMBUSTION("CombustionGenerator", Tier.LV, 32, Kind.FLUID_FUEL, "diesel", 2),
    SOLAR_SI("SolarPanelSi", Tier.LV, 8, Kind.PASSIVE, null, 0),
    STEAM_TURBINE("SteamTurbine", Tier.MV, 128, Kind.FLUID_FUEL, "steam", 40),
    GAS_TURBINE("GasTurbine", Tier.MV, 128, Kind.FLUID_FUEL, "hydrogen", 20),
    SOLAR_GAAS("SolarPanelGaAs", Tier.HV, 64, Kind.PASSIVE, null, 0),
    // TODO(§15/§14 balance): the doc pairs "Ar 10 mB/t" here with an Air Separator that yields
    // 49 mB of argon per 400-tick operation (0.12 mB/t). Taken literally that is ~82 separators
    // at 200 EU/t each to run one 512 EU/t generator - it could never pay for its own fuel. The
    // correction is split between both sides rather than distorting either one alone: the draw
    // drops 10 -> 2 mB/t here, and the separator's argon yield rises to 800 mB/operation, so a
    // single separator sustains exactly one Plasma Generator for a net +312 EU/t.
    PLASMA_GENERATOR("PlasmaGenerator", Tier.HV, 512, Kind.FLUID_FUEL, "argon", 2),
    // §18.2: ignition then Deuterium Cell consumption - see TileEntityGeneratorSC.updateFusion().
    FUSION_REACTOR("FusionReactor", Tier.EV, 2048, Kind.FUSION, null, 0);

    public enum Kind { PASSIVE, FLUID_FUEL, FUSION }

    public final String displayName;
    public final Tier tier;
    public final int euPerTick;
    public final Kind kind;
    public final String fuelFluidName;
    /** mB of fuelFluidName consumed per tick while generating (§15's own "X mB/t" figures). */
    public final int fuelRatePerTick;

    GeneratorType(String displayName, Tier tier, int euPerTick, Kind kind, String fuelFluidName, int fuelRatePerTick) {
        this.displayName = displayName;
        this.tier = tier;
        this.euPerTick = euPerTick;
        this.kind = kind;
        this.fuelFluidName = fuelFluidName;
        this.fuelRatePerTick = fuelRatePerTick;
    }

    /** See MachineType.localizedName() - same scheme, keys live under "sc.generator.". */
    public String localizedName() {
        return com.sc.manual.Lang.trOr("sc.generator." + name().toLowerCase(java.util.Locale.ROOT), displayName);
    }

    public String frontTexture() {
        return "machine" + displayName + "Front";
    }
}
