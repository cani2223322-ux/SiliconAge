package com.sc.tileentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.conduit.RedstoneMode;
import com.sc.energy.CableType;
import com.sc.energy.EnergyNetSC;
import com.sc.energy.ExplosionLogic;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModBlocks;
import com.sc.util.CorrosiveFluids;
import com.sc.util.PipeType;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyConductor;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * A conduit bundle (Ender IO style): up to one cable, one fluid pipe and one pneumatic tube in
 * the same block, each in its own slot (ConduitKind), each with a mode per side (ConduitMode).
 * Conduits only join conduits of the same kind AND type - copper cable to copper cable, steel
 * pipe to steel pipe - so every network has one rating.
 *
 * Cable (§9.1/§12): without IC2 it's a node of EnergyNetSC's networks; with IC2 it is an IC2
 * IEnergyConductor (registered only while the bundle has a cable) and IC2 routes.
 * Pipe (§12.2/§12.3): a small buffer (= its mB/t rating) filled by the machines next to it and by
 * its extract connectors; each tick it delivers up to its rating straight into the tanks on the
 * normal connectors of its pipe network, nearest first, taking turns (Ender IO style - no
 * segment-to-segment pumping, so nothing sloshes back and forth). A corrosive fluid eats through
 * anything but PTFE.
 * Tube (§12.2, "4 предмета/тик, round-robin"): moves up to 4 items a tick from each inventory it
 * extracts from to the inventories on the tube network, nearest first, taking turns.
 */
@Optional.Interface(iface = "ic2.api.energy.tile.IEnergyConductor", modid = Reference.IC2_MODID)
public class TileEntityConduitBundleSC extends TileEntity implements IFluidHandler, IEnergyConductor, com.sc.energy.Ic2LoadQueueSC.Deferred {

    // TODO(design doc §12.3): 200 ticks was a TODO-filled default, not confirmed.
    private static final int CORROSION_TICKS = 200;
    private static final int ITEMS_PER_TICK = 4;
    private static final int MAX_TUBE_NODES = 1024;

    /** Bumped whenever any tube / pipe network may have changed shape; each rebuilds its route list then. */
    private static int tubeVersion;
    private static int pipeVersion;

    private CableType cable;
    private PipeType pipe;
    private boolean tube;
    private final byte[] modes = new byte[ConduitKind.values().length * 6];
    /** Filters and speed upgrades of the tube connectors (Ender IO style), see ConnectorInventorySC. */
    private final com.sc.conduit.ConnectorInventorySC connectorItems = new com.sc.conduit.ConnectorInventorySC(this);

    // Ender IO's connector options, per conduit and side (kind * 6 + side)
    /** Ender IO's default channel colour: green (dye 2). */
    public static final int DEFAULT_COLOR = 2;
    private static final int SLOTS = ConduitKind.values().length * 6;
    private final byte[] redstone = new byte[SLOTS];
    private final byte[] extractColor = new byte[SLOTS];
    private final byte[] insertColor = new byte[SLOTS];
    private final int[] priority = new int[SLOTS];
    private final boolean[] roundRobin = new boolean[SLOTS];

    {
        java.util.Arrays.fill(extractColor, (byte) DEFAULT_COLOR);
        java.util.Arrays.fill(insertColor, (byte) DEFAULT_COLOR);
        java.util.Arrays.fill(roundRobin, true);
    }
    private FluidTank tank;
    private int corrosionTimer;

    // tube and pipe routing (server only, rebuilt on demand)
    private List<Route> routes;
    private int routesVersion = -1;
    /** Round robin position of each extracting side (a shared counter let two sources skip each other's turns). */
    private final int[] nextRoute = new int[6];
    private int nextSource;
    /** Last redstone state seen - IC2 is told about redstone-controlled cable sides only when it flips. */
    private Boolean lastPowered;
    private List<Route> fluidRoutes;
    private int fluidRoutesVersion = -1;
    private int nextFluidRoute;

    // client fluid sync
    private String sentFluid;
    private int sentAmount;
    private int syncCooldown;

    private boolean registeredEnergy;
    /**
     * Saved with the extract / insert connector modes. Bundles from before them are migrated once
     * (migrateModes): a pipe or tube on a machine's old output face (south; the top too for a
     * second output fluid) used to receive its products, so it becomes an extract connector.
     */
    private boolean modesV2 = true;

    // ------------------------------------------------------------------ parts

    public CableType getCable() {
        return cable;
    }

    public PipeType getPipe() {
        return pipe;
    }

    public boolean hasTube() {
        return tube;
    }

    public boolean has(ConduitKind kind) {
        switch (kind) {
            case CABLE: return cable != null;
            case PIPE: return pipe != null;
            default: return tube;
        }
    }

    public boolean isEmpty() {
        return cable == null && pipe == null && !tube;
    }

    /** Sub-type ordinal of a part (cable / pipe type), 0 for the tube. */
    public int subtype(ConduitKind kind) {
        switch (kind) {
            case CABLE: return cable == null ? -1 : cable.ordinal();
            case PIPE: return pipe == null ? -1 : pipe.ordinal();
            default: return tube ? 0 : -1;
        }
    }

