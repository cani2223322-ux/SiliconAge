package com.sc.inventory;

import com.sc.machine.MachineStatus;
import com.sc.tileentity.TileEntityMachineSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Generic container for every machine (§13) - 3 input / 3 output slots, the 4 upgrade slots and
 * the player's inventory, at GuiMachineSC's large layout (GuiBigSC), see TileEntityMachineSC.
 */
public class ContainerMachineSC extends Container {

    private final TileEntityMachineSC machine;

    public ContainerMachineSC(InventoryPlayer playerInv, TileEntityMachineSC machine) {
        this.machine = machine;

        boolean tanks = tightSlots(machine.getMachineType());
        for (int i = 0; i < TileEntityMachineSC.INPUT_SLOTS; i++) {
            addSlotToContainer(new SlotRecipeInput(machine, i, slotX(tanks, i), GuiMachineSC.IN_Y));
        }
        for (int i = 0; i < TileEntityMachineSC.OUTPUT_SLOTS; i++) {
            addSlotToContainer(new SlotOutputOnly(machine, TileEntityMachineSC.INPUT_SLOTS + i, slotX(tanks, i), GuiMachineSC.OUT_Y));
        }
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SlotUpgrade(machine, TileEntityMachineSC.FIRST_UPGRADE_SLOT + i,
                    GuiBigSC.UPG_X + i * 18, GuiBigSC.UPG_Y));
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

