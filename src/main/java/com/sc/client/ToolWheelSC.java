package com.sc.client;

import org.lwjgl.input.Keyboard;

import com.sc.handler.ArmorNetSC;
import com.sc.item.ItemBladeSC;
import com.sc.manual.Lang;
import com.sc.util.BladeForm;
import com.sc.util.ToolLevelSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;

/**
 * Shift + mouse wheel with a Singular tool in hand (docs/plan-singular-tools.md): the blade's form (BLADE_FORM),
 * the drill's mode - normal / black hole size (DRILL_MODE); the hotbar doesn't scroll then. The blade's next form
 * is known here (BladeForm.cycle, the server's own rule): set at once and named on the action bar; the drill's
 * mode is named once the server's answer comes back (its rules are the drill's own). Client only; on the Forge
 * bus (MouseEvent) and on FML's (ClientTickEvent).
 */
public class ToolWheelSC {

    public static final ToolWheelSC INSTANCE = new ToolWheelSC();
    /** Ticks the drill's mode is watched for the server's answer after a step. */
    private static final int WATCH_TICKS = 20;
    /** Client ticks at least between two steps sent (a little more than the server's BladeSingularSC.WHEEL_GAP). */
    private static final int SEND_GAP = com.sc.item.BladeSingularSC.WHEEL_GAP + 1;

    private int ticks;
    private int sentAt = -100;

    private int watch;
    private int watchSlot = -1;
    private String watchMode = "";

    private ToolWheelSC() {
    }

    private static boolean shiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.dwheel == 0 || mc.thePlayer == null || mc.currentScreen != null || !shiftDown()) {
            return;
        }
        ItemStack held = mc.thePlayer.getCurrentEquippedItem();
        int delta = event.dwheel > 0 ? -1 : 1;              // the hotbar's direction: up = back
        if (!ToolLevelSC.isBlade(held) && !ToolLevelSC.isDrill(held)) {
            return;
        }
        event.setCanceled(true);                            // the hotbar doesn't scroll with a Singular tool in hand
        if (mc.thePlayer.isUsingItem() || ticks - sentAt < SEND_GAP) {
            return;                                         // mid-block the form stays (the server refuses it too); too fast: dropped
        }
        sentAt = ticks;
        if (ToolLevelSC.isBlade(held)) {
            BladeForm from = ItemBladeSC.formOf(held);
            BladeForm to = BladeForm.cycle(from, delta, ToolLevelSC.effectiveLevel(mc.thePlayer, held), ToolLevelSC.branchOf(held),
                    mc.thePlayer.capabilities.isCreativeMode);
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.BLADE_FORM, delta));
            if (to != from) {
                ItemBladeSC.setForm(held, to);               // shown at once; the server's copy follows
            }
            mc.ingameGUI.func_110326_a(Lang.tr(to == from ? "sc.toolwheel.form.only" : "sc.toolwheel.form", Lang.tr(to.langKey())), false);
        } else {
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DRILL_MODE, delta));
            watch = WATCH_TICKS;
            watchSlot = mc.thePlayer.inventory.currentItem;
            watchMode = modeName(mc.thePlayer, held);
        }
    }

    /** The drill's mode once the server has changed it: «Режим бура: Чёрная дыра 9×9 · туннель 3». */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (watch <= 0) {
            return;
        }
        watch--;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.inventory.currentItem != watchSlot) {
            watch = 0;
            return;
        }
        ItemStack held = mc.thePlayer.getCurrentEquippedItem();
        if (!ToolLevelSC.isDrill(held)) {
            watch = 0;
            return;
        }
        String now = modeName(mc.thePlayer, held);
        if (!now.equals(watchMode)) {
            mc.ingameGUI.func_110326_a(Lang.tr("sc.toolwheel.mode", now), false);
            watch = 0;
        }
    }

    /** «Обычный» / «Чёрная дыра 9×9» / «Чёрная дыра 9×9 · туннель 3» - the size the drill's level lets it work at. */
    public static String modeName(net.minecraft.entity.player.EntityPlayer p, ItemStack drill) {
        return SingularHudSC.holeLine(p, drill);
    }
}
