package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §2.4): the airfield's surface as items (/give only: no tab, hidden in NEI).
 * A name per meta (tile.siliconage.embassyAirfield.<name>.name); the tooltip: sc.tooltip.embassyAirfield.<name> (one line a meta), Shift
 * sc.tooltip.embassyAirfield.details, Ctrl sc.tooltip.airfield.howto (the airfield's showcases share it). The coordinator's.
 */
public class ItemBlockAirfieldSC extends ItemBlock {

    public ItemBlockAirfieldSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockAirfieldSC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockAirfieldSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.embassyAirfield." + BlockAirfieldSC.nameOf(stack.getItemDamage())));
        TooltipSC.more(list, Lang.tr("sc.tooltip.embassyAirfield.details"), Lang.tr("sc.tooltip.airfield.howto"));
    }
}
