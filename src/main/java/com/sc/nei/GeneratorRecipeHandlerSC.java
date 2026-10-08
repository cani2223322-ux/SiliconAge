package com.sc.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.energy.GeneratorType;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.inventory.GuiGaugeSC;
import com.sc.item.ItemFluidDropSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * NEI page for the generators: what each one burns (fluids as ItemFluidDropSC drops, so they
 * can be followed with R/U), what it puts out, and how much energy one unit of fuel is worth.
 * R on a generator shows its page, U on a fuel shows which generator burns it - any furnace
 * fuel counts for the Combustion Generator's solid-fuel slot.
 */
public class GeneratorRecipeHandlerSC extends TemplateRecipeHandler {

    private static final ResourceLocation SHEET = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");
    private static final String ID = "sc.generators";
    private static final int FUEL_X = 30, FUEL2_X = 50, SLOT_Y = 16, ARROW_X = 74, ARROW_Y = 16, GEN_X = 110;

    private final Gui gui = new Gui();

    public GeneratorRecipeHandlerSC() {
        transferRects.add(new RecipeTransferRect(new Rectangle(ARROW_X, ARROW_Y, 24, 17), ID));
    }

    @Override
    public String getRecipeName() {
        return Lang.tr("sc.nei.gen.name");
    }

