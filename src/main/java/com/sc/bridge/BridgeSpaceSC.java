package com.sc.bridge;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/**
 * «Проверить место» (docs/plan-ground-bridge.md §7а, §7б): is there room for a vortex end at a point - a free
 * w (wide) x w (high) x 2 (deep) volume (3 Ground, 5 Space), no lava, no water, 1 <= y, the top inside the world.
 * A floor is not needed (§7б): the end may hang in the air - onGround / inAir say which, voidBelow that nothing
 * at all is under it down to y = 0 (a warning); one comes out of an air end with a soft landing. «Y авто» finds
 * the topmost such place standing on a floor; the nearest free place within a radius (at any height). Pure over
 * Cells, so the self-test runs it on a mock world; Of(World) reads a real one.
 *
 * The end's plane: across u = -(w-1)/2..(w-1)/2 (along X for axis 0, along Z for axis 1), up v = 0..w-1 from
 * the floor (y), and depth d = 0 (the vortex) and 1 (where one steps out) along the normal.
 */
public final class BridgeSpaceSC {

    private BridgeSpaceSC() {
    }

    public static final int AIR = 0, PASS = 1, SOLID = 2, WATER = 3, LAVA = 4, OTHER = 5;
    /** М-6: a free cell inside another bridge's ring (or right in front of / behind it) - taken for an end. */
    public static final int RING = 6;
    /** How far under the surface «Y авто» looks; the explicit Y's search window (± blocks) for the nearest place. */
    public static final int AUTO_DEPTH = 256, NEAR_AUTO_DEPTH = 16, NEAR_DY = 6;
    /** The nearest free place: radius without / with the Navigation Computer. */
    public static final int NEAR_RADIUS = 16, NEAR_RADIUS_NAV = 32;
    public static final int DEPTH = 2;

    public interface Cells {
        int cell(int x, int y, int z);

        /** The highest non-air block's y in that column (or the world's top). */
        int top(int x, int z);

        int height();
    }

    public static final class Result {
        public boolean free;
        /** Why not (lang key) and its %s arguments. */
        public String reason = "";
        public String[] args = new String[0];
        public int x, y, z;
        public boolean hasNearest;
        public int nx, ny, nz, nDist;
        /** Free and standing on a floor (the step-out cell of the middle column has a solid block under it) / hanging in the air. */
        public boolean onGround, inAir;
        /** In the air with nothing at all under the step-out cell down to y = 0 (the End's void): allowed, with a warning. */
        public boolean voidBelow;
    }

    /** Nearest-place search: each block of height difference counts this much extra (the same Y is preferred). */
    public static final double Y_PENALTY = 1.0;

    private static int[] pos(int x, int y, int z, int axis, int u, int v, int d) {
        return axis == 0 ? new int[]{x + u, y + v, z + d} : new int[]{x + d, y + v, z + u};
    }

    /** The check at exactly (x, y, z): y is the lowest cell of the vortex (the floor is y - 1). */
    public static Result check(Cells c, int x, int y, int z, int w, int axis) {
        Result r = new Result();
        r.x = x;
        r.y = y;
        r.z = z;
        int h = (w - 1) / 2;
        if (y < 1) {
            return no(r, "sc.bridge.place.low");
        }
        if (y + w > c.height()) {
            return no(r, "sc.bridge.place.high");
        }
        boolean lava = false, water = false, ring = false;
        int blocked = 0, rows = w;
        boolean centreBlocked = false;
        for (int v = 0; v < w; v++) {
            boolean rowBlocked = false;
            for (int u = -h; u <= h; u++) {
                for (int d = 0; d < DEPTH; d++) {
                    int[] p = pos(x, y, z, axis, u, v, d);
                    int k = c.cell(p[0], p[1], p[2]);
                    if (k == LAVA) {
                        lava = true;
                    } else if (k == WATER) {
                        water = true;
                    } else if (k == RING) {
                        ring = true;
                        blocked++;
                    } else if (k != AIR && k != PASS) {
                        blocked++;
                        rowBlocked = true;
                        if (u == 0 && v == 0 && d == 0) {
                            centreBlocked = true;
                        }
                    }
                }
            }
            if (rowBlocked && rows == w) {
                rows = v;
            }
        }
        // directly under the volume: lava there is refused (one would step out into it); a floor is optional (§7б)
        boolean floorLava = false;
        for (int u = -h; u <= h; u++) {
            for (int d = 0; d < DEPTH; d++) {
                int[] p = pos(x, y - 1, z, axis, u, 0, d);
                if (c.cell(p[0], p[1], p[2]) == LAVA) {
                    floorLava = true;
                }
            }
        }
        if (lava || floorLava) {
            return no(r, lava ? "sc.bridge.place.lava" : "sc.bridge.place.overlava");
        }
        if (ring) {
            return no(r, "sc.bridge.place.ring");
        }
        if (centreBlocked) {
            return no(r, "sc.bridge.place.inside");
        }
        if (water) {
            return no(r, "sc.bridge.place.water");
        }
        if (blocked > 0) {
            r.args = new String[]{String.valueOf(w), String.valueOf(Math.max(1, rows))};
            return no(r, "sc.bridge.place.small");
        }
        r.free = true;
        int[] out = pos(x, y - 1, z, axis, 0, 0, 1);              // under the cell one steps out to
        r.onGround = c.cell(out[0], out[1], out[2]) == SOLID;
        r.inAir = !r.onGround;
        r.voidBelow = r.inAir && nothingBelow(c, out[0], out[1], out[2]);
        return r;
    }

