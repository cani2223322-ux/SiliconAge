package com.sc.inventory;

import com.sc.tileentity.TileEntityQuarrySC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * The quarry's screen: 9 filter examples (ghost slots, like the tube filter's), the 27-slot
 * buffer, 8 upgrade slots, the drill head, scanner and area card slots, the player's inventory.
 * GuiQuarrySC shows only the groups of the open tab (setShown) - the others sit off-screen.
 */
public class ContainerQuarrySC extends Container {

    public static final int G_FILTER = 0, G_BUFFER = 1, G_UPGRADES = 2, G_PLAYER = 3, G_LENS = 4, G_TOOLS = 5, G_BATTERY = 6;
    /** The battery slot's item, under the gauge (GuiQuarrySC's gauge column, content coordinates). */
    public static final int BATTERY_X = 217, BATTERY_Y = 132 - 14;
    public static final int FILTER_X = 44, FILTER_Y = 61, BUFFER_X = 44, BUFFER_Y = 91;
    /** 18 module slots as a 9 x 2 grid; the head, scanner and card in a row under it, each over its label. */
    public static final int UPGRADE_X = 14, UPGRADE_Y = 38, TOOLS_Y = 84, TOOL_COL = 36;
    public static final int HEAD_X = 14, SCANNER_X = 14 + TOOL_COL, CARD_X = 14 + 2 * TOOL_COL;
    public static final int LENS_X = 14, LENS_Y = 38;
    public static final int INV_X = 44, INV_Y = 166;
    /** Slot y's are counted from here (the title strip and the row of tabs above it). */
    public static final int TOP = 14;
    public static final int FIRST_BUFFER = TileEntityQuarrySC.FILTER_SLOTS, FIRST_UPGRADE = FIRST_BUFFER + TileEntityQuarrySC.BUFFER,
            FIRST_TOOLS = FIRST_UPGRADE + TileEntityQuarrySC.UPGRADES, FIRST_LENS = FIRST_TOOLS + 3,
            FIRST_PLAYER = FIRST_LENS + TileEntityQuarrySC.LENSES;

    private final TileEntityQuarrySC quarry;
    private final InventoryBasic view = new InventoryBasic("filter", false, TileEntityQuarrySC.FILTER_SLOTS);
    private final int[] shownX, group;

