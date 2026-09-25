package com.sc.tileentity;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * One class drives every generator (§15), parameterized by GeneratorType from block metadata -
 * same pattern as the machine/cable/pipe TileEntities. Unlike TileEntityMachineSC, a generator
 * is a pure IEnergyHandlerSC *source* (isEnergySource()=true / isEnergySink()=false) except
 * Fusion Reactor, which is the mod's one dual-role tile: a sink during ignition (§18.2 - it
 * needs an external EV charge to reach 1,000,000 EU before the reaction is self-sustaining)
 * and a source afterwards. See updateFusion().
 *
 * Solar panels (Kind.PASSIVE) need daylight + open sky + no rain and consume no fuel.
 * Fluid-fuelled generators (Combustion/Steam Turbine/Gas Turbine/Plasma Generator) drain their
 * type's fuel at its §15 mB/t rate whenever there's enough buffered energy room to accept the
 * output (mirrors a machine pausing on "output full").
 */
public class TileEntityGeneratorSC extends TileEntityEnergyBase implements ISidedInventory, IFluidHandler {

    private static final long IGNITION_THRESHOLD = 1000000L;
    public static final int MODULE_LIFE_TICKS = 1000000;
    /**
     * TODO(design doc §18.2 says "Deuterium Cell x1/тик"): literally that burns a stack of 64
     * cells in 3 seconds, while one cell takes hundreds of halite ores through the electrolyzer -
     * the reactor could never run. One cell now fuels 5 minutes (12.3M EU at 2048 EU/t).
     */
    public static final int CELL_BURN_TICKS = 6000;
    public static final int TANK_CAPACITY = 4000;

    private GeneratorType generatorType = GeneratorType.COMBUSTION;
    private final FluidTank fuelTank = new FluidTank(TANK_CAPACITY);
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_BLANKET = 1;

    /** Fusion Reactor: the Deuterium Cell (§18.2). Combustion Generator: coal or other furnace fuel. */
    private ItemStack fuelSlot;
    /** Only used by Fusion Reactor - the Li-Blanket Module consumed at ignition (§18.2). */
    private ItemStack blanketSlot;
    private long ignitionEU;
    private boolean ignited;
    private int moduleLifeRemaining;
    /** Ticks of fusion left from the Deuterium Cell already fed in. */
    private int cellBurnRemaining;
    /** Combustion Generator only: ticks left on the solid fuel item currently burning. */
    private int solidBurnTicks;
    public static final int SOLID_FUEL_DIVISOR = 12;
    private GeneratorStatus status = GeneratorStatus.IDLE;
    /** The face with the front texture, turned to the player on placement - cosmetic, energy leaves every face. */
    private ForgeDirection facing = ForgeDirection.SOUTH;

    public ForgeDirection getFacing() {
        return facing;
    }

    /** Horizontal faces only (like a furnace); anything else is the old default, south. */
    public void setFacing(ForgeDirection facing) {
        this.facing = facing != null && facing != ForgeDirection.UNKNOWN && facing.offsetY == 0 ? facing : ForgeDirection.SOUTH;
    }

    public void setGeneratorType(GeneratorType type) {
        this.generatorType = type;
        setTier(type.tier);
    }

    public GeneratorType getGeneratorType() {
        return generatorType;
    }

    public GeneratorStatus getStatus() {
        return status;
    }

    /** Client-side sync only. */
    public void setStatusClient(GeneratorStatus value) {
        this.status = value;
    }

    public long getIgnitionEU() {
        return ignitionEU;
    }

    public boolean isIgnited() {
        return ignited;
    }

    public FluidTank getFuelTank() {
        return fuelTank;
    }

    /** EU that must be pumped in before the Fusion Reactor lights (§18.2) - drives the GUI's ignition bar. */
    public static long getIgnitionThreshold() {
        return IGNITION_THRESHOLD;
    }

    // ---- client-side sync only, written by ContainerGeneratorSC.updateProgressBar() ----

    public void setFuelFluidClient(int fluidId, int amount) {
        Fluid fluid = amount <= 0 ? null : FluidRegistry.getFluid(fluidId);
        fuelTank.setFluid(fluid == null ? null : new FluidStack(fluid, amount));
    }

