package com.sc.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * A fluid container in the hand, right-clicked on a machine or a generator: a full one pours into
 * the tank that takes its fluid (a bucket of water into the Ore Washer, diesel into a boiler, a
 * cell of argon into a puller), an empty one takes from the fullest output tank (steam out of a
 * boiler, lye out of the electrolyzer). Buckets, our cells and other mods' containers alike -
 * anything in the FluidContainerRegistry, or an IFluidContainerItem. Creative hands don't change.
 */
public final class FluidHandSC {

    private FluidHandSC() {
    }

    /** Whether the stack is something this pours from or into. */
    public static boolean isContainer(ItemStack held) {
        return held != null && (FluidContainerRegistry.isContainer(held) || held.getItem() instanceof IFluidContainerItem);
    }

    /** Server side: pour or take, then tell the player what happened. */
    public static void use(World world, int x, int y, int z, EntityPlayer player, IFluidHandler te, ItemStack held) {
        boolean creative = player.capabilities.isCreativeMode;
        if (FluidContainerRegistry.isFilledContainer(held)) {                           // a full bucket / cell in
            FluidStack in = FluidContainerRegistry.getFluidForFilledItem(held);
            if (in == null) {
                return;
            }
            if (!te.canFill(ForgeDirection.UNKNOWN, in.getFluid())) {
                tell(player, "sc.chat.fluid.wrong", in);
            } else if (te.fill(ForgeDirection.UNKNOWN, in, false) != in.amount) {
                tell(player, "sc.chat.fluid.nofit", in);
            } else {
                te.fill(ForgeDirection.UNKNOWN, in, true);
                if (!creative) {
                    swap(player, held, FluidContainerRegistry.drainFluidContainer(held));
                }
                splash(world, x, y, z);
                tell(player, "sc.chat.fluid.in", in);
            }
            return;
        }
        if (FluidContainerRegistry.isEmptyContainer(held)) {                            // an empty bucket / cell out
            FluidStack avail = te.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, false);
            ItemStack filled = avail == null ? null : FluidContainerRegistry.fillFluidContainer(avail.copy(), held);
            FluidStack taken = filled == null ? null : FluidContainerRegistry.getFluidForFilledItem(filled);
            FluidStack check = taken == null ? null : te.drain(ForgeDirection.UNKNOWN, taken, false);
            if (check == null || check.amount != taken.amount) {
                player.addChatComponentMessage(new ChatComponentTranslation(avail == null ? "sc.chat.fluid.empty" : "sc.chat.fluid.little"));
                return;
            }
            te.drain(ForgeDirection.UNKNOWN, taken, true);
            if (!creative) {
                swap(player, held, filled);
            }
            splash(world, x, y, z);
            tell(player, "sc.chat.fluid.out", taken);
            return;
        }
        if (held.getItem() instanceof IFluidContainerItem && held.stackSize == 1) {     // tanks-in-an-item
            IFluidContainerItem item = (IFluidContainerItem) held.getItem();
            FluidStack carried = item.getFluid(held);
            if (carried != null && carried.amount > 0) {
                if (!te.canFill(ForgeDirection.UNKNOWN, carried.getFluid())) {
                    tell(player, "sc.chat.fluid.wrong", carried);
                    return;
                }
                int room = te.fill(ForgeDirection.UNKNOWN, carried, false);
                FluidStack poured = room <= 0 ? null : item.drain(held, room, !creative);
                if (poured == null || poured.amount <= 0) {
                    tell(player, "sc.chat.fluid.nofit", carried);
                    return;
                }
                te.fill(ForgeDirection.UNKNOWN, poured, true);
                splash(world, x, y, z);
                tell(player, "sc.chat.fluid.in", poured);
            } else {
                FluidStack avail = te.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, false);
                if (avail == null) {
                    player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.fluid.empty"));
                    return;
                }
                int took = item.fill(held, avail.copy(), true);
                FluidStack drained = took <= 0 ? null : te.drain(ForgeDirection.UNKNOWN, new FluidStack(avail, took), true);
                if (drained != null) {
                    splash(world, x, y, z);
                    tell(player, "sc.chat.fluid.out", drained);
                }
            }
            player.inventoryContainer.detectAndSendChanges();
        }
    }

    private static void tell(EntityPlayer player, String key, FluidStack f) {
        player.addChatComponentMessage(new ChatComponentTranslation(key, f.amount, f.getLocalizedName()));
    }

    private static void splash(World world, int x, int y, int z) {
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.splash", 0.3F, 1.2F);
    }

    /** Takes one of `held` and hands `result` back (in the same slot if it was the last one). */
    private static void swap(EntityPlayer player, ItemStack held, ItemStack result) {
        if (held.stackSize == 1) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
        } else {
            held.stackSize--;
            if (result != null && !player.inventory.addItemStackToInventory(result)) {
                player.dropPlayerItemWithRandomChoice(result, false);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }
}
