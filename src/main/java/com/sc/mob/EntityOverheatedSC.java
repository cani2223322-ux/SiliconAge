package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.mob.ai.EntityAIOverheatedSeekSC;
import com.sc.radiation.RadiationSC;
import com.sc.util.ArmorSuit;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.1, rule ПС-31): «Перегретый» (Overheated Shell) - a red-hot Quantum shell in the
 * emergency mode (no helium), reserve 0..15, no weapon. Radiation 2 within 4 blocks (a RadiationSC source of its own), sets on
 * fire for 3 s, a burst of steam on dying (4 damage, radius 3). A bucket of water: 6 damage and -50% protection for 10 s. The ПЭ-1
 * is almost useless on it (nothing to suppress, nothing to drain). Walks slowly to the energy sources. HP 30. Drops: Shell
 * Firmware 1, a Boiled Loop 20%; trophy: a Quantum piece 1%. The Oxide Fields.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 12 in MobEntitiesSC), the
 * World constructor (EntityList), HP, the faction. The numbers below are what the handbook's bestiary prints.
 * <p>
 * Package M: Quantum PIECES_MIN..PIECES_MAX pieces glowing red-hot orange (ModelArmorGlowSC.COLORS 2), the emergency mode (iron
 * points), a reserve by RESERVE_DAMAGE_MIN..MAX blockable damage - so the armour is thin and often «down» from the start; fire
 * can't hurt it. The fist FIST_MIN..FIST_MAX sets the target on fire for FIRE_SECONDS. Every RADIATION_EVERY ticks it reports
 * itself to RadiationSC as a source of RADIATION_LEVEL within RADIATION_RADIUS (reportEntity: one source moving with it; the
 * blocks between cut it; the lead suit, D2O, a field with F_RADIATION protect as from any source - ПА-2). Water (a bucket of it
 * right-clicked on it - emptied but in creative - or standing in water) quenches it: WATER_DAMAGE past the armour and, for
 * WATER_TICKS, half of what its armour would stop goes through (applyArmorCalculations); the steam hisses off it (DW_WET, the
 * clients draw it). Dying it bursts:
 * STEAM_DAMAGE to every living thing but the Substrate's own within STEAM_RADIUS, no block touched. Its idle is a slow walk to
 * the nearest of this mod's machines within SEEK_RADIUS (EntityAIOverheatedSeekSC). It avoids water (it knows).
 */
