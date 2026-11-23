🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен. Заменяет `SiliconAgeBeta-0.2.3.jar`: старый jar из папки `mods` уберите. Миры 0.2.3 открываются без изменений — тоннели и лётные поля появляются только на неоткрытой земле, старые посольства не меняются. Аддон Silicon Age: Vehicles 0.1.1-alpha работает с этой версией. На сервере и в LAN — одинаковый jar и одинаковые id измерения у клиента и сервера (раздел `dimension` конфига).

**Новое**

*Дела в Подложке*
- **Доска контрактов Старейшины:** 3 общих контракта, 1 местный (Гряды, Окисные поля, Мёртвый Узел), заказ Наладчиков и раз в неделю Большой заказ; новые цели — Штурмовики, враги в ЭМИ-бурю, сейфы лагерей, тайники Сигнала, срочная депеша до заката.
- **Звенья до 4 игроков** (`/scsquad`, щелчок Кредит-чипом по другу или команда scoreboard): убийство засчитывается каждому, кто нанёс от 10% урона; метка гибели напарника — цель моста и спасательного рейса.
- **Вызов налёта за залог 8 КЧ:** «Тревога» (с „Знакомого“) и «Тяжёлая ночь» (с „Своего“) — четвёртая волна с Экзо-палачом и Штурмовиком-Командиром, награда x1,5; после налёта — итог в чат.
- **Розыск:** раз в неделю Капитан Города объявляет именного Отступника в броне Квант или Экзо; награда — КЧ, „Доверие“ и Жетон розыска.
- **Собиратели** выходят днём к друзам и фумаролам, на них охотятся Линейщики и Мародёры; за спасение — „Доверие“ и подарок.
- **Протоколы Пробоя:** до 3 карт усложнения боя со Смотрителем, за каждую — Крупицы и шанс трофеев, за все три — схема окраски «Пробой».
- **Посты-ретрансляторы:** Ретранслятор ставят на зачищенный Мёртвый Узел и 5 минут держат против 3 волн — Наладчики строят пост с куполом, стражами и Причалом.
- **Старые тоннели Литографов** под плато: развязки, ниши с сундуками и мимиками, укрытие от ЭМИ-бури.
- Новые правила раздела `dimension`: `squads`, `raidAlarms`, `wanted`, `gatherers`, `breachProtocols`, `relayPosts`, `tunnels`; опция клиента `ambientLife` (дроны и мотыльки).

*Техника*
- **Сингулярный энергобанк:** мультиблок от 3 x 3 x 3 до 7 x 7 x 7, ячейки на 4 и 16 млрд EU, порты LV–SV, экран с графиком, компаратор; ключ снимает любой блок, порт уносит заряд и режим.
- **ЭУФ-литограф (QV) и процессоры:** ЭУФ-маска, Процессор (QV), Экзо-процессор (XV), Сингулярная матрица (SV); второй, более дешёвый вариант рецептов квантовых и экзо-панелей, Токамака, Экзо-реактора и накопителей QV–SV.
- **Порт данных** в стене Токамака XV и Сингулярного реактора: «Стоп» и «Разрешение» красным камнем; компаратор читает машины, генераторы, реакторы, карьеры и Экзо-установку.
- **Разборка** трофеев и своих старых вещей на Станции обслуживания брони — 25–35% деталей крафта, чипы и модули — целыми.
- **Добыча Подложки дома:** Экзо-установка в Подложке поднимает её руды; лом, нити, конденсат и нанитовая пыль перерабатываются в домашних машинах; Нанитовая паста чинит фотошаблоны, мишени и головки буров. Гафний из лишнего циркония.

*Лётное поле посольства* (вместе с аддоном Silicon Age: Vehicles)
- Новое посольство получает лётное поле Наладчиков: ВПП 200 x 9 с огнями и разметкой, перрон со стоянками, Терминал, вышка с маяком, Ангар, вертолётная площадка, створы захода с ILS. Где ровной земли мало — короткая ВПП 90 x 7 или только площадка «H».
- 5 Зенитных постов: огонь — над квадратом посольства (около 145 блоков от ядра) и створами, в остальном круге 256 — предупреждение. Допуск — „Доверие“ с „Знакомого“, пропуск 7700 раз в сутки.
- Колонка газов (водород и кислород в броню за КЧ), «Лётная книжка» (первая посадка в каждом посольстве — 2 КЧ и +5 „Доверия“), вкладка «Лётное поле» у Диспетчера, `/scembassy airfield`.
- Правило сервера `embassy.airfield`: `AUTO` (по умолчанию — поле только при установленном аддоне), `ALWAYS`, `NEVER`. Без аддона посольства генерируются как раньше. Посадки, ПВО, ATIS и услуги поля работают со следующей версией аддона Vehicles (с API лётного поля).

