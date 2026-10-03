package com.sc.tileentity;

/**
 * A bridge part with a tile entity (capacitor, energy port, gas port): the controller that counted it
 * at its last check (TileEntityBridgeControllerSC scans), so a click on the part opens that controller's
 * screen and the gas port fills that controller's tanks.
 */
public interface IBridgePartSC {

    /** The linked controller's x y z, or null. */
    int[] controllerPos();

    /** Set by the controller's check (null: no longer linked). */
    void link(int[] pos);
}
