package com.sc.hazard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.ai.NpcNavSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §6.3, rules ПН-9..ПН-11; proposal §9 «Эхо моста»): the bridge's echo.
 * <ul>
 * <li>home from the Substrate by a bridge without the Portal Shield: HOME_CHANCE that PARTICLES_MIN..MAX nanite particles slip
 * through to the player's arrival (a new swarm there - EntityNaniteSC.spawnEcho: not «born in Si-14», no loot; outside the
 * Substrate they drain nothing - EntityNaniteSC's rule - and fade after ECHO_LIFE_TICKS, NBT ECHO_NBT); with the Shield never;</li>
 * <li>into the Substrate: ARRIVAL_CHANCE that SQUAD_DELAY later a squad of SQUAD_MIN..MAX Marauders (born in Si-14, the usual
 * elite roll; the Rift's chance in the Rift) appears SQUAD_DIST_MIN..MAX blocks from the arrival point, after this player - never
 * when the player came down on a dock or into a dome / its belt; at most once a player in COOLDOWN_TICKS (the player's entity
 * data COOLDOWN_NBT, world total time; set when a squad is sent);</li>
 * </ul>
 * TileEntityBridgeControllerSC.enter calls crossed() after a player went through (the coordinator's line) - a Vehicles ship's
 * Jump never does: no echo from it (ПН-11). The squads waiting live in memory (UUID, world, point, time - a restart forgets
 * them); a squad whose point's chunk isn't loaded waits up to SQUAD_WAIT more, then is dropped. Registered by SCMod on FML's bus
 * (the server tick for the squads, once a second). Server only.
 */
public final class BridgeEchoSC {

    public static final BridgeEchoSC INSTANCE = new BridgeEchoSC();
    /** ПН-9, ПН-10: the chances. TODO(balance): 10% each */
    public static final float HOME_CHANCE = 0.10F, ARRIVAL_CHANCE = 0.10F;
    /** ПН-9: the particles that slip through, their life outside the Substrate (60 s). */
    public static final int PARTICLES_MIN = 3, PARTICLES_MAX = 6, ECHO_LIFE_TICKS = 1200;
    /** ПН-10: the squad - after 60 s, 2..4 Marauders, 16..28 blocks off; once in 10 min a player. TODO(balance): the distance, the cooldown */
    public static final int SQUAD_DELAY = 1200, SQUAD_MIN = 2, SQUAD_MAX = 4, SQUAD_DIST_MIN = 16, SQUAD_DIST_MAX = 28, COOLDOWN_TICKS = 12000;
    /** The echo particles' entity NBT key: the world time they fade at. */
    public static final String ECHO_NBT = "scEcho";
    /** ПН-10: the player's entity data key - the world total time the next arrival echo may come at (not persistent: a death clears it). */
    public static final String COOLDOWN_NBT = "scEchoCd";
    /** ПН-11: a squad whose point isn't loaded waits this much past its time, then is dropped; the spots tried round it. */
    public static final int SQUAD_WAIT = 600, SQUAD_TRIES = 8;
    /** The places tried for each Marauder after the first, round it (uneven ground, a pond: so a squad is rarely one alone). */
    public static final int MEMBER_TRIES = 4;

    private static final List<Squad> SQUADS = new ArrayList<Squad>();
    private static final Random RAND = new Random();
    /** The world test: the rolls fixed (null - the dice). */
    private static Float forcedRoll;

    /** A squad waiting: whose, where, when (world total time). */
    private static final class Squad {
        final UUID player;
        final int dim;
        final double x, y, z;
        final long due;

        Squad(UUID player, int dim, double x, double y, double z, long due) {
            this.player = player;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.due = due;
        }
    }

    private BridgeEchoSC() {
    }

    /**
     * Server: player `p` came out of a bridge into dimension `toDim` at (x, y, z), from `fromDim`; `portalShield` - the bridge
     * it took has the Portal Shield (BridgeStructureSC's scan).
     */
    public static void crossed(EntityPlayerMP p, int fromDim, int toDim, double x, double y, double z, boolean portalShield) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        boolean from = SubstrateSC.isDim(fromDim), to = SubstrateSC.isDim(toDim);
        if (from == to) {
            return;                                         // within one world: no echo
        }
        World w = world(toDim, p);
        if (w == null) {
            return;
        }
        long now = w.getTotalWorldTime();
        if (from) {
            if (homeEcho(true, false, portalShield, roll())) {
                int n = PARTICLES_MIN + RAND.nextInt(PARTICLES_MAX - PARTICLES_MIN + 1);
                if (EntityNaniteSC.spawnEcho(w, x, y, z, n, ECHO_LIFE_TICKS, RAND) > 0) {
                    EmpStormSC.tell(p, EnumChatFormatting.DARK_PURPLE, "sc.echo.swarm");
                }
            }
            return;
        }
        boolean sheltered = SettlementSC.dockZoneAt(w, x, y, z) != null || SettlementSC.beltAt(w, x, z) != null;
        boolean cooling = p.getEntityData().getLong(COOLDOWN_NBT) > now;
        if (!arrivalEcho(false, true, sheltered, cooling, roll())) {
            return;
        }
        p.getEntityData().setLong(COOLDOWN_NBT, now + COOLDOWN_TICKS);
        SQUADS.add(new Squad(p.getUniqueID(), toDim, x, y, z, now + SQUAD_DELAY));
    }

    /** ПН-9 (pure): the echo home happens - out of the Substrate into another world, no Shield, the roll under HOME_CHANCE. */
    public static boolean homeEcho(boolean fromSubstrate, boolean toSubstrate, boolean shield, float roll) {
        return fromSubstrate && !toSubstrate && !shield && roll < HOME_CHANCE;
    }

    /** ПН-10 (pure): the squad comes - into the Substrate from elsewhere, not at a dock / in a dome, no cooldown, the roll. */
    public static boolean arrivalEcho(boolean fromSubstrate, boolean toSubstrate, boolean sheltered, boolean cooling, float roll) {
        return toSubstrate && !fromSubstrate && !sheltered && !cooling && roll < ARRIVAL_CHANCE;
    }

    /** The world test: every roll is `roll` from now on (0 - the echo always, 1 - never); null - the dice again. */
    public static void forceRoll(Float roll) {
        forcedRoll = roll;
    }

    /** The world test: the squads waiting for player `id`. */
    public static int waiting(UUID id) {
        int n = 0;
        for (Squad q : SQUADS) {
            if (q.player.equals(id)) {
                n++;
            }
        }
        return n;
    }

    /** The world test: the squads waiting for `id` come now, their time skipped. @return the Marauders that appeared */
    public static int comeNow(UUID id) {
        int made = 0;
        for (Iterator<Squad> it = SQUADS.iterator(); it.hasNext(); ) {
            Squad q = it.next();
            WorldServer w = q.player.equals(id) ? DimensionManager.getWorld(q.dim) : null;
            if (w != null) {
                made += Math.max(0, spawn(w, q));
                it.remove();
            }
        }
        return made;
    }

    /** Server stopped: the squads waiting. */
    public static void clearAll() {
        SQUADS.clear();
        forcedRoll = null;
    }

    // ------------------------------------------------------------------ the squads

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SQUADS.isEmpty()) {
            return;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || srv.getTickCounter() % 20 != 0) {
            return;
        }
        for (Iterator<Squad> it = SQUADS.iterator(); it.hasNext(); ) {
            Squad q = it.next();
            WorldServer w = DimensionManager.getWorld(q.dim);
            WorldServer clock = w != null ? w : srv.worldServerForDimension(0);
            long now = clock == null ? Long.MAX_VALUE : clock.getTotalWorldTime();
            if (now < q.due) {
                continue;
            }
            if (now > q.due + SQUAD_WAIT) {
                it.remove();                                // ПН-11: never loaded in time - dropped
                continue;
            }
            if (w != null && spawn(w, q) > 0) {
                it.remove();
            }
        }
    }

    /**
     * ПН-10: squad `q` at a loaded, free spot SQUAD_DIST_MIN..MAX from its point (not in a dome's belt nor a dock's zone), after
     * its player. @return the Marauders that appeared (0 - no spot loaded or free yet: it waits)
     */
    private static int spawn(WorldServer w, Squad q) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        for (int i = 0; i < SQUAD_TRIES; i++) {
            double a = RAND.nextDouble() * Math.PI * 2D;
            double d = SQUAD_DIST_MIN + RAND.nextDouble() * (SQUAD_DIST_MAX - SQUAD_DIST_MIN);
            int bx = MathHelper.floor_double(q.x + Math.cos(a) * d), bz = MathHelper.floor_double(q.z + Math.sin(a) * d);
            int y = NpcNavSC.groundY(w, bx, bz);
            if (y <= 0) {
                continue;                                   // not loaded: never loads a chunk
            }
            if (r != null && (r.inBelt(w.provider.dimensionId, bx + 0.5D, bz + 0.5D) != null
                    || r.dockZoneAt(w.provider.dimensionId, bx + 0.5D, y, bz + 0.5D) != null)) {
                continue;
            }
            boolean rift = SubstrateCellsSC.biomeKind(w, bx, bz) == BiomeSubstrateSC.RIFT;
            EntityPlayer target = w.func_152378_a(q.player);
            int n = SQUAD_MIN + RAND.nextInt(SQUAD_MAX - SQUAD_MIN + 1), made = 0;
            for (int k = 0; k < n; k++) {
                EntityLiving m = null;
                for (int t = 0; t < (k == 0 ? 1 : MEMBER_TRIES) && m == null; t++) {   // the others round the first: a few places each
                    double sx = bx + 0.5D + (k == 0 ? 0D : RAND.nextDouble() * 4D - 2D), sz = bz + 0.5D + (k == 0 ? 0D : RAND.nextDouble() * 4D - 2D);
                    int sy = k == 0 ? y : NpcNavSC.groundY(w, MathHelper.floor_double(sx), MathHelper.floor_double(sz));
                    m = sy <= 0 ? null : SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_MARAUDER, sx, sy, sz, true, rift);
                }
                if (m == null) {
                    continue;
                }
                made++;
                if (target != null && target.isEntityAlive() && !target.capabilities.isCreativeMode) {
                    m.setAttackTarget(target);
                }
            }
            if (made > 0) {
                if (target != null) {
                    EmpStormSC.tell(target, EnumChatFormatting.DARK_RED, "sc.echo.squad");
                }
                return made;
            }
        }
        return 0;
    }

    private static float roll() {
        Float f = forcedRoll;
        return f != null ? f.floatValue() : RAND.nextFloat();
    }

    /** The world the player came out in (it is there already; the bridge's own target otherwise). */
    private static World world(int dim, EntityPlayerMP p) {
        if (p.worldObj != null && p.worldObj.provider.dimensionId == dim) {
            return p.worldObj;
        }
        return DimensionManager.getWorld(dim);
    }
}
