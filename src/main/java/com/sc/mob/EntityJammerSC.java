package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpModule;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAIEmpShooterSC;
import com.sc.mob.ai.EntityAIJammerEmitterSC;
import com.sc.mob.ai.EntityAIJammerKeepBackSC;
import com.sc.util.ArmorSuit;
import com.sc.util.WeaponType;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
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

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.3): «Глушитель» (Renegade Jammer) - Quantum with helium and a Defense chip
 * (defenseTier 3: suppression -30%, the siphon -40%), antenna «horns», the personal EMP screen (MobArmorSC.screen: the
 * first ПЭ-1 shot is lost on it, back in 30 s). The Pulse Emitter (a burst of 3 rays of 3). One in 50 carries a real ПЭ-1
 * (dimension.mobsUseEmp; else the Ion Cutter): a Point every 15 s after a 1 s warning, a Pulse only at players standing
 * together (3 s of hum heard 16 blocks off, cover by the line of sight saves) - EmpMobGunSC / EntityAIEmpShooterSC
 * (package A); a suppressed Jammer doesn't fire it. Stays behind the others. HP 36. Drops: Quantum Scrap 40%, a ПЭ-1 module
 * 5% (a carrier 25%); trophies Quantum piece 1%, Pulse Emitter 1%, the ПЭ-1 2% - a carrier's only.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP. The numbers below are what the handbook's bestiary prints.
 * <p>
 * The AI: it keeps KEEP_MIN..KEEP_MAX blocks from its target, behind the nearest armoured ally if there is one
 * (EntityAIJammerKeepBackSC - the walking), and shoots from there (EntityAIJammerEmitterSC: the aim ACT_AIM, the burst;
 * look only). A carrier holds the ПЭ-1 in place of the emitter (seen in hand, its trophy's category) and has package A's
 * EntityAIEmpShooterSC(this, true, true, false, EmpMobGunSC.RANGE) - added when it gets the gun (spawning) or is loaded
 * with one, so a plain Jammer never carries the task. With the rule off (EmpMobGunSC.allowed() false) the one in 50 gets the
 * Ion Cutter instead - no mob holds a ПЭ-1 then.
 */
public class EntityJammerSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "jammer";
    public static final double HP = 36D, SPEED = 0.26D, FOLLOW = 32D;
    /** Quantum 4/4 with helium, the crimson light (ModelArmorGlowSC.COLORS 1), a Defense chip III, the personal screen; the reserve's damage. */
    public static final int PIECES = 4, GLOW = 1, DEFENSE_TIER = 3;
    public static final float RESERVE_DAMAGE_MIN = 40F, RESERVE_DAMAGE_MAX = 60F;
    /** ПБ-17, ПБ-18: one in 50 carries a ПЭ-1 (TODO(balance)); its reach (EmpMobGunSC's). */
    public static final float CARRIER_CHANCE = 0.02F;
    public static final int EMP_RANGE = EmpMobGunSC.RANGE;
    /**
     * ПС-10: the Pulse Emitter - an aim of AIM_TICKS (1 s), a burst of BURST rays of BURST_DAMAGE, BURST_GAP_TICKS ticks apart,
     * then RELOAD_TICKS; its reach and violet.
     */
    public static final int AIM_TICKS = 20, BURST = 3, BURST_GAP_TICKS = 5, RELOAD_TICKS = 60, EMITTER_RANGE = 16, EMITTER_COLOR = 0xB070FF;
    public static final int BURST_DAMAGE = MobCombatSC.beamDamage(WeaponType.PULSE_EMITTER), ION_BEAM_DAMAGE = MobCombatSC.beamDamage(WeaponType.ION_CUTTER),
            FIST_MIN = MobCombatSC.FIST, FIST_MAX = MobCombatSC.FIST + MobCombatSC.FIST_SPREAD;
    /** Its place in a fight: this many blocks from the target, behind an armoured ally within ALLY_RADIUS. */
    public static final int KEEP_MIN = 8, KEEP_MAX = 14, ALLY_RADIUS = 16;
    /** ПТ-13: Quantum Armour Scrap; a random ПЭ-1 module (a carrier's chance). TODO(balance) */
    public static final float SCRAP_CHANCE = 0.4F, MODULE_CHANCE = 0.05F, CARRIER_MODULE_CHANCE = 0.25F;
    /** At its spawn a Trooper comes with it (SpawnRulesSC's chance, if the caps allow). */
    public static final float ESCORT_CHANCE = SpawnRulesSC.JAMMER_ESCORT_CHANCE;
    public static final int XP = 12;

    /** The ПЭ-1 task is in its tasks (a carrier). */
    private boolean empTask;

    public EntityJammerSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIJammerEmitterSC(this));
        tasks.addTask(3, new EntityAIJammerKeepBackSC(this, 1.0D));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(MobCombatSC.FIST);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПС-4: the fist 3..4 (a gun in hand) - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(getHeldItem(), MobCombatSC.FIST, rand) * damageMul());
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.QUANTUM, PIECES, GLOW, rand);
            ItemStack gun;
            if (rand.nextFloat() < CARRIER_CHANCE) {
                gun = EmpMobGunSC.allowed() ? MobCombatSC.pe1Stack() : MobCombatSC.gunStack(WeaponType.ION_CUTTER);
            } else {
                gun = MobCombatSC.gunStack(WeaponType.PULSE_EMITTER);
            }
            setCurrentItemOrArmor(0, gun);
            MobArmorSC a = mobArmor();
            a.helium = true;
            a.emergency = false;
            a.defenseTier = DEFENSE_TIER;
            a.screen = true;                                 // ПБ-19: the first ПЭ-1 shot is lost on it
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

    /** It holds a ПЭ-1 (the trophy category, the module's chance). */
    public boolean carrier() {
        return MobCombatSC.holdsPe1(this);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (!worldObj.isRemote) {
            armEmp();
        }
    }

    /** ПТ-1, ПТ-13: Quantum Armour Scrap; a random ПЭ-1 module (more often from a carrier). */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < SCRAP_CHANCE) {
            ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.QUANTUM_SCRAP, 1);
            if (scrap != null) {
                out.add(scrap);
            }
        }
        if (ModItems.empModule != null && rand.nextFloat() < (carrier() ? CARRIER_MODULE_CHANCE : MODULE_CHANCE)) {
            EmpModule[] all = EmpModule.values();
            out.add(ModItems.empModule.stackOf(all[rand.nextInt(all.length)]));
        }
    }

    // ------------------------------------------------------------------ sounds: a renegade, its antennas crackling (vanilla's)

    @Override
    protected String getLivingSound() {
        return rand.nextBoolean() ? "random.click" : "mob.villager.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.villager.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.8F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.15F, 1.6F);
    }
}
