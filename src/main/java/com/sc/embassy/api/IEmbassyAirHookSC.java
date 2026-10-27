package com.sc.embassy.api;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §5.2, docs/idea-embassy-airfield.md §9): what the Vehicles add-on gives
 * the core back - implemented by the ADD-ON only (its embassy compat class registers one with EmbassyAirApiSC.registerHook in
 * its init, after checking EmbassyAirApiSC.API_VERSION); the core never names a class of the add-on. Every method is called on the
 * server thread, with a real player (never a FakePlayer), and must not throw (the core catches and logs a throw, then forgets the hook
 * for that call only). Java 6: no default methods - the add-on implements them all (an empty answer is fine). Fixed for API_VERSION 1:
 * methods are only ever appended in a later version (with the version raised).
 */
public interface IEmbassyAirHookSC {

    /** The EmbassyAirApiSC.API_VERSION the add-on was built against (the core refuses a hook of a newer version than its own). */
    int apiVersion();

    /**
     * A «Колонка обслуживания» clicked (not sneaking - a sneaking click is always the core's armour gases): the add-on opens its service
     * window (charging, hydrogen, repair, «Электролит», parking, the tow - it pays through EmbassyAirApiSC.pay) and answers true; false -
     * the core serves the click itself (the armour's gases for Credit Chips). `column` is the index into field.columns().
     */
    boolean openService(EntityPlayerMP p, AirfieldInfoSC field, int column);

    /**
     * The Dispatcher's «Лётное поле» tab: the add-on's lines for `p` (the parked machines, the services' prices, the tower's state...),
     * translated by the client (ChatComponentTranslation of the add-on's own lang keys); null or empty - none. At most
     * EmbassyAirApiSC.TAB_ROWS are shown.
     */
    List<IChatComponent> serviceRows(EntityPlayerMP p, AirfieldInfoSC field);

    /** The field's ATIS line now (the tab shows it; null - none), e.g. „Тантал-1, информация „Альфа“, ВПП 36, ветер 340 8, QNH 1013“. */
    IChatComponent atisLine(World w, AirfieldInfoSC field);

    /**
     * A button of the tab pressed: `action` is the add-on's own key (the tab shows one button, EmbassyAirApiSC.TAB_ACTION_SERVICE - the
     * service window, as a column's click); the core checked that `p` stands at that embassy's Dispatcher.
     */
    void tabAction(EntityPlayerMP p, AirfieldInfoSC field, String action);

    /**
     * An airfield was made or renewed in `w` (the revision is now `revision` - EmbassyAirApiSC.revision): the add-on rebuilds its virtual
     * airports. Called from the world generation (a structure start being made) - only mark them dirty here, rebuild on the next tick.
     */
    void airfieldsChanged(World w, int revision);
}
