# Подложка (Si-14) — техплан Ф6 «Посольства Наладчиков» и разбивка на пакеты (2026-10-19)

Дизайн: `docs/idea-embassy-substrate.md` (сводное предложение после проверки двух проектов; все вопросы §14 решены пользователем 2026-10-19), коды правил Ф6 — `docs/plan-substrate.md` §54–§63. Техпланы Ф1–Ф5: `docs/plan-substrate-tech.md`, `-f2.md` … `-f5.md` (их правила работы, сборка и запреты действуют и здесь).
Здесь — архитектура Ф6 под существующий код и разбивка на 5 параллельных пакетов (G, S, R, T, D) с **непересекающимися файлами**. Подфазы предложения §13: Ф6.1 — ядро (написано, §0), Ф6.2 — G, Ф6.3 — S, Ф6.4 — R, Ф6.5 — T и D.
Java 6 (без лямбд, diamond, try-with-resources, switch по String, без API Java 7+), MCP-имена, Forge 1.7.10-10.13.4.1614.
Сборка:
`T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /c/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q`
Строки «Read timed out» и 404 безвредны. runServer не запускать, сборка — всегда clean build. Проверка reobf: `unzip -p build/libs/SiliconAge-0.2.2.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` печатает 1 (после релиза 0.2.2 jar называется `SiliconAge-0.2.2.jar`; `0.2.1` в старой команде больше не собирается). Каждый пакет обязан собираться сам по себе поверх ядра.

## 0. Ядро (уже написано, собирается)

Правит **только координатор**. Нужна правка ядра или чужого файла — описать её в отчёте (файл, место, текст). Заготовки (стабы) принадлежат пакетам, указанным в их Javadoc: сигнатуры, имена, меты, ключи NBT и числа в них фиксированы, тела — пакетов.

| Файл (`com/sc/…`) | Что |
|---|---|
| `dim/SubstrateConfigSC.java` | Раздел конфига `embassy` (тот же файл, `CATEGORY_EMBASSY`), правила: `embassies` (true), `embassyGrid` (2048, 1024…16384), `embassyChance` (45), `embassySpawnMin` (1500), `embassyRingMin` (1300), `embassyRingMax` (2500; меньше `min + 256` — расширяется само). Публичные статические — значит правила: уходят клиенту через `SubstrateNetSC` (`ruleFields()` — теперь **20**). Комментарии ru / en с „…“ |
| `settle/SettlementSC.java` | `EMBASSY_DIM` = 0; **`governs(w)`** — Подложка или Верхний мир при `embassies`; `embassiesGenerate()` (Подложка включена и `embassies`); `isEmbassy(s)` (размер `EMBASSY`); `inSubstrate(s)`. `protecting`, `beltAt`, `dockZoneAt`, `fieldOverlapOwner`, `refusesBridgeEnd` — через `governs` (было `SubstrateSC.is`). `displayName` посольства — `sc.embassy.name` |
| `settle/SettlementSizeSC.java` | `EMBASSY` (ординал 3): купол 88–96, 18–22 жителя, 6 Постовых + 4 Старших, Капитан (100%), Комендант, причал. `SUBSTRATE_SIZES` = 3, `substrate()`. Генератор Подложки его не выбирает (`SIZE_WEIGHTS` длиной 3, `RosterSC.sizeOf` — максимум CITY) |
| `settle/NpcKindSC.java` | `DISPATCHER` (12, «Диспетчер прокола»), `QUARTERMASTER` (13, «Интендант») — жители; `langKey(boolean embassy)` — Старейшина посольства «Посол» (`sc.settle.kind.ambassador`) |
| `settle/SettlementRegistrySC.java` | `Settlement.dockZone` — радиус зоны причала записи (по умолчанию `SettlementSC.DOCK_ZONE` 5; у посольства 13); NBT `DockZone` пишется только когда ≠ 5 (записи Ф2–Ф5 не меняются); `inDockZone` и `reach()` читают его; `embassies()` |
| `settle/ReputationSC.java` | `give(p, amount, source, day, ceiling)` (старая сигнатура осталась), `EMBASSY_CEILING` = 599 (`OWN_FROM − 1`), `NO_CEILING`, `ceiling(s)` |
| `tileentity/TileEntitySettlementCoreSC.java` | `settlementTick` — через `governs` (было «только Подложка»); у посольства после `RosterSC.tick` — `EmbassyRepairSC.tick` (Достройка, G) |
| `settle/SettlementProtectSC.java` | `onDetonate` — через `governs` (взрывы в цилиндре и зоне Вокзала посольства гасятся) |
| `settle/npc/ai/NpcLinkSC.java` | `exiled`: удаляется существо вне мира, где поселения работают (`governs`), или в мире ≠ `record.dim` (житель посольства, унесённый в Подложку, и наоборот); `setHome`: житель, чей пост дальше купола − 2 (Диспетчеры посольства на Вокзале), получает дом вокруг поста радиусом `OUTPOST_HOME` 8 |
| `settle/travel/ItemSettlementMapSC.java` | `make`: карта, купленная в посольстве (запись не в Подложке), строится от `first(Si-14)` |
| `settle/RosterSC.java` | Диспетчер у причалов Подложки: `dispatcherDue(s, substrateDim)` (запись Подложки своих размеров с причалом и без Диспетчера), `addDispatcher(s)` (слот в КОНЕЦ, NEVER, новое имя, `extra` «S» `F6Disp`), `dispatcherPost(s)` (по ту сторону улицы от `P_GATE`); вызывается в `tick` после апгрейда Ф3 — старые поселения получают его без перестройки. `visitors`: причал посольства не попадает в `knownDocks`; Пропуск — под потолком (`ceiling(s)`); посольство отмечается «виденным» (`EmbassyTicketSC.see`, строка `sc.embassy.seen`). `restore` записи посольства — `EmbassySiteSC.siteAtCore` + `EmbassyLayoutSC.plan` / `fill` |
| `settle/SettleNetSC.java` | Виды: → клиенту `FLIGHT_STATE` 3, `FLIGHT_RESULT` 4; → серверу `FLIGHT_DO` 6, `FLIGHT_REFRESH` 7 (обработчик сервера отдаёт их `embassy.FlightServerSC.onMessage`, остальное — `TradeServerSC`) |
| `settle/trade/TradeServerSC.java` | Хук координатора в `open`: у Диспетчера после `TRADE_STATE` — `FlightServerSC.sendState(p, t)`. Остальное в файле — T |
| `settle/gen/SettlementLayoutSC.java` | `Plan` больше не `final` (наследник — `EmbassyLayoutSC.Plan`); больше ничего |
| `block/BlockShowcase2SC.java` | Меты Ф6 в конце: 7 `gravityCoil` (сегмент Большого кольца), 8 `cryoTank` (баки Вокзала), 9 `dispatchConsole` (стол Диспетчерской); `NAMES`, `LIGHT` (8 / 3 / 6). Вид (иконки, `look`) — S |
| `tileentity/TileEntityFieldGeneratorSC.java` | `playerZoneNear(w, dim, x, z, reach)` — поле игрока рядом (реестр зон; ядра поселений не считаются) |
| `settle/SettlementCompatSC.java` | IMC AE2 Spatial: `embassyRingSC` (новые меты витрин уходят вместе с `showcase2SC` по `NAMES.length`) |
| `embassy/EmbassySC.java` | Имена структуры `SCOwEmbassy` / `SCOwEmb*`, `init`, `mapGen(world)` (один `MapGenEmbassySC` на Верхний мир сервера), `forget`, `clearAll` |
| `embassy/EmbassySiteSC.java` | **Размещение, написано целиком** (§5.1): сетка, шанс, кандидаты, спавн, биомы через `WorldChunkManager`, деревни и храмы, гарантированное посольство и его запись в реестре, «открытая земля» без создания файлов регионов, кэш |
| `embassy/EmbassyLayoutSC.java` | **Геометрия посольства, состав, запись** (§5.2): стена, Вокзал, кольцо, вихрь, зона, ровная площадка и полоса сопряжения, пол, рамка (`Plan.x/z/frame`), `roster`, `rosterProblem`, `plan` (кварталы — `EmbassyQuartersSC`, S), `record`, `fill`, `planOf`, `targetY`, `inVortex`, `onVokzal` |
| `embassy/EmbassyRulesSC.java` | **Все числа и чистые решения рейсов, написаны целиком** (§7.1): окно, рейсы, уровни, цены, лимиты, билет «туда-обратно», спасательный рейс, поручительство, мягкий допуск, отказы |
| `embassy/EmbassyTicketSC.java` | **Записи игрока о рейсах** (`PlayerRec.extra` «R»), написаны целиком: ознакомительный, посольство вылета, виденные посольства, обратная половина, талон, спасённая гибель, поручительство |
| `embassy/EmbassyNetSC.java`, `embassy/client/EmbassyClientSC.java` | Канал `SiliconAgeDim`, дискриминатор 5 → клиенту: `BEAMS` 1 (S), `FLIGHT_FX` 2 (R); `send`, `sendNear` |
| `embassy/EmbassyBlocksSC.java`, `EmbassyItemsSC.java`, `EmbassyRecipesSC.java` | Регистрация `embassyRingSC` + TE `SiliconAge.embassyRing`; предмета `embassyFinder`; рецепт Пеленгатора (компас + 2 Транзистора + Кремниевая пластина, бесформенный) |
| `embassy/StructureEmbassyStartSC.java`, `ComponentEmbassySC.java` | Старт (наследник старта поселения, NBT `SettlementId`) и базовый кусок «SCOwEmbPart» (наследник `ComponentSettlementSC`: окно populate, флаг 2, свет вручную, палитра) |
| Заготовки G | `embassy/MapGenEmbassySC.java` (фиксированы `RANGE` 10, имена, `registerStructures`, `ensureLoaded`, `startAt(w, …)`, `covers(w, …)`; заглушка ничего не ставит), `EmbassyWorldGenSC.java`, `ComponentEmbGroundSC.java`, `EmbassyRepairSC.java`, `CommandEmbassySC.java` |
| Заготовки S | `embassy/EmbassyQuartersSC.java` (заглушка-раскладка: посты в фиксированных точках, лоты Сердца, Вокзала и кольца), `EmbassyPiecesSC.java`, `EmbassyBeamSC.java`, `embassy/client/EmbassyBeamClientSC.java` |
| Заготовки R | `embassy/FlightServerSC.java`, `FlightSC.java`, `tileentity/TileEntityEmbassyRingSC.java` (фазу и NBT `scRing` держит уже ядро), `block/BlockEmbassyRingSC.java`, `block/ItemBlockEmbassyRingSC.java`, `embassy/client/RingClientSC.java` |
| Заготовки T | `embassy/EmbassyTradeSC.java`, `ItemEmbassyFinderSC.java`, `EmbassyHintSC.java` |
| Заготовки D | `debug/SelfTestEmbassySC.java`, `debug/WorldTestEmbassySC.java` |

### 0.1 Правки ядра в общих файлах (готово, остальным их не трогать)
- `SCMod`: в preInit — `EmbassyWorldGenSC.INSTANCE` на `MinecraftForge.EVENT_BUS`, `EmbassyWorldGenSC.DECOR` на `MinecraftForge.TERRAIN_GEN_BUS`, `FlightSC.INSTANCE`, `EmbassyBeamSC.INSTANCE`, `EmbassyHintSC.INSTANCE` на шине FML; в `registerRecipes()` — `EmbassyRecipesSC.init()`; в init — `EmbassySC.init()` после `ArenaSC.init()` и `GameRegistry.registerWorldGenerator(EmbassyWorldGenSC.INSTANCE, 1000)`; в `serverStopped` — `EmbassySC.clearAll()`; в `serverStarting` — `CommandEmbassySC`; при `-Dsc.worldtest` — `WorldTestEmbassySC` на FML.
- `ModItems.init`: после `FinaleItemsSC.init()` — `EmbassyItemsSC.init()`. `ModBlocks.init`: после `ArenaBlocksSC.init()` — `EmbassyBlocksSC.init()`.
- `SubstrateNetSC.init`: `EmbassyNetSC.register(CHANNEL)` (дискриминатор 5).
- `CommonProxy` / `ClientProxy`: `embassyMessage(kind, nbt)` → `EmbassyClientSC.onMessage`; в `ClientProxy.preInit` — `EmbassyBeamClientSC.register()`, `RingClientSC.register()`.
- `debug/SelfTestSC.run`: `SelfTestEmbassySC.run()` после `SelfTestFinaleSC`.
- **Честные поправки самотестов Ф1–Ф5 (сделаны ядром, потому что их ломает ядро):** `SelfTestSubstrateSC.RULES` += 6 правил раздела `embassy`; `SelfTestCombatSC` — правил 14 → 20, `kindsAndSizes` — 14 видов (+ `dispatcher`, `quartermaster`, не стражи); `SelfTestHazardSC` — правил 14 → 20, налёты и строки таблицы наград — по `SUBSTRATE_SIZES` (посольство не грабят); `SelfTestFinaleSC` — правил 14 → 20; `SelfTestSettlementSC` — `SUBSTRATE_SIZES == 3`, строки таблицы размеров `world.settlements` — по `SUBSTRATE_SIZES` (посольство — своя статья, §9.4). Тексты новых видов, размера и мет витрин (их ждут `SelfTestCombatSC` / `SelfTestSettlementSC`) — D (§9.3).

