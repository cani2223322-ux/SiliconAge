package com.sc.settle.trade;

import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementItemsSC;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.3, rules ПО-1, ПО-3): one deal of a profession - up to two things in
 * (what the player gives), one out. Unlike vanilla's MerchantRecipe (Item and count only - six copper ingots would pay
 * for six germanium ones) a side matches the item AND its meta strictly (OreDictionary.itemMatches(.., .., true)), the
 * NBT too where `nbt` says so, or any stack of an ore name (UniDict in the pack makes one metal of every name - by
 * name the deal still takes it). Credit Chips are an ingredient like any other (CHIPS).
 * <p>
 * Who pays chips: `buy()` deals (the player gives chips - closed to a «Нарушитель», ReputationSC.buys), `sell()` deals
 * (the player gets chips - closed to an «Враг» only; under the global 64-a-day cap, TradeRulesSC). The player's side is
 * what the level's multiplier and the settlement's ±10% scale (TradeRulesSC.playerSide). `key` is the deal's stable
 * id: the limits' counters (SettlementRegistrySC) and the lang (sc.trade.offer.<key>) go by it - never rename one.
 * Data, built by TradeTableSC; package T owns the file.
 */
public final class TradeOfferSC {

    /** What comes out: an item, «Доверие» (the Elder's penance, a Stolen Cargo back), a Settlement Map made for this settlement. */
    public static final int OUT_ITEM = 0, OUT_REPUTATION = 1, OUT_MAP = 2;

    /** One side of a deal. */
    public static final class Ingredient {
        /** An exact item (meta strict), or null for an ore name. */
        public final ItemStack stack;
        /** An ore dictionary name, or null. */
        public final String ore;
        public final int count;
        /** The stack's NBT must match too (a service's input, Ф3). */
        public final boolean nbt;

        private Ingredient(ItemStack stack, String ore, int count, boolean nbt) {
            this.stack = stack;
            this.ore = ore;
            this.count = Math.max(1, count);
            this.nbt = nbt;
        }

        public static Ingredient of(ItemStack stack, int count) {
            return new Ingredient(stack, null, count, false);
        }

        public static Ingredient ore(String name, int count) {
            return new Ingredient(null, name, count, false);
        }

        /** `count` Credit Chips. */
        public static Ingredient chips(int count) {
            return new Ingredient(SettlementItemsSC.chips(1), null, count, false);
        }

        public boolean isChips() {
            return stack != null && SettlementItemsSC.creditChip != null && stack.getItem() == SettlementItemsSC.creditChip;
        }

        /** `s` is this side's thing (whatever its count). */
        public boolean matches(ItemStack s) {
            if (s == null) {
                return false;
            }
            if (stack != null) {
                return OreDictionary.itemMatches(stack, s, true) && (!nbt || ItemStack.areItemStackTagsEqual(stack, s));
            }
            for (ItemStack o : OreDictionary.getOres(ore)) {
                if (OreDictionary.itemMatches(o, s, false)) {
                    return true;
                }
            }
            return false;
        }

        /** How many of this side's thing `inv` holds (the player's main inventory: both sides call it - the screen too). */
        public int countIn(ItemStack[] inv) {
            int n = 0;
            if (inv != null) {
                for (ItemStack s : inv) {
                    if (s != null && s.stackSize > 0 && matches(s)) {
                        n += s.stackSize;
                    }
                }
            }
            return n;
        }

        /**
         * Server: takes `n` of this side's thing out of `inv` (emptied slots set to null). Call only after countIn said
         * there are as many. @return how many were taken
         */
        public int takeFrom(ItemStack[] inv, int n) {
            int left = n;
            for (int i = 0; i < inv.length && left > 0; i++) {
                ItemStack s = inv[i];
                if (s != null && s.stackSize > 0 && matches(s)) {
                    int t = Math.min(left, s.stackSize);
                    s.stackSize -= t;
                    left -= t;
                    if (s.stackSize <= 0) {
                        inv[i] = null;
                    }
                }
            }
            return n - left;
        }

        /** A stack to show / give: the item, or the ore name's first stack (null if none is registered). */
        public ItemStack display() {
            ItemStack s = stack;
            if (s == null && ore != null && !OreDictionary.getOres(ore).isEmpty()) {
                s = OreDictionary.getOres(ore).get(0);
                if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                    s = new ItemStack(s.getItem(), 1, 0);
                }
            }
            if (s == null) {
                return null;
            }
            ItemStack out = s.copy();
            out.stackSize = count;
            return out;
        }
    }

    public final String key;
    public final NpcKindSC kind;
    /** What the player gives: 1 or 2 sides. */
    public final Ingredient[] in;
    /** OUT_ITEM: what the player gets; OUT_REPUTATION / OUT_MAP: null. */
    public final Ingredient out;
    public final int outKind;
    /** OUT_REPUTATION: how much. */
    public final int outAmount;
    /** The lowest ReputationSC level it is offered at. */
    public final int minLevel;
    /** Times a game day per player at one settlement; a `global` one: per period over every settlement. */
    public final int limit;
    public final boolean global;
    /** A global deal's period: 1 (a day) or 7 (a week). */
    public final int periodDays;

    public TradeOfferSC(String key, NpcKindSC kind, Ingredient[] in, Ingredient out, int outKind, int outAmount, int minLevel, int limit,
                        boolean global, int periodDays) {
        this.key = key;
        this.kind = kind;
        this.in = in;
        this.out = out;
        this.outKind = outKind;
        this.outAmount = outAmount;
        this.minLevel = minLevel;
        this.limit = limit;
        this.global = global;
        this.periodDays = Math.max(1, periodDays);
    }

    /** The player pays chips. */
    public boolean buy() {
        for (Ingredient i : in) {
            if (i != null && i.isChips()) {
                return true;
            }
        }
        return false;
    }

    /** The player gets chips. */
    public boolean sell() {
        return outKind == OUT_ITEM && out != null && out.isChips();
    }

    /**
     * What the screen shows on the right of the arrow (and the self-test checks exists): the output stack - the item
     * (`out.count` of it), a blank Settlement Map (the real one is made at the deal: ItemSettlementMapSC.make); null
     * for «Доверие» (the screen draws its own badge with `outAmount`).
     */
    public ItemStack outStack() {
        if (outKind == OUT_ITEM) {
            return out == null ? null : out.display();
        }
        if (outKind == OUT_MAP) {
            return SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap);
        }
        return null;
    }

    /** sc.trade.offer.<key>: the deal's own line (the screen's row and the handbook's table). */
    public String langKey() {
        return "sc.trade.offer." + key;
    }
}
