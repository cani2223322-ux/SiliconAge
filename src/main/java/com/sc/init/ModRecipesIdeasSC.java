package com.sc.init;

import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.SCToolType;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * The proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md): every new recipe of its items, one method an item - each implementer fills
 * only his own (the coordinator's split, so no two of them edit one method). SCMod.registerRecipes calls init() after the other recipe
 * classes. Only vanilla items and this mod's own.
 */
public final class ModRecipesIdeasSC {

    private ModRecipesIdeasSC() {
    }

    public static void init() {
        pd6();
        pd7();
        om2();
        om3();
        om4();
        om5();
        om7();
    }

    /**
     * ПД-6: the five Breach Protocols - shapeless, a Breakdown Matrix each (the Warden's own trophy finds a use) and the Substrate's loot
     * (TODO(balance)): «Глухие катушки» + 2 Shield Cores + 2 Nb3Sn coils; «Короткий фитиль» + 2 TNT + 2 Plasma Condensate; «Нашествие»
     * + 4 Shell Firmware; «Тень Затмения» + 2 Singular Armour Scrap + an Eye of Ender; «Помехи» + 4 Induction Thread.
     */
    private static void pd6() {
        ItemStack matrix = com.sc.ruin.RelicItemsSC.relic(com.sc.ruin.ItemRelicSC.BREAKDOWN_MATRIX, 1);
        net.minecraft.item.Item coil = ModItems.component("nb3SnCoil");
        if (matrix == null || IdeaItemsSC.breachProtocol == null || coil == null) {
            return;
        }
        ItemStack core = loot(com.sc.mob.ItemCombatLootSC.LootKind.SHIELD_CORE), plasma = loot(com.sc.mob.ItemCombatLootSC.LootKind.PLASMA_CONDENSATE);
        ItemStack firmware = loot(com.sc.mob.ItemCombatLootSC.LootKind.SHELL_FIRMWARE), scrap = loot(com.sc.mob.ItemCombatLootSC.LootKind.SINGULAR_SCRAP);
        ItemStack thread = loot(com.sc.mob.ItemCombatLootSC.LootKind.INDUCTION_THREAD);
        if (core == null || plasma == null || firmware == null || scrap == null || thread == null) {
            return;
        }
        shapeless(IdeaItemsSC.breachProtocol(com.sc.arena.BreachProtocolsSC.DEAF_COILS), matrix, core, core, new ItemStack(coil), new ItemStack(coil));
        shapeless(IdeaItemsSC.breachProtocol(com.sc.arena.BreachProtocolsSC.SHORT_FUSE), matrix, new ItemStack(Blocks.tnt), new ItemStack(Blocks.tnt),
                plasma, plasma);
        shapeless(IdeaItemsSC.breachProtocol(com.sc.arena.BreachProtocolsSC.SWARM), matrix, firmware, firmware, firmware, firmware);
        shapeless(IdeaItemsSC.breachProtocol(com.sc.arena.BreachProtocolsSC.ECLIPSE_SHADOW), matrix, scrap, scrap, new ItemStack(Items.ender_eye));
        shapeless(IdeaItemsSC.breachProtocol(com.sc.arena.BreachProtocolsSC.JAMMING), matrix, thread, thread, thread, thread);
    }

    /** One of the Substrate's loot (MobItemsSC), null before init. */
    private static ItemStack loot(com.sc.mob.ItemCombatLootSC.LootKind k) {
        return com.sc.mob.MobItemsSC.loot(k, 1);
    }

    /** A shapeless recipe of exact stacks (one of each listed). */
    private static void shapeless(ItemStack out, ItemStack... in) {
        if (out == null) {
            return;
        }
        Object[] o = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            ItemStack s = in[i].copy();
            s.stackSize = 1;
            o[i] = s;
        }
        GameRegistry.addShapelessRecipe(out, o);
    }

    /** ПД-7: the Relay is bought (TradeTableSC), not crafted - nothing here unless ПД-7 decides otherwise. */
    private static void pd7() {
    }

    /** ОМ-2: the singular scrap's crushing, the thread's rolling, the condensate's centrifuge, the paste - and their ticks. TODO(balance) */
    public static final int OM2_TICKS = 200;
    /** ОМ-2: the tantalum dust beside the hafnium out of the singular scrap (a «pinch»). TODO(balance) */
    public static final float OM2_TANTALUM_CHANCE = 0.25F;
    /** ОМ-2: the argon out of one Plasma Condensate (mB). */
    public static final int OM2_ARGON = 500;
    /** ОМ-3 (TODO(balance)): the Chemical Reactor 4 Zirconium Dust + 500 mB chlorine -> 500 mB chlorides, 300 ticks; the Refinery 4000 mB
     * chlorides -> 8 Zirconium Dust, a small pile of hafnium, 2000 mB chlorine, 1200 ticks. */
    public static final int OM3_ZR_IN = 4, OM3_CL_IN = 500, OM3_CHLORIDES = 500, OM3_REACTOR_TICKS = 300, OM3_SPLIT = 4000, OM3_ZR_OUT = 8,
            OM3_CL_OUT = 2000, OM3_REFINERY_TICKS = 1200;

    /**
     * ОМ-2: the Substrate's loot in the home machines (TODO(balance)) - the Crusher: Singular Armour Scrap -> Hafnium Dust (+ Tantalum
     * Dust OM2_TANTALUM_CHANCE); the Rolling Machine with the coil mould: 4 Induction Thread -> an Nb3Sn coil; shapeless: a Boiled Loop + a
     * liquid helium cell + a superconductor cable -> a Helium Loop Module (no controller; the usual recipe stays); the Centrifuge: a Plasma
     * Condensate -> 500 mB of argon; shapeless: 2 Natural Wafers + electronic-grade silicon -> a Seed Crystal (no diamond); the Chemical
     * Reactor: 4 Nanite Dust + a slimeball -> a Nanite Paste; and the paste's repair (PasteRepairRecipeSC).
     */
    private static void om2() {
        ItemStack scrap = loot(com.sc.mob.ItemCombatLootSC.LootKind.SINGULAR_SCRAP), thread = loot(com.sc.mob.ItemCombatLootSC.LootKind.INDUCTION_THREAD);
        ItemStack loop = loot(com.sc.mob.ItemCombatLootSC.LootKind.BOILED_LOOP), plasma = loot(com.sc.mob.ItemCombatLootSC.LootKind.PLASMA_CONDENSATE);
        ItemStack wafer = loot(com.sc.mob.ItemCombatLootSC.LootKind.NATURAL_WAFER), nanite = loot(com.sc.mob.ItemCombatLootSC.LootKind.NANITE_DUST);
        if (scrap != null && ModItems.dust != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER, new ItemStack[]{scrap}, null, null,
                    new ItemStack[]{ModItems.dust.stackOf(com.sc.util.Material.HAFNIUM)}, null, null, OM2_TICKS, 0f,
                    new ItemStack[]{ModItems.dust.stackOf(com.sc.util.Material.TANTALUM)}, new float[]{OM2_TANTALUM_CHANCE}));
        }
        net.minecraft.item.Item coil = ModItems.component("nb3SnCoil");
        if (thread != null && coil != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.ROLLING_MACHINE, new ItemStack[]{n(thread, 4), new ItemStack(ModItems.TOOLS.get(SCToolType.MOLD_COIL))},
                    null, null, new ItemStack[]{new ItemStack(coil)}, null, null, OM2_TICKS, 0f));
        }
        net.minecraft.item.Item heLoop = ModItems.component("heLoopModule");
        if (loop != null && heLoop != null && ModItems.liquidHeCell != null && ModBlocks.cableSC != null) {
            shapeless(new ItemStack(heLoop), loop, new ItemStack(ModItems.liquidHeCell),
                    new ItemStack(ModBlocks.cableSC, 1, com.sc.energy.CableType.SUPERCONDUCTOR.ordinal()));
        }
        if (plasma != null && ModFluids.argon != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.CENTRIFUGE, new ItemStack[]{plasma}, null, null, new ItemStack[0],
                    new FluidStack(ModFluids.argon, OM2_ARGON), null, OM2_TICKS, 0f));
        }
        if (wafer != null && ModItems.siliconMaterial != null && ModItems.TOOLS.get(SCToolType.SEED_CRYSTAL) != null) {
            shapeless(new ItemStack(ModItems.TOOLS.get(SCToolType.SEED_CRYSTAL)), wafer, wafer,
                    ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.ELECTRONIC_GRADE_SI));
        }
        if (nanite != null && IdeaItemsSC.nanitePaste != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR, new ItemStack[]{n(nanite, 4), new ItemStack(Items.slime_ball)}, null, null,
                    new ItemStack[]{IdeaItemsSC.nanitePaste(1)}, null, null, OM2_TICKS, 0f));
        }
        net.minecraftforge.oredict.RecipeSorter.register("siliconage:pasteRepair", PasteRepairRecipeSC.class,
                net.minecraftforge.oredict.RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new PasteRepairRecipeSC());
    }

    /**
     * ОМ-3: hafnium out of spare zirconium (TODO(balance)) - the Chemical Reactor (MV): 4 Zirconium Dust + 500 mB chlorine -> 500 mB of the
     * chlorides (zrcl4, corrosive - PTFE pipes); the Refinery (HV): 4000 mB of chlorides -> 8 Zirconium Dust, a small pile of Hafnium Dust,
     * 2000 mB chlorine. Net: 24 Zirconium Dust and 2000 mB chlorine a small pile - slower than the Metallurgist's.
     */
    private static void om3() {
        if (ModFluids.zrcl4 == null || ModFluids.chlorine == null || ModItems.dust == null || ModItems.dustTiny == null) {
            return;
        }
        RecipeRegistry.register(new MachineRecipe(MachineType.CHEM_REACTOR, new ItemStack[]{n(ModItems.dust.stackOf(com.sc.util.Material.ZIRCONIUM), OM3_ZR_IN)},
                new FluidStack(ModFluids.chlorine, OM3_CL_IN), null, new ItemStack[0], new FluidStack(ModFluids.zrcl4, OM3_CHLORIDES), null,
                OM3_REACTOR_TICKS, 0f));
        RecipeRegistry.register(new MachineRecipe(MachineType.REFINERY, new ItemStack[0], new FluidStack(ModFluids.zrcl4, OM3_SPLIT), null,
                new ItemStack[]{n(ModItems.dust.stackOf(com.sc.util.Material.ZIRCONIUM), OM3_ZR_OUT), ModItems.dustTiny.stackOf(com.sc.util.Material.HAFNIUM)},
                new FluidStack(ModFluids.chlorine, OM3_CL_OUT), null, OM3_REFINERY_TICKS, 0f));
    }

    /** ОМ-3 (pure): the net zirconium dust spent for one small pile of hafnium (the reactor's in, the refinery's back). */
    public static int om3ZirconiumPerPile() {
        return OM3_SPLIT / OM3_CHLORIDES * OM3_ZR_IN - OM3_ZR_OUT;
    }

    /** ОМ-3 (pure): the net chlorine spent for one small pile (mB). */
    public static int om3ChlorinePerPile() {
        return OM3_SPLIT / OM3_CHLORIDES * OM3_CL_IN - OM3_CL_OUT;
    }

    /** `s` x n (a copy). */
    private static ItemStack n(ItemStack s, int count) {
        ItemStack c = s.copy();
        c.stackSize = count;
        return c;
    }

    /** ОМ-4 (TODO(balance)): the processor wafer's run, its defects; the matrix's two runs; the krypton and the singular matter. */
    public static final int OM4_WAFER_TICKS = 600, OM4_PROCESSOR_TICKS = 300, OM4_MATRIX_SUBSTRATE_TICKS = 1200, OM4_MATRIX_HOME_TICKS = 2400,
            OM4_STATION_TICKS = 1200, OM4_KRYPTON = 250, OM4_MATTER_SUBSTRATE = 100, OM4_MATTER_HOME = 500, OM4_HELIUM = 1000;
    public static final float OM4_WAFER_DEFECTS = 0.15F;

    /**
     * ОМ-4: the EUV Lithograph's ladder (TODO(balance)) - the EUV mask (Upgrade Station EV: a photomask, 4 tungsten ingots, 2 quartz chambers;
     * the plan's controller dropped - the station has 3 input slots); the processor wafer (the Lithograph: a metallized wafer, the mask - worn -, 250 mB krypton, 15% defects); the
     * Processor (the Packager: the wafer, a 40-pin lead frame, a ceramic package); the Exo Processor (Upgrade Station EV: 4 Processors, 2
     * hafnium ingots, a HfO2 die + 1000 mB of liquid helium in the tank - the plan's helium cell: 3 input slots); the Singular Matrix (the Lithograph: 4 Exo Processors + 2 Natural Wafers + 100 mB
     * of singular matter, or at home 4 GaAs wafers + 500 mB, twice as long). Then the cheaper twins (cheaper()).
     */
    private static void om4() {
        if (ModItems.siliconMaterial == null || ModItems.TOOLS.get(SCToolType.EUV_MASK) == null || ModItems.TOOLS.get(SCToolType.PHOTOMASK) == null) {
            return;
        }
        ItemStack mask = new ItemStack(ModItems.TOOLS.get(SCToolType.EUV_MASK)), wafer = si(com.sc.util.SiliconMaterial.PROCESSOR_WAFER, 1);
        ItemStack processor = si(com.sc.util.SiliconMaterial.PROCESSOR, 1), exo = si(com.sc.util.SiliconMaterial.EXO_PROCESSOR, 1);
        ItemStack matrix = si(com.sc.util.SiliconMaterial.SINGULAR_MATRIX, 1);
        net.minecraft.item.Item chamber = ModItems.component("quartzChamber"), ceramic = ModItems.component("ceramicPackage"), die = ModItems.component("hfo2Die");
        if (chamber != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.UPGRADE_STATION_EV, new ItemStack[]{new ItemStack(ModItems.TOOLS.get(SCToolType.PHOTOMASK)),
                    n(ModItems.ingot.stackOf(com.sc.util.Material.TUNGSTEN), 4), new ItemStack(chamber, 2)},      // 3 slots (TileEntityMachineSC.INPUT_SLOTS)
                    null, null, new ItemStack[]{mask}, null, null, OM4_STATION_TICKS, 0f));
        }
        if (ModFluids.krypton != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.EUV_LITHOGRAPH, new ItemStack[]{si(com.sc.util.SiliconMaterial.METALLIZED_WAFER, 1), mask.copy()},
                    new FluidStack(ModFluids.krypton, OM4_KRYPTON), null, new ItemStack[]{wafer}, null, null, OM4_WAFER_TICKS, OM4_WAFER_DEFECTS));
        }
        if (ModItems.leadFrame40 != null && ceramic != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.PACKAGER, new ItemStack[]{wafer.copy(), new ItemStack(ModItems.leadFrame40), new ItemStack(ceramic)},
                    null, null, new ItemStack[]{processor}, null, null, OM4_PROCESSOR_TICKS, 0f));
        }
        if (die != null && ModFluids.liquidHelium != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.UPGRADE_STATION_EV, new ItemStack[]{n(processor, 4),
                    n(ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM), 2), new ItemStack(die)}, new FluidStack(ModFluids.liquidHelium, OM4_HELIUM),
                    null, new ItemStack[]{exo}, null, null, OM4_STATION_TICKS, 0f));
        }
        ItemStack natural = loot(com.sc.mob.ItemCombatLootSC.LootKind.NATURAL_WAFER);
        if (ModFluids.singularMatter != null && natural != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.EUV_LITHOGRAPH, new ItemStack[]{n(exo, 4), n(natural, 2)},
                    new FluidStack(ModFluids.singularMatter, OM4_MATTER_SUBSTRATE), null, new ItemStack[]{matrix}, null, null, OM4_MATRIX_SUBSTRATE_TICKS, 0f));
        }
        if (ModFluids.singularMatter != null) {
            RecipeRegistry.register(new MachineRecipe(MachineType.EUV_LITHOGRAPH, new ItemStack[]{n(exo, 4), si(com.sc.util.SiliconMaterial.GAAS_WAFER, 4)},
                    new FluidStack(ModFluids.singularMatter, OM4_MATTER_HOME), null, new ItemStack[]{matrix.copy()}, null, null, OM4_MATRIX_HOME_TICKS, 0f));
        }
        cheaper(processor, exo, matrix);
    }

    /**
     * ОМ-4: the cheaper twins (the old recipes stay): every 4 Controllers -> 1 Processor / Exo Processor / Singular Matrix by the product's
     * tier (none to replace - one stands in for the dearest part), the other repeated parts -25% rounded up; the carried block (a reactor's
     * generator, a storage's, the Armour Station) carried as in the old recipe (CarryRecipe, StorageUpgradeRecipeSC, ChargeCarryRecipeSC).
     */
    private static void cheaper(ItemStack processor, ItemStack exo, ItemStack matrix) {
        ItemStack hf = ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM);
        // the solar panels (QV, XV): S x4 H x2 C x2 X(controller) -> S x3 H x2 C x2 + the tier's processor
        OreRecipes.shapeless(gen(com.sc.energy.GeneratorType.SOLAR_QUANTUM), gen(com.sc.energy.GeneratorType.SOLAR_NANO),
                gen(com.sc.energy.GeneratorType.SOLAR_NANO), gen(com.sc.energy.GeneratorType.SOLAR_NANO), comp("heLoopModule"), comp("heLoopModule"),
                cab(com.sc.energy.CableType.QUANTUM), cab(com.sc.energy.CableType.QUANTUM), processor.copy());
        OreRecipes.shapeless(gen(com.sc.energy.GeneratorType.SOLAR_EXO), gen(com.sc.energy.GeneratorType.SOLAR_QUANTUM),
                gen(com.sc.energy.GeneratorType.SOLAR_QUANTUM), gen(com.sc.energy.GeneratorType.SOLAR_QUANTUM), hf.copy(), hf.copy(),
                cab(com.sc.energy.CableType.EXO), cab(com.sc.energy.CableType.EXO), exo.copy());
        // the Tokamak (QV): N x4 C x2 X x2 F -> N x3 C x2 P F; the Exo Reactor (XV): H x4 C X x2 T F -> H x3 C E T F
        GameRegistry.addRecipe(new ModRecipesCrafting.CarryRecipe(gen(com.sc.energy.GeneratorType.TOKAMAK), ModRecipesCrafting.CarryRecipe.GENERATOR,
                com.sc.block.BlockGeneratorSC.class, com.sc.energy.GeneratorType.TOKAMAK, "NCN", "PF ", "NC ",
                'N', comp("nb3SnCoil"), 'C', cab(com.sc.energy.CableType.QUANTUM), 'P', processor.copy(), 'F', gen(com.sc.energy.GeneratorType.FUSION_REACTOR)));
        GameRegistry.addRecipe(new ModRecipesCrafting.CarryRecipe(gen(com.sc.energy.GeneratorType.EXO_REACTOR), ModRecipesCrafting.CarryRecipe.GENERATOR,
                com.sc.block.BlockGeneratorSC.class, com.sc.energy.GeneratorType.EXO_REACTOR, "HCH", "ET ", "HF ",
                'H', hf.copy(), 'C', cab(com.sc.energy.CableType.EXO), 'E', exo.copy(), 'T', gen(com.sc.energy.GeneratorType.TOKAMAK),
                'F', comp("fusionCore")));
        // the storages: QV (K x4 C x2 E x2 X -> K x3 C x2 X P: the Processor for the two Helium Loops), XV (K x4 C x2 controllers x2 X -> K x3 C x2 X E), SV (the two Exo cores -> M)
        ItemStack iv = new ItemStack(ModBlocks.energyStorageSC, 1, 4), qv = new ItemStack(ModBlocks.energyStorageSC, 1, 5), xv = new ItemStack(ModBlocks.energyStorageSC, 1, 6);
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(qv.copy(), "KCK", "PX ", "KC ",
                'K', ModItems.ingot.stackOf(com.sc.util.Material.PLATINUM), 'C', cab(com.sc.energy.CableType.QUANTUM), 'X', iv, 'P', processor.copy()));
        GameRegistry.addRecipe(new StorageUpgradeRecipeSC(xv.copy(), "KCK", "PX ", "KC ",
                'K', hf.copy(), 'C', cab(com.sc.energy.CableType.EXO), 'X', qv, 'P', exo.copy()));
        if (com.sc.energy.Tier.SV.isContentReady()) {
            ItemStack sv = new ItemStack(ModBlocks.energyStorageSC, 1, com.sc.energy.Tier.SV.ordinal());
            GameRegistry.addRecipe(new StorageUpgradeRecipeSC(sv, "CFC", "MX ", "HCH",
                    'C', cab(com.sc.energy.CableType.EXO), 'F', comp("fusionCore"), 'M', matrix.copy(), 'X', xv, 'H', hf.copy()));
        }
        // the Singular station: the fusion core and the hafnium -> the Singular Matrix (the Exo core's charge carried as before)
        if (ModBlocks.singularStation != null && ModBlocks.gravityCoil != null && ModBlocks.armorStation != null) {
            int stationMax = new com.sc.tileentity.TileEntitySingularStationSC().getMaxEnergyStored();
            GameRegistry.addRecipe(new ChargeCarryRecipeSC(new ItemStack(ModBlocks.singularStation), stationMax, "GXG", "CAC", " M ",
                    'G', new ItemStack(ModBlocks.gravityCoil), 'X', new ItemStack(ModItems.battery, 1, com.sc.util.SingularStationMath.EXO_CORE_META),
                    'C', cab(com.sc.energy.CableType.SINGULAR), 'A', new ItemStack(ModBlocks.armorStation), 'M', matrix.copy())
                    .carryFrom(net.minecraft.item.Item.getItemFromBlock(ModBlocks.armorStation)));
        }
    }

    private static ItemStack si(com.sc.util.SiliconMaterial m, int count) {
        ItemStack s = ModItems.siliconMaterial.stackOf(m);
        s.stackSize = count;
        return s;
    }

    private static ItemStack gen(com.sc.energy.GeneratorType t) {
        return ModBlocks.generatorStack(t, 1);
    }

    private static ItemStack cab(com.sc.energy.CableType t) {
        return new ItemStack(ModBlocks.cableSC, 1, t.ordinal());
    }

    private static ItemStack comp(String name) {
        return new ItemStack(ModItems.component(name));
    }

    /** ОМ-5: the Data Port - a lead block, a comparator, a controller, 2 redstone, a titanium plate (shaped "RKR" / "CLP"). */
    private static void om5() {
        ItemStack port = com.sc.init.IdeaBlocksSC.dataPort(1);
        if (port == null || ModBlocks.leadBlock == null || ModItems.siliconMaterial == null || ModItems.component("tiPlate") == null) {
            return;
        }
        OreRecipes.shaped(port, "RKR", "CLP", 'R', Items.redstone, 'K', Items.comparator, 'C', si(com.sc.util.SiliconMaterial.CONTROLLER, 1),
                'L', new ItemStack(ModBlocks.leadBlock), 'P', comp("tiPlate"));
    }

    /**
     * ОМ-7: the Energy Bank's blocks (TODO(balance)) - 4 casings (4 W-Ti plates, 4 hafnium ingots, a titanium casing), 4 glass (4 lead
     * glass round a casing), the controller (a casing, 2 Exo Processors, a Singular Matrix, an SV storage), the 4-billion cell (2 SV
     * storages, 2 Processors, a casing), the 16-billion cell (4 small cells round a Singular Matrix), a port of tier T (a casing, a
     * cable of T, a controller).
     */
    private static void om7() {
        ItemStack casing = com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.CASING, 1);
        if (casing == null || ModItems.siliconMaterial == null || ModBlocks.energyStorageSC == null) {
            return;
        }
        ItemStack sv = new ItemStack(ModBlocks.energyStorageSC, 1, com.sc.energy.Tier.SV.ordinal());
        ItemStack matrix = si(com.sc.util.SiliconMaterial.SINGULAR_MATRIX, 1);
        if (ModItems.component("wTiPlate") != null && ModItems.component("tiCasing") != null) {
            OreRecipes.shaped(com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.CASING, 4), "WHW", "HTH", "WHW",
                    'W', comp("wTiPlate"), 'H', ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM), 'T', comp("tiCasing"));
        }
        if (ModBlocks.leadGlass != null) {
            OreRecipes.shaped(com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.GLASS, 4), " G ", "GCG", " G ",
                    'G', new ItemStack(ModBlocks.leadGlass), 'C', casing.copy());
        }
        OreRecipes.shaped(com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.CONTROLLER, 1), "EME", " C ", " S ",
                'E', si(com.sc.util.SiliconMaterial.EXO_PROCESSOR, 1), 'M', matrix.copy(), 'C', casing.copy(), 'S', sv.copy());
        ItemStack small = com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.CELL_4, 1);
        OreRecipes.shaped(small, "SCS", "P P", 'S', sv.copy(), 'C', casing.copy(), 'P', si(com.sc.util.SiliconMaterial.PROCESSOR, 1));
        OreRecipes.shaped(com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.CELL_16, 1), " X ", "XMX", " X ",
                'X', small.copy(), 'M', matrix.copy());
        for (com.sc.energy.Tier t : com.sc.energy.Tier.values()) {
            ItemStack cable = om7Cable(t);
            if (cable != null) {
                OreRecipes.shapeless(com.sc.init.IdeaBlocksSC.energyBank(com.sc.block.BlockEnergyBankSC.PORT_FIRST + t.ordinal(), 1),
                        casing.copy(), cable, si(com.sc.util.SiliconMaterial.CONTROLLER, 1));
            }
        }
    }

    /** ОМ-7: the port's cable of tier `t` - the insulated one of that tier first (LV: the insulated copper). */
    private static ItemStack om7Cable(com.sc.energy.Tier t) {
        com.sc.energy.CableType any = null;
        for (com.sc.energy.CableType c : com.sc.energy.CableType.values()) {
            if (c.tier == t) {
                if (c.insulated) {
                    return cab(c);
                }
                any = any == null ? c : any;
            }
        }
        return any == null ? null : cab(any);
    }
}