### 0.2 Что уже работает с одним ядром
Регистрируется всё (блок кольца и его TE, Пеленгатор и рецепт, имена структуры, сообщения, правила, меты витрин, виды жителей, размер). **Посольств нет** (`MapGenEmbassySC.canSpawnStructureAtCoords` — false, тело — G). Обобщённые проверки уже работают, но в Верхнем мире записей нет — поведение Верхнего мира прежнее. Сразу работают: Диспетчер у каждого причала Подложки (Посёлок, Город, «Первый причал»; посты — нет), с пустым экраном обмена, пока R не сделает вкладку «Рейсы» (скин — T); потолок «Доверия» (для записей-посольств); зона причала по записи. `EmbassySiteSC` уже отвечает, где посольства будут (команда — G).

## 1. Регистрация и порядок

| Этап | Что | Где |
|---|---|---|
| preInit | `SubstrateConfigSC.load` → раздел `embassy` (6 правил) | ядро |
| preInit | Блок `embassyRingSC` + TE (`EmbassyBlocksSC`, после арены); предмет `embassyFinder` (`EmbassyItemsSC`, после Ф5) | ядро |
| preInit | Канал `SiliconAgeDim`: дискр. 5 (`EmbassyNetSC`); новые виды `SettleNetSC` 3, 4, 6, 7 | ядро |
| preInit | Шины: `EmbassyWorldGenSC` (EVENT_BUS: `ReplaceBiomeBlocks`, `WorldEvent.Load/Unload`), `.DECOR` (TERRAIN_GEN_BUS: `Populate`, `Decorate`), `FlightSC`, `EmbassyBeamSC`, `EmbassyHintSC` (FML) | ядро; тела — G, R, S, T |
| preInit, клиент | `EmbassyBeamClientSC` (S), `RingClientSC` (R: TESR кольца) | пакеты |
| init | `EmbassySC.init` → `MapGenEmbassySC.registerStructures` (старт, «Part», «Ground», `EmbassyPiecesSC.register` — куски S) — всегда, даже при выключенных посольствах; IWorldGenerator вес 1000; рецепт | ядро + S |
| загрузка мира 0 | `EmbassySiteSC.decideGuaranteed` (G зовёт в `onWorldLoad`; иначе — сам при первом вопросе) | ядро + G |
| мир 0, provideChunk | `ReplaceBiomeBlocks`: старты в радиусе 10 чанков → выравнивание массивов чанка → (ваниль: поверхность биома, пещеры, овраги, структуры) | G |
| мир 0, populate | запреты Populate / Decorate в окнах площади + 8 → (ванильный populate, чужие генераторы) → наш генератор: куски окна (земля G, затем дома S) | G + S |
| жизнь | ядро поселения (состав, Клон-капсула, Пропуск, Достройка G), окна рейсов (R), лучи (S), строка при первом входе (T) | пакеты |

## 2. Общие решения

### 2.1 Конфиг (раздел `embassy`, холдер `SubstrateConfigSC`)
| Ключ | По умолчанию | Что |
|---|---|---|
| `embassies` | true | ПЕ-1, ПП-14: посольства появляются (в неоткрытой земле) и работают (защита, жители, рейсы). Выкл.: новых нет; созданные стоят пустыми (`governs` = ложь: ядро не тикает, жители удаляются как «вне мира») |
| `embassyGrid` | 2048 (1024…16384) | ПЕ-1: регион; кандидаты — в четверти региона от середины, два посольства не ближе половины региона |
| `embassyChance` | 45 | ПЕ-1: шанс региона, % — TODO(balance): `/scembassy list 4000` на сиде пользователя |
| `embassySpawnMin` | 1500 | ПЕ-1: не ближе к спавну (кроме гарантированного) |
| `embassyRingMin` / `embassyRingMax` | 1300 / 2500 | ПЕ-2: кольцо гарантированного (`max ≥ min + 256`, иначе расширяется) |

Все шесть — правила (публичные статические): клиент получает значения сервера (справочник показывает числа сервера). Раздел помечен `setCategoryRequiresWorldRestart`. Числа размещения меняют только ещё не созданную землю; решение о гарантированном перепроверяется только если в реестре записано «нигде» при других числах (§5.1).

### 2.2 Реестровые имена и id (фиксированы, только дописываются)
| Что | Имя / id | Владелец |
|---|---|---|
| Структура | старт `SCOwEmbassy` (`StructureEmbassyStartSC`), куски `SCOwEmbPart` (база, ядро), `SCOwEmbGround` (G), `SCOwEmb<Имя>` (S: Heart, Square, Workshop, Depot, Greenhouse, House, Wall, Tower, Gate, Street, Vokzal, Ring, Office — по необходимости). Данные — `data/SCOwEmbassy.dat` Верхнего мира. Префикс `SCOw` не пересекается с `SCSi14*` | ядро, G, S |
| Блок | `embassyRingSC` (`BlockEmbassyRingSC`, одна мета, неломаемый, свет 9), TE `SiliconAge.embassyRing` (`TileEntityEmbassyRingSC`, NBT `scRing` {id}) | R |
| Меты витрин | `showcase2SC` 7 `gravityCoil`, 8 `cryoTank`, 9 `dispatchConsole` | S (вид) |
| Предмет | `embassyFinder` (`ItemEmbassyFinderSC`, стек 1, не расходуется) | T |
| Виды жителей | `NpcKindSC` 12 `DISPATCHER` («dispatcher»), 13 `QUARTERMASTER` («quartermaster»); скины `textures/entity/tuner_dispatcher.png`, `tuner_quartermaster.png` (`RenderTunerSC` уже берёт `tuner_<key>`) | T (скины) |
| Размер | `SettlementSizeSC` 3 `EMBASSY` («embassy») | ядро |
| Команда | `/scembassy` (псевдоним `/scemb`), уровень 2 | G |
| Сущности | новых нет (Диспетчер и Интендант — `EntityTunerSC`); пул 0–25 не меняется, следующий свободный — 26 | — |
| Биомы, измерения, GUI | новых нет | — |

### 2.3 Рецепты (`EmbassyRecipesSC`, ядро)
Пеленгатор посольства = компас + 2 Транзистора (`SiliconMaterial.TRANSISTOR`) + Кремниевая пластина (`SI_WAFER`), бесформенный. Других рецептов в Ф6 нет: билеты — запись, Капсула и Карта — лавка. `-Dsc.packcheck`: пересечений быть не должно.

### 2.4 Перечисления и коды
`SettlementSizeSC.EMBASSY` 3. `NpcKindSC.DISPATCHER` 12, `QUARTERMASTER` 13. `BlockShowcase2SC.GRAVITY_COIL` 7, `CRYO_TANK` 8, `DISPATCH_CONSOLE` 9. `EmbassyLayoutSC.LOT_*`: HEART 0, SQUARE 1, WORKSHOP 2, DEPOT 3, GREENHOUSE 4, HOUSE 5, WALL 6, TOWER 7, GATE 8, STREET 9, VOKZAL 10, RING 11, OFFICE 12 (S дописывает). `EmbassyRulesSC`: фазы IDLE 0, COUNTDOWN 1, VORTEX 2, COOL 3; рейсы INTRO 0, FIRST 1, DOCK 2, RESCUE 3, HOME 4, BACK 5, OTHER 6; цели-не-поселения `TARGET_FIRST_PURE` −2, `TARGET_MARK` −3; отказы OK 0, OFF 1, ENEMY 2, VIOLATOR 3, LEVEL 4, LIMIT 5, USED 6, NO_MARK 7, NO_TARGET 8, SIGNAL 9, CHIPS 10, BUSY 11, NOT_HERE 12, NO_BACK 13, FRIEND 14, PENDING 15, CONFIRM 16, CHANNEL 17 (`REFUSE_KEYS`). `SettleNetSC`: FLIGHT_STATE 3, FLIGHT_RESULT 4, FLIGHT_DO 6, FLIGHT_REFRESH 7. `EmbassyNetSC`: BEAMS 1, FLIGHT_FX 2. `TradeTableSC` (T дописывает ключи): `qmBread`, `qmLogs`, `qmCoal`, `qmIron`; `ContractTableSC` (T): `embSupplyBread`, `embSupplyLogs`, `embSupplyIron`, `embDispatch`; `TradeServerSC.RESULT_KEYS` (T, в конец): `notHere` уже есть — для закрытых в посольстве сделок новых кодов не нужно.

### 2.5 Хранилище
| Что | Где | Владелец |
|---|---|---|
| Посольства | глобальный реестр поселений: записи размера `EMBASSY`, `dim` 0, `dockZone` 13, точки `P_*` и `ring`, `vokzal`, `office`, `backGate`, `depot`, `hall` | ядро (запись делает G через `EmbassyLayoutSC.record`) |
| Гарантированное посольство | реестр, мировой `extra` «E»: `x`, `z`, `r`, `f` (пол), `s` (сид), `g` (точка сетки), `at`; либо `none` + `grid`, `min`, `max`, `chance` | ядро (`EmbassySiteSC`) |
| Проход Достройки | `Settlement.extra` «G» {`w` — следующее окно, `p` — конец прохода} | G |
| Диспетчер Ф6 у причала Подложки | слот в конце `slots`; `extra` «S» `F6Disp` | ядро (`RosterSC`) |
| Рейсы игрока | `PlayerRec.extra` «R» (`EmbassyTicketSC`): `intro`, `from`, `seen` (int[]), `back` {e, u}, `talon` {r, t, s, c, d, g, + R: `o` — талон «туда-обратно»: его возврат снимает обратную половину}, `rescued`, `guar` {f, s, a, + R: `n` — имя друга}, `guarPaid`; R дописал `owed` (КЧ возврата, плательщик был не в сети) | ядро (доступ), R (пишет) |
| Лимиты рейсов | `PlayerRec.globalCounts`: `embRide` (2 в сутки), `embOther` (1), `embGuar` (1), период — сутки | R |
| Сделки Интенданта | обычные сделки: `LocalRec.counts` посольства | T |
| Контракты Посла | `PlayerRec.contracts` (`ContractRec.settlement` = id посольства; «Депеша» сдаётся у любого Старейшины Подложки) | T |
| Строка при первом входе | постоянный NBT игрока `scEmbHint` | T |
| Старты | `data/SCOwEmbassy.dat` (ванильный `MapGenStructureData`) | G |
| Кольцо | NBT TE `scRing` {id}; фаза и такты — описание TE (не сохраняются) | ядро / R |
| Переходное | окна рейсов, предупреждения мягкого допуска (`FlightSC`, R); кэш площадок (`EmbassySiteSC`, ядро); списки лучей (`EmbassyBeamSC`, S) — всё чистится в `serverStopped` | |

Всё, что у игрока, — по UUID в глобальном реестре или в его постоянном NBT (переживает смерть и удалённую `DIM-1414`, у друга по LAN своё). Талон — в `PlayerRec`, а не в `LocalRec` посольства, как было в предложении: его надо найти из Подложки (обратный рейс) и на рассвете (возврат), не зная поселения (§14).

### 2.6 «Доверие» и потолок (ПД-7)
`ReputationSC.give(p, amount, source, day, ReputationSC.ceiling(s))` — всюду, где «Доверие» начисляется **за дела в посольстве**: Пропуск (ядро, `RosterSC.pass`), сделки и контракты, искупление у Посла (T, `TradeServerSC`). Прибавка обрезается до 599; игрок выше потолка (добрал делами в Подложке) получает 0 и ничего не теряет; штрафы без потолка. «Депеша домой» сдаётся в Подложке — там потолка нет. Суточные лимиты общие (64 КЧ выдачи, +20 за сделки, 3 контракта): `PlayerRec` один на сервер. «Враг» — свой у каждого посольства (`LocalRec.enemyUntil`). Рейс — не сделка: «Доверия» не даёт.

