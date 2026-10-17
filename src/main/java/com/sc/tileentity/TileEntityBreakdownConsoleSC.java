package com.sc.tileentity;

import com.sc.arena.ArenaDataSC;
import com.sc.arena.ArenaWorldSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.4, rules ПУ-11, ПС-56; proposal §9 «Ключ вставляют в Консоль Пробоя. На мирной
 * сложности Консоль отказывает»): «Консоль Пробоя» - the arena's one TileEntity, at ArenaLayoutSC.CONSOLE_DX / DZ, floorY + 1.
 * It knows its arena's region (NBT "scArena" {ri, rj}: setup by the structure piece) and asks the record (ArenaDataSC) for the rest;
 * a click (BlockArenaSC, server) goes to ArenaWorldSC.start - never a FakePlayer's (no machine starts a boss fight). Its tick (every
 * TICK_EVERY): the first one after a load asks for the reset (ПС-56: the world time since the record's lastPlayerAt -
 * ArenaWorldSC.resetIfDue), then the key's glow - a change of the record's keyIn re-sends the description packet (clientKey: the
 * block's lit key slot and its sparks). Unbreakable (the block), no inventory - the key lives in the record (keyIn), so a broken
 * chunk or a /setblock never duplicates it.
 * <p>
 * Fixed (the coordinator's): the class, setup / ri / rj / click, the NBT key, the description packet. A's: updateEntity's body,
 * click's text when no record (a hand-placed console: sc.arena.noarena).
 */
public class TileEntityBreakdownConsoleSC extends TileEntity {

    public static final String NBT = "scArena";
    /** The console's slow tick (ticks). */
    public static final int TICK_EVERY = 20;

    /** The arena's region; set - the structure placed it (a hand-placed one has none). */
    private int ri, rj;
    private boolean set;
    /** Client: a key lies in the console (the glow). */
    public boolean clientKey;
    /** Server: the first tick after a load is done; the glow the clients were last sent. */
    private boolean loaded, sentKey;

    public TileEntityBreakdownConsoleSC() {
    }

    /** The structure piece, right after placing the block: the console of region (ri, rj)'s arena. */
    public void setup(int regionI, int regionJ) {
        ri = regionI;
        rj = regionJ;
        set = true;
        markDirty();
    }

    public boolean isSet() {
        return set;
    }

    public int ri() {
        return ri;
    }

    public int rj() {
        return rj;
    }

    /** Its arena's record, or null (not set up, not the Substrate, a client). */
    public ArenaDataSC.Arena arena() {
        ArenaDataSC d = set ? ArenaDataSC.get(worldObj) : null;
        return d == null ? null : d.arena(ri, rj);
    }

    /** Server: `p` clicked it. */
    public void click(EntityPlayer p) {
        if (p == null || p instanceof FakePlayer || !(worldObj instanceof WorldServer)) {
            return;                                             // FakePlayer: never
        }
        ArenaDataSC.Arena a = arena();
        if (a == null) {
            ChatComponentTranslation m = new ChatComponentTranslation("sc.arena.noarena");
            m.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
            p.addChatComponentMessage(m);
            return;
        }
        ArenaWorldSC.start((WorldServer) worldObj, a, p, xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || !(worldObj instanceof WorldServer)) {
            return;
        }
        if (!loaded) {
            loaded = true;
            ArenaDataSC.Arena a = arena();
            sentKey = a != null && a.keyIn;                     // the chunk's own packets carried it
            if (a != null) {
                ArenaWorldSC.resetIfDue((WorldServer) worldObj, a);   // ПС-56: loaded long after everyone left
            }
            return;
        }
        if ((worldObj.getTotalWorldTime() + (xCoord & 15)) % TICK_EVERY != 0) {
            return;
        }
        ArenaDataSC.Arena a = arena();
        boolean key = a != null && a.keyIn;
        if (key != sentKey) {
            sentKey = key;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (set) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("ri", ri);
            t.setInteger("rj", rj);
            nbt.setTag(NBT, t);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        set = nbt.hasKey(NBT);
        NBTTagCompound t = nbt.getCompoundTag(NBT);
        ri = t.getInteger("ri");
        rj = t.getInteger("rj");
    }

    /** The key's glow to the clients (the record's keyIn). */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        ArenaDataSC.Arena a = arena();
        t.setBoolean("key", a != null && a.keyIn);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, t);
    }

    /** Client: the glow, and the block drawn again (its key slot's icon - BlockArenaSC). */
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        boolean key = pkt.func_148857_g().getBoolean("key");
        if (key != clientKey) {
            clientKey = key;
            if (worldObj != null) {
                worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
            }
        }
    }
}
