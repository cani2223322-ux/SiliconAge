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
        hideWildBlocks();
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

    /**
     * Ф4 (docs/plan-substrate-tech-f4.md §10.3): what only the world has and nobody holds - every vent (the fumarole, the crack, the
     * geyser, the RTG: unbreakable), the camp's safe shut and open (campSC metas 2..3: unbreakable), the puncture pillar's core
     * (singularGlass meta 1). The flora, the Singular Glass, the monocrystal, the Stolen Field Generator, the stolen panel and pad,
     * the ruin metas 4..6, the relics, the Pelengator, the new loot, the Singular trophies (TrophyTableSC.SHOWN) and the ten eggs
     * stay - each with its «Как получить» page (ObtainHandlerSC). R in cheat mode still asks the hidden ones: they have pages too.
     */
    static void hideWildBlocks() {
        // every meta listed explicitly (the blocks are in no creative tab), then the world's own hidden
        for (net.minecraft.block.Block b : wildBlocks()) {
            API.setItemListEntries(net.minecraft.item.Item.getItemFromBlock(b), wildStacks(b));
        }
        if (com.sc.dim.SubstrateWildBlocksSC.vent != null) {
            for (int m = 0; m < com.sc.dim.SubstrateWildBlocksSC.VENT_NAMES.length; m++) {
                API.hideItem(new net.minecraft.item.ItemStack(com.sc.dim.SubstrateWildBlocksSC.vent, 1, m));
            }
        }
        if (com.sc.ruin.CampBlocksSC.camp != null) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.ruin.CampBlocksSC.camp, 1, com.sc.ruin.CampBlocksSC.CAMP_SAFE));
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.ruin.CampBlocksSC.camp, 1, com.sc.ruin.CampBlocksSC.CAMP_SAFE_OPEN));
        }
        if (com.sc.dim.SubstrateWildBlocksSC.glass != null) {
            API.hideItem(new net.minecraft.item.ItemStack(com.sc.dim.SubstrateWildBlocksSC.glass, 1, com.sc.dim.SubstrateWildBlocksSC.PILLAR_CORE));
        }
    }

    /** Ф4: the wild and camp blocks NEI lists meta by meta (registered ones only). */
    static List<net.minecraft.block.Block> wildBlocks() {
        List<net.minecraft.block.Block> out = new ArrayList<net.minecraft.block.Block>();
        for (net.minecraft.block.Block b : new net.minecraft.block.Block[]{com.sc.dim.SubstrateWildBlocksSC.glass, com.sc.dim.SubstrateWildBlocksSC.flora,
                com.sc.dim.SubstrateWildBlocksSC.vent, com.sc.ruin.CampBlocksSC.stolenField, com.sc.ruin.CampBlocksSC.camp}) {
            if (b != null) {
                out.add(b);
            }
        }
        return out;
    }

    /** Every meta of a wild or camp block (SubstrateWildBlocksSC / CampBlocksSC names; NeiCoverageSC checks them too). */
    static List<net.minecraft.item.ItemStack> wildStacks(net.minecraft.block.Block b) {
        String[] names = b == com.sc.ruin.CampBlocksSC.stolenField || b == com.sc.ruin.CampBlocksSC.camp ? com.sc.ruin.CampBlocksSC.namesOf(b)
                : com.sc.dim.SubstrateWildBlocksSC.namesOf(b);
        List<net.minecraft.item.ItemStack> out = new ArrayList<net.minecraft.item.ItemStack>();
        for (int m = 0; m < Math.max(1, names.length); m++) {
            out.add(new net.minecraft.item.ItemStack(b, 1, m));
        }
        return out;
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
