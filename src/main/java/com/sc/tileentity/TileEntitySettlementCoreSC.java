package com.sc.tileentity;

import com.sc.dim.SubstrateSC;
import com.sc.energy.FieldMode;
import com.sc.settle.RosterSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.1-§5.2, rule ПП-6): «Ядро поселения» - a Field Generator's master
 * that needs no energy (★ «вечное ядро»: by the lore, a surviving splinter of the Lithographers' reactor). It reuses
 * the field's whole protection unchanged - the player's own Field Generator is not touched by any of this:
 * <ul>
 * <li>it is in the field's ACTIVE list like any up field, so privateRefusing / privateFor (the bridge, the wrench, the
 * drill, the quarry, the safe-spot finder, the armour station), fieldWith (F_NO_ENDER), noSpawnFor (F_NO_SPAWN), the
 * explosion shield, the EMP screen (EmpRulesSC.screenAbsorbs) and the overlap check of players' fields all see it;
 * FieldRendererSC draws its dome (the TESR is found by the superclass);</li>
 * <li>pay() is always true and the upkeep 0, so every protective action is free; it takes no energy (no sink, no
 * connection); its settings can't be changed: the field's screen never opens (the block has none, FieldNetSC wants
 * that screen open), the wrench's paste and the link module need the owner's rights - the owner is
 * «[Поселение-N]», which no player can be;</li>
 * <li>the fixed settings (fixed(): DOME of the settlement's radius from its floor up - the zone's offset brings the
 * dome's base from the core down to the floor -, F_PRIVATE + F_NO_MECHANISMS + F_NO_SPAWN + F_NO_ENDER + F_DAMAGE +
 * F_EMP_SCREEN + F_SHOW, FILTER_HOSTILE - only IMob are pushed, the Tuners and guards stay -, no redstone control,
 * always on, the turquoise shell) are put back over whatever an NBT holds, every load.</li>
 * </ul>
 * Package C writes the rest: settlementTick (every SLOW_TICKS: the roster - spawning the NEVER slots whose posts are
 * loaded, the Clone Capsule's copies of the DEAD ones with no player within SettlementSC.RESPAWN_CLEAR, the lost
 * creatures -, the first pass, the known docks), a record missing from the registry rebuilt from this tile's NBT.
 * The invariants above are the coordinator's; C may add, not loosen them.
 */
public class TileEntitySettlementCoreSC extends TileEntityFieldGeneratorSC {

    /** ПП-6: the core's switches. */
    public static final int FLAGS = F_PRIVATE | F_NO_MECHANISMS | F_NO_SPAWN | F_NO_ENDER | F_DAMAGE | F_EMP_SCREEN | F_SHOW;
    /** «Бирюза Наладчиков» (PRESETS[11]). */
    public static final int SHELL_RGB = 0x3CD2C8;
    /** The roster and the rest of package C's work run this often (ticks). */
    public static final int SLOW_TICKS = 20;
    private static final String NBT_ID = "scSettlement", NBT_RADIUS = "scRadius", NBT_FLOOR = "scFloorY";

    /** The registry's id (-1 not set up yet), the dome's radius, the floor's Y. */
    private int settlementId = -1, radius = 24, floorY = -1;
    /** set up or loaded: the fixed settings are on. A core placed by hand / setblock has neither - its first tick does it. */
    private boolean configured;
    /** Package C's transient roster state (the census's misses; afresh after a reload). */
    private final RosterSC.State roster = new RosterSC.State();

    public TileEntitySettlementCoreSC() {
    }

