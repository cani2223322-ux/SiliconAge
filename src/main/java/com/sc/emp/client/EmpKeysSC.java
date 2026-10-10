package com.sc.emp.client;

import org.lwjgl.input.Keyboard;

import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.manual.Lang;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE C: the ПЭ-1 key bindings in «Управление» (category Silicon Age, unbound by default): each mode
 * (key.sc.emp.point / pulse / siphon -> SELECT_MODE), «Отменить набор» (CANCEL_CHARGE) and «Аварийная перезагрузка»
 * (EMERGENCY_REBOOT, works without the gun). The modes can also get a K-menu key (ArmorKeyBindsSC "emp_<key>",
 * Ctrl / Shift / Alt) - both end in selectMode. Shift + wheel is ToolWheelSC's. register() is called by EmpClientSC.
 */
@SideOnly(Side.CLIENT)
public final class EmpKeysSC {

    public static final EmpKeysSC INSTANCE = new EmpKeysSC();
    public static KeyBinding KEY_POINT, KEY_PULSE, KEY_SIPHON, KEY_CANCEL, KEY_REBOOT;
    /** The mode keys in EmpMode order. */
    private static KeyBinding[] modeKeys = new KeyBinding[0];

    private EmpKeysSC() {
    }

    public static void register() {
        KEY_POINT = key("key.sc.emp.point");
        KEY_PULSE = key("key.sc.emp.pulse");
        KEY_SIPHON = key("key.sc.emp.siphon");
        KEY_CANCEL = key("key.sc.emp.cancel");
        KEY_REBOOT = key("key.sc.emp.reboot");
        modeKeys = new KeyBinding[]{KEY_POINT, KEY_PULSE, KEY_SIPHON};
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    private static KeyBinding key(String name) {
        KeyBinding k = new KeyBinding(name, Keyboard.KEY_NONE, "key.categories.sc");
        ClientRegistry.registerKeyBinding(k);
        return k;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        for (int i = 0; i < modeKeys.length && i < EmpMode.values().length; i++) {
            if (modeKeys[i].isPressed()) {
                selectMode(mc, EmpMode.values()[i]);
            }
        }
        if (KEY_CANCEL != null && KEY_CANCEL.isPressed() && ItemEmpGunSC.held(mc.thePlayer) != null) {
            EmpNetSC.send(EmpNetSC.CANCEL_CHARGE, 0, 0);       // the server drops the charge-up (no EU) and says so
        }
        if (KEY_REBOOT != null && KEY_REBOOT.isPressed()) {
            EmpNetSC.send(EmpNetSC.EMERGENCY_REBOOT, 0, 0);    // the server checks suppression, cooldown, argon, charge and says why not
        }
    }

    /**
     * A mode key (here or the K menu's): the gun in hand switches to that mode - shown at once and named on the action
     * bar; without the gun / a mode switched off in the K menu: says so.
     */
    public static void selectMode(Minecraft mc, EmpMode m) {
        String name = Lang.tr("sc.emp.mode." + m.key());
        ItemStack gun = ItemEmpGunSC.held(mc.thePlayer);
        if (gun == null) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.empkey.nogun", name), false);
            return;
        }
        if (!ItemEmpGunSC.modeEnabled(gun, m)) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.emp.modeoff"), false);
            return;
        }
        if (ItemEmpGunSC.modeOf(gun) != m) {
            ItemEmpGunSC.setMode(gun, m);                        // shown at once; the server's copy follows (it drops a charge-up)
            EmpNetSC.send(EmpNetSC.SELECT_MODE, m.ordinal(), 0);
        }
        mc.ingameGUI.func_110326_a(Lang.tr("sc.emp.wheel", name), false);
    }
}
