package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAICleaveSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;

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
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.5): «Экзо-палач» (Exo Executioner) - an elite of its kind (alwaysElite:
 * «Казнь» never finishes it, no modifier), Exo 4/4 with helium and the shield, reserve by 100..140 (Ф4: +1 a second at a
 * stolen charge pad). The Exo Blade (11). «Рассечение»: a 1 s wind-up (IMobActionSC.ACT_WINDUP), then a 3-block cone of 6
 * «Пробой» (BreakdownDamageSC - through the Exo shield too); a ПЭ-1 Point during the wind-up breaks it off; no «Рассечение»
 * while its chestplate is suppressed. HP 70. Drops: Exo Scrap 1..2 100%, a Shield Core 15%, a liquid helium cell 50%;
 * trophies Exo piece 0.5%, Exo Blade 0.3%.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP. The numbers below are what the handbook's bestiary prints.
 * <p>
 * «Рассечение» is EntityAICleaveSC (above the melee): a target within CLEAVE_REACH, the wind-up standing and facing it (its
 * way locked when the wind-up starts - stepping aside saves), then everything living in the cone CLEAVE_REACH deep and
 * CLEAVE_HALF_ANGLE to each side but the Substrate's own hostiles takes CLEAVE_DAMAGE of «Пробой» (x damageMul()); next one
 * CLEAVE_COOLDOWN later. The empHit below breaks a wind-up on any ПЭ-1 Point that reaches it. Drawn a little larger
 * (RenderExecutionerSC), the helium tank on its back frosted.
 */
public class EntityExecutionerSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "executioner";
    public static final double HP = 70D, SPEED = 0.27D, FOLLOW = 32D;
    /** Exo 4/4 with helium and the shield, the crimson light (ModelArmorGlowSC.COLORS 1); the blockable damage that drains it. */
    public static final int PIECES = 4, GLOW = 1;
    public static final float RESERVE_DAMAGE_MIN = 100F, RESERVE_DAMAGE_MAX = 140F;
    /** ПС-10: its Exo Blade (its own number, above everyone else's 10). */
    public static final int BLADE_DAMAGE = 11;
    /**
     * «Рассечение» (ПБ-15): the target within CLEAVE_REACH blocks, a wind-up of WINDUP_TICKS (1 s), a cone CLEAVE_REACH
     * deep and CLEAVE_HALF_ANGLE degrees to each side, CLEAVE_DAMAGE of «Пробой» to each, CLEAVE_COOLDOWN to the next.
     * TODO(design): the cone; TODO(balance): the cooldown
     */
    public static final int CLEAVE_REACH = 3, WINDUP_TICKS = 20, CLEAVE_HALF_ANGLE = 60, CLEAVE_DAMAGE = 6, CLEAVE_COOLDOWN = 100;
    /** ПТ-13: Exo Armour Scrap 1..2 (100%), a Shield Core, a liquid helium cell. TODO(balance) */
    public static final int SCRAP_MIN = 1, SCRAP_MAX = 2;
    public static final float CORE_CHANCE = 0.15F, HELIUM_CELL_CHANCE = 0.5F;
    /** Where (SpawnRulesSC's): the canyons, no closer than this to the world's spawn. */
    public static final int MIN_DIST = SpawnRulesSC.EXECUTIONER_MIN_DIST;
    public static final int XP = 25;

    private final EntityAICleaveSC cleave;

    public EntityExecutionerSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        cleave = new EntityAICleaveSC(this);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, cleave);
        tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.7D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 10F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BLADE_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.6D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** An elite of its kind: never a modifier, «Казнь» never finishes it (ПС-24). */
    @Override
    protected boolean alwaysElite() {
        return true;
    }

    /** ПС-10: its Exo Blade 11 (the blade in hand; without one the fist) - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        float d = MobCombatSC.blade(this) != null ? BLADE_DAMAGE : MobCombatSC.meleeDamage(null, MobCombatSC.FIST, rand);
        return MobCombatSC.melee(this, target, d * damageMul());
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.EXO, PIECES, GLOW, rand);
            setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.EXO));
            MobArmorSC a = mobArmor();
            a.helium = true;                                 // the shield
            a.emergency = false;
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    /** ПТ-1, ПТ-13: Exo Armour Scrap 1..2, a Shield Core, a liquid helium cell. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.EXO_SCRAP, SCRAP_MIN + rand.nextInt(SCRAP_MAX - SCRAP_MIN + 1));
        if (scrap != null) {
            out.add(scrap);
        }
        if (rand.nextFloat() < CORE_CHANCE) {
            ItemStack core = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHIELD_CORE, 1);
            if (core != null) {
                out.add(core);
            }
        }
        if (ModItems.liquidHeCell != null && rand.nextFloat() < HELIUM_CELL_CHANCE) {
            out.add(new ItemStack(ModItems.liquidHeCell));
        }
    }

    /** A ПЭ-1 Point that reaches it during the wind-up breaks «Рассечение» off (the rest is the base's). */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        EmpSC.HitResult r = super.empHit(shooter, mode, upgraded, pieces);
        if (mode == EmpMode.POINT) {
            cleave.breakOff();
        }
        return r;
    }

    /** It is winding «Рассечение» up now (both sides: the telegraph). */
    public boolean windingUp() {
        return mobAction() == IMobActionSC.ACT_WINDUP;
    }

    /** The clients: the wind-up's red glints along the blade's arc (ПС-9), frost off the tank now and then. */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            return;
        }
        if (windingUp()) {
            double yaw = Math.toRadians(renderYawOffset);
            for (int i = 0; i < 2; i++) {
                double a = yaw + (rand.nextDouble() - 0.5D) * Math.toRadians(CLEAVE_HALF_ANGLE * 2);
                double r = 0.8D + rand.nextDouble() * (CLEAVE_REACH - 0.8D);
                worldObj.spawnParticle("reddust", posX - Math.sin(a) * r, posY + 0.2D + rand.nextDouble() * 0.5D, posZ + Math.cos(a) * r,
                        1.0D, 0.1D, 0.1D);
            }
        } else if (ticksExisted % 10 == 0) {
            double yaw = Math.toRadians(renderYawOffset);
            worldObj.spawnParticle("snowshovel", posX + Math.sin(yaw) * 0.5D, posY + 1.1D, posZ - Math.cos(yaw) * 0.5D, 0D, -0.02D, 0D);
        }
    }

    // ------------------------------------------------------------------ sounds: heavy, frosted plating (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.irongolem.throw";
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
        return 0.6F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.6F, 0.8F);
    }
}
