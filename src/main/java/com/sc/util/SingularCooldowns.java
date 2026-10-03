package com.sc.util;

import java.util.concurrent.atomic.AtomicLongArray;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Cooldowns of the suit functions, per player (the Singular functions: void rescue, heat vent, phase
 * dash ...; stage 2b adds its own). The server keeps the world tick each one ends at in the player's
 * persisted data (it survives death and relogging - a death doesn't reset a cooldown); every change is
 * sent to that player's client (ArmorNetSC.CooldownMessage), which keeps them for the K menu and the
 * HUD of stage 5. The world tick is the same in every dimension (the worlds share it).
 */
public final class SingularCooldowns {

    private static final String TAG = "scCooldowns";
    /** The client's copy: the end tick of each function by ordinal (0: none). Written by the network thread. */
    private static final AtomicLongArray CLIENT = new AtomicLongArray(64);

    private SingularCooldowns() {
    }

    /** Pure: ticks left until `end` at `now` (0 when over). */
    public static int left(long end, long now) {
        return end <= now ? 0 : (int) Math.min(Integer.MAX_VALUE, end - now);
    }

    private static NBTTagCompound store(EntityPlayer p, boolean create) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (create && !data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        NBTTagCompound cd = persisted.getCompoundTag(TAG);
        if (create && !persisted.hasKey(TAG)) {
            persisted.setTag(TAG, cd);
        }
        return cd;
    }

    /** The tick the function's cooldown ends at for this player (server: its data, client: the synced copy), 0: none. */
    public static long endOf(EntityPlayer p, ArmorFeature f) {
        if (p == null || f == null) {
            return 0L;
        }
        if (p.worldObj != null && p.worldObj.isRemote) {
            return f.ordinal() < CLIENT.length() ? CLIENT.get(f.ordinal()) : 0L;
        }
        return store(p, false).getLong(f.name());
    }

    /** Ticks left of the function's cooldown (0: ready). */
    public static int get(EntityPlayer p, ArmorFeature f) {
        return p == null || p.worldObj == null ? 0 : left(endOf(p, f), p.worldObj.getTotalWorldTime());
    }

    public static boolean ready(EntityPlayer p, ArmorFeature f) {
        return get(p, f) <= 0;
    }

    /** Server: the function cools down for `ticks` from now (0 clears it); the player's client is told. */
    public static void set(EntityPlayer p, ArmorFeature f, int ticks) {
        if (p == null || f == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        long end = ticks <= 0 ? 0L : p.worldObj.getTotalWorldTime() + ticks;
        NBTTagCompound cd = store(p, true);
        if (end <= 0) {
            cd.removeTag(f.name());
        } else {
            cd.setLong(f.name(), end);
        }
        send(p, f.ordinal(), end);
    }

    /** Server, at login: the client's copy cleared and every running cooldown sent again. */
    public static void syncAll(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        send(p, -1, 0L);
        long now = p.worldObj.getTotalWorldTime();
        for (ArmorFeature f : ArmorFeature.values()) {
            long end = endOf(p, f);
            if (end > now) {
                send(p, f.ordinal(), end);
            }
        }
    }

    private static void send(EntityPlayer p, int ordinal, long end) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null) {
            com.sc.handler.ArmorNetSC.CHANNEL.sendTo(new com.sc.handler.ArmorNetSC.CooldownMessage(ordinal, end), (EntityPlayerMP) p);
        }
    }

    /** The client's copy for one function ordinal (0: none). */
    public static long clientEnd(int ordinal) {
        return ordinal >= 0 && ordinal < CLIENT.length() ? CLIENT.get(ordinal) : 0L;
    }

    /** Client, from the server's message: ordinal -1 clears every cooldown. */
    public static void clientSet(int ordinal, long end) {
        if (ordinal < 0) {
            for (int i = 0; i < CLIENT.length(); i++) {
                CLIENT.set(i, 0L);
            }
        } else if (ordinal < CLIENT.length()) {
            CLIENT.set(ordinal, end);
        }
    }
}
