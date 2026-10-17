package com.sc.mob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sc.dim.SubstrateSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, rules ПС-39, ПТ-19; proposal §4 «Смерть: ... Выпавшие вещи подбирает
 * Утилизатор. Он хранит их в себе и не исчезает, пока не отдаст: вещи нужно отбить»): the Scrapper's errands. Registered by SCMod
 * on Forge's bus (PlayerDropsEvent: a player died in the Substrate - every EntityItem of the death tagged DROP_NBT = the owner's
 * UUID, DROP_AT = the time; nothing else changes - the Death Mark is package B's of Ф2) and FML's (the Substrate's world tick,
 * every CHECK_TICKS: tagged items older than ARRIVE_TICKS whose chunk is loaded and that no Scrapper serves yet - a Scrapper
 * SPAWN_MIN..MAX blocks off, born in Si-14, its home the death's place, outside the caps; it walks there and swallows them -
 * BAG_STACKS at most -, never despawns while it holds anything, goes back to its home within HOME_LEASH). The owner coming within
 * TELL_RANGE hears «Утилизатор подобрал ваши вещи» once. Killed (by anyone, anything), it drops its whole bag (ПТ-19) plus 2..6
 * crushed ores (ПТ-1 for those). A restart loses nothing: the tags and the bag are NBT. Numbers fixed (the coordinator's;
 * TODO(balance)).
 * <p>
 * The tags live in the EntityItem's Forge data (saved with it). Tagged only when the drops really fall (LOWEST priority, a
 * cancelled event - a grave mod took them - is left alone); FakePlayers too (the world test). The check (check - public for the
 * world test) runs over loadedEntityList only: an item in an unloaded chunk waits (it doesn't age either) - when the owner comes
 * back by the Death Mark, the chunk loads and the Scrapper comes then. «Served»: a living Scrapper with room in its bag on an
 * errand whose home lies within HOME_LEASH of the item, or any with room within EntityScrapperSC.COLLECT_RADIUS + 2 of it. An
 * idle Scrapper with room within HOME_LEASH takes the errand instead of a new one coming. Never for a thing inside a settlement's
 * dome (an IMob can't go there), never on Peaceful, at most MAX_PER_CHECK new Scrappers a check. The spot: SPOT_TRIES random
 * columns SPAWN_MIN..SPAWN_MAX off whose chunk is loaded (never loads one), the first place to stand within SPOT_DY of the
 * item's height, not within SPOT_MARGIN past a dome; SpawnDirectorSC.spawnAt does the rest (room, other mods' CheckSpawn, «born
 * in Si-14»). An errand ends (EntityScrapperSC.endErrand) when nothing tagged is left within HOME_LEASH of its home.
 */
public final class ScrapperSC {

    public static final ScrapperSC INSTANCE = new ScrapperSC();
    /** The death drops' entity NBT: the owner's UUID (string), the time of the death. */
    public static final String DROP_NBT = "scDeathDrop", DROP_AT = "scDeathAt";
    /** ПС-39: it comes 60 s after the death, from 16..24 blocks off; its bag; its leash; the owner's line. */
    public static final int ARRIVE_TICKS = 1200, SPAWN_MIN = 16, SPAWN_MAX = 24, BAG_STACKS = 27, HOME_LEASH = 32, TELL_RANGE = 32;
    public static final int CHECK_TICKS = 100;
    /** ПТ-19: the crushed ores on top of the bag. */
    public static final int ORES_MIN = 2, ORES_MAX = 6;
    /** The spot's search: columns tried, how far up or down from the item's height, the margin past a dome; new Scrappers a check. */
    public static final int SPOT_TRIES = 12, SPOT_DY = 8, SPOT_MARGIN = 4, MAX_PER_CHECK = 3;

    /** The deaths whose owner heard the line already: "uuid@time" (one line for one death, however many Scrappers took it). */
    private static final Set<String> TOLD = new HashSet<String>();

    private ScrapperSC() {
    }

    /** Server stopped: what N keeps in memory (the owners told). */
    public static void clearAll() {
        synchronized (TOLD) {
            TOLD.clear();
        }
    }

    // ------------------------------------------------------------------ the tags

    /** A player died in the Substrate: their things (as they fall) get the owner and the time. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDrops(PlayerDropsEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p == null || p.worldObj == null || p.worldObj.isRemote || !SubstrateSC.is(p.worldObj) || e.drops == null) {
            return;
        }
        String owner = p.getUniqueID().toString();
        long now = p.worldObj.getTotalWorldTime();
        for (EntityItem item : e.drops) {
            tag(item, owner, now);
        }
    }

    /** `item` is a dead player's thing: `owner` (UUID string), dead at world time `at`. Public for the world test. */
    public static void tag(EntityItem item, String owner, long at) {
        if (item == null || owner == null) {
            return;
        }
        NBTTagCompound data = item.getEntityData();
        data.setString(DROP_NBT, owner);
        data.setLong(DROP_AT, at);
    }

    /** `item` is a tagged thing of a death. */
    public static boolean tagged(EntityItem item) {
        return item != null && item.getEntityData().hasKey(DROP_NBT);
    }

    /** A UUID out of its string, null for a broken one. */
    public static UUID uuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the errands

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)) {
            return;
        }
        if (e.world.getTotalWorldTime() % CHECK_TICKS == 0) {
            check((WorldServer) e.world);
        }
    }

    /** One check of `w` (the world tick every CHECK_TICKS; the world test). @return how many Scrappers it sent (new ones) */
    public static int check(WorldServer w) {
        long now = w.getTotalWorldTime();
        List<EntityItem> tagged = new ArrayList<EntityItem>();
        List<EntityScrapperSC> scrappers = new ArrayList<EntityScrapperSC>();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityItem) {
                EntityItem item = (EntityItem) o;
                if (!item.isDead && tagged(item)) {
                    tagged.add(item);
                }
            } else if (o instanceof EntityScrapperSC && ((EntityScrapperSC) o).isEntityAlive()) {
                scrappers.add((EntityScrapperSC) o);
            }
        }
        for (EntityScrapperSC s : scrappers) {
            if (s.onErrand() && !anyNear(tagged, s.homeX() + 0.5D, s.homeZ() + 0.5D, HOME_LEASH)) {
                s.endErrand();                                  // nothing of a death left near its home: it lives on as any other
            }
        }
        if (w.difficultySetting == EnumDifficulty.PEACEFUL) {
            return 0;
        }
        int sent = 0;
        for (EntityItem item : tagged) {
            long at = item.getEntityData().getLong(DROP_AT);
            if (now - at < ARRIVE_TICKS && now >= at || served(item, scrappers) || inDome(w, item.posX, item.posZ, 0)) {
                continue;
            }
            EntityScrapperSC idle = idleNear(item, scrappers);
            if (idle != null) {
                idle.errand(MathHelper.floor_double(item.posX), MathHelper.floor_double(item.posY), MathHelper.floor_double(item.posZ));
                continue;
            }
            if (sent >= MAX_PER_CHECK) {
                continue;
            }
            EntityScrapperSC s = send(w, item);
            if (s != null) {
                scrappers.add(s);
                sent++;
            }
        }
        return sent;
    }

    /** Some tagged thing lies within `r` (horizontally) of (x, z). */
    private static boolean anyNear(List<EntityItem> items, double x, double z, double r) {
        for (EntityItem i : items) {
            if (!i.isDead && sq(i.posX - x, i.posZ - z) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /** A Scrapper with room is already on it (an errand home within the leash, or right next to it). */
    private static boolean served(EntityItem item, List<EntityScrapperSC> scrappers) {
        double near = EntityScrapperSC.COLLECT_RADIUS + 2D;
        for (EntityScrapperSC s : scrappers) {
            if (!s.isEntityAlive() || s.bagFull()) {
                continue;
            }
            if (s.onErrand() && s.hasOwnHome() && sq(s.homeX() + 0.5D - item.posX, s.homeZ() + 0.5D - item.posZ) <= HOME_LEASH * HOME_LEASH) {
                return true;
            }
            if (s.getDistanceSqToEntity(item) <= near * near) {
                return true;
            }
        }
        return false;
    }

    /** A Scrapper with room and no errand within the leash of `item`, or null. */
    private static EntityScrapperSC idleNear(EntityItem item, List<EntityScrapperSC> scrappers) {
        for (EntityScrapperSC s : scrappers) {
            if (s.isEntityAlive() && !s.onErrand() && !s.bagFull() && s.getDistanceSqToEntity(item) <= HOME_LEASH * HOME_LEASH) {
                return s;
            }
        }
        return null;
    }

    /** (x, z) is inside a settlement's dome, or within `margin` past its edge. */
    private static boolean inDome(World w, double x, double z, int margin) {
        SettlementRegistrySC r = SettlementSC.registry(w);
        return r != null && r.within(w.provider.dimensionId, x, z, margin) != null;
    }

    /** A new Scrapper SPAWN_MIN..SPAWN_MAX off `item`, its home the death's place; null - no spot, refused. */
    private static EntityScrapperSC send(WorldServer w, EntityItem item) {
        int y0 = MathHelper.floor_double(item.posY);
        for (int i = 0; i < SPOT_TRIES; i++) {
            double a = w.rand.nextDouble() * Math.PI * 2D, d = SPAWN_MIN + w.rand.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
            int x = MathHelper.floor_double(item.posX + Math.cos(a) * d), z = MathHelper.floor_double(item.posZ + Math.sin(a) * d);
            if (!w.blockExists(x, 64, z) || inDome(w, x + 0.5D, z + 0.5D, SPOT_MARGIN)) {
                continue;                                       // never loads a chunk; never by a dome
            }
            int y = standY(w, x, y0, z);
            if (y < 0) {
                continue;
            }
            EntityLiving e = SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_SCRAPPER, x + 0.5D, y, z + 0.5D, false, false);
            if (e instanceof EntityScrapperSC) {
                EntityScrapperSC s = (EntityScrapperSC) e;
                s.errand(MathHelper.floor_double(item.posX), y0, MathHelper.floor_double(item.posZ));
                return s;
            }
        }
        return null;
    }

    /** The place to stand in column (x, z) nearest `y0` within SPOT_DY (solid under, two free blocks, no liquid), -1 none. */
    private static int standY(World w, int x, int y0, int z) {
        for (int dy = 0; dy <= SPOT_DY; dy++) {
            for (int s = 0; s < 2; s++) {
                int y = s == 0 ? y0 + dy : y0 - dy;
                if (s == 1 && dy == 0 || y < 2 || y > w.getHeight() - 3) {
                    continue;
                }
                Block under = w.getBlock(x, y - 1, z);
                if (under.getMaterial().isSolid() && !under.getMaterial().isLiquid() && free(w, x, y, z) && free(w, x, y + 1, z)) {
                    return y;
                }
            }
        }
        return -1;
    }

    private static boolean free(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return !b.getMaterial().blocksMovement() && !b.getMaterial().isLiquid();
    }

    private static double sq(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------------ the owner's line

    /**
     * Server (EntityScrapperSC, its owner within TELL_RANGE): «Утилизатор подобрал ваши вещи» to `p` once for the death at
     * `deathAt` (however many Scrappers took its things). A FakePlayer is never sent anything. @return it was said now
     */
    public static boolean tell(EntityPlayer p, long deathAt) {
        if (p == null) {
            return false;
        }
        synchronized (TOLD) {
            if (!TOLD.add(p.getUniqueID().toString() + "@" + deathAt)) {
                return false;
            }
        }
        if (p instanceof FakePlayer || p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler == null) {
            return true;
        }
        ChatComponentTranslation msg = new ChatComponentTranslation("sc.scrapper.took");
        msg.getChatStyle().setColor(EnumChatFormatting.GOLD);
        p.addChatComponentMessage(msg);
        return true;
    }
}
