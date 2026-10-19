package com.sc.embassy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.6, rule ПЕ-9): the «Достройка» - an embassy's core (TileEntitySettlementCoreSC's second,
 * the coordinator's call) puts back what other mods broke past Forge's events (a meteor's crater, a tree grown in from a neighbour's
 * window, Thaumcraft's taint, an Equal Trade wand, a Spatial IO cell): one populate window of its cylinder or its Vokzal at most every
 * WINDOW_TICKS, only while that window's four chunks are loaded and no player is within PLAYER_CLEAR of it (nobody sees blocks pop,
 * nothing is loaded for it); a window is its start's pieces run again over it (they are idempotent: the same block and meta is no
 * change, no tile is made twice). A pass over every window of the embassy, then PASS_PAUSE_TICKS of rest. The pass's place lives in
 * the record's extra EXTRA "G" (package G's key: {w: the next window's index, p: the world time the last pass ended}).
 * repair(world, s, true) - the operator's /scembassy repair: a whole pass now, players near or not, still only over loaded windows.
 * <ul>
 * <li>the windows: populate's own grid (blocks 16k + 8 .. 16k + 23: four chunks each, every neighbour a piece may look at within
 * them), the ones meeting the dome's disc or the Vokzal's platform, in x then z order (windows(s)); a window not populated yet is
 * never run (its own populate builds it the first time);</li>
 * <li>a second looks at up to LOOK windows - a window not ready (its chunks not loaded, a player near) is passed over until the next
 * pass - and runs the first ready one; a new embassy rests FIRST_REST_TICKS first (its city was just built; a meteor that fell on
 * it while its land was being made is put back within minutes, not after the long rest);</li>
 * <li>while a window runs, repairing() is true: the ground piece then fills only the protected ground and clears only growth
 * (ComponentEmbGroundSC), never a building's block;</li>
 * <li>the core itself gone (an AE2 meteor's crater sets every block but bedrock to air, the core's tile with it - its second gone
 * too): the Overworld's own tick (onWorldTick, FML's bus - SCMod registers INSTANCE) looks at every embassy record whose core's chunk
 * is loaded once a second and runs its Heart's window when the core block or its tile isn't there (heal: the Heart's piece puts the
 * core back and sets it up) - the core then goes on with the pass itself.</li>
 * </ul>
 * Fixed (the coordinator's): the class, the numbers' names, the signatures. Bodies G's.
 */
public final class EmbassyRepairSC {

    public static final EmbassyRepairSC INSTANCE = new EmbassyRepairSC();
    /** One window a second at most; a player this near (across) - not now; a whole pass, then this long of rest. TODO(balance) */
    public static final int WINDOW_TICKS = 20, PLAYER_CLEAR = 32, PASS_PAUSE_TICKS = 24000;
    /** A new embassy's first rest (ticks); a lost core's Heart window is run at most this often (ticks). */
    public static final int FIRST_REST_TICKS = 1200, HEAL_TICKS = 200;
    /** Package G's key of Settlement.extra. */
    public static final String EXTRA = "G";
    /** EXTRA's keys: the next window's index (0: a pass not begun), the world time the last pass ended. */
    public static final String K_NEXT = "w", K_PASS = "p";
    /** Windows a second looks at most (the not ready ones are passed over cheaply). */
    public static final int LOOK = 8;

    /** Record id -> the world time its last window was looked at (transient). */
    private static final Map<Integer, Long> LAST = new HashMap<Integer, Long>();
    /** Record id -> the world time its lost core's Heart window last ran (transient). */
    private static final Map<Integer, Long> HEALED = new HashMap<Integer, Long>();
    /** A window of the «Достройка» runs now (server thread). */
    private static boolean repairing;

    private EmbassyRepairSC() {
    }

    /** The pieces run for the «Достройка» now, not for their window's populate (ComponentEmbGroundSC asks it). */
    public static boolean repairing() {
        return repairing;
    }

    /** The core's second (server; `s` the core's own embassy record). */
    public static void tick(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, long now) {
        if (w == null || w.isRemote || r == null || !SettlementSC.isEmbassy(s)) {
            return;
        }
        Long last = LAST.get(Integer.valueOf(s.id));
        if (last != null && now >= last.longValue() && now - last.longValue() < WINDOW_TICKS) {
            return;
        }
        StructureEmbassyStartSC st = startOf(w, s);
        if (st == null) {
            return;
        }
        LAST.put(Integer.valueOf(s.id), Long.valueOf(now));
        NBTTagCompound g = s.extra.getCompoundTag(EXTRA);
        if (!g.hasKey(K_PASS)) {                                // a new embassy: its city was just built - a short rest first
            g.setInteger(K_NEXT, 0);
            g.setLong(K_PASS, now - PASS_PAUSE_TICKS + FIRST_REST_TICKS);     // as if a pass had ended that long ago
            s.extra.setTag(EXTRA, g);
            r.markDirty();
            return;
        }
        int next = g.getInteger(K_NEXT);
        long ended = g.getLong(K_PASS);
        if (next <= 0 && now >= ended && now - ended < PASS_PAUSE_TICKS) {
            return;                                             // resting (a time gone back - a new pass now)
        }
        List<int[]> wins = windows(s);
        int i = next < 0 || next >= wins.size() ? 0 : next;
        for (int k = 0; k < LOOK && i < wins.size(); k++) {
            int[] win = wins.get(i++);
            if (ready(w, win, false)) {
                run(w, st, win);
                break;
            }
        }
        if (i >= wins.size()) {
            g.setInteger(K_NEXT, 0);
            g.setLong(K_PASS, now);
        } else {
            g.setInteger(K_NEXT, i);
        }
        s.extra.setTag(EXTRA, g);
        r.markDirty();
    }

    /** The Overworld's second: every embassy whose core is gone gets its Heart's window (heal). */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        World w = e.world;
        if (e.phase != TickEvent.Phase.END || w == null || w.isRemote || w.provider == null || w.provider.dimensionId != EmbassySiteSC.DIM
                || w.getTotalWorldTime() % WINDOW_TICKS != 0 || !SettlementSC.governs(w)) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        for (SettlementRegistrySC.Settlement s : r.embassies()) {
            if (s.dim == EmbassySiteSC.DIM) {
                heal(w, s, false);
            }
        }
    }

    /**
     * Embassy `s`'s core is gone (its chunk loaded, no core block or no tile of this record there): its Heart's window - the one holding
     * the core's column - is run now if ready (`force`: a player near too), at most every HEAL_TICKS. @return whether it ran
     */
    public static boolean heal(World w, SettlementRegistrySC.Settlement s, boolean force) {
        IChunkProvider cp = w == null ? null : w.getChunkProvider();
        if (cp == null || s == null || !cp.chunkExists(s.x >> 4, s.z >> 4) || SettlementBlocksSC.core == null) {
            return false;                                       // its core's chunk not loaded: nothing to look at, nothing loaded
        }
        TileEntity te = w.getBlock(s.x, s.y, s.z) == SettlementBlocksSC.core ? w.getTileEntity(s.x, s.y, s.z) : null;
        if (te instanceof TileEntitySettlementCoreSC && ((TileEntitySettlementCoreSC) te).settlementId() == s.id) {
            return false;                                       // it stands: its own second runs the «Достройка»
        }
        long now = w.getTotalWorldTime();
        Long last = HEALED.get(Integer.valueOf(s.id));
        if (last != null && now >= last.longValue() && now - last.longValue() < HEAL_TICKS) {
            return false;
        }
        int[] win = {windowOf(s.x), windowOf(s.z)};
        StructureEmbassyStartSC st = startOf(w, s);
        if (st == null || !ready(w, win, force)) {
            return false;
        }
        HEALED.put(Integer.valueOf(s.id), Long.valueOf(now));
        run(w, st, win);
        FMLLog.info("[Silicon Age] Tuners' Embassies: the core of embassy %d at %d %d %d was gone (a crater, a mod's tool) - its Heart is put back",
                s.id, s.x, s.y, s.z);
        return true;
    }

    /** A whole pass now (the command). @return the windows put back (loaded ones only) */
    public static int repair(WorldServer w, SettlementRegistrySC.Settlement s, boolean force) {
        StructureEmbassyStartSC st = startOf(w, s);
        if (st == null) {
            return 0;
        }
        int n = 0;
        for (int[] win : windows(s)) {
            if (ready(w, win, force)) {
                run(w, st, win);
                n++;
            }
        }
        long now = w.getTotalWorldTime();
        NBTTagCompound g = s.extra.getCompoundTag(EXTRA);
        g.setInteger(K_NEXT, 0);                                // a pass made: the rest starts now
        g.setLong(K_PASS, now);
        s.extra.setTag(EXTRA, g);
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r != null) {
            r.markDirty();
        }
        LAST.put(Integer.valueOf(s.id), Long.valueOf(now));
        return n;
    }

    /** The built start of embassy record `s` (keyed by its core's chunk, its id the record's), or null (none made in this world). */
    public static StructureEmbassyStartSC startOf(World w, SettlementRegistrySC.Settlement s) {
        MapGenEmbassySC m = s == null ? null : EmbassySC.mapGen(w);
        StructureEmbassyStartSC st = m == null ? null : m.startAt(w, s.x >> 4, s.z >> 4);
        return st != null && st.isSizeableStructure() && st.settlementId() == s.id ? st : null;
    }

    /**
     * The «Достройка»'s windows of record `s` - {minX, minZ} of populate's grid (16k + 8), the ones meeting its dome's disc or its
     * Vokzal's platform (the beacon's VOKZAL_HALF square), in x then z order. Pure.
     */
    public static List<int[]> windows(SettlementRegistrySC.Settlement s) {
        List<int[]> out = new ArrayList<int[]>();
        int r = s.radius, h = EmbassyLayoutSC.VOKZAL_HALF;
        int minX = s.x - r, minZ = s.z - r, maxX = s.x + r, maxZ = s.z + r;
        if (s.hasDock) {
            minX = Math.min(minX, s.dockX - h);
            minZ = Math.min(minZ, s.dockZ - h);
            maxX = Math.max(maxX, s.dockX + h);
            maxZ = Math.max(maxZ, s.dockZ + h);
        }
        long r2 = (long) r * r;
        for (int wx = windowOf(minX); wx <= maxX; wx += 16) {
            for (int wz = windowOf(minZ); wz <= maxZ; wz += 16) {
                long nx = Math.max(wx, Math.min(wx + 15, s.x)) - s.x, nz = Math.max(wz, Math.min(wz + 15, s.z)) - s.z;
                boolean disc = nx * nx + nz * nz <= r2;
                boolean vokzal = s.hasDock && wx <= s.dockX + h && wx + 15 >= s.dockX - h && wz <= s.dockZ + h && wz + 15 >= s.dockZ - h;
                if (disc || vokzal) {
                    out.add(new int[]{wx, wz});
                }
            }
        }
        return out;
    }

    /** The populate window (its min corner, 16k + 8) holding block coordinate `c`. */
    public static int windowOf(int c) {
        return ((c - 8) >> 4 << 4) + 8;
    }

    /**
     * Window `win` may run now: its four chunks loaded (nothing is loaded for it), its own populate done (chunk (x0 >> 4, z0 >> 4)
     * populated - before that it is built for the first time by its populate), and - unless `force` - no real player within
     * PLAYER_CLEAR of it across.
     */
    static boolean ready(World w, int[] win, boolean force) {
        IChunkProvider cp = w.getChunkProvider();
        int cx = win[0] >> 4, cz = win[1] >> 4;
        if (cp == null || !cp.chunkExists(cx, cz) || !cp.chunkExists(cx + 1, cz) || !cp.chunkExists(cx, cz + 1) || !cp.chunkExists(cx + 1, cz + 1)
                || !w.getChunkFromChunkCoords(cx, cz).isTerrainPopulated) {
            return false;
        }
        if (force) {
            return true;
        }
        double r2 = (double) PLAYER_CLEAR * PLAYER_CLEAR;
        for (Object o : w.playerEntities) {
            if (!(o instanceof EntityPlayer) || o instanceof FakePlayer) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            double dx = Math.max(0D, Math.max(win[0] - p.posX, p.posX - (win[0] + 16)));
            double dz = Math.max(0D, Math.max(win[1] - p.posZ, p.posZ - (win[1] + 16)));
            if (dx * dx + dz * dz < r2) {
                return false;
            }
        }
        return true;
    }

    /** The start's pieces run again over window `win` (repairing() true meanwhile). */
    private static void run(World w, StructureEmbassyStartSC st, int[] win) {
        StructureBoundingBox box = new StructureBoundingBox(win[0], win[1], win[0] + 15, win[1] + 15);
        Random rand = new Random(w.getSeed() ^ ((long) win[0] * 341873128712L + (long) win[1] * 132897987541L));
        repairing = true;
        try {
            st.generateStructure(w, rand, box);
        } finally {
            repairing = false;
        }
    }

    /** Server stopped. */
    public static void clearAll() {
        LAST.clear();
        HEALED.clear();
        repairing = false;
    }
}
