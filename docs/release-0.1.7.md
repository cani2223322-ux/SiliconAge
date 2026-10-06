🇷🇺 **Русский**

> ⚠️ **Альфа-версия.** Возможны ошибки и изменения, ломающие миры. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен (общая энергосеть). Заменяет `SiliconAgeAlpha-0.1.6.jar`: старый jar из папки `mods` уберите.

**Новое**

*Сингулярная броня — четвёртый костюм*
- Шлем, нагрудник, поножи и ботинки: защита 5/10/8/5, 64 млн EU в каждой части, зарядка SV, предел нагрева 1000. Все функции Экзо на 20% меньше газа, полный комплект даёт бонусы Экзо.
- **Около 20 своих функций:** гравитационный полёт, антигравитация, фазовый рывок, горизонт событий, гравитационный пресс и захват, замедление времени, чёрная дыра, купол, режим сингулярности, резонанс, гравитационный сканер, чувство угрозы, анализатор и другие.
- **Уровни 1–5** у каждой части: очки за газы, полёт, бои и исследование мира, задания на каждый уровень, бонусы, ветки выбора на уровнях 3 и 5.
- Новый газ — **сингулярная материя**.
- **11 цветовых схем**, **профили функций** «Бой / Шахта / Полёт» (клавиша P), откаты функций над хотбаром, вкладка «Уровень» в меню K.
- Крафт: **преобразование** Экзо-части в сингулярную в Сингулярной станции (заряд, газы, чипы и настройки сохраняются).

*Станции обслуживания брони*
- **Станция обслуживания брони (MV):** 4 слота, 8 баков газов, заряжает и заправляет броню в слотах и того, кто стоит на ней.
- **Сингулярная станция обслуживания (SV):** модернизация частей до следующего уровня, преобразование, перенос уровня, синхронизация, смена ветки, цветовая схема. Ресурсы списываются по ходу, отмена возвращает 50%. **Гравитационные стабилизаторы** (до 4) и Сингулярный реактор рядом ускоряют процесс.

*Газы и меню брони*
- **Газы в энергоброне:** гелий (охлаждение), кислород (дыхание, и в космосе Galacticraft), водород (полёт, рывок), аргон, криптон, тяжёлая вода, дейтерий.
- **Строгие газы:** каждая функция работает только при своём газе. **Аварийный режим** Кванта и Экзо без гелия: только HUD, защита как у железной брони.
- **Меню брони (K) переделано:** все 4 части с полосками газов, у функции — её газ и клавиша, вкладка «Газы» с «Заправить всё».

*Наземный и Космический мост*
- Кольцо из гравитационных катушек открывает портал **мгновенно в любую точку**, Космический мост — в другое измерение; конец открывается и **в воздухе**, с мягкой посадкой.
- Контроллер, конденсатор сингулярности, порты, 6 модулей. **Пульты** с пятью режимами («Домой», «Забрать друга», «Ко мне»…), **координатор**, **связь с сингулярной бронёй** (клавиши H / J).
- Износ кольца, стабильность, перегрев, «знакомые места». У всех частей есть рецепты.

*Преобразователь энергии*
- EU ⇄ **RF**, **джоули Mekanism** (с «Картой Mekanism») и **gJ Galacticraft** (с «Картой Galacticraft»). Курсы 1 EU = 4 RF = 10 J, потери 5% — всё в конфиге.
- Необязателен: без RF API, Mekanism и Galacticraft у него нет рецепта, а поставленный работает как буфер EU.
- **Поддержка Mekanism написана по API и в игре с Mekanism не проверялась.** MJ, Universal Electricity и Botania не поддерживаются.

*Машины и крафты*
- **Компрессор материи — режим «жидкая материя»**; Сингулярный реактор собирает сингулярную материю; ячейка сингулярной материи.

*Справочник*
- 14 разделов, больше 175 статей. Новые разделы **«Путь развития»**, **«Мост»**, **«Компоненты»**, **«Справочная»**. Исправлены ошибки в текстах, у каждого предмета есть статья.

*Разное*
- Генераторы, реакторы, генератор поля, карьер, душ и передатчики **ставятся выключенными** — без риска взрыва.

