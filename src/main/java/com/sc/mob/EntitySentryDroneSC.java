package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAISentryDroneBeamSC;
import com.sc.mob.ai.EntityAISentryDroneFlySC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.util.SiliconMaterial;
import com.sc.util.SoundsSC;
import com.sc.util.WeaponType;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.7): «Одичавший дрон-охранник» (Feral Sentry Drone) - flies, natural armour
 * 6, an Ion Cutter beam of 4 every 2 s after 1 s of its searchlight on the target (IMobActionSC.ACT_SEARCHLIGHT), only along
 * a clear line of sight. It calls the drones within 24 blocks. It lives in the structures only - the lithographs (made with
 * them) and the Dead Nodes (SpawnDirectorSC) - and never strays from its home box. Any ПЭ-1 Point downs it at once
 * (IEmpReactiveSC). HP 16. Drops (ПТ-1): a Sensor 5%, a Focusing Lens 1%, a Controller 0.8%.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP.
 * <p>
 * Its home: NBT scHome {x, y, z, r} - the structure's middle and half size; the leash is r + HOME_MARGIN round it (vanilla's
 * home area: no target outside it is taken, EntityAISentryDroneFlySC never leaves it) and HOME_DY up or down. A lithograph's
 * drones get the hall (ComponentLitHallSC); one without a home (SpawnDirectorSC's at a Dead Node, an egg's) takes one on its
 * first tick: the Dead Node whose zone it is in, a lithograph's hall it is in, else where it is (HOME_R_ALONE). The
 * searchlight: DataWatcher DW_TARGET - its target's entity id while the light is on (-1 off); every client draws the cone
 * (RenderSentryDroneSC). A drone that takes a target hands it to every drone within CALL_RADIUS that has none.
 */
