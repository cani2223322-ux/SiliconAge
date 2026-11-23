# Лётное поле посольства — техплан ядра (фазы А1 + А2, API для аддона, ПВО посольства) и разбивка на пакеты (2026-10-27)

Дизайн: `docs/idea-embassy-airfield.md` (сводное предложение, все числа и проверенные факты кода; **решения пользователя 2026-10-26** в конце — они главнее предложения). Общий план с частью аддона: `docs/plan-embassy-airfield.md`. Техплан Ф6 (посольства): `docs/plan-substrate-tech-f6.md` — его правила работы, сборка и запреты действуют и здесь.
Здесь — архитектура ядра под существующий код (`SiliconAge` 0.2.3 + коммит bf114b4) и разбивка на 5 параллельных пакетов (**G, P, R, T, D**) с **непересекающимися файлами**. Аддон (`SiliconAgeVehicles`) в этом прогоне **не трогается**; его часть — отдельный прогон по `docs/plan-embassy-airfield.md` §3.
Java 6 (без лямбд, diamond, try-with-resources, switch по String, API Java 7+), MCP-имена, Forge 1.7.10-10.13.4.1614.
Сборка:
`T=/d/AI/Claude/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /d/AI/Claude/MineMod/SiliconAge; gradle clean build --offline -q`
Строки «Read timed out» и 404 безвредны. runServer не запускать, сборка — всегда clean build. Проверка reobf: `unzip -p build/libs/SiliconAge-0.2.3.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` печатает 1. Каждый пакет обязан собираться сам по себе поверх ядра.

**Решения пользователя (кратко, обязательны):** название «Лётное поле посольства» (в UI «Лётное поле „Тантал-1“»), Аэровокзал, Вышка КДП со Светомаяком, Перрон, МС-1…МС-4, вертодром „Н“, Ангар, Колонка обслуживания, памятник «Последний челнок Литографов»; ВПП 200 × 9 сбоку вдоль главной улицы в нынешнем квадрате (REACH_MAX 135, RANGE 10, `explored()` и правила места не меняются), запасные SHORT 90 × 7 + „Н“ и HELI; **поле строится только с аддоном** (`embassy.airfield` = AUTO | ALWAYS | NEVER, по умолчанию AUTO = `Loader.isModLoaded("SiliconAgeVehicles")` в момент решения о старте); **старые посольства не трогаются** (никакого дооснащения, никакой команды дооснащения); купол — без стены и штрафов; **у посольства своё ПВО** (огонь — аддон, ядро — посты, зона, допуск, пропуск 7700); услуги — таблица §7 предложения; новых жителей нет; памятник, светомаяк, ATIS, «Лётная книжка»; ИКАО X + две буквы элемента + буква номера.

## 0. Ядро (уже написано, собирается)

Правит **только координатор**. Нужна правка ядра или чужого файла — описать её в отчёте (файл, место, текст). Заготовки (стабы) принадлежат пакетам, указанным в их Javadoc: сигнатуры, имена, меты, ключи NBT и числа в них фиксированы, тела — пакетов.

| Файл (`com/sc/…`) | Что |
|---|---|
| `embassy/EmbassyAirfieldSC.java` | **Геометрия поля, написана целиком** (§3): варианты NONE 0 / HELI 1 / SHORT 2 / FULL 3, сторона по сиду, все числа, `Field` (неизменяемый: прямоугольники в кадре (a, q), `x/z/frame/world`, `levelPast`, `onShape`, `inZone`, `neutral`, `inAdZone`, `zoneBox`, `aaBlock`, `beaconBlock`, `towerDesk`, `columnBlock`, `windsockBlock`, `thresholdRow`, `lights(meta)`, `localizer`, `glideSlope`, `block`, `lots()`), решение (`buildsNow`, `decideFor`, `sideOf`), запись (`decisionOf`, `writeDecision`, `decisionOfStart`, `fieldOf` с кэшем, `zoneBox`), `revision` / `changed` / `clearAll`, ИКАО (`icao`, `ICAO_ELEMENT`), номера ВПП (`heading`, `number`), самопроверка **`problem(plan)`** |
| `embassy/EmbassyAirRulesSC.java` | **Все числа и чистые решения услуг, «Лётной книжки» и ПВО** (§7, §6.2): услуги S_* и их ключи, MIN/MAX_LEVEL, BASE, UNIT, PER_SERVICE, PER_DAY, бесплатное, коды оплаты PAY_*, `refusal`, `price`, `units`, `admitted`, `passAvailable`, `logbookChips`, `armourUnits`; AD_RADIUS 256, WARN_SECONDS 10, PASS_SECONDS 60, ESCORT_RANGE 64, ADMIT_LEVEL «Знакомый»; ключи хранилища |
| `embassy/EmbassyAirDefenceSC.java` | **Сторона ядра ПВО посольства, написана целиком** (§6.2): `admitted`, `escortOf` (звено ПД-2 рядом), `holdFire`, `requestPass` / `passUntil` (журнал 7700 в `LocalRec.extra` «X»), строки чата `line` / `tell` (AD_*) |
| `embassy/api/EmbassyAirApiSC.java`, `AirfieldInfoSC.java`, `IEmbassyAirHookSC.java` | **Публичное API для аддона, написано целиком** (§5): API_VERSION = 1, крюк, данные полей в мировых координатах, нейтральное небо, купол, уровни, талон, оплата, «Лётная книжка», ПВО |
| `embassy/ComponentEmbAirSC.java` | База кусков поля (§4.3): NBT «EmbAir» {kind, side, ils, version}, `field()`, помощники кадра (a, q): `atq`, `airq`, `surface`, `machineq`, `clipq`, `dirOfq`, `tileq`; абстрактный `buildField` |
| `block/BlockAirfieldSC.java`, `block/ItemBlockAirfieldSC.java` | Блок «Покрытие лётного поля» `embassyAirfieldSC` (§2.4): меты, `LIGHT`, `runway(meta)`, правила (неломаемый, взрывостойкий, без дропа, без pick-block, **не непрозрачный куб — снег не ложится**, грани между своими не рисуются); предмет — имя по мете и подсказка TooltipSC. Вид (иконки) — P |
| `block/BlockShowcase2SC.java`, `block/ItemBlockShowcase2SC.java` | Меты витрин в конце: 10 `towerConsole`, 11 `serviceColumn`, 12 `airfieldBeacon`, 13 `aaPost`; `NAMES`, `LIGHT` (6 / 5 / 14 / 2); щелчок по колонке → `EmbassyAirServiceSC.useColumn` (сервер); подсказки новых мет — свои ключи `sc.tooltip.airfield.*`. Вид — P |
| `embassy/EmbassyBlocksSC.java` | Регистрация `embassyAirfieldSC` (всегда, и без аддона: мир, созданный с аддоном, грузится без него) |
| `embassy/EmbassyLayoutSC.java` | LOT_RUNWAY 14 … LOT_AIR_DEFENCE 20 (в конец); `Plan.air`, `Plan.field`; `plan(seed, site, floor, air)` (старый `plan(seed, site, floor)` = без поля); `fill` пишет «X»; `planOf` читает «X» |
| `embassy/MapGenEmbassySC.java` | Решение о поле только для **новой** записи (`decideFor`), план с полем, лог варианта, `changed`; у существующей записи — её «X» (никогда не меняется); `covers()` — охват `max(REACH_MAX, FUNNEL_REACH)` и куски старта (аудит 2026-11-04); RANGE 26 |
| `embassy/ComponentEmbGroundSC.java` | NBT «Air» {kind, side, ils, version} (только у стартов с полем), `airDecision()`, `field()`. Тела выравнивания и засыпки под формой — G |
| `embassy/EmbassyPiecesSC.java` | Имена кусков дописаны: `Runway`, `Apron`, `Terminal`, `Hangar`, `Helipad`, `Funnel`, `AirDefence` (проверка длины имён и классов); куски поля строятся после городских из `Field.lots()`. **В этом прогоне файл — координатора** |
| `embassy/EmbassySC.java` | `clearAll` → `EmbassyAirfieldSC.clearAll`, `EmbassyAirServiceSC.clearAll`, `EmbassyAirApiSC.clearAll` |
| Заготовки G | `embassy/EmbassyAirSiteSC.java` (`choose` — заглушка: FULL на стороне по сиду, оба ILS) |
| Заготовки P | `embassy/ComponentEmbRunwaySC`, `…ApronSC`, `…TerminalSC`, `…HangarSC`, `…HelipadSC`, `…FunnelSC`, `…AirDefenceSC` (конструкторы и высоты фиксированы, `buildField` пустой) |
| Заготовки R | `embassy/EmbassyAirServiceSC.java` (`useColumn`, `quote`, `pay`, `firstLanding`, `forget`, `clearAll` — пустые; `landed` — написан) |
| Заготовки T | `embassy/EmbassyAirServerSC.java` (`onMessage`, `sendState`, `state` — пустые) |
| Заготовки D | `debug/SelfTestAirfieldSC.java` (`run`, `check`), `debug/WorldTestAirfieldSC.java` (без обработчиков, флаг `finished`) |

