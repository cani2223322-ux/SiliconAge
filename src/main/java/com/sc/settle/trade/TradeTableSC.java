package com.sc.settle.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.emp.EmpModule;
import com.sc.finale.FinaleItemsSC;
import com.sc.finale.FinaleRulesSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.Material;
import com.sc.util.SiliconMaterial;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.4, rules ПО-3, ПО-7): every deal of every Ф2 profession (proposal §7
 * «Примеры сделок»: the Technologist, the Metallurgist, the Gas Technician, the Cartographer, the Elder), built lazily
 * after the items exist (ModItems, init). The rules it keeps (the self-test checks them all): only the mod's and
 * vanilla's items; never armour, blades, ПЭ-1, reactors, bridge parts or Singularity Clots out; no thing (Credit
 * Chips aside) both bought and sold anywhere in the world (ПО-7). The signatures are fixed; the table is package T's.
 * <p>
 * The numbers are the proposal's (TODO(balance) all of them). What the player gives is the base here - TradeRulesSC
 * scales it by the level and the settlement. The proposal's «Чужак» sales (goods -> chips) carry minLevel VIOLATOR: a
 * «Нарушитель» only sells (ПД-1, at x2), so they must stay open to him; every purchase needs its level and buys(). Metals go in by their ore name (UniDict's ingots are as good as ours) and
 * come out as the mod's own ingots; everything else is an exact item and meta.
 * <p>
 * Not in Ф2, left out: the Cartographer's «Координатор арены» and the Elder's «Схема окраски «Наладчик»» and «Модуль
 * Литографов» (they came in Ф5, below); the Power Engineer, the Armourer and the Salvager came in Ф3, the Ф4 deals below. So that the Gas
 * Technician and the Cartographer bought something from a «Чужак» in Ф2, each takes one of the mod's things for chips
 * (gasPtfe, cartLenses: never sold by anyone - ПО-7); they stay beside the Ф4 deals.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §6.7, rules ПО-10..ПО-13; the keys appended): the Power Engineer (parts for chips,
 * the HV cell, the He-Loop Module; the charge service for Nano / Quantum / Exo pieces - one limit for the three), the
 * Armourer (the Defense Chip, three ПЭ-1 modules; the restoring services - TrophyRestoreSC's prices, one row per price
 * group and tier, no limit TODO(balance)), the Salvager (firmware, scrap, nanite dust and a Nano trophy piece for chips).
 * The services stand aside of ПО-7's «nothing sold»: what they give is the player's own thing (a piece charged, a trophy
 * made real). ПО-7's «both bought and sold» has its one definition here (boughtAndSold) - the self-tests ask it.
 * <p>
 * Ф4 (package C, docs/plan-substrate-tech-f4.md §9.5, rules ПО-14..ПО-16; the keys appended; TODO(balance)): the Gas Technician
 * buys a Boiled-out Loop (the Overheated's) for 3 chips, the Technologist four Natural Wafers (the Wafer Crab's) for 2, the
 * Salvager two Singular Armour Scraps for 8, four Induction Threads for 2, two Plasma Condensates for 3 - all «Чужак»; the
 * Cartographer sells the Ruin Direction Finder for 10 chips and a Focusing Lens («Партнёр», one a day). ПО-7 holds: the loot is
 * only bought, the finder only sold, the lens only a price. The Elder's Crumb counts towards the player's three a day
 * (TradeServerSC, CrumbLimitSC); a lost raid closes the settlement's trade for a day (TradeServerSC.R_RAID).
 * <p>
 * Ф5 (package P, docs/plan-substrate-tech-f5.md §9.5, rules ПО-17..ПО-22; the keys appended, the deals after each profession's
 * own - buildF5; TODO(balance)): the Cartographer sells the Arena Coordinator for 16 chips + a Singularity Crumb («Почётный», one a
 * day); the Elder the Lithographers' Module for a Breakdown Matrix + 48 chips («Свой», no limit of its own - the Matrix is the
 * Breakdown Warden's, and rare) and the paint scheme «Наладчик» for 3 Crumbs + 32 chips («Почётный», one a day); the Armourer the
 * ПЭ-1 module «Широкий канал» for 24 chips + 2 Controllers («Знакомый», one a day) - only while the caravan's rare deal is open to
 * the player (CaravanSC.dealOpen; closed - TradeServerSC.R_CARAVAN_DEAL), the deal made uses it up (CaravanSC.useDeal). ПО-7 (ПО-22):
 * the Crumb and the Matrix are only a part of a price (boughtAndSold never counts a purchase's part); the Coordinator, the Module,
 * the scheme and the module only come out, none of them forbiddenOut (no armour, blade or ПЭ-1). A Crumb paid is never counted
 * towards the player's Crumbs a day (CrumbLimitSC counts what a player gets - the Elder's ELDER_CRUMB alone, TradeServerSC).
 */
