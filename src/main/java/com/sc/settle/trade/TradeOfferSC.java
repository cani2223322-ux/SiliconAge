package com.sc.settle.trade;

import java.util.ArrayList;
import java.util.List;

import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.MobItemsSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.util.ArmorSuit;

import net.minecraft.item.Item;
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
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §6.8, rule ПО-13): the services - OUT_CHARGE (the Power Engineer charges a piece of
 * the player's own armour to 100%) and OUT_RESTORE (the Armourer makes a trophy the real item again). A service's first
 * side is a matcher - «any matching» thing of the player's (Ingredient.piece: a piece of one suit short of a full charge;
 * Ingredient.trophies: a trophy of some metas) - which is never taken but worked on in its slot: the one in the hand,
 * else the first in the inventory (TradeServicesSC.slot). Its other sides (chips, components: up to MAX_SERVICE_SIDES in
 * all) are paid as a deal's. Several deals may share one limit (`counter`: the three charges, 4 a day for all of them).
 */
public final class TradeOfferSC {

    /**
     * What comes out: an item, «Доверие» (the Elder's penance, a Stolen Cargo back), a Settlement Map made for this
     * settlement; Ф3's services: the player's own piece charged (OUT_CHARGE), the player's trophy restored (OUT_RESTORE).
     * Appended only.
     */
    public static final int OUT_ITEM = 0, OUT_REPUTATION = 1, OUT_MAP = 2, OUT_CHARGE = 3, OUT_RESTORE = 4;
    /** Sides in: a deal 1..2 (the screen's row shows them all), a service 2..4 (the matcher and the chips in the row, all in the details). */
    public static final int MAX_SIDES = 2, MAX_SERVICE_SIDES = 4;
    /** `limit` of a deal with no limit of its own (the Armourer's restoring, TODO(balance)): the screen says «без лимита», nothing is counted. */
    public static final int UNLIMITED = 1000000;

    /** One side of a deal. */
    public static final class Ingredient {
        /** An exact item (meta strict), or null for an ore name / a matcher. */
        public final ItemStack stack;
        /** An ore dictionary name, or null. */
        public final String ore;
        public final int count;
        /** The stack's NBT must match too (a service's input, Ф3). */
        public final boolean nbt;
        /** Ф3 matcher «a piece of this suit's armour, its charge short of full» (OUT_CHARGE's side), or null. */
        public final ArmorSuit suit;
        /** Ф3 matcher «a trophy of one of these metas» (OUT_RESTORE's side, the Salvager's Nano pieces), or null. */
        private final int[] metas;

        private Ingredient(ItemStack stack, String ore, int count, boolean nbt, ArmorSuit suit, int[] metas) {
            this.stack = stack;
            this.ore = ore;
            this.count = Math.max(1, count);
            this.nbt = nbt;
            this.suit = suit;
            this.metas = metas;
        }

        public static Ingredient of(ItemStack stack, int count) {
            return new Ingredient(stack, null, count, false, null, null);
        }

        public static Ingredient ore(String name, int count) {
            return new Ingredient(null, name, count, false, null, null);
        }

        /** `count` Credit Chips. */
        public static Ingredient chips(int count) {
            return new Ingredient(SettlementItemsSC.chips(1), null, count, false, null, null);
        }

        /** Ф3: one piece of `suit`'s armour that isn't fully charged (never a Singular one: the Power Engineer won't - null then). */
        public static Ingredient piece(ArmorSuit suit) {
            return suit == null || suit == ArmorSuit.SINGULAR ? null : new Ingredient(null, null, 1, false, suit, null);
        }

        /** Ф3: one trophy (MobItemsSC.trophy) of any of `metas` (TrophyTableSC). */
        public static Ingredient trophies(int... metas) {
            return new Ingredient(null, null, 1, false, null, metas == null ? new int[0] : metas.clone());
        }

        public boolean isChips() {
            return stack != null && SettlementItemsSC.creditChip != null && stack.getItem() == SettlementItemsSC.creditChip;
        }

