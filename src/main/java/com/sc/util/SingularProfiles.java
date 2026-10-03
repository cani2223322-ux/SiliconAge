package com.sc.util;

import com.sc.item.ItemArmorSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

/**
 * М4 (docs/plan-singular-armor.md §6): three function profiles of the Singular suit - «Бой / Шахта /
 * Полёт». Each keeps the switch bits (ItemArmorSC "FnToggled" / "FnToggled2") of every worn Singular
 * piece; they live in the Singular chestplate's NBT "SingProfiles": "Act" the active profile (absent:
 * none yet), "P0".."P2" a compound each with "t0".."t3" = {FnToggled, FnToggled2} of that piece (absent:
 * the piece wasn't worn when it was saved - left as it is when applied).
 *
 * Applying never switches on a function that's locked for its piece - above the piece's level or on
 * the side of a branch not chosen (creative: everything open); switching off always goes through.
 * Server-authoritative: the K menu and the "next profile" key send ArmorNetSC.PROFILE_*.
 */
public final class SingularProfiles {

    public static final String NBT = "SingProfiles", ACTIVE = "Act";
    public static final int COUNT = 3;
    /** select(): what happened. */
    public static final int NONE = 0, APPLIED = 1, CAPTURED = 2;

    private SingularProfiles() {
    }

