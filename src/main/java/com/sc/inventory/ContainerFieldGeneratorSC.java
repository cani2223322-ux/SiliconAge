package com.sc.inventory;

import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Screen of a Field Generator master node (§7/§9/§16): the settings (buttons), the energy bar kept
 * live via the same field-sync pattern as ContainerMachineSC/ContainerGeneratorSC, and the
 * Upgrades tab - four slots for energy storage / transformer upgrades and the player's inventory. The slots only
 * show on that tab (GuiFieldGeneratorSC moves them off-screen on the others, setSlotsShown).
 * Only ever opened on a master (see BlockFieldGeneratorSC).
 */
public class ContainerFieldGeneratorSC extends Container {

    private final TileEntityFieldGeneratorSC field;
    private final IntSyncSC sync = new IntSyncSC(5);  // energy, mode, node count, active, range

    // Button ids for enchantItem (the vanilla GUI-button packet, no custom networking needed).
    public static final int BTN_RANGE_MINUS_16 = 0, BTN_RANGE_MINUS_1 = 1, BTN_RANGE_PLUS_1 = 2,
            BTN_RANGE_PLUS_16 = 3, BTN_MODE = 4, BTN_COLOR = 5, BTN_REDSTONE = 6, BTN_FILTER = 7;
    /** Switches: BTN_FLAG_BASE + the bit's index (TileEntityFieldGeneratorSC.F_*). */
    public static final int BTN_FLAG_BASE = 10, FLAG_COUNT = 9;

    /** Server side of the screen's buttons, master only, owner / access list only. */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!field.isMaster() || !canInteractWith(player)) {
            return false;
        }
        if (!field.allowed(player)) {
            player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.noaccess", field.getOwner()));
            return false;
        }
        switch (id) {
            case BTN_RANGE_MINUS_16: field.adjustRange(-16); return true;
            case BTN_RANGE_MINUS_1: field.adjustRange(-1); return true;
            case BTN_RANGE_PLUS_1: field.adjustRange(1); return true;
            case BTN_RANGE_PLUS_16: field.adjustRange(16); return true;
            case BTN_MODE: field.cycleMode(); return true;
            case BTN_COLOR: field.cycleColor(); return true;
            case BTN_REDSTONE: field.cycleRedstone(); return true;
            case BTN_FILTER: field.cycleFilter(); return true;
            default:
                if (id >= BTN_FLAG_BASE && id < BTN_FLAG_BASE + FLAG_COUNT) {
                    field.toggle(1 << (id - BTN_FLAG_BASE));
                    return true;
                }
                return false;
        }
    }

    public ContainerFieldGeneratorSC(InventoryPlayer playerInv, TileEntityFieldGeneratorSC field) {
        this.field = field;
        int n = TileEntityFieldGeneratorSC.UPGRADE_SLOTS;
        for (int i = 0; i < n; i++) {
            addSlotToContainer(new SlotFieldUpgrade(field, i, UPGRADE_X + i * 18, UPGRADE_Y));
        }
        // The player's inventory (its hotbar also keeps vanilla's processPlayerBlockPlacement from
        // NPE-ing on a screen with no slot for the held item - the reason this screen used to carry
        // a hidden hotbar).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, INV_Y + 58));
        }
        shownX = new int[inventorySlots.size()];
        for (int i = 0; i < shownX.length; i++) {
            shownX[i] = ((Slot) inventorySlots.get(i)).xDisplayPosition;
        }
    }

    /** Where the Upgrades tab draws its slots (GuiFieldGeneratorSC draws the frames there). */
    public static final int UPGRADE_X = 89, UPGRADE_Y = 48, INV_X = 44, INV_Y = 144;
    private final int[] shownX;

    /** Client: the slots on the Upgrades tab, off-screen (not hoverable or clickable) on the others. */
    @SideOnly(Side.CLIENT)
    public void setSlotsShown(boolean shown) {
        for (int i = 0; i < shownX.length; i++) {
            ((Slot) inventorySlots.get(i)).xDisplayPosition = shown ? shownX[i] : -10000;
        }
    }

    /** Shift-click: upgrades into the upgrade slots, back out into the inventory, main grid <-> hotbar. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int n = TileEntityFieldGeneratorSC.UPGRADE_SLOTS, hotbar = n + 27, end = inventorySlots.size();
        if (index >= n && field.isItemValidForSlot(0, original) && !field.allowed(player)) {
            return null;                            // strangers don't put upgrades in either
        }
        if (index < n) {
            if (!mergeItemStack(original, n, end, true)) {
                return null;
            }
        } else if (field.isItemValidForSlot(0, original)) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, n)
                    && !mergeItemStack(original, index < hotbar ? hotbar : n, index < hotbar ? end : hotbar, false)) {
                return null;
            }
        } else if (!mergeItemStack(original, index < hotbar ? hotbar : n, index < hotbar ? end : hotbar, false)) {
            return null;
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    /** Takes energy storage and transformer upgrades only; only the owner / access list may take them out (the field's own rule). */
    private static class SlotFieldUpgrade extends Slot {
        private final TileEntityFieldGeneratorSC field;

        SlotFieldUpgrade(TileEntityFieldGeneratorSC field, int index, int x, int y) {
            super(field, index, x, y);
            this.field = field;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return field.isItemValidForSlot(getSlotIndex(), stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return field.allowed(player);
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < TileEntityFieldGeneratorSC.UPGRADE_SLOTS && !field.allowed(player)) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    public TileEntityFieldGeneratorSC getField() {
        return field;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return field.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{field.getEnergyStored(), field.getMode().ordinal(),
                field.getNodeCount(), field.isActive() ? 1 : 0, field.getRange()});
    }

    /**
     * The first sync round carries every value in index order (IntSyncSC); until its last one
     * (range, index 4) has landed the others are still 0, and applying them early showed a frame
     * of "0 nodes / range 1". Later rounds only carry what changed, so from then on any of them
     * applies at once.
     */
    private boolean stateReady;

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id == 0) {
            field.setEnergyStoredClient(sync.value(0));
        } else if (id > 0) {
            if (id == sync.count() - 1) {
                stateReady = true;
            }
            if (stateReady) {
                field.setClientState(sync.value(1), sync.value(2), sync.value(3) != 0, sync.value(4));
            }
        }
    }
}