        /** A matcher («any matching»: a piece to charge, a trophy) rather than one exact thing or an ore name. */
        public boolean any() {
            return suit != null || metas != null;
        }

        /** A trophy matcher's metas (a copy; empty for any other side). */
        public int[] metas() {
            return metas == null ? new int[0] : metas.clone();
        }

        /** `s` is this side's thing (whatever its count). */
        public boolean matches(ItemStack s) {
            if (s == null || s.getItem() == null) {
                return false;
            }
            if (suit != null) {
                return s.getItem() instanceof ItemArmorSC && ((ItemArmorSC) s.getItem()).getSuit() == suit
                        && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s);
            }
            if (metas != null) {
                if (MobItemsSC.trophy == null || s.getItem() != MobItemsSC.trophy) {
                    return false;
                }
                for (int m : metas) {
                    if (m == s.getItemDamage()) {
                        return true;
                    }
                }
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
            return takeFrom(inv, n, -1);
        }

        /**
         * The same, slot `first` first (the player's hand - the Salvager takes the held trophy, not the first one), then
         * the rest in order; -1: in order.
         */
        public int takeFrom(ItemStack[] inv, int n, int first) {
            int left = n;
            for (int i : takePlan(inv, n, first)) {           // ПС-4: in takePlan's order
                left -= takeAt(inv, i, left);
            }
            return n - left;
        }

        /**
         * ПС-4 (audit 2026-10-20), pure: the slots takeFrom would take `n` from, in its order - slot `first` (if it matches), then,
         * for an ore-dictionary side, vanilla's items before other mods' (oak and birch logs before another mod's precious wood),
         * else in order; as many slots as cover `n`. The screen frames them.
         */
        public int[] takePlan(ItemStack[] inv, int n, int first) {
            List<Integer> plan = new java.util.ArrayList<Integer>();
            int left = n;
            if (inv == null) {
                return new int[0];
            }
            if (first >= 0 && first < inv.length && usable(inv[first]) && left > 0) {
                plan.add(Integer.valueOf(first));
                left -= inv[first].stackSize;
            }
            for (int pass = ore != null ? 0 : 1; pass < 2 && left > 0; pass++) {
                for (int i = 0; i < inv.length && left > 0; i++) {
                    if (i == first || !usable(inv[i]) || ore != null && (pass == 0) != vanilla(inv[i])) {
                        continue;
                    }
                    plan.add(Integer.valueOf(i));
                    left -= inv[i].stackSize;
                }
            }
            int[] out = new int[plan.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = plan.get(i).intValue();
            }
            return out;
        }

        private boolean usable(ItemStack s) {
            return s != null && s.stackSize > 0 && matches(s);
        }

        /** ПС-4: a vanilla item (its registry name's mod "minecraft"). */
        private static boolean vanilla(ItemStack s) {
            try {
                cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier id = cpw.mods.fml.common.registry.GameRegistry.findUniqueIdentifierFor(s.getItem());
                return id != null && "minecraft".equals(id.modId);
            } catch (RuntimeException e) {
                return false;
            }
        }

        private int takeAt(ItemStack[] inv, int i, int want) {
            ItemStack s = inv[i];
            if (want <= 0 || s == null || s.stackSize <= 0 || !matches(s)) {
                return 0;
            }
            int t = Math.min(want, s.stackSize);
            s.stackSize -= t;
            if (s.stackSize <= 0) {
                inv[i] = null;
            }
            return t;
        }

