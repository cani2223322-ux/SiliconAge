package com.sc.energy;

import com.sc.util.ConfigSC;

import cpw.mods.fml.common.Loader;

/**
 * The other mods' energies the Energy Converter (TileEntityEnergyConverterSC) exchanges EU for, and
 * whether each one is there in this game: RF - CoFH's Redstone Flux API on the class path (Industrial
 * Upgrade, Thermal, EnderIO... ship it); J - Mekanism loaded; gJ - Galacticraft loaded. Nothing here
 * touches another mod's class: the API is looked up by name only. Plus the converter's arithmetic -
 * rates, loss, tiers, buffers, the balance - as plain static functions (the self-test checks them).
 */
public final class ForeignEnergySC {

    /** The energies, in the order of the pair buttons (the ordinal is saved: append only). */
    public enum Kind {
        RF("RF", 0xFFE04040),
        J("J", 0xFF4A90E8),
        GJ("gJ", 0xFF46D46E);

        /** The unit as shown («RF», «J», «gJ»). */
        public final String unit;
        /** The gauge's colour. */
        public final int colour;

        Kind(String unit, int colour) {
            this.unit = unit;
            this.colour = colour;
        }

        public static Kind byOrdinal(int o) {
            Kind[] v = values();
            return o >= 0 && o < v.length ? v[o] : null;
        }

        /** Units of this energy one EU is worth (the config; gJ 0 - Galacticraft's own rate). */
        public double perEu() {
            switch (this) {
                case RF: return Math.max(0.1, ConfigSC.converterRfPerEu);
                case J: return Math.max(0.1, ConfigSC.converterJPerEu);
                default: return ConfigSC.converterGjPerEu > 0 ? ConfigSC.converterGjPerEu : gcRate();
            }
        }

        /** The mod providing it is installed (a card is needed on top for J and gJ). */
        public boolean modPresent() {
            switch (this) {
                case RF: return rfApi();
                case J: return mekanism();
                default: return galacticraft();
            }
        }
    }

    /**
     * The converter screen's colours of each energy by its buffer's fill (docs/energy-converter/conv3_rf_palettes.png,
     * variant C2): the energy's own hue, brighter the fuller - one step per quarter (below 25 %, 25-50, 50-75, from 75 %).
     * Rows: RF red, J blue, gJ green.
     */
    private static final int[][] PALETTE = {
            {0xFF5A1414, 0xFF8C1E1E, 0xFFB42828, 0xFFE63C3C},
            {0xFF17375A, 0xFF25558C, 0xFF2F6DB4, 0xFF3C8CE6},
            {0xFF174E27, 0xFF257A3D, 0xFF2F9C4E, 0xFF3CC864}};
    /** The colour of "no other energy" on the converter screen. */
    public static final int NO_KIND_COLOUR = 0xFF6A6E74;

    /** The colour of energy `k` at buffer fill `level` (0..1, clamped): its gauge, ARGB. Null kind - grey. */
    public static int gaugeColour(Kind k, double level) {
        if (k == null) {
            return NO_KIND_COLOUR;
        }
        double l = Double.isNaN(level) ? 0 : Math.max(0, Math.min(1, level));
        return PALETTE[k.ordinal()][Math.min(3, (int) (l * 4))];
    }

    /** The energy's full, bright colour (its label, the arrow, the graph line, the neighbour's mark), ARGB. */
    public static int labelColour(Kind k) {
        return gaugeColour(k, 1.0);
    }

    /** Galacticraft's default gJ for one EU (its EnergyConfigHandler: 16 / 2.44). */
    public static final double GC_DEFAULT_RATE = 16.0 / 2.44;
    public static final String MEKANISM_MODID = "Mekanism", GC_MODID = "GalacticraftCore";

    /** The converter's numbers (the design): EU buffer, base tier, module limits. */
    public static final int BASE_EU_BUFFER = 1000000, MAX_TRANSFORMERS = 4, MAX_AMPLIFIERS = 3, MAX_STORAGE = 4,
            MAX_EFFICIENCY = 2, LOSS_PER_EFFICIENCY = 2;
    public static final Tier BASE_TIER = Tier.MV;

    private static Boolean rf, mek, gc;
    private static Double gcRate;
    /** Self-test only: every energy counts as present. */
    public static boolean testAllPresent;

    private ForeignEnergySC() {
    }

    /** CoFH's RF API (IEnergyHandler as a receiver and provider) is on the class path. */
    public static boolean rfApi() {
        if (testAllPresent) {
            return true;
        }
        if (rf == null) {
            rf = hasRfApi();
        }
        return rf;
    }

