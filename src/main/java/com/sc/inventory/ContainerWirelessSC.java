package com.sc.inventory;

import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The wireless blocks' screen (GuiWirelessSC, the large layout): the translator's crystal half on
 * the screen, the battery slot under the gauge (transmitter / translator), the player's inventory;
 * the link's numbers synced; the buttons (owner / creative only).
 */
public class ContainerWirelessSC extends Container {

    public static final int CRYSTAL_X = 186, CRYSTAL_Y = 86;
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_BATTERY_MODE = 12, BTN_UNLINK = 20, BTN_BEAM = 21,
            BTN_PAUSE = 22, BTN_ROLE = 23;

    private final TileEntityWirelessSC te;
    private final int tileSlots;
    private final IntSyncSC sync = new IntSyncSC(12);

    public ContainerWirelessSC(InventoryPlayer playerInv, final TileEntityWirelessSC te) {
        this.te = te;
        if (te.getKind() == TileEntityWirelessSC.QUANTUM) {
            addSlotToContainer(new Slot(te, TileEntityWirelessSC.SLOT_CRYSTAL, CRYSTAL_X, CRYSTAL_Y) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return te.isItemValidForSlot(TileEntityWirelessSC.SLOT_CRYSTAL, stack);
                }

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            });
        }
        if (te.getKind() != TileEntityWirelessSC.RECEIVER) {
            addSlotToContainer(new SlotBatterySC(te, TileEntityWirelessSC.SLOT_BATTERY,
                    SlotBatterySC.itemX(GuiBigSC.GAUGE_X), SlotBatterySC.itemY(GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H)));
        }
        tileSlots = inventorySlots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, GuiBigSC.INV_X + col * 18, GuiBigSC.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, GuiBigSC.INV_X + col * 18, GuiBigSC.HOTBAR_Y));
        }
    }

    public TileEntityWirelessSC getTile() {
        return te;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.isUseableByPlayer(player);
    }

    /** Strangers look but don't take the crystal or the battery. */
    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < tileSlots && !te.allowed(player)) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player) || !te.allowed(player)) {
            return false;
        }
        switch (id) {
            case BTN_POWER:
                te.setPowerOn(!te.isPowerOn());
                com.sc.util.SoundsSC.powerClick(te, te.isPowerOn());
                return true;
            case BTN_REDSTONE: te.setRedstoneMode((te.getRedstoneMode() + 1) % 3); return true;
            case BTN_BATTERY_MODE: te.cycleBatteryMode(); return true;
            case BTN_UNLINK: te.unlink(); return true;
            case BTN_BEAM: te.toggleBeam(); return true;
            case BTN_PAUSE: te.togglePause(); return true;
            case BTN_ROLE: te.toggleRole(); return true;
            default: return false;
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] p = te.partnerPos();
        sync.send(this, crafters, new int[]{te.getEnergyStored(), te.getStatus(), te.getFlow(), te.getLossPct(), te.getDistance(),
                p[0], p[1], p[2], p[3], p[4], te.screenBits(), te.powerFlags()});
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id == 0) {
            te.setEnergyStoredClient(sync.value(0));
        } else if (id == 11) {
            te.setPowerFlagsClient(sync.value(11));
        } else {
            te.setScreenClient(sync.value(1), sync.value(2), sync.value(3), sync.value(4), sync.value(5), sync.value(6),
                    sync.value(7), sync.value(8), sync.value(9), sync.value(10));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack() || !te.allowed(player)) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int end = inventorySlots.size(), hotbar = tileSlots + 27;
        if (index < tileSlots) {
            if (!mergeItemStack(original, tileSlots, end, true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, tileSlots)
                && !mergeItemStack(original, index < hotbar ? hotbar : tileSlots, index < hotbar ? end : hotbar, false)) {
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
