package com.sc.inventory;

import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Energy storage screen: one weapon / armor charging slot, the player inventory, energy + flow sync. */
public class ContainerEnergyStorageSC extends Container {

    /** On the large screen's holo panel, left of the readings (GuiEnergyStorageSC / GuiBigSC). */
    public static final int SLOT_X = 16, SLOT_Y = 30;
    /** The discharge slot, under the charge slot. */
    public static final int DIS_X = 16, DIS_Y = 60;
    private static final int TILE_SLOTS = TileEntityEnergyStorageSC.SLOT_COUNT;

    private final TileEntityEnergyStorageSC storage;
    private final IntSyncSC sync = new IntSyncSC(3);   // energy, flow per tick, the power switch
    /** The power switch and the redstone mode (GuiPowerSC). */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11;

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        if (id == BTN_POWER) {
            storage.setPowerOn(!storage.isPowerOn());
            return true;
        }
        if (id == BTN_REDSTONE) {
            storage.setRedstoneMode((storage.getRedstoneMode() + 1) % 3);
            return true;
        }
        return false;
    }

    public ContainerEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        this.storage = storage;
        addSlotToContainer(new Slot(storage, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return ContainerEnergyStorageSC.this.storage.isItemValidForSlot(0, stack);       // chargeable, and of the block's tier or lower
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });
        addSlotToContainer(new Slot(storage, TileEntityEnergyStorageSC.SLOT_DISCHARGE, DIS_X, DIS_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return TileEntityEnergyStorageSC.isDischargeable(stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });
        for (int i = 0; i < TileEntityEnergyStorageSC.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new Slot(storage, TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + i, GuiBigSC.UPG_X + i * 18, GuiBigSC.UPG_Y) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return TileEntityEnergyStorageSC.acceptsUpgrade(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, GuiBigSC.INV_X + col * 18, GuiBigSC.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, GuiBigSC.INV_X + col * 18, GuiBigSC.HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return storage.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{storage.getEnergyStored(), storage.getFlowPerTick(), storage.powerFlags()});
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id == 0) {
            storage.setEnergyStoredClient(sync.value(0));
        } else if (id == 1) {
            storage.setFlowClient(sync.value(1));
        } else if (id == 2) {
            storage.setPowerFlagsClient(sync.value(2));
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
        if (slotIndex < TILE_SLOTS) {
            if (!mergeItemStack(original, TILE_SLOTS, inventorySlots.size(), true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, TILE_SLOTS)) {
            int hotbarStart = TILE_SLOTS + 27;
            boolean moved = slotIndex < hotbarStart
                    ? mergeItemStack(original, hotbarStart, inventorySlots.size(), false)
                    : mergeItemStack(original, TILE_SLOTS, hotbarStart, false);
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
