package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.1): the core as an item (only /give and NEI's cheat mode reach it - it is
 * in no tab and hidden in NEI). Tooltip: sc.tooltip.settlementCore, Shift .details, Ctrl .howto (package D's text).
 * Placed by hand it is a core of no settlement (id -1): its field still protects and pushes, but no roster.
 */
public class ItemBlockSettlementCoreSC extends ItemBlock {

    public ItemBlockSettlementCoreSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.settlementCore"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.settlementCore.details"), Lang.tr("sc.tooltip.settlementCore.howto"));
    }
}
