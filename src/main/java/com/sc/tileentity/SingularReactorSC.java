package com.sc.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * The Singular Reactor (GeneratorType.SINGULAR_REACTOR): a tiny black hole held by 16 gravity
 * coils inside a 7x7x5 build. TileEntityGeneratorSC owns the block, its slots, energy buffer
 * and the port registry shared with the Tokamak XV; this class holds the hole's own state and
 * physics and is ticked from there.
 *
 * The build, dy from the reactor (levels 1-5 = dy -2..+2):
 * - dy -2 / +2: the floor and the cap, 7x7 lead blocks or lead glass;
 * - dy -1 / +1: a 7x7 wall ring (lead, lead glass or ports) and, inside, 8 gravity coils in a
 *   ring round the axis (the 3x3 round the column, its centre empty);
 * - dy 0: the wall ring with ports and the reactor in the centre;
 * - everything else inside (the chamber) is empty - air.
 * Ports may sit anywhere in the three wall rings: up to 8 of the mod's tanks (liquid helium,
 * deuterium, argon) and up to 4 of its energy storages, IV or better.
 *
 * The cycle: "Lighting" draws 700 million EU from the port storages (CHARGE_PER_TICK at most),
 * then 50 000 mB of deuterium and one matter capsule go into 30 s of compression, and a hole of
 * 50% mass is born. It evaporates (faster the lighter it is - Hawking), capsules feed it; its
 * output follows its mass (powerFactor). Liquid helium 12 mB/t and deuterium 0.25 mB/t hold it
 * (containment, 0-100%). "Stop" eats the hole in 60 s and returns a fifth of the charge.
 * Accidents never break the world: lost containment pulls things in for 5 s, then a flash of
 * radiation through every wall and 2-4 coils thrown out as items; a hole under 5% evaporates
 * in a flash 32 blocks wide. Argon (1000 mB) puts it out softly below 10% containment.
 */
public class SingularReactorSC {

    // ------------------------------------------------------------------ the numbers

    /** The charge that lights it, and how fast it comes out of the port storages (8 SV packets a tick: ~33 s). */
    public static final long IGNITION_EU = GeneratorType.SINGULAR_REACTOR.ignitionThreshold();
    public static final int CHARGE_PER_TICK = 8 * 131072;
    /** Compression, the soft stop's eating, the pull before a flash, the flash itself - ticks. */
    public static final int COMPRESS_TICKS = 600, DRAIN_TICKS = 1200, PULL_TICKS = 100, BURST_TICKS = 1200;
    /** Liquid helium and deuterium a tick while it compresses / runs / is being eaten. */
    public static final double HE_PER_TICK = 12, D_PER_TICK = 0.25;
    /**
     * To light: liquid helium in the port tanks - the whole compression (7200 mB) and a margin, so a
     * compression that starts can't run dry halfway and lose the charge, the capsule and the deuterium;
     * deuterium the compression takes; argon a soft stop takes.
     */
    public static final int HE_START = (int) (COMPRESS_TICKS * HE_PER_TICK) + 800, D_IGNITION = 50000, ARGON_STOP = 1000;
    /** Mass (0-1): at birth, one capsule, what Auto holds, the lower end of the work window and of the light zone, evaporation. */
    public static final double START_MASS = 0.50, CAPSULE_MASS = 0.10, AUTO_TARGET = 0.55;
    public static final double WINDOW_LO = 0.40, WINDOW_HI = 0.70, LIGHT = 0.20, EVAP_MASS = 0.05, FEED_CEILING = 0.98;
    /** Evaporation a tick at the Auto mass: one capsule in 7.5 minutes; it goes as 1 / mass. */
    public static final double E0 = CAPSULE_MASS / 9000.0;
    /** Feed modes: Economy, Normal, Overdrive - one capsule in this many ticks (10, 7.5, 5 minutes). */
    public static final int MODE_ECO = 0, MODE_NORMAL = 1, MODE_FORCE = 2;
    public static final int[] FEED_TICKS = {12000, 9000, 6000};
    /** Auto: extra feed for each unit of mass short of the target (5% short: one more Normal rate); its ceiling. */
    public static final double AUTO_GAIN = (CAPSULE_MASS / 9000.0) / 0.05, AUTO_MAX = 2 * CAPSULE_MASS / 9000.0;
    /** Output: the rated SV packet and the ceiling (x1.5). */
    public static final int RATED = GeneratorType.SINGULAR_REACTOR.euPerTick, MAX_OUTPUT = RATED * 3 / 2;
    /** Share of the charge a soft stop gives back to the port storages. */
    public static final double RETURN_SHARE = 0.20;
    /** Containment a second: no helium, no deuterium, the build broken, a light hole; the recovery; argon below this. */
    public static final float CONT_NO_HE = 4F, CONT_NO_D = 2F, CONT_BROKEN = 10F, CONT_LIGHT = 1F, CONT_RECOVER = 2F, CONT_ARGON = 10F;
    /** The pull's reach; the flashes: lost containment, evaporation (level, radius). */
    public static final int PULL_RADIUS = 8, EJECT_RADIUS = 24, EVAP_RADIUS = 32;
    public static final float EJECT_LEVEL = 25F, EVAP_LEVEL = 30F, PULL_LEVEL = 10F;
    public static final int TANKS_MAX = 8, STORES_MAX = 4;
    /** The port gases (0 He, 1 D, 2 Ar). */
    public static final String[] GASES = {"liquidhelium", "deuterium", "argon"};
    /** Wall labels: nothing, 1-3 a gas, a storage, a tank never filled, another fluid. */
    public static final int LABEL_NONE = 0, LABEL_STORE = 5, LABEL_FREE = 6, LABEL_OTHER = 7;
    public static final int WALL_CELLS = 72;

    public static final int PHASE_IDLE = 0, PHASE_CHARGE = 1, PHASE_COMPRESS = 2, PHASE_RUN = 3, PHASE_DRAIN = 4, PHASE_PULL = 5;
    /** What ended the last run (kept until "Allow lighting"): put out safely, lost containment, evaporated. */
    public static final int EVENT_NONE = 0, EVENT_SOFT = 1, EVENT_EJECT = 2, EVENT_EVAP = 3;

