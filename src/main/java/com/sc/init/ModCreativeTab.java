package com.sc.init;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.DrillType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * The mod's creative tab, in a fixed order of its own - not by item ID. Vanilla lists a tab by
 * the numeric IDs, and a world that hands the mod's items IDs from scattered free slots (an old
 * world after the mod id changed) got them all jumbled. The order: ores -> materials (metal blocks
 * after the ingots) -> parts -> tools and upgrades -> machines -> energy (batteries, wireless, the
 * converter) -> logistics -> field generator -> radiation -> the bridge -> suits, blades and drills
 * tier by tier -> ranged weapons; anything not listed here comes last (nothing is lost).
 */
public class ModCreativeTab extends CreativeTabs {

    public static final CreativeTabs TAB = new ModCreativeTab("siliconage");

    private ModCreativeTab(String label) {
        super(label);
    }

    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(ModBlocks.oreSC);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void displayAllReleventItems(List list) {
        List<Object> order = new ArrayList<Object>();
        // ores and the world
        add(order, ModBlocks.oreSC, ModBlocks.limestoneSC);
        // materials
        add(order, ModItems.crushedOre, ModItems.purifiedCrushedOre, ModItems.dust, ModItems.dustTiny, ModItems.ingot,
                ModBlocks.metalBlock, ModBlocks.metalBlock2, ModItems.siliconMaterial, ModItems.coke, ModItems.rubber, ModItems.rubberBlue, ModItems.rubberHeatResist,
                ModItems.compound, ModItems.alFoil, ModItems.leadFrame3, ModItems.leadFrame16, ModItems.leadFrame40,
                ModItems.liquidHeCell, ModItems.deuteriumCell, ModItems.singularCell, ModItems.singularCrumb, ModItems.singularClot,
                ModItems.fluidBucket);
        // parts, in the order they were registered
        order.addAll(ModItems.COMPONENTS.values());
        // tools and upgrades
        order.addAll(ModItems.TOOLS.values());
        order.addAll(ModItems.WRENCHES);
        add(order, ModItems.upgrade, ModItems.tubeSpeedUpgrade, ModItems.itemFilter, ModItems.armorChip,
                ModItems.fieldLinkModule, ModItems.windRotor, ModItems.isotopeCapsule, ModItems.manual);
        // machines, energy, logistics, the field
        add(order, ModBlocks.machineSC, ModBlocks.machineSC2);
        add(order, ModBlocks.generatorSC, ModBlocks.generatorSC2, ModBlocks.tokamakCoil, ModBlocks.gravityCoil, ModBlocks.cableSC, ModBlocks.transformerSC, ModBlocks.energyStorageSC, ModBlocks.chargePadSC);
        // energy on the move: batteries, wireless links, the converter
        add(order, ModItems.battery, ModBlocks.wirelessTx, ModBlocks.wirelessRx, ModBlocks.quantumTranslator, ModItems.linkCard,
                ModItems.entangledCrystal, ModBlocks.energyConverter, ModItems.converterModule);
        add(order, ModBlocks.quarrySC, ModItems.quarryModule, ModItems.oreScanner, ModItems.areaCard, ModItems.oreLens);
        order.addAll(ModItems.DRILL_HEADS);
        add(order, ModBlocks.pipeSC, ModBlocks.tubeItemPneumatic, ModBlocks.conduitBundle, ModBlocks.tankSC);
        add(order, ModBlocks.fieldGeneratorSC);
        // radiation: lead, the suit, the dosimeter, the radioprotector, the shower
        add(order, ModBlocks.leadBlock, ModBlocks.leadGlass);
        add(order, (Object[]) ModItems.leadSuit);
        add(order, ModItems.dosimeter, ModItems.radioprotector, ModBlocks.shower);
        // the bridge: its parts, the remotes, the coordinator, the armour link module
        add(order, ModBlocks.bridge, ModItems.bridgeRemote, ModItems.coordinator, ModItems.bridgeLinkModule);
        // gear, tier by tier: the suit, its blade, its drill
        for (int tier = 0; tier < ArmorSuit.values().length; tier++) {
            ItemArmorSC[] pieces = ModItems.ARMOR.get(ArmorSuit.values()[tier]);
            if (pieces != null) {
                add(order, (Object[]) pieces);
            }
            if (tier < BladeType.values().length) {               // one blade / drill per suit (Singular too)
                add(order, ModItems.BLADES.get(BladeType.values()[tier]));
            }
            if (tier < DrillType.values().length) {
                add(order, ModItems.DRILLS.get(DrillType.values()[tier]));
            }
        }
        order.addAll(ModItems.WEAPONS.values());
        // the suits' service: the stations, the Singular station's stabiliser
        add(order, ModBlocks.armorStation, ModBlocks.singularStation, ModBlocks.gravStabiliser);

        Set<Item> seen = new HashSet<Item>();
        for (Object o : order) {
            Item item = o instanceof Block ? Item.getItemFromBlock((Block) o) : (Item) o;
            if (item != null && seen.add(item) && item.getCreativeTab() == this) {
                item.getSubItems(item, this, list);
            }
        }
        // whatever this list forgot still shows (at the end, by ID as before)
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            if (item != null && !seen.contains(item) && item.getCreativeTab() == this) {
                item.getSubItems(item, this, list);
            }
        }
    }

    private static void add(List<Object> order, Object... things) {
        for (Object t : things) {
            if (t != null) {
                order.add(t);
            }
        }
    }
}
