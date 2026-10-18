package com.sc.settle.npc;

import java.util.Collections;
import java.util.List;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBladeSC;
import com.sc.mob.BreakdownDamageSC;
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
import com.sc.settle.npc.ai.EntityAICaptainBlowSC;
import com.sc.settle.npc.ai.EntityAICommandantPostSC;
import com.sc.settle.npc.ai.EntityAICommandantPulseSC;
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
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.SoundsSC;
import com.sc.util.WeaponType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.INpc;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.IMob;
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
 * <li>a ПЭ-1 Point by a player: SettlementProtectSC.violation POINT_WARDEN first, then the armour's answer (2026-10-18, ПГ-20: any
 * ПЭ-1 use - the Pulse and every Siphon second too);</li>
 * <li>death: SettlementRegistrySC.slotDied, the sparks, the equipment gone - only experience.</li>
 * </ul>
 * A stray (a spawn egg's, settlement -1) is a Sentry that only answers who hits it.
 * <p>
 * Ф3, package S (docs/plan-substrate-tech-f3.md §6.4, §6.5; rules ПГ-11..ПГ-14) - two more ranks, the Sentries and the
 * Senior Wardens unchanged:
 * <ul>
 * <li>«Капитан купола» (CAPTAIN): Exo 4/4 with helium (the shield up), the Armour Chip (defenseTier 1), the reserve by
 * CAPTAIN_RESERVE_*, the Exo Blade (CAPTAIN_BLADE), HP 80, experience 30. His post is the guard square (P_SQUARE); he
 * leads the guard - any hostile in the dome or the belt he goes for himself, wherever it is (EntityAIWardenBrainSC, the
 * leash as everyone's). The heavy blow (EntityAICaptainBlowSC): a target within HEAVY_REACH, HEAVY_WINDUP ticks of
 * wind-up (the telegraph IMobActionSC.ACT_WINDUP in DW_ACTION - every client sees the blade charge), then HEAVY_BLOW of
 * «Пробой» (BreakdownDamageSC.of(this): it passes armour and every shield), HEAVY_COOLDOWN after; a ПЭ-1 Point that hits
 * him during the wind-up breaks it off (empHit), and with his chestplate suppressed he doesn't wind up at all.</li>
 * <li>«Комендант города» (COMMANDANT): Singular 4/4 of the highest level (SingularLevel.MAX) in the settlement's colours
 * (COMMANDANT_SCHEME, the dome's turquoise light - the stacks' NBT set once, when he is dressed), helium (the shield), the
 * reserve by COMMANDANT_RESERVE_*, the Singular Blade (COMMANDANT_BLADE), HP 120, experience 50. He keeps to the plaza
 * (P_PLAZA within PLAZA_STAY: EntityAICommandantPostSC) and cuts down who comes up to him; while EmpMobGunSC.allowed(),
 * hostiles in the mod's armour in the dome or the belt that he sees get a ПЭ-1 Pulse (EntityAICommandantPulseSC over
 * package A's EntityAIEmpShooterSC, his reach the dome's radius + COMMANDANT_RANGE_OUT) - he holds the ПЭ-1 for it, the
 * blade otherwise; never at a player (the guards' refusal), and the ПЭ-1 never drops (nothing he wears does).</li>
 * </ul>
 * Both come back from the Clone Capsule after NpcKindSC.CHIEF_RESPAWN_TICKS; a Point at either is a provocation as at
 * any guard (ПГ-14). Their looks: RenderWardenSC (their own skins, the Captain's cloak and the Commandant's mantle).
 * <p>
 * 2026-10-18 (docs/plan-substrate.md §25а, rules ПГ-15..ПГ-20): every rank's suit works as the player's of its tier - WardenSuitSC
 * (the reserve of a charged player set, the plating proof against piercing, Regeneration, the dome's recharge, the heat, the Shield
 * function, the Singular perks); HP 60 / 70 / 80 / 120; «Казнь» as for a player (IExecuteImmuneSC); ПЭ-1 in every mode, the Siphon's
 * draw vented (EmpMobSC.ventsAll). A warden saved before is refitted on loading (WardenSuitSC.VERSION).
 */
public class EntityWardenSC extends EntityCreature implements INpc, ISettlementGuardSC, IArmoredMobSC, IEmpTargetSC, ISubstrateNativeSC,
        com.sc.mob.IMobActionSC, com.sc.mob.IExecuteImmuneSC {

    public static final String NAME = "warden";
    /** DataWatcher (fixed): the rank (byte, NpcKindSC ordinal), the name code (int), the alert (byte, GuardAlertSC.CALM..ATTACKING). */
    public static final int DW_KIND = 24, DW_NAME = 25, DW_ALERT = 26;
    /** Ф3 (the coordinator's, docs/plan-substrate-tech-f3.md §2.4): the telegraph (byte, IMobActionSC.ACT_*) - the Captain's wind-up, the Commandant's Pulse. 28..31 package S. */
    public static final int DW_ACTION = 27;

    /**
     * ПГ-2: HP by rank (Sentry, Senior Warden; 2026-10-18, ПГ-15: 40 / 50 before - with the player's plating a Quantum blade's
     * reference fight is WardenSuitSC.fightSeconds); its walk; the path search's reach (NpcNavSC walks farther in legs). TODO(balance)
     */
    public static final double SENTRY_HP = 60D, SENIOR_HP = 70D, SPEED = 0.3D, FOLLOW = 24D;
    /** Multiples of SPEED: the patrol, a chase, the way back from past the leash. */
    public static final double PATROL = 0.7D, CHASE = 1.0D, BACK = 0.9D;
    /** ПГ-2: 4/4 of the rank's suit; its reserve - ПГ-15, WardenSuitSC.reserveOf (a charged player set: Nano 800, Quantum 2000 units). */
    public static final int PIECES = 4;
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
     * ПГ-3: below RECHARGE_BELOW of its reserve it goes to a charge pad and stands there until RECHARGE_UNTIL, every PAD_EVERY
     * ticks WardenSuitSC.padUnits (ПГ-17: PAD_SHARE of the full reserve a second, PAD_UNITS at least). TODO(balance)
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

    // ---- Ф3 (§6.4, §6.5): the Captain and the Commandant
    /** ПГ-11 / ПГ-12: HP. */
    public static final double CAPTAIN_HP = 80D, COMMANDANT_HP = 120D;
    /** ПГ-11 / ПГ-12: the reserve - ПГ-15, WardenSuitSC.CAPTAIN_RESERVE / COMMANDANT_RESERVE (1600 / 3000 units). */
    /** ПГ-11: the Captain's Armour Chip (MobArmorSC.defenseTier: suppression -30%, the Siphon -40%). TODO(balance) */
    public static final int CAPTAIN_DEFENSE_TIER = 1;
    /** Their blades: the Exo Blade 10, the Singular Blade 12 (MobCombatSC's Singular 13..14 is the hostiles'). TODO(balance) */
    public static final int CAPTAIN_BLADE = 10, COMMANDANT_BLADE = 12;
    /** ПГ-13: only experience. */
    public static final int XP_CAPTAIN = 30, XP_COMMANDANT = 50;
    /**
     * ПГ-11: the Captain's heavy blow - a target within HEAVY_REACH blocks, HEAVY_WINDUP ticks of wind-up, HEAVY_BLOW of
     * «Пробой», HEAVY_COOLDOWN ticks before the next wind-up. TODO(balance): the cooldown.
     */
    public static final int HEAVY_BLOW = 5, HEAVY_REACH = 3, HEAVY_WINDUP = 20, HEAVY_COOLDOWN = 100;
    /**
     * ПГ-12: the Commandant's ПЭ-1 reaches his dome's radius + this; he keeps within PLAZA_STAY blocks of P_PLAZA; a target
     * nearer than COMMANDANT_GUN_FROM gets the blade, not the Pulse. TODO(balance)
     */
    public static final int COMMANDANT_RANGE_OUT = 8, PLAZA_STAY = 7, COMMANDANT_GUN_FROM = 4;
    /** ПГ-12: «в цветах поселения» - the Singular scheme of his pieces (the dome's cyan on the near-black plating) and their level. */
    public static final SingularScheme COMMANDANT_SCHEME = SingularScheme.B;
    public static final int COMMANDANT_LEVEL = SingularLevel.MAX;

    private final MobArmorSC armor;
    /** 2026-10-18, ПГ-15..ПГ-20: the suit's functions on top of the armour (heat, Regeneration, the Shield, the Singular perks). */
    private final WardenSuitSC suit;
    protected int settlementId = -1, slot = -1;
    /** The core column {x, z} of the record bound to (ISettlementMemberSC.boundCore), null unknown. */
    protected int[] boundCore;
    private final NpcLinkSC link;
    /** Server, the brain's (EntityAIWardenBrainSC): the warned and the watched player, past the leash, the «Партнёры» near. */
    private EntityPlayer warned, watched;
    private boolean returning;
    private List<EntityPlayer> partners = Collections.emptyList();
    /** Server, Ф3: a ПЭ-1 Point hit the Captain during his wind-up (EntityAICaptainBlowSC takes it). */
    private boolean windupBroken;
    /** Server, Ф3: the Commandant's Pulse task, made once his dome's radius is known (its reach); null for the others. */
    private EntityAICommandantPulseSC pulse;

    public EntityWardenSC(World world) {
        super(world);
        setSize(0.6F, 1.95F);
        armor = new MobArmorSC(this);
        armor.pierceProof = true;                                      // ПГ-16: before anything is fitted (a /summon too)
        suit = new WardenSuitSC(this);
        link = new NpcLinkSC(this);
        for (int i = 0; i < 5; i++) {
            setEquipmentDropChance(i, 0F);
        }
        experienceValue = XP_SENTRY;
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWardenReturnSC(this));
        tasks.addTask(1, new EntityAICommandantPostSC(this));          // Ф3: only the Commandant's - the plaza
        tasks.addTask(2, new EntityAICaptainBlowSC(this));             // Ф3: only the Captain's - the heavy blow
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
        dataWatcher.addObject(DW_ACTION, Byte.valueOf((byte) com.sc.mob.IMobActionSC.ACT_NONE));
    }

    // ------------------------------------------------------------------ Ф3: the telegraph (IMobActionSC)

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
     * experience. Anything worn before is replaced. Ф3: the Captain and the Commandant - equipChief.
     */
    public void equip(NpcKindSC rank) {
        if (rank != null && rank.chief()) {
            equipChief(rank);
            return;
        }
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
        WardenSuitSC.fit(armor, rank, true);
        suit.heat = 0;
        suit.hot = false;
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(hpOf(rank));
        setHealth(getMaxHealth());
        experienceValue = senior ? XP_SENIOR : XP_SENTRY;
    }

    /**
     * Ф3, server (ПГ-11, ПГ-12): the Captain / the Commandant dressed - the four pieces of his suit lit turquoise (the
     * Commandant's at COMMANDANT_LEVEL in COMMANDANT_SCHEME: the stacks' NBT set here, once), helium in the loop (the
     * shield), the Captain's Armour Chip, the reserve, his blade (sheathed - its light off - until a warning or a fight),
     * HP, experience. Anything worn before is replaced.
     */
    private void equipChief(NpcKindSC rank) {
        boolean commandant = rank == NpcKindSC.COMMANDANT;
        ItemArmorSC[] pieces = ModItems.ARMOR.get(suitOf(rank));
        for (int t = 0; t < 4; t++) {
            ItemStack st = null;
            if (pieces != null && pieces[t] != null) {
                st = new ItemStack(pieces[t]);
                ItemArmorSC.setGlowColor(st, GLOW);
                if (commandant) {
                    SingularLevel.setLevel(st, COMMANDANT_LEVEL);
                    SingularScheme.setScheme(st, COMMANDANT_SCHEME);
                }
            }
            setCurrentItemOrArmor(4 - t, st);                  // armorType 0 (helmet) is slot 4
        }
        setCurrentItemOrArmor(0, chiefBlade(rank));
        armor.helium = true;
        armor.emergency = false;
        armor.defenseTier = commandant ? 0 : CAPTAIN_DEFENSE_TIER;
        WardenSuitSC.fit(armor, rank, true);
        suit.heat = 0;
        suit.hot = false;
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(hpOf(rank));
        setHealth(getMaxHealth());
        experienceValue = xpOf(rank);
    }

    /** Ф3: a chief's blade - the Captain's Exo, the Commandant's Singular - sheathed (drawBlade lights it). */
    private static ItemStack chiefBlade(NpcKindSC rank) {
        return new ItemStack(ModItems.BLADES.get(rank == NpcKindSC.COMMANDANT ? BladeType.SINGULAR : BladeType.EXO));
    }

    /** ПГ-2 / ПГ-11 / ПГ-12: the rank's suit (pure). */
    public static ArmorSuit suitOf(NpcKindSC rank) {
        if (rank == NpcKindSC.CAPTAIN) {
            return ArmorSuit.EXO;
        }
        if (rank == NpcKindSC.COMMANDANT) {
            return ArmorSuit.SINGULAR;
        }
        return rank == NpcKindSC.SENIOR_WARDEN ? ArmorSuit.QUANTUM : ArmorSuit.NANO;
    }

    /** ПГ-15: the rank's full reserve, damage units (pure; WardenSuitSC.reserveOf). */
    public static float reserveOf(NpcKindSC rank) {
        return WardenSuitSC.reserveOf(rank);
    }

    /** ПГ-2 / ПГ-11 / ПГ-12: the rank's HP (pure). */
    public static double hpOf(NpcKindSC rank) {
        if (rank == NpcKindSC.CAPTAIN) {
            return CAPTAIN_HP;
        }
        if (rank == NpcKindSC.COMMANDANT) {
            return COMMANDANT_HP;
        }
        return rank == NpcKindSC.SENIOR_WARDEN ? SENIOR_HP : SENTRY_HP;
    }

    /** ПГ-3: units a charging plate gives every PAD_EVERY ticks (EntityAIWardenPadSC). */
    public static float padUnits(float reserveMax) {
        return WardenSuitSC.padUnits(reserveMax);
    }

    /** ПГ-9 / ПГ-13: the experience a rank gives (pure). */
    public static int xpOf(NpcKindSC rank) {
        if (rank == NpcKindSC.CAPTAIN) {
            return XP_CAPTAIN;
        }
        if (rank == NpcKindSC.COMMANDANT) {
            return XP_COMMANDANT;
        }
        return rank == NpcKindSC.SENIOR_WARDEN ? XP_SENIOR : XP_SENTRY;
    }

    /** Ф3: a chief's blade blow (CAPTAIN_BLADE, COMMANDANT_BLADE), 0 for the other ranks (theirs are MobCombatSC's). Pure. */
    public static int chiefBladeDamage(NpcKindSC rank) {
        return rank == NpcKindSC.CAPTAIN ? CAPTAIN_BLADE : rank == NpcKindSC.COMMANDANT ? COMMANDANT_BLADE : 0;
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
     * stands at the Heart at night). Ф3: the Captain and the Commandant - their posts (the guard square, the plaza), day
     * and night.
     */
    public static int role(SettlementRegistrySC.Settlement s, int slot, boolean night) {
        int n = rankIndex(s, slot);
        if (n < 0) {
            return ROLE_PATROL;
        }
        NpcKindSC k = s.slots.get(slot).kindOf();
        if (k == NpcKindSC.SENIOR_WARDEN || k.chief()) {
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
     * spot (P_CLONE), else beside the core. Ф3: the Captain - his post, else P_SQUARE; the Commandant - his post, else
     * P_PLAZA. Pure.
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
                if (p == null && sl != null && sl.kindOf().chief()) {
                    p = NpcLinkSC.point(s, sl.kindOf() == NpcKindSC.COMMANDANT ? SettlementRegistrySC.P_PLAZA : SettlementRegistrySC.P_SQUARE);
                }
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

    // ------------------------------------------------------------------ Ф3: the Captain and the Commandant (server)

    /** The Commandant of a dome (bound): he keeps to the plaza (ПГ-12). */
    public boolean holdsPlaza() {
        return kind() == NpcKindSC.COMMANDANT && isBound();
    }

    /** The Commandant's plaza: his post, else P_PLAZA, else the core's column; null without a record. */
    public int[] plaza() {
        SettlementRegistrySC.Settlement s = link.get();
        if (s == null) {
            return null;
        }
        int[] p = NpcLinkSC.post(link.slot());
        if (p == null) {
            p = NpcLinkSC.point(s, SettlementRegistrySC.P_PLAZA);
        }
        return p != null ? p : NpcLinkSC.core(s);
    }

    /** `e` is within `extra` blocks past PLAZA_STAY of the plaza `p` (across) - the Commandant reaches it from there. */
    public static boolean nearPlaza(Entity e, int[] p, double extra) {
        double r = PLAZA_STAY + extra;
        return p != null && NpcLinkSC.distSq(e, p) <= r * r;
    }

    /** ПГ-12: the Commandant's ПЭ-1 reach - his dome's radius + COMMANDANT_RANGE_OUT; 0 without a record. */
    public double pulseRange() {
        SettlementRegistrySC.Settlement s = link.get();
        return s == null ? 0D : s.radius + COMMANDANT_RANGE_OUT;
    }

    /** ПГ-12: a target worth the Commandant's Pulse - a hostile in the mod's armour that still holds its reserve (server). */
    public static boolean empWorthy(Entity e) {
        return e instanceof IMob && e instanceof IEmpTargetSC && !(e instanceof ISettlementMemberSC) && ((IEmpTargetSC) e).empArmoured();
    }

    /**
     * ПГ-11, server: the Captain's heavy blow lands on `target` - HEAVY_BLOW of «Пробой» (BreakdownDamageSC.of(this): past
     * armour, the mob's shield and the player's full Exo / Singular shield), a zap and sparks every watcher sees. The wind-up
     * before it is EntityAICaptainBlowSC's; the world test calls this directly. @return the target took it
     */
    public boolean heavyBlow(EntityLivingBase target) {
        if (target == null || worldObj.isRemote) {
            return false;
        }
        boolean hit = target.attackEntityFrom(BreakdownDamageSC.of(this), HEAVY_BLOW);
        double y = target.posY + target.height * 0.5D;
        SoundsSC.play(worldObj, target.posX, y, target.posZ, "field.zap", 1.0F, 0.8F);
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("magicCrit", target.posX, y, target.posZ, 16, 0.3D, 0.4D, 0.3D, 0.15D);
        }
        return hit;
    }

    /** ПГ-11: a ПЭ-1 Point hit the Captain during his wind-up since the last ask (EntityAICaptainBlowSC) - and forgets it. */
    public boolean takeWindupBroken() {
        boolean b = windupBroken;
        windupBroken = false;
        return b;
    }

    /**
     * Server, now and then: the Commandant's Pulse task - made (and put among his tasks) once his record gives its reach,
     * made again if that changes, dropped if he isn't the Commandant any more (another slot's kind); and the ПЭ-1 in his hand
     * while his target is a far hostile in armour that the Pulse may hit, the Singular Blade otherwise (one equipment
     * change when it switches).
     */
    private void chiefDuty() {
        boolean commandant = kind() == NpcKindSC.COMMANDANT;
        double range = commandant ? pulseRange() : 0D;
        if (pulse != null && (!commandant || pulse.reach() != range)) {
            tasks.removeTask(pulse);
            pulse = null;
        }
        if (commandant && range > 0D && pulse == null) {
            pulse = new EntityAICommandantPulseSC(this, range);
            tasks.addTask(2, pulse);
        }
        if (!commandant || armor.suit() == null) {
            return;
        }
        EntityLivingBase t = getAttackTarget();
        boolean gun = t != null && t.isEntityAlive() && EmpMobGunSC.allowed() && empWorthy(t)
                && getDistanceSqToEntity(t) > COMMANDANT_GUN_FROM * COMMANDANT_GUN_FROM;
        ItemStack held = getHeldItem();
        boolean holdsGun = held != null && held.getItem() == ModItems.empGun;
        if (gun && !holdsGun && ModItems.empGun != null) {
            setCurrentItemOrArmor(0, new ItemStack(ModItems.empGun));
        } else if (!gun && holdsGun) {
            setCurrentItemOrArmor(0, chiefBlade(NpcKindSC.COMMANDANT));
            drawBlade(alert() >= GuardAlertSC.WARNING);
        }
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

    /** ПГ-15..ПГ-20: the suit's functions (heat, Regeneration...), server state. */
    public WardenSuitSC suit() {
        return suit;
    }

    /** ПГ-16: «Казнь» finishes a warden only where it may finish players (weapons.bladeExecutePlayers, off by default). */
    @Override
    public boolean executeImmune() {
        return !com.sc.util.ConfigSC.bladeExecutePlayers;
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
            suit.serverTick();
            if ((pulse != null || kind() == NpcKindSC.COMMANDANT) && (worldObj.getTotalWorldTime() + getEntityId()) % 5L == 0L) {
                chiefDuty();                                    // Ф3: after the AI's update - its task list is free to change
            }
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

    /**
     * ПС-4: the blade 6 / 8, the fist 3..4 with a gun in hand - never the held item's attributes. Ф3: the Captain's and the
     * Commandant's blades their own numbers (chiefBladeDamage).
     */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        int chief = chiefBladeDamage(kind());
        if (chief > 0 && MobCombatSC.blade(this) != null) {
            return MobCombatSC.melee(this, target, chief);
        }
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
        // Ф3: the Captain and the Commandant have words of their own (sc.settle.warden.<captain|commandant>.say.*; a stray is a Sentry)
        String words = kind().chief() && s != null ? "sc.settle.warden." + kind().key + ".say." + say : "sc.settle.warden.say." + say;
        IChatComponent msg = new ChatComponentTranslation("sc.settle.warden.info", NpcNamesSC.name(nameCode()),
                new ChatComponentTranslation(kind().langKey()), new ChatComponentTranslation(words));
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
        suit.write(tag);
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
        experienceValue = xpOf(kind());
        boolean old = suit.read(tag) < WardenSuitSC.VERSION;
        if (armor.suit() != null) {
            // the transient flags; the saved reserve's share stays. ПГ-15: a warden saved before its suit worked as the player's
            // gets the rank's reserve (that share of it) and HP
            WardenSuitSC.fit(armor, kind(), false);
            if (old) {
                getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(hpOf(kind()));
            }
        }
        suit.version = WardenSuitSC.VERSION;
        armor.overheated = suit.hot;
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

    /**
     * ПГ-6 / ПГ-14 / ПГ-20: any ПЭ-1 use on a guard by a real player - a Point, a Pulse, a Siphon stream's every second - is an
     * attack on it (SettlementProtectSC.violation POINT_WARDEN: the warning, the -40 once a provocation, the guards' answer),
     * before the armour's answer. Ф3 (ПГ-11): a Point or a Pulse that reaches the Captain during his wind-up breaks it off
     * (EntityAICaptainBlowSC). ПГ-18: a hit that reached the armour heats the suit as a player's.
     */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        empProvokes(shooter);
        if (mode != EmpMode.SIPHON && !worldObj.isRemote && mobAction() == com.sc.mob.IMobActionSC.ACT_WINDUP) {
            windupBroken = true;
        }
        EmpSC.HitResult r = armor.empHit(shooter, mode, upgraded, pieces);
        if (r.anyArmour && !worldObj.isRemote) {
            boolean allImmune = true;
            for (int i = 0; i < 4; i++) {
                allImmune &= r.ticks[i] <= 0;
            }
            r.heat = suit.empHeat(mode, allImmune);
        }
        return r;
    }

    /** ПГ-20: a real player's ПЭ-1 at this guard (any mode) - SettlementProtectSC.violation POINT_WARDEN with its settlement. */
    private void empProvokes(Entity shooter) {
        if (!worldObj.isRemote && shooter instanceof EntityPlayer && !(shooter instanceof FakePlayer)) {
            SettlementRegistrySC.Settlement s = link.get();
            if (s != null) {
                SettlementProtectSC.violation((EntityPlayer) shooter, s, ViolationRulesSC.POINT_WARDEN);
            }
        }
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

    /** ПГ-20: a Siphon stream's second - an attack on the guard as any ПЭ-1 use; the suit heats (ПГ-18), its recharge waits. */
    @Override
    public void empStreamSecond(Entity shooter) {
        empProvokes(shooter);
        armor.streamSecond(shooter);
        if (!worldObj.isRemote && armor.suit() != null) {
            suit.empHeat(EmpMode.SIPHON, false);
        }
    }
}
