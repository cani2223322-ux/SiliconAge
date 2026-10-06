package com.sc.util;

import java.util.ArrayList;
import java.util.List;

import com.sc.item.MaterialItemKind;

/**
 * Every non-Silicon material from 01_recipes.md §11 that needs crushedOre/purifiedCrushedOre/
 * dust/dustTiny/ingot items, per the IC2-style 5-stage naming convention from §11.1.
 *
 * Silicon is deliberately absent (§11.2: it has its own dedicated chain, step 5 territory -
 * see the silicon chain table in §3, not this generic metal pipeline).
 *
 * hasCrushedOre / hasPurifiedCrushedOre / hasDust / hasIngot follow §4's stated processing
 * path per metal; hasPurifiedCrushedOre is only true where §4 names an explicit "Ore Washer"
 * step (Cu, Sn, Pb, Zn, Ge, Pt, Li). hasTinyDust is only true for the Centrifuge byproduct
 * metals §11.2 gives as TINY dusts (Ag, Ga, In, Nb, Hf) plus Ash (§11.4's Carbon-dust
 * byproduct). Pd, Ce and La are Centrifuge byproducts too, but §11.2 has them come out at full
 * size - they used to carry tiny-dust items as well, which nothing could ever produce.
 */
public enum Material {

    // ---- Main-chain metals (100% guaranteed yield from their own ore, §11.2) ----
    COPPER("Copper", true, true, true, false, true, null),
    TIN("Tin", true, true, true, false, true, null),
    LEAD("Lead", true, true, true, false, true, null),
    ZINC("Zinc", true, true, true, false, true, null),
    GERMANIUM("Germanium", true, true, true, false, true, null),
    TITANIUM("Titanium", true, false, true, false, true, null),
    TUNGSTEN("Tungsten", true, false, true, false, true, null),
    TANTALUM("Tantalum", true, false, true, false, true, null),
    ZIRCONIUM("Zirconium", true, false, true, false, true, null),
    PLATINUM("Platinum", true, true, true, false, true, null),
    NEODYMIUM("Neodymium", true, false, true, false, true, null),
    LITHIUM("Lithium", true, true, true, false, true, null),
    // Al's dust stage is Al2O3 (Alumina), not metallic dust - §11.5: it's the same item that
    // feeds both the Electrolyzer (-> ingotAluminium) and Ceramic Rod/Package (§1/§2).
    ALUMINIUM("Aluminium", true, false, true, false, true, "AluminiumOxide"),

    // ---- Centrifuge byproducts of another ore (§11.2's split table) ----
    SILVER("Silver", false, false, true, true, true, null),
    GALLIUM("Gallium", false, false, true, true, true, null),
    INDIUM("Indium", false, false, true, true, true, null),
    NIOBIUM("Niobium", false, false, true, true, true, null),
    HAFNIUM("Hafnium", false, false, true, true, true, null),
    PALLADIUM("Palladium", false, false, true, false, true, null),
    CERIUM("Cerium", false, false, true, false, true, null),
    LANTHANUM("Lanthanum", false, false, true, false, true, null),

    // ---- Non-ore / special materials ----
    CARBON("Carbon", false, false, true, false, false, null),   // §11.4: Coal -> Crusher -> dustCarbon
    ASH("Ash", false, false, false, true, false, null),         // §11.4: 10% byproduct of Carbon dust, not compacted further
    STEEL("Steel", false, false, false, false, true, null),     // §11.3: alloy, ingot only, no ore of its own
    ARSENIC("Arsenic", false, false, true, false, false, null), // §18.1: AsH3 -> Chem Reactor -> dustArsenic (GaAs subsystem), no ore/ingot of its own
    SCRAP("Scrap", false, false, true, false, false, null),     // §13.2: defect-roll output, recyclable back through Crusher
    // §4: Magnesite -> Crusher -> Kiln (calcined to MgO, the "dust") -> electrolysis -> Mg,
    // the reducing agent of the Kroll titanium process. Appended last on purpose: item
    // metadata is the position in each kind's list, so inserting earlier would shift the
    // meta of every material after it in existing worlds.
    MAGNESIUM("Magnesium", true, false, true, false, true, "Magnesia"),
    // Vanilla ores through the mod's pipeline. No ingot of their own: the dust smelts to vanilla's
    // iron / gold ingot. Appended last for the same metadata reason as Magnesium.
    IRON("Iron", true, true, true, false, false, null),
    GOLD("Gold", true, true, true, false, false, null),
    DIAMOND("Diamond", false, false, true, false, false, null);  // crushed diamond: cheaper diamond wire / blade

    public final String oreDictName;
    public final boolean hasCrushedOre;
    public final boolean hasPurifiedCrushedOre;
    public final boolean hasDust;
    public final boolean hasTinyDust;
    public final boolean hasIngot;
    private final String dustNameOverride;

    Material(String oreDictName, boolean hasCrushedOre, boolean hasPurifiedCrushedOre,
             boolean hasDust, boolean hasTinyDust, boolean hasIngot, String dustNameOverride) {
        this.oreDictName = oreDictName;
        this.hasCrushedOre = hasCrushedOre;
        this.hasPurifiedCrushedOre = hasPurifiedCrushedOre;
        this.hasDust = hasDust;
        this.hasTinyDust = hasTinyDust;
        this.hasIngot = hasIngot;
        this.dustNameOverride = dustNameOverride;
    }

    public boolean has(MaterialItemKind kind) {
        switch (kind) {
            case CRUSHED_ORE: return hasCrushedOre;
            case PURIFIED_CRUSHED_ORE: return hasPurifiedCrushedOre;
            case DUST: return hasDust;
            case DUST_TINY: return hasTinyDust;
            case INGOT: return hasIngot;
            default: return false;
        }
    }

    /** OreDict suffix for this material under the given item kind (handles §11.5's Al override). */
    public String oreDictNameFor(MaterialItemKind kind) {
        if (kind == MaterialItemKind.DUST && dustNameOverride != null) {
            return dustNameOverride;
        }
        return oreDictName;
    }

    /** Stable, filtered, metadata-ordered list of every material that has an item of this kind. */
    public static Material[] byKind(MaterialItemKind kind) {
        List<Material> list = new ArrayList<Material>();
        for (Material material : values()) {
            if (material.has(kind)) {
                list.add(material);
            }
        }
        return list.toArray(new Material[list.size()]);
    }
}
