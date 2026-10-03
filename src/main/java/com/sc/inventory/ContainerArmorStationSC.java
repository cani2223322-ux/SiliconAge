package com.sc.inventory;

import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.util.ArmorGasSC.Gas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/** The Armour Service Station's screen: the four armour slots, the four module slots, the player's inventory, the numbers synced. */
public class ContainerArmorStationSC extends Container {

    /** BTN_GAS + gas: its check box; BTN_CLEAR + gas: pour its tank out for EU. */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_FILL = 12, BTN_HELIUM = 13, BTN_GAS = 20, BTN_CLEAR = 30;
    /** The screen's size (the station's own layout, wider than GuiBigSC's). */
    public static final int W = 400, H = 292;
    /** The armour slots (item coordinates): a column at the left of the screen, helmet on top. */
    public static final int PIECE_X = 15, PIECE_Y = 30, PIECE_STEP = 32;
    /** The module row (item coordinates of the first) and the player's inventory, centred. */
    public static final int UPG_X = 48, UPG_Y = 176, INV_X = 119, INV_Y = 210, HOTBAR_Y = 268;

    private static final int GASES = Gas.values().length;
    private final TileEntityArmorStationSC te;
    /** energy, status, players, settings, power flags, then per gas the amount, per gas the capacity, per gas the tank. */
    private static final int TANK_BASE = 5 + 2 * GASES;
    private final IntSyncSC sync = new IntSyncSC(TANK_BASE + GASES);

    public ContainerArmorStationSC(InventoryPlayer playerInv, TileEntityArmorStationSC te) {
        this.te = te;
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS; i++) {
            addSlotToContainer(new SlotPiece(te, i, PIECE_X, PIECE_Y + i * PIECE_STEP));
        }
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {     // the module row under the screen, as a machine's
            addSlotToContainer(new SlotModule(te, TileEntityArmorStationSC.FIRST_UPGRADE_SLOT + i, UPG_X + i * 18, UPG_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    /** One piece of the suit: only the mod's armour of this slot's type, one at a time; an empty slot shows the vanilla outline. */
    public static class SlotPiece extends Slot {
        private final int type;

        public SlotPiece(IInventory inv, int type, int x, int y) {
            super(inv, type, x, y);
            this.type = type;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return TileEntityArmorStationSC.fits(type, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getBackgroundIconIndex() {
            return ItemArmor.func_94602_b(type);
        }
    }

    /** A module slot: only the modules the station takes (TileEntityArmorStationSC.acceptsModule). */
    public static class SlotModule extends Slot {
        public SlotModule(IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return inventory.isItemValidForSlot(getSlotIndex(), stack);
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
        if (id >= BTN_GAS && id < BTN_GAS + GASES) {
            te.toggleGas(Gas.values()[id - BTN_GAS]);
            return true;
        }
        if (id >= BTN_CLEAR && id < BTN_CLEAR + GASES) {
            te.clearTank(Gas.values()[id - BTN_CLEAR]);
            return true;
        }
        switch (id) {
            case BTN_POWER:
                te.setPowerOn(!te.isPowerOn());
                com.sc.util.SoundsSC.powerClick(te, te.isPowerOn());
                return true;
            case BTN_REDSTONE: te.setRedstoneMode((te.getRedstoneMode() + 1) % 3); return true;
            case BTN_FILL: te.toggleFillGases(); return true;
            case BTN_HELIUM: te.toggleHeliumOnly(); return true;
            default: return false;
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] v = new int[sync.count()];
        v[0] = te.getEnergyStored();
        v[1] = te.getStatus();
        v[2] = te.getPlayers();
        v[3] = te.settingsFlags();
        v[4] = te.powerFlags();
        for (Gas g : Gas.values()) {
            v[5 + g.ordinal()] = te.shownAmount(g);
            v[5 + GASES + g.ordinal()] = te.shownCapacity(g);
            v[TANK_BASE + g.ordinal()] = te.tankAmount(g);          // the gas is the tank's own: the amount is enough
        }
        sync.send(this, crafters, v);
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
        } else if (id <= 3) {
            te.setScreenClient(sync.value(1), sync.value(2), sync.value(3));
        } else if (id >= TANK_BASE) {
            te.setTankClient(id - TANK_BASE, sync.value(id));
        } else {
            int g = (id - 5) % GASES;
            te.setShownClient(g, sync.value(5 + g), sync.value(5 + GASES + g));
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
        int pieces = TileEntityArmorStationSC.SLOTS, own = TileEntityArmorStationSC.ALL_SLOTS, end = inventorySlots.size(),
                hotbar = own + 27;
        if (index < own) {
            if (!mergeItemStack(original, own, end, true)) {
                return null;
            }
        } else if (TileEntityArmorStationSC.acceptsModule(original)
                && SlotMergeSC.mergeValid(inventorySlots, original, TileEntityArmorStationSC.FIRST_UPGRADE_SLOT, own)) {
            // a module: into the module row (what didn't fit stays where it was)
        } else {
            boolean moved = false;
            for (int i = 0; i < pieces && !moved; i++) {
                Slot target = (Slot) inventorySlots.get(i);
                if (!target.getHasStack() && target.isItemValid(original)) {
                    target.putStack(original.splitStack(1));
                    moved = true;
                }
            }
            if (!moved && !mergeItemStack(original, index < hotbar ? hotbar : own, index < hotbar ? end : hotbar, false)) {
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

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }
}
