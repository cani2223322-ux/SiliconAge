package com.sc.inventory;

import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Read-only status screen for a Field Generator master node (§7/§9/§16) - no inventory slots at
 * all (the cluster holds no items), just the energy bar kept live via the same field-sync
 * pattern as ContainerMachineSC/ContainerGeneratorSC. Only ever opened on a master (see
 * BlockFieldGeneratorSC) - a linked node has nothing of its own worth a screen for.
 */
public class ContainerFieldGeneratorSC extends Container {

    private final TileEntityFieldGeneratorSC field;
    private final IntSyncSC sync = new IntSyncSC(5);  // energy, mode, node count, active, range

    // Button ids for enchantItem (the vanilla GUI-button packet, no custom networking needed).
    public static final int BTN_RANGE_MINUS_16 = 0, BTN_RANGE_MINUS_1 = 1, BTN_RANGE_PLUS_1 = 2,
            BTN_RANGE_PLUS_16 = 3, BTN_MODE = 4, BTN_COLOR = 5, BTN_REDSTONE = 6, BTN_FILTER = 7;
    /** Switches: BTN_FLAG_BASE + the bit's index (TileEntityFieldGeneratorSC.F_*). */
    public static final int BTN_FLAG_BASE = 10, FLAG_COUNT = 9;

    /** Server side of the screen's buttons, master only, owner / access list only. */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!field.isMaster() || !canInteractWith(player)) {
            return false;
        }
        if (!field.allowed(player)) {
            player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.noaccess", field.getOwner()));
            return false;
        }
        switch (id) {
            case BTN_RANGE_MINUS_16: field.adjustRange(-16); return true;
            case BTN_RANGE_MINUS_1: field.adjustRange(-1); return true;
            case BTN_RANGE_PLUS_1: field.adjustRange(1); return true;
            case BTN_RANGE_PLUS_16: field.adjustRange(16); return true;
            case BTN_MODE: field.cycleMode(); return true;
            case BTN_COLOR: field.cycleColor(); return true;
            case BTN_REDSTONE: field.cycleRedstone(); return true;
            case BTN_FILTER: field.cycleFilter(); return true;
            default:
                if (id >= BTN_FLAG_BASE && id < BTN_FLAG_BASE + FLAG_COUNT) {
                    field.toggle(1 << (id - BTN_FLAG_BASE));
                    return true;
                }
                return false;
        }
    }

    public ContainerFieldGeneratorSC(InventoryPlayer playerInv, TileEntityFieldGeneratorSC field) {
        this.field = field;
        // Hidden, locked hotbar slots. Vanilla NetHandlerPlayServer.processPlayerBlockPlacement looks up
        // the held item's slot in the container the click just opened and NPEs (server crash "Ticking
        // memory connection") if there is none - e.g. right-clicking with a charged item whose NBT
        // differs client/server. Off-screen and never drawn, clicked or shift-clicked.
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new SlotHidden(playerInv, col));
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            return null;                      // the hidden hotbar is not for use
        }
        return super.slotClick(slotId, button, mode, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }

    private static class SlotHidden extends Slot {
        SlotHidden(InventoryPlayer inv, int index) {
            super(inv, index, -10000, -10000);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean func_111238_b() {      // not hoverable, not rendered
            return false;
        }
    }

    public TileEntityFieldGeneratorSC getField() {
        return field;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return field.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sync.send(this, crafters, new int[]{field.getEnergyStored(), field.getMode().ordinal(),
                field.getNodeCount(), field.isActive() ? 1 : 0, field.getRange()});
    }

    /**
     * The first sync round carries every value in index order (IntSyncSC); until its last one
     * (range, index 4) has landed the others are still 0, and applying them early showed a frame
     * of "0 nodes / range 1". Later rounds only carry what changed, so from then on any of them
     * applies at once.
     */
    private boolean stateReady;

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id == 0) {
            field.setEnergyStoredClient(sync.value(0));
        } else if (id > 0) {
            if (id == sync.count() - 1) {
                stateReady = true;
            }
            if (stateReady) {
                field.setClientState(sync.value(1), sync.value(2), sync.value(3) != 0, sync.value(4));
            }
        }
    }
}
