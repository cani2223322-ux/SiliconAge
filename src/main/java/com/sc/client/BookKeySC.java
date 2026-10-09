package com.sc.client;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The handbook key (G): over an item in any inventory screen it opens that item's article (no
 * article: the book's search for its name) and Esc goes back to that screen; in the world, the held
 * item's article (or the book where it was left). Also ticks the handbook's first steps, and brings
 * the book back when NEI's recipes opened from it are closed. Not while a text field could be taking
 * the letter (anvil, creative search, NEI's search).
 */
@SideOnly(Side.CLIENT)
public class BookKeySC {

    public static final KeyBinding KEY_BOOK = new KeyBinding("key.sc.book", Keyboard.KEY_G, "key.categories.sc");
    private boolean wasDown;

    /** The book that opened NEI's recipes (it opens again when they close); null: none. */
    private static GuiBook afterNei;

    public static void register() {
        ClientRegistry.registerKeyBinding(KEY_BOOK);
        BookKeySC h = new BookKeySC();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(h);
        MinecraftForge.EVENT_BUS.register(h);
    }

    /** The book just opened NEI's recipes: bring it back when NEI's screens are closed. */
    public static void returnAfterNei(GuiBook book) {
        afterNei = book;
    }

    private static boolean neiRecipes(Object gui) {
        return gui != null && gui.getClass().getName().startsWith("codechicken.nei.recipe.");
    }

    /**
     * NEI's recipe screen closing (to nothing) after the book opened it: the book instead, on its page.
     * Paging through NEI's recipes keeps the wait; any other screen ends it (so NEI opened elsewhere closes as usual).
     */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiBook book = afterNei;
        if (book == null) {
            return;
        }
        if (neiRecipes(event.gui)) {
            return;
        }
        afterNei = null;
        if (event.gui == null && neiRecipes(Minecraft.getMinecraft().currentScreen) && Minecraft.getMinecraft().thePlayer != null) {
            book.afterNei();
            event.gui = book;
        }
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
        int code = KEY_BOOK.getKeyCode();
        if (!(mc.currentScreen instanceof GuiContainer) || code == 0) {
            wasDown = false;
            return;
        }
        // a mouse button is code - 100 in 1.7.10 key bindings
        boolean down = code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed || typing(mc.currentScreen)) {
            return;
        }
        try {
            Slot slot = ReflectionHelper.getPrivateValue(GuiContainer.class, (GuiContainer) mc.currentScreen, "theSlot", "field_147006_u");
            ItemStack s = slot == null ? null : slot.getStack();
            if (s == null || s.getItem() == null) {
                return;
            }
            BookEntry e = BookContent.entryFor(s);
            GuiBook book = e != null ? new GuiBook(e) : GuiBook.search(s.getDisplayName());
            GuiContainer gc = (GuiContainer) mc.currentScreen;
            if (keepOpen(mc, gc)) {
                mc.displayGuiScreen(book.from(gc));          // the container stays open: Esc in the book goes back to it
            } else {
                mc.thePlayer.closeScreen();                  // tells the server - its container mustn't stay open
                mc.displayGuiScreen(book);
            }
        } catch (Throwable t) {
            // a screen without the vanilla hovered slot
        }
    }

    /**
     * The screen can be left open behind the book and gone back to: nothing on the cursor and nothing in
     * the inventory's crafting grid (a client-side close would drop them from view).
     */
    private static boolean keepOpen(Minecraft mc, GuiContainer gc) {
        if (mc.thePlayer.inventory.getItemStack() != null) {
            return false;
        }
        if (!(gc.inventorySlots instanceof ContainerPlayer) && !(gc instanceof net.minecraft.client.gui.inventory.GuiContainerCreative)
                && !gc.inventorySlots.getClass().getName().startsWith("com.sc.")) {
            return false;                              // another mod's container may act on its client-side close (a chest lid shuts)
        }
        if (gc.inventorySlots instanceof ContainerPlayer) {
            ContainerPlayer cp = (ContainerPlayer) gc.inventorySlots;
            for (int i = 0; i < cp.craftMatrix.getSizeInventory(); i++) {
                if (cp.craftMatrix.getStackInSlot(i) != null) {
                    return false;
                }
            }
        }
        return true;
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
