package com.sc.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * Portable tank (Thermal Expansion style): one fluid or gas, any side in or out, the contents
 * travel with the block when it's broken (BlockTankSC / ItemBlockTankSC). With auto-output on it
 * pushes its fluid into the block below every tick - tanks stacked in a column empty downwards.
 */
public class TileEntityTankSC extends TileEntity implements IFluidHandler {

    /** mB per tier: steel, titanium, tungsten, superconducting (Thermal Expansion's 8k / 32k / 128k / 512k). */
    public static final int[] CAPACITY = {8000, 32000, 128000, 512000};
    /** mB pushed down per tick with auto-output on. */
    public static final int OUTPUT_PER_TICK = 1000;

    private int tier;
    private FluidTank tank = new FluidTank(CAPACITY[0]);
    private boolean autoOutput;

    // client sync of the level shown
    private String sentFluid;
    private int sentAmount;
    private int syncCooldown;

    public static int capacityOf(int tier) {
        return CAPACITY[tier >= 0 && tier < CAPACITY.length ? tier : 0];
    }

    public void setTier(int tier) {
        this.tier = tier >= 0 && tier < CAPACITY.length ? tier : 0;
        FluidStack held = tank.getFluid();
        tank = new FluidTank(CAPACITY[this.tier]);
        if (held != null) {
            tank.fill(held, true);
        }
    }

    public int getTier() {
        return tier;
    }

    public FluidTank getTank() {
        return tank;
    }

    public boolean isAutoOutput() {
        return autoOutput;
    }

    public void setAutoOutput(boolean on) {
        autoOutput = on;
        changed(true);
    }

    /** Loads the contents an item carried (placement). */
    public void setContents(FluidStack fluid) {
        tank.setFluid(null);
        if (fluid != null && fluid.amount > 0) {
            tank.fill(fluid, true);
        }
        changed(true);
    }

    // ---- ticking: auto-output down, level sync ----

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (autoOutput && tank.getFluidAmount() > 0 && worldObj.blockExists(xCoord, yCoord - 1, zCoord)) {
            TileEntity below = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
            if (below instanceof IFluidHandler) {
                FluidStack offer = tank.getFluid().copy();
                offer.amount = Math.min(OUTPUT_PER_TICK, offer.amount);
                int moved = ((IFluidHandler) below).fill(ForgeDirection.UP, offer, true);
                if (moved > 0) {
                    tank.drain(moved, true);
                    changed(false);
                }
            }
        }
        if (syncCooldown > 0) {
            syncCooldown--;
        }
        FluidStack held = tank.getFluid();
        String name = held == null ? null : held.getFluid().getName();
        int amount = held == null ? 0 : held.amount;
        boolean kind = name == null ? sentFluid != null : !name.equals(sentFluid);
        boolean level = Math.abs(amount - sentAmount) * 64 > tank.getCapacity();
        if (kind || (level && syncCooldown == 0)) {
            sentFluid = name;
            sentAmount = amount;
            syncCooldown = 10;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());   // comparators
            worldObj.updateLightByType(net.minecraft.world.EnumSkyBlock.Block, xCoord, yCoord, zCoord);
        }
    }

    private void changed(boolean resync) {
        markDirty();
        if (resync && worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** 0..15 by how full it is (a comparator's reading). */
    public int comparatorLevel() {
        int amount = tank.getFluidAmount();
        return amount <= 0 ? 0 : 1 + (int) ((long) amount * 14 / tank.getCapacity());
    }

    // ---- IFluidHandler: every side ----

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        int filled = tank.fill(resource, doFill);
        if (doFill && filled > 0) {
            changed(false);
        }
        return filled;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || tank.getFluid() == null || !resource.isFluidEqual(tank.getFluid())) {
            return null;
        }
        return drain(from, resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        FluidStack out = tank.drain(maxDrain, doDrain);
        if (doDrain && out != null && out.amount > 0) {
            changed(false);
        }
        return out;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{tank.getInfo()};
    }

    // ---- NBT / sync ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readContents(nbt);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        writeContents(nbt);
    }

    private void writeContents(NBTTagCompound nbt) {
        nbt.setInteger("Tier", tier);
        nbt.setBoolean("AutoOutput", autoOutput);
        nbt.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
    }

    private void readContents(NBTTagCompound nbt) {
        tier = nbt.getInteger("Tier");
        if (tier < 0 || tier >= CAPACITY.length) {
            tier = 0;
        }
        tank = new FluidTank(CAPACITY[tier]);
        tank.readFromNBT(nbt.getCompoundTag("Tank"));
        autoOutput = nbt.getBoolean("AutoOutput");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeContents(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity pkt) {
        readContents(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.updateLightByType(net.minecraft.world.EnumSkyBlock.Block, xCoord, yCoord, zCoord);
        }
    }
}
