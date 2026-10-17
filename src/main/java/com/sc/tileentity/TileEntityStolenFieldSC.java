package com.sc.tileentity;

import com.sc.energy.FieldMode;
import com.sc.mob.FactionSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.3, rules ПЗ-2..ПЗ-5; proposal §9 «Лагерь Отступников под краденым полем»): «Краденый
 * генератор поля» - a Field Generator's master owned by «[Отступники]», in the middle of a Renegades' camp. Unlike a settlement's
 * core it is powered for real - by the camp's Stolen Solar Panels outside it (TileEntityStolenSolarSC feeds it: feed()); at
 * night they give half, the upkeep is more than that, so towards midnight the field goes down (the base class's
 * «sc.field.warn.down») - or the players break the panels.
 * <ul>
 * <li>the fixed settings (fixed(), over any NBT, every load): DOME of `radius` from the generator, F_PRIVATE + F_EMP_SCREEN only
 * (F_SHOW - the dome is seen; no pushing, no F_DAMAGE, no F_NO_SPAWN), no redstone control, always on, the crimson shell, the
 * owner OWNER - no player can be him: strangers break and place nothing inside, open no chest, a ПЭ-1 shot from outside dies at
 * the border (paid from the buffer, EmpMathSC.screenCost - firing at it drains it);</li>
 * <li>the faction exception (ПЗ-3): targets() - nobody is pushed (the Renegades, and anything else, stay); deflects() - a shot of
 * the Renegades' own side is never turned back (FactionSC), every other non-player projectile as in any field;</li>
 * <li>no screen, no item charging (the camp's power isn't the players'), no upgrades or battery.</li>
 * </ul>
 * Package C writes the rest: campTick (every CAMP_TICKS - the camp's record in RareRegistrySC, the Warlord and the residents back
 * after 3 days, the safe relocked; RuinWorldSC does it when the generator is gone). The invariants above are the coordinator's;
 * C may add, not loosen them.
 */
public class TileEntityStolenFieldSC extends TileEntityFieldGeneratorSC {

    /** ПЗ-2: the owner (a name no player can have: Minecraft names are [A-Za-z0-9_]). */
    public static final String OWNER = "[Отступники]";
    /** ПЗ-2: the switches. */
    public static final int FLAGS = F_PRIVATE | F_EMP_SCREEN | F_SHOW;
    /** The crimson of the Renegades' shell. */
    public static final int SHELL_RGB = 0xC02838;
    /** ПЗ-2: the dome's radius when a camp sets none (CampLayoutSC.FIELD_RADIUS). TODO(balance) */
    public static final int DEFAULT_RADIUS = 10;
    /** Package C's camp tick runs this often (ticks). */
    public static final int CAMP_TICKS = 100;
    private static final String NBT_RADIUS = "scRadius", NBT_CAMP = "scCamp";

    private int radius = DEFAULT_RADIUS;
    /** The camp's registry key (RareRegistrySC.campKey), "" for a generator set by hand. */
    private String campKey = "";
    private boolean configured;

    public TileEntityStolenFieldSC() {
    }

    /** Package C, right after placing the block in its camp piece: the dome's radius and the camp it holds; the fixed settings at once. */
    public void setup(int domeRadius, String camp) {
        configured = true;
        radius = Math.max(2, domeRadius);
        campKey = camp == null ? "" : camp;
        NBTTagCompound n = new NBTTagCompound();
        super.writeToNBT(n);
        fixed(n);
        super.readFromNBT(n);
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public int radius() {
        return radius;
    }

    public String campKey() {
        return campKey;
    }

    /** The fixed settings written over `n` (a whole NBT of this tile, its coordinates in it); the stored energy is kept. */
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
        n.setInteger("ChargeMode", CHARGE_ARMOR_FIRST);
        n.setInteger("Redstone", REDSTONE_ALWAYS);
        n.setInteger("Filter", FILTER_HOSTILE);
        n.setInteger("Anchor", ANCHOR_NODES);
        n.removeTag("AnchorPoint");
        n.setInteger("OffX", 0);
        n.setInteger("OffY", 0);
        n.setInteger("OffZ", 0);
        n.setInteger("Outline", OUTLINE_NEVER);
        n.setInteger("Anim", ANIM_PULSE);
        n.setIntArray("RGB", new int[]{SHELL_RGB, SHELL_RGB, SHELL_RGB});
        n.setString("Owner", OWNER);
        n.setTag("Access", new NBTTagList());
        n.setBoolean("PowerOff", false);
        n.removeTag("Upgrades");
        n.removeTag("Battery");
    }

    // ------------------------------------------------------------------ ПЗ-3: the faction exception

    /** Nobody is pushed out (the Renegades live under it; the other creatures aren't its business either). */
    @Override
    public boolean targets(Entity e) {
        return false;
    }

    /** Ф4 (ПФ-3): its EMP screen covers whoever is under it from a storm's pulse (paid from its own buffer). */
    @Override
    public boolean shieldsFromStorm(Entity e) {
        return true;
    }

    /** The Renegades' own shots pass; every other non-player projectile is turned back as by any field. */
    @Override
    protected boolean deflects(Entity e) {
        return !FactionSC.is(projectileShooter(e), FactionSC.RENEGADES) && super.deflects(e);
    }

    // ------------------------------------------------------------------ the power: the camp's panels feed it, it serves nobody

    /** TileEntityStolenSolarSC: `eu` into the buffer (no more than room). @return taken */
    public int feed(int eu) {
        int room = getMaxEnergyStored() - getEnergyStored();
        int n = Math.max(0, Math.min(eu, room));
        if (n > 0) {
            addEnergy(n);
        }
        return n;
    }

    @Override
    public boolean canItemCharge(EntityPlayer p) {
        return false;
    }

    @Override
    public int extractForItemCharging(int max) {
        return 0;
    }

    /** No screen, no slots. */
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
            setup(radius, campKey);                         // set by hand: a stolen field without a camp
        }
        super.updateEntity();
        if (worldObj != null && !worldObj.isRemote && !campKey.isEmpty()
                && (worldObj.getTotalWorldTime() + (xCoord * 31 + zCoord * 17 & 63)) % CAMP_TICKS == 0) {
            campTick();
        }
    }

    /**
     * Package C: the camp's slow tick (its record, the Warlord's and the residents' return, the safe). Server, the Substrate.
     * RuinWorldSC.campTick: the generator's place into its camp's record (a generator set again by the camp's return is found
     * there), then the camp's own return check (ПЗ-6) - the same as the world tick's for a camp whose generator is gone.
     */
    protected void campTick() {
        com.sc.ruin.RuinWorldSC.campTick(this);
    }

    // ------------------------------------------------------------------ NBT: the fixed settings over every load

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        configured = true;
        radius = nbt.hasKey(NBT_RADIUS) ? Math.max(2, nbt.getInteger(NBT_RADIUS)) : radius;
        campKey = nbt.getString(NBT_CAMP);
        NBTTagCompound c = (NBTTagCompound) nbt.copy();
        fixed(c);
        super.readFromNBT(c);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger(NBT_RADIUS, radius);
        nbt.setString(NBT_CAMP, campKey);
    }
}
