package com.sc.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.sc.inventory.GuiFurnaceSceneSC;
import com.sc.inventory.GuiMachineSC;
import com.sc.machine.MachineType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityMachineSC;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/**
 * NEI pages of the electric and the induction furnace: every furnace recipe (vanilla's and other
 * mods'), the furnace's own scene between the slots, its EU/t, the time a piece takes and the
 * experience it keeps. Opened from the machine block (U), from any smeltable item (U) or product
 * (R), and from the progress bar on the furnace's own screen. One subclass per furnace, as for
 * the machines (TemplateRecipeHandler recreates handlers by class).
 */
public abstract class SmelterRecipeHandlerSC extends TemplateRecipeHandler {

    private static final int IN_X = 22, IN_Y = 17, OUT_X = 128, OUT_Y = 17, SCENE_X = 46, SCENE_Y = 3, SCENE_W = 74, SCENE_H = 38;

    private final MachineType type;

    protected SmelterRecipeHandlerSC(MachineType type) {
        this.type = type;
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(SCENE_X, SCENE_Y, SCENE_W, SCENE_H), ownId()));
    }

    public static class Electric extends SmelterRecipeHandlerSC {
        public Electric() {
            super(MachineType.ELECTRIC_FURNACE);
        }
    }

    public static class Induction extends SmelterRecipeHandlerSC {
        public Induction() {
            super(MachineType.INDUCTION_FURNACE);
        }
    }

    MachineType getMachineType() {
        return type;
    }

    private String ownId() {
        return "sc.machine." + type.name();
    }

    @Override
    public String getRecipeName() {
        return type.localizedName();
    }

    @Override
    public String getGuiTexture() {
        return "textures/gui/container/furnace.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return ownId();
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public List<Class<? extends GuiContainer>> getRecipeTransferRectGuis() {
        return new ArrayList<Class<? extends GuiContainer>>();
    }

    @SuppressWarnings("unchecked")
    private static Map<ItemStack, ItemStack> smelting() {
        return FurnaceRecipes.smelting().getSmeltingList();
    }

    /** The furnace screen the player clicked the progress bar on, if it's this furnace. */
    private boolean openedFromOwnScreen(String outputId) {
        if (!MachineRecipeHandlerSC.ID_OPEN_MACHINE.equals(outputId)) {
            return false;
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen instanceof GuiMachineSC && ((GuiMachineSC) screen).getMachineType() == type;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ownId().equals(outputId) || openedFromOwnScreen(outputId)) {
            for (Map.Entry<ItemStack, ItemStack> e : smelting().entrySet()) {
                arecipes.add(new CachedSmelt(e.getKey(), e.getValue()));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Map.Entry<ItemStack, ItemStack> e : smelting().entrySet()) {
            if (NEIServerUtils.areStacksSameType(e.getValue(), result)) {
                arecipes.add(new CachedSmelt(e.getKey(), e.getValue()));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (NEIServerUtils.areStacksSameTypeCrafting(ingredient, MachineRecipeHandlerSC.machineStack(type))) {
            loadCraftingRecipes(ownId());
            return;
        }
        for (Map.Entry<ItemStack, ItemStack> e : smelting().entrySet()) {
            if (NEIServerUtils.areStacksSameTypeCrafting(e.getKey(), ingredient)) {
                CachedSmelt r = new CachedSmelt(e.getKey(), e.getValue());
                r.setIngredientPermutation(java.util.Collections.singletonList(r.in), ingredient);   // show what was asked, not every subtype
                arecipes.add(r);
            }
        }
    }

    // ---- drawing: the furnace screens' look ----

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        com.sc.inventory.GuiHoloSC.screen(0, 0, 166, 63);
        com.sc.inventory.GuiHoloSC.slot(IN_X, IN_Y, false);
        com.sc.inventory.GuiHoloSC.slot(OUT_X, OUT_Y, true);
        float t = cycleticks;
        if (type == MachineType.INDUCTION_FURNACE) {
            float heat = (cycleticks % 200) / 200F;
            GuiFurnaceSceneSC.crucibles(SCENE_X, SCENE_Y, SCENE_W, SCENE_H, t, heat, new boolean[]{true, true});
        } else {
            GuiFurnaceSceneSC.chamber(SCENE_X, SCENE_Y, SCENE_W, SCENE_H, t, true);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedSmelt r = (CachedSmelt) arecipes.get(recipe);
        String fast = String.format(java.util.Locale.ROOT, "%.1f", TileEntityMachineSC.SMELT_TICKS / 20F);
        String line = type == MachineType.INDUCTION_FURNACE
                ? Lang.tr("sc.nei.smelt.induction", type.euPerTick, fast, String.format(java.util.Locale.ROOT, "%.1f", TileEntityMachineSC.SMELT_TICKS / 60F))
                : Lang.tr("sc.nei.smelt", type.euPerTick, fast);
        GuiDraw.drawString(line, 22, 45, com.sc.inventory.GuiHoloSC.VALUE, false);
        float xp = FurnaceRecipes.smelting().func_151398_b(r.out.item);
        if (xp > 0) {
            GuiDraw.drawString(Lang.tr("sc.nei.smelt.xp", String.format(java.util.Locale.ROOT, "%.1f", xp)), 22, 54, 0xB8FF40, false);
        }
        com.sc.inventory.GuiHoloSC.glint(0, 0, 166, 63);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ---- one recipe ----

    public class CachedSmelt extends CachedRecipe {
        final PositionedStack in, out;
        final List<PositionedStack> others = new ArrayList<PositionedStack>();

        CachedSmelt(ItemStack input, ItemStack output) {
            ItemStack i = input.copy();
            i.stackSize = 1;
            in = new PositionedStack(i, IN_X, IN_Y);
            out = new PositionedStack(output.copy(), OUT_X, OUT_Y);
            others.add(new PositionedStack(MachineRecipeHandlerSC.machineStack(type), 3, 44));
        }

        @Override
        public PositionedStack getResult() {
            return out;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return getCycledIngredients(cycleticks / 48, java.util.Collections.singletonList(in));
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return others;
        }
    }
}