### 2.7 Сеть
| Канал / дискр. | Сообщение | Направление | Виды |
|---|---|---|---|
| `SiliconAgeDim` 0 | `SubstrateNetSC.Message` | → клиент | id и правила (теперь 20) |
| 1, 2 | `SettleNetSC` | ↔ | + `FLIGHT_STATE` 3, `FLIGHT_RESULT` 4 (→ клиент, R); `FLIGHT_DO` 6, `FLIGHT_REFRESH` 7 (→ сервер, R) |
| 3, 4 | `HazardNetSC`, `FinaleNetSC` | | без изменений |
| 5 | `EmbassyNetSC.ToClient` | → клиент | `BEAMS` {list: [{x, y, z}]} (S), `FLIGHT_FX` {k, x, y, z, t} (R) |

Фаза кольца идёт пакетом описания TE (`TileEntityEmbassyRingSC.getDescriptionPacket`: `p`, `t`). Обработчик клиента может прийти не в клиентском потоке — классы пакетов пишут только volatile-поля или свою очередь. FakePlayer пакетов не получает (`send` проверяет). Свободны: `SiliconAgeDim` — 6 и дальше; `ArmorNetSC` — 7 и дальше.

### 2.8 DataWatcher, ключи NBT
Новых сущностей нет. Вид жителя — прежний слот `EntityTunerSC` (байт, ординалы 12 и 13 помещаются); имя «Посол» клиент выводит сам: `NpcKindSC.langKey(!SubstrateSC.is(world))` — мир посольства клиент узнаёт по провайдеру. NBT: TE кольца `scRing` {id}; куски — `SettlementId`, `Floor`, `Facing`, `Variant` (базы) + свои поля пакета; старт — `SettlementId`; игрок — `scEmbHint`; `SubstrateEntrySC.NBT_FROM` ставит R после рейса в Подложку (точка Вокзала посольства вылета).

### 2.9 GUI
Новых окон нет: вкладка «Рейсы» — внутри экрана обмена (`GuiTradeSC`, `TRADE_GUI_ID` 21) у Диспетчера, как «Контракты» у Старейшины. Пеленгатор, кольцо — щелчок и строка в чат. Свободные id: 10–19, 22+.

## 3. Правила Ф6 (коды — `docs/plan-substrate.md` §54–§63)
ПЕ-1…ПЕ-13 — посольства (размещение, земля, город, Вокзал, Достройка, команда, поиск, луч, защита); ПП-14 — обобщение «только Подложка»; ПЖ-11…ПЖ-14 — Диспетчер, Интендант, состав посольства, «Посол»; ПГ-21 — стражи посольства; ПР-7, ПР-8 — зона причала записи, Диспетчер у причалов Подложки; ПД-7 — потолок «Доверия»; ПО-23…ПО-27 — лавка, Карта, Интендант, Пеленгатор, контракты Посла; ПЯ-1…ПЯ-16 — рейсы Большого кольца туда и обратно; ПК-26…ПК-30 — конфиг, имена, совместимость, мультиплеер.

## 4. Обобщение «только Подложка» (ядро, ПП-14 — сделано)
Пять проверок предложения (A.1, A.2, §9) и их окружение:

| Место | Было | Стало |
|---|---|---|
| `SettlementSC.substrate()` → `governed()` (`protecting`, `beltAt`, `dockZoneAt`, `fieldOverlapOwner`, `refusesBridgeEnd`) | `SubstrateSC.is(w)` | `governs(w)` |
| `SettlementProtectSC.onDetonate` | `SubstrateSC.is(w)` | `governs(w)` |
| `TileEntitySettlementCoreSC.settlementTick` | `SubstrateSC.is(worldObj)` | `governs(worldObj)` (+ Достройка у посольства) |
| `NpcLinkSC.exiled` | «не Подложка» | «не `governs`» или «мир ≠ `record.dim`» |
| `ItemSettlementMapSC.make` | от записи покупки | от `first(Si-14)`, если запись не в Подложке |
| `RosterSC.visitors` | любой причал — в `knownDocks` | кроме посольств (мост их и так не покажет: `si14Targets` берёт только Подложку) |

Остаются только для Подложки (и проверены): `RaidSC`, `CaravanSC`, `EmpStormSC`, `EconomyEventsSC` (убийства — только `ISubstrateNativeSC`, их в Верхнем мире нет), Капсула и Метка в `TravelEventsSC`, `SpawnDirectorSC`, радиация Подложки. Поле ядра то же: `TileEntitySettlementCoreSC.FLAGS` (F_PRIVATE, F_NO_MECHANISMS, F_NO_SPAWN, F_NO_ENDER, F_DAMAGE, F_EMP_SCREEN, F_SHOW, F_RADIATION — последний в Верхнем мире ничего не значит). Обходят события: Equal Trade TC, MFFS, AE2 Spatial IO, палочка и Ender Quarry ExU, порча TC — отсюда Достройка (§5.6).

## 5. Генерация — пакет G (ПЕ-1…ПЕ-10, ПК-27, ПК-28)

### 5.1 Размещение (`EmbassySiteSC`, ядро, написано целиком)
- `worldFits(w)`: серверный Верхний мир, тип DEFAULT или LARGE_BIOMES, структуры мира включены (на клиенте — ложь: нет сида).
- Регион `grid()` × `grid()`; `rolls(seed, rx, rz, embassyChance)`; `candidates(seed, rx, rz, grid)` — 16 точек в ± grid/4 от середины (первая — главная, 15 запасных; было 5 — интегратор), так что посольства соседних регионов ≥ grid/2 (1024) друг от друга; кандидат ближе `embassySpawnMin` к спавну (`WorldInfo.getSpawnX/Z`) пропускается; первый подходящий (`fitSite`) — посольство региона.
- `fitSite(w, x, z, guaranteed)`: сид площадки `siteSeed`, радиус `radius(seed)` (88–96), `biomeProblem` по слоям биомов на четверти разрешения (`getBiomesForGeneration(null, …)`, чанки не грузятся; мутация ванили id + 128 — по типам Forge своей основы: свои Forge только угадывает, всем SANDY): в площади (`area(r)` = r + 31 + 8 ≤ 135) ≥ 40% PLAINS / SAVANNA / SANDY и ≤ 60% FOREST, нет OCEAN, BEACH, SWAMP (RIVER можно — выравнивание её засыпает); внутри кромки (`levelRadius` − `EDGE_IN` 4: там всё срезается до пола) ≤ 25% HILLS; на кромке — от неё до площадь + 16 — не больше `EDGE_PCT` 10% HILLS или `rootHeight` ≥ `EDGE_ROOT` 0,3 и ни одного `heightVariation` ≥ `SHATTERED_VAR` 0,6 (полоса сопряжения — всего 8 блоков: холмы там стоят стеной вокруг города); в площадь + 16 нет MOUNTAIN и `rootHeight` ≥ `HIGH_ROOT` 0,8 (плато саванны и столовых гор — у Forge PLAINS / SANDY, верх 90–100 над полом не выше 72; Extreme Hills M); в + 64 нет JUNGLE, MUSHROOM; в + 192 нет MAGICAL и SPOOKY, кроме ванильного Тёмного леса. Чтение в два шага: сначала площадь + 16 (77 × 77 отсчётов — вода, горы, кромка и доли отсеивают почти всё), кольца до + 192 (165 × 165) — только для прошедшей точки. **Интегратор (2026-10-19):** первые правила (≥ 70% / ≤ 30%, в + 16 ни реки, ни холмов, Тёмный лес — SPOOKY, 5 кандидатов, 64 попытки) пропускали 0 из 9000 точек шести сидов — на мире теста не встало ни одного посольства и гарантированное «нигде»; новые пропускают ≈ 2% точек, посольство встаёт в ≈ 28% выпавших регионов (12 сидов), гарантированное — на всех 12. `LAND_RULES` (2) пишется с «нигде» — решение со старыми правилами перепроверяется. Действующая доля посольств ≈ 45% × 28% ≈ 13% регионов — TODO(balance) `embassyChance`. **Ревью (2026-10-19, `LAND_RULES` 3):** кромка, плато и мутации; замер на тех же 12 сидах (−4043695398149670526, −1234567890123, −77777, 12345, 31337, 987654321, «Российская империя», 1, 42, 2024, 8675309, 555555; регионы ± 12288 от (0, 0)): без правила кромки — 22% выпавших регионов, кромка без единого холма — 3% и гарантированное лишь на 6 сидах из 12, `EDGE_PCT` 10 — 14% (≈ 45% × 14% ≈ 6% всех регионов), точку проходят 1,6%, гарантированное — на всех 12 (замер — от (0, 0); на свежем мире теста srv4, спавн 216 252, оно встало там же, где на правилах 2: 1192 −1280, 1816 блоков). Чтение площадки — 0,34 мс против 1,07 мс у одного чтения 165²: первый расчёт региона ≈ втрое дешевле. TODO(balance) `embassyChance` — тем более. Пол — `EmbassyLayoutSC.floorY` от среднего `rootHeight`. Деревня (`villageStart`: сетка 32, разнос 8, соль 10387312, живой `MapGenVillage.villageSpawnBiomes`) не ближе площадь + 128; храм / хижина (`templeStart`, соль 14357617, биомы desert, desertHills, jungle, jungleHills, swampland) не ближе площадь + 32. Своё `Random`, мировой не трогается.
- `site(w, rx, rz)` (кэш по региону; сброс — смена сида, чисел конфига или спавна; **ревью:** сначала `made` — кандидат региона, у которого уже есть запись посольства или старт (пустой тоже), остаётся посольством региона при любом спавне, числах и правилах земли; такое место гарантированное не вытесняет), `siteAtChunk(w, cx, cz)` (дешёвый путь для `canSpawnStructureAtCoords`: регион не выпал или чанк не чанк ни одного кандидата — без чтения биомов), `siteAtCore(w, x, z)`, `near(w, x, z, reach)` (по близости), `nearest`.
- **Гарантированное** (`guaranteed(w)`, `decideGuaranteed(w)`): точка сетки в кольце 1300–2500 на неоткрытой земле (ближайшая к спавну) — она; иначе 128 чистых кандидатов кольца (`guaranteeCandidates`; было 64) — первый, чья земля подходит, ≥ grid/2 от любого посольства сетки и от любой записи посольства (ревью: и созданных при других числах), ни один чанк площади не загружен и не на диске (`explored`), нет поля игрока ближе площадь + 32 (`zoneNear` → `TileEntityFieldGeneratorSC.playerZoneNear`: коробка мастера и узлов, раздутая на `range` поля; ревью — раньше мерилось только до мастера); точка сетки в кольце — тоже без поля рядом; иначе то же в 2500–4000; иначе строка в лог. Решение пишется в реестр (мировой `extra` «E») и больше не меняется; «нигде» перепроверяется при других числах. Посольство сетки ближе grid/2 к гарантированному не ставится (изменённый конфиг).
- `explored(w, site)` / `chunkOnDisk(w, cx, cz)`: чанк загружен (`ChunkProviderServer.chunkExists`) или есть файл региона **и** чанк в нём; отсутствующий файл региона не открывается (AnvilChunkLoader создал бы пустой); ревью: `explored` смотрит каждый из ≤ 4 файлов региона следа один раз (а не `File.exists` на каждый из ~324 чанков), луч держит «не открыто» `RECHECK_TICKS` (6000). `regionFileExists(dim, cx, cz)` — для мира, который не загружен (R: открыт ли регион (0, 0) Подложки, §7.5).

