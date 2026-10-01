package com.sc.inventory;

import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.tileentity.TileEntityConduitBundleSC;

import com.sc.conduit.ConnectorInventorySC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The connector menu of one side of a conduit bundle (Ender IO style). Its buttons arrive through
 * the vanilla GUI-button packet (one byte): id = kind * 32 + action. Slots: this side's tube
 * extract filter, speed upgrade and insert filter (ConnectorInventorySC), then the player's
 * inventory. The screen moves the connector slots out of sight when they don't apply.
 */
public class ContainerConduitSC extends Container {

    public static final int MODE_NEXT = 0, MODE_PREV = 1, RS_NEXT = 2, RS_PREV = 3,
            OUT_COLOR_NEXT = 4, OUT_COLOR_PREV = 5, IN_COLOR_NEXT = 6, IN_COLOR_PREV = 7,
            PRIO_UP = 8, PRIO_DOWN = 9, PRIO_UP_10 = 10, PRIO_DOWN_10 = 11, ROUND_ROBIN = 12;
    private static final int ACTIONS = 32;

    private final TileEntityConduitBundleSC bundle;
    private final ForgeDirection side;

    /** Container slot numbers of the connector slots, and where the screen shows them. */
    public static final int SLOT_OUT_FILTER = 0, SLOT_SPEED = 1, SLOT_IN_FILTER = 2, CONNECTOR_SLOTS = 3;
    public static final int[][] SLOT_POS = {{9, 83}, {29, 83}, {61, 83}};
    public static final int INV_X = 18, INV_Y = 120;

    public ContainerConduitSC(TileEntityConduitBundleSC bundle, ForgeDirection side, net.minecraft.entity.player.InventoryPlayer playerInv) {
        this.bundle = bundle;
        this.side = side;
        ConnectorInventorySC inv = bundle.getConnectorItems();
        int[] which = {ConnectorInventorySC.OUT_FILTER, ConnectorInventorySC.SPEED, ConnectorInventorySC.IN_FILTER};
        for (int i = 0; i < CONNECTOR_SLOTS; i++) {
            addSlotToContainer(new SlotConnector(bundle, side, which[i], SLOT_POS[i][0], SLOT_POS[i][1]));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    /**
     * A filter slot holds one filter, the speed slot up to 15 upgrades. Something only goes in while
     * the slot is one the screen shows: the bundle still has its tube, this side is plugged into a
     * block, and the mode uses it (extract: extract filter and speed, insert: insert filter) -
     * otherwise a shift-click could put a filter where nothing uses it and nothing drops it.
     */
    public static class SlotConnector extends Slot {
        private final TileEntityConduitBundleSC bundle;
        private final ForgeDirection side;
        private final int which;

        SlotConnector(TileEntityConduitBundleSC bundle, ForgeDirection side, int which, int x, int y) {
            super(bundle.getConnectorItems(), ConnectorInventorySC.index(side, which), x, y);
            this.bundle = bundle;
            this.side = side;
            this.which = which;
        }

        public static boolean inUse(TileEntityConduitBundleSC bundle, ForgeDirection side, int which) {
            if (bundle.isInvalid() || !bundle.has(ConduitKind.TUBE) || bundle.getWorldObj() == null || !isConnector(bundle, side)) {
                return false;
            }
            ConduitMode m = bundle.mode(ConduitKind.TUBE, side);
            return which == ConnectorInventorySC.IN_FILTER ? m.inserts(ConduitKind.TUBE) : m.extracts(ConduitKind.TUBE);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return inUse(bundle, side, which) && inventory.isItemValidForSlot(getSlotIndex(), stack);
        }

        @Override
        public int getSlotStackLimit() {
            return getSlotIndex() % ConnectorInventorySC.PER_SIDE == ConnectorInventorySC.SPEED ? 15 : 1;
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
        if (index < CONNECTOR_SLOTS) {
            if (!mergeItemStack(original, CONNECTOR_SLOTS, inventorySlots.size(), true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, CONNECTOR_SLOTS)) {
            return null;
        }
        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    public static int buttonId(ConduitKind kind, int action) {
        return kind.ordinal() * ACTIONS + action;
    }

    /** Plugged into a block on this side (not linked to the next bundle): the full connector options apply. */
    public static boolean isConnector(TileEntityConduitBundleSC bundle, ForgeDirection side) {
        return !(bundle.getWorldObj().getTileEntity(bundle.xCoord + side.offsetX, bundle.yCoord + side.offsetY,
                bundle.zCoord + side.offsetZ) instanceof TileEntityConduitBundleSC);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player) || id < 0 || id / ACTIONS >= ConduitKind.values().length) {
            return false;
        }
        ConduitKind kind = ConduitKind.values()[id / ACTIONS];
        int action = id % ACTIONS;
        if (!bundle.has(kind)) {
            return false;
        }
        boolean connector = isConnector(bundle, side);
        ConduitMode mode = bundle.mode(kind, side);
        switch (action) {
            case MODE_NEXT:
            case MODE_PREV:
                if (connector) {
                    bundle.setMode(kind, side, mode.step(kind, true, action == MODE_NEXT ? 1 : -1));
                } else {
                    bundle.setModeLinked(kind, side, mode == ConduitMode.OFF ? ConduitMode.NORMAL : ConduitMode.OFF);
                }
                return true;
            default:
                break;
        }
        if (!connector) {
            return false;                   // the options below belong to a connector only
        }
        switch (action) {
            case RS_NEXT: bundle.setRedstoneMode(kind, side, bundle.redstoneMode(kind, side).step(1)); return true;
            case RS_PREV: bundle.setRedstoneMode(kind, side, bundle.redstoneMode(kind, side).step(-1)); return true;
            case OUT_COLOR_NEXT: bundle.setExtractColor(kind, side, bundle.extractColor(kind, side) + 1); return true;
            case OUT_COLOR_PREV: bundle.setExtractColor(kind, side, bundle.extractColor(kind, side) + 15); return true;
            case IN_COLOR_NEXT: bundle.setInsertColor(kind, side, bundle.insertColor(kind, side) + 1); return true;
            case IN_COLOR_PREV: bundle.setInsertColor(kind, side, bundle.insertColor(kind, side) + 15); return true;
            case PRIO_UP: bundle.setPriority(kind, side, bundle.priority(kind, side) + 1); return true;
            case PRIO_DOWN: bundle.setPriority(kind, side, bundle.priority(kind, side) - 1); return true;
            case PRIO_UP_10: bundle.setPriority(kind, side, bundle.priority(kind, side) + 10); return true;
            case PRIO_DOWN_10: bundle.setPriority(kind, side, bundle.priority(kind, side) - 10); return true;
            case ROUND_ROBIN: bundle.setRoundRobin(kind, side, !bundle.roundRobin(kind, side)); return true;
            default: return false;
        }
    }

    public TileEntityConduitBundleSC getBundle() {
        return bundle;
    }

    public ForgeDirection getSide() {
        return side;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !bundle.isInvalid() && bundle.getWorldObj() != null
                && bundle.getWorldObj().getTileEntity(bundle.xCoord, bundle.yCoord, bundle.zCoord) == bundle
                && player.getDistanceSq(bundle.xCoord + 0.5, bundle.yCoord + 0.5, bundle.zCoord + 0.5) <= 64;
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;                                   // a hotbar key can't put more than the slot takes
        }
        return super.slotClick(slotId, button, mode, player);
    }
}
