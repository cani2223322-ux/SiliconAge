package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMobGunSC;
import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.mob.ai.EntityAIEmpShooterSC;
import com.sc.mob.ai.EntityAISentinelLeashSC;
import com.sc.mob.ai.EntityAISentinelPressSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.util.BladeType;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.WeaponType;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.3, rule ПС-33): «Сингулярный часовой» (Singular Sentinel) - Singular armour of
 * level 1 (a senior one: the top level), the shield, reserve 150..250, 4 units a second back after 5 s without a blow. The
 * Singular Blade 30% (14), else the Exo Blade (10). 5% carry a ПЭ-1 - a Point and a Pulse by the rules of ПБ-17, 32 blocks. One in
 * each Vault, never further than 24 blocks from it; the gravity press (radius 6, 5 s, every 30 s, after 3 s of a hum). «Казнь»
 * never finishes it. HP 100. Drops: Shell Firmware 3..5, a Sealed Crumb 25%, the Fragment of the Breakdown Key 100%; trophies: a
 * Singular piece 0.2%, the Singular Blade 0.1%, the ПЭ-1 10% of a carrier.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 14 in MobEntitiesSC), the
 * World constructor (EntityList), HP, the faction, alwaysElite. The numbers below are what the handbook's bestiary prints.
 * <p>
 * Package M:
 * <ul>
 * <li>the suit: the full Singular set in the scheme H «Пустота» (MobCombatSC.dressSingular - its own animated light), level 1 -
 * SENIOR_CHANCE of them the top level (SingularLevel.MAX), set once on spawning; helium in the loop (the shield); a reserve by
 * RESERVE_DAMAGE_MIN..MAX blockable damage, REGEN_PER_SECOND units a second back after REGEN_DELAY_TICKS ticks without a blow (MobArmorSC);</li>
 * <li>the weapon: the Singular Blade (SINGULAR_BLADE_DAMAGE) with SINGULAR_BLADE_CHANCE, else the Exo Blade (EXO_BLADE_DAMAGE);
 * CARRIER_CHANCE carry a ПЭ-1 instead (EmpMobGunSC.allowed(); EntityAIEmpShooterSC(this, true, true, false, EMP_RANGE): a Point,
 * a Pulse only at a group - ПБ-17) and fight with the fist up close; with the rule off a carrier holds the Ion Cutter;</li>
 * <li>the gravity press (EntityAISentinelPressSC, ПС-9): a player target within PRESS_RADIUS + 2 - PRESS_HUM_TICKS of a low hum
 * (ACT_PRESS, heard HUM_RANGE off), then PRESS_TICKS of every player within PRESS_RADIUS pinned down: Slowness IV, no jump (in
 * the air - pulled to the ground at once); the next one PRESS_EVERY after its hum began; a suppressed chestplate - no press;</li>
 * <li>the Vault (package C, through SpawnDirectorSC.spawnAt and RuinWorldSC.settle): its home - vanilla's home area, the leash
 * the bunker's half size + LEASH (the Forge data scHome {x, y, z, r, l}, put back after a reload - MobCombatSC.restoreHome): it
 * never goes further and lets a target out of it go (EntityAISentinelLeashSC); its record
 * RareRegistrySC.vaultKey(i, j) (C marks it ALIVE; seen / its death - SpawnDirectorSC.rareSeen / rareDied); never despawns;</li>
 * <li>the clients: a ring of dark particles over its shoulders, the hum's dark motes sinking round it, the press's falling
 * shards (DW_PRESS).</li>
 * </ul>
 */
public class EntitySentinelSC extends EntityArmoredMobSC implements IFactionLootSC, ITrophyCategoriesSC {

