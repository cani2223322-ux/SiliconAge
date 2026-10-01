package com.sc.proxy;

public class CommonProxy {

    public void preInit() {
    }

    public void init() {
    }

    public void postInit() {
    }

    /**
     * Opens the handbook (§1 of 02_guide_book.md). No-op on the server: GuiManual is a
     * GuiScreen and exists client-side only, which is exactly why this goes through the proxy.
     * The previous shape - a @SideOnly(CLIENT) method called straight from ItemSCManual - left
     * common code holding an invokespecial to a method FML strips on a dedicated server, and
     * only survived because the JVM resolves that call lazily and the branch never ran there.
     */
    public void openManual() {
    }

    /** Sneak + right-click with an energy blade: switch it like its key does. Client only. */
    public void toggleBlade() {
    }

    /** A client connected to another machine's server (not single player, not the LAN host) - ConfigSyncSC. */
    public boolean playsOnRemoteServer() {
        return false;
    }
}
