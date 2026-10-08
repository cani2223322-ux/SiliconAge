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
import com.sc.bridge.BridgeSoftLandSC;
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
 * Stage 2: remotes, the armour, the modes ДР1-ДР5, access. Stage 3 (§9): wear and «Ремонт» (С2), the stability from its factors
 * and turbulence (С3), the mass of a pass (С4), familiar places and scatter (С5), interference and resonance (С6), scouting (С7),
 * the shortage warning (С11), heat and the overheat lock (С12); §11 the birth, the coils' heat colours, the shaking vortex, the hologram.
 */
public class TileEntityBridgeControllerSC extends TileEntity {

    public static final int MAX_JOURNAL = 20, BOOKMARKS = 8, BOOKMARKS_NAV = 32;
    /** Ground: the target at least this far from the ring. */
    public static final int MIN_DISTANCE = 16;
    public static final int WORLD_LIMIT = 29999000;
    public static final int AUTO_Y = Integer.MIN_VALUE;
    /** Access: owner and friends (BridgeMathSC.ACCESS_FRIENDS), public. */
    public static final int ACCESS_OWNER = BridgeMathSC.ACCESS_FRIENDS;

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

    // stage 2: the name, friends, «Дистанционный режим», the bridge's id (remotes / helmets check it), who is linked
    private String name = "";
    private final List<String> friends = new ArrayList<String>();
    private boolean remoteMode;
    private long bridgeId;
    /** Bound remotes and helmets: {"p": player, "k": SRC_REMOTE / SRC_ARMOUR, "t": time}. */
    private final List<NBTTagCompound> links = new ArrayList<NBTTagCompound>();
    /** A server-side target change (a coordinator) the screen must take: counted up. */
    private int targetRev;
    // the open portal, stage 2: end A away from the ring (a projection), the mode, who opened it
    private boolean aProj;
    private int aDim, ax, ay, az, apAxis, aW;
    private int openMode;
    /** The total world time this portal opened (its vortex cells tell their clients - the disc grows from a point). */
    private long openedAt;
    private String opener = "";
    private boolean openPrecise;
    // stage 3 (§9): the ring's heat (tenths of a percent) and its overheat lock (С12); the argon shortage (С3); what
    // this opening has done so far (С2 wear at the close): its distance, the mass through it, its peak heat; the
    // far end's scatter (С5 / С7)
    private int heat, heatAtClose;
    private boolean overheatLock, heatWarned;
    private int argonDeficit;
    private long openDist;
    private boolean openOtherDim;
    private int massTotal, peakHeat, openScatter, openShift;

    // ------------------------------------------------------------------ transient

    private BridgeStructureSC.Scan scan;
    private final Set<Long> partKeys = new HashSet<Long>();
    private final List<int[]> linked = new ArrayList<int[]>();
    private int tick;
    /** ticketC: the controller's own chunk while the portal is open and end A is in another world (ticketA holds it otherwise). */
    private ForgeChunkManager.Ticket ticketA, ticketB, ticketR, ticketC;
    private long chargedThisSecond, chargeRate;
    private int[] highlight;
    private long highlightUntil;
    private BridgeMsgSC lastMsg;
    private long lastMsgAt;
    private int coolHeDebt;
    /** С4: the mass that passed lately - {until (world time), tenths}. */
    private final List<long[]> massLoad = new ArrayList<long[]>();
    /** С6, looked at once a second: another bridge near, a thunderstorm at an end, a running Singular reactor near the ring. */
    private boolean envInterf, envStorm, envRes, envKnown;
    /** С3: the vortex shakes (its cells are told); the coils' colour now. */
    private boolean turbulent;
    private int coilsMeta = -1;
    /** Client: the hologram over the ring while the portal is open {x, y, z, dim, ring size}, or null. */
    private int[] holo;

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

    public int getWear() {
        return wear;
    }

    /** The ring's heat, tenths of a percent (С12). */
    public int getHeat() {
        return heat;
    }

    public boolean isOverheatLocked() {
        return overheatLock;
    }

    public boolean isTurbulent() {
        return turbulent;
    }

    /** The far end's scatter radius of the open portal and how far it was really shifted (С5 / С7). */
    public int[] getOpenScatter() {
        return new int[]{openScatter, openShift};
    }

