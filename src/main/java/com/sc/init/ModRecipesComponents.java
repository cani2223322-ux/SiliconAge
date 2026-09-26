package com.sc.init;

import com.sc.energy.CableType;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.Material;
import com.sc.util.SCToolType;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Closes §17.2/§17.3/§18.3: the components that recipes consumed but that nothing ever produced,
 * plus the chemistry that fed them. Before this, 24 components and 7 fluids had no source at all,
 * so the silicon chain stopped at step 2 (no HCl), no armour or weapon could be assembled, and
 * only the bare copper cable of five tiers was reachable.
 *
 * Most of this is transcribed from the design doc rather than invented: §18.3 gives the frames,
 * quartz parts, Nb3Sn alloy line, plates, He-Loop and Li-Blanket modules and the Power Core
 * Frame; §17.2 gives Developer and Compound; §11.6 gives the two rubber variants. Where the doc
 * is silent the recipe is marked TODO at its own site and built by analogy with a neighbouring
 * one it already specifies.
 *
 * Where two products share a machine and a material the Rolling Machine's press mold tells them
 * apart; where a machine has an "accepts nothing" recipe a discriminating fluid or catalyst does -
 * RecipeRegistry picks the most specific match, so the plain recipe only runs when the
 * discriminator is absent.
 */
public final class ModRecipesComponents {

    private ModRecipesComponents() {
    }

    public static void init() {
        registerFrames();
        registerOpticsAndModules();
        registerCasingsAndCoils();
        registerEnergyCells();
        registerAlloyLine();
        registerFusionCore();
        registerPlates();
        registerRubberVariants();
        registerCompound();
        registerHfO2Die();
        registerChemistry();
        registerPetrochemistry();
    }

    // ---- crafting-table assemblies (§18.3) ----

    private static void registerFrames() {
        // §18.3 says frames are "4x Ingot, кольцом (как трубы, §12.4)" - but the pipe ring IS that
        // exact shape, so 4 titanium ingots in a ring matched the Titanium Pipe, the Ti Frame and
        // the Ti Casing all at once and the crafting grid only ever handed out the first one
        // registered. Frames keep §18.3's four ingots, in the corners instead; the pipe keeps the
        // ring, and casings take the full eight (see registerCasingsAndCoils).
        corners(comp("alFrame"), ingot(Material.ALUMINIUM));
        corners(comp("tiFrame"), ingot(Material.TITANIUM));
        ring(comp("quartzChamber"), new ItemStack(Items.quartz)); // §18.3: Quartz Crystal == vanilla Nether Quartz
        // §18.3: Power Core Frame = Steel Ingot x4 + Controller, ring with the controller in the middle.
        OreRecipes.shaped(comp("powerCoreFrame"), new Object[]{
                " S ", "SCS", " S ",
                'S', ingot(Material.STEEL), 'C', silicon(SiliconMaterial.CONTROLLER)});
    }

    private static void registerOpticsAndModules() {
        OreRecipes.shapeless(comp("focusLens"), Blocks.glass, Items.diamond);              // §18.3
        OreRecipes.shapeless(comp("sensor"), silicon(SiliconMaterial.DIE), comp("focusLens")); // §18.3
        // TODO(§6): the chip-upgrade "lens" is a separate registration from the weapon Focus Lens
        // and the doc never separates them - given a plain optical element, quartz instead of diamond.
        OreRecipes.shapeless(comp("lens"), Blocks.glass, new ItemStack(Items.quartz));
        OreRecipes.shapeless(comp("quartzEmitter"), comp("quartzChamber"), silicon(SiliconMaterial.CONTROLLER)); // §18.3
        OreRecipes.shapeless(comp("heLoopModule"),                                          // §18.3
                new ItemStack(ModItems.liquidHeCell), cable(CableType.SUPERCONDUCTOR), silicon(SiliconMaterial.CONTROLLER));
        OreRecipes.shapeless(comp("liBlanketModule"),                                       // §18.3
                ingot(Material.LITHIUM), ingot(Material.LITHIUM), ingot(Material.LITHIUM), ingot(Material.LITHIUM),
                comp("tiPlate"), silicon(SiliconMaterial.CONTROLLER));
    }

