package com.sc.settle.travel;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8.4, rule ПР-4): «Капсула возврата» - one use, out of the Substrate only:
 * a right-click there starts TravelRulesSC.CAPSULE_TICKS (10 s) of preparation on the server (TravelEventsSC: a timer
 * per player UUID - not the item-in-use, whose client-only accessors common code may not call); any damage knocks it
 * down; then one capsule is used up and TeleportQueueSC carries the player to where the bridge took them from
 * (SubstrateEntrySC.NBT_FROM in the persistent NBT; none - the Overworld's spawn), to a safe free place, never into a
 * stranger's field. Bought from a Cartographer (8 КЧ). Stack 16. Tooltip sc.tooltip.returnCapsule (the departure point
 * lives in the server's persistent NBT only - the client's tooltip can't show it; the chat says where it goes).
 */
public class ItemReturnCapsuleSC extends Item {

    public ItemReturnCapsuleSC() {
        setMaxStackSize(16);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".returnCapsule");
        setTextureName(Reference.ASSETS + ":returnCapsule");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP && !(player instanceof FakePlayer)) {
            TravelEventsSC.startCapsule((EntityPlayerMP) player);   // the capsule itself goes when the preparation ends
        }
        return stack;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.returnCapsule"));
        TooltipSC.more(list, Lang.tr("sc.tooltip.returnCapsule.details", TravelRulesSC.CAPSULE_TICKS / 20),
                Lang.tr("sc.tooltip.returnCapsule.howto", TravelRulesSC.CAPSULE_TICKS / 20));
    }
}
