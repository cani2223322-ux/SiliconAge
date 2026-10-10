package com.sc.emp;

import java.util.EnumSet;
import java.util.Random;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;
import com.sc.util.SoundsSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * PACKAGE B (docs/plan-pe1-tech.md): the armour's side of ПЭ-1 that isn't plain state - which functions a
 * suppression switches off, the emergency reboot and the Singular «Страж» reflection. (The effects themselves -
 * functions, chips, plating, flight, boil - are hooked into ArmorLogicSC / ItemArmorSC / CommonEventHandler /
 * ArmorGasSC, asking this class and EmpSC.)
 */
public final class EmpArmorSC {

    /**
     * Moving the wearer: off while the leggings or the boots are suppressed, even when the function sits in the
     * chestplate (the flight, the engine boost). Leg functions are listed too, though their own piece covers them.
     */
    private static final EnumSet<ArmorFeature> MOVEMENT = EnumSet.of(ArmorFeature.FLIGHT, ArmorFeature.GRAV_FLIGHT,
            ArmorFeature.BOOSTER, ArmorFeature.STEP_ASSIST, ArmorFeature.SPEED, ArmorFeature.DASH, ArmorFeature.PHASE_DASH,
            ArmorFeature.STABILIZER, ArmorFeature.FALL_DAMPING, ArmorFeature.JUMP, ArmorFeature.WATER_WALK,
            ArmorFeature.ANTIGRAV, ArmorFeature.GRAV_STRIKE);

    private EmpArmorSC() {
    }

    // ------------------------------------------------------------------ what a suppression switches off (both sides)

    /** The function belongs to the moving group the leggings / boots gate. */
    public static boolean movement(ArmorFeature f) {
        return MOVEMENT.contains(f);
    }

    /**
     * The ПЭ-1 has the function off (ArmorLogicSC.active): its own piece is suppressed - but breathing (AIR) never
     * goes - or it moves the wearer and the leggings or the boots are suppressed.
     */
    public static boolean featureSuppressed(EntityPlayer p, ArmorFeature f) {
        if (p == null || f == ArmorFeature.AIR) {
            return false;
        }
        if (EmpSC.isSuppressed(p, f.piece)) {
            return true;
        }
        return movement(f) && (EmpSC.isSuppressed(p, 2) || EmpSC.isSuppressed(p, 3));
    }

    /** The chestplate is suppressed: its chips give nothing, cost nothing and don't heat (as in emergency mode). */
    public static boolean chipsSuppressed(EntityPlayer p) {
        return EmpSC.isSuppressed(p, 1);
    }

    /** A switched-on flight was cut by a suppression (the chestplate, the leggings or the boots): flightCutReason's key. */
    public static boolean flightCut(EntityPlayer p) {
        return featureSuppressed(p, ArmorFeature.FLIGHT);         // the same pieces for both flights
    }

    // ------------------------------------------------------------------ server: the hit's news to the target

    /**
     * Server, after EmpSC.applyHit (the core or package A calls it): «Ваша броня подавлена ЭМИ: шлем на 13 с» to the
     * target - the pieces that got a suppression, the longest time. Nothing when none did (immune / no armour).
     */
    public static void notifySuppressed(EntityPlayer target, EmpSC.HitResult r) {
        if (target == null || r == null || target.worldObj.isRemote || !r.anySuppressed()) {
            return;
        }
        int n = 0, max = 0;
        for (int i = 0; i < 4; i++) {
            if (r.ticks[i] > 0) {
                n++;
                max = Math.max(max, r.ticks[i]);
            }
        }
        IChatComponent pieces;
        if (n == 4) {
            pieces = new ChatComponentTranslation("sc.emp.piece.all");
        } else {
            pieces = new ChatComponentText("");
            boolean first = true;
            for (int i = 0; i < 4; i++) {
                if (r.ticks[i] > 0) {
                    if (!first) {
                        pieces.appendText(", ");
                    }
                    pieces.appendSibling(new ChatComponentTranslation("sc.emp.piece." + i));
                    first = false;
                }
            }
        }
        target.addChatComponentMessage(new ChatComponentTranslation("sc.emp.suppressed.you", pieces, seconds(max)));
    }