    private static void registerCasingsAndCoils() {
        // TODO(§5/§7): casings are used by the generator and weapon recipes but never given one of
        // their own - a hollow square of eight ingots of the metal each is named for. Eight rather
        // than a four-ingot ring, because that ring is already the Steel/Titanium Pipe (§12.4).
        hollow(comp("steelCasing"), ingot(Material.STEEL));
        hollow(comp("tiCasing"), ingot(Material.TITANIUM));
        // TODO(§7): a coil is wire wound on a core, by analogy with §18.3's Nb3Sn Coil.
        OreRecipes.shapeless(comp("copperCoil"),
                cable(CableType.COPPER_BARE), cable(CableType.COPPER_BARE), cable(CableType.COPPER_BARE),
                cable(CableType.COPPER_BARE), new ItemStack(Items.iron_ingot));
        // TODO(§7): Plasma Rifle barrel - tungsten for the same reason its turbine blade is.
        OreRecipes.shapeless(comp("wBarrel"),
                ingot(Material.TUNGSTEN), ingot(Material.TUNGSTEN), ingot(Material.TUNGSTEN));
        // TODO(§7): Ion Cutter grip, from the polymer the Refinery already makes.
        OreRecipes.shapeless(comp("polymerHandle"), comp("polymerPlate"), comp("polymerPlate"));
    }

    /**
     * TODO(§7): the three weapon batteries have no recipe in the doc. Tiered the same way the
     * cables are - each cell is built from the pair below it plus that tier's cable and plate, so
     * the EU/t ladder from §9.1 is mirrored in the crafting cost.
     */
    private static void registerEnergyCells() {
        OreRecipes.shapeless(comp("energyCellLV"),
                comp("steelCasing"), cable(CableType.COPPER_BARE), cable(CableType.COPPER_BARE),
                ingot(Material.LEAD), ingot(Material.LEAD));
        OreRecipes.shapeless(comp("energyCellMV"),
                comp("energyCellLV"), comp("energyCellLV"), cable(CableType.SILVER), cable(CableType.SILVER),
                comp("tiPlate"));
        OreRecipes.shapeless(comp("energyCellHV"),
                comp("energyCellMV"), comp("energyCellMV"), cable(CableType.TUNGSTEN), cable(CableType.TUNGSTEN),
                comp("wTiPlate"));
    }

    // ---- machine lines ----

