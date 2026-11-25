package com.sc.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md): the Data Port as an item (registered by IdeaBlocksSC, registry name "dataPort"). Its tooltip:
 * sc.tooltip.dataPort, Shift .details, Ctrl .howto (TooltipSC).
 */
public class ItemBlockDataPortSC extends ItemBlock {

    public ItemBlockDataPortSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        com.sc.util.TooltipSC.wrap(list, com.sc.manual.Lang.tr("sc.tooltip.dataPort"), "§7");
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.dataPort.details"), com.sc.manual.Lang.tr("sc.tooltip.dataPort.howto"));
    }
}
