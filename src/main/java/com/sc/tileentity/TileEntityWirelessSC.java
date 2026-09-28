package com.sc.tileentity;

import java.util.HashMap;
import java.util.Map;

import com.sc.SCMod;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemEntangledCrystalSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Wireless energy, three blocks on one tile:
 * - a TRANSMITTER (LV..XV) takes energy from its grid and sends it to the receiver it's linked
 *   with (a link card: right-click the transmitter, then the receiver), in the same dimension,
 *   up to its tier's range, losing a little per block on the way;
 * - a RECEIVER gives what arrives to its grid through its front face;
 * - a QUANTUM TRANSLATOR (XV) holds half of an entangled crystal: the one holding the other half
 *   is its pair, anywhere, in any dimension, no loss. One end gives, the other takes; the giving
 *   one pays an upkeep, both keep their chunk loaded, and the crystal wears (~1% an hour).
 * The linked ends find each other through a registry of the loaded ones, by id.
 */
public class TileEntityWirelessSC extends TileEntityEnergyBase implements IInventory {

    public static final int TRANSMITTER = 0, RECEIVER = 1, QUANTUM = 2;
    public static final int SLOT_CRYSTAL = 0, SLOT_BATTERY = 1, SLOTS = 2;
    /** Status shown on the screen (sc.wl.status.N). */
    public static final int ST_OK = 0, ST_NO_LINK = 1, ST_UNLOADED = 2, ST_TOO_FAR = 3, ST_OFF = 4, ST_FULL = 5, ST_IDLE = 6,
            ST_NO_CRYSTAL = 7, ST_SPENT = 8, ST_PAIR_MISSING = 9, ST_ROLE = 10, ST_PAUSED = 11, ST_NO_ENERGY = 12, ST_OTHER_DIM = 13;
    /** The quantum pair: its upkeep (EU/t, paid by the giving end), its rate, how often the crystal wears a step. */
    public static final int QUANTUM_UPKEEP = 2048, QUANTUM_RATE = 32768, WEAR_EVERY = 72;

    /** Loaded wireless tiles on the server, by id. */
    private static final Map<Long, TileEntityWirelessSC> LOADED = new HashMap<Long, TileEntityWirelessSC>();

    private int kind;
    private long id, partnerId;
    private int partnerX, partnerY, partnerZ, partnerDim, partnerTier = -1;
    private String owner = "";
    private ForgeDirection facing = ForgeDirection.SOUTH;
    private boolean beam = true, paused, giving = true;
    private final ItemStack[] slots = new ItemStack[SLOTS];
    private ForgeChunkManager.Ticket ticket;
    // what the screen shows (synced by ContainerWirelessSC)
    private int status = ST_NO_LINK, flow, flowWindow, lossPct, distance, receivedAt;
    private long wearTicks;
    /** Last tick the pair worked (not saved); a translator holds its chunk for CHUNK_GRACE after that. */
    private long linkedAt = -1;
    /** The next tick a refused chunk ticket may be asked for again. */
    private long ticketRetryAt;
    /** A translator keeps its chunk loaded this long without a working link (5 minutes), then lets it go. */
    public static final long CHUNK_GRACE = 6000;

    public TileEntityWirelessSC() {
        super();
    }

    public void setup(int kind, Tier tier) {
        this.kind = kind;
        setTier(kind == QUANTUM ? Tier.XV : tier);
    }

    public int getKind() {
        return kind;
    }

    // ------------------------------------------------------------------ range and loss

    /** How far a link of this tier reaches (blocks); XV: the whole dimension. */
    public static int range(Tier t) {
        switch (t) {
            case LV: return 16;
            case MV: return 32;
            case HV: return 64;
            case EV: return 128;
            case IV: return 256;
            case QV: return 512;
            default: return Integer.MAX_VALUE;
        }
    }

    /** Blocks per 1% lost, by tier: the higher, the cleaner the link. */
    public static int blocksPerPercent(Tier t) {
        switch (t) {
            case LV:
            case MV:
            case HV: return 8;
            case EV: return 12;
            case IV: return 16;
            case QV: return 24;
            default: return 32;
        }
    }

    /** % lost over this distance, at most 50. */
    public static int lossPct(double dist, Tier t) {
        return (int) Math.min(50, dist / blocksPerPercent(t));
    }

