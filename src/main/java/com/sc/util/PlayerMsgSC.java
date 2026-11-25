package com.sc.util;

import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The server's player helpers shared by the settlement / embassy / arena / mob systems: a real player (a connected EntityPlayerMP, never a
 * FakePlayer - the same test as HazardNetSC.reachable), a coloured chat line to one, a real player online by UUID, a stack given or
 * dropped at the feet. Server side.
 */
public final class PlayerMsgSC {

    private PlayerMsgSC() {
    }

    /** A real, connected player on the server (a FakePlayer, anything else or null - false). */
    public static boolean isReal(Object o) {
        return o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && ((EntityPlayerMP) o).playerNetServerHandler != null;
    }

    /** A chat line (Lang key `key`, its args) in `colour` to a real player; nothing for anyone else. */
    public static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (isReal(p)) {
            p.addChatComponentMessage(new ChatComponentTranslation(key, args).setChatStyle(new ChatStyle().setColor(colour)));
        }
    }

    /** The real player `id` online (any world), or null. */
    public static EntityPlayerMP online(UUID id) {
        MinecraftServer srv = MinecraftServer.getServer();
        if (id == null || srv == null || srv.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** `s` into `p`'s inventory; what doesn't fit dropped at the feet (picked up at once). `s` is used up. */
    public static void giveOrDrop(EntityPlayer p, ItemStack s) {
        if (s == null || s.stackSize <= 0) {
            return;
        }
        if (p.inventory.addItemStackToInventory(s) && s.stackSize <= 0) {
            return;
        }
        if (s.stackSize > 0) {
            EntityItem e = new EntityItem(p.worldObj, p.posX, p.posY + 0.5D, p.posZ, s.copy());
            e.delayBeforeCanPickup = 0;
            p.worldObj.spawnEntityInWorld(e);
            s.stackSize = 0;
        }
    }
}