    private static int seconds(int ticks) {
        return (ticks + 19) / 20;
    }

    // ------------------------------------------------------------------ the emergency reboot

    /**
     * Server (EmpNetSC.EMERGENCY_REBOOT): nothing suppressed - refused, nothing paid; on cooldown - refused; a set
     * with an argon tank (Quantum and up) pays REBOOT_ARGON_MB argon, the Nano suit none (TODO(balance)); and
     * REBOOT_CHARGE_SHARE of the suit's charge. Either missing - refused, nothing paid. Then EmpSC.clearSuppression.
     */
    public static void emergencyReboot(EntityPlayerMP p) {
        if (p == null || p.worldObj.isRemote) {
            return;
        }
        if (!EmpSC.anySuppressed(p)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.nothing"));
            return;
        }
        int cd = EmpSC.rebootCooldownTicks(p);
        if (cd > 0) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.cooldown", seconds(cd)));
            return;
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        int argon = rebootArgon(worn);
        if (argon > 0 && ArmorGasSC.amountOf(worn, Gas.ARGON) < argon) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.noargon", argon));
            return;
        }
        if (!ArmorLogicSC.canPaySuitShare(p, EmpMathSC.REBOOT_CHARGE_SHARE)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.nocharge", Math.round(EmpMathSC.REBOOT_CHARGE_SHARE * 100)));
            return;
        }
        if (argon > 0 && !ArmorGasSC.drainExact(worn, Gas.ARGON, argon)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.noargon", argon));
            return;
        }
        ArmorLogicSC.paySuitShare(p, EmpMathSC.REBOOT_CHARGE_SHARE);
        EmpSC.clearSuppression(p);
        SoundsSC.play(p.worldObj, p.posX, p.posY, p.posZ, EmpSC.SND_REBOOT, 1F, 1F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.emp.reboot.done"));
    }

    /** Argon the reboot costs for the set `worn`: REBOOT_ARGON_MB with an argon tank in it, 0 without (Nano). */
    public static int rebootArgon(ItemStack[] worn) {
        return ArmorGasSC.capacityOf(worn, Gas.ARGON) > 0 ? EmpMathSC.REBOOT_ARGON_MB : 0;
    }

    // ------------------------------------------------------------------ «Страж» (docs/plan-pe1-tech.md §2, open question 1)

    /**
     * The armour has no «Страж» branch (only the Singular blade does); the closest is the Singular chestplate's
     * level-5 branch Б «Гравитационный купол». This one predicate decides it - change the condition here.
     * A suppressed chestplate doesn't guard.
     */
    private static boolean guardian(EntityPlayer p) {
        ItemStack chest = EmpSC.modPiece(p, 1);
        return chest != null && ((ItemArmorSC) chest.getItem()).getSuit() == ArmorSuit.SINGULAR
                && SingularLevel.branchChoice(chest, 5) == SingularLevel.BRANCH_B && !EmpSC.isSuppressed(p, 1);
    }

    /** Server, before a Pulse lands (package A asks): the target's Singular «Страж» turns it back (EmpMathSC.GUARD_REFLECT_PCT). */
    public static boolean guardReflects(EntityPlayer target, Random rand) {
        return target != null && guardian(target) && rand.nextInt(100) < EmpMathSC.GUARD_REFLECT_PCT;
    }

    /** Share of its capacity the shooter's armour must hold for the «Страж» to turn the siphon back. */
    public static final float GUARD_REVERSE_MIN_CHARGE = 0.10F;

    /** Server, each stream second (package A asks): the «Страж» target reverses the siphon - the shooter's charged armour feeds it. */
    public static boolean guardReversesSiphon(EntityPlayer target, EntityPlayer shooter) {
        if (target == null || shooter == null || !guardian(target)) {
            return false;
        }
        long charge = 0, cap = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = EmpSC.modPiece(shooter, i);
            if (s != null) {
                charge += ItemArmorSC.chargeOf(s);
                cap += ItemArmorSC.capacityOf(s);
            }
        }
        return cap > 0 && charge > cap * (double) GUARD_REVERSE_MIN_CHARGE;
    }
}
