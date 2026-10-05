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
                boolean accepts = te.canFill(ForgeDirection.UNKNOWN, carried.getFluid());
                int room = accepts ? te.fill(ForgeDirection.UNKNOWN, carried, false) : 0;
                if (room <= 0) {
                    // the target won't take it (or is full): top a part-filled cell up from it instead
                    FluidStack topped = topUp(te, item, held, carried, creative);
                    if (topped != null) {
                        splash(world, x, y, z);
                        tell(player, "sc.chat.fluid.out", topped);
                        player.inventoryContainer.detectAndSendChanges();
                        return;
                    }
                    tell(player, accepts ? "sc.chat.fluid.nofit" : "sc.chat.fluid.wrong", carried);
                    return;
                }
                FluidStack poured = item.drain(held, room, !creative);
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
                int took = item.fill(held, avail.copy(), !creative);     // creative hands don't change
                FluidStack drained = took <= 0 ? null : te.drain(ForgeDirection.UNKNOWN, new FluidStack(avail, took), true);
                if (drained != null) {
                    splash(world, x, y, z);
                    tell(player, "sc.chat.fluid.out", drained);
                }
            }
            player.inventoryContainer.detectAndSendChanges();
        }
    }

    /**
     * A part-filled tank-in-an-item takes more of the same fluid from the handler: the room is
     * simulated on both sides first, the handler is drained for real, and the item gets exactly what
     * came out (nothing out - nothing in, so no dupes). Creative hands don't change. Null: nothing taken.
     */
    public static FluidStack topUp(IFluidHandler te, IFluidContainerItem item, ItemStack held, FluidStack carried, boolean creative) {
        int room = item.getCapacity(held) - carried.amount;
        if (room <= 0) {
            return null;
        }
        FluidStack want = new FluidStack(carried, room);
        int fits = item.fill(held, want.copy(), false);
        if (fits <= 0) {
            return null;
        }
        want = new FluidStack(carried, fits);
        FluidStack avail = te.drain(ForgeDirection.UNKNOWN, want.copy(), false);
        boolean byStack = avail != null && avail.isFluidEqual(carried) && avail.amount > 0;
        if (!byStack) {                                     // handlers that only drain by amount
            avail = te.drain(ForgeDirection.UNKNOWN, fits, false);
            if (avail == null || !avail.isFluidEqual(carried) || avail.amount <= 0) {
                return null;
            }
        }
        int take = Math.min(fits, avail.amount);
        FluidStack drained = byStack ? te.drain(ForgeDirection.UNKNOWN, new FluidStack(carried, take), true)
                : te.drain(ForgeDirection.UNKNOWN, take, true);
        if (drained == null || drained.amount <= 0) {
            return null;
        }
        if (!creative) {
            item.fill(held, drained.copy(), true);
        }
        return drained;
    }

    private static void tell(EntityPlayer player, String key, FluidStack f) {
        // the fluid's name translated by the player's client, not the server
        player.addChatComponentMessage(new ChatComponentTranslation(key, f.amount, new ChatComponentTranslation(f.getFluid().getUnlocalizedName(f))));
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
