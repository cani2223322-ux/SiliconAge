package com.sc.settle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.arena.ArenaLayoutSC;
import com.sc.block.BlockDockSC;
import com.sc.block.BlockRelaySC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.hazard.RaidRulesSC;
import com.sc.init.IdeaBlocksSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.npc.ai.NpcNavSC;
import com.sc.tileentity.TileEntityRelaySC;
import com.sc.tileentity.TileEntitySettlementCoreSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * ПД-7 (docs/plan-ideas-2026-10-26.md, «Отвоевать Мёртвый Узел: ретрансляционный пост», ★ A): the Relay's life - its placing (Forge's
 * BlockEvent.PlaceEvent: RelayRulesSC's refusals, the defence begins), the defence (FML's world tick: the waves, the defenders, the end),
 * the post's build (a settlement record of SettlementSizeSC.RELAY - SettlementRegistrySC.add - its core, its dock, the Sentries' slots,
 * the blocks over the plaza), its loss to a raid (RaidRunSC's lost raid on a RELAY record -> destroy). SCMod registers INSTANCE on
 * Forge's bus and FML's (the coordinator's).
 * <p>
 * The defence (memory only - a restart pauses it, the Relay stands: a right click goes on from the wave and the time its tile kept,
 * BlockRelaySC): HOLD_TICKS; the waves
 * (RaidRulesSC.wave of the Post's row) at 0, WAVE_GAP, 2 x WAVE_GAP or sooner when the last one is down, SPAWN_MIN..SPAWN_MAX out along
 * the canyons; the raiders go for the nearest defender (a real player within DEFEND_REACH), else for the Relay - one at it strikes
 * STRIKE a second off its RELAY_HP. Lost: its HP gone, nobody near for ABANDON_TICKS, the block gone - the Relay drops as an item, the
 * raiders are ordinary mobs, the crossing and the defenders rest RelayRulesSC.LOST_PAUSE. Held: the post - its record (RELAY, dome 12, a dock DOCK_OUT along a canyon, two Sentries' slots, extra Y),
 * the core in the Relay's place, the MAST over it, the dock 7 x 7, four lamps; the defenders +HELD_REP «Доверия» and the dock known.
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE, the signatures. The bodies - ПД-7's.
 */
public final class RelayPostSC {

    public static final RelayPostSC INSTANCE = new RelayPostSC();
    /** The defence's numbers (TODO(balance)): the Relay's HP and a raider's strike, the waves' gap, where they come from. */
    public static final int RELAY_HP = 100, STRIKE = 5, WAVE_GAP = 2000, SPAWN_MIN = 24, SPAWN_MAX = 40, STEP_TICKS = 20, SAY_TICKS = 1200;
    /** The post's shape: the dock this far along a canyon from the core, the floor's tolerance for the Relay, the footprint's height. */
    public static final int DOCK_OUT = 17, FLOOR_TOL = 2, SCAN_UP = 8, DOCK_HALF = 3;
    /** The raiders' entity NBT (the defence's crossing key). */
    public static final String RAIDER_NBT = "scRelayRaid";
    /**
     * ПД-7 (the review's fixes, appended): the registry's world extra (SettlementRegistrySC.extra RelayRulesSC.EXTRA) - GONE: the destroyed
     * posts' ids (int array; their cores never make the record again - RosterSC.restore -, their Sentries go - NpcLinkSC.exiled), PEND: a
     * destroyed post's parts whose chunks weren't loaded at the loss {dim, x, y, z, c: the core and mast still to ruin, dx, dy, dz, d: the
     * dock}, COOL: the crossings' lost-defence pause {"i,j": until world total time}; a player's (PlayerRec.extra EXTRA) LOST: his own pause.
     */
    public static final String GONE = "gone", PEND = "pend", COOL = "cool", LOST = "lost";
    /** How often the pending ruins are looked at (ticks; a multiple of STEP_TICKS). */
    public static final int SWEEP_TICKS = 100;
    /** The four ways along the canyons: +X, -X, +Z, -Z. */
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final Map<Long, Defence> DEFENCES = new HashMap<Long, Defence>();
    private static final Random RAND = new Random();

    /** One defence running at a Node (memory). */
    static final class Defence {
        final int i, j, x, y, z, dir;
        final UUID by;
        /** The world total time it began (a resume after a restart: as far back as the time already held - TileEntityRelaySC.held). */
        long startedAt;
        int wave = -1, hp = RELAY_HP;
        long waveAt, aloneSince = -1L, lastSay;
        /** The world total time of its last step (step: the time it wasn't stepped or its chunk was unloaded doesn't count). */
        long steppedAt;
        final List<EntityLiving> raiders = new ArrayList<EntityLiving>();
        final List<UUID> defenders = new ArrayList<UUID>();

        Defence(int i, int j, int x, int y, int z, int dir, UUID by, long now) {
            this.i = i;
            this.j = j;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir;
            this.by = by;
            this.startedAt = now;
            this.lastSay = now;
            this.steppedAt = now;
        }
    }

    private RelayPostSC() {
    }

    private static Long key(int i, int j) {
        return Long.valueOf(((long) i << 32) ^ (j & 0xFFFFFFFFL));
    }

    // ================================================================== the rules

    /**
     * Pure but for the world (server): why the Relay may not be set up at (x, y, z) of `w` by `p` - a lang key (sc.relay.refuse.*), or null
     * if it may (the rule, the plaza, the region, the gap, an arena, a building, a defence running, a lost defence's pause).
     */
    public static String refusal(World w, EntityPlayer p, int x, int y, int z) {
        return refusalWhy(w, p, x, y, z)[0];
    }

    /**
     * {the lang key or null, its argument (a settlement's name code / a post's), the canyon way for the dock} - and a fourth, a pause's
     * minutes, for the lost-defence pauses (the crossing's COOL, the player's LOST), or the busy region's Relay "x, z".
     */
    private static String[] refusalWhy(World w, EntityPlayer p, int x, int y, int z) {
        if (!SubstrateConfigSC.relayPosts) {
            return new String[]{"sc.relay.refuse.off", null, null};
        }
        if (w == null || w.isRemote || p == null || p instanceof FakePlayer || !SubstrateSC.is(w)) {
            return new String[]{"sc.relay.refuse.place", null, null};
        }
        int[] n = RuinLayoutSC.nodeNear(x, z);
        long seed = w.getSeed();
        if (n == null || !onPlaza(n, x, z) || Math.abs((y - 1) - SubstrateTerrainSC.nodeFloorY(seed, n[0], n[1])) > FLOOR_TOL
                || ArenaLayoutSC.isArena(seed, n[0], n[1])) {
            return new String[]{"sc.relay.refuse.place", null, null};
        }
        if (VaultLayoutSC.hasVault(seed, n[0], n[1])) {
            return new String[]{"sc.relay.refuse.vault", null, null};
        }
        synchronized (DEFENCES) {
            for (Defence d : DEFENCES.values()) {
                if (d.i == n[0] && d.j == n[1]) {
                    return new String[]{"sc.relay.refuse.busy", null, null};
                }
                if (RelayRulesSC.regionKey(d.i, d.j) == RelayRulesSC.regionKey(n[0], n[1])) {     // ...or another in its region: one post a region
                    return new String[]{"sc.relay.refuse.busyRegion", null, null, d.x + ", " + d.z};
                }
            }
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        long now = w.getTotalWorldTime();
        long left = pauseLeft(crossingPause(r, n[0], n[1]), now);
        if (left > 0L) {                                        // ПД-7: a defence lost here not long ago - the Node rests
            return new String[]{"sc.relay.refuse.cooldown", null, null, String.valueOf(minutes(left))};
        }
        SettlementRegistrySC.PlayerRec me = r == null ? null : r.playerIfAny(p.getUniqueID());
        left = pauseLeft(me == null ? 0L : me.extra.getCompoundTag(RelayRulesSC.EXTRA).getLong(LOST), now);
        if (left > 0L) {                                        // ...or he lost one anywhere not long ago
            return new String[]{"sc.relay.refuse.cooldownMe", null, null, String.valueOf(minutes(left))};
        }
        if (r != null) {
            long region = RelayRulesSC.regionKey(n[0], n[1]);
            for (SettlementRegistrySC.Settlement s : r.all()) {
                if (s.dim != w.provider.dimensionId) {
                    continue;
                }
                NBTTagCompound yx = s.extra.getCompoundTag(RelayRulesSC.EXTRA);
                if (SettlementSC.isRelay(s) && yx.hasKey("i") && RelayRulesSC.regionKey(yx.getInteger("i"), yx.getInteger("j")) == region) {
                    return new String[]{"sc.relay.refuse.region", String.valueOf(NpcNamesSC.code(s.nameElement, s.nameNumber)), null};
                }
                if (!SettlementSC.isRelay(s) && s.distSq(x + 0.5D, z + 0.5D) < (double) RelayRulesSC.SETTLEMENT_GAP * RelayRulesSC.SETTLEMENT_GAP) {
                    return new String[]{"sc.relay.refuse.near", String.valueOf(NpcNamesSC.code(s.nameElement, s.nameNumber)), null};
                }
            }
        }
        int dir = clearWay(w, x, y, z);
        if (dir < 0) {
            return new String[]{"sc.relay.refuse.built", null, null};
        }
        return new String[]{null, null, String.valueOf(dir)};
    }

    /** (x, z) is within PLAZA across of crossing n's centre. */
    static boolean onPlaza(int[] n, int x, int z) {
        double dx = x + 0.5D - (n[0] * SubstrateTerrainSC.GRID + 0.5D), dz = z + 0.5D - (n[1] * SubstrateTerrainSC.GRID + 0.5D);
        return dx * dx + dz * dz <= (double) RelayRulesSC.PLAZA * RelayRulesSC.PLAZA;
    }

    /** The first way along a canyon whose footprint (the dome's cylinder and the dock 7 x 7) holds no player's block; -1 none. */
    private static int clearWay(World w, int x, int y, int z) {
        int r = SettlementSizeSC.RELAY.radiusMax, floor = y - 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                for (int yy = floor + 1; yy <= floor + SCAN_UP; yy++) {
                    if (!(dx == 0 && dz == 0 && yy == y) && !natural(w, x + dx, yy, z + dz)) {
                        return -1;
                    }
                }
            }
        }
        for (int k = 0; k < DIRS.length; k++) {
            int bx = x + DIRS[k][0] * DOCK_OUT, bz = z + DIRS[k][1] * DOCK_OUT;
            boolean ok = true;
            for (int a = -DOCK_HALF; a <= DOCK_HALF && ok; a++) {
                for (int b = -DOCK_HALF; b <= DOCK_HALF && ok; b++) {
                    for (int yy = floor; yy <= floor + 3 && ok; yy++) {
                        ok = yy == floor ? !playerBlock(w, bx + a, yy, bz + b) : natural(w, bx + a, yy, bz + b);
                    }
                }
            }
            if (ok) {
                return k;
            }
        }
        return -1;
    }

    /** Air, the Substrate's rock, its wild blocks, a ruin's block or chest - nothing a player put there. */
    private static boolean natural(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z)) {
            return true;                                        // never loads a chunk: what isn't loaded isn't a building
        }
        Block b = w.getBlock(x, y, z);
        return b == null || b.isAir(w, x, y, z) || !playerBlock(w, x, y, z);
    }

    private static boolean playerBlock(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z)) {
            return false;
        }
        Block b = w.getBlock(x, y, z);
        return !(b == null || b.isAir(w, x, y, z) || b == SubstrateBlocksSC.rock || b == SubstrateBlocksSC.ore || b == SubstrateBlocksSC.druse
                || b == RuinBlocksSC.ruin || b == SubstrateWildBlocksSC.flora || b == SubstrateWildBlocksSC.vent || b == SubstrateWildBlocksSC.glass
                || b == IdeaBlocksSC.relay || b == Blocks.chest && ruinChest(w, x, y, z) || b == Blocks.water || b == Blocks.flowing_water);
    }

    /** A Dead Node's loot chest: one stands beside a husk (ComponentNodePlazaSC.things) - a player's chest elsewhere is his block. */
    private static boolean ruinChest(World w, int x, int y, int z) {
        for (int k = 0; k < DIRS.length; k++) {
            int bx = x + DIRS[k][0], bz = z + DIRS[k][1];
            if (w.blockExists(bx, y, bz) && w.getBlock(bx, y, bz) == RuinBlocksSC.ruin) {
                return true;
            }
        }
        return false;
    }

    /** Server: the Dead Node at crossing (i, j) has a post now (SpawnDirectorSC: no patrols there). */
    public static boolean claimed(World w, int i, int j) {
        SettlementRegistrySC r = w == null || w.isRemote ? null : SettlementRegistrySC.get(w);
        if (r == null) {
            return false;
        }
        for (SettlementRegistrySC.Settlement s : r.all()) {
            if (SettlementSC.isRelay(s) && s.dim == w.provider.dimensionId) {
                NBTTagCompound yx = s.extra.getCompoundTag(RelayRulesSC.EXTRA);
                if (yx.hasKey("i") && yx.getInteger("i") == i && yx.getInteger("j") == j) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Pure: the ticks a pause running until world total time `until` has left at `now` (0 - none). */
    public static long pauseLeft(long until, long now) {
        return until > now ? until - now : 0L;
    }

    /** Pure: `ticks` as whole minutes, rounded up (at least 1). */
    public static long minutes(long ticks) {
        return Math.max(1L, (ticks + 1199L) / 1200L);
    }

    /** The lost-defence pause key of crossing (i, j) in COOL. */
    static String crossingKey(int i, int j) {
        return i + "," + j;
    }

    /** Until when (world total time) crossing (i, j) rests after a lost defence; 0 - it doesn't. */
    static long crossingPause(SettlementRegistrySC r, int i, int j) {
        if (r == null || !r.extra.hasKey(RelayRulesSC.EXTRA, 10)) {
            return 0L;
        }
        return r.extra.getCompoundTag(RelayRulesSC.EXTRA).getCompoundTag(COOL).getLong(crossingKey(i, j));
    }

    /** Server: post `id` was destroyed (GONE) - its core never makes its record again, its creatures go. */
    public static boolean gone(SettlementRegistrySC r, int id) {
        if (r == null || id <= 0 || !r.extra.hasKey(RelayRulesSC.EXTRA, 10)) {
            return false;
        }
        for (int g : r.extra.getCompoundTag(RelayRulesSC.EXTRA).getIntArray(GONE)) {
            if (g == id) {
                return true;
            }
        }
        return false;
    }

    /** The registry's world extra compound of the posts (made and attached if missing). */
    private static NBTTagCompound worldExtra(SettlementRegistrySC r) {
        NBTTagCompound y = r.extra.getCompoundTag(RelayRulesSC.EXTRA);
        r.extra.setTag(RelayRulesSC.EXTRA, y);
        return y;
    }

    /** A refusal's first argument: its own literal (a pause's minutes), a settlement's name, else the gap. */
    private static Object argOf(String[] why) {
        if (why.length > 3 && why[3] != null) {
            return why[3];
        }
        return why[1] == null ? String.valueOf(RelayRulesSC.SETTLEMENT_GAP) : NpcNamesSC.name(Integer.parseInt(why[1]));
    }

    // ================================================================== the placing

    /** Forge: the Relay set up - refused (the item stays) or the defence begins. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (e.world == null || e.world.isRemote || IdeaBlocksSC.relay == null || e.placedBlock != IdeaBlocksSC.relay
                || e.world.getBlockMetadata(e.x, e.y, e.z) != BlockRelaySC.RELAY) {
            return;
        }
        String[] why = refusalWhy(e.world, e.player, e.x, e.y, e.z);
        if (why[0] != null) {
            e.setCanceled(true);
            if (e.player != null && !(e.player instanceof FakePlayer)) {
                tell(e.player, EnumChatFormatting.RED, why[0], argOf(why), String.valueOf(RelayRulesSC.SETTLEMENT_GAP));
            }
            return;
        }
        begin((WorldServer) e.world, e.player, e.x, e.y, e.z, Integer.parseInt(why[2]), false);
    }

    /** BlockRelaySC's right click on a standing Relay with no defence (after a restart): it begins again. */
    public static boolean resume(World w, EntityPlayer p, int x, int y, int z) {
        if (w == null || w.isRemote || !(w instanceof WorldServer)) {
            return false;
        }
        String[] why = refusalWhy(w, p, x, y, z);
        if (why[0] != null) {
            if (p != null && !(p instanceof FakePlayer)) {
                tell(p, EnumChatFormatting.RED, why[0], argOf(why), String.valueOf(RelayRulesSC.SETTLEMENT_GAP));
            }
            return true;
        }
        begin((WorldServer) w, p, x, y, z, Integer.parseInt(why[2]), true);
        return true;
    }

    /**
     * The defence at the Relay (x, y, z), its dock's way `dir`. `resume` (a right click after a restart): it goes on from where the tile
     * left it (TileEntityRelaySC's wave, time held, HP) - the waves that came don't come again.
     */
    private static Defence begin(WorldServer w, EntityPlayer p, int x, int y, int z, int dir, boolean resume) {
        int[] n = RuinLayoutSC.nodeNear(x, z);
        long now = w.getTotalWorldTime();
        Defence d = new Defence(n[0], n[1], x, y, z, dir, p.getUniqueID(), now);
        d.defenders.add(p.getUniqueID());
        TileEntity te = w.getTileEntity(x, y, z);
        TileEntityRelaySC relay = te instanceof TileEntityRelaySC ? (TileEntityRelaySC) te : null;
        long lostAt = crossingPause(SettlementRegistrySC.get(w), n[0], n[1]) - RelayRulesSC.LOST_PAUSE;
        if (resume && relay != null && relay.wave() >= 0 && lostAt < relay.at()) {     // a defence lost since its set-up: afresh
            d.wave = Math.min(relay.wave(), RelayRulesSC.WAVES - 1);
            d.startedAt = now - Math.max(0L, Math.min(relay.held(), (long) RelayRulesSC.HOLD_TICKS));
            d.waveAt = d.startedAt;
            d.hp = relay.hp() > 0 ? Math.min(relay.hp(), RELAY_HP) : RELAY_HP;
        } else if (relay != null) {
            relay.setUp(p.getUniqueID().toString(), now);
        }
        synchronized (DEFENCES) {
            DEFENCES.put(key(n[0], n[1]), d);
        }
        long left = Math.max(0L, RelayRulesSC.HOLD_TICKS - (now - d.startedAt));
        tellNear(w, d, EnumChatFormatting.GOLD, "sc.relay.start", String.valueOf(minutes(left)));
        return d;
    }

    /** The defence running at (x, y, z), or null. */
    public static boolean defending(int x, int y, int z) {
        synchronized (DEFENCES) {
            for (Defence d : DEFENCES.values()) {
                if (d.x == x && d.y == y && d.z == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Forge: a Relay under defence can't be broken by a player (the raiders' strikes are its end). */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.world != null && !e.world.isRemote && e.block == IdeaBlocksSC.relay && e.blockMetadata == BlockRelaySC.RELAY && defending(e.x, e.y, e.z)) {
            e.setCanceled(true);
            if (e.getPlayer() != null && !(e.getPlayer() instanceof FakePlayer)) {
                tell(e.getPlayer(), EnumChatFormatting.YELLOW, "sc.relay.refuse.busy");
            }
        }
    }

    // ================================================================== the defence

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)
                || e.world.getTotalWorldTime() % STEP_TICKS != 0L) {
            return;
        }
        if (e.world.getTotalWorldTime() % SWEEP_TICKS == 0L) {
            sweep((WorldServer) e.world);
        }
        List<Defence> l;
        synchronized (DEFENCES) {
            if (DEFENCES.isEmpty()) {
                return;
            }
            l = new ArrayList<Defence>(DEFENCES.values());
        }
        for (Defence d : l) {
            if (step((WorldServer) e.world, d)) {
                synchronized (DEFENCES) {
                    DEFENCES.remove(key(d.i, d.j));
                }
            }
        }
    }

    /** Defence `d`'s clocks `ticks` later: the time held, the wave and the alone-timer don't run on. */
    private static void stopClock(Defence d, long ticks) {
        d.startedAt += ticks;
        d.waveAt += ticks;
        if (d.aloneSince >= 0L) {
            d.aloneSince += ticks;
        }
    }

    /** One step of defence `d`. @return over */
    static boolean step(WorldServer w, Defence d) {
        long now = w.getTotalWorldTime(), gap = Math.max(0L, now - d.steppedAt);
        d.steppedAt = now;
        if (!w.blockExists(d.x, d.y, d.z)) {
            d.startedAt += gap;                                 // unloaded: the time held and the wave stop (nobody near can't run the time out)...
            d.waveAt += gap;
            if (d.aloneSince < 0L) {
                d.aloneSince = now;                             // ...but nobody is near: the alone-timer runs
            } else if (now - d.aloneSince >= RelayRulesSC.ABANDON_TICKS) {
                release(d);                                     // abandoned: lost without loading the chunk - the Relay stays, its progress
                pause(w, d);                                    // void (begin: a lost defence's tile isn't resumed)
                return true;
            }
            return false;
        }
        if (gap > STEP_TICKS) {
            stopClock(d, gap - STEP_TICKS);                     // the Substrate itself was unloaded (no steps): the same
        }
        long t = now - d.startedAt;
        if (w.getBlock(d.x, d.y, d.z) != IdeaBlocksSC.relay) {
            lose(w, d, false);
            return true;
        }
        List<EntityPlayer> near = defenders(w, d);
        if (near.isEmpty()) {
            if (d.aloneSince < 0L) {
                d.aloneSince = now;
            } else if (now - d.aloneSince >= RelayRulesSC.ABANDON_TICKS) {
                lose(w, d, true);
                return true;
            }
        } else {
            d.aloneSince = -1L;
            for (EntityPlayer p : near) {
                if (!d.defenders.contains(p.getUniqueID())) {
                    d.defenders.add(p.getUniqueID());
                }
            }
        }
        for (Iterator<EntityLiving> it = d.raiders.iterator(); it.hasNext(); ) {
            EntityLiving r = it.next();
            if (r.isDead || r.getHealth() <= 0F) {
                it.remove();
            }
        }
        if (t >= RelayRulesSC.HOLD_TICKS && d.wave >= RelayRulesSC.WAVES - 1) {
            build(w, d);
            return true;
        }
        if (d.wave < RelayRulesSC.WAVES - 1 && (d.wave < 0 || d.raiders.isEmpty() || t >= (long) (d.wave + 1) * WAVE_GAP)) {
            nextWave(w, d);
        }
        steer(w, d, near);
        if (d.hp <= 0) {
            lose(w, d, true);
            return true;
        }
        if (now - d.lastSay >= SAY_TICKS) {
            d.lastSay = now;
            long left = Math.max(0L, RelayRulesSC.HOLD_TICKS - t);
            tellNear(w, d, EnumChatFormatting.YELLOW, "sc.relay.left", String.valueOf((left + 1199L) / 1200L));
        }
        TileEntity te = w.getTileEntity(d.x, d.y, d.z);
        if (te instanceof TileEntityRelaySC) {
            ((TileEntityRelaySC) te).progress(d.wave, t, d.hp);    // a restart pauses it here (resume): no wave comes twice
        }
        return false;
    }

    /** The real players within DEFEND_REACH of the Relay (alive, not creative). */
    private static List<EntityPlayer> defenders(WorldServer w, Defence d) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double r = RelayRulesSC.DEFEND_REACH;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && p.isEntityAlive() && !p.capabilities.isCreativeMode
                    && p.getDistanceSq(d.x + 0.5D, d.y, d.z + 0.5D) <= r * r) {
                out.add(p);
            }
        }
        return out;
    }

    /** The next wave (the Post's row of RaidRulesSC.wave) out along the canyons. */
    private static void nextWave(WorldServer w, Defence d) {
        d.wave++;
        d.waveAt = w.getTotalWorldTime();
        int[][] comp = RaidRulesSC.wave(Math.min(d.wave, RaidRulesSC.WAVES - 1), SettlementSizeSC.OUTPOST.ordinal(), w.rand);
        for (int[] pair : comp) {
            if (pair[0] == MobEntitiesSC.ID_REPAIR_DRONE) {
                continue;                                       // the drones keep to their Shells - none on a plaza's defence
            }
            for (int k = 0; k < pair[1]; k++) {
                EntityLiving e = spawnOne(w, d, pair[0]);
                if (e != null) {
                    e.getEntityData().setLong(RAIDER_NBT, key(d.i, d.j).longValue());
                    e.func_110163_bv();
                    d.raiders.add(e);
                }
            }
        }
        tellNear(w, d, EnumChatFormatting.GOLD, "sc.relay.wave", String.valueOf(d.wave + 1), String.valueOf(RelayRulesSC.WAVES));
    }

    private static EntityLiving spawnOne(WorldServer w, Defence d, int kind) {
        for (int t = 0; t < 6; t++) {
            int[] dir = DIRS[RAND.nextInt(DIRS.length)];
            int out = SPAWN_MIN + RAND.nextInt(SPAWN_MAX - SPAWN_MIN + 1), side = RAND.nextInt(7) - 3;
            int bx = d.x + dir[0] * out + dir[1] * side, bz = d.z + dir[1] * out + dir[0] * side;
            int y = NpcNavSC.groundY(w, bx, bz);
            if (y <= 0) {
                continue;
            }
            EntityLiving e = SpawnDirectorSC.spawnAt(w, kind, bx + 0.5D, y, bz + 0.5D, true, false);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    /** The raiders: the nearest defender in their follow range, else the Relay - one at it strikes STRIKE a step. */
    private static void steer(WorldServer w, Defence d, List<EntityPlayer> near) {
        for (EntityLiving e : d.raiders) {
            if (!(e instanceof EntityCreature) || e.worldObj != w || w.getEntityByID(e.getEntityId()) != e) {
                continue;
            }
            EntityCreature c = (EntityCreature) e;
            double follow = c.getEntityAttribute(SharedMonsterAttributes.followRange).getAttributeValue();
            EntityLivingBase tgt = c.getAttackTarget();
            if (tgt == null || !tgt.isEntityAlive() || c.getDistanceSqToEntity(tgt) > follow * follow) {
                EntityPlayer best = null;
                double bestD = follow * follow;
                for (EntityPlayer p : near) {
                    double dd = c.getDistanceSqToEntity(p);
                    if (dd <= bestD) {
                        best = p;
                        bestD = dd;
                    }
                }
                if (best != null) {
                    c.setAttackTarget(best);
                } else {
                    double dd = c.getDistanceSq(d.x + 0.5D, d.y, d.z + 0.5D);
                    if (dd <= 2.5D * 2.5D) {
                        d.hp -= STRIKE;                         // at the Relay: a strike a step
                        c.swingItem();
                        w.playSoundEffect(d.x + 0.5D, d.y + 0.5D, d.z + 0.5D, "random.anvil_land", 0.3F, 1.8F);
                    } else if (c.getNavigator().noPath()) {
                        NpcNavSC.walkTo(c, d.x + 0.5D, d.y, d.z + 0.5D, 1.0D);
                    }
                }
            }
        }
    }

    /**
     * Lost: the Relay drops as an item (if still there), the raiders ordinary mobs, the line; the crossing and every defender rest
     * RelayRulesSC.LOST_PAUSE (no endless waves on demand: set up, beat the waves, walk off, set up again).
     */
    private static void lose(WorldServer w, Defence d, boolean drop) {
        if (drop) {
            dropRelay(w, d);
        }
        release(d);
        pause(w, d);
        tellNear(w, d, EnumChatFormatting.RED, "sc.relay.lost");
    }

    /** The Relay (if still there) back as an item. */
    private static void dropRelay(WorldServer w, Defence d) {
        if (w.getBlock(d.x, d.y, d.z) == IdeaBlocksSC.relay) {
            w.setBlockToAir(d.x, d.y, d.z);
            EntityItem it = new EntityItem(w, d.x + 0.5D, d.y + 0.5D, d.z + 0.5D, IdeaBlocksSC.relay(1));
            it.delayBeforeCanPickup = 10;
            w.spawnEntityInWorld(it);
        }
    }

    /** A lost defence's pause: crossing (i, j) in COOL, each defender's LOST (expired crossings dropped). */
    private static void pause(WorldServer w, Defence d) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        long now = w.getTotalWorldTime(), until = now + RelayRulesSC.LOST_PAUSE;
        NBTTagCompound y = worldExtra(r), c = y.getCompoundTag(COOL);
        for (Object k : new ArrayList<Object>(c.func_150296_c())) {
            if (c.getLong((String) k) <= now) {
                c.removeTag((String) k);
            }
        }
        c.setLong(crossingKey(d.i, d.j), until);
        y.setTag(COOL, c);
        for (UUID u : d.defenders) {
            SettlementRegistrySC.PlayerRec rec = r.player(u);
            NBTTagCompound m = rec.extra.getCompoundTag(RelayRulesSC.EXTRA);
            m.setLong(LOST, until);
            rec.extra.setTag(RelayRulesSC.EXTRA, m);
        }
        r.markDirty();
    }

    private static void release(Defence d) {
        for (EntityLiving e : d.raiders) {
            release(e);
        }
        d.raiders.clear();
    }

    /** No raider any more: the NBT off, the vanilla despawn again (EntityLiving.persistenceRequired). */
    private static void release(EntityLiving e) {
        e.getEntityData().removeTag(RAIDER_NBT);
        try {
            ReflectionHelper.setPrivateValue(EntityLiving.class, e, Boolean.FALSE, "persistenceRequired", "field_82179_bU");
        } catch (RuntimeException ex) {
            // another mapping: it stays
        }
    }

    /**
     * Forge: a raider loaded again (its chunk, a restart - a new object of the same UUID): of a defence still running - it takes its old
     * self's place in the defence; else an ordinary mob again (as RaidSC.onJoin).
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world == null || e.world.isRemote || !(e.entity instanceof EntityLiving) || !e.entity.getEntityData().hasKey(RAIDER_NBT)) {
            return;
        }
        EntityLiving n = (EntityLiving) e.entity;
        Defence d;
        synchronized (DEFENCES) {
            d = DEFENCES.get(Long.valueOf(n.getEntityData().getLong(RAIDER_NBT)));
        }
        if (d == null) {
            release(n);
            return;
        }
        UUID u = n.getUniqueID();
        for (Iterator<EntityLiving> it = d.raiders.iterator(); it.hasNext(); ) {
            EntityLiving o = it.next();
            if (o != n && u.equals(o.getUniqueID())) {
                it.remove();                                    // its old self, unloaded
            }
        }
        if (!d.raiders.contains(n)) {
            d.raiders.add(n);
        }
    }

    // ================================================================== the post

    /** Held: the post's record and blocks; the defenders' «Доверие», the dock known, the line. */
    private static SettlementRegistrySC.Settlement build(WorldServer w, Defence d) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            lose(w, d, true);
            return null;
        }
        long region = RelayRulesSC.regionKey(d.i, d.j);
        for (SettlementRegistrySC.Settlement o : r.all()) {
            NBTTagCompound oy = o.extra.getCompoundTag(RelayRulesSC.EXTRA);
            if (o.dim == w.provider.dimensionId && SettlementSC.isRelay(o) && oy.hasKey("i")
                    && RelayRulesSC.regionKey(oy.getInteger("i"), oy.getInteger("j")) == region) {
                dropRelay(w, d);                                // the region got its post meanwhile: one a region - over, not lost
                release(d);
                tellNear(w, d, EnumChatFormatting.YELLOW, "sc.relay.refuse.region", NpcNamesSC.name(NpcNamesSC.code(o.nameElement, o.nameNumber)));
                return null;
            }
        }
        release(d);
        int floor = d.y - 1, dx = DIRS[d.dir][0], dz = DIRS[d.dir][1];
        long seed = mix(w.getSeed() ^ 0x5E1A7L, d.i, d.j);
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = w.provider.dimensionId;
        s.x = d.x;
        s.y = d.y;
        s.z = d.z;
        s.floorY = floor;
        s.size = SettlementSizeSC.RELAY.ordinal();
        s.radius = SettlementSizeSC.RELAY.radiusMax;
        s.seed = seed;
        Random rand = new Random(seed);
        int code = NpcNamesSC.random(rand);
        s.nameElement = NpcNamesSC.element(code);
        s.nameNumber = NpcNamesSC.number(code);
        s.hasDock = true;
        s.dockX = d.x + dx * DOCK_OUT;
        s.dockY = floor;
        s.dockZ = d.z + dz * DOCK_OUT;
        s.dockZone = SettlementSC.DOCK_ZONE;
        s.madeAt = w.getTotalWorldTime();
        s.version = SettlementRegistrySC.VERSION;
        s.points.put(SettlementRegistrySC.P_PLAZA, new int[]{d.x, floor + 1, d.z});
        s.points.put(SettlementRegistrySC.P_SQUARE, new int[]{d.x, floor + 1, d.z});
        s.points.put(SettlementRegistrySC.P_GATE, new int[]{d.x + dx * (s.radius - 1), floor + 1, d.z + dz * (s.radius - 1)});
        for (int k = 0; k < SettlementSizeSC.RELAY.sentries; k++) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = NpcKindSC.SENTRY.ordinal();
            int c = NpcNamesSC.random(rand);
            sl.nameElement = NpcNamesSC.element(c);
            sl.nameNumber = NpcNamesSC.number(c);
            int side = k == 0 ? 1 : -1;
            sl.postX = sl.homeX = d.x + dz * 4 * side;
            sl.postY = sl.homeY = floor + 1;
            sl.postZ = sl.homeZ = d.z + dx * 4 * side;
            sl.state = SettlementRegistrySC.NEVER;
            s.slots.add(sl);
        }
        NBTTagCompound yx = new NBTTagCompound();
        yx.setInteger("i", d.i);
        yx.setInteger("j", d.j);
        yx.setLong("at", w.getTotalWorldTime());
        s.extra.setTag(RelayRulesSC.EXTRA, yx);
        r.add(s);
        // the blocks: the core in the Relay's place, the mast over it, the dock, four lamps at the dome's edge
        w.setBlock(d.x, d.y, d.z, SettlementBlocksSC.core, 0, 3);
        TileEntity te = w.getTileEntity(d.x, d.y, d.z);
        if (te instanceof TileEntitySettlementCoreSC) {
            ((TileEntitySettlementCoreSC) te).setup(s.id, s.radius, floor);
        }
        if (w.isAirBlock(d.x, d.y + 1, d.z)) {
            w.setBlock(d.x, d.y + 1, d.z, IdeaBlocksSC.relay, BlockRelaySC.MAST, 3);
            TileEntity m = w.getTileEntity(d.x, d.y + 1, d.z);
            if (m instanceof TileEntityRelaySC) {
                ((TileEntityRelaySC) m).setPost(s.id);
            }
        }
        for (int a = -DOCK_HALF; a <= DOCK_HALF; a++) {
            for (int b = -DOCK_HALF; b <= DOCK_HALF; b++) {
                w.setBlock(s.dockX + a, floor, s.dockZ + b, SettlementBlocksSC.dock, a == 0 && b == 0 ? BlockDockSC.BEACON : BlockDockSC.PLATE, 3);
                for (int yy = floor + 1; yy <= floor + 3; yy++) {
                    if (!w.isAirBlock(s.dockX + a, yy, s.dockZ + b)) {
                        w.setBlockToAir(s.dockX + a, yy, s.dockZ + b);
                    }
                }
            }
        }
        int lr = s.radius - 2;
        for (int[] q : new int[][]{{lr, lr}, {-lr, lr}, {lr, -lr}, {-lr, -lr}}) {
            int lx = d.x + (int) Math.round(q[0] * 0.7D), lz = d.z + (int) Math.round(q[1] * 0.7D);
            if (w.isAirBlock(lx, floor + 1, lz) && w.isAirBlock(lx, floor + 2, lz)) {
                w.setBlock(lx, floor + 1, lz, SettlementBlocksSC.decor, com.sc.block.BlockSettlementDecorSC.TITANIUM, 3);
                w.setBlock(lx, floor + 2, lz, Blocks.glowstone, 0, 3);
            }
        }
        long day = SettlementSC.day(w);
        for (UUID u : d.defenders) {
            EntityPlayer p = w.func_152378_a(u);
            SettlementRegistrySC.PlayerRec rec = r.player(u);
            int got = ReputationSC.give(rec, RelayRulesSC.HELD_REP, ReputationSC.SRC_RELAY, day);
            rec.knownDocks.add(Integer.valueOf(s.id));
            if (p != null && got >= 0) {
                tell(p, EnumChatFormatting.GREEN, "sc.relay.built", NpcNamesSC.name(code), String.valueOf(d.x), String.valueOf(d.z));
            }
        }
        r.markDirty();
        return s;
    }

    /**
     * Server (RaidRunSC, a raid on post `s` lost): the post is gone - its id GONE (its core never makes the record again: RosterSC.restore),
     * its loaded blocks back to ruin (an unloaded core or dock: PEND - the sweep ruins it when its chunk is loaded), its loaded creatures
     * removed (the unloaded ones go when loaded: NpcLinkSC.exiled), its record removed (SettlementRegistrySC.remove - RELAY only), the line
     * to the Substrate.
     */
    public static void destroy(World w, SettlementRegistrySC.Settlement s) {
        if (w == null || w.isRemote || s == null || !SettlementSC.isRelay(s)) {
            return;
        }
        boolean core = ruinCore(w, s.x, s.y, s.z), dock = !s.hasDock || ruinDock(w, s.dockX, s.dockY, s.dockZ);
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof ISettlementMemberSC && o instanceof Entity && ((ISettlementMemberSC) o).settlementId() == s.id) {
                ((Entity) o).setDead();                          // the post's Sentries: no core makes them again
            }
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r != null) {
            NBTTagCompound y = worldExtra(r);
            int[] had = y.getIntArray(GONE), g = java.util.Arrays.copyOf(had, had.length + 1);
            g[had.length] = s.id;
            y.setIntArray(GONE, g);
            if (!core || !dock) {
                NBTTagCompound p = new NBTTagCompound();
                p.setInteger("dim", s.dim);
                p.setInteger("x", s.x);
                p.setInteger("y", s.y);
                p.setInteger("z", s.z);
                p.setBoolean("c", !core);
                p.setInteger("dx", s.dockX);
                p.setInteger("dy", s.dockY);
                p.setInteger("dz", s.dockZ);
                p.setBoolean("d", !dock);
                NBTTagList l = y.getTagList(PEND, 10);
                l.appendTag(p);
                y.setTag(PEND, l);
            }
            r.remove(s.id);
            r.markDirty();
        }
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer)) {
                tell((EntityPlayer) o, EnumChatFormatting.RED, "sc.relay.destroyed", NpcNamesSC.name(NpcNamesSC.code(s.nameElement, s.nameNumber)));
            }
        }
    }

    /** A destroyed post's core (and the mast over it) to ruin. @return done - its chunk loaded (else nothing was read: never loads one) */
    private static boolean ruinCore(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z)) {
            return false;
        }
        if (w.getBlock(x, y, z) == SettlementBlocksSC.core) {
            w.removeTileEntity(x, y, z);
            w.setBlock(x, y, z, RuinBlocksSC.ruin, 0, 3);
        }
        if (y + 1 < 256 && w.getBlock(x, y + 1, z) == IdeaBlocksSC.relay) {
            w.setBlock(x, y + 1, z, RuinBlocksSC.ruin, 0, 3);
        }
        return true;
    }

    /** A destroyed post's dock 7 x 7 at (x, y, z) to ruin - its loaded part. @return all of it was loaded */
    private static boolean ruinDock(World w, int x, int y, int z) {
        boolean all = true;
        for (int a = -DOCK_HALF; a <= DOCK_HALF; a++) {
            for (int b = -DOCK_HALF; b <= DOCK_HALF; b++) {
                if (!w.blockExists(x + a, y, z + b)) {
                    all = false;
                } else if (w.getBlock(x + a, y, z + b) == SettlementBlocksSC.dock) {
                    w.setBlock(x + a, y, z + b, RuinBlocksSC.ruin, 0, 3);
                }
            }
        }
        return all;
    }

    /** Every SWEEP_TICKS: the destroyed posts' parts left in unloaded chunks (PEND) - ruined once their chunks are loaded. */
    private static void sweep(WorldServer w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null || !r.extra.hasKey(RelayRulesSC.EXTRA, 10)) {
            return;
        }
        NBTTagCompound y = r.extra.getCompoundTag(RelayRulesSC.EXTRA);
        if (!y.hasKey(PEND, 9)) {
            return;
        }
        NBTTagList l = y.getTagList(PEND, 10), keep = new NBTTagList();
        boolean changed = false;
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound p = l.getCompoundTagAt(i);
            if (p.getInteger("dim") == w.provider.dimensionId) {
                if (p.getBoolean("c") && ruinCore(w, p.getInteger("x"), p.getInteger("y"), p.getInteger("z"))) {
                    p.setBoolean("c", false);
                    changed = true;
                }
                if (p.getBoolean("d") && ruinDock(w, p.getInteger("dx"), p.getInteger("dy"), p.getInteger("dz"))) {
                    p.setBoolean("d", false);
                    changed = true;
                }
            }
            if (p.getBoolean("c") || p.getBoolean("d")) {
                keep.appendTag(p);
            }
        }
        if (changed) {
            if (keep.tagCount() == 0) {
                y.removeTag(PEND);
            } else {
                y.setTag(PEND, keep);
            }
            r.markDirty();
        }
    }

    /** Server stopped: the defences running (memory only - a restart pauses a defence: the Relay stands, a right click goes on). */
    public static void clearAll() {
        synchronized (DEFENCES) {
            DEFENCES.clear();
        }
    }

    // ================================================================== the world test's hooks

    /** The world test: the defence at the Relay (x, y, z) held now - the post built. @return the post's record, or null */
    public static SettlementRegistrySC.Settlement finishNow(WorldServer w, int x, int y, int z) {
        Defence d = null;
        synchronized (DEFENCES) {
            for (Defence e : DEFENCES.values()) {
                if (e.x == x && e.y == y && e.z == z) {
                    d = e;
                }
            }
            if (d != null) {
                DEFENCES.remove(key(d.i, d.j));
            }
        }
        return d == null ? null : build(w, d);
    }

    /** The world test: the defence's begin as a placing would (the refusal asked). @return null - began, else the refusal's key */
    public static String placeNow(WorldServer w, EntityPlayer p, int x, int y, int z) {
        String[] why = refusalWhy(w, p, x, y, z);
        if (why[0] == null) {
            begin(w, p, x, y, z, Integer.parseInt(why[2]), false);
        }
        return why[0];
    }

    // ================================================================== helpers

    private static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static void tellNear(WorldServer w, Defence d, EnumChatFormatting colour, String key, Object... args) {
        double r = RelayRulesSC.DEFEND_REACH + 32;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getDistanceSq(d.x + 0.5D, d.y, d.z + 0.5D) <= r * r) {
                tell(p, colour, key, args);
            }
        }
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (p instanceof EntityPlayerMP && !(p instanceof FakePlayer)) {
            p.addChatComponentMessage(new ChatComponentTranslation(key, args).setChatStyle(new ChatStyle().setColor(colour)));
        }
    }
}
