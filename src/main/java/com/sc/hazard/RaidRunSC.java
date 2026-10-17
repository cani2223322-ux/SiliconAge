package com.sc.hazard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sc.mob.EliteModSC;
import com.sc.mob.EliteSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.SpawnDirectorSC;
import com.sc.ruin.RuinLootSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.ai.NpcNavSC;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §6.1-§6.2, rules ПН-2..ПН-6): one running raid, in memory only (RaidSC keeps them by
 * settlement id; a restart ends it - its raiders are ordinary mobs again on their next load). A step every RaidSC.RUN_TICKS:
 * <ul>
 * <li>the defenders: every real player who defends (RaidSC.defends) while it runs joins them, by UUID (a relog, a respawn stays
 * one); present - in the settlement's world within reach + PRESENT_MARGIN of the core (the world test's `by` counts too);</li>
 * <li>the waves (ПН-2, RaidRulesSC.wave): each from SPAWN_OUT_MIN..MAX past the dome's edge on the gate's side (P_GATE; none - a
 * side fixed at the start), spread SPREAD across, never in a dome's belt, by SpawnDirectorSC.spawnAt (born in Si-14, the usual
 * elite roll); the elite wave's first armoured one an elite (EliteSC.apply); the repair drones over the column's Shells, bound to
 * them. The next wave NEXT_DELAY after this one is down, or WAVE_TICKS after it came anyway. A wave no room was found for is tried
 * again EMPTY_TRIES steps, then the raid is called off (no win, no loss);</li>
 * <li>down: each raider of it killed (its health gone), or - WAVE_TICKS after it came - none of it left loaded and one of it
 * killed (the rest in unloaded chunks hold no wave). A raider removed alive (setDead: Peaceful, a cleaner mod, a drone of a
 * fallen Shell) is no kill: a wave gone with none of it killed calls the raid off, Peaceful calls it off at once;</li>
 * <li>the orders: a raider without a live target in its follow range takes the nearest defender in it, else walks to the gate
 * (NpcNavSC's legs: the gate is past the path search's reach) - under the dome it can't come (IMob is pushed out);</li>
 * <li>won (ПН-3) - the last wave down: each present defender «Доверие» RaidRulesSC.rep (ReputationSC.SRC_RAID), the Credit Chips
 * RaidRulesSC.chips (outside the daily chips' cap, as the Pass), the Elder's reward - CHEST_ROLLS draws of the Dead Node's chest
 * (RuinLootSC.nodeChest) - into the inventory, what doesn't fit at the feet; sc.raid.won;</li>
 * <li>lost (ПН-4) - RAID_TICKS over, or no defender present for ABANDON_TICKS: the trade closed until world time + CLOSED_TICKS
 * (RaidSC.CLOSED); sc.raid.lost.</li>
 * </ul>
 * At the end every raider still about loses its NBT and its persistence (RaidSC.release). Server only.
 */
final class RaidRunSC {

    /** A wave down this long - the next one comes (or the raid is won). */
    static final int NEXT_DELAY = 200;
    /** A wave that found no room (the gate's ground taken, unloaded): tried again this many steps, then the raid is called off. */
    static final int EMPTY_TRIES = 5;
    /** The defenders count within reach + this of the core (ПН-3: the reward, ПН-4: the abandoned raid). */
    static final int PRESENT_MARGIN = 32;
    /** The raiders' spread across the gate's line; the spots tried for one; the drones over their Shells. */
    static final int SPREAD = 8, TRIES = 6;
    static final double DRONE_Y = 2.2D;
    /** The march to the gate (a multiple of the raider's movementSpeed). */
    static final double MARCH = 1.0D;

    final int id, dim, size;
    private final long startedAt;
    /** The world test's defender (a FakePlayer is in no player list), or the first real one. */
    private final EntityPlayer by;
    private final Set<UUID> defenders = new LinkedHashSet<UUID>();
    /** The raiders of the wave running, and of all the waves (the orders, the release at the end). */
    private final List<EntityLiving> current = new ArrayList<EntityLiving>(), all = new ArrayList<EntityLiving>();
    /** The wave running, its raiders made, its empty steps, its raiders killed (not those removed alive). */
    private int wave = -1, made, emptyTries, waveKilled;
    private long waveAt, downSince = -1L, aloneSince = -1L;
    /** The gate's side: the unit vector from the core, the gate itself (null: none in the plan). */
    private final double dirX, dirZ;
    private final int[] gate;

    RaidRunSC(WorldServer w, SettlementRegistrySC.Settlement s, EntityPlayer by) {
        id = s.id;
        dim = w.provider.dimensionId;
        size = s.size;
        startedAt = w.getTotalWorldTime();
        this.by = by;
        if (by != null) {
            defenders.add(by.getUniqueID());
        }
        int[] g = s.point(SettlementRegistrySC.P_GATE);
        gate = g != null && g.length == 3 && g[1] > 0 ? g : null;
        double dx = gate == null ? 0D : gate[0] - s.x, dz = gate == null ? 0D : gate[2] - s.z, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1D) {
            double a = w.rand.nextDouble() * Math.PI * 2D;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1D;
        }
        dirX = dx / len;
        dirZ = dz / len;
    }

    /** The raid begins: the line, the siren, the first wave. */
    void begin(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        for (EntityPlayer p : audience(w, s)) {
            EmpStormSC.tell(p, EnumChatFormatting.RED, "sc.raid.start", SettlementSC.displayName(s));
        }
        nextWave(w, r, s);
    }

    int wave() {
        return wave;
    }

    /** The raiders not killed yet (all the waves; an unloaded one counts - it is still out there). */
    int left() {
        int n = 0;
        for (EntityLiving e : all) {
            if (!killed(e)) {
                n++;
            }
        }
        return n;
    }

    /**
     * A raider of this raid loaded again (RaidSC.onJoin, a new instance of the same UUID): it takes its old self's place - in
     * the wave running if it was of it (or is unknown here). @return kept
     */
    boolean adopt(EntityLiving e) {
        UUID u = e.getUniqueID();
        boolean known = false, ofWave = false;
        for (Iterator<EntityLiving> it = all.iterator(); it.hasNext(); ) {
            EntityLiving o = it.next();
            if (o != e && u.equals(o.getUniqueID())) {
                known = true;
                ofWave |= current.remove(o);
                it.remove();
            }
        }
        if (!all.contains(e)) {
            all.add(e);
        }
        if ((ofWave || !known) && !current.contains(e)) {
            current.add(e);
        }
        return true;
    }

    /** One step (RaidSC's tick). @return the raid is over (won, lost or called off) */
    boolean step(WorldServer w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        if (s == null || s.dim != dim || w.provider.dimensionId != dim || RaidSC.peaceful(w)) {
            releaseAll();                                       // Peaceful: the mobs remove themselves - called off
            return true;
        }
        long t = w.getTotalWorldTime();
        int reach = RaidRulesSC.reach(size);
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!defenders.contains(p.getUniqueID()) && RaidSC.defends(w, r, s, p, reach)) {
                defenders.add(p.getUniqueID());
            }
        }
        if (present(w, s).isEmpty()) {
            if (aloneSince < 0L) {
                aloneSince = t;
            } else if (t - aloneSince >= RaidRulesSC.ABANDON_TICKS) {
                lose(w, r, s);
                return true;
            }
        } else {
            aloneSince = -1L;
        }
        if (t - startedAt >= RaidRulesSC.RAID_TICKS) {
            lose(w, r, s);
            return true;
        }
        prune(all);
        int left = pruneWave();
        if (left == 0 && made == 0) {
            if (++emptyTries > EMPTY_TRIES) {
                releaseAll();                               // no room for it at the gate: called off
                return true;
            }
            spawnWave(w, r, s);
            return false;
        }
        if (left == 0 && waveKilled == 0) {
            releaseAll();                                   // the whole wave removed alive, none of it killed: called off
            return true;
        }
        if (left == 0 || waveKilled > 0 && t - waveAt >= RaidRulesSC.WAVE_TICKS && loadedIn(current) == 0) {
            if (downSince < 0L) {
                downSince = t;
            }
            if (t - downSince >= NEXT_DELAY) {
                if (wave >= RaidRulesSC.WAVES - 1) {
                    win(w, r, s);
                    return true;
                }
                nextWave(w, r, s);
            }
        } else {
            downSince = -1L;
            if (wave < RaidRulesSC.WAVES - 1 && t - waveAt >= RaidRulesSC.WAVE_TICKS) {
                nextWave(w, r, s);
            }
        }
        steer(w, s);
        return false;
    }

    // ------------------------------------------------------------------ the waves

    private void nextWave(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        wave++;
        waveAt = w.getTotalWorldTime();
        downSince = -1L;
        emptyTries = 0;
        made = 0;
        waveKilled = 0;
        current.clear();
        spawnWave(w, r, s);
        for (EntityPlayer p : audience(w, s)) {
            EmpStormSC.tell(p, EnumChatFormatting.GOLD, "sc.raid.wave", String.valueOf(wave + 1), String.valueOf(RaidRulesSC.WAVES));
        }
        EmpStormSC.siren(w, s);
    }

    /** ПН-2: wave `wave` at the gate's side. */
    private void spawnWave(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        int[][] comp = RaidRulesSC.wave(wave, size, w.rand);
        boolean elite = RaidRulesSC.eliteWave(wave, comp);
        List<EntityHollowShellSC> shells = new ArrayList<EntityHollowShellSC>();
        for (int[] pair : comp) {
            for (int k = 0; k < pair[1]; k++) {
                EntityLiving e;
                if (pair[0] == MobEntitiesSC.ID_REPAIR_DRONE) {
                    if (shells.isEmpty()) {
                        break;                                  // no Shell to mend: no drone
                    }
                    EntityHollowShellSC sh = shells.get(k % shells.size());
                    e = SpawnDirectorSC.spawnAt(w, pair[0], sh.posX + w.rand.nextDouble() - 0.5D, sh.posY + DRONE_Y,
                            sh.posZ + w.rand.nextDouble() - 0.5D, false, false);
                    if (e instanceof EntityRepairDroneSC) {
                        ((EntityRepairDroneSC) e).bind(sh);
                    }
                } else {
                    e = spawnOne(w, r, s, pair[0]);
                }
                if (e == null) {
                    continue;
                }
                RaidSC.enlist(e, id);
                current.add(e);
                all.add(e);
                made++;
                if (e instanceof EntityHollowShellSC) {
                    shells.add((EntityHollowShellSC) e);
                }
                if (elite && e instanceof EntityArmoredMobSC) {     // the elite wave: its first armoured one
                    MobArmorSC a = ((EntityArmoredMobSC) e).mobArmor();
                    EliteSC.apply((EntityArmoredMobSC) e, EliteModSC.pick(w.rand, a.screen, a.heliumOn()));
                    elite = false;
                }
            }
        }
    }

    /** One raider of pool id `kind` SPAWN_OUT_MIN..MAX past the dome's edge on the gate's side; null - no room in TRIES spots. */
    private EntityLiving spawnOne(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, int kind) {
        for (int i = 0; i < TRIES; i++) {
            double out = s.radius + RaidRulesSC.SPAWN_OUT_MIN + w.rand.nextInt(RaidRulesSC.SPAWN_OUT_MAX - RaidRulesSC.SPAWN_OUT_MIN + 1);
            double side = (w.rand.nextDouble() * 2D - 1D) * SPREAD;
            int bx = MathHelper.floor_double(s.x + 0.5D + dirX * out - dirZ * side);
            int bz = MathHelper.floor_double(s.z + 0.5D + dirZ * out + dirX * side);
            if (r.inBelt(dim, bx + 0.5D, bz + 0.5D) != null) {
                continue;                                       // never in a dome's belt (a neighbour's, the edge of its own)
            }
            int y = NpcNavSC.groundY(w, bx, bz);
            if (y <= 0) {
                continue;                                       // not loaded (never loads a chunk)
            }
            EntityLiving e = SpawnDirectorSC.spawnAt(w, kind, bx + 0.5D, y, bz + 0.5D, true, false);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the orders

    /** Each raider: its target, the nearest defender in its follow range, else the march to the gate. */
    private void steer(WorldServer w, SettlementRegistrySC.Settlement s) {
        double gx = gate != null ? gate[0] + 0.5D : s.x + 0.5D + dirX * s.radius;
        double gy = gate != null ? gate[1] : s.floorY + 1;
        double gz = gate != null ? gate[2] + 0.5D : s.z + 0.5D + dirZ * s.radius;
        List<EntityPlayer> near = present(w, s);
        for (EntityLiving e : all) {
            if (!(e instanceof EntityCreature) || e instanceof EntityRepairDroneSC || !loaded(e)) {
                continue;                                       // the drones keep to their Shells
            }
            EntityCreature c = (EntityCreature) e;
            double follow = c.getEntityAttribute(SharedMonsterAttributes.followRange).getAttributeValue();
            EntityLivingBase tgt = c.getAttackTarget();
            if (tgt != null && tgt.isEntityAlive() && c.getDistanceSqToEntity(tgt) <= follow * follow) {
                continue;
            }
            EntityPlayer best = null;
            double bestD = follow * follow;
            for (EntityPlayer p : near) {
                double d = c.getDistanceSqToEntity(p);
                if (RaidSC.real(p) && !p.capabilities.isCreativeMode && d <= bestD) {
                    best = p;
                    bestD = d;
                }
            }
            if (best != null) {
                c.setAttackTarget(best);
            } else if (c.getNavigator().noPath()) {
                NpcNavSC.walkTo(c, gx, gy, gz, MARCH);
            }
        }
    }

    // ------------------------------------------------------------------ the end

    /** The world unloads under the raid (RaidSC.onUnload): nobody defends it - lost. */
    void abandon(WorldServer w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        if (s == null) {
            releaseAll();
        } else {
            lose(w, r, s);
        }
    }

    /** ПН-3: the last wave down - each present defender's reward. */
    private void win(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        long day = SettlementSC.day(w);
        int rep = RaidRulesSC.rep(size), chips = RaidRulesSC.chips(size);
        int rolls = RaidRulesSC.CHEST_ROLLS[Math.max(0, Math.min(RaidRulesSC.CHEST_ROLLS.length - 1, size))];
        for (EntityPlayer p : present(w, s)) {
            int got = ReputationSC.give(r.player(p.getUniqueID()), rep, ReputationSC.SRC_RAID, day);
            give(p, SettlementItemsSC.chips(chips));
            for (int i = 0; i < rolls; i++) {
                for (ItemStack st : RuinLootSC.nodeChest(w.rand)) {
                    give(p, st);
                }
            }
            if (HazardNetSC.reachable(p)) {
                ((EntityPlayerMP) p).inventoryContainer.detectAndSendChanges();
            }
            EmpStormSC.tell(p, EnumChatFormatting.GREEN, "sc.raid.won", SettlementSC.displayName(s), ReputationSC.signed(got),
                    String.valueOf(chips));
        }
        r.markDirty();
        releaseAll();
    }

    /** ПН-4: the trade closed for a day (world time), the line to the defenders and those near. */
    private void lose(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        NBTTagCompound x = s.extra.getCompoundTag(RaidSC.EXTRA);
        x.setLong(RaidSC.CLOSED, w.getWorldTime() + RaidRulesSC.CLOSED_TICKS);
        s.extra.setTag(RaidSC.EXTRA, x);
        r.markDirty();
        for (EntityPlayer p : audience(w, s)) {
            EmpStormSC.tell(p, EnumChatFormatting.RED, "sc.raid.lost", SettlementSC.displayName(s));
        }
        releaseAll();
    }

    /** The raiders still about are ordinary mobs again (an unloaded one: on its next load - RaidSC.onJoin). */
    private void releaseAll() {
        for (EntityLiving e : all) {
            if (loaded(e)) {
                RaidSC.release(e);
            }
        }
        all.clear();
        current.clear();
    }

    // ------------------------------------------------------------------ helpers

    /** The defenders present: in `w` within reach + PRESENT_MARGIN of the core, alive (the online ones by UUID, and `by`). */
    private List<EntityPlayer> present(WorldServer w, SettlementRegistrySC.Settlement s) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double r = RaidRulesSC.reach(size) + PRESENT_MARGIN;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (defenders.contains(p.getUniqueID()) && p.isEntityAlive() && s.distSq(p.posX, p.posZ) <= r * r) {
                out.add(p);
            }
        }
        if (by != null && !RaidSC.real(by) && by.isEntityAlive() && by.worldObj == w && s.distSq(by.posX, by.posZ) <= r * r && !out.contains(by)) {
            out.add(by);                                        // the world test's FakePlayer
        }
        return out;
    }

    /** Who hears the raid's lines: the real players within reach + PRESENT_MARGIN, and the defenders online anywhere. */
    private List<EntityPlayer> audience(WorldServer w, SettlementRegistrySC.Settlement s) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        double r = RaidRulesSC.reach(size) + PRESENT_MARGIN;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (HazardNetSC.reachable(p) && s.distSq(p.posX, p.posZ) <= r * r) {
                out.add(p);
            }
        }
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv != null && srv.getConfigurationManager() != null) {
            for (Object o : srv.getConfigurationManager().playerEntityList) {
                EntityPlayer p = (EntityPlayer) o;
                if (defenders.contains(p.getUniqueID()) && HazardNetSC.reachable(p) && !out.contains(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Killed (or removed: a despawn, setDead) - an unloaded chunk's raider is neither: it comes back by RaidSC.onJoin. */
    private static boolean killed(EntityLiving e) {
        return e.isDead || e.getHealth() <= 0F;
    }

    /** Killed for real: its health gone (a blow, the void, /kill) - not removed alive by setDead. */
    private static boolean died(EntityLiving e) {
        return e.getHealth() <= 0F;
    }

    /** Not killed and in its world now (its chunk loaded). */
    private static boolean loaded(EntityLiving e) {
        return !killed(e) && e.worldObj != null && e.worldObj.getEntityByID(e.getEntityId()) == e;
    }

    /** The wave running: drops the killed and the removed (waveKilled counts the killed). @return how many are left (unloaded too) */
    private int pruneWave() {
        for (Iterator<EntityLiving> it = current.iterator(); it.hasNext(); ) {
            EntityLiving e = it.next();
            if (killed(e)) {
                waveKilled += died(e) ? 1 : 0;
                it.remove();
            }
        }
        return current.size();
    }

    /** How many of `l` are in their world now. */
    private static int loadedIn(List<EntityLiving> l) {
        int n = 0;
        for (EntityLiving e : l) {
            n += loaded(e) ? 1 : 0;
        }
        return n;
    }

    /** Drops the killed. @return how many are left (the unloaded ones too: a wave isn't down while they are out there) */
    private static int prune(List<EntityLiving> l) {
        for (Iterator<EntityLiving> it = l.iterator(); it.hasNext(); ) {
            if (killed(it.next())) {
                it.remove();
            }
        }
        return l.size();
    }

    /** Into the inventory, what doesn't fit at the feet. */
    private static void give(EntityPlayer p, ItemStack s) {
        if (s == null || s.stackSize <= 0) {
            return;
        }
        p.inventory.addItemStackToInventory(s);
        if (s.stackSize > 0) {
            p.dropPlayerItemWithRandomChoice(s, false);
        }
    }
}
