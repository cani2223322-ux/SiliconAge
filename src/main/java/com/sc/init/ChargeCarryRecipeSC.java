package com.sc.init;

import com.sc.item.ItemBatterySC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * A block built round a charged battery (the Singular Station round an Exo core): the battery's
 * charge goes into the block item's buffer (NBT "EnergySC", what the block reads on placement),
 * up to `maxEu`. Generic materials go by their ore names as in OreRecipes.shaped; the batteries stay
 * exact stacks (any charge), as in BatteryRecipeSC.
 * МК-3: with carryFrom(item), a block item of that kind in the grid (the Armour Service Station)
 * hands its saved contents over - its NBT (tanks, modules, settings) goes into the result as is,
 * its EU is added to the batteries' (still capped at `maxEu`). Nothing of it is lost in the craft.
 */
public class ChargeCarryRecipeSC extends ShapedOreRecipe {

    private final int maxEu;
    /** The block item whose saved contents go into the result (null: none). */
    private Item carry;

    public ChargeCarryRecipeSC(ItemStack result, int maxEu, Object... recipe) {
        super(result, convert(recipe));
        this.maxEu = Math.max(0, maxEu);
    }

    /** The grid's `item` (a block item keeping its contents in NBT) passes them to the result. */
    public ChargeCarryRecipeSC carryFrom(Item item) {
        this.carry = item;
        return this;
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
        return (int) Math.max(0L, Math.min(max, batteries(grid)));
    }

    private static long batteries(InventoryCrafting grid) {
        long stored = 0;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (ItemBatterySC.isBattery(s)) {
                stored += ItemBatterySC.chargeOf(s);
            }
        }
        return stored;
    }

    /** The saved contents of the carried block item in `grid` (null: none), without its name / repair data. */
    public static NBTTagCompound carriedTag(InventoryCrafting grid, Item item) {
        if (item == null) {
            return null;
        }
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s != null && s.getItem() == item && s.hasTagCompound()) {
                NBTTagCompound t = (NBTTagCompound) s.getTagCompound().copy();
                t.removeTag("display");
                t.removeTag("RepairCost");
                t.removeTag("ench");
                return t.hasNoTags() ? null : t;
            }
        }
        return null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        if (out == null) {
            return null;
        }
        NBTTagCompound from = carriedTag(grid, carry);
        long eu = batteries(grid);
        NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
        if (from != null) {
            eu += Math.max(0, from.getInteger("EnergySC"));
            from.removeTag("EnergySC");
            for (Object k : from.func_150296_c()) {         // getKeySet: the station's tanks, modules, settings
                tag.setTag((String) k, from.getTag((String) k).copy());
            }
        }
        int put = (int) Math.max(0L, Math.min(maxEu, eu));
        if (put > 0) {
            tag.setInteger("EnergySC", put);
        }
        if (!tag.hasNoTags()) {
            out.setTagCompound(tag);
        }
        return out;
    }
}
