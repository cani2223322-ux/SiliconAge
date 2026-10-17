package com.sc.settle.trade;

import com.sc.item.ItemArmorSC;
import com.sc.mob.TrophyRestoreSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE T (docs/plan-substrate-tech-f3.md §6.8, rule ПО-13): the services of the «Терминал обмена» - what they work on
 * and what it becomes. A service (TradeOfferSC.service()) takes nothing of its matcher's (in[0]) away: it works on one
 * thing of the player's own in its slot - the one in the hand if it matches, else the first matching one of the main
 * inventory (worn armour isn't asked: what will be taken is the one the screen shows). OUT_CHARGE: the piece charged to
 * its capacity by ItemArmorSC.setCharge, its other NBT (functions, chips, gases, the scheme) untouched; OUT_RESTORE: the
 * trophy replaced by TrophyRestoreSC.restored - a fresh real item, the trophy's NBT never copied.
 * <p>
 * slot / result are pure but for the stacks they read: the screen asks them of the client's inventory, the server of its
 * own (TradeServerSC: slot before the payment, apply after it). Package T's.
 */
public final class TradeServicesSC {

    private TradeServicesSC() {
    }

    /**
     * The slot of `inv` (a player's main inventory; `held` - the current hotbar slot) whose thing service `o` works on:
     * the held one if it fits, else the first one that does; -1 none (or `o` no service).
     */
    public static int slot(ItemStack[] inv, int held, TradeOfferSC o) {
        if (inv == null || o == null || !o.service() || o.in == null || o.in.length == 0 || o.in[0] == null) {
            return -1;
        }
        if (held >= 0 && held < inv.length && fits(inv[held], o)) {
            return held;
        }
        for (int i = 0; i < inv.length; i++) {
            if (fits(inv[i], o)) {
                return i;
            }
        }
        return -1;
    }

    /** The same for `p`'s inventory (its current item first). */
    public static int slot(EntityPlayer p, TradeOfferSC o) {
        return p == null || p.inventory == null ? -1 : slot(p.inventory.mainInventory, p.inventory.currentItem, o);
    }

    /** `s` is a thing service `o` takes: its matcher's, and (a restoring) a trophy the Armourer restores (TrophyRestoreSC.refusal). */
    public static boolean fits(ItemStack s, TradeOfferSC o) {
        if (s == null || s.getItem() == null || s.stackSize <= 0 || o == null || !o.service() || !o.in[0].matches(s)) {
            return false;
        }
        return o.outKind != TradeOfferSC.OUT_RESTORE || TrophyRestoreSC.refusal(s.getItemDamage(), TrophyRestoreSC.WHERE_ARMOURER) == TrophyRestoreSC.OK;
    }

    /**
     * What `s` becomes by service `o` (a new stack; `s` itself untouched): OUT_CHARGE - a copy at its full charge;
     * OUT_RESTORE - TrophyRestoreSC.restored of its meta. Null if `o` is no service or `s` nothing.
     */
    public static ItemStack result(TradeOfferSC o, ItemStack s) {
        if (o == null || s == null || s.getItem() == null) {
            return null;
        }
        if (o.outKind == TradeOfferSC.OUT_CHARGE) {
            if (!(s.getItem() instanceof ItemArmorSC)) {
                return null;
            }
            ItemStack c = s.copy();
            c.stackSize = 1;
            ItemArmorSC.setCharge(c, ItemArmorSC.capacityOf(c));
            return c;
        }
        return o.outKind == TradeOfferSC.OUT_RESTORE ? TrophyRestoreSC.restored(s.getItemDamage()) : null;
    }

    /**
     * Server (TradeServerSC.perform, after the payment): does service `o` on slot `slot` of `p`'s main inventory - the
     * piece charged in place, the trophy replaced in its slot. @return done (false: the slot no longer fits - never after
     * slot() said it does, the payment taking no matcher's thing)
     */
    static boolean apply(EntityPlayer p, TradeOfferSC o, int slot) {
        ItemStack[] inv = p.inventory.mainInventory;
        if (slot < 0 || slot >= inv.length || !fits(inv[slot], o)) {
            return false;
        }
        ItemStack s = inv[slot];
        if (o.outKind == TradeOfferSC.OUT_CHARGE) {
            ItemArmorSC.setCharge(s, ItemArmorSC.capacityOf(s));
            return true;
        }
        ItemStack out = result(o, s);
        if (out == null) {
            return false;
        }
        inv[slot] = out;                                       // a trophy stacks to one: the slot held just it
        return true;
    }
}
