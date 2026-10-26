package com.sc.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * ПД-7 (docs/plan-ideas-2026-10-26.md): the Relay as an item (meta BlockRelaySC.RELAY; the MAST only by /give). A name per meta
 * (tile.siliconage.relay.<name>.name); ПД-7's tooltip: sc.tooltip.relay.<name>, Shift .details, Ctrl .howto (TooltipSC).
 */
public class ItemBlockRelaySC extends ItemBlock {

    public ItemBlockRelaySC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockRelaySC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockRelaySC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String n = BlockRelaySC.nameOf(stack.getItemDamage());
        com.sc.util.TooltipSC.wrap(list, com.sc.manual.Lang.tr("sc.tooltip.relay." + n), "\u00a77");
        if (stack.getItemDamage() == BlockRelaySC.MAST) {
            com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.relay.mast.details"), com.sc.manual.Lang.tr("sc.tooltip.relay.mast.howto"));
            return;
        }
        int g = com.sc.settle.RelayRulesSC.REGION;
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.relay.relay.details", com.sc.settle.RelayRulesSC.HOLD_TICKS / 1200,
                com.sc.settle.RelayRulesSC.WAVES), com.sc.manual.Lang.tr("sc.tooltip.relay.relay.howto",
                com.sc.manual.Lang.tr("sc.settle.rep." + com.sc.settle.ReputationSC.LEVEL_KEYS[com.sc.settle.RelayRulesSC.BUY_LEVEL]), g, g,
                com.sc.settle.RelayRulesSC.SETTLEMENT_GAP));
    }
}
