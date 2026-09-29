package com.sc.inventory;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * Container for every generator: the two item slots (fuel / rotor / capsules / deuterium cell,
 * blanket module / second capsule), four upgrade slots in the side panel, the player's
 * inventory. Every generator has all six slots (same indices on both sides); the ones its type
 * doesn't use are parked off-screen. Energy, tanks, ignition, heat and the live output are
 * synced through IntSyncSC.
 */
public class ContainerGeneratorSC extends Container {

    /** On the large screen's holo panel (GuiGeneratorSC / GuiBigSC). */
    public static final int SLOT_FUEL_X = 16, SLOT_BLANKET_X = 38, SLOT_Y = 30;
    /** The Fusion Reactor's deuterium and blanket slots, under its torus (GuiGeneratorSC). */
    public static final int FUS_FUEL_X = 14, FUS_BLANKET_X = 90, FUS_SLOT_Y = 89;

    /** The Solid Fuel Generator's slot, beside its firebox. */
    public static final int SF_SLOT_X = 82, SF_SLOT_Y = 36;
    /** The Wind Turbine's rotor slot, beside its output. */
    public static final int WD_SLOT_X = 108, WD_SLOT_Y = 78;
    /** The Geothermal Generator's bucket slot, at the head of its flow sheet. */
    public static final int GEO_SLOT_X = 19, GEO_SLOT_Y = 41;
    /** The RTG's two capsule slots, under their bays. */
    public static final int RTG_SLOT_X0 = 20, RTG_SLOT_X1 = 58, RTG_SLOT_Y = 88;

    public static int slotX(GeneratorType type, int slot) {
        if (type == GeneratorType.SOLID_FUEL) {
            return slot == 0 ? SF_SLOT_X : SLOT_BLANKET_X;
        }
        if (type == GeneratorType.WIND_TURBINE) {
            return slot == 0 ? WD_SLOT_X : SLOT_BLANKET_X;
        }
        if (type == GeneratorType.GEOTHERMAL) {
            return slot == 0 ? GEO_SLOT_X : SLOT_BLANKET_X;
        }
        if (type == GeneratorType.RTG) {
            return slot == 0 ? RTG_SLOT_X0 : RTG_SLOT_X1;
        }
        if (type == GeneratorType.FUSION_REACTOR || type == GeneratorType.TOKAMAK) {
            return slot == 0 ? FUS_FUEL_X : FUS_BLANKET_X;
        }
        return slot == 0 ? SLOT_FUEL_X : SLOT_BLANKET_X;
    }

    public static int slotY(GeneratorType type) {
        return type == GeneratorType.FUSION_REACTOR || type == GeneratorType.TOKAMAK ? FUS_SLOT_Y : type == GeneratorType.SOLID_FUEL ? SF_SLOT_Y
                : type == GeneratorType.WIND_TURBINE ? WD_SLOT_Y : type == GeneratorType.GEOTHERMAL ? GEO_SLOT_Y
                : type == GeneratorType.RTG ? RTG_SLOT_Y : SLOT_Y;
    }
    /** enchantItem button: the Creative Generator's tier. */
    public static final int BTN_CREATIVE_TIER = 0;
    /** enchantItem buttons: BTN_CLEAR + tank (0 fuel, 1 second fuel, 2 water) pours it out for EU. */
    public static final int BTN_CLEAR = 1;
    /** The power switch and the redstone mode (GuiPowerSC). */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_BATTERY_MODE = 12;
    /** The big tokamak: put the plasma out safely; the mode (big when built / normal only). */
    public static final int BTN_SOFT_STOP = 20, BTN_BIG_MODE = 21;

    private final TileEntityGeneratorSC generator;

