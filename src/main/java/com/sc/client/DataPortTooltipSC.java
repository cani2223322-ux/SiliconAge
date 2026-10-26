package com.sc.client;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md): the Data Port's tooltip - the block is registered with the default ItemBlock (IdeaBlocksSC), so
 * its lines come from here: sc.tooltip.dataPort, Shift .details, Ctrl .howto (TooltipSC). Registered once by BlockDataPortSC, client only.
 */
public class DataPortTooltipSC {

    private static boolean registered;

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new DataPortTooltipSC());
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.itemStack;
        if (s == null || com.sc.init.IdeaBlocksSC.dataPort == null || s.getItem() != Item.getItemFromBlock(com.sc.init.IdeaBlocksSC.dataPort)) {
            return;
        }
        TooltipSC.wrap(e.toolTip, Lang.tr("sc.tooltip.dataPort"), "§7");
        TooltipSC.more(e.toolTip, Lang.tr("sc.tooltip.dataPort.details"), Lang.tr("sc.tooltip.dataPort.howto"));
    }
}
