package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.item.ItemArmorSC;
import com.sc.machine.UpgradeType;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.SingularStationMath;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;

/**
 * The Singular Service Station (SV, docs/plan-singular-armor.md §7): everything of the Armour
 * Service Station (four armour slots, eight gas tanks - singular matter's SM_TANK mB - charging, filling
 * the suit of whoever stands on it, modules, pipes, buckets, windows) plus:
 *  - modernisation (ПР1): every Singular piece in the slots that is ready (points full, a task of the
 *    next level done by the player who presses the button) goes up a level at once, for the cost in
 *    SingularStationMath (ПР3 set discount, Ф8 resonance), drawn as the progress grows (ПР4: nothing
 *    there - it waits); its slots are locked (ПР6); «Отменить» gives back half of what was drawn;
 *    4 -> 5 consumes a Singular core in the catalyst slot, its charge counts toward the EU;
 *  - Ф3 branch change of the chestplate (BRANCH_SM mB of singular matter), Ф4 level transfer from the
 *    donor slot to a level-1 piece of the same type, Ф5 sync of lagging pieces, the colour scheme;
 *  - speed: gravitational stabilisers within STAB_RADIUS (same Y +-1; up to 4, +25% each) and a
 *    running Singular reactor within RES_RADIUS (+30%, -10% EU).
 * Charging can be switched off. Placed switched off; the item keeps energy, modules, tanks, settings;
 * breaking it (or the wrench) cancels a running process first by the 50% rule.
 */
public class TileEntitySingularStationSC extends TileEntityArmorStationSC {

    /** The two extra slots after the module slots: the donor piece (Ф4) and the catalyst (Singular core). */
    public static final int DONOR_SLOT = ALL_SLOTS, CATALYST_SLOT = ALL_SLOTS + 1, SING_SLOTS = ALL_SLOTS + 2;
    /** The donor slot's bit in a process mask. */
    public static final int DONOR_BIT = 4;
    /** Singular matter's tank, mB (+SM_PER_EXTENSION per Tank Extension). */
    public static final int SM_TANK = 4000, SM_PER_EXTENSION = 2000;
    /** Stabilisers count within this many blocks across, at the station's Y +- STAB_DY. Resonance: a running reactor within RES_RADIUS. */
    public static final int STAB_RADIUS = 3, STAB_DY = 1, RES_RADIUS = 16, SCAN_EVERY = 20;

    private final ItemStack[] extra = new ItemStack[2];
    private boolean chargeOn = true;
    private SingularProcessSC proc;
    /** Last scan: stabilisers counted (offsets dx, dy, dz each), resonance. */
    private int stabilisers;
    private byte[] stabOffsets = new byte[0];
    private boolean resonance;
    /** Last process tick: resources that held it back (bit per SingularStationMath.R_*), switched off. */
    private int shortMask;
    private boolean pausedOff;
    /** What the clients were last told (working, stabilisers). */
    private boolean sentWorking;
    private byte[] sentStab;
    private long lastScan = Long.MIN_VALUE;
    /** Clients: the process runs (rings fast, hologram, beams); the hologram's pieces. */
    private boolean workingClient;
    private final ItemStack[] holo = new ItemStack[4];

    public TileEntitySingularStationSC() {
        super();
        setTier(Tier.SV);
    }

    // ------------------------------------------------------------------ state

    public SingularProcessSC getProcess() {
        return proc;
    }

    public boolean isChargeOn() {
        return chargeOn;
    }

    public void toggleCharge() {
        chargeOn = !chargeOn;
        markDirty();
    }

    public int getStabilisers() {
        return stabilisers;
    }

    public boolean hasResonance() {
        return resonance;
    }

    public int getShortMask() {
        return shortMask;
    }

    public boolean isPausedOff() {
        return pausedOff;
    }

    public double speed() {
        return SingularStationMath.speed(stabilisers, resonance);
    }

    /** A process runs and the station is switched on (the look: rings fast, glow, particles). */
    public boolean isWorking() {
        return worldObj != null && worldObj.isRemote ? workingClient : proc != null && switchedOn();
    }

