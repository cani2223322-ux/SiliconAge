package com.sc.item;

import java.util.List;

import com.sc.bridge.BridgeMathSC;
import com.sc.manual.Lang;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

/**
 * The Armour Link Module (docs/plan-ground-bridge.md §8): the Singular Station's «Связь» tab merges it into the
 * Singular helmet in its helmet slot (taken from the inventory of whoever presses the button); then the helmet
 * is linked to up to BridgeMathSC.MAX_LINKS bridges by «Привязать шлем» in each controller's screen.
 */
public class ItemBridgeLinkModuleSC extends ItemSimpleSC {

    public ItemBridgeLinkModuleSC() {
        super("bridgeLinkModule", "bridgeLinkModule");
        setMaxStackSize(16);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.bridgeLinkModule"));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.tooltip.bridgeLinkModule.details", BridgeMathSC.MAX_LINKS, BridgeMathSC.ARMOUR_DISCOUNT),
                Lang.tr("sc.tooltip.bridgeLinkModule.howto"));
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }
}
