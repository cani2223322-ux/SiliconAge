package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.IEmpUselessSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemDrillSC;
import com.sc.item.ItemWeaponSC;
import com.sc.mob.ai.EntityAIColossusFightSC;
import com.sc.mob.ai.EntityAIColossusNightSC;
import com.sc.util.Material;
import com.sc.util.OreEntry;
import com.sc.util.SiliconMaterial;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.7, rule ПС-37): «Кварцитовый колосс» (Quartzite Colossus) - natural armour 12; its
 * facets reflect arrows and halve beams. Neutral by day. A stomp within 4, crystals thrown 12 blocks (an instant hit after a
 * visible aim - no projectile entity, ПС-4). Melee from the side or behind, explosions x1.5, the mod's drill x3. The ПЭ-1 is
 * useless (IEmpUselessSC) - the lesson that it doesn't work on everything. HP 120. Drops: Quartzite 4..8, Silica Sand 4..8,
 * 50% crushed tantalum or platinum ore 1..3. The plateaus and the Ridges.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 18 in MobEntitiesSC), the
 * World constructor (EntityList), HP, IEmpUselessSC (the ПЭ-1 is useless). The rest - its AI, its numbers, its drops
 * (IFactionLootSC) - is package N's.
 * <p>
 * By day it hunts nobody (EntityAIColossusNightSC looks for a player only at night - RaidRulesSC.night) but answers a blow
 * (EntityAIHurtByTarget, calling the Colossi near). EntityAIColossusFightSC: a target within STOMP_RADIUS - the stomp wound up
 * STOMP_WINDUP ticks (ACT_STOMP), then STOMP_DAMAGE to everything living and not a Substrate hostile within STOMP_RADIUS that
 * stands within STOMP_DY of its feet, thrown up and away; a target THROW_MIN..THROW_RANGE off in sight - a crystal aimed
 * THROW_AIM ticks (ACT_THROW), then THROW_DAMAGE along the clear line at once (quartz sparks along it). Nothing knocks it back.
 * The blows it takes (damageMul): arrows bounce off (no damage - vanilla turns a refused arrow back, here faster; the archer is
 * its foe all the same), a beam gun's (a player's ItemWeaponSC in hand, a mob's gun) x BEAM_MUL, explosions x BLAST_MUL, a
 * player's drill of the mod's x DRILL_MUL, any other blow from within FRONT_HALF_ANGLE of its face x FRONT_MUL (the side and the
 * back - full). The census: the Quantum tier (ПС-41).
 */
public class EntityColossusSC extends EntitySubstrateMobSC implements IEmpUselessSC, IFactionLootSC {

    public static final String NAME = "colossus";
    public static final double HP = 120D;
    /** Natural armour (getTotalArmorValue). */
    public static final int NATURAL_ARMOUR = 12;
    /** ПС-37: the stomp - its wind-up, damage, cooldown (ticks), reach and how far up or down from its feet it reaches. */
    public static final int STOMP_WINDUP = 20, STOMP_DAMAGE = 6, STOMP_COOLDOWN = 60;
    public static final double STOMP_RADIUS = 4D, STOMP_DY = 2D;
    /** The crystal - its aim, damage, cooldown (ticks), the range it is thrown from (no nearer than THROW_MIN: there it stomps). */
    public static final int THROW_AIM = 20, THROW_DAMAGE = 7, THROW_COOLDOWN = 80;
    public static final double THROW_RANGE = 12D, THROW_MIN = 5D;
    /** Its facets and bulk (TODO(balance)): beams, explosions, the mod's drill, a blow from the front (degrees each side). */
    public static final float BEAM_MUL = 0.5F, BLAST_MUL = 1.5F, DRILL_MUL = 3F, FRONT_MUL = 0.7F;
    public static final double FRONT_HALF_ANGLE = 60D;
    /** A refused arrow flies back at a tenth of its speed (vanilla's EntityArrow); x this first - it bounces back at 0.4. */
    public static final double ARROW_BOUNCE = 4D;
    public static final double SPEED = 0.22D, FOLLOW = 24D;
    /** ПТ-1: Quartzite ore, Silica Sand, the chance of crushed tantalum or platinum ore. TODO(balance) */
    public static final int QUARTZITE_MIN = 4, QUARTZITE_MAX = 8, SAND_MIN = 4, SAND_MAX = 8, ORE_MIN = 1, ORE_MAX = 3;
    public static final float ORE_CHANCE = 0.5F;
    public static final int XP = 20;

