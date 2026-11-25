package com.sc.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): the Energy Bank's controller - the structure (its side, the cells, the ports - scanned when a
 * bank block near it changes (BlockEnergyBankSC -> markNear), every SCAN_SAFETY ticks to be sure, every SCAN_EVERY while a chunk of a
 * try isn't loaded; a part missing: the bank stands «разобран», nothing lost: the energy stays in the controller's NBT, capped to what the cells
 * hold when it forms again), the energy (a long), the inflow / outflow history (EnergyBankMathSC.HISTORY seconds). NBT "scBank" {eu: long,
 * cap: long, size} - fixed by the coordinator; the rest is ОМ-7's. A comparator reads its charge (IComparatorSC).
 * <p>
 * The scan: the controller sits on a face (not an edge); the block behind it inwards is a cell - so for each side 3, 5, 7 and each such
 * inward direction, every place of the controller on that face is tried (EnergyBankMathSC.check, read lazily: it stops at the first wrong
 * block); the last good cube is tried first. A cube with an unloaded chunk - the scan waits (the bank stays as it was). Not formed: the
 * energy stays, the ports stand; the screen says why (the try that got furthest: its reason and block).
 * <p>
 * The energy: each tick every IN port's buffer gives up to portRate while the bank has room (energy < capacity - a broken cell makes the
 * capacity smaller, the energy stays: until it drops below, out only), every OUT port's buffer gets up to portRate. The flow is summed for a
 * second, its EU/t kept HISTORY seconds (not saved: after a load it starts again).
 */
public class TileEntityEnergyBankSC extends TileEntity implements com.sc.energy.IComparatorSC {

    public static final String NBT = "scBank";
    /** The scan's period while a try waits on an unloaded chunk, ticks. */
    public static final int SCAN_EVERY = 20;
    /** The safety rescan's period, ticks (the scan is otherwise event-driven: markStructureDirty). */
    public static final int SCAN_SAFETY = 600;
    /** A dirty controller rescans at most this often, ticks (many blocks changed at once: one scan for them). */
    public static final int SCAN_MIN_GAP = 4;

