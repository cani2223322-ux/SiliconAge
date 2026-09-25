package com.sc.inventory;

import com.sc.energy.GeneratorType;
import com.sc.energy.GeneratorStatus;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Generic container for every generator (§15) - a single fuel slot (only meaningful for Fusion Reactor's Deuterium Cell, §18.2) + energy bar. */
public class ContainerGeneratorSC extends Container {

    private final TileEntityGeneratorSC generator;

    public ContainerGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        this.generator = generator;

        // 0 = Deuterium Cell, 1 = Li-Blanket Module (§18.2). Both only accept their own item;
        // TileEntityGeneratorSC.isItemValidForSlot enforces that for every other route too.
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_FUEL, 26, 33));
        addSlotToContainer(new SlotFiltered(generator, TileEntityGeneratorSC.SLOT_BLANKET, 44, 33));
        // Both slots always exist (same slot indices on both sides for every type), but only the
        // ones this generator type fills are placed on the panel - the rest are parked off-screen
        // so they can't be hovered or clicked into where GuiGeneratorSC draws no pocket.
        GeneratorType type = generator.getGeneratorType();
        if (type != GeneratorType.COMBUSTION && type != GeneratorType.FUSION_REACTOR) {
            hide((Slot) inventorySlots.get(TileEntityGeneratorSC.SLOT_FUEL));
        }
        if (type != GeneratorType.FUSION_REACTOR) {
            hide((Slot) inventorySlots.get(TileEntityGeneratorSC.SLOT_BLANKET));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public TileEntityGeneratorSC getGenerator() {
        return generator;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return generator.isUseableByPlayer(player);
    }

    // ---- server->client energy sync - see ContainerMachineSC's identical block for why. ----

    private static final int ID_ENERGY = 0;
    private static final int ID_FUEL_FLUID = 1;
    private static final int ID_FUEL_AMOUNT = 2;
    private static final int ID_IGNITION = 3;
    private static final int ID_IGNITED = 4;
    private static final int ID_STATUS = 5;

    private final IntSyncSC sync = new IntSyncSC(6);
    private int pendingFuelId;

    private int currentValue(int id) {
        FluidStack fuel = generator.getFuelTank().getFluid();
        switch (id) {
            case ID_ENERGY: return generator.getEnergyStored();
            case ID_FUEL_FLUID: return fuel == null ? 0 : fuel.getFluidID();
            case ID_FUEL_AMOUNT: return fuel == null ? 0 : fuel.amount;
            case ID_IGNITION: return (int) Math.min(Integer.MAX_VALUE, generator.getIgnitionEU());
            case ID_IGNITED: return generator.isIgnited() ? 1 : 0;
            default: return generator.getStatus().ordinal();
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] values = new int[sync.count()];
        for (int id = 0; id < values.length; id++) {
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
        int data = sync.value(id);
        switch (id) {
            case ID_ENERGY:
                generator.setEnergyStoredClient(data);
                break;
            case ID_FUEL_FLUID:
                pendingFuelId = data;
                generator.setFuelFluidClient(data, sync.value(ID_FUEL_AMOUNT));
                break;
            case ID_FUEL_AMOUNT:
                generator.setFuelFluidClient(pendingFuelId, data);
                break;
            case ID_IGNITION:
                generator.setIgnitionClient(data, generator.isIgnited());
                break;
            case ID_IGNITED:
                generator.setIgnitionClient((int) generator.getIgnitionEU(), data != 0);
                break;
            default:
                generator.setStatusClient(GeneratorStatus.byOrdinal(data));
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
