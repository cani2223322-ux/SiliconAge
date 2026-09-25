package com.sc.conduit;

/**
 * The three conduits a bundle can hold, one of each (Ender IO style). Each always runs in its
 * own slot of the block - an offset along the block diagonal - so a cable in one bundle lines up
 * with the cable in the next whatever else either bundle carries. Cables take the centre slot,
 * being the most common thing to lay on its own.
 */
public enum ConduitKind {

    CABLE(0F),
    PIPE(0.1875F),
    TUBE(-0.1875F);

    /** Half the thickness of every conduit: 3/16 of a block wide, like Ender IO's. */
    public static final float HALF_WIDTH = 0.09375F;

    /** Offset of this conduit's centre from the block centre, the same on all three axes. */
    public final float offset;

    ConduitKind(float offset) {
        this.offset = offset;
    }

    /** Low / high edge of this conduit's cross-section on each axis. */
    public float lo() {
        return 0.5F + offset - HALF_WIDTH;
    }

    public float hi() {
        return 0.5F + offset + HALF_WIDTH;
    }
}
