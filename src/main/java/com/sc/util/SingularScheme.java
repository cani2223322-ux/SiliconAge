package com.sc.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The Singular suit's colour schemes (docs/plan-singular-armor.md §8): kept in each piece's NBT
 * ("SingScheme", the ordinal - append only), A by default. The item icon and the worn model's
 * textures follow it: items/armorSingular<Piece>_<key>.png, models/armor/singular_<key>_layer_N.png
 * and its glow layers. A new scheme = a row here, a palette row in the texture generator
 * (gen_singular_tex.py), its textures and the lang name "sc.singular.scheme.<key>".
 * The Singular station (a later stage) changes it; for now setScheme / the creative tab / NEI.
 */
public enum SingularScheme {

    A(0x221630, 0xBE6EFF),   // Фиолетовая тьма / Violet Dark (default)
    B(0x121216, 0x78E6FF),   // Горизонт событий / Event Horizon
    C(0x280E14, 0xFF465A),   // Багровая / Crimson
    D(0x9696A5, 0x8CC8FF),   // Белый карлик / White Dwarf
    E(0x1A1410, 0xFFAA32),   // Аккреционный диск / Accretion Disk
    F(0x0E1610, 0x5AFF78),   // Тёмная материя / Dark Matter
    G(0x101434, 0xFF50DC),   // Квазар / Quasar
    H(0x0C0C0C, 0xEBEBEB),   // Пустота / Void
    I(0x2C1A0A, 0xFFE678),   // Сверхновая / Supernova
    J(0x0E282E, 0xFF78BE),   // Туманность / Nebula
    K(0x343A42, 0x3C8CFF);   // Нейтронная звезда / Neutron Star

    /** Piece NBT key: the scheme's ordinal. */
    public static final String NBT = "SingScheme";
    public static final SingularScheme DEFAULT = A;

    /** The plating's base colour and the accent (lines, core glow, aura) - RGB, as in the texture generator. */
    public final int base, accent;

    SingularScheme(int base, int accent) {
        this.base = base;
        this.accent = accent;
    }

    /** Lower-case letter: the texture suffix and the lang key's end. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String langKey() {
        return "sc.singular.scheme." + key();
    }

    public static SingularScheme of(int ordinal) {
        SingularScheme[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : DEFAULT;
    }

    /** The scheme of a piece (any stack: no tag or an unknown value - the default). */
    public static SingularScheme of(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT)
                ? of(stack.getTagCompound().getInteger(NBT)) : DEFAULT;
    }

    /** Sets a piece's scheme (the Singular station will call this). */
    public static void setScheme(ItemStack stack, SingularScheme scheme) {
        if (stack == null) {
            return;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger(NBT, (scheme == null ? DEFAULT : scheme).ordinal());
    }
}
