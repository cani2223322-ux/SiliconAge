package com.sc.item;

import com.sc.Reference;
import com.sc.util.Material;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * Dusts and tiny dusts drawn with IC2's own iron-dust / small-iron-dust icons, tinted per
 * material - the same look as IC2's dusts (and whatever a resource pack makes of them). Both
 * IC2 icons are pure greyscale, so the tint multiplies cleanly. Without IC2 the mod's own dust
 * textures are used. Nothing of IC2's is copied into the mod; its icons are borrowed at runtime.
 */
public final class DustLookSC {

    private static Boolean ic2Loaded;
    private static ItemStack dust, smallDust;

    private DustLookSC() {
    }

    public static boolean available() {
        if (ic2Loaded == null) {
            ic2Loaded = Loader.isModLoaded(Reference.IC2_MODID);
        }
        return ic2Loaded;
    }

    /** IC2's iron dust (or small iron dust) icon, or null without IC2. */
    @SideOnly(Side.CLIENT)
    public static IIcon icon(boolean tiny) {
        if (!available()) {
            return null;
        }
        if (dust == null) {
            dust = Ic2Lookup.item("ironDust");
            smallDust = Ic2Lookup.item("smallIronDust");
        }
        ItemStack stack = tiny ? smallDust : dust;
        return stack == null || stack.getItem() == null ? null : stack.getItem().getIconFromDamage(stack.getItemDamage());
    }

    /** RGB multiplied onto IC2's grey dust. Metals match their ingots; oxides and the rest their own colour. */
    public static int tint(Material material) {
        switch (material) {
            case ALUMINIUM: return 0xF4F2EE;   // the "dust" is Al2O3 powder
            case MAGNESIUM: return 0xF8F6F0;   // MgO
            case TITANIUM: return 0x9A94A6;    // titanium concentrate (ilmenite, dark)
            case CARBON: return 0x3C3C40;
            case ASH: return 0xB4B0AC;
            case ARSENIC: return 0x8C9098;
            case SCRAP: return 0xA0907C;
            case IRON: return 0xD8CCC4;
            case GOLD: return 0xFFE070;
            case DIAMOND: return 0x9CF4EC;
            default: return IngotLookSC.tint(material);
        }
    }

    /** Isolated so IC2's API class is only touched when IC2 is loaded. */
    private static final class Ic2Lookup {
        static ItemStack item(String name) {
            try {
                return ic2.api.item.IC2Items.getItem(name);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
