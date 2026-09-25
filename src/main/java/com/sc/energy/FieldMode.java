package com.sc.energy;

/**
 * §16: Field Generator cluster modes - "Union=1x, Box=2x, Prism=1.5x"; the dome (half spheres
 * over the nodes) and the cylinder (upright cylinders round them) were added later - ordinals
 * are saved, only ever append.
 */
public enum FieldMode {

    UNION(1.0),
    BOX(2.0),
    PRISM(1.5),
    DOME(0.75),
    CYLINDER(1.25);

    public final double costMultiplier;

    FieldMode(double costMultiplier) {
        this.costMultiplier = costMultiplier;
    }

    public FieldMode next() {
        FieldMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Shapes built node by node (bubbles, domes, cylinders): mobs are pushed away from the nearest node. */
    public boolean perNode() {
        return this == UNION || this == DOME || this == CYLINDER;
    }
}