public class EntityOverheatedSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "overheated";
    public static final double HP = 30D, SPEED = 0.2D, FOLLOW = 16D;
    /** Quantum 2..4 pieces, the red-hot orange light (ModelArmorGlowSC.COLORS 2); the reserve's blockable damage. TODO(balance) */
    public static final int PIECES_MIN = 2, PIECES_MAX = 4, GLOW = 2;
    public static final float RESERVE_DAMAGE_MIN = 0F, RESERVE_DAMAGE_MAX = 15F;
    /** ПС-31: its fist and the fire it lights (seconds). */
    public static final int FIST_MIN = MobCombatSC.FIST, FIST_MAX = MobCombatSC.FIST + MobCombatSC.FIST_SPREAD, FIRE_SECONDS = 3;
    /** ПА-2: a source of radiation - its level and reach, reported every RADIATION_EVERY ticks. */
    public static final float RADIATION_LEVEL = 2F;
    public static final int RADIATION_RADIUS = 4, RADIATION_EVERY = 20;
    /** Dying: the steam burst's damage and radius. */
    public static final float STEAM_DAMAGE = 4F, STEAM_RADIUS = 3F;
    /** Water: its damage, the share of the armour's stop that goes through while quenched, how long that lasts. TODO(balance) */
    public static final float WATER_DAMAGE = 6F, WATER_ARMOUR_CUT = 0.5F;
    public static final int WATER_TICKS = 200;
    /** Its idle: the machines within SEEK_RADIUS, looked for every SEEK_TICKS. */
    public static final int SEEK_RADIUS = 16, SEEK_TICKS = 100;
    /** ПТ-1, ПТ-18: Shell Firmware 1, a Boiled Loop 20%. Its group (SpawnRulesSC's): 1..2 on the Oxide Fields and in the Rift. */
    public static final int FIRMWARE = 1;
    public static final float LOOP_CHANCE = 0.2F;
    public static final int GROUP_MIN = SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_OVERHEATED], GROUP_MAX = SpawnRulesSC.GROUP_MAX[MobEntitiesSC.ID_OVERHEATED];
    public static final int XP = 8;
    /** DataWatcher (package M's, §2.6): quenched now (byte 0 / 1) - the clients' steam. */
    public static final int DW_WET = 26;
    /** NBT: the world time (getTotalWorldTime) its quench ends. */
    public static final String NBT_WET = "scWetUntil";

    /** Server: the world time its quench ends (0 - dry). */
    private long wetUntil;

    public EntityOverheatedSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = XP;
        isImmuneToFire = true;                                       // red-hot already
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.1D, false));
        tasks.addTask(5, new EntityAIOverheatedSeekSC(this, 0.9D));
        tasks.addTask(6, new EntityAIWander(this, 0.6D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_WET, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(FIST_MIN);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПЗ-3: a burnt-out Shell - the Shells'. */
    @Override
    public String faction() {
        return FactionSC.SHELLS;
    }

    /** ПС-4, ПС-31: the fist 3..4 (no weapon) and the fire - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(null, MobCombatSC.FIST, rand) * damageMul());
        if (hit) {
            target.setFire(FIRE_SECONDS);
        }
        return hit;
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.QUANTUM, PIECES_MIN + rand.nextInt(PIECES_MAX - PIECES_MIN + 1), GLOW, rand);
            MobArmorSC a = mobArmor();
            a.helium = false;                                // boiled away
            a.emergency = true;                              // the emergency mode: iron points
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    /** ПТ-1, ПТ-18: Shell Firmware 1, a Boiled Loop 20%. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack firmware = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, FIRMWARE);
        if (firmware != null) {
            out.add(firmware);
        }
        if (rand.nextFloat() < LOOP_CHANCE) {
            ItemStack loop = MobItemsSC.loot(ItemCombatLootSC.LootKind.BOILED_LOOP, 1);
            if (loop != null) {
                out.add(loop);
            }
        }
    }

    // ------------------------------------------------------------------ water

    /** Quenched now (both sides: the DataWatcher). */
    public boolean wet() {
        return dataWatcher.getWatchableObjectByte(DW_WET) != 0;
    }

    /**
     * A bucket of water right-clicked on it: the quench (server), the bucket emptied (not in creative). Both sides answer true -
     * the hand swings, the click goes no further.
     */
    @Override
    protected boolean interact(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        if (held == null || held.getItem() != Items.water_bucket || !isEntityAlive()) {
            return super.interact(p);
        }
        if (!worldObj.isRemote) {
            quench(DamageSource.causePlayerDamage(p).setDamageBypassesArmor());
            if (!p.capabilities.isCreativeMode) {
                p.inventory.setInventorySlotContents(p.inventory.currentItem, new ItemStack(Items.bucket));
            }
        }
        return true;
    }

    /** Server: WATER_DAMAGE from `src` (past the armour), and WATER_TICKS of half the armour's stop going through. */
    private void quench(DamageSource src) {
        wetUntil = worldObj.getTotalWorldTime() + WATER_TICKS;
        dataWatcher.updateObject(DW_WET, Byte.valueOf((byte) 1));
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + height * 0.6D, posZ, 16, 0.3D, 0.5D, 0.3D, 0.05D);
        }
        playSound("random.fizz", 1.2F, 0.6F + rand.nextFloat() * 0.2F);
        attackEntityFrom(src, WATER_DAMAGE);
    }

    /** -50% protection while quenched: of what the armour would stop, WATER_ARMOUR_CUT goes through (the reserve pays it all). */
    @Override
    protected float applyArmorCalculations(DamageSource src, float amount) {
        float after = super.applyArmorCalculations(src, amount);
        return !worldObj.isRemote && wetUntil > worldObj.getTotalWorldTime() ? after + (amount - after) * WATER_ARMOUR_CUT : after;
    }

    // ------------------------------------------------------------------ the tick: radiation, standing in water, the steam

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            clientSteam();
            return;
        }
        long now = worldObj.getTotalWorldTime();
        if (isEntityAlive() && (now + getEntityId()) % RADIATION_EVERY == 0) {
            RadiationSC.reportEntity(this, MathHelper.floor_double(posX), MathHelper.floor_double(posY + 1D), MathHelper.floor_double(posZ),
                    RADIATION_LEVEL, RADIATION_RADIUS);
        }
        if (wetUntil > 0L && now >= wetUntil) {
            wetUntil = 0L;
            dataWatcher.updateObject(DW_WET, Byte.valueOf((byte) 0));
        }
        if (isInWater() && wetUntil == 0L && isEntityAlive()) {
            quench(DamageSource.drown);                      // standing in water: again once the last quench is over
        }
    }

    /** The clients: steam out of the joints now and then, a white plume while quenched; a drop of molten metal. */
    private void clientSteam() {
        boolean w = wet();
        if (w || ticksExisted % 8 == 0) {
            double a = rand.nextDouble() * Math.PI * 2D;
            worldObj.spawnParticle(w ? "cloud" : "smoke", posX + Math.cos(a) * 0.35D, posY + 0.6D + rand.nextDouble() * 1.1D,
                    posZ + Math.sin(a) * 0.35D, 0D, w ? 0.08D : 0.03D, 0D);
        }
        if (!w && ticksExisted % 40 == 0) {
            worldObj.spawnParticle("dripLava", posX + (rand.nextDouble() - 0.5D) * 0.5D, posY + 0.8D, posZ + (rand.nextDouble() - 0.5D) * 0.5D,
                    0D, 0D, 0D);
        }
    }

    /** Dying (the death went through): the steam burst - STEAM_DAMAGE within STEAM_RADIUS to all living but the Substrate's own. */
    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (worldObj.isRemote || !dead) {
            return;
        }
        double r = STEAM_RADIUS;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(r, r, r));
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == this || !e.isEntityAlive() || e instanceof ISubstrateNativeSC || e.getDistanceSqToEntity(this) > r * r) {
                continue;
            }
            e.attackEntityFrom(DamageSource.causeMobDamage(this), STEAM_DAMAGE);
        }
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + 1D, posZ, 40, r * 0.4D, 0.6D, r * 0.4D, 0.08D);
            ((WorldServer) worldObj).func_147487_a("largesmoke", posX, posY + 1D, posZ, 12, 0.5D, 0.5D, 0.5D, 0.02D);
        }
        playSound("random.fizz", 2F, 0.5F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (wetUntil > 0L) {
            tag.setLong(NBT_WET, wetUntil);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        wetUntil = tag.getLong(NBT_WET);
        dataWatcher.updateObject(DW_WET, Byte.valueOf((byte) (wetUntil > 0L ? 1 : 0)));
    }

    // ------------------------------------------------------------------ sounds: hot metal hissing (vanilla's)

    @Override
    protected String getLivingSound() {
        return rand.nextBoolean() ? "random.fizz" : "mob.blaze.breathe";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected float getSoundPitch() {
        return 0.7F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.25F, 1.2F);
    }
}