public final class TradeTableSC {

    /** The deals' keys (stable: the limits' counters and the lang sc.trade.offer.<key> go by them - append, never rename). */
    public static final String TECH_WAFERS = "techWafers", TECH_TRANSISTORS = "techTransistors", TECH_MEMORY = "techMemoryChips",
            TECH_GAAS = "techGaAs", TECH_CONTROLLERS = "techControllers",
            MET_GERMANIUM = "metGermanium", MET_NIOBIUM = "metNiobium", MET_TANTALUM = "metTantalum", MET_HAFNIUM = "metHafnium",
            GAS_PTFE = "gasPtfe", GAS_HELIUM = "gasHelium", GAS_DEUTERIUM = "gasDeuterium",
            CART_LENSES = "cartLenses", CART_CAPSULE = "cartCapsule", CART_MAP = "cartMap",
            ELDER_PENANCE = "elderPenance", ELDER_CARGO = "elderCargo", ELDER_CRUMB = "elderCrumb",
            // ---- Ф3 (appended)
            PE_PARTS = "peParts", PE_CELL = "peCellHV", PE_HE_LOOP = "peHeLoop",
            PE_CHARGE_NANO = "peChargeNano", PE_CHARGE_QUANTUM = "peChargeQuantum", PE_CHARGE_EXO = "peChargeExo",
            ARM_DEFENSE = "armDefenseChip", ARM_CAPACITY = "armModCapacity", ARM_COOLING = "armModCooling", ARM_FOCUS = "armModFocus",
            ARM_RESTORE_NANO = "armRestoreNano", ARM_RESTORE_QUANTUM = "armRestoreQuantum", ARM_RESTORE_EXO = "armRestoreExo",
            ARM_RESTORE_ARMS_NANO = "armRestoreArmsNano", ARM_RESTORE_ARMS_QUANTUM = "armRestoreArmsQuantum",
            ARM_RESTORE_ARMS_EXO = "armRestoreArmsExo", ARM_RESTORE_PE1 = "armRestorePe1",
            SAL_FIRMWARE = "salFirmware", SAL_NANO_SCRAP = "salNanoScrap", SAL_QUANTUM_SCRAP = "salQuantumScrap",
            SAL_EXO_SCRAP = "salExoScrap", SAL_NANITE = "salNaniteDust", SAL_TROPHY = "salNanoTrophy",
            // ---- Ф4 (appended)
            GAS_BOILED_LOOP = "gasBoiledLoop", TECH_NATURAL_WAFERS = "techNaturalWafer", SAL_SINGULAR_SCRAP = "salSingularScrap",
            SAL_INDUCTION = "salInductionThread", SAL_PLASMA = "salPlasmaCondensate", CART_FINDER = "cartRuinFinder",
            // ---- Ф5 (appended)
            CART_ARENA_COORDINATOR = "cartArenaCoordinator", ELDER_LITHO_MODULE = "elderLithoModule", ELDER_TUNER_SCHEME = "elderTunerScheme",
            ARM_MOD_WIDE = "armModWide";
    /** The Power Engineer's three charges share one limit (4 a day for all of them, ПО-10): their counter. */
    public static final String PE_CHARGE_COUNTER = "peCharge";
    /** The charge service's chips by suit (Nano, Quantum, Exo; ПО-10). TODO(balance) */
    public static final int[] CHARGE_CHIPS = {2, 4, 8};

    private static volatile List<TradeOfferSC> all;

    private TradeTableSC() {
    }

    /** The deals of `kind`, in the screen's order (empty for a guard). */
    public static List<TradeOfferSC> offers(NpcKindSC kind) {
        List<TradeOfferSC> out = new ArrayList<TradeOfferSC>();
        for (TradeOfferSC o : all()) {
            if (o.kind == kind) {
                out.add(o);
            }
        }
        return out;
    }