    /** Adds a part if this bundle doesn't have one of that kind yet. @return whether it did */
    public boolean addPart(ConduitKind kind, int subtype) {
        if (has(kind)) {
            return false;
        }
        switch (kind) {
            case CABLE:
                cable = CableType.values()[clamp(subtype, CableType.values().length)];
                break;
            case PIPE:
                pipe = PipeType.values()[clamp(subtype, PipeType.values().length)];
                tank = new FluidTank(pipe.throughput);
                break;
            default:
                tube = true;
        }
        for (int i = 0; i < 6; i++) {
            int k = kind.ordinal() * 6 + i;
            modes[k] = 0;
            redstone[k] = 0;
            extractColor[k] = (byte) DEFAULT_COLOR;
            insertColor[k] = (byte) DEFAULT_COLOR;
            priority[k] = 0;
            roundRobin[k] = true;
        }
        partsChanged(kind);
        return true;
    }

    public com.sc.conduit.ConnectorInventorySC getConnectorItems() {
        return connectorItems;
    }

    /** Removes a part (a tube's filters and upgrades fall out). @return the item it was made from, or null if it wasn't there */
    public ItemStack removePart(ConduitKind kind) {
        ItemStack drop = partStack(kind);
        if (drop == null) {
            return null;
        }
        if (kind == ConduitKind.TUBE) {
            spillConnectorItems();
        }
        switch (kind) {
            case CABLE: cable = null; break;
            case PIPE: pipe = null; tank = null; corrosionTimer = 0; break;
            default: tube = false;
        }
        partsChanged(kind);
        return drop;
    }

    /** The item a part is placed from (the cable / pipe / tube items of the mod). */
    public ItemStack partStack(ConduitKind kind) {
        if (!has(kind)) {
            return null;
        }
        switch (kind) {
            case CABLE: return new ItemStack(ModBlocks.cableSC, 1, cable.ordinal());
            case PIPE: return new ItemStack(ModBlocks.pipeSC, 1, pipe.ordinal());
            default: return new ItemStack(ModBlocks.tubeItemPneumatic);
        }
    }

