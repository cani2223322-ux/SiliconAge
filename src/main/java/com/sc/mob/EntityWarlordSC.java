package com.sc.mob;

import java.util.List;
import java.util.Random;

import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.mob.ai.EntityAIWarlordDashSC;
import com.sc.mob.ai.EntityAIWarlordWeaponSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.2, rule ПС-32): «Вожак лагеря» (Camp Warlord), a mini-boss - an Exo chestplate with
 * helium (the shield) over Quantum, reserve 80..100; the Quantum Blade and the Ion Cutter. «Казнь» never finishes it. A Point
 * into its chestplate takes the shield; then it fights as a Trooper. HP 90. Drops: the camp's key 100% (ItemRelicSC.CAMP_KEY,
 * bound to its camp - package C's item), 4 scrap; trophies: a Quantum piece 2%, the Exo chestplate 0.5%. Only in a camp (package
 * C places it and brings it back after 3 days).
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f4.md §2.2, §7): the class name, NAME (pool id 13 in MobEntitiesSC), the
 * World constructor (EntityList), HP, the faction, alwaysElite. The numbers below are what the handbook's bestiary prints.
 * <p>
 * Package M:
 * <ul>
 * <li>the suit: a Quantum helmet, leggings and boots and the Exo chestplate, all in the crimson light (ModelArmorGlowSC.COLORS
 * 1), helium in the loop - the chestplate's shield; a reserve by RESERVE_DAMAGE_MIN..MAX blockable damage;</li>
 * <li>the weapons (EntityAIWarlordWeaponSC): the Quantum Blade (BLADE_DAMAGE) up close, the Ion Cutter's beam (BEAM_DAMAGE
 * after an aim of AIM_TICKS, CUTTER_RANGE) once the target is further than SWITCH_RANGE; the Trooper's dash with the blade
 * (EntityAIWarlordDashSC: DASH_FROM..DASH_TO, the cue, the dash, the recovery with its back open - BACKSTAB_MUL);</li>
 * <li>it leads the camp: a player's blow calls the Renegades within RALLY_RADIUS that fight nobody yet onto that player (at
 * most once in RALLY_TICKS);</li>
 * <li>the camp (package C, through SpawnDirectorSC.spawnAt and RuinWorldSC.settle): its home - the camp's box (the Forge data
 * scHome {x, y, z, r, l} and vanilla's home area: it stays within the leash l and goes back - put back after a reload,
 * MobCombatSC.restoreHome), the safe of its camp (the Forge data scCampSafe int[3], ItemRelicSC.WARLORD_SAFE_NBT) - its key
 * drops bound to it; its record RareRegistrySC.campKey(...) (C marks it ALIVE; it reports itself seen every
 * SpawnDirectorSC.RARE_SEEN_TICKS and its death - SpawnDirectorSC.rareSeen / rareDied); it never despawns.</li>
 * </ul>
 */
public class EntityWarlordSC extends EntityArmoredMobSC implements IFactionLootSC, ITrophyCategoriesSC {

