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

    /** Opens the Bridge Controller's screen at x y z (client only; the block's click on the client calls it). */
    public void openBridge(int x, int y, int z) {
    }

    /** The server's answer to the bridge screen: its state (client only). */
    public void bridgeState(int x, int y, int z, net.minecraft.nbt.NBTTagCompound state) {
    }

    /** A right-click with a Bridge / Space Remote: its screen (client only). */
    public void openRemote() {
    }

    /** Sneak + right-click with a Coordinator: its name screen (client only). */
    public void openCoordinator() {
    }

    /** The server's answer to a remote's screen / the armour's «Мост» tab (client only). */
    public void bridgeFarState(net.minecraft.nbt.NBTTagCompound state) {
    }

    /** §7б: this client's player came out of a bridge end in the air - the soft landing (client only). */
    public void bridgeSoftLand(int ticks) {
    }

    /** ВП5 / ВП12: an open vortex's centre cell ticks on the client - its particles and hum (client only). */
    public void vortexTick(net.minecraft.tileentity.TileEntity vortex) {
    }

    /** ВП7: an end near this client closed - its vortex shrinks to a point and pops (client only; any thread). */
    public void bridgeCollapse(double x, double y, double z, int kind, int size, int axis, boolean ringless, int stability) {
    }

    /** ВП11: this client's player came through a bridge - the flash at the screen's edges and a trail (client only; any thread). */
    public void bridgeArrive(int kind) {
    }

    /** A client connected to another machine's server (not single player, not the LAN host) - ConfigSyncSC. */
    public boolean playsOnRemoteServer() {
        return false;
    }
}