### 0.1 Правки ядра в общих файлах (готово, остальным их не трогать)
- `dim/SubstrateConfigSC`: `embassy.airfield` = AUTO | ALWAYS | NEVER (§2.1) — **не правило** (приватное поле, клиенту не уходит; число правил самотестов не меняется); `loadAirfield` — отдельный метод после `loadEmbassies` (самотест Ф6 перечитывает тот на пустых конфигурациях); `embassyAirfield()`, `setEmbassyAirfield(mode)` (для мирового теста).
- `settle/SettlementSC`: `airfieldAt(w, x, z)` (зона поля, по индексу регионов), `airfieldProtecting(w, x, y, z)` (зона и `y ≥ floorY − FOUNDATION`), `embassyGroundAt` — диск **или** зона поля, `refusesBridgeEnd` — и в зоне поля.
- `settle/SettlementRegistrySC.Settlement`: `reach()` включает `zoneBox` поля (поиск по индексу у края региона не промахнётся); `inBelt` — и зона поля (ПЖ-2 для жителей на постах поля, стража видит поле своим); `inAirfield`, `airfieldBox()`; `overlapping` — поле игрока не может зайти на зону поля (ПП-8).
- `settle/RosterSC.fromEmbassyPlan`: потерянная запись делается заново с решением из куска земли старта (`decisionOfStart`, NBT «Air»).
- `settle/ReputationSC`: `SRC_AIR` = 14 (в конец).
- `settle/SettlementBlocksSC.unbreakable`: и `EmbassyBlocksSC.airfield`. `settle/SettlementCompatSC`: `embassyAirfieldSC` (все меты) в чёрный список AE2 Spatial (витрины поля уходят с `showcase2SC` по `NAMES.length`).
- `settle/SettleNetSC`: виды AIR_STATE 5, AIR_RESULT 6 (→ клиенту), AIR_DO 8, AIR_REFRESH 9 (→ серверу; обработчик отдаёт их `EmbassyAirServerSC.onMessage`).
- `settle/trade/TradeServerSC.open`: у Диспетчера после `FlightServerSC.sendState` — `EmbassyAirServerSC.sendState(p, t)` (хук координатора; остальное в файле не трогать).
- `SCMod`: при `-Dsc.worldtest` — `WorldTestAirfieldSC` на шине FML (после `WorldTestEmbassyFlySC(false)`); `debug/SelfTestSC.run`: `SelfTestAirfieldSC.run()` после `SelfTestEmbassySC`.

### 0.2 Что уже работает с одним ядром
Регистрируется всё (блок покрытия и предмет, меты витрин, имена кусков, сообщения, конфиг). При AUTO **без аддона** (среда разработки) поле не строится: посольства как раньше, записи без «X», всё новое молчит. При ALWAYS (или с аддоном) новое посольство получает запись «X» FULL (заглушка G) и куски поля, которые пока **ничего не ставят**; охрана (через `SettlementSC.airfieldAt`) уже знает зону, индекс регионов и `overlapping` — тоже; API отвечает данными поля. Самопроверка геометрии уже зелёная: `problem()` = null на 648 планах (12 сидов × r 88…96 × HELI/SHORT/FULL × обе стороны), и `EmbassyQuartersSC.problem` не сломан (проверено координатором отдельным прогоном).

## 1. Регистрация и порядок

| Этап | Что | Где |
|---|---|---|
| preInit | `SubstrateConfigSC.load` → `loadAirfield` (`embassy.airfield`) | ядро |
| preInit | Блок `embassyAirfieldSC` (`EmbassyBlocksSC`, после кольца); меты витрин 10–13 — тот же `showcase2SC` | ядро |
| preInit | `SettleNetSC`: новые виды 5, 6, 8, 9 (тот же канал `SiliconAgeDim`, дискр. 1/2 — новых дискриминаторов нет) | ядро |
| init | `EmbassySC.init` → `MapGenEmbassySC.registerStructures` → `EmbassyPiecesSC.register` (+ 7 имён поля) — всегда | ядро |
| init (аддон) | аддон: `EmbassyAirApiSC.registerHook(...)` после проверки `API_VERSION` | аддон (позже) |
| мир 0, `getStructureStart` | новая запись: `EmbassyAirfieldSC.decideFor` (конфиг + `EmbassyAirSiteSC.choose` по слоям биомов) → `plan(…, air)` → запись с «X», кусок земли с «Air», куски поля → `changed` (ревизия, крюк) | ядро + G |
| мир 0, `ReplaceBiomeBlocks` | выравнивание массивов чанка **по форме** (`Field.levelPast`) | G |
| мир 0, populate | кусок земли (засыпка и расчистка под формой), затем куски города, затем куски поля (только окно) | G + P |
| жизнь | охрана зоны (R), колонка (R), вкладка Диспетчера (T), «Достройка» окон поля (G), светомаяк (P), API (ядро) | пакеты |
| `serverStopped` | `EmbassySC.clearAll` → кэши поля, квоты колонки, инфо API | ядро |

## 2. Общие решения

### 2.1 Конфиг (раздел `embassy`, холдер `SubstrateConfigSC`)
| Ключ | По умолчанию | Что |
|---|---|---|
| `airfield` | `AUTO` | ЛП-1: AUTO — поле получает новое посольство, только если аддон загружен **в момент решения о старте** (без аддона посольства генерируются ровно как раньше; один сид с аддоном и без него даёт разные посольства — принято пользователем); ALWAYS — всегда; NEVER — никогда. Неизвестное слово — AUTO. Старые посольства не меняются никогда |

Не правило (клиенту не уходит): только серверная генерация его читает. Раздел уже помечен `setCategoryRequiresWorldRestart`.

### 2.2 Реестровые имена и id (фиксированы, только дописываются)
| Что | Имя / id | Владелец |
|---|---|---|
| Блок | `embassyAirfieldSC` (`BlockAirfieldSC`, без TE; `Reference.ASSETS + ".embassyAirfield"`) | ядро (вид — P) |
| Меты `embassyAirfieldSC` | 0 `runway`, 1 `shoulder`, 2 `apron`, 3 `markWhite`, 4 `markYellow`, 5 `lightEdge`, 6 `lightThreshold`, 7 `lightEnd`, 8 `lightTaxi`; `LIGHT` {0,0,0,0,0,10,10,8,6}; полоса для аддона — 0, 3, 5, 6, 7 | ядро |
| Меты `showcase2SC` | 10 `towerConsole` (пульт КДП — декор до А5), 11 `serviceColumn` («Колонка обслуживания»), 12 `airfieldBeacon` («Светомаяк»), 13 `aaPost` («Зенитный пост Наладчиков»); все с `TileEntityShowcaseSC` (лицо) | ядро (вид — P) |
| Куски | `SCOwEmbRunway`, `SCOwEmbApron`, `SCOwEmbTerminal`, `SCOwEmbHangar`, `SCOwEmbHelipad`, `SCOwEmbFunnel`, `SCOwEmbAirDefence` (`EmbassyPiecesSC.NAMES` 12…18) | ядро (тела — P) |
| Лоты | `EmbassyLayoutSC.LOT_RUNWAY` 14, `LOT_APRON` 15, `LOT_TERMINAL` 16, `LOT_HANGAR` 17, `LOT_HELIPAD` 18, `LOT_FUNNEL` 19, `LOT_AIR_DEFENCE` 20 — только в `Field.lots()`, **не** в `Plan.lots` (правила кварталов их не видят) | ядро |
| Пакет API | `com.sc.embassy.api` (`EmbassyAirApiSC`, `AirfieldInfoSC`, `IEmbassyAirHookSC`) | ядро |
| Сущности, биомы, измерения, GUI, рецепты | новых нет (GUI свободны 10–19, 23+) | — |

### 2.3 Перечисления и коды
`EmbassyAirfieldSC`: NONE 0, HELI 1, SHORT 2, FULL 3 (`KIND_KEYS` none/heli/short/full); ILS_LOW 1, ILS_HIGH 2; SPOT_LINER 0, SPOT_SMALL 1, SPOT_HELI 2 (`SPOT_KEYS` ms1…ms4, heli). `EmbassyAirRulesSC`: услуги S_PARK 0, S_EMERGENCY 1, S_CHARGE 2, S_HYDROGEN 3, S_ARMOUR_GAS 4, S_REPAIR 5, S_ELECTROLYTE 6, S_TOW 7, S_CHART 8 (`SERVICE_KEYS` park, emergency, charge, hydrogen, armourGas, repair, electrolyte, tow, chart); оплата PAY_OK 0, ENEMY 1, LEVEL 2, LIMIT 3, CHIPS 4, NO_FIELD 5, UNKNOWN 6 (`PAY_KEYS`). `EmbassyAirDefenceSC`: PASS_GRANTED 0, RUNNING 1, USED 2, NO_FIELD 3, ADMITTED 4; строки AD_WARN 0, AD_FIRE 1, AD_PASS 2, AD_PASS_USED 3, AD_PASS_OVER 4, AD_LEFT 5 (`AD_KEYS` warn, fire, pass, passUsed, passOver, left). В API — те же числа. `ReputationSC.SRC_AIR` 14. `SettleNetSC` AIR_STATE 5, AIR_RESULT 6, AIR_DO 8, AIR_REFRESH 9.

