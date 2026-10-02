package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArmorStationSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The Armour Service Station as an item: what it does and what its tanks hold (Shift), how to use it (Ctrl), the energy and the modules it kept. */
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
        if (nbt != null && nbt.hasKey(TileEntityArmorStationSC.ITEM_UPGRADES_KEY)) {        // modules kept in the item
            for (ItemStack up : TileEntityArmorStationSC.upgradesOf(nbt.getCompoundTag(TileEntityArmorStationSC.ITEM_UPGRADES_KEY))) {
                if (up != null) {
                    list.add(Lang.tr("sc.machine.tooltip.upgrade", up.getDisplayName() + (up.stackSize > 1 ? " x" + up.stackSize : "")));
                }
            }
        }
        PickaxeOnlySC.tooltip(list);
        StringBuilder tanks = new StringBuilder();                 // Shift: what the tanks hold (only the gases there are)
        if (nbt != null && nbt.hasKey(TileEntityArmorStationSC.ITEM_TANKS_KEY)) {
            int[] a = TileEntityArmorStationSC.tankAmountsOf(nbt.getCompoundTag(TileEntityArmorStationSC.ITEM_TANKS_KEY));
            for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
                if (a[g.ordinal()] > 0) {
                    tanks.append('\n').append(Lang.tr("sc.armorStation.tooltip.tank", Lang.tr("sc.armorStation.gas." + g.key()), a[g.ordinal()]));
                }
            }
        }
        com.sc.util.TooltipSC.more(list,
                Lang.tr("sc.armorStation.details", TileEntityArmorStationSC.GAS_PER_TICK, TileEntityArmorStationSC.MB_PER_EU)
                        + "\n" + Lang.tr("sc.armorStation.tanks.tooltip", TileEntityArmorStationSC.TANK_CAPACITY,
                        com.sc.machine.UpgradeType.TANK_PER_UPGRADE)
                        + "\n" + Lang.tr("sc.armorStation.modules.tooltip", TileEntityArmorStationSC.MAX_OVERCLOCKERS,
                        com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE)
                        + (tanks.length() > 0 ? "\n" + Lang.tr("sc.armorStation.tooltip.tanks") + tanks : ""),
                Lang.tr("sc.armorStation.howto"));
    }
}
