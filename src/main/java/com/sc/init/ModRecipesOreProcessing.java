package com.sc.init;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.Material;
import com.sc.util.OreEntry;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * The generic ore pipeline from §4 / §11.1-§11.4, which nothing had ever registered: the mod
 * generated 16 ores in the world and had crushedOre/purifiedCrushedOre/dust/dustTiny/ingot items
 * for them, but not one recipe connecting any of it. Every metal ingot in the mod was therefore
 * unobtainable, which in turn blocked practically every component, machine and cable recipe -
 * Ore Washer and Centrifuge existed purely as empty blocks for the same reason.
 *
 * Ore -> Crusher -> crushedOre x2 -> [Ore Washer + water -> purifiedCrushedOre] -> Centrifuge ->
 * dust (+ §11.2's chanced by-product) -> Furnace -> ingot.
 *
 * TODO(§4): several metals are specified with much longer real-chemistry routes - Ti via
 * TiCl4/Kroll, W via WO3 + H2 reduction, Ge via a Zone Refiner, Al via Chem Reactor + Calciner.
 * Those intermediate machines don't exist in the mod (no Calciner, no Zone Refiner, no
 * H2-Reduction Chamber), so each of them is collapsed onto the generic pipeline here. §11.2
 * explicitly notes that these paths are "сложный многостадийный путь (§4), но без развилки
 * продукта" - the product is never ambiguous, only the number of steps, so collapsing them
 * costs flavour rather than correctness.
 */
public final class ModRecipesOreProcessing {

    private static final int CRUSHER_TICKS = 200;
    private static final int WASHER_TICKS = 150;
    private static final int CENTRIFUGE_TICKS = 250;
    private static final int WASH_WATER_MB = 1000;

    private ModRecipesOreProcessing() {
    }

    /**
     * §11.2's table: which metal an ore yields, and what trace comes out with it. Halite,
     * Magnesite and Quartzite are deliberately absent - §11.2 calls them exceptions that go
     * straight to a specific machine instead of through this pipeline (registerMagnesium).
     */
    private enum OreYield {
        CHALCOPYRITE(OreEntry.CHALCOPYRITE, Material.COPPER),
        CASSITERITE(OreEntry.CASSITERITE, Material.TIN),
        GALENA(OreEntry.GALENA, Material.LEAD, Material.SILVER, 0.125f),
        SPHALERITE(OreEntry.SPHALERITE, Material.ZINC, Material.GALLIUM, 0.06f, Material.INDIUM, 0.04f),
        BAUXITE(OreEntry.BAUXITE, Material.ALUMINIUM),
        ILMENITE(OreEntry.ILMENITE, Material.TITANIUM),
        WOLFRAMITE(OreEntry.WOLFRAMITE, Material.TUNGSTEN),
        ARGYRODITE(OreEntry.ARGYRODITE, Material.GERMANIUM),
        TANTALITE(OreEntry.TANTALITE, Material.TANTALUM, Material.NIOBIUM, 0.15f),
        BADDELEYITE(OreEntry.BADDELEYITE, Material.ZIRCONIUM, Material.HAFNIUM, 0.02f),
        // Sperrylite is PtAs2 - arsenic is its majority element, so it comes out every time. This
        // is the mod's only arsenic entry point: §18.1's AsH3 -> As decomposition and the AsH3
        // synthesis in ModRecipesComponents otherwise just feed each other in a closed loop.
        SPERRYLITE(OreEntry.SPERRYLITE, Material.PLATINUM, Material.PALLADIUM, 0.50f, Material.ARSENIC, 1.0f),
        MONAZITE(OreEntry.MONAZITE, Material.NEODYMIUM, Material.CERIUM, 0.70f, Material.LANTHANUM, 0.50f),
        SPODUMENE(OreEntry.SPODUMENE, Material.LITHIUM);

        final OreEntry ore;
        final Material main;
        final Material[] traces;
        final float[] chances;

        OreYield(OreEntry ore, Material main) {
            this(ore, main, new Material[0], new float[0]);
        }

        OreYield(OreEntry ore, Material main, Material trace, float chance) {
            this(ore, main, new Material[]{trace}, new float[]{chance});
        }

        OreYield(OreEntry ore, Material main, Material t1, float c1, Material t2, float c2) {
            this(ore, main, new Material[]{t1, t2}, new float[]{c1, c2});
        }

        OreYield(OreEntry ore, Material main, Material[] traces, float[] chances) {
            this.ore = ore;
            this.main = main;
            this.traces = traces;
            this.chances = chances;
        }
    }

    public static void init() {
        for (OreYield yield : OreYield.values()) {
            registerOreChain(yield);
        }
        registerTinyDustCompacting();
        registerBootstrapSmelting();
        registerDustSmelting();
        registerCarbonAndCoke();
        registerSteel();
        registerAluminium();
        registerMagnesium();
        registerTitanium();
    }

