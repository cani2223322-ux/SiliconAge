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
 */
public final class ContractTableSC {

    public static final int KILL = 0, DELIVER = 1, SUPPRESS_KILL = 2, CARGO = 3, ANCHOR = 4;
    /** Contracts on the board a day. */
    public static final int BOARD = 3;
    /** The contracts' keys (stable: ContractRec.key, lang sc.contract.<key> and .desc). */
    public static final String KILL_MARAUDERS = "killMarauders", DELIVER_TANTALUM_CAPS = "deliverTantalumCaps",
            SUPPRESS_KILL_KEY = "suppressKill", RETURN_CARGO = "returnCargo", ANCHOR_KEY = "anchor";

    private static volatile List<Contract> pool;

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

    /** Every contract (not the one-time ANCHOR: the Elder offers it on its own). Empty before the items exist. */
    public static List<Contract> pool() {
        List<Contract> p = pool;
        return p != null ? p : build();
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
        return null;
    }

    /** The board for `player` on game day `day`: BOARD keys of the pool (the same wherever asked that day; pure). */
    public static List<String> board(UUID player, long day) {
        List<Contract> p = pool();
        List<String> keys = new ArrayList<String>(p.size());
        for (Contract c : p) {
            keys.add(c.key);
        }
        long seed = day * 0x9E3779B97F4A7C15L;
        if (player != null) {
            seed ^= player.getMostSignificantBits() * 31L ^ player.getLeastSignificantBits();
        }
        Random r = new Random(seed);                    // java.util.Random's sequence is fixed by its spec: the same on every machine
        for (int i = keys.size() - 1; i > 0; i--) {
            Collections.swap(keys, i, r.nextInt(i + 1));
        }
        return keys.size() > BOARD ? new ArrayList<String>(keys.subList(0, BOARD)) : keys;
    }
}
