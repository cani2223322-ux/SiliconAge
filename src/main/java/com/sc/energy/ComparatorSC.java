package com.sc.energy;

import net.minecraft.tileentity.TileEntity;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md): the comparator's helpers - the coordinator's signatures, ОМ-5's bodies where marked. Pure but
 * for notify.
 */
public final class ComparatorSC {

    private ComparatorSC() {
    }

    /** Pure: `value` of `max` as a comparator level 0..15 - 0 only for nothing, 15 only for full (vanilla's container rule). */
    public static int level(double value, double max) {
        if (!(max > 0D) || !(value > 0D)) {
            return 0;
        }
        if (value >= max) {
            return 15;
        }
        return 1 + (int) Math.floor(value / max * 14D);
    }

    /**
     * Server: the tile's level changed from `last` - its block's comparators are told (World.func_147453_f). @return the level to keep
     * as the new `last`
     */
    public static int notify(TileEntity te, int last) {
        if (te == null || te.getWorldObj() == null || te.getWorldObj().isRemote || !(te instanceof IComparatorSC)) {
            return last;
        }
        int now = ((IComparatorSC) te).comparatorLevel();
        if (now != last) {
            te.getWorldObj().func_147453_f(te.xCoord, te.yCoord, te.zCoord, te.getBlockType());
        }
        return now;
    }
}
