package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAIShellPatrolSC;
import com.sc.mob.ai.EntityAIShellRebootSC;
import com.sc.mob.ai.EntityAIShellReturnSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.SoundsSC;

import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): «Пустая оболочка» (Hollow Shell) - the Lithographers' empty Exo 4/4,
 * cold white light, reserve by 70..110; with helium the shield; without («Ржавая») the emergency mode, no shield. The Exo
 * Blade 15% (10), the Quantum Blade 25% (8), else fists (6). Patrols of 2..3 at the Dead Nodes, 30% with two Repair Drones
 * (EntityRepairDroneSC: 3 units a second into its reserve). It attacks anything living. A ПЭ-1 Pulse that suppresses it also
 * makes it «reboot»: 3 s standing still (IMobActionSC.ACT_REBOOT; Ф4's EMP storm does it on every pulse). HP 50. Drops:
 * Shell Firmware 1..3 100%, a liquid helium cell 10%; trophies Exo piece 0.5%, Exo Blade 0.3%, Quantum Blade 0.7%.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP. The numbers below are what the handbook's bestiary prints.
 * <p>
 * The patrol (SpawnDirectorSC.setPatrol at the spawn - an egg's Shell has none and just wanders): one patrol id for the
 * group and its home, the Dead Node's crossing - vanilla's home area of LEASH blocks (a target beyond it isn't taken, the
 * wander stays inside), EntityAIShellReturnSC brings it back when a chase took it past LEASH, EntityAIShellPatrolSC walks
 * the zone in line: the member with the lowest UUID leads to random points of the zone, the others keep abreast of it.
 * NBT: "scPatrol" (long), "scNode" (int[3] - the home). The reboot (ПБ-21): reboot(ticks) - public for Ф4's storm; the
 * empHit below calls it on a Pulse that suppressed a piece. EntityAIShellRebootSC (all the bits) keeps it standing.
 */
public class EntityHollowShellSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "hollowShell";
    public static final double HP = 50D, SPEED = 0.24D, FOLLOW = 32D;
    /** Exo 4/4 with the cold white light (ModelArmorGlowSC.COLORS 9); the blockable damage that drains it; «Ржавая» (no helium). TODO(balance) */
    public static final int PIECES = 4, GLOW = 9;
    public static final float RESERVE_DAMAGE_MIN = 70F, RESERVE_DAMAGE_MAX = 110F, RUSTY_CHANCE = 0.3F;
    /** In hand: the Exo Blade, else the Quantum Blade, else its fists. */
    public static final float EXO_BLADE_CHANCE = 0.15F, QUANTUM_BLADE_CHANCE = 0.25F;
    /** ПС-10: its blows - the fist (no spread: the cap), the Exo and the Quantum Blade (MobCombatSC's). */
    public static final int FIST_DAMAGE = 6, EXO_BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.EXO.ordinal()],
            QUANTUM_BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()];
    /** ПБ-21: a Pulse that suppresses it - this long standing still. */
    public static final int REBOOT_TICKS = 60;
    /** The patrol: its home's reach (from the crossing), the zone it walks (RuinLayoutSC.NODE_HALF), the line's spacing. */
    public static final int LEASH = 48, ZONE = RuinLayoutSC.NODE_HALF, SPACING = 2;
    /** ПТ-1: Shell Firmware 1..3 (100%), a liquid helium cell. TODO(balance) */
    public static final int FIRMWARE_MIN = 1, FIRMWARE_MAX = 3;
    public static final float HELIUM_CELL_CHANCE = 0.1F;
    /** Its group (SpawnRulesSC's): 2..3, two repair drones with DRONE_PATROL_CHANCE of the patrols. */
    public static final int GROUP_MIN = SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_HOLLOW_SHELL], GROUP_MAX = SpawnRulesSC.GROUP_MAX[MobEntitiesSC.ID_HOLLOW_SHELL],
            PATROL_DRONES = SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_REPAIR_DRONE];
    public static final float DRONE_PATROL_CHANCE = SpawnRulesSC.DRONE_PATROL_CHANCE;
    public static final int XP = 12;
    public static final String NBT_PATROL = "scPatrol", NBT_NODE = "scNode";

    /** «Свои» are the Substrate's hostiles; everything else living is a target (ПС-8: «нападает на всё живое»). */
    public static final IEntitySelector NOT_OURS = new IEntitySelector() {
        @Override
        public boolean isEntityApplicable(Entity e) {
            return e instanceof EntityLivingBase && !MobCombatSC.hostileNative(e);
        }
    };

    private long patrol, rebootUntil;
    private boolean hasNode;
    private int homeX, homeY, homeZ;

    public EntityHollowShellSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAIShellRebootSC(this));
        tasks.addTask(1, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIShellReturnSC(this, 1.0D));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(5, new EntityAIShellPatrolSC(this, 0.8D));
        tasks.addTask(6, new EntityAIWander(this, 0.7D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));      // the patrol answers together
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityLivingBase.class, 10, true, false, NOT_OURS));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(FIST_DAMAGE);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПС-10: the fist 6, the Exo Blade 10, the Quantum Blade 8 - never the held item's attributes; x damageMul(); none while it reboots. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (rebooting()) {
            return false;
        }
        float d = MobCombatSC.blade(this) != null ? MobCombatSC.meleeDamage(getHeldItem(), FIST_DAMAGE, rand) : FIST_DAMAGE;
        return MobCombatSC.melee(this, target, d * damageMul());
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.EXO, PIECES, GLOW, rand);
            float r = rand.nextFloat();
            if (r < EXO_BLADE_CHANCE) {
                setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.EXO));
            } else if (r < EXO_BLADE_CHANCE + QUANTUM_BLADE_CHANCE) {
                setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.QUANTUM));
            }
            MobArmorSC a = mobArmor();
            boolean rusty = rand.nextFloat() < RUSTY_CHANCE;
            a.helium = !rusty;                               // with helium: the Exo shield
            a.emergency = rusty;                             // «Ржавая»: iron points, no shield
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    /** ПТ-1: Shell Firmware 1..3, a liquid helium cell. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack firmware = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, FIRMWARE_MIN + rand.nextInt(FIRMWARE_MAX - FIRMWARE_MIN + 1));
        if (firmware != null) {
            out.add(firmware);
        }
        if (ModItems.liquidHeCell != null && rand.nextFloat() < HELIUM_CELL_CHANCE) {
            out.add(new ItemStack(ModItems.liquidHeCell));
        }
    }

    // ------------------------------------------------------------------ the patrol

    /**
     * Server (SpawnDirectorSC, before it joins the world): one of patrol `id` (not 0), home - the Dead Node's crossing at
     * (x, y, z): vanilla's home area of LEASH blocks.
     */
    public void setPatrol(long id, int x, int y, int z) {
        patrol = id;
        hasNode = true;
        homeX = x;
        homeY = y;
        homeZ = z;
        setHomeArea(x, y, z, LEASH);
    }

    /** Its patrol's id, 0 - none (an egg's). */
    public long patrol() {
        return patrol;
    }

    public boolean hasNode() {
        return hasNode;
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

    // ------------------------------------------------------------------ ПБ-21: the reboot

    /** Server: stands still and doesn't hit for `ticks` (a suppressing Pulse; Ф4 - each pulse of the EMP storm). */
    public void reboot(int ticks) {
        if (worldObj.isRemote || ticks <= 0 || !isEntityAlive()) {
            return;
        }
        long until = worldObj.getTotalWorldTime() + ticks;
        boolean was = rebooting();
        rebootUntil = Math.max(rebootUntil, until);
        setMobAction(IMobActionSC.ACT_REBOOT);
        getNavigator().clearPathEntity();
        if (!was) {
            SoundsSC.play(worldObj, posX, posY + 1.2D, posZ, "emp.reboot", 0.9F, 0.9F + rand.nextFloat() * 0.2F);
            if (worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 1.4D, posZ, 10, 0.25D, 0.35D, 0.25D, 0.05D);
            }
        }
    }

    /** It is rebooting now (server: by its clock; client: by the telegraph). */
    public boolean rebooting() {
        if (worldObj.isRemote) {
            return mobAction() == IMobActionSC.ACT_REBOOT;
        }
        return rebootUntil > worldObj.getTotalWorldTime();
    }

    /** ПБ-21: a Pulse that suppressed a piece of it - the reboot (the rest is the base's: suppression, the shield down). */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        EmpSC.HitResult r = super.empHit(shooter, mode, upgraded, pieces);
        if (mode == EmpMode.PULSE && r != null && r.anySuppressed()) {
            reboot(REBOOT_TICKS);
        }
        return r;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            if (mobAction() == IMobActionSC.ACT_REBOOT && !rebooting()) {
                setMobAction(IMobActionSC.ACT_NONE);         // back up: the lights flick on
                SoundsSC.play(worldObj, posX, posY + 1.2D, posZ, "power.on", 0.7F, 1.1F);
            }
            return;
        }
        if (mobAction() == IMobActionSC.ACT_REBOOT && ticksExisted % 4 == 0) {   // the clients: sparks off the stalled suit
            worldObj.spawnParticle("fireworksSpark", posX + (rand.nextDouble() - 0.5D) * 0.6D, posY + 0.6D + rand.nextDouble() * 1.2D,
                    posZ + (rand.nextDouble() - 0.5D) * 0.6D, 0D, 0.02D, 0D);
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (patrol != 0L) {
            tag.setLong(NBT_PATROL, patrol);
        }
        if (hasNode) {
            tag.setIntArray(NBT_NODE, new int[]{homeX, homeY, homeZ});
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        patrol = tag.getLong(NBT_PATROL);
        int[] n = tag.getIntArray(NBT_NODE);
        if (n.length == 3) {
            setPatrol(patrol, n[0], n[1], n[2]);
        }
    }

    // ------------------------------------------------------------------ sounds: an empty suit (vanilla's)

    @Override
    protected String getLivingSound() {
        return "random.click";                              // the scanning beam's click
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
        return 0.75F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.3F, 1.1F);       // in step
    }
}
