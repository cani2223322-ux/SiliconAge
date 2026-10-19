package com.sc.settle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.sc.ShieldEventHandler;
import com.sc.dim.SubstrateConfigSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.3-§5.4, rules ПП-7..ПП-10, ПЖ-2, ПГ-6): a settlement's protection by the
 * registry - Forge's bus (registered by SCMod, both buses) at priority HIGH, so it decides before ShieldEventHandler
 * (which then sees the event cancelled and keeps quiet - no second chat line). Works in a «cold» world too (the core
 * not ticked yet). The core's own field (TileEntitySettlementCoreSC) adds what a field does: hostiles pushed and hurt,
 * shots turned back, the EMP screen, no ender teleport, no spawning, explosions.
 * <ul>
 * <li>BreakEvent, PlaceEvent, PlayerInteractEvent (RIGHT_CLICK_BLOCK on a tile entity / a mechanism, flint and steel,
 * fire charges, buckets), FillBucketEvent, ExplosionEvent.Detonate - inside a protected cylinder (or a dock zone:
 * nobody at all) cancelled, then violation();</li>
 * <li>the core, the showcase machines, the dock's blocks, the trim: never broken, by anyone, creative included - but a
 * creative player takes away one standing in no settlement (put there by /give: takenAway);</li>
 * <li>AttackEntityEvent, LivingAttackEvent with a player source (hand, shot, absolute damage) on a Tuner in its dome +
 * belt: cancelled, violation(HIT_TUNER); on a guard: let through, violation(HIT_WARDEN); a FakePlayer's attack on any
 * settlement creature in the belt: cancelled, silently; LivingDeathEvent of a guard by a player: KILL_WARDEN;</li>
 * <li>LivingSpawnEvent.CheckSpawn of an IMob inside a cylinder: DENY.</li>
 * </ul>
 * Right-clicks on creatures are never touched (trading). A right-click on a showcase in a dome is refused silently
 * (nothing to use there - and the core field's own «private zone» line stays out); the core itself answers with its
 * chat line (BlockSettlementCoreSC). On FML's bus: the server tick runs GuardAlertSC.tick, a player logging out is
 * forgotten by the guards.
 * <p>
 * The escalation (ПП-10) is kept per player UUID and settlement, transient: the last two block violations counted (a
 * sliding WINDOW_TICKS - ViolationRulesSC.stageAt; a held click counts once a second - REPEAT_TICKS), the last blow at
 * a Tuner; a chat line at most once a SAY_TICKS per player (the «Враг» lines always).
 * The signatures are fixed (package N's guard calls violation for a ПЭ-1 Point on it); the bodies are package C's.
 */
public final class SettlementProtectSC {

    public static final SettlementProtectSC INSTANCE = new SettlementProtectSC();
    /** The escalation windows of players gone quiet are dropped this often (ticks). */
    public static final int PRUNE_TICKS = 1200;

    /** Per player: the last chat line, the escalation at each settlement. Server thread only. */
    private static final Map<UUID, Offender> OFFENDERS = new HashMap<UUID, Offender>();

    private SettlementProtectSC() {
    }

    private static final class Offender {
        /** The world time of the last chat line (0: none yet). */
        long said;
        /** Settlement id -> {the last block violation counted, the one before it, the last blow at a Tuner charged}. */
        final Map<Integer, long[]> windows = new HashMap<Integer, long[]>();

        long[] window(int id) {
            long[] w = windows.get(Integer.valueOf(id));
            if (w == null) {
                w = new long[3];
                windows.put(Integer.valueOf(id), w);
            }
            return w;
        }
    }

    // ------------------------------------------------------------------ the hook (ПП-10)

    /**
     * ПП-10: player `p` did a ViolationRulesSC kind at settlement `s` (already cancelled where it is cancelled): the
     * escalation within 60 s, the guard's warning in the chat («Страж Ниобий-7: Не трогай. Это держит купол.»), the
     * «Доверие» penalty, the watch / the provocation (GuardAlertSC). Creative players: cancelled, no penalty, no
     * aggro. A FakePlayer: never (cancelled silently by the caller). Server.
     */
    public static void violation(EntityPlayer p, SettlementRegistrySC.Settlement s, int kind) {
        if (s == null || !real(p) || p.worldObj.provider.dimensionId != s.dim || kind < 0 || kind >= ViolationRulesSC.KEYS.length) {
            return;
        }
        World w = p.worldObj;
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        long now = w.getTotalWorldTime();
        UUID id = p.getUniqueID();
        Offender o = offender(id);
        long[] win = o.window(s.id);
        if (p.capabilities.isCreativeMode) {
            // ПП-10: refused all the same, but no penalty and no guards
            if (maySay(o, now)) {
                say(p, o, now, colour(new ChatComponentTranslation("sc.settle.warn.creative", speaker(w, s, p.posX, p.posY, p.posZ)),
                        EnumChatFormatting.GRAY), false);
            }
            return;
        }
        int stage = 0;
        if (ViolationRulesSC.blockKind(kind)) {
            if (ViolationRulesSC.repeats(now, win[0])) {
                return;                                         // a held click: refused, counted once a second
            }
            stage = ViolationRulesSC.stageAt(now, win[0], win[1]);
            win[1] = win[0];
            win[0] = Math.max(1L, now);
        } else if (kind == ViolationRulesSC.HIT_TUNER) {
            if (ViolationRulesSC.repeats(now, win[2])) {
                return;                                         // a flurry of blows: one a second is charged
            }
            win[2] = Math.max(1L, now);
        }
        int pen = ViolationRulesSC.penalty(kind, stage);
        boolean charged = true;
        if (kind == ViolationRulesSC.HIT_WARDEN || kind == ViolationRulesSC.POINT_WARDEN) {
            charged = ViolationRulesSC.chargesAgain(GuardAlertSC.chargedAt(s.id, p), now);   // -40 once a provocation
            if (charged) {
                GuardAlertSC.noteCharged(s.id, p, now);
            } else {
                pen = 0;
            }
        }
        SettlementRegistrySC.PlayerRec pr = r.player(id);
        int applied = 0;
        if (kind == ViolationRulesSC.KILL_WARDEN) {
            int before = pr.reputation;
            pr.reputation = ReputationSC.afterWardenKill(before);
            applied = pr.reputation - before;
        } else if (pen < 0) {
            applied = ReputationSC.give(pr, pen, ReputationSC.SRC_PENALTY, SettlementSC.day(w));
        }
        r.markDirty();

        boolean aggroRule = SubstrateConfigSC.guardsAggroOnViolations;
        if (ViolationRulesSC.provokes(kind, stage, aggroRule)) {
            GuardAlertSC.provoke(s.id, p, ViolationRulesSC.aggroTicks(kind), now);
        }
        if (stage >= ViolationRulesSC.STAGE_WATCH) {
            GuardAlertSC.watch(s.id, p, now);
        }
        // ПГ-8 / ПД-1: a guard killed, or a penalty that leaves «Доверие» below -50 - the local «Враг» for 3 game days
        SettlementRegistrySC.LocalRec was = s.localIfAny(id);
        boolean wasEnemy = was != null && was.enemy(now);
        boolean enemy = kind == ViolationRulesSC.KILL_WARDEN || applied < 0 && pr.reputation < ReputationSC.ENEMY_BELOW;
        if (enemy) {
            SettlementRegistrySC.LocalRec l = s.local(id);
            l.enemyUntil = Math.max(l.enemyUntil, now + ReputationSC.ENEMY_TICKS);
            r.markDirty();
        }

        String delta = ReputationSC.signed(applied);
        String key = null;
        EnumChatFormatting colour = EnumChatFormatting.RED;
        switch (kind) {
            case ViolationRulesSC.BREAK:
            case ViolationRulesSC.PLACE:
            case ViolationRulesSC.USE:
            case ViolationRulesSC.FIRE:
                if (stage <= ViolationRulesSC.STAGE_WARN) {
                    key = "sc.settle.warn.first";
                    colour = EnumChatFormatting.GOLD;
                } else if (stage == ViolationRulesSC.STAGE_WATCH) {
                    key = "sc.settle.warn.watch";
                    colour = EnumChatFormatting.GOLD;
                } else {
                    key = aggroRule ? "sc.settle.warn.lastAggro" : "sc.settle.warn.last";
                }
                break;
            case ViolationRulesSC.HIT_TUNER:
                key = "sc.settle.warn.hitTuner";
                break;
            case ViolationRulesSC.HIT_WARDEN:
                key = charged ? "sc.settle.warn.hitWarden" : null;      // a fight going on: no line a blow
                break;
            case ViolationRulesSC.POINT_WARDEN:
                key = charged ? "sc.settle.warn.pointWarden" : null;
                break;
            default:
                break;
        }
        if (key != null && maySay(o, now)) {
            IChatComponent who = speaker(w, s, p.posX, p.posY, p.posZ);  // looked up only for a line that goes
            IChatComponent what = new ChatComponentTranslation("sc.settle.violation." + ViolationRulesSC.KEYS[kind]);
            // block stage 1: the speaker only; stages 2-3: the cost and what it was for; a blow (stage 0): the cost
            say(p, o, now, colour(stage >= ViolationRulesSC.STAGE_WATCH ? new ChatComponentTranslation(key, who, delta, what)
                    : stage == ViolationRulesSC.STAGE_WARN ? new ChatComponentTranslation(key, who)
                    : new ChatComponentTranslation(key, who, delta), colour), false);
        }
        if (kind == ViolationRulesSC.KILL_WARDEN) {
            say(p, o, now, colour(new ChatComponentTranslation("sc.settle.enemy.kill", SettlementSC.displayName(s), delta),
                    EnumChatFormatting.DARK_RED), true);
        } else if (enemy && !wasEnemy) {
            say(p, o, now, colour(new ChatComponentTranslation("sc.settle.enemy.trust", SettlementSC.displayName(s)),
                    EnumChatFormatting.DARK_RED), true);
        }
    }

    /** Server stopped: the escalation windows and chat throttles go. */
    public static void clearAll() {
        OFFENDERS.clear();
    }

    // ------------------------------------------------------------------ blocks (ПП-7, ПР-1)

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.world.isRemote) {
            return;
        }
        if (SettlementBlocksSC.unbreakable(e.block)) {
            if (!takenAway(e.getPlayer(), e.world, e.x, e.y, e.z, e.block)) {
                e.setCanceled(true);                            // the core, a showcase, a dock block, the trim: anyone, anywhere, creative too
            }
            return;
        }
        SettlementRegistrySC.Settlement s = SettlementSC.protecting(e.world, e.x + 0.5D, e.y + 0.5D, e.z + 0.5D);
        if (s != null) {
            e.setCanceled(true);
            violation(e.getPlayer(), s, ViolationRulesSC.BREAK);
        } else if (SettlementSC.dockZoneAt(e.world, e.x + 0.5D, e.y + 0.5D, e.z + 0.5D) != null) {
            e.setCanceled(true);                                // the dock's zone: nobody, no violation
        }
    }

    /**
     * A creative player may take away a settlement's block that stands in no settlement - outside every protected
     * cylinder and dock zone, and a core of no settlement (id -1): one put there by /give or a command (an unbreakable
     * copy, or a core's eternal private field, over somebody's base would stay for good). A settlement's own core,
     * showcases, dock and trim stay unbreakable for everyone. Server (a client answers false: the server removes it).
     */
    public static boolean takenAway(EntityPlayer p, World w, int x, int y, int z, Block b) {
        if (p == null || p instanceof FakePlayer || !p.capabilities.isCreativeMode || w == null || w.isRemote
                || SettlementSC.protecting(w, x + 0.5D, y + 0.5D, z + 0.5D) != null
                || SettlementSC.dockZoneAt(w, x + 0.5D, y + 0.5D, z + 0.5D) != null) {
            return false;
        }
        if (b == SettlementBlocksSC.core) {
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, y, z);
            return !(te instanceof com.sc.tileentity.TileEntitySettlementCoreSC)
                    || ((com.sc.tileentity.TileEntitySettlementCoreSC) te).settlementId() < 0;
        }
        return true;
    }

    /** PlaceEvent and MultiPlaceEvent (a bed, a door: every block it sets). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (e.world.isRemote) {
            return;
        }
        SettlementRegistrySC.Settlement s = SettlementSC.protecting(e.world, e.x + 0.5D, e.y + 0.5D, e.z + 0.5D);
        boolean dock = s == null && SettlementSC.dockZoneAt(e.world, e.x + 0.5D, e.y + 0.5D, e.z + 0.5D) != null;
        if (s == null && e instanceof BlockEvent.MultiPlaceEvent) {
            for (BlockSnapshot b : ((BlockEvent.MultiPlaceEvent) e).getReplacedBlockSnapshots()) {
                s = SettlementSC.protecting(e.world, b.x + 0.5D, b.y + 0.5D, b.z + 0.5D);
                if (s != null) {
                    break;
                }
                dock |= SettlementSC.dockZoneAt(e.world, b.x + 0.5D, b.y + 0.5D, b.z + 0.5D) != null;
            }
        }
        if (s != null) {
            e.setCanceled(true);
            violation(e.player, s, ViolationRulesSC.PLACE);
        } else if (dock) {
            e.setCanceled(true);
        }
    }

    /**
     * RIGHT_CLICK_BLOCK in a cylinder: a tile entity or a mechanism (ShieldEventHandler.isMechanism) - USE; flint and
     * steel, a fire charge, a bucket (where the fire / fluid would go) - FIRE, in a dock zone too (no violation there).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote || e.entityPlayer == null) {
            return;
        }
        World w = e.world;
        SettlementRegistrySC.Settlement s = SettlementSC.protecting(w, e.x + 0.5D, e.y + 0.5D, e.z + 0.5D);
        if (s != null) {
            Block b = w.getBlock(e.x, e.y, e.z);
            if (b == SettlementBlocksSC.showcase || b == SettlementBlocksSC.dock) {
                e.setCanceled(true);                            // a showcase, the dock: nothing to use, no violation
                return;
            } else if (!SettlementBlocksSC.unbreakable(b) && (w.getTileEntity(e.x, e.y, e.z) != null || ShieldEventHandler.isMechanism(b))) {
                e.setCanceled(true);
                violation(e.entityPlayer, s, ViolationRulesSC.USE);
                return;
            }
        }
        ItemStack held = e.entityPlayer.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemFlintAndSteel || held.getItem() instanceof ItemFireball
                || held.getItem() instanceof ItemBucket)) {
            return;
        }
        ForgeDirection d = ForgeDirection.getOrientation(e.face);
        double ax = e.x + d.offsetX + 0.5D, ay = e.y + d.offsetY + 0.5D, az = e.z + d.offsetZ + 0.5D;
        SettlementRegistrySC.Settlement t = SettlementSC.protecting(w, ax, ay, az);
        if (t != null) {
            e.setCanceled(true);
            violation(e.entityPlayer, t, ViolationRulesSC.FIRE);
        } else if (SettlementSC.dockZoneAt(w, ax, ay, az) != null) {
            e.setCanceled(true);
        }
    }

    /** Buckets filled or emptied at a block in a cylinder or a dock zone (the block hit and the one before its face). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBucket(FillBucketEvent e) {
        if (e.world.isRemote || e.target == null || e.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        ForgeDirection d = ForgeDirection.getOrientation(e.target.sideHit);
        double x = e.target.blockX + 0.5D, y = e.target.blockY + 0.5D, z = e.target.blockZ + 0.5D;
        double ax = x + d.offsetX, ay = y + d.offsetY, az = z + d.offsetZ;
        SettlementRegistrySC.Settlement s = SettlementSC.protecting(e.world, x, y, z);
        if (s == null) {
            s = SettlementSC.protecting(e.world, ax, ay, az);
        }
        if (s != null) {
            e.setCanceled(true);
            violation(e.entityPlayer, s, ViolationRulesSC.FIRE);
        } else if (SettlementSC.dockZoneAt(e.world, x, y, z) != null || SettlementSC.dockZoneAt(e.world, ax, ay, az) != null) {
            e.setCanceled(true);
        }
    }

    /**
     * ПП-7, ПР-1: an explosion breaks no block in a cylinder or a dock zone, nor a settlement's own block anywhere near. Ф6 (ПП-14):
     * in every world SettlementSC.governs - an embassy's cylinder and Vokzal too (a creeper, TNT, a Galacticraft meteor alike).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDetonate(ExplosionEvent.Detonate e) {
        World w = e.world;
        if (w.isRemote || !SettlementSC.governs(w)) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        int dim = w.provider.dimensionId;
        double ex = e.explosion.explosionX, ez = e.explosion.explosionZ;
        // a settlement indexed in a region the blast's blocks may reach (rays go some 1.3 x its size; mods' blasts further)
        int reach = (int) Math.ceil(Math.max(0F, e.explosion.explosionSize) * 2F) + 16;
        if (r == null || r.near(dim, (int) Math.floor(ex), (int) Math.floor(ez), reach).isEmpty()) {
            return;
        }
        boolean kept = false;
        for (Iterator<ChunkPosition> it = e.getAffectedBlocks().iterator(); it.hasNext(); ) {
            ChunkPosition c = it.next();
            double x = c.chunkPosX + 0.5D, y = c.chunkPosY + 0.5D, z = c.chunkPosZ + 0.5D;
            if (r.protecting(dim, x, y, z) != null || r.dockZoneAt(dim, x, y, z) != null
                    || SettlementBlocksSC.unbreakable(w.getBlock(c.chunkPosX, c.chunkPosY, c.chunkPosZ))) {
                kept |= !w.isAirBlock(c.chunkPosX, c.chunkPosY, c.chunkPosZ);
                it.remove();
            }
        }
        if (kept) {
            w.playSoundEffect(ex, e.explosion.explosionY, ez, "random.fizz", 1.0F, 0.8F);   // as the field's shield does
        }
    }

    /** ПП-7: no hostile spawns in a cylinder or a dock zone (the core's field does it too, but only while loaded). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !(e.entityLiving instanceof IMob)) {
            return;
        }
        if (SettlementSC.protecting(e.world, e.x, e.y, e.z) != null || SettlementSC.dockZoneAt(e.world, e.x, e.y, e.z) != null) {
            e.setResult(Event.Result.DENY);
        }
    }

    // ------------------------------------------------------------------ creatures (ПЖ-2, ПГ-6, ПП-9)

    /** A blow by hand: a Tuner in the belt - refused, HIT_TUNER; a FakePlayer's at any member there - refused, silently. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttackEntity(AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        Entity t = e.target;
        if (p == null || t == null || t.worldObj == null || t.worldObj.isRemote || !(t instanceof ISettlementMemberSC)) {
            return;
        }
        SettlementRegistrySC.Settlement s = SettlementSC.beltAt(t.worldObj, t.posX, t.posZ);
        if (s == null) {
            return;
        }
        if (p instanceof FakePlayer) {
            e.setCanceled(true);
            return;
        }
        if (t instanceof ISettlementNpcSC) {
            e.setCanceled(true);
            violation(p, s, ViolationRulesSC.HIT_TUNER);
        }
        // a guard: the blow goes on to attackEntityFrom - LivingAttackEvent charges it (one place for hands, shots and absolute damage)
    }

    /** Any damage from a player (hand, shot, thrown, absolute): a Tuner in the belt - refused; a guard - let through, HIT_WARDEN. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (t == null || t.worldObj == null || t.worldObj.isRemote || !(t instanceof ISettlementMemberSC)) {
            return;
        }
        EntityPlayer p = playerOf(e.source);
        if (p == null) {
            return;
        }
        if (p instanceof FakePlayer) {
            if (SettlementSC.beltAt(t.worldObj, t.posX, t.posZ) != null) {
                e.setCanceled(true);                            // ПП-9: grinders and killer machines, silently
            }
            return;
        }
        if (t instanceof ISettlementNpcSC) {
            SettlementRegistrySC.Settlement s = SettlementSC.beltAt(t.worldObj, t.posX, t.posZ);
            if (s != null) {
                e.setCanceled(true);
                violation(p, s, ViolationRulesSC.HIT_TUNER);
            }
            return;
        }
        if (t instanceof ISettlementGuardSC) {
            SettlementRegistrySC.Settlement own = ownSettlement(t);
            if (own != null && own.inBelt(t.posX, t.posZ)) {
                violation(p, own, ViolationRulesSC.HIT_WARDEN);
            }
        }
    }

    /** A guard killed by a player: KILL_WARDEN (low priority - a death another mod cancels doesn't count). A player died: the guards forget them. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (t == null || t.worldObj == null || t.worldObj.isRemote) {
            return;
        }
        if (t instanceof EntityPlayer) {
            GuardAlertSC.forgetAll(t.getUniqueID());
            return;
        }
        if (!(t instanceof ISettlementGuardSC)) {
            return;
        }
        EntityPlayer p = playerOf(e.source);
        if (p == null && t.func_94060_bK() instanceof EntityPlayer) {
            p = (EntityPlayer) t.func_94060_bK();               // burnt, fallen, drowned after a player's blows
        }
        SettlementRegistrySC.Settlement own = p == null || p instanceof FakePlayer ? null : ownSettlement(t);
        if (own != null) {
            violation(p, own, ViolationRulesSC.KILL_WARDEN);
        }
    }

    // ------------------------------------------------------------------ FML's bus

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        World w0 = DimensionManager.getWorld(0);
        if (w0 == null) {
            return;
        }
        long now = w0.getTotalWorldTime();                      // one clock for every world (DerivedWorldInfo)
        if (now % GuardAlertSC.TICK_EVERY == 0) {
            GuardAlertSC.tick(now);
        }
        if (now % PRUNE_TICKS == 0) {
            prune(now);
        }
    }

    @SubscribeEvent
    public void onLoggedOut(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.player != null) {
            GuardAlertSC.forgetAll(e.player.getUniqueID());
        }
    }

    // ------------------------------------------------------------------ chat

    /**
     * Who speaks a settlement's lines near (x, y, z): «Страж <name>» - the nearest living guard (loaded, else the
     * nearest ALIVE guard slot of the registry by its post), «Голос купола» with no guard at all.
     */
    public static IChatComponent speaker(World w, SettlementRegistrySC.Settlement s, double x, double y, double z) {
        EntityLivingBase g = GuardAlertSC.nearestGuard(w, s.id, x, y, z);
        int code = g instanceof ISettlementMemberSC ? ((ISettlementMemberSC) g).nameCode() : 0;
        if (code == 0) {
            double best = Double.MAX_VALUE;
            for (SettlementRegistrySC.Slot sl : s.slots) {
                if (sl.state == SettlementRegistrySC.ALIVE && sl.kindOf().guard && sl.nameCode() != 0) {
                    double dx = sl.postX + 0.5D - x, dy = sl.postY - y, dz = sl.postZ + 0.5D - z;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best) {
                        best = d;
                        code = sl.nameCode();
                    }
                }
            }
        }
        return code == 0 ? new ChatComponentTranslation("sc.settle.warn.voice")
                : new ChatComponentTranslation("sc.settle.warn.speaker", NpcNamesSC.name(code));
    }

    /** A settlement's chat line to `p`: at most once a SAY_TICKS (`force`: always - the «Враг» lines, the pass). @return whether it was sent */
    public static boolean say(EntityPlayer p, IChatComponent line, boolean force) {
        if (!real(p) || line == null) {
            return false;
        }
        return say(p, offender(p.getUniqueID()), p.worldObj.getTotalWorldTime(), line, force);
    }

    /** A line may go to the player now (none within SAY_TICKS). */
    private static boolean maySay(Offender o, long now) {
        return o.said <= 0L || now < o.said || now - o.said >= ViolationRulesSC.SAY_TICKS;
    }

    private static boolean say(EntityPlayer p, Offender o, long now, IChatComponent line, boolean force) {
        if (!force && !maySay(o, now)) {
            return false;
        }
        o.said = Math.max(1L, now);
        p.addChatComponentMessage(line);
        return true;
    }

    /** `c` in colour `f` (its arguments inherit it). */
    public static IChatComponent colour(IChatComponent c, EnumChatFormatting f) {
        c.setChatStyle(new ChatStyle().setColor(f));
        return c;
    }

    // ------------------------------------------------------------------ helpers

    /** A real player on the server (not a FakePlayer: it gets no packets and never «violates»). */
    static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && p.worldObj != null && !p.worldObj.isRemote;
    }

    /** The player behind a damage source: the attacker, an arrow's shooter, a thrown thing's thrower; null none. */
    static EntityPlayer playerOf(DamageSource src) {
        if (src == null) {
            return null;
        }
        Entity e = src.getEntity();
        Entity direct = src.getSourceOfDamage();
        if (e == null && direct instanceof EntityArrow) {
            e = ((EntityArrow) direct).shootingEntity;
        }
        if (e == null && direct instanceof EntityThrowable) {
            e = ((EntityThrowable) direct).getThrower();
        }
        return e instanceof EntityPlayer ? (EntityPlayer) e : null;
    }

    /** A settlement creature's own settlement (bound, in this world), or null. */
    static SettlementRegistrySC.Settlement ownSettlement(Entity t) {
        int id = SettlementSC.settlementOf(t);
        SettlementRegistrySC r = id < 0 ? null : SettlementRegistrySC.get(t.worldObj);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        return s != null && s.dim == t.worldObj.provider.dimensionId ? s : null;
    }

    private static Offender offender(UUID id) {
        Offender o = OFFENDERS.get(id);
        if (o == null) {
            o = new Offender();
            OFFENDERS.put(id, o);
        }
        return o;
    }

    /** Players whose last line and last violations are past every window: dropped (they start afresh - as they would anyway). */
    private static void prune(long now) {
        for (Iterator<Offender> it = OFFENDERS.values().iterator(); it.hasNext(); ) {
            Offender o = it.next();
            long last = o.said;
            for (long[] w : o.windows.values()) {
                last = Math.max(last, Math.max(w[0], w[2]));
            }
            if (now < last || now - last >= ViolationRulesSC.WINDOW_TICKS) {
                if (now >= last) {
                    it.remove();
                } else {
                    o.windows.clear();                          // the clock went back: no window is valid
                    o.said = 0L;
                }
            }
        }
    }
}
