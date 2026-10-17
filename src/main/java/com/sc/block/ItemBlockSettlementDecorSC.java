package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §4.5): the settlement trim as items (/give only: no tab, hidden in NEI) -
 * both blocks, BlockSettlementDecorSC (a name per meta, tile.siliconage.settlementDecor.<name>.name) and its Glass
 * (tile.siliconage.settlementGlass.name). Tooltip sc.tooltip.settlementDecor, Shift .details, Ctrl .howto.
 */
public class ItemBlockSettlementDecorSC extends ItemBlock {

    public ItemBlockSettlementDecorSC(Block block) {
        super(block);
        setHasSubtypes(!(block instanceof BlockSettlementDecorSC.Glass));
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return field_150939_a instanceof BlockSettlementDecorSC.Glass ? 0 : damage >= 0 && damage < BlockSettlementDecorSC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return field_150939_a instanceof BlockSettlementDecorSC.Glass ? super.getUnlocalizedName()
                : super.getUnlocalizedName() + "." + BlockSettlementDecorSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.settlementDecor"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.settlementDecor.details"), Lang.tr("sc.tooltip.settlementDecor.howto"));
    }
}
