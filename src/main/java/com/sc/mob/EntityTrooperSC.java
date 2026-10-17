package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.mob.ai.EntityAIRechargeAtTraceSC;
import com.sc.mob.ai.EntityAITrooperDashSC;
import com.sc.mob.ai.EntityAITrooperRifleSC;
import com.sc.mob.ai.EntityAITrooperTargetSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.2, docs/idea-dimension-substrate.md §8): «Штурмовик-отступник» (Renegade
 * Trooper) - full crimson Quantum 4/4, reserve by 40..70 blockable damage; half of them «сбойные» - no helium, the emergency
 * mode (iron points). The Quantum Blade (8) or the Plasma Rifle (a beam of 7 after a 1.5 s laser aim). A dash of 6 blocks
 * with a 0.5 s cue, then 1.5 s of recovery when a blow in the back counts x1.5; no dash while a piece is suppressed. It goes
 * for the player in the weakest armour first. HP 40. Drops (ПТ-1): Quantum Scrap 50%; trophies Quantum piece 1%, Quantum
 * Blade 0.7%, Plasma Rifle 0.6%.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP. The numbers below are what the handbook's bestiary prints.
 * <p>
 * The AI: a «down» Trooper runs to a Copper Trace (EntityAIRechargeAtTraceSC, as the Marauder); a blade holder dashes
 * (EntityAITrooperDashSC: the cue IMobActionSC.ACT_DASH, the dash, then ACT_RECOVER - crouched, its back open), a rifle
 * holder aims and fires (EntityAITrooperRifleSC: ACT_AIM, the red laser line); the target is the visible player in the
 * weakest armour (EntityAITrooperTargetSC, looked at again every RETARGET_TICKS), whoever hit it after that. The clients
 * draw the telegraphs' sparks themselves from the DataWatcher (onLivingUpdate below): red sparks at the legs during the
 * cue and the red trail of the dash, steam off its back while it recovers.
 */
