package com.sc.mob;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.ruin.RuinLayoutSC;
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
 */
public final class SpawnDirectorSC {

    public static final SpawnDirectorSC INSTANCE = new SpawnDirectorSC();

    /** A repair drone appears this high over its Shell's feet. */
    private static final double DRONE_Y = 2.2D;
    /** The census flags of an entry. */
    private static final int F_QUANTUM = 1, F_ELITE = 2;

    /** Ticks since each player's last try (by UUID; a new one starts at a random point - the players don't all fire at once). */
    private static final Map<UUID, Integer> COUNTERS = new HashMap<UUID, Integer>();
    /** The census: the world and its tick it is of, the natives' positions and flags (x, y, z, flags). */
    private static int censusDim = Integer.MIN_VALUE;
    private static long censusTime = Long.MIN_VALUE;
    private static double[] census = new double[4 * 64];
    private static int censusN;
    /** World.spawnHostileMobs (protected), found once; null - not looked for yet. */
    private static Field hostileField;
    private static boolean hostileMissing;

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

    /** Server stopped: the counters and the census; the mobs' damage logs and trophy locks too (MobEventsSC). */
    public static void clearAll() {
        COUNTERS.clear();
        censusDim = Integer.MIN_VALUE;
        censusTime = Long.MIN_VALUE;
        censusN = 0;
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
        Integer was = COUNTERS.get(id);
        int n = (was == null ? p.worldObj.rand.nextInt(SpawnRulesSC.PERIOD) : was.intValue()) + 1;
        if (n < SpawnRulesSC.PERIOD) {
            COUNTERS.put(id, Integer.valueOf(n));
            return;
        }
        COUNTERS.put(id, Integer.valueOf(0));
        if (!p.capabilities.isCreativeMode && p.isEntityAlive()) {
            tryGroup((WorldServer) p.worldObj, p);
        }
    }

    /** One try for one player (server). @return how many mobs appeared (a swarm's particles and a patrol's drones too) */
    public static int tryGroup(WorldServer w, EntityPlayer p) {
        boolean peaceful = w.difficultySetting == EnumDifficulty.PEACEFUL;
        if (peaceful || !w.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !hostilesAllowed(w)) {
            return 0;
        }
        takeCensus(w);
        int near = near(p.posX, p.posY, p.posZ, SpawnRulesSC.NEAR_RADIUS, 0);
        if (!SpawnRulesSC.maySpawn(near, censusN, 1, false)) {
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
        int place = SpawnRulesSC.placeOf(node != null, kindOf(w, spot[0], spot[2]) == BiomeSubstrateSC.CANYONS);
        int elitesNear = near(spot[0] + 0.5D, spot[1], spot[2] + 0.5D, SpawnRulesSC.ELITE_RADIUS, F_ELITE);
        double fromSpawn = fromSpawn(w, spot[0], spot[2]);
        int[] weights = SpawnRulesSC.weightsAt(place, fromSpawn, nearQuantum < SpawnRulesSC.QUANTUM_NEAR_MAX,
                SpawnRulesSC.eliteAllowed(elitesNear));
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
        int group = SpawnRulesSC.fitGroup(near, nearQuantum, censusN, places, 1, quantum, false);
        Group g = new Group(w, kind, spot, node, fromSpawn, elitesNear);
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
        int elites, made;
        final List<EntityHollowShellSC> shells = new ArrayList<EntityHollowShellSC>();

        Group(WorldServer w, int kind, int[] spot, int[] node, double fromSpawn, int elitesNear) {
            this.w = w;
            this.kind = kind;
            this.spot = spot;
            this.node = node;
            this.fromSpawn = fromSpawn;
            this.elites = elitesNear;
            this.patrol = w.rand.nextLong() | 1L;                        // never 0 (0 - no patrol)
        }
    }

    /**
     * A place to stand on at column (x, z): its top by the height map, solid under it, two free blocks, no player within
     * MIN_DIST, not within SPAWN_MARGIN past a settlement's dome, not in a lithograph's hall (Ф3); null none. Public for the
     * world test (ПП-11).
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
        return new int[]{x, y, z};
    }

    /** The column's biome kind: the world's own biome, else the terrain's maths. */
    private static int kindOf(World w, int x, int z) {
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        return b instanceof BiomeSubstrateSC ? ((BiomeSubstrateSC) b).kind : SubstrateTerrainSC.biomeKind(x, z);
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
        if (g.w.rand.nextInt(100) >= EliteModSC.chancePct(g.fromSpawn, false)) {
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
        if (!SpawnRulesSC.maySpawn(near, censusN, 1, false)) {
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
        for (Object o : w.loadedEntityList) {
            if (MobCombatSC.hostileNative((Entity) o) && !((Entity) o).isDead) {
                add((Entity) o);
            }
        }
    }

    /** One native into this tick's census by its ICensusSC answer (not one that doesn't count). */
    private static void add(Entity e) {
        int flags = 0;
        if (e instanceof ICensusSC) {
            ICensusSC c = (ICensusSC) e;
            if (!c.censusCounts()) {
                return;
            }
            flags = (c.censusTier() >= ICensusSC.TIER_QUANTUM ? F_QUANTUM : 0) | (c.censusElite() ? F_ELITE : 0);
        }
        addPoint(e.posX, e.posY, e.posZ, flags);
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
