package com.sc.mob;

import java.util.Map;
import java.util.UUID;

import com.sc.arena.ArenaDataSC;
import com.sc.arena.ArenaWorldSC;
import com.sc.dim.SubstrateSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.mob.ai.EntityAIWardenLeashSC;
import com.sc.mob.ai.EntityAIWardenPointSC;
import com.sc.mob.ai.EntityAIWardenPressSC;
import com.sc.mob.ai.EntityAIWardenSilenceSC;
import com.sc.util.BladeType;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5, rules ПС-50..ПС-58, ПТ-21..ПТ-24; proposal §9 «Босс: Смотритель Пробоя»): the
 * Breakdown Warden - 3.5 blocks tall; the torso a huge Singular chestplate-reactor with rings of dark particles, four arms (the
 * Singular Blade and the ПЭ-1М in two of them), a skirt of cables; four Containment Coils (EntityContainmentCoilSC) power its shield
 * by beams. The only IBossDisplayData of the mod (its bar - the render's BossStatus). HP WardenRulesSC.maxHealth(players). Three
 * phases by its health (WardenRulesSC - every number there): «Удержание», «Пробой», «Перегрузка». Summoned by the Breakdown Console
 * (ArenaWorldSC.start -> summon), born in Si-14; one fight an arena (the record's fightId - a stale Warden leaves the world).
 * <p>
 * Fixed (the coordinator's, §2.2, §2.6, §2.7): the class, NAME (pool id 22), the World constructor (EntityList), the DataWatcher
 * slots, the NBT keys, IBossDisplayData, IOwnDropsSC (MobDropsSC hands it the drops), alwaysElite (no «Казнь»), outside the
 * census, summon's signature. Everything else is B's:
 * <ul>
 * <li>the suit: the full Singular set of the top level (MobCombatSC.dressSingular - the scheme L «Литограф»'s colours if B wishes,
 * else H «Пустота»), helium (the shield in «Удержание» and «Пробой»), the reserve RESERVE_MIN..MAX, REGEN_PER_SECOND after
 * REGEN_DELAY_TICKS; the blade BLADE_DAMAGE; the ПЭ-1М (EmpMobGunSC: Points at the strongest-armoured player, ПБ-17 - its warning,
 * line of sight, immunity) in every phase;</li>
 * <li>«Удержание» (ПС-52): invulnerable while a coil is lit (attackEntityFrom refused - sparks off its shield, sc.warden.shielded to
 * the shooter once in 3 s); all four dark - WINDOW_TICKS with mobArmor().emergency (iron points, no shield), then every coil lit
 * again; Hollow Shell waves (SpawnDirectorSC.spawnAt from the canyons' mouths, born in Si-14, ACT_SUMMON);</li>
 * <li>«Пробой» (ПС-53): the highlight (DW_HIGHLIGHT; empHit override: a Point into the highlighted piece x HIGHLIGHT_MUL and
 * HIGHLIGHT_RESERVE off the reserve; the chestplate highlighted - every piece suppressed SET_TICKS); the gravity press (as the
 * Sentinel's: ACT_PRESS, PRESS_HUM, PRESS_TICKS within PRESS_RADIUS); «Импульс глушения» (ACT_SILENCE, SILENCE_HUM, then every
 * player within SILENCE_RADIUS it can see gets a ПЭ-1 Pulse by the rules - immunity, cover by line of sight, the field's EMP
 * screen); cracks (the arcCrack vent placed into the floor, ArenaDataSC.Arena.addCrack); nanite swarms under the shared cap;</li>
 * <li>«Перегрузка» (ПС-54): no shield; WardenRulesSC.overheated - protection off (ACT_OVERHEAT, the chestplate white-hot); the 60 s
 * clock (DW_TIMER) - WardenRulesSC.coreResetDue: ACT_CORE_RESET for CORE_RESET_WARN, then CORE_RESET_DAMAGE of «Пробой»
 * (BreakdownDamageSC.of(this)) to every player in the arena without a solid block (a column) between him and its chest; no block
 * broken; the clock again;</li>
 * <li>the fight's bookkeeping (ПС-56): every 20 ticks - the players within SCALE_RADIUS (the record's lastPlayerAt, maxPlayers; the
 * health rescaled up - WardenRulesSC.rescaled), the leash LEASH round the centre, fightCurrent (else setDead);</li>
 * <li>the drops (ПТ-21..ПТ-24, ownDrops): every player with WardenRulesSC.lootEligible(share) whose loot is due (PlayerRec.extra
 * WardenRulesSC.EXTRA) - the Breakdown Matrix, 4..8 Crumbs (real ones: beyond CrumbLimitSC), 1..2 Clots, 4..8 Singular Armour
 * Scrap, PAINT_CHANCE the scheme «Литограф», one trophy roll (WardenRulesSC.trophyMeta), the first kill's Journal №12 - into his
 * inventory (online and alive) or kept as PENDING (WardenLootSC delivers it); SingularProgressSC.wardenDefeated for each; then
 * ArenaWorldSC.bossDied. ПТ-1 holds: born in Si-14, died in the Substrate, real players (an egg's Warden - experience only);</li>
 * <li>the clients (BossClientSC, mob/client): the model (ModelBreakdownWardenSC), its render with the bar (BossStatus), the coils'
 * beams to it, the highlight's white glow, the telegraphs (ПС-9).</li>
 * </ul>
 * <p>
 * Package B: the fight itself is WardenFightSC's (the phases, the coils, the waves, the highlight, the cracks, the swarms, the heat,
 * the blast, the bookkeeping, scWarden); the hums are the AI's (EntityAIWardenPressSC, EntityAIWardenSilenceSC), the ПЭ-1М's Points
 * EntityAIWardenPointSC (the strongest armour in sight, ПЭ-1М numbers), the leash EntityAIWardenLeashSC; the loot WardenLootSC's. The
 * suit is «Литограф»'s (the drawn suit is the Warden's own model, ModelBreakdownWardenSC - the worn stacks are its armour and its
 * trophies). Without the mobs' ПЭ-1 (EmpMobGunSC.allowed() false at the summons) it holds the Singular Blade instead - no Points, no
 * silencing pulse, no ПЭ-1М trophy. DW 31 (B's): the press's stage (WardenFightSC.STAGE_*). An egg's Warden fights round where it
 * stood - no coils, no waves, no cracks, no loot.
 */
public class EntityBreakdownWardenSC extends EntityArmoredMobSC implements IBossDisplayData, IOwnDropsSC {

    public static final String NAME = "breakdownWarden";
    public static final double SPEED = 0.22D, FOLLOW = 48D;
    public static final int XP = 500;
    /**
     * DataWatcher (fixed, 26..30 - 31 is B's): the phase and its flags (byte: bits 0..1 the phase 1..3; FLAG_SHIELDED - a coil is lit,
     * FLAG_WINDOW - the 10 s window, FLAG_OVERHEAT - no protection now, FLAG_RESET_ARMED - the blast winding up); the highlighted
     * piece (byte: -1 none, 0..3 armorType, SET_ALL the whole set suppressed); the lit coils (byte, bits 0..3 by coil index); the
     * phase's clock (int: seconds left, rounded up - the window's, or the 60 s to the blast; seconds, so it changes once a second,
     * not every tick); the players the health is scaled for (byte).
     */
    public static final int DW_PHASE = 26, DW_HIGHLIGHT = 27, DW_COILS = 28, DW_TIMER = 29, DW_SCALE = 30;
    public static final int FLAG_SHIELDED = 4, FLAG_WINDOW = 8, FLAG_OVERHEAT = 16, FLAG_RESET_ARMED = 32, SET_ALL = 4;
    /** DataWatcher (B's): the press's stage (byte: WardenFightSC.STAGE_NONE / HUM / PRESS). */
    public static final int DW_PRESS = 31;
    /** Entity NBT: its fight {ri, rj, id} (ArenaDataSC), the phase's state (B's). */
    public static final String NBT_FIGHT = "scFight", NBT_STATE = "scWarden";
    /** ПС-50: its suit - the top Singular level in the scheme «Литограф» (the Lithographers' own warden). */
    public static final int LEVEL = SingularLevel.MAX;
    public static final SingularScheme SCHEME = SingularScheme.L;
    /** The ПЭ-1М's reach (the player's Point reach: the pit is 60 across). */
    public static final double EMP_RANGE = EmpMobGunSC.RANGE;

    /** Its arena's region and fight (none: -1 / 0 - an egg's Warden: it fights without coils, no loot). */
    protected int arenaRi, arenaRj;
    protected long fightId;
    protected boolean bound;
    /** Server: the fight (made in the constructor - entityInit runs before it, so nothing there may touch it). */
    private final WardenFightSC fight;

    public EntityBreakdownWardenSC(World world) {
        super(world);
        setSize(WardenRulesSC.WIDTH, WardenRulesSC.HEIGHT);
        isImmuneToFire = true;
        experienceValue = XP;
        fight = new WardenFightSC(this);
        getNavigator().setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWardenLeashSC(this, 1.1D));
        tasks.addTask(2, new EntityAIWardenSilenceSC(this));
        tasks.addTask(2, new EntityAIWardenPressSC(this));
        tasks.addTask(3, new EntityAIWardenPointSC(this, EMP_RANGE));
        tasks.addTask(4, new EntityAIAttackOnCollide(this, 1.0D, false));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 16F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        // §13 п.13: a look for a player about once in 10 ticks (no target: the 97 x 97 box and canSee every tick otherwise)
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 10, true));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_PHASE, Byte.valueOf((byte) WardenRulesSC.PHASE_HOLD));
        dataWatcher.addObject(DW_HIGHLIGHT, Byte.valueOf((byte) -1));
        dataWatcher.addObject(DW_COILS, Byte.valueOf((byte) 0));
        dataWatcher.addObject(DW_TIMER, Integer.valueOf(0));
        dataWatcher.addObject(DW_SCALE, Byte.valueOf((byte) 1));
        dataWatcher.addObject(DW_PRESS, Byte.valueOf((byte) WardenFightSC.STAGE_NONE));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(WardenRulesSC.BASE_HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(WardenRulesSC.BLADE_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    /**
     * ArenaWorldSC.start (package A): the Warden of arena `a`'s current fight (a.fightId set already) for `players` players - made at
     * the arena's middle on its floor, dressed, born in Si-14, persistent, its coils placed on the pedestals (B), in the world.
     * @return it, or null (couldn't be placed)
     */
    public static EntityBreakdownWardenSC summon(WorldServer w, ArenaDataSC.Arena a, int players) {
        EntityBreakdownWardenSC boss = new EntityBreakdownWardenSC(w);
        boss.bindTo(a);
        boss.setLocationAndAngles(a.x + 0.5D, a.floorY + 1, a.z + 0.5D, 0F, 0F);
        boss.onSpawnWithEgg(null);
        BornInSubstrateSC.mark(boss);
        boss.func_110163_bv();
        boss.fight.scaleTo(Math.max(1, Math.max(players, a.maxPlayers)), true);   // ПС-50: the health for the players there
        if (!w.spawnEntityInWorld(boss)) {
            return null;
        }
        boss.fight.placeCoils();                                // ПС-52: the four coils on their pedestals
        boss.fight.sync();
        return boss;
    }

    /** Its fight is arena `a`'s current one. */
    public void bindTo(ArenaDataSC.Arena a) {
        arenaRi = a.ri;
        arenaRj = a.rj;
        fightId = a.fightId;
        bound = true;
    }

    public boolean bound() {
        return bound;
    }

    /** Its arena's region (i, j) and its fight's id (the coils' scFight). */
    public int arenaRi() {
        return arenaRi;
    }

    public int arenaRj() {
        return arenaRj;
    }

    public long fightId() {
        return fightId;
    }

    /** Its arena's record (server; null: an egg's Warden, or the record gone). */
    public ArenaDataSC.Arena arena() {
        ArenaDataSC d = bound ? ArenaDataSC.get(worldObj) : null;
        return d == null ? null : d.arena(arenaRi, arenaRj);
    }

    /** Server: its fight (the AI and the world test). */
    public WardenFightSC fight() {
        return fight;
    }

    /** Server: bound, and its fight is still its arena's current one (ArenaWorldSC.fightCurrent; an egg's Warden - false). */
    public boolean fightCurrent() {
        return bound && ArenaWorldSC.fightCurrent(worldObj, arenaRi, arenaRj, fightId);
    }

    /**
     * ПТ-1, server: its arena's own Warden - its current fight's, born in Si-14, in the Substrate. Only such a one gives the personal
     * loot and the boss's progress points; an egg's, a /summon's or a stale one (its fight reset while it lay unloaded) is a mob.
     */
    public boolean arenaWarden() {
        return fightCurrent() && BornInSubstrateSC.isBorn(this) && SubstrateSC.is(worldObj);
    }

    /** Both sides: the phase 1..3. */
    public int phase() {
        return dataWatcher.getWatchableObjectByte(DW_PHASE) & 3;
    }

    /** Both sides: a flag of DW_PHASE (FLAG_*). */
    public boolean phaseFlag(int flag) {
        return (dataWatcher.getWatchableObjectByte(DW_PHASE) & flag) != 0;
    }

    /** Both sides: the highlighted piece (-1 none, 0..3, SET_ALL). */
    public int highlight() {
        return dataWatcher.getWatchableObjectByte(DW_HIGHLIGHT);
    }

    /** Both sides: the lit coils' bits. */
    public int coilsLit() {
        return dataWatcher.getWatchableObjectByte(DW_COILS) & 15;
    }

    /** Both sides: the phase's clock (seconds left, rounded up). */
    public int timer() {
        return dataWatcher.getWatchableObjectInt(DW_TIMER);
    }

    /** Both sides: the players its health is scaled for. */
    public int scaledFor() {
        return dataWatcher.getWatchableObjectByte(DW_SCALE);
    }

    /** Both sides: the press's stage (WardenFightSC.STAGE_*). */
    public int pressStage() {
        return dataWatcher.getWatchableObjectByte(DW_PRESS);
    }

    /** Server (WardenFightSC.sync): the watched state from the fight - the DataWatcher sends only what changed. */
    void syncWatchers(int phaseFlags, int highlightPiece, int coilBits, int timerSeconds, int scale, int press) {
        dataWatcher.updateObject(DW_PHASE, Byte.valueOf((byte) phaseFlags));
        dataWatcher.updateObject(DW_HIGHLIGHT, Byte.valueOf((byte) highlightPiece));
        dataWatcher.updateObject(DW_COILS, Byte.valueOf((byte) coilBits));
        dataWatcher.updateObject(DW_TIMER, Integer.valueOf(timerSeconds));
        dataWatcher.updateObject(DW_SCALE, Byte.valueOf((byte) Math.min(127, scale)));
        dataWatcher.updateObject(DW_PRESS, Byte.valueOf((byte) press));
    }

    /** Server (its coils): none lights again on its own now (the window, or «Удержание» over). */
    public boolean coilsHeld() {
        return fight.coilsHeld();
    }

    /** It holds the ПЭ-1М (both sides: the held stack is synced) - the Points, the silencing pulse, the ПЭ-1М trophy. */
    public boolean holdsPe1m() {
        return ItemEmpGunSC.upgraded(getHeldItem());
    }

    @Override
    protected boolean alwaysElite() {
        return true;
    }

    /** Not a spawn director's mob: the census leaves it (and its fight) out. */
    @Override
    public boolean censusCounts() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    // ------------------------------------------------------------------ the suit, the blow

    /** The summons, the spawn egg: the suit «Литограф» of the top level, the ПЭ-1М (or the blade), helium, the reserve. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote) {
            MobCombatSC.dressSingular(this, LEVEL, SCHEME);
            ItemStack held = EmpMobGunSC.allowed() && ModItems.empGun != null
                    ? new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_UPGRADED) : MobCombatSC.litBlade(BladeType.SINGULAR);
            setCurrentItemOrArmor(0, held);
            MobArmorSC a = mobArmor();
            a.helium = true;                                    // the shield in «Удержание» and «Пробой»
            a.emergency = false;
            a.regenPerSecond = WardenRulesSC.REGEN_PER_SECOND;
            a.regenDelayTicks = WardenRulesSC.REGEN_DELAY_TICKS;
            a.spawnReserveForDamage(WardenRulesSC.RESERVE_MIN, WardenRulesSC.RESERVE_MAX);
        }
        return data;
    }

    /** ПС-55: the Singular Blade's 16 (the boss's cap) - never the held item's attributes; x damageMul(). */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return MobCombatSC.melee(this, target, WardenRulesSC.BLADE_DAMAGE * damageMul());
    }

    /**
     * ПС-52: while the coils' shield holds, every blow is refused (sparks; the shooter told) - a command's outOfWorld excepted. A
     * refused blow takes nothing (no health, no reserve, no hurt frames), so it is not in the shares of the damage (ПТ-21: the
     * suit's helium shield is - it takes the reserve, MobArmorSC.shield -> MobEventsSC.shieldTook); counted, an endless hail on the
     * coils' shield would outweigh the whole fight. The world's own «Пробой» (its cracks in the floor, a geyser) never hurts the
     * Breakdown's Warden: it would only dilute the players' shares of the damage (ПТ-21).
     */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src != null && BreakdownDamageSC.TYPE_WORLD.equals(src.damageType)) {
            return false;
        }
        if (!worldObj.isRemote && src != null && src != DamageSource.outOfWorld && isEntityAlive() && fight.shieldedByCoils()) {
            fight.shieldedHit(src);
            return false;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** ПС-54: overheated - no protection at all (the shield is gone with the helium already): the blow goes to the health whole. */
    @Override
    protected float applyArmorCalculations(DamageSource src, float amount) {
        if (!worldObj.isRemote && fight.overheated()) {
            return amount;
        }
        return super.applyArmorCalculations(src, amount);
    }

    /** ПС-53: the armour's own answer, then the highlight's bonus (a Point into the glowing piece). */
    @Override
    public EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces) {
        EmpSC.HitResult r = super.empHit(shooter, mode, upgraded, pieces);
        if (!worldObj.isRemote) {
            fight.onEmpHit(mode, r);
        }
        return r;
    }

    @Override
    protected void fall(float distance) {
    }

    // ------------------------------------------------------------------ the tick, the death

    @Override
    public void onLivingUpdate() {
        // ПС-56: a stale Warden (its fight was reset) leaves the world - asked on its first tick too (a reloaded one's count starts
        // again: else it would fight up to 5 s for a record not its own), before its AI and its fight do anything
        if (!worldObj.isRemote && !isDead && bound && (ticksExisted <= 1 || ticksExisted % 100 == 0) && !fightCurrent()) {
            fight.dropCoils();
            setDead();
            return;
        }
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            clientFx();
            return;
        }
        if (isDead) {
            return;
        }
        if (isEntityAlive()) {
            fight.tick();
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);                                     // the drops (ownDrops) are decided in here
        if (!worldObj.isRemote && dead) {
            fight.dropCoils();
            ArenaDataSC.Arena a = arena();
            if (a != null && worldObj instanceof WorldServer) {
                ArenaWorldSC.bossDied((WorldServer) worldObj, a, this);
            }
        }
    }

    /** ПТ-21..ПТ-24: the personal loot (WardenLootSC). */
    @Override
    public void ownDrops(LivingDropsEvent event, Map<UUID, Float> shares, boolean doMobLoot) {
        WardenLootSC.award(this, shares, doMobLoot);
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (bound) {
            NBTTagCompound f = new NBTTagCompound();
            f.setInteger("ri", arenaRi);
            f.setInteger("rj", arenaRj);
            f.setLong("id", fightId);
            tag.setTag(NBT_FIGHT, f);
        }
        NBTTagCompound s = new NBTTagCompound();
        fight.write(s);
        tag.setTag(NBT_STATE, s);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        bound = tag.hasKey(NBT_FIGHT);
        NBTTagCompound f = tag.getCompoundTag(NBT_FIGHT);
        arenaRi = f.getInteger("ri");
        arenaRj = f.getInteger("rj");
        fightId = f.getLong("id");
        if (tag.hasKey(NBT_STATE)) {
            fight.read(tag.getCompoundTag(NBT_STATE));
        }
    }

    // ------------------------------------------------------------------ the clients' particles (ПС-9)

    private void clientFx() {
        if (deathTime > 0) {
            deathFx();
            return;
        }
        double chest = posY + height * 0.6D;
        double a = ticksExisted * 0.22D;                        // two rings of dark particles round the reactor, turning both ways
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            worldObj.spawnParticle("smoke", posX + Math.cos(ang) * 1.3D, chest + 0.35D, posZ + Math.sin(ang) * 1.3D, 0D, 0D, 0D);
            worldObj.spawnParticle("smoke", posX + Math.cos(-ang) * 1.1D, chest - 0.45D, posZ + Math.sin(-ang) * 1.1D, 0D, 0D, 0D);
        }
        if (phaseFlag(FLAG_SHIELDED) && rand.nextInt(3) == 0) {    // the coils' shield shimmers
            double ang = rand.nextDouble() * Math.PI * 2D, h = rand.nextDouble() * height;
            worldObj.spawnParticle("instantSpell", posX + Math.cos(ang) * 1.1D, posY + h, posZ + Math.sin(ang) * 1.1D, 0D, 0D, 0D);
        }
        int act = mobAction();
        if (act == IMobActionSC.ACT_SILENCE) {                   // motes drawn in from the pulse's reach
            for (int i = 0; i < 3; i++) {
                double ang = rand.nextDouble() * Math.PI * 2D, r = 3D + rand.nextDouble() * (WardenRulesSC.SILENCE_RADIUS - 3);
                worldObj.spawnParticle("portal", posX, chest, posZ, Math.cos(ang) * r, rand.nextDouble() * 0.6D, Math.sin(ang) * r);
            }
        } else if (act == IMobActionSC.ACT_CORE_RESET) {         // the core winds up: white sparks and red rising from it
            worldObj.spawnParticle("fireworksSpark", posX + (rand.nextDouble() - 0.5D), chest, posZ + (rand.nextDouble() - 0.5D),
                    (rand.nextDouble() - 0.5D) * 0.4D, 0.2D + rand.nextDouble() * 0.3D, (rand.nextDouble() - 0.5D) * 0.4D);
            double ang = rand.nextDouble() * Math.PI * 2D, r = rand.nextDouble() * 6D;
            worldObj.spawnParticle("reddust", posX + Math.cos(ang) * r, posY + 0.2D, posZ + Math.sin(ang) * r, 1D, 0.25D, 0.2D);
        } else if (act == IMobActionSC.ACT_OVERHEAT) {           // white-hot: the chestplate smokes and flames
            worldObj.spawnParticle(rand.nextBoolean() ? "flame" : "largesmoke", posX + (rand.nextDouble() - 0.5D) * width, chest,
                    posZ + (rand.nextDouble() - 0.5D) * width, 0D, 0.06D, 0D);
        } else if (act == IMobActionSC.ACT_SUMMON) {
            worldObj.spawnParticle("portal", posX, posY + height, posZ, (rand.nextDouble() - 0.5D) * 4D, rand.nextDouble(),
                    (rand.nextDouble() - 0.5D) * 4D);
        }
        int stage = pressStage();
        if (stage == WardenFightSC.STAGE_HUM) {                    // as the Sentinel's: dark motes drawn in, then shards falling
            double ang = rand.nextDouble() * Math.PI * 2D, r = 1.5D + rand.nextDouble() * (WardenRulesSC.PRESS_RADIUS - 1.5D);
            worldObj.spawnParticle("portal", posX, posY + 0.3D, posZ, Math.cos(ang) * r, rand.nextDouble() * 1.5D, Math.sin(ang) * r);
        } else if (stage == WardenFightSC.STAGE_PRESS) {
            for (int i = 0; i < 4; i++) {
                double ang = rand.nextDouble() * Math.PI * 2D, r = rand.nextDouble() * WardenRulesSC.PRESS_RADIUS;
                worldObj.spawnParticle("portal", posX + Math.cos(ang) * r, posY + 0.1D, posZ + Math.sin(ang) * r, 0D, 2.5D, 0D);
            }
        }
    }

    /** Its death: the reactor breaking up - sparks and smoke out of the chest. */
    private void deathFx() {
        double chest = posY + height * 0.6D;
        for (int i = 0; i < 4; i++) {
            worldObj.spawnParticle(i == 0 ? "largeexplode" : "fireworksSpark", posX + (rand.nextDouble() - 0.5D) * 2D,
                    chest + (rand.nextDouble() - 0.5D) * 2D, posZ + (rand.nextDouble() - 0.5D) * 2D, (rand.nextDouble() - 0.5D) * 0.3D,
                    rand.nextDouble() * 0.3D, (rand.nextDouble() - 0.5D) * 0.3D);
        }
    }

    // ------------------------------------------------------------------ sounds: a heavy machine (vanilla's)

    @Override
    protected String getLivingSound() {
        return "mob.wither.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wither.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.4F + (rand.nextFloat() - rand.nextFloat()) * 0.05F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, net.minecraft.block.Block block) {
        playSound("mob.irongolem.walk", 1.2F, 0.5F);
    }
}
