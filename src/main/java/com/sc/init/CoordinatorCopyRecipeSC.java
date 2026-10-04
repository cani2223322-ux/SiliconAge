package com.sc.init;

import com.sc.bridge.BridgeItemDataSC;
import com.sc.item.ItemCoordinatorSC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * A coordinator copied: one with a stored point and one empty, anywhere in the grid, nothing else.
 * The result is the empty one with the point, name and safe flag written in; the filled one stays in
 * the grid (ItemCoordinatorSC's container item - a filled coordinator is never used up in a craft),
 * so the craft leaves two coordinators with the same point. Two empty or two filled ones do not match.
 */
public class CoordinatorCopyRecipeSC extends ShapelessOreRecipe {

    public CoordinatorCopyRecipeSC() {
        super(new ItemStack(ModItems.coordinator), new ItemStack(ModItems.coordinator), new ItemStack(ModItems.coordinator));
    }

    /** {filled, empty} in `grid`, or null when it is not exactly one of each and nothing else. */
    private static ItemStack[] pair(InventoryCrafting grid) {
        ItemStack filled = null, empty = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s == null) {
                continue;
            }
            if (!ItemCoordinatorSC.isCoordinator(s)) {
                return null;
            }
            if (BridgeItemDataSC.point(s) != null) {
                if (filled != null) {
                    return null;
                }
                filled = s;
            } else {
                if (empty != null) {
                    return null;
                }
                empty = s;
            }
        }
        return filled != null && empty != null ? new ItemStack[]{filled, empty} : null;
    }

    @Override
    public boolean matches(InventoryCrafting grid, net.minecraft.world.World world) {
        return pair(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack[] p = pair(grid);
        if (p == null) {
            return null;
        }
        ItemStack out = new ItemStack(ModItems.coordinator);
        BridgeItemDataSC.copyPoint(p[0], out);
        return out;
    }
}
