package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Blocks with contents (machines, generators, storages, quarries, wireless blocks, the shower,
 * tanks): broken only with a pickaxe - by hand they don't break at all, so nothing inside is lost
 * to a stray punch. Creative breaks them as usual.
 */
public final class PickaxeOnlySC {

    /** The tooltip line of every such block's item. */
    public static final String TOOLTIP_KEY = "sc.tooltip.pickaxeOnly";

    private PickaxeOnlySC() {
    }

    /** A creative player, or one holding anything that works as a pickaxe (any level). */
    public static boolean canBreak(EntityPlayer player) {
        if (player == null || player.capabilities.isCreativeMode) {
            return true;
        }
        ItemStack held = player.getCurrentEquippedItem();
        return held != null && held.getItem() != null && held.getItem().getHarvestLevel(held, "pickaxe") >= 0;
    }

    /** getPlayerRelativeBlockHardness: the usual speed with a pickaxe, none without. */
    public static float hardness(float normal, EntityPlayer player) {
        return canBreak(player) ? normal : 0F;
    }

    public static void tooltip(List list) {
        list.add("§8" + Lang.tr(TOOLTIP_KEY));
    }
}