    /** A machine type with any tank gets the tighter slot layout beside its tank gauges. */
    public static boolean usesTanks(com.sc.machine.MachineType type) {
        for (int i = 0; i < TANK_COUNT; i++) {
            if (com.sc.machine.RecipeRegistry.usesTank(type, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tighter slot layout: machines whose tanks stand as full gauges beside the slots. The Ore
     * Washer and the Blast Furnace have screens of their own and keep the roomy one.
     */
    public static boolean tightSlots(com.sc.machine.MachineType type) {
        return usesTanks(type) && type != com.sc.machine.MachineType.ORE_WASHER && type != com.sc.machine.MachineType.BLAST_FURNACE
                && type != com.sc.machine.MachineType.CZOCHRALSKI_PULLER && type != com.sc.machine.MachineType.CZOCHRALSKI_PULLER_EV
                && type != com.sc.machine.MachineType.WIRE_SAW;
    }

    /** Where slot i of a row sits: 18 apart beside tanks, 22 apart otherwise. */
    public static int slotX(boolean tanks, int i) {
        return tanks ? 14 + i * 18 : 16 + i * 22;
    }

    public TileEntityMachineSC getMachine() {
        return machine;
    }

    /** enchantItem buttons: BTN_CLEAR + tank index pours that tank out for EU. */
    public static final int BTN_CLEAR = 0;

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        if (id >= BTN_CLEAR && id < BTN_CLEAR + TANK_COUNT) {
            machine.clearTank(id - BTN_CLEAR);
            return true;
        }
        return false;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return machine.isUseableByPlayer(player);
    }

    // ---- server->client sync for the energy bar / progress square / heat (§13's own status
    // display) - without this, a TileEntity's fields only ever reach the client via chunk-load
    // NBT, so a GUI reading them directly would be frozen at whatever they were when the chunk
    // was last sent, never moving while the player actually watches the screen. Same field-sync
    // pattern vanilla's ContainerFurnace uses for cookTime/furnaceBurnTime. ----

    public static final int TANK_COUNT = 4;
    private static final int ID_ENERGY = 0;
    private static final int ID_PROGRESS = 1;
    private static final int ID_RECIPE_TICKS = 2;
    private static final int ID_HEAT = 3;
    private static final int ID_STATUS = 4;
    /** Sync slots from here on carry the four tanks as (fluid id, amount) pairs. */
    private static final int TANK_ID_BASE = 5;

    private final IntSyncSC sync = new IntSyncSC(TANK_ID_BASE + TANK_COUNT * 2);

    private int currentValue(int id) {
        if (id >= TANK_ID_BASE) {
            FluidStack fluid = machine.getTank((id - TANK_ID_BASE) / 2).getFluid();
            if (fluid == null) {
                return 0;
            }
            return (id - TANK_ID_BASE) % 2 == 0 ? fluid.getFluidID() : fluid.amount;
        }
        switch (id) {
            case ID_ENERGY: return machine.getEnergyStored();
            case ID_PROGRESS: return machine.getProgressTicks();
            case ID_RECIPE_TICKS: return machine.getCurrentRecipeTicks();
            case ID_HEAT: return machine.getHeat();
            default: return machine.getStatus().ordinal();
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
        if (id >= TANK_ID_BASE) {
            int tank = (id - TANK_ID_BASE) / 2;
            if ((id - TANK_ID_BASE) % 2 == 0) {
                pendingFluidId[tank] = data;
                // Only the id may have changed (a tank emptied and refilled with another fluid
                // to the same amount), in which case no amount update follows.
                machine.setTankFluidClient(tank, data, sync.value(id + 1));
            } else {
                machine.setTankFluidClient(tank, pendingFluidId[tank], data);
            }
            return;
        }
        switch (id) {
            case ID_ENERGY:
                machine.setEnergyStoredClient(data);
                break;
            case ID_PROGRESS:
                machine.setProgressTicksClient(data);
                break;
            case ID_RECIPE_TICKS:
                machine.setCurrentRecipeTicksClient(data);
                break;
            case ID_HEAT:
                machine.setHeatClient(data);
                break;
            default:
                machine.setStatusClient(MachineStatus.byOrdinal(data));
        }
    }

    /** A tank needs both halves before it can be rebuilt; the id always arrives first (lower sync slot). */
    private final int[] pendingFluidId = new int[TANK_COUNT];

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack original = slot.getStack();
        ItemStack result = original.copy();
        int machineSlots = TileEntityMachineSC.FIRST_UPGRADE_SLOT + TileEntityMachineSC.UPGRADE_SLOTS;

        if (slotIndex < machineSlots) {
            if (!mergeItemStack(original, machineSlots, inventorySlots.size(), true)) {
                return null;
            }
        } else if (original.getItem() instanceof com.sc.item.ItemUpgradeSC) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, TileEntityMachineSC.FIRST_UPGRADE_SLOT, machineSlots)
                    && !shuffleInPlayerInventory(original, slotIndex, machineSlots)) {
                return null;
            }
        } else if (!SlotMergeSC.mergeValid(inventorySlots, original, 0, TileEntityMachineSC.INPUT_SLOTS)
                && !shuffleInPlayerInventory(original, slotIndex, machineSlots)) {
            return null;
        }

        if (original.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    /** Shift-click inside the player's own inventory: main grid <-> hotbar, like vanilla. */
    private boolean shuffleInPlayerInventory(ItemStack stack, int slotIndex, int firstPlayerSlot) {
        int hotbarStart = firstPlayerSlot + 27;
        return slotIndex < hotbarStart
                ? mergeItemStack(stack, hotbarStart, inventorySlots.size(), false)
                : mergeItemStack(stack, firstPlayerSlot, hotbarStart, false);
    }

    /**
     * Input slots only take things this machine has a recipe for. Vanilla's Slot.isItemValid()
     * always says yes and never consults the IInventory, so without this the GUI would happily
     * let a player drag junk into a slot that TileEntityMachineSC.isItemValidForSlot() rejects
     * for every other route (hoppers, tubes, shift-click).
     */
    private static class SlotRecipeInput extends Slot {
        private final TileEntityMachineSC machine;

        SlotRecipeInput(TileEntityMachineSC machine, int index, int x, int y) {
            super(machine, index, x, y);
            this.machine = machine;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return machine.isItemValidForSlot(getSlotIndex(), stack);
        }
    }

    /** Upgrade slots take only machine upgrades. */
    private static class SlotUpgrade extends Slot {
        SlotUpgrade(net.minecraft.inventory.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack != null && stack.getItem() instanceof com.sc.item.ItemUpgradeSC
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).generatorOnly()
                    && !com.sc.item.ItemUpgradeSC.typeOf(stack).fieldOnly();
        }
    }

    /** Output slots can't be manually filled by the player - only the machine writes to them. */
    private static class SlotOutputOnly extends Slot {
        SlotOutputOnly(net.minecraft.inventory.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }
}
