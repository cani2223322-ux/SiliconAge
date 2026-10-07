🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен (общая энергосеть). Заменяет `SiliconAgeBeta-0.1.8.jar`: старый jar из папки `mods` уберите. Миры 0.1.8 открываются без изменений.

**Новое**

*Сингулярный клинок и Сингулярный бур (SV)*
- Получаются **преобразованием** Экзо-клинка и Экзо-бура в Сингулярной станции (новый слот инструмента): модернизация до уровня 5, смена ветки, цветовая схема.
- Газы берут из баков надетой брони, все функции Экзо на 20% дешевле; с полным Сингулярным комплектом — питание от нагрудника, нагрев уходит в броню, откаты −25%.
- **Клинок — 6 форм** (Shift + колесо, у каждой свой вид и особая атака): Меч, Коса, Копьё, Хлыст-клинок, Щит-клинок, Сингулярная. Гравитационный рывок, каскад, клинок в ножнах, разрез пространства, идеальное парирование, чутьё охотника, заряженный удар, горизонт событий, гравитационный щит, цепной разрез, контратака, гравитационная привязка. Ветки «Разрушитель», «Дуэлянт», «Страж» («Последний шанс»). Очки уровня за убийства.
- **Бур — режим «Чёрная дыра»** 5×5 / 9×9 / 12×12 (и туннель 3): уничтожает зону, кроме бедрока, блоков с содержимым и чужих приватов, и даёт **крупицы сингулярности**. Гравитационная воронка, сундук добычи в любом измерении, осушение, замена блоков, фазовое копание. Ветки «Шахтёр» и «Старатель».
- **Крупица** и **сгусток сингулярности** (9 крупиц): очки уровня бура, оплата сингулярной материи при модернизации в станции, сгусток в Компрессоре материи → 100 мБ.

*Ванильные руды в машинах мода*
- Железная и золотая руда: «дробилка → промывка → центрифуга → печь» — **2 слитка с руды** (из золота иногда серебро).
- Уголь, красная пыль, лазурит, кварц, алмазы и изумруды дробятся сразу в добычу с шансом прибавки.
- Кварц Незера → кремнезёмный песок, алмаз → алмазная пыль (дешёвые алмазная проволока и лезвие), железная пыль + угольная → сталь.
- Модули карьера и Экзо-буровая обрабатывают ванильные руды сами. Статья «Ванильные руды» в справочнике.

*Обзор сети и справочник*
- **Обзор сети:** Shift + ПКМ ключом по кабелю — голограмма всей сети: кабели по нагрузке, источники, потребители, накопители и трансформаторы, панель выработки, потребления и потерь.
- **Поиск по справочнику** (Ctrl+F или «/») по заголовкам и всему тексту, список найденного с подсветкой и переходом к месту; **закладки** на статьях.

**Исправленные баги**
- **Крупицы сингулярности:** их нельзя больше фармить генератором камня (камень считается в 8 раз меньше), поршнями и падающим песком; проверки приватов не снимают отметку «поставлено игроком»; поршни в зоне чёрной дыры не ломаются.
- **Клинок и PvP:** при выключенном PvP клинок не тянет, не привязывает, не замедляет и не отталкивает игроков; привязка отпускает цель в чужом привате.
- **Клинок:** идеальное парирование больше не защищает от лавы и огня; щит Стража больше не бесплатен навсегда; контратака ограничена; лимит очков за убийства и откат «Последнего шанса» не сбрасываются смертью или сменой клинка.
- Выбор формы клинка в меню K срабатывал только на один шаг.
- Алмазная пыль больше не сгорает в Компрессоре материи; дробилка не перемалывает алмазы и кварц, пришедшие автоматикой.
- **Справочник:** нечитаемый файл закладок больше не стирается, закладки сверх 64 не теряются, Esc и клавиши E/G во время поиска работают как ожидается.

Полный список — в `CHANGELOG.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional (one energy network). Replaces `SiliconAgeBeta-0.1.8.jar`: remove the old jar from `mods`. 0.1.8 worlds open as they are.

**New**

*Singular Blade and Singular Drill (SV)*
- Made by **converting** an Exo blade or drill in the Singular Station (a new tool slot): modernisation up to level 5, branch change, colour scheme.
- They take gases from the worn suit's tanks, every Exo function costs 20% less; with the full Singular suit they draw from the chestplate, dump heat into the suit and get cooldowns -25%.
- **Blade - 6 forms** (sneak + wheel, each with its own look and special attack): Sword, Scythe, Spear, Whip, Shield, Singular. Gravity pull, cascade, sheath, rift step, perfect parry, hunter's sense, charged strike, event horizon, gravity shield, chain cut, riposte, gravity tether. Destroyer, Duelist and Guardian branches (Last Chance). Level points for kills.
- **Drill - Black Hole mode** 5x5 / 9x9 / 12x12 (and a 3-deep tunnel): destroys the zone but bedrock, blocks with contents and other claims, and gives **Singular Crumbs**. Gravity funnel, a loot chest in any dimension, drain, block replace, phase digging. Miner and Prospector branches.
- **Singular Crumb** and **Singular Clot** (9 crumbs): drill level points, paying Singular Matter for modernisation in the station, a clot gives 100 mB in the Matter Compressor.

*Vanilla ores in the mod's machines*
- Iron and gold ore: "crusher → washer → centrifuge → furnace" - **2 ingots per ore** (gold sometimes gives silver).
- Coal, redstone, lapis, quartz, diamond and emerald ore are crushed straight into their drops with a chance of a bonus.
- Nether quartz → silica sand, diamond → diamond dust (cheaper diamond wire and blade), iron dust + carbon dust → steel.
- The quarry modules and the Exo drilling rig process vanilla ores on their own. A "Vanilla ores" handbook article.

*Network overview and handbook*
- **Network overview:** sneak + right-click a cable with a wrench - a hologram of the whole network: cables by load, sources, consumers, storages and transformers, a panel with output, consumption and losses.
- **Handbook search** (Ctrl+F or "/") over titles and all text, a result list with highlights and a jump to the match; **bookmarks** on articles.

**Fixed bugs**
- **Singular Crumbs:** they can no longer be farmed with a stone generator (stone counts 8 times less), pistons or falling sand; claim checks no longer clear the "placed by a player" mark; pistons in the black hole's zone are left alone.
- **Blade and PvP:** with PvP off the blade no longer pulls, tethers, slows or pushes players; a tether lets go in another player's private field.
- **Blade:** perfect parry no longer protects from lava and fire; the Guardian's shield is no longer free forever; riposte is capped; the kill-point cap and the Last Chance cooldown survive death and a blade swap.
- Picking a blade form in the K menu moved it only one step.
- Diamond dust no longer burns in the Matter Compressor; the Crusher no longer grinds diamonds and quartz fed by automation.
- **Handbook:** an unreadable bookmarks file is no longer wiped, bookmarks beyond 64 are kept, Esc and the E/G keys behave while searching.

The full list is in `CHANGELOG.md`.