    public void setIgnitionClient(int eu, boolean isIgnited) {
        this.ignitionEU = eu;
        this.ignited = isIgnited;
    }

    @Override
    public boolean isEnergySource() {
        return generatorType != GeneratorType.FUSION_REACTOR || ignited;
    }

    @Override
    public boolean isEnergySink() {
        return generatorType == GeneratorType.FUSION_REACTOR && !ignited;
    }

    @Override
    public int demandedEnergy() {
        if (generatorType == GeneratorType.FUSION_REACTOR && !ignited) {
            return (int) Math.min(Integer.MAX_VALUE, IGNITION_THRESHOLD - ignitionEU);
        }
        return 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (generatorType != GeneratorType.FUSION_REACTOR || ignited) {
            return 0; // every other generator, and an already-ignited reactor, never accepts energy
        }
        int room = (int) Math.min(Integer.MAX_VALUE, IGNITION_THRESHOLD - ignitionEU);
        int accepted = Math.min(room, amount);
        if (!simulate) {
            ignitionEU += accepted;
        }
        return accepted;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        switch (generatorType.kind) {
            case PASSIVE:
                updatePassive();
                break;
            case FLUID_FUEL:
                updateFluidFuel();
                break;
            case FUSION:
                updateFusion();
                break;
            default:
        }
    }