    // ------------------------------------------------------------------ pure logic (the self-test checks these)

    /** Output multiplier by mass: under 20% x1.5, 20-40% x1.2, the window 40-70% x1.0, over 70% x0.6. */
    public static double powerFactor(double mass) {
        return mass < LIGHT ? 1.5 : mass < WINDOW_LO ? 1.2 : mass <= WINDOW_HI ? 1.0 : 0.6;
    }

    /** EU a tick at this mass (at most MAX_OUTPUT). */
    public static int outputFor(double mass) {
        return (int) Math.min(MAX_OUTPUT, Math.round(RATED * powerFactor(mass)));
    }

    /** Mass lost a tick: E0 at the Auto mass, as 1 / mass (a lighter hole evaporates faster). */
    public static double evaporation(double mass) {
        return E0 * AUTO_TARGET / Math.max(0.01, mass);
    }

    /** Mass a tick the injector puts in: a fixed rate for the mode, or Auto holding AUTO_TARGET; nothing near full. */
    public static double feedRate(int mode, boolean auto, double mass) {
        if (mass >= FEED_CEILING) {
            return 0;
        }
        if (auto) {
            return Math.max(0, Math.min(AUTO_MAX, evaporation(mass) + AUTO_GAIN * (AUTO_TARGET - mass)));
        }
        return CAPSULE_MASS / FEED_TICKS[Math.max(0, Math.min(2, mode))];
    }

    /** Containment change a second. */
    public static float containmentDelta(boolean heShort, boolean dShort, boolean buildOk, double mass) {
        float d = 0F;
        if (heShort) {
            d -= CONT_NO_HE;
        }
        if (dShort) {
            d -= CONT_NO_D;
        }
        if (!buildOk) {
            d -= CONT_BROKEN;
        }
        if (mass < LIGHT) {
            d -= CONT_LIGHT;
        }
        return d == 0F ? CONT_RECOVER : d;
    }

    /** Radiation of a running hole by its mass (the lead shell stops it): level and reach. */
    public static float radiationLevelFor(double mass) {
        return mass < LIGHT ? 16F : mass < WINDOW_LO ? 12F : mass <= WINDOW_HI ? 9F : 6F;
    }

    public static int radiationRadiusFor(double mass) {
        return mass < LIGHT ? 30 : mass < WINDOW_LO ? 24 : mass <= WINDOW_HI ? 20 : 16;
    }

    /** The Hawking temperature, for the screen (billions of K): goes as 1 / mass. */
    public static double hawkingBillionK(double mass) {
        return 1.2 * START_MASS / Math.max(0.01, mass);
    }

    /** A matter capsule (the Matter Compressor's): compared by the component, which may not be registered yet. */
    public static boolean isCapsule(ItemStack s) {
        net.minecraft.item.Item cap = com.sc.init.ModItems.component("matterCapsule");
        return s != null && cap != null && s.getItem() == cap;
    }

