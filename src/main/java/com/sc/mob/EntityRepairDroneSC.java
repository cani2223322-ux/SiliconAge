package com.sc.mob;

import java.util.List;
import java.util.UUID;

import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.mob.ai.EntityAIDroneRepairSC;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): «Ремонтный дрон» - a Hollow Shell's flying repairman: 8 HP, one hit
 * downs it; while its Shell lives it hovers near and feeds it 3 units of reserve a second along a visible beam
 * (IMobActionSC.ACT_REPAIR; MobArmorSC.recharge - nothing while a siphon draws). Any ПЭ-1 Point downs it at once
 * (IEmpReactiveSC). Without its Shell it falls apart in 30 s. Never alone: SpawnDirectorSC makes two with a patrol (30%).
 * No census place of its own (counts with its Shell). Drops: nothing (experience only).
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP. The numbers below are what the handbook's bestiary prints.
 * <p>
 * Its Shell: the entity id in DataWatcher DW_SHELL (20, for the clients' beam; -1 none), the UUID in NBT "scShell"
 * {most, least} (the id changes with every load). A drone that never had a Shell (an egg's) takes the nearest one within
 * ADOPT_RADIUS; one whose Shell is gone (dead, or not to be found) falls apart after ORPHAN_TICKS. It flies (no gravity, no
 * fall damage, vanilla's flyer's drag): EntityAIDroneRepairSC steers it round its Shell's shoulders and does the repair.
 * A ПЭ-1 Point or Pulse that reaches it (EmpGunLogicSC / EmpMobGunSC, after the rules and the screens) kills it on the spot,
 * the shooter named as its killer.
 */
public class EntityRepairDroneSC extends EntitySubstrateMobSC implements IEmpReactiveSC {

    public static final String NAME = "repairDrone";
    public static final double HP = 8D;
    /** DataWatcher (§2.4): its Shell's entity id (int, -1 none). NBT: its Shell's UUID. */
    public static final int DW_SHELL = 20;
    public static final String NBT_SHELL = "scShell";
    /** The repair: REPAIR_UNITS into the Shell's reserve every REPAIR_EVERY ticks, from no farther than REPAIR_REACH blocks. */
    public static final float REPAIR_UNITS = 3F;
    public static final int REPAIR_EVERY = 20, REPAIR_REACH = 6;
    /** Without its Shell it falls apart after this (30 s); an egg's drone takes a Shell this near. */
    public static final int ORPHAN_TICKS = 600, ADOPT_RADIUS = 16;
    /** Flying: the most it moves a tick, how high over its Shell's feet it hovers, how far round it. */
    public static final double FLY_SPEED = 0.35D, HOVER_Y = 2.4D, ORBIT = 1.4D;
    public static final int XP = 3;

    private UUID shellId;
    private EntityHollowShellSC shell;
    private int orphan;

    public EntityRepairDroneSC(World world) {
        super(world);
        setSize(0.6F, 0.6F);
        experienceValue = XP;
        tasks.addTask(0, new EntityAIDroneRepairSC(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(ADOPT_RADIUS);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_SHELL, Integer.valueOf(-1));
    }

    /** It comes with its Shell: no place of its own under the caps. */
    @Override
    public boolean censusCounts() {
        return false;
    }

    // ------------------------------------------------------------------ its Shell

    /** Server (SpawnDirectorSC, before it joins the world): it repairs `s`. */
    public void bind(EntityHollowShellSC s) {
        shell = s;
        shellId = s == null ? null : s.getUniqueID();
        orphan = 0;
        dataWatcher.updateObject(DW_SHELL, Integer.valueOf(s == null ? -1 : s.getEntityId()));
    }

    /**
     * Server, once a tick (EntityAIDroneRepairSC): its Shell - the one it knows, else looked up by UUID once a second round
     * it (an egg's drone: the nearest Shell); null while there is none. A Shell whose chunk unloaded is let go (a new
     * instance comes back with the chunk - found by UUID). Counts the orphan's ticks and falls apart after ORPHAN_TICKS.
     */
    public EntityHollowShellSC shell() {
        if (shell != null && (!shell.isEntityAlive() || shell.worldObj != worldObj
                || worldObj.getEntityByID(shell.getEntityId()) != shell)) {
            shell = null;                               // dead, gone, or its chunk unloaded (not dead, off the world's id map)
        }
        if (shell == null && ticksExisted % 20 == 0) {
            shell = find();
            if (shell != null) {
                bind(shell);
            }
        }
        if (shell == null) {
            if (dataWatcher.getWatchableObjectInt(DW_SHELL) != -1) {
                dataWatcher.updateObject(DW_SHELL, Integer.valueOf(-1));
            }
            if (++orphan >= ORPHAN_TICKS) {
                fallApart();
            }
        } else {
            orphan = 0;
            if (dataWatcher.getWatchableObjectInt(DW_SHELL) != shell.getEntityId()) {
                dataWatcher.updateObject(DW_SHELL, Integer.valueOf(shell.getEntityId()));
            }
        }
        return shell;
    }

    /** Both sides: its Shell by the DataWatcher's id (the clients' beam), null none or not loaded. */
    public Entity watchedShell() {
        int id = dataWatcher.getWatchableObjectInt(DW_SHELL);
        return id < 0 ? null : worldObj.getEntityByID(id);
    }

    private EntityHollowShellSC find() {
        double r = ADOPT_RADIUS * 2D;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityHollowShellSC.class, boundingBox.expand(r, r, r));
        EntityHollowShellSC best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : near) {
            EntityHollowShellSC s = (EntityHollowShellSC) o;
            if (!s.isEntityAlive()) {
                continue;
            }
            if (shellId != null) {
                if (shellId.equals(s.getUniqueID())) {
                    return s;
                }
                continue;
            }
            double d = getDistanceSqToEntity(s);
            if (d <= ADOPT_RADIUS * ADOPT_RADIUS && d < bestD) {         // never had one: the nearest Shell takes it on
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Its Shell is gone: it falls apart (no drops, no experience). */
    private void fallApart() {
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("largesmoke", posX, posY + 0.3D, posZ, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        }
        playSound("random.fizz", 0.6F, 1.4F);
        setDead();
    }

    // ------------------------------------------------------------------ ПБ-20: a ПЭ-1 downs it

    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode == null || mode == EmpMode.SIPHON) {
            return false;
        }
        DamageSource src = shooter instanceof EntityPlayer ? DamageSource.causePlayerDamage((EntityPlayer) shooter)
                : shooter instanceof EntityLivingBase ? DamageSource.causeMobDamage((EntityLivingBase) shooter) : DamageSource.generic;
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.3D, posZ, 12, 0.2D, 0.2D, 0.2D, 0.08D);
        }
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 0.8F, 1.5F);
        hurtResistantTime = 0;
        attackEntityFrom(src, getHealth() + 100F);
        if (isEntityAlive()) {                                       // something cancelled the blow: down all the same
            setHealth(0F);
            onDeath(src);
        }
        return true;
    }

    // ------------------------------------------------------------------ flying (as vanilla's EntityFlying)

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

    /** No gravity: the motion its AI gives it, the air's drag; in water a heavier drag. */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (isInWater() || handleLavaMovement()) {
            moveEntity(motionX, motionY, motionZ);
            motionX *= 0.8D;
            motionY *= 0.8D;
            motionZ *= 0.8D;
        } else {
            moveEntity(motionX, motionY, motionZ);
            motionX *= 0.91D;
            motionY *= 0.91D;
            motionZ *= 0.91D;
        }
        prevLimbSwingAmount = limbSwingAmount;
        limbSwingAmount = 0F;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (shellId != null) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("most", shellId.getMostSignificantBits());
            t.setLong("least", shellId.getLeastSignificantBits());
            tag.setTag(NBT_SHELL, t);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey(NBT_SHELL)) {
            NBTTagCompound t = tag.getCompoundTag(NBT_SHELL);
            shellId = new UUID(t.getLong("most"), t.getLong("least"));
        }
    }

    // ------------------------------------------------------------------ sounds: a small fan and relays (vanilla's)

    @Override
    protected String getLivingSound() {
        return "random.click";
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
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.8F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
    }
}
