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
            BTN_PRIORITY = 70, BTN_COMPARATOR = 72;

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

        @Override
        public int getSlotStackLimit() {
            return 4;
        }

        @Override
        public int limitFor(ItemStack stack) {
            return Math.max(1, TileEntityEnergyConverterSC.moduleLimit(stack));
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
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
        if (slotIndex < tileSlots && !slot.canTakeStack(player)) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        if (slotIndex < tileSlots) {
            if (!mergeItemStack(original, tileSlots, inventorySlots.size(), true)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, tileSlots)) {
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
        return super.slotClick(slotId, button, mode, player);
    }
}
