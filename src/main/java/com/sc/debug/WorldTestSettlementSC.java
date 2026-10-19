package com.sc.debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.emp.EmpMobSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.init.ModBlocks;
import com.sc.item.ItemArmorSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.gen.ComponentSettlementSC;
import com.sc.settle.gen.MapGenSettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.gen.StructureSettlementStartSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.settle.travel.TravelRulesSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.tileentity.TileEntityShowcaseSC;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * PACKAGE D (docs/plan-substrate-tech-f2.md §11.2): the settlements in a running server (-Dsc.worldtest=true; SCMod
 * registers it on FML's bus at server start). SKIP while the Substrate is off or dimension.villages is. One line
 * each, "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO settlement: ...", as WorldTestSubstrateSC. It starts after the Ф1
 * test's report (START), so the two never share the world's chunks or its forced-chunk tickets.
 * <ol>
 * <li>registration: SiliconAge.tuner / .warden in EntityList, the structure's MapGenStructureIO names, both tiles' ids;</li>
 * <li>the First Dock generated (the world test's own seed): its footprint and a ring of chunks loaded in one tick while
 * ChunkEvent.Load is counted - not one chunk past the ring (no cascade); the core where the plan puts it with the
 * record's id, the record (first, the roster of §4.2, the points, the dock), the beacon and the plate with air over
 * it, no Minecraft stone; two ticks on, the core among the up fields. A world explored before Ф2 that has no room for
 * it: the nearest settlement still to come instead;</li>
 * <li>a Ф1 world: one chunk of a far settlement's footprint made with the generator off - its start then is empty, no
 * record, no core;</li>
 * <li>the protection: a FakePlayer's break / place inside cancelled, an explosion inside breaks nothing, a bucket in the
 * dock's zone cancelled, a player's Field Generator next to the dome refused with «[Поселение-N]», refusesBridgeEnd
 * inside yes, on the beacon no;</li>
 * <li>the roster spawned by the core (package C) and the settlers: a FakePlayer's blow in the belt cancelled, past it
 * not, a ПЭ-1 Pulse refused;</li>
 * <li>the guards: armour by rank, the reserve in bounds, armoured for ПЭ-1, a FakePlayer's blow cancelled silently;</li>
 * <li>the Clone Capsule: a Tuner killed - DEAD, back at the capsule once its time is past; a second Tuner the same day -
 * not; a guard - back after its 5 minutes;</li>
 * <li>the trade: TradeServerSC.execute (package T, found by reflection - no window, no packets) for a FakePlayer - the
 * inputs taken, the chips given, the counter, the limit; a Violator buys nothing; past 64 chips a day - refused;</li>
 * <li>the bridge (package B): famInfo at the dock's beacon - no scatter; at the death mark - at least 30; an end on the
 * square refused with the nearest dock's landing point, on the dock allowed; the Settlement Map's docks (the core can't
 * be broken, every workshop of the roster has its showcase machines and the Clone Capsule stands - checked alongside);</li>
 * <li>the spawn director: no spot within 40 of the dome's edge, one far off;</li>
 * <li>Ф1: the Substrate's own world test points are far from every settlement.</li>
 * </ol>
 * Every switch it flips is put back, every ticket released; the creatures it kills are the settlement's own (the core
 * brings them back), the FakePlayers are never spawned.
 */
public final class WorldTestSettlementSC {

    /** After the Ф1 test's unload report (it starts at 140 and reports 1200 ticks after its last check). */
    private static final int START = 2400;
    private static final int FIELD_WAIT = 2, ROSTER_WAIT = 600, RESPAWN_WAIT = 160;
    /** Where the Ф1 world test does its multi-tick checks (WorldTestSubstrateSC.GX / GZ). */
    private static final int F1_X = 8 * 16 + 8, F1_Z = 8 * 16 + 2;
    private static final GameProfile FAKE = new GameProfile(UUID.fromString("5c0e7a1e-5e77-4000-8000-00000000f001"), "[SCTestSettle]");
    private static final GameProfile TRADER = new GameProfile(UUID.fromString("5c0e7a1e-5e77-4000-8000-00000000f002"), "[SCTestTrader]");

    private int ticks, phase, phaseAt;
    private boolean done, saved, savedVillages, savedAggro;
    private int dim;
    private WorldServer w;
    private SettlementRegistrySC reg;
    private SettlementRegistrySC.Settlement rec;
    private SettlementLayoutSC.Site site;
    private SettlementLayoutSC.Plan plan;
    private TileEntitySettlementCoreSC core;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final ChunkWatch watch = new ChunkWatch();
    /** The Clone Capsule's check: the first Tuner's slot and creature, the second Tuner's slot, the guard's slot, the game day of the kills. */
    private int tunerSlot = -1, tuner2Slot = -1, guardSlot = -1;
    private UUID tunerWas;
    private long killDay;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        try {
            if (ticks == START) {
                start();
            } else if (ticks > START) {
                step();
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void start() {
        if (!SubstrateSC.active() || !SettlementSC.generates()) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: " + (SubstrateSC.active() ? "dimension.villages is off" : "the dimension is off ("
                    + SubstrateSC.offReason() + ")"));
            done = true;
            return;
        }
        dim = SubstrateSC.dimId();
        savedVillages = SubstrateConfigSC.villages;
        savedAggro = SubstrateConfigSC.guardsAggroOnViolations;
        saved = true;
        registration();
        w = MinecraftServer.getServer().worldServerForDimension(dim);
        reg = SettlementRegistrySC.get(w);
        if (w == null || reg == null) {
            print(false, "the world: worldServerForDimension(" + dim + ") " + (w == null ? "null" : "ok") + ", the registry " + (reg == null ? "null" : "ok"));
            finish();
            return;
        }
        if (!generate()) {
            finish();
            return;
        }
        emptyStart();
        protection();
        forceDome();
        f1Points();
        phase = 1;
        phaseAt = ticks;
    }

    private void step() {
        int t = ticks - phaseAt;
        if (phase == 1 && t >= FIELD_WAIT) {
            fieldUp();
            phase = 2;
            phaseAt = ticks;
        } else if (phase == 2 && (t % 20 == 0 && rosterReady() || t >= ROSTER_WAIT)) {
            roster(t);
            villagers();
            wardens();
            trade();
            bridge();
            travel();
            showcases();
            spawning();
            killFirstTuner();
            phase = 3;
            phaseAt = ticks;
        } else if (phase == 3 && (t % 20 == 0 && tunerBack() != null || t >= RESPAWN_WAIT)) {
            firstBack(t);
            killMore();
            phase = 4;
            phaseAt = ticks;
        } else if (phase == 4 && t >= RESPAWN_WAIT) {
            moreBack();
            finish();
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (saved) {
            SubstrateConfigSC.villages = savedVillages;
            SubstrateConfigSC.guardsAggroOnViolations = savedAggro;
        }
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        if (w != null) {
            clear(FakePlayerFactory.get(w, FAKE));
            clear(FakePlayerFactory.get(w, TRADER));
        }
        w = null;
        System.out.println("[SC-WORLDTEST] DONE settlement");
    }

    // ------------------------------------------------------------------ 1. registration

    private void registration() {
        boolean entities = EntityList.stringToClassMapping.get("SiliconAge." + EntityTunerSC.NAME) == EntityTunerSC.class
                && EntityList.stringToClassMapping.get("SiliconAge." + EntityWardenSC.NAME) == EntityWardenSC.class;
        String start = MapGenStructureIO.func_143033_a(new StructureSettlementStartSC());
        String piece = MapGenStructureIO.func_143036_a(new ComponentSettlementSC());
        boolean structure = MapGenSettlementSC.START_NAME.equals(start) && piece != null && piece.startsWith(MapGenSettlementSC.PIECE_PREFIX);
        String coreId = tileId(new TileEntitySettlementCoreSC()), showcaseId = tileId(new TileEntityShowcaseSC());
        boolean tiles = "SiliconAge.settlementCore".equals(coreId) && "SiliconAge.showcase".equals(showcaseId);
        boolean blocks = SettlementBlocksSC.core != null && SettlementBlocksSC.showcase != null && SettlementBlocksSC.dock != null
                && SettlementItemsSC.creditChip != null && SettlementItemsSC.returnCapsule != null && SettlementItemsSC.settlementMap != null;
        print(entities && structure && tiles && blocks, "registration: SiliconAge.tuner / SiliconAge.warden in EntityList " + entities + ", the structure "
                + start + " / piece " + piece + " (" + MapGenSettlementSC.START_NAME + ", " + MapGenSettlementSC.PIECE_PREFIX + "*) " + structure
                + ", tiles " + coreId + " / " + showcaseId + " " + tiles + ", the blocks and items " + blocks);
    }

    private static String tileId(TileEntity te) {
        NBTTagCompound n = new NBTTagCompound();
        try {
            te.writeToNBT(n);
        } catch (RuntimeException e) {
            return "unregistered (" + e.getMessage() + ")";
        }
        return n.getString("id");
    }

    // ------------------------------------------------------------------ 2. the First Dock, generated

    /** @return false if nothing could be generated (the rest needs a settlement) */
    private boolean generate() {
        long seed = w.getSeed();
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, 0, 0);
        if (s == null) {
            print(false, "the First Dock: SettlementLayoutSC.site(seed, 0, 0) is null (package S)");
            return false;
        }
        boolean first = true;
        if (reg.atCentre(dim, s.x, s.z) == null && !fresh(SettlementLayoutSC.plan(seed, s))) {
            System.out.println("[SC-WORLDTEST] INFO settlement: the First Dock's land was explored before its start (a world of 0.2.x) - the nearest"
                    + " settlement still to come is checked instead");
            s = freshSite(seed, 24, 24, true);
            first = false;
            if (s == null) {
                System.out.println("[SC-WORLDTEST] SKIP settlement: no fresh land for a settlement near regions 24..44");
                return false;
            }
        }
        site = s;
        plan = SettlementLayoutSC.plan(seed, s);
        int[] fc = SettlementLayoutSC.footprintChunks(plan);
        boolean made = reg.atCentre(dim, s.x, s.z) != null;              // an earlier run of the test in this world
        watch.start(w);
        MinecraftForge.EVENT_BUS.register(watch);
        try {
            for (int cx = fc[0] - 1; cx <= fc[2] + 1; cx++) {
                for (int cz = fc[1] - 1; cz <= fc[3] + 1; cz++) {
                    w.getChunkFromChunkCoords(cx, cz);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int outside = 0;
        String far = null;
        for (Long k : watch.loaded) {
            int x = (int) (k.longValue() >> 32), z = (int) k.longValue();
            if (x < fc[0] - 1 || x > fc[2] + 1 || z < fc[1] - 1 || z > fc[3] + 1) {
                outside++;
                far = far == null ? x + ", " + z : far;
            }
        }
        rec = reg.atCentre(dim, s.x, s.z);
        TileEntity te = w.getTileEntity(plan.coreX, plan.coreY, plan.coreZ);
        core = te instanceof TileEntitySettlementCoreSC ? (TileEntitySettlementCoreSC) te : null;
        boolean coreOk = w.getBlock(plan.coreX, plan.coreY, plan.coreZ) == SettlementBlocksSC.core && core != null && rec != null
                && core.settlementId() == rec.id && core.radius() == s.radius && core.floorY() == plan.floorY;
        List<NpcKindSC> kinds = new ArrayList<NpcKindSC>();
        for (SettlementRegistrySC.Slot sl : rec == null ? new ArrayList<SettlementRegistrySC.Slot>() : rec.slots) {
            kinds.add(sl.kindOf());
        }
        withoutF6Dispatcher(rec, kinds);                // Ф6: the Dispatcher a dock's core appends at its first second is not the plan's
        // Ф3: a record made by an earlier run of Ф2 had its Ф3 slots appended (RosterSC.upgrade - WorldTestArmourerSC checks that): its roster
        // is the Ф2 one with the new kinds after the guards, not the plan's of version 2. One still of version 1 - its core's first second
        // (the upgrade) is yet to come: this test has just loaded its chunk - holds the Ф2 roster, which no plan of version 2 makes
        boolean v1 = rec != null && rec.version < SettlementRegistrySC.VERSION;
        boolean upgraded = rec != null && (v1 || rec.extra.getCompoundTag("S").hasKey("F3From"));
        String rosterWhy = rec == null ? "no record" : upgraded ? null : SelfTestSettlementSC.rosterProblem(s.size, s.seed, kinds);
        boolean record = rec != null && rec.first == first && rec.size == s.size.ordinal() && rec.radius == s.radius && rec.floorY == plan.floorY
                && rec.hasDock == plan.dock && (!plan.dock || rec.dockX == plan.dockX && rec.dockY == plan.dockY && rec.dockZ == plan.dockZ)
                && rosterWhy == null && (upgraded || kinds.equals(SettlementLayoutSC.roster(s))) && rec.points.keySet().containsAll(plan.pointNames)
                && rec.madeAt >= 0L;
        String dockWhy = plan.dock ? dockProblem() : null;
        int stone = 0;
        for (int cx = fc[0]; cx <= fc[2]; cx++) {
            for (int cz = fc[1]; cz <= fc[3]; cz++) {
                Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 256; y++) {
                            stone += ch.getBlock(x, y, z) == Blocks.stone ? 1 : 0;
                        }
                    }
                }
            }
        }
        boolean cascade = made || outside == 0;
        print(coreOk && record && dockWhy == null && stone == 0 && cascade, "generation (package S, ПП-1..ПП-4) of " + (first ? "the First Dock" : "a settlement")
                + " (" + s.size + " r " + s.radius + " at " + s.x + ", " + s.z + ", " + (fc[2] - fc[0] + 1) + " x " + (fc[3] - fc[1] + 1) + " chunks): the core"
                + " at " + plan.coreX + ", " + plan.coreY + ", " + plan.coreZ + " with the record's id " + coreOk + "; the record (first " + (rec != null && rec.first)
                + ", the roster of §4.2 " + (rosterWhy == null ? v1 ? "ok (a Ф2 record, its upgrade on the core's first second)" : upgraded
                ? "ok (a Ф2 record upgraded)" : "ok" : rosterWhy) + ", the points, the dock) " + record
                + "; the dock "
                + (plan.dock ? dockWhy == null ? "ok" : dockWhy : "none (an Outpost)") + "; Minecraft stone " + stone + " (0); "
                + (made ? "made by an earlier run - no cascade count" : watch.loaded.size() + " chunks loaded, " + outside + " of them past the ring"
                + (far == null ? "" : " (" + far + ")") + " - no cascade " + cascade));
        return rec != null;
    }

    /** The beacon in the middle of a 7 x 7 plate, 8 blocks of air over it; null if it is so. */
    private String dockProblem() {
        if (rec == null || !rec.hasDock) {
            return "not in the record";
        }
        int bx = rec.dockX, by = rec.dockY, bz = rec.dockZ;
        if (w.getBlock(bx, by, bz) != SettlementBlocksSC.dock || w.getBlockMetadata(bx, by, bz) != com.sc.block.BlockDockSC.BEACON) {
            return "no beacon at " + bx + ", " + by + ", " + bz;
        }
        int plates = 0, air = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if ((dx | dz) != 0 && w.getBlock(bx + dx, by, bz + dz) == SettlementBlocksSC.dock
                        && w.getBlockMetadata(bx + dx, by, bz + dz) == com.sc.block.BlockDockSC.PLATE) {
                    plates++;
                }
                for (int dy = 1; dy <= 8; dy++) {
                    air += w.isAirBlock(bx + dx, by + dy, bz + dz) ? 1 : 0;
                }
            }
        }
        return plates == 48 && air == 49 * 8 ? null : "plates " + plates + " (48), air over it " + air + " (392)";
    }

    /** No chunk of the plan's footprint exists yet (on disk or loaded): its start, when made, is a full one. */
    private boolean fresh(SettlementLayoutSC.Plan p) {
        int[] fc = SettlementLayoutSC.footprintChunks(p);
        IChunkLoader l = w.theChunkProviderServer.currentChunkLoader;
        for (int cx = fc[0]; cx <= fc[2]; cx++) {
            for (int cz = fc[1]; cz <= fc[3]; cz++) {
                if (w.theChunkProviderServer.chunkExists(cx, cz) || l instanceof AnvilChunkLoader && ((AnvilChunkLoader) l).chunkExists(w, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first site in regions (rx0.., rz0..) with fresh land and no record (with a dock if `dock`), or null. */
    private SettlementLayoutSC.Site freshSite(long seed, int rx0, int rz0, boolean dock) {
        for (int rx = rx0; rx < rx0 + 20; rx++) {
            for (int rz = rz0; rz < rz0 + 20; rz++) {
                SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                if (s != null && (!dock || s.size.dock) && reg.atCentre(dim, s.x, s.z) == null && fresh(SettlementLayoutSC.plan(seed, s))) {
                    return s;
                }
            }
        }
        return null;
    }

    /** 2b: the core's field is up - as any field, in the ACTIVE list (privateRefusing, the explosion shield, the EMP screen see it). */
    private void fieldUp() {
        boolean up = core != null && !core.isInvalid() && TileEntityFieldGeneratorSC.activeFieldsIn(w).contains(core);
        print(up, "the core's field (ПП-6): " + FIELD_WAIT + " ticks after the generation it is among the world's up fields " + up);
    }

    // ------------------------------------------------------------------ 3. a Ф1 world: an empty start

    private void emptyStart() {
        long seed = w.getSeed();
        SettlementLayoutSC.Site s = freshSite(seed, 60, 60, false);
        if (s == null) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: the empty start - no fresh land near regions 60..80");
            return;
        }
        SettlementLayoutSC.Plan p = SettlementLayoutSC.plan(seed, s);
        int[] fc = SettlementLayoutSC.footprintChunks(p);
        SubstrateConfigSC.villages = false;                              // a 0.2.x world: one chunk of its land made before any start
        try {
            w.getChunkFromChunkCoords(fc[0], fc[1]);
        } finally {
            SubstrateConfigSC.villages = savedVillages;
        }
        int cx = s.x >> 4, cz = s.z >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                w.getChunkFromChunkCoords(x, z);
            }
        }
        boolean noRecord = reg.atCentre(dim, s.x, s.z) == null;
        boolean noCore = w.getBlock(p.coreX, p.coreY, p.coreZ) != SettlementBlocksSC.core;
        boolean populated = w.getChunkFromChunkCoords(cx, cz).isTerrainPopulated;
        print(noRecord && noCore && populated, "a world of 0.2.x (ПП-2): chunk " + fc[0] + ", " + fc[1] + " of a " + s.size + "'s land at " + s.x + ", " + s.z
                + " made first with the generator off - then its core's chunk populated " + populated + ": no record " + noRecord + ", no core " + noCore);
    }

    // ------------------------------------------------------------------ 4. the protection (package C, §5.3)

    private void protection() {
        FakePlayer fake = fake();
        int[] sq = point(SettlementRegistrySC.P_SQUARE);
        int x = sq[0], z = sq[2], y = plan.floorY;
        fake.setLocationAndAngles(x + 0.5D, y + 1, z + 0.5D, 0F, 0F);
        Block floor = w.getBlock(x, y, z);
        BlockEvent.BreakEvent br = new BlockEvent.BreakEvent(x, y, z, w, floor, w.getBlockMetadata(x, y, z), fake);
        boolean breakOff = MinecraftForge.EVENT_BUS.post(br);
        BlockEvent.PlaceEvent pl = new BlockEvent.PlaceEvent(BlockSnapshot.getBlockSnapshot(w, x, y + 1, z), floor, fake);
        boolean placeOff = MinecraftForge.EVENT_BUS.post(pl);
        // an explosion two blocks over the square's floor
        int solid = solidAround(x, y + 2, z);
        w.newExplosion(null, x + 0.5D, y + 2.5D, z + 0.5D, 3F, false, true);
        int after = solidAround(x, y + 2, z);
        // a bucket in the dock's zone
        boolean bucketOff = true;
        if (rec.hasDock) {
            FillBucketEvent fb = new FillBucketEvent(fake, new ItemStack(Items.water_bucket), w,
                    new MovingObjectPosition(rec.dockX + 2, rec.dockY, rec.dockZ, 1, Vec3.createVectorHelper(rec.dockX + 2.5D, rec.dockY + 1D, rec.dockZ + 0.5D)));
            bucketOff = MinecraftForge.EVENT_BUS.post(fb);
        }
        // a player's Field Generator just outside the dome, on the side away from the dock
        int[] d = awayFromDock();
        int fx = rec.x + d[0] * (rec.radius + 4), fz = rec.z + d[1] * (rec.radius + 4), fy = plan.floorY + 1;
        Block was = w.getBlock(fx, fy, fz);
        int wasMeta = w.getBlockMetadata(fx, fy, fz);
        String refused = null;
        w.setBlock(fx, fy, fz, ModBlocks.fieldGeneratorSC, 0, 2);
        TileEntity te = w.getTileEntity(fx, fy, fz);
        if (te instanceof TileEntityFieldGeneratorSC) {
            ((TileEntityFieldGeneratorSC) te).setPlacer("SCTestFieldOwner");
            refused = ((TileEntityFieldGeneratorSC) te).placedNearForeign();
        }
        w.setBlock(fx, fy, fz, was, wasMeta, 2);
        boolean field = rec.owner().equals(refused);
        boolean bridge = SettlementSC.refusesBridgeEnd(w, rec.x, plan.floorY + 2, rec.z)
                && (!rec.hasDock || !SettlementSC.refusesBridgeEnd(w, rec.dockX, rec.dockY + 1, rec.dockZ));
        print(breakOff && placeOff && solid == after && bucketOff && field && bridge, "protection (package C, ПП-7..ПП-9): a FakePlayer breaking the square's"
                + " floor cancelled " + breakOff + ", placing on it " + placeOff + "; an explosion over the square - solid blocks round it " + solid + " -> "
                + after + "; a bucket in the dock's zone cancelled " + bucketOff + (rec.hasDock ? "" : " (no dock)") + "; a Field Generator " + (rec.radius + 4)
                + " from the core - refused, owner \"" + refused + "\" " + field + "; refusesBridgeEnd inside the dome yes, on the beacon no " + bridge);
    }

    private int solidAround(int x, int y, int z) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    n += w.isAirBlock(x + dx, y + dy, z + dz) ? 0 : 1;
                }
            }
        }
        return n;
    }

    /** A unit step along x or z away from the dock (the dock's side kept clear of the test's blocks), {dx, dz}. */
    private int[] awayFromDock() {
        if (rec == null || !rec.hasDock) {
            return new int[]{1, 0};
        }
        int dx = rec.dockX - rec.x, dz = rec.dockZ - rec.z;
        return Math.abs(dx) >= Math.abs(dz) ? new int[]{dx > 0 ? -1 : 1, 0} : new int[]{0, dz > 0 ? -1 : 1};
    }

    /** A named point of the record (the core's column for a missing one). */
    private int[] point(String name) {
        int[] p = rec.point(name);
        return p != null ? p : new int[]{rec.x, plan.floorY + 1, rec.z};
    }

    /**
     * Forces the dome's chunks and a ring round them: the core ticks, the creatures live (a world with no players unloads
     * every other chunk), and a post's populate window (its chunk 8 blocks back - package C's «built») stays loaded.
     */
    private void forceDome() {
        ForgeChunkManager.Ticket t = null;
        int r = rec.radius + 16, forced = 0, wanted = 0;
        for (int cx = rec.x - r >> 4; cx <= rec.x + r >> 4; cx++) {
            for (int cz = rec.z - r >> 4; cz <= rec.z + r >> 4; cz++) {
                wanted++;
                if (t == null || t.getChunkList().size() >= t.getChunkListDepth()) {
                    t = ForgeChunkManager.requestTicket(SCMod.instance, w, ForgeChunkManager.Type.NORMAL);
                    if (t == null) {
                        break;
                    }
                    t.getModData().setString("Kind", "worldtest");          // ChunkLoaderSC releases it on a later load
                    tickets.add(t);
                }
                ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
                forced++;
            }
        }
        if (forced < wanted) {
            System.out.println("[SC-WORLDTEST] INFO settlement: only " + forced + " of the dome's " + wanted + " chunks could be forced (Forge's ticket"
                    + " limits) - the roster may come up short");
        }
    }

    /** 11: the Ф1 world test's points are clear of every settlement (its spawn director and mobs stay as they were). */
    private void f1Points() {
        SettlementRegistrySC.Settlement near = reg.within(dim, F1_X, F1_Z, SettlementSC.SPAWN_MARGIN + SpawnRulesSC.MAX_DIST);
        print(near == null, "Ф1's test points (" + F1_X + ", " + F1_Z + ": the spawn director, the gun, the portal) are no nearer than "
                + (SettlementSC.SPAWN_MARGIN + SpawnRulesSC.MAX_DIST) + " to a dome's edge " + (near == null) + (near == null ? "" : " - settlement " + near.id
                + " at " + near.x + ", " + near.z) + "; WorldTestSubstrateSC / WorldTestEmpSC report their own lines");
    }

    // ------------------------------------------------------------------ 5-7. the roster, the settlers, the guards

    private List<Entity> members() {
        List<Entity> out = new ArrayList<Entity>();
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof ISettlementMemberSC && ((Entity) o).isEntityAlive() && ((ISettlementMemberSC) o).settlementId() == rec.id) {
                out.add((Entity) o);
            }
        }
        return out;
    }

    private List<EntityTunerSC> tuners() {
        List<EntityTunerSC> out = new ArrayList<EntityTunerSC>();
        for (Entity e : members()) {
            if (e instanceof EntityTunerSC) {
                out.add((EntityTunerSC) e);
            }
        }
        return out;
    }

    private List<EntityWardenSC> guards() {
        List<EntityWardenSC> out = new ArrayList<EntityWardenSC>();
        for (Entity e : members()) {
            if (e instanceof EntityWardenSC) {
                out.add((EntityWardenSC) e);
            }
        }
        return out;
    }

    private int alive() {
        int n = 0;
        for (SettlementRegistrySC.Slot s : rec.slots) {
            n += s.state == SettlementRegistrySC.ALIVE ? 1 : 0;
        }
        return n;
    }

    private boolean rosterReady() {
        return alive() == rec.slots.size() && members().size() >= rec.slots.size();
    }

    /** ПП-13: the core put the roster in the world (package C), every creature bound to its slot (package N). */
    private void roster(int waited) {
        List<Entity> m = members();
        StringBuilder bad = new StringBuilder();
        Set<Integer> slots = new HashSet<Integer>();
        for (Entity e : m) {
            ISettlementMemberSC s = (ISettlementMemberSC) e;
            SettlementRegistrySC.Slot sl = rec.slots.size() > s.slot() && s.slot() >= 0 ? rec.slots.get(s.slot()) : null;
            if (sl == null || !slots.add(Integer.valueOf(s.slot())) || sl.kindOf() != s.kind() || sl.nameCode() != s.nameCode()
                    || !e.getUniqueID().equals(sl.uuid())) {
                bad.append(' ').append(EntityList.getEntityString(e)).append(" slot ").append(s.slot());
            }
        }
        int waiting = 0;                                                // an earlier run's dead, still waiting for the Clone Capsule
        for (SettlementRegistrySC.Slot sl : rec.slots) {
            waiting += waitsForClone(sl) ? 1 : 0;
        }
        int want = rec.slots.size() - waiting;
        boolean full = alive() == want && m.size() == want;
        print(full && bad.length() == 0, "the roster (package C, ПП-13): " + m.size() + " creatures of " + rec.slots.size() + " slots in the world, "
                + alive() + " ALIVE in the registry" + (waiting == 0 ? "" : " (" + waiting + " DEAD from an earlier run wait for the Clone Capsule)") + ", "
                + waited + " ticks after the field came up; each bound to its own slot (kind, name, UUID)" + (bad.length() == 0 ? "" : " - NOT:" + bad));
    }

    /** A DEAD slot the Clone Capsule doesn't bring back yet (RosterSC's rule: its respawn time, a Tuner a game day per settlement). */
    private boolean waitsForClone(SettlementRegistrySC.Slot sl) {
        if (sl.state != SettlementRegistrySC.DEAD) {
            return false;
        }
        NBTTagCompound c = rec.extra.getCompoundTag(com.sc.settle.RosterSC.EXTRA);
        boolean tunerToday = c.hasKey(com.sc.settle.RosterSC.CLONE_DAY) && c.getLong(com.sc.settle.RosterSC.CLONE_DAY) == SettlementSC.day(w);
        NpcKindSC k = sl.kindOf();
        return w.getTotalWorldTime() - sl.diedAt < k.respawnTicks() || !k.guard && tunerToday;
    }

    /** 5: ПЖ-2 - untouchable in the belt, ordinary past it; ПЭ-1's Pulse refused. */
    private void villagers() {
        List<EntityTunerSC> ts = tuners();
        if (ts.isEmpty()) {
            print(false, "the settlers (package C / N, ПЖ-2): no Tuner of the settlement in the world");
            return;
        }
        EntityTunerSC t = ts.get(0);
        FakePlayer fake = fake();
        DamageSource src = DamageSource.causePlayerDamage(fake);
        boolean inBelt = MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(t, src, 4F));
        float h = t.getHealth();
        t.hurtResistantTime = 0;
        t.attackEntityFrom(src, 4F);
        boolean unhurt = t.getHealth() == h;
        double ox = t.posX, oy = t.posY, oz = t.posZ;
        int[] d = awayFromDock();
        t.setPosition(rec.x + 0.5D + d[0] * (rec.radius + SettlementSC.BELT + 10), oy, rec.z + 0.5D + d[1] * (rec.radius + SettlementSC.BELT + 10));
        boolean past = !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(t, src, 4F));
        t.setPosition(ox, oy, oz);
        int pulse = EmpMobSC.refusal(t, EmpMode.PULSE);
        print(inBelt && unhurt && past && pulse != EmpNetSC.OK, "the settlers (package C, ПЖ-2): a FakePlayer's blow at " + t.kind() + " in the dome"
                + " cancelled " + inBelt + ", its health kept " + unhurt + "; past the belt not cancelled " + past + "; a PE-1 Pulse at it refused " + (pulse != EmpNetSC.OK)
                + (pulse != EmpNetSC.OK ? "" : " (EmpMobSC.refusal answers OK for a settler: the Pulse would slow it)"));
    }

    /** 6: ПГ-2, ПГ-3 - the rank's armour and reserve; ПЭ-1 sees the armour; a FakePlayer's blow cancelled silently (ПП-9). */
    private void wardens() {
        List<EntityWardenSC> gs = guards();
        if (gs.isEmpty()) {
            print(false, "the guards (package N, ПГ-2): no guard of the settlement in the world");
            return;
        }
        StringBuilder bad = new StringBuilder();
        int sentries = 0, seniors = 0, chiefs = 0;
        FakePlayer fake = fake();
        for (EntityWardenSC g : gs) {
            boolean senior = g.kind() == NpcKindSC.SENIOR_WARDEN, captain = g.kind() == NpcKindSC.CAPTAIN, commandant = g.kind() == NpcKindSC.COMMANDANT;
            sentries += senior || captain || commandant ? 0 : 1;
            seniors += senior ? 1 : 0;
            chiefs += captain || commandant ? 1 : 0;
            // Ф3 (docs/plan-substrate-tech-f3.md §6.4, §6.5): the Captain's Exo with helium, the Commandant's Singular armour
            ArmorSuit want = commandant ? ArmorSuit.SINGULAR : captain ? ArmorSuit.EXO : senior ? ArmorSuit.QUANTUM : ArmorSuit.NANO;
            int pieces = 0;
            for (int t = 0; t < 4; t++) {
                ItemStack s = g.getEquipmentInSlot(4 - t);
                pieces += s != null && s.getItem() instanceof ItemArmorSC && ((ItemArmorSC) s.getItem()).getSuit() == want ? 1 : 0;
            }
            // 2026-10-18, ПГ-15: the rank's full reserve (a charged player set / the chiefs' own) - a guard saved before is refitted on loading
            float full = EntityWardenSC.reserveOf(g.kind()), max = g.mobArmor().reserveMax;
            boolean hp = Math.abs(g.getMaxHealth() - EntityWardenSC.hpOf(g.kind())) < 0.01D;
            boolean cancelled = MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(g, DamageSource.causePlayerDamage(fake), 4F));
            // ПГ-20: the Point, the Pulse and the Siphon all work on a guard now
            boolean works = EmpMobSC.refusal(g, EmpMode.POINT) == EmpNetSC.OK && EmpMobSC.refusal(g, EmpMode.PULSE) == EmpNetSC.OK
                    && EmpMobSC.refusal(g, EmpMode.SIPHON) == EmpNetSC.OK && EmpMobSC.ventsAll(g);
            if (pieces != 4 || (senior || captain) && !g.mobArmor().helium || Math.abs(max - full) > 0.01F || !hp || !g.mobArmor().pierceProof
                    || !g.empArmoured() || !cancelled || !works) {
                bad.append(' ').append(g.kind()).append(": ").append(pieces).append(" pieces of ").append(want).append(senior ? ", helium " + g.mobArmor().helium : "")
                        .append(", reserve ").append(max).append(" (").append(full).append("), HP ").append(g.getMaxHealth()).append(", pierce-proof ")
                        .append(g.mobArmor().pierceProof).append(", armoured ").append(g.empArmoured()).append(", a FakePlayer's blow cancelled ")
                        .append(cancelled).append(", Point / Pulse / Siphon allowed, the Siphon vented ").append(works);
            }
        }
        print(bad.length() == 0, "the guards (package N, ПГ-2, ПГ-3, ПГ-15, ПГ-20): " + sentries + " Sentries in Nano 4/4, " + seniors + " Senior Wardens in"
                + " Quantum 4/4 with helium" + (chiefs > 0 ? ", " + chiefs + " Ф3 chiefs (the Captain in Exo with helium, the Commandant in Singular armour)" : "")
                + ", the rank's reserve and HP, the plating pierce-proof, armoured for PE-1 - the Point, the Pulse and the Siphon (vented) all allowed, a"
                + " FakePlayer's blow in the belt cancelled" + (bad.length() == 0 ? "" : " - NOT:" + bad));
        empProvokes(gs.get(0));
    }

    /**
     * 6a (2026-10-18, ПГ-20): any ПЭ-1 use on a guard by a real player is an attack on it - a Pulse (the whole suit suppressed) and a
     * Siphon stream's second each put the guards after that player (GuardAlertSC.provoked); the test's players are forgotten after.
     * A Tuner still refuses every mode (NO_OWN).
     */
    private void empProvokes(EntityWardenSC g) {
        long now = w.getTotalWorldTime();
        WorldTestArmourerSC.StationPlayer pulser = new WorldTestArmourerSC.StationPlayer(w, new GameProfile(UUID.fromString(
                "5c0e7a1e-5e77-4000-8000-00000000f010"), "[SCTestPulser]"));
        WorldTestArmourerSC.StationPlayer drainer = new WorldTestArmourerSC.StationPlayer(w, new GameProfile(UUID.fromString(
                "5c0e7a1e-5e77-4000-8000-00000000f011"), "[SCTestDrainer]"));
        pulser.setLocationAndAngles(g.posX + 2, g.posY, g.posZ, 0F, 0F);
        drainer.setLocationAndAngles(g.posX - 2, g.posY, g.posZ, 0F, 0F);
        com.sc.settle.GuardAlertSC.forget(rec.id, pulser);
        com.sc.settle.GuardAlertSC.forget(rec.id, drainer);
        boolean before = com.sc.settle.GuardAlertSC.provoked(rec.id, pulser.getUniqueID(), now)
                || com.sc.settle.GuardAlertSC.provoked(rec.id, drainer.getUniqueID(), now);
        com.sc.emp.EmpSC.HitResult r = g.empHit(pulser, EmpMode.PULSE, false, new int[]{0, 1, 2, 3});
        int suppressed = 0;
        for (int i = 0; i < 4; i++) {
            suppressed += g.mobArmor().suppressed(i) ? 1 : 0;
        }
        boolean pulsed = com.sc.settle.GuardAlertSC.provoked(rec.id, pulser.getUniqueID(), now);
        g.empStreamSecond(drainer);
        boolean drained = com.sc.settle.GuardAlertSC.provoked(rec.id, drainer.getUniqueID(), now);
        com.sc.settle.GuardAlertSC.forget(rec.id, pulser);
        com.sc.settle.GuardAlertSC.forget(rec.id, drainer);
        boolean tunerRefuses = true;
        List<EntityTunerSC> ts = tuners();
        if (!ts.isEmpty()) {
            for (EmpMode m : EmpMode.values()) {
                tunerRefuses &= EmpMobSC.refusal(ts.get(0), m) == EmpNetSC.NO_OWN;
            }
        }
        print(!before && r.anyArmour && suppressed == 4 - countImmune(r) && pulsed && drained && tunerRefuses, "PE-1 on a guard (ПГ-20): a real player's"
                + " Pulse at " + g.kind() + " - " + suppressed + " pieces suppressed, the guards after him " + pulsed + "; a Siphon stream's second by"
                + " another - after him too " + drained + "; a Tuner refuses every mode (NO_OWN) " + tunerRefuses + (ts.isEmpty() ? " (no Tuner loaded)" : ""));
    }

    private static int countImmune(com.sc.emp.EmpSC.HitResult r) {
        int n = 0;
        for (boolean b : r.immune) {
            n += b ? 1 : 0;
        }
        return n;
    }

    /** 7a: a Tuner killed (not by a player) - its slot DEAD; its time put in the past for the Clone Capsule. */
    private void killFirstTuner() {
        List<EntityTunerSC> ts = tuners();
        if (ts.size() < 2) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: the Clone Capsule - fewer than 2 Tuners in the world");
            return;
        }
        EntityTunerSC t = ts.get(ts.size() - 1).kind() != NpcKindSC.ELDER ? ts.get(ts.size() - 1) : ts.get(0);
        tunerSlot = t.slot();
        tunerWas = t.getUniqueID();
        // a run earlier this game day may have used the day's one Tuner (package C keeps it in extra "C", RosterSC.CLONE_DAY);
        // the Tuners an earlier run left DEAD wait a day from now, so the capsule's one copy goes to this test's victim
        long now = w.getTotalWorldTime();
        for (SettlementRegistrySC.Slot other : rec.slots) {
            if (other.state == SettlementRegistrySC.DEAD && !other.kindOf().guard) {
                other.diedAt = now;
            }
        }
        rec.extra.getCompoundTag(com.sc.settle.RosterSC.EXTRA).removeTag(com.sc.settle.RosterSC.CLONE_DAY);
        kill(t);
        SettlementRegistrySC.Slot sl = reg.slot(rec.id, tunerSlot);
        boolean dead = sl != null && sl.state == SettlementRegistrySC.DEAD && sl.uuid() == null;
        print(dead, "the Clone Capsule (package N / C, ПП-13): a " + t.kind() + " killed - slot " + tunerSlot + " DEAD " + dead);
        if (sl != null) {
            sl.diedAt = w.getTotalWorldTime() - NpcKindSC.TUNER_RESPAWN_TICKS - 40L;
            reg.markDirty();
        }
    }

    /** The first Tuner's slot back with a new creature, or null. */
    private Entity tunerBack() {
        if (tunerSlot < 0) {
            return null;
        }
        SettlementRegistrySC.Slot sl = reg.slot(rec.id, tunerSlot);
        if (sl == null || sl.state != SettlementRegistrySC.ALIVE || sl.uuid() == null || sl.uuid().equals(tunerWas)) {
            return null;
        }
        for (Entity e : members()) {
            if (e.getUniqueID().equals(sl.uuid())) {
                return e;
            }
        }
        return null;
    }

    private void firstBack(int waited) {
        if (tunerSlot < 0) {
            return;
        }
        Entity e = tunerBack();
        int[] clone = point(SettlementRegistrySC.P_CLONE);
        double dist = e == null ? -1D : Math.sqrt(e.getDistanceSq(clone[0] + 0.5D, clone[1], clone[2] + 0.5D));
        print(e != null && dist <= 8D, "the Clone Capsule: with no player within " + SettlementSC.RESPAWN_CLEAR + " of the core and its time past, slot "
                + tunerSlot + " is back " + (e != null) + " after " + waited + " ticks, " + (e == null ? "-" : String.valueOf(Math.round(dist)))
                + " blocks from the capsule (<= 8)");
    }

    /** 7b: a second Tuner the same day, and a guard. */
    private void killMore() {
        List<EntityTunerSC> ts = tuners();
        for (EntityTunerSC t : ts) {
            if (t.slot() != tunerSlot && t.kind() != NpcKindSC.ELDER) {
                tuner2Slot = t.slot();
                kill(t);
                break;
            }
        }
        List<EntityWardenSC> gs = guards();
        if (!gs.isEmpty()) {
            guardSlot = gs.get(0).slot();
            kill(gs.get(0));
        }
        long now = w.getTotalWorldTime();
        SettlementRegistrySC.Slot t2 = tuner2Slot < 0 ? null : reg.slot(rec.id, tuner2Slot), g = guardSlot < 0 ? null : reg.slot(rec.id, guardSlot);
        if (t2 != null) {
            t2.diedAt = now - NpcKindSC.TUNER_RESPAWN_TICKS - 40L;
        }
        if (g != null) {
            g.diedAt = now - NpcKindSC.GUARD_RESPAWN_TICKS - 40L;
        }
        reg.markDirty();
        killDay = SettlementSC.day(w);
    }

    private void moreBack() {
        SettlementRegistrySC.Slot t2 = tuner2Slot < 0 ? null : reg.slot(rec.id, tuner2Slot), g = guardSlot < 0 ? null : reg.slot(rec.id, guardSlot);
        boolean sameDay = SettlementSC.day(w) == killDay;
        if (t2 != null) {
            boolean waits = t2.state == SettlementRegistrySC.DEAD;
            if (sameDay) {
                print(waits, "the Clone Capsule: a second Tuner killed the same game day stays DEAD (one a day per settlement) " + waits);
            } else {
                System.out.println("[SC-WORLDTEST] SKIP settlement: the second Tuner - a new game day began during the wait");
            }
        }
        if (g != null) {
            boolean back = g.state == SettlementRegistrySC.ALIVE && g.uuid() != null;
            print(back, "the Clone Capsule: a guard killed, its " + NpcKindSC.GUARD_RESPAWN_TICKS / 1200 + " minutes past - back " + back + " within "
                    + RESPAWN_WAIT + " ticks");
        }
    }

    private static void kill(EntityLivingBase e) {
        e.hurtResistantTime = 0;
        e.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
        if (e.isEntityAlive()) {
            e.setHealth(0F);
            e.onDeath(DamageSource.outOfWorld);
        }
    }

    // ------------------------------------------------------------------ 8. the trade (package T, §9.1)

    private void trade() {
        Method execute;
        try {
            execute = TradeServerSC.class.getMethod("execute", EntityPlayer.class, EntityTunerSC.class, String.class, int.class);
        } catch (NoSuchMethodException e) {
            print(false, "the trade (package T, §9.1): TradeServerSC.execute(EntityPlayer, EntityTunerSC, String, int) is missing");
            return;
        }
        TradeOfferSC sell = null, buy = null;
        EntityTunerSC at = null, buyAt = null;
        for (EntityTunerSC t : tuners()) {
            for (TradeOfferSC o : TradeTableSC.offers(t.kind())) {
                if (sell == null && o.sell() && o.minLevel <= ReputationSC.STRANGER && plain(o)) {
                    sell = o;
                    at = t;
                }
                if (buy == null && o.buy() && o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && !o.out.isChips() && o.minLevel <= ReputationSC.STRANGER) {
                    buy = o;
                    buyAt = t;
                }
            }
        }
        if (sell == null) {
            print(false, "the trade: no Tuner here offers a sale of goods for chips to a Stranger (" + tuners().size() + " Tuners)");
            return;
        }
        FakePlayer p = FakePlayerFactory.get(w, TRADER);
        UUID id = p.getUniqueID();
        long today = SettlementSC.day(w);
        SettlementRegistrySC.PlayerRec pr = reg.player(id);
        pr.reputation = 0;
        pr.rollDay(today);
        pr.chips = pr.repTrades = 0;
        SettlementRegistrySC.LocalRec lr = rec.local(id);
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        p.setLocationAndAngles(at.posX, at.posY, at.posZ, 0F, 0F);
        clear(p);
        int level = ReputationSC.level(0);
        for (TradeOfferSC.Ingredient i : sell.in) {
            give(p, i.display(), TradeRulesSC.playerSide(i.count, level, rec.seed, sell.key) * (sell.limit + 1));
        }
        boolean each = true;
        StringBuilder codes = new StringBuilder();
        for (int n = 0; n < sell.limit; n++) {
            int[] before = counts(p, sell);
            codes.append(call(execute, p, at, sell.key)).append(' ');
            int[] after = counts(p, sell);
            for (int i = 0; i < sell.in.length; i++) {           // +1 a deal keeps a fresh player a Stranger all the way
                each &= before[i] - after[i] == TradeRulesSC.playerSide(sell.in[i].count, level, rec.seed, sell.key);
            }
            each &= after[sell.in.length] - before[sell.in.length] == sell.out.count;
        }
        int counted = used(p, at, sell.key);
        int[] before = counts(p, sell);
        int over = call(execute, p, at, sell.key);
        boolean limit = Arrays.equals(before, counts(p, sell));
        print(each && counted == sell.limit && limit, "the trade (package T, ПО-1, ПО-4): " + sell.key + " at a " + at.kind() + " by a FakePlayer (no window,"
                + " no packets) " + sell.limit + " times - the inputs taken, " + sell.out.count + " chips given each time " + each + " (codes " + codes.toString().trim()
                + "), the day's counter " + counted + " (" + sell.limit + "); once more - refused, nothing taken " + limit + " (code " + over + ")");

        // ПД-1: a Violator buys nothing
        boolean noBuy = true;
        if (buy != null) {
            pr.reputation = -10;
            clear(p);
            give(p, SettlementItemsSC.chips(1), 64);
            p.setLocationAndAngles(buyAt.posX, buyAt.posY, buyAt.posZ, 0F, 0F);
            int[] b0 = counts(p, buy);
            int code = call(execute, p, buyAt, buy.key);
            noBuy = Arrays.equals(b0, counts(p, buy));
            print(noBuy, "the trade (ПД-1): a Violator (-10) asking " + buy.key + " - refused, nothing taken " + noBuy + " (code " + code + ")");
            pr.reputation = 0;
        } else {
            System.out.println("[SC-WORLDTEST] SKIP settlement: the Violator's refusal - no Tuner here sells goods for chips to a Stranger");
        }

        // ПО-5: 63 chips given out today - a deal that would give more is refused
        clear(p);
        pr.rollDay(today);
        pr.chips = TradeRulesSC.CHIPS_PER_DAY - 1;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        p.setLocationAndAngles(at.posX, at.posY, at.posZ, 0F, 0F);
        for (TradeOfferSC.Ingredient i : sell.in) {
            give(p, i.display(), TradeRulesSC.playerSide(i.count, ReputationSC.level(pr.reputation), rec.seed, sell.key));
        }
        int[] c0 = counts(p, sell);
        int code = call(execute, p, at, sell.key);
        boolean capped = sell.out.count <= 1 || Arrays.equals(c0, counts(p, sell)) && pr.chips == TradeRulesSC.CHIPS_PER_DAY - 1;
        print(capped, "the trade (ПО-5): " + (TradeRulesSC.CHIPS_PER_DAY - 1) + " chips given out today, a deal of " + sell.out.count + " more - refused,"
                + " nothing taken " + capped + " (code " + code + ")");
        clear(p);
    }

    /** Every input an item (not chips) the test can hold; not a matcher (Ф3: the Salvager's trophy - a side never scaled). */
    private static boolean plain(TradeOfferSC o) {
        for (TradeOfferSC.Ingredient i : o.in) {
            if (i == null || i.isChips() || i.display() == null || i.any()) {
                return false;
            }
        }
        return o.out != null && o.out.isChips();
    }

    /** The counts of each input and (last) of the chips in `p`'s inventory. */
    private static int[] counts(EntityPlayer p, TradeOfferSC o) {
        int[] n = new int[o.in.length + 1];
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null) {
                continue;
            }
            for (int i = 0; i < o.in.length; i++) {
                if (o.in[i] != null && !o.in[i].isChips() && o.in[i].matches(s)) {
                    n[i] += s.stackSize;
                }
            }
            if (SettlementItemsSC.creditChip != null && s.getItem() == SettlementItemsSC.creditChip) {
                n[o.in.length] += s.stackSize;
            }
        }
        return n;
    }

    private static void give(EntityPlayer p, ItemStack s, int count) {
        if (s == null) {
            return;
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && count > 0; i++) {
            if (inv[i] == null) {
                ItemStack c = s.copy();
                c.stackSize = Math.min(count, c.getMaxStackSize());
                inv[i] = c;
                count -= c.stackSize;
            }
        }
    }

    private static void clear(EntityPlayer p) {
        if (p != null) {
            for (int i = 0; i < p.inventory.mainInventory.length; i++) {
                p.inventory.mainInventory[i] = null;
            }
        }
    }

    /** How many times `p` made `key` at `t` today: TradeServerSC.usedNow (package T) if it has one, else the registry's LocalRec counter. */
    private int used(EntityPlayer p, EntityTunerSC t, String key) {
        try {
            Object r = TradeServerSC.class.getMethod("usedNow", EntityPlayer.class, EntityTunerSC.class, String.class).invoke(null, p, t, key);
            if (r instanceof Number) {
                return ((Number) r).intValue();
            }
        } catch (Exception e) {
            // no such helper: the counter itself
        }
        return rec.local(p.getUniqueID()).count(key);
    }

    /** TradeServerSC.execute(p, t, key, 1)'s code (an exception: Integer.MIN_VALUE, printed). */
    private static int call(Method m, EntityPlayer p, EntityTunerSC t, String key) {
        try {
            Object r = m.invoke(null, p, t, key, Integer.valueOf(1));
            return r instanceof Number ? ((Number) r).intValue() : 0;
        } catch (Exception e) {
            System.out.println("[SC-WORLDTEST] INFO settlement: TradeServerSC.execute threw " + (e.getCause() != null ? e.getCause() : e));
            return Integer.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ 9. the bridge (package B, §8.2)

    private void bridge() {
        WorldServer over = DimensionManager.getWorld(0);
        if (over == null) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: the bridge - no Overworld");
            return;
        }
        TileEntityBridgeControllerSC c = new TileEntityBridgeControllerSC();
        c.setWorldObj(over);
        ChunkCoordinates sp = over.getSpawnPoint();
        c.xCoord = sp.posX;
        c.yCoord = 100;
        c.zCoord = sp.posZ;
        FakePlayer p = fake();
        int[] dock = rec.hasDock ? c.famInfo(p, dim, rec.dockX + 3, rec.dockZ, false, false, false) : null;
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        pr.hasDeath = true;
        pr.deathDim = dim;
        pr.deathX = rec.x + 300;
        pr.deathY = plan.floorY + 1;
        pr.deathZ = rec.z;
        pr.deathAt = w.getTotalWorldTime();
        int[] mark = c.famInfo(p, dim, rec.x + 303, rec.z, false, false, false);
        pr.clearDeath();
        boolean snap = !rec.hasDock || reg.dockNear(dim, rec.dockX + 3, rec.dockZ, TravelRulesSC.DOCK_SNAP) == rec;
        boolean ok = (dock == null || dock[2] == 0) && mark[2] >= TravelRulesSC.DEATH_SCATTER && snap;
        print(ok, "the bridge (package B, ПР-2, ПР-6): a target 3 from the dock's beacon - scatter " + (dock == null ? "- (no dock)" : String.valueOf(dock[2]))
                + " (0), the registry's dockNear " + snap + "; a target 3 from the player's death mark - scatter " + mark[2] + " (>= " + TravelRulesSC.DEATH_SCATTER + ")");
    }

    /**
     * 9b (integrator, ПР-2, ПР-5): a bridge end under the dome is refused with the nearest dock's landing point, on the
     * dock it may open; the Cartographer's Settlement Map holds at most MAP_DOCKS docks of this world, nearest first, this
     * settlement's own one first.
     */
    private void travel() {
        FakePlayer p = fake();
        int[] sq = point(SettlementRegistrySC.P_SQUARE);
        com.sc.bridge.BridgeMsgSC in = TileEntityBridgeControllerSC.endRefusal(w, p, sq[0], plan.floorY + 1, sq[2]);
        List<SettlementRegistrySC.Settlement> near = reg.nearestDocks(dim, sq[0] + 0.5D, sq[2] + 0.5D, 1, -1);
        String want = near.isEmpty() ? "sc.bridge.place.settlementNoDock" : "sc.bridge.place.settlement";
        String[] args = near.isEmpty() ? new String[0] : new String[]{String.valueOf(near.get(0).dockX), String.valueOf(Math.min(255, near.get(0).dockY + 1)),
                String.valueOf(near.get(0).dockZ)};
        boolean inside = in != null && want.equals(in.key) && Arrays.equals(args, in.args);
        com.sc.bridge.BridgeMsgSC onDock = rec.hasDock ? TileEntityBridgeControllerSC.endRefusal(w, p, rec.dockX, rec.dockY + 1, rec.dockZ) : null;
        ItemStack map = com.sc.settle.travel.ItemSettlementMapSC.make(reg, rec);
        net.minecraft.nbt.NBTTagList docks = com.sc.settle.travel.ItemSettlementMapSC.docks(map);
        boolean mapOk = docks.tagCount() <= TravelRulesSC.MAP_DOCKS && (!rec.hasDock || docks.tagCount() > 0 && docks.getCompoundTagAt(0).getInteger("id") == rec.id);
        double last = -1D;
        for (int i = 0; i < docks.tagCount(); i++) {
            NBTTagCompound e = docks.getCompoundTagAt(i);
            double dx = e.getInteger("x") - rec.x, dz = e.getInteger("z") - rec.z, d = dx * dx + dz * dz;
            mapOk &= e.getInteger("dim") == dim && d >= last - 1e-6;
            last = d;
        }
        print(inside && onDock == null && mapOk, "travel (package B, ПР-2, ПР-5): a bridge end on the square refused - " + (in == null ? "null" : in.key
                + " " + Arrays.toString(in.args)) + " (" + want + " " + Arrays.toString(args) + ") " + inside + "; on the dock "
                + (rec.hasDock ? (onDock == null ? "allowed" : "refused " + onDock.key) : "- (no dock)") + "; the Settlement Map " + docks.tagCount()
                + " docks (<= " + TravelRulesSC.MAP_DOCKS + ", its own first, nearest first, this world) " + mapOk);
    }

    /**
     * 2c (integrator, ПП-6, ПП-12): the core can't be broken (a FakePlayer's BreakEvent cancelled); every profession of
     * the roster has its workshop's showcase machines in the settlement, each with its tile, and the Clone Capsule stands.
     */
    private void showcases() {
        FakePlayer fake = fake();
        boolean coreOff = MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(plan.coreX, plan.coreY, plan.coreZ, w, SettlementBlocksSC.core, 0, fake));
        int[] fc = SettlementLayoutSC.footprintChunks(plan);
        int[] count = new int[16], count2 = new int[16];
        int noTile = 0, trim = 0, real = 0;
        for (int cx = fc[0]; cx <= fc[2]; cx++) {
            for (int cz = fc[1]; cz <= fc[3]; cz++) {
                Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = Math.max(0, plan.floorY - 8); y < Math.min(256, plan.floorY + 24); y++) {
                            Block b = ch.getBlock(x, y, z);
                            if (b == SettlementBlocksSC.showcase) {
                                count[ch.getBlockMetadata(x, y, z) & 15]++;
                                noTile += w.getTileEntity(cx * 16 + x, y, cz * 16 + z) instanceof TileEntityShowcaseSC ? 0 : 1;
                            } else if (SettlementBlocksSC.showcase2 != null && b == SettlementBlocksSC.showcase2) {
                                count2[ch.getBlockMetadata(x, y, z) & 15]++;    // Ф3: the Power Engineer's and the Salvager's machines
                            } else if (b == SettlementBlocksSC.decor || b == SettlementBlocksSC.glass) {
                                trim++;
                            } else if (b == ModBlocks.metalBlock || b == ModBlocks.metalBlock2 || b == ModBlocks.leadGlass || b == ModBlocks.tokamakCoil) {
                                real++;
                            }
                        }
                    }
                }
            }
        }
        if (trim == 0) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: the trim (§4.5) - this settlement was built before the trim existed (" + real
                    + " real metal / lead glass / coil blocks in it)");
        } else {
            print(real == 0, "the trim (§4.5, ПП-7): " + trim + " blocks of the settlement's metal, coils and lead glass are the trim, " + real
                    + " of the real ones (0)");
        }
        StringBuilder missing = new StringBuilder();
        Set<NpcKindSC> kinds = new HashSet<NpcKindSC>(SettlementLayoutSC.roster(site));
        int second = showcase2From();
        boolean upgraded = rec.extra.getCompoundTag("S").hasKey("F3From");
        for (NpcKindSC k : kinds) {
            if (upgraded && (k == NpcKindSC.POWER_ENGINEER || k == NpcKindSC.SALVAGER)) {
                continue;                                   // a Ф2 settlement upgraded in Ф3: its new Tuners have no workshop (ПЖ-10)
            }
            for (int m : SettlementLayoutSC.workshopMachines(k)) {
                boolean two = m >= second;
                int meta = two ? m - second : m;
                String name = two ? "showcase2." + com.sc.block.BlockShowcase2SC.nameOf(meta)
                        : com.sc.block.BlockShowcaseSC.NAMES[Math.max(0, Math.min(com.sc.block.BlockShowcaseSC.NAMES.length - 1, meta))];
                if ((two ? count2[meta & 15] : count[meta & 15]) == 0 && missing.indexOf(name) < 0) {
                    missing.append(' ').append(name);
                }
            }
        }
        if (count[com.sc.block.BlockShowcaseSC.CLONE_CAPSULE] == 0) {
            missing.append(" cloneCapsule");
        }
        int all = 0;
        for (int n : count) {
            all += n;
        }
        print(coreOff && noTile == 0 && missing.length() == 0, "the core and the showcases (package C / S, ПП-6, ПП-12): a FakePlayer's break of the core"
                + " cancelled " + coreOff + "; " + all + " showcase machines (" + count[com.sc.block.BlockShowcaseSC.SHOWER] + " showers, "
                + count[com.sc.block.BlockShowcaseSC.CHARGE_PAD] + " charge pads, " + count[com.sc.block.BlockShowcaseSC.CLONE_CAPSULE] + " Clone Capsules), "
                + noTile + " without their tile; every workshop of the roster has its machines" + (missing.length() == 0 ? " true" : " - MISSING:" + missing));
    }

    /**
     * Ф6 (docs/plan-substrate-tech-f6.md §2.5, ПЖ-11): a Substrate record's last slot is the Dispatcher RosterSC.addDispatcher appended
     * at its core's first second (extra "S" F6Disp) - not a slot of the plan's roster: left out of `kinds` before they are compared.
     */
    static void withoutF6Dispatcher(SettlementRegistrySC.Settlement rec, List<NpcKindSC> kinds) {
        if (rec != null && !kinds.isEmpty() && kinds.get(kinds.size() - 1) == NpcKindSC.DISPATCHER
                && rec.extra.getCompoundTag(com.sc.settle.RosterSC.EXTRA_S).getBoolean(com.sc.settle.RosterSC.F6_DISPATCHER)) {
            kinds.remove(kinds.size() - 1);
        }
    }

    /** Ф3: SettlementLayoutSC.SHOWCASE2 (package S) - a workshopMachines code from it on is a BlockShowcase2SC meta; 16 until S has it. */
    private static int showcase2From() {
        try {
            return SettlementLayoutSC.class.getField("SHOWCASE2").getInt(null);
        } catch (Exception e) {
            return 16;
        }
    }

    // ------------------------------------------------------------------ 10. the spawn director (package C, ПП-11)

    private void spawning() {
        Method spot;
        try {
            spot = SpawnDirectorSC.class.getDeclaredMethod("spotAt", World.class, int.class, int.class);
            spot.setAccessible(true);
        } catch (NoSuchMethodException e) {
            System.out.println("[SC-WORLDTEST] SKIP settlement: SpawnDirectorSC.spotAt(World, int, int) is gone - the margin is the director's own business");
            return;
        }
        int[] d = awayFromDock();
        int[] at = {0, rec.radius + 10, rec.radius + SettlementSC.SPAWN_MARGIN - 1};
        StringBuilder found = new StringBuilder();
        for (int dist : at) {
            int x = rec.x + d[0] * dist, z = rec.z + d[1] * dist;
            w.getChunkFromChunkCoords(x >> 4, z >> 4);
            if (spotAt(spot, x, z) != null) {
                found.append(' ').append(dist);
            }
        }
        w.getChunkFromChunkCoords(F1_X >> 4, F1_Z >> 4);
        int[] far = null;
        for (int i = 0; i < 16 && far == null; i++) {
            far = spotAt(spot, F1_X + i, F1_Z + i);
        }
        print(found.length() == 0, "the spawn director (package C, ПП-11): no spot at the core, " + (rec.radius + 10) + " and " + (rec.radius + SettlementSC.SPAWN_MARGIN - 1)
                + " from it (the margin " + SettlementSC.SPAWN_MARGIN + " past the edge)" + (found.length() == 0 ? "" : " - FOUND at:" + found) + "; a spot far off "
                + (far != null ? "found" : "none (the terrain there?)"));
    }

    private int[] spotAt(Method m, int x, int z) {
        try {
            return (int[]) m.invoke(null, w, Integer.valueOf(x), Integer.valueOf(z));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------

    private FakePlayer fake() {
        return FakePlayerFactory.get(w, FAKE);
    }

    /** Public static number `field` of `c`, or `plan` while the class has none (package N's constants). */
    private static float num(Class<?> c, String field, float plan) {
        try {
            Object v = c.getField(field).get(null);
            return v instanceof Number ? ((Number) v).floatValue() : plan;
        } catch (Throwable t) {
            return plan;
        }
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " settlement: " + what);
    }

    /** The chunks loaded in the watched world while it listens (ChunkEvent.Load: generated or read from disk alike). */
    public static final class ChunkWatch {
        private World world;
        final Set<Long> loaded = new HashSet<Long>();

        void start(World w) {
            world = w;
            loaded.clear();
        }

        @SubscribeEvent
        public void onLoad(ChunkEvent.Load e) {
            if (e.world == world) {
                Chunk c = e.getChunk();
                loaded.add(Long.valueOf((long) c.xPosition << 32 ^ c.zPosition & 0xFFFFFFFFL));
            }
        }
    }
}
