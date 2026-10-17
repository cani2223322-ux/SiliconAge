package com.sc.block;

import java.util.List;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * The Substrate's rock as an item: a name per meta (tile.siliconage.substrateRock.<name>.name); the tooltip per meta -
 * what it is; under Shift where it lies, under Ctrl the pickaxe and what it drops.
 */
public class ItemBlockSubstrateRockSC extends ItemBlock {

    public ItemBlockSubstrateRockSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < SubstrateBlocksSC.ROCK_METAS ? damage : SubstrateBlocksSC.CRYSTALLITE;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + SubstrateBlocksSC.rockName(stack.getItemDamage());
    }

    /**
     * Per meta: sc.tooltip.substrateRock.<name>, under Shift .<name>.details, under Ctrl .<name>.howto (the pickaxe, the drop).
     * Ф4: the Monocrystal's howto takes its drop's numbers (1..MONO_DROP_MAX Metallurgical Silicon) as %1$s, %2$s.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String key = "sc.tooltip.substrateRock." + SubstrateBlocksSC.rockName(stack.getItemDamage());
        list.add("§7" + Lang.tr(key));
        String howto = getMetadata(stack.getItemDamage()) == SubstrateBlocksSC.MONOCRYSTAL
                ? Lang.tr(key + ".howto", 1, BlockSubstrateRockSC.MONO_DROP_MAX) : Lang.tr(key + ".howto");
        TooltipSC.more(list, Lang.tr(key + ".details"), howto);
    }
}
