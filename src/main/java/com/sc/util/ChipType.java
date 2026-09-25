package com.sc.util;

/** §16: armor chip categories - "не более 1 чипа каждого типа на костюм", enforced by ItemArmorChipSC. */
public enum ChipType {

    SENSOR("chipSensor"),
    POWER("chipPower"),
    DEFENSE("chipDefense"),
    MOBILITY("chipMobility"),
    UTILITY("chipUtility");

    public final String textureName;

    ChipType(String textureName) {
        this.textureName = textureName;
    }
}
