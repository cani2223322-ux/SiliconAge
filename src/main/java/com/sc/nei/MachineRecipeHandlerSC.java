package com.sc.nei;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.init.ModBlocks;
import com.sc.init.OreRecipes;
import com.sc.inventory.GuiGaugeSC;
import com.sc.inventory.GuiMachineSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.Lang;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * NEI page for one machine type's RecipeRegistry recipes (NEI only knows crafting-table and
 * furnace recipes on its own, so none of the 29 machines' processes showed up before). Items
 * sit in slots, fluids in small tanks with their own texture and a name/amount tooltip,
 * by-products carry their drop chance, and the bottom line gives EU/t, time and defect chance.
 * Registered through one subclass per MachineType (MachineHandlersSC) - see there for why.
 */
public abstract class MachineRecipeHandlerSC extends TemplateRecipeHandler {

    private static final ResourceLocation SHEET = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");
    /** Shared id for the progress-arrow click inside GuiMachineSC; resolved to the open machine's type. */
    static final String ID_OPEN_MACHINE = "sc.machine.open";

    // Layout, relative to the recipe's origin (NEI's 166 x 65 recipe area).
    private static final int[][] IN_SLOTS = {{3, 5}, {21, 5}, {39, 5}};
    private static final int[][] OUT_SLOTS = {{130, 5}, {148, 5}, {130, 23}, {148, 23}};
    private static final int[] IN_TANK_X = {60, 70};
    private static final int[] OUT_TANK_X = {108, 118};
    private static final int TANK_Y = 4, TANK_W = 8, TANK_H = 36;
    private static final int ARROW_X = 82, ARROW_Y = 14;
    /** The scene's band (between the input and output tanks). */
    private static final int SCENE_Y = 3, SCENE_H = 38;

    private final MachineType type;
    private final Gui gui = new Gui();

    protected MachineRecipeHandlerSC(MachineType type) {
        this.type = type;
        // Added here, not in loadTransferRects(): the superclass constructor calls that before
        // `type` is set. Clicking the arrow on this page lists every recipe of the machine.
        transferRects.add(new RecipeTransferRect(new Rectangle(ARROW_X, ARROW_Y, 24, 17), ownId()));
    }