*Доработки по проверке 2026-11-04* — все 47 пунктов:
- **Энергия:** голограмма сети показывает банк как накопитель, подсказка порта — вход и выход отдельно, WAILA у банка и Порта данных; Порт данных не обрывает сжатие Сингулярного реактора.
- **Машины:** просторный экран ЭУФ-литографа и постоянный рецепт; бак гелия Станции улучшений сливается при пустом входе; утилизатор карьера берёт камень Края, породы Подложки и камень других модов.
- **Броня:** разборка не начинается без места в инвентаре; снятие «Ёмкости» с зарядом — со второго нажатия и с суммой сгоревших EU.
- **Мост и трубы:** трубы не теряют маршруты при розжиге соседней печи; справочник учитывает множитель цены сервера.
- **Подложка:** засады на Собирателей, розыск ждёт конца боя, оба модификатора Отступника видны в Анализаторе и WAILA; «Тревога» снимается через 3 суток с возвратом залога и не теряет его при остановке сервера.
- **Лётное поле:** створы с ILS расчищаются до 300 блоков, холм над глиссадой снимает ILS, лес больше не мешает полному полю (полных полей 76% вместо 47%), код ИКАО совпадает с аддоном.

**Исправленные баги** — полная проверка 2026-11-04, 26 исправлений, главные:
- *Энергобанк:* порт выхода с Industrial Upgrade отдаёт обещанные 4 пакета за тик; соседние порты не гоняют энергию по кругу и под IC2 не взрываются; контроллер и ячейка крафтятся только из разряженных накопителей без модулей; банк у границы загруженной области разбирается правильно.
- *Машины:* из одного фотошаблона больше не получается 16 ЭУФ-масок; Станция обслуживания брони не съедает жидкость из полного бака; ЭУФ-литограф теперь крафтится; третий ремонт алмазной головки пастой возможен.
- *Мост:* взрывы, визер и дракон больше не ломают контроллер моста с владельцем.
- *Подложка:* оборона Ретранслятора не идёт без игроков рядом; две обороны на одном участке больше не дают два поста; Отступник сохраняет имя и модификаторы после перезагрузки; сорванный налёт возвращает залог; схема «Пробой» открывается и участнику боя, который был не в сети.
- *Прочее:* Порт данных в оболочке реактора экранирует как свинец; фильтр карьера узнаёт руды Подложки; Сифон не сжигает энергию, если приёмник — аккумулятор; угол рулёжного круга и перрона не перекрашивается при каждом ремонте поля.

