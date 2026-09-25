package com.sc.util;

/**
 * The 4 fluid pipes from 01_recipes.md §12.2 (Item Pneumatic Tube is a separate block -
 * it carries ItemStacks, not fluids, see TileEntityItemTubeSC).
 */
public enum PipeType {

    COPPER("pipeCopper", 400, false),
    STEEL("pipeSteel", 1000, false),
    PTFE("pipePTFE", 300, true),
    TITANIUM("pipeTitanium", 1500, false);

    public final String textureName;
    /** mB/t this pipe can move, §12.2. */
    public final int throughput;
    /** §12.2/§12.3: only PTFE resists corrosive fluids (HCl, HF, Cl2, NaOH solution, ...). */
    public final boolean chemicallyResistant;

    PipeType(String textureName, int throughput, boolean chemicallyResistant) {
        this.textureName = textureName;
        this.throughput = throughput;
        this.chemicallyResistant = chemicallyResistant;
    }
}
