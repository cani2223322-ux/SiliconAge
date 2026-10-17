package com.sc.settle.trade;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.settle.ReputationSC;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.2, rule ПО-2): «Кредит-чип» (КЧ) - the settlements' money. Its own
 * item, not a meta of a shared one; never crafted - the Tuners pay it for the player's goods and contracts, the first
 * pass gives 8 (later the Salvager, Ф3). Stack 64. Tooltip sc.tooltip.creditChip, Shift .details (where it comes
 * from: the pass's count), Ctrl .howto (what it buys, the day's cap of chips from Tuners). Texture items/creditChip
 * (tools/gen_trade_tex.py).
 */
public class ItemCreditChipSC extends Item {

    public ItemCreditChipSC() {
        setMaxStackSize(64);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".creditChip");
        setTextureName(Reference.ASSETS + ":creditChip");
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.creditChip"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.creditChip.details", ReputationSC.PASS_CHIPS),
                Lang.tr("sc.tooltip.creditChip.howto", TradeRulesSC.CHIPS_PER_DAY));
    }
}
