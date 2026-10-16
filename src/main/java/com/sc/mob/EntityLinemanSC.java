package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.mob.ai.EntityAIRechargeAtTraceSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SiliconMaterial;
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
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.2): «Обходчик» (Lineman) - an empty Nano shell with gaps, one yellow
 * diode and a torn cable, walking the plateaus in pairs. Nano 1..3 pieces with the yellow light, drained by 20..30
 * blockable damage (the reserve by what it wears: MobArmorSC.spawnReserveForDamage) - plain Nano armour (ПБ-5: Nano
 * has no emergency mode; its weakness is the few pieces and the small reserve). Fist 3..4; 5% carry an Ion Cutter
 * (an instant beam, 4). With the reserve gone it crouches at a Copper Trace to recharge - the moment to catch it.
 * HP 20. Drops (MobDropsSC, rule ПТ-1): Shell Firmware 100%, Transistor 0..2; trophies Nano piece 2%, Ion Cutter 1.5%.
 * <p>
 * Fixed: the class name, NAME, the World constructor (EntityList). The numbers below are what the handbook's bestiary
 * prints.
 */
public class EntityLinemanSC extends EntityArmoredMobSC implements IFactionLootSC {

    public static final String NAME = "lineman";

    public static final double HP = 20D, SPEED = 0.25D, FOLLOW = 32D;
    /**
     * Worn Nano pieces; the blockable damage that drains its armour (the reserve on spawning is that x the share its
     * pieces stop - 2..3 Plasma Rifle shots, 4..5 arrows, whatever it wears); the Ion Cutter's chance; the light
     * (ModelArmorGlowSC.COLORS: #FFE040).
     */
    public static final int PIECES_MIN = 1, PIECES_MAX = 3, GLOW = 3;
    public static final float RESERVE_MIN = 20F, RESERVE_MAX = 30F, ION_CUTTER_CHANCE = 0.05F;
    /** Its blows (MobCombatSC's, here for the handbook's bestiary): the fist, the cutter's beam. */
    public static final int FIST_MIN = MobCombatSC.FIST, FIST_MAX = MobCombatSC.FIST + MobCombatSC.FIST_SPREAD,
            BEAM_DAMAGE = MobCombatSC.beamDamage(WeaponType.ION_CUTTER);

    public EntityLinemanSC(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIRechargeAtTraceSC(this, 1.2D));
        tasks.addTask(2, new EntityAIBeamAttackSC(this, 1.0D));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));      // the pair answers together
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        // only what WAILA-like tools show: the blow itself is MobCombatSC's (attackEntityAsMob below)
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(MobCombatSC.FIST);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПС-4: the fist 3..4 (or what's in hand - MobCombatSC), never the held item's attributes. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(getHeldItem(), MobCombatSC.FIST, rand));
    }

    /** SpawnDirectorSC, the spawn egg, a spawner: the pieces, the cutter, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dress(this, ArmorSuit.NANO, PIECES_MIN + rand.nextInt(PIECES_MAX - PIECES_MIN + 1), GLOW, rand);
            if (rand.nextFloat() < ION_CUTTER_CHANCE) {
                setCurrentItemOrArmor(0, MobCombatSC.gunStack(WeaponType.ION_CUTTER));
            }
            MobArmorSC a = mobArmor();
            a.helium = false;
            a.emergency = false;
            a.spawnReserveForDamage(RESERVE_MIN, RESERVE_MAX);
        }
        return data;
    }

    /**
     * ПС-6: never. With the drop chance 0 vanilla still drops each worn piece at Looting x 1% (rand - looting / 100 < 0):
     * an Exo blade's looting mode would make it 5% of a real, working piece - trophies are MobDropsSC's single roll alone.
     */
    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
    }

    /** ПТ-1 (MobDropsSC's numbers): Shell Firmware, Transistors 0..TRANSISTOR_MAX. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        ItemStack firmware = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, MobDropsSC.FIRMWARE);
        if (firmware != null) {
            out.add(firmware);
        }
        int t = rand.nextInt(MobDropsSC.TRANSISTOR_MAX + 1);
        if (t > 0) {
            ItemStack s = ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR);
            s.stackSize = t;
            out.add(s);
        }
    }

    // ------------------------------------------------------------------ sounds: an empty shell (vanilla's, ПС §5.9)

    @Override
    protected String getLivingSound() {
        return "random.click";
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
        return 1.3F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.15F, 1.5F);
    }
}