### 5.2 План (`EmbassyLayoutSC`, ядро)
Рамка как у `SettlementLayoutSC`: `a` — вдоль главной улицы к главным воротам (`gateDir` по сиду), `p` — поперёк (по часовой), ядро в (0, 0); `Plan.x(a, p)`, `z(a, p)`, обратное `frame(x, z)`.
| Что | Число (в рамке) |
|---|---|
| Ядро | (0, 0), `floorY + 1` (на пьедестале) |
| Колпак, Площадь стражи, мастерская | `CAP_HALF` 3 (7 × 7), `SQUARE_HALF` 10 (21 × 21), `WORKSHOP` 11 |
| Точки Сердца | плаза (7, 0), площадь (−7, 0), Клон-капсула (0, 6), Зал посла (0, −6); 4 зарядные плиты (±8, ±8) |
| Стена | внешняя грань на r − 3 (`WALL_IN`), толщина 2, высота 8; 4 вышки высотой 12 на диагоналях (`P_TOWER0..3` — их площадки); ворота шириной 7: главные (+a) и задние (−a); `P_GATE` (r − 7, 2), `P_BACK_GATE` (−(r − 7), −2), склады `P_DEPOT` (−(r − 17), 0) |
| Вокзал | середина (маяк причала, `dock*`) — (r + 18, 0), площадка 25 × 25 (`VOKZAL_HALF` 12); зона причала 13 (`DOCK_ZONE`); Диспетчерская `P_OFFICE` (r + 12, −8) |
| Большое кольцо | плоскость поперёк улицы на a = r + 24 (`RING_BACK` 6 за маяком), середина `floorY + 3` (`RING_DY`), радиус 6 (Ø 13; низ утоплен в площадку на 3); вихрь 5 × 5 (`VORTEX_HALF` 2) — от пола; `inVortex`: плоскость ± 1, |p| ≤ 2, ноги от `floor + 1` до `floor + 6` |
| Ровная земля | диск r + 31 (`LEVEL_OUT`; дальние углы Вокзала внутри), затем 8 (`BLEND`) к своей высоте (`targetY`: smoothstep); след (`footprint`) — квадрат ± (r + 39) ≤ 135 (`REACH_MAX`) |
| Пол | `floorY(meanRoot)` = clamp(64 + round(24 × средний rootHeight), 65, 72): равнины, саванна, пустыня — 67 |
`roster(site)` (ПЖ-13): Посол, 2 Диспетчера, 2 Интенданта, по 2 Технолога, Металлурга, Газовщика, Картографа, Скупщика, Энергетик (16) + 2…6 из {Интендант, Технолог, Металлург, Скупщик, Энергетик} по сиду (всего 18–22); затем 6 Постовых, 4 Старших, Капитан, Комендант. Оружейника нет (лавки брони нет). `plan(seed, site, floorY)` ставит рамку, точки, Вокзал, кольцо, имена, след; лоты и посты — `EmbassyQuartersSC.lay` (S, один пост на запись состава по порядку). `record(plan, dim, now)` — запись реестра (размер EMBASSY, `dockZone` 13, слоты NEVER с именами); `fill(s, plan)` — для `RosterSC.restore`; `planOf(s, seed)` — план записи заново (R: кольцо, Вокзал, вихрь).

### 5.3 Структура (`MapGenEmbassySC`, G)
- `range` = 10 (след доходит до 9 чанков, старт создаётся раньше любого его чанка). Один экземпляр на Верхний мир: `EmbassySC.mapGen(world)` (новый мир — новый экземпляр; выгрузка — `EmbassySC.forget`).
- `canSpawnStructureAtCoords(cx, cz)`: `SettlementSC.embassiesGenerate() && EmbassySiteSC.siteAtChunk(worldObj, cx, cz) != null`.
- `getStructureStart(cx, cz)` (по образцу `MapGenSettlementSC`): площадка → `EmbassyLayoutSC.plan(seed, site, site.floorY)`; запись в центре (`atCentre(0, coreX, coreZ)`) есть — старт со своими кусками по ней (id записи); записи нет и `EmbassySiteSC.explored` — ПУСТОЙ старт и строка в лог (ПЕ-4); иначе `reg.reserveIds(max id стартов)`, `reg.add(EmbassyLayoutSC.record(p, 0, now))`, `EmbassyBeamSC.recordMade(world, rec)`, старт: `new ComponentEmbGroundSC(id, p)` первым, затем `EmbassyPiecesSC.pieces(id, p)`.
- Генерация — через `func_151539_a(provider, world, cx, cz, null)` в `ReplaceBiomeBlocks` и `generateStructuresInChunk(world, rand, cx, cz)` в populate; `ensureLoaded`, `startAt(w, …)`, `covers(w, …)` уже в ядре (ванильное чтение данных приватное).

### 5.4 `ReplaceBiomeBlocks` (`EmbassyWorldGenSC.onReplaceBiomeBlocks`, G; ПЕ-4, ПЕ-6)
Событие идёт на EVENT_BUS из `ChunkProviderGenerate.replaceBlocksForBiome` (конструктор с `World` — проверено по байткоду 1614; `ChunkProviderHell` шлёт то же — фильтр dim 0 и `EmbassySiteSC.worldFits`; `event.world` может быть null у старого конструктора — тогда ничего). Порядок:
1. `EmbassySC.mapGen(world).func_151539_a(event.chunkProvider, world, chunkX, chunkZ, null)` — старты в радиусе 10 чанков, раньше любого чанка своей площади, кто бы ни создал чанк (игрок пешком, мост, ракета, `/tp`).
2. Для каждого непустого старта, чей след задевает чанк (старт хранит id; план берётся из записи — `EmbassyLayoutSC.planOf` — или из кусков): колонки выравниваются **в массиве** `blockArray` / `metaArray`, индекс `(x * 16 + z) * h + y`, `h = blockArray.length / 256` (раскладка конструктора `Chunk`, сверено по байткоду): натуральная высота — верхний камень колонки в массиве; `past = levelPast(dx, dz, levelRadius)`; `target = targetY(natural, floorY, past)`; ниже `target` — камень (вода и воздух под ним — тоже камень), выше — воздух (вода тоже). `biomeArray` не трогается; ванильный проход поверхности потом кладёт траву / песок на новый верх. Только свой чанк — каскада нет.
3. Пещеры и овраги ваниль режет **после** — их засыпает кусок земли (§5.5).

### 5.5 Populate и кусок земли (G; ПЕ-6, ПЕ-7)
- `generate(...)` (IWorldGenerator, вес 1000 — после всех чужих): dim 0 → `mapGen.generateStructuresInChunk(world, random, chunkX, chunkZ)` — только окно populate.
- `ComponentEmbGroundSC` (первый кусок старта, бокс — след, y 1…255): на ровной земле (`past ≤ 0`) засыпает воздух и жидкость от `FOUNDATION` (6) под полом до пола (`biome.fillerBlock` в 3 блоках под верхом, ниже — камень), ставит `biome.topBlock` на пол, где его нет; над полом до высоты оболочки купола (половина сферы радиуса r) убирает то, что выросло или нападало (деревья соседних окон, лёд, край кратера) — внутри купола и над Вокзалом; в полосе сопряжения — только засыпка пещер до `target`. Свои поля NBT (ядро, радиусы) — G.

### 5.6 Запреты и Достройка (G; ПЕ-6, ПЕ-9)
- `Decor.onPopulate`: `PopulateChunkEvent.Populate` LAKE, LAVA, DUNGEON, ANIMALS → `Result.DENY`, если окно чанка (`chunkX * 16 + 8`…`+ 15`) задевает площадь посольства + `BAN_MARGIN` 8 (`banned` → `mapGen.covers(w, …)`); `Decor.onDecorate`: `DecorateBiomeEvent.Decorate` TREE, BIG_SHROOM, CACTUS, PUMPKIN, REED, LAKE, LILYPAD → DENY там же. **У Decorate `chunkX` / `chunkZ` — блоки** (BiomeDecorator.chunk_X), у Populate — чанки. Оба события — на TERRAIN_GEN_BUS.
- `EmbassyRepairSC.tick` (секунда ядра посольства): не чаще окна в `WINDOW_TICKS` (20); окна — по 16 × 16 (сдвиг 8, как populate) цилиндра купола и зоны Вокзала; окно — только если загружены 4 его чанка и нет игрока ближе `PLAYER_CLEAR` 32; окно — `start.generateStructure(world, rand, box)` заново (куски идемпотентны: тот же блок с той же метой — `setBlock` ничего не меняет, TE не пересоздаётся; ядро, уже стоящее, не настраивается заново). Проход по всем окнам — затем `PASS_PAUSE_TICKS` (24000) отдыха; место прохода — `extra` «G». `repair(w, s, true)` — проход сейчас (команда), игроки рядом не мешают, но только загруженные окна. **Ревью:** новый город отдыхает `FIRST_REST_TICKS` (1200), не 24000; ядро само пропало (кратер метеорита AE2 ставит воздух на всё, кроме бедрока, — с TE ядра, а Достройку вело только оно) — `EmbassyRepairSC.onWorldTick` (FML, раз в секунду, запись посольства с загруженным чанком ядра) запускает окно Сердца (`heal`, не чаще `HEAL_TICKS` 200): кусок Сердца ставит ядро и настраивает его. Порча Thaumcraft (`Thaumcraft:blockTaintFibres`, `Thaumcraft:blockTaint` — по имени; материал TC не заменяемый и не растение) — «чужое» над полом и «пустое» под ним; колонка защищённой земли с биомом Tainted Land получает свой биом (`Utils.setBiomeAt` TC — он же шлёт клиентам; нет его — массив чанка). `ComponentSettlementSC.put` не ставит тот же блок с той же метой (setBlock Forge 1614 гоняет оба прохода света и без изменения). Медная трасса, её узел и кварцевый блок в кусках посольства — отделка `BlockSettlementDecorSC` (меты 9–13: `TRACE`, `NODE` со светом узла, `QUARTZ`, `QUARTZ_CHISELED`, `QUARTZ_PILLAR`; `ComponentEmbassySC.trim`) — Достройка больше не возвращает дроп (Ender Quarry / Equal Trade + ChickenChunks были бы вечной фермой меди и кварца).

### 5.7 Команда (`CommandEmbassySC`, G; ПЕ-10)
`/scembassy list [радиус]` (по умолчанию 4000, не больше 16000): посольства сетки и гарантированное — x z, радиус, пол, расстояние, состояние (создано: id записи / не открыто / пусто — старая земля), и доля регионов с посольством — это и есть замер `embassyChance` на сиде пользователя (TODO(balance)); чисто: ни один чанк не грузится. `where` — гарантированное и ближайшее. `repair [id]` — Достройка ближайшего созданного (или по id). Строки `sc.cmd.embassy.*` (D). Уровень 2.

## 6. Город — пакет S (ПЕ-7, ПЕ-8, ПЕ-12, ПЖ-13, ПГ-21)

### 6.1 Кварталы (`EmbassyQuartersSC.lay`, S)
Один пост на запись состава, по порядку (`plan.posts.get(i)` — для `roster.get(i)`), у каждого дом (сон). Кварталы от середины наружу (предложение §3): Сердце — ядро под колпаком 7 × 7, Клон-капсула (`P_CLONE`), Зал посла (`P_HALL`: Посол); Площадь стражи 21 × 21 — Станция обслуживания брони (у `P_SQUARE`: Капитан), 4 Зарядные плиты (`P_PAD0..3`); кольцо мастерских 11 × 11 (по одной на профессию: `SettlementLayoutSC.workshopLine` / `workshopSpots` / `workshopPosts`; Энергетик — с крышей, Скупщик — склад); склады снабжения у задних ворот (`P_DEPOT`: Интенданты; ящики, мешки — витрины `CRATE`, `RACK`); оранжерея (декор, единственная зелень: листва и цветы ванили); жилые ячейки (`houseHomes`, сон); стражи: Постовые — главные и задние ворота, обход по стене, двое у Сердца ночью; Старшие — 4 вышки; Комендант — плаза; Диспетчеры — `P_OFFICE` (их дом — вокруг поста, ядро уже это делает). Все лоты — под оболочкой купола (полусфера от пола) или на ровной земле снаружи (Вокзал, улица), в площади.

### 6.2 Куски (`EmbassyPiecesSC`, `ComponentEmb*SC`, S)
По одному классу на вид лота (`EmbassyLayoutSC.LOT_*`), все наследники `ComponentEmbassySC`, имена `SCOwEmb<Имя>` в `register()`. Правила (`ComponentEmbassySC`): только окно, флаг 2, свет вручную (`put`); блоки — ванильные (камень, кирпич, стекло, шерсть, железные решётки, кварц) и мода, но ничего ценного (металл, катушки, свинцовое стекло — отделка `BlockSettlementDecorSC`, неломаемая) и **ни одного бревна или доски** в постройках (ПМ-7); каждый кусок идемпотентен (Достройка). Сердце ставит блок ядра (`SettlementBlocksSC.core`) и зовёт `TileEntitySettlementCoreSC.setup(id, radius, floorY)` только если TE ещё не того поселения. Кусок кольца ставит сегменты `showcase2SC` `GRAVITY_COIL` по окружности радиуса 6 в плоскости a = r + 24 (низ утоплен в площадку), под серединой — `EmbassyBlocksSC.ring` и `TileEntityEmbassyRingSC.setup(id)`. Вокзал — площадка 25 × 25, баки `CRYO_TANK`, Диспетчерская со столом `DISPATCH_CONSOLE`, маяк причала на середине (блок причала Подложки `SettlementBlocksSC.dock` — его маяк). Стена с вышками и воротами, улица от главных ворот до Вокзала.

### 6.3 Витрины Ф6 (`BlockShowcase2SC` 7–9, S — только вид)
Иконки и `look()` для `GRAVITY_COIL` (вид Гравикатушки моста, светится), `CRYO_TANK` (бак с указателем уровня), `DISPATCH_CONSOLE` (стол с экранами); TE витрины — как у прочих (лицо, искры раз в 20 тиков). `NAMES` и `LIGHT` — ядро, не менять. Текстуры `showcase2_<name>*.png`, генератор `tools/gen_embassy_tex.py`.

