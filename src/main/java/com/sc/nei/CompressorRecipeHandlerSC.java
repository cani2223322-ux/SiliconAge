package com.sc.nei;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.inventory.GuiMachineSC;
import com.sc.inventory.GuiSceneSC;
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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * NEI page of the Matter Compressor. It has no recipe list - any item without NBT becomes mass -
 * so the page shows examples (stone, the heavy-metal blocks, dirt) with how many of each make one
 * Compressed Matter Capsule, and for a heavy metal (U on it) how much mass it gives. R on the
 * capsule, U on the machine and a click on its screen's progress bar open it as well. The last page: a
 * singularity clot in the liquid mode - ItemSingularClotSC.SM_PER_CLOT mB of singular matter at once.
 */
public class CompressorRecipeHandlerSC extends TemplateRecipeHandler {

    private static final int IN_X = 22, IN_Y = 17, OUT_X = 128, OUT_Y = 17, SCENE_X = 46, SCENE_Y = 3, SCENE_W = 74, SCENE_H = 38;
    private static final MachineType TYPE = MachineType.MATTER_COMPRESSOR;

    public CompressorRecipeHandlerSC() {
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(SCENE_X, SCENE_Y, SCENE_W, SCENE_H), ownId()));
    }

    MachineType getMachineType() {
        return TYPE;
    }

    private static String ownId() {
        return "sc.machine." + TYPE.name();
    }

    @Override
    public String getRecipeName() {
        return TYPE.localizedName();
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

    /** Stone, the heavy-metal blocks (the mod's and vanilla's), plain dirt. */
    private static List<ItemStack> examples() {
        List<ItemStack> list = new ArrayList<ItemStack>();
        list.add(new ItemStack(Blocks.cobblestone));
        for (String ore : new String[]{"blockLead", "blockTungsten", "blockTantalum", "blockHafnium"}) {
            List<ItemStack> found = OreDictionary.getOres(ore);
            if (!found.isEmpty() && found.get(0).getItemDamage() != OreDictionary.WILDCARD_VALUE) {
                list.add(found.get(0).copy());
            }
        }
        list.add(new ItemStack(Blocks.iron_block));
        list.add(new ItemStack(Blocks.gold_block));
        list.add(new ItemStack(Blocks.dirt));
        return list;
    }

    private boolean openedFromOwnScreen(String outputId) {
        if (!MachineRecipeHandlerSC.ID_OPEN_MACHINE.equals(outputId)) {
            return false;
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen instanceof GuiMachineSC && ((GuiMachineSC) screen).getMachineType() == TYPE;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ownId().equals(outputId) || openedFromOwnScreen(outputId)) {
            for (ItemStack s : examples()) {
                arecipes.add(new CachedCompress(s));
            }
            addClot();
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        ItemStack capsule = TileEntityMachineSC.capsuleStack();
        net.minecraftforge.fluids.FluidStack asFluid = result == null ? null : MachineRecipeHandlerSC.fluidFor(result);
        boolean sm = com.sc.init.ModFluids.singularMatter != null && (asFluid != null && asFluid.getFluid() == com.sc.init.ModFluids.singularMatter
                || result != null && result.getItem() instanceof com.sc.item.ItemSingularCellSC);
        if (capsule != null && NEIServerUtils.areStacksSameTypeCrafting(capsule, result) || sm) {   // СМ1: singular matter comes from here too
            loadCraftingRecipes(ownId());
        }
    }

    /** The clot's page (liquid mode): one clot -> SM_PER_CLOT mB of singular matter. */
    private void addClot() {
        if (com.sc.init.ModItems.singularClot != null && com.sc.init.ModItems.singularCell != null && com.sc.init.ModFluids.singularMatter != null) {
            arecipes.add(new CachedCompress());
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (NEIServerUtils.areStacksSameTypeCrafting(ingredient, MachineRecipeHandlerSC.machineStack(TYPE))) {
            loadCraftingRecipes(ownId());
            return;
        }
        if (com.sc.item.ItemSingularClotSC.isClot(ingredient)) {
            addClot();
            return;
        }
        // only the heavy metals: every other item would carry a page that only says "it fits too"
        int mass = ingredient == null || ingredient.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0
                : TileEntityMachineSC.matterMass(ingredient);
        if (mass == TileEntityMachineSC.MASS_HEAVY * TileEntityMachineSC.MASS_ITEM
                || mass == TileEntityMachineSC.MASS_HEAVY * TileEntityMachineSC.MASS_BLOCK) {
            arecipes.add(new CachedCompress(ingredient));
        }
    }

    // ---- drawing: the machine screen's look ----

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        com.sc.inventory.GuiHoloSC.screen(0, 0, 166, 63);
        com.sc.inventory.GuiHoloSC.slot(IN_X, IN_Y, false);
        com.sc.inventory.GuiHoloSC.slot(OUT_X, OUT_Y, true);
        float t = cycleticks, p = (cycleticks % 80) / 80F;
        GuiSceneSC.matterPress(SCENE_X, SCENE_Y, SCENE_W, SCENE_H, t, true, p, 0.6F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedCompress r = (CachedCompress) arecipes.get(recipe);
        if (r.clot) {
            drawFit(Lang.tr("sc.nei.comp.clot", com.sc.item.ItemSingularClotSC.SM_PER_CLOT), 22, 45, 166 - 24, 0xD080FF);
            drawFit(Lang.tr("sc.nei.comp.clot.mode"), 22, 54, 166 - 24, com.sc.inventory.GuiHoloSC.VALUE);
            com.sc.inventory.GuiHoloSC.glint(0, 0, 166, 63);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            return;
        }
        String time = String.format(java.util.Locale.ROOT, "%.1f",
                TileEntityMachineSC.configTicks(TileEntityMachineSC.COMPRESS_TICKS) / 20F);
        GuiDraw.drawString(Lang.tr("sc.nei.cost", TileEntityMachineSC.configEuPerTick(TYPE), time), 22, 45,
                com.sc.inventory.GuiHoloSC.VALUE, false);
        drawFit(Lang.tr("sc.nei.comp.mass", r.mass, r.needed, TileEntityMachineSC.MATTER_PER_CAPSULE), 22, 54, 166 - 24, 0xFF9AA4);
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

    @Override
    public List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe guiRecipe, ItemStack stack, List<String> currenttip, int recipe) {
        CachedCompress r = (CachedCompress) arecipes.get(recipe);
        if (r.clot) {
            if (guiRecipe.isMouseOver(r.in, recipe)) {
                currenttip.add("§d" + Lang.tr("sc.nei.comp.clot.tip"));
            }
            return super.handleItemTooltip(guiRecipe, stack, currenttip, recipe);
        }
        if (guiRecipe.isMouseOver(r.in, recipe)) {
            currenttip.add("§c" + Lang.tr("sc.nei.comp.tip", r.mass));
            currenttip.add("§8" + Lang.tr("sc.nei.comp.any"));
        }
        if (guiRecipe.isMouseOver(r.out, recipe)) {
            currenttip.add("§d" + Lang.tr("sc.nei.comp.liquid", TileEntityMachineSC.SM_PER_CAPSULE));
        }
        return super.handleItemTooltip(guiRecipe, stack, currenttip, recipe);
    }

    // ---- one example ----

    public class CachedCompress extends CachedRecipe {
        final PositionedStack in, out;
        final int mass, needed;
        final boolean clot;
        final List<PositionedStack> others = new ArrayList<PositionedStack>();

        /** The singularity clot: SM_PER_CLOT mB in a Singular Matter Cell (the liquid mode). */
        CachedCompress() {
            clot = true;
            mass = 0;
            needed = 1;
            in = new PositionedStack(new ItemStack(com.sc.init.ModItems.singularClot), IN_X, IN_Y);
            out = new PositionedStack(com.sc.item.ItemSingularCellSC.filled(com.sc.init.ModItems.singularCell,
                    com.sc.item.ItemSingularClotSC.SM_PER_CLOT), OUT_X, OUT_Y);
            others.add(new PositionedStack(MachineRecipeHandlerSC.machineStack(TYPE), 3, 44));
        }

        CachedCompress(ItemStack input) {
            clot = false;
            ItemStack i = input.copy();
            mass = Math.max(1, TileEntityMachineSC.matterMass(i));
            needed = (TileEntityMachineSC.MATTER_PER_CAPSULE + mass - 1) / mass;
            i.stackSize = Math.min(needed, i.getMaxStackSize());
            in = new PositionedStack(i, IN_X, IN_Y);
            ItemStack capsule = TileEntityMachineSC.capsuleStack();
            out = new PositionedStack(capsule == null ? new ItemStack(Blocks.stone) : capsule, OUT_X, OUT_Y);
            others.add(new PositionedStack(MachineRecipeHandlerSC.machineStack(TYPE), 3, 44));
        }

        @Override
        public PositionedStack getResult() {
            return out;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return java.util.Collections.singletonList(in);
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return others;
        }
    }
}
