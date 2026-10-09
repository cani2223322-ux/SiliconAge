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
    private final IntSyncSC sync = new IntSyncSC(7);  // energy, mode, node count, active, range, EU charged / s, players charged

    // Button ids for enchantItem (the vanilla GUI-button packet, no custom networking needed).
    public static final int BTN_RANGE_MINUS_16 = 0, BTN_RANGE_MINUS_1 = 1, BTN_RANGE_PLUS_1 = 2,
            BTN_RANGE_PLUS_16 = 3, BTN_MODE = 4, BTN_COLOR = 5, BTN_REDSTONE = 6, BTN_FILTER = 7,
            BTN_CHARGE_MODE = 8, BTN_RESERVE_MINUS = 9, BTN_RESERVE_PLUS = 30,
            BTN_OUTLINE = 31, BTN_ANIM = 32, BTN_BRIGHT = 33, BTN_BELOW_MINUS = 34, BTN_BELOW_PLUS = 35, BTN_POWER = 36,
            BTN_BATTERY_MODE = 37;
    /** Switches: BTN_FLAG_BASE + the bit's index (TileEntityFieldGeneratorSC.F_*). */
    public static final int BTN_FLAG_BASE = 10, FLAG_COUNT = 18;

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
            case BTN_POWER: field.togglePower(); return true;
            case BTN_BATTERY_MODE: field.cycleBatteryMode(); return true;
            case BTN_FILTER: field.cycleFilter(); return true;
            case BTN_CHARGE_MODE: field.cycleChargeMode(); return true;
            case BTN_RESERVE_MINUS: field.adjustChargeReserve(-TileEntityFieldGeneratorSC.RESERVE_STEP); return true;
            case BTN_RESERVE_PLUS: field.adjustChargeReserve(TileEntityFieldGeneratorSC.RESERVE_STEP); return true;
            case BTN_OUTLINE: field.cycleOutline(); return true;
            case BTN_ANIM: field.cycleAnim(); return true;
            case BTN_BRIGHT: field.cycleBrightness(); return true;
            case BTN_BELOW_MINUS: field.adjustChargeBelow(-TileEntityFieldGeneratorSC.CHARGE_BELOW_STEP); return true;
            case BTN_BELOW_PLUS: field.adjustChargeBelow(TileEntityFieldGeneratorSC.CHARGE_BELOW_STEP); return true;
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
        // the battery slot under the gauge - last, on every tab; only the owner / access list puts it in
        // or takes it out (slotClick refuses a stranger's every click on it, transferStackInSlot never fills it for them)
        final TileEntityFieldGeneratorSC f = field;
        addSlotToContainer(new SlotBatterySC(field, TileEntityFieldGeneratorSC.SLOT_BATTERY, BATTERY_X, BATTERY_Y) {
            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return f.allowed(player);
            }
        });
        shownX = new int[inventorySlots.size()];
        for (int i = 0; i < shownX.length; i++) {
            shownX[i] = ((Slot) inventorySlots.get(i)).xDisplayPosition;
        }
    }

    /** Where the Upgrades tab draws its slots (GuiFieldGeneratorSC draws the frames there). */
    public static final int UPGRADE_X = 89, UPGRADE_Y = 48, INV_X = 44, INV_Y = 144;
    /** The battery slot's item under the gauge (GuiFieldGeneratorSC: gauge x 214, bottom 106). */
    public static final int BATTERY_X = 217, BATTERY_Y = 110;
    private final int[] shownX;

    /** Client: the slots on the Upgrades tab, off-screen (not hoverable or clickable) on the others. */
    @SideOnly(Side.CLIENT)
    public void setSlotsShown(boolean shown) {
        for (int i = 0; i < shownX.length; i++) {
            boolean always = i == shownX.length - 1;                   // the battery slot
            ((Slot) inventorySlots.get(i)).xDisplayPosition = shown || always ? shownX[i] : -10000;
        }
    }

    /**
     * Shift-click: upgrades into the upgrade slots, back out into the inventory, main grid <-> hotbar.
     * A stranger only moves things within their own inventory (the field's slots are the owner's).
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int n = TileEntityFieldGeneratorSC.UPGRADE_SLOTS, hotbar = n + 27, battery = inventorySlots.size() - 1, end = battery;
        boolean may = field.allowed(player);
        if (index == battery) {
            if (!may || !mergeItemStack(original, n, end, true)) {
                return null;
            }
            slot.putStack(original.stackSize == 0 ? null : original);
            return result;
        }
        if (index < n && !may) {
            return null;
        }
        if (may && com.sc.item.BatteryFeedSC.accepts(original) && !((Slot) inventorySlots.get(battery)).getHasStack()) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, battery, battery + 1)) {
                return null;
            }
            slot.putStack(original.stackSize == 0 ? null : original);
            return result;
        }
        if (index < n) {
            if (!mergeItemStack(original, n, end, true)) {
                return null;
            }
        } else if (may && field.isItemValidForSlot(0, original)) {
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
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;                                   // a hotbar key can't put more than the slot takes
        }
        // a stranger: no click of any kind (put, take, hotbar key, drag, drop) on the field's own slots -
        // the battery's slot as much as the upgrades'; their own inventory stays theirs to sort
        boolean fieldSlot = slotId >= 0 && slotId < TileEntityFieldGeneratorSC.UPGRADE_SLOTS || slotId == inventorySlots.size() - 1;
        if (fieldSlot && !field.allowed(player)) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    /** What the access list looked like when last sent to the screen's viewers (null: not yet). */
    private String accessSent;

    /** The screen's first sync: the access list to this player too (only an allowed one sees it). */
    @Override
    public void addCraftingToCrafters(net.minecraft.inventory.ICrafting crafter) {
        super.addCraftingToCrafters(crafter);
        sendAccess(crafter);
    }

    /** The access list rides only here, to the screen's viewers - the block's description packet leaves it out. */
    private void sendAccess(Object crafter) {
        if (crafter instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP p = (net.minecraft.entity.player.EntityPlayerMP) crafter;
            p.playerNetServerHandler.sendPacket(field.accessPacket(field.allowed(p)));
        }
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
        String now = field.getOwner() + "|" + field.getAccess();
        if (!now.equals(accessSent)) {
            accessSent = now;
            for (Object crafter : crafters) {
                sendAccess(crafter);
            }
        }
        sync.send(this, crafters, new int[]{field.getEnergyStored(), field.getMode().ordinal(),
                field.getNodeCount(), field.isActive() ? 1 : 0, field.getRange(), field.getChargedLastSecond(), field.getPlayersLastSecond()});
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
                field.setChargeStatsClient(sync.value(5), sync.value(6));
            }
        }
    }
}
