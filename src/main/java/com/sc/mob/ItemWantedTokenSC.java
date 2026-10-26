package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск», ★ A): «Жетон розыска» - every player who dealt WantedRulesSC.REWARD_SHARE of the damage
 * to the week's wanted Renegade gets one (with the chips and the «Доверие»); WantedRulesSC.TOKENS_PER_RESTORE of them pay a settlement's
 * Armourer for a trophy's restoring instead of his Credit Chips (the components are still paid). Never crafted. Stack 16.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "wantedToken" (IdeaItemsSC), the texture items/wantedToken. ПД-4's: the tooltip
 * (TooltipSC: sc.tooltip.wantedToken, Shift .details, Ctrl .howto), the texture (tools/gen_wanted_tex.py), NEI «how to get».
 */
public class ItemWantedTokenSC extends Item {

    public ItemWantedTokenSC() {
        setMaxStackSize(16);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".wantedToken");
        setTextureName(Reference.ASSETS + ":wantedToken");
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        com.sc.util.TooltipSC.wrap(list, com.sc.manual.Lang.tr("sc.tooltip.wantedToken"), "\u00a77");
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.wantedToken.details", Math.round(WantedRulesSC.REWARD_SHARE * 100F)),
                com.sc.manual.Lang.tr("sc.tooltip.wantedToken.howto", WantedRulesSC.TOKENS_PER_RESTORE));
    }
}
