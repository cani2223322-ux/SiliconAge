package com.sc.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The Singular blade's forms (docs/plan-singular-tools.md §2.1), switched with Shift + wheel. Kept in the
 * blade's NBT "SingForm" as the ordinal (append only); ItemBladeSC.formOf reads it (default SWORD).
 * `level` - the blade level it opens at; `branch` - the branch it needs as well (the shield-blade: the
 * Guardian), 0 for none.
 */
public enum BladeForm {

    SWORD(1, 0),
    SCYTHE(1, 0),
    SPEAR(1, 0),
    WHIP(2, 0),
    SHIELD(3, ToolLevelSC.BLADE_GUARDIAN),
    SINGULAR(5, 0);

    public static final String NBT = "SingForm";
    public static final BladeForm DEFAULT = SWORD;

    public final int level;
    public final int branch;

    BladeForm(int level, int branch) {
        this.level = level;
        this.branch = branch;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** sc.bladeform.<key> */
    public String langKey() {
        return "sc.bladeform." + key();
    }

    public static BladeForm of(int ordinal) {
        BladeForm[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : DEFAULT;
    }

    /** Pure: open at blade level `level` with branch `chosen` (creative: every branch). */
    public boolean open(int level, int chosen, boolean creative) {
        return level >= this.level && (branch == 0 || creative || chosen == branch);
    }

    /** Open for this player with this blade (its level, its branch; creative: all). */
    public boolean open(EntityPlayer p, ItemStack blade) {
        boolean creative = p != null && p.capabilities.isCreativeMode;
        return open(ToolLevelSC.effectiveLevel(p, blade), ToolLevelSC.branchOf(blade), creative);
    }

    /** Pure: the next open form from `from` going `delta` steps (wrapping; SWORD is always open). */
    public static BladeForm cycle(BladeForm from, int delta, int level, int chosen, boolean creative) {
        BladeForm[] v = values();
        int dir = delta < 0 ? -1 : 1;
        int i = (from == null ? DEFAULT : from).ordinal();
        for (int n = 0; n < v.length; n++) {
            i = ((i + dir) % v.length + v.length) % v.length;
            if (v[i].open(level, chosen, creative)) {
                return v[i];
            }
        }
        return DEFAULT;
    }
}