### 2.4 Хранилище
| Что | Где | Владелец |
|---|---|---|
| Решение о поле | `Settlement.extra` **«X»** {`k` kind, `s` side ±1, `v` version (1), `i` ILS-биты}. Буква «A» из предложения **занята** (с bf114b4: `RaidAlarmSC` ПД-3 на `Settlement.extra` и `PlayerRec.extra`); заняты также C, E (мир), F, G, K, L, O, P (арена), Q, R, S, T, V, W, Y; «X» свободна во всех трёх записях. Записи без «X» — без поля, навсегда | ядро (`fill` / `writeDecision`) |
| Решение в старте | NBT куска земли «Air» int[4] (только у стартов, решённых с полем; старые старты — диск) — для восстановления потерянной записи | ядро |
| Куски поля | база кусков посольства + «EmbFrame» + «EmbAir» int[4] | ядро |
| Пропуск 7700 | `LocalRec.extra` «X» {`d` — игровой день последнего пропуска, `u` — мировое время конца удержания} (у игрока в этом посольстве; по UUID) | ядро (`EmbassyAirDefenceSC`) |
| Карты подхода | `LocalRec.extra` «X» {`c` — сколько карт оплачено в этом посольстве за всё время; первая бесплатная — по нему, а не по суточному счёту} (`EmbassyAirServiceSC.CHARTS`) | R |
| «Лётная книжка» | `PlayerRec.extra` «X» {`f` — int[] id посольств первой посадки} | R (пишет), ядро (`landed`) |
| Суточные количества услуг | `LocalRec.counts` `air.<serviceKey>` — в мере услуги (EU, мБ, % …; газы брони — мБ), откатываются на рассвете (`LocalRec.rollDay`) | R |
| Квота колонки | память R по UUID (`COLUMN_CONFIRM_TICKS` 100), чистится `clearAll` | R |
| Переходное | кэш полей записей (`EmbassyAirfieldSC`, WeakHashMap), ревизия, инфо API — всё чистится в `serverStopped` | ядро |

Всё у игрока — по UUID в глобальном реестре (друг по LAN — своё; переживает смерть).

### 2.5 «Доверие» и КЧ
Услуга — не сделка: «Доверия» не даёт, КЧ просто снимаются (`TradeServerSC.takeChips`). «Лётная книжка» — 2 КЧ в общих 64 в сутки (`TradeRulesSC.chipsAllowed`, `PlayerRec.chips`) и +5 «Доверия» `ReputationSC.give(rec, 5, SRC_AIR, day, ReputationSC.ceiling(s))` под потолок 599. Других начислений и штрафов за полёты нет. ПВО посольства «Доверие» не меняет.

### 2.6 Сеть
| Канал / дискр. | Сообщение | Направление | Виды |
|---|---|---|---|
| `SiliconAgeDim` 1, 2 | `SettleNetSC` | ↔ | + AIR_STATE 5, AIR_RESULT 6 (→ клиент, T); AIR_DO 8, AIR_REFRESH 9 (→ сервер, T) |
| 5 | `EmbassyNetSC.ToClient` | → клиент | `BEAMS` (P дописывает в его NBT список `lights` [{x, y, z}] — светомаяки полей в `SEND_REACH`; новых видов нет) |

Обработчик клиента может прийти не в клиентском потоке — только volatile-поля (как `TradeClientSC`). FakePlayer пакетов не получает (`SettleNetSC.send` проверяет).

### 2.7 GUI
Новых окон нет: вкладка «Лётное поле» — внутри экрана обмена (`GuiTradeSC`, `TRADE_GUI_ID` 21) у Диспетчера посольства **с полем**. Колонка без аддона — строки в чат с подтверждением вторым щелчком; с аддоном окно открывает аддон (своим GUI-хендлером).

## 3. Геометрия (`EmbassyAirfieldSC`, ядро, написано целиком; правила ЛП-1…ЛП-20)

Кадр посольства: `a` — вдоль главной улицы к главным воротам, `p` — поперёк; у поля `q = p × side` (к полю). `r` — радиус купола (88–96), `lv = r + LEVEL_OUT` (r + 31), квадрат площади ±(r + 39).

### 3.1 Правила
- **ЛП-1 Решение.** Только для новой записи, в `getStructureStart`: `buildsNow()` (ALWAYS, или AUTO и `Loader.isModLoaded("SiliconAgeVehicles")`), затем `EmbassyAirSiteSC.choose` (G). `null` → «X» не пишется, посольство как раньше. Решение записи не меняется никогда (`renew` сохраняет «X»).
- **ЛП-2 Форма ровной земли** — диск `lv` ∪ (FULL) прямоугольник ВПП a = ±lv, q = r + 7…lv ∪ прямоугольник перрона a = r − 32…lv, q = 14…r + 15; сопряжение BLEND 8; `Field.levelPast` — наименьшее «за краем»; всё внутри квадрата ±(r + 39).
- **ЛП-3 Геометрия** (FULL): ВПП a = −100…+100, ось q = r + 23, покрытие 9 (±4), обочины по 2 (полоса — `strip`); пороги смещены на 20 (a = ∓80; посадочная дистанция 181 — TODO(balance) по `LinerSimSVA`); разворотные площадки 21 × 12 у обоих торцов (q = r + 7…r + 18); рулёжка «А» a = 94…r + 31, q = r + 10…r + 16 (7) — от площадки у +a торца вдоль края перрона, параллельной нет («бэктрек»); перрон a = r + 3…r + 31, q = 24…r + 9 (край в 14 от оси — крыло лайнера не над полосой); МС-1 22 × 22 (a r + 10…r + 31, q r − 12…r + 9), МС-2…4 13 × 13 (a r + 19…r + 31, q r − 27…r − 15 / r − 42…r − 30 / r − 57…r − 45); колонки (a r + 31) у каждого МС; Аэровокзал 17 × 9 × 7 (a r + 6…r + 22, q 14…22 — вплотную к платформе Вокзала, вне зоны 13); Вышка КДП 7 × 7 × 20 (a r + 24…r + 30, q 14…20), пульт на +17, светомаяк на +21; **Ангар 27 × 21 × 5** (a r − 24…r + 2, q r − 23…r − 3, ворота на +a к перрону; высота 5, не 11 из предложения: 11 пробивает переходную поверхность 1:4 — до полосы 20); памятник 6 × 6 × 6 (a r + 4…r + 9, q 24…29); мачта колдуна (−70, r + 3), высота 3; **вертодром „Н“ 11 × 11** по другую сторону Вокзала (a r + 12…r + 22, q −24…−14) — один на посольство, у всех вариантов, его колонка (r + 17, −25); створы FULL — расчистка «роста» над плоскостью 1:20 от порога, ±15 от оси, до a = ±150 (только эта часть выходит за квадрат; рамка старта удлиняется по a, RANGE 10 её покрывает); аудит 2026-11-04 (ЛП-2 А, решения версии 2): у торца с ILS — до 300 за порогом (a = ±380), RANGE 26 — см. §3.2.
  SHORT: ВПП 90 × 7 (a −45…+45, ось q = r + 18, без обочин и смещения), „Н“, колдун (−30, r + 3), посты ПВО. HELI: „Н“ и посты ПВО.
  **Посты ПВО** («Зенитный пост Наладчиков», 5 штук, площадка 5 × 5, высота 2, пушка `AA_POST` на +1): (±r/2, −(r + 14)), (−(r + 14), −6), (±r/4, r + 4) — вокруг купола и поля, вне купола + 3, на ровной земле, у всех вариантов.
  **Огни** (`Field.lights`, ставит P ровно там, утоплены в покрытие на полу): красные торцевые — ряды концов; зелёные пороговые — ряды порогов (`thresholdRow`: FULL — смещённый порог, SHORT — на 1 внутрь от торца); кромки — обе кромочные строки покрытия каждые 6 от −a торца, кроме рядов торцов и порогов; синие рулёжки — обе кромки «А» каждые 6.
  **Виртуальный ILS аддона** (блоков нет): курсовой — на оси в 15 за дальним торцом; глиссадный — в 30 за порогом, в 15 от оси со стороны купола (TODO(balance)).
