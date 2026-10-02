package com.sc;

public final class Reference {

    public static final String MODID = "SiliconAge";
    /**
     * Resource domain (assets/siliconage: textures, lang keys). Minecraft 1.7.10 skips asset folders with
     * capital letters, so it can't be the mod id itself - the same split as IC2 ("IC2" / "ic2").
     */
    public static final String ASSETS = "siliconage";
    public static final String NAME = "Silicon Age: From Wafer to ExoTech";
    public static final String VERSION = "0.1.6";

    public static final String CLIENT_PROXY = "com.sc.proxy.ClientProxy";
    public static final String COMMON_PROXY = "com.sc.proxy.CommonProxy";

    /** Modid IC2 registers itself under - used for Loader.isModLoaded() checks and @Optional annotations. */
    public static final String IC2_MODID = "IC2";

    private Reference() {
    }
}
