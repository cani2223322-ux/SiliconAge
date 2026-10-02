package com.sc.energy;

/**
 * Every generator: the 7 from 01_recipes.md §15/§18.2 and the mod's own added after them
 * (appended - a generator's block metadata is its ordinal, split over two blocks of 16, see
 * ModBlocks.generatorStack). Fluid fuel is looked up by name at runtime (FluidRegistry.getFluid)
 * rather than held as a Fluid reference, since this enum's constants are initialized at
 * class-load time - before ModFluids.init() runs in preInit.
 *
 * euPerTick is the generator's rated output: fuel generators make exactly that, the others at
 * most that (a solar panel at night, a wind turbine low down, a thermoelectric pair...).
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
    FUSION_REACTOR("FusionReactor", Tier.EV, 2048, Kind.FUSION, null, 0),

    // ---- the mod's own, appended ----
    /** Coal, charcoal, wood... any furnace fuel - the first generator of a new world. */
    SOLID_FUEL("SolidFuelGenerator", Tier.LV, 16, Kind.SOLID, null, 0),
    /** Up to 96 EU/t: the higher above sea level and the freer the air round it, the more; rain and storms add. Needs a rotor. */
    WIND_TURBINE("WindTurbine", Tier.MV, 96, Kind.WIND, null, 0),
    /** 4 EU/t for each side in flowing water. */
    WATER_WHEEL("WaterWheel", Tier.LV, 16, Kind.WATER, null, 0),
    /** Peltier elements between a hot and a cold side: lava / fire next to water / ice / snow. */
    THERMOELECTRIC("ThermoelectricGenerator", Tier.HV, 384, Kind.THERMO, null, 0),
    GEOTHERMAL("GeothermalGenerator", Tier.HV, 256, Kind.FLUID_FUEL, "lava", 4),
    /** Hydrogen + oxygen -> energy + water (the water is pumped out). */
    FUEL_CELL("FuelCell", Tier.HV, 400, Kind.DUAL_FLUID, "hydrogen", 4, "oxygen", 2),
    /** Two radioisotope capsules, 32 EU/t each, for a whole day of play without looking. */
    RTG("RadioisotopeGenerator", Tier.MV, 64, Kind.RTG, null, 0),
    SOLAR_NANO("SolarPanelNano", Tier.IV, 1024, Kind.PASSIVE, null, 0),
    SOLAR_QUANTUM("SolarPanelQuantum", Tier.QV, 4096, Kind.PASSIVE, null, 0),
    SOLAR_EXO("SolarPanelExo", Tier.XV, 16384, Kind.PASSIVE, null, 0),
    PLASMA_REACTOR("PlasmaReactor", Tier.IV, 8192, Kind.DUAL_FLUID, "argon", 4, "deuterium", 1),
    /** A Fusion Reactor in a ring of 8 Tokamak Coils. */
    TOKAMAK("Tokamak", Tier.QV, 16384, Kind.FUSION, null, 0),
    /** Lit once by a huge charge, then runs on liquid helium cooling alone. */
    EXO_REACTOR("ExoReactor", Tier.XV, 32768, Kind.EXO, "liquidhelium", 1),
    /** Creative only: endless energy at the tier chosen on its screen. */
    CREATIVE("CreativeGenerator", Tier.XV, 32768, Kind.CREATIVE, null, 0),
    /**
     * The tokamak inside its 7x7x3 build (24 coils, a lead shell, port tanks and storages) - lit
     * only with the build whole; see TileEntityGeneratorSC's "Tokamak XV" section.
     */
    TOKAMAK_XV("TokamakXV", Tier.XV, 65536, Kind.FUSION, null, 0),
    /**
     * A tiny black hole held by 16 gravity coils inside its 7x7x5 build: lit by a 700 million EU
     * charge from its port storages, fed with matter capsules, cooled by liquid helium. Its output
     * follows the hole's mass (x0.6 - x1.5 of this); see tileentity/SingularReactorSC.
     */
    SINGULAR_REACTOR("SingularReactor", Tier.SV, 131072, Kind.SINGULAR, null, 0);

    public enum Kind { PASSIVE, FLUID_FUEL, FUSION, SOLID, WIND, WATER, THERMO, DUAL_FLUID, RTG, EXO, CREATIVE, SINGULAR }

    public final String displayName;
    public final Tier tier;
    public final int euPerTick;
    public final Kind kind;
    public final String fuelFluidName;
    /** mB of fuelFluidName consumed per tick while generating (§15's own "X mB/t" figures). */
    public final int fuelRatePerTick;
    /** The second fluid of a DUAL_FLUID generator, and its mB per tick. */
    public final String fuel2FluidName;
    public final int fuel2RatePerTick;

    GeneratorType(String displayName, Tier tier, int euPerTick, Kind kind, String fuelFluidName, int fuelRatePerTick) {
        this(displayName, tier, euPerTick, kind, fuelFluidName, fuelRatePerTick, null, 0);
    }

    GeneratorType(String displayName, Tier tier, int euPerTick, Kind kind, String fuelFluidName, int fuelRatePerTick,
                  String fuel2FluidName, int fuel2RatePerTick) {
        this.displayName = displayName;
        this.tier = tier;
        this.euPerTick = euPerTick;
        this.kind = kind;
        this.fuelFluidName = fuelFluidName;
        this.fuelRatePerTick = fuelRatePerTick;
        this.fuel2FluidName = fuel2FluidName;
        this.fuel2RatePerTick = fuel2RatePerTick;
    }

    /** See MachineType.localizedName() - same scheme, keys live under "sc.generator.". */
    public String localizedName() {
        return com.sc.manual.Lang.trOr("sc.generator." + name().toLowerCase(java.util.Locale.ROOT), displayName);
    }

    public String frontTexture() {
        return "machine" + displayName + "Front";
    }

    /** Burns something (fluid, item, cells, coolant) - the kinds Overdrive / Economizer upgrades act on. */
    public boolean burnsFuel() {
        return kind == Kind.FLUID_FUEL || kind == Kind.DUAL_FLUID || kind == Kind.SOLID || kind == Kind.FUSION || kind == Kind.EXO;
    }

    /** Ignition charge a FUSION / EXO generator needs before it runs (it takes energy in until then). */
    public long ignitionThreshold() {
        switch (this) {
            case FUSION_REACTOR: return 1000000L;
            case TOKAMAK:
            case TOKAMAK_XV: return 10000000L;
            case EXO_REACTOR: return 100000000L;
            case SINGULAR_REACTOR: return 700000000L;
            default: return 0;
        }
    }

    public boolean needsIgnition() {
        return ignitionThreshold() > 0;
    }

    /** The top face carries the front texture (solar panels look at the sky). */
    public boolean frontOnTop() {
        return kind == Kind.PASSIVE;
    }

    /**
     * EU one mB of `fluid` gives in this generator, or 0 if it isn't a fuel here. The Combustion
     * Generator takes several fuels (the mod's and other mods' by their fluid names).
     */
    public double euPerMb(String fluid) {
        if (fluid == null) {
            return 0;
        }
        if (this == COMBUSTION) {
            if (fluid.equals("diesel")) return 16;
            if (fluid.equals("fuel")) return 24;            // BuildCraft
            if (fluid.equals("biodiesel")) return 20;
            if (fluid.equals("ethanol") || fluid.equals("bioethanol")) return 12;
            if (fluid.equals("biofuel")) return 10;
            if (fluid.equals("crudeoil") || fluid.equals("oil")) return 5;
            if (fluid.equals("ic2biogas")) return 4;
            return 0;
        }
        if (kind == Kind.FLUID_FUEL && fluid.equals(fuelFluidName)) {
            return (double) euPerTick / fuelRatePerTick;
        }
        return 0;
    }

    /** The Combustion Generator's other fuels, for the handbook and NEI. */
    public static final String[] COMBUSTION_FUELS = {"diesel", "fuel", "biodiesel", "ethanol", "bioethanol", "biofuel", "crudeoil", "oil", "ic2biogas"};
}