- **`problem(plan)`** (самотест D): всё (кроме створов и коридоров) вне купола + 3 и внутри квадрата; углы — на ровной форме; ничего на платформе Вокзала и в его зоне 13; ничего на лотах города (кроме лота стены — это квадрат купола); перрон и все МС не ближе 14 к оси ВПП; постройки (Аэровокзал 7, Вышка со светомаяком 21, Ангар 5, памятник 6, посты ПВО 2, колдун 3, колонки 1) под переходной поверхностью 1:4 от полосы и — в ±12 от оси за торцом — под плоскостью захода 1:20 от порога. **Сейчас — null на всех 648 планах** (проверка координатора).
- **ЛП-4 Покрытие** — `BlockAirfieldSC`: `isOpaqueCube` = false (снег на него не ложится: `BlockSnow.canPlaceBlockAt` спрашивает ровно это), в остальном — полный твёрдый куб (Material.rock, `renderAsNormalBlock`, светонепроницаемость 255). Полоса для аддона — `EmbassyAirApiSC.isRunwaySurface` (меты 0, 3, 5, 6, 7); обочина, перрон, жёлтая разметка и синие огни — не полоса (заливка `RunwayMath` не сливает перрон с ВПП).
- **ЛП-5 Куски** строят только окно populate, флаг 2, свет вручную, идемпотентны (Достройка). «Рост» над полом всей формы поля при первой постройке убирает кусок земли (как под куполом: до `VOKZAL_CLEAR` 40 — всё, выше — только «рост»); створы — кусок `Funnel`.
- **ЛП-6 Охрана** — зона `Field.zone` (FULL: оба прямоугольника ровной формы, „Н“ + 2, посты ПВО, колдун; SHORT: ВПП + 2, „Н“, посты, колдун; HELI: „Н“, посты) от `floorY − 6` вверх: блоки не ломаются и не ставятся, взрывы гасятся, враждебные мобы не появляются, концы мостов не открываются, поля игроков не заходят, жители неприкосновенны (пояс); калитки и колонка работают; технику (сущность) ставить можно. **Нарушение без штрафа «Доверия»** (как зона причала; решение координатора).
- **ЛП-7 «Достройка» поля** — окна поля (G): на поле убирается только «рост» и снег; куски поля ставят своё заново (свой блок на месте — без изменений); не ближе `PLAYER_CLEAR` 32 к игроку, как у купола.
- **ЛП-8 Нейтральное небо** — диск `lv`, зона поля и коридоры захода (±20 от оси, до 300 за порогом), любая высота: ПВО **игроков** там не стреляет (`neutralAt`; правка аддона), кроме неба над базой игрока (ревью 2026-10-27). Своё ПВО посольства этим не связано.
- **ЛП-9 Зона ПВО посольства** — круг AD_RADIUS 256 от ядра, любая высота (TODO(balance)). Аудит 2026-11-04, ЛП-1 А: огонь — только над квадратом посольства (`Field.fireHalf` = r + 39 + `AD_FIRE_MARGIN` 10, около 145 от ядра) и створами захода (`Field.approaches`) внутри круга (`Field.inFireZone`, `adZoneAt`); в остальном круге — только предупреждение «вы приближаетесь к зоне ПВО» (`adWarnZoneAt`, строка `AD_NEAR` = 6, ключ `sc.airfield.ad.near`; показывает аддон).
- **ЛП-10…ЛП-16** — услуги, колонка, оплата, допуск, прикрытие, 7700, «Лётная книжка» (§6.2, §7). **ЛП-17** — вкладка Диспетчера (§8). **ЛП-18** — посты 2 Постовых и 1 Газовщика на поле (`Field.posts`, G). **ЛП-19** — старые посольства не трогаются (нет «X» — нет поля; никакого дооснащения). **ЛП-20** — светомаяк (P).

### 3.2 Выбор варианта (`EmbassyAirSiteSC.choose`, G)
По слоям биомов `WorldChunkManager` (как `EmbassySiteSC.biomeProblem`, ни один чанк не грузится): (1) FULL на стороне по сиду — прямоугольники ровной формы + 16 и створы до 300 за порогом (±20): нет OCEAN, BEACH, SWAMP, MOUNTAIN; rootHeight < `EDGE_ROOT` 0,3; HILLS ≤ `EDGE_PCT` 10%; heightVariation < 0,6; FOREST в створе ≤ `APPROACH_FOREST_PCT` 30% (TODO(balance)). Створ одного торца плохой или чанки его кусков створа уже на диске (`chunkOnDisk`) — бит ILS этого торца снят (только визуальный заход); оба плохие — FULL не на этой стороне. (2) FULL на другой стороне. (3) SHORT (внутри диска — всегда помещается). (4) HELI — запас. Доля FULL — замер `/scembassy list` на 12 сидах §5.1 техплана Ф6, цель ≥ 60% (TODO(balance)). Ревью 2026-10-27: створ может идти над водой и держать до 20% холмов (`APPROACH_WATER`, `APPROACH_HILLS_PCT`), ровная земля — по-прежнему 10%; замер самотеста (`SelfTestAirfieldSC.fullShare`, 12 сидов × 12 мест, строка INFO): первые правила 31%, текущие 47% (ILS у обоих торцов 13%), с 20% холмов и на ровной земле — 52%; 60% не набирается — главные причины: лес в створе > 30%, холмы и вода на ровной земле — решение за пользователем.

**Аудит 2026-11-04 (решения пользователя ЛП-2 А, ЛП-3 Б, ЛП-6 А, ЛП-7 А; версия решения `VERSION` 2 = `LONG_FUNNELS`, старые записи версии 1 строятся и досбираются как прежде):**
- **ЛП-3 Б:** лес в створе больше **не запрещает FULL** (`APPROACH_FOREST_PCT` = −1): у торца с ILS его вырубает длинный створ (ЛП-2). Правила ровной земли не менялись (холмы ≤ 10%, без воды); створ — как раньше (вода можно, холмы ≤ 20%, без гор, плато, «разбитой» земли). Цель 60% остаётся целью: долю перемеряет самотест (строка INFO `fullShare`) в прогоне координатора — число вписать сюда после прогона.
- **ЛП-2 А:** у торца с ILS створ расчищается (только «рост»: деревья, листва, лианы, грибы; рельеф не трогаем) над плоскостью 1:20 в коридоре ±15 до `FUNNEL_ILS_OUT` 300 за порогом (a = ±380, `FUNNEL_REACH`); у торца без ILS — по-прежнему до ±150. ILS снимается, если в коридоре створа (300 за порогом, ±20) есть «холмистая» выборка (`F_HILLY`) выше плоскости — верх оценивается по слоям биомов: 64 + 24 × (rootHeight + heightVariation) (`EmbassyAirSiteSC.pierces`, `ilsProblem`, TODO(balance)), или если чанки длинного створа уже на диске. `MapGenEmbassySC.RANGE` 10 → 26 (старт делается раньше первого чанка длинного створа; кандидаты региона отсеиваются дёшево — `mayHold`), `covers()` смотрит куски старта, а не всю его рамку (полосы рядом со створами ±380 — не посольские; риск 4 снят). Окон створов может быть больше 64 — для них отметки досборки в байтах `G.fb` (`K_SETTLED_MORE`), не в битах `G.f`.
- **ЛП-6 А:** створ строится в позже загруженных чанках, досбирается (`settle`) и чистит свисающую листву только `FUNNEL_DAYS` 7 игровых дней после создания записи (`madeAt`); позже — пропуск (у `settle` сразу `G.f` = −1).
- **ЛП-7 А:** при каждой постройке / досборке створа листва и лианы над плоскостью захода убираются и в `OVERHANG` 6 блоков вокруг — в соседних уже загруженных чанках, только в коридоре створа, не над базой игрока.

### 3.3 Имена
ИКАО: `icao(element, number)` = «X» + `ICAO_ELEMENT[element]` (две буквы по символу; однобуквенные: W → WO, V → VA, Y → YT) + буква номера (1–26 → A–Z, дальше по кругу); уникальность и закрытие префикса X для новых вышек игроков — аддон (`TowerMath.unique`, `defaultIcao`). Аудит 2026-11-04, ЛП-5 А: вкладка Диспетчера и `/scembassy airfield` показывают код, сделанный уникальным тем же способом, что в аддоне (`EmbassyAirfieldSC.icaoShown`: сначала FULL по номеру записи, затем остальные; занятый код — следующая свободная пара последних букв); вычисляется, в сохранение не пишется; API (`AirfieldInfoSC.icao`) по-прежнему отдаёт предложенный код. Номер ВПП — по истинному курсу (`heading`, `number`: «36/18» или «09/27»), без магнитного склонения.

## 4. Генерация — пакет G (ЛП-1, ЛП-2, ЛП-5, ЛП-7, ЛП-18)

