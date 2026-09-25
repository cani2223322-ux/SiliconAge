package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.ItemWeaponSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Energy storage (LV..EV): a big buffer that takes energy on five faces and sends it out of its
 * front face only (the face turned to the player when it was placed), at its tier's voltage -
 * same rules for the mod's own net and for IC2. A slot charges a weapon or an armor piece of the
 * block's tier or lower from the stored energy.
 * Broken and picked up, it keeps its charge in the item (BlockEnergyStorageSC).
 */
public class TileEntityEnergyStorageSC extends TileEntityEnergyBase implements IInventory {

    /** TODO(design doc has no storage blocks): capacities per tier, LV..XV (XV close to the int ceiling). */
    public static final int[] CAPACITY = {40000, 300000, 4000000, 40000000, 300000000, 1000000000, 2000000000};

    private ForgeDirection facing = ForgeDirection.SOUTH;
    private ItemStack chargeSlot;
    /** EU gained (+) or lost (-) per tick, averaged over the last second - shown on the screen. */
    private int flowPerTick;
    private int energyAtWindowStart = -1;

    public TileEntityEnergyStorageSC() {
        super(Tier.LV);
    }

    public void setStorageTier(Tier tier) {
        setTier(tier);
    }

    public static int capacityOf(Tier tier) {
        return CAPACITY[tier.ordinal()];
    }

    @Override
    public int getMaxEnergyStored() {
        return capacityOf(getTier());
    }

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        boolean changed = this.facing != facing;
        this.facing = facing;
        if (changed) {
            refreshEnergyNet();       // IC2 caches the faces a tile emits / accepts on - re-announce it
        }
    }

    // ---- faces: in on five, out on the front ----

    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return true;
    }

    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        return side != facing;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        return new ForgeDirection[]{facing};
    }

    /** Loaded charge from a placed item (BlockEnergyStorageSC.onBlockPlacedBy). */
    public void setStoredFromItem(int eu) {
        addEnergy(Math.max(0, eu));
    }

    public int getFlowPerTick() {
        return flowPerTick;
    }

    public void setFlowClient(int flow) {
        flowPerTick = flow;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        chargeRound();
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            if (energyAtWindowStart >= 0) {
                flowPerTick = (getEnergyStored() - energyAtWindowStart) / 20;
            }
            energyAtWindowStart = getEnergyStored();
        }
    }

    /** One tick's charging: the slot's item, at most one packet of the tier's voltage. */
    protected void chargeRound() {
        int taken = chargeSlotItem(Math.min(getEnergyStored(), getTier().getVoltage()));
        if (taken > 0) {
            removeEnergy(taken);
            markDirty();
        }
    }

    /** Charges the slot's item up to `max` EU (the caller takes them out of the buffer). @return EU taken */
    protected int chargeSlotItem(int max) {
        return chargeItem(chargeSlot, max);
    }

    /**
     * Charges one item up to `max` EU if it is chargeable here: only items of this block's tier
     * or lower (as IC2 chargers) - the mod's suits, blades and weapons, and with IC2 any IC2
     * electric item. @return EU taken
     */
    public int chargeItem(ItemStack s, int max) {
        if (s == null || max <= 0 || !tierAllows(s)) {
            return 0;
        }
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return com.sc.item.ItemArmorSC.charge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.charge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.charge(s, max);
        }
        if (s.getItem() instanceof ItemWeaponSC) {
            return ItemWeaponSC.charge(s, ((ItemWeaponSC) s.getItem()).getType(), max);
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2Charge.charge(s, max, getTier().toIc2Tier());
        }
        return 0;
    }

    /** The mod's own item is at most this block's tier (IC2 items are checked by IC2's charge()). */
    public boolean tierAllows(ItemStack s) {
        Tier block = getTier();
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return ((com.sc.item.ItemArmorSC) s.getItem()).getSuit().chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.typeOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.typeOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (s.getItem() instanceof ItemWeaponSC) {
            return ((ItemWeaponSC) s.getItem()).getType().tier.ordinal() <= block.ordinal();
        }
        return true;
    }

    /** Kept apart so IC2's API is only loaded when IC2 is. */
    private static final class Ic2Charge {
        static int charge(ItemStack s, int max, int tier) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem) || ic2.api.item.ElectricItem.manager == null) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.charge(s, max, tier, false, false);
        }
    }

    // ---- the charge slot ----

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? chargeSlot : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || chargeSlot == null) {
            return null;
        }
        ItemStack out = chargeSlot.splitStack(amount);
        if (chargeSlot.stackSize <= 0) {
            chargeSlot = null;
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == 0) {
            chargeSlot = stack;
            markDirty();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.energyStorage";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && isChargeable(stack) && tierAllows(stack);
    }

    /** Weapons and armor pieces go in the charge slot (isItemValidForSlot adds the tier rule). */
    public static boolean isChargeable(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemWeaponSC || stack.getItem() instanceof com.sc.item.ItemArmorSC
                || stack.getItem() instanceof com.sc.item.ItemBladeSC || stack.getItem() instanceof com.sc.item.ItemDrillSC);
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.SOUTH;
        }
        chargeSlot = nbt.hasKey("ChargeSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("ChargeSlot")) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        if (chargeSlot != null) {
            nbt.setTag("ChargeSlot", chargeSlot.writeToNBT(new NBTTagCompound()));
        }
    }

    /** Facing reaches the client with the chunk, so the front face renders on the right side. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
