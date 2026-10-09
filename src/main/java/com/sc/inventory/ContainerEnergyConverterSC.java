package com.sc.inventory;

import com.sc.tileentity.TileEntityEnergyConverterSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

/**
 * The Energy Converter's screen: the charge slot, six expansion slots, the player's inventory (at the
 * 340 x 262 layout's places - the compact screen moves them), everything the screen shows synced
 * (TileEntityEnergyConverterSC.syncValues), and its buttons.
 */
public class ContainerEnergyConverterSC extends Container {

    /** Item positions of the large layout (GuiEnergyConverterSC). */
    public static final int CHARGE_X = 19, CHARGE_Y = 102, MOD_X = 9, MOD_Y = 30, MOD_STEP = 20, INV_X = 9, INV_Y = 183, HOTBAR_Y = 241;
    /** Buttons (sendEnchantPacket ids - a byte). */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_PAIR = 20, BTN_DIR = 30, BTN_MODE = 40, BTN_BUF = 50, BTN_FILTER = 60,
            BTN_PRIORITY = 70, BTN_COMPARATOR = 72, BTN_ALL = 75;

    private final TileEntityEnergyConverterSC te;
    private final IntSyncSC sync = new IntSyncSC(TileEntityEnergyConverterSC.SYNC_COUNT);
    private final int[] received = new int[TileEntityEnergyConverterSC.SYNC_COUNT];
    private final int tileSlots;

    public static int modX(int i) {
        return MOD_X + (i % 2) * MOD_STEP;
    }

    public static int modY(int i) {
        return MOD_Y + (i / 2) * MOD_STEP;
    }