    /** The server's loaded controllers (weak: a lost one never leaks) - BlockEnergyBankSC marks those near a changed block dirty. */
    private static final java.util.Set<TileEntityEnergyBankSC> LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<TileEntityEnergyBankSC, Boolean>());

    /**
     * BlockEnergyBankSC (server): a bank block at (x, y, z) put, taken away or changed - every loaded controller of `world` whose cube
     * could hold it (within SIZE_MAX - 1 on each axis) rescans.
     */
    public static void markNear(net.minecraft.world.World world, int x, int y, int z) {
        if (world == null || world.isRemote || LOADED.isEmpty()) {
            return;
        }
        int r = EnergyBankMathSC.SIZE_MAX - 1;
        for (TileEntityEnergyBankSC c : LOADED) {
            if (c.worldObj == world && !c.isInvalid() && Math.abs(c.xCoord - x) <= r && Math.abs(c.yCoord - y) <= r
                    && Math.abs(c.zCoord - z) <= r) {
                c.markStructureDirty();
            }
        }
    }

    /** The EU held and the capacity (the cells of the last good scan), the cube's side (0: not formed). */
    protected long energy, capacity;
    protected int size;

    // ---- the structure (server; the screen's copies come by the container)
    private int ox, oy, oz;
    private int small, big, portsIn, portsOut;
    /** Per tier: the IN ports (low 16 bits) and the OUT ports (high 16). */
    private final int[] portTiers = new int[Tier.values().length];
    private final List<int[]> ports = new ArrayList<int[]>();
    /** Not formed: the reason (EnergyBankMathSC.R_*) and its block. */
    private int badReason = EnergyBankMathSC.R_SIZE, badX, badY, badZ;
    /** Not saved: a block of the cube may have changed - rescan (true after a load / validate). */
    private transient boolean dirty = true;
    /** The last scan waited on an unloaded chunk: rescanned every SCAN_EVERY until it doesn't. */
    private transient boolean waiting;
    private transient long lastScan = Long.MIN_VALUE / 2;
    private transient boolean registered;

    // ---- the flow
    private long secIn, secOut;
    private int nowIn, nowOut, histHead;
    private final int[] histIn = new int[EnergyBankMathSC.HISTORY], histOut = new int[EnergyBankMathSC.HISTORY];
    private int comparatorLast = -1;

    public long energy() {
        return energy;
    }

    public long capacity() {
        return capacity;
    }

    public boolean formed() {
        return size > 0;
    }

    public int size() {
        return size;
    }

    public int cellsSmall() {
        return small;
    }

    public int cellsBig() {
        return big;
    }

    public int portsIn() {
        return portsIn;
    }

    public int portsOut() {
        return portsOut;
    }

    /** Per tier: IN | OUT << 16. */
    public int portTier(int ordinal) {
        return ordinal >= 0 && ordinal < portTiers.length ? portTiers[ordinal] : 0;
    }

    public int nowIn() {
        return nowIn;
    }

    public int nowOut() {
        return nowOut;
    }

    /** The inflow `ago` seconds back (0 - the last second). */
    public int histIn(int ago) {
        return histIn[ring(histHead - 1 - ago)];
    }

    public int histOut(int ago) {
        return histOut[ring(histHead - 1 - ago)];
    }

    private static int ring(int i) {
        int m = EnergyBankMathSC.HISTORY;
        return (i % m + m) % m;
    }

    public int badReason() {
        return badReason;
    }

    public int[] badAt() {
        return new int[]{badX, badY, badZ};
    }

    /** ОМ-7 (the placed controller's item): the energy it carried - kept even over the capacity (out only until it drops). */
    public void setEnergyFromItem(long eu) {
        energy = Math.max(0L, eu);
        markDirty();
    }

    @Override
    public int comparatorLevel() {
        return com.sc.energy.ComparatorSC.level(energy, capacity);
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
        if (!registered) {
            registered = true;
            LOADED.add(this);
        }
        long t = worldObj.getTotalWorldTime();
        long phase = t + (xCoord & 15);
        if (dirty && t - lastScan >= SCAN_MIN_GAP || waiting && phase % SCAN_EVERY == 0 || phase % SCAN_SAFETY == 0) {
            dirty = false;
            lastScan = t;
            waiting = scan();
        }
        if (formed()) {
            move();
        }
        if (t % 20 == 0) {
            nowIn = (int) Math.min(Integer.MAX_VALUE, secIn / 20);
            nowOut = (int) Math.min(Integer.MAX_VALUE, secOut / 20);
            histIn[histHead] = nowIn;
            histOut[histHead] = nowOut;
            histHead = (histHead + 1) % EnergyBankMathSC.HISTORY;
            secIn = 0;
            secOut = 0;
            comparatorLast = com.sc.energy.ComparatorSC.notify(this, comparatorLast);
        }
    }

    /** The ports' buffers this tick: IN into the bank while it has room, OUT out of it. */
    private void move() {
        long before = energy;
        for (int[] p : ports) {
            if (!worldObj.blockExists(p[0], p[1], p[2])) {
                continue;                                       // a port in an unloaded chunk waits (never loads one from the tick)
            }
            TileEntity te = worldObj.getTileEntity(p[0], p[1], p[2]);
            if (!(te instanceof TileEntityEnergyBankPortSC)) {
                continue;
            }
            TileEntityEnergyBankPortSC port = (TileEntityEnergyBankPortSC) te;
            long rate = EnergyBankMathSC.portRate(port.getTier());
            if (port.isOut()) {
                int put = (int) Math.min(Math.min(rate, energy), port.room());
                if (put > 0) {
                    put = port.bankPut(put);
                    energy -= put;
                    secOut += put;
                }
            } else if (energy < capacity) {
                int take = (int) Math.min(Math.min(rate, capacity - energy), port.getEnergyStored());
                if (take > 0) {
                    take = port.bankTake(take);
                    energy += take;
                    secIn += take;
                }
            }
        }
        if (energy != before) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);    // saved; comparators once a second
        }
    }

    // ------------------------------------------------------------------ the scan

    /** The world's blocks of one try's cube, read as check() asks (and kept for the good one). */
    private final class WorldKinds extends EnergyBankMathSC.Kinds {
        final int n, x0, y0, z0;
        final int[] seen;

        WorldKinds(int n, int x0, int y0, int z0) {
            this.n = n;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            seen = new int[n * n * n];
            java.util.Arrays.fill(seen, Integer.MIN_VALUE);
        }

        @Override
        public int at(int i) {
            if (seen[i] == Integer.MIN_VALUE) {
                int x = x0 + i % n, y = y0 + i / n % n, z = z0 + i / (n * n);
                Block b = worldObj.getBlock(x, y, z);
                seen[i] = b == com.sc.init.IdeaBlocksSC.energyBank && b != null ? worldObj.getBlockMetadata(x, y, z) : EnergyBankMathSC.K_NONE;
            }
            return seen[i];
        }
    }

    /** -2: a chunk of the cube isn't loaded (wait), else EnergyBankMathSC.check's code. */
    private static final int UNLOADED = -2;

    private int tryCube(int n, int x0, int y0, int z0, WorldKinds[] keep) {
        if (y0 < 0 || y0 + n - 1 > 255) {
            return EnergyBankMathSC.code(0, EnergyBankMathSC.R_SIZE);
        }
        if (!worldObj.checkChunksExist(x0, y0, z0, x0 + n - 1, y0 + n - 1, z0 + n - 1)) {
            return UNLOADED;
        }
        WorldKinds k = new WorldKinds(n, x0, y0, z0);
        keep[0] = k;
        return EnergyBankMathSC.check(n, k);
    }

    /**
     * When dirty (a bank block near changed), every SCAN_SAFETY ticks, every SCAN_EVERY while waiting: the last good cube first, then
     * every try round the controller (see the class). @return a try waited on an unloaded chunk (scanned again soon)
     */
    private boolean scan() {
        WorldKinds[] keep = new WorldKinds[1];
        boolean oldBroken = false;                              // the last cube is loaded and no longer good
        if (size > 0) {
            int c = tryCube(size, ox, oy, oz, keep);
            if (c == UNLOADED) {
                return true;                                    // part of it unloaded: the bank stays as it was
            }
            if (c == EnergyBankMathSC.OK) {
                form(keep[0]);
                return false;
            }
            oldBroken = true;
        }
        int bestIndex = -1, bestCode = EnergyBankMathSC.code(0, EnergyBankMathSC.R_SIZE);
        int[] bestAt = {xCoord, yCoord, zCoord};
        boolean unloaded = false;
        for (int n = EnergyBankMathSC.SIZE_MIN; n <= EnergyBankMathSC.SIZE_MAX; n += 2) {
            for (int d = 0; d < 6; d++) {
                int ix = Facing.offsetsXForSide[d], iy = Facing.offsetsYForSide[d], iz = Facing.offsetsZForSide[d];
                if (yCoord + iy < 0 || yCoord + iy > 255) {
                    continue;                                   // out of the world: the inside isn't this way
                }
                if (!worldObj.blockExists(xCoord + ix, yCoord + iy, zCoord + iz)) {
                    unloaded = true;                            // the neighbour's chunk isn't loaded: this way waits
                    continue;
                }
                Block behind = worldObj.getBlock(xCoord + ix, yCoord + iy, zCoord + iz);
                int bm = worldObj.getBlockMetadata(xCoord + ix, yCoord + iy, zCoord + iz);
                if (behind != com.sc.init.IdeaBlocksSC.energyBank || bm != EnergyBankMathSC.K_CELL_4 && bm != EnergyBankMathSC.K_CELL_16) {
                    continue;                                   // the inside isn't this way
                }
                for (int a = 1; a <= n - 2; a++) {
                    for (int b = 1; b <= n - 2; b++) {
                        int[] o = origin(n, ix, iy, iz, a, b);
                        int c = tryCube(n, o[0], o[1], o[2], keep);
                        if (c == UNLOADED) {
                            unloaded = true;                    // this try waits; the others still may form it
                            continue;
                        }
                        if (c == EnergyBankMathSC.OK) {
                            form(keep[0]);
                            return false;
                        }
                        int i = EnergyBankMathSC.badIndex(c);
                        if (i > bestIndex) {
                            bestIndex = i;
                            bestCode = c;
                            bestAt = new int[]{o[0] + i % n, o[1] + i / n % n, o[2] + i / (n * n)};
                        }
                    }
                }
            }
        }
        if (unloaded && !oldBroken) {
            return true;                                        // nothing formed, but a try is unloaded: the bank stays as it was
                                                                // (a broken old cube unforms: its capacity and ports mustn't stay)
        }
        unform(EnergyBankMathSC.badReason(bestCode), bestAt);
        return unloaded;                                        // unformed, but an unloaded try may still form it once it loads
    }

    /** A block of the cube (or of a cube it may form) changed: rescan (BlockEnergyBankSC, markNear). */
    public void markStructureDirty() {
        dirty = true;
    }

    @Override
    public void validate() {
        super.validate();
        dirty = true;                                           // a new / loaded / moved controller scans at once
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unregister();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unregister();
    }

    /** Out of LOADED - only a server's controller ever joins it (the client's never touches the set from its thread). */
    private void unregister() {
        if (registered) {
            LOADED.remove(this);
            registered = false;
        }
    }

    /**
     * The cube's min corner for side `n`, the inward direction (ix, iy, iz) and the controller at (a, b) on its face's other two axes
     * (1..n-2: never on an edge).
     */
    private int[] origin(int n, int ix, int iy, int iz, int a, int b) {
        int x0, y0, z0;
        if (ix != 0) {
            x0 = ix > 0 ? xCoord : xCoord - (n - 1);
            y0 = yCoord - a;
            z0 = zCoord - b;
        } else if (iy != 0) {
            y0 = iy > 0 ? yCoord : yCoord - (n - 1);
            x0 = xCoord - a;
            z0 = zCoord - b;
        } else {
            z0 = iz > 0 ? zCoord : zCoord - (n - 1);
            x0 = xCoord - a;
            y0 = yCoord - b;
        }
        return new int[]{x0, y0, z0};
    }

    /** A good cube: the cells, the ports (told whose they are), the capacity. */
    private void form(WorldKinds k) {
        int n = k.n, s = 0, g = 0, in = 0, out = 0;
        java.util.Arrays.fill(portTiers, 0);
        ports.clear();
        for (int i = 0; i < k.seen.length; i++) {
            int kind = k.at(i);
            if (kind == EnergyBankMathSC.K_CELL_4) {
                s++;
            } else if (kind == EnergyBankMathSC.K_CELL_16) {
                g++;
            } else if (EnergyBankMathSC.isPort(kind)) {
                int x = k.x0 + i % n, y = k.y0 + i / n % n, z = k.z0 + i / (n * n);
                TileEntity te = worldObj.getTileEntity(x, y, z);
                if (te instanceof TileEntityEnergyBankPortSC) {
                    TileEntityEnergyBankPortSC p = (TileEntityEnergyBankPortSC) te;
                    p.bind(xCoord, yCoord, zCoord);
                    ports.add(new int[]{x, y, z});
                    int tier = kind - EnergyBankMathSC.K_PORT_FIRST;
                    if (p.isOut()) {
                        out++;
                        portTiers[tier] += 1 << 16;
                    } else {
                        in++;
                        portTiers[tier]++;
                    }
                }
            }
        }
        long cap = EnergyBankMathSC.scaledCapacity(s, g, com.sc.util.ConfigSC.storageCapacity);
        boolean changed = size != n || capacity != cap;
        size = n;
        ox = k.x0;
        oy = k.y0;
        oz = k.z0;
        small = s;
        big = g;
        portsIn = in;
        portsOut = out;
        capacity = cap;
        badReason = 0;
        if (changed) {
            markDirty();
        }
    }

    private void unform(int reason, int[] at) {
        boolean changed = size != 0;
        size = 0;
        ports.clear();
        portsIn = 0;
        portsOut = 0;
        java.util.Arrays.fill(portTiers, 0);
        badReason = reason;
        badX = at[0];
        badY = at[1];
        badZ = at[2];
        if (changed) {
            markDirty();
        }
    }

    // ------------------------------------------------------------------ the screen's copies (ContainerEnergyBankSC)

    public void setClient(long eu, long cap, int n, int s, int g, int in, int out, int fin, int fout, int reason, int bx, int by, int bz) {
        energy = eu;
        capacity = cap;
        size = n;
        small = s;
        big = g;
        portsIn = in;
        portsOut = out;
        nowIn = fin;
        nowOut = fout;
        badReason = reason;
        badX = bx;
        badY = by;
        badZ = bz;
    }

    public void setPortTierClient(int ordinal, int v) {
        if (ordinal >= 0 && ordinal < portTiers.length) {
            portTiers[ordinal] = v;
        }
    }

    /** The client's graph: sample `ago` seconds back. */
    public void setHistoryClient(int ago, int in, int out) {
        int i = ring(histHead - 1 - ago);
        histIn[i] = in;
        histOut[i] = out;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("eu", energy);
        t.setLong("cap", capacity);
        t.setByte("size", (byte) size);
        t.setInteger("ox", ox);
        t.setInteger("oy", oy);
        t.setInteger("oz", oz);
        nbt.setTag(NBT, t);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagCompound t = nbt.getCompoundTag(NBT);
        energy = Math.max(0L, t.getLong("eu"));
        capacity = Math.max(0L, t.getLong("cap"));
        size = t.getByte("size");
        ox = t.getInteger("ox");
        oy = t.getInteger("oy");
        oz = t.getInteger("oz");
    }
}
