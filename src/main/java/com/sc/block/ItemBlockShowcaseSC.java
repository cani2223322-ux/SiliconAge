package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.6): the showcase machines as items (/give only: no tab, hidden in NEI).
 * A name per meta (tile.siliconage.showcase.<name>.name); tooltip sc.tooltip.showcase, Shift .details, Ctrl .howto.
 */
public class ItemBlockShowcaseSC extends ItemBlock {

    public ItemBlockShowcaseSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockShowcaseSC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockShowcaseSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.showcase"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.showcase.details"), Lang.tr("sc.tooltip.showcase.howto"));
    }
}
