package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.mob.ai.EntityAIEclipseBlinkSC;
import com.sc.mob.ai.EntityAIEclipseFleeSC;
import com.sc.mob.ai.EntityAIEclipseStrikeSC;
import com.sc.mob.ai.EntityAIEclipseTargetSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.util.BladeType;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rule ПС-34): «Затмение» (Singular Renegade «Eclipse»), a rare elite - Singular
 * armour of the top level in the scheme C «Crimson», the shield, reserve 200..250; the Singular Blade (13), a heavy blow of 8
 * «Пробой» after a 1 s wind-up. Hunts the player in the strongest armour; «Мерцание» - a blink of 8 blocks (never under a dome:
 * F_NO_ENDER); under 25% HP it flees and recovers. At most one in the whole dimension (RareRegistrySC.ECLIPSE). HP 140. Drops:
 * Singular Armour Scrap 2..3, a Sealed Crumb 30%; trophies: a Singular piece (level 1) 0.2%, the Singular Blade 0.1%. The Rift.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 15 in MobEntitiesSC), the
 * World constructor (EntityList), HP, the faction, alwaysElite. The numbers below are what the handbook's bestiary prints.
 * <p>
 * Package M:
 * <ul>
 * <li>the suit: the full Singular set of the top level in the scheme C (MobCombatSC.dressSingular - its own crimson light),
 * helium (the shield), a reserve by RESERVE_DAMAGE_MIN..MAX blockable damage - no recharge but while it recovers;</li>
 * <li>the fight: the Singular Blade BLADE_DAMAGE; the heavy blow (EntityAIEclipseStrikeSC: HEAVY_WINDUP of IMobActionSC.ACT_WINDUP,
 * then HEAVY_BLOW of «Пробой» within STRIKE_REACH in front, HEAVY_COOLDOWN; a ПЭ-1 Point in the wind-up breaks it off);
 * the hunt (EntityAIEclipseTargetSC: the visible player in the strongest armour within HUNT_RANGE); «Мерцание»
 * (EntityAIEclipseBlinkSC: BLINK_DISTANCE once in BLINK_MIN..BLINK_MAX ticks after BLINK_CUE_TICKS of ACT_BLINK, never into or out
 * of a field with F_NO_ENDER);</li>
 * <li>the flight (ПС-34): under FLEE_BELOW of its health it lets its target go and runs (ACT_FLEE, EntityAIEclipseFleeSC) from
 * every player within FLEE_DISTANCE; with nobody that near it heals HEAL_PER_SECOND a second and its reserve comes back RECHARGE units a
 * second up to RECHARGE_TO; both there - it hunts again (DW_FLEE, NBT scFlee);</li>
 * <li>one in the dimension: SpawnDirectorSC's own roll in the Rift makes it the record RareRegistrySC.ECLIPSE's ALIVE one; it
 * reports itself seen and its death (SpawnDirectorSC.rareSeen / rareDied - a stale copy leaves the world); never despawns;</li>
 * <li>the clients: crimson motes round it, the blink's flicker, a dark trail in flight; near it (16 blocks) the world's sounds
 * fade (MobClientSC - PlaySoundEvent17).</li>
 * </ul>
 */
