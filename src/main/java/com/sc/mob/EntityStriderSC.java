package com.sc.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.hazard.EmpStormSC;
import com.sc.mob.ai.EntityAIStriderArcSC;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.8, rule ПС-38): «Дуговой ходок» (Arc Strider) - no armour, a tripod 4 blocks high.
 * Only in an EMP storm. A chain lightning of 5 that jumps to 3 targets in the mod's armour (never to one without it); arrows
 * pass through the arc. A ПЭ-1 Pulse collapses the arc for 5 s: it falls and takes x2 damage; water on the electrode - 8. HP 60.
 * Drops: Plasma Condensate 50%, a lot of experience.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 19 in MobEntitiesSC), the
 * World constructor (EntityList), HP, IEmpReactiveSC. The rest - its AI, its numbers, its drops (IFactionLootSC) - is package N's.
 * <p>
 * It hunts only what its lightning can reach: a survival player wearing a piece of the mod's armour (charged - wired) within
 * ARC_RANGE (vanilla's nearest-in-sight with that selector), or whoever hit it if that one is wired too. EntityAIStriderArcSC
 * charges ARC_CHARGE ticks (ACT_ARC: its arc swells - every client sees it) and lets the chain go (arc): ARC_DAMAGE to the target
 * and up to CHAIN_JUMPS more - players in the mod's armour and armoured mobs wearing a piece of it, each within CHAIN_STEP of the
 * last struck, the nearest first, nobody twice; a target without the mod's armour is never struck. A ПЭ-1 Pulse collapses the
 * arc (ACT_COLLAPSED) for COLLAPSE_TICKS: it lies down, no move, no lightning, every blow x COLLAPSED_MUL. While the arc stands
 * an arrow flies on through it (no damage); collapsed it hits as usual. Water on the electrode - a bucket (empty but in creative)
 * or standing in water (once every WATER_EVERY ticks): WATER_DAMAGE. A creature of the storm: DISPERSE_TICKS after the storm is
 * over (EmpStormSC.active false - counted once a second) it crumbles into sparks, no drops (setDead). The census: the Quantum
 * tier (ПС-41).
 */
public class EntityStriderSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "strider";
    public static final double HP = 60D;
    /** ПС-38: the chain lightning - its charge, damage, jumps past the first, cooldown (ticks), the first target's range, a jump. */
    public static final int ARC_CHARGE = 20, ARC_DAMAGE = 5, CHAIN_JUMPS = 3, ARC_COOLDOWN = 60;
    public static final double ARC_RANGE = 16D, CHAIN_STEP = 6D;
    /** ПБ-24: a Pulse collapses its arc this long; collapsed it takes every blow x this. */
    public static final int COLLAPSE_TICKS = 100;
    public static final float COLLAPSED_MUL = 2F;
    /** Water on its electrode: the damage; standing in water - again only after so many ticks. */
    public static final int WATER_DAMAGE = 8, WATER_EVERY = 40;
    /** ПФ-6: it crumbles this long after the storm is over. */
    public static final int DISPERSE_TICKS = 600;
    /** A refused arrow flies back at a tenth of its speed (vanilla's EntityArrow); x -10 first - it flies on as it came. */
    private static final double ARROW_THROUGH = -10D;
    public static final double SPEED = 0.24D;
    /** ПТ-1: Plasma Condensate's chance. TODO(balance) */
    public static final float CONDENSATE_CHANCE = 0.5F;
    public static final int XP = 40;
    private static final String NBT_CALM = "scCalm";

    /** Wired: a survival player wearing a piece of the mod's armour, or an armoured mob wearing one (both sides: the worn stacks). */
    public static final IEntitySelector WIRED = new IEntitySelector() {
        @Override
        public boolean isEntityApplicable(Entity e) {
            return wired(e);
        }
    };

    private int collapseLeft, calm, wetCooldown;

    public EntityStriderSC(World world) {
        super(world);
        setSize(1.2F, 4.0F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIStriderArcSC(this));
        tasks.addTask(5, new EntityAIWander(this, 0.7D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 12F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false) {
            @Override
            public boolean shouldExecute() {
                return wired(taskOwner.getAITarget()) && super.shouldExecute();
            }
        });
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 10, true, false, WIRED));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(ARC_RANGE + 8D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.6D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(ARC_DAMAGE);   // what WAILA-like tools show
    }

    /** ПС-41: it takes a Quantum tier's place in the census. */
    @Override
    public int censusTier() {
        return TIER_QUANTUM;
    }

    /** `e` is wired: a survival player wearing a piece of the mod's armour, or an armoured mob wearing one. */
    public static boolean wired(Entity e) {
        if (e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) e;
            return p.isEntityAlive() && !p.capabilities.disableDamage && EmpSC.wearsModArmour(p);
        }
        return e instanceof IArmoredMobSC && e instanceof EntityLivingBase && ((EntityLivingBase) e).isEntityAlive()
                && MobAnalysisSC.modPieces((EntityLivingBase) e) > 0;
    }

    // ------------------------------------------------------------------ the arc

    /** Both sides: the arc is collapsed (it lies down). */
    public boolean collapsed() {
        return mobAction() == ACT_COLLAPSED;
    }

    /**
     * Server (EntityAIStriderArcSC): the chain lightning from its electrode - ARC_DAMAGE to `first`, then the nearest wired one
     * within CHAIN_STEP of the last struck, up to CHAIN_JUMPS times, nobody twice. Sparks along each jump, the crack at each.
     * @return how many it struck
     */
    public int arc(EntityLivingBase first) {
        if (worldObj.isRemote || first == null || !wired(first)) {
            return 0;
        }
        List<EntityLivingBase> struck = new ArrayList<EntityLivingBase>();
        double fx = posX, fy = posY + height * 0.95D, fz = posZ;
        EntityLivingBase t = first;
        while (t != null) {
            line(fx, fy, fz, t.posX, t.posY + t.height * 0.6D, t.posZ);
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causeMobDamage(this), ARC_DAMAGE);
            SoundsSC.play(worldObj, t.posX, t.posY + 1D, t.posZ, "field.zap", 1F, 0.9F + rand.nextFloat() * 0.3F);
            struck.add(t);
            if (struck.size() > CHAIN_JUMPS) {
                break;
            }
            fx = t.posX;
            fy = t.posY + t.height * 0.6D;
            fz = t.posZ;
            t = nextJump(t, struck);
        }
        playSound("ambient.weather.thunder", 0.6F, 1.6F);
        return struck.size();
    }

    /** The nearest wired one within CHAIN_STEP of `from` not struck yet (not this strider), or null. */
    private EntityLivingBase nextJump(EntityLivingBase from, List<EntityLivingBase> struck) {
        double r = CHAIN_STEP;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, from.boundingBox.expand(r, r, r));
        EntityLivingBase best = null;
        double bestD = r * r;
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == this || struck.contains(e) || !wired(e)) {
                continue;
            }
            double d = e.getDistanceSqToEntity(from);
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** A jump's sparks: blue-white dust along a jagged line, to the players near. */
    private void line(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (!(worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) worldObj;
        int n = 10;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1), j = i == n ? 0D : 0.25D;
            ws.func_147487_a("reddust", x0 + (x1 - x0) * k + (rand.nextDouble() - 0.5D) * j, y0 + (y1 - y0) * k + (rand.nextDouble() - 0.5D) * j,
                    z0 + (z1 - z0) * k + (rand.nextDouble() - 0.5D) * j, 0, 0.55D, 0.85D, 1.0D, 1.0D);
        }
        ws.func_147487_a("fireworksSpark", x1, y1, z1, 6, 0.15D, 0.15D, 0.15D, 0.08D);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        if (collapseLeft > 0) {
            motionX = 0D;
            motionZ = 0D;
            if (--collapseLeft <= 0) {
                setMobAction(ACT_NONE);
                SoundsSC.play(worldObj, posX, posY + 2D, posZ, "power.on", 0.9F, 0.8F);
            } else if (collapseLeft % 10 == 0 && worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("smoke", posX, posY + 0.5D, posZ, 3, 0.6D, 0.2D, 0.6D, 0.01D);
            }
        }
        if (wetCooldown > 0) {
            wetCooldown--;
        } else if (isInWater()) {
            douse(DamageSource.drown);
        }
        if (ticksExisted % 20 == 0) {
            calm = EmpStormSC.active(worldObj) ? 0 : calm + 20;
            if (calm >= DISPERSE_TICKS) {
                disperse();
            }
        }
    }

    /** The storm is long over: it crumbles into sparks, nothing left behind. */
    private void disperse() {
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            ws.func_147487_a("fireworksSpark", posX, posY + height * 0.5D, posZ, 40, 0.5D, height * 0.4D, 0.5D, 0.1D);
            ws.func_147487_a("smoke", posX, posY + height * 0.5D, posZ, 12, 0.5D, height * 0.4D, 0.5D, 0.02D);
        }
        SoundsSC.play(worldObj, posX, posY + 2D, posZ, "power.off", 1F, 0.6F);
        setDead();
    }

    /** Collapsed, it does nothing at all; else the usual. */
    @Override
    protected void updateAITasks() {
        if (collapsed()) {
            return;
        }
        super.updateAITasks();
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (collapsed()) {
            super.moveEntityWithHeading(0F, 0F);
            return;
        }
        super.moveEntityWithHeading(strafe, forward);
    }

    /** No vanilla melee: the lightning is the whole of it. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
    }

    // ------------------------------------------------------------------ the blows it takes, water, ПБ-24

    /** Arrows fly on through a standing arc; collapsed every blow x COLLAPSED_MUL. */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src != null && src.getSourceOfDamage() instanceof EntityArrow && !collapsed()) {
            EntityArrow a = (EntityArrow) src.getSourceOfDamage();
            if (!worldObj.isRemote) {
                a.motionX *= ARROW_THROUGH;                     // vanilla's refused arrow: x -0.1 after this - on as it came
                a.motionY *= ARROW_THROUGH;
                a.motionZ *= ARROW_THROUGH;
            }
            return false;
        }
        if (collapsed()) {
            amount *= COLLAPSED_MUL;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Server: water on the electrode - WATER_DAMAGE from `src` (once in WATER_EVERY ticks). @return it hurt */
    public boolean douse(DamageSource src) {
        if (worldObj.isRemote || wetCooldown > 0 || !isEntityAlive()) {
            return false;
        }
        wetCooldown = WATER_EVERY;
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + height * 0.9D, posZ, 20, 0.3D, 0.3D, 0.3D, 0.1D);
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + height * 0.9D, posZ, 6, 0.3D, 0.2D, 0.3D, 0.02D);
        }
        playSound("random.fizz", 1F, 0.8F);
        hurtResistantTime = 0;
        return attackEntityFrom(src, WATER_DAMAGE);
    }

    /** A water bucket on its electrode: WATER_DAMAGE, the bucket empty (but in creative). */
    @Override
    protected boolean interact(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        if (held == null || held.getItem() != Items.water_bucket) {
            return super.interact(p);
        }
        if (!worldObj.isRemote && douse(DamageSource.causePlayerDamage(p)) && !p.capabilities.isCreativeMode) {
            p.inventory.setInventorySlotContents(p.inventory.currentItem, new ItemStack(Items.bucket));
        }
        return true;
    }

    /** ПБ-24: a Pulse collapses the arc for COLLAPSE_TICKS (a Point, a Siphon: nothing of its own - the caller's Slowness). */
    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode != EmpMode.PULSE) {
            return false;
        }
        collapseLeft = COLLAPSE_TICKS;
        setMobAction(ACT_COLLAPSED);
        getNavigator().clearPathEntity();
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + height * 0.9D, posZ, 30, 0.4D, 0.4D, 0.4D, 0.15D);
        }
        SoundsSC.play(worldObj, posX, posY + 2D, posZ, "power.off", 1F, 0.7F);
        playSound("random.anvil_land", 0.6F, 0.5F);
        if (shooter instanceof EntityLivingBase && shooter != this) {
            setRevengeTarget((EntityLivingBase) shooter);
        }
        return true;
    }

    /** ПТ-1: Plasma Condensate half the time (the experience is its own - XP). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < CONDENSATE_CHANCE) {
            ItemStack s = MobItemsSC.loot(ItemCombatLootSC.LootKind.PLASMA_CONDENSATE, 1);
            if (s != null) {
                out.add(s);
            }
        }
    }

    /** Server (the world test): ticks since the last storm it felt. */
    public int calmTicks() {
        return calm;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger(NBT_CALM, calm);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        calm = tag.getInteger(NBT_CALM);
    }

    // ------------------------------------------------------------------ sounds: a transformer on legs (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.blaze.breathe";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "random.break";
    }

    @Override
    protected float getSoundPitch() {
        return 0.5F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.5F, 1.3F);
    }
}
