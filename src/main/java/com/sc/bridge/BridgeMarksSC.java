package com.sc.bridge;

import java.util.ArrayList;
import java.util.List;

import com.sc.block.BlockBridgeSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * Where the Receiver Beacons and Interdimensional Anchors stand, in every dimension (the server's global
 * map storage, "SiliconAgeBridgeMarks"): the bridge asks it without loading the chunks they are in. The
 * blocks add themselves when placed and leave when broken; a mark whose block is gone (a world edited
 * outside the game) is dropped the next time its chunk is looked at.
 */
public class BridgeMarksSC extends WorldSavedData {

    public static final String NAME = "SiliconAgeBridgeMarks";
    public static final int BEACON = 0, ANCHOR = 1;

    /** [kind, dim, x, y, z]. */
    private final List<int[]> marks = new ArrayList<int[]>();

    public BridgeMarksSC(String name) {
        super(name);
    }

    /** The server's marks (any world of the server reaches the same global storage). */
    public static BridgeMarksSC get(World w) {
        if (w == null || w.mapStorage == null) {
            return null;
        }
        BridgeMarksSC m = (BridgeMarksSC) w.mapStorage.loadData(BridgeMarksSC.class, NAME);
        if (m == null) {
            m = new BridgeMarksSC(NAME);
            w.mapStorage.setData(NAME, m);
        }
        return m;
    }

    public void add(int kind, int dim, int x, int y, int z) {
        remove(dim, x, y, z);
        marks.add(new int[]{kind, dim, x, y, z});
        markDirty();
    }

    public void remove(int dim, int x, int y, int z) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            int[] m = marks.get(i);
            if (m[1] == dim && m[2] == x && m[3] == y && m[4] == z) {
                marks.remove(i);
                markDirty();
            }
        }
    }

    /** A mark of this kind within `radius` blocks (straight) of x y z in `dim`. */
    public boolean near(int kind, int dim, int x, int y, int z, int radius) {
        long r2 = (long) radius * radius;
        for (int i = marks.size() - 1; i >= 0; i--) {
            int[] m = marks.get(i);
            if (m[0] == kind && m[1] == dim) {
                long dx = m[2] - x, dy = y == Integer.MIN_VALUE ? 0 : m[3] - y, dz = m[4] - z;     // «Y авто»: across only
                if (dx * dx + dy * dy + dz * dz <= r2 && stillThere(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Any mark of this kind in `dim`. */
    public boolean any(int kind, int dim) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            int[] m = marks.get(i);
            if (m[0] == kind && m[1] == dim && stillThere(i)) {
                return true;
            }
        }
        return false;
    }

    /** МС-10: false (and the mark dropped) when its chunk is loaded and the block is gone; an unloaded chunk is trusted. */
    private boolean stillThere(int i) {
        int[] m = marks.get(i);
        World w = DimensionManager.getWorld(m[1]);
        if (w == null || !w.getChunkProvider().chunkExists(m[2] >> 4, m[4] >> 4)) {
            return true;
        }
        int meta = m[0] == BEACON ? BlockBridgeSC.BEACON : BlockBridgeSC.ANCHOR;
        if (w.getBlock(m[2], m[3], m[4]) instanceof BlockBridgeSC && w.getBlockMetadata(m[2], m[3], m[4]) == meta) {
            return true;
        }
        marks.remove(i);
        markDirty();
        return false;
    }

    public int count() {
        return marks.size();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        marks.clear();
        NBTTagList l = nbt.getTagList("Marks", 11);
        for (int i = 0; i < l.tagCount(); i++) {
            int[] m = l.func_150306_c(i);
            if (m.length == 5) {
                marks.add(m);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList l = new NBTTagList();
        for (int[] m : marks) {
            l.appendTag(new net.minecraft.nbt.NBTTagIntArray(m.clone()));
        }
        nbt.setTag("Marks", l);
    }
}
