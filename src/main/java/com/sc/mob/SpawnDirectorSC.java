package com.sc.mob;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildTerrainSC;
import com.sc.hazard.EmpStormSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.5): the only thing that spawns hostiles in the Substrate (the chunk
 * provider's spawn lists are empty) - every 200 ticks per player there, inside its limits (rules ПС-1..ПС-6), and it
 * alone marks them «born in Si-14» (BornInSubstrateSC.mark). Registered by SCMod on FML's bus.
 * <p>
 * Each SpawnRulesSC.PERIOD ticks of a player in the Substrate (not in creative; not on Peaceful; doMobSpawning on;
 * the world's spawnHostileMobs on - a dedicated server's spawn-monsters, as vanilla's SpawnerAnimals asks it):
 * a spot 24..48 blocks away in a random direction - a loaded column, its top by the height map, a solid block to stand
 * on, two free blocks, no liquid, no player within 24; the kind by the column's biome (SpawnRulesSC weights); the
 * group (Linemen a pair, Marauders 2..4) cut down to what the limits allow - at most 8 natives within 64 blocks of the
 * player, about 40 in the whole dimension. The members stand round the first spot. Light doesn't matter (a «technical»
 * world: getCanSpawnHere isn't asked); other mods' LivingSpawnEvent.CheckSpawn is. Despawning is vanilla's (ПС-3).
 * Ф2 (ПП-11, package C): no spot within SettlementSC.SPAWN_MARGIN past a settlement's dome - by the registry, so a
 * dome whose core isn't loaded keeps its margin too.
 * <p>
 * The census of the natives (ISubstrateNativeSC and IMob) runs once a world tick over loadedEntityList and is kept as
 * their positions for that tick only - two players in one tick share it, no entity is held.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.9, rules ПС-7, ПС-8, ПС-20):
 * <ul>
 * <li>the census keeps each native's place, its tier and whether it is an elite (ICensusSC; who doesn't count - a swarm's
 * other particles, a mimic, a repair drone - is left out); the caps: 8 within 64 of the player, of them at most 2 of the
 * Quantum tier; at most 1 elite within 128 of the spot; 40 in the world (SpawnRulesSC);</li>
 * <li>the place of the spot: a Dead Node's zone (RuinLayoutSC.nodeNear), else the canyons or the plateaus by the biome;
 * never inside a lithograph's hall (RuinLayoutSC.inLitho - its mimics and drones come only with the generation). The weights
 * by place (SpawnRulesSC.weightsAt: no Executioner nearer than 1000 blocks to the world's spawn or with an elite near, no
 * Quantum kind when the Quantum cap is full);</li>
 * <li>the groups: Troopers 1..2, a Jammer (+ a Trooper, 50%, if the caps allow), a Shell patrol of 2..3 (one patrol id,
 * home - the Node's crossing; 30% with two repair drones bound to its first Shells, out of the census), the Executioner
 * alone, a nanite swarm of 6..10 particles (EntityNaniteSC.spawnSwarm - one census place), sentry drones 1..2 (each takes
 * its Dead Node's zone for its home on its first tick - EntitySentryDroneSC), all cut by both counts
 * (SpawnRulesSC.fitGroup);</li>
 * <li>the elites (ПС-20): each armoured member but the Executioner rolls EliteModSC.chancePct(its distance from the world's
 * spawn point) while the elite cap round the spot has room; a hit - EliteSC.apply(mob, EliteModSC.pick(...)) after
 * onSpawnWithEgg. Eggs and spawners never make elites (they never come here).</li>
 * </ul>
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §7.12, rules ПС-30, ПС-41, ПС-42):
 * <ul>
 * <li>the place of the spot - a Dead Node's zone, else the column's biome kind (SpawnRulesSC.placeOf(node, kind): the plateaus,
 * the canyons, the Ridges, the Oxide Fields, the Rift); never inside a Vault's bunker (VaultLayoutSC.inVault - its Sentinel
 * comes only with the generation, package C); the weights of the place, in an EMP storm the swarm x3 and the Arc Strider
 * everywhere but at a Node (EmpStormSC.active);</li>
 * <li>«Затмение» - its own roll at a Rift spot (SpawnRulesSC.eclipseChancePct: 2%, x3 in the Singular Emission) while its
 * record (RareRegistrySC.ECLIPSE) isn't ALIVE and 3 days went by since it died; it is the record's ALIVE one from its
 * first tick; a world tick watches the record - its place loaded and no such entity there LOST_CHECKS times: it counts as
 * dead (missed). The Warlord and the Sentinel never come from here;</li>
 * <li>the elite roll in the Rift - 12% (EliteModSC.chancePct(..., true), ПС-42);</li>
 * <li>the rare ones' records from their own ticks (rareSeen, rareDied - the clock: rareNow);</li>
 * <li>the peaceful branch (the Wafer Crab): its own try every SpawnRulesSC.PEACEFUL_PERIOD ticks a player, at most 4 within 64
 * blocks and 12 in the world (its own count beside the hostiles' census), on the plateaus and the Ridges, a group of 1..3,
 * «born in Si-14»; Peaceful difficulty doesn't stop it (World.spawnPeacefulMobs - a server's spawn-animals - does); a
 * peaceful creature never counts in the hostiles' census (add).</li>
 * </ul>
 */
public final class SpawnDirectorSC {

    public static final SpawnDirectorSC INSTANCE = new SpawnDirectorSC();

