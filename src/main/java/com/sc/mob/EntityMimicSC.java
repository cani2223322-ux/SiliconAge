package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.mob.ai.EntityAIMimicChewSC;
import com.sc.ruin.RuinLootSC;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.8): «Мимик-станок» (Mimic Machine) - asleep it is a Crusher or a furnace
 * (a block-sized entity drawn as the machine: IMobActionSC.ACT_DORMANT, IDisguisedSC - WAILA and the analyzer say «???»).
 * A right-click, a blow, or a player within 3 blocks wakes it; then it «chews» (6). A ПЭ-1 Point wakes it early and stuns it
 * 3 s (IEmpReactiveSC). Lithographs only (made with them, never by SpawnDirectorSC); never despawns; no census place.
 * HP 40. Drops (ПТ-1): 1..2 things of a lithograph's loot, 25% a component.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP.
 * <p>
 * Asleep (DW_ACTION = ACT_DORMANT): a box of a block (0.98 - it fits the block grid; the renderer draws a whole block), it
 * stands where it was put, facing a side (yaw a multiple of 90), never moves or falls, can't be pushed - and is as solid as
 * a block to walk into (getBoundingBox). Its look: DataWatcher DW_DISGUISE (DISGUISE_CRUSHER / DISGUISE_FURNACE: the mod's own
 * machines' icons - RenderMimicSC). It wakes (wake: for good - NBT scAwake) when a player right-clicks it, when anything
 * hurts it, when a survival player comes within WAKE_RADIUS (looked at every WAKE_CHECK ticks), or when a ПЭ-1 reaches it -
 * that one stuns it STUN_TICKS too (no move, no bite; sparks). Awake it hunts the one who woke it, then any player in
 * FOLLOW: EntityAIMimicChewSC - CHEW_DAMAGE every CHEW_EVERY ticks at SPEED; with nobody about it walks back to its home (the
 * lithograph's hall, NBT scHome {x, y, z, r}).
 */
public class EntityMimicSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC, IDisguisedSC {

    public static final String NAME = "mimic";
    public static final double HP = 40D;
    /** «Жевание» (ПС-10's cap): its damage, every so many ticks. */
    public static final int CHEW_DAMAGE = 6, CHEW_EVERY = 20;
    /** Waking: a survival player this near, looked at every so many ticks; a ПЭ-1's stun. */
    public static final int WAKE_RADIUS = 3, WAKE_CHECK = 10, STUN_TICKS = 60;
    public static final double SPEED = 0.3D, FOLLOW = 24D;
    /** ПТ-1 (RuinLootSC.mimicLoot - here for the handbook's bestiary): rolls of a lithograph's table, a component's chance. */
    public static final int LOOT_MIN = RuinLootSC.MIMIC_ROLLS_MIN, LOOT_MAX = RuinLootSC.MIMIC_ROLLS_MAX;
    public static final float COMPONENT_CHANCE = RuinLootSC.MIMIC_COMPONENT_CHANCE;
    public static final int XP = 10;
    /** Its looks asleep (DW_DISGUISE): the mod's Crusher, its Electric Furnace. */
    public static final int DISGUISE_CRUSHER = 0, DISGUISE_FURNACE = 1;

    /** DataWatcher (§2.4): the disguise (byte). Asleep: DW_ACTION = ACT_DORMANT. */
    public static final int DW_DISGUISE = 20;
    public static final String NBT_KIND = "scMimic", NBT_AWAKE = "scAwake", NBT_HOME = "scHome";

    private int stun;
    private int homeX, homeY, homeZ, homeR = -1;

    public EntityMimicSC(World world) {
        super(world);
        setSize(0.98F, 0.98F);
        experienceValue = XP;
        setMobAction(ACT_DORMANT);
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIMimicChewSC(this));
        tasks.addTask(4, new EntityAIMoveTowardsRestriction(this, 1.0D));
        tasks.addTask(5, new EntityAIWander(this, 0.6D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 10, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(CHEW_DAMAGE);   // what WAILA-like tools show
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_DISGUISE, Byte.valueOf((byte) DISGUISE_CRUSHER));
    }

    /** A structure's fixture: no place under the caps. */
    @Override
    public boolean censusCounts() {
        return false;
    }

    // ------------------------------------------------------------------ asleep / awake

    /** Both sides: still asleep as a machine. */
    public boolean dormant() {
        return mobAction() == ACT_DORMANT;
    }

    /** ПИ-3: asleep it passes for a machine. */
    @Override
    public boolean disguised() {
        return dormant();
    }

    /**
     * ПИ-3: asleep it is «invisible» as a creature - the mods that list or mark mobs (a minimap's radar, a health bar over the
     * one aimed at; vanilla's isInvisibleToPlayer asks this) skip it. Its machine is drawn all the same (RenderMimicSC is no
     * living renderer; no shadow), and a click or a blow still reaches it (canBeCollidedWith).
     */
    @Override
    public boolean isInvisible() {
        return dormant() || super.isInvisible();
    }

    /** Both sides: DISGUISE_CRUSHER / DISGUISE_FURNACE. */
    public int disguise() {
        return dataWatcher.getWatchableObjectByte(DW_DISGUISE) == DISGUISE_FURNACE ? DISGUISE_FURNACE : DISGUISE_CRUSHER;
    }

    /** Server (the lithograph's hall, an egg): its look asleep. */
    public void setDisguise(int kind) {
        dataWatcher.updateObject(DW_DISGUISE, Byte.valueOf((byte) (kind == DISGUISE_FURNACE ? DISGUISE_FURNACE : DISGUISE_CRUSHER)));
    }

    /** Both sides: stunned by a ПЭ-1 (server's count; a client knows nothing of it). */
    public boolean stunned() {
        return stun > 0;
    }

    /** Server: it wakes for good - `by` (if any) its first target. */
    public void wake(EntityLivingBase by) {
        if (worldObj.isRemote) {
            return;
        }
        if (dormant()) {
            setMobAction(ACT_NONE);
            playSound("random.anvil_land", 0.5F, 0.6F);
            SoundsSC.play(worldObj, posX, posY + 0.5D, posZ, "machine.grind", 0.8F, 0.6F);
            if (worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("largesmoke", posX, posY + 0.8D, posZ, 6, 0.3D, 0.2D, 0.3D, 0.01D);
            }
        }
        if (by != null && by != this && by.isEntityAlive() && !(by instanceof EntityPlayer && ((EntityPlayer) by).capabilities.disableDamage)) {
            setAttackTarget(by);
        }
    }

    /** Server (the lithograph's hall): its home - the hall's middle and half size. */
    public void setHome(int x, int y, int z, int r) {
        homeX = x;
        homeY = y;
        homeZ = z;
        homeR = Math.max(1, r);
        setHomeArea(x, y, z, homeR + EntitySentryDroneSC.HOME_MARGIN);
    }

    /** The spawn egg: a random look, on the block grid, facing a side. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            setDisguise(rand.nextInt(2));
            setLocationAndAngles(MathHelper.floor_double(posX) + 0.5D, posY, MathHelper.floor_double(posZ) + 0.5D, rand.nextInt(4) * 90F, 0F);
        }
        return data;
    }

    @Override
    public void onLivingUpdate() {
        if (dormant()) {                                        // a machine: still, square to the grid
            motionX = motionY = motionZ = 0D;
            float yaw = Math.round(rotationYaw / 90F) * 90F;
            rotationYaw = prevRotationYaw = renderYawOffset = prevRenderYawOffset = rotationYawHead = prevRotationYawHead = yaw;
            rotationPitch = prevRotationPitch = 0F;
        }
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        if (dormant() && ticksExisted % WAKE_CHECK == 0) {
            EntityPlayer p = survivorNear();
            if (p != null) {
                wake(p);
            }
        }
        if (stun > 0) {
            stun--;
            motionX = 0D;
            motionZ = 0D;
            if (stun % 10 == 0 && worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.8D, posZ, 3, 0.3D, 0.2D, 0.3D, 0.05D);
            }
        }
    }

    /** The nearest survival player within WAKE_RADIUS, or null. */
    private EntityPlayer survivorNear() {
        double r = WAKE_RADIUS;
        List<?> l = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(posX - r, posY - r, posZ - r, posX + r, posY + r, posZ + r));
        for (Object o : l) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.isEntityAlive() && !p.capabilities.isCreativeMode && getDistanceSqToEntity(p) <= r * r) {
                return p;
            }
        }
        return null;
    }

    /** Asleep or stunned no task runs (the machine doesn't look about); awake - the usual. */
    @Override
    protected void updateAITasks() {
        if (dormant() || stunned()) {
            return;
        }
        super.updateAITasks();
    }

    /** Asleep it doesn't move at all, gravity included. */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (dormant()) {
            return;
        }
        super.moveEntityWithHeading(strafe, forward);
    }

    /** Asleep: as solid as a block to walk into; awake - a creature. */
    @Override
    public AxisAlignedBB getBoundingBox() {
        return dormant() ? boundingBox : null;
    }

    @Override
    public boolean canBePushed() {
        return !dormant();
    }

    @Override
    public void knockBack(Entity by, float strength, double dx, double dz) {
        if (!dormant()) {
            super.knockBack(by, strength, dx, dz);
        }
    }

    /** A right-click wakes it (the machine's screen it hoped for never opens). */
    @Override
    protected boolean interact(EntityPlayer p) {
        if (dormant()) {
            wake(p);
            return true;
        }
        return super.interact(p);
    }

    /** Any blow wakes it - the blow lands all the same. */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && dormant() && !isEntityInvulnerable()) {
            wake(src != null && src.getEntity() instanceof EntityLivingBase ? (EntityLivingBase) src.getEntity() : null);
        }
        return super.attackEntityFrom(src, amount);
    }

    /** «Жевание»: CHEW_DAMAGE as its own blow (EntityAIMimicChewSC calls it every CHEW_EVERY ticks). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (dormant() || stunned()) {
            return false;
        }
        SoundsSC.play(worldObj, posX, posY + 0.5D, posZ, "machine.grind", 0.7F, 1.3F);
        return MobCombatSC.melee(this, target, CHEW_DAMAGE);
    }

    /** It stays: a lithograph's machine never despawns (asleep or not). */
    @Override
    protected boolean canDespawn() {
        return false;
    }

    // ------------------------------------------------------------------ ПБ-20: a ПЭ-1 wakes it and stuns it

    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode == null || mode == EmpMode.SIPHON) {
            return false;
        }
        wake(shooter instanceof EntityLivingBase ? (EntityLivingBase) shooter : null);
        stun = STUN_TICKS;
        getNavigator().clearPathEntity();
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 0.8F, 0.8F);
        return true;
    }

    /** ПТ-1: a lithograph's loot (RuinLootSC.mimicLoot). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        RuinLootSC.mimicLoot(out, rand);
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte(NBT_KIND, (byte) disguise());
        tag.setBoolean(NBT_AWAKE, !dormant());
        if (homeR > 0) {
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
        setDisguise(tag.getByte(NBT_KIND));
        setMobAction(tag.getBoolean(NBT_AWAKE) ? ACT_NONE : ACT_DORMANT);
        if (tag.hasKey(NBT_HOME)) {
            NBTTagCompound h = tag.getCompoundTag(NBT_HOME);
            setHome(h.getInteger("x"), h.getInteger("y"), h.getInteger("z"), h.getInteger("r"));
        }
    }

    // ------------------------------------------------------------------ sounds: a machine on legs (vanilla's and the mod's)

    @Override
    protected String getLivingSound() {
        return dormant() ? null : "mob.irongolem.walk";
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
        return 0.7F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.3F, 1.4F);
    }
}