### 4.1 Решение (`EmbassyAirSiteSC.choose`)
§3.2. Чистые проверки вынести в чистые функции (самотест D зовёт их на синтетических данных). Чтение — только слои биомов; `chunkOnDisk` — как в `explored()` (не создаёт файлов регионов).

### 4.2 Выравнивание (`EmbassyTerrainSC`, `EmbassyWorldGenSC`, `ComponentEmbGroundSC`)
- `levelChunk` — перегрузка с `EmbassyAirfieldSC.Field` (null — диск, как сейчас): расстояние «за краем» — `field.levelPast(x, z)`; `ComponentEmbGroundSC.levelChunk` передаёт `field()`. Работает только в массивах своего чанка — каскада нет.
- Кусок земли: засыпка пещер под всей формой (`field.levelPast ≤ 0`), сопряжение — как сейчас (`bandTarget`); **первая постройка** над полом всей формы поля (не только купол и Вокзал) — всё до `VOKZAL_CLEAR` 40, выше — только «рост» (`foreign`). Деревья IC2 и TC (`IWorldGenerator`, мимо Decorate) так не вырастут на ВПП.
- **Достройка** (`EmbassyRepairSC.repairing()`): в зоне поля (`field.inZone`) — засыпка как у защищённой земли, над полом только «рост» и снег/лёд (тот же `foreign`).
- Старт без «Air» (старый) — ровно прежнее поведение.

### 4.3 Окна Достройки (`EmbassyRepairSC.windows`)
К окнам купола и Вокзала дописать окна (чанки populate), которые пересекают `Field.zone` (без створов: за квадратом земля не защищена). Порядок обхода, `PLAYER_CLEAR`, пауза между проходами — прежние.

### 4.4 Запреты (`EmbassyWorldGenSC`)
Аудит 2026-11-04 (ЛП-2): `covers()` смотрит рамку старта и затем куски (`piecesMeet`) — запрет деревьев лежит на кусках (створы до ±380 при ILS), а не на всей рамке. Было: «Ничего нового в коде не нужно: `covers()` смотрит рамку старта, а куски створа удлиняют её по a до ±150 — TREE и BIG_SHROOM запрещены и там (побочный эффект — нет ванильных деревьев на прямоугольнике ±150 × ±(r + 39); риск 4). G проверяет это мировым логом и, если мешает, предлагает координатору `covers()` по форме из записи.»

### 4.5 Посты (`EmbassyQuartersSC.lay`, ЛП-18)
У FULL и SHORT (`p.field != null && p.field.posts != null`): Постовые №5 и №6 (`sentryPosts` индексы 4 и 5 — «внутри стены по бокам») встают на `field.posts[0]` (у перрона / у „Н“) и `field.posts[1]` (у дальнего торца); **второй** Газовщик (`GAS_TECH`) — на `field.posts[2]` (у колонок), первый остаётся в мастерской. Сон — как сейчас (Постовые №5–6 спят у поста: `NpcLinkSC` даёт дом вокруг поста дальше купола − 2; Газовщик — в жилой ячейке). `problem()` кварталов принимает эти посты. Без поля — ровно прежний план (самотест: план с `air = null` совпадает с планом до правки).
Интеграция: `EntityWardenSC.role` — Постовые №5–6 записи с постами поля — `ROLE_POST` днём и ночью (иначе они патрулировали бы и к посту не шли). Ревью 2026-10-27: дальний пост (FULL и SHORT) — у колдуна, `(windsock.a, windsock.q + 1)`, в его зоне, не ближе `WING_CLEAR` 14 от оси ВПП (FULL 19, SHORT 14: крыло не проходит над постовым; `problem()` это проверяет); поводок `NpcLinkSC.leash(s, slot)` поднят только самим Постовым поля (`fieldSentry`) — до своего поста + `FIELD_LEASH` 8, у остальной стражи — прежний пояс; перепись (`RosterSC.reach`) ждёт загрузки круга, где может быть существо слота: пояс, поводок Постового поля, пост Наладчика за куполом + `OUTPOST_HOME` 8 (второй Газовщик у колонок — 139–150 от ядра), — иначе ложная «смерть» и лишний клон.

### 4.6 Команда (`CommandEmbassySC`)
`/scembassy list` — у каждой записи вариант поля («airfield full side +1 ILS 36/18», «no airfield» без «X»; для неоткрытых мест — что решило бы `choose` сейчас, с пометкой); `/scembassy airfield [id]` — **только отчёт** (вариант, ИКАО, номера, ILS, зона, посты ПВО, ревизия). Дооснащения нет.

## 5. Публичное API (`com.sc.embassy.api`, ядро, написано целиком; API_VERSION = 1)

### 5.1 `AirfieldInfoSC` (неизменяемое, мировые координаты)
Поля: `id`, `dim`, `kind`, `version`, `side`, `nameElement`, `nameNumber`, `icao`, `coreX`, `coreZ`, `floorY`, `radius`, `gateDir`, `alongX`, `width`, `length`, `headingLow/High`, `numberLow/High`, `ilsLow/High`, `adRadius`. Методы (массивы — копии): `name()`, `runwayBox`, `stripBox`, `end(low)`, `threshold(low)`, `thresholdKey(low)` (ключ торца аддона — середина ряда пороговых огней), `landingDistance(low)`, `localizer(low)`, `glideSlope(low)`, `padBox(low)`, `taxiBox`, `apronBox`, `lights(meta)`, `funnelBoxes`, `approachBoxes`, `towerDesk` (ключ вышки), `beacon`, `terminalBox`, `towerBox`, `hangarBox`, `monumentBox`, `spots/spotBox/spotKind/spotKey`, `helipadBox`, `columns`, `windsock`, `zoneBox`, `adPosts`, `inAdZone`, `inZone`, `neutral`, `onRunway`, `inDome`. «low» — торец со стороны −a; посадка над ним — курсом `headingLow`.

### 5.2 `IEmbassyAirHookSC` (реализует аддон)
`apiVersion()`; `openService(p, field, column)` (окно услуг аддона; false — ядро обслуживает само); `serviceRows(p, field)` (строки вкладки, IChatComponent, ≤ 8); `atisLine(w, field)`; `tabAction(p, field, action)`; `airfieldsChanged(w, revision)` (зовётся из генерации — только пометить). Ядро ловит исключения крюка.

### 5.3 `EmbassyAirApiSC` (статические, сервер; на клиенте — пусто / null / false / −1)
`registerHook`, `hook`; `airfields(w)`, `airfield(w, id)`, `airfieldAt(w, x, z)`, `domeAt(w, x, z)`, `neutralAt(w, x, y, z)`, `isRunwaySurface(block, meta)`, `isEmbassyBlock(block)` (белый список `obstacle()`), `revision(w)`; `levelOf(p, id)`, `knows(p, id)` (был — `EmbassyTicketSC.SEEN` — или ближе 1024), `hasTalon(p, id)` (рейс, купленный там сегодня, или действующая обратная половина рейса оттуда); `quote(p, id, serviceKey, amount, free)` (КЧ или −PAY_*), `pay(p, id, serviceKey, amount, free)` (PAY_*), `firstLanding(p, id)`; ПВО: `adRadius()`, `warnSeconds()`, `adZoneAt(w, x, z)`, `admitted(p, id)`, `escortOf(p, id)`, `requestPass(p, id)`, `passUntil(p, id)`, `holdFire(p, id)`, `adLine(w, id, line, callsign)`, `tell(p, id, line, callsign)`. Отличие от §9 предложения: `pay` берёт количество **в мере услуги** (единицы считает ядро) и флаг `free` (бесплатный случай аддона: первые сутки стоянки, первая карта; стоянку при `hasTalon` и первый перегон за сутки ядро проверяет само).

## 6. Охрана и ПВО

### 6.1 Охрана зоны поля (R, ЛП-6)
`SettlementProtectSC`: `onBreak` / `onPlace` (и MultiPlace) / `onBucket` / огонь в `onInteract` — в `SettlementSC.airfieldProtecting` отменяются **молча** (как зона причала, без `violation`); `onInteract` по блоку с TE или механизму в зоне — отмена молча, **кроме** неломаемых блоков поселений (колонка `showcase2SC` проходит — щелчок уходит в `onBlockActivated`); `onDetonate` — блоки в зоне вычёркиваются (как цилиндр); `onCheckSpawn` — уже работает (`embassyGroundAt` знает зону). Мосты (`refusesBridgeEnd`), поля игроков (`overlapping`), пояс жителей (`inBelt`) — уже в ядре.