    /** A repair drone appears this high over its Shell's feet. */
    private static final double DRONE_Y = 2.2D;
    /** The census flags of an entry. */
    private static final int F_QUANTUM = 1, F_ELITE = 2;
    /**
     * МБ-3 (audit 2026-10-20): a standing structure's resident (a camp's or a Vault's dweller with its home, a Sentinel, a Warlord -
     * the rare ones' records) - counted near a player as ever, never in the dimension's cap.
     */
    private static final int F_RESIDENT = 4;

    /** Ticks since each player's last try (by UUID; a new one starts at a random point - the players don't all fire at once). */
    private static final Map<UUID, Integer> COUNTERS = new HashMap<UUID, Integer>();
    /** Ф4: the same for the peaceful branch (SpawnRulesSC.PEACEFUL_PERIOD). */
    private static final Map<UUID, Integer> PEACEFUL_COUNTERS = new HashMap<UUID, Integer>();
    /** Ф4: a rare one looks at its record this often (ticks, ПС-30); «Затмение» unseen this long is looked for by the world tick. */
    public static final int RARE_SEEN_TICKS = 100, ECLIPSE_WATCH_TICKS = 20, ECLIPSE_UNSEEN_TICKS = 140;
    /**
     * МБ-2 (audit 2026-10-20): an «Затмение» recorded ALIVE but not seen for this long (3 game days - stuck in a chunk nobody loads)
     * counts as dead, dated its last sighting: the Rift may make a new one. A stale copy loaded later leaves the world when the new
     * one lives (rareSeen), or lives again when there is none.
     */
    public static final long ECLIPSE_STALE_TICKS = 72000L;

    /** МБ-2, pure: an ALIVE record last seen at `seenAt` is stale at `now`. */
    public static boolean stale(long seenAt, long now) {
        return now - seenAt >= ECLIPSE_STALE_TICKS;
    }
    /** Ф4: the record's place must be loaded this far round (blocks) before a look without it counts as a miss. */
    private static final int ECLIPSE_WATCH_RADIUS = 48;
    /** Ф4: «Затмение» never comes lower than this in the Rift (its floor is the glass bottom of the void). */
    private static final int ECLIPSE_MIN_Y = 30;
    /** The census: the world and its tick it is of, the natives' positions and flags (x, y, z, flags). */
    private static int censusDim = Integer.MIN_VALUE;
    private static long censusTime = Long.MIN_VALUE;
    private static double[] census = new double[4 * 64];
    private static int censusN;
    /** МБ-3: of censusN the ones the dimension's cap counts (not F_RESIDENT); the players in the census's world (no FakePlayers). */
    private static int censusWorld, censusPlayers;
    /** Ф4: the peaceful natives of the same tick (x, y, z) - the Wafer Crabs, their own caps. */
    private static double[] peace = new double[3 * 16];
    private static int peaceN;
    /** World.spawnHostileMobs (protected), found once; null - not looked for yet. */
    private static Field hostileField;
    private static boolean hostileMissing;
    /** Ф4: World.spawnPeacefulMobs (protected) - a server's spawn-animals - the same way. */
    private static Field peacefulField;
    private static boolean peacefulMissing;

    private SpawnDirectorSC() {
    }

    /**
     * The world lets monsters spawn: World.spawnHostileMobs - MinecraftServer sets it from server.properties'
     * spawn-monsters (single player: not Peaceful). Another mapping without the field: yes (the old behaviour).
     */
    public static boolean hostilesAllowed(World w) {
        if (!hostileMissing) {
            try {
                if (hostileField == null) {
                    hostileField = ReflectionHelper.findField(World.class, "spawnHostileMobs", "field_72985_G");
                }
                return hostileField.getBoolean(w);
            } catch (Exception ex) {
                hostileMissing = true;
            }
        }
        return true;
    }

    /** Ф4: the world lets animals spawn - World.spawnPeacefulMobs (server.properties' spawn-animals); no such field: yes. */
    public static boolean peacefulAllowed(World w) {
        if (!peacefulMissing) {
            try {
                if (peacefulField == null) {
                    peacefulField = ReflectionHelper.findField(World.class, "spawnPeacefulMobs", "field_72992_H");
                }
                return peacefulField.getBoolean(w);
            } catch (Exception ex) {
                peacefulMissing = true;
            }
        }
        return true;
    }

