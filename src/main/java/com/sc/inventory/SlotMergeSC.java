package com.sc.inventory;

import java.util.List;

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
                int room = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit()) - held.stackSize;
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
                int n = Math.min(stack.stackSize, Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit()));
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
