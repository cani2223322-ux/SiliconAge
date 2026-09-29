package com.sc.client;

import org.lwjgl.input.Keyboard;

import com.sc.manual.BookContent;
import com.sc.manual.BookEntry;
import com.sc.manual.BookProgressSC;
import com.sc.manual.GuiBook;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The handbook key (G): over an item in any inventory screen it opens that item's article; in
 * the world, the held item's article (or the book where it was left). Also ticks the handbook's
 * first steps. Not while a text field could be taking the letter (anvil, creative search, NEI's search).
 */
@SideOnly(Side.CLIENT)
public class BookKeySC {

    public static final KeyBinding KEY_BOOK = new KeyBinding("key.sc.book", Keyboard.KEY_G, "key.categories.sc");
    private boolean wasDown;

    public static void register() {
        ClientRegistry.registerKeyBinding(KEY_BOOK);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new BookKeySC());
    }

    /** A text field of the screen (any field of its classes) has the keyboard. */
    private static boolean focusedTextField(Object gui) {
        for (Class<?> c = gui.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!net.minecraft.client.gui.GuiTextField.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(gui);
                    if (v != null && ((net.minecraft.client.gui.GuiTextField) v).isFocused()) {
                        return true;
                    }
                } catch (Throwable t) {
                    // unreadable: ignore
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null) {
            return;
        }
        BookProgressSC.tick();
        if (mc.currentScreen == null) {
            wasDown = false;
            if (KEY_BOOK.isPressed()) {
                ItemStack held = mc.thePlayer.getHeldItem();
                BookEntry e = held == null ? null : BookContent.entryFor(held);
                mc.displayGuiScreen(new GuiBook(e));
            }
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer) || KEY_BOOK.getKeyCode() <= 0) {
            wasDown = false;
            return;
        }
        boolean down = Keyboard.isKeyDown(KEY_BOOK.getKeyCode());
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed || typing(mc.currentScreen)) {
            return;
        }
        try {
            Slot slot = ReflectionHelper.getPrivateValue(GuiContainer.class, (GuiContainer) mc.currentScreen, "theSlot", "field_147006_u");
            ItemStack s = slot == null ? null : slot.getStack();
            BookEntry e = s == null ? null : BookContent.entryFor(s);
            if (e != null) {
                mc.thePlayer.closeScreen();                  // tells the server - its container mustn't stay open
                mc.displayGuiScreen(new GuiBook(e));
            }
        } catch (Throwable t) {
            // a screen without the vanilla hovered slot
        }
    }

    private static boolean typing(Object gui) {
        if (gui instanceof GuiRepair || focusedTextField(gui)) {
            return true;
        }
        if (gui instanceof GuiContainerCreative && ((GuiContainerCreative) gui).func_147056_g() == CreativeTabs.tabAllSearch.getTabIndex()) {
            return true;
        }
        return cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems") && com.sc.nei.NeiBookSC.searchFocused();
    }
}
