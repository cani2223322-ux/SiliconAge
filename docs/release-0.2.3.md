🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен. Заменяет `SiliconAgeBeta-0.2.2.jar`: старый jar из папки `mods` уберите. Миры 0.2.2 открываются без изменений — посольства появляются только на неоткрытой земле. Аддон Silicon Age: Vehicles работает с этой версией. На сервере и в LAN — одинаковый jar и одинаковые id измерения у клиента и сервера (раздел `dimension` конфига).

**Новое**

*Посольства Наладчиков* — редкие большие города в Верхнем мире: внешние станции Литографов, отрезанные Пробоем. Для новичка и друга по LAN — вход в Подложку без своего Космического моста.
- **Город:** купол радиусом 88–96, стена с четырьмя вышками, Сердце с Ядром и Залом посла, мастерские, склады снабжения, оранжерея, жилые ячейки; у главных ворот — Вокзал прокола с Большим кольцом. 18–22 жителя (Посол, Диспетчеры прокола, Интенданты, Технологи, Металлурги, Энергетик и другие) и 12 стражей. Защита — как у поселений Подложки; налётов, бурь, караванов и радиации в Верхнем мире нет.
- **Где:** одно посольство на регион 2048 × 2048, два — не ближе 1024 блоков, ни одно — ближе 1500 к точке появления; одно гарантировано в 1300–2500 блоках от неё. Только ровная, сухая, открытая земля (равнины, саванна, пустыня) и только там, где ни один чанк ещё не создан. Найти: **Пеленгатор посольства** (компас + 2 Транзистора + Кремниевая пластина; направление, расстояние и позывной в 6000 блоках), строка при первом входе, бирюзовый купол и луч над кольцом, видный за 1024 блока.
- **Рейсы через Большое кольцо:** Диспетчер прокола продаёт за Кредит-чипы рейсы в Подложку — ознакомительный к «Первому причалу», к «Первому причалу» и к известному причалу, спасательный к Метке гибели. Цена зависит от «Доверия», до 4 пассажиров за рейс (друзья по LAN — вместе), билет «туда-обратно» на 10% дешевле, можно оплатить рейс другу.
- **Обратно:** у причалов Посёлков, Городов и «Первого причала» Подложки теперь стоит Диспетчер: «Домой» на Вокзал посольства, обратная половина билета, из Города — в другое посещённое посольство. 2 рейса в сутки туда и назад вместе.
- **Обмен:** продажи Наладчикам, зарядка у Энергетика, Капсула возврата и Карта поселений; Интендант скупает хлеб, брёвна, уголь и железо, у Посла — свои контракты («Снабжение экспедиции», «Депеша домой»). «Доверие» за дела в посольстве — до 599.
- **Сервер:** новый раздел конфига `embassy` — `embassies`, `embassyGrid`, `embassyChance`, `embassySpawnMin`, `embassyRingMin` / `embassyRingMax`, `flightPriceMultiplier`, `flightsPerDay`. Команда `/scembassy` (`list`, `where`, `repair`; `/scemb`) для операторов. Шесть новых статей главы «Подложка» в справочнике, страницы NEI, подсказки.

*Доработки по проверке 2026-10-20* — все 74 рекомендованных улучшения:
- **Энергия:** красная кнопка питания видит блоки IC2; владельцы блоков — по UUID, а не по нику; Shift + ПКМ бронёй больше не берёт энергию из чужого беспроводного блока; накопитель не принимает лишний модуль ёмкости; аккумулятор говорит, до какого уровня заряжает и есть ли в цели подходящий предмет.
- **Машины:** кнопки слива баков — со второго щелчка; экран показывает фактический шанс брака; индукционная печь сама делит вход на два потока; дробилка берёт руды других модов из словаря руд; рецепты ищутся быстрее; компрессор материи не съедает броню и инструменты.
- **Генераторы:** новый раздел конфига `generators` — множители выработки и расхода топлива и список топлива генератора внутреннего сгорания (с топливами EnderIO, Thermal, Forestry); экономайзер больше не даёт энергию из воды; предупреждение о слабом кабеле на выходе; ветрогенератор считает высоту от уровня земли мира; порты в стене реакторов экранируют излучение.
- **Карьер:** не разрушается взрывами; «Удержание чанков» держит и соседний чанк с сундуком выдачи; ведро или капсула по карьеру заливает и сливает баки; квантовый ключ копирует настройки; прогноз считает только копаемые клетки; настройки `balance.quarryEnergy` и `balance.quarryChunkLoading`.
- **Броня и оружие:** HUD радиации называет причину, по которой щит не работает; свои иконки у новых чипов; ПЭ-1 предупреждает о перегреве вовремя и не даёт молча менять настройки во время набора; резак, эмиттер и винтовка — со звуком, лучом и перезарядкой; боссы дают очки не чаще 3 раз за 10 минут.
- **Мост:** детали с содержимым ломают только владелец и друзья; загрузка связки не сбрасывает маршруты труб; конец не открывается над потолком Ада; координатор проверяет место для обоих мостов.
- **Подложка и посольства:** сообщения бури, мглы и Выброса с числами, строка «Мгла: М:СС» в HUD; меньше рывков на новой земле; множители погоды, Сигнала, Смотрителя и фона радиации в разделе `dimension`; лимит мобов растёт с числом игроков; неиспользованный талон рейса можно сдать; окно обмена обводит слоты, которые уйдут.
- **Прочее:** подсказки и справочник показывают настройки сервера; все комментарии в файле настроек — на английском и русском.

