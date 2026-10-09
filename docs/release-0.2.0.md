🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен (общая энергосеть). Заменяет `SiliconAgeBeta-0.1.10.jar`: старый jar из папки `mods` уберите. Миры 0.1.10 открываются без изменений.

**⚠️ Изменения баланса**
- **Газовая турбина** тратит 2 мБ водорода в тик вместо 20 (64 EU с 1 мБ).
- **Токамак XV:** стабильность = восстановление минус штраф форсажа (с 1–2 «Форсажами» держится, с 3–4 падает); без дейтерия он не стоит на бесплатной паузе — гелий тратится; бланкет изнашивается по мощности; сломанный горящий XV гаснет, нужен новый розжиг.
- **Переработка руд:** мойка (15%) и центрифуга (10%) иногда дают лишнюю пыль — около 2,5 слитка с руды вместо 2.
- **Рывок Экзо** в воздухе — один раз до приземления; **модуль расширения бака** — только в генераторы с баками; **модуль качества** — только в машины с шансом брака.
- **Сингулярная станция:** первый выбор ветки нагрудника бесплатный; при отмене преобразования ядра возвращаются с половиной засчитанного заряда.

**Новое**

*Сайт и обновления*
- **Сайт мода в чате:** при каждом входе в мир или на сервер в чате (только у вас) появляется строка о сайте silicon-age.site — что там есть — и ссылки [Открыть сайт] [Гайды] [Справочник]. Отключается в конфиге: `site.message = false`.
- **Проверка обновлений:** раз за запуск игры мод спрашивает у сайта (silicon-age.site/version.json) номер последней версии и, если вышла новая, пишет в чат «Доступна новая версия … [Скачать] [Что нового]» и до трёх пунктов «что нового» на языке игрока; на выделенном сервере — операторам при входе и в лог. Без сети молчит, игру не задерживает. Конфиг: `updates.check`, `updates.channel` (beta / stable), `updates.url`.

*Доработки по итогам проверки (134 пункта)*
- *Баланс:* газовая турбина тратит 2 мБ водорода в тик; стабильность Токамака XV — восстановление минус штраф форсажа, без дейтерия он тратит гелий, бланкет изнашивается по мощности, сломанный горящий XV гаснет; жидкотопливные генераторы дожигают остаток бака; мойка (15%) и центрифуга (10%) иногда дают лишнюю пыль — около 2,5 слитка с руды; удача карьера работает и на «блочные» руды; побочные металлы (германий, индий, цинк, палладий) получили рецепты; первый выбор ветки нагрудника бесплатный; рывок Экзо в воздухе — один раз до приземления.
- *Новые настройки конфига:* раздел energy (взрывы машин, сила взрыва, разрушение блоков), bridge (включение, межпространственные переходы, множитель цены), singular (множители EU, газов, времени и очков), fieldClaimCheck, ic2CableCurrentLimit; клиентские настройки больше не перезаписываются сервером.
- *Приваты и мультиплеер:* карьер, узлы генератора поля, беспроводные блоки и преобразователь ломает только владелец (и оператор); переключатель «Запретить механизмы» в поле; поле защищает животных и рамки от чужих стрел; поле нельзя развернуть поверх чужого, даже выключенного; мост проверяет приваты других модов на всём вихре, у публичного моста посторонние не трогают питание, баки и закладки; повторные запросы согласия после «Отклонить» блокируются на 5 минут; зарядная плита может заряжать только владельца и команду; сканер Ш1 не показывает сундуки в чужих приватах; владелец карьера — по UUID.
- *Сохранение при снятии и крафте:* генераторы уносят улучшения, хранилища — режимы и выходы, машины — режим батареи и «Подогрев», Сингулярный реактор — режим подачи, контроллер моста — имя и id; крафт вокруг блока переносит энергию, баки и настройки; изношенные головки годятся в крафт; ячейки оставляют вёдра.
- *Интерфейс и подсказки:* подсказки «для чего» и «где делается» (Ctrl) у простых предметов, чипов, модулей, свинцового костюма, известняка; HUD зарядов сингулярного клинка; блок «Поднято» и кнопка «Вылить» у экзо-установки; «не просканировано» на карте скана; WAILA для карьера, поля, связки проводов; статус «Принимает» у приёмника; компактный справочник на маленьком экране, поиск рецептов без различия «ё/е»; экран брони закрывается клавишей K; клавиши брони срабатывают при беге и приседе.
- *Производительность:* затягиватель и трубы делают паузы, когда нечего переносить; энергосеть перестраивается только в своём измерении; предпросмотр моста ищет место в радиусе 6; очередь сингулярных сил ограничена 8 нажатиями.

