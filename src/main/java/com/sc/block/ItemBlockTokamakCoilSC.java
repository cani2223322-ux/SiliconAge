package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** The tokamak coil as an item: what it's for. */
public class ItemBlockTokamakCoilSC extends ItemBlock {

    public ItemBlockTokamakCoilSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tokamakCoil.tooltip"));
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.tokamakCoil.tooltip.howto"));
    }
}
