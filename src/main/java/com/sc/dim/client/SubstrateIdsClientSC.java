package com.sc.dim.client;

import com.sc.dim.SubstrateNetSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The client's half of SubstrateNetSC's id check (docs/plan-substrate-tech.md §2.2): the server's Substrate ids
 * differ from this client's config - one chat line, once the player is in the world («id Подложки не совпадают с
 * сервером: ...»; the details are in the log). Shared core: only the coordinator edits this file.
 */
@SideOnly(Side.CLIENT)
public final class SubstrateIdsClientSC {

    public static final SubstrateIdsClientSC INSTANCE = new SubstrateIdsClientSC();

    private SubstrateIdsClientSC() {
    }

    public static void register() {
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        String w = SubstrateNetSC.takeWarning();
        if (w != null) {
            IChatComponent m = new ChatComponentText("[Silicon Age] ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE));
            m.appendSibling(new ChatComponentTranslation("sc.dim.ids.mismatch", w).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            mc.thePlayer.addChatMessage(m);
        }
    }
}