**Исправленные баги — полная проверка мода, 94 ошибки**
- *Краши и дюпы:* двойной клик модулем при заблокированном модуле преобразователя ронял игру; модули, доложенные в стопку, не пересчитывались (рассинхрон тира, риск взрыва); ПКМ по генератору поля без владельца ронял чат клиента; действия брони выполнялись не в потоке сервера.
- *Приваты и защита:* меню Токамака XV и Сингулярного реактора больше не открывается в чужом приватном поле; «Чёрная дыра» и «Захват» сингулярной брони, энергооружие, ключ в режиме поворота, детали связок проводов и обзор моста уважают чужие поля и приваты других модов; при перекрытии приватных зон доступ решает любое поле, а не случайное; перенос генератора поля и Модуль связи больше не кладут приват на чужой клейм; на online-mode сервере владелец больше не «чужой» в собственном клейме.
- *Мост:* «Проверить место» не чаще раза в секунду (не грузит чанки); предпросмотр «Забрать друга» не выдаёт координаты игроков; сдвиг дальнего конца проверяет согласие и расстояние; калибровка сбрасывается только при изменении кольца; Космический мост держит чанк контроллера; портал не открывается при стабильности ниже порога; вихрь не ставится в траву и цветы; снятие ключом сохраняет износ и остывание; буфер энергопорта и заряд конденсатора не теряются при поломке, взрыве и каменной кирке; катушки гаснут, охладитель не копит «долг» гелия; точка из другого измерения для Наземного моста отклоняется.
- *Машины и генераторы:* оверклокер посреди операции больше не завершает её мгновенно; при machineSpeed < 1 EU/t не завышается; заряд розжига и жидкости в баках сохраняются после рестарта; соседние чанки не подгружаются опросом; батарея в Токамаке XV заряжается; генератор не срезает топливо сверх ёмкости бака; плавильни после смены мира не путают результаты; взрыв от перенапряжения не сжигает содержимое блока.
- *Карьер:* защита от жидкостей учитывает, что насос заберёт; не ползёт по воде при полном баке; «Тишина» глушит бур и луч; ремонт головки работает у остановленного карьера; статус «мешает чужая зона» виден; горящая редстоун-руда считается рудой; после перезагрузки курсор сверяется с областью.
- *Броня и инструменты:* смягчение падения требует газа на весь урон; дыхание в космосе Galacticraft тратит EU; скидка комплекта Квант не действует при перегреве; поглощение учитывает цену; аргон не тратится, если щит уже гасит лаву; замена блоков бура больше не делает поставленные блоки «природными»; коса и цепной разрез не сбрасывают неуязвимость целей; шёлк на кликнутой редстоун-руде даёт руду; лазер без цели не греет бур; квантовый ключ не кладёт неподходящие модули и не снимает второй блок.
- *Прочее:* криптон теперь получается — разделитель воздуха с водой даёт жидкий гелий и криптон; настройки звука клиента работают на сервере; клиент сам решает, проверять ли обновления; обзор сети, HUD (радиация, панель бура, «готово к модернизации»), подсказки руд (измерения из конфига) и NEI генераторов исправлены; справочник о целях поля исправлен.

