package com.sc.emp;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Server: EmpNetSC.ActionMessage, run on the next server tick (queued). The gun's settings are plain NBT and
 * done here; modules and the charge-up go to package A (EmpGunLogicSC), the emergency reboot to package B (EmpArmorSC).
 */
public final class EmpActionsSC {

    private EmpActionsSC() {
    }

    public static void handle(EntityPlayerMP p, byte action, int a, int b) {
        if (action == EmpNetSC.EMERGENCY_REBOOT) {
            EmpArmorSC.emergencyReboot(p);                       // needs the armour, not the gun
            return;
        }
        ItemStack gun = ItemEmpGunSC.held(p);
        if (gun == null) {
            return;
        }
        if (p.isUsingItem() && action != EmpNetSC.SELECT_MODE && action != EmpNetSC.CYCLE_MODE && action != EmpNetSC.CANCEL_CHARGE) {
            return;                                              // settings never touch the NBT mid charge-up / stream (rule 0.4.1)
        }
        switch (action) {
            case EmpNetSC.SELECT_MODE: {
                EmpMode m = EmpMode.of(a);
                if (a >= 0 && a < EmpMode.values().length && ItemEmpGunSC.modeEnabled(gun, m) && ItemEmpGunSC.modeOf(gun) != m) {
                    EmpGunLogicSC.cancelCharge(p, gun, false);
                    ItemEmpGunSC.setMode(gun, m);
                }
                break;
            }
            case EmpNetSC.CYCLE_MODE: {
                EmpMode next = ItemEmpGunSC.step(gun, ItemEmpGunSC.modeOf(gun), a);
                if (next != ItemEmpGunSC.modeOf(gun)) {
                    EmpGunLogicSC.cancelCharge(p, gun, false);
                    ItemEmpGunSC.setMode(gun, next);
                }
                break;
            }
            case EmpNetSC.TOGGLE_MODE:
                if (a >= 0 && a < EmpMode.values().length) {
                    ItemEmpGunSC.setModeEnabled(gun, EmpMode.of(a), b != 0);
                }
                break;
            case EmpNetSC.SET_ZONE:
                ItemEmpGunSC.setZone(gun, a);
                break;
            case EmpNetSC.SET_RECEIVER_ORDER:
                ItemEmpGunSC.setReceiverOrder(gun, a);
                break;
            case EmpNetSC.TOGGLE_RECEIVER:
                ItemEmpGunSC.setReceiverEnabled(gun, a, b != 0);
                break;
            case EmpNetSC.SET_LIMIT:
                ItemEmpGunSC.setLimit(gun, a);
                break;
            case EmpNetSC.SET_WARN:
                ItemEmpGunSC.setWarn(gun, b != 0);
                break;
            case EmpNetSC.MODULE_INSTALL:
                EmpGunLogicSC.installModule(p, gun, EmpModule.of(a), b);
                break;
            case EmpNetSC.MODULE_REMOVE:
                EmpGunLogicSC.removeModule(p, gun, EmpModule.of(a));
                break;
            case EmpNetSC.CANCEL_CHARGE:
                EmpGunLogicSC.cancelCharge(p, gun, true);
                break;
            default:
                break;
        }
    }
}