### 6.4 Луч (`EmbassyBeamSC`, `EmbassyBeamClientSC`, S; ПЕ-12)
Сервер: каждому настоящему игроку в Верхнем мире — `EmbassyNetSC.BEAMS` со списком посольств в `SEND_REACH` 2048 (созданные — их кольцо; несозданные на неоткрытой земле — точка кольца площадки; пустые старты — никогда): при входе, при переходе в Верхний мир, раз в `RESEND_TICKS` 600 если список изменился, всем — при новой записи (`recordMade`). Клиент — по образцу `SignalClientSC` (отдельный класс, Сигнал не трогать): бирюзовый столб (0x48F0DC, «Бирюза Наладчиков») от кольца до неба, виден за `BEAM_VIEW` 1024 у горизонта, без тумана, ширина растёт с расстоянием, аддитивное смешение, тест глубины (холмы закрывают); только в Верхнем мире; выход из мира или сервера — список сброшен.

## 7. Рейсы — пакет R (ПЯ-1…ПЯ-16, ПР-8)

### 7.1 Числа (`EmbassyRulesSC`, ядро, написано целиком; TODO(balance) все)
| Рейс | Откуда → куда | Уровень | База КЧ | Знакомый ×1,25 / Партнёр / Свой ×0,9 / Почётный ×0,85 | Лимит |
|---|---|---|---|---|---|
| INTRO «Ознакомительный» | посольство → «Первый причал» | Знакомый | 12 | 15 / 12 / 11 / 11 | 1 раз на игрока; в общих 2 |
| FIRST «К Первому причалу» | посольство → `first(Si-14)` | Партнёр | 24 | — / 24 / 22 / 21 | 2 в сутки (`embRide`) |
| DOCK «К известному причалу» | посольство → причал из `knownDocks` (Подложка) | Партнёр | 32 | — / 32 / 29 / 28 | тот же |
| RESCUE «Спасательный» | посольство → Метка гибели, разброс 30 | Знакомый | 20 | 25 / 20 / 18 / 17 | вне лимита: 1 на гибель, не позже 2 суток |
| HOME «Домой» | причал Подложки с Диспетчером → посольство вылета (`EmbassyTicketSC.from`) | Знакомый | 20 | 25 / 20 / 18 / 17 | в общих 2 |
| BACK «Обратная половина» | там же → посольство билета | — («Нарушитель» тоже) | оплачено | — | 3 дня; в общих 2 |
| OTHER «В другое посольство» | Город Подложки → виденное посольство | Свой | 40 | — / — / 36 / 34 | 1 в сутки (`embOther`); в общих 2 |
Цена = `TradeRulesSC.playerSide(база, уровень, сид поселения, «flight.<рейс>»)`: множитель уровня × разброс поселения ±10% (`SEED_SPREAD_PCT`), вверх. «Туда-обратно» (INTRO, FIRST, DOCK): (рейс + HOME того же поселения) × 90%, вверх (24 + 20 → 40), обратная половина — `BACK_TICKS` 3 суток. Поручительство: рейс друга по множителю поручителя × 150%, вверх; поручитель ≥ «Партнёр», друг ≥ «Чужак» и не «Враг» этого посольства, ближе 8 к Диспетчеру; 1 в сутки (`embGuar`); друг стал «Врагом» этого посольства за 3 суток — поручителю −30 (один раз). Окно: отсчёт 10 с → вихрь 20 с (`SPACE_LIFE_S`) → остывание 60 с (`COOL_S`), до 4 пассажиров. Мягкий допуск: нет ни одной части брони мода или нет Капсулы — предупреждение, второй щелчок в 5 с. `refusal(...)` — порядок проверок и коды отказов (§2.4).

### 7.2 Вкладка «Рейсы» (`FlightServerSC`, `GuiTradeSC`, `TradeClientSC`, R)
- Экран обмена Диспетчера (GUI 21; сделок у него нет): вкладка «Рейсы» (по образцу вкладки «Контракты» Старейшины): список рейсов этого места с ценой для игрока, «туда-обратно», серые недоступные с причиной (`EmbassyRulesSC.refuseKey`), осталось рейсов сегодня, обратная половина (посольство и дни), талон (ожидает окна), фаза окна и таймер, для поручительства — игроки рядом (≤ 8, их уровень); кнопки «Купить», «Туда-обратно», «За друга», «Лететь» (подтверждение мягкого допуска — та же кнопка в 5 с). Заголовок: имя и «Посол» у Старейшины посольства — `langKey(!SubstrateSC.is(world))` (весь `GuiTradeSC` — R).
- `sendState(p, t)` — `FLIGHT_STATE` (хук ядра в `TradeServerSC.open`; после каждого `FLIGHT_DO` / `FLIGHT_REFRESH` — снова). `onMessage`: только пока открыт `ContainerTradeSC` живого Диспетчера ближе 8 (как у `TradeServerSC`), никогда FakePlayer. `FLIGHT_DO` {ride, target, round, friend, confirm, go}: `refusal` заново по серверным данным; КЧ считаются по инвентарю и снимаются (помощник КЧ `TradeServerSC` — по запросу T, иначе свой); счётчики дня; талон (`EmbassyTicketSC.setTalon`), обратная половина (`setBack`), отметка ознакомительного; окно — `FlightSC.board`; `FLIGHT_RESULT` {code, ride, chips}. КЧ рейса — не «выдача КЧ» и не сделка: ни «Доверия», ни суточных 64.
- Где что продаётся: в посольстве — INTRO, FIRST, DOCK, RESCUE, «туда-обратно», поручительство; у причала Подложки (Посёлок, Город, «Первый причал» — там, где Диспетчер; у Постов его нет) — HOME, BACK; у Города ещё OTHER.

### 7.3 Окно (`FlightSC`, R; ПЯ-3)
Окно — на поселение (id), переходное. Первый оплативший запускает отсчёт; присоединиться можно в отсчёте и в вихре, пока < 4; в остывании — «Кольцо остывает» (`R_BUSY`). Посадка: в посольстве — шаг в вихрь (`EmbassyLayoutSC.inVortex` по `planOf`), у причала Подложки — на плиту причала (`BOARD_DOCK` 3 от маяка, по высоте — зона причала); переносится только настоящий игрок с талоном этого поселения, не верхом и без седока (питомцы и маунты остаются). Окно закрылось — талон остаётся до следующего окна в тот же день; талон прошлого дня — назад плательщику (`Talon.stale`: КЧ в инвентарь, иначе под ноги), проверка игрока раз в `TALON_CHECK` 100 тиков. Фаза — `TileEntityEmbassyRingSC.setPhase` (кольцо найти по точке `ring` записи; чанк не загружен — ничего); у причала Подложки — серверные частицы портала (видят оба игрока по LAN) и `FLIGHT_FX`. Без игрока окно не открывается (оплата — это и есть игрок рядом).

### 7.4 Кольцо (`TileEntityEmbassyRingSC`, `BlockEmbassyRingSC`, `RingClientSC`, R)
TE лёгкий: id посольства (`setup` — кусок кольца S), фаза и такты (пакет описания); не `TileEntityBridgeControllerSC` — чужим нельзя дать свободные координаты. Клиент: TESR — вихрь в середине кольца во время VORTEX (вид вихря моста; `client.BridgeVortexFxSC` можно звать, не менять), растущее свечение катушек в отсчёте, гаснущее в остывании; `onFx` — столб света над маяком Подложки 20 с и вспышка там, где ушёл пассажир. Блок неломаемый, иконки `embassyRing`, `embassyRing_top`, свет 9.

### 7.5 Цели и перенос (`FlightSC.carry`, R; ПЯ-5, ПЯ-15)
- FIRST / INTRO: `reg.first(Si-14)` — маяк + 1. Нет записи: если файла региона (0, 0) Подложки нет (`EmbassySiteSC.regionFileExists(dim, 0, 0)` — ложь) — точка маяка из чистого плана `SettlementLayoutSC.plan(seed, site(seed, 0, 0))` (`TARGET_FIRST_PURE`; сам перенос загрузит чанк — с ним создастся запись); регион есть, а записи нет (открыт до Ф2) — ближайший к (0, 0) причал Подложки из реестра; нет и его — `R_CHANNEL` «Канал не найден», КЧ назад.
- DOCK: причал из `knownDocks` игрока (только Подложка: `inSubstrate`), маяк + 1. RESCUE: `PlayerRec.hasDeath` в Подложке, `rescuable(...)`, точка Метки + `BridgeMathSC.scatterOffset(rand, RESCUE_SCATTER)`; `EmbassyTicketSC.setRescued(deathAt)`. HOME / BACK / OTHER: Вокзал посольства (маяк + 1).
- Перенос: целевой мир (`worldServerForDimension`, он грузится), `SafeSpotSC.find` (8; грузит чанк), `BridgeTeleportSC.teleport` (пропуск `strictEntry` выдаётся сам; `mayCross` — местных не пустит), `BridgeSoftLandSC.start`, `BridgeEchoSC.crossed(p, from, to, x, y, z, true)` (у причала отряда нет — «укрытие»; у Метки бросок остаётся — задуманный риск; обратно домой Эхо не идёт — щит). В Подложку: `SubstrateEntrySC.NBT_FROM` := маяк Вокзала посольства вылета (+1), `via` «embassy» (Капсула возврата ведёт на Вокзал), `EmbassyTicketSC.setFrom(embassyId)`. Не из события перехода (окно тикает в `ServerTickEvent`).

### 7.6 Ограничения (R; ПЯ-16)
`bridge.enabled` и `bridge.crossDimension` (`ConfigSC.bridgeEnabled`, `bridgeCrossDimension`), `SubstrateSC.active()`; не «Враг» этого места, не «Нарушитель» (кроме BACK); при `requireSignal` — своя Навигационная запись (`SubstrateSignalSC.knows`) для рейсов в Подложку; один талон за раз (`R_PENDING`).

## 8. Обмен — пакет T (ПО-23…ПО-27, ПД-7, ПЖ-11, ПЖ-12, ПЖ-14, ПЕ-11)

### 8.1 Лавка посольства (`EmbassyTradeSC.allowed`, `TradeServerSC`, T; ПО-23, ПО-24)
В посольстве открыто: все продажи Наладчикам (товар → КЧ) как в Подложке; сделки Интенданта; Капсула (`cartCapsule`: 8 КЧ, «Чужак», 2 в сутки) и Карта поселений (`cartMap`, строится от `first(Si-14)` — ядро); зарядка Энергетика (услуга); искупление у Посла. Закрыто (не показывается, `run` отказывает `R_NOT_HERE`): тантал, гафний, дейтерий, гелий, модули ПЭ-1, чипы, Крупица, Координатор, Пеленгатор руин, всё «Свой» / «Почётный» Ф3–Ф5. «Доверие» — `give(..., ceiling(s))` (+1 за сделку под потолком 599). Ни одной сделки, ни одного контракта на убийство ванильных мобов (`EconomyEventsSC` — только Подложка).

### 8.2 Интендант (T; ПО-25)
| Ключ | Даёт игрок | Получает | В сутки | «Доверие» |
|---|---|---|---|---|
| `qmBread` | 16 хлеба | 1 КЧ | 4 | +1 (сделка) |
| `qmLogs` | 32 бревна (`logWood`) | 1 КЧ | 4 | +1 |
| `qmCoal` | 16 угля | 1 КЧ | 4 | +1 |
| `qmIron` | 8 железных слитков (`ingotIron`) | 2 КЧ | 3 | +1 |
≤ 18 КЧ в сутки, в общем пределе 64; «Чужак»; масштаб уровнем и разбросом — как у продаж (`TradeRulesSC`). ПО-7: никто в мире этого не продаёт (самотест `boughtAndSold`). Рецептов и ванильных предметов на выход нет.

### 8.3 Посол: контракты (T; ПО-27)
`ContractTableSC` (ключи в конец): «Снабжение экспедиции» — `embSupplyBread` 64 хлеба / `embSupplyLogs` 48 брёвен / `embSupplyIron` 24 железа (DELIVER) → 6 КЧ, +15; `deliverTantalumCaps` (уже есть: 32 Танталовых конденсатора → 14 КЧ, +25) — и на доске Посла; «Депеша домой» `embDispatch` — новая цель контракта (сдаётся у Старейшины **любого** поселения Подложки, не у Посла, в течение 3 игровых суток; предмета нет — сдача кнопкой на вкладке контрактов того Старейшины) → 10 КЧ, +30 **сверх потолка** (сдаётся в Подложке). 3 контракта в сутки — общие с Подложкой. На доске Посла — только эти; контрактов на убийство нет. Искупление у Посла — под потолком.

