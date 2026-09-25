package com.sc.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sc.energy.FieldMode;

import net.minecraft.util.AxisAlignedBB;

/**
 * The protected volume of a Field Generator cluster, per mode (§16's Union / Box / Prism - the
 * design doc names the three shapes without defining them; these are this mod's reading), for
 * the cluster's configurable range R (1..MAX_RANGE blocks, set on the master's screen):
 *
 *  - UNION: a sphere of radius R around every node - bubbles that merge where they overlap.
 *  - BOX: the axis-aligned box around all nodes, grown by R on every side.
 *  - PRISM: the convex outline of the nodes seen from above, grown by R, as a wall running the
 *    full height of the world - nothing gets in from above either (ghasts, blazes, arrows).
 *  - DOME: the upper half of each node's sphere, from the node's floor up (cheaper - nothing below).
 *  - CYLINDER: an upright cylinder round each node, radius R, R up and R down.
 *
 * Coordinates are block-centre based (node x + 0.5). Pure geometry, shared by the tile entity
 * (mobs, projectiles, explosions) and the client renderer.
 */
public final class FieldShapeSC {

    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 256;
    /** TODO(design doc §16 gives no dimensions): what a freshly placed generator starts with. */
    public static final int DEFAULT_RANGE = 8;
    public static final double WORLD_BOTTOM = 0, WORLD_TOP = 256;

    private FieldShapeSC() {
    }

    public static int clampRange(int range) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
    }

    /** Axis-aligned bounds enclosing the whole shape (for entity queries and render culling). */
    public static AxisAlignedBB bounds(FieldMode mode, List<int[]> nodes, int range) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int[] n : nodes) {
            minX = Math.min(minX, n[0]); minY = Math.min(minY, n[1]); minZ = Math.min(minZ, n[2]);
            maxX = Math.max(maxX, n[0]); maxY = Math.max(maxY, n[1]); maxZ = Math.max(maxZ, n[2]);
        }
        double r = range;
        if (mode == FieldMode.DOME) {                              // half spheres: nothing below the lowest node
            return AxisAlignedBB.getBoundingBox(minX + 0.5 - r, minY, minZ + 0.5 - r,
                    maxX + 0.5 + r, maxY + 0.5 + r, maxZ + 0.5 + r);
        }
        if (mode == FieldMode.PRISM) {
            return AxisAlignedBB.getBoundingBox(minX + 0.5 - r, WORLD_BOTTOM, minZ + 0.5 - r,
                    maxX + 0.5 + r, WORLD_TOP, maxZ + 0.5 + r);
        }
        return AxisAlignedBB.getBoundingBox(minX + 0.5 - r, minY + 0.5 - r, minZ + 0.5 - r,
                maxX + 0.5 + r, maxY + 0.5 + r, maxZ + 0.5 + r);
    }

    public static boolean contains(FieldMode mode, List<int[]> nodes, int range, double x, double y, double z) {
        if (nodes.isEmpty()) {
            return false;
        }
        switch (mode) {
            case UNION:
                for (int[] n : nodes) {
                    double dx = x - (n[0] + 0.5), dy = y - (n[1] + 0.5), dz = z - (n[2] + 0.5);
                    if (dx * dx + dy * dy + dz * dz <= (double) range * range) {
                        return true;
                    }
                }
                return false;
            case DOME:                                            // the upper half of each bubble, from the node's floor up
                for (int[] n : nodes) {
                    double dx = x - (n[0] + 0.5), dy = y - n[1], dz = z - (n[2] + 0.5);
                    if (dy >= 0 && dx * dx + dy * dy + dz * dz <= (double) range * range) {
                        return true;
                    }
                }
                return false;
            case CYLINDER:                                        // upright cylinders, radius and half-height = range
                for (int[] n : nodes) {
                    double dx = x - (n[0] + 0.5), dy = y - (n[1] + 0.5), dz = z - (n[2] + 0.5);
                    if (Math.abs(dy) <= range && dx * dx + dz * dz <= (double) range * range) {
                        return true;
                    }
                }
                return false;
            case PRISM: {
                AxisAlignedBB b = bounds(mode, nodes, range);
                if (y < b.minY || y > b.maxY) {
                    return false;
                }
                List<double[]> hull = hull(nodes);
                if (hull.size() < 3) {
                    // 1-2 nodes or a straight line: no outline to follow - it's the box, same as drawn.
                    return x >= b.minX && x <= b.maxX && b.minZ <= z && z <= b.maxZ;
                }
                return insidePolygon(hull, x, z) || distanceToOutline(hull, x, z) <= range;
            }
            default: {
                AxisAlignedBB b = bounds(mode, nodes, range);
                return x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ;
            }
        }
    }

    /** Convex hull of the nodes' block centres in the XZ plane (Andrew's monotone chain), counter-clockwise. */
    public static List<double[]> hull(List<int[]> nodes) {
        List<double[]> pts = new ArrayList<double[]>();
        for (int[] n : nodes) {
            double[] p = {n[0] + 0.5, n[2] + 0.5};
            boolean dup = false;
            for (double[] q : pts) {
                dup |= q[0] == p[0] && q[1] == p[1];
            }
            if (!dup) {
                pts.add(p);
            }
        }
        if (pts.size() < 3) {
            return pts;
        }
        Collections.sort(pts, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]);
            }
        });
        double[][] h = new double[pts.size() * 2][];
        int k = 0;
        for (double[] p : pts) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], p) <= 0) {
                k--;
            }
            h[k++] = p;
        }
        for (int i = pts.size() - 2, t = k + 1; i >= 0; i--) {
            double[] p = pts.get(i);
            while (k >= t && cross(h[k - 2], h[k - 1], p) <= 0) {
                k--;
            }
            h[k++] = p;
        }
        List<double[]> out = new ArrayList<double[]>();
        for (int i = 0; i < k - 1; i++) {
            out.add(h[i]);
        }
        return out;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private static boolean insidePolygon(List<double[]> poly, double x, double z) {
        if (poly.size() < 3) {
            return false;
        }
        for (int i = 0; i < poly.size(); i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
            if (cross(a, b, new double[]{x, z}) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Distance from (x,z) to the nearest point of the polygon's outline. */
    private static double distanceToOutline(List<double[]> poly, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < poly.size(); i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
            double vx = b[0] - a[0], vz = b[1] - a[1];
            double len2 = vx * vx + vz * vz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * vx + (z - a[1]) * vz) / len2));
            best = Math.min(best, Math.hypot(x - (a[0] + t * vx), z - (a[1] + t * vz)));
        }
        return best;
    }
}
