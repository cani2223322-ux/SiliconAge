package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.SingularStationMath;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** The Gravitational Stabiliser as an item: what it gives (Shift), where to put it (Ctrl). */
public class ItemBlockGravStabiliserSC extends ItemBlock {

    public ItemBlockGravStabiliserSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.gravStabiliser.tooltip"));
        com.sc.util.TooltipSC.more(list,
                Lang.tr("sc.gravStabiliser.details", SingularStationMath.STABILISER_PERCENT, SingularStationMath.MAX_STABILISERS),
                Lang.tr("sc.gravStabiliser.howto", TileEntitySingularStationSC.STAB_RADIUS, TileEntitySingularStationSC.STAB_DY));
    }
}
