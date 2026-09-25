package com.sc.inventory;

import org.lwjgl.opengl.GL11;

import com.sc.energy.Tier;
import com.sc.init.ModFluids;
import com.sc.manual.Lang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

/**
 * Shared drawing helpers for the gauges in the machine/generator/field GUIs, so all three read
 * the same way: a recessed well, filled from the bottom (or left) by the live value, with a
 * hover tooltip carrying the actual numbers.
 *
 * All three GUI sheets are 256x256 (drawTexturedModalRect's UV scale is a fixed 1/256) and
 * carry the same sprite block right of the panel - the SPR_* coordinates below.
 *
 * Fill drawing happens in drawGuiContainerBackgroundLayer, where coordinates are absolute
 * (guiLeft + x); hover testing takes panel-local coordinates (mouse - guiLeft/guiTop, see
 * GuiMachineSC.drawScreen) - hence the separate isOver().
 */
public final class GuiGaugeSC {

    // Sprite block (u, v) - see gen_gui_panels.py's sprites().
    public static final int SPR_ARROW_U = 176, SPR_ARROW_V = 0;         // 16x16 progress arrow, full
    public static final int SPR_SLOT_U = 192, SPR_SLOT_V = 0;           // 18x18 slot pocket
    public static final int SPR_ENERGY_U = 212, SPR_ENERGY_V = 0;       // 10x44 energy fill
    public static final int SPR_GLASS_U = 222, SPR_GLASS_V = 0;         // 8x44 tank glass + ticks
    public static final int SPR_UNUSED_U = 230, SPR_UNUSED_V = 0;       // 8x44 hatch: tank never used
    public static final int SPR_HEAT_U = 176, SPR_HEAT_V = 48;          // 42x5 heat fill
    public static final int SPR_IGNITION_U = 176, SPR_IGNITION_V = 54;  // 52x8 ignition fill
    public static final int SPR_SUN_U = 176, SPR_SUN_OFF_U = 208, SPR_SUN_V = 64; // 32x32
    public static final int SPR_ARROW_R_U = 176, SPR_ARROW_R_FULL_U = 200, SPR_ARROW_R_V = 100; // 24x17, right-pointing (NEI)
    public static final int SPR_PROCESS_U = 176, SPR_PROCESS_V = 120;  // 16x16 frames: 4 per row, one row per ProcessKind

    private static final int EMPTY_WELL = 0xFF3C3C3C;
    private static final int WELL_RIM = 0xFF1E1E1E;
    private static final int WELL_LIGHT = 0xFFFFFFFF;

    private GuiGaugeSC() {
    }

    /** Fills a well bottom-up with a flat colour. `fraction` is clamped to 0..1. */
    public static void drawVertical(int x, int y, int width, int height, float fraction, int argb) {
        int filled = (int) (height * clamp(fraction));
        if (filled > 0) {
            Gui.drawRect(x, y + (height - filled), x + width, y + height, argb);
        }
    }

    /** Bottom-up fill cut from a sprite of the GUI sheet that's currently bound. */
    public static void drawSpriteVertical(Gui gui, int x, int y, int u, int v, int width, int height, float fraction) {
        int filled = (int) (height * clamp(fraction));
        if (filled > 0) {
            gui.drawTexturedModalRect(x, y + height - filled, u, v + height - filled, width, filled);
        }
    }

    /** Top-down fill - the progress arrow runs from the input row down to the output row. */
    public static void drawSpriteDown(Gui gui, int x, int y, int u, int v, int width, int height, float fraction) {
        int filled = (int) (height * clamp(fraction));
        if (filled > 0) {
            gui.drawTexturedModalRect(x, y, u, v, width, filled);
        }
    }

    public static void drawSpriteHorizontal(Gui gui, int x, int y, int u, int v, int width, int height, float fraction) {
        int filled = (int) (width * clamp(fraction));
        if (filled > 0) {
            gui.drawTexturedModalRect(x, y, u, v, filled, height);
        }
    }

