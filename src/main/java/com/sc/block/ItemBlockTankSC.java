package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityTankSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Portable tank item: one per tier, carrying its fluid ("Fluid") and auto-output in NBT. */
public class ItemBlockTankSC extends ItemBlock {

    public ItemBlockTankSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockTankSC.TIER_NAMES[BlockTankSC.tierOf(stack.getItemDamage())];
    }

    public static FluidStack contents(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("Fluid")
                ? FluidStack.loadFluidStackFromNBT(stack.getTagCompound().getCompoundTag("Fluid")) : null;
    }

    /** A filled tank doesn't stack (each keeps its own contents); empty ones do. */
    @Override
    public int getItemStackLimit(ItemStack stack) {
        return contents(stack) != null ? 1 : 16;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int capacity = TileEntityTankSC.capacityOf(BlockTankSC.tierOf(stack.getItemDamage()));
        FluidStack fluid = contents(stack);
        if (fluid == null || fluid.getFluid() == null) {
            list.add(Lang.tr("sc.tank.tooltip.empty", capacity));
        } else {
            list.add(Lang.tr(fluid.getFluid().isGaseous(fluid) ? "sc.tank.tooltip.gas" : "sc.tank.tooltip.fluid",
                    fluid.getLocalizedName(), fluid.amount, capacity));
        }
        if (stack.hasTagCompound() && stack.getTagCompound().getBoolean("AutoOutput")) {
            list.add("\u00a77" + Lang.tr("sc.tank.output.on"));
        }
        PickaxeOnlySC.tooltip(list);
        if (com.sc.util.TooltipSC.ctrl()) {
            com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tank.tooltip.howto"), "\u00a77");
            if (fluid != null) {                    // those crafts take only an empty tank
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tank.tooltip.craftempty"), "\u00a77");
            }
        } else {
            com.sc.util.TooltipSC.hintUse(list);
        }
    }
}