### 8.4 Пеленгатор посольства (`ItemEmbassyFinderSC`, T; ПО-26)
Щелчок в Верхнем мире (сервер): направление (8 румбов, `ItemRuinFinderSC.DIRS`, `sc.finder.dir.*`), расстояние и имя ближайшего посольства в `SEARCH` 6000 (`EmbassySiteSC.nearest`; пустой старт — пропуск; созданное — по имени записи, несозданное — «без позывного»); ничего — «Тишина в эфире»; в Подложке и других мирах — «Здесь нет сигнала посольств». Не расходуется; не чаще `COOLDOWN` 40 тиков на игрока. Иконка `embassyFinder`.

### 8.5 Строка при первом входе (`EmbassyHintSC`, T; ПЕ-11)
Первый вход игрока в мир с посольствами (`scEmbHint` в постоянном NBT): через `DELAY_TICKS` 100 — «Перехвачен сигнал: Посольство „…“, ≈ N блоков на запад» (ближайшее в `SEARCH`, чаще всего гарантированное; несозданное — «без позывного»). Ничего нет — строки нет, флаг ставится. У друга по LAN — своя.

### 8.6 Жители (T; ПЖ-11, ПЖ-12, ПЖ-14)
`EntityTunerSC`: Диспетчер весь день у поста (без плазы; ночует у поста — Диспетчерская / ворота); Интендант — как ремесленник (склады); имя в чате и над головой — `langKey(embassy)` («Посол»); `SettlementClientSC` (табличка), `compat/WailaEntitySC` (строка вида), `RenderTunerSC` (скины `tuner_dispatcher`, `tuner_quartermaster`; `tools/gen_settlement_mobs_tex.py`).

## 9. Тексты, справочник, NEI, README, CHANGELOG — пакет D

### 9.1 Lang (CRLF, UTF-8; кавычки — „…“ в ru и “…” в en, никогда « » — `SelfTestSC.langQuotes`)
Ключи ядра (D вписывает сразу):
- `sc.embassy.name` «Посольство „%s“» | «Embassy “%s”»; `sc.embassy.seen` «%s: вы отмечены в журнале посольства — сюда можно вернуться рейсом» | «%s: you are in the embassy's log — flights can bring you back here».
- `sc.settle.kind.dispatcher` «Диспетчер прокола» | «Puncture Dispatcher», `.quartermaster` «Интендант» | «Quartermaster», `.ambassador` «Посол» | «Ambassador»; `sc.settle.size.embassy` «Посольство» | «Embassy».
- `tile.siliconage.showcase2.gravityCoil.name` «Гравикатушка кольца» | «Ring Gravity Coil», `.cryoTank.name` «Криобак» | «Cryo Tank», `.dispatchConsole.name` «Пульт Диспетчера» | «Dispatcher's Desk».
- `tile.siliconage.embassyRing.name` «Ядро Большого кольца» | «Great Ring Core»; `sc.tooltip.embassyRing` (+ `.details`, `.howto`).
- `item.siliconage.embassyFinder.name` «Пеленгатор посольства» | «Embassy Direction Finder»; `sc.tooltip.embassyFinder` (+ `.details` с %s — дальность, `.howto` — рецепт).
- `sc.flight.ride.<intro|first|dock|rescue|home|back|other>`; `sc.flight.refuse.<ok|off|enemy|violator|level|limit|used|noMark|noTarget|signal|chips|busy|notHere|noBack|friend|pending|confirm|channel>` (по `REFUSE_KEYS`).
- `sc.cmd.embassy.usage`.
Присылают пакеты (ожидаемые): G — `sc.cmd.embassy.list.*`, `.where.*`, `.repair.*`, `.none`; S — тексты нет (кроме табличек, если будут); R — `sc.flight.tab`, `.buy`, `.round`, `.friend`, `.go`, `.left` (%s), `.back` (%s, %s), `.talon`, `.phase.<idle|countdown|vortex|cool>` (%s), `.warn` («Там стреляют, Странник…»), `.bought` (%s, %s), `.boarding`, `.carried`, `.refund` (%s), `.channelLost`, `sc.flight.guarantee.penalty` (%s), `.guarantee.given` (%s); T — `sc.trade.offer.qm<Bread|Logs|Coal|Iron>`, `sc.contract.emb*` (+ `.desc`), `sc.contract.dispatch.turnIn`, `sc.finder.embassy` (направление, расстояние, имя), `.embassy.nameless`, `.embassy.none`, `.embassy.offworld`, `sc.embassy.hint` (имя, расстояние, направление), `.hint.nameless`.

### 9.2 Справочник (глава WORLD, `BookWorldSC`, `BookContent`)
Новые статьи в конце главы (`F6_ARTICLES`): `world.embassies` «Посольства Наладчиков» (что это, лор: внешние станции Литографов, канал в «зелёный мир с одной луной» — крючок Журнала №12, отрезанные экипажи; где — сетка, шанс, гарантированное в кольце, как найти: Пеленгатор, строка при входе, купол, луч), `world.embassyCity` «Город посольства» (купол 88–96, стена, кварталы, жители и стражи — таблица из `EmbassyLayoutSC.roster`, защита как у поселений, чего нет: налётов, бурь, караванов), `world.flights` «Большое кольцо: рейсы» (таблица §7.1 из `EmbassyRulesSC`, окно, посадка, мягкий допуск, лимит, «туда-обратно», спасательный, поручительство, Капсула на Вокзал), `world.flightsBack` «Рейсы домой» (Диспетчер у причалов Подложки, HOME / BACK / OTHER), `world.embassyTrade` «Лавка и Интендант» (что можно, чего нет, Интендант, контракты Посла, «Депеша домой», потолок 599), `world.embassyRules` (правила раздела `embassy` с числами сервера). Дописать: `world.settlements` (Диспетчер у причала), `world.trust` (потолок посольств), `world.docks` (зона 13 Вокзала, мосты садятся на маяк), `world.substrate` (раздел `embassy` строкой). G по Пеленгатору, блоку кольца, витринам Ф6 открывает статьи. Все числа — из кода.

### 9.3 NEI
Скрыть: `embassyRingSC`, меты 7–9 `showcase2SC` (весь блок и так скрыт). Показать: Пеленгатор с рецептом и «Как получить»: «рецепт; показывает ближайшее посольство в Верхнем мире». `NeiCoverageSC` не должен отмечать новое.

### 9.4 README (черновик `docs/readme-substrate-draft.md`, ru + en)
Раздел «Посольства Наладчиков»: где и как часто (`embassyGrid`, `embassyChance`, гарантированное), рейсы и цены, обратные рейсы, лимиты; только в неоткрытой земле — старые миры получают посольства дальше открытой полосы; совместимость: AE2-метеориты и порча TC — Достройка, MFFS / ExU / Spatial IO — мимо событий; ChickenChunks рядом держит город загруженным (FPS); JourneyMap — координаты из чата; как выключить (`embassies = false`).

### 9.5 CHANGELOG
Запись Ф6 в «Unreleased» (ru и en) в стиле Ф1–Ф5: «Подложка (Si-14), фаза Ф6 «Посольства Наладчиков»». Только дописывать.

## 10. Тесты — пакет D

### 10.1 Самотест `debug/SelfTestEmbassySC` (из `SelfTestSC.run`, после `SelfTestFinaleSC`)
Каждый раздел сам по себе; вывод `[SC-TEST] PASS / FAIL embassy: …`.
1. Конфиг: 20 правил; шесть новых с умолчаниями (true, 2048, 45, 1500, 1300, 2500), раздел `embassy`; `embassyRingMax < min + 256` расширяется (через рефлексию `loadEmbassies` на тестовом `Configuration` в памяти).
2. Размеры и виды: `EMBASSY` ординал 3, числа (88–96, 18–22, 6, 4, Капитан 100%, Комендант, причал), `substrate()` ложь, `SUBSTRATE_SIZES` 3, `radius(seed)` в границах на 4000 сидов; `NpcKindSC` 14, `DISPATCHER` 12, `QUARTERMASTER` 13 не стражи, `respawnTicks` 24000; `langKey(true)` у ELDER — `ambassador`, у прочих — свой; `of(13)`.
3. Реестр: `dockZone` 13 — NBT туда-обратно (ключ есть), 5 — ключа нет; `inDockZone` на 12,9 / 13,1; `reach()` включает зону + 1; `embassies()`; `isEmbassy`, `inSubstrate` (с id Подложки).
4. Потолок: 590 + 20 (сделка) → 599 (+9); 599 + 1 → 0; 800 + 10 → 0, остаётся 800; −5 проходит; без потолка — прежнее; `ceiling` посольства 599, поселения — `NO_CEILING`; дневной предел сделок соблюдается и под потолком.
5. Диспетчер: `dispatcherDue` — Посёлок / Город / «Первый» в Подложке с причалом да; Пост нет; посольство нет; другой мир нет; после `addDispatcher` — нет, второй вызов −1; прежние слоты не тронуты; имя новое и не повторяется; `dispatcherPost` — зеркало `P_GATE` по улице (оба направления оси), без `P_GATE` — ядро.
6. `EmbassySiteSC` чисто: `region` на отрицательных; доля `rolls` 45 ± 2% на 20 000 регионах трёх сидов; кандидаты в ± grid/4 от середины и детерминированы; кандидаты соседних регионов ≥ grid/2; `siteSeed`, `radius`; `villageStart` / `templeStart` = формула `World.setRandomSeed` (своя копия в тесте); `gridCell` на отрицательных (ванильное правило); `guaranteeCandidates` — `GUARANTEE_TRIES` (128) точек в [min, max] от спавна.
7. `EmbassyLayoutSC`: `floorY(0,125)` 67, `(−2)` 65, `(2)` 72; рамка: `frame(x(a, p), z(a, p))` = (a, p) во всех 4 направлениях; на 200 площадках: постов = составу, `rosterProblem` null, жителей 18–22, Посол первый, Оружейника нет; Вокзал целиком в ровном диске; кольцо в ровном диске; `inVortex` в середине кольца да, за 2 блока от плоскости нет, у пола да, на 7 выше нет; `targetY` — пол при past ≤ 0, натуральная при ≥ 8, монотонна между; след = квадрат `area`; `record`: EMBASSY, `dockZone` 13, причал = маяк Вокзала, слоты NEVER с разными именами; `fill` не трогает id и имя.
8. `EmbassyRulesSC`: `phaseAt` на 0 / 199 / 200 / 599 / 600 / 1799 / 1800; таблица цен §7.1 через `TradeRulesSC.playerSide(база, уровень, 0)`; `price` с разбросом в ±10%; `roundTripPrice` FIRST у «Партнёра» при разбросе 0 — 40; поручительство; матрица `refusal` (каждый код хотя бы раз, порядок: место → канал → «Враг» → «Нарушитель» (BACK — нет) → уровень → Запись → талон → ознакомительный → лимиты → цель); `rescuable` (свежая, через 2 суток + 1, та же уже спасённая); `backValid`; `guaranteeBroken`; `softWarning`; `confirmed`.
9. `EmbassyTicketSC`: все поля туда-обратно через NBT реестра (`PlayerRec.write/read` — через `SettlementRegistrySC` в памяти); `see` дважды — один раз; `Talon.stale`.
10. Тела пакетов (по фиксированным именам): G — помощник выравнивания колонки (синтетический массив: камень до 80 при полу 67 → камень до 67, воздух выше; вода над 60 → камень до 67); S — кварталы на 60 площадках: каждый пост жителя, кроме Диспетчеров, внутри купола − 2, Диспетчеры в зоне причала, посты стражей внутри стены или на вышках, лоты не пересекаются, все внутри площади, лот кольца на a = r + 24; T — `EmbassyTradeSC.allowed`: в посольстве нет ни одной сделки, отдающей тантал, гафний, дейтерий, гелий, модуль ПЭ-1, чип, Крупицу, Координатор, Пеленгатор руин; сделки Интенданта есть и только в посольстве; Капсула и Карта есть; ПО-7 (`boughtAndSold` ложь для хлеба, брёвен, угля, железа); ключи контрактов Посла; R — `FlightServerSC` состояние на подставных записях (по желанию).
11. Тексты: все ключи §9.1 и отчётов пакетов в обоих lang с теми же аргументами, без кириллицы и « » в en_US, без « » в ru; виды, размер, меты витрин — с текстами.
12. Справочник: статьи `F6_ARTICLES` строятся без сырых ключей; G новых предметов и блоков открывает статьи; сквозная сверка Ф1–Ф6.