    // newInstance(): TemplateRecipeHandler's default (getClass().newInstance()) recreates the
    // per-machine subclass from MachineHandlersSC, type included.

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
        return SHEET.toString();
    }

    @Override
    public String getOverlayIdentifier() {
        return ownId();
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    /**
     * This page's arrow rect must not be registered on the machine screen (different layout);
     * NEISiliconAgeConfig registers the machine screen's own arrow once, with
     * ID_OPEN_MACHINE, and loadCraftingRecipes resolves which machine it was.
     */
    @Override
    public List<Class<? extends GuiContainer>> getRecipeTransferRectGuis() {
        return new ArrayList<Class<? extends GuiContainer>>();
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        boolean openMachine = ID_OPEN_MACHINE.equals(outputId) && openMachineType() == type;
        if (ownId().equals(outputId) || openMachine) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                arecipes.add(new CachedMachineRecipe(r));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    /** The machine type of the GuiMachineSC the player just clicked in, if any. */
    private static MachineType openMachineType() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen instanceof GuiMachineSC ? ((GuiMachineSC) screen).getMachineType() : null;
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        FluidStack asFluid = fluidFor(result);
        for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
            if (producesItem(r, result) || (asFluid != null && producesFluid(r, asFluid))) {
                arecipes.add(new CachedMachineRecipe(r));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        // The machine block itself: "what does this machine do?"
        if (NEIServerUtils.areStacksSameTypeCrafting(ingredient, machineStack(type))) {
            loadCraftingRecipes(ownId());
            return;
        }
        FluidStack asFluid = fluidFor(ingredient);
        for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
            if (consumesItem(r, ingredient) || (asFluid != null && consumesFluid(r, asFluid))) {
                arecipes.add(new CachedMachineRecipe(r));
            }
        }
    }

    /** The fluid an item stands for: one of our NEI fluid drops, or any registered filled container. */
    static FluidStack fluidFor(ItemStack stack) {
        FluidStack drop = com.sc.item.ItemFluidDropSC.fluidOf(stack);
        return drop != null ? drop : FluidContainerRegistry.getFluidForFilledItem(stack);
    }

    private static boolean producesItem(MachineRecipe r, ItemStack result) {
        for (ItemStack out : r.outputs) {
            if (NEIServerUtils.areStacksSameTypeCrafting(out, result)) {
                return true;
            }
        }
        for (ItemStack out : r.byproducts) {
            if (NEIServerUtils.areStacksSameTypeCrafting(out, result)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumesItem(MachineRecipe r, ItemStack ingredient) {
        for (ItemStack in : r.inputs) {
            if (MachineRecipe.isSameIngredient(ingredient, in)) {
                return true;
            }
        }
        return false;
    }

    private static boolean producesFluid(MachineRecipe r, FluidStack f) {
        return f.isFluidEqual(r.fluidOutputA) || f.isFluidEqual(r.fluidOutputB);
    }

    private static boolean consumesFluid(MachineRecipe r, FluidStack f) {
        return f.isFluidEqual(r.fluidInputA) || f.isFluidEqual(r.fluidInputB);
    }

    static ItemStack machineStack(MachineType type) {
        return com.sc.block.BlockMachineSC.stackOf(type, 1);
    }

    // ---- drawing ----

    /**
     * The machine screens' look: a dark holo panel, holo slot pockets, and in the middle the
     * machine's own animated scene (the same one its screen shows) between its fluid tanks.
     */
    @Override
    public void drawBackground(int recipe) {
        CachedMachineRecipe r = (CachedMachineRecipe) arecipes.get(recipe);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        com.sc.inventory.GuiHoloSC.screen(0, 0, 166, 63);
        for (int i = 0; i < IN_SLOTS.length; i++) {
            if (i < r.recipe.inputs.length) {
                com.sc.inventory.GuiHoloSC.slot(IN_SLOTS[i][0], IN_SLOTS[i][1], false);
            }
        }
        for (int i = 0; i < OUT_SLOTS.length; i++) {
            if (i < r.outputCount) {
                com.sc.inventory.GuiHoloSC.slot(OUT_SLOTS[i][0], OUT_SLOTS[i][1], i == 0);
            }
        }
        MachineRecipe m = r.recipe;
        int x0 = m.fluidInputB != null ? 80 : m.fluidInputA != null ? 70 : 58;
        int x1 = m.fluidOutputA != null || m.fluidOutputB != null ? 106 : 128;
        float t = cycleticks, p = (cycleticks % 80) / 80F;
        if (!NeiScenesSC.draw(type, m, x0, SCENE_Y, x1 - x0, SCENE_H, t, p)) {
            NeiScenesSC.arrow(x0, SCENE_Y, x1 - x0, SCENE_H, p);
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedMachineRecipe r = (CachedMachineRecipe) arecipes.get(recipe);
        Minecraft mc = Minecraft.getMinecraft();

        FluidStack[] tanks = {r.recipe.fluidInputA, r.recipe.fluidInputB, r.recipe.fluidOutputA, r.recipe.fluidOutputB};
        for (int i = 0; i < 4; i++) {
            if (tanks[i] == null) {
                continue;
            }
            int x = tankX(i);
            Gui.drawRect(x - 2, TANK_Y - 2, x + TANK_W + 2, TANK_Y + TANK_H + 2, com.sc.inventory.GuiHoloSC.CYAN_MID);
            Gui.drawRect(x - 1, TANK_Y - 1, x + TANK_W + 1, TANK_Y + TANK_H + 1, 0xFF0E1A26);
            // At least a third full, so a 20 mB input still reads as "this fluid" at a glance.
            int capacity = Math.max(tanks[i].amount, 1000);
            FluidStack shown = tanks[i].copy();
            shown.amount = Math.max(shown.amount, capacity / 3);
            GuiGaugeSC.drawFluid(mc, x, TANK_Y, TANK_W, TANK_H, shown, capacity);
            GuiGaugeSC.bind(mc, SHEET);
            GuiGaugeSC.drawBlended(gui, x, TANK_Y, GuiGaugeSC.SPR_GLASS_U, GuiGaugeSC.SPR_GLASS_V, TANK_W, TANK_H);
        }

        // what the machine really takes: the config's machineSpeed / machineEnergy, as TileEntityMachineSC
        String time = String.format(java.util.Locale.ROOT, "%.1f", com.sc.tileentity.TileEntityMachineSC.configTicks(r.recipe.ticks) / 20f);
        GuiDraw.drawString(Lang.tr("sc.nei.cost", com.sc.tileentity.TileEntityMachineSC.configEuPerTick(type), time), 22, 45,
                com.sc.inventory.GuiHoloSC.VALUE, false);
        int defect = Math.round(r.recipe.defectChance * 100);
        String last = type.heatCapable ? (r.recipe.defectChance > 0 ? Lang.tr("sc.nei.defect.heat", defect) : Lang.tr("sc.nei.heat"))
                : r.recipe.defectChance > 0 ? Lang.tr("sc.nei.defect", defect) : null;
        if (last != null) {
            drawFit(last, 22, 54, 166 - 22 - 2, 0xFF7A5A);
        }
        com.sc.inventory.GuiHoloSC.glint(0, 0, 166, 63);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** A line that is scaled down (never up) to fit maxW. */
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

    private static int tankX(int i) {
        return i < 2 ? IN_TANK_X[i] : OUT_TANK_X[i - 2];
    }

    // ---- tooltips: fluids (NEI has no fluid stacks) and by-product chances ----

    @Override
    public List<String> handleTooltip(GuiRecipe guiRecipe, List<String> currenttip, int recipe) {
        CachedMachineRecipe r = (CachedMachineRecipe) arecipes.get(recipe);
        Point mouse = relativeMouse(guiRecipe, recipe);
        if (mouse != null) {
            FluidStack[] tanks = {r.recipe.fluidInputA, r.recipe.fluidInputB, r.recipe.fluidOutputA, r.recipe.fluidOutputB};
            for (int i = 0; i < 4; i++) {
                if (tanks[i] != null && GuiGaugeSC.isOver(tankX(i), TANK_Y, TANK_W, TANK_H, mouse.x, mouse.y)) {
                    currenttip.add(tanks[i].getFluid().getLocalizedName(tanks[i]));
                    currenttip.add("\u00a77" + tanks[i].amount + " mB");
                    if (i == 3 && RecipeRegistry.usesTank(type, 3)) {
                        currenttip.add("\u00a77" + Lang.tr("sc.gui.tank.output.top"));
                    }
                    currenttip.add("\u00a78" + Lang.tr("sc.nei.fluidkeys"));
                }
            }
        }
        return super.handleTooltip(guiRecipe, currenttip, recipe);
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe guiRecipe, ItemStack stack, List<String> currenttip, int recipe) {
        CachedMachineRecipe r = (CachedMachineRecipe) arecipes.get(recipe);
        for (int i = 0; i < r.byproductStacks.size(); i++) {
            if (guiRecipe.isMouseOver(r.byproductStacks.get(i), recipe)) {
                currenttip.add("\u00a76" + Lang.tr("sc.nei.chance", chancePercent(r.recipe.byproductChances[i])));
            }
        }
        return super.handleItemTooltip(guiRecipe, stack, currenttip, recipe);
    }

    // ---- tanks act like item slots for navigation: R / left click = recipes, U / right click = uses ----

    @Override
    public boolean keyTyped(GuiRecipe guiRecipe, char keyChar, int keyCode, int recipe) {
        ItemStack drop = tankDropUnderMouse(guiRecipe, recipe);
        if (drop != null) {
            if (keyCode == codechicken.nei.NEIClientConfig.getKeyBinding("gui.recipe")) {
                return codechicken.nei.recipe.GuiCraftingRecipe.openRecipeGui("item", drop);
            }
            if (keyCode == codechicken.nei.NEIClientConfig.getKeyBinding("gui.usage")) {
                return codechicken.nei.recipe.GuiUsageRecipe.openRecipeGui("item", drop);
            }
        }
        return super.keyTyped(guiRecipe, keyChar, keyCode, recipe);
    }

    @Override
    public boolean mouseClicked(GuiRecipe guiRecipe, int button, int recipe) {
        ItemStack drop = tankDropUnderMouse(guiRecipe, recipe);
        if (drop != null) {
            if (button == 0) {
                return codechicken.nei.recipe.GuiCraftingRecipe.openRecipeGui("item", drop);
            }
            if (button == 1) {
                return codechicken.nei.recipe.GuiUsageRecipe.openRecipeGui("item", drop);
            }
        }
        return super.mouseClicked(guiRecipe, button, recipe);
    }

    private ItemStack tankDropUnderMouse(GuiRecipe guiRecipe, int recipe) {
        CachedMachineRecipe r = (CachedMachineRecipe) arecipes.get(recipe);
        Point mouse = relativeMouse(guiRecipe, recipe);
        if (mouse == null) {
            return null;
        }
        FluidStack[] tanks = {r.recipe.fluidInputA, r.recipe.fluidInputB, r.recipe.fluidOutputA, r.recipe.fluidOutputB};
        for (int i = 0; i < 4; i++) {
            if (tanks[i] != null && GuiGaugeSC.isOver(tankX(i), TANK_Y, TANK_W, TANK_H, mouse.x, mouse.y)) {
                return com.sc.item.ItemFluidDropSC.stackOf(tanks[i].getFluid());
            }
        }
        return null;
    }

    private static String chancePercent(float chance) {
        float pct = chance * 100f;
        return pct == Math.round(pct) ? String.valueOf(Math.round(pct)) : String.format(java.util.Locale.ROOT, "%.1f", pct);
    }

    /** Mouse position relative to recipe `recipe`'s origin, or null if it can't be read. */
    static Point relativeMouse(GuiRecipe guiRecipe, int recipe) {
        try {
            Point mouse = GuiDraw.getMousePosition();
            Point offset = guiRecipe.getRecipePosition(recipe);
            int left = (Integer) ReflectionHelper.getPrivateValue(GuiContainer.class, guiRecipe, "guiLeft", "field_147003_i");
            int top = (Integer) ReflectionHelper.getPrivateValue(GuiContainer.class, guiRecipe, "guiTop", "field_147009_r");
            return new Point(mouse.x - left - offset.x, mouse.y - top - offset.y);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- one displayed recipe ----

    public class CachedMachineRecipe extends CachedRecipe {

        final MachineRecipe recipe;
        final List<PositionedStack> ingredients = new ArrayList<PositionedStack>();
        final List<PositionedStack> others = new ArrayList<PositionedStack>();
        final List<PositionedStack> byproductStacks = new ArrayList<PositionedStack>();
        final PositionedStack result;
        final int outputCount;

        CachedMachineRecipe(MachineRecipe recipe) {
            this.recipe = recipe;
            for (int i = 0; i < recipe.inputs.length && i < IN_SLOTS.length; i++) {
                ingredients.add(new PositionedStack(alternatives(recipe.inputs[i]), IN_SLOTS[i][0], IN_SLOTS[i][1]));
            }
            List<ItemStack> outs = new ArrayList<ItemStack>();
            for (ItemStack s : recipe.outputs) {
                outs.add(s);
            }
            int firstByproduct = outs.size();
            for (ItemStack s : recipe.byproducts) {
                outs.add(s);
            }
            outputCount = Math.min(outs.size(), OUT_SLOTS.length);
            PositionedStack first = null;
            for (int i = 0; i < outputCount; i++) {
                PositionedStack ps = new PositionedStack(outs.get(i).copy(), OUT_SLOTS[i][0], OUT_SLOTS[i][1]);
                if (i == 0) {
                    first = ps;
                } else {
                    others.add(ps);
                }
                if (i >= firstByproduct) {
                    byproductStacks.add(ps);
                }
            }
            result = first;
            others.add(new PositionedStack(machineStack(type), 3, 44));
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return getCycledIngredients(cycleticks / 20, ingredients);
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return others;
        }
    }

    /** An ingredient plus every OreDictionary item the machine would accept in its place. */
    private static Object alternatives(ItemStack required) {
        String ore = OreRecipes.sharedName(required);
        if (ore == null) {
            return required.copy();
        }
        List<ItemStack> list = new ArrayList<ItemStack>();
        for (ItemStack s : OreDictionary.getOres(ore)) {
            ItemStack c = s.copy();
            c.stackSize = required.stackSize;
            list.add(c);
        }
        return list.isEmpty() ? required.copy() : list;
    }
}
