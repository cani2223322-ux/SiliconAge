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
        for (net.minecraftforge.fluids.FluidStack fluid : com.sc.tileentity.TileEntityMachineSC.tankFluidsOf(
                stack.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityMachineSC.ITEM_TANKS_KEY))) {
            if (fluid != null && fluid.getFluid() != null) {
                list.add(com.sc.manual.Lang.tr(fluid.getFluid().isGaseous(fluid) ? "sc.tank.tooltip.gas" : "sc.tank.tooltip.fluid",
                        fluid.getLocalizedName(), fluid.amount, com.sc.tileentity.TileEntityMachineSC.TANK_CAPACITY));
            }
        }
    }
}
