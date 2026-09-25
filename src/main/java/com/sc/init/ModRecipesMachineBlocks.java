package com.sc.init;

import com.sc.energy.CableType;
import com.sc.machine.MachineType;
import com.sc.util.Material;
import com.sc.util.SCToolType;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Crafting recipes for the 29 machine blocks and the tools they run on. The design doc gives
 * recipes for generators (§5), armour (§6) and weapons (§7) but never for a single processing
 * machine, and none had been written - so no machine could be built in survival and every
 * machine recipe in the mod was unreachable. §13.5's tools (except the Seed Crystal, §17.1) had
 * no recipe either.
 *
 * TODO(§13): every recipe here is this project's own design. The one rule they follow is that
 * each tier is built only from what the tier below it can produce, so there's no loop:
 *
 *   LV  iron + bare copper cable       - reachable from vanilla + the furnace-smelted copper/tin/
 *                                        lead ores and a coal-fired Combustion Generator
 *   MV  steel + insulated copper cable - steel from the Blast Furnace, rubber from slime in the Kiln
 *   HV  titanium + tungsten cable      - both come out of the MV Centrifuge
 *   EV  superconductor + Controllers   - upgrades of HV machines, once the chain makes chips
 *
 * The Blast Furnace is an HV machine by power draw but is built from LV materials, because steel
 * is the MV tier's gate; on LV power it simply runs slowly. No HV machine needs a Controller,
 * since Controllers are what the HV silicon machines produce.
 *
 * Within a tier all machines share one frame and differ by a working part and a core, each a
 * vanilla item that says what the machine does (a piston to crush, a cauldron to hold a bath).
 */
public final class ModRecipesMachineBlocks {

    private ModRecipesMachineBlocks() {
    }

    public static void init() {
        registerLV();
        registerMV();
        registerHV();
        registerEV();
        registerTools();
        registerEnergyStorage();
        registerTransformers();
    }

    private static void registerLV() {
        ItemStack i = new ItemStack(Items.iron_ingot);
        ItemStack c = cable(CableType.COPPER_BARE);
        machine(MachineType.CRUSHER, i, c, new ItemStack(Items.flint), new ItemStack(Blocks.piston));
        machine(MachineType.ORE_WASHER, i, c, new ItemStack(Blocks.glass), new ItemStack(Items.cauldron));
        machine(MachineType.KILN, i, c, new ItemStack(Blocks.brick_block), new ItemStack(Blocks.furnace));
        machine(MachineType.ROLLING_MACHINE, i, c, new ItemStack(Blocks.piston), new ItemStack(Blocks.iron_block));
        machine(MachineType.DICING_SAW, i, c, new ItemStack(Items.flint), new ItemStack(Items.diamond));
        machine(MachineType.FLUID_CELL_FILLER, i, c, new ItemStack(Blocks.glass), new ItemStack(Items.bucket));
        machine(MachineType.BOILER_LV, i, c, new ItemStack(Items.bucket), new ItemStack(Blocks.furnace));

        // HV by draw, LV by materials - see the class javadoc.
        OreRecipes.shaped(block(MachineType.BLAST_FURNACE), new Object[]{
                "BBB", "BFB", "ICI",
                'B', Blocks.brick_block, 'F', Blocks.furnace, 'I', Items.iron_ingot, 'C', c});
    }

