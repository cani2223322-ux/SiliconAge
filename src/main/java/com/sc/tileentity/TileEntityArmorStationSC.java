package com.sc.tileentity;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The Armour Service Station (MV): four slots for the energy suit's pieces (helmet, chestplate,
 * leggings, boots) and an MV buffer. Every EVERY ticks it pulls the gases the pieces lack
 * (ArmorGasSC) out of the fluid containers beside it - the mod's tanks, pipes and conduit bundles,
 * other mods' tanks, any side - at GAS_PER_TICK mB a tick per gas, and charges the pieces like a
 * charge pad of its tier. A player standing on top gets the worn suit filled and charged the same
 * way. Pipes may also push gases straight in (fill). Gases cost energy (pumping): 1 EU per
 * MB_PER_EU mB. Per-gas switches and a master switch on the screen; power switch and redstone
 * like every machine.
 */
public class TileEntityArmorStationSC extends TileEntityEnergyBase implements IInventory, IFluidHandler {

    /** Ticks between two rounds. */
    public static final int EVERY = 5;
    /** mB of one gas a tick (a round moves EVERY x this of each gas). */
    public static final int GAS_PER_TICK = 100;
    /** Pumping: 1 EU per this many mB. */
    public static final int MB_PER_EU = 20;
    public static final int SLOTS = 4;
    public static final int ST_IDLE = 0, ST_WORKING = 1, ST_FULL = 2, ST_NO_GAS = 3, ST_NO_ENERGY = 4, ST_OFF = 5;
    /** All gases switched on. */
    public static final int ALL_GASES = (1 << Gas.values().length) - 1;

    /** Slot i holds the piece of ItemArmor.armorType i (0 helmet .. 3 boots). */
    private final ItemStack[] slots = new ItemStack[SLOTS];
    private ForgeDirection facing = ForgeDirection.NORTH;
    private boolean fillGases = true;
    private int gasMask = ALL_GASES;
    private int status, players;
    /** Lit top (clients): something was filled or charged in the last round. */
    private boolean active;
    /** Screen: per gas, in the slots and on the players on top: [gas] amount / capacity (client copy too). */
    private final int[] shownAmount = new int[Gas.values().length], shownCap = new int[Gas.values().length];

    public TileEntityArmorStationSC() {
        super(Tier.MV);
    }