    /** A sprite with alpha (tank glass) - blending isn't guaranteed on in the background layer. */
    public static void drawBlended(Gui gui, int x, int y, int u, int v, int width, int height) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        gui.drawTexturedModalRect(x, y, u, v, width, height);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** A recessed well drawn in code, for the GUIs whose wells depend on the block's type. */
    public static void drawWell(int x, int y, int width, int height) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, WELL_LIGHT);
        Gui.drawRect(x - 1, y - 1, x + width, y + height, WELL_RIM);
        Gui.drawRect(x, y, x + width, y + height, EMPTY_WELL);
        GL11.glColor4f(1f, 1f, 1f, 1f);    // drawRect leaves its colour set - it'd tint the next sprite
    }

    /**
     * A tank's contents drawn with the fluid's own (animated) texture, tiled up from the bottom
     * and tinted with its colour - falls back to a flat colour for a fluid with no icon. Leaves
     * the blocks atlas bound: callers rebind their GUI sheet afterwards (bind()).
     */
    public static void drawFluid(Minecraft mc, int x, int y, int width, int height, FluidStack stack, int capacity) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0) {
            return;
        }
        int filled = Math.max(1, (int) (height * clamp((float) stack.amount / Math.max(1, capacity))));
        IIcon icon = stack.getFluid().getIcon(stack);
        if (icon == null) {
            Gui.drawRect(x, y + height - filled, x + width, y + height, fluidColor(stack));
            return;
        }
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        // Our textures carry their colour already; other mods' fluids expect getColor() as a tint.
        int tint = ModFluids.OWNED.contains(stack.getFluid()) ? 0xFFFFFF : stack.getFluid().getColor(stack);
        GL11.glColor4f(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        double u0 = icon.getMinU(), u1 = icon.getInterpolatedU(Math.min(16, width));
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int drawn = 0; drawn < filled; ) {
            int seg = Math.min(16, filled - drawn);
            int bottom = y + height - drawn;
            int top = bottom - seg;
            double v0 = icon.getInterpolatedV(16 - seg), v1 = icon.getMaxV();
            t.addVertexWithUV(x, bottom, 0, u0, v1);
            t.addVertexWithUV(x + width, bottom, 0, u1, v1);
            t.addVertexWithUV(x + width, top, 0, u1, v0);
            t.addVertexWithUV(x, top, 0, u0, v0);
            drawn += seg;
        }
        t.draw();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Rebinds a GUI sheet after drawFluid() switched to the blocks atlas. */
    public static void bind(Minecraft mc, ResourceLocation sheet) {
        mc.getTextureManager().bindTexture(sheet);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Small tier plate ("LV" .. "EV") ending at rightX on the title row, in the tier's colour. */
    public static void drawTierBadge(FontRenderer font, Tier tier, int rightX, int y) {
        String label = tier.name();
        int w = font.getStringWidth(label) + 4;
        int x = rightX - w;
        Gui.drawRect(x, y, x + w, y + 10, 0xFF000000 | tierColor(tier));
        Gui.drawRect(x, y + 9, x + w, y + 10, 0x60000000);
        font.drawString(label, x + 2, y + 1, 0xFFFFFF);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** LV grey, MV orange, HV gold, EV violet - the usual IC2/GregTech reading of the tiers. */
    public static int tierColor(Tier tier) {
        switch (tier) {
            case LV: return 0x6E6E6E;
            case MV: return 0xC0661A;
            case HV: return 0xB8900E;
            default: return 0x7A3CB8;
        }
    }

    public static boolean isOver(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float fraction) {
        return fraction < 0 ? 0 : fraction > 1 ? 1 : fraction;
    }

    /**
     * Our own fluids' colour (ModFluids.COLORS, matching their textures), else the fluid's own
     * colour when it defines one, else a stable colour derived from its name - so no tank ever
     * renders as flat unreadable white.
     */
    public static int fluidColor(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return EMPTY_WELL;
        }
        Integer own = ModFluids.COLORS.get(stack.getFluid().getName());
        if (own != null) {
            return own;
        }
        int color = stack.getFluid().getColor();
        if ((color & 0x00FFFFFF) != 0x00FFFFFF) {
            return 0xFF000000 | (color & 0x00FFFFFF);
        }
        int hash = stack.getFluid().getName().hashCode();
        int r = 80 + Math.abs(hash % 150);
        int g = 80 + Math.abs((hash / 150) % 150);
        int b = 80 + Math.abs((hash / 22500) % 150);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** e.g. "Argon: 50 / 4000 mB", or the localised "Empty" when there's nothing in it. */
    public static String fluidLabel(FluidStack stack, int capacity) {
        if (stack == null || stack.getFluid() == null) {
            return Lang.tr("sc.gui.tank.empty") + " (0 / " + capacity + " mB)";
        }
        return stack.getFluid().getLocalizedName(stack) + ": " + stack.amount + " / " + capacity + " mB";
    }
}
