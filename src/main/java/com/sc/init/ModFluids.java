package com.sc.init;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * The fluids consumed/produced by the silicon chain (§3) and its gas suppliers (§4/§8/§14).
 * None are placeable world fluid blocks (no BlockFluidClassic) - they only ever live inside
 * FluidTanks (machine internals, pipes). Their icons are attached client-side at texture-stitch
 * time by FluidTextureHandler; COLORS mirrors the textures' base colour for the GUI gauges,
 * since 1.7.10's Fluid has no setColor.
 */
public final class ModFluids {

    public static Fluid hcl;
    public static Fluid sihcl3;
    public static Fluid hydrogen;
    public static Fluid oxygen;
    public static Fluid nitrogen;
    public static Fluid argon;
    public static Fluid krypton;
    public static Fluid fluorine;
    public static Fluid photoresist;
    public static Fluid developer;
    public static Fluid hf;
    public static Fluid ph3;
    public static Fluid bcl3;
    public static Fluid ash3;
    public static Fluid naoh;
    public static Fluid chlorine;
    public static Fluid steam;
    public static Fluid crudeOil;
    public static Fluid diesel;
    public static Fluid liquidHelium;
    public static Fluid deuterium;
    /** Titanium tetrachloride - the Kroll process intermediate (§4: Ilmenite -> TiCl4 -> Ti). */
    public static Fluid ticl4;
    /** Heavy water (D2O): enriched out of water in the chemical reactor, electrolysed into deuterium. */
    public static Fluid heavyWater;
    /** Singular matter (docs/plan-singular-armor.md §4): the Singular suit's 8th tank; its source (the Matter Compressor) comes later. */
    public static Fluid singularMatter;

    /** Fluids this mod actually registered - only these get our icons (see register()). */
    public static final List<Fluid> OWNED = new ArrayList<Fluid>();
    /** Base colour of each fluid's texture, ARGB, keyed by fluid name. */
    public static final Map<String, Integer> COLORS = new HashMap<String, Integer>();

    private ModFluids() {
    }

    public static void init() {
        // §12.3 "агрессивная химия": the acids and acid-formers that eat metal pipe - only PTFE carries
        // them. Dry chlorine, caustic soda, TiCl4 and the dopant hydrides are handled in steel
        // in real plants; listing them made the first titanium (Cl2 -> TiCl4 -> Kroll) need PTFE,
        // which only the HV Refinery makes - built from titanium.
        for (String corrosive : new String[]{"hcl", "hf", "fluorine", "bcl3"}) {
            com.sc.util.CorrosiveFluids.register(corrosive);
        }
        COLORS.put("hcl", 0xFFC8D87A);
        COLORS.put("sihcl3", 0xFFB8C8D0);
        COLORS.put("hydrogen", 0xFFD8E8FF);
        COLORS.put("oxygen", 0xFF9EC8F0);
        COLORS.put("nitrogen", 0xFFC8C0E8);
        COLORS.put("argon", 0xFFB890F0);
        COLORS.put("krypton", 0xFFD8F0E0);
        COLORS.put("fluorine", 0xFFF0E890);
        COLORS.put("photoresist", 0xFFD05A28);
        COLORS.put("developer", 0xFF7AD0C0);
        COLORS.put("hf", 0xFFA8E0B8);
        COLORS.put("ph3", 0xFFD8A8D8);
        COLORS.put("bcl3", 0xFFC8C8C8);
        COLORS.put("ash3", 0xFFD8D0A0);
        COLORS.put("naoh", 0xFFE8E8F0);
        COLORS.put("chlorine", 0xFFC0D840);
        COLORS.put("steam", 0xFFE8E8E8);
        COLORS.put("crudeoil", 0xFF2A2018);
        COLORS.put("diesel", 0xFFD0A030);
        COLORS.put("liquidhelium", 0xFFB0F0FF);
        COLORS.put("deuterium", 0xFFA0A8F0);
        COLORS.put("ticl4", 0xFFE8E4C8);
        COLORS.put("heavywater", 0xFF3A62C0);
        COLORS.put("singularmatter", 0xFFC85AFF);
        hcl = register("hcl");
        sihcl3 = register("sihcl3");
        hydrogen = register("hydrogen", true);
        oxygen = register("oxygen", true);
        nitrogen = register("nitrogen", true);
        argon = register("argon", true);
        krypton = register("krypton", true);
        fluorine = register("fluorine", true);
        photoresist = register("photoresist");
        developer = register("developer");
        hf = register("hf");
        ph3 = register("ph3", true);
        bcl3 = register("bcl3", true);
        ash3 = register("ash3", true);
        naoh = register("naoh");
        chlorine = register("chlorine", true);
        steam = register("steam", true);
        crudeOil = register("crudeoil");
        diesel = register("diesel");
        liquidHelium = register("liquidhelium");
        deuterium = register("deuterium");
        ticl4 = register("ticl4", false);
        heavyWater = register("heavywater");
        singularMatter = register("singularmatter");
    }

    private static Fluid register(String name) {
        return register(name, false);
    }

    private static Fluid register(String name, boolean gaseous) {
        Fluid fluid = new Fluid(name).setGaseous(gaseous);
        if (FluidRegistry.registerFluid(fluid)) {
            OWNED.add(fluid);
        } else {
            // Someone else already owns this name - several of ours ("hydrogen", "oxygen",
            // "steam", "chlorine", "diesel", "crudeoil") are also registered by IC2/Railcraft/
            // Thermal Expansion. registerFluid() leaves OUR instance out of the registry in that
            // case, so hand back theirs: keeping ours would mean a field that isn't identical to
            // FluidRegistry.getFluid(name) and whose setGaseous() never took effect.
            return FluidRegistry.getFluid(name);
        }
        return fluid;
    }
}
