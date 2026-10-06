package com.sc.nei;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularStationMath;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

/**
 * NEI page of the Singular Station's Б-1 conversion: an Exo piece and its materials -> the Singular
 * piece of level 1, with the EU / gases and the time under it (SingularStationMath). R on a
 * Singular piece, U on an Exo piece, a material or the station itself open it. The tool slot's two
 * conversions (Exo blade / drill -> the Singular one, docs/plan-singular-tools.md §4) are pages 4 and 5.
 */
public class SingularConvertHandlerSC extends TemplateRecipeHandler {

    static final String ID = "sc.singconvert";
    private static final int IN_X = 4, Y = 6, MAT_X = 28, OUT_X = 142, ARROW_X = 104;
    /** Page types: 0..3 the armour pieces, then the blade and the drill (tool kind = type - 3). */
    private static final int T_BLADE = 4, T_DRILL = 5, TYPES = 6;

    public SingularConvertHandlerSC() {
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(ARROW_X, Y, 30, 16), ID));
    }

    @Override
    public String getRecipeName() {
        return Lang.tr("sc.nei.conv.title");
    }

    @Override
    public String getGuiTexture() {
        return "textures/gui/container/furnace.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public List<Class<? extends GuiContainer>> getRecipeTransferRectGuis() {
        return new ArrayList<Class<? extends GuiContainer>>();
    }

    private static ItemStack piece(ArmorSuit suit, int type) {
        if (type == T_BLADE) {
            return new ItemStack(ModItems.BLADES.get(suit == ArmorSuit.EXO ? com.sc.util.BladeType.EXO : com.sc.util.BladeType.SINGULAR));
        }
        if (type == T_DRILL) {
            return new ItemStack(ModItems.DRILLS.get(suit == ArmorSuit.EXO ? com.sc.util.DrillType.EXO : com.sc.util.DrillType.SINGULAR));
        }
        return new ItemStack(ModItems.ARMOR.get(suit)[type]);
    }

    private static int toolOf(int type) {
        return type >= T_BLADE ? type - 3 : SingularStationMath.TOOL_NONE;
    }

    private static int maskOf(int type) {
        return type < T_BLADE ? 1 << type : 0;
    }

    /** The page type of a blade / drill stack (any tier), or -1. */
    private static int toolType(ItemStack s) {
        int k = TileEntitySingularStationSC.toolKindOf(s);
        return k == SingularStationMath.TOOL_BLADE ? T_BLADE : k == SingularStationMath.TOOL_DRILL ? T_DRILL : -1;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) {
            for (int t = 0; t < TYPES; t++) {
                arecipes.add(new CachedConvert(t));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result != null && result.getItem() instanceof ItemArmorSC && ((ItemArmorSC) result.getItem()).getSuit() == ArmorSuit.SINGULAR) {
            arecipes.add(new CachedConvert(((ItemArmorSC) result.getItem()).armorType));
        } else if (com.sc.util.ToolLevelSC.isSingularTool(result)) {
            arecipes.add(new CachedConvert(toolType(result)));
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        if (NEIServerUtils.areStacksSameTypeCrafting(ingredient, new ItemStack(ModBlocks.singularStation))) {
            loadCraftingRecipes(ID);
            return;
        }
        if (TileEntitySingularStationSC.isExo(ingredient)) {
            arecipes.add(new CachedConvert(((ItemArmorSC) ingredient.getItem()).armorType));
            return;
        }
        if (TileEntitySingularStationSC.isExoTool(ingredient)) {
            arecipes.add(new CachedConvert(toolType(ingredient)));
            return;
        }
        int k = TileEntitySingularStationSC.materialKind(ingredient);
        for (int t = 0; k >= 0 && t < TYPES; t++) {
            if (SingularStationMath.convertMaterials(maskOf(t), toolOf(t))[k] > 0) {
                arecipes.add(new CachedConvert(t));
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        com.sc.inventory.GuiHoloSC.screen(0, 0, 166, 63);
        CachedConvert r = (CachedConvert) arecipes.get(recipe);
        com.sc.inventory.GuiHoloSC.slot(IN_X, Y, false);
        for (int i = 0; i < r.mats.size(); i++) {
            com.sc.inventory.GuiHoloSC.slot(MAT_X + i * 18, Y, false);
        }
        com.sc.inventory.GuiHoloSC.slot(OUT_X, Y, true);
        // the arrow: violet, filling with the cycle
        int len = 28, fill = (cycleticks % 60) * len / 60;
        Gui.drawRect(ARROW_X, Y + 7, ARROW_X + len, Y + 9, 0xFF3A2A50);
        Gui.drawRect(ARROW_X, Y + 7, ARROW_X + fill, Y + 9, 0xFFBE6EFF);
        Gui.drawRect(ARROW_X + len, Y + 5, ARROW_X + len + 2, Y + 11, 0xFFBE6EFF);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedConvert r = (CachedConvert) arecipes.get(recipe);
        long[] c = SingularStationMath.convertCost(maskOf(r.type), toolOf(r.type));
        StringBuilder b = new StringBuilder(SingularStationMath.shortAmount(c[0], Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"),
                Lang.tr("sc.singStation.unit.b")) + " EU");
        for (int i = 1; i < SingularStationMath.RESOURCES; i++) {
            if (c[i] > 0) {
                b.append(" · ").append(com.sc.client.GasUiSC.shortName(SingularStationMath.GAS[i])).append(' ').append(c[i]);
            }
        }
        drawFit(b.toString(), 4, 30, 158, com.sc.inventory.GuiHoloSC.VALUE);
        drawFit(Lang.tr("sc.nei.conv.time", TileEntitySingularStationSC.timeText(SingularStationMath.convertTicks(maskOf(r.type), toolOf(r.type)))), 4, 40, 120,
                com.sc.inventory.GuiHoloSC.LABEL);
        drawFit(Lang.tr(r.type >= T_BLADE ? "sc.nei.conv.note.tool" : "sc.nei.conv.note"), 4, 50, 120, 0x8898A8);
        com.sc.inventory.GuiHoloSC.glint(0, 0, 166, 63);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void drawFit(String s, int x, int y, int maxW, int color) {
        int w = Minecraft.getMinecraft().fontRenderer.getStringWidth(s);
        if (w <= maxW) {
            GuiDraw.drawString(s, x, y, color, false);
            return;
        }
        float k = maxW / (float) w;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + 4F * (1F - k), 0F);
        GL11.glScalef(k, k, 1F);
        GuiDraw.drawString(s, 0, 0, color, false);
        GL11.glPopMatrix();
    }

    // ---- one piece's (or tool's) conversion ----

    public class CachedConvert extends CachedRecipe {
        final int type;
        final PositionedStack in, out;
        final List<PositionedStack> mats = new ArrayList<PositionedStack>();
        final List<PositionedStack> others = new ArrayList<PositionedStack>();

        CachedConvert(int type) {
            this.type = type;
            in = new PositionedStack(piece(ArmorSuit.EXO, type), IN_X, Y);
            out = new PositionedStack(piece(ArmorSuit.SINGULAR, type), OUT_X, Y);
            int[] need = SingularStationMath.convertMaterials(maskOf(type), toolOf(type));
            for (int k = 0; k < need.length; k++) {
                if (need[k] > 0) {
                    mats.add(new PositionedStack(TileEntitySingularStationSC.materialStack(k, need[k]), MAT_X + mats.size() * 18, Y));
                }
            }
            others.add(new PositionedStack(new ItemStack(ModBlocks.singularStation), 146, 44));
        }

        @Override
        public PositionedStack getResult() {
            return out;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            List<PositionedStack> all = new ArrayList<PositionedStack>();
            all.add(in);
            all.addAll(mats);
            return all;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return others;
        }
    }
}