    private static NBTTagCompound root(ItemStack chest, boolean create) {
        if (chest == null) {
            return null;
        }
        if (!chest.hasTagCompound()) {
            if (!create) {
                return null;
            }
            chest.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound t = chest.getTagCompound();
        if (!t.hasKey(NBT)) {
            if (!create) {
                return null;
            }
            t.setTag(NBT, new NBTTagCompound());
        }
        return t.getCompoundTag(NBT);
    }

    /** The profiles live here: a worn Singular chestplate (null: none - no profiles). */
    public static ItemStack holder(ItemStack[] worn) {
        ItemStack chest = worn == null || worn.length <= ArmorGasSC.CHEST ? null : worn[ArmorGasSC.CHEST];
        return SingularLevel.isSingular(chest) ? chest : null;
    }

    /** The active profile 0..2, -1: none chosen yet. */
    public static int active(ItemStack chest) {
        NBTTagCompound r = root(chest, false);
        if (r == null || !r.hasKey(ACTIVE)) {
            return -1;
        }
        int a = r.getInteger(ACTIVE);
        return a >= 0 && a < COUNT ? a : -1;
    }

    public static void setActive(ItemStack chest, int i) {
        NBTTagCompound r = root(chest, i >= 0 && i < COUNT);
        if (r != null) {
            if (i >= 0 && i < COUNT) {
                r.setInteger(ACTIVE, i);
            } else {
                r.removeTag(ACTIVE);
            }
        }
    }

    /** Profile `i` holds something. */
    public static boolean has(ItemStack chest, int i) {
        NBTTagCompound r = root(chest, false);
        return r != null && i >= 0 && i < COUNT && r.hasKey("P" + i);
    }

    /** Pure: the profile after `active` (none: the first). */
    public static int next(int active) {
        return active < 0 || active >= COUNT - 1 ? 0 : active + 1;
    }

    /** Pure: the function is on in a piece whose switch fields are `lo` / `hi`. */
    public static boolean wantsOn(int lo, int hi, ArmorFeature f) {
        int o = f.ordinal();
        boolean bit = o < 32 ? (lo & (1 << o)) != 0 : o < 64 && (hi & (1 << (o & 31))) != 0;
        return f.onByDefault != bit;
    }

    /** The function may be switched on in this piece: open at its level and on its branch's chosen side (creative: always). */
    public static boolean allowed(ArmorFeature f, ItemStack piece, boolean creative) {
        return creative || SingularLevel.unlocked(f, SingularLevel.levelOf(piece), false) && SingularLevel.branchAllowed(f, piece);
    }

    /** Saves the switches of every worn Singular piece into profile `i` (kept in the chestplate). @return false: no Singular chestplate */
    public static boolean capture(ItemStack[] worn, int i) {
        ItemStack chest = holder(worn);
        if (chest == null || i < 0 || i >= COUNT) {
            return false;
        }
        NBTTagCompound prof = new NBTTagCompound();
        for (int t = 0; t < 4 && t < worn.length; t++) {
            ItemStack s = worn[t];
            if (SingularLevel.isSingular(s)) {
                NBTTagCompound tag = s.getTagCompound();
                prof.setIntArray("t" + t, new int[]{tag == null ? 0 : tag.getInteger(ItemArmorSC.TOGGLED),
                        tag == null ? 0 : tag.getInteger(ItemArmorSC.TOGGLED2)});
            }
        }
        root(chest, true).setTag("P" + i, prof);
        return true;
    }

    /**
     * Applies profile `i` to the worn Singular pieces: each function of a stored piece to its saved
     * state, but a locked one is never switched on. @return {switches changed, locked ones left off}, null: nothing to apply
     */
    public static int[] apply(ItemStack[] worn, int i, boolean creative) {
        ItemStack chest = holder(worn);
        if (chest == null || !has(chest, i)) {
            return null;
        }
        NBTTagCompound prof = root(chest, false).getCompoundTag("P" + i);
        int changed = 0, skipped = 0;
        for (int t = 0; t < 4 && t < worn.length; t++) {
            ItemStack s = worn[t];
            int[] bits = prof.getIntArray("t" + t);
            if (!SingularLevel.isSingular(s) || bits.length < 2) {
                continue;
            }
            for (ArmorFeature f : ArmorFeature.values()) {
                if (!f.availableIn(ArmorSuit.SINGULAR, t)) {
                    continue;
                }
                boolean want = wantsOn(bits[0], bits[1], f);
                if (want && !allowed(f, s, creative)) {
                    if (!ItemArmorSC.isEnabled(s, f)) {
                        skipped++;
                    }
                    continue;                                   // locked: not switched on (left as it is)
                }
                if (ItemArmorSC.isEnabled(s, f) != want) {
                    ItemArmorSC.setEnabled(s, f, want);
                    changed++;
                }
            }
        }
        return new int[]{changed, skipped};
    }

    /** Picks profile `i`: applied, or - while it's empty - the current switches saved into it. @return NONE / APPLIED / CAPTURED */
    public static int select(ItemStack[] worn, int i, boolean creative) {
        ItemStack chest = holder(worn);
        if (chest == null || i < 0 || i >= COUNT) {
            return NONE;
        }
        int r;
        if (has(chest, i)) {
            apply(worn, i, creative);
            r = APPLIED;
        } else {
            capture(worn, i);
            r = CAPTURED;
        }
        setActive(chest, i);
        return r;
    }

    // ------------------------------------------------------------------ server (ArmorNetSC)

    private static ChatComponentTranslation name(int i) {
        return new ChatComponentTranslation("sc.armor.sing.profile.name." + i);
    }

    /** Server: PROFILE_SELECT (i), PROFILE_SAVE (into i, -1: the active one), PROFILE_NEXT. */
    public static void serverAction(EntityPlayerMP p, int what, int i) {
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        ItemStack chest = holder(worn);
        if (chest == null) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.profile.nochest"));
            return;
        }
        boolean creative = p.capabilities.isCreativeMode;
        if (what == 2) {
            i = next(active(chest));
            what = 0;
        }
        if (what == 0) {
            if (i < 0 || i >= COUNT) {
                return;
            }
            int[] r = has(chest, i) ? apply(worn, i, creative) : null;
            if (r == null) {
                capture(worn, i);                               // an empty profile takes the current switches
                setActive(chest, i);
                p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.profile.captured", name(i)));
            } else {
                setActive(chest, i);
                p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.profile.on", name(i)));
                if (r[1] > 0) {
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.profile.locked", String.valueOf(r[1])));
                }
            }
        } else {
            int target = i >= 0 && i < COUNT ? i : active(chest);
            if (target < 0) {
                return;
            }
            capture(worn, target);
            setActive(chest, target);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.profile.saved", name(target)));
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    /** The player's active profile (worn Singular chestplate), -1: none. */
    public static int activeOf(EntityPlayer p) {
        return p == null ? -1 : active(holder(ArmorGasSC.wornSet(p)));
    }
}