    /**
     * §11.1's IC2-style convention: 9 tiny dusts make one full dust. Without it every metal that
     * only ever arrives as a Centrifuge trace - Ag, Ga, In, Nb, Hf - is a dead end, since the
     * smelting step works on full dusts. Those five are exactly the metals the Silver Cable,
     * GaAs wafers and Nb3Sn superconductor recipes need.
     */
    private static void registerTinyDustCompacting() {
        for (Material material : Material.values()) {
            if (!material.hasTinyDust || !material.hasDust) {
                continue;
            }
            ItemStack tiny = ModItems.dustTiny.stackOf(material);
            Object[] nine = new Object[9];
            for (int i = 0; i < nine.length; i++) {
                nine[i] = tiny.copy();
            }
            OreRecipes.shapeless(ModItems.dust.stackOf(material), nine);
        }
    }

    private static void registerOreChain(OreYield yield) {
        ItemStack oreBlock = new ItemStack(ModBlocks.oreSC, 1, yield.ore.meta());
        ItemStack crushed = stack(ModItems.crushedOre, yield.main, 2);

        RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER,
                new ItemStack[]{oreBlock}, null, null,
                new ItemStack[]{crushed}, null, null,
                CRUSHER_TICKS, 0f));

        // §4 only names an Ore Washer step for some metals; Material.hasPurifiedCrushedOre
        // already encodes exactly that list, so it decides whether the chain has three stages.
        ItemStack centrifugeInput = stack(ModItems.crushedOre, yield.main, 1);
        if (yield.main.hasPurifiedCrushedOre) {
            ItemStack purified = stack(ModItems.purifiedCrushedOre, yield.main, 1);
            RecipeRegistry.register(new MachineRecipe(MachineType.ORE_WASHER,
                    new ItemStack[]{stack(ModItems.crushedOre, yield.main, 1)},
                    new FluidStack(FluidRegistry.WATER, WASH_WATER_MB), null,
                    new ItemStack[]{purified}, null, null,
                    WASHER_TICKS, 0f));
            centrifugeInput = stack(ModItems.purifiedCrushedOre, yield.main, 1);
        }

        ItemStack[] traces = new ItemStack[yield.traces.length];
        for (int i = 0; i < traces.length; i++) {
            Material trace = yield.traces[i];
            // §11.2: Ag/Ga/In/Nb/Hf come out as tiny dusts, Pd/Ce/La (and As) at full size -
            // exactly what Material.hasTinyDust encodes.
            traces[i] = stack(trace.hasTinyDust ? ModItems.dustTiny : ModItems.dust, trace, 1);
        }

        RecipeRegistry.register(new MachineRecipe(MachineType.CENTRIFUGE,
                new ItemStack[]{centrifugeInput}, null, null,
                new ItemStack[]{stack(ModItems.dust, yield.main, 1)}, null, null,
                CENTRIFUGE_TICKS, 0f, traces, yield.chances));
    }

    /**
     * The mod's way in without IC2. Every generator needs copper cable, copper needs the Crusher,
     * and the Crusher needs power - so without a furnace route for a couple of simple ores a new
     * world could never build its first machine. Copper, tin and lead (the three §11.2 lists as
     * single-component ores) smelt straight from the ore block 1:1, IC2-style; crushing first
     * then smelting the crushed or washed ore gives 2:1, which is the Crusher's reward. Every
     * other metal still needs the full pipeline.
     */
    private static void registerBootstrapSmelting() {
        Object[][] simple = {
                {OreEntry.CHALCOPYRITE, Material.COPPER},
                {OreEntry.CASSITERITE, Material.TIN},
                {OreEntry.GALENA, Material.LEAD},
        };
        for (Object[] pair : simple) {
            OreEntry ore = (OreEntry) pair[0];
            Material metal = (Material) pair[1];
            ItemStack ingot = ModItems.ingot.stackOf(metal);
            GameRegistry.addSmelting(new ItemStack(ModBlocks.oreSC, 1, ore.meta()), ingot.copy(), 0.7F);
            GameRegistry.addSmelting(ModItems.crushedOre.stackOf(metal), ingot.copy(), 0.3F);
            if (metal.hasPurifiedCrushedOre) {
                GameRegistry.addSmelting(ModItems.purifiedCrushedOre.stackOf(metal), ingot.copy(), 0.3F);
            }
        }
    }

    /** §11.1's last stage: a metal dust smelts to its ingot in a plain furnace. */
    private static void registerDustSmelting() {
        for (Material material : Material.values()) {
            if (!material.hasIngot || !material.hasDust) {
                continue;
            }
            // Aluminium's "dust" is Al2O3 (§11.5), Magnesium's is MgO, Steel is an alloy (§11.3),
            // and Titanium only comes out of the Kroll process (§4) - all below.
            if (material == Material.ALUMINIUM || material == Material.STEEL
                    || material == Material.MAGNESIUM || material == Material.TITANIUM) {
                continue;
            }
            GameRegistry.addSmelting(ModItems.dust.stackOf(material), ModItems.ingot.stackOf(material), 0.3F);
        }
    }

    /** §11.4: vanilla Coal/Charcoal -> Crusher -> dustCarbon + 10% dustTinyAsh. §17: Coal -> Kiln -> Coke. */
    private static void registerCarbonAndCoke() {
        ItemStack[] ash = {ModItems.dustTiny.stackOf(Material.ASH)};
        float[] ashChance = {0.10f};
        for (ItemStack fuel : new ItemStack[]{new ItemStack(Items.coal, 1, 0), new ItemStack(Items.coal, 1, 1)}) {
            RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER,
                    new ItemStack[]{fuel}, null, null,
                    new ItemStack[]{ModItems.dust.stackOf(Material.CARBON)}, null, null,
                    CRUSHER_TICKS, 0f, ash, ashChance));
        }
        RecipeRegistry.register(new MachineRecipe(MachineType.KILN,
                new ItemStack[]{new ItemStack(Items.coal, 1, 0)}, null, null,
                new ItemStack[]{new ItemStack(ModItems.coke)}, null, null,
                150, 0f));
    }

    /** §11.3: ingotIron + dustCarbon -> Blast Furnace -> ingotSteel, 200 ticks, 2% defect. */
    private static void registerSteel() {
        RecipeRegistry.register(new MachineRecipe(MachineType.BLAST_FURNACE,
                new ItemStack[]{new ItemStack(Items.iron_ingot), ModItems.dust.stackOf(Material.CARBON)}, null, null,
                new ItemStack[]{ModItems.ingot.stackOf(Material.STEEL)}, null, null,
                200, 0.02f));
    }

    /**
     * §11.5: Al2O3 powder is reduced to aluminium in an electrolyzer, not a furnace. The doc
     * doesn't say WHICH electrolyzer and the mod only has the Chlor-Alkali one, so that block
     * does double duty here (and for §4's MgO -> Mg, see registerMagnesium).
     */
    private static void registerAluminium() {
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[]{ModItems.dust.stackOf(Material.ALUMINIUM)}, null, null,
                new ItemStack[]{ModItems.ingot.stackOf(Material.ALUMINIUM)}, null, null,
                300, 0f));
    }

    /**
     * §4: Magnesite (MgCO3) -> Crusher -> Kiln, calcined to MgO (CO2 driven off) -> electrolysis
     * of the melt -> Mg (the oxygen is vented: collecting it in the cell's shared output tank A
     * blocked its brine/halite recipes). The electrolysis runs in the Chlor-Alkali cell, like
     * aluminium's (the mod has one electrolyser). The magnesite ore used to have no recipe at all.
     */
    private static void registerMagnesium() {
        ItemStack magnesite = new ItemStack(ModBlocks.oreSC, 1, com.sc.util.OreEntry.MAGNESITE.meta());
        RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER,
                new ItemStack[]{magnesite}, null, null,
                new ItemStack[]{stack(ModItems.crushedOre, Material.MAGNESIUM, 2)}, null, null,
                CRUSHER_TICKS, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.KILN,
                new ItemStack[]{stack(ModItems.crushedOre, Material.MAGNESIUM, 1)}, null, null,
                new ItemStack[]{ModItems.dust.stackOf(Material.MAGNESIUM)}, null, null,
                200, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[]{ModItems.dust.stackOf(Material.MAGNESIUM)}, null, null,
                new ItemStack[]{ModItems.ingot.stackOf(Material.MAGNESIUM)}, null, null,
                300, 0f));
    }

    /**
     * §4's Kroll route: Ilmenite concentrate (the Centrifuge's titanium dust) + carbon, chlorinated
     * in the Chem Reactor -> TiCl4; TiCl4 reduced by molten magnesium in the Blast Furnace ->
     * titanium (the MgCl2 by-product isn't modelled - returning its chlorine as a fluid filled
     * the furnace's output tank and stalled it after 16 ingots). Titanium dust used to smelt
     * straight to ingots in a plain furnace, skipping all of this.
     */
    private static void registerTitanium() {
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{ModItems.dust.stackOf(Material.TITANIUM), ModItems.dust.stackOf(Material.CARBON)},
                new FluidStack(ModFluids.chlorine, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.ticl4, 500), null,
                200, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.BLAST_FURNACE,
                new ItemStack[]{stack(ModItems.ingot, Material.MAGNESIUM, 2)},
                new FluidStack(ModFluids.ticl4, 500), null,
                new ItemStack[]{ModItems.ingot.stackOf(Material.TITANIUM)}, null, null,
                300, 0.02f));
    }

    private static ItemStack stack(com.sc.item.ItemMaterialSC item, Material material, int count) {
        ItemStack stack = item.stackOf(material);
        stack.stackSize = count;
        return stack;
    }
}
