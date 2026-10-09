package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.radiation.RadiationSC;
import com.sc.radiation.RadiationStateSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/** The radioprotector: swallowed (like food, but also on a full stomach) it takes 30% off the dose; with no dose it is not swallowed (nor spent). */
public class ItemRadioprotectorSC extends Item {

    public static final float DOSE_TAKEN = 30F;
    /** Below this dose (%) it isn't swallowed at all - nothing to take off. */
    public static final float DOSE_MIN = 1F;

    public ItemRadioprotectorSC() {
        setMaxStackSize(16);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".radioprotector");
        setTextureName(Reference.ASSETS + ":radioprotector");
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.eat;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // no dose: not started (the client by its synced copy while it has one, the server by the real dose)
        float dose = !world.isRemote ? RadiationSC.doseOf(player) : RadiationStateSC.fresh() ? RadiationStateSC.dose : DOSE_MIN;
        if (dose < DOSE_MIN) {
            if (!world.isRemote) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.radioprotector.nodose"));
            }
            return stack;
        }
        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote ? RadiationStateSC.fresh() && RadiationStateSC.dose <= 0F : RadiationSC.doseOf(player) <= 0F) {
            if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {   // the dose went while it was swallowed: kept
                player.addChatComponentMessage(new ChatComponentTranslation("sc.radioprotector.nodose"));
                ((net.minecraft.entity.player.EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
            }
            return stack;
        }
        if (!world.isRemote) {
            RadiationSC.setDose(player, RadiationSC.doseOf(player) - DOSE_TAKEN);
            world.playSoundAtEntity(player, "random.burp", 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        return stack;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§a" + Lang.tr("sc.radioprotector.tooltip", (int) DOSE_TAKEN));
    }
}
