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
 * The transfer runs every STEP ticks (STEP x the per-tick rate): the target's pieces and the receivers change NBT
 * at that pace, not every tick. What goes into the gun waits in Use.pendingCharge (with the start's -5 000 EU) and
 * the stream's gun heat in Use.pendingHeat - written when the stream stops (rule 0.4.1). Once a second: the rules,
 * the EMP screen, the «Страж» reversal, EmpSC.streamSecond (heat, boil, «Энергия утекает!»), the gun's heat.
 */
public final class EmpSiphonSC {

    /** Ticks between transfers. */
    static final int STEP = 5;
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
        if (ItemEmpGunSC.overheated(gun) || ItemEmpGunSC.heatOf(gun) + u.pendingHeat >= EmpMathSC.GUN_HEAT_MAX) {
            stop(p, u, "sc.emp.siphon.stop.overheat");
            return;
        }
        // by the world clock, not by calls: a player's tick can come twice or not at all in a server tick (packet jitter)
        if ((u.secondAt < 0 || now - u.secondAt >= 20) && !second(p, u, target, u.secondAt >= 0)) {
            return;
        }
        if (u.stepAt < 0 || now - u.stepAt >= STEP) {
            u.stepAt = now;
            transfer(p, u, target);
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
            int before = ItemEmpGunSC.heatOf(u.gun) + u.pendingHeat;
            u.pendingHeat += EmpMathSC.gunHeat(EmpMode.SIPHON, ItemEmpGunSC.hasModule(u.gun, EmpModule.COOLING));
            int after = ItemEmpGunSC.heatOf(u.gun) + u.pendingHeat;
            if (after < EmpMathSC.GUN_HEAT_MAX) {
                EmpGunLogicSC.heatNews(p, u.gun, before, after);   // the warning; the overheat itself is told when the stream stops (flush)
            }
        }
        net.minecraft.util.Vec3 c = EmpShotSC.centre(target);
        EmpGunLogicSC.fx(p, EmpNetSC.FX_STREAM_ON, target.getEntityId(), c.xCoord, c.yCoord, c.zCoord);   // renewed: the client's 2 s timeout
        SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_SIPHON, 0.6F, 1F);
        return true;
    }

    /** STEP ticks of the stream. */
    private static void transfer(EntityPlayerMP p, EmpGunLogicSC.Use u, EntityPlayer target) {
        ItemStack gun = u.gun;
        boolean wide = ItemEmpGunSC.hasModule(gun, EmpModule.WIDE);
        int loss = EmpMathSC.siphonLossPct(ItemEmpGunSC.hasModule(gun, EmpModule.EFFICIENT), EmpSC.anySuppressed(target), ConfigSC.empSiphonLoss);
        if (u.reversed) {
            // «Страж»: the shooter's charged suit feeds the target's
            long avail = armourCharge(p), room = armourCapacity(target) - armourCharge(target);
            if (avail <= 0) {
                stop(p, u, "sc.emp.siphon.stop.empty");
                return;
            }
            if (room <= 0) {
                stop(p, u, "sc.emp.siphon.stop.full");
                return;
            }
            int rate = EmpMathSC.siphonRate(EmpMathSC.gunRate(wide), suitVoltage(target), suitVoltage(p), 0, ConfigSC.empSiphonRateMultiplier) * STEP;
            int[] step = EmpShotSC.streamStep(rate, avail, room, loss);
            int taken = drain(p, step[0]);
            ArmorLogicSC.chargeSuit(target, (int) Math.min(room, EmpMathSC.delivered(taken, loss)));
            return;
        }
        if (EmpShotSC.limitReached(ItemEmpGunSC.limitOf(gun), armourCharge(p), armourCapacity(p))) {
            stop(p, u, "sc.emp.siphon.stop.limit");
            return;
        }
        long avail = armourCharge(target);
        if (avail <= 0) {
            stop(p, u, "sc.emp.siphon.stop.empty");
            return;
        }
        int recv = pickReceiver(p, gun, u);
        if (recv < 0) {
            stop(p, u, "sc.emp.siphon.stop.full");
            return;
        }
        long room = room(p, gun, u, recv);
        int rate = EmpMathSC.siphonRate(EmpMathSC.gunRate(wide), receiverVoltage(p, recv), suitVoltage(target), EmpSC.defenseTier(target),
                ConfigSC.empSiphonRateMultiplier) * STEP;
        int[] step = EmpShotSC.streamStep(rate, avail, room, loss);
        int taken = drain(target, step[0]);
        int delivered = (int) Math.min(room, EmpMathSC.delivered(taken, loss));
        u.received += give(p, u, recv, delivered);
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

    /** Writes what the stream owes the gun: the start's EU, what it received, its heat. */
    static void flush(EntityPlayer p, EmpGunLogicSC.Use u, long now) {
        if (u.pendingCharge != 0) {
            ItemEmpGunSC.setCharge(u.gun, ItemEmpGunSC.chargeOf(u.gun) + u.pendingCharge);
            u.pendingCharge = 0;
        }
        if (u.pendingHeat != 0) {
            int from = ItemEmpGunSC.heatOf(u.gun);
            EmpGunLogicSC.heatUp(p, u.gun, from, u.pendingHeat, now);
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

    private static int receiverVoltage(EntityPlayer p, int recv) {
        if (recv == EmpMathSC.RECV_BATTERY) {
            ItemStack b = battery(p);
            return b == null ? 0 : ItemBatterySC.tierOf(b).getVoltage();
        }
        return recv == EmpMathSC.RECV_ARMOR ? suitVoltage(p) : EmpMathSC.CHARGE_TIER.getVoltage();
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

    /** The charge tier's voltage of the worn suit (the chestplate first), 0 without one. */
    private static int suitVoltage(EntityPlayer p) {
        for (int i : CHEST_FIRST) {
            ItemStack s = EmpSC.modPiece(p, i);
            if (s != null) {
                ArmorSuit suit = ((ItemArmorSC) s.getItem()).getSuit();
                return suit.chargeTier.getVoltage();
            }
        }
        return 0;
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
