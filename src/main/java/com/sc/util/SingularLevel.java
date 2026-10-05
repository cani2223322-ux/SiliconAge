package com.sc.util;

import com.sc.item.ItemArmorSC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The Singular suit's levels (docs/plan-singular-armor.md §6). Each piece keeps its own level in NBT
 * "SingLevel" (1..5, a piece without it is level 1) and its points towards the next one in "SingPts";
 * a Singular function opens at the level in the plan's §3 table (requiredLevel). A player in creative
 * mode counts as level 5 and has both sides of every branch.
 *
 * Stage 3: points (ОЧ1-ОЧ7, given by SingularProgressSC to every worn Singular piece, capped at the
 * piece's threshold), the tasks (Р5: one of three per level, counted per player in the persisted
 * data, key "scSingLv"), the branches (Р2: NBT "SingBranch3" / "SingBranch5") and the bonuses (Р3 /
 * Р4). The level-up itself is the Singular station's (stage 4): readyToUpgrade, applyLevelUp and
 * levelUpEffects are its API.
 */
public final class SingularLevel {

    public static final String NBT = "SingLevel", PTS = "SingPts", BRANCH3 = "SingBranch3", BRANCH5 = "SingBranch5", SYNC = "SingSync";
    public static final int MIN = 1, MAX = 5;

    private SingularLevel() {
    }

    /** A Singular armour piece. */
    public static boolean isSingular(ItemStack piece) {
        return piece != null && piece.getItem() instanceof ItemArmorSC && ((ItemArmorSC) piece.getItem()).getSuit() == ArmorSuit.SINGULAR;
    }

    private static NBTTagCompound tag(ItemStack piece) {
        if (!piece.hasTagCompound()) {
            piece.setTagCompound(new NBTTagCompound());
        }
        return piece.getTagCompound();
    }

    /** The piece's level, 1..5 (no tag / not set: 1). */
    public static int levelOf(ItemStack piece) {
        if (piece == null || !piece.hasTagCompound() || !piece.getTagCompound().hasKey(NBT)) {
            return MIN;
        }
        return clamp(piece.getTagCompound().getInteger(NBT));
    }

    public static void setLevel(ItemStack piece, int level) {
        if (piece == null) {
            return;
        }
        tag(piece).setInteger(NBT, clamp(level));
    }

    public static int clamp(int level) {
        return Math.max(MIN, Math.min(MAX, level));
    }

    /** The level a function opens at: the plan's §3 column; 1 for every function from before the Singular suit. */
    /** The bridge link's portals to the scanner's finds are precise from this level (stage 3 scatters them below it). */
    public static final int BRIDGE_FINDS_LEVEL = 5;

    public static int requiredLevel(ArmorFeature f) {
        switch (f) {
            case GRAV_STRIKE: case EVENT_HORIZON: case PHASE_DASH:
                return 2;
            case ANALYZER:
                return 2;
            case HEAT_VENT: case GRAV_PRESS: case GRAV_GRAB: case GRAV_SCANNER: case THREAT_SENSE: case BRIDGE_LINK:
                return 3;
            case TIME_SLOW: case RESONANCE:
                return 4;
            case BLACK_HOLE: case GRAV_DOME: case SINGULARITY:
                return 5;
            default:
                return 1;
        }
    }

    // ------------------------------------------------------------------ points (plan §6 "Очки")

    /** Points a piece at level N needs to go to N+1 (index = level; level 5: none). */
    private static final int[] THRESHOLD = {0, 2000, 8000, 25000, 60000, 0};

    /** ОЧ1 mB of gas a point, ОЧ2 blocks of gravity flight a point (ОЧ3: a point a damage point). */
    public static final int GAS_MB_PER_POINT = 10, FLIGHT_BLOCKS_PER_POINT = 10;
    /** ОЧ4: a mob, a boss; at most KILL_CAP points a minute from mobs (bosses are not capped). */
    public static final int KILL_POINTS = 5, BOSS_POINTS = 500, KILL_CAP = 200;
    /** ОЧ7: a biome first visited in the suit, a dimension first visited. */
    public static final int BIOME_POINTS = 50, DIMENSION_POINTS = 200;