    /** Nothing but air in this column from y down to 0. */
    public static boolean nothingBelow(Cells c, int x, int y, int z) {
        for (int yy = y; yy >= 0; yy--) {
            if (c.cell(x, yy, z) != AIR) {
                return false;
            }
        }
        return true;
    }

    private static Result no(Result r, String key) {
        r.free = false;
        r.reason = key;
        return r;
    }

    /** «Y авто»: the topmost free place standing on a floor in this column (within depth blocks under the surface), or -1. */
    public static int autoY(Cells c, int x, int z, int w, int axis, int depth) {
        int top = Math.min(c.height() - w, c.top(x, z) + 1);
        for (int y = top; y >= Math.max(1, top - depth); y--) {
            Result r = check(c, x, y, z, w, axis);
            if (r.free && r.onGround) {
                return y;
            }
        }
        return -1;
    }

    /**
     * The check with «Y авто» (y = Integer.MIN_VALUE) or an exact y, and when it isn't free the nearest free
     * place within `radius` blocks (by straight distance; with an exact y at any height within NEAR_DY, a place at
     * another height counting Y_PENALTY a block of the difference farther - the same Y is preferred).
     */
    public static Result probe(Cells c, int x, int y, int z, int w, int axis, int radius) {
        boolean auto = y == Integer.MIN_VALUE;
        Result r;
        if (auto) {
            int ay = autoY(c, x, z, w, axis, AUTO_DEPTH);
            if (ay >= 0) {
                r = check(c, x, ay, z, w, axis);
            } else {
                r = check(c, x, Math.min(c.height() - w, c.top(x, z) + 1), z, w, axis);
                r.free = false;                           // free in the air maybe, but «авто» wants a surface
                r.onGround = r.inAir = r.voidBelow = false;
                if (r.reason.length() == 0) {
                    r.reason = "sc.bridge.place.nofloor";
                }
            }
        } else {
            r = check(c, x, y, z, w, axis);
        }
        if (r.free) {
            return r;
        }
        int bx = 0, by = 0, bz = 0;
        double best = Double.MAX_VALUE, bestDist = 0;
        int baseY = auto ? r.y : y;
        for (int ring = 0; ring <= radius; ring++) {
            if (ring > best) {
                break;                                    // every further ring is farther than the best already found
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int px = x + dx, pz = z + dz;
                    if (auto) {
                        int ay = autoY(c, px, pz, w, axis, NEAR_AUTO_DEPTH);
                        if (ay >= 0) {
                            double dist = Math.sqrt(dx * dx + dz * dz + (double) (ay - baseY) * (ay - baseY));
                            if (dist < best && (ring > 0 || ay != r.y)) {
                                best = dist;
                                bestDist = dist;
                                bx = px;
                                by = ay;
                                bz = pz;
                            }
                        }
                    } else {
                        for (int k = 0; k <= 2 * NEAR_DY; k++) {
                            int dy = (k + 1) / 2 * (k % 2 == 0 ? 1 : -1);
                            if (ring == 0 && dy == 0) {
                                continue;
                            }
                            int py = y + dy;
                            if (py < 1 || py + w > c.height()) {
                                continue;
                            }
                            double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);
                            double score = dist + Math.abs(dy) * Y_PENALTY;
                            if (score >= best) {
                                continue;
                            }
                            if (check(c, px, py, pz, w, axis).free) {
                                best = score;
                                bestDist = dist;
                                bx = px;
                                by = py;
                                bz = pz;
                            }
                        }
                    }
                }
            }
        }
        if (best < Double.MAX_VALUE) {
            r.hasNearest = true;
            r.nx = bx;
            r.ny = by;
            r.nz = bz;
            r.nDist = (int) Math.round(bestDist);
        }
        return r;
    }

    /**
     * С3 turbulence: the nearest place a body can stand to (x, y, z) - a solid floor and two free cells - within
     * `radius` blocks across and `dy` up or down; {x, y, z} or null.
     */
    public static int[] standSpot(Cells c, int x, int y, int z, int radius, int dy) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int k = 0; k <= 2 * dy; k++) {
                        int py = y + (k + 1) / 2 * (k % 2 == 0 ? 1 : -1);
                        if (py < 1 || py + 2 > c.height()) {
                            continue;
                        }
                        int f = c.cell(x + dx, py - 1, z + dz), a = c.cell(x + dx, py, z + dz), b = c.cell(x + dx, py + 1, z + dz);
                        if (f == SOLID && (a == AIR || a == PASS) && (b == AIR || b == PASS)) {
                            return new int[]{x + dx, py, z + dz};
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * С3 turbulence (§7б): the nearest place a body fits at (x, y, z) - two free cells, no lava under them; a floor
     * is not needed (one comes out of the air with a soft landing) - within `radius` across and `dy` up or down; or null.
     */
    public static int[] freeSpot(Cells c, int x, int y, int z, int radius, int dy) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int k = 0; k <= 2 * dy; k++) {
                        int py = y + (k + 1) / 2 * (k % 2 == 0 ? 1 : -1);
                        if (py < 1 || py + 2 > c.height()) {
                            continue;
                        }
                        int a = c.cell(x + dx, py, z + dz), b = c.cell(x + dx, py + 1, z + dz), f = c.cell(x + dx, py - 1, z + dz);
                        if ((a == AIR || a == PASS) && (b == AIR || b == PASS) && f != LAVA) {
                            return new int[]{x + dx, py, z + dz};
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ other bridges' rings (М-6)

    /** Where other bridges' rings stand: their room is taken for an end. */
    public interface Zones {
        boolean ring(int x, int y, int z);
    }

    /** The cells with the free ones inside `zones` seen as RING (taken). */
    public static Cells guarded(final Cells c, final Zones zones) {
        if (zones == null) {
            return c;
        }
        return new Cells() {
            @Override
            public int cell(int x, int y, int z) {
                int k = c.cell(x, y, z);
                return (k == AIR || k == PASS) && zones.ring(x, y, z) ? RING : k;
            }

            @Override
            public int top(int x, int z) {
                return c.top(x, z);
            }

            @Override
            public int height() {
                return c.height();
            }
        };
    }

    // ------------------------------------------------------------------ a real world

    /** The cells of a real world (loads the chunks it reads - server side, on a button press). */
    public static Cells of(final World w) {
        return new Cells() {
            @Override
            public int cell(int x, int y, int z) {
                if (y < 0 || y >= w.getHeight()) {
                    return y < 0 ? OTHER : AIR;
                }
                Block b = w.getBlock(x, y, z);
                if (b.isAir(w, x, y, z)) {
                    return AIR;
                }
                Material m = b.getMaterial();
                if (m == Material.lava) {
                    return LAVA;
                }
                if (m.isLiquid()) {
                    return WATER;
                }
                if (b == com.sc.init.ModBlocks.bridgeVortex) {
                    return OTHER;
                }
                if (m.blocksMovement()) {
                    return SOLID;
                }
                if (b.hasTileEntity(w.getBlockMetadata(x, y, z))) {
                    return OTHER;                          // a flower pot, a skull...: the vortex never breaks those
                }
                return PASS;                               // grass, flowers, snow, torches: one walks through them, the vortex breaks them
            }

            @Override
            public int top(int x, int z) {
                return w.getHeightValue(x, z);
            }

            @Override
            public int height() {
                return w.getHeight();
            }
        };
    }
}
