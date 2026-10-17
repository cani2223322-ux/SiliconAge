package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE S (docs/plan-substrate-tech-f3.md §6.3): the second showcase block as items (/give only: no tab, hidden in NEI). A
 * name per meta (tile.siliconage.showcase2.<name>.name); the tooltip is the first block's (sc.tooltip.showcase, Shift
 * .details, Ctrl .howto) - the same thing.
 */
public class ItemBlockShowcase2SC extends ItemBlock {

    public ItemBlockShowcase2SC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockShowcase2SC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockShowcase2SC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.showcase"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.showcase.details"), Lang.tr("sc.tooltip.showcase.howto"));
    }
}
