package com.sc.tileentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeMarksSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeSpaceSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.bridge.BridgeTeleportSC;
import com.sc.init.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * The Bridge Controller (docs/plan-ground-bridge.md, stage 1): the bridge's brain. Once a second it checks
 * the build round it (BridgeStructureSC: the 5x5 Ground / 7x7 Space ring of gravity coils standing on the
 * controller, focusers, the parts linked by touching, stabilisers) and links the capacitors and ports; it
 * keeps the shared tanks (§5), the calibration (С1), the cooling, the target, the bookmarks and the journal.
 *
 * «Открыть» (mode ДР0 «С базы»): if everything is there the singularity is born at once (§6) - the burst
 * from the capacitors, singular matter and gases from the tanks - and a vortex fills the ring's inside (end
 * A) and stands at the target (end B, its own free 3x3x2 / 5x5x2, never in a block). Whatever enters one end
 * comes out of the other (players; mobs, items, minecarts with the Mass Compensator). While it is open the
 * hold (EU a tick, He/Ar/D2O a second) is paid; its time runs out, «Закрыть», a resource short for 3 s or
 * stability under 10% fold it; then the ring cools 60 s (20 s with the Ring Cooler). Nothing else gates it.
 *
 * Stage 2/3 data already has its place: mode (ДР1-ДР5), the access mode, wear (С2).
 */
public class TileEntityBridgeControllerSC extends TileEntity {

    public static final int MAX_JOURNAL = 20, BOOKMARKS = 8, BOOKMARKS_NAV = 32;
    /** Ground: the target at least this far from the ring. */
    public static final int MIN_DISTANCE = 16;
    public static final int WORLD_LIMIT = 29999000;
    public static final int AUTO_Y = Integer.MIN_VALUE;
    /** Access (stage 2: friends, public). */
    public static final int ACCESS_OWNER = 0;

    // ------------------------------------------------------------------ saved state

    private String owner = "";
    private int facing = 3;
    private boolean powerOn;
    private final int[] tanks = new int[BridgeMathSC.GASES.length];
    private long calibSig;
    private int coolTicks, coolTotal;
    private int mode, access = ACCESS_OWNER, wear;
    /** The target: x, y (AUTO_Y), z, dimension. */
    private int tx, ty = AUTO_Y, tz, tdim;
    private boolean targetSet;
    private NBTTagCompound place;
    private final List<NBTTagCompound> bookmarks = new ArrayList<NBTTagCompound>();
    private final List<NBTTagCompound> journal = new ArrayList<NBTTagCompound>();
    private int opens;

    // the open portal
    private boolean open;
    private long openId;
    private int openKind, lifeLeft, lifeTotal, stability = 100, shortTicks = -1;
    private int aAxis, aSize;
    private int bDim, bx, by, bz, bAxis, bW;
    private boolean coilsLit;
    private BridgeMathSC.Cost hold;
    private String shortWhat = "";

    // ------------------------------------------------------------------ transient

    private BridgeStructureSC.Scan scan;
    private final Set<Long> partKeys = new HashSet<Long>();
    private final List<int[]> linked = new ArrayList<int[]>();
    private int tick;
    private ForgeChunkManager.Ticket ticketA, ticketB;
    private long chargedThisSecond, chargeRate;
    private int[] highlight;
    private long highlightUntil;
    private BridgeMsgSC lastMsg;
    private long lastMsgAt;
    private int coolHeDebt;

    // ------------------------------------------------------------------ getters

    public String getOwner() {
        return owner;
    }

    public void setOwner(String o) {
        owner = o == null ? "" : o;
        markDirty();
    }

    public int getFacing() {
        return facing;
    }

    public void setFacing(int f) {
        facing = f;
        markDirty();
    }

    public boolean isPowerOn() {
        return powerOn;
    }

