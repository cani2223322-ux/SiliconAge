package com.sc.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container.mergeItemStack (shift-click) without its 1.7.10 blind spot: it never asks
 * Slot.isItemValid() or getSlotStackLimit(), so shift-clicking dirt put it straight into a
 * machine's recipe-filtered input slot, or into a generator slot that isn't even on screen
 * (ContainerGeneratorSC parks unused ones off-panel) where the player couldn't take it back.
 * Same order as vanilla: top up matching stacks first, then one empty slot.
 */
final class SlotMergeSC {

    private SlotMergeSC() {
    }

    /** A slot whose limit depends on the item going in (1.7.10's getSlotStackLimit() has no stack to ask about). */
    interface Limited {
        int limitFor(ItemStack stack);
    }

    /** How many of `stack` the slot holds: Limited's own answer, else getSlotStackLimit(). */
    static int limit(Slot slot, ItemStack stack) {
        return slot instanceof Limited ? ((Limited) slot).limitFor(stack) : slot.getSlotStackLimit();
    }

    /**
     * slotClick mode 2 (a hotbar key over a slot): into an empty slot vanilla puts the whole hotbar
     * stack, past getSlotStackLimit(). Call first in Container.slotClick and return null when true.
     * @return true if the click must be refused: the hotbar stack doesn't pass isItemValid or is
     * more than the target slot holds (a slot of the player's own inventory is never refused)
     */
    static boolean refuseHotbarSwap(Container container, int slotId, int button, int mode, EntityPlayer player) {
        if (mode != 2 || button < 0 || button >= 9 || slotId < 0 || slotId >= container.inventorySlots.size()) {
            return false;
        }
        Slot slot = (Slot) container.inventorySlots.get(slotId);
        ItemStack hot = player.inventory.getStackInSlot(button);
        if (slot == null || hot == null || slot.inventory == player.inventory || slot.getHasStack()) {
            return false;                       // taking out to the hotbar: vanilla puts nothing in
        }
        return !slot.isItemValid(hot) || hot.stackSize > limit(slot, hot);
    }

    static boolean mergeValid(List<?> slots, ItemStack stack, int start, int end) {
        boolean moved = false;
        if (stack.isStackable()) {
            for (int i = start; i < end && stack.stackSize > 0; i++) {
                Slot slot = (Slot) slots.get(i);
                ItemStack held = slot.getStack();
                if (held == null || !slot.isItemValid(stack) || held.getItem() != stack.getItem()
                        || (stack.getHasSubtypes() && stack.getItemDamage() != held.getItemDamage())
                        || !ItemStack.areItemStackTagsEqual(stack, held)) {
                    continue;
                }
                int room = Math.min(stack.getMaxStackSize(), limit(slot, stack)) - held.stackSize;
                if (room > 0) {
                    int n = Math.min(room, stack.stackSize);
                    held.stackSize += n;
                    stack.stackSize -= n;
                    slot.onSlotChanged();
                    moved = true;
                }
            }
        }
        for (int i = start; i < end && stack.stackSize > 0; i++) {
            Slot slot = (Slot) slots.get(i);
            if (slot.getStack() == null && slot.isItemValid(stack)) {
                int n = Math.min(stack.stackSize, Math.min(stack.getMaxStackSize(), limit(slot, stack)));
                ItemStack put = stack.copy();
                put.stackSize = n;
                slot.putStack(put);
                stack.stackSize -= n;
                moved = true;
                break;
            }
        }
        return moved;
    }
}