    /** Server stopped: the counters and the census; the mobs' damage logs and trophy locks too (MobEventsSC). */
    public static void clearAll() {
        COUNTERS.clear();
        PEACEFUL_COUNTERS.clear();
        censusDim = Integer.MIN_VALUE;
        censusTime = Long.MIN_VALUE;
        censusN = 0;
        peaceN = 0;
        MobEventsSC.clearAll();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.player instanceof EntityPlayerMP) || e.player instanceof FakePlayer) {
            return;
        }
        EntityPlayer p = e.player;
        if (p.worldObj.isRemote || !SubstrateSC.is(p.worldObj)) {
            return;
        }
        UUID id = p.getUniqueID();
        if (due(PEACEFUL_COUNTERS, id, SpawnRulesSC.PEACEFUL_PERIOD, p.worldObj.rand) && p.isEntityAlive()) {
            tryPeaceful((WorldServer) p.worldObj, p);              // Ф4: the Wafer Crabs - creative or not, Peaceful or not
        }
        if (!due(COUNTERS, id, SpawnRulesSC.PERIOD, p.worldObj.rand)) {
            return;
        }
        if (!p.capabilities.isCreativeMode && p.isEntityAlive()) {
            tryGroup((WorldServer) p.worldObj, p);
        }
    }

    /** One tick of player `id`'s counter in `counters`: true once every `period` ticks (a new player starts at a random point). */
    private static boolean due(Map<UUID, Integer> counters, UUID id, int period, Random rand) {
        Integer was = counters.get(id);
        int n = (was == null ? rand.nextInt(period) : was.intValue()) + 1;
        if (n < period) {
            counters.put(id, Integer.valueOf(n));
            return false;
        }
        counters.put(id, Integer.valueOf(0));
        return true;
    }

    /** Ф4 (ПС-30): the record of «Затмение» watched once a second in the Substrate (FML bus, the world tick). */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)
                || e.world.getTotalWorldTime() % ECLIPSE_WATCH_TICKS != 0) {
            return;
        }
        eclipseWatch((WorldServer) e.world);
    }

    /** One try for one player (server). @return how many mobs appeared (a swarm's particles and a patrol's drones too) */
    public static int tryGroup(WorldServer w, EntityPlayer p) {
        boolean peaceful = w.difficultySetting == EnumDifficulty.PEACEFUL;
        if (peaceful || !w.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !hostilesAllowed(w)) {
            return 0;
        }
        takeCensus(w);
        int near = near(p.posX, p.posY, p.posZ, SpawnRulesSC.NEAR_RADIUS, 0);
        if (!SpawnRulesSC.maySpawn(near, censusWorld, 1, false, worldCap())) {   // МБ-3: the residents aside, by the players
            return 0;
        }
        int nearQuantum = near(p.posX, p.posY, p.posZ, SpawnRulesSC.NEAR_RADIUS, F_QUANTUM);
        Random rand = w.rand;
        int[] spot = null;
        for (int i = 0; i < SpawnRulesSC.SPOT_TRIES && spot == null; i++) {
            double ang = rand.nextDouble() * Math.PI * 2D;
            double dist = SpawnRulesSC.MIN_DIST + rand.nextDouble() * (SpawnRulesSC.MAX_DIST - SpawnRulesSC.MIN_DIST);
            spot = spotAt(w, MathHelper.floor_double(p.posX + Math.cos(ang) * dist), MathHelper.floor_double(p.posZ + Math.sin(ang) * dist));
        }
        if (spot == null) {
            return 0;
        }
        int[] node = RuinLayoutSC.nodeNear(spot[0], spot[2]);
        int place = SpawnRulesSC.placeOf(node != null, kindOf(w, spot[0], spot[2]));       // Ф4: the Ridges, the Oxide Fields, the Rift
        int elitesNear = near(spot[0] + 0.5D, spot[1], spot[2] + 0.5D, SpawnRulesSC.ELITE_RADIUS, F_ELITE);
        double fromSpawn = fromSpawn(w, spot[0], spot[2]);
        if (place == SpawnRulesSC.PLACE_RIFT) {
            int made = eclipse(w, spot, near, nearQuantum, elitesNear, rand);                // Ф4, ПС-34: its own roll first
            if (made > 0) {
                return made;
            }
        }
        int[] weights = SpawnRulesSC.weightsAt(place, fromSpawn, nearQuantum < SpawnRulesSC.QUANTUM_NEAR_MAX,
                SpawnRulesSC.eliteAllowed(elitesNear), EmpStormSC.active(w));
        int total = SpawnRulesSC.sum(weights);
        int kind = total <= 0 ? -1 : SpawnRulesSC.pick(weights, rand.nextInt(total));
        if (kind < 0 || kind >= SpawnRulesSC.GROUP_MIN.length || SpawnRulesSC.GROUP_MAX[kind] <= 0) {
            return 0;
        }
        int want = SpawnRulesSC.GROUP_MIN[kind] + rand.nextInt(SpawnRulesSC.GROUP_MAX[kind] - SpawnRulesSC.GROUP_MIN[kind] + 1);
        if (kind == MobEntitiesSC.ID_NANITE) {
            return swarm(w, spot, near, want, rand);
        }
        boolean quantum = SpawnRulesSC.quantumKind(kind);
        int places = kind == MobEntitiesSC.ID_JAMMER && rand.nextFloat() < SpawnRulesSC.JAMMER_ESCORT_CHANCE ? want + 1 : want;
        int group = SpawnRulesSC.fitGroup(near, nearQuantum, censusWorld, places, 1, quantum, false, worldCap());
        Group g = new Group(w, kind, spot, node, fromSpawn, elitesNear, place == SpawnRulesSC.PLACE_RIFT);
        for (int m = 0; m < group; m++) {
            int[] at = m == 0 ? spot : null;
            for (int t = 0; at == null && t < SpawnRulesSC.MEMBER_TRIES; t++) {
                int s = SpawnRulesSC.GROUP_SPREAD;
                at = spotAt(w, spot[0] + rand.nextInt(2 * s + 1) - s, spot[2] + rand.nextInt(2 * s + 1) - s);
            }
            int k = kind == MobEntitiesSC.ID_JAMMER && m >= want ? MobEntitiesSC.ID_TROOPER : kind;    // the Jammer's escort
            if (at != null) {
                spawn(g, k, at);
            }
        }
        if (kind == MobEntitiesSC.ID_HOLLOW_SHELL && !g.shells.isEmpty() && rand.nextFloat() < SpawnRulesSC.DRONE_PATROL_CHANCE) {
            for (int d = 0; d < SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_REPAIR_DRONE]; d++) {
                drone(g, g.shells.get(d % g.shells.size()));
            }
        }
        return g.made;
    }

    /** One group being made: its world, kind, first spot, Dead Node crossing, distance from the spawn; what it made so far. */
    private static final class Group {
        final WorldServer w;
        final int kind;
        final int[] spot, node;
        final double fromSpawn;
        final long patrol;
        /** Ф4, ПС-42: the spot is in the Rift (the elite roll's 12%). */
        final boolean rift;
        int elites, made;
        final List<EntityHollowShellSC> shells = new ArrayList<EntityHollowShellSC>();

        Group(WorldServer w, int kind, int[] spot, int[] node, double fromSpawn, int elitesNear, boolean rift) {
            this.w = w;
            this.kind = kind;
            this.spot = spot;
            this.node = node;
            this.fromSpawn = fromSpawn;
            this.elites = elitesNear;
            this.rift = rift;
            this.patrol = w.rand.nextLong() | 1L;                        // never 0 (0 - no patrol)
        }
    }

    /**
     * A place to stand on at column (x, z): its top by the height map, solid under it, two free blocks, no player within
     * MIN_DIST, not within SPAWN_MARGIN past a settlement's dome, not in a lithograph's hall (Ф3), not in a Vault's bunker,
     * not on the Rift's void floor or its pillar - islands and the rim only (Ф4); null none. Public for the world test (ПП-11).
     */
    public static int[] spotAt(World w, int x, int z) {
        if (!w.blockExists(x, 64, z)) {
            return null;                                     // never loads a chunk
        }
        SettlementRegistrySC settlements = SettlementSC.registry(w);
        if (settlements != null && settlements.within(w.provider.dimensionId, x + 0.5D, z + 0.5D, SettlementSC.SPAWN_MARGIN) != null) {
            return null;                                     // ПП-11: no closer than SPAWN_MARGIN past a dome's edge (the core may be unloaded)
        }
        int y = w.getHeightValue(x, z);
        if (y <= 1 || y >= w.getHeight() - 2) {
            return null;
        }
        if (kindOf(w, x, z) == BiomeSubstrateSC.RIFT && (SubstrateWildTerrainSC.pillar(x, z)
                || y <= SubstrateWildTerrainSC.RIFT_FLOOR + 1 && SubstrateWildTerrainSC.riftVoid(w.getSeed(), x, z))) {
            return null;                                     // Ф4: the Rift's glass floor counts in the height map - nobody on it or on the pillar
        }
        Block under = w.getBlock(x, y - 1, z);
        if (!under.getMaterial().isSolid() || under.getMaterial().isLiquid() || !w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z)) {
            return null;
        }
        if (w.getClosestPlayer(x + 0.5D, y, z + 0.5D, SpawnRulesSC.MIN_DIST) != null) {
            return null;
        }
        if (RuinLayoutSC.inLitho(w.getSeed(), x, y, z) || RuinLayoutSC.inLitho(w.getSeed(), x, y + 1, z)) {
            return null;                                     // ПС-8: a lithograph's hall has only its own residents
        }
        if (VaultLayoutSC.inVault(w.getSeed(), x, y, z) || VaultLayoutSC.inVault(w.getSeed(), x, y + 1, z)) {
            return null;                                     // Ф4, ПС-41: a Vault has only its Sentinel
        }
        if (builtArenaNear(w, x, z)) {
            return null;                                     // Ф5, ПС-57: a Breakdown Epicentre has only the Warden's waves
        }
        return new int[]{x, y, z};
    }

    /**
     * Ф5, ПС-57: the column lies in a built Breakdown Epicentre's box (+ 8) - its record READY / FIGHT / KEYED. An arena crossing
     * whose record is EMPTY (a world of 0.2.x: explored ground, or its Dead Node made already) keeps its Node's zone and spawns;
     * no record yet - its start not made, nothing of it is loaded.
     */
    private static boolean builtArenaNear(World w, int x, int z) {
        int[] c = com.sc.arena.ArenaLayoutSC.arenaNear(w.getSeed(), x, z, 8);
        if (c == null) {
            return false;
        }
        com.sc.arena.ArenaDataSC d = com.sc.arena.ArenaDataSC.get(w);
        com.sc.arena.ArenaDataSC.Arena a = d == null ? null : d.arenaAt(c[0], c[1]);
        return a != null && a.state != com.sc.arena.ArenaDataSC.EMPTY;
    }

    /** The column's biome kind: the world's own biome, else the cells' (Ф4: SubstrateCellsSC - five kinds). */
    private static int kindOf(World w, int x, int z) {
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        return b instanceof BiomeSubstrateSC ? ((BiomeSubstrateSC) b).kind : SubstrateCellsSC.biomeKind(w, x, z);
    }

    /** Blocks (horizontal) from the world's spawn point. */
    private static double fromSpawn(World w, int x, int z) {
        ChunkCoordinates s = w.getSpawnPoint();
        double dx = x + 0.5D - s.posX, dz = z + 0.5D - s.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A new entity of pool id `kind`, null for one the director doesn't make. */
    private static EntityLiving create(World w, int kind) {
        switch (kind) {
            case MobEntitiesSC.ID_LINEMAN:
                return new EntityLinemanSC(w);
            case MobEntitiesSC.ID_MARAUDER:
                return new EntityMarauderSC(w);
            case MobEntitiesSC.ID_TROOPER:
                return new EntityTrooperSC(w);
            case MobEntitiesSC.ID_JAMMER:
                return new EntityJammerSC(w);
            case MobEntitiesSC.ID_HOLLOW_SHELL:
                return new EntityHollowShellSC(w);
            case MobEntitiesSC.ID_REPAIR_DRONE:
                return new EntityRepairDroneSC(w);
            case MobEntitiesSC.ID_EXECUTIONER:
                return new EntityExecutionerSC(w);
            case MobEntitiesSC.ID_SENTRY_DRONE:
                return new EntitySentryDroneSC(w);
            case MobEntitiesSC.ID_OVERHEATED:                // Ф4 (the coordinator's: every pool id the world makes)
                return new EntityOverheatedSC(w);
            case MobEntitiesSC.ID_WARLORD:
                return new EntityWarlordSC(w);
            case MobEntitiesSC.ID_SENTINEL:
                return new EntitySentinelSC(w);
            case MobEntitiesSC.ID_ECLIPSE:
                return new EntityEclipseSC(w);
            case MobEntitiesSC.ID_PHANTOM:
                return new EntityPhantomSC(w);
            case MobEntitiesSC.ID_LEECH:
                return new EntityLeechSC(w);
            case MobEntitiesSC.ID_COLOSSUS:
                return new EntityColossusSC(w);
            case MobEntitiesSC.ID_STRIDER:
                return new EntityStriderSC(w);
            case MobEntitiesSC.ID_SCRAPPER:
                return new EntityScrapperSC(w);
            case MobEntitiesSC.ID_WAFER_CRAB:
                return new EntityWaferCrabSC(w);
            default:
                return null;
        }
    }

    /** A member of `g` of pool id `kind` standing at `at` (a flyer: a little over it). @return it appeared */
    private static boolean spawn(Group g, int kind, int[] at) {
        WorldServer w = g.w;
        EntityLiving e = create(w, kind);
        if (e == null) {
            return false;
        }
        boolean flyer = kind == MobEntitiesSC.ID_SENTRY_DRONE;
        float x = at[0] + 0.5F, y = at[1] + (flyer ? 1.5F : 0F), z = at[2] + 0.5F;
        e.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360F, 0F);
        if (!place(w, e, x, y, z)) {
            return false;
        }
        if (e instanceof EntityHollowShellSC && g.node != null) {
            int hx = g.node[0] * SubstrateTerrainSC.GRID, hz = g.node[1] * SubstrateTerrainSC.GRID;
            ((EntityHollowShellSC) e).setPatrol(g.patrol, hx, homeY(w, hx, hz, g.node, at[1]), hz);
        }
        if (e instanceof EntityArmoredMobSC && SpawnRulesSC.mayRollElite(kind)) {
            elite(g, (EntityArmoredMobSC) e);
        }
        if (!join(w, e)) {
            return false;
        }
        if (e instanceof EntityHollowShellSC) {
            g.shells.add((EntityHollowShellSC) e);
        }
        g.made++;
        return true;
    }

    /**
     * Ф4 (the coordinator's, docs/plan-substrate-tech-f4.md §7.12): one creature of pool id `kind` at (x, y, z) outside the
     * director's own tries and caps - a raid's wave (RaidSC), the bridge's echo squad (BridgeEchoSC), a camp's or a Vault's
     * resident and its return (package C), the Scrapper's errand (ScrapperSC). The same checks as a group member's (room, no
     * liquid, other mods' CheckSpawn), onSpawnWithEgg, an elite roll for an armoured one when `rollElite` (EliteModSC.chancePct by
     * its distance from the world's spawn; `rift` - the Rift's 12%), «born in Si-14», into the world and this tick's census.
     * Server. @return the creature, or null (no room, refused, an unknown id)
     */
    public static EntityLiving spawnAt(WorldServer w, int kind, double x, double y, double z, boolean rollElite, boolean rift) {
        EntityLiving e = create(w, kind);
        if (e == null) {
            return null;
        }
        e.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360F, 0F);
        if (!place(w, e, (float) x, (float) y, (float) z)) {
            return null;
        }
        if (rollElite && e instanceof EntityArmoredMobSC && SpawnRulesSC.mayRollElite(kind) && !((EntityArmoredMobSC) e).executeImmune()
                && w.rand.nextInt(100) < EliteModSC.chancePct(fromSpawn(w, (int) Math.floor(x), (int) Math.floor(z)), rift)) {
            MobArmorSC a = ((EntityArmoredMobSC) e).mobArmor();
            EliteSC.apply((EntityArmoredMobSC) e, EliteModSC.pick(w.rand, a.screen, a.heliumOn()));
        }
        return join(w, e) ? e : null;
    }

    // ------------------------------------------------------------------ Ф4: «Затмение», the rare ones, the peaceful branch

    /**
     * Ф4, ПС-34: «Затмение»'s own roll at a Rift spot - SpawnRulesSC.eclipseChancePct by its record, the caps (one more of the
     * Quantum tier near the player, an elite place round the spot), not on the void's floor. @return 1 - it came, 0 - not now
     */
    private static int eclipse(WorldServer w, int[] spot, int near, int nearQuantum, int elitesNear, Random rand) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null || spot[1] < ECLIPSE_MIN_Y || !SpawnRulesSC.maySpawn(near, nearQuantum, censusWorld, 1, true, false, worldCap())) {
            return 0;
        }
        RareRegistrySC.Rec r = reg.recIfAny(RareRegistrySC.ECLIPSE);
        long now = rareNow(w);
        int pct = SpawnRulesSC.eclipseChancePct(r == null ? RareRegistrySC.NONE : r.state, r == null ? 0L : r.diedAt, now,
                EmpStormSC.emission(w), SpawnRulesSC.eliteAllowed(elitesNear));
        if (pct <= 0 || rand.nextInt(100) >= pct) {
            return 0;
        }
        EntityLiving e = create(w, MobEntitiesSC.ID_ECLIPSE);
        float x = spot[0] + 0.5F, y = spot[1], z = spot[2] + 0.5F;
        e.setLocationAndAngles(x, y, z, rand.nextFloat() * 360F, 0F);
        if (!place(w, e, x, y, z) || !join(w, e)) {
            return 0;
        }
        reg.alive(RareRegistrySC.ECLIPSE, e, now);                 // the one in the dimension, from now on
        return 1;
    }

    /**
     * Ф4, ПС-30: the record of «Затмение» ALIVE, yet not seen for ECLIPSE_UNSEEN_TICKS - its place loaded ECLIPSE_WATCH_RADIUS
     * round and no living entity of its UUID in the world: one more miss (RareRegistrySC.missed: LOST_CHECKS of them - dead now,
     * its 3 days begin). An unloaded place is never a miss (it sleeps there with its chunk).
     */
    private static void eclipseWatch(WorldServer w) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(RareRegistrySC.ECLIPSE);
        if (r == null || r.state != RareRegistrySC.ALIVE) {
            return;
        }
        long now = rareNow(w);
        if (stale(r.seenAt, now)) {
            reg.died(RareRegistrySC.ECLIPSE, r.seenAt);          // МБ-2: unseen 3 days - dead since its last sighting
            return;
        }
        if (now - r.seenAt < ECLIPSE_UNSEEN_TICKS || r.dim != w.provider.dimensionId) {
            return;
        }
        int d = ECLIPSE_WATCH_RADIUS;
        if (!w.checkChunksExist(r.x - d, 0, r.z - d, r.x + d, 0, r.z + d)) {
            return;
        }
        UUID id = r.uuid();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityEclipseSC && !((Entity) o).isDead && id.equals(((Entity) o).getUniqueID())) {
                reg.seen(RareRegistrySC.ECLIPSE, (Entity) o, now);
                return;
            }
        }
        reg.missed(RareRegistrySC.ECLIPSE, now);
    }

    /**
     * Ф4, ПС-30: the clock of the rare ones' records (RareRegistrySC: alive, seen, died, the respawn's cooldowns) - the world's
     * total time: it runs on with doDaylightCycle off, never goes back with /time set, and the Substrate shares the Overworld's.
     * Package C's camps and Vaults read the same clock.
     */
    public static long rareNow(World w) {
        return w == null ? 0L : w.getTotalWorldTime();
    }

    /**
     * Ф4, ПС-30, server - a rare creature's own slow tick (every RARE_SEEN_TICKS): its record seen now. An ALIVE record of another
     * UUID - this is a stale copy (an old chunk, a restored backup): it leaves the world, nothing dropped; so does it under a DEAD
     * record of another UUID (★, МБ-2: a newer one came and died since - an Eclipse left unloaded for 3 days, then the one after
     * it killed, would else be killed twice); a record DEAD with its own UUID (it was counted lost meanwhile) or none - it is the
     * record's live one again. A creature without a key (an egg's) - nothing.
     * @return it stays in the world
     */
    public static boolean rareSeen(EntityLiving e) {
        String key = RareRegistrySC.keyOf(e);
        RareRegistrySC reg = key == null || e.worldObj == null || e.worldObj.isRemote ? null : RareRegistrySC.get(e.worldObj);
        if (reg == null) {
            return true;
        }
        long now = rareNow(e.worldObj);
        if (reg.seen(key, e, now)) {
            return true;
        }
        RareRegistrySC.Rec r = reg.recIfAny(key);
        if (!mayRevive(r == null ? RareRegistrySC.NONE : r.state, r == null || r.uuid().equals(e.getUniqueID()))) {
            e.setDead();                                           // the other one is the live one, or came after it: never two
            return false;
        }
        reg.alive(key, e, now);
        return true;
    }

    /**
     * ★ (МБ-2): pure - may a loaded rare creature not matching its ALIVE record become the record's live one: the record's `state`,
     * `ownId` - the record has its UUID (or there is no record). ALIVE (another one) - no; DEAD with another UUID - no (a newer
     * one came and died); DEAD with its own (counted lost), NONE - yes.
     */
    public static boolean mayRevive(int state, boolean ownId) {
        return state == RareRegistrySC.NONE || state == RareRegistrySC.DEAD && ownId;
    }

    /** Ф4, ПС-30, server - from a rare creature's onDeath (vanilla's `dead` set: the death went through): its record DEAD now. */
    public static void rareDied(EntityLiving e) {
        String key = RareRegistrySC.keyOf(e);
        RareRegistrySC reg = key == null || e.worldObj == null || e.worldObj.isRemote ? null : RareRegistrySC.get(e.worldObj);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(key);
        if (r != null && r.state == RareRegistrySC.ALIVE && r.uuid().equals(e.getUniqueID())) {
            reg.died(key, rareNow(e.worldObj));
        }
    }

    /**
     * Ф4, ПС-41: one peaceful try for one player (server) - the Wafer Crabs: doMobSpawning and spawn-animals on, at most
     * SpawnRulesSC.PEACEFUL_NEAR_MAX within NEAR_RADIUS and PEACEFUL_WORLD_MAX in the world, a spot 24..48 blocks off on the
     * plateaus or the Ridges (not a Node's zone), a group of 1..3 round it, «born in Si-14». Public for the world test.
     * @return how many came
     */
    public static int tryPeaceful(WorldServer w, EntityPlayer p) {
        if (!w.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !peacefulAllowed(w)) {
            return 0;
        }
        takeCensus(w);
        int near = nearPeace(p.posX, p.posY, p.posZ, SpawnRulesSC.NEAR_RADIUS);
        if (!SpawnRulesSC.peacefulMaySpawn(near, peaceN, 1)) {
            return 0;
        }
        Random rand = w.rand;
        double ang = rand.nextDouble() * Math.PI * 2D;
        double dist = SpawnRulesSC.MIN_DIST + rand.nextDouble() * (SpawnRulesSC.MAX_DIST - SpawnRulesSC.MIN_DIST);
        int[] spot = spotAt(w, MathHelper.floor_double(p.posX + Math.cos(ang) * dist), MathHelper.floor_double(p.posZ + Math.sin(ang) * dist));
        if (spot == null || !SpawnRulesSC.peacefulPlace(SpawnRulesSC.placeOf(RuinLayoutSC.nodeNear(spot[0], spot[2]) != null,
                kindOf(w, spot[0], spot[2])))) {
            return 0;
        }
        int kind = MobEntitiesSC.ID_WAFER_CRAB;
        int want = SpawnRulesSC.GROUP_MIN[kind] + rand.nextInt(SpawnRulesSC.GROUP_MAX[kind] - SpawnRulesSC.GROUP_MIN[kind] + 1);
        int group = SpawnRulesSC.fitPeaceful(near, peaceN, want);
        int made = 0;
        for (int m = 0; m < group; m++) {
            int[] at = m == 0 ? spot : null;
            for (int t = 0; at == null && t < SpawnRulesSC.MEMBER_TRIES; t++) {
                int s = SpawnRulesSC.GROUP_SPREAD;
                at = spotAt(w, spot[0] + rand.nextInt(2 * s + 1) - s, spot[2] + rand.nextInt(2 * s + 1) - s);
            }
            if (at == null) {
                continue;
            }
            EntityLiving e = create(w, kind);
            float x = at[0] + 0.5F, y = at[1], z = at[2] + 0.5F;
            e.setLocationAndAngles(x, y, z, rand.nextFloat() * 360F, 0F);
            if (place(w, e, x, y, z) && join(w, e)) {
                made++;
            }
        }
        return made;
    }

    /** Two repair drones of a patrol: over its Shells' shoulders, bound to them, out of the census. */
    private static boolean drone(Group g, EntityHollowShellSC shell) {
        WorldServer w = g.w;
        EntityRepairDroneSC d = new EntityRepairDroneSC(w);
        float x = (float) shell.posX + (w.rand.nextFloat() - 0.5F), y = (float) (shell.posY + DRONE_Y), z = (float) shell.posZ + (w.rand.nextFloat() - 0.5F);
        d.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360F, 0F);
        if (!place(w, d, x, y, z)) {
            return false;
        }
        d.bind(shell);
        if (!join(w, d)) {
            return false;
        }
        g.made++;
        return true;
    }

    /** The checks and the dressing of a new mob at (x, y, z): room, no liquid, other mods' CheckSpawn; onSpawnWithEgg. */
    private static boolean place(WorldServer w, EntityLiving e, float x, float y, float z) {
        if (!w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() || w.isAnyLiquid(e.boundingBox)
                || ForgeEventFactory.canEntitySpawn(e, w, x, y, z) == Event.Result.DENY) {
            return false;
        }
        if (!ForgeEventFactory.doSpecialSpawn(e, w, x, y, z)) {
            e.onSpawnWithEgg((IEntityLivingData) null);
        }
        return true;
    }

    /** «Born in Si-14», into the world, into this tick's census. */
    private static boolean join(WorldServer w, EntityLiving e) {
        BornInSubstrateSC.mark(e);
        if (!w.spawnEntityInWorld(e)) {
            return false;
        }
        add(e);
        return true;
    }

    /** ПС-20: the elite roll of one armoured member (the cap round the spot counted with this group's own elites). */
    private static void elite(Group g, EntityArmoredMobSC mob) {
        if (!SpawnRulesSC.eliteAllowed(g.elites) || mob.executeImmune()) {
            return;
        }
        if (g.w.rand.nextInt(100) >= EliteModSC.chancePct(g.fromSpawn, g.rift)) {      // Ф4, ПС-42: the Rift's 12%
            return;
        }
        MobArmorSC a = mob.mobArmor();
        EliteSC.apply(mob, EliteModSC.pick(g.w.rand, a.screen, a.heliumOn()));
        if (mob.executeImmune()) {
            g.elites++;
        }
    }

    /** The ground of a Node's crossing for a home: the column's top if loaded and near the spot's, else the plaza's floor. */
    private static int homeY(World w, int x, int z, int[] node, int spotY) {
        if (w.blockExists(x, 64, z)) {
            int top = w.getHeightValue(x, z);
            if (Math.abs(top - spotY) <= 8) {
                return top;
            }
        }
        return SubstrateTerrainSC.nodeFloorY(w.getSeed(), node[0], node[1]) + 1;
    }

    /** A nanite swarm at the spot (package X's factory): one census place for all its particles. @return particles made */
    private static int swarm(WorldServer w, int[] spot, int near, int count, Random rand) {
        if (!SpawnRulesSC.maySpawn(near, censusWorld, 1, false, worldCap())) {
            return 0;
        }
        int made = EntityNaniteSC.spawnSwarm(w, spot[0] + 0.5D, spot[1] + 1.0D, spot[2] + 0.5D, count, rand);
        if (made > 0) {
            addPoint(spot[0] + 0.5D, spot[1] + 1.0D, spot[2] + 0.5D, 0);
        }
        return Math.max(0, made);
    }

    // ------------------------------------------------------------------ the census (once a world tick)

    private static void takeCensus(World w) {
        long now = w.getTotalWorldTime();
        if (censusDim == w.provider.dimensionId && censusTime == now) {
            return;
        }
        censusDim = w.provider.dimensionId;
        censusTime = now;
        censusN = 0;
        censusWorld = 0;
        peaceN = 0;
        censusPlayers = 0;
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayer && !(o instanceof net.minecraftforge.common.util.FakePlayer)) {
                censusPlayers++;
            }
        }
        for (Object o : w.loadedEntityList) {
            Entity e = (Entity) o;
            if (!e.isDead && (MobCombatSC.hostileNative(e) || e instanceof EntityWaferCrabSC)) {
                add(e);
            }
        }
    }

    private static void addPeace(double x, double y, double z) {
        if (3 * peaceN + 3 > peace.length) {
            double[] more = new double[peace.length * 2];
            System.arraycopy(peace, 0, more, 0, peace.length);
            peace = more;
        }
        peace[3 * peaceN] = x;
        peace[3 * peaceN + 1] = y;
        peace[3 * peaceN + 2] = z;
        peaceN++;
    }

    /** Ф4: the peaceful natives in the box `r` round a point (this tick's census). */
    private static int nearPeace(double x, double y, double z, int r) {
        int n = 0;
        for (int i = 0; i < peaceN; i++) {
            if (Math.abs(peace[3 * i] - x) <= r && Math.abs(peace[3 * i + 1] - y) <= r && Math.abs(peace[3 * i + 2] - z) <= r) {
                n++;
            }
        }
        return n;
    }

    /**
     * One native into this tick's census by its ICensusSC answer (not one that doesn't count). Ф4: a peaceful native (the Wafer
     * Crab) goes to its own count, never the hostiles' (spawnAt and the peaceful branch come here too).
     */
    private static void add(Entity e) {
        if (!MobCombatSC.hostileNative(e)) {
            if (e instanceof EntityWaferCrabSC) {
                addPeace(e.posX, e.posY, e.posZ);
            }
            return;
        }
        int flags = 0;
        if (e instanceof ICensusSC) {
            ICensusSC c = (ICensusSC) e;
            if (!c.censusCounts()) {
                return;
            }
            flags = (c.censusTier() >= ICensusSC.TIER_QUANTUM ? F_QUANTUM : 0) | (c.censusElite() ? F_ELITE : 0);
        }
        if (e.getEntityData().hasKey(MobCombatSC.HOME_NBT) || RareRegistrySC.keyOf(e) != null) {
            flags |= F_RESIDENT;                                // МБ-3: a structure's resident - not in the dimension's cap
        }
        addPoint(e.posX, e.posY, e.posZ, flags);
    }

    /** МБ-3: this census's dimension cap - SpawnRulesSC.worldMax by its players. */
    private static int worldCap() {
        return SpawnRulesSC.worldMax(censusPlayers);
    }

    private static void addPoint(double x, double y, double z, int flags) {
        if (4 * censusN + 4 > census.length) {
            double[] more = new double[census.length * 2];
            System.arraycopy(census, 0, more, 0, census.length);
            census = more;
        }
        census[4 * censusN] = x;
        census[4 * censusN + 1] = y;
        census[4 * censusN + 2] = z;
        census[4 * censusN + 3] = flags;
        censusN++;
        if ((flags & F_RESIDENT) == 0) {
            censusWorld++;
        }
    }

    /** Natives in the box `r` round a point (this tick's census) having all of `flags` (0 - every one). */
    private static int near(double x, double y, double z, int r, int flags) {
        int n = 0;
        for (int i = 0; i < censusN; i++) {
            if (Math.abs(census[4 * i] - x) <= r && Math.abs(census[4 * i + 1] - y) <= r && Math.abs(census[4 * i + 2] - z) <= r
                    && ((int) census[4 * i + 3] & flags) == flags) {
                n++;
            }
        }
        return n;
    }

    /** The census of the world's current tick round a point (the world test): {natives, of the Quantum tier, elites} within `r`. */
    public static int[] censusNear(World w, double x, double y, double z, int r) {
        takeCensus(w);
        return new int[]{near(x, y, z, r, 0), near(x, y, z, r, F_QUANTUM), near(x, y, z, r, F_ELITE)};
    }
}
