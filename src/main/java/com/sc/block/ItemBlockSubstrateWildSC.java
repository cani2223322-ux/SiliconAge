package com.sc.block;

import java.util.List;

import com.sc.dim.SubstrateRadiationSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArcCrackSC;
import com.sc.tileentity.TileEntitySubstrateVentSC;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §3.3): Ф4's world blocks as items (SubstrateWildBlocksSC: singularGlass,
 * substrateFlora, substrateVent) - a name per meta (tile.siliconage.wild.<name>.name: the metas' names are unique over the three); the TooltipSC tooltip per meta:
 * sc.tooltip.wild.<name> (what it is), under Shift .details (where it is found), under Ctrl .howto (what it gives / does - W adds
 * the numbers as arguments). Fixed (the coordinator's): the class (the registry uses it for all three); the rest W's.
 * <p>
 * The numbers (W): the core's and the moss's light; the fumarole's mB a second; the crack's EU/t, reach, drain (in %: its whole
 * and its tenths - «%3$s,%4$s» / «%3$s.%4$s») and damage; the geyser's seconds between discharges, reach, damage and drain; the RTG's level and reach. The glass and
 * Wireweed take none. The texts stay in lang (package D), with the same %s in both languages.
 */
public class ItemBlockSubstrateWildSC extends ItemBlock {

    public ItemBlockSubstrateWildSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    private String[] names() {
        return SubstrateWildBlocksSC.namesOf(field_150939_a);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < names().length ? damage : 0;
    }

    /** The meta's name ("" for a block with none). */
    public String nameOf(int meta) {
        String[] n = names();
        return n.length == 0 ? "" : n[meta >= 0 && meta < n.length ? meta : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int meta = getMetadata(stack.getItemDamage());
        String key = "sc.tooltip.wild." + nameOf(meta);
        list.add("§7" + Lang.tr(key));
        TooltipSC.more(list, Lang.tr(key + ".details"), Lang.tr(key + ".howto", howtoArgs(meta)));
    }

    /** The numbers of the meta's .howto (none: an empty array - Lang.tr formats nothing then). */
    private Object[] howtoArgs(int meta) {
        if (field_150939_a == SubstrateWildBlocksSC.glass) {
            return meta == SubstrateWildBlocksSC.PILLAR_CORE ? new Object[]{BlockSingularGlassSC.CORE_LIGHT} : new Object[0];
        }
        if (field_150939_a == SubstrateWildBlocksSC.flora) {
            return meta == SubstrateWildBlocksSC.DIODE_MOSS ? new Object[]{BlockSubstrateFloraSC.MOSS_LIGHT} : new Object[0];
        }
        if (field_150939_a != SubstrateWildBlocksSC.vent) {
            return new Object[0];
        }
        switch (meta) {
            case SubstrateWildBlocksSC.FUMAROLE:
                return new Object[]{TileEntitySubstrateVentSC.HELIUM_MB_MIN, TileEntitySubstrateVentSC.HELIUM_MB_MAX};
            case SubstrateWildBlocksSC.ARC_CRACK:
                return new Object[]{TileEntityArcCrackSC.EU_PER_TICK, TileEntityArcCrackSC.HAZARD_RADIUS,         // the drain: whole and tenths
                        TileEntityArcCrackSC.DRAIN_TENTHS_PCT / 10, TileEntityArcCrackSC.DRAIN_TENTHS_PCT % 10, TileEntityArcCrackSC.HAZARD_DAMAGE};
            case SubstrateWildBlocksSC.ION_GEYSER:
                return new Object[]{TileEntitySubstrateVentSC.GEYSER_MIN / 20, TileEntitySubstrateVentSC.GEYSER_MAX / 20,
                        TileEntitySubstrateVentSC.GEYSER_RADIUS, TileEntitySubstrateVentSC.GEYSER_DAMAGE, TileEntitySubstrateVentSC.GEYSER_DRAIN_PCT};
            case SubstrateWildBlocksSC.CRACKED_RTG:
                return new Object[]{com.sc.radiation.RadiationSC.fmt(SubstrateRadiationSC.rtgLevel()), SubstrateRadiationSC.RTG_RADIUS};   // ПМ-6
            default:
                return new Object[0];
        }
    }
}
