package com.sc.tileentity;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.radiation.RadiationSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The decontamination shower (MV): a player standing on it with a radiation dose is washed - the
 * dose falls 2% a second for 60 EU/t and 10 mB of water a tick while it runs. Water by pipe or
 * bucket; a battery slot under the gauge; power switch and redstone like every machine.
 */
public class TileEntityShowerSC extends TileEntityEnergyBase implements IInventory, IFluidHandler {

    public static final int EU_PER_TICK = 60, WATER_PER_TICK = 10, TANK = 8000;
    public static final float DOSE_PER_SECOND = 2F;
    public static final int SLOT_BATTERY = 0;
    public static final int ST_IDLE = 0, ST_WASHING = 1, ST_CLEAN = 2, ST_NO_WATER = 3, ST_NO_ENERGY = 4, ST_OFF = 5;

    private final FluidTank tank = new FluidTank(TANK);
    private final ItemStack[] slots = new ItemStack[1];
    private int status, players;
    /** Shown to clients: the water runs (particles, the screen's scene). */
    private boolean washing;

    public TileEntityShowerSC() {
        super(Tier.MV);
    }

    public FluidTank getTank() {
        return tank;
    }

    public int getStatus() {
        return status;
    }

    public int getPlayers() {
        return players;
    }

    public boolean isWashing() {
        return washing;
    }

    public void setScreenClient(int st, int n, int water) {
        status = st;
        players = n;
        tank.setFluid(water > 0 ? new FluidStack(FluidRegistry.WATER, water) : null);
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (feedFromBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
        }
        if (!switchedOn()) {
            setWashing(false, ST_OFF, 0);
            return;
        }
        List<EntityPlayer> on = standing();
        int dirty = 0;
        for (EntityPlayer p : on) {
            if (RadiationSC.doseOf(p) > 0) {
                dirty++;
            }
        }
        if (dirty == 0) {
            setWashing(false, on.isEmpty() ? ST_IDLE : ST_CLEAN, on.size());
            return;
        }
        if (tank.getFluidAmount() < WATER_PER_TICK) {
            setWashing(false, ST_NO_WATER, on.size());
            return;
        }
        if (getEnergyStored() < EU_PER_TICK) {
            setWashing(false, ST_NO_ENERGY, on.size());
            return;
        }
        removeEnergy(EU_PER_TICK);
        tank.drain(WATER_PER_TICK, true);
        setWashing(true, ST_WASHING, on.size());
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            for (EntityPlayer p : on) {
                RadiationSC.setDose(p, RadiationSC.doseOf(p) - DOSE_PER_SECOND);
                p.extinguish();
            }
        }
        if (worldObj.getTotalWorldTime() % 40 == 0) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, "liquid.water", 0.6F, 1.2F);
        }
        markDirty();
    }

    /** Players standing on the shower (feet in the block above it). */
    @SuppressWarnings("unchecked")
    public List<EntityPlayer> standing() {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord + 0.05, yCoord + 1, zCoord + 0.05, xCoord + 0.95, yCoord + 1.6, zCoord + 0.95);
        return worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
    }

    private void setWashing(boolean on, int st, int n) {
        status = st;
        players = n;
        if (on != washing) {
            washing = on;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ---- inventory: the battery ----

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
        return "tile.siliconage.shower.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null && com.sc.item.BatteryFeedSC.accepts(stack);
    }

    // ---- water ----

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() != FluidRegistry.WATER) {
            return 0;
        }
        int n = tank.fill(resource, doFill);
        if (n > 0 && doFill) {
            markDirty();
        }
        return n;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid == FluidRegistry.WATER;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{tank.getInfo()};
    }

    // ---- the item keeps the energy and the water ----

    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (getEnergyStored() > 0) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        if (tank.getFluidAmount() > 0) {
            nbt.setInteger("Water", tank.getFluidAmount());
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        if (nbt.hasKey("EnergySC")) {
            restoreEnergy(nbt.getInteger("EnergySC"));
        }
        if (nbt.hasKey("Water")) {
            tank.setFluid(new FluidStack(FluidRegistry.WATER, Math.min(TANK, nbt.getInteger("Water"))));
        }
        markDirty();
    }

    // ---- saving, and what clients see ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tank.readFromNBT(nbt.getCompoundTag("Tank"));
        washing = nbt.getBoolean("Washing");
        NBTTagList list = nbt.getTagList("Items", 10);
        slots[SLOT_BATTERY] = list.tagCount() > 0 ? ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(0)) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        nbt.setBoolean("Washing", washing);
        NBTTagList list = new NBTTagList();
        if (slots[SLOT_BATTERY] != null) {
            list.appendTag(slots[SLOT_BATTERY].writeToNBT(new NBTTagCompound()));
        }
        nbt.setTag("Items", list);
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("Washing", washing);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        washing = pkt.func_148857_g().getBoolean("Washing");
    }

    /** Switched off (it's placed so): it takes no energy, so no line can overvolt it. */
    @Override
    public int demandedEnergy() {
        return powerOn ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }
}
