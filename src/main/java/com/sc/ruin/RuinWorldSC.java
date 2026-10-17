package com.sc.ruin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntitySentinelSC;
import com.sc.mob.EntityWarlordSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.RareRegistrySC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.4, §8.6, rules ПЗ-6, ПХ-4): the camps' and the Vaults' slow life - registered by
 * SCMod on FML's bus (the Substrate's world tick, every CHECK_TICKS): for every record of RareRegistrySC whose place is loaded and
 * has no player within RESPAWN_CLEAR - a camp's Warlord and residents back CAMP_RESTORE_TICKS after the Warlord died (the safe
 * relocked, a missing Stolen Field Generator set again where its block is air), a Vault's Sentinel back SENTINEL_RESPAWN_TICKS
 * after it died; the «lost» check of a rare one gone without dying (RareRegistrySC.missed). Signatures and numbers fixed (the
 * coordinator's); the bodies C's.
 * <p>
 * The records (RareRegistrySC, keys campKey / vaultKey; the state is the Warlord's / the Sentinel's) keep in their extra what the
 * return needs, written when the structure placed its residents (campMade / vaultMade - the numbers of that day, so a retuned
 * layout never moves an old camp): a camp's middle on its floor (X_MID), safe, generator, panels, pad (X_PAD, an Executioner's
 * camp only), box, home radius, Marauders and whether it has an Executioner; a Vault's middle, the Sentinel's spot, home radius.
 * <ul>
 * <li>«Lost» (every CHECK_TICKS): an ALIVE rare one of a camp or a Vault found among the loaded entities is seen (its place
 * kept fresh); all it may be in loaded without it (areaLoaded: its structure with the leash, and round its last place - a
 * walk into a chunk just past the view is no miss) - one more miss; RareRegistrySC.LOST_CHECKS of them - dead now. A return
 * too needs all that area loaded (its residents counted in full).</li>
 * <li>A camp's return (ПЗ-6; also from its generator's camp tick - TileEntityStolenFieldSC): the Warlord first (a lost one
 * found again is taken back, else a new one - no room: nothing changes, next time), then the safe relocked (CAMP_SAFE), the
 * generator, panels and pad only where their block is air (the players' buildings are never touched; a generator set again
 * has its buffer full), the Marauders up to the camp's count and the Executioner (counted by their scHome among the loaded),
 * on free floor in the box's loaded chunks.</li>
 * <li>A Vault's Sentinel (ПХ-4): at its spot (or another corner of the room) in the bunker, the same key.</li>
 * </ul>
 * Every resident: SpawnDirectorSC.spawnAt (born in Si-14, outside the director's caps), persistent, at home - vanilla's
 * setHomeArea and the entity NBT HOME_NBT {x, y, z, r, l[, u]} (getEntityData: the structure's middle, its half size, the leash -
 * the Sentinel's 24 past the bunker, a camp's WARLORD_LEASH past its box for all of it; u - a camp's: its home area a column up
 * to CAMP_RIM over the floor, EntityArmoredMobSC.isWithinHomeDistance), set again on every load (EntityJoinWorldEvent, Forge's
 * bus). The Warlord also carries its safe (ItemRelicSC.WARLORD_SAFE_NBT) for its key. Server thread only; nothing kept in
 * memory between ticks.
 */
public final class RuinWorldSC {

    public static final RuinWorldSC INSTANCE = new RuinWorldSC();
    /** ПЗ-6: a camp comes back 3 game days after its Warlord died; ПХ-4: a Sentinel after 7 (TODO(balance)); never with a player this near. */
    public static final long CAMP_RESTORE_TICKS = 3L * 24000L, SENTINEL_RESPAWN_TICKS = 7L * 24000L;
    public static final int RESPAWN_CLEAR = 48, CHECK_TICKS = 200;
    /** The residents' entity NBT (getEntityData): their home {x, y, z, r, l} - the structure's middle, half size, vanilla's leash. */
    public static final String HOME_NBT = "scHome";
    /** ПХ-3: how far past its bunker a Sentinel may go. TODO(balance) */
    public static final int SENTINEL_LEASH = 24;
    /** How far past its camp's box the Warlord may go: vanilla's EntityAITarget keeps to the home area, so a shorter leash would let it
     *  ignore a player hitting it from just outside the camp. TODO(balance) */
    public static final int WARLORD_LEASH = 16;
    /**
     * How far over its floor a camp's home area reaches (HOME_NBT "u"): past the canyon's rim, so the camp answers a player
     * shooting down from it (the area a column - the walls keep the residents on the floor anyway).
     */
    public static final int CAMP_RIM = SubstrateTerrainSC.CANYON_DEPTH_MAX + 8;
    /** The records' extra keys (C's). */
    public static final String X_MID = "Mid", X_SAFE = "Safe", X_FIELD = "Field", X_PANELS = "Panels", X_PAD = "Pad", X_BOX = "Box",
            X_HOME = "Home", X_MAR = "Mar", X_EXE = "Exe", X_SPAWN = "Spawn", X_OPENED = "Opened", X_RESTORED = "Restored";
    /** Tries for a free spot of one resident on a return. */
    private static final int SPOT_TRIES = 32;
    /** Round its last place this much loaded too for a miss (it may have chased someone out of its area). */
    private static final int LOST_MARGIN = 16;
    private static final String CAMP_PREFIX = "camp:", VAULT_PREFIX = "vault:";

    private RuinWorldSC() {
    }

    /** Server stopped: what C keeps in memory (nothing - every state is in RareRegistrySC and the entities' NBT). */
    public static void clearAll() {
    }

    // ================================================================== the events

    /** The Substrate's world tick: every CHECK_TICKS, the records (tick). */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        World w = e.world;
        if (e.phase != TickEvent.Phase.END || !(w instanceof WorldServer) || !SubstrateSC.is(w)
                || (w.getTotalWorldTime() + 61L) % CHECK_TICKS != 0) {
            return;
        }
        tick((WorldServer) w, w.getTotalWorldTime());
    }

    /** A resident loaded (or spawned): its home again (vanilla's home area isn't saved). */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world == null || e.world.isRemote || !(e.entity instanceof EntityCreature) || !SubstrateSC.is(e.world)) {
            return;
        }
        NBTTagCompound h = e.entity.getEntityData().getCompoundTag(HOME_NBT);
        if (h.hasKey("x")) {
            ((EntityCreature) e.entity).setHomeArea(h.getInteger("x"), h.getInteger("y"), h.getInteger("z"),
                    h.hasKey("l") ? h.getInteger("l") : h.getInteger("r"));
        }
    }

    // ================================================================== the slow tick

    /**
     * Every camp's and Vault's record: the «lost» check of a living one, the return of a dead one. Server, the Substrate.
     * @return how many came back (the world test)
     */
    public static int tick(WorldServer w, long now) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null || reg.all().isEmpty()) {
            return 0;
        }
        Map<UUID, EntityLiving> rare = null;
        int back = 0;
        for (RareRegistrySC.Rec r : new ArrayList<RareRegistrySC.Rec>(reg.all())) {
            boolean camp = r.key.startsWith(CAMP_PREFIX), vault = r.key.startsWith(VAULT_PREFIX);
            if (!camp && !vault) {
                continue;                                       // «Затмение» is package M's
            }
            if (r.state == RareRegistrySC.ALIVE) {
                if (rare == null) {
                    rare = loadedRare(w);
                }
                lost(w, reg, r, rare, now);
            } else if (camp ? restoreCamp(w, reg, r, now) : respawnSentinel(w, reg, r, now)) {
                back++;
            }
        }
        return back;
    }

    /** The loaded living rare ones (with a registry key) by UUID. */
    private static Map<UUID, EntityLiving> loadedRare(World w) {
        Map<UUID, EntityLiving> out = new HashMap<UUID, EntityLiving>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityLiving && ((EntityLiving) o).isEntityAlive() && RareRegistrySC.keyOf((Entity) o) != null) {
                out.put(((EntityLiving) o).getUniqueID(), (EntityLiving) o);
            }
        }
        return out;
    }

    /** The «lost» check of one living record: seen, or one more look at its loaded area (areaLoaded) without it. */
    private static void lost(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, Map<UUID, EntityLiving> rare, long now) {
        EntityLiving e = rare.get(r.uuid());
        if (e != null && r.key.equals(RareRegistrySC.keyOf(e))) {
            reg.seen(r.key, e, now);
            return;
        }
        if (areaLoaded(w, r, true) && reg.missed(r.key, now)) {
            FMLLog.info("[Silicon Age] Si-14 Substrate: %s is gone without dying (its place loaded, nobody there) - counted dead", r.key);
        }
    }

    /** Pure: a record may come back now - its state and cooldown (RareRegistrySC.mayRespawn), its place loaded, no player near. */
    public static boolean mayReturn(int state, long diedAt, long now, long cooldown, boolean loaded, boolean playerNear) {
        return state != RareRegistrySC.ALIVE && RareRegistrySC.mayRespawn(state, diedAt, now, cooldown) && loaded && !playerNear;
    }

    // ================================================================== the camps (ПЗ-6)

    /** The generator's camp tick (TileEntityStolenFieldSC.campTick): its place into the record, the camp's return check. */
    public static void campTick(TileEntityStolenFieldSC f) {
        World w = f.getWorldObj();
        if (!(w instanceof WorldServer) || !SubstrateSC.is(w)) {
            return;
        }
        RareRegistrySC reg = RareRegistrySC.get(w);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(f.campKey());
        if (r == null || !r.extra.hasKey(X_MID)) {
            return;                                             // not a camp's (set by hand), or its residents not placed yet
        }
        int[] at = {f.xCoord, f.yCoord, f.zCoord};
        if (!Arrays.equals(r.extra.getIntArray(X_FIELD), at)) {
            r.extra.setIntArray(X_FIELD, at);
            reg.markDirty();
        }
        if (r.state != RareRegistrySC.ALIVE) {
            restoreCamp((WorldServer) w, reg, r, w.getTotalWorldTime());
        }
    }

    /** ComponentCampGroundSC, placing the residents: the camp's record (made NONE if new) keeps its spots. */
    public static void campMade(World w, String key, int[] mid, int[] safe, int[] field, int[][] panels, int[] pad, int[] box, int homeR,
                                int marauders, boolean executioner) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null) {
            return;
        }
        NBTTagCompound x = reg.rec(key).extra;
        x.setIntArray(X_MID, mid);
        x.setIntArray(X_SAFE, safe);
        x.setIntArray(X_FIELD, field);
        int[] p = new int[panels.length * 3];
        for (int i = 0; i < panels.length; i++) {
            System.arraycopy(panels[i], 0, p, i * 3, 3);
        }
        x.setIntArray(X_PANELS, p);
        if (pad != null) {
            x.setIntArray(X_PAD, pad);
        } else {
            x.removeTag(X_PAD);
        }
        x.setIntArray(X_BOX, box);
        x.setInteger(X_HOME, homeR);
        x.setInteger(X_MAR, marauders);
        x.setBoolean(X_EXE, executioner);
        reg.markDirty();
    }

    /** BlockCampSC: the safe at (x, y, z) was opened now (its camp's record notes when - the world test, the handbook). */
    public static void safeOpened(World w, int x, int y, int z) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null) {
            return;
        }
        int[] at = {x, y, z};
        for (RareRegistrySC.Rec r : reg.all()) {
            if (r.key.startsWith(CAMP_PREFIX) && Arrays.equals(r.extra.getIntArray(X_SAFE), at)) {
                r.extra.setLong(X_OPENED, w.getTotalWorldTime());
                reg.markDirty();
                return;
            }
        }
    }

    /**
     * ПЗ-6: camp `r` comes back if it may now (its Warlord dead CAMP_RESTORE_TICKS, its middle loaded, no player within
     * RESPAWN_CLEAR). @return it came back
     */
    public static boolean restoreCamp(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, long now) {
        NBTTagCompound x = r.extra;
        int[] mid = x.getIntArray(X_MID);
        if (mid.length != 3 || !mayReturn(r.state, r.diedAt, now, CAMP_RESTORE_TICKS, areaLoaded(w, r, false),
                w.getClosestPlayer(mid[0] + 0.5D, mid[1], mid[2] + 0.5D, RESPAWN_CLEAR) != null)) {
            return false;
        }
        int[] home = {mid[0], mid[1], mid[2], Math.max(1, x.getInteger(X_HOME))};
        int[] box = x.getIntArray(X_BOX);
        if (box.length != 4) {
            box = new int[]{mid[0] - home[3], mid[2] - home[3], mid[0] + home[3], mid[2] + home[3]};
        }
        int[] safe = x.getIntArray(X_SAFE);
        // the Warlord first: a lost one about again is the camp's; a new one needs room (none: nothing changes, next time)
        int marauders = 0, executioners = 0;
        EntityLiving boss = null;
        for (Object o : w.loadedEntityList) {
            if (!(o instanceof EntityLiving) || !((EntityLiving) o).isEntityAlive()) {
                continue;
            }
            EntityLiving e = (EntityLiving) o;
            if (e instanceof EntityWarlordSC && r.key.equals(RareRegistrySC.keyOf(e))) {
                boss = e;
            } else if (homeIs(e, mid)) {
                if (e instanceof EntityMarauderSC) {
                    marauders++;
                } else if (e instanceof EntityExecutionerSC) {
                    executioners++;
                }
            }
        }
        Random rand = w.rand;
        if (boss != null) {
            reg.alive(r.key, boss, now);
        } else {
            int[] spot = freeSpot(w, box, mid[1], rand);
            boss = spot == null ? null : warlord(w, r.key, spot[0], spot[1], spot[2], home, safe.length == 3 ? safe : null);
            if (boss == null) {
                return false;
            }
        }
        // the blocks: the safe relocked; the generator, the panels, the pad only on air
        if (safe.length == 3 && w.blockExists(safe[0], safe[1], safe[2])) {
            Block b = w.getBlock(safe[0], safe[1], safe[2]);
            if (b == CampBlocksSC.camp && w.getBlockMetadata(safe[0], safe[1], safe[2]) == CampBlocksSC.CAMP_SAFE_OPEN
                    || b.isAir(w, safe[0], safe[1], safe[2])) {
                w.setBlock(safe[0], safe[1], safe[2], CampBlocksSC.camp, CampBlocksSC.CAMP_SAFE, 2);
            }
        }
        int[] field = x.getIntArray(X_FIELD);
        if (field.length == 3 && emptyLoaded(w, field) && CampBlocksSC.stolenField != null) {
            w.setBlock(field[0], field[1], field[2], CampBlocksSC.stolenField, 0, 2);
            TileEntity te = w.getTileEntity(field[0], field[1], field[2]);
            if (te instanceof TileEntityStolenFieldSC) {
                TileEntityStolenFieldSC g = (TileEntityStolenFieldSC) te;
                g.setup(CampLayoutSC.FIELD_RADIUS, r.key);
                g.feed(g.getMaxEnergyStored());
            }
        }
        int[] panels = x.getIntArray(X_PANELS);
        for (int i = 0; i + 2 < panels.length; i += 3) {
            int[] p = {panels[i], panels[i + 1], panels[i + 2]};
            if (emptyLoaded(w, p)) {
                w.setBlock(p[0], p[1], p[2], CampBlocksSC.camp, CampBlocksSC.STOLEN_SOLAR, 2);
                TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileEntityStolenSolarSC && field.length == 3) {
                    ((TileEntityStolenSolarSC) te).bind(field[0], field[1], field[2]);
                }
            }
        }
        int[] pad = x.getIntArray(X_PAD);
        if (pad.length == 3 && emptyLoaded(w, pad)) {
            w.setBlock(pad[0], pad[1], pad[2], CampBlocksSC.camp, CampBlocksSC.STOLEN_PAD, 2);
        }
        // the residents up to the camp's count
        for (int m = marauders; m < x.getInteger(X_MAR); m++) {
            int[] spot = freeSpot(w, box, mid[1], rand);
            if (spot != null) {
                resident(w, MobEntitiesSC.ID_MARAUDER, spot[0], spot[1], spot[2], home);
            }
        }
        if (x.getBoolean(X_EXE) && executioners == 0) {
            int[] spot = pad.length == 3 ? freeSpot(w, new int[]{pad[0] - 3, pad[2] - 3, pad[0] + 3, pad[2] + 3}, mid[1], rand) : null;
            if (spot == null) {
                spot = freeSpot(w, box, mid[1], rand);
            }
            if (spot != null) {
                resident(w, MobEntitiesSC.ID_EXECUTIONER, spot[0], spot[1], spot[2], home);
            }
        }
        x.setLong(X_RESTORED, now);
        reg.markDirty();
        FMLLog.info("[Silicon Age] Si-14 Substrate: the Renegades' camp at %d %d is back (%s)", mid[0], mid[2], r.key);
        return true;
    }

    /** The Warlord of camp `key` at (x, y, z): settled at `home`, its safe in its NBT (for its key), ALIVE in the registry. */
    public static EntityLiving warlord(WorldServer w, String key, int x, int y, int z, int[] home, int[] safe) {
        EntityLiving e = SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_WARLORD, x + 0.5D, y, z + 0.5D, false, false);
        if (e == null) {
            return null;
        }
        settle(e, home, home[3] + WARLORD_LEASH, CAMP_RIM);
        if (safe != null) {
            e.getEntityData().setIntArray(ItemRelicSC.WARLORD_SAFE_NBT, safe);
        }
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg != null) {
            reg.alive(key, e, w.getTotalWorldTime());
        }
        return e;
    }

    /**
     * One of a camp's residents (pool id `kind`: a Marauder, the Executioner) at (x, y, z), the elite roll as anywhere; its leash
     * and its column the Warlord's (a shorter one: vanilla's EntityAITarget would let it ignore a player hitting it from outside).
     */
    public static EntityLiving resident(WorldServer w, int kind, int x, int y, int z, int[] home) {
        EntityLiving e = SpawnDirectorSC.spawnAt(w, kind, x + 0.5D, y, z + 0.5D, true, false);
        if (e != null) {
            settle(e, home, home[3] + WARLORD_LEASH, CAMP_RIM);
        }
        return e;
    }

    // ================================================================== the Vaults (ПХ-3, ПХ-4)

    /** ComponentVaultBunkerSC, placing the Sentinel: the Vault's record (made NONE if new) keeps its middle and the spot. */
    public static void vaultMade(World w, String key, int[] mid, int[] spawn, int homeR) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null) {
            return;
        }
        NBTTagCompound x = reg.rec(key).extra;
        x.setIntArray(X_MID, mid);
        if (spawn != null) {
            x.setIntArray(X_SPAWN, spawn);
        }
        x.setInteger(X_HOME, homeR);
        reg.markDirty();
    }

    /** The Sentinel of Vault `key` at (x, y, z): settled (its leash SENTINEL_LEASH past the bunker), ALIVE in the registry. */
    public static EntityLiving sentinel(WorldServer w, String key, int x, int y, int z, int[] home) {
        EntityLiving e = SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_SENTINEL, x + 0.5D, y, z + 0.5D, false, false);
        if (e == null) {
            return null;
        }
        settle(e, home, home[3] + SENTINEL_LEASH);
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg != null) {
            reg.alive(key, e, w.getTotalWorldTime());
        }
        return e;
    }

    /** ПХ-4: Vault `r`'s Sentinel comes back if it may now. @return it came back */
    public static boolean respawnSentinel(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, long now) {
        NBTTagCompound x = r.extra;
        int[] mid = x.getIntArray(X_MID);
        if (mid.length != 3 || !mayReturn(r.state, r.diedAt, now, SENTINEL_RESPAWN_TICKS, areaLoaded(w, r, false),
                w.getClosestPlayer(mid[0] + 0.5D, mid[1], mid[2] + 0.5D, RESPAWN_CLEAR) != null)) {
            return false;
        }
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntitySentinelSC && ((EntitySentinelSC) o).isEntityAlive() && r.key.equals(RareRegistrySC.keyOf((Entity) o))) {
                reg.alive(r.key, (EntitySentinelSC) o, now);   // a lost one about again
                return false;
            }
        }
        List<int[]> spots = new ArrayList<int[]>();
        int[] spawn = x.getIntArray(X_SPAWN);
        if (spawn.length == 3) {
            spots.add(spawn);
        }
        for (int[] d : ComponentVaultBunkerSC.SENTINEL_AT) {
            spots.add(new int[]{mid[0] + d[0], mid[1], mid[2] + d[1]});
        }
        int[] home = {mid[0], mid[1], mid[2], Math.max(1, x.getInteger(X_HOME))};
        for (int[] s : spots) {
            if (free(w, s[0], s[1], s[2]) && w.isAirBlock(s[0], s[1] + 2, s[2]) && sentinel(w, r.key, s[0], s[1], s[2], home) != null) {
                FMLLog.info("[Silicon Age] Si-14 Substrate: the Vault's Sentinel at %d %d is back (%s)", mid[0], mid[2], r.key);
                return true;
            }
        }
        return false;
    }

    // ================================================================== helpers

    /** A resident: persistent, vanilla's home area (`leash`), its home in its NBT (HOME_NBT - read again on every load). */
    public static void settle(EntityLiving e, int[] home, int leash) {
        settle(e, home, leash, 0);
    }

    /** The same; `up` > 0 - its home area a column up to `up` over the home (HOME_NBT "u": a camp's). */
    public static void settle(EntityLiving e, int[] home, int leash, int up) {
        e.func_110163_bv();                                     // persistent: never despawns
        NBTTagCompound h = new NBTTagCompound();
        h.setInteger("x", home[0]);
        h.setInteger("y", home[1]);
        h.setInteger("z", home[2]);
        h.setInteger("r", home[3]);
        h.setInteger("l", leash);
        if (up > 0) {
            h.setInteger("u", up);
        }
        e.getEntityData().setTag(HOME_NBT, h);
        if (e instanceof EntityCreature) {
            ((EntityCreature) e).setHomeArea(home[0], home[1], home[2], leash);
        }
    }

    /**
     * All rare one `r` (and a camp's residents) may be in is loaded: its structure's middle with the half size and the leash
     * (WARLORD_LEASH, SENTINEL_LEASH) round it, and - `last` (the «lost» check) - LOST_MARGIN round its last place.
     */
    private static boolean areaLoaded(World w, RareRegistrySC.Rec r, boolean last) {
        int[] mid = r.extra.getIntArray(X_MID);
        int reach = Math.max(1, r.extra.getInteger(X_HOME)) + (r.key.startsWith(CAMP_PREFIX) ? WARLORD_LEASH : SENTINEL_LEASH);
        if (mid.length == 3 && !w.checkChunksExist(mid[0] - reach, 0, mid[2] - reach, mid[0] + reach, 0, mid[2] + reach)) {
            return false;
        }
        int d = mid.length == 3 ? LOST_MARGIN : reach;
        return !last && mid.length == 3 || w.checkChunksExist(r.x - d, 0, r.z - d, r.x + d, 0, r.z + d);
    }

    /** `e`'s home (HOME_NBT) is the structure whose middle is `mid` (its column). */
    public static boolean homeIs(Entity e, int[] mid) {
        NBTTagCompound h = e.getEntityData().getCompoundTag(HOME_NBT);
        return h.hasKey("x") && h.getInteger("x") == mid[0] && h.getInteger("z") == mid[2];
    }

    /** The block at `p` is loaded and air (a missing camp block may go back there). */
    private static boolean emptyLoaded(World w, int[] p) {
        return w.blockExists(p[0], p[1], p[2]) && w.isAirBlock(p[0], p[1], p[2]);
    }

    /** Loaded, two blocks of air over a solid one. */
    private static boolean free(World w, int x, int y, int z) {
        return y > 1 && y < 254 && w.blockExists(x, y, z) && w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z)
                && w.getBlock(x, y - 1, z).getMaterial().isSolid();
    }

    /** A free spot on the floor (Y `y`) inside `box` {minX, minZ, maxX, maxZ} - its loaded chunks only - or null. */
    private static int[] freeSpot(World w, int[] box, int y, Random rand) {
        int sx = Math.max(1, box[2] - box[0] - 3), sz = Math.max(1, box[3] - box[1] - 3);
        for (int t = 0; t < SPOT_TRIES; t++) {
            int x = box[0] + 2 + rand.nextInt(sx), z = box[1] + 2 + rand.nextInt(sz);
            if (free(w, x, y, z)) {
                return new int[]{x, y, z};
            }
        }
        return null;
    }
}
