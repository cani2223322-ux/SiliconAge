package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3): the ruin blocks as items - a name per meta (tile.siliconage.ruin.<name>.name);
 * the TooltipSC tooltip per meta: sc.tooltip.ruin.<name> (what it is), under Shift .details (where it is found), under Ctrl
 * .howto (the pickaxe and what it gives - the chances from BlockRuinSC as %s).
 */
public class ItemBlockRuinSC extends ItemBlock {

    public ItemBlockRuinSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockRuinSC.NAMES.length ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockRuinSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int meta = getMetadata(stack.getItemDamage());
        String key = "sc.tooltip.ruin." + BlockRuinSC.nameOf(meta);
        list.add("§7" + Lang.tr(key));
        TooltipSC.more(list, Lang.tr(key + ".details"), Lang.tr(key + ".howto", chance(meta)));
    }

    /** The meta's drop chance for its .howto line (%). */
    private static int chance(int meta) {
        switch (meta) {
            case BlockRuinSC.BURNT_PANEL:
                return BlockRuinSC.PANEL_FIRMWARE_PCT;
            case BlockRuinSC.MACHINE_HUSK:
                return BlockRuinSC.HUSK_TRANSISTOR_PCT;
            case BlockRuinSC.TANK_HUSK:
                return 100;
            default:
                return BlockRuinSC.METAL_SCRAP_PCT;
        }
    }
}