Полный список — в `CHANGELOG.md`; подробности — `docs/ideas-2026-10-26-sdelano.md`, `docs/plan-embassy-airfield-core.md`, `docs/nedorabotki-2026-11-04-sdelano.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional. Replaces `SiliconAgeBeta-0.2.3.jar`: remove the old jar from `mods`. 0.2.3 worlds open as they are - tunnels and airfields appear on unexplored land only, old embassies are never changed. The Silicon Age: Vehicles 0.1.1-alpha add-on works with this version. On a server and on LAN - the same jar and the same dimension ids on client and server (the config's `dimension` section).

**New**

*Things to do in the Substrate*
- **The Elder's contract board:** 3 common contracts, 1 local one (the Ridges, the Oxide Fields, a Dead Node), a Tuners' order and a weekly Big order; new goals - Troopers, hostiles in an EMP storm, camp safes, Signal caches, an urgent dispatch before sunset.
- **Squads of up to 4 players** (`/scsquad`, a Credit Chip click on a friend or one scoreboard team): a kill counts for everyone who dealt 10% of the damage; a mate's death mark is a bridge and rescue flight target.
- **A raid called for an 8-chip deposit:** "Alarm" (from Acquaintance) and "Heavy Night" (from Own) - a fourth wave with an Exo Executioner and a Commander Trooper, the reward x1.5; a summary in the chat after the raid.
- **Wanted:** once a week a City's Captain posts a named Renegade in Quantum or Exo armour; the bounty is chips, Trust and a Wanted Token.
- **Gatherers** walk out to the druses and fumaroles by day, Linemen and Marauders hunt them; a rescue gives Trust and a gift.
- **Breach Protocols:** up to 3 handicap cards for the Warden fight, each gives Crumbs and trophy chance, all three - the "Breach" paint scheme.
- **Relay posts:** a Relay set on a cleared Dead Node and held for 5 minutes against 3 waves - the Tuners build a post with a dome, sentries and a dock.
- **The Lithographers' old tunnels** under the plateaus: junctions, niches with chests and mimics, a shelter from the EMP storm.
- New rules of the `dimension` section: `squads`, `raidAlarms`, `wanted`, `gatherers`, `breachProtocols`, `relayPosts`, `tunnels`; the client option `ambientLife` (drones and moths).

*Technology*
- **The Singular Energy Bank:** a multiblock from 3 x 3 x 3 to 7 x 7 x 7, cells of 4 and 16 billion EU, LV-SV ports, a screen with a graph, a comparator; the wrench takes off any block, a port keeps its charge and mode.
- **The EUV Lithograph (QV) and processors:** the EUV mask, the Processor (QV), the Exo Processor (XV), the Singular Matrix (SV); a second, cheaper recipe for the Quantum and Exo panels, the Tokamak, the Exo Reactor and the QV-SV storages.
- **The Data Port** in the wall of the Tokamak XV and the Singular Reactor: "Stop" and "Permit" by redstone; comparators read machines, generators, reactors, quarries and the Exo rig.
- **Salvage** of trophies and your own old gear at the Armour Service Station - 25-35% of the crafting parts, chips and modules back whole.
- **Substrate loot at home:** the Exo rig in the Substrate mines its ores; scrap, threads, condensate and nanite dust are processed in the home machines; Nanite Paste mends photomasks, targets and drill heads. Hafnium from spare zirconium.

*The embassy airfield* (together with the Silicon Age: Vehicles add-on)
- A new embassy gets the Tuners' airfield: a 200 x 9 runway with lights and markings, an apron with stands, the Terminal, a tower with a beacon, the Hangar, a helipad, approach funnels with ILS. Where flat land is short - a 90 x 7 runway or the "H" helipad only.
- 5 AA emplacements: they fire over the embassy's square (about 145 blocks from the core) and the funnels, the rest of the 256 circle only warns. Clearance - Trust from Acquaintance, a 7700 pass once a day.
- A gas column (hydrogen and oxygen into armour for chips), the logbook (the first landing at each embassy - 2 chips and +5 Trust), the Airfield tab at the Dispatcher, `/scembassy airfield`.
- The server rule `embassy.airfield`: `AUTO` (the default - a field only with the add-on installed), `ALWAYS`, `NEVER`. Without the add-on embassies generate as before. Landings, air defence, ATIS and the field's services work with the next Vehicles version (with the airfield API).

*Improvements from the 2026-11-04 audit* - all 47 items:
- **Energy:** the network hologram shows the bank as storage, the port's tooltip shows input and output apart, WAILA for the bank and the Data Port; the Data Port no longer cuts a Singular Reactor's compression short.
- **Machines:** a roomy EUV Lithograph screen and a final recipe; the Upgrade Station's helium tank drains with an empty input; the quarry's trash disposal takes end stone, the Substrate's rock and other mods' stone.
- **Armour:** salvage won't start without inventory room; taking off a charged Capacity module needs a second click and tells how much EU burnt.
- **Bridge and conduits:** conduits keep their routes when a neighbouring furnace lights; the handbook counts the server's price multiplier.
- **Substrate:** ambushes on Gatherers, the wanted week waits for the fight to end, both of the Renegade's modifiers in the Analyser and WAILA; an "Alarm" call is withdrawn after 3 days with the deposit back and keeps the deposit over a server stop.
- **Airfield:** ILS corridors are cleared out to 300 blocks, a hill above the glide path takes the ILS away, forest no longer blocks a full field (76% full fields instead of 47%), the ICAO code matches the add-on's.

**Bugs fixed** - the full audit of 2026-11-04, 26 fixes, the main ones:
- *Energy Bank:* the output port sends the promised 4 packets a tick with Industrial Upgrade; adjacent ports no longer pass energy in circles nor explode under IC2; the controller and cell craft only from empty storages without upgrades; a bank at the edge of the loaded area unforms properly.
- *Machines:* one photomask no longer gives 16 EUV masks; the Armour Service Station no longer eats a full tank's fluid; the EUV Lithograph can now be crafted; the third paste repair of the diamond head works.
- *Bridge:* explosions, the Wither and the Dragon no longer break an owned bridge controller.
- *Substrate:* the Relay defence doesn't run with no players near; two defences in one region no longer give two posts; the Renegade keeps its name and modifiers after a reload; a raid that falls through returns the deposit; the "Breach" scheme unlocks for a fight participant who was offline.
- *Other:* the Data Port in a reactor shell shields like lead; the quarry filter knows the Substrate's ores; the Siphon wastes no energy when the receiver is a battery; the corner of the turning pad and the apron is no longer repainted at every field repair.

The full list is in `CHANGELOG.md`; details - `docs/ideas-2026-10-26-sdelano.md`, `docs/plan-embassy-airfield-core.md`, `docs/nedorabotki-2026-11-04-sdelano.md`.