    /** Client: the hologram over the ring {x, y, z, dim, ring size}, or null. */
    public int[] getHolo() {
        return holo;
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
        // a build problem (junk inside, a side taken, a port off) keeps the calibration: only the ring's signature or a
        // coil out and back (ringChanged) loses it
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

    /** The ring's coils glow while a portal is open and show its heat (metadata BridgeMathSC.coilMeta: 0 dark ... 3 hot). */
    private void litCoils(BridgeStructureSC.Scan s, boolean lit) {
        setCoils(s, BridgeMathSC.coilMeta(lit, heat));
    }

    private void setCoils(BridgeStructureSC.Scan s, int meta) {
        if (s == null || !s.found) {
            return;
        }
        int h = (s.size - 1) / 2;
        for (int v = 1; v <= s.size; v++) {
            for (int u = -h; u <= h; u++) {
                if (BridgeStructureSC.isRing(s.size, u, v)) {
                    int[] p = s.at(u, v);
                    if (worldObj.getBlock(p[0], p[1], p[2]) == ModBlocks.gravityCoil && worldObj.getBlockMetadata(p[0], p[1], p[2]) != meta) {
                        worldObj.setBlockMetadataWithNotify(p[0], p[1], p[2], meta, 3);
                    }
                }
            }
        }
        coilsLit = meta > 0;
        coilsMeta = meta;
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
        // М-4: a coil out / back no longer takes wear off (only «Ремонт» does); the calibration is lost below
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

    /**
     * May use the controller's screen: the owner, a friend, anyone in the public mode (null: the server itself - tests).
     * М-7: a controller without an owner (placed by automation) - anyone may open / close portals from it, but its
     * settings and bindings need an owner (ownerlessRefusal); the first player to right-click it becomes its owner (claim).
     */
    public boolean allowed(EntityPlayer p) {
        return trusted(p) || access == BridgeMathSC.ACCESS_PUBLIC || owner.length() == 0;
    }

    /** The owner or a friend: remotes and helmets bind and work only for them (§10); without an owner - nobody (М-7). */
    public boolean trusted(EntityPlayer p) {
        return p == null || owner.length() > 0 && (isOwner(p) || isFriend(p.getCommandSenderName()));
    }

    public boolean isOwner(EntityPlayer p) {
        return p == null || owner.length() > 0 && owner.equals(p.getCommandSenderName());
    }

    /** No owner yet. */
    public boolean ownerless() {
        return owner.length() == 0;
    }

    /**
     * М-7: the first player to right-click an ownerless controller (or bind a remote to it) becomes its owner.
     * @return whether p became the owner now
     */
    public boolean claim(EntityPlayer p) {
        if (p == null || worldObj == null || worldObj.isRemote || owner.length() > 0 || p instanceof net.minecraftforge.common.util.FakePlayer) {
            return false;
        }
        setOwner(p.getCommandSenderName());
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.msg.claimed");
        log(m, nameOf(p), false);
        tell(p, m);
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    /** М-7: the settings / bindings of an ownerless controller are refused (said to the player); null - go on. */
    public BridgeMsgSC ownerlessRefusal(EntityPlayer p) {
        if (p == null || owner.length() > 0) {
            return null;
        }
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.refuse.noowner");
        tell(p, m);
        return m;
    }

    public boolean isFriend(String n) {
        for (String f : friends) {
            if (f.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getFriends() {
        return friends;
    }

    public int getAccess() {
        return access;
    }

    public void setAccess(int a) {
        access = a == BridgeMathSC.ACCESS_PUBLIC ? a : BridgeMathSC.ACCESS_FRIENDS;
        markDirty();
    }

    /** Adds a friend (not the owner, not twice, at most MAX_FRIENDS, a plausible name). @return whether added */
    public boolean addFriend(String n) {
        String t = n == null ? "" : n.trim();
        if (t.length() < 2 || t.length() > 16 || !t.matches("[A-Za-z0-9_]+") || t.equalsIgnoreCase(owner) || isFriend(t)
                || friends.size() >= BridgeMathSC.MAX_FRIENDS) {
            return false;
        }
        friends.add(t);
        markDirty();
        return true;
    }

    public void removeFriend(int i) {
        if (i >= 0 && i < friends.size()) {
            friends.remove(i);
            markDirty();
        }
    }

    public String getBridgeName() {
        return name;
    }

    public void setBridgeName(String n) {
        name = cut(n);
        markDirty();
    }

    /** The name for a BridgeMsgSC argument: the name, or "@noname" (translated by the reader). */
    public String nameArg() {
        return name.length() == 0 ? "@noname" : name;
    }

    /** The name for a chat translation argument. */
    public Object nameArgChat() {
        return name.length() == 0 ? new net.minecraft.util.ChatComponentTranslation(BridgeMsgSC.RES + "noname") : name;
    }

    /** The bridge's id (made once): a remote or a helmet bound to a controller that was broken and put back elsewhere is not fooled. */
    public long ensureBridgeId() {
        if (bridgeId == 0) {
            bridgeId = (worldObj == null ? new java.util.Random().nextLong() : worldObj.rand.nextLong()) ^ System.nanoTime();
            if (bridgeId == 0) {
                bridgeId = 1;
            }
            markDirty();
        }
        return bridgeId;
    }

    public long getBridgeId() {
        return bridgeId;
    }

    public boolean isRemoteMode() {
        return remoteMode;
    }

    /** A remote or a helmet was bound: remembered for the screen (the newest 16). */
    public void noteLink(EntityPlayer p, int kind) {
        String who = nameOf(p);
        for (int i = links.size() - 1; i >= 0; i--) {
            if (links.get(i).getString("p").equalsIgnoreCase(who) && links.get(i).getInteger("k") == kind) {
                links.remove(i);
            }
        }
        NBTTagCompound e = new NBTTagCompound();
        e.setString("p", who);
        e.setInteger("k", kind);
        e.setLong("t", System.currentTimeMillis());
        links.add(e);
        while (links.size() > 16) {
            links.remove(0);
        }
        log(new BridgeMsgSC(kind == BridgeMathSC.SRC_ARMOUR ? "sc.bridge.journal.linkArmour" : "sc.bridge.journal.linkRemote"), who, false);
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

    /** М-3: the ticks between two open attempts of one player (any bridge, any way: the screen, a remote, the armour). */
    public static final int OPEN_COOLDOWN_TICKS = 20;
    /** М-3: a player's last open attempt (the server's tick). */
    private static final java.util.Map<String, Long> LAST_OPEN_TRY = new java.util.HashMap<String, Long>();
    /** М-3: a player's last «Проверить место» (its own limit: a check and «Открыть» right after it both go). */
    private static final java.util.Map<String, Long> LAST_PROBE_TRY = new java.util.HashMap<String, Long>();

    /**
     * М-3: open attempts are rate-limited per player (at most one a second) - spamming «Открыть» can't make the server
     * load / generate chunks at a target faster than that. The refusal goes to the chat only (not the journal).
     * @return null - go on (the attempt is counted); else the refusal
     */
    public static BridgeMsgSC openThrottle(EntityPlayer p) {
        return throttle(p, LAST_OPEN_TRY, "sc.bridge.refuse.tooFast");
    }

    /** М-3: «Проверить место» (the screen, a remote, the armour) reads / generates chunks at any point too - one a second. */
    public static BridgeMsgSC probeThrottle(EntityPlayer p) {
        return throttle(p, LAST_PROBE_TRY, "sc.bridge.refuse.probeTooFast");
    }

    private static BridgeMsgSC throttle(EntityPlayer p, java.util.Map<String, Long> tries, String key) {
        if (p == null || p instanceof net.minecraftforge.common.util.FakePlayer || MinecraftServer.getServer() == null) {
            return null;                                     // fake players don't send packets (the world tests drive them)
        }
        long now = com.sc.bridge.BridgeFarSC.now();
        String k = p.getCommandSenderName().toLowerCase(java.util.Locale.ROOT);
        synchronized (tries) {
            Long last = tries.get(k);
            if (last != null && now >= last && now - last < OPEN_COOLDOWN_TICKS) {
                BridgeMsgSC m = new BridgeMsgSC(key);
                tell(p, m);
                return m;
            }
            if (tries.size() > 256) {
                tries.clear();
            }
            tries.put(k, now);
        }
        return null;
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

    /** What opening to the target would take now (the server itself: no familiar-place price). */
    public BridgeMathSC.Cost previewCost() {
        return previewCost(null);
    }

    /** What opening to the target would take now for `viewer` (С5 his familiar places, С6 the resonance). */
    public BridgeMathSC.Cost previewCost(EntityPlayer viewer) {
        int kind = bridgeKind();
        BridgeMarksSC marks = worldObj == null ? null : BridgeMarksSC.get(worldObj);
        int dim = targetDim();
        boolean beacon = kind == BridgeMathSC.GROUND && marks != null && marks.near(BridgeMarksSC.BEACON, dim, tx, ty, tz, BridgeMathSC.BEACON_RADIUS);
        boolean anchor = kind == BridgeMathSC.SPACE && marks != null && marks.any(BridgeMarksSC.ANCHOR, dim);
        BridgeMathSC.Cost c = BridgeMathSC.cost(kind, new long[]{targetDistance()}, beacon, anchor, scan == null ? 0 : scan.stabCount());
        int[] f = famInfo(viewer, dim, tx, tz, beacon, anchor, false);
        if (!envKnown) {
            refreshEnv();
        }
        return BridgeMathSC.adjust(c, f[1], envRes);
    }

    /**
     * С5 / С7 for a target point: {familiar (1 / 0; -1 the server itself - neutral), the price percent, the scatter
     * radius, scouting (1 / 0)}. A Receiver Beacon makes a place familiar.
     */
    public int[] famInfo(EntityPlayer p, int dim, int x, int z, boolean beacon, boolean anchor, boolean preciseFind) {
        if (p == null) {
            return new int[]{-1, 0, 0, 0};
        }
        BridgeFamiliarSCRef f = new BridgeFamiliarSCRef(worldObj);
        String me = p.getCommandSenderName();
        boolean familiar = beacon || f.familiar(me, dim, x, z);
        boolean scouting = bridgeKind() == BridgeMathSC.SPACE && dim != ownDim() && !anchor && !f.scouted(me, dim);
        int r = BridgeMathSC.scatterRadius(familiar, beacon, scan != null && scan.nav, preciseFind, scouting);
        return new int[]{familiar ? 1 : 0, familiar ? BridgeMathSC.FAMILIAR_PCT : BridgeMathSC.UNFAMILIAR_PCT, r, scouting ? 1 : 0};
    }

    /** The familiar-places store (null-safe). */
    private static final class BridgeFamiliarSCRef {
        final com.sc.bridge.BridgeFamiliarSC f;

        BridgeFamiliarSCRef(World w) {
            f = com.sc.bridge.BridgeFamiliarSC.get(w);
        }

        boolean familiar(String p, int dim, int x, int z) {
            return f != null && f.familiar(p, dim, x, z);
        }

        boolean scouted(String p, int dim) {
            return f != null && f.scouted(p, dim);
        }
    }

    // ------------------------------------------------------------------ stage 3: the environment, stability, mass, heat

    /** С6, once a second: another bridge controller within 64 blocks, a thunderstorm at an end, a running Singular reactor within 32. */
    private void refreshEnv() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        int[] c = ringCentre();
        envInterf = BridgeMathSC.interferers(xCoord, yCoord, zCoord, controllersNear(worldObj, xCoord, zCoord, BridgeMathSC.INTERFERENCE_RADIUS),
                BridgeMathSC.INTERFERENCE_RADIUS) > 0;
        boolean storm = stormAt(worldObj, c[0], c[2]);
        if (open) {
            World wb = DimensionManager.getWorld(bDim);
            storm |= wb != null && stormAt(wb, bx, bz);
            if (aProj) {
                World wa = DimensionManager.getWorld(aDim);
                storm |= wa != null && stormAt(wa, ax, az);
            }
        }
        envStorm = storm || stormForTest;
        envRes = com.sc.item.SingularSensesSC.sourceNear(worldObj, c[0], c[1], c[2], BridgeMathSC.RESONANCE_RADIUS) == 2;
        envKnown = true;
    }

    /** Test hook: a thunderstorm at an end. */
    private boolean stormForTest;

    public void setStormForTest(boolean s) {
        stormForTest = s;
    }

    private static boolean stormAt(World w, int x, int z) {
        return w.isThundering() && w.blockExists(x, 64, z) && w.getBiomeGenForCoords(x, z).canSpawnLightningBolt();
    }

    /** The bridge controllers in the loaded chunks within `r` blocks across (this one too). */
    private static List<int[]> controllersNear(World w, int x, int z, int r) {
        List<int[]> l = new ArrayList<int[]>();
        for (int chX = (x - r) >> 4; chX <= (x + r) >> 4; chX++) {
            for (int chZ = (z - r) >> 4; chZ <= (z + r) >> 4; chZ++) {
                if (!w.getChunkProvider().chunkExists(chX, chZ)) {
                    continue;
                }
                for (Object o : w.getChunkFromChunkCoords(chX, chZ).chunkTileEntityMap.values()) {
                    if (o instanceof TileEntityBridgeControllerSC && !((TileEntity) o).isInvalid()) {
                        TileEntity te = (TileEntity) o;
                        l.add(new int[]{te.xCoord, te.yCoord, te.zCoord});
                    }
                }
            }
        }
        return l;
    }

    /** С4: the mass that passed in the last MASS_TICKS (tenths). */
    private int massNow() {
        long now = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        int t = 0;
        for (int i = massLoad.size() - 1; i >= 0; i--) {
            if (massLoad.get(i)[0] <= now) {
                massLoad.remove(i);
            } else {
                t += (int) massLoad.get(i)[1];
            }
        }
        return t;
    }

    /** С3: the stability now with each factor (closed: what a vortex would start at). */
    public BridgeMathSC.Stab stabNow() {
        if (!envKnown) {
            refreshEnv();
        }
        return BridgeMathSC.stability(scan == null ? 0 : scan.stabCount(), wear, massNow(), scan != null && scan.mass, envInterf, envStorm,
                open ? argonDeficit : 0);
    }

    /** С4 mass of one pass, tenths of a unit. */
    public static int massOf(Entity e) {
        if (e instanceof EntityPlayer) {
            return BridgeMathSC.MASS_PLAYER;
        }
        if (e instanceof EntityMinecart) {
            return BridgeMathSC.MASS_CART;
        }
        if (e instanceof EntityLiving) {
            return BridgeMathSC.MASS_MOB;
        }
        return BridgeMathSC.MASS_ITEM;
    }

    /** «Ремонт» (С2): the wear back to 0 for helium and EU, at once. */
    public BridgeMsgSC repair(EntityPlayer p) {
        if (!allowed(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        if (open) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.open"));
        }
        if (wear <= 0) {
            BridgeMsgSC m = new BridgeMsgSC("sc.bridge.msg.noWear");
            lastMsg = m;
            lastMsgAt = worldObj == null ? 0 : worldObj.getTotalWorldTime();
            tell(p, m);
            return m;
        }
        int he = BridgeMathSC.repairHe(wear);
        long eu = BridgeMathSC.repairEu(wear);
        BridgeMsgSC miss = new BridgeMsgSC("sc.bridge.refuse.repairmissing");
        if (tanks[BridgeMathSC.HE] < he) {
            miss.part("sc.bridge.need.gas", gasName(BridgeMathSC.HE), he, tanks[BridgeMathSC.HE]);
        }
        long have = portEnergy() + capacitorEnergy();
        if (have < eu) {
            miss.part("sc.bridge.need.eu", BridgeMathSC.group(eu - have));
        }
        if (!miss.parts.isEmpty()) {
            return refuse(p, miss);
        }
        tanks[BridgeMathSC.HE] -= he;
        drawAny(eu);
        int was = wear;
        wear = 0;
        markDirty();
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.journal.repaired", was, he, BridgeMathSC.group(eu));
        log(m, nameOf(p), false);
        tell(p, m);
        return m;
    }

    /** С12: 100% heat - the portal shuts down, the ring is locked for 2 min and wears +10%; nothing in the world breaks. */
    private void overheat() {
        shortWhat = "";
        EntityPlayer q = playerByName(opener);
        closePortal("sc.bridge.journal.overheat");
        coolTotal = BridgeMathSC.OVERHEAT_LOCK_S * 20;
        coolTicks = coolTotal;
        overheatLock = true;
        wear = Math.min(BridgeMathSC.MAX_WEAR, wear + BridgeMathSC.OVERHEAT_WEAR);
        heatAtClose = BridgeMathSC.HEAT_MAX;
        int[] c = ringCentre();
        worldObj.playSoundEffect(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, "random.fizz", 1.5F, 0.6F);
        tell(q, new BridgeMsgSC("sc.bridge.msg.overheat", BridgeMathSC.OVERHEAT_LOCK_S, BridgeMathSC.OVERHEAT_WEAR));
        markDirty();
    }

    /** С3: the vortex's cells are told the stability (their clients colour it, crack it, shake it under 30%). */
    private void applyTurbulence(boolean t) {
        turbulent = t;
        for (int end = 0; end < 2; end++) {
            World w = endWorld(end);
            if (w == null) {
                continue;
            }
            for (int[] c : endCells(end)) {
                if (w.blockExists(c[0], c[1], c[2])) {
                    TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
                    if (te instanceof TileEntityBridgeVortexSC && ((TileEntityBridgeVortexSC) te).getOpenId() == openId) {
                        ((TileEntityBridgeVortexSC) te).setStability(stability);     // the colour, the cracks, the shaking
                    }
                }
            }
        }
    }

    /** §11: a shaking vortex crackles at both ends. */
    private void crackle() {
        for (int end = 0; end < 2; end++) {
            World w = endWorld(end);
            List<int[]> cells = endCells(end);
            if (w == null || cells.isEmpty()) {
                continue;
            }
            int[] c = cells.get(cells.size() / 2);
            w.playSoundEffect(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, "fire.fire", 1.2F, 1.4F + worldObj.rand.nextFloat() * 0.4F);
            if (worldObj.rand.nextInt(3) == 0) {
                w.playSoundEffect(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, "random.fizz", 0.5F, 1.6F);
            }
        }
    }

    /** С3 turbulence: an arrival lands up to TURB_SHIFT blocks off, at the nearest free place (§7б: the air too - a soft landing); null - as planned. */
    private int[] turbulentSpot(int dim, double x, double y, double z) {
        World w = DimensionManager.getWorld(dim);
        if (w == null) {
            return null;
        }
        int[] off = BridgeMathSC.scatterOffset(worldObj.rand, BridgeMathSC.TURB_SHIFT);
        int px = (int) Math.floor(x) + off[0], py = (int) Math.floor(y), pz = (int) Math.floor(z) + off[1];
        if (!w.blockExists(px, py, pz)) {
            return null;
        }
        return BridgeSpaceSC.freeSpot(BridgeSpaceSC.of(w), px, py, pz, 4, 6);
    }

    /**
     * С5 / С7: the far end moved up to `radius` blocks, to the nearest free place (never into a block); null - it stays.
     * The new place keeps plan()'s rules: no one else near it without consent (§10), the Ground ends apart.
     */
    private int[] scatterEnd(End e, End other, int radius, EntityPlayer p, Order o) {
        WorldServer ws = worldFor(e.dim);
        if (ws == null || radius <= 0) {
            return null;
        }
        int[] off = BridgeMathSC.scatterOffset(worldObj.rand, radius);
        if (off[0] == 0 && off[1] == 0) {
            return null;
        }
        int x = e.x + off[0], z = e.z + off[1];
        int near = scan != null && scan.nav ? BridgeSpaceSC.NEAR_RADIUS_NAV : BridgeSpaceSC.NEAR_RADIUS;
        BridgeSpaceSC.Cells cells = cellsFor(ws);
        BridgeSpaceSC.Result r = BridgeSpaceSC.probe(cells, x, e.y, z, e.w, e.axis, near);
        int[] at = r.free ? new int[]{r.x, r.y, r.z} : r.hasNearest ? new int[]{r.nx, r.ny, r.nz} : null;
        if (at == null) {
            r = BridgeSpaceSC.probe(cells, x, AUTO_Y, z, e.w, e.axis, near);
            at = r.free ? new int[]{r.x, r.y, r.z} : r.hasNearest ? new int[]{r.nx, r.ny, r.nz} : null;
        }
        if (at == null || ShieldEventHandlerPrivate.foreignField(ws, p, at[0], at[1], at[2])) {
            return null;
        }
        if (bridgeKind() == BridgeMathSC.GROUND && e.dim == ownDim()) {
            int[] c = ringCentre();
            if (BridgeMathSC.distance(c[0], 0, c[2], at[0], 0, at[2]) < MIN_DISTANCE) {
                return null;
            }
            if (other != null && other.kind != BridgeMathSC.END_RING && other.dim == e.dim
                    && BridgeMathSC.distance(other.x, 0, other.z, at[0], 0, at[2]) < MIN_DISTANCE) {
                return null;
            }
        }
        String me = nameOf(p);
        for (Object ob : ws.playerEntities) {
            String qn = ((EntityPlayer) ob).getCommandSenderName();
            if (!qn.equalsIgnoreCase(me) && (e.player == null || !qn.equalsIgnoreCase(e.player)) && !o.agreed(qn)
                    && ((EntityPlayer) ob).getDistanceSq(at[0] + 0.5, at[1] + 1, at[2] + 0.5) <= BridgeMathSC.CONSENT_RADIUS * BridgeMathSC.CONSENT_RADIUS) {
                return null;
            }
        }
        return at;
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
        NBTTagCompound t = probeAt(p, tx, ty, tz, targetDim(), true);
        if (t == null) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.nodim", targetDim()));
        }
        place = t;
        markDirty();
        BridgeMsgSC m = BridgeMsgSC.read(t.getCompoundTag("msg"));
        lastMsg = m;
        lastMsgAt = worldObj.getTotalWorldTime();
        return m;
    }

    /**
     * «Проверить место» at a point (the controller's screen, a remote - 1 mB Kr from the bridge's tanks - or the
     * armour, which pays from the helmet itself: payFromBridge false). The Ground bridge looks in its own world only.
     * @return the answer ("free", "reason", "args", "at", "near", "dist", "w", "beacon", "msg"), or null (no such
     * dimension / not enough krypton in the bridge)
     */
    public NBTTagCompound probeAt(EntityPlayer p, int x, int y, int z, int dim, boolean payFromBridge) {
        int d = bridgeKind() == BridgeMathSC.SPACE ? dim : ownDim();
        WorldServer w = worldFor(d);
        if (w == null || (payFromBridge && tanks[BridgeMathSC.KR] < BridgeMathSC.PROBE_KR)) {
            return null;
        }
        if (payFromBridge) {
            tanks[BridgeMathSC.KR] -= BridgeMathSC.PROBE_KR;
            markDirty();
        }
        int size = BridgeMathSC.vortexSize(bridgeKind());
        int radius = scan != null && scan.nav ? BridgeSpaceSC.NEAR_RADIUS_NAV : BridgeSpaceSC.NEAR_RADIUS;
        x = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, x));
        z = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, z));
        y = y == AUTO_Y ? AUTO_Y : Math.max(1, Math.min(254, y));
        BridgeSpaceSC.Result r = BridgeSpaceSC.probe(cellsFor(w), x, y, z, size, ringAxis(), radius);
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("free", r.free);
        t.setString("reason", r.reason);
        t.setTag("args", new BridgeMsgSC("x", (Object[]) r.args).write());
        t.setIntArray("at", new int[]{r.x, r.y, r.z, d});
        t.setBoolean("air", r.free && r.inAir);
        t.setBoolean("void", r.free && r.voidBelow);
        if (r.free && ShieldEventHandlerPrivate.foreignField(w, p, r.x, r.y, r.z)) {
            t.setBoolean("free", false);
            t.setBoolean("air", false);
            t.setBoolean("void", false);
            t.setString("reason", "sc.bridge.place.field");
        }
        if (r.hasNearest) {
            t.setIntArray("near", new int[]{r.nx, r.ny, r.nz, r.nDist});
        }
        int[] c = ringCentre();
        long dist = d == ownDim() ? BridgeMathSC.distance(c[0], 0, c[2], x, 0, z) : 0;
        t.setLong("dist", dist);
        t.setInteger("w", size);
        BridgeMarksSC marks = BridgeMarksSC.get(worldObj);
        t.setBoolean("beacon", bridgeKind() == BridgeMathSC.GROUND && marks != null
                && marks.near(BridgeMarksSC.BEACON, d, x, y, z, BridgeMathSC.BEACON_RADIUS));
        t.setLong("time", worldObj.getTotalWorldTime());
        BridgeMsgSC m = t.getBoolean("free") ? new BridgeMsgSC(t.getBoolean("air") ? "sc.bridge.place.freeairat" : "sc.bridge.place.freeat", r.x, r.y, r.z)
                .part(t.getBoolean("void") ? new BridgeMsgSC("sc.bridge.place.voidbelow") : null)
                : new BridgeMsgSC("sc.bridge.place.blockedat", r.x, r.y, r.z).part(new BridgeMsgSC(t.getString("reason"), (Object[]) r.args));
        t.setTag("msg", m.write());
        return t;
    }

    private int[] ringCentre() {
        return scan == null ? new int[]{xCoord, yCoord, zCoord} : scan.centre();
    }

    /**
     * М-6: the room of this bridge's ring no other bridge's end may take - the ring's inside and SIDE_CLEAR blocks in
     * front of and behind it - as a box {minX, minY, minZ, maxX, maxY, maxZ}; null without a whole ring.
     */
    public int[] ringZoneBox() {
        BridgeStructureSC.Scan s = scan != null ? scan : rescan();
        if (s == null || !s.found || s.coilsNeeded <= 0 || s.coils < s.coilsNeeded) {
            return null;
        }
        int h = (s.size - 1) / 2, d = BridgeStructureSC.SIDE_CLEAR;
        int[] a = BridgeStructureSC.at(s.cx, s.cy, s.cz, s.axis, -(h - 1), 2, -d);
        int[] b = BridgeStructureSC.at(s.cx, s.cy, s.cz, s.axis, h - 1, s.size - 1, d);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }

    /** М-6: a world's cells for this bridge's ends - the rooms of the other bridges' rings count as taken. */
    private BridgeSpaceSC.Cells cellsFor(World w) {
        return BridgeSpaceSC.guarded(BridgeSpaceSC.of(w), new RingZones(w, w.provider.dimensionId == ownDim() ? this : null));
    }

    /**
     * М-6: the other bridges' ring rooms near the cells a check reads. A chunk's controllers (and its neighbours',
     * a ring reaches at most 5 blocks from its controller) are looked up when the check first reads a block there -
     * loaded chunks only, nothing is loaded for it.
     */
    static final class RingZones implements BridgeSpaceSC.Zones {
        private final World w;
        private final TileEntityBridgeControllerSC self;
        private final Set<Long> touched = new HashSet<Long>(), listed = new HashSet<Long>();
        private final List<int[]> boxes = new ArrayList<int[]>();

        RingZones(World w, TileEntityBridgeControllerSC self) {
            this.w = w;
            this.self = self;
        }

        private static long chunkKey(int cx, int cz) {
            return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        }

        @Override
        public boolean ring(int x, int y, int z) {
            int cx = x >> 4, cz = z >> 4;
            if (touched.add(chunkKey(cx, cz))) {
                for (int i = cx - 1; i <= cx + 1; i++) {
                    for (int j = cz - 1; j <= cz + 1; j++) {
                        long k = chunkKey(i, j);
                        if (listed.contains(k) || !w.getChunkProvider().chunkExists(i, j)) {
                            continue;
                        }
                        listed.add(k);
                        // a copy: a controller's first check (rescan) may add tile entities to the chunk
                        for (Object o : new ArrayList<Object>(w.getChunkFromChunkCoords(i, j).chunkTileEntityMap.values())) {
                            if (o instanceof TileEntityBridgeControllerSC && o != self && !((TileEntity) o).isInvalid()) {
                                TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) o;
                                if (self != null && c.xCoord == self.xCoord && c.yCoord == self.yCoord && c.zCoord == self.zCoord) {
                                    continue;
                                }
                                int[] b = c.ringZoneBox();
                                if (b != null) {
                                    boxes.add(b);
                                }
                            }
                        }
                    }
                }
            }
            for (int[] b : boxes) {
                if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                    return true;
                }
            }
            return false;
        }
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
     * «Открыть» to the stored target (ДР0). Everything is checked at once; if all is there the vortex opens now.
     * @return null when it opened, else the refusal (already in the chat and the journal)
     */
    public BridgeMsgSC tryOpen(EntityPlayer p) {
        Order o = new Order();
        o.hasPoint = targetSet;
        o.px = tx;
        o.py = ty;
        o.pz = tz;
        o.pdim = targetDim();
        return openOrder(p, o);
    }

    // ------------------------------------------------------------------ stage 2: orders (ДР0-ДР5), their ends, consent

    /** What to open: the mode, the target point (ДР0 / 2 / 3), the friend and «к вам» (ДР4), the source, who agreed. */
    public static final class Order {
        public int mode = BridgeMathSC.MODE_BASE, source = BridgeMathSC.SRC_CONTROLLER;
        public boolean hasPoint, toMe;
        public int px, py = AUTO_Y, pz, pdim;
        public String pointName = "", friend = "";
        /**
         * The target came from the scanner's finds (the armour's «Находки сканера»); precise: no scatter of the
         * far end (stage 3 scatters unfamiliar places - the armour at level 5 opens precisely to its finds).
         */
        public boolean fromFind, precise = true;
        /** Players who agreed to an end next to them (§10). */
        public final List<String> consented = new ArrayList<String>();

        public NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("m", mode);
            t.setInteger("s", source);
            t.setBoolean("hp", hasPoint);
            t.setBoolean("me", toMe);
            t.setIntArray("p", new int[]{px, py, pz, pdim});
            t.setString("pn", pointName);
            t.setString("f", friend);
            t.setBoolean("ff", fromFind);
            t.setBoolean("pr", precise);
            NBTTagList c = new NBTTagList();
            for (String n : consented) {
                c.appendTag(new net.minecraft.nbt.NBTTagString(n));
            }
            t.setTag("c", c);
            return t;
        }

        public static Order read(NBTTagCompound t) {
            Order o = new Order();
            o.mode = Math.max(0, Math.min(BridgeMathSC.MODES - 1, t.getInteger("m")));
            o.source = t.getInteger("s");
            o.hasPoint = t.getBoolean("hp");
            o.toMe = t.getBoolean("me");
            int[] p = t.getIntArray("p");
            if (p.length == 4) {
                o.px = p[0];
                o.py = p[1];
                o.pz = p[2];
                o.pdim = p[3];
            }
            o.pointName = t.getString("pn");
            o.friend = t.getString("f");
            o.fromFind = t.getBoolean("ff");
            o.precise = !t.hasKey("pr") || t.getBoolean("pr");
            NBTTagList c = t.getTagList("c", 8);
            for (int i = 0; i < c.tagCount(); i++) {
                o.consented.add(c.getStringTagAt(i));
            }
            return o;
        }

        public boolean agreed(String n) {
            for (String c : consented) {
                if (c.equalsIgnoreCase(n)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** One end of a plan: where its vortex stands; END_NEAR - next to whom. */
    public static final class End {
        public int kind, dim, x, y, z, axis, w;
        public String player = "";
    }

    /** Where an order's ends go and what it costs - or why not, or whose consent is missing. */
    public static final class Plan {
        public End a, b;
        public BridgeMsgSC refuse;
        public String consentFrom;
        public long[] dists = new long[0];
        public int[] pct = new int[0];
        public BridgeMathSC.Cost cost;
        public boolean beacon, anchor;
        /** Stage 3 for the target point's end: famInfo ({familiar, percent, scatter, scouting}; all 0 / -1 without a point). */
        public int[] fam = {-1, 0, 0, 0};
    }

    /** Test hook: players by name the world test makes (fake players are not on the server's list). */
    public static final java.util.Map<String, EntityPlayer> TEST_PLAYERS = new java.util.HashMap<String, EntityPlayer>();

    /** An online player by name (the world test's fake ones too), or null. */
    public static EntityPlayer playerByName(String n) {
        if (n == null || n.length() == 0) {
            return null;
        }
        EntityPlayer t = TEST_PLAYERS.get(n.toLowerCase(java.util.Locale.ROOT));
        if (t != null) {
            return t;
        }
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null || s.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : s.getConfigurationManager().playerEntityList) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getCommandSenderName().equalsIgnoreCase(n)) {
                return p;
            }
        }
        return null;
    }

    private static BridgeMsgSC placeRefusal(BridgeSpaceSC.Result r) {
        return new BridgeMsgSC("sc.bridge.refuse.place", r.x, r.y, r.z).part(new BridgeMsgSC(r.reason, (Object[]) r.args));
    }

    /** Where the ends of an order go now (no side effects): the ring, a free point, the nearest free place next to a player. */
    public Plan plan(EntityPlayer p, Order o) {
        return plan(p, o, false);
    }

    /**
     * preview (the remote's / the armour's screen, once a second): a target point in a chunk that isn't loaded is
     * taken as it is - no place check there (it would load or generate far chunks every second); «Открыть» checks it.
     */
    public Plan plan(EntityPlayer p, Order o, boolean preview) {
        Plan pl = new Plan();
        int kind = bridgeKind(), w = BridgeMathSC.vortexSize(kind);
        int[] kinds = BridgeMathSC.modeEnds(o.mode, o.toMe);
        int friendAt = BridgeMathSC.friendEnd(o.mode, o.toMe);
        String me = nameOf(p);
        int[] c = ringCentre();
        int radius = scan != null && scan.nav ? BridgeSpaceSC.NEAR_RADIUS_NAV : BridgeSpaceSC.NEAR_RADIUS;
        End[] ends = new End[2];
        List<Long> dists = new ArrayList<Long>();
        List<Integer> pct = new ArrayList<Integer>();
        for (int i = 0; i < 2; i++) {
            End e = new End();
            e.kind = kinds[i];
            e.w = w;
            if (e.kind == BridgeMathSC.END_RING) {
                e.dim = ownDim();
                e.x = c[0];
                e.y = c[1];
                e.z = c[2];
                e.axis = ringAxis();
                ends[i] = e;
                continue;
            }
            WorldServer ws;
            BridgeSpaceSC.Result r;
            if (e.kind == BridgeMathSC.END_POINT) {
                if (!o.hasPoint) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.notarget");
                    return pl;
                }
                e.dim = kind == BridgeMathSC.SPACE ? o.pdim : ownDim();
                if (kind == BridgeMathSC.GROUND && o.pdim != ownDim()) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.groundDim");
                    return pl;
                }
                ws = worldFor(e.dim);
                if (ws == null) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.nodim", e.dim);
                    return pl;
                }
                if (kind == BridgeMathSC.GROUND && BridgeMathSC.distance(c[0], 0, c[2], o.px, 0, o.pz) < MIN_DISTANCE) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.near", MIN_DISTANCE);
                    return pl;
                }
                e.axis = ringAxis();
                if (preview && !ws.getChunkProvider().chunkExists(o.px >> 4, o.pz >> 4)) {
                    e.x = o.px;
                    e.y = o.py == AUTO_Y ? 0 : o.py;
                    e.z = o.pz;
                    dists.add(e.dim == ownDim() ? BridgeMathSC.distance(c[0], 0, c[2], e.x, 0, e.z) : 0);
                    pct.add(0);
                    ends[i] = e;
                    continue;
                }
                BridgeSpaceSC.Cells cells = cellsFor(ws);
                r = BridgeSpaceSC.probe(cells, o.px, o.py, o.pz, w, ringAxis(), o.fromFind ? radius : 0);
                if (!r.free && o.fromFind && !r.hasNearest && o.py != AUTO_Y) {
                    // М-1: a find deep in the rock with no room within the radius - the surface above it («Y авто»)
                    r = BridgeSpaceSC.probe(cells, o.px, AUTO_Y, o.pz, w, ringAxis(), radius);
                }
                if (!r.free && !(o.fromFind && r.hasNearest)) {
                    pl.refuse = placeRefusal(r);
                    return pl;
                }
                // М-1: a scanner's find is a block (an ore, a chest) - the end goes to the nearest free place next to it, as
                // END_NEAR does (radius 16 / 32 with the Navigation Computer); «precise» (no scatter) stays as it was
                e.x = r.free ? r.x : r.nx;
                e.y = r.free ? r.y : r.ny;
                e.z = r.free ? r.z : r.nz;
            } else {
                boolean isFriend = i == friendAt;
                EntityPlayer who = isFriend ? playerByName(o.friend) : p;
                if (who == null) {
                    pl.refuse = isFriend ? new BridgeMsgSC("sc.bridge.refuse.friendOff", o.friend.length() == 0 ? "?" : o.friend)
                            : new BridgeMsgSC("sc.bridge.refuse.noplayer");
                    return pl;
                }
                if (isFriend && p != null && who.getCommandSenderName().equalsIgnoreCase(me)) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.friendSelf");
                    return pl;
                }
                e.player = who.getCommandSenderName();
                if (isFriend && !o.agreed(e.player)) {
                    // §10: his consent before anything about where he is (dimension, place, distance, price) - no ends yet
                    pl.consentFrom = e.player;
                    return pl;
                }
                e.dim = who.worldObj.provider.dimensionId;
                if (kind == BridgeMathSC.GROUND && e.dim != ownDim()) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.groundDim");
                    return pl;
                }
                ws = worldFor(e.dim);
                if (ws == null) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.nodim", e.dim);
                    return pl;
                }
                int[] s = BridgeMathSC.projectionSpot(who.posX, who.boundingBox.minY, who.posZ, who.rotationYaw);
                e.axis = s[3];
                r = BridgeSpaceSC.probe(cellsFor(ws), s[0], s[1], s[2], w, s[3], radius);
                if (!r.free && !r.hasNearest) {
                    pl.refuse = new BridgeMsgSC("sc.bridge.refuse.nearplace", e.player).part(new BridgeMsgSC(r.reason, (Object[]) r.args));
                    return pl;
                }
                e.x = r.free ? r.x : r.nx;
                e.y = r.free ? r.y : r.ny;
                e.z = r.free ? r.z : r.nz;
            }
            if (ShieldEventHandlerPrivate.foreignField(ws, p, e.x, e.y, e.z)) {
                pl.refuse = new BridgeMsgSC("sc.bridge.refuse.place", e.x, e.y, e.z).part("sc.bridge.place.field");
                return pl;
            }
            long dist = e.dim == ownDim() ? BridgeMathSC.distance(c[0], 0, c[2], e.x, 0, e.z) : 0;
            if (kind == BridgeMathSC.GROUND && dist < MIN_DISTANCE) {
                pl.refuse = new BridgeMsgSC(e.kind == BridgeMathSC.END_NEAR ? "sc.bridge.refuse.nearring" : "sc.bridge.refuse.near", MIN_DISTANCE);
                return pl;
            }
            dists.add(dist);
            pct.add(o.source == BridgeMathSC.SRC_ARMOUR && e.kind == BridgeMathSC.END_NEAR && e.player.equalsIgnoreCase(me)
                    ? BridgeMathSC.ARMOUR_DISCOUNT : 0);
            ends[i] = e;
        }
        pl.a = ends[0];
        pl.b = ends[1];
        if (pl.a.kind != BridgeMathSC.END_RING && pl.a.dim == pl.b.dim && kind == BridgeMathSC.GROUND
                && BridgeMathSC.distance(pl.a.x, 0, pl.a.z, pl.b.x, 0, pl.b.z) < MIN_DISTANCE) {
            pl.refuse = new BridgeMsgSC("sc.bridge.refuse.endsnear", MIN_DISTANCE);
            return pl;
        }
        // §10: an end next to someone else - their consent first
        for (End e : ends) {
            if (e.kind == BridgeMathSC.END_RING) {
                continue;
            }
            if (e.kind == BridgeMathSC.END_NEAR && !e.player.equalsIgnoreCase(me) && !o.agreed(e.player)) {
                pl.consentFrom = e.player;
                break;
            }
            World ew = DimensionManager.getWorld(e.dim);
            if (ew != null) {
                for (Object ob : ew.playerEntities) {
                    EntityPlayer q = (EntityPlayer) ob;
                    String qn = q.getCommandSenderName();
                    if (!qn.equalsIgnoreCase(me) && !qn.equalsIgnoreCase(e.player) && !o.agreed(qn)
                            && q.getDistanceSq(e.x + 0.5, e.y + 1, e.z + 0.5) <= BridgeMathSC.CONSENT_RADIUS * BridgeMathSC.CONSENT_RADIUS) {
                        pl.consentFrom = qn;
                        break;
                    }
                }
            }
            if (pl.consentFrom != null) {
                break;
            }
        }
        pl.dists = new long[dists.size()];
        pl.pct = new int[pct.size()];
        for (int i = 0; i < pl.dists.length; i++) {
            pl.dists[i] = dists.get(i);
            pl.pct[i] = pct.get(i);
        }
        BridgeMarksSC marks = worldObj == null ? null : BridgeMarksSC.get(worldObj);
        int farDim = pl.b.dim != ownDim() ? pl.b.dim : pl.a.dim;
        pl.beacon = kind == BridgeMathSC.GROUND && marks != null && pl.b.kind == BridgeMathSC.END_POINT
                && marks.near(BridgeMarksSC.BEACON, pl.b.dim, pl.b.x, pl.b.y, pl.b.z, BridgeMathSC.BEACON_RADIUS);
        pl.anchor = kind == BridgeMathSC.SPACE && marks != null && marks.any(BridgeMarksSC.ANCHOR, farDim);
        pl.cost = BridgeMathSC.cost(kind, pl.dists, pl.pct, pl.beacon, pl.anchor, scan == null ? 0 : scan.stabCount());
        End pt = pl.b.kind == BridgeMathSC.END_POINT ? pl.b : pl.a.kind == BridgeMathSC.END_POINT ? pl.a : null;
        if (pt != null) {
            pl.fam = famInfo(p, pt.dim, pt.x, pt.z, pl.beacon, pl.anchor, o.fromFind && o.precise);
        }
        if (!envKnown) {
            refreshEnv();
        }
        BridgeMathSC.adjust(pl.cost, pl.fam[1], envRes);
        return pl;
    }

    /** The consent request last sent (the world test answers it). */
    private int lastConsentId;

    public int getLastConsentId() {
        return lastConsentId;
    }

    private BridgeMsgSC askConsent(EntityPlayer p, Order o, String who) {
        NBTTagCompound order = o.write();
        order.setIntArray("ctrl", new int[]{xCoord, yCoord, zCoord, ownDim()});
        com.sc.bridge.BridgeConsentSC.Request r = com.sc.bridge.BridgeConsentSC.server().ask(nameOf(p), who, com.sc.bridge.BridgeFarSC.now(), order);
        lastConsentId = r.id;
        EntityPlayer target = playerByName(who);
        if (target != null) {
            target.addChatComponentMessage(com.sc.bridge.BridgeFarSC.consentQuestion(r.id, nameOf(p), nameArgChat(), o.mode));
        }
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.msg.consentSent", who, BridgeMathSC.CONSENT_TICKS / 20);
        log(m, nameOf(p), false);
        tell(p, m);
        return m;
    }

    /**
     * Opens an order (the controller's screen ДР0, a remote, the armour, an accepted consent). Everything is checked
     * at once; when all is there the singularity is born now.
     * @return null when it opened; the refusal (already in the chat and the journal); or «sc.bridge.msg.consentSent»
     */
    public BridgeMsgSC openOrder(EntityPlayer p, Order o) {
        if (worldObj == null || worldObj.isRemote) {
            return null;
        }
        boolean far = o.source != BridgeMathSC.SRC_CONTROLLER;
        if (far ? !trusted(p) : !allowed(p)) {
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
            return refuse(p, new BridgeMsgSC(overheatLock ? "sc.bridge.refuse.overheat" : "sc.bridge.refuse.cooling", (coolTicks + 19) / 20));
        }
        Plan pl = plan(p, o);
        if (pl.refuse != null) {
            return refuse(p, pl.refuse);
        }
        if (pl.consentFrom != null) {
            return askConsent(p, o, pl.consentFrom);
        }
        BridgeMathSC.Cost c = pl.cost;
        BridgeMsgSC miss = missing(c);
        if (!miss.parts.isEmpty()) {
            return refuse(p, miss);
        }
        // С3: a vortex that would fold on its first second isn't born (nothing is paid); a storm at an end counts too
        refreshEnv();
        boolean storm = envStorm;
        for (End e : new End[]{pl.a, pl.b}) {
            World ew = e.kind == BridgeMathSC.END_RING ? null : DimensionManager.getWorld(e.dim);
            storm |= ew != null && stormAt(ew, e.x, e.z);
        }
        int st0 = BridgeMathSC.stability(s.stabCount(), wear, massNow(), s.mass, envInterf, storm, 0).total;
        if (st0 < BridgeMathSC.STAB_FOLD) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.unstable", st0, BridgeMathSC.STAB_FOLD));
        }
        // С5 / С7: an unfamiliar point (or a first trip into a dimension) scatters the far end to a free place near it
        int shift = 0;
        End pt = pl.b.kind == BridgeMathSC.END_POINT ? pl.b : pl.a.kind == BridgeMathSC.END_POINT ? pl.a : null;
        if (pt != null && pl.fam[2] > 0) {
            int[] at = scatterEnd(pt, pt == pl.b ? pl.a : pl.b, pl.fam[2], p, o);
            if (at != null) {
                shift = (int) BridgeMathSC.distance(pt.x, 0, pt.z, at[0], 0, at[2]);
                pt.x = at[0];
                pt.y = at[1];
                pt.z = at[2];
            }
        }
        // everything is there: the singularity is born now
        drawCapacitors(c.eu);
        int[] needTank = needTanks(c);
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
        argonDeficit = 0;
        heat = 0;
        heatWarned = false;
        massTotal = 0;
        peakHeat = 0;
        openScatter = pl.fam[2];
        openShift = shift;
        long farthest = 0;
        for (long x : pl.dists) {
            farthest = Math.max(farthest, x);
        }
        openDist = farthest;
        openOtherDim = pl.a.dim != ownDim() || pl.b.dim != ownDim();
        shortTicks = -1;
        shortWhat = "";
        aAxis = s.axis;
        aSize = s.size;
        aProj = pl.a.kind != BridgeMathSC.END_RING;
        aDim = pl.a.dim;
        ax = pl.a.x;
        ay = pl.a.y;
        az = pl.a.z;
        apAxis = pl.a.axis;
        aW = pl.a.w;
        bDim = pl.b.dim;
        bx = pl.b.x;
        by = pl.b.y;
        bz = pl.b.z;
        bAxis = pl.b.axis;
        bW = pl.b.w;
        openMode = o.mode;
        opener = nameOf(p);
        openPrecise = o.precise;                                     // stage 3: an imprecise far end scatters on unknown ground
        opens++;
        refreshEnv();
        stability = stabNow().total;
        turbulent = false;
        openedAt = worldObj.getTotalWorldTime();
        placeEnds();
        litCoils(s, true);
        loadChunks();
        // С5: both ends are familiar places for whoever opened them now; С7: this dimension is scouted
        com.sc.bridge.BridgeFamiliarSC fam = p == null ? null : com.sc.bridge.BridgeFamiliarSC.get(worldObj);
        if (fam != null) {
            for (End e : new End[]{pl.a, pl.b}) {
                fam.markBlock(nameOf(p), e.dim, e.x, e.z, 0);
                if (e.dim != ownDim()) {
                    fam.scout(nameOf(p), e.dim);
                }
            }
        }
        birth();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);           // the hologram over the ring
        float pitch = s.kind == BridgeMathSC.SPACE ? 0.7F : 1.0F;
        World wa = endWorld(0), wb = endWorld(1);
        int[] ce = aProj ? new int[]{ax, ay + 1, az} : s.centre();
        if (wa != null) {
            wa.playSoundEffect(ce[0] + 0.5, ce[1] + 0.5, ce[2] + 0.5, "portal.trigger", 1.0F, pitch);
        }
        if (wb != null) {
            wb.playSoundEffect(bx + 0.5, by + 1.5, bz + 0.5, "portal.trigger", 1.0F, pitch);
        }
        if (aProj) {
            int[] rc = s.centre();
            worldObj.playSoundEffect(rc[0] + 0.5, rc[1] + 0.5, rc[2] + 0.5, "portal.trigger", 0.6F, pitch);
        }
        long d = 0;
        for (long x : pl.dists) {
            d = Math.max(d, x);
        }
        BridgeMsgSC m = o.mode == BridgeMathSC.MODE_BASE
                ? new BridgeMsgSC(bDim == ownDim() ? "sc.bridge.journal.opened" : "sc.bridge.journal.openedDim", bx, by, bz, BridgeMathSC.group(d), bDim)
                : new BridgeMsgSC("sc.bridge.journal.openedMode", "@mode." + o.mode, bx, by, bz, BridgeMathSC.group(d));
        if (shift > 0) {
            m.part("sc.bridge.msg.scattered", shift, pl.fam[2]);
        }
        log(m, nameOf(p), false);
        tell(p, m);
        for (End e : new End[]{pl.a, pl.b}) {
            EntityPlayer q = e.kind == BridgeMathSC.END_NEAR && !e.player.equalsIgnoreCase(nameOf(p)) ? playerByName(e.player) : null;
            if (q != null) {
                tell(q, new BridgeMsgSC("sc.bridge.msg.openedNear", nameOf(p), nameArg()));
            }
        }
        sendHud();
        markDirty();
        return null;
    }

    /** What is missing for a cost (capacitors, tanks) - parts empty when nothing. */
    public BridgeMsgSC missing(BridgeMathSC.Cost c) {
        BridgeMsgSC miss = new BridgeMsgSC("sc.bridge.refuse.missing");
        long have = capacitorEnergy();
        if (have < c.eu) {
            miss.part("sc.bridge.need.cap", BridgeMathSC.group(c.eu - have));
        }
        int[] needTank = needTanks(c);
        for (int i = 0; i < tanks.length; i++) {
            if (tanks[i] < needTank[i]) {
                miss.part("sc.bridge.need.gas", gasName(i), needTank[i], tanks[i]);
            }
        }
        return miss;
    }

    private int[] needTanks(BridgeMathSC.Cost c) {
        int[] needTank = new int[tanks.length];
        needTank[BridgeMathSC.SM] = c.sm;
        needTank[BridgeMathSC.D] = c.d;
        needTank[BridgeMathSC.KR] = c.kr;
        needTank[BridgeMathSC.AR] = c.ar;
        return needTank;
    }

    /** The opener's HUD line (§8): the bridge, the time left, the stability, the heat, a fold coming - or that it closed. */
    private void sendHud() {
        EntityPlayer q = playerByName(opener);
        if (q instanceof net.minecraft.entity.player.EntityPlayerMP && !(q instanceof net.minecraftforge.common.util.FakePlayer)) {
            com.sc.bridge.BridgeNetSC.CHANNEL.sendTo(new com.sc.bridge.BridgeNetSC.Hud(open, name, lifeLeft, lifeTotal, stability, openKind,
                    heat / 10, shortTicks >= 0 ? (shortTicks + 19) / 20 : -1, shortWhat), (net.minecraft.entity.player.EntityPlayerMP) q);
        }
    }

    /** §11: the singularity is born - a flash and an implosion, then the vortex unfolds (the clients near each end). */
    private void birth() {
        for (int end = 0; end < 2; end++) {
            List<int[]> cells = endCells(end);
            if (cells.isEmpty()) {
                continue;
            }
            double x = 0, y = 0, z = 0;
            for (int[] c : cells) {
                x += c[0] + 0.5;
                y += c[1] + 0.5;
                z += c[2] + 0.5;
            }
            x /= cells.size();
            y /= cells.size();
            z /= cells.size();
            int dim = end == 0 ? (aProj ? aDim : ownDim()) : bDim;
            com.sc.bridge.BridgeNetSC.CHANNEL.sendToAllAround(new com.sc.bridge.BridgeNetSC.Birth(x, y, z, openKind),
                    new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(dim, x, y, z, 64));
        }
    }

    /** The vortex cells of an end: [x, y, z, tileU, tileV]. */
    public List<int[]> endCells(int end) {
        List<int[]> out = new ArrayList<int[]>();
        if (end == 0 && !aProj) {
            int n = aSize, h = (n - 1) / 2;
            for (int v = 2; v < n; v++) {
                for (int u = -h + 1; u < h; u++) {
                    int[] p = BridgeStructureSC.at(xCoord, yCoord, zCoord, aAxis, u, v, 0);
                    out.add(new int[]{p[0], p[1], p[2], u + h - 1, n - 1 - v});
                }
            }
        } else {
            boolean a = end == 0;
            int w = a ? aW : bW, h = (w - 1) / 2, axis = a ? apAxis : bAxis, x0 = a ? ax : bx, y0 = a ? ay : by, z0 = a ? az : bz;
            for (int v = 0; v < w; v++) {
                for (int u = -h; u <= h; u++) {
                    int[] p = axis == 0 ? new int[]{x0 + u, y0 + v, z0} : new int[]{x0, y0 + v, z0 + u};
                    out.add(new int[]{p[0], p[1], p[2], u + h, w - 1 - v});
                }
            }
        }
        return out;
    }

    private World endWorld(int end) {
        return end == 0 ? (aProj ? DimensionManager.getWorld(aDim) : worldObj) : DimensionManager.getWorld(bDim);
    }

    /** The open portal's end A away from the ring: {dim, x, y, z, axis, w}, or null (A is the ring). */
    public int[] getEndA() {
        return aProj ? new int[]{aDim, ax, ay, az, apAxis, aW} : null;
    }

    public int getOpenMode() {
        return openMode;
    }

    public String getOpener() {
        return opener;
    }

    /** The open portal's far end is precise (stage 3: otherwise it scatters on unknown ground). */
    public boolean isOpenPrecise() {
        return openPrecise;
    }

    /**
     * Puts the vortex cells that are missing: into air, or into a walk-through block (grass, flowers, snow layer, torch -
     * what BridgeSpaceSC counts as PASS), which is first broken with its drops. Never into a solid block, a liquid or a TileEntity.
     */
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
                    if (te instanceof TileEntityBridgeVortexSC && ((TileEntityBridgeVortexSC) te).controller() != null) {
                        continue;                             // М-6: another live portal's cell is never taken over
                    }
                }
                if (!b.isAir(w, c[0], c[1], c[2]) && b != ModBlocks.bridgeVortex) {
                    net.minecraft.block.material.Material m = b.getMaterial();
                    if (m.blocksMovement() || m.isLiquid() || b.hasTileEntity(w.getBlockMetadata(c[0], c[1], c[2]))) {
                        continue;                             // never in a block
                    }
                    w.func_147480_a(c[0], c[1], c[2], true);  // grass, flowers, snow, torches (BridgeSpaceSC PASS): broken with drops
                }
                w.setBlock(c[0], c[1], c[2], ModBlocks.bridgeVortex, openKind == BridgeMathSC.SPACE ? 1 : 0, 3);
                TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
                if (te instanceof TileEntityBridgeVortexSC) {
                    int[] look = endLook(end);
                    ((TileEntityBridgeVortexSC) te).setup(ownDim(), xCoord, yCoord, zCoord, end, openId, c[3], c[4], look[0], look[1], look[2] != 0,
                            openedAt, stability);
                }
            }
        }
    }

    /** An end's opening for its look: {cells a side, plane axis, 1 if no ring round it (a projected end)}. */
    private int[] endLook(int end) {
        if (end == 0 && !aProj) {
            return new int[]{Math.max(1, aSize - 2), aAxis, 0};
        }
        return end == 0 ? new int[]{aW, apAxis, 1} : new int[]{bW, bAxis, 1};
    }

    private void removeEnds() {
        for (int end = 0; end < 2; end++) {
            World w = endWorld(end);
            if (w == null) {
                continue;
            }
            List<int[]> cells = endCells(end);
            if (!cells.isEmpty()) {                          // ВП7: the clients near the end watch it collapse to a point
                double x = 0, y = 0, z = 0;
                for (int[] c : cells) {
                    x += c[0] + 0.5;
                    y += c[1] + 0.5;
                    z += c[2] + 0.5;
                }
                x /= cells.size();
                y /= cells.size();
                z /= cells.size();
                int[] look = endLook(end);
                com.sc.bridge.BridgeNetSC.CHANNEL.sendToAllAround(new com.sc.bridge.BridgeNetSC.Collapse(x, y, z, openKind, look[0], look[1],
                        look[2] != 0, stability), new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(w.provider.dimensionId, x, y, z, 64));
            }
            for (int[] c : cells) {
                if (w.blockExists(c[0], c[1], c[2]) && w.getBlock(c[0], c[1], c[2]) == ModBlocks.bridgeVortex) {
                    TileEntity te = w.getTileEntity(c[0], c[1], c[2]);
                    if (te instanceof TileEntityBridgeVortexSC && ((TileEntityBridgeVortexSC) te).getOpenId() != 0
                            && ((TileEntityBridgeVortexSC) te).getOpenId() != openId) {
                        continue;                             // М-6: another portal's cell stays
                    }
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
        turbulent = false;
        shortTicks = -1;
        sendHud();
        // С2: the opening wore the ring 1-3% (far, heavy, hot)
        wear = Math.min(BridgeMathSC.MAX_WEAR, wear + BridgeMathSC.wearPerOpen(openDist, openOtherDim, massTotal, peakHeat / 10));
        heatAtClose = heat;
        massLoad.clear();
        litCoils(scan, false);
        releaseChunks();
        coolTotal = BridgeMathSC.coolTicks();
        coolTicks = coolTotal;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);           // the hologram goes
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
            boolean arShort = false, heShort = false;
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
                    if (i == BridgeMathSC.HE) {
                        heShort = true;
                    }
                }
            }
            // С3 stability, С6 interference, С12 heat
            argonDeficit = BridgeMathSC.argonStep(argonDeficit, arShort);
            refreshEnv();
            stability = stabNow().total;
            heat = BridgeMathSC.heatStep(heat, wear, stability, heShort);
            peakHeat = Math.max(peakHeat, heat);
            if (heat >= BridgeMathSC.HEAT_MAX) {
                overheat();
                return;
            }
            if (heat >= BridgeMathSC.HEAT_WARN && !heatWarned) {
                heatWarned = true;
                tell(playerByName(opener), new BridgeMsgSC("sc.bridge.msg.hot", heat / 10));
                log(new BridgeMsgSC("sc.bridge.msg.hot", heat / 10), "", true);
            }
            if (stability < BridgeMathSC.STAB_FOLD) {
                shortWhat = "";
                closePortal("sc.bridge.journal.unstable");
                return;
            }
            boolean turb = stability < BridgeMathSC.TURBULENCE;
            applyTurbulence(turb);
            if (turb) {
                crackle();
            }
            int cm = BridgeMathSC.coilMeta(true, heat);
            if (cm != coilsMeta && scan != null) {
                setCoils(scan, cm);
            }
            if (scan != null && !scan.valid) {
                shortWhat = "";
                closePortal("sc.bridge.journal.broken");
                return;
            }
            placeEnds();                                       // a cell someone took is put back (into air / walk-through)
            markDirty();
        }
        if (tick % 10 == 0) {
            sendHud();
        }
        if (shortNow) {
            if (shortTicks < 0) {
                shortTicks = BridgeMathSC.SHORT_GRACE_S * 20;
                shortWhat = what;
                BridgeMsgSC warn = new BridgeMsgSC("sc.bridge.journal.short", "@" + what, BridgeMathSC.SHORT_GRACE_S);
                log(warn, "", true);
                tell(playerByName(opener), new BridgeMsgSC("sc.bridge.msg.shortWarn", "@" + what, BridgeMathSC.SHORT_GRACE_S));    // С11
                sendHud();
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
        if (ticketA == null || ticketB == null || ticketC == null && aElsewhere()) {
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
        } else if (aProj) {
            dim = aDim;
            int[] o = apAxis == 0 ? new int[]{0, 0, 1} : new int[]{1, 0, 0};
            x = ax + 0.5 + o[0];
            y = ay;
            z = az + 0.5 + o[2];
            yaw = apAxis == 0 ? 0F : -90F;
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
        // never out into someone else's private field (the exit cell next to the end; a turbulent shift below)
        EntityPlayer rights = player ? (EntityPlayer) e : playerByName(opener);
        World dest = DimensionManager.getWorld(dim);
        if (dest != null && ShieldEventHandlerPrivate.foreignField(dest, rights, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return;                                          // privateFor has told the player
        }
        // С4: the pass costs EU by its mass and weighs on the stability for 10 s (the Mass Compensator halves both)
        int pm = massOf(e);
        if (!drawAny(BridgeMathSC.massEu(pm, scan != null && scan.mass))) {
            if (player) {
                tell((EntityPlayer) e, new BridgeMsgSC("sc.bridge.msg.massEu", BridgeMathSC.group(BridgeMathSC.massEu(pm, scan != null && scan.mass))));
            }
            return;
        }
        massLoad.add(new long[]{worldObj.getTotalWorldTime() + BridgeMathSC.MASS_TICKS, pm});
        massTotal += pm;
        // С3: a shaking vortex throws the arrival off - up to 8 blocks, to the nearest free place - with a push
        boolean shaken = false;
        if (stability < BridgeMathSC.TURBULENCE) {
            int[] spot = turbulentSpot(dim, x, y, z);
            if (spot != null && dest != null && ShieldEventHandlerPrivate.foreignField(dest, rights, spot[0], spot[1], spot[2])) {
                spot = null;                                 // lands as planned instead
            }
            if (spot != null) {
                x = spot[0] + 0.5;
                y = spot[1];
                z = spot[2] + 0.5;
                shaken = true;
            }
        }
        World from = e.worldObj;
        double fx = e.posX, fy = e.posY, fz = e.posZ;
        Entity moved = BridgeTeleportSC.teleport(e, dim, x, y, z, yaw);
        if (moved != null) {
            moved.getEntityData().setLong("scBridgeCd", now + BridgeMathSC.TELEPORT_COOLDOWN);
            BridgeSoftLandSC.arrived(moved);                 // §7б: out into the air - a soft landing
            from.playSoundEffect(fx, fy, fz, "mob.endermen.portal", 0.8F, 1.0F);
            moved.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 0.8F, 1.0F);
            if (moved instanceof net.minecraft.entity.player.EntityPlayerMP) {      // ВП11: the arrival's flash and trail
                com.sc.bridge.BridgeNetSC.CHANNEL.sendTo(new com.sc.bridge.BridgeNetSC.Arrive(openKind),
                        (net.minecraft.entity.player.EntityPlayerMP) moved);
            }
            if (shaken) {
                moved.addVelocity((worldObj.rand.nextDouble() - 0.5) * 0.8, 0.35, (worldObj.rand.nextDouble() - 0.5) * 0.8);
                moved.velocityChanged = true;
                moved.worldObj.playSoundEffect(x, y, z, "random.fizz", 0.7F, 1.5F);
                lastShift = new int[]{(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
            }
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
            World wa = aProj ? DimensionManager.getWorld(aDim) : worldObj;
            if (wa == null && aProj) {
                wa = worldFor(aDim);
            }
            ticketA = wa == null ? null : ticket(wa);
            if (ticketA != null) {
                forceAround(ticketA, endCells(0));
                if (wa == worldObj) {
                    ForgeChunkManager.forceChunk(ticketA, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
                }
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
        if (ticketC == null && aElsewhere()) {
            ticketC = ticket(worldObj);
            if (ticketC != null) {
                ForgeChunkManager.forceChunk(ticketC, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            }
        }
    }

    /** End A stands in another world: ticketA doesn't hold the controller's chunk (ticketC does). */
    private boolean aElsewhere() {
        return aProj && aDim != ownDim();
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
        release(ticketA);
        ticketA = null;
        release(ticketB);
        ticketB = null;
        release(ticketC);
        ticketC = null;
    }

    /** Lets a ticket go while its world is still the server's (ForgeChunkManager forgets an unloaded world's tickets itself). */
    private static void release(ForgeChunkManager.Ticket t) {
        if (t != null && t.world != null && DimensionManager.getWorld(t.world.provider.dimensionId) == t.world) {
            ForgeChunkManager.releaseTicket(t);
        }
    }

    /** ChunkLoaderSC: after a load, the controller's own ticket comes back while the portal is open. */
    public void adoptTicket(ForgeChunkManager.Ticket t) {
        if (aElsewhere()) {
            if (!open || ticketC != null) {
                ForgeChunkManager.releaseTicket(t);
                return;
            }
            ticketC = t;                                     // end A is in another world (its ticket is asked for again on the next tick)
            ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            return;
        }
        if (!open || ticketA != null) {
            ForgeChunkManager.releaseTicket(t);
            return;
        }
        ticketA = t;
        forceAround(t, endCells(0));
        ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
    }

    /** ChunkLoaderSC: after a load, the «Дистанционный режим» ticket comes back (or goes, when the mode is off). */
    public void adoptRemoteTicket(ForgeChunkManager.Ticket t) {
        if (!remoteMode || ticketR != null) {
            ForgeChunkManager.releaseTicket(t);
            return;
        }
        ticketR = t;
        ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
    }

    /** «Дистанционный режим» on / off: the controller's chunk stays loaded (REMOTE_MODE_EU a tick) so remotes and helmets reach it. */
    public void setRemoteMode(boolean on) {
        if (on == remoteMode) {
            return;
        }
        remoteMode = on;
        if (!on && ticketR != null) {
            ForgeChunkManager.releaseTicket(ticketR);
            ticketR = null;
        }
        markDirty();
    }

    private void remoteModeTick() {
        if (!remoteMode) {
            return;
        }
        if (!drawAny(BridgeMathSC.REMOTE_MODE_EU)) {
            setRemoteMode(false);
            log(new BridgeMsgSC("sc.bridge.journal.remoteOff"), "", true);
            return;
        }
        if (ticketR == null && worldObj instanceof WorldServer) {
            ticketR = ForgeChunkManager.requestTicket(com.sc.SCMod.instance, worldObj, ForgeChunkManager.Type.NORMAL);
            if (ticketR != null) {
                NBTTagCompound d = ticketR.getModData();
                d.setString("Kind", "bridgeRemote");
                d.setInteger("x", xCoord);
                d.setInteger("y", yCoord);
                d.setInteger("z", zCoord);
                d.setInteger("dim", ownDim());
                ForgeChunkManager.forceChunk(ticketR, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            }
        }
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
        if (bridgeId == 0) {
            ensureBridgeId();
        }
        remoteModeTick();
        if (open) {
            holdTick();
        }
        if (powerOn) {
            chargeTick();
        }
        if (!open && tick % 100 == 0) {
            refreshEnv();                                    // С6 for the screen while closed
        }
        if (!open && coolTicks > 0) {
            int step = 1;
            if (scan != null && scan.cooler && !overheatLock) {
                if (tick % 20 == 0 && coolHeDebt == 0) {
                    coolHeDebt = BridgeMathSC.COOLER_HE_PER_S;     // a second without helium isn't owed later
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
            heat = BridgeMathSC.coolingHeat(heatAtClose, coolTicks, coolTotal);
            if (coolTicks == 0) {
                coolHeDebt = 0;
                heat = 0;
                if (overheatLock) {
                    overheatLock = false;
                    log(new BridgeMsgSC("sc.bridge.journal.overheatEnd"), "", false);
                }
                markDirty();
            }
        } else if (!open && heat > 0) {
            heat = 0;
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
            if (ticketR != null) {
                ForgeChunkManager.releaseTicket(ticketR);
                ticketR = null;
            }
            // the controller is gone: no one dims a ring left glowing with its heat (loaded chunks only, as rescan)
            if (coilsMeta != 0 && scan != null
                    && worldObj.checkChunksExist(xCoord - 5, yCoord - 1, zCoord - 5, xCoord + 5, yCoord + 9, zCoord + 5)) {
                setCoils(scan, 0);
            }
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        // let them go, not just forget them: a forgotten ticket kept its chunks loaded until a restart while the
        // controller, loaded again, asked for new ones (an open portal holds this chunk: ticketA or ticketC)
        if (worldObj != null && !worldObj.isRemote) {
            releaseChunks();
            release(ticketR);
        }
        ticketA = null;
        ticketB = null;
        ticketC = null;
        ticketR = null;
    }

    // ------------------------------------------------------------------ the screen's actions (BridgeNetSC)

    public static final int A_CHECK = 2, A_CALIBRATE = 3, A_PROBE = 4, A_OPEN = 6, A_CLOSE = 7, A_BM_ADD = 8, A_BM_RENAME = 9,
            A_BM_DELETE = 10, A_POWER = 12, A_CLEAR = 13, A_TARGET = 14, A_MODE = 15;
    /** Stage 2: the name, friends, access, «Дистанционный режим» (owner); bind the worn helmet; the coordinators in the inventory. */
    public static final int A_NAME = 16, A_FRIEND_ADD = 17, A_FRIEND_DEL = 18, A_ACCESS = 19, A_REMOTE_MODE = 20, A_BIND_HELMET = 21,
            A_FROM_COORD = 22, A_TO_COORD = 23, A_COPY_COORD = 24;
    /** Stage 3: «Ремонт» (С2). */
    public static final int A_REPAIR = 25;

    /** М-7: what an ownerless controller refuses - its settings, bindings, the ring's service; opening / closing stay open to all. */
    private static boolean needsOwner(int a) {
        switch (a) {
            case A_NAME:
            case A_FRIEND_ADD:
            case A_FRIEND_DEL:
            case A_ACCESS:
            case A_REMOTE_MODE:
            case A_BIND_HELMET:
            case A_CALIBRATE:
            case A_REPAIR:
            case A_CLEAR:
            case A_POWER:
            case A_BM_ADD:
            case A_BM_RENAME:
            case A_BM_DELETE:
                return true;
            default:
                return false;
        }
    }

    public void action(EntityPlayer p, int a, int[] v, String s) {
        if (needsOwner(a) && ownerlessRefusal(p) != null) {
            return;
        }
        if (a == A_OPEN && openThrottle(p) != null || a == A_PROBE && probeThrottle(p) != null) {
            return;
        }
        switch (a) {
            case A_NAME:
            case A_FRIEND_ADD:
            case A_FRIEND_DEL:
            case A_ACCESS:
                if (!isOwner(p)) {
                    refuse(p, new BridgeMsgSC("sc.bridge.refuse.owneronly", owner));
                    return;
                }
                if (a == A_NAME) {
                    setBridgeName(s);
                } else if (a == A_FRIEND_ADD) {
                    BridgeMsgSC m = addFriend(s) ? new BridgeMsgSC("sc.bridge.msg.friendAdded", cut(s)) : new BridgeMsgSC("sc.bridge.refuse.friendBad");
                    lastMsg = m;
                    lastMsgAt = worldObj == null ? 0 : worldObj.getTotalWorldTime();
                } else if (a == A_FRIEND_DEL) {
                    if (v.length >= 1) {
                        removeFriend(v[0]);
                    }
                } else {
                    setAccess(access == BridgeMathSC.ACCESS_PUBLIC ? BridgeMathSC.ACCESS_FRIENDS : BridgeMathSC.ACCESS_PUBLIC);
                }
                return;
            case A_REMOTE_MODE:
                if (!trusted(p)) {
                    refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
                    return;
                }
                setRemoteMode(!remoteMode);
                log(new BridgeMsgSC(remoteMode ? "sc.bridge.journal.remoteOn" : "sc.bridge.journal.remoteOffHand"), nameOf(p), false);
                return;
            case A_BIND_HELMET:
                bindHelmet(p);
                return;
            case A_FROM_COORD:
            case A_TO_COORD:
            case A_COPY_COORD:
                if (p == null || !allowed(p)) {
                    return;
                }
                coordAction(p, a, v, s);
                return;
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
            case A_REPAIR:
                repair(p);
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

    /** «Привязать шлем»: the worn Singular helmet with the Armour Link Module links this bridge (owner / friends, up to 3). */
    public BridgeMsgSC bindHelmet(EntityPlayer p) {
        if (p == null) {
            return null;
        }
        if (!trusted(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        net.minecraft.item.ItemStack helmet = p.getCurrentArmor(3);
        if (!com.sc.util.SingularLevel.isSingular(helmet) || ((com.sc.item.ItemArmorSC) helmet.getItem()).armorType != 0) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.nohelmet"));
        }
        if (!com.sc.bridge.BridgeItemDataSC.hasModule(helmet)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.nomodule"));
        }
        if (scan == null || !scan.found) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.build"));
        }
        int i = com.sc.bridge.BridgeItemDataSC.addLink(helmet, xCoord, yCoord, zCoord, ownDim(), ensureBridgeId(), name, bridgeKind());
        if (i < 0) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.links", BridgeMathSC.MAX_LINKS));
        }
        com.sc.bridge.BridgeItemDataSC.select(helmet, i);
        p.inventoryContainer.detectAndSendChanges();
        noteLink(p, BridgeMathSC.SRC_ARMOUR);
        BridgeMsgSC m = new BridgeMsgSC("sc.bridge.msg.helmetBound", nameArg(), i + 1, BridgeMathSC.MAX_LINKS);
        tell(p, m);
        return m;
    }

    /** The coordinators in a player's inventory: «Из коорд.» (the first with a point), «В коорд.», copy one into an empty one. */
    private void coordAction(EntityPlayer p, int a, int[] v, String s) {
        net.minecraft.item.ItemStack[] inv = p.inventory.mainInventory;
        int withPoint = -1, empty = -1, any = -1;
        net.minecraft.item.ItemStack held = p.getHeldItem();
        for (int i = 0; i < inv.length; i++) {
            if (com.sc.item.ItemCoordinatorSC.isCoordinator(inv[i])) {
                any = any < 0 ? i : any;
                if (com.sc.bridge.BridgeItemDataSC.point(inv[i]) != null) {
                    withPoint = withPoint < 0 ? i : withPoint;
                } else if (empty < 0) {
                    empty = i;
                }
            }
        }
        BridgeMsgSC m;
        if (a == A_FROM_COORD) {
            net.minecraft.item.ItemStack c = com.sc.item.ItemCoordinatorSC.isCoordinator(held) && com.sc.bridge.BridgeItemDataSC.point(held) != null
                    ? held : withPoint >= 0 ? inv[withPoint] : null;
            if (c == null) {
                m = new BridgeMsgSC("sc.bridge.refuse.nocoord");
            } else {
                int[] pt = com.sc.bridge.BridgeItemDataSC.point(c);
                setTarget(pt[0], pt[1], pt[2], pt[3]);
                targetRev++;
                m = new BridgeMsgSC("sc.bridge.msg.fromCoord", pt[0], pt[1], pt[2]);
            }
        } else if (a == A_TO_COORD) {
            net.minecraft.item.ItemStack c = com.sc.item.ItemCoordinatorSC.isCoordinator(held) ? held : empty >= 0 ? inv[empty] : any >= 0 ? inv[any] : null;
            if (c == null || v.length < 4) {
                m = new BridgeMsgSC("sc.bridge.refuse.nocoord");
            } else {
                World w = DimensionManager.getWorld(bridgeKind() == BridgeMathSC.SPACE ? v[3] : ownDim());
                if (w != null && !w.checkChunksExist(v[0] - 2, 0, v[2] - 2, v[0] + 2, 255, v[2] + 2)) {
                    w = null;                                // a point far off isn't checked: a packet never loads / generates chunks for free
                }
                int y = v[1];
                boolean safe = false;
                if (w != null && y != AUTO_Y) {
                    safe = BridgeSpaceSC.check(BridgeSpaceSC.of(w), v[0], y, v[2], BridgeMathSC.vortexSize(BridgeMathSC.GROUND), ringAxis()).free;
                } else if (w != null) {
                    y = BridgeSpaceSC.autoY(BridgeSpaceSC.of(w), v[0], v[2], BridgeMathSC.vortexSize(BridgeMathSC.GROUND), ringAxis(), BridgeSpaceSC.AUTO_DEPTH);
                    safe = y >= 0;
                    y = y >= 0 ? y : v[1];
                }
                com.sc.bridge.BridgeItemDataSC.setPoint(c, v[0], y, v[2], bridgeKind() == BridgeMathSC.SPACE ? v[3] : ownDim(), safe);
                if (s != null && s.trim().length() > 0) {
                    com.sc.bridge.BridgeItemDataSC.setPointName(c, s);
                }
                m = new BridgeMsgSC("sc.bridge.msg.toCoord", v[0], y == AUTO_Y ? "@auto" : String.valueOf(y), v[2]);
            }
        } else {
            if (withPoint < 0 || empty < 0) {
                m = new BridgeMsgSC("sc.bridge.refuse.copycoord");
            } else {
                com.sc.bridge.BridgeItemDataSC.copyPoint(inv[withPoint], inv[empty]);
                m = new BridgeMsgSC("sc.bridge.msg.copyCoord", com.sc.item.ItemCoordinatorSC.label(inv[withPoint]));
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        lastMsg = m;
        lastMsgAt = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        tell(p, m);
    }

    /** Adds a bookmark (the screen, a remote, the armour's «Запомнить точку»). @return the refusal, or null */
    public BridgeMsgSC addBookmark(EntityPlayer p, String n, int x, int y, int z, int dim) {
        if (!allowed(p)) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
        }
        if (bookmarks.size() >= maxBookmarks()) {
            return refuse(p, new BridgeMsgSC("sc.bridge.refuse.bookmarks", maxBookmarks()));
        }
        NBTTagCompound b = new NBTTagCompound();
        b.setString("n", cut(n));
        b.setIntArray("p", new int[]{x, y, z, dim});
        bookmarks.add(b);
        markDirty();
        return null;
    }

    /**
     * What a remote or the armour shows of this bridge (BridgeFarSC): its name, kind, readiness, capacitors,
     * tanks, ring, stability, the open portal, the bookmarks, and - for an order - its plan: the cost, what is
     * missing and where the ends would stand.
     */
    public NBTTagCompound writeFarState(EntityPlayer viewer, Order o) {
        if (scan == null) {
            rescan();
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setString("name", name);
        t.setString("owner", owner);
        t.setInteger("kind", bridgeKind());
        t.setBoolean("trusted", trusted(viewer));
        t.setBoolean("found", scan != null && scan.found);
        t.setBoolean("valid", scan != null && scan.valid);
        t.setBoolean("power", powerOn);
        t.setBoolean("calibrated", isCalibrated());
        t.setBoolean("open", open);
        t.setBoolean("remoteMode", remoteMode);
        t.setIntArray("time", new int[]{lifeLeft, lifeTotal, coolTicks, coolTotal, stability, shortTicks});
        t.setInteger("baseStab", stabNow().total);
        t.setInteger("wear", wear);
        stage3State(t);
        t.setLong("capEu", capacitorEnergy());
        t.setLong("capMax", capacitorMax());
        int[] caps = new int[tanks.length];
        for (int i = 0; i < caps.length; i++) {
            caps[i] = tankCapacity(i);
        }
        t.setIntArray("tanks", tanks.clone());
        t.setIntArray("tankCaps", caps);
        t.setIntArray("pos", new int[]{xCoord, yCoord, zCoord, ownDim()});
        NBTTagList bm = new NBTTagList();
        for (NBTTagCompound b : bookmarks) {
            bm.appendTag(b.copy());
        }
        t.setTag("bookmarks", bm);
        t.setInteger("bmMax", maxBookmarks());
        if (viewer != null && viewer.worldObj != null && viewer.worldObj.provider.dimensionId == ownDim()) {
            int[] c = ringCentre();
            t.setLong("dist", BridgeMathSC.distance(c[0], 0, c[2], (int) Math.floor(viewer.posX), 0, (int) Math.floor(viewer.posZ)));
        } else {
            t.setLong("dist", -1);
        }
        if (open) {
            t.setIntArray("endB", new int[]{bDim, bx, by, bz});
            t.setString("opener", opener);
        }
        if (o != null) {
            Plan pl = previewPlan(viewer, o);
            if (pl.refuse != null) {
                t.setTag("refuse", pl.refuse.write());
            } else if (pl.a == null || pl.b == null) {
                t.setString("consent", pl.consentFrom == null ? "" : pl.consentFrom);    // the friend's consent first: no plan yet
            } else {
                BridgeMathSC.Cost c = pl.cost;
                t.setLong("costEu", c.eu);
                t.setIntArray("cost", new int[]{c.sm, c.d, c.kr, c.ar, c.holdEu, c.heSec, c.arSec, c.d2oSec, c.lifeTicks, c.beacon ? 1 : 0, c.anchor ? 1 : 0});
                t.setInteger("proj", pl.dists.length);
                int pct = 0;
                for (int x : pl.pct) {
                    pct = Math.max(pct, x);
                }
                t.setInteger("pct", pct);
                t.setIntArray("fam", pl.fam);
                t.setInteger("famPct", c.famPct);
                BridgeMsgSC miss = missing(c);
                if (!miss.parts.isEmpty()) {
                    t.setTag("missing", miss.write());
                }
                if (pl.consentFrom != null) {
                    t.setString("consent", pl.consentFrom);
                }
                t.setIntArray("endA", new int[]{pl.a.kind, pl.a.dim, pl.a.x, pl.a.y, pl.a.z});
                t.setIntArray("endBp", new int[]{pl.b.kind, pl.b.dim, pl.b.x, pl.b.y, pl.b.z});
                t.setString("endAp", pl.a.player);
                t.setString("endBpl", pl.b.player);
            }
        }
        if (scan != null && scan.kind == BridgeMathSC.SPACE) {
            t.setTag("dims", dimList());
        }
        return t;
    }

    /** М-2: how long a remote's / the armour's preview plan is kept (ticks). */
    public static final int PREVIEW_CACHE_TICKS = 30;
    /** М-2: the preview plans by key (viewer, his block and look, the order) - {world time, Plan}. */
    private final java.util.Map<String, Object[]> previewCache = new java.util.HashMap<String, Object[]>();

    /**
     * М-2: the preview plan for the screens (asked once a second each) kept 1.5 s per key: the viewer, the block he
     * stands on, where he looks (a quarter - the projection spot), the order (mode, target, friend). A remote's screen
     * no longer re-reads up to a million blocks of a big nearest-place search on every poll; «Открыть» plans anew.
     */
    private Plan previewPlan(EntityPlayer viewer, Order o) {
        if (worldObj == null) {
            return plan(viewer, o, true);
        }
        long now = worldObj.getTotalWorldTime();
        StringBuilder k = new StringBuilder();
        if (viewer != null) {
            k.append(viewer.getCommandSenderName()).append('|').append(viewer.worldObj == null ? 0 : viewer.worldObj.provider.dimensionId).append('|')
                    .append((int) Math.floor(viewer.posX)).append(',').append((int) Math.floor(viewer.boundingBox.minY)).append(',')
                    .append((int) Math.floor(viewer.posZ)).append('|')
                    .append(net.minecraft.util.MathHelper.floor_double(viewer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3).append('|');
        }
        k.append(o.write().toString());
        String key = k.toString();
        Object[] hit = previewCache.get(key);
        if (hit != null && now >= (Long) hit[0] && now - (Long) hit[0] < PREVIEW_CACHE_TICKS) {
            return (Plan) hit[1];
        }
        if (previewCache.size() > 32) {
            java.util.Iterator<Object[]> it = previewCache.values().iterator();
            while (it.hasNext()) {
                long t = (Long) it.next()[0];
                if (now < t || now - t >= PREVIEW_CACHE_TICKS) {
                    it.remove();
                }
            }
            if (previewCache.size() > 32) {
                previewCache.clear();
            }
        }
        Plan pl = plan(viewer, o, true);
        previewCache.put(key, new Object[]{now, pl});
        return pl;
    }

    /** Stage 3 for the screens: heat, the overheat lock, the stability's factors, the environment, the repair's price, the scatter. */
    private void stage3State(NBTTagCompound t) {
        t.setInteger("heat", heat);
        t.setBoolean("overheat", overheatLock);
        t.setIntArray("stab", stabNow().parts());
        t.setIntArray("env", new int[]{envInterf ? 1 : 0, envStorm ? 1 : 0, envRes ? 1 : 0});
        t.setInteger("repairHe", BridgeMathSC.repairHe(wear));
        t.setLong("repairEu", BridgeMathSC.repairEu(wear));
        t.setIntArray("scatter", new int[]{openScatter, openShift});
        t.setBoolean("turb", turbulent);
    }

    /** The registered dimensions with their names (the Space bridge's choice). */
    private static NBTTagList dimList() {
        NBTTagList dims = new NBTTagList();
        for (Integer id : DimensionManager.getStaticDimensionIDs()) {
            NBTTagCompound d = new NBTTagCompound();
            d.setInteger("id", id);
            String nm;
            try {
                World dw = DimensionManager.getWorld(id);
                nm = dw != null ? dw.provider.getDimensionName() : DimensionManager.createProviderFor(id).getDimensionName();
            } catch (Throwable ex) {
                nm = "DIM" + id;
            }
            d.setString("n", nm);
            dims.appendTag(d);
        }
        return dims;
    }

    public void closeFrom(EntityPlayer p) {
        if (!trusted(p)) {
            refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", owner));
            return;
        }
        if (open) {
            shortWhat = "";
            closePortal("sc.bridge.journal.closed");
        }
    }

    /** A refusal said to the player and kept in the journal (the remote / armour paths). */
    public BridgeMsgSC refuseFar(EntityPlayer p, BridgeMsgSC m) {
        return refuse(p, m);
    }

    /** Everything the screen shows (BridgeNetSC sends it twice a second while the screen is open). */
    public NBTTagCompound writeState(EntityPlayer viewer) {
        if (scan == null) {
            rescan();
        }
        NBTTagCompound t = new NBTTagCompound();
        boolean ok = allowed(viewer);                         // a stranger at a FRIENDS / PRIVATE controller: no coordinates,
        boolean trust = trusted(viewer);                      // bookmarks, journal or friends (the screen reads missing keys as empty)
        t.setString("owner", owner);
        t.setBoolean("allowed", ok);
        t.setBoolean("isOwner", isOwner(viewer));
        t.setBoolean("trusted", trust);
        t.setString("name", name);
        NBTTagList fr = new NBTTagList();
        if (trust) {
            for (String f : friends) {
                fr.appendTag(new net.minecraft.nbt.NBTTagString(f));
            }
        }
        t.setTag("friends", fr);
        t.setBoolean("remoteMode", remoteMode);
        int remotes = 0, helmets = 0;
        for (NBTTagCompound l : links) {
            if (l.getInteger("k") == BridgeMathSC.SRC_ARMOUR) {
                helmets++;
            } else {
                remotes++;
            }
        }
        t.setIntArray("links", new int[]{remotes, helmets});
        t.setInteger("targetRev", targetRev);
        int coords = 0;
        if (viewer != null) {
            for (net.minecraft.item.ItemStack st : viewer.inventory.mainInventory) {
                coords += com.sc.item.ItemCoordinatorSC.isCoordinator(st) ? 1 : 0;
            }
        }
        t.setInteger("coords", coords);
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
        if (open && ok) {
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
        if (ok) {
            t.setIntArray("target", new int[]{tx, ty, tz, targetDim()});
            t.setBoolean("targetSet", targetSet);
        }
        BridgeMathSC.Cost c = previewCost(viewer);
        t.setLong("costEu", c.eu);
        t.setIntArray("fam", famInfo(viewer, targetDim(), tx, tz, c.beacon, c.anchor, false));
        t.setInteger("famPct", c.famPct);
        stage3State(t);
        t.setIntArray("cost", new int[]{c.sm, c.d, c.kr, c.ar, c.holdEu, c.heSec, c.arSec, c.d2oSec, c.lifeTicks, c.beacon ? 1 : 0, c.anchor ? 1 : 0});
        t.setLong("dist", targetDistance());
        if (place != null && ok) {
            t.setTag("place", place);
        }
        NBTTagList bm = new NBTTagList();
        if (ok) {
            for (NBTTagCompound b : bookmarks) {
                bm.appendTag(b.copy());
            }
        }
        t.setTag("bookmarks", bm);
        t.setInteger("bmMax", maxBookmarks());
        NBTTagList jl = new NBTTagList();
        if (trust) {
            for (NBTTagCompound j : journal) {
                jl.appendTag(j.copy());
            }
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
            t.setTag("dims", dimList());
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
        int[] h = nbt.getIntArray("Hold");
        if (open && hold != null && h.length == 4) {
            hold.holdEu = h[0];
            hold.heSec = h[1];
            hold.arSec = h[2];
            hold.d2oSec = h[3];
        }
        int[] pa = nbt.getIntArray("PortalA");
        aProj = open && pa.length == 6;
        if (aProj) {
            aDim = pa[0];
            ax = pa[1];
            ay = pa[2];
            az = pa[3];
            apAxis = pa[4];
            aW = pa[5];
        }
        openMode = nbt.getInteger("OpenMode");
        opener = nbt.getString("Opener");
        openPrecise = nbt.getBoolean("Precise");
        int[] s3 = nbt.getIntArray("Stage3");
        if (s3.length == 10) {
            heat = s3[0];
            heatAtClose = s3[1];
            overheatLock = s3[2] != 0;
            argonDeficit = s3[3];
            openDist = s3[4];
            openOtherDim = s3[5] != 0;
            massTotal = s3[6];
            peakHeat = s3[7];
            openScatter = s3[8];
            openShift = s3[9];
        }
        name = nbt.getString("Name");
        friends.clear();
        NBTTagList fl = nbt.getTagList("Friends", 8);
        for (int i = 0; i < fl.tagCount() && i < BridgeMathSC.MAX_FRIENDS; i++) {
            friends.add(fl.getStringTagAt(i));
        }
        remoteMode = nbt.getBoolean("RemoteMode");
        bridgeId = nbt.getLong("BridgeId");
        links.clear();
        NBTTagList ll = nbt.getTagList("Links", 10);
        for (int i = 0; i < ll.tagCount(); i++) {
            links.add(ll.getCompoundTagAt(i));
        }
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
        if (hold != null) {
            nbt.setIntArray("Hold", new int[]{hold.holdEu, hold.heSec, hold.arSec, hold.d2oSec});
        }
        if (open && aProj) {
            nbt.setIntArray("PortalA", new int[]{aDim, ax, ay, az, apAxis, aW});
        }
        nbt.setInteger("OpenMode", openMode);
        nbt.setString("Opener", opener);
        nbt.setBoolean("Precise", openPrecise);
        nbt.setIntArray("Stage3", new int[]{heat, heatAtClose, overheatLock ? 1 : 0, argonDeficit, (int) Math.min(Integer.MAX_VALUE, openDist),
                openOtherDim ? 1 : 0, massTotal, peakHeat, openScatter, openShift});
        nbt.setString("Name", name);
        NBTTagList fl = new NBTTagList();
        for (String f : friends) {
            fl.appendTag(new net.minecraft.nbt.NBTTagString(f));
        }
        nbt.setTag("Friends", fl);
        nbt.setBoolean("RemoteMode", remoteMode);
        nbt.setLong("BridgeId", bridgeId);
        NBTTagList ll = new NBTTagList();
        for (NBTTagCompound l : links) {
            ll.appendTag(l.copy());
        }
        nbt.setTag("Links", ll);
    }

    private void writeBookmarks(NBTTagCompound nbt) {
        NBTTagList bm = new NBTTagList();
        for (NBTTagCompound b : bookmarks) {
            bm.appendTag(b.copy());
        }
        nbt.setTag("Bookmarks", bm);
    }

    /** The item keeps the tanks and the bookmarks, and the ring's wear and cooling (М-4 / С12: not undone by moving the controller). */
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
        if (wear > 0) {
            nbt.setInteger("Wear", wear);
        }
        if (coolTicks > 0) {
            nbt.setIntArray("BridgeCool", new int[]{coolTicks, coolTotal, heatAtClose, overheatLock ? 1 : 0});
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        readTanks(nbt);
        readBookmarks(nbt);
        wear = Math.max(0, Math.min(BridgeMathSC.MAX_WEAR, nbt.getInteger("Wear")));
        int[] c = nbt.getIntArray("BridgeCool");
        if (c.length == 4 && c[0] > 0) {
            coolTicks = c[0];
            coolTotal = Math.max(c[0], c[1]);
            heatAtClose = Math.max(0, c[2]);
            overheatLock = c[3] != 0;
            heat = BridgeMathSC.coolingHeat(heatAtClose, coolTicks, coolTotal);
        }
        markDirty();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Facing", facing);
        if (open) {
            nbt.setIntArray("Holo", new int[]{bx, by, bz, bDim, aSize});      // §11: the target over the ring
        }
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    /** The hologram stands over the ring: drawn from further than the block. */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.AxisAlignedBB getRenderBoundingBox() {
        return holo != null ? INFINITE_EXTENT_AABB : super.getRenderBoundingBox();
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        facing = pkt.func_148857_g().getInteger("Facing");
        int[] h = pkt.func_148857_g().getIntArray("Holo");
        holo = h.length == 5 ? h : null;
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }

    // ------------------------------------------------------------------ tests

    /** World test: the ring cooled at once. */
    public void setCoolForTest(int t) {
        coolTicks = Math.max(0, t);
        if (coolTicks == 0) {
            overheatLock = false;
            heat = 0;
        }
    }

    /** «Дистанционный режим» holds its chunk ticket now. */
    public boolean hasRemoteTicket() {
        return ticketR != null;
    }

    /** World test: the wear set directly. */
    public void setWearForTest(int w) {
        wear = Math.max(0, Math.min(BridgeMathSC.MAX_WEAR, w));
    }

    /** World test: the heat set directly (tenths). */
    public void setHeatForTest(int h) {
        heat = Math.max(0, Math.min(BridgeMathSC.HEAT_MAX, h));
    }

    /** Where the last shaken arrival landed (С3), or null. */
    private int[] lastShift;

    public int[] getLastShift() {
        return lastShift;
    }

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
