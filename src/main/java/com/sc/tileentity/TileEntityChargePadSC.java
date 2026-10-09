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
    /** Who placed it ("" for a pad from before owners) and whether it charges only them and their team (ЭН-7). */
    private String owner = "";
    private boolean ownerOnly;

    public boolean isActive() {
        return active;
    }

    public String getOwner() {
        return owner;
    }

    /** BlockChargePadSC.onBlockPlacedBy - the placer. */
    public void setOwner(String name) {
        owner = name == null ? "" : name;
        markDirty();
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }

    /** The screen's "charge: everyone / owner and team" switch (ContainerEnergyStorageSC). */
    public void setOwnerOnly(boolean on) {
        ownerOnly = on;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);     // the screen shows the mode
        }
    }

    /**
     * May this player be charged from the pad? Never inside a private field that refuses them (the
     * same rule as sneak-click charging, but silent - it runs every few ticks); in owner-only mode
     * just the owner and players on the owner's scoreboard team. An ownerless pad charges everyone.
     */
    public boolean mayCharge(EntityPlayer p) {
        if (com.sc.ShieldEventHandler.privateFieldAgainst(worldObj, p, xCoord, yCoord, zCoord) != null) {
            return false;
        }
        if (!ownerOnly || owner.isEmpty() || owner.equalsIgnoreCase(p.getCommandSenderName())) {
            return true;
        }
        net.minecraft.scoreboard.Team mine = worldObj.getScoreboard().getPlayersTeam(owner);
        return mine != null && mine.isSameTeam(p.getTeam());
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
            EntityPlayer p = (EntityPlayer) o;
            if (!mayCharge(p)) {
                continue;
            }
            int used = chargePlayer(p, budget);
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

    // ---- NBT: the lit top and the charge mode go to the client with the block ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        active = nbt.getBoolean("PadActive");
        owner = nbt.getString("PadOwner");
        ownerOnly = nbt.getBoolean("PadOwnerOnly");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("PadActive", active);
        nbt.setString("PadOwner", owner);
        nbt.setBoolean("PadOwnerOnly", ownerOnly);
    }
}
