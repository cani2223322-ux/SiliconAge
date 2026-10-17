package com.sc.mob;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.util.ArmorSuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
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
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §2.4, §5; the coordinator's): DataWatcher DW_ELITE (24, the elite modifier's code -
 * EliteModSC, 0 none) and DW_ACTION (25, IMobActionSC's telegraph); 26..31 belong to the mob classes. An elite's «Казнь»
 * immunity (IExecuteImmuneSC), the census (ICensusSC), the modifier's work (EliteSC: serverTick, onDeath, damageMul - the
 * subclasses multiply their blows by damageMul()). NBT "scEliteMod".
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §8.3, the coordinator's): the faction (IFactionSC) - FactionSC.defaultOf by the class; a
 * Ф4 class says its own (the Warlord, «Затмение» - Renegades; the Overheated, the Sentinel - Shells).
 */
public abstract class EntityArmoredMobSC extends EntityMob implements IArmoredMobSC, IEmpTargetSC, ISubstrateNativeSC,
        IExecuteImmuneSC, ICensusSC, IMobActionSC, IFactionSC {

    /** DataWatcher (fixed, Ф3): the elite modifier's code (byte: EliteModSC.code, 0 none), the telegraph (byte: IMobActionSC.ACT_*). */
    public static final int DW_ELITE = 24, DW_ACTION = 25;
    /** The entity NBT key of the elite modifier's code. */
    public static final String NBT_ELITE = "scEliteMod";

    private final MobArmorSC armor;

    protected EntityArmoredMobSC(World world) {
        super(world);
        armor = new MobArmorSC(this);
        for (int i = 0; i < 5; i++) {
            setEquipmentDropChance(i, 0F);
        }
    }

    /**
     * Ф4: a camp's resident (MobCombatSC.HOME_NBT with "u" - RuinWorldSC.settle): its home area a column - across within the
     * leash, up to "u" over the home (the canyon's rim: vanilla's EntityAITarget keeps to the home area, so a sphere would let
     * the camp ignore a player shooting down at it; the walls keep it on the floor anyway). Else vanilla's sphere.
     */
    @Override
    public boolean isWithinHomeDistance(int x, int y, int z) {
        if (hasHome()) {
            NBTTagCompound h = getEntityData().getCompoundTag(MobCombatSC.HOME_NBT);
            if (h.hasKey("u")) {
                ChunkCoordinates c = getHomePosition();
                double dx = x - c.posX, dz = z - c.posZ, r = func_110174_bM();
                int dy = y - c.posY;
                return dx * dx + dz * dz < r * r && dy < h.getInteger("u") && dy > -r;
            }
        }
        return super.isWithinHomeDistance(x, y, z);
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
        dataWatcher.addObject(DW_ELITE, Byte.valueOf((byte) 0));
        dataWatcher.addObject(DW_ACTION, Byte.valueOf((byte) IMobActionSC.ACT_NONE));
    }

    // ------------------------------------------------------------------ Ф3: the elite modifier, the telegraph, the census

    /** The elite modifier (both sides: the DataWatcher), null - a plain mob. */
    public EliteModSC eliteMod() {
        return EliteModSC.of(dataWatcher.getWatchableObjectByte(DW_ELITE));
    }

    /** Server (EliteSC.apply): the modifier shown and kept; null clears it. */
    public void setEliteMod(EliteModSC m) {
        dataWatcher.updateObject(DW_ELITE, Byte.valueOf((byte) (m == null ? 0 : m.code())));
    }

    /** An elite of its kind (the Exo Executioner; Ф4 the Sentinel, «Затмение», the warlords): never gets a modifier, never «Казнь». */
    protected boolean alwaysElite() {
        return false;
    }

    /** ПБ-8, ПС-24: an elite (a modifier, or of its kind) - «Казнь» doesn't finish it. */
    @Override
    public boolean executeImmune() {
        return alwaysElite() || armor.elite || eliteMod() != null;
    }

    @Override
    public int mobAction() {
        return dataWatcher.getWatchableObjectByte(DW_ACTION);
    }

    @Override
    public void setMobAction(int action) {
        if (mobAction() != action) {
            dataWatcher.updateObject(DW_ACTION, Byte.valueOf((byte) action));
        }
    }

    /** ПС-7: every armoured native counts (a subclass may not). */
    @Override
    public boolean censusCounts() {
        return true;
    }

    /** ПС-7: Quantum armour and above is the «Квант и выше» tier. */
    @Override
    public int censusTier() {
        ArmorSuit s = armor.suit();
        return s != null && s.atLeast(ArmorSuit.QUANTUM) || alwaysElite() ? TIER_QUANTUM : TIER_PLAIN;
    }

    @Override
    public boolean censusElite() {
        return executeImmune();
    }

    /** Ф4, ПЗ-3: the side it is on (a Stolen Field spares its own). */
    @Override
    public String faction() {
        return FactionSC.defaultOf(this);
    }

    /** Server: what this mob's blows are multiplied by now (ПС-21: a Commander's group) - the subclasses' attackEntityAsMob and beams use it. */
    public float damageMul() {
        return EliteSC.damageMul(this);
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead) {               // dead: vanilla's flag, unset if a mod cancelled LivingDeathEvent
            EliteSC.onDeath(this);
        }
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
            EliteSC.serverTick(this);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        armor.write(tag);
        EliteModSC m = eliteMod();
        if (m != null) {
            tag.setByte(NBT_ELITE, (byte) m.code());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        armor.read(tag);
        setEliteMod(EliteModSC.of(tag.getByte(NBT_ELITE)));
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