    public byte[] stabiliserOffsets() {
        return stabOffsets;
    }

    public ItemStack holoPiece(int i) {
        return holo[i];
    }

    /** Ticks left at the present speed (0: no process). */
    public int ticksLeft() {
        if (proc == null) {
            return 0;
        }
        return SingularStationMath.duration((int) Math.ceil(proc.baseTicks * (1.0 - proc.progress)), speed());
    }

    /** "~3 мин" / "45 с" for the screen and WAILA. */
    public static String timeText(int ticks) {
        int s = (ticks + 19) / 20;
        return s >= 120 ? com.sc.manual.Lang.tr("sc.singStation.time.min", (s + 59) / 60) : com.sc.manual.Lang.tr("sc.singStation.time.sec", s);
    }

    /** Clients (the screen's sync). */
    public void setSingularClient(int stab, boolean res, int shortBits, boolean off, boolean charge) {
        stabilisers = stab;
        resonance = res;
        shortMask = shortBits;
        pausedOff = off;
        chargeOn = charge;
    }

    /** Clients: the process as the screen's sync sends it (null: none). */
    public void setProcessClient(SingularProcessSC p) {
        proc = p;
    }

    /** A slot a running process holds (the pieces it works on, the donor, the catalyst). */
    public boolean isLocked(int slot) {
        if (proc == null) {
            return false;
        }
        if (slot >= 0 && slot < SLOTS) {
            return proc.locks(slot);
        }
        return slot == DONOR_SLOT ? proc.locks(DONOR_BIT) : slot == CATALYST_SLOT;
    }

    // ------------------------------------------------------------------ tanks, energy, charging

    @Override
    public int tankCapacity(Gas g) {
        if (g == Gas.SINGULAR_MATTER) {
            return SM_TANK + Math.min(UpgradeType.MAX_TANK_UPGRADES, upgradeCount(UpgradeType.TANK_EXTENSION)) * SM_PER_EXTENSION;
        }
        return tankCapacity();
    }

    @Override
    public int chargePiece(ItemStack s, int max) {
        return chargeOn ? super.chargePiece(s, max) : 0;
    }

    @Override
    public String getInventoryName() {
        return "tile.siliconage.singularStation.name";
    }

    // ------------------------------------------------------------------ the extra slots

    public static boolean isCore(ItemStack s) {
        return s != null && s.getItem() instanceof com.sc.item.ItemBatterySC && s.getItemDamage() == SingularStationMath.CORE_META;
    }

    /** What the donor slot takes: a Singular piece; the catalyst slot: a Singular core. */
    public static boolean fitsExtra(int slot, ItemStack s) {
        if (slot == DONOR_SLOT) {
            return SingularLevel.isSingular(s);
        }
        return slot == CATALYST_SLOT && isCore(s);
    }

