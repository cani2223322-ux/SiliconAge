package com.sc.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * ПД-7 (docs/plan-ideas-2026-10-26.md): the Relay's tile - on a RELAY block the defence it runs (settle.RelayPostSC: who set it up, when,
 * the wave), on the built post's MAST the post's settlement id. NBT under NBT ("scRelay") - fixed by the coordinator: {by: the player's
 * UUID, at: the world time it was set up, post: the settlement id (-1 none)}. ПД-7 writes the rest.
 */
public class TileEntityRelaySC extends TileEntity {

    public static final String NBT = "scRelay";

    /** The post's settlement id (a MAST), -1 none. */
    protected int postId = -1;
    /** Who set it up (UUID string, "" none) and when (world time). */
    protected String by = "";
    protected long at;
    /** ПД-7 (the review's fix, appended NBT: wave, held, hp): the defence's last wave (-1 none yet), the ticks held, the Relay's HP (-1 full). */
    protected int wave = -1, hp = -1;
    protected long held;

    public int postId() {
        return postId;
    }

    /** ПД-7 (RelayPostSC): the Relay set up by `uuid` at world time `time`. */
    public void setUp(String uuid, long time) {
        by = uuid == null ? "" : uuid;
        at = time;
        wave = -1;
        held = 0L;
        hp = -1;
        markDirty();
    }

    /** ПД-7 (RelayPostSC, every step of the defence): how far it got - a restart pauses it here, a right click goes on. */
    public void progress(int wave, long held, int hp) {
        if (this.wave != wave || this.held != held || this.hp != hp) {
            this.wave = wave;
            this.held = held;
            this.hp = hp;
            markDirty();
        }
    }

    /** The defence's last wave (-1 none yet). */
    public int wave() {
        return wave;
    }

    /** The ticks the defence was held. */
    public long held() {
        return held;
    }

    /** The Relay's HP (-1 full). */
    public int hp() {
        return hp;
    }

    /** ПД-7 (RelayPostSC): the MAST of post `id`. */
    public void setPost(int id) {
        postId = id;
        markDirty();
    }

    /** Who set it up (UUID string, "" none). */
    public String by() {
        return by;
    }

    @Override
    public boolean canUpdate() {
        return false;                                           // ПД-7: true once the defence ticks here
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("by", by == null ? "" : by);
        t.setLong("at", at);
        t.setInteger("post", postId);
        t.setInteger("wave", wave);
        t.setLong("held", held);
        t.setInteger("hp", hp);
        nbt.setTag(NBT, t);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagCompound t = nbt.getCompoundTag(NBT);
        by = t.getString("by");
        at = t.getLong("at");
        postId = t.hasKey("post") ? t.getInteger("post") : -1;
        wave = t.hasKey("wave") ? t.getInteger("wave") : -1;
        held = t.getLong("held");
        hp = t.hasKey("hp") ? t.getInteger("hp") : -1;
    }
}