    /** Every deal of every profession (unmodifiable; empty before the items exist). */
    public static List<TradeOfferSC> all() {
        List<TradeOfferSC> l = all;
        if (l == null) {
            l = build();
        }
        return l;
    }

    /** The deal of `key`, null none. */
    public static TradeOfferSC byKey(String key) {
        for (TradeOfferSC o : all()) {
            if (o.key.equals(key)) {
                return o;
            }
        }
        return null;
    }

    /** Built once, after preInit registered the items (asked earlier: an empty list, not kept). Both sides call it. */
    private static synchronized List<TradeOfferSC> build() {
        if (all != null) {
            return all;
        }
        if (SettlementItemsSC.creditChip == null || ModItems.siliconMaterial == null || ModItems.ingot == null) {
            return Collections.emptyList();
        }
        List<TradeOfferSC> l = new ArrayList<TradeOfferSC>();
        // the table's «Чужак» sales are open to a «Нарушитель» too (ПД-1: he only sells, at x2); its purchases are not
        int anyone = ReputationSC.VIOLATOR, stranger = ReputationSC.STRANGER, acq = ReputationSC.ACQUAINTANCE,
                partner = ReputationSC.PARTNER, own = ReputationSC.OWN;
        // ---- the Technologist: wafers, GaAs, controllers
        NpcKindSC k = NpcKindSC.TECHNOLOGIST;
        l.add(sale(TECH_WAFERS, k, item(silicon(SiliconMaterial.SI_WAFER), 16), 3, anyone, 8));
        l.add(sale(TECH_TRANSISTORS, k, item(silicon(SiliconMaterial.TRANSISTOR), 8), 2, anyone, 8));
        l.add(sale(TECH_NATURAL_WAFERS, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.NATURAL_WAFER, 1), 4), 2, anyone, 8));   // Ф4 (ПО-14)
        l.add(sale(TECH_MEMORY, k, item(silicon(SiliconMaterial.MEMORY_CHIP), 3), 3, acq, 6));
        l.add(purchase(TECH_GAAS, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(6), item(silicon(SiliconMaterial.SI_WAFER), 1)},
                item(silicon(SiliconMaterial.GAAS_WAFER), 1), acq, 4));
        l.add(purchase(TECH_CONTROLLERS, k, chipsIn(12), item(silicon(SiliconMaterial.CONTROLLER), 2), partner, 2));
        // ---- the Metallurgist: tantalum, hafnium
        k = NpcKindSC.METALLURGIST;
        l.add(sale(MET_GERMANIUM, k, TradeOfferSC.Ingredient.ore("ingotGermanium", 6), 2, anyone, 6));
        l.add(sale(MET_NIOBIUM, k, TradeOfferSC.Ingredient.ore("ingotNiobium", 4), 2, anyone, 6));
        l.add(purchase(MET_TANTALUM, k, chipsIn(6), item(ModItems.ingot.stackOf(Material.TANTALUM), 3), partner, 6));
        l.add(purchase(MET_HAFNIUM, k, chipsIn(10), item(ModItems.ingot.stackOf(Material.HAFNIUM), 2), own, 4));
        // ---- the Gas Technician: liquid helium, deuterium
        k = NpcKindSC.GAS_TECH;
        l.add(sale(GAS_BOILED_LOOP, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.BOILED_LOOP, 1), 1), 3, anyone, 6));   // Ф4 (ПО-14)
        l.add(sale(GAS_PTFE, k, item(component("ptfeSheet"), 6), 2, anyone, 6));          // PTFE (the cryo tanks' seals). TODO(balance)
        l.add(purchase(GAS_HELIUM, k, chipsIn(8), item(stack(ModItems.liquidHeCell)), acq, 4));
        l.add(purchase(GAS_DEUTERIUM, k, chipsIn(14), item(stack(ModItems.deuteriumCell)), partner, 2));
        // ---- the Rift Cartographer: the Return Capsule, the Settlement Map
        k = NpcKindSC.CARTOGRAPHER;
        // Plain lenses (the Focuser's optics; Ф5's «Координатор арены» - buildF5). TODO(balance)
        l.add(sale(CART_LENSES, k, item(component("lens"), 6), 1, anyone, 6));
        l.add(purchase(CART_CAPSULE, k, chipsIn(8), item(SettlementItemsSC.capsules(1)), stranger, 2));
        l.add(new TradeOfferSC(CART_MAP, k, chipsIn(3), null, TradeOfferSC.OUT_MAP, 0, acq, 4, false, 1));
        l.add(purchase(CART_FINDER, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(10), item(component("focusLens"), 1)},
                item(stack(RelicItemsSC.ruinFinder)), partner, 1));                    // Ф4 (ПО-15, ПХ-6): one a day
        // ---- the Elder: the penance, a Stolen Cargo back, the Singularity Crumb
        k = NpcKindSC.ELDER;
        l.add(new TradeOfferSC(ELDER_PENANCE, k, chipsIn(ReputationSC.PENANCE_CHIPS), null, TradeOfferSC.OUT_REPUTATION,
                ReputationSC.PENANCE, ReputationSC.VIOLATOR, 1, true, 1));
        l.add(new TradeOfferSC(ELDER_CARGO, k, new TradeOfferSC.Ingredient[]{item(MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1))},
                null, TradeOfferSC.OUT_REPUTATION, ReputationSC.CARGO, ReputationSC.VIOLATOR, 8, false, 1));
        // Ф5's «3 Крупицы + 32 КЧ -> Схема окраски «Наладчик»», «Матрица Пробоя + 48 КЧ -> Модуль Литографов» - buildF5
        l.add(new TradeOfferSC(ELDER_CRUMB, k, chipsIn(64), item(stack(ModItems.singularCrumb)), TradeOfferSC.OUT_ITEM, 0,
                ReputationSC.HONOURED, TradeRulesSC.CRUMBS_PER_WEEK, true, SettlementSC.WEEK_DAYS));
        buildF3(l, anyone, acq, partner, own);
        buildF5(l, acq, own);
        for (TradeOfferSC o : l) {
            if (!complete(o)) {
                return Collections.emptyList();                 // an item missing (not registered yet): ask again later
            }
        }
        all = Collections.unmodifiableList(l);
        return all;
    }

    /**
     * Ф3 (ПО-10..ПО-12): the Power Engineer, the Armourer, the Salvager - appended after the Ф2 deals (each profession's
     * own order on the screen).
     */
    private static void buildF3(List<TradeOfferSC> l, int anyone, int acq, int partner, int own) {
        // ---- the Power Engineer: parts for chips, the HV cell, the He-Loop Module; the charge
        NpcKindSC k = NpcKindSC.POWER_ENGINEER;
        l.add(new TradeOfferSC(PE_PARTS, k, new TradeOfferSC.Ingredient[]{item(component("capacitor"), 4), item(component("resistor"), 2)},
                TradeOfferSC.Ingredient.chips(1), TradeOfferSC.OUT_ITEM, 0, anyone, 8, false, 1));
        l.add(purchase(PE_CELL, k, chipsIn(9), item(component("energyCellHV"), 1), partner, 3));
        l.add(new TradeOfferSC(PE_HE_LOOP, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(20), item(component("nb3SnCoil"), 1)},
                item(component("heLoopModule"), 1), TradeOfferSC.OUT_ITEM, 0, own, 1, true, SettlementSC.WEEK_DAYS));
        String[] charges = {PE_CHARGE_NANO, PE_CHARGE_QUANTUM, PE_CHARGE_EXO};
        ArmorSuit[] suits = {ArmorSuit.NANO, ArmorSuit.QUANTUM, ArmorSuit.EXO};     // never the Singular one (ПО-10)
        for (int i = 0; i < charges.length; i++) {
            l.add(new TradeOfferSC(charges[i], k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.piece(suits[i]),
                    TradeOfferSC.Ingredient.chips(CHARGE_CHIPS[i])}, null, TradeOfferSC.OUT_CHARGE, 0, acq, 4, false, 1, PE_CHARGE_COUNTER));
        }
        // ---- the Armourer: the Defense Chip, the ПЭ-1 modules (global limits); the restoring (ПТ-12)
        k = NpcKindSC.ARMOURER;
        l.add(new TradeOfferSC(ARM_DEFENSE, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(12), item(silicon(SiliconMaterial.CONTROLLER), 2)},
                item(ModItems.armorChip == null ? null : ModItems.armorChip.stackOf(ChipType.DEFENSE, 1), 1), TradeOfferSC.OUT_ITEM, 0, partner, 1, true, 1));
        l.add(new TradeOfferSC(ARM_CAPACITY, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(16), item(component("tantalumCapacitor"), 4)},
                item(module(EmpModule.CAPACITY), 1), TradeOfferSC.OUT_ITEM, 0, partner, 1, true, 1));
        l.add(new TradeOfferSC(ARM_COOLING, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(16), item(silicon(SiliconMaterial.TRANSISTOR), 2)},
                item(module(EmpModule.COOLING), 1), TradeOfferSC.OUT_ITEM, 0, partner, 1, true, 1));
        l.add(new TradeOfferSC(ARM_FOCUS, k, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(24), item(component("focusLens"), 2)},
                item(module(EmpModule.FOCUS), 1), TradeOfferSC.OUT_ITEM, 0, own, 1, true, SettlementSC.WEEK_DAYS));
        String[] pieces = {ARM_RESTORE_NANO, ARM_RESTORE_QUANTUM, ARM_RESTORE_EXO};
        String[] arms = {ARM_RESTORE_ARMS_NANO, ARM_RESTORE_ARMS_QUANTUM, ARM_RESTORE_ARMS_EXO};
        for (int t = 0; t < TrophyRestoreSC.ARMOURER_TIERS; t++) {
            l.add(restore(pieces[t], TrophyRestoreSC.G_PIECE, t));
        }
        for (int t = 0; t < TrophyRestoreSC.ARMOURER_TIERS; t++) {
            l.add(restore(arms[t], TrophyRestoreSC.G_ARMS, t));
        }
        l.add(restore(ARM_RESTORE_PE1, TrophyRestoreSC.G_PE1, ArmorSuit.EXO.ordinal()));
        // ---- the Salvager: what the Substrate's fights leave (all «Чужак» - open to a «Нарушитель», ПД-1)
        k = NpcKindSC.SALVAGER;
        l.add(sale(SAL_FIRMWARE, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, 1), 3), 1, anyone, 16));
        l.add(sale(SAL_NANO_SCRAP, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1), 8), 2, anyone, 8));
        l.add(sale(SAL_QUANTUM_SCRAP, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.QUANTUM_SCRAP, 1), 6), 3, anyone, 8));
        l.add(sale(SAL_EXO_SCRAP, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.EXO_SCRAP, 1), 4), 5, anyone, 8));
        l.add(sale(SAL_SINGULAR_SCRAP, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.SINGULAR_SCRAP, 1), 2), 8, anyone, 8));   // Ф4 (ПО-14)
        l.add(sale(SAL_NANITE, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.NANITE_DUST, 1), 16), 1, anyone, 8));
        l.add(sale(SAL_TROPHY, k, TradeOfferSC.Ingredient.trophies(TrophyRestoreSC.metas(TrophyRestoreSC.G_PIECE, ArmorSuit.NANO.ordinal())),
                6, anyone, 4));
        l.add(sale(SAL_INDUCTION, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.INDUCTION_THREAD, 1), 4), 2, anyone, 8));     // Ф4 (ПО-14)
        l.add(sale(SAL_PLASMA, k, item(MobItemsSC.loot(ItemCombatLootSC.LootKind.PLASMA_CONDENSATE, 1), 2), 3, anyone, 8));
    }

    /**
     * Ф5 (ПО-17..ПО-21): the Cartographer's Arena Coordinator, the Elder's Lithographers' Module and «Наладчик» scheme, the
     * Armourer's «Широкий канал» (the caravan's rare deal - TradeServerSC asks CaravanSC.dealOpen) - appended after every
     * profession's earlier deals. Their «one a day» is at this settlement, as the Cartographer's finder (the proposal's «1», not
     * «глоб.»); the Module has no limit of its own (TradeOfferSC.UNLIMITED: the Matrix it takes is the boss's).
     */
    private static void buildF5(List<TradeOfferSC> l, int acq, int own) {
        int honoured = ReputationSC.HONOURED;
        ItemStack crumb = stack(ModItems.singularCrumb);
        // ---- the Cartographer: 16 chips + a Crumb -> the Arena Coordinator (ПО-17)
        l.add(purchase(CART_ARENA_COORDINATOR, NpcKindSC.CARTOGRAPHER, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(16), item(crumb, 1)},
                item(FinaleItemsSC.arenaCoordinator(1)), honoured, 1));
        // ---- the Elder: a Breakdown Matrix + 48 chips -> the Lithographers' Module (ПО-18); 3 Crumbs + 32 chips -> «Наладчик» (ПО-19)
        l.add(new TradeOfferSC(ELDER_LITHO_MODULE, NpcKindSC.ELDER, new TradeOfferSC.Ingredient[]{item(RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX, 1), 1),
                TradeOfferSC.Ingredient.chips(48)}, item(FinaleItemsSC.lithoModule(1)), TradeOfferSC.OUT_ITEM, 0, own, TradeOfferSC.UNLIMITED, false, 1));
        l.add(purchase(ELDER_TUNER_SCHEME, NpcKindSC.ELDER, new TradeOfferSC.Ingredient[]{item(crumb, 3), TradeOfferSC.Ingredient.chips(32)},
                item(FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_TUNER, 1)), honoured, 1));
        // ---- the Armourer: 24 chips + 2 Controllers -> «Широкий канал» (ПО-21) - only while the caravan's rare deal is open (TradeServerSC)
        l.add(purchase(ARM_MOD_WIDE, NpcKindSC.ARMOURER, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(24),
                item(silicon(SiliconMaterial.CONTROLLER), 2)}, item(module(EmpModule.WIDE), 1), acq, 1));
    }

    /**
     * One of the Armourer's restoring services (ПТ-12): a trophy of `group` at `tier` + his chips + the parts -> the real
     * item (TrophyRestoreSC: the same parts the station takes); no limit (TradeOfferSC.UNLIMITED, TODO(balance)). Its parts
     * not registered yet: a side with nothing in it - complete() then asks for the table again later.
     */
    private static TradeOfferSC restore(String key, int group, int tier) {
        TradeOfferSC.Ingredient[] parts = TrophyRestoreSC.parts(group, tier);
        if (parts == null) {
            parts = new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.of(null, 1)};
        }
        TradeOfferSC.Ingredient[] in = new TradeOfferSC.Ingredient[2 + parts.length];
        in[0] = TradeOfferSC.Ingredient.trophies(TrophyRestoreSC.metas(group, tier));
        in[1] = TradeOfferSC.Ingredient.chips(Math.max(1, TrophyRestoreSC.armourerChips(group, tier)));
        System.arraycopy(parts, 0, in, 2, parts.length);
        return new TradeOfferSC(key, NpcKindSC.ARMOURER, in, null, TradeOfferSC.OUT_RESTORE, 0, TrophyRestoreSC.armourerLevel(group, tier),
                TradeOfferSC.UNLIMITED, false, 1);
    }

    /** Every side of `o` names a thing (an item still null before init would make a dead deal; a matcher - something to match). */
    private static boolean complete(TradeOfferSC o) {
        for (TradeOfferSC.Ingredient i : o.in) {
            if (i != null && i.any()) {
                if (i.choices().isEmpty()) {
                    return false;
                }
                continue;
            }
            if (i == null || (i.stack == null && i.ore == null) || (i.stack != null && i.stack.getItem() == null)) {
                return false;
            }
        }
        return o.outKind != TradeOfferSC.OUT_ITEM || (o.out != null && o.out.stack != null && o.out.stack.getItem() != null);
    }

    /** The player sells `goods` for `chips` Credit Chips. */
    private static TradeOfferSC sale(String key, NpcKindSC kind, TradeOfferSC.Ingredient goods, int chips, int level, int limit) {
        return new TradeOfferSC(key, kind, new TradeOfferSC.Ingredient[]{goods}, TradeOfferSC.Ingredient.chips(chips),
                TradeOfferSC.OUT_ITEM, 0, level, limit, false, 1);
    }

    /** The player buys `out` for `in`. */
    private static TradeOfferSC purchase(String key, NpcKindSC kind, TradeOfferSC.Ingredient[] in, TradeOfferSC.Ingredient out, int level, int limit) {
        return new TradeOfferSC(key, kind, in, out, TradeOfferSC.OUT_ITEM, 0, level, limit, false, 1);
    }

    private static TradeOfferSC.Ingredient[] chipsIn(int n) {
        return new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(n)};
    }

    private static TradeOfferSC.Ingredient item(ItemStack s) {
        return item(s, s == null ? 1 : s.stackSize);
    }

    private static TradeOfferSC.Ingredient item(ItemStack s, int count) {
        ItemStack one = s == null ? null : s.copy();
        if (one != null) {
            one.stackSize = 1;
        }
        return TradeOfferSC.Ingredient.of(one, count);
    }

    private static ItemStack silicon(SiliconMaterial m) {
        return ModItems.siliconMaterial.stackOf(m);
    }

    private static ItemStack component(String name) {
        Item i = ModItems.component(name);
        return i == null ? null : new ItemStack(i);
    }

    private static ItemStack stack(Item i) {
        return i == null ? null : new ItemStack(i);
    }

    private static ItemStack module(EmpModule m) {
        return ModItems.empModule == null ? null : ModItems.empModule.stackOf(m);
    }

    // ------------------------------------------------------------------ for the tooltips and the handbook

    /**
     * The Salvager's sale that takes `s` (a combat loot, a trophy) - the table's base numbers: in[0].count of it for
     * out.count chips; null if he doesn't buy it.
     */
    public static TradeOfferSC salvagerSale(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        for (TradeOfferSC o : offers(NpcKindSC.SALVAGER)) {
            if (o.sell() && o.in.length == 1 && o.in[0] != null && o.in[0].matches(s)) {
                return o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ПО-7

    /**
     * ПО-7's «ни один предмет не продаётся и не покупается одновременно», its one definition (the self-tests ask it): the
     * display names of the things both bought and sold - empty when the rule holds. «Bought»: what a deal turns into chips
     * or «Доверие» (the goods of a deal the player pays no chips for - a sale, a Stolen Cargo back); «sold»: what a deal
     * gives out (a purchase's item). A part of a purchase's price (the GaAs deal's wafer, the Armourer's Controllers) is
     * neither: nothing comes back for it, so no loop chips -> thing -> chips can run through it. The services (ПО-13) stand
     * aside: they give the player's own thing back, their parts are a price.
     */
    public static List<String> boughtAndSold() {
        List<TradeOfferSC.Ingredient> bought = new ArrayList<TradeOfferSC.Ingredient>(), sold = new ArrayList<TradeOfferSC.Ingredient>();
        for (TradeOfferSC o : all()) {
            if (o.service()) {
                continue;
            }
            if (!o.buy()) {
                for (TradeOfferSC.Ingredient i : o.in) {
                    if (i != null && !i.isChips()) {
                        bought.add(i);
                    }
                }
            }
            if (o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && !o.out.isChips()) {
                sold.add(o.out);
            }
        }
        List<String> both = new ArrayList<String>();
        for (TradeOfferSC.Ingredient b : bought) {
            for (TradeOfferSC.Ingredient o : sold) {
                ItemStack os = o.display();
                for (ItemStack bs : b.choices()) {
                    if (bs != null && os != null && (b.matches(os) || o.matches(bs))) {
                        both.add(bs.getDisplayName());
                    }
                }
            }
        }
        return both;
    }

    /**
     * ПО-7: a thing the Tuners never sell - any armour, the mod's blades and guns, the ПЭ-1, a Singularity Clot,
     * the bridge's parts (its blocks, remote, coordinator, link module), the reactors (the generator blocks, the
     * tokamak and gravity coils, the gravity stabiliser, the fusion core). The self-test runs every output through it.
     */
    public static boolean forbiddenOut(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return false;
        }
        Item i = s.getItem();
        if (i instanceof ItemArmor || i instanceof com.sc.item.ItemBladeSC || i instanceof com.sc.item.ItemWeaponSC
                || i instanceof com.sc.emp.ItemEmpGunSC || i == ModItems.singularClot || i == ModItems.bridgeRemote
                || i == ModItems.coordinator || i == ModItems.bridgeLinkModule || i == ModItems.component("fusionCore")) {
            return true;
        }
        Block b = Block.getBlockFromItem(i);
        return b != null && (b == ModBlocks.bridge || b == ModBlocks.generatorSC || b == ModBlocks.generatorSC2
                || b == ModBlocks.tokamakCoil || b == ModBlocks.gravityCoil || b == ModBlocks.gravStabiliser);
    }
}