    public ContainerGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        this.generator = generator;
        GeneratorType type = generator.getGeneratorType();
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_FUEL, slotX(type, 0), slotY(type)));
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_BLANKET, slotX(type, 1), slotY(type)));
        for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + i, GuiBigSC.UPG_X + i * 18, GuiBigSC.UPG_Y));
        }
        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            if (!TileEntityGeneratorSC.usesSlot(type, slot)) {
                hide((Slot) inventorySlots.get(slot));
            }
        }
        if (!TileEntityGeneratorSC.hasUpgradeSlots(type)) {
            for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
                hide((Slot) inventorySlots.get(TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + i));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, GuiBigSC.INV_X + col * 18, GuiBigSC.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, GuiBigSC.INV_X + col * 18, GuiBigSC.HOTBAR_Y));
        }
        // the battery slot under the gauge - last, so the player's slots keep their indices
        addSlotToContainer(new SlotBatterySC(generator, TileEntityGeneratorSC.SLOT_BATTERY,
                SlotBatterySC.itemX(GuiBigSC.GAUGE_X), SlotBatterySC.itemY(GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H)));
    }

    public TileEntityGeneratorSC getGenerator() {
        return generator;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return generator.isUseableByPlayer(player);
    }

    /** The Creative Generator's tier button - creative players only. */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (id == BTN_CREATIVE_TIER && generator.getGeneratorType() == GeneratorType.CREATIVE && player.capabilities.isCreativeMode) {
            generator.cycleCreativeTier();
            return true;
        }
        if (id == BTN_POWER && canInteractWith(player)) {
            generator.setPowerOn(!generator.isPowerOn());
            com.sc.util.SoundsSC.powerClick(generator, generator.isPowerOn());
            return true;
        }
        if (id == BTN_REDSTONE && canInteractWith(player)) {
            generator.setRedstoneMode((generator.getRedstoneMode() + 1) % 3);
            return true;
        }
        if (id == BTN_SOFT_STOP && canInteractWith(player) && generator.getGeneratorType() == GeneratorType.TOKAMAK) {
            generator.softStop();
            return true;
        }
        if (id == BTN_BIG_MODE && canInteractWith(player) && generator.getGeneratorType() == GeneratorType.TOKAMAK) {
            generator.toggleBigAllowed();
            return true;
        }
        if (id == BTN_BATTERY_MODE && canInteractWith(player)) {
            generator.cycleBatteryMode();
            return true;
        }
        if (id >= BTN_CLEAR && id < BTN_CLEAR + 3 && canInteractWith(player)) {
            generator.clearTank(id - BTN_CLEAR);
            return true;
        }
        return false;
    }

    // ---- server->client sync - see ContainerMachineSC's identical block for why ----

    private static final int ID_ENERGY = 0, ID_F1 = 1, ID_F1_AMT = 2, ID_F2 = 3, ID_F2_AMT = 4, ID_OUT = 5, ID_OUT_AMT = 6,
            ID_IGNITION = 7, ID_IGNITED = 8, ID_STATUS = 9, ID_OUTPUT = 10, ID_HEAT = 11, ID_RAMP = 12, ID_INFO_A = 13,
            ID_INFO_B = 14, ID_TIER = 15, ID_POWER = 16, ID_INFLOW = 17, ID_CELL = 18,
            ID_LIFE = 19, ID_SOLID = 20, ID_SOLID_TOTAL = 21, ID_SOLID_ITEM = 22, ID_SIDES = 23,
            /** The big tokamak's eight numbers (TileEntityGeneratorSC.bigSync). */
            ID_BIG = 24, COUNT = 32;

    private final IntSyncSC sync = new IntSyncSC(COUNT);

    private static int fluidId(FluidTank t) {
        FluidStack f = t.getFluid();
        return f == null ? 0 : f.getFluidID();
    }

    private int[] bigNow;

    private int currentValue(int id) {
        if (id >= ID_BIG) {
            return bigNow[id - ID_BIG];
        }
        switch (id) {
            case ID_ENERGY: return generator.getEnergyStored();
            case ID_F1: return fluidId(generator.getFuelTank());
            case ID_F1_AMT: return generator.getFuelTank().getFluidAmount();
            case ID_F2: return fluidId(generator.getFuelTank2());
            case ID_F2_AMT: return generator.getFuelTank2().getFluidAmount();
            case ID_OUT: return fluidId(generator.getOutTank());
            case ID_OUT_AMT: return generator.getOutTank().getFluidAmount();
            case ID_IGNITION: return (int) Math.min(Integer.MAX_VALUE, generator.getIgnitionEU());
            case ID_IGNITED: return generator.isIgnited() ? 1 : 0;
            case ID_STATUS: return generator.getStatus().ordinal();
            case ID_OUTPUT: return generator.getLastOutput();
            case ID_HEAT: return generator.getHeat();
            case ID_RAMP: return generator.getRamp();
            case ID_INFO_A: return generator.getInfoA();
            case ID_INFO_B: return generator.getInfoB();
            case ID_POWER: return generator.powerFlags();
            case ID_INFLOW: return generator.getInflowTenths();
            case ID_CELL: return (int) Math.ceil(generator.getCellBurnRemaining());
            case ID_LIFE: return generator.getModuleLife();
            case ID_SOLID: return (int) Math.ceil(generator.getSolidBurn());
            case ID_SOLID_TOTAL: return generator.getSolidBurnTotal();
            case ID_SOLID_ITEM: return generator.getSolidBurnItem();
            case ID_SIDES: return generator.getSideInfo();
            default: return generator.getCreativeTier().ordinal();
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] values = new int[COUNT];
        bigNow = generator.bigSync();
        for (int id = 0; id < COUNT; id++) {
            values[id] = currentValue(id);
        }
        sync.send(this, crafters, values);
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id >= ID_BIG) {
            int[] v = new int[COUNT - ID_BIG];
            for (int i = 0; i < v.length; i++) {
                v[i] = sync.value(ID_BIG + i);
            }
            generator.setBigClient(v);
            return;
        }
        switch (id) {
            case ID_ENERGY:
                generator.setEnergyStoredClient(sync.value(ID_ENERGY));
                break;
            case ID_F1:
            case ID_F1_AMT:
                TileEntityGeneratorSC.setTankClient(generator.getFuelTank(), sync.value(ID_F1), sync.value(ID_F1_AMT));
                break;
            case ID_F2:
            case ID_F2_AMT:
                TileEntityGeneratorSC.setTankClient(generator.getFuelTank2(), sync.value(ID_F2), sync.value(ID_F2_AMT));
                break;
            case ID_OUT:
            case ID_OUT_AMT:
                TileEntityGeneratorSC.setTankClient(generator.getOutTank(), sync.value(ID_OUT), sync.value(ID_OUT_AMT));
                break;
            case ID_IGNITION:
            case ID_IGNITED:
                generator.setIgnitionClient(sync.value(ID_IGNITION), sync.value(ID_IGNITED) != 0);
                break;
            case ID_STATUS:
                generator.setStatusClient(GeneratorStatus.byOrdinal(sync.value(ID_STATUS)));
                break;
            case ID_INFLOW:
                generator.setInflowClient(sync.value(ID_INFLOW));
                break;
            case ID_CELL:
            case ID_LIFE:
                generator.setFusionClient(sync.value(ID_CELL), sync.value(ID_LIFE));
                break;
            case ID_SOLID:
            case ID_SOLID_TOTAL:
            case ID_SOLID_ITEM:
                generator.setSolidClient(sync.value(ID_SOLID), sync.value(ID_SOLID_TOTAL), sync.value(ID_SOLID_ITEM));
                break;
            case ID_SIDES:
                generator.setSideInfoClient(sync.value(ID_SIDES));
                break;
            case ID_POWER:
                generator.setPowerFlagsClient(sync.value(ID_POWER));
                break;
            default:
                generator.setLiveClient(sync.value(ID_OUTPUT), sync.value(ID_HEAT), sync.value(ID_RAMP),
                        sync.value(ID_INFO_A), sync.value(ID_INFO_B), sync.value(ID_TIER));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();

        int generatorSlots = TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + TileEntityGeneratorSC.UPGRADE_SLOTS;
        int battery = inventorySlots.size() - 1;
        if (slotIndex < generatorSlots || slotIndex == battery) {
            if (!mergeItemStack(original, generatorSlots, battery, true)) {
                return null;
            }
        } else if (com.sc.item.BatteryFeedSC.accepts(original) && !((Slot) inventorySlots.get(battery)).getHasStack()) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, battery, battery + 1)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, generatorSlots)
                && !shuffleInPlayerInventory(original, slotIndex, generatorSlots)) {
            return null;
        }

        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    private static void hide(Slot slot) {
        slot.xDisplayPosition = -2000;
        slot.yDisplayPosition = -2000;
    }

    /** Shift-click inside the player's own inventory: main grid <-> hotbar, like vanilla. */
    private boolean shuffleInPlayerInventory(ItemStack stack, int slotIndex, int firstPlayerSlot) {
        int hotbarStart = firstPlayerSlot + 27;
        return slotIndex < hotbarStart
                ? mergeItemStack(stack, hotbarStart, firstPlayerSlot + 36, false)
                : mergeItemStack(stack, firstPlayerSlot, hotbarStart, false);
    }

    /** Defers to the TileEntity so the GUI can't accept what hoppers and tubes are refused. */
    private static class SlotFiltered extends Slot {
        private final TileEntityGeneratorSC generator;

        SlotFiltered(TileEntityGeneratorSC generator, int index, int x, int y) {
            super(generator, index, x, y);
            this.generator = generator;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return generator.isItemValidForSlot(getSlotIndex(), stack);
        }
    }
}