**Исправленные баги**
- **Вылет «Exception ticking world»** (NullPointerException в `World.func_147467_a`) при быстром полёте по новой земле. Это гонка в самом Forge 1.7.10: чанк, который уже снят с очереди сохранения, но ещё не записан в файл, на миллисекунды «исчезал». Теперь мод держит такой чанк читаемым до записи (отключить — `-Dsc.noChunkSaveGuard=true`).
- **Знаки в ресурс-паках шрифта:** паки, перерисовывающие расширенный ASCII (например, русский фикс для Thermal Expansion), превращали кавычки «», «×», «±», «·», «°» в буквы. Теперь кавычки „…“ (в английском — “…”), а знаки — «x», «+/-», «•», «˚».
- **Полная проверка 2026-10-20 — 46 исправлений**, главные:
  - *Энергия:* при выключенных взрывах перегруженная машина только дымит и не отнимает энергию у сети; зарядная плита и аккумулятор заряжают предметы на полной скорости, а не в 5–10 раз медленнее; беспроводные блоки не показывают чужим координаты второго конца; крафт реактора из генераторов больше не уничтожает их модули.
  - *Карьер:* удача с дроблением не размножает руды, которые дробилка не перерабатывает; поворот ключом и остановка сразу видны у всех игроков.
  - *Броня и оружие:* ПЭ-1 больше не залипает после смены измерения, Сифон останавливается при рассинхроне; рывок Экзо верхом не тратит ресурсы впустую; блок клинка снимается при смене измерения; «Чутьё охотника» требует криптон; Сингулярный бур не отправляет добычу в сундук без «Связи с сундуком».
  - *Мост:* посторонние не могут сливать баки чужого моста; при нехватке газа вихрь схлопывается через 3 с, а не держится минуту с перегревом; «Y авто» работает в незагруженных чанках и в Аду.
  - *Подложка и посольства:* клиент с выключенным у себя измерением не вылетает при входе в Подложку сервера; новый билет «туда-обратно» не стирает оплаченную обратную половину; Часовой не застревает откинутым после Точки ПЭ-1.
  - *Тексты:* котёл показывает настоящий выход пара, генераторы — настоящий максимум, Токамак XV — свои числа в NEI; вернулись полные подсказки модулей; на сервере подсказки руд показывают измерения генерации сервера.

