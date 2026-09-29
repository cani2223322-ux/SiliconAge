package com.sc.tileentity;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

/**
 * Charge pad (LV..EV), like IC2's: an energy storage (same buffer, slot, output face) with a pad
 * on top. A player standing on it gets the worn armour, then the item in hand, then the rest of the
 * inventory charged from the stored energy - at most the tier's voltage per tick in all (the slot
 * and every player on the pad share it), and only items of the pad's tier or lower (as IC2
 * chargers; chargeItem). The mod's suits, blades and weapons always; with IC2, any IC2 electric
 * item too. The top lights up while it charges.
 */
public class TileEntityChargePadSC extends TileEntityEnergyStorageSC {

    /** Ticks between two charging rounds (each gives voltage x this). */
    private static final int EVERY = 5;

    private boolean active;

    public boolean isActive() {
        return active;
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.chargePad";
    }

    @Override
    protected void switchedOff() {
        if (active) {
            active = false;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** Every EVERY ticks: one budget of voltage x EVERY for the slot first, then the players on top. */
    @Override
    protected void chargeRound() {
        if (worldObj.getTotalWorldTime() % EVERY != 0) {
            return;
        }
        int budget = Math.min(getEnergyStored(), getTier().getVoltage() * EVERY);
        int start = budget;
        budget -= chargeSlotItem(budget);
        boolean charged = false;
        AxisAlignedBB top = AxisAlignedBB.getBoundingBox(xCoord, yCoord + 1, zCoord, xCoord + 1, yCoord + 1.5, zCoord + 1);
        List players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, top);
        for (Object o : players) {
            if (budget <= 0) {
                break;
            }
            int used = chargePlayer((EntityPlayer) o, budget);
            budget -= used;
            charged |= used > 0;
        }
        int used = start - budget;
        if (used > 0) {
            removeEnergy(used);
            markDirty();
        }
        if (charged != active) {
            active = charged;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);     // the lit / dark top
        }
    }

    /** @return EU the player's items took out of `budget` */
    private int chargePlayer(EntityPlayer p, int budget) {
        int start = budget;
        for (int i = 3; i >= 0 && budget > 0; i--) {                  // helmet first, like the HUD
            budget -= chargeItem(p.inventory.armorInventory[i], budget);
        }
        ItemStack held = p.getCurrentEquippedItem();
        // a blocking blade keeps its block (getItemInUse() is client-only - isUsingItem() works on
        // a dedicated server, and the item in use is always the held one)
        if (!p.isUsingItem()) {
            budget -= chargeItem(held, budget);
        }
        for (ItemStack s : p.inventory.mainInventory) {
            if (budget <= 0) {
                break;
            }
            if (s != held) {
                budget -= chargeItem(s, budget);
            }
        }
        return start - budget;
    }

    // ---- NBT: the lit top goes to the client with the block ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        active = nbt.getBoolean("PadActive");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("PadActive", active);
    }
}