public class EntityTrooperSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "trooper";
    public static final double HP = 40D, SPEED = 0.28D, FOLLOW = 32D;
    /** Quantum 4/4 with the crimson light (ModelArmorGlowSC.COLORS 1: #FF4040); the blockable damage that drains it; «сбойные». TODO(balance) */
    public static final int PIECES = 4, GLOW = 1;
    public static final float RESERVE_DAMAGE_MIN = 40F, RESERVE_DAMAGE_MAX = 70F, FAULTY_CHANCE = 0.5F;
    /** In hand: the Quantum Blade, else the Plasma Rifle. TODO(balance) */
    public static final float BLADE_CHANCE = 0.5F;
    /** ПС-10: the rifle's laser aim (1.5 s), a shot every SHOT_EVERY_MIN..MAX ticks (2..3 s), its reach and its red. */
    public static final int AIM_TICKS = 30, SHOT_EVERY_MIN = 40, SHOT_EVERY_MAX = 60, RIFLE_RANGE = 16, RIFLE_COLOR = 0xFF3030;
    /**
     * The dash (a blade holder): a target DASH_FROM..DASH_TO blocks off and in sight, the DASH_CUE_TICKS cue (0.5 s), DASH_LENGTH
     * forward (a blow if it ends next to the target), RECOVER_TICKS (1.5 s) with its back open - a blow from behind x
     * BACKSTAB_MUL; DASH_COOLDOWN from one dash to the next. TODO(balance): the cooldown
     */
    public static final int DASH_CUE_TICKS = 10, RECOVER_TICKS = 30, DASH_COOLDOWN = 100, DASH_FROM = 4, DASH_TO = 8, DASH_LENGTH = 6;
    public static final float BACKSTAB_MUL = 1.5F;
    /** The weakest-armoured visible player is looked for this often (ticks). */
    public static final int RETARGET_TICKS = 40;
    /** Its blows (MobCombatSC's, here for the handbook's bestiary): the blade, the rifle's beam, the fist (a rifle holder up close). */
    public static final int BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()], RIFLE_DAMAGE = MobCombatSC.beamDamage(WeaponType.PLASMA_RIFLE),
            FIST_MIN = MobCombatSC.FIST, FIST_MAX = MobCombatSC.FIST + MobCombatSC.FIST_SPREAD;
    /** ПТ-13: Quantum Armour Scrap. Its group (SpawnRulesSC's): 1..2 in the canyons. */
    public static final float SCRAP_CHANCE = 0.5F;
    public static final int GROUP_MIN = SpawnRulesSC.GROUP_MIN[MobEntitiesSC.ID_TROOPER], GROUP_MAX = SpawnRulesSC.GROUP_MAX[MobEntitiesSC.ID_TROOPER];
    public static final int XP = 10;

    /** Server: the way it faced when it dashed - «behind» is judged by it while it recovers. */
    private float dashYaw;

    public EntityTrooperSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIRechargeAtTraceSC(this, 1.3D));      // «села» - runs for the trace
        tasks.addTask(2, new EntityAITrooperDashSC(this));
        tasks.addTask(3, new EntityAITrooperRifleSC(this, 1.0D));
        tasks.addTask(4, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAITrooperTargetSC(this));          // the weakest armour first (the friend in Nano)
        targetTasks.addTask(2, new EntityAIHurtByTarget(this, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BLADE_DAMAGE);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПС-4, ПС-10: the Quantum Blade 8, the fist 3..4 (the rifle in hand) - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(getHeldItem(), MobCombatSC.FIST, rand) * damageMul());
    }

    /** SpawnDirectorSC, the spawn egg, a spawner: the suit, the weapon, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.QUANTUM, PIECES, GLOW, rand);
            setCurrentItemOrArmor(0, rand.nextFloat() < BLADE_CHANCE ? MobCombatSC.litBlade(BladeType.QUANTUM)
                    : MobCombatSC.gunStack(WeaponType.PLASMA_RIFLE));
            MobArmorSC a = mobArmor();
            boolean faulty = rand.nextFloat() < FAULTY_CHANCE;
            a.helium = !faulty;                              // with helium: suppression -20% (Quantum has no shield)
            a.emergency = faulty;                            // «сбойный»: iron points - before the reserve is counted from them
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    /** ПТ-1, ПТ-13 (the numbers above): Quantum Armour Scrap. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < SCRAP_CHANCE) {
            ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.QUANTUM_SCRAP, 1);
            if (scrap != null) {
                out.add(scrap);
            }
        }
    }

    // ------------------------------------------------------------------ the dash's recovery: the back open

    /** Server (EntityAITrooperDashSC): the dash goes this way - its back is judged by it until the recovery ends. */
    public void setDashYaw(float yaw) {
        dashYaw = yaw;
    }

    public float dashYaw() {
        return dashYaw;
    }

    /** While it recovers from a dash (ACT_RECOVER) a blow from behind - more than 90 degrees off its dash - counts x1.5. */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && amount > 0F && src != null && mobAction() == IMobActionSC.ACT_RECOVER
                && MobCombatSC.behind(this, src.getEntity(), dashYaw)) {
            amount *= BACKSTAB_MUL;
            if (worldObj instanceof WorldServer) {
                ((WorldServer) worldObj).func_147487_a("crit", posX, posY + height * 0.6D, posZ, 10, 0.25D, 0.3D, 0.25D, 0.15D);
            }
        }
        return super.attackEntityFrom(src, amount);
    }

    /** The clients: the telegraphs' sparks (ПС-9) - the cue and the dash's red trail, steam off the open back. */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            return;
        }
        int a = mobAction();
        if (a == IMobActionSC.ACT_DASH) {
            double moved = (posX - prevPosX) * (posX - prevPosX) + (posZ - prevPosZ) * (posZ - prevPosZ);
            int n = moved > 0.25D ? 4 : 1;                   // dashing: a dense trail; the cue: a spark or two at the legs
            for (int i = 0; i < n; i++) {
                double k = n > 1 ? i / (double) n : rand.nextDouble();
                worldObj.spawnParticle("reddust", prevPosX + (posX - prevPosX) * k + (rand.nextDouble() - 0.5D) * 0.5D,
                        posY + 0.2D + rand.nextDouble() * 1.4D, prevPosZ + (posZ - prevPosZ) * k + (rand.nextDouble() - 0.5D) * 0.5D, 1.0D, 0.12D, 0.12D);
            }
        } else if (a == IMobActionSC.ACT_RECOVER && ticksExisted % 3 == 0) {
            double fx = -Math.sin(Math.toRadians(renderYawOffset)), fz = Math.cos(Math.toRadians(renderYawOffset));
            worldObj.spawnParticle("smoke", posX - fx * 0.45D, posY + 1.2D, posZ - fz * 0.45D, -fx * 0.02D, 0.04D, -fz * 0.02D);
        }
    }

    // ------------------------------------------------------------------ sounds: a renegade in a closed helmet (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.villager.idle";
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
        return 0.65F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.2F, 1.4F);
    }
}