    /**
     * The conduits the block drops when it goes some other way than part by part. The tube's filters
     * and upgrades are not in here: they spill as the real stacks when the block is removed
     * (spillConnectorItems from breakBlock) - copies here would double them for anyone who still
     * had the connector menu open.
     */
    public List<ItemStack> allPartStacks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ConduitKind kind : ConduitKind.values()) {
            ItemStack s = partStack(kind);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Throws the connector slots' items into the world (server side). */
    public void spillConnectorItems() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        for (ItemStack s : connectorItems.clear()) {
            net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(worldObj,
                    xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, s);
            item.delayBeforeCanPickup = 10;
            worldObj.spawnEntityInWorld(item);
        }
    }

    private void partsChanged(ConduitKind kind) {
        if (worldObj != null && !worldObj.isRemote) {
            if (kind == ConduitKind.CABLE) {
                reregisterEnergy();
            }
            shapeChanged(kind);
        }
        sync();
    }

    /** A tube / pipe network may look different now (server only; the counters are shared statics). */
    private static void shapeChanged(ConduitKind kind) {
        if (kind == ConduitKind.TUBE) {
            tubeVersion++;
        } else if (kind == ConduitKind.PIPE) {
            pipeVersion++;
        }
    }

    private static int clamp(int i, int n) {
        return i >= 0 && i < n ? i : 0;
    }

    // ------------------------------------------------------------------ modes and connections

    public ConduitMode mode(ConduitKind kind, ForgeDirection dir) {
        return ConduitMode.of(modes[kind.ordinal() * 6 + dir.ordinal()]);
    }

    public void setMode(ConduitKind kind, ForgeDirection dir, ConduitMode mode) {
        modes[kind.ordinal() * 6 + dir.ordinal()] = (byte) mode.ordinal();
        if (worldObj != null && !worldObj.isRemote) {
            if (kind == ConduitKind.CABLE) {
                reregisterEnergy();
            }
            shapeChanged(kind);
        }
        sync();
    }

    // ---- connector options (the menu's second row) ----

    private static int slot(ConduitKind kind, ForgeDirection dir) {
        return kind.ordinal() * 6 + dir.ordinal();
    }

    public RedstoneMode redstoneMode(ConduitKind kind, ForgeDirection dir) {
        return RedstoneMode.of(redstone[slot(kind, dir)]);
    }

    public int extractColor(ConduitKind kind, ForgeDirection dir) {
        return extractColor[slot(kind, dir)] & 15;
    }

    public int insertColor(ConduitKind kind, ForgeDirection dir) {
        return insertColor[slot(kind, dir)] & 15;
    }

    public int priority(ConduitKind kind, ForgeDirection dir) {
        return priority[slot(kind, dir)];
    }

    public boolean roundRobin(ConduitKind kind, ForgeDirection dir) {
        return roundRobin[slot(kind, dir)];
    }

    public void setRedstoneMode(ConduitKind kind, ForgeDirection dir, RedstoneMode mode) {
        redstone[slot(kind, dir)] = (byte) mode.ordinal();
        optionsChanged(kind);
    }

    public void setExtractColor(ConduitKind kind, ForgeDirection dir, int color) {
        extractColor[slot(kind, dir)] = (byte) (color & 15);
        optionsChanged(kind);
    }

    public void setInsertColor(ConduitKind kind, ForgeDirection dir, int color) {
        insertColor[slot(kind, dir)] = (byte) (color & 15);
        optionsChanged(kind);
    }

    public void setPriority(ConduitKind kind, ForgeDirection dir, int value) {
        priority[slot(kind, dir)] = Math.max(-99, Math.min(99, value));
        optionsChanged(kind);
    }

    public void setRoundRobin(ConduitKind kind, ForgeDirection dir, boolean value) {
        roundRobin[slot(kind, dir)] = value;
        optionsChanged(kind);
    }

    private void optionsChanged(ConduitKind kind) {
        if (worldObj != null && !worldObj.isRemote) {
            if (kind == ConduitKind.CABLE) {
                reregisterEnergy();
            }
            shapeChanged(kind);
        }
        sync();
    }

    /** Whether an extracting connector may work now (its redstone control against a signal at the bundle). */
    public boolean redstoneAllows(ConduitKind kind, ForgeDirection dir) {
        RedstoneMode mode = redstoneMode(kind, dir);
        if (mode == RedstoneMode.ALWAYS || worldObj == null) {
            return mode != RedstoneMode.NEVER;
        }
        return mode.allows(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
    }

    /** Connector menu: set a side's mode; a link to the next bundle is switched on both ends. */
    public void setModeLinked(ConduitKind kind, ForgeDirection dir, ConduitMode mode) {
        setMode(kind, dir, mode);
        TileEntityConduitBundleSC other = bundleAt(dir);
        if (other != null && other.has(kind)) {
            other.setMode(kind, dir.getOpposite(), mode == ConduitMode.OFF ? ConduitMode.OFF : ConduitMode.NORMAL);
        }
    }

    /** Wrench click on a side: next mode; a link between two bundles is switched on both ends. @return the new mode */
    public ConduitMode cycleMode(ConduitKind kind, ForgeDirection dir) {
        TileEntityConduitBundleSC other = bundleAt(dir);
        boolean connector = other == null;
        ConduitMode next = mode(kind, dir).next(kind, connector);
        setMode(kind, dir, next);
        if (other != null && other.has(kind)) {
            other.setMode(kind, dir.getOpposite(), next == ConduitMode.OFF ? ConduitMode.OFF : ConduitMode.NORMAL);
        }
        return next;
    }

    public boolean open(ConduitKind kind, ForgeDirection dir) {
        return has(kind) && mode(kind, dir) != ConduitMode.OFF;
    }

    public boolean cableOpen(ForgeDirection dir) {
        return open(ConduitKind.CABLE, dir);
    }

    private TileEntity neighbour(ForgeDirection dir) {
        if (worldObj == null) {
            return null;
        }
        int x = xCoord + dir.offsetX, y = yCoord + dir.offsetY, z = zCoord + dir.offsetZ;
        if (!worldObj.blockExists(x, y, z)) {
            return null;
        }
        return worldObj.getTileEntity(x, y, z);
    }

    private TileEntityConduitBundleSC bundleAt(ForgeDirection dir) {
        TileEntity te = neighbour(dir);
        return te instanceof TileEntityConduitBundleSC ? (TileEntityConduitBundleSC) te : null;
    }

    /** Joined to the same kind and type of conduit in the neighbouring bundle. */
    public boolean linksTo(ConduitKind kind, ForgeDirection dir) {
        if (!open(kind, dir)) {
            return false;
        }
        TileEntityConduitBundleSC other = bundleAt(dir);
        return other != null && other.subtype(kind) == subtype(kind) && other.open(kind, dir.getOpposite());
    }

    /** Plugged into a machine / tank / inventory on that side (drawn with a connector plate). */
    public boolean connectorAt(ConduitKind kind, ForgeDirection dir) {
        if (!open(kind, dir)) {
            return false;
        }
        TileEntity te = neighbour(dir);
        if (te == null || te instanceof TileEntityConduitBundleSC) {
            return false;
        }
        switch (kind) {
            case CABLE: return te instanceof TileEntityEnergyBase || isIc2EnergyTile(te);
            case PIPE: return te instanceof IFluidHandler;
            default: return te instanceof IInventory;
        }
    }

    public boolean connects(ConduitKind kind, ForgeDirection dir) {
        return linksTo(kind, dir) || connectorAt(kind, dir);
    }

    private static final Class<?> IC2_ENERGY_TILE = ic2EnergyTile();

    private static Class<?> ic2EnergyTile() {
        if (!Loader.isModLoaded(Reference.IC2_MODID)) {
            return null;
        }
        try {
            return Class.forName("ic2.api.energy.tile.IEnergyTile");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean isIc2EnergyTile(TileEntity te) {
        return IC2_ENERGY_TILE != null && IC2_ENERGY_TILE.isInstance(te);
    }

    private void migrateModes() {
        modesV2 = true;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity te = neighbour(dir);
            if (!(te instanceof TileEntityMachineSC)) {
                continue;
            }
            // the machine's face that touches us
            ForgeDirection face = dir.getOpposite();
            boolean oldOutput = face == ForgeDirection.SOUTH
                    || (face == ForgeDirection.UP && com.sc.machine.RecipeRegistry.usesTank(((TileEntityMachineSC) te).getMachineType(), 3));
            if (!oldOutput) {
                continue;
            }
            for (ConduitKind kind : new ConduitKind[]{ConduitKind.PIPE, ConduitKind.TUBE}) {
                if (has(kind) && mode(kind, dir) == ConduitMode.NORMAL) {
                    setMode(kind, dir, ConduitMode.EXTRACT);
                }
            }
        }
        markDirty();
    }

    /** A neighbouring block was placed or removed - connections may have changed. */
    public void onNeighbourChanged() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (tube) {
            tubeVersion++;
        }
        if (pipe != null) {
            pipeVersion++;
        }
        if (cable != null && !Loader.isModLoaded(Reference.IC2_MODID)) {
            EnergyNetSC.instance().invalidate();
        }
        if (cable != null && Loader.isModLoaded(Reference.IC2_MODID)) {
            boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
            if (lastPowered == null || lastPowered != powered) {   // null: not known since loading - IC2 may hold the old state
                for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                    if (redstoneMode(ConduitKind.CABLE, dir) != RedstoneMode.ALWAYS) {
                        reregisterEnergy();      // a redstone-controlled side has switched
                        break;
                    }
                }
            }
            lastPowered = powered;
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (isEmpty()) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord);   // nothing left to see, click or break
            return;
        }
        if (!modesV2) {
            migrateModes();
        }
        if (pipe != null) {
            tickCorrosion();
            if (pipe != null) {
                extractFluid();
                deliverFluid();
                syncFluid();
            }
        }
        if (tube) {
            moveItems();
        }
        // every other tick, by position (getNodeStats allocates): the cable burns within 2 ticks of a packet
        // above its tier; the top tier only under Industrial Upgrade - its sources go past SV, IC2's don't
        if (cable != null && registeredEnergy && Loader.isModLoaded(Reference.IC2_MODID)
                && ((worldObj.getTotalWorldTime() + xCoord + zCoord) & 1) == 0
                && (cable.tier != com.sc.energy.Tier.max() || Loader.isModLoaded(TileEntityEnergyStorageSC.IU_MODID))) {
            checkIc2Overvoltage();
        }
    }

    // ---- pipe ----

    private FluidTank tank() {
        if (tank == null) {
            tank = new FluidTank(pipe == null ? 1000 : pipe.throughput);
        }
        return tank;
    }

    public FluidStack getFluid() {
        return pipe == null || tank == null ? null : tank.getFluid();
    }

    public int getFluidCapacity() {
        return pipe == null ? 0 : pipe.throughput;
    }

    /** §12.3: a corrosive fluid in anything but PTFE eats the pipe away (only the pipe - the rest of the bundle stays). */
    private void tickCorrosion() {
        FluidStack held = tank().getFluid();
        boolean exposed = !pipe.chemicallyResistant && held != null && CorrosiveFluids.isCorrosive(held.getFluid().getName());
        if (exposed) {
            if (++corrosionTimer >= CORROSION_TICKS) {
                worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.fizz", 0.8F, 1.0F);
                removePart(ConduitKind.PIPE);
                if (isEmpty()) {
                    worldObj.setBlockToAir(xCoord, yCoord, zCoord);
                }
            }
        } else if (corrosionTimer > 0) {
            corrosionTimer--;
        }
    }

    /** Extract connectors pull from their tank, up to the pipe's rating per tick. */
    private void extractFluid() {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (!mode(ConduitKind.PIPE, dir).extracts(ConduitKind.PIPE) || !connectorAt(ConduitKind.PIPE, dir)
                    || !redstoneAllows(ConduitKind.PIPE, dir)) {
                continue;
            }
            int room = tank().getCapacity() - tank().getFluidAmount();
            if (room <= 0) {
                return;
            }
            IFluidHandler source = (IFluidHandler) neighbour(dir);
            ForgeDirection face = dir.getOpposite();
            FluidStack held = tank().getFluid();
            FluidStack drained = held != null
                    ? source.drain(face, new FluidStack(held.getFluid(), room), false)
                    : source.drain(face, room, false);
            if (drained == null || drained.amount <= 0) {
                continue;
            }
            // only what the network can deliver somewhere: a full or unwilling target leaves the fluid in its source
            int wanted = deliverable(drained, room);
            if (wanted <= 0) {
                continue;
            }
            drained = drained.copy();
            drained.amount = Math.min(drained.amount, wanted);
            int fits = tank().fill(drained, false);
            if (fits <= 0) {
                continue;
            }
            FluidStack taken = held != null
                    ? source.drain(face, new FluidStack(held.getFluid(), fits), true)
                    : source.drain(face, fits, true);
            if (taken != null) {
                tank().fill(taken, true);
                fluidChanged();
            }
        }
    }

    /**
     * How much of that fluid the pipe network's insert connectors would take now (simulated), up to
     * max - less what's already waiting in this pipe. Not back into this bundle's own extracting sides.
     */
    private int deliverable(FluidStack fluid, int max) {
        int total = 0;
        for (Route r : fluidRoutes()) {
            if (total >= max) {
                break;
            }
            if (r.owner == this && mode(ConduitKind.PIPE, r.dir).extracts(ConduitKind.PIPE)) {
                continue;
            }
            if (!worldObj.blockExists(r.x, r.y, r.z)) {
                continue;
            }
            TileEntity te = worldObj.getTileEntity(r.x, r.y, r.z);
            if (!(te instanceof IFluidHandler) || te instanceof TileEntityConduitBundleSC) {
                continue;
            }
            IFluidHandler target = (IFluidHandler) te;
            ForgeDirection face = r.dir.getOpposite();
            if (!target.canFill(face, fluid.getFluid())) {
                continue;
            }
            FluidStack offer = fluid.copy();
            offer.amount = max - total;
            total += Math.max(0, target.fill(face, offer, false));
        }
        FluidStack waiting = tank().getFluid();
        return Math.max(0, Math.min(max, total) - (waiting == null ? 0 : waiting.amount));
    }

    /**
     * Delivers up to the pipe's rating per tick into the tanks on the normal connectors of this
     * pipe network, nearest first, taking turns between them.
     */
    private void deliverFluid() {
        FluidStack held = tank().getFluid();
        if (held == null || held.amount <= 0) {
            return;
        }
        List<Route> list = fluidRoutes();
        int budget = pipe.throughput;
        int turn = nextFluidRoute;
        int[] order = visitOrder(list, turn);
        for (int k = 0; k < order.length && budget > 0 && held != null; k++) {
            Route r = list.get(order[k]);
            if (r.owner == this && mode(ConduitKind.PIPE, r.dir).extracts(ConduitKind.PIPE)) {
                continue;                     // in / out: not straight back into the tank it came out of
            }
            if (!worldObj.blockExists(r.x, r.y, r.z)) {
                continue;
            }
            TileEntity te = worldObj.getTileEntity(r.x, r.y, r.z);
            if (!(te instanceof IFluidHandler) || te instanceof TileEntityConduitBundleSC) {
                continue;
            }
            IFluidHandler target = (IFluidHandler) te;
            ForgeDirection face = r.dir.getOpposite();
            if (!target.canFill(face, held.getFluid())) {
                continue;
            }
            FluidStack offer = held.copy();
            offer.amount = Math.min(budget, held.amount);
            int accepted = target.fill(face, offer, true);
            if (accepted > 0) {
                tank().drain(accepted, true);
                budget -= accepted;
                held = tank().getFluid();
                nextFluidRoute = turn + k + 1;
                fluidChanged();
            }
        }
    }

    /** Every tank on a normal connector of this pipe network (same pipe type), nearest first. */
    private List<Route> fluidRoutes() {
        if (fluidRoutes != null && fluidRoutesVersion == pipeVersion) {
            return fluidRoutes;
        }
        List<Route> out = new ArrayList<Route>();
        Set<TileEntityConduitBundleSC> seen = new HashSet<TileEntityConduitBundleSC>();
        ArrayDeque<TileEntityConduitBundleSC> queue = new ArrayDeque<TileEntityConduitBundleSC>();
        queue.add(this);
        seen.add(this);
        while (!queue.isEmpty() && seen.size() < MAX_TUBE_NODES) {
            TileEntityConduitBundleSC b = queue.poll();
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (b.linksTo(ConduitKind.PIPE, dir)) {
                    TileEntityConduitBundleSC next = b.bundleAt(dir);
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                } else if (b.connectorAt(ConduitKind.PIPE, dir) && b.mode(ConduitKind.PIPE, dir).inserts(ConduitKind.PIPE)) {
                    out.add(new Route(b.xCoord + dir.offsetX, b.yCoord + dir.offsetY, b.zCoord + dir.offsetZ, dir,
                            0, b.priority(ConduitKind.PIPE, dir), b));
                }
            }
        }
        sortByPriority(out);
        fluidRoutes = out;
        fluidRoutesVersion = pipeVersion;
        return out;
    }

    /** The fluid in the pipe changed: its chunk has to be saved (or it comes back doubled or gone). */
    private void fluidChanged() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    /** Players see the fluid inside: re-send when it changes kind, or its level by a quarter (at most once a second). */
    private void syncFluid() {
        if (syncCooldown > 0) {
            syncCooldown--;
        }
        FluidStack held = tank().getFluid();
        String name = held == null ? null : held.getFluid().getName();
        int amount = held == null ? 0 : held.amount;
        boolean kindChanged = name == null ? sentFluid != null : !name.equals(sentFluid);
        boolean levelChanged = Math.abs(amount - sentAmount) * 4 > tank().getCapacity();
        if (kindChanged || (levelChanged && syncCooldown == 0)) {
            sentFluid = name;
            sentAmount = amount;
            syncCooldown = 20;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ---- IFluidHandler (the pipe; a bundle without one takes nothing) ----

    private boolean pipeSide(ForgeDirection from) {
        return pipe != null && (from == ForgeDirection.UNKNOWN || mode(ConduitKind.PIPE, from) != ConduitMode.OFF);
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        int filled = pipeSide(from) ? tank().fill(resource, doFill) : 0;
        if (doFill && filled > 0) {
            fluidChanged();
        }
        return filled;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (!pipeSide(from) || resource == null || tank().getFluid() == null || !resource.isFluidEqual(tank().getFluid())) {
            return null;
        }
        return drained(tank().drain(resource.amount, doDrain), doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return pipeSide(from) ? drained(tank().drain(maxDrain, doDrain), doDrain) : null;
    }

    private FluidStack drained(FluidStack out, boolean done) {
        if (done && out != null && out.amount > 0) {
            fluidChanged();
        }
        return out;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return pipeSide(from);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return pipeSide(from);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return pipe == null ? new FluidTankInfo[0] : new FluidTankInfo[]{tank().getInfo()};
    }

    // ---- tube ----

    /** A block the network can deliver into (routes are listed nearest first, then sorted by priority). */
    private static final class Route {
        final int x, y, z;
        final ForgeDirection dir;      // side of the conduit bundle the block is on
        final int color;               // the connector's insert channel (tubes)
        final int priority;
        final TileEntityConduitBundleSC owner;   // the bundle with the connector (its insert filter)

        Route(int x, int y, int z, ForgeDirection dir, int color, int priority, TileEntityConduitBundleSC owner) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir;
            this.color = color;
            this.priority = priority;
            this.owner = owner;
        }
    }

    /** Highest priority first; the sort is stable, so nearest first within a priority. */
    private static void sortByPriority(List<Route> list) {
        java.util.Collections.sort(list, new java.util.Comparator<Route>() {
            @Override
            public int compare(Route a, Route b) {
                return b.priority < a.priority ? -1 : b.priority > a.priority ? 1 : 0;
            }
        });
    }

    /**
     * Indices of `list` in the order to try them: priority groups one after another, and within a
     * group turn by turn starting `turn` places in (0 = always nearest first).
     */
    private static int[] visitOrder(List<Route> list, int turn) {
        int[] order = new int[list.size()];
        int n = 0;
        for (int start = 0; start < list.size(); ) {
            int end = start;
            while (end < list.size() && list.get(end).priority == list.get(start).priority) {
                end++;
            }
            int size = end - start;
            for (int k = 0; k < size; k++) {
                order[n++] = start + (((turn + k) % size) + size) % size;
            }
            start = end;
        }
        return order;
    }

    /** Whether this tube takes items out of the inventory on that side: extract or in/out, redstone permitting. */
    private boolean extractsFrom(ForgeDirection dir, TileEntity te) {
        return mode(ConduitKind.TUBE, dir).extracts(ConduitKind.TUBE) && redstoneAllows(ConduitKind.TUBE, dir);
    }

    private void moveItems() {
        ForgeDirection[] dirs = ForgeDirection.VALID_DIRECTIONS;
        for (int i = 0; i < dirs.length; i++) {
            ForgeDirection dir = dirs[(nextSource + i) % dirs.length];
            if (!connectorAt(ConduitKind.TUBE, dir)) {
                continue;
            }
            TileEntity te = neighbour(dir);
            if (!extractsFrom(dir, te)) {
                continue;
            }
            if (sendFrom((IInventory) te, dir)) {
                nextSource = (nextSource + i + 1) % dirs.length;
                return;
            }
        }
    }

    /**
     * Up to ITEMS_PER_TICK of one stack from `source` to a destination on the network that takes it:
     * only connectors on the same colour channel, highest priority first, then nearest - turn by
     * turn if the extract connector has round robin on.
     */
    private boolean sendFrom(IInventory source, ForgeDirection sourceDir) {
        int color = extractColor(ConduitKind.TUBE, sourceDir);
        List<Route> list = new ArrayList<Route>();
        for (Route r : routes()) {
            if (r.color == color) {
                list.add(r);
            }
        }
        if (list.isEmpty()) {
            return false;
        }
        boolean rr = roundRobin(ConduitKind.TUBE, sourceDir);
        int turn = rr ? nextRoute[sourceDir.ordinal()] : 0;
        int[] order = visitOrder(list, turn);
        ItemStack outFilter = connectorItems.get(sourceDir, com.sc.conduit.ConnectorInventorySC.OUT_FILTER);
        int perMove = Math.min(com.sc.item.ItemTubeSpeedSC.MAX_ITEMS,
                ITEMS_PER_TICK + com.sc.item.ItemTubeSpeedSC.ITEMS_PER_UPGRADE * connectorItems.speed(sourceDir));
        for (int slot : accessibleSlots(source, sourceDir)) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack == null || !canTake(source, slot, stack, sourceDir) || !com.sc.conduit.ItemFilterSC.passes(outFilter, stack)) {
                continue;
            }
            for (int k = 0; k < order.length; k++) {
                Route r = list.get(order[k]);
                if (!worldObj.blockExists(r.x, r.y, r.z)) {
                    continue;
                }
                TileEntity dest = worldObj.getTileEntity(r.x, r.y, r.z);
                if (!(dest instanceof IInventory) || dest == source
                        || !com.sc.conduit.ItemFilterSC.passes(r.owner.connectorItems.get(r.dir, com.sc.conduit.ConnectorInventorySC.IN_FILTER), stack)) {
                    continue;
                }
                ItemStack toMove = stack.copy();
                toMove.stackSize = Math.min(perMove, stack.stackSize);
                int accepted = toMove.stackSize - insert((IInventory) dest, r.dir, toMove);
                if (accepted > 0) {
                    source.decrStackSize(slot, accepted);
                    source.markDirty();
                    ((IInventory) dest).markDirty();
                    if (rr) {
                        nextRoute[sourceDir.ordinal()] = turn + k + 1;   // the next one after the one that took it
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Every inventory the tube network delivers into, nearest first (breadth-first over linked tubes). */
    private List<Route> routes() {
        if (routes != null && routesVersion == tubeVersion) {
            return routes;
        }
        List<Route> out = new ArrayList<Route>();
        Set<TileEntityConduitBundleSC> seen = new HashSet<TileEntityConduitBundleSC>();
        ArrayDeque<TileEntityConduitBundleSC> queue = new ArrayDeque<TileEntityConduitBundleSC>();
        queue.add(this);
        seen.add(this);
        while (!queue.isEmpty() && seen.size() < MAX_TUBE_NODES) {
            TileEntityConduitBundleSC b = queue.poll();
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (b.linksTo(ConduitKind.TUBE, dir)) {
                    TileEntityConduitBundleSC next = b.bundleAt(dir);
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                } else if (b.connectorAt(ConduitKind.TUBE, dir)) {
                    ConduitMode m = b.mode(ConduitKind.TUBE, dir);
                    if (m.inserts(ConduitKind.TUBE)) {
                        out.add(new Route(b.xCoord + dir.offsetX, b.yCoord + dir.offsetY, b.zCoord + dir.offsetZ, dir,
                                b.insertColor(ConduitKind.TUBE, dir), b.priority(ConduitKind.TUBE, dir), b));
                    }
                }
            }
        }
        sortByPriority(out);
        routes = out;
        routesVersion = tubeVersion;
        return out;
    }

    /** The face of the inventory the tube touches (the tube sits on its opposite side). */
    private static int facing(ForgeDirection dir) {
        return dir.getOpposite().ordinal();
    }

    /** Slots reachable from that side, honouring ISidedInventory (a machine's side config). */
    private static int[] accessibleSlots(IInventory inv, ForgeDirection dir) {
        if (inv instanceof ISidedInventory) {
            return ((ISidedInventory) inv).getAccessibleSlotsFromSide(facing(dir));
        }
        int[] all = new int[inv.getSizeInventory()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    private static boolean canTake(IInventory inv, int slot, ItemStack stack, ForgeDirection dir) {
        return !(inv instanceof ISidedInventory) || ((ISidedInventory) inv).canExtractItem(slot, stack, facing(dir));
    }

    private static boolean canGive(IInventory inv, int slot, ItemStack stack, ForgeDirection dir) {
        return inv.isItemValidForSlot(slot, stack)
                && (!(inv instanceof ISidedInventory) || ((ISidedInventory) inv).canInsertItem(slot, stack, facing(dir)));
    }

    /** @return how many of `stack` did NOT fit. */
    private static int insert(IInventory destination, ForgeDirection destDir, ItemStack stack) {
        int remaining = stack.stackSize;
        int[] slots = accessibleSlots(destination, destDir);
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            int slot = slots[i];
            if (!canGive(destination, slot, stack, destDir)) {
                continue;
            }
            ItemStack existing = destination.getStackInSlot(slot);
            if (existing == null) {
                int place = Math.min(remaining, Math.min(destination.getInventoryStackLimit(), stack.getMaxStackSize()));
                ItemStack placed = stack.copy();
                placed.stackSize = place;
                destination.setInventorySlotContents(slot, placed);
                remaining -= place;
            } else if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                int room = Math.min(destination.getInventoryStackLimit(), existing.getMaxStackSize()) - existing.stackSize;
                int place = Math.min(remaining, Math.max(0, room));
                existing.stackSize += place;
                remaining -= place;
            }
        }
        return remaining;
    }

    // ------------------------------------------------------------------ energy registration

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote && Loader.isModLoaded(Reference.IC2_MODID)) {
            com.sc.energy.Ic2LoadQueueSC.queue(this);     // IC2: on the next server tick (Ic2LoadQueueSC)
        } else {
            registerEnergy();
        }
        networksMoved();
    }

    /** Ic2LoadQueueSC: the bundle is in its world now - its cable joins the net. */
    @Override
    public void joinEnergyNetNow() {
        registerEnergy();
    }

    @Override
    public void invalidate() {
        com.sc.energy.Ic2LoadQueueSC.cancel(this);
        unregisterEnergy();
        networksMoved();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        com.sc.energy.Ic2LoadQueueSC.cancel(this);
        unregisterEnergy();
        networksMoved();
        super.onChunkUnload();
    }

    private void networksMoved() {
        if (worldObj == null || !worldObj.isRemote) {
            tubeVersion++;
            pipeVersion++;
        }
    }

    private void registerEnergy() {
        if (registeredEnergy || cable == null || worldObj == null || worldObj.isRemote || isInvalid()) {
            return;
        }
        if (Loader.isModLoaded(Reference.IC2_MODID)) {
            postIc2LoadEvent();
        } else {
            EnergyNetSC.instance().addCable(this);
        }
        registeredEnergy = true;
    }

    private void unregisterEnergy() {
        if (!registeredEnergy) {
            return;
        }
        if (Loader.isModLoaded(Reference.IC2_MODID)) {
            postIc2UnloadEvent();
        } else {
            EnergyNetSC.instance().removeCable(this);
        }
        registeredEnergy = false;
    }

    /** IC2 caches which sides join, so a changed cable or side means leaving and rejoining its net. */
    private void reregisterEnergy() {
        if (worldObj != null && worldObj.isRemote) {
            return;
        }
        unregisterEnergy();
        registerEnergy();
        if (!Loader.isModLoaded(Reference.IC2_MODID)) {
            EnergyNetSC.instance().invalidate();
        }
    }

    @Optional.Method(modid = Reference.IC2_MODID)
    private void postIc2LoadEvent() {
        MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
    }

    @Optional.Method(modid = Reference.IC2_MODID)
    private void postIc2UnloadEvent() {
        MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
    }

    // IC2 2.2.828's energy net never burns a conductor: it stores getConductorBreakdownEnergy()
    // but never reads it, and never calls removeConductor() / removeInsulation() (only its own
    // TileEntityCable does). So an EV reactor could run through our bare copper forever with IC2
    // installed. Every tick each cable reads the voltage IC2 left on it after its last calculation
    // (an instant value, not an average - non-zero only while a source pushes energy through) and
    // applies §9.3 itself: the first packet above the cable's tier burns it.
    @Optional.Method(modid = Reference.IC2_MODID)
    private void checkIc2Overvoltage() {
        if (ic2.api.energy.EnergyNet.instance == null) {
            return;
        }
        ic2.api.energy.NodeStats stats = ic2.api.energy.EnergyNet.instance.getNodeStats(this);
        if (stats == null || stats.getVoltage() <= 0) {
            return;
        }
        int ic2Tier = ic2.api.energy.EnergyNet.instance.getTierFromPower(stats.getVoltage());
        if (ic2Tier > 0 && ic2Tier > cable.tier.toIc2Tier()) {
            ExplosionLogic.burnCable(this);
        }
    }

    // ---- IC2 IEnergyConductor: §9.1's amps/loss mapped onto IC2's model ----

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getConductionLoss() {
        return cable == null ? 0 : cable.lossPerBlock;
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getInsulationEnergyAbsorption() {
        return cable != null && cable.insulated ? cable.tier.getVoltage() * 8 : 0;
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getInsulationBreakdownEnergy() {
        return cable != null && cable.insulated ? cable.maxThroughput() * 2 : 0;
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public double getConductorBreakdownEnergy() {
        return cable == null ? 0 : cable.maxThroughput() * 4;
    }

    // IC2 2.2.828 never calls these two (see checkIc2Overvoltage); kept for IC2 builds that do -
    // they burn only the cable, the bundle's pipe and tube stay.
    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public void removeInsulation() {
        ExplosionLogic.burnCable(this);
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public void removeConductor() {
        ExplosionLogic.burnCable(this);
    }

    /**
     * Only through an open cable side, never into a different cable type (networks don't mix), and
     * at a block only the way the connector's mode allows: taking energy out of it needs extract /
     * in-out (and its redstone control), giving it energy needs insert / in-out.
     */
    private boolean ic2Side(TileEntity other, ForgeDirection side, boolean intoBlock) {
        if (cable == null || !cableOpen(side)) {
            return false;
        }
        if (other instanceof TileEntityConduitBundleSC) {
            TileEntityConduitBundleSC b = (TileEntityConduitBundleSC) other;
            return b.cable == cable && b.cableOpen(side.getOpposite());
        }
        ConduitMode m = mode(ConduitKind.CABLE, side);
        return intoBlock ? m.inserts(ConduitKind.CABLE)
                : m.extracts(ConduitKind.CABLE) && redstoneAllows(ConduitKind.CABLE, side);
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection side) {
        return ic2Side(emitter, side, false);
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection side) {
        return ic2Side(receiver, side, true);
    }

    // ------------------------------------------------------------------ NBT and sync

    private void sync() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readParts(nbt);
        connectorItems.readFromNBT(nbt);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        writeParts(nbt);
        nbt.setInteger("Corrosion", corrosionTimer);
        nbt.setBoolean("ModesV2", modesV2);
        connectorItems.writeToNBT(nbt);
    }

    private void writeParts(NBTTagCompound nbt) {
        nbt.setInteger("Cable", cable == null ? -1 : cable.ordinal());
        nbt.setInteger("Pipe", pipe == null ? -1 : pipe.ordinal());
        nbt.setBoolean("Tube", tube);
        nbt.setByteArray("Modes", modes.clone());
        nbt.setByteArray("Redstone", redstone.clone());
        nbt.setByteArray("ColorOut", extractColor.clone());
        nbt.setByteArray("ColorIn", insertColor.clone());
        nbt.setIntArray("Priority", priority.clone());
        byte[] rr = new byte[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            rr[i] = (byte) (roundRobin[i] ? 1 : 0);
        }
        nbt.setByteArray("RoundRobin", rr);
        if (pipe != null) {
            nbt.setTag("Tank", tank().writeToNBT(new NBTTagCompound()));
        }
    }

    private void readParts(NBTTagCompound nbt) {
        CableType oldCable = cable;
        int c = nbt.getInteger("Cable");
        cable = c >= 0 && c < CableType.values().length ? CableType.values()[c] : null;
        int p = nbt.getInteger("Pipe");
        pipe = p >= 0 && p < PipeType.values().length ? PipeType.values()[p] : null;
        tube = nbt.getBoolean("Tube");
        byte[] m = nbt.getByteArray("Modes");
        for (int i = 0; i < modes.length; i++) {
            modes[i] = i < m.length ? m[i] : 0;
        }
        if (nbt.hasKey("Redstone")) {            // bundles from before the connector options keep the defaults
            byte[] rs = nbt.getByteArray("Redstone"), co = nbt.getByteArray("ColorOut"), ci = nbt.getByteArray("ColorIn");
            byte[] rr = nbt.getByteArray("RoundRobin");
            int[] pr = nbt.getIntArray("Priority");
            for (int i = 0; i < SLOTS; i++) {
                redstone[i] = i < rs.length ? rs[i] : 0;
                extractColor[i] = i < co.length ? co[i] : (byte) DEFAULT_COLOR;
                insertColor[i] = i < ci.length ? ci[i] : (byte) DEFAULT_COLOR;
                roundRobin[i] = i >= rr.length || rr[i] != 0;
                priority[i] = i < pr.length ? pr[i] : 0;
            }
        }
        tank = pipe == null ? null : new FluidTank(pipe.throughput);
        if (pipe != null && nbt.hasKey("Tank")) {
            tank.readFromNBT(nbt.getCompoundTag("Tank"));
        }
        corrosionTimer = nbt.getInteger("Corrosion");
        if (nbt.hasKey("Cable")) {                 // a full save (not a client description packet)
            modesV2 = !nbt.hasKey("Corrosion") || nbt.getBoolean("ModesV2");
        }
        if (oldCable != cable && worldObj != null && !worldObj.isRemote) {
            reregisterEnergy();
        }
    }

    /** Parts, modes and the fluid inside go to the client (the renderer draws all three). */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeParts(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readParts(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** Replaces a pre-bundle cable / pipe / tube block with a bundle holding the same conduit (and fluid). */
    public static void convertLegacy(net.minecraft.world.World world, int x, int y, int z, ConduitKind kind, int subtype, FluidStack fluid) {
        if (!world.setBlock(x, y, z, ModBlocks.conduitBundle, 0, 3)) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityConduitBundleSC) {
            TileEntityConduitBundleSC bundle = (TileEntityConduitBundleSC) te;
            bundle.addPart(kind, subtype);
            bundle.modesV2 = false;               // an old cable/pipe/tube block: same migration as an old bundle
            if (kind == ConduitKind.PIPE && fluid != null) {
                bundle.tank().fill(fluid, true);
            }
        }
    }
}
