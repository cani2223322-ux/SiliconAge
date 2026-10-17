package com.sc.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.3, rule ПЗ-4): a stolen solar panel - once a second (by its position's phase),
 * under the open sky, EU_DAY a tick by day and half at night (as the mod's Silicon panel), nothing in an EMP storm
 * (EmpStormSC.blocksSolar), straight into its camp's Stolen Field Generator within FEED_RANGE (TileEntityStolenFieldSC.feed - the
 * Renegades' wiring: no cable, nothing for a player to tap). The camp's numbers are tuned so its field holds by day and goes down
 * towards midnight (the upkeep of a dome of CampLayoutSC.FIELD_RADIUS with its two flags is more than PANELS x EU_DAY / 2).
 * Fixed (the coordinator's): the class, the registry id "SiliconAge.stolenSolar"; the body and the numbers C's (TODO(balance)).
 * <p>
 * The numbers: the camp's dome (one node, radius 10, DOME, F_PRIVATE) costs 24 + 16 = 40 EU a tick; four panels give 64 by day
 * (the buffer, 51 200, fills in under two minutes) and 32 at night - 8 short a tick, so a full buffer lasts 6 400 ticks: the
 * field drops a little past midnight, sooner if the players shoot at it (its ПЭ-1 screen pays from the buffer) or break a panel.
 * The output follows the server's fieldUpkeep (ConfigSC) as the upkeep does, so a cheaper or dearer field keeps the same night.
 * Down at night, a generator isn't fed (the stolen converter can't restart a dome on the night's trickle): it stays down until
 * the sun is up - else it would flick up and down all night on the half output. The generator: the one its camp bound it to
 * (bind - NBT scField), else the nearest Stolen Field Generator within FEED_RANGE among the loaded tiles (looked for again every
 * SEARCH_TICKS while it has none).
 */
public class TileEntityStolenSolarSC extends TileEntity {

    /** ПЗ-4: a panel's EU a tick by day (half at night), how far its field may be, the panels a camp has. TODO(balance) */
    public static final int EU_DAY = 16, FEED_RANGE = 24, PANELS = 4;
    /** How often an unbound panel looks for a generator (ticks). */
    public static final int SEARCH_TICKS = 200;
    private static final String NBT_FIELD = "scField";

    /** Its generator's place (bound - by its camp, or found). */
    private int fx, fy, fz;
    private boolean bound;
    private long nextSearch;

    @Override
    public boolean canUpdate() {
        return true;
    }

    /** Its camp's generator at (x, y, z) (MapGenCampSC / RuinWorldSC, right after the block is set). */
    public void bind(int x, int y, int z) {
        fx = x;
        fy = y;
        fz = z;
        bound = true;
        markDirty();
    }

    /**
     * Pure: EU a tick of one panel - by day, under the sky, no storm; `upkeepScale` the server's fieldUpkeep. By day rounded (at
     * least 1), at night rounded down - so the night stays short of the upkeep at the smallest fieldUpkeep too.
     */
    public static int output(boolean sky, boolean storm, boolean day, float upkeepScale) {
        if (!sky || storm) {
            return 0;
        }
        return day ? Math.max(1, Math.round(EU_DAY * upkeepScale)) : (int) Math.floor(EU_DAY * 0.5F * upkeepScale);
    }

    /** Pure: the panel feeds its generator now - by day always, at night only while the dome stands (the class comment). */
    public static boolean feeds(boolean day, boolean fieldUp) {
        return day || fieldUp;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || (worldObj.getTotalWorldTime() + (xCoord * 7 + zCoord * 13 & 15)) % 20 != 0) {
            return;
        }
        boolean day = worldObj.isDaytime();
        int eu = output(worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord), com.sc.hazard.EmpStormSC.blocksSolar(worldObj, xCoord, yCoord, zCoord),
                day, com.sc.util.ConfigSC.fieldUpkeep);
        if (eu <= 0) {
            return;
        }
        TileEntityStolenFieldSC f = field();
        if (f != null && feeds(day, f.isActive())) {
            f.feed(eu * 20);
        }
    }

    /** Its generator if loaded and in reach (the bound one, else the nearest found - at most every SEARCH_TICKS). */
    private TileEntityStolenFieldSC field() {
        if (bound && worldObj.blockExists(fx, fy, fz)) {
            TileEntity te = worldObj.getTileEntity(fx, fy, fz);
            if (te instanceof TileEntityStolenFieldSC && !te.isInvalid() && inReach(fx, fy, fz)) {
                return (TileEntityStolenFieldSC) te;
            }
        }
        long now = worldObj.getTotalWorldTime();
        if (now < nextSearch) {
            return null;
        }
        nextSearch = now + SEARCH_TICKS;
        TileEntityStolenFieldSC best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : worldObj.loadedTileEntityList) {
            if (o instanceof TileEntityStolenFieldSC && !((TileEntity) o).isInvalid()) {
                TileEntity te = (TileEntity) o;
                double d = te.getDistanceFrom(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D);
                if (d < bestD && inReach(te.xCoord, te.yCoord, te.zCoord)) {
                    bestD = d;
                    best = (TileEntityStolenFieldSC) te;
                }
            }
        }
        if (best != null) {
            bind(best.xCoord, best.yCoord, best.zCoord);
        }
        return best;
    }

    private boolean inReach(int x, int y, int z) {
        double dx = x - xCoord, dy = y - yCoord, dz = z - zCoord;
        return dx * dx + dy * dy + dz * dz <= FEED_RANGE * FEED_RANGE;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        int[] a = nbt.getIntArray(NBT_FIELD);
        bound = a.length == 3;
        if (bound) {
            fx = a[0];
            fy = a[1];
            fz = a[2];
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (bound) {
            nbt.setIntArray(NBT_FIELD, new int[]{fx, fy, fz});
        }
    }
}
