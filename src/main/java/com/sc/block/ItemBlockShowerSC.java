package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityShowerSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** The decontamination shower as an item: what it washes with, and the charge and water it kept (BlockShowerSC.getDrops). */
public class ItemBlockShowerSC extends ItemBlock {

    public ItemBlockShowerSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.shower.tooltip", TileEntityShowerSC.EU_PER_TICK, TileEntityShowerSC.WATER_PER_TICK));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.machine.tooltip.energy", String.valueOf(nbt.getInteger("EnergySC"))));
        }
        if (nbt != null && nbt.getInteger("Water") > 0) {
            list.add(Lang.tr("sc.tank.tooltip.fluid", new FluidStack(FluidRegistry.WATER, 1).getLocalizedName(),
                    nbt.getInteger("Water"), TileEntityShowerSC.TANK));
        }
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.shower.hint"));
    }
}
