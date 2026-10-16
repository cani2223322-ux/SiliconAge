package com.sc.mob;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.util.ArmorSuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-substrate-tech.md §4): the base of every armoured Substrate hostile - an EntityMob (IMob:
 * a settlement's field pushes it out, Peaceful removes it) wearing the mod's armour for real through MobArmorSC.
 * Package M's mobs (EntityLinemanSC, EntityMarauderSC...) extend it and add their AI, equipment, sounds and drops;
 * they must call super in every method overridden here.
 * <p>
 * Where the armour works: damageEntity (not attackEntityFrom - vanilla's hurt-resistance frames have already cut
 * the blow to what really lands, so a burst of hits can't drain the shield twice): the shield first, then
 * applyArmorCalculations by the reserve. The vanilla equipment drop is off on every slot (ПС-6): the drop chance 0
 * alone is not enough - vanilla's check is rand - looting x 0.01 < chance, so Looting would still drop real, working
 * pieces (an Exo blade's looting mode: 5% a slot, a farm's dream) - hence dropEquipment does nothing; trophies are
 * MobDropsSC's single roll.
 */
public abstract class EntityArmoredMobSC extends EntityMob implements IArmoredMobSC, IEmpTargetSC, ISubstrateNativeSC {

    private final MobArmorSC armor;

    protected EntityArmoredMobSC(World world) {
        super(world);
        armor = new MobArmorSC(this);
        for (int i = 0; i < 5; i++) {
            setEquipmentDropChance(i, 0F);
        }
    }

    /** ПС-6: never drops the worn equipment, Looting or not (see the class comment). */
    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
    }

    @Override
    public MobArmorSC mobArmor() {
        return armor;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        MobArmorSC.addWatchers(dataWatcher);
    }

    @Override
    protected void damageEntity(DamageSource src, float amount) {
        if (!worldObj.isRemote && !isEntityInvulnerable()) {
            amount = armor.shield(src, amount);
            if (amount <= 0F) {
                return;
            }
        }
        super.damageEntity(src, amount);
    }

    @Override
    protected float applyArmorCalculations(DamageSource src, float amount) {
        return worldObj.isRemote ? amount : armor.applyArmor(src, amount);
    }

    /** What the armour bar / WAILA / other mods read: the real points now. */
    @Override
    public int getTotalArmorValue() {
        return worldObj != null && worldObj.isRemote ? super.getTotalArmorValue() : armor.points();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            armor.serverTick();
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        armor.write(tag);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        armor.read(tag);
        armor.sync();
    }

    // ------------------------------------------------------------------ IEmpTargetSC (ПЭ-1 on this mob)

    /** Both sides: the worn stacks are synced to clients (S04), the reserve's «down» by the DataWatcher (FLAG_DOWN). */
    @Override
    public boolean empArmoured() {
        if (armor.suit() == null) {
            return false;
        }
        return worldObj != null && worldObj.isRemote ? !MobArmorSC.clientFlag(this, MobArmorSC.FLAG_DOWN) : !armor.down();
    }

    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        return armor.empHit(shooter, mode, upgraded, pieces);
    }

    @Override
    public boolean empSuppressed(int piece) {
        return worldObj.isRemote ? MobArmorSC.clientSuppressed(this, piece) : armor.suppressed(piece);
    }

    @Override
    public int empSuppressedTicks(int piece) {
        return piece < 0 || piece > 3 ? 0 : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, armor.suppressedUntil[piece] - worldObj.getTotalWorldTime()));
    }

    @Override
    public int empImmuneTicks(int piece) {
        if (!armor.immune(piece)) {
            return 0;                                   // out of range, still suppressed, or no immunity running
        }
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, armor.immuneUntil[piece] - worldObj.getTotalWorldTime()));
    }

    @Override
    public ArmorSuit empSuit() {
        return armor.suit();
    }

    @Override
    public int empDefenseTier() {
        return armor.defenseTier;
    }

    @Override
    public long empCapacityEu() {
        ArmorSuit s = armor.suit();
        return s == null ? 0L : (long) (armor.reserveMax * s.euPerDamage);
    }

    @Override
    public long empChargeEu() {
        ArmorSuit s = armor.suit();
        return s == null ? 0L : (long) (Math.max(0F, armor.reserve) * s.euPerDamage);
    }

    @Override
    public int empDrainEu(int eu) {
        return armor.drainEu(eu);
    }

    @Override
    public void empStreamSecond(Entity shooter) {
        armor.streamSecond(shooter);
    }
}