    private void updatePassive() {
        boolean canGenerate = worldObj.isDaytime() && !worldObj.isRaining() && worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord);
        if (canGenerate && getEnergyStored() < getMaxEnergyStored()) {
            addEnergy(generatorType.euPerTick);
            status = GeneratorStatus.GENERATING;
        } else {
            status = canGenerate ? GeneratorStatus.BUFFER_FULL : GeneratorStatus.NO_SUNLIGHT;
        }
    }

    /**
     * The Combustion Generator's bootstrap fallback: any furnace fuel in slot 0, one tick of
     * output per SOLID_FUEL_DIVISOR ticks of vanilla burn time. Its §5 fuel, diesel, only comes
     * from the HV Refinery, so without this the one generator a new world can build had nothing
     * to burn. Diesel still wins whenever the tank has it - it's the pipeable, hands-off option.
     * TODO(balance): /12 puts coal at ~4,300 EU, in line with IC2's generator (4,000).
     */
    private boolean burnSolidFuel() {
        if (solidBurnTicks <= 0) {
            if (fuelSlot == null || !TileEntityFurnace.isItemFuel(fuelSlot)) {
                return false;
            }
            solidBurnTicks = Math.max(1, TileEntityFurnace.getItemBurnTime(fuelSlot) / SOLID_FUEL_DIVISOR);
            ItemStack container = fuelSlot.getItem().getContainerItem(fuelSlot); // lava bucket -> bucket
            fuelSlot.stackSize--;
            if (fuelSlot.stackSize <= 0) {
                fuelSlot = container;
            }
            markDirty();
        }
        solidBurnTicks--;
        return true;
    }

    private void updateFluidFuel() {
        if (getEnergyStored() >= getMaxEnergyStored()) {
            status = GeneratorStatus.BUFFER_FULL;
            return;
        }
        Fluid required = FluidRegistry.getFluid(generatorType.fuelFluidName);
        FluidStack held = fuelTank.getFluid();
        if (required != null && held != null && held.getFluid().equals(required) && held.amount >= generatorType.fuelRatePerTick) {
            fuelTank.drain(generatorType.fuelRatePerTick, true);
            addEnergy(generatorType.euPerTick);
            status = GeneratorStatus.GENERATING;
            return;
        }
        if (generatorType == GeneratorType.COMBUSTION && burnSolidFuel()) {
            addEnergy(generatorType.euPerTick);
            status = GeneratorStatus.GENERATING;
            return;
        }
        status = GeneratorStatus.NO_FUEL;
    }

    private void updateFusion() {
        if (!ignited) {
            if (ignitionEU < IGNITION_THRESHOLD) {
                status = GeneratorStatus.IGNITING;
                return;
            }
            if (blanketSlot == null || blanketSlot.getItem() != com.sc.init.ModItems.component("liBlanketModule")) {
                status = GeneratorStatus.NO_BLANKET;
                return;
            }
            // §18.2: ignition is a one-off expensive event, and the blanket module is what the
            // reaction breeds tritium in. Spending both here is what makes the module actually
            // "require replacement" - previously ignitionEU was never cleared, so once the module
            // ran out the reactor silently re-ignited for free on the very next tick and reset its
            // own lifetime, forever.
            blanketSlot.stackSize--;
            if (blanketSlot.stackSize <= 0) {
                blanketSlot = null;
            }
            ignitionEU = 0;
            ignited = true;
            moduleLifeRemaining = MODULE_LIFE_TICKS;
            status = GeneratorStatus.GENERATING;
            markDirty();
            refreshEnergyNet();      // sink -> source: IC2 caches that on load
            return;
        }
        if (moduleLifeRemaining <= 0) {
            ignited = false; // Li-Blanket Module spent (§18.2) - needs replacement, re-ignition required
            status = GeneratorStatus.BLANKET_DEPLETED;
            markDirty();
            refreshEnergyNet();      // source -> sink again
            return;
        }
        if (getEnergyStored() >= getMaxEnergyStored()) {
            status = GeneratorStatus.BUFFER_FULL;
            return;
        }
        if (cellBurnRemaining <= 0) {
            if (fuelSlot == null || fuelSlot.getItem() != deuteriumCellItemOrNull()) {
                status = GeneratorStatus.NO_DEUTERIUM;
                return;
            }
            fuelSlot.stackSize--;
            if (fuelSlot.stackSize <= 0) {
                fuelSlot = null;
            }
            cellBurnRemaining = CELL_BURN_TICKS;
            markDirty();
        }
        cellBurnRemaining--;
        moduleLifeRemaining--;
        addEnergy(generatorType.euPerTick);
        status = GeneratorStatus.GENERATING;
    }

    /** Deuterium Cell isn't registered until ModRecipesInfrastructure's Fusion wiring - null-safe until then. */
    private Item deuteriumCellItemOrNull() {
        return com.sc.init.ModItems.deuteriumCell;
    }

    // ---- IInventory (2 slots, both only meaningful for the Fusion Reactor: the Deuterium
    // Cell it burns and the Li-Blanket Module it consumes to ignite, §18.2) ----

    @Override
    public int getSizeInventory() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == SLOT_BLANKET ? blanketSlot : fuelSlot;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        ItemStack result = stack.splitStack(Math.min(amount, stack.stackSize));
        if (stack.stackSize <= 0) {
            setSlot(slot, null);
        }
        markDirty();
        return result;
    }

    private void setSlot(int slot, ItemStack stack) {
        if (slot == SLOT_BLANKET) {
            blanketSlot = stack;
        } else {
            fuelSlot = stack;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        setSlot(slot, stack);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container." + generatorType.displayName;
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
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (generatorType == GeneratorType.COMBUSTION) {
            return slot == SLOT_FUEL && TileEntityFurnace.isItemFuel(stack);
        }
        if (generatorType != GeneratorType.FUSION_REACTOR) {
            return false;
        }
        return slot == SLOT_BLANKET
                ? stack.getItem() == com.sc.init.ModItems.component("liBlanketModule")
                : stack.getItem() == deuteriumCellItemOrNull();
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        if (generatorType == GeneratorType.FUSION_REACTOR) {
            return new int[]{SLOT_FUEL, SLOT_BLANKET};
        }
        return generatorType == GeneratorType.COMBUSTION ? new int[]{SLOT_FUEL} : new int[0];
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        // Only what's left behind by a burnt fuel - the empty bucket from lava - so a hopper can
        // clear it. Anything still burnable stays put.
        return generatorType == GeneratorType.COMBUSTION && slot == SLOT_FUEL && !TileEntityFurnace.isItemFuel(stack);
    }

    // ---- IFluidHandler (fuel intake for the FLUID_FUEL kind) ----

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || !isFuel(resource.getFluid())) {
            return 0;
        }
        return fuelTank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return isFuel(fluid);
    }

    /**
     * Only this generator's own fuel gets into the tank. It used to take any fluid: water piped
     * into a Steam Turbine filled the tank for good (nothing drains it and the burn check wants
     * steam), bricking the generator until it was broken.
     */
    private boolean isFuel(Fluid fluid) {
        return generatorType.kind == GeneratorType.Kind.FLUID_FUEL && fluid != null
                && fluid.getName().equals(generatorType.fuelFluidName);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{fuelTank.getInfo()};
    }

    // ---- the dropped item keeps the buffer, the fuel and the reactor's ignition (BlockGeneratorSC) ----

    /** @return what the dropped item carries, or null when there's nothing worth keeping */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (getEnergyStored() > 0) {
            nbt.setInteger("EnergySC", getEnergyStored());
        }
        FluidStack fuel = fuelTank.getFluid();
        if (fuel != null && fuel.amount > 0) {
            nbt.setTag("FuelTank", fuelTank.writeToNBT(new NBTTagCompound()));
        }
        if (generatorType == GeneratorType.FUSION_REACTOR) {
            if (ignitionEU > 0) {
                nbt.setLong("IgnitionEU", ignitionEU);
            }
            if (ignited) {
                nbt.setBoolean("Ignited", true);
                nbt.setInteger("ModuleLife", moduleLifeRemaining);
                nbt.setInteger("CellBurn", cellBurnRemaining);
            }
        }
        return nbt.hasNoTags() ? null : nbt;
    }

    /** Placed from an item that carries writeToItem()'s data. */
    public void readFromItem(NBTTagCompound nbt) {
        addEnergy(Math.max(0, nbt.getInteger("EnergySC")));
        if (nbt.hasKey("FuelTank")) {
            fuelTank.readFromNBT(nbt.getCompoundTag("FuelTank"));
        }
        if (generatorType == GeneratorType.FUSION_REACTOR) {
            ignitionEU = Math.max(0L, Math.min(IGNITION_THRESHOLD, nbt.getLong("IgnitionEU")));
            boolean was = ignited;
            ignited = nbt.getBoolean("Ignited");
            if (ignited) {
                moduleLifeRemaining = Math.max(0, Math.min(MODULE_LIFE_TICKS, nbt.getInteger("ModuleLife")));
                cellBurnRemaining = Math.max(0, Math.min(CELL_BURN_TICKS, nbt.getInteger("CellBurn")));
            }
            if (ignited != was) {
                refreshEnergyNet();
            }
        }
        markDirty();
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setFacing(nbt.hasKey("Facing") ? ForgeDirection.getOrientation(nbt.getInteger("Facing")) : ForgeDirection.SOUTH);
        GeneratorType[] types = GeneratorType.values();
        int ordinal = nbt.getInteger("GeneratorType");
        generatorType = types[ordinal >= 0 && ordinal < types.length ? ordinal : 0];
        fuelTank.readFromNBT(nbt.getCompoundTag("FuelTank"));
        ignitionEU = nbt.getLong("IgnitionEU");
        ignited = nbt.getBoolean("Ignited");
        moduleLifeRemaining = nbt.getInteger("ModuleLife");
        cellBurnRemaining = nbt.getInteger("CellBurn");
        solidBurnTicks = nbt.getInteger("SolidBurn");
        fuelSlot = nbt.hasKey("FuelSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("FuelSlot")) : null;
        blanketSlot = nbt.hasKey("BlanketSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("BlanketSlot")) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("GeneratorType", generatorType.ordinal());
        nbt.setTag("FuelTank", fuelTank.writeToNBT(new NBTTagCompound()));
        nbt.setLong("IgnitionEU", ignitionEU);
        nbt.setBoolean("Ignited", ignited);
        nbt.setInteger("ModuleLife", moduleLifeRemaining);
        nbt.setInteger("CellBurn", cellBurnRemaining);
        nbt.setInteger("SolidBurn", solidBurnTicks);
        if (fuelSlot != null) {
            nbt.setTag("FuelSlot", fuelSlot.writeToNBT(new NBTTagCompound()));
        }
        if (blanketSlot != null) {
            nbt.setTag("BlanketSlot", blanketSlot.writeToNBT(new NBTTagCompound()));
        }
        nbt.setInteger("Facing", facing.ordinal());
    }

    /** Facing reaches the client with the chunk, so the front renders on the right side. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
