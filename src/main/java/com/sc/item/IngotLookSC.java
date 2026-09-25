package com.sc.item;

import com.sc.util.Material;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.init.Items;
import net.minecraft.util.IIcon;

/**
 * Ingots are drawn with vanilla's own iron-ingot icon, tinted per metal - the same shape as a
 * vanilla ingot (and whatever the player's resource pack makes of it), in the metal's colour.
 * Vanilla's iron ingot is pure greyscale, so the tint multiplies cleanly: 0xFFFFFF is iron.
 * The mod ships no copy of Mojang's texture; it borrows the stitched icon at runtime.
 */
public final class IngotLookSC {

    private IngotLookSC() {
    }

    @SideOnly(Side.CLIENT)
    public static IIcon icon() {
        return Items.iron_ingot.getIconFromDamage(0);
    }

    /** RGB multiplied onto the grey vanilla ingot. */
    public static int tint(Material material) {
        switch (material) {
            case COPPER: return 0xFF9A5A;
            case TIN: return 0xE6EEF6;
            case LEAD: return 0x9EA0C8;
            case ZINC: return 0xCCDCE4;
            case GERMANIUM: return 0xB8C0CC;
            case TITANIUM: return 0xD8CCF0;
            case TUNGSTEN: return 0x8C96A0;
            case TANTALUM: return 0xA0A8C8;
            case ZIRCONIUM: return 0xEEE6CC;
            case PLATINUM: return 0xF4F6FF;
            case NEODYMIUM: return 0xC4CCB0;
            case LITHIUM: return 0xF0EAF8;
            case ALUMINIUM: return 0xEEF4FA;
            case SILVER: return 0xE8F0FF;
            case GALLIUM: return 0xCCDCEC;
            case INDIUM: return 0xDCD4EC;
            case NIOBIUM: return 0xB0B8E8;
            case HAFNIUM: return 0x98A0B0;
            case PALLADIUM: return 0xEEE8DE;
            case CERIUM: return 0xE8DCB8;
            case LANTHANUM: return 0xE0ECD8;
            case STEEL: return 0x9CA4AC;
            case MAGNESIUM: return 0xF6F6F2;
            default: return 0xFFFFFF;
        }
    }

    /** Nb3Sn (the superconductor alloy ingot, a separate component item). */
    public static final int NB3SN_TINT = 0x98A2C0;
}
