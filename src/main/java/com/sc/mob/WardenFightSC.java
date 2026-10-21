package com.sc.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sc.arena.ArenaBlocksSC;
import com.sc.arena.ArenaDataSC;
import com.sc.arena.ArenaLayoutSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.emp.EmpArmorSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpRulesSC;
import com.sc.emp.EmpSC;
import com.sc.emp.EmpShotSC;
import com.sc.finale.FinaleNetSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.3-§5.5, rules ПС-51..ПС-56): the Breakdown Warden's fight - its three phases, the
 * coils, the bookkeeping - the server's side of one EntityBreakdownWardenSC (made by it, ticked from its onLivingUpdate). Every
 * number is WardenRulesSC's. The fight's clocks are tick counters of the Warden's own (they stand while its chunk is unloaded -
 * the arena's reset after RESET_AWAY_TICKS is package A's, by the world's time); saved in its NBT scWarden.
 * <ul>
 * <li>«Удержание» (ПС-52): invulnerable while a coil is lit (shieldedByCoils - a coil not found yet counts as lit); every coil dark or
 * broken - the window (WINDOW_TICKS: mobArmor().emergency - iron points, no shield; FLAG_WINDOW), then every coil lit again; a
 * wave of Hollow Shells every WAVE_EVERY (ACT_SUMMON its last 2 s; waveSize(n), at most WAVE_MAX alive in the arena) from the
 * canyons' mouths (SpawnDirectorSC.spawnAt, born in Si-14; their home the arena's middle, the nearest player their target at once);
 * every Shell in the arena hunts players only (huntPlayersOnly - the coils are no prey; kept by the bookkeeping after a reload); a
 * lost coil (/kill) is put back on its pedestal;</li>
 * <li>«Пробой» (ПС-53): the coils spent (dark for good); the highlight every HIGHLIGHT_EVERY for HIGHLIGHT_TICKS (onEmpHit: a Point
 * into it x HIGHLIGHT_MUL and HIGHLIGHT_RESERVE off the reserve; the chestplate - the whole set SET_TICKS, DW_HIGHLIGHT SET_ALL); the
 * press and «Импульс глушения» (their hums are the AI's: EntityAIWardenPressSC, EntityAIWardenSilenceSC); a Breakdown crack every
 * CRACK_EVERY (the arcCrack vent into a floor plate, at most CRACKS_MAX - ArenaDataSC.Arena.addCrack); a nanite swarm every
 * SWARM_EVERY at the pit's edge (EntityNaniteSC.spawnSwarm - its own caps);</li>
 * <li>«Перегрузка» (ПС-54): helium gone (no shield); overheated - the protection off (FLAG_OVERHEAT, ACT_OVERHEAT; the Warden's
 * applyArmorCalculations lets the blow through); the clock FINISH_TICKS (DW_TIMER, in seconds): coreResetDue - ACT_CORE_RESET and
 * FLAG_RESET_ARMED for CORE_RESET_WARN (sc.warden.reset.warn, the siren), then «сброс ядра» (coreReset), the clock again; the press,
 * the silencing pulse, the cracks and the swarms go on;</li>
 * <li>the phases by the health, one way (phaseOf; a big blow may pass two at once), each with its line in the chat to the arena's
 * players (sc.warden.phase2, .phase3);</li>
 * <li>the bookkeeping (ПС-56, every BOOK_TICKS): the real players within SCALE_RADIUS of the middle (alive, not creative, not a
 * FakePlayer) - the record's lastPlayerAt; they and every real player in the damage log (fighters: a shooter from afar too) - the
 * record's maxPlayers, the health scaled up to the most seen (rescaled: the same share), DW_SCALE;
 * the coils found and linked; out of the pit (past LEASH + LOST_MARGIN, or under the floor) - back to the middle at once.</li>
 * </ul>
 * An egg's Warden (not bound): its middle is where it first stood; no coils (vulnerable from the start), no waves, no cracks; the
 * rest as above. Server only.
 */
public final class WardenFightSC {

    /** The bookkeeping's period (§13: the fight's count every 20 ticks). */
    public static final int BOOK_TICKS = 20;
    /** The first wave this long after the summons; the call's telegraph before a wave; a lost coil found missing this many times is made again. */
    public static final int FIRST_WAVE = 300, SUMMON_TELL = 40, COIL_LOST_SCANS = 3;
    /** «Пробой»'s first highlight / crack / swarm / press / silencing pulse after the phase began (ticks). TODO(balance) */
    public static final int P2_HIGHLIGHT = 60, P2_CRACK = 100, P2_SWARM = 200, P2_PRESS = 160, P2_SILENCE = 300;
    /** Off the leash this far past LEASH, or under the floor - back to the middle at once (a canyon's fall, a pushed Warden). */
    public static final int LOST_MARGIN = 14;
    /** The shells' mouths: this far from the middle along the four lines, spread across the canyon; their patrol id. */
    public static final int MOUTH_R = ArenaLayoutSC.PIT_R - 2, MOUTH_SPREAD = 2;
    /**
     * ПМ-1: a wave's Shell carries this flag in its entity data (Forge's, saved with the mob) - MobDropsSC rolls no trophy for it
     * (and keeps the killer's bad luck as it was); the firmware, the helium and the experience stay.
     */
    public static final String WAVE_TAG = "scWardenWave";
    /** «sc.warden.shielded» to a shooter at most once in this many ticks (3 s); the spark's sound at most every SPARK_SOUND. */
    public static final int SHIELD_TELL_TICKS = 60, SPARK_SOUND = 6;
    /** The press's stages (EntityBreakdownWardenSC.DW_PRESS). */
    public static final int STAGE_NONE = 0, STAGE_HUM = 1, STAGE_PRESS = 2;
    /** A crack's place: off the columns, this far from a pedestal, the console, the middle (blocks). */
    public static final int CRACK_CLEAR = 2, CRACK_TRIES = 8;
    /** The BOSS_FX reach. */
    public static final double FX_RANGE = 96D;
    /**
     * FinaleNetSC.BOSS_FX kinds - the same fixed numbers as mob.client.BossClientSC.FX_* (that class is the client's: the server's
     * code never names it): the core reset's blast, the silencing pulse's wave, a coil dark / broken / lit.
     */
    public static final int FX_CORE_RESET = 1, FX_SILENCE = 2, FX_COIL_DARK = 3, FX_COIL_BROKEN = 4, FX_COIL_LIT = 5;
    private static final int[] ALL_PIECES = {0, 1, 2, 3};
    private static final int[][] MOUTHS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final EntityBreakdownWardenSC boss;

    /** The phase (WardenRulesSC.PHASE_*), one way. */
    public int phase = WardenRulesSC.PHASE_HOLD;
    /** The arena's middle (its floor's centre block + 0.5, standing height), its floor; known yet. */
    public double cx, cy, cz;
    public int floorY;
    private boolean centred;
    /** «Удержание»: the window's ticks left; the next wave; the call's telegraph left. Public for the world test. */
    public int windowLeft, waveIn = FIRST_WAVE, summonLeft;
    /** «Пробой» (and after): the highlight (next, left), the whole set's suppression left, the next crack / swarm / press / pulse. */
    public int highlightIn, highlightLeft, setLeft, crackIn, swarmIn, pressIn, silenceIn;
    /** The highlighted piece now (-1 none, 0..3, EntityBreakdownWardenSC.SET_ALL). */
    public int highlight = -1;
    /** The press: its stage, its ticks left; the silencing pulse's hum under way. */
    public int pressStage = STAGE_NONE, pressLeft;
    public boolean silenceHum;
    /** «Перегрузка»: ticks into it, the 60 s clock, the blast's wind-up left. Public for the world test («перевод часов»). */
    public int phaseTicks, clockTicks, resetLeft;
    /** The players the health is scaled for (the most seen this fight). */
    public int scale = 1;
    /** The coils by index (server references, found by scanning) and how many scans each was missing. */
    private final EntityContainmentCoilSC[] coils = new EntityContainmentCoilSC[WardenRulesSC.COILS];
    private final int[] missing = new int[WardenRulesSC.COILS];
    /** «sc.warden.shielded» told: player -> world time. Not saved. */
    private final Map<UUID, Long> told = new HashMap<UUID, Long>();
    private long lastSpark;

    WardenFightSC(EntityBreakdownWardenSC boss) {
        this.boss = boss;
    }

    private World w() {
        return boss.worldObj;
    }

    private long now() {
        return boss.worldObj.getTotalWorldTime();
    }

    // ------------------------------------------------------------------ where

    /**
     * Server: the middle known - a bound Warden's from its arena's record (never saved: the record is the truth), an egg's where it
     * stood first (saved in scWarden). @return known
     */
    boolean centre() {
        if (centred) {
            return true;
        }
        if (boss.bound()) {
            ArenaDataSC.Arena a = boss.arena();
            if (a == null) {
                return false;
            }
            setCentre(a.x + 0.5D, a.floorY + 1, a.z + 0.5D, a.floorY);
        } else {
            setCentre(boss.posX, Math.floor(boss.posY), boss.posZ, (int) Math.floor(boss.posY) - 1);
        }
        return true;
    }

    /** Server: the fight knows its middle (the AI asks before it goes by it - a reloaded Warden's first tick runs the AI first). */
    public boolean ready() {
        return centre();
    }

    private void setCentre(double x, double y, double z, int floor) {
        cx = x;
        cy = y;
        cz = z;
        floorY = floor;
        centred = true;
    }

    /** Horizontal distance² of `e` from the middle. */
    public double flatDistSq(Entity e) {
        double dx = e.posX - cx, dz = e.posZ - cz;
        return dx * dx + dz * dz;
    }

    /** The real players within `r` of the middle: alive, not in creative, not a FakePlayer (the fight's players). */
    public List<EntityPlayerMP> players(double r) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        double r2 = r * r;
        for (Object o : w().playerEntities) {
            if (!(o instanceof EntityPlayerMP) || o instanceof FakePlayer) {
                continue;
            }
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.isEntityAlive() && !p.capabilities.isCreativeMode && p.getDistanceSq(cx, cy, cz) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    /** A line to the arena's players (every real one within SCALE_RADIUS, creative too - they watch). */
    void tell(String key, Object... args) {
        double r2 = WardenRulesSC.SCALE_RADIUS * WardenRulesSC.SCALE_RADIUS;
        for (Object o : w().playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && ((EntityPlayer) o).getDistanceSq(cx, cy, cz) <= r2) {
                ((EntityPlayer) o).addChatComponentMessage(new ChatComponentTranslation(key, args));
            }
        }
    }

    /** A BOSS_FX of `kind` at (x, y, z) to the players within FX_RANGE. */
    void fx(int kind, double x, double y, double z) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("k", kind);
        t.setDouble("x", x);
        t.setDouble("y", y);
        t.setDouble("z", z);
        FinaleNetSC.sendAround(w(), x, y, z, FX_RANGE, FinaleNetSC.BOSS_FX, t);
    }

    // ------------------------------------------------------------------ the tick

    /** Server, every tick of a living Warden (after vanilla's and the armour's). */
    void tick() {
        if (!centre()) {
            return;
        }
        if ((boss.ticksExisted + boss.getEntityId()) % BOOK_TICKS == 0) {
            bookkeeping();
        }
        int byHealth = WardenRulesSC.phaseOf(boss.getHealth(), boss.getMaxHealth());
        while (phase < byHealth && phase < WardenRulesSC.PHASE_OVERLOAD) {
            enterPhase(phase + 1);
        }
        if (phase == WardenRulesSC.PHASE_HOLD) {
            tickHold();
        } else {
            tickLater();
        }
        if (pressStage == STAGE_PRESS) {
            tickPress();
        }
        telegraph();
        sync();
    }

    /** «Удержание»: the window, the waves. */
    private void tickHold() {
        if (!boss.bound()) {
            return;                                             // an egg's: no coils, no waves
        }
        if (windowLeft > 0) {
            if (--windowLeft == 0) {
                closeWindow();
            }
        } else if (WardenRulesSC.windowOpens(litCount())) {
            openWindow();
        }
        if (summonLeft > 0) {
            summonLeft--;
        }
        if (--waveIn == SUMMON_TELL) {
            summonLeft = SUMMON_TELL;                           // ACT_SUMMON: the call before the wave
            SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "warden.hold", 2F, 0.7F);
        }
        if (waveIn <= 0) {
            waveIn = WardenRulesSC.WAVE_EVERY;
            wave();
        }
    }

    /** «Пробой» and «Перегрузка»: the highlight (Пробой only), the cracks, the swarms, the heat and the clock (Перегрузка). */
    private void tickLater() {
        if (pressIn > 0) {
            pressIn--;
        }
        if (silenceIn > 0) {
            silenceIn--;
        }
        if (phase == WardenRulesSC.PHASE_BREAKDOWN) {
            if (highlightLeft > 0 && --highlightLeft == 0 && highlight != EntityBreakdownWardenSC.SET_ALL) {
                highlight = -1;
            }
            if (setLeft > 0 && --setLeft == 0 && highlight == EntityBreakdownWardenSC.SET_ALL) {
                highlight = -1;
            }
            if (--highlightIn <= 0) {
                highlightIn = WardenRulesSC.HIGHLIGHT_EVERY;
                if (setLeft <= 0) {
                    pickHighlight();
                }
            }
        }
        if (--crackIn <= 0) {
            crackIn = WardenRulesSC.CRACK_EVERY;
            crack();
        }
        if (--swarmIn <= 0) {
            swarmIn = WardenRulesSC.SWARM_EVERY;
            swarm();
        }
        if (phase == WardenRulesSC.PHASE_OVERLOAD) {
            boolean was = overheated();
            phaseTicks++;
            if (!was && overheated()) {                         // the heat is full: 4 s with no protection
                SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "emp.overheat", 2F, 0.5F);
                w().playSoundEffect(boss.posX, boss.posY + 2D, boss.posZ, "random.fizz", 1.5F, 0.5F);
            }
            clockTicks++;
            if (resetLeft > 0) {
                if (--resetLeft == 0) {
                    coreReset();
                    clockTicks = 0;
                }
            } else if (WardenRulesSC.coreResetDue(clockTicks)) {
                resetLeft = WardenRulesSC.CORE_RESET_WARN;      // ACT_CORE_RESET, FLAG_RESET_ARMED: the last seconds
                tell("sc.warden.reset.warn");
                SoundsSC.play(w(), cx, cy + 4D, cz, "warden.siren", 4F, 0.8F);
            }
        }
    }

    /** «Перегрузка»'s heat: no protection now (WardenRulesSC.overheated of the ticks into it). */
    public boolean overheated() {
        return phase == WardenRulesSC.PHASE_OVERLOAD && WardenRulesSC.overheated(phaseTicks);
    }

    /** The coils hold dark now (the window, or «Удержание» over) - none lights again on its own. */
    public boolean coilsHeld() {
        return windowLeft > 0 || phase != WardenRulesSC.PHASE_HOLD;
    }

    /** ПС-52, server: the coils' shield takes every blow now (bound, «Удержание», no window, some coil lit or not found). */
    public boolean shieldedByCoils() {
        return boss.bound() && phase == WardenRulesSC.PHASE_HOLD && windowLeft <= 0 && litCount() > 0;
    }

    // ------------------------------------------------------------------ phases

    private void enterPhase(int p) {
        phase = p;
        MobArmorSC a = boss.mobArmor();
        if (p == WardenRulesSC.PHASE_BREAKDOWN) {
            windowLeft = 0;
            a.emergency = false;                                // the window is over with «Удержание»
            for (EntityContainmentCoilSC c : coils) {
                if (live(c)) {
                    c.spend();                                  // ПС-53: dark for good (an unloaded one: when found)
                }
            }
            summonLeft = 0;
            highlightIn = P2_HIGHLIGHT;
            crackIn = P2_CRACK;
            swarmIn = P2_SWARM;
            pressIn = P2_PRESS;
            silenceIn = P2_SILENCE;
            tell("sc.warden.phase2");
            SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "warden.blast", 3F, 0.7F);
        } else if (p == WardenRulesSC.PHASE_OVERLOAD) {
            highlight = -1;
            highlightLeft = setLeft = 0;
            a.helium = false;                                   // ПС-54: the helium boiled off - no shield
            a.emergency = false;
            phaseTicks = clockTicks = resetLeft = 0;
            tell("sc.warden.phase3");
            SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "warden.blast", 3F, 0.5F);
        }
        a.sync();
    }

    private void openWindow() {
        windowLeft = WardenRulesSC.WINDOW_TICKS;
        MobArmorSC a = boss.mobArmor();
        a.emergency = true;                                     // iron points, no shield
        a.sync();
        tell("sc.warden.window");
        SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "power.off", 3F, 0.4F);
        if (w() instanceof WorldServer) {
            ((WorldServer) w()).func_147487_a("largeexplode", boss.posX, boss.posY + 2D, boss.posZ, 3, 0.6D, 0.6D, 0.6D, 0D);
        }
    }

    private void closeWindow() {
        MobArmorSC a = boss.mobArmor();
        a.emergency = false;
        a.sync();
        for (EntityContainmentCoilSC c : coils) {
            if (live(c)) {
                c.relight();                                    // every coil lit again, a broken one with its full reserve
            }
        }
        SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "warden.hold", 3F, 0.6F);
    }

    // ------------------------------------------------------------------ the coils

    /** Coils lit now - a coil not found (yet, or its chunk unloaded) counts as lit: an unloaded pedestal never opens the window. */
    public int litCount() {
        int n = 0;
        for (EntityContainmentCoilSC c : coils) {
            if (!live(c) || c.lit()) {
                n++;
            }
        }
        return n;
    }

    /** `c` is in the world now (not dead, not gone with its chunk: the world's id map still holds this very entity). */
    private boolean live(Entity c) {
        return c != null && !c.isDead && w().getEntityByID(c.getEntityId()) == c;
    }

    /** The lit coils' bits (DW_COILS; only the ones found - the beams). */
    private int litBits() {
        int b = 0;
        for (int k = 0; k < coils.length; k++) {
            EntityContainmentCoilSC c = coils[k];
            if (live(c) && c.lit()) {
                b |= 1 << k;
            }
        }
        return b;
    }

    /** Server: coil `k` (null: not found). */
    public EntityContainmentCoilSC coil(int k) {
        return k >= 0 && k < coils.length ? coils[k] : null;
    }

    /** Server (the summons): the four coils on their pedestals, bound and linked. */
    void placeCoils() {
        if (!boss.bound() || !centre()) {
            return;
        }
        for (int k = 0; k < WardenRulesSC.COILS; k++) {
            placeCoil(k);
        }
    }

    private void placeCoil(int k) {
        int[] s = ArenaLayoutSC.coilSpot(k);
        double x = Math.floor(cx) + s[0] + 0.5D, z = Math.floor(cz) + s[1] + 0.5D;
        if (!w().blockExists((int) Math.floor(x), floorY, (int) Math.floor(z)) || !(w() instanceof WorldServer)) {
            return;                                             // never loads a chunk
        }
        EntityContainmentCoilSC c = EntityContainmentCoilSC.create((WorldServer) w(), x, floorY + 2, z);
        c.bindTo(boss.arenaRi(), boss.arenaRj(), boss.fightId(), k);
        if (w().spawnEntityInWorld(c)) {
            coils[k] = c;
            missing[k] = 0;
            c.link(boss);
            if (phase != WardenRulesSC.PHASE_HOLD) {
                c.spend();
            }
        }
    }

    /** Bookkeeping: the coils of this fight round the arena found and linked; one lost long enough - put back («Удержание»). */
    private void findCoils() {
        boolean all = true;
        for (EntityContainmentCoilSC c : coils) {
            if (!live(c)) {
                all = false;
            }
        }
        if (all) {
            for (EntityContainmentCoilSC c : coils) {
                if (c.boss() != boss) {
                    c.link(boss);
                }
            }
            return;
        }
        double r = ArenaLayoutSC.RIM_R + 4;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - r, floorY - 4, cz - r, cx + r, floorY + 12, cz + r);
        boolean[] seen = new boolean[coils.length];
        for (Object o : w().getEntitiesWithinAABB(EntityContainmentCoilSC.class, box)) {
            EntityContainmentCoilSC c = (EntityContainmentCoilSC) o;
            if (c.isDead || !c.ofFight(boss.arenaRi(), boss.arenaRj(), boss.fightId())) {
                continue;
            }
            int k = c.index();
            if (seen[k] && coils[k] != c) {
                c.setDead();                                    // never two on one pedestal
                continue;
            }
            seen[k] = true;
            coils[k] = c;
            missing[k] = 0;
            c.link(boss);
            if (phase != WardenRulesSC.PHASE_HOLD) {
                c.spend();
            }
        }
        for (int k = 0; k < coils.length; k++) {
            if (seen[k]) {
                continue;
            }
            coils[k] = null;
            if (phase == WardenRulesSC.PHASE_HOLD && ++missing[k] >= COIL_LOST_SCANS) {
                placeCoil(k);                                   // taken away by a command: a new one (its chunk loaded)
            }
        }
    }

    /** Server (the Warden died): its coils go with it - a burst of smoke and sparks. */
    void dropCoils() {
        for (EntityContainmentCoilSC c : coils) {
            if (live(c)) {
                fx(FX_COIL_BROKEN, c.posX, c.posY + EntityContainmentCoilSC.BEAM_Y, c.posZ);
                c.setDead();
            }
        }
    }

    // ------------------------------------------------------------------ the blows the coils' shield refuses (ПС-52)

    /** Server: a blow on the coils' shield - sparks where it struck, «sc.warden.shielded» to the shooter once in 3 s. */
    void shieldedHit(DamageSource src) {
        Entity hitter = src.getEntity();
        if (hitter == null && src.getSourceOfDamage() instanceof EntityArrow) {
            hitter = ((EntityArrow) src.getSourceOfDamage()).shootingEntity;
        }
        long now = now();
        Entity at = src.getSourceOfDamage() != null ? src.getSourceOfDamage() : hitter;
        double x = boss.posX, y = boss.posY + boss.height * 0.6D, z = boss.posZ;
        if (at != null) {                                       // the side it came from
            double dx = at.posX - x, dz = at.posZ - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.01D) {
                x += dx / d * (boss.width * 0.8D);
                z += dz / d * (boss.width * 0.8D);
            }
        }
        if (w() instanceof WorldServer) {
            ((WorldServer) w()).func_147487_a("magicCrit", x, y, z, 10, 0.25D, 0.4D, 0.25D, 0.15D);
        }
        if (now - lastSpark >= SPARK_SOUND) {
            lastSpark = now;
            SoundsSC.play(w(), x, y, z, "field.zap", 0.8F, 1.4F + boss.getRNG().nextFloat() * 0.2F);
        }
        if (hitter instanceof EntityPlayerMP && !(hitter instanceof FakePlayer)) {
            UUID id = ((EntityPlayer) hitter).getUniqueID();
            Long last = told.get(id);
            if (last == null || now - last.longValue() >= SHIELD_TELL_TICKS) {
                told.put(id, Long.valueOf(now));
                ((EntityPlayer) hitter).addChatComponentMessage(new ChatComponentTranslation("sc.warden.shielded"));
            }
        }
        if (told.size() > 16) {
            for (Iterator<Map.Entry<UUID, Long>> it = told.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue().longValue() >= SHIELD_TELL_TICKS) {
                    it.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ the waves («Удержание»)

    /** ПС-52: a wave - waveSize(players), at most WAVE_MAX of the arena's Shells alive, from the canyons' mouths. */
    private void wave() {
        if (!(w() instanceof WorldServer) || !SubstrateSC.is(w())) {
            return;
        }
        WorldServer ws = (WorldServer) w();
        int alive = 0;
        double r = ArenaLayoutSC.RIM_R + 8;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - r, floorY - 4, cz - r, cx + r, floorY + 16, cz + r);
        for (Object o : ws.getEntitiesWithinAABB(EntityHollowShellSC.class, box)) {
            if (((Entity) o).isEntityAlive()) {
                alive++;
            }
        }
        int want = Math.min(WardenRulesSC.waveSize(Math.max(1, players(WardenRulesSC.SCALE_RADIUS).size())), WardenRulesSC.WAVE_MAX - alive);
        if (want <= 0) {
            return;
        }
        long patrol = boss.getRNG().nextLong() | 1L;
        int first = boss.getRNG().nextInt(MOUTHS.length);
        int made = 0;
        for (int i = 0; i < want * 2 && made < want; i++) {
            int[] m = MOUTHS[(first + i) % MOUTHS.length];
            int across = boss.getRNG().nextInt(MOUTH_SPREAD * 2 + 1) - MOUTH_SPREAD;
            int x = (int) Math.floor(cx) + m[0] * MOUTH_R + (m[0] == 0 ? across : 0);
            int z = (int) Math.floor(cz) + m[1] * MOUTH_R + (m[1] == 0 ? across : 0);
            if (!ws.blockExists(x, floorY, z)) {
                continue;
            }
            EntityLiving e = SpawnDirectorSC.spawnAt(ws, MobEntitiesSC.ID_HOLLOW_SHELL, x + 0.5D, floorY + 1, z + 0.5D, false, false);
            if (!(e instanceof EntityHollowShellSC)) {
                continue;
            }
            made++;
            EntityHollowShellSC shell = (EntityHollowShellSC) e;
            shell.getEntityData().setBoolean(WAVE_TAG, true);        // ПМ-1: no trophy off a wave's Shell
            shell.setPatrol(patrol, (int) Math.floor(cx), floorY + 1, (int) Math.floor(cz));
            huntPlayersOnly(shell);
            EntityPlayer near = ws.getClosestPlayerToEntity(shell, WardenRulesSC.SCALE_RADIUS);
            if (near != null && !near.capabilities.disableDamage) {
                shell.setAttackTarget(near);
            }
            ws.func_147487_a("portal", x + 0.5D, floorY + 2D, z + 0.5D, 30, 0.4D, 0.8D, 0.4D, 0.4D);
            ws.playSoundEffect(x + 0.5D, floorY + 1.5D, z + 0.5D, "mob.endermen.portal", 1F, 0.6F);
        }
    }

    /**
     * A Shell in the arena hunts players only: its «anything living» target task (EntityHollowShellSC.NOT_OURS) would take the coils
     * for prey (they are living, and no hostile of the Substrate) - swapped for vanilla's nearest-player one (HuntPlayers). The AI
     * isn't saved: a reloaded Shell is swapped again by the bookkeeping (keepShellsOffCoils). Once a Shell.
     */
    static void huntPlayersOnly(EntityHollowShellSC shell) {
        EntityAITasks t = shell.targetTasks;
        List<EntityAINearestAttackableTarget> drop = new ArrayList<EntityAINearestAttackableTarget>();
        int prio = 2;
        for (Object o : t.taskEntries) {
            EntityAITasks.EntityAITaskEntry en = (EntityAITasks.EntityAITaskEntry) o;
            if (en.action instanceof HuntPlayers) {
                return;                                         // swapped already
            }
            if (en.action instanceof EntityAINearestAttackableTarget) {
                drop.add((EntityAINearestAttackableTarget) en.action);
                prio = en.priority;
            }
        }
        for (EntityAINearestAttackableTarget a : drop) {
            t.removeTask(a);
        }
        t.addTask(prio, new HuntPlayers(shell));
    }

    /** The arena's Shell's target task: the nearest player (a marker class - huntPlayersOnly knows its own). */
    static final class HuntPlayers extends EntityAINearestAttackableTarget {
        HuntPlayers(EntityHollowShellSC shell) {
            super(shell, EntityPlayer.class, 10, true);
        }
    }

    /** Bookkeeping («Удержание»): every Shell in the arena hunts players only, and lets a coil go if it took one for prey. */
    private void keepShellsOffCoils() {
        double r = ArenaLayoutSC.RIM_R + 8;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - r, floorY - 4, cz - r, cx + r, floorY + 16, cz + r);
        for (Object o : w().getEntitiesWithinAABB(EntityHollowShellSC.class, box)) {
            EntityHollowShellSC shell = (EntityHollowShellSC) o;
            if (shell.isDead) {
                continue;
            }
            huntPlayersOnly(shell);
            if (shell.getAttackTarget() instanceof EntityContainmentCoilSC) {
                shell.setAttackTarget(null);
            }
        }
    }

    // ------------------------------------------------------------------ «Пробой»: the highlight, the cracks, the swarms

    /** ПС-53: one piece glows - one neither suppressed nor immune if there is one. */
    private void pickHighlight() {
        MobArmorSC a = boss.mobArmor();
        int[] free = new int[4];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            if (MobArmorSC.modPiece(boss, i) != null && !a.suppressed(i) && !a.immune(i)) {
                free[n++] = i;
            }
        }
        highlight = n > 0 ? free[boss.getRNG().nextInt(n)] : boss.getRNG().nextInt(4);
        highlightLeft = WardenRulesSC.HIGHLIGHT_TICKS;
        SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "emp.charge", 1.5F, 0.5F);
    }

    /**
     * ПС-53, server (EntityBreakdownWardenSC.empHit after the armour's own): a Point into the highlighted piece - its suppression
     * HIGHLIGHT_MUL times longer and HIGHLIGHT_RESERVE off the reserve; the chestplate - the whole set suppressed SET_TICKS (the
     * chestplate itself at least its own x1.5). The highlight is spent on it.
     */
    void onEmpHit(EmpMode mode, EmpSC.HitResult r) {
        int h = highlight;
        if (phase != WardenRulesSC.PHASE_BREAKDOWN || mode != EmpMode.POINT || r == null || h < 0 || h > 3 || highlightLeft <= 0
                || r.ticks[h] <= 0) {
            return;
        }
        MobArmorSC a = boss.mobArmor();
        long now = now();
        int immunity = EmpMathSC.immunityTicks(ConfigSC.empImmunitySeconds);
        int own = WardenRulesSC.highlightTicks(r.ticks[h]);
        suppress(a, h, own, now, immunity);
        r.ticks[h] = own;
        if (h == 1) {                                           // «Сердце комплекта»: the whole set
            for (int i = 0; i < 4; i++) {
                if (i != 1 && MobArmorSC.modPiece(boss, i) != null) {
                    suppress(a, i, WardenRulesSC.SET_TICKS, now, immunity);
                    r.ticks[i] = Math.max(r.ticks[i], WardenRulesSC.SET_TICKS);
                }
            }
            highlight = EntityBreakdownWardenSC.SET_ALL;
            setLeft = Math.max(WardenRulesSC.SET_TICKS, own);
        } else {
            highlight = -1;
        }
        highlightLeft = 0;
        ArmorSuit s = a.suit();
        if (s != null) {
            a.drainEu((int) Math.min(Integer.MAX_VALUE, (long) (WardenRulesSC.HIGHLIGHT_RESERVE * s.euPerDamage)));
        }
        a.sync();
        SoundsSC.play(w(), boss.posX, boss.posY + 2D, boss.posZ, "emp.suppress", 2F, 0.6F);
        if (w() instanceof WorldServer) {
            ((WorldServer) w()).func_147487_a("fireworksSpark", boss.posX, boss.posY + boss.height * 0.6D, boss.posZ, 24, 0.5D, 0.8D, 0.5D, 0.15D);
        }
    }

    private static void suppress(MobArmorSC a, int piece, int ticks, long now, int immunity) {
        long until = Math.max(a.suppressedUntil[piece], now + ticks);
        a.suppressedUntil[piece] = until;
        a.immuneUntil[piece] = Math.max(a.immuneUntil[piece], until + immunity);
    }

    /** ПС-53: a Breakdown crack opens in a floor plate (at most CRACKS_MAX; the arena's record keeps it for A to close). */
    private void crack() {
        ArenaDataSC.Arena a = boss.bound() ? boss.arena() : null;
        ArenaDataSC d = a == null ? null : ArenaDataSC.get(w());
        if (a == null || d == null || SubstrateWildBlocksSC.vent == null || ArenaBlocksSC.arena == null
                || a.cracks().size() >= WardenRulesSC.CRACKS_MAX) {
            return;
        }
        for (int t = 0; t < CRACK_TRIES; t++) {
            double ang = boss.getRNG().nextDouble() * Math.PI * 2D, rr = 4D + boss.getRNG().nextDouble() * (ArenaLayoutSC.PIT_R - 6);
            int dx = (int) Math.round(Math.cos(ang) * rr), dz = (int) Math.round(Math.sin(ang) * rr);
            if (!crackSpot(dx, dz)) {
                continue;
            }
            int x = a.x + dx, z = a.z + dz, y = a.floorY;
            if (!w().blockExists(x, y, z) || !ArenaBlocksSC.isFloor(w().getBlock(x, y, z), w().getBlockMetadata(x, y, z))
                    || !w().isAirBlock(x, y + 1, z)) {
                continue;
            }
            w().setBlock(x, y, z, SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.ARC_CRACK, 3);
            a.addCrack(x, y, z);
            d.markDirty();
            SoundsSC.play(w(), x + 0.5D, y + 1D, z + 0.5D, "field.zap", 2F, 0.5F);
            w().playSoundEffect(x + 0.5D, y + 1D, z + 0.5D, "random.explode", 0.8F, 1.6F);
            if (w() instanceof WorldServer) {
                ((WorldServer) w()).func_147487_a("fireworksSpark", x + 0.5D, y + 1.1D, z + 0.5D, 20, 0.6D, 0.1D, 0.6D, 0.12D);
            }
            return;
        }
    }

    /** A crack's offset: off the columns, the pedestals, the console and the very middle (pure). */
    public static boolean crackSpot(int dx, int dz) {
        if (!ArenaLayoutSC.inPit(dx, dz) || dx * dx + dz * dz <= CRACK_CLEAR * CRACK_CLEAR) {
            return false;
        }
        for (int ex = -CRACK_CLEAR; ex <= CRACK_CLEAR; ex++) {
            for (int ez = -CRACK_CLEAR; ez <= CRACK_CLEAR; ez++) {
                if (ArenaLayoutSC.inColumn(dx + ex, dz + ez)) {
                    return false;
                }
            }
        }
        for (int k = 0; k < WardenRulesSC.COILS; k++) {
            int[] s = ArenaLayoutSC.coilSpot(k);
            if (Math.abs(dx - s[0]) <= CRACK_CLEAR && Math.abs(dz - s[1]) <= CRACK_CLEAR) {
                return false;
            }
        }
        return Math.abs(dx - ArenaLayoutSC.CONSOLE_DX) > CRACK_CLEAR + 1 || Math.abs(dz - ArenaLayoutSC.CONSOLE_DZ) > CRACK_CLEAR + 1;
    }

    /** ПС-53: a nanite swarm at the pit's edge (the Substrate only; EntityNaniteSC's caps: 12 a swarm and a 32 x 32 area). */
    private void swarm() {
        if (!SubstrateSC.is(w())) {
            return;
        }
        double ang = boss.getRNG().nextDouble() * Math.PI * 2D;
        double r = boss.bound() ? ArenaLayoutSC.PIT_R - 4 : 10D;
        double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
        if (!w().blockExists((int) Math.floor(x), floorY, (int) Math.floor(z))) {
            return;
        }
        int n = EntityNaniteSC.SWARM_MIN + boss.getRNG().nextInt(EntityNaniteSC.SWARM_MAX - EntityNaniteSC.SWARM_MIN + 1);
        if (EntityNaniteSC.spawnSwarm(w(), x, floorY + 2.5D, z, n, boss.getRNG()) > 0) {
            w().playSoundEffect(x, floorY + 2D, z, "mob.silverfish.say", 1F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ the press (as the Sentinel's, ПС-53)

    /** Server (EntityAIWardenPressSC after the hum): PRESS_TICKS of the press. */
    public void startPress() {
        pressStage = STAGE_PRESS;
        pressLeft = WardenRulesSC.PRESS_TICKS;
        SoundsSC.play(w(), boss.posX, boss.posY + 1D, boss.posZ, "warden.hold", 2F, 0.4F);
        w().playSoundEffect(boss.posX, boss.posY, boss.posZ, "mob.wither.shoot", 1.4F, 0.4F);
        w().playSoundEffect(boss.posX, boss.posY, boss.posZ, "random.anvil_land", 1F, 0.4F);
    }

    /** Every tick of the press: the players within PRESS_RADIUS of the Warden pinned down (Slowness IV, no jump). */
    private void tickPress() {
        if (--pressLeft <= 0 || !boss.isEntityAlive()) {
            pressStage = STAGE_NONE;
            pressLeft = 0;
            return;
        }
        double r2 = WardenRulesSC.PRESS_RADIUS * WardenRulesSC.PRESS_RADIUS;
        boolean slow = (boss.ticksExisted + boss.getEntityId()) % EntitySentinelSC.SLOW_EVERY == 0;
        for (Object o : w().playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.isCreativeMode || p instanceof FakePlayer || p.getDistanceSqToEntity(boss) > r2) {
                continue;
            }
            if (slow) {
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, EntitySentinelSC.SLOW_TICKS, EntitySentinelSC.SLOW_AMPLIFIER));
            }
            if (!p.onGround) {
                p.motionY = Math.min(p.motionY, -EntitySentinelSC.PRESS_PULL);
                p.velocityChanged = true;
            }
        }
    }

    // ------------------------------------------------------------------ «Импульс глушения» (ПС-53)

    /**
     * Server (EntityAIWardenSilenceSC after the hum): a ПЭ-1 Pulse (the ПЭ-1М's numbers) on every real player within SILENCE_RADIUS
     * the Warden can see - one pass over playerEntities: cover by the line of sight saves; the rules (EmpMobGunSC.refusal: the config,
     * creative), the field's EMP screen (EmpRulesSC.screenAbsorbs), «Страж»'s reflection back onto the Warden, then the player's own
     * path (EmpSC.applyHit: durations by his suit, the 20 s immunity, heat). Not the ПЭ-1's shooterSuppressed rule: it is the
     * Warden's own discharge, not an aimed shot. BOSS_FX FX_SILENCE. @return players reached
     */
    public int silence() {
        World w = w();
        fx(FX_SILENCE, boss.posX, boss.posY, boss.posZ);   // the wave runs out along the ground from its feet
        SoundsSC.play(w, boss.posX, boss.posY, boss.posZ, EmpSC.SND_PULSE, 3F, 0.6F);
        if (!EmpMobGunSC.allowed()) {
            return 0;
        }
        int n = 0;
        double r2 = WardenRulesSC.SILENCE_RADIUS * WardenRulesSC.SILENCE_RADIUS;
        for (Object o : new ArrayList<Object>(w.playerEntities)) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p instanceof FakePlayer || p.getDistanceSqToEntity(boss) > r2) {
                continue;
            }
            if (pulse(p)) {
                n++;
            }
        }
        return n;
    }

    /** One player's share of the silencing pulse (public for the world test: a FakePlayer target). @return it reached him */
    public boolean pulse(EntityPlayer p) {
        World w = w();
        if (!EmpShotSC.inSight(w, boss, p, WardenRulesSC.SILENCE_RADIUS) || EmpMobGunSC.refusal(boss, p, EmpMode.PULSE) != EmpNetSC.OK) {
            return false;                                       // behind cover - nothing at all
        }
        double[] border = EmpRulesSC.screenAbsorbs((Entity) boss, p, EmpMathSC.screenCost(EmpMode.PULSE));
        if (border != null) {
            beam(EmpNetSC.FX_PULSE, p.getEntityId(), border[0], border[1], border[2]);
            beam(EmpNetSC.FX_ABSORBED, p.getEntityId(), border[0], border[1], border[2]);
            return false;
        }
        Vec3 at = EmpShotSC.centre(p);
        beam(EmpNetSC.FX_PULSE, p.getEntityId(), at.xCoord, at.yCoord, at.zCoord);
        if (EmpArmorSC.guardReflects(p, boss.getRNG())) {      // «Страж»: back onto the Warden
            beam(EmpNetSC.FX_REFLECTED, p.getEntityId(), boss.posX, boss.boundingBox.minY + boss.height / 2, boss.posZ);
            boss.empHit(p, EmpMode.PULSE, true, ALL_PIECES.clone());
            return true;
        }
        EmpSC.HitResult r = EmpSC.applyHit(p, EmpMode.PULSE, true, ALL_PIECES.clone());
        if (r.anySuppressed()) {
            SoundsSC.play(w, p.posX, p.posY, p.posZ, EmpSC.SND_SUPPRESS, 0.8F, 1F);
            beam(EmpNetSC.FX_SUPPRESSED, p.getEntityId(), at.xCoord, at.yCoord, at.zCoord);
        }
        return true;
    }

    private void beam(int kind, int targetId, double x, double y, double z) {
        EmpNetSC.sendFx(w(), new EmpNetSC.FxMessage(kind, boss.getEntityId(), targetId, x, y, z), boss.posX, boss.posY, boss.posZ);
    }

    // ------------------------------------------------------------------ «сброс ядра» (ПС-54)

    /**
     * Server: «сброс ядра» - CORE_RESET_DAMAGE of «Пробой» (BreakdownDamageSC.of(the Warden)) to every real player within
     * CORE_RESET_RADIUS of the middle (the pit, the rim, the funnel's ledges and the canyons' mouths: a shooter from the edge isn't
     * safe either) with no solid block between him and its chest (a column, his own wall, the rock saves); no block broken; BOSS_FX
     * FX_CORE_RESET. One pass over playerEntities. @return players hit
     */
    public int coreReset() {
        fx(FX_CORE_RESET, cx, cy + 1D, cz);
        SoundsSC.play(w(), cx, cy + 2D, cz, "warden.blast", 6F, 0.5F);
        w().playSoundEffect(cx, cy + 2D, cz, "random.explode", 6F, 0.5F);
        int n = 0;
        for (Object o : new ArrayList<Object>(w().playerEntities)) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.isEntityAlive() && !(p instanceof FakePlayer) && coreResetOn(p)) {
                n++;
            }
        }
        return n;
    }

    /** One target of the blast (public for the world test: a FakePlayer behind a column / in the open). @return it was hit */
    public boolean coreResetOn(EntityLivingBase t) {
        double r = WardenRulesSC.CORE_RESET_RADIUS;
        if (t == null || !t.isEntityAlive() || flatDistSq(t) > r * r || !blastReaches(t)) {
            return false;
        }
        return t.attackEntityFrom(BreakdownDamageSC.of(boss), WardenRulesSC.CORE_RESET_DAMAGE);
    }

    /** No solid block between `t` (its middle, or its head) and the Warden's chest. */
    public boolean blastReaches(Entity t) {
        World w = w();
        Vec3 chest = Vec3.createVectorHelper(boss.posX, boss.posY + boss.height * 0.6D, boss.posZ);
        Vec3 mid = EmpShotSC.centre(t);
        if (w.func_147447_a(Vec3.createVectorHelper(chest.xCoord, chest.yCoord, chest.zCoord), mid, false, true, false) == null) {
            return true;
        }
        Vec3 head = Vec3.createVectorHelper(mid.xCoord, t.boundingBox.maxY - 0.2D, mid.zCoord);
        return w.func_147447_a(chest, head, false, true, false) == null;
    }

    // ------------------------------------------------------------------ the bookkeeping (ПС-56)

    private void bookkeeping() {
        List<EntityPlayerMP> near = players(WardenRulesSC.SCALE_RADIUS);
        int n = fighters(near);
        int most = Math.max(scale, n);
        if (boss.bound()) {
            ArenaDataSC d = ArenaDataSC.get(w());
            ArenaDataSC.Arena a = d == null ? null : boss.arena();
            if (a != null) {
                if (!near.isEmpty()) {
                    a.lastPlayerAt = now();                     // only a player near keeps the fight from its reset
                }
                a.maxPlayers = Math.max(a.maxPlayers, n);
                most = Math.max(most, a.maxPlayers);
                d.markDirty();
            }
            findCoils();
            if (phase == WardenRulesSC.PHASE_HOLD) {
                keepShellsOffCoils();
            }
        }
        scaleTo(most, false);
        double lost = WardenRulesSC.LEASH + LOST_MARGIN;
        if (flatDistSq(boss) > lost * lost || boss.posY < floorY - 2 || boss.posY > floorY + ArenaLayoutSC.WALL_H + 8) {
            boss.getNavigator().clearPathEntity();
            boss.setPositionAndUpdate(cx, cy, cz);              // out of its pit: back in the middle at once
        }
    }

    /**
     * ПС-50: the fight's players now - every real player within SCALE_RADIUS (`near`) and every real one in the damage log (a share
     * of the damage from past SCALE_RADIUS - the plateau's edge, a gravity flight - counts as a fighter too: no smaller health for
     * shooters from afar, each with his loot), each once.
     */
    private int fighters(List<EntityPlayerMP> near) {
        Set<UUID> ids = new HashSet<UUID>(MobEventsSC.damageShares(boss).keySet());
        for (EntityPlayerMP p : near) {
            ids.add(p.getUniqueID());
        }
        return ids.size();
    }

    /** Server: the health for `players` (at least 1, never down: the most seen this fight; `full` - at full health, the summons). */
    void scaleTo(int players, boolean full) {
        int n = Math.max(1, Math.max(scale, players));
        float oldMax = boss.getMaxHealth(), newMax = WardenRulesSC.maxHealth(n);
        scale = n;
        if (full) {
            boss.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(newMax);
            boss.setHealth(newMax);
        } else if (newMax > oldMax + 0.01F) {
            float h = WardenRulesSC.rescaled(boss.getHealth(), oldMax, newMax);
            boss.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(newMax);
            boss.setHealth(h);
        }
    }

    // ------------------------------------------------------------------ the telegraph and the DataWatcher

    /** Ours now (the highest first): the blast's wind-up, the heat, the silencing hum, the press, the call; -1 none. */
    private int wantedAction() {
        if (resetLeft > 0) {
            return IMobActionSC.ACT_CORE_RESET;
        }
        if (overheated()) {
            return IMobActionSC.ACT_OVERHEAT;
        }
        if (silenceHum) {
            return IMobActionSC.ACT_SILENCE;
        }
        if (pressStage != STAGE_NONE) {
            return IMobActionSC.ACT_PRESS;
        }
        return summonLeft > 0 ? IMobActionSC.ACT_SUMMON : -1;
    }

    /** One of ours is up (the ПЭ-1М waits for it: one telegraph at a time). */
    public boolean busy() {
        return wantedAction() >= 0;
    }

    private void telegraph() {
        int want = wantedAction(), cur = boss.mobAction();
        if (want >= 0) {
            if (cur != want) {
                boss.setMobAction(want);
            }
        } else if (cur == IMobActionSC.ACT_CORE_RESET || cur == IMobActionSC.ACT_OVERHEAT || cur == IMobActionSC.ACT_SILENCE
                || cur == IMobActionSC.ACT_PRESS || cur == IMobActionSC.ACT_SUMMON) {
            boss.setMobAction(IMobActionSC.ACT_NONE);           // ours ended; a ПЭ-1 charge-up's is EmpMobGunSC's
        }
    }

    /** The DataWatcher from the state (it sends only what changed). */
    void sync() {
        int flags = phase & 3;
        if (shieldedByCoils()) {
            flags |= EntityBreakdownWardenSC.FLAG_SHIELDED;
        }
        if (windowLeft > 0) {
            flags |= EntityBreakdownWardenSC.FLAG_WINDOW;
        }
        if (overheated()) {
            flags |= EntityBreakdownWardenSC.FLAG_OVERHEAT;
        }
        if (resetLeft > 0) {
            flags |= EntityBreakdownWardenSC.FLAG_RESET_ARMED;
        }
        int timer = phase == WardenRulesSC.PHASE_HOLD ? windowLeft
                : phase == WardenRulesSC.PHASE_OVERLOAD ? (resetLeft > 0 ? resetLeft : Math.max(0, WardenRulesSC.FINISH_TICKS - clockTicks)) : 0;
        boss.syncWatchers(flags, highlight, litBits(), (timer + 19) / 20, scale, pressStage);   // seconds: a packet a second, not 20
    }

    // ------------------------------------------------------------------ NBT (scWarden)

    void write(NBTTagCompound t) {
        t.setByte("ph", (byte) phase);
        t.setInteger("win", windowLeft);
        t.setInteger("wave", waveIn);
        t.setInteger("hlIn", highlightIn);
        t.setInteger("hlLeft", highlightLeft);
        t.setByte("hl", (byte) highlight);
        t.setInteger("set", setLeft);
        t.setInteger("crack", crackIn);
        t.setInteger("swarm", swarmIn);
        t.setInteger("press", pressIn);
        t.setInteger("sil", silenceIn);
        t.setInteger("p3", phaseTicks);
        t.setInteger("clock", clockTicks);
        t.setInteger("reset", resetLeft);
        t.setByte("sc", (byte) Math.min(127, scale));
        Map<UUID, Float> log = new HashMap<UUID, Float>();
        float total = MobEventsSC.exportLog(boss, log);        // ПТ-21: the shares outlive an unload, a restart (a player's death)
        if (total > 0F) {
            NBTTagList l = new NBTTagList();
            for (Map.Entry<UUID, Float> en : log.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setLong("m", en.getKey().getMostSignificantBits());
                c.setLong("l", en.getKey().getLeastSignificantBits());
                c.setFloat("d", en.getValue().floatValue());
                l.appendTag(c);
            }
            t.setTag("dmg", l);
            t.setFloat("dmgT", total);
        }
        if (centred && !boss.bound()) {
            t.setIntArray("home", new int[]{(int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz)});
        }
    }

    void read(NBTTagCompound t) {
        int p = t.getByte("ph");
        phase = p >= WardenRulesSC.PHASE_HOLD && p <= WardenRulesSC.PHASE_OVERLOAD ? p : WardenRulesSC.PHASE_HOLD;
        windowLeft = Math.max(0, t.getInteger("win"));
        waveIn = t.hasKey("wave") ? t.getInteger("wave") : FIRST_WAVE;
        highlightIn = t.getInteger("hlIn");
        highlightLeft = Math.max(0, t.getInteger("hlLeft"));
        highlight = t.hasKey("hl") ? t.getByte("hl") : -1;
        setLeft = Math.max(0, t.getInteger("set"));
        crackIn = t.getInteger("crack");
        swarmIn = t.getInteger("swarm");
        pressIn = t.getInteger("press");
        silenceIn = t.getInteger("sil");
        phaseTicks = Math.max(0, t.getInteger("p3"));
        clockTicks = Math.max(0, t.getInteger("clock"));
        resetLeft = Math.max(0, t.getInteger("reset"));
        scale = Math.max(1, t.getByte("sc"));
        if (t.hasKey("dmgT")) {
            Map<UUID, Float> log = new HashMap<UUID, Float>();
            NBTTagList l = t.getTagList("dmg", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                log.put(new UUID(c.getLong("m"), c.getLong("l")), Float.valueOf(c.getFloat("d")));
            }
            MobEventsSC.restoreLog(boss, log, t.getFloat("dmgT"));
        }
        pressStage = STAGE_NONE;                               // a hum or a press doesn't outlive a reload
        silenceHum = false;
        if (highlight == EntityBreakdownWardenSC.SET_ALL || highlight < -1 || highlight > 3 || highlightLeft <= 0) {
            highlight = -1;                                     // the set's suppression itself isn't saved (MobArmorSC): no glow for it
            setLeft = 0;
        }
        int[] home = t.getIntArray("home");
        if (home.length == 3 && !boss.bound()) {
            setCentre(home[0] + 0.5D, home[1], home[2] + 0.5D, home[1] - 1);   // an egg's Warden keeps its first place
        }
    }
}
