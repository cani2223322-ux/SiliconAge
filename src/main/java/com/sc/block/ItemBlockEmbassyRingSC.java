package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.4): the Great Ring's core as an item (/give only: no tab, hidden in NEI). Its tooltip
 * sc.tooltip.embassyRing (Shift .details, Ctrl .howto - lang package D). Fixed (the coordinator's).
 */
public class ItemBlockEmbassyRingSC extends ItemBlock {

    public ItemBlockEmbassyRingSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.embassyRing"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.embassyRing.details"), Lang.tr("sc.tooltip.embassyRing.howto"));
    }
}
