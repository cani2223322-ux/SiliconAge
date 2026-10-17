package com.sc.mob;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
 */
public final class SpawnDirectorSC {

    public static final SpawnDirectorSC INSTANCE = new SpawnDirectorSC();

    /** Ticks since each player's last try (by UUID; a new one starts at a random point - the players don't all fire at once). */
    private static final Map<UUID, Integer> COUNTERS = new HashMap<UUID, Integer>();
    /** The census: the world and its tick it is of, the natives' positions (x, y, z). */
    private static int censusDim = Integer.MIN_VALUE;
    private static long censusTime = Long.MIN_VALUE;
    private static double[] census = new double[3 * 64];
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

    /** One try for one player (server). @return how many mobs appeared */
    public static int tryGroup(WorldServer w, EntityPlayer p) {
        boolean peaceful = w.difficultySetting == EnumDifficulty.PEACEFUL;
        if (peaceful || !w.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !hostilesAllowed(w)) {
            return 0;
        }
        takeCensus(w);
        int near = near(p.posX, p.posY, p.posZ);
        if (!SpawnRulesSC.maySpawn(near, censusN, 1, false)) {
            return 0;
        }
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
        int[] weights = kindOf(w, spot[0], spot[2]) == BiomeSubstrateSC.CANYONS ? SpawnRulesSC.CANYON_WEIGHTS : SpawnRulesSC.PLATEAU_WEIGHTS;
        int total = SpawnRulesSC.sum(weights);
        int kind = total <= 0 ? -1 : SpawnRulesSC.pick(weights, rand.nextInt(total));
        if (kind < 0 || kind >= SpawnRulesSC.GROUP_MIN.length) {
            return 0;
        }
        int want = SpawnRulesSC.GROUP_MIN[kind] + rand.nextInt(SpawnRulesSC.GROUP_MAX[kind] - SpawnRulesSC.GROUP_MIN[kind] + 1);
        int group = SpawnRulesSC.fitGroup(near, censusN, want, 1, false);
        int made = 0;
        for (int m = 0; m < group; m++) {
            int[] at = m == 0 ? spot : null;
            for (int t = 0; at == null && t < SpawnRulesSC.MEMBER_TRIES; t++) {
                int s = SpawnRulesSC.GROUP_SPREAD;
                at = spotAt(w, spot[0] + rand.nextInt(2 * s + 1) - s, spot[2] + rand.nextInt(2 * s + 1) - s);
            }
            if (at != null && spawn(w, kind, at)) {
                made++;
            }
        }
        return made;
    }

    /**
     * A place to stand on at column (x, z): its top by the height map, solid under it, two free blocks, no player within
     * MIN_DIST, not within SPAWN_MARGIN past a settlement's dome; null none. Public for the world test (ПП-11).
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
        return new int[]{x, y, z};
    }

    /** The column's biome kind: the world's own biome, else the terrain's maths. */
    private static int kindOf(World w, int x, int z) {
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        return b instanceof BiomeSubstrateSC ? ((BiomeSubstrateSC) b).kind : SubstrateTerrainSC.biomeKind(x, z);
    }

    private static boolean spawn(WorldServer w, int kind, int[] at) {
        EntityArmoredMobSC e = kind == MobEntitiesSC.ID_MARAUDER ? new EntityMarauderSC(w) : new EntityLinemanSC(w);
        float x = at[0] + 0.5F, y = at[1], z = at[2] + 0.5F;
        e.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360F, 0F);
        if (!w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() || w.isAnyLiquid(e.boundingBox)
                || ForgeEventFactory.canEntitySpawn(e, w, x, y, z) == Event.Result.DENY) {
            return false;
        }
        if (!ForgeEventFactory.doSpecialSpawn(e, w, x, y, z)) {
            e.onSpawnWithEgg((IEntityLivingData) null);
        }
        BornInSubstrateSC.mark(e);
        if (!w.spawnEntityInWorld(e)) {
            return false;
        }
        add(e);
        return true;
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
            if (o instanceof ISubstrateNativeSC && o instanceof IMob && !((Entity) o).isDead) {
                add((Entity) o);
            }
        }
    }

    private static void add(Entity e) {
        if (3 * censusN + 3 > census.length) {
            double[] more = new double[census.length * 2];
            System.arraycopy(census, 0, more, 0, census.length);
            census = more;
        }
        census[3 * censusN] = e.posX;
        census[3 * censusN + 1] = e.posY;
        census[3 * censusN + 2] = e.posZ;
        censusN++;
    }

    /** Natives in the box NEAR_RADIUS round a point (this tick's census). */
    private static int near(double x, double y, double z) {
        int r = SpawnRulesSC.NEAR_RADIUS, n = 0;
        for (int i = 0; i < censusN; i++) {
            if (Math.abs(census[3 * i] - x) <= r && Math.abs(census[3 * i + 1] - y) <= r && Math.abs(census[3 * i + 2] - z) <= r) {
                n++;
            }
        }
        return n;
    }
}