    public static final String NAME = "sentinel";
    public static final double HP = 100D, SPEED = 0.25D, FOLLOW = 24D;
    /** ПС-33: the Singular level 1, the senior ones' (SENIOR_CHANCE) the top; the scheme H «Пустота». TODO(balance) */
    public static final int LEVEL = SingularLevel.MIN, SENIOR_LEVEL = SingularLevel.MAX;
    public static final float SENIOR_CHANCE = 0.2F;
    public static final SingularScheme SCHEME = SingularScheme.H;
    /** The reserve's blockable damage; its recharge (units a second) after REGEN_DELAY_TICKS ticks without a blow. */
    public static final float RESERVE_DAMAGE_MIN = 150F, RESERVE_DAMAGE_MAX = 250F, REGEN_PER_SECOND = 4F;
    public static final int REGEN_DELAY_TICKS = 100;
    /** ПС-33: the blades (its own caps: the Singular 14, the Exo 10) and the Singular one's chance. */
    public static final int SINGULAR_BLADE_DAMAGE = 14, EXO_BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.EXO.ordinal()];
    public static final float SINGULAR_BLADE_CHANCE = 0.3F;
    /** ПБ-17: a ПЭ-1 carrier's share and reach; the Ion Cutter in its place with the rule off (its aim, reach, colour). */
    public static final float CARRIER_CHANCE = 0.05F;
    public static final int EMP_RANGE = 32, CUTTER_AIM_TICKS = 20, CUTTER_RANGE = 16, CUTTER_COLOR = 0x59D9FF;
    /** The press: radius, hum, press, cooldown (ticks), the hum's range; pulled down at this speed while airborne. TODO(balance) */
    public static final int PRESS_RADIUS = 6, PRESS_HUM_TICKS = 60, PRESS_TICKS = 100, PRESS_EVERY = 600, HUM_RANGE = 24;
    public static final double PRESS_PULL = 0.9D;
    /** Slowness IV (the amplifier), refreshed every SLOW_EVERY ticks for SLOW_TICKS. */
    public static final int SLOW_AMPLIFIER = 3, SLOW_EVERY = 10, SLOW_TICKS = 20;
    /** ПХ-3: its leash past the Vault's bunker (RuinWorldSC.SENTINEL_LEASH; without a radius of its own - from home; an egg's has no home). */
    public static final int LEASH = 24;
    /** ПТ-1, ПТ-17: Shell Firmware FIRMWARE_MIN..MAX, a Sealed Crumb CRUMB_CHANCE (x2 in the Emission), the Fragment 100%. */
    public static final int FIRMWARE_MIN = 3, FIRMWARE_MAX = 5;
    public static final float CRUMB_CHANCE = 0.25F;
    /** ПТ-16 (TrophyTableSC): a carrier's ПЭ-1 as a trophy (the handbook reads it here). */
    public static final float CARRIER_PE1 = TrophyTableSC.SENTINEL_PE1;
    public static final int XP = 50;
    /** DataWatcher (package M's, §2.6): the press's stage (byte: STAGE_*). */
    public static final int DW_PRESS = 26;
    public static final int STAGE_NONE = 0, STAGE_HUM = 1, STAGE_PRESS = 2;

    /** Server: the ПЭ-1 task is in its tasks (a carrier); the world time the press ends (0 - none). */
    private boolean empTask;
    private long pressUntil;

    public EntitySentinelSC(World world) {
        super(world);
        setSize(0.7F, 2.2F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAISentinelLeashSC(this, 1.1D));
        tasks.addTask(2, new EntityAISentinelPressSC(this));
        tasks.addTask(3, new EntityAIBeamAttackSC(this, 1.0D, CUTTER_AIM_TICKS, 1, 1, CUTTER_RANGE, CUTTER_COLOR));
        tasks.addTask(4, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(6, new EntityAIWander(this, 0.6D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 12F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_PRESS, Byte.valueOf((byte) STAGE_NONE));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(EXO_BLADE_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.8D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПЗ-3: the Lithographers' own guard - the Shells'. */
    @Override
    public String faction() {
        return FactionSC.SHELLS;
    }

    /** ПС-33: «Казнь» never finishes it, no modifier. */
    @Override
    protected boolean alwaysElite() {
        return true;
    }

    /** ПС-30: a Vault's one - never despawns (its Vault brings it back only after its death). */
    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** ПС-4, ПС-33: the Singular Blade 14, the Exo Blade 10, else the fist - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        BladeType b = MobCombatSC.blade(this);
        float d = b == BladeType.SINGULAR ? SINGULAR_BLADE_DAMAGE : b != null ? EXO_BLADE_DAMAGE
                : MobCombatSC.meleeDamage(null, MobCombatSC.FIST, rand);
        return MobCombatSC.melee(this, target, d * damageMul());
    }

    /** SpawnDirectorSC.spawnAt (package C's Vault), the spawn egg: the suit, the weapon, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dressSingular(this, rand.nextFloat() < SENIOR_CHANCE ? SENIOR_LEVEL : LEVEL, SCHEME);
            ItemStack held;
            if (rand.nextFloat() < CARRIER_CHANCE) {
                held = EmpMobGunSC.allowed() ? MobCombatSC.pe1Stack() : MobCombatSC.gunStack(WeaponType.ION_CUTTER);
            } else {
                held = MobCombatSC.litBlade(rand.nextFloat() < SINGULAR_BLADE_CHANCE ? BladeType.SINGULAR : BladeType.EXO);
            }
            setCurrentItemOrArmor(0, held);
            MobArmorSC a = mobArmor();
            a.helium = true;                                 // the shield
            a.emergency = false;
            a.regenPerSecond = REGEN_PER_SECOND;
            a.regenDelayTicks = REGEN_DELAY_TICKS;
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
            armEmp();
        }
        return data;
    }

    /** A carrier: package A's ПЭ-1 task, once (it fires by EmpMobGunSC's rules; nothing while !allowed()). */
    private void armEmp() {
        if (!empTask && MobCombatSC.holdsPe1(this)) {
            empTask = true;
            tasks.addTask(1, new EntityAIEmpShooterSC(this, true, true, false, EMP_RANGE));
        }
    }

    /** It holds a ПЭ-1 (its trophy's category). */
    public boolean carrier() {
        return MobCombatSC.holdsPe1(this);
    }

    /** Its suit is of the top level (a senior Sentinel; both sides - the worn chestplate's NBT). */
    public boolean senior() {
        ItemStack chest = MobArmorSC.modPiece(this, 1);
        return chest != null && SingularLevel.levelOf(chest) >= SENIOR_LEVEL;
    }

    // ------------------------------------------------------------------ the press

    /** The press's stage now (both sides: the DataWatcher). */
    public int pressStage() {
        return dataWatcher.getWatchableObjectByte(DW_PRESS);
    }

    /** The players about it are pinned down now (both sides). */
    public boolean pressing() {
        return pressStage() == STAGE_PRESS;
    }

    /** Server (EntityAISentinelPressSC). */
    public void setPressStage(int stage) {
        if (pressStage() != stage) {
            dataWatcher.updateObject(DW_PRESS, Byte.valueOf((byte) stage));
        }
    }

    /** Server (EntityAISentinelPressSC, after the hum): PRESS_TICKS of the press begin. */
    public void startPress() {
        pressUntil = worldObj.getTotalWorldTime() + PRESS_TICKS;
        setPressStage(STAGE_PRESS);
        if (mobAction() != IMobActionSC.ACT_PRESS) {
            setMobAction(IMobActionSC.ACT_PRESS);
        }
        playSound("mob.wither.shoot", 1.2F, 0.4F);
        playSound("random.anvil_land", 0.8F, 0.4F);
    }

    /** Server, every tick of the press: the players within PRESS_RADIUS (not creative) pinned down. */
    private void press(long now) {
        if (now >= pressUntil || !isEntityAlive()) {
            pressUntil = 0L;
            setPressStage(STAGE_NONE);
            if (mobAction() == IMobActionSC.ACT_PRESS) {
                setMobAction(IMobActionSC.ACT_NONE);
            }
            return;
        }
        double r2 = PRESS_RADIUS * PRESS_RADIUS;
        boolean slow = (now + getEntityId()) % SLOW_EVERY == 0;
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.isCreativeMode || p instanceof FakePlayer || p.getDistanceSqToEntity(this) > r2) {
                continue;
            }
            if (slow) {
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, SLOW_TICKS, SLOW_AMPLIFIER));
            }
            if (!p.onGround) {
                p.motionY = Math.min(p.motionY, -PRESS_PULL);    // no jump: back to the ground at once
                p.velocityChanged = true;                         // the tracker sends it to the player itself too
            }
        }
    }

    // ------------------------------------------------------------------ the Vault: its home and leash, its record

    /** Server: its home - the bunker's middle, its half size `r`, the leash r + LEASH (RuinWorldSC.settle's format). */
    public void setHome(int x, int y, int z, int r) {
        MobCombatSC.setHome(this, x, y, z, r, r + LEASH);
    }

    /** It has a home to keep to (vanilla's home area - set by its Vault). */
    public boolean hasLeash() {
        return hasHome();
    }

    public int homeX() {
        return getHomePosition().posX;
    }

    public int homeY() {
        return getHomePosition().posY;
    }

    public int homeZ() {
        return getHomePosition().posZ;
    }

    /** Further than the leash (vanilla's home distance, + `margin`) from home - horizontally or up and down. */
    public boolean beyondLeash(double margin) {
        if (!hasHome()) {
            return false;
        }
        double r = func_110174_bM() + margin, dx = posX - (homeX() + 0.5D), dz = posZ - (homeZ() + 0.5D);
        return dx * dx + dz * dz > r * r || Math.abs(posY - homeY()) > r;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            clientFx();
            return;
        }
        long now = worldObj.getTotalWorldTime();
        if (pressUntil > 0L) {
            press(now);
        }
        if ((ticksExisted + getEntityId()) % SpawnDirectorSC.RARE_SEEN_TICKS == 0 && isEntityAlive()) {
            SpawnDirectorSC.rareSeen(this);                  // ПС-30: its record; a stale copy leaves
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead) {
            SpawnDirectorSC.rareDied(this);                  // ПХ-4: the Vault's 7 days begin
        }
    }

    // ------------------------------------------------------------------ drops, trophies

    /** ПТ-1, ПТ-17: Shell Firmware 3..5, a Sealed Crumb 25% (x2 in the Emission), the Fragment of the Breakdown Key. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack firmware = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, FIRMWARE_MIN + rand.nextInt(FIRMWARE_MAX - FIRMWARE_MIN + 1));
        if (firmware != null) {
            out.add(firmware);
        }
        if (rand.nextFloat() < MobDropsSC.crumbChance(this, CRUMB_CHANCE)) {
            ItemStack crumb = RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1);
            if (crumb != null) {
                out.add(crumb);
            }
        }
        ItemStack fragment = RelicItemsSC.relic(ItemRelicSC.KEY_FRAGMENT, 1);
        if (fragment != null) {
            out.add(fragment);
        }
    }

    /**
     * ПТ-16 (§7.3): only a Singular piece (0.2%), a Singular Blade (0.1%) and a carrier's ПЭ-1 at its own 10% - an Exo Blade in
     * its hand, an Ion Cutter (mobsUseEmp off) are no trophy of a Sentinel's.
     */
    @Override
    public float[] trophyCategories(float[] standard) {
        if (MobCombatSC.blade(this) != BladeType.SINGULAR) {
            standard[MobDropsSC.C_BLADE] = 0F;
        }
        standard[MobDropsSC.C_GUN] = 0F;
        if (standard[MobDropsSC.C_PE1] > 0F) {
            standard[MobDropsSC.C_PE1] = TrophyTableSC.SENTINEL_PE1;
        }
        return standard;
    }

    @Override
    public int trophyMeta(int index, Random rand) {
        return -1;
    }

    // ------------------------------------------------------------------ NBT

    /** The home lives in the Forge data (saved with it); vanilla's home area is put back from it. */
    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        MobCombatSC.restoreHome(this, LEASH);
        if (!worldObj.isRemote) {
            armEmp();
        }
    }

    // ------------------------------------------------------------------ the clients: the ring, the hum, the press (ПС-9)

    private void clientFx() {
        if (deathTime > 0) {
            return;
        }
        double a = ticksExisted * 0.35D;                         // the ring over its shoulders, two motes turning
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            worldObj.spawnParticle("smoke", posX + Math.cos(ang) * 0.75D, posY + height + 0.15D, posZ + Math.sin(ang) * 0.75D, 0D, 0D, 0D);
        }
        int stage = pressStage();
        if (stage == STAGE_HUM) {                                // dark motes drawn in to it
            double ang = rand.nextDouble() * Math.PI * 2D, r = 1.5D + rand.nextDouble() * (PRESS_RADIUS - 1.5D);
            worldObj.spawnParticle("portal", posX, posY + 0.3D, posZ, Math.cos(ang) * r, rand.nextDouble() * 1.5D, Math.sin(ang) * r);
        } else if (stage == STAGE_PRESS) {                       // the press: shards falling to the ground all round
            for (int i = 0; i < 3; i++) {
                double ang = rand.nextDouble() * Math.PI * 2D, r = rand.nextDouble() * PRESS_RADIUS;
                worldObj.spawnParticle("portal", posX + Math.cos(ang) * r, posY + 0.1D, posZ + Math.sin(ang) * r, 0D, 2.5D, 0D);
            }
        }
    }

    // ------------------------------------------------------------------ sounds: a cold, heavy guard (vanilla's)

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
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.45F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.7F, 0.7F);
    }
}