    /** Index of a wall cell on one level (|dx| or |dz| = 3), 0-23, or -1. */
    public static int wallIndex(int dx, int dz) {
        int k = 0;
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    if (x == dx && z == dz) {
                        return k;
                    }
                    k++;
                }
            }
        }
        return -1;
    }

    /** Index of a coil in the ring round the axis (|dx|, |dz| <= 1, not the centre), 0-7, or -1. */
    public static int ringIndex(int dx, int dz) {
        int k = 0;
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (x == dx && z == dz) {
                    return k;
                }
                k++;
            }
        }
        return -1;
    }

    /** What the build wants at (dx, dy, dz): 0 lead (cap / floor), 1 wall (lead or a port), 2 a coil, 3 empty, 4 the reactor. */
    public static int cellRole(int dx, int dy, int dz) {
        if (Math.abs(dy) == 2) {
            return 0;
        }
        if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
            return 1;
        }
        if (dy == 0) {
            return dx == 0 && dz == 0 ? 4 : 3;
        }
        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (dx != 0 || dz != 0) ? 2 : 3;
    }

    // ------------------------------------------------------------------ state

    private final TileEntityGeneratorSC g;

    private int phase = PHASE_IDLE, phaseTicks, event = EVENT_NONE;
    private double mass, capsuleLeft, drainFrom;
    private float containment = 100F;
    private int feedMode = MODE_NORMAL;
    private boolean auto = true;
    private int burstTicks, burstRadius;
    private float burstLevel;
    private double heDebt, dDebt;
    private boolean heShort, dShort, noCapsule;
    private int warnedAt = 100;
    private boolean warnedLight;
    /** Mass a second over the last minute (%, 255 - none), a ring. */
    private final byte[] hist = new byte[60];
    private int histHead;

    // the last scan (not saved but portMem)
    private boolean ready, frozen, scanned, portTaken;
    private final int[] walls = new int[3], ports = new int[3], junk = new int[3];
    private int coils, capMissing, tanks, stores, weak;
    private long capTop, capBottom;
    private final int[] labels = new int[WALL_CELLS];
    private final String[] portMem = new String[WALL_CELLS];
    private final int[] portCap = new int[3], portFluid = new int[3], portCount = new int[3];
    private int freeTanks;
    private long storeHave, storeRoom;

    SingularReactorSC(TileEntityGeneratorSC g) {
        this.g = g;
        java.util.Arrays.fill(hist, (byte) 255);
    }

    // ------------------------------------------------------------------ getters (screen, WAILA, tests)

    public int getPhase() {
        return phase;
    }

    public int getPhaseTicks() {
        return phaseTicks;
    }

    public int getEvent() {
        return event;
    }

    public double getMass() {
        return mass;
    }

    public double getCapsuleLeft() {
        return capsuleLeft;
    }

    public float getContainment() {
        return containment;
    }

    public int getFeedMode() {
        return feedMode;
    }

    public boolean isAuto() {
        return auto;
    }

    public int getBurstTicks() {
        return burstTicks;
    }

    public boolean isHeliumShort() {
        return heShort;
    }

    public boolean isDeuteriumShort() {
        return dShort;
    }

    public boolean isCapsuleShort() {
        return noCapsule;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isPortTaken() {
        return portTaken;
    }

    /** The hole exists (running, being eaten, or about to be thrown out). */
    public boolean hasHole() {
        return phase == PHASE_RUN || phase == PHASE_DRAIN || phase == PHASE_PULL;
    }

    /** The block can't be broken: a hole, the 30 s of compression (the charge, capsule and deuterium are in it) or a flash still burning. */
    public boolean holdsBlock() {
        return hasHole() || phase == PHASE_COMPRESS || burstTicks > 0;
    }

    public int getCoils() {
        return coils;
    }

    public int getCoilCount() {
        return Integer.bitCount(coils);
    }

    /** Wall cells in place on level l (0: dy -1, 1: dy 0, 2: dy +1), 24 bits; ports among them. */
    public int getWalls(int l) {
        return walls[l];
    }

    public int getPorts(int l) {
        return ports[l];
    }

    /** Something in the chamber on level l (5x5, bit (dz + 2) * 5 + dx + 2). */
    public int getJunk(int l) {
        return junk[l];
    }

    public int getWallCount() {
        return Integer.bitCount(walls[0]) + Integer.bitCount(walls[1]) + Integer.bitCount(walls[2]);
    }

    public int getJunkCount() {
        return Integer.bitCount(junk[0]) + Integer.bitCount(junk[1]) + Integer.bitCount(junk[2]);
    }

    public int getCapMissing() {
        return capMissing;
    }

    public boolean capAt(boolean top, int dx, int dz) {
        return ((top ? capTop : capBottom) >> ((dz + 3) * 7 + dx + 3) & 1) != 0;
    }

    public int getLabel(int cell) {
        return cell >= 0 && cell < WALL_CELLS ? labels[cell] : 0;
    }

    public int getPortCap(int gas) {
        return portCap[gas];
    }

    public int getPortFluid(int gas) {
        return portFluid[gas];
    }

    public int getPortCount(int gas) {
        return portCount[gas];
    }

    public int getFreeTanks() {
        return freeTanks;
    }

    public int getTanks() {
        return tanks;
    }

    public int getStores() {
        return stores;
    }

    public int getWeakStores() {
        return weak;
    }

    public long getStoresHave() {
        return storeHave;
    }

    public long getStoresRoom() {
        return storeRoom;
    }

    /** Mass k seconds ago (0 the latest), % or -1. */
    public int massAgo(int k) {
        int v = hist[((histHead - 1 - k) % 60 + 60) % 60] & 255;
        return v == 255 ? -1 : v;
    }

    /** EU a tick it makes at this moment (0 without a hole). */
    public int outputNow() {
        if (phase == PHASE_RUN) {
            return outputFor(mass);
        }
        if (phase == PHASE_DRAIN) {
            return (int) Math.round(RATED * (double) phaseTicks / DRAIN_TICKS);
        }
        return 0;
    }

    /** The feed a tick now (mass): what the injector puts in while it has a capsule. */
    public double feedNow() {
        return phase == PHASE_RUN ? feedRate(feedMode, auto, mass) : 0;
    }

    /** Everything is there to press "Lighting" (the charge may still be missing from the storages - see chargeOk). */
    public boolean canLight() {
        return phase == PHASE_IDLE && event == EVENT_NONE && ready && portFluid[0] >= HE_START && portFluid[1] >= D_IGNITION
                && hasCapsule() && chargeOk();
    }

    /** The port storages hold the rest of the charge. */
    public boolean chargeOk() {
        return g.getIgnitionEU() + storeHave >= IGNITION_EU;
    }

    public boolean hasCapsule() {
        return isCapsule(g.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL));
    }

    public int capsules() {
        ItemStack s = g.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        return isCapsule(s) ? s.stackSize : 0;
    }

    // ------------------------------------------------------------------ radiation

    public boolean isPiercing() {
        return burstTicks > 0 || phase == PHASE_PULL;
    }

    public float radiationLevel() {
        if (burstTicks > 0) {
            return Math.max(PULL_LEVEL, burstLevel * burstTicks / (float) BURST_TICKS);
        }
        if (phase == PHASE_PULL) {
            return PULL_LEVEL;
        }
        if (phase == PHASE_RUN || phase == PHASE_DRAIN) {
            return radiationLevelFor(mass);
        }
        return phase == PHASE_COMPRESS ? radiationLevelFor(WINDOW_LO) / 2 : 0F;
    }

    public int radiationRadius() {
        if (burstTicks > 0) {
            return burstRadius;
        }
        if (phase == PHASE_PULL) {
            return PULL_RADIUS;
        }
        return radiationRadiusFor(phase == PHASE_COMPRESS ? START_MASS : mass);
    }

    // ------------------------------------------------------------------ the buttons (server)

    /** "Lighting": the charge starts coming out of the port storages. */
    public boolean light() {
        if (!canLight()) {
            return false;
        }
        phase = PHASE_CHARGE;
        phaseTicks = 0;
        g.markDirty();
        syncBlock();
        return true;
    }

    /** "Stop": cancels the charge / compression; eats a running hole; "Allow lighting" after an event. */
    public void stop() {
        switch (phase) {
            case PHASE_IDLE:
                if (event != EVENT_NONE) {
                    event = EVENT_NONE;
                }
                break;
            case PHASE_CHARGE:
                phase = PHASE_IDLE;                                  // the charge drawn so far stays for next time
                break;
            case PHASE_COMPRESS:
                returnCharge();
                phase = PHASE_IDLE;
                break;
            case PHASE_RUN:
                phase = PHASE_DRAIN;
                phaseTicks = DRAIN_TICKS;
                drainFrom = mass;
                g.tellNearSC("sc.chat.sing.stopping");
                break;
            default:
        }
        g.markDirty();
        syncBlock();
    }

    /** The phase reaches the clients with the block (hasHole: the block's hardness, the wrench) - not only the open screen. */
    private void syncBlock() {
        World w = g.getWorldObj();
        if (w != null && !w.isRemote) {
            w.markBlockForUpdate(g.xCoord, g.yCoord, g.zCoord);
        }
    }

    public void cycleFeed() {
        feedMode = (feedMode + 1) % 3;
        g.markDirty();
    }

    public void toggleAuto() {
        auto = !auto;
        g.markDirty();
    }

    // ------------------------------------------------------------------ the tick (server)

    /** One server tick (called from TileEntityGeneratorSC.updateEntity). @return EU made this tick */
    int tick(boolean switchedOn) {
        World w = g.getWorldObj();
        long time = w.getTotalWorldTime();
        if (burstTicks > 0 && --burstTicks == 0) {
            syncBlock();                  // the flash is over: the client may let the block be broken again
        }
        if (time % 20 == 5) {
            hist[histHead] = (byte) (hasHole() ? Math.max(0, Math.min(100, (int) Math.round(mass * 100))) : 255);
            histHead = (histHead + 1) % 60;
        }
        if (!scanned || time % 20 == 3) {
            scanned = true;
            scan();
        }
        if (frozen) {
            return 0;                                                // part of the build unloaded: held as it is
        }
        int was = phase;
        int eu;
        switch (phase) {
            case PHASE_CHARGE: eu = tickCharge(switchedOn); break;
            case PHASE_COMPRESS: eu = tickCompress(); break;
            case PHASE_RUN: eu = tickRun(switchedOn, time); break;
            case PHASE_DRAIN: eu = tickDrain(switchedOn, time); break;
            case PHASE_PULL: eu = tickPull(); break;
            default:
                g.setStatusSC(!ready ? GeneratorStatus.NO_STRUCTURE : event == EVENT_SOFT ? GeneratorStatus.SOFT_STOP
                        : event != EVENT_NONE ? GeneratorStatus.DISRUPTED : GeneratorStatus.IDLE);
                eu = 0;
        }
        if (phase != was) {
            syncBlock();                                             // born, put out, thrown out: the clients' hasHole follows
        }
        return eu;
    }

    private int tickCharge(boolean switchedOn) {
        if (!ready) {
            g.setStatusSC(GeneratorStatus.NO_STRUCTURE);
            return 0;
        }
        if (!switchedOn) {                                           // switched off: no charge, and no compression either -
            g.setStatusSC(g.isPowerOn() ? GeneratorStatus.REDSTONE : GeneratorStatus.DISABLED);
            return 0;                                                // the capsule and the deuterium stay where they are
        }
        g.setStatusSC(GeneratorStatus.IGNITING);
        chargeFromPorts();
        if (g.getIgnitionEU() < IGNITION_EU) {
            return 0;
        }
        if (!hasCapsule()) {
            g.setStatusSC(GeneratorStatus.NO_CAPSULE);
            return 0;
        }
        if (portFluid[0] < HE_START) {
            g.setStatusSC(GeneratorStatus.NO_COOLANT);
            return 0;
        }
        if (g.drainPortsSC(GASES[1], D_IGNITION, true) < D_IGNITION) {
            g.setStatusSC(GeneratorStatus.NO_DEUTERIUM);
            return 0;
        }
        g.decrStackSize(TileEntityGeneratorSC.SLOT_FUEL, 1);        // the seed
        g.setIgnitionEUSC(g.getIgnitionEU() - IGNITION_EU);
        phase = PHASE_COMPRESS;
        phaseTicks = COMPRESS_TICKS;
        heDebt = 0;
        g.tellNearSC("sc.chat.sing.compress");
        sound("mob.endermen.portal", 1.5F, 0.5F);
        g.markDirty();
        return 0;
    }

    private int tickCompress() {
        g.setStatusSC(GeneratorStatus.IGNITING);
        if (!ready || !drainHelium()) {
            g.tellNearSC(!ready ? "sc.chat.sing.abort.build" : "sc.chat.sing.abort.he");
            returnCharge();
            phase = PHASE_IDLE;
            event = EVENT_SOFT;
            g.setStatusSC(GeneratorStatus.SOFT_STOP);
            g.markDirty();
            return 0;
        }
        if (phaseTicks % 100 == 0) {
            sound("portal.trigger", 0.6F, 0.4F + 0.5F * (COMPRESS_TICKS - phaseTicks) / COMPRESS_TICKS);
        }
        if (--phaseTicks > 0) {
            return 0;
        }
        phase = PHASE_RUN;                                          // born
        mass = START_MASS;
        capsuleLeft = 0;
        containment = 100F;
        warnedAt = 100;
        warnedLight = false;
        dDebt = 0;
        g.setIgnitedSC(true);
        g.tellNearSC("sc.chat.sing.born");
        sound("random.explode", 1.2F, 0.3F);
        g.markDirty();
        return 0;
    }

    /** Liquid helium for the coils, always in full. @return false: the tanks are dry */
    private boolean drainHelium() {
        heDebt += HE_PER_TICK;
        int he = (int) heDebt;
        if (he <= 0) {
            return true;
        }
        int got = g.drainPortsSC(GASES[0], he, false);
        heDebt -= got;
        heShort = got < he;
        if (heShort) {
            heDebt = 0;
        }
        return !heShort;
    }

    private void drainDeuterium() {
        dDebt += D_PER_TICK;
        int d = (int) dDebt;
        if (d <= 0) {
            return;
        }
        int got = g.drainPortsSC(GASES[1], d, false);
        dDebt -= got;
        dShort = got < d;
        if (dShort) {
            dDebt = 0;
        }
    }

    /** The injector: feeds this tick's mass from the capsule in it, taking the next one from the slot. */
    private void feed() {
        double want = feedRate(feedMode, auto, mass);
        if (want <= 0) {
            noCapsule = false;
            return;
        }
        if (capsuleLeft < want) {
            ItemStack s = g.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
            if (isCapsule(s)) {
                g.decrStackSize(TileEntityGeneratorSC.SLOT_FUEL, 1);
                capsuleLeft += CAPSULE_MASS;
            }
        }
        double put = Math.min(want, capsuleLeft);
        noCapsule = put < want;
        capsuleLeft -= put;
        mass = Math.min(1.0, mass + put);
    }

    private int tickRun(boolean switchedOn, long time) {
        drainHelium();
        drainDeuterium();
        feed();
        mass -= evaporation(mass);
        if (mass < EVAP_MASS) {
            evaporate();
            return 0;
        }
        if (time % 20 == 0 && !perSecond()) {
            return 0;
        }
        return give(switchedOn, outputFor(mass));
    }

    private int tickDrain(boolean switchedOn, long time) {
        drainHelium();
        if (time % 20 == 0) {
            containment = Math.max(0F, Math.min(100F, containment + containmentDelta(heShort, false, ready, START_MASS)));
            if (containment < CONT_ARGON && g.drainPortsSC(GASES[2], ARGON_STOP, true) == ARGON_STOP) {
                g.tellNearSC("sc.chat.sing.argon");                  // argon puts it out softly here too, as while it runs
                endHole(EVENT_SOFT, GeneratorStatus.SOFT_STOP);
                return 0;
            }
            if (containment <= 0F) {
                beginPull();
                return 0;
            }
        }
        phaseTicks--;
        mass = drainFrom * Math.max(0, phaseTicks) / DRAIN_TICKS;
        if (phaseTicks <= 0) {
            returnCharge();
            endHole(EVENT_SOFT, GeneratorStatus.SOFT_STOP);
            g.tellNearSC("sc.chat.sing.drained");
            return 0;
        }
        return give(switchedOn, outputNow());
    }

    /** Energy into the buffer (pushed to the port storages afterwards); nothing while switched off or full. */
    private int give(boolean switchedOn, int eu) {
        if (!switchedOn) {
            g.setStatusSC(g.isPowerOn() ? GeneratorStatus.REDSTONE : GeneratorStatus.DISABLED);
            return 0;
        }
        int room = g.getMaxEnergyStored() - g.getEnergyStored();
        if (room <= 0) {
            g.setStatusSC(GeneratorStatus.BUFFER_FULL);
            return 0;
        }
        int put = Math.min(room, eu);
        g.addEnergySC(put);
        g.setStatusSC(heShort ? GeneratorStatus.NO_COOLANT : dShort ? GeneratorStatus.NO_DEUTERIUM
                : noCapsule ? GeneratorStatus.NO_CAPSULE : GeneratorStatus.GENERATING);
        return put;
    }

    /** Once a second while it runs: containment, warnings, argon, the ejection. @return false: it ended */
    private boolean perSecond() {
        containment = Math.max(0F, Math.min(100F, containment + containmentDelta(heShort, dShort, ready, mass)));
        if (containment < 40F && warnedAt > 40) {
            warnedAt = 40;
            g.tellNearSC("sc.chat.sing.warn40");
        }
        if (containment < 20F && warnedAt > 20) {
            warnedAt = 20;
            g.tellNearSC("sc.chat.sing.warn20");
        }
        if (containment > 45F) {
            warnedAt = 100;
        }
        if (mass < LIGHT && !warnedLight) {
            warnedLight = true;
            g.tellNearSC("sc.chat.sing.light");
        } else if (mass > LIGHT + 0.05) {
            warnedLight = false;
        }
        if (containment < CONT_ARGON && g.drainPortsSC(GASES[2], ARGON_STOP, true) == ARGON_STOP) {
            g.tellNearSC("sc.chat.sing.argon");
            endHole(EVENT_SOFT, GeneratorStatus.SOFT_STOP);
            return false;
        }
        if (containment <= 0F) {
            beginPull();
            return false;
        }
        g.markDirty();
        return true;
    }

    /** Containment lost: 5 s of pull before the flash. */
    private void beginPull() {
        phase = PHASE_PULL;
        phaseTicks = PULL_TICKS;
        g.setIgnitedSC(false);
        g.setStatusSC(GeneratorStatus.DISRUPTED);
        g.tellNearSC("sc.chat.sing.eject");
        sound("mob.wither.spawn", 1.5F, 0.5F);
        g.markDirty();
    }

    private int tickPull() {
        g.setStatusSC(GeneratorStatus.DISRUPTED);
        pull();
        if (--phaseTicks > 0) {
            return 0;
        }
        burst(EJECT_LEVEL, EJECT_RADIUS);
        knockCoils(2 + g.getWorldObj().rand.nextInt(3));
        g.tellNearSC("sc.chat.sing.ejected");
        endHole(EVENT_EJECT, GeneratorStatus.DISRUPTED);
        return 0;
    }

    /** Items, mobs and players within PULL_RADIUS drift toward the hole - softly, no harm. */
    private void pull() {
        World w = g.getWorldObj();
        double cx = g.xCoord + 0.5, cy = g.yCoord + 0.5, cz = g.zCoord + 0.5;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - PULL_RADIUS, cy - PULL_RADIUS, cz - PULL_RADIUS,
                cx + PULL_RADIUS, cy + PULL_RADIUS, cz + PULL_RADIUS);
        for (Object o : w.getEntitiesWithinAABB(Entity.class, box)) {
            Entity e = (Entity) o;
            if (!(e instanceof EntityItem) && !(e instanceof EntityLivingBase)) {
                continue;
            }
            if (e instanceof net.minecraft.entity.player.EntityPlayer && (((net.minecraft.entity.player.EntityPlayer) e).capabilities.isCreativeMode
                    || ((net.minecraft.entity.player.EntityPlayer) e).capabilities.isFlying)) {
                continue;                                            // creative or flying: left alone
            }
            double dx = cx - e.posX, dy = cy - e.posY, dz = cz - e.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > PULL_RADIUS || d < 0.8) {
                continue;
            }
            double k = 0.06 * (1.0 - d / (PULL_RADIUS + 1)) / d;
            e.motionX = clampV(e.motionX + dx * k);
            e.motionY = clampV(e.motionY + dy * k + 0.02);
            e.motionZ = clampV(e.motionZ + dz * k);
            e.fallDistance = 0F;
            e.velocityChanged = true;
        }
    }

    private static double clampV(double v) {
        return Math.max(-0.6, Math.min(0.6, v));
    }

    /** The mass is gone: a flash 32 blocks wide for a minute, the coils whole. */
    private void evaporate() {
        burst(EVAP_LEVEL, EVAP_RADIUS);
        g.tellNearSC("sc.chat.sing.evap");
        endHole(EVENT_EVAP, GeneratorStatus.DISRUPTED);
    }

    private void burst(float level, int radius) {
        burstLevel = level;
        burstRadius = radius;
        burstTicks = BURST_TICKS;
        sound("random.explode", 2F, 0.5F);
        World w = g.getWorldObj();
        if (w instanceof net.minecraft.world.WorldServer) {          // the server's spawnParticle reaches nobody: send them out
            ((net.minecraft.world.WorldServer) w).func_147487_a("portal", g.xCoord + 0.5, g.yCoord + 0.5, g.zCoord + 0.5,
                    96, 1.5, 1.5, 1.5, 1.0);
        }
    }

    /** Coils thrown out of the ring as items (never destroyed). */
    private void knockCoils(int n) {
        World w = g.getWorldObj();
        List<int[]> found = new ArrayList<int[]>();
        Block coil = com.sc.init.ModBlocks.gravityCoil;
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dz != 0) && w.getBlock(g.xCoord + dx, g.yCoord + dy, g.zCoord + dz) == coil) {
                        found.add(new int[]{g.xCoord + dx, g.yCoord + dy, g.zCoord + dz});
                    }
                }
            }
        }
        java.util.Collections.shuffle(found, w.rand);
        for (int i = 0; i < n && i < found.size(); i++) {
            int[] c = found.get(i);
            w.func_147480_a(c[0], c[1], c[2], true);              // broken, dropped as an item
        }
    }

    /** The hole is gone (or put out): the reactor dark, the latch set. */
    private void endHole(int why, GeneratorStatus status) {
        phase = PHASE_IDLE;
        phaseTicks = 0;
        event = why;
        mass = 0;
        capsuleLeft = 0;
        containment = 100F;
        heShort = dShort = noCapsule = false;
        g.setIgnitedSC(false);
        g.setStatusSC(status);
        g.markDirty();
    }

    private void sound(String name, float volume, float pitch) {
        g.getWorldObj().playSoundEffect(g.xCoord + 0.5, g.yCoord + 0.5, g.zCoord + 0.5, name, volume, pitch);
    }

    // ------------------------------------------------------------------ the port storages

    /** The charge out of the port storages, CHARGE_PER_TICK at most a tick over all of them. */
    private void chargeFromPorts() {
        long need = Math.min(CHARGE_PER_TICK, IGNITION_EU - g.getIgnitionEU());
        World w = g.getWorldObj();
        for (int[] p : g.storePortsSC()) {
            if (need <= 0) {
                return;
            }
            if (!w.blockExists(p[0], p[1], p[2])) {
                continue;
            }
            TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC && ((TileEntityEnergyStorageSC) te).isPowerOn()) {
                int took = ((TileEntityEnergyStorageSC) te).extractForItemCharging((int) need);
                if (took > 0) {
                    g.setIgnitionEUSC(g.getIgnitionEU() + took);
                    need -= took;
                    te.markDirty();
                }
            }
        }
    }

    /** A fifth of the charge back into the port storages (what doesn't fit is lost). */
    private void returnCharge() {
        long left = Math.round(IGNITION_EU * RETURN_SHARE);
        World w = g.getWorldObj();
        for (int[] p : g.storePortsSC()) {
            if (left <= 0 || !w.blockExists(p[0], p[1], p[2])) {
                continue;
            }
            TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC) {
                TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
                while (left > 0) {
                    int took = s.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, s.inputTier().getVoltage(),
                            (int) Math.min(Integer.MAX_VALUE, left), false);
                    if (took <= 0) {
                        break;
                    }
                    left -= took;
                }
                s.markDirty();
            }
        }
    }

    // ------------------------------------------------------------------ the scan

    private static boolean isShell(Block b) {
        return b == com.sc.init.ModBlocks.leadBlock || b == com.sc.init.ModBlocks.leadGlass;
    }

    /** Once a second: the 7x7x5 round the reactor. */
    private void scan() {
        World w = g.getWorldObj();
        int x0 = g.xCoord, y0 = g.yCoord, z0 = g.zCoord;
        if (!w.checkChunksExist(x0 - 3, y0 - 2, z0 - 3, x0 + 3, y0 + 2, z0 + 3)) {
            frozen = true;
            return;
        }
        frozen = false;
        List<int[]> tankPorts = g.tankPortsSC(), storePorts = g.storePortsSC();
        tankPorts.clear();
        storePorts.clear();
        int caps = 0, coilBits = 0, nTanks = 0, nStores = 0, nWeak = 0, free = 0;
        long top = 0, bottom = 0;
        int[] wl = new int[3], pt = new int[3], jk = new int[3], cap3 = new int[3], cnt3 = new int[3];
        boolean taken = false;
        Block coil = com.sc.init.ModBlocks.gravityCoil;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy += 4) {
                    if (!isShell(w.getBlock(x0 + dx, y0 + dy, z0 + dz))) {
                        caps++;
                    } else if (dy > 0) {
                        top |= 1L << ((dz + 3) * 7 + dx + 3);
                    } else {
                        bottom |= 1L << ((dz + 3) * 7 + dx + 3);
                    }
                }
            }
        }
        for (int l = 0; l < 3; l++) {
            int dy = l - 1, y = y0 + dy;
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int x = x0 + dx, z = z0 + dz, role = cellRole(dx, dy, dz);
                    if (role == 4) {
                        continue;
                    }
                    if (role == 2) {
                        if (w.getBlock(x, y, z) == coil) {
                            coilBits |= 1 << (ringIndex(dx, dz) + (dy > 0 ? 8 : 0));
                        }
                        continue;
                    }
                    if (role == 3) {
                        if (!w.isAirBlock(x, y, z)) {
                            jk[l] |= 1 << ((dz + 2) * 5 + dx + 2);
                        }
                        continue;
                    }
                    int wi = wallIndex(dx, dz), cell = l * 24 + wi;
                    labels[cell] = LABEL_NONE;
                    TileEntity te = w.getTileEntity(x, y, z);
                    boolean store = te instanceof TileEntityEnergyStorageSC && !(te instanceof TileEntityChargePadSC);
                    if (isShell(w.getBlock(x, y, z))) {
                        wl[l] |= 1 << wi;
                        portMem[cell] = null;
                    } else if ((te instanceof TileEntityTankSC || store) && g.portHeldByOtherSC(x, y, z)) {
                        taken = true;                                 // another build's port: a gap here
                        portMem[cell] = null;
                    } else if (te instanceof TileEntityTankSC && nTanks < TANKS_MAX) {
                        wl[l] |= 1 << wi;
                        pt[l] |= 1 << wi;
                        nTanks++;
                        tankPorts.add(new int[]{x, y, z});
                        FluidTank t = ((TileEntityTankSC) te).getTank();
                        FluidStack f = t.getFluid();
                        if (f != null && f.amount > 0 && f.getFluid() != null) {
                            portMem[cell] = f.getFluid().getName();
                        }
                        int gas = portMem[cell] == null ? -1 : java.util.Arrays.asList(GASES).indexOf(portMem[cell]);
                        if (gas >= 0) {
                            cap3[gas] += t.getCapacity();
                            cnt3[gas]++;
                            labels[cell] = gas + 1;
                        } else if (portMem[cell] == null) {
                            free++;
                            labels[cell] = LABEL_FREE;
                        } else {
                            labels[cell] = LABEL_OTHER;
                        }
                    } else if (store && nStores < STORES_MAX) {
                        wl[l] |= 1 << wi;
                        pt[l] |= 1 << wi;
                        portMem[cell] = null;
                        labels[cell] = LABEL_STORE;
                        nStores++;
                        if (((TileEntityEnergyStorageSC) te).getTier().ordinal() < Tier.IV.ordinal()) {
                            nWeak++;
                        }
                        storePorts.add(new int[]{x, y, z});
                    } else {
                        portMem[cell] = null;
                    }
                }
            }
        }
        long have = 0, room = 0;
        for (int[] p : storePorts) {
            TileEntity te = w.getTileEntity(p[0], p[1], p[2]);
            if (te instanceof TileEntityEnergyStorageSC) {
                have += ((TileEntityEnergyStorageSC) te).getEnergyStored();
                room += ((TileEntityEnergyStorageSC) te).getMaxEnergyStored();
            }
        }
        for (int i = 0; i < 3; i++) {
            walls[i] = wl[i];
            ports[i] = pt[i];
            junk[i] = jk[i];
            portCap[i] = cap3[i];
            portCount[i] = cnt3[i];
        }
        coils = coilBits;
        capMissing = caps;
        capTop = top;
        capBottom = bottom;
        tanks = nTanks;
        stores = nStores;
        weak = nWeak;
        freeTanks = free;
        storeHave = have;
        storeRoom = room;
        portTaken = taken;
        g.holdPortsSC();
        ready = coilBits == 0xFFFF && wl[0] == 0xFFFFFF && wl[1] == 0xFFFFFF && wl[2] == 0xFFFFFF && caps == 0
                && jk[0] == 0 && jk[1] == 0 && jk[2] == 0 && nStores > 0 && nWeak == 0;
        for (int i = 0; i < 3; i++) {
            portFluid[i] = g.portAmountSC(GASES[i]);
        }
    }

    // ------------------------------------------------------------------ the screen's sync

    /** Ints the screen gets (see sync / setClient). */
    public static final int SYNC = 58;

    public int[] sync() {
        int[] v = new int[SYNC];
        v[0] = phase | event << 3 | feedMode << 5 | (auto ? 128 : 0) | (ready ? 256 : 0) | (heShort ? 512 : 0) | (dShort ? 1024 : 0)
                | (noCapsule ? 2048 : 0) | (portTaken ? 4096 : 0) | (frozen ? 8192 : 0);
        v[1] = (int) Math.round(mass * 1000000);
        v[2] = Math.round(containment * 100);
        v[3] = phaseTicks;
        v[4] = burstTicks;
        v[5] = burstRadius | Math.round(burstLevel * 10) << 8;
        for (int i = 0; i < 3; i++) {
            v[6 + i] = walls[i];
            v[9 + i] = ports[i];
            v[12 + i] = junk[i];
            v[15 + i] = portCap[i];
            v[18 + i] = portFluid[i];
        }
        v[21] = coils;
        v[22] = (int) (capTop & 0x1FFFFFF);
        v[23] = (int) (capTop >>> 25);
        v[24] = (int) (capBottom & 0x1FFFFFF);
        v[25] = (int) (capBottom >>> 25);
        for (int i = 0; i < 9; i++) {
            int p = 0;
            for (int k = 0; k < 8; k++) {
                p |= (labels[i * 8 + k] & 7) << (k * 3);
            }
            v[26 + i] = p;
        }
        v[35] = Math.min(15, portCount[0]) | Math.min(15, portCount[1]) << 4 | Math.min(15, portCount[2]) << 8 | Math.min(15, freeTanks) << 12
                | Math.min(15, tanks) << 16 | Math.min(7, stores) << 20 | Math.min(7, weak) << 23;
        v[36] = (int) Math.min(Integer.MAX_VALUE, storeHave / 1000);
        v[37] = (int) Math.min(Integer.MAX_VALUE, storeRoom / 1000);
        v[38] = (int) Math.round(capsuleLeft * 1000000);
        for (int i = 0; i < 15; i++) {
            v[39 + i] = (hist[i * 4] & 255) | (hist[i * 4 + 1] & 255) << 8 | (hist[i * 4 + 2] & 255) << 16 | (hist[i * 4 + 3] & 255) << 24;
        }
        v[54] = histHead;
        v[55] = Math.min(255, capMissing);
        v[56] = (int) Math.round(drainFrom * 1000000);
        v[57] = warnedAt;
        return v;
    }

    public void setClient(int[] v) {
        if (v.length < SYNC) {
            return;
        }
        phase = v[0] & 7;
        event = v[0] >> 3 & 3;
        feedMode = v[0] >> 5 & 3;
        auto = (v[0] & 128) != 0;
        ready = (v[0] & 256) != 0;
        heShort = (v[0] & 512) != 0;
        dShort = (v[0] & 1024) != 0;
        noCapsule = (v[0] & 2048) != 0;
        portTaken = (v[0] & 4096) != 0;
        frozen = (v[0] & 8192) != 0;
        mass = v[1] / 1000000.0;
        containment = v[2] / 100F;
        phaseTicks = v[3];
        burstTicks = v[4];
        burstRadius = v[5] & 255;
        burstLevel = (v[5] >>> 8) / 10F;
        for (int i = 0; i < 3; i++) {
            walls[i] = v[6 + i];
            ports[i] = v[9 + i];
            junk[i] = v[12 + i];
            portCap[i] = v[15 + i];
            portFluid[i] = v[18 + i];
        }
        coils = v[21];
        capTop = (v[22] & 0x1FFFFFFL) | (long) v[23] << 25;
        capBottom = (v[24] & 0x1FFFFFFL) | (long) v[25] << 25;
        for (int i = 0; i < 9; i++) {
            for (int k = 0; k < 8; k++) {
                labels[i * 8 + k] = v[26 + i] >> (k * 3) & 7;
            }
        }
        portCount[0] = v[35] & 15;
        portCount[1] = v[35] >> 4 & 15;
        portCount[2] = v[35] >> 8 & 15;
        freeTanks = v[35] >> 12 & 15;
        tanks = v[35] >> 16 & 15;
        stores = v[35] >> 20 & 7;
        weak = v[35] >> 23 & 7;
        storeHave = v[36] * 1000L;
        storeRoom = v[37] * 1000L;
        capsuleLeft = v[38] / 1000000.0;
        for (int i = 0; i < 15; i++) {
            for (int k = 0; k < 4; k++) {
                hist[i * 4 + k] = (byte) (v[39 + i] >>> (k * 8));
            }
        }
        histHead = v[54];
        capMissing = v[55];
        drainFrom = v[56] / 1000000.0;
        warnedAt = v[57];
    }

    // ------------------------------------------------------------------ NBT

    void write(NBTTagCompound nbt) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Phase", phase);
        t.setInteger("PhaseTicks", phaseTicks);
        t.setInteger("Event", event);
        t.setDouble("Mass", mass);
        t.setDouble("Capsule", capsuleLeft);
        t.setDouble("DrainFrom", drainFrom);
        t.setFloat("Containment", containment);
        t.setInteger("Feed", feedMode);
        t.setBoolean("Auto", auto);
        t.setInteger("Burst", burstTicks);
        t.setInteger("BurstR", burstRadius);
        t.setFloat("BurstL", burstLevel);
        t.setDouble("HeDebt", heDebt);
        t.setDouble("DDebt", dDebt);
        t.setInteger("WarnedAt", warnedAt);
        NBTTagCompound mem = new NBTTagCompound();
        for (int i = 0; i < WALL_CELLS; i++) {
            if (portMem[i] != null) {
                mem.setString("w" + i, portMem[i]);
            }
        }
        t.setTag("PortMem", mem);
        nbt.setTag("Singular", t);
    }

    void read(NBTTagCompound nbt) {
        NBTTagCompound t = nbt.getCompoundTag("Singular");
        phase = Math.max(0, Math.min(PHASE_PULL, t.getInteger("Phase")));
        phaseTicks = Math.max(0, t.getInteger("PhaseTicks"));
        event = Math.max(0, Math.min(EVENT_EVAP, t.getInteger("Event")));
        mass = Math.max(0, Math.min(1, t.getDouble("Mass")));
        capsuleLeft = Math.max(0, Math.min(CAPSULE_MASS * 2, t.getDouble("Capsule")));
        drainFrom = t.getDouble("DrainFrom");
        containment = t.hasKey("Containment") ? Math.max(0F, Math.min(100F, t.getFloat("Containment"))) : 100F;
        feedMode = t.hasKey("Feed") ? Math.max(0, Math.min(2, t.getInteger("Feed"))) : MODE_NORMAL;
        auto = !t.hasKey("Auto") || t.getBoolean("Auto");
        burstTicks = Math.max(0, Math.min(BURST_TICKS, t.getInteger("Burst")));
        burstRadius = t.getInteger("BurstR");
        burstLevel = t.getFloat("BurstL");
        heDebt = t.getDouble("HeDebt");
        dDebt = t.getDouble("DDebt");
        warnedAt = t.hasKey("WarnedAt") ? t.getInteger("WarnedAt") : 100;
        NBTTagCompound mem = t.getCompoundTag("PortMem");
        for (int i = 0; i < WALL_CELLS; i++) {
            portMem[i] = mem.hasKey("w" + i) ? mem.getString("w" + i) : null;
        }
    }

    // ------------------------------------------------------------------ tests

    /** Tests: the hole at this mass straight away (as if born), the charge in. */
    public void setRunningForTest(double m) {
        phase = PHASE_RUN;
        mass = m;
        containment = 100F;
        warnedAt = 100;
        event = EVENT_NONE;
        g.setIgnitedSC(true);
    }

    public void setMassForTest(double m) {
        mass = m;
    }

    public void setContainmentForTest(float c) {
        containment = c;
    }

    /** Tests: the compression shortened to this many ticks. */
    public void shortenCompressionForTest(int ticks) {
        if (phase == PHASE_COMPRESS) {
            phaseTicks = Math.min(phaseTicks, ticks);
        }
    }

    public void shortenDrainForTest(int ticks) {
        if (phase == PHASE_DRAIN) {
            phaseTicks = Math.min(phaseTicks, ticks);
        }
    }
}
