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
        for (SmelterRecipeHandlerSC handler : new SmelterRecipeHandlerSC[]{new SmelterRecipeHandlerSC.Electric(), new SmelterRecipeHandlerSC.Induction()}) {
            API.registerRecipeHandler(handler);
            API.registerUsageHandler(handler);
            covered.add(handler.getMachineType());
        }
        CompressorRecipeHandlerSC compressor = new CompressorRecipeHandlerSC();
        API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.lightSC));   // the searchlight's light: not an item to have
        API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.bridgeVortex)); // Р-1: the bridge's vortex, a technical block
        API.registerRecipeHandler(compressor);
        API.registerUsageHandler(compressor);
        covered.add(compressor.getMachineType());
        for (MachineType type : MachineType.values()) {
            if (!covered.contains(type)) {
                cpw.mods.fml.common.FMLLog.warning("[Silicon Age] no NEI handler class for %s - add one to MachineHandlersSC", type);
            }
        }
        // the bundle block itself is never an item you hold - the cable / pipe / tube items are
        API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.conduitBundle));
        hideSettlementBlocks();
        showRuinBlocks();
        // the Energy Converter and its modules: no other energy in this game (no RF API, Mekanism, Galacticraft) - no use, no recipe
        if (!com.sc.energy.ForeignEnergySC.anyPresent()) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.energyConverter));
            for (int i = 0; i < com.sc.item.ItemConverterModuleSC.Kind.values().length; i++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.init.ModItems.converterModule, 1, i));
            }
        }
        GeneratorRecipeHandlerSC generators = new GeneratorRecipeHandlerSC();
        API.registerRecipeHandler(generators);
        API.registerUsageHandler(generators);
        // «Как получить»: the items no recipe makes - ores, limestone, crumbs, scrap, creative, foreign fluids, UniDict's swaps
        API.registerRecipeHandler(new ObtainHandlerSC());
        // the Singular Station's Б-1 conversion: Exo piece + materials -> Singular piece
        SingularConvertHandlerSC convert = new SingularConvertHandlerSC();
        API.registerRecipeHandler(convert);
        API.registerUsageHandler(convert);
        // Clicking the progress bar of any machine screen opens that machine's recipe page.
        List<Class<? extends GuiContainer>> guis = new ArrayList<Class<? extends GuiContainer>>();
        guis.add(GuiMachineSC.class);
        List<TemplateRecipeHandler.RecipeTransferRect> rects = new ArrayList<TemplateRecipeHandler.RecipeTransferRect>();
        rects.add(new TemplateRecipeHandler.RecipeTransferRect(
                // NEI measures these from (guiLeft + 5, guiTop + 11) on a screen with no overlay
                // (RecipeInfo.getGuiOffset) - unshifted, the rect sat over the output slots.
                new Rectangle(GuiMachineSC.PROGRESS_X - 5, GuiMachineSC.PROGRESS_Y - 1 - 11, 52,
                        GuiMachineSC.PROGRESS_H + 2), MachineRecipeHandlerSC.ID_OPEN_MACHINE));
        TemplateRecipeHandler.RecipeTransferRectHandler.registerRectsToGuis(guis, rects);
    }

    /**
     * Подложка Ф2 (docs/plan-substrate-tech-f2.md §10.3): what only the settlement generator places - the core, every
     * showcase machine (Ф3: the second block's too), both dock blocks, the trim (every meta, its glass), the Settlement
     * Brick (the rock's meta BookWorldSC.BRICK). The chip, the
     * capsule, the map, the Stolen Cargo and the eggs stay, with their «Как получить» pages (ObtainHandlerSC).
     */
    static void hideSettlementBlocks() {
        if (com.sc.settle.SettlementBlocksSC.core != null) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.core));
        }
        if (com.sc.settle.SettlementBlocksSC.showcase != null) {
            for (int m = 0; m < com.sc.block.BlockShowcaseSC.NAMES.length; m++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.showcase, 1, m));
            }
        }
        if (com.sc.settle.SettlementBlocksSC.dock != null) {
            for (int m = 0; m < com.sc.block.BlockDockSC.NAMES.length; m++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.dock, 1, m));
            }
        }
        if (com.sc.settle.SettlementBlocksSC.decor != null) {
            for (int m = 0; m < com.sc.block.BlockSettlementDecorSC.NAMES.length; m++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.decor, 1, m));
            }
        }
        if (com.sc.settle.SettlementBlocksSC.glass != null) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.glass));
        }
        if (com.sc.dim.SubstrateBlocksSC.rock != null) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.dim.SubstrateBlocksSC.rock, 1, com.sc.manual.BookWorldSC.BRICK));
        }
        // Ф3 (docs/plan-substrate-tech-f3.md §10.3): the Power Engineer's and the Salvager's showcase machines
        if (com.sc.settle.SettlementBlocksSC.showcase2 != null) {
            for (int m = 0; m < com.sc.block.BlockShowcase2SC.NAMES.length; m++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.settle.SettlementBlocksSC.showcase2, 1, m));
            }
        }
    }

    /**
     * Ф3 (§10.3): the ruin blocks are in no creative tab (found, not made) but NEI lists every meta, each with its «Как
     * получить» page (ObtainHandlerSC: the Dead Nodes and the lithographs, what it breaks into). The new loot, trophies
     * (TrophyTableSC.SHOWN) and eggs come through their items' own getSubItems.
     */
    static void showRuinBlocks() {
        if (com.sc.ruin.RuinBlocksSC.ruin != null) {
            API.setItemListEntries(net.minecraft.item.Item.getItemFromBlock(com.sc.ruin.RuinBlocksSC.ruin), ruinStacks());
        }
    }

    /** Every meta of the ruin block (NeiCoverageSC checks them too). */
    static List<net.minecraft.item.ItemStack> ruinStacks() {
        List<net.minecraft.item.ItemStack> out = new ArrayList<net.minecraft.item.ItemStack>();
        for (int m = 0; com.sc.ruin.RuinBlocksSC.ruin != null && m < com.sc.block.BlockRuinSC.NAMES.length; m++) {
            out.add(new net.minecraft.item.ItemStack(com.sc.ruin.RuinBlocksSC.ruin, 1, m));
        }
        return out;
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