### 10.2 Мировой тест `debug/WorldTestEmbassySC` (`-Dsc.worldtest=true`, после `WorldTestFinaleSC` — D добавляет ему флаг `finished` и ждёт его)
Вывод `[SC-WORLDTEST] PASS / FAIL / SKIP / INFO embassy: …`. SKIP, если `!embassiesGenerate()` или `!worldFits(overworld)`. Чанки — тикетом, изменённое возвращается, сущности удаляются, FakePlayer в мир не спавнится.
1. Регистрация: `embassyRingSC` и TE, `embassyFinder` и его рецепт, имена `SCOwEmbassy`, `SCOwEmbPart`, `SCOwEmbGround` и куски S.
2. Гарантированное: решение записано в реестре («E»), расстояние до спавна в кольце, `landProblem` null; `siteAtChunk` его чанка отвечает им же.
3. Генерация без каскада: счётчик `ChunkEvent.Load` до и после; чанки площади гарантированного создаются (`loadChunk`) в порядке «сначала середина» (прибытие телепортом в центр будущего посольства) — старт создан в первом же чанке, непустой; затем populate всех окон — ни одного загруженного чанка за «площадь + окно + 1» сверх запрошенных; полупостроенного старта нет (`startAt` непустой, все куски окна стоят).
4. Город: запись (EMBASSY, `dockZone` 13, слотов 30–34, точки `ring`, `vokzal`, `office`); пол на `floorY` в 64 колонках ровного диска (верх — пол или постройка), полоса сопряжения монотонна; ни одного бревна / доски / листвы вне оранжереи в цилиндре; ядро и его TE с `settlementId`; TE кольца с id; маяк Вокзала = причал записи; Вокзал вне купола.
5. Старая земля: посольство сетки, чей чанк площади создан при выключенных `embassies` (правило выкл. на время одного чанка) — пустой старт, записи нет.
6. Защита (`governs(0)`): `protecting` внутри — посольство; взрыв в цилиндре и в зоне Вокзала — блоки целы; `CheckSpawn` зомби в цилиндре — DENY; чужое поле поверх — `fieldOverlapOwner` = «[Поселение-N]»; конец моста в цилиндре — отказ, у маяка — нет.
7. Состав: секунды ядра — жители появляются (≤ 4 за тик) на построенных постах; Посол — ELDER; два Диспетчера у Диспетчерской, их дом — вокруг поста; житель посольства, перенесённый в Подложку, — `setDead` (`exiled`); житель Подложки в Верхнем мире — тоже.
8. Диспетчер в Подложке: запись Посёлка с причалом без Диспетчера — после секунды ядра слот в конце (NEVER, `F6Disp`), прежние слоты те же; Пост — без.
9. Рейсы (помощники R по фиксированным именам, «игрок» — не заспавненный тестовый): отказы по уровню, «Враг», лимит 3-го рейса; INTRO у «Знакомого» — цена по `price`, талон, отметка ознакомительного; окно: фазы по тактам, вход в вихрь (`inVortex`) в фазе VORTEX — перенос в цель (точка FIRST: запись или чистый план); талон прошлого дня — назад; HOME из Подложки к посольству вылета; «туда-обратно»: обратная половина 3 дня, четвёртый день — `R_NO_BACK`; RESCUE: свежая Метка — да, через 2 суток — нет, второй раз — нет; поручительство и штраф −30; мягкий допуск — предупреждение, второй FLIGHT_DO с confirm — проходит. Настоящий межмировой перенос живого игрока — только в игре.
10. Обмен (T): у Картографа посольства Капсула есть, Пеленгатора руин нет; у Технолога посольства продажа пластин есть, покупка GaAs нет; Интендант — 4 хлеба-сделки, пятая `R_LIMIT`; потолок: «Доверие» 590 + контракт (+15) → 599; «Депеша» взята у Посла, сдана Старейшине Подложки — +30 сверх 599.
11. Пеленгатор: в Верхнем мире — направление на гарантированное; в Подложке — «нет сигнала».
12. Достройка: блок стены снят `setBlockToAir` (мимо событий) — `repair(force)` возвращает его; TE ядра не продублирован; окно с игроком ближе 32 — без `force` не трогается.
13. Запреты: в окне площади нет ванильных деревьев, озёр, данжей (по результату генерации).
14. Регресс: прочие мировые тесты — как раньше (по логу).

### 10.3 Только в игре (после пакетов)
Вид купола 88–96, стены, вышек, кварталов, Вокзала, кольца и вихря; луч на горизонте днём и ночью; FPS у посольства (купол 96 и ≈ 34 существа) вдвоём по LAN; настоящие рейсы туда и обратно вдвоём в одном окне, мягкая посадка, Эхо у Метки, Капсула на Вокзал; гарантированное на сиде пользователя («Российская империя»: спавн 162/425, открыта полоса x −1024…1536, z −512…2560 — посольство встанет западнее или восточнее), плотность `/scembassy list 4000`; AE2-метеорит, порча TC, ульи Forestry / Magic Bees, каучук IC2 и руды GC / IndustrialUpgrade / TransformersMod у посольства (`-Dsc.packcheck`); ChickenChunks рядом; JourneyMap и координаты из чата.

## 11. Пакеты (файлы не пересекаются)

Общие правила владения — как в Ф1–Ф5: каждый существующий файл правит ровно один пакет или координатор; новые файлы создаёт только их пакет (новые имена — с префиксом своей темы); нужна правка чужого файла — в отчёт координатору (файл, место, текст). Заготовки ядра (§0) принадлежат пакетам, указанным в их Javadoc.

### G — генерация: старты, выравнивание, populate, запреты, Достройка, команда (Ф6.2)
- **Существующие:** заготовки `embassy/MapGenEmbassySC.java` (тела `canSpawnStructureAtCoords`, `getStructureStart`; фиксированное не менять), `embassy/EmbassyWorldGenSC.java`, `embassy/ComponentEmbGroundSC.java`, `embassy/EmbassyRepairSC.java`, `embassy/CommandEmbassySC.java`.
- **Новые:** `embassy/EmbassyTerrainSC.java` (выравнивание колонки массива — чистый помощник для самотеста), `embassy/EmbassyGen*SC.java` (по необходимости).
- **Зависит от:** ядро (`EmbassySiteSC`, `EmbassyLayoutSC`, `EmbassySC.mapGen`), S (`EmbassyPiecesSC.pieces`, `EmbassyBeamSC.recordMade` — сигнатуры есть).
- **Не трогает:** `EmbassySiteSC`, `EmbassyLayoutSC`, `EmbassySC` (ядро), `TileEntitySettlementCoreSC` (ядро уже зовёт `EmbassyRepairSC.tick`), `ChunkProviderSubstrateSC`, `MapGenSettlementSC`, куски S.

### S — город: кварталы, куски, витрины Ф6, луч (Ф6.3)
- **Существующие:** заготовки `embassy/EmbassyQuartersSC.java`, `embassy/EmbassyPiecesSC.java`, `embassy/EmbassyBeamSC.java`, `embassy/client/EmbassyBeamClientSC.java`; `block/BlockShowcase2SC.java` (только вид мет 7–9: иконки, `look`, `registerBlockIcons`; `NAMES` / `LIGHT` — ядро), `tileentity/TileEntityShowcaseSC.java` (частицы новых мет, по необходимости).
- **Новые:** `embassy/ComponentEmb*SC.java` («SCOwEmb*», кроме «Part» и «Ground»), `assets/siliconage/textures/blocks/showcase2_gravityCoil*.png`, `showcase2_cryoTank*.png`, `showcase2_dispatchConsole*.png`, `tools/gen_embassy_tex.py`.
- **Зависит от:** ядро (`EmbassyLayoutSC`, `ComponentEmbassySC`, `EmbassyBlocksSC.ring`, `TileEntityEmbassyRingSC.setup`, `TileEntitySettlementCoreSC.setup`), `SettlementLayoutSC` (помощники мастерских — только читать).
- **Не трогает:** `SettlementLayoutSC`, `ComponentSettlementSC` и куски Подложки, `lore/client/SignalClientSC` (луч — свой класс), `MapGenEmbassySC` (G).

### R — рейсы туда и обратно (Ф6.4)
- **Существующие:** заготовки `embassy/FlightServerSC.java`, `embassy/FlightSC.java`, `tileentity/TileEntityEmbassyRingSC.java` (только R-часть: отсчёт клиента, видимое; фиксированное — §0), `block/BlockEmbassyRingSC.java` (иконки, свет, искры), `block/ItemBlockEmbassyRingSC.java` (не менять), `embassy/client/RingClientSC.java`; `settle/client/GuiTradeSC.java` (вкладка «Рейсы», «Посол» в заголовке), `settle/client/TradeClientSC.java` (хранение `FLIGHT_STATE` / `FLIGHT_RESULT`), `inventory/ContainerTradeSC.java` (по необходимости).
- **Новые:** `embassy/FlightRun*SC.java`, `embassy/FlightTargetsSC.java` (по необходимости), `embassy/client/RenderEmbassyRingSC.java` (TESR), `assets/siliconage/textures/blocks/embassyRing*.png`, `tools/gen_embassy_ring_tex.py`.
- **Зависит от:** ядро (`EmbassyRulesSC`, `EmbassyTicketSC`, `EmbassyLayoutSC.planOf` / `inVortex`, `EmbassySiteSC.regionFileExists`, `SettleNetSC`, `EmbassyNetSC`), мост (`BridgeTeleportSC`, `BridgeSoftLandSC`, `BridgeEchoSC`, `SafeSpotSC` — только звать), T (помощник КЧ `TradeServerSC` — запросом, или свой).
- **Не трогает:** `TradeServerSC`, `TradeTableSC` (T), `RosterSC`, `SettleNetSC` (ядро), `TravelEventsSC`, `BridgeTeleportSC`, `BridgeEchoSC`, `SubstrateEntrySC`, `TileEntityBridgeControllerSC`, `client/BridgeVortexFxSC`.

### T — обмен: лавка посольства, Интендант, Посол, Пеленгатор, жители (Ф6.5, обмен)
- **Существующие:** `settle/trade/TradeTableSC.java`, `settle/trade/TradeServerSC.java` (хук координатора в `open` не трогать), `settle/trade/ContractTableSC.java`, `settle/trade/TradeRulesSC.java` и `TradeOfferSC.java` (по необходимости), `settle/npc/EntityTunerSC.java`, `settle/client/SettlementClientSC.java`, `settle/client/RenderTunerSC.java`, `compat/WailaEntitySC.java` (только строка вида жителя), заготовки `embassy/EmbassyTradeSC.java`, `embassy/ItemEmbassyFinderSC.java`, `embassy/EmbassyHintSC.java`; `tools/gen_settlement_mobs_tex.py`.
- **Новые:** `assets/siliconage/textures/entity/tuner_dispatcher.png`, `tuner_quartermaster.png`, `textures/items/embassyFinder.png`, `tools/gen_embassy_items_tex.py` (по необходимости).
- **Зависит от:** ядро (`ReputationSC.give(…, ceiling)`, `EmbassySiteSC.nearest`, `SettlementSC.isEmbassy`).
- **Не трогает:** `GuiTradeSC`, `TradeClientSC` (R), `RosterSC`, `NpcKindSC`, `ReputationSC` (ядро), `EconomyEventsSC` (остаётся только для Подложки).

### D — тексты, справочник, NEI, тесты, README, CHANGELOG
- **Существующие:** `assets/siliconage/lang/ru_RU.lang`, `en_US.lang` (CRLF), `manual/BookWorldSC.java`, `manual/BookContent.java`, `nei/NEISiliconAgeConfig.java`, `nei/ObtainHandlerSC.java`, `nei/NeiCoverageSC.java`, `debug/SelfTestSubstrateSC.java`, `debug/SelfTestSettlementSC.java`, `debug/SelfTestCombatSC.java`, `debug/SelfTestHazardSC.java`, `debug/SelfTestFinaleSC.java`, `debug/WorldTestSubstrateSC.java`, `debug/WorldTestSettlementSC.java`, `debug/WorldTestCombatSC.java`, `debug/WorldTestArmourerSC.java`, `debug/WorldTestHazardSC.java`, `debug/WorldTestWildSC.java`, `debug/WorldTestFinaleSC.java` (только честные поправки и флаг `finished`), заготовки `debug/SelfTestEmbassySC.java`, `debug/WorldTestEmbassySC.java`; `docs/readme-substrate-draft.md`, `CHANGELOG.md` (только запись Ф6 в «Unreleased»).
- **Не трогает:** `debug/SelfTestEmpSC.java`, `debug/WorldTestEmpSC.java` (регресс ПЭ-1), `debug/SelfTestSC.java` (вызов уже есть).
- **Порядок:** ключи §9.1 и каркас статей — сразу; числа и тесты тел G / S / R / T — после пакетов; в конце собрать ключи из отчётов; последним — сквозная сверка Ф1–Ф6.

