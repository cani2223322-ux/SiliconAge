package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** The gravity coil as an item: what it's for (TooltipSC: Shift - details, Ctrl - how to use). */
public class ItemBlockGravityCoilSC extends ItemBlock {

    public ItemBlockGravityCoilSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.gravityCoil.tooltip"));
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.gravityCoil.tooltip.more"), Lang.tr("sc.gravityCoil.tooltip.howto"));
    }
}
