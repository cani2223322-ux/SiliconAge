package com.sc.block;

import com.sc.machine.MachineType;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockMachineSC extends ItemBlock {

    private final int typeOffset;

    public ItemBlockMachineSC(Block block) {
        super(block);
        setHasSubtypes(true);
        typeOffset = block instanceof BlockMachineSC ? ((BlockMachineSC) block).getTypeOffset() : 0;
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        MachineType[] values = MachineType.values();
        int index = typeOffset + stack.getItemDamage();
        MachineType type = values[index >= 0 && index < values.length ? index : 0];
        return super.getUnlocalizedName() + "." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A machine broken with fluid inside keeps it (BlockMachineSC.getDrops) - show what it holds. */
    @Override
    public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list, boolean advanced) {
        MachineType[] values = MachineType.values();
        int index = typeOffset + stack.getItemDamage();
        MachineType type = values[index >= 0 && index < values.length ? index : 0];
        list.add("§7" + com.sc.manual.Lang.tr("sc.machine.tooltip.info", type.tier.name(), type.tier.getVoltage(),
                com.sc.tileentity.TileEntityMachineSC.configEuPerTick(type)));
        contents(stack, type, list);
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.trOr("sc.manual.machine." + type.name().toLowerCase(java.util.Locale.ROOT), null),
                com.sc.manual.Lang.tr("sc.machine.tooltip.howto"));
    }

    /** What a broken machine kept: charge, upgrades, tank contents. */
    private static void contents(ItemStack stack, MachineType type, java.util.List list) {
        int charge = stack.hasTagCompound() ? stack.getTagCompound().getInteger(com.sc.tileentity.TileEntityMachineSC.ITEM_ENERGY_KEY) : 0;
        if (charge > 0) {                                                       // the buffer's charge (BlockMachineSC.getDrops)
            list.add(com.sc.manual.Lang.tr("sc.machine.tooltip.energy", String.valueOf(charge)));
        }
        int matter = stack.hasTagCompound() ? stack.getTagCompound().getInteger(com.sc.tileentity.TileEntityMachineSC.ITEM_MATTER_KEY) : 0;
        if (matter > 0) {                                                       // a Matter Compressor's mass counter
            list.add(com.sc.manual.Lang.tr("sc.machine.tooltip.matter", matter, com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE));
        }
        if (stack.hasTagCompound() && stack.getTagCompound().getBoolean(com.sc.tileentity.TileEntityMachineSC.ITEM_LIQUID_KEY)) {
            list.add(com.sc.manual.Lang.tr("sc.machine.tooltip.liquid"));            // СМ1: set to «жидкая материя»
        }
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(com.sc.tileentity.TileEntityMachineSC.ITEM_UPGRADES_KEY)) {
            for (ItemStack up : com.sc.tileentity.TileEntityMachineSC.upgradesOf(
                    stack.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityMachineSC.ITEM_UPGRADES_KEY))) {
                if (up != null) {
                    list.add(com.sc.manual.Lang.tr("sc.machine.tooltip.upgrade", up.getDisplayName()));
                }
            }
        }
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(com.sc.tileentity.TileEntityMachineSC.ITEM_TANKS_KEY)) {
            return;
        }
        // the tanks' size once placed: the compressor's bigger base, plus the saved Tank Extensions
        int capacity = type.isCompressor() ? com.sc.tileentity.TileEntityMachineSC.SM_TANK : com.sc.tileentity.TileEntityMachineSC.TANK_CAPACITY;
        int extensions = 0;
        if (stack.getTagCompound().hasKey(com.sc.tileentity.TileEntityMachineSC.ITEM_UPGRADES_KEY)) {
            for (ItemStack up : com.sc.tileentity.TileEntityMachineSC.upgradesOf(
                    stack.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityMachineSC.ITEM_UPGRADES_KEY))) {
                if (up != null && up.getItem() instanceof com.sc.item.ItemUpgradeSC
                        && com.sc.item.ItemUpgradeSC.typeOf(up) == com.sc.machine.UpgradeType.TANK_EXTENSION) {
                    extensions += up.stackSize;
                }
            }
        }
        capacity += Math.min(com.sc.machine.UpgradeType.MAX_TANK_UPGRADES, extensions) * com.sc.machine.UpgradeType.TANK_PER_UPGRADE;
        for (net.minecraftforge.fluids.FluidStack fluid : com.sc.tileentity.TileEntityMachineSC.tankFluidsOf(
                stack.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityMachineSC.ITEM_TANKS_KEY))) {
            if (fluid != null && fluid.getFluid() != null) {
                list.add(com.sc.manual.Lang.tr(fluid.getFluid().isGaseous(fluid) ? "sc.tank.tooltip.gas" : "sc.tank.tooltip.fluid",
                        fluid.getLocalizedName(), fluid.amount, capacity));
            }
        }
    }
}
