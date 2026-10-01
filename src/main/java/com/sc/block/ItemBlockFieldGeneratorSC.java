package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The Field Generator's item: what it does, the charge and settings it carries when broken or
 * dismantled (TileEntityFieldGeneratorSC.writeToItem), details under Shift, how to use under Ctrl.
 */
public class ItemBlockFieldGeneratorSC extends ItemBlock {

    public ItemBlockFieldGeneratorSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.field.tooltip.summary"));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.field.tooltip.charge", String.valueOf(nbt.getInteger("EnergySC"))));
        }
        if (nbt != null && nbt.hasKey("FieldSC")) {
            list.add("§a" + Lang.tr("sc.field.tooltip.configured"));
        }
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.field.tooltip.details") + "\n" + Lang.tr("sc.field.tooltip.details2"),
                Lang.tr("sc.field.tooltip.howto"));
    }
}
