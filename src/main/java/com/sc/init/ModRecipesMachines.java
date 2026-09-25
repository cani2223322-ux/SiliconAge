package com.sc.init;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.Material;
import com.sc.util.OreEntry;
import com.sc.util.SCToolType;
import com.sc.util.SiliconMaterial;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * The silicon chain's own process recipes (§3, all 14 numbered steps + the Anneal variant +
 * the Czochralski/Stepper EV upgrades, §13.8) plus the GaAs subsystem (§18.1) - registered
 * directly as part of implementing each machine (step 5), not deferred to step 11 (which
 * covers crafting-table recipes and the generic per-metal Ore Washer/Centrifuge recipes that
 * aren't specific to any one machine's identity).
 *
 * A handful of simplifications, all called out where they happen:
 * - Czochralski Puller's Ar and Stepper's Ar/Kr/F2 (§3 steps 4/8) are continuous per-tick
 *   flows in the design doc, not lump sums like every other fluid input - modelled here as a
 *   flat per-operation amount instead (TODO, needs a real continuous-flow model later).
 * - Stepper's "Ar/Kr/F2" gas mixture is modelled as Argon alone (TileEntityMachineSC only has
 *   2 input fluid tanks, and no machine needs all 3 anyway per the doc's own imprecision here).
 * - Ion Implanter's 3 dopant gases (PH3/BCl3/AsH3) all produce the same generic "Doped Wafer" -
 *   §13.6's n/p-type distinction is deferred to whichever later step first needs it.
 */
public final class ModRecipesMachines {

    private ModRecipesMachines() {
    }

