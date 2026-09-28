package com.sc.init;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.Material;
import com.sc.util.OreEntry;
import com.sc.util.SCToolType;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Step 6 (§14): the supply-infrastructure machines' own recipes, registered the same way as
 * ModRecipesMachines (a machine's process recipe is part of implementing it, not deferred to
 * step 11). Upgrade Station (all 3 tiers) is registered as a MachineType now but gets no
 * recipes here - its recipes are armor/chip upgrades (§6), which don't exist until step 8.
 *
 * Simplifications (§14 itself is already TODO-filled by analogy, see the design doc):
 * - Chlor-Alkali Electrolyzer only outputs H2 (feeds CVD Chamber/GaAs, already built in step
 *   5) - Cl2 and NaOH aren't modelled yet since nothing in the mod consumes them so far.
 * - Air Separator only outputs O2+Ar (the two gases already consumed elsewhere in the mod,
 *   §3/§13) - N2 and He aren't modelled yet for the same reason.
 * - Refinery's Tar byproduct isn't modelled (pure waste, nothing consumes it).
 * - Fluid Cell Filler's "any empty cell" concept is simplified to one concrete pairing
 *   (vanilla Bucket -> Liquid He Cell) rather than a generic cell-filling system.
 */
public final class ModRecipesInfrastructure {

    private ModRecipesInfrastructure() {
    }

    public static void init() {
        registerChlorAlkali();
        registerAirSeparator();
        registerRefinery();
        registerRollingMachine();
        registerKiln();
        registerFluidCellFiller();
        registerBoiler(MachineType.BOILER_LV);
        registerBoiler(MachineType.BOILER_MV);
        registerDielectric();
    }

