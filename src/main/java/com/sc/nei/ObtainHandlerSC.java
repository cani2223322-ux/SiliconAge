package com.sc.nei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import com.sc.block.BlockMachineSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateOreTableSC;
import com.sc.emp.EmpTooltipSC;
import com.sc.energy.GeneratorType;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemFluidDropSC;
import com.sc.item.ItemSingularCrumbSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.BookContent;
import com.sc.manual.BookWorldSC;
import com.sc.manual.Lang;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.MobRecipesSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.travel.TravelRulesSC;
import com.sc.util.ConfigSC;
import com.sc.util.Material;
import com.sc.util.OreEntry;
import com.sc.util.SiliconMaterial;
import com.sc.worldgen.OreGenSC;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * NEI page «Как получить» (R) for the items of the mod no recipe makes: the ores and limestone (where they
 * generate, from the config), the singularity crumb (the Singular drill's black hole), scrap dust (machine
 * defects), the creative generator, the fluids of other mods that the mod's generators burn, the Substrate's
 * rock, ores and druse (generated there), its trophies and combat loot (off its mobs) and the spawn eggs (creative
 * only), the settlements' Credit Chip, Return Capsule and Settlement Map (Ф2: the settlers and the Cartographer's
 * deal), Ф3's ruin blocks (the Dead Nodes and the lithographs), its loot (who drops it, who buys it) and its trophies'
 * restoring (the Armourer, one's own station - BookWorldSC.restoreLine), and - in a pack that unifies metals (UniDict) -
 * the mod's ingots and dusts it swaps for another mod's.
 */
public class ObtainHandlerSC extends TemplateRecipeHandler {

    static final String ID = "sc.obtain";
    private static final int W = 166, H = 124, ICON_X = 4, ICON_Y = 4, REL_X = 30, TEXT_Y = 25, TEXT_W = 158;
    /** The text is drawn at this scale (the holo screens' small print): a line takes LINE_H pixels. */
    private static final float K = 0.75F;
    private static final int LINE_H = 7;

    @Override
    public String getRecipeName() {
        return Lang.tr("sc.nei.obtain.title");
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
        return 1;
    }

    @Override
    public List<Class<? extends GuiContainer>> getRecipeTransferRectGuis() {
        return new ArrayList<Class<? extends GuiContainer>>();
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        Page p = pageFor(result);
        if (p != null) {
            arecipes.add(p);
        }
    }

    // ------------------------------------------------------------------ which item, which page

    Page pageFor(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        Item item = s.getItem();
        if (item == Item.getItemFromBlock(ModBlocks.oreSC)) {
            return orePage(s, OreEntry.byMeta(s.getItemDamage()));
        }
        if (item == Item.getItemFromBlock(ModBlocks.limestoneSC)) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.world"));
            p.text(Lang.tr("sc.manual.ores.where", OreGenSC.LIMESTONE_MIN_Y, OreGenSC.LIMESTONE_MAX_Y, OreGenSC.LIMESTONE_VEIN_SIZE,
                    OreGenSC.LIMESTONE_VEINS_PER_CHUNK) + com.sc.block.ItemBlockOreSC.dimensionsSuffix());
            p.text(Lang.tr("sc.manual.ores.biomes", Lang.tr("sc.manual.ores.anybiome")));
            p.text(Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool.stone")));
            p.text(Lang.tr("sc.nei.obtain.limestone"));
            p.rel(new ItemStack(Items.stone_pickaxe)).rel(BlockMachineSC.stackOf(MachineType.BLAST_FURNACE, 1));
            return p;
        }
        if (item == ModItems.singularCrumb) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.tooltip.singcrumb.details", ItemSingularCrumbSC.CRUMB_BLOCKS, ItemSingularCrumbSC.ORE_MUL, ItemSingularCrumbSC.STONE_DIV));
            p.rel(new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR)));
            return p;
        }
        if (item == ModItems.dust && ModItems.dust.materialAt(s.getItemDamage()) == Material.SCRAP) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.scrap"));
            p.rel(BlockMachineSC.stackOf(MachineType.CRUSHER, 1));
            return p;
        }
        if (ModBlocks.generatorTypeOf(s) == GeneratorType.CREATIVE) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.creative"));
            return p;
        }
        if (item == ModItems.fluidDrop) {
            return fluidPage(s);
        }
        Page settle = settlementPage(s);
        if (settle != null) {
            return settle;
        }
        Page substrate = substratePage(s);
        if (substrate != null) {
            return substrate;
        }
        if (Loader.isModLoaded("UniDict") && (item == ModItems.ingot || item == ModItems.dust || item == ModItems.dustTiny)) {
            return unifiedPage(s);
        }
        return null;
    }

    private Page orePage(ItemStack s, OreEntry ore) {
        ConfigSC.OreGenSettings g = BookContent.gen(ore);
        Page p = new Page(s);
        p.text(Lang.tr("sc.nei.obtain.world"));
        if (g.veinsPerChunk <= 0) {
            p.text(Lang.tr("sc.nei.obtain.off"));
        } else {
            p.text(Lang.tr("sc.manual.ores.where", g.minY, g.maxY, g.veinSize, g.veinsPerChunk) + com.sc.block.ItemBlockOreSC.dimensionsSuffix());
        }
        p.text(Lang.tr("sc.manual.ores.biomes", BookContent.biomes(ore)));
        p.text(Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool." + ore.tool.name().toLowerCase(java.util.Locale.ROOT))));
        p.text(Lang.tr("sc.nei.obtain.ore"));
        p.rel(BookContent.pickaxe(ore)).rel(BlockMachineSC.stackOf(MachineType.CRUSHER, 1));
        return p;
    }

    // ------------------------------------------------------------------ the Substrate (Si-14, docs/plan-substrate-tech.md §7.3)

    /** The Substrate's rock / ores / druse (generated there), its trophies and loot (off its mobs), the spawn eggs; null: none of them. */
    private Page substratePage(ItemStack s) {
        Item item = s.getItem();
        int meta = s.getItemDamage();
        if (blockItem(item, SubstrateBlocksSC.rock)) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.substrate"));
            p.text(BookWorldSC.rockLine(meta));
            p.rel(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1));
            if (meta == SubstrateBlocksSC.CRYSTALLITE || meta == SubstrateBlocksSC.QUARTZ_TURF) {
                p.rel(ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND));
            } else if (meta == SubstrateBlocksSC.COPPER_TRACE || meta == SubstrateBlocksSC.TRACE_NODE) {
                p.rel(ModItems.crushedOre.stackOf(Material.COPPER));
            }
            return p;
        }
        if (blockItem(item, SubstrateBlocksSC.ore)) {
            OreEntry ore = OreEntry.byMeta(meta);
            ConfigSC.OreGenSettings g = BookContent.gen(ore);
            float mul = SubstrateOreTableSC.veinMultiplier(ore);
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.substrate"));
            if (g.veinsPerChunk <= 0 || mul <= 0F) {
                p.text(Lang.tr("sc.nei.obtain.off"));
            } else {
                p.text(Lang.tr("sc.nei.obtain.subore", SubstrateOreTableSC.minY(ore), SubstrateOreTableSC.maxY(ore), g.veinSize,
                        EmpTooltipSC.decimal(g.veinsPerChunk * mul), EmpTooltipSC.decimal(mul)));
            }
            p.text(Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool." + ore.tool.name().toLowerCase(java.util.Locale.ROOT))));
            p.text(Lang.tr("sc.nei.obtain.subore.drop"));
            p.rel(new ItemStack(ModBlocks.oreSC, 1, ore.meta())).rel(BookContent.pickaxe(ore)).rel(BlockMachineSC.stackOf(MachineType.CRUSHER, 1));
            return p;
        }
        if (blockItem(item, SubstrateBlocksSC.druse)) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.substrate"));
            p.text(Lang.tr("sc.manual.world.block.druse"));
            p.rel(ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND));
            return p;
        }
        // Ф3 (docs/plan-substrate-tech-f3.md §10.3): the ruin blocks - the Dead Nodes' and the lithographs', broken into scrap
        if (blockItem(item, RuinBlocksSC.ruin)) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.ruin"));
            p.text(BookWorldSC.ruinLine(meta));
            p.rel(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1));
            p.rel(MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1));
            return p;
        }
        if (MobItemsSC.trophy != null && item == MobItemsSC.trophy) {
            String from = BookWorldSC.trophyDroppers(meta), restore = BookWorldSC.restoreLine(meta);
            Page p = new Page(s);
            p.text(from == null ? Lang.tr("sc.nei.obtain.trophy.later") : Lang.tr("sc.nei.obtain.trophy", from, BookWorldSC.trophyChancePct(meta)));
            p.text(Lang.tr("sc.nei.obtain.mobrules"));
            p.text(restore != null ? restore : Lang.tr("sc.nei.obtain.trophy.norestore"));     // Ф3 (ПТ-11, ПТ-12): where and for what
            p.rel(BookWorldSC.realOfTrophy(meta));
            if (MobItemsSC.spawnEgg != null) {
                p.rel(new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TUNER));          // the Armourer
            }
            return p;
        }
        if (MobItemsSC.combatLoot != null && item == MobItemsSC.combatLoot) {
            boolean cargo = meta == BookWorldSC.LOOT_STOLEN_CARGO;          // Ф2: back to an Elder, never into the Crusher
            ItemCombatLootSC.LootKind kind = ItemCombatLootSC.LootKind.of(meta);
            Page p = new Page(s);
            p.text(BookWorldSC.lootLine(meta));
            p.text(Lang.tr("sc.nei.obtain.mobrules"));
            p.rel(BookWorldSC.lootEgg(meta));
            if (cargo || MobRecipesSC.inputCount(kind) <= 0) {
                // the Elder takes the cargo back; Ф3's loot the Crusher doesn't take goes to the Salvager (ПО-12)
                p.rel(MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TUNER));
            } else {
                p.rel(BlockMachineSC.stackOf(MachineType.CRUSHER, 1));
            }
            return p;
        }
        if (MobItemsSC.spawnEgg != null && item == MobItemsSC.spawnEgg) {
            Page p = new Page(s);
            p.text(Lang.tr(meta == MobEntitiesSC.ID_TUNER || meta == MobEntitiesSC.ID_WARDEN ? "sc.nei.obtain.egg.settle" : "sc.nei.obtain.egg"));
            return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ the settlements (Подложка Ф2, docs/plan-substrate-tech-f2.md §10.3)

    /**
     * The Credit Chip (the settlers' pay, the first pass), the Return Capsule and the Settlement Map (the Cartographer's
     * deal of TradeTableSC: its chips, level, limit), and what only the settlement generator places (hidden in NEI, but
     * R in cheat mode still asks): the core, the showcases, the dock, the trim, the Settlement Brick. Null: none of them.
     */
    private Page settlementPage(ItemStack s) {
        Item item = s.getItem();
        if (SettlementItemsSC.creditChip != null && item == SettlementItemsSC.creditChip) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.creditChip", ReputationSC.PASS_CHIPS));
            p.text(Lang.tr("sc.nei.obtain.creditChip.limit", TradeRulesSC.CHIPS_PER_DAY));
            p.text(Lang.tr("sc.nei.obtain.settlerules"));
            if (MobItemsSC.spawnEgg != null) {
                p.rel(new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TUNER));
            }
            return p;
        }
        if (SettlementItemsSC.returnCapsule != null && item == SettlementItemsSC.returnCapsule
                || SettlementItemsSC.settlementMap != null && item == SettlementItemsSC.settlementMap) {
            Page p = new Page(s);
            String seller = BookWorldSC.sellerLine(s);
            p.text(seller != null ? seller : Lang.tr("sc.nei.obtain.cartographer"));
            p.text(item == SettlementItemsSC.returnCapsule ? Lang.tr("sc.tooltip.returnCapsule.details", TravelRulesSC.CAPSULE_TICKS / 20)
                    : Lang.tr("sc.tooltip.settlementMap.details", TravelRulesSC.MAP_DOCKS));
            p.rel(SettlementItemsSC.chips(1));
            return p;
        }
        boolean placed = blockItem(item, SettlementBlocksSC.core) || blockItem(item, SettlementBlocksSC.showcase) || blockItem(item, SettlementBlocksSC.dock)
                || blockItem(item, SettlementBlocksSC.decor) || blockItem(item, SettlementBlocksSC.glass) || blockItem(item, SettlementBlocksSC.showcase2)
                || blockItem(item, SubstrateBlocksSC.rock) && s.getItemDamage() == BookWorldSC.BRICK;
        if (placed) {
            Page p = new Page(s);
            p.text(Lang.tr("sc.nei.obtain.settlement"));
            p.rel(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1));
            return p;
        }
        return null;
    }

    private static boolean blockItem(Item item, Block b) {
        return b != null && item != null && item == Item.getItemFromBlock(b);
    }

    /** Only the fluids no recipe of the mod makes and the mod doesn't register itself. */
    private Page fluidPage(ItemStack s) {
        FluidStack f = ItemFluidDropSC.fluidOf(s);
        if (f == null || f.getFluid() == null || ownFluids().contains(f.getFluid().getName())) {
            return null;
        }
        Page p = new Page(s);
        p.text(Lang.tr("sc.nei.obtain.fluid", f.getFluid().getLocalizedName(f)));
        double eu = GeneratorType.COMBUSTION.euPerMb(f.getFluid().getName());
        if (eu > 0) {
            p.text(Lang.tr("sc.nei.obtain.fluid.burn", eu == Math.rint(eu) ? String.valueOf((long) eu) : String.valueOf(eu)));
            p.rel(ModBlocks.generatorStack(GeneratorType.COMBUSTION, 1));
        }
        p.text(Lang.tr("sc.nei.obtain.fluid.more"));
        return p;
    }

    private static Set<String> own;

    /** Fluids the mod makes (a machine recipe's output) or registers (ModFluids' fields). */
    static Set<String> ownFluids() {
        if (own == null) {
            Set<String> set = new HashSet<String>();
            for (MachineType type : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                    for (FluidStack o : new FluidStack[]{r.fluidOutputA, r.fluidOutputB}) {
                        if (o != null && o.getFluid() != null) {
                            set.add(o.getFluid().getName());
                        }
                    }
                }
            }
            for (java.lang.reflect.Field fd : com.sc.init.ModFluids.class.getFields()) {
                try {
                    Object v = java.lang.reflect.Modifier.isStatic(fd.getModifiers()) ? fd.get(null) : null;
                    if (v instanceof Fluid) {
                        set.add(((Fluid) v).getName());
                    }
                } catch (Exception e) {
                    // not a fluid field
                }
            }
            own = set;
        }
        return own;
    }

    /** A pack that unifies metals: ours swapped for another mod's - which one, and that every recipe takes either. */
    private Page unifiedPage(ItemStack s) {
        String name = com.sc.init.OreRecipes.sharedName(s);
        if (name == null) {
            return null;
        }
        List<ItemStack> others = new ArrayList<ItemStack>();
        for (ItemStack o : OreDictionary.getOres(name)) {
            GameRegistry.UniqueIdentifier id = o.getItem() == null ? null : GameRegistry.findUniqueIdentifierFor(o.getItem());
            if (id != null && !"SiliconAge".equals(id.modId)) {
                ItemStack c = o.copy();
                if (c.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                    c.setItemDamage(0);
                }
                others.add(c);
            }
        }
        if (others.isEmpty()) {
            return null;
        }
        Page p = new Page(s);
        p.text(Lang.tr("sc.nei.obtain.unidict", others.get(0).getDisplayName()));
        p.relCycle(others);
        return p;
    }

    /** Developer check (NeiCoverageSC): how far a page's text runs past its height, in pixels (<= 0: it fits); -999 no page. */
    public static int overflow(ItemStack s) {
        ObtainHandlerSC h = new ObtainHandlerSC();
        Page p = h.pageFor(s);
        if (p == null) {
            return -999;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int y = TEXT_Y;
        for (String t : p.lines) {
            y += mc.fontRenderer.listFormattedStringToWidth(t, (int) (TEXT_W / K)).size() * LINE_H + 2;
        }
        return y - 2 - H;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        com.sc.inventory.GuiHoloSC.screen(0, 0, W, H);
        Page p = (Page) arecipes.get(recipe);
        com.sc.inventory.GuiHoloSC.slot(ICON_X, ICON_Y, true);
        for (int i = 0; i < p.related.size(); i++) {
            com.sc.inventory.GuiHoloSC.slot(REL_X + i * 18, ICON_Y, false);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void drawExtras(int recipe) {
        Page p = (Page) arecipes.get(recipe);
        Minecraft mc = Minecraft.getMinecraft();
        int y = TEXT_Y;
        for (String t : p.lines) {
            @SuppressWarnings("unchecked")
            List<String> wrapped = mc.fontRenderer.listFormattedStringToWidth(t, (int) (TEXT_W / K));
            for (String w : wrapped) {
                if (y + LINE_H > H) {
                    break;
                }
                GL11.glPushMatrix();
                GL11.glTranslatef(4F, y, 0F);
                GL11.glScalef(K, K, 1F);
                GuiDraw.drawString(w, 0, 0, com.sc.inventory.GuiHoloSC.VALUE, false);
                GL11.glPopMatrix();
                y += LINE_H;
            }
            y += 2;
        }
        com.sc.inventory.GuiHoloSC.glint(0, 0, W, H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ one page

    public class Page extends CachedRecipe {
        final PositionedStack result;
        final List<PositionedStack> related = new ArrayList<PositionedStack>();
        final List<String> lines = new ArrayList<String>();

        Page(ItemStack item) {
            ItemStack c = item.copy();
            c.stackSize = 1;
            result = new PositionedStack(c, ICON_X, ICON_Y);
        }

        Page text(String s) {
            lines.add(s);
            return this;
        }

        Page rel(ItemStack s) {
            if (s != null && related.size() < 7) {
                related.add(new PositionedStack(s, REL_X + related.size() * 18, ICON_Y));
            }
            return this;
        }

        Page relCycle(List<ItemStack> all) {
            if (!all.isEmpty() && related.size() < 7) {
                related.add(new PositionedStack(all, REL_X + related.size() * 18, ICON_Y));
            }
            return this;
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return getCycledIngredients(Minecraft.getMinecraft().thePlayer == null ? 0
                    : (int) (Minecraft.getMinecraft().thePlayer.ticksExisted / 20), related);
        }
    }
}
