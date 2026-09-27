package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.machine.UpgradeType;
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
@cpw.mods.fml.common.Optional.Interface(iface = "ic2.api.energy.tile.IMultiEnergySource", modid = "industrialupgrade")
public class TileEntityEnergyStorageSC extends TileEntityEnergyBase implements net.minecraft.inventory.ISidedInventory, ic2.api.energy.tile.IMultiEnergySource {

    /** Slots: 0 charges an item, 1 takes the energy out of one, 2..5 the upgrades. */
    public static final int SLOT_CHARGE = 0, SLOT_DISCHARGE = 1, FIRST_UPGRADE_SLOT = 2, UPGRADE_SLOTS = 4,
            FIRST_EXTRA_CHARGE = FIRST_UPGRADE_SLOT + UPGRADE_SLOTS, MAX_CHARGE_SLOTS = 4,
            SLOT_COUNT = FIRST_EXTRA_CHARGE + MAX_CHARGE_SLOTS - 1;
    /** Capacity upgrade: +25% of the tier's capacity each. */
    public static final int CAPACITY_PERCENT_PER_UPGRADE = 25;
    /** Overdrive: one more packet of the output voltage a tick each, at most this many. */
    public static final int MAX_EXTRA_PACKETS = 4;

    /** TODO(design doc has no storage blocks): capacities per tier, LV..XV (XV close to the int ceiling). */
    public static final int[] CAPACITY = {40000, 300000, 4000000, 40000000, 300000000, 1000000000, 2000000000};

    private ForgeDirection facing = ForgeDirection.SOUTH;
    private ItemStack chargeSlot;
    /** Charge slots 2..4 (inventory slots 6..8) - how many are open goes by the tier (chargeSlots()). */
    private final ItemStack[] extraCharge = new ItemStack[MAX_CHARGE_SLOTS - 1];
    private ItemStack dischargeSlot;
    private final ItemStack[] upgradeSlots = new ItemStack[UPGRADE_SLOTS];
    /** Set once the upgrades went into the dropped item, so breakBlock doesn't drop them loose too. */
    private boolean upgradesInItem;
    /** The comparator level last announced to the neighbours. */
    private int lastComparator = -1;
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
        long cap = capacityOf(getTier()) * (100L + (long) CAPACITY_PERCENT_PER_UPGRADE * upgradeCount(UpgradeType.ENERGY_STORAGE)) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    // ---- upgrades: Transformer (output a tier up), Energy Storage (+25%), Overdrive (+1 packet a tick) ----