### 6.2 ПВО посольства — сторона ядра (написана, `EmbassyAirDefenceSC`; ЛП-9, ЛП-13…ЛП-15)
- Зона — круг 256 от ядра, любая высота (`Field.inAdZone`, `adWarnZoneAt` — только предупреждение `AD_NEAR`); огонь (аудит 2026-11-04, ЛП-1 А) — над квадратом посольства (около 145) и створами внутри круга (`Field.inFireZone`, `adZoneAt`). Посты — 5 пушек `AA_POST` (`Field.aaBlock`), ставит P; без аддона молчат.
- **Допуск** (ЛП-13): `ReputationSC.localLevel(rep, enemy) ≥ ACQUAINTANCE` и не «Враг» этого посольства; без записи игрока — «Чужак» (нет допуска).
- **Прикрытие** (ЛП-14): `escortOf` — допущенный товарищ по звену ПД-2 (`SquadSC.mates`) в сети, жив, в том же мире, ближе ESCORT_RANGE 64 (TODO(balance)). Пассажиров и экипаж аддон проверяет сам: допущен любой на борту — борт не цель.
- **7700** (ЛП-15): `requestPass` — один пропуск PASS_SECONDS 60 на игрока на посольство в игровой день (журнал `LocalRec.extra` «X» {d, u}); повторно — PASS_RUNNING / PASS_USED; допущенному — PASS_ADMITTED (не нужен).
- **Предупреждение** (ревью 2026-10-27): `requestWarn` / `warnUntil` — одно на игрока на посольство в игровые сутки (журнал `LocalRec.extra` «X» {w — день, e — конец 10 с}); повторный вход в те же сутки — конец уже в прошлом, огонь сразу. Правило для аддона (Javadoc API): секунды предупреждения — на разворот, борт, который за это время сближается с полем или снижается к нему, — цель сразу; конец разворота борта — самый ранний из его седоков.
- **База игрока** (ревью 2026-10-27): над зоной включённого Генератора поля игрока (`EmbassyAirfieldSC.playerBaseAt`) `adZoneAt` = −1, `holdFire` = true, `neutralAt` = false — посольство, появившееся рядом с базой, не стреляет над ней и не глушит её ПВО в створе. `AirfieldInfoSC.inAdZone` — голый круг.
- Строки (`sc.airfield.ad.<key>`, аргументы {0 — посольство, 1 — позывной, 2 — секунды, 3 — уровень допуска}): красные `warn`, `fire`; жёлтые `pass`, `passUsed`, `passOver`, `left`. Аддон пишет тот же текст в своё радио.
- Огонь, цели «в воздухе», предупреждение и 10 с, снаряды, радио — **аддон** (`docs/plan-embassy-airfield.md` §3.3).

## 7. Услуги — пакет R (ЛП-10…ЛП-12, ЛП-16)

### 7.1 Числа (`EmbassyAirRulesSC`, ядро; TODO(balance) все)
Цена = база × единицы × `ReputationSC.PRICE[уровень]` × разброс ±10% (`TradeRulesSC.playerSide`, вверх).
| Услуга (ключ) | С уровня (до) | База, КЧ / единица | За услугу | В сутки |
|---|---|---|---|---|
| park | «Чужак» | 1 / сутки (первые сутки 0 — аддон; при `hasTalon` 0) | — | — (2 МС на игрока — аддон) |
| emergency | «Нарушитель» (до «Чужака») | 1 / раз (до 25% бака и буфера — аддон) | — | 1 |
| charge | «Знакомый» | 1 / 2 000 000 EU | 16 000 000 | 32 000 000 |
| hydrogen | «Знакомый» | 1 / 8 000 мБ | 48 000 | 64 000 |
| armourGas (ядро) | «Знакомый» | 1 / 4 000 мБ | — | 16 000 |
| repair | «Знакомый» | 1 / 10% износа | 60% | — |
| electrolyte | «Знакомый» | 2 / шт | — | — |
| tow | все, кроме «Врага» | 1 (первый за сутки 0) | — | — |
| chart | «Чужак» | 1 (первая 0) | — | — |
Отказы по порядку: нет поля / неизвестная услуга → «Враг» → уровень → потолки → КЧ. Услуги не продают предметов (ПО-7). «Чужак» и «Нарушитель» долетают, по сути, только с пропуском 7700 — таблица остаётся (решение пользователя 7).

### 7.2 Колонка обслуживания (`EmbassyAirServiceSC.useColumn`, R; ЛП-11)
Колонка — блок `SERVICE_COLUMN` ровно в `Field.columnBlock(i)`. Посольство колонки — `SettlementSC.airfieldAt` её столбца. Не крадучись и есть крюк — `openService` (true — всё). Иначе (или крадучись) — газы брони ядра: первый щелчок — строка-квота (чего не хватает надетой броне по `COLUMN_GASES` водород и кислород: `ArmorGasSC.suitCapacity − suitAmount`, в пределах 16 000 мБ сегодня; цена) или отказ; второй щелчок за `COLUMN_CONFIRM_TICKS` 100 — оплата (`TradeServerSC.takeChips`), заправка (`ArmorGasSC.suitFill`), счёт мБ в `LocalRec.counts` `air.armourGas`, реестр грязный. Квота — по UUID (двое одновременно — каждому своя). Работает и без аддона (только там, где поле построено: при ALWAYS).

### 7.3 Оплата для аддона (`quote` / `pay`, ЛП-12) и «Лётная книжка» (`firstLanding`, ЛП-16)
`quote`: `refusal(service, level, enemy, amount, usedToday)` → цена (0 при `free`, при park + `hasTalon`, при tow и нуле перегонов сегодня, при chart и `FREE_CHARTS`) → нехватка КЧ = PAY_CHIPS. `pay`: то же, затем `takeChips`, `addCount(air.<key>, amount)`, `markDirty`. `firstLanding`: `landed(rec, id)` — false; иначе id в `PlayerRec.extra` «X».`f`, КЧ `chipsAllowed(rec.chips, 2)` → `giveChips`, `rec.chips += n`, «Доверие» +5 под потолок, строка `sc.airfield.logbook.first`, true.

## 8. Вкладка Диспетчера — пакет T (ЛП-17)
Вкладка «Лётное поле» в `GuiTradeSC` — только у Диспетчера посольства **с полем** (без поля вкладки и сообщения нет). Сервер (`EmbassyAirServerSC.sendState`) шлёт AIR_STATE: `tuner`, `id`, `k`, `icao`, `rl`/`rh` (номера ВПП), `lore` (0 — аддона нет: «Поле законсервировано, бортов с Пробоя не было» и «Связи с бортами нет со времён Пробоя»; 1 — есть: «Поле открыто для машин зелёного мира»), `adm` (допуск ПВО), `lvl`, `pass` (0 — сегодня не было, 1 — идёт, 2 — использован), `book` (садился ли здесь), `gas` {цена всего остатка на сегодня `p` за `v` мБ (аудит 2026-11-04, ЛП-4 А: колонка округляет один раз по всей заправке), остаток мБ на сегодня; для „Врага“ — своя строка `sc.airfield.tab.gas.enemy`}, `af` — полуширина квадрата огня ПВО (ЛП-1), `rows` (строки крюка — JSON `IChatComponent.Serializer`, ≤ 8), `atis` (JSON или нет), `act` (кнопка «Обслуживание» крюка). AIR_DO {act} → `hook.tabAction`; AIR_REFRESH → состояние заново; AIR_RESULT {tuner, ok, key, args}. Сервер проверяет открытое окно у этого Диспетчера (как `FlightServerSC.openDispatcher`). Вкладки у Диспетчера: «Рейсы», «Сделки» (если есть), «Лётное поле» — три вкладки по ширине правой колонки (`RW` 78). Клиент: поля в `TradeClientSC` (volatile + счётчики, как FLIGHT_*), разбор JSON — на клиенте (`IChatComponent.Serializer.func_150699_a`).

## 9. Тексты — ключи по пакетам (lang — только D; ru и en, CRLF, „…“ / “…”, никогда « », без символов 128–255 таблицы шрифта)
- **Ядро (уже в коде):** `tile.siliconage.embassyAirfield.<runway|shoulder|apron|markWhite|markYellow|lightEdge|lightThreshold|lightEnd|lightTaxi>.name`; `tile.siliconage.showcase2.<towerConsole|serviceColumn|airfieldBeacon|aaPost>.name`; `sc.tooltip.embassyAirfield.<9 имён>`, `sc.tooltip.embassyAirfield.details`, `sc.tooltip.airfield.howto` (Ctrl: «только на лётном поле посольства»); `sc.tooltip.airfield.<towerConsole|serviceColumn|airfieldBeacon|aaPost>` и `.details`; `sc.airfield.ad.<warn|fire|pass|passUsed|passOver|left>` ({0} посольство, {1} позывной, {2} секунды, {3} уровень; warn — с подсказкой «допуск — „Знакомый“: прилетите сначала по земле — Кольцом или пешком»); `sc.airfield.unknown`; `sc.airfield.pay.<ok|enemy|level|limit|chips|noField|unknown>`; `sc.airfield.service.<9 ключей>`; `sc.airfield.spot.<ms1..ms4|heli>`; `sc.airfield.kind.<none|heli|short|full>`.
- **R:** `sc.airfield.column.*` (квота, оплачено, полна, уровень, «Враг», лимит дня, нет КЧ, нет брони, «щёлкните ещё раз»), `sc.airfield.logbook.first` — присылает D.
- **T:** `sc.airfield.tab`, `sc.airfield.tab.*` (лор ×3, ИКАО, ВПП, вертодром, допуск да/нет, пропуск ×3, книжка да/нет, газы, кнопка «Обслуживание», строки результата).
- **G:** строки команды — по образцу `CommandEmbassySC` (если там переводимые ключи).
- **D сам:** статья справочника (раздел «Лётное поле» в статье посольств `BookEmbassySC`), README-черновик.

