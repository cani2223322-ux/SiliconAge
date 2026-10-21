package com.sc.block;

import java.util.List;

import com.sc.conduit.ConduitKind;
import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class ItemBlockTubeSC extends ItemBlockConduitSC {

    public ItemBlockTubeSC(Block block) {
        super(block);
    }

    @Override
    protected ConduitKind kind() {
        return ConduitKind.TUBE;
    }

    /** СТ-1 (audit 2026-10-20): what it carries, who gets it first, what goes into a connector, how one is set. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("\u00a77" + Lang.tr("sc.tube.tooltip"));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.tube.tooltip.details"), Lang.tr("sc.tube.tooltip.howto"));
    }
}
