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
        cablesAndPipes();
        baseMaterials();
        passiveComponents();
        generators();
        newGenerators();
        quarry();
        armorAndChips();
        weaponsAndField();
        upgrades();
        tubeParts();
        tanks();
        manual();
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
        OreRecipes.shapeless(cable(CableType.COPPER_BARE, 4), ingot(Material.COPPER));
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
        OreRecipes.shapeless(comp("sputterBacking"), ingot(Material.STEEL));
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
        OreRecipes.shaped(generator(GeneratorType.PLASMA_REACTOR), "NGN", "CXC", "NGN",
                'N', comp("nb3SnCoil"), 'G', generator(GeneratorType.PLASMA_GENERATOR), 'C', cable(CableType.NIOBIUM_TITANIUM), 'X', controller);
        // QV: the fusion reactor made a tokamak; its coils (8 around it)
        OreRecipes.shaped(generator(GeneratorType.TOKAMAK), "NCN", "XFX", "NCN",
                'N', comp("nb3SnCoil"), 'C', cable(CableType.QUANTUM), 'X', controller, 'F', generator(GeneratorType.FUSION_REACTOR));
        OreRecipes.shaped(new ItemStack(ModBlocks.tokamakCoil), "NTN", "CHC", "NTN",
                'N', comp("nb3SnCoil"), 'T', comp("tiCasing"), 'C', cable(CableType.NIOBIUM_TITANIUM), 'H', new ItemStack(ModItems.liquidHeCell));
        // XV: a tokamak with a second fusion core, hafnium and Exo cable
        OreRecipes.shaped(generator(GeneratorType.EXO_REACTOR), "HCH", "XTX", "HFH",
                'H', ingot(Material.HAFNIUM), 'C', cable(CableType.EXO), 'X', controller, 'T', generator(GeneratorType.TOKAMAK),
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
        ItemStack[] heads = new ItemStack[ModItems.DRILL_HEADS.size()];
        for (int i = 0; i < heads.length; i++) {
            heads[i] = new ItemStack(ModItems.DRILL_HEADS.get(i));
        }
        OreRecipes.shaped(heads[0], "S S", "SXS", " S ", 'S', ingot(Material.STEEL), 'X', comp("steelCasing"));
        OreRecipes.shaped(heads[1], "W W", "WXW", " W ", 'W', ingot(Material.TUNGSTEN), 'X', heads[0]);
        OreRecipes.shaped(heads[2], "D D", "DXD", " D ",
                'D', new ItemStack(ModItems.TOOLS.get(com.sc.util.SCToolType.DIAMOND_BLADE)), 'X', heads[1]);
        OreRecipes.shaped(heads[3], "HEH", "CXC", "HEH",
                'H', ingot(Material.HAFNIUM), 'E', comp("energyCellHV"), 'C', controller, 'X', heads[2]);
        ItemStack lv = new ItemStack(ModBlocks.quarrySC, 1, 0), mv = new ItemStack(ModBlocks.quarrySC, 1, 1),
                hv = new ItemStack(ModBlocks.quarrySC, 1, 2), ev = new ItemStack(ModBlocks.quarrySC, 1, 3);
        OreRecipes.shaped(lv, "STS", "KXK", "CHC",
                'S', ingot(Material.STEEL), 'T', transistor, 'K', comp("copperCoil"), 'X', comp("steelCasing"), 'C', wire, 'H', heads[0]);
        OreRecipes.shaped(mv, "PCP", "KXK", "PCP",
                'P', comp("tiPlate"), 'C', cable(CableType.SILVER), 'K', comp("copperCoil"), 'X', lv);
        OreRecipes.shaped(hv, "PCP", "KXK", "PCP",
                'P', comp("wTiPlate"), 'C', cable(CableType.TUNGSTEN), 'K', controller, 'X', mv);
        OreRecipes.shaped(ev, "PCP", "KXK", "PCP",
                'P', comp("tiCasing"), 'C', cable(CableType.SUPERCONDUCTOR), 'K', comp("nb3SnCoil"), 'X', hv);

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
                {com.sc.item.ItemQuarryModuleSC.Kind.DOUBLE, comp("nb3SnCoil"), heads[1]},
                {com.sc.item.ItemQuarryModuleSC.Kind.FLUID_GUARD, comp("ptfeSheet"), comp("steelCasing")},
                {com.sc.item.ItemQuarryModuleSC.Kind.GENTLE, comp("sensor"), comp("lens")},
                {com.sc.item.ItemQuarryModuleSC.Kind.REPAIR, controller, ingot(Material.TUNGSTEN)},
                {com.sc.item.ItemQuarryModuleSC.Kind.ECONOMY, comp("capacitor"), comp("energyCellMV")},
                {com.sc.item.ItemQuarryModuleSC.Kind.RESONATOR, comp("lens"), comp("heLoopModule")},
                {com.sc.item.ItemQuarryModuleSC.Kind.STABILIZER, controller, comp("nb3SnCoil")},
                {com.sc.item.ItemQuarryModuleSC.Kind.DEEP_SCAN, comp("hfo2Die"), new ItemStack(ModItems.oreScanner)},
        };
        for (Object[] r : modules) {
            OreRecipes.shaped(m.stackOf((com.sc.item.ItemQuarryModuleSC.Kind) r[0]), " A ", "WMW", " T ",
                    'A', r[1], 'W', wire, 'M', r[2], 'T', transistor);
        }
        // the Exo Drilling Rig: an EV quarry round a fusion core, Exo cable and an Exo drill head
        OreRecipes.shaped(new ItemStack(ModBlocks.quarrySC, 1, 4), "HFH", "XQX", "CEC",
                'H', ingot(Material.HAFNIUM), 'F', comp("fusionCore"), 'X', controller, 'Q', ev,
                'C', cable(CableType.EXO), 'E', heads[3]);
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
            default: return comp("resistor");
        }
    }

    // ---- §7 ----

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
        OreRecipes.shaped(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.QUALITY), " L ", "SXS", " T ",
                'L', comp("lens"), 'S', comp("sensor"), 'X', silicon(SiliconMaterial.CONTROLLER), 'T', transistor);
    }

    /**
     * Pneumatic tube filters and speed upgrade (TODO: not in the design doc, modelled on Ender IO's):
     * the basic filter is paper round a hopper, the advanced one adds logic around it.
     */
    private static void tubeParts() {
        ItemStack basic = new ItemStack(ModItems.itemFilter, 1, 0);
        OreRecipes.shaped(basic, " P ", "PHP", " P ", 'P', Items.paper, 'H', Blocks.hopper);
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
}
