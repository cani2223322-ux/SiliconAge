package com.sc.block;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Transformer item: one variant per tier pair. */
public class ItemBlockTransformerSC extends ItemBlock {

    public ItemBlockTransformerSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockTransformerSC.lowTierFor(stack.getItemDamage()).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        Tier low = BlockTransformerSC.lowTierFor(stack.getItemDamage());
        Tier high = BlockTransformerSC.highTierFor(stack.getItemDamage());
        list.add(Lang.tr("sc.transformer.tooltip.faces", high.name(), high.getVoltage(), low.name(), low.getVoltage()));
        list.add(Lang.tr("sc.transformer.tooltip.use"));
    }
}
