package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8.1): the dock's blocks as items (/give only). A name per meta
 * (tile.siliconage.dock.<name>.name); tooltip sc.tooltip.dock.<name>, Shift .details, Ctrl .howto.
 */
public class ItemBlockDockSC extends ItemBlock {

    public ItemBlockDockSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockDockSC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockDockSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String k = "sc.tooltip.dock." + BlockDockSC.nameOf(stack.getItemDamage());
        list.add("§7" + Lang.tr(k));
        TooltipSC.more(list, Lang.tr(k + ".details"), Lang.tr(k + ".howto"));
    }
}