public class EntitySentryDroneSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "sentryDrone";
    public static final double HP = 16D;
    /** Natural armour (getTotalArmorValue). */
    public static final int NATURAL_ARMOUR = 6;
    /** The Ion Cutter (ПС-4): the searchlight before a shot, a shot every RELOAD_TICKS ticks, its range, its beam. */
    public static final int SEARCHLIGHT_TICKS = 20, RELOAD_TICKS = 40, RANGE = 16, BEAM_DAMAGE = MobCombatSC.beamDamage(WeaponType.ION_CUTTER);
    /** It calls the drones this near; the leash past its home's half size; up / down from its home's middle; a lone drone's home. */
    public static final int CALL_RADIUS = 24, HOME_MARGIN = 8, HOME_DY = 6, HOME_R_ALONE = 12;
    /** It looks out for a player once every so many ticks (the AI rule: a ray no oftener than every 10 ticks). */
    public static final int SIGHT_EVERY = 10;
    /** ПТ-1 drops. TODO(balance) */
    public static final float SENSOR_CHANCE = 0.05F, LENS_CHANCE = 0.01F, CONTROLLER_CHANCE = 0.008F;
    /** Flight: top speed, how far from its target it hangs, how high over the target's eyes. */
    public static final double FLY_SPEED = 0.22D, HOVER_DIST = 7D, HOVER_UP = 2D;
    public static final int XP = 8;

    /** DataWatcher (§2.4): the searchlight's target (int entity id, -1 off). */
    public static final int DW_TARGET = 20;
    public static final String NBT_HOME = "scHome";

    private int homeX, homeY, homeZ, homeR = -1;

    public EntitySentryDroneSC(World world) {
        super(world);
        setSize(0.6F, 0.6F);
        experienceValue = XP;
        tasks.addTask(1, new EntityAISentryDroneBeamSC(this));
        tasks.addTask(2, new EntityAISentryDroneFlySC(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(RANGE);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_TARGET, Integer.valueOf(-1));
    }

    /** Natural armour 6 (plus anything worn - nothing). */
    @Override
    public int getTotalArmorValue() {
        return Math.min(20, super.getTotalArmorValue() + NATURAL_ARMOUR);
    }

    // ------------------------------------------------------------------ home

    /** Server: its home - the structure's middle and half size (the leash: r + HOME_MARGIN). */
    public void setHome(int x, int y, int z, int r) {
        homeX = x;
        homeY = y;
        homeZ = z;
        homeR = Math.max(1, r);
        setHomeArea(x, y, z, homeR + HOME_MARGIN);
    }

    public boolean hasOwnHome() {
        return homeR > 0;
    }

    public int homeX() {
        return homeX;
    }

    public int homeY() {
        return homeY;
    }

    public int homeZ() {
        return homeZ;
    }

    /** The leash: how far from its home's middle it may fly (horizontally). */
    public int leash() {
        return homeR + HOME_MARGIN;
    }

    /** A drone without a home takes one: its Dead Node, its lithograph's hall, else where it is. Server. */
    private void takeHome() {
        int x = (int) Math.floor(posX), y = (int) Math.floor(posY), z = (int) Math.floor(posZ);
        long seed = worldObj.getSeed();
        int[] node = RuinLayoutSC.nodeNear(x, z);
        if (node != null) {
            setHome(node[0] * SubstrateTerrainSC.GRID, SubstrateTerrainSC.nodeFloorY(seed, node[0], node[1]) + 5,
                    node[1] * SubstrateTerrainSC.GRID, RuinLayoutSC.NODE_HALF);
            return;
        }
        RuinLayoutSC.LithoSite s = RuinLayoutSC.inLitho(seed, x, y, z) ? RuinLayoutSC.lithoNear(seed, x, z, 0) : null;
        if (s != null) {
            setHome(s.x, s.floorY + RuinLayoutSC.HALL_H / 2, s.z, RuinLayoutSC.HALL_HALF);
            return;
        }
        setHome(x, y, z, HOME_R_ALONE);
    }

    // ------------------------------------------------------------------ the searchlight, the call

    /** Both sides: the searchlight's target id, -1 off. */
    public int lightTarget() {
        return dataWatcher.getWatchableObjectInt(DW_TARGET);
    }

    /** Server (EntityAISentryDroneBeamSC): the searchlight on `t` (null: off) - the telegraph ACT_SEARCHLIGHT with it. */
    public void setLight(Entity t) {
        int id = t == null ? -1 : t.getEntityId();
        if (lightTarget() != id) {
            dataWatcher.updateObject(DW_TARGET, Integer.valueOf(id));
        }
        setMobAction(t == null ? ACT_NONE : ACT_SEARCHLIGHT);
    }

    /**
     * The nearest survival, visible player within RANGE whom it sees (a ray, once every SIGHT_EVERY ticks) inside its home
     * area, or null. Its own look-out, not vanilla's: that one sees only 4 blocks up and down, and a drone hangs higher.
     */
    private EntityPlayer spot() {
        EntityPlayer best = null;
        double bestD = RANGE * RANGE;
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.disableDamage || p.isInvisible()) {
                continue;
            }
            double d = getDistanceSqToEntity(p);
            if (d < bestD && isWithinHomeDistance((int) Math.floor(p.posX), (int) Math.floor(p.posY), (int) Math.floor(p.posZ)) && canEntityBeSeen(p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** A new target: every drone within CALL_RADIUS without one takes it too. */
    @Override
    public void setAttackTarget(EntityLivingBase t) {
        EntityLivingBase was = getAttackTarget();
        super.setAttackTarget(t);
        if (t != null && t != was && !worldObj.isRemote) {
            double r = CALL_RADIUS;
            List<?> near = worldObj.getEntitiesWithinAABB(EntitySentryDroneSC.class, boundingBox.expand(r, r, r));
            for (Object o : near) {
                EntitySentryDroneSC d = (EntitySentryDroneSC) o;
                if (d != this && d.isEntityAlive() && d.getAttackTarget() == null && d.getDistanceSqToEntity(this) <= r * r) {
                    d.takeTarget(t);
                }
            }
        }
    }

    /** Called by another drone: the target without calling on (no echo). */
    private void takeTarget(EntityLivingBase t) {
        super.setAttackTarget(t);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        if (!hasOwnHome()) {
            takeHome();
        }
        if (getAttackTarget() == null && (ticksExisted + getEntityId()) % SIGHT_EVERY == 0) {
            EntityPlayer p = spot();
            if (p != null) {
                setAttackTarget(p);
            }
        }
        EntityLivingBase t = getAttackTarget();
        if (t != null && ticksExisted % 20 == 0 && (!t.isEntityAlive() || t instanceof EntityPlayer && ((EntityPlayer) t).capabilities.disableDamage
                || getDistanceSqToEntity(t) > RANGE * RANGE * 4 || !isWithinHomeDistance((int) Math.floor(t.posX), (int) Math.floor(t.posY), (int) Math.floor(t.posZ)))) {
            setAttackTarget(null);                              // gone, out of reach, out of its home: back to the patrol
        }
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
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.3D, posZ, 14, 0.2D, 0.2D, 0.2D, 0.08D);
        }
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 0.9F, 1.2F);
        setLight(null);
        hurtResistantTime = 0;
        attackEntityFrom(src, getHealth() + 100F);
        if (isEntityAlive()) {                                  // something cancelled the blow: down all the same
            setHealth(0F);
            onDeath(src);
        }
        return true;
    }

    /** ПТ-1: a Sensor, a Focusing Lens, a Controller - each now and then. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < SENSOR_CHANCE) {
            add(out, ModItems.component("sensor"));
        }
        if (rand.nextFloat() < LENS_CHANCE) {
            add(out, ModItems.component("focusLens"));
        }
        if (rand.nextFloat() < CONTROLLER_CHANCE && ModItems.siliconMaterial != null) {
            out.add(ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER));
        }
    }

    private static void add(List<ItemStack> out, Item item) {
        if (item != null) {
            out.add(new ItemStack(item));
        }
    }

    /** No blow of its own: the beam is the whole of it. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
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
        moveEntity(motionX, motionY, motionZ);
        double drag = isInWater() || handleLavaMovement() ? 0.8D : 0.91D;
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        prevLimbSwingAmount = limbSwingAmount;
        limbSwingAmount = 0F;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (hasOwnHome()) {
            NBTTagCompound h = new NBTTagCompound();
            h.setInteger("x", homeX);
            h.setInteger("y", homeY);
            h.setInteger("z", homeZ);
            h.setInteger("r", homeR);
            tag.setTag(NBT_HOME, h);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey(NBT_HOME)) {
            NBTTagCompound h = tag.getCompoundTag(NBT_HOME);
            setHome(h.getInteger("x"), h.getInteger("y"), h.getInteger("z"), h.getInteger("r"));
        }
    }

    // ------------------------------------------------------------------ sounds: a fan and relays (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.blaze.breathe";
    }

    @Override
    public int getTalkInterval() {
        return 120;
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
        return 0.35F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.7F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
    }
}
