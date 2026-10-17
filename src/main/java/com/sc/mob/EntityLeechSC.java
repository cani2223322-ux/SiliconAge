package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.ai.EntityAILeechCoilSC;
import com.sc.util.Material;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.6, rule ПС-36): «Токоед» (Current Leech) - no armour, a snake of cables 3 blocks
 * long. Coils round its prey for 5 s, drawing 1% of the charge of each armour piece a second (without the mod's armour: 2
 * damage a second). Water - a short circuit: 10 damage and 3 s stunned. A Pulse or a Siphon of the ПЭ-1 bursts it with a
 * discharge (6 damage within 3 - don't fire point-blank). HP 30. Drops: an MV energy cell 40%, crushed copper ore 1..3. The
 * canyons and the Ridges.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 17 in MobEntitiesSC), the
 * World constructor (EntityList), HP, IEmpReactiveSC. The rest - its AI, its numbers, its drops (IFactionLootSC) - is package N's.
 * <p>
 * The coil (ACT_WRAP; EntityAILeechCoilSC crawls up to the prey): WRAP_TICKS at the prey's feet (its place set to the prey's
 * every tick; nothing pushes either of them), once a second DRAIN of the capacity of every worn piece of the mod's
 * (ItemArmorSC.discharge, at least 1 EU a piece), without one BITE as its own blow; then WRAP_COOLDOWN before the next. The prey
 * leaves it only by killing it, by water or a ПЭ-1 - or by going where it can't follow (another world, a teleport: farther than
 * LET_GO). DataWatcher DW_PREY: the prey's id while coiled (-1 none) - every client draws the coil round that one.
 * <p>
 * Water (standing in it, or a right-click with a water bucket - the bucket empties but in creative): SHORT_DAMAGE and
 * STUN_TICKS stunned (ACT_STUNNED: no move, no coil; sparks); again only after the stun. A ПЭ-1 Pulse or a Siphon's start
 * (EmpSiphonSC asks it) - the burst: BURST_DAMAGE «Пробой» to everything living within BURST_RADIUS (the shooter too), and it is
 * dead, the shooter's kill. A Point does nothing of its own (the caller gives the Slowness).
 */
