package com.sc.mob;

import java.util.Random;

import com.sc.emp.ItemEmpGunSC;
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
import net.minecraft.entity.monster.IMob;
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
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4, rule ПС-10): the caps of the new mobs - the Quantum Blade 8, the Exo Blade 10 (the
 * Executioner's own 11 - EntityExecutionerSC), a Hollow Shell's fist 6, the Plasma Rifle's beam 7, the Pulse Emitter's 3
 * a ray (a burst of 3); every blow and beam of an armoured native times its damageMul() (a Commander's group, ПС-21).
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §7.1..§7.4): the caps of the new armoured ones are their own (the Sentinel's Singular Blade
 * 14 and Exo 10, «Затмение»'s 13 and its heavy blow, the Warlord's Quantum 8 and the Ion Cutter's 4); wear / dressSingular put on
 * the exact pieces they wear (the Warlord's Exo chestplate over Quantum, the Singular suits' level and colour scheme).
 */
public final class MobCombatSC {

    /** Fist: FIST + 0..FIST_SPREAD (3..4); a club (a stick) 4. */
    public static final int FIST = 3, FIST_SPREAD = 1, CLUB = 4;
    /**
     * Blades by BladeType ordinal, + 0..BLADE_SPREAD: Nano 6, Quantum 8, Exo 10 (Ф3, ПС-10: the cap - no spread; the
     * Executioner's 11 is its own), Singular 13..14 (the boss 16, Ф5). TODO(balance)
     */
    public static final int[] BLADE = {6, 8, 10, 13}, BLADE_SPREAD = {0, 0, 0, 1};
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

    /**
     * Ф4 (docs/plan-substrate-tech-f4.md §7.2..§7.4): spawning - one piece of `suit` worn in armour slot `armorType` (0 helmet ..
     * 3 boots) with the light `glow` (0 - the suit's own; the stack's NBT is set here and by the caller once, before the mob is
     * in the world - never again). @return the worn stack (null before the items exist)
     */
    public static ItemStack wear(EntityLiving mob, ArmorSuit suit, int armorType, int glow) {
        ItemArmorSC[] pieces = ModItems.ARMOR.get(suit);
        if (pieces == null || armorType < 0 || armorType > 3) {
            return null;
        }
        ItemStack s = new ItemStack(pieces[armorType]);
        if (glow != 0) {
            ItemArmorSC.setGlowColor(s, glow);
        }
        mob.setCurrentItemOrArmor(4 - armorType, s);
        return s;
    }

    /**
     * Ф4 (§7.3, §7.4): spawning - the full Singular suit of `level` (SingularLevel, 1..5) in the colour scheme `scheme`
     * (SingularScheme: the worn model's textures and its own animated light) - the Sentinel's «Пустота», «Затмение»'s «Багровая».
     * Set once here, before the mob is in the world (the stacks' NBT is never changed after).
     */
    public static void dressSingular(EntityLiving mob, int level, com.sc.util.SingularScheme scheme) {
        for (int t = 0; t < 4; t++) {
            ItemStack s = wear(mob, ArmorSuit.SINGULAR, t, 0);
            if (s != null) {
                com.sc.util.SingularLevel.setLevel(s, level);
                com.sc.util.SingularScheme.setScheme(s, scheme);
            }
        }
    }

    /** Ф4 (§2.7): the Forge data key of a camp's / a Vault's resident's home - {x, y, z, r: the structure's half size, l: the leash}. */
    public static final String HOME_NBT = "scHome";

    /**
     * Ф4 (§7.2, §7.3; package C's RuinWorldSC.settle writes the same): `mob`'s home at (x, y, z), the structure's half size `r`,
     * the leash `leash` - kept in its Forge data (saved with it) and set as vanilla's home area (not saved by vanilla).
     */
    public static void setHome(net.minecraft.entity.EntityCreature mob, int x, int y, int z, int r, int leash) {
        net.minecraft.nbt.NBTTagCompound h = new net.minecraft.nbt.NBTTagCompound();
        h.setInteger("x", x);
        h.setInteger("y", y);
        h.setInteger("z", z);
        h.setInteger("r", r);
        h.setInteger("l", leash);
        mob.getEntityData().setTag(HOME_NBT, h);
        mob.setHomeArea(x, y, z, Math.max(1, leash));
    }

    /**
     * Ф4: after a reload - vanilla's home area back from the Forge data (HOME_NBT: the leash "l", else "r", else `fallback`);
     * nothing without a home. @return it has a home now
     */
    public static boolean restoreHome(net.minecraft.entity.EntityCreature mob, int fallback) {
        if (!mob.getEntityData().hasKey(HOME_NBT)) {
            return false;
        }
        net.minecraft.nbt.NBTTagCompound h = mob.getEntityData().getCompoundTag(HOME_NBT);
        int leash = h.hasKey("l") ? h.getInteger("l") : h.hasKey("r") ? h.getInteger("r") : fallback;
        mob.setHomeArea(h.getInteger("x"), h.getInteger("y"), h.getInteger("z"), Math.max(1, leash > 0 ? leash : fallback));
        return true;
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

    // ------------------------------------------------------------------ Ф3

    /** Spawning (Ф3): the basic ПЭ-1 a Jammer carrier holds - only the look and the trophy's category (a mob's gun has no charge). */
    public static ItemStack pe1Stack() {
        return new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_BASIC);
    }

    /** It holds a ПЭ-1 (a carrier: the trophy category «ПЭ-1», the module's larger chance). */
    public static boolean holdsPe1(EntityLivingBase mob) {
        return ItemEmpGunSC.isGun(mob.getHeldItem());
    }

    /** One of the Substrate's hostiles (ISubstrateNativeSC and IMob): the census, the drops, «свои» for a Shell and a cleave. */
    public static boolean hostileNative(Entity e) {
        return e instanceof ISubstrateNativeSC && e instanceof IMob;
    }

    /**
     * `attacker` stands behind `mob` facing `yaw` (degrees, Minecraft's: 0 = +Z): more than 90 degrees off that way, seen
     * from above. Pure but for the positions.
     */
    public static boolean behind(Entity mob, Entity attacker, float yaw) {
        if (attacker == null || attacker == mob) {
            return false;
        }
        double dx = attacker.posX - mob.posX, dz = attacker.posZ - mob.posZ;
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        return dx * fx + dz * fz < 0D;
    }

    /**
     * (x, z) lies within `halfAngle` degrees of the way `yaw` faces from (ox, oz) (Minecraft's yaw, seen from above); the
     * point at the origin counts. Pure.
     */
    public static boolean inCone(double ox, double oz, float yaw, double x, double z, double halfAngle) {
        double dx = x - ox, dz = z - oz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            return true;
        }
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        return (dx * fx + dz * fz) / len >= Math.cos(Math.toRadians(halfAngle));
    }

    /** Minecraft's yaw (degrees) from (ox, oz) towards (x, z). */
    public static float yawTo(double ox, double oz, double x, double z) {
        return (float) (Math.atan2(z - oz, x - ox) * 180D / Math.PI) - 90F;
    }
}