    /** The RF API looked up by name, without initialising anything: every interface the converter needs, the new layout. */
    private static boolean hasRfApi() {
        try {
            ClassLoader cl = ForeignEnergySC.class.getClassLoader();
            Class<?> handler = Class.forName("cofh.api.energy.IEnergyHandler", false, cl);
            Class<?> receiver = Class.forName("cofh.api.energy.IEnergyReceiver", false, cl);
            Class<?> provider = Class.forName("cofh.api.energy.IEnergyProvider", false, cl);
            Class.forName("cofh.api.energy.IEnergyConnection", false, cl);
            Class.forName("cofh.api.energy.IEnergyContainerItem", false, cl);
            return receiver.isAssignableFrom(handler) && provider.isAssignableFrom(handler);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean mekanism() {
        if (testAllPresent) {
            return true;
        }
        if (mek == null) {
            mek = Loader.isModLoaded(MEKANISM_MODID) && classThere("mekanism.api.energy.IStrictEnergyAcceptor")
                    && classThere("mekanism.api.energy.ICableOutputter");
        }
        return mek;
    }

    public static boolean galacticraft() {
        if (testAllPresent) {
            return true;
        }
        if (gc == null) {
            gc = Loader.isModLoaded(GC_MODID) && classThere("micdoodle8.mods.galacticraft.api.transmission.tile.IElectrical")
                    && classThere("micdoodle8.mods.galacticraft.api.transmission.tile.IConductor");
        }
        return gc;
    }

    private static boolean classThere(String name) {
        try {
            Class.forName(name, false, ForeignEnergySC.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Some other energy is there: the converter has a use (its recipe is added, NEI shows it). */
    public static boolean anyPresent() {
        return rfApi() || mekanism() || galacticraft();
    }

    /** Galacticraft's own gJ for one EU (EnergyConfigHandler.IC2_RATIO, read by reflection), else its default. */
    static double gcRate() {
        if (gcRate == null) {
            double r = GC_DEFAULT_RATE;
            if (galacticraft() && !testAllPresent) {
                try {
                    Class<?> c = Class.forName("micdoodle8.mods.galacticraft.core.energy.EnergyConfigHandler");
                    float v = c.getField("IC2_RATIO").getFloat(null);
                    if (v > 0.01F && v < 10000F) {
                        r = v;
                    }
                } catch (Throwable t) {
                    // its rate isn't readable: the default
                }
            }
            gcRate = r;
        }
        return gcRate;
    }

    // ------------------------------------------------------------------ the arithmetic

    /** The loss in %: the config's, 2 less for each Efficiency module (at most 2 count), never below 0. */
    public static int lossPercent(int efficiencyModules) {
        return Math.max(0, ConfigSC.converterLoss - LOSS_PER_EFFICIENCY * Math.max(0, Math.min(MAX_EFFICIENCY, efficiencyModules)));
    }

    /** What `eu` EU become in the other energy at `rate` units an EU, `lossPct` % lost. */
    public static double euToX(double eu, double rate, int lossPct) {
        return eu * rate * (100 - lossPct) / 100.0;
    }

    /** What `x` units of the other energy become in EU. */
    public static double xToEu(double x, double rate, int lossPct) {
        return x / rate * (100 - lossPct) / 100.0;
    }

    /** The EU tier: MV, one up for each Transformer (at most 4 count). */
    public static Tier euTier(int transformers) {
        return Tier.byOrdinal(BASE_TIER.ordinal() + Math.max(0, Math.min(MAX_TRANSFORMERS, transformers)));
    }

    /** The working tier (the throughput's): the EU tier, SV with a Universal Transformer. */
    public static Tier workTier(int transformers, boolean universal) {
        return universal ? Tier.SV : euTier(transformers);
    }

    /** Packets a tick: x2 for each Channel Amplifier, at most 3 count (x8). */
    public static int packets(int amplifiers) {
        return 1 << Math.max(0, Math.min(MAX_AMPLIFIERS, amplifiers));
    }

    /** Throughput, EU (or their worth) a tick: the working tier's voltage x packets. */
    public static int throughput(int transformers, boolean universal, int amplifiers) {
        return (int) Math.min(Integer.MAX_VALUE, (long) workTier(transformers, universal).getVoltage() * packets(amplifiers));
    }

    /** The EU buffer: 1 000 000 EU, x4 for each Energy Storage module (at most 4 count). */
    public static int euCapacity(int storageModules) {
        return BASE_EU_BUFFER << 2 * Math.max(0, Math.min(MAX_STORAGE, storageModules));
    }

    /**
     * The pair switch's refund: the old energy's buffer `x` back into EU at `rate` with the loss, or -1
     * when the EU buffer hasn't the room (`euRoom`) - the switch is refused then.
     */
    public static long refund(double x, double rate, int lossPct, long euRoom) {
        long eu = (long) Math.floor(xToEu(x, rate, lossPct) + 1e-9);
        return eu > euRoom ? -1 : eu;
    }

    /** The balance mode leaves the buffers alone while their fill differs by no more than this. */
    public static final double BALANCE_DEADBAND = 0.02;

    /**
     * «Баланс»: how many EU to turn into the other energy this tick (> 0), or how many EU to make of it
     * (< 0 - that many EU come out), so both buffers end up equally full; 0 inside the dead band.
     * At most `budget` EU (or their worth) a tick; never more than the room on the other side.
     */
    public static long balanceStep(long eu, long euCap, double x, double xCap, double rate, int lossPct, long budget) {
        if (euCap <= 0 || xCap <= 0) {
            return 0;
        }
        double fe = (double) eu / euCap, fx = x / xCap, keep = (100 - lossPct) / 100.0;
        if (Math.abs(fe - fx) <= BALANCE_DEADBAND) {
            return 0;
        }
        // xCap = euCap * rate: in EU worth both buffers share one scale, xe = x / rate
        double xe = x / rate;
        if (fe > fx) {
            // (eu - e) = xe + e * keep  ->  e = (eu - xe) / (1 + keep)
            long e = (long) Math.floor((eu - xe) / (1 + keep));
            long room = (long) Math.floor((xCap - x) / (rate * keep));
            return Math.max(0, Math.min(Math.min(e, budget), Math.min(eu, room)));
        }
        // the other way: take y EU worth of x, make y * keep EU: (eu + y * keep) = (xe - y)  ->  y = (xe - eu) / (1 + keep)
        double y = (xe - eu) / (1 + keep);
        long out = (long) Math.floor(Math.min(y, budget) * keep);
        out = Math.min(out, euCap - eu);
        return -Math.max(0, out);
    }
}
