package com.sc.emp;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBatterySC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * ПЭ-1 «Сифон» (package A, docs/plan-pe1.md §4): the stream that pulls the EU of the target's suit (the mod's
 * armour only - no mobs, machines or other mods' armour) into the shooter's receivers: a battery of this mod in the
 * inventory, the shooter's own suit, the gun itself - in the K menu's order, minus the switched-off ones.
 *
 * Изменено 2026-10-11: the speed is a share of the target suit's FULL charge a second (EmpMathSC.siphonRate, by its
 * chestplate's suit); the target always loses the whole amount, the receivers get what fits after the loss and the
 * rest is vented (sparks, FX_VENTED) - the stream goes on with full receivers unless the «Предел» is «пока приёмник
 * не полон». The gun's overheat is soft: at GUN_HEAT_MAX the stream runs throttled (SIPHON_THROTTLED_PCT) while the
 * gun cools, back to full speed below GUN_HEAT_RECOVER (EmpMathSC.streamHeat).
 *
 * The transfer runs every STEP ticks (STEP x the per-tick rate): the target's pieces and the receivers change NBT
 * at that pace, not every tick. What goes into the gun waits in Use.pendingCharge (with the start's -5 000 EU) and
 * the stream's gun heat (tenths) in Use.pendingHeat - written when the stream stops (rule 0.4.1). Once a second: the
 * rules, the EMP screen, the «Страж» reversal, EmpSC.streamSecond (heat, boil, «Энергия утекает!»), the gun's heat.
 */
public final class EmpSiphonSC {

    /** Ticks between transfers. */
    static final int STEP = 5;
    /** Venting sparks at most this often, ticks. */
    private static final int VENT_FX_EVERY = 10;
    private static final int[] CHEST_FIRST = {1, 0, 2, 3};

    private EmpSiphonSC() {
    }

    /** The player in the crosshair within the siphon's range wearing the mod's armour, or null (both sides). */
    static EntityPlayer targetOf(World w, EntityPlayer shooter, ItemStack gun) {
        Entity e = EmpShotSC.aim(w, shooter, EmpGunLogicSC.range(gun, EmpMode.SIPHON)).entity;
        return e instanceof EntityPlayer && EmpSC.wearsModArmour((EntityPlayer) e) ? (EntityPlayer) e : null;
    }

    /** Server: the stream starts (5 000 EU owed by the gun) - or why not. @return started */
    static boolean start(EntityPlayerMP p, ItemStack gun, long now) {
        EntityPlayer target = targetOf(p.worldObj, p, gun);
        if (target == null) {
            EmpGunLogicSC.say(p, "sc.emp.siphon.notarget");
            return false;
        }
        int r = EmpGunLogicSC.refusal(p, target, true);
        if (r != EmpNetSC.OK) {
            EmpGunLogicSC.say(p, "sc.emp.refuse." + r);
            return false;
        }
        if (pickReceiver(p, gun, null) < 0) {
            EmpGunLogicSC.say(p, "sc.emp.noreceiver");
            return false;
        }
        if (armourCharge(target) <= 0) {
            EmpGunLogicSC.say(p, "sc.emp.siphon.stop.empty");
            return false;
        }
        EmpGunLogicSC.Use u = new EmpGunLogicSC.Use(gun, EmpMode.SIPHON, now);
        u.targetId = target.getEntityId();
        u.pendingCharge = -EmpMathSC.EU_SIPHON_START;
        u.throttled = ItemEmpGunSC.overheated(gun);                 // an overheated gun starts the stream weakened
        if (u.throttled) {
            EmpGunLogicSC.say(p, "sc.emp.siphon.throttled", EmpMathSC.SIPHON_THROTTLED_PCT, EmpMathSC.GUN_HEAT_RECOVER);
        }
        EmpGunLogicSC.USES.put(p, u);
        return true;
    }

    /** Server, every tick of a running stream (the gun is in use). */
    static void tick(EntityPlayerMP p, EmpGunLogicSC.Use u, long now) {
        ItemStack gun = u.gun;
        Entity e = p.worldObj.getEntityByID(u.targetId);
        EntityPlayer target = e instanceof EntityPlayer ? (EntityPlayer) e : null;
        if (target == null || target.isDead || target.getHealth() <= 0
                || !EmpShotSC.inSight(p.worldObj, p, target, EmpGunLogicSC.range(gun, EmpMode.SIPHON))) {
            boolean far = target == null || target.isDead || target.getHealth() <= 0
                    || EmpShotSC.eyes(p).distanceTo(EmpShotSC.centre(target)) > EmpGunLogicSC.range(gun, EmpMode.SIPHON) + target.width;
            stop(p, u, far ? "sc.emp.siphon.stop.range" : "sc.emp.siphon.stop.cover");
            return;
        }
        if (!ConfigSC.empEnabled) {
            stop(p, u, "sc.emp.siphon.stop.refused");
            return;
        }
        // by the world clock, not by calls: a player's tick can come twice or not at all in a server tick (packet jitter)
        if ((u.secondAt < 0 || now - u.secondAt >= 20) && !second(p, u, target, u.secondAt >= 0)) {
            return;
        }
        if (u.stepAt < 0 || now - u.stepAt >= STEP) {
            u.stepAt = now;
            transfer(p, u, target, now);
        }
    }

    /** The stream's second (`heats`: not the first - the gun heats for each second gone by). @return false when it stopped */
    private static boolean second(EntityPlayerMP p, EmpGunLogicSC.Use u, EntityPlayer target, boolean heats) {
        u.secondAt = EmpSC.now(p);
        if (EmpGunLogicSC.refusal(p, target, false) != EmpNetSC.OK) {
            stop(p, u, "sc.emp.siphon.stop.refused");
            return false;
        }
        double[] border = EmpRulesSC.screenAbsorbs(p, target, EmpMathSC.screenCost(EmpMode.SIPHON));
        if (border != null) {
            EmpGunLogicSC.fx(p, EmpNetSC.FX_ABSORBED, target.getEntityId(), border[0], border[1], border[2]);
            stop(p, u, "sc.emp.siphon.stop.screen");
            return false;
        }
        u.reversed = EmpArmorSC.guardReversesSiphon(target, p);
        EmpSC.streamSecond(target, p);
        if (heats) {
            heatSecond(p, u);
        }
        net.minecraft.util.Vec3 c = EmpShotSC.centre(target);
        EmpGunLogicSC.fx(p, EmpNetSC.FX_STREAM_ON, target.getEntityId(), c.xCoord, c.yCoord, c.zCoord);   // renewed: the client's 2 s timeout
        SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_SIPHON, u.throttled ? 0.35F : 0.6F, u.throttled ? 0.8F : 1F);
        return true;
    }

    /** A second of the gun's heat (pending, rule 0.4.1): the soft overheat, its end, the 2 s warning - told at once. */
    private static void heatSecond(EntityPlayerMP p, EmpGunLogicSC.Use u) {
        boolean cooling = ItemEmpGunSC.hasModule(u.gun, EmpModule.COOLING);
        int before = heatTenths(u);
        int[] h = EmpMathSC.streamHeat(before, u.throttled, cooling);
        u.pendingHeat += h[0] - before;
        boolean was = u.throttled;
        u.throttled = h[1] != 0;
        if (!was && u.throttled) {
            SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_OVERHEAT, 0.8F, 1F);
            EmpGunLogicSC.say(p, "sc.emp.siphon.throttled", EmpMathSC.SIPHON_THROTTLED_PCT, EmpMathSC.GUN_HEAT_RECOVER);
        } else if (was && !u.throttled) {
            EmpGunLogicSC.say(p, "sc.emp.siphon.recovered");
        } else if (!u.throttled && ItemEmpGunSC.warnOn(u.gun) && EmpMathSC.overheatWarnTenths(h[0], cooling)
                && !EmpMathSC.overheatWarnTenths(before, cooling)) {
            SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_OVERHEAT, 0.4F, 1.4F);
            EmpGunLogicSC.say(p, "sc.emp.overheat.warn");
        }
    }

    /** The gun's heat with what the stream hasn't written yet, tenths. */
    private static int heatTenths(EmpGunLogicSC.Use u) {
        return Math.max(0, Math.min(EmpMathSC.GUN_HEAT_MAX * 10, ItemEmpGunSC.heatOf(u.gun) * 10 + u.pendingHeat));
    }

    /** The running stream's gun heat, whole units (the aim readout: the HUD / K tab can't see the pending part). */
    static int liveHeat(EmpGunLogicSC.Use u) {
        return heatTenths(u) / 10;
    }

    /** STEP ticks of the stream. */
    private static void transfer(EntityPlayerMP p, EmpGunLogicSC.Use u, EntityPlayer target, long now) {
        ItemStack gun = u.gun;
        boolean wide = ItemEmpGunSC.hasModule(gun, EmpModule.WIDE);
        int loss = EmpMathSC.siphonLossPct(ItemEmpGunSC.hasModule(gun, EmpModule.EFFICIENT), EmpSC.anySuppressed(target), ConfigSC.empSiphonLoss);
        if (u.reversed) {
            // «Страж»: the shooter's charged suit feeds the target's - at the shooter's (the drained suit's) rate
            long avail = armourCharge(p), room = armourCapacity(target) - armourCharge(target);
            if (avail <= 0) {
                stop(p, u, "sc.emp.siphon.stop.empty");
                return;
            }
            if (room <= 0) {
                stop(p, u, "sc.emp.siphon.stop.full");
                return;
            }
            int rate = EmpMathSC.throttle(EmpMathSC.siphonRate(armourCapacity(p), suitOf(p), wide, 0, ConfigSC.empSiphonRateMultiplier, STEP),
                    u.throttled);
            int[] step = EmpShotSC.streamStep(rate, avail, room, loss, false);
            int taken = drain(p, step[0]);
            ArmorLogicSC.chargeSuit(target, (int) Math.min(room, EmpMathSC.delivered(taken, loss)));
            return;
        }
        int limit = ItemEmpGunSC.limitOf(gun);
        if (EmpShotSC.limitReached(limit, armourCharge(p), armourCapacity(p))) {
            stop(p, u, "sc.emp.siphon.stop.limit");
            return;
        }
        long avail = armourCharge(target);
        if (avail <= 0) {
            stop(p, u, "sc.emp.siphon.stop.empty");
            return;
        }
        boolean stopWhenFull = EmpShotSC.stopsWhenFull(limit);
        int recv = pickReceiver(p, gun, u);
        if (recv < 0 && stopWhenFull) {
            stop(p, u, "sc.emp.siphon.stop.full");
            return;
        }
        long room = recv < 0 ? 0L : stopWhenFull ? room(p, gun, u, recv) : totalRoom(p, gun, u);
        int rate = EmpMathSC.throttle(EmpMathSC.siphonRate(armourCapacity(target), suitOf(target), wide, EmpSC.defenseTier(target),
                ConfigSC.empSiphonRateMultiplier, STEP), u.throttled);
        int[] step = EmpShotSC.streamStep(rate, avail, room, loss, !stopWhenFull);
        int taken = drain(target, step[0]);
        int arrived = EmpMathSC.delivered(taken, loss);
        int want = (int) Math.min(room, arrived);
        int got = stopWhenFull ? give(p, u, recv, want) : giveAll(p, u, want);
        u.received += got;
        int vented = arrived - got;
        if (vented > 0) {
            u.vented += vented;
            if (u.ventFxAt < 0 || now - u.ventFxAt >= VENT_FX_EVERY || now < u.ventFxAt) {
                u.ventFxAt = now;
                EmpGunLogicSC.fx(p, EmpNetSC.FX_VENTED, target.getEntityId(), p.posX, p.posY, p.posZ);
            }
        }
    }

    /** Server: the stream ends - the gun gets its EU and heat, the effect stops, `key` (null: none) to the shooter. */
    static void stop(EntityPlayerMP p, EmpGunLogicSC.Use u, String key) {
        EmpGunLogicSC.USES.remove(p);
        long now = EmpSC.now(p);
        flush(p, u, now);
        fxOff(p, u);
        if (key != null) {
            EmpGunLogicSC.say(p, key);
        }
        EmpGunLogicSC.endUse(p, u.gun);
    }

    /**
     * Writes what the stream owes the gun: the start's EU, what it received, its heat and whether it ended overheated
     * (the soft overheat told itself while the stream ran - no second message here).
     */
    static void flush(EntityPlayer p, EmpGunLogicSC.Use u, long now) {
        if (u.pendingCharge != 0) {
            ItemEmpGunSC.setCharge(u.gun, ItemEmpGunSC.chargeOf(u.gun) + u.pendingCharge);
            u.pendingCharge = 0;
        }
        if (u.pendingHeat != 0 || u.throttled != ItemEmpGunSC.overheated(u.gun)) {
            ItemEmpGunSC.setStreamHeat(u.gun, heatTenths(u) / 10, u.throttled, now);
            u.pendingHeat = 0;
        }
    }

    static void fxOff(EntityPlayer p, EmpGunLogicSC.Use u) {
        EmpGunLogicSC.fx(p, EmpNetSC.FX_STREAM_OFF, u.targetId, p.posX, p.posY, p.posZ);
    }

    // ------------------------------------------------------------------ receivers

    /** The receiver the energy would go to now (EmpMathSC.RECV_*), -1 «Некуда принимать». `u`: the running stream or null. */
    static int pickReceiver(EntityPlayer p, ItemStack gun, EmpGunLogicSC.Use u) {
        boolean[] on = new boolean[EmpMathSC.RECV_COUNT];
        long[] room = new long[EmpMathSC.RECV_COUNT];
        for (int r = 0; r < EmpMathSC.RECV_COUNT; r++) {
            on[r] = ItemEmpGunSC.receiverEnabled(gun, r);
            room[r] = on[r] ? room(p, gun, u, r) : 0L;
        }
        return EmpShotSC.pickReceiver(ItemEmpGunSC.receiverOrder(gun), on, room);
    }

    /** Free room of a receiver, EU. */
    static long room(EntityPlayer p, ItemStack gun, EmpGunLogicSC.Use u, int recv) {
        if (recv == EmpMathSC.RECV_BATTERY) {
            ItemStack b = battery(p);
            return b == null ? 0L : ItemBatterySC.capacityOf(b) - ItemBatterySC.chargeOf(b);
        }
        if (recv == EmpMathSC.RECV_ARMOR) {
            return armourCapacity(p) - armourCharge(p);
        }
        int pending = u != null && u.gun == gun ? u.pendingCharge : 0;
        return Math.max(0L, (long) ItemEmpGunSC.capacityOf(gun) - ItemEmpGunSC.chargeOf(gun) - pending);
    }

    /** Free room of every switched-on receiver together, EU. */
    private static long totalRoom(EntityPlayer p, ItemStack gun, EmpGunLogicSC.Use u) {
        long sum = 0;
        for (int r : ItemEmpGunSC.receiverOrder(gun)) {
            if (r >= 0 && r < EmpMathSC.RECV_COUNT && ItemEmpGunSC.receiverEnabled(gun, r)) {
                sum += Math.max(0L, room(p, gun, u, r));
            }
        }
        return sum;
    }

    /** EU into the switched-on receivers in their order, each as much as it takes. @return EU taken in all */
    private static int giveAll(EntityPlayer p, EmpGunLogicSC.Use u, int eu) {
        int left = eu;
        for (int r : ItemEmpGunSC.receiverOrder(u.gun)) {
            if (left <= 0) {
                break;
            }
            if (r < 0 || r >= EmpMathSC.RECV_COUNT || !ItemEmpGunSC.receiverEnabled(u.gun, r)) {
                continue;
            }
            long room = room(p, u.gun, u, r);
            if (room > 0) {
                left -= give(p, u, r, (int) Math.min(room, left));
            }
        }
        return eu - left;
    }

    /** EU into the receiver. @return EU it took */
    private static int give(EntityPlayer p, EmpGunLogicSC.Use u, int recv, int eu) {
        if (eu <= 0) {
            return 0;
        }
        if (recv == EmpMathSC.RECV_BATTERY) {
            ItemStack b = battery(p);
            int left = eu;
            for (int i = 0; i <= STEP && left > 0 && b != null; i++) {     // ItemBatterySC.charge takes its rate a call
                int moved = ItemBatterySC.charge(b, left);
                if (moved <= 0) {
                    break;
                }
                left -= moved;
            }
            return eu - left;
        }
        if (recv == EmpMathSC.RECV_ARMOR) {
            return ArmorLogicSC.chargeSuit(p, eu);
        }
        u.pendingCharge += eu;                                   // the gun: written when the stream stops
        return eu;
    }

    /** The first battery of this mod in the main inventory that isn't full, or null. */
    private static ItemStack battery(EntityPlayer p) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (ItemBatterySC.isBattery(s) && ItemBatterySC.chargeOf(s) < ItemBatterySC.capacityOf(s)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the suits

    /** EU in the worn pieces of the mod's armour. */
    static long armourCharge(EntityPlayer p) {
        long sum = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = EmpSC.modPiece(p, i);
            if (s != null) {
                sum += ItemArmorSC.chargeOf(s);
            }
        }
        return sum;
    }

    /** The full charge of the worn pieces of the mod's armour (their max capacity together). */
    static long armourCapacity(EntityPlayer p) {
        long sum = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = EmpSC.modPiece(p, i);
            if (s != null) {
                sum += ItemArmorSC.capacityOf(s);
            }
        }
        return sum;
    }

    /** The suit that sets the stream's speed: the chestplate's, else the best worn piece's; null without the mod's armour. */
    static ArmorSuit suitOf(EntityPlayer p) {
        ItemStack chest = EmpSC.modPiece(p, 1);
        if (chest != null) {
            return ((ItemArmorSC) chest.getItem()).getSuit();
        }
        ArmorSuit best = null;
        for (int i = 0; i < 4; i++) {
            ItemStack s = EmpSC.modPiece(p, i);
            if (s != null) {
                ArmorSuit suit = ((ItemArmorSC) s.getItem()).getSuit();
                if (best == null || suit.ordinal() > best.ordinal()) {
                    best = suit;
                }
            }
        }
        return best;
    }

    /** Takes up to `eu` from the worn pieces, the chestplate first. @return EU taken */
    private static int drain(EntityPlayer p, int eu) {
        int left = eu;
        for (int i : CHEST_FIRST) {
            ItemStack s = EmpSC.modPiece(p, i);
            if (s != null && left > 0) {
                left -= ItemArmorSC.discharge(s, left);
            }
        }
        return eu - left;
    }
}
