package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.CampLayoutSC;
import com.sc.ruin.RuinWorldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.3): the camps' blocks as items (CampBlocksSC: stolenField, campSC) - a name per
 * meta (tile.siliconage.camp.<name>.name), the TooltipSC tooltip sc.tooltip.camp.<name> (+ .details, .howto). Fixed (the
 * coordinator's): the class; the rest C's.
 * <p>
 * The numbers the lines carry (%s, in this order): stolenField - the dome's radius, the scrap, the components' most;
 * stolenSolar - EU a tick by day, at night; stolenPad - none; campSafe / campSafeOpen - the days the camp takes to come back.
 */
public class ItemBlockCampSC extends ItemBlock {

    public ItemBlockCampSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    private String[] names() {
        return CampBlocksSC.namesOf(field_150939_a);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < names().length ? damage : 0;
    }

    public String nameOf(int meta) {
        String[] n = names();
        return n.length == 0 ? "" : n[meta >= 0 && meta < n.length ? meta : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.siliconage.camp." + nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String name = nameOf(getMetadata(stack.getItemDamage()));
        String key = "sc.tooltip.camp." + name;
        list.add("§7" + Lang.tr(key));
        TooltipSC.more(list, Lang.tr(key + ".details", args(name)), Lang.tr(key + ".howto", args(name)));
    }

    /** The numbers of the meta's lines (the class comment). */
    private static Object[] args(String name) {
        if ("stolenField".equals(name)) {
            return new Object[]{CampLayoutSC.FIELD_RADIUS, BlockStolenFieldSC.SCRAP, BlockStolenFieldSC.COMPONENTS_MAX};
        }
        if ("stolenSolar".equals(name)) {
            return new Object[]{TileEntityStolenSolarSC.EU_DAY, TileEntityStolenSolarSC.EU_DAY / 2};
        }
        if ("campSafe".equals(name) || "campSafeOpen".equals(name)) {
            return new Object[]{RuinWorldSC.CAMP_RESTORE_TICKS / 24000L};
        }
        return new Object[0];
    }
}