Полный список по пунктам — `docs/nedorabotki-2026-10-09-sdelano.md`, все изменения — `CHANGELOG.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional (one energy network). Replaces `SiliconAgeBeta-0.1.10.jar`: remove the old jar from `mods`. 0.1.10 worlds open as they are.

**⚠️ Balance changes**
- **Gas turbine** burns 2 mB of hydrogen a tick instead of 20 (64 EU per mB).
- **Tokamak XV:** stability = recovery minus the overclock penalty (holds with 1-2 Overclockers, falls with 3-4); without deuterium it no longer idles for free - helium is still burnt; the blanket wears by power; a burning XV that is broken goes out and needs a new ignition.
- **Ore processing:** the washer (15%) and centrifuge (10%) sometimes give an extra dust - about 2.5 ingots per ore instead of 2.
- **Exo dash** in the air - once per landing; the **tank extension** fits only generators with tanks; the **quality module** only machines with a scrap chance.
- **Singular station:** the first chestplate branch is free; cancelling a conversion gives the cores back with half the counted charge.

**New**

*Site and updates*
- **The mod's site in the chat:** on every world or server join the chat (yours only) tells about silicon-age.site - what's there - with [Open the site] [Guides] [Handbook] links. Off in the config: `site.message = false`.
- **Update check:** once a game session the mod asks the site (silicon-age.site/version.json) for the latest version and, when a newer one is out, says "Version … is out [Download] [What's new]" in the chat with up to three points of what's new in the player's language; on a dedicated server - to operators as they log in and in the log. Silent without a network, never holds the game up. Config: `updates.check`, `updates.channel` (beta / stable), `updates.url`.

*Improvements from the audit (134 items)*
- *Balance:* the gas turbine burns 2 mB of hydrogen a tick; Tokamak XV stability is recovery minus the overclock penalty, without deuterium it still burns helium, the blanket wears by power, a burning XV that is broken goes out; liquid-fuel generators burn the tank to the end; the washer (15%) and centrifuge (10%) sometimes give an extra dust - about 2.5 ingots per ore; quarry Fortune works on block-dropping ores too; by-product metals (germanium, indium, zinc, palladium) got recipes; the first chestplate branch is free; the Exo air dash is once per landing.
- *New config options:* section energy (machine explosions, blast power, block damage), bridge (on/off, cross-dimension, cost multiplier), singular (EU, gas, time and score multipliers), fieldClaimCheck, ic2CableCurrentLimit; client-only options are no longer overwritten by the server.
- *Claims and multiplayer:* the quarry, field generator nodes, wireless blocks and the converter can be broken only by their owner (and ops); a "No mechanisms" switch for fields; fields protect animals and frames from strangers' arrows; a field can't be laid over another one, even switched off; bridges check other mods' claims over the whole vortex, strangers can't touch a public bridge's power, tanks and bookmarks; repeated consent requests after Decline are blocked for 5 minutes; the charge pad can charge only its owner and team; the Sh1 scanner hides chests in others' claims; the quarry owner is kept by UUID.
- *Kept on dismantling and crafting:* generators keep their upgrades, storage keeps its modes and outputs, machines keep the battery mode and Preheat, the Singular reactor its feed mode, the bridge controller its name and id; crafting around a block carries energy, tanks and settings; worn drill heads fit the next recipe; cells leave buckets.
- *Interface and tooltips:* "what for" and "made in" (Ctrl) tooltips for simple items, chips, modules, the lead suit and limestone; a HUD for the Singular blade's charges; "Brought up" and "Drain" on the Exo rig; "not scanned" on the scan map; WAILA for the quarry, field and conduit bundle; "Receiving" status for receivers; a compact handbook on small screens, recipe search ignoring ё/е; the armour screen closes with K; armour keys work while sprinting and sneaking.
- *Performance:* the puller and pipes back off when there is nothing to move; the energy net rebuilds only in its own dimension; the bridge preview looks for a place within 6 blocks; the Singular powers queue is capped at 8 presses.

**Bugs fixed - a full check of the mod, 94 bugs**
- *Crashes and dupes:* double-clicking a module while the converter's module was locked crashed the game; modules added to a stack weren't recounted (tier desync, explosion risk); right-clicking an ownerless field generator broke the client's chat; armour actions ran outside the server thread.
- *Claims and protection:* the Tokamak XV and Singular reactor screens no longer open inside someone else's private field; the Singular suit's Black Hole and Grab, energy weapons, the wrench's rotate mode, conduit bundle parts and the bridge screen respect other fields and other mods' claims; with overlapping private zones every field counts, not a random one; moving a field generator or the Link Module no longer lays a private zone over a claim; on online-mode servers the owner is no longer a stranger in their own claim.
- *Bridge:* the place check at most once a second (no chunk loading); the Fetch-a-friend preview no longer leaks player coordinates; moving the far end checks consent and distance; calibration resets only when the ring changes; the Space bridge keeps the controller's chunk loaded; no portal below the collapse threshold; no vortex cells in grass or flowers; wrench dismantling keeps wear and cooldown; the energy port buffer and capacitor charge survive breaking, blasts and stone pickaxes; coils go dark, the ring cooler keeps no helium debt; a point from another dimension is refused for a Ground bridge.
- *Machines and generators:* an overclocker inserted mid-operation no longer finishes it at once; machineSpeed < 1 no longer inflates EU/t; ignition charge and tank fluids survive a restart; neighbour polling no longer loads chunks; the battery in the Tokamak XV charges; generators no longer cut fuel above the tank size; smelters no longer mix up results after a world switch; an overvoltage blast no longer burns the block's contents.
- *Quarry:* the liquid guard knows what the pump will take; no crawling over water with a full tank; Silence mutes the drill and beam; head repair works on a stopped quarry; the "blocked by a zone" status shows; lit redstone ore counts as ore; the cursor is checked against the area after a reload.
- *Armour and tools:* fall damping needs gas for the whole fall; breathing in Galacticraft space costs EU; the Quantum set discount is off while overheated; absorption respects its cost; no argon drain when the shield already stops lava; the drill's replace no longer turns placed blocks "natural"; scythe and chain cut no longer reset the targets' immunity; silk touch on the clicked redstone ore gives the ore; the laser doesn't heat the drill with no target; the quantum wrench pastes only fitting modules and no longer takes a second block.
- *Other:* krypton can now be made - the Air Separator with water gives liquid helium and krypton; client sound settings work on servers; the client decides itself whether to check for updates; network overview, HUD (radiation, drill panel, ready-to-upgrade), ore tooltips (dimensions from the config) and the generators' NEI fixed; the handbook on field targets corrected.

Item-by-item list in `docs/nedorabotki-2026-10-09-sdelano.md`, every change in `CHANGELOG.md`.
