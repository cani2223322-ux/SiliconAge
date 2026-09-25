package com.sc.inventory;

import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Energy storage screen: one weapon / armor charging slot, the player inventory, energy + flow sync. */
public class ContainerEnergyStorageSC extends Container {

    /** Right of the flow / output lines, just clear of the energy well at x 152 (GuiEnergyStorageSC). */
    public static final int SLOT_X = 130, SLOT_Y = 53;

    private final TileEntityEnergyStorageSC storage;
    private final IntSyncSC sync = new IntSyncSC(2);   // energy, flow per tick

    public ContainerEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        this.storage = storage;
        addSlotToContainer(new Slot(storage, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return ContainerEnergyStorageSC.this.storage.isItemValidForSlot(0, stack);       // chargeable, and of the block's tier or lower
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return storage.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{storage.getEnergyStored(), storage.getFlowPerTick()});
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id == 0) {
            storage.setEnergyStoredClient(sync.value(0));
        } else if (id == 1) {
            storage.setFlowClient(sync.value(1));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        if (slotIndex == 0) {
            if (!mergeItemStack(original, 1, inventorySlots.size(), true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, 1)) {
            int hotbarStart = 1 + 27;
            boolean moved = slotIndex < hotbarStart
                    ? mergeItemStack(original, hotbarStart, inventorySlots.size(), false)
                    : mergeItemStack(original, 1, hotbarStart, false);
            if (!moved) {
                return null;
            }
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}
