package com.sc.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityContainmentCoilSC;
import com.sc.mob.WardenRulesSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.tileentity.TileEntityBreakdownConsoleSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.4-§4.6, rules ПС-56, ПУ-11): the arenas' slow life and the fight's bookkeeping on
 * the arena's side. Registered by the coordinator on FML's bus (the world tick) and Forge's (A may add ChunkEvent.Load etc.).
 * <ul>
 * <li>the Console's click (TileEntityBreakdownConsoleSC.click calls start): Peaceful - refused (sc.arena.peaceful: a boss
 * EntityMob would vanish with the key); a fight on, or a countdown running - sc.arena.busy; no key in the Console - a Breakdown
 * Key in hand goes in (keyIn; spent from the hand but in creative), else sc.arena.nokey; then a COUNTDOWN_TICKS countdown in the
 * chat to the players within TELL_RADIUS, then rise: the record FIGHT, a new fightId (the world's time), fightStartedAt /
 * lastPlayerAt now, maxPlayers the fighters within SCALE_RADIUS, EntityBreakdownWardenSC.summon (package B; null - the record
 * KEYED, the key still in, sc.arena.blocked);</li>
 * <li>the reset (ПС-56): every CHECK_TICKS for each FIGHT record whose Console chunk is loaded - WardenRulesSC.resetDue(lastPlayerAt,
 * now) (lastPlayerAt is B's while the Warden ticks; an unloaded arena's stays old - so a world loaded hours later resets at once,
 * the Console's first tick asks the same - resetIfDue): the Warden and its coils removed if loaded, the cracks closed (takeCracks -
 * the floor's plating back where a crack still is), the record KEYED (the key stays), sc.arena.reset to the players near; a FIGHT
 * record whose Warden is missing (Peaceful, killed by a command) LOST_CHECKS checks in a row with the arena's box loaded - the
 * same reset;</li>
 * <li>bossDied (B's onDeath): the record READY, keyIn false (the key spent), the cracks closed, sc.arena.won;</li>
 * <li>fightCurrent (B's Warden and coils on load / every 100 ticks): their fight is the record's current one - else they leave
 * the world (setDead, no drops).</li>
 * </ul>
 * A crack in a chunk unloaded at the end stays in the record and is closed by a later pass, once its chunk is loaded. The countdowns
 * and the missing counts live in memory only (a restart forgets a countdown - the key stays in the Console, a click starts it
 * again). FakePlayers: the Console never answers one (TileEntityBreakdownConsoleSC.click); start itself takes one (the world test)
 * but never tells it anything, and no FakePlayer is ever counted as a fighter. Server thread only.
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE, the static API's signatures. The bodies are A's (and the helpers below).
 */
public final class ArenaWorldSC {

    public static final ArenaWorldSC INSTANCE = new ArenaWorldSC();
    /** A FIGHT record with its Warden missing this many checks (the chunk loaded) resets. TODO(balance) */
    public static final int LOST_CHECKS = 10;
    /** The Console's countdown before the Warden rises (ticks). */
    public static final int COUNTDOWN_TICKS = 100;
    /** The slow pass over the records (ticks): the resets, the missing Wardens, the cracks left over. */
    public static final int CHECK_TICKS = 20;
    /** Who hears the arena: the countdown and the end - round its middle; the reset - whoever is in sight of it. TODO(balance) */
    public static final int TELL_RADIUS = WardenRulesSC.SCALE_RADIUS + 16, RESET_TELL_RADIUS = 160;
    /** A coil this far past the arena's box is still the arena's (the reset removes it). */
    private static final int BOX_MARGIN = 8;

    /** The countdowns running (the region's key -> it). */
    private static final Map<Long, Countdown> COUNTDOWNS = new HashMap<Long, Countdown>();
    /** FIGHT records whose Warden was missing at the last checks (the region's key -> checks in a row). */
    private static final Map<Long, Integer> LOST = new HashMap<Long, Integer>();

    private ArenaWorldSC() {
    }

    /** Server stopped: nothing of that server's arenas kept in memory (the countdowns, the lost counts). */
    public static void clearAll() {
        COUNTDOWNS.clear();
        LOST.clear();
    }

    // ================================================================== the Console

    /**
     * The Console at (x, y, z) of arena `a` clicked by `p` (a Breakdown Key in his hand, or one in the Console already): the
     * refusals, the key, the countdown. @return the click was used (the block's onBlockActivated returns it)
     */
    public static boolean start(WorldServer w, ArenaDataSC.Arena a, EntityPlayer p, int x, int y, int z) {
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d == null || a == null || p == null) {
            return false;
        }
        if (w.difficultySetting == EnumDifficulty.PEACEFUL) {
            tell(p, EnumChatFormatting.RED, "sc.arena.peaceful");
            return true;
        }
        if (a.state == ArenaDataSC.FIGHT || counting(a)) {
            tell(p, EnumChatFormatting.YELLOW, "sc.arena.busy");
            return true;
        }
        if (a.state == ArenaDataSC.EMPTY) {
            tell(p, EnumChatFormatting.GRAY, "sc.arena.noarena");
            return true;
        }
        if (!a.keyIn) {
            ItemStack held = p.getCurrentEquippedItem();
            if (!isKey(held)) {
                tell(p, EnumChatFormatting.GRAY, "sc.arena.nokey", String.valueOf(ItemRelicSC.FRAGMENTS));
                return true;
            }
            if (!p.capabilities.isCreativeMode) {
                held.stackSize--;
                if (held.stackSize <= 0) {
                    p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
                }
                if (!(p instanceof FakePlayer)) {
                    p.inventoryContainer.detectAndSendChanges();
                }
            }
            a.keyIn = true;                                     // ПУ-11: the key lives in the record, not in the block
            d.markDirty();
            tell(p, EnumChatFormatting.LIGHT_PURPLE, "sc.arena.inserted");
            w.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.anvil_land", 0.4F, 1.6F);
            refreshConsole(w, a);
        }
        countdown(w, a, x, y, z);
        return true;
    }

    /** `s` is a Breakdown Key (ItemRelicSC meta BREAKDOWN_KEY). */
    public static boolean isKey(ItemStack s) {
        return s != null && s.stackSize > 0 && RelicItemsSC.relic != null && s.getItem() == RelicItemsSC.relic
                && s.getItemDamage() == ItemRelicSC.BREAKDOWN_KEY;
    }

    /** Arena `a`'s countdown is running. */
    public static boolean counting(ArenaDataSC.Arena a) {
        return a != null && COUNTDOWNS.containsKey(key(a));
    }

    /**
     * The countdown of arena `a` (its key in the Console, no fight, none running) from its Console at (x, y, z): COUNTDOWN_TICKS of
     * sc.arena.countdown lines, then rise. @return it began
     */
    public static boolean countdown(WorldServer w, ArenaDataSC.Arena a, int x, int y, int z) {
        if (w == null || a == null || a.state == ArenaDataSC.FIGHT || a.state == ArenaDataSC.EMPTY || !a.keyIn || counting(a)) {
            return false;
        }
        Countdown c = new Countdown(a.ri, a.rj, x, y, z, w.getTotalWorldTime());
        COUNTDOWNS.put(key(a), c);
        announce(w, a, c, COUNTDOWN_TICKS / 20);
        return true;
    }

    /**
     * The countdown's end: the fight of arena `a` begins - a new fightId, the record FIGHT, the fighters counted, the Warden
     * summoned (package B). Its summon failing - the record KEYED (the key still in), sc.arena.blocked. @return the Warden, or null
     */
    public static EntityBreakdownWardenSC rise(WorldServer w, ArenaDataSC.Arena a, long now) {
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d == null || a == null || a.state == ArenaDataSC.FIGHT || a.state == ArenaDataSC.EMPTY || !a.keyIn) {
            return null;
        }
        COUNTDOWNS.remove(key(a));
        closeCracks(w, a, d);                                   // any left from the last fight
        a.fightId = now > a.fightId ? now : a.fightId + 1;      // new: every Warden and coil of an older fight is stale now
        a.state = ArenaDataSC.FIGHT;
        a.fightStartedAt = now;
        a.lastPlayerAt = now;
        a.maxPlayers = Math.max(1, fighters(w, a));
        a.bossUuid = "";
        LOST.remove(key(a));
        d.markDirty();
        EntityBreakdownWardenSC boss;
        try {
            boss = EntityBreakdownWardenSC.summon(w, a, a.maxPlayers);
        } catch (RuntimeException ex) {
            FMLLog.severe("[Silicon Age] Si-14 Substrate: the Breakdown Warden of the arena at %d %d could not be summoned: %s", a.x, a.z, ex);
            boss = null;
        }
        if (boss == null) {
            a.state = ArenaDataSC.KEYED;
            d.markDirty();
            tellNear(w, a, TELL_RADIUS, EnumChatFormatting.RED, "sc.arena.blocked");
            refreshConsole(w, a);
            return null;
        }
        a.bossUuid = boss.getUniqueID().toString();
        d.markDirty();
        w.playSoundEffect(a.x + 0.5D, a.floorY + 2D, a.z + 0.5D, "ambient.weather.thunder", 2.0F, 1.4F);
        refreshConsole(w, a);
        return boss;
    }

    // ================================================================== the end of a fight

    /** B's Warden died in arena `a` (its onDeath, after its loot): the record READY, the key spent, the cracks closed. */
    public static void bossDied(WorldServer w, ArenaDataSC.Arena a, EntityBreakdownWardenSC boss) {
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d == null || a == null || a.state != ArenaDataSC.FIGHT) {
            return;
        }
        if (boss != null && a.bossUuid != null && a.bossUuid.length() > 0 && !a.bossUuid.equals(boss.getUniqueID().toString())) {
            return;                                             // a stale Warden's death ends nothing
        }
        a.state = ArenaDataSC.READY;
        a.keyIn = false;                                        // ПУ-11: the key is spent when the Warden falls
        a.bossUuid = "";
        closeCracks(w, a, d);
        LOST.remove(key(a));
        COUNTDOWNS.remove(key(a));
        d.markDirty();
        tellNear(w, a, TELL_RADIUS, EnumChatFormatting.GOLD, "sc.arena.won");
        refreshConsole(w, a);
    }

    /**
     * ПС-56: the fight of arena `a` resets - its Warden and coils leave the world (those loaded; the rest leave when loaded:
     * fightCurrent is false now), the cracks close, the record KEYED with the key still in the Console, sc.arena.reset near.
     */
    public static void reset(WorldServer w, ArenaDataSC.Arena a) {
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d == null || a == null) {
            return;
        }
        removeFight(w, a);
        a.state = ArenaDataSC.KEYED;
        a.keyIn = true;
        a.bossUuid = "";
        closeCracks(w, a, d);
        LOST.remove(key(a));
        COUNTDOWNS.remove(key(a));
        d.markDirty();
        tellNear(w, a, RESET_TELL_RADIUS, EnumChatFormatting.YELLOW, "sc.arena.reset");
        refreshConsole(w, a);
    }

    /** ПС-56 for the Console's first tick after a load: arena `a`'s fight is over by the time (everyone away too long) - reset. */
    public static boolean resetIfDue(WorldServer w, ArenaDataSC.Arena a) {
        if (w == null || a == null || a.state != ArenaDataSC.FIGHT || !WardenRulesSC.resetDue(a.lastPlayerAt, w.getTotalWorldTime())) {
            return false;
        }
        reset(w, a);
        return true;
    }

    /** The fight `fightId` of region (ri, rj) is still the arena's current one (B's Warden and coils ask; a client: true). */
    public static boolean fightCurrent(World w, int ri, int rj, long fightId) {
        if (w == null || w.isRemote) {
            return true;
        }
        ArenaDataSC d = ArenaDataSC.get(w);
        ArenaDataSC.Arena a = d == null ? null : d.arena(ri, rj);
        return a != null && a.state == ArenaDataSC.FIGHT && a.fightId == fightId;
    }

    // ================================================================== the ticks

    /** The world tick (END, the Substrate only): the countdowns every tick, the records every CHECK_TICKS. */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        long now = w.getTotalWorldTime();
        if (!COUNTDOWNS.isEmpty()) {
            countdowns(w, now);
        }
        if ((now + 7L) % CHECK_TICKS == 0) {
            tick(w, now);
        }
    }

    /** The countdowns: a line a second, the Warden at the end; one whose arena changed, unloaded or went Peaceful - dropped. */
    private static void countdowns(WorldServer w, long now) {
        ArenaDataSC d = ArenaDataSC.get(w);
        List<ArenaDataSC.Arena> due = new ArrayList<ArenaDataSC.Arena>();
        for (Iterator<Countdown> it = COUNTDOWNS.values().iterator(); it.hasNext();) {
            Countdown c = it.next();
            ArenaDataSC.Arena a = d == null ? null : d.arena(c.ri, c.rj);
            if (a == null || a.state == ArenaDataSC.FIGHT || !a.keyIn || !w.blockExists(c.x, c.y, c.z)) {
                it.remove();                                    // the key stays in the record: a click starts it again
                continue;
            }
            if (w.difficultySetting == EnumDifficulty.PEACEFUL) {
                it.remove();
                tellNear(w, a, TELL_RADIUS, EnumChatFormatting.RED, "sc.arena.peaceful");
                continue;
            }
            long t = now - c.startedAt;
            if (t >= COUNTDOWN_TICKS) {
                it.remove();
                due.add(a);
            } else if (t > 0 && t % 20 == 0) {
                announce(w, a, c, (int) ((COUNTDOWN_TICKS - t) / 20));
            }
        }
        for (ArenaDataSC.Arena a : due) {
            rise(w, a, now);
        }
    }

    /**
     * The slow pass (every CHECK_TICKS; the world test calls it too): each FIGHT record with its Console loaded - the reset by the
     * time, the missing Warden; every other record - its cracks left over in chunks unloaded at the fight's end. @return resets
     */
    public static int tick(WorldServer w, long now) {
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d == null) {
            return 0;
        }
        int resets = 0;
        for (ArenaDataSC.Arena a : new ArrayList<ArenaDataSC.Arena>(d.all())) {
            if (a.state != ArenaDataSC.FIGHT) {
                if (!a.cracks().isEmpty()) {
                    closeCracks(w, a, d);
                }
                continue;
            }
            if (!w.blockExists(a.x + ArenaLayoutSC.CONSOLE_DX, a.floorY + 1, a.z + ArenaLayoutSC.CONSOLE_DZ)) {
                continue;                                       // unloaded: decided when it loads (the Console's first tick, this)
            }
            Long k = key(a);
            if (WardenRulesSC.resetDue(a.lastPlayerAt, now)) {
                reset(w, a);
                resets++;
                continue;
            }
            if (!w.checkChunksExist(a.x - ArenaLayoutSC.HALF, 0, a.z - ArenaLayoutSC.HALF, a.x + ArenaLayoutSC.HALF, 0, a.z + ArenaLayoutSC.HALF)) {
                continue;                                       // the Warden may be in a chunk not loaded
            }
            if (warden(w, a) != null) {
                LOST.remove(k);
                continue;
            }
            Integer n = LOST.get(k);
            int lost = (n == null ? 0 : n.intValue()) + 1;
            if (lost >= LOST_CHECKS) {
                FMLLog.info("[Silicon Age] Si-14 Substrate: the Breakdown Warden of the arena at %d %d is gone without dying - the fight resets", a.x, a.z);
                reset(w, a);
                resets++;
            } else {
                LOST.put(k, Integer.valueOf(lost));
            }
        }
        return resets;
    }

    // ================================================================== helpers

    /** Arena `a`'s current Warden among the loaded entities (its UUID; before it is known - bound to the arena), or null. */
    public static EntityBreakdownWardenSC warden(WorldServer w, ArenaDataSC.Arena a) {
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityBreakdownWardenSC && !((Entity) o).isDead && ours((EntityBreakdownWardenSC) o, a)) {
                return (EntityBreakdownWardenSC) o;
            }
        }
        return null;
    }

    private static boolean ours(EntityBreakdownWardenSC b, ArenaDataSC.Arena a) {
        String u = a.bossUuid;
        if (u != null && u.length() > 0) {
            return u.equals(b.getUniqueID().toString());
        }
        return b.bound() && b.arena() == a;
    }

    /** The reset's removal: the arena's Warden(s) and every Containment Coil in its box (they are only ever its fight's). */
    @SuppressWarnings("unchecked")
    private static void removeFight(WorldServer w, ArenaDataSC.Arena a) {
        double r = ArenaLayoutSC.HALF + BOX_MARGIN, cx = a.x + 0.5D, cz = a.z + 0.5D;
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            Entity e = (Entity) o;
            if (e.isDead) {
                continue;
            }
            if (e instanceof EntityBreakdownWardenSC && (ours((EntityBreakdownWardenSC) e, a) || ((EntityBreakdownWardenSC) e).arena() == a)
                    || e instanceof EntityContainmentCoilSC && Math.abs(e.posX - cx) <= r && Math.abs(e.posZ - cz) <= r) {
                e.setDead();                                    // no death: no drops, no loot
            }
        }
    }

    /**
     * The cracks of arena `a` closed: the floor's plating back where a crack still is (a player's block or a broken crack is left
     * as it is); a crack in a chunk not loaded stays in the record for a later pass. @return closed
     */
    private static int closeCracks(WorldServer w, ArenaDataSC.Arena a, ArenaDataSC d) {
        List<int[]> open = a.takeCracks();
        if (open.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (int[] c : open) {
            if (!w.blockExists(c[0], c[1], c[2])) {
                a.addCrack(c[0], c[1], c[2]);
                continue;
            }
            if (isCrack(w.getBlock(c[0], c[1], c[2]), w.getBlockMetadata(c[0], c[1], c[2])) && ArenaBlocksSC.arena != null) {
                w.setBlock(c[0], c[1], c[2], ArenaBlocksSC.arena, ArenaBlocksSC.FLOOR, 2);   // to the clients, no neighbour asked
            }
            closed++;
        }
        if (closed > 0) {
            d.markDirty();
        }
        return closed;
    }

    /** The Breakdown crack B opens in the floor (the Ф4 vents' arcCrack). */
    public static boolean isCrack(Block b, int meta) {
        return b != null && b == SubstrateWildBlocksSC.vent && meta == SubstrateWildBlocksSC.ARC_CRACK;
    }

    /** The fighters of arena `a`: real players within SCALE_RADIUS of its middle, alive, not in creative (as B counts them). */
    public static int fighters(WorldServer w, ArenaDataSC.Arena a) {
        double cx = a.x + 0.5D, cy = a.floorY + 1D, cz = a.z + 0.5D, r2 = WardenRulesSC.SCALE_RADIUS * (double) WardenRulesSC.SCALE_RADIUS;
        int n = 0;
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer)) {
                EntityPlayer p = (EntityPlayer) o;
                if (p.isEntityAlive() && !p.capabilities.isCreativeMode && p.getDistanceSq(cx, cy, cz) <= r2) {
                    n++;
                }
            }
        }
        return n;
    }

    /** The Console's glow to the clients now (its description packet with the record's keyIn). */
    private static void refreshConsole(WorldServer w, ArenaDataSC.Arena a) {
        int x = a.x + ArenaLayoutSC.CONSOLE_DX, y = a.floorY + 1, z = a.z + ArenaLayoutSC.CONSOLE_DZ;
        if (w.blockExists(x, y, z) && w.getTileEntity(x, y, z) instanceof TileEntityBreakdownConsoleSC) {
            w.markBlockForUpdate(x, y, z);
        }
    }

    /** A second of the countdown: the line to the players near, a tick of the Console. */
    private static void announce(WorldServer w, ArenaDataSC.Arena a, Countdown c, int seconds) {
        tellNear(w, a, TELL_RADIUS, EnumChatFormatting.LIGHT_PURPLE, "sc.arena.countdown", String.valueOf(seconds));
        w.playSoundEffect(c.x + 0.5D, c.y + 0.5D, c.z + 0.5D, "note.bass", 1.0F, 0.5F + (COUNTDOWN_TICKS / 20 - seconds) * 0.2F);
    }

    /** A line to every real player within `radius` of arena `a`'s middle. */
    private static void tellNear(WorldServer w, ArenaDataSC.Arena a, int radius, EnumChatFormatting colour, String key, Object... args) {
        double cx = a.x + 0.5D, cy = a.floorY + 1D, cz = a.z + 0.5D, r2 = radius * (double) radius;
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && ((EntityPlayer) o).getDistanceSq(cx, cy, cz) <= r2) {
                tell((EntityPlayer) o, colour, key, args);
            }
        }
    }

    /** A line to one player (never a FakePlayer: no one behind it, no connection). */
    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (p instanceof FakePlayer) {
            return;
        }
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    private static Long key(ArenaDataSC.Arena a) {
        return Long.valueOf(ArenaLayoutSC.key(a.ri, a.rj));
    }

    /** One running countdown: its arena's region, its Console, when it began (the world's time). */
    private static final class Countdown {
        final int ri, rj, x, y, z;
        final long startedAt;

        Countdown(int ri, int rj, int x, int y, int z, long startedAt) {
            this.ri = ri;
            this.rj = rj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.startedAt = startedAt;
        }
    }
}
