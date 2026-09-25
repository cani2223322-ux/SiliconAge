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
        cablesAndPipes();
        baseMaterials();
        passiveComponents();
        generators();
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

    private static ItemStack generator(GeneratorType type) {
        return new ItemStack(ModBlocks.generatorSC, 1, type.ordinal());
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
