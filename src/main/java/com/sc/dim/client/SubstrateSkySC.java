package com.sc.dim.client;

import com.sc.dim.SubstrateConfigSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.IRenderHandler;

/**
 * PACKAGE P (docs/plan-substrate-tech-f5.md §10.2, rule ПМ-19; proposal §3 «Небо и свет»: «опция customSky ... сама гаснет при
 * ShadersModCore»): the Substrate's own sky - asked every frame by WorldProviderSubstrateSC.getSkyRenderer (the coordinator's
 * line); null keeps vanilla's sky with the provider's colours (Ф1..Ф4).
 * <p>
 * Fixed (the coordinator's): the class, renderer() and shadersPresent() with their contracts - renderer() is null unless this
 * client's dimension.customSky is on (SubstrateConfigSC.customSky(), never the server's: it isn't a rule) and ShadersModCore isn't
 * loaded (class "shadersmodcore.client.Shaders" - a coremod, not a mod: Loader doesn't list it), looked up once. P writes the
 * renderer itself (an IRenderHandler drawn instead of RenderGlobal.renderSky): the sky's colour from the provider (the storm's
 * violet included), the stars as a circuit grid (short straight segments joining bright points, by a fixed seed - the same every
 * night), a dim ring low on the horizon (the «аккреционный диск»), a small sharp white sun and vanilla's moon phases, the
 * sunrise colours from calcSunriseSunsetColors; display lists built once (and again on a resource reload), no allocation a frame.
 * Client only.
 * <p>
 * P's renderer: SubstrateSkyRenderSC - one for the client, made on the first frame the own sky is asked for (the GL thread), and
 * from then on told of every resource reload (its lists and textures are built again).
 */
@SideOnly(Side.CLIENT)
public final class SubstrateSkySC {

    /** ShadersModCore's main class (its shaders draw the sky themselves). */
    public static final String SHADERS_CLASS = "shadersmodcore.client.Shaders";

    private static Boolean shaders;
    /** The own sky's renderer (made once). */
    private static SubstrateSkyRenderSC sky;

    private SubstrateSkySC() {
    }

    /** ShadersModCore is loaded (looked up once). */
    public static boolean shadersPresent() {
        if (shaders == null) {
            boolean found;
            try {
                Class.forName(SHADERS_CLASS, false, SubstrateSkySC.class.getClassLoader());
                found = true;
            } catch (Throwable t) {
                found = false;
            }
            shaders = Boolean.valueOf(found);
        }
        return shaders.booleanValue();
    }

    /** The own sky now, or null (customSky off, ShadersModCore there). */
    public static IRenderHandler renderer() {
        if (!SubstrateConfigSC.customSky() || shadersPresent()) {
            return null;
        }
        if (sky == null) {
            sky = new SubstrateSkyRenderSC();
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            if (rm instanceof IReloadableResourceManager) {
                ((IReloadableResourceManager) rm).registerReloadListener(sky);     // calls it once now: built on its first frame anyway
            }
        }
        return sky;
    }

    /** P's renderer may use it: the client's world, or null. */
    static WorldClient world() {
        return Minecraft.getMinecraft().theWorld;
    }
}