    /**
     * Package S, right after placing the core block in its piece: the settlement this core holds. Applies the fixed
     * settings at once (the field is up from its first tick).
     */
    public void setup(int id, int domeRadius, int floor) {
        configured = true;
        settlementId = id;
        radius = Math.max(1, domeRadius);
        floorY = floor;
        NBTTagCompound n = new NBTTagCompound();
        super.writeToNBT(n);
        fixed(n);
        super.readFromNBT(n);
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public int settlementId() {
        return settlementId;
    }

    public int radius() {
        return radius;
    }

    public int floorY() {
        return floorY;
    }

    /** The fixed settings written over `n` (a whole NBT of this tile, its coordinates in it). */
    private void fixed(NBTTagCompound n) {
        int x = n.getInteger("x"), y = n.getInteger("y"), z = n.getInteger("z");
        n.setInteger("Mode", FieldMode.DOME.ordinal());
        n.setInteger("Range", radius);
        n.setInteger("Height", 0);
        n.setBoolean("Master", true);
        NBTTagList nodes = new NBTTagList();
        NBTTagCompound self = new NBTTagCompound();
        self.setInteger("X", x);
        self.setInteger("Y", y);
        self.setInteger("Z", z);
        nodes.appendTag(self);
        n.setTag("Nodes", nodes);
        n.removeTag("MasterX");
        n.removeTag("MasterY");
        n.removeTag("MasterZ");
        n.setInteger("Flags", FLAGS);
        n.setInteger("ChargeMode", CHARGE_ARMOR_FIRST);           // present: the old-save branch adds no charging sparks
        n.setInteger("Redstone", REDSTONE_ALWAYS);
        n.setInteger("Filter", FILTER_HOSTILE);
        n.setInteger("Anchor", ANCHOR_NODES);
        n.removeTag("AnchorPoint");
        n.setInteger("OffX", 0);
        n.setInteger("OffY", floorY < 0 ? 0 : Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, floorY - y)));   // the dome from the floor up
        n.setInteger("OffZ", 0);
        n.setInteger("Outline", OUTLINE_NEVER);
        n.setInteger("Anim", ANIM_PULSE);
        n.setIntArray("RGB", new int[]{SHELL_RGB, SHELL_RGB, SHELL_RGB});
        n.setString("Owner", settlementId < 0 ? SettlementSC.ownerName(0) : SettlementSC.ownerName(settlementId));
        n.setTag("Access", new NBTTagList());
        n.setBoolean("PowerOff", false);
        n.setInteger("EnergySC", 0);
        n.removeTag("Upgrades");
        n.removeTag("Battery");
    }

    // ------------------------------------------------------------------ eternal: never pays, never takes energy

    @Override
    public boolean pay(int eu) {
        return true;
    }

    @Override
    public int upkeepPerTick() {
        return 0;
    }

    @Override
    public int extrasPerTick() {
        return 0;
    }

    @Override
    public int demandedEnergy() {
        return 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public boolean isEnergySink() {
        return false;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return false;
    }

    @Override
    public boolean acceptsEnergyFrom(net.minecraft.tileentity.TileEntity emitter, ForgeDirection side) {
        return false;
    }

    @Override
    public boolean canItemCharge(EntityPlayer p) {
        return false;
    }

    @Override
    public int extractForItemCharging(int max) {
        return 0;
    }

    /** No screen, no slots: nothing for a player to use. */
    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return false;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        // nothing goes in (no upgrades, no battery)
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public void updateEntity() {
        if (!configured && worldObj != null && !worldObj.isRemote) {
            setup(settlementId, radius, floorY);                // placed by hand (id -1): its field protects as any core's, no roster
        }
        super.updateEntity();
        if (worldObj != null && !worldObj.isRemote && settlementId >= 0
                && (worldObj.getTotalWorldTime() + (xCoord * 31 + zCoord * 17 & 15)) % SLOW_TICKS == 0) {
            settlementTick();
        }
    }

    /**
     * Package C: the roster, the Clone Capsule, the first pass, the known docks (RosterSC). Server, the Substrate. A
     * record the registry lost is made again from this tile (RosterSC.restore); a record of this id standing elsewhere
     * (another core's - a registry rebuilt by hand) leaves this core its field only.
     */
    protected void settlementTick() {
        if (!SubstrateSC.is(worldObj)) {
            return;
        }
        SettlementRegistrySC r = SettlementSC.registry(worldObj);
        if (r == null) {
            return;
        }
        long now = worldObj.getTotalWorldTime();
        SettlementRegistrySC.Settlement s = r.byId(settlementId);
        if (s == null) {
            s = RosterSC.restore(worldObj, r, settlementId, xCoord, yCoord, zCoord, radius, floorY, now);
        }
        if (s == null || s.dim != worldObj.provider.dimensionId || s.x != xCoord || s.z != zCoord) {
            return;
        }
        RosterSC.tick(worldObj, r, s, roster, now);
    }

    /** The world test (§11.2 п.7): the settlement's second now, not at its turn. Server; does nothing for a core of no settlement. */
    public void settlementTickNow() {
        if (worldObj != null && !worldObj.isRemote && settlementId >= 0) {
            settlementTick();
        }
    }

    // ------------------------------------------------------------------ NBT: the fixed settings over every load

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        configured = true;
        settlementId = nbt.hasKey(NBT_ID) ? nbt.getInteger(NBT_ID) : -1;
        radius = nbt.hasKey(NBT_RADIUS) ? Math.max(1, nbt.getInteger(NBT_RADIUS)) : radius;
        floorY = nbt.hasKey(NBT_FLOOR) ? nbt.getInteger(NBT_FLOOR) : -1;
        NBTTagCompound c = (NBTTagCompound) nbt.copy();
        fixed(c);
        super.readFromNBT(c);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger(NBT_ID, settlementId);
        nbt.setInteger(NBT_RADIUS, radius);
        nbt.setInteger(NBT_FLOOR, floorY);
    }
}
