package com.sc.inventory;

import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.1, rule ПО-1): the «Терминал обмена»'s container, one per player (a
 * Tuner serves several at once). Opened by GuiHandlerSC.TRADE_GUI_ID with x = the Tuner's entity id. Holds no items of
 * its own - a deal takes its inputs straight from the player's inventory when it is made (TradeServerSC), so there is
 * nothing to lose or hand back on close; the deals and their state come by SettleNetSC.TRADE_STATE. Valid while the
 * Tuner lives and stays within REACH.
 * <p>
 * It does show the player's own inventory (as a merchant's screen does): with it the server's detectAndSendChanges
 * keeps the client's inventory right while the screen is open - a deal's inputs gone, its output in - which the
 * player's own inventory container would not (a set-slot packet for window 0 reaches only the hotbar while another
 * window is open). Shift-click moves a stack between the hotbar and the rest, as in the player's own inventory.
 */
public class ContainerTradeSC extends Container {

    /** The player must stay this close to the Tuner (blocks). */
    public static final double REACH = 8D;
    /** The player's inventory on the screen (item coordinates; GuiTradeSC draws the pockets there): as the mod's big screens. */
    public static final int INV_X = 44, INV_Y = 155, HOTBAR_Y = 213;

    protected final EntityPlayer player;
    protected final EntityTunerSC tuner;

    public ContainerTradeSC(EntityPlayer player, EntityTunerSC tuner) {
        this.player = player;
        this.tuner = tuner;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public EntityTunerSC getTuner() {
        return tuner;
    }

    /** The player whose screen this is. */
    public EntityPlayer getPlayer() {
        return player;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return tuner != null && tuner.isEntityAlive() && tuner.worldObj == p.worldObj && p.getDistanceSqToEntity(tuner) <= REACH * REACH;
    }

    /** Shift-click: the main inventory (slots 0..26) to the hotbar (27..35) and back. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = index >= 0 && index < inventorySlots.size() ? (Slot) inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        boolean moved = index < 27 ? mergeItemStack(stack, 27, 36, false) : mergeItemStack(stack, 0, 27, false);
        if (!moved) {
            return null;
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == copy.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(p, stack);
        return copy;
    }
}
