package com.sc.mob;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.10, rule ПС-40; proposal §3 «Флора и мирная фауна»): «Подложник» (Wafer Crab) - a
 * peaceful crab with an iridescent wafer on its back; runs from players. Not IMob (a dome never pushes it, Peaceful keeps it), a
 * native (ISubstrateNativeSC: the bridge never takes it, the soul vial is refused, «born in Si-14» - ПТ-1). Drops (ПТ-1): a
 * Natural Wafer 5% - the Technologist buys it. HP 8. The plateaus and the Ridges (SpawnDirectorSC's peaceful tries, package M).
 * <p>
 * Fixed (the coordinator's): the class name, NAME (pool id 21 in MobEntitiesSC), the World constructor, HP, no faction. The rest
 * is package N's.
 * <p>
 * Its AI is vanilla's (no telegraph, no DataWatcher slot of its own): a player within FLEE_FROM - it scuttles off
 * (EntityAIAvoidEntity, faster when the player is near), hurt - it panics; else it wanders and watches. It never hurts anyone.
 * Vanilla's despawning (an EntityCreature: no player near - it goes; the director makes more). The wafer - factionLoot, asked by
 * MobDropsSC under ПТ-1 for a peaceful native too (package M).
 */
public class EntityWaferCrabSC extends EntityCreature implements ISubstrateNativeSC, IAnimals, IFactionSC, IFactionLootSC {

    public static final String NAME = "waferCrab";
    public static final double HP = 8D;
    /** It runs from a player this near; the speeds (of its own) far and near. */
    public static final float FLEE_FROM = 8F;
    public static final double SPEED = 0.3D, FLEE_FAR = 1.0D, FLEE_NEAR = 1.4D, PANIC = 1.5D;
    /** ПТ-1: the Natural Wafer's chance. TODO(balance) */
    public static final float WAFER_CHANCE = 0.05F;
    public static final int XP = 2;

    public EntityWaferCrabSC(World world) {
        super(world);
        setSize(0.7F, 0.4F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, PANIC));
        tasks.addTask(2, new EntityAIAvoidEntity(this, EntityPlayer.class, FLEE_FROM, FLEE_FAR, FLEE_NEAR));
        tasks.addTask(4, new EntityAIWander(this, 0.8D));
        tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 6F));
        tasks.addTask(6, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public String faction() {
        return FactionSC.NONE;
    }

    /** ПТ-1: a Natural Wafer now and then. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < WAFER_CHANCE) {
            ItemStack s = MobItemsSC.loot(ItemCombatLootSC.LootKind.NATURAL_WAFER, 1);
            if (s != null) {
                out.add(s);
            }
        }
    }

    /** Nothing of vanilla's (the wafer is factionLoot's, under ПТ-1). */
    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
    }

    // ------------------------------------------------------------------ sounds: a small crab (vanilla's)

    @Override
    protected String getLivingSound() {
        return null;
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
        return 1.6F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.silverfish.step", 0.1F, 1.6F);
    }
}
