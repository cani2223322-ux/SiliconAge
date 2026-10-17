package com.sc.settle.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
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
 * Not in Ф2, left out (TODO): the Technologist's «4 Природные пластины -> 2 КЧ» (the Substrate Dweller, Ф4), the Gas
 * Technician's «1 Выкипевший контур -> 3 КЧ» (the Overheated, Ф4), the Cartographer's «Пеленгатор руин» and
 * «Координатор арены» (Ф3-Ф5), the Elder's «Схема окраски «Наладчик»» and «Модуль Литографов» (Ф3 / Ф5), the Power
 * Engineer, the Armourer and the Salvager (Ф3). So that the Gas Technician and the Cartographer buy something from a
 * «Чужак» meanwhile, each takes one of the mod's things for chips (gasPtfe, cartLenses: never sold by anyone - ПО-7).
 */
public final class TradeTableSC {

    /** The deals' keys (stable: the limits' counters and the lang sc.trade.offer.<key> go by them - append, never rename). */
    public static final String TECH_WAFERS = "techWafers", TECH_TRANSISTORS = "techTransistors", TECH_MEMORY = "techMemoryChips",
            TECH_GAAS = "techGaAs", TECH_CONTROLLERS = "techControllers",
            MET_GERMANIUM = "metGermanium", MET_NIOBIUM = "metNiobium", MET_TANTALUM = "metTantalum", MET_HAFNIUM = "metHafnium",
            GAS_PTFE = "gasPtfe", GAS_HELIUM = "gasHelium", GAS_DEUTERIUM = "gasDeuterium",
            CART_LENSES = "cartLenses", CART_CAPSULE = "cartCapsule", CART_MAP = "cartMap",
            ELDER_PENANCE = "elderPenance", ELDER_CARGO = "elderCargo", ELDER_CRUMB = "elderCrumb";

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
        // TODO(Ф4): «4 Природные пластины -> 2 КЧ» (the Substrate Dweller's), Чужак, 8
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
        // TODO(Ф4): «1 Выкипевший контур -> 3 КЧ» (the Overheated's), Чужак, 6. Meanwhile PTFE (the cryo tanks' seals). TODO(balance)
        l.add(sale(GAS_PTFE, k, item(component("ptfeSheet"), 6), 2, anyone, 6));
        l.add(purchase(GAS_HELIUM, k, chipsIn(8), item(stack(ModItems.liquidHeCell)), acq, 4));
        l.add(purchase(GAS_DEUTERIUM, k, chipsIn(14), item(stack(ModItems.deuteriumCell)), partner, 2));
        // ---- the Rift Cartographer: the Return Capsule, the Settlement Map
        k = NpcKindSC.CARTOGRAPHER;
        // TODO(Ф3-Ф5): «Пеленгатор руин», «Координатор арены». Meanwhile plain lenses (the Focuser's optics). TODO(balance)
        l.add(sale(CART_LENSES, k, item(component("lens"), 6), 1, anyone, 6));
        l.add(purchase(CART_CAPSULE, k, chipsIn(8), item(SettlementItemsSC.capsules(1)), stranger, 2));
        l.add(new TradeOfferSC(CART_MAP, k, chipsIn(3), null, TradeOfferSC.OUT_MAP, 0, acq, 4, false, 1));
        // ---- the Elder: the penance, a Stolen Cargo back, the Singularity Crumb
        k = NpcKindSC.ELDER;
        l.add(new TradeOfferSC(ELDER_PENANCE, k, chipsIn(ReputationSC.PENANCE_CHIPS), null, TradeOfferSC.OUT_REPUTATION,
                ReputationSC.PENANCE, ReputationSC.VIOLATOR, 1, true, 1));
        l.add(new TradeOfferSC(ELDER_CARGO, k, new TradeOfferSC.Ingredient[]{item(MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1))},
                null, TradeOfferSC.OUT_REPUTATION, ReputationSC.CARGO, ReputationSC.VIOLATOR, 8, false, 1));
        // TODO(Ф3/Ф5): «3 Крупицы + 32 КЧ -> Схема окраски», «Матрица Пробоя + 48 КЧ -> Модуль Литографов»
        l.add(new TradeOfferSC(ELDER_CRUMB, k, chipsIn(64), item(stack(ModItems.singularCrumb)), TradeOfferSC.OUT_ITEM, 0,
                ReputationSC.HONOURED, TradeRulesSC.CRUMBS_PER_WEEK, true, SettlementSC.WEEK_DAYS));
        for (TradeOfferSC o : l) {
            if (!complete(o)) {
                return Collections.emptyList();                 // an item missing (not registered yet): ask again later
            }
        }
        all = Collections.unmodifiableList(l);
        return all;
    }

    /** Every side of `o` names a thing (an item still null before init would make a dead deal). */
    private static boolean complete(TradeOfferSC o) {
        for (TradeOfferSC.Ingredient i : o.in) {
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

    // ------------------------------------------------------------------ ПО-7

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
