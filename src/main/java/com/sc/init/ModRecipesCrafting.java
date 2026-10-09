package com.sc.init;

import com.sc.energy.CableType;
import com.sc.energy.GeneratorType;
import com.sc.item.ItemArmorChipSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.Material;
import com.sc.util.PipeType;
import com.sc.util.SiliconMaterial;
import com.sc.util.WeaponType;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Step 11: every crafting-table recipe from §1, §2, §5 (generator corpus), §6 (Nano base +
 * chip tier I), §7 (weapons/Field Generator/Field Link Module) that doesn't belong on a
 * machine (those are steps 5-9's job, registered in ModRecipesMachines/Infrastructure/
 * UpgradeStation instead).
 *
 * Where §5-§7 don't give exact material counts for a non-chestplate armor piece or an exact
 * 3x3 layout, this file picks a reasonable proportional/ring-shaped one and says so inline -
 * same "TODO by analogy" spirit as the design doc's own gap-filling rule.
 */
public final class ModRecipesCrafting {

    private ModRecipesCrafting() {
    }

    public static void init() {
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:tankupgrade", TankUpgradeRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:storageupgrade", StorageUpgradeRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:battery", BatteryRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:chargecarry", ChargeCarryRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:bridgecharge", BridgeChargeRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:coordinatorcopy", CoordinatorCopyRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPELESS, "after:forge:shapelessore");
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:carry", CarryRecipe.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPED, "after:forge:shapedore");
        if (!returnsRegistered) {
            returnsRegistered = true;
            cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new CarryReturns());
        }
        cablesAndPipes();
        baseMaterials();
        passiveComponents();
        generators();
        newGenerators();
        quarry();
        armorAndChips();
        weaponsAndField();
        batteries();
        svBlocks();
        wireless();
        radiation();
        upgrades();
        tubeParts();
        tanks();
        manual();
        bridge();
        converter();
    }

    /**
     * The Energy Converter and its modules - only when some other energy is in the game (the RF API,
     * Mekanism or Galacticraft): without one the converter has no use, and no recipe.
     */
    private static void converter() {
        if (!com.sc.energy.ForeignEnergySC.anyPresent()) {
            return;
        }
        ItemStack ev = cable(CableType.SUPERCONDUCTOR);
        ItemStack controller = silicon(SiliconMaterial.CONTROLLER), memory = silicon(SiliconMaterial.MEMORY_CHIP);
        com.sc.item.ItemConverterModuleSC m = ModItems.converterModule;
        OreRecipes.shaped(new ItemStack(ModBlocks.energyConverter), "TKT", "EXE", "TRT",
                'T', comp("tiPlate"), 'K', comp("copperCoil"), 'E', ev, 'X', controller, 'R', Blocks.redstone_block);
        OreRecipes.shaped(m.stackOf(com.sc.item.ItemConverterModuleSC.Kind.AMPLIFIER), "KEK", "ECE", "KEK",
                'K', comp("copperCoil"), 'E', ev, 'C', comp("tantalumCapacitor"));
        OreRecipes.shaped(m.stackOf(com.sc.item.ItemConverterModuleSC.Kind.EFFICIENCY), "PDP", "DXD", "PDP",
                'P', comp("polymerPlate"), 'D', comp("dielectric"), 'X', controller);
        OreRecipes.shaped(m.stackOf(com.sc.item.ItemConverterModuleSC.Kind.CARD_MEKANISM), " K ", "MXM", " D ",
                'K', comp("copperCoil"), 'M', memory, 'X', controller, 'D', comp("dielectric"));
        OreRecipes.shaped(m.stackOf(com.sc.item.ItemConverterModuleSC.Kind.CARD_GALACTICRAFT), " H ", "MXM", " T ",
                'H', comp("heLoopModule"), 'M', memory, 'X', controller, 'T', comp("tiPlate"));
    }

    private static ItemStack cable(CableType type) {
        return cable(type, 1);
    }

    private static ItemStack cable(CableType type, int count) {
        return new ItemStack(ModBlocks.cableSC, count, type.ordinal());
    }

    private static ItemStack pipe(PipeType type) {
        return new ItemStack(ModBlocks.pipeSC, 1, type.ordinal());
    }

    private static ItemStack ingot(Material m) {
        return ModItems.ingot.stackOf(m);
    }

    private static ItemStack ingot(Material m, int count) {
        ItemStack stack = ModItems.ingot.stackOf(m);
        stack.stackSize = count;
        return stack;
    }

    private static ItemStack dust(Material m) {
        return ModItems.dust.stackOf(m);
    }

    private static ItemStack silicon(SiliconMaterial m) {
        return ModItems.siliconMaterial.stackOf(m);
    }

    private static ItemStack rubber() {
        return new ItemStack(ModItems.rubber);
    }

    private static ItemStack comp(String name) {
        return new ItemStack(ModItems.component(name));
    }

    private static ItemStack comp(String name, int count) {
        return new ItemStack(ModItems.component(name), count);
    }

    // ---- §1 ----

    private static void cablesAndPipes() {
        // Two ingots side by side, not one alone: a single copper ingot is other mods' nugget recipe (an ingot ->
        // 9 nuggets), and whichever was registered first took the grid.
        OreRecipes.shaped(cable(CableType.COPPER_BARE, 8), new Object[]{"II", 'I', ingot(Material.COPPER)});
        OreRecipes.shapeless(cable(CableType.COPPER_INSULATED), cable(CableType.COPPER_BARE), rubber());
        OreRecipes.shapeless(cable(CableType.SILVER), ingot(Material.SILVER), new ItemStack(ModItems.rubberBlue));
        OreRecipes.shapeless(cable(CableType.TUNGSTEN), ingot(Material.TUNGSTEN), new ItemStack(ModItems.rubberHeatResist));
        OreRecipes.shapeless(cable(CableType.SUPERCONDUCTOR),
                ingot(Material.NIOBIUM), ingot(Material.TIN), new ItemStack(ModItems.liquidHeCell));
        // above EV: IV (NbTi), QV (Quantum), XV (Exo) - each from two of the tier below
        OreRecipes.shapeless(cable(CableType.NIOBIUM_TITANIUM, 2), cable(CableType.SUPERCONDUCTOR), cable(CableType.SUPERCONDUCTOR),
                ingot(Material.NIOBIUM), ingot(Material.TITANIUM), new ItemStack(ModItems.liquidHeCell));
        OreRecipes.shapeless(cable(CableType.QUANTUM, 2), cable(CableType.NIOBIUM_TITANIUM), cable(CableType.NIOBIUM_TITANIUM),
                ingot(Material.PLATINUM), new ItemStack(ModItems.component("nb3SnPlate")), new ItemStack(ModItems.liquidHeCell));
        OreRecipes.shapeless(cable(CableType.EXO, 2), cable(CableType.QUANTUM), cable(CableType.QUANTUM),
                ingot(Material.HAFNIUM), ingot(Material.TANTALUM), silicon(SiliconMaterial.CONTROLLER));
        // SV: four Exo cables round a He loop module, hafnium in the corners
        OreRecipes.shaped(cable(CableType.SINGULAR, 4), new Object[]{"HEH", "ELE", "HEH",
                'E', cable(CableType.EXO), 'L', comp("heLoopModule"), 'H', ingot(Material.HAFNIUM)});

        // §12.4: "кольцом" = cross pattern, top/bottom/left/right filled, centre+corners empty.
        OreRecipes.shaped(pipe(PipeType.COPPER), new Object[]{" X ", "X X", " X ", 'X', ingot(Material.COPPER)});
        OreRecipes.shaped(pipe(PipeType.STEEL), new Object[]{" X ", "X X", " X ", 'X', ingot(Material.STEEL)});
        OreRecipes.shaped(pipe(PipeType.TITANIUM), new Object[]{" X ", "X X", " X ", 'X', ingot(Material.TITANIUM)});
        OreRecipes.shapeless(pipe(PipeType.PTFE), comp("ptfeSheet"), comp("ptfeSheet"));

        OreRecipes.shapeless(new ItemStack(ModBlocks.tubeItemPneumatic), rubber(), Blocks.glass, Items.iron_ingot);
    }

    private static void baseMaterials() {
        GameRegistry.addSmelting(dust(Material.ALUMINIUM), comp("ceramicRod"), 0.1F);

        // TODO(§13.5, added during bug-hunt): no exact recipe given anywhere for a Sputter
        // Target's backing plate - see ModItems' sputterBacking TODO for why the item itself
        // exists at all. Picked a cheap, plain-steel-plate crafting recipe by analogy with the
        // mod's other simple backing/frame components (comp("alFrame")/comp("tiFrame") etc.).
        // two side by side: a single steel ingot is other mods' steel-nugget recipe (Railcraft and others)
        OreRecipes.shaped(comp("sputterBacking", 2), new Object[]{"SS", 'S', ingot(Material.STEEL)});
    }

    // ---- §2 ----

    private static void passiveComponents() {
        OreRecipes.shaped(comp("resistor"), new Object[]{
                " W ", "RCR", " W ",
                'W', cable(CableType.COPPER_BARE), 'R', comp("ceramicRod"), 'C', dust(Material.CARBON)});

        OreRecipes.shaped(comp("capacitor"), new Object[]{
                " W ", "FDF", " W ",
                'W', cable(CableType.COPPER_BARE), 'F', new ItemStack(ModItems.alFoil), 'D', comp("dielectric")});

        OreRecipes.shapeless(comp("tantalumCapacitor"),
                dust(Material.TANTALUM), comp("dielectric"), cable(CableType.COPPER_BARE), cable(CableType.COPPER_BARE));
    }

    // ---- §5 (generator corpus - no chemistry per the doc's own heading) ----

    private static void generators() {
        // §5: "Combustion Chamber (Furnace-крафт)" - a furnace clad in iron. It used to be made by
        // smelting a steel ingot, which put the mod's only bootstrap generator behind the Blast
        // Furnace, i.e. behind a powered machine.
        OreRecipes.shaped(comp("combustionChamber"), new Object[]{
                " I ", "IFI", " I ", 'I', new ItemStack(Items.iron_ingot), 'F', Blocks.furnace});

        // TODO(§5): the doc says 4 Steel Ingot. Steel needs the Blast Furnace, which needs power,
        // which needs this generator - so it is iron here, keeping it buildable from the vanilla
        // start plus furnace-smelted copper. It also burns coal (TileEntityGeneratorSC) for the same
        // reason: its diesel only arrives much later, from the Refinery.
        OreRecipes.shapeless(generator(GeneratorType.COMBUSTION),
                Items.iron_ingot, Items.iron_ingot, Items.iron_ingot, Items.iron_ingot,
                comp("combustionChamber"), cable(CableType.COPPER_BARE), cable(CableType.COPPER_BARE));

        OreRecipes.shapeless(generator(GeneratorType.SOLAR_SI),
                silicon(SiliconMaterial.SI_WAFER), silicon(SiliconMaterial.SI_WAFER),
                silicon(SiliconMaterial.SI_WAFER), silicon(SiliconMaterial.SI_WAFER),
                comp("alFrame"), cable(CableType.COPPER_BARE));

        // A shapeless recipe matches ONE item per grid slot and ignores the stack size written into
        // its definition (vanilla's ShapelessRecipes.matches compares only item and damage) - the
        // old "ingot(STEEL, 6)" silently cost a single ingot. §5's six ingots are six slots.
        OreRecipes.shapeless(generator(GeneratorType.STEAM_TURBINE),
                ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL),
                comp("turbineBladeTungsten"), cable(CableType.SILVER), cable(CableType.SILVER));
        OreRecipes.shapeless(generator(GeneratorType.GAS_TURBINE),
                ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL), ingot(Material.STEEL),
                comp("turbineBladeTitanium"), cable(CableType.SILVER), cable(CableType.SILVER));

        OreRecipes.shapeless(generator(GeneratorType.SOLAR_GAAS),
                silicon(SiliconMaterial.GAAS_WAFER), silicon(SiliconMaterial.GAAS_WAFER),
                silicon(SiliconMaterial.GAAS_WAFER), silicon(SiliconMaterial.GAAS_WAFER),
                comp("tiFrame"), cable(CableType.TUNGSTEN));

        OreRecipes.shapeless(generator(GeneratorType.PLASMA_GENERATOR),
                comp("tiCasing"), comp("tiCasing"), comp("tiCasing"), comp("tiCasing"), comp("tiCasing"), comp("tiCasing"),
                comp("quartzChamber"), cable(CableType.TUNGSTEN), cable(CableType.TUNGSTEN));

        // §5: 40 Ti Casing + 8 Nb3Sn Coil + 8 Superconductor + 4 Quartz Chamber + 4 Li-Blanket is 64
        // items and could never fit a 3x3 grid - written as stack sizes it cost 5 items in total.
        // The doc calls it a multiblock "собирается в мире", i.e. staged: the Upgrade Station (EV)
        // assembles the bulk into a Fusion Core (see ModRecipesComponents), and the grid finishes
        // it with the remaining eight parts - 1 + 4 + 4 slots, the full 64-item cost.
        OreRecipes.shapeless(generator(GeneratorType.FUSION_REACTOR),
                comp("fusionCore"),
                comp("quartzChamber"), comp("quartzChamber"), comp("quartzChamber"), comp("quartzChamber"),
                comp("liBlanketModule"), comp("liBlanketModule"), comp("liBlanketModule"), comp("liBlanketModule"));
    }

    /**
     * The generators added after §15 - every ingredient is one of the mod's own items. Each
     * tier of a line is built round the one below (solar Nano / Quantum / Exo, the reactors),
     * like the energy storages.
     */
    private static void newGenerators() {
        ItemStack coil = comp("copperCoil"), controller = silicon(SiliconMaterial.CONTROLLER);
        // LV: coal etc. in a combustion chamber, copper windings
        OreRecipes.shaped(generator(GeneratorType.SOLID_FUEL), "CKC", "IXI", "CKC",
                'C', ingot(Material.COPPER), 'K', coil, 'I', ingot(Material.TIN), 'X', comp("combustionChamber"));
        // LV: a tin paddle wheel on a copper dynamo
        OreRecipes.shaped(generator(GeneratorType.WATER_WHEEL), "T T", "TKT", "LCL",
                'T', ingot(Material.TIN), 'K', coil, 'L', ingot(Material.LEAD), 'C', cable(CableType.COPPER_INSULATED));
        // MV: steel mast, generator of two coils; the rotor of four titanium blades
        OreRecipes.shaped(generator(GeneratorType.WIND_TURBINE), "SKS", "SXS", "SCS",
                'S', ingot(Material.STEEL), 'K', coil, 'X', comp("steelCasing"), 'C', cable(CableType.SILVER));
        OreRecipes.shaped(new ItemStack(ModItems.windRotor), " B ", "BSB", " B ",
                'B', comp("turbineBladeTitanium"), 'S', ingot(Material.STEEL));
        // MV: lead shielding round thermocouples; the capsule: lithium isotopes in zirconium and lead
        OreRecipes.shaped(generator(GeneratorType.RTG), "LKL", "LXL", "LCL",
                'L', ingot(Material.LEAD), 'K', coil, 'X', comp("steelCasing"), 'C', cable(CableType.SILVER));
        OreRecipes.shaped(new ItemStack(ModItems.isotopeCapsule), "LZL", "YPY", "LZL",
                'L', ingot(Material.LEAD), 'Z', ingot(Material.ZIRCONIUM), 'Y', ingot(Material.LITHIUM), 'P', comp("tiPlate"));
        // HV: Peltier elements - silicon dies between titanium plates
        OreRecipes.shaped(generator(GeneratorType.THERMOELECTRIC), "PDP", "DXD", "CDC",
                'P', comp("tiPlate"), 'D', silicon(SiliconMaterial.DIE), 'X', controller, 'C', cable(CableType.TUNGSTEN));
        // HV: a quartz chamber for the lava in titanium casings
        OreRecipes.shaped(generator(GeneratorType.GEOTHERMAL), "TQT", "KTK", "CTC",
                'T', comp("tiCasing"), 'Q', comp("quartzChamber"), 'K', coil, 'C', cable(CableType.TUNGSTEN));
        // HV: platinum catalyst on a polymer membrane
        OreRecipes.shaped(generator(GeneratorType.FUEL_CELL), "PMP", "MXM", "CTC",
                'P', ingot(Material.PLATINUM), 'M', comp("polymerPlate"), 'X', controller, 'C', cable(CableType.TUNGSTEN),
                'T', comp("tiPlate"));
        // Solar Nano (IV) / Quantum (QV) / Exo (XV): four of the panel below round the new tier's parts
        OreRecipes.shaped(generator(GeneratorType.SOLAR_NANO), "SHS", "CXC", "SHS",
                'S', generator(GeneratorType.SOLAR_GAAS), 'H', comp("hfo2Die"), 'C', cable(CableType.NIOBIUM_TITANIUM), 'X', controller);
        OreRecipes.shaped(generator(GeneratorType.SOLAR_QUANTUM), "SHS", "CXC", "SHS",
                'S', generator(GeneratorType.SOLAR_NANO), 'H', comp("heLoopModule"), 'C', cable(CableType.QUANTUM), 'X', controller);
        OreRecipes.shaped(generator(GeneratorType.SOLAR_EXO), "SHS", "CXC", "SHS",
                'S', generator(GeneratorType.SOLAR_QUANTUM), 'H', ingot(Material.HAFNIUM), 'C', cable(CableType.EXO), 'X', controller);
        // IV: two plasma generators and Nb3Sn coils
        // РЦ-2: the reactors below are built round generators - their EU and fuel go into the new one (CarryRecipe)
        carry(GeneratorType.PLASMA_REACTOR, "NGN", "CXC", "NGN",
                'N', comp("nb3SnCoil"), 'G', generator(GeneratorType.PLASMA_GENERATOR), 'C', cable(CableType.NIOBIUM_TITANIUM), 'X', controller);
        // QV: the fusion reactor made a tokamak; its coils (8 around it)
        carry(GeneratorType.TOKAMAK, "NCN", "XFX", "NCN",
                'N', comp("nb3SnCoil"), 'C', cable(CableType.QUANTUM), 'X', controller, 'F', generator(GeneratorType.FUSION_REACTOR));
        OreRecipes.shaped(new ItemStack(ModBlocks.tokamakCoil), "NTN", "CHC", "NTN",
                'N', comp("nb3SnCoil"), 'T', comp("tiCasing"), 'C', cable(CableType.NIOBIUM_TITANIUM), 'H', new ItemStack(ModItems.liquidHeCell));
        // XV: the Tokamak XV - a tokamak with Nb3Sn coils, Exo cable and two fusion cores
        carry(GeneratorType.TOKAMAK_XV, "NCN", "FTF", "NCN",
                'N', comp("nb3SnCoil"), 'C', cable(CableType.EXO), 'F', comp("fusionCore"), 'T', generator(GeneratorType.TOKAMAK));
        // XV: a tokamak with a second fusion core, hafnium and Exo cable
        carry(GeneratorType.EXO_REACTOR, "HCH", "XTX", "HFH",
                'H', ingot(Material.HAFNIUM), 'C', cable(CableType.EXO), 'X', controller, 'T', generator(GeneratorType.TOKAMAK),
                'F', comp("fusionCore"));
        // SV: the Singular Reactor - an Exo Reactor and a Tokamak XV round a fusion core, a nether star, Singular cable
        carry(GeneratorType.SINGULAR_REACTOR, "CNC", "EFT", "CCC",
                'C', cable(CableType.SINGULAR), 'N', new ItemStack(Items.nether_star), 'E', generator(GeneratorType.EXO_REACTOR),
                'F', comp("fusionCore"), 'T', generator(GeneratorType.TOKAMAK_XV));
        // its gravity coils (16 in the build), two at a time: tokamak coils, hafnium, Singular cable, a fusion core
        OreRecipes.shaped(new ItemStack(ModBlocks.gravityCoil, 2), "HSH", "KFK", "HSH",
                'H', ingot(Material.HAFNIUM), 'S', cable(CableType.SINGULAR), 'K', new ItemStack(ModBlocks.tokamakCoil),
                'F', comp("fusionCore"));
        // ... or one at a time with Exo cable in place of the Singular (Г2: the bridge's ring before a Singular reactor)
        OreRecipes.shaped(new ItemStack(ModBlocks.gravityCoil, 1), "HSH", "KFK", "HSH",
                'H', ingot(Material.HAFNIUM), 'S', cable(CableType.EXO), 'K', new ItemStack(ModBlocks.tokamakCoil),
                'F', comp("fusionCore"));
        // generator upgrades
        ItemStack transistor = silicon(SiliconMaterial.TRANSISTOR);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE), "KKK", "WTW", "WCW",
                'K', comp("capacitor"), 'W', cable(CableType.SILVER), 'T', transistor, 'C', comp("combustionChamber"));
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ECONOMIZER), "RRR", "WTW", "WSW",
                'R', comp("resistor"), 'W', cable(CableType.SILVER), 'T', transistor, 'S', comp("sensor"));
        // the field generator's charge booster: a field link module over a charger's worth of coils and cells
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.CHARGE_BOOSTER), " L ", "WTW", "KEK",
                'L', new ItemStack(ModItems.fieldLinkModule), 'W', cable(CableType.TUNGSTEN), 'T', transistor,
                'K', comp("copperCoil"), 'E', comp("energyCellHV"));
    }

    /** The quarry line, its drill heads, modules, the ore scanner and the area card - the mod's own items only. */
    private static void quarry() {
        ItemStack transistor = silicon(SiliconMaterial.TRANSISTOR), controller = silicon(SiliconMaterial.CONTROLLER);
        ItemStack wire = cable(CableType.COPPER_INSULATED);
        // РЦ-4: as ingredients the heads and the diamond blade fit at any wear (their damage is the wear)
        ItemStack[] heads = new ItemStack[ModItems.DRILL_HEADS.size()], worn = new ItemStack[heads.length];
        for (int i = 0; i < heads.length; i++) {
            heads[i] = new ItemStack(ModItems.DRILL_HEADS.get(i));
            worn[i] = new ItemStack(ModItems.DRILL_HEADS.get(i), 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        }
        OreRecipes.shaped(heads[0], "S S", "SXS", " S ", 'S', ingot(Material.STEEL), 'X', comp("steelCasing"));
        OreRecipes.shaped(heads[1], "W W", "WXW", " W ", 'W', ingot(Material.TUNGSTEN), 'X', worn[0]);
        OreRecipes.shaped(heads[2], "D D", "DXD", " D ",
                'D', new ItemStack(ModItems.TOOLS.get(com.sc.util.SCToolType.DIAMOND_BLADE), 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE),
                'X', worn[1]);
        OreRecipes.shaped(heads[3], "HEH", "CXC", "HEH",
                'H', ingot(Material.HAFNIUM), 'E', comp("energyCellHV"), 'C', controller, 'X', worn[2]);
        ItemStack lv = new ItemStack(ModBlocks.quarrySC, 1, 0), mv = new ItemStack(ModBlocks.quarrySC, 1, 1),
                hv = new ItemStack(ModBlocks.quarrySC, 1, 2), ev = new ItemStack(ModBlocks.quarrySC, 1, 3);
        OreRecipes.shaped(lv, "STS", "KXK", "CHC",
                'S', ingot(Material.STEEL), 'T', transistor, 'K', comp("copperCoil"), 'X', comp("steelCasing"), 'C', wire, 'H', worn[0]);
        // РЦ-2: a quarry a tier up keeps the old one's EU, tanks, settings and filter (CarryRecipe.COPY)
        GameRegistry.addRecipe(new CarryRecipe(mv, CarryRecipe.COPY, com.sc.block.BlockQuarrySC.class, null, "PCP", "KXK", "PCP",
                'P', comp("tiPlate"), 'C', cable(CableType.SILVER), 'K', comp("copperCoil"), 'X', lv));
        GameRegistry.addRecipe(new CarryRecipe(hv, CarryRecipe.COPY, com.sc.block.BlockQuarrySC.class, null, "PCP", "KXK", "PCP",
                'P', comp("wTiPlate"), 'C', cable(CableType.TUNGSTEN), 'K', controller, 'X', mv));
        GameRegistry.addRecipe(new CarryRecipe(ev, CarryRecipe.COPY, com.sc.block.BlockQuarrySC.class, null, "PCP", "KXK", "PCP",
                'P', comp("tiCasing"), 'C', cable(CableType.SUPERCONDUCTOR), 'K', comp("nb3SnCoil"), 'X', hv));

        com.sc.item.ItemQuarryModuleSC m = ModItems.quarryModule;
        Object[][] modules = {
                {com.sc.item.ItemQuarryModuleSC.Kind.SPEED, comp("copperCoil"), ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER)},
                {com.sc.item.ItemQuarryModuleSC.Kind.FORTUNE, comp("lens"), ingot(Material.PLATINUM)},
                {com.sc.item.ItemQuarryModuleSC.Kind.SILK, comp("sensor"), comp("ptfeSheet")},
                {com.sc.item.ItemQuarryModuleSC.Kind.CRUSH, comp("copperCoil"),
                        new ItemStack(ModBlocks.machineSC, 1, com.sc.machine.MachineType.CRUSHER.ordinal())},
                {com.sc.item.ItemQuarryModuleSC.Kind.WASH, m.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.CRUSH),
                        new ItemStack(ModBlocks.machineSC, 1, com.sc.machine.MachineType.ORE_WASHER.ordinal())},
                {com.sc.item.ItemQuarryModuleSC.Kind.PUMP, comp("copperCoil"), pipe(PipeType.STEEL)},
                {com.sc.item.ItemQuarryModuleSC.Kind.MAGNET, comp("copperCoil"), ingot(Material.NEODYMIUM)},
                {com.sc.item.ItemQuarryModuleSC.Kind.RADIUS, comp("sensor"), comp("tiPlate")},
                {com.sc.item.ItemQuarryModuleSC.Kind.SILENT, comp("polymerPlate"), new ItemStack(ModItems.rubber)},
                {com.sc.item.ItemQuarryModuleSC.Kind.AUTOSTOP, comp("sensor"), controller},
                {com.sc.item.ItemQuarryModuleSC.Kind.TRASH, comp("sensor"), comp("combustionChamber")},
                {com.sc.item.ItemQuarryModuleSC.Kind.CENTRIFUGE, m.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.WASH),
                        ModRecipesMachineBlocks.block(com.sc.machine.MachineType.CENTRIFUGE)},
                {com.sc.item.ItemQuarryModuleSC.Kind.VEIN, comp("sensor"), new ItemStack(ModItems.oreScanner)},
                {com.sc.item.ItemQuarryModuleSC.Kind.DOUBLE, comp("nb3SnCoil"), worn[1]},
                {com.sc.item.ItemQuarryModuleSC.Kind.FLUID_GUARD, comp("ptfeSheet"), comp("steelCasing")},
                {com.sc.item.ItemQuarryModuleSC.Kind.GENTLE, comp("sensor"), comp("lens")},
                {com.sc.item.ItemQuarryModuleSC.Kind.REPAIR, controller, ingot(Material.TUNGSTEN)},
                {com.sc.item.ItemQuarryModuleSC.Kind.ECONOMY, comp("capacitor"), comp("energyCellMV")},
                {com.sc.item.ItemQuarryModuleSC.Kind.RESONATOR, comp("lens"), comp("heLoopModule")},
                {com.sc.item.ItemQuarryModuleSC.Kind.STABILIZER, controller, comp("nb3SnCoil")},
                {com.sc.item.ItemQuarryModuleSC.Kind.DEEP_SCAN, comp("hfo2Die"), new ItemStack(ModItems.oreScanner)},
                {com.sc.item.ItemQuarryModuleSC.Kind.TANK, comp("steelCasing"), new ItemStack(ModBlocks.tankSC, 1, 0)},
                {com.sc.item.ItemQuarryModuleSC.Kind.FLUID_VEIN, comp("sensor"), m.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.PUMP)},
                {com.sc.item.ItemQuarryModuleSC.Kind.CHUNK_LOADER, new ItemStack(Items.ender_eye), new ItemStack(Blocks.obsidian)},
        };
        for (Object[] r : modules) {
            ItemStack out = m.stackOf((com.sc.item.ItemQuarryModuleSC.Kind) r[0]);
            if (r[0] == com.sc.item.ItemQuarryModuleSC.Kind.TANK) {     // РЦ-2: the module keeps no fluid - an empty tank only
                GameRegistry.addRecipe(new CarryRecipe(out, CarryRecipe.EMPTY, com.sc.block.BlockTankSC.class, null, " A ", "WMW", " T ",
                        'A', r[1], 'W', wire, 'M', r[2], 'T', transistor));
                continue;
            }
            OreRecipes.shaped(out, " A ", "WMW", " T ",
                    'A', r[1], 'W', wire, 'M', r[2], 'T', transistor);
        }
        // the Exo Drilling Rig: an EV quarry round a fusion core, Exo cable and an Exo drill head (the quarry's contents come along)
        GameRegistry.addRecipe(new CarryRecipe(new ItemStack(ModBlocks.quarrySC, 1, 4), CarryRecipe.COPY, com.sc.block.BlockQuarrySC.class, null,
                "HFH", "XQX", "CEC",
                'H', ingot(Material.HAFNIUM), 'F', comp("fusionCore"), 'X', controller, 'Q', ev,
                'C', cable(CableType.EXO), 'E', worn[3]));
        // its ore lenses: a lens, a controller and four of that ore
        for (com.sc.util.OreEntry o : com.sc.util.OreEntry.values()) {
            ItemStack ore = new ItemStack(ModBlocks.oreSC, 1, o.meta());
            OreRecipes.shaped(new ItemStack(ModItems.oreLens, 1, o.meta()), " O ", "OLO", " X ",
                    'O', ore, 'L', comp("lens"), 'X', controller);
        }
        OreRecipes.shaped(new ItemStack(ModItems.oreScanner), " L ", "SXS", " E ",
                'L', comp("lens"), 'S', comp("sensor"), 'X', controller, 'E', comp("energyCellLV"));
        OreRecipes.shapeless(new ItemStack(ModItems.areaCard), comp("polymerPlate"), silicon(SiliconMaterial.MEMORY_CHIP), wire);
    }

    private static ItemStack generator(GeneratorType type) {
        return ModBlocks.generatorStack(type, 1);
    }

    /** A reactor built round generators (CarryRecipe.GENERATOR). */
    private static void carry(GeneratorType type, Object... recipe) {
        GameRegistry.addRecipe(new CarryRecipe(generator(type), CarryRecipe.GENERATOR, com.sc.block.BlockGeneratorSC.class, type, recipe));
    }

    // ---- §6 (Nano base corpus + Chip tier I - Quantum/Exo/tier II/III are Upgrade Station recipes) ----

    private static void armorAndChips() {
        // Only "Nano Chestplate" is given an exact formula in §6; the other 3 pieces are this
        // session's proportional extrapolation (helmet/boots ~ half the chestplate, leggings ~ 3/4).
        com.sc.item.ItemArmorSC[] nano = ModItems.ARMOR.get(ArmorSuit.NANO);
        // helmet - the Focus Lens is its visor, and is also what keeps it from being the exact same
        // ingredient list as the boots (which made the boots uncraftable: the grid returns the first match)
        OreRecipes.shapeless(new ItemStack(nano[0]),
                comp("polymerPlate"), comp("polymerPlate"), comp("tiPlate"), comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER),
                comp("focusLens"));
        OreRecipes.shapeless(new ItemStack(nano[1]), // chestplate - §6's own example
                comp("polymerPlate"), comp("polymerPlate"), comp("polymerPlate"), comp("polymerPlate"),
                comp("tiPlate"), comp("tiPlate"), comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER));
        OreRecipes.shapeless(new ItemStack(nano[2]), // leggings
                comp("polymerPlate"), comp("polymerPlate"), comp("polymerPlate"),
                comp("tiPlate"), comp("tiPlate"), comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER));
        OreRecipes.shapeless(new ItemStack(nano[3]), // boots
                comp("polymerPlate"), comp("polymerPlate"), comp("tiPlate"), comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER));

        // §6: Chip тир I = Die (нужного типа) + Ceramic Package + Controller. The mod has a single
        // generic Die, so all five types used to be the exact same recipe and the grid only ever
        // made the first (Sensor) - Power/Defense/Mobility/Utility, and every tier above them, were
        // unobtainable. TODO(§13.6): in place of typed dies, each type takes one part that says
        // what it does.
        for (ChipType type : ChipType.values()) {
            ItemStack chipTier1 = new ItemStack(ModItems.armorChip, 1, ItemArmorChipSC.metaFor(type, 1));
            OreRecipes.shapeless(chipTier1,
                    silicon(SiliconMaterial.DIE), comp("ceramicPackage"), silicon(SiliconMaterial.CONTROLLER),
                    chipSpecialisation(type));
        }
    }

    private static ItemStack chipSpecialisation(ChipType type) {
        switch (type) {
            case SENSOR: return comp("sensor");
            case POWER: return comp("tantalumCapacitor");
            case DEFENSE: return comp("tiPlate");
            case MOBILITY: return comp("copperCoil");
            // life-support chips (docs/plan-armor-gases.md)
            case CRYO_LOOP: return comp("heLoopModule");
            case CRYO_TANK: return comp("ptfeSheet");
            case OXYGEN_REGEN: return comp("quartzChamber");
            case RECUPERATOR: return comp("nb3SnCoil");
            default: return comp("resistor");
        }
    }

    // ---- §7 ----

    /**
     * Portable batteries: the LV cell from an energy cell and a silicon wafer; each tier after it
     * round two of the tier below (their charge comes along - BatteryRecipeSC).
     */
    private static void batteries() {
        net.minecraft.item.Item b = ModItems.battery;
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 0), " C ", "IWI", "IEI",
                'C', cable(CableType.COPPER_BARE), 'I', new ItemStack(Items.iron_ingot),
                'W', silicon(SiliconMaterial.SI_WAFER), 'E', comp("energyCellLV")));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 1), " C ", "BEB", "KSK",
                'C', cable(CableType.COPPER_INSULATED), 'B', new ItemStack(b, 1, 0), 'E', comp("energyCellMV"),
                'K', comp("copperCoil"), 'S', comp("steelCasing")));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 2), " C ", "BEB", "GDG",
                'C', cable(CableType.TUNGSTEN), 'B', new ItemStack(b, 1, 1), 'E', comp("energyCellHV"),
                'G', new ItemStack(Items.gold_ingot), 'D', new ItemStack(Items.diamond)));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 3), " C ", "BXB", "QTQ",
                'C', cable(CableType.SUPERCONDUCTOR), 'B', new ItemStack(b, 1, 2), 'X', silicon(SiliconMaterial.CONTROLLER),
                'Q', new ItemStack(Items.quartz), 'T', comp("tantalumCapacitor")));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 4), " C ", "BXB", "NMN",
                'C', cable(CableType.QUANTUM), 'B', new ItemStack(b, 1, 3), 'X', silicon(SiliconMaterial.CONTROLLER),
                'N', comp("nb3SnCoil"), 'M', silicon(SiliconMaterial.MEMORY_CHIP)));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 5), " C ", "BSB", "HXH",
                'C', cable(CableType.EXO), 'B', new ItemStack(b, 1, 4), 'S', new ItemStack(Items.nether_star),
                'H', ingot(Material.HAFNIUM), 'X', silicon(SiliconMaterial.CONTROLLER)));
        GameRegistry.addRecipe(new BatteryRecipeSC(new ItemStack(b, 1, 6), " C ", "BFB", "HSH",
                'C', cable(CableType.SINGULAR), 'B', new ItemStack(b, 1, 5), 'F', comp("fusionCore"),
                'H', ingot(Material.HAFNIUM), 'S', new ItemStack(Items.nether_star)));
    }

    /** The tier's own cable (the first one of that tier), or the best cable below it if it has none (SV: Exo). */
    private static CableType cableOf(com.sc.energy.Tier t) {
        CableType best = CableType.COPPER_BARE;
        for (CableType c : CableType.values()) {
            if (c.tier == t) {
                return c;
            }
            if (c.tier.ordinal() < t.ordinal() && c.tier.ordinal() >= best.tier.ordinal()) {
                best = c;
            }
        }
        return best;
    }

    /**
     * SV (Singular) blocks, each built round its XV one: the storage round an XV storage with two
     * exo cores (XV batteries), a fusion core, hafnium and Exo cable (its charge comes along); the
     * charge pad, like the others, the SV storage under pressure plates; the XV-SV transformer round
     * the QV-XV one with controllers, hafnium and Exo cable. The cable is Exo (cableOf) as long as
     * there is no SV cable - none is needed. Only while Tier.SV_CONTENT_READY.
     */
    private static void svBlocks() {
        com.sc.energy.Tier sv = com.sc.energy.Tier.SV;
        if (!sv.isContentReady()) {
            return;
        }
        ItemStack xvStorage = new ItemStack(ModBlocks.energyStorageSC, 1, com.sc.energy.Tier.XV.ordinal());
        ItemStack svStorage = new ItemStack(ModBlocks.energyStorageSC, 1, sv.ordinal());
        ItemStack exoCore = new ItemStack(ModItems.battery, 1, 5);
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(svStorage, "CFC", "BXB", "HCH",
                'C', cable(CableType.EXO), 'F', comp("fusionCore"), 'B', exoCore, 'X', xvStorage,
                'H', ingot(Material.HAFNIUM)));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(new ItemStack(ModBlocks.chargePadSC, 1, sv.ordinal()), "PPP", "CXC",
                'P', new ItemStack(Blocks.heavy_weighted_pressure_plate), 'C', cable(cableOf(sv)), 'X', svStorage.copy()));
        // the transformer's meta is its low tier: XV-SV = XV's ordinal, built round QV-XV
        OreRecipes.shaped(new ItemStack(ModBlocks.transformerSC, 1, com.sc.energy.Tier.XV.ordinal()), "HCH", "EXE", "HCH",
                'H', ingot(Material.HAFNIUM), 'C', cable(CableType.EXO), 'E', silicon(SiliconMaterial.CONTROLLER),
                'X', new ItemStack(ModBlocks.transformerSC, 1, com.sc.energy.Tier.QV.ordinal()));
    }

    /**
     * Wireless energy: a transmitter / receiver of each tier round an energy storage of that tier
     * with the tier's cable (an ender pearl sends, an eye of ender receives); the link card; the
     * quantum translator round an XV storage; the entangled crystal round a quantum cell.
     */
    private static void wireless() {
        OreRecipes.shapeless(new ItemStack(ModItems.linkCard), new ItemStack(Items.paper), new ItemStack(Items.redstone),
                silicon(SiliconMaterial.TRANSISTOR), cable(CableType.COPPER_BARE));
        for (com.sc.energy.Tier t : com.sc.energy.Tier.values()) {
            if (!t.isContentReady()) {
                continue;                     // a tier whose blocks are hidden (Tier.SV_CONTENT_READY) has no Tx / Rx recipe
            }
            CableType wire = cableOf(t);
            ItemStack storage = new ItemStack(ModBlocks.energyStorageSC, 1, t.ordinal());
            // РЦ-2: the storage's charge goes into the new block, its upgrades back to the crafter
            GameRegistry.addRecipe(new CarryRecipe(new ItemStack(ModBlocks.wirelessTx, 1, t.ordinal()), CarryRecipe.ENERGY,
                    com.sc.block.BlockEnergyStorageSC.class, null, " E ", "CSC", "IXI",
                    'E', new ItemStack(Items.ender_pearl), 'C', cable(wire), 'S', storage,
                    'I', new ItemStack(Items.iron_ingot), 'X', silicon(SiliconMaterial.CONTROLLER)));
            GameRegistry.addRecipe(new CarryRecipe(new ItemStack(ModBlocks.wirelessRx, 1, t.ordinal()), CarryRecipe.ENERGY,
                    com.sc.block.BlockEnergyStorageSC.class, null, " E ", "CSC", "IXI",
                    'E', new ItemStack(Items.ender_eye), 'C', cable(wire), 'S', storage,
                    'I', new ItemStack(Items.iron_ingot), 'X', silicon(SiliconMaterial.CONTROLLER)));
        }
        GameRegistry.addRecipe(new CarryRecipe(new ItemStack(ModBlocks.quantumTranslator), CarryRecipe.ENERGY,
                com.sc.block.BlockEnergyStorageSC.class, null, "HCH", "XSX", "HCH",
                'H', ingot(Material.HAFNIUM), 'C', cable(CableType.EXO), 'X', silicon(SiliconMaterial.CONTROLLER),
                'S', new ItemStack(ModBlocks.energyStorageSC, 1, com.sc.energy.Tier.XV.ordinal())));
        OreRecipes.shaped(new ItemStack(ModItems.entangledCrystal), "PDP", "EQE", "PMP",
                'P', new ItemStack(Items.ender_pearl), 'D', new ItemStack(Items.diamond), 'E', new ItemStack(Items.ender_eye),
                'Q', new ItemStack(ModItems.battery, 1, 4), 'M', silicon(SiliconMaterial.MEMORY_CHIP));
    }

    /**
     * Radiation: the lead block and back, lead glass, the lead suit (lead with a leather lining, the
     * helmet with a lead-glass window), the dosimeter, the radioprotector (activated carbon), the
     * lead casing for RTGs and reactors, the decontamination shower.
     */
    private static void radiation() {
        ItemStack lead = ingot(Material.LEAD);
        OreRecipes.shaped(new ItemStack(ModBlocks.leadBlock), "LLL", "LLL", "LLL", 'L', lead);
        OreRecipes.shapeless(ingot(Material.LEAD, 9), new ItemStack(ModBlocks.leadBlock));
        OreRecipes.shaped(new ItemStack(ModBlocks.leadGlass, 5), "GLG", "LGL", "GLG", 'G', new ItemStack(Blocks.glass), 'L', lead);
        ItemStack leather = new ItemStack(Items.leather), window = new ItemStack(ModBlocks.leadGlass);
        OreRecipes.shaped(new ItemStack(ModItems.leadSuit[0]), "LKL", "LGL", 'L', lead, 'K', leather, 'G', window);
        OreRecipes.shaped(new ItemStack(ModItems.leadSuit[1]), "L L", "LKL", "LLL", 'L', lead, 'K', leather);
        OreRecipes.shaped(new ItemStack(ModItems.leadSuit[2]), "LKL", "L L", "L L", 'L', lead, 'K', leather);
        OreRecipes.shaped(new ItemStack(ModItems.leadSuit[3]), "K K", "L L", "L L", 'L', lead, 'K', leather);
        OreRecipes.shaped(new ItemStack(ModItems.dosimeter), "IGI", "RTR", "ILI",
                'I', new ItemStack(Items.iron_ingot), 'G', new ItemStack(Blocks.glass_pane), 'R', new ItemStack(Items.redstone),
                'T', silicon(SiliconMaterial.TRANSISTOR), 'L', lead);
        OreRecipes.shapeless(new ItemStack(ModItems.radioprotector, 2), new ItemStack(Items.glass_bottle), new ItemStack(Items.sugar),
                new ItemStack(Items.glowstone_dust), dust(Material.CARBON));
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.RAD_SHIELDING), "LBL", "ITI", "LBL",
                'L', lead, 'B', "blockLead", 'I', new ItemStack(Items.iron_ingot), 'T', silicon(SiliconMaterial.TRANSISTOR));
        OreRecipes.shaped(new ItemStack(ModBlocks.shower), "IPI", "GBG", "ICI",
                'I', new ItemStack(Items.iron_ingot), 'P', pipe(PipeType.COPPER), 'G', window, 'B', new ItemStack(Items.bucket),
                'C', cable(CableType.SILVER));
        // the Armour Service Station: an MV charge pad on top, steel tanks and pistons (the pumps), a controller, MV cable
        OreRecipes.shaped(new ItemStack(ModBlocks.armorStation), "KPK", "TXT", "ICI",
                'K', new ItemStack(Blocks.piston), 'P', new ItemStack(ModBlocks.chargePadSC, 1, com.sc.energy.Tier.MV.ordinal()),
                'T', new ItemStack(ModBlocks.tankSC, 1, 0), 'X', silicon(SiliconMaterial.CONTROLLER), 'I', new ItemStack(Items.iron_ingot),
                'C', cable(cableOf(com.sc.energy.Tier.MV)));
        singularCrafts();
    }

    /**
     * The Singular armour's blocks and cell (docs/plan-singular-armor.md, СС1 / ГС1 / item 7):
     *  - the Singular Service Station round an Armour Service Station: gravity coils, an Exo core (its
     *    charge goes into the station's buffer - ChargeCarryRecipeSC), Singular cable, a fusion core, hafnium;
     *    the Armour Station's own EU, tanks and modules go into the new station too (carryFrom);
     *  - the Gravitational Stabiliser: hafnium round a gravity coil, a Compressed Matter Capsule, Singular cable;
     *  - the Singular Matter cell (empty): titanium plates and glass round a hafnium ingot.
     * The Singular suit itself comes from the station (Б-1: an Exo piece converted), not from the grid.
     */
    private static void singularCrafts() {
        ItemStack coil = new ItemStack(ModBlocks.gravityCoil), hf = ingot(Material.HAFNIUM);
        int stationMax = new com.sc.tileentity.TileEntitySingularStationSC().getMaxEnergyStored();
        GameRegistry.addRecipe(new ChargeCarryRecipeSC(new ItemStack(ModBlocks.singularStation), stationMax, "GXG", "CAC", "HFH",
                'G', coil, 'X', new ItemStack(ModItems.battery, 1, com.sc.util.SingularStationMath.EXO_CORE_META),
                'C', cable(CableType.SINGULAR), 'A', new ItemStack(ModBlocks.armorStation), 'F', comp("fusionCore"), 'H', hf)
                .carryFrom(net.minecraft.item.Item.getItemFromBlock(ModBlocks.armorStation)));     // its EU, tanks, modules go over
        OreRecipes.shaped(new ItemStack(ModBlocks.gravStabiliser), "HMH", " G ", "HCH",
                'H', hf, 'M', comp("matterCapsule"), 'G', coil, 'C', cable(CableType.SINGULAR));
        OreRecipes.shaped(new ItemStack(ModItems.singularCell), " T ", "GHG", " T ",
                'T', comp("tiPlate"), 'G', new ItemStack(Blocks.glass_pane), 'H', hf);
        // nine singularity crumbs (the Singular drill's) press into a clot - the Matter Compressor makes it 100 mB of singular matter.
        // No recipe makes the Singular blade / drill: only the Singular station's conversion of the Exo ones.
        ItemStack crumb = new ItemStack(ModItems.singularCrumb);
        OreRecipes.shapeless(new ItemStack(ModItems.singularClot), crumb, crumb, crumb, crumb, crumb, crumb, crumb, crumb, crumb);
    }

    /**
     * Blocks of metal as a block of iron: 9 ingots into a block and back. The ingots go by the ore
     * dictionary (other mods' copper makes our block too) - unless another mod already has its own
     * block of that metal: then that mod's recipe keeps the 9 ingots (two recipes on one grid
     * clash) and ours only turns back into ingots.
     */
    private static boolean metalDone;

    /**
     * Blocks of metal - postInit (SCMod), once every mod has put its blocks in the ore dictionary
     * and its recipes in: our block into 9 ingots always; 9 ingots into our block only while no
     * other mod has a block of that metal (two recipes on one grid clash). Otherwise two of the
     * other mod's blocks - only those truly worth 9 ingots (made of 9, or coming apart into 9) -
     * one on the other, make two of ours.
     */
    public static void metalBlocksLate() {
        if (metalDone) {
            return;
        }
        metalDone = true;
        for (Material m : com.sc.block.BlockMetalSC.METALS) {
            ItemStack block = com.sc.block.BlockMetalSC.stackOf(m, 1);
            OreRecipes.shapeless(ingot(m, 9), block);
            java.util.List<ItemStack> others = otherBlocks(m, block);
            if (others.isEmpty()) {
                OreRecipes.shaped(block, "III", "III", "III", 'I', ingot(m));
                continue;
            }
            ItemStack two = block.copy();
            two.stackSize = 2;
            for (ItemStack theirs : others) {
                if (theirs.getItemDamage() != net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE && worthNine(m, theirs)) {
                    ItemStack one = theirs.copy();
                    one.stackSize = 1;
                    GameRegistry.addRecipe(new net.minecraftforge.oredict.ShapedOreRecipe(two, "B", "B", 'B', one));
                }
            }
        }
    }

    /** The ore-dictionary names of a metal's block and ingot (aluminium has the American spelling too). */
    private static String[] names(Material m, String prefix) {
        return m == Material.ALUMINIUM ? new String[]{prefix + "Aluminium", prefix + "Aluminum"} : new String[]{prefix + m.oreDictName};
    }

    /** Other mods' blocks of this metal (ours left out). */
    public static java.util.List<ItemStack> otherBlocks(Material m, ItemStack ours) {
        java.util.List<ItemStack> list = new java.util.ArrayList<ItemStack>();
        for (String name : names(m, "block")) {
            for (ItemStack s : net.minecraftforge.oredict.OreDictionary.getOres(name)) {
                if (s.getItem() != ours.getItem()) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private static final net.minecraft.inventory.Container NO_CONTAINER = new net.minecraft.inventory.Container() {
        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) {
            return true;
        }
    };

    /** What a crafting grid holding these stacks (slot order) makes, or null. */
    public static ItemStack craft(ItemStack... grid) {
        net.minecraft.inventory.InventoryCrafting inv = new net.minecraft.inventory.InventoryCrafting(NO_CONTAINER, 3, 3);
        for (int i = 0; i < grid.length; i++) {
            inv.setInventorySlotContents(i, grid[i] == null ? null : grid[i].copy());
        }
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
            net.minecraft.item.crafting.IRecipe r = (net.minecraft.item.crafting.IRecipe) o;
            try {
                if (r.matches(inv, null)) {
                    return r.getCraftingResult(inv);
                }
            } catch (Throwable t) {
                // a recipe that wants a real world: not ours to judge
            }
        }
        return null;
    }

    private static boolean isIngotOf(ItemStack s, Material m) {
        if (s == null) {
            return false;
        }
        for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(s)) {
            for (String name : names(m, "ingot")) {
                if (net.minecraftforge.oredict.OreDictionary.getOreName(id).equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Another mod's block is worth 9 ingots: it comes apart into 9, or 9 of our ingots make it. */
    public static boolean worthNine(Material m, ItemStack theirs) {
        ItemStack apart = craft(theirs);
        if (apart != null && apart.stackSize == 9 && isIngotOf(apart, m)) {
            return true;
        }
        ItemStack in = ingot(m);
        ItemStack made = craft(in, in, in, in, in, in, in, in, in);
        return made != null && made.stackSize == 1 && made.getItem() == theirs.getItem() && made.getItemDamage() == theirs.getItemDamage();
    }

    private static void weaponsAndField() {
        OreRecipes.shapeless(weapon(WeaponType.ION_CUTTER),
                comp("polymerHandle"), comp("focusLens"), silicon(SiliconMaterial.CONTROLLER), comp("energyCellLV"));
        OreRecipes.shapeless(weapon(WeaponType.PULSE_EMITTER),
                comp("steelCasing"), comp("copperCoil"), comp("copperCoil"), silicon(SiliconMaterial.CONTROLLER), comp("energyCellMV"));
        // Nano drill: a steel motor housing, two copper coils for the motor, titanium plates for the
        // bit, the Nano suit's power core frame. Quantum / Exo are Upgrade Station recipes.
        OreRecipes.shapeless(new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.NANO)),
                comp("steelCasing"), comp("copperCoil"), comp("copperCoil"), comp("tiPlate"), comp("tiPlate"),
                comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER), comp("energyCellMV"));
        // Nano blade: a polymer hilt, a titanium guard, the focus lens and coil that shape the blade,
        // the Nano suit's power core frame. Quantum / Exo are Upgrade Station recipes, like the suit.
        OreRecipes.shapeless(new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.NANO)),
                comp("polymerHandle"), comp("tiPlate"), comp("tiPlate"), comp("focusLens"), comp("copperCoil"),
                comp("powerCoreFrame"), silicon(SiliconMaterial.CONTROLLER), comp("energyCellMV"));
        OreRecipes.shapeless(weapon(WeaponType.PLASMA_RIFLE),
                comp("tiCasing"), comp("wBarrel"), comp("wBarrel"), comp("quartzChamber"),
                silicon(SiliconMaterial.CONTROLLER), comp("energyCellHV"));

        // TODO(§7): the doc's "Ti Casing x6 + Quartz Emitter + Controller x2 + Tungsten Cable x2" is
        // eleven items, two more than a crafting grid holds (as a stack size it cost one casing).
        // Four casings keeps every other part and fits exactly.
        OreRecipes.shapeless(new ItemStack(ModBlocks.fieldGeneratorSC),
                comp("tiCasing"), comp("tiCasing"), comp("tiCasing"), comp("tiCasing"),
                comp("quartzEmitter"), silicon(SiliconMaterial.CONTROLLER), silicon(SiliconMaterial.CONTROLLER),
                cable(CableType.TUNGSTEN), cable(CableType.TUNGSTEN));

        // §18.3: Quartz Crystal = vanilla Nether Quartz (no separate item, see 01_recipes.md §18.3).
        OreRecipes.shapeless(new ItemStack(ModItems.fieldLinkModule),
                Items.quartz, cable(CableType.SUPERCONDUCTOR), silicon(SiliconMaterial.CONTROLLER));
    }

    private static ItemStack weapon(WeaponType type) {
        return new ItemStack(ModItems.WEAPONS.get(type));
    }

    // ---- guide book (step 10) ----

    /**
     * IC2-style machine upgrades (TODO: not in the design doc - recipes modelled on IC2's): every
     * one is built around a transistor, the chain's basic logic part.
     */
    private static void upgrades() {
        ItemStack transistor = silicon(SiliconMaterial.TRANSISTOR);
        ItemStack wire = cable(CableType.COPPER_INSULATED);
        // Wrenches: steel; + LV cell, coil and controller; + HV cell, tungsten cable and controllers.
        ItemStack wrench = new ItemStack(ModItems.WRENCHES.get(0));
        ItemStack wrenchElectric = new ItemStack(ModItems.WRENCHES.get(1));
        ItemStack wrenchQuantum = new ItemStack(ModItems.WRENCHES.get(2));
        OreRecipes.shaped(wrench, "S S", " S ", " S ", 'S', ingot(Material.STEEL));
        OreRecipes.shaped(wrenchElectric, " K ", "CWC", " E ",
                'K', comp("copperCoil"), 'C', cable(CableType.COPPER_INSULATED), 'W', wrench, 'E', comp("energyCellLV"));
        OreRecipes.shaped(wrenchQuantum, "PKP", "CWC", "PEP",
                'P', comp("wTiPlate"), 'K', silicon(SiliconMaterial.CONTROLLER), 'C', cable(CableType.TUNGSTEN),
                'W', wrenchElectric, 'E', comp("energyCellHV"));

        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER), "CCC", "WTW",
                'C', comp("capacitor"), 'W', wire, 'T', transistor);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER), "GGG", "WXW", "GTG",
                'G', Blocks.glass, 'W', wire, 'X', new ItemStack(ModBlocks.transformerSC, 1, 0), 'T', transistor);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE), "PPP", "WEW", "PTP",
                'P', "plankWood", 'W', wire, 'E', comp("energyCellLV"), 'T', transistor);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.EJECTOR), "IPI", "WTW",
                'I', ingot(Material.TIN), 'P', Blocks.piston, 'W', wire, 'T', transistor);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.PULLER), "IPI", "WTW",
                'I', ingot(Material.TIN), 'P', Blocks.sticky_piston, 'W', wire, 'T', transistor);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.HEAT_SINK), "AAA", "ACA", "ATA",
                'A', ingot(Material.ALUMINIUM), 'C', ingot(Material.COPPER), 'T', transistor);
        GameRegistry.addRecipe(new CarryRecipe(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION), CarryRecipe.EMPTY,
                com.sc.block.BlockTankSC.class, null, "IGI", "WXW", " T ",
                'I', ingot(Material.TIN), 'G', Blocks.glass, 'W', wire, 'X', new ItemStack(ModBlocks.tankSC, 1, 0), 'T', transistor));
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.QUALITY), " L ", "SXS", " T ",
                'L', comp("lens"), 'S', comp("sensor"), 'X', silicon(SiliconMaterial.CONTROLLER), 'T', transistor);
        // energy storage modules, both on a Transformer upgrade: the Output Splitter between two HV-EV
        // transformers and four tungsten (HV) cables; the Adaptive Transformer reads the line with a
        // comparator and a controller chip, gold contacts
        ItemStack transformerUpgrade = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OUTPUT_SPLITTER), "PKP", "TXT", "PKP",
                'P', cable(CableType.TUNGSTEN), 'K', Items.comparator, 'T', new ItemStack(ModBlocks.transformerSC, 1, 2),
                'X', transformerUpgrade);
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ADAPTIVE_TRANSFORMER), "GKG", "TXT", "GCG",
                'G', Items.gold_ingot, 'K', Items.comparator, 'T', transistor, 'X', transformerUpgrade,
                'C', silicon(SiliconMaterial.CONTROLLER));
    }

    /**
     * Pneumatic tube filters and speed upgrade (TODO: not in the design doc, modelled on Ender IO's):
     * the basic filter is paper round a hopper over a bare copper cable (the cable: paper round a hopper alone is
     * Ender IO's own basic filter - with both mods the grid gave theirs), the advanced one adds logic around it.
     */
    private static void tubeParts() {
        ItemStack basic = new ItemStack(ModItems.itemFilter, 1, 0);
        OreRecipes.shaped(basic, " P ", "PHP", " W ", 'P', Items.paper, 'H', Blocks.hopper, 'W', cable(CableType.COPPER_BARE));
        OreRecipes.shaped(new ItemStack(ModItems.itemFilter, 1, 1), " T ", "CFC", " T ",
                'T', silicon(SiliconMaterial.TRANSISTOR), 'C', Items.comparator, 'F', basic);
        OreRecipes.shaped(new ItemStack(ModItems.tubeSpeedUpgrade), " I ", "PTP", " I ",
                'I', Items.iron_ingot, 'P', Blocks.piston, 'T', silicon(SiliconMaterial.TRANSISTOR));
    }

    /**
     * Portable tanks (TODO: not in the design doc, Thermal Expansion's tiers): a metal frame round
     * glass; each tier is built round the one below, which keeps its fluid (TankUpgradeRecipeSC).
     */
    private static void tanks() {
        ItemStack steel = new ItemStack(ModBlocks.tankSC, 1, 0), titanium = new ItemStack(ModBlocks.tankSC, 1, 1),
                tungsten = new ItemStack(ModBlocks.tankSC, 1, 2), cryo = new ItemStack(ModBlocks.tankSC, 1, 3);
        OreRecipes.shaped(steel, "IGI", "G G", "IGI", 'I', ingot(Material.STEEL), 'G', Blocks.glass);
        GameRegistry.addRecipe(new TankUpgradeRecipeSC(titanium, "IGI", "GXG", "IGI",
                'I', ingot(Material.TITANIUM), 'G', Blocks.glass, 'X', steel));
        GameRegistry.addRecipe(new TankUpgradeRecipeSC(tungsten, "IGI", "GXG", "IGI",
                'I', ingot(Material.TUNGSTEN), 'G', Blocks.glass, 'X', titanium));
        GameRegistry.addRecipe(new TankUpgradeRecipeSC(cryo, "NGN", "GXG", "NGN",
                'N', comp("nb3SnPlate"), 'G', Blocks.glass, 'X', tungsten));
    }

    private static void manual() {
        OreRecipes.shapeless(new ItemStack(ModItems.manual),
                Items.book, comp("resistor"), cable(CableType.COPPER_BARE));
    }

    /**
     * The Ground / Space Bridge (docs/plan-ground-bridge.md, recipes approved «все ★»): the eleven parts,
     * the remotes, the coordinator (and its copy) and the Armour Link Module. The capacitor and the remotes
     * take the charge of the battery (and of the Bridge Remote) built into them - BridgeChargeRecipeSC.
     */
    private static void bridge() {
        ItemStack ctl = silicon(SiliconMaterial.CONTROLLER), mem = silicon(SiliconMaterial.MEMORY_CHIP), tp = comp("tiPlate"),
                hf = ingot(Material.HAFNIUM), exo = cable(CableType.EXO), sing = cable(CableType.SINGULAR), nb = comp("nb3SnPlate"),
                cap = comp("matterCapsule"), sen = comp("sensor"), eye = new ItemStack(Items.ender_eye), obs = new ItemStack(Blocks.obsidian);
        // 1. the controller
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.CONTROLLER, 1), "XPX", "EOE", "GKG",
                'X', ctl, 'P', mem, 'E', exo, 'O', eye, 'G', hf, 'K', comp("tiCasing"));
        // 2. the Singularity Capacitor round a QV cell (its charge comes along)
        GameRegistry.addRecipe(new BridgeChargeRecipeSC(part(com.sc.block.BlockBridgeSC.CAPACITOR, 1), "GCG", "CQC", "GCG",
                'G', hf, 'C', comp("tantalumCapacitor"), 'Q', new ItemStack(ModItems.battery, 1, 4)));
        // 3. the energy port
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.ENERGY_PORT, 1), "TET", "EXE", "TET", 'T', tp, 'E', exo, 'X', ctl);
        // 4. the gas port: titanium pipes round a steel tank
        GameRegistry.addRecipe(new CarryRecipe(part(com.sc.block.BlockBridgeSC.GAS_PORT, 1), CarryRecipe.EMPTY,
                com.sc.block.BlockTankSC.class, null, "TPT", "PBP", "TPT",
                'T', tp, 'P', pipe(PipeType.TITANIUM), 'B', new ItemStack(ModBlocks.tankSC, 1, 0)));
        // 5. focusers, two at a time
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.FOCUSER, 2), "NZN", "SMS", "NZN",
                'N', nb, 'Z', new ItemStack(Items.nether_star), 'S', sing, 'M', cap);
        // 6. the navigation computer
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.NAV, 1), "PCP", "DXD", "TTT",
                'P', mem, 'C', new ItemStack(Items.compass), 'D', sen, 'X', ctl, 'T', tp);
        // 7. the mass compensator
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.MASS, 1), "NGN", "GOG", "NGN",
                'N', nb, 'G', hf, 'O', new ItemStack(ModBlocks.gravityCoil));
        // 8. the ring cooler
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.COOLER, 1), "CLC", "LHL", "CLC",
                'C', comp("copperCoil"), 'L', comp("ptfeSheet"), 'H', comp("heLoopModule"));
        // 9. the portal shield
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.SHIELD, 1), "OFO", "DXD", "OOO",
                'O', obs, 'F', comp("focusLens"), 'D', sen, 'X', ctl);
        // 10. the receiver beacon
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.BEACON, 1), "LOL", "EXE", "TTT",
                'L', new ItemStack(Items.glowstone_dust), 'O', eye, 'E', exo, 'X', ctl, 'T', tp);
        // 11. the interdimensional anchor
        OreRecipes.shaped(part(com.sc.block.BlockBridgeSC.ANCHOR, 1), "BOB", "SMS", "BGB",
                'B', obs, 'O', eye, 'S', sing, 'M', cap, 'G', hf);
        // 12. the Bridge Remote round an EV cell (its charge comes along)
        ItemStack ground = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.GROUND);
        GameRegistry.addRecipe(new BridgeChargeRecipeSC(ground.copy(), "LIL", "TXT", "TVT",
                'L', comp("polymerPlate"), 'I', comp("quartzEmitter"), 'T', tp, 'X', ctl, 'V', new ItemStack(ModItems.battery, 1, 3)));
        // 13. the Space Remote round a Bridge Remote (its binding and charge) and an Exo core
        GameRegistry.addRecipe(new BridgeChargeRecipeSC(new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.SPACE),
                "SOS", "MRM", "SYS", 'S', sing, 'O', eye, 'M', cap, 'R', ground, 'Y', new ItemStack(ModItems.battery, 1, 5)));
        // 14. the coordinator, 15. its copy
        OreRecipes.shaped(new ItemStack(ModItems.coordinator), "TRT", "GCG", "TRT",
                'T', tp, 'R', new ItemStack(Items.redstone), 'G', new ItemStack(Blocks.glass_pane), 'C', new ItemStack(Items.compass));
        GameRegistry.addRecipe(new CoordinatorCopyRecipeSC());
        // 16. the Armour Link Module
        OreRecipes.shaped(new ItemStack(ModItems.bridgeLinkModule), "GOG", "PXP", "GSG",
                'G', hf, 'O', eye, 'P', mem, 'X', ctl, 'S', sing);
    }

    private static ItemStack part(int meta, int count) {
        return com.sc.block.BlockBridgeSC.stack(meta, count);
    }

    // ---- РЦ-2: crafts that keep what their block ingredients held ----

    private static boolean returnsRegistered;

    /**
     * A block built round blocks that keep their contents in item NBT: what fits goes into the result,
     * nothing is lost silently.
     *  - COPY: the same kind of block a tier up (the quarry) - the carried item's NBT goes over as is;
     *  - ENERGY: a block with a buffer of its own round a storage (wireless Tx / Rx, the translator) - the
     *    storage's EU (capped on placement); its upgrades have no place there and go back to the crafter
     *    (CarryReturns);
     *  - GENERATOR: a reactor round generators - their EU summed, their fuel into the new one's tank for
     *    that fluid (a fuel it can't burn has nowhere to go); the ignition starts over;
     *  - EMPTY: a part that keeps no fluid round a tank (a quarry module, an upgrade, the gas port) - only
     *    an empty tank fits: a fluid could not come back as an item.
     * Generic materials go by their ore names as in OreRecipes.shaped; the carried blocks stay exact stacks.
     */
    public static final class CarryRecipe extends net.minecraftforge.oredict.ShapedOreRecipe {

        public static final int COPY = 0, ENERGY = 1, GENERATOR = 2, EMPTY = 3;
        private static final String[] GEN_TANKS = {"FuelTank", "FuelTank2", "OutTank"};

        private final int mode;
        private final Class<? extends net.minecraft.block.Block> carry;
        /** GENERATOR: the reactor made. */
        private final GeneratorType into;

        public CarryRecipe(ItemStack result, int mode, Class<? extends net.minecraft.block.Block> carry, GeneratorType into,
                           Object... recipe) {
            super(result, convert(recipe, carry));
            this.mode = mode;
            this.carry = carry;
            this.into = into;
        }

        private static Object[] convert(Object[] in, Class<? extends net.minecraft.block.Block> carry) {
            Object[] out = new Object[in.length];
            for (int i = 0; i < in.length; i++) {
                out[i] = in[i] instanceof ItemStack && !isOf((ItemStack) in[i], carry) ? OreRecipes.oreName((ItemStack) in[i]) : in[i];
            }
            return out;
        }

        static boolean isOf(ItemStack s, Class<? extends net.minecraft.block.Block> carry) {
            return s != null && s.getItem() instanceof net.minecraft.item.ItemBlock
                    && carry.isInstance(net.minecraft.block.Block.getBlockFromItem(s.getItem()));
        }

        /** Whether the result takes over something of its ingredients (not EMPTY). */
        public boolean carries() {
            return mode != EMPTY;
        }

        @Override
        public boolean matches(net.minecraft.inventory.InventoryCrafting grid, net.minecraft.world.World world) {
            if (!super.matches(grid, world)) {
                return false;
            }
            if (mode == EMPTY) {
                for (int i = 0; i < grid.getSizeInventory(); i++) {
                    ItemStack s = grid.getStackInSlot(i);
                    if (isOf(s, carry) && com.sc.block.ItemBlockTankSC.contents(s) != null) {
                        return false;                   // a filled tank: empty it first, its fluid would be lost
                    }
                }
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting grid) {
            ItemStack out = super.getCraftingResult(grid);
            if (out == null || mode == EMPTY) {
                return out;
            }
            net.minecraft.nbt.NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new net.minecraft.nbt.NBTTagCompound();
            long eu = 0;
            net.minecraftforge.fluids.FluidStack f1 = null, f2 = null;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                ItemStack s = grid.getStackInSlot(i);
                if (!isOf(s, carry) || !s.hasTagCompound()) {
                    continue;
                }
                net.minecraft.nbt.NBTTagCompound t = s.getTagCompound();
                if (mode == COPY) {
                    for (Object k : t.func_150296_c()) {        // getKeySet
                        String key = (String) k;
                        if (!"display".equals(key) && !"RepairCost".equals(key) && !"ench".equals(key)) {
                            tag.setTag(key, t.getTag(key).copy());
                        }
                    }
                    break;
                }
                eu += Math.max(0, t.getInteger("EnergySC"));
                if (mode == GENERATOR) {
                    for (String key : GEN_TANKS) {
                        net.minecraftforge.fluids.FluidStack f = t.hasKey(key)
                                ? net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(t.getCompoundTag(key)) : null;
                        if (f == null || f.getFluid() == null || f.amount <= 0) {
                            continue;
                        }
                        if (fitsFuel(into, f.getFluid().getName())) {
                            f1 = add(f1, f);
                        } else if (fitsFuel2(into, f.getFluid().getName())) {
                            f2 = add(f2, f);
                        }
                    }
                }
            }
            if (eu > 0) {
                tag.setInteger("EnergySC", (int) Math.min(Integer.MAX_VALUE, eu));
            }
            if (f1 != null) {
                tag.setTag("FuelTank", f1.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            }
            if (f2 != null) {
                tag.setTag("FuelTank2", f2.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            }
            if (!tag.hasNoTags()) {
                out.setTagCompound(tag);
            }
            return out;
        }

        private static net.minecraftforge.fluids.FluidStack add(net.minecraftforge.fluids.FluidStack sum,
                                                                net.minecraftforge.fluids.FluidStack f) {
            if (sum == null) {
                return f.copy();
            }
            if (sum.isFluidEqual(f)) {
                sum.amount += f.amount;     // fuller than the tank holds: it takes nothing in until it burns down
            }
            return sum;
        }

        /** What goes into a generator's first tank (as TileEntityGeneratorSC.fitsTank1). */
        private static boolean fitsFuel(GeneratorType type, String fluid) {
            switch (type.kind) {
                case FLUID_FUEL: return type.euPerMb(fluid) > 0;
                case DUAL_FLUID:
                case EXO: return fluid.equals(type.fuelFluidName);
                default: return false;
            }
        }

        private static boolean fitsFuel2(GeneratorType type, String fluid) {
            return type.kind == GeneratorType.Kind.DUAL_FLUID && fluid.equals(type.fuel2FluidName);
        }

        /** ENERGY: the upgrades of the storages in `grid` - they have no place in the result. */
        java.util.List<ItemStack> leftovers(net.minecraft.inventory.IInventory grid) {
            java.util.List<ItemStack> out = new java.util.ArrayList<ItemStack>();
            if (mode != ENERGY) {
                return out;
            }
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                ItemStack s = grid.getStackInSlot(i);
                if (!isOf(s, carry) || !s.hasTagCompound() || !s.getTagCompound().hasKey("UpgradesSC")) {
                    continue;
                }
                net.minecraft.nbt.NBTTagList list = s.getTagCompound().getCompoundTag("UpgradesSC").getTagList("Items", 10);
                for (int k = 0; k < list.tagCount(); k++) {
                    ItemStack u = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(k));
                    if (u != null) {
                        out.add(u);
                    }
                }
            }
            return out;
        }
    }

    /**
     * Gives the crafter back what a CarryRecipe's result has no place for (a storage's upgrades). The
     * grid is still whole when the event fires (SlotCrafting fires it before using the ingredients up).
     */
    public static final class CarryReturns {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onCrafted(cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent e) {
            if (e.player == null || e.player.worldObj.isRemote || !(e.craftMatrix instanceof net.minecraft.inventory.InventoryCrafting)) {
                return;
            }
            net.minecraft.inventory.InventoryCrafting grid = (net.minecraft.inventory.InventoryCrafting) e.craftMatrix;
            for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
                if (o instanceof CarryRecipe && ((CarryRecipe) o).mode == CarryRecipe.ENERGY && ((CarryRecipe) o).matches(grid, e.player.worldObj)) {
                    for (ItemStack u : ((CarryRecipe) o).leftovers(grid)) {
                        if (!e.player.inventory.addItemStackToInventory(u) && u.stackSize > 0) {
                            e.player.dropPlayerItemWithRandomChoice(u, false);
                        }
                    }
                    return;
                }
            }
        }
    }

    private static java.util.Set<String> carryResults;

    /**
     * РЦ-7: whether `s` is made by a craft that takes over its ingredients' charge or contents (a carry
     * recipe of any kind) - for the "contents come along" line in its tooltip. Built on first use.
     */
    public static boolean carriesContents(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return false;
        }
        if (carryResults == null) {
            java.util.Set<String> set = new java.util.HashSet<String>();
            for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
                boolean carry = o instanceof CarryRecipe ? ((CarryRecipe) o).carries()
                        : o instanceof StorageUpgradeRecipeSC || o instanceof TankUpgradeRecipeSC || o instanceof ChargeCarryRecipeSC
                        || o instanceof BridgeChargeRecipeSC || o instanceof BatteryRecipeSC;
                ItemStack r = carry ? ((net.minecraft.item.crafting.IRecipe) o).getRecipeOutput() : null;
                if (r != null && r.getItem() != null) {
                    set.add(net.minecraft.item.Item.getIdFromItem(r.getItem()) + ":" + r.getItemDamage());
                }
            }
            carryResults = set;
        }
        return carryResults.contains(net.minecraft.item.Item.getIdFromItem(s.getItem()) + ":" + s.getItemDamage());
    }
}