**Исправленные баги**
- Пять проверок мода на баги: **27 ошибок**, плюс около 25 исправлений и доработок по их итогам.
- **Газы и броня:** вылет клиента от сообщения о газах; траты газов в творческом режиме; станция заправляла газы только после полной зарядки.
- **Дюпы и потери:** дюп модулей при ломании с открытым экраном; пропажа или второе Сингулярное ядро при отмене модернизации 4→5; реактор терял сингулярную материю.
- **Мост:** утечка тикетов чанков; выход в чужое приватное поле; подделка находки сканера клиентом.
- **Преобразователь энергии:** RF из дробного остатка; буферы не сохранялись при автосохранении.
- Выключенные станция брони и душ брали энергию.

Полный список — в `CHANGELOG.md`.

---

🇬🇧 **English**

> ⚠️ **Alpha.** Expect bugs and world-breaking changes. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional (one energy network). Replaces `SiliconAgeAlpha-0.1.6.jar`: remove the old jar from `mods`.

**New**

*Singular armour, the fourth suit*
- Helmet, chestplate, leggings and boots: protection 5/10/8/5, 64M EU in each piece, SV charging, heat limit 1000. Every Exo function on 20% less gas; the full set gives the Exo bonuses.
- **About 20 functions of its own:** gravitational flight, antigravity, phase dash, event horizon, gravity press and grab, time slowing, black hole, dome, Singularity mode, resonance, gravity scanner, threat sense, analyzer and more.
- **Levels 1-5** for each piece: points for gases, flight, fights and exploring, tasks for every level, bonuses, branch choices at levels 3 and 5.
- A new gas, **Singular Matter**.
- **11 colour schemes**, **function profiles** Combat / Mining / Flight (the P key), cooldowns above the hotbar, a Level tab in the K screen.
- Crafting: **conversion** of an Exo piece into a Singular one in the Singular Station (charge, gases, chips and settings kept).

*Service stations*
- **Armour Service Station (MV):** 4 slots, 8 gas tanks, charges and refuels the suits in its slots and whoever stands on it.
- **Singular Service Station (SV):** modernising pieces to the next level, conversion, level transfer, sync, branch change, colour scheme. Resources are drawn as it goes, cancelling gives 50% back. **Gravitational Stabilisers** (up to 4) and a Singular Reactor nearby speed it up.

*Gases and the armour screen*
- **Suit gases:** helium (cooling), oxygen (breathing, in Galacticraft space too), hydrogen (flight, dash), argon, krypton, heavy water, deuterium.
- **Strict gases:** every function works only with its gas. **Emergency mode** for Quantum and Exo without helium: HUD only, iron-armour protection.
- **Armour screen (K) redesigned:** all 4 pieces with gas bars, every function shows its gas and key, a Gases tab with "Refuel all".

*Ground and Space Bridge*
- A ring of gravity coils opens a portal **instantly to any point**, the Space Bridge into another dimension; an end opens **in the air** too, with a soft landing.
- Controller, Singularity Capacitor, ports, 6 modules. **Remotes** with five modes (Home, Fetch a friend, To me...), a **coordinator**, a **Singular armour link** (H / J keys).
- Ring wear, stability, overheating, familiar places. Every part has a recipe.

*Energy Converter*
- EU ⇄ **RF**, **Mekanism joules** (with the Mekanism Card) and **Galacticraft gJ** (with the Galacticraft Card). Rates 1 EU = 4 RF = 10 J, 5% loss, all in the config.
- Optional: without the RF API, Mekanism and Galacticraft it has no recipe, and one already placed works as an EU buffer.
- **Mekanism support is written against its API and not yet tested in game with Mekanism.** MJ, Universal Electricity and Botania are not supported.

*Machines and crafts*
- **Matter Compressor, liquid matter mode**; the Singular Reactor gathers Singular Matter; a Singular Matter Cell.

*Handbook*
- 14 chapters, 175+ articles. New chapters **Progression**, **Bridge**, **Components**, **Reference**. Text errors fixed, every item has an article.

*Misc*
- Generators, reactors, the field generator, the quarry, the shower and the transmitters are **placed switched off**, with no risk of blowing up.

**Fixed bugs**
- Five bug checks of the mod: **27 bugs**, plus about 25 fixes and refinements that followed them.
- **Gases and armour:** a client crash from the gas message; gases spent in creative mode; the station filled gases only after a full charge.
- **Dupes and losses:** an upgrade dupe when breaking with the screen open; a lost or a second Singular Core when cancelling a 4->5 modernisation; the reactor lost its Singular Matter.
- **Bridge:** leaked chunk tickets; an exit into someone else's private field; a client faking a scanner find.
- **Energy Converter:** RF out of a fractional remainder; buffers not saved by autosaves.
- A switched-off armour station or shower still took energy.

The full list is in `CHANGELOG.md`.
