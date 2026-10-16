package com.sc.mob;

import java.util.Random;

import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBladeSC;
import com.sc.item.ItemWeaponSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.4, rule ПС-4): what the Substrate's mobs wear and hit for - their own
 * capped numbers, never the item's. Vanilla's EntityMob.attackEntityAsMob takes the attackDamage attribute, and a held
 * item's attribute modifiers are added to it on every equipment change: a mod blade in a mob's hand would hit for
 * 12..40 (one blow kills a friend in Nano). So the mob classes override attackEntityAsMob with melee() below - the
 * number from the table, a zombie's knockback, no enchantments of the held item. Armour-breaking, absolute damage and
 * «Казнь» (BladeLogicSC) stay the player's alone (ПБ-9).
 */
public final class MobCombatSC {

    /** Fist: FIST + 0..FIST_SPREAD (3..4); a club (a stick) 4. */
    public static final int FIST = 3, FIST_SPREAD = 1, CLUB = 4;
    /** Blades by BladeType ordinal, + 0..BLADE_SPREAD: Nano 6, Quantum 8, Exo 10..11, Singular 13..14 (the boss 16, Ф5). TODO(balance) */
    public static final int[] BLADE = {6, 8, 10, 13}, BLADE_SPREAD = {0, 0, 1, 1};
    /** Beams by WeaponType ordinal: Ion Cutter 4, Pulse Emitter 3 a ray (a burst of 3, Ф3), Plasma Rifle 7 (Ф3). */
    public static final int[] BEAM = {4, 3, 7};
    /** Ticks between two melee blows (vanilla's EntityAIAttackOnCollide: 20). */
    public static final int MELEE_COOLDOWN = 20;

    private MobCombatSC() {
    }

    /** The held mod blade's type, or null. */
    public static BladeType blade(EntityLivingBase mob) {
        return ItemBladeSC.typeOf(mob.getHeldItem());
    }

    /** The held mod gun's type (Ion Cutter, Pulse Emitter, Plasma Rifle), or null. */
    public static WeaponType gun(EntityLivingBase mob) {
        ItemStack s = mob.getHeldItem();
        return s != null && s.getItem() instanceof ItemWeaponSC ? ((ItemWeaponSC) s.getItem()).getType() : null;
    }

    /** A club: a plain stick in hand. */
    public static boolean club(EntityLivingBase mob) {
        ItemStack s = mob.getHeldItem();
        return s != null && s.getItem() == Items.stick;
    }

    /**
     * The melee blow of a mob holding `held` (a blade, a club or nothing / a gun: the fist `fist` .. fist + FIST_SPREAD).
     * Pure but for the dice.
     */
    public static int meleeDamage(ItemStack held, int fist, Random rand) {
        BladeType b = ItemBladeSC.typeOf(held);
        if (b != null) {
            int i = b.ordinal();
            return BLADE[i] + (BLADE_SPREAD[i] > 0 ? rand.nextInt(BLADE_SPREAD[i] + 1) : 0);
        }
        if (held != null && held.getItem() == Items.stick) {
            return CLUB;
        }
        return fist + rand.nextInt(FIST_SPREAD + 1);
    }

    /** The beam of a gun type (0 for none). */
    public static int beamDamage(WeaponType t) {
        return t == null ? 0 : BEAM[t.ordinal()];
    }

    /**
     * EntityMob.attackEntityAsMob without the attribute and the held item's enchantments: `damage` as the mob's own blow,
     * a zombie's knockback (the vanilla one of attackEntityFrom), the target's thorns answering.
     */
    public static boolean melee(EntityLiving mob, Entity target, float damage) {
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(mob), damage);
        if (hit && target instanceof EntityLivingBase) {
            EnchantmentHelper.func_151384_a((EntityLivingBase) target, mob);   // thorns and the like of what the target wears
        }
        return hit;
    }

    /**
     * Spawning: `count` distinct random pieces of `suit` worn (slot 4 - armorType), each with the light `glow`
     * (ItemArmorSC.setGlowColor - the stacks' NBT is set once here and never touched again: every change would go to
     * every watcher with the whole NBT). Only the look: the armour itself is MobArmorSC.
     */
    public static void dress(EntityLiving mob, ArmorSuit suit, int count, int glow, Random rand) {
        ItemArmorSC[] pieces = ModItems.ARMOR.get(suit);
        if (pieces == null) {
            return;
        }
        int[] slots = {0, 1, 2, 3};
        for (int i = slots.length - 1; i > 0; i--) {         // shuffle, then the first `count`
            int j = rand.nextInt(i + 1);
            int t = slots[i];
            slots[i] = slots[j];
            slots[j] = t;
        }
        for (int i = 0; i < Math.min(4, count); i++) {
            ItemStack s = new ItemStack(pieces[slots[i]]);
            ItemArmorSC.setGlowColor(s, glow);
            mob.setCurrentItemOrArmor(4 - slots[i], s);
        }
    }

    /** Spawning: a fresh stack of the mod's blade, lit (a hilt in a mob's hand would look like a stick). */
    public static ItemStack litBlade(BladeType t) {
        ItemStack s = new ItemStack(ModItems.BLADES.get(t));
        ItemBladeSC.setEnabled(s, com.sc.util.BladeFeature.BLADE, true);
        return s;
    }

    public static ItemStack gunStack(WeaponType t) {
        return new ItemStack(ModItems.WEAPONS.get(t));
    }
}
