package com.sc.tileentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemAreaCardSC;
import com.sc.item.ItemDrillHeadSC;
import com.sc.item.ItemQuarryModuleSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.machine.UpgradeType;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The Silicon Quarry, LV / MV / HV / EV (metadata). It digs its area layer by layer from its own
 * level down: centred on it (plus an offset) with the size set on its screen, or the box of an
 * Area Card. A drill head sets its speed and what it can break; modules add fortune, silk touch,
 * crushing and washing of ore, a pump, a magnet, more area, silence and auto-stop - each switched
 * on the Functions tab. What it digs goes into a 27-slot buffer and from there into any inventory
 * next to it. The screen's settings reach the client with the block (description packet); the
 * live numbers through ContainerQuarrySC.
 */
public class TileEntityQuarrySC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler {

    public static final int BUFFER = 27, FIRST_UPGRADE = BUFFER, UPGRADES = 18;
    public static final int SLOT_HEAD = FIRST_UPGRADE + UPGRADES, SLOT_SCANNER = SLOT_HEAD + 1, SLOT_CARD = SLOT_SCANNER + 1;
    /** 4 lens slots open, 4 more with resonator modules (appended after the old four - saves keep theirs). */
    public static final int FIRST_LENS = SLOT_CARD + 1, LENSES = 8, BASE_LENSES = 4;
    /** The battery slot under the energy gauge (after the lenses, so older saves keep their slots). */
    public static final int SLOT_BATTERY = FIRST_LENS + LENSES;
    public static final int SLOTS = SLOT_BATTERY + 1, FILTER_SLOTS = 9;
    /** Module slots open by tier: LV 6, MV 10, HV 14, EV 18 (the Exo rig: all 18). */
    public static final int[] UNLOCKED = {6, 10, 14, 18};
    /** Exo Drilling Rig: EU a haul costs, and hauls a second (x1.4 per speed module, cost x1.6). */
    public static final int EXO_COST = 200000;
    public static final double EXO_RATE = 1.0;
    public static final int[] BASE_SIZE = {8, 16, 32, 64};
    public static final double[] TIER_SPEED = {1, 1.5, 2, 3};

    // ---- function switches (the Functions tab) ----
    public static final int F_SPEED = 1, F_FORTUNE = 2, F_SILK = 4, F_CRUSH = 8, F_WASH = 16, F_PUMP = 32, F_PUMP_LAVA = 64,
            F_MAG_ITEMS = 128, F_MAG_XP = 256, F_RADIUS = 512, F_SILENT = 1024, F_AUTOSTOP = 2048, F_FILTER = 4096,
            F_SKIP_TILES = 8192, F_WARN_BUFFER = 16384, F_WARN_HEAD = 32768, F_WARN_ENERGY = 65536, F_WARN_DONE = 131072,
            F_TRASH = 1 << 18, F_CENTRIFUGE = 1 << 19, F_VEIN = 1 << 20, F_DOUBLE = 1 << 21, F_FLUID_GUARD = 1 << 22,
            F_GENTLE = 1 << 23, F_REPAIR = 1 << 24, F_ECONOMY = 1 << 25, F_STABILIZER = 1 << 26, F_DEEP_SCAN = 1 << 27;
    public static final int FLAG_COUNT = 28;
    /** Everything on but silk touch and the repair (a request: it switches itself off when the head is whole). */
    public static final int DEFAULT_FLAGS = ~F_SILK & ~F_REPAIR & ((1 << FLAG_COUNT) - 1);
    /** The switches added after the first 18: on by default in quarries saved before them (the repair excepted). */
    private static final int NEW_FLAGS_DEFAULT = ((1 << FLAG_COUNT) - 1) & ~((1 << 18) - 1) & ~F_REPAIR;
    /** Head repair: points mended a tick, EU a point. Trash: EU an item. Fluid guard: EU a block turned to stone. */
    public static final int REPAIR_PER_TICK = 20, REPAIR_COST = 25, TRASH_COST = 1, GUARD_COST = 10, VEIN_MAX = 64;
    // ---- look of the area (the Area tab) ----
    /** Bit 4 was the ore outlines (removed): kept free, old saves may still have it set. */
    public static final int V_DASH = 1, V_PLANE = 2;
    public static final int SHOW_ALWAYS = 0, SHOW_WRENCH = 1, SHOW_MENU = 2, SHOW_NEVER = 3;
    public static final int[] PALETTE = {0xFFB020, 0x9CF03A, 0x40D8FF, 0xFF4040, 0xB070FF, 0xFFFFFF, 0xFF70C0, 0xFF8A20};
    public static final int SHAPE_SQUARE = 0, SHAPE_CIRCLE = 1, SHAPE_SHAFT = 2;
    public static final int REPLACE_HOLE = 0, REPLACE_STONE = 1, REPLACE_DIRT = 2, REPLACE_ASWAS = 3;
    public static final int FILTER_ALL = 0, FILTER_ORE = 1, FILTER_ONLY = 2, FILTER_EXCEPT = 3;
    public static final int POWER_FULL = 0, POWER_ECO = 1, POWER_MIN = 2;
    public static final int REDSTONE_ALWAYS = 0, REDSTONE_ON = 1, REDSTONE_OFF = 2;

    /** Saved and synced by ordinal: new ones go at the end. */
    public enum Status { PAUSED, RUNNING, NO_POWER, NO_HEAD, BUFFER_FULL, DONE, NO_AREA, REDSTONE, BLOCKED_BY_FIELD, REPAIRING, TANK_FULL, DISABLED,
        WAITING_CHUNK }

    // ---- the pump's tank: compartments, each its own fluid ----
    public static final int TANKS = 4, TANK_BASE = 16000, TANK_PER_MODULE = 32000, FLUID_FILTER_MAX = 6;
    /** The washing water tank: 32 000 mB, +32 000 per tank module too. */
    public static final int WATER_BASE = 32000;
    /** A compartment's output side: SIDE_ANY, a ForgeDirection ordinal 0..5, or SIDE_NONE. */
    public static final int SIDE_ANY = -1, SIDE_NONE = 6;
    /** When the right compartment is full: leave the fluid in the world, pause, destroy it (trash module), make it a block. */
    public static final int FULL_LEAVE = 0, FULL_PAUSE = 1, FULL_VOID = 2, FULL_BLOCK = 3;
    /** Fluid filter: off, only the listed fluids, all but the listed; what's filtered out: left in the world or removed. */
    public static final int FF_OFF = 0, FF_ONLY = 1, FF_EXCEPT = 2;
    private static final int PUMP_OK = 0, PUMP_SKIP = 1, PUMP_WAIT = 2;
    private final FluidTank[] tanks = {new FluidTank(TANK_BASE), new FluidTank(TANK_BASE), new FluidTank(TANK_BASE), new FluidTank(TANK_BASE)};
    private final int[] tankSides = {SIDE_ANY, SIDE_ANY, SIDE_ANY, SIDE_ANY};
    /** A compartment pinned to a fluid takes only that one, even empty; auto: the quarry pushes it out itself. */
    private final String[] tankPinned = new String[TANKS];
    private final boolean[] tankAuto = new boolean[TANKS];
    /** Pouring a tank out costs 1 EU per 10 mB; auto output moves up to this much a compartment every 10 ticks. */
    public static final int CLEAR_MB_PER_EU = 10, AUTO_OUT = 1000;
    private final List<String> fluidFilter = new ArrayList<String>();
    private int fluidFilterMode = FF_OFF, fluidFilterRemove, tankFull = FULL_LEAVE;

    private final ItemStack[] slots = new ItemStack[SLOTS];
    private final ItemStack[] filter = new ItemStack[FILTER_SLOTS];
    private final List<ItemStack> overflow = new ArrayList<ItemStack>();
    private final FluidTank water = new FluidTank(WATER_BASE);

    // settings
    private int sizeX = 8, sizeZ = 8, offX, offZ, bottomY = 1, shape, replace, flags = DEFAULT_FLAGS, fortuneLevel = 5,
            powerMode, redstone, outSide = -1, filterMode, show, vflags = V_DASH | V_PLANE, brightness = 3,
            colorFrame = PALETTE[0], colorPlane = PALETTE[2];
    private String owner = "";
    /** The side the front faces (2-5), turned to the placer. */
    private int facing = 3;
    // state
    private boolean running, done;
    /** Stopped by the auto-stop module on a full buffer (not by the player): starts again when there's room. */
    private boolean autoStopped;
    /** Blocks left because a private field or another mod's protection forbids them - since the last start / new area. */
    private int skippedPrivate;
    private int layerY = -1, cursor;
    private long mined;
    private int xp;
    private double progress;
    private Status status = Status.PAUSED;
    private int lastCost;
    private int warned;          // bits of Status already reported to the owner
    // scanner (server)
    private boolean scanDirty;
    private int scanY, scanIndex;
    /** The scan's progress: blocks looked at and in all (for the screen's percentage). */
    private long scanned, scanTotal;
    /** EU the scanner spends per block it looks at - it is no free X-ray. */
    public static final int SCAN_COST = 8;
    private final Map<String, Integer> oreCounts = new LinkedHashMap<String, Integer>();

    public TileEntityQuarrySC() {
        super(Tier.LV);
    }

    public void setQuarryTier(Tier tier) {
        setTier(tier);
        int t = Math.min(3, tier.ordinal());
        sizeX = sizeZ = BASE_SIZE[t];
    }

    /** Saved before the tier went into the NBT ("TierSC"): the tier comes from the block's metadata on the first tick. */
    private boolean tierFromMeta;

