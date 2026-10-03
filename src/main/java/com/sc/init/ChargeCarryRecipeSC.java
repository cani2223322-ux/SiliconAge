package com.sc.init;

import com.sc.item.ItemBatterySC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * A block built round a charged battery (the Singular Station round an Exo core): the battery's
 * charge goes into the block item's buffer (NBT "EnergySC", what the block reads on placement),
 * up to `maxEu`. Generic materials go by their ore names as in OreRecipes.shaped; the batteries stay
 * exact stacks (any charge), as in BatteryRecipeSC.
 */
public class ChargeCarryRecipeSC extends ShapedOreRecipe {

    private final int maxEu;

    public ChargeCarryRecipeSC(ItemStack result, int maxEu, Object... recipe) {
        super(result, convert(recipe));
        this.maxEu = Math.max(0, maxEu);
    }

    private static Object[] convert(Object[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] instanceof ItemStack && !ItemBatterySC.isBattery((ItemStack) in[i]) ? OreRecipes.oreName((ItemStack) in[i]) : in[i];
        }
        return out;
    }

    /** EU the batteries in `grid` carry, capped at `max`. */
    public static int carried(InventoryCrafting grid, int max) {
        long stored = 0;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (ItemBatterySC.isBattery(s)) {
                stored += ItemBatterySC.chargeOf(s);
            }
        }
        return (int) Math.max(0L, Math.min(max, stored));
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        int eu = carried(grid, maxEu);
        if (out != null && eu > 0) {
            NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
            tag.setInteger("EnergySC", eu);
            out.setTagCompound(tag);
        }
        return out;
    }
}