    public static final String NAME = "warlord";
    public static final double HP = 90D, SPEED = 0.28D, FOLLOW = 32D;
    /** The crimson light (ModelArmorGlowSC.COLORS 1); the reserve's blockable damage. TODO(balance) */
    public static final int GLOW = 1;
    public static final float RESERVE_DAMAGE_MIN = 80F, RESERVE_DAMAGE_MAX = 100F;
    /** ПС-32: the Quantum Blade, the Ion Cutter's beam (MobCombatSC's), the cutter's aim and reach and its ion blue. */
    public static final int BLADE_DAMAGE = MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()], BEAM_DAMAGE = MobCombatSC.beamDamage(WeaponType.ION_CUTTER),
            AIM_TICKS = 20, CUTTER_RANGE = 16, CUTTER_COLOR = 0x59D9FF;
    /** The weapon by the distance: the cutter further than SWITCH_RANGE (in sight), the blade again closer than BLADE_FROM. */
    public static final double SWITCH_RANGE = 6D, BLADE_FROM = 5D;
    /** The dash (the Trooper's, EntityTrooperSC): its cue, recovery, cooldown, reach; a blow from behind in the recovery. TODO(balance) */
    public static final int DASH_CUE_TICKS = 10, RECOVER_TICKS = 30, DASH_COOLDOWN = 100, DASH_FROM = 3, DASH_TO = 6, DASH_LENGTH = 6;
    public static final float BACKSTAB_MUL = 1.5F;
    /** It leads the camp: the Renegades this near come at the player who hit it, once in RALLY_TICKS. */
    public static final int RALLY_RADIUS = 24, RALLY_TICKS = 40;
    /** ПТ-1: the camp's key (with a known safe), Quantum Armour Scrap SCRAP. */
    public static final int SCRAP = 4;
    /** ПТ-16 (TrophyTableSC): its trophies - a worn Quantum piece, the Exo chestplate (the handbook reads them here). */
    public static final float TROPHY_PIECE = TrophyTableSC.WARLORD_QUANTUM, TROPHY_EXO_CHEST = TrophyTableSC.WARLORD_EXO_CHEST;
    public static final int XP = 40;
    /** The Forge data (fixed, §2.7): its camp's safe int[3] (package C sets it - ItemRelicSC.WARLORD_SAFE_NBT); its home - MobCombatSC.HOME_NBT. */
    public static final String NBT_CAMP_SAFE = ItemRelicSC.WARLORD_SAFE_NBT;
    /** A Warlord's leash when its home has none of its own. */
    public static final int HOME_R_DEFAULT = 16;

    private final EntityAIWarlordDashSC dash;
    /** Server: the way it faced when it dashed (its back while it recovers). */
    private float dashYaw;
    /** Server: no rally before this tick (ticksExisted). */
    private int rallyAt;

    public EntityWarlordSC(World world) {
        super(world);
        setSize(0.6F, 1.9F);
        experienceValue = XP;
        getNavigator().setCanSwim(true);
        dash = new EntityAIWarlordDashSC(this);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWarlordWeaponSC(this));
        tasks.addTask(2, dash);
        tasks.addTask(3, new EntityAIBeamAttackSC(this, 1.0D, AIM_TICKS, 1, 1, CUTTER_RANGE, CUTTER_COLOR));
        tasks.addTask(4, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 1.0D));
        tasks.addTask(6, new EntityAIWander(this, 0.7D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 10F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BLADE_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.4D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /** ПЗ-3: the Renegades' - the camp's Stolen Field spares it. */
    @Override
    public String faction() {
        return FactionSC.RENEGADES;
    }

    /** ПС-32: a mini-boss - «Казнь» never finishes it, no modifier, the elite cap counts it. */
    @Override
    protected boolean alwaysElite() {
        return true;
    }

    /** ПС-30: a camp's one - never despawns (its camp brings it back only after its death). */
    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** ПС-4, ПС-32: the Quantum Blade 8 (the blade in hand), else the fist - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        float d = MobCombatSC.blade(this) != null ? BLADE_DAMAGE : MobCombatSC.meleeDamage(null, MobCombatSC.FIST, rand);
        return MobCombatSC.melee(this, target, d * damageMul());
    }

    /** SpawnDirectorSC.spawnAt (package C's camp), the spawn egg: the suit, the blade, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.wear(this, ArmorSuit.QUANTUM, 0, GLOW);
            MobCombatSC.wear(this, ArmorSuit.EXO, 1, GLOW);
            MobCombatSC.wear(this, ArmorSuit.QUANTUM, 2, GLOW);
            MobCombatSC.wear(this, ArmorSuit.QUANTUM, 3, GLOW);
            setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.QUANTUM));
            MobArmorSC a = mobArmor();
            a.helium = true;                                 // the Exo chestplate's shield
            a.emergency = false;
            a.spawnReserveForDamage(RESERVE_DAMAGE_MIN, RESERVE_DAMAGE_MAX);
        }
        return data;
    }

    // ------------------------------------------------------------------ the weapons, the dash

    /** It holds the Ion Cutter now (both sides). */
    public boolean holdsCutter() {
        return MobCombatSC.gun(this) == WeaponType.ION_CUTTER;
    }

    /** Server (EntityAIWarlordWeaponSC): the Ion Cutter or the Quantum Blade into its hand. */
    public void wield(boolean cutter) {
        setCurrentItemOrArmor(0, cutter ? MobCombatSC.gunStack(WeaponType.ION_CUTTER) : MobCombatSC.litBlade(BladeType.QUANTUM));
    }

    /** Server: a dash or its recovery is under way (the weapon stays). */
    public boolean dashing() {
        return dash.running();
    }

    /** Server (EntityAIWarlordDashSC): the dash goes this way - its back is judged by it until the recovery ends. */
    public void setDashYaw(float yaw) {
        dashYaw = yaw;
    }

    /**
     * While it recovers from a dash (ACT_RECOVER) a blow from behind counts x1.5 (the Trooper's rule); a player's blow rallies the
     * camp's Renegades.
     */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && amount > 0F && src != null) {
            if (mobAction() == IMobActionSC.ACT_RECOVER && MobCombatSC.behind(this, src.getEntity(), dashYaw)) {
                amount *= BACKSTAB_MUL;
                if (worldObj instanceof WorldServer) {
                    ((WorldServer) worldObj).func_147487_a("crit", posX, posY + height * 0.6D, posZ, 10, 0.25D, 0.3D, 0.25D, 0.15D);
                }
            }
            Entity by = src.getEntity();
            if (by instanceof EntityPlayer && !((EntityPlayer) by).capabilities.isCreativeMode && ticksExisted >= rallyAt) {
                rallyAt = ticksExisted + RALLY_TICKS;
                rally((EntityPlayer) by);
            }
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Server: the camp's Renegades within RALLY_RADIUS that fight nobody come at `p`. */
    private void rally(EntityPlayer p) {
        double r = RALLY_RADIUS;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityCreature.class, boundingBox.expand(r, 8D, r));
        for (Object o : near) {
            EntityCreature c = (EntityCreature) o;
            if (c != this && c.isEntityAlive() && c.getAttackTarget() == null && MobCombatSC.hostileNative(c) && FactionSC.is(c, FactionSC.RENEGADES)) {
                c.setAttackTarget(p);
            }
        }
    }

    // ------------------------------------------------------------------ the camp: its home, its safe, its record

    /** Server: its home - the camp's box middle, its half size `r`, the leash (MobCombatSC.setHome - RuinWorldSC.settle's format). */
    public void setHome(int x, int y, int z, int r, int leash) {
        MobCombatSC.setHome(this, x, y, z, r, leash);
    }

    /** Server: the safe of its camp - the key it drops opens only that one (the Forge data, as package C puts it there). */
    public void setCampSafe(int x, int y, int z) {
        getEntityData().setIntArray(NBT_CAMP_SAFE, new int[]{x, y, z});
    }

    /** Its camp's safe {x, y, z}, null - none known (an egg's Warlord). */
    public int[] campSafe() {
        int[] a = getEntityData().getIntArray(NBT_CAMP_SAFE);
        return a.length >= 3 ? new int[]{a[0], a[1], a[2]} : null;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            clientTelegraph();
            return;
        }
        if ((ticksExisted + getEntityId()) % SpawnDirectorSC.RARE_SEEN_TICKS == 0 && isEntityAlive()) {
            SpawnDirectorSC.rareSeen(this);                  // ПС-30: its record; a stale copy leaves
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead) {
            SpawnDirectorSC.rareDied(this);                  // ПС-30: the camp's 3 days begin
        }
    }

    // ------------------------------------------------------------------ drops, trophies

    /** ПТ-1: the camp's key bound to its safe (100%, when the safe is known), Quantum Armour Scrap SCRAP. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        int[] safe = campSafe();
        ItemStack key = safe == null ? null : ItemRelicSC.campKey(safe[0], safe[1], safe[2]);   // NBT scCamp {x, y, z}
        if (key != null) {
            out.add(key);
        }
        ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.QUANTUM_SCRAP, SCRAP);
        if (scrap != null) {
            out.add(scrap);
        }
    }

    /** ПТ-16: its own categories only - a worn Quantum piece 2%, the Exo chestplate 0.5% (nothing of its weapons). */
    @Override
    public float[] trophyCategories(float[] standard) {
        boolean quantum = false;
        for (int t = 0; t < 4; t++) {
            quantum |= MobArmorSC.pieceSuit(this, t) == ArmorSuit.QUANTUM;
        }
        boolean exo = MobArmorSC.pieceSuit(this, 1) == ArmorSuit.EXO;
        return new float[]{0F, 0F, 0F, 0F, quantum ? TrophyTableSC.WARLORD_QUANTUM : 0F, exo ? TrophyTableSC.WARLORD_EXO_CHEST : 0F};
    }

    @Override
    public int trophyMeta(int index, Random rand) {
        if (index == MobDropsSC.OWN + 1) {
            return TrophyTableSC.armorMeta(ArmorSuit.EXO, 1);
        }
        if (index != MobDropsSC.OWN) {
            return -1;
        }
        int[] types = new int[4];
        int n = 0;
        for (int t = 0; t < 4; t++) {
            if (MobArmorSC.pieceSuit(this, t) == ArmorSuit.QUANTUM) {
                types[n++] = t;
            }
        }
        return n == 0 ? -1 : TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, types[rand.nextInt(n)]);
    }

    // ------------------------------------------------------------------ NBT

    /** The safe and the home live in the Forge data (saved with it); vanilla's home area is put back from it. */
    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        MobCombatSC.restoreHome(this, HOME_R_DEFAULT);
    }

    // ------------------------------------------------------------------ the clients: the dash's sparks (ПС-9), a sound

    private void clientTelegraph() {
        int a = mobAction();
        if (a == IMobActionSC.ACT_DASH) {
            double moved = (posX - prevPosX) * (posX - prevPosX) + (posZ - prevPosZ) * (posZ - prevPosZ);
            int n = moved > 0.25D ? 4 : 1;
            for (int i = 0; i < n; i++) {
                double k = n > 1 ? i / (double) n : rand.nextDouble();
                worldObj.spawnParticle("reddust", prevPosX + (posX - prevPosX) * k + (rand.nextDouble() - 0.5D) * 0.5D,
                        posY + 0.2D + rand.nextDouble() * 1.5D, prevPosZ + (posZ - prevPosZ) * k + (rand.nextDouble() - 0.5D) * 0.5D, 1.0D, 0.1D, 0.1D);
            }
        } else if (a == IMobActionSC.ACT_RECOVER && ticksExisted % 3 == 0) {
            double fx = -Math.sin(Math.toRadians(renderYawOffset)), fz = Math.cos(Math.toRadians(renderYawOffset));
            worldObj.spawnParticle("smoke", posX - fx * 0.45D, posY + 1.2D, posZ - fz * 0.45D, -fx * 0.02D, 0.04D, -fz * 0.02D);
        }
    }

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
        return 0.55F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        playSound("mob.irongolem.walk", 0.3F, 1.1F);
    }
}
