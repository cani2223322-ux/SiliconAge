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

    public static final int G_FILTER = 0, G_BUFFER = 1, G_UPGRADES = 2, G_PLAYER = 3, G_LENS = 4, G_TOOLS = 5;
    public static final int FILTER_X = 44, FILTER_Y = 50, BUFFER_X = 44, BUFFER_Y = 80;
    /** 18 module slots as a 9 x 2 grid; the head, scanner and card in a row under it, each after its label. */
    public static final int UPGRADE_X = 44, UPGRADE_Y = 40, TOOLS_Y = 90, TOOL_COL = 78, TOOL_SLOT = 52;
    public static final int HEAD_X = 8 + TOOL_SLOT, SCANNER_X = 8 + TOOL_COL + TOOL_SLOT, CARD_X = 8 + 2 * TOOL_COL + TOOL_SLOT;
    public static final int LENS_X = 8, LENS_Y = 40;
    public static final int INV_X = 44, INV_Y = 157;
    /** The screen's content starts this much lower than its coordinates say (two rows of tabs above it). */
    public static final int TOP = 22;
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
        shownX = new int[inventorySlots.size()];
        group = new int[inventorySlots.size()];
        for (int i = 0; i < shownX.length; i++) {
            shownX[i] = ((Slot) inventorySlots.get(i)).xDisplayPosition;
            group[i] = i < FIRST_BUFFER ? G_FILTER : i < FIRST_UPGRADE ? G_BUFFER : i < FIRST_TOOLS ? G_UPGRADES
                    : i < FIRST_LENS ? G_TOOLS : i < FIRST_PLAYER ? G_LENS : G_PLAYER;
        }
    }

    public TileEntityQuarrySC getQuarry() {
        return quarry;
    }

    /** Client: only these slot groups on screen (the others off it, not hoverable or clickable). */
    @SideOnly(Side.CLIENT)
    public void setShown(boolean... groups) {
        for (int i = 0; i < shownX.length; i++) {
            ((Slot) inventorySlots.get(i)).xDisplayPosition = groups[group[i]] ? shownX[i] : -10000;
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
        if (slotId >= 0 && slotId < FIRST_PLAYER && !quarry.allowed(player)) {
            return null;                            // only the owner takes the output or changes the modules
        }
        return super.slotClick(slotId, button, mode, player);
    }

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
        int end = inventorySlots.size(), hotbar = FIRST_PLAYER + 27;
        if (index < FIRST_PLAYER) {
            if (!mergeItemStack(original, FIRST_PLAYER, end, true)) {
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
    private static final int COUNT = 19;
    private final IntSyncSC sync = new IntSyncSC(COUNT);

    private int value(int id) {
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
        } else if (id == 8 || id == 9) {
            com.sc.tileentity.TileEntityGeneratorSC.setTankClient(quarry.getTank(0), sync.value(8), sync.value(9));
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

    private static class SlotValid extends Slot {
        private final TileEntityQuarrySC quarry;

        SlotValid(TileEntityQuarrySC q, int index, int x, int y) {
            super(q, index, x, y);
            this.quarry = q;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return quarry.isItemValidForSlot(getSlotIndex(), stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return quarry.allowed(player);
        }
    }
}