    public EntityColossusSC(World world) {
        super(world);
        setSize(1.4F, 3.6F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIColossusFightSC(this));
        tasks.addTask(5, new EntityAIWander(this, 0.6D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 10F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAIColossusNightSC(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(THROW_DAMAGE);   // what WAILA-like tools show
    }

    /** Natural armour 12 (plus anything worn - nothing). */
    @Override
    public int getTotalArmorValue() {
        return Math.min(20, super.getTotalArmorValue() + NATURAL_ARMOUR);
    }

    /** ПС-41: it takes a Quantum tier's place in the census. */
    @Override
    public int censusTier() {
        return TIER_QUANTUM;
    }

    // ------------------------------------------------------------------ its blows (EntityAIColossusFightSC)

    /** Server: the stomp - STOMP_DAMAGE to everything living near its feet but the Substrate's hostiles, thrown up and away. */
    public void stomp() {
        if (worldObj.isRemote) {
            return;
        }
        double r = STOMP_RADIUS;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX - r, posY - STOMP_DY, posZ - r,
                posX + r, posY + STOMP_DY, posZ + r));
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            double dx = e.posX - posX, dz = e.posZ - posZ, d = Math.sqrt(dx * dx + dz * dz);
            if (e == this || !e.isEntityAlive() || MobCombatSC.hostileNative(e) || d > r) {
                continue;
            }
            if (e.attackEntityFrom(DamageSource.causeMobDamage(this), STOMP_DAMAGE)) {
                double k = d < 0.1D ? 0D : 0.5D / d;
                e.addVelocity(dx * k, 0.45D, dz * k);
                e.velocityChanged = true;
            }
        }
        playSound("random.explode", 0.8F, 0.5F);
        playSound("mob.irongolem.throw", 1.2F, 0.5F);
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            int bx = MathHelper.floor_double(posX), by = MathHelper.floor_double(posY - 0.2D), bz = MathHelper.floor_double(posZ);
            Block under = worldObj.getBlock(bx, by, bz);
            String crack = under.getMaterial().isSolid() ? "blockcrack_" + Block.getIdFromBlock(under) + "_" + worldObj.getBlockMetadata(bx, by, bz) : "cloud";
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8D;
                ws.func_147487_a(crack, posX + Math.cos(a) * 2.2D, posY + 0.1D, posZ + Math.sin(a) * 2.2D, 4, 0.4D, 0.1D, 0.4D, 0.15D);
            }
        }
    }

    /** Server: the crystal - THROW_DAMAGE to `t` at once (the caller checked the line), quartz sparks along it. */
    public void throwCrystal(EntityLivingBase t) {
        if (worldObj.isRemote || t == null) {
            return;
        }
        double x0 = posX, y0 = posY + height * 0.85D, z0 = posZ;
        double x1 = t.posX, y1 = t.posY + t.height * 0.6D, z1 = t.posZ;
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            int n = 14;
            for (int i = 1; i <= n; i++) {
                double k = i / (double) (n + 1), arc = Math.sin(k * Math.PI) * 0.8D;    // a little arc, as a thrown thing
                ws.func_147487_a("reddust", x0 + (x1 - x0) * k, y0 + (y1 - y0) * k + arc, z0 + (z1 - z0) * k, 0, 0.92D, 0.95D, 1.0D, 1.0D);
            }
            ws.func_147487_a("crit", x1, y1, z1, 12, 0.2D, 0.2D, 0.2D, 0.3D);
        }
        playSound("mob.irongolem.throw", 1F, 1.2F);
        worldObj.playSoundEffect(x1, y1, z1, "dig.glass", 1F, 0.8F);
        t.attackEntityFrom(DamageSource.causeMobDamage(this), THROW_DAMAGE);
    }

    /** No vanilla melee: the stomp and the crystal are the whole of it. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
    }

    // ------------------------------------------------------------------ the blows it takes

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src != null && src.getSourceOfDamage() instanceof EntityArrow) {
            bounce((EntityArrow) src.getSourceOfDamage());
            return false;                                       // the facets: no damage, the arrow turns back
        }
        return super.attackEntityFrom(src, amount * damageMul(src));
    }

    /** ПС-37: the multiplier of a blow by its kind (the attacker's hand and place; 1 for anything else). */
    public float damageMul(DamageSource src) {
        if (src == null) {
            return 1F;
        }
        if (src.isExplosion()) {
            return BLAST_MUL;
        }
        Entity direct = src.getSourceOfDamage();
        if (!(direct instanceof EntityLivingBase) || direct != src.getEntity()) {
            return 1F;                                          // fire, a fall, a projectile of some other kind
        }
        EntityLivingBase a = (EntityLivingBase) direct;
        ItemStack held = a.getHeldItem();
        if (held != null && held.getItem() instanceof ItemWeaponSC || !(a instanceof EntityPlayer) && MobCombatSC.gun(a) != null) {
            return BEAM_MUL;                                    // the facets scatter a beam
        }
        if (a instanceof EntityPlayer && held != null && held.getItem() instanceof ItemDrillSC) {
            return DRILL_MUL;                                   // the drill bites into the quartz
        }
        return MobCombatSC.inCone(posX, posZ, renderYawOffset, a.posX, a.posZ, FRONT_HALF_ANGLE) ? FRONT_MUL : 1F;
    }

    /** The arrow turns back off a facet (vanilla's refused arrow: x -0.1 after this). */
    private void bounce(EntityArrow a) {
        if (worldObj.isRemote) {
            return;
        }
        a.motionX *= ARROW_BOUNCE;
        a.motionY *= ARROW_BOUNCE;
        a.motionZ *= ARROW_BOUNCE;
        if (a.shootingEntity instanceof EntityLivingBase && a.shootingEntity != this) {
            setRevengeTarget((EntityLivingBase) a.shootingEntity);   // no damage, but it was a blow: neutral no more
        }
        worldObj.playSoundEffect(a.posX, a.posY, a.posZ, "random.anvil_land", 0.3F, 1.9F);
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("crit", a.posX, a.posY, a.posZ, 4, 0.05D, 0.05D, 0.05D, 0.1D);
        }
    }

    @Override
    public void knockBack(Entity by, float strength, double dx, double dz) {
    }

    @Override
    protected void fall(float distance) {
        super.fall(Math.max(0F, distance - 2F));
    }

    /** ПТ-1: Quartzite ore and Silica Sand, half the time crushed tantalum or platinum ore. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (ModBlocks.oreSC != null) {
            out.add(new ItemStack(ModBlocks.oreSC, QUARTZITE_MIN + rand.nextInt(QUARTZITE_MAX - QUARTZITE_MIN + 1), OreEntry.QUARTZITE.meta()));
        }
        if (ModItems.siliconMaterial != null) {
            ItemStack sand = ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND);
            if (sand != null) {
                sand.stackSize = SAND_MIN + rand.nextInt(SAND_MAX - SAND_MIN + 1);
                out.add(sand);
            }
        }
        if (rand.nextFloat() < ORE_CHANCE && ModItems.crushedOre != null) {
            ItemStack ore = ModItems.crushedOre.stackOf(rand.nextBoolean() ? Material.TANTALUM : Material.PLATINUM);
            if (ore != null) {
                ore.stackSize = ORE_MIN + rand.nextInt(ORE_MAX - ORE_MIN + 1);
                out.add(ore);
            }
        }
    }

    // ------------------------------------------------------------------ sounds: stone and glass (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.irongolem.walk";
    }

    @Override
    protected String getHurtSound() {
        return "dig.glass";
    }

    @Override
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.6F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 1F, 0.6F);
    }

    /** A little louder for its bulk (the sounds all play through playSound, at this volume). */
    @Override
    protected float getSoundVolume() {
        return 1.2F;
    }

    /** Server, SoundsSC for its wind-ups (EntityAIColossusFightSC). */
    public void windUpSound(boolean stomp) {
        SoundsSC.play(worldObj, posX, posY + height * 0.6D, posZ, stomp ? "machine.grind" : "mob.beam.aim", 0.8F, stomp ? 0.5F : 0.7F);
    }
}
