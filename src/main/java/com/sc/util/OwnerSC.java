package com.sc.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * ЭН-3 (audit 2026-10-20, ★ A): a block's owner by UUID, as the quarry has it since КР-5 of 2026-10-08 (TileEntityQuarrySC.isOwner):
 * the name for the screens and the chat, the UUID once known for sure (saved). The check goes by the UUID; by the name (any case)
 * only for an old block without one - and on the server the first match writes the player's UUID down. A renamed owner keeps
 * his blocks (the name follows on his next action), a newcomer with the old name gets none. On the client by the name only: in a
 * LAN game without accounts the client's own UUID may differ from the one the server gave it. For the wireless blocks, the
 * converter, the charge pad, the bridge controller and the field generator (their access lists stay by names). A plain holder,
 * one per tile: not thread-safe, the server's thread (and the client's for the name) only.
 */
public final class OwnerSC {

    private String name = "";
    /** The UUID as a string once known for sure ("" until then: an old block, or not looked up yet). */
    private String id = "";
    /** The profile for other mods' claim checks (not saved) and the name it was made for. */
    private GameProfile profile;
    private String profileFor;
    /** The profile's UUID is a guess (the UsernameCache's or the offline one): the owner online replaces it. */
    private boolean profileGuessed;

    /** The owner's name ("" - nobody: anyone may, as the blocks had it). */
    public String name() {
        return name;
    }

    /** The owner's UUID string, "" while unknown. */
    public String id() {
        return id;
    }

    public boolean isEmpty() {
        return name.isEmpty();
    }

    /**
     * Owned by `n` (null or "" - nobody). On the server (`w` not remote) an online player of that name gives his UUID at once;
     * otherwise it is learnt on the owner's first action (matches).
     */
    public void set(String n, World w) {
        name = n == null ? "" : n;
        id = "";
        profile = null;
        if (w != null && !w.isRemote && !name.isEmpty()) {
            EntityPlayerMP p = online(name);
            if (p != null && p.getUniqueID() != null) {
                id = p.getUniqueID().toString();
            }
        }
    }

    /** Owned by player `p` (the placer): his name and UUID; null or a FakePlayer - nobody (automation leaves a block ownerless). */
    public void set(EntityPlayer p) {
        profile = null;
        if (p == null || p instanceof net.minecraftforge.common.util.FakePlayer) {
            name = "";
            id = "";
            return;
        }
        name = p.getCommandSenderName();
        id = p.getUniqueID() == null ? "" : p.getUniqueID().toString();
    }

    public void clear() {
        name = "";
        id = "";
        profile = null;
    }

    /**
     * Is `p` the owner (false with no owner - the caller decides what an ownerless block allows)? By the UUID once known; by the
     * name before that. On the server a renamed owner's new name and an old block's UUID are written down, and `te` (may be null)
     * is marked dirty then. On the client (te's world remote) by the name only.
     */
    public boolean matches(EntityPlayer p, TileEntity te) {
        if (p == null || name.isEmpty()) {
            return false;
        }
        World w = te == null ? p.worldObj : te.getWorldObj();
        boolean server = w != null && !w.isRemote;
        if (!server) {
            return name.equalsIgnoreCase(p.getCommandSenderName());
        }
        String pid = p.getUniqueID() == null ? null : p.getUniqueID().toString();
        if (!id.isEmpty() && pid != null) {
            if (!id.equalsIgnoreCase(pid)) {
                return false;
            }
            if (!name.equals(p.getCommandSenderName()) && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
                name = p.getCommandSenderName();          // the owner was renamed: the name follows
                profile = null;
                dirty(te);
            }
            return true;
        }
        if (!name.equalsIgnoreCase(p.getCommandSenderName())) {
            return false;
        }
        if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer) && pid != null) {
            id = pid;                                     // an old block: the owner's UUID, now known for sure
            profile = null;
            dirty(te);
        }
        return true;
    }

    /** The name only, any case (an offline owner's things, a zone's record, the old screens' checks). */
    public boolean nameIs(String n) {
        return n != null && !name.isEmpty() && name.equalsIgnoreCase(n);
    }

    /**
     * The owner as a GameProfile for other mods' claim checks (a FakePlayer's BreakEvent), null with no owner. The UUID: the saved
     * one; else the player online (then saved, `te` - may be null - marked dirty); else the UUID this server last saw the name
     * log in with (Forge's UsernameCache: a map in memory - no web lookup, no file written; ★: the vanilla profile cache asked
     * Mojang in the server's thread for an expired name and rewrote usercache.json on every call, and for a TLauncher name Mojang
     * may know another, premium account); else the offline UUID of the name. Those two are not saved, but kept until the name
     * changes or the owner is seen online.
     */
    public GameProfile profile(TileEntity te) {
        if (name.isEmpty()) {
            return null;
        }
        if (profile != null && name.equals(profileFor) && !profileGuessed) {
            return profile;
        }
        java.util.UUID u = null;
        if (!id.isEmpty()) {
            try {
                u = java.util.UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                id = "";
            }
        }
        if (u == null) {
            EntityPlayerMP p = online(name);
            GameProfile g = p == null || p instanceof net.minecraftforge.common.util.FakePlayer ? null : p.getGameProfile();
            if (g != null && g.getId() != null) {
                u = g.getId();
                id = u.toString();
                dirty(te);
            }
        }
        if (u == null) {
            if (profile != null && name.equals(profileFor)) {
                return profile;                                   // the guess, made once for this name
            }
            u = seenId(name);
            if (u == null) {
                u = EntityPlayer.func_146094_a(new GameProfile(null, name));   // what an offline server gives the name
            }
            profile = new GameProfile(u, name);
            profileFor = name;
            profileGuessed = true;
            return profile;
        }
        profile = new GameProfile(u, name);
        profileFor = name;
        profileGuessed = false;
        return profile;
    }

    /** The player of name `n` online on this server, or null (no server, or no player list yet - the self-test at preInit). */
    private static EntityPlayerMP online(String n) {
        MinecraftServer srv = MinecraftServer.getServer();
        net.minecraft.server.management.ServerConfigurationManager cm = srv == null ? null : srv.getConfigurationManager();
        return cm == null ? null : cm.func_152612_a(n);
    }

    /** The UUID this server last saw `n` (any case) log in with - Forge's UsernameCache; null - never seen. */
    private static java.util.UUID seenId(String n) {
        java.util.Map<java.util.UUID, String> seen;
        try {
            seen = net.minecraftforge.common.UsernameCache.getMap();
        } catch (RuntimeException e) {
            return null;
        }
        for (java.util.Map.Entry<java.util.UUID, String> e : seen.entrySet()) {
            if (n.equalsIgnoreCase(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Reads the owner from `nbt`: the name under `nameKey` (the key the block always had), the UUID under `idKey` (absent - unknown). */
    public void read(NBTTagCompound nbt, String nameKey, String idKey) {
        name = nbt.getString(nameKey);
        id = name.isEmpty() ? "" : nbt.getString(idKey);
        profile = null;
    }

    /** Writes the name under `nameKey` (always, "" too) and the UUID under `idKey` (only when known). */
    public void write(NBTTagCompound nbt, String nameKey, String idKey) {
        nbt.setString(nameKey, name);
        if (!id.isEmpty()) {
            nbt.setString(idKey, id);
        } else {
            nbt.removeTag(idKey);
        }
    }

    private static void dirty(TileEntity te) {
        if (te != null && te.getWorldObj() != null) {
            te.markDirty();
        }
    }
}