Полный список — в `CHANGELOG.md`, описание посольств — `docs/idea-embassy-substrate.md` и `docs/plan-substrate.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional. Replaces `SiliconAgeBeta-0.2.2.jar`: remove the old jar from `mods`. 0.2.2 worlds open as they are - embassies appear on unexplored land only. The Silicon Age: Vehicles add-on works with this version. On a server and on LAN - the same jar and the same dimension ids on client and server (the config's `dimension` section).

**New**

*The Tuners' Embassies* - rare big cities in the Overworld: the Lithographers' outer stations cut off by the Breakdown. For a newcomer and a LAN friend - a way into the Substrate without a Space Bridge of one's own.
- **The city:** a dome of radius 88-96, a wall with four towers, the Heart with the core and the Ambassador's hall, workshops, supply depots, a greenhouse, living cells; at the main gate - the Puncture Vokzal with the Great Ring. 18-22 settlers (the Ambassador, Puncture Dispatchers, Quartermasters, Technologists, Metallurgists, the Power Engineer and others) and 12 guards. Protection as at the Substrate's settlements; no raids, storms, caravans or radiation in the Overworld.
- **Where:** one embassy a 2048 x 2048 region, two never closer than 1024 blocks, none closer than 1500 to the spawn; one guaranteed 1300-2500 blocks from it. Only flat, dry, open land (plains, savanna, desert) and only where no chunk exists yet. To find one: the **Embassy Direction Finder** (a compass + 2 Transistors + a Silicon Wafer; the direction, distance and call sign within 6000 blocks), a line at the first login, the turquoise dome and a beam over the ring seen 1024 blocks off.
- **Flights through the Great Ring:** for Credit Chips the Puncture Dispatcher sells flights into the Substrate - the introductory one to the First Dock, to the First Dock and to a dock you know, the rescue to your death mark. The price depends on Trust, up to 4 passengers a flight (LAN friends together), a round trip 10% off, you can pay a friend's flight.
- **Back:** the docks of the Substrate's Settlements, Cities and the First Dock now have a Dispatcher: "Home" to the embassy's Vokzal, a ticket's return half, from a City - to another embassy you have been to. 2 flights a day, there and back together.
- **Trade:** sales to the Tuners, charging at the Power Engineer, the Return Capsule and the Settlement Map; the Quartermaster buys bread, logs, coal and iron, the Ambassador has contracts of his own ("Expedition supplies", "A dispatch home"). Trust from an embassy's deeds goes up to 599.
- **Server:** a new config section `embassy` - `embassies`, `embassyGrid`, `embassyChance`, `embassySpawnMin`, `embassyRingMin` / `embassyRingMax`, `flightPriceMultiplier`, `flightsPerDay`. The command `/scembassy` (`list`, `where`, `repair`; `/scemb`) for operators. Six new articles of the handbook's Substrate chapter, NEI pages, tooltips.

*Improvements from the 2026-10-20 audit* - all 74 recommended ones:
- **Energy:** the red power button sees IC2 blocks; block owners by UUID, not by name; Shift + right-click with armour no longer takes energy from someone else's wireless block; storage refuses a capacity module it can't use; the battery tells up to which tier it charges and whether the target holds a fitting item.
- **Machines:** tank dump buttons need a second click; the screen shows the real defect chance; the Induction Furnace splits its input into two streams itself; the crusher takes other mods' ores from the ore dictionary; recipes are found faster; the Matter Compressor no longer eats armour and tools.
- **Generators:** a new config section `generators` - output and fuel use multipliers and the Combustion Generator's fuel list (with EnderIO's, Thermal's and Forestry's fuels); the Economizer no longer makes energy out of water; a warning about a weak output cable; the wind generator counts height from the world's ground level; ports in the reactors' walls shield radiation.
- **Quarry:** explosions no longer destroy it; the Chunk Loader also holds the neighbour chunk with the output chest; a bucket or capsule on the quarry fills and drains its tanks; the Quantum Wrench copies its settings; the forecast counts only cells really dug; settings `balance.quarryEnergy` and `balance.quarryChunkLoading`.
- **Armour and weapons:** the radiation HUD names why the shield doesn't work; the new chips have their own icons; PE-1 warns about overheating in time and no longer silently drops settings changed while charging; the cutter, emitter and rifle fire with a sound, a beam and a cooldown; bosses give points no more than 3 times in 10 minutes.
- **Bridge:** parts holding things can be broken only by the owner and friends; loading a bundle no longer resets pipe routes; an end never opens above the Nether's ceiling; the Coordinator checks room for both bridges.
- **Substrate and embassies:** storm, haze and Emission messages with numbers, a "Haze: M:SS" HUD line; fewer stutters on new land; weather, Signal, Warden and background radiation multipliers in the `dimension` section; the mob cap grows with the players; an unused flight ticket can be handed back; the exchange window outlines the slots that will go.
- **Other:** tooltips and the handbook show the server's settings; every comment in the config file is in English and Russian.

**Bugs fixed**
- **The "Exception ticking world" crash** (a NullPointerException in `World.func_147467_a`) when flying fast over new land. It is a race in Forge 1.7.10 itself: a chunk already taken off the save queue but not yet written to its file was "gone" for milliseconds. The mod now keeps such a chunk readable until it is written (off with `-Dsc.noChunkSaveGuard=true`).
- **Signs in font resource packs:** packs that redraw extended ASCII (e.g. the Russian fix for Thermal Expansion) turned the «» quotes, "×", "±", "·", "°" into letters. Quotes are now “…” (in Russian „…“), the signs "x", "+/-", "•", "˚".
- **The full audit of 2026-10-20 - 46 fixes**, the main ones:
  - *Energy:* with explosions off an overloaded machine only smokes and takes no energy from the network; the charging pad and the battery charge items at full speed, not 5-10 times slower; wireless blocks no longer show strangers the other end's coordinates; crafting a reactor from generators no longer destroys their modules.
  - *Quarry:* fortune with crushing no longer multiplies ores the crusher can't process; a wrench turn and a stop are seen by every player at once.
  - *Armour and weapons:* PE-1 no longer sticks after a dimension change, the Siphon stops on a desync; the Exo dash while riding no longer wastes resources; the blade's block ends on a dimension change; Hunter's Sense needs krypton; the Singular Drill doesn't send loot to the chest without the chest link.
  - *Bridge:* strangers can't drain someone else's bridge tanks; short of gas the vortex collapses in 3 s instead of holding a minute and overheating; "Y auto" works in unloaded chunks and in the Nether.
  - *Substrate and embassies:* a client with the dimension off at home no longer crashes entering the server's Substrate; a new round-trip ticket no longer wipes a paid return half; the Sentinel no longer stays thrown back after a PE-1 Point.
  - *Texts:* the boiler shows its real steam output, generators their real maximum, the Tokamak XV its own numbers in NEI; the modules' full tooltips are back; on a server ore tooltips show the server's generation dimensions.

The full list is in `CHANGELOG.md`, the embassies' design in `docs/idea-embassy-substrate.md` and `docs/plan-substrate.md`.