    private static void registerDielectric() {
        // §1: Dielectric (Ta2O5) = Ta Powder + O2 -> Chem Reactor (reuses the silicon chain's
        // own Chem Reactor, same as the GaAs subsystem reuses Chem Reactor/Czochralski/Wire Saw).
        ItemStack dustTa = ModItems.dust.stackOf(Material.TANTALUM);
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{dustTa}, new FluidStack(ModFluids.oxygen, 50), null,
                new ItemStack[]{new ItemStack(ModItems.component("dielectric"))}, null, null,
                200, 0.03f));
    }

    private static void registerChlorAlkali() {
        // §18.2: deuterium is a trace byproduct here. TODO(balance): the natural isotope ratio
        // (~1 mB per 500 mB H2) meant ~1000 halite per Deuterium Cell; scaled to 10 mB so the
        // Fusion Reactor's fuel loop is playable (25 halite per 250 mB cell, see the Filler).
        ItemStack halite = new ItemStack(ModBlocks.oreSC, 1, OreEntry.HALITE.meta());
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[]{halite}, null, null,
                new ItemStack[0], new FluidStack(ModFluids.hydrogen, 500), new FluidStack(ModFluids.deuterium, 10),
                300, 0f));
        // Water electrolysis: hydrogen and oxygen on MV - alkaline, as it's done for real: a little
        // NaOH is the electrolyte (without it plain water never starts this, so a brine line that
        // ran out of halite doesn't fill its tanks with hydrogen). It costs more than a fuel cell
        // gets back from the gases (90 EU/t x 800 t = 72 000 EU against 50 000): no endless loop.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[0], new FluidStack(FluidRegistry.WATER, 1000), new FluidStack(ModFluids.naoh, 10),
                new ItemStack[0], new FluidStack(ModFluids.hydrogen, 500), new FluidStack(ModFluids.oxygen, 250),
                800, 0f));
        // Deuterium the real way: water enriched in heavy water by hydrogen-water exchange (1 part
        // in 20 here; the hydrogen is needed, so a developer line short of NaOH doesn't turn to
        // heavy water), the heavy water electrolysed. One reactor and one electrolyser keep a
        // plasma reactor (1 mB/t) going.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[0], new FluidStack(FluidRegistry.WATER, 2000), new FluidStack(ModFluids.hydrogen, 50),
                new ItemStack[0], new FluidStack(ModFluids.heavyWater, 100), null,
                100, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[0], new FluidStack(ModFluids.heavyWater, 200), null,
                new ItemStack[0], new FluidStack(ModFluids.deuterium, 200), new FluidStack(ModFluids.oxygen, 100),
                200, 0f));
    }

    private static void registerAirSeparator() {
        // Oxygen stays at §14's rate (400 mB / 400 t = 1 mB/t). Argon is raised from the doc's
        // 49 mB, which is realistic for air composition but left the Plasma Generator unable to
        // ever pay for its own fuel - see the TODO on GeneratorType.PLASMA_GENERATOR for the
        // arithmetic. 800 mB/operation = 2 mB/t feeds exactly one Plasma Generator, and is far
        // beyond what the chain's own argon users (Czochralski/Stepper/Sputterer) need.
        RecipeRegistry.register(new MachineRecipe(MachineType.AIR_SEPARATOR,
                new ItemStack[0], null, null,
                new ItemStack[0], new FluidStack(ModFluids.oxygen, 400), new FluidStack(ModFluids.argon, 800),
                400, 0f));
    }

    private static void registerRefinery() {
        ItemStack rubberOut = new ItemStack(ModItems.rubber, 3);
        RecipeRegistry.register(new MachineRecipe(MachineType.REFINERY,
                new ItemStack[0], new FluidStack(ModFluids.crudeOil, 1000), null,
                new ItemStack[]{rubberOut}, new FluidStack(ModFluids.diesel, 500), null,
                500, 0f));
    }

    /**
     * Every Rolling Machine product is selected by the press mold in an input slot (§2's "форма"),
     * not by how many ingots are loaded: with counts as the only discriminator a full stack of
     * copper always became Lead Frame x40, and the copper sputter target only came out with
     * exactly two ingots. With a mold each recipe needs just its own mold + metal, so any stack
     * size works and two products can never tie. Nb3Sn and Ti plates live in ModRecipesComponents.
     */
    private static void registerRollingMachine() {
        ItemStack ingotAl = ModItems.ingot.stackOf(Material.ALUMINIUM);
        ItemStack ingotCu = ModItems.ingot.stackOf(Material.COPPER);
        ItemStack ingotW = ModItems.ingot.stackOf(Material.TUNGSTEN);
        ItemStack ingotTi = ModItems.ingot.stackOf(Material.TITANIUM);

        press(SCToolType.MOLD_PLATE, copy(ingotAl, 1), new ItemStack(ModItems.alFoil));
        // §2/§13.7: the frame sizes feed the Packager's three chip classes.
        press(SCToolType.MOLD_LEAD_FRAME_3, copy(ingotCu, 1), new ItemStack(ModItems.leadFrame3));
        press(SCToolType.MOLD_LEAD_FRAME_16, copy(ingotCu, 2), new ItemStack(ModItems.leadFrame16));
        press(SCToolType.MOLD_LEAD_FRAME_40, copy(ingotCu, 3), new ItemStack(ModItems.leadFrame40));

        // §17.2: Turbine Blade = 2 Ingot(material) -> Press.
        press(SCToolType.MOLD_BLADE, copy(ingotW, 2), new ItemStack(ModItems.component("turbineBladeTungsten")));
        press(SCToolType.MOLD_BLADE, copy(ingotTi, 2), new ItemStack(ModItems.component("turbineBladeTitanium")));

        // §13.5: Sputter Targets - 2 ingots pressed onto a backing plate (real targets are backing+material).
        ItemStack backing = new ItemStack(ModItems.component("sputterBacking"));
        registerSputterTarget(Material.COPPER, backing, SCToolType.SPUTTER_TARGET_COPPER);
        registerSputterTarget(Material.ALUMINIUM, backing, SCToolType.SPUTTER_TARGET_ALUMINIUM);
        registerSputterTarget(Material.TUNGSTEN, backing, SCToolType.SPUTTER_TARGET_TUNGSTEN);
    }

    private static void registerSputterTarget(Material material, ItemStack backing, SCToolType toolType) {
        register(MachineType.ROLLING_MACHINE,
                new ItemStack[]{copy(ModItems.ingot.stackOf(material), 2), backing.copy(), mold(SCToolType.MOLD_TARGET)},
                new ItemStack[]{new ItemStack(ModItems.TOOLS.get(toolType))}, 100, 0f);
    }

    static void press(SCToolType mold, ItemStack metal, ItemStack product) {
        register(MachineType.ROLLING_MACHINE, new ItemStack[]{metal, mold(mold)}, new ItemStack[]{product}, 100, 0f);
    }

    static ItemStack mold(SCToolType type) {
        return new ItemStack(ModItems.TOOLS.get(type));
    }

    private static void registerKiln() {
        // §11.6: Rubber(heat-resist) = Rubber + dustAluminiumOxide -> Kiln.
        ItemStack rubber = new ItemStack(ModItems.rubber);
        ItemStack aluminiumOxideDust = ModItems.dust.stackOf(Material.ALUMINIUM);
        register(MachineType.KILN, new ItemStack[]{rubber, aluminiumOxideDust},
                new ItemStack[]{new ItemStack(ModItems.rubberHeatResist)}, 150, 0f);

        // §2: Ceramic Package = Al2O3 Powder x4 -> Press -> Kiln - the press step is folded
        // into this one Kiln recipe (no separate "pressed blank" item) for step 11's scope.
        ItemStack aluminiumOxideX4 = copy(ModItems.dust.stackOf(Material.ALUMINIUM), 4);
        register(MachineType.KILN, new ItemStack[]{aluminiumOxideX4},
                new ItemStack[]{new ItemStack(ModItems.component("ceramicPackage"))}, 150, 0f);
    }

    private static void registerFluidCellFiller() {
        RecipeRegistry.register(new MachineRecipe(MachineType.FLUID_CELL_FILLER,
                new ItemStack[]{new ItemStack(Items.bucket)}, new FluidStack(ModFluids.liquidHelium, 1000), null,
                new ItemStack[]{new ItemStack(ModItems.liquidHeCell)}, null, null,
                50, 0f));
        // §18.2: the only way to get Deuterium Cells for the Fusion Reactor's fuel loop.
        RecipeRegistry.register(new MachineRecipe(MachineType.FLUID_CELL_FILLER,
                new ItemStack[]{new ItemStack(Items.bucket)}, new FluidStack(ModFluids.deuterium, 250), null,
                new ItemStack[]{new ItemStack(ModItems.deuteriumCell)}, null, null,
                50, 0f));
    }

    /**
     * §14's boiler is specified as per-tick rates: Water 50 mB/t + (Coal | Diesel 2 mB/t) ->
     * Steam 40 mB/t, and §15 makes it the Steam Turbine's only fuel source at exactly 40 mB/t.
     *
     * The previous lump recipe scaled diesel correctly for a 100-tick operation (2 x 100 = 200)
     * but left water at 500 and steam at 400 - a tenth of the stated rates - so one boiler fed
     * only a tenth of one turbine and the documented chain could never balance. Restated over a
     * 50-tick operation instead of 100, which hits all three rates exactly while keeping every
     * amount inside a machine's 4000 mB tank (5000 mB of water for a 100-tick batch would not
     * fit, and the tank size is this project's own TODO default, not a doc number).
     *
     * TODO(design doc §14): the doc gives the MV boiler its own tier and 3.6x the EU/t (25 -> 90)
     * but never says what that buys. Identical output for 3.6x the power would make the upgrade
     * strictly worse, so MV runs the same batch in half the time (double throughput).
     */
    private static void registerBoiler(MachineType boiler) {
        int ticks = boiler == MachineType.BOILER_MV ? 25 : 50;
        for (int coal = 0; coal <= 1; coal++) {             // coal and charcoal
            RecipeRegistry.register(new MachineRecipe(boiler,
                    new ItemStack[]{new ItemStack(Items.coal, 1, coal)}, new FluidStack(FluidRegistry.WATER, 2500), null,
                    new ItemStack[0], new FluidStack(ModFluids.steam, 2000), null,
                    ticks, 0f));
        }
        RecipeRegistry.register(new MachineRecipe(boiler,
                new ItemStack[0], new FluidStack(FluidRegistry.WATER, 2500), new FluidStack(ModFluids.diesel, 100),
                new ItemStack[0], new FluidStack(ModFluids.steam, 2000), null,
                ticks, 0f));
    }

    private static void register(MachineType type, ItemStack[] inputs, ItemStack[] outputs, int ticks, float defect) {
        RecipeRegistry.register(new MachineRecipe(type, inputs, null, null, outputs, null, null, ticks, defect));
    }

    private static ItemStack copy(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }
}