    public ContainerQuarrySC(InventoryPlayer playerInv, TileEntityQuarrySC quarry) {
        this.quarry = quarry;
        for (int i = 0; i < TileEntityQuarrySC.FILTER_SLOTS; i++) {
            view.setInventorySlotContents(i, quarry.getFilter()[i]);
            addSlotToContainer(new SlotGhost(view, i, FILTER_X + i * 18, FILTER_Y + TOP));
        }
        for (int i = 0; i < TileEntityQuarrySC.BUFFER; i++) {
            addSlotToContainer(new SlotBuffer(quarry, i, BUFFER_X + i % 9 * 18, BUFFER_Y + TOP + i / 9 * 18));
        }
        for (int i = 0; i < TileEntityQuarrySC.UPGRADES; i++) {
            addSlotToContainer(new SlotValid(quarry, TileEntityQuarrySC.FIRST_UPGRADE + i, UPGRADE_X + i % 9 * 18, UPGRADE_Y + TOP + i / 9 * 18));
        }
        addSlotToContainer(new SlotValid(quarry, TileEntityQuarrySC.SLOT_HEAD, HEAD_X, TOOLS_Y + TOP));
        addSlotToContainer(new SlotValid(quarry, TileEntityQuarrySC.SLOT_SCANNER, SCANNER_X, TOOLS_Y + TOP));
        addSlotToContainer(new SlotValid(quarry, TileEntityQuarrySC.SLOT_CARD, CARD_X, TOOLS_Y + TOP));
        for (int i = 0; i < TileEntityQuarrySC.LENSES; i++) {
            addSlotToContainer(new SlotValid(quarry, TileEntityQuarrySC.FIRST_LENS + i, LENS_X + i % 4 * 18, LENS_Y + TOP + i / 4 * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, INV_Y + TOP + 58));
        }
        final TileEntityQuarrySC q = quarry;
        addSlotToContainer(new SlotBatterySC(quarry, TileEntityQuarrySC.SLOT_BATTERY, BATTERY_X, BATTERY_Y + TOP) {   // last: every tab
            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return q.allowed(player);           // a stranger's double-click (mode 6) doesn't gather it either
            }
        });
        shownX = new int[inventorySlots.size()];
        group = new int[inventorySlots.size()];
        for (int i = 0; i < shownX.length; i++) {
            shownX[i] = ((Slot) inventorySlots.get(i)).xDisplayPosition;
            group[i] = i < FIRST_BUFFER ? G_FILTER : i < FIRST_UPGRADE ? G_BUFFER : i < FIRST_TOOLS ? G_UPGRADES
                    : i < FIRST_LENS ? G_TOOLS : i < FIRST_PLAYER ? G_LENS : i < FIRST_PLAYER + 36 ? G_PLAYER : G_BATTERY;
        }
    }

    public TileEntityQuarrySC getQuarry() {
        return quarry;
    }

    /** Client: only these slot groups on screen (the others off it, not hoverable or clickable). */
    @SideOnly(Side.CLIENT)
    public void setShown(boolean... groups) {
        for (int i = 0; i < shownX.length; i++) {
            boolean on = group[i] >= groups.length || groups[group[i]];                // the battery: always
            ((Slot) inventorySlots.get(i)).xDisplayPosition = on ? shownX[i] : -10000;
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return quarry.isUseableByPlayer(player);
    }

    // ---- filter examples: a copy of the cursor's item, never the item itself ----

    private void setExample(int i, ItemStack s, EntityPlayer player) {
        if (!quarry.allowed(player)) {
            return;                                 // a stranger's click changes nothing, not even their view
        }
        ItemStack one = s == null ? null : s.copy();
        if (one != null) {
            one.stackSize = 1;
        }
        view.setInventorySlotContents(i, one);
        if (!player.worldObj.isRemote && quarry.allowed(player)) {
            quarry.setFilterStack(i, one);
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < FIRST_BUFFER) {
            setExample(slotId, player.inventory.getItemStack(), player);
            return null;
        }
        if ((slotId >= 0 && slotId < FIRST_PLAYER || slotId == inventorySlots.size() - 1) && !quarry.allowed(player)) {
            return null;                            // only the owner takes the output or changes the modules
        }
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;                            // a hotbar key would put a whole stack past the slot's limit
        }
        clicking = player.inventory.getItemStack();
        try {
            return super.slotClick(slotId, button, mode, player);
        } finally {
            clicking = null;
        }
    }

    /** The cursor's stack during a click: an empty module / lens slot's limit is that item's (SlotValid). */
    private ItemStack clicking;

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < FIRST_BUFFER) {
            setExample(index, null, player);
            return null;
        }
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        if (!quarry.allowed(player)) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int battery = inventorySlots.size() - 1, end = battery, hotbar = FIRST_PLAYER + 27;
        if (index < FIRST_PLAYER || index == battery) {
            if (!mergeItemStack(original, FIRST_PLAYER, end, true)) {
                return null;
            }
        } else if (com.sc.item.BatteryFeedSC.accepts(original) && !((Slot) inventorySlots.get(battery)).getHasStack()) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, battery, battery + 1)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, FIRST_UPGRADE, FIRST_PLAYER)
                && !mergeItemStack(original, index < hotbar ? hotbar : FIRST_PLAYER, index < hotbar ? end : hotbar, false)) {
            return null;
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    // ---- live numbers ----

    /** 0..12 as before (8 / 9: the first compartment), 13..18: the other compartments' fluid and amount. */
    private static final int COUNT = 22;             // 19, 20: the fluid vein's counters; 21: blocks a private field kept
    private final IntSyncSC sync = new IntSyncSC(COUNT);

    private int value(int id) {
        if (id == 21) {
            return quarry.getSkippedPrivate();
        }
        if (id == 19) {
            return quarry.getFluidVeinLast();
        }
        if (id == 20) {
            return quarry.getFluidVeinTotal();
        }
        if (id >= 13) {
            net.minecraftforge.fluids.FluidTank t = quarry.getTank(1 + (id - 13) / 2);
            FluidStack f = t.getFluid();
            return (id - 13) % 2 == 0 ? (f == null ? 0 : f.getFluidID()) : t.getFluidAmount();
        }
        FluidStack p = quarry.getTank(0).getFluid();
        switch (id) {
            case 0: return quarry.getEnergyStored();
            case 1: return quarry.getStatus().ordinal();
            case 2: return quarry.getLayerY();
            case 3: return quarry.getCursor();
            case 4: return (int) Math.min(Integer.MAX_VALUE, quarry.getMined());
            case 5: return quarry.getXp();
            case 6: return quarry.getLastCost();
            case 7: return quarry.isRunning() ? 1 : 0;
            case 8: return p == null ? 0 : p.getFluidID();
            case 9: return quarry.getTank(0).getFluidAmount();
            case 10: return quarry.getWater().getFluidAmount();
            case 12: return quarry.scanPercent();
            default: return (int) Math.min(Integer.MAX_VALUE, quarry.blocksLeft());
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] v = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            v[i] = value(i);
        }
        sync.send(this, crafters, v);
    }

    /** Blocks left, as the server counted them. */
    public int blocksLeftClient;

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id == 0) {
            quarry.setEnergyStoredClient(sync.value(0));
        } else if (id == 21) {
            quarry.setSkippedPrivateClient(sync.value(21));
        } else if (id == 8 || id == 9) {
            com.sc.tileentity.TileEntityGeneratorSC.setTankClient(quarry.getTank(0), sync.value(8), sync.value(9));
        } else if (id == 19 || id == 20) {
            quarry.setFluidVeinClient(sync.value(19), sync.value(20));
        } else if (id >= 13) {
            int t = (id - 13) / 2;
            com.sc.tileentity.TileEntityGeneratorSC.setTankClient(quarry.getTank(1 + t), sync.value(13 + t * 2), sync.value(14 + t * 2));
        } else if (id == 10) {
            com.sc.tileentity.TileEntityGeneratorSC.setTankClient(quarry.getWater(),
                    net.minecraftforge.fluids.FluidRegistry.getFluidID("water"), sync.value(10));
        } else if (id == 11) {
            blocksLeftClient = sync.value(11);
        } else if (id == 12) {
            quarry.setScanPercentClient(sync.value(12));
        } else {
            quarry.setLiveClient(sync.value(1), sync.value(2), sync.value(3), sync.value(4), sync.value(5), sync.value(6), sync.value(7) != 0);
        }
    }

    // ---- slots ----

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

    /** The buffer: take from it, never put into it. */
    private static class SlotBuffer extends Slot {
        SlotBuffer(TileEntityQuarrySC q, int index, int x, int y) {
            super(q, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return ((TileEntityQuarrySC) inventory).allowed(player);
        }
    }

    private class SlotValid extends Slot implements SlotMergeSC.Limited {
        private final TileEntityQuarrySC quarry;

        SlotValid(TileEntityQuarrySC q, int index, int x, int y) {
            super(q, index, x, y);
            this.quarry = q;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return quarry.isItemValidForSlot(getSlotIndex(), stack);
        }

        /** One lens a slot; a module slot holds as many of a module as work (its kind's max). */
        @Override
        public int limitFor(ItemStack s) {
            int i = getSlotIndex();
            if (i >= TileEntityQuarrySC.FIRST_LENS && i < TileEntityQuarrySC.FIRST_LENS + TileEntityQuarrySC.LENSES) {
                return 1;
            }
            if (s != null && s.getItem() instanceof com.sc.item.ItemQuarryModuleSC
                    && i >= TileEntityQuarrySC.FIRST_UPGRADE && i < TileEntityQuarrySC.FIRST_UPGRADE + TileEntityQuarrySC.UPGRADES) {
                return com.sc.item.ItemQuarryModuleSC.kindOf(s).max;
            }
            return super.getSlotStackLimit();
        }

        /** Vanilla's clicks ask without a stack: the one on the cursor (going in), else the one in the slot. */
        @Override
        public int getSlotStackLimit() {
            return limitFor(clicking != null ? clicking : getStack());
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return quarry.allowed(player);
        }

        /**
         * A module topped up in place (a click onto the stack, SlotMergeSC's shift-click) only grows
         * stackSize - route it through the quarry so the frame / zone reach the other players too.
         */
        @Override
        public void onSlotChanged() {
            int i = getSlotIndex();
            if (i >= TileEntityQuarrySC.FIRST_UPGRADE && i < TileEntityQuarrySC.FIRST_UPGRADE + TileEntityQuarrySC.UPGRADES) {
                quarry.setInventorySlotContents(i, getStack());
            } else {
                super.onSlotChanged();
            }
        }
    }
}
