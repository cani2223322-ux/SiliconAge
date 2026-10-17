package com.sc.settle.npc;

import java.util.Collections;
import java.util.List;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.init.ModItems;
import com.sc.item.ItemBladeSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobCombatSC;
import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.ISettlementGuardSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementProtectSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.ViolationRulesSC;
import com.sc.settle.npc.ai.EntityAIStrayWanderSC;
import com.sc.settle.npc.ai.EntityAIWardenBeamSC;
import com.sc.settle.npc.ai.EntityAIWardenBrainSC;
import com.sc.settle.npc.ai.EntityAIWardenNodSC;
import com.sc.settle.npc.ai.EntityAIWardenPadSC;
import com.sc.settle.npc.ai.EntityAIWardenPatrolSC;
import com.sc.settle.npc.ai.EntityAIWardenReturnSC;
import com.sc.settle.npc.ai.EntityAIWardenWarnSC;
import com.sc.settle.npc.ai.EntityAIWardenWatchSC;
import com.sc.settle.npc.ai.NpcLinkSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeFeature;
import com.sc.util.BladeType;
import com.sc.util.SoundsSC;
import com.sc.util.WeaponType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.INpc;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.4-§6.8, rules ПГ-1..ПГ-10): «Страж купола» - a Sentry (Nano 4/4) or a
 * Senior Warden (Quantum 4/4 with helium) in the mod's armour for real: the same MobArmorSC as the hostiles (reserve,
 * shield, ПЭ-1's suppression and siphon - IEmpTargetSC), its look by ModelArmorGlowSC through IArmoredMobSC.
 * EntityCreature + INpc + ISettlementGuardSC (not EntityMob / IMob / EntityGolem: see ISettlementGuardSC). Never
 * despawns, never leashed, drops nothing of its equipment (ПГ-9: the «казённая» armour goes «into the dome» in
 * sparks), only experience. Registered by the coordinator in MobEntitiesSC (pool id ID_WARDEN = 3, "SiliconAge.warden").
 * <p>
 * Fixed for everyone (the coordinator's): DataWatcher 20..23 MobArmorSC's, DW_KIND / DW_NAME / DW_ALERT, 27..31 free
 * for package N; the armour's wiring (as EntityArmoredMobSC: the shield in damageEntity, the reserve in
 * applyArmorCalculations, the points, the tick, the NBT "scMobArmor"); the member's NBT and bindTo.
 * <p>
 * Package N's (here and in settle.npc.ai):
 * <ul>
 * <li>bindTo / a spawn egg dresses it by rank (equip): 4/4 of the suit with the dome's turquoise light (GLOW - the
 * stacks' NBT set once, never again), helium for the Senior, the reserve by spawnReserveForDamage, HP 40 / 50, half
 * the guards a blade (sheathed - its light off - until a warning or a fight: «клинок наголо»), half a beam gun;</li>
 * <li>the blows are MobCombatSC's (blades 6 / 8), the beams EntityAIBeamAttackSC's (4 / 7; the Plasma Rifle every
 * 2..3 s - EntityAIWardenBeamSC); never a projectile (the core would turn it back);</li>
 * <li>the brain (EntityAIWardenBrainSC, every THINK ticks): the leash (NpcLinkSC.leash - past it, back without a
 * target), the offender (GuardAlertSC.target - package C decides who, when and for how long), the warned / watched
 * player, hostiles (IMob) in the dome and HUNT_OUT past it or within HELP_RADIUS of a «Партнёр» (ПГ-10), a non-player
 * that hit it; the alert DW_ALERT (GuardAlertSC's, ATTACKING while it fights) - the client blinks its lamp by it;</li>
 * <li>its duty (EntityAIWardenPatrolSC, role): Sentry #0 at the gate, the other Sentries round the dome's edge and the
 * belt PATROL_OUT past it (the first HEART_GUARDS of them at the Heart at night), the Seniors at their posts (towers,
 * the gate); below RECHARGE_BELOW reserve it walks to a charge pad (P_PAD*) and recharges there to RECHARGE_UNTIL; it nods to a
 * «Партнёр» (EntityAIWardenNodSC);</li>
 * <li>a ПЭ-1 Point by a player: SettlementProtectSC.violation POINT_WARDEN first, then the armour's answer;</li>
 * <li>death: SettlementRegistrySC.slotDied, the sparks, the equipment gone - only experience.</li>
 * </ul>
 * A stray (a spawn egg's, settlement -1) is a Sentry that only answers who hits it.
 */
public class EntityWardenSC extends EntityCreature implements INpc, ISettlementGuardSC, IArmoredMobSC, IEmpTargetSC, ISubstrateNativeSC {

    public static final String NAME = "warden";
    /** DataWatcher (fixed): the rank (byte, NpcKindSC ordinal), the name code (int), the alert (byte, GuardAlertSC.CALM..ATTACKING). */
    public static final int DW_KIND = 24, DW_NAME = 25, DW_ALERT = 26;

    /** ПГ-2: HP by rank (Sentry, Senior Warden); its walk; the path search's reach (NpcNavSC walks farther in legs). TODO(balance) */
    public static final double SENTRY_HP = 40D, SENIOR_HP = 50D, SPEED = 0.3D, FOLLOW = 24D;
    /** Multiples of SPEED: the patrol, a chase, the way back from past the leash. */
    public static final double PATROL = 0.7D, CHASE = 1.0D, BACK = 0.9D;
    /** ПГ-2: 4/4 of the rank's suit; its reserve - what blockable damage of RESERVE_* drains (MobArmorSC.spawnReserveForDamage). */
    public static final int PIECES = 4;
    public static final float SENTRY_RESERVE_MIN = 20F, SENTRY_RESERVE_MAX = 40F, SENIOR_RESERVE_MIN = 40F, SENIOR_RESERVE_MAX = 70F;
    /** The dome's turquoise on the pieces (ModelArmorGlowSC.COLORS[5]) - set once, when it is dressed. */
    public static final int GLOW = 5;
    /** ПГ-2: a blade, else a beam gun - half and half. TODO(balance) */
    public static final float BLADE_CHANCE = 0.5F;
    /** Its blows (MobCombatSC's - here for the handbook): the Nano / Quantum Blade, the Ion Cutter / Plasma Rifle beam. */
    public static final int SENTRY_BLADE = MobCombatSC.BLADE[BladeType.NANO.ordinal()], SENIOR_BLADE = MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()],
            SENTRY_BEAM = MobCombatSC.beamDamage(WeaponType.ION_CUTTER), SENIOR_BEAM = MobCombatSC.beamDamage(WeaponType.PLASMA_RIFLE);
    /**
     * The beams' visible aim (EntityAIBeamAttackSC's 1 s, both guns); the Plasma Rifle fires every 2..3 s - the Ion
     * Cutter's 2 s and up to RIFLE_RELOAD_SPREAD ticks more (EntityAIWardenBeamSC). TODO(balance)
     */
    public static final int SENTRY_AIM_TICKS = EntityAIBeamAttackSC.AIM_TICKS, SENIOR_AIM_TICKS = EntityAIBeamAttackSC.AIM_TICKS,
            RIFLE_RELOAD_SPREAD = 20;
    /**
     * ПГ-3: below RECHARGE_BELOW of its reserve it goes to a charge pad and stands there until RECHARGE_UNTIL, PAD_UNITS
     * every PAD_EVERY ticks. TODO(balance)
     */
    public static final float RECHARGE_BELOW = 0.3F, RECHARGE_UNTIL = 0.9F, PAD_UNITS = 2F;
    public static final int PAD_EVERY = 20;
    /** ПГ-5: the patrol's belt past the dome's edge, hostiles hunted this far past it; ПГ-10: a «Партнёр» helped this close to him. */
    public static final int PATROL_OUT = 16, HUNT_OUT = 24, HELP_RADIUS = 24;
    /** ПГ-9: only experience. */
    public static final int XP_SENTRY = 10, XP_SENIOR = 15;
    /** Duties (role): the gate, the patrol, its own post (a Senior: a tower or the gate), the Heart at night. */
    public static final int ROLE_GATE = 0, ROLE_PATROL = 1, ROLE_POST = 2, ROLE_HEART = 3;
    /** ПГ-5 «ночью двое у Сердца»: the Sentries #1..#HEART_GUARDS (the gate's #0 stays at the gate). */
    public static final int HEART_GUARDS = 2;
    /** How far apart the two at the Heart stand from the Clone Capsule's spot (else the core's column). */
    public static final int HEART_SPREAD = 2, HEART_CORE = 4;
    /** The brain's look round, ticks. */
    public static final int THINK = 10;

    private final MobArmorSC armor;
    protected int settlementId = -1, slot = -1;
    /** The core column {x, z} of the record bound to (ISettlementMemberSC.boundCore), null unknown. */
    protected int[] boundCore;
    private final NpcLinkSC link;
    /** Server, the brain's (EntityAIWardenBrainSC): the warned and the watched player, past the leash, the «Партнёры» near. */
    private EntityPlayer warned, watched;
    private boolean returning;
    private List<EntityPlayer> partners = Collections.emptyList();

    public EntityWardenSC(World world) {
        super(world);
        setSize(0.6F, 1.95F);
        armor = new MobArmorSC(this);
        link = new NpcLinkSC(this);
        for (int i = 0; i < 5; i++) {
            setEquipmentDropChance(i, 0F);
        }
        experienceValue = XP_SENTRY;
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWardenReturnSC(this));
        tasks.addTask(2, new EntityAIWardenBeamSC(this, CHASE));
        tasks.addTask(3, new EntityAIAttackOnCollide(this, CHASE, true));
        tasks.addTask(4, new EntityAIWardenWarnSC(this));
        tasks.addTask(5, new EntityAIWardenWatchSC(this));
        tasks.addTask(6, new EntityAIWardenPadSC(this));
        tasks.addTask(7, new EntityAIWardenPatrolSC(this));
        tasks.addTask(8, new EntityAIStrayWanderSC(this, PATROL));
        tasks.addTask(9, new EntityAIWardenNodSC(this));
        tasks.addTask(10, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(11, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIWardenBrainSC(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        MobArmorSC.addWatchers(dataWatcher);
        dataWatcher.addObject(DW_KIND, Byte.valueOf((byte) NpcKindSC.SENTRY.ordinal()));
        dataWatcher.addObject(DW_NAME, Integer.valueOf(0));
        dataWatcher.addObject(DW_ALERT, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(SENTRY_HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    // ------------------------------------------------------------------ ISettlementMemberSC

    @Override
    public int settlementId() {
        return settlementId;
    }

    @Override
    public int slot() {
        return slot;
    }

    @Override
    public int[] boundCore() {
        return boundCore;
    }

    @Override
    public NpcKindSC kind() {
        return NpcKindSC.of(dataWatcher.getWatchableObjectByte(DW_KIND));
    }

    public void setKind(NpcKindSC k) {
        dataWatcher.updateObject(DW_KIND, Byte.valueOf((byte) (k == null || !k.guard ? NpcKindSC.SENTRY : k).ordinal()));
    }

    @Override
    public int nameCode() {
        return dataWatcher.getWatchableObjectInt(DW_NAME);
    }

    public void setNameCode(int code) {
        dataWatcher.updateObject(DW_NAME, Integer.valueOf(code));
    }

    public int alert() {
        return dataWatcher.getWatchableObjectByte(DW_ALERT);
    }

    public void setAlert(int a) {
        if (alert() != a) {
            dataWatcher.updateObject(DW_ALERT, Byte.valueOf((byte) a));
        }
    }

    @Override
    public void bindTo(SettlementRegistrySC.Settlement s, int slot) {
        link.forget();
        if (s == null || slot < 0 || slot >= s.slots.size()) {
            settlementId = -1;
            this.slot = -1;
            boundCore = null;
            return;
        }
        settlementId = s.id;
        this.slot = slot;
        boundCore = new int[]{s.x, s.z};
        SettlementRegistrySC.Slot sl = s.slots.get(slot);
        setKind(sl.kindOf());
        setNameCode(sl.nameCode());
        NpcLinkSC.setHome(this, s);
        equip(kind());
    }

    /** A spawn egg / a spawner: a stray Sentry (a random name) that only answers who hits it. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote && settlementId < 0) {
            if (armor.suit() == null) {
                equip(kind());
            }
            if (nameCode() == 0) {
                setNameCode(NpcNamesSC.random(rand));
            }
        }
        return data;
    }

    /**
     * ПГ-2, server: dressed for `rank` - the suit 4/4 lit turquoise, a sheathed blade or a beam gun, the reserve, HP,
     * experience. Anything worn before is replaced.
     */
    public void equip(NpcKindSC rank) {
        boolean senior = rank == NpcKindSC.SENIOR_WARDEN;
        for (int i = 1; i <= 4; i++) {
            setCurrentItemOrArmor(i, null);
        }
        MobCombatSC.dress(this, suitOf(rank), PIECES, GLOW, rand);
        ItemStack weapon;
        if (rand.nextFloat() < BLADE_CHANCE) {
            weapon = new ItemStack(ModItems.BLADES.get(senior ? BladeType.QUANTUM : BladeType.NANO));      // sheathed: its light off
        } else {
            weapon = MobCombatSC.gunStack(senior ? WeaponType.PLASMA_RIFLE : WeaponType.ION_CUTTER);
        }
        setCurrentItemOrArmor(0, weapon);
        armor.helium = senior;
        armor.emergency = false;
        armor.regenPerSecond = 0F;
        float[] r = reserveFor(rank);
        armor.spawnReserveForDamage(r[0], r[1]);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(senior ? SENIOR_HP : SENTRY_HP);
        setHealth(getMaxHealth());
        experienceValue = senior ? XP_SENIOR : XP_SENTRY;
    }

    /** ПГ-2: the rank's suit (pure). */
    public static ArmorSuit suitOf(NpcKindSC rank) {
        return rank == NpcKindSC.SENIOR_WARDEN ? ArmorSuit.QUANTUM : ArmorSuit.NANO;
    }

    /** ПГ-2: {min, max} blockable damage that drains the rank's reserve (pure). */
    public static float[] reserveFor(NpcKindSC rank) {
        return rank == NpcKindSC.SENIOR_WARDEN ? new float[]{SENIOR_RESERVE_MIN, SENIOR_RESERVE_MAX} : new float[]{SENTRY_RESERVE_MIN, SENTRY_RESERVE_MAX};
    }

    // ------------------------------------------------------------------ duty (pure; EntityAIWardenPatrolSC asks)

    /** The slot's index among the slots of its own kind (Sentry #0, #1...), -1 for a bad slot. */
    public static int rankIndex(SettlementRegistrySC.Settlement s, int slot) {
        if (s == null || slot < 0 || slot >= s.slots.size()) {
            return -1;
        }
        int kind = s.slots.get(slot).kind, n = 0;
        for (int i = 0; i < slot; i++) {
            if (s.slots.get(i).kind == kind) {
                n++;
            }
        }
        return n;
    }

    /**
     * ПГ-5: slot `slot`'s duty - a Senior at its post (a tower, the gate); Sentry #0 at the gate; the next HEART_GUARDS
     * Sentries at the Heart at night; the other Sentries on patrol (an Outpost's two: the gate and one patrolling, who
     * stands at the Heart at night).
     */
    public static int role(SettlementRegistrySC.Settlement s, int slot, boolean night) {
        int n = rankIndex(s, slot);
        if (n < 0) {
            return ROLE_PATROL;
        }
        if (s.slots.get(slot).kindOf() == NpcKindSC.SENIOR_WARDEN) {
            return ROLE_POST;
        }
        if (n == 0) {
            return ROLE_GATE;
        }
        return night && n <= HEART_GUARDS ? ROLE_HEART : ROLE_PATROL;
    }

    /**
     * Where slot `slot` stands for `role` (ROLE_PATROL: null - it walks the ring): the gate - its post, else P_GATE;
     * a Senior - its post, else its tower (P_TOWER + its index), else P_GATE; the Heart - beside the Clone Capsule's
     * spot (P_CLONE), else beside the core. Pure.
     */
    public static int[] dutyPoint(SettlementRegistrySC.Settlement s, int slot, int role) {
        if (s == null || role == ROLE_PATROL) {
            return null;
        }
        SettlementRegistrySC.Slot sl = slot >= 0 && slot < s.slots.size() ? s.slots.get(slot) : null;
        int[] p;
        switch (role) {
            case ROLE_GATE:
                p = NpcLinkSC.post(sl);
                return p != null ? p : NpcLinkSC.point(s, SettlementRegistrySC.P_GATE);
            case ROLE_POST:
                p = NpcLinkSC.post(sl);
                if (p == null) {
                    p = NpcLinkSC.point(s, SettlementRegistrySC.P_TOWER + Math.max(0, rankIndex(s, slot)));
                }
                return p != null ? p : NpcLinkSC.point(s, SettlementRegistrySC.P_GATE);
            default:
                int side = rankIndex(s, slot) % 2 == 1 ? -1 : 1;
                p = NpcLinkSC.point(s, SettlementRegistrySC.P_CLONE);
                if (p != null) {
                    return new int[]{p[0] + side * HEART_SPREAD, p[1], p[2]};
                }
                p = NpcLinkSC.core(s);
                return new int[]{p[0] + side * HEART_CORE, p[1], p[2]};
        }
    }

    /** ПГ-3: the reserve is low enough to go to a pad / full enough to leave it (pure). */
    public static boolean needsPad(float reserve, float max) {
        return max > 0F && reserve < max * RECHARGE_BELOW;
    }

    public static boolean padDone(float reserve, float max) {
        return max <= 0F || reserve >= max * RECHARGE_UNTIL;
    }

    // ------------------------------------------------------------------ the brain's state (server)

    public NpcLinkSC link() {
        return link;
    }

    public boolean isBound() {
        return settlementId >= 0;
    }

    /** The brain's look round (EntityAIWardenBrainSC). */
    public void setBrain(EntityPlayer warned, EntityPlayer watched, boolean returning, List<EntityPlayer> partners) {
        this.warned = warned;
        this.watched = watched;
        this.returning = returning;
        this.partners = partners == null ? Collections.<EntityPlayer>emptyList() : partners;
    }

    /** The provoked player inside his 2 s warning (it faces him, blade drawn), or null. */
    public EntityPlayer warnedPlayer() {
        return warned != null && warned.isEntityAlive() && warned.worldObj == worldObj ? warned : null;
    }

    /** The player it should walk up to and watch, or null. */
    public EntityPlayer watchedPlayer() {
        return watched != null && watched.isEntityAlive() && watched.worldObj == worldObj ? watched : null;
    }

    /** Past its leash: walking back, no target. */
    public boolean returning() {
        return returning;
    }

    /** ПГ-10: the «Партнёры» (and better) within reach - it helps them, nods to them. Don't keep the list. */
    public List<EntityPlayer> partners() {
        return partners;
    }

    /**
     * The alert to show (server): the DataWatcher (the client's lamp), the blade drawn from WARNING up (ПГ-6 «клинок
     * наголо»), the alarm when it rises to WARNING.
     */
    public void showAlert(int a) {
        int was = alert();
        setAlert(a);
        drawBlade(a >= GuardAlertSC.WARNING);
        if (a == GuardAlertSC.WARNING && was < GuardAlertSC.WARNING) {
            worldObj.playSoundAtEntity(this, "note.pling", 1.5F, 0.6F);
        }
    }

    /** A held blade lit or sheathed - a new stack only when it changes (the equipment packet carries its NBT). */
    private void drawBlade(boolean on) {
        ItemStack held = getHeldItem();
        if (ItemBladeSC.typeOf(held) == null || ItemBladeSC.isEnabled(held, BladeFeature.BLADE) == on) {
            return;
        }
        ItemStack c = held.copy();
        ItemBladeSC.setEnabled(c, BladeFeature.BLADE, on);
        setCurrentItemOrArmor(0, c);
        if (on) {
            SoundsSC.play(worldObj, posX, posY + 1.2D, posZ, "power.on", 0.7F, 1.3F);
        }
    }

    // ------------------------------------------------------------------ the armour (as EntityArmoredMobSC)

    @Override
    public MobArmorSC mobArmor() {
        return armor;
    }

    /** ПГ-9: never drops the worn equipment, Looting or not. */
    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
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

    @Override
    public int getTotalArmorValue() {
        return worldObj != null && worldObj.isRemote ? super.getTotalArmorValue() : armor.points();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            if ((worldObj.getTotalWorldTime() + getEntityId()) % 20L == 0L && NpcLinkSC.exiled(this)) {
                return;                                         // ПВ-8: a bound guard out of the Substrate is removed
            }
            armor.serverTick();
        }
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean allowLeashing() {
        return false;
    }

    /** ПС-4: the blade 6 / 8, the fist 3..4 with a gun in hand - never the held item's attributes. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, MobCombatSC.meleeDamage(getHeldItem(), MobCombatSC.FIST, rand));
    }

    /**
     * Right-click: one line - its name, rank and a word by the player's standing here («Проходи. Ничего не трогай.»).
     * Always taken: no name tag on a guard. A FakePlayer: nothing.
     */
    @Override
    protected boolean interact(EntityPlayer p) {
        if (!isEntityAlive()) {
            return false;
        }
        if (worldObj.isRemote) {
            return true;
        }
        if (!(p instanceof EntityPlayerMP) || p instanceof FakePlayer) {
            return false;
        }
        p.addChatComponentMessage(greeting(p));
        return true;
    }

    private IChatComponent greeting(EntityPlayer p) {
        String say;
        SettlementRegistrySC.Settlement s = link.get();
        if (s == null) {
            say = "stray";
        } else if (alert() != GuardAlertSC.CALM) {
            say = "busy";
        } else {
            SettlementRegistrySC r = SettlementRegistrySC.get(worldObj);
            SettlementRegistrySC.PlayerRec rec = r == null ? null : r.playerIfAny(p.getUniqueID());
            SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
            int level = ReputationSC.localLevel(rec == null ? 0 : rec.reputation, l != null && l.enemy(worldObj.getTotalWorldTime()));
            say = level >= ReputationSC.PARTNER ? "partner" : ReputationSC.LEVEL_KEYS[level];
        }
        IChatComponent msg = new ChatComponentTranslation("sc.settle.warden.info", NpcNamesSC.name(nameCode()),
                new ChatComponentTranslation(kind().langKey()), new ChatComponentTranslation("sc.settle.warden.say." + say));
        msg.getChatStyle().setColor(EnumChatFormatting.DARK_AQUA);
        return msg;
    }

    /** ПГ-9: the slot's timer (package C), the «казённая» armour «into the dome» in sparks - nothing of it stays. */
    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (worldObj.isRemote || !dead) {
            return;
        }
        NpcLinkSC.died(this);
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            ws.func_147487_a("fireworksSpark", posX, posY + height * 0.5D, posZ, 24, 0.3D, 0.5D, 0.3D, 0.08D);
            for (int i = 0; i < 12; i++) {                      // a turquoise trail rising off the body
                ws.func_147487_a("reddust", posX + (rand.nextDouble() - 0.5D) * 0.6D, posY + i * 0.35D, posZ + (rand.nextDouble() - 0.5D) * 0.6D,
                        0, 0.24D, 0.82D, 0.78D, 1.0D);
            }
        }
        SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.off", 1F, 0.8F);
        for (int i = 0; i < 5; i++) {
            setCurrentItemOrArmor(i, null);
        }
    }

    // ------------------------------------------------------------------ sounds: vanilla's, lower than a Tuner's

    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return "mob.villager.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.villager.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.7F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        armor.write(tag);
        NBTTagCompound m = new NBTTagCompound();
        m.setInteger("id", settlementId);
        m.setInteger("slot", slot);
        if (boundCore != null) {
            m.setIntArray("core", boundCore.clone());
        }
        tag.setTag(ISettlementMemberSC.NBT, m);
        tag.setByte("scKind", (byte) kind().ordinal());
        tag.setInteger("scName", nameCode());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        armor.read(tag);
        armor.sync();
        if (tag.hasKey(ISettlementMemberSC.NBT)) {
            NBTTagCompound m = tag.getCompoundTag(ISettlementMemberSC.NBT);
            settlementId = m.getInteger("id");
            slot = m.getInteger("slot");
            int[] c = m.getIntArray("core");
            boundCore = c.length == 2 ? c : null;
        }
        setKind(NpcKindSC.of(tag.getByte("scKind")));
        setNameCode(tag.getInteger("scName"));
        experienceValue = kind() == NpcKindSC.SENIOR_WARDEN ? XP_SENIOR : XP_SENTRY;
        link.forget();
    }

    // ------------------------------------------------------------------ IEmpTargetSC (as EntityArmoredMobSC)

    @Override
    public boolean empArmoured() {
        if (armor.suit() == null) {
            return false;
        }
        return worldObj != null && worldObj.isRemote ? !MobArmorSC.clientFlag(this, MobArmorSC.FLAG_DOWN) : !armor.down();
    }

    /** ПГ-6: a Point by a real player provokes (SettlementProtectSC.violation POINT_WARDEN) - before the armour's answer. */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        if (mode == EmpMode.POINT && !worldObj.isRemote && shooter instanceof EntityPlayer && !(shooter instanceof FakePlayer)) {
            SettlementRegistrySC.Settlement s = link.get();
            if (s != null) {
                SettlementProtectSC.violation((EntityPlayer) shooter, s, ViolationRulesSC.POINT_WARDEN);
            }
        }
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
            return 0;
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