    public static void init() {
        ItemStack quartzite = new ItemStack(ModBlocks.oreSC, 1, OreEntry.QUARTZITE.meta());
        ItemStack limestone = new ItemStack(ModBlocks.limestoneSC);
        ItemStack coke = new ItemStack(ModItems.coke);

        // 1. Crusher: Quartzite Ore -> Silica Sand x2, 200t, 0% (§3)
        register(MachineType.CRUSHER,
                items(quartzite), null, null,
                items(silicon(SiliconMaterial.SILICA_SAND, 2)), null,
                200, 0f);

        // Scrap recycling: every machine's defect output used to be a dead end - no recipe took
        // it. Crushed back to silica sand (as real fabs recycle broken wafers into feedstock).
        // TODO(design doc has no scrap sink): 4 scrap -> 2 sand, 100t.
        ItemStack scrap = ModItems.dust.stackOf(com.sc.util.Material.SCRAP);
        scrap.stackSize = 4;
        register(MachineType.CRUSHER,
                items(scrap), null, null,
                items(silicon(SiliconMaterial.SILICA_SAND, 2)), null,
                100, 0f);

        // 2. Blast Furnace: Silica Sand + Coke + Limestone -> Metallurgical Si, 800t, 5%
        register(MachineType.BLAST_FURNACE,
                items(silicon(SiliconMaterial.SILICA_SAND, 1), coke, limestone), null, null,
                items(silicon(SiliconMaterial.METALLURGICAL_SI, 1)), null,
                800, 0.05f);

        // 3. Chem Reactor: Metallurgical Si + HCl(500mB) -> SiHCl3(fluid), 300t, 3%
        register(MachineType.CHEM_REACTOR,
                items(silicon(SiliconMaterial.METALLURGICAL_SI, 1)), fluid(ModFluids.hcl, 500), null,
                items(), fluid(ModFluids.sihcl3, 500),
                300, 0.03f);

        // 3b. CVD Chamber: SiHCl3 + H2 -> Electronic-Grade Si, 400t, 3%
        register(MachineType.CVD_CHAMBER,
                items(), fluid(ModFluids.sihcl3, 300), fluid(ModFluids.hydrogen, 200),
                items(silicon(SiliconMaterial.ELECTRONIC_GRADE_SI, 1)), null,
                400, 0.03f);

        // 4. Czochralski Puller (HV): EG-Si x9 + Seed Crystal + Ar -> Si Ingot, 1200t, 10%
        // TODO: Ar's "5 mB/t" continuous flow (§3/§13.4) simplified to a flat 100mB/operation.
        register(MachineType.CZOCHRALSKI_PULLER,
                items(silicon(SiliconMaterial.ELECTRONIC_GRADE_SI, 9), tool(SCToolType.SEED_CRYSTAL)), fluid(ModFluids.argon, 100), null,
                items(silicon(SiliconMaterial.SI_INGOT, 1)), null,
                1200, 0.10f);

        // 4EV. Czochralski Puller (EV upgrade, §13.8): same recipe, 900t, 5%
        register(MachineType.CZOCHRALSKI_PULLER_EV,
                items(silicon(SiliconMaterial.ELECTRONIC_GRADE_SI, 9), tool(SCToolType.SEED_CRYSTAL)), fluid(ModFluids.argon, 100), null,
                items(silicon(SiliconMaterial.SI_INGOT, 1)), null,
                900, 0.05f);

        // 5. Wire Saw: Si Ingot + Diamond Wire + Water -> Si Wafer x8, 200t, 8%
        register(MachineType.WIRE_SAW,
                items(silicon(SiliconMaterial.SI_INGOT, 1), tool(SCToolType.DIAMOND_WIRE)), fluid(FluidRegistry.WATER, 1000), null,
                items(silicon(SiliconMaterial.SI_WAFER, 8)), null,
                200, 0.08f);

        // 6. Oxidation Furnace: Si Wafer x6 + O2(50mB) -> Oxidized Wafer x6, 300t, 2%
        register(MachineType.OXIDATION_FURNACE,
                items(silicon(SiliconMaterial.SI_WAFER, 6)), fluid(ModFluids.oxygen, 50), null,
                items(silicon(SiliconMaterial.OXIDIZED_WAFER, 6)), null,
                300, 0.02f);

        // 7. Photoresist Coater: Oxidized Wafer + Photoresist(50mB) -> Coated Wafer, 200t, 3%
        register(MachineType.PHOTORESIST_COATER,
                items(silicon(SiliconMaterial.OXIDIZED_WAFER, 1)), fluid(ModFluids.photoresist, 50), null,
                items(silicon(SiliconMaterial.COATED_WAFER, 1)), null,
                200, 0.03f);

        // 8. Stepper (HV): Coated Wafer + Photomask + Ar/Kr/F2 -> Exposed Wafer, 400t, 12%
        // TODO: gas mixture simplified to Argon alone - see class javadoc.
        register(MachineType.STEPPER,
                items(silicon(SiliconMaterial.COATED_WAFER, 1), tool(SCToolType.PHOTOMASK)), fluid(ModFluids.argon, 50), null,
                items(silicon(SiliconMaterial.EXPOSED_WAFER, 1)), null,
                400, 0.12f);

        // 8EV. Stepper (EV upgrade, §13.8): 300t, 6%
        // §3's table prints "400/600" for HV/EV, which taken literally makes the EV machine
        // SLOWER than the HV one while drawing 4x the power (450 -> 1800 EU/t) - the upgrade
        // would be strictly worse at the one thing it exists for. §13.8 is explicit that both
        // EV upgrades exist because Stepper and Czochralski Puller are the chain's bottlenecks,
        // and it gives the Czochralski EV a real speedup (1200 -> 900). Read the same way here:
        // 400 -> 300 ticks, keeping the halved defect rate the table does agree on.
        register(MachineType.STEPPER_EV,
                items(silicon(SiliconMaterial.COATED_WAFER, 1), tool(SCToolType.PHOTOMASK)), fluid(ModFluids.argon, 50), null,
                items(silicon(SiliconMaterial.EXPOSED_WAFER, 1)), null,
                300, 0.06f);

        // 9. Etching Bath: Exposed Wafer + Developer(20mB) + HF(25mB) -> Etched Wafer, 200t, 5%
        register(MachineType.ETCHING_BATH,
                items(silicon(SiliconMaterial.EXPOSED_WAFER, 1)), fluid(ModFluids.developer, 20), fluid(ModFluids.hf, 25),
                items(silicon(SiliconMaterial.ETCHED_WAFER, 1)), null,
                200, 0.05f);

        // 10. Ion Implanter (HV): Etched Wafer + {PH3|BCl3|AsH3}(100mB) -> Doped Wafer, 300t, 4%
        // Three recipes (one per dopant), same generic output - see class javadoc.
        for (net.minecraftforge.fluids.Fluid dopant : new net.minecraftforge.fluids.Fluid[]{ModFluids.ph3, ModFluids.bcl3, ModFluids.ash3}) {
            register(MachineType.ION_IMPLANTER,
                    items(silicon(SiliconMaterial.ETCHED_WAFER, 1)), fluid(dopant, 100), null,
                    items(silicon(SiliconMaterial.DOPED_WAFER, 1)), null,
                    300, 0.04f);
        }

        // 11. Oxidation Furnace (Anneal): Doped Wafer -> Annealed Wafer, 200t, 1%
        register(MachineType.OXIDATION_FURNACE,
                items(silicon(SiliconMaterial.DOPED_WAFER, 1)), null, null,
                items(silicon(SiliconMaterial.ANNEALED_WAFER, 1)), null,
                200, 0.01f);

        // 12. Sputterer: Annealed Wafer + {Cu|Al|W target} + Ar -> Metallized Wafer, 200t, 3%
        SCToolType[] targets = {SCToolType.SPUTTER_TARGET_COPPER, SCToolType.SPUTTER_TARGET_ALUMINIUM, SCToolType.SPUTTER_TARGET_TUNGSTEN};
        for (SCToolType target : targets) {
            register(MachineType.SPUTTERER,
                    items(silicon(SiliconMaterial.ANNEALED_WAFER, 1), tool(target)), fluid(ModFluids.argon, 30), null,
                    items(silicon(SiliconMaterial.METALLIZED_WAFER, 1)), null,
                    200, 0.03f);
        }

        // 13. Dicing Saw: Metallized Wafer + Diamond Blade + Water -> Die x8, 150t, 5%
        register(MachineType.DICING_SAW,
                items(silicon(SiliconMaterial.METALLIZED_WAFER, 1), tool(SCToolType.DIAMOND_BLADE)), fluid(FluidRegistry.WATER, 1000), null,
                items(silicon(SiliconMaterial.DIE, 8)), null,
                150, 0.05f);

        // 14. Packager: Die(n) + Lead Frame + Compound -> Transistor/Memory Chip/Controller, 250t, 4% (§13.7)
        register(MachineType.PACKAGER,
                items(silicon(SiliconMaterial.DIE, 1), new ItemStack(ModItems.leadFrame3), new ItemStack(ModItems.compound)),
                null, null,
                items(silicon(SiliconMaterial.TRANSISTOR, 1)), null,
                250, 0.04f);
        register(MachineType.PACKAGER,
                items(silicon(SiliconMaterial.DIE, 2), new ItemStack(ModItems.leadFrame16), new ItemStack(ModItems.compound)),
                null, null,
                items(silicon(SiliconMaterial.MEMORY_CHIP, 1)), null,
                250, 0.04f);
        register(MachineType.PACKAGER,
                items(silicon(SiliconMaterial.DIE, 4), new ItemStack(ModItems.leadFrame40), new ItemStack(ModItems.compound)),
                null, null,
                items(silicon(SiliconMaterial.CONTROLLER, 1)), null,
                250, 0.04f);

        registerGaAs();
    }