    /** Points needed at `level` for the next level (0 at the top level). */
    public static int threshold(int level) {
        return level >= MIN && level < MAX ? THRESHOLD[level] : 0;
    }

    /** The piece's points towards its next level. */
    public static int points(ItemStack piece) {
        return piece == null || !piece.hasTagCompound() ? 0 : Math.max(0, piece.getTagCompound().getInteger(PTS));
    }

    public static void setPoints(ItemStack piece, int pts) {
        if (piece != null) {
            tag(piece).setInteger(PTS, capPoints(levelOf(piece), pts));
        }
    }

    /** Pure: points kept at `level` - 0..threshold (nothing at the top level): the excess isn't kept. */
    public static int capPoints(int level, long pts) {
        return (int) Math.max(0, Math.min(threshold(level), pts));
    }

    /** The piece has all the points its level asks for (the top level: never) - «готово». */
    public static boolean pointsFull(ItemStack piece) {
        int need = threshold(levelOf(piece));
        return need > 0 && points(piece) >= need;
    }

    /** Adds points to a Singular piece (capped at its threshold). @return how many were added */
    public static int addPoints(ItemStack piece, int n) {
        if (!isSingular(piece) || n <= 0) {
            return 0;
        }
        int before = points(piece);
        int after = capPoints(levelOf(piece), (long) before + n);
        if (after != before) {
            tag(piece).setInteger(PTS, after);
        }
        return after - before;
    }

    /** `n` points to every worn Singular piece. @return how many pieces took any */
    public static int awardWorn(EntityPlayer p, int n) {
        int took = 0;
        for (int t = 0; t < 4 && n > 0; t++) {
            took += addPoints(ArmorGasSC.worn(p, t), n) > 0 ? 1 : 0;
        }
        return took;
    }

    /** Any Singular piece worn. */
    public static boolean wearsSingular(EntityPlayer p) {
        for (int t = 0; t < 4; t++) {
            if (isSingular(ArmorGasSC.worn(p, t))) {
                return true;
            }
        }
        return false;
    }

    /** Pure, ОЧ5: the points a key function gives per use (0: none). */
    public static int keyPoints(ArmorFeature f) {
        if (f == null) {
            return 0;
        }
        switch (f) {
            case PHASE_DASH: return 2;
            case GRAV_GRAB: return 5;
            case GRAV_PRESS: return 10;
            case TIME_SLOW: case GRAV_DOME: return 15;
            case BLACK_HOLE: case SINGULARITY: return 20;
            default: return 0;
        }
    }

    /** Pure, ОЧ4: what a kill gives with `used` mob points already given this minute (bosses ignore the cap). */
    public static int killPoints(boolean boss, int used) {
        return boss ? BOSS_POINTS : Math.max(0, Math.min(KILL_POINTS, KILL_CAP - used));
    }

