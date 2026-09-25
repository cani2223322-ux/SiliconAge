package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.block.BlockConduitSC;
import com.sc.block.BlockItemTubeSC;
import com.sc.block.BlockPipeSC;
import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.tileentity.TileEntityConduitBundleSC;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Draws conduit bundles the Ender IO way: every conduit is a thin tube whose texture runs along
 * its length (the coloured stripe follows the conduit round corners), a core where its arms meet,
 * a dark connector plate where it plugs into a machine / tank / chest (orange-rimmed when it
 * extracts, blue when it inserts, green for a pipe's or tube's in / out), and a pipe shows the fluid inside through its glass.
 * Also draws the cable / pipe / tube items (a short straight run with its core).
 */
public class ConduitRenderer implements ISimpleBlockRenderingHandler {

    private static final float CONNECTOR_DEPTH = 0.0625F;
    private static final float CONNECTOR_MARGIN = 0.0625F;
    /** Fluid sits this far inside the pipe's glass. */
    private static final float FLUID_INSET = 0.015625F;

    private final int renderId;

    public ConduitRenderer(int renderId) {
        this.renderId = renderId;
    }

    // ------------------------------------------------------------------ world

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        Tessellator t = Tessellator.instance;
        int light = block.getMixedBrightnessForBlock(world, x, y, z);
        TileEntityConduitBundleSC te = BlockConduitSC.bundle(world, x, y, z);
        if (te == null) {
            // a pre-bundle single conduit that hasn't converted itself yet: just its core
            ConduitKind kind = kindOf(block);
            IIcon[] icons = icons(kind, world.getBlockMetadata(x, y, z));
            box(t, x, y, z, BlockConduitSC.coreBox(kind), icons[1], -1, light, 1, 1, 1);
            return true;
        }
        for (ConduitKind kind : ConduitKind.values()) {
            if (!te.has(kind)) {
                continue;
            }
            IIcon[] icons = icons(kind, te.subtype(kind));
            FluidStack fluid = kind == ConduitKind.PIPE ? te.getFluid() : null;
            IIcon fluidIcon = null;
            float fr = 1, fg = 1, fb = 1;
            if (fluid != null && fluid.amount > 0) {
                Fluid f = fluid.getFluid();
                fluidIcon = f.getStillIcon() != null ? f.getStillIcon() : f.getIcon();
                int c = f.getColor(fluid);
                fr = ((c >> 16) & 255) / 255F;
                fg = ((c >> 8) & 255) / 255F;
                fb = (c & 255) / 255F;
            }
            float[] core = BlockConduitSC.coreBox(kind);
            if (fluidIcon != null) {
                box(t, x, y, z, inset(core), fluidIcon, -1, light, fr, fg, fb);
            }
            box(t, x, y, z, core, icons[1], -1, light, 1, 1, 1);
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (!te.connects(kind, dir)) {
                    continue;
                }
                float[] arm = BlockConduitSC.armBox(kind, dir);
                int axis = axisOf(dir);
                if (te.connectorAt(kind, dir)) {
                    // stop at the connector plate instead of sharing the block face with it
                    if (dir.offsetX + dir.offsetY + dir.offsetZ > 0) {
                        arm[axis + 3] = 1 - CONNECTOR_DEPTH;
                    } else {
                        arm[axis] = CONNECTOR_DEPTH;
                    }
                }
                if (fluidIcon != null) {
                    box(t, x, y, z, inset(arm, axis), fluidIcon, axis, light, fr, fg, fb);
                }
                box(t, x, y, z, arm, icons[0], axis, light, 1, 1, 1);
            }
        }
        // one connector plate per face, over every conduit plugged in there
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            float[] plate = null;
            IIcon icon = BlockConduitSC.connector;
            for (ConduitKind kind : ConduitKind.values()) {
                if (!te.connectorAt(kind, dir)) {
                    continue;
                }
                ConduitMode mode = te.mode(kind, dir);
                boolean out = mode.extracts(kind), in = mode.inserts(kind);
                if (icon == BlockConduitSC.connector && !(kind == ConduitKind.CABLE && out && in)) {
                    // orange rim: takes out of the block, blue: puts into it, green: both (a cable's
                    // default in / out stays plain)
                    icon = out && in ? BlockConduitSC.connectorBoth : out ? BlockConduitSC.connectorExtract : BlockConduitSC.connectorInsert;
                }
                float lo = kind.lo() - CONNECTOR_MARGIN, hi = kind.hi() + CONNECTOR_MARGIN;
                float[] b = plateBox(dir, lo, hi);
                plate = plate == null ? b : union(plate, b);
            }
            if (plate != null) {
                box(t, x, y, z, plate, icon, -1, light, 1, 1, 1);
            }
        }
        return true;
    }

    private static ConduitKind kindOf(Block block) {
        if (block instanceof BlockPipeSC) {
            return ConduitKind.PIPE;
        }
        if (block instanceof BlockItemTubeSC) {
            return ConduitKind.TUBE;
        }
        return ConduitKind.CABLE;
    }

    /** {arm, core} icons of a conduit. */
    private static IIcon[] icons(ConduitKind kind, int subtype) {
        switch (kind) {
            case CABLE: {
                int i = Math.max(0, Math.min(BlockConduitSC.cableArm.length - 1, subtype));
                return new IIcon[]{BlockConduitSC.cableArm[i], BlockConduitSC.cableCore[i]};
            }
            case PIPE: {
                int i = Math.max(0, Math.min(BlockConduitSC.pipeArm.length - 1, subtype));
                return new IIcon[]{BlockConduitSC.pipeArm[i], BlockConduitSC.pipeCore[i]};
            }
            default:
                return new IIcon[]{BlockConduitSC.tubeArm, BlockConduitSC.tubeCore};
        }
    }

    private static int axisOf(ForgeDirection dir) {
        return dir.offsetX != 0 ? 0 : dir.offsetY != 0 ? 1 : 2;
    }

    /** Thin square plate on face `dir`, spanning lo..hi on the other two axes. */
    private static float[] plateBox(ForgeDirection dir, float lo, float hi) {
        float[] b = {lo, lo, lo, hi, hi, hi};
        int axis = axisOf(dir);
        boolean positive = dir.offsetX + dir.offsetY + dir.offsetZ > 0;
        b[axis] = positive ? 1 - CONNECTOR_DEPTH : 0;
        b[axis + 3] = positive ? 1 : CONNECTOR_DEPTH;
        return b;
    }

    private static float[] union(float[] a, float[] b) {
        return new float[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[3], b[3]), Math.max(a[4], b[4]), Math.max(a[5], b[5])};
    }

    private static float[] inset(float[] b) {
        return inset(b, -1);
    }

    /** Shrinks a box, except along `keepAxis` (fluid in an arm reaches the block edge). */
    private static float[] inset(float[] b, int keepAxis) {
        float[] o = b.clone();
        for (int i = 0; i < 3; i++) {
            if (i != keepAxis) {
                o[i] += FLUID_INSET;
                o[i + 3] -= FLUID_INSET;
            }
        }
        return o;
    }

    /**
     * One box. On the faces lying along `axis` (-1: none, a core) the texture's u runs along the
     * axis, one full texture per block, so a stripe continues from arm to arm; v covers the
     * conduit's width. Other faces get the whole texture. Shading as vanilla's block faces.
     */
    private static void box(Tessellator t, double x, double y, double z, float[] b, IIcon icon, int axis, int light,
                            float r, float g, float bl) {
        if (icon == null) {
            return;
        }
        if (light >= 0) {
            t.setBrightness(light);         // world only - items are lit by GL
        }
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        // corners counter-clockwise seen from outside; the two axes each face spans
        face(t, x, y, z, icon, axis, 0.5F, r, g, bl, b, 0, 2,
                new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}});          // DOWN
        face(t, x, y, z, icon, axis, 1.0F, r, g, bl, b, 0, 2,
                new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}});          // UP
        face(t, x, y, z, icon, axis, 0.8F, r, g, bl, b, 0, 1,
                new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}});          // NORTH
        face(t, x, y, z, icon, axis, 0.8F, r, g, bl, b, 0, 1,
                new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}});          // SOUTH
        face(t, x, y, z, icon, axis, 0.6F, r, g, bl, b, 2, 1,
                new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}});          // WEST
        face(t, x, y, z, icon, axis, 0.6F, r, g, bl, b, 2, 1,
                new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}});          // EAST
    }

    /**
     * One quad from local (0..1) corners. `a`/`c` are the axes the face spans: when one of them
     * is the conduit's axis, u follows it (position in the block x 16) and v spans the width;
     * otherwise the whole icon is stretched over the face.
     */
    private static void face(Tessellator t, double x, double y, double z, IIcon icon, int axis, float shade,
                             float r, float g, float bl, float[] b, int a, int c, float[][] corners) {
        t.setColorOpaque_F(shade * r, shade * g, shade * bl);
        int along = axis == a ? a : axis == c ? c : -1;
        for (float[] p : corners) {
            double u, v;
            if (along >= 0) {
                int across = along == a ? c : a;
                u = icon.getInterpolatedU(p[along] * 16);
                v = icon.getInterpolatedV(fraction(p[across], b[across], b[across + 3]) * 16);
            } else {
                u = icon.getInterpolatedU(fraction(p[a], b[a], b[a + 3]) * 16);
                v = icon.getInterpolatedV(16 - fraction(p[c], b[c], b[c + 3]) * 16);
            }
            t.addVertexWithUV(x + p[0], y + p[1], z + p[2], u, v);
        }
    }

    private static double fraction(float value, float lo, float hi) {
        return hi > lo ? (value - lo) / (hi - lo) : 0;
    }

    // ------------------------------------------------------------------ items

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        ConduitKind kind = kindOf(block);
        IIcon[] icons = icons(kind, metadata);
        float lo = 0.5F - ConduitKind.HALF_WIDTH * 1.6F, hi = 0.5F + ConduitKind.HALF_WIDTH * 1.6F;
        float clo = 0.5F - ConduitKind.HALF_WIDTH * 2.2F, chi = 0.5F + ConduitKind.HALF_WIDTH * 2.2F;
        Tessellator t = Tessellator.instance;
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        t.startDrawingQuads();
        t.setNormal(0, 1, 0);
        box(t, 0, 0, 0, new float[]{0, lo, lo, 1, hi, hi}, icons[0], 0, -1, 1, 1, 1);
        box(t, 0, 0, 0, new float[]{clo, clo, clo, chi, chi, chi}, icons[1], -1, -1, 1, 1, 1);
        t.draw();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return renderId;
    }
}
