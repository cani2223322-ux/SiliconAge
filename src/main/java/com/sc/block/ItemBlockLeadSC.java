package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.radiation.RadiationSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** The lead block and lead glass as items: how much radiation they let through. */
public class ItemBlockLeadSC extends ItemBlock {

    public ItemBlockLeadSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        boolean glass = field_150939_a instanceof BlockLeadSC.Glass;
        float through = glass ? RadiationSC.THROUGH_LEAD_GLASS : RadiationSC.THROUGH_LEAD;
        list.add("§7" + Lang.tr(glass ? "sc.lead.tooltip.glass" : "sc.lead.tooltip.block", Math.round(through * 100)));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.lead.tooltip.more"), null);
    }
}