## 10. Пакеты (файлы не пересекаются)

Общие правила владения — как в Ф1–Ф6: каждый существующий файл правит ровно один пакет или координатор; новые файлы создаёт только их пакет; нужна правка чужого файла — в отчёт координатору (файл, место, текст). Заготовки ядра (§0) принадлежат пакетам, указанным в их Javadoc.

### G — генерация: решение, форма, выравнивание, земля, Достройка, посты, команда
- **Существующие:** `embassy/EmbassyAirSiteSC.java` (заготовка: тело `choose` и чистые помощники), `embassy/EmbassyTerrainSC.java`, `embassy/EmbassyWorldGenSC.java`, `embassy/ComponentEmbGroundSC.java` (только тела выравнивания, засыпки и расчистки; поля «Air», `airDecision`, `field` — ядро), `embassy/EmbassyRepairSC.java`, `embassy/EmbassyQuartersSC.java`, `embassy/CommandEmbassySC.java`.
- **Новые:** `embassy/EmbassyAirGen*SC.java` (по необходимости).
- **Не трогает:** `EmbassyAirfieldSC`, `EmbassyLayoutSC`, `MapGenEmbassySC`, `EmbassyPiecesSC`, `EmbassySiteSC` (ядро), куски P.

### P — постройки поля, вид блоков, светомаяк
- **Существующие:** заготовки `embassy/ComponentEmbRunwaySC.java`, `ComponentEmbApronSC.java`, `ComponentEmbTerminalSC.java`, `ComponentEmbHangarSC.java`, `ComponentEmbHelipadSC.java`, `ComponentEmbFunnelSC.java`, `ComponentEmbAirDefenceSC.java` (тела `buildField`); `block/BlockAirfieldSC.java` (только `registerBlockIcons` / `getIcon`); `block/BlockShowcase2SC.java` (только вид мет 10–13: иконки, `look`, `registerBlockIcons`); `embassy/EmbassyBeamSC.java` и `embassy/client/EmbassyBeamClientSC.java` (список `lights` в BEAMS, мигающий бело-зелёный светомаяк ночью); `tileentity/TileEntityShowcaseSC.java` (частицы новых мет — по необходимости).
- **Новые:** `assets/siliconage/textures/blocks/embassyAirfield_*.png`, `showcase2_towerConsole_*.png`, `showcase2_serviceColumn_*.png`, `showcase2_airfieldBeacon_*.png`, `showcase2_aaPost_*.png`, `tools/gen_airfield_tex.py`; помощники `embassy/ComponentEmbAir*SC.java` (не регистрируются).
- **Не трогает:** `ComponentEmbAirSC`, `EmbassyPiecesSC`, `EmbassyAirfieldSC` (ядро), куски города Ф6, `ComponentEmbGroundSC` (G).

### R — охрана поля, колонка, оплата, «Лётная книжка»
- **Существующие:** `settle/SettlementProtectSC.java` (§6.1), заготовка `embassy/EmbassyAirServiceSC.java` (§7.2–§7.3; `landed` не менять).
- **Новые:** `embassy/EmbassyAirService*SC.java` (по необходимости).
- **Не трогает:** `SettlementSC`, `SettlementRegistrySC`, `ReputationSC`, `EmbassyAirRulesSC`, `EmbassyAirDefenceSC`, API (ядро), `TradeServerSC` (только звать `takeChips` / `giveChips` / `chipsHeld`), `BlockShowcase2SC` (щелчок уже зовёт `useColumn`), `ArmorGasSC` (только звать).

### T — вкладка «Лётное поле» у Диспетчера
- **Существующие:** заготовка `embassy/EmbassyAirServerSC.java`; `settle/client/GuiTradeSC.java` (третья вкладка Диспетчера; «Рейсы» не ломать); `settle/client/TradeClientSC.java` (AIR_STATE / AIR_RESULT, `forget`).
- **Новые:** нет (по необходимости `settle/client/AirTabSC.java` — рисование вкладки).
- **Не трогает:** `SettleNetSC`, `TradeServerSC` (хуки ядра), `FlightServerSC`, `ClientProxy` (уже отдаёт все виды `TradeClientSC`).

### D — тексты, справочник, NEI, тесты, README
- **Существующие:** `assets/siliconage/lang/ru_RU.lang`, `en_US.lang` (CRLF), `manual/BookEmbassySC.java` (раздел «Лётное поле»), `manual/BookContent.java` (по необходимости), `nei/NEISiliconAgeConfig.java` (`hideEmbassyBlocks`: все меты `embassyAirfieldSC`; меты 10–13 витрин уже скрыты по `NAMES`), `nei/ObtainHandlerSC.java` (страница блоков поля и мет 10–13 — «только на лётном поле посольства»), `nei/NeiCoverageSC.java`, заготовки `debug/SelfTestAirfieldSC.java`, `debug/WorldTestAirfieldSC.java`; `debug/SelfTestEmbassySC.java` и `debug/WorldTestEmbassySC.java` (только если их проверки ломает поле — честные поправки), `debug/SelfTestSC.java` (`langQuotes` / `langFontTable` — не ослаблять).
- **Новые:** `docs/readme-airfield-draft.md` (ru + en).
- **Порядок:** ключи §9 и каркас статьи — сразу (`SelfTestCombatSC` и `SelfTestEmbassySC` перебирают `BlockShowcase2SC.NAMES` — без ключей `tile.siliconage.showcase2.<towerConsole|serviceColumn|airfieldBeacon|aaPost>.name` они красные; `SelfTestEmbassySC` ~стр. 1284 и `ObtainHandlerSC.embassyPage` знают меты 7–9 — дописать 10–13); тесты тел G / P / R / T — после пакетов; в конце — ключи из отчётов, сквозная сверка.

### Только координатор
Всё из §0 и §0.1, а также `SCMod.java`, `init/*`, `proxy/*`, `handler/GuiHandlerSC.java`, `dim/SubstrateConfigSC.java`, `settle/SettlementSC.java`, `settle/SettlementRegistrySC.java`, `settle/SettleNetSC.java`, `settle/ReputationSC.java`, `settle/SettlementBlocksSC.java`, `settle/SettlementCompatSC.java`, `settle/RosterSC.java`, `embassy/EmbassyLayoutSC.java`, `embassy/MapGenEmbassySC.java`, `embassy/EmbassyPiecesSC.java`, `embassy/EmbassySC.java`, `embassy/EmbassyBlocksSC.java`, `embassy/EmbassyAirfieldSC.java`, `embassy/EmbassyAirRulesSC.java`, `embassy/EmbassyAirDefenceSC.java`, `embassy/ComponentEmbAirSC.java`, `embassy/api/*`, `block/BlockAirfieldSC.java` (кроме вида), `block/ItemBlockAirfieldSC.java`, `block/ItemBlockShowcase2SC.java`.

## 11. Тесты — пакет D