    public static boolean acceptsUpgrade(ItemStack s) {
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC)) {
            return false;
        }
        UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        return t == UpgradeType.TRANSFORMER || t == UpgradeType.ENERGY_STORAGE || t == UpgradeType.OVERDRIVE;
    }

    public int upgradeCount(UpgradeType type) {
        int n = 0;
        for (ItemStack s : upgradeSlots) {
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, type == UpgradeType.OVERDRIVE ? MAX_EXTRA_PACKETS : UpgradeType.MAX_EFFECTIVE);
    }

    /** Transformer upgrades send the output out a tier higher each (up to XV). */
    @Override
    public Tier outputTier() {
        Tier[] tiers = Tier.values();
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + upgradeCount(UpgradeType.TRANSFORMER))];
    }

    /** One packet a tick, and one more per Overdrive upgrade. */
    @Override
    public int packetsPerTick() {
        return 1 + upgradeCount(UpgradeType.OVERDRIVE);
    }

    /** Industrial Upgrade's energy net: several packets a tick (see packetsPerTick()). */
    @Override
    public boolean sendMultibleEnergyPackets() {
        return packetsPerTick() > 1;
    }

    @Override
    public double getMultibleEnergyPacketAmount() {
        return packetsPerTick();
    }

    /** The upgrades as an item NBT compound for the dropped block (null when empty) - see TileEntityMachineSC. */
    public NBTTagCompound upgradesForItem() {
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeSlots[i] != null) {
                NBTTagCompound t = upgradeSlots[i].writeToNBT(new NBTTagCompound());
                t.setByte("Slot", (byte) i);
                list.appendTag(t);
            }
        }
        upgradesInItem = list.tagCount() > 0;
        if (!upgradesInItem) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Items", list);
        return tag;
    }

    public boolean upgradesInItem() {
        return upgradesInItem;
    }

    /** Puts back what upgradesForItem() saved (on placement, before the charge is loaded). */
    public void loadUpgradesFromItem(NBTTagCompound tag) {
        net.minecraft.nbt.NBTTagList list = tag == null ? null : tag.getTagList("Items", 10);
        for (int k = 0; list != null && k < list.tagCount(); k++) {
            NBTTagCompound t = list.getCompoundTagAt(k);
            int i = t.getByte("Slot");
            if (i >= 0 && i < UPGRADE_SLOTS) {
                upgradeSlots[i] = ItemStack.loadItemStackFromNBT(t);
            }
        }
        markDirty();
    }

    // ---- the comparator: 0 empty, 1..15 by how full ----

    public int comparatorLevel() {
        int e = getEnergyStored();
        return e <= 0 ? 0 : Math.min(15, 1 + (int) (14L * e / Math.max(1, getMaxEnergyStored())));
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
    public int offerableEnergy() {
        return switchedOn() ? super.offerableEnergy() : 0;
    }

    @Override
    public int demandedEnergy() {
        return switchedOn() ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
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
        if (switchedOn()) {                                          // off: no charging or emptying items either
            chargeRound();
            dischargeRound();
        }
        if (worldObj.getTotalWorldTime() % 10 == 0) {
            int level = comparatorLevel();
            if (level != lastComparator) {
                lastComparator = level;
                worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());   // tell the comparators round it
            }
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            if (energyAtWindowStart >= 0) {
                flowPerTick = (getEnergyStored() - energyAtWindowStart) / 20;
            }
            energyAtWindowStart = getEnergyStored();
        }
    }

    /**
     * Charge slots by the tier: LV and MV one, HV and EV two, IV three, QV and XV four - each
     * charging on its own at the tier's voltage, so a bigger storage charges more at once.
     */
    public int chargeSlots() {
        switch (getTier()) {
            case LV:
            case MV: return 1;
            case HV:
            case EV: return 2;
            case IV: return 3;
            default: return 4;
        }
    }

    /** The self-test's handle on one charging round (chargeRound is protected). */
    public void chargeRoundForTest() {
        chargeRound();
    }

    /** The inventory slot of charge slot k (0 .. chargeSlots()-1). */
    public static int chargeSlotIndex(int k) {
        return k == 0 ? SLOT_CHARGE : FIRST_EXTRA_CHARGE + k - 1;
    }

    /** One tick's charging: each open slot's item, at most one packet of the tier's voltage each. */
    protected void chargeRound() {
        boolean any = false;
        for (int k = 0; k < chargeSlots(); k++) {
            int taken = chargeItem(getStackInSlot(chargeSlotIndex(k)), Math.min(getEnergyStored(), getTier().getVoltage()));
            if (taken > 0) {
                removeEnergy(taken);
                any = true;
            }
        }
        if (any) {
            markDirty();
        }
    }

    /**
     * One tick's discharging: energy out of the discharge slot's item into the buffer, at most one
     * packet of the tier's voltage and what room there is.
     */
    protected void dischargeRound() {
        int room = getMaxEnergyStored() - getEnergyStored();
        if (dischargeSlot == null || room <= 0) {
            return;
        }
        int got = dischargeItem(dischargeSlot, Math.min(room, getTier().getVoltage()));
        if (got > 0) {
            addEnergy(got);
            markDirty();
        }
    }

    /** Takes up to `max` EU out of an item: the mod's suits, blades and drills, and with IC2 any battery. @return EU got */
    public int dischargeItem(ItemStack s, int max) {
        if (s == null || max <= 0) {
            return 0;
        }
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            return com.sc.item.ItemArmorSC.discharge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            return com.sc.item.ItemBladeSC.discharge(s, max);
        }
        if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            return com.sc.item.ItemDrillSC.discharge(s, max);
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.discharge(s, max);
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2Charge.discharge(s, max, getTier().toIc2Tier());
        }
        return 0;
    }

    /** What goes in the discharge slot: something the storage can take energy out of. */
    public static boolean isDischargeable(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.getItem() instanceof com.sc.item.ItemArmorSC || stack.getItem() instanceof com.sc.item.ItemBladeSC
                || stack.getItem() instanceof com.sc.item.ItemDrillSC || com.sc.item.ItemBatterySC.isBattery(stack)) {
            return true;
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) && Ic2Charge.providesEnergy(stack);
    }

    /** Charges the slot's item up to `max` EU (the caller takes them out of the buffer). @return EU taken */
    protected int chargeSlotItem(int max) {
        int start = max;
        for (int k = 0; k < chargeSlots() && max > 0; k++) {       // the charge pad: one budget across its slots
            max -= chargeItem(getStackInSlot(chargeSlotIndex(k)), max);
        }
        return start - max;
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
        if (com.sc.item.ItemWrenchSC.isElectric(s)) {
            return com.sc.item.ItemWrenchSC.charge(s, max);
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.charge(s, max);
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
        if (com.sc.item.ItemWrenchSC.isElectric(s)) {
            return com.sc.item.ItemWrenchSC.tierOf(s).chargeTier.ordinal() <= block.ordinal();
        }
        if (com.sc.item.ItemBatterySC.isBattery(s)) {
            return com.sc.item.ItemBatterySC.tierOf(s).ordinal() <= block.ordinal();
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

        static int discharge(ItemStack s, int max, int tier) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem) || ic2.api.item.ElectricItem.manager == null
                    || !((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s)) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.discharge(s, max, tier, false, true, false);
        }

        static boolean providesEnergy(ItemStack s) {
            return s.getItem() instanceof ic2.api.item.IElectricItem && ((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s);
        }
    }

    // ---- the charge slot ----

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == SLOT_CHARGE) {
            return chargeSlot;
        }
        if (slot == SLOT_DISCHARGE) {
            return dischargeSlot;
        }
        if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            return extraCharge[slot - FIRST_EXTRA_CHARGE];
        }
        return slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE ? upgradeSlots[slot - FIRST_UPGRADE_SLOT] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack s = getStackInSlot(slot);
        if (s == null) {
            return null;
        }
        ItemStack out = s.splitStack(amount);
        if (s.stackSize <= 0) {
            setInventorySlotContents(slot, null);
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
        if (slot == SLOT_CHARGE) {
            chargeSlot = stack;
        } else if (slot == SLOT_DISCHARGE) {
            dischargeSlot = stack;
        } else if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            extraCharge[slot - FIRST_EXTRA_CHARGE] = stack;
        } else if (slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE) {
            upgradeSlots[slot - FIRST_UPGRADE_SLOT] = stack;
            if (worldObj != null && !worldObj.isRemote) {
                refreshEnergyNet();   // a transformer changes the output tier IC2 cached
            }
        } else {
            return;
        }
        markDirty();
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
        return 64;
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
        if (slot == SLOT_CHARGE) {
            return isChargeable(stack) && tierAllows(stack);
        }
        if (slot == SLOT_DISCHARGE) {
            return isDischargeable(stack);
        }
        if (slot >= FIRST_EXTRA_CHARGE && slot < SLOT_COUNT) {
            return slot - FIRST_EXTRA_CHARGE + 1 < chargeSlots() && isChargeable(stack) && tierAllows(stack);
        }
        return slot >= FIRST_UPGRADE_SLOT && slot < FIRST_EXTRA_CHARGE && acceptsUpgrade(stack);
    }

    // ---- automation: hoppers and pipes reach the charge slots and the discharge slot, never the upgrades ----

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int n = chargeSlots();
        int[] out = new int[n + 1];
        for (int k = 0; k < n; k++) {
            out[k] = chargeSlotIndex(k);
        }
        out[n] = SLOT_DISCHARGE;
        return out;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return (slot < FIRST_UPGRADE_SLOT || slot >= FIRST_EXTRA_CHARGE) && getStackInSlot(slot) == null && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot < FIRST_UPGRADE_SLOT || slot >= FIRST_EXTRA_CHARGE;
    }

    /** Weapons and armor pieces go in the charge slot (isItemValidForSlot adds the tier rule). */
    public static boolean isChargeable(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemWeaponSC || stack.getItem() instanceof com.sc.item.ItemArmorSC
                || stack.getItem() instanceof com.sc.item.ItemBladeSC || stack.getItem() instanceof com.sc.item.ItemDrillSC
                || com.sc.item.ItemWrenchSC.isElectric(stack) || com.sc.item.ItemBatterySC.isBattery(stack));
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
        dischargeSlot = nbt.hasKey("DischargeSlot") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("DischargeSlot")) : null;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            String k = "Upgrade" + i;
            upgradeSlots[i] = nbt.hasKey(k) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(k)) : null;
        }
        for (int i = 0; i < extraCharge.length; i++) {
            String k = "ChargeSlot" + (i + 2);
            extraCharge[i] = nbt.hasKey(k) ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(k)) : null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Facing", facing.ordinal());
        if (chargeSlot != null) {
            nbt.setTag("ChargeSlot", chargeSlot.writeToNBT(new NBTTagCompound()));
        }
        if (dischargeSlot != null) {
            nbt.setTag("DischargeSlot", dischargeSlot.writeToNBT(new NBTTagCompound()));
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeSlots[i] != null) {
                nbt.setTag("Upgrade" + i, upgradeSlots[i].writeToNBT(new NBTTagCompound()));
            }
        }
        for (int i = 0; i < extraCharge.length; i++) {
            if (extraCharge[i] != null) {
                nbt.setTag("ChargeSlot" + (i + 2), extraCharge[i].writeToNBT(new NBTTagCompound()));
            }
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