public class EntityEclipseSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "eclipse";
    public static final double HP = 140D, SPEED = 0.3D, FOLLOW = 32D;
    /** ПС-34: the Singular top level, the scheme C «Багровая»; the reserve's blockable damage. TODO(balance) */
    public static final int LEVEL = SingularLevel.MAX;
    public static final SingularScheme SCHEME = SingularScheme.C;
    public static final float RESERVE_DAMAGE_MIN = 200F, RESERVE_DAMAGE_MAX = 250F;
    /** ПС-34: its Singular Blade (its own cap, 13); the heavy blow of «Пробой» - wind-up, reach, cone, cooldown. TODO(balance) */
    public static final int BLADE_DAMAGE = 13, HEAVY_BLOW = 8, HEAVY_WINDUP = 20, HEAVY_COOLDOWN = 100;
    public static final double STRIKE_REACH = 3D, STRIKE_HALF_ANGLE = 60D;
    /** The hunt: how far it looks for the strongest armour, how often it looks again. */
    public static final double HUNT_RANGE = 32D;
    public static final int RETARGET_TICKS = 60;
    /** «Мерцание»: how far, how often (BLINK_MIN..BLINK_MAX ticks), its flicker before it goes. */
    public static final double BLINK_DISTANCE = 8D;
    public static final int BLINK_MIN = 100, BLINK_MAX = 200, BLINK_CUE_TICKS = 10;
    /** The flight: under FLEE_BELOW of its health; it runs from players within FLEE_DISTANCE at FLEE_SPEED; heals HEAL_PER_SECOND HP and
     * RECHARGE reserve units a second (nobody that near) until both are at RECHARGE_TO. TODO(balance) */
    public static final float FLEE_BELOW = 0.25F, RECHARGE_TO = 0.6F, HEAL_PER_SECOND = 1F, RECHARGE = 4F;
    public static final double FLEE_DISTANCE = 24D, FLEE_SPEED = 1.4D;
    /** ПТ-1, ПТ-17, ПТ-18: Singular Armour Scrap SCRAP_MIN..MAX, a Sealed Crumb CRUMB_CHANCE (x2 in the Emission). */
    public static final int SCRAP_MIN = 2, SCRAP_MAX = 3;
    public static final float CRUMB_CHANCE = 0.3F;
    public static final int XP = 80;
    /** Client: sounds fade within MUFFLE_RADIUS of it, to HUSH_VOLUME of their volume (MobClientSC). */
    public static final double MUFFLE_RADIUS = 16D;
    public static final float HUSH_VOLUME = 0.4F;
    /** DataWatcher (package M's, §2.6): it flees now (byte 0 / 1). */
    public static final int DW_FLEE = 26;
    public static final String NBT_FLEE = "scFlee";

    private final EntityAIEclipseStrikeSC strike;

    public EntityEclipseSC(World world) {
        super(world);
        setSize(0.7F, 2.0F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        strike = new EntityAIEclipseStrikeSC(this);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIEclipseFleeSC(this));
        tasks.addTask(2, new EntityAIEclipseBlinkSC(this));
        tasks.addTask(3, strike);
        tasks.addTask(4, new EntityAIAttackOnCollide(this, 1.1D, false));
        tasks.addTask(6, new EntityAIWander(this, 0.7D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 12F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIEclipseTargetSC(this));
        targetTasks.addTask(2, new EntityAIHurtByTarget(this, false));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_FLEE, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BLADE_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.7D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПЗ-3: a Singular Renegade - the Renegades'. */
    @Override
    public String faction() {
        return FactionSC.RENEGADES;
    }

    /** ПС-34: a rare elite of its own - «Казнь» never finishes it. */
    @Override
    protected boolean alwaysElite() {
        return true;
    }

    /** ПС-30: the one in the dimension - never despawns. */
    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** ПС-4, ПС-34: the Singular Blade 13 (in hand), else the fist - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        float d = MobCombatSC.blade(this) != null ? BLADE_DAMAGE : MobCombatSC.meleeDamage(null, MobCombatSC.FIST, rand);
        return MobCombatSC.melee(this, target, d * damageMul());
    }

    /** SpawnDirectorSC (its roll in the Rift), the spawn egg: the suit, the blade, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dressSingular(this, LEVEL, SCHEME);
            setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.SINGULAR));
            MobArmorSC a = mobArmor();
            a.helium = true;                                 // the shield
            a.emergency = false;
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    /** A ПЭ-1 Point that reaches it during the heavy blow's wind-up breaks it off (the rest is the base's). */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        EmpSC.HitResult r = super.empHit(shooter, mode, upgraded, pieces);
        if (mode == EmpMode.POINT) {
            strike.breakOff();
        }
        return r;
    }

    // ------------------------------------------------------------------ the flight

    /** It flees and recovers now (both sides: the DataWatcher). */
    public boolean fleeing() {
        return dataWatcher.getWatchableObjectByte(DW_FLEE) != 0;
    }

    private void setFleeing(boolean on) {
        dataWatcher.updateObject(DW_FLEE, Byte.valueOf((byte) (on ? 1 : 0)));
        if (on) {
            super.setAttackTarget(null);
            setMobAction(IMobActionSC.ACT_FLEE);
            playSound("mob.endermen.scream", 0.8F, 0.5F);
        } else if (mobAction() == IMobActionSC.ACT_FLEE) {
            setMobAction(IMobActionSC.ACT_NONE);
        }
    }

    /** No target while it flees (vanilla's HurtByTarget would turn it back to the fight). */
    @Override
    public void setAttackTarget(EntityLivingBase target) {
        if (target != null && worldObj != null && !worldObj.isRemote && fleeing()) {
            return;
        }
        super.setAttackTarget(target);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            clientFx();
            return;
        }
        if (!isEntityAlive()) {
            return;
        }
        boolean flee = fleeing();
        if (!flee && getHealth() < getMaxHealth() * FLEE_BELOW) {
            setFleeing(true);
        } else if (flee && (ticksExisted + getEntityId()) % 20 == 0) {
            recover();
        }
        if ((ticksExisted + getEntityId()) % SpawnDirectorSC.RARE_SEEN_TICKS == 0) {
            SpawnDirectorSC.rareSeen(this);                  // ПС-30: its record; a stale copy leaves
        }
    }

    /** Server, once a second of its flight: nobody within FLEE_DISTANCE - HEAL_PER_SECOND and RECHARGE; both at RECHARGE_TO - it hunts again. */
    private void recover() {
        if (mobAction() == IMobActionSC.ACT_NONE) {
            setMobAction(IMobActionSC.ACT_FLEE);             // after a blink the telegraph is the flight's again
        }
        if (worldObj.getClosestVulnerablePlayerToEntity(this, FLEE_DISTANCE) != null) {
            return;
        }
        MobArmorSC a = mobArmor();
        if (getHealth() < getMaxHealth() * RECHARGE_TO) {
            heal(HEAL_PER_SECOND);
        }
        if (a.reserve < a.reserveMax * RECHARGE_TO) {
            a.recharge(Math.min(RECHARGE, a.reserveMax * RECHARGE_TO - a.reserve));
        }
        if (getHealth() >= getMaxHealth() * RECHARGE_TO && a.reserve >= a.reserveMax * RECHARGE_TO - 1.0E-3F) {
            setFleeing(false);
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead) {
            SpawnDirectorSC.rareDied(this);                  // ПС-34: its 3 days begin
        }
    }

    // ------------------------------------------------------------------ drops

    /** ПТ-1, ПТ-17, ПТ-18: Singular Armour Scrap 2..3, a Sealed Crumb 30% (x2 in the Emission). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.SINGULAR_SCRAP, SCRAP_MIN + rand.nextInt(SCRAP_MAX - SCRAP_MIN + 1));
        if (scrap != null) {
            out.add(scrap);
        }
        if (rand.nextFloat() < MobDropsSC.crumbChance(this, CRUMB_CHANCE)) {
            ItemStack crumb = RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1);
            if (crumb != null) {
                out.add(crumb);
            }
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean(NBT_FLEE, fleeing());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataWatcher.updateObject(DW_FLEE, Byte.valueOf((byte) (tag.getBoolean(NBT_FLEE) ? 1 : 0)));
    }

    // ------------------------------------------------------------------ the clients (ПС-9)

    private void clientFx() {
        if (deathTime > 0) {
            return;
        }
        int a = mobAction();
        if (a == IMobActionSC.ACT_BLINK) {                       // the flicker where it stands
            for (int i = 0; i < 3; i++) {
                worldObj.spawnParticle("portal", posX + (rand.nextDouble() - 0.5D) * width, posY + rand.nextDouble() * height,
                        posZ + (rand.nextDouble() - 0.5D) * width, (rand.nextDouble() - 0.5D) * 2D, -rand.nextDouble(), (rand.nextDouble() - 0.5D) * 2D);
            }
        } else if (a == IMobActionSC.ACT_WINDUP) {               // crimson motes up the raised blade
            worldObj.spawnParticle("reddust", posX + (rand.nextDouble() - 0.5D) * 0.8D, posY + 1.4D + rand.nextDouble() * 0.9D,
                    posZ + (rand.nextDouble() - 0.5D) * 0.8D, 1.0D, 0.15D, 0.25D);
        }
        if (fleeing() && ticksExisted % 2 == 0) {                 // the flight: a dark trail
            worldObj.spawnParticle("largesmoke", posX, posY + 0.8D, posZ, 0D, 0.02D, 0D);
        } else if (ticksExisted % 6 == 0) {                       // its crimson motes, drifting
            worldObj.spawnParticle("reddust", posX + (rand.nextDouble() - 0.5D) * 1.2D, posY + rand.nextDouble() * height,
                    posZ + (rand.nextDouble() - 0.5D) * 1.2D, 0.6D, 0.02D, 0.06D);
        }
    }

    // ------------------------------------------------------------------ sounds: hushed, heavy (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.endermen.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wither.death";
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    protected float getSoundPitch() {
        return 0.4F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.4F, 0.9F);
    }
}