    /** Pure: the whole points in an accumulated amount at `perPoint` a point (the caller keeps the rest). */
    public static int wholePoints(double acc, double perPoint) {
        return acc <= 0 || perPoint <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.floor(acc / perPoint + 1e-9));
    }

    // ------------------------------------------------------------------ the tasks (Р5), per player

    /** The player's level data in the persisted NBT. */
    public static final String STORE = "scSingLv";
    /** Counters in STORE. */
    public static final String C_FLY = "fly", C_H2ABS = "h2abs", C_GAS = "gas", C_KILLS = "kills", C_NETHER = "nether",
            C_DASHES = "dashes", C_WITHER = "wither", C_DRAGON = "dragon", C_SM = "sm", C_BIOMES = "biomes", C_DIMS = "dims";
    /** Root keys of the persisted NBT kept by stage 2b. */
    public static final String HOLE_KILLS = "scHoleKills", RES_REACTOR_SEC = "scResReactorSec", SPAWNERS = "scSpawnersScanned";

    /** What each task needs: [target level 2..5][task 0..2]. */
    private static final int[][] TASK_NEED = {
        {5000, 300, 2000},      // to 2: fly 5 km in gravity flight, Н2 absorbs 300 damage, spend 2 000 mB of gases
        {100, 600, 50},         // to 3: 100 mobs in the suit, 10 min in the Nether under 50% heat, 50 phase dashes
        {1, 300, 20},           // to 4: a Wither, 5 min of resonance with the Singular reactor, 20 spawners scanned
        {1, 30, 5000},          // to 5: the Ender Dragon in the suit, 30 mobs by the black hole, 5 000 mB of SM poured
    };
    public static final int TASKS = 3;

    /** What task `index` for `target` level needs (0: no such task). */
    public static int taskNeed(int target, int index) {
        return target >= 2 && target <= MAX && index >= 0 && index < TASKS ? TASK_NEED[target - 2][index] : 0;
    }

    /** The player's persisted data (created when `create`). */
    public static NBTTagCompound persisted(EntityPlayer p, boolean create) {
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (create && !data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        return persisted;
    }

    /** The player's level counters (created when `create`). */
    public static NBTTagCompound store(EntityPlayer p, boolean create) {
        NBTTagCompound persisted = persisted(p, create);
        NBTTagCompound s = persisted.getCompoundTag(STORE);
        if (create && !persisted.hasKey(STORE)) {
            persisted.setTag(STORE, s);
        }
        return s;
    }

    /** Pure: a task's counter read from the persisted data (whole units, not capped). */
    public static int taskValue(NBTTagCompound persisted, int target, int index) {
        NBTTagCompound s = persisted.getCompoundTag(STORE);
        switch (target * 10 + index) {
            case 20: return (int) Math.min(Integer.MAX_VALUE, s.getDouble(C_FLY));
            case 21: return (int) s.getFloat(C_H2ABS);
            case 22: return s.getInteger(C_GAS);
            case 30: return s.getInteger(C_KILLS);
            case 31: return s.getInteger(C_NETHER);
            case 32: return s.getInteger(C_DASHES);
            case 40: return s.getInteger(C_WITHER);
            case 41: return persisted.getInteger(RES_REACTOR_SEC);
            case 42: return persisted.getCompoundTag(SPAWNERS).func_150296_c().size();
            case 50: return s.getInteger(C_DRAGON);
            case 51: return persisted.getInteger(HOLE_KILLS);
            case 52: return s.getInteger(C_SM);
            default: return 0;
        }
    }

    /** Pure: one of the target level's tasks is done in this persisted data. */
    public static boolean taskDoneIn(NBTTagCompound persisted, int target) {
        for (int i = 0; i < TASKS; i++) {
            int need = taskNeed(target, i);
            if (need > 0 && taskValue(persisted, target, i) >= need) {
                return true;
            }
        }
        return false;
    }

    /** The client's copy of the task counters, (target-2)*TASKS + index (the server sends it, ArmorNetSC.LevelMessage). */
    private static volatile int[] clientTasks = new int[4 * TASKS];
    private static volatile int clientBiomes, clientDims;

    /** The task counters as sent to the client: (target-2)*TASKS + index. */
    public static int[] taskValues(NBTTagCompound persisted) {
        int[] v = new int[4 * TASKS];
        for (int t = 2; t <= MAX; t++) {
            for (int i = 0; i < TASKS; i++) {
                v[(t - 2) * TASKS + i] = taskValue(persisted, t, i);
            }
        }
        return v;
    }

    /** Client, from the server's message. */
    public static void clientSet(int[] values, int biomes, int dims) {
        if (values != null && values.length == 4 * TASKS) {
            clientTasks = values.clone();
        }
        clientBiomes = biomes;
        clientDims = dims;
    }

    /** Biomes / dimensions the player has explored in the suit (client: the synced copy). */
    public static int explored(EntityPlayer p, boolean dims) {
        if (p == null) {
            return 0;
        }
        if (p.worldObj != null && p.worldObj.isRemote) {
            return dims ? clientDims : clientBiomes;
        }
        return store(p, false).getIntArray(dims ? C_DIMS : C_BIOMES).length;
    }

    private static int value(EntityPlayer p, int target, int index) {
        if (p.worldObj != null && p.worldObj.isRemote) {
            int[] v = clientTasks;
            return target >= 2 && target <= MAX && index >= 0 && index < TASKS ? v[(target - 2) * TASKS + index] : 0;
        }
        return taskValue(persisted(p, false), target, index);
    }

    /** The UI: {current (capped at the need), needed} of task `index` for `target` level (client: the synced copy). */
    public static int[] taskProgress(EntityPlayer p, int target, int index) {
        int need = taskNeed(target, index);
        int cur = p == null || need <= 0 ? 0 : Math.min(need, value(p, target, index));
        return new int[]{cur, need};
    }

    /** One of the three tasks for `target` level is done (a task done counts for every piece going to that level). */
    public static boolean taskDone(EntityPlayer p, int target) {
        for (int i = 0; i < TASKS; i++) {
            int[] pr = taskProgress(p, target, i);
            if (pr[1] > 0 && pr[0] >= pr[1]) {
                return true;
            }
        }
        return false;
    }

    /** The piece may go up a level: Singular, under 5, its points full and a task for the next level done. */
    public static boolean readyToUpgrade(EntityPlayer p, ItemStack piece) {
        int lvl = levelOf(piece);
        return isSingular(piece) && lvl < MAX && pointsFull(piece) && taskDone(p, lvl + 1);
    }

    /** Stage 4 (the station): the piece goes up one level, its points back to 0. @return the new level */
    public static int applyLevelUp(ItemStack piece) {
        if (!isSingular(piece)) {
            return levelOf(piece);
        }
        int lvl = clamp(levelOf(piece) + 1);
        setLevel(piece, lvl);
        tag(piece).setInteger(PTS, 0);
        return lvl;          // the tanks only grow with the level - nothing stored is lost
    }

    /** Server: «Сингулярная броня: уровень N!», a sound and a burst of particles round the player. */
    public static void levelUpEffects(EntityPlayer p, int newLevel) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.sing.levelup", String.valueOf(newLevel)));
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.levelup", 1F, 0.7F);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 0.8F, 0.6F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) p.worldObj;
            ws.func_147487_a("portal", p.posX, p.posY + 1, p.posZ, 120, 0.6, 1.0, 0.6, 1.2);
            ws.func_147487_a("witchMagic", p.posX, p.posY + 1, p.posZ, 60, 0.8, 1.0, 0.8, 0.2);
            ws.func_147487_a("fireworksSpark", p.posX, p.posY + 1.2, p.posZ, 40, 0.3, 0.3, 0.3, 0.25);
        }
    }

    // ------------------------------------------------------------------ the branches (Р2)

    /** A function in no branch / a branch not chosen yet (both sides locked); the first and the second alternative. */
    public static final int BRANCH_BOTH = 0, BRANCH_NONE = 0, BRANCH_A = 1, BRANCH_B = 2;

    private static String branchKey(int level) {
        return level == 3 ? BRANCH3 : level == 5 ? BRANCH5 : null;
    }

    /**
     * The branch the piece has chosen at `level` (Р2: level 3 - 1 the press / 2 the grab, level 5 -
     * 1 the black hole / 2 the dome); BRANCH_NONE while not chosen - then both sides are locked.
     */
    public static int branchChoice(ItemStack piece, int level) {
        String k = branchKey(level);
        if (piece == null || k == null || !piece.hasTagCompound()) {
            return BRANCH_NONE;
        }
        int c = piece.getTagCompound().getInteger(k);
        return c == BRANCH_A || c == BRANCH_B ? c : BRANCH_NONE;
    }

    /** Sets (BRANCH_NONE: clears) a branch choice - the free first choice, the station's re-choice. @return whether it was valid */
    public static boolean setBranch(ItemStack piece, int level, int choice) {
        String k = branchKey(level);
        if (piece == null || k == null || choice < BRANCH_NONE || choice > BRANCH_B) {
            return false;
        }
        if (choice == BRANCH_NONE) {
            if (piece.hasTagCompound()) {
                piece.getTagCompound().removeTag(k);
            }
        } else {
            tag(piece).setInteger(k, choice);
        }
        return true;
    }

    /** The piece has reached `level` (3 or 5) and its branch there isn't chosen yet: the K menu offers the choice. */
    public static boolean branchPending(ItemStack piece, int level) {
        return isSingular(piece) && branchKey(level) != null && levelOf(piece) >= level && branchChoice(piece, level) == BRANCH_NONE;
    }

    /**
     * Server: the free first choice from the K menu (ArmorNetSC.BRANCH) for the worn chestplate -
     * only once it has reached the level and only while nothing is chosen. @return whether it was taken
     */
    public static boolean chooseFree(EntityPlayer p, int level, int choice) {
        ItemStack chest = ArmorGasSC.worn(p, ArmorGasSC.CHEST);
        if ((choice != BRANCH_A && choice != BRANCH_B) || !branchPending(chest, level)) {
            return false;
        }
        return setBranch(chest, level, choice);
    }

    /** Which side of a branch a function is (BRANCH_A / BRANCH_B), BRANCH_BOTH when it's in no branch. */
    public static int branchOf(ArmorFeature f) {
        switch (f) {
            case GRAV_PRESS: case BLACK_HOLE:
                return BRANCH_A;
            case GRAV_GRAB: case GRAV_DOME:
                return BRANCH_B;
            default:
                return BRANCH_BOTH;
        }
    }

    /** The function of a branch side: level 3 - 1 Н10 press, 2 Н8 grab; level 5 - 1 Н3 black hole, 2 Н17 dome; else null. */
    public static ArmorFeature branchFeature(int level, int choice) {
        if (level == 3) {
            return choice == BRANCH_A ? ArmorFeature.GRAV_PRESS : choice == BRANCH_B ? ArmorFeature.GRAV_GRAB : null;
        }
        if (level == 5) {
            return choice == BRANCH_A ? ArmorFeature.BLACK_HOLE : choice == BRANCH_B ? ArmorFeature.GRAV_DOME : null;
        }
        return null;
    }

    /** Pure: a function of side `side` is allowed with the choice `choice` (nothing chosen: neither side). */
    public static boolean branchAllows(int side, int choice) {
        return side == BRANCH_BOTH || side == choice;
    }

    /** The piece's branch choice lets the function work (no creative rule). */
    public static boolean branchAllowed(ArmorFeature f, ItemStack piece) {
        int side = branchOf(f);
        return side == BRANCH_BOTH || branchAllows(side, branchChoice(piece, requiredLevel(f)));
    }

    /** The piece's branch choice lets the function work for this player (creative: both sides). */
    public static boolean branchAllowed(EntityPlayer p, ArmorFeature f, ItemStack piece) {
        return (p != null && p.capabilities.isCreativeMode) || branchAllowed(f, piece);
    }

    /** The level that counts for this player and piece: creative mode - always MAX. */
    public static int effectiveLevel(EntityPlayer p, ItemStack piece) {
        return p != null && p.capabilities.isCreativeMode ? MAX : levelOf(piece);
    }

    /** Pure: a function is open with a piece of `level` (creative: true). */
    public static boolean unlocked(ArmorFeature f, int level, boolean creative) {
        return creative || level >= requiredLevel(f);
    }

    /** The function is open in this worn piece (its own piece; not worn: by the level-1 rule). */
    public static boolean unlocked(EntityPlayer p, ArmorFeature f, ItemStack piece) {
        return unlocked(f, levelOf(piece), p != null && p.capabilities.isCreativeMode);
    }

    // ------------------------------------------------------------------ the bonuses (Р3 / Р4)

    /** Р3 per level above 1: tanks +10%, damage protection +5%, EU use -5%; Р4: all four pieces at one level (2+) +10% more each. */
    public static final int TANK_PCT = 10, PROTECT_PCT = 5, EU_PCT = 5, SYNC_PCT = 10;

    /** Pure: a bonus in percent at `level` with `perLevel` a level (+SYNC_PCT when the set is in sync). */
    public static int bonusPercent(int level, boolean sync, int perLevel) {
        int lvl = clamp(level);
        return lvl <= MIN ? 0 : (lvl - 1) * perLevel + (sync ? SYNC_PCT : 0);
    }

    /** Pure, Р4: the common level of a set of four Singular pieces all at the same level 2+, else 0. */
    public static int syncedLevel(ItemStack[] w) {
        if (w == null || w.length < 4) {
            return 0;
        }
        int lvl = -1;
        for (int t = 0; t < 4; t++) {
            if (!isSingular(w[t]) || (lvl >= 0 && levelOf(w[t]) != lvl)) {
                return 0;
            }
            lvl = levelOf(w[t]);
        }
        return lvl >= 2 ? lvl : 0;
    }

    /** The piece was last worn in a synced set (NBT "SingSync", kept by updateSync). */
    public static boolean synced(ItemStack piece) {
        return piece != null && piece.hasTagCompound() && piece.getTagCompound().getBoolean(SYNC);
    }

    /** Marks each Singular piece of the worn set synced or not (Р4). @return whether anything changed */
    public static boolean updateSync(ItemStack[] w) {
        boolean sync = syncedLevel(w) > 0;
        boolean changed = false;
        for (int t = 0; w != null && t < w.length && t < 4; t++) {
            if (isSingular(w[t]) && synced(w[t]) != sync) {
                if (sync) {
                    tag(w[t]).setBoolean(SYNC, true);
                } else {
                    w[t].getTagCompound().removeTag(SYNC);
                }
                changed = true;
            }
        }
        return changed;
    }

    /**
     * СБ-2: the sync flag is valid only while the piece is worn - updateSync keeps it on the worn set; a piece
     * found anywhere else (the main inventory - ItemArmorSC.onUpdate; the Singular station's armour / donor
     * slots - TileEntitySingularStationSC) loses it here. @return whether it had the flag
     */
    public static boolean clearSync(ItemStack piece) {
        if (piece == null || !piece.hasTagCompound() || !piece.getTagCompound().hasKey(SYNC)) {
            return false;
        }
        piece.getTagCompound().removeTag(SYNC);
        return true;
    }

    /** Р3 + Р4: the piece's tanks grow by this much, percent (ArmorGasSC.levelBonusPercent). */
    public static int tankBonusPercent(ItemStack piece) {
        return isSingular(piece) ? bonusPercent(levelOf(piece), synced(piece), TANK_PCT) : 0;
    }

    /** Р3 + Р4: the share of its EU a function of this piece costs (ArmorLogicSC.pay). */
    public static float euMul(ItemStack piece) {
        return isSingular(piece) ? 1F - bonusPercent(levelOf(piece), synced(piece), EU_PCT) / 100F : 1F;
    }

    /** Р3 + Р4: the worn set's extra protection, percent: the average of the four slots' bonuses (a missing piece gives 0). */
    public static int protectionPercent(ItemStack[] w) {
        int sum = 0;
        for (int t = 0; w != null && t < w.length && t < 4; t++) {
            if (isSingular(w[t])) {
                sum += bonusPercent(levelOf(w[t]), synced(w[t]), PROTECT_PCT);
            }
        }
        return sum / 4;
    }
}
