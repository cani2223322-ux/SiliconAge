package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.mob.ai.EntityAIPhantomFlySC;
import com.sc.mob.ai.EntityAIPhantomStrikeSC;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.5, rule ПС-35): «Наводка» (Induction Phantom) - no armour, all but invisible (seen
 * in the helmet's night vision and the Singular helmet's sensors). Passes through blocks. Puts out a helmet for 4 s (as a Point:
 * the immunity of 20 s after) and strikes 3 «Пробой». Any ПЭ-1 hit kills it at once; ordinary weapons do half. HP 20. Drops: an
 * Induction Thread 30%. The canyons and the Ridges.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 16 in MobEntitiesSC), the
 * World constructor (EntityList), HP, IEmpReactiveSC. The rest - its AI, its numbers, its drops (IFactionLootSC) - is package N's.
 * <p>
 * It flies without a path (noClip - rock is no wall to it; EntityAIPhantomFlySC steers it straight at its prey, else drifts a
 * few blocks over the ground), feels a survival player within SENSE through anything (looked for every SENSE_EVERY ticks) and
 * strikes within REACH once every STRIKE_EVERY ticks (EntityAIPhantomStrikeSC): the helmet out for HELMET_TICKS
 * (EmpSC.stormHit on slot 0 - only a helmet of the mod's, the usual immunity after it, no boiling) and BREAKDOWN of «Пробой»
 * (BreakdownDamageSC.of: through the armour and every shield). Never suffocates, drowns, burns or falls. isInvisible() always: the
 * mods that list or mark mobs skip it; NoiseClientSC draws it only to a player who sees it (the night vision of a helmet of the
 * mod's, the Singular helmet's Ш1 / Ш2), to anyone else a faint shimmer. A faint crackle is all one hears of it.
 */
public class EntityPhantomSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "phantom";
    public static final double HP = 20D;
    /** ПС-35: the strike - the helmet out (EmpSC.stormHit), «Пробой», once every so many ticks within REACH (centre to centre). */
    public static final int HELMET_TICKS = 80, BREAKDOWN = 3, STRIKE_EVERY = 40;
    public static final double REACH = 1.6D;
    /** ПБ-22: ordinary weapons do this share (a ПЭ-1 hit kills at once). */
    public static final float WEAPON_MUL = 0.5F;
    /** It feels a survival player this near - through rock, no line of sight - looked for every so many ticks (the AI rule). */
    public static final double SENSE = 16D;
    public static final int SENSE_EVERY = 10;
    /** Flight: top speed at its prey, drifting; how high over the ground it drifts. */
    public static final double FLY_SPEED = 0.25D, DRIFT_SPEED = 0.06D;
    public static final int DRIFT_MIN_UP = 1, DRIFT_MAX_UP = 6;
    /** ПТ-1: the Induction Thread's chance. TODO(balance) */
    public static final float THREAD_CHANCE = 0.3F;
    public static final int XP = 8;

    /** A ПЭ-1 hit is killing it: its blow goes through whole (attackEntityFrom). */
    private boolean empKill;

    public EntityPhantomSC(World world) {
        super(world);
        setSize(0.6F, 1.6F);
        noClip = true;
        isImmuneToFire = true;                                  // light in the wires: lava and fire pass through it too
        experienceValue = XP;
        tasks.addTask(1, new EntityAIPhantomStrikeSC(this));
        tasks.addTask(2, new EntityAIPhantomFlySC(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(SENSE);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BREAKDOWN);   // what WAILA-like tools show
    }

    // ------------------------------------------------------------------ the hunt

    @Override
    public void onLivingUpdate() {
        noClip = true;
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (t == null && (ticksExisted + getEntityId()) % SENSE_EVERY == 0) {
            EntityPlayer p = sense();
            if (p != null) {
                setAttackTarget(p);
            }
        } else if (t != null && ticksExisted % 20 == 0 && (!t.isEntityAlive() || t instanceof EntityPlayer && ((EntityPlayer) t).capabilities.disableDamage
                || t.worldObj != worldObj || getDistanceSqToEntity(t) > SENSE * SENSE * 4D)) {
            setAttackTarget(null);                              // gone, a creative one, far away: back to drifting
        }
    }

    /** The nearest survival player within SENSE (no line of sight: it feels the currents of the suit), or null. */
    private EntityPlayer sense() {
        EntityPlayer best = null;
        double bestD = SENSE * SENSE;
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.disableDamage) {
                continue;
            }
            double d = getDistanceSqToEntity(p);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Server (EntityAIPhantomStrikeSC): the strike on `t` - a player's helmet of the mod's goes out for HELMET_TICKS (the usual
     * immunity after: a second strike within 20 s leaves it on), then BREAKDOWN «Пробой». @return it landed
     */
    public boolean strike(EntityLivingBase t) {
        if (t == null || !t.isEntityAlive() || worldObj.isRemote) {
            return false;
        }
        if (t instanceof EntityPlayer) {
            EmpSC.stormHit((EntityPlayer) t, 0, HELMET_TICKS, 1, 0);
        }
        boolean hit = t.attackEntityFrom(BreakdownDamageSC.of(this), BREAKDOWN);
        SoundsSC.play(worldObj, t.posX, t.posY + t.height * 0.8D, t.posZ, "field.zap", 0.8F, 1.6F);
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("magicCrit", t.posX, t.posY + t.height * 0.85D, t.posZ, 10, 0.25D, 0.2D, 0.25D, 0.05D);
        }
        return hit;
    }

    /** Its strike is the AI's (strike); vanilla's melee never runs. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
    }

    // ------------------------------------------------------------------ ПБ-22: any ПЭ-1 hit kills it

    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode == null) {
            return false;
        }
        DamageSource src = shooter instanceof EntityPlayer ? DamageSource.causePlayerDamage((EntityPlayer) shooter)
                : shooter instanceof EntityLivingBase ? DamageSource.causeMobDamage((EntityLivingBase) shooter) : DamageSource.generic;
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + height * 0.5D, posZ, 18, 0.25D, 0.4D, 0.25D, 0.08D);
        }
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 1F, 1.4F);
        empKill = true;
        try {
            hurtResistantTime = 0;
            attackEntityFrom(src, getHealth() + 100F);
        } finally {
            empKill = false;
        }
        if (isEntityAlive()) {                                  // something cancelled the blow: dead all the same
            setHealth(0F);
            onDeath(src);
        }
        return true;
    }

    /** ПБ-22: ordinary weapons do WEAPON_MUL; the walls, water and falls nothing (it lives in the rock). */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src == DamageSource.inWall || src == DamageSource.drown || src == DamageSource.fall) {
            return false;
        }
        if (!empKill && src != DamageSource.outOfWorld) {
            amount *= WEAPON_MUL;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** ПТ-1: an Induction Thread now and then. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < THREAD_CHANCE) {
            ItemStack s = MobItemsSC.loot(ItemCombatLootSC.LootKind.INDUCTION_THREAD, 1);
            if (s != null) {
                out.add(s);
            }
        }
    }

    // ------------------------------------------------------------------ a ghost in the rock

    /** Both sides: it is never «seen» as a creature (minimaps, health bars); NoiseClientSC decides what each client draws. */
    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {
    }

    @Override
    public void knockBack(Entity by, float strength, double dx, double dz) {
    }

    @Override
    protected void fall(float distance) {
    }

    @Override
    protected void updateFallState(double dy, boolean onGround) {
    }

    @Override
    public boolean isOnLadder() {
        return false;
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean doesEntityNotTriggerPressurePlate() {
        return true;
    }

    /** No gravity, no collision (noClip): the motion its AI gives it, a little drag. */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.9D;
        motionY *= 0.9D;
        motionZ *= 0.9D;
        prevLimbSwingAmount = limbSwingAmount;
        limbSwingAmount = 0F;
    }

    // ------------------------------------------------------------------ sounds: a faint crackle (the mod's own, vanilla's)

    @Override
    protected String getLivingSound() {
        return Reference.ASSETS + ":field.zap";
    }

    @Override
    public int getTalkInterval() {
        return 160;
    }

    @Override
    protected String getHurtSound() {
        return "mob.blaze.hit";
    }

    @Override
    protected String getDeathSound() {
        return "random.fizz";
    }

    @Override
    protected float getSoundVolume() {
        return 0.25F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.8F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
    }
}
