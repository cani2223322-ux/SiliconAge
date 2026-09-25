package com.sc.init;

import com.sc.block.ItemBlockTankSC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Crafting a tank into the next tier (the old tank sits in the middle): the fluid and the
 * auto-output setting come along - the bigger tank always has room for them.
 */
public class TankUpgradeRecipeSC extends ShapedOreRecipe {

    public TankUpgradeRecipeSC(ItemStack result, Object... recipe) {
        super(result, recipe);
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemBlockTankSC && s.hasTagCompound()) {
                out.setTagCompound((net.minecraft.nbt.NBTTagCompound) s.getTagCompound().copy());
                break;
            }
        }
        return out;
    }
}