        /**
         * A stack to show / give: the item, or the ore name's first stack (null if none is registered); a matcher - its
         * first choice (choices()).
         */
        public ItemStack display() {
            ItemStack s = stack;
            if (any()) {
                List<ItemStack> c = choices();
                s = c.isEmpty() ? null : c.get(0);
            }
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

        /**
         * A matcher's things, one of each (the screen cycles through them: «any matching»): the suit's four pieces
         * (empty, so they match), the trophies of its metas. For any other side: its display() alone.
         */
        public List<ItemStack> choices() {
            List<ItemStack> l = new ArrayList<ItemStack>();
            if (suit != null) {
                Item[] pieces = ModItems.ARMOR.get(suit);
                for (int i = 0; pieces != null && i < pieces.length; i++) {
                    if (pieces[i] != null) {
                        l.add(new ItemStack(pieces[i]));
                    }
                }
            } else if (metas != null) {
                for (int m : metas) {
                    ItemStack t = MobItemsSC.trophyStack(m);
                    if (t != null) {
                        l.add(t);
                    }
                }
            } else {
                ItemStack d = display();
                if (d != null) {
                    l.add(d);
                }
            }
            return l;
        }
    }

    public final String key;
    public final NpcKindSC kind;
    /** What the player gives: 1 or 2 sides (a service: its matcher first, then up to three more). */
    public final Ingredient[] in;
    /** OUT_ITEM: what the player gets; OUT_REPUTATION / OUT_MAP / the services: null. */
    public final Ingredient out;
    public final int outKind;
    /** OUT_REPUTATION: how much. */
    public final int outAmount;
    /** The lowest ReputationSC level it is offered at. */
    public final int minLevel;
    /** Times a game day per player at one settlement; a `global` one: per period over every settlement. UNLIMITED: none. */
    public final int limit;
    public final boolean global;
    /** A global deal's period: 1 (a day) or 7 (a week). */
    public final int periodDays;
    /** The limit's counter (SettlementRegistrySC's counts): the key, or one shared by several deals (the three charges). */
    public final String counter;

    public TradeOfferSC(String key, NpcKindSC kind, Ingredient[] in, Ingredient out, int outKind, int outAmount, int minLevel, int limit,
                        boolean global, int periodDays) {
        this(key, kind, in, out, outKind, outAmount, minLevel, limit, global, periodDays, null);
    }

    /** The same with a shared limit: `counter` (null - the deal's own key). */
    public TradeOfferSC(String key, NpcKindSC kind, Ingredient[] in, Ingredient out, int outKind, int outAmount, int minLevel, int limit,
                        boolean global, int periodDays, String counter) {
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
        this.counter = counter == null || counter.isEmpty() ? key : counter;
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

    /** Ф3: a service (OUT_CHARGE, OUT_RESTORE) - in[0] is the player's own thing it works on, in its slot. */
    public boolean service() {
        return outKind == OUT_CHARGE || outKind == OUT_RESTORE;
    }

    /** No limit of its own (UNLIMITED): not counted, the screen says so. */
    public boolean unlimited() {
        return limit >= UNLIMITED;
    }

    /**
     * What the screen shows on the right of the arrow (and the self-test checks exists): the output stack - the item
     * (`out.count` of it), a blank Settlement Map (the real one is made at the deal: ItemSettlementMapSC.make); null
     * for «Доверие» (the screen draws its own badge with `outAmount`). A service: what its matcher's first choice
     * becomes (TradeServicesSC.result - the screen shows the one that will really be taken instead).
     */
    public ItemStack outStack() {
        if (outKind == OUT_ITEM) {
            return out == null ? null : out.display();
        }
        if (outKind == OUT_MAP) {
            return SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap);
        }
        if (service() && in != null && in.length > 0 && in[0] != null) {
            ItemStack first = in[0].display();
            return first == null ? null : TradeServicesSC.result(this, first);
        }
        return null;
    }

    /** sc.trade.offer.<key>: the deal's own line (the screen's row and the handbook's table). */
    public String langKey() {
        return "sc.trade.offer." + key;
    }

    /**
     * sc.trade.offer.<key>.any: a deal whose first side is a matcher (a service, the Salvager's trophy) - what fits, in
     * words («часть брони Нано не с полным зарядом»): the screen when nothing does, the handbook's table. Null otherwise.
     */
    public String anyLangKey() {
        return in != null && in.length > 0 && in[0] != null && in[0].any() ? langKey() + ".any" : null;
    }
}
