package com.sc.conduit;

import com.sc.item.ItemTubeSpeedSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The item slots of a bundle's tube connectors, three per side (Ender IO style): the extract
 * filter, the insert filter and the speed upgrades. A separate IInventory on purpose - if the
 * bundle itself were one, hoppers and other mods' pipes would stuff things into it.
 */
public class ConnectorInventorySC implements IInventory {

    public static final int OUT_FILTER = 0, IN_FILTER = 1, SPEED = 2, PER_SIDE = 3;

    private final TileEntity owner;
    private final ItemStack[] slots = new ItemStack[6 * PER_SIDE];

    public ConnectorInventorySC(TileEntity owner) {
        this.owner = owner;
    }

    public static int index(ForgeDirection side, int which) {
        return side.ordinal() * PER_SIDE + which;
    }

    public ItemStack get(ForgeDirection side, int which) {
        return slots[index(side, which)];
    }

    /** Speed upgrades on that side (0..15). */
    public int speed(ForgeDirection side) {
        ItemStack s = get(side, SPEED);
        return s == null ? 0 : s.stackSize;
    }

    public boolean isEmpty() {
        for (ItemStack s : slots) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }

    /** Takes everything out (the tube is being removed). */
    public java.util.List<ItemStack> clear() {
        java.util.List<ItemStack> out = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                out.add(slots[i]);
                slots[i] = null;
            }
        }
        markDirty();
        return out;
    }

    /** Copies of everything in it (drops when the whole block goes). */
    public java.util.List<ItemStack> contents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<ItemStack>();
        for (ItemStack s : slots) {
            if (s != null) {
                out.add(s.copy());
            }
        }
        return out;
    }

    @Override
    public int getSizeInventory() {
        return slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slots[slot] == null) {
            return null;
        }
        ItemStack out = slots[slot].splitStack(Math.min(amount, slots[slot].stackSize));
        if (slots[slot].stackSize <= 0) {
            slots[slot] = null;
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        slots[slot] = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.connector";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        if (owner.getWorldObj() != null) {
            owner.markDirty();
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return slot % PER_SIDE == SPEED ? stack.getItem() instanceof ItemTubeSpeedSC : ItemFilterSC.isFilter(stack);
    }

    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                NBTTagCompound e = new NBTTagCompound();
                e.setInteger("Slot", i);
                slots[i].writeToNBT(e);
                list.appendTag(e);
            }
        }
        nbt.setTag("ConnectorItems", list);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = null;
        }
        NBTTagList list = nbt.getTagList("ConnectorItems", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int slot = e.getInteger("Slot");
            if (slot >= 0 && slot < slots.length) {
                slots[slot] = ItemStack.loadItemStackFromNBT(e);
            }
        }
    }
}