    @Override
    public int getSizeInventory() {
        return SING_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= ALL_SLOTS && slot < SING_SLOTS ? extra[slot - ALL_SLOTS] : super.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < ALL_SLOTS) {
            return super.decrStackSize(slot, amount);
        }
        ItemStack s = extra[slot - ALL_SLOTS];
        if (s == null) {
            return null;
        }
        ItemStack out = s.splitStack(Math.min(amount, s.stackSize));
        if (s.stackSize <= 0) {
            extra[slot - ALL_SLOTS] = null;
        }
        markDirty();
        return out;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < ALL_SLOTS) {
            super.setInventorySlotContents(slot, stack);
            return;
        }
        extra[slot - ALL_SLOTS] = stack;
        markDirty();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (isLocked(slot)) {
            return false;
        }
        return slot >= ALL_SLOTS ? fitsExtra(slot, stack) : super.isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return !isLocked(slot) && super.canInsertItem(slot, stack, side);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return !isLocked(slot) && super.canExtractItem(slot, stack, side);
    }

    @Override
    public java.util.List<ItemStack> takeLooseContents() {
        java.util.List<ItemStack> out = super.takeLooseContents();
        for (int i = 0; i < extra.length; i++) {
            if (extra[i] != null) {
                out.add(extra[i]);
                extra[i] = null;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the work

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        long now = worldObj.getTotalWorldTime();
        if (lastScan == Long.MIN_VALUE || now - lastScan >= (proc != null ? SCAN_EVERY : 5 * SCAN_EVERY) || now < lastScan) {
            lastScan = now;
            scan();
        }
        processTick();
        boolean w = isWorking();
        if (w != sentWorking || sentStab == null || !java.util.Arrays.equals(sentStab, stabOffsets)) {
            sentWorking = w;
            sentStab = stabOffsets.clone();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** Counts the stabilisers round the station (up to 4) and looks for a running Singular reactor. */
    public void scan() {
        java.util.List<int[]> found = new java.util.ArrayList<int[]>();
        for (int dy = -STAB_DY; dy <= STAB_DY && found.size() < SingularStationMath.MAX_STABILISERS; dy++) {
            for (int dx = -STAB_RADIUS; dx <= STAB_RADIUS && found.size() < SingularStationMath.MAX_STABILISERS; dx++) {
                for (int dz = -STAB_RADIUS; dz <= STAB_RADIUS && found.size() < SingularStationMath.MAX_STABILISERS; dz++) {
                    if ((dx != 0 || dz != 0 || dy != 0) && worldObj.blockExists(xCoord + dx, yCoord + dy, zCoord + dz)
                            && worldObj.getBlock(xCoord + dx, yCoord + dy, zCoord + dz) == com.sc.init.ModBlocks.gravStabiliser) {
                        found.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        byte[] off = new byte[found.size() * 3];
        for (int i = 0; i < found.size(); i++) {
            off[i * 3] = (byte) found.get(i)[0];
            off[i * 3 + 1] = (byte) found.get(i)[1];
            off[i * 3 + 2] = (byte) found.get(i)[2];
        }
        stabOffsets = off;
        stabilisers = found.size();
        resonance = com.sc.item.SingularSensesSC.sourceNear(worldObj, xCoord, yCoord, zCoord, RES_RADIUS) == 2;
    }

    /** Test hook: the counted stabilisers and resonance, without a world scan. */
    public void setScanForTest(int stab, boolean res) {
        stabilisers = Math.max(0, Math.min(SingularStationMath.MAX_STABILISERS, stab));
        resonance = res;
    }

    /** ПР4: one tick of the process - the resources the next step needs are drawn; a short one holds the progress. */
    private void processTick() {
        if (proc == null) {
            shortMask = 0;
            pausedOff = false;
            return;
        }
        if (!switchedOn()) {
            pausedOff = true;
            return;
        }
        pausedOff = false;
        double pNew = Math.min(1.0, proc.progress + speed() / Math.max(1, proc.baseTicks));
        long[] need = SingularStationMath.needFor(proc.cost, proc.drawn, pNew);
        if (need[SingularStationMath.R_EU] > 0) {
            int take = (int) Math.min(need[SingularStationMath.R_EU], getEnergyStored());
            if (take > 0) {
                removeEnergy(take);
                proc.drawn[SingularStationMath.R_EU] += take;
            }
        }
        for (int r = 1; r < SingularStationMath.RESOURCES; r++) {
            Gas g = SingularStationMath.GAS[r];
            int take = (int) Math.min(need[r], tankAmount(g));
            if (take > 0) {
                getTank(g).drain(take, true);
                proc.drawn[r] += take;
            }
        }
        long[] still = SingularStationMath.needFor(proc.cost, proc.drawn, pNew);
        int bits = 0;
        for (int r = 0; r < SingularStationMath.RESOURCES; r++) {
            bits |= still[r] > 0 ? 1 << r : 0;
        }
        shortMask = bits;
        proc.progress = Math.max(proc.progress, SingularStationMath.progressOf(proc.cost, proc.drawn, pNew));
        if (proc.progress >= 1.0 && SingularStationMath.progressOf(proc.cost, proc.drawn, 1.0) >= 1.0) {
            complete();
        } else if (worldObj.getTotalWorldTime() % 20 == 0) {
            markDirty();
        }
    }

    // ------------------------------------------------------------------ starting

    /** The levels the modernisation would take: slot i's level if the piece is ready for this player, else 0. */
    public int[] readyLevels(EntityPlayer p) {
        int[] lv = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = getStackInSlot(i);
            if (SingularLevel.readyToUpgrade(p, s)) {
                lv[i] = SingularLevel.levelOf(s);
            }
        }
        return lv;
    }

    /** ПР1: the button. @return null when it started, else the lang key of why not */
    public String startModernise(EntityPlayer p) {
        if (proc != null) {
            return "sc.singStation.err.busy";
        }
        int[] lv = readyLevels(p);
        if (SingularStationMath.pieces(lv) == 0) {
            return "sc.singStation.err.noready";
        }
        return startModerniseFor(lv, p == null ? "" : p.getCommandSenderName());
    }

    /** Starts the modernisation of the pieces at levels `lv` (0: not taking part) - the readiness already checked. */
    public String startModerniseFor(int[] lv, String starter) {
        if (proc != null) {
            return "sc.singStation.err.busy";
        }
        int mask = 0;
        for (int i = 0; i < SLOTS && i < lv.length; i++) {
            ItemStack s = getStackInSlot(i);
            if (lv[i] >= 1 && lv[i] <= 4 && SingularLevel.isSingular(s) && SingularLevel.levelOf(s) == lv[i]) {
                mask |= 1 << i;
            }
        }
        if (mask == 0) {
            return "sc.singStation.err.noready";
        }
        boolean cat = SingularStationMath.needsCatalyst(lv);
        if (cat && !isCore(extra[1])) {
            return "sc.singStation.err.nocatalyst";
        }
        SingularProcessSC p = new SingularProcessSC();
        p.kind = SingularProcessSC.KIND_MODERNISE;
        p.mask = mask;
        p.resonance = resonance;
        long[] cost = SingularStationMath.moderniseCost(lv, resonance);
        System.arraycopy(cost, 0, p.cost, 0, cost.length);
        p.baseTicks = Math.max(1, SingularStationMath.moderniseTicks(lv));
        for (int i = 0; i < SLOTS; i++) {
            p.levels[i] = lv[i];
        }
        p.starter = starter == null ? "" : starter;
        if (cat) {                                          // the core is used up; its charge pays the EU first
            long eu = Math.min(com.sc.item.ItemBatterySC.chargeOf(extra[1]), p.cost[SingularStationMath.R_EU]);
            p.catalystEu = eu;
            p.drawn[SingularStationMath.R_EU] = eu;
            extra[1] = null;
        }
        begin(p);
        return null;
    }

    /** Ф5: the lagging Singular pieces up to the highest one's level, for resources only (no tasks). */
    public String startSync(EntityPlayer p) {
        if (proc != null) {
            return "sc.singStation.err.busy";
        }
        int[] lv = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = getStackInSlot(i);
            lv[i] = SingularLevel.isSingular(s) ? SingularLevel.levelOf(s) : 0;
        }
        int top = SingularStationMath.top(lv), mask = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (lv[i] > 0 && lv[i] < top) {
                mask |= 1 << i;
            }
        }
        if (mask == 0) {
            return "sc.singStation.err.nosync";
        }
        SingularProcessSC q = new SingularProcessSC();
        q.kind = SingularProcessSC.KIND_SYNC;
        q.mask = mask;
        q.target = top;
        long[] cost = SingularStationMath.syncCost(lv);
        System.arraycopy(cost, 0, q.cost, 0, cost.length);
        q.baseTicks = Math.max(1, SingularStationMath.syncTicks(lv));
        System.arraycopy(lv, 0, q.levels, 0, SLOTS);
        q.starter = p == null ? "" : p.getCommandSenderName();
        begin(q);
        return null;
    }

    /** The armour slot the donor's level would go to (its type), or -1 when there is no valid pair. */
    public int transferTarget() {
        ItemStack donor = extra[0];
        if (!SingularLevel.isSingular(donor) || SingularLevel.levelOf(donor) < 2) {
            return -1;
        }
        int t = ((ItemArmorSC) donor.getItem()).armorType;
        ItemStack target = t >= 0 && t < SLOTS ? getStackInSlot(t) : null;
        return SingularLevel.isSingular(target) && SingularLevel.levelOf(target) == SingularLevel.MIN ? t : -1;
    }

    /** Ф4: the donor's level and points go to the level-1 piece of its type in the slots; the donor drops to level 1. */
    public String startTransfer(EntityPlayer p) {
        if (proc != null) {
            return "sc.singStation.err.busy";
        }
        int t = transferTarget();
        if (t < 0) {
            return "sc.singStation.err.notransfer";
        }
        int dl = SingularLevel.levelOf(extra[0]);
        SingularProcessSC q = new SingularProcessSC();
        q.kind = SingularProcessSC.KIND_TRANSFER;
        q.mask = 1 << t | 1 << DONOR_BIT;
        q.target = t;
        long[] cost = SingularStationMath.transferCost(dl);
        System.arraycopy(cost, 0, q.cost, 0, cost.length);
        q.baseTicks = Math.max(1, SingularStationMath.transferTicks(dl));
        q.levels[t] = SingularLevel.MIN;
        q.levels[DONOR_BIT] = dl;
        q.starter = p == null ? "" : p.getCommandSenderName();
        begin(q);
        return null;
    }

    private void begin(SingularProcessSC p) {
        proc = p;
        shortMask = 0;
        lastScan = Long.MIN_VALUE;                         // the speed right away
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1, zCoord + 0.5, "mob.endermen.portal", 0.6F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ cancelling, finishing

    /** «Отменить»: half of what was drawn goes back (EU to the buffer, gases to the tanks, the core's share as a core). */
    public void cancelProcess() {
        if (proc == null) {
            return;
        }
        SingularProcessSC p = proc;
        proc = null;
        long gridEu = p.drawn[SingularStationMath.R_EU] - p.catalystEu;
        long back = SingularStationMath.refund(gridEu);
        if (back > 0) {
            addEnergy((int) Math.min(Integer.MAX_VALUE, back));          // only what fits the buffer
        }
        for (int r = 1; r < SingularStationMath.RESOURCES; r++) {
            long g = SingularStationMath.refund(p.drawn[r]);
            if (g > 0) {
                fillTank(SingularStationMath.GAS[r], (int) Math.min(Integer.MAX_VALUE, g), true);
            }
        }
        if (p.catalystEu > 0 && com.sc.init.ModItems.battery != null) {
            ItemStack core = new ItemStack(com.sc.init.ModItems.battery, 1, SingularStationMath.CORE_META);
            com.sc.item.ItemBatterySC.setCharge(core, SingularStationMath.refund(p.catalystEu));
            if (extra[1] == null) {
                extra[1] = core;
            } else if (worldObj != null && !worldObj.isRemote) {
                worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, core));
            }
        }
        shortMask = 0;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1, zCoord + 0.5, "random.fizz", 0.6F, 0.6F);
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** The process is paid in full: the pieces change, the effects. */
    private void complete() {
        SingularProcessSC p = proc;
        proc = null;
        shortMask = 0;
        EntityPlayer who = p.starter == null || p.starter.isEmpty() ? null : worldObj.getPlayerEntityByName(p.starter);
        if (who != null && who.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) > 64 * 64) {
            who = null;
        }
        if (p.kind == SingularProcessSC.KIND_MODERNISE) {
            int top = 0;
            for (int i = 0; i < SLOTS; i++) {
                ItemStack s = getStackInSlot(i);
                if (p.locks(i) && SingularLevel.isSingular(s) && SingularLevel.levelOf(s) == p.levels[i]) {
                    top = Math.max(top, SingularLevel.applyLevelUp(s));
                }
            }
            if (who != null && top > 0) {
                SingularLevel.levelUpEffects(who, top);
            }
        } else if (p.kind == SingularProcessSC.KIND_SYNC) {
            for (int i = 0; i < SLOTS; i++) {
                ItemStack s = getStackInSlot(i);
                if (p.locks(i) && SingularLevel.isSingular(s) && SingularLevel.levelOf(s) < p.target) {
                    SingularLevel.setLevel(s, p.target);
                    SingularLevel.setPoints(s, 0);
                }
            }
            if (who != null) {
                who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.done.sync", String.valueOf(p.target)));
            }
        } else if (p.kind == SingularProcessSC.KIND_TRANSFER) {
            ItemStack donor = extra[0], target = p.target >= 0 && p.target < SLOTS ? getStackInSlot(p.target) : null;
            if (SingularLevel.isSingular(donor) && SingularLevel.isSingular(target)) {
                transferLevel(donor, target);
                if (who != null) {
                    who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.done.transfer",
                            String.valueOf(SingularLevel.levelOf(target))));
                }
            }
        }
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, "random.levelup", 1F, 0.6F);
        worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, "mob.endermen.portal", 1F, 0.5F);
        if (worldObj instanceof net.minecraft.world.WorldServer) {
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) worldObj;
            ws.func_147487_a("portal", xCoord + 0.5, yCoord + 1.6, zCoord + 0.5, 150, 0.4, 0.6, 0.4, 1.2);
            ws.func_147487_a("witchMagic", xCoord + 0.5, yCoord + 1.6, zCoord + 0.5, 60, 0.5, 0.5, 0.5, 0.2);
        }
        completed++;
    }

    /** Processes finished (the world test counts them). */
    private int completed;

    public int completedCount() {
        return completed;
    }

    /** Ф4 on two pieces: the donor's level, points and branches go to the target; the donor is level 1 again. */
    public static void transferLevel(ItemStack donor, ItemStack target) {
        int lvl = SingularLevel.levelOf(donor), pts = SingularLevel.points(donor);
        int b3 = SingularLevel.branchChoice(donor, 3), b5 = SingularLevel.branchChoice(donor, 5);
        SingularLevel.setLevel(target, lvl);
        SingularLevel.setPoints(target, pts);
        SingularLevel.setBranch(target, 3, b3);
        SingularLevel.setBranch(target, 5, b5);
        SingularLevel.setLevel(donor, SingularLevel.MIN);
        SingularLevel.setPoints(donor, 0);
        SingularLevel.setBranch(donor, 3, SingularLevel.BRANCH_NONE);
        SingularLevel.setBranch(donor, 5, SingularLevel.BRANCH_NONE);
    }

    // ------------------------------------------------------------------ Ф3 branches, the colour scheme

    /** Ф3: the chestplate in the slot takes branch `choice` at `level` (3 / 5) for BRANCH_SM mB of singular matter. */
    public String changeBranch(int level, int choice) {
        ItemStack chest = getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        if (!SingularLevel.isSingular(chest) || (level != 3 && level != 5) || (choice != SingularLevel.BRANCH_A && choice != SingularLevel.BRANCH_B)) {
            return "sc.singStation.err.nochest";
        }
        if (isLocked(com.sc.util.ArmorGasSC.CHEST)) {
            return "sc.singStation.err.busy";
        }
        if (SingularLevel.levelOf(chest) < level) {
            return "sc.singStation.err.branchlevel";
        }
        if (SingularLevel.branchChoice(chest, level) == choice) {
            return "sc.singStation.err.samebranch";
        }
        if (tankAmount(Gas.SINGULAR_MATTER) < SingularStationMath.BRANCH_SM) {
            return "sc.singStation.err.nosm";
        }
        getTank(Gas.SINGULAR_MATTER).drain(SingularStationMath.BRANCH_SM, true);
        SingularLevel.setBranch(chest, level, choice);
        markDirty();
        return null;
    }

    /** The scheme the screen shows: the chestplate's, else the first Singular piece's (null: none in the slots). */
    public SingularScheme shownScheme() {
        int[] order = {com.sc.util.ArmorGasSC.CHEST, com.sc.util.ArmorGasSC.HELMET, com.sc.util.ArmorGasSC.LEGS, com.sc.util.ArmorGasSC.BOOTS};
        for (int i : order) {
            if (SingularLevel.isSingular(getStackInSlot(i))) {
                return SingularScheme.of(getStackInSlot(i));
            }
        }
        return null;
    }

    /** ◄ ►: every Singular piece in the armour slots takes the next scheme (free). @return whether any changed */
    public boolean cycleScheme(int dir) {
        SingularScheme now = shownScheme();
        if (now == null) {
            return false;
        }
        SingularScheme next = SingularStationMath.cycle(now, dir);
        for (int i = 0; i < SLOTS; i++) {
            if (SingularLevel.isSingular(getStackInSlot(i))) {
                SingularScheme.setScheme(getStackInSlot(i), next);
            }
        }
        markDirty();
        return true;
    }

    // ------------------------------------------------------------------ the item, saving, clients

    @Override
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = super.writeToItem();
        if (!chargeOn) {
            nbt.setBoolean("ChargeOff", true);
        }
        return nbt;
    }

    @Override
    public void readFromItem(NBTTagCompound nbt) {
        super.readFromItem(nbt);
        chargeOn = !nbt.getBoolean("ChargeOff");
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setTier(Tier.SV);
        chargeOn = !nbt.getBoolean("ChargeOff");
        extra[0] = null;
        extra[1] = null;
        NBTTagList list = nbt.getTagList("SingItems", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int k = t.getByte("Slot");
            if (k >= 0 && k < extra.length) {
                extra[k] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        proc = nbt.hasKey("SingProc") ? SingularProcessSC.readFromNBT(nbt.getCompoundTag("SingProc")) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("ChargeOff", !chargeOn);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < extra.length; i++) {
            if (extra[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                extra[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        nbt.setTag("SingItems", list);
        if (proc != null) {
            NBTTagCompound t = new NBTTagCompound();
            proc.writeToNBT(t);
            nbt.setTag("SingProc", t);
        }
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        net.minecraft.network.Packet p = super.getDescriptionPacket();
        if (p instanceof net.minecraft.network.play.server.S35PacketUpdateTileEntity) {
            NBTTagCompound nbt = ((net.minecraft.network.play.server.S35PacketUpdateTileEntity) p).func_148857_g();
            boolean w = isWorking();
            nbt.setBoolean("SingWork", w);
            nbt.setByteArray("SingStab", stabOffsets);
            if (w) {                                        // the hologram: the pieces' look only (item, damage, scheme)
                NBTTagList holoList = new NBTTagList();
                for (int i = 0; i < SLOTS; i++) {
                    ItemStack s = getStackInSlot(i);
                    if (s != null) {
                        ItemStack look = new ItemStack(s.getItem(), 1, s.getItemDamage());
                        if (SingularLevel.isSingular(s)) {
                            SingularScheme.setScheme(look, SingularScheme.of(s));
                        }
                        NBTTagCompound t = look.writeToNBT(new NBTTagCompound());
                        t.setByte("Slot", (byte) i);
                        holoList.appendTag(t);
                    }
                }
                nbt.setTag("SingHolo", holoList);
            }
        }
        return p;
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(manager, pkt);
        NBTTagCompound nbt = pkt.func_148857_g();
        boolean was = workingClient;
        workingClient = nbt.getBoolean("SingWork");
        stabOffsets = nbt.getByteArray("SingStab");
        stabilisers = stabOffsets.length / 3;
        java.util.Arrays.fill(holo, null);
        NBTTagList list = nbt.getTagList("SingHolo", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int k = t.getByte("Slot");
            if (k >= 0 && k < holo.length) {
                holo[k] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        if (worldObj != null && was != workingClient) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);   // the lit top
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord - STAB_RADIUS, yCoord - STAB_DY, zCoord - STAB_RADIUS,
                xCoord + STAB_RADIUS + 1, yCoord + 3, zCoord + STAB_RADIUS + 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 96 * 96;
    }
}