    private void fixTierFromMeta() {
        tierFromMeta = false;
        if (getBlockType() instanceof com.sc.block.BlockQuarrySC) {
            setTier(com.sc.block.BlockQuarrySC.tierFor(getBlockMetadata()));
            refreshEnergyNet();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private int tierIndex() {
        return Math.min(3, getTier().ordinal());
    }

    /** The Exo Drilling Rig: the same block family, tier XV - it brings ore up from the deep, digging nothing. */
    public boolean isExo() {
        return getTier() == Tier.XV;
    }

    public int unlockedUpgrades() {
        return isExo() ? UPGRADES : UNLOCKED[tierIndex()];
    }

    /** The tier a module slot opens at (for its lock's tooltip). */
    public static Tier tierUnlocking(int index) {
        for (int t = 0; t < UNLOCKED.length; t++) {
            if (index < UNLOCKED[t]) {
                return Tier.values()[t];
            }
        }
        return Tier.EV;
    }

    // ------------------------------------------------------------------ modules and switches

    public int moduleCount(ItemQuarryModuleSC.Kind kind) {
        int n = 0;
        for (int i = FIRST_UPGRADE; i < FIRST_UPGRADE + UPGRADES; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof ItemQuarryModuleSC && ItemQuarryModuleSC.kindOf(s) == kind) {
                n += s.stackSize;
            }
        }
        return Math.min(n, kind.max);
    }

    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (int i = FIRST_UPGRADE; i < FIRST_UPGRADE + UPGRADES; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, UpgradeType.MAX_EFFECTIVE);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** A module's function works: the module is in, its switch is on, and the power mode allows it. */
    public boolean active(ItemQuarryModuleSC.Kind kind, int flag) {
        if (moduleCount(kind) <= 0 || !has(flag)) {
            return false;
        }
        if (powerMode == POWER_MIN && kind != ItemQuarryModuleSC.Kind.RADIUS && kind != ItemQuarryModuleSC.Kind.SILENT
                && kind != ItemQuarryModuleSC.Kind.FLUID_GUARD && kind != ItemQuarryModuleSC.Kind.GENTLE
                && kind != ItemQuarryModuleSC.Kind.REPAIR && kind != ItemQuarryModuleSC.Kind.ECONOMY
                && kind != ItemQuarryModuleSC.Kind.RESONATOR
                && kind != ItemQuarryModuleSC.Kind.AUTOSTOP) {
            return false;
        }
        if (powerMode == POWER_ECO && getEnergyStored() < getMaxEnergyStored() / 4
                && (kind == ItemQuarryModuleSC.Kind.SPEED || kind == ItemQuarryModuleSC.Kind.MAGNET)) {
            return false;
        }
        return true;
    }

    public int fortune() {
        if (!active(ItemQuarryModuleSC.Kind.FORTUNE, F_FORTUNE) || silk()) {
            return 0;
        }
        return Math.max(0, Math.min(fortuneLevel, moduleCount(ItemQuarryModuleSC.Kind.FORTUNE)));
    }

    public boolean silk() {
        return active(ItemQuarryModuleSC.Kind.SILK, F_SILK);
    }

    @Override
    public boolean acceptsAnyVoltage() {
        return upgradeCount(UpgradeType.UNIVERSAL_TRANSFORMER) > 0;
    }

    @Override
    public Tier inputTier() {
        Tier[] tiers = Tier.values();
        if (upgradeCount(UpgradeType.UNIVERSAL_TRANSFORMER) > 0) {
            return tiers[tiers.length - 1];
        }
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + upgradeCount(UpgradeType.TRANSFORMER))];
    }

    /** The rig's buffer is 20M EU: a haul with every module in costs up to ~7M, more than tier XV's own buffer. */
    public static final int EXO_BUFFER = 20000000;

    @Override
    public int getMaxEnergyStored() {
        return (isExo() ? EXO_BUFFER : super.getMaxEnergyStored()) + upgradeCount(UpgradeType.ENERGY_STORAGE) * UpgradeType.STORAGE_PER_UPGRADE;
    }

    public ItemDrillHeadSC.Kind headKind() {
        ItemStack h = slots[SLOT_HEAD];
        return h != null && h.getItem() instanceof ItemDrillHeadSC ? ((ItemDrillHeadSC) h.getItem()).kind : null;
    }

    /** Blocks a second: the head's speed x the tier's x 1.4 per speed module. */
    public double blocksPerSecond() {
        ItemDrillHeadSC.Kind head = headKind();
        if (head == null) {
            return 0;
        }
        int speed = active(ItemQuarryModuleSC.Kind.SPEED, F_SPEED) ? moduleCount(ItemQuarryModuleSC.Kind.SPEED) : 0;
        double bps = head.blocksPerSecond * TIER_SPEED[tierIndex()] * Math.pow(1.4, speed) * com.sc.util.ConfigSC.quarrySpeed;
        if (active(ItemQuarryModuleSC.Kind.DOUBLE, F_DOUBLE)) {
            bps *= 2;
        }
        if (active(ItemQuarryModuleSC.Kind.ECONOMY, F_ECONOMY)) {
            bps *= 0.8;
        }
        return bps;
    }

    /** EU one block costs: by its hardness, times what the modules add. */
    public int costFor(float hardness) {
        double eu = 30 + 15 * Math.max(0, hardness);
        if (active(ItemQuarryModuleSC.Kind.SPEED, F_SPEED)) {
            eu *= Math.pow(1.6, moduleCount(ItemQuarryModuleSC.Kind.SPEED));
        }
        eu *= 1 + 0.5 * fortune();
        if (silk()) {
            eu *= 2;
        }
        if (active(ItemQuarryModuleSC.Kind.CRUSH, F_CRUSH)) {
            eu *= 1.5;
        }
        if (active(ItemQuarryModuleSC.Kind.WASH, F_WASH)) {
            eu *= 1.5;
        }
        if (headKind() == ItemDrillHeadSC.Kind.EXO) {
            eu *= 1.5;
        }
        if (active(ItemQuarryModuleSC.Kind.ECONOMY, F_ECONOMY)) {
            eu *= 0.75;
        }
        return (int) Math.ceil(eu);
    }

    // ------------------------------------------------------------------ the area

    public int maxSize() {
        int radius = active(ItemQuarryModuleSC.Kind.RADIUS, F_RADIUS) ? moduleCount(ItemQuarryModuleSC.Kind.RADIUS) : 0;
        return BASE_SIZE[tierIndex()] + 8 * radius;
    }

    /**
     * {x0, z0, x1, z1, yTop, yBottom} (inclusive), or null if the card's box is too big, too far or
     * in another dimension.
     * Without a card: centred on the quarry plus the offset, from its own level down to bottomY.
     */
    public int[] area() {
        if (isExo()) {
            return null;
        }
        int max = maxSize();
        int[] card = ItemAreaCardSC.area(slots[SLOT_CARD]);
        if (card != null) {
            if (worldObj != null && !ItemAreaCardSC.inDimension(slots[SLOT_CARD], worldObj.provider.dimensionId)) {
                return null;                                 // the card's box is in another dimension
            }
            if (card[3] - card[0] + 1 > max || card[5] - card[2] + 1 > max
                    || Math.abs((card[0] + card[3]) / 2 - xCoord) > 64 + max || Math.abs((card[2] + card[5]) / 2 - zCoord) > 64 + max) {
                return null;
            }
            return new int[]{card[0], card[2], card[3], card[5], Math.min(card[4], 255), Math.max(1, card[1])};
        }
        int sx = shape == SHAPE_SHAFT ? 1 : Math.max(1, Math.min(sizeX, max));
        int sz = shape == SHAPE_SHAFT ? 1 : Math.max(1, Math.min(shape == SHAPE_CIRCLE ? sizeX : sizeZ, max));
        int x0 = xCoord + offX - (sx - 1) / 2, z0 = zCoord + offZ - (sz - 1) / 2;
        return new int[]{x0, z0, x0 + sx - 1, z0 + sz - 1, yCoord, Math.max(1, Math.min(bottomY, yCoord))};
    }

    public boolean usesCard() {
        return ItemAreaCardSC.area(slots[SLOT_CARD]) != null;
    }

    /** Cell `i` of a layer (row-major), or null if the shape leaves it out. */
    private int[] cell(int[] a, int i) {
        int w = a[2] - a[0] + 1;
        int x = a[0] + i % w, z = a[1] + i / w;
        if (shape == SHAPE_CIRCLE && !usesCard()) {
            double cx = (a[0] + a[2]) / 2.0, cz = (a[1] + a[3]) / 2.0, r = w / 2.0;
            if (Math.pow(x - cx, 2) + Math.pow(z - cz, 2) > r * r) {
                return null;
            }
        }
        return new int[]{x, z};
    }

    private int cellsPerLayer(int[] a) {
        return (a[2] - a[0] + 1) * (a[3] - a[1] + 1);
    }

    /** Settings changed: start the area over. */
    /** The area the cursor was walking (not saved: after a load the saved cursor is trusted). */
    private int[] areaSeen;
    /** A whole water body / lake was pumped this tick - one a tick (hundreds of block changes each). */
    private boolean bodyPumped;

    private void resetCursor() {
        int[] a = area();
        layerY = a == null ? -1 : a[4];
        cursor = 0;
        done = false;
        progress = 0;
        clearScan();
        warned = 0;
        skippedPrivate = 0;
    }

    // ------------------------------------------------------------------ what gets dug

    public static boolean isOre(Block block, int meta) {
        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return false;
        }
        for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, meta))) {
            if (OreDictionary.getOreName(id).startsWith("ore")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOre(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return false;
        }
        for (int id : OreDictionary.getOreIDs(s)) {
            if (OreDictionary.getOreName(id).startsWith("ore")) {
                return true;
            }
        }
        return false;
    }

    /** Does the filter let this block be dug? (Everything else stays where it is.) */
    private boolean passesFilter(Block block, int meta) {
        if (replace == REPLACE_ASWAS) {
            return isOre(block, meta);
        }
        if (!has(F_FILTER) || filterMode == FILTER_ALL) {
            return true;
        }
        if (filterMode == FILTER_ORE) {
            return isOre(block, meta);
        }
        Item item = Item.getItemFromBlock(block);
        boolean listed = false;
        for (ItemStack f : filter) {
            if (f != null && item != null && f.getItem() == item && (!f.getHasSubtypes() || f.getItemDamage() == block.damageDropped(meta))) {
                listed = true;
                break;
            }
        }
        return filterMode == FILTER_ONLY ? listed : !listed;
    }

    /** 0: dig it, 1: skip it (leave it), 2: a fluid for the pump. */
    private int judge(int x, int y, int z) {
        if (x == xCoord && y == yCoord && z == zCoord) {
            return 1;
        }
        Block block = worldObj.getBlock(x, y, z);
        if (block.isAir(worldObj, x, y, z)) {
            return 1;
        }
        if (block.getMaterial().isLiquid()) {
            return active(ItemQuarryModuleSC.Kind.PUMP, F_PUMP) ? 2 : 1;
        }
        float hardness = block.getBlockHardness(worldObj, x, y, z);
        ItemDrillHeadSC.Kind head = headKind();
        if (hardness < 0 || head == null || hardness > head.maxHardness) {
            return 1;
        }
        if (block.hasTileEntity(worldObj.getBlockMetadata(x, y, z)) && has(F_SKIP_TILES)) {
            return 1;
        }
        if (active(ItemQuarryModuleSC.Kind.GENTLE, F_GENTLE) && built(block)) {
            return 1;
        }
        return passesFilter(block, worldObj.getBlockMetadata(x, y, z)) ? 0 : 1;
    }

    /** What players build (and the Gentle module leaves): wood, glass, wool, bricks, doors, rails, torches... */
    public static boolean built(Block b) {
        net.minecraft.block.material.Material m = b.getMaterial();
        if (m == net.minecraft.block.material.Material.wood || m == net.minecraft.block.material.Material.glass
                || m == net.minecraft.block.material.Material.cloth || m == net.minecraft.block.material.Material.carpet
                || m == net.minecraft.block.material.Material.circuits || m == net.minecraft.block.material.Material.redstoneLight) {
            return true;
        }
        return b == Blocks.stonebrick || b == Blocks.brick_block || b == Blocks.nether_brick || b == Blocks.quartz_block
                || b == Blocks.stone_brick_stairs || b == Blocks.brick_stairs || b == Blocks.stone_stairs || b == Blocks.quartz_stairs
                || b == Blocks.nether_brick_stairs || b == Blocks.stone_slab || b == Blocks.double_stone_slab
                || b == Blocks.cobblestone_wall || b == Blocks.iron_bars || b == Blocks.iron_door || b == Blocks.rail
                || b == Blocks.golden_rail || b == Blocks.detector_rail || b == Blocks.activator_rail || b == Blocks.bookshelf
                || b == Blocks.glowstone;
    }

    /** Another player's private field zone, or another mod's protection, forbids breaking there. */
    private boolean forbidden(int x, int y, int z) {
        TileEntityFieldGeneratorSC field = TileEntityFieldGeneratorSC.fieldWith(worldObj, TileEntityFieldGeneratorSC.F_PRIVATE, x + 0.5, y + 0.5, z + 0.5);
        if (field != null && !field.allowedName(owner)) {
            return true;
        }
        if (worldObj instanceof WorldServer) {
            com.mojang.authlib.GameProfile op = ownerProfile();
            net.minecraftforge.common.util.FakePlayer fake = op == null
                    ? net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((WorldServer) worldObj)   // no owner (an old quarry): as before
                    : net.minecraftforge.common.util.FakePlayerFactory.get((WorldServer) worldObj, op);
            if (op != null && fake.worldObj != worldObj) {
                fake.setWorld(worldObj);                // Forge caches it by profile only: a quarry in another dimension
            }
            net.minecraftforge.event.world.BlockEvent.BreakEvent ev = new net.minecraftforge.event.world.BlockEvent.BreakEvent(
                    x, y, z, worldObj, worldObj.getBlock(x, y, z), worldObj.getBlockMetadata(x, y, z), fake);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev);
            return ev.isCanceled();
        }
        return false;
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (tierFromMeta) {
            fixTierFromMeta();
        }
        long time = worldObj.getTotalWorldTime();
        if (applyTankCapacityLater || time % 20 == 0) {
            applyTankCapacityLater = false;
            applyTankCapacity();                   // modules in or out: the compartments' size follows
        }
        if (time % 10 == 0) {
            autoOutput();
            feedWash();
        }
        if (time % 5 == 0) {
            pushOut();
        }
        if (powerOn && (scanDirty || scanY > 0)) {                 // switched off: no scan, no magnet
            scanStep();
        }
        if (powerOn && time % 20 == 0) {
            magnet();
        }
        if (time % 40 == 0 && running && layerY != layerSent) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);   // the renderer's layer line - only when it moved
        }
        if (feedFromBattery(slots[SLOT_BATTERY]) > 0) {
            markDirty();
        }
        dig();
        holdChunks();
        if (status == Status.RUNNING) {
            com.sc.util.SoundsSC.loop(this, isExo() ? RIG_SOUND : DRILL_SOUND);
        }
    }

    private void setStatus(Status s) {
        if (s != status) {
            status = s;
            if (s != Status.RUNNING && s != Status.PAUSED) {
                warn(s);               // once per start (warned is cleared by Start and by a new area)
            }
        }
    }

    private void dig() {
        if (!running && autoStopped) {
            flushOverflow();
            if (overflow.isEmpty() && freeSlots() >= AUTOSTOP_RESUME) {
                running = true;                            // the auto-stop's buffer has room again: carry on
                autoStopped = false;
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);   // the renderer's running look
            }
        }
        if (!running) {
            setStatus(autoStopped ? Status.BUFFER_FULL : done ? Status.DONE : Status.PAUSED);
            return;
        }
        if (!powerOn) {
            setStatus(Status.DISABLED);                // the power switch (GuiPowerSC): takes no energy either
            return;
        }
        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        if (redstone == REDSTONE_ON && !powered || redstone == REDSTONE_OFF && powered) {
            setStatus(Status.REDSTONE);
            return;
        }
        if (!isExo()) {
            if (area() == null) {
                setStatus(Status.NO_AREA);
                return;
            }
            if (headKind() == null) {
                setStatus(Status.NO_HEAD);
                return;
            }
            if (repairing()) {
                return;
            }
        }
        if (!overflow.isEmpty() || firstEmpty() < 0) {
            flushOverflow();
            if (!overflow.isEmpty() || firstEmpty() < 0) {
                setStatus(Status.BUFFER_FULL);
                if (active(ItemQuarryModuleSC.Kind.AUTOSTOP, F_AUTOSTOP)) {
                    running = false;
                    autoStopped = true;                      // goes on by itself once the buffer has room
                    markDirty();
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                }
                return;
            }
        }
        if (isExo()) {
            haul();
            return;
        }
        int[] a = area();
        if (areaSeen != null && !java.util.Arrays.equals(a, areaSeen)) {
            resetCursor();                                   // the area changed: the cursor meant another cell
        }
        areaSeen = a.clone();
        if (layerY < 0 || layerY > a[4]) {
            layerY = a[4];
            cursor = 0;
        }
        progress = Math.min(progress + blocksPerSecond() / 20.0, 16);
        int checks = 0, dug = 0;
        bodyPumped = false;
        while (progress >= 1 && checks < 512 && dug < 16 && !bodyPumped) {
            if (layerY < a[5]) {
                done = true;
                running = false;
                setStatus(Status.DONE);
                markDirty();
                return;
            }
            int[] c = cell(a, cursor);
            checks++;
            if (c == null) {
                advance(a);
                continue;
            }
            int x = c[0], z = c[1], y = layerY;
            if (!worldObj.blockExists(x, y, z)) {
                setStatus(Status.WAITING_CHUNK);
                return;                                      // not loaded: wait for it rather than skip it for good
            }
            int verdict = judge(x, y, z);
            if (verdict == 1) {
                advance(a);
                continue;
            }
            if (forbidden(x, y, z)) {
                skippedPrivate++;
                setStatus(Status.BLOCKED_BY_FIELD);
                advance(a);
                continue;
            }
            Block block = worldObj.getBlock(x, y, z);
            int cost = verdict == 2 ? 20 : costFor(block.getBlockHardness(worldObj, x, y, z));
            if (getEnergyStored() < cost) {
                setStatus(Status.NO_POWER);
                return;
            }
            if (verdict == 2) {
                int r = pump(x, y, z, block);
                if (r == PUMP_WAIT) {
                    setStatus(Status.TANK_FULL);
                    return;
                }
                if (r == PUMP_SKIP) {
                    advance(a);
                    continue;
                }
                removeEnergy(cost);
            } else {
                removeEnergy(cost);                          // paid first: the vein and the fluid guard spend after it
                dug += mine(x, y, z, block);
            }
            lastCost = cost;
            progress -= 1;
            dug++;
            advance(a);
            if (headKind() == null) {
                setStatus(Status.NO_HEAD);
                return;
            }
        }
        setStatus(Status.RUNNING);
    }

    // ------------------------------------------------------------------ the Exo Drilling Rig

    /** Hauls a second (the speed modules' x1.4 each). */
    public double haulsPerSecond() {
        int speed = active(ItemQuarryModuleSC.Kind.SPEED, F_SPEED) ? moduleCount(ItemQuarryModuleSC.Kind.SPEED) : 0;
        double rate = EXO_RATE * Math.pow(1.4, speed) * com.sc.util.ConfigSC.quarrySpeed;
        return active(ItemQuarryModuleSC.Kind.ECONOMY, F_ECONOMY) ? rate * 0.8 : rate;
    }

    public int haulCost() {
        double eu = EXO_COST;
        if (active(ItemQuarryModuleSC.Kind.SPEED, F_SPEED)) {
            eu *= Math.pow(1.6, moduleCount(ItemQuarryModuleSC.Kind.SPEED));
        }
        eu *= 1 + 0.5 * fortune();
        if (active(ItemQuarryModuleSC.Kind.STABILIZER, F_STABILIZER)) {
            eu *= 1.5;
        }
        if (active(ItemQuarryModuleSC.Kind.ECONOMY, F_ECONOMY)) {
            eu *= 0.75;
        }
        if (active(ItemQuarryModuleSC.Kind.CRUSH, F_CRUSH)) {
            eu *= 1.25;
        }
        if (active(ItemQuarryModuleSC.Kind.WASH, F_WASH)) {
            eu *= 1.25;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(eu));
    }

    /** Lenses of that mod ore in the lens slots. */
    public int lensCount(int ore) {
        int n = 0;
        for (int i = FIRST_LENS; i < FIRST_LENS + unlockedLenses(); i++) {
            ItemStack s = slots[i];
            if (s != null && s.getItem() instanceof com.sc.item.ItemOreLensSC && com.sc.item.ItemOreLensSC.oreOf(s) == ore) {
                n++;                                        // one lens a slot (an old stack counts as one)
            }
        }
        return n;
    }

    /** An ore's weight here: its base, x(1 + 4 per lens), 0 if the filter leaves it out. */
    public int weightOf(com.sc.machine.ExoOreTableSC.Entry e) {
        if (has(F_FILTER) && (filterMode == FILTER_ONLY || filterMode == FILTER_EXCEPT)) {
            boolean listed = false;
            for (ItemStack f : filter) {
                if (f != null && f.getItem() == e.ore.getItem() && f.getItemDamage() == e.ore.getItemDamage()) {
                    listed = true;
                    break;
                }
            }
            if (filterMode == FILTER_ONLY ? !listed : listed) {
                return 0;
            }
        }
        return e.weight * (1 + lensBoost() * (e.lens >= 0 ? lensCount(e.lens) : 0));
    }

    /** What one lens adds: x5 (4), with the stabilizer x7 (6). */
    public int lensBoost() {
        return active(ItemQuarryModuleSC.Kind.STABILIZER, F_STABILIZER) ? 6 : com.sc.machine.ExoOreTableSC.LENS_BOOST;
    }

    /** Lens slots open: 4, +1 per resonator module. */
    public int unlockedLenses() {
        return Math.min(LENSES, BASE_LENSES + (isExo() ? moduleCount(ItemQuarryModuleSC.Kind.RESONATOR) : 0));
    }

    /** The ores the rig can bring up: the table, plus other mods' ores with a deep scan module. */
    public List<com.sc.machine.ExoOreTableSC.Entry> exoEntries() {
        if (!active(ItemQuarryModuleSC.Kind.DEEP_SCAN, F_DEEP_SCAN)) {
            return com.sc.machine.ExoOreTableSC.entries();
        }
        List<com.sc.machine.ExoOreTableSC.Entry> all = new ArrayList<com.sc.machine.ExoOreTableSC.Entry>(com.sc.machine.ExoOreTableSC.entries());
        all.addAll(com.sc.machine.ExoOreTableSC.foreign());
        return all;
    }

    public int totalWeight() {
        int t = 0;
        for (com.sc.machine.ExoOreTableSC.Entry e : exoEntries()) {
            t += weightOf(e);
        }
        return t;
    }

    /** One tick of the rig: hauls ore up while it has the energy - fortune adds copies, crushing / washing as the quarry's. */
    private void haul() {
        int total = totalWeight();
        if (total <= 0) {
            setStatus(Status.NO_AREA);
            return;
        }
        progress = Math.min(progress + haulsPerSecond() / 20.0, 8);
        int cost = haulCost();
        while (progress >= 1) {
            if (getEnergyStored() < cost) {
                setStatus(Status.NO_POWER);
                return;
            }
            int pick = worldObj.rand.nextInt(total);
            com.sc.machine.ExoOreTableSC.Entry got = null;
            for (com.sc.machine.ExoOreTableSC.Entry e : exoEntries()) {
                pick -= weightOf(e);
                if (pick < 0) {
                    got = e;
                    break;
                }
            }
            if (got == null) {
                return;
            }
            removeEnergy(cost);
            lastCost = cost;
            progress -= 1;
            ItemStack ore = got.ore.copy();
            int f = fortune();
            ore.stackSize = 1 + (f > 0 ? worldObj.rand.nextInt(f + 1) : 0);
            for (ItemStack out : process(ore)) {
                store(out);
            }
            mined++;
            String key = Item.itemRegistry.getNameForObject(ore.getItem()) + "@" + ore.getItemDamage();
            Integer n = oreCounts.get(key);
            oreCounts.put(key, n == null ? 1 : n + 1);
            markDirty();
        }
        setStatus(Status.RUNNING);
    }

    /**
     * Head repair: while its switch is on (with the module in), the quarry mends the head instead of
     * digging - REPAIR_PER_TICK points a tick at REPAIR_COST EU each - and switches it off itself
     * when the head is whole. @return true while it's repairing (no digging this tick)
     */
    private boolean repairing() {
        if (!has(F_REPAIR)) {
            return false;
        }
        ItemStack head = slots[SLOT_HEAD];
        if (moduleCount(ItemQuarryModuleSC.Kind.REPAIR) <= 0 || head == null || head.getItemDamage() <= 0) {
            flags &= ~F_REPAIR;
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return false;
        }
        int points = Math.min(REPAIR_PER_TICK, head.getItemDamage());
        points = Math.min(points, getEnergyStored() / REPAIR_COST);
        if (points <= 0) {
            setStatus(Status.NO_POWER);
            return true;
        }
        removeEnergy(points * REPAIR_COST);
        head.setItemDamage(head.getItemDamage() - points);
        setStatus(Status.REPAIRING);
        markDirty();
        return true;
    }

    private void advance(int[] a) {
        cursor++;
        if (cursor >= cellsPerLayer(a)) {
            cursor = 0;
            layerY--;
        }
    }

    private void replaceAfter(int x, int y, int z) {
        Block with = replace == REPLACE_STONE || replace == REPLACE_ASWAS ? Blocks.stone : replace == REPLACE_DIRT ? Blocks.dirt : Blocks.air;
        worldObj.setBlock(x, y, z, with, 0, 3);
    }

    /** @return PUMP_OK (taken or removed), PUMP_SKIP (left, go on), PUMP_WAIT (a full tank pauses the quarry) */
    private int pump(int x, int y, int z, Block block) {
        Fluid fluid = fluidOf(block);
        int meta = worldObj.getBlockMetadata(x, y, z);
        if (fluid == null) {
            return PUMP_SKIP;
        }
        if (fluid == FluidRegistry.LAVA && !has(F_PUMP_LAVA)) {
            return PUMP_SKIP;
        }
        if (!fluidWanted(fluid)) {                  // the fluid filter: left in the world, or removed
            if (fluidFilterRemove == 0) {
                return PUMP_SKIP;
            }
            worldObj.setBlock(x, y, z, Blocks.air, 0, 3);
            return PUMP_OK;
        }
        if (fluidVeinActive()) {
            return pumpBody(x, y, z, fluid, true);  // the fluid vein: the whole lake, past the area too
        }
        if (fluid == FluidRegistry.WATER) {
            return pumpBody(x, y, z, fluid, false); // water heals itself: take the whole body at once
        }
        net.minecraftforge.fluids.IFluidBlock fb = block instanceof net.minecraftforge.fluids.IFluidBlock
                ? (net.minecraftforge.fluids.IFluidBlock) block : null;
        FluidStack there = fb != null ? (fb.canDrain(worldObj, x, y, z) ? fb.drain(worldObj, x, y, z, false) : null)
                : meta == 0 ? new FluidStack(fluid, 1000) : null;
        if (there == null || there.amount <= 0) {   // not a source: just cleared away
            worldObj.setBlock(x, y, z, Blocks.air, 0, 3);
            return PUMP_OK;
        }
        int t = compartmentFor(there);
        if (t < 0) {
            switch (tankFull) {
                case FULL_PAUSE:
                    return PUMP_WAIT;
                case FULL_VOID:
                    if (!active(ItemQuarryModuleSC.Kind.TRASH, F_TRASH) || getEnergyStored() < TRASH_COST) {
                        return PUMP_SKIP;
                    }
                    removeEnergy(TRASH_COST);
                    break;
                case FULL_BLOCK:
                    if (fluid == FluidRegistry.LAVA) {
                        store(new ItemStack(Blocks.obsidian));
                    } else if (fluid == FluidRegistry.WATER) {
                        store(new ItemStack(Blocks.ice));
                    } else {
                        return PUMP_SKIP;
                    }
                    break;
                default:
                    return PUMP_SKIP;
            }
        } else {
            FluidStack got = fb != null ? fb.drain(worldObj, x, y, z, true) : there;
            if (got != null) {
                tanks[t].fill(got, true);
            }
        }
        if (worldObj.getBlock(x, y, z) == block) {
            worldObj.setBlock(x, y, z, Blocks.air, 0, 3);
        }
        return PUMP_OK;
    }

    /** Water taken a pump step at most, and EU per block past the first; the fluid vein: EU a block outside the area. */
    public static final int WATER_BODY_MAX = 256, WATER_BODY_COST = 2, FLUID_VEIN_COST = 5;
    /** The fluid vein's reach past the area, by tier (LV .. EV). */
    public static final int[] FLUID_VEIN_RANGE = {8, 16, 32, 64};
    private boolean fluidVeinOn = true, fluidVeinKeepFlowing;
    /** The washing tank tops itself up from the pump's tanks' water. */
    private boolean washFromTanks;
    private int fluidVeinRange = 64;
    /** Blocks the last vein step took, and all of them so far (the Tanks tab shows them). */
    private int fluidVeinLast, fluidVeinTotal;

    public boolean fluidVeinActive() {
        return fluidVeinOn && moduleCount(ItemQuarryModuleSC.Kind.FLUID_VEIN) > 0;
    }

    /** How far past the area the vein may go: the setting, at most the tier's. */
    public int fluidVeinReach() {
        return Math.min(fluidVeinRange, FLUID_VEIN_RANGE[tierIndex()]);
    }

    public boolean isFluidVeinOn() { return fluidVeinOn; }
    public boolean isFluidVeinKeepFlowing() { return fluidVeinKeepFlowing; }
    public int getFluidVeinLast() { return fluidVeinLast; }
    public int getFluidVeinTotal() { return fluidVeinTotal; }

    /** Client: the vein's counters from the screen's sync. */
    public void setFluidVeinClient(int last, int total) {
        fluidVeinLast = last;
        fluidVeinTotal = total;
    }

    /**
     * A connected body of one fluid, taken a step at a time (WATER_BODY_MAX blocks): vanilla water
     * refills a gap between two sources, so taking it a block at a time never ends - the body goes
     * without block updates, and nothing is told to flow back. Without the vein only the area's
     * part (water only); with it, the whole lake up to fluidVeinReach() past the area, 5 EU a block
     * out there. Every source goes into the tank (1000 mB, or what another mod's block holds); a
     * full tank: the Tank full setting (leave it / pause / destroy / make ice or obsidian).
     */
    private int pumpBody(int x0, int y0, int z0, Fluid fluid, boolean vein) {
        bodyPumped = true;
        int[] a = area();
        int r = vein ? fluidVeinReach() : 0;
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<int[]>();
        java.util.Set<Long> seen = new java.util.HashSet<Long>();
        open.add(new int[]{x0, y0, z0});
        seen.add(posKey(x0, y0, z0));
        int taken = 0, visits = 0;
        while (!open.isEmpty() && taken < WATER_BODY_MAX && visits < WATER_BODY_MAX * 8) {
            int[] c = open.poll();
            visits++;
            int x = c[0], y = c[1], z = c[2];
            Block b = worldObj.getBlock(x, y, z);
            if (fluidOf(b) != fluid || forbidden(x, y, z)) {
                continue;
            }
            boolean inside = a != null && x >= a[0] && x <= a[2] && z >= a[1] && z <= a[3] && y <= a[4] && y >= a[5];
            int cost = taken == 0 ? 0 : inside ? WATER_BODY_COST : FLUID_VEIN_COST;
            if (getEnergyStored() < cost) {
                break;
            }
            net.minecraftforge.fluids.IFluidBlock fb = b instanceof net.minecraftforge.fluids.IFluidBlock
                    ? (net.minecraftforge.fluids.IFluidBlock) b : null;
            FluidStack there = fb != null ? (fb.canDrain(worldObj, x, y, z) ? fb.drain(worldObj, x, y, z, false) : null)
                    : worldObj.getBlockMetadata(x, y, z) == 0 ? new FluidStack(fluid, 1000) : null;
            boolean source = there != null && there.amount > 0;
            if (source) {
                int t = compartmentFor(there);
                if (t >= 0) {
                    FluidStack got = fb != null ? fb.drain(worldObj, x, y, z, true) : there;
                    if (got != null) {
                        tanks[t].fill(got, true);
                    }
                } else if (tankFull == FULL_PAUSE) {
                    return taken > 0 ? PUMP_OK : PUMP_WAIT;
                } else if (tankFull == FULL_VOID && active(ItemQuarryModuleSC.Kind.TRASH, F_TRASH)
                        && getEnergyStored() >= cost + TRASH_COST) {
                    removeEnergy(TRASH_COST);
                } else if (tankFull == FULL_BLOCK && (fluid == FluidRegistry.WATER || fluid == FluidRegistry.LAVA)) {
                    store(new ItemStack(fluid == FluidRegistry.WATER ? Blocks.ice : Blocks.obsidian));
                } else {
                    break;                                              // left in the world
                }
            }
            if (source || !(vein && !inside && fluidVeinKeepFlowing)) {
                removeEnergy(cost);
                if (worldObj.getBlock(x, y, z) == b) {
                    worldObj.setBlock(x, y, z, Blocks.air, 0, 2);       // no neighbour updates: no refilling
                }
                taken++;
            }
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nx = x + d.offsetX, ny = y + d.offsetY, nz = z + d.offsetZ;
                if (a != null && (nx < a[0] - r || nx > a[2] + r || nz < a[1] - r || nz > a[3] + r
                        || ny > a[4] + r || ny < Math.max(1, a[5] - r))) {
                    continue;
                }
                if (!worldObj.blockExists(nx, ny, nz) || !seen.add(posKey(nx, ny, nz))) {
                    continue;
                }
                open.add(new int[]{nx, ny, nz});
            }
        }
        if (taken > 0) {
            if (vein) {
                fluidVeinLast = taken;
                fluidVeinTotal += taken;
            }
            markDirty();
        }
        return taken > 0 ? PUMP_OK : PUMP_SKIP;
    }

    /** The open compartment that takes all of it: one already holding that fluid, or an empty one; -1 if none. */
    private int compartmentFor(FluidStack fs) {
        int open = unlockedTanks(), empty = -1, emptyPinned = -1;
        String name = fs.getFluid().getName();
        applyTankCapacity();
        for (int i = 0; i < open; i++) {
            if (tankPinned[i] != null && !tankPinned[i].equals(name)) {
                continue;                           // pinned to another fluid
            }
            FluidStack in = tanks[i].getFluid();
            if (in != null && in.amount > 0 && in.isFluidEqual(fs)) {
                if (tanks[i].fill(fs, false) >= fs.amount) {
                    return i;
                }
            } else if (in == null || in.amount <= 0) {
                if (tankPinned[i] != null && emptyPinned < 0) {
                    emptyPinned = i;
                } else if (tankPinned[i] == null && empty < 0) {
                    empty = i;
                }
            }
        }
        return emptyPinned >= 0 ? emptyPinned : empty;
    }

    public String getTankPinned(int i) {
        return tankPinned[i];
    }

    public boolean getTankAuto(int i) {
        return tankAuto[i];
    }

    /** EU to pour out a tank (0..3 the pump's, 4 the washing water). */
    public int clearCost(int i) {
        FluidTank t = i == TANKS ? water : tanks[i];
        return (t.getFluidAmount() + CLEAR_MB_PER_EU - 1) / CLEAR_MB_PER_EU;
    }

    public boolean isWashFromTanks() {
        return washFromTanks;
    }

    /** The washing tank takes water from the pump's tanks (up to AUTO_OUT every 10 ticks) while it has room. */
    private void feedWash() {
        if (!washFromTanks) {
            return;
        }
        water.setCapacity(waterCapacity());
        int room = water.getCapacity() - water.getFluidAmount(), budget = AUTO_OUT;
        for (int i = 0; i < unlockedTanks() && room > 0 && budget > 0; i++) {
            FluidStack in = tanks[i].getFluid();
            if (in == null || in.amount <= 0 || in.getFluid() != FluidRegistry.WATER) {
                continue;
            }
            int move = Math.min(Math.min(room, budget), in.amount);
            water.fill(new FluidStack(FluidRegistry.WATER, move), true);
            tanks[i].drain(move, true);
            room -= move;
            budget -= move;
            markDirty();
        }
    }

    /** Auto output: every compartment switched to it pushes its fluid into the handlers on its side(s). */
    private void autoOutput() {
        for (int i = 0; i < unlockedTanks(); i++) {
            if (!tankAuto[i] || tankSides[i] == SIDE_NONE || tanks[i].getFluidAmount() <= 0) {
                continue;
            }
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (tankSides[i] != SIDE_ANY && tankSides[i] != dir.ordinal() || tanks[i].getFluidAmount() <= 0) {
                    continue;
                }
                int nx = xCoord + dir.offsetX, ny = yCoord + dir.offsetY, nz = zCoord + dir.offsetZ;
                if (!worldObj.blockExists(nx, ny, nz)) {
                    continue;
                }
                TileEntity te = worldObj.getTileEntity(nx, ny, nz);
                if (!(te instanceof IFluidHandler) || te instanceof TileEntityQuarrySC) {
                    continue;
                }
                FluidStack offer = tanks[i].getFluid().copy();
                offer.amount = Math.min(AUTO_OUT, offer.amount);
                int took = ((IFluidHandler) te).fill(dir.getOpposite(), offer, true);
                if (took > 0) {
                    tanks[i].drain(took, true);
                    markDirty();
                }
            }
        }
    }

    /** Compartments open: LV 1, MV 2, HV 3, EV 4. */
    public int unlockedTanks() {
        return Math.min(TANKS, tierIndex() + 1);
    }

    /** Each compartment's size: 16 000 mB, +32 000 per tank module. */
    public int tankCapacity() {
        return TANK_BASE + TANK_PER_MODULE * moduleCount(ItemQuarryModuleSC.Kind.TANK);
    }

    /** The washing water tank's size: 32 000 mB, +32 000 per tank module. */
    public int waterCapacity() {
        return WATER_BASE + TANK_PER_MODULE * moduleCount(ItemQuarryModuleSC.Kind.TANK);
    }

    private void applyTankCapacity() {
        int cap = tankCapacity();
        for (FluidTank t : tanks) {
            t.setCapacity(cap);
        }
        water.setCapacity(waterCapacity());
    }

    public FluidTank getTank(int i) {
        return tanks[i];
    }

    public int getTankSide(int i) {
        return tankSides[i];
    }

    public List<String> getFluidFilter() {
        return fluidFilter;
    }

    public int getFluidFilterMode() {
        return fluidFilterMode;
    }

    public int getFluidFilterRemove() {
        return fluidFilterRemove;
    }

    public int getTankFull() {
        return tankFull;
    }

    /** Does the fluid filter let the pump take it? */
    public boolean fluidWanted(Fluid f) {
        if (fluidFilterMode == FF_OFF) {
            return true;
        }
        boolean listed = fluidFilter.contains(f.getName());
        return fluidFilterMode == FF_ONLY ? listed : !listed;
    }

    private void addToFluidFilter(Fluid f) {
        if (f != null && !fluidFilter.contains(f.getName()) && fluidFilter.size() < FLUID_FILTER_MAX) {
            fluidFilter.add(f.getName());
        }
    }

    /** May a compartment give its fluid out on that side? */
    private boolean sideAllows(int i, ForgeDirection from) {
        int s = tankSides[i];
        if (s == SIDE_NONE) {
            return false;
        }
        return s == SIDE_ANY || from == ForgeDirection.UNKNOWN || s == from.ordinal();
    }

    /**
     * The fluid of a liquid block. Forge only knows water and lava by their still blocks, but a
     * source turns into the flowing block as soon as anything next to it changes - both count.
     */
    public static Fluid fluidOf(Block block) {
        if (block == Blocks.water || block == Blocks.flowing_water) {
            return FluidRegistry.WATER;
        }
        if (block == Blocks.lava || block == Blocks.flowing_lava) {
            return FluidRegistry.LAVA;
        }
        if (block instanceof net.minecraftforge.fluids.IFluidBlock) {
            return ((net.minecraftforge.fluids.IFluidBlock) block).getFluid();
        }
        return FluidRegistry.lookupFluidForBlock(block);
    }

    /** @return the vein blocks dug along with it */
    private int mine(int x, int y, int z, Block block) {
        boolean ore = isOre(block, worldObj.getBlockMetadata(x, y, z));
        int oreMeta = worldObj.getBlockMetadata(x, y, z);
        mineOne(x, y, z, block);
        if (ore && active(ItemQuarryModuleSC.Kind.VEIN, F_VEIN)) {
            return vein(x, y, z, block, oreMeta);
        }
        return 0;
    }

    /**
     * The rest of an ore vein: every touching block of the same ore (26 neighbours), up to VEIN_MAX
     * blocks within 32 of the first - inside the area or not - each paid for like any block.
     */
    private int vein(int x0, int y0, int z0, Block ore, int meta) {
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<int[]>();
        java.util.Set<Long> seen = new java.util.HashSet<Long>();
        open.add(new int[]{x0, y0, z0});
        int taken = 0;
        while (!open.isEmpty() && taken < VEIN_MAX) {
            int[] c = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = c[0] + dx, y = c[1] + dy, z = c[2] + dz;
                        if (y < 1 || y > 255 || Math.abs(x - x0) > 32 || Math.abs(z - z0) > 32 || !seen.add(posKey(x, y, z))) {
                            continue;
                        }
                        if (!worldObj.blockExists(x, y, z) || worldObj.getBlock(x, y, z) != ore || worldObj.getBlockMetadata(x, y, z) != meta) {
                            continue;
                        }
                        int cost = costFor(ore.getBlockHardness(worldObj, x, y, z));
                        if (getEnergyStored() < cost || forbidden(x, y, z) || taken >= VEIN_MAX || headKind() == null) {
                            return taken;
                        }
                        removeEnergy(cost);
                        mineOne(x, y, z, ore);
                        taken++;
                        open.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return taken;
    }

    /** Lava and water next to a dug block turn to stone (the pump's own sources in the area are left to it). */
    private void guardFluids(int x, int y, int z) {
        if (!active(ItemQuarryModuleSC.Kind.FLUID_GUARD, F_FLUID_GUARD)) {
            return;
        }
        int[] a = area();
        boolean pump = active(ItemQuarryModuleSC.Kind.PUMP, F_PUMP);
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int nx = x + d.offsetX, ny = y + d.offsetY, nz = z + d.offsetZ;
            if (!worldObj.blockExists(nx, ny, nz) || !worldObj.getBlock(nx, ny, nz).getMaterial().isLiquid()) {
                continue;
            }
            boolean inArea = a != null && nx >= a[0] && nx <= a[2] && nz >= a[1] && nz <= a[3] && ny <= a[4] && ny >= a[5];
            if (pump && inArea && worldObj.getBlockMetadata(nx, ny, nz) == 0) {
                continue;
            }
            if (forbidden(nx, ny, nz)) {
                continue;
            }
            if (getEnergyStored() < GUARD_COST) {
                return;
            }
            removeEnergy(GUARD_COST);
            worldObj.setBlock(nx, ny, nz, Blocks.stone, 0, 3);
        }
    }

    private void mineOne(int x, int y, int z, Block block) {
        int meta = worldObj.getBlockMetadata(x, y, z);
        List<ItemStack> drops = new ArrayList<ItemStack>();
        net.minecraftforge.common.util.FakePlayer fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((WorldServer) worldObj);
        ItemStack silked = silk() && block.canSilkHarvest(worldObj, fake, x, y, z, meta) ? silkStack(block, meta) : null;
        if (silked != null) {
            drops.add(silked);
        } else {
            drops.addAll(block.getDrops(worldObj, x, y, z, meta, fortune()));
            if (active(ItemQuarryModuleSC.Kind.MAGNET, F_MAG_XP)) {
                xp += Math.max(0, block.getExpDrop(worldObj, meta, fortune()));   // as a player's pickaxe: none with silk touch
            }
        }
        boolean ore = isOre(block, meta);
        if (!has(F_SILENT) || moduleCount(ItemQuarryModuleSC.Kind.SILENT) == 0) {
            worldObj.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
        }
        replaceAfter(x, y, z);
        guardFluids(x, y, z);
        if (ore) {
            String key = Item.itemRegistry.getNameForObject(Item.getItemFromBlock(block)) + "@" + block.damageDropped(meta);
            Integer n = oreCounts.get(key);
            if (n != null) {
                if (n <= 1) {
                    oreCounts.remove(key);
                } else {
                    oreCounts.put(key, n - 1);
                }
            }
        }
        for (ItemStack d : drops) {
            for (ItemStack out : process(d)) {
                store(out);
            }
        }
        mined++;
        ItemStack head = slots[SLOT_HEAD];
        if (head != null && head.getMaxDamage() > 0) {
            head.setItemDamage(head.getItemDamage() + (active(ItemQuarryModuleSC.Kind.DOUBLE, F_DOUBLE) ? 2 : 1));
            if (head.getItemDamage() >= head.getMaxDamage()) {
                slots[SLOT_HEAD] = null;
                worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.break", 1F, 0.8F);
                warn(Status.NO_HEAD);
            }
        }
        markDirty();
    }

    /** A block position packed into a long (26 bits x, 12 y, 26 z: the whole +-30M world without clashes). */
    private static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static java.lang.reflect.Method stackedBlock;

    /**
     * What silk touch gives, as a player's pickaxe would (Block.createStackedBlock): a sideways log
     * is a plain log, a top slab a slab, lit redstone ore the ore - not the raw metadata. null: none.
     */
    private static ItemStack silkStack(Block block, int meta) {
        try {
            if (stackedBlock == null) {
                stackedBlock = cpw.mods.fml.relauncher.ReflectionHelper.<Block>findMethod(Block.class, null,
                        new String[]{"createStackedBlock", "func_149644_j"}, int.class);
            }
            ItemStack s = (ItemStack) stackedBlock.invoke(block, meta);
            return s == null || s.getItem() == null ? null : s;   // a block with no item: fall back to getDrops
        } catch (Exception e) {
            Item item = Item.getItemFromBlock(block);
            return item == null ? null : new ItemStack(item, 1, item.getHasSubtypes() ? meta : 0);
        }
    }

    /** Crushing, washing, then the centrifuge - the machines' own recipes, one item at a time. */
    private List<ItemStack> process(ItemStack drop) {
        List<ItemStack> stage = new ArrayList<ItemStack>();
        if (drop == null) {
            return stage;
        }
        stage.add(drop);
        if (isOre(drop) && active(ItemQuarryModuleSC.Kind.CRUSH, F_CRUSH)) {
            stage = through(stage, MachineType.CRUSHER, false);
            if (active(ItemQuarryModuleSC.Kind.WASH, F_WASH)) {
                stage = through(stage, MachineType.ORE_WASHER, true);
            }
            if (active(ItemQuarryModuleSC.Kind.CENTRIFUGE, F_CENTRIFUGE)) {
                stage = through(stage, MachineType.CENTRIFUGE, false);
            }
        }
        return stage;
    }

    /** Every item of `in` that has a recipe in `type` goes through it (byproducts by their chance); the rest stays as it is. */
    private List<ItemStack> through(List<ItemStack> in, MachineType type, boolean water) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack s : in) {
            for (int n = 0; n < s.stackSize; n++) {
                ItemStack one = single(s);
                MachineRecipe r = RecipeRegistry.findMatch(type, new ItemStack[]{one}, water ? this.water.getFluid() : null, null);
                int need = r == null || r.fluidInputA == null ? 0 : r.fluidInputA.amount;
                if (r == null || this.water.getFluidAmount() < need) {
                    out.add(one);
                    continue;
                }
                if (need > 0) {
                    this.water.drain(need, true);
                }
                for (ItemStack o : r.outputs) {
                    if (o != null) {
                        out.add(o.copy());
                    }
                }
                for (int b = 0; b < r.byproducts.length; b++) {
                    if (worldObj.rand.nextFloat() < r.byproductChances[b]) {
                        out.add(r.byproducts[b].copy());
                    }
                }
            }
        }
        return out;
    }

    private static ItemStack single(ItemStack s) {
        ItemStack c = s.copy();
        c.stackSize = 1;
        return c;
    }

    // ------------------------------------------------------------------ the buffer

    /** Free buffer slots an auto-stopped quarry waits for (a row: it doesn't flicker on and off a slot at a time). */
    public static final int AUTOSTOP_RESUME = 9;

    private int freeSlots() {
        int n = 0;
        for (int i = 0; i < BUFFER; i++) {
            if (slots[i] == null) {
                n++;
            }
        }
        return n;
    }

    private int firstEmpty() {
        for (int i = 0; i < BUFFER; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /** Into the buffer (topping up first); what doesn't fit waits in the overflow. */
    private void store(ItemStack stack) {
        if (active(ItemQuarryModuleSC.Kind.TRASH, F_TRASH) && trash(stack) && getEnergyStored() >= TRASH_COST * stack.stackSize) {
            removeEnergy(TRASH_COST * stack.stackSize);
            return;
        }
        ItemStack s = stack.copy();
        for (int i = 0; i < BUFFER && s.stackSize > 0; i++) {
            ItemStack b = slots[i];
            if (b != null && b.isItemEqual(s) && ItemStack.areItemStackTagsEqual(b, s) && b.stackSize < b.getMaxStackSize()) {
                int move = Math.min(s.stackSize, b.getMaxStackSize() - b.stackSize);
                b.stackSize += move;
                s.stackSize -= move;
            }
        }
        for (int i = 0; i < BUFFER && s.stackSize > 0; i++) {
            if (slots[i] == null) {
                slots[i] = s.copy();
                s.stackSize = 0;
            }
        }
        if (s.stackSize > 0) {
            overflow.add(s);
        }
    }

    /** What the trash module destroys: cobblestone, stone, dirt, gravel, sand, netherrack. */
    public static boolean trash(ItemStack s) {
        Block b = Block.getBlockFromItem(s.getItem());
        return b == Blocks.cobblestone || b == Blocks.stone || b == Blocks.dirt || b == Blocks.gravel || b == Blocks.sand
                || b == Blocks.netherrack || b == Blocks.grass;
    }

    private void flushOverflow() {
        List<ItemStack> copy = new ArrayList<ItemStack>(overflow);
        overflow.clear();
        for (ItemStack s : copy) {
            store(s);
        }
    }

    /** One stack every 5 ticks into each neighbouring inventory (or only the chosen side). */
    private void pushOut() {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (outSide >= 0 && d.ordinal() != outSide) {
                continue;
            }
            if (!worldObj.blockExists(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ)) {
                continue;                                  // don't load the neighbour's chunk every tick
            }
            TileEntity te = worldObj.getTileEntity(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
            if (!(te instanceof IInventory) || te instanceof TileEntityQuarrySC) {
                continue;
            }
            int moved = 0;
            for (int i = 0; i < BUFFER; i++) {
                if (slots[i] != null) {
                    int left = com.sc.util.InvUtilSC.insert((IInventory) te, d, slots[i]);
                    if (left != slots[i].stackSize) {
                        if (left <= 0) {
                            slots[i] = null;
                        } else {
                            slots[i].stackSize = left;
                        }
                        markDirty();
                        if (++moved >= 5) {                // runs every 5 ticks: up to 5 stacks a side, as fast as before;
                            break;                         // one it won't take doesn't block the rest
                        }
                    }
                }
            }
        }
    }

    private void magnet() {
        boolean items = active(ItemQuarryModuleSC.Kind.MAGNET, F_MAG_ITEMS), orbs = active(ItemQuarryModuleSC.Kind.MAGNET, F_MAG_XP);
        int[] a = area();
        if ((!items && !orbs) || a == null) {
            return;
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(a[0], a[5], a[1], a[2] + 1, a[4] + 4, a[3] + 1);
        if (items) {
            for (Object o : worldObj.getEntitiesWithinAABB(EntityItem.class, box)) {
                EntityItem e = (EntityItem) o;
                if (!e.isDead && e.getEntityItem() != null && firstEmpty() >= 0) {
                    store(e.getEntityItem());
                    e.setDead();
                }
            }
        }
        if (orbs) {
            for (Object o : worldObj.getEntitiesWithinAABB(EntityXPOrb.class, box)) {
                EntityXPOrb e = (EntityXPOrb) o;
                if (!e.isDead) {
                    xp += e.getXpValue();
                    e.setDead();
                }
            }
        }
    }

    // ------------------------------------------------------------------ the scanner

    public boolean hasScanner() {
        return slots[SLOT_SCANNER] != null && slots[SLOT_SCANNER].getItem() == com.sc.init.ModItems.oreScanner;
    }

    /** Looks through the area a slice at a time (4096 blocks a tick) for ore. */
    private void scanStep() {
        if (isExo()) {
            scanDirty = false;
            scanY = 0;
            return;
        }
        int[] a = area();
        if (!hasScanner() || a == null) {
            oreCounts.clear();
            scanDirty = false;
            scanY = 0;
            return;
        }
        if (scanDirty) {
            oreCounts.clear();
            scanY = a[4];
            scanIndex = 0;
            scanDirty = false;
            scanned = 0;
            scanTotal = (long) (a[4] - a[5] + 1) * cellsPerLayer(a);
        }
        int budget = 1024, cells = cellsPerLayer(a);
        while (budget-- > 0 && scanY >= a[5]) {
            if (getEnergyStored() < SCAN_COST) {
                return;                            // waits for energy
            }
            removeEnergy(SCAN_COST);
            scanned++;
            int[] c = cell(a, scanIndex);
            if (c != null && worldObj.blockExists(c[0], scanY, c[1])) {
                Block b = worldObj.getBlock(c[0], scanY, c[1]);
                int meta = worldObj.getBlockMetadata(c[0], scanY, c[1]);
                if (!b.isAir(worldObj, c[0], scanY, c[1]) && isOre(b, meta)) {
                    String key = Item.itemRegistry.getNameForObject(Item.getItemFromBlock(b)) + "@" + b.damageDropped(meta);
                    Integer n = oreCounts.get(key);
                    oreCounts.put(key, n == null ? 1 : n + 1);
                }
            }
            if (++scanIndex >= cells) {
                scanIndex = 0;
                scanY--;
            }
        }
        if (scanY < a[5]) {
            scanY = 0;
            scanned = scanTotal;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        } else if (worldObj.getTotalWorldTime() % 100 == 0) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);   // the counts so far
        }
    }

    /** Scan progress 0-100, -1 before a scan (it only runs when the Map tab's Scan button is pressed). */
    public int scanPercent() {
        return scanTotal <= 0 ? -1 : (int) Math.min(100, scanned * 100 / scanTotal);
    }

    /** Old counts go (a new area, the scanner taken out, a reload); nothing is scanned until asked. */
    private void clearScan() {
        oreCounts.clear();
        scanDirty = false;
        scanY = 0;
        scanned = 0;
        scanTotal = 0;
    }

    private int scanPercentClient = -1;

    public int getScanPercentClient() {
        return scanPercentClient;
    }

    public void setScanPercentClient(int p) {
        scanPercentClient = p;
    }

    public Map<String, Integer> getOreCounts() {
        return oreCounts;
    }

    // ------------------------------------------------------------------ chunk loading (the chunk-keeping module)

    /**
     * The chunk-keeping module's Forge ticket. It holds two chunks at most: the quarry's own (so it
     * ticks with no player near) and the one the cursor digs in now - not the whole area: a 64 x 64
     * area is up to 25 chunks, Forge's default cap for a ticket (maximumChunksPerTicket), and every
     * forced chunk costs the server a loaded, ticking chunk. The quarry digs one cell at a time, so
     * the chunk under the cursor is all it needs; the next one is forced when the cursor crosses into
     * it (one tick of WAITING_CHUNK). The vein / fluid vein past that chunk still stop at unloaded ones.
     */
    private ForgeChunkManager.Ticket ticket;
    private long ticketRetryAt;
    /** The cursor's chunk this ticket holds (null: none yet) - the own chunk is held from the ticket's start. */
    private ChunkCoordIntPair heldDig;
    /** Chunks held now (the client's copy through ContainerQuarrySC). */
    private int chunksHeld;

    /** Held while it works: running, switched on, the module in, and digging (or waiting for the chunk it digs in). */
    private boolean wantChunks() {
        if (!running || !powerOn || moduleCount(ItemQuarryModuleSC.Kind.CHUNK_LOADER) <= 0) {
            return false;
        }
        return status == Status.RUNNING || status == Status.WAITING_CHUNK || status == Status.BLOCKED_BY_FIELD
                || status == Status.REPAIRING;
    }

    /** The chunk of the cell the cursor is on (the rig: none - it digs nothing). */
    private ChunkCoordIntPair cursorChunk() {
        int[] a = area();
        if (a == null || layerY < a[5]) {
            return null;
        }
        int[] c = cell(a, cursor);
        return c == null ? heldDig : new ChunkCoordIntPair(c[0] >> 4, c[1] >> 4);   // a cell the shape leaves out: keep the last
    }

    /**
     * World tick the chunks were last wanted. A running quarry that only waits a moment (the energy for
     * the next block, a full buffer or tank) keeps its ticket CHUNK_GRACE ticks: on a weak feed the status
     * flips RUNNING / NO_POWER every few ticks, and each flip released the ticket and asked for a new one.
     */
    private long chunksWantedAt;
    public static final int CHUNK_GRACE = 200;

    private void holdChunks() {
        long now = worldObj.getTotalWorldTime();
        if (wantChunks()) {
            chunksWantedAt = now;
        } else {
            boolean waiting = running && powerOn && moduleCount(ItemQuarryModuleSC.Kind.CHUNK_LOADER) > 0
                    && now >= chunksWantedAt && now - chunksWantedAt < CHUNK_GRACE;
            if (!waiting) {
                releaseChunks();
            }
            return;
        }
        if (ticket == null) {
            if (now < ticketRetryAt) {
                return;
            }
            ticket = ForgeChunkManager.requestTicket(com.sc.SCMod.instance, worldObj, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                ticketRetryAt = now + 100;              // Forge's ticket limit for the mod: try again later
                return;
            }
            NBTTagCompound d = ticket.getModData();
            d.setString("Kind", "quarry");
            d.setInteger("x", xCoord);
            d.setInteger("y", yCoord);
            d.setInteger("z", zCoord);
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
            heldDig = null;
        }
        ChunkCoordIntPair own = new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4);
        ChunkCoordIntPair dig = cursorChunk();
        if (dig == null ? heldDig != null : !dig.equals(heldDig)) {
            if (heldDig != null && !heldDig.equals(own)) {
                ForgeChunkManager.unforceChunk(ticket, heldDig);
            }
            heldDig = dig;
            if (dig != null && !dig.equals(own) && ticket.getChunkList().size() < ticket.getMaxChunkListDepth()) {
                ForgeChunkManager.forceChunk(ticket, dig);
                worldObj.getChunkFromChunkCoords(dig.chunkXPos, dig.chunkZPos);   // loaded now, not on the next pass
            }
        }
        chunksHeld = ticket.getChunkList().size();
    }

    private void releaseChunks() {
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
        heldDig = null;
        chunksHeld = 0;
    }

    /** After a world load (ChunkLoaderSC): this quarry's ticket, kept - its own chunk again; the cursor's on the next tick. */
    public void adoptTicket(ForgeChunkManager.Ticket t) {
        if (ticket != null && ticket != t) {
            ForgeChunkManager.releaseTicket(ticket);
        }
        ticket = t;
        ChunkCoordIntPair own = new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4);
        for (ChunkCoordIntPair c : new ArrayList<ChunkCoordIntPair>(t.getChunkList())) {
            if (!c.equals(own)) {
                ForgeChunkManager.unforceChunk(t, c);
            }
        }
        ForgeChunkManager.forceChunk(t, own);
        heldDig = null;
        chunksHeld = t.getChunkList().size();
        if (worldObj != null) {
            chunksWantedAt = worldObj.getTotalWorldTime();   // saved mid-wait (no power...): the grace starts over
        }
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) {
            releaseChunks();                            // broken / replaced: its chunks go
        }
        super.invalidate();
    }

    public int getChunksHeld() {
        return chunksHeld;
    }

    public void setChunksHeldClient(int n) {
        chunksHeld = n;
    }

    // ------------------------------------------------------------------ owner and warnings

    public int getFacing() {
        return facing;
    }

    public void setFacing(int side) {
        if (side >= 2 && side <= 5) {
            facing = side;
            markDirty();
        }
    }

    /** Breaking it: what waited for room in the buffer drops too. */
    public void dropOverflow() {
        for (ItemStack s : overflow) {
            worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, s));
        }
        overflow.clear();
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String name) {
        owner = name == null ? "" : name;
        ownerId = "";
        ownerProfile = null;
        if (worldObj != null && !worldObj.isRemote && !owner.isEmpty()) {
            EntityPlayerMP p = net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().func_152612_a(owner);
            if (p != null) {
                ownerId = p.getUniqueID().toString();          // the placer is here: their UUID, no lookup needed
            }
        }
        markDirty();
    }

    /** The owner's UUID once known for sure (saved; "" until then - an old quarry, or not looked up yet). */
    private String ownerId = "";
    /** The owner's profile for the protection check (not saved), and the name it was made for. */
    private com.mojang.authlib.GameProfile ownerProfile;
    private String ownerProfileFor;

    /**
     * The owner as a GameProfile, for the fake player of the protection check (BreakEvent): other mods'
     * claims see who the quarry digs for. Null without an owner. The UUID: the saved one, else the
     * player online, else the offline UUID of the name (not saved: the real one is taken once the owner
     * is online).
     */
    private com.mojang.authlib.GameProfile ownerProfile() {
        if (owner.isEmpty()) {
            return null;
        }
        if (ownerProfile != null && owner.equals(ownerProfileFor)) {
            return ownerProfile;
        }
        java.util.UUID id = null;
        if (!ownerId.isEmpty()) {
            try {
                id = java.util.UUID.fromString(ownerId);
            } catch (IllegalArgumentException e) {
                ownerId = "";
            }
        }
        net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
        if (id == null && srv != null) {
            EntityPlayerMP p = srv.getConfigurationManager().func_152612_a(owner);
            com.mojang.authlib.GameProfile g = p != null ? p.getGameProfile() : null;   // no profile-cache lookup: it may ask Mojang's server and stall the tick
            if (g != null && g.getId() != null) {
                id = g.getId();
                ownerId = id.toString();
                markDirty();
            }
        }
        if (id == null) {
            id = EntityPlayer.func_146094_a(new com.mojang.authlib.GameProfile(null, owner));   // what an offline server gives the name
            return new com.mojang.authlib.GameProfile(id, owner);   // not kept: the real UUID is taken once the owner is online
        }
        ownerProfile = new com.mojang.authlib.GameProfile(id, owner);
        ownerProfileFor = owner;
        return ownerProfile;
    }

    public boolean allowed(EntityPlayer p) {
        return owner.isEmpty() || owner.equalsIgnoreCase(p.getCommandSenderName())
                || p instanceof EntityPlayerMP && net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().func_152596_g(((EntityPlayerMP) p).getGameProfile());
    }

    private void warn(Status s) {
        int flag = s == Status.BUFFER_FULL ? F_WARN_BUFFER : s == Status.NO_HEAD ? F_WARN_HEAD : s == Status.NO_POWER ? F_WARN_ENERGY
                : s == Status.DONE ? F_WARN_DONE : 0;
        int bit = 1 << s.ordinal();
        if (flag == 0 || !has(flag) || (warned & bit) != 0 || owner.isEmpty()) {
            return;
        }
        warned |= bit;
        EntityPlayerMP p = net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().func_152612_a(owner);
        if (p != null) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.quarry.warn." + s.name().toLowerCase(java.util.Locale.ROOT),
                    xCoord, yCoord, zCoord));
        }
    }

    // ------------------------------------------------------------------ screen actions (QuarryNetSC)

    public static final int A_RUN = 0, A_RESET = 1, A_REDSTONE = 2, A_POWER = 3, A_XP = 4, A_SIZE_X = 5, A_SIZE_Z = 6,
            A_OFF_X = 7, A_OFF_Z = 8, A_BOTTOM = 9, A_SHAPE = 10, A_REPLACE = 11, A_FLAG = 12, A_FORTUNE = 13,
            A_FILTER_MODE = 14, A_OUT_SIDE = 15, A_SHOW = 16, A_VFLAG = 17, A_BRIGHT = 18, A_COLOR_FRAME = 19,
            A_COLOR_PLANE = 20, A_SCAN = 21, A_TANK_SIDE = 22, A_TANK_CLEAR = 23, A_TANK_TO_FILTER = 24,
            A_FF_MODE = 25, A_FF_REMOVE = 26, A_TANK_FULL = 27, A_FF_HAND = 28, A_FF_CLEAR = 29, A_FF_DELETE = 30,
            A_TANK_PIN = 31, A_TANK_AUTO = 32, A_FVEIN = 33, A_FVEIN_RANGE = 34, A_FVEIN_FLOWING = 35, A_WASH_FEED = 36,
            A_SWITCH = 37, A_BATTERY_MODE = 38;

    private static final com.sc.util.SoundsSC.Loop DRILL_SOUND = new com.sc.util.SoundsSC.Loop("quarry.drill", 0.6F, 1F),
            RIG_SOUND = new com.sc.util.SoundsSC.Loop("quarry.beam", 0.55F, 1F);

    public void action(EntityPlayer p, int action, int value) {
        boolean area = false;
        switch (action) {
            case A_RUN:
                running = !running;
                autoStopped = false;                 // the player's own start / stop: no auto-resume
                if (running && done) {
                    resetCursor();
                }
                if (running) {
                    skippedPrivate = 0;
                }
                warned = 0;
                break;
            case A_RESET: resetCursor(); break;
            case A_REDSTONE: redstone = (redstone + 1) % 3; break;
            case A_POWER: powerMode = (powerMode + 1) % 3; break;
            case A_SWITCH:
                powerOn = !powerOn;
                com.sc.util.SoundsSC.powerClick(this, powerOn);
                break;
            case A_BATTERY_MODE: cycleBatteryMode(); break;
            case A_XP:
                if (xp > 0) {
                    p.addExperience(xp);
                    xp = 0;
                }
                break;
            case A_SIZE_X: sizeX = clamp(sizeX + value, 1, maxSize()); area = true; break;
            case A_SIZE_Z: sizeZ = clamp(sizeZ + value, 1, maxSize()); area = true; break;
            case A_OFF_X: offX = clamp(offX + value, -64, 64); area = true; break;
            case A_OFF_Z: offZ = clamp(offZ + value, -64, 64); area = true; break;
            case A_BOTTOM: bottomY = clamp(bottomY + value, 1, Math.max(1, yCoord)); area = true; break;
            case A_SHAPE: shape = (shape + 1) % 3; area = true; break;
            case A_REPLACE: replace = (replace + 1) % 4; break;
            case A_FLAG:
                if (value >= 0 && value < FLAG_COUNT) {
                    flags ^= 1 << value;
                    if ((1 << value) == F_SILK && has(F_SILK)) {
                        flags &= ~F_FORTUNE;         // silk touch and fortune don't mix
                    } else if ((1 << value) == F_FORTUNE && has(F_FORTUNE)) {
                        flags &= ~F_SILK;
                    }
                    if ((1 << value) == F_RADIUS) {
                        area = true;
                    }
                }
                break;
            case A_FORTUNE: fortuneLevel = clamp(fortuneLevel + value, 1, 5); break;
            case A_FILTER_MODE: filterMode = (filterMode + 1) % 4; break;
            case A_OUT_SIDE: outSide = outSide >= 5 ? -1 : outSide + 1; break;
            case A_SHOW: show = (show + 1) % 4; break;
            case A_VFLAG: vflags ^= value & (V_DASH | V_PLANE); break;
            case A_BRIGHT: brightness = brightness % 4 + 1; break;
            case A_COLOR_FRAME: colorFrame = value & 0xFFFFFF; break;
            case A_COLOR_PLANE: colorPlane = value & 0xFFFFFF; break;
            case A_SCAN: scanDirty = true; break;
            case A_TANK_SIDE:
                if (value >= 0 && value < TANKS) {
                    tankSides[value] = tankSides[value] >= SIDE_NONE ? SIDE_ANY : tankSides[value] + 1;
                }
                break;
            case A_TANK_CLEAR:                      // 0..3 a compartment, 4 the washing water - paid in EU
                if (value >= 0 && value <= TANKS) {
                    int cost = clearCost(value);
                    if (getEnergyStored() >= cost) {
                        removeEnergy(cost);
                        (value == TANKS ? water : tanks[value]).setFluid(null);
                    }
                }
                break;
            case A_TANK_PIN:
                if (value >= 0 && value < TANKS) {
                    if (tankPinned[value] != null) {
                        tankPinned[value] = null;
                    } else if (tanks[value].getFluid() != null) {
                        tankPinned[value] = tanks[value].getFluid().getFluid().getName();
                    }
                }
                break;
            case A_FVEIN: fluidVeinOn = !fluidVeinOn; break;
            case A_WASH_FEED: washFromTanks = !washFromTanks; break;
            case A_FVEIN_RANGE: {                   // 8 -> 16 -> 32 -> 64 (as far as the tier goes) -> 8
                int max = FLUID_VEIN_RANGE[tierIndex()];
                int now = fluidVeinReach();
                fluidVeinRange = now >= max ? 8 : now * 2;
                break;
            }
            case A_FVEIN_FLOWING: fluidVeinKeepFlowing = !fluidVeinKeepFlowing; break;
            case A_TANK_AUTO:
                if (value >= 0 && value < TANKS) {
                    tankAuto[value] = !tankAuto[value];
                }
                break;
            case A_TANK_TO_FILTER:
                if (value >= 0 && value < TANKS && tanks[value].getFluid() != null) {
                    addToFluidFilter(tanks[value].getFluid().getFluid());
                }
                break;
            case A_FF_MODE: fluidFilterMode = (fluidFilterMode + 1) % 3; break;
            case A_FF_REMOVE: fluidFilterRemove = 1 - fluidFilterRemove; break;
            case A_TANK_FULL: tankFull = (tankFull + 1) % 4; break;
            case A_FF_HAND: {                       // the fluid in the item in hand (a bucket, a cell...)
                ItemStack held = p.getCurrentEquippedItem();
                FluidStack in = held == null ? null
                        : held.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem
                        ? ((net.minecraftforge.fluids.IFluidContainerItem) held.getItem()).getFluid(held)
                        : net.minecraftforge.fluids.FluidContainerRegistry.getFluidForFilledItem(held);
                if (in != null) {
                    addToFluidFilter(in.getFluid());
                }
                break;
            }
            case A_FF_CLEAR: fluidFilter.clear(); break;
            case A_FF_DELETE:
                if (value >= 0 && value < fluidFilter.size()) {
                    fluidFilter.remove(value);
                }
                break;
            default: return;
        }
        if (area) {
            resetCursor();
        }
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ getters for the screen / renderer

    public int getSizeX() { return sizeX; }
    public int getSizeZ() { return sizeZ; }
    public int getOffX() { return offX; }
    public int getOffZ() { return offZ; }
    public int getBottomY() { return bottomY; }
    public int getShape() { return shape; }
    public int getReplace() { return replace; }
    public int getFlags() { return flags; }
    public int getFortuneLevel() { return fortuneLevel; }
    public int getPowerMode() { return powerMode; }
    public int getRedstone() { return redstone; }

    /** The quarry keeps its own redstone setting; the power switch's button (GuiPowerSC) shows it. */
    @Override
    public int getRedstoneMode() { return redstone; }

    /** Switched off, it takes no energy - as a machine. */
    @Override
    public int demandedEnergy() {
        return powerOn ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }
    public int getOutSide() { return outSide; }
    public int getFilterMode() { return filterMode; }
    public int getShow() { return show; }
    public int getVflags() { return vflags; }
    public int getBrightness() { return brightness; }
    public int getColorFrame() { return colorFrame; }
    public int getColorPlane() { return colorPlane; }
    public boolean isRunning() { return running; }
    public boolean isDone() { return done; }
    public int getLayerY() { return layerY; }
    public int getCursor() { return cursor; }
    public long getMined() { return mined; }
    public int getXp() { return xp; }
    public Status getStatus() { return status; }
    public int getLastCost() { return lastCost; }
    public boolean isAutoStopped() { return autoStopped; }
    public int getSkippedPrivate() { return skippedPrivate; }

    /** Client: the private-field count from the screen's sync. */
    public void setSkippedPrivateClient(int n) {
        skippedPrivate = n;
    }
    /** All the pump's fluid, for the summary line. */
    public int pumpedTotal() {
        int n = 0;
        for (FluidTank t : tanks) {
            n += t.getFluidAmount();
        }
        return n;
    }
    public FluidTank getWater() { return water; }
    public ItemStack[] getFilter() { return filter; }

    /** Client sync (ContainerQuarrySC). */
    public void setLiveClient(int statusOrdinal, int layer, int cur, int minedLow, int xpValue, int cost, boolean run) {
        status = Status.values()[Math.max(0, Math.min(Status.values().length - 1, statusOrdinal))];
        layerY = layer;
        cursor = cur;
        mined = minedLow;
        xp = xpValue;
        lastCost = cost;
        running = run;
    }

    public void setFilterStack(int i, ItemStack s) {
        if (i >= 0 && i < FILTER_SLOTS) {
            filter[i] = s == null ? null : single(s);
            markDirty();
        }
    }

    /** Blocks left to look at in the area (for the forecast). */
    public long blocksLeft() {
        int[] a = area();
        if (a == null || layerY < a[5]) {
            return 0;
        }
        long perLayer = cellsPerLayer(a);
        return (long) (layerY - a[5]) * perLayer + (perLayer - cursor);
    }

    // ------------------------------------------------------------------ IInventory

    @Override
    public int getSizeInventory() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOTS ? slots[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack s = getStackInSlot(slot);
        if (s == null) {
            return null;
        }
        ItemStack out = s.stackSize <= count ? s : s.splitStack(count);
        if (out == s) {
            slots[slot] = null;
        }
        onSlotChange(slot);
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS) {
            slots[slot] = stack;
            onSlotChange(slot);
        }
    }

    private void onSlotChange(int slot) {
        markDirty();
        if (slot == SLOT_CARD || slot >= FIRST_UPGRADE && slot < FIRST_UPGRADE + UPGRADES) {
            if (worldObj != null && !worldObj.isRemote) {
                if (slot == SLOT_CARD) {
                    resetCursor();
                }
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
        if (slot == SLOT_SCANNER && worldObj != null && !worldObj.isRemote) {
            clearScan();                                   // not on the client: the screen's slot sync would wipe the counts it was sent
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.quarry";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack s) {
        if (s == null) {
            return false;
        }
        if (slot < BUFFER) {
            return false;                   // the quarry fills its buffer itself
        }
        if (slot == SLOT_BATTERY) {
            return com.sc.item.BatteryFeedSC.accepts(s);
        }
        if (slot < FIRST_UPGRADE + UPGRADES) {
            if (slot - FIRST_UPGRADE >= unlockedUpgrades()) {
                return false;                       // locked at this tier
            }
            if (s.getItem() instanceof ItemQuarryModuleSC) {
                ItemQuarryModuleSC.Kind k = ItemQuarryModuleSC.kindOf(s);
                if (isExo()) {
                    return k.scope != ItemQuarryModuleSC.QUARRY;
                }
                return k.scope != ItemQuarryModuleSC.EXO && tierIndex() >= k.minTier;
            }
            if (s.getItem() instanceof com.sc.item.ItemUpgradeSC) {
                UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
                return t == UpgradeType.TRANSFORMER || t == UpgradeType.UNIVERSAL_TRANSFORMER || t == UpgradeType.ENERGY_STORAGE;
            }
            return false;
        }
        if (slot >= FIRST_LENS) {
            return isExo() && slot - FIRST_LENS < unlockedLenses() && s.getItem() instanceof com.sc.item.ItemOreLensSC;
        }
        if (isExo()) {
            return false;                           // no head, scanner or card in the rig
        }
        if (slot == SLOT_HEAD) {
            return s.getItem() instanceof ItemDrillHeadSC;
        }
        if (slot == SLOT_SCANNER) {
            return s.getItem() == com.sc.init.ModItems.oreScanner;
        }
        return s.getItem() instanceof ItemAreaCardSC;
    }

    private static final int[] BUFFER_SLOTS = new int[BUFFER + 1];

    static {
        for (int i = 0; i < BUFFER; i++) {
            BUFFER_SLOTS[i] = i;
        }
        BUFFER_SLOTS[BUFFER] = SLOT_BATTERY;
    }

    /** Pipes and hoppers take from the buffer; a charged battery goes in, an empty one comes out. */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return BUFFER_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_BATTERY && slots[SLOT_BATTERY] == null && com.sc.item.BatteryFeedSC.accepts(stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot < BUFFER || slot == SLOT_BATTERY && com.sc.item.BatteryFeedSC.chargeOf(stack) <= 0;
    }

    // ------------------------------------------------------------------ fluids: pumped out, water for washing in

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return resource != null && resource.getFluid() == FluidRegistry.WATER ? TileEntityMachineSC.safeFill(water, resource, doFill) : 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null) {
            return null;
        }
        for (int i = 0; i < TANKS; i++) {
            if (sideAllows(i, from) && resource.isFluidEqual(tanks[i].getFluid())) {
                return tanks[i].drain(resource.amount, doDrain);
            }
        }
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        for (int i = 0; i < TANKS; i++) {
            if (sideAllows(i, from) && tanks[i].getFluidAmount() > 0) {
                return tanks[i].drain(maxDrain, doDrain);
            }
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid == FluidRegistry.WATER;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        for (int i = 0; i < TANKS; i++) {
            FluidStack in = tanks[i].getFluid();
            if (sideAllows(i, from) && in != null && in.amount > 0 && (fluid == null || in.getFluid() == fluid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        List<FluidTankInfo> info = new ArrayList<FluidTankInfo>();
        for (int i = 0; i < TANKS; i++) {
            if (sideAllows(i, from)) {
                info.add(tanks[i].getInfo());
            }
        }
        info.add(water.getInfo());
        return info.toArray(new FluidTankInfo[info.size()]);
    }

    // ------------------------------------------------------------------ the item keeps it all (wrench / breaking)

    /**
     * Settings, energy, tanks, the mined count and stored XP for the dropped item (the buffer drops
     * as items). Not the digging position: placed again, it starts its area over (readFromItem).
     */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeSettings(nbt);
        nbt.setInteger("EnergySC", getEnergyStored());
        nbt.setBoolean("Running", running);
        nbt.setLong("Mined", mined);
        nbt.setInteger("Xp", xp);
        nbt.setBoolean("PowerOff", !powerOn);
        nbt.setInteger("BatteryMode", batteryMode);
        writeTanks(nbt);
        if (water.getFluidAmount() > 0) {
            nbt.setTag("Water", water.writeToNBT(new NBTTagCompound()));
        }
        return nbt;
    }

    public void readFromItem(NBTTagCompound nbt) {
        readSettings(nbt);
        restoreEnergy(nbt.getInteger("EnergySC"));
        readTanks(nbt);
        if (nbt.hasKey("Water")) {
            water.readFromNBT(nbt.getCompoundTag("Water"));
        }
        mined = nbt.getLong("Mined");
        xp = nbt.getInteger("Xp");
        powerOn = !nbt.getBoolean("PowerOff");
        batteryMode = nbt.getInteger("BatteryMode") & 1;
        resetCursor();
        markDirty();
    }

    // ------------------------------------------------------------------ NBT

    private void writeSettings(NBTTagCompound nbt) {
        int[] s = {sizeX, sizeZ, offX, offZ, bottomY, shape, replace, flags, fortuneLevel, powerMode, redstone, outSide,
                filterMode, show, vflags, brightness, colorFrame, colorPlane, 2};
        nbt.setIntArray("Settings", s);
        nbt.setString("Owner", owner);
        nbt.setInteger("Facing", facing);
        NBTTagList f = new NBTTagList();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filter[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                filter[i].writeToNBT(t);
                f.appendTag(t);
            }
        }
        nbt.setTag("Filter", f);
        nbt.setIntArray("TankSides", tankSides.clone());
        nbt.setInteger("FluidFilterMode", fluidFilterMode);
        nbt.setInteger("FluidFilterRemove", fluidFilterRemove);
        nbt.setInteger("TankFull", tankFull);
        NBTTagList ff = new NBTTagList();
        for (String name : fluidFilter) {
            ff.appendTag(new net.minecraft.nbt.NBTTagString(name));
        }
        nbt.setTag("FluidFilter", ff);
        NBTTagList pins = new NBTTagList();
        byte[] auto = new byte[TANKS];
        for (int i = 0; i < TANKS; i++) {
            pins.appendTag(new net.minecraft.nbt.NBTTagString(tankPinned[i] == null ? "" : tankPinned[i]));
            auto[i] = (byte) (tankAuto[i] ? 1 : 0);
        }
        nbt.setTag("TankPinned", pins);
        nbt.setByteArray("TankAuto", auto);
        nbt.setBoolean("FluidVeinOff", !fluidVeinOn);
        nbt.setBoolean("WashFromTanks", washFromTanks);
        nbt.setBoolean("FluidVeinKeepFlowing", fluidVeinKeepFlowing);
        nbt.setInteger("FluidVeinRange", fluidVeinRange);
        nbt.setInteger("FluidVeinTotal", fluidVeinTotal);
    }

    /** The pump's compartments; a save from before them had one tank, "Pumped" - it becomes the first. */
    private void writeTanks(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < TANKS; i++) {
            if (tanks[i].getFluidAmount() > 0) {
                NBTTagCompound t = tanks[i].writeToNBT(new NBTTagCompound());
                t.setByte("Tank", (byte) i);
                list.appendTag(t);
            }
        }
        nbt.setTag("Tanks", list);
    }

    private void readTanks(NBTTagCompound nbt) {
        for (FluidTank t : tanks) {
            t.setFluid(null);
        }
        if (nbt.hasKey("Tanks")) {
            NBTTagList list = nbt.getTagList("Tanks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                int k = t.getByte("Tank");
                if (k >= 0 && k < TANKS) {
                    tanks[k].setCapacity(Integer.MAX_VALUE);
                    tanks[k].readFromNBT(t);
                }
            }
        } else if (nbt.hasKey("Pumped")) {
            tanks[0].readFromNBT(nbt.getCompoundTag("Pumped"));
        }
        applyTankCapacityLater = true;
    }

    /** The modules may not be read yet when the tanks are: sizes are set on the first tick. */
    private boolean applyTankCapacityLater;

    private void readSettings(NBTTagCompound nbt) {
        int[] s = nbt.getIntArray("Settings");
        if (s.length >= 18) {
            sizeX = s[0]; sizeZ = s[1]; offX = s[2]; offZ = s[3]; bottomY = s[4]; shape = s[5]; replace = s[6]; flags = s[7];
            fortuneLevel = s[8]; powerMode = s[9]; redstone = s[10]; outSide = s[11]; filterMode = s[12]; show = s[13];
            vflags = s[14]; brightness = s[15]; colorFrame = s[16]; colorPlane = s[17];
            if (s.length < 19) {
                flags |= NEW_FLAGS_DEFAULT;        // saved before the second batch of modules
            }
        }
        owner = nbt.getString("Owner");
        if (nbt.hasKey("TankSides")) {
            int[] ts = nbt.getIntArray("TankSides");
            for (int i = 0; i < TANKS && i < ts.length; i++) {
                tankSides[i] = Math.max(SIDE_ANY, Math.min(SIDE_NONE, ts[i]));
            }
        }
        fluidFilterMode = Math.max(0, Math.min(2, nbt.getInteger("FluidFilterMode")));
        fluidFilterRemove = nbt.getInteger("FluidFilterRemove") != 0 ? 1 : 0;
        tankFull = Math.max(0, Math.min(3, nbt.getInteger("TankFull")));
        NBTTagList pins = nbt.getTagList("TankPinned", 8);
        byte[] auto = nbt.getByteArray("TankAuto");
        for (int i = 0; i < TANKS; i++) {
            String pin = i < pins.tagCount() ? pins.getStringTagAt(i) : "";
            tankPinned[i] = pin.isEmpty() ? null : pin;
            tankAuto[i] = i < auto.length && auto[i] != 0;
        }
        fluidVeinOn = !nbt.getBoolean("FluidVeinOff");
        washFromTanks = nbt.getBoolean("WashFromTanks");
        fluidVeinKeepFlowing = nbt.getBoolean("FluidVeinKeepFlowing");
        fluidVeinRange = nbt.hasKey("FluidVeinRange") ? Math.max(8, Math.min(64, nbt.getInteger("FluidVeinRange"))) : 64;
        fluidVeinTotal = nbt.getInteger("FluidVeinTotal");
        fluidFilter.clear();
        NBTTagList ff = nbt.getTagList("FluidFilter", 8);
        for (int i = 0; i < ff.tagCount() && fluidFilter.size() < FLUID_FILTER_MAX; i++) {
            fluidFilter.add(ff.getStringTagAt(i));
        }
        if (nbt.hasKey("Facing")) {
            facing = Math.max(2, Math.min(5, nbt.getInteger("Facing")));
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filter[i] = null;
        }
        NBTTagList f = nbt.getTagList("Filter", 10);
        for (int i = 0; i < f.tagCount(); i++) {
            NBTTagCompound t = f.getCompoundTagAt(i);
            int slot = t.getByte("Slot");
            if (slot >= 0 && slot < FILTER_SLOTS) {
                filter[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tierFromMeta = !nbt.hasKey("TierSC");
        readSettings(nbt);
        running = nbt.getBoolean("Running");
        done = nbt.getBoolean("Done");
        layerY = nbt.getInteger("LayerY");
        cursor = nbt.getInteger("Cursor");
        mined = nbt.getLong("Mined");
        xp = nbt.getInteger("Xp");
        progress = nbt.getDouble("Progress");
        status = Status.values()[Math.max(0, Math.min(Status.values().length - 1, nbt.getInteger("Status")))];
        readTanks(nbt);
        water.readFromNBT(nbt.getCompoundTag("Water"));
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = null;
        }
        NBTTagList list = nbt.getTagList("Slots", 10);
        boolean oldLayout = !nbt.hasKey("Layout");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot") & 0xFF;
            if (oldLayout && slot >= 35 && slot <= 37) {
                slot += SLOT_HEAD - 35;               // head / scanner / card after 8 module slots, now after 18
            }
            if (slot < SLOTS) {
                slots[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        overflow.clear();
        NBTTagList ov = nbt.getTagList("Overflow", 10);
        for (int i = 0; i < ov.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(ov.getCompoundTagAt(i));
            if (s != null) {
                overflow.add(s);
            }
        }
        autoStopped = nbt.getBoolean("AutoStopped");
        skippedPrivate = nbt.getInteger("SkippedPrivate");
        ownerId = nbt.getString("OwnerId");
        ownerProfile = null;
        clearScan();                                   // an old save's "OreList" is just left unread
        NBTTagList hl = nbt.getTagList("HaulLog", 10);
        for (int i = 0; i < hl.tagCount(); i++) {
            oreCounts.put(hl.getCompoundTagAt(i).getString("K"), hl.getCompoundTagAt(i).getInteger("N"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        writeSettings(nbt);
        nbt.setBoolean("Running", running);
        nbt.setBoolean("Done", done);
        nbt.setInteger("LayerY", layerY);
        nbt.setInteger("Cursor", cursor);
        nbt.setLong("Mined", mined);
        nbt.setInteger("Xp", xp);
        nbt.setDouble("Progress", progress);
        nbt.setInteger("Status", status.ordinal());
        nbt.setBoolean("AutoStopped", autoStopped);
        nbt.setInteger("SkippedPrivate", skippedPrivate);
        nbt.setString("OwnerId", ownerId);
        writeTanks(nbt);
        nbt.setTag("Water", water.writeToNBT(new NBTTagCompound()));
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                slots[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        nbt.setTag("Slots", list);
        nbt.setInteger("Layout", 2);
        NBTTagList ov = new NBTTagList();
        for (ItemStack s : overflow) {
            ov.appendTag(s.writeToNBT(new NBTTagCompound()));
        }
        nbt.setTag("Overflow", ov);
        if (isExo()) {
            NBTTagList hl = new NBTTagList();
            for (Map.Entry<String, Integer> e : oreCounts.entrySet()) {
                NBTTagCompound t = new NBTTagCompound();
                t.setString("K", e.getKey());
                t.setInteger("N", e.getValue());
                hl.appendTag(t);
            }
            nbt.setTag("HaulLog", hl);
        }
    }

    /** The layer the last description packet carried (updateEntity re-sends only when it changes). */
    private int layerSent = Integer.MIN_VALUE;

    /** Settings, the digging layer and the scanned ore counts reach the client with the block. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        layerSent = layerY;
        NBTTagCompound nbt = new NBTTagCompound();
        writeSettings(nbt);
        nbt.setInteger("TierSC", getTier().ordinal());
        nbt.setBoolean("Running", running);
        nbt.setBoolean("Done", done);
        nbt.setInteger("LayerY", layerY);
        nbt.setInteger("Cursor", cursor);
        nbt.setBoolean("PowerOff", !powerOn);
        nbt.setInteger("BatteryMode", batteryMode);
        NBTTagList list = new NBTTagList();
        for (int i : new int[]{SLOT_CARD}) {
            if (slots[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                slots[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        for (int i = FIRST_UPGRADE; i < FIRST_UPGRADE + UPGRADES; i++) {
            if (slots[i] != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                slots[i].writeToNBT(t);
                list.appendTag(t);
            }
        }
        nbt.setTag("Slots", list);
        // only how much ore there is - never where (the scanner is no X-ray)
        NBTTagList c = new NBTTagList();
        for (Map.Entry<String, Integer> e : oreCounts.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("K", e.getKey());
            t.setInteger("N", e.getValue());
            c.appendTag(t);
        }
        nbt.setTag("OreCounts", c);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager manager, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        readSettings(nbt);
        setTier(Tier.values()[Math.max(0, Math.min(Tier.values().length - 1, nbt.getInteger("TierSC")))]);
        running = nbt.getBoolean("Running");
        done = nbt.getBoolean("Done");
        layerY = nbt.getInteger("LayerY");
        cursor = nbt.getInteger("Cursor");
        powerOn = !nbt.getBoolean("PowerOff");
        batteryMode = nbt.getInteger("BatteryMode") & 1;
        for (int i = FIRST_UPGRADE; i <= SLOT_CARD; i++) {
            if (i != SLOT_HEAD && i != SLOT_SCANNER) {
                slots[i] = null;
            }
        }
        NBTTagList list = nbt.getTagList("Slots", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot") & 0xFF;
            if (slot < SLOTS) {
                slots[slot] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        oreCounts.clear();
        NBTTagList c = nbt.getTagList("OreCounts", 10);
        for (int i = 0; i < c.tagCount(); i++) {
            oreCounts.put(c.getCompoundTagAt(i).getString("K"), c.getCompoundTagAt(i).getInteger("N"));
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 128 * 128;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }
}
