package com.sc.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.inventory.GuiMachineSC;
import com.sc.machine.MachineType;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.gui.inventory.GuiContainer;

/**
 * NEI plugin entry point - NEI finds it by its class name (NEI*Config) in any loaded jar, so it
 * is only ever touched when NEI is installed. Registers one recipe page per machine type.
 */
public class NEISiliconAgeConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        java.util.Set<MachineType> covered = java.util.EnumSet.noneOf(MachineType.class);
        for (MachineRecipeHandlerSC handler : MachineHandlersSC.all()) {
            API.registerRecipeHandler(handler);
            API.registerUsageHandler(handler);
            covered.add(handler.getMachineType());
        }
        for (MachineType type : MachineType.values()) {
            if (!covered.contains(type)) {
                cpw.mods.fml.common.FMLLog.warning("[Silicon Age] no NEI handler class for %s - add one to MachineHandlersSC", type);
            }
        }
        // the bundle block itself is never an item you hold - the cable / pipe / tube items are
        API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.conduitBundle));
        GeneratorRecipeHandlerSC generators = new GeneratorRecipeHandlerSC();
        API.registerRecipeHandler(generators);
        API.registerUsageHandler(generators);
        // Clicking the progress arrow of any machine screen opens that machine's recipe page.
        List<Class<? extends GuiContainer>> guis = new ArrayList<Class<? extends GuiContainer>>();
        guis.add(GuiMachineSC.class);
        List<TemplateRecipeHandler.RecipeTransferRect> rects = new ArrayList<TemplateRecipeHandler.RecipeTransferRect>();
        rects.add(new TemplateRecipeHandler.RecipeTransferRect(
                // NEI measures these from (guiLeft + 5, guiTop + 11) on a screen with no overlay
                // (RecipeInfo.getGuiOffset) - unshifted, the rect sat over the output slots.
                new Rectangle(GuiMachineSC.PROGRESS_X - 5, GuiMachineSC.PROGRESS_Y - 11, 16, 16), MachineRecipeHandlerSC.ID_OPEN_MACHINE));
        TemplateRecipeHandler.RecipeTransferRectHandler.registerRectsToGuis(guis, rects);
    }

    @Override
    public String getName() {
        return "Silicon Age";
    }

    @Override
    public String getVersion() {
        return Reference.VERSION;
    }
}
