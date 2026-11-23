package com.sc.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.embassy.CommandEmbassySC;
import com.sc.embassy.EmbassyAirDefenceSC;
import com.sc.embassy.EmbassyAirRulesSC;
import com.sc.embassy.EmbassyAirServerSC;
import com.sc.embassy.EmbassyAirServiceSC;
import com.sc.embassy.EmbassyAirfieldSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyPiecesSC;
import com.sc.embassy.EmbassyQuartersSC;
import com.sc.embassy.EmbassyRepairSC;
import com.sc.embassy.EmbassySC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.MapGenEmbassySC;
import com.sc.embassy.StructureEmbassyStartSC;
import com.sc.embassy.api.AirfieldInfoSC;
import com.sc.embassy.api.EmbassyAirApiSC;
import com.sc.init.ModItems;
import com.sc.settle.NpcKindSC;
import com.sc.settle.RosterSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SquadSC;
import com.sc.settle.ViolationRulesSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.ai.NpcLinkSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * PACKAGE D (docs/plan-embassy-airfield-core.md §11.2): the world test of «Лётное поле посольства» on a real server (-Dsc.worldtest; SCMod
 * registers one on FML's bus after WorldTestEmbassyFlySC - the coordinator's). It sets embassy.airfield ALWAYS for its own embassy
 * (SubstrateConfigSC.setEmbassyAirfield, the old value back after it), makes a FULL embassy on new land far from the others (counting
 * ChunkEvent.Load before and after: no chunk outside the start's windows - no cascade), teleports a FakePlayer into the middle of the
 * future field, checks the built field against EmbassyAirfieldSC.Field (the paving, the lights at Field.lights, the columns, the desk,
 * the beacon, the guns at Field.aaBlock), the protection (break / place / blast / spawn on the field, the column's click passes), the
 * registry (reach, airfieldAt, overlapping), the API (airfields, airfieldAt, neutralAt, isRunwaySurface, pay / quote codes, firstLanding
 * once, requestPass once a day, admitted / escortOf), the column's armour gases and the «Лётная книжка», the Dispatcher's AIR_STATE, the
 * «Достройка» over the field (snow and growth gone, a building's block kept), and an old record without "X" untouched.
 * <p>
 * Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO airfield: ...", the last line "DONE airfield". It begins after WorldTestEmbassyFlySC
 * (its own static "finished" when it has one - asked by name -, else its flyer seen in the Overworld and gone, or never seen within
 * FLY_SEEN of WorldTestEmbassySC's end) and does its work in a few steps (generation and the checks at once, the «Достройка» later). The
 * players: a FakePlayer for the protection's events (FakePlayer gets no packet, no service, no pass, no logbook - checked too) and real
 * test players that never tick nor join the server's list (WorldTestArmourerSC.StationPlayer: their chat kept) for the API, the column and
 * the tab - two at once (LAN). Built on the core alone: the packages' bodies are asked for through the core's fixed signatures; one not
 * written yet fails its own line. Every ticket released, the test's blocks set back, its creatures never spawned; the land made stays.
 */
public final class WorldTestAirfieldSC {

    /** Done (later tests may wait for it). */
    static volatile boolean finished;

    /** Never before this. */
    static final int START = WorldTestEmbassySC.START + 1200;
    /** The longest wait for the embassy tests; the fly-through's flyer not seen this long after WorldTestEmbassySC's end - it skipped. */
    private static final int WAIT_MAX = 180000, FLY_SEEN = 3600;
    /** The schedule (ticks from the start: the column's clicks T_CLICK apart - past its HOLD_TICKS, within COLUMN_CONFIRM_TICKS); the longest it may take. */
    private static final int T_CHECKS = 2, T_CLICK = 10, T_REPAIR = 40, T_END = 80, TIMEOUT = 6000;
    /** Rings of regions round the spawn's a fresh site is looked for in; this many fresh sites asked for a FULL field (about 60% of the land gives one). */
    private static final int RINGS = 14, SITES = 8;
    /** The record extra's mark of the embassy this test made (with ALWAYS - a later run in the same world knows it is no generator's). */
    static final String TEST_MARK = "SCTestAirfield";
    /** The decision's funnels (EmbassyAirSiteSC.END_LOW / END_HIGH) whose chunks were on disk before the start was made. */
    private final boolean[] funnelOnDisk = new boolean[2];
    /** WorldTestEmbassyFlySC's flyer (its profile's id, when its field can't be read). */
    private static final UUID FLYER = UUID.fromString("5c0e7a1e-a77a-4000-8000-00000000f0f1");
    private static final GameProfile FAKE = new GameProfile(UUID.fromString("5c0e7a1e-a1f1-4000-8000-0000000000a1"), "[SCTestAirfield]");

    private int ticks, startAt, next, embassyDoneAt = -1;
    private boolean done, started, flyerSeen;
    private WorldServer w;
    private long seed;
    private SettlementRegistrySC reg;
    private EmbassySiteSC.Site site;
    private EmbassyLayoutSC.Plan plan;
    private int[] decided;
    private SettlementRegistrySC.Settlement rec;
    private EmbassyAirfieldSC.Field field;
    private FakePlayer fake;
    /** The embassies' records made before this test (none of them may have an "X" - AUTO without the add-on), and those that had one. */
    private final List<Integer> before = new ArrayList<Integer>();
    private final Set<Integer> beforeHadX = new java.util.HashSet<Integer>();
    private int modeWas = -1;
    private final List<Object[]> steps = new ArrayList<Object[]>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final Set<String> notes = new TreeSet<String>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START) {
            return;
        }
        try {
            if (!started) {
                if (!ready() && ticks < START + WAIT_MAX) {
                    return;
                }
                started = true;
                startAt = ticks;
                if (!ready()) {
                    System.out.println("[SC-WORLDTEST] INFO airfield: the embassy tests never finished - beginning anyway");
                }
                begin();
            }
            while (!done && next < steps.size() && ticks - startAt >= ((Integer) steps.get(next)[0]).intValue()) {
                Object[] s = steps.get(next++);
                try {
                    ((Runnable) s[2]).run();
                } catch (Throwable t) {
                    print(false, s[1] + " - exception " + t);
                    t.printStackTrace();
                }
            }
            if (!done && (next >= steps.size() || ticks - startAt > TIMEOUT)) {
                if (next < steps.size()) {
                    print(false, "timed out before " + steps.get(next)[1]);
                }
                finish();
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    /** WorldTestEmbassySC finished and the fly-through after it (its "finished" if it has one; else its flyer seen and gone, or never seen). */
    private boolean ready() {
        if (!WorldTestEmbassySC.finished) {
            return false;
        }
        if (!WorldTestEmbassyFlySC.registered) {
            return true;
        }
        Boolean own = flyFinished();
        if (own != null) {
            return own.booleanValue();
        }
        if (embassyDoneAt < 0) {
            embassyDoneAt = ticks;
        }
        WorldServer ow = MinecraftServer.getServer().worldServerForDimension(0);
        boolean flying = false;
        UUID flyer = flyerId();
        for (Object o : ow == null ? Collections.emptyList() : ow.playerEntities) {
            flying |= o instanceof FakePlayer && flyer.equals(((FakePlayer) o).getUniqueID());
        }
        flyerSeen |= flying;
        return flyerSeen ? !flying : ticks - embassyDoneAt > FLY_SEEN;
    }

    /** WorldTestEmbassyFlySC's static "finished" (null: it has none). */
    private static Boolean flyFinished() {
        try {
            Field f = WorldTestEmbassyFlySC.class.getDeclaredField("finished");
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == boolean.class) {
                f.setAccessible(true);
                return Boolean.valueOf(f.getBoolean(null));
            }
        } catch (Throwable t) {
            // none: the flyer is watched instead
        }
        return null;
    }

    private static UUID flyerId() {
        try {
            Field f = WorldTestEmbassyFlySC.class.getDeclaredField("FLYER");
            f.setAccessible(true);
            return ((GameProfile) f.get(null)).getId();
        } catch (Throwable t) {
            return FLYER;
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void begin() {
        w = MinecraftServer.getServer().worldServerForDimension(EmbassySiteSC.DIM);
        try {
            registration();
        } catch (Throwable t) {
            print(false, "registration - exception " + t);
        }
        if (w == null || !SettlementSC.embassiesGenerate() || !EmbassySiteSC.worldFits(w)) {
            System.out.println("[SC-WORLDTEST] SKIP airfield: the embassies don't generate here (embassies " + SubstrateConfigSC.embassies
                    + ", the Overworld fits " + (w != null && EmbassySiteSC.worldFits(w)) + ")");
            finish();
            return;
        }
        seed = w.getSeed();
        reg = SettlementRegistrySC.get(w);
        fake = FakePlayerFactory.get(w, FAKE);
        for (SettlementRegistrySC.Settlement s : reg.embassies()) {
            before.add(Integer.valueOf(s.id));
            if (EmbassyAirfieldSC.decisionOf(s) != null) {
                beforeHadX.add(Integer.valueOf(s.id));
            }
        }
        step(0, "old records", new Runnable() {
            public void run() {
                oldRecords();
            }
        });
        step(0, "generation", new Runnable() {
            public void run() {
                generation();
            }
        });
        step(T_CHECKS, "built", new Runnable() {
            public void run() {
                built();
            }
        });
        step(T_CHECKS, "protection", new Runnable() {
            public void run() {
                protection();
            }
        });
        step(T_CHECKS, "registry", new Runnable() {
            public void run() {
                registry();
            }
        });
        step(T_CHECKS, "API", new Runnable() {
            public void run() {
                api();
            }
        });
        step(T_CHECKS, "services", new Runnable() {
            public void run() {
                services();
            }
        });
        step(T_CHECKS, "air defence", new Runnable() {
            public void run() {
                airDefence();
            }
        });
        step(T_CHECKS, "column (first clicks)", new Runnable() {
            public void run() {
                columnQuote();
            }
        });
        step(T_CHECKS + T_CLICK, "column (p1's second click)", new Runnable() {
            public void run() {
                columnFirstPays();
            }
        });
        step(T_CHECKS + 2 * T_CLICK, "column (p2's second click)", new Runnable() {
            public void run() {
                columnSecondPays();
            }
        });
        step(T_CHECKS, "tab", new Runnable() {
            public void run() {
                tab();
            }
        });
        step(T_CHECKS, "command", new Runnable() {
            public void run() {
                command();
            }
        });
        step(T_REPAIR, "repair", new Runnable() {
            public void run() {
                repair();
            }
        });
        step(T_END, "untouched", new Runnable() {
            public void run() {
                untouched();
            }
        });
    }

    private void step(int at, String name, Runnable r) {
        steps.add(new Object[]{Integer.valueOf(at), name, r});
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (modeWas >= 0) {
            SubstrateConfigSC.setEmbassyAirfield(modeWas);                 // never left ALWAYS (an exception on the way)
        }
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        if (!notes.isEmpty()) {
            System.out.println("[SC-WORLDTEST] INFO airfield: " + notes);
        }
        w = null;
        System.out.println("[SC-WORLDTEST] DONE airfield");
        finished = true;
    }

    // ------------------------------------------------------------------ 1. registration (§2.2)

    @SuppressWarnings("unchecked")
    private void registration() {
        StringBuilder bad = new StringBuilder();
        Object b = Block.blockRegistry.getObject("SiliconAge:embassyAirfieldSC");
        if (b == null || b == Blocks.air || b != EmbassyBlocksSC.airfield) {
            bad.append(" block embassyAirfieldSC");
        }
        Set<String> names = new TreeSet<String>();
        for (Field f : MapGenStructureIO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) f.get(null)).entrySet()) {
                    for (Object o : new Object[]{e.getKey(), e.getValue()}) {
                        if (o instanceof String && ((String) o).startsWith(EmbassySC.PIECE_PREFIX)) {
                            names.add((String) o);
                        }
                    }
                }
            } catch (Exception ex) {
                bad.append(" MapGenStructureIO ").append(f.getName());
            }
        }
        int air = 0;
        for (int i = EmbassyPiecesSC.NAMES.length - 7; i < EmbassyPiecesSC.NAMES.length; i++) {
            if (names.contains(EmbassySC.PIECE_PREFIX + EmbassyPiecesSC.NAMES[i])) {
                air++;
            } else {
                bad.append(" piece ").append(EmbassyPiecesSC.NAMES[i]);
            }
        }
        print(bad.length() == 0, "registration (§2.2): SiliconAge:embassyAirfieldSC, the airfield's " + air + " pieces (7) of " + EmbassyPiecesSC.NAMES.length
                + " in MapGenStructureIO, the showcase metas up to " + (BlockShowcase2SC.NAMES.length - 1) + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 2. the old records (ЛП-19) and AUTO without the add-on

    private void oldRecords() {
        int mode = SubstrateConfigSC.embassyAirfield();
        boolean addon = Loader.isModLoaded(EmbassyAirfieldSC.ADDON);
        int withX = 0, ours = 0;
        StringBuilder which = new StringBuilder();
        for (SettlementRegistrySC.Settlement s : reg.embassies()) {
            if (s.extra.getBoolean(TEST_MARK)) {
                ours++;                                                 // an earlier run's own (ALWAYS): not the generator's
            } else if (EmbassyAirfieldSC.decisionOf(s) != null) {
                withX++;
                which.append(' ').append(s.id);
            }
        }
        if (mode == SubstrateConfigSC.AIRFIELD_AUTO && !addon) {
            print(withX == 0, "AUTO without the add-on (ЛП-1): the " + before.size() + " embassies made before this test have no \"X\" (no field) - "
                    + withX + " with one" + which + " (an earlier run's own test embassies left out: " + ours + ")");
        } else {
            System.out.println("[SC-WORLDTEST] INFO airfield: embassy.airfield " + mode + ", the add-on " + (addon ? "loaded" : "absent") + " - "
                    + withX + " of the " + before.size() + " earlier embassies have an \"X\"" + which);
        }
    }

    // ------------------------------------------------------------------ 3. the generation (ЛП-1, ЛП-2, ЛП-5): a FULL embassy on new land

    private void generation() {
        modeWas = SubstrateConfigSC.embassyAirfield();
        SubstrateConfigSC.setEmbassyAirfield(SubstrateConfigSC.AIRFIELD_ALWAYS);
        try {
            generate();
        } finally {
            SubstrateConfigSC.setEmbassyAirfield(modeWas);
            boolean back = SubstrateConfigSC.embassyAirfield() == modeWas;
            modeWas = -1;
            if (!back) {
                print(false, "the mode embassy.airfield not set back");
            }
        }
    }

    private void generate() {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        int fresh = 0;
        String kinds = "";
        for (EmbassySiteSC.Site s : freshSites(sx, sz)) {
            fresh++;
            int[] d = EmbassyAirfieldSC.decideFor(w, s);              // ALWAYS: the land's own answer (package G's choose)
            kinds += (kinds.isEmpty() ? "" : " ") + (d == null ? "null" : EmbassyAirfieldSC.KIND_KEYS[Math.max(0, Math.min(3, d[0]))]);
            if (d != null && d[0] == EmbassyAirfieldSC.FULL) {
                site = s;
                decided = d;
                // the funnels' chunks on disk before the start (made earlier: their land and trees are as they were - the review's check)
                EmbassyAirfieldSC.Field ff = com.sc.embassy.EmbassyAirSiteSC.fullField(s, d[1]);
                for (int e = 0; e < funnelOnDisk.length; e++) {
                    funnelOnDisk[e] = com.sc.embassy.EmbassyAirSiteSC.funnelOnDisk(w, ff, e);
                }
                break;
            }
        }
        if (site == null) {
            System.out.println("[SC-WORLDTEST] SKIP airfield: no fresh embassy site with a FULL field within " + RINGS + " rings of regions round the spawn ("
                    + fresh + " fresh sites: " + kinds + ")");
            return;
        }
        plan = EmbassyLayoutSC.plan(seed, site, site.floorY, decided);
        EmbassyAirfieldSC.Field f = plan.field;
        int[] box = chunksOf(f, EmbassyLayoutSC.footprintChunks(plan));
        WorldTestEmbassySC.Watch watch = new WorldTestEmbassySC.Watch(w, box);
        // a FakePlayer teleported into the middle of the future field: its chunk is the first one made
        int mx = f.x(0, f.axis), mz = f.z(0, f.axis);
        fake.setLocationAndAngles(mx + 0.5D, site.floorY + 1, mz + 0.5D, 0F, 0F);
        MinecraftForge.EVENT_BUS.register(watch);
        StructureEmbassyStartSC first;
        try {
            w.getChunkFromBlockCoords(mx, mz);
            MapGenEmbassySC gen = EmbassySC.mapGen(w);
            first = gen == null ? null : gen.startAt(w, site.chunkX(), site.chunkZ());
            for (int x = box[0] - 1; x <= box[2] + 1; x++) {
                for (int z = box[1] - 1; z <= box[3] + 1; z++) {
                    w.getChunkFromChunkCoords(x, z);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int unpopulated = 0;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                unpopulated += w.getChunkFromChunkCoords(x, z).isTerrainPopulated ? 0 : 1;
            }
        }
        force(box);
        rec = reg.atCentre(EmbassySiteSC.DIM, site.x, site.z);
        if (rec != null) {
            rec.extra.setBoolean(TEST_MARK, true);                    // this test's own (ALWAYS): a later run's AUTO check leaves it out
            reg.markDirty();
        }
        field = EmbassyAirfieldSC.fieldOf(rec);
        int[] got = EmbassyAirfieldSC.decisionOf(rec), start = rec == null ? null : EmbassyAirfieldSC.decisionOfStart(w, rec.x, rec.z);
        boolean made = first != null && first.isSizeableStructure() && rec != null && first.settlementId() == rec.id;
        boolean x = Arrays.equals(got, decided) && field != null && field.kind == EmbassyAirfieldSC.FULL && Arrays.equals(start, decided);
        print(made && x && unpopulated == 0 && watch.ours == 0, "generation (ЛП-1, ЛП-2, ЛП-5) at " + site + " (" + fresh + " fresh sites asked: " + kinds
                + "): a FakePlayer at the middle of the future runway " + mx + " " + mz + " - its chunk made first, the start made with it, not empty, the"
                + " record's " + made + " (" + (first == null ? 0 : first.getComponents().size()) + " pieces); the record's \"X\" and the ground piece's"
                + " \"Air\" " + Arrays.toString(got) + " / " + Arrays.toString(start) + " = the decision " + Arrays.toString(decided) + " " + x + "; "
                + ((box[2] - box[0] + 3) * (box[3] - box[1] + 3)) + " chunks asked, " + watch.loaded.size() + " loaded, " + watch.ours + " past them by"
                + " this mod's code (0 - no cascade), " + watch.foreign + " by another mod's or vanilla's; not populated " + unpopulated + " (0)"
                + (watch.past.length() == 0 ? "" : "; loaded past them:" + watch.past));
    }

    /** {minCX, minCZ, maxCX, maxCZ}: the plan's footprint, the field's zone and its funnels. */
    private static int[] chunksOf(EmbassyAirfieldSC.Field f, int[] footprint) {
        int[] b = footprint.clone();
        List<int[]> boxes = new ArrayList<int[]>();
        boxes.add(f.zoneBox());
        for (int[] q : f.funnels) {
            boxes.add(f.world(q));
        }
        for (int[] w : boxes) {
            b[0] = Math.min(b[0], w[0] >> 4);
            b[1] = Math.min(b[1], w[1] >> 4);
            b[2] = Math.max(b[2], w[2] >> 4);
            b[3] = Math.max(b[3], w[3] >> 4);
        }
        return b;
    }

    /**
     * The fresh sites (no start, no record, the land not explored, no zone near) of the nearest rings of regions round the spawn's, SITES of
     * them at most, out to RINGS - nearest first (WorldTestEmbassyFlySC's way; the earlier tests used the near ones up).
     */
    private List<EmbassySiteSC.Site> freshSites(final int sx, final int sz) {
        int g = EmbassySiteSC.grid(), rx0 = EmbassySiteSC.region(sx, g), rz0 = EmbassySiteSC.region(sz, g);
        List<EmbassySiteSC.Site> out = new ArrayList<EmbassySiteSC.Site>();
        MapGenEmbassySC gen = EmbassySC.mapGen(w);
        for (int r = 0; r <= RINGS && out.size() < SITES; r++) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) {
                        continue;
                    }
                    EmbassySiteSC.Site s = EmbassySiteSC.site(w, rx0 + i, rz0 + j);
                    if (s != null && !s.made && (gen == null || gen.startAt(w, s.chunkX(), s.chunkZ()) == null) && reg.atCentre(EmbassySiteSC.DIM, s.x, s.z) == null
                            && !EmbassySiteSC.explored(w, s) && !EmbassySiteSC.zoneNear(w, s)) {
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

    private void force(int[] box) {
        ForgeChunkManager.Ticket t = null;
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        return;
                    }
                    t.getModData().setString("Kind", "worldtest");          // ChunkLoaderSC releases it on a later load
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(x, z));
            }
        }
    }

    private boolean none(String what) {
        if (rec == null || field == null) {
            System.out.println("[SC-WORLDTEST] SKIP airfield: " + what + " - no airfield embassy made");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 4. what the pieces built (package P, ЛП-3, ЛП-5), the posts (G, ЛП-18)

    private void built() {
        if (none("the built field")) {
            return;
        }
        EmbassyAirfieldSC.Field f = field;
        Block af = EmbassyBlocksSC.airfield, show = SettlementBlocksSC.showcase2;
        int y = f.floorY, runway = 0, runwayBad = 0, paved = 0, pavedBad = 0;
        for (int a = f.runway[0]; a <= f.runway[2]; a++) {
            for (int q = f.strip[1]; q <= f.strip[3]; q++) {
                int x = f.x(a, q), z = f.z(a, q);
                boolean on = w.getBlock(x, y, z) == af;
                if (EmbassyAirfieldSC.in(f.runway, a, q)) {
                    runway++;
                    runwayBad += on && BlockAirfieldSC.runway(w.getBlockMetadata(x, y, z)) ? 0 : 1;
                } else {
                    paved++;
                    pavedBad += on ? 0 : 1;                                // the shoulders
                }
            }
        }
        for (int[] r : new int[][]{f.padLow, f.padHigh, f.taxi, f.apron, f.helipad}) {
            for (int a = r[0]; a <= r[2]; a++) {
                for (int q = r[1]; q <= r[3]; q++) {
                    if (EmbassyAirfieldSC.in(f.monument, a, q)) {
                        continue;                                          // the monument stands on the apron: its own blocks
                    }
                    paved++;
                    pavedBad += w.getBlock(f.x(a, q), y, f.z(a, q)) == af ? 0 : 1;
                }
            }
        }
        // the lights exactly at Field.lights
        int lights = 0, lightsBad = 0;
        for (int meta : new int[]{BlockAirfieldSC.LIGHT_EDGE, BlockAirfieldSC.LIGHT_THRESHOLD, BlockAirfieldSC.LIGHT_END, BlockAirfieldSC.LIGHT_TAXI}) {
            for (int[] c : f.lights(meta)) {
                lights++;
                int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
                lightsBad += w.getBlock(x, y, z) == af && w.getBlockMetadata(x, y, z) == meta ? 0 : 1;
            }
        }
        // the fixed blocks: the columns, the desk, the beacon, the guns
        int fixed = 0, fixedBad = 0;
        StringBuilder bad = new StringBuilder();
        List<int[]> blocks = new ArrayList<int[]>();
        for (int i = 0; i < f.columns.length; i++) {
            blocks.add(with(f.columnBlock(i), BlockShowcase2SC.SERVICE_COLUMN));
        }
        for (int i = 0; i < f.aaPosts.length; i++) {
            blocks.add(with(f.aaBlock(i), BlockShowcase2SC.AA_POST));
        }
        blocks.add(with(f.towerDesk(), BlockShowcase2SC.TOWER_CONSOLE));
        blocks.add(with(f.beaconBlock(), BlockShowcase2SC.AIRFIELD_BEACON));
        for (int[] b : blocks) {
            fixed++;
            if (w.getBlock(b[0], b[1], b[2]) != show || w.getBlockMetadata(b[0], b[1], b[2]) != b[3]) {
                fixedBad++;
                if (bad.length() < 300) {
                    bad.append(' ').append(BlockShowcase2SC.nameOf(b[3])).append('@').append(b[0]).append(',').append(b[1]).append(',').append(b[2]);
                }
            }
        }
        // no log nor plank in the field's buildings (§12 п. 5)
        int wood = 0;
        for (int[] r : new int[][]{f.terminal, f.tower, f.hangar, f.monument}) {
            for (int a = r[0]; a <= r[2]; a++) {
                for (int q = r[1]; q <= r[3]; q++) {
                    for (int dy = 0; dy <= EmbassyAirfieldSC.BEACON_DY; dy++) {
                        Block b = w.getBlock(f.x(a, q), y + dy, f.z(a, q));
                        wood += b == Blocks.log || b == Blocks.log2 || b == Blocks.planks || b == Blocks.wooden_slab || b == Blocks.double_wooden_slab ? 1 : 0;
                    }
                }
            }
        }
        // no snow layer can settle on the runway (ЛП-4: not an opaque cube)
        int snowy = 0, cold = 0;
        for (int a = -90; a <= 90; a += 30) {
            int x = f.x(a, f.axis), z = f.z(a, f.axis);
            snowy += Blocks.snow_layer.canPlaceBlockAt(w, x, y + 1, z) ? 1 : 0;
            cold += w.func_147478_e(x, y + 1, z, false) ? 1 : 0;
        }
        print(runwayBad == 0 && pavedBad == 0 && lightsBad == 0 && fixedBad == 0 && wood == 0 && snowy == 0, "built field (package P, ЛП-3..ЛП-5): the"
                + " runway's " + runway + " cells paved with its runway metas - " + runwayBad + " not; the shoulders, pads, taxiway, apron and helipad "
                + paved + " - " + pavedBad + " not; the lights at Field.lights " + lights + " - " + lightsBad + " not; the columns, guns, desk and beacon "
                + fixed + " - " + fixedBad + " not" + bad + "; logs / planks in the buildings " + wood + " (0); a snow layer could settle on " + snowy
                + " of 7 runway cells (0; the biome would snow on " + cold + ")");
        // ЛП-18 (package G): the Sentries #5, #6 and the second Gas Engineer posted exactly on Field.posts
        int posted = 0;
        StringBuilder off = new StringBuilder();
        for (int i = 0; f.posts != null && i < f.posts.length; i++) {
            int px = f.x(f.posts[i][0], f.posts[i][1]), pz = f.z(f.posts[i][0], f.posts[i][1]);
            NpcKindSC want = i < 2 ? NpcKindSC.SENTRY : NpcKindSC.GAS_TECH;
            boolean found = false;
            for (SettlementRegistrySC.Slot sl : rec.slots) {
                found |= sl.kind == want.ordinal() && sl.postX == px && sl.postZ == pz;
            }
            posted += found ? 1 : 0;
            if (!found) {
                off.append(' ').append(want.key).append('@').append(px).append(',').append(pz);
            }
        }
        print(f.posts != null && posted == f.posts.length, "posts on the field (package G, ЛП-18): " + posted + " of " + (f.posts == null ? 0 : f.posts.length)
                + " (two Sentries, the second Gas Engineer) on Field.posts" + off);
        // ЛП-3, §4.4 (the integrator's): the approach funnels - no growth over the 1:FUNNEL_SLOPE plane (the Funnel piece), no vanilla tree
        // in them at all (covers(): a piece of the start meets the column - piecesMeet; an end with an ILS of version 2 out to FUNNEL_ILS_OUT
        // past its threshold, FUNNEL_REACH along the street, an end without one to FUNNEL_A; Decorate's TREE / BIG_SHROOM banned there)
        int over = 0, vanilla = 0, cols = 0;
        int[] vanillaOf = new int[f.funnels.length];
        for (int v = 0; v < f.funnels.length; v++) {
            int[] r = f.funnels[v];
            for (int a = r[0]; a <= r[2]; a++) {
                int dist = v == 0 ? f.thresholdLow - a : a - f.thresholdHigh;
                int y0 = y + Math.max(0, dist) / EmbassyAirfieldSC.FUNNEL_SLOPE + 1;
                for (int q = r[1]; q <= r[3]; q++) {
                    int x = f.x(a, q), z = f.z(a, q);
                    cols++;
                    for (int yy = y - 8; yy <= y + 64; yy++) {
                        Block b = w.getBlock(x, yy, z);
                        boolean tree = b == Blocks.log || b == Blocks.log2 || b == Blocks.leaves || b == Blocks.leaves2;
                        vanillaOf[v] += tree ? 1 : 0;
                        vanilla += tree && (v >= funnelOnDisk.length || !funnelOnDisk[v]) ? 1 : 0;
                        over += yy >= y0 && (tree || b.isWood(w, x, yy, z) || b.isLeaves(w, x, yy, z) || b == Blocks.vine
                                || b == Blocks.brown_mushroom_block || b == Blocks.red_mushroom_block) ? 1 : 0;
                    }
                }
            }
        }
        print(f.funnels.length == 2 && over == 0 && vanilla == 0, "approach funnels (ЛП-3, §4.4): " + f.funnels.length + " funnels, " + cols + " columns - growth"
                + " over the 1:" + EmbassyAirfieldSC.FUNNEL_SLOPE + " plane " + over + " (0), vanilla logs / leaves in the funnels made with the start "
                + vanilla + " (0: covers() - the start's pieces - bans Decorate's trees in the funnels: " + EmbassyAirfieldSC.FUNNEL_A + " without an ILS, "
                + EmbassyAirfieldSC.FUNNEL_ILS_OUT + " past the threshold with one, " + EmbassyAirfieldSC.FUNNEL_REACH + " along the street at most); by funnel "
                + Arrays.toString(vanillaOf)
                + ", on disk before the start " + Arrays.toString(funnelOnDisk) + " (their land as it was), the ILS bits " + f.ils);
        // ЛП-18 (the integrator's, the review's): the field's Sentries keep their posts day and night, their OWN leash reaches them (the rest of
        // the guard keeps the belt's); the census's reach (RosterSC.reach) of every slot holds its post - a guard's its leash, a Tuner's post
        // out past the dome (the second Gas Engineer's by the columns) OUTPOST_HOME past it; an old record's #5 / #6 still patrol, every
        // leash and reach of it the belt's
        int sentries = 0, onDuty = 0, leashShort = 0, othersChanged = 0, reachShort = 0, beltLeash = ViolationRulesSC.leash(rec.radius);
        int belt = rec.radius + SettlementSC.BELT, widest = belt;
        double far = 0D;
        for (int i = 0; i < rec.slots.size(); i++) {
            SettlementRegistrySC.Slot sl = rec.slots.get(i);
            double d = Math.sqrt(rec.distSq(sl.postX + 0.5D, sl.postZ + 0.5D));
            int reach = RosterSC.reach(rec, i), leash = NpcLinkSC.leash(rec, i);
            boolean guard = sl.kindOf().guard, out = !guard && d > rec.radius - NpcLinkSC.TUNER_HOME_INSET;
            reachShort += reach < belt || reach < (guard ? leash : d + (out ? NpcLinkSC.OUTPOST_HOME : 0)) ? 1 : 0;
            widest = Math.max(widest, reach);
            if (!NpcLinkSC.fieldSentry(rec, i)) {
                othersChanged += guard && leash != beltLeash ? 1 : 0;
                continue;
            }
            sentries++;
            int[] p = EntityWardenSC.dutyPoint(rec, i, EntityWardenSC.ROLE_POST);
            onDuty += EntityWardenSC.role(rec, i, false) == EntityWardenSC.ROLE_POST && EntityWardenSC.role(rec, i, true) == EntityWardenSC.ROLE_POST
                    && p != null && p[0] == sl.postX && p[2] == sl.postZ ? 1 : 0;
            leashShort += leash < d + NpcLinkSC.FIELD_LEASH - 1 ? 1 : 0;
            far = Math.max(far, d);
        }
        int oldSentries = 0, oldPatrol = 0, oldChanged = 0;
        for (Integer id : before) {
            SettlementRegistrySC.Settlement s = reg.byId(id.intValue());
            if (s == null || EmbassyAirfieldSC.fieldOf(s) != null) {
                continue;
            }
            for (int i = 0; i < s.slots.size(); i++) {
                oldChanged += s.slots.get(i).kindOf().guard && NpcLinkSC.leash(s, i) != ViolationRulesSC.leash(s.radius)
                        || RosterSC.reach(s, i) != s.radius + SettlementSC.BELT ? 1 : 0;
                if (s.slots.get(i).kind == NpcKindSC.SENTRY.ordinal() && EmbassyQuartersSC.fieldPost(NpcKindSC.SENTRY, EntityWardenSC.rankIndex(s, i)) >= 0) {
                    oldSentries++;
                    oldPatrol += EntityWardenSC.role(s, i, false) == EntityWardenSC.ROLE_PATROL ? 1 : 0;
                }
            }
        }
        print(sentries == 2 && onDuty == 2 && leashShort == 0 && othersChanged == 0 && reachShort == 0 && oldPatrol == oldSentries && oldChanged == 0,
                "the field's Sentries' duty (ЛП-18): " + onDuty + " of " + sentries + " (2) at their posts day and night (ROLE_POST, the duty point"
                + " their post), their own leash short of the post + " + NpcLinkSC.FIELD_LEASH + " " + leashShort + " (0; the farthest post "
                + Math.round(far) + "), the other guards' leash not the belt's " + beltLeash + " - " + othersChanged + " (0); the census's reach short of"
                + " a slot's post / leash / outpost home " + reachShort + " (0; the widest " + widest + ", the belt " + belt + "); the earlier records"
                + " without a field: Sentries #5 / #6 patrolling " + oldPatrol + " of " + oldSentries + ", a leash or reach not the belt's " + oldChanged + " (0)");
    }

    private static int[] with(int[] b, int meta) {
        return b == null ? new int[]{0, -1, 0, meta} : new int[]{b[0], b[1], b[2], meta};
    }

    // ------------------------------------------------------------------ 5. the protection (package R, ЛП-6)

    private void protection() {
        if (none("the protection")) {
            return;
        }
        EmbassyAirfieldSC.Field f = field;
        int y = f.floorY;
        int rx = f.x(40, f.axis), rz = f.z(40, f.axis);
        int ax = f.x((f.apron[0] + f.apron[2]) / 2, (f.apron[1] + f.apron[3]) / 2), az = f.z((f.apron[0] + f.apron[2]) / 2, (f.apron[1] + f.apron[3]) / 2);
        fake.setLocationAndAngles(rx + 0.5D, y + 1, rz + 0.5D, 0F, 0F);
        boolean protect = SettlementSC.airfieldProtecting(w, rx + 0.5D, y, rz + 0.5D) == rec && SettlementSC.airfieldProtecting(w, rx + 0.5D, y - 7, rz + 0.5D) == null;
        boolean breakOff = MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(rx, y, rz, w, w.getBlock(rx, y, rz), w.getBlockMetadata(rx, y, rz), fake));
        boolean placeOff = MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(BlockSnapshot.getBlockSnapshot(w, ax, y + 1, az), w.getBlock(ax, y, az), fake));
        // a blast on the apron: a stone block set there past the events stays (the zone's blocks are struck off the explosion)
        Block was = w.getBlock(ax, y + 1, az);
        int wasMeta = w.getBlockMetadata(ax, y + 1, az);
        w.setBlock(ax, y + 1, az, Blocks.stone, 0, 2);
        w.newExplosion(null, ax + 1.5D, y + 1.5D, az + 0.5D, 3F, false, true);
        boolean blast = w.getBlock(ax, y + 1, az) == Blocks.stone;
        w.setBlock(ax, y + 1, az, was, wasMeta, 2);
        // no hostile spawn on the runway; no bridge end there
        EntityZombie zombie = new EntityZombie(w);
        zombie.setLocationAndAngles(rx + 0.5D, y + 1, rz + 0.5D, 0F, 0F);
        LivingSpawnEvent.CheckSpawn spawn = new LivingSpawnEvent.CheckSpawn(zombie, w, rx + 0.5F, y + 1, rz + 0.5F);
        MinecraftForge.EVENT_BUS.post(spawn);
        boolean denied = spawn.getResult() == Event.Result.DENY;
        boolean bridge = SettlementSC.refusesBridgeEnd(w, rx, y + 1, rz);
        // the column's right click passes (its onBlockActivated serves it)
        int[] c = f.columnBlock(0);
        PlayerInteractEvent click = new PlayerInteractEvent(fake, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, c[0], c[1], c[2], 1, w);
        MinecraftForge.EVENT_BUS.post(click);
        boolean column = !click.isCanceled() && click.useBlock != Event.Result.DENY;
        print(protect && breakOff && placeOff && blast && denied && bridge && column, "protection (package R, ЛП-6): the zone protects from FOUNDATION"
                + " under the floor " + protect + "; breaking the runway refused " + breakOff + ", placing on the apron refused " + placeOff + ", a blast leaves"
                + " the apron's blocks " + blast + ", a zombie's CheckSpawn on the runway DENY " + denied + ", a bridge end refused there " + bridge
                + "; the column's right click passes " + column);
    }

    // ------------------------------------------------------------------ 6. the registry (§0.1)

    private void registry() {
        if (none("the registry")) {
            return;
        }
        EmbassyAirfieldSC.Field f = field;
        int[] reach = rec.reach(), zone = f.zoneBox();
        boolean covers = reach[0] <= zone[0] && reach[1] <= zone[1] && reach[2] >= zone[2] && reach[3] >= zone[3];
        int rx = f.x(60, f.axis), rz = f.z(60, f.axis), px = f.x(f.endHigh + 40, f.axis), pz = f.z(f.endHigh + 40, f.axis);
        boolean at = SettlementSC.airfieldAt(w, rx + 0.5D, rz + 0.5D) == rec && SettlementSC.airfieldAt(w, px + 0.5D, pz + 0.5D) == null
                && SettlementSC.embassyGroundAt(w, rx + 0.5D, rz + 0.5D) == rec && rec.inBelt(rx + 0.5D, rz + 0.5D);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(rx - 4, f.floorY, rz - 4, rx + 5, f.floorY + 10, rz + 5);
        boolean overlap = reg.overlapping(EmbassySiteSC.DIM, box) == rec;
        print(covers && at && overlap, "registry (§0.1): reach() covers the zone " + Arrays.toString(zone) + " " + covers + "; airfieldAt / embassyGroundAt"
                + " / inBelt on the runway, none past the far end " + at + "; a player's field box over the runway overlaps the embassy " + overlap);
    }

    // ------------------------------------------------------------------ 7. the API (§5)

    private void api() {
        if (none("the API")) {
            return;
        }
        EmbassyAirfieldSC.Field f = field;
        boolean listed = false;
        for (AirfieldInfoSC i : EmbassyAirApiSC.airfields(w)) {
            listed |= i.id == rec.id && i.kind == EmbassyAirfieldSC.FULL;
        }
        AirfieldInfoSC info = EmbassyAirApiSC.airfield(w, rec.id);
        int rx = f.x(60, f.axis), rz = f.z(60, f.axis);
        AirfieldInfoSC at = EmbassyAirApiSC.airfieldAt(w, rx, rz);
        boolean where = info != null && at != null && at.id == rec.id && Arrays.equals(info.runwayBox(), f.world(f.runway))
                && EmbassyAirApiSC.domeAt(w, rec.x + 0.5D, rec.z + 0.5D) == rec.id && EmbassyAirApiSC.domeAt(w, rx + 0.5D, rz + 0.5D) == -1
                && EmbassyAirApiSC.neutralAt(w, rx + 0.5D, 200D, rz + 0.5D) && !EmbassyAirApiSC.neutralAt(w, rec.x + 5000.5D, 90D, rec.z + 0.5D)
                && EmbassyAirApiSC.adZoneAt(w, rec.x + 100.5D, rec.z + 0.5D) == rec.id && EmbassyAirApiSC.revision(w) > 0
                && EmbassyAirApiSC.isRunwaySurface(w.getBlock(rx, f.floorY, rz), w.getBlockMetadata(rx, f.floorY, rz))
                && !EmbassyAirApiSC.isRunwaySurface(w.getBlock(rx, f.floorY + 1, rz), w.getBlockMetadata(rx, f.floorY + 1, rz));
        String icao = info == null ? "-" : info.icao + " " + info.numberLow + "/" + info.numberHigh;
        // ЛП-1: the guns fire over the square (fireHalf) and the corridors only - 200 off the core is the warning ring in every direction,
        // the fire zone in none but an approach corridor's (at least one of the four is off the corridors)
        int ringWarn = 0, ringQuiet = 0;
        StringBuilder ringAt = new StringBuilder();
        for (int[] d : new int[][]{{200, 0}, {-200, 0}, {0, 200}, {0, -200}}) {
            double qx = rec.x + d[0] + 0.5D, qz = rec.z + d[1] + 0.5D;
            int warnId = EmbassyAirApiSC.adWarnZoneAt(w, qx, qz), fireId = EmbassyAirApiSC.adZoneAt(w, qx, qz);
            ringWarn += warnId == rec.id ? 1 : 0;
            ringQuiet += fireId == -1 ? 1 : 0;
            ringAt.append(' ').append(d[0]).append('/').append(d[1]).append(":warn ").append(warnId).append(",fire ").append(fireId);
        }
        boolean ring = ringWarn == 4 && ringQuiet >= 1;
        where &= ring;
        icao += "; the ring at 200 (fireHalf " + f.fireHalf() + "):" + ringAt + " - warned 4/4, quiet >= 1 " + ring;
        // a FakePlayer is never served
        boolean fakeOff = EmbassyAirApiSC.quote(fake, rec.id, "charge", 1, false) == -EmbassyAirApiSC.PAY_NO_FIELD
                && EmbassyAirApiSC.pay(fake, rec.id, "charge", 1, false) == EmbassyAirApiSC.PAY_NO_FIELD && !EmbassyAirApiSC.firstLanding(fake, rec.id)
                && EmbassyAirApiSC.requestPass(fake, rec.id) == EmbassyAirApiSC.PASS_NO_FIELD && !EmbassyAirApiSC.admitted(fake, rec.id);
        print(listed && where && fakeOff, "API (§5): airfields() lists it FULL " + listed + "; airfield / airfieldAt / domeAt / neutralAt (at 200 over"
                + " the runway; not far away) / adZoneAt / revision / isRunwaySurface of the runway's block (not of the air over it) " + where + " (" + icao
                + "); a FakePlayer is never served - no quote, no pay, no logbook, no pass, no clearance " + fakeOff);
    }

    // ------------------------------------------------------------------ 8. the services (package R, ЛП-10..ЛП-12, ЛП-16)

    private void services() {
        if (none("the services")) {
            return;
        }
        WorldTestArmourerSC.StationPlayer p = station("pilot", field.columnBlock(0));
        SettlementRegistrySC.PlayerRec pr = dealer(p, 0);
        int id = rec.id;
        // «Чужак»: charging is «Знакомый»'s - PAY_LEVEL; an unknown service - PAY_UNKNOWN
        int stranger = EmbassyAirApiSC.quote(p, id, "charge", 2000000, false), unknown = EmbassyAirApiSC.quote(p, id, "nope", 1, false);
        // «Знакомый» without chips - PAY_CHIPS; with them a price, paid, the day's amount counted, the chips taken
        pr.reputation = 150;
        int poor = EmbassyAirApiSC.quote(p, id, "charge", 2000000, false);
        chips(p, 64);
        int price = EmbassyAirApiSC.quote(p, id, "charge", 2000000, false);
        int paid = EmbassyAirApiSC.pay(p, id, "charge", 2000000, false);
        int left = held(p);
        SettlementRegistrySC.LocalRec lr = rec.localIfAny(p.getUniqueID());
        int counted = lr == null ? 0 : lr.count(EmbassyAirRulesSC.COUNT_PREFIX + "charge");
        int over = EmbassyAirApiSC.pay(p, id, "charge", EmbassyAirRulesSC.PER_SERVICE[EmbassyAirRulesSC.S_CHARGE] + 1, false);
        int free = EmbassyAirApiSC.quote(p, id, "chart", 1, true);
        // «Враг»: refused before all
        rec.local(p.getUniqueID()).enemyUntil = w.getTotalWorldTime() + 1000L;
        int enemy = EmbassyAirApiSC.quote(p, id, "chart", 1, false);
        rec.local(p.getUniqueID()).enemyUntil = 0L;
        boolean codes = stranger == -EmbassyAirApiSC.PAY_LEVEL && unknown == -EmbassyAirApiSC.PAY_UNKNOWN && poor == -EmbassyAirApiSC.PAY_CHIPS && price > 0
                && paid == EmbassyAirApiSC.PAY_OK && left == 64 - price && counted == 2000000 && over == EmbassyAirApiSC.PAY_LIMIT && free == 0
                && enemy == -EmbassyAirApiSC.PAY_ENEMY;
        // the «Лётная книжка»: once - 2 chips and +5; again - nothing
        clear(p);
        pr.reputation = 150;
        int chipsBefore = pr.chips;
        boolean once = EmbassyAirApiSC.firstLanding(p, id);
        int got = held(p), rep = pr.reputation;
        boolean twice = EmbassyAirApiSC.firstLanding(p, id);
        boolean book = once && !twice && got == EmbassyAirRulesSC.LOGBOOK_CHIPS && rep == 150 + EmbassyAirRulesSC.LOGBOOK_TRUST
                && pr.chips == chipsBefore + EmbassyAirRulesSC.LOGBOOK_CHIPS && EmbassyAirServiceSC.landed(pr, id);
        clear(p);
        print(codes && book, "services (package R, ЛП-10..ЛП-12, ЛП-16): quote / pay - «Чужак» charging " + stranger + " (-LEVEL), an unknown service "
                + unknown + ", no chips " + poor + " (-CHIPS), at «Знакомый» 2 000 000 EU " + price + " chips, paid " + paid + ", " + left + " of 64 left, "
                + counted + " EU counted today, over the service's cap " + over + " (LIMIT), the free chart " + free + ", «Враг» " + enemy + " " + codes
                + "; the logbook once (" + once + ", again " + twice + "): +" + got + " chips, Trust " + rep + " (155), landed " + book + " - said " + p.said);
    }

    // ------------------------------------------------------------------ 9. the embassy's air defence (ЛП-13..ЛП-15)

    @SuppressWarnings("unchecked")
    private void airDefence() {
        if (none("the air defence")) {
            return;
        }
        int id = rec.id;
        int[] c = field.columnBlock(0);
        WorldTestArmourerSC.StationPlayer a = station("ad-admitted", c), b = station("ad-stranger", c);
        SettlementRegistrySC.PlayerRec ar = dealer(a, 150), br = dealer(b, 20);
        boolean admit = EmbassyAirApiSC.admitted(a, id) && !EmbassyAirApiSC.admitted(b, id) && EmbassyAirApiSC.levelOf(b, id) == com.sc.settle.ReputationSC.STRANGER;
        rec.local(a.getUniqueID()).enemyUntil = w.getTotalWorldTime() + 1000L;
        admit &= !EmbassyAirApiSC.admitted(a, id);
        rec.local(a.getUniqueID()).enemyUntil = 0L;
        // the cover (ЛП-14): b and a in one squad, near - b is no target; far apart - he is
        String escort = "skipped (squads off)";
        boolean cover = true;
        if (SubstrateConfigSC.squads) {
            List<Object> players = w.playerEntities;
            players.add(a);
            players.add(b);
            try {
                SquadSC.join(reg, a.getUniqueID(), a.getCommandSenderName(), b.getUniqueID(), b.getCommandSenderName(), w.getTotalWorldTime());
                boolean near = EmbassyAirApiSC.escortOf(b, id) == a && EmbassyAirApiSC.holdFire(b, id);
                b.setLocationAndAngles(b.posX + EmbassyAirRulesSC.ESCORT_RANGE + 10, b.posY, b.posZ, 0F, 0F);
                boolean far = EmbassyAirApiSC.escortOf(b, id) == null;
                b.setLocationAndAngles(c[0] + 0.5D, c[1], c[2] + 0.5D, 0F, 0F);
                SquadSC.leave(reg, b.getUniqueID(), b.getCommandSenderName());
                boolean alone = EmbassyAirApiSC.escortOf(b, id) == null && !EmbassyAirApiSC.holdFire(b, id);
                cover = near && far && alone;
                escort = "near " + near + ", 74 away " + far + ", out of the squad " + alone;
            } finally {
                players.remove(a);
                players.remove(b);
            }
        }
        // 7700 (ЛП-15): once a game day - granted, running, used; the next day again; the admitted needs none; no field - none
        long now = w.getTotalWorldTime(), today = SettlementSC.day(w);
        int granted = EmbassyAirApiSC.requestPass(b, id), running = EmbassyAirApiSC.requestPass(b, id);
        boolean holding = EmbassyAirApiSC.passUntil(b, id) == now + EmbassyAirRulesSC.PASS_SECONDS * 20L && EmbassyAirApiSC.holdFire(b, id);
        NBTTagCompound x = rec.local(b.getUniqueID()).extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        x.setLong(EmbassyAirRulesSC.PASS_UNTIL, now - 1L);                // the hold over, the same day
        int used = EmbassyAirApiSC.requestPass(b, id);
        x.setLong(EmbassyAirRulesSC.PASS_DAY, today - 1L);                // the next game day
        int again = EmbassyAirApiSC.requestPass(b, id);
        x.setLong(EmbassyAirRulesSC.PASS_UNTIL, 0L);
        int admitted = EmbassyAirApiSC.requestPass(a, id);
        int noField = -1;
        for (SettlementRegistrySC.Settlement s : reg.embassies()) {
            if (EmbassyAirfieldSC.fieldOf(s) == null && s.dim == EmbassySiteSC.DIM) {
                noField = EmbassyAirApiSC.requestPass(b, s.id);
                break;
            }
        }
        boolean pass = granted == EmbassyAirApiSC.PASS_GRANTED && running == EmbassyAirApiSC.PASS_RUNNING && holding && used == EmbassyAirApiSC.PASS_USED
                && again == EmbassyAirApiSC.PASS_GRANTED && admitted == EmbassyAirApiSC.PASS_ADMITTED
                && (noField == -1 || noField == EmbassyAirApiSC.PASS_NO_FIELD);
        // the review's: one warning a game day - its WARN_SECONDS from the first ask, the same end the same day (out and back: no new seconds,
        // past it - the fire at once), a new one the next day; the admitted and the FakePlayer need none
        long ws = EmbassyAirRulesSC.WARN_SECONDS * 20L;
        long w1 = EmbassyAirApiSC.requestWarn(b, id), w2 = EmbassyAirApiSC.requestWarn(b, id), wu = EmbassyAirApiSC.warnUntil(b, id);
        NBTTagCompound wx = rec.local(b.getUniqueID()).extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        wx.setLong(EmbassyAirRulesSC.WARN_DAY, today - 1L);               // the next game day
        long w3 = EmbassyAirApiSC.requestWarn(b, id);
        boolean warned = w1 == now + ws && w2 == w1 && wu == w1 && w3 == now + ws && EmbassyAirApiSC.requestWarn(a, id) == 0L
                && EmbassyAirApiSC.requestWarn(fake, id) == 0L && EmbassyAirApiSC.warnUntil(a, id) == 0L
                && wx.getLong(EmbassyAirRulesSC.PASS_DAY) == today;                    // the 7700 ledger beside it kept
        // the review's: the «Лётная книжка» gives its «Враг» nothing and writes nothing
        rec.local(b.getUniqueID()).enemyUntil = w.getTotalWorldTime() + 1000L;
        boolean enemyBook = !EmbassyAirApiSC.firstLanding(b, id) && !EmbassyAirServiceSC.landed(br, id);
        rec.local(b.getUniqueID()).enemyUntil = 0L;
        // the lines: the warning names the board, the seconds, the level that admits; tell() writes it to the pilot's chat
        String warn = EmbassyAirApiSC.adLine(w, id, EmbassyAirApiSC.AD_WARN, "SC-77").getUnformattedText();
        boolean lines = warn.contains("SC-77") && warn.contains(String.valueOf(EmbassyAirRulesSC.WARN_SECONDS)) && !warn.contains("sc.airfield");
        print(admit && cover && pass && warned && enemyBook && lines, "air defence (ЛП-13..ЛП-15): admitted at «Знакомый», not «Чужак», not the «Враг» "
                + admit + "; the cover of an admitted squad mate: " + escort + "; 7700 - granted " + granted + ", running " + running + " (holds fire "
                + holding + "), used " + used + ", next day " + again + ", the admitted " + admitted + ", an embassy with no field " + noField + "; the"
                + " warning once a day (" + w1 + " / " + w2 + " / next day " + w3 + ", now " + now + ") " + warned + "; the logbook refuses the «Враг» "
                + enemyBook + "; the warning \"" + warn + "\" " + lines);
        clear(a);
        clear(b);
        ar.reputation = br.reputation = 0;
    }

    // ------------------------------------------------------------------ 10. the column's armour gases (package R, ЛП-11) - two players at once

    /** The column's two players (the steps share them: a second click counts only HOLD_TICKS after the first - EmbassyAirServiceSC). */
    private WorldTestArmourerSC.StationPlayer gas1, gas2;
    private int quoted1, quoted2, filled1, paid1;
    private boolean waiting, other;

    /** The first clicks of two crouching players (always the core's gases): each gets his own quote, nothing is filled yet. */
    private void columnQuote() {
        if (none("the column")) {
            return;
        }
        int[] c = field.columnBlock(0);
        gas1 = station("gas-1", c);
        gas2 = station("gas-2", c);
        for (WorldTestArmourerSC.StationPlayer p : new WorldTestArmourerSC.StationPlayer[]{gas1, gas2}) {
            dealer(p, 150);
            suit(p);
            chips(p, 64);
            p.setSneaking(true);
        }
        EmbassyAirServiceSC.useColumn(w, c[0], c[1], c[2], gas1);
        EmbassyAirServiceSC.useColumn(w, c[0], c[1], c[2], gas2);
        quoted1 = gas1.said.size();
        quoted2 = gas2.said.size();
        waiting = ArmorGasSC.suitAmount(gas1, ArmorGasSC.Gas.HYDROGEN) == 0 && ArmorGasSC.suitAmount(gas2, ArmorGasSC.Gas.HYDROGEN) == 0
                && held(gas1) == 64 && held(gas2) == 64;
    }

    /** p1's second click: his own quote paid and filled, p2's still pending. */
    private void columnFirstPays() {
        if (gas1 == null) {
            return;
        }
        int[] c = field.columnBlock(0);
        EmbassyAirServiceSC.useColumn(w, c[0], c[1], c[2], gas1);
        filled1 = ArmorGasSC.suitAmount(gas1, ArmorGasSC.Gas.HYDROGEN);
        paid1 = 64 - held(gas1);
        other = ArmorGasSC.suitAmount(gas2, ArmorGasSC.Gas.HYDROGEN) == 0 && held(gas2) == 64;
    }

    /** p2's second click: his quote paid too; the day's count; the verdict. */
    private void columnSecondPays() {
        if (gas1 == null) {
            return;
        }
        int[] c = field.columnBlock(0);
        ArmorGasSC.Gas h2 = ArmorGasSC.Gas.HYDROGEN;
        EmbassyAirServiceSC.useColumn(w, c[0], c[1], c[2], gas2);
        int filled2 = ArmorGasSC.suitAmount(gas2, h2), cap = ArmorGasSC.suitCapacity(gas1, h2);
        SettlementRegistrySC.LocalRec l1 = rec.localIfAny(gas1.getUniqueID());
        int counted = l1 == null ? 0 : l1.count(EmbassyAirRulesSC.COUNT_PREFIX + EmbassyAirRulesSC.SERVICE_KEYS[EmbassyAirRulesSC.S_ARMOUR_GAS]);
        boolean ok = cap > 0 && quoted1 > 0 && quoted2 > 0 && waiting && filled1 > 0 && paid1 > 0 && other && filled2 > 0 && counted >= filled1
                && counted <= EmbassyAirRulesSC.PER_DAY[EmbassyAirRulesSC.S_ARMOUR_GAS];
        print(ok, "column (package R, ЛП-11): two crouching players at «Знакомый» in Quantum armour (" + cap + " mB of hydrogen to fill) at once: the"
                + " first clicks quote (" + quoted1 + " / " + quoted2 + " lines, nothing filled " + waiting + "), p1's second " + T_CLICK + " ticks on fills "
                + filled1 + " mB for " + paid1 + " chips while p2's quote waits " + other + ", p2's second fills " + filled2 + "; " + counted + " mB counted"
                + " today (<= 16 000) - said " + gas1.said);
        for (WorldTestArmourerSC.StationPlayer p : new WorldTestArmourerSC.StationPlayer[]{gas1, gas2}) {
            clear(p);
            for (int i = 0; i < 4; i++) {
                p.inventory.armorInventory[i] = null;
            }
            EmbassyAirServiceSC.forget(p.getUniqueID());
        }
        gas1 = gas2 = null;
    }

    /** A Quantum suit, every tank empty. */
    private static void suit(WorldTestArmourerSC.StationPlayer p) {
        com.sc.item.ItemArmorSC[] pieces = ModItems.ARMOR.get(ArmorSuit.QUANTUM);
        for (int t = 0; pieces != null && t < 4; t++) {
            ItemStack s = new ItemStack(pieces[t]);
            for (ArmorGasSC.Gas g : ArmorGasSC.Gas.values()) {
                ArmorGasSC.setAmount(s, g, 0);
            }
            p.inventory.armorInventory[3 - t] = s;
        }
    }

    // ------------------------------------------------------------------ 11. the Dispatcher's tab (package T, ЛП-17)

    private void tab() {
        if (none("the Dispatcher's tab")) {
            return;
        }
        List<Integer> slots = rec.slotsOf(NpcKindSC.DISPATCHER);
        if (slots.isEmpty()) {
            print(false, "tab (ЛП-17): the record has no Dispatcher's slot");
            return;
        }
        SettlementRegistrySC.Slot sl = rec.slots.get(slots.get(0).intValue());
        EntityTunerSC t = new EntityTunerSC(w);                             // never spawned
        t.bindTo(rec, slots.get(0).intValue());
        t.setLocationAndAngles(sl.postX + 0.5D, sl.postY, sl.postZ + 0.5D, 0F, 0F);
        WorldTestArmourerSC.StationPlayer p = station("tab", new int[]{sl.postX + 1, sl.postY, sl.postZ});
        dealer(p, 150);
        NBTTagCompound s = EmbassyAirServerSC.state(p, t);
        boolean addon = EmbassyAirApiSC.hook() != null;                   // the review's: the add-on's hook (0.1.0 registers none), not the mod
        String want = EmbassyAirfieldSC.icaoShown(reg, rec);                // ЛП-5: the code the tab shows (unique among the fields)
        boolean ok = s != null && s.getInteger("id") == rec.id && s.getInteger("k") == EmbassyAirfieldSC.FULL && want.equals(s.getString("icao"))
                && s.getInteger("lore") == (addon ? 1 : 0) && s.getBoolean("adm") && s.getInteger("lvl") == com.sc.settle.ReputationSC.ACQUAINTANCE
                && s.getInteger("pass") == 0 && s.hasKey("rl") && s.hasKey("rh") && s.hasKey("gas");
        // an embassy without a field has no tab
        boolean noTab = true;
        for (SettlementRegistrySC.Settlement o : reg.embassies()) {
            List<Integer> os = EmbassyAirfieldSC.fieldOf(o) == null ? o.slotsOf(NpcKindSC.DISPATCHER) : null;
            if (os != null && !os.isEmpty()) {
                EntityTunerSC d = new EntityTunerSC(w);
                d.bindTo(o, os.get(0).intValue());
                noTab = EmbassyAirServerSC.state(p, d) == null;
                break;
            }
        }
        print(ok && noTab, "tab (package T, ЛП-17): AIR_STATE at the Dispatcher of an airfield embassy - " + (s == null ? "none" : s.toString().length() > 300
                ? s.toString().substring(0, 300) + "..." : s.toString()) + " (id, FULL, " + want + ", lore " + (addon ? 1 : 0) + " - the hook " + (addon ? "registered" : "none") + ", admitted, «Знакомый»,"
                + " no pass, the numbers, the gases) " + ok + "; none at an embassy without a field " + noTab);
        clear(p);
    }

    // ------------------------------------------------------------------ 12. the «Достройка» over the field (package G, ЛП-7)

    private void repair() {
        if (none("the «Достройка»")) {
            return;
        }
        EmbassyAirfieldSC.Field f = field;
        int y = f.floorY;
        int sx = f.x(10, f.axis), sz = f.z(10, f.axis);                  // snow on the runway
        int[] ap = {(f.apron[0] + f.apron[2]) / 2, (f.apron[1] + f.apron[3]) / 2};
        int gx = f.x(ap[0], ap[1]), gz = f.z(ap[0], ap[1]);              // growth (a log) on the apron
        int[] l = f.lights(BlockAirfieldSC.LIGHT_EDGE).get(0);
        int lx = f.x(l[0], l[1]), lz = f.z(l[0], l[1]);                  // an edge light taken off past the events
        int[] col = f.columnBlock(0);
        Block snowWas = w.getBlock(sx, y + 1, sz), logWas = w.getBlock(gx, y + 1, gz);
        w.setBlock(sx, y + 1, sz, Blocks.snow_layer, 0, 2);
        w.setBlock(gx, y + 1, gz, Blocks.log, 0, 2);
        w.setBlockToAir(lx, y, lz);
        int windows = EmbassyRepairSC.repair(w, rec, true);
        boolean snow = w.getBlock(sx, y + 1, sz) != Blocks.snow_layer, log = w.getBlock(gx, y + 1, gz) != Blocks.log;
        boolean light = w.getBlock(lx, y, lz) == EmbassyBlocksSC.airfield && w.getBlockMetadata(lx, y, lz) == BlockAirfieldSC.LIGHT_EDGE;
        boolean kept = w.getBlock(col[0], col[1], col[2]) == SettlementBlocksSC.showcase2 && w.getBlockMetadata(col[0], col[1], col[2]) == BlockShowcase2SC.SERVICE_COLUMN;
        // the test leaves the field whole whatever the «Достройка» did
        if (!snow) {
            w.setBlock(sx, y + 1, sz, snowWas == Blocks.snow_layer ? Blocks.air : snowWas, 0, 2);
        }
        if (!log) {
            w.setBlock(gx, y + 1, gz, logWas == Blocks.log ? Blocks.air : logWas, 0, 2);
        }
        if (!light) {
            w.setBlock(lx, y, lz, EmbassyBlocksSC.airfield, BlockAirfieldSC.LIGHT_EDGE, 2);
        }
        print(snow && log && light && kept, "«Достройка» over the field (package G, ЛП-7): repair(force) (" + windows + " windows) - the snow on the runway gone "
                + snow + ", the log on the apron gone " + log + ", the edge light taken off put back " + light + ", the column kept " + kept);
    }

    // ------------------------------------------------------------------ 13. the old records untouched (ЛП-19), AUTO without the add-on

    /**
     * The command (package G, §4.6; the integrator's): /scembassy list - this embassy's line with its airfield and the variants' share; /scembassy
     * airfield id - the report (its ICAO first, the runway, the zone, the air defence, the revision); an earlier record without "X" - one
     * "no airfield" line, nothing fitted (ЛП-19). No raw key, no throw.
     */
    private void command() {
        if (none("the command")) {
            return;
        }
        WorldTestArmourerSC.StationPlayer p = station("cmd", new int[]{rec.x, rec.floorY, rec.z});
        CommandEmbassySC cmd = new CommandEmbassySC();
        StringBuilder err = new StringBuilder();
        List<String> list = said(p, cmd, err, "list", "2000");
        List<String> report = said(p, cmd, err, "airfield", String.valueOf(rec.id));
        SettlementRegistrySC.Settlement old = null;
        for (Integer id : before) {
            SettlementRegistrySC.Settlement s = reg.byId(id.intValue());
            if (s != null && EmbassyAirfieldSC.decisionOf(s) == null) {
                old = s;
                break;
            }
        }
        List<String> oldSaid = old == null ? new ArrayList<String>() : said(p, cmd, err, "airfield", String.valueOf(old.id));
        String icao = EmbassyAirfieldSC.icaoShown(reg, rec);                // ЛП-5: the code /scembassy shows
        int airLines = 0, raw = 0;
        boolean share = false;
        List<String> all = new ArrayList<String>(list);
        all.addAll(report);
        all.addAll(oldSaid);
        for (String l : all) {
            raw += l.contains("sc.cmd.") || l.contains("sc.airfield.") ? 1 : 0;
        }
        for (String l : list) {
            airLines += l.contains("• ") ? 1 : 0;
            share |= l.startsWith("Airfields (") || l.startsWith("Лётные поля (");
        }
        boolean reportOk = report.size() == 5 && report.get(0).contains(icao);
        boolean oldOk = old == null || oldSaid.size() == 1 && EmbassyAirfieldSC.decisionOf(old) == null && EmbassyAirfieldSC.fieldOf(old) == null;
        print(err.length() == 0 && raw == 0 && airLines >= 1 && share && reportOk && oldOk, "command (package G, §4.6): /scembassy list 2000 - "
                + list.size() + " lines, " + airLines + " with an airfield variant, the share line " + share + "; /scembassy airfield " + rec.id + " - "
                + report.size() + " lines (5), " + icao + " first " + reportOk + "; an earlier record without \"X\" (" + (old == null ? "none" : old.id)
                + ") - " + oldSaid + " " + oldOk + "; raw keys " + raw + " (0)" + err + " - " + (report.isEmpty() ? "" : report.get(0))
                + (list.size() > 1 ? " / " + list.get(1) : "") + (list.isEmpty() ? "" : " / " + list.get(list.size() - 1)));
    }

    /** What /scembassy `args` says to `p` (its chat cleared before), a throw into `err`. */
    private static List<String> said(WorldTestArmourerSC.StationPlayer p, CommandEmbassySC cmd, StringBuilder err, String... args) {
        p.said.clear();
        try {
            cmd.processCommand(p, args);
        } catch (Throwable t) {
            err.append(' ').append(Arrays.toString(args)).append(": ").append(t);
        }
        List<String> out = new ArrayList<String>(p.said);
        p.said.clear();
        return out;
    }

    private void untouched() {
        StringBuilder bad = new StringBuilder();
        int old = 0;
        for (Integer id : before) {
            SettlementRegistrySC.Settlement s = reg.byId(id.intValue());
            if (s == null) {
                continue;
            }
            old++;
            boolean x = EmbassyAirfieldSC.decisionOf(s) != null;
            if (x && !beforeHadX.contains(id)) {
                bad.append(' ').append(id).append(" got an \"X\"");
            }
            if (!x && (EmbassyAirfieldSC.fieldOf(s) != null || s.airfieldBox() != null || EmbassyAirApiSC.airfield(w, s.id) != null)) {
                bad.append(' ').append(id).append(" has a field without \"X\"");
            }
        }
        // AUTO without the add-on: a new embassy's decision is none (no "X" written)
        String auto = "the add-on loaded or the mode not AUTO - not asked";
        boolean autoOk = true;
        if (SubstrateConfigSC.embassyAirfield() == SubstrateConfigSC.AIRFIELD_AUTO && !Loader.isModLoaded(EmbassyAirfieldSC.ADDON)) {
            int[] d = site == null ? null : EmbassyAirfieldSC.decideFor(w, site);
            autoOk = d == null && !EmbassyAirfieldSC.buildsNow();
            auto = "decideFor " + (d == null ? "null" : Arrays.toString(d)) + ", buildsNow " + EmbassyAirfieldSC.buildsNow();
        }
        print(bad.length() == 0 && autoOk, "old records untouched (ЛП-19): " + old + " embassies made before this test keep their \"X\" or its lack, none got"
                + " a field" + bad + "; AUTO without the add-on: " + auto + " " + autoOk);
        finish();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A real test player (never ticking, not in the server's list; his chat kept) standing at block `at` - with a connection that drops
     * every packet (WorldTestEmbassyFlySC.Mute): the airfield's services serve only a player with one (a real player has it).
     */
    private WorldTestArmourerSC.StationPlayer station(String name, int[] at) {
        WorldTestArmourerSC.StationPlayer p = new WorldTestArmourerSC.StationPlayer(w, new GameProfile(UUID.nameUUIDFromBytes(("sc-airfield-" + name)
                .getBytes()), "[SCTestAir]"));
        new WorldTestEmbassyFlySC.Mute(p);
        p.setLocationAndAngles(at[0] + 0.5D, at[1] + 1, at[2] + 0.5D, 0F, 0F);
        return p;
    }

    /** `p`'s record at `reputation` with today's counters fresh, not this embassy's «Враг», his inventory empty. */
    private SettlementRegistrySC.PlayerRec dealer(WorldTestArmourerSC.StationPlayer p, int reputation) {
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        long today = SettlementSC.day(w);
        pr.reputation = reputation;
        pr.rollDay(today - 1);
        pr.rollDay(today);
        SettlementRegistrySC.LocalRec lr = rec.local(p.getUniqueID());
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        lr.extra.removeTag(EmbassyAirRulesSC.LEDGER);
        pr.extra.removeTag(EmbassyAirRulesSC.LOGBOOK);
        clear(p);
        return pr;
    }

    private static void chips(WorldTestArmourerSC.StationPlayer p, int n) {
        ItemStack c = SettlementItemsSC.chips(n);
        if (c != null) {
            p.inventory.addItemStackToInventory(c);
        }
    }

    private static int held(WorldTestArmourerSC.StationPlayer p) {
        ItemStack want = SettlementItemsSC.chips(1);
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && want != null && s.getItem() == want.getItem() && s.getItemDamage() == want.getItemDamage() ? s.stackSize : 0;
        }
        return n;
    }

    private static void clear(WorldTestArmourerSC.StationPlayer p) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            p.inventory.mainInventory[i] = null;
        }
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " airfield: " + what);
    }
}
