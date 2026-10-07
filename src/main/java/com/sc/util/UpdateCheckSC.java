package com.sc.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sc.Reference;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * «Есть обновление»: the latest release from the mod's site (ConfigSC.updateUrl, the site's build writes
 * version.json at each release) - fetched once, on a daemon thread, with short timeouts; nothing about the
 * player is sent. A newer version is told in the chat: on a client once a game session (UpdateClientSC), on a
 * dedicated server to operators as they log in, and in the server log. Off with updates.check = false.
 */
public final class UpdateCheckSC {

    public static final String DEFAULT_URL = "https://silicon-age.site/version.json";
    private static final int TIMEOUT_MS = 5000, MAX_BYTES = 65536;

    /** What version.json says. */
    public static final class Info {
        public String version = "", channel = "beta", url = "", page = "";
        public String[] ru = new String[0], en = new String[0];
    }

    private static volatile Info latest;
    private static volatile boolean started, done;

    private UpdateCheckSC() {
    }

    /** Starts the one check of this game session (no-op when off or already started). */
    public static synchronized void start() {
        if (!ConfigSC.updateCheck || started) {
            return;
        }
        started = true;
        Thread t = new Thread("Silicon Age update check") {
            @Override
            public void run() {
                try {
                    latest = parse(fetch(ConfigSC.updateUrl));
                    Info n = newer();
                    if (n != null) {
                        org.apache.logging.log4j.LogManager.getLogger(Reference.MODID).info(
                                "Silicon Age {} is out (this is {}): {}", n.version, Reference.VERSION, n.url);
                    }
                } catch (Throwable e) {
                    latest = null;                          // no network, no site: nothing to say
                } finally {
                    done = true;
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /** The check has finished (with an answer or not). */
    public static boolean done() {
        return done;
    }

    /** The release to tell about: newer than this jar and in the channel the config wants; null otherwise. */
    public static Info newer() {
        Info i = latest;
        if (i == null || compare(i.version, Reference.VERSION) <= 0) {
            return null;
        }
        if ("stable".equalsIgnoreCase(ConfigSC.updateChannel) && !"stable".equalsIgnoreCase(i.channel)) {
            return null;
        }
        return i;
    }

    private static String fetch(String address) throws Exception {
        java.net.URLConnection c = new URL(address).openConnection();   // http(s), or a local file for a pack's own mirror
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "SiliconAge/" + Reference.VERSION);
        if (c instanceof HttpURLConnection) {
            ((HttpURLConnection) c).setInstanceFollowRedirects(true);
        }
        InputStream in = c.getInputStream();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < MAX_BYTES) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
            if (c instanceof HttpURLConnection) {
                ((HttpURLConnection) c).disconnect();
            }
        }
    }

    /** version.json -> Info; null if it isn't one. Pure (self-test). */
    public static Info parse(String json) {
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject o = root.getAsJsonObject();
            Info i = new Info();
            i.version = str(o, "version");
            if (i.version.isEmpty()) {
                return null;
            }
            i.channel = str(o, "channel").isEmpty() ? "beta" : str(o, "channel");
            i.url = str(o, "url");
            i.page = str(o, "page");
            if (o.has("changes") && o.get("changes").isJsonObject()) {
                JsonObject ch = o.getAsJsonObject("changes");
                i.ru = lines(ch, "ru");
                i.en = lines(ch, "en");
            }
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString().trim() : "";
    }

    private static String[] lines(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return new String[0];
        }
        JsonArray a = o.getAsJsonArray(key);
        int n = Math.min(3, a.size());
        String[] out = new String[n];
        for (int k = 0; k < n; k++) {
            out[k] = a.get(k).getAsString();
        }
        return out;
    }

    /** Version order by number: "0.1.10-beta" > "0.1.9"; the suffix doesn't count. Pure (self-test). */
    public static int compare(String a, String b) {
        int[] x = nums(a), y = nums(b);
        for (int k = 0; k < 3; k++) {
            if (x[k] != y[k]) {
                return x[k] < y[k] ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] nums(String v) {
        int[] out = new int[3];
        String[] p = (v == null ? "" : v.trim().replaceFirst("^[vV]", "")).split("[.\\-]");
        for (int k = 0; k < 3 && k < p.length; k++) {
            try {
                out[k] = Integer.parseInt(p[k]);
            } catch (NumberFormatException e) {
                out[k] = 0;
            }
        }
        return out;
    }

    /** The chat line: «[Silicon Age] Доступна версия X (у вас Y). [Скачать] [Что нового]» - translated on the reader's side. */
    public static IChatComponent message(Info i) {
        IChatComponent m = new ChatComponentText("[Silicon Age] ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE));
        m.appendSibling(new ChatComponentTranslation("sc.update.available", i.version, Reference.VERSION)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
        if (!i.url.isEmpty()) {
            m.appendSibling(new ChatComponentText(" "));
            m.appendSibling(link("sc.update.download", i.url));
        }
        if (!i.page.isEmpty()) {
            m.appendSibling(new ChatComponentText(" "));
            m.appendSibling(link("sc.update.changes", i.page));
        }
        return m;
    }

    private static IChatComponent link(String key, String url) {
        return new ChatComponentTranslation(key).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(url))));
    }

    /** Dedicated server: an operator logging in hears about a newer version. */
    public static void onLogin(EntityPlayer p) {
        net.minecraft.server.MinecraftServer s = net.minecraft.server.MinecraftServer.getServer();
        if (s == null || !s.isDedicatedServer() || p == null || !p.canCommandSenderUseCommand(2, "")) {
            return;
        }
        Info i = newer();
        if (i != null) {
            p.addChatComponentMessage(message(i));
        }
    }
}