    public ContainerEnergyConverterSC(InventoryPlayer playerInv, TileEntityEnergyConverterSC te) {
        this.te = te;
        addSlotToContainer(new Slot(te, TileEntityEnergyConverterSC.SLOT_CHARGE, CHARGE_X, CHARGE_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return TileEntityEnergyConverterSC.isChargeable(stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }

            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return ContainerEnergyConverterSC.this.te.allowed(player);   // БП-3: a stranger's double-click doesn't gather it either
            }
        });
        for (int i = 0; i < TileEntityEnergyConverterSC.MODULE_SLOTS; i++) {
            addSlotToContainer(new ModuleSlot(te, TileEntityEnergyConverterSC.FIRST_MODULE + i, modX(i), modY(i)));
        }
        tileSlots = inventorySlots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    /** An expansion slot: modules only, as many as count; one holding energy (storage, the pair's card) stays. */
    private final class ModuleSlot extends Slot implements SlotMergeSC.Limited {
        ModuleSlot(TileEntityEnergyConverterSC te, int index, int x, int y) {
            super(te, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return TileEntityEnergyConverterSC.isModule(stack);
        }

        /** Vanilla's clicks ask without a stack: the one on the cursor (going in), else the one in the slot. */
        @Override
        public int getSlotStackLimit() {
            ItemStack in = getStack(), s = clicking != null ? clicking : in;
            if (s == null) {
                return 4;
            }
            int lim = limitFor(s);
            if (clicking != null && in != null && clicking.isItemEqual(in) && ItemStack.areItemStackTagsEqual(clicking, in)) {
                lim = Math.max(lim, in.stackSize);  // an old stack past the limit: topping up must not pull the extra out
            }
            return lim;
        }

        @Override
        public int limitFor(ItemStack stack) {
            return Math.max(1, TileEntityEnergyConverterSC.moduleLimit(stack));
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            if (!te.allowed(player)) {
                return false;                       // БП-3: the owner's modules
            }
            ItemStack cur = player.inventory.getItemStack(), in = getStack();
            if (cur != null && in != null && cur.isItemEqual(in) && ItemStack.areItemStackTagsEqual(cur, in)) {
                return true;
            }
            int why = te.moduleRemoveBlock(getSlotIndex());
            if (why == 0) {
                return true;
            }
            if (!player.worldObj.isRemote && cur == null) {
                player.addChatComponentMessage(why == 1
                        ? new ChatComponentTranslation("sc.conv.msg.storage", String.valueOf(te.capacityWithout(getSlotIndex())))
                        : new ChatComponentTranslation("sc.conv.msg.card"));
            }
            return false;
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
        if (!te.allowed(player)) {                  // БП-3: a stranger sees the screen but changes nothing
            player.addChatComponentMessage(new ChatComponentTranslation("sc.conv.msg.owneronly", te.getOwner()));
            return false;
        }
        if (id == BTN_POWER) {
            te.setPowerOn(!te.isPowerOn());
            com.sc.util.SoundsSC.powerClick(te, te.isPowerOn());
            return true;
        }
        if (id == BTN_REDSTONE) {
            te.setRedstoneMode((te.getRedstoneMode() + 1) % 3);
            return true;
        }
        if (id >= BTN_PAIR && id < BTN_PAIR + 3) {
            int r = te.switchPair(id - BTN_PAIR);
            if (r == TileEntityEnergyConverterSC.SW_NO_ROOM) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.conv.msg.noroom", String.valueOf(te.refundPreview())));
            } else if (r == TileEntityEnergyConverterSC.SW_UNAVAILABLE) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.conv.msg.unavailable"));
            }
            return true;
        }
        if (id >= BTN_DIR && id < BTN_DIR + 3) {
            te.setDirection(id - BTN_DIR);
            return true;
        }
        if (id >= BTN_MODE && id < BTN_MODE + 6) {
            te.cycleMode(id - BTN_MODE);
            return true;
        }
        if (id >= BTN_BUF && id < BTN_BUF + 6) {
            te.cycleBuf(id - BTN_BUF);
            return true;
        }
        if (id >= BTN_FILTER && id < BTN_FILTER + 6) {
            te.cycleFilter(id - BTN_FILTER);
            return true;
        }
        if (id == BTN_PRIORITY) {
            te.cyclePriority();
            return true;
        }
        if (id == BTN_COMPARATOR) {
            te.cycleComparator();
            return true;
        }
        if (id >= BTN_ALL && id <= BTN_ALL + TileEntityEnergyConverterSC.ALL_AUTO) {
            te.setAllFaces(id - BTN_ALL);
            return true;
        }
        return false;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, te.syncValues());
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id >= 0) {
            received[id] = sync.value(id);
            te.syncClient(id, sync.value(id), received);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        boolean allowed = te.allowed(player);
        if (slotIndex < tileSlots && (!allowed || !slot.canTakeStack(player))) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        if (slotIndex < tileSlots) {
            if (!mergeItemStack(original, tileSlots, inventorySlots.size(), true)) {
                return null;
            }
        } else if (!(allowed && SlotMergeSC.mergeValid(inventorySlots, original, 0, tileSlots))) {   // a stranger: inventory <-> hotbar only
            int hotbarStart = tileSlots + 27;
            boolean moved = slotIndex < hotbarStart
                    ? mergeItemStack(original, hotbarStart, inventorySlots.size(), false)
                    : mergeItemStack(original, tileSlots, hotbarStart, false);
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

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;
        }
        if (slotId >= 0 && slotId < tileSlots && !te.allowed(player)) {
            return null;                            // БП-3: the owner's slots
        }
        clicking = player.inventory.getItemStack();
        try {
            return super.slotClick(slotId, button, mode, player);
        } finally {
            clicking = null;
        }
    }

    /** The cursor's stack during a click: a module slot's limit is that module's (ModuleSlot.getSlotStackLimit). */
    private ItemStack clicking;

    /** Double-click collecting (mode 6) skips a module that can't come out now (decrStackSize would give null). */
    @Override
    public boolean func_94530_a(ItemStack stack, Slot slot) {
        if (slot instanceof ModuleSlot && te.moduleRemoveBlock(slot.getSlotIndex()) != 0) {
            return false;
        }
        return super.func_94530_a(stack, slot);
    }
}
