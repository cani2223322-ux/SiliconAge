package com.sc.mob;

import java.util.Random;

import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск: именные Отступники», ★ A): the week's wanted named Renegade - a Renegade Trooper's
 * kin (EntityTrooperSC: its AI, its blows, its render by default) in Quantum or Exo armour with two elite modifiers from the allowed
 * pairs (WantedRulesSC), a name of its own over its head («Хром-7 „Резчик“» - each client in its own language, from DW_WANTED), and an
 * escort of Troopers. WantedSC brings it into the world where its record says it roams (never the spawn director, no spawn egg -
 * MobEntitiesSC.hasEgg) and takes it back when it leaves the loaded land.
 * <p>
 * Its suit 4/4 with the crimson glow (MobCombatSC.dress, GLOW), Quantum - the Quantum Blade or the Plasma Rifle, Exo - the Exo Blade;
 * HP HP, the reserve RESERVE_MIN..RESERVE_MAX. The second modifier is NBT NBT_MOD2 (EliteSC.applySecond / has); it never grows stronger
 * by killing. Its record is the only one it answers to: a copy of an older week, or one the record no longer holds (WantedSC took it
 * back while its chunk was unloaded), leaves the world on its first tick (setDead - no death, no drops).
 * <p>
 * Fixed (the coordinator's): the class, NAME (pool id MobEntitiesSC.ID_WANTED 26), the World constructor, the DataWatcher slot DW_WANTED
 * 26 (int: WantedRulesSC.packLook - the name's codes and the second modifier, for the clients), the NBT key NBT ("scWanted": the
 * record's id - a stale one leaves the world on its first tick). The rest is ПД-4's.
 */
public class EntityWantedSC extends EntityTrooperSC {

    public static final String NAME = "wantedRenegade";
    /** The clients' look: WantedRulesSC.packLook (name codes, the second elite modifier's code). */
    public static final int DW_WANTED = 26;
    /** Entity NBT: the wanted record's id (WantedSC); 0 - none (a /summon): it leaves on its first tick. */
    public static final String NBT = "scWanted";
    /** Entity NBT: the second elite modifier's code (EliteModSC.code). */
    public static final String NBT_MOD2 = "scEliteMod2";
    /** ПД-4: the health, the suit's reserve. TODO(balance) */
    public static final double HP = 80D;
    public static final float RESERVE_MIN = 120F, RESERVE_MAX = 160F;
    /** How often it asks its record whether it is still the one (ticks). */
    public static final int CHECK_TICKS = 20;

    private long wantedId;
    private EliteModSC mod2;

    public EntityWantedSC(World world) {
        super(world);
        experienceValue = XP * 4;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_WANTED, Integer.valueOf(0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
    }

    /** The packed look (WantedRulesSC.packLook) - both sides. */
    public int look() {
        return dataWatcher.getWatchableObjectInt(DW_WANTED);
    }

    /** Server: the packed look for the clients. */
    public void setLook(int packed) {
        if (look() != packed) {
            dataWatcher.updateObject(DW_WANTED, Integer.valueOf(packed));
        }
    }

    /** The wanted record's id this one answers to (0 none). */
    public long wantedId() {
        return wantedId;
    }

    public void setWantedId(long id) {
        wantedId = id;
    }

    /** The second elite modifier (server; the clients read it from look()). */
    public EliteModSC mod2() {
        return mod2;
    }

    void setMod2(EliteModSC m) {
        mod2 = m;
    }

    /** Never made by an egg or the spawn director: WantedSC dresses it (dress) after the Trooper's own dressing. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        return super.onSpawnWithEgg(data);
    }

    /** Server (WantedSC): the suit 4/4, the weapon by the suit, the reserve, the health full. */
    public void dress(ArmorSuit suit, Random r) {
        if (worldObj.isRemote) {
            return;
        }
        MobCombatSC.dress(this, suit, PIECES, GLOW, r);
        if (suit == ArmorSuit.EXO) {
            setCurrentItemOrArmor(0, MobCombatSC.litBlade(BladeType.EXO));
        } else {
            setCurrentItemOrArmor(0, r.nextBoolean() ? MobCombatSC.litBlade(BladeType.QUANTUM) : MobCombatSC.gunStack(WeaponType.PLASMA_RIFLE));
        }
        MobArmorSC a = mobArmor();
        a.helium = true;
        a.emergency = false;
        a.spawnReserveForDamage(RESERVE_MIN, RESERVE_MAX);
        a.sync();
        setHealth(getMaxHealth());
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote && !isDead && (ticksExisted == 1 || ticksExisted % CHECK_TICKS == 0) && !WantedSC.current(this)) {
            WantedSC.leave(this);                               // a stale copy, or the record took it back - gone, no death
        }
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setLong(NBT, wantedId);
        if (mod2 != null) {
            tag.setByte(NBT_MOD2, (byte) mod2.code());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        wantedId = tag.getLong(NBT);
        mod2 = tag.hasKey(NBT_MOD2) ? EliteModSC.of(tag.getByte(NBT_MOD2)) : null;
    }
}
