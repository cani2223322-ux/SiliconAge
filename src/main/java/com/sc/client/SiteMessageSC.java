package com.sc.client;

import com.sc.util.ConfigSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * «У мода есть свой сайт»: on every world / server join, a few seconds in (after «Есть обновление», if any), the chat
 * tells about silicon-age.site - what's there and [Открыть сайт] [Гайды] [Справочник]. Only this player sees it;
 * off with site.message = false in the config.
 */
@SideOnly(Side.CLIENT)
public final class SiteMessageSC {

    public static final SiteMessageSC INSTANCE = new SiteMessageSC();
    public static final String SITE = "https://silicon-age.site/";
    private static final int DELAY_TICKS = 120;
    /** The update check is given this long to answer first, then the site line goes anyway. */
    private static final int MAX_WAIT_TICKS = 400;

    private boolean told;
    private int inWorld;

    private SiteMessageSC() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            inWorld = 0;                                    // back in the menu: the next join tells again
            told = false;
            return;
        }
        if (told || ++inWorld < DELAY_TICKS || (!UpdateClientSC.settled() && inWorld < MAX_WAIT_TICKS)) {
            return;
        }
        told = true;
        if (!ConfigSC.siteMessage) {
            return;
        }
        IChatComponent m = new ChatComponentText("[Silicon Age] ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE));
        m.appendSibling(new ChatComponentTranslation("sc.site.msg.title")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
        m.appendSibling(new ChatComponentText(" "));
        m.appendSibling(link("sc.site.msg.address", SITE));
        mc.thePlayer.addChatMessage(m);
        mc.thePlayer.addChatMessage(new ChatComponentTranslation("sc.site.msg.desc")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        IChatComponent links = new ChatComponentText(" ");
        links.appendSibling(link("sc.site.msg.open", SITE));
        links.appendSibling(new ChatComponentText("  "));
        links.appendSibling(link("sc.site.msg.guides", SITE + "guides.html"));
        links.appendSibling(new ChatComponentText("  "));
        links.appendSibling(link("sc.site.msg.wiki", SITE + "wiki.html"));
        mc.thePlayer.addChatMessage(links);
    }

    private static IChatComponent link(String key, String url) {
        return new ChatComponentTranslation(key).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(url))));
    }
}
