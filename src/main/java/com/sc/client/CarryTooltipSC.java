package com.sc.client;

import com.sc.Reference;
import com.sc.init.ModRecipesCrafting;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * РЦ-7: under Shift, every Silicon Age item made by a carry craft (wireless, quarries, reactors,
 * storage / tank upgrades, the Singular station, the bridge's capacitor and remotes, batteries)
 * says that the ingredients' charge and contents come along. One handler instead of a line in each item.
 */
public class CarryTooltipSC {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new CarryTooltipSC());
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.itemStack;
        if (s == null || s.getItem() == null || !TooltipSC.shift()) {
            return;
        }
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(s.getItem());
        if (id != null && Reference.MODID.equals(id.modId) && ModRecipesCrafting.carriesContents(s)) {
            TooltipSC.wrap(e.toolTip, Lang.tr("sc.tooltip.carry"), "\u00a77");
        }
    }
}
