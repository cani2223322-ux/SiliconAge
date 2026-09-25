package com.sc.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Moving items into / out of a neighbouring inventory the way automation should: through the face
 * that actually touches it, honouring ISidedInventory. `dir` is always the direction from the
 * one doing the moving towards the inventory, so the inventory's own face is dir's opposite.
 */
public final class InvUtilSC {

    private InvUtilSC() {
    }

    public static int face(ForgeDirection dir) {
        return dir.getOpposite().ordinal();
    }

    public static int[] slots(IInventory inv, ForgeDirection dir) {
        if (inv instanceof ISidedInventory) {
            return ((ISidedInventory) inv).getAccessibleSlotsFromSide(face(dir));
        }
        int[] all = new int[inv.getSizeInventory()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    public static boolean canTake(IInventory inv, int slot, ItemStack stack, ForgeDirection dir) {
        return !(inv instanceof ISidedInventory) || ((ISidedInventory) inv).canExtractItem(slot, stack, face(dir));
    }

    public static boolean canGive(IInventory inv, int slot, ItemStack stack, ForgeDirection dir) {
        return inv.isItemValidForSlot(slot, stack)
                && (!(inv instanceof ISidedInventory) || ((ISidedInventory) inv).canInsertItem(slot, stack, face(dir)));
    }

    /** Puts as much of `stack` as fits into `inv` (stack itself is not changed). @return how many did NOT fit */
    public static int insert(IInventory inv, ForgeDirection dir, ItemStack stack) {
        int remaining = stack.stackSize;
        int[] slots = slots(inv, dir);
        // top up matching stacks first, then empty slots
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                int slot = slots[i];
                ItemStack existing = inv.getStackInSlot(slot);
                if ((pass == 0) == (existing == null) || !canGive(inv, slot, stack, dir)) {
                    continue;
                }
                int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
                if (existing == null) {
                    ItemStack placed = stack.copy();
                    placed.stackSize = Math.min(remaining, limit);
                    inv.setInventorySlotContents(slot, placed);
                    remaining -= placed.stackSize;
                } else if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                    int place = Math.min(remaining, Math.max(0, limit - existing.stackSize));
                    existing.stackSize += place;
                    remaining -= place;
                }
            }
        }
        if (remaining != stack.stackSize) {
            inv.markDirty();
        }
        return remaining;
    }
}
