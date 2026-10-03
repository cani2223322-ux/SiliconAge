package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.SingularStationMath;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The Singular Service Station as an item: what it does (Shift: the details, the tanks), how to use it (Ctrl), the energy and modules it kept. */
public class ItemBlockSingularStationSC extends ItemBlock {

    public ItemBlockSingularStationSC(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.singStation.tooltip"));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.machine.tooltip.energy", String.valueOf(nbt.getInteger("EnergySC"))));
        }
        if (nbt != null && nbt.hasKey(TileEntityArmorStationSC.ITEM_UPGRADES_KEY)) {
            for (ItemStack up : TileEntityArmorStationSC.upgradesOf(nbt.getCompoundTag(TileEntityArmorStationSC.ITEM_UPGRADES_KEY))) {
                if (up != null) {
                    list.add(Lang.tr("sc.machine.tooltip.upgrade", up.getDisplayName() + (up.stackSize > 1 ? " x" + up.stackSize : "")));
                }
            }
        }
        PickaxeOnlySC.tooltip(list);
        StringBuilder tanks = new StringBuilder();
        if (nbt != null && nbt.hasKey(TileEntityArmorStationSC.ITEM_TANKS_KEY)) {
            int[] a = TileEntityArmorStationSC.tankAmountsOf(nbt.getCompoundTag(TileEntityArmorStationSC.ITEM_TANKS_KEY));
            for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
                if (a[g.ordinal()] > 0) {
                    tanks.append('\n').append(Lang.tr("sc.armorStation.tooltip.tank", Lang.tr("sc.armorStation.gas." + g.key()), a[g.ordinal()]));
                }
            }
        }
        com.sc.util.TooltipSC.more(list,
                Lang.tr("sc.singStation.details", TileEntityArmorStationSC.TANK_CAPACITY, TileEntitySingularStationSC.SM_TANK)
                        + "\n" + Lang.tr("sc.singStation.details2", SingularStationMath.STABILISER_PERCENT, SingularStationMath.MAX_STABILISERS,
                        SingularStationMath.RESONANCE_SPEED_PERCENT, 100 - SingularStationMath.RESONANCE_EU_PERCENT)
                        + (tanks.length() > 0 ? "\n" + Lang.tr("sc.armorStation.tooltip.tanks") + tanks : ""),
                Lang.tr("sc.singStation.howto"));
    }
}
