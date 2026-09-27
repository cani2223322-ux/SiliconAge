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
    /** enchantItem button: the Creative Generator's tier. */
    public static final int BTN_CREATIVE_TIER = 0;

    private final TileEntityGeneratorSC generator;

    public ContainerGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        this.generator = generator;
        GeneratorType type = generator.getGeneratorType();
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_FUEL, SLOT_FUEL_X, SLOT_Y));
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_BLANKET, SLOT_BLANKET_X, SLOT_Y));
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
        return false;
    }

    // ---- server->client sync - see ContainerMachineSC's identical block for why ----

    private static final int ID_ENERGY = 0, ID_F1 = 1, ID_F1_AMT = 2, ID_F2 = 3, ID_F2_AMT = 4, ID_OUT = 5, ID_OUT_AMT = 6,
            ID_IGNITION = 7, ID_IGNITED = 8, ID_STATUS = 9, ID_OUTPUT = 10, ID_HEAT = 11, ID_RAMP = 12, ID_INFO_A = 13,
            ID_INFO_B = 14, ID_TIER = 15, COUNT = 16;

    private final IntSyncSC sync = new IntSyncSC(COUNT);

    private static int fluidId(FluidTank t) {
        FluidStack f = t.getFluid();
        return f == null ? 0 : f.getFluidID();
    }

    private int currentValue(int id) {
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
            default: return generator.getCreativeTier().ordinal();
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] values = new int[COUNT];
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

        int generatorSlots = generator.getSizeInventory();
        if (slotIndex < generatorSlots) {
            if (!mergeItemStack(original, generatorSlots, inventorySlots.size(), true)) {
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
                ? mergeItemStack(stack, hotbarStart, inventorySlots.size(), false)
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
