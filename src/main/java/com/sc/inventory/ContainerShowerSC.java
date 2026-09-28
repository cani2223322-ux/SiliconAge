package com.sc.inventory;

import com.sc.tileentity.TileEntityShowerSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** The decontamination shower's screen: the battery slot under the gauge, the player's inventory, the numbers synced. */
public class ContainerShowerSC extends Container {

    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_BATTERY_MODE = 12;

    private final TileEntityShowerSC te;
    private final IntSyncSC sync = new IntSyncSC(5);

    public ContainerShowerSC(InventoryPlayer playerInv, TileEntityShowerSC te) {
        this.te = te;
        addSlotToContainer(new SlotBatterySC(te, TileEntityShowerSC.SLOT_BATTERY,
                SlotBatterySC.itemX(GuiBigSC.GAUGE_X), SlotBatterySC.itemY(GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H)));
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
        return te.isUseableByPlayer(player);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        switch (id) {
            case BTN_POWER:
                te.setPowerOn(!te.isPowerOn());
                com.sc.util.SoundsSC.powerClick(te, te.isPowerOn());
                return true;
            case BTN_REDSTONE: te.setRedstoneMode((te.getRedstoneMode() + 1) % 3); return true;
            case BTN_BATTERY_MODE: te.cycleBatteryMode(); return true;
            default: return false;
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{te.getEnergyStored(), te.getStatus(), te.getPlayers(), te.getTank().getFluidAmount(),
                te.powerFlags()});
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id == 0) {
            te.setEnergyStoredClient(sync.value(0));
        } else if (id == 4) {
            te.setPowerFlagsClient(sync.value(4));
        } else {
            te.setScreenClient(sync.value(1), sync.value(2), sync.value(3));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int end = inventorySlots.size(), hotbar = 1 + 27;
        if (index < 1) {
            if (!mergeItemStack(original, 1, end, true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, 1)
                && !mergeItemStack(original, index < hotbar ? hotbar : 1, index < hotbar ? end : hotbar, false)) {
            return null;
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}
