package com.sc.settle.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sc.init.ModItems;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.settle.ReputationSC;

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
 */
public final class ContractTableSC {

    public static final int KILL = 0, DELIVER = 1, SUPPRESS_KILL = 2, CARGO = 3, ANCHOR = 4,
            // ---- Ф6 (appended): «Депеша домой» - taken at an embassy, handed in at a Substrate Elder
            DISPATCH = 5;
    /** Contracts on the board a day. */
    public static final int BOARD = 3;
    /** The contracts' keys (stable: ContractRec.key, lang sc.contract.<key> and .desc). */
    public static final String KILL_MARAUDERS = "killMarauders", DELIVER_TANTALUM_CAPS = "deliverTantalumCaps",
            SUPPRESS_KILL_KEY = "suppressKill", RETURN_CARGO = "returnCargo", ANCHOR_KEY = "anchor",
            // ---- Ф6 (appended): the Ambassador's board
            EMB_SUPPLY_BREAD = "embSupplyBread", EMB_SUPPLY_LOGS = "embSupplyLogs", EMB_SUPPLY_IRON = "embSupplyIron", EMB_DISPATCH = "embDispatch";
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

        public Contract(String key, int goal, int target, int chips, int reputation, int modulePct, String what) {
            this(key, goal, target, chips, reputation, modulePct, what, null);
        }

        public Contract(String key, int goal, int target, int chips, int reputation, int modulePct, String what, ItemStack item) {
            this.key = key;
            this.goal = goal;
            this.target = target;
            this.chips = chips;
            this.reputation = reputation;
            this.modulePct = modulePct;
            this.what = what;
            this.item = item;
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
        Item caps = ModItems.component("tantalumCapacitor");
        ItemStack cargo = MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1);
        if (caps == null || cargo == null) {
            return Collections.emptyList();
        }
        List<Contract> l = new ArrayList<Contract>();
        l.add(new Contract(KILL_MARAUDERS, KILL, 10, 8, 20, 0, EntityMarauderSC.NAME));
        l.add(new Contract(DELIVER_TANTALUM_CAPS, DELIVER, 32, 14, 25, 0, "", new ItemStack(caps)));
        l.add(new Contract(SUPPRESS_KILL_KEY, SUPPRESS_KILL, 5, 10, 25, 20, ""));
        l.add(new Contract(RETURN_CARGO, CARGO, 3, 10, 35, 0, "", cargo));
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

    /** The board for `player` on game day `day`: BOARD keys of the pool (the same wherever asked that day; pure). */
    public static List<String> board(UUID player, long day) {
        return shuffled(pool(), player, day, 0L);
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
        return keys.size() > BOARD ? new ArrayList<String>(keys.subList(0, BOARD)) : keys;
    }
}