    private static Tier lower(Tier a, Tier b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    // ------------------------------------------------------------------ energy roles

    @Override
    public boolean isEnergySink() {
        return kind == TRANSMITTER || kind == QUANTUM && giving;
    }

    @Override
    public boolean isEnergySource() {
        return kind == RECEIVER || kind == QUANTUM && !giving;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        return new ForgeDirection[]{facing};
    }

    /** Switched off (or held by redstone, or a paused translator): nothing taken from the grid or given to it. */
    private boolean flowing() {
        return switchedOn() && !(kind == QUANTUM && paused);
    }

    @Override
    public int demandedEnergy() {
        return flowing() ? super.demandedEnergy() : 0;
    }

    @Override
    public int offerableEnergy() {
        return flowing() ? super.offerableEnergy() : 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        return flowing() ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }

    /** A transmitter or a giving translator takes energy on every face. */
    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        return isEnergySink();
    }

    // ------------------------------------------------------------------ the registry

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) {
            TileEntityWirelessSC other = id == 0 ? null : LOADED.get(id);
            if (id == 0 || other != null && other != this && isLive(other) && !sameSpot(other)) {
                id = newId();
            }
            LOADED.put(id, this);
        }
    }

    /** A registered tile that still stands in a loaded world (not a leftover of an unloaded chunk / world). */
    private static boolean isLive(TileEntityWirelessSC t) {
        if (t.isInvalid() || t.worldObj == null) {
            return false;
        }
        net.minecraft.world.World w = net.minecraftforge.common.DimensionManager.getWorld(t.worldObj.provider.dimensionId);
        return w == t.worldObj && w.getTileEntity(t.xCoord, t.yCoord, t.zCoord) == t;
    }

    private boolean sameSpot(TileEntityWirelessSC t) {
        return t.worldObj != null && worldObj != null && t.worldObj.provider.dimensionId == worldObj.provider.dimensionId
                && t.xCoord == xCoord && t.yCoord == yCoord && t.zCoord == zCoord;
    }

    /** A world goes (server stop, a dimension unloaded): its tiles leave the registry. null: all of them. */
    public static void forgetWorld(net.minecraft.world.World w) {
        for (java.util.Iterator<TileEntityWirelessSC> it = LOADED.values().iterator(); it.hasNext(); ) {
            TileEntityWirelessSC t = it.next();
            if (w == null || t.worldObj == w) {
                it.remove();
            }
        }
    }

    private void unregister() {
        if (LOADED.get(id) == this) {
            LOADED.remove(id);
        }
    }

    private long newId() {
        long n;
        do {
            n = (worldObj.rand.nextLong() & Long.MAX_VALUE) | 1L;
        } while (LOADED.containsKey(n));
        return n;
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) {
            unregister();
            holdChunk(false);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) {
            unregister();
        }
        super.onChunkUnload();
    }

    /** The linked other end of an A pair, if loaded and still linked back; null otherwise. */
    private TileEntityWirelessSC partner() {
        if (partnerId == 0) {
            return null;
        }
        TileEntityWirelessSC p = LOADED.get(partnerId);
        if (p == null || p.isInvalid() || p.partnerId != id || p.kind != (kind == TRANSMITTER ? RECEIVER : TRANSMITTER)) {
            return null;
        }
        return p;
    }

    /** The other half's translator: the same pair, the other half, a live crystal. */
    private TileEntityWirelessSC quantumPartner() {
        ItemStack mine = slots[SLOT_CRYSTAL];
        long pair = ItemEntangledCrystalSC.pairOf(mine);
        if (pair == 0) {
            return null;
        }
        for (TileEntityWirelessSC t : LOADED.values()) {
            if (t != this && t.kind == QUANTUM && !t.isInvalid() && t.worldObj != null && !t.worldObj.isRemote && ItemEntangledCrystalSC.pairOf(t.slots[SLOT_CRYSTAL]) == pair
                    && ItemEntangledCrystalSC.halfOf(t.slots[SLOT_CRYSTAL]) != ItemEntangledCrystalSC.halfOf(mine)) {
                return t;
            }
        }
        return null;
    }

    public static TileEntityWirelessSC loaded(long id) {
        return LOADED.get(id);
    }

    /** The link card: this transmitter and that receiver become a pair (old links on either end dropped). */
    public static void link(TileEntityWirelessSC tx, TileEntityWirelessSC rx) {
        for (TileEntityWirelessSC end : new TileEntityWirelessSC[]{tx, rx}) {
            TileEntityWirelessSC old = end.partnerId == 0 ? null : LOADED.get(end.partnerId);
            if (old != null && old != tx && old != rx && old.partnerId == end.id) {
                old.unlinkHere();
            }
        }
        tx.partnerId = rx.id;
        rx.partnerId = tx.id;
        tx.notePartner(rx);
        rx.notePartner(tx);
    }

    private void notePartner(TileEntityWirelessSC p) {
        partnerX = p.xCoord;
        partnerY = p.yCoord;
        partnerZ = p.zCoord;
        partnerDim = p.worldObj == null ? 0 : p.worldObj.provider.dimensionId;
        partnerTier = p.getTier().ordinal();
        changed();
    }

    private void unlinkHere() {
        partnerId = 0;
        partnerTier = -1;
        flow = 0;
        status = ST_NO_LINK;
        changed();
    }

    /** The screen's Unlink: both ends forget each other. */
    public void unlink() {
        TileEntityWirelessSC p = partner();
        if (p != null) {
            p.unlinkHere();
        }
        unlinkHere();
    }

    public long getId() {
        return id;
    }

    public boolean isLinkedTo(TileEntityWirelessSC other) {
        return partnerId == other.id && other.partnerId == id;
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        long time = worldObj.getTotalWorldTime();
        if (time % 20 == 0) {
            flow = flowWindow / 20;
            flowWindow = 0;
        }
        if (kind == TRANSMITTER) {
            tickTransmitter(time);
        } else if (kind == RECEIVER) {
            TileEntityWirelessSC p = partner();
            status = p == null ? (partnerId == 0 ? ST_NO_LINK : ST_UNLOADED) : time - receivedAt < 40 ? ST_OK : ST_IDLE;
            if (p != null) {
                distance = (int) Math.sqrt(p.getDistanceFrom(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5));
                lossPct = lossPct(distance, lower(getTier(), p.getTier()));
            }
        } else {
            tickQuantum(time);
        }
    }

    private void tickTransmitter(long time) {
        if (!switchedOn()) {
            status = ST_OFF;
            return;
        }
        if (feedFromBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
        }
        TileEntityWirelessSC rx = partner();
        if (rx == null) {
            status = partnerId == 0 ? ST_NO_LINK : ST_UNLOADED;
            return;
        }
        if (rx.worldObj != worldObj) {
            status = ST_OTHER_DIM;
            return;
        }
        double dist = Math.sqrt(rx.getDistanceFrom(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5));
        Tier linkTier = lower(getTier(), rx.getTier());
        distance = (int) dist;
        if (dist > range(linkTier)) {
            status = ST_TOO_FAR;
            return;
        }
        int sent = send(rx, dist);
        status = sent > 0 ? ST_OK : rx.getEnergyStored() >= rx.getMaxEnergyStored() ? ST_FULL : ST_IDLE;
        if (sent > 0 && beam && time % 20 == 0) {
            beamParticles(rx);
        }
    }

    /** One tick's sending to the receiver over this distance. @return EU taken from this end */
    private int send(TileEntityWirelessSC rx, double dist) {
        Tier linkTier = lower(getTier(), rx.getTier());
        lossPct = lossPct(dist, linkTier);
        int rate = Math.min(getEnergyStored(), linkTier.getVoltage());
        int room = rx.getMaxEnergyStored() - rx.getEnergyStored();
        if (rate <= 0 || room <= 0) {
            return 0;
        }
        int keep = 100 - lossPct;
        int deliver = Math.min(room, rate * keep / 100);
        int take = Math.min(rate, (deliver * 100 + keep - 1) / keep);
        removeEnergy(take);
        rx.receiveWireless(deliver);
        flowWindow += take;
        markDirty();
        return take;
    }

    private void receiveWireless(int eu) {
        addEnergy(eu);
        flowWindow += eu;
        if (worldObj != null) {
            receivedAt = (int) worldObj.getTotalWorldTime();
        }
        markDirty();
    }

    /** Self-test: one sending tick to that receiver over that distance. */
    public int sendForTest(TileEntityWirelessSC rx, double dist) {
        return send(rx, dist);
    }

    private void beamParticles(TileEntityWirelessSC rx) {
        if (!(worldObj instanceof net.minecraft.world.WorldServer)) {
            return;
        }
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) worldObj;
        double dx = rx.xCoord - xCoord, dy = rx.yCoord - yCoord, dz = rx.zCoord - zCoord;
        int n = (int) Math.min(24, Math.max(4, Math.sqrt(dx * dx + dy * dy + dz * dz)));
        for (int i = 1; i < n; i++) {
            double k = (double) i / n;
            // reddust with no count: the "velocity" is its colour
            ws.func_147487_a("reddust", xCoord + 0.5 + dx * k, yCoord + 0.8 + dy * k, zCoord + 0.5 + dz * k, 0, 0.43, 0.9, 1.0, 1.0);
        }
    }

    private void tickQuantum(long time) {
        ItemStack crystal = slots[SLOT_CRYSTAL];
        if (crystal == null || ItemEntangledCrystalSC.pairOf(crystal) == 0) {
            status = ST_NO_CRYSTAL;
            holdChunk(false);
            return;
        }
        if (ItemEntangledCrystalSC.lifeOf(crystal) <= 0) {
            status = ST_SPENT;
            holdChunk(false);
            return;
        }
        if (!switchedOn() || paused) {
            status = paused ? ST_PAUSED : ST_OFF;
            holdChunk(false);
            return;
        }
        if (linkedAt < 0 || linkedAt > time) {
            linkedAt = time;                              // just placed / loaded: a grace to find the other end
        }
        holdChunk(time - linkedAt < CHUNK_GRACE);
        if (giving && feedFromBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
        }
        TileEntityWirelessSC p = quantumPartner();
        if (p == null) {
            status = ST_PAIR_MISSING;
            return;
        }
        partnerX = p.xCoord;
        partnerY = p.yCoord;
        partnerZ = p.zCoord;
        partnerDim = p.worldObj == null ? 0 : p.worldObj.provider.dimensionId;
        lossPct = 0;
        if (p.giving == giving || p.paused || !p.switchedOn()) {
            status = p.giving == giving ? ST_ROLE : ST_PAUSED;
            return;
        }
        if (!giving) {
            status = time - receivedAt < 40 ? ST_OK : ST_IDLE;
            if (time - receivedAt < 40) {
                linkedAt = time;
            }
            return;
        }
        if (getEnergyStored() < QUANTUM_UPKEEP) {
            status = ST_NO_ENERGY;
            return;
        }
        removeEnergy(QUANTUM_UPKEEP);
        linkedAt = time;
        int room = p.getMaxEnergyStored() - p.getEnergyStored();
        int n = Math.min(Math.min(getEnergyStored(), QUANTUM_RATE), room);
        if (n > 0) {
            removeEnergy(n);
            p.receiveWireless(n);
            flowWindow += n;
        }
        status = n > 0 ? ST_OK : ST_FULL;
        if (++wearTicks % WEAR_EVERY == 0) {                      // both halves wear while the link is up
            ItemEntangledCrystalSC.wear(slots[SLOT_CRYSTAL], 1);
            ItemEntangledCrystalSC.wear(p.slots[SLOT_CRYSTAL], 1);
            p.markDirty();
        }
        markDirty();
    }

    // ------------------------------------------------------------------ chunk loading (the quantum pair)

    private void holdChunk(boolean want) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (want && ticket == null) {
            long now = worldObj.getTotalWorldTime();
            if (now < ticketRetryAt) {
                return;
            }
            ticket = ForgeChunkManager.requestTicket(SCMod.instance, worldObj, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                ticketRetryAt = now + 100;
                return;
            }
            if (ticket != null) {
                NBTTagCompound d = ticket.getModData();
                d.setInteger("x", xCoord);
                d.setInteger("y", yCoord);
                d.setInteger("z", zCoord);
                ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            }
        } else if (!want && ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
    }

    /** After a world load (ChunkLoaderSC): this tile's ticket, kept. */
    public void adoptTicket(ForgeChunkManager.Ticket t) {
        if (ticket != null && ticket != t) {
            ForgeChunkManager.releaseTicket(ticket);
        }
        ticket = t;
        ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
    }

    // ------------------------------------------------------------------ settings

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection f) {
        boolean was = facing != f;
        facing = f;
        if (was) {
            refreshEnergyNet();
            changed();
        }
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String name) {
        owner = name == null ? "" : name;
    }

    public boolean allowed(EntityPlayer p) {
        return owner.isEmpty() || owner.equals(p.getCommandSenderName()) || p.capabilities.isCreativeMode;
    }

    public boolean isBeam() {
        return beam;
    }

    public void toggleBeam() {
        beam = !beam;
        changed();
    }

    public boolean isPaused() {
        return paused;
    }

    public void togglePause() {
        paused = !paused;
        changed();
    }

    public boolean isGiving() {
        return giving;
    }

    public void toggleRole() {
        giving = !giving;
        refreshEnergyNet();
        changed();
    }

    private void changed() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ---- the screen's numbers (server side read by the container, client side set from it) ----

    public int getStatus() {
        return status;
    }

    public int getFlow() {
        return flow;
    }

    public int getLossPct() {
        return lossPct;
    }

    public int getDistance() {
        return distance;
    }

    public int[] partnerPos() {
        return new int[]{partnerX, partnerY, partnerZ, partnerDim, partnerTier};
    }

    public boolean hasLink() {
        return kind == QUANTUM ? ItemEntangledCrystalSC.pairOf(slots[SLOT_CRYSTAL]) != 0 : partnerId != 0;
    }

    public void setScreenClient(int status, int flow, int loss, int dist, int px, int py, int pz, int pdim, int ptier, int bits) {
        this.status = status;
        this.flow = flow;
        this.lossPct = loss;
        this.distance = dist;
        this.partnerX = px;
        this.partnerY = py;
        this.partnerZ = pz;
        this.partnerDim = pdim;
        this.partnerTier = ptier;
        this.beam = (bits & 1) != 0;
        this.paused = (bits & 2) != 0;
        this.giving = (bits & 4) != 0;
        this.partnerId = (bits & 8) != 0 ? 1 : 0;
    }

    public int screenBits() {
        return (beam ? 1 : 0) | (paused ? 2 : 0) | (giving ? 4 : 0) | (partnerId != 0 ? 8 : 0);
    }

    // ------------------------------------------------------------------ inventory: the crystal half, a battery

    @Override
    public int getSizeInventory() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOTS ? slots[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack s = getStackInSlot(slot);
        if (s == null) {
            return null;
        }
        ItemStack out = s.stackSize <= count ? s : s.splitStack(count);
        if (out == s) {
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
        if (slot >= 0 && slot < SLOTS) {
            slots[slot] = stack;
            markDirty();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.wireless";
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
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
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
        if (slot == SLOT_CRYSTAL) {
            return kind == QUANTUM && ItemEntangledCrystalSC.pairOf(stack) != 0;
        }
        return slot == SLOT_BATTERY && kind != RECEIVER && com.sc.item.BatteryFeedSC.accepts(stack);
    }

    // ------------------------------------------------------------------ NBT and client sync

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        kind = nbt.getInteger("Kind");
        id = nbt.getLong("WId");
        partnerId = nbt.getLong("Partner");
        partnerX = nbt.getInteger("PX");
        partnerY = nbt.getInteger("PY");
        partnerZ = nbt.getInteger("PZ");
        partnerDim = nbt.getInteger("PDim");
        partnerTier = nbt.hasKey("PTier") ? nbt.getInteger("PTier") : -1;
        owner = nbt.getString("Owner");
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.SOUTH;
        }
        beam = !nbt.getBoolean("NoBeam");
        paused = nbt.getBoolean("Paused");
        giving = !nbt.getBoolean("Taking");
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = nbt.hasKey("Slot" + i) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("Slot" + i)) : null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Kind", kind);
        nbt.setLong("WId", id);
        nbt.setLong("Partner", partnerId);
        nbt.setInteger("PX", partnerX);
        nbt.setInteger("PY", partnerY);
        nbt.setInteger("PZ", partnerZ);
        nbt.setInteger("PDim", partnerDim);
        nbt.setInteger("PTier", partnerTier);
        nbt.setString("Owner", owner);
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setBoolean("NoBeam", !beam);
        nbt.setBoolean("Paused", paused);
        nbt.setBoolean("Taking", !giving);
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null) {
                nbt.setTag("Slot" + i, slots[i].writeToNBT(new NBTTagCompound()));
            }
        }
    }

    /** The item keeps the energy and the link: a transmitter taken with a wrench and put down again stays linked. */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (getEnergyStored() > 0) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        if (kind != QUANTUM && partnerId != 0) {
            nbt.setLong("WId", id);
            nbt.setLong("Partner", partnerId);
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        if (nbt.hasKey("EnergySC")) {
            restoreEnergy(nbt.getInteger("EnergySC"));
        }
        if (worldObj != null && !worldObj.isRemote && nbt.hasKey("WId") && !LOADED.containsKey(nbt.getLong("WId"))) {
            unregister();
            id = nbt.getLong("WId");
            partnerId = nbt.getLong("Partner");
            LOADED.put(id, this);
        }
        markDirty();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