    // §18.1: reuses Chem Reactor / Czochralski Puller / Wire Saw instead of new machines.
    private static void registerGaAs() {
        // G1: AsH3 -> dustArsenic + H2, 200t, 3%
        register(MachineType.CHEM_REACTOR,
                items(), fluid(ModFluids.ash3, 100), null,
                items(dust(Material.ARSENIC, 1)), fluid(ModFluids.hydrogen, 100),
                200, 0.03f);

        // G2: dustGallium x4 + dustArsenic x4 + Seed Crystal + Ar -> GaAs Ingot, 1200t, 10%
        register(MachineType.CZOCHRALSKI_PULLER,
                items(dust(Material.GALLIUM, 4), dust(Material.ARSENIC, 4), tool(SCToolType.SEED_CRYSTAL)), fluid(ModFluids.argon, 100), null,
                items(silicon(SiliconMaterial.GAAS_INGOT, 1)), null,
                1200, 0.10f);
        // G2EV: the EV puller is crafted FROM the HV one, so it has to keep GaAs too (as with
        // silicon: 900t, 5%) - otherwise upgrading the puller cut the GaAs chain off.
        register(MachineType.CZOCHRALSKI_PULLER_EV,
                items(dust(Material.GALLIUM, 4), dust(Material.ARSENIC, 4), tool(SCToolType.SEED_CRYSTAL)), fluid(ModFluids.argon, 100), null,
                items(silicon(SiliconMaterial.GAAS_INGOT, 1)), null,
                900, 0.05f);

        // G3: GaAs Ingot + Diamond Wire + Water -> GaAs Wafer x8, 200t, 8%
        register(MachineType.WIRE_SAW,
                items(silicon(SiliconMaterial.GAAS_INGOT, 1), tool(SCToolType.DIAMOND_WIRE)), fluid(FluidRegistry.WATER, 1000), null,
                items(silicon(SiliconMaterial.GAAS_WAFER, 8)), null,
                200, 0.08f);
    }

    private static void register(MachineType type, ItemStack[] inputs, FluidStack fluidA, FluidStack fluidB,
                                  ItemStack[] outputs, FluidStack fluidOutput, int ticks, float defect) {
        RecipeRegistry.register(new MachineRecipe(type, inputs, fluidA, fluidB, outputs, fluidOutput, null, ticks, defect));
    }

    private static ItemStack[] items(ItemStack... stacks) {
        return stacks;
    }

    private static ItemStack silicon(SiliconMaterial material, int count) {
        ItemStack stack = ModItems.siliconMaterial.stackOf(material);
        stack.stackSize = count;
        return stack;
    }

    private static ItemStack dust(Material material, int count) {
        ItemStack stack = ModItems.dust.stackOf(material);
        stack.stackSize = count;
        return stack;
    }

    private static ItemStack tool(SCToolType type) {
        return new ItemStack(ModItems.TOOLS.get(type));
    }

    private static FluidStack fluid(net.minecraftforge.fluids.Fluid f, int amount) {
        return new FluidStack(f, amount);
    }
}
