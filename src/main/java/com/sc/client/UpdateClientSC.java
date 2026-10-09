package com.sc.client;

import com.sc.util.UpdateCheckSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * The client's «Есть обновление»: once a game session, a few seconds into the first world, the check's answer
 * goes into the chat - the version, [Скачать] / [Что нового] and up to three lines of what's new in the
 * player's language. Nothing when the jar is up to date, the check is off or the site can't be reached.
 */
@SideOnly(Side.CLIENT)
public final class UpdateClientSC {

    public static final UpdateClientSC INSTANCE = new UpdateClientSC();
    private static final int DELAY_TICKS = 100;

    private boolean told;
    private int inWorld;

    private UpdateClientSC() {
    }

    /** Nothing more will come into the chat this session (said, nothing to say, or the check is off) - SiteMessageSC goes after it. */
    public static boolean settled() {
        return INSTANCE.told || !com.sc.util.ConfigSC.updateCheck;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (told || e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            inWorld = 0;
            return;
        }
        UpdateCheckSC.start();
        if (++inWorld < DELAY_TICKS || !UpdateCheckSC.done()) {
            return;                                         // after the join's own chat, and once the answer is in
        }
        told = true;
        UpdateCheckSC.Info i = UpdateCheckSC.newer();
        if (i == null) {
            return;
        }
        mc.thePlayer.addChatMessage(UpdateCheckSC.message(i));
        boolean ru = "ru".equals(com.sc.manual.Lang.trOr("sc.book.lang", "en"));
        for (String line : ru && i.ru.length > 0 ? i.ru : i.en) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + " - " + line));
        }
    }
}
