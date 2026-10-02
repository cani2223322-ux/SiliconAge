package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArmorStationSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The Armour Service Station as an item: what it does (Shift), how to use it (Ctrl), the energy it kept. */
public class ItemBlockArmorStationSC extends ItemBlock {

    public ItemBlockArmorStationSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.armorStation.tooltip"));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.machine.tooltip.energy", String.valueOf(nbt.getInteger("EnergySC"))));
        }
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list,
                Lang.tr("sc.armorStation.details", TileEntityArmorStationSC.GAS_PER_TICK, TileEntityArmorStationSC.MB_PER_EU),
                Lang.tr("sc.armorStation.howto"));
    }
}
