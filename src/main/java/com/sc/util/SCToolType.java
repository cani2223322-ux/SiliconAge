package com.sc.util;

/**
 * The silicon-chain consumable tools from §13.5 (durability, not metadata-subtyped - vanilla's
 * damage value IS the durability counter, so each tool needs its own Item instance rather than
 * sharing one metadata item the way ItemMaterialSC's dusts/ingots do).
 */
public enum SCToolType {

    DIAMOND_WIRE("diamondWire", 64),
    DIAMOND_BLADE("diamondBlade", 64),
    SEED_CRYSTAL("seedCrystal", 200),
    PHOTOMASK("photomask", 16),
    SPUTTER_TARGET_COPPER("sputterTargetCopper", 32),
    SPUTTER_TARGET_ALUMINIUM("sputterTargetAluminium", 32),
    SPUTTER_TARGET_TUNGSTEN("sputterTargetTungsten", 32),

    // Rolling Machine press molds (§2: "форма - NBT-параметр"). Several products come off the
    // press from the same metal - three lead frames and a sputter target from copper, a turbine
    // blade and a plate from titanium - and telling them apart by ingot count meant a player who
    // loaded a full stack only ever got one of them. The mold sitting in an input slot is what
    // selects the product; it wears like any other §13.5 tool. TODO: durability by analogy.
    MOLD_PLATE("moldPlate", 256),
    MOLD_BLADE("moldBlade", 256),
    MOLD_COIL("moldCoil", 256),
    MOLD_TARGET("moldTarget", 256),
    MOLD_LEAD_FRAME_3("moldLeadFrame3", 256),
    MOLD_LEAD_FRAME_16("moldLeadFrame16", 256),
    MOLD_LEAD_FRAME_40("moldLeadFrame40", 256);

    public final String textureName;
    public final int durability;

    SCToolType(String textureName, int durability) {
        this.textureName = textureName;
        this.durability = durability;
    }
}
