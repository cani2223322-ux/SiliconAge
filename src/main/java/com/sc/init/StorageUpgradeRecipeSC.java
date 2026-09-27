package com.sc.init;

import com.sc.block.BlockEnergyStorageSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Crafting a storage into the next tier, or into a charge pad (the old storage sits in the
 * recipe): its stored charge ("EnergySC") comes along, capped at what the result holds. Like
 * TankUpgradeRecipeSC, with the same OreDict swap for materials as OreRecipes.shaped.
 */
public class StorageUpgradeRecipeSC extends ShapedOreRecipe {

    public StorageUpgradeRecipeSC(ItemStack result, Object... recipe) {
        super(result, convert(recipe));
    }

    private static Object[] convert(Object[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            // the storage ingredient itself stays an exact stack - only generic materials are swapped
            out[i] = in[i] instanceof ItemStack && !isStorage((ItemStack) in[i]) ? OreRecipes.oreName((ItemStack) in[i]) : in[i];
        }
        return out;
    }

    private static boolean isStorage(ItemStack s) {
        return s != null && s.getItem() instanceof ItemBlock
                && Block.getBlockFromItem(s.getItem()) instanceof BlockEnergyStorageSC;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        long stored = 0;
        NBTTagCompound ups = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (isStorage(s) && s.hasTagCompound()) {
                stored += Math.max(0, s.getTagCompound().getInteger("EnergySC"));
                if (s.getTagCompound().hasKey("UpgradesSC")) {
                    ups = (NBTTagCompound) s.getTagCompound().getCompoundTag("UpgradesSC").copy();
                }
            }
        }
        if (out != null && (stored > 0 || ups != null)) {
            NBTTagCompound nbt = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
            if (ups != null) {
                nbt.setTag("UpgradesSC", ups);             // the upgrades come along: the charge they allowed fits again
            }
            if (stored > 0) {
                int cap = TileEntityEnergyStorageSC.capacityOf(BlockEnergyStorageSC.tierFor(out.getItemDamage()));
                nbt.setInteger("EnergySC", (int) Math.min(ups != null ? Integer.MAX_VALUE : cap, stored));
            }
            out.setTagCompound(nbt);
        }
        return out;
    }
}