    public void setPowerOn(boolean on) {
        powerOn = on;
        markDirty();
        for (int[] p : linked) {
            TileEntity te = worldObj == null ? null : worldObj.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityBridgeEnergyPortSC) {
                ((TileEntityBridgeEnergyPortSC) te).setPowerOn(on);
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    public long getOpenId() {
        return openId;
    }

    public int getLifeLeft() {
        return lifeLeft;
    }

    public int getCoolTicks() {
        return coolTicks;
    }

    public int getStability() {
        return stability;
    }

    public boolean isCalibrated() {
        return calibSig != 0 && scan != null && scan.signature == calibSig;
    }

    public long getCalibSig() {
        return calibSig;
    }

    public BridgeStructureSC.Scan getScan() {
        return scan;
    }

    public int getOpens() {
        return opens;
    }

    public List<NBTTagCompound> getBookmarks() {
        return bookmarks;
    }

    public List<NBTTagCompound> getJournal() {
        return journal;
    }

    public int[] getEndB() {
        return new int[]{bDim, bx, by, bz, bAxis, bW};
    }

    public BridgeMsgSC getLastMsg() {
        return lastMsg;
    }

    public int bridgeKind() {
        return scan == null ? BridgeMathSC.GROUND : scan.kind;
    }

    public int maxBookmarks() {
        return scan != null && scan.nav ? BOOKMARKS_NAV : BOOKMARKS;
    }

    /** Whether this part is one the last check counted (capacitor / port). */
    public boolean countsPart(int x, int y, int z) {
        return partKeys.contains(key(x, y, z));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // ------------------------------------------------------------------ tanks

    public int tankAmount(int i) {
        return tanks[i];
    }

    public int tankCapacity(int i) {
        return BridgeMathSC.tankCapacity(i, scan == null ? 1 : scan.gasPorts.size());
    }

    public int fillTank(int i, int mb, boolean doFill) {
        int room = Math.max(0, tankCapacity(i) - tanks[i]);
        int n = Math.max(0, Math.min(room, mb));
        if (doFill && n > 0) {
            tanks[i] += n;
            markDirty();
        }
        return n;
    }

    public int drainTank(int i, int mb, boolean doDrain) {
        int n = Math.max(0, Math.min(tanks[i], mb));
        if (doDrain && n > 0) {
            tanks[i] -= n;
            markDirty();
        }
        return n;
    }

    /** The screen's clear button: the tank is emptied (free, nothing comes back). */
    public void clearTank(int i) {
        if (i >= 0 && i < tanks.length && tanks[i] > 0) {
            tanks[i] = 0;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ energy

    private List<TileEntityBridgeCapacitorSC> capacitors() {
        List<TileEntityBridgeCapacitorSC> l = new ArrayList<TileEntityBridgeCapacitorSC>();
        if (scan == null || worldObj == null) {
            return l;
        }
        for (int[] p : scan.capacitors) {
            if (worldObj.blockExists(p[0], p[1], p[2])) {
                TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileEntityBridgeCapacitorSC) {
                    l.add((TileEntityBridgeCapacitorSC) te);
                }
            }
        }
        return l;
    }

    private List<TileEntityBridgeEnergyPortSC> energyPorts() {
        List<TileEntityBridgeEnergyPortSC> l = new ArrayList<TileEntityBridgeEnergyPortSC>();
        if (scan == null || worldObj == null) {
            return l;
        }
        for (int[] p : scan.energyPorts) {
            if (worldObj.blockExists(p[0], p[1], p[2])) {
                TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileEntityBridgeEnergyPortSC) {
                    l.add((TileEntityBridgeEnergyPortSC) te);
                }
            }
        }
        return l;
    }

    public long capacitorEnergy() {
        long t = 0;
        for (TileEntityBridgeCapacitorSC c : capacitors()) {
            t += c.getEnergy();
        }
        return t;
    }

    public long capacitorMax() {
        return (scan == null ? 0 : scan.capacitors.size()) * BridgeMathSC.CAPACITOR_EU;
    }

    public long portEnergy() {
        long t = 0;
        for (TileEntityBridgeEnergyPortSC p : energyPorts()) {
            t += p.getEnergyStored();
        }
        return t;
    }

    /** Takes `eu` from the capacitors (all or nothing). */
    private boolean drawCapacitors(long eu) {
        List<TileEntityBridgeCapacitorSC> caps = capacitors();
        long[] s = new long[caps.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = caps.get(i).getEnergy();
        }
        if (!BridgeMathSC.drain(s, eu)) {
            return false;
        }
        for (int i = 0; i < s.length; i++) {
            caps.get(i).setEnergy(s[i]);
        }
        return true;
    }

    /** Takes `eu` from the ports first, then the capacitors (all or nothing). */
    private boolean drawAny(long eu) {
        if (eu <= 0) {
            return true;
        }
        if (portEnergy() + capacitorEnergy() < eu) {
            return false;
        }
        long left = eu;
        for (TileEntityBridgeEnergyPortSC p : energyPorts()) {
            left -= p.take((int) Math.min(Integer.MAX_VALUE, left));
            if (left <= 0) {
                return true;
            }
        }
        return drawCapacitors(left);
    }

    /** The ports' energy into the capacitors (the ports take nothing while the bridge is off). */
    private void chargeTick() {
        List<TileEntityBridgeCapacitorSC> caps = capacitors();
        if (caps.isEmpty()) {
            return;
        }
        long[] s = new long[caps.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = caps.get(i).getEnergy();
        }
        long room = (long) s.length * BridgeMathSC.CAPACITOR_EU - BridgeMathSC.total(s);
        if (room <= 0) {
            return;
        }
        long moved = 0;
        for (TileEntityBridgeEnergyPortSC p : energyPorts()) {
            if (room - moved <= 0) {
                break;
            }
            moved += p.take((int) Math.min(p.getEnergyStored(), room - moved));
        }
        if (moved > 0) {
            BridgeMathSC.charge(s, moved);
            for (int i = 0; i < s.length; i++) {
                caps.get(i).setEnergy(s[i]);
            }
            chargedThisSecond += moved;
        }
    }

    // ------------------------------------------------------------------ the check

    private BridgeStructureSC.View view() {
        final World w = worldObj;
        return new BridgeStructureSC.View() {
            @Override
            public int kind(int x, int y, int z) {
                return kindAt(w, x, y, z);
            }
        };
    }

    public static int kindAt(World w, int x, int y, int z) {
        if (y < 0 || y >= w.getHeight() || !w.blockExists(x, y, z)) {
            return BridgeStructureSC.K_AIR;                    // never loads a chunk for the check
        }
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z)) {
            return BridgeStructureSC.K_AIR;
        }
        if (b == ModBlocks.gravityCoil) {
            return BridgeStructureSC.K_COIL;
        }
        if (b == ModBlocks.bridge) {
            return BlockBridgeSC.kindOf(w.getBlockMetadata(x, y, z));
        }
        if (b == ModBlocks.gravStabiliser) {
            return BridgeStructureSC.K_STABILISER;
        }
        if (b == ModBlocks.bridgeVortex) {
            return BridgeStructureSC.K_VORTEX;
        }
        Material m = b.getMaterial();
        if (!m.isLiquid() && !m.blocksMovement()) {
            return BridgeStructureSC.K_PASSABLE;
        }
        return BridgeStructureSC.K_OTHER;
    }

    /** The check now (also the «Проверить» button). @return the scan, or null when the chunks round it aren't loaded */
    public BridgeStructureSC.Scan rescan() {
        if (worldObj == null || worldObj.isRemote) {
            return scan;
        }
        // the ring and the room round it must be loaded; stabilisers further out count only in loaded chunks (kindAt)
        if (!worldObj.checkChunksExist(xCoord - 5, yCoord - 1, zCoord - 5, xCoord + 5, yCoord + 9, zCoord + 5)) {
            return scan;
        }
        BridgeStructureSC.Scan s = BridgeStructureSC.scan(view(), xCoord, yCoord, zCoord);
        scan = s;
        relink(s);
        if (calibSig != 0 && s.signature != calibSig && !open) {
            calibSig = 0;
            log(new BridgeMsgSC("sc.bridge.journal.calibLost"), "", true);
            markDirty();
        }
        if (calibSig != 0 && !s.valid && !open) {
            calibSig = 0;                                    // a ring change (a coil out and back) - calibrate again
            log(new BridgeMsgSC("sc.bridge.journal.calibLost"), "", true);
            markDirty();
        }
        if (!open && s.found) {
            litCoils(s, false);
        }
        return s;
    }

    /** The parts of this check point to this controller; the ones it no longer counts let go. */
    private void relink(BridgeStructureSC.Scan s) {
        List<int[]> now = new ArrayList<int[]>();
        now.addAll(s.capacitors);
        now.addAll(s.energyPorts);
        now.addAll(s.gasPorts);
        Set<Long> keys = new HashSet<Long>();
        int[] me = {xCoord, yCoord, zCoord};
        for (int[] p : now) {
            keys.add(key(p[0], p[1], p[2]));
            TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof IBridgePartSC) {
                ((IBridgePartSC) te).link(me);
            }
            if (te instanceof TileEntityBridgeEnergyPortSC && ((TileEntityBridgeEnergyPortSC) te).isPowerOn() != powerOn) {
                ((TileEntityBridgeEnergyPortSC) te).setPowerOn(powerOn);
            }
        }
        for (int[] p : linked) {
            if (!keys.contains(key(p[0], p[1], p[2])) && worldObj.blockExists(p[0], p[1], p[2])) {
                TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof IBridgePartSC) {
                    int[] c = ((IBridgePartSC) te).controllerPos();
                    if (c != null && c[0] == xCoord && c[1] == yCoord && c[2] == zCoord) {
                        ((IBridgePartSC) te).link(null);
                        if (te instanceof TileEntityBridgeEnergyPortSC) {
                            ((TileEntityBridgeEnergyPortSC) te).setPowerOn(false);
                        }
                    }
                }
            }
        }
        linked.clear();
        linked.addAll(now);
        partKeys.clear();
        partKeys.addAll(keys);
    }

    /** The ring's coils glow (metadata 1) while a portal is open. */
    private void litCoils(BridgeStructureSC.Scan s, boolean lit) {
        if (s == null || !s.found) {
            return;
        }
        int h = (s.size - 1) / 2;
        for (int v = 1; v <= s.size; v++) {
            for (int u = -h; u <= h; u++) {
                if (BridgeStructureSC.isRing(s.size, u, v)) {
                    int[] p = s.at(u, v);
                    if (worldObj.getBlock(p[0], p[1], p[2]) == ModBlocks.gravityCoil && worldObj.getBlockMetadata(p[0], p[1], p[2]) != (lit ? 1 : 0)) {
                        worldObj.setBlockMetadataWithNotify(p[0], p[1], p[2], lit ? 1 : 0, 3);
                    }
                }
            }
        }
        coilsLit = lit;
    }

    /** A coil at x y z was broken: if it was one of this ring's, the calibration is gone (an open portal folds). */
    public void ringChanged(int x, int y, int z) {
        BridgeStructureSC.Scan s = scan;
        if (s == null || !s.found) {
            return;
        }
        int h = (s.size - 1) / 2;
        boolean mine = false;
        for (int v = 1; v <= s.size && !mine; v++) {
            for (int u = -h; u <= h; u++) {
                int[] p = s.at(u, v);
                if (BridgeStructureSC.isRing(s.size, u, v) && p[0] == x && p[1] == y && p[2] == z) {
                    mine = true;
                    break;
                }
            }
        }
        if (!mine) {
            return;
        }
        if (open) {
            shortWhat = "";
            closePortal("sc.bridge.journal.broken");
        }
        if (calibSig != 0) {
            calibSig = 0;
            log(new BridgeMsgSC("sc.bridge.journal.calibLost"), "", true);
            markDirty();
        }
    }

    /** «Проверить»: the check now and its first problem highlighted for a few seconds. */
    public BridgeStructureSC.Scan checkBuild() {
        BridgeStructureSC.Scan s = rescan();
        highlight = null;
        if (s != null) {
            for (BridgeStructureSC.Problem p : s.problems) {
                if (p.hasPos) {
                    highlight = new int[]{p.x, p.y, p.z};
                    highlightUntil = worldObj.getTotalWorldTime() + 100;
                    break;
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ access, messages, journal

    public boolean allowed(EntityPlayer p) {
        return p == null || owner.length() == 0 || owner.equals(p.getCommandSenderName());      // null: the server itself (tests)
    }

    private void log(BridgeMsgSC m, String who, boolean bad) {
        NBTTagCompound e = new NBTTagCompound();
        e.setLong("t", System.currentTimeMillis());
        e.setString("p", who == null ? "" : who);
        e.setTag("m", m.write());
        e.setBoolean("bad", bad);
        journal.add(e);
        while (journal.size() > MAX_JOURNAL) {
            journal.remove(0);
        }
        lastMsg = m;
        lastMsgAt = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        markDirty();
    }

    private static void tell(EntityPlayer p, BridgeMsgSC m) {
        if (p != null && m != null) {
            p.addChatComponentMessage(m.chat());
        }
    }

    private String nameOf(EntityPlayer p) {
        return p == null ? "" : p.getCommandSenderName();
    }

    /** A refusal: to the chat, the journal, the screen. @return it */
    private BridgeMsgSC refuse(EntityPlayer p, BridgeMsgSC m) {
        log(m, nameOf(p), true);
        tell(p, m);
        return m;
    }

    // ------------------------------------------------------------------ the target

    public void setTarget(int x, int y, int z, int dim) {
        tx = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, x));
        ty = y == AUTO_Y ? AUTO_Y : Math.max(1, Math.min(254, y));
        tz = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, z));
        tdim = dim;
        targetSet = true;
        markDirty();
    }

    public int[] getTarget() {
        return new int[]{tx, ty, tz, targetDim()};
    }

    /** Ground: always its own world; Space: the chosen one. */
    public int targetDim() {
        return bridgeKind() == BridgeMathSC.SPACE ? tdim : worldObj == null ? 0 : worldObj.provider.dimensionId;
    }

    private int ownDim() {
        return worldObj == null ? 0 : worldObj.provider.dimensionId;
    }

    /** Horizontal distance from the ring's middle to the target. */
    public long targetDistance() {
        int[] c = scan == null ? new int[]{xCoord, yCoord, zCoord} : scan.centre();
        if (targetDim() != ownDim()) {
            return 0;
        }
        return BridgeMathSC.distance(c[0], 0, c[2], tx, 0, tz);
    }

    /** What opening to the target would take now. */
    public BridgeMathSC.Cost previewCost() {
        int kind = bridgeKind();
        BridgeMarksSC marks = worldObj == null ? null : BridgeMarksSC.get(worldObj);
        int dim = targetDim();
        boolean beacon = kind == BridgeMathSC.GROUND && marks != null && marks.near(BridgeMarksSC.BEACON, dim, tx, ty, tz, BridgeMathSC.BEACON_RADIUS);
        boolean anchor = kind == BridgeMathSC.SPACE && marks != null && marks.any(BridgeMarksSC.ANCHOR, dim);
        return BridgeMathSC.cost(kind, new long[]{targetDistance()}, beacon, anchor, scan == null ? 0 : scan.stabCount());
    }

    private WorldServer worldFor(int dim) {
        if (dim == ownDim() && worldObj instanceof WorldServer) {
            return (WorldServer) worldObj;
        }
        if (!DimensionManager.isDimensionRegistered(dim)) {
            return null;
        }
        MinecraftServer s = MinecraftServer.getServer();
        return s == null ? null : s.worldServerForDimension(dim);
    }

    /** The far end's orientation: the ring's. */
    private int ringAxis() {
        return scan == null ? 0 : scan.axis;
    }

    /** «Проверить место» (1 mB krypton): free or not, why, the nearest free place. */
    public BridgeMsgSC probe(EntityPlayer p) {
        if (!allowed(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        if (tanks[BridgeMathSC.KR] < BridgeMathSC.PROBE_KR) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.probekr", BridgeMathSC.PROBE_KR));
        }
        WorldServer w = worldFor(targetDim());
        if (w == null) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.nodim", targetDim()));
        }
        tanks[BridgeMathSC.KR] -= BridgeMathSC.PROBE_KR;
        int size = BridgeMathSC.vortexSize(bridgeKind());
        int radius = scan != null && scan.nav ? BridgeSpaceSC.NEAR_RADIUS_NAV : BridgeSpaceSC.NEAR_RADIUS;
        BridgeSpaceSC.Result r = BridgeSpaceSC.probe(BridgeSpaceSC.of(w), tx, ty, tz, size, ringAxis(), radius);
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("free", r.free);
        t.setString("reason", r.reason);
        t.setTag("args", new BridgeMsgSC("x", (Object[]) r.args).write());
        t.setIntArray("at", new int[]{r.x, r.y, r.z, targetDim()});
        if (r.free && ShieldEventHandlerPrivate.foreignField(w, p, r.x, r.y, r.z)) {
            t.setBoolean("free", false);
            t.setString("reason", "sc.bridge.place.field");
        }
        if (r.hasNearest) {
            t.setIntArray("near", new int[]{r.nx, r.ny, r.nz, r.nDist});
        }
        t.setLong("dist", targetDistance());
        t.setInteger("w", size);
        t.setBoolean("beacon", previewCost().beacon);
        t.setLong("time", worldObj.getTotalWorldTime());
        place = t;
        markDirty();
        BridgeMsgSC m = t.getBoolean("free") ? new BridgeMsgSC("sc.bridge.place.freeat", r.x, r.y, r.z)
                : new BridgeMsgSC("sc.bridge.place.blockedat", r.x, r.y, r.z).part(new BridgeMsgSC(t.getString("reason"), (Object[]) r.args));
        lastMsg = m;
        lastMsgAt = worldObj.getTotalWorldTime();
        return m;
    }

    // ------------------------------------------------------------------ calibration

    public BridgeMsgSC calibrate(EntityPlayer p) {
        if (!allowed(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        BridgeStructureSC.Scan s = rescan();
        if (s == null || !s.valid) {
            BridgeMsgSC m = new BridgeMsgSC("sc.bridge.refuse.build");
            if (s != null && !s.problems.isEmpty()) {
                m.part(problemMsg(s.problems.get(0)));
            }
            return refuse(p, m);
        }
        if (open) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.open"));
        }
        if (isCalibrated()) {
            BridgeMsgSC m = new BridgeMsgSC("sc.bridge.msg.calibrated");
            tell(p, m);
            return m;
        }
        BridgeMsgSC miss = new BridgeMsgSC("sc.bridge.refuse.calibmissing");
        if (tanks[BridgeMathSC.KR] < BridgeMathSC.CALIB_KR) {
            miss.part("sc.bridge.need.gas", gasName(BridgeMathSC.KR), BridgeMathSC.CALIB_KR, tanks[BridgeMathSC.KR]);
        }
        long eu = portEnergy() + capacitorEnergy();
        if (eu < BridgeMathSC.CALIB_EU) {
            miss.part("sc.bridge.need.eu", BridgeMathSC.group(BridgeMathSC.CALIB_EU - eu));
        }
        if (!miss.parts.isEmpty()) {
            return refuse(p, miss);
        }
        tanks[BridgeMathSC.KR] -= BridgeMathSC.CALIB_KR;
        drawAny(BridgeMathSC.CALIB_EU);
        calibSig = s.signature;
        markDirty();
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.journal.calibrated");
        log(m, nameOf(p), false);
        tell(p, m);
        return m;
    }

    private static String gasName(int tank) {
        return "@gas." + BridgeMathSC.GASES[tank].key();
    }

    public static BridgeMsgSC problemMsg(BridgeStructureSC.Problem pr) {
        return new BridgeMsgSC(pr.key, (Object[]) pr.args);
    }

    // ------------------------------------------------------------------ opening

    /**
     * «Открыть» to the stored target. Everything is checked at once; if all is there the vortex opens now.
     * @return null when it opened, else the refusal (already in the chat and the journal)
     */
    public BridgeMsgSC tryOpen(EntityPlayer p) {
        if (worldObj == null || worldObj.isRemote) {
            return null;
        }
        if (!allowed(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        BridgeStructureSC.Scan s = rescan();
        if (open) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.open"));
        }
        if (s == null || !s.valid) {
            BridgeMsgSC m = new BridgeMsgSC("sc.bridge.refuse.build");
            if (s != null && !s.problems.isEmpty()) {
                m.part(problemMsg(s.problems.get(0)));
            }
            return refuse(p, m);
        }
        if (!powerOn) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.off"));
        }
        if (!isCalibrated()) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.calib"));
        }
        if (coolTicks > 0) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.cooling", (coolTicks + 19) / 20));
        }
        if (!targetSet) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.notarget"));
        }
        int dim = targetDim();
        WorldServer w = worldFor(dim);
        if (w == null) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.nodim", dim));
        }
        if (s.kind == BridgeMathSC.GROUND && targetDistance() < MIN_DISTANCE) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.near", MIN_DISTANCE));
        }
        int size = BridgeMathSC.vortexSize(s.kind);
        BridgeSpaceSC.Result r = BridgeSpaceSC.probe(BridgeSpaceSC.of(w), tx, ty, tz, size, s.axis, 0);
        if (!r.free) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.place", r.x, r.y, r.z).part(new BridgeMsgSC(r.reason, (Object[]) r.args)));
        }
        if (ShieldEventHandlerPrivate.foreignField(w, p, r.x, r.y, r.z)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.place", r.x, r.y, r.z).part("sc.bridge.place.field"));
        }
        BridgeMathSC.Cost c = previewCost();
        BridgeMsgSC miss = new BridgeMsgSC("sc.bridge.refuse.missing");
        long have = capacitorEnergy();
        if (have < c.eu) {
            miss.part("sc.bridge.need.cap", BridgeMathSC.group(c.eu - have));
        }
        int[] needTank = new int[tanks.length];
        needTank[BridgeMathSC.SM] = c.sm;
        needTank[BridgeMathSC.D] = c.d;
        needTank[BridgeMathSC.KR] = c.kr;
        needTank[BridgeMathSC.AR] = c.ar;
        for (int i = 0; i < tanks.length; i++) {
            if (tanks[i] < needTank[i]) {
                miss.part("sc.bridge.need.gas", gasName(i), needTank[i], tanks[i]);
            }
        }
        if (!miss.parts.isEmpty()) {
            return refuse(p, miss);
        }
        // everything is there: the singularity is born now
        drawCapacitors(c.eu);
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] -= needTank[i];
        }
        open = true;
        openId = worldObj.rand.nextLong() ^ System.nanoTime();
        if (openId == 0) {
            openId = 1;
        }
        openKind = s.kind;
        hold = c;
        lifeTotal = c.lifeTicks;
        lifeLeft = c.lifeTicks;
        stability = BridgeMathSC.baseStability(s.stabCount());
        shortTicks = -1;
        shortWhat = "";
        aAxis = s.axis;
        aSize = s.size;
        bDim = dim;
        bx = r.x;
        by = r.y;
        bz = r.z;
        bAxis = s.axis;
        bW = size;
        opens++;
        placeEnds();
        litCoils(s, true);
        loadChunks();
        int[] ce = s.centre();
        worldObj.playSoundEffect(ce[0] + 0.5, ce[1] + 0.5, ce[2] + 0.5, "portal.trigger", 1.0F, s.kind == BridgeMathSC.SPACE ? 0.7F : 1.0F);
        w.playSoundEffect(bx + 0.5, by + 1.5, bz + 0.5, "portal.trigger", 1.0F, s.kind == BridgeMathSC.SPACE ? 0.7F : 1.0F);
        BridgeMsgSC m = new BridgeMsgSC(dim == ownDim() ? "sc.bridge.journal.opened" : "sc.bridge.journal.openedDim", bx, by, bz,
                BridgeMathSC.group(targetDistance()), dim);
        log(m, nameOf(p), false);
        tell(p, m);
        markDirty();
        return null;
    }

    /** The vortex cells of an end: [x, y, z, tileU, tileV]. */
    public List<int[]> endCells(int end) {
        List<int[]> out = new ArrayList<int[]>();
        if (end == 0) {
            int n = aSize, h = (n - 1) / 2;
            for (int v = 2; v < n; v++) {
                for (int u = -h + 1; u < h; u++) {
                    int[] p = BridgeStructureSC.at(xCoord, yCoord, zCoord, aAxis, u, v, 0);
                    out.add(new int[]{p[0], p[1], p[2], u + h - 1, n - 1 - v});
                }
            }
        } else {
            int w = bW, h = (w - 1) / 2;
            for (int v = 0; v < w; v++) {
                for (int u = -h; u <= h; u++) {
                    int[] p = bAxis == 0 ? new int[]{bx + u, by + v, bz} : new int[]{bx, by + v, bz + u};
                    out.add(new int[]{p[0], p[1], p[2], u + h, w - 1 - v});
                }
            }
        }
        return out;
    }

    private World endWorld(int end) {
        return end == 0 ? worldObj : DimensionManager.getWorld(bDim);
    }

    /** Puts the vortex cells that are missing (into air only). */
    private void placeEnds() {
        for (int end = 0; end < 2; end++) {
            World w = endWorld(end);
            if (w == null) {
                continue;
            }
            for (int[] c : endCells(end)) {
                if (!w.blockExists(c[0], c[1], c[2])) {
                    continue;
                }
                Block b = w.getBlock(c[0], c[1], c[2]);
                if (b == ModBlocks.bridgeVortex) {
                    TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
                    if (te instanceof TileEntityBridgeVortexSC && ((TileEntityBridgeVortexSC) te).getOpenId() == openId) {
                        continue;
                    }
                }
                if (!b.isAir(w, c[0], c[1], c[2]) && b != ModBlocks.bridgeVortex) {
                    continue;                                 // never in a block
                }
                w.setBlock(c[0], c[1], c[2], ModBlocks.bridgeVortex, openKind == BridgeMathSC.SPACE ? 1 : 0, 3);
                TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
                if (te instanceof TileEntityBridgeVortexSC) {
                    ((TileEntityBridgeVortexSC) te).setup(ownDim(), xCoord, yCoord, zCoord, end, openId, c[3], c[4]);
                }
            }
        }
    }

    private void removeEnds() {
        for (int end = 0; end < 2; end++) {
            World w = endWorld(end);
            if (w == null) {
                continue;
            }
            for (int[] c : endCells(end)) {
                if (w.blockExists(c[0], c[1], c[2]) && w.getBlock(c[0], c[1], c[2]) == ModBlocks.bridgeVortex) {
                    w.setBlockToAir(c[0], c[1], c[2]);
                }
            }
        }
    }

    /** «Закрыть», the time out, a shortage, low stability, the controller broken. `why`: the journal's key. */
    public void closePortal(String why) {
        if (!open || worldObj == null || worldObj.isRemote) {
            return;
        }
        removeEnds();
        open = false;
        litCoils(scan, false);
        releaseChunks();
        coolTotal = BridgeMathSC.coolTicks();
        coolTicks = coolTotal;
        int[] c = scan == null ? new int[]{xCoord, yCoord + 3, zCoord} : scan.centre();
        worldObj.playSoundEffect(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, "mob.endermen.portal", 1.0F, 0.5F);
        if (why != null) {
            log(new BridgeMsgSC(why, shortWhat.length() == 0 ? "" : "@" + shortWhat), "", !"sc.bridge.journal.closed".equals(why)
                    && !"sc.bridge.journal.timeout".equals(why));
        }
        markDirty();
    }

    // ------------------------------------------------------------------ while open

    private void holdTick() {
        boolean shortNow = false;
        String what = "";
        if (!drawAny(hold.holdEu)) {
            shortNow = true;
            what = "eu";
        }
        if (tick % 20 == 0) {
            boolean arShort = false;
            int[] per = new int[tanks.length];
            per[BridgeMathSC.HE] = hold.heSec;
            per[BridgeMathSC.AR] = hold.arSec;
            per[BridgeMathSC.D2O] = hold.d2oSec;
            for (int i = 0; i < tanks.length; i++) {
                if (per[i] <= 0) {
                    continue;
                }
                if (tanks[i] >= per[i]) {
                    tanks[i] -= per[i];
                } else {
                    tanks[i] = 0;
                    shortNow = true;
                    what = "gas." + BridgeMathSC.GASES[i].key();
                    if (i == BridgeMathSC.AR) {
                        arShort = true;
                    }
                }
            }
            stability = BridgeMathSC.stabilityStep(stability, BridgeMathSC.baseStability(scan == null ? 0 : scan.stabCount()), arShort);
            if (stability < BridgeMathSC.STAB_FOLD) {
                shortWhat = "";
                closePortal("sc.bridge.journal.unstable");
                return;
            }
            if (scan != null && !scan.valid) {
                shortWhat = "";
                closePortal("sc.bridge.journal.broken");
                return;
            }
            placeEnds();                                       // a cell someone took is put back (into air)
            markDirty();
        }
        if (shortNow) {
            if (shortTicks < 0) {
                shortTicks = BridgeMathSC.SHORT_GRACE_S * 20;
                shortWhat = what;
                log(new BridgeMsgSC("sc.bridge.journal.short", "@" + what, BridgeMathSC.SHORT_GRACE_S), "", true);
            } else if (--shortTicks <= 0) {
                shortWhat = what;
                closePortal("sc.bridge.journal.shortClosed");
                return;
            }
        } else if (tick % 20 == 0 && shortTicks >= 0) {
            shortTicks = -1;                                 // the resource came back
            shortWhat = "";
        }
        if (--lifeLeft <= 0) {
            shortWhat = "";
            closePortal("sc.bridge.journal.timeout");
            return;
        }
        if (ticketA == null || ticketB == null) {
            loadChunks();
        }
        if (!coilsLit && scan != null) {
            litCoils(scan, true);
        }
    }

    /** An entity touched end `end`: through to the other one. */
    public void enter(Entity e, int end) {
        if (!open || e == null || e.isDead || e.worldObj == null || e.worldObj.isRemote) {
            return;
        }
        if (e.ridingEntity != null || e.riddenByEntity != null) {
            return;
        }
        boolean player = e instanceof EntityPlayer;
        boolean mass = scan != null && scan.mass;
        if (!player && !(mass && (e instanceof EntityLiving || e instanceof EntityItem || e instanceof EntityMinecart || e instanceof EntityXPOrb))) {
            return;
        }
        if (end == 1 && e instanceof IMob && scan != null && scan.shield) {
            return;                                         // the Portal Shield: nothing hostile comes in
        }
        long now = e.worldObj.getTotalWorldTime();
        NBTTagCompound data = e.getEntityData();
        if (data.getLong("scBridgeCd") > now) {
            return;
        }
        double x, y, z;
        float yaw;
        int dim;
        if (end == 0) {
            dim = bDim;
            int[] o = bAxis == 0 ? new int[]{0, 0, 1} : new int[]{1, 0, 0};
            x = bx + 0.5 + o[0];
            y = by;
            z = bz + 0.5 + o[2];
            yaw = bAxis == 0 ? 0F : -90F;
        } else {
            dim = ownDim();
            int d = exitSideA();
            int[] p = BridgeStructureSC.at(xCoord, yCoord, zCoord, aAxis, 0, 2, d);
            x = p[0] + 0.5;
            y = p[1];
            z = p[2] + 0.5;
            yaw = aAxis == 0 ? (d > 0 ? 0F : 180F) : (d > 0 ? -90F : 90F);
        }
        data.setLong("scBridgeCd", now + BridgeMathSC.TELEPORT_COOLDOWN);
        World from = e.worldObj;
        double fx = e.posX, fy = e.posY, fz = e.posZ;
        Entity moved = BridgeTeleportSC.teleport(e, dim, x, y, z, yaw);
        if (moved != null) {
            moved.getEntityData().setLong("scBridgeCd", now + BridgeMathSC.TELEPORT_COOLDOWN);
            from.playSoundEffect(fx, fy, fz, "mob.endermen.portal", 0.8F, 1.0F);
            moved.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 0.8F, 1.0F);
        }
    }

    /** Coming out at the ring: the side the controller's screen faces when it faces along the ring's normal, else +1. */
    private int exitSideA() {
        if (aAxis == 0) {
            return facing == 2 ? -1 : 1;                     // 2 north (-Z), 3 south (+Z)
        }
        return facing == 4 ? -1 : 1;                         // 4 west (-X), 5 east (+X)
    }

    // ------------------------------------------------------------------ chunks

    private ForgeChunkManager.Ticket ticket(World w) {
        ForgeChunkManager.Ticket t = ForgeChunkManager.requestTicket(com.sc.SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
        if (t != null) {
            NBTTagCompound d = t.getModData();
            d.setString("Kind", "bridge");
            d.setInteger("x", xCoord);
            d.setInteger("y", yCoord);
            d.setInteger("z", zCoord);
            d.setInteger("dim", ownDim());
        }
        return t;
    }

    private void loadChunks() {
        if (ticketA == null) {
            ticketA = ticket(worldObj);
            if (ticketA != null) {
                forceAround(ticketA, endCells(0));
                ForgeChunkManager.forceChunk(ticketA, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            }
        }
        if (ticketB == null) {
            World w = DimensionManager.getWorld(bDim);
            if (w == null) {
                w = worldFor(bDim);
            }
            if (w != null) {
                ticketB = ticket(w);
                if (ticketB != null) {
                    forceAround(ticketB, endCells(1));
                }
            }
        }
    }

    private static void forceAround(ForgeChunkManager.Ticket t, List<int[]> cells) {
        Set<Long> done = new HashSet<Long>();
        for (int[] c : cells) {
            int cx = c[0] >> 4, cz = c[2] >> 4;
            if (done.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL))) {
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
            }
        }
    }

    private void releaseChunks() {
        if (ticketA != null) {
            ForgeChunkManager.releaseTicket(ticketA);
            ticketA = null;
        }
        if (ticketB != null) {
            ForgeChunkManager.releaseTicket(ticketB);
            ticketB = null;
        }
    }

    /** ChunkLoaderSC: after a load, the controller's own ticket comes back while the portal is open. */
    public void adoptTicket(ForgeChunkManager.Ticket t) {
        if (!open || ticketA != null) {
            ForgeChunkManager.releaseTicket(t);
            return;
        }
        ticketA = t;
        forceAround(t, endCells(0));
        ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        tick++;
        if (scan == null || tick % 20 == 0) {
            rescan();
        }
        if (open) {
            holdTick();
        }
        if (powerOn) {
            chargeTick();
        }
        if (!open && coolTicks > 0) {
            int step = 1;
            if (scan != null && scan.cooler) {
                if (tick % 20 == 0) {
                    coolHeDebt += BridgeMathSC.COOLER_HE_PER_S;
                }
                if (coolHeDebt > 0 && tanks[BridgeMathSC.HE] >= coolHeDebt) {
                    tanks[BridgeMathSC.HE] -= coolHeDebt;
                    coolHeDebt = 0;
                }
                if (coolHeDebt == 0) {
                    step = BridgeMathSC.COOLER_SPEED;
                }
            }
            coolTicks = Math.max(0, coolTicks - step);
            if (coolTicks == 0) {
                coolHeDebt = 0;
                markDirty();
            }
        }
        if (tick % 20 == 0) {
            chargeRate = chargedThisSecond;
            chargedThisSecond = 0;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (worldObj != null && !worldObj.isRemote) {
            releaseChunks();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        ticketA = null;
        ticketB = null;
    }

    // ------------------------------------------------------------------ the screen's actions (BridgeNetSC)

    public static final int A_CHECK = 2, A_CALIBRATE = 3, A_PROBE = 4, A_OPEN = 6, A_CLOSE = 7, A_BM_ADD = 8, A_BM_RENAME = 9,
            A_BM_DELETE = 10, A_POWER = 12, A_CLEAR = 13, A_TARGET = 14, A_MODE = 15;

    public void action(EntityPlayer p, int a, int[] v, String s) {
        switch (a) {
            case A_CHECK:
                checkBuild();
                return;
            case A_TARGET:
                if (v.length >= 4 && allowed(p)) {
                    setTarget(v[0], v[1], v[2], v[3]);
                }
                return;
            case A_PROBE:
                if (v.length >= 4 && allowed(p)) {
                    setTarget(v[0], v[1], v[2], v[3]);
                }
                probe(p);
                return;
            case A_CALIBRATE:
                calibrate(p);
                return;
            case A_OPEN:
                if (v.length >= 4 && allowed(p)) {
                    setTarget(v[0], v[1], v[2], v[3]);
                }
                tryOpen(p);
                return;
            case A_MODE:
                return;                                          // stage 2: ДР1-ДР5
            default:
                break;
        }
        if (!allowed(p)) {
            refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
            return;
        }
        switch (a) {
            case A_CLOSE:
                if (open) {
                    shortWhat = "";
                    closePortal("sc.bridge.journal.closed");
                }
                return;
            case A_POWER:
                setPowerOn(!powerOn);
                com.sc.util.SoundsSC.powerClick(this, powerOn);
                return;
            case A_CLEAR:
                if (v.length >= 1) {
                    clearTank(v[0]);
                }
                return;
            case A_BM_ADD:
                if (bookmarks.size() >= maxBookmarks()) {
                    refuse(p, new BridgeMsgSC("sc.bridge.refuse.bookmarks", maxBookmarks()));
                    return;
                }
                if (v.length >= 4) {
                    NBTTagCompound b = new NBTTagCompound();
                    b.setString("n", cut(s));
                    b.setIntArray("p", new int[]{v[0], v[1], v[2], v[3]});
                    bookmarks.add(b);
                    markDirty();
                }
                return;
            case A_BM_RENAME:
                if (v.length >= 1 && v[0] >= 0 && v[0] < bookmarks.size()) {
                    bookmarks.get(v[0]).setString("n", cut(s));
                    markDirty();
                }
                return;
            case A_BM_DELETE:
                if (v.length >= 1 && v[0] >= 0 && v[0] < bookmarks.size()) {
                    bookmarks.remove(v[0]);
                    markDirty();
                }
                return;
            default:
                break;
        }
    }

    private static String cut(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 32 ? t.substring(0, 32) : t;
    }

    /** Everything the screen shows (BridgeNetSC sends it twice a second while the screen is open). */
    public NBTTagCompound writeState(EntityPlayer viewer) {
        if (scan == null) {
            rescan();
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setString("owner", owner);
        t.setBoolean("allowed", allowed(viewer));
        t.setBoolean("power", powerOn);
        t.setInteger("mode", mode);
        t.setInteger("access", access);
        t.setInteger("wear", wear);
        BridgeStructureSC.Scan s = scan;
        if (s != null) {
            t.setBoolean("found", s.found);
            t.setInteger("kind", s.kind);
            t.setInteger("size", s.size);
            t.setInteger("axis", s.axis);
            t.setIntArray("counts", new int[]{s.coils, s.coilsNeeded, s.focusers, s.focusersNeeded, s.capacitors.size(), s.energyPorts.size(),
                    s.gasPorts.size(), s.stabCount(), s.junk});
            t.setByteArray("cells", s.cells);
            t.setBoolean("valid", s.valid);
            t.setInteger("modules", (s.nav ? 1 : 0) | (s.mass ? 2 : 0) | (s.cooler ? 4 : 0) | (s.shield ? 8 : 0));
            NBTTagList pr = new NBTTagList();
            for (int i = 0; i < s.problems.size() && i < 6; i++) {
                pr.appendTag(problemMsg(s.problems.get(i)).write());
            }
            t.setTag("problems", pr);
            t.setInteger("problemCount", s.problems.size());
        }
        t.setBoolean("calibrated", isCalibrated());
        t.setBoolean("calibOld", calibSig != 0 && !isCalibrated());
        t.setBoolean("open", open);
        t.setIntArray("time", new int[]{lifeLeft, lifeTotal, coolTicks, coolTotal, stability, shortTicks});
        t.setString("shortWhat", shortWhat);
        if (open) {
            t.setIntArray("endB", new int[]{bDim, bx, by, bz});
        }
        t.setLong("capEu", capacitorEnergy());
        t.setLong("capMax", capacitorMax());
        t.setLong("portEu", portEnergy());
        t.setLong("rate", chargeRate);
        int[] caps = new int[tanks.length];
        for (int i = 0; i < caps.length; i++) {
            caps[i] = tankCapacity(i);
        }
        t.setIntArray("tanks", tanks.clone());
        t.setIntArray("tankCaps", caps);
        t.setIntArray("target", new int[]{tx, ty, tz, targetDim()});
        t.setBoolean("targetSet", targetSet);
        BridgeMathSC.Cost c = previewCost();
        t.setLong("costEu", c.eu);
        t.setIntArray("cost", new int[]{c.sm, c.d, c.kr, c.ar, c.holdEu, c.heSec, c.arSec, c.d2oSec, c.lifeTicks, c.beacon ? 1 : 0, c.anchor ? 1 : 0});
        t.setLong("dist", targetDistance());
        if (place != null) {
            t.setTag("place", place);
        }
        NBTTagList bm = new NBTTagList();
        for (NBTTagCompound b : bookmarks) {
            bm.appendTag(b.copy());
        }
        t.setTag("bookmarks", bm);
        t.setInteger("bmMax", maxBookmarks());
        NBTTagList jl = new NBTTagList();
        for (NBTTagCompound j : journal) {
            jl.appendTag(j.copy());
        }
        t.setTag("journal", jl);
        if (highlight != null && worldObj.getTotalWorldTime() < highlightUntil) {
            t.setIntArray("hl", highlight);
            t.setInteger("hlTicks", (int) (highlightUntil - worldObj.getTotalWorldTime()));
        }
        if (lastMsg != null && worldObj.getTotalWorldTime() - lastMsgAt < 200) {
            t.setTag("last", lastMsg.write());
        }
        t.setInteger("dimHere", ownDim());
        if (s != null && s.kind == BridgeMathSC.SPACE) {
            NBTTagList dims = new NBTTagList();
            for (Integer id : DimensionManager.getStaticDimensionIDs()) {
                NBTTagCompound d = new NBTTagCompound();
                d.setInteger("id", id);
                String name;
                try {
                    World dw = DimensionManager.getWorld(id);
                    name = dw != null ? dw.provider.getDimensionName() : DimensionManager.createProviderFor(id).getDimensionName();
                } catch (Throwable ex) {
                    name = "DIM" + id;
                }
                d.setString("n", name);
                dims.appendTag(d);
            }
            t.setTag("dims", dims);
        }
        return t;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        owner = nbt.getString("Owner");
        facing = nbt.hasKey("Facing") ? nbt.getInteger("Facing") : 3;
        powerOn = nbt.getBoolean("PowerOn");
        readTanks(nbt);
        calibSig = nbt.getLong("CalibSig");
        coolTicks = nbt.getInteger("Cool");
        coolTotal = nbt.getInteger("CoolTotal");
        mode = nbt.getInteger("Mode");
        access = nbt.getInteger("Access");
        wear = nbt.getInteger("Wear");
        int[] t = nbt.getIntArray("Target");
        if (t.length == 4) {
            tx = t[0];
            ty = t[1];
            tz = t[2];
            tdim = t[3];
        }
        targetSet = nbt.getBoolean("TargetSet");
        place = nbt.hasKey("Place") ? nbt.getCompoundTag("Place") : null;
        readBookmarks(nbt);
        journal.clear();
        NBTTagList jl = nbt.getTagList("Journal", 10);
        for (int i = 0; i < jl.tagCount(); i++) {
            journal.add(jl.getCompoundTagAt(i));
        }
        opens = nbt.getInteger("Opens");
        open = nbt.getBoolean("Open");
        openId = nbt.getLong("OpenId");
        int[] o = nbt.getIntArray("Portal");
        if (o.length == 15) {
            openKind = o[0];
            lifeLeft = o[1];
            lifeTotal = o[2];
            stability = o[3];
            shortTicks = o[4];
            aAxis = o[5];
            aSize = o[6];
            bDim = o[7];
            bx = o[8];
            by = o[9];
            bz = o[10];
            bAxis = o[11];
            bW = o[12];
            if (open) {
                hold = BridgeMathSC.cost(openKind, new long[]{o[13]}, false, o[14] != 0, 0);
            }
        } else {
            open = false;
        }
        shortWhat = nbt.getString("ShortWhat");
    }

    private void readTanks(NBTTagCompound nbt) {
        int[] a = nbt.getIntArray("BridgeTanks");
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] = i < a.length ? Math.max(0, a[i]) : 0;
        }
    }

    private void readBookmarks(NBTTagCompound nbt) {
        bookmarks.clear();
        NBTTagList bm = nbt.getTagList("Bookmarks", 10);
        for (int i = 0; i < bm.tagCount() && i < BOOKMARKS_NAV; i++) {
            bookmarks.add(bm.getCompoundTagAt(i));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("Owner", owner);
        nbt.setInteger("Facing", facing);
        nbt.setBoolean("PowerOn", powerOn);
        nbt.setIntArray("BridgeTanks", tanks.clone());
        nbt.setLong("CalibSig", calibSig);
        nbt.setInteger("Cool", coolTicks);
        nbt.setInteger("CoolTotal", coolTotal);
        nbt.setInteger("Mode", mode);
        nbt.setInteger("Access", access);
        nbt.setInteger("Wear", wear);
        nbt.setIntArray("Target", new int[]{tx, ty, tz, tdim});
        nbt.setBoolean("TargetSet", targetSet);
        if (place != null) {
            nbt.setTag("Place", place);
        }
        writeBookmarks(nbt);
        NBTTagList jl = new NBTTagList();
        for (NBTTagCompound j : journal) {
            jl.appendTag(j.copy());
        }
        nbt.setTag("Journal", jl);
        nbt.setInteger("Opens", opens);
        nbt.setBoolean("Open", open);
        nbt.setLong("OpenId", openId);
        long dist = hold == null ? 0 : (hold.eu - BridgeMathSC.GROUND_BURST) * 1000 / BridgeMathSC.GROUND_PER_1000;
        nbt.setIntArray("Portal", new int[]{openKind, lifeLeft, lifeTotal, stability, shortTicks, aAxis, aSize, bDim, bx, by, bz, bAxis, bW,
                (int) Math.max(0, Math.min(Integer.MAX_VALUE, openKind == BridgeMathSC.GROUND ? dist : 0)), hold != null && hold.anchor ? 1 : 0});
        nbt.setString("ShortWhat", shortWhat);
    }

    private void writeBookmarks(NBTTagCompound nbt) {
        NBTTagList bm = new NBTTagList();
        for (NBTTagCompound b : bookmarks) {
            bm.appendTag(b.copy());
        }
        nbt.setTag("Bookmarks", bm);
    }

    /** The item keeps the tanks and the bookmarks. */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        boolean any = false;
        for (int a : tanks) {
            any |= a > 0;
        }
        if (any) {
            nbt.setIntArray("BridgeTanks", tanks.clone());
        }
        if (!bookmarks.isEmpty()) {
            writeBookmarks(nbt);
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        readTanks(nbt);
        readBookmarks(nbt);
        markDirty();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Facing", facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        facing = pkt.func_148857_g().getInteger("Facing");
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }

    // ------------------------------------------------------------------ tests

    /** World test: fill a tank directly. */
    public void putTankForTest(int i, int mb) {
        tanks[i] = Math.max(0, mb);
    }

    /** Is the field generator at the target someone else's (private for this player)? */
    static final class ShieldEventHandlerPrivate {
        static boolean foreignField(World w, EntityPlayer p, int x, int y, int z) {
            return p != null && com.sc.ShieldEventHandler.privateFor(w, p, x, y, z);
        }
    }
}