    // ------------------------------------------------------------------ state

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection f) {
        if (f != null && f.offsetY == 0 && f != ForgeDirection.UNKNOWN) {
            facing = f;
            markDirty();
        }
    }

    public boolean isFillGases() {
        return fillGases;
    }

    public void toggleFillGases() {
        fillGases = !fillGases;
        markDirty();
    }

    public boolean gasEnabled(Gas g) {
        return (gasMask & (1 << g.ordinal())) != 0;
    }

    public void toggleGas(Gas g) {
        gasMask ^= 1 << g.ordinal();
        markDirty();
    }

    /** "Helium only": only helium on; pressed again - every gas back on. */
    public void toggleHeliumOnly() {
        int helium = 1 << Gas.HELIUM.ordinal();
        gasMask = gasMask == helium ? ALL_GASES : helium;
        markDirty();
    }

    public int getGasMask() {
        return gasMask;
    }

    public int getStatus() {
        return status;
    }

    public int getPlayers() {
        return players;
    }

    public boolean isActive() {
        return active;
    }

    public int shownAmount(Gas g) {
        return shownAmount[g.ordinal()];
    }

    public int shownCapacity(Gas g) {
        return shownCap[g.ordinal()];
    }

    /** Settings flags for the container's sync: bit 0 fillGases, bits 1.. the gas mask. */
    public int settingsFlags() {
        return (fillGases ? 1 : 0) | gasMask << 1;
    }

    public void setScreenClient(int st, int n, int settings) {
        status = st;
        players = n;
        fillGases = (settings & 1) != 0;
        gasMask = (settings >> 1) & ALL_GASES;
    }

    public void setShownClient(int gas, int amount, int cap) {
        shownAmount[gas] = amount;
        shownCap[gas] = cap;
    }

    // ------------------------------------------------------------------ the work

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || worldObj.getTotalWorldTime() % EVERY != 0) {
            return;
        }
        List<EntityPlayer> on = standing();
        players = on.size();
        if (!switchedOn()) {
            status = ST_OFF;
            setActive(false);
            refreshShown(on);
            return;
        }
        boolean did = false, needGas = false, gotGas = false, starved = false;
        // energy: one budget of voltage x EVERY for the slots first, then the players on top (a charge pad)
        int budget = Math.min(getEnergyStored(), getTier().getVoltage() * EVERY), start = budget;
        for (int i = 0; i < SLOTS && budget > 0; i++) {
            budget -= chargePiece(slots[i], budget);
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4 && budget > 0; t++) {
                budget -= chargePiece(ArmorGasSC.worn(p, t), budget);
            }
        }
        boolean needEu = anyNeedsCharge(on);
        if (start - budget > 0) {
            removeEnergy(start - budget);
            did = true;
        }
        if (fillGases) {
            for (Gas g : Gas.values()) {
                if (!gasEnabled(g)) {
                    continue;
                }
                int need = need(g, on);
                if (need <= 0) {
                    continue;
                }
                needGas = true;
                int affordable = getEnergyStored() * MB_PER_EU;
                if (affordable <= 0) {
                    starved = true;
                    continue;
                }
                int moved = pullGas(g, Math.min(Math.min(need, GAS_PER_TICK * EVERY), affordable), on);
                if (moved > 0) {
                    removeEnergy((moved + MB_PER_EU - 1) / MB_PER_EU);
                    gotGas = true;
                    did = true;
                }
            }
        }
        if (did) {
            markDirty();
        }
        status = did ? ST_WORKING : starved || (needEu && getEnergyStored() <= 0) ? ST_NO_ENERGY
                : needGas && !gotGas ? ST_NO_GAS : hasAnyArmor(on) ? ST_FULL : ST_IDLE;
        setActive(did);
        refreshShown(on);
    }

    /** Players standing on the station (feet in the block above it). */
    @SuppressWarnings("unchecked")
    public List<EntityPlayer> standing() {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord, yCoord + 1, zCoord, xCoord + 1, yCoord + 1.5, zCoord + 1);
        return worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
    }

    private void setActive(boolean on) {
        if (on != active) {
            active = on;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);       // the lit / dark top
        }
    }

    /** Charges one piece out of `max` EU, as a charge pad of this tier (only suits whose charge tier is at most MV). */
    public int chargePiece(ItemStack s, int max) {
        if (s == null || max <= 0 || !(s.getItem() instanceof ItemArmorSC) || !tierAllows(s)) {
            return 0;
        }
        return ItemArmorSC.charge(s, max);
    }

    /** Any of the mod's suits: a service station charges Nano, Quantum and Exo alike, at its own MV rate. */
    public boolean tierAllows(ItemStack s) {
        return s != null && s.getItem() instanceof ItemArmorSC;
    }

    private boolean anyNeedsCharge(List<EntityPlayer> on) {
        for (ItemStack s : slots) {
            if (tierAllows(s) && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                return true;
            }
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4; t++) {
                ItemStack s = ArmorGasSC.worn(p, t);
                if (tierAllows(s) && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnyArmor(List<EntityPlayer> on) {
        for (ItemStack s : slots) {
            if (s != null) {
                return true;
            }
        }
        for (EntityPlayer p : on) {
            for (int t = 0; t < 4; t++) {
                if (ArmorGasSC.worn(p, t) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Room for `g` in the pieces in the slots, mB, in the fill order. */
    private int[] slotRoom(Gas g) {
        int[] room = new int[FILL_ORDER.length];
        for (int k = 0; k < FILL_ORDER.length; k++) {
            ItemStack s = slots[FILL_ORDER[k]];
            room[k] = Math.max(0, ArmorGasSC.capacity(s, g) - ArmorGasSC.amount(s, g));
        }
        return room;
    }

    /** Room for `g` in the slots and the suits of the players on top, mB. */
    public int need(Gas g, List<EntityPlayer> on) {
        long sum = 0;
        for (int r : slotRoom(g)) {
            sum += r;
        }
        for (EntityPlayer p : on) {
            sum += Math.max(0, ArmorGasSC.suitCapacity(p, g) - ArmorGasSC.suitAmount(p, g));
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Pulls up to `want` mB of `g` out of the fluid handlers beside the station into the armour. @return mB moved */
    private int pullGas(Gas g, int want, List<EntityPlayer> on) {
        Fluid fluid = g.fluidOf();
        if (fluid == null || want <= 0) {
            return 0;
        }
        int got = 0;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (got >= want) {
                break;
            }
            int x = xCoord + dir.offsetX, y = yCoord + dir.offsetY, z = zCoord + dir.offsetZ;
            if (!worldObj.blockExists(x, y, z)) {
                continue;
            }
            TileEntity te = worldObj.getTileEntity(x, y, z);
            if (!(te instanceof IFluidHandler) || te instanceof TileEntityArmorStationSC) {
                continue;
            }
            IFluidHandler h = (IFluidHandler) te;
            ForgeDirection from = dir.getOpposite();
            int room = Math.min(want - got, need(g, on));   // never take more than fits
            if (room <= 0) {
                break;
            }
            FluidStack test = h.drain(from, new FluidStack(fluid, room), false);
            if (test == null || test.amount <= 0 || test.getFluid() != fluid) {
                continue;
            }
            FluidStack real = h.drain(from, new FluidStack(fluid, Math.min(test.amount, room)), true);
            if (real == null || real.amount <= 0) {
                continue;
            }
            int put = putGas(g, Math.min(real.amount, room), on);
            int rest = real.amount - put;
            if (rest > 0) {                                // a handler that gave more than asked: give it back
                int back = h.fill(from, new FluidStack(fluid, rest), true);
                if (back < rest) {
                    cpw.mods.fml.common.FMLLog.fine("[Silicon Age] armour station at %d,%d,%d: %d mB of %s neither fitted nor went back",
                            xCoord, yCoord, zCoord, rest - back, fluid.getName());
                }
            }
            got += put;
        }
        return got;
    }

    /** Puts `mb` of `g` into the pieces in the slots (chestplate first), then the suits on top. @return mB put */
    public int putGas(Gas g, int mb, List<EntityPlayer> on) {
        int[] room = slotRoom(g);
        int[] split = split(mb, room);
        int put = 0;
        for (int k = 0; k < FILL_ORDER.length; k++) {
            if (split[k] > 0) {
                put += ArmorGasSC.fill(slots[FILL_ORDER[k]], g, split[k], false);
            }
        }
        if (on != null) {
            for (EntityPlayer p : on) {
                if (put >= mb) {
                    break;
                }
                put += ArmorGasSC.suitFill(p, g, mb - put, false);
            }
        }
        return put;
    }

    /** The slots in fill order: the chestplate's loop first, as ArmorGasSC.suitFill. */
    public static final int[] FILL_ORDER = {ArmorGasSC.CHEST, ArmorGasSC.HELMET, ArmorGasSC.LEGS, ArmorGasSC.BOOTS};

    /**
     * Pure: shares `mb` out over tanks with `room` free each, in their order (each takes what it
     * can before the next). @return how much each one gets; the sum is min(mb, total room).
     */
    public static int[] split(int mb, int[] room) {
        int[] out = new int[room.length];
        int left = Math.max(0, mb);
        for (int k = 0; k < room.length && left > 0; k++) {
            out[k] = Math.max(0, Math.min(room[k], left));
            left -= out[k];
        }
        return out;
    }

    /** Sums for the screen and WAILA: per gas, in the slots and on the players on top. */
    private void refreshShown(List<EntityPlayer> on) {
        for (Gas g : Gas.values()) {
            long a = 0, c = 0;
            for (ItemStack s : slots) {
                a += ArmorGasSC.amount(s, g);
                c += ArmorGasSC.capacity(s, g);
            }
            for (EntityPlayer p : on) {
                a += ArmorGasSC.suitAmount(p, g);
                c += ArmorGasSC.suitCapacity(p, g);
            }
            shownAmount[g.ordinal()] = (int) Math.min(Integer.MAX_VALUE, a);
            shownCap[g.ordinal()] = (int) Math.min(Integer.MAX_VALUE, c);
        }
    }

    // ------------------------------------------------------------------ inventory: the four pieces

    /** What goes in slot `slot`: a piece of the mod's suits of that type. */
    public static boolean fits(int slot, ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmorSC && ((ItemArmorSC) stack.getItem()).armorType == slot;
    }

    @Override
    public int getSizeInventory() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOTS ? slots[slot] : null;
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
        return "tile.siliconage.armorStation.name";
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
        return fits(slot, stack);
    }

    // ------------------------------------------------------------------ gases pushed in by pipes

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        Gas g = resource == null ? null : Gas.of(resource.getFluid());
        if (g == null || worldObj == null || !fillGases || !gasEnabled(g) || !switchedOn()) {
            return 0;
        }
        List<EntityPlayer> on = standing();
        int n = Math.min(resource.amount, Math.min(need(g, on), getEnergyStored() * MB_PER_EU));
        long now = worldObj.getTotalWorldTime();
        n = Math.min(n, pipeLeft(g, now));                 // pipes: GAS_PER_TICK a tick per gas, however often they call
        if (n <= 0) {
            return 0;
        }
        if (!doFill) {
            return n;
        }
        int put = putGas(g, n, on);
        if (put > 0) {
            pipeUsed(g, now, put);
            removeEnergy((put + MB_PER_EU - 1) / MB_PER_EU);
            markDirty();
        }
        return put;
    }

    /** What the pipes put in this tick, per gas (reset when the world time moves on; not saved). */
    private final int[] pipeIn = new int[Gas.values().length];
    private long pipeTick = Long.MIN_VALUE;

    /** mB of `g` the pipes may still push in at world time `now` (GAS_PER_TICK a tick per gas). */
    public int pipeLeft(Gas g, long now) {
        if (now != pipeTick) {
            pipeTick = now;
            java.util.Arrays.fill(pipeIn, 0);
        }
        return Math.max(0, GAS_PER_TICK - pipeIn[g.ordinal()]);
    }

    /** Counts `mb` of `g` pushed in by a pipe at world time `now`. */
    public void pipeUsed(Gas g, long now, int mb) {
        pipeLeft(g, now);
        pipeIn[g.ordinal()] += mb;
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
        return Gas.of(fluid) != null;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[0];
    }

    // ------------------------------------------------------------------ the item keeps the energy and the settings

    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (getEnergyStored() > 0) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        if (!fillGases || gasMask != ALL_GASES) {
            nbt.setBoolean("NoGases", !fillGases);
            nbt.setInteger("GasMask", gasMask);
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        if (nbt.hasKey("EnergySC")) {
            restoreEnergy(nbt.getInteger("EnergySC"));
        }
        if (nbt.hasKey("GasMask")) {
            fillGases = !nbt.getBoolean("NoGases");
            gasMask = nbt.getInteger("GasMask") & ALL_GASES;
        }
        markDirty();
    }

    // ------------------------------------------------------------------ saving, and what clients see

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing.offsetY != 0 || facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.NORTH;
        }
        fillGases = !nbt.getBoolean("NoGases");
        gasMask = nbt.hasKey("GasMask") ? nbt.getInteger("GasMask") & ALL_GASES : ALL_GASES;
        active = nbt.getBoolean("StationActive");
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = null;
        }
        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot");
            if (slot >= 0 && slot < SLOTS) {
                slots[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setBoolean("NoGases", !fillGases);
        nbt.setInteger("GasMask", gasMask);
        nbt.setBoolean("StationActive", active);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                slots[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        nbt.setTag("Items", list);
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("StationActive", active);
        nbt.setInteger("Facing", facing.ordinal());
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        active = nbt.getBoolean("StationActive");
        ForgeDirection f = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (f.offsetY == 0 && f != ForgeDirection.UNKNOWN) {
            facing = f;
        }
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }
}