    @Override
    public String getGuiTexture() {
        return SHEET.toString();
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    // ---- the entries: one per generator, two for the Combustion Generator (diesel / solid fuel) ----

    private List<GenEntry> allEntries(ItemStack solidFuel) {
        List<GenEntry> list = new ArrayList<GenEntry>();
        for (GeneratorType type : GeneratorType.values()) {
            if (type == GeneratorType.CREATIVE) {
                continue;
            }
            if (type == GeneratorType.SOLID_FUEL) {
                list.add(new GenEntry(type, solidFuel != null ? solidFuel : new ItemStack(Items.coal)));
                continue;
            }
            if (type.kind == GeneratorType.Kind.FLUID_FUEL || type.kind == GeneratorType.Kind.DUAL_FLUID
                    || type.kind == GeneratorType.Kind.EXO) {
                ItemStack drop = ItemFluidDropSC.stackOf(FluidRegistry.getFluid(type.fuelFluidName));
                list.add(new GenEntry(type, drop));
            } else {
                list.add(new GenEntry(type, null));
            }
            if (type == GeneratorType.COMBUSTION) {
                list.add(new GenEntry(type, solidFuel != null ? solidFuel : new ItemStack(Items.coal)));
            }
        }
        return list;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) {
            arecipes.addAll(allEntries(null));
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (GenEntry e : allEntries(null)) {
            if (NEIServerUtils.areStacksSameTypeCrafting(e.generatorStack(), result)) {
                arecipes.add(e);
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        boolean isGenerator = ModBlocks.generatorTypeOf(ingredient) != null;
        FluidStack fluid = MachineRecipeHandlerSC.fluidFor(ingredient);
        boolean furnaceFuel = fluid == null && TileEntityFurnace.isItemFuel(ingredient);
        for (GenEntry e : allEntries(furnaceFuel ? oneOf(ingredient) : null)) {
            if (isGenerator && NEIServerUtils.areStacksSameTypeCrafting(e.generatorStack(), ingredient)) {
                arecipes.add(e);
            } else if (fluid != null && e.fuel != null && !e.isSolidFuel()
                    && (fluid.getFluid().getName().equals(e.type.fuelFluidName) || fluid.getFluid().getName().equals(e.type.fuel2FluidName))) {
                arecipes.add(e);
            } else if (e.type.kind == GeneratorType.Kind.FUSION && (sameItem(ingredient, ModItems.deuteriumCell)
                    || sameItem(ingredient, ModItems.component("liBlanketModule")))) {
                arecipes.add(e);
            } else if (e.type.kind == GeneratorType.Kind.SINGULAR && com.sc.tileentity.SingularReactorSC.isCapsule(ingredient)) {
                arecipes.add(e);
            } else if (furnaceFuel && e.isSolidFuel()) {
                arecipes.add(e);
            }
        }
    }

    private static ItemStack oneOf(ItemStack s) {
        ItemStack c = s.copy();
        c.stackSize = 1;
        return c;
    }

    private static boolean sameItem(ItemStack stack, net.minecraft.item.Item item) {
        return item != null && stack.getItem() == item;
    }

    // ---- drawing ----

    @Override
    public void drawBackground(int recipe) {
        GenEntry e = (GenEntry) arecipes.get(recipe);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GuiDraw.changeTexture(SHEET);
        if (e.fuelStacks.size() > 0) {
            pocket(FUEL_X, SLOT_Y);
        }
        if (e.fuelStacks.size() > 1) {
            pocket(FUEL2_X, SLOT_Y);
        }
        pocket(GEN_X, SLOT_Y);
        gui.drawTexturedModalRect(ARROW_X, ARROW_Y, GuiGaugeSC.SPR_ARROW_R_U, GuiGaugeSC.SPR_ARROW_R_V, 24, 17);
    }

    private void pocket(int x, int y) {
        gui.drawTexturedModalRect(x - 1, y - 1, GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
    }

    @Override
    public void drawExtras(int recipe) {
        GenEntry e = (GenEntry) arecipes.get(recipe);
        GuiDraw.changeTexture(SHEET);
        GuiGaugeSC.drawSpriteHorizontal(gui, ARROW_X, ARROW_Y, GuiGaugeSC.SPR_ARROW_R_FULL_U, GuiGaugeSC.SPR_ARROW_R_V,
                24, 17, (cycleticks % 40) / 40F);
        // the Tokamak XV: XV, or SV when what is beside it takes SV (TileEntityGeneratorSC.tokamakOutputFor)
        String tier = e.type == GeneratorType.TOKAMAK_XV ? e.type.tier.name() + "/" + com.sc.energy.Tier.SV.name() : e.type.tier.name();
        GuiDraw.drawString(Lang.tr("sc.nei.gen.output", e.type.euPerTick, tier), 4, 40, 0x404040, false);
        String[] lines = e.details();
        for (int i = 0; i < lines.length; i++) {
            GuiDraw.drawString(lines[i], 4, 50 + i * 9, 0x606060, false);
        }
    }

    // ---- one displayed generator ----

    public class GenEntry extends CachedRecipe {

        final GeneratorType type;
        /** The shown fuel: a fluid drop, a furnace fuel, or null (solar / fusion). */
        final ItemStack fuel;
        final List<PositionedStack> fuelStacks = new ArrayList<PositionedStack>();
        private final ItemStack solidFuel;
        private final PositionedStack result;

        GenEntry(GeneratorType type, ItemStack fuel) {
            this.type = type;
            this.fuel = fuel;
            this.solidFuel = fuel != null && ItemFluidDropSC.fluidOf(fuel) == null ? fuel : null;
            if (type.kind == GeneratorType.Kind.FUSION) {
                fuelStacks.add(new PositionedStack(new ItemStack(ModItems.deuteriumCell), FUEL_X, SLOT_Y));
                fuelStacks.add(new PositionedStack(new ItemStack(ModItems.component("liBlanketModule")), FUEL2_X, SLOT_Y));
            } else if (type.kind == GeneratorType.Kind.SINGULAR) {
                net.minecraft.item.Item cap = ModItems.component("matterCapsule");
                if (cap != null) {
                    fuelStacks.add(new PositionedStack(new ItemStack(cap), FUEL_X, SLOT_Y));
                }
                fuelStacks.add(new PositionedStack(new ItemStack(ModBlocks.gravityCoil, 16), fuelStacks.isEmpty() ? FUEL_X : FUEL2_X, SLOT_Y));
            } else if (fuel != null) {
                fuelStacks.add(new PositionedStack(fuel, FUEL_X, SLOT_Y));
                if (type.kind == GeneratorType.Kind.DUAL_FLUID && type.fuel2FluidName != null) {
                    ItemStack drop2 = ItemFluidDropSC.stackOf(FluidRegistry.getFluid(type.fuel2FluidName));
                    if (drop2 != null) {
                        fuelStacks.add(new PositionedStack(drop2, FUEL2_X, SLOT_Y));
                    }
                }
            }
            result = new PositionedStack(generatorStack(), GEN_X, SLOT_Y);
        }

        boolean isSolidFuel() {
            return solidFuel != null;
        }

        ItemStack generatorStack() {
            return ModBlocks.generatorStack(type, 1);
        }

        String[] details() {
            switch (type.kind) {
                case PASSIVE:
                    return new String[]{Lang.tr("sc.nei.gen.solar")};
                case FUSION:
                    long cellEu = (long) TileEntityGeneratorSC.CELL_BURN_TICKS * type.euPerTick;
                    return new String[]{
                            Lang.tr("sc.nei.gen.fusion1", String.valueOf(type.ignitionThreshold()),
                                    TileEntityGeneratorSC.MODULE_LIFE_TICKS / 72000),
                            Lang.tr("sc.nei.gen.fusion2", TileEntityGeneratorSC.CELL_BURN_TICKS / 1200, String.valueOf(cellEu))};
                case SOLID:
                    if (solidFuel != null) {
                        int t = Math.max(1, TileEntityFurnace.getItemBurnTime(solidFuel) / TileEntityGeneratorSC.SOLID_GEN_DIVISOR);
                        return new String[]{Lang.tr("sc.nei.gen.solid", seconds(t), String.valueOf((long) t * type.euPerTick))};
                    }
                    return new String[]{Lang.tr("sc.manual.gen.kind.solid")};
                case SINGULAR:
                    return new String[]{
                            Lang.tr("sc.nei.gen.sing1", com.sc.tileentity.SingularReactorSC.IGNITION_EU / 1000000,
                                    com.sc.tileentity.SingularReactorSC.D_IGNITION, com.sc.tileentity.SingularReactorSC.MAX_OUTPUT),
                            Lang.tr("sc.nei.gen.sing2", (int) com.sc.tileentity.SingularReactorSC.HE_PER_TICK)};
                case WIND:
                case WATER:
                case THERMO:
                case RTG:
                case EXO:
                case CREATIVE:
                case DUAL_FLUID:
                    return new String[]{Lang.tr("sc.manual.gen.kind." + type.kind.name().toLowerCase(java.util.Locale.ROOT))};
                default:
                    if (solidFuel != null) {
                        int ticks = Math.max(1, TileEntityFurnace.getItemBurnTime(solidFuel) / TileEntityGeneratorSC.SOLID_FUEL_DIVISOR);
                        return new String[]{Lang.tr("sc.nei.gen.solid", seconds(ticks), String.valueOf((long) ticks * type.euPerTick))};
                    }
                    int ticks = 1000 / Math.max(1, type.fuelRatePerTick);
                    return new String[]{Lang.tr("sc.nei.gen.fluid", type.fuelRatePerTick, seconds(ticks),
                            String.valueOf((long) ticks * type.euPerTick))};
            }
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return fuelStacks;
        }
    }

    private static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20F);
    }
}
