package com.sc.inventory;

import com.sc.conduit.ItemFilterSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Setting up the item filter in the player's hand (Ender IO style): 5 or 10 ghost slots - a click
 * with an item puts a copy of it in (nothing is used up), a click with an empty hand clears it -
 * and the switches (buttons through the vanilla GUI-button packet). Everything is written straight
 * into the filter's NBT. The filter itself can't be moved while the screen is open.
 */
public class ContainerFilterSC extends Container {

    public static final int BTN_BLACKLIST = 0, BTN_IGNORE_META = 1, BTN_MATCH_NBT = 2, BTN_ORE_DICT = 3;
    public static final int GHOST_X = 8, GHOST_Y = 20, INV_X = 8, INV_Y = 84;

    private final EntityPlayer player;
    private final int heldSlot;
    private final int ghosts;
    private final InventoryBasic view;

    public ContainerFilterSC(EntityPlayer player, int heldSlot) {
        this.player = player;
        this.heldSlot = heldSlot;
        ItemStack filter = filter();
        this.ghosts = filter == null ? ItemFilterSC.BASIC_SLOTS : ItemFilterSC.slots(filter);
        this.view = new InventoryBasic("filter", false, ghosts);
        refreshView();
        for (int i = 0; i < ghosts; i++) {
            addSlotToContainer(new SlotGhost(view, i, GHOST_X + (i % 5) * 18, GHOST_Y + (i / 5) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    /** The filter being edited: whatever is in the slot that was selected when the screen opened. */
    public ItemStack filter() {
        ItemStack s = player.inventory.getStackInSlot(heldSlot);
        return ItemFilterSC.isFilter(s) ? s : null;
    }

    public int ghostCount() {
        return ghosts;
    }

    /** The ghost slots show the filter's examples. */
    private void refreshView() {
        ItemStack filter = filter();
        ItemStack[] entries = filter == null ? new ItemStack[ghosts] : ItemFilterSC.entries(filter);
        for (int i = 0; i < ghosts; i++) {
            view.setInventorySlotContents(i, i < entries.length ? entries[i] : null);
        }
    }

    private void setExample(int slot, ItemStack example) {
        ItemStack filter = filter();
        if (filter != null) {
            ItemFilterSC.setEntry(filter, slot, example);
            refreshView();
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < ghosts) {
            ItemStack cursor = player.inventory.getItemStack();
            setExample(slotId, cursor);       // a copy of one, or cleared by an empty hand
            return null;
        }
        // the filter stays where it is: not picked up, not swapped with a number key
        if (slotId >= ghosts && slotId < inventorySlots.size()) {
            Slot slot = (Slot) inventorySlots.get(slotId);
            if (slot.inventory == player.inventory && slot.getSlotIndex() == heldSlot) {
                return null;
            }
        }
        if (mode == 2 && button == heldSlot) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    /** Shift-click from the inventory: a copy goes into the first empty example slot. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < ghosts) {
            setExample(index, null);
            return null;
        }
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        for (int i = 0; i < ghosts; i++) {
            if (view.getStackInSlot(i) == null) {
                setExample(i, slot.getStack());
                break;
            }
        }
        return null;
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        ItemStack filter = filter();
        if (filter == null) {
            return false;
        }
        String key;
        switch (id) {
            case BTN_BLACKLIST: key = ItemFilterSC.BLACKLIST; break;
            case BTN_IGNORE_META: key = ItemFilterSC.IGNORE_META; break;
            case BTN_MATCH_NBT: key = ItemFilterSC.MATCH_NBT; break;
            case BTN_ORE_DICT: key = ItemFilterSC.ORE_DICT; break;
            default: return false;
        }
        if ((id == BTN_MATCH_NBT || id == BTN_ORE_DICT) && !ItemFilterSC.isAdvanced(filter)) {
            return false;
        }
        ItemFilterSC.setFlag(filter, key, !ItemFilterSC.flag(filter, key));
        return true;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return filter() != null;
    }

    /** An example slot: shows a copy, never holds a real item. */
    private static class SlotGhost extends Slot {
        SlotGhost(InventoryBasic inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }
}
