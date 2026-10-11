package com.sc.emp;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-pe1-tech.md): the gun's behaviour - charge-up, Point / Pulse hitscan, the gun's heat,
 * modules, the aim scan (EmpNetSC.AimMessage). The siphon stream is EmpSiphonSC, the aim ray and the pure
 * decisions EmpShotSC.
 *
 * Use: right-click starts player.setItemInUse on both sides (the client checks what it can - charge, heat, mode,
 * a siphon target - so it doesn't start what the server refuses); the server keeps the charge-up / stream in USES
 * and fires itself when the charge-up is done (playerTick). Rule 0.4.1: the gun's NBT isn't touched while it is in
 * use - a changed stack reaches the client as a new object and drops its itemInUse; the shot spends EU at the end,
 * the stream keeps its EU and heat in Use and writes them when it stops. That same effect ends the client's use
 * after a shot; endUse forces it when nothing changed (a cancel).
 *
 * The client repeats a held right-click every 4 ticks once the use is over: a click that comes within HELD_GAP of
 * the last activity (no release in between) is ignored on both sides, so holding the button doesn't fire again.
 */
public final class EmpGunLogicSC {

    /** Ticks between clicks / using ticks that still count as one held button (server a little looser: packet jitter). */
    private static final int HELD_GAP_CLIENT = 6, HELD_GAP_SERVER = 8;
    /** The same message to the same player at most once in this many ticks. */
    private static final int SAY_COOLDOWN = 20;
    private static final int[] ALL_PIECES = {0, 1, 2, 3};

    /** Server: a running charge-up (Point / Pulse) or stream (Siphon). */
    static final class Use {
        final ItemStack gun;
        final EmpMode mode;
        final long start;
        /** Pulse: the target locked at the start (0 none) and ticks it has been out of sight. */
        int lockedId, lost;
        /** Pulse: seconds of hum played. */
        int hums;
        /**
         * Siphon: the target, the «Страж» reversal of this second, the gun's EU and heat (TENTHS of a heat unit) not yet
         * written, EU received / vented (overflow), the soft overheat (the stream at SIPHON_THROTTLED_PCT).
         */
        int targetId;
        boolean reversed, throttled;
        int pendingCharge, pendingHeat;
        long received, vented;
        /** Siphon: world ticks of the last stream second and transfer, of the last venting sparks (-1: not yet). */
        long secondAt = -1, stepAt = -1, ventFxAt = -1;

        Use(ItemStack gun, EmpMode mode, long start) {
            this.gun = gun;
            this.mode = mode;
            this.start = start;
        }
    }

    static final Map<EntityPlayer, Use> USES = new WeakHashMap<EntityPlayer, Use>();
    /** Last click / using tick per player, one map per side (each touched by its own thread only). */
    private static final Map<EntityPlayer, Long> ACTIVITY_SERVER = new WeakHashMap<EntityPlayer, Long>();
    private static final Map<EntityPlayer, Long> ACTIVITY_CLIENT = new WeakHashMap<EntityPlayer, Long>();
    private static final Map<EntityPlayer, Map<String, Long>> SAID = new WeakHashMap<EntityPlayer, Map<String, Long>>();
    /** Server: the last aim scan per player, the last cooling step per gun stack (identity keys). */
    private static final Map<EntityPlayer, Long> SCANNED = new WeakHashMap<EntityPlayer, Long>();
    private static final Map<ItemStack, Long> COOLED = new WeakHashMap<ItemStack, Long>();

    private EmpGunLogicSC() {
    }

    /** ItemEmpGunSC.onItemRightClick: start the charge-up / the stream (player.setItemInUse), or refuse. */
    public static ItemStack rightClick(ItemStack stack, World world, EntityPlayer player) {
        long now = world.getTotalWorldTime();
        Map<EntityPlayer, Long> act = activity(world);
        Long last = act.get(player);
        act.put(player, now);
        if (last != null && EmpShotSC.heldOn(last, now, world.isRemote ? HELD_GAP_CLIENT : HELD_GAP_SERVER)) {
            return stack;                                       // still the button of the last use
        }
        EmpMode mode = ItemEmpGunSC.modeOf(stack);
        if (mode != EmpMode.SIPHON && player.isSneaking()) {
            return stack;                                       // Shift + right-click is the cancel gesture
        }
        int why = startRefusal(stack, mode, now);
        if (why != EmpShotSC.START_OK) {
            if (!world.isRemote) {
                sayStart(player, stack, why, now);
            }
            return stack;
        }
        if (world.isRemote) {
            if (mode == EmpMode.SIPHON && (EmpSiphonSC.targetOf(world, player, stack) == null
                    || EmpSiphonSC.pickReceiver(player, stack, null) < 0)) {
                return stack;                                   // the server says why
            }
            player.setItemInUse(stack, stack.getMaxItemUseDuration());
            return stack;
        }
        if (!(player instanceof EntityPlayerMP)) {
            return stack;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        drop(p);
        if (mode == EmpMode.SIPHON) {
            if (!EmpSiphonSC.start(p, stack, now)) {
                return stack;                                   // not in use: the server resends the inventory, the client drops it too
            }
        } else {
            Use u = new Use(stack, mode, now);
            if (mode == EmpMode.PULSE) {
                EmpShotSC.Hit h = EmpShotSC.aim(world, p, range(stack, mode));
                u.lockedId = h.entity != null ? h.entity.getEntityId() : 0;
                SoundsSC.play(world, p.posX, p.posY, p.posZ, EmpSC.SND_PULSE_HUM, EmpMathSC.PULSE_HUM_RANGE / 16F, 0.8F);
            } else {
                SoundsSC.play(world, p.posX, p.posY, p.posZ, EmpSC.SND_CHARGE, 0.6F, 1F);
            }
            USES.put(p, u);
        }
        player.setItemInUse(stack, stack.getMaxItemUseDuration());
        return stack;
    }

    /** ItemEmpGunSC.onUsingTick: `ticksUsed` since the start (both sides). */
    public static void usingTick(ItemStack stack, EntityPlayer player, int ticksUsed) {
        World w = player.worldObj;
        activity(w).put(player, w.getTotalWorldTime());
        if (!w.isRemote) {
            return;                                             // the server's charge-up runs in playerTick
        }
        EmpMode mode = ItemEmpGunSC.modeOf(stack);
        // Client: the charge-up bar ends with the shot (the server fires on its own) or with Shift (the server cancels too)
        if (mode != EmpMode.SIPHON && (player.isSneaking() || ticksUsed >= EmpMathSC.chargeTicks(mode, ItemEmpGunSC.upgraded(stack)))) {
            player.clearItemInUse();
        }
    }

    /** ItemEmpGunSC.onPlayerStoppedUsing: right-click released after `ticksUsed`. */
    public static void stoppedUsing(ItemStack stack, World world, EntityPlayer player, int ticksUsed) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) player;
            Use u = USES.get(p);
            if (u != null && u.mode == EmpMode.SIPHON) {
                EmpSiphonSC.stop(p, u, null);
            } else if (u != null) {
                USES.remove(p);                                 // released before the shot: nothing spent
                say(p, "sc.emp.cancelled");
            }
        }
        activity(world).remove(player);                         // a real release: the next click may start at once
    }

    /** ItemEmpGunSC.onUpdate: the gun's cooling (never writes NBT of the gun in use - see the tech plan). */
    public static void inventoryTick(ItemStack stack, World world, Entity holder, boolean held) {
        if (world.isRemote) {
            return;
        }
        long now = world.getTotalWorldTime();
        Long last = COOLED.get(stack);
        if (last != null && now >= last && now - last < 20) {
            return;                                             // once a second by the clock (a holder may tick twice a tick)
        }
        if (holder instanceof EntityPlayer && inUse((EntityPlayer) holder, stack)) {
            return;                                             // in use: rule 0.4.1
        }
        COOLED.put(stack, now);
        int heat = ItemEmpGunSC.heatOf(stack);
        long at = ItemEmpGunSC.lastHeatAt(stack);
        if (heat > 0 && (now - at >= EmpMathSC.GUN_COOL_DELAY || now < at)) {
            ItemEmpGunSC.setHeat(stack, heat - EmpMathSC.GUN_COOL_PER_SECOND, now);
        }
    }

    /** Server, every tick of every player (EmpEventsSC): the stream, the aim scan, pending EU. */
    public static void playerTick(EntityPlayerMP p) {
        long now = EmpSC.now(p);
        Use u = USES.get(p);
        if (u != null) {
            if (p.isDead || p.getHealth() <= 0 || !inUse(p, u.gun)) {
                drop(p);                                        // another slot, another item, death: no shot, the stream stops
            } else if (u.mode == EmpMode.SIPHON) {
                EmpSiphonSC.tick(p, u, now);
            } else {
                chargeTick(p, u, now);
            }
        }
        ItemStack gun = ItemEmpGunSC.held(p);
        Long scanned = SCANNED.get(p);
        if (gun != null && (scanned == null || now < scanned || now - scanned >= EmpMathSC.AIM_SCAN_TICKS)) {
            SCANNED.put(p, now);
            aimScan(p, gun, USES.get(p));
        }
    }

    /** Server: drop the charge-up / stream without spending EU (`told`: say so in the action bar). */
    public static void cancelCharge(EntityPlayerMP p, ItemStack gun, boolean told) {
        Use u = USES.get(p);
        if (u == null) {
            return;
        }
        if (u.mode == EmpMode.SIPHON) {
            EmpSiphonSC.stop(p, u, null);
        } else {
            USES.remove(p);
            endUse(p, u.gun);
        }
        if (told) {
            say(p, "sc.emp.cancelled");
        }
    }

    /** Server: put module `m` from main inventory slot `slot` into the gun. */
    public static void installModule(EntityPlayerMP p, ItemStack gun, EmpModule m, int slot) {
        if (m == null || !ItemEmpGunSC.isGun(gun)) {
            return;
        }
        if (ItemEmpGunSC.hasModule(gun, m)) {
            say(p, "sc.emp.module.already", moduleName(m));
            return;
        }
        ItemStack s = slot >= 0 && slot < p.inventory.mainInventory.length ? p.inventory.mainInventory[slot] : null;
        if (ItemEmpModuleSC.moduleOf(s) != m) {
            say(p, "sc.emp.module.missing", moduleName(m));
            return;
        }
        cancelCharge(p, gun, false);
        p.inventory.decrStackSize(slot, 1);
        ItemEmpGunSC.setModule(gun, m, true);
        say(p, "sc.emp.module.installed", moduleName(m));
    }

    /** Server: take module `m` out of the gun into the inventory (or drop it). */
    public static void removeModule(EntityPlayerMP p, ItemStack gun, EmpModule m) {
        if (m == null || !ItemEmpGunSC.isGun(gun)) {
            return;
        }
        if (!ItemEmpGunSC.hasModule(gun, m)) {
            say(p, "sc.emp.module.missing", moduleName(m));
            return;
        }
        cancelCharge(p, gun, false);
        ItemEmpGunSC.setModule(gun, m, false);                  // Capacity: setModule cuts the charge to the smaller buffer
        ItemStack out = new ItemStack(com.sc.init.ModItems.empModule, 1, m.ordinal());
        if (!p.inventory.addItemStackToInventory(out) && out.stackSize > 0) {
            p.dropPlayerItemWithRandomChoice(out, false);
        }
        say(p, "sc.emp.module.removed", moduleName(m));
    }

    /** Server: forget a player's stream / charge-up state (logout, death, server stop: null = everyone). */
    public static void forget(EntityPlayer p) {
        if (p == null) {
            USES.clear();
            ACTIVITY_SERVER.clear();
            SAID.clear();
            SCANNED.clear();
            COOLED.clear();
            return;
        }
        Use u = USES.remove(p);
        if (u != null && u.mode == EmpMode.SIPHON) {
            EmpSiphonSC.flush(p, u, EmpSC.now(p));              // the stream's EU / heat still reach the gun (saved after the logout event)
            EmpSiphonSC.fxOff(p, u);
        }
        ACTIVITY_SERVER.remove(p);
        SAID.remove(p);
        SCANNED.remove(p);
    }

    // ------------------------------------------------------------------ the charge-up and the shot (server)

    private static void chargeTick(EntityPlayerMP p, Use u, long now) {
        int t = (int) (now - u.start);
        if (p.isSneaking()) {
            cancelCharge(p, u.gun, true);                       // Shift during the charge-up
            return;
        }
        if (u.mode == EmpMode.PULSE) {
            if (u.lockedId != 0) {
                Entity e = p.worldObj.getEntityByID(u.lockedId);
                if (e == null || e.isDead || !EmpShotSC.inSight(p.worldObj, p, e, range(u.gun, u.mode))) {
                    if (++u.lost > EmpMathSC.PULSE_LOS_GRACE) {
                        USES.remove(p);
                        endUse(p, u.gun);
                        say(p, "sc.emp.lost");
                        return;
                    }
                } else {
                    u.lost = 0;
                }
            }
            if (t / 20 > u.hums) {                              // the hum grows, once a second
                u.hums = t / 20;
                SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_PULSE_HUM, EmpMathSC.PULSE_HUM_RANGE / 16F, 0.8F + t / 100F);
            }
        }
        if (t >= EmpMathSC.chargeTicks(u.mode, ItemEmpGunSC.upgraded(u.gun))) {
            fire(p, u, now);
        }
    }

    /** The charge-up is done: the shot. EU are spent only once the rules let it through (a miss spends them too). */
    private static void fire(EntityPlayerMP p, Use u, long now) {
        USES.remove(p);
        p.clearItemInUse();
        activity(p.worldObj).put(p, now);
        ItemStack gun = u.gun;
        EmpMode mode = u.mode;
        World w = p.worldObj;
        int why = startRefusal(gun, mode, now);
        if (why != EmpShotSC.START_OK) {
            sayStart(p, gun, why, now);
            endUse(p, gun);
            return;
        }
        int range = range(gun, mode);
        EmpShotSC.Hit h = null;
        if (mode == EmpMode.PULSE && u.lockedId != 0) {
            Entity e = w.getEntityByID(u.lockedId);
            if (e != null && !e.isDead && EmpShotSC.inSight(w, p, e, range)) {
                h = new EmpShotSC.Hit(e, EmpShotSC.centre(e));
            }
        }
        if (h == null) {
            h = EmpShotSC.aim(w, p, range);
        }
        Entity target = h.entity;
        if (target != null) {
            int r = refusal(p, target, true);
            if (r != EmpNetSC.OK) {
                say(p, "sc.emp.refuse." + r);
                endUse(p, gun);
                return;
            }
        }
        pay(p, gun, mode, now);
        SoundsSC.play(w, p.posX, p.posY, p.posZ, mode == EmpMode.PULSE ? EmpSC.SND_PULSE : EmpSC.SND_SHOT, 1F, 1F);
        int beam = mode == EmpMode.PULSE ? EmpNetSC.FX_PULSE : EmpNetSC.FX_POINT;
        if (target == null) {
            fx(p, beam, 0, h.at.xCoord, h.at.yCoord, h.at.zCoord);
            return;
        }
        double[] border = EmpRulesSC.screenAbsorbs(p, target, EmpMathSC.screenCost(mode));
        if (border != null) {
            fx(p, beam, target.getEntityId(), border[0], border[1], border[2]);
            fx(p, EmpNetSC.FX_ABSORBED, target.getEntityId(), border[0], border[1], border[2]);
            say(p, "sc.emp.absorbed");
            return;
        }
        fx(p, beam, target.getEntityId(), h.at.xCoord, h.at.yCoord, h.at.zCoord);
        boolean upgraded = ItemEmpGunSC.upgraded(gun);
        if (mode == EmpMode.PULSE && target instanceof EntityPlayer && EmpArmorSC.guardReflects((EntityPlayer) target, p.getRNG())) {
            fx(p, EmpNetSC.FX_REFLECTED, target.getEntityId(), p.posX, p.boundingBox.minY + p.height / 2, p.posZ);
            say(p, "sc.emp.reflected");
            if (EmpSC.wearsModArmour(p)) {                      // turned back on the shooter; without the mod's armour nothing happens
                EmpSC.HitResult back = EmpSC.applyHit(p, EmpMode.PULSE, upgraded, ALL_PIECES);
                if (back.anySuppressed()) {
                    suppressedFx(p, p);
                }
            }
            return;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer tp = (EntityPlayer) target;
            int[] pieces = mode == EmpMode.POINT
                    ? EmpMathSC.pointPieces(ItemEmpGunSC.zoneOf(gun),
                    EmpShotSC.heightShare(h.at.yCoord, target.boundingBox.minY, target.boundingBox.maxY))
                    : ALL_PIECES;
            report(p, tp, EmpSC.applyHit(tp, mode, upgraded, pieces), pieces);
        } else if (target instanceof EntityLivingBase) {
            EmpSC.applyNoArmour((EntityLivingBase) target);
            say(p, "sc.emp.noarmour");
        }
    }

    /** The shot's price: EU, the gun's heat, the Pulse cooldown. */
    private static void pay(EntityPlayerMP p, ItemStack gun, EmpMode mode, long now) {
        ItemEmpGunSC.discharge(gun, EmpMathSC.euCost(mode));
        if (mode == EmpMode.PULSE) {
            ItemEmpGunSC.setLastPulseAt(gun, now);
        }
        heatUp(p, gun, ItemEmpGunSC.heatOf(gun), EmpMathSC.gunHeat(mode, ItemEmpGunSC.hasModule(gun, EmpModule.COOLING)), now);
    }

    /** Writes `from + add` as the gun's heat (not while it is in use); the warning 2 s before and the overheat. */
    static void heatUp(EntityPlayer p, ItemStack gun, int from, int add, long now) {
        ItemEmpGunSC.setHeat(gun, from + add, now);
        heatNews(p, gun, from, from + add);
    }

    /** The overheat / its warning, once the heat went from `before` to `after` (written or still pending). */
    static void heatNews(EntityPlayer p, ItemStack gun, int before, int after) {
        boolean cooling = ItemEmpGunSC.hasModule(gun, EmpModule.COOLING);
        if (after >= EmpMathSC.GUN_HEAT_MAX && before < EmpMathSC.GUN_HEAT_MAX) {
            SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_OVERHEAT, 0.8F, 1F);
            say(p, "sc.emp.overheat", EmpMathSC.coolSeconds(EmpMathSC.GUN_HEAT_MAX));
        } else if (ItemEmpGunSC.warnOn(gun) && EmpMathSC.overheatWarn(after, cooling) && !EmpMathSC.overheatWarn(before, cooling)) {
            SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_OVERHEAT, 0.4F, 1.4F);
            say(p, "sc.emp.overheat.warn");
        }
    }

    /** What the hit did, to the shooter; sparks and the sound at the target. */
    private static void report(EntityPlayerMP p, EntityPlayer target, EmpSC.HitResult r, int[] pieces) {
        if (!r.anyArmour) {
            say(p, "sc.emp.noarmour");                          // applyHit gave the Slowness
            return;
        }
        int ticks = 0, immune = 0;
        for (int i = 0; i < 4; i++) {
            ticks = Math.max(ticks, r.ticks[i]);
            if (r.immune[i]) {
                immune = Math.max(immune, EmpSC.immuneTicks(target, i));
            }
        }
        if (r.anySuppressed()) {
            suppressedFx(p, target);
            say(p, "sc.emp.hit", target.getCommandSenderName(), new ChatComponentTranslation(pieceKey(pieces)), EmpShotSC.seconds(ticks));
        } else {
            say(p, "sc.emp.immune", EmpShotSC.seconds(immune));
        }
    }

    private static void suppressedFx(EntityPlayer shooter, EntityPlayer target) {
        SoundsSC.play(target.worldObj, target.posX, target.posY, target.posZ, EmpSC.SND_SUPPRESS, 0.8F, 1F);
        Vec3 c = EmpShotSC.centre(target);
        fx(shooter, EmpNetSC.FX_SUPPRESSED, target.getEntityId(), c.xCoord, c.yCoord, c.zCoord);
    }

    /** "шлем" / "нагрудник" / "ноги" / "вся броня" for the hit message. */
    private static String pieceKey(int[] pieces) {
        if (pieces.length >= 4) {
            return "sc.emp.piece.all";
        }
        if (pieces.length >= 2) {
            return "sc.emp.piece.legs";
        }
        return "sc.emp.piece." + pieces[0];
    }

    // ------------------------------------------------------------------ the aim scan (server)

    /** Every AIM_SCAN_TICKS with a ПЭ-1 in hand: the readout to the shooter, «ЭМИ-наведение» to a target with a Sensor chip. */
    private static void aimScan(EntityPlayerMP p, ItemStack gun, Use u) {
        if (p.playerNetServerHandler == null || p instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EmpNetSC.AimMessage m = new EmpNetSC.AimMessage();
        boolean streaming = u != null && u.mode == EmpMode.SIPHON;
        Entity target = streaming ? p.worldObj.getEntityByID(u.targetId)
                : EmpShotSC.aim(p.worldObj, p, range(gun, ItemEmpGunSC.modeOf(gun))).entity;
        if (target != null) {
            m.targetId = target.getEntityId();
            m.reason = refusal(p, target, false);
            if (target instanceof EntityPlayer) {
                readTarget(p, (EntityPlayer) target, m);
                if (EmpSC.hasSensorChip((EntityPlayer) target)) {
                    EmpSC.markAimed((EntityPlayer) target, p);
                }
            }
        }
        m.receiver = EmpSiphonSC.pickReceiver(p, gun, u);
        m.receiverRoom = m.receiver < 0 ? 0L : EmpSiphonSC.room(p, gun, u, m.receiver);
        m.streaming = streaming;
        m.received = streaming ? u.received : 0L;
        m.vented = streaming ? u.vented : 0L;
        m.heat = streaming ? EmpSiphonSC.liveHeat(u) : -1;
        m.throttled = streaming && u.throttled;
        if (!(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            EmpNetSC.channel().sendTo(m, p);
        }
    }

    /** The target's suit, suppressed / immune slots and (with the shooter's Sensor chip) its charge. */
    private static void readTarget(EntityPlayer shooter, EntityPlayer t, EmpNetSC.AimMessage m) {
        ArmorSuit suit = null;
        for (int i : new int[]{1, 0, 2, 3}) {
            ItemStack s = EmpSC.modPiece(t, i);
            if (s != null && suit == null) {
                suit = ((ItemArmorSC) s.getItem()).getSuit();
            }
            int sup = EmpSC.suppressedTicks(t, i), imm = EmpSC.immuneTicks(t, i);
            if (sup > 0) {
                m.slots |= 1 << i;
                m.suppressedTicks = Math.max(m.suppressedTicks, sup);
            } else if (imm > 0) {
                m.slots |= 1 << (4 + i);
                m.immuneTicks = Math.max(m.immuneTicks, imm);
            }
        }
        m.suit = suit == null ? -1 : suit.ordinal();
        m.chargePct = suit != null && EmpSC.hasSensorChip(shooter)
                ? EmpShotSC.pctOf(EmpSiphonSC.armourCharge(t), EmpSiphonSC.armourCapacity(t)) : -1;
    }

    // ------------------------------------------------------------------ shared helpers (package)

    static int range(ItemStack gun, EmpMode mode) {
        return EmpMathSC.range(mode, ItemEmpGunSC.hasModule(gun, EmpModule.FOCUS));
    }

    private static int startRefusal(ItemStack gun, EmpMode mode, long now) {
        // the Siphon's overheat is soft (2026-10-11): it starts throttled; Point / Pulse wait until below GUN_HEAT_RECOVER
        return EmpShotSC.startRefusal(ConfigSC.empEnabled, ItemEmpGunSC.modeEnabled(gun, mode), mode != EmpMode.SIPHON && ItemEmpGunSC.overheated(gun),
                ItemEmpGunSC.chargeOf(gun), EmpMathSC.euCost(mode), mode == EmpMode.PULSE ? ItemEmpGunSC.pulseCooldown(gun, now) : 0);
    }

    private static void sayStart(EntityPlayer p, ItemStack gun, int why, long now) {
        String key = EmpShotSC.startKey(why);
        if (why == EmpShotSC.START_OVERHEAT) {
            say(p, key, EmpMathSC.coolSeconds(ItemEmpGunSC.heatOf(gun)));
        } else if (why == EmpShotSC.START_NO_CHARGE) {
            say(p, key, EmpMathSC.euCost(ItemEmpGunSC.modeOf(gun)));
        } else if (why == EmpShotSC.START_COOLDOWN) {
            say(p, key, EmpShotSC.seconds(ItemEmpGunSC.pulseCooldown(gun, now)));
        } else if (key != null) {
            say(p, key);
        }
    }

    /** EmpRulesSC.refusal, and «не действует на игроков» from the config. */
    static int refusal(EntityPlayer shooter, Entity target, boolean fire) {
        int r = EmpRulesSC.refusal(shooter, target, fire);
        if (r == EmpNetSC.OK && target instanceof EntityPlayer && !ConfigSC.empAffectsPlayers) {
            r = EmpNetSC.NO_PLAYERS;
        }
        return r;
    }

    /**
     * `p` is using `stack` now. EntityPlayer.getItemInUse() is @SideOnly(CLIENT) - on a dedicated server it doesn't
     * exist (NoSuchMethodError); vanilla drops the item in use as soon as it isn't the held stack, so "in use and
     * held" is the same test on both sides.
     */
    static boolean inUse(EntityPlayer p, ItemStack stack) {
        return stack != null && p.isUsingItem() && p.getCurrentEquippedItem() == stack;
    }

    /** Server: ends the use on both sides - the client gets its inventory again (new stack objects: its itemInUse drops). */
    static void endUse(EntityPlayerMP p, ItemStack gun) {
        if (inUse(p, gun)) {
            p.clearItemInUse();
        }
        activity(p.worldObj).put(p, EmpSC.now(p));
        if (p.playerNetServerHandler != null && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            p.sendContainerToPlayer(p.inventoryContainer);
        }
    }

    /** Server: whatever runs is dropped (a new click, another slot): the stream writes what it owes, a charge-up costs nothing. */
    private static void drop(EntityPlayerMP p) {
        Use u = USES.get(p);
        if (u == null) {
            return;
        }
        if (u.mode == EmpMode.SIPHON) {
            EmpSiphonSC.stop(p, u, null);
        } else {
            USES.remove(p);
        }
    }

    static void fx(EntityPlayer shooter, int kind, int targetId, double x, double y, double z) {
        EmpNetSC.sendFx(shooter.worldObj, new EmpNetSC.FxMessage(kind, shooter.getEntityId(), targetId, x, y, z),
                shooter.posX, shooter.posY, shooter.posZ);
    }

    /** A chat line to the player, the same key at most once in SAY_COOLDOWN ticks. */
    static void say(EntityPlayer p, String key, Object... args) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        long now = EmpSC.now(p);
        Map<String, Long> said = SAID.get(p);
        if (said == null) {
            said = new HashMap<String, Long>();
            SAID.put(p, said);
        }
        Long at = said.get(key);
        if (at != null && now >= at && now - at < SAY_COOLDOWN) {
            return;
        }
        said.put(key, now);
        p.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }

    private static ChatComponentTranslation moduleName(EmpModule m) {
        return new ChatComponentTranslation("item." + com.sc.Reference.ASSETS + ".empModule." + m.key() + ".name");
    }

    private static Map<EntityPlayer, Long> activity(World w) {
        return w.isRemote ? ACTIVITY_CLIENT : ACTIVITY_SERVER;
    }
}