public class EntityLeechSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "leech";
    public static final double HP = 30D;
    /** ПС-36: the coil - its length, the cooldown after it, the share of a piece's capacity drawn a second, the bite without the mod's armour. */
    public static final int WRAP_TICKS = 100, WRAP_COOLDOWN = 100, BITE = 2;
    public static final float DRAIN = 0.01F;
    /** It coils on when this near its prey (horizontally / up or down); lets go of a prey farther than LET_GO. */
    public static final double COIL_REACH = 1.4D, COIL_DY = 1.5D, LET_GO = 4D;
    /** Water: a short circuit - its damage and the stun. */
    public static final int SHORT_DAMAGE = 10, STUN_TICKS = 60;
    /** ПБ-23: the burst - «Пробой» to everything living this near. */
    public static final int BURST_DAMAGE = 6;
    public static final double BURST_RADIUS = 3D;
    public static final double SPEED = 0.28D, FOLLOW = 16D;
    /** ПТ-1: an MV energy cell's chance (an empty one), crushed copper ore. TODO(balance) */
    public static final float CELL_CHANCE = 0.4F;
    public static final int COPPER_MIN = 1, COPPER_MAX = 3;
    /** ItemBatterySC's MV cell (TIERS index 1). */
    public static final int CELL_META = 1;
    public static final int XP = 10;

    /** DataWatcher (§2.6): the coiled prey's entity id (int, -1 none). */
    public static final int DW_PREY = 20;
    private static final String NBT_COOLDOWN = "scCoilCd";

    private int wrap, cooldown, stun;

    public EntityLeechSC(World world) {
        super(world);
        setSize(0.8F, 0.5F);
        experienceValue = XP;
        tasks.addTask(1, new EntityAILeechCoilSC(this));
        tasks.addTask(5, new EntityAIWander(this, 0.7D));
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
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BITE);   // what WAILA-like tools show
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_PREY, Integer.valueOf(-1));
    }

    // ------------------------------------------------------------------ the coil

    /** Both sides: the coiled prey's id, -1 none. */
    public int preyId() {
        return dataWatcher.getWatchableObjectInt(DW_PREY);
    }

    /** Both sides: coiled round something now. */
    public boolean coiled() {
        return preyId() >= 0;
    }

    /** Server: stunned by water. */
    public boolean stunned() {
        return stun > 0;
    }

    /** Server (EntityAILeechCoilSC): it may coil now - not stunned, not coiled, the cooldown over. */
    public boolean mayCoil() {
        return stun <= 0 && wrap <= 0 && cooldown <= 0;
    }

    /** Server (EntityAILeechCoilSC): coils round `t` for WRAP_TICKS. */
    public void coil(EntityLivingBase t) {
        if (worldObj.isRemote || t == null || !mayCoil()) {
            return;
        }
        wrap = WRAP_TICKS;
        dataWatcher.updateObject(DW_PREY, Integer.valueOf(t.getEntityId()));
        setMobAction(ACT_WRAP);
        getNavigator().clearPathEntity();
        playSound("mob.silverfish.say", 0.8F, 0.6F);
        SoundsSC.play(worldObj, posX, posY, posZ, "emp.siphon", 0.5F, 1.6F);
    }

    /** Server: lets go (the coil is over, broken off, the prey gone) - the cooldown starts. */
    private void uncoil() {
        if (preyId() >= 0) {
            dataWatcher.updateObject(DW_PREY, Integer.valueOf(-1));
            cooldown = WRAP_COOLDOWN;
        }
        wrap = 0;
        if (mobAction() == ACT_WRAP) {
            setMobAction(ACT_NONE);
        }
    }

    private Entity prey() {
        int id = preyId();
        return id < 0 ? null : worldObj.getEntityByID(id);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (stun > 0) {
            stun--;
            motionX = 0D;
            motionZ = 0D;
            if (stun % 10 == 0 && worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.3D, posZ, 3, 0.3D, 0.1D, 0.3D, 0.05D);
            }
            if (stun == 0 && mobAction() == ACT_STUNNED) {
                setMobAction(ACT_NONE);
            }
        } else if (isWet()) {
            shortCircuit(DamageSource.generic);                 // standing in water: again once the stun is over
        }
        if (wrap > 0) {
            coilTick();
        }
    }

    /** A tick of the coil: at the prey's feet; once a second its charge (or its blood). */
    private void coilTick() {
        Entity e = prey();
        EntityLivingBase t = e instanceof EntityLivingBase ? (EntityLivingBase) e : null;
        if (t == null || !t.isEntityAlive() || t.worldObj != worldObj || getDistanceSqToEntity(t) > LET_GO * LET_GO
                || t instanceof EntityPlayer && ((EntityPlayer) t).capabilities.disableDamage || --wrap <= 0) {
            uncoil();
            return;
        }
        setPosition(t.posX, t.posY, t.posZ);
        motionX = motionY = motionZ = 0D;
        if ((WRAP_TICKS - wrap) % 20 == 1) {                    // the first sip at once: five in the coil
            drink(t);
        }
    }

    /** ПС-36: 1% of every worn piece of the mod's (at least 1 EU), else its bite. */
    private void drink(EntityLivingBase t) {
        boolean drank = false;
        if (t instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) t;
            for (int i = 0; i < 4; i++) {
                ItemStack s = p.inventory.armorInventory[3 - i];
                if (s != null && s.getItem() instanceof ItemArmorSC) {
                    int amount = (int) Math.max(1L, (long) (ItemArmorSC.capacityOf(s) * (double) DRAIN));
                    ItemArmorSC.discharge(s, amount);
                    drank = true;                               // an empty suit is still the mod's: no bite
                }
            }
        }
        if (drank) {
            if (worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("reddust", t.posX, t.posY + 0.4D, t.posZ, 0, 0.3D, 0.85D, 1D, 1D);
                ((WorldServer) worldObj).func_147487_a("fireworksSpark", t.posX, t.posY + 0.3D, t.posZ, 2, 0.3D, 0.1D, 0.3D, 0.02D);
            }
            SoundsSC.play(worldObj, t.posX, t.posY, t.posZ, "field.zap", 0.4F, 1.8F);
        } else {
            t.hurtResistantTime = 0;
            MobCombatSC.melee(this, t, BITE);
        }
    }

    /** Never pushed while coiled (it is wound round its prey's legs); never pushes it either. */
    @Override
    public boolean canBePushed() {
        return !coiled() && super.canBePushed();
    }

    @Override
    protected void collideWithEntity(Entity e) {
        if (!coiled()) {
            super.collideWithEntity(e);
        }
    }

    @Override
    public void knockBack(Entity by, float strength, double dx, double dz) {
        if (!coiled()) {
            super.knockBack(by, strength, dx, dz);
        }
    }

    /** Stunned or coiled, its own tasks rest (the coil is this class's tick). */
    @Override
    protected void updateAITasks() {
        if (stunned() || coiled()) {
            return;
        }
        super.updateAITasks();
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (coiled() && !worldObj.isRemote) {
            return;                                             // its place is its prey's (coilTick)
        }
        super.moveEntityWithHeading(strafe, forward);
    }

    /** No blow of vanilla's: the coil is the whole of it. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
    }

    // ------------------------------------------------------------------ water: a short circuit

    /** Server: SHORT_DAMAGE from `src` and STUN_TICKS stunned - not while still stunned. @return it shorted */
    public boolean shortCircuit(DamageSource src) {
        if (worldObj.isRemote || stun > 0 || !isEntityAlive()) {
            return false;
        }
        uncoil();
        stun = STUN_TICKS;
        setMobAction(ACT_STUNNED);
        getNavigator().clearPathEntity();
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.3D, posZ, 16, 0.4D, 0.2D, 0.4D, 0.1D);
            ((WorldServer) worldObj).func_147487_a("smoke", posX, posY + 0.3D, posZ, 6, 0.3D, 0.1D, 0.3D, 0.02D);
        }
        SoundsSC.play(worldObj, posX, posY, posZ, "power.off", 0.9F, 1.2F);
        playSound("random.fizz", 0.8F, 1F);
        hurtResistantTime = 0;
        attackEntityFrom(src, SHORT_DAMAGE);
        return true;
    }

    /** A water bucket on it: the short circuit, the bucket empty (but in creative). */
    @Override
    protected boolean interact(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        if (held == null || held.getItem() != Items.water_bucket) {
            return super.interact(p);
        }
        if (!worldObj.isRemote && shortCircuit(DamageSource.causePlayerDamage(p)) && !p.capabilities.isCreativeMode) {
            p.inventory.setInventorySlotContents(p.inventory.currentItem, new ItemStack(Items.bucket));
        }
        return true;
    }

    // ------------------------------------------------------------------ ПБ-23: a Pulse or a Siphon bursts it

    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode == null || mode == EmpMode.POINT) {
            return false;
        }
        burst(shooter);
        return true;
    }

    /** The burst: BURST_DAMAGE «Пробой» to everything living within BURST_RADIUS (`shooter` too), then dead - `shooter`'s kill. */
    private void burst(Entity shooter) {
        uncoil();
        double r = BURST_RADIUS;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX - r, posY - r, posZ - r, posX + r, posY + r, posZ + r));
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e != this && e.isEntityAlive() && e.getDistanceSqToEntity(this) <= r * r) {
                e.attackEntityFrom(BreakdownDamageSC.of(this), BURST_DAMAGE);
            }
        }
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            ws.func_147487_a("fireworksSpark", posX, posY + 0.3D, posZ, 40, 1.0D, 0.5D, 1.0D, 0.25D);
            ws.func_147487_a("largeexplode", posX, posY + 0.3D, posZ, 1, 0D, 0D, 0D, 0D);
        }
        SoundsSC.play(worldObj, posX, posY, posZ, "field.zap", 1.5F, 0.6F);
        playSound("random.explode", 0.6F, 1.6F);
        DamageSource src = shooter instanceof EntityPlayer ? DamageSource.causePlayerDamage((EntityPlayer) shooter)
                : shooter instanceof EntityLivingBase ? DamageSource.causeMobDamage((EntityLivingBase) shooter) : DamageSource.generic;
        hurtResistantTime = 0;
        attackEntityFrom(src, getHealth() + 100F);
        if (isEntityAlive()) {                                  // something cancelled the blow: burst all the same
            setHealth(0F);
            onDeath(src);
        }
    }

    /** ПТ-1: an MV energy cell now and then, crushed copper ore. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < CELL_CHANCE && ModItems.battery != null) {
            out.add(new ItemStack(ModItems.battery, 1, CELL_META));
        }
        ItemStack copper = ModItems.crushedOre == null ? null : ModItems.crushedOre.stackOf(Material.COPPER);
        if (copper != null) {
            copper.stackSize = COPPER_MIN + rand.nextInt(COPPER_MAX - COPPER_MIN + 1);
            out.add(copper);
        }
    }

    // ------------------------------------------------------------------ NBT (the coil itself is not kept: a restart lets go)

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger(NBT_COOLDOWN, Math.max(cooldown, wrap > 0 ? WRAP_COOLDOWN : 0));
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        cooldown = tag.getInteger(NBT_COOLDOWN);
    }

    // ------------------------------------------------------------------ sounds: cables and sparks (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.silverfish.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.silverfish.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.silverfish.kill";
    }

    @Override
    protected float getSoundPitch() {
        return 0.6F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.silverfish.step", 0.2F, 0.7F);
    }
}
