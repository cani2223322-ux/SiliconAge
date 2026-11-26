package com.sc.settle.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.settle.npc.EntityGathererSC;
import com.sc.tileentity.TileEntityShowcaseSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A, part 2 - the client's own): the Gatherer's render (RenderGathererSC:
 * a Tuner with a sack), the Tuner drones hovering over the settlements' workshops and the moths in the canyons at night - drawn only on
 * this client (client particles / client-only entities, never sent by a server), off with dimension.ambientLife
 * (SubstrateConfigSC.ambientLife() - each client's own). ClientProxy calls register() (the coordinator's); everything in it is ПД-5's.
 * <p>
 * The client tick, every EVERY ticks, only in the Substrate and with ambientLife on: the drones - the workshops' showcases
 * (TileEntityShowcaseSC in the loaded tile list) within DRONE_REACH, one drone to a group of GROUP blocks, DRONES at most; the moths - at
 * night (MOTH_FROM..MOTH_TO of the day), the player in the Scribe Canyons' belt, MOTHS at most within MOTH_REACH, born at a Copper Trace
 * or a flora block. Nothing is written into the world, nothing comes from the server.
 */
@SideOnly(Side.CLIENT)
public final class AmbientLifeClientSC {

    public static final int EVERY = 20, DRONE_REACH = 48, GROUP = 8, DRONES = 6;
    public static final int MOTH_FROM = 13000, MOTH_TO = 23000, MOTH_REACH = 24, MOTHS = 24, MOTH_TRIES = 12;

    private static final List<EntityDroneFxSC> DRONE_LIST = new ArrayList<EntityDroneFxSC>();
    private static final List<EntityMothFxSC> MOTH_LIST = new ArrayList<EntityMothFxSC>();
    private static final Random RAND = new Random();
    private static World seenWorld;

    private AmbientLifeClientSC() {
    }

    /** ClientProxy.preInit: the renderers, the client tick for the drones and the moths. */
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityGathererSC.class, new RenderGathererSC());
        FMLCommonHandler.instance().bus().register(new AmbientLifeClientSC.Handler());
    }

    /** Pure: the moths' hours (the day's ticks). */
    public static boolean mothTime(long worldTime) {
        long t = (worldTime % 24000L + 24000L) % 24000L;
        return t >= MOTH_FROM && t < MOTH_TO;
    }

    public static final class Handler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            World w = mc.theWorld;
            EntityPlayer p = mc.thePlayer;
            if (w != seenWorld) {
                DRONE_LIST.clear();
                MOTH_LIST.clear();
                seenWorld = w;
            }
            if (w == null || p == null || mc.isGamePaused() || p.ticksExisted % EVERY != 0 || !SubstrateSC.is(w)
                    || !SubstrateConfigSC.ambientLife() || mc.effectRenderer == null) {
                return;
            }
            prune(DRONE_LIST);
            prune(MOTH_LIST);
            drones(mc, w, p);
            moths(mc, w, p);
        }
    }

    private static void prune(List<? extends EntityFX> l) {
        for (Iterator<? extends EntityFX> it = l.iterator(); it.hasNext(); ) {
            if (it.next().isDead) {
                it.remove();
            }
        }
    }

    /** One drone to a group of showcases within DRONE_REACH (DRONES at most). */
    private static void drones(Minecraft mc, World w, EntityPlayer p) {
        if (DRONE_LIST.size() >= DRONES) {
            return;
        }
        Set<Long> taken = new HashSet<Long>();
        for (EntityDroneFxSC d : DRONE_LIST) {
            taken.add(Long.valueOf(((long) d.groupX() << 32) ^ (d.groupZ() & 0xFFFFFFFFL)));
        }
        List<?> tiles = w.loadedTileEntityList;                  // Э7: read in place (the client thread; addEffect doesn't touch it)
        for (int i = 0; i < tiles.size() && DRONE_LIST.size() < DRONES; i++) {
            Object o = tiles.get(i);
            if (!(o instanceof TileEntityShowcaseSC)) {
                continue;
            }
            TileEntityShowcaseSC t = (TileEntityShowcaseSC) o;
            double dx = t.xCoord + 0.5D - p.posX, dz = t.zCoord + 0.5D - p.posZ;
            if (dx * dx + dz * dz > (double) DRONE_REACH * DRONE_REACH) {
                continue;
            }
            Long key = Long.valueOf(((long) (t.xCoord >> 3) << 32) ^ ((t.zCoord >> 3) & 0xFFFFFFFFL));
            if (!taken.add(key)) {
                continue;
            }
            double h = EntityDroneFxSC.HOVER_MIN + RAND.nextDouble() * (EntityDroneFxSC.HOVER_MAX - EntityDroneFxSC.HOVER_MIN);
            EntityDroneFxSC d = new EntityDroneFxSC(w, t.xCoord + 0.5D, t.yCoord + h, t.zCoord + 0.5D);
            mc.effectRenderer.addEffect(d);
            DRONE_LIST.add(d);
        }
    }

    /** The night's moths round a canyon's Copper Traces and flora within MOTH_REACH (MOTHS at most). */
    private static void moths(Minecraft mc, World w, EntityPlayer p) {
        int px = MathHelper.floor_double(p.posX), pz = MathHelper.floor_double(p.posZ);
        if (!mothTime(w.getWorldTime()) || !SubstrateTerrainSC.inCanyonBelt(px, pz)) {
            return;
        }
        for (int i = 0; i < MOTH_TRIES && MOTH_LIST.size() < MOTHS; i++) {
            int x = px + RAND.nextInt(2 * MOTH_REACH + 1) - MOTH_REACH, z = pz + RAND.nextInt(2 * MOTH_REACH + 1) - MOTH_REACH;
            int y0 = MathHelper.floor_double(p.posY);
            for (int y = y0 + 6; y >= y0 - 8; y--) {
                if (!w.blockExists(x, y, z)) {
                    break;
                }
                Block b = w.getBlock(x, y, z);
                boolean trace = b == SubstrateBlocksSC.rock && w.getBlockMetadata(x, y, z) == SubstrateBlocksSC.COPPER_TRACE;
                if ((trace || b == SubstrateWildBlocksSC.flora) && w.isAirBlock(x, y + 1, z)) {
                    EntityMothFxSC m = new EntityMothFxSC(w, x + 0.5D, y + 1.2D + RAND.nextDouble(), z + 0.5D);
                    mc.effectRenderer.addEffect(m);
                    MOTH_LIST.add(m);
                    break;
                }
            }
        }
    }
}
