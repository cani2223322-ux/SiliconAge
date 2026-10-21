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
    /**
     * Who placed it (nobody for a pad from before owners) and whether it charges only them and their team (ЭН-7). ЭН-3 (audit
     * 2026-10-20): by name and UUID - an old pad's owner by the name until his first charge writes the UUID down.
     */
    private final com.sc.util.OwnerSC owner = new com.sc.util.OwnerSC();
    private boolean ownerOnly;

    public boolean isActive() {
        return active;
    }

    public String getOwner() {
        return owner.name();
    }

    public void setOwner(String name) {
        owner.set(name, worldObj);
        markDirty();
    }

    /** BlockChargePadSC.onBlockPlacedBy - the placer (ЭН-3: his name and UUID; a FakePlayer - nobody). */
    public void setOwner(EntityPlayer p) {
        owner.set(p);
        markDirty();
    }

    /** ЭН-3: the pad's owner (by the UUID; an old pad by the name - the first match writes the UUID down). */
    public boolean isOwner(EntityPlayer p) {
        return owner.matches(p, this);
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
        if (!ownerOnly || owner.isEmpty() || owner.matches(p, this)) {
            return true;
        }
        net.minecraft.scoreboard.Team mine = worldObj.getScoreboard().getPlayersTeam(owner.name());
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
            budget -= chargeWhole(p.inventory.armorInventory[i], budget);
        }
        ItemStack held = p.getCurrentEquippedItem();
        // a blocking blade keeps its block (getItemInUse() is client-only - isUsingItem() works on
        // a dedicated server, and the item in use is always the held one)
        if (!p.isUsingItem()) {
            budget -= chargeWhole(held, budget);
        }
        for (ItemStack s : p.inventory.mainInventory) {
            if (budget <= 0) {
                break;
            }
            if (s != held) {
                budget -= chargeWhole(s, budget);
            }
        }
        return start - budget;
    }

    /** The slots' items from one round's budget, each charged as for the whole round (chargeWhole). @return EU taken */
    @Override
    protected int chargeSlotItem(int max) {
        int start = max;
        for (int k = 0; k < chargeSlots() && max > 0; k++) {
            max -= chargeWhole(getStackInSlot(chargeSlotIndex(k)), max);
        }
        return start - max;
    }

    /**
     * One item charged for a whole round: up to EVERY calls of chargeItem (one a tick the round
     * stands for) - a battery takes at most its rate a call, an IC2 item its transfer limit, so
     * one call every EVERY ticks would charge them at a fifth of the speed. An item that takes all
     * it wants at once (a suit) stops after the first: the next call gives nothing. @return EU taken
     */
    private int chargeWhole(ItemStack s, int budget) {
        int used = 0;
        for (int k = 0; k < EVERY && used < budget; k++) {
            int t = chargeItem(s, budget - used);
            if (t <= 0) {
                break;
            }
            used += t;
        }
        return used;
    }

    // ---- NBT: the lit top and the charge mode go to the client with the block ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        active = nbt.getBoolean("PadActive");
        owner.read(nbt, "PadOwner", "PadOwnerId");
        ownerOnly = nbt.getBoolean("PadOwnerOnly");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("PadActive", active);
        owner.write(nbt, "PadOwner", "PadOwnerId");
        nbt.setBoolean("PadOwnerOnly", ownerOnly);
    }
}