### Без владельца в Ф6
Файлы Ф1–Ф5, которые Ф6 не трогает: `settle/*` кроме перечисленных (в т. ч. `SettlementProtectSC`, `GuardAlertSC`, `ViolationRulesSC`), `settle/npc/*` кроме `EntityTunerSC` (T), `settle/npc/ai/*` (в т. ч. `NpcLinkSC` — правка ядра сделана), `settle/gen/*`, `settle/travel/*` (`ItemSettlementMapSC` — правка ядра сделана), `settle/caravan/*`, `hazard/*`, `ruin/*`, `mob/*`, `dim/*` кроме ядра, `bridge/*`, `lore/*`, `arena/*`, `finale/*`. Нужна правка — запрос координатору.

### Только координатор
`SCMod.java`, `util/ConfigSC.java`, `init/ModBlocks.java`, `init/ModItems.java`, `init/ModCreativeTab.java`, `proxy/ClientProxy.java`, `proxy/CommonProxy.java`, `handler/GuiHandlerSC.java`, `handler/ConfigSyncSC.java`, `handler/ArmorNetSC.java`, `CommonEventHandler.java`, `ShieldEventHandler.java`, `tileentity/TileEntityFieldGeneratorSC.java`, `tileentity/TileEntitySettlementCoreSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `dim/SubstrateConfigSC.java`, `dim/SubstrateSC.java`, `dim/SubstrateNetSC.java`, `dim/SubstrateEntrySC.java`, `settle/SettlementSC.java`, `settle/SettlementSizeSC.java`, `settle/NpcKindSC.java`, `settle/SettlementRegistrySC.java`, `settle/ReputationSC.java`, `settle/RosterSC.java`, `settle/SettleNetSC.java`, `settle/SettlementCompatSC.java`, `settle/gen/SettlementLayoutSC.java`, `block/BlockShowcase2SC.java` (`NAMES`, `LIGHT`), `embassy/EmbassySC.java`, `embassy/EmbassySiteSC.java`, `embassy/EmbassyLayoutSC.java`, `embassy/EmbassyRulesSC.java`, `embassy/EmbassyTicketSC.java`, `embassy/EmbassyNetSC.java`, `embassy/client/EmbassyClientSC.java`, `embassy/EmbassyBlocksSC.java`, `embassy/EmbassyItemsSC.java`, `embassy/EmbassyRecipesSC.java`, `embassy/StructureEmbassyStartSC.java`, `embassy/ComponentEmbassySC.java`, `debug/SelfTestSC.java`, `docs/plan-substrate*.md`, `README.md`.

## 12. Жёсткие правила для всех
1. Аддон Vehicles не трогать. Файлы не удалять. Не коммитить.
2. Код брони игрока и ПЭ-1 между игроками не трогать; `SelfTestEmpSC` и `WorldTestEmpSC` — зелёные без правок.
3. Клиентский код — только в client-пакетах (`embassy/client`, `settle/client`); общий код не зовёт `@SideOnly(CLIENT)`-методы (`getItemInUse` и т. п.); FakePlayer пакетов не получает, не летает, не получает талонов и строки при входе, не платит.
4. Мир посольств — только серверный Верхний мир: `SettlementSC.governs`, `EmbassySiteSC.worldFits`; мир Подложки — `SubstrateSC.is` / `isDim`.
5. Мультиплеер (LAN вдвоём): всё решает сервер; состояние игроков — по UUID (рейсы и талоны — `PlayerRec.extra` «R», счётчики — `globalCounts`, строка — постоянный NBT); двое в одном окне со своими целями; луч — каждому клиенту; поручительство — только за игрока рядом.
6. Генерация — без каскада: старты — в `ReplaceBiomeBlocks` (без блоков), выравнивание — только массивы своего чанка, куски — только окно populate; чтение биомов — слои `WorldChunkManager`, никогда чанки; проверка «открыто» не создаёт файлов регионов; земля созданных чанков не меняется, кроме Достройки (только цилиндр и Вокзал, только загруженные окна без игрока рядом, только отличия).
7. В постройках посольства нет брёвен и досок (кроме оранжереи — только листва и цветы) и нет ценных блоков; всё неломаемое — отделка и витрины мода.
8. Ничего не моделируется без игрока: окна рейсов — только по оплате; Достройка — только в загруженных окнах; луч — только игрокам в Верхнем мире.
9. Каждый новый блок, мета и предмет — сразу с подсказкой TooltipSC (кратко, Shift, Ctrl) и строкой NEI (D).
10. Рецепты — только ванильные предметы и предметы мода (в Ф6 — один рецепт ядра).
11. Тексты добавляет только D; остальные присылают `ключ=ru | en`. Кавычки — „…“ / “…”, никогда « ». Тон — как в остальном UI мода.
12. Реестровые имена, меты, виды жителей, ключи NBT и `extra`, имена MapGenStructureIO, порядок перечислений, виды сообщений — фиксированы (§2), только дописываются.
13. Производительность: `canSpawnStructureAtCoords` — без чтения биомов, кроме чанка кандидата; кэш площадок; запреты — `covers` по стартам; Достройка — не чаще окна в секунду; окна рейсов — только существующие; лучи — один проход на игрока раз в 600 тиков.
14. Конфликты с модами сборки (предложение §5, §12): провайдер Верхнего мира не меняется (GC и GS не задеты); событие — только у `ChunkProviderGenerate` dim 0; наш генератор — последний (1000); в чёрном списке AE2 Spatial — ядро, витрины, кольцо; новых сущностей, биомов и измерений нет.

## 13. Проверка Ф6 в реальной сборке
По §10.3 вместе с проверкой Ф1–Ф5 (`docs/plan-substrate.md` §10 и техпланы). Сборка пользователя: TLauncher Forge, `-Dsc.packcheck` (память «Silicon Age pack check»), его javaw не закрывать. Мир «Российская империя» (тип `default`, MapFeatures вкл.): гарантированное посольство встанет вне открытой полосы. LAN вдвоём: рейс в одном окне, обратный рейс, поручительство, луч у обоих.

## 14. Неясно / решено координатором
- **Конфиг — в разделе `embassy` того же холдера** (`SubstrateConfigSC`), все шесть ключей — правила (публичные статические): клиент видит числа сервера в справочнике. Число правил 14 → 20 — честные поправки самотестов сделаны ядром (§0.1). Кольцо гарантированного — два ключа `embassyRingMin` / `embassyRingMax`.
- **Структуры мира выключены — посольств нет** (`worldFits`: как у ванильных структур; в предложении не сказано).
- **Талон и билет — в `PlayerRec.extra` «R»**, не в `LocalRec.extra` посольства: обратный рейс в Подложке и возврат на рассвете должны найти его, не зная поселения. «Посольство вылета» — `from` там же.
- **Обратная половина возит и «Нарушителя»** (оплачено заранее; отказывает только «Враг» этого причала). Остальные рейсы — не «Нарушителю» (предложение §6 «Условия»).
- **«Знакомые посольства» для OTHER** — посещённые: `RosterSC.visitors` отмечает посольство при входе в пояс (`EmbassyTicketSC.see`, строка `sc.embassy.seen`). OTHER — только из Города Подложки (таблица предложения), «Свой», 40, 1 в сутки, и в общих 2.
- **Ознакомительный рейс считается в общих 2** (это рейс туда); спасательный — вне лимита (предложение).
- **Посадка:** в посольстве — шаг в вихрь кольца (5 × 5 от пола; кольцо Ø 13 утоплено в площадку на 3, середина на 3 над полом); у причала Подложки колец нет — плита причала (3 от маяка) во время «канала» 20 с. Не прошёл — талон ждёт следующего окна того же дня.
- **Маяк причала посольства — середина Вокзала** (r + 18), кольцо — на 6 дальше, Диспетчерская — в зоне 13. Углы площадки 25 × 25 вне зоны 13 (до 17) не защищены зоной — это декор; кольцо, Диспетчерская и баки — в зоне.
- **Диспетчеры посольства живут на Вокзале** (вне купола): `NpcLinkSC.setHome` даёт дом вокруг поста (8), если пост дальше купола − 2; весь день у поста (T, `EntityTunerSC`). Диспетчер Подложки стоит **внутри** ворот (по ту сторону улицы от `P_GATE`), посадка — на плите причала.
- **Диспетчер у причалов Подложки — без повышения `VERSION`**: слот дописывается в конец по признаку «нет ни одного DISPATCHER» (идемпотентно, переживает `renew`); новые поселения получают его так же — в первую секунду ядра. Самотесты Ф3 (`upgrade`) не меняются.
- **Размер `EMBASSY` в `SettlementSizeSC`** (как в предложении) — таблицы налётов и наград Подложки остаются длиной 3 (`SUBSTRATE_SIZES`); код, индексирующий их, обрезает индекс (проверено).
- **Команда — `/scembassy`** (псевдоним `/scemb`) по образцу `/scbridge`, `/scenergy`, а не `/sc embassy` из предложения: общего `/sc` в моде нет, имя «sc» могло бы столкнуться с чужим.
- **Пеленгатор не расходуется** (компас и два транзистора — дёшево; в предложении «по образцу Пеленгатора руин» — без числа зарядов), откат 2 с.
- **Луч** виден за 1024 у горизонта; сервер шлёт и несозданные посольства на неоткрытой земле (иначе луч появлялся бы, только когда город уже виден). Пустые старты — никогда.
- **Плата за рейс — не сделка**: «Доверия» не даёт и в 64 КЧ выдачи не входит; КЧ просто снимаются.
- **Поиск гарантированного — при загрузке Верхнего мира** (G) или при первом вопросе: до любого чанка мира; решение не меняется; «нигде» с теми же числами не перепроверяется.
- **Достройка: проход, затем 20 минут отдыха** (в предложении — «медленный тик, не чаще окна в секунду» без паузы): постоянный обход 169 окон держал бы TPS без пользы.
- **«Подпитка канала» (Э5, 250 млн EU)** — в предложении «по желанию»: в Ф6 не входит.
- **Плотность** (45%) — TODO(balance): после генерации у пользователя `/scembassy list 4000`.
- **Ревью Ф6 (2026-10-19):** земля — кромка, плато, мутации (`LAND_RULES` 3, §5.1); созданные посольства держатся при смене спавна и чисел (`made`, Пеленгатор слышит записи, восстановление записи — по старту); поле игрока у неоткрытой площадки — пустой старт; рейсы: возврат КЧ мёртвому — в долг, сорванный перенос возвращает дневной рейс (откат счётчиков дня талона), поручительство — только за друга с открытым экраном того же Диспетчера, друг может отказаться (`FLIGHT_DO decline`, кнопка «Отказаться» вместо «Купить»), просроченный талон отсутствующего друга возвращается поручителю при его осмотре; экран поручителя обновляет список друзей раз в 2 с; Достройка — §5.6; подсказка витрин 7–9 своя (`sc.tooltip.showcase.embassy.howto`); падежи справочника.
- **Интеграция Ф6 (2026-10-19, интегратор):** земля ПЕ-3 ослаблена (§5.1: на прежних правилах не вставало ни одного посольства), `CANDIDATES` 16, `GUARANTEE_TRIES` 128, `LAND_RULES` 2; Постовые посольства обходят город по кольцевой дороге внутри стены (`EntityAIWardenPatrolSC.waypoint`: `EmbassyQuartersSC.innerFace` − `ROAD_GAP` − 0,5 − 0…`ROAD_W` − 1), а не снаружи купола; `LOT_ENERGY` 13 записан в `EmbassyLayoutSC.LOT_*`; контракты Посла берутся с «Чужака» (решение T, `ContractTableSC.takeLevel`; экран читает `cLevel`), якорного контракта в посольстве нет (`noAnchor`), значок «Депеши» — бумага; КЧ рейсов — помощники `TradeServerSC.chipsHeld / takeChips / giveChips`; справочник берёт контракты Посла из `embassyPool()`; таблицы налётов справочника — только `SUBSTRATE_SIZES`; `EmbassySiteSC.guaranteed` не решает, пока новый мир ищет спавн. Мировой тест дополнен: второй проход Достройки по целому городу не ставит ни одного блока (кусок земли и куски города согласны), катушки кольца, криобаки, столы Диспетчеров, место у каждого поста, луч; каскад считается по коду мода (на свежем мире единственный лишний чанк — каучуковое дерево IC2, `WorldGenRubTree`), спаунер пещерного паука глубоко под городом (заброшенная шахта) — не запрет ПЕ-6.
