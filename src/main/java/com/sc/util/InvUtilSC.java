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
            int[] sided = ((ISidedInventory) inv).getAccessibleSlotsFromSide(face(dir));
            return sided == null ? new int[0] : sided;              // another mod's inventory may give null
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
        return insert(inv, dir, stack, false);
    }

    /**
     * As insert, or only counts (simulate). Never edits the stack another inventory hands out (it may be a
     * copy): a topped-up slot gets a fresh stack through setInventorySlotContents; slot numbers out of
     * range are skipped. @return how many did NOT fit
     */
    public static int insert(IInventory inv, ForgeDirection dir, ItemStack stack, boolean simulate) {
        if (inv == null || stack == null || stack.stackSize <= 0) {
            return stack == null ? 0 : Math.max(0, stack.stackSize);
        }
        int remaining = stack.stackSize;
        int[] slots = slots(inv, dir);
        int size = inv.getSizeInventory();
        int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
        if (limit <= 0) {
            return remaining;
        }
        // top up matching stacks first, then empty slots
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                int slot = slots[i];
                if (slot < 0 || slot >= size) {
                    continue;
                }
                ItemStack existing = inv.getStackInSlot(slot);
                if ((pass == 0) == (existing == null) || !canGive(inv, slot, stack, dir)) {
                    continue;
                }
                if (existing == null) {
                    int place = Math.min(remaining, limit);
                    if (!simulate) {
                        ItemStack placed = stack.copy();
                        placed.stackSize = place;
                        inv.setInventorySlotContents(slot, placed);
                    }
                    remaining -= place;
                } else if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                    int place = Math.min(remaining, Math.max(0, limit - existing.stackSize));
                    if (place > 0 && !simulate) {
                        ItemStack grown = existing.copy();
                        grown.stackSize += place;
                        inv.setInventorySlotContents(slot, grown);
                    }
                    remaining -= place;
                }
            }
        }
        if (!simulate && remaining != stack.stackSize) {
            inv.markDirty();
        }
        return remaining;
    }

    /**
     * Moves up to `max` of slot `slot` of `source` into `dest` without trusting the source: asks only for
     * what fits, moves what decrStackSize really returned (null or nothing: nothing moved), and puts any
     * surplus or mismatch back into the source - or drops it at (x, y, z) if `world` is given.
     * @return how many items arrived in `dest`
     */
    public static int move(IInventory source, int slot, ForgeDirection sourceDir, IInventory dest, ForgeDirection destDir, int max,
                           net.minecraft.world.World world, double x, double y, double z) {
        ItemStack there = source.getStackInSlot(slot);
        if (there == null || there.stackSize <= 0 || max <= 0) {
            return 0;
        }
        ItemStack want = there.copy();
        want.stackSize = Math.min(max, there.stackSize);
        int fits = want.stackSize - insert(dest, destDir, want, true);
        if (fits <= 0) {
            return 0;
        }
        ItemStack got = source.decrStackSize(slot, fits);
        if (got == null || got.stackSize <= 0) {
            return 0;
        }
        source.markDirty();
        boolean same = got.isItemEqual(there) && ItemStack.areItemStackTagsEqual(got, there);
        int arrived = 0;
        ItemStack left = got;
        if (same) {
            int notIn = insert(dest, destDir, got, false);
            arrived = got.stackSize - notIn;
            left = got.copy();
            left.stackSize = notIn;
        }
        if (left.stackSize > 0) {
            int back = insert(source, sourceDir, left, false);
            if (back > 0 && world != null && !world.isRemote) {
                ItemStack drop = left.copy();
                drop.stackSize = back;
                world.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(world, x, y, z, drop));
            }
        }
        return arrived;
    }
}
