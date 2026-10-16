package com.sc.dim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sc.bridge.BridgeTeleportSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * PACKAGE E (docs/plan-substrate-tech.md §6.4): moves of players put off to the next server tick - never from inside
 * another transfer (PlayerChangedDimensionEvent fires in the middle of the server's own one). strictEntry's way back
 * now; the Return Capsule in Ф2. Each move checks the place first (two free blocks over a solid one, no one else's
 * private field) and goes through BridgeTeleportSC.teleport. Registered by SCMod on FML's bus (ServerTickEvent).
 * <p>
 * One move per player: queueing the same player again replaces the old one. A player gone (logged out) or dead by
 * the time it is due is dropped - a death sends them out of the Substrate anyway. No safe place within SEARCH_RADIUS
 * of the asked point: the spawn of dimension.respawnDimension, where a death would have taken them.
 */
public final class TeleportQueueSC {

    public static final TeleportQueueSC INSTANCE = new TeleportQueueSC();

    /** SafeSpotSC looks this far (columns) round the asked point, and round the respawn world's spawn after it. */
    public static final int SEARCH_RADIUS = 8;

    /** One move: who, where to, the chat key told there; queuedAt - the server's tick it was asked in. */
    private static final class Move {
        final UUID id;
        final int dim, queuedAt;
        final double x, y, z;
        final float yaw;
        final String reason;

        Move(UUID id, int dim, double x, double y, double z, float yaw, String reason, int queuedAt) {
            this.id = id;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.reason = reason;
            this.queuedAt = queuedAt;
        }
    }

    /** Server thread only (queue is asked from server events, the tick runs there). */
    private static final Map<UUID, Move> QUEUE = new LinkedHashMap<UUID, Move>();

    private TeleportQueueSC() {
    }

    /** Server: `p` goes to (x, y, z) of `dim` on the next tick; `reason` is the chat key told on arrival (null: none). */
    public static void queue(EntityPlayerMP p, int dim, double x, double y, double z, float yaw, String reason) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        UUID id = p.getUniqueID();
        QUEUE.remove(id);                                  // the newer move replaces it (and goes to the end of the line)
        QUEUE.put(id, new Move(id, dim, x, y, z, yaw, reason, tick()));
    }

    /** A move of `p` is waiting (the world test). */
    public static boolean queued(EntityPlayer p) {
        return p != null && QUEUE.containsKey(p.getUniqueID());
    }

    /** Server stopped. */
    public static void clear() {
        QUEUE.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) {
            QUEUE.clear();
            return;
        }
        int now = srv.getTickCounter();
        // taken out first: a move may queue another (a transfer's own events) while the line is being walked
        List<Move> due = new ArrayList<Move>();
        for (Iterator<Move> it = QUEUE.values().iterator(); it.hasNext();) {
            Move m = it.next();
            if (now > m.queuedAt) {                        // not in the tick it was asked in: the transfer that asked is over
                due.add(m);
                it.remove();
            }
        }
        for (Move m : due) {
            try {
                carry(srv, m);
            } catch (RuntimeException ex) {
                FMLLog.warning("[Silicon Age] Si-14 Substrate: a queued move to dimension %d failed: %s", m.dim, ex);
            }
        }
    }

    private static void carry(MinecraftServer srv, Move m) {
        EntityPlayerMP p = player(srv, m.id);
        if (p == null || p.isDead || p.getHealth() <= 0F || p.playerNetServerHandler == null) {
            return;
        }
        WorldServer w = world(srv, m.dim);
        int[] at = w == null ? null : SafeSpotSC.find(w, p, MathHelper.floor_double(m.x), MathHelper.floor_double(m.y),
                MathHelper.floor_double(m.z), SEARCH_RADIUS);
        if (at == null) {
            // nowhere safe there: the respawn world's spawn (as a death would) - a safe place by it, else its top
            w = world(srv, SubstrateSC.respawnDimension());
            if (w == null) {
                w = srv.worldServerForDimension(0);
            }
            ChunkCoordinates s = w.getSpawnPoint();
            int sy = SafeSpotSC.surfaceY(w, s.posX, s.posY, s.posZ);
            at = SafeSpotSC.find(w, p, s.posX, sy, s.posZ, SEARCH_RADIUS);
            if (at == null) {
                at = new int[]{s.posX, sy, s.posZ};
            }
        }
        // nobody crosses mounted (the server's transfer leaves a mount behind half-attached)
        if (p.ridingEntity != null) {
            p.mountEntity((Entity) null);
        }
        if (p.riddenByEntity != null) {
            p.riddenByEntity.mountEntity((Entity) null);
        }
        Entity moved = BridgeTeleportSC.teleport(p, w.provider.dimensionId, at[0] + 0.5, at[1], at[2] + 0.5, m.yaw);
        if (moved == null) {
            FMLLog.warning("[Silicon Age] Si-14 Substrate: %s couldn't be moved to dimension %d (%d %d %d)", p.getCommandSenderName(),
                    w.provider.dimensionId, at[0], at[1], at[2]);
            return;
        }
        if (m.reason != null && moved instanceof EntityPlayerMP) {
            ((EntityPlayerMP) moved).addChatComponentMessage(new ChatComponentTranslation(m.reason));
        }
    }

    /** The online player with this UUID, or null. */
    private static EntityPlayerMP player(MinecraftServer srv, UUID id) {
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** The world of `dim` (loading it), or null when there is no such dimension or it won't load. */
    private static WorldServer world(MinecraftServer srv, int dim) {
        if (!DimensionManager.isDimensionRegistered(dim)) {
            return null;
        }
        try {
            return srv.worldServerForDimension(dim);
        } catch (RuntimeException ex) {
            FMLLog.warning("[Silicon Age] Si-14 Substrate: dimension %d didn't load for a queued move: %s", dim, ex);
            return null;
        }
    }

    /** The server's tick (the same clock in every dimension). */
    private static int tick() {
        MinecraftServer srv = MinecraftServer.getServer();
        return srv == null ? 0 : srv.getTickCounter();
    }
}