    /**
     * First stage of the Fusion Reactor (§5): the three bulk parts, 56 of its 64 items, which no
     * crafting grid could ever hold. Machine recipes do honour stack sizes, and the Upgrade
     * Station (EV) is the mod's assembly machine at the reactor's own tier. The final stage is a
     * crafting recipe in ModRecipesCrafting.
     */
    private static void registerFusionCore() {
        RecipeRegistry.register(new MachineRecipe(MachineType.UPGRADE_STATION_EV,
                new ItemStack[]{comp("tiCasing", 40), comp("nb3SnCoil", 8), cable(CableType.SUPERCONDUCTOR, 8)}, null, null,
                new ItemStack[]{comp("fusionCore")}, null, null,
                1200, 0f));
        // Universal transformer upgrade: deliberately the hardest upgrade to make - two QV-XV
        // transformers (the whole transformer chain twice), 16 transformer upgrades and 16 Exo
        // cables, five minutes in the EV Upgrade Station.
        ItemStack transformers = new ItemStack(com.sc.init.ModBlocks.transformerSC, 2, 5);
        ItemStack upgrades = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER);
        upgrades.stackSize = 16;
        RecipeRegistry.register(new MachineRecipe(MachineType.UPGRADE_STATION_EV,
                new ItemStack[]{transformers, upgrades, cable(CableType.EXO, 16)}, null, null,
                new ItemStack[]{ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER)}, null, null,
                6000, 0f));
    }

    /** §18.3: Nb Ingot + Sn Ingot -> Blast Furnace -> Nb3Sn Ingot, then Rolling Machine shapes it. */
    private static void registerAlloyLine() {
        RecipeRegistry.register(new MachineRecipe(MachineType.BLAST_FURNACE,
                new ItemStack[]{ingot(Material.NIOBIUM), ingot(Material.TIN)}, null, null,
                new ItemStack[]{comp("nb3SnIngot")}, null, null,
                300, 0.02f));
        // The press mold picks the shape (see ModRecipesInfrastructure.registerRollingMachine).
        ModRecipesInfrastructure.press(SCToolType.MOLD_PLATE, comp("nb3SnIngot", 1), comp("nb3SnPlate"));
        ModRecipesInfrastructure.press(SCToolType.MOLD_COIL, comp("nb3SnIngot", 2), comp("nb3SnCoil"));
    }

    /**
     * §18.3: "Ti Plate / W-Ti Plate | Ingot(материала) -> Press (плоская форма); W-Ti Plate =
     * Ti Plate + малое кол-во W Ingot -> Kiln (спекание)". One ingot per plate - the plate mold,
     * not an odd ingot count, is what keeps it apart from the Ti turbine blade on the same press.
     */
    private static void registerPlates() {
        ModRecipesInfrastructure.press(SCToolType.MOLD_PLATE, ingot(Material.TITANIUM), comp("tiPlate"));
        RecipeRegistry.register(new MachineRecipe(MachineType.KILN,
                new ItemStack[]{comp("tiPlate"), ingot(Material.TUNGSTEN)}, null, null,
                new ItemStack[]{comp("wTiPlate")}, null, null,
                200, 0f));
    }

    /** §11.6: both variants are the base Rubber plus a dopant, one cold, one fired. */
    private static void registerRubberVariants() {
        // TODO(§4): the only rubber source is the Refinery - an HV machine on crude oil - yet
        // Insulated Copper Cable is LV and every higher cable needs rubber too, so without IC2's
        // rubber trees the cable ladder had no bottom rung. Slime vulcanised in the LV Kiln is the
        // early source (GregTech's slimeball convention); the Refinery stays the bulk one.
        RecipeRegistry.register(new MachineRecipe(MachineType.KILN,
                new ItemStack[]{new ItemStack(Items.slime_ball)}, null, null,
                new ItemStack[]{new ItemStack(ModItems.rubber, 2)}, null, null,
                100, 0f));
        OreRecipes.shapeless(new ItemStack(ModItems.rubberBlue),
                new ItemStack(ModItems.rubber), ModItems.dustTiny.stackOf(Material.SILVER));
        // rubberHeatResist already has its Kiln recipe in ModRecipesInfrastructure.
    }

    // ---- chemistry: the fluids nothing produced ----

    /**
     * §17.2: "Compound (Packager, §3 шаг 14) - связующий состав корпуса чипа... TODO:
     * Polymer(100mB) + dustAluminiumOxide x1 -> Chem Reactor -> Compound x1, 150 тиков, 2% брак".
     * Polymer is an item here rather than a fluid (the Refinery presses it into sheets, see
     * registerPetrochemistry), so the 100 mB becomes one plate. Without this the chain's very
     * last step had no encapsulant and no chip could ever be packaged.
     */
    private static void registerCompound() {
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{comp("polymerPlate"), ModItems.dust.stackOf(Material.ALUMINIUM)}, null, null,
                new ItemStack[]{new ItemStack(ModItems.compound)}, null, null,
                150, 0.02f));
    }

    /**
     * TODO(§6): Chip tier III needs an "HfO2-Die" and the doc never says how to make one, so all
     * five tier III chips were unreachable. HfO2 is the real high-k gate dielectric grown on a
     * die by oxidising hafnium - the Oxidation Furnace already takes O2 for exactly this kind of
     * step (§3 step 6). Hafnium comes from Baddeleyite's 2% trace, compacted 9 -> 1.
     */
    private static void registerHfO2Die() {
        RecipeRegistry.register(new MachineRecipe(MachineType.OXIDATION_FURNACE,
                new ItemStack[]{silicon(SiliconMaterial.DIE), ModItems.dust.stackOf(Material.HAFNIUM)},
                new FluidStack(ModFluids.oxygen, 100), null,
                new ItemStack[]{comp("hfo2Die")}, null, null,
                300, 0.03f));
    }

    private static void registerChemistry() {
        // §14: brine electrolysis. The dry-halite recipe (H2 + trace deuterium, §18.2) stays as it
        // was; water in an input tank is what selects this one instead, and the machine only has
        // two output tanks so the two product pairs have to live on separate recipes.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[]{halite()}, new FluidStack(FluidRegistry.WATER, 1000), null,
                new ItemStack[0], new FluidStack(ModFluids.naoh, 500), new FluidStack(ModFluids.chlorine, 500),
                300, 0f));

        // Real chlor-alkali co-products burned together give hydrogen chloride; this is what the
        // silicon chain's step 3 runs on, and without it the whole chain stopped at step 2.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[0], new FluidStack(ModFluids.chlorine, 500), new FluidStack(ModFluids.hydrogen, 500),
                new ItemStack[0], new FluidStack(ModFluids.hcl, 1000), null,
                200, 0f));

        // §17.2, verbatim: NaOH(50) + Water(50) -> Developer(100), 150 ticks, 3% defect.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[0], new FluidStack(ModFluids.naoh, 50), new FluidStack(FluidRegistry.WATER, 50),
                new ItemStack[0], new FluidStack(ModFluids.developer, 100), null,
                150, 0.03f));

        // TODO(§10): no ore in §10 carries fluorine, and the doc never names an HF source even
        // though §3 step 9 and §4's Ta/Nb route both need it. Lithium micas are the real-world
        // fluorine mineral that travels with lithium ores, so crushed Spodumene doubles as one.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHLOR_ALKALI_ELECTROLYZER,
                new ItemStack[]{ModItems.crushedOre.stackOf(Material.LITHIUM)},
                new FluidStack(FluidRegistry.WATER, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.fluorine, 500), null,
                300, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[0], new FluidStack(ModFluids.fluorine, 500), new FluidStack(ModFluids.hydrogen, 500),
                new ItemStack[0], new FluidStack(ModFluids.hf, 1000), null,
                200, 0f));

        // Arsine for §3 step 10 and §18.1's GaAs line. Deliberately yields less AsH3 than §18.1's
        // decomposition recipe gets arsenic back from, so the two can't be cycled for free arsenic.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{ModItems.dust.stackOf(Material.ARSENIC)}, new FluidStack(ModFluids.hydrogen, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.ash3, 100), null,
                200, 0.03f));

        // The other two Ion Implanter dopants (§3 step 10). Neither is given a source anywhere in
        // the doc, and §10 has no phosphorus or boron ore - so each is drawn from the ore whose
        // real-world mineral actually carries that element. Monazite is a rare-earth PHOSPHATE,
        // which is where the phosphine comes from; halite is an evaporite, and borate salts form
        // in the same saline deposits, chlorinated here into BCl3. TODO: both are flavour routes
        // standing in for dedicated phosphorus/boron chains.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{ModItems.crushedOre.stackOf(Material.NEODYMIUM)},
                new FluidStack(ModFluids.hydrogen, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.ph3, 500), null,
                200, 0.03f));
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{halite()}, new FluidStack(ModFluids.chlorine, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.bcl3, 500), null,
                200, 0.03f));

        // TODO(§14): §14 lists He as a 1 mB trace of air separation, but the Air Separator already
        // uses both of its output tanks for O2 and Ar. Modelled as a separate water-cooled
        // cryogenic run instead; the coolant is also what tells the two recipes apart.
        RecipeRegistry.register(new MachineRecipe(MachineType.AIR_SEPARATOR,
                new ItemStack[0], new FluidStack(FluidRegistry.WATER, 1000), null,
                new ItemStack[0], new FluidStack(ModFluids.liquidHelium, 100), null,
                400, 0f));
    }

    private static void registerPetrochemistry() {
        // TODO(§4): crude oil is called "новый жидкий ресурс" and never given a way to obtain it -
        // there is no pump, no derrick and no oil worldgen. Direct coal liquefaction keeps it on
        // machines that already exist instead of adding a new extraction system.
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR,
                new ItemStack[]{new ItemStack(Items.coal, 4, 0)}, new FluidStack(ModFluids.hydrogen, 500), null,
                new ItemStack[0], new FluidStack(ModFluids.crudeOil, 1000), null,
                400, 0.05f));

        // §14: "отдельный рецепт с катализатором -> Polymer/PTFE/Photoresist". The catalyst is what
        // distinguishes it from the plain Diesel/Rubber run on the same machine and the same input.
        // TODO: a real catalyst is not consumed - it is here, for want of a tool-style slot.
        RecipeRegistry.register(new MachineRecipe(MachineType.REFINERY,
                new ItemStack[]{ModItems.dust.stackOf(Material.PLATINUM)}, new FluidStack(ModFluids.crudeOil, 1000), null,
                new ItemStack[]{comp("polymerPlate", 2), comp("ptfeSheet", 2)},
                new FluidStack(ModFluids.photoresist, 500), null,
                500, 0f));
    }

    // ---- helpers ----

    private static void ring(ItemStack output, ItemStack material) {
        OreRecipes.shaped(output, new Object[]{" X ", "X X", " X ", 'X', material});
    }

    private static void corners(ItemStack output, ItemStack material) {
        OreRecipes.shaped(output, new Object[]{"X X", "   ", "X X", 'X', material});
    }

    private static void hollow(ItemStack output, ItemStack material) {
        OreRecipes.shaped(output, new Object[]{"XXX", "X X", "XXX", 'X', material});
    }

    private static ItemStack comp(String name) {
        return new ItemStack(ModItems.component(name));
    }

    private static ItemStack comp(String name, int count) {
        return new ItemStack(ModItems.component(name), count);
    }

    private static ItemStack ingot(Material material) {
        return ModItems.ingot.stackOf(material);
    }

    private static ItemStack ingot(Material material, int count) {
        ItemStack stack = ModItems.ingot.stackOf(material);
        stack.stackSize = count;
        return stack;
    }

    private static ItemStack silicon(SiliconMaterial material) {
        return ModItems.siliconMaterial.stackOf(material);
    }

    private static ItemStack cable(CableType type) {
        return cable(type, 1);
    }

    private static ItemStack cable(CableType type, int count) {
        return new ItemStack(ModBlocks.cableSC, count, type.ordinal());
    }

    private static ItemStack halite() {
        return new ItemStack(ModBlocks.oreSC, 1, com.sc.util.OreEntry.HALITE.meta());
    }
}
