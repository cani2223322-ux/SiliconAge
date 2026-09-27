package com.sc.init;

import com.sc.item.ItemBatterySC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * A battery built round the tier below it: the charge of the batteries in the grid comes along,
 * capped at what the new one holds. Generic materials are swapped for their ore names as in
 * OreRecipes.shaped; the batteries themselves stay exact stacks (any charge).
 */
public class BatteryRecipeSC extends ShapedOreRecipe {

    public BatteryRecipeSC(ItemStack result, Object... recipe) {
        super(result, convert(recipe));
    }

    private static Object[] convert(Object[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] instanceof ItemStack && !ItemBatterySC.isBattery((ItemStack) in[i]) ? OreRecipes.oreName((ItemStack) in[i]) : in[i];
        }
        return out;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        long stored = 0;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (ItemBatterySC.isBattery(s)) {
                stored += ItemBatterySC.chargeOf(s);
            }
        }
        if (out != null && stored > 0) {
            ItemBatterySC.setCharge(out, stored);
        }
        return out;
    }
}