    private static void registerMV() {
        ItemStack s = ModItems.ingot.stackOf(Material.STEEL);
        ItemStack c = cable(CableType.COPPER_INSULATED);
        machine(MachineType.CENTRIFUGE, s, c, new ItemStack(Items.gold_ingot), new ItemStack(Blocks.piston));
        machine(MachineType.CHEM_REACTOR, s, c, new ItemStack(Blocks.glass), new ItemStack(Items.cauldron));
        machine(MachineType.CHLOR_ALKALI_ELECTROLYZER, s, c, new ItemStack(Items.gold_ingot), new ItemStack(Items.cauldron));
        machine(MachineType.CVD_CHAMBER, s, c, new ItemStack(Blocks.glass), new ItemStack(Blocks.furnace));
        machine(MachineType.ETCHING_BATH, s, c, new ItemStack(ModItems.rubber), new ItemStack(Items.cauldron));
        machine(MachineType.OXIDATION_FURNACE, s, c, new ItemStack(Blocks.iron_bars), new ItemStack(Blocks.furnace));
        machine(MachineType.PACKAGER, s, c, new ItemStack(Items.iron_ingot), new ItemStack(Blocks.sticky_piston));
        machine(MachineType.PHOTORESIST_COATER, s, c, new ItemStack(Blocks.glass), new ItemStack(Blocks.dispenser));
        machine(MachineType.SPUTTERER, s, c, new ItemStack(Items.gold_ingot), new ItemStack(Blocks.dispenser));
        machine(MachineType.UPGRADE_STATION_MV, s, c, new ItemStack(Items.redstone), new ItemStack(Blocks.crafting_table));
        machine(MachineType.WIRE_SAW, s, c, new ItemStack(Items.string), new ItemStack(Items.diamond));
        machine(MachineType.BOILER_MV, s, c, new ItemStack(Items.bucket), new ItemStack(Blocks.furnace));
    }

    private static void registerHV() {
        ItemStack t = ModItems.ingot.stackOf(Material.TITANIUM);
        ItemStack c = cable(CableType.TUNGSTEN);
        machine(MachineType.AIR_SEPARATOR, t, c, new ItemStack(Blocks.glass), new ItemStack(Blocks.piston));
        machine(MachineType.CZOCHRALSKI_PULLER, t, c, new ItemStack(Items.quartz), new ItemStack(Blocks.furnace));
        machine(MachineType.ION_IMPLANTER, t, c, new ItemStack(Items.gold_ingot), new ItemStack(Blocks.dispenser));
        machine(MachineType.REFINERY, t, c, new ItemStack(Items.bucket), new ItemStack(Items.cauldron));
        machine(MachineType.STEPPER, t, c, new ItemStack(Blocks.glass), new ItemStack(ModItems.component("focusLens")));
        machine(MachineType.UPGRADE_STATION_HV, t, c, new ItemStack(Items.gold_ingot), new ItemStack(Blocks.crafting_table));
    }

    /** §13.8's EV upgrades: the HV machine itself, wrapped in superconductor and fresh Controllers. */
    private static void registerEV() {
        upgrade(MachineType.CZOCHRALSKI_PULLER_EV, MachineType.CZOCHRALSKI_PULLER);
        upgrade(MachineType.STEPPER_EV, MachineType.STEPPER);
        upgrade(MachineType.UPGRADE_STATION_EV, MachineType.UPGRADE_STATION_HV);
    }

