package com.sc.debug;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassySC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.MapGenEmbassySC;
import com.sc.embassy.StructureEmbassyStartSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.worldgen.ChunkSaveGuardSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * The embassy's fly-through world test (the crash of 2026-10-22: "Exception ticking world" - a null chunk in WorldServer's block
 * ticks 30 s after an embassy's start was made under a player flying in): a player as the server sees one - in the Overworld's
 * playerEntities and its PlayerManager, so the chunks round him are watched, ticked (activeChunkSet), unloaded behind him and made
 * ahead of him exactly as for a real one (his packets go nowhere) - flies SPEED blocks a tick over fresh land through a fresh embassy
 * (its start made under him, its footprint levelled and built as he passes), then everything that happened is told:
 * <ul>
 * <li>the start made, not empty, the record's; every footprint chunk populated as he passes over the core;</li>
 * <li>no chunk made past his view by this mod's code (a cascade), none made while another is made or populated by this mod's code
 * (nested generation), none of this mod's asks loading a chunk again soon after it was unloaded (the crash's way: a chunk asked again
 * while its first save is being written comes back null);</li>
 * <li>not one chunk of his view missing past its tick (WorldServer's block ticks would ask it), the server's tick time and this mod's
 * share of the server thread (a sampler - what made the hours of lag), INFO.</li>
 * </ul>
 * After WorldTestEmbassySC (-Dsc.worldtest=true), or alone along a route of the operator's: -Dsc.flytest=x0,z0,x1,z1[,speed]
 * (the crash's own: the spawn to -819 3067 in a world of seed -4961976079085425528). The flyer leaves the world afterwards; the land
 * made stays.
 */
public final class WorldTestEmbassyFlySC {

    static final String PROP = "sc.flytest";
    private static final GameProfile FLYER = new GameProfile(UUID.fromString("5c0e7a1e-a77a-4000-8000-00000000f0f1"), "[SCTestFlyer]");
    /** Blocks a tick (creative flight sprinting is about 1.1, a gravitation chestplate's 2 and more), the flight's height. */
    static final double SPEED = 1.6D, Y = 110D;
    /** The flight starts this far from the core (past the reach a start is made at: the view + MapGenEmbassySC.RANGE) and ends this far past it. */
    static final int FROM = 30 * 16, PAST = 14 * 16;
    /** Ticks after the flight before the verdict; the longest the whole test may take; a load this soon after an unload is "again". */
    static final int SETTLE = 200, TIMEOUT = 30000, AGAIN = 2400;
    /** Its share of the server thread this mod may take while the flyer makes fresh land through an embassy (%). TODO(balance) */
    static final int OURS_MAX = 35;
    /** Rings of regions round the spawn's the world test looks for a fresh site in (a region is embassy.embassyGrid across). */
    static final int RINGS = 12;

    private final boolean alone;
    private int ticks, startAt, phase, settled, flightTicks;
    private long flightNanos, worstTick, lastTickAt;
    private double x0, z0, x1, z1, speed, length, flown, closest = Double.MAX_VALUE;
    private int[] footprint;
    private int coreLoaded = -1, corePopulated = -1;
    private int nullChunks;
    private final StringBuilder nullAt = new StringBuilder();
    private WorldServer w;
    private Flyer flyer;
    private Probe probe;
    private Sampler sampler;
    private EmbassySiteSC.Site site;
    private boolean freshSite;

    /** Registered after WorldTestEmbassySC (-Dsc.worldtest): the world test's last "DONE embassy" is printed after this one's. */
    static volatile boolean registered;

    public WorldTestEmbassyFlySC(boolean alone) {
        this.alone = alone;
        registered |= !alone;
    }

    /** -Dsc.flytest's route, or null. */
    static double[] route() {
        String p = System.getProperty(PROP);
        if (p == null) {
            return null;
        }
        String[] s = p.split(",");
        try {
            double[] r = new double[5];
            for (int i = 0; i < 4; i++) {
                r[i] = Double.parseDouble(s[i].trim());
            }
            r[4] = s.length > 4 ? Double.parseDouble(s[4].trim()) : SPEED;
            return r;
        } catch (RuntimeException e) {
            System.out.println("[SC-WORLDTEST] INFO embassy-fly: -D" + PROP + "=x0,z0,x1,z1[,speed] - not " + p);
            return null;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase >= 4) {
            return;
        }
        ticks++;
        try {
            if (phase == 0) {
                if (alone ? ticks < 100 : ticks < WorldTestEmbassySC.START || !WorldTestEmbassySC.finished && ticks < WorldTestEmbassySC.START + 90000) {
                    return;
                }
                phase = 1;
                startAt = ticks;
                w = MinecraftServer.getServer().worldServerForDimension(EmbassySiteSC.DIM);
                if (w == null) {
                    System.out.println("[SC-WORLDTEST] SKIP embassy-fly: no Overworld");
                    end();
                }
                return;
            }
            if (ticks - startAt > TIMEOUT) {
                print(false, "timed out (" + (int) flown + " of " + (int) length + " blocks flown)");
                end();
                return;
            }
            if (phase == 1) {
                if (queueOf(w.theChunkProviderServer.currentChunkLoader) > 16 && ticks - startAt < 1200) {
                    return;                                     // the Overworld's save queue short first (the window's test waits on it)
                }
                saveWindow();
                phase = 2;
                begin();
            } else if (phase == 2) {
                fly();
            } else if (phase == 3 && ++settled >= SETTLE) {
                verdict();
                end();
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            end();
        }
    }

    // ------------------------------------------------------------------ the route

    private void begin() {
        double[] r = alone ? route() : null;
        if (r != null) {
            x0 = r[0];
            z0 = r[1];
            x1 = r[2];
            z1 = r[3];
            speed = Math.max(0.1D, r[4]);
            site = EmbassySiteSC.worldFits(w) ? EmbassySiteSC.nearest(w, (int) x1, (int) z1, 600) : null;
            freshSite = site != null && fresh(site);
        } else {
            if (!SettlementSC.embassiesGenerate() || !EmbassySiteSC.worldFits(w)) {
                System.out.println("[SC-WORLDTEST] SKIP embassy-fly: the embassies don't generate here");
                phase = 4;
                return;
            }
            if (!chooseRoute()) {
                System.out.println("[SC-WORLDTEST] SKIP embassy-fly: no fresh embassy site within " + RINGS + " rings of regions round the spawn");
                phase = 4;
                return;
            }
            speed = SPEED;
        }
        length = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
        if (site != null) {
            footprint = EmbassyLayoutSC.footprintChunks(EmbassyLayoutSC.plan(w.getSeed(), site, site.floorY));
        }
        System.out.println("[SC-WORLDTEST] INFO embassy-fly: flying " + (int) x0 + " " + (int) z0 + " -> " + (int) x1 + " " + (int) z1 + " (" + (int) length
                + " blocks, " + speed + " a tick, view " + view() + ")" + (site == null ? ", no embassy site near its end"
                        : ", over the embassy site " + site + (freshSite ? " (fresh: no start, no record, its land not explored)" : " (made or explored before)")));
        flyer = new Flyer(w);
        flyer.setPosition(x0, Y, z0);
        flyer.managedPosX = x0;
        flyer.managedPosZ = z0;
        probe = new Probe(w, flyer);
        MinecraftForge.EVENT_BUS.register(probe);
        w.playerEntities.add(flyer);
        w.getPlayerManager().addPlayer(flyer);
        sampler = new Sampler(Thread.currentThread());
        sampler.start();
        lastTickAt = System.nanoTime();
    }

    /** A site with no start, no record, its land not explored and none of it near another start. */
    private boolean fresh(EmbassySiteSC.Site s) {
        MapGenEmbassySC gen = EmbassySC.mapGen(w);
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        return (gen == null || gen.startAt(w, s.chunkX(), s.chunkZ()) == null) && (reg == null || reg.atCentre(EmbassySiteSC.DIM, s.x, s.z) == null)
                && !EmbassySiteSC.explored(w, s) && !EmbassySiteSC.zoneNear(w, s);
    }

    /**
     * The world test's route: the nearest fresh site to the spawn (freshSites) whose way in - of eight - crosses no land made before
     * (as in the crash), else the one with the least old land on its way; the start is made under the flyer either way.
     */
    private boolean chooseRoute() {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        List<EmbassySiteSC.Site> sites = freshSites(sx, sz);
        int v = view() + 2, fresh = 0, best = Integer.MAX_VALUE;
        for (EmbassySiteSC.Site s : sites) {
            fresh++;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4D * k, dx = Math.cos(a), dz = Math.sin(a);
                double ax = s.x + 0.5D - dx * FROM, az = s.z + 0.5D - dz * FROM, bx = s.x + 0.5D + dx * PAST, bz = s.z + 0.5D + dz * PAST;
                int old = oldLand(ax, az, bx, bz, v);
                if (old < best) {                               // the least old land on the way (none: the crash's own fresh flight)
                    best = old;
                    site = s;
                    freshSite = true;
                    x0 = ax;
                    z0 = az;
                    x1 = bx;
                    z1 = bz;
                }
            }
            if (best == 0) {
                break;
            }
        }
        System.out.println("[SC-WORLDTEST] INFO embassy-fly: " + fresh + " fresh embassy sites (no start, no record, the land not explored) on the nearest"
                + " ring of regions round the spawn that has one (" + looked + " regions asked); the route's old land "
                + (site == null ? "-" : best + " of its samples (0: all new)"));
        return site != null;
    }

    private int looked;

    /**
     * The fresh sites of the nearest ring of regions round the spawn's (rings out to RINGS) that has any - a test world's near land is
     * explored by the earlier tests, its far one is not; each region's site is asked once (EmbassySiteSC caches it).
     */
    private List<EmbassySiteSC.Site> freshSites(final int sx, final int sz) {
        int g = EmbassySiteSC.grid(), rx0 = EmbassySiteSC.region(sx, g), rz0 = EmbassySiteSC.region(sz, g);
        List<EmbassySiteSC.Site> out = new ArrayList<EmbassySiteSC.Site>();
        for (int r = 0; r <= RINGS && out.isEmpty(); r++) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) {
                        continue;
                    }
                    looked++;
                    EmbassySiteSC.Site s = EmbassySiteSC.site(w, rx0 + i, rz0 + j);
                    if (s != null && !s.made && fresh(s)) {
                        out.add(s);
                    }
                }
            }
        }
        Collections.sort(out, new Comparator<EmbassySiteSC.Site>() {
            @Override
            public int compare(EmbassySiteSC.Site a, EmbassySiteSC.Site b) {
                return Double.compare(a.distSq(sx, sz), b.distSq(sx, sz));
            }
        });
        return out;
    }

    /**
     * The path's samples (every 2 chunks along it and across its view) loaded or on disk. A fresh site's start is made under the flyer
     * however old the land on the way is (no chunk within MapGenEmbassySC.RANGE of its core was made: else its start would be).
     */
    private int oldLand(double ax, double az, double bx, double bz, int v) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        int n = 0;
        for (double d = 0; d <= len; d += 32D) {
            int cx = (int) Math.floor((ax + (bx - ax) * d / len) / 16D), cz = (int) Math.floor((az + (bz - az) * d / len) / 16D);
            for (int i = -v; i <= v; i += 2) {
                for (int j = -v; j <= v; j += 2) {
                    n += EmbassySiteSC.chunkOnDisk(w, cx + i, cz + j) ? 1 : 0;
                }
            }
        }
        return n;
    }

    private static int view() {
        return MinecraftServer.getServer().getConfigurationManager().getViewDistance();
    }

    // ------------------------------------------------------------------ the flight

    private void fly() {
        long now = System.nanoTime();
        long dt = now - lastTickAt;
        lastTickAt = now;
        flightNanos += dt;
        flightTicks++;
        worstTick = Math.max(worstTick, dt);
        checkView();
        probe.pending();                                       // the save queue's longest
        flown = Math.min(length, flown + speed);
        double t = length <= 0D ? 1D : flown / length, x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
        flyer.prevPosX = flyer.posX;
        flyer.prevPosZ = flyer.posZ;
        flyer.setPosition(x, Y, z);
        w.getPlayerManager().updatePlayerPertinentChunks(flyer);
        if (site != null) {
            double d = site.distSq(x, z);
            if (d < closest) {
                closest = d;
                footprintNow();                                 // the closest pass so far (over the core on the world test's route)
            }
        }
        if (alone && flightTicks % 200 == 0) {
            System.out.println("[SC-WORLDTEST] INFO embassy-fly: at " + (int) x + " " + (int) z + " (" + (int) flown + "/" + (int) length + "), "
                    + flightTicks + " ticks, " + (flightNanos / 1000000L / Math.max(1, flightTicks)) + " ms a tick, worst " + worstTick / 1000000L
                    + " ms, chunks loaded " + w.theChunkProviderServer.getLoadedChunkCount() + ", pending saves " + probe.pending() + ", "
                    + probe.brief() + ", " + sampler.brief());
        }
        if (flown >= length) {
            phase = 3;
        }
    }

    /**
     * Every chunk of the flyer's block-tick square (WorldServer's activeChunkSet: floor(pos / 16) +- view) that the last tick asked
     * is loaded now (WorldServer.func_147456_g asked each - a missing one there was the crash's null).
     */
    private void checkView() {
        int r = view(), cx = (int) Math.floor(flyer.posX / 16D), cz = (int) Math.floor(flyer.posZ / 16D);
        ChunkProviderServer cp = w.theChunkProviderServer;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (cp.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(cx + i, cz + j)) == null) {
                    if (nullChunks++ < 8) {
                        nullAt.append(' ').append(cx + i).append(',').append(cz + j);
                    }
                }
            }
        }
    }

    /** As the flyer passes over the core: the footprint's chunks loaded and populated. */
    private void footprintNow() {
        if (footprint == null) {
            return;
        }
        int loaded = 0, populated = 0;
        for (int x = footprint[0]; x <= footprint[2]; x++) {
            for (int z = footprint[1]; z <= footprint[3]; z++) {
                Chunk c = (Chunk) w.theChunkProviderServer.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
                if (c != null) {
                    loaded++;
                    populated += c.isTerrainPopulated ? 1 : 0;
                }
            }
        }
        coreLoaded = loaded;
        corePopulated = populated;
    }

    // ------------------------------------------------------------------ the save window (the crash's race made to happen)

    /**
     * The crash's race made to happen, both ways it goes: the File IO thread held at a region file's lock (RegionFile.write is
     * synchronized - this thread takes it first) right after it took a chunk off its save queue (the chunk's first save). Forge's own
     * AnvilChunkLoader on a scratch folder: chunkExists "yes" while queued, then the read in the window NULL (what ChunkIOExecutor
     * hands the block ticks as the chunk) and chunkExists "no" (the chunk made anew from the seed). The Overworld's own loader
     * (ChunkSaveGuardSC): a far chunk with a marker block unloaded, its save held the same way - Forge's whole ask
     * (ChunkProviderServer.loadChunk) hands back that very chunk, its marker there.
     */
    private void saveWindow() throws Exception {
        int sx = w.getWorldInfo().getSpawnX() >> 4, sz = w.getWorldInfo().getSpawnZ() >> 4;
        Chunk spawn = w.getChunkFromChunkCoords(sx, sz);
        File dir = new File(new File(w.getSaveHandler().getWorldDirectory(), "sc-worldtest-chunkio"), String.valueOf(System.currentTimeMillis()));
        AnvilChunkLoader stock = new AnvilChunkLoader(dir);
        RegionFile rf = RegionFileCache.createOrLoadRegionFile(dir, sx, sz);
        boolean there, taken, thereAfter;
        Object[] read;
        long t0 = System.currentTimeMillis();
        synchronized (rf) {
            stock.saveChunk(w, spawn);
            there = stock.chunkExists(w, sx, sz);
            taken = waitTaken(stock, sx, sz);
            read = stock.loadChunk__Async(w, sx, sz);
            thereAfter = stock.chunkExists(w, sx, sz);
        }
        if (taken) {
            print(there && read == null && !thereAfter, "the crash's race on Forge's own chunk loader: a chunk queued for its first save - chunkExists "
                    + there + " (true), then taken by the File IO thread and not yet written (" + (System.currentTimeMillis() - t0) + " ms): its read "
                    + (read == null ? "null" : "the chunk") + " (null - what ChunkIOExecutor hands back as the chunk), chunkExists " + thereAfter
                    + " (false - the chunk made anew from the seed)");
        } else {
            System.out.println("[SC-WORLDTEST] INFO embassy-fly: the File IO thread didn't take the scratch chunk in 20 s - the race on Forge's own loader not shown");
        }
        IChunkLoader l = w.theChunkProviderServer.currentChunkLoader;
        boolean guard = ChunkSaveGuardSC.guarded(w), off = Boolean.getBoolean(ChunkSaveGuardSC.OFF);
        print(guard || off, "the Overworld saves through the chunk save guard: " + guard + " (" + (l == null ? "no loader" : l.getClass().getName())
                + (off ? "; -D" + ChunkSaveGuardSC.OFF + " - Forge's own" : "") + ")");
        if (!(l instanceof AnvilChunkLoader)) {
            return;
        }
        int fx = ((sx >> 5) + 97 + (int) (System.currentTimeMillis() / 1000L % 199L)) << 5 | 5, fz = ((sz >> 5) - 89) << 5 | 9;
        boolean before = EmbassySiteSC.chunkOnDisk(w, fx, fz);
        w.getChunkFromChunkCoords(fx, fz);
        int bx = (fx << 4) + 3, bz = (fz << 4) + 3;
        w.setBlock(bx, 250, bz, Blocks.glass, 0, 2);
        RegionFile frf = RegionFileCache.createOrLoadRegionFile(((AnvilChunkLoader) l).chunkSaveLocation, fx, fz);
        ChunkProviderServer cp = w.theChunkProviderServer;
        Chunk back;
        boolean unloaded, held, writing;
        t0 = System.currentTimeMillis();
        synchronized (frf) {
            cp.unloadChunksIfNotNearSpawn(fx, fz);
            for (int i = 0; i < 64 && cp.chunkExists(fx, fz); i++) {
                cp.unloadQueuedChunks();
            }
            unloaded = !cp.chunkExists(fx, fz);
            held = unloaded && waitTaken((AnvilChunkLoader) l, fx, fz);
            writing = l instanceof ChunkSaveGuardSC.Loader && ((ChunkSaveGuardSC.Loader) l).writing(fx, fz);
            back = held ? cp.loadChunk(fx, fz) : null;
        }
        boolean marker = back != null && back.getBlock(bx & 15, 250, bz & 15) == Blocks.glass;
        String what = "a far chunk " + fx + "," + fz + " (" + (before ? "saved before" : "its first save") + ") with a marker unloaded, taken off the queue by"
                + " the File IO thread and held there (" + (System.currentTimeMillis() - t0) + " ms; unloaded " + unloaded + ", held " + held + ", being written "
                + writing + "): Forge's loadChunk handed back " + (back == null ? "NULL" : marker ? "the chunk with its marker" : "a chunk WITHOUT its marker (made anew)");
        if (guard) {
            print(held && writing && back != null && marker, "the chunk save guard: " + what + " (the very chunk)");
        } else {
            System.out.println("[SC-WORLDTEST] INFO embassy-fly: Forge's own chunk saving: " + what);
        }
        if (back != null) {
            w.setBlock(bx, 250, bz, Blocks.air, 0, 2);
        }
    }

    /** The loader's save queue length (-1 unknown). */
    private static int queueOf(IChunkLoader l) {
        Object q = l instanceof AnvilChunkLoader ? Probe.field(l, "field_75828_a", "chunksToRemove") : null;
        Object lock = l instanceof AnvilChunkLoader ? Probe.field(l, "field_75827_c", "syncLockObject") : null;
        if (!(q instanceof List) || lock == null) {
            return -1;
        }
        synchronized (lock) {
            return ((List<?>) q).size();
        }
    }

    /** Waits (up to 20 s) till the File IO thread has taken chunk (x, z) off `l`'s save queue. */
    private static boolean waitTaken(AnvilChunkLoader l, int x, int z) throws InterruptedException {
        Object q = Probe.field(l, "field_75826_b", "pendingAnvilChunksCoordinates");
        Object lock = Probe.field(l, "field_75827_c", "syncLockObject");
        if (!(q instanceof Set) || lock == null) {
            return false;
        }
        Set<?> queued = (Set<?>) q;
        ChunkCoordIntPair c = new ChunkCoordIntPair(x, z);
        long until = System.currentTimeMillis() + 20000L;
        while (System.currentTimeMillis() < until) {
            synchronized (lock) {
                if (!queued.contains(c)) {
                    break;
                }
            }
            Thread.sleep(1L);
        }
        Thread.sleep(20L);                                     // the writer on to the region file's lock (it compresses first)
        synchronized (lock) {
            return !queued.contains(c);
        }
    }

    // ------------------------------------------------------------------ the verdict

    private void verdict() {
        sampler.halt();
        int all = footprint == null ? 0 : (footprint[2] - footprint[0] + 1) * (footprint[3] - footprint[1] + 1);
        if (site != null && freshSite) {
            MapGenEmbassySC gen = EmbassySC.mapGen(w);
            StructureEmbassyStartSC st = gen == null ? null : gen.startAt(w, site.chunkX(), site.chunkZ());
            SettlementRegistrySC reg = SettlementRegistrySC.get(w);
            SettlementRegistrySC.Settlement rec = reg == null ? null : reg.atCentre(EmbassySiteSC.DIM, site.x, site.z);
            boolean built = st != null && st.isSizeableStructure() && rec != null && st.settlementId() == rec.id;
            print(built && corePopulated == all && coreLoaded == all, "the start made under the flyer at " + site + ": built " + built + " (record "
                    + (rec == null ? "none" : String.valueOf(rec.id)) + ", " + (st == null ? 0 : st.getComponents().size()) + " pieces); over the core "
                    + coreLoaded + " of " + all + " footprint chunks loaded, " + corePopulated + " populated (all)");
        } else {
            System.out.println("[SC-WORLDTEST] INFO embassy-fly: no fresh embassy on the route - the start's line not told");
        }
        print(probe.pastOurs == 0, "no cascade: " + probe.loads + " chunks loaded, " + probe.past + " past the flyer's view (" + probe.pastOurs
                + " by this mod's code - 0)" + probe.list(probe.pastList));
        print(probe.nestedOurs == 0, "no nested generation: " + probe.nested + " chunks made while another was made or populated ("
                + probe.nestedOurs + " by this mod's code - 0)" + probe.list(probe.nestedList));
        print(probe.againOurs == 0, "no chunk asked again soon after its unload by this mod's code: " + probe.again + " loaded again within "
                + AGAIN + " ticks of their unload (" + probe.againOurs + " by this mod's code - 0), " + probe.fromPending
                + " of them while their save was still queued (the crash's window), " + probe.tickAsks + " by the block ticks' own ask of their chunk"
                + probe.list(probe.againList));
        print(probe.oursInMaking == 0 && probe.nestedOurs == 0, "the embassy's start and its look-ups ask for no chunk while a chunk is made: "
                + probe.oursInMaking + " chunks loaded from inside ReplaceBiomeBlocks / MapGenEmbassySC / EmbassySiteSC (0); " + probe.oursLoads
                + " chunks loaded at all with this mod's code on the stack" + probe.list(probe.oursList) + "; ReplaceBiomeBlocks of " + probe.made
                + " chunks took " + (probe.made == 0 ? 0 : probe.makeNanos / 1000L / probe.made) + " us a chunk, the longest " + probe.makeMax / 1000000L
                + " ms (chunk " + probe.makeMaxX + "," + probe.makeMaxZ + (site != null ? ", the core's chunk " + site.chunkX()
                + "," + site.chunkZ() : "") + ")");
        print(probe.remade == 0, "no saved chunk made anew from the seed (its save not found while it was being written - the crash's race; queue up to "
                + probe.maxPending + " chunks): " + probe.remade + " (0)" + probe.list(probe.remadeList));
        boolean off = Boolean.getBoolean(ChunkSaveGuardSC.OFF);
        print(off || ChunkSaveGuardSC.guarded(w), "the chunk save guard whole after the flight (its self-check found every save of "
                + probe.loads + " chunks' world in the queue as Forge keeps it): " + ChunkSaveGuardSC.guarded(w) + (off ? " (-D" + ChunkSaveGuardSC.OFF + ")" : ""));
        print(nullChunks == 0, "the flyer's tick square whole after every tick: " + nullChunks + " chunks missing (0)" + nullAt);
        int pct = sampler.busy == 0 ? 0 : sampler.ours * 100 / sampler.busy;
        print(pct <= OURS_MAX, "this mod's share of the server thread over " + flightTicks + " ticks of flight (" + (flightNanos / 1000000L / Math.max(1, flightTicks))
                + " ms a tick, worst " + worstTick / 1000000L + " ms; " + probe.loads + " chunks): " + pct + "% (<= " + OURS_MAX + "%) - " + sampler.report());
    }

    private void end() {
        if (phase >= 4) {
            return;
        }
        phase = 4;
        if (sampler != null) {
            sampler.halt();
        }
        if (probe != null) {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        if (w != null && flyer != null) {
            w.getPlayerManager().removePlayer(flyer);
            w.playerEntities.remove(flyer);
        }
        flyer = null;
        System.out.println("[SC-WORLDTEST] DONE embassy-fly");
        if (!alone) {
            System.out.println("[SC-WORLDTEST] DONE embassy");      // the embassy world test's own end, kept the world test's last line
        }
        if (alone) {
            MinecraftServer.getServer().initiateShutdown();
        }
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " embassy-fly: " + what);
    }

    // ------------------------------------------------------------------ the flyer

    /** A player the server sees (playerEntities, PlayerManager); his packets go nowhere, he is never ticked as an entity. */
    static final class Flyer extends FakePlayer {
        Flyer(WorldServer w) {
            super(w, FLYER);
            new Mute(this);
            capabilities.allowFlying = true;
            capabilities.isFlying = true;
        }
    }

    /** His connection: every packet dropped. */
    static final class Mute extends NetHandlerPlayServer {
        Mute(EntityPlayerMP p) {
            super(MinecraftServer.getServer(), new NetworkManager(false), p);
        }

        @Override
        public void sendPacket(Packet p) {
        }
    }

    // ------------------------------------------------------------------ the probe (chunk loads, unloads, generation)

    /** What the chunks did while he flew. */
    public static final class Probe {
        private final WorldServer world;
        private final EntityPlayerMP p;
        private final Map<Long, Long> unloadedAt = new HashMap<Long, Long>();
        private Set<?> pendingSet;
        private List<?> pendingList;
        private Object lock;
        int loads, past, pastOurs, nested, nestedOurs, again, againOurs, fromPending, maxPending, remade, tickAsks, oursLoads, oursInMaking, made;
        /** ReplaceBiomeBlocks' handlers (every mod's - the embassy's start is made in ours): their total and longest time, where. */
        long makeNanos, makeMax;
        int makeMaxX, makeMaxZ;
        private final java.util.ArrayDeque<long[]> making = new java.util.ArrayDeque<long[]>();
        final Map<String, Integer> oursList = new HashMap<String, Integer>();
        final Map<String, Integer> pastList = new HashMap<String, Integer>(), nestedList = new HashMap<String, Integer>(),
                againList = new HashMap<String, Integer>(), remadeList = new HashMap<String, Integer>();

        Probe(WorldServer world, EntityPlayerMP p) {
            this.world = world;
            this.p = p;
            IChunkLoader l = world.theChunkProviderServer.currentChunkLoader;
            if (l instanceof AnvilChunkLoader) {
                pendingSet = (Set<?>) field(l, "field_75826_b", "pendingAnvilChunksCoordinates");
                pendingList = (List<?>) field(l, "field_75828_a", "chunksToRemove");
                lock = field(l, "field_75827_c", "syncLockObject");
            }
        }

        static Object field(Object o, String... names) {
            for (String n : names) {
                try {
                    Field f = AnvilChunkLoader.class.getDeclaredField(n);
                    f.setAccessible(true);
                    return f.get(o);
                } catch (Throwable t) {
                    // the other name
                }
            }
            return null;
        }

        /** Chunks queued for writing (the File IO thread's backlog of this world). */
        int pending() {
            if (pendingList == null || lock == null) {
                return -1;
            }
            synchronized (lock) {
                int n = pendingList.size();
                maxPending = Math.max(maxPending, n);
                return n;
            }
        }

        boolean isPending(int x, int z) {
            if (pendingSet == null || lock == null) {
                return false;
            }
            synchronized (lock) {
                return pendingSet.contains(new ChunkCoordIntPair(x, z));
            }
        }

        String brief() {
            return loads + " loads, " + past + " past the view (" + pastOurs + " ours), nested " + nested + " (" + nestedOurs + " ours), again " + again
                    + " (" + againOurs + " ours, " + fromPending + " from the queue, " + tickAsks + " by the block ticks' own ask), made anew after their save "
                    + remade + ", queue max " + maxPending;
        }

        /** Within the flyer's view: his PlayerManager square (managedPos) or his block-tick square (pos), `margin` more. */
        private boolean inView(int x, int z, int margin) {
            int r = view() + margin;
            int mx = (int) p.managedPosX >> 4, mz = (int) p.managedPosZ >> 4;
            int ax = (int) Math.floor(p.posX / 16D), az = (int) Math.floor(p.posZ / 16D);
            return Math.abs(x - mx) <= r && Math.abs(z - mz) <= r || Math.abs(x - ax) <= r && Math.abs(z - az) <= r;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onUnload(ChunkEvent.Unload e) {
            if (e.world == world) {
                Chunk c = e.getChunk();
                unloadedAt.put(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(c.xPosition, c.zPosition)), Long.valueOf(world.getTotalWorldTime()));
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onLoad(ChunkEvent.Load e) {
            if (e.world != world) {
                return;
            }
            loads++;
            Chunk c = e.getChunk();
            StackTraceElement[] st = new Throwable().getStackTrace();
            String who = who(st, 0, st.length);
            boolean ours = who.startsWith("OURS");
            if (ours) {                                        // any chunk at all loaded with this mod's code on the stack
                oursLoads++;
                count(oursList, who);
                for (StackTraceElement f : st) {
                    if ("onReplaceBiomeBlocks".equals(f.getMethodName()) || f.getClassName().endsWith("MapGenEmbassySC")
                            || f.getClassName().endsWith("EmbassySiteSC")) {
                        oursInMaking++;                         // inside a chunk's making: the embassy's start, its look-ups
                        break;
                    }
                }
            }
            if (!inView(c.xPosition, c.zPosition, 0)) {
                past++;
                pastOurs += ours ? 1 : 0;
                count(pastList, who);
            }
            Long k = Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(c.xPosition, c.zPosition));
            Long at = unloadedAt.remove(k);
            if (at != null && world.getTotalWorldTime() - at.longValue() <= AGAIN) {
                again++;
                againOurs += ours ? 1 : 0;
                fromPending += isPending(c.xPosition, c.zPosition) ? 1 : 0;
                tickAsks += who.startsWith(" World.func_72964_e WorldServer.func_147456_g") ? 1 : 0;   // the block ticks' own ask of their chunk (the crash's line)
                count(againList, who);
            }
        }

        /** ReplaceBiomeBlocks: a chunk being made - inside another's load (its making or its populate)? */
        /** After every mod's ReplaceBiomeBlocks handler: how long they took (onReplace started the clock). */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onReplaceEnd(ChunkProviderEvent.ReplaceBiomeBlocks e) {
            if (e.world != world || making.isEmpty()) {
                return;
            }
            long[] m = making.pop();
            long d = System.nanoTime() - m[0];
            makeNanos += d;
            if (d > makeMax) {
                makeMax = d;
                makeMaxX = e.chunkX;
                makeMaxZ = e.chunkZ;
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onReplace(ChunkProviderEvent.ReplaceBiomeBlocks e) {
            if (e.world != world) {
                return;
            }
            made++;
            making.push(new long[]{System.nanoTime()});
            StackTraceElement[] st = new Throwable().getStackTrace();
            if (unloadedAt.containsKey(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(e.chunkX, e.chunkZ)))) {
                remade++;                                       // made anew from the seed after it was saved: its save was not found
                count(remadeList, who(st, 0, st.length));
            }
            int first = -1, second = -1;
            for (int i = 0; i < st.length; i++) {
                if ("originalLoadChunk".equals(st[i].getMethodName())) {
                    if (first < 0) {
                        first = i;
                    } else if (second < 0) {
                        second = i;
                    }
                }
            }
            if (second < 0) {
                return;
            }
            nested++;
            String who = who(st, first + 1, second);           // who asked for this chunk while the outer one was being made / populated
            nestedOurs += who.startsWith("OURS") ? 1 : 0;
            count(nestedList, who);
        }

        /** The frames that asked (frames from..to-1): OURS when one is this mod's code (not the test's), the first few named. */
        private static String who(StackTraceElement[] st, int from, int to) {
            boolean mine = false;
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (int i = from; i < to; i++) {
                String k = st[i].getClassName();
                if (k.startsWith("java.") || k.startsWith("sun.") || k.contains("EventBus") || k.contains("ASMEventHandler")
                        || k.startsWith("cpw.mods.fml.common.eventhandler") || k.startsWith("com.sc.debug.") || k.startsWith("net.minecraft.world.chunk.")
                        || k.endsWith("ChunkProviderServer") || k.startsWith("net.minecraftforge.common.chunkio")
                        || k.startsWith("net.minecraftforge.common.util.AsynchronousExecutor")) {
                    continue;
                }
                mine |= k.startsWith("com.sc.");
                if (n++ < 6) {
                    b.append(' ').append(k.substring(k.lastIndexOf('.') + 1)).append('.').append(st[i].getMethodName());
                }
            }
            return (mine ? "OURS" : "") + b;
        }

        private static void count(Map<String, Integer> m, String k) {
            Integer v = m.get(k);
            m.put(k, Integer.valueOf(v == null ? 1 : v.intValue() + 1));
        }

        String list(Map<String, Integer> m) {
            if (m.isEmpty()) {
                return "";
            }
            List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
            Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                    return b.getValue().intValue() - a.getValue().intValue();
                }
            });
            StringBuilder b = new StringBuilder("; by:");
            for (int i = 0; i < l.size() && i < 6; i++) {
                b.append(" [").append(l.get(i).getValue()).append(" x").append(l.get(i).getKey()).append(']');
            }
            return b.toString();
        }
    }

    // ------------------------------------------------------------------ the sampler (the server thread's time)

    /** Samples the server thread's stack every 2 ms: busy (not in MinecraftServer.run's sleep), this mod's code in it, where. */
    static final class Sampler extends Thread {
        private final Thread target;
        private volatile boolean run = true;
        int total, busy, ours, making;
        private final Map<String, Integer> inner = new HashMap<String, Integer>(), outer = new HashMap<String, Integer>();

        Sampler(Thread target) {
            super("SC-FlySampler");
            setDaemon(true);
            this.target = target;
        }

        void halt() {
            run = false;
        }

        @Override
        public void run() {
            while (run) {
                StackTraceElement[] st = target.getStackTrace();
                synchronized (this) {
                    tally(st);
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private void tally(StackTraceElement[] st) {
            total++;
            if (st.length == 0 || "sleep".equals(st[0].getMethodName()) && st.length > 1 && st[1].getClassName().endsWith("MinecraftServer")) {
                return;                                         // the server waits for its next tick
            }
            busy++;
            String in = null, out = null;
            boolean gen = false;
            for (StackTraceElement f : st) {
                String k = f.getClassName();
                gen |= "originalLoadChunk".equals(f.getMethodName()) || k.startsWith("net.minecraftforge.common.chunkio");
                if (k.startsWith("com.sc.") && !k.startsWith("com.sc.debug.")) {
                    String m = k.substring(k.lastIndexOf('.') + 1) + "." + f.getMethodName();
                    if (in == null) {
                        in = m;
                    }
                    out = m;
                }
            }
            making += gen ? 1 : 0;
            if (in != null) {
                ours++;
                Probe.count(inner, in);
                Probe.count(outer, out);
            }
        }

        synchronized String brief() {
            return "server busy " + (total == 0 ? 0 : busy * 100 / total) + "%, ours " + (busy == 0 ? 0 : ours * 100 / busy) + "% of it";
        }

        synchronized String report() {
            return "busy " + (total == 0 ? 0 : busy * 100 / total) + "% of the time, making / loading chunks " + (busy == 0 ? 0 : making * 100 / busy)
                    + "% of that; ours by entry" + top(outer) + "; ours by spot" + top(inner);
        }

        private String top(Map<String, Integer> m) {
            List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
            Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                    return b.getValue().intValue() - a.getValue().intValue();
                }
            });
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < l.size() && i < 6; i++) {
                b.append(' ').append(l.get(i).getKey()).append(' ').append(busy == 0 ? 0 : l.get(i).getValue().intValue() * 100 / busy).append('%');
            }
            return b.length() == 0 ? " none" : b.toString();
        }
    }
}
