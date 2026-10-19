package com.sc.debug;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.arena.ArenaBlocksSC;
import com.sc.arena.ArenaDataSC;
import com.sc.arena.ArenaLayoutSC;
import com.sc.arena.ArenaSC;
import com.sc.arena.ArenaWorldSC;
import com.sc.arena.MapGenArenaSC;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateSignalSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpModule;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.finale.FinaleItemsSC;
import com.sc.finale.FinaleRulesSC;
import com.sc.init.ModItems;
import com.sc.item.SingularProgressSC;
import com.sc.lore.JournalSC;
import com.sc.lore.SignalDataSC;
import com.sc.lore.SignalRulesSC;
import com.sc.lore.WreckLayoutSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityContainmentCoilSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.mob.TrophyTableSC;
import com.sc.mob.WardenRulesSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.StructureRuinStartSC;
import com.sc.settle.CrumbLimitSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.caravan.CaravanRulesSC;
import com.sc.settle.caravan.CaravanRunSC;
import com.sc.settle.caravan.CaravanSC;
import com.sc.settle.caravan.EntityCaravanGuardSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * PACKAGE D (docs/plan-substrate-tech-f5.md §11.2): Ф5's world test on a real server (-Dsc.worldtest=true, after WorldTestWildSC is
 * finished). Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO finale: ...". SKIP when the dimension is off.
 * <ol>
 * <li>registration: the four new names in EntityList, the structures SCSi14Arena / SCSi14Wreck, the block arenaSC and its TE, the five
 * items, the relics' metas 3..4;</li>
 * <li>the arena: the nearest built (or fresh) one to the spawn generated - the floor's plating on floorY over the disk, air above it,
 * the wall, the Console with its record READY and set up, 4 pedestals, 8 columns, no Blocks.stone in the box, no Dead Node's start on
 * the crossing, no cascade; a start made again where its chunks exist - its record decides (built: the pieces), the record kept; an arena whose ground was made first
 * (its start held off while one chunk was made) - EMPTY and an empty start;</li>
 * <li>the Console: on Peaceful - refused, the key kept; with a key (a test player's hand) - after the countdown FIGHT, the Warden by the
 * middle and 4 coils on the pedestals, the key in the record, gone from the hand; a second click - busy (a second key kept);</li>
 * <li>the fight on that Warden: a blow with a coil lit - the health kept; a Point into each coil - DARK; all dark - the window, a blow
 * gets through; the window over - every coil lit again; 59% - phase 2, the coils out, a crack opens and is recorded; 24% - phase 3,
 * an overheated window - the blow whole into the health; the core reset - a test player behind a column untouched, one in the open
 * hit (the highlight's x1.5: in game);</li>
 * <li>the reset: lastPlayerAt set back RESET_AWAY_TICKS - KEYED, the Warden and its coils gone, the cracks closed to the floor, the
 * key still in; a click without a key - the fight again;</li>
 * <li>the loot of that second Warden (WardenLootSC.award with the shares of three registry records - 60% / 40% / 5%, none online):
 * the two kept PENDING, the third nothing; delivered to the first (the Matrix, 4..8 Crumbs past the cap - CrumbLimitSC.left the same,
 * the Clots, the scrap, the first kill's Journal №12); the same day again - the cooldown; three days on - a loot without a Journal;
 * wardenDefeated counts; bossDied - READY, the key spent;</li>
 * <li>a wreck: the nearest wreck site generated - Burnt Coils in its ring, its chest with a Journal №9 / №10;</li>
 * <li>the Signal (package J's startNow / cacheNow / endNow): on - the record; a test player near - the chest with Journal №11 and
 * the patrol born in Si-14, a second time nothing; ended - off;</li>
 * <li>a caravan at the First Dock (package K's startNow / ambushNow / stepNow): master + 2 guards past the dome's edge, bound to the
 * dock; the ambush (Renegades born in Si-14) killed - +20 Trust to the test's FakePlayer defender, no rare deal for it; the rare deal
 * of a real-type dealer - the Armourer's «Широкий канал» once, then refused; the master killed - lost, nothing;</li>
 * <li>the deals (the coordinator's): a loaded Cartographer's Arena Coordinator once a day (then R_LIMIT); a loaded Elder's «Наладчик»
 * (the Crumbs paid leave the day's Crumb cap alone) and the Lithographers' Module twice (no limit);</li>
 * <li>requireSignal: on - the bridge's list for a viewer without the record lacks the Substrate and an end there is refused; the
 * Navigation Record used (package P's item) - there; a bridge in the Substrate - always; the rule back;</li>
 * <li>the Lithographers' Module: a field's upkeep halved (up) with it in a slot, unchanged without;</li>
 * <li>the ПЭ-1М trophy: Singular-level - refused at the Armourer and the plain station, the Singular station's; restored a ПЭ-1М
 * with no modules and no charge (the station's two clicks: in game);</li>
 * <li>the progress: an armoured native with a piece suppressed is suppressedArmoured (the kill itself needs a real player: in game);
 * the armoured enemies are IMob (C_KILLS counts them);</li>
 * <li>the regression of Ф1..Ф4's world tests - their own lines in the log (this test begins only after WorldTestWildSC).</li>
 * </ol>
 * Built on the core alone: the packages' own helpers (A's tick / counting, B's fight state, WardenLootSC's award, J's and K's
 * startNow) are asked for by name - a missing one fails its own lines. The test players are never spawned (listed in their chunk
 * where a search must find them); every ticket released, every block placed removed, the test's creatures and items removed. What the
 * world generated stays (the test world's arena, wreck, the EMPTY record of the held-off arena). Ф6: WorldTestEmbassySC waits for
 * `finished` (set wherever this test is done - the SKIP, a missing world, the end).
 */
public final class WorldTestFinaleSC {

    /** Never before this; and after WorldTestWildSC is finished. */
    static final int START = WorldTestWildSC.START + 600;
    /** Waits this long for WorldTestWildSC, then begins anyway (INFO). */
    private static final int WAIT_MAX = 72000;
    private static final int TIMEOUT = 4000;
    /** The schedule (ticks from the start). */
    private static final int T_CONSOLE = 2, T_CARAVAN = 40, T_FIGHT = ArenaWorldSC.COUNTDOWN_TICKS + 40, T_WINDOW = T_FIGHT + 30,
            T_RELIT = T_WINDOW + 40, T_PHASE2 = T_RELIT + 30, T_CRACK = T_PHASE2 + 30, T_PHASE3 = T_CRACK + 30, T_RESET = T_PHASE3 + 20,
            T_RESET_CHECK = T_RESET + 60, T_LOOT = T_RESET_CHECK + ArenaWorldSC.COUNTDOWN_TICKS + 40, T_END = T_LOOT + 40;
    private static final Object NONE = new Object();
    private static final String A_WORLD = "com.sc.arena.ArenaWorldSC", LOOT = "com.sc.mob.WardenLootSC", SIGNAL = "com.sc.lore.SignalSC";

    /** Ф6: the test is over (or skipped) - WorldTestEmbassySC begins after it. */
    static volatile boolean finished;

    private int ticks, startAt, next;
    private boolean done, started;
    private WorldServer w;
    private long seed;
    private final List<Step> steps = new ArrayList<Step>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final List<int[]> placed = new ArrayList<int[]>();
    private final List<Entity> made = new ArrayList<Entity>(), listed = new ArrayList<Entity>();
    private final Set<String> missing = new TreeSet<String>();
    /** The arena under test, its middle, floor, Console. */
    private ArenaDataSC.Arena arena;
    private int ax, az, floorY, kx, ky, kz;
    private WorldTestArmourerSC.StationPlayer keyHolder, second;
    private EntityBreakdownWardenSC boss;
    private float hpBefore;
    private long fightBefore;
    /** The caravan's settlement and defender. */
    private SettlementRegistrySC.Settlement dock;
    private WorldTestCombatSC.HurtablePlayer defender;

    private abstract static class Step {
        final int at;
        final String name;

        Step(int at, String name) {
            this.at = at;
            this.name = name;
        }

        abstract void run();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START || !WorldTestWildSC.finished && ticks < START + WAIT_MAX) {
            return;
        }
        try {
            if (!started) {
                started = true;
                startAt = ticks;
                if (!WorldTestWildSC.finished) {
                    System.out.println("[SC-WORLDTEST] INFO finale: WorldTestWildSC never finished - beginning anyway");
                }
                start();
            }
            while (!done && next < steps.size() && ticks - startAt >= steps.get(next).at) {
                Step s = steps.get(next++);
                try {
                    s.run();
                } catch (Throwable t) {
                    print(false, s.name + " - exception " + t);
                    t.printStackTrace();
                }
            }
            if (!done && (next >= steps.size() || ticks - startAt > TIMEOUT)) {
                if (next < steps.size()) {
                    print(false, "timed out before " + steps.get(next).name);
                }
                finish();
            }
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
            finish();
        }
    }

    // ------------------------------------------------------------------ the schedule

    private void start() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the dimension is off (" + SubstrateSC.offReason() + ")");
            done = true;
            finished = true;
            return;
        }
        w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        if (w == null) {
            print(false, "the Substrate's world not there");
            done = true;
            finished = true;
            return;
        }
        seed = w.getSeed();
        step(0, "registration", new Runnable() {
            public void run() {
                registration();
            }
        });
        step(0, "arena", new Runnable() {
            public void run() {
                arenaMake();
            }
        });
        step(0, "requireSignal", new Runnable() {
            public void run() {
                requireSignal();
            }
        });
        step(0, "module", new Runnable() {
            public void run() {
                module();
            }
        });
        step(0, "pe1m", new Runnable() {
            public void run() {
                pe1m();
            }
        });
        step(0, "progress", new Runnable() {
            public void run() {
                progress();
            }
        });
        step(0, "wreck", new Runnable() {
            public void run() {
                wreck();
            }
        });
        step(0, "signal", new Runnable() {
            public void run() {
                signal();
            }
        });
        step(0, "caravan load", new Runnable() {
            public void run() {
                caravanLoad();
            }
        });
        step(T_CONSOLE, "console", new Runnable() {
            public void run() {
                console();
            }
        });
        step(T_CARAVAN, "caravan", new Runnable() {
            public void run() {
                caravan();
            }
        });
        step(T_CARAVAN + 20, "deals", new Runnable() {
            public void run() {
                deals();
            }
        });
        step(T_FIGHT, "fight up", new Runnable() {
            public void run() {
                fightUp();
            }
        });
        step(T_WINDOW, "fight window", new Runnable() {
            public void run() {
                fightWindow();
            }
        });
        step(T_RELIT, "fight relit", new Runnable() {
            public void run() {
                fightRelit();
            }
        });
        step(T_PHASE2, "fight phase 2", new Runnable() {
            public void run() {
                fightPhase2();
            }
        });
        step(T_CRACK, "fight cracks", new Runnable() {
            public void run() {
                fightCracks();
            }
        });
        step(T_PHASE3, "fight phase 3", new Runnable() {
            public void run() {
                fightPhase3();
            }
        });
        step(T_RESET, "reset", new Runnable() {
            public void run() {
                resetBegin();
            }
        });
        step(T_RESET_CHECK, "reset check", new Runnable() {
            public void run() {
                resetCheck();
            }
        });
        step(T_LOOT, "loot", new Runnable() {
            public void run() {
                loot();
            }
        });
        step(T_END, "regression", new Runnable() {
            public void run() {
                System.out.println("[SC-WORLDTEST] INFO finale: the regression of Ф1..Ф4 (WorldTestEmpSC, WorldTestSubstrateSC, WorldTestSettlementSC,"
                        + " WorldTestCombatSC, WorldTestArmourerSC, WorldTestHazardSC, WorldTestWildSC) - their own lines above"
                        + (missing.isEmpty() ? "" : "; the packages' helpers asked for and not found: " + missing));
            }
        });
    }

    private void step(int at, final String name, final Runnable r) {
        steps.add(new Step(at, name) {
            @Override
            void run() {
                r.run();
            }
        });
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (w != null) {
            if (CaravanSC.class != null) {
                call(CaravanSC.class, "stopNow", w);
            }
            for (int[] p : placed) {
                TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof IInventory) {
                    for (int i = 0; i < ((IInventory) te).getSizeInventory(); i++) {
                        ((IInventory) te).setInventorySlotContents(i, null);
                    }
                }
                w.setBlockToAir(p[0], p[1], p[2]);
            }
            for (Entity e : listed) {
                unlist(e);
            }
        }
        for (Entity e : made) {
            if (e != null && !e.isDead) {
                e.setDead();
            }
        }
        for (ForgeChunkManager.Ticket t : tickets) {
            ForgeChunkManager.releaseTicket(t);
        }
        tickets.clear();
        placed.clear();
        made.clear();
        listed.clear();
        w = null;
        System.out.println("[SC-WORLDTEST] DONE finale");
        finished = true;
    }

    // ------------------------------------------------------------------ 1. registration (§2.2)

    @SuppressWarnings("unchecked")
    private void registration() {
        StringBuilder bad = new StringBuilder();
        Map<String, Class<?>> names = EntityList.stringToClassMapping;
        for (int id = MobEntitiesSC.ID_BREAKDOWN_WARDEN; id <= MobEntitiesSC.ID_CARAVAN_GUARD; id++) {
            if (!names.containsKey("SiliconAge." + MobEntitiesSC.nameOf(id))) {
                bad.append(" entity ").append(MobEntitiesSC.nameOf(id));
            }
        }
        Map<String, Class<?>> starts = ReflectionHelper.getPrivateValue(MapGenStructureIO.class, null, "field_143040_a", "startNameToClassMap");
        for (String n : new String[]{"SCSi14Arena", "SCSi14Wreck"}) {
            if (!starts.containsKey(n)) {
                bad.append(" structure ").append(n);
            }
        }
        Object block = Block.blockRegistry.getObject("SiliconAge:arenaSC");
        if (block == null || block == Blocks.air || block != ArenaBlocksSC.arena) {
            bad.append(" block arenaSC");
        }
        Map<String, Class<?>> tiles = ReflectionHelper.getPrivateValue(TileEntity.class, null, "field_145855_i", "nameToClassMap");
        if (!tiles.containsKey("SiliconAge.breakdownConsole")) {
            bad.append(" tile SiliconAge.breakdownConsole");
        }
        for (String n : new String[]{"arenaCoordinator", "journalSC", "paintScheme", "lithoModule", "navRecord"}) {
            if (Item.itemRegistry.getObject("SiliconAge:" + n) == null) {
                bad.append(" item ").append(n);
            }
        }
        Item relic = RelicItemsSC.relic;
        for (int m = ItemRelicSC.BREAKDOWN_KEY; m <= ItemRelicSC.BREAKDOWN_MATRIX; m++) {
            if (relic == null || !("item.siliconage.relic." + ItemRelicSC.NAMES[m]).equals(relic.getUnlocalizedName(new ItemStack(relic, 1, m)))) {
                bad.append(" relic ").append(m);
            }
        }
        print(bad.length() == 0, "registration (§2.2): the boss, its coils and the caravan's two in EntityList, the structures SCSi14Arena / SCSi14Wreck,"
                + " arenaSC and its TE, the five items, the relics 3..4" + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 2. the arena (§4.1, §4.2, ПУ-8..ПУ-10)

    private void arenaMake() {
        if (!ArenaSC.generates()) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the arena - arenas don't generate");
            return;
        }
        ArenaDataSC d = ArenaDataSC.get(w);
        // the nearest arena of the spawn that is built (a former run's) or whose ground nobody has seen yet
        int[] pick = null;
        boolean fresh = false;
        int[] held = null;
        for (int r = 0; r <= 3 && pick == null; r++) {
            for (int ri = -r; ri <= r; ri++) {
                for (int rj = -r; rj <= r; rj++) {
                    if (Math.max(Math.abs(ri), Math.abs(rj)) != r) {
                        continue;
                    }
                    int[] a = ArenaLayoutSC.arenaOf(seed, ri, rj);
                    if (a == null) {
                        continue;
                    }
                    ArenaDataSC.Arena rec = d == null ? null : d.arenaAt(a[0], a[1]);
                    boolean untouched = rec == null && freshBox(a);
                    if (pick == null && (rec != null && rec.state != ArenaDataSC.EMPTY || untouched)) {
                        pick = a;
                        fresh = untouched;
                    } else if (held == null && untouched) {
                        held = a;                                   // the next untouched one: the held-off case below
                    }
                }
            }
        }
        if (pick == null || d == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the arena - no built or untouched arena within 3 regions of the spawn");
            return;
        }
        int i = pick[0], j = pick[1], g = SubstrateTerrainSC.GRID >> 4;
        ax = ArenaLayoutSC.centre(i);
        az = ArenaLayoutSC.centre(j);
        floorY = ArenaLayoutSC.floorY(seed, i, j);
        int[] box = {(ax - ArenaLayoutSC.HALF) >> 4, (az - ArenaLayoutSC.HALF) >> 4, (ax + ArenaLayoutSC.HALF) >> 4, (az + ArenaLayoutSC.HALF) >> 4};
        force(i * g, j * g, 3);
        int outside = generate(box);
        arena = d.arenaAt(i, j);
        // the pit: the floor on floorY over the disk, the air above it (but the pieces), the wall's plating on the rim
        Block plate = ArenaBlocksSC.arena;
        int floor = 0, disk = 0, airBad = 0, wall = 0, rim = 0, lamps = 0;
        for (int dx = -ArenaLayoutSC.RIM_R; dx <= ArenaLayoutSC.RIM_R; dx++) {
            for (int dz = -ArenaLayoutSC.RIM_R; dz <= ArenaLayoutSC.RIM_R; dz++) {
                if (ArenaLayoutSC.inPit(dx, dz)) {
                    disk++;
                    floor += w.getBlock(ax + dx, floorY, az + dz) == plate ? 1 : 0;
                    if (!feature(dx, dz)) {
                        for (int y = floorY + 1; y <= floorY + ArenaLayoutSC.WALL_H; y++) {
                            airBad += w.isAirBlock(ax + dx, y, az + dz) ? 0 : 1;
                        }
                    }
                } else if (ArenaLayoutSC.onRim(dx, dz)) {
                    rim++;
                    wall += w.getBlock(ax + dx, floorY + ArenaLayoutSC.WALL_H, az + dz) == plate
                            && w.getBlockMetadata(ax + dx, floorY + ArenaLayoutSC.WALL_H, az + dz) == ArenaBlocksSC.WALL ? 1 : 0;
                }
                for (int y = floorY; y <= floorY + ArenaLayoutSC.WALL_H; y++) {
                    lamps += w.getBlock(ax + dx, y, az + dz) == plate && w.getBlockMetadata(ax + dx, y, az + dz) == ArenaBlocksSC.LAMP ? 1 : 0;
                }
            }
        }
        kx = ax + ArenaLayoutSC.CONSOLE_DX;
        ky = floorY + 1;
        kz = az + ArenaLayoutSC.CONSOLE_DZ;
        TileEntity te = w.getTileEntity(kx, ky, kz);
        boolean console = w.getBlock(kx, ky, kz) == plate && w.getBlockMetadata(kx, ky, kz) == ArenaBlocksSC.CONSOLE
                && te instanceof com.sc.tileentity.TileEntityBreakdownConsoleSC && Boolean.TRUE.equals(call(te, "isSet"));
        int pedestals = 0, columns = 0;
        for (int k = 0; k < ArenaLayoutSC.COILS; k++) {
            int[] s = ArenaLayoutSC.coilSpot(k);
            pedestals += w.getBlock(ax + s[0], floorY + 1, az + s[1]) == plate && w.getBlockMetadata(ax + s[0], floorY + 1, az + s[1]) == ArenaBlocksSC.PEDESTAL ? 1 : 0;
        }
        for (int k = 0; k < ArenaLayoutSC.COLUMNS; k++) {
            int[] c = ArenaLayoutSC.columnSpot(k);
            boolean whole = true;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = floorY + 1; y <= floorY + ArenaLayoutSC.COLUMN_H; y++) {
                        whole &= w.getBlock(ax + c[0] + dx, y, az + c[1] + dz) == plate;
                    }
                }
            }
            columns += whole ? 1 : 0;
        }
        int stone = 0;
        for (int cx = box[0]; cx <= box[2]; cx++) {
            for (int cz = box[1]; cz <= box[3]; cz++) {
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
        ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(w);
        StructureRuinStartSC node = gen == null ? null : gen.nodes().startAt(i * g, j * g);
        boolean noNode = node == null || !node.isSizeableStructure();
        boolean ready = arena != null && (arena.state == ArenaDataSC.READY || arena.state == ArenaDataSC.KEYED) && arena.floorY == floorY;
        boolean ok = ready && floor == disk && airBad == 0 && wall * 2 >= rim && console && pedestals == ArenaLayoutSC.COILS && columns == ArenaLayoutSC.COLUMNS
                && stone == 0 && noNode && (!fresh || outside == 0);
        print(ok, "arena (ПУ-8..ПУ-10) at crossing " + i + "," + j + " (" + ax + ", " + az + "), " + (fresh ? "fresh" : "built before") + ": the record "
                + (arena == null ? "none" : String.valueOf(arena.state)) + " (READY), the floor " + floor + " of " + disk + " on Y " + floorY + ", "
                + airBad + " blocks over it but the pieces (0), the wall's plating " + wall + " of " + rim + " rim columns at its top, the Console set up "
                + console + ", pedestals " + pedestals + " (4), columns " + columns + " (8), lamps " + lamps + ", Minecraft stone " + stone + " (0), a Dead"
                + " Node's start there " + !noNode + " (false), " + (fresh ? outside + " chunk(s) past the window + 1 loaded (0 - no cascade)" : "the cascade: a fresh one's"));
        // a start made again where its chunks exist (the structure's own file lost): the record decides - a built arena keeps its
        // pieces (the box's chunks not made yet go on with the arena; the made ones never change), the record kept (ПУ-3, ПУ-11)
        int stateBefore = arena == null ? -1 : arena.state;
        StructureStart again = startAt(new MapGenArenaSC(), i * g, j * g);
        ArenaDataSC.Arena after = d.arenaAt(i, j);
        print(again != null && again.isSizeableStructure() && after == arena && ready && after.state == stateBefore, "arena again (ПУ-3, ПУ-11): a start"
                + " made again where its chunks exist (its record " + stateBefore + ") has " + (again == null ? "?" : again.getComponents().size())
                + " pieces (> 0: the record decides), the record kept " + (after == arena) + ", its state " + (after == null ? "?" : after.state));
        if (held != null) {
            heldOff(held);
        } else {
            System.out.println("[SC-WORLDTEST] INFO finale: the held-off arena (EMPTY) - no second untouched arena within 3 regions");
        }
    }

    /** ПУ-9 (a ground of 0.2.x): arena `a`'s start held off while a chunk of its box is made - then its start is empty and its record EMPTY. */
    @SuppressWarnings("unchecked")
    private void heldOff(int[] a) {
        ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(w);
        if (gen == null) {
            return;
        }
        int g = SubstrateTerrainSC.GRID >> 4, cx = a[0] * g, cz = a[1] * g;
        Map<Long, Object> map;
        try {
            map = (Map<Long, Object>) ReflectionHelper.getPrivateValue(MapGenStructure.class, gen.arenas(), "structureMap", "field_75053_d");
        } catch (Throwable t) {
            System.out.println("[SC-WORLDTEST] INFO finale: the held-off arena - the structure map can't be reached (" + t + ")");
            return;
        }
        Long key = Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
        Object placeholder = new MapGenArenaSC.StructureArenaStartSC(cx, cz);
        map.put(key, placeholder);                                     // the world's generator sees a start there: none is made
        try {
            w.getChunkFromChunkCoords(cx - 1, cz - 1);                  // a chunk of its box - "the ground explored before"
        } finally {
            if (map.get(key) == placeholder) {
                map.remove(key);
            }
        }
        StructureStart st = startAt(new MapGenArenaSC(), cx, cz);
        ArenaDataSC d = ArenaDataSC.get(w);
        ArenaDataSC.Arena rec = d == null ? null : d.arenaAt(a[0], a[1]);
        if (st != null && !map.containsKey(key)) {
            map.put(key, st);                                          // the world's generator keeps that empty start from now on
        }
        print(st != null && !st.isSizeableStructure() && rec != null && rec.state == ArenaDataSC.EMPTY, "arena held off (ПУ-9, ПУ-11): at crossing " + a[0]
                + "," + a[1] + " a chunk of the box made first - the start " + (st == null ? "?" : st.getComponents().size() + " pieces (0)") + ", the record "
                + (rec == null ? "none" : String.valueOf(rec.state)) + " (EMPTY " + ArenaDataSC.EMPTY + ")");
    }

    /** The offset holds one of the arena's pieces (a column, a pedestal, the Console, a lamp's column): not air over the floor. */
    private static boolean feature(int dx, int dz) {
        if (ArenaLayoutSC.inColumn(dx, dz) || dx == ArenaLayoutSC.CONSOLE_DX && dz == ArenaLayoutSC.CONSOLE_DZ) {
            return true;
        }
        for (int k = 0; k < ArenaLayoutSC.COILS; k++) {
            int[] s = ArenaLayoutSC.coilSpot(k);
            if (Math.abs(s[0] - dx) <= 1 && Math.abs(s[1] - dz) <= 1) {
                return true;                                            // a pedestal and its coil's room
            }
        }
        for (int k = 0; k < ArenaLayoutSC.LAMPS; k++) {
            int[] l = ArenaLayoutSC.lampSpot(k);
            if (Math.abs(l[0] - dx) <= 1 && Math.abs(l[1] - dz) <= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean freshBox(int[] a) {
        int x = ArenaLayoutSC.centre(a[0]), z = ArenaLayoutSC.centre(a[1]), r = ArenaLayoutSC.HALF;
        return fresh((x - r) >> 4, (z - r) >> 4, (x + r) >> 4, (z + r) >> 4);
    }

    // ------------------------------------------------------------------ 3. the Console (§4.4, ПУ-11)

    private void console() {
        if (arena == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the Console - no arena");
            return;
        }
        if (w.difficultySetting == EnumDifficulty.PEACEFUL) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the Console and the fight - the test world is on Peaceful");
            arena = null;
            return;
        }
        if (arena.state == ArenaDataSC.FIGHT) {
            ArenaWorldSC.bossDied(w, arena, null);                    // a former run's fight left behind: closed first
        }
        if (arena.state == ArenaDataSC.KEYED) {
            arena.keyIn = false;
            arena.state = ArenaDataSC.READY;                           // a former run's key: taken back for a clean start
        }
        keyHolder = station("key-holder", kx, ky, kz + 2);
        second = station("second-key", kx + 1, ky, kz + 2);
        // Peaceful: refused, the key kept
        keyHolder.inventory.mainInventory[0] = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1);
        keyHolder.inventory.currentItem = 0;
        EnumDifficulty was = w.difficultySetting;
        boolean refused;
        try {
            w.difficultySetting = EnumDifficulty.PEACEFUL;
            ArenaWorldSC.start(w, arena, keyHolder, kx, ky, kz);
            refused = !arena.keyIn && ArenaWorldSC.isKey(keyHolder.inventory.mainInventory[0]) && arena.state == ArenaDataSC.READY;
        } finally {
            w.difficultySetting = was;
        }
        // with the key: it goes into the record, the countdown begins
        boolean used = ArenaWorldSC.start(w, arena, keyHolder, kx, ky, kz);
        boolean in = used && arena.keyIn && !ArenaWorldSC.isKey(keyHolder.inventory.mainInventory[0]);
        Object counting = call(ArenaWorldSC.class, "counting", arena);
        print(refused && in && !Boolean.FALSE.equals(counting), "console (ПУ-11): on Peaceful refused, the key kept " + refused + "; with a key in hand -"
                + " taken from the hand into the record " + in + ", the countdown of " + ArenaWorldSC.COUNTDOWN_TICKS + " ticks running " + counting
                + " (said: " + keyHolder.said + ")");
        fightBefore = arena.fightId;
    }

    // ------------------------------------------------------------------ 4. the fight (§5.3, ПС-51..ПС-54)

    private void fightUp() {
        if (arena == null) {
            return;
        }
        boss = warden();
        List<EntityContainmentCoilSC> coils = coils();
        int onPedestals = 0;
        for (EntityContainmentCoilSC c : coils) {
            for (int k = 0; k < ArenaLayoutSC.COILS; k++) {
                int[] s = ArenaLayoutSC.coilSpot(k);
                if (Math.abs(c.posX - (ax + s[0] + 0.5)) <= 1.5 && Math.abs(c.posZ - (az + s[1] + 0.5)) <= 1.5) {
                    onPedestals++;
                    break;
                }
            }
        }
        boolean near = boss != null && Math.hypot(boss.posX - ax, boss.posZ - az) <= WardenRulesSC.LEASH;
        boolean fight = arena.state == ArenaDataSC.FIGHT && arena.keyIn && arena.fightId != fightBefore;
        // a second click while it runs: busy, the second player's key kept
        second.inventory.mainInventory[0] = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1);
        second.inventory.currentItem = 0;
        long id = arena.fightId;
        ArenaWorldSC.start(w, arena, second, kx, ky, kz);
        boolean busy = arena.fightId == id && arena.state == ArenaDataSC.FIGHT && ArenaWorldSC.isKey(second.inventory.mainInventory[0]);
        print(fight && near && coils.size() == ArenaLayoutSC.COILS && onPedestals == ArenaLayoutSC.COILS && busy && boss != null
                && Math.abs(boss.getMaxHealth() - WardenRulesSC.maxHealth(Math.max(1, arena.maxPlayers))) < 0.5F, "console fight (ПУ-11, ПС-50): after the countdown"
                + " the record FIGHT with a new fight id and the key in " + fight + "; the Warden " + (boss == null ? "none" : "HP " + boss.getMaxHealth()
                + " within " + WardenRulesSC.LEASH + " of the middle " + near) + "; " + coils.size() + " coils, " + onPedestals + " on the pedestals (4); a second"
                + " click - busy, the second key kept " + busy);
        if (boss == null) {
            return;
        }
        // a blow with the coils lit: the health kept (ПС-52)
        WorldTestCombatSC.HurtablePlayer hitter = hurtable("hitter", ax + 3, floorY + 1, az + 3);
        hpBefore = boss.getHealth();
        boss.attackEntityFrom(DamageSource.causePlayerDamage(hitter), 10F);
        // coilsLit() - the lit coils' bits (DW_COILS), not their count
        boolean shielded = boss.getHealth() >= hpBefore - 0.01F && Integer.bitCount(boss.coilsLit()) == ArenaLayoutSC.COILS;
        // a ПЭ-1 Point into each coil: dark
        int dark = 0;
        for (EntityContainmentCoilSC c : coils) {
            boolean took = ((IEmpReactiveSC) c).empStruck(hitter, EmpMode.POINT, c.posX, c.posY + 1, c.posZ);
            dark += took && coilState(c) == EntityContainmentCoilSC.DARK ? 1 : 0;
        }
        print(shielded && dark == coils.size(), "fight shield (ПС-52): a blow with every coil lit - the health " + hpBefore + " -> " + boss.getHealth() + " kept "
                + shielded + "; a Point into each coil - " + dark + " of " + coils.size() + " DARK" + (SubstrateConfigSC.empMobTargets ? "" : " (empMobTargets off!)"));
    }

    private void fightWindow() {
        if (boss == null || boss.isDead) {
            return;
        }
        WorldTestCombatSC.HurtablePlayer hitter = hurtable("hitter", ax + 3, floorY + 1, az + 3);
        boolean open = boss.coilsLit() == 0 && boss.phaseFlag(EntityBreakdownWardenSC.FLAG_WINDOW);
        float hp = boss.getHealth();
        boss.hurtResistantTime = 0;
        boss.attackEntityFrom(DamageSource.causePlayerDamage(hitter), 10F);
        boolean through = boss.getHealth() < hp;
        // the window ended now (its counter set to its last tick): every coil lit again
        Object fight = call(boss, "fight");
        boolean set = setField(fight, "windowLeft", Integer.valueOf(1));
        print(open && through, "fight window (ПС-52): all four dark - the window open " + open + " (coils lit " + boss.coilsLit() + "), a blow gets through "
                + through + " (" + hp + " -> " + boss.getHealth() + ")" + (set ? "" : "; B's fight().windowLeft not reachable - its end waits " + WardenRulesSC.WINDOW_TICKS));
    }

    private void fightRelit() {
        if (boss == null || boss.isDead) {
            return;
        }
        int lit = 0;
        for (EntityContainmentCoilSC c : coils()) {
            lit += coilState(c) == EntityContainmentCoilSC.LIT ? 1 : 0;
        }
        print(lit == ArenaLayoutSC.COILS && Integer.bitCount(boss.coilsLit()) == ArenaLayoutSC.COILS && boss.phase() == WardenRulesSC.PHASE_HOLD, "fight relit (ПС-52): the window"
                + " over - " + lit + " coils LIT again (4), phase " + boss.phase() + " (1)");
        boss.setHealth(boss.getMaxHealth() * 0.59F);                    // to «Пробой»
    }

    private void fightPhase2() {
        if (boss == null || boss.isDead) {
            return;
        }
        boolean two = boss.phase() == WardenRulesSC.PHASE_BREAKDOWN && boss.coilsLit() == 0;
        Object fight = call(boss, "fight");
        boolean set = setField(fight, "crackIn", Integer.valueOf(1));
        print(two, "fight phase 2 (ПС-51, ПС-53): at 59% - phase " + boss.phase() + " (2), the coils out for good (lit " + boss.coilsLit() + ")"
                + (set ? "" : "; B's fight().crackIn not reachable - the crack waits " + WardenRulesSC.CRACK_EVERY));
    }

    private void fightCracks() {
        if (boss == null || boss.isDead || arena == null) {
            return;
        }
        List<int[]> cracks = arena.cracks();
        int open = 0;
        for (int[] c : cracks) {
            open += w.getBlock(c[0], c[1], c[2]) == SubstrateWildBlocksSC.vent && w.getBlockMetadata(c[0], c[1], c[2]) == SubstrateWildBlocksSC.ARC_CRACK ? 1 : 0;
        }
        print(!cracks.isEmpty() && open == cracks.size() && cracks.size() <= WardenRulesSC.CRACKS_MAX, "fight cracks (ПС-53): " + cracks.size() + " Breakdown"
                + " crack(s) in the record, " + open + " of them an arc crack in the floor (at most " + WardenRulesSC.CRACKS_MAX + ")");
        boss.setHealth(boss.getMaxHealth() * 0.24F);                    // to «Перегрузка»
    }

    private void fightPhase3() {
        if (boss == null || boss.isDead) {
            return;
        }
        boolean three = boss.phase() == WardenRulesSC.PHASE_OVERLOAD;
        Object fight = call(boss, "fight");
        // the heat's window: the phase's clock at its last OVERHEAT_TICKS - no protection, the blow whole into the health
        boolean set = setField(fight, "phaseTicks", Integer.valueOf(WardenRulesSC.OVERHEAT_EVERY - WardenRulesSC.OVERHEAT_TICKS + 5));
        Object hot = call(fight, "overheated");
        WorldTestCombatSC.HurtablePlayer hitter = hurtable("hitter", ax + 3, floorY + 1, az + 3);
        float hp = boss.getHealth();
        boss.hurtResistantTime = 0;
        boss.attackEntityFrom(DamageSource.causePlayerDamage(hitter), 8F);
        float took = hp - boss.getHealth();
        print(three && Boolean.TRUE.equals(hot) && took >= 7.9F, "fight phase 3 (ПС-54): at 24% - phase " + boss.phase() + " (3); the heat's window"
                + (set ? "" : " (phaseTicks not reachable)") + " overheated " + hot + ", a blow of 8 took " + took + " of the health (whole)");
        // the core reset (ПС-54): a test player behind a column untouched, one in the open hit
        int[] col = ArenaLayoutSC.columnSpot(0);
        double cx = ax + col[0] + 1, cz = az + col[1] + 1, vx = cx - boss.posX, vz = cz - boss.posZ, len = Math.max(1e-3, Math.hypot(vx, vz));
        WorldTestCombatSC.HurtablePlayer hidden = hurtable("hidden", (int) Math.floor(cx + vx / len * 2.5), floorY + 1, (int) Math.floor(cz + vz / len * 2.5));
        int[] open = ArenaLayoutSC.coilSpot(1);
        WorldTestCombatSC.HurtablePlayer exposed = hurtable("exposed", ax + open[0] / 2, floorY + 1, az + open[1] / 2);
        for (WorldTestCombatSC.HurtablePlayer p : new WorldTestCombatSC.HurtablePlayer[]{hidden, exposed}) {
            p.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100D);
            p.setHealth(100F);
        }
        Object reachHidden = call(fight, "blastReaches", hidden), reachOpen = call(fight, "blastReaches", exposed);
        call(fight, "coreResetOn", hidden);
        call(fight, "coreResetOn", exposed);
        float hit = 100F - exposed.getHealth(), safe = 100F - hidden.getHealth();
        print(Boolean.FALSE.equals(reachHidden) && Boolean.TRUE.equals(reachOpen) && safe < 0.01F && hit >= WardenRulesSC.CORE_RESET_DAMAGE * 0.5F,
                "fight core reset (ПС-54): behind a column - reached " + reachHidden + ", took " + safe + " (0); in the open - reached " + reachOpen + ", took "
                + hit + " of «Пробой» (" + WardenRulesSC.CORE_RESET_DAMAGE + ", by the difficulty); the highlight's x1.5 and the silencing pulse: in game");
    }

    // ------------------------------------------------------------------ 5. the reset (§4.6, ПС-56)

    private void resetBegin() {
        if (arena == null || arena.state != ArenaDataSC.FIGHT) {
            return;
        }
        arena.lastPlayerAt = w.getTotalWorldTime() - WardenRulesSC.RESET_AWAY_TICKS - 1;
        Object ticked = call(cls(A_WORLD), "tick", w, Long.valueOf(w.getTotalWorldTime()));
        if (ticked == NONE) {
            System.out.println("[SC-WORLDTEST] INFO finale: ArenaWorldSC.tick not callable - the world's own tick resets it");
        }
    }

    private void resetCheck() {
        if (arena == null) {
            return;
        }
        boolean keyed = arena.state == ArenaDataSC.KEYED && arena.keyIn;
        boolean gone = boss == null || boss.isDead;
        int coilsLeft = coils().size();
        int closed = 0, cracks = 0;
        // the cracks the fight opened: back to the floor's plating (takeCracks emptied the record)
        for (int dx = -ArenaLayoutSC.PIT_R; dx <= ArenaLayoutSC.PIT_R; dx++) {
            for (int dz = -ArenaLayoutSC.PIT_R; dz <= ArenaLayoutSC.PIT_R; dz++) {
                if (!ArenaLayoutSC.inPit(dx, dz)) {
                    continue;
                }
                Block b = w.getBlock(ax + dx, floorY, az + dz);
                cracks += b == SubstrateWildBlocksSC.vent ? 1 : 0;
                closed += b == ArenaBlocksSC.arena ? 1 : 0;
            }
        }
        boolean clean = cracks == 0 && arena.cracks().isEmpty();
        print(keyed && gone && coilsLeft == 0 && clean, "reset (ПС-56): every player away " + WardenRulesSC.RESET_AWAY_TICKS + " ticks - the record "
                + arena.state + " (KEYED " + ArenaDataSC.KEYED + ") with the key in " + arena.keyIn + ", the Warden gone " + gone + ", coils left " + coilsLeft
                + " (0), cracks left in the floor " + cracks + " (0, " + closed + " plating)");
        // a click without a key: the fight again (the key in the Console)
        keyHolder.inventory.mainInventory[0] = null;
        fightBefore = arena.fightId;
        ArenaWorldSC.start(w, arena, keyHolder, kx, ky, kz);
    }

    // ------------------------------------------------------------------ 6. the loot (§5.6, ПТ-21..ПТ-24)

    private void loot() {
        if (arena == null) {
            return;
        }
        EntityBreakdownWardenSC b = warden();
        boolean again = b != null && arena.state == ArenaDataSC.FIGHT && arena.fightId != fightBefore;
        print(again, "reset fight (ПС-56): a click without a key - the fight again " + again);
        if (b == null) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            print(false, "loot - no settlements' registry");
            return;
        }
        WorldTestArmourerSC.StationPlayer p1 = station("loot-60", ax + 2, floorY + 1, az), p2 = station("loot-40", ax - 2, floorY + 1, az),
                p3 = station("loot-5", ax, floorY + 1, az + 2);
        for (EntityPlayerMP p : new EntityPlayerMP[]{p1, p2, p3}) {
            r.player(p.getUniqueID()).extra.removeTag(WardenRulesSC.EXTRA);       // a former run's record: forgotten
        }
        int crumbsLeft = CrumbLimitSC.left(p1);
        Map<UUID, Float> shares = new HashMap<UUID, Float>();
        shares.put(p1.getUniqueID(), Float.valueOf(0.6F));
        shares.put(p2.getUniqueID(), Float.valueOf(0.4F));
        shares.put(p3.getUniqueID(), Float.valueOf(0.05F));
        Object n = call(cls(LOOT), "award", b, shares, Boolean.TRUE);
        int k1 = count(call(cls(LOOT), "pendingCount", w, p1.getUniqueID())), k2 = count(call(cls(LOOT), "pendingCount", w, p2.getUniqueID())),
                k3 = count(call(cls(LOOT), "pendingCount", w, p3.getUniqueID()));
        call(cls(LOOT), "deliverPending", p1);
        int matrix = held(p1, RelicItemsSC.relic, ItemRelicSC.BREAKDOWN_MATRIX), crumbs = held(p1, ModItems.singularCrumb, -1),
                clots = held(p1, ModItems.singularClot, -1), scrap = held(p1, MobItemsSC.combatLoot, com.sc.manual.BookWorldSC.LOOT_SINGULAR_SCRAP),
                journal12 = held(p1, FinaleItemsSC.journal, JournalSC.metaOf(WardenRulesSC.FIRST_KILL_JOURNAL));
        boolean given = matrix == WardenRulesSC.MATRIX && crumbs >= WardenRulesSC.CRUMBS_MIN && crumbs <= WardenRulesSC.CRUMBS_MAX
                && clots >= WardenRulesSC.CLOTS_MIN && clots <= WardenRulesSC.CLOTS_MAX && scrap >= WardenRulesSC.SCRAP_MIN && scrap <= WardenRulesSC.SCRAP_MAX
                && journal12 == 1 && CrumbLimitSC.left(p1) == crumbsLeft;
        print(Integer.valueOf(2).equals(n) && k1 > 0 && k2 > 0 && k3 == 0 && given, "loot (ПТ-21..ПТ-24): award for 60% / 40% / 5% (none online) - " + n
                + " players (2); kept " + k1 + " / " + k2 + " / " + k3 + " stacks (the third none); delivered to the first: the Matrix " + matrix + ", Crumbs "
                + crumbs + " (4..8, the day's cap left " + crumbsLeft + " -> " + CrumbLimitSC.left(p1) + "), Clots " + clots + ", scrap " + scrap + ", Journal №12 "
                + journal12 + " " + given);
        // the same day again: the cooldown; three days on: a loot, no Journal (not the first kill)
        Object n2 = call(cls(LOOT), "award", b, shares, Boolean.TRUE);
        NBTTagCompound wr = r.player(p1.getUniqueID()).extra.getCompoundTag(WardenRulesSC.EXTRA);
        wr.setLong(WardenRulesSC.LAST_DAY, wr.getLong(WardenRulesSC.LAST_DAY) - WardenRulesSC.LOOT_COOLDOWN_DAYS);
        r.player(p1.getUniqueID()).extra.setTag(WardenRulesSC.EXTRA, wr);
        call(cls(LOOT), "award", b, shares, Boolean.TRUE);
        Object kept = call(cls(LOOT), "takePending", w, p1.getUniqueID());
        int journals = 0, stacks = 0;
        if (kept instanceof List) {
            for (Object o : (List<?>) kept) {
                stacks++;
                journals += o instanceof ItemStack && ((ItemStack) o).getItem() == FinaleItemsSC.journal ? 1 : 0;
            }
        }
        call(cls(LOOT), "takePending", w, p2.getUniqueID());
        print(Integer.valueOf(0).equals(n2) && stacks > 0 && journals == 0, "loot cooldown (ПТ-21, ПТ-24): the same day again - " + n2 + " players (0);"
                + " three days on - " + stacks + " stacks, Journals " + journals + " (0: not the first kill)");
        // the shares outlive an unload / a restart (ПТ-21): a log of 60 of 100 for the first record's player kept in scWarden, back on a
        // copy read from the Warden's NBT
        Map<UUID, Float> log = new HashMap<UUID, Float>();
        log.put(p1.getUniqueID(), Float.valueOf(60F));
        com.sc.mob.MobEventsSC.restoreLog(b, log, 100F);
        NBTTagCompound saved = new NBTTagCompound();
        b.writeToNBT(saved);
        EntityBreakdownWardenSC copy = new EntityBreakdownWardenSC(w);
        copy.readFromNBT(saved);
        Float kept60 = com.sc.mob.MobEventsSC.damageShares(copy).get(p1.getUniqueID());
        com.sc.mob.MobEventsSC.forget(copy);
        com.sc.mob.MobEventsSC.forget(b);
        print(kept60 != null && Math.abs(kept60.floatValue() - 0.6F) < 1.0E-3F && copy.bound() && copy.fightId() == b.fightId(),
                "loot shares kept (ПТ-21): 60 of 100 through the Warden's NBT - " + kept60 + " (0.6), its fight " + copy.bound());
        // the task (ПК-23): wardenDefeated counts a suited player
        for (int t = 0; t < 4; t++) {
            p1.inventory.armorInventory[t] = new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[3 - t]);
        }
        int before = SingularLevel.store(p1, true).getInteger(SingularLevel.C_WARDEN);
        SingularProgressSC.wardenDefeated(p1);
        int after = SingularLevel.store(p1, true).getInteger(SingularLevel.C_WARDEN);
        // the arena's end (ПУ-11): READY, the key spent
        ArenaWorldSC.bossDied(w, arena, b);
        boolean ready = arena.state == ArenaDataSC.READY && !arena.keyIn;
        // its fight over now - a stale Warden (a reset's leftover) gives nothing whatever the shares (ПТ-1: no loot without the key)
        for (EntityPlayerMP p : new EntityPlayerMP[]{p1, p2, p3}) {
            r.player(p.getUniqueID()).extra.removeTag(WardenRulesSC.EXTRA);
        }
        Object stale = call(cls(LOOT), "award", b, shares, Boolean.TRUE);
        int staleKept = count(call(cls(LOOT), "pendingCount", w, p1.getUniqueID()));
        print(Integer.valueOf(0).equals(stale) && staleKept == 0 && !b.arenaWarden(), "loot stale (ПТ-1): its fight over - award " + stale
                + " players (0), kept " + staleKept + " (0)");
        b.setDead();
        for (EntityContainmentCoilSC c : coils()) {
            c.setDead();
        }
        print(after == before + 1 && ready, "loot end (ПК-23, ПУ-11): wardenDefeated - the task's count " + before + " -> " + after + "; bossDied - the record "
                + arena.state + " (READY), the key spent " + !arena.keyIn);
    }

    private static int held(EntityPlayerMP p, Item item, int meta) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && item != null && s.getItem() == item && (meta < 0 || s.getItemDamage() == meta)) {
                n += s.stackSize;
            }
        }
        return n;
    }

    private static int count(Object o) {
        return o instanceof Integer ? ((Integer) o).intValue() : -1;
    }

    // ------------------------------------------------------------------ 7. a wreck (§6.2, ПЛ-3, ПЛ-4)

    private void wreck() {
        if (!ArenaSC.generates()) {
            return;
        }
        WreckLayoutSC.Site site = null;
        boolean fresh = false;
        for (int r = 1; r <= 12 && site == null; r++) {
            for (int cx = -r; cx <= r && site == null; cx++) {
                for (int cz = -r; cz <= r && site == null; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r) {
                        continue;
                    }
                    WreckLayoutSC.Site s = WreckLayoutSC.site(seed, cx, cz);
                    if (s == null) {
                        continue;
                    }
                    ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(w);
                    StructureRuinStartSC st = gen == null ? null : gen.wrecks().startAt(s.x >> 4, s.z >> 4);
                    boolean untouched = fresh((s.x - WreckLayoutSC.HALF) >> 4, (s.z - WreckLayoutSC.HALF) >> 4, (s.x + WreckLayoutSC.HALF) >> 4,
                            (s.z + WreckLayoutSC.HALF) >> 4);
                    if (untouched || st != null && st.isSizeableStructure()) {
                        site = s;
                        fresh = untouched;
                    }
                }
            }
        }
        if (site == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the wreck - no built or untouched wreck within 12 cells of the spawn");
            return;
        }
        int[] box = {(site.x - WreckLayoutSC.HALF) >> 4, (site.z - WreckLayoutSC.HALF) >> 4, (site.x + WreckLayoutSC.HALF) >> 4, (site.z + WreckLayoutSC.HALF) >> 4};
        force(site.x >> 4, site.z >> 4, 2);
        int outside = generate(box);
        int coils = 0, stone = 0;
        String journal = "none";
        for (int x = site.x - WreckLayoutSC.HALF; x <= site.x + WreckLayoutSC.HALF; x++) {
            for (int z = site.z - WreckLayoutSC.HALF; z <= site.z + WreckLayoutSC.HALF; z++) {
                for (int y = Math.max(1, site.y - 12); y <= Math.min(255, site.y + WreckLayoutSC.TILT_MAX + 6); y++) {
                    Block b = w.getBlock(x, y, z);
                    coils += b == RuinBlocksSC.ruin && w.getBlockMetadata(x, y, z) == com.sc.manual.BookWorldSC.RUIN_BURNT_COIL ? 1 : 0;
                    stone += b == Blocks.stone ? 1 : 0;
                    TileEntity te = b == Blocks.chest ? w.getTileEntity(x, y, z) : null;
                    if (te instanceof TileEntityChest) {
                        for (int i = 0; i < ((IInventory) te).getSizeInventory(); i++) {
                            ItemStack s = ((IInventory) te).getStackInSlot(i);
                            if (s != null && s.getItem() == FinaleItemsSC.journal) {
                                journal = "№" + JournalSC.numberOf(s.getItemDamage());
                            }
                        }
                    }
                }
            }
        }
        print(coils >= 2 * site.radius && ("№9".equals(journal) || "№10".equals(journal)) && stone == 0 && (!fresh || outside == 0), "wreck (ПЛ-3, ПЛ-4) of"
                + " cell " + site.cellX + "," + site.cellZ + " at " + site.x + ", " + site.z + " (" + (fresh ? "fresh" : "built before") + "): " + coils + " Burnt"
                + " Coils (a ring of radius " + site.radius + "), the chest's Journal " + journal + " (№9 / №10), Minecraft stone " + stone + (fresh ? ", "
                + outside + " chunk(s) past the window + 1 loaded (0)" : ""));
    }

    // ------------------------------------------------------------------ 8. the Signal (§7, ПЛ-5..ПЛ-8)

    private void signal() {
        Class<?> sc = cls(SIGNAL);
        if (sc == null) {
            print(false, "signal - no SignalSC");
            return;
        }
        SignalDataSC d = SignalDataSC.get(w);
        if (d != null && d.on) {
            call(sc, "endNow", w);                                      // the world's own signal of today: ended first
        }
        // a crossing of its own: not an arena, out of the spawn's ring, away from the others
        int i = -21, j = 17;
        while (ArenaLayoutSC.isArena(seed, i, j)) {
            i--;
        }
        int g = SubstrateTerrainSC.GRID >> 4;
        force(i * g, j * g, 3);
        // the plaza and its Node built (populated) before the cache, as round a real player: its middle holds the Node's heart -
        // a chest put into a chunk not populated yet would be built over by the Node a moment later
        int reach = com.sc.ruin.RuinLayoutSC.NODE_HALF + 8, cx = i * SubstrateTerrainSC.GRID, cz = j * SubstrateTerrainSC.GRID;
        generate(new int[]{(cx - reach) >> 4, (cz - reach) >> 4, (cx + reach) >> 4, (cz + reach) >> 4});
        Object began = call(sc, "startNow", w, Integer.valueOf(i), Integer.valueOf(j), Integer.valueOf(SignalRulesSC.DURATION_TICKS));
        boolean on = d != null && d.on && d.i == i && d.j == j && d.endAt > w.getTotalWorldTime() && !d.cacheDone;
        WorldTestArmourerSC.StationPlayer near = station("signal-near", i * SubstrateTerrainSC.GRID + 3, d == null ? 64 : d.y + 1, j * SubstrateTerrainSC.GRID + 3);
        Object res = call(sc, "cacheNow", w, near);
        String chest = "none", patrolText = "none";
        boolean cacheOk = false;
        if (res != null && res != NONE) {
            int x = count(field(res, "x")), y = count(field(res, "y")), z = count(field(res, "z"));
            TileEntity te = w.getTileEntity(x, y, z);
            int journal = 0, chips = 0;
            if (te instanceof IInventory) {
                for (int k = 0; k < ((IInventory) te).getSizeInventory(); k++) {
                    ItemStack s = ((IInventory) te).getStackInSlot(k);
                    journal += s != null && s.getItem() == FinaleItemsSC.journal && s.getItemDamage() == JournalSC.metaOf(11) ? 1 : 0;
                    chips += s != null ? 1 : 0;
                }
                placed.add(new int[]{x, y, z});                         // the cache's chest: removed at the end
            }
            Object pat = field(res, "patrol");
            int born = 0, size = 0;
            if (pat instanceof List) {
                for (Object o : (List<?>) pat) {
                    if (o instanceof Entity) {
                        size++;
                        born += BornInSubstrateSC.isBorn((Entity) o) ? 1 : 0;
                        made.add((Entity) o);
                    }
                }
            }
            chest = x + " " + y + " " + z + " with " + chips + " stack(s), Journal №11 " + journal;
            patrolText = size + " born in Si-14 " + born + " (" + SignalRulesSC.patrolSize(1) + ")";
            boolean peaceful = w.difficultySetting == EnumDifficulty.PEACEFUL;          // no patrol on Peaceful (package J)
            cacheOk = journal == 1 && (peaceful ? size == 0 : size >= SignalRulesSC.patrolSize(1) && born == size);
        }
        Object twice = call(sc, "cacheNow", w, near);
        call(sc, "endNow", w);
        boolean off = d != null && !d.on;
        print(!Boolean.FALSE.equals(began) && began != NONE && on && cacheOk && (twice == null) && off, "signal (ПЛ-5..ПЛ-8, package J's startNow): on at"
                + " crossing " + i + "," + j + " " + on + "; a player near - the cache " + chest + ", the patrol " + patrolText + "; a second time - "
                + (twice == null ? "nothing" : String.valueOf(twice)) + "; ended - off " + off);
    }

    // ------------------------------------------------------------------ 9. a caravan (§8, ПН-12..ПН-16)

    private void caravanLoad() {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        dock = r == null ? null : r.first(w.provider.dimensionId);
        if (dock == null) {
            return;
        }
        // the start spot 40..64 past the dome's edge, loaded 32 round it, the route's ring (package K's report: radius + 64 + 32 + 16)
        force(dock.x >> 4, dock.z >> 4, Math.min(11, (dock.radius + CaravanRulesSC.APPROACH_TO + CaravanRunSC.SPAWN_LOADED + 16 + 15) >> 4));
    }

    private void caravan() {
        if (dock == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the caravan - no First Dock in the registry (villages off, or not made yet)");
            return;
        }
        if (!SubstrateConfigSC.caravans) {
            System.out.println("[SC-WORLDTEST] INFO finale: the caravan - dimension.caravans is off; the test's own caravan runs anyway");
        }
        boolean peaceful = w.difficultySetting == EnumDifficulty.PEACEFUL;
        CaravanSC.stopNow(w);
        // the defender: a test FakePlayer - K counts `by` as a defender only when it isn't a real player (a never-spawned real-type one
        // would be in no list at all); a FakePlayer gets the Trust, never the rare deal (the core's CaravanSC.deal refuses it)
        defender = hurtable("caravan-defender", dock.x, dock.y + 1, dock.z);
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        SettlementRegistrySC.PlayerRec rec = r.player(defender.getUniqueID());
        rec.extra.removeTag(CaravanRulesSC.EXTRA);
        int rep0 = rec.reputation;
        boolean began = CaravanSC.startNow(w, dock, defender, true);
        EntityCaravanMasterSC m = CaravanSC.master();
        int guards = CaravanSC.guards().size(), guardsBound = 0;
        for (EntityCaravanGuardSC g : CaravanSC.guards()) {
            guardsBound += g.settlementId() == dock.id ? 1 : 0;
        }
        int came = CaravanSC.ambushNow(w), ambushers = CaravanSC.ambushers().size(), born = 0;
        for (EntityLiving e : CaravanSC.ambushers()) {
            born += BornInSubstrateSC.isBorn(e) ? 1 : 0;
        }
        double out = m == null ? 0 : Math.hypot(m.posX - dock.x, m.posZ - dock.z) - dock.radius;
        if (m != null) {
            defender.setLocationAndAngles(m.posX + 2, m.posY, m.posZ, 0F, 0F);
        }
        for (EntityLiving e : new ArrayList<EntityLiving>(CaravanSC.ambushers())) {
            e.attackEntityFrom(DamageSource.outOfWorld, 1000F);            // killed for real - no player's kill mixed into the Trust
        }
        CaravanSC.stepNow(w);
        int rewarded = CaravanSC.lastRewarded(), rep1 = r.player(defender.getUniqueID()).reputation;
        boolean fakeNoDeal = !CaravanSC.dealOpen(defender);
        CaravanSC.stopNow(w);
        boolean ambushOk = peaceful ? came == 0 : came >= CaravanRulesSC.ambushSize(1) && born == came && rewarded == 1 && rep1 - rep0 == CaravanRulesSC.REWARD_REP;
        print(began && m != null && m.settlementId() == dock.id && guards == CaravanRulesSC.GUARDS && guardsBound == guards && out > 0 && ambushOk && fakeNoDeal,
                "caravan defended (ПН-13..ПН-15) at the First Dock " + dock.id + ": a master " + (m != null) + " " + Math.round(out) + " blocks past the dome's"
                + " edge and " + guards + " guards (2), bound to the dock " + guardsBound + "; the ambush " + came + " / " + ambushers + " Renegades ("
                + (peaceful ? "none on Peaceful" : CaravanRulesSC.ambushSize(1) + ", born in Si-14 " + born) + "), all killed - rewarded " + rewarded
                + " defender(s), Trust " + rep0 + " -> " + rep1 + " (+" + CaravanRulesSC.REWARD_REP + "), the FakePlayer defender gets no rare deal " + fakeNoDeal);
        rareDeal();
        // the master killed: lost, nothing
        int repBefore = r.player(defender.getUniqueID()).reputation;
        boolean began2 = CaravanSC.startNow(w, dock, defender, true);
        EntityCaravanMasterSC m2 = CaravanSC.master();
        CaravanSC.ambushNow(w);
        if (m2 != null) {
            kill(m2, DamageSource.outOfWorld);
        }
        CaravanSC.stepNow(w);
        int end = CaravanSC.lastEnd(), rep2 = r.player(defender.getUniqueID()).reputation;
        boolean running = CaravanSC.running();
        CaravanSC.stopNow(w);
        print(began2 && end == CaravanRunSC.END_LOST && rep2 == repBefore && !running, "caravan lost (ПН-15): the master killed - the end " + end + " (END_LOST "
                + CaravanRunSC.END_LOST + "), over " + !running + ", Trust " + repBefore + " -> " + rep2 + " (the same)");
    }

    /**
     * ПН-15, ПО-21: the rare deal of a real-type test player (never spawned: K's defenders never list it, so it's granted here as the
     * reward would) - the Armourer's «Широкий канал» once, then refused R_CARAVAN_DEAL; a FakePlayer - refused whatever.
     */
    private void rareDeal() {
        WorldTestArmourerSC.StationPlayer dealer = station("caravan-dealer", dock.x, dock.y + 1, dock.z);
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        r.player(dealer.getUniqueID()).extra.removeTag(CaravanRulesSC.EXTRA);
        boolean closed = !CaravanSC.dealOpen(dealer);
        CaravanSC.grantDeal(dealer);
        boolean open = CaravanSC.dealOpen(dealer);
        EntityTunerSC armourer = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityTunerSC && !(o instanceof EntityCaravanMasterSC) && ((EntityTunerSC) o).kind() == NpcKindSC.ARMOURER && !((Entity) o).isDead
                    && r.byId(((EntityTunerSC) o).settlementId()) != null) {
                armourer = (EntityTunerSC) o;
                break;
            }
        }
        TradeOfferSC wide = TradeTableSC.byKey(TradeTableSC.ARM_MOD_WIDE);
        if (armourer == null || wide == null) {
            CaravanSC.useDeal(dealer);
            print(closed && open && !CaravanSC.dealOpen(dealer), "caravan deal (ПН-15, ПО-21): closed before " + closed + ", granted - open " + open
                    + ", used - closed " + !CaravanSC.dealOpen(dealer) + " (no Armourer loaded" + (wide == null ? ", no armModWide deal" : "") + " - the trade itself: in game)");
            return;
        }
        SettlementRegistrySC.Settlement s = r.byId(armourer.settlementId());
        SettlementRegistrySC.PlayerRec pr = r.player(dealer.getUniqueID());
        long today = com.sc.settle.SettlementSC.day(w);
        pr.reputation = ReputationSC.ACQUAINTANCE_FROM;
        pr.rollDay(today);
        pr.chips = pr.repTrades = 0;
        SettlementRegistrySC.LocalRec lr = s.local(dealer.getUniqueID());
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        dealer.setLocationAndAngles(armourer.posX, armourer.posY, armourer.posZ, 0F, 0F);
        int first = offerTrade(dealer, armourer, s, wide), modules = heldWide(dealer);
        boolean used = !CaravanSC.dealOpen(dealer);
        int second = offerTrade(dealer, armourer, s, wide);
        WorldTestCombatSC.HurtablePlayer fake = hurtable("caravan-fake-dealer", (int) armourer.posX, (int) armourer.posY, (int) armourer.posZ);
        r.player(fake.getUniqueID()).reputation = ReputationSC.ACQUAINTANCE_FROM;
        int fakeCode = offerTrade(fake, armourer, s, wide);
        for (int i = 0; i < dealer.inventory.mainInventory.length; i++) {
            dealer.inventory.mainInventory[i] = null;
        }
        print(closed && open && first == TradeServerSC.OK && modules == 1 && used && second == TradeServerSC.R_CARAVAN_DEAL && fakeCode == TradeServerSC.R_CARAVAN_DEAL,
                "caravan deal (ПН-15, ПО-21): closed before " + closed + ", granted - open " + open + "; at " + SettlementSC.displayName(s).getUnformattedText()
                + "'s Armourer «Широкий канал» - " + first + " (OK " + TradeServerSC.OK + "), the module " + modules + ", the deal used up " + used + "; again - " + second
                + " (R_CARAVAN_DEAL " + TradeServerSC.R_CARAVAN_DEAL + "); a FakePlayer - " + fakeCode);
    }

    /** One deal `o` at `tuner`: its price (scaled for the player's level and the settlement's spread) put in the inventory first. @return the code */
    private int offerTrade(EntityPlayerMP p, EntityTunerSC tuner, SettlementRegistrySC.Settlement s, TradeOfferSC o) {
        int level = ReputationSC.level(SettlementRegistrySC.get(w).player(p.getUniqueID()).reputation);
        int[] counts = TradeRulesSC.playerCounts(o, level, TradeRulesSC.seedPct(s.seed, o.key));
        for (int i = 0; i < o.in.length && i < counts.length; i++) {
            ItemStack d = o.in[i] == null ? null : o.in[i].display();
            for (int left = counts[i]; d != null && left > 0; ) {
                ItemStack c = d.copy();
                c.stackSize = Math.min(left, c.getMaxStackSize());
                left -= c.stackSize;
                p.inventory.addItemStackToInventory(c);
            }
        }
        p.inventory.currentItem = 0;
        return TradeServerSC.execute(p, tuner, o.key, 1);
    }

    /**
     * ПО-17..ПО-19 (package P's deals, the coordinator's test): at a loaded Cartographer - the Arena Coordinator for 16 chips + a
     * Crumb at «Почётный», a second one the same day refused (R_LIMIT); at a loaded Elder - «Наладчик» for 3 Crumbs + 32 chips (the
     * Crumbs paid don't touch the day's Crumb cap) and the Lithographers' Module for a Breakdown Matrix + 48 chips at «Свой», twice
     * (no limit). A real-type test player (never spawned); SKIP when no such Tuner is loaded.
     */
    private void deals() {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        EntityTunerSC cart = loadedTuner(r, NpcKindSC.CARTOGRAPHER), elder = loadedTuner(r, NpcKindSC.ELDER);
        TradeOfferSC coord = TradeTableSC.byKey(TradeTableSC.CART_ARENA_COORDINATOR), scheme = TradeTableSC.byKey(TradeTableSC.ELDER_TUNER_SCHEME),
                module = TradeTableSC.byKey(TradeTableSC.ELDER_LITHO_MODULE);
        if (r == null || coord == null || scheme == null || module == null) {
            print(false, "deals (ПО-17..ПО-19): the deals cartArenaCoordinator / elderTunerScheme / elderLithoModule in TradeTableSC " + (coord != null) + " / "
                    + (scheme != null) + " / " + (module != null));
            return;
        }
        WorldTestArmourerSC.StationPlayer dealer = station("deals-dealer", dock == null ? 0 : dock.x, dock == null ? 64 : dock.y + 1, dock == null ? 0 : dock.z);
        if (cart == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the Cartographer's Arena Coordinator - no Cartographer loaded");
        } else {
            SettlementRegistrySC.Settlement s = dealerAt(r, dealer, cart, ReputationSC.HONOURED_FROM);
            int first = offerTrade(dealer, cart, s, coord), got = held(dealer, FinaleItemsSC.arenaCoordinator(1));
            clear(dealer);
            int second = offerTrade(dealer, cart, s, coord);
            clear(dealer);
            print(first == TradeServerSC.OK && got == 1 && second == TradeServerSC.R_LIMIT, "deals (ПО-17): at " + SettlementSC.displayName(s).getUnformattedText()
                    + "'s Cartographer at «Почётный» - the Arena Coordinator " + first + " (OK " + TradeServerSC.OK + "), got " + got + " (1); a second the same day "
                    + second + " (R_LIMIT " + TradeServerSC.R_LIMIT + ")");
        }
        if (elder == null) {
            System.out.println("[SC-WORLDTEST] SKIP finale: the Elder's «Наладчик» and Module - no Elder loaded");
            return;
        }
        SettlementRegistrySC.Settlement s = dealerAt(r, dealer, elder, ReputationSC.HONOURED_FROM);
        int capBefore = CrumbLimitSC.left(dealer);
        int tuner = offerTrade(dealer, elder, s, scheme), schemes = held(dealer, FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_TUNER, 1));
        int capAfter = CrumbLimitSC.left(dealer);
        clear(dealer);
        int m1 = offerTrade(dealer, elder, s, module);
        int m2 = offerTrade(dealer, elder, s, module), modules = held(dealer, FinaleItemsSC.lithoModule(1));
        clear(dealer);
        print(tuner == TradeServerSC.OK && schemes == 1 && capAfter == capBefore && m1 == TradeServerSC.OK && m2 == TradeServerSC.OK && modules == 2, "deals"
                + " (ПО-18, ПО-19): at " + SettlementSC.displayName(s).getUnformattedText() + "'s Elder - «Наладчик» " + tuner + " (OK), got " + schemes + " (1), the"
                + " day's Crumb cap left " + capBefore + " -> " + capAfter + " (the same); the Lithographers' Module twice " + m1 + " / " + m2 + " (OK / OK, no"
                + " limit), got " + modules + " (2)");
    }

    /** A loaded live Tuner of `kind` bound to a registry settlement (not a caravan's), or null. */
    private EntityTunerSC loadedTuner(SettlementRegistrySC r, NpcKindSC kind) {
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityTunerSC && !(o instanceof EntityCaravanMasterSC) && ((EntityTunerSC) o).kind() == kind && !((Entity) o).isDead
                    && r != null && r.byId(((EntityTunerSC) o).settlementId()) != null) {
                return (EntityTunerSC) o;
            }
        }
        return null;
    }

    /** The dealer at `tuner` with `reputation`, no enmity and the day's counters fresh there. @return the tuner's settlement */
    private SettlementRegistrySC.Settlement dealerAt(SettlementRegistrySC r, EntityPlayerMP dealer, EntityTunerSC tuner, int reputation) {
        SettlementRegistrySC.Settlement s = r.byId(tuner.settlementId());
        SettlementRegistrySC.PlayerRec pr = r.player(dealer.getUniqueID());
        long today = SettlementSC.day(w);
        pr.reputation = reputation;
        pr.rollDay(today);
        pr.chips = pr.repTrades = 0;
        SettlementRegistrySC.LocalRec lr = s.local(dealer.getUniqueID());
        lr.enemyUntil = 0L;
        lr.rollDay(today - 1);
        lr.rollDay(today);
        dealer.setLocationAndAngles(tuner.posX, tuner.posY, tuner.posZ, 0F, 0F);
        clear(dealer);
        return s;
    }

    private static int held(EntityPlayerMP p, ItemStack want) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && want != null && s.getItem() == want.getItem() && s.getItemDamage() == want.getItemDamage() ? s.stackSize : 0;
        }
        return n;
    }

    private static void clear(EntityPlayerMP p) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            p.inventory.mainInventory[i] = null;
        }
    }

    private static int heldWide(EntityPlayerMP p) {
        ItemStack want = ModItems.empModule == null ? null : ModItems.empModule.stackOf(EmpModule.WIDE);
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            n += s != null && want != null && s.getItem() == want.getItem() && s.getItemDamage() == want.getItemDamage() ? s.stackSize : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ 10. requireSignal (ПВ-13)

    private void requireSignal() {
        WorldTestArmourerSC.StationPlayer viewer = station("nav-viewer", 0, 80, 0);
        viewer.getEntityData().removeTag(EntityPlayerMP.PERSISTED_NBT_TAG);
        NBTTagList dims = new NBTTagList();
        for (int id : new int[]{0, SubstrateSC.dimId(), -1}) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id);
            dims.appendTag(t);
        }
        boolean was = SubstrateConfigSC.requireSignal;
        boolean hidden, refused, shown, open, inside, recordUsed;
        try {
            SubstrateConfigSC.requireSignal = true;
            hidden = SubstrateSignalSC.filterDims(dims, viewer, 0).tagCount() == 2;
            refused = SubstrateSignalSC.refuses(viewer, SubstrateSC.dimId(), 0);
            ItemStack record = FinaleItemsSC.navRecord(2);
            viewer.inventory.mainInventory[0] = record;
            viewer.inventory.currentItem = 0;
            ItemStack back = record == null ? null : record.getItem().onItemRightClick(record, w, viewer);
            recordUsed = SubstrateSignalSC.knows(viewer) && back != null && back.stackSize == 1;
            if (!SubstrateSignalSC.knows(viewer)) {
                SubstrateSignalSC.grant(viewer);                        // package P's item not written yet: the core's grant, to go on
            }
            shown = SubstrateSignalSC.filterDims(dims, viewer, 0).tagCount() == 3;
            open = !SubstrateSignalSC.refuses(viewer, SubstrateSC.dimId(), 0);
            inside = SubstrateSignalSC.openTo(station("nav-stranger", 0, 80, 0), SubstrateSC.dimId());
        } finally {
            SubstrateConfigSC.requireSignal = was;
        }
        print(hidden && refused && recordUsed && shown && open && inside, "requireSignal (ПВ-13): the rule on - the list for a viewer without the record lacks"
                + " the Substrate " + hidden + ", an end there refused " + refused + "; the Navigation Record used (spent one, package P's item) " + recordUsed
                + " - listed " + shown + ", reached " + open + "; a bridge in the Substrate - open to a stranger " + inside + "; the rule back to " + was
                + " (the controller's own screen lists: in game)");
    }

    // ------------------------------------------------------------------ 11. the Lithographers' Module (ПО-20)

    private void module() {
        TileEntityFieldGeneratorSC te = new TileEntityFieldGeneratorSC();      // no world: nothing of it is placed
        int without = te.upkeepPerTick();
        ItemStack module = FinaleItemsSC.lithoModule(1);
        boolean valid = te.isItemValidForSlot(0, module);
        te.setInventorySlotContents(0, module);
        int with = te.upkeepPerTick();
        te.setInventorySlotContents(1, FinaleItemsSC.lithoModule(1));
        int two = te.upkeepPerTick();
        print(valid && te.hasLithoModule() && with == FinaleRulesSC.upkeep(without, true) && with < without && two == with, "module (ПО-20): a field's upkeep "
                + without + " EU/t, with the Lithographers' Module in a slot " + with + " (x" + FinaleRulesSC.UPKEEP_SHARE + ", up), a second one " + two
                + " (no more); the slot takes it " + valid);
    }

    // ------------------------------------------------------------------ 12. the ПЭ-1М trophy (ПТ-26)

    private void pe1m() {
        int meta = TrophyTableSC.META_PE1M;
        boolean shown = TrophyTableSC.shown(meta);
        int armourer = TrophyRestoreSC.refusal(meta, TrophyRestoreSC.WHERE_ARMOURER), plain = TrophyRestoreSC.refusal(meta, TrophyRestoreSC.WHERE_STATION),
                singular = TrophyRestoreSC.refusal(meta, TrophyRestoreSC.WHERE_SINGULAR_STATION);
        ItemStack out = TrophyRestoreSC.restored(meta);
        StringBuilder modules = new StringBuilder();
        if (out != null) {
            for (EmpModule m : EmpModule.values()) {
                if (ItemEmpGunSC.hasModule(out, m)) {
                    modules.append(' ').append(m.name());
                }
            }
        }
        boolean real = out != null && out.getItem() == ModItems.empGun && out.getItemDamage() == ItemEmpGunSC.META_UPGRADED && modules.length() == 0
                && ItemEmpGunSC.chargeOf(out) == 0;
        print(shown && armourer == TrophyRestoreSC.R_SINGULAR_ONLY && plain == TrophyRestoreSC.R_SINGULAR_ONLY && singular == TrophyRestoreSC.OK && real,
                "pe1m (ПТ-26): the trophy 24 shown " + shown + "; refused at the Armourer " + armourer + " and the plain station " + plain + " (R_SINGULAR_ONLY "
                + TrophyRestoreSC.R_SINGULAR_ONLY + "), the Singular station's " + singular + " (OK); restored - " + (out == null ? "nothing" : out.getDisplayName()
                + " meta " + out.getItemDamage() + ", charge " + ItemEmpGunSC.chargeOf(out)) + (modules.length() == 0 ? ", no modules" : ", modules" + modules)
                + " (the station's two clicks: in game)");
    }

    // ------------------------------------------------------------------ 13. the progress (ПК-23)

    private void progress() {
        Entity e = EntityList.createEntityByName("SiliconAge.trooper", w);
        if (!(e instanceof EntityLiving) || !(e instanceof IArmoredMobSC)) {
            print(false, "progress - no Trooper made");
            return;
        }
        EntityLiving mob = (EntityLiving) e;
        mob.setLocationAndAngles(0.5, 200, 0.5, 0F, 0F);
        mob.onSpawnWithEgg(null);
        boolean before = SingularProgressSC.suppressedArmoured(mob);
        com.sc.mob.MobArmorSC a = ((IArmoredMobSC) mob).mobArmor();
        if (a != null) {
            for (int k = 0; k < a.suppressedUntil.length; k++) {
                a.suppressedUntil[k] = w.getTotalWorldTime() + 200L;
            }
        }
        boolean after = SingularProgressSC.suppressedArmoured(mob);
        Entity crab = EntityList.createEntityByName("SiliconAge.waferCrab", w);
        boolean imob = mob instanceof IMob && !(crab instanceof IMob) && !SingularProgressSC.suppressedArmoured(crab instanceof EntityLivingBase
                ? (EntityLivingBase) crab : null);
        print(!before && after && imob, "progress (ПК-23): an armoured Trooper - suppressedArmoured " + before + " (false), a piece suppressed " + after
                + " (true); the armoured enemies are IMob (C_KILLS counts them), the crab neither " + imob + " (the kills themselves need a real player: in game)");
    }

    // ------------------------------------------------------------------ the fight's creatures

    /** The Warden of the arena's current fight (loaded, alive), or null. */
    private EntityBreakdownWardenSC warden() {
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityBreakdownWardenSC && !((Entity) o).isDead && ((EntityBreakdownWardenSC) o).bound()
                    && Math.hypot(((Entity) o).posX - ax, ((Entity) o).posZ - az) < ArenaLayoutSC.HALF + 8) {
                Object id = call(o, "fightId");
                if (!(id instanceof Long) || arena == null || ((Long) id).longValue() == arena.fightId) {
                    return (EntityBreakdownWardenSC) o;
                }
            }
        }
        return null;
    }

    private List<EntityContainmentCoilSC> coils() {
        List<EntityContainmentCoilSC> out = new ArrayList<EntityContainmentCoilSC>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityContainmentCoilSC && !((Entity) o).isDead && Math.hypot(((Entity) o).posX - ax, ((Entity) o).posZ - az) < ArenaLayoutSC.HALF + 2) {
                out.add((EntityContainmentCoilSC) o);
            }
        }
        return out;
    }

    /** A coil's state: package B's coilState(), else its DataWatcher slot (DW_STATE, fixed). */
    private int coilState(EntityContainmentCoilSC c) {
        Object s = call(c, "coilState");
        if (s instanceof Integer) {
            return ((Integer) s).intValue();
        }
        return c.getDataWatcher().getWatchableObjectByte(EntityContainmentCoilSC.DW_STATE);
    }

    // ------------------------------------------------------------------ helpers

    private void force(int cx, int cz, int r) {
        ForgeChunkManager.Ticket t = null;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
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

    /** Every chunk of `box` (chunk coords) and its ring made (so the box is populated); @return chunks loaded past the ring (a cascade) */
    private int generate(int[] box) {
        WorldTestSettlementSC.ChunkWatch watch = new WorldTestSettlementSC.ChunkWatch();
        watch.start(w);
        MinecraftForge.EVENT_BUS.register(watch);
        try {
            for (int cx = box[0] - 1; cx <= box[2] + 1; cx++) {
                for (int cz = box[1] - 1; cz <= box[3] + 1; cz++) {
                    w.getChunkFromChunkCoords(cx, cz);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int outside = 0;
        for (Long k : watch.loaded) {
            int x = (int) (k.longValue() >> 32), z = (int) k.longValue();
            if (x < box[0] - 1 || x > box[2] + 1 || z < box[1] - 1 || z > box[3] + 1) {
                outside++;
            }
        }
        return outside;
    }

    /** No chunk of [x0..x1] x [z0..z1] (chunk coords, and their ring) exists yet (on disk or loaded). */
    private boolean fresh(int x0, int z0, int x1, int z1) {
        IChunkLoader l = w.theChunkProviderServer.currentChunkLoader;
        for (int cx = x0 - 1; cx <= x1 + 1; cx++) {
            for (int cz = z0 - 1; cz <= z1 + 1; cz++) {
                if (w.theChunkProviderServer.chunkExists(cx, cz) || l instanceof AnvilChunkLoader && ((AnvilChunkLoader) l).chunkExists(w, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A structure start of `gen` at chunk (cx, cz), as the world would make it now (MapGenStructure.getStructureStart); null if it can't be asked. */
    private StructureStart startAt(MapGenStructure gen, int cx, int cz) {
        try {
            ReflectionHelper.setPrivateValue(MapGenBase.class, gen, w, "worldObj", "field_75039_c");
            Random r = new Random(seed);
            long a = r.nextLong(), b = r.nextLong();
            r.setSeed((long) cx * a ^ (long) cz * b ^ seed);
            ReflectionHelper.setPrivateValue(MapGenBase.class, gen, r, "rand", "field_75038_b");
            Method m = ReflectionHelper.findMethod(MapGenStructure.class, gen, new String[]{"getStructureStart", "func_75049_b"}, int.class, int.class);
            return (StructureStart) m.invoke(gen, Integer.valueOf(cx), Integer.valueOf(cz));
        } catch (Throwable t) {
            System.out.println("[SC-WORLDTEST] INFO finale: a structure start couldn't be asked for (" + t + ")");
            return null;
        }
    }

    /** A real (not Fake) test player that never ticks and isn't in the server's list; its chat kept (WorldTestArmourerSC's). */
    private WorldTestArmourerSC.StationPlayer station(String name, double x, double y, double z) {
        WorldTestArmourerSC.StationPlayer p = new WorldTestArmourerSC.StationPlayer(w, profile(name));
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        return p;
    }

    /** A test FakePlayer that takes damage, listed in its chunk (a search finds it), never spawned. */
    private WorldTestCombatSC.HurtablePlayer hurtable(String name, int x, int y, int z) {
        WorldTestCombatSC.HurtablePlayer p = new WorldTestCombatSC.HurtablePlayer(w, profile(name));
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, p, 0, "field_147101_bU");
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        p.setHealth(p.getMaxHealth());
        list(p);
        return p;
    }

    private static IntHashMap idMap(WorldServer w) {
        return (IntHashMap) ReflectionHelper.getPrivateValue(WorldServer.class, w, "entityIdMap", "field_73066_T");
    }

    private void list(Entity e) {
        Chunk c = w.getChunkFromBlockCoords((int) Math.floor(e.posX), (int) Math.floor(e.posZ));
        c.addEntity(e);
        idMap(w).addKey(e.getEntityId(), e);
        if (!listed.contains(e)) {
            listed.add(e);
        }
    }

    private void unlist(Entity e) {
        if (e == null) {
            return;
        }
        Chunk c = w.getChunkFromBlockCoords((int) Math.floor(e.posX), (int) Math.floor(e.posZ));
        c.removeEntity(e);
        if (idMap(w).lookup(e.getEntityId()) == e && !w.loadedEntityList.contains(e)) {
            idMap(w).removeObject(e.getEntityId());
        }
    }

    /** A death by `src` whatever the shield and the reserve would take (its onDeath: the events, the drops). */
    private static void kill(EntityLivingBase e, DamageSource src) {
        e.setHealth(0F);
        e.onDeath(src);
        e.setDead();
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("sc-finale-" + name).getBytes()), "[SCTestFinale]");
    }

    // ------------------------------------------------------------------ the packages' helpers by name (built on the core alone)

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name, true, WorldTestFinaleSC.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Public method `name` of `target` (an instance, or a Class for a static one) taking these arguments, invoked; NONE if there is none. */
    private Object call(Object target, String name, Object... args) {
        Class<?> c = target instanceof Class ? (Class<?>) target : target == null ? null : target.getClass();
        if (c == null) {
            missing.add("?." + name);
            return NONE;
        }
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length || !fits(m.getParameterTypes(), args)) {
                continue;
            }
            try {
                return m.invoke(target instanceof Class ? null : target, args);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(c.getSimpleName() + "." + name + ": " + e.getCause(), e.getCause());
            } catch (Exception e) {
                missing.add(c.getSimpleName() + "." + name + " (" + e + ")");
                return NONE;
            }
        }
        missing.add(c.getSimpleName() + "." + name);
        return NONE;
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (a == null) {
                if (t.isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> want = !t.isPrimitive() ? t : t == int.class ? Integer.class : t == long.class ? Long.class : t == boolean.class ? Boolean.class
                    : t == float.class ? Float.class : t == double.class ? Double.class : t == byte.class ? Byte.class : t == short.class ? Short.class
                    : Character.class;
            if (!want.isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    /** Public field `name` of `target`, null if none. */
    private Object field(Object target, String name) {
        try {
            return target == null ? null : target.getClass().getField(name).get(target);
        } catch (Throwable t) {
            missing.add((target == null ? "?" : target.getClass().getSimpleName()) + "." + name);
            return null;
        }
    }

    /** Sets public field `name` of `target`. @return it was there */
    private boolean setField(Object target, String name, Object v) {
        try {
            if (target == null || target == NONE) {
                return false;
            }
            target.getClass().getField(name).set(target, v);
            return true;
        } catch (Throwable t) {
            missing.add((target == null ? "?" : target.getClass().getSimpleName()) + "." + name);
            return false;
        }
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " finale: " + what);
    }
}
