package com.sc.bridge;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * §7б «Мягкое приземление»: whoever comes out of a bridge end hanging in the air falls slowly (the fall capped at
 * FALL_CAP blocks a tick) and takes no fall damage until it touches the ground (water, a ladder, flying) or
 * MAX_TICKS pass. Living entities only (players; mobs through the Mass Compensator). The server owns the state -
 * a countdown in the entity's data (saved with it); a player's client gets the same countdown by a packet, since
 * the client moves its player. It only limits falling, never lifts, so it lives alongside the Singular armour's
 * anti-gravity / soft descent (each caps the fall; the slower cap wins, no damage either way).
 */
public final class BridgeSoftLandSC {

    /** The entity data key: ticks left. */
    public static final String TAG = "scBridgeSoftLand";
    /** The fall is never faster than this (blocks a tick, negative = down). */
    public static final double FALL_CAP = -0.15;
    /** 30 s at most. */
    public static final int MAX_TICKS = 600;
    /** The first ticks after the arrival the ground under the old position doesn't end it (onGround is stale after a teleport). */
    public static final int GRACE = 10;

    /** The handler on Forge's bus (SCMod). */
    public BridgeSoftLandSC() {
    }

    // ------------------------------------------------------------------ pure (SelfTestSC)

    /** The countdown's next value: 0 - over (on the ground after the grace, or the time is up). */
    public static int next(int left, boolean grounded) {
        if (left <= 0) {
            return 0;
        }
        if (grounded && left <= MAX_TICKS - GRACE) {
            return 0;
        }
        return left - 1;
    }

    /** The vertical motion with the cap applied (a fall slower than the cap, or going up, is left as it is). */
    public static double cap(double motionY) {
        return motionY < FALL_CAP ? FALL_CAP : motionY;
    }

    /** Coming out at a cell with this under it (BridgeSpaceSC kinds) needs a soft landing: nothing to stand on. */
    public static boolean needed(int below) {
        return below == BridgeSpaceSC.AIR || below == BridgeSpaceSC.PASS;
    }

    // ------------------------------------------------------------------ the world

    /** The server: an arrival at e's position - a soft landing if there is nothing to stand on under it. */
    public static boolean arrived(Entity e) {
        if (!(e instanceof EntityLivingBase) || e.worldObj == null || e.worldObj.isRemote) {
            return false;
        }
        World w = e.worldObj;
        int x = (int) Math.floor(e.posX), y = (int) Math.floor(e.boundingBox.minY + 0.001), z = (int) Math.floor(e.posZ);
        if (!needed(BridgeSpaceSC.of(w).cell(x, y - 1, z))) {
            return false;
        }
        start((EntityLivingBase) e);
        return true;
    }

    /** The server: the soft landing starts (or restarts) for e. */
    public static void start(EntityLivingBase e) {
        NBTTagCompound d = e.getEntityData();
        boolean was = d.getInteger(TAG) > 0;
        d.setInteger(TAG, MAX_TICKS);
        e.fallDistance = 0;
        if (e instanceof EntityPlayerMP && !(e instanceof net.minecraftforge.common.util.FakePlayer)) {
            EntityPlayerMP p = (EntityPlayerMP) e;
            if (p.playerNetServerHandler != null) {
                BridgeNetSC.CHANNEL.sendTo(new BridgeNetSC.SoftLand(MAX_TICKS), p);
                if (!was) {
                    p.addChatMessage(new BridgeMsgSC("sc.bridge.msg.softland").chat());
                }
            }
        }
    }

    public static boolean active(Entity e) {
        return e != null && e.getEntityData().getInteger(TAG) > 0;
    }

    /** The client: the server says this player comes out in the air. */
    public static void startClient(EntityPlayer p, int ticks) {
        if (p != null) {
            p.getEntityData().setInteger(TAG, Math.max(0, Math.min(MAX_TICKS, ticks)));
            p.fallDistance = 0;
        }
    }

    /** Every tick, both sides (a player's client caps its own motion; the server caps mobs' and keeps the fall distance at 0). */
    public static void tick(EntityLivingBase e) {
        NBTTagCompound d = e.getEntityData();
        int left = d.getInteger(TAG);
        if (left <= 0) {
            return;
        }
        boolean flying = e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isFlying;
        boolean grounded = e.onGround || flying || e.isInWater() || e.handleLavaMovement() || e.isOnLadder() || e.ridingEntity != null;
        int n = next(left, grounded);
        if (n <= 0) {
            d.removeTag(TAG);
            e.fallDistance = 0;
            return;
        }
        d.setInteger(TAG, n);
        e.fallDistance = 0;
        boolean moves = e.worldObj.isRemote ? e instanceof EntityPlayer : !(e instanceof EntityPlayer);
        if (moves) {
            e.motionY = cap(e.motionY);
        }
    }

    @SubscribeEvent
    public void onUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving != null) {
            tick(event.entityLiving);
        }
    }

    /** No fall damage while it lasts (before the armour's own fall handling). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.entityLiving != null && !event.entityLiving.worldObj.isRemote && active(event.entityLiving)) {
            event.distance = 0F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFlyableFall(net.minecraftforge.event.entity.player.PlayerFlyableFallEvent event) {
        if (event.entityPlayer != null && !event.entityPlayer.worldObj.isRemote && active(event.entityPlayer)) {
            event.distance = 0F;
        }
    }

    /** МС-8: a dimension loaded - the controllers' dimension list is built anew (it is cached for 60 s otherwise). */
    @SubscribeEvent
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load e) {
        if (!e.world.isRemote) {
            com.sc.tileentity.TileEntityBridgeControllerSC.invalidateDims();
        }
    }
}
