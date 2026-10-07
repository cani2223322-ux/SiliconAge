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
 *    4 -> 5 consumes a Singular core in the catalyst slot at the start (Н-1 В2), its charge counts toward
 *    the EU, the charge above the cost goes into the station's EU buffer (what does not fit is lost);
 *  - Ф3 branch change of the chestplate (BRANCH_SM mB of singular matter), Ф4 level transfer from the
 *    donor slot to a level-1 piece of the same type, Ф5 sync of lagging pieces, the colour scheme;
 *  - speed: gravitational stabilisers within STAB_RADIUS (same Y +-1; up to 4, +25% each) and a
 *    running Singular reactor within RES_RADIUS (+30%, -10% EU).
 *  - the tool slot (docs/plan-singular-tools.md §4): an Exo / Singular blade or drill; it joins the conversion
 *    (convertTool) and the modernisation (its share of the row; the drill's crumbs pay up to half its SM), takes the
 *    scheme with the pieces and its branch re-choice (changeToolBranch, BRANCH_SM);
 *  - Б-1 conversion: the Exo pieces in the armour slots become Singular pieces of level 1, for the
 *    materials in the six material slots - a whole Exo set in one go - (and a Singular core in the catalyst slot) - taken whole at
 *    the start, given back whole on «Отменить» - and the resources of SingularStationMath.convertCost,
 *    drawn as the progress grows like the modernisation (the cores' charge counts toward the EU).
 * Charging can be switched off. Placed switched off; the item keeps energy, modules, tanks, settings;
 * breaking it (or the wrench) cancels a running process first by the 50% rule.
 */
public class TileEntitySingularStationSC extends TileEntityArmorStationSC {

    /**
     * The extra slots after the module slots: the donor piece (Ф4), the catalyst (Singular core), the six conversion
     * materials (Б-1) - 8 donor, 9 catalyst, 10..15 materials. They are saved by index ("SingItems", Slot 0..7 =
     * index - ALL_SLOTS); the materials were 4 (10..13) before, the last two (14, 15) were added at the end, so an
     * older station loads as it was. The tool slot (16, a blade or a drill, docs/plan-singular-tools.md §4) was appended
     * after them the same way (SingItems Slot 8). SING_SLOTS is the whole inventory; the materials end at MATERIAL_END.
     */
    public static final int DONOR_SLOT = ALL_SLOTS, CATALYST_SLOT = ALL_SLOTS + 1, MATERIAL_SLOT = ALL_SLOTS + 2, MATERIAL_SLOTS = 6,
            MATERIAL_END = MATERIAL_SLOT + MATERIAL_SLOTS, TOOL_SLOT = MATERIAL_END, SING_SLOTS = TOOL_SLOT + 1;
    /** The donor slot's and the tool slot's bits in a process mask. */
    public static final int DONOR_BIT = 4, TOOL_BIT = 5;
    /** Singular matter's tank, mB (+SM_PER_EXTENSION per Tank Extension). */
    public static final int SM_TANK = 4000, SM_PER_EXTENSION = 2000;
    /** Stabilisers count within this many blocks across, at the station's Y +- STAB_DY. Resonance: a running reactor within RES_RADIUS. */
    public static final int STAB_RADIUS = 3, STAB_DY = 1, RES_RADIUS = 16, SCAN_EVERY = 20;

    private final ItemStack[] extra = new ItemStack[SING_SLOTS - ALL_SLOTS];
    private boolean chargeOn = true;
    /** С-3: «Инструмент тоже» - the modernisation takes the ready tool in the tool slot along (off: the armour alone). */
    private boolean modTool;
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
    private final ItemStack[] holo = new ItemStack[5];

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

    public boolean isModTool() {
        return modTool;
    }

    public void toggleModTool() {
        modTool = !modTool;
        markDirty();
    }

    /** The tool's level for the modernisation: ready and «Инструмент тоже» on, else 0. */
    public int toolModerniseLevel() {
        return modTool ? toolReadyLevel() : 0;
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
    public void setSingularClient(int stab, boolean res, int shortBits, boolean off, boolean charge, boolean tool) {
        stabilisers = stab;
        resonance = res;
        shortMask = shortBits;
        pausedOff = off;
        chargeOn = charge;
        modTool = tool;
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
        return slot == DONOR_SLOT ? proc.locks(DONOR_BIT) : slot == TOOL_SLOT ? proc.locks(TOOL_BIT) : slot == CATALYST_SLOT;
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

    /**
     * What the donor slot takes: a Singular piece; the catalyst slot: a Singular core; a material slot: a conversion
     * material or Singular crumbs (the drill's modernisation); the tool slot: an Exo or Singular blade or drill.
     */
    public static boolean fitsExtra(int slot, ItemStack s) {
        if (slot == DONOR_SLOT) {
            return SingularLevel.isSingular(s);
        }
        if (slot == TOOL_SLOT) {
            return isStationTool(s);
        }
        if (slot >= MATERIAL_SLOT && slot < MATERIAL_END) {
            return materialKind(s) >= 0 || isCrumb(s);
        }
        return slot == CATALYST_SLOT && isCore(s);
    }

    /** «Крупица сингулярности» - pays part of the drill's SM in its modernisation. */
    public static boolean isCrumb(ItemStack s) {
        return s != null && s.getItem() != null && s.getItem() == com.sc.init.ModItems.singularCrumb;
    }

    /** An Exo blade or drill - the tool conversion's input. */
    public static boolean isExoTool(ItemStack s) {
        return com.sc.item.ItemBladeSC.typeOf(s) == com.sc.util.BladeType.EXO || com.sc.item.ItemDrillSC.typeOf(s) == com.sc.util.DrillType.EXO;
    }

    /** What the tool slot takes: an Exo or Singular blade / drill. */
    public static boolean isStationTool(ItemStack s) {
        return isExoTool(s) || com.sc.util.ToolLevelSC.isSingularTool(s);
    }

    /** SingularStationMath.TOOL_* of a blade / drill (any tier), TOOL_NONE otherwise. */
    public static int toolKindOf(ItemStack s) {
        return com.sc.item.ItemBladeSC.typeOf(s) != null ? SingularStationMath.TOOL_BLADE
                : com.sc.item.ItemDrillSC.typeOf(s) != null ? SingularStationMath.TOOL_DRILL : SingularStationMath.TOOL_NONE;
    }

    public ItemStack getTool() {
        return extra[TOOL_SLOT - ALL_SLOTS];
    }

    /** The Exo tool the conversion would take (TOOL_*), TOOL_NONE when the slot holds none. */
    public int convertTool() {
        ItemStack t = getTool();
        return isExoTool(t) ? toolKindOf(t) : SingularStationMath.TOOL_NONE;
    }

    /** The Singular tool's level when it is ready for the modernisation (points full, under 5), else 0. */
    public int toolReadyLevel() {
        ItemStack t = getTool();
        return com.sc.util.ToolLevelSC.readyToUpgrade(t) ? com.sc.util.ToolLevelSC.levelOf(t) : 0;
    }

    /** Singular crumbs in the material slots. */
    public int crumbsHave() {
        int n = 0;
        for (int slot = MATERIAL_SLOT; slot < MATERIAL_END; slot++) {
            ItemStack s = getStackInSlot(slot);
            n += isCrumb(s) ? s.stackSize : 0;
        }
        return n;
    }

    /** Crumbs the drill's modernisation from `toolLevel` would take now (a blade / no tool: 0). */
    public int crumbsFor(int toolLevel) {
        if (toolKindOf(getTool()) != SingularStationMath.TOOL_DRILL || toolLevel <= 0) {
            return 0;
        }
        long sm = SingularStationMath.toolModerniseCost(SingularStationMath.TOOL_DRILL, toolLevel, false)[SingularStationMath.R_SM];
        return SingularStationMath.crumbsUsable(sm, crumbsHave());
    }

    /** The conversion material kind (SingularStationMath.M_*) of a stack, or -1. */
    public static int materialKind(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return -1;
        }
        if (s.getItem() instanceof com.sc.item.ItemBatterySC) {
            return s.getItemDamage() == SingularStationMath.CORE_META ? SingularStationMath.M_SING_CORE
                    : s.getItemDamage() == SingularStationMath.EXO_CORE_META ? SingularStationMath.M_EXO_CORE : -1;
        }
        if (s.getItem() == com.sc.init.ModItems.component("matterCapsule")) {
            return SingularStationMath.M_CAPSULE;
        }
        if (s.getItem() == com.sc.init.ModItems.component("focusLens")) {
            return SingularStationMath.M_LENS;
        }
        if (s.getItem() == com.sc.init.ModItems.component("nb3SnPlate")) {
            return SingularStationMath.M_NB3SN;
        }
        if (s.getItem() == com.sc.init.ModItems.component("fusionCore")) {
            return SingularStationMath.M_FUSION;
        }
        ItemStack hf = com.sc.init.ModItems.ingot == null ? null : com.sc.init.ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM);
        if (hf != null && s.getItem() == hf.getItem() && s.getItemDamage() == hf.getItemDamage()) {
            return SingularStationMath.M_HAFNIUM;
        }
        return -1;
    }

    /** One stack of material kind `kind` (count `n`) - for the screen, NEI and the book. */
    public static ItemStack materialStack(int kind, int n) {
        ItemStack s;
        switch (kind) {
            case SingularStationMath.M_CAPSULE: s = new ItemStack(com.sc.init.ModItems.component("matterCapsule")); break;
            case SingularStationMath.M_LENS: s = new ItemStack(com.sc.init.ModItems.component("focusLens")); break;
            case SingularStationMath.M_NB3SN: s = new ItemStack(com.sc.init.ModItems.component("nb3SnPlate")); break;
            case SingularStationMath.M_FUSION: s = new ItemStack(com.sc.init.ModItems.component("fusionCore")); break;
            case SingularStationMath.M_HAFNIUM: s = com.sc.init.ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM); break;
            case SingularStationMath.M_EXO_CORE: s = new ItemStack(com.sc.init.ModItems.battery, 1, SingularStationMath.EXO_CORE_META); break;
            default: s = new ItemStack(com.sc.init.ModItems.battery, 1, SingularStationMath.CORE_META); break;
        }
        s.stackSize = Math.max(1, n);
        return s;
    }

    /** An Exo piece (any charge) - the conversion's input. */
    public static boolean isExo(ItemStack s) {
        return s != null && s.getItem() instanceof ItemArmorSC && ((ItemArmorSC) s.getItem()).getSuit() == com.sc.util.ArmorSuit.EXO;
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
        if ((slot >= 0 && slot < SLOTS) || slot == DONOR_SLOT) {
            SingularLevel.clearSync(stack);                 // СБ-2: a piece in the station is not worn
        }
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

    /** Hoppers and tubes: the armour slots and the tool slot (same rules: one item into an empty slot, not while locked). */
    private static final int[] PIPE_SLOTS = {0, 1, 2, 3, TOOL_SLOT};

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return PIPE_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        if (slot == TOOL_SLOT) {
            return !isLocked(slot) && getTool() == null && isStationTool(stack);
        }
        return !isLocked(slot) && super.canInsertItem(slot, stack, side);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return !isLocked(slot) && (slot == TOOL_SLOT || super.canExtractItem(slot, stack, side));
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
        if (now % 20 == 0) {                                // СБ-2: pieces that came in some other way (an old save, a pipe)
            boolean cleared = SingularLevel.clearSync(extra[DONOR_SLOT - ALL_SLOTS]);
            for (int i = 0; i < SLOTS; i++) {
                cleared |= SingularLevel.clearSync(getStackInSlot(i));
            }
            if (cleared) {
                markDirty();
            }
        }
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
        if (SingularStationMath.pieces(lv) == 0 && toolModerniseLevel() == 0) {
            return toolReadyLevel() > 0 ? "sc.singStation.err.tooloff" : "sc.singStation.err.noready";
        }
        return startModerniseFor(lv, p == null ? "" : p.getCommandSenderName());
    }

    /**
     * Starts the modernisation of the pieces at levels `lv` (0: not taking part) - the readiness already checked - and of
     * the Singular tool in the tool slot when it is ready (ToolLevelSC.readyToUpgrade): its share of the row joins the
     * cost, the drill's crumbs (material slots) pay up to half its SM and leave the slots now (back whole on «Отменить»).
     */
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
        int toolLv = toolModerniseLevel(), tool = toolLv > 0 ? toolKindOf(getTool()) : SingularStationMath.TOOL_NONE;
        if (toolLv > 0) {
            mask |= 1 << TOOL_BIT;
        }
        if (mask == 0) {
            return "sc.singStation.err.noready";
        }
        boolean cat = SingularStationMath.needsCatalyst(lv) || toolLv == 4;
        if (cat && !isCore(extra[1])) {
            return "sc.singStation.err.nocatalyst";
        }
        SingularProcessSC p = new SingularProcessSC();
        p.kind = SingularProcessSC.KIND_MODERNISE;
        p.mask = mask;
        p.resonance = resonance;
        int crumbs = crumbsFor(toolLv);
        long[] cost = SingularStationMath.moderniseCost(lv, tool, toolLv, crumbs, resonance);
        System.arraycopy(cost, 0, p.cost, 0, cost.length);
        p.baseTicks = Math.max(1, SingularStationMath.moderniseTicks(lv, tool, toolLv));
        for (int i = 0; i < SLOTS && i < lv.length; i++) {
            p.levels[i] = lv[i];
        }
        p.levels[TOOL_BIT] = toolLv;
        for (int slot = MATERIAL_SLOT; slot < MATERIAL_END && crumbs > 0; slot++) {   // the crumbs leave the slots now, whole
            if (isCrumb(extra[slot - ALL_SLOTS])) {
                ItemStack taken = decrStackSize(slot, Math.min(crumbs, extra[slot - ALL_SLOTS].stackSize));
                if (taken != null) {
                    crumbs -= taken.stackSize;
                    p.items.add(taken);
                }
            }
        }
        p.starter = starter == null ? "" : starter;
        long lost = 0;
        if (cat) {                                          // Н-1 В2: the core is always used up; its charge pays the EU first
            long charge = Math.max(0L, com.sc.item.ItemBatterySC.chargeOf(extra[1]));
            long eu = Math.min(charge, p.cost[SingularStationMath.R_EU]);
            p.catalystEu = eu;
            p.drawn[SingularStationMath.R_EU] = eu;
            extra[1] = null;
            long over = charge - eu;                        // the charge above the cost -> the station's buffer, as much as fits
            if (over > 0) {
                long put = Math.min(over, Math.max(0L, (long) getMaxEnergyStored() - getEnergyStored()));
                if (put > 0) {
                    addEnergy((int) Math.min(Integer.MAX_VALUE, put));
                }
                lost = over - put;
            }
        }
        begin(p);
        if (lost > 0 && worldObj != null && !worldObj.isRemote && !p.starter.isEmpty()) {
            EntityPlayer who = worldObj.getPlayerEntityByName(p.starter);
            if (who != null) {
                who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.catalyst.lost",
                        String.valueOf(lost)));
            }
        }
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

    // ------------------------------------------------------------------ Б-1 conversion

    /** The armour slots holding an Exo piece (bit per slot). */
    public int convertMask() {
        int mask = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (isExo(getStackInSlot(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** The materials there now, per kind (a kind may lie in several slots): the six material slots and the catalyst slot (a Singular core). */
    public int[] materialsHave() {
        int[] have = new int[SingularStationMath.MATERIALS];
        for (int slot = CATALYST_SLOT; slot < MATERIAL_END; slot++) {
            ItemStack s = getStackInSlot(slot);
            int k = materialKind(s);
            if (k >= 0) {
                have[k] += s.stackSize;
            }
        }
        return have;
    }

    /** EU the cores the conversion of `mask` would use carry (the first ones found, as startConvert takes them). */
    public long coreChargeFor(int mask) {
        return coreChargeFor(mask, SingularStationMath.TOOL_NONE);
    }

    /** The same with the Exo tool `tool` (SingularStationMath.TOOL_*) converted too. */
    public long coreChargeFor(int mask, int tool) {
        int[] need = SingularStationMath.convertMaterials(mask, tool);
        long eu = 0;
        int[] left = {need[SingularStationMath.M_EXO_CORE], need[SingularStationMath.M_SING_CORE]};
        for (int slot = CATALYST_SLOT; slot < MATERIAL_END; slot++) {
            ItemStack s = getStackInSlot(slot);
            int k = materialKind(s);
            int j = k == SingularStationMath.M_EXO_CORE ? 0 : k == SingularStationMath.M_SING_CORE ? 1 : -1;
            if (j >= 0 && left[j] > 0) {
                left[j]--;
                eu += com.sc.item.ItemBatterySC.chargeOf(s);
            }
        }
        return eu;
    }

    /** Б-1: the button «Преобразовать». @return null when it started, else the lang key of why not */
    public String startConvert(EntityPlayer p) {
        return startConvertFor(p == null ? "" : p.getCommandSenderName());
    }

    public String startConvertFor(String starter) {
        if (proc != null) {
            return "sc.singStation.err.busy";
        }
        int mask = convertMask(), tool = convertTool();
        if (mask == 0 && tool == SingularStationMath.TOOL_NONE) {
            return "sc.singStation.err.noexo";
        }
        int[] need = SingularStationMath.convertMaterials(mask, tool), have = materialsHave();
        for (int k = 0; k < need.length; k++) {
            if (have[k] < need[k]) {
                return "sc.singStation.err.nomaterials";
            }
        }
        SingularProcessSC q = new SingularProcessSC();
        q.kind = SingularProcessSC.KIND_CONVERT;
        q.mask = mask | (tool != SingularStationMath.TOOL_NONE ? 1 << TOOL_BIT : 0);
        long[] cost = SingularStationMath.convertCost(mask, tool);
        System.arraycopy(cost, 0, q.cost, 0, cost.length);
        q.baseTicks = Math.max(1, SingularStationMath.convertTicks(mask, tool));
        q.starter = starter == null ? "" : starter;
        long coreEu = 0;
        int[] left = need.clone();
        for (int slot = CATALYST_SLOT; slot < MATERIAL_END; slot++) {      // the materials leave the slots now, whole
            ItemStack s = getStackInSlot(slot);
            int k = materialKind(s);
            if (k < 0 || left[k] <= 0) {
                continue;
            }
            ItemStack taken = decrStackSize(slot, Math.min(left[k], s.stackSize));
            if (taken == null) {
                continue;
            }
            left[k] -= taken.stackSize;
            if (k == SingularStationMath.M_EXO_CORE || k == SingularStationMath.M_SING_CORE) {
                coreEu += com.sc.item.ItemBatterySC.chargeOf(taken);
            }
            q.items.add(taken);
        }
        long eu = Math.min(coreEu, q.cost[SingularStationMath.R_EU]);   // the cores' charge pays the EU first
        q.catalystEu = eu;
        q.drawn[SingularStationMath.R_EU] = eu;
        begin(q);
        return null;
    }

    /** Б-1 on one piece: the Singular piece of its type, level 1, scheme A, with the Exo piece's charge, gases (clamped), chips and switches. */
    public static ItemStack convertPiece(ItemStack exo) {
        if (!isExo(exo)) {
            return exo;
        }
        int t = ((ItemArmorSC) exo.getItem()).armorType;
        ItemStack out = new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR)[t]);
        NBTTagCompound tag = exo.hasTagCompound() ? (NBTTagCompound) exo.getTagCompound().copy() : new NBTTagCompound();
        for (String k : new String[]{SingularLevel.NBT, SingularLevel.PTS, SingularLevel.BRANCH3, SingularLevel.BRANCH5, SingularLevel.SYNC,
                SingularScheme.NBT}) {
            tag.removeTag(k);
        }
        int[] gas = new int[Gas.values().length];
        for (Gas g : Gas.values()) {
            gas[g.ordinal()] = com.sc.util.ArmorGasSC.amount(exo, g);
            tag.removeTag(com.sc.util.ArmorGasSC.nbtKey(g));
        }
        out.setTagCompound(tag);
        ItemArmorSC.setCharge(out, ItemArmorSC.chargeOf(exo));
        for (Gas g : Gas.values()) {                         // what the Singular tank holds of it, at most
            if (gas[g.ordinal()] > 0) {
                com.sc.util.ArmorGasSC.setAmount(out, g, gas[g.ordinal()]);
            }
        }
        SingularLevel.setLevel(out, SingularLevel.MIN);
        SingularLevel.setPoints(out, 0);
        SingularScheme.setScheme(out, SingularScheme.DEFAULT);
        return out;
    }

    /** NBT keys a fresh Singular tool must not inherit (levels, branch, scheme, cooldowns, form / hole modes, heat). */
    private static final String[] TOOL_FRESH_KEYS = {com.sc.util.ToolLevelSC.LEVEL, com.sc.util.ToolLevelSC.PTS, com.sc.util.ToolLevelSC.BRANCH,
        com.sc.util.ToolLevelSC.SCHEME, com.sc.util.ToolLevelSC.COOLDOWNS, com.sc.util.BladeForm.NBT, com.sc.item.ItemDrillSC.HOLE,
        com.sc.item.ItemDrillSC.HOLE_DEPTH, "HeatSC", "OverheatSC"};

    /** The tool conversion with the default scheme A. */
    public static ItemStack convertTool(ItemStack exo) {
        return convertTool(exo, SingularScheme.DEFAULT);
    }

    /**
     * The tool conversion: an Exo blade / drill becomes the Singular one of level 1 in scheme `scheme`, keeping its NBT -
     * the charge (capped at the new capacity, which is bigger anyway), the function switches (FnToggled, by feature ordinal),
     * the drill's linked chest, a name, enchantments - with the blade's Looting tag re-synced to the Singular tier. Cooled.
     * Not an Exo tool: returned as it is.
     */
    public static ItemStack convertTool(ItemStack exo, SingularScheme scheme) {
        if (!isExoTool(exo)) {
            return exo;
        }
        boolean blade = com.sc.item.ItemBladeSC.typeOf(exo) != null;
        ItemStack out = new ItemStack(blade ? com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR)
                : com.sc.init.ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
        NBTTagCompound tag = exo.hasTagCompound() ? (NBTTagCompound) exo.getTagCompound().copy() : new NBTTagCompound();
        for (String k : TOOL_FRESH_KEYS) {
            tag.removeTag(k);
        }
        out.setTagCompound(tag);
        if (blade) {
            com.sc.item.ItemBladeSC.setCharge(out, com.sc.item.ItemBladeSC.chargeOf(exo));
            com.sc.item.ItemBladeSC.syncLooting(out);
        } else {
            com.sc.item.ItemDrillSC.setCharge(out, com.sc.item.ItemDrillSC.chargeOf(exo));
        }
        com.sc.util.ToolLevelSC.setLevel(out, com.sc.util.ToolLevelSC.MIN);
        com.sc.util.ToolLevelSC.setPoints(out, 0);
        com.sc.util.ToolLevelSC.setScheme(out, scheme == null ? SingularScheme.DEFAULT : scheme);
        return out;
    }

    /** Gives a stack back to the slots it can go to (a core: the catalyst slot first; then the material slots), the rest drops. */
    private void putBack(ItemStack s) {
        if (s == null || s.stackSize <= 0) {
            return;
        }
        if (isCore(s) && extra[CATALYST_SLOT - ALL_SLOTS] == null) {
            extra[CATALYST_SLOT - ALL_SLOTS] = s;
            return;
        }
        for (int slot = MATERIAL_SLOT; slot < MATERIAL_END && s.stackSize > 0; slot++) {
            ItemStack in = extra[slot - ALL_SLOTS];
            if (in != null && in.isItemEqual(s) && ItemStack.areItemStackTagsEqual(in, s) && in.isStackable()) {
                int n = Math.min(s.stackSize, Math.min(in.getMaxStackSize(), getInventoryStackLimit()) - in.stackSize);
                if (n > 0) {
                    in.stackSize += n;
                    s.stackSize -= n;
                }
            }
        }
        for (int slot = MATERIAL_SLOT; slot < MATERIAL_END && s.stackSize > 0; slot++) {
            if (extra[slot - ALL_SLOTS] == null) {
                extra[slot - ALL_SLOTS] = s.copy();
                s.stackSize = 0;
            }
        }
        if (s.stackSize > 0 && worldObj != null && !worldObj.isRemote) {
            worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, s.copy()));
        }
    }

    /** Test hook: the process stands at `p` with its resources paid for that far (the rest is drawn as usual). */
    public void fastForwardForTest(double p) {
        if (proc == null) {
            return;
        }
        double q = Math.max(proc.progress, Math.min(0.999, p));
        long[] need = SingularStationMath.needFor(proc.cost, proc.drawn, q);
        for (int r = 0; r < SingularStationMath.RESOURCES; r++) {
            proc.drawn[r] += need[r];
        }
        proc.progress = q;
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

    /**
     * «Отменить»: half of what was drawn goes back (EU to the buffer, gases to the tanks). The catalyst core was used up at
     * the start (Н-1 В2), so a new core comes back carrying 50% of the charge that was counted toward the cost (an empty one
     * too); the leftover that went into the buffer at the start is not counted again (no dupe).
     */
    public void cancelProcess() {
        if (proc == null) {
            return;
        }
        SingularProcessSC p = proc;
        proc = null;
        boolean convert = p.kind == SingularProcessSC.KIND_CONVERT;
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
        for (ItemStack s : p.items) {                       // Б-1 materials (the cores with their charge), the drill's crumbs: whole
            putBack(s.copy());
        }
        if (!convert && (p.catalystEu > 0 || (p.kind == SingularProcessSC.KIND_MODERNISE && SingularStationMath.needsCatalyst(p.levels)))
                && com.sc.init.ModItems.battery != null) {         // the core used up at the start comes back as a new one
            if (isCore(extra[1])) {                         // an older save (СБ-4) still holds the kept core: the refund goes into it, no second core
                com.sc.item.ItemBatterySC.setCharge(extra[1],
                        com.sc.item.ItemBatterySC.chargeOf(extra[1]) + SingularStationMath.refund(p.catalystEu));
            } else {
                ItemStack core = new ItemStack(com.sc.init.ModItems.battery, 1, SingularStationMath.CORE_META);
                com.sc.item.ItemBatterySC.setCharge(core, SingularStationMath.refund(p.catalystEu));
                if (extra[1] == null) {
                    extra[1] = core;
                } else if (worldObj != null && !worldObj.isRemote) {
                    worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, core));
                }
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
            ItemStack tool = getTool();
            if (p.locks(TOOL_BIT) && com.sc.util.ToolLevelSC.isSingularTool(tool) && com.sc.util.ToolLevelSC.levelOf(tool) == p.levels[TOOL_BIT]) {
                int lv = com.sc.util.ToolLevelSC.applyLevelUp(tool);
                if (who != null) {
                    com.sc.util.ToolLevelSC.levelUpEffects(who, tool, lv);
                }
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
        } else if (p.kind == SingularProcessSC.KIND_CONVERT) {
            int n = 0;
            for (int i = 0; i < SLOTS; i++) {
                ItemStack s = getStackInSlot(i);
                if (p.locks(i) && isExo(s)) {
                    super.setInventorySlotContents(i, convertPiece(s));
                    n++;
                }
            }
            if (p.locks(TOOL_BIT) && isExoTool(getTool())) {
                SingularScheme sc = shownScheme();
                extra[TOOL_SLOT - ALL_SLOTS] = convertTool(getTool(), sc == null ? SingularScheme.DEFAULT : sc);
                n++;
            }
            if (who != null && n > 0) {
                who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.done.convert", String.valueOf(n)));
            }
        } else if (p.kind == SingularProcessSC.KIND_TRANSFER) {
            ItemStack donor = extra[0], target = p.target >= 0 && p.target < SLOTS ? getStackInSlot(p.target) : null;
            if (SingularLevel.isSingular(donor) && SingularLevel.isSingular(target)) {
                int lost = pourIntoTanks(transferLevel(donor, target));     // СБ-1: the donor's surplus gas -> the tanks
                if (who != null) {
                    who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.done.transfer",
                            String.valueOf(SingularLevel.levelOf(target))));
                    if (lost > 0) {
                        who.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.singStation.transfer.gaslost",
                                String.valueOf(lost)));
                    }
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

    /**
     * Ф4 on two pieces: the donor's level, points and branches go to the target; the donor is level 1 again.
     * СБ-1: the donor's tanks shrink with its level - what no longer fits leaves the piece.
     * @return mB of each gas (by Gas ordinal) that left the donor - the station pours it into its own tanks
     */
    public static int[] transferLevel(ItemStack donor, ItemStack target) {
        Gas[] gases = Gas.values();
        int[] before = new int[gases.length];
        for (Gas g : gases) {
            before[g.ordinal()] = com.sc.util.ArmorGasSC.amount(donor, g);
        }
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
        int[] over = new int[gases.length];
        for (Gas g : gases) {
            int cap = com.sc.util.ArmorGasSC.capacity(donor, g), was = before[g.ordinal()];
            if (was > cap) {
                over[g.ordinal()] = was - cap;
                com.sc.util.ArmorGasSC.setAmount(donor, g, cap);    // no hidden gas above the tank
            }
        }
        return over;
    }

    /** СБ-1: the gas `over` (by Gas ordinal) goes into the station's tanks; @return mB that did not fit (lost) */
    public int pourIntoTanks(int[] over) {
        int lost = 0;
        for (Gas g : Gas.values()) {
            int n = over == null || g.ordinal() >= over.length ? 0 : over[g.ordinal()];
            if (n > 0) {
                lost += n - fillTank(g, n, true);
            }
        }
        return lost;
    }

    // ------------------------------------------------------------------ the bridge link (docs/plan-ground-bridge.md §8, «Связь»)

    /** The Armour Link Modules in a player's inventory. */
    public static int linkModules(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && s.getItem() instanceof com.sc.item.ItemBridgeLinkModuleSC) {
                n += s.stackSize;
            }
        }
        return n;
    }

    /**
     * «Связь»: the Singular helmet in the helmet slot takes an Armour Link Module from the player's inventory;
     * a helmet that has it already forgets its linked bridges instead. @return the chat key
     */
    public String linkModule(EntityPlayer p) {
        ItemStack helmet = getStackInSlot(com.sc.util.ArmorGasSC.HELMET);
        if (!SingularLevel.isSingular(helmet)) {
            return "sc.singStation.link.nohelmet";
        }
        if (isLocked(com.sc.util.ArmorGasSC.HELMET)) {
            return "sc.singStation.err.busy";
        }
        if (com.sc.bridge.BridgeItemDataSC.hasModule(helmet)) {
            com.sc.bridge.BridgeItemDataSC.clearLinks(helmet);
            markDirty();
            return "sc.singStation.link.cleared";
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof com.sc.item.ItemBridgeLinkModuleSC) {
                if (!p.capabilities.isCreativeMode && --inv[i].stackSize <= 0) {
                    inv[i] = null;
                }
                com.sc.bridge.BridgeItemDataSC.installModule(helmet);
                p.inventoryContainer.detectAndSendChanges();
                markDirty();
                worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1, zCoord + 0.5, "random.anvil_use", 0.5F, 1.6F);
                return "sc.singStation.link.done";
            }
        }
        return "sc.singStation.link.nomodule";
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

    /** The tool's branch re-choice (or a first choice): `choice` (ToolLevelSC branch) for BRANCH_SM mB of singular matter, as the chestplate's. */
    public String changeToolBranch(int choice) {
        ItemStack t = getTool();
        if (!com.sc.util.ToolLevelSC.isSingularTool(t) || !com.sc.util.ToolLevelSC.validBranch(com.sc.util.ToolLevelSC.isBlade(t), choice)) {
            return "sc.singStation.err.notool";
        }
        if (isLocked(TOOL_SLOT)) {
            return "sc.singStation.err.busy";
        }
        if (com.sc.util.ToolLevelSC.levelOf(t) < com.sc.util.ToolLevelSC.BRANCH_LEVEL) {
            return "sc.singStation.err.branchlevel";
        }
        if (com.sc.util.ToolLevelSC.branchOf(t) == choice) {
            return "sc.singStation.err.samebranch";
        }
        if (tankAmount(Gas.SINGULAR_MATTER) < SingularStationMath.BRANCH_SM) {
            return "sc.singStation.err.nosm";
        }
        getTank(Gas.SINGULAR_MATTER).drain(SingularStationMath.BRANCH_SM, true);
        com.sc.util.ToolLevelSC.setBranch(t, choice);
        markDirty();
        return null;
    }

    /** The scheme the screen shows: the chestplate's, else the first Singular piece's, else the Singular tool's (null: none). */
    public SingularScheme shownScheme() {
        int[] order = {com.sc.util.ArmorGasSC.CHEST, com.sc.util.ArmorGasSC.HELMET, com.sc.util.ArmorGasSC.LEGS, com.sc.util.ArmorGasSC.BOOTS};
        for (int i : order) {
            if (SingularLevel.isSingular(getStackInSlot(i))) {
                return SingularScheme.of(getStackInSlot(i));
            }
        }
        return com.sc.util.ToolLevelSC.isSingularTool(getTool()) ? com.sc.util.ToolLevelSC.schemeOf(getTool()) : null;
    }

    /** ◄ ►: every Singular piece in the armour slots and the Singular tool take the next scheme (free). @return whether any changed */
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
        if (com.sc.util.ToolLevelSC.isSingularTool(getTool())) {
            com.sc.util.ToolLevelSC.setScheme(getTool(), next);
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
        if (modTool) {
            nbt.setBoolean("ModTool", true);
        }
        return nbt;
    }

    @Override
    public void readFromItem(NBTTagCompound nbt) {
        super.readFromItem(nbt);
        chargeOn = !nbt.getBoolean("ChargeOff");
        modTool = nbt.getBoolean("ModTool");
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setTier(Tier.SV);
        chargeOn = !nbt.getBoolean("ChargeOff");
        modTool = nbt.getBoolean("ModTool");
        java.util.Arrays.fill(extra, null);
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
        nbt.setBoolean("ModTool", modTool);
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
                ItemStack tool = getTool();
                if (tool != null) {                         // the tool beside the pieces (Slot 4)
                    ItemStack look = new ItemStack(tool.getItem(), 1, tool.getItemDamage());
                    if (com.sc.util.ToolLevelSC.isSingularTool(tool)) {
                        com.sc.util.ToolLevelSC.setScheme(look, com.sc.util.ToolLevelSC.schemeOf(tool));
                    }
                    NBTTagCompound t = look.writeToNBT(new NBTTagCompound());
                    t.setByte("Slot", (byte) SLOTS);
                    holoList.appendTag(t);
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
