package com.sc.settle.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sc.init.ModItems;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityLeechSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityOverheatedSC;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.settle.ReputationSC;
import com.sc.util.SiliconMaterial;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.6, rule ПО-8): the Elders' contracts (proposal §7 «Контракты») - the
 * board shows BOARD a game day per player (the same at every Elder, renewed at dawn), a player takes at most
 * TradeRulesSC.CONTRACTS_PER_DAY a day; only hostiles born in Si-14 killed by that real player count. The goals:
 * KILL (kills of one mob kind), DELIVER (things handed to the Elder), SUPPRESS_KILL (armoured kills with a piece under
 * ПЭ-1's suppression at death), CARGO (Stolen Cargoes handed in), ANCHOR (the world's one-time anchor contract).
 * Pure; the signatures are fixed, the table package T's.
 * <p>
 * The numbers are the proposal's (TODO(balance)): «Убить 10 Мародёров» 8 КЧ +20, «Доставить 32 Танталовых
 * конденсатора» 14 КЧ +25, «Подавить ПЭ-1 и добить 5 бронированных» 10 КЧ +25 and 20% a ПЭ-1 module, «Вернуть 3
 * Краденых груза» 10 КЧ +35; the one-time «Поставить Межпространственный якорь» 20 КЧ +50 (ReputationSC.ANCHOR*).
 * DELIVER and CARGO may be handed in a part at a time; KILL and SUPPRESS_KILL count by themselves (EconomyEventsSC).
 * A taken contract stays open past dawn until it is handed in or dropped.
 * <p>
 * Ф6 (package T, docs/plan-substrate-tech-f6.md §8.3, rules ПО-27, ПД-7; the keys and the goal appended; TODO(balance)): an embassy's
 * Elder - the Ambassador - has a board of his own (embassyBoard: BOARD of embassyPool, a day per player as the Substrate's): «Снабжение
 * экспедиции» - 64 bread, 48 logs or 24 iron ingots (DELIVER) for 6 chips and +15; the Substrate's «Доставить 32 Танталовых
 * конденсатора» (the same contract, 14 chips +25); «Депеша домой» (the new goal DISPATCH: nothing to carry - handed in by the button at
 * the Elder of ANY Substrate settlement, never the Ambassador, within DISPATCH_DAYS) for 10 chips and +30. No kill contract there. The
 * three a day are the player's over every Elder (TradeRulesSC.CONTRACTS_PER_DAY); an embassy's contracts are taken from «Чужак»
 * (takeLevel - a newcomer's way to «Знакомый» without a bridge, proposal §8) and handed in where they came from (TradeServerSC: the
 * Ambassador's at an embassy, the Substrate's and the dispatch at a Substrate Elder). The «Доверие» of what is handed in at an embassy
 * is held under its ceiling (ReputationSC.ceiling - 599); the dispatch is handed in in the Substrate - over it.
 * <p>
 * ПД-1 (docs/plan-ideas-2026-10-26.md, «Живая доска контрактов», ★ A; TODO(balance) - all the numbers): the Substrate's pool grows to 20 -
 * 10 common ones (the old four, «Охота на Штурмовиков», «Работа в бурю» STORM_KILL, «Показания Узлов» READINGS, «Сейф Отступников» SAFE,
 * «Срочная депеша» URGENT, «Тайник Сигнала» SIGNAL), 5 local ones (G_LOCAL, by ContractLocalSC's tags of the settlement), 4 orders of
 * the Tuners (G_ORDER, DELIVER: transistors, controllers, Nb3Sn coils, an EV battery - never helium or deuterium) and the weekly Big
 * order (G_BIG, BIG_ORDER: everything at once). A Substrate Elder's board (substrateBoard): BOARD common + 1 local + 1 order + the Big
 * one; the three a day and the 64 chips stay. The Ambassador's board is unchanged.
 */
public final class ContractTableSC {

    public static final int KILL = 0, DELIVER = 1, SUPPRESS_KILL = 2, CARGO = 3, ANCHOR = 4,
            // ---- Ф6 (appended): «Депеша домой» - taken at an embassy, handed in at a Substrate Elder
            DISPATCH = 5,
            // ---- ПД-1 (docs/plan-ideas-2026-10-26.md, «Живая доска контрактов», appended): an armoured kill during an EMP storm, a
            // Dead Node's readings, a camp's safe, «Срочная депеша» before sunset, the Signal's cache, the weekly Big order
            STORM_KILL = 6, READINGS = 7, SAFE = 8, URGENT = 9, SIGNAL = 10, BIG_ORDER = 11;
    /** ПД-1: a contract's group - the common ones, the settlement's local ones (ContractLocalSC tags), the Tuners' orders, the weekly Big order. */
    public static final int G_GENERAL = 0, G_LOCAL = 1, G_ORDER = 2, G_BIG = 3;
    /** Common contracts on the board a day (ПД-1: plus one local, one order and the Big order - substrateBoard). */
    public static final int BOARD = 3;
    /** The contracts' keys (stable: ContractRec.key, lang sc.contract.<key> and .desc). */
    public static final String KILL_MARAUDERS = "killMarauders", DELIVER_TANTALUM_CAPS = "deliverTantalumCaps",
            SUPPRESS_KILL_KEY = "suppressKill", RETURN_CARGO = "returnCargo", ANCHOR_KEY = "anchor",
            // ---- Ф6 (appended): the Ambassador's board
            EMB_SUPPLY_BREAD = "embSupplyBread", EMB_SUPPLY_LOGS = "embSupplyLogs", EMB_SUPPLY_IRON = "embSupplyIron", EMB_DISPATCH = "embDispatch",
            // ---- ПД-1 (appended): the new common ones, the local ones, the orders, the Big order
            HUNT_TROOPERS = "huntTroopers", STORM_ARMOURED = "stormArmoured", NODE_READINGS = "nodeReadings", CRACK_SAFE = "crackSafe",
            URGENT_DISPATCH = "urgentDispatch", SIGNAL_CACHE = "signalCache", HUNT_COLOSSUS = "huntColossus", HUNT_LEECHES = "huntLeeches",
            HUNT_OVERHEATED = "huntOverheated", DOWN_DRONES = "downDrones", HUNT_SHELLS = "huntShells", ORDER_TRANSISTORS = "orderTransistors",
            ORDER_CONTROLLERS = "orderControllers", ORDER_COILS = "orderCoils", ORDER_EV_BATTERY = "orderEvBattery", BIG_ORDER_KEY = "bigOrder";
    /**
     * ПД-1: «Срочная депеша» is taken from dawn until URGENT_TAKE_UNTIL ticks of the day (noon is 6000), to the nearest other Substrate
     * settlement with an Elder no farther than URGENT_MAX blocks (none - not on the board), handed in there before sunset (day·24000 +
     * URGENT_SUNSET). TODO(balance)
     */
    public static final int URGENT_TAKE_UNTIL = 6000, URGENT_MAX = 1500, URGENT_SUNSET = 12000;
    /** ПД-1: PlayerRec.extra key of the Big order ({w: the week it was taken, d: the day}). */
    public static final String BIG_EXTRA = "O";
    /** ПД-1: ContractRec.extra keys - the dispatch's destination settlement and deadline (world time), the readings' crossings ({i, j} pairs). */
    public static final String X_TO = "to", X_BY = "by", X_NODES = "nodes";
    /** ПД-1: ModItems.battery's EV meta (ItemBatterySC.TIERS: LV, MV, HV, EV ...). */
    public static final int EV_BATTERY_META = 3;
    /** ПД-1: the local contract's and the order's own salts (the common three keep the old shuffle). */
    private static final long LOCAL_SALT = 0x10CA1C0DL, ORDER_SALT = 0x0DE2BEEFL;
    /**
     * Ф6 (ПО-27): «Депеша домой» is handed in until DISPATCH_DAYS dawns after the day it was taken (that day and three more: never
     * less than three full game days); later - it is lost (TradeServerSC.R_DISPATCH_LATE). TODO(balance)
     */
    public static final int DISPATCH_DAYS = 3;
    /** Ф6: the Ambassador's board is shuffled apart from the Substrate's (a salt on the same seed). */
    private static final long EMBASSY_SALT = 0xE3BA55ADL;

    private static volatile List<Contract> pool, embassyPool;

    private ContractTableSC() {
    }

    /** One contract. `key`: stable (the registry's ContractRec.key, lang sc.contract.<key>). */
    public static final class Contract {
        public final String key;
        public final int goal, target, chips, reputation;
        /** SUPPRESS_KILL: the chance of a ПЭ-1 module on top, %. */
        public final int modulePct;
        /** KILL: the mob's entity name ("marauder"); DELIVER: an ore name or "" (then `item`). */
        public final String what;
        /** DELIVER without an ore name, CARGO: the exact thing handed in (meta strict); null otherwise. */
        public final ItemStack item;
        /** ПД-1: G_GENERAL / G_LOCAL / G_ORDER / G_BIG. */
        public final int group;
        /** ПД-1: a local one's ContractLocalSC.TAG_* (0: for every settlement). */
        public final int localTag;
        /** ПД-1: BIG_ORDER's sides - all handed in at once (null for the rest). */
        private final TradeOfferSC.Ingredient[] bundle;

        public Contract(String key, int goal, int target, int chips, int reputation, int modulePct, String what) {
            this(key, goal, target, chips, reputation, modulePct, what, null);
        }

        public Contract(String key, int goal, int target, int chips, int reputation, int modulePct, String what, ItemStack item) {
            this(key, goal, target, chips, reputation, modulePct, what, item, G_GENERAL, 0, null);
        }

        /** ПД-1: the full form - a group, a local tag, a Big order's sides. */
        public Contract(String key, int goal, int target, int chips, int reputation, int modulePct, String what, ItemStack item, int group,
                        int localTag, TradeOfferSC.Ingredient[] bundle) {
            this.key = key;
            this.goal = goal;
            this.target = target;
            this.chips = chips;
            this.reputation = reputation;
            this.modulePct = modulePct;
            this.what = what;
            this.item = item;
            this.group = group;
            this.localTag = localTag;
            this.bundle = bundle == null ? null : bundle.clone();
        }

        /** ПД-1: BIG_ORDER's sides (a copy; empty for the rest). */
        public TradeOfferSC.Ingredient[] bundle() {
            return bundle == null ? new TradeOfferSC.Ingredient[0] : bundle.clone();
        }

        /** DELIVER / CARGO: what the Elder takes (one side of a deal - TradeOfferSC.Ingredient's strict match); null for the rest. */
        public TradeOfferSC.Ingredient handIn() {
            if (goal != DELIVER && goal != CARGO) {
                return null;
            }
            if (what != null && !what.isEmpty()) {
                return TradeOfferSC.Ingredient.ore(what, target);
            }
            return item == null ? null : TradeOfferSC.Ingredient.of(item, target);
        }

        /** sc.contract.<key> - the board's line («Убить 10 Мародёров»); .desc - the details. */
        public String langKey() {
            return "sc.contract." + key;
        }
    }

    /** The one-time «Поставить Межпространственный якорь» (not on the board: the Elder offers it on its own). */
    public static final Contract ANCHOR_CONTRACT = new Contract(ANCHOR_KEY, ANCHOR, 1, ReputationSC.ANCHOR_CHIPS, ReputationSC.ANCHOR, 0, "");

    /**
     * Every contract of the Substrate's Elders (not the one-time ANCHOR: the Elder offers it on its own; Ф6: not the Ambassador's -
     * embassyPool). Empty before the items exist.
     */
    public static List<Contract> pool() {
        List<Contract> p = pool;
        return p != null ? p : build();
    }

    /** Ф6 (ПО-27): the Ambassador's contracts - the supplies, the tantalum capacitors (the pool's own), the dispatch. Empty before the items exist. */
    public static List<Contract> embassyPool() {
        List<Contract> p = embassyPool;
        if (p == null) {
            build();
            p = embassyPool;
        }
        return p != null ? p : Collections.<Contract>emptyList();
    }

    /** Ф6: the pool of an embassy's Elder (`embassy`) or a Substrate one. */
    public static List<Contract> pool(boolean embassy) {
        return embassy ? embassyPool() : pool();
    }

    private static synchronized List<Contract> build() {
        if (pool != null) {
            return pool;
        }
        Item caps = ModItems.component("tantalumCapacitor"), coil = ModItems.component("nb3SnCoil");
        ItemStack cargo = MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1);
        if (caps == null || cargo == null || coil == null || ModItems.siliconMaterial == null || ModItems.battery == null) {
            return Collections.emptyList();
        }
        List<Contract> l = new ArrayList<Contract>();
        l.add(new Contract(KILL_MARAUDERS, KILL, 10, 8, 20, 0, EntityMarauderSC.NAME));
        l.add(new Contract(DELIVER_TANTALUM_CAPS, DELIVER, 32, 14, 25, 0, "", new ItemStack(caps)));
        l.add(new Contract(SUPPRESS_KILL_KEY, SUPPRESS_KILL, 5, 10, 25, 20, ""));
        l.add(new Contract(RETURN_CARGO, CARGO, 3, 10, 35, 0, "", cargo));
        // ПД-1 (TODO(balance), all the numbers): the new common ones
        l.add(new Contract(HUNT_TROOPERS, KILL, 4, 10, 25, 0, EntityTrooperSC.NAME));
        l.add(new Contract(STORM_ARMOURED, STORM_KILL, 3, 12, 30, 0, ""));
        l.add(new Contract(NODE_READINGS, READINGS, 2, 8, 20, 0, ""));
        l.add(new Contract(CRACK_SAFE, SAFE, 1, 14, 30, 0, ""));
        l.add(new Contract(URGENT_DISPATCH, URGENT, 1, 10, 25, 0, ""));
        l.add(new Contract(SIGNAL_CACHE, SIGNAL, 1, 16, 40, 0, ""));
        // the local ones (ContractLocalSC.tags of the settlement)
        l.add(local(HUNT_COLOSSUS, 1, 12, 30, EntityColossusSC.NAME, ContractLocalSC.TAG_RIDGES));
        l.add(local(HUNT_LEECHES, 4, 8, 20, EntityLeechSC.NAME, ContractLocalSC.TAG_RIDGES));
        l.add(local(HUNT_OVERHEATED, 3, 10, 25, EntityOverheatedSC.NAME, ContractLocalSC.TAG_OXIDE));
        l.add(local(DOWN_DRONES, 4, 8, 20, EntitySentryDroneSC.NAME, ContractLocalSC.TAG_NODE));
        l.add(local(HUNT_SHELLS, 4, 10, 25, EntityHollowShellSC.NAME, ContractLocalSC.TAG_NODE));
        // the Tuners' orders (DELIVER, a part at a time): the factory's products - never helium or deuterium
        ItemStack transistor = ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR);
        ItemStack controller = ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER);
        ItemStack coils = new ItemStack(coil), battery = new ItemStack(ModItems.battery, 1, EV_BATTERY_META);
        l.add(order(ORDER_TRANSISTORS, 64, 8, 15, transistor));
        l.add(order(ORDER_CONTROLLERS, 6, 12, 25, controller));
        l.add(order(ORDER_COILS, 6, 12, 25, coils));
        l.add(order(ORDER_EV_BATTERY, 1, 10, 20, battery));        // any charge: Ingredient.of matches the meta, never the NBT
        // the Big order: everything at once, once a game week a player
        l.add(new Contract(BIG_ORDER_KEY, BIG_ORDER, 1, 16, 40, 0, "", null, G_BIG, 0, new TradeOfferSC.Ingredient[]{
                TradeOfferSC.Ingredient.of(transistor, 32), TradeOfferSC.Ingredient.of(controller, 4), TradeOfferSC.Ingredient.of(coils, 4),
                TradeOfferSC.Ingredient.of(battery, 1)}));
        // Ф6 (ПО-27): the Ambassador's - the expedition's supplies (ПМ-7: nothing of it grows in the Substrate), the capacitors, the dispatch
        List<Contract> e = new ArrayList<Contract>();
        e.add(new Contract(EMB_SUPPLY_BREAD, DELIVER, 64, 6, 15, 0, "", new ItemStack(Items.bread)));
        e.add(new Contract(EMB_SUPPLY_LOGS, DELIVER, 48, 6, 15, 0, "logWood"));
        e.add(new Contract(EMB_SUPPLY_IRON, DELIVER, 24, 6, 15, 0, "ingotIron"));
        e.add(l.get(1));                                        // deliverTantalumCaps: one contract on both boards
        e.add(new Contract(EMB_DISPATCH, DISPATCH, 1, 10, 30, 0, ""));
        embassyPool = Collections.unmodifiableList(e);
        pool = Collections.unmodifiableList(l);
        return pool;
    }

    private static Contract local(String key, int target, int chips, int rep, String mob, int tag) {
        return new Contract(key, KILL, target, chips, rep, 0, mob, null, G_LOCAL, tag, null);
    }

    private static Contract order(String key, int target, int chips, int rep, ItemStack what) {
        return new Contract(key, DELIVER, target, chips, rep, 0, "", what, G_ORDER, 0, null);
    }

    /** ПД-1: the Substrate pool's contracts of group `g` (in the pool's order). */
    public static List<Contract> group(int g) {
        List<Contract> out = new ArrayList<Contract>();
        for (Contract c : pool()) {
            if (c.group == g) {
                out.add(c);
            }
        }
        return out;
    }

    public static Contract byKey(String key) {
        if (ANCHOR_KEY.equals(key)) {
            return ANCHOR_CONTRACT;
        }
        for (Contract c : pool()) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        for (Contract c : embassyPool()) {                     // Ф6: the Ambassador's own (deliverTantalumCaps is found above)
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    /** Ф6 (ПО-27): a contract only an embassy gives (the supplies, the dispatch) - a Substrate Elder never puts it on a board. */
    public static boolean embassyOnly(String key) {
        return EMB_SUPPLY_BREAD.equals(key) || EMB_SUPPLY_LOGS.equals(key) || EMB_SUPPLY_IRON.equals(key) || EMB_DISPATCH.equals(key);
    }

    /** Ф6 (ПО-27): a dispatch taken on game day `takenDay` is too late to hand in on day `day` (DISPATCH_DAYS dawns have passed). Pure. */
    public static boolean dispatchLate(long takenDay, long day) {
        return day - takenDay > DISPATCH_DAYS;
    }

    /**
     * Ф6: the level an Elder's contracts are taken from - a Substrate Elder's «Знакомый» (ПО-8); the Ambassador's «Чужак»: an embassy
     * is a newcomer's way in, its contracts are his road to «Знакомый» (proposal §8: +65..95 a day). The trade screen asks the same.
     */
    public static int takeLevel(boolean embassy) {
        return embassy ? ReputationSC.STRANGER : ReputationSC.ACQUAINTANCE;
    }

    /**
     * The common part of the board for `player` on game day `day`: BOARD keys of the pool's common contracts (the same wherever asked
     * that day; pure). ПД-1: a Substrate Elder's whole board is substrateBoard.
     */
    public static List<String> board(UUID player, long day) {
        List<Contract> common = group(G_GENERAL);
        return shuffled(common, player, day, 0L, BOARD);
    }

    /**
     * ПД-1 (pure): a Substrate Elder's board for `player` on game day `day` - BOARD common ones (shuffled by the player and the day as
     * always; «Срочная депеша» only if `urgentOk`: a destination exists from here - else the next one in the same order takes its place,
     * so the rest of the common part is the same at every Elder), then one local contract of those fitting the
     * settlement's `tags` (salted by the tags, not the settlement: the same where the tags are the same; none fits - no line), one of the
     * Tuners' orders (its own salt) and the Big order if `bigOn` (not taken this week, or taken today).
     */
    public static List<String> substrateBoard(UUID player, long day, int tags, boolean urgentOk, boolean bigOn) {
        List<Contract> common = new ArrayList<Contract>(), locals = new ArrayList<Contract>(), orders = new ArrayList<Contract>();
        Contract big = null;
        String urgent = null;
        for (Contract c : pool()) {
            if (c.group == G_GENERAL) {
                common.add(c);
                urgent = c.goal == URGENT ? c.key : urgent;
            } else if (c.group == G_LOCAL && (c.localTag == 0 || (c.localTag & tags) != 0)) {
                locals.add(c);
            } else if (c.group == G_ORDER) {
                orders.add(c);
            } else if (c.group == G_BIG) {
                big = c;
            }
        }
        // all the common ones shuffled (the same order at every Elder), the first BOARD taken - «Срочная депеша» skipped where it can't go:
        // the others stay the same everywhere
        List<String> out = new ArrayList<String>();
        for (String k : shuffled(common, player, day, 0L, common.size())) {
            if (out.size() < BOARD && (urgentOk || !k.equals(urgent))) {
                out.add(k);
            }
        }
        out.addAll(shuffled(locals, player, day, LOCAL_SALT ^ ((long) tags << 40), 1));
        out.addAll(shuffled(orders, player, day, ORDER_SALT, 1));
        if (bigOn && big != null) {
            out.add(big.key);
        }
        return out;
    }

    /** ПД-1 (pure): a kill counts for STORM_KILL - an armoured hostile finished off while an EMP storm is on. */
    public static boolean stormCounts(boolean armoured, boolean storm) {
        return armoured && storm;
    }

    /** ПД-1 (pure): «Срочная депеша» due by world time `by` is too late at world time `now`. */
    public static boolean urgentLate(long by, long now) {
        return now > by;
    }

    /** ПД-1 (pure): «Срочная депеша» can be taken at world time `worldTime` (from dawn until URGENT_TAKE_UNTIL). */
    public static boolean urgentTakeable(long worldTime) {
        return Math.max(0L, worldTime) % 24000L < URGENT_TAKE_UNTIL;
    }

    /** ПД-1 (pure): the deadline of a dispatch taken on game day `day` - that day's sunset (world time). */
    public static long urgentBy(long day) {
        return day * 24000L + URGENT_SUNSET;
    }

    /** Ф6 (ПО-27): the Ambassador's board for `player` on game day `day`: BOARD keys of embassyPool (the same at every embassy that day; pure). */
    public static List<String> embassyBoard(UUID player, long day) {
        return shuffled(embassyPool(), player, day, EMBASSY_SALT);
    }

    /** Ф6: the board of an embassy's Elder (`embassy`) or a Substrate one. */
    public static List<String> board(UUID player, long day, boolean embassy) {
        return embassy ? embassyBoard(player, day) : board(player, day);
    }

    /** BOARD keys of `p` shuffled by the player and the day (`salt` 0: the Substrate's board as it always was). */
    private static List<String> shuffled(List<Contract> p, UUID player, long day, long salt) {
        return shuffled(p, player, day, salt, BOARD);
    }

    /** The first `n` keys of `p` shuffled by the player, the day and `salt`. */
    private static List<String> shuffled(List<Contract> p, UUID player, long day, long salt, int n) {
        List<String> keys = new ArrayList<String>(p.size());
        for (Contract c : p) {
            keys.add(c.key);
        }
        long seed = day * 0x9E3779B97F4A7C15L;
        if (player != null) {
            seed ^= player.getMostSignificantBits() * 31L ^ player.getLeastSignificantBits();
        }
        seed ^= salt;
        Random r = new Random(seed);                    // java.util.Random's sequence is fixed by its spec: the same on every machine
        for (int i = keys.size() - 1; i > 0; i--) {
            Collections.swap(keys, i, r.nextInt(i + 1));
        }
        return keys.size() > n ? new ArrayList<String>(keys.subList(0, n)) : keys;
    }
}