### 11.1 Самотест `debug/SelfTestAirfieldSC` (из `SelfTestSC.run`, после `SelfTestEmbassySC`; чистая логика)
1. `problem()` = null на 12 сидах × r 88…96 × HELI / SHORT / FULL × обе стороны (648 планов); `EmbassyQuartersSC.problem` — тоже (с постами поля после G).
2. Кадр: `frame(x(a, q), z(a, q))` = (a, q) для 4 `gateDir` × 2 стороны; `world(rect)` — тот же прямоугольник.
3. Форма: `levelPast ≤ 0` на ВПП, перроне, площадках, рулёжке, ангаре; > 0 за квадратом; ровная форма + BLEND внутри ±(r + 39); `onShape` диска не меньше старого.
4. Огни: все `lights(LIGHT_EDGE/THRESHOLD/END)` на покрытии ВПП, `LIGHT_TAXI` — на «А»; числа: FULL 64 / 18 / 18 / 10, SHORT 28 / 14 / 14 / 0; всё в `inZone`; колонки, пушки, пульт, светомаяк, колдун — в зоне / в своих постройках.
5. Запись: `writeDecision` → `decisionOf` (тот же массив); без «X» — `fieldOf` = null; NONE — null; `fill` без `air` не трогает «X»; `plan(seed, site, floor)` == `plan(…, null)` (посты и лоты те же).
6. `EmbassyAirRulesSC`: порядок отказов; цена для каждого уровня (`PRICE`); единицы (округление вверх); потолки PER_SERVICE / PER_DAY; аварийная только «Чужак»/«Нарушитель»; `admitted` (Знакомый — да, Чужак/Нарушитель/Враг — нет); `passAvailable`; `logbookChips` под 64.
7. API: `API_VERSION` = 1; PAY_ / PASS_ / AD_ = числа правил; `isRunwaySurface` для мет 0, 3, 5, 6, 7 и нет для 1, 2, 4, 8 и других блоков; `isEmbassyBlock`.
8. Куски: `EmbassyPiecesSC.NAMES` — первые 12 прежние, затем 7 новых; каждое имя зарегистрировано.
9. Меты: `BlockShowcase2SC.NAMES` 14, `LIGHT` той же длины; `BlockAirfieldSC.NAMES` 9, `LIGHT` 9; `SettlementBlocksSC.unbreakable(airfield)`.
10. ИКАО и номера: `icao(0, 1)` = XTAA, `icao(6, 1)` = XWOA, `icao(17, 1)` = XVAA, `icao(18, 1)` = XYTA, `icao(0, 27)` = XTAA; `number(heading(0, true))` = 36, `heading(0, false)` → 18, `gateDir` 1 → 09/27.
11. Реестр: `reach()` записи с полем покрывает `zoneBox`; `inBelt` на поле; `overlapping` ловит ящик над полем; запись без «X» — прежние `reach` и `inBelt`.
12. Lang: все ключи §9 есть в ru и en; `langQuotes`, `langFontTable` зелёные.
13. G: чистые проверки `EmbassyAirSiteSC` на синтетических данных (ILS-бит снимается у плохого створа).

### 11.2 Мировой тест `debug/WorldTestAirfieldSC` (`-Dsc.worldtest`, после `WorldTestEmbassyFlySC` — ждать его `finished`)
`setEmbassyAirfield(ALWAYS)` на время теста (старое — обратно). Новое посольство на новой земле вдали от других; `ChunkEvent.Load` до и после — нет чанков вне окон старта (**нет каскада**); телепорт FakePlayer в центр будущего поля до его генерации; проверка построенного по `Field` (покрытие, огни в `lights`, колонки, пульт, светомаяк, пушки в `aaBlock`, снега нет на ВПП после `func_147478_e`); охрана (ломать / ставить / взрыв / спавн на поле — отказ; щелчок по колонке — проходит); реестр (`reach`, `airfieldAt`, `overlapping`); API (`airfields`, `airfieldAt`, `neutralAt`, `isRunwaySurface`, коды `quote` / `pay`, `firstLanding` один раз, `requestPass` раз в сутки, `admitted` / `escortOf`); газы брони колонкой; AIR_STATE у Диспетчера; Достройка поля (снег и «рост» уходят, свой блок остаётся); старая запись без «X» не тронута; конфиг AUTO без аддона — запись без «X».

### 11.3 Только в игре (после пакетов)
Вид поля днём и ночью (огни, светомаяк, памятник), FPS (~70 светящихся блоков), снег на полосе зимой, деревья в створах (TC, IC2, большие дубы), посты Постовых ночью (поиск пути по 100+ блокам), колонка вдвоём по LAN (две квоты), вкладка у двух игроков, `-Dsc.packcheck` на сборке пользователя (TLauncher Forge, его javaw не закрывать), JourneyMap. С аддоном — в его прогоне.

## 12. Жёсткие правила для всех
1. Аддон Vehicles (и любые `SAV-wt-*`) **не трогать**; `com.sva` в ядре не упоминать — только `Loader.isModLoaded("SiliconAgeVehicles")` и API ядра. Файлы не удалять. Не коммитить.
2. Клиентский код — только в client-пакетах; общий код не зовёт `@SideOnly(CLIENT)`-методы; FakePlayer пакетов не получает, не платит, не получает «Лётной книжки» и пропуска 7700.
3. Генерация — без каскада: решение — по слоям биомов; выравнивание — только массивы своего чанка; куски — только окно populate; старые старты и записи (без «Air» / «X») — ровно прежнее поведение.
4. LAN вдвоём: всё решает сервер; состояние — по UUID (`PlayerRec.extra` «X», `LocalRec.extra` «X», `LocalRec.counts`); двое у одной колонки — каждому своя квота.
5. В постройках поля нет брёвен и досок и нет ценных блоков; всё неломаемое — покрытие, отделка и витрины мода; рецептов нет.
6. Каждый новый блок / мета — с подсказкой TooltipSC (кратко, Shift, Ctrl) и скрыт в NEI (D).
7. Тексты добавляет только D; остальные присылают `ключ=ru | en`. Кавычки — „…“ / “…”, никогда « ».
8. Реестровые имена, меты, ключи NBT и `extra`, имена кусков, LOT_*, виды сообщений — фиксированы (§2), только дописываются.

## 13. Риски и что проверить в игре
1. **Посадочная дистанция лайнера** не измерена (181 от смещённого порога) — замер `LinerSimSVA` в прогоне аддона до фиксации 200 / 20; варианты: смещение 10, лайнер без автопосадки, SHORT закрыт для лайнера.
2. **Рулёжка «А» за +a торцом** лежит в 7–13 от оси под заходом над этим торцом: авто-вышка аддона держит руление, пока идёт посадка над +a торцом (или сажает над −a).
3. **Деревья за a = ±150** — глиссада там ≥ 5,2 блока; меры — проверка створа (FOREST ≤ 30%), угол 3,5°, строка ATIS. Аудит 2026-11-04 (ЛП-2 А): у торца с ILS створ расчищается до 300 за порогом, холм выше 1:20 снимает ILS (оценка по слоям биомов — проверить в мире).
4. **Рамка старта ±150** запрещает ванильные деревья на всём прямоугольнике (§4.4). Аудит 2026-11-04: снят — `covers()` смотрит куски старта (рамка теперь до ±380).
5. **Доля FULL неизвестна** — замер на 12 сидах. Аудит 2026-11-04 (ЛП-3 Б): лес в створе FULL не запрещает — перемер самотестом 2026-11-23: FULL 100 из 131 мест (76%), оба торца с ILS — 14 (10%); было 62 (47%) и 18 (13%).
6. **Один сид — разные посольства** с аддоном и без (AUTO; принято пользователем).
7. **Ангар 5, а не 11** — переходная поверхность; техника аддона ниже 2,7 — проверить вид.
8. **Постовые и Газовщик на поле** — путь ночью по открытой земле; `inBelt` теперь включает поле (стража ведёт бой и на поле) — проверить, что она не уходит за ограду.
9. **Колонка без аддона** (ALWAYS) — только газы брони; с аддоном крадучись — газы ядра.
10. **FPS** — ~70 светящихся блоков (свет вручную при генерации).
11. **Расхождение API** ядра и аддона — `API_VERSION`, `CORE_DEP`.
12. **Охрана поля без штрафов** — гриферство ограничено отменой; если нужно «Доверие» −5 — одна строка R.

## 14. Неясно / решено координатором
- **Ключ записи — «X», не «A»**: «A» занята `RaidAlarmSC` (ПД-3, bf114b4) на `Settlement.extra` и `PlayerRec.extra`; «X» свободна в `Settlement`, `LocalRec`, `PlayerRec`.
- **Один вертодром „Н“** (по другую сторону Вокзала, у всех вариантов), а не два (на перроне и у Вокзала): на перроне FULL нет места рядом с МС-1 и рулёжкой.
- **Ангар 27 × 21 × 5** вместо × 11 и сдвинут к куполу (a r − 24…r + 2, q r − 23…r − 3): иначе пробивает 1:4 (`problem`).
- **5 постов ПВО**, площадка 5 × 5, высота 2: два со стороны от поля, один за куполом, два у поля; все у всех вариантов.
- **Охрана поля без нарушения «Доверия»** (как зона причала) — решение пользователя 5 «без штрафов».
- **Пропуск 7700 — по игроку**, не по борту: в журнале игрока в этом посольстве (пилот, запросивший 7700).
- **Прикрытие звеном — ESCORT_RANGE 64** (TODO(balance)); экипаж/пассажиры — аддон (`riders()`).
- **`pay` — количество в мере услуги + флаг `free`** (отличие от §9 предложения: удобнее аддону, единицы и округление — ядро).
- **Колонка крадучись — всегда газы ядра** (с аддоном обычный щелчок открывает его окно).
- **Без поля нет вкладки** у Диспетчера (лор «законсервировано» виден только при ALWAYS без аддона).
- **`inBelt` включает зону поля** (ПЖ-2 и стража на постах поля).
- **Ревизия** — счётчик в памяти (растёт и при остановке сервера), не в NBT: аддон строит виртуальные аэропорты при загрузке мира всё равно.
