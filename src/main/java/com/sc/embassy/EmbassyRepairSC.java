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
 * them), the ones meeting the dome's disc or the Vokzal's platform - and, with an airfield («Лётное поле посольства», ЛП-7), its
 * protected zone (EmbassyAirfieldSC.Field.zone: there the ground piece takes only growth and snow / ice, the field's pieces put their
 * own back) -, in x then z order (windows(s)); a window not populated yet is never run (its own populate builds it the first time);</li>
 * <li>a second looks at up to LOOK windows - a window not ready (its chunks not loaded, a player near) is passed over until the next
 * pass - and runs the first ready one; a new embassy rests FIRST_REST_TICKS first (its city was just built; a meteor that fell on
 * it while its land was being made is put back within minutes, not after the long rest);</li>
 * <li>while a window runs, repairing() is true: the ground piece then fills only the protected ground and clears only growth
 * (ComponentEmbGroundSC), never a building's block;</li>
 * <li>the approach funnels («Лётное поле», the review's): past the square nobody keeps the land, so the pass never runs there - but each
 * funnel window is cleared of growth ONCE more (settle: the funnel pieces only, over the 1:20 plane) as soon as every chunk round it
 * is populated: other mods' trees that don't keep to their chunk (IC2's rubber trees, Thaumcraft's great trees) grow into a window
 * built before their own chunk was; then never again (EXTRA's K_SETTLED - the land is the players'); ЛП-6 of the audit 2026-11-04:
 * only within EmbassyAirfieldSC.FUNNEL_DAYS of the record's making (later - K_SETTLED -1 at once);</li>
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
    /**
     * EXTRA's key (the review's): the funnel windows settled (funnelWindows' indexes as bits of a long - up to 64 windows; -1: all, or
     * ЛП-6 of the audit 2026-11-04: the field past EmbassyAirfieldSC.FUNNEL_DAYS - never again). K_SETTLED_MORE (ЛП-2's long funnels: more
     * than 64 windows) - one byte a window (1 settled) instead of the long's bits; "f" is then only ever -1.
     */
    public static final String K_SETTLED = "f", K_SETTLED_MORE = "fb";
    /** settle: the chunks this far round a funnel window's own (every populate that can still put growth into it) must be populated. */
    public static final int SETTLE_AROUND = 1;
    /** Windows a second looks at most (the not ready ones are passed over cheaply). */
    public static final int LOOK = 8;
    /** ЛП-2: a field's funnel windows at most (two long funnels: about 2 x 19 x 3). */
    public static final int MAX_FUNNEL_WINDOWS = 256;

    /** Record id -> the world time its last window was looked at (transient). */
    private static final Map<Integer, Long> LAST = new HashMap<Integer, Long>();
    /** Record id -> the world time its lost core's Heart window last ran (transient). */
    private static final Map<Integer, Long> HEALED = new HashMap<Integer, Long>();
    /** A window of the «Достройка» runs now (server thread). */
    private static boolean repairing;
    /** A funnel window is settled now (settle: the funnel pieces run once more past the square - server thread). */
    private static boolean settling;

    private EmbassyRepairSC() {
    }

    /** The pieces run for the «Достройка» now, not for their window's populate (ComponentEmbGroundSC asks it). */
    public static boolean repairing() {
        return repairing;
    }

    /** A funnel window is settled now (repairing() true too): ComponentEmbFunnelSC clears its growth once more. */
    public static boolean settling() {
        return settling;
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
        if (settle(w, r, s, st)) {
            return;                                             // a funnel window settled this second
        }
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
     * Vokzal's platform (the beacon's VOKZAL_HALF square) or (ЛП-7, docs/plan-embassy-airfield-core.md §4.3) its airfield's protected
     * zone (EmbassyAirfieldSC.Field.zone; not the funnels: past the square the land isn't protected), in x then z order. A record without
     * a field ("X") - the same windows as before. Pure.
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
        EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(s);
        int[][] zone = new int[f == null ? 0 : f.zone.length][];
        for (int i = 0; i < zone.length; i++) {
            zone[i] = f.world(f.zone[i]);                       // {minX, minZ, maxX, maxZ}
            minX = Math.min(minX, zone[i][0]);
            minZ = Math.min(minZ, zone[i][1]);
            maxX = Math.max(maxX, zone[i][2]);
            maxZ = Math.max(maxZ, zone[i][3]);
        }
        long r2 = (long) r * r;
        for (int wx = windowOf(minX); wx <= maxX; wx += 16) {
            for (int wz = windowOf(minZ); wz <= maxZ; wz += 16) {
                long nx = Math.max(wx, Math.min(wx + 15, s.x)) - s.x, nz = Math.max(wz, Math.min(wz + 15, s.z)) - s.z;
                boolean disc = nx * nx + nz * nz <= r2;
                boolean vokzal = s.hasDock && wx <= s.dockX + h && wx + 15 >= s.dockX - h && wz <= s.dockZ + h && wz + 15 >= s.dockZ - h;
                boolean field = false;
                for (int[] b : zone) {
                    field |= wx <= b[2] && wx + 15 >= b[0] && wz <= b[3] && wz + 15 >= b[1];
                }
                if (disc || vokzal || field) {
                    out.add(new int[]{wx, wz});
                }
            }
        }
        return out;
    }

    /**
     * The review's: record `s`'s approach funnels' windows ({minX, minZ} of populate's grid, the ones meeting Field.funnels; each funnel
     * in x then z order; at most 64 a version 1 field - settle's long, then K_SETTLED_MORE's bytes: ЛП-2's long funnels, at most
     * MAX_FUNNEL_WINDOWS). Empty: no field, no funnels (SHORT, HELI). Pure.
     */
    public static List<int[]> funnelWindows(SettlementRegistrySC.Settlement s) {
        List<int[]> out = new ArrayList<int[]>();
        EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(s);
        int cap = f == null || f.version < EmbassyAirfieldSC.LONG_FUNNELS ? 64 : MAX_FUNNEL_WINDOWS;    // a version 1 field: its windows as before
        for (int i = 0; f != null && i < f.funnels.length; i++) {
            int[] b = f.world(f.funnels[i]);
            for (int wx = windowOf(b[0]); wx <= b[2]; wx += 16) {
                for (int wz = windowOf(b[1]); wz <= b[3] && out.size() < cap; wz += 16) {
                    boolean twice = false;
                    for (int[] o : out) {
                        twice |= o[0] == wx && o[1] == wz;
                    }
                    if (!twice) {
                        out.add(new int[]{wx, wz});
                    }
                }
            }
        }
        return out;
    }

    /**
     * The review's: one funnel window of `s` not settled yet (of the next LOOK) whose chunks and every chunk SETTLE_AROUND round them are
     * loaded and populated, no player near (ready) - its funnel pieces run once more (settling: growth over the 1:20 plane goes), its
     * bit set. @return one ran
     */
    static boolean settle(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, StructureEmbassyStartSC st) {
        NBTTagCompound g = s.extra.getCompoundTag(EXTRA);
        if (g.getLong(K_SETTLED) == -1L) {
            return false;                                       // every funnel window settled: never again
        }
        List<int[]> wins = funnelWindows(s);
        if (wins.isEmpty()) {
            return false;
        }
        if (!EmbassyAirfieldSC.funnelsOpen(s.madeAt, w.getTotalWorldTime())) {
            g.setLong(K_SETTLED, -1L);                          // ЛП-6: FUNNEL_DAYS past the record's making - never again
            g.removeTag(K_SETTLED_MORE);
            s.extra.setTag(EXTRA, g);
            r.markDirty();
            return false;
        }
        if (wins.size() > 64) {
            return settleMore(w, r, s, st, g, wins);
        }
        long done = g.getLong(K_SETTLED), all = wins.size() >= 64 ? -1L : (1L << wins.size()) - 1L;
        int looked = 0;
        for (int i = 0; i < wins.size() && looked < LOOK; i++) {
            if ((done & 1L << i) != 0L) {
                continue;
            }
            looked++;
            int[] win = wins.get(i);
            if (!aroundPopulated(w, win) || !ready(w, win, false)) {
                continue;
            }
            runFunnels(w, st, win);
            done |= 1L << i;
            g.setLong(K_SETTLED, (done & all) == all ? -1L : done);     // all of them: -1, never looked at again
            s.extra.setTag(EXTRA, g);
            r.markDirty();
            return true;
        }
        return false;
    }

    /** settle of a field with more than 64 funnel windows (ЛП-2's long funnels): K_SETTLED_MORE's bytes, K_SETTLED -1 once all are done. */
    private static boolean settleMore(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, StructureEmbassyStartSC st, NBTTagCompound g,
            List<int[]> wins) {
        byte[] old = g.getByteArray(K_SETTLED_MORE), done = new byte[wins.size()];
        System.arraycopy(old, 0, done, 0, Math.min(old.length, done.length));
        int looked = 0;
        for (int i = 0; i < wins.size() && looked < LOOK; i++) {
            if (done[i] != 0) {
                continue;
            }
            looked++;
            int[] win = wins.get(i);
            if (!aroundPopulated(w, win) || !ready(w, win, false)) {
                continue;
            }
            runFunnels(w, st, win);
            done[i] = 1;
            boolean all = true;
            for (byte b : done) {
                all &= b != 0;
            }
            if (all) {
                g.setLong(K_SETTLED, -1L);                      // all of them: never looked at again
                g.removeTag(K_SETTLED_MORE);
            } else {
                g.setByteArray(K_SETTLED_MORE, done);
            }
            s.extra.setTag(EXTRA, g);
            r.markDirty();
            return true;
        }
        return false;
    }

    /** Every chunk of window `win` and SETTLE_AROUND round them is loaded and populated (nothing is loaded for it). */
    static boolean aroundPopulated(World w, int[] win) {
        IChunkProvider cp = w.getChunkProvider();
        int cx = win[0] >> 4, cz = win[1] >> 4;
        for (int x = cx - SETTLE_AROUND; x <= cx + 1 + SETTLE_AROUND; x++) {
            for (int z = cz - SETTLE_AROUND; z <= cz + 1 + SETTLE_AROUND; z++) {
                if (cp == null || !cp.chunkExists(x, z) || !w.getChunkFromChunkCoords(x, z).isTerrainPopulated) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The start's funnel pieces only run again over window `win` (repairing() and settling() true meanwhile). */
    private static void runFunnels(World w, StructureEmbassyStartSC st, int[] win) {
        StructureBoundingBox box = new StructureBoundingBox(win[0], win[1], win[0] + 15, win[1] + 15);
        Random rand = new Random(w.getSeed() ^ ((long) win[0] * 341873128712L + (long) win[1] * 132897987541L));
        repairing = true;
        settling = true;
        try {
            for (Object o : st.getComponents()) {
                if (o instanceof ComponentEmbFunnelSC && ((ComponentEmbFunnelSC) o).getBoundingBox().intersectsWith(box)) {
                    ((ComponentEmbFunnelSC) o).addComponentParts(w, rand, box);
                }
            }
        } finally {
            repairing = false;
            settling = false;
        }
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
        settling = false;
    }
}
