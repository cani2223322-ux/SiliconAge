package com.sc.inventory;

import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Energy storage screen: one weapon / armor charging slot, the player inventory, energy + flow sync. */
public class ContainerEnergyStorageSC extends Container {

    /** On the large screen's holo panel, left of the readings (GuiEnergyStorageSC / GuiBigSC). */
    public static final int SLOT_X = 16, SLOT_Y = 30;
    /** The discharge slot, at the foot of the charge column, beside it. */
    public static final int DIS_X = 34, DIS_Y = 94;
    /** The charge slots one under another down the left edge, 20 apart (a charge bar under each). */
    public static final int CHARGE_X = 14, CHARGE_Y = 34, CHARGE_STEP = 20;

    public static int chargeX(int k) {
        return CHARGE_X;
    }

    public static int chargeY(int k) {
        return CHARGE_Y + k * CHARGE_STEP;
    }

    /** The storage's own slots in this container: its charge slots, the discharge slot, the upgrades. */
    private final int tileSlots;

    private final TileEntityEnergyStorageSC storage;
    private final IntSyncSC sync = new IntSyncSC(4);   // energy, flow per tick, the power switch, the adaptive tier
    /** The power switch and the redstone mode (GuiPowerSC). */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11;
    /** A charge pad's "charge: everyone / owner and team" switch (ЭН-7). */
    public static final int BTN_PAD_MODE = 12;

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        if (id == BTN_POWER) {
            storage.setPowerOn(!storage.isPowerOn());
            com.sc.util.SoundsSC.powerClick(storage, storage.isPowerOn());
            return true;
        }
        if (id == BTN_REDSTONE) {
            storage.setRedstoneMode((storage.getRedstoneMode() + 1) % 3);
            return true;
        }
        if (id == BTN_PAD_MODE && storage instanceof com.sc.tileentity.TileEntityChargePadSC) {
            com.sc.tileentity.TileEntityChargePadSC pad = (com.sc.tileentity.TileEntityChargePadSC) storage;
            String name = player.getCommandSenderName();
            if (pad.getOwner().isEmpty()) {
                pad.setOwner(name);                        // a pad from before owners: the first to switch it claims it
            }
            if (!pad.getOwner().equalsIgnoreCase(name) && !player.capabilities.isCreativeMode) {
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.pad.owneronly", pad.getOwner()));
                return false;
            }
            pad.setOwnerOnly(!pad.isOwnerOnly());          // marks the block for update: the screen follows
            return true;
        }
        return false;
    }

    public ContainerEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        this.storage = storage;
        for (int k = 0; k < storage.chargeSlots(); k++) {
            final int index = TileEntityEnergyStorageSC.chargeSlotIndex(k);
            addSlotToContainer(new Slot(storage, index, chargeX(k), chargeY(k)) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return ContainerEnergyStorageSC.this.storage.isItemValidForSlot(index, stack);   // chargeable, and of the block's tier or lower
                }

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            });
        }
        addSlotToContainer(new Slot(storage, TileEntityEnergyStorageSC.SLOT_DISCHARGE, DIS_X, DIS_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return TileEntityEnergyStorageSC.isDischargeable(stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });
        for (int i = 0; i < TileEntityEnergyStorageSC.UPGRADE_SLOTS; i++) {
            final int upgSlot = TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + i;
            addSlotToContainer(new Slot(storage, upgSlot, GuiBigSC.UPG_X + i * 18, GuiBigSC.UPG_Y) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return ContainerEnergyStorageSC.this.storage.acceptsUpgradeHere(stack);   // the splitter from HV, none in a pad
                }

                /** A capacity upgrade stays while the charge wouldn't fit without it. */
                @Override
                public boolean canTakeStack(EntityPlayer player) {
                    TileEntityEnergyStorageSC st = ContainerEnergyStorageSC.this.storage;
                    ItemStack cur = player.inventory.getItemStack(), in = getStack();
                    if (cur != null && in != null && cur.isItemEqual(in) && ItemStack.areItemStackTagsEqual(cur, in)) {
                        return true;                       // putting more of it in, not taking it out
                    }
                    if (st.canRemoveUpgrade(upgSlot)) {
                        return true;
                    }
                    if (!player.worldObj.isRemote && cur == null) {   // not on a double click's sweep
                        player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(
                                "sc.storage.upgrade.discharge", st.capacityWithout(upgSlot)));
                    }
                    return false;
                }
            });
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

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return storage.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{storage.getEnergyStored(), storage.getFlowPerTick(), storage.powerFlags(), storage.adaptiveSync()});
        com.sc.energy.Tier out = storage.outputTier();
        if (lastOutput != null && out.ordinal() > lastOutput.ordinal()) {   // a Transformer upgrade went in
            for (Object o : crafters) {
                if (o instanceof EntityPlayer) {
                    com.sc.energy.CableWarningSC.outputRaised(storage, (EntityPlayer) o);
                }
            }
        }
        lastOutput = out;
    }

    /** The output tier last seen (server side): a rise warns about a weaker cable at the front. */
    private com.sc.energy.Tier lastOutput;

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id == 0) {
            storage.setEnergyStoredClient(sync.value(0));
        } else if (id == 1) {
            storage.setFlowClient(sync.value(1));
        } else if (id == 2) {
            storage.setPowerFlagsClient(sync.value(2));
        } else if (id == 3) {
            storage.setAdaptiveClient(sync.value(3));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
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
            return null;                                   // a hotbar key can't put more than the slot takes
        }
        return super.slotClick(slotId, button, mode, player);
    }
}
