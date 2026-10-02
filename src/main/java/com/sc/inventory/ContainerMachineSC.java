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
        int used = machine.getMachineType().isSmelter() ? machine.getMachineType().smeltStreams() : TileEntityMachineSC.INPUT_SLOTS;
        for (int i = 0; i < TileEntityMachineSC.INPUT_SLOTS; i++) {             // a smelter's unused slots: off the screen
            addSlotToContainer(new SlotRecipeInput(machine, i, i < used ? slotX(tanks, i) : -2000, GuiMachineSC.IN_Y));
        }
        for (int i = 0; i < TileEntityMachineSC.OUTPUT_SLOTS; i++) {
            addSlotToContainer(new SlotOutputOnly(machine, TileEntityMachineSC.INPUT_SLOTS + i, i < used ? slotX(tanks, i) : -2000,
                    GuiMachineSC.OUT_Y));
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
        // the battery slot under the gauge - last, so the player's slots keep their indices
        addSlotToContainer(new SlotBatterySC(machine, TileEntityMachineSC.SLOT_BATTERY,
                SlotBatterySC.itemX(GuiBigSC.GAUGE_X), SlotBatterySC.itemY(GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H)));
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
                && type != com.sc.machine.MachineType.WIRE_SAW && type != com.sc.machine.MachineType.OXIDATION_FURNACE
                && type != com.sc.machine.MachineType.PHOTORESIST_COATER
                && type != com.sc.machine.MachineType.STEPPER && type != com.sc.machine.MachineType.STEPPER_EV
                && type != com.sc.machine.MachineType.ION_IMPLANTER && type != com.sc.machine.MachineType.SPUTTERER
                && type != com.sc.machine.MachineType.DICING_SAW
                && type != com.sc.machine.MachineType.FLUID_CELL_FILLER;
        // (the Rolling Machine has no tanks: its slots are roomy already)
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
    /** The power switch and the redstone mode (cycles always / with a signal / without). */
    public static final int BTN_POWER = 10, BTN_REDSTONE = 11, BTN_BATTERY_MODE = 12;
    /** Smelters: take the stored experience; the induction furnace: keep warm on / off. */
    public static final int BTN_XP = 13, BTN_KEEP_WARM = 14;

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!canInteractWith(player)) {
            return false;
        }
        if (id >= BTN_CLEAR && id < BTN_CLEAR + TANK_COUNT) {
            machine.clearTank(id - BTN_CLEAR);
            return true;
        }
        if (id == BTN_POWER) {
            machine.setPowerOn(!machine.isPowerOn());
            com.sc.util.SoundsSC.powerClick(machine, machine.isPowerOn());
            return true;
        }
        if (id == BTN_REDSTONE) {
            machine.setRedstoneMode((machine.getRedstoneMode() + 1) % 3);
            return true;
        }
        if (id == BTN_BATTERY_MODE) {
            machine.cycleBatteryMode();
            return true;
        }
        if (id == BTN_XP && machine.getMachineType().isSmelter()) {
            machine.takeXp(player);
            return true;
        }
        if (id == BTN_KEEP_WARM && machine.getMachineType() == com.sc.machine.MachineType.INDUCTION_FURNACE) {
            machine.toggleKeepWarm();
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
    private static final int ID_POWER = 5;
    /** Sync slots from here on carry the four tanks as (fluid id, amount) pairs. */
    private static final int TANK_ID_BASE = 6;

    /** After the tanks: a smelter's second stream, its experience (tenths), keep-warm. */
    private static final int ID_SMELT2 = TANK_ID_BASE + TANK_COUNT * 2, ID_XP = ID_SMELT2 + 1, ID_WARM = ID_SMELT2 + 2;
    /** The Matter Compressor's mass counter. */
    private static final int ID_MATTER = ID_WARM + 1;

    private final IntSyncSC sync = new IntSyncSC(ID_MATTER + 1);

    private int currentValue(int id) {
        if (id == ID_SMELT2) {
            return machine.getSmeltProgress(1);
        }
        if (id == ID_XP) {
            return (int) (machine.getStoredXp() * 10);
        }
        if (id == ID_WARM) {
            return machine.isKeepWarm() ? 1 : 0;
        }
        if (id == ID_MATTER) {
            return machine.getMatter();
        }
        if (id >= TANK_ID_BASE) {
            FluidStack fluid = machine.getTank((id - TANK_ID_BASE) / 2).getFluid();
            if (fluid == null) {
                return 0;
            }
            return (id - TANK_ID_BASE) % 2 == 0 ? fluid.getFluidID() : fluid.amount;
        }
        switch (id) {
            case ID_ENERGY: return machine.getEnergyStored();
            case ID_PROGRESS: return machine.getMachineType().isSmelter() ? machine.getSmeltProgress(0) : machine.getProgressTicks();
            case ID_RECIPE_TICKS: return machine.getCurrentRecipeTicks();
            case ID_HEAT: return machine.getHeat();
            case ID_POWER: return machine.powerFlags();
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
        if (id == ID_SMELT2) {
            machine.setSmeltProgressClient(1, data);
            return;
        }
        if (id == ID_XP) {
            machine.setStoredXpClient(data / 10F);
            return;
        }
        if (id == ID_WARM) {
            machine.setKeepWarmClient(data != 0);
            return;
        }
        if (id == ID_MATTER) {
            machine.setMatterClient(data);
            return;
        }
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
                machine.setSmeltProgressClient(0, data);
                break;
            case ID_RECIPE_TICKS:
                machine.setCurrentRecipeTicksClient(data);
                break;
            case ID_HEAT:
                machine.setHeatClient(data);
                break;
            case ID_POWER:
                machine.setPowerFlagsClient(data);
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
        int battery = inventorySlots.size() - 1, playerEnd = battery;

        if (slotIndex < machineSlots || slotIndex == battery) {
            if (!mergeItemStack(original, machineSlots, playerEnd, true)) {
                return null;
            }
            if (slot instanceof SlotOutputOnly) {                    // shift-click out of a smelter counts too
                ItemStack moved = result.copy();
                moved.stackSize = result.stackSize - original.stackSize;
                ((SlotOutputOnly) slot).smelted(player, moved);
            }
        } else if (com.sc.item.BatteryFeedSC.accepts(original) && !((Slot) inventorySlots.get(battery)).getHasStack()) {
            if (!SlotMergeSC.mergeValid(inventorySlots, original, battery, battery + 1)) {
                return null;
            }
        } else if (original.getItem() instanceof com.sc.item.ItemUpgradeSC) {
            // an upgrade this machine also takes as an ingredient (an Upgrade Station's recipe) goes
            // to the inputs first, then the upgrade slots
            boolean ingredient = machine.isItemValidForSlot(0, original);
            if (!(ingredient && SlotMergeSC.mergeValid(inventorySlots, original, 0, TileEntityMachineSC.INPUT_SLOTS))
                    && !SlotMergeSC.mergeValid(inventorySlots, original, TileEntityMachineSC.FIRST_UPGRADE_SLOT, machineSlots)
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
                ? mergeItemStack(stack, hotbarStart, firstPlayerSlot + 36, false)
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

    /** Upgrade slots take only the upgrades this machine uses (TileEntityMachineSC.isItemValidForSlot). */
    private static class SlotUpgrade extends Slot {
        SlotUpgrade(net.minecraft.inventory.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return inventory.isItemValidForSlot(getSlotIndex(), stack);
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

        @Override
        public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
            smelted(player, stack);
            super.onPickupFromSlot(player, stack);
        }

        /**
         * A smelter's product taken by a player: what a furnace's output slot does (the item's own
         * onCrafting, the smelting event other mods listen to, the iron / fish achievements) - the
         * experience stays in the machine for its button.
         */
        void smelted(EntityPlayer player, ItemStack stack) {
            if (stack == null || player.worldObj.isRemote || !(inventory instanceof TileEntityMachineSC)
                    || !((TileEntityMachineSC) inventory).getMachineType().isSmelter()) {
                return;
            }
            stack.onCrafting(player.worldObj, player, stack.stackSize);
            cpw.mods.fml.common.FMLCommonHandler.instance().firePlayerSmeltedEvent(player, stack);
            if (stack.getItem() == net.minecraft.init.Items.iron_ingot) {
                player.addStat(net.minecraft.stats.AchievementList.acquireIron, 1);
            }
            if (stack.getItem() == net.minecraft.init.Items.cooked_fished) {
                player.addStat(net.minecraft.stats.AchievementList.cookFish, 1);
            }
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (SlotMergeSC.refuseHotbarSwap(this, slotId, button, mode, player)) {
            return null;                                   // a hotbar key can't put more than the slot takes
        }
        return super.slotClick(slotId, button, mode, player);
    }
}
