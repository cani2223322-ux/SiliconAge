package com.sc.debug;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.SCMod;
import com.sc.block.BlockShowcaseSC;
import com.sc.block.BlockSingularGlassSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.dim.SubstrateWildTerrainSC;
import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.IEmpUselessSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityOverheatedSC;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.EntitySentinelSC;
import com.sc.mob.EntityWarlordSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.RareRegistrySC;
import com.sc.mob.ScrapperSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.radiation.RadiationSC;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.CampLayoutSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.MapGenCampSC;
import com.sc.ruin.MapGenVaultSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinWorldSC;
import com.sc.ruin.StructureRuinStartSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.tileentity.TileEntityArcCrackSC;
import com.sc.tileentity.TileEntityShowerSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;
import com.sc.tileentity.TileEntitySubstrateVentSC;
import com.sc.tileentity.TileEntityTankSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.StatCollector;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * PACKAGE D (docs/plan-substrate-tech-f4.md §11.3): Ф4's wild land on a real server (-Dsc.worldtest=true, after
 * WorldTestHazardSC) - the five biomes registered, a cell's kind kept and a legacy cell left the plateaus, the Ridges' / Oxide
 * Fields' / Rift's terrain (no Blocks.stone, no cascade), the vents (a fumarole fills a tank, a crack drains armour and bites, a
 * geyser discharges, an RTG radiates), the monocrystal's drop, a camp (its Stolen Field: the owner and the flags, no push, the
 * Renegades' shots pass, a stranger breaks nothing, the safe, the key, the Warlord's death and return), a Vault and its
 * Sentinel, the new creatures (HP, armour, the Phantom and the Leech under a ПЭ-1, the Strider without a storm, the Scrapper
 * taking a death's things). Output "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO wild: ...".
 * <ol>
 * <li>registration: the five biomes in their slots with WASTELAND / DRY / SPARSE, the ten names in EntityList, the structures
 * SCSi14Camp / SCSi14Vault, the blocks and the tiles;</li>
 * <li>the cells: fresh Ridges / Oxide / Rift cells (pureKind, nothing explored) - an inner block of chunks made: the chunks'
 * own biome = the kind, no Blocks.stone in Y 0..160, the tops by the pure shapes, the Rift's glass floor and an island; no chunk
 * loaded past the window + 1 while they were made; a cell whose inner chunk was made before its decision - LEGACY, plateaus;</li>
 * <li>the vents at Y 200 over the test's place: a fumarole beside a tank of the mod - 5..10 mB of helium in 100 ticks; a crack
 * - a Nano suit within 2 loses charge, a bare player takes «Пробой»; a geyser bites within 6..10 s; an RTG radiates; the
 * monocrystal drops Metallurgical Silicon;</li>
 * <li>a camp generated on fresh land: the Stolen Field (owner, exactly F_PRIVATE | F_EMP_SCREEN | F_SHOW), its Marauders stay
 * under it, a Marauder's arrow passes, a skeleton's turns, a FakePlayer's break is cancelled; the panels; the safe - no key,
 * the field up, the field down and open (meta 3); the Warlord ALIVE, killed - DEAD, its time moved back 3 days - back;</li>
 * <li>a Vault generated under a fresh Node: the plating, its Sentinel ALIVE, a chest; killed by a FakePlayer - no Fragment;</li>
 * <li>the creatures: each of the ten by onSpawnWithEgg (HP, armour of the armoured), the Phantom under a Point - dead, the
 * Leech under a Pulse - burst (a player beside it hurt), the Colossus useless to the ПЭ-1, the Strider without a storm gone
 * within 600 ticks, the Scrapper: a death's things marked, picked up after 60 s, given back by its death;</li>
 * <li>the integrator's: the Rift cell's pillar core lit after its populate, no spawn spot on the void's floor or the pillar, spots
 * on the islands; the flora only on the rock (shears take it, it crumbles with its rock); the gate's shower washes the dose.</li>
 * </ol>
 * Built on the core alone (packages W / M / N / C write the bodies - a missing one fails its own lines). The test players are
 * never spawned (a vent's or a creature's search finds them listed in their chunk, as WorldTestCombatSC's targets); every
 * ticket released, every block placed removed, the test's creatures and items removed. What the world generated stays (the
 * test world's camp and Vault). WorldTestFinaleSC waits for `finished`.
 */
public final class WorldTestWildSC {

    /** Never before this; and after WorldTestHazardSC is finished. */
    static final int START = WorldTestHazardSC.START + 600;
    private static final int[] AT = {0, 40, 100, 240, 520, 800, ScrapperSC.ARRIVE_TICKS + 3 * ScrapperSC.CHECK_TICKS + 400};
    private static final int TIMEOUT = 3600;
    private static final String[] MOB_NAMES = {"overheated", "warlord", "sentinel", "eclipse", "phantom", "leech", "colossus", "strider", "scrapper", "waferCrab"};
    private static final double[] MOB_HP = {30, 90, 100, 140, 20, 30, 120, 60, 60, 8};
    /** Ф5 (docs/plan-substrate-tech-f5.md §11.2): done or skipped - WorldTestFinaleSC begins after it. */
    static volatile boolean finished;

    private int ticks, stage, startAt;
    private boolean done, started;
    private WorldServer w;
    private int bx, by, bz;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private final List<int[]> placed = new ArrayList<int[]>();
    private final List<Entity> made = new ArrayList<Entity>(), listed = new ArrayList<Entity>();
    /** The vents. */
    private int fx, fy, fz;
    private WorldTestCombatSC.HurtablePlayer suited, bare, geyserBare;
    private FakePlayer rtgPlayer;
    private int suitedCharge;
    private float bareHp, geyserHp;
    /** The flora and the gate's shower (the integrator's). */
    private static final float SHOWER_DOSE = 50F;
    private boolean floraOk;
    private String floraLine = "-";
    private WorldTestCombatSC.HurtablePlayer showered;
    /** The fresh Rift cell of cells(), for rift(). */
    private int[] riftCell;
    /** The camp. */
    private CampLayoutSC.Site camp;
    private TileEntityStolenFieldSC field;
    private final List<EntityMarauderSC> residents = new ArrayList<EntityMarauderSC>();
    private EntityWarlordSC warlord;
    private int[] safe;
    private int panelsOut;
    private final List<int[]> panelSpots = new ArrayList<int[]>();
    private WorldTestArmourerSC.StationPlayer opener;
    /** The creatures. */
    private Entity strider;
    private EntityScrapperSC scrapperSeen;
    private final List<EntityItem> deathDrops = new ArrayList<EntityItem>();
    private final Set<Integer> scrappersBefore = new HashSet<Integer>();
    /** The Scrapper's victim's column and its top; the floor it dies on (the test's own glass, 49 x 49 at Y 220 - its ticket's chunks). */
    private int vx, vy, vz;
    private static final int PLATFORM_Y = 220, PLATFORM_R = 24;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        ticks++;
        if (ticks < START || !WorldTestHazardSC.finished) {
            return;
        }
        try {
            if (!started) {
                started = true;
                startAt = ticks;
                start();
                stage = 1;
            } else if (stage < AT.length && ticks - startAt >= AT[stage]) {
                stage(stage++);
                if (stage >= AT.length) {
                    finish();
                }
            } else if (ticks - startAt > TIMEOUT) {
                print(false, "timed out at stage " + stage);
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
            System.out.println("[SC-WORLDTEST] SKIP wild: the dimension is off (" + SubstrateSC.offReason() + ")");
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
        bx = -27 * SubstrateCellsSC.CELL + 128;
        bz = 33 * SubstrateCellsSC.CELL + 128;
        force(bx >> 4, bz >> 4, 2);
        by = ground(bx, bz);
        run("registration", new Runnable() {
            public void run() {
                registration();
            }
        });
        run("cells", new Runnable() {
            public void run() {
                cells();
            }
        });
        run("rift", new Runnable() {
            public void run() {
                rift();
            }
        });
        run("vents", new Runnable() {
            public void run() {
                ventsPlace();
            }
        });
        run("camp", new Runnable() {
            public void run() {
                campMake();
            }
        });
        run("vault", new Runnable() {
            public void run() {
                vault();
            }
        });
        run("creatures", new Runnable() {
            public void run() {
                creatures();
            }
        });
    }

    private void stage(int s) {
        switch (s) {
            case 1:                                         // +40: the camp's dome held its own; the safe without a key / with the field up
                run("camp field", new Runnable() {
                    public void run() {
                        campField();
                    }
                });
                break;
            case 2:                                         // +100: the vents worked; the field down - the safe opens; the Warlord dies
                run("vents check", new Runnable() {
                    public void run() {
                        ventsCheck();
                    }
                });
                run("camp safe", new Runnable() {
                    public void run() {
                        campSafe();
                    }
                });
                break;
            case 3:                                         // +240: the geyser has fired at least once
                run("geyser", new Runnable() {
                    public void run() {
                        geyserCheck();
                    }
                });
                break;
            case 4:                                         // +520: the camp's Warlord back (its death moved 3 days back)
                run("camp return", new Runnable() {
                    public void run() {
                        campReturn();
                    }
                });
                break;
            case 5:                                         // +800: the Strider gone without a storm
                run("strider", new Runnable() {
                    public void run() {
                        striderCheck();
                    }
                });
                break;
            default:                                        // the Scrapper has come for the things
                run("scrapper", new Runnable() {
                    public void run() {
                        scrapperCheck();
                    }
                });
                break;
        }
    }

    /** One part by itself: an exception fails it alone. */
    private void run(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            print(false, what + " - exception " + t);
            t.printStackTrace();
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (w != null) {
            for (int[] p : placed) {
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
        for (EntityItem e : deathDrops) {
            if (!e.isDead) {
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
        finished = true;
        System.out.println("[SC-WORLDTEST] DONE wild");
    }

    // ------------------------------------------------------------------ 1. registration (§1, §2.2)

    @SuppressWarnings("unchecked")
    private void registration() {
        StringBuilder bad = new StringBuilder();
        int[] want = {SubstrateConfigSC.biomeIdPlateaus(), SubstrateConfigSC.biomeIdCanyons(), SubstrateConfigSC.biomeIdRidges(), SubstrateConfigSC.biomeIdOxide(),
                SubstrateConfigSC.biomeIdRift()};
        BiomeGenBase[] slots = BiomeGenBase.getBiomeGenArray();
        Set<Integer> ids = new HashSet<Integer>();
        for (int k = 0; k < BiomeSubstrateSC.KINDS; k++) {
            BiomeSubstrateSC b = SubstrateSC.biome(k);
            if (b == null || b.kind != k || slots[b.biomeID] != b || b.biomeID != want[k] || !ids.add(b.biomeID)) {
                bad.append(" biome ").append(k).append(b == null ? ":none" : ":" + b.biomeID + " (config " + want[k] + ")");
                continue;
            }
            if (!BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.WASTELAND) || !BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.DRY)
                    || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.MOUNTAIN) || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.HILLS)
                    || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.FOREST) || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.WATER)) {
                bad.append(" types ").append(BiomeSubstrateSC.NAMES[k]);
            }
        }
        Map<String, Class<?>> names = EntityList.stringToClassMapping;
        for (String n : MOB_NAMES) {
            if (!names.containsKey("SiliconAge." + n)) {
                bad.append(" entity ").append(n);
            }
        }
        Map<String, Class<?>> starts = ReflectionHelper.getPrivateValue(MapGenStructureIO.class, null, "field_143040_a", "startNameToClassMap");
        for (String n : new String[]{MapGenCampSC.START_NAME, MapGenVaultSC.START_NAME}) {
            if (!starts.containsKey(n)) {
                bad.append(" structure ").append(n);
            }
        }
        for (String n : new String[]{"singularGlass", "substrateFlora", "substrateVent", "stolenField", "campSC"}) {
            if (Block.blockRegistry.getObject("SiliconAge:" + n) == null || Block.blockRegistry.getObject("SiliconAge:" + n) == Blocks.air) {
                bad.append(" block ").append(n);
            }
        }
        Map<String, Class<?>> tiles = ReflectionHelper.getPrivateValue(TileEntity.class, null, "field_145855_i", "nameToClassMap");
        for (String n : new String[]{"SiliconAge.substrateVent", "SiliconAge.arcCrack", "SiliconAge.stolenField", "SiliconAge.stolenSolar"}) {
            if (!tiles.containsKey(n)) {
                bad.append(" tile ").append(n);
            }
        }
        if (RelicItemsSC.relic == null || RelicItemsSC.ruinFinder == null) {
            bad.append(" items relicSC / ruinFinder");
        }
        print(bad.length() == 0, "registration (§2.2): five biomes " + ids + " in their slots with WASTELAND / DRY (no MOUNTAIN / HILLS), the ten creatures in"
                + " EntityList, the structures " + MapGenCampSC.START_NAME + " / " + MapGenVaultSC.START_NAME + ", the blocks and tiles, the items"
                + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 2. the cells and their terrain (ПМ-10..ПМ-14, ПМ-18)

    @SuppressWarnings("unchecked")
    private void cells() {
        long seed = w.getSeed();
        StringBuilder bad = new StringBuilder(), info = new StringBuilder();
        int[] kinds = {BiomeSubstrateSC.RIDGES, BiomeSubstrateSC.OXIDE, BiomeSubstrateSC.RIFT};
        int[] legacyCell = null;
        for (int kind : kinds) {
            int[] cell = freshCell(kind, null);
            if (cell == null) {
                bad.append(" no fresh ").append(BiomeSubstrateSC.NAMES[kind]).append(" cell");
                continue;
            }
            if (kind == BiomeSubstrateSC.RIDGES) {
                legacyCell = freshCell(kind, cell);
            }
            if (kind == BiomeSubstrateSC.RIFT) {
                riftCell = cell;                                            // rift() looks at its pillar and its spawn spots
            }
            int cx0 = cell[0] * 16 + 6, cz0 = cell[1] * 16 + 6;             // the inner square's chunks 6..8 of the cell's 16
            Set<Long> before = loadedChunks();
            for (int cx = cx0; cx <= cx0 + 2; cx++) {
                for (int cz = cz0; cz <= cz0 + 2; cz++) {
                    w.theChunkProviderServer.loadChunk(cx, cz);
                }
            }
            Set<Long> after = loadedChunks();
            after.removeAll(before);
            for (Long k : after) {
                int x = (int) (long) k, z = (int) (k >> 32);
                if (x < cx0 - 1 || x > cx0 + 3 || z < cz0 - 1 || z > cz0 + 3) {
                    bad.append(" cascade ").append(x).append(',').append(z);
                }
            }
            force(cx0 + 1, cz0 + 1, 1);
            int biomeId = SubstrateSC.biome(kind).biomeID, stone = 0, wrongBiome = 0, heights = 0, cols = 0, glass = 0, voids = 0, island = 0;
            for (int cx = cx0; cx <= cx0 + 2; cx++) {
                for (int cz = cz0; cz <= cz0 + 2; cz++) {
                    Chunk ch = w.getChunkFromChunkCoords(cx, cz);
                    byte[] biomes = ch.getBiomeArray();
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int x = cx * 16 + lx, z = cz * 16 + lz;
                            wrongBiome += (biomes[lz << 4 | lx] & 255) != biomeId ? 1 : 0;
                            for (int y = 0; y <= 160; y++) {
                                stone += ch.getBlock(lx, y, lz) == Blocks.stone ? 1 : 0;
                            }
                            if ((lx & 3) != 0 || (lz & 3) != 0) {
                                continue;
                            }
                            cols++;
                            int top = topSolid(ch, lx, lz);
                            if (kind == BiomeSubstrateSC.RIFT) {
                                boolean v = SubstrateWildTerrainSC.riftVoid(seed, x, z), pillar = SubstrateWildTerrainSC.pillar(x, z);
                                int[] is = SubstrateWildTerrainSC.islandAt(seed, x, z);
                                if (v && !pillar && is == null) {
                                    voids++;
                                    Block f = ch.getBlock(lx, SubstrateWildTerrainSC.RIFT_FLOOR, lz);
                                    glass += f == SubstrateWildBlocksSC.glass ? 1 : 0;
                                    heights += top <= SubstrateWildTerrainSC.RIFT_FLOOR + 1 ? 0 : 1;
                                } else if (is != null && ch.getBlock(lx, (is[0] + is[1]) / 2, lz) != Blocks.air) {
                                    island++;
                                }
                            } else {
                                int want = kind == BiomeSubstrateSC.RIDGES ? SubstrateWildTerrainSC.ridgeY(seed, x, z) : SubstrateWildTerrainSC.duneY(seed, x, z);
                                heights += Math.abs(top - want) <= 1 ? 0 : 1;
                            }
                        }
                    }
                }
            }
            boolean ok = SubstrateCellsSC.cellKind(w, cell[0], cell[1]) == kind && !SubstrateCellsSC.legacy(w, cell[0], cell[1]) && wrongBiome == 0 && stone == 0
                    && heights * 10 <= cols && (kind != BiomeSubstrateSC.RIFT || voids > 0 && glass == voids);
            info.append(' ').append(BiomeSubstrateSC.NAMES[kind]).append(" cell ").append(cell[0]).append(',').append(cell[1]).append(": biome off ").append(wrongBiome)
                    .append(", stone ").append(stone).append(", tops off ").append(heights).append('/').append(cols)
                    .append(kind == BiomeSubstrateSC.RIFT ? ", void columns " + voids + " with a glass floor " + glass + ", island columns " + island : "").append(ok ? "" : " BAD");
            if (!ok) {
                bad.append(' ').append(BiomeSubstrateSC.NAMES[kind]);
            }
        }
        // ПМ-11: a cell whose inner chunk was made before its decision stays the plateaus for good (LEGACY)
        String legacy = "no second fresh Ridges cell";
        if (legacyCell != null) {
            int cx = legacyCell[0] * 16 + 8, cz = legacyCell[1] * 16 + 8;
            w.theChunkProviderServer.loadChunk(cx, cz);                      // made now (it decides the cell: the Ridges)
            SubstrateCellsSC.forget(w, legacyCell[0], legacyCell[1]);        // as a world of 0.2.1 would have it: the chunk there, no decision
            int k = SubstrateCellsSC.cellKind(w, legacyCell[0], legacyCell[1]);
            boolean leg = SubstrateCellsSC.legacy(w, legacyCell[0], legacyCell[1]);
            legacy = "cell " + legacyCell[0] + "," + legacyCell[1] + " kind " + k + " legacy " + leg;
            if (k != BiomeSubstrateSC.PLATEAUS || !leg) {
                bad.append(" legacy");
            }
        }
        print(bad.length() == 0, "cells (ПМ-10..ПМ-14, ПМ-18): fresh cells made - the chunks' own biome, no Blocks.stone in Y 0..160, the tops by the pure"
                + " shapes, the Rift's glass floor, no chunk loaded past the window + 1" + info + "; ПМ-11 " + legacy + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    /** A cell of pure kind `kind` near the test's place with nothing explored in it (and not `not`), or null. */
    private int[] freshCell(int kind, int[] not) {
        int c0x = bx >> 8, c0z = bz >> 8;
        for (int r = 1; r <= 30; r++) {
            for (int cx = c0x - r; cx <= c0x + r; cx++) {
                for (int cz = c0z - r; cz <= c0z + r; cz++) {
                    if (Math.max(Math.abs(cx - c0x), Math.abs(cz - c0z)) != r || not != null && not[0] == cx && not[1] == cz
                            || SubstrateCellsSC.pureKind(w.getSeed(), cx, cz) != kind) {
                        continue;
                    }
                    int x0 = cx * SubstrateCellsSC.CELL, z0 = cz * SubstrateCellsSC.CELL;
                    if (!StructureRuinStartSC.explored(w, x0, z0, x0 + SubstrateCellsSC.CELL - 1, z0 + SubstrateCellsSC.CELL - 1)
                            && !SubstrateCellsSC.legacy(w, cx, cz)) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return null;
    }

    private Set<Long> loadedChunks() {
        Set<Long> out = new HashSet<Long>();
        for (Object o : w.theChunkProviderServer.loadedChunks) {
            Chunk c = (Chunk) o;
            out.add((long) c.zPosition << 32 | c.xPosition & 0xFFFFFFFFL);
        }
        return out;
    }

    private static int topSolid(Chunk ch, int lx, int lz) {
        for (int y = 255; y > 0; y--) {
            Block b = ch.getBlock(lx, y, lz);
            if (b != Blocks.air && b.getMaterial().blocksMovement()) {
                return y;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ 3. the vents (ПА-4..ПА-7, ПМ-16)

    private void ventsPlace() {
        fx = bx;
        fy = 200;
        fz = bz;
        // the fumarole beside a tank of the mod
        place(fx, fy, fz, SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.FUMAROLE);
        place(fx + 1, fy, fz, ModBlocks.tankSC, 0);
        // the crack: a Nano suit and a bare player within 2
        place(fx, fy, fz + 8, SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.ARC_CRACK);
        suited = hurtable("crack-suited", fx + 1, fy + 1, fz + 8);
        WorldTestCombatSC.wear(suited, ArmorSuit.NANO, 100, false);
        suitedCharge = charge(suited);
        bare = hurtable("crack-bare", fx - 1, fy + 1, fz + 8);
        bareHp = bare.getHealth();
        // the geyser: a bare player within 3; the RTG: a player 3 off
        place(fx, fy, fz + 16, SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.ION_GEYSER);
        geyserBare = hurtable("geyser-bare", fx + 2, fy + 1, fz + 16);
        geyserHp = geyserBare.getHealth();
        place(fx, fy, fz + 24, SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.CRACKED_RTG);
        rtgPlayer = FakePlayerFactory.get(w, profile("rtg"));
        rtgPlayer.setLocationAndAngles(fx + 3.5, fy, fz + 24.5, 0F, 0F);
        // the flora (ПМ-15): only on the Substrate's rock, it crumbles with the rock under it, shears take it (the integrator's)
        Block fl = SubstrateWildBlocksSC.flora;
        place(fx + 4, fy, fz, SubstrateBlocksSC.rock, SubstrateBlocksSC.CRYSTALLITE);
        place(fx + 6, fy, fz, Blocks.dirt, 0);
        boolean onRock = fl.canPlaceBlockAt(w, fx + 4, fy + 1, fz), onDirt = fl.canPlaceBlockAt(w, fx + 6, fy + 1, fz);
        place(fx + 4, fy + 1, fz, fl, SubstrateWildBlocksSC.WIREWEED);
        boolean stood = w.getBlock(fx + 4, fy + 1, fz) == fl;
        List<ItemStack> sheared = stood ? ((IShearable) fl).onSheared(new ItemStack(Items.shears), w, fx + 4, fy + 1, fz, 0) : null;
        boolean shears = sheared != null && sheared.size() == 1 && sheared.get(0).getItem() == Item.getItemFromBlock(fl);
        w.setBlockToAir(fx + 4, fy, fz);                                  // the rock under it gone: it can't stay
        boolean crumbled = stood && w.getBlock(fx + 4, fy + 1, fz) != fl;
        floraOk = onRock && !onDirt && stood && shears && crumbled;
        floraLine = "on the rock " + onRock + ", on dirt " + onDirt + ", shears take it " + shears + ", crumbles with its rock " + crumbled;
        // the gate's shower (ПА-3): a player standing on it loses TileEntityShowerSC.DOSE_PER_SECOND of the dose a second, free
        place(fx - 4, fy, fz, SettlementBlocksSC.showcase, BlockShowcaseSC.SHOWER);
        showered = hurtable("shower", fx - 4, fy + 1, fz);
        RadiationSC.setDose(showered, SHOWER_DOSE);
    }

    private void ventsCheck() {
        TileEntity tank = w.getTileEntity(fx + 1, fy, fz), fum = w.getTileEntity(fx, fy, fz), crack = w.getTileEntity(fx, fy, fz + 8);
        int mb = 0;
        if (tank instanceof TileEntityTankSC && ((TileEntityTankSC) tank).getTank().getFluid() != null
                && ((TileEntityTankSC) tank).getTank().getFluid().getFluid() == ModFluids.liquidHelium) {
            mb = ((TileEntityTankSC) tank).getTank().getFluidAmount();
        }
        int secs = AT[2] / 20;
        boolean helium = fum instanceof TileEntitySubstrateVentSC && mb >= TileEntitySubstrateVentSC.HELIUM_MB_MIN * (secs - 1)
                && mb <= TileEntitySubstrateVentSC.HELIUM_MB_MAX * (secs + 1);
        int chargeNow = charge(suited);
        float lostPct = suitedCharge <= 0 ? 0F : 100F * (suitedCharge - chargeNow) / suitedCharge;
        float wantPct = TileEntityArcCrackSC.DRAIN_TENTHS_PCT / 10F * secs;          // every piece -0.5% a second
        boolean drained = crack instanceof TileEntityArcCrackSC && lostPct >= wantPct * 0.5F && lostPct <= wantPct * 2F;
        boolean hurt = bare.getHealth() < bareHp;
        boolean source = crack instanceof TileEntityArcCrackSC && ((TileEntityArcCrackSC) crack).isEnergySource() && !((TileEntityArcCrackSC) crack).canItemCharge(suited);
        float rad = RadiationSC.levelAt(rtgPlayer);
        // the monocrystal (ПМ-16): Metallurgical Silicon, never the purification chain skipped
        List<ItemStack> drops = SubstrateBlocksSC.rock == null ? new ArrayList<ItemStack>()
                : SubstrateBlocksSC.rock.getDrops(w, fx, fy, fz, SubstrateBlocksSC.MONOCRYSTAL, 0);
        int mgSi = 0;
        ItemStack mg = ModItems.siliconMaterial.stackOf(SiliconMaterial.METALLURGICAL_SI);
        for (ItemStack s : drops) {
            mgSi += s != null && s.getItem() == mg.getItem() && s.getItemDamage() == mg.getItemDamage() ? s.stackSize : 0;
        }
        boolean geode = mgSi >= 1 && mgSi <= 3;
        print(helium && drained && hurt && source && rad >= 1F && geode, "vents (ПА-4..ПА-7, ПМ-16): the fumarole gave the tank " + mb + " mB of liquid helium in "
                + secs + " s (" + TileEntitySubstrateVentSC.HELIUM_MB_MIN + ".." + TileEntitySubstrateVentSC.HELIUM_MB_MAX + " a second) " + helium + "; the crack:"
                + " a Nano suit within " + TileEntityArcCrackSC.HAZARD_RADIUS + " lost " + n(lostPct) + "% (" + n(wantPct) + ") " + drained + ", a bare player hurt "
                + hurt + ", an energy source that charges no item " + source + "; the RTG's radiation 3 off " + n(rad) + " (>= 1); the monocrystal gives "
                + mgSi + " Metallurgical Silicon (1..3) " + geode);
        // the gate's shower: one wash a second (TileEntityShowcaseSC.TICK), a FakePlayer's dose has no decay of its own
        float dose = showered == null ? -1F : RadiationSC.doseOf(showered);
        float hi = SHOWER_DOSE - TileEntityShowerSC.DOSE_PER_SECOND * (secs - 1), lo = SHOWER_DOSE - TileEntityShowerSC.DOSE_PER_SECOND * (secs + 1);
        boolean washed = dose >= lo && dose <= hi;
        print(floraOk && washed, "flora and the shower (ПМ-15, ПА-3): the flora " + floraLine + "; the gate's shower in " + secs + " s - the dose "
                + n(SHOWER_DOSE) + " now " + n(dose) + " (" + n(lo) + ".." + n(hi) + ") " + washed);
    }

    /**
     * The Rift (ПМ-14, the integrator's): the pillar's core glows after its chunk's populate; the spawn director stands nobody on the
     * void's glass floor (it counts in the height map) or the pillar, and finds places on the islands.
     */
    private void rift() {
        if (riftCell == null) {
            print(false, "rift (ПМ-14): no fresh Rift cell from cells()");
            return;
        }
        long seed = w.getSeed();
        int mx = riftCell[0] * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2, mz = riftCell[1] * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
        int samples = 0, core = 0, lit = 0;
        for (int y = 3; y < SubstrateWildTerrainSC.PILLAR_TOP; y += 13) {
            samples++;
            if (w.getBlock(mx, y, mz) == SubstrateWildBlocksSC.glass && w.getBlockMetadata(mx, y, mz) == SubstrateWildBlocksSC.PILLAR_CORE) {
                core++;
                lit += w.getSavedLightValue(EnumSkyBlock.Block, mx, y, mz) >= BlockSingularGlassSC.CORE_LIGHT ? 1 : 0;
            }
        }
        int voids = 0, voidSpots = 0, pillarSpots = 0, islands = 0, islandSpots = 0;
        int x0 = riftCell[0] * SubstrateCellsSC.CELL + 6 * 16, z0 = riftCell[1] * SubstrateCellsSC.CELL + 6 * 16;   // cells()' chunks 6..8
        for (int x = x0; x < x0 + 48; x += 2) {
            for (int z = z0; z < z0 + 48; z += 2) {
                boolean spot = SpawnDirectorSC.spotAt(w, x, z) != null;
                if (SubstrateWildTerrainSC.pillar(x, z)) {
                    pillarSpots += spot ? 1 : 0;
                } else if (SubstrateWildTerrainSC.islandAt(seed, x, z) != null) {
                    islands++;
                    islandSpots += spot ? 1 : 0;
                } else if (SubstrateWildTerrainSC.riftVoid(seed, x, z) && w.getHeightValue(x, z) <= SubstrateWildTerrainSC.RIFT_FLOOR + 1) {
                    voids++;
                    voidSpots += spot ? 1 : 0;
                }
            }
        }
        boolean ok = core == samples && lit == samples && voids > 0 && voidSpots == 0 && pillarSpots == 0 && (islands < 4 || islandSpots > 0);
        print(ok, "rift (ПМ-14, ПС-41): cell " + riftCell[0] + "," + riftCell[1] + " - the pillar's core " + core + " of " + samples + " samples at "
                + mx + " " + mz + ", lit " + BlockSingularGlassSC.CORE_LIGHT + "+ " + lit + "; spawn spots: the void's floor " + voidSpots + " of " + voids
                + " columns (0), the pillar " + pillarSpots + " (0), the islands " + islandSpots + " of " + islands);
    }

    private void geyserCheck() {
        boolean bit = geyserBare.getHealth() < geyserHp;
        print(bit, "geyser (ПА-6): a bare player within " + TileEntitySubstrateVentSC.GEYSER_RADIUS + " - " + n(geyserHp - geyserBare.getHealth()) + " HP in "
                + (AT[3] / 20) + " s (a discharge every " + (TileEntitySubstrateVentSC.GEYSER_MIN / 20) + ".." + (TileEntitySubstrateVentSC.GEYSER_MAX / 20) + " s)");
    }

    private static int charge(EntityPlayer p) {
        int c = 0;
        for (ItemStack s : p.inventory.armorInventory) {
            c += s == null ? 0 : ItemArmorSC.chargeOf(s);
        }
        return c;
    }

    // ------------------------------------------------------------------ 4. a camp (ПЗ-1..ПЗ-7)

    private void campMake() {
        long seed = w.getSeed();
        camp = null;
        int line0 = SubstrateTerrainSC.lineIndex(bz);
        for (int dl = 0; dl <= 12 && camp == null; dl++) {
            for (int sign = -1; sign <= 1 && camp == null; sign += 2) {
                int line = line0 + sign * dl;
                for (int seg = (bx >> 8) - 10; seg <= (bx >> 8) + 10 && camp == null; seg++) {
                    CampLayoutSC.Site s = CampLayoutSC.site(seed, CampLayoutSC.AXIS_X, line, seg);
                    if (s == null) {
                        continue;
                    }
                    int[] b = s.box();
                    if (!StructureRuinStartSC.explored(w, b[0] - 48, b[1] - 48, b[2] + 48, b[3] + 48)) {
                        camp = s;
                    }
                }
            }
        }
        if (camp == null) {
            print(false, "camp (ПЗ-1): no camp on fresh land within 12 lines of the test's place");
            return;
        }
        int[] b = camp.box();
        for (int cx = (b[0] >> 4) - 3; cx <= (b[2] >> 4) + 3; cx++) {
            for (int cz = (b[1] >> 4) - 3; cz <= (b[3] >> 4) + 3; cz++) {
                w.theChunkProviderServer.loadChunk(cx, cz);
            }
        }
        force(camp.x >> 4, camp.z >> 4, Math.max((b[2] - b[0]) >> 5, 2) + 2);
        // the Stolen Field Generator, its residents, its safe
        field = null;
        safe = null;
        for (int x = b[0] - 4; x <= b[2] + 4; x++) {
            for (int z = b[1] - 4; z <= b[3] + 4; z++) {
                for (int y = Math.max(1, camp.floorY - 4); y <= camp.floorY + 12; y++) {
                    TileEntity te = w.getTileEntity(x, y, z);
                    if (te instanceof TileEntityStolenFieldSC && field == null) {
                        field = (TileEntityStolenFieldSC) te;
                    }
                    if (CampBlocksSC.camp != null && w.getBlock(x, y, z) == CampBlocksSC.camp && w.getBlockMetadata(x, y, z) == CampBlocksSC.CAMP_SAFE && safe == null) {
                        safe = new int[]{x, y, z};
                    }
                }
            }
        }
        residents.clear();
        warlord = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityMarauderSC && camp.holds((int) ((Entity) o).posX, (int) ((Entity) o).posZ, 4)) {
                residents.add((EntityMarauderSC) o);
            }
            if (o instanceof EntityWarlordSC && camp.holds((int) ((Entity) o).posX, (int) ((Entity) o).posZ, 8)) {
                warlord = (EntityWarlordSC) o;
            }
        }
        String key = RareRegistrySC.campKey(camp.axis, camp.line, camp.seg);
        RareRegistrySC rr = RareRegistrySC.get(w);
        RareRegistrySC.Rec rec = rr == null ? null : rr.recIfAny(key);
        boolean owner = field != null && TileEntityStolenFieldSC.OWNER.equals(field.getOwner());
        int flags = 0;
        for (int bit = 1; field != null && bit > 0 && bit <= TileEntityFieldGeneratorSC.F_EMP_SCREEN; bit <<= 1) {
            flags |= field.has(bit) ? bit : 0;                              // every switch there is, exactly
        }
        int panels = 0;
        for (Object o : w.loadedTileEntityList) {
            if (o instanceof TileEntityStolenSolarSC && field != null && ((TileEntity) o).getDistanceFrom(field.xCoord, field.yCoord, field.zCoord)
                    <= TileEntityStolenSolarSC.FEED_RANGE * TileEntityStolenSolarSC.FEED_RANGE) {
                panels++;
            }
        }
        boolean residentsOk = residents.size() >= CampLayoutSC.MARAUDERS_MIN && warlord != null && rec != null && rec.state == RareRegistrySC.ALIVE
                && warlord.getUniqueID().equals(rec.uuid());
        print(field != null && owner && flags == TileEntityStolenFieldSC.FLAGS && field.radius() == CampLayoutSC.FIELD_RADIUS && residentsOk && safe != null
                && panels == TileEntityStolenSolarSC.PANELS, "camp (ПЗ-1, ПЗ-2, ПЗ-6): at " + camp.x + ", " + camp.z + " - the Stolen Field " + (field != null)
                + ", owner " + (field == null ? "-" : field.getOwner()) + " " + owner + ", flags " + flags + " (" + TileEntityStolenFieldSC.FLAGS + "), radius "
                + (field == null ? "-" : String.valueOf(field.radius())) + "; " + residents.size() + " Marauders (" + CampLayoutSC.MARAUDERS_MIN + "+), the Warlord "
                + (warlord != null) + " ALIVE in the registry " + (rec != null && rec.state == RareRegistrySC.ALIVE) + "; the safe " + (safe != null) + "; "
                + panels + " stolen panels (" + TileEntityStolenSolarSC.PANELS + ")");
        // the review's: the camp answers a player past its box and on the canyon's rim - every resident's home area the Warlord's
        // (WARLORD_LEASH past the box), a column up to CAMP_RIM over the floor
        int[] mid = rec == null ? new int[0] : rec.extra.getIntArray(RuinWorldSC.X_MID);
        if (mid.length == 3 && !residents.isEmpty()) {
            int reach = CampLayoutSC.HALF_LEN + RuinWorldSC.WARLORD_LEASH, ok = 0, all = 0;
            List<EntityLiving> folk = new ArrayList<EntityLiving>(residents);
            if (warlord != null) {
                folk.add(warlord);
            }
            for (EntityLiving e : folk) {
                EntityCreature c = (EntityCreature) e;
                all++;
                ok += c.isWithinHomeDistance(mid[0] + reach - 2, mid[1], mid[2]) && c.isWithinHomeDistance(mid[0], mid[1] + SubstrateTerrainSC.CANYON_DEPTH_MAX + 2, mid[2])
                        && !c.isWithinHomeDistance(mid[0], mid[1] + RuinWorldSC.CAMP_RIM + 1, mid[2]) && !c.isWithinHomeDistance(mid[0] + reach + 2, mid[1], mid[2]) ? 1 : 0;
            }
            print(ok == all, "camp reach (ПЗ-2): " + ok + " of " + all + " (the Marauders, the Warlord) take a target " + (reach - 2) + " blocks off the middle"
                    + " and " + (SubstrateTerrainSC.CANYON_DEPTH_MAX + 2) + " over the floor (the rim), not past " + reach + " / " + RuinWorldSC.CAMP_RIM);
        }
        // the residents' places now, for the push check; the key's opener (never spawned, a real player: C refuses FakePlayers)
        for (EntityMarauderSC m : residents) {
            m.getEntityData().setDouble("scTestX", m.posX);
            m.getEntityData().setDouble("scTestZ", m.posZ);
        }
    }

    private void campField() {
        if (camp == null || field == null) {
            return;
        }
        // ПЗ-3: nobody is pushed - the field targets none of its residents (nor a Shell, nor a stranger's mob); those under it are still there
        int targeted = 0, under = 0, stayed = 0;
        for (EntityMarauderSC m : residents) {
            targeted += field.targets(m) ? 1 : 0;
            double dx = m.getEntityData().getDouble("scTestX") - (field.xCoord + 0.5), dz = m.getEntityData().getDouble("scTestZ") - (field.zCoord + 0.5);
            if (dx * dx + dz * dz <= field.radius() * field.radius()) {
                under++;
                double ex = m.posX - (field.xCoord + 0.5), ez = m.posZ - (field.zCoord + 0.5);
                stayed += m.isDead || ex * ex + ez * ez <= (field.radius() + 3) * (field.radius() + 3) ? 1 : 0;
            }
        }
        com.sc.mob.EntityLinemanSC shellSide = new com.sc.mob.EntityLinemanSC(w);
        shellSide.setLocationAndAngles(field.xCoord + 2.5, field.yCoord + 1, field.zCoord + 0.5, 0F, 0F);
        boolean noPush = targeted == 0 && !field.targets(shellSide) && !field.targets(warlord == null ? shellSide : warlord) && stayed * 2 >= under;   // most (they may walk off by themselves)
        // ПЗ-3: a Renegade's shot passes, a skeleton's turns (deflects is protected: asked by reflection)
        String shots = "-";
        if (!residents.isEmpty()) {
            try {
                Method d = TileEntityFieldGeneratorSC.class.getDeclaredMethod("deflects", Entity.class);
                d.setAccessible(true);
                EntityArrow own = new EntityArrow(w), other = new EntityArrow(w);
                own.motionX = other.motionX = 1.0D;                          // in flight: a field leaves a resting arrow be
                own.shootingEntity = residents.get(0);
                EntitySkeleton sk = new EntitySkeleton(w);
                other.shootingEntity = sk;
                boolean a = ((Boolean) d.invoke(field, own)).booleanValue(), b = ((Boolean) d.invoke(field, other)).booleanValue();
                shots = (!a && b) + " (own " + a + ", a skeleton's " + b + ")";
            } catch (Throwable t) {
                shots = "deflects not asked: " + t;
            }
        }
        // ПЗ-5: a stranger breaks nothing under it (the dome starts at the generator's own level - its floor is outside, as any dome's)
        FakePlayer stranger = FakePlayerFactory.get(w, profile("camp-stranger"));
        int x = field.xCoord + 2, y = field.yCoord, z = field.zCoord;
        stranger.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, 0F, 0F);
        BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(x, y, z, w, w.getBlock(x, y, z), w.getBlockMetadata(x, y, z), stranger);
        MinecraftForge.EVENT_BUS.post(ev);
        // ПЗ-7: the safe - no key; the key with the field up
        String locked = "-", powered = "-";
        if (safe != null) {
            opener = new WorldTestArmourerSC.StationPlayer(w, profile("camp-opener"));
            opener.setLocationAndAngles(safe[0] + 0.5, safe[1] + 1, safe[2] + 1.5, 0F, 0F);
            locked = said(opener, "sc.camp.safe.locked", null);
            powered = said(opener, "sc.camp.safe.powered", key());
        }
        panelsOut = breakPanels();
        emptyBuffer();
        print(noPush && shots.startsWith("true") && ev.isCanceled() && "true".equals(locked) && "true".equals(powered), "camp field (ПЗ-3..ПЗ-5, ПЗ-7): the"
                + " field targets " + targeted + " of " + residents.size() + " Marauders (0), nor a Lineman / the Warlord; " + stayed + " of the " + under
                + " under it still there after " + (AT[1] / 20) + " s " + noPush + "; shots " + shots
                + "; a FakePlayer's break cancelled " + ev.isCanceled() + "; the safe without a key - locked " + locked + ", with its key and the field up - "
                + powered);
    }

    private void campSafe() {
        if (camp == null || field == null || safe == null) {
            return;
        }
        // the field down (its panels broken, its buffer emptied at the last stage): the safe opens with the key, meta 3, the loot out
        emptyBuffer();
        Set<Integer> before = itemsNear(safe[0], safe[1], safe[2]);
        String opened = said(opener, "sc.camp.safe.opened", key());
        Set<Integer> after = itemsNear(safe[0], safe[1], safe[2]);
        after.removeAll(before);
        int meta = w.getBlockMetadata(safe[0], safe[1], safe[2]);
        print("true".equals(opened) && meta == CampBlocksSC.CAMP_SAFE_OPEN && !after.isEmpty() && !field.isActive(), "camp safe (ПЗ-4, ПЗ-7): " + panelsOut
                + " stolen panel(s) broken, the buffer emptied - the field down " + !field.isActive() + "; the key opens it " + opened + ", meta " + meta + " (" + CampBlocksSC.CAMP_SAFE_OPEN + "), " + after.size() + " stack(s) out");
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityItem && after.contains(((Entity) o).getEntityId())) {
                ((Entity) o).setDead();
            }
        }
        // the Warlord killed - DEAD; its death moved 3 days back for the return (no players near)
        if (warlord != null) {
            kill(warlord, DamageSource.outOfWorld);
            RareRegistrySC.Rec rec = RareRegistrySC.get(w).recIfAny(key0());
            boolean dead = rec != null && rec.state == RareRegistrySC.DEAD;
            if (rec != null) {
                rec.diedAt = w.getTotalWorldTime() - RuinWorldSC.CAMP_RESTORE_TICKS - 20;
                long dayTime = w.getWorldTime() - RuinWorldSC.CAMP_RESTORE_TICKS - 20;
                if (rec.diedAt < 0 || dayTime < 0) {
                    rec.diedAt = Math.min(rec.diedAt, dayTime);
                }
                RareRegistrySC.get(w).markDirty();
            }
            print(dead, "camp Warlord (ПС-30, ПЗ-6): killed - the registry DEAD " + dead);
        }
    }

    private void campReturn() {
        if (camp == null) {
            return;
        }
        RareRegistrySC.Rec rec = RareRegistrySC.get(w).recIfAny(key0());
        EntityWarlordSC back = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityWarlordSC && !((Entity) o).isDead && o != warlord && camp.holds((int) ((Entity) o).posX, (int) ((Entity) o).posZ, 8)) {
                back = (EntityWarlordSC) o;
            }
        }
        int meta = safe == null ? -1 : w.getBlockMetadata(safe[0], safe[1], safe[2]), panels = 0;
        for (int[] pp : panelSpots) {
            panels += CampBlocksSC.camp != null && w.getBlock(pp[0], pp[1], pp[2]) == CampBlocksSC.camp && w.getBlockMetadata(pp[0], pp[1], pp[2]) == CampBlocksSC.STOLEN_SOLAR ? 1 : 0;
        }
        print(back != null && rec != null && rec.state == RareRegistrySC.ALIVE && meta == CampBlocksSC.CAMP_SAFE && panels == panelSpots.size(), "camp return (ПЗ-6):"
                + " 3 days after the Warlord's death, nobody within " + RuinWorldSC.RESPAWN_CLEAR + " - a new Warlord " + (back != null) + ", ALIVE "
                + (rec != null && rec.state == RareRegistrySC.ALIVE) + ", the safe locked again (meta " + meta + "), the broken panels back " + panels + " of "
                + panelSpots.size());
    }

    /** Breaks the camp's stolen panels (ПЗ-4: «разбить панели»); @return how many - their places kept for the return's check. */
    private int breakPanels() {
        panelSpots.clear();
        for (Object o : new ArrayList<Object>(w.loadedTileEntityList)) {
            if (o instanceof TileEntityStolenSolarSC && ((TileEntity) o).getDistanceFrom(field.xCoord, field.yCoord, field.zCoord)
                    <= TileEntityStolenSolarSC.FEED_RANGE * TileEntityStolenSolarSC.FEED_RANGE) {
                TileEntity t = (TileEntity) o;
                panelSpots.add(new int[]{t.xCoord, t.yCoord, t.zCoord});
            }
        }
        for (int[] pp : panelSpots) {
            w.setBlockToAir(pp[0], pp[1], pp[2]);
        }
        return panelSpots.size();
    }

    /** The Stolen Field's buffer emptied (as at midnight). */
    private void emptyBuffer() {
        try {
            java.lang.reflect.Field f = com.sc.energy.TileEntityEnergyBase.class.getDeclaredField("energyStored");
            f.setAccessible(true);
            f.setInt(field, 0);
        } catch (Throwable t) {
            print(false, "camp: the field's buffer not emptied - " + t);
        }
    }

    private String key0() {
        return RareRegistrySC.campKey(camp.axis, camp.line, camp.seg);
    }

    /** The camp's key (ItemRelicSC CAMP_KEY) for this camp's safe. */
    private ItemStack key() {
        ItemStack k = RelicItemsSC.relic(ItemRelicSC.CAMP_KEY, 1);
        if (k != null && safe != null) {
            net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound(), c = new net.minecraft.nbt.NBTTagCompound();
            c.setInteger("x", safe[0]);
            c.setInteger("y", safe[1]);
            c.setInteger("z", safe[2]);
            t.setTag(ItemRelicSC.KEY_NBT, c);
            k.setTagCompound(t);
        }
        return k;
    }

    /** Right-click the safe with `held` in hand; "true" if the opener was told `key`'s line. */
    private String said(WorldTestArmourerSC.StationPlayer p, String key, ItemStack held) {
        p.said.clear();
        p.inventory.currentItem = 0;
        p.inventory.mainInventory[0] = held;
        Block b = w.getBlock(safe[0], safe[1], safe[2]);
        b.onBlockActivated(w, safe[0], safe[1], safe[2], p, 1, 0.5F, 1F, 0.5F);
        String want = StatCollector.translateToLocal(key);
        for (String s : p.said) {
            if (s.contains(want) || s.contains(key)) {
                return "true";
            }
        }
        return "false " + p.said;
    }

    private Set<Integer> itemsNear(int x, int y, int z) {
        Set<Integer> out = new HashSet<Integer>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityItem && ((Entity) o).getDistanceSq(x + 0.5, y + 0.5, z + 0.5) < 36) {
                out.add(((Entity) o).getEntityId());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 5. a Vault and its Sentinel (ПХ-1..ПХ-4)

    private void vault() {
        long seed = w.getSeed();
        int[] v = null;
        int bi0 = floorDiv(bx >> 8, VaultLayoutSC.BLOCK), bj0 = floorDiv(bz >> 8, VaultLayoutSC.BLOCK);
        for (int r = 0; r <= 4 && v == null; r++) {
            for (int bi = bi0 - r; bi <= bi0 + r && v == null; bi++) {
                for (int bj = bj0 - r; bj <= bj0 + r && v == null; bj++) {
                    int[] c = VaultLayoutSC.vaultOf(seed, bi, bj);
                    if (c != null && !StructureRuinStartSC.explored(w, c[0] * 256 - 40, c[1] * 256 - 40, c[0] * 256 + 40, c[1] * 256 + 40)) {
                        v = c;
                    }
                }
            }
        }
        if (v == null) {
            print(false, "vault (ПХ-1): no Vault under a fresh Node within 4 blocks of the test's place");
            return;
        }
        int x = v[0] * SubstrateTerrainSC.GRID, z = v[1] * SubstrateTerrainSC.GRID, f = VaultLayoutSC.floorY(seed, v[0], v[1]);
        for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) {
            for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
                w.theChunkProviderServer.loadChunk(cx, cz);
            }
        }
        force(x >> 4, z >> 4, 2);
        int plating = 0, chests = 0;
        for (int dx = -VaultLayoutSC.HALF; dx <= VaultLayoutSC.HALF; dx++) {
            for (int dz = -VaultLayoutSC.HALF; dz <= VaultLayoutSC.HALF; dz++) {
                for (int y = f; y < f + VaultLayoutSC.HEIGHT; y++) {
                    Block b = w.getBlock(x + dx, y, z + dz);
                    plating += b == RuinBlocksSC.ruin && w.getBlockMetadata(x + dx, y, z + dz) == com.sc.manual.BookWorldSC.RUIN_VAULT_PLATING ? 1 : 0;
                    chests += b == Blocks.chest ? 1 : 0;
                }
            }
        }
        EntitySentinelSC sentinel = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntitySentinelSC && !((Entity) o).isDead && ((Entity) o).getDistanceSq(x, f, z) < 40 * 40) {
                sentinel = (EntitySentinelSC) o;
            }
        }
        RareRegistrySC.Rec rec = RareRegistrySC.get(w).recIfAny(RareRegistrySC.vaultKey(v[0], v[1]));
        boolean alive = rec != null && rec.state == RareRegistrySC.ALIVE && sentinel != null && sentinel.getUniqueID().equals(rec.uuid());
        // killed by a FakePlayer: no Fragment (ПТ-1 - only a real player's kill gives the faction's loot)
        String noFragment = "-";
        if (sentinel != null) {
            FakePlayer killer = FakePlayerFactory.get(w, profile("vault-killer"));
            killer.setLocationAndAngles(sentinel.posX + 1, sentinel.posY, sentinel.posZ, 0F, 0F);
            Set<Integer> before = itemsNear((int) sentinel.posX, (int) sentinel.posY, (int) sentinel.posZ);
            boolean qualifies = MobDropsSC.qualifies(sentinel, killer);
            kill(sentinel, DamageSource.causePlayerDamage(killer));
            int relics = 0;
            for (Object o : w.loadedEntityList) {
                if (o instanceof EntityItem && !before.contains(((Entity) o).getEntityId()) && ((EntityItem) o).getEntityItem() != null
                        && ((EntityItem) o).getEntityItem().getItem() == RelicItemsSC.relic && ((Entity) o).getDistanceSq(sentinel.posX, sentinel.posY, sentinel.posZ) < 64) {
                    relics++;
                    ((Entity) o).setDead();
                }
            }
            noFragment = String.valueOf(!qualifies && relics == 0) + " (" + relics + " relic(s))";
        }
        print(plating > 0 && chests >= 1 && alive && noFragment.startsWith("true"), "vault (ПХ-1..ПХ-4): under crossing " + v[0] + "," + v[1] + " floor " + f + " - "
                + plating + " vault plating blocks, " + chests + " chest(s); the Sentinel there and ALIVE in the registry " + alive + "; killed by a FakePlayer - no"
                + " Fragment " + noFragment);
    }

    // ------------------------------------------------------------------ 6. the creatures (§7, ПБ-22..ПБ-26, ПТ-19)

    private void creatures() {
        StringBuilder bad = new StringBuilder();
        FakePlayer shooter = FakePlayerFactory.get(w, profile("creature-shooter"));
        shooter.setLocationAndAngles(bx + 0.5, by, bz - 6.5, 0F, 0F);
        Entity phantom = null, leech = null, colossus = null, overheated = null;
        for (int i = 0; i < MOB_NAMES.length; i++) {
            Entity e = EntityList.createEntityByName("SiliconAge." + MOB_NAMES[i], w);
            if (!(e instanceof EntityLiving)) {
                bad.append(' ').append(MOB_NAMES[i]).append(":not made");
                continue;
            }
            EntityLiving l = (EntityLiving) e;
            l.setLocationAndAngles(bx + 3.5 + i * 2, by, bz + 12.5, 0F, 0F);
            l.onSpawnWithEgg(null);
            if (Math.abs(l.getMaxHealth() - MOB_HP[i]) > 0.01) {
                bad.append(' ').append(MOB_NAMES[i]).append(":HP ").append(l.getMaxHealth());
            }
            if (i <= 3 && (!(l instanceof EntityArmoredMobSC) || ((EntityArmoredMobSC) l).mobArmor().suit() == null)) {
                bad.append(' ').append(MOB_NAMES[i]).append(":no armour");
            }
            if (i == 0) {
                overheated = l;
            } else if (i == 4) {
                phantom = l;
            } else if (i == 5) {
                leech = l;
            } else if (i == 6) {
                colossus = l;
            }
        }
        // the Phantom: any ПЭ-1 hit kills it (ПБ-22)
        String ph = "-";
        if (phantom instanceof IEmpReactiveSC) {
            list(phantom);
            boolean took = ((IEmpReactiveSC) phantom).empStruck(shooter, EmpMode.POINT, phantom.posX, phantom.posY + 1, phantom.posZ);
            ph = String.valueOf(took && (phantom.isDead || ((EntityLivingBase) phantom).getHealth() <= 0F));
        }
        // the Leech: a Pulse bursts it - a discharge round it, the shooter beside it hurt too (ПБ-23)
        String lc = "-";
        if (leech instanceof IEmpReactiveSC) {
            list(leech);
            WorldTestCombatSC.HurtablePlayer near = hurtable("leech-near", (int) leech.posX + 1, (int) leech.posY, (int) leech.posZ);
            float hp = near.getHealth();
            boolean took = ((IEmpReactiveSC) leech).empStruck(near, EmpMode.PULSE, leech.posX, leech.posY + 0.5, leech.posZ);
            lc = String.valueOf(took && (leech.isDead || ((EntityLivingBase) leech).getHealth() <= 0F) && near.getHealth() < hp);
        }
        boolean useless = colossus instanceof IEmpUselessSC && !(colossus instanceof IEmpReactiveSC);
        print(bad.length() == 0 && "true".equals(ph) && "true".equals(lc) && useless, "creatures (§7): the ten by onSpawnWithEgg - their HP, the armoured four"
                + " dressed" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; the Phantom under a Point dead " + ph + "; the Leech under a Pulse burst, the one"
                + " beside it hurt " + lc + "; the Colossus useless to the ПЭ-1 (IEmpUselessSC) " + useless + " (the gun's own branch: in game)");
        // the review's: the Overheated is one source moving with it (ПА-2) - reported 2 blocks on, its old place adds nothing
        if (overheated != null) {
            int sy = 200;
            double px = bx + 1.5, py = sy + 0.5, pz = bz + 0.5;
            float before = RadiationSC.fromSources(w, px, py, pz);
            RadiationSC.reportEntity(overheated, bx - 2, sy, bz, EntityOverheatedSC.RADIATION_LEVEL, EntityOverheatedSC.RADIATION_RADIUS);
            RadiationSC.reportEntity(overheated, bx, sy, bz, EntityOverheatedSC.RADIATION_LEVEL, EntityOverheatedSC.RADIATION_RADIUS);
            float got = RadiationSC.fromSources(w, px, py, pz) - before;
            float want = EntityOverheatedSC.RADIATION_LEVEL * (1F - 1F / EntityOverheatedSC.RADIATION_RADIUS)
                    * RadiationSC.through(w, bx + 0.5, py, bz + 0.5, px, py, pz, bx, sy, bz);
            print(want > 0F && Math.abs(got - want) < 0.02F, "overheated source (ПА-2): reported at two blocks in turn - " + n(got) + " a block from"
                    + " the last (" + n(want) + " from one source; its old place two blocks further would add " + n(EntityOverheatedSC.RADIATION_LEVEL / 4F) + ")");
        }
        // the Strider: no storm - gone within 600 ticks (spawned for real, the test's own)
        strider = EntityList.createEntityByName("SiliconAge.strider", w);
        if (strider instanceof EntityLiving) {
            strider.setLocationAndAngles(bx + 0.5, w.getHeightValue(bx, bz + 20), bz + 20.5, 0F, 0F);   // on its column's top (the Rift: no fall)
            ((EntityLiving) strider).onSpawnWithEgg(null);
            w.spawnEntityInWorld(strider);
            made.add(strider);
        }
        // the Scrapper (ПТ-19): a death's things in the Substrate marked, the Scrapper comes for them - on a flat floor of the test's own
        // high over its place (the land there is the seed's: a Rift's void or island leaves it no spot SPAWN_MIN..MAX off), the
        // leftovers of an earlier run here removed first
        vx = bx - 8;
        vz = bz - 8;
        vy = PLATFORM_Y + 1;
        for (int x = vx - PLATFORM_R; x <= vx + PLATFORM_R; x++) {
            for (int z = vz - PLATFORM_R; z <= vz + PLATFORM_R; z++) {
                place(x, PLATFORM_Y, z, Blocks.glass, 0);                     // clear glass: no sky light to work out under it
            }
        }
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityScrapperSC) {
                if (flat((Entity) o, vx + 0.5, vz + 0.5) < 64 * 64) {
                    ((Entity) o).setDead();
                } else {
                    scrappersBefore.add(((Entity) o).getEntityId());
                }
            }
        }
        WorldTestArmourerSC.StationPlayer victim = new WorldTestArmourerSC.StationPlayer(w, profile("scrapper-victim"));
        victim.setLocationAndAngles(vx + 0.5, vy, vz + 0.5, 0F, 0F);
        java.util.ArrayList<EntityItem> drops = new java.util.ArrayList<EntityItem>();
        ItemStack[] things = {new ItemStack(Items.diamond), new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[0]), new ItemStack(Blocks.cobblestone, 7)};
        for (ItemStack s : things) {
            EntityItem it = new EntityItem(w, victim.posX + (w.rand.nextDouble() - 0.5) * 0.5, vy + 0.25, victim.posZ + (w.rand.nextDouble() - 0.5) * 0.5, s);
            it.delayBeforeCanPickup = 40;
            it.lifespan = 20 * 60 * 10;
            drops.add(it);
        }
        MinecraftForge.EVENT_BUS.post(new PlayerDropsEvent(victim, DamageSource.generic, drops, false));
        int marked = 0;
        for (EntityItem it : drops) {
            marked += it.getEntityData().hasKey(ScrapperSC.DROP_NBT) && victim.getUniqueID().toString().equals(it.getEntityData().getString(ScrapperSC.DROP_NBT)) ? 1 : 0;
            w.spawnEntityInWorld(it);
            deathDrops.add(it);
        }
        print(marked == drops.size(), "scrapper marks (ПТ-19): " + marked + " of " + drops.size() + " death drops marked " + ScrapperSC.DROP_NBT + " with the owner's"
                + " UUID (" + ScrapperSC.DROP_AT + " the time)");
    }

    private void striderCheck() {
        boolean gone = strider == null || strider.isDead;
        print(strider != null && gone, "strider (ПС-38): without a storm it crumbles within 600 ticks - gone after " + (AT[5] / 20) + " s " + gone);
        if (strider != null && !strider.isDead) {
            strider.setDead();
        }
    }

    private void scrapperCheck() {
        EntityScrapperSC s = null;
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityScrapperSC && !((Entity) o).isDead && !scrappersBefore.contains(((Entity) o).getEntityId())
                    && flat((Entity) o, vx + 0.5, vz + 0.5) < 48 * 48) {
                s = (EntityScrapperSC) o;
            }
        }
        int swallowed = 0;
        for (EntityItem it : deathDrops) {
            swallowed += it.isDead ? 1 : 0;
        }
        String back = "-";
        StringBuilder why = new StringBuilder();
        if (s != null && swallowed < deathDrops.size()) {           // what kept it: where it is, its errand, its target, the things
            why.append(" [the Scrapper at ").append((int) Math.floor(s.posX)).append(' ').append((int) Math.floor(s.posY)).append(' ')
                    .append((int) Math.floor(s.posZ)).append(", home ").append(s.homeX()).append(' ').append(s.homeY()).append(' ').append(s.homeZ())
                    .append(", errand ").append(s.onErrand()).append(", target ").append(s.getAttackTarget()).append(", stunned ").append(s.stunned())
                    .append(", no path ").append(s.getNavigator().noPath()).append(", age ").append(s.ticksExisted).append(", on ground ").append(s.onGround)
                    .append(", a path home now ").append(s.getNavigator().tryMoveToXYZ(s.homeX() + 0.5D, s.homeY(), s.homeZ() + 0.5D, 1.0D)).append(", forced ")
                    .append(w.getPersistentChunks().containsKey(new ChunkCoordIntPair((int) Math.floor(s.posX) >> 4, (int) Math.floor(s.posZ) >> 4)))
                    .append(", under it ").append(Block.blockRegistry.getNameForObject(w.getBlock((int) Math.floor(s.posX), (int) Math.floor(s.posY) - 1,
                            (int) Math.floor(s.posZ)))).append(']');
            int crabs = 0;
            for (Object o : w.loadedEntityList) {
                if (o instanceof EntityScrapperSC && flat((Entity) o, vx + 0.5, vz + 0.5) < 64 * 64) {
                    crabs++;
                    why.append(" [crab ").append(((Entity) o).getEntityId()).append(" age ").append(((Entity) o).ticksExisted).append(((Entity) o).isDead ? " dead" : "")
                            .append(scrappersBefore.contains(((Entity) o).getEntityId()) ? " before" : "").append(']');
                }
            }
            why.append(" crabs near ").append(crabs);
            for (EntityItem it : deathDrops) {
                why.append(" [").append((int) Math.floor(it.posX)).append(' ').append((int) Math.floor(it.posY)).append(' ').append((int) Math.floor(it.posZ))
                        .append(it.isDead ? " dead" : "").append(']');
            }
        }
        if (s != null) {
            Set<Integer> before = itemsNear((int) s.posX, (int) s.posY, (int) s.posZ);
            kill(s, DamageSource.outOfWorld);
            int out = 0;
            for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
                if (o instanceof EntityItem && !before.contains(((Entity) o).getEntityId()) && ((Entity) o).getDistanceSq(s.posX, s.posY, s.posZ) < 64) {
                    out++;
                    ((Entity) o).setDead();
                }
            }
            back = out + " stack(s) out";
        }
        if (s == null) {                                    // what the Scrapper's check saw: the things, the Scrappers about, one more check now
            long now = w.getTotalWorldTime();
            for (EntityItem it : deathDrops) {
                why.append(" [").append((int) Math.floor(it.posX)).append(' ').append((int) Math.floor(it.posY)).append(' ').append((int) Math.floor(it.posZ))
                        .append(" age ").append(now - it.getEntityData().getLong(ScrapperSC.DROP_AT)).append(it.isDead ? " dead" : "").append(']');
            }
            int about = 0;
            for (Object o : w.loadedEntityList) {
                about += o instanceof EntityScrapperSC ? 1 : 0;
            }
            why.append("; Scrappers in the world ").append(about).append(", a check now sends ").append(ScrapperSC.check(w));
            for (Object o : w.loadedEntityList) {
                if (o instanceof EntityScrapperSC && !scrappersBefore.contains(((Entity) o).getEntityId())) {
                    ((Entity) o).setDead();                 // the diagnosis's own
                }
            }
        }
        print(s != null && swallowed == deathDrops.size() && !back.startsWith("0") && !"-".equals(back), "scrapper (ПС-39, ПТ-19):"
                + " a Scrapper came " + (s != null) + ", " + swallowed + " of " + deathDrops.size() + " things swallowed after " + (AT[6] / 20) + " s; killed - "
                + back + why);
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
                w.theChunkProviderServer.loadChunk(x, z);                     // now, not on the next tick
            }
        }
    }

    private int ground(int x, int z) {
        w.getChunkFromBlockCoords(x, z);
        return w.getTopSolidOrLiquidBlock(x, z);
    }

    private void place(int x, int y, int z, Block b, int meta) {
        if (b != null && w.isAirBlock(x, y, z)) {
            w.setBlock(x, y, z, b, meta, 3);
            placed.add(new int[]{x, y, z});
        }
    }

    /** The squared distance across (x, z) from `e` to (x, z). */
    private static double flat(Entity e, double x, double z) {
        return (e.posX - x) * (e.posX - x) + (e.posZ - z) * (e.posZ - z);
    }

    /** A test player that takes damage, listed in its chunk (a vent's or a creature's search finds it), never spawned. */
    private WorldTestCombatSC.HurtablePlayer hurtable(String name, int x, int y, int z) {
        WorldTestCombatSC.HurtablePlayer p = new WorldTestCombatSC.HurtablePlayer(w, profile(name));
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, p, 0, "field_147101_bU");
        p.setLocationAndAngles(x + 0.5, y, z + 0.5, 0F, 0F);
        p.setHealth(p.getMaxHealth());
        list(p);
        return p;
    }

    /** A death by `src` whatever the shield and the reserve would take (its onDeath: the drops, the registry). */
    private static void kill(EntityLivingBase e, DamageSource src) {
        e.setHealth(0F);
        e.onDeath(src);
        e.setDead();
    }

    private static IntHashMap idMap(WorldServer w) {
        return (IntHashMap) ReflectionHelper.getPrivateValue(WorldServer.class, w, "entityIdMap", "field_73066_T");
    }

    /** Findable by a search and by id, never ticked (not in the world's list) - as WorldTestCombatSC's targets. */
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

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("sc-wild-" + name).getBytes()), "[SCTestWild]");
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " wild: " + what);
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }

    static int floorDiv(int a, int b) {
        int q = a / b;
        return a % b != 0 && (a < 0) != (b < 0) ? q - 1 : q;
    }
}