    /**
     * Energy storages (TODO: not in the design doc): cells of the tier in a frame of that tier's
     * metal and cable, each tier built around the one below it.
     */
    /**
     * Transformers (TODO: not in the design doc, modelled on IC2's): the low tier's cable on
     * top, the high tier's below, coils around a core that grows with the tier.
     */
    private static void registerTransformers() {
        ItemStack lvMv = new ItemStack(ModBlocks.transformerSC, 1, 0);
        ItemStack mvHv = new ItemStack(ModBlocks.transformerSC, 1, 1);
        ItemStack hvEv = new ItemStack(ModBlocks.transformerSC, 1, 2);
        OreRecipes.shaped(lvMv, "PCP", "PKP", "PSP",
                'P', "plankWood", 'C', cable(CableType.COPPER_INSULATED), 'K', new ItemStack(ModItems.component("copperCoil")),
                'S', cable(CableType.SILVER));
        OreRecipes.shaped(mvHv, " C ", "KXK", " S ",
                'C', cable(CableType.SILVER), 'K', new ItemStack(ModItems.component("copperCoil")),
                'X', new ItemStack(ModItems.component("steelCasing")), 'S', cable(CableType.TUNGSTEN));
        OreRecipes.shaped(hvEv, " C ", "EXE", " S ",
                'C', cable(CableType.TUNGSTEN), 'E', new ItemStack(ModItems.component("energyCellHV")),
                'X', mvHv, 'S', cable(CableType.SUPERCONDUCTOR));
        // above EV: each built round the one below, the new tier's cable underneath
        ItemStack evIv = new ItemStack(ModBlocks.transformerSC, 1, 3);
        ItemStack ivQv = new ItemStack(ModBlocks.transformerSC, 1, 4);
        ItemStack qvXv = new ItemStack(ModBlocks.transformerSC, 1, 5);
        OreRecipes.shaped(evIv, " C ", "EXE", " S ",
                'C', cable(CableType.SUPERCONDUCTOR), 'E', new ItemStack(ModItems.component("nb3SnCoil")),
                'X', hvEv, 'S', cable(CableType.NIOBIUM_TITANIUM));
        OreRecipes.shaped(ivQv, " C ", "EXE", " S ",
                'C', cable(CableType.NIOBIUM_TITANIUM), 'E', new ItemStack(ModItems.component("nb3SnCoil")),
                'X', evIv, 'S', cable(CableType.QUANTUM));
        OreRecipes.shaped(qvXv, " C ", "EXE", " S ",
                'C', cable(CableType.QUANTUM), 'E', ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER),
                'X', ivQv, 'S', cable(CableType.EXO));
    }

    private static void registerEnergyStorage() {
        ItemStack lv = new ItemStack(ModBlocks.energyStorageSC, 1, 0);
        ItemStack mv = new ItemStack(ModBlocks.energyStorageSC, 1, 1);
        ItemStack hv = new ItemStack(ModBlocks.energyStorageSC, 1, 2);
        ItemStack ev = new ItemStack(ModBlocks.energyStorageSC, 1, 3);
        OreRecipes.shaped(lv, "ICI", "EEE", "ICI",
                'I', new ItemStack(Items.iron_ingot), 'C', cable(CableType.COPPER_BARE), 'E', new ItemStack(ModItems.component("energyCellLV")));
        // Each tier is built round the one below, whose charge comes along (StorageUpgradeRecipeSC).
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(mv, "SCS", "EXE", "SCS",
                'S', ModItems.ingot.stackOf(Material.STEEL), 'C', cable(CableType.COPPER_INSULATED),
                'E', new ItemStack(ModItems.component("energyCellMV")), 'X', lv));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(hv, "TCT", "EXE", "TCT",
                'T', ModItems.ingot.stackOf(Material.TITANIUM), 'C', cable(CableType.TUNGSTEN),
                'E', new ItemStack(ModItems.component("energyCellHV")), 'X', mv));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(ev, "KCK", "EXE", "KCK",
                'K', new ItemStack(ModItems.component("tiCasing")), 'C', cable(CableType.SUPERCONDUCTOR),
                'E', new ItemStack(ModItems.component("energyCellHV")), 'X', hv));
        // IV, QV (Quantum), XV (Exo)
        ItemStack iv = new ItemStack(ModBlocks.energyStorageSC, 1, 4);
        ItemStack qv = new ItemStack(ModBlocks.energyStorageSC, 1, 5);
        ItemStack xv = new ItemStack(ModBlocks.energyStorageSC, 1, 6);
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(iv, "KCK", "EXE", "KCK",
                'K', new ItemStack(ModItems.component("wTiPlate")), 'C', cable(CableType.NIOBIUM_TITANIUM),
                'E', new ItemStack(ModItems.component("nb3SnCoil")), 'X', ev));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(qv, "KCK", "EXE", "KCK",
                'K', ModItems.ingot.stackOf(Material.PLATINUM), 'C', cable(CableType.QUANTUM),
                'E', new ItemStack(ModItems.component("heLoopModule")), 'X', iv));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(xv, "KCK", "EXE", "KCK",
                'K', ModItems.ingot.stackOf(Material.HAFNIUM), 'C', cable(CableType.EXO),
                'E', ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER), 'X', qv));

        // Charge pads (IC2 style): the storage of that tier under a row of iron pressure plates, wired
        // with that tier's cable. The storage's charge stays in the pad.
        CableType[] padCables = {CableType.COPPER_BARE, CableType.COPPER_INSULATED, CableType.TUNGSTEN, CableType.SUPERCONDUCTOR,
                CableType.NIOBIUM_TITANIUM, CableType.QUANTUM, CableType.EXO};
        ItemStack[] storages = {lv, mv, hv, ev, iv, qv, xv};
        for (int t = 0; t < storages.length; t++) {
            GameRegistry.addRecipe(new StorageUpgradeRecipeSC(new ItemStack(ModBlocks.chargePadSC, 1, t), "PPP", "CXC",
                    'P', new ItemStack(Blocks.heavy_weighted_pressure_plate), 'C', cable(padCables[t]), 'X', storages[t].copy()));
        }
    }

    private static void registerTools() {
        ItemStack steel = ModItems.ingot.stackOf(Material.STEEL);

        // §17.1, verbatim: the Seed Crystal's own chicken-and-egg start - "EG-Si x1 + Diamond".
        OreRecipes.shapeless(tool(SCToolType.SEED_CRYSTAL),
                ModItems.siliconMaterial.stackOf(SiliconMaterial.ELECTRONIC_GRADE_SI), Items.diamond);
        // TODO(§13.5): no recipe given. Steel wire carrying diamond grit.
        OreRecipes.shapeless(tool(SCToolType.DIAMOND_WIRE), steel, steel, Items.diamond, Items.diamond, Items.string);
        // TODO(§13.5): a steel disc with a diamond rim.
        OreRecipes.shaped(tool(SCToolType.DIAMOND_BLADE), new Object[]{
                " D ", "DSD", " D ", 'D', Items.diamond, 'S', steel});
        // TODO(§13.5): real masks are a chrome pattern on fused quartz; gold stands in for chrome.
        // Deliberately needs no Controller - the Stepper uses it BEFORE the chain makes any.
        OreRecipes.shapeless(tool(SCToolType.PHOTOMASK), Blocks.glass_pane, Items.quartz, Items.quartz, Items.gold_ingot);

        // Press molds: a steel die around a vanilla item showing its shape.
        mold(SCToolType.MOLD_PLATE, new ItemStack(Blocks.heavy_weighted_pressure_plate), steel);
        mold(SCToolType.MOLD_BLADE, new ItemStack(Items.iron_sword), steel);
        mold(SCToolType.MOLD_COIL, new ItemStack(Items.string), steel);
        mold(SCToolType.MOLD_TARGET, new ItemStack(Items.gold_ingot), steel);
        mold(SCToolType.MOLD_LEAD_FRAME_3, new ItemStack(Blocks.redstone_torch), steel);
        mold(SCToolType.MOLD_LEAD_FRAME_16, new ItemStack(Items.repeater), steel);
        mold(SCToolType.MOLD_LEAD_FRAME_40, new ItemStack(Items.comparator), steel);
    }

    // ---- helpers ----

    /** One tier frame: `frame` at the corners, the tier's cable top and bottom, `part` either side of `core`. */
    private static void machine(MachineType type, ItemStack frame, ItemStack cable, ItemStack part, ItemStack core) {
        OreRecipes.shaped(block(type), new Object[]{
                "ACA", "PXP", "ACA",
                'A', frame, 'C', cable, 'P', part, 'X', core});
    }

    private static void upgrade(MachineType ev, MachineType hv) {
        OreRecipes.shaped(block(ev), new Object[]{
                "SCS", "CMC", "SCS",
                'S', cable(CableType.SUPERCONDUCTOR),
                'C', ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER),
                'M', block(hv)});
    }

    private static void mold(SCToolType type, ItemStack shape, ItemStack steel) {
        OreRecipes.shaped(tool(type), new Object[]{" S ", "SXS", " S ", 'S', steel, 'X', shape});
    }

    static ItemStack block(MachineType type) {
        int ord = type.ordinal();
        return new ItemStack(ord < 16 ? ModBlocks.machineSC : ModBlocks.machineSC2, 1, ord % 16);
    }

    private static ItemStack cable(CableType type) {
        return new ItemStack(ModBlocks.cableSC, 1, type.ordinal());
    }

    private static ItemStack tool(SCToolType type) {
        return new ItemStack(ModItems.TOOLS.get(type));
    }
}
