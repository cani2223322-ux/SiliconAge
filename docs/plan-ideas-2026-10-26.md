# Silicon Age — план работ по предложению 2026-10-26, вариант ★ (15 пунктов)

Источник: `docs/idea-improvements-2026-10-26.md`. Решение пользователя (2026-10-26): «ПД-1…8 все звёздочки. ОМ 1-7 делай все звёздочки. Остальное пока не трогай». Значит, делаются ровно варианты ★ (А) пунктов ПД-1 … ПД-8 и ОМ-1 … ОМ-7. **УД-1 … УД-8 и СВ-1 … СВ-7 не делаются**, раздел «Что не вошло» — тоже (Кольцо Литографов, Гравитационный ускоритель, ЭМИ против техники, аспекты Thaumcraft, жидкости мода как топливо, настройки в игре, P2P AE2, «Доверие» обоим в звене). Варианты Б/В не делаются: ПД-1 В «Перевозка», ПД-2 Б (общая доска и HUD), ПД-5 Б/В (только одна часть), ПД-6 Б (устройства Литографов), ПД-7 Б (быстрые переходы), ОМ-1 Б (запрет модуля)/В (таблицы других измерений), ОМ-2 В (бездефектная подложка), ОМ-5 Б/В — вне плана.

Для каждого пункта: что менять, где, числа (открытые — `TODO(balance)`), новое (id, NBT, конфиг), тексты (ключ = ru | en), справочник, проверки, файлы. Пути — от `src/main/java/com/sc/`, если не сказано иначе.

## Общие правила для исполнителей

- Minecraft 1.7.10, Forge 10.13.4.1614, исходники уровня Java 6: без лямбд, `<>`, try-with-resources и `switch` по строкам; имена MCP. Стиль — как в соседнем коде (английские комментарии и javadoc, коды пунктов в комментариях: `ПД-3`, `ОМ-5`, `TODO(balance)`).
- Никогда не вызывать методы `@SideOnly(CLIENT)` из общего/серверного кода; FakePlayer пакетов не получает (не слать ему ничего, не давать ему наград, не считать защитником).
- Аддон `C:/Users/Aleksandr/Documents/MineMod/SiliconAgeVehicles` не трогать. Файлы не удалять. Папку `.minecraft` и `javaw` пользователя не трогать. Не коммитить. `runServer` не запускать.
- lang-файлы `ru_RU.lang` / `en_US.lang` — CRLF, UTF-8; кавычки в значениях: ru „…“, en “…”; никаких символов расширенной таблицы FontRenderer (« » × ± · ° — вместо них x, +/-, •, ˚, тире — « - »); одинаковое число и порядок `%s` в ru и en (SelfTestSC.langQuotes / langFontTable). Ключи присылаются списком «ключ = ru | en» — в lang-файлы их вносит тот, кто сводит работу.
- Каждый новый предмет/блок: подсказка TooltipSC сразу (краткая строка всегда, описание за Shift, «как получить/управление» за Ctrl — `TooltipSC.more` / `wrap`), текстура (свой скрипт Python в `tools/`, в стиле мода — палитры и приёмы соседних `gen_*_tex.py`, превью `*_preview.png`), запись NEI «Как получить» (`ObtainHandlerSC.ideaPage`) или скрытие в NEI (`NEISiliconAgeConfig.hideIdeaBlocks`), упоминание в справочнике.
- Рецепты — только ванильные предметы и предметы мода. Новые рецепты — только в своём методе `init/ModRecipesIdeasSC` (pd6/om2/om3/om4/om5/om7).
- NBT/meta/enum/id сущностей — только дописывать; отсутствующий ключ NBT читается разумным значением по умолчанию; старые миры загружаются.
- Новые ключи конфига — уже добавлены координатором (см. ниже); новых не заводить, без нужды — через координатора.
- Мультиплеер (LAN, двое): у каждого игрока свои записи по UUID; на клиенте UUID локального игрока может не совпадать с серверным — решения принимает сервер, клиент получает ответ; ничего клиентского не вызывать на сервере.
- Генерация мира — без каскада: строить только в окне populate (MapGenStructure-старт, все куски в пределах 8 чанков от чанка старта), «старая» земля (чанк площади уже на диске) — пустой старт (`StructureRuinStartSC.explored`).
- Сборка: `T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd C:/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q` («Read timed out»/404 — не ошибка). Проверка реобфускации: `unzip -p build/libs/SiliconAge-0.2.3.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` = 1.
- Резервная копия до правок есть: `C:/Users/Aleksandr/Documents/MineMod/backups/2026-10-26_1204_before-ideas-pd-om/` (jar, src, build.gradle).
- CHANGELOG.md («Unreleased»), SESSIONS.md и lang-файлы ведёт тот, кто сводит работу; в списках файлов ниже их нет.
- Свои самотесты — только в своём методе `debug/SelfTestIdeasSC` (pd1 … om7); свой мировой тест — свой новый класс `debug/WorldTest<…>SC` и одна строка в своём слоте `debug/WorldTestIdeasSC.register()`. Место мировых тестов: Верхний мир — X = 4000 + 100·k (k — номер пункта по порядку: ПД-1 = 1 … ПД-8 = 8, ОМ-1 = 9 … ОМ-7 = 15), Y 200, Z 0; Подложка (DIM-1414) — регион X = 20000 + 1024·k, Z = 20000 (свои ячейки, свой перекрёсток).

## Что уже сделал координатор (общие файлы — исполнителям их не править, кроме указанного в пунктах)

Сборка после правок прошла (`gradle clean build --offline -q`), проверка реобфускации = 1.

- **Конфиг** `dim/SubstrateConfigSC.java` (раздел `dimension`, все — правила: сервер шлёт их клиентам сам, SubstrateNetSC): `squads` (ПД-2), `raidAlarms` (ПД-3), `wanted` (ПД-4), `gatherers` (ПД-5), `breachProtocols` (ПД-6), `relayPosts` (ПД-7), `tunnels` (ПД-8) — все `true`, комментарии en/ru. Клиентская (не правило, приватное поле, как `customSky`): `ambientLife` (ПД-5, `SubstrateConfigSC.ambientLife()`). Самотесты под 35 правил: SelfTestCombatSC, SelfTestEmbassySC, SelfTestFinaleSC (+ проверка, что `ambientLife` не правило), SelfTestHazardSC, список RULES в SelfTestSubstrateSC. `ConfigSC`/`ConfigSyncSC` не менялись (новых общих ключей нет).
- **Сущности** `mob/MobEntitiesSC.java`: `ID_WANTED = 26` («wantedRenegade», `mob/EntityWantedSC` extends EntityTrooperSC, DW 26, NBT `scWanted`), `ID_GATHERER = 27` («gatherer», `settle/npc/EntityGathererSC` extends EntityTunerSC, DW 27, NBT `scGatherer`); NAMES, EGG_COLORS, `hasEgg` (у обоих нет яйца). `mob/SpawnRulesSC.java`: все 8 таблиц по id дописаны нулями (26, 27). Самотесты пула (28 имён): SelfTestCombatSC, SelfTestFinaleSC, SelfTestHazardSC.
- **Предметы** `init/IdeaItemsSC.java` (вызов в конце `ModItems.init`): «wantedToken» (`mob/ItemWantedTokenSC`, ПД-4), «breachProtocol» (`arena/ItemBreachProtocolSC`, меты 0..4 = `arena/BreachProtocolsSC`, ПД-6), «nanitePaste» (`item/ItemNanitePasteSC` extends ItemSimpleSC, в `ModItems.COMPONENTS`, ОМ-2). Помощники `IdeaItemsSC.wantedToken(n)`, `breachProtocol(p)`, `nanitePaste(n)`.
- **ОМ-4 в перечислениях**: `util/SiliconMaterial` + PROCESSOR_WAFER («processorWafer», мета 18), PROCESSOR (19), EXO_PROCESSOR (20), SINGULAR_MATRIX (21) — на предмете siliconMaterial; `util/SCToolType` + EUV_MASK («euvMask», 24 использования, реестр «toolEUV_MASK»); `machine/MachineType` + EUV_LITHOGRAPH («EUVLithograph», QV, 8000 EU/t, heatCapable) — ordinal 32; третий блок машин `ModBlocks.machineSC3` («machineSC3», `BlockMachineSC(32)`), `BlockMachineSC.MAX_TYPES = 48`, `blockFor` на три блока, BlockShowcaseSC через `blockFor`, вкладка творчества.
- **Блоки** `init/IdeaBlocksSC.java` (вызов в конце `ModBlocks.init`): «relay» (`block/BlockRelaySC` меты RELAY 0 / MAST 1, `block/ItemBlockRelaySC`, TE «SiliconAge.relay» `tileentity/TileEntityRelaySC`, NBT `scRelay`) — ПД-7; «dataPort» (`block/BlockDataPortSC`, TE «SiliconAge.dataPort» `tileentity/TileEntityDataPortSC`, NBT `scDataPort`, режимы STOP/PERMIT) — ОМ-5; «energyBank» (`block/BlockEnergyBankSC` меты CASING 0, GLASS 1, CONTROLLER 2, CELL_4 3, CELL_16 4, PORT_FIRST 5 + Tier.ordinal() = 5..12; `block/ItemBlockEnergyBankSC`; TE «SiliconAge.energyBank» `tileentity/TileEntityEnergyBankSC` (NBT `scBank` {eu, cap, size}), «SiliconAge.energyBankPort» `tileentity/TileEntityEnergyBankPortSC` extends TileEntityEnergyBase (NBT `scBankPort`)) — ОМ-7. Помощники `IdeaBlocksSC.relay(n)`, `dataPort(n)`, `energyBank(meta, n)`. NEI: `NEISiliconAgeConfig.hideIdeaBlocks()` прячет MAST.
- **GUI** `handler/GuiHandlerSC.java`: `ENERGY_BANK_GUI_ID = 22` → `inventory/ContainerEnergyBankSC` / `inventory/GuiEnergyBankSC` (контроллер банка открывает его ПКМ — уже в `BlockEnergyBankSC.onBlockActivated`).
- **Сеть**: новых каналов и дискриминаторов не нужно (см. каждый пункт: состояние идёт через существующие пакеты экрана обмена, рейсов, моста, BOSS_FX, описание TE и IntSyncSC контейнера). Канал «SiliconAgeDim» занят 0..5; если исполнителю всё же понадобится сообщение — через координатора (свободны 6, 7).
- **Жидкость** `init/ModFluids.zrcl4` («zrcl4», едкая — в `CorrosiveFluids`, цвет 0xFFD8E0B0), ведро `ItemFluidBucketSC.FLUIDS` + «zrcl4» (мета 24) — ОМ-3.
- **Поселения**: `settle/SettlementSizeSC.RELAY` (ordinal 4, «relay»: купол 12, жителей 0, 2 Постовых, Причал; `relay()`, `tableIndex()` — строка таблиц размеров: Подложка — свой ordinal, RELAY — OUTPOST, прочее — последняя), `SettlementSC.isRelay(s)`; `settle/SettlementRegistrySC`: `remove(int id)` (только записи RELAY), `players()` (только чтение), `ContractRec.extra` (NBT «Extra», пишется, если не пуст).
- **Общие числа**: `settle/ReputationSC.SRC_RESCUE = 11` (ПД-5), `SRC_WANTED = 12` (ПД-4), `SRC_RELAY = 13` (ПД-7); `settle/trade/TradeOfferSC.OUT_ALARM = 5` (ПД-3), `OUT_WANTED_HINT = 6` (ПД-4); `mob/RareRegistrySC.WANTED = "wanted"` (ПД-4).
- **Общие помощники**: `mob/MobEventsSC.participants(mob, minShare)` — игроки онлайн с долей урона ≥ minShare (ПД-2, ПД-4); `settle/trade/EconomyEventsSC.onDeath` разделён: прогресс контрактов идёт по `SquadSC.contractCredits(mob, killer)`, совпадение цели — `EconomyEventsSC.killCounts(k, mob, name, suppressed)` (ПД-1 дописывает свои цели, ПД-2 — звено); «Доверие» за убийство — как было, только добившему; `energy/IComparatorSC` и `energy/ComparatorSC.level/notify` (ОМ-5, ОМ-7).
- **Генерация ПД-8**: `ruin/MapGenTunnelSC` (старт «SCSi14Tunnel», куски «SCSi14Tun…», класс старта `MapGenTunnelSC.StructureTunnelStartSC` с CellX/CellZ), `ruin/TunnelLayoutSC` (чистые функции), регистрация в `RuinSC.init`; в `dim/ChunkProviderSubstrateSC`: поле `tunnels`, `tunnels()`, старты в provideChunk/recreateStructures (после обломков, до вырезателя пещер), постройка в populate (после обломков, до поселений), поиск структуры по имени. Пока `canSpawnStructureAtCoords` = false — ничего не строится.
- **Заглушки с фиксированными сигнатурами** (тела — исполнителей): `settle/trade/ContractLocalSC`, `settle/trade/ContractEventsSC` (ПД-1); `settle/SquadSC`, `settle/CommandSquadSC` (ПД-2); `hazard/RaidAlarmSC` (ПД-3); `mob/WantedSC`, `mob/WantedRulesSC`, `mob/client/WantedClientSC` (ПД-4); `settle/GathererSC`, `settle/client/AmbientLifeClientSC` (ПД-5); `arena/BreachProtocolsSC`, `arena/BreachFightSC` (ПД-6); `settle/RelayPostSC`, `settle/RelayRulesSC` (ПД-7); `item/SalvageSC` (ОМ-6); `energy/EnergyBankMathSC` (ОМ-7).
- **Проводка**: `SCMod` — регистрация INSTANCE на шинах (ContractEventsSC — FML; SquadSC, WantedSC, GathererSC, RelayPostSC — Forge и FML), `clearAll()` всех новых систем в serverStopped, команда `/scsquad`, `ModRecipesIdeasSC.init()` в registerRecipes, `WorldTestIdeasSC.register()` в блоке worldtest; `ClientProxy` — `WantedClientSC.register()`, `AmbientLifeClientSC.register()`; `SelfTestSC.run` → `SelfTestIdeasSC.run()`; `BookWorldSC` → `BookIdeasSC.add` в конце главы WORLD; `ObtainHandlerSC.ideaPage` (до посольских страниц).

## Пересечения файлов (править аккуратно, сливая с соседними пунктами)

| Файл | Пункты |
|---|---|
| `settle/trade/TradeTableSC.java` | ПД-3, ПД-4, ПД-7 |
| `settle/trade/TradeServerSC.java` | ПД-1, ПД-3, ПД-4 |
| `settle/client/GuiTradeSC.java`, `settle/client/TradeClientSC.java` | ПД-1, ПД-3, ПД-4 |
| `hazard/RaidSC.java`, `hazard/RaidRunSC.java`, `hazard/RaidRulesSC.java` | ПД-3, ПД-7 |
| `settle/npc/EntityWardenSC.java` (строки Капитана) | ПД-3, ПД-4 |
| `mob/TrophyRestoreSC.java` | ПД-4, ОМ-6 |
| `mob/MobDropsSC.java` | ПД-6 |
| `mob/SpawnDirectorSC.java` | ПД-7 |
| `embassy/FlightTargetsSC.java` | ПД-2, ПД-7 |
| `tileentity/TileEntityQuarrySC.java` | ОМ-1, ОМ-5 |
| `tileentity/TileEntityGeneratorSC.java`, `tileentity/SingularReactorSC.java` | ОМ-5 |
| `manual/BookContent.java` | ОМ-1, ОМ-2, ОМ-3, ОМ-4, ОМ-5, ОМ-6, ОМ-7 |
| `manual/BookWorldSC.java` | ПД-1, ПД-2, ПД-3, ОМ-2 |
| `manual/BookPathSC.java` | ОМ-4, ОМ-7 |
| `manual/BookIdeasSC.java` (свой метод) | ПД-2 … ПД-8 |
| `init/ModRecipesIdeasSC.java` (свой метод) | ПД-6, ОМ-2, ОМ-3, ОМ-4, ОМ-5, ОМ-7 |
| `nei/ObtainHandlerSC.java` (`ideaPage`) | ПД-4, ПД-7 |
| `debug/SelfTestIdeasSC.java` (свой метод), `debug/WorldTestIdeasSC.java` (своя строка) | все |
| `README.md` | ПД-2 … ПД-8 (ключи конфига), ОМ-1 |
| `compat/WailaSC.java` | ОМ-5, ОМ-7 |

Порядок внутри общих файлов: ПД-3 → ПД-7 (налёты), ПД-1 → ПД-3 → ПД-4 (обмен), ПД-4 → ОМ-6 (восстановление), ОМ-4 → ОМ-7 (рецепты на процессорах), ОМ-1 → ОМ-5 (карьер).

---------------------------------------------------------------------------------------------------

## ПД-1. Живая доска контрактов

**★** А. Новые контракты в Подложке (14–16, с набором под поселение) плюс заказы Наладчиков на продукцию завода (транзисторы, контроллеры, катушки Nb3Sn, EV-аккумулятор) и раз в неделю «Большой заказ». Гелия и дейтерия в заказах нет. Награды и пределы прежние: 6–16 КЧ за контракт, не больше 3 контрактов (TradeRulesSC.CONTRACTS_PER_DAY) и 64 КЧ (CHIPS_PER_DAY) в сутки.

**Что сделать.**

1. `settle/trade/ContractTableSC`: новые цели (дописать): `STORM_KILL = 6` (добить бронированного во время ЭМИ-бури), `READINGS = 7` (снять показания в Мёртвом Узле), `SAFE = 8` (вскрыть сейф лагеря), `URGENT = 9` (срочная депеша до заката), `SIGNAL = 10` (забрать тайник Сигнала), `BIG_ORDER = 11` (Большой заказ: несколько предметов разом). Поле `Contract.group`: `G_GENERAL = 0`, `G_LOCAL = 1`, `G_ORDER = 2`, `G_BIG = 3`; поле `localTag` (ContractLocalSC.TAG_*; 0 — для всех). Для BIG_ORDER — список сторон (`TradeOfferSC.Ingredient[] bundle`).
2. Пул Старейшин Подложки (ключи стабильны, `sc.contract.<key>` / `.desc`):
   - общие (G_GENERAL): прежние 4 (`killMarauders` 10 Мародёров 8 КЧ +20; `deliverTantalumCaps` 32 конденсатора 14 КЧ +25; `suppressKill` 5 10 КЧ +25, 20% модуль; `returnCargo` 3 груза 10 КЧ +35) и новые: `huntTroopers` — KILL 4 «trooper», 10 КЧ +25; `stormArmoured` — STORM_KILL 3, 12 КЧ +30; `nodeReadings` — READINGS 2 разных Узла, 8 КЧ +20; `crackSafe` — SAFE 1, 14 КЧ +30; `urgentDispatch` — URGENT 1, 10 КЧ +25; `signalCache` — SIGNAL 1, 16 КЧ +40;
   - местные (G_LOCAL, по `ContractLocalSC.tags`): `huntColossus` (TAG_RIDGES) KILL 1 «colossus» 12 КЧ +30; `huntLeeches` (TAG_RIDGES) KILL 4 «leech» 8 КЧ +20; `huntOverheated` (TAG_OXIDE) KILL 3 «overheated» 10 КЧ +25; `downDrones` (TAG_NODE) KILL 4 «sentryDrone» 8 КЧ +20; `huntShells` (TAG_NODE) KILL 4 «hollowShell» 10 КЧ +25 — итого контрактов 15;
   - заказы (G_ORDER, DELIVER, можно частями): `orderTransistors` 64 транзистора (SiliconMaterial.TRANSISTOR) 8 КЧ +15; `orderControllers` 6 контроллеров 12 КЧ +25; `orderCoils` 6 катушек Nb3Sn (`nb3SnCoil`) 12 КЧ +25; `orderEvBattery` 1 EV-аккумулятор (ModItems.battery мета 3, любой заряд) 10 КЧ +20;
   - `bigOrder` (G_BIG, BIG_ORDER, сдаётся только целиком): 32 транзистора + 4 контроллера + 4 катушки Nb3Sn + 1 EV-аккумулятор, 16 КЧ +40; раз в игровую неделю на игрока (PlayerRec.extra `"O"` {w: неделя, когда взят}) у любого Старейшины Подложки. TODO(balance) — все числа.
   Посольская доска (embassyPool) не меняется.
3. Доска (`board(UUID, long day, SettlementRegistrySC.Settlement s)` вместо `board(UUID, long, boolean)` для Подложки; посольская — как была): `BOARD = 3` общих из 10 (перемешивание по игроку и дню, как сейчас) + 1 местный (из подходящих тегам поселения, соль — маска тегов, а не id: у поселений с одинаковыми тегами он одинаков; нет подходящих — нет строки) + 1 заказ (из 4, своя соль) + `bigOrder`, если на этой неделе не взят. 3 контракта в сутки — по-прежнему на все строки вместе. Чистая функция; `SelfTestSettlementSC` (проверка BOARD) поправить под новую доску.
4. `settle/trade/ContractLocalSC` (заглушка есть): `tagsOf(int[] cellKinds)` — по видам своей ячейки и восьми соседних (`BiomeSubstrateSC.RIDGES` → TAG_RIDGES, `OXIDE` → TAG_OXIDE, `RIFT` → TAG_RIFT), TAG_NODE всегда; `tags(World, Settlement)` — `SubstrateCellsSC.cellKind` по ячейкам вокруг ядра, результат один раз в `Settlement.extra "L" {t}`; клиент / посольство / пост-ретранслятор — TAG_NODE.
5. Цели:
   - KILL местных — прежняя ветка `killCounts` (имя моба);
   - STORM_KILL — в `EconomyEventsSC.killCounts`: `mob instanceof IArmoredMobSC` и `EmpStormSC.active(mob.worldObj)`;
   - READINGS — `ContractEventsSC` на FML `PlayerTickEvent` (END, сервер, раз в READINGS_EVERY = 20 тиков): игрок в Подложке, `RuinLayoutSC.nodeNear(x, z)` ≠ null, в пределах 10 блоков от центра перекрёстка (площадь), Узел построен (не арена, `MapGenDeadNodeSC.covers`); копится время по UUID (память); на READINGS_TICKS = 200 — шаг, если этот перекрёсток ещё не засчитан (ContractRec.extra «nodes»: int[] пар i, j), строка `sc.contract.readings.step`;
   - SAFE — `ContractEventsSC.safeOpened(p, x, y, z)`: вызвать из `block/BlockCampSC` сразу после `openSafe` (только настоящий игрок);
   - SIGNAL — `ContractEventsSC.signalCache(p)`: вызвать из `lore/SignalCacheSC.make` (тайник отдан игроку `p`);
   - URGENT — берётся только с рассвета до URGENT_TAKE_UNTIL = 6000 тиков суток (иначе `R_URGENT_TIME`); при взятии назначается адресат — ближайшее другое поселение Подложки со Старейшиной (не посольство, не пост-ретранслятор) не дальше URGENT_MAX = 1500 блоков (нет — контракт не на доске), в ContractRec.extra {to: id, by: мировое время заката того же дня (день·24000 + 12000)}; сдаётся кнопкой у Старейшины адресата (ничего не нести, как «Депеша домой»), у другого — `R_ELSEWHERE`; после `by` — потерян (`R_DISPATCH_LATE`), строка `sc.contract.urgent.late`;
   - BIG_ORDER — сдаётся только если в инвентаре все стороны сразу (TradeOfferSC.Ingredient каждая), снимаются все.
6. `settle/trade/TradeServerSC`: взятие/сдача по новым целям и группам; новые коды отказа (дописать): `R_URGENT_TIME = 23`, `R_BIG_WEEK = 24`; состояние экрана: у строки доски {k, g: группа, to: имя адресата (элемент, номер), dl: тиков до заката}; сообщения по KILL/STORM_KILL — как сейчас (`say`).
7. `settle/client/GuiTradeSC`, `TradeClientSC`: строки доски с меткой группы (местный — значок биома, заказ — ящик, большой — золотой), у срочной — адресат и время до заката; список прокручивается (уже есть).
8. Тексты убийственных контрактов: в `.desc` вместо «Засчитываются только ваши убийства…» — «Засчитываются ваши убийства и убийства звена (кто нанёс 10% урона)» (согласовано с ПД-2; при выключенном `squads` фраза верна частично — допустимо).

**Числа (TODO(balance)).** Все награды выше; URGENT_TAKE_UNTIL 6000, URGENT_MAX 1500, READINGS 200 тиков на 10 блоков от центра.

**Новое.** NBT: ContractRec.extra (ключи `to`, `by`, `nodes`), PlayerRec.extra `"O"` {w}, Settlement.extra `"L"` {t}. Сеть — существующие TRADE_STATE / CONTRACT_*. Конфиг — нет.

**Справочник.** Статья обмена (`BookWorldSC`, «sc.manual.world.trade.contracts»): состав доски (3 общих + местный + заказ + большой раз в неделю), таблица контрактов с наградами, где брать и сдавать депешу.

**Тексты (ключ = ru | en).**
- sc.contract.huntTroopers = Охота на Штурмовиков | Trooper hunt
- sc.contract.huntTroopers.desc = Убейте Штурмовиков-отступников, рождённых в Подложке: %s. Засчитываются ваши убийства и убийства звена (кто нанёс 10% урона). | Kill %s Renegade Troopers born in the Substrate. Your kills count, and your squad's (whoever dealt 10% of the damage).
- sc.contract.stormArmoured = Работа в бурю | Storm work
- sc.contract.stormArmoured.desc = Добейте бронированных врагов во время ЭМИ-бури: %s. | Finish off %s armoured hostiles during an EMP storm.
- sc.contract.nodeReadings = Показания Узлов | Node readings
- sc.contract.nodeReadings.desc = Постойте на площади Мёртвого Узла 10 с - показания снимутся сами. Нужно разных Узлов: %s. | Stand on a Dead Node's plaza for 10 s - the readings are taken by themselves. Different Nodes needed: %s.
- sc.contract.readings.step = Показания Узла %s %s сняты. | Readings of the Node at %s %s taken.
- sc.contract.crackSafe = Сейф Отступников | The Renegades' safe
- sc.contract.crackSafe.desc = Вскройте сейф в лагере Отступников: %s. Ключ - у Вожака лагеря. | Open a safe in a Renegades' camp: %s. The camp's Warlord has the key.
- sc.contract.urgentDispatch = Срочная депеша | Urgent dispatch
- sc.contract.urgentDispatch.desc = Отнесите депешу Старейшине поселения „%s“ до заката. Берётся только до полудня. | Take the dispatch to the Elder of “%s” before sunset. Only taken before noon.
- sc.contract.urgent.to = Адресат: „%s“, %s бл., до заката %s | To: “%s”, %s blocks, %s to sunset
- sc.contract.urgent.late = Срочная депеша опоздала - контракт потерян. | The urgent dispatch is too late - the contract is lost.
- sc.trade.result.urgentTime = Срочные депеши берут только до полудня. | Urgent dispatches are only taken before noon.
- sc.contract.signalCache = Тайник Сигнала | The Signal's cache
- sc.contract.signalCache.desc = Заберите тайник Сигнала Литографов: %s. Сигнал бывает не каждый день. | Take a cache of the Lithographers' Signal: %s. There isn't a Signal every day.
- sc.contract.huntColossus = Колосс у Гряд | The Ridges' Colossus
- sc.contract.huntColossus.desc = Убейте Кварцитового колосса, рождённого в Подложке: %s. | Kill %s Quartzite Colossus born in the Substrate.
- sc.contract.huntLeeches = Токоеды у Гряд | The Ridges' Leeches
- sc.contract.huntLeeches.desc = Убейте Токоедов, рождённых в Подложке: %s. | Kill %s Current Leeches born in the Substrate.
- sc.contract.huntOverheated = Перегретые на полях | The Overheated of the fields
- sc.contract.huntOverheated.desc = Убейте Перегретых, рождённых в Подложке: %s. | Kill %s Overheated born in the Substrate.
- sc.contract.downDrones = Дроны Узла | The Node's drones
- sc.contract.downDrones.desc = Собейте одичавших дронов-охранников: %s. | Shoot down %s feral sentry drones.
- sc.contract.huntShells = Оболочки Узла | The Node's Shells
- sc.contract.huntShells.desc = Уничтожьте Пустые оболочки: %s. | Destroy %s Hollow Shells.
- sc.contract.orderTransistors = Заказ: транзисторы | Order: transistors
- sc.contract.orderTransistors.desc = Наладчикам нужны транзисторы: %s шт. Можно частями. | The Tuners need transistors: %s. A part at a time is fine.
- sc.contract.orderControllers = Заказ: контроллеры | Order: controllers
- sc.contract.orderControllers.desc = Наладчикам нужны контроллеры: %s шт. Можно частями. | The Tuners need controllers: %s. A part at a time is fine.
- sc.contract.orderCoils = Заказ: катушки Nb3Sn | Order: Nb3Sn coils
- sc.contract.orderCoils.desc = Наладчикам нужны катушки Nb3Sn: %s шт. Можно частями. | The Tuners need Nb3Sn coils: %s. A part at a time is fine.
- sc.contract.orderEvBattery = Заказ: EV-аккумулятор | Order: an EV battery
- sc.contract.orderEvBattery.desc = Наладчикам нужен переносной аккумулятор EV: %s шт. Заряд не важен. | The Tuners need a portable EV battery: %s. Its charge doesn't matter.
- sc.contract.bigOrder = Большой заказ | Big order
- sc.contract.bigOrder.desc = Раз в неделю: 32 транзистора, 4 контроллера, 4 катушки Nb3Sn и EV-аккумулятор - всё сразу. | Once a week: 32 transistors, 4 controllers, 4 Nb3Sn coils and an EV battery - all at once.
- sc.trade.result.bigWeek = Большой заказ на этой неделе уже взят. | This week's big order is taken already.
- sc.contract.group.local = Местный | Local
- sc.contract.group.order = Заказ | Order
- sc.contract.group.big = Раз в неделю | Weekly
- sc.contract.killMarauders.desc (замена) = Убейте Мародёров, рождённых в Подложке: %s. Засчитываются ваши убийства и убийства звена (кто нанёс 10% урона). | Kill %s Marauders born in the Substrate. Your kills count, and your squad's (whoever dealt 10% of the damage).
- sc.contract.suppressKill.desc — та же добавка про звено.
- sc.manual.world.trade.contracts.2 = На доске каждый день: 3 общих контракта, 1 местный (по тому, что вокруг поселения: Гряды, Окисные поля, Мёртвый Узел), 1 заказ Наладчиков и раз в неделю Большой заказ. Взять можно 3 в сутки. | Every day the board has 3 common contracts, 1 local one (by what lies round the settlement: the Ridges, the Oxide Fields, a Dead Node), 1 Tuners' order and once a week the Big order. 3 a day can be taken.

**Проверки.** Самотест `SelfTestIdeasSC.pd1`: пул — 10 общих, 5 местных, 4 заказа, 1 большой; все награды 6..16 КЧ и +10..+40; `tagsOf` (Гряды → RIDGES|NODE, пусто → NODE); доска: одинакова при повторе, 3 разных общих + ≤1 местный по тегам + 1 заказ (+ большой, если не взят), разные поселения с одинаковыми тегами — одинаковы; `killCounts` STORM_KILL (чистый помощник `stormCounts(armoured, storm)`); срок депеши (чистый `urgentLate(by, now)`); языковые ключи всех контрактов в обоих файлах с одним числом `%s`. Мировой тест `WorldTestContractsSC` (Подложка, регион k = 1): две записи поселений, игрок-тест берёт `urgentDispatch` у первого, сдача у второго — OK, у первого — R_ELSEWHERE; `ContractEventsSC.safeOpened` двигает SAFE; 200 тиков на площади Узла — шаг READINGS, второй раз тот же Узел — нет. Вручную по LAN: двое берут разные доски у разных Старейшин.

**Файлы:** `settle/trade/ContractTableSC.java`, `settle/trade/EconomyEventsSC.java` (только `killCounts`), `settle/trade/ContractLocalSC.java`, `settle/trade/ContractEventsSC.java`, `settle/trade/TradeServerSC.java`, `settle/client/GuiTradeSC.java`, `settle/client/TradeClientSC.java`, `block/BlockCampSC.java`, `lore/SignalCacheSC.java`, `manual/BookWorldSC.java`, `debug/SelfTestIdeasSC.java`, `debug/SelfTestSettlementSC.java`, `debug/WorldTestContractsSC.java` (новый), `debug/WorldTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ПД-2. Звено для двоих

**★** А. Осторожный зачёт: прогресс контракта получает каждый в звене, кто нанёс цели не меньше 10% урона; «Доверие» за само убийство — только добившему (как сейчас). Метка гибели напарника видна в целях моста и в спасательном рейсе. Звено — щелчок Кредит-чипом по другу или одна ванильная команда scoreboard. Правило `dimension.squads` (по умолчанию вкл.).

**Что сделать.**

1. `settle/SquadSC` (заглушка с сигнатурами есть):
   - Forge `EntityInteractEvent`: игрок с Кредит-чипом в руке ПКМ по другому настоящему игроку (не FakePlayer, не он сам) → приглашение в памяти (по UUID цели: от кого, до мирового времени + INVITE_TICKS = 1200), отмена события (чип не тратится, сделок нет); цели — строка с кнопками [Принять] / [Отклонить] (ChatClickEvent RUN_COMMAND `/scsquad accept <имя>` / `decline <имя>`), пригласившему — «приглашение отправлено». Звено полно (MAX = 4) — отказ.
   - Принятие (`CommandSquadSC`): у пригласившего есть звено — цель входит в него; нет — новое звено (id = мировое время ^ случайное, ≠ 0) у обоих. Хранится в `PlayerRec.extra "Q"` {s: id, t: время вступления, n: последнее имя игрока (обновлять при входе в мир — FML PlayerLoggedIn)}. Строка всем членам онлайн.
   - `/scsquad leave` — выйти (остался один — звено распалось у него тоже); `/scsquad list` — члены: имя, онлайн, расстояние (если в одном мире).
   - `mates(a, b)`: одно звено (id) или `a.isOnSameTeam(b)` (ванильная команда); не сам с собой; только при `SubstrateConfigSC.squads`.
   - `matesOf(UUID)`: обход `SettlementRegistrySC.players()` по id (редко: список моста, рейсы, приглашения).
   - `contractCredits(mob, killer)`: добивший (как сейчас) + при `squads` каждый `p` из `MobEventsSC.participants(mob, SHARE)`, `p != killer`, `mates(killer, p)`, `MobDropsSC.qualifies(mob, p)`. Без добившего (никто не набрал MobDropsSC.KILLER_SHARE) — никому, как сейчас. Логика выбора — чистая функция `credited(Map<UUID,Float> shares, UUID killer, Set<UUID> mates, float share)` для самотеста.
   - FML `PlayerLoggedOut`: приглашения ему и от него — забыть.
2. Мост `tileentity/TileEntityBridgeControllerSC.si14Targets`: для каждого напарника (по звену — `matesOf`; по команде — игроки онлайн `mates`) с меткой гибели в Подложке (`hasDeath`, тот же dim) — строка `k = TravelRulesSC.T_MATE_DEATH` (дописать = 3), `l = "sc.bridge.target.mateDeath"`, `pn` — имя напарника (из "Q".n или онлайн), `p` — {x, AUTO_Y, z, dim}; `deathMarkAt` признаёт и метку напарника (тот же разброс `TravelRulesSC.DEATH_SCATTER`, AT_MARK); гаснет метка по-прежнему только когда к ней подойдёт её владелец. Экраны (`client/GuiBridgeControllerSC`, `client/GuiBridgeFarSC`, где перечисляются цели Подложки) — подпись с именем.
3. Спасательный рейс: `embassy/FlightTargetsSC` — для RIDE_RESCUE, кроме своей метки, свежие метки напарников (`rescueOpen(mateRec, now)`); `embassy/FlightServerSC` — FLIGHT_STATE: «rescueMates» [{n, u}]; FLIGHT_DO {ride: RESCUE, target: "mate:<uuid>"}; при перелёте помечать спасение на записи напарника (`EmbassyTicketSC.rescuedAt` его) — одно спасение на одну гибель всего; разброс как у своей. `settle/client/GuiTradeSC` (вкладка «Рейсы») — выбор метки.
4. Тексты убийственных контрактов — см. ПД-1 п. 8.

**Числа.** SHARE 0.10, MAX 4, INVITE_TICKS 1200 (TODO(balance)).

**Новое.** NBT: `PlayerRec.extra "Q"` {s, t, n}. Команда `/scsquad` (зарегистрирована). TravelRulesSC.T_MATE_DEATH = 3 (дописать). Сеть: существующие состояния моста и рейсов (новые поля в их NBT). Конфиг `dimension.squads` (есть).

**Справочник.** `BookIdeasSC.squads` — статья «world.squads»: как собрать звено (щелчок чипом, команда scoreboard), что даёт (10% урона — контракт, «Доверие» — добившему), метка напарника у моста и Диспетчера, команды.

**Тексты (ключ = ru | en).**
- sc.squad.invite.sent = Приглашение в звено отправлено: %s. | Squad invitation sent to %s.
- sc.squad.invite.got = %s зовёт вас в звено: %s %s | %s invites you to a squad: %s %s
- sc.squad.accept = [Принять] | [Accept]
- sc.squad.decline = [Отклонить] | [Decline]
- sc.squad.declined = %s отказался от звена. | %s declined the squad.
- sc.squad.expired = Приглашение в звено истекло. | The squad invitation has expired.
- sc.squad.joined = %s теперь в звене. | %s has joined the squad.
- sc.squad.left = %s вышел из звена. | %s has left the squad.
- sc.squad.full = Звено полное: не больше %s. | The squad is full: %s at most.
- sc.squad.none = Вы не в звене. | You're not in a squad.
- sc.squad.off = Звенья выключены на этом сервере. | Squads are off on this server.
- sc.squad.list.head = Звено (%s): | Squad (%s):
- sc.squad.list.line = %s - %s | %s - %s
- sc.squad.list.offline = не в сети | offline
- sc.squad.list.far = %s бл. | %s blocks
- sc.cmd.squad.usage = /scsquad accept|decline <имя> | leave | list | /scsquad accept|decline <name> | leave | list
- sc.bridge.target.mateDeath = Метка гибели: %s | Death mark: %s
- sc.bridge.target.mateDeath.hint = Место гибели напарника: разброс до %s бл.; метка гаснет, когда к ней подойдёт он сам | Where your mate died: scatter up to %s blocks; the mark goes out when he himself comes near it
- sc.flight.target.mateMark = Метка гибели: %s (%s %s) | Death mark: %s (%s %s)
- sc.manual.world.squads.title = Звено | The squad
- sc.manual.world.squads.1 = Звено - до %s игроков: щёлкните Кредит-чипом по другу, он примет приглашение в чате. Игроки одной команды scoreboard - звено и без приглашения. | A squad is up to %s players: right-click a friend with a Credit Chip, he accepts in the chat. Players of one scoreboard team are a squad with no invitation.
- sc.manual.world.squads.2 = Убийство засчитывается в контракты каждому в звене, кто нанёс цели не меньше %s%% урона; „Доверие“ за само убийство получает только добивший. | A kill counts towards the contracts of every member who dealt at least %s%% of the target's damage; the kill's “Trust” goes to the one who finished it only.
- sc.manual.world.squads.3 = Метка гибели напарника - цель моста и Спасательного рейса Диспетчера. Одна гибель - одно спасение на всё звено. | A mate's death mark is a bridge target and a target of the Dispatcher's Rescue flight. One death - one rescue for the whole squad.

**Проверки.** Самотест `pd2`: `credited` — добивший всегда, напарник с 10% — да, с 9% — нет, чужой с 50% — нет, без добившего — пусто; правило выкл. — только добивший; MAX и приглашение (чистые). Мировой тест (по желанию) `WorldTestSquadSC` (Подложка, k = 2): запись двух UUID в одно звено, `matesOf`; цель моста T_MATE_DEATH в `si14Targets` для зрителя-теста. Вручную по LAN: двое в звене, один гасит ПЭ-1, второй добивает — контракт у обоих, «Доверие» у добившего; метка друга в списке моста.

**Файлы:** `settle/SquadSC.java`, `settle/CommandSquadSC.java`, `settle/travel/TravelRulesSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `client/GuiBridgeControllerSC.java`, `client/GuiBridgeFarSC.java`, `embassy/FlightTargetsSC.java`, `embassy/FlightServerSC.java`, `embassy/EmbassyTicketSC.java`, `settle/client/GuiTradeSC.java`, `manual/BookIdeasSC.java`, `debug/SelfTestIdeasSC.java`, `debug/WorldTestSquadSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-3. Тревога по вызову

**★** А. «Тревога» с уровня «Знакомый» (одна на игрока раз в 2 дня) и «Тяжёлая ночь» с уровня «Свой» (не чаще раза в 3 дня). Залог 8 КЧ, налёт — в ближайшую ночь, когда покупатель рядом. В «Тяжёлую ночь» — четвёртая волна (Экзо-палач и Штурмовик-Командир), награда x1,5. Победа — залог назад плюс обычная награда (+30/40/50 «Доверия», КЧ, сундук Старейшины); поражение — залог сгорает, торговля закрыта на сутки. После боя — итог в чат: волны, время, кто сколько налётчиков добил. Правило `dimension.raidAlarms` (и `raids`).

**Что сделать.**

1. Услуги Старейшины (`settle/trade/TradeTableSC`, ключи дописать): `ELDER_ALARM = "elderAlarm"` (OUT_ALARM, стороны: `chips(RaidAlarmSC.DEPOSIT)`, уровень ACQUAINTANCE, лимит UNLIMITED — частоту держит RaidAlarmSC), `ELDER_HEAVY_NIGHT = "elderHeavyNight"` (OUT_ALARM, 8 КЧ, уровень OWN) — только в поселениях, где служит Капитан (`SettlementSizeSC.captain(seed)`: Город всегда, Посёлок 30%), — это и есть «у Капитана». Ни в посольстве, ни на посту-ретрансляторе. Капитан (`settle/npc/EntityWardenSC`, Shift + ПКМ по Капитану) добавляет строку: «Тяжёлую ночь объявляет Старейшина, оборону веду я» (`sc.alarm.captain`).
2. `hazard/RaidAlarmSC` (заглушка есть): `refusal(p, s, kind)` (до оплаты: R_OFF, R_LEVEL, R_COOLDOWN — `due()` по PlayerRec.extra "A" {a, h}, R_PENDING — уже ждёт вызов здесь, R_RUNNING, R_SIZE, + R_CAPTAIN = 7 для тяжёлой без Капитана) и `order(p, s, kind)` (после оплаты: Settlement.extra "A" {by, k, d}, день игрока). Пока вызов ждёт, та же строка услуги у этого Старейшины — «Отменить и вернуть залог» (TRADE_DO с тем же ключом: вызов снят, 8 КЧ вернулись, день вызова игрока не сбрасывается — дважды подряд не схитрить).
3. `hazard/RaidSC.checks`: поселение с ожидающим вызовом — ночью, если `by` защищает его (`defends`) — `begin(w, r, s, by)` без броска и без отката поселения (свой обычный бросок этой ночи не тратится), вызов снят; запуску передать вид (`RaidRunSC.kind`: 0 обычный, 1 тревога, 2 тяжёлая) и `by`.
4. `hazard/RaidRulesSC`: `WAVES_HEAVY = 4`; `wave(3, size, r)` для тяжёлой: {ID_EXECUTIONER 1, ID_TROOPER 1 — элита EliteModSC.COMMANDER, ID_JAMMER 1}; `HEAVY_MUL` из RaidAlarmSC; все таблицы по размеру — через `SettlementSizeSC.of(size).tableIndex()` (готовит и ПД-7).
5. `hazard/RaidRunSC`: число волн по виду; на 4-й волне палач и Командир (EliteSC.apply(..., COMMANDER)); победа: награда x HEAVY_MUL для тяжёлой (rep, КЧ, броски сундука — с округлением вверх: 45/60/75, 9/14/18, 3/5/6), `by` (если присутствует) получает залог 8 КЧ назад (вне суточного лимита КЧ, как награда налёта); поражение: залог не возвращается (уже уплачен), торговля закрыта CLOSED_TICKS (как сейчас). Подсчёт добитых налётчиков по защитникам: `RaidSC` на Forge `LivingDeathEvent` (LOWEST) налётчика (RAIDER_NBT) → `MobEventsSC.killerOf` → `run.killed(uuid)`. Итог по концу любого налёта (и обычного): всем присутствующим и `audience` — `sc.raid.summary` (волн пройдено из всех, время мин/с) и `sc.raid.summary.kills` (имя — число, по убыванию; никого — `.none`).
6. Экран обмена (`settle/client/GuiTradeSC`): строка услуги показывает «Залог 8 КЧ», статус (ждёт здесь / откат N сут. / нужен Капитан).

**Числа (TODO(balance)).** DEPOSIT 8, ALARM_DAYS 2, HEAVY_DAYS 3, HEAVY_MUL 1.5, 4-я волна — палач + Командир + Глушитель.

**Новое.** NBT: Settlement.extra "A" {by, k, d}, PlayerRec.extra "A" {a, h}. TradeOfferSC.OUT_ALARM (есть). Конфиг `dimension.raidAlarms` (есть). Сеть — существующая.

**Справочник.** Статья налётов (`BookWorldSC.raids`) — абзац о Тревоге и Тяжёлой ночи (уровни, залог, откаты, 4-я волна, x1,5, итог).

**Тексты (ключ = ru | en).**
- sc.trade.offer.elderAlarm = Тревога | Alarm
- sc.trade.offer.elderHeavyNight = Тяжёлая ночь | Heavy night
- sc.trade.offer.alarmCancel = Отменить и вернуть залог | Cancel, get the deposit back
- sc.alarm.deposit = Залог: %s КЧ | Deposit: %s chips
- sc.alarm.ordered = Тревога объявлена: налёт придёт в ближайшую ночь, когда вы будете в %s бл. от ядра „%s“. | The alarm is raised: a raid comes the first night you are within %s blocks of “%s”'s core.
- sc.alarm.orderedHeavy = Тяжёлая ночь объявлена: четыре волны, в последней - Экзо-палач и Штурмовик-Командир. Будьте в %s бл. от ядра „%s“ ночью. | A heavy night is called: four waves, the last with an Exo Executioner and a Commander Trooper. Be within %s blocks of “%s”'s core at night.
- sc.alarm.cancelled = Тревога отменена, залог %s КЧ возвращён. | The alarm is called off, the %s-chip deposit is back.
- sc.alarm.begins = Тревога: Отступники идут на „%s“! | Alarm: the Renegades march on “%s”!
- sc.alarm.refuse.off = Тревог на этом сервере нет. | There are no alarms on this server.
- sc.alarm.refuse.level = Нужен уровень „%s“. | You need “%s”.
- sc.alarm.refuse.cooldown = Снова можно через %s сут. | Again in %s days.
- sc.alarm.refuse.pending = Здесь уже ждут налёта. | A raid is awaited here already.
- sc.alarm.refuse.running = Налёт уже идёт. | A raid is already on.
- sc.alarm.refuse.size = Здесь тревогу не объявить. | No alarm can be raised here.
- sc.alarm.refuse.captain = Тяжёлую ночь объявляют только там, где служит Капитан. | A heavy night is only called where a Captain serves.
- sc.alarm.captain = Тяжёлую ночь объявляет Старейшина. Оборону поведу я. | The Elder calls a heavy night. I'll lead the defence.
- sc.raid.wave4 = Четвёртая волна: Экзо-палач и Штурмовик-Командир! | The fourth wave: an Exo Executioner and a Commander Trooper!
- sc.raid.deposit.back = Залог %s КЧ возвращён. | The %s-chip deposit is back.
- sc.raid.deposit.lost = Налёт проигран - залог сгорел. | The raid is lost - the deposit is gone.
- sc.raid.summary = Итог налёта на „%s“: волн %s из %s, %s мин %s с. | The raid on “%s”: waves %s of %s, %s min %s s.
- sc.raid.summary.kills = Добили: %s | Finished off: %s
- sc.raid.summary.kill = %s - %s | %s - %s
- sc.raid.summary.none = Добили: никто. | Finished off: nobody.
- sc.manual.world.raids.alarm = Налёт можно вызвать: у Старейшины „Тревога“ (с „%s“, раз в %s сут.) и „Тяжёлая ночь“ (с „%s“, раз в %s сут., где служит Капитан) за залог %s КЧ. Налёт придёт в первую ночь, когда вы рядом; в Тяжёлую ночь - четвёртая волна и награда x%s. Победа вернёт залог, поражение его сжигает. | A raid can be called: the Elder's “Alarm” (from “%s”, once in %s days) and “Heavy night” (from “%s”, once in %s days, where a Captain serves) for a %s-chip deposit. The raid comes the first night you are near; on a heavy night - a fourth wave and the reward x%s. A win returns the deposit, a loss burns it.

**Проверки.** Самотест `pd3`: `due()` (2/3 дня, -1 — можно), тяжёлая волна 3 (палач, Штурмовик, Глушитель), множители наград (45/60/75 и т. д.), `tableIndex` для RELAY = 0. Мировой тест `WorldTestAlarmSC` (Подложка, k = 3): вызов на запись поселения от тест-игрока, ночь (setWorldTime), `RaidSC` начинает налёт без броска, 4 волны у тяжёлой (`RaidSC.wave(id)` доходит до 3), `stepNow` до победы — залог у тест-игрока (или проверка вызова `give`).

**Файлы:** `hazard/RaidAlarmSC.java`, `hazard/RaidSC.java`, `hazard/RaidRunSC.java`, `hazard/RaidRulesSC.java`, `settle/trade/TradeTableSC.java`, `settle/trade/TradeServerSC.java`, `settle/client/GuiTradeSC.java`, `settle/npc/EntityWardenSC.java`, `manual/BookWorldSC.java`, `debug/SelfTestIdeasSC.java`, `debug/SelfTestHazardSC.java` (если меняются проверенные числа налёта), `debug/WorldTestAlarmSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-4. Розыск: именные Отступники

**★** А. Раз в игровую неделю Капитан Города объявляет розыск: один именной Отступник на мир (например, «Хром-7 „Резчик“») в броне Квант или Экзо с охраной из Штурмовиков, кочует в 600–1500 блоках от Города; Картограф за 4 КЧ подскажет, где его видели. Два модификатора элиты — только из проверенных пар; от убийства игроков не усиливается. Каждому, кто нанёс от 10% урона, — КЧ, «Доверие» и «Жетон розыска»; за 3 жетона Оружейник восстанавливает трофей без КЧ. Правило `dimension.wanted`.

**Что сделать.**

1. Запись — `RareRegistrySC.WANTED` (одна на Подложку): состояние NONE (объявлен, не в мире) / ALIVE (в мире) / DEAD; extra: {w: неделя, id: long (EntityWantedSC.NBT), city: id Города, x, z: где он сейчас, sx, sz, sd: «видели» — место и мировое время, look: `WantedRulesSC.packLook`, m1, m2: коды модификаторов, suit: ArmorSuit ordinal, esc: UUID охраны}.
2. `mob/WantedSC` на FML WorldTickEvent (Подложка, END, раз в 200 тиков):
   - новая неделя (`SettlementSC.week`), правило вкл., есть хоть один Город Подложки в реестре — объявить: Город — по сиду недели среди Городов; точка — угол и расстояние RANGE_MIN..RANGE_MAX от ядра, не в поясе купола (`registry.within`), не в Разломе (`SubstrateCellsSC.biomeKind` ≠ RIFT), не на перекрёстке арены; костюм Квант (QUANTUM_PCT) или Экзо; пара модификаторов из `PAIRS`; имя/прозвище по сиду. Старый розыск недели прошлой (живой) — уходит (его сущность при загрузке видит чужой id и исчезает). Строка всем в Подложке: `sc.wanted.posted`;
   - кочёвка без игрока: раз в ROAM_EVERY шаг ROAM_STEP в случайную сторону внутри кольца и тех же запретов; «видели» = эта точка;
   - появление: настоящий игрок (не творческий) в SPAWN_NEAR от точки и её чанк загружен — `EntityWantedSC` + ESCORT Штурмовиков (через `SpawnDirectorSC.spawnAt`: рождены в Si-14; постоянные — `func_110163_bv`), запись ALIVE; пока жив и загружен — точка записи следует за ним каждые 100 тиков, поводок: дальше RANGE_MAX — поворот домой (setHomeArea);
   - ушли игроки (никого в 128 блоках 600 тиков) — он и охрана уходят из мира (setDead без смерти), запись NONE в его точке.
3. `mob/EntityWantedSC` (зарегистрирован, наследник Штурмовика): одевание по suit 4/4 (MobArmorSC, багровое свечение GLOW 1), Квант — Квантовый клинок или Плазменная винтовка, Экзо — Экзо-клинок; HP 80, запас 120..160 (TODO(balance)); `onSpawnWithEgg` не используется (яйца нет); NBT `scWanted` (id), второй модификатор — NBT `scEliteMod2`, клиентам — `DW_WANTED` (packLook); первый тик: id ≠ записи — исчезнуть; не исчезает (persistence). Имя над головой и модификаторы — `mob/client/RenderWantedSC` (вид Штурмовика или Экзо-палача по костюму), регистрация в `WantedClientSC.register()`.
4. Два модификатора: `mob/EliteSC` — помощник `has(EntityArmoredMobSC mob, EliteModSC m)` (основной код или `scEliteMod2` у EntityWantedSC); все проверки модификаторов в EliteSC — через `has`. Пары `WantedRulesSC.PAIRS` (из Командир, Заряженный, Экранированный, Ветеран; без Ремонтника и Мстителя). Никакого роста силы от убийств.
5. Смерть (Forge `LivingDeathEvent`, LOWEST, не отменена, `EntityWantedSC`): каждому из `MobEventsSC.participants(mob, REWARD_SHARE)` (только рождён в Si-14 — `MobDropsSC.qualifies`): REWARD_CHIPS КЧ (вне 64 в сутки, как награда налёта), `ReputationSC.give(rec, REWARD_REP, SRC_WANTED, day)`, 1 Жетон розыска, строка `sc.wanted.reward`; всем в Подложке — `sc.wanted.down` (имя и кто); запись DEAD до следующей недели. Обычная добыча и трофей — как у элиты (MobDropsSC без изменений). Охрана после его смерти — обычные мобы (NBT снят).
6. Картограф (`TradeTableSC`): `CART_WANTED = "cartWanted"` — OUT_WANTED_HINT, 4 КЧ, уровень STRANGER, лимит 2 в сутки; до оплаты — отказ, если розыска нет (`sc.wanted.none`); после — `WantedSC.hint(p)`: «видели около X Z (+/-64) N мин назад» (случайный сдвиг ≤ HINT_SPREAD, на каждый раз свой).
7. Оружейник: близнецы услуг восстановления (`TradeTableSC`: `ARM_RESTORE_TOKENS_*` к каждому ARM_RESTORE_*) — стороны: трофей-сопоставитель + 3 Жетона + детали TrophyRestoreSC.parts (без КЧ); если сторон больше `MAX_SERVICE_SIDES` — `TrophyRestoreSC` даёт укороченный набор деталей для жетонов (не дешевле по сути: жетон дорог). `mob/TrophyRestoreSC` — функция `tokenParts(meta)`.
8. Капитан Города (Shift + ПКМ, `EntityWardenSC`): строка текущего розыска (`sc.wanted.captain`) или «на этой неделе тихо».

**Числа (TODO(balance)).** WantedRulesSC: RANGE 600..1500, ROAM_STEP 48 / 1200 тиков, SPAWN_NEAR 96, ESCORT 3, QUANTUM_PCT 60, REWARD_SHARE 0.10, REWARD_CHIPS 12, REWARD_REP 40, TOKENS_PER_RESTORE 3, HINT_CHIPS 4, HINT_SPREAD 64, имён 12, прозвищ 12.

**Новое.** Сущность id 26 «wantedRenegade»; предмет «wantedToken» (стопка 16; текстура `items/wantedToken.png`, скрипт `tools/gen_wanted_tex.py`: медный жетон с багровым знаком Отступников); RareRegistrySC.WANTED; ReputationSC.SRC_WANTED; TradeOfferSC.OUT_WANTED_HINT; NBT `scWanted`, `scEliteMod2`, DW 26. NEI «как получить» — `ObtainHandlerSC.ideaPage` (жетон). Конфиг `dimension.wanted` (есть).

**Справочник.** `BookIdeasSC.wanted` — статья «world.wanted»: объявление, где искать (кольцо, Картограф), модификаторы, награды, жетоны.

**Тексты (ключ = ru | en).**
- entity.SiliconAge.wantedRenegade.name = Отступник в розыске | Wanted Renegade
- item.siliconage.wantedToken.name = Жетон розыска | Wanted Token
- sc.tooltip.wantedToken = Знак за поимку именного Отступника | A token for bringing down a named Renegade
- sc.tooltip.wantedToken.details = Его получает каждый, кто нанёс Отступнику в розыске не меньше %s%% урона. | Everyone who dealt the wanted Renegade at least %s%% of its damage gets one.
- sc.tooltip.wantedToken.howto = %s жетона - Оружейник восстановит трофей без Кредит-чипов (детали - как обычно). | %s tokens - an Armourer restores a trophy with no Credit Chips (the parts as usual).
- sc.wanted.fullName = %s „%s“ | %s “%s”
- sc.wanted.name.0 … sc.wanted.name.11 = Хром-7 | Chrome-7; Кобальт-3 | Cobalt-3; Вольфрам-9 | Tungsten-9; Иридий-2 | Iridium-2; Осмий-5 | Osmium-5; Тантал-8 | Tantalum-8; Висмут-4 | Bismuth-4; Ниобий-6 | Niobium-6; Гафний-1 | Hafnium-1; Рений-7 | Rhenium-7; Селен-3 | Selenium-3; Индий-5 | Indium-5
- sc.wanted.alias.0 … sc.wanted.alias.11 = Резчик | Carver; Глушитель | Muffler; Ржавый | Rusty; Пробойник | Breaker; Тихий | Quiet; Шрам | Scar; Искра | Spark; Сварщик | Welder; Зуб | Fang; Полночь | Midnight; Обрыв | Ledge; Гвоздь | Nail
- sc.wanted.posted = Капитан „%s“ объявляет розыск: %s. Его видели в %s бл. от Города „%s“. | Captain “%s” posts a wanted Renegade: %s. Seen %s blocks from the City “%s”.
- sc.wanted.captain = Разыскиваем: %s. Кочует вокруг нашего Города. Где видели - спросите Картографа. | Wanted: %s. Roams round our City. Ask a Cartographer where he was seen.
- sc.wanted.quiet = На этой неделе розыска нет. | Nobody is wanted this week.
- sc.wanted.none = На этой неделе разыскивать некого. | There's nobody to hunt this week.
- sc.wanted.hint = %s видели около %s %s (+/-%s бл.) %s мин назад. | %s was seen near %s %s (+/-%s blocks) %s min ago.
- sc.wanted.down = %s повержен! Отличились: %s. | %s is down! Credit to: %s.
- sc.wanted.reward = За %s: %s КЧ, %s „Доверия“, Жетон розыска. | For %s: %s chips, %s “Trust”, a Wanted Token.
- sc.trade.offer.cartWanted = Где видели разыскиваемого | Where the wanted one was seen
- sc.trade.offer.armRestoreTokens = %s за жетоны | %s for tokens
- sc.nei.obtain.wantedToken = Награда за Отступника в розыске - каждому, кто нанёс не меньше %s%% урона. Рецептов нет. | The wanted Renegade's reward - for everyone who dealt at least %s%% of the damage. No recipes.
- sc.manual.world.wanted.title = Розыск | Wanted
- sc.manual.world.wanted.1 = Раз в игровую неделю Капитан Города объявляет розыск: один именной Отступник на весь мир, в броне Квант или Экзо, с %s Штурмовиками охраны. Кочует в %s-%s бл. от Города. | Once a game week a City's Captain posts a wanted one: one named Renegade in the whole world, in Quantum or Exo armour, with %s Troopers as an escort. It roams %s-%s blocks from the City.
- sc.manual.world.wanted.2 = У него два модификатора элиты из проверенных пар; от убийства игроков он не усиливается. Картограф за %s КЧ скажет, где его видели (+/-%s бл.). | It has two elite modifiers from tested pairs; killing players doesn't make it stronger. A Cartographer tells where it was seen for %s chips (+/-%s blocks).
- sc.manual.world.wanted.3 = Каждому, кто нанёс от %s%% урона, - %s КЧ, %s „Доверия“ и Жетон розыска; %s жетона заменяют Кредит-чипы Оружейнику при восстановлении трофея. | Everyone who dealt %s%% of the damage gets %s chips, %s “Trust” and a Wanted Token; %s tokens stand in for an Armourer's Credit Chips when restoring a trophy.

**Проверки.** Самотест `pd4`: `packLook` туда-обратно, `allowedPair` (6 пар, симметрично, без Ремонтника/Мстителя, не сам с собой), точка в кольце (чистая `WantedRulesSC.spot(seed, week, cx, cz)` → расстояние в 600..1500), имена 12/12 в обоих lang, награды. Мировой тест `WorldTestWantedSC` (Подложка, k = 4): запись-город, объявление (`WantedSC.postNow` — тестовый крючок), появление (`spawnNow`), убийство с журналом урона двух UUID (`MobEventsSC.restoreLog`) — награды и DEAD; новая неделя — новая запись. Вручную по LAN: оба бьют, оба получают жетон.

**Файлы:** `mob/WantedSC.java`, `mob/WantedRulesSC.java`, `mob/EntityWantedSC.java`, `mob/ItemWantedTokenSC.java`, `mob/EliteSC.java`, `mob/TrophyRestoreSC.java`, `mob/client/WantedClientSC.java`, `mob/client/RenderWantedSC.java` (новый), `settle/trade/TradeTableSC.java`, `settle/trade/TradeServerSC.java`, `settle/client/GuiTradeSC.java`, `settle/npc/EntityWardenSC.java`, `nei/ObtainHandlerSC.java`, `manual/BookIdeasSC.java`, `tools/gen_wanted_tex.py` (новый), `src/main/resources/assets/siliconage/textures/items/wantedToken.png` (новый), `debug/SelfTestIdeasSC.java`, `debug/WorldTestWantedSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-5. Ожившие поселения: Собиратели, Дрон-наладчик, мотыльки

**★** А. Двумя частями. 1) Собиратели: днём 1–2 выходят из Посёлков и Городов к друзам и фумаролам, только если игрок в 96 блоках; навредить им игроки не могут; на напавшего — зов о помощи с направлением; спасённый — +10 «Доверия» и небольшой подарок, раз в сутки на поселение. 2) Косметика: Дрон-наладчик над мастерскими и мотыльки в каньонах — только на своём компьютере, выключаются в настройках. Правило `dimension.gatherers`, клиентское `dimension.ambientLife`.

**Что сделать — часть 1.**

1. `settle/GathererSC` (заглушка с числами есть), FML WorldTickEvent (Подложка, END, раз в CHECK_TICKS = 200): правило вкл., не Мирная сложность, время суток WORK_FROM..WORK_UNTIL; для каждой записи размера SETTLEMENT/CITY (не посольство, не пост) с загруженным ядром и настоящим игроком (не творческий, не «Враг» здесь) в NEAR = 96 от ядра — если вышедших меньше PER_SIZE[size]: точка дня (раз в сутки, Settlement.extra "F" {spot, sd}): 200 случайных столбцов в кольце радиус купола + 8 .. + REACH (64), верхний твёрдый блок и до 3 вниз — друза (`SubstrateBlocksSC.druse`) или фумарола (`SubstrateWildBlocksSC.vent`, её мета); ближайшая; нет — в этот день не выходят. Собиратель появляется у ворот (P_GATE) внутри купола: `EntityGathererSC` с привязкой к поселению без слота (как мастер каравана: settlementId, slot -1, boundCore, вид LOOK, имя `NpcNamesSC.random`), NBT `scGatherer` = id выхода (память GathererSC).
2. `settle/npc/EntityGathererSC` (зарегистрирован): ИИ — плавание; идти к точке шагами без долгого поиска пути (как EntityAICaravanWalkSC — новый `settle/npc/ai/EntityAIGathererWalkSC`), stepHeight 2; у точки GATHER_TICKS «собирает» (взмахи, частицы друзы/пара); домой к воротам; в куполе — уходит (искры Наладчиков, без смерти). Враг ближе 12 (IMob) — бежит домой (`EntityAITunerFleeSC`-подобно), состояние ATTACKED. Неуязвим для игроков везде: `attackEntityFrom` — источник игрок (или стрела игрока) → false; ПКМ — строка (имя, «Собиратель», куда идёт), торговли нет. HP 20. Нет игрока в NEAR 400 тиков — уходит сразу (ничего не моделируется). Первый тик/каждые 20: выход не его (перезапуск) — уходит.
3. Нападение (Forge `LivingAttackEvent` на Собирателе от IMob): состояние ATTACKED; зов — всем настоящим игрокам в NEAR: `sc.gatherer.call` (имя, сторона света `sc.finder.dir.*`, расстояние), повтор не чаще 200 тиков. Спасение: был ATTACKED, все напавшие мертвы (или ушли дальше 24) 100 тиков, Собиратель жив — игрокам в 32 блоках, если сегодня в этом поселении спасения ещё не было (extra "F".rd): +RESCUE_REP (SRC_RESCUE) и подарок (1 из таблицы: 4 медных слитка, 2 танталовой пыли, 1 ячейка жидкого гелия, 1 Природная пластина — TODO(balance)) + строка `sc.gatherer.saved`. Погиб — `sc.gatherer.died` игрокам рядом, ничего больше (слота нет).
4. Рендер: `settle/client/RenderGathererSC` (RenderTunerSC + мешок/корзина за спиной, текстура `textures/entity/gatherer_sack.png`), регистрация в `AmbientLifeClientSC.register()`.

**Что сделать — часть 2 (только клиент, `settle/client/AmbientLifeClientSC`).**

5. Клиентский тик (раз в 20 тиков, только в Подложке, только при `SubstrateConfigSC.ambientLife()`): Дрон-наладчик — по `mc.theWorld.loadedTileEntityList` найти витрины мастерских (TileEntityShowcaseSC) в 48 блоках; над каждой группой (одна на 8 блоков) — не больше одного дрона, всего ≤ 6: клиентская частица `settle/client/EntityDroneFxSC` (EntityFX: маленький квадрокоптер — 2 кадра текстуры `textures/particles/tunerDrone.png`, мигающий бирюзовый диод), висит 3–5 блоков над витриной, покачивается, иногда опускается к ней «паять» (искра), живёт 600 тиков. Мотыльки — ночью (13000..23000), в биоме Скрайб-каньонов в 24 блоках от игрока, у Медных трасс и флоры: ≤ 24 частиц `settle/client/EntityMothFxSC` (машут, тянутся к свету — к ближайшему источнику света ≥ 8), текстура `textures/particles/moth.png`. Ничего не приходит с сервера, ничего не пишется в мир.

**Числа (TODO(balance)).** NEAR 96, REACH 64, PER_SIZE {0, 1, 2}, WORK 1000..10000, GATHER_TICKS 600, RESCUE_REP 10, дронов ≤ 6, мотыльков ≤ 24.

**Новое.** Сущность id 27 «gatherer»; Settlement.extra "F" {spot, sd, rd}; ReputationSC.SRC_RESCUE; текстуры `entity/gatherer_sack.png`, `particles/tunerDrone.png`, `particles/moth.png` (скрипт `tools/gen_ambient_tex.py`). Конфиги — есть.

**Справочник.** `BookIdeasSC.gatherers` — статья «world.gatherers» (когда выходят, зов, спасение, награда раз в сутки; дроны и мотыльки — настройка ambientLife).

**Тексты (ключ = ru | en).**
- entity.SiliconAge.gatherer.name = Собиратель | Gatherer
- sc.gatherer.line = %s • Собиратель - идёт к %s | %s • Gatherer - going to %s
- sc.gatherer.spot.druse = друзе | the druse
- sc.gatherer.spot.fumarole = фумароле | the fumarole
- sc.gatherer.call = Собиратель %s зовёт на помощь! %s, %s бл. | Gatherer %s calls for help! %s, %s blocks.
- sc.gatherer.saved = Собиратель %s спасён: %s „Доверия“ и подарок - %s. | Gatherer %s is saved: %s “Trust” and a gift - %s.
- sc.gatherer.savedToday = Собиратель %s спасён. Спасибо! (награда сегодня уже выдана) | Gatherer %s is saved. Thank you! (today's reward is given already)
- sc.gatherer.died = Собиратель %s погиб. | Gatherer %s is dead.
- sc.manual.world.gatherers.title = Собиратели | The Gatherers
- sc.manual.world.gatherers.1 = Днём из Посёлков (%s) и Городов (%s) выходят Собиратели - к друзам и фумаролам в %s бл. от купола, только пока игрок в %s бл. Игроки им навредить не могут. | By day Gatherers walk out of the Settlements (%s) and the Cities (%s) to the druses and fumaroles within %s blocks of the dome, only while a player is within %s blocks. Players can't hurt them.
- sc.manual.world.gatherers.2 = Если на Собирателя напали, в чат придёт зов с направлением. Спасённый даст +%s „Доверия“ и небольшой подарок - раз в сутки на поселение. | If a Gatherer is attacked, a call with the direction comes in the chat. Saved, he gives +%s “Trust” and a small gift - once a game day a settlement.
- sc.manual.world.gatherers.3 = Над мастерскими кружат Дроны-наладчики, ночью в каньонах летают мотыльки. Их видит только ваш компьютер; выключаются настройкой dimension.ambientLife. | Tuner drones circle over the workshops, at night moths fly in the canyons. Only your computer draws them; the setting dimension.ambientLife turns them off.

**Проверки.** Самотест `pd5`: PER_SIZE (Пост 0), окно времени, раз в сутки (`rescueDue(rd, day)` чистая), `ambientLife` приватная (уже проверено координатором), lang. Мировой тест `WorldTestGatherersSC` (Подложка, k = 5): запись Посёлка, друза в 30 блоках, тест-игрок рядом днём — выходит 1 Собиратель; удар игрока — HP не меняется; урон от моба — ATTACKED и зов; моб убит — спасение (+10 один раз, второй раз в тот же день — 0). Вручную: дроны и мотыльки видны, с ambientLife=false — нет; у второго игрока по LAN — свои.

**Файлы:** `settle/GathererSC.java`, `settle/npc/EntityGathererSC.java`, `settle/npc/ai/EntityAIGathererWalkSC.java` (новый), `settle/client/AmbientLifeClientSC.java`, `settle/client/RenderGathererSC.java` (новый), `settle/client/EntityDroneFxSC.java` (новый), `settle/client/EntityMothFxSC.java` (новый), `manual/BookIdeasSC.java`, `tools/gen_ambient_tex.py` (новый), `src/main/resources/assets/siliconage/textures/entity/gatherer_sack.png`, `.../textures/particles/tunerDrone.png`, `.../textures/particles/moth.png` (новые), `debug/SelfTestIdeasSC.java`, `debug/WorldTestGatherersSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-6. Повторный бой со Смотрителем: Протоколы Пробоя

**★** А. Протоколы Пробоя — карты усложнения, до трёх вставляются в Консоль вместе с Ключом: «Глухие катушки» (Точка ПЭ-1 не гасит катушки), «Короткий фитиль» (35 с на добивание вместо 60), «Нашествие» (волн в 1,5 раза больше), «Тень Затмения» (на половине HP в яму входит копия Затмения), «Помехи» (импульс ЭМИ по всей яме каждые 25 с). Каждый: +2 Крупицы и +25% к шансу трофеев; все три сразу — схема окраски «Пробой» навсегда. Каждое сочетание проверяется расчётом, опасные пары запрещены. Правило `dimension.breachProtocols`.

**Что сделать.**

1. `arena/BreachProtocolsSC` (числа и `allowed` есть): дописать чистую `danger(int mask)` — сумма DANGER[p] (DEAF 3, SHORT 2, SWARM 2, SHADOW 3, JAMMING 3 — TODO(balance)) + надбавки пар (DEAF+JAMMING +6, SHORT+SHADOW +6 — FORBIDDEN, прочие пары +0..1), DANGER_MAX = 9; `allowed` дополнительно требует `danger ≤ DANGER_MAX`; запрещённые пары превышают.
2. `arena/BreachFightSC.insert` из `ArenaWorldSC.start` (до проверки Ключа): в руке Протокол, правило вкл., арена READY/KEYED (не FIGHT, нет отсчёта) — бит в маску (`Arena.extra "P" {m}`), такой уже есть — `sc.protocol.refuse.same`, `!allowed` — `.pair`/`.max`; предмет снят (не в творческом), строка `sc.protocol.inserted` (n из 3), звук. Shift + ПКМ пустой рукой (не в бою) — все протоколы назад в инвентарь (`sc.protocol.removed`). Описание TE консоли (`TileEntityBreakdownConsoleSC.getDescriptionPacket`) несёт маску — `BlockArenaSC` рисует 3 огонька протоколов.
3. Действие в бою (маска — `BreachFightSC.mask(a)`):
   - DEAF_COILS: `mob/EntityContainmentCoilSC` — реакция на Точку ПЭ-1 (IEmpReactiveSC) игнорируется (искры «глухо»), катушка гаснет только расстрелом запаса;
   - SHORT_FUSE: `mob/WardenFightSC` — время добивания `finishTicks(a)` = SHORT_FUSE_TICKS (700) вместо FINISH_TICKS (1200), и DW_TIMER показывает его;
   - SWARM: размер волны оболочек `ceil(waveSize x 1.5)`, предел живых `ceil(WAVE_MAX x 1.5)`;
   - ECLIPSE_SHADOW: при первом падении здоровья Смотрителя ≤ SHADOW_AT — копия `EntityEclipseSC` на краю ямы, NBT `scShadow` = fightId: без записи RareRegistrySC, без добычи (`MobDropsSC.onDrops` — пусто для `scShadow`), уходит, когда `ArenaWorldSC.fightCurrent` ложно;
   - JAMMING: `BreachFightSC.tick` раз в JAMMING_EVERY (500) — всем игрокам в WardenRulesSC.SCALE_RADIUS от центра: подавление одной части брони, как импульс ЭМИ-бури (иммунитет 20 с соблюдается; под открытым небом не требуется — это яма), эффект BOSS_FX с новым `BossClientSC.FX_JAMMING`.
4. Конец боя: `ArenaWorldSC.bossDied` — маска стирается вместе с Ключом (протоколы потрачены); сброс (`reset`) — маска остаётся с Ключом. `mob/WardenLootSC` — каждому получателю добычи: + `crumbs(mask)` Крупиц (сверх суточного предела, как добыча босса), шанс трофеев x `trophyMul(mask)`; `scheme(mask)` — `FinaleRulesSC.unlock(player, SingularScheme.N)` и строка `sc.protocol.scheme` (уже открыта — ничего).
5. `util/SingularScheme`: дописать `N(0x14060E, 0xFF2E6E)` — «Пробой»; `unlockable()` — L, M, N (бит N = 4); палитра в `tools/gen_singular_tex.py` и все текстуры схемы N (броня, клинок, бур — как у L/M); `sc.singular.scheme.n`. Справочник `BookFinaleSC` (счёт открываемых схем — теперь 3).
6. Рецепты (`ModRecipesIdeasSC.pd6`, по 1 Матрице Пробоя — `RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX)` — на протокол; TODO(balance)): «Глухие катушки» — Матрица + 2 «Сердечник щита» + 2 катушки Nb3Sn; «Короткий фитиль» — Матрица + 2 TNT + 2 «Плазменный конденсат»; «Нашествие» — Матрица + 4 «Прошивка оболочки»; «Тень Затмения» — Матрица + 2 «Лом брони Сингулярный» + 1 Око Края; «Помехи» — Матрица + 4 «Индукционная нить» (бесформенные).
7. Предмет: подсказка TooltipSC по мете, текстуры `items/breachProtocol_<key>.png` (скрипт `tools/gen_protocol_tex.py`: карта-чип с символом протокола в цветах Пробоя), NEI — рецепты обычные.

**Числа (TODO(balance)).** CRUMBS_EACH 2, TROPHY_BONUS_EACH 0.25, SHORT_FUSE_TICKS 700, SWARM_MUL 1.5, SHADOW_AT 0.5, JAMMING_EVERY 500, DANGER и пары выше.

**Новое.** Предмет «breachProtocol» меты 0..4; Arena.extra "P" {m}; SingularScheme.N; NBT `scShadow`; BossClientSC.FX_JAMMING. Сеть — BOSS_FX и описание TE. Конфиг — есть.

**Справочник.** `BookIdeasSC.protocols` — статья «world.protocols» (5 протоколов, как вставить/вынуть, запрещённые пары, награды, схема «Пробой»).

**Тексты (ключ = ru | en).**
- item.siliconage.breachProtocol.deafCoils.name = Протокол „Глухие катушки“ | Protocol “Deaf Coils”
- item.siliconage.breachProtocol.shortFuse.name = Протокол „Короткий фитиль“ | Protocol “Short Fuse”
- item.siliconage.breachProtocol.swarm.name = Протокол „Нашествие“ | Protocol “Swarm”
- item.siliconage.breachProtocol.eclipseShadow.name = Протокол „Тень Затмения“ | Protocol “Eclipse's Shadow”
- item.siliconage.breachProtocol.jamming.name = Протокол „Помехи“ | Protocol “Jamming”
- sc.tooltip.breachProtocol.deafCoils = Точка ПЭ-1 не гасит катушки удержания | A PE-1 Point no longer darkens the containment coils
- sc.tooltip.breachProtocol.shortFuse = На добивание - %s с вместо %s | %s s to finish it instead of %s
- sc.tooltip.breachProtocol.swarm = Волн Пустых оболочек в %s раза больше | %s times the Hollow Shell waves
- sc.tooltip.breachProtocol.eclipseShadow = На половине здоровья в яму входит копия Затмения | At half health a copy of the Eclipse comes into the pit
- sc.tooltip.breachProtocol.jamming = Импульс ЭМИ по всей яме каждые %s с | An EMP pulse over the whole pit every %s s
- sc.tooltip.breachProtocol.details = Вставляется в Консоль Пробоя до Ключа, до %s протоколов. Каждый: +%s Крупицы и +%s%% к шансу трофеев; все %s - схема „Пробой“ навсегда. Тратится вместе с Ключом. | Goes into the Breakdown Console before the Key, up to %s protocols. Each: +%s Crumbs and +%s%% to the trophies' chance; all %s - the “Breach” scheme for good. Spent with the Key.
- sc.tooltip.breachProtocol.howto = Shift + ПКМ по Консоли пустой рукой - вынуть все. Не вместе: „Глухие катушки“ с „Помехами“, „Короткий фитиль“ с „Тенью Затмения“. | Sneak + right-click the Console empty-handed - take them all out. Never together: “Deaf Coils” with “Jamming”, “Short Fuse” with “Eclipse's Shadow”.
- sc.protocol.inserted = Протокол „%s“ вставлен (%s из %s). | Protocol “%s” is in (%s of %s).
- sc.protocol.removed = Протоколы вынуты: %s. | Protocols taken out: %s.
- sc.protocol.refuse.off = Протоколы на этом сервере выключены. | Protocols are off on this server.
- sc.protocol.refuse.same = Такой протокол уже вставлен. | That protocol is in already.
- sc.protocol.refuse.max = Больше %s протоколов не вставить. | No more than %s protocols.
- sc.protocol.refuse.pair = „%s“ и „%s“ вместе не вставить - бой станет непроходимым. | “%s” and “%s” can't go in together - the fight couldn't be won.
- sc.protocol.refuse.busy = Бой уже идёт. | The fight is on already.
- sc.protocol.scheme = Схема окраски „%s“ открыта навсегда! | The paint scheme “%s” is yours for good!
- sc.singular.scheme.n = Пробой | Breach
- sc.manual.world.protocols.title = Протоколы Пробоя | Breach Protocols
- sc.manual.world.protocols.1 = Протоколы делают бой со Смотрителем тяжелее: до %s штук вставляются в Консоль вместе с Ключом и тратятся с ним. | The protocols make the Warden's fight harder: up to %s go into the Console with the Key and are spent with it.
- sc.manual.world.protocols.2 = Каждый даёт +%s Крупицы и +%s%% к шансу трофеев; за все %s сразу - схема окраски „Пробой“ навсегда. Опасные пары Консоль не принимает. | Each gives +%s Crumbs and +%s%% to the trophies' chance; all %s at once - the “Breach” paint scheme for good. The Console refuses the dangerous pairs.

**Проверки.** Самотест `pd6`: все 32 маски — `allowed` ⇔ (≤ 3, нет запрещённой пары, danger ≤ max); 4 допустимые тройки; `crumbs/trophyMul/scheme`; ключи/меты; `SingularScheme.N.unlockable()` и бит 4; lang 5 протоколов. Мировой тест `WorldTestProtocolsSC` (Подложка, арена теста Ф5 или своя, k = 6): вставка двух протоколов, третьего запрещённого — отказ; бой с SHORT_FUSE — `finishTicks` 700; глухая катушка не гаснет от Точки; смерть босса — маска стёрта, Крупицы +4 у тест-получателя. Вручную по LAN: «Помехи» бьют обоих.

**Файлы:** `arena/BreachProtocolsSC.java`, `arena/BreachFightSC.java`, `arena/ItemBreachProtocolSC.java`, `arena/ArenaWorldSC.java`, `tileentity/TileEntityBreakdownConsoleSC.java`, `block/BlockArenaSC.java`, `mob/WardenFightSC.java`, `mob/WardenLootSC.java`, `mob/EntityContainmentCoilSC.java`, `mob/EntityEclipseSC.java`, `mob/MobDropsSC.java`, `mob/client/BossClientSC.java`, `util/SingularScheme.java`, `tools/gen_singular_tex.py`, `tools/gen_protocol_tex.py` (новый), текстуры `items/breachProtocol_*.png` и схемы N (новые), `init/ModRecipesIdeasSC.java` (pd6), `manual/BookIdeasSC.java`, `manual/BookFinaleSC.java`, `debug/SelfTestIdeasSC.java`, `debug/SelfTestFinaleSC.java` (счёт схем), `debug/WorldTestProtocolsSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-7. Отвоевать Мёртвый Узел: ретрансляционный пост

**★** А. Игрок уровня «Свой» покупает у Старейшины Ретранслятор, ставит его на площади зачищенного Узла и 5 минут держит оборону против 3 волн. Удержал — Наладчики строят маленький пост с куполом и Причалом, цель моста; патрули тут больше не появляются; на пост бывают налёты, проигранный — уничтожает его. Один пост на участок 4 x 4 перекрёстка, не ближе 512 блоков к поселению, только на пустом месте, не поверх построек игрока. Правило `dimension.relayPosts`.

**Что сделать.**

1. Покупка: `TradeTableSC` `ELDER_RELAY = "elderRelay"` — purchase PRICE_CHIPS 24 КЧ → 1 Ретранслятор (`IdeaBlocksSC.relay(1)`), уровень OWN, лимит 1 в сутки. Рецепта нет; NEI «как получить» — `ideaPage`.
2. Установка (`settle/RelayPostSC`, Forge `BlockEvent.PlaceEvent` блока relay мета RELAY): отказ (событие отменено, предмет остаётся, строка) по `refusal(w, p, x, y, z)`: правило выкл.; не Подложка; не площадь (`RuinLayoutSC.nodeNear` и ≤ RelayRulesSC.PLAZA от центра перекрёстка, высота — пол Узла ±2); арена; Хранилище под этим Узлом (`VaultLayoutSC` — люк в площади, `sc.relay.refuse.vault`); в регионе (`RelayRulesSC.regionKey`) уже есть пост (RELAY с extra "Y" того же региона); ядро поселения ближе SETTLEMENT_GAP 512; постройка игрока в отпечатке поста (купол r 12 + Причал 7 x 7 на 17 блоков по оси каньона): любой блок, кроме воздуха, породы Подложки, блоков руин (RuinBlocksSC), трасс; уже идёт оборона в этом Узле. FakePlayer — отказ.
3. Оборона (FML WorldTickEvent, Подложка, раз в 20 тиков; память, как RaidRunSC): HOLD_TICKS 6000; волны 0, 2000, 4000 тиков (или раньше, когда прошлая добита) — `RaidRulesSC.wave(n, OUTPOST, r)`, появляются по осям каньонов в 24..40 от Ретранслятора, `SpawnDirectorSC.spawnAt` (рождены в Si-14), цель — ближайший защитник, иначе идут к Ретранслятору; защитники — настоящие игроки в DEFEND_REACH 48; у Ретранслятора RELAY_HP 100: налётчик вплотную бьёт его (5 за удар); проигрыш — HP 0 или 1200 тиков никого рядом или Ретранслятор сломан: Ретранслятор выпадает предметом (можно снова), налётчики — обычные мобы, строка `sc.relay.lost`. Каждую минуту — `sc.relay.left`. Перезапуск сервера — оборона пропала, Ретранслятор стоит (ПКМ — снова начать).
4. Удержал: пост — запись реестра (`SettlementRegistrySC.add`): size RELAY, radius 12, x/y/z — место Ретранслятора, floorY — пол Узла, seed `SubstrateTerrainSC.mix(seed, i, j)`, имя (`NpcNamesSC` по сиду), hasDock, dock — 17 блоков по оси каньона (маяк), dockZone 5, slots — 2 SENTRY (NEVER), points: P_PLAZA/P_SQUARE — ядро, P_GATE — в сторону Причала, extra "Y" {i, j, at}. Блоки: ядро (`SettlementBlocksSC.core`, `TileEntitySettlementCoreSC.setup(id, 12, floorY)`) на месте Ретранслятора, над ним MAST (TE: postId), плиты и маяк Причала 7 x 7 (`SettlementBlocksSC.dock`), 4 фонаря-колонны по краю купола. Защитникам: +HELD_REP (SRC_RELAY), пост — в knownDocks (цель моста), строка `sc.relay.built`.
5. Патрули: `mob/SpawnDirectorSC` — места PLACE_NODE в зоне Узла с постом (`RelayPostSC.claimed(w, i, j)`) не используются (оболочки-патрули и прочее «узловое»).
6. Налёты: пост — обычная запись для `RaidSC` (ночью, защитник рядом); таблицы — `tableIndex()` (как Пост); проигранный налёт на RELAY (`RaidRunSC.lose`) — `RelayPostSC.destroy(w, s)` вместо закрытия торговли: блоки ядра/мачты/Причала → металл руин, запись `SettlementRegistrySC.remove(id)` (сначала убрать ядро — иначе оно восстановит запись), строка `sc.relay.destroyed` всем в Подложке. Победа — обычная награда.
7. Исключения: `embassy/FlightTargetsSC` — причалы постов не цели рейсов (`!SettlementSC.isRelay`); караваны (`settle/caravan/CaravanSC`/`CaravanRulesSC`) — посты не цель; `RosterSC` — для RELAY нет Старейшины/жителей, только стражи; Клон-капсула работает (стражи возвращаются).
8. Блок: RELAY — ломается киркой (если обороны нет), твёрдость 5; MAST — неломаемый, без дропа, скрыт в NEI (сделано), подсказки по мете (TooltipSC), текстуры `blocks/relay_*.png` (скрипт `tools/gen_relay_tex.py`: мачта с антеннами и бирюзовым огнём, в цветах Наладчиков).

**Числа (TODO(balance)).** RelayRulesSC: PLAZA 10, REGION 4, SETTLEMENT_GAP 512, PRICE_CHIPS 24, BUY_LEVEL OWN, HOLD_TICKS 6000, WAVES 3, DEFEND_REACH 48, ABANDON_TICKS 1200, HELD_REP 40; RELAY_HP 100, удар 5.

**Новое.** Блок «relay» (меты 0/1), TE «SiliconAge.relay» (NBT `scRelay` {by, at, post}); SettlementSizeSC.RELAY; Settlement.extra "Y"; ReputationSC.SRC_RELAY. Сеть — нет (строки чата). Конфиг — есть.

**Справочник.** `BookIdeasSC.relayPosts` — статья «world.relayPosts» (покупка, где ставить и где нельзя, оборона, пост и Причал, налёты на пост).

**Тексты (ключ = ru | en).**
- tile.siliconage.relay.relay.name = Ретранслятор | Relay
- tile.siliconage.relay.mast.name = Мачта поста | Post Mast
- sc.tooltip.relay.relay = Сердце будущего поста Наладчиков | The heart of a Tuners' post to be
- sc.tooltip.relay.relay.details = Ставится на площадь зачищенного Мёртвого Узла: %s мин обороны от %s волн - и Наладчики построят пост с куполом и Причалом. | Set it up on a cleared Dead Node's plaza: %s min of defence against %s waves - and the Tuners build a post with a dome and a dock.
- sc.tooltip.relay.relay.howto = Покупается у Старейшины с „%s“. Один пост на участок %s x %s перекрёстка, не ближе %s бл. к поселению. | Bought from an Elder from “%s”. One post to a %s x %s crossings' area, no closer than %s blocks to a settlement.
- sc.tooltip.relay.mast = Мачта поста-ретранслятора | A relay post's mast
- sc.tooltip.relay.mast.details = Держит купол и Причал поста. Проигранный налёт уничтожит пост. | Holds the post's dome and dock. A lost raid destroys the post.
- sc.tooltip.relay.mast.howto = Только у поста; не ломается. | Only a post has one; it doesn't break.
- sc.settle.size.relay = Пост-ретранслятор | Relay post
- sc.trade.offer.elderRelay = Ретранслятор | Relay
- sc.relay.refuse.off = Посты-ретрансляторы на этом сервере выключены. | Relay posts are off on this server.
- sc.relay.refuse.place = Ретранслятор ставят только на площадь Мёртвого Узла в Подложке. | The Relay goes only on a Dead Node's plaza in the Substrate.
- sc.relay.refuse.region = На этом участке уже есть пост: %s. | This area has a post already: %s.
- sc.relay.refuse.near = Слишком близко к поселению „%s“: нужно не ближе %s бл. | Too close to “%s”: no closer than %s blocks.
- sc.relay.refuse.built = Здесь стоят чужие блоки - пост строят только на пустом месте. | Somebody's blocks stand here - a post is built on clear ground only.
- sc.relay.refuse.vault = Под этим Узлом Хранилище - его люк нельзя закрыть постом. | A Vault lies under this Node - a post can't cover its hatch.
- sc.relay.refuse.busy = Здесь уже держат оборону. | A defence is on here already.
- sc.relay.start = Ретранслятор включён! Держите оборону %s мин - Отступники уже идут. | The Relay is on! Hold for %s min - the Renegades are coming.
- sc.relay.wave = Волна %s из %s. | Wave %s of %s.
- sc.relay.left = До постройки поста: %s мин. | The post is built in %s min.
- sc.relay.lost = Оборона проиграна - Ретранслятор сорван. Его можно поставить снова. | The defence is lost - the Relay is knocked off. It can be set up again.
- sc.relay.built = Наладчики построили пост „%s“ у %s %s. Его Причал - теперь цель моста. | The Tuners have built the post “%s” at %s %s. Its dock is a bridge target now.
- sc.relay.destroyed = Пост „%s“ пал - Отступники его разрушили. | The post “%s” has fallen - the Renegades destroyed it.
- sc.nei.obtain.relay = Продаёт Старейшина поселения Подложки с „%s“ за %s КЧ. | Sold by a Substrate Elder from “%s” for %s chips.
- sc.manual.world.relayPosts.title = Посты-ретрансляторы | Relay posts
- sc.manual.world.relayPosts.1 = Ретранслятор (Старейшина, „%s“, %s КЧ) ставят на площадь зачищенного Мёртвого Узла и держат оборону %s мин против %s волн. | The Relay (an Elder, “%s”, %s chips) is set up on a cleared Dead Node's plaza and held for %s min against %s waves.
- sc.manual.world.relayPosts.2 = Удержали - Наладчики строят пост с куполом и Причалом: цель моста, патрули Узла больше не приходят. На пост бывают налёты; проигранный налёт его уничтожит. | Held - the Tuners build a post with a dome and a dock: a bridge target, the Node's patrols never come again. Posts get raided; a lost raid destroys one.
- sc.manual.world.relayPosts.3 = Один пост на участок %s x %s перекрёстка, не ближе %s бл. к поселению, только на пустом месте - не поверх ваших построек и не над Хранилищем. | One post to a %s x %s crossings' area, no closer than %s blocks to a settlement, on clear ground only - never over your buildings or a Vault.

**Проверки.** Самотест `pd7`: `regionKey` (0..3 одинаковы, 4 — другой, отрицательные), `tableIndex` RELAY = 0, `SettlementRegistrySC.remove` (RELAY — удалён и не найден в `candidates`; обычный — отказ), ключи lang. Мировой тест `WorldTestRelaySC` (Подложка, k = 7, свой перекрёсток): установка вне площади — отказ; на площади — оборона; `RelayPostSC.finishNow` (тестовый крючок) — запись RELAY с Причалом, ядро, мачта; `si14Targets` после knownDocks — есть причал поста; второй Ретранслятор в регионе — отказ; проигранный налёт (`RaidSC.startNow` + `lose`) — запись удалена, ядра нет.

**Файлы:** `settle/RelayPostSC.java`, `settle/RelayRulesSC.java`, `block/BlockRelaySC.java`, `block/ItemBlockRelaySC.java`, `tileentity/TileEntityRelaySC.java`, `hazard/RaidSC.java`, `hazard/RaidRunSC.java`, `hazard/RaidRulesSC.java`, `mob/SpawnDirectorSC.java`, `settle/trade/TradeTableSC.java`, `embassy/FlightTargetsSC.java`, `settle/caravan/CaravanSC.java`, `settle/RosterSC.java`, `nei/ObtainHandlerSC.java`, `manual/BookIdeasSC.java`, `tools/gen_relay_tex.py` (новый), текстуры `blocks/relay_*.png` (новые), `debug/SelfTestIdeasSC.java`, `debug/WorldTestRelaySC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ПД-8. Тоннели под плато

**★** А. Тоннели — прямые медные шины 5 x 4 под плато на высоте Y 22–28, с развязками, сундуками и мимиками; входы — в стенах каньонов (дно каньонов на той же высоте); в бурю — укрытие и дорога. Только на ещё не открытой земле. Правило `dimension.tunnels`.

**Что сделать.**

1. `ruin/TunnelLayoutSC` (чистые; сигнатуры есть): `has(seed, cellX, cellZ, alongX)` — CHANCE_PCT по хэшу ячейки (своя соль), никогда: ячейка спавна (0, 0) и кольцо SubstrateCellsSC.SPAWN_RING, Разлом (`SubstrateCellsSC.pureKind` = RIFT), ячейка с площадкой поселения (`SettlementLayoutSC.site` её региона) или литографией (`RuinLayoutSC.lithoSite`); `floorY` — FLOOR_MIN..FLOOR_MAX по хэшу; линия тоннеля — на смещении ±48 от середины ячейки поперёк (по хэшу); концы — у стены каньона: от `x = cell·256 + canyonHalfWidth + 1` до `(cell+1)·256 − canyonHalfWidth − 1` (вдоль X; вдоль Z — так же); на концах пол = `SubstrateTerrainSC.canyonFloorY` там, подъём/спуск RAMP блоков на 1 к floorY; конец не открывается, если в 16 блоках лагерь (`CampLayoutSC.siteNear`) — там глухая стена.
2. `ruin/MapGenTunnelSC`: `canSpawnStructureAtCoords` — чанк `startChunk(cell)`, `RuinSC.generates()`, `SubstrateConfigSC.tunnels`, `has(...)` хотя бы по одной оси; `getStructureStart` — explored (`StructureRuinStartSC.explored` по коробке обоих тоннелей) → пустой старт с CellX/CellZ; иначе куски: `ComponentTunnelSC` отрезками ≤ 64 блоков (новые классы `ruin/ComponentTunnelSC`, `ruin/ComponentTunnelJunctionSC`), развязка JUNCTION x JUNCTION x 5 в пересечении двух тоннелей ячейки (колонна-светильник, сундук); зарегистрировать имена кусков в `registerStructures` (PIECE_PREFIX + «Seg», «Junction»); `covers(x, z, margin)` — коробки кусков (для вырезателя).
3. Кусок: пол — металл руин (RuinBlocksSC), стены и потолок — порода `SubstrateBlocksSC.rock` мета COPPER_TRACE (5) в 1 блок, каждые 8 блоков — TRACE_NODE (свечение — помощником `ChunkProviderSubstrateSC.glow` / его статической версией), внутри воздух WIDTH x HEIGHT; ниша каждые CHEST_EVERY: сундук `RuinLootSC.tunnelChest` (новая функция: как nodeChest, слабее; Журналов нет) или в MIMIC_PCT случаях мимик (`EntityMimicSC`, как в `ComponentLitHallSC`: дом — ниша); строится только в окне populate по `StructureBoundingBox` куска; не вырезать сквозь блоки других структур (Хранилища, литографии — коробки уже исключены выбором ячеек).
4. Вырезатель (`dim/MapGenSubstrateCavesSC`): `keepOut` + тоннели (не резать «виа» через тоннель — `tunnels.covers`), из `ChunkProviderSubstrateSC` передать `tunnels()` (одна строка в конструкторе провайдера — разрешено ПД-8).
5. Буря: ЭМИ-импульс бьёт только под открытым небом — в тоннеле укрытие само собой (проверить в мировом тесте). Спавн: SpawnDirector может ставить мобов в тоннели (тёмные) — так и задумано, без изменений.

**Числа (TODO(balance)).** WIDTH 5, HEIGHT 4, FLOOR 22..28, RAMP 3, CHANCE_PCT 35 на ось, JUNCTION 9, CHEST_EVERY 48, MIMIC_PCT 30, смещение линии ±48.

**Новое.** Структура «SCSi14Tunnel», куски «SCSi14TunSeg», «SCSi14TunJunction». Блоков и предметов нет. Конфиг `dimension.tunnels` (есть).

**Справочник.** `BookIdeasSC.tunnels` — статья «world.tunnels» (где искать входы, высота, укрытие в бурю, сундуки и мимики, только новая земля).

**Тексты (ключ = ru | en).**
- sc.manual.world.tunnels.title = Тоннели под плато | The tunnels under the plateaus
- sc.manual.world.tunnels.1 = Под плато тянутся прямые медные шины Литографов - тоннели %s x %s на высоте Y %s-%s. Входы открываются прямо в стенах каньонов: дно каньонов на той же высоте. | Straight copper busbars of the Lithographers run under the plateaus - tunnels %s x %s at Y %s-%s. Their entrances open right in the canyons' walls: the canyons' floors lie at the same height.
- sc.manual.world.tunnels.2 = В ЭМИ-бурю тоннель - и укрытие, и дорога: импульс бьёт только под открытым небом. На развязках и в нишах - сундуки; часть ниш занимают мимики. | In an EMP storm a tunnel is both a shelter and a road: the pulse strikes only under the open sky. The junctions and niches hold chests; mimics take some of the niches.
- sc.manual.world.tunnels.3 = Тоннели появляются только в ещё не открытой земле (правило dimension.tunnels). | Tunnels appear only in land not explored yet (the rule dimension.tunnels).

**Проверки.** Самотест `pd8`: `has` — никогда в ячейке спавна, в Разломе, в ячейке поселения/литографии; доля ячеек ≈ CHANCE_PCT (±5% на 2000 ячеек); `floorY` в 22..28; `startChunk` — середина ячейки, все куски ячейки в 8 чанках от него. Мировой тест `WorldTestTunnelsSC` (Подложка, k = 8, новая земля): найти ячейку с тоннелем, сгенерировать чанки, проверить воздух 5 x 4 на floorY, медные стены, вход в стене каньона; в бурю `EmpStormSC` не бьёт стоящего в тоннеле (крючок проверки «под небом»).

**Файлы:** `ruin/TunnelLayoutSC.java`, `ruin/MapGenTunnelSC.java`, `ruin/ComponentTunnelSC.java` (новый), `ruin/ComponentTunnelJunctionSC.java` (новый), `ruin/RuinLootSC.java`, `dim/MapGenSubstrateCavesSC.java`, `dim/ChunkProviderSubstrateSC.java` (одна строка `caves.keepOut`), `manual/BookIdeasSC.java`, `debug/SelfTestIdeasSC.java`, `debug/WorldTestTunnelsSC.java` (новый), `debug/WorldTestIdeasSC.java`, `README.md`.

---------------------------------------------------------------------------------------------------

## ОМ-1. Экзо-установка в Подложке: свои руды вместо алмазов

**★** А. Свой стол руд Подложки: тантал x2; бадделеит, сперрилит, аргиродит, вольфрамит и монацит x1,5; медь и олово x0,5 (как SubstrateOreTableSC.veinMultiplier), без ванильных руд; Глубинный сканер добавляет Монокристалл и друзы, без руд других модов. Явное предупреждение, когда модуль удержания чанков ставят в Подложке (не запрет).

**Что сделать.**

1. `machine/ExoOreTableSC`: `substrateEntries()` — только руды мода (`ModBlocks.oreSC`, мета OreEntry; линза — та же мета), вес `round(defaultVeinSize x 4 x SubstrateOreTableSC.veinMultiplier(ore))`; `substrateDeep()` — Монокристалл (`SubstrateBlocksSC.rock` мета MONOCRYSTAL) и Кристаллическая друза (`SubstrateBlocksSC.druse` мета 0) — вместе SUBSTRATE_DEEP_SHARE 10% от стола (по половине, не меньше 1); линз у них нет.
2. `tileentity/TileEntityQuarrySC.exoEntries()`: в Подложке (`SubstrateSC.is(worldObj)` — и на клиенте для экрана) — `substrateEntries()` (+ `substrateDeep()` с модулем Глубинного сканера) вместо ванильно-модового стола и чужих руд. Фильтр и линзы работают как прежде. У стоящих в Подложке установок выход меняется сам (в CHANGELOG — у сводящего).
3. Экран установки (`inventory/GuiQuarrySC`, вкладка «Линзы» — таблица шансов): заголовок «Подложка: свои руды», в Подложке — новая таблица (она уже берётся из `exoEntries`).
4. Предупреждение о модуле удержания чанков (`ItemQuarryModuleSC.Kind.CHUNK_LOADER`) в Подложке: `inventory/ContainerQuarrySC` — когда игрок кладёт модуль в слот карьера/установки, стоящей в Подложке (сервер, после `slotClick`, сравнение числа модулей до/после) — строка в чат (жёлтая) `sc.quarry.chunkLoader.substrate`, один раз на вставку; подсказка модуля (`item/ItemQuarryModuleSC`) — строка об этом всегда в Shift-описании; вкладка «Функции» — значок предупреждения у строки удержания чанков с подсказкой, если установка в Подложке. Модуль работает как работал.
5. Справочник: статья карьера/Экзо-установки (`manual/BookContent`) — абзац «в Подложке — свои руды» с таблицей множителей и предупреждением о модуле. README — строка в разделе Подложки.

**Числа (TODO(balance)).** SUBSTRATE_DEEP_SHARE 10%.

**Новое.** Нет предметов, NBT, конфигов.

**Тексты (ключ = ru | en).**
- sc.quarry.chunkLoader.substrate = Внимание: в Подложке модуль удержания чанков держит загруженным весь её мир - мобов, бури, налёты. Это заметная нагрузка на хоста. | Note: in the Substrate the Chunk Loader module keeps its whole world loaded - the mobs, the storms, the raids. That is a noticeable load on the host.
- sc.tooltip.quarryModule.chunkLoader.substrate = В Подложке держит загруженным весь её мир (нагрузка на хоста). | In the Substrate it keeps that whole world loaded (a load on the host).
- sc.gui.quarry.substrateTable = Подложка: свои руды | The Substrate: its own ores
- sc.manual.quarry.exo.substrate = В Подложке Экзо-установка поднимает только её руды: тантал x%s; бадделеит, сперрилит, аргиродит, вольфрамит и монацит x%s; медь и олово x%s. Глубинный сканер добавляет Монокристалл и друзы (руд других модов там нет). | In the Substrate the Exo rig brings up its ores only: tantalum x%s; baddeleyite, sperrylite, argyrodite, wolframite and monazite x%s; copper and tin x%s. The deep scanner adds Monocrystal and druses (no other mods' ores there).
- sc.manual.quarry.exo.substrateChunks = Модуль удержания чанков в Подложке держит загруженным весь её мир - лучше без него. | In the Substrate the Chunk Loader module keeps its whole world loaded - better do without it.

**Проверки.** Самотест `om1`: в `substrateEntries` нет ванильных руд; вес тантала / вес меди = 4 x (defaultVeinSize тантала / меди); все 16 руд мода есть; `substrateDeep` — 2 записи, без чужих руд. Мировой тест `WorldTestRigSubstrateSC` (Подложка, k = 9): установка с энергией творчества — 200 выемок: только руды мода, ни одного алмаза; вставка модуля удержания чанков через контейнер тест-игрока — строка предупреждения (или флаг).

**Файлы:** `machine/ExoOreTableSC.java`, `tileentity/TileEntityQuarrySC.java`, `inventory/ContainerQuarrySC.java`, `inventory/GuiQuarrySC.java`, `item/ItemQuarryModuleSC.java`, `manual/BookContent.java`, `README.md`, `debug/SelfTestIdeasSC.java`, `debug/WorldTestRigSubstrateSC.java` (новый), `debug/WorldTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-2. Добыча Подложки в домашних машинах

**★** А. Все рецепты: лом → гафниевая пыль в дробилке; нить → катушка Nb3Sn в прокатном станке; контур → Гелиевый контур; конденсат → аргон в центрифуге; Природные пластины → затравка для установки Чохральского; нанитовая паста чинит изношенные инструменты мода (фотомаски, мишени, головки бура). Ремонт пастой не выходит дешевле новой фотомаски. Продажа Наладчикам остаётся, цены не меняются.

**Что сделать (рецепты — `ModRecipesIdeasSC.om2`, TODO(balance)).**

1. Дробилка: 1 «Лом брони Сингулярный» (`MobItemsSC.loot(SINGULAR_SCRAP)`) → 1 Гафниевая пыль (`ModItems.dust` HAFNIUM) + малая пыль тантала (как побочный, если MachineRecipe поддерживает второй выход; иначе только гафний), 200 тиков.
2. Прокатный станок (форма MOLD_COIL): 4 «Индукционная нить» → 1 катушка Nb3Sn (`nb3SnCoil`), 200 тиков (рядом с рецептом 2 слитка Nb3Sn → катушка; формы и выбор по входу — как в `ModRecipesInfrastructure.press`).
3. Верстак, бесформенный: 1 «Выкипевший контур» + 1 ячейка жидкого гелия + 1 кабель сверхпроводника → 1 «Гелиевый контур» (`heLoopModule`) — без контроллера (обычный рецепт: ячейка + кабель + контроллер — остаётся).
4. Центрифуга: 1 «Плазменный конденсат» → 500 мБ аргона (жидкий выход), 200 тиков (если центрифуга не отдаёт жидкость — Газоразделитель воздуха/Хлор-щелочной: выбрать машину с жидким выходом и записать в справочник).
5. Верстак, бесформенный: 2 «Природная пластина» + 1 электронный кремний → 1 Затравочный кристалл (`SCToolType.SEED_CRYSTAL`) — вместо алмаза.
6. Хим. реактор: 4 «Нанитовая пыль» + 1 слизь (Items.slime_ball) → 1 «Нанитовая паста» (`IdeaItemsSC.nanitePaste`), 200 тиков.
7. Ремонт пастой — новый `init/PasteRepairRecipeSC implements IRecipe` (RecipeSorter: `"siliconage:pasteRepair"`, SHAPELESS; `GameRegistry.addRecipe`): в сетке ровно один изношенный инструмент из списка — `ItemToolSC` PHOTOMASK, SPUTTER_TARGET_COPPER/ALUMINIUM/TUNGSTEN, DIAMOND_WIRE, DIAMOND_BLADE, EUV_MASK (ОМ-4) и головка бура `ItemDrillHeadSC` — и k паст, k = 1 + число прошлых ремонтов (NBT инструмента `scPasteRepairs`); результат — тот же инструмент, износ уменьшен на REPAIR_SHARE 25% от максимума (не ниже 0), счётчик +1; больше MAX_REPAIRS 3 — не чинится («изношен окончательно» в подсказке). Итого за всю жизнь не больше +75% одного нового инструмента за 1 + 2 + 3 = 6 паст (24 нанитовой пыли) — не дешевле нового (TODO(balance): сверить с ценой фотомаски в самотесте).
8. Подсказки: `ItemNanitePasteSC` (TooltipSC: что, сколько чинит, «как сделать»); у инструментов (`item/ItemToolSC`, `item/ItemDrillHeadSC`) — строка «ремонтов пастой: n из 3»; у добычи — новые «как применить» (lang, см. ниже); текстура `items/nanitePaste.png` (скрипт `tools/gen_paste_tex.py`: тюбик с зелёной светящейся пастой в стиле предметов добычи).
9. Справочник: статья добычи Подложки (`BookWorldSC`, «sc.manual.world.loot.*») и материалы (`BookContent`) — куда идёт каждая добыча дома.

**Новое.** Предмет «nanitePaste» (зарегистрирован); рецепт `PasteRepairRecipeSC`; NBT инструмента `scPasteRepairs`.

**Тексты (ключ = ru | en).**
- item.siliconage.nanitePaste.name = Нанитовая паста | Nanite Paste
- sc.tooltip.nanitePaste = Ремонтная паста из сгоревших нанитов | A repair paste of burnt-out nanites
- sc.tooltip.nanitePaste.details = В сетке крафта с изношенным инструментом мода (фотомаска, мишень, алмазная проволока или диск, ЭУФ-маска, головка бура) возвращает %s%% прочности. Каждый следующий ремонт того же инструмента просит на одну пасту больше; не больше %s ремонтов. | In a crafting grid with a worn tool of the mod (a photomask, a sputter target, a diamond wire or blade, an EUV mask, a drill head) it gives back %s%% of the durability. Each next repair of the same tool takes one paste more; %s repairs at most.
- sc.tooltip.nanitePaste.howto = Хим. реактор: 4 Нанитовой пыли и слизь. | Chemical Reactor: 4 Nanite Dust and a slimeball.
- sc.tooltip.pasteRepairs = Ремонтов пастой: %s из %s | Paste repairs: %s of %s
- sc.tooltip.pasteRepairs.done = Изношен окончательно - пастой больше не чинится | Worn out for good - no more paste repairs
- sc.tooltip.loot.singularScrap.howto (замена) = Дробилка: гафниевая пыль. Обратно в броню не превращается; его покупают жители поселений. | Crusher: hafnium dust. Never turns back into armour; the settlers buy it.
- sc.tooltip.loot.inductionThread.howto (замена) = Прокатный станок (форма катушки): 4 нити - катушка Nb3Sn. Её покупают и жители поселений. | Rolling Machine (coil mould): 4 threads - an Nb3Sn coil. The settlers buy it too.
- sc.tooltip.loot.boiledLoop.howto (замена) = С ячейкой жидкого гелия и сверхпроводником - Гелиевый контур (без контроллера). Его покупают и жители поселений. | With a liquid helium cell and a superconductor - a Helium Loop (no controller). The settlers buy it too.
- sc.tooltip.loot.plasmaCondensate.howto (замена) = Центрифуга: 500 мБ аргона. Его покупают и жители поселений. | Centrifuge: 500 mB of argon. The settlers buy it too.
- sc.tooltip.loot.naturalWafer.howto (замена) = 2 пластины и электронный кремний - Затравочный кристалл без алмаза. Её покупают и жители поселений. | 2 wafers and electronic-grade silicon - a Seed Crystal with no diamond. The settlers buy it too.
- sc.tooltip.loot.naniteDust.howto (замена) = Хим. реактор: 4 пыли и слизь - Нанитовая паста для ремонта инструментов. | Chemical Reactor: 4 dust and a slimeball - Nanite Paste to mend tools.

**Проверки.** Самотест `om2`: каждый из 6 рецептов находится в RecipeRegistry / CraftingManager по своим входам; `PasteRepairRecipeSC.matches/getCraftingResult` на стеках: 1 паста к новому ремонту, 2 ко второму, отказ на целом инструменте и после 3 ремонтов; износ −25%; «не дешевле»: 6 паст = 24 пыли ≥ входов нового инструмента в пересчёте (TODO(balance) — сравнить с ценой пыли у Скупщика). Мировой тест — не нужен.

**Файлы:** `init/ModRecipesIdeasSC.java` (om2), `item/ItemNanitePasteSC.java`, `init/PasteRepairRecipeSC.java` (новый), `item/ItemToolSC.java`, `item/ItemDrillHeadSC.java`, `manual/BookWorldSC.java`, `manual/BookContent.java`, `tools/gen_paste_tex.py` (новый), `src/main/resources/assets/siliconage/textures/items/nanitePaste.png` (новый), `debug/SelfTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-3. Гафний из лишнего циркония

**★** А. Химия в два шага, как у титана: цирконий и хлор в хим. реакторе → хлориды циркония; нефтезавод разделяет их обратно на цирконий, щепотку гафния и часть хлора. Выход в 2–3 раза лучше нынешнего, но медленнее, чем покупать гафний у Металлурга Подложки. Хлориды едкие — только трубы PTFE.

**Что сделать (`ModRecipesIdeasSC.om3`, TODO(balance)).**

1. Хим. реактор (MV, 100 EU/t): 4 Циркониевой пыли + 500 мБ хлора → 500 мБ `zrcl4`, 300 тиков.
2. Нефтезавод (HV, 250 EU/t): 4000 мБ `zrcl4` → 8 Циркониевой пыли + 1 малая Гафниевая пыль (`ModItems.dustTiny` HAFNIUM) + 2000 мБ хлора, 1200 тиков. Итог: чистый расход 24 пыли циркония (≈ 12 руд бадделеита) и 2000 мБ хлора на малую кучку гафния: вместе с нынешним следом (≈ 1 малая на 16–17 руд) — ≈ 1 малая на 7 руд, в 2,4 раза лучше; на слиток ≈ 27 мин одной пары машин — медленнее, чем 2 слитка за 10 КЧ у Металлурга.
3. Жидкость `zrcl4` зарегистрирована (едкая — уже в CorrosiveFluids, координатор), ведро — мета 24. Текстуры `blocks/fluids/zrcl4_still.png` + `.mcmeta`, `zrcl4_flow.png` + `.mcmeta` (скрипт `tools/gen_zrcl4_tex.py` по образцу соседних текстур жидкостей; цвет 0xD8E0B0 — бледно-жёлтый пар); имя и строка справочника по жидкостям.
4. Справочник: материалы (`BookContent` / `BookMaterialsSC`) — строка гафния: «или из циркония: хим. реактор → нефтезавод»; статья жидкостей — zrcl4 (едкая, PTFE); NEI — рецепты машин сами.

**Новое.** Жидкость «zrcl4» (есть). Предметов нет.

**Тексты (ключ = ru | en).**
- fluid.zrcl4 = Хлориды циркония и гафния | Zirconium and hafnium chlorides
- sc.book.fluid.d.zrcl4 = Хлориды циркония и гафния: хлорированный цирконий; нефтезавод разделяет их на цирконий, щепотку гафния и хлор. Едкие - только трубы PTFE. | Zirconium and hafnium chlorides: chlorinated zirconium; the Refinery splits them into zirconium, a pinch of hafnium and chlorine. Corrosive - PTFE pipes only.
- sc.manual.materials.hafniumZr = Гафний можно добыть и из лишнего циркония: хим. реактор (4 Циркониевой пыли и 500 мБ хлора - 500 мБ хлоридов), затем нефтезавод (4000 мБ хлоридов - 8 Циркониевой пыли, малая кучка гафния и 2000 мБ хлора). Медленно, но цирконий не копится. | Hafnium can also come from spare zirconium: the Chemical Reactor (4 Zirconium Dust and 500 mB of chlorine - 500 mB of chlorides), then the Refinery (4000 mB of chlorides - 8 Zirconium Dust, a small pile of hafnium and 2000 mB of chlorine). Slow, but the zirconium no longer piles up.

**Проверки.** Самотест `om3`: `zrcl4` есть в FluidRegistry и `CorrosiveFluids.isCorrosive("zrcl4")`; оба рецепта находятся; баланс: чистый расход циркония на малую кучку ≥ 20 (не «бесконечный гафний»), хлор не создаётся из ничего (на выходе ≤ на входе); ведро мета 24 = «zrcl4».

**Файлы:** `init/ModRecipesIdeasSC.java` (om3), `manual/BookContent.java`, `manual/BookMaterialsSC.java`, `tools/gen_zrcl4_tex.py` (новый), `src/main/resources/assets/siliconage/textures/blocks/fluids/zrcl4_still.png`, `.../zrcl4_still.png.mcmeta`, `.../zrcl4_flow.png`, `.../zrcl4_flow.png.mcmeta` (новые), `debug/SelfTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-4. ЭУФ-литограф и процессоры

**★** А. Вся лестница: машина уровня QV «ЭУФ-литограф» делает процессорные пластины, дальше Процессор (QV), Экзо-процессор (XV) и Сингулярная матрица (SV); у матрицы два пути — с Природными пластинами из Подложки или домашний, подороже. Новые, более выгодные рецепты панелей, Токамака, Экзо-реактора, накопителей и Сингулярной станции; старые остаются.

**Что сделать.**

1. Машина `MachineType.EUV_LITHOGRAPH` (есть: QV, 8000 EU/t, нагрев как у степперов) на `ModBlocks.machineSC3` мета 0. Текстура лицевой стороны `blocks/machineEUVLithographFront.png` (скрипт `tools/gen_euv_tex.py` в стиле `machineStepperEVFront`, корпус — `machineCasingQV`), сцена экрана (`inventory/GuiSceneSC` — как у степпера: колонна с источником ЭУФ, зеркала, маска), NEI-обработчик (`nei/MachineHandlersSC` — проверка «no NEI handler class for EUV_LITHOGRAPH» не должна срабатывать), имя `sc.machine.euv_lithograph`, `tile.siliconage.machineSC3.euv_lithograph.name`, подсказка/справочник машины `sc.manual.machine.euv_lithograph`.
2. Предметы (меты siliconMaterial 18..21 и инструмент «toolEUV_MASK» уже зарегистрированы): текстуры `items/processorWafer.png`, `processor.png`, `exoProcessor.png`, `singularMatrix.png`, `items/euvMask.png` (скрипт тот же), имена, подсказки (`ItemSiliconMaterialSC` — строки `sc.si.tooltip.<мета>` / `.howto`; `ItemToolSC` — маска, TooltipSC).
3. Рецепты (`ModRecipesIdeasSC.om4`, TODO(balance)):
   - ЭУФ-маска: Upgrade Station EV: 1 фотомаска + 4 слитка вольфрама + 2 «Кварцевая камера» + 1 контроллер → 1 ЭУФ-маска (24 использования);
   - Процессорная пластина: ЭУФ-литограф: 1 металлизированная пластина (METALLIZED_WAFER) + ЭУФ-маска (изнашивается) + 250 мБ криптона → 1 пластина процессоров, 600 тиков, брак 15%;
   - Процессор (QV): Упаковщик (Packager): 1 пластина процессоров + 1 выводная рамка x40 + 1 керамический корпус → 1 Процессор, 300 тиков;
   - Экзо-процессор (XV): Upgrade Station EV: 4 Процессора + 2 слитка гафния + 1 «hfo2Die» + 1 жидкий гелий (ячейка) → 1 Экзо-процессор;
   - Сингулярная матрица (SV), путь Подложки: ЭУФ-литограф: 4 Экзо-процессора + 2 Природные пластины + 100 мБ сингулярной материи → 1 матрица, 1200 тиков; домашний (дороже): ЭУФ-литограф: 4 Экзо-процессора + 4 GaAs-пластины + 500 мБ сингулярной материи → 1 матрица, 2400 тиков.
   - Новые выгодные рецепты (старые остаются): в рецептах Солнечной панели Квант/Экзо (и SV, если есть), Токамака, Экзо-реактора, накопителей QV/XV/SV и Сингулярной станции обслуживания — второй вариант, где каждые 4 контроллера заменены 1 Процессором (QV-уровень), 1 Экзо-процессором (XV) или 1 Сингулярной матрицей (SV) по уровню изделия, и прочие детали −25% (округление вверх). Исполнитель берёт нынешние рецепты из `ModRecipesCrafting`/`ModRecipesMachineBlocks` и пишет варианты в om4.
4. Справочник: «Путь развития» (`manual/BookPathSC`) — ЭУФ-литограф на QV, процессоры на QV/XV/SV; статья машины (`BookContent`) — цепочка и оба пути матрицы; NEI — сами.

**Новое.** Всё зарегистрировано координатором (тип машины, блок, меты, инструмент). Конфигов нет.

**Тексты (ключ = ru | en).**
- sc.machine.euv_lithograph = ЭУФ-литограф | EUV Lithograph
- tile.siliconage.machineSC3.euv_lithograph.name = ЭУФ-литограф | EUV Lithograph
- sc.manual.machine.euv_lithograph = Машина уровня QV: экспонирует металлизированную пластину жёстким ультрафиолетом через ЭУФ-маску - выходит пластина процессоров. Из неё - Процессор, Экзо-процессор и Сингулярная матрица. | A QV machine: exposes a metallized wafer to extreme ultraviolet through an EUV mask - out comes a processor wafer. It makes the Processor, the Exo Processor and the Singular Matrix.
- item.siliconage.siliconMaterial.processor_wafer.name = Пластина процессоров | Processor Wafer
- item.siliconage.siliconMaterial.processor.name = Процессор | Processor
- item.siliconage.siliconMaterial.exo_processor.name = Экзо-процессор | Exo Processor
- item.siliconage.siliconMaterial.singular_matrix.name = Сингулярная матрица | Singular Matrix
- item.siliconage.euv_mask.name = ЭУФ-маска | EUV Mask
- sc.si.tooltip.processor_wafer = Пластина с сотней процессорных кристаллов, снятая с ЭУФ-литографа | A wafer of a hundred processor dies off the EUV Lithograph
- sc.si.tooltip.processor = Процессор уровня QV - заменяет четыре контроллера в рецептах | A QV processor - stands in for four controllers in recipes
- sc.si.tooltip.exo_processor = Экзо-процессор уровня XV | An XV Exo Processor
- sc.si.tooltip.singular_matrix = Сингулярная матрица уровня SV - вершина кремниевой цепочки | An SV Singular Matrix - the top of the silicon chain
- sc.si.tooltip.singular_matrix.howto = ЭУФ-литограф: 4 Экзо-процессора и 2 Природные пластины Подложки или 4 GaAs-пластины (дороже), сингулярная материя. | EUV Lithograph: 4 Exo Processors and 2 of the Substrate's Natural Wafers, or 4 GaAs wafers (dearer), singular matter.
- sc.tooltip.euvMask = Отражательная маска ЭУФ-литографа, изнашивается | The EUV Lithograph's reflective mask; it wears
- sc.manual.path.euv = QV: ЭУФ-литограф и Процессор; XV: Экзо-процессор; SV: Сингулярная матрица - из Природных пластин Подложки или домашняя, дороже. | QV: the EUV Lithograph and the Processor; XV: the Exo Processor; SV: the Singular Matrix - from the Substrate's Natural Wafers, or the dearer home way.

**Проверки.** Самотест `om4`: `MachineType.EUV_LITHOGRAPH.ordinal() == 32`, `BlockMachineSC.blockFor` → machineSC3, мета 0; меты 18..21 и их имена текстур; все рецепты цепочки находятся; каждый новый «выгодный» рецепт дешевле старого по числу контроллеров-эквивалентов и старый на месте; NEI-обработчик есть. Мировой тест — не нужен (машина — обычная).

**Файлы:** `init/ModRecipesIdeasSC.java` (om4), `inventory/GuiSceneSC.java`, `inventory/GuiMachineSC.java` (если нужен свой экран), `nei/MachineHandlersSC.java`, `item/ItemSiliconMaterialSC.java`, `item/ItemToolSC.java`, `manual/BookPathSC.java`, `manual/BookContent.java`, `tools/gen_euv_tex.py` (новый), текстуры `blocks/machineEUVLithographFront.png`, `items/processorWafer.png`, `items/processor.png`, `items/exoProcessor.png`, `items/singularMatrix.png`, `items/euvMask.png` (новые), `debug/SelfTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-5. Редстоун для машин и реакторов

**★** А. Сигнал компаратора у машин, генераторов, реакторов и карьеров (прогресс, буфер, стабильность плазмы, масса дыры) и «Порт данных» в стене мультиблока: по импульсу редстоуна — аварийная мягкая остановка. Сигнал на розжиг только разрешает его, сам розжиг по-прежнему с экрана.

**Что сделать.**

1. Компаратор (интерфейс `energy/IComparatorSC`, помощник `ComparatorSC.level/notify` — есть): блоки `block/BlockMachineSC`, `block/BlockGeneratorSC`, `block/BlockQuarrySC` — `hasComparatorInputOverride` true, `getComparatorInputOverride` → `comparatorLevel()` тайла (не IComparatorSC — 0). Тайлы реализуют интерфейс и раз в 20 тиков `last = ComparatorSC.notify(this, last)`:
   - `TileEntityMachineSC`: прогресс текущей операции (0 — простой; ComparatorSC.level(progress, max));
   - `TileEntityGeneratorSC`: обычные — буфер; FUSION_REACTOR/TOKAMAK/EXO_REACTOR — разгон (ramp 0..1000; не зажжён — 0); TOKAMAK_XV — стабильность плазмы (stability 0..100; не зажжён — 0); SINGULAR_REACTOR — масса дыры (`getSingular().getMass()` к её наибольшей);
   - `TileEntityQuarrySC`: карьер — доля пройденной площади; Экзо-установка — заполнение буфера выхода.
2. Порт данных (блок и TE зарегистрированы): `TileEntityDataPortSC` — тик раз в 10: привязка к реактору (её ставит сам реактор при скане), редстоун (`isBlockIndirectlyGettingPowered`): STOP — фронт 0→1 → `softStop()` реактора, если зажжён (флажок «Разрешить розжиг» не трогать); PERMIT — розжиг разрешён, только пока порт запитан; пропал сигнал при горящем реакторе — `softStop()`. Компаратор у порта — `comparatorLevel()` его реактора. ПКМ пустой рукой или ключом — режим STOP ↔ PERMIT (строка). Не привязан — компаратор 0, строка в подсказке WAILA/ПКМ «не в стене реактора».
3. Реакторы: `TileEntityGeneratorSC.scanBig` (Токамак XV) и `tileentity/SingularReactorSC` (скан 7 x 7 x 5): Порт данных в стене — как свинцовая стена (`isShell` true для `IdeaBlocksSC.dataPort`), реактор сообщает порту себя (`bind(x, y, z)`), портов любое число; `ignitionAllowedByPorts()` — все PERMIT-порты запитаны (нет PERMIT — да); кнопка розжига на экране (`GuiTokamakXVSC`, `GuiSingularSC` → сервер) при запрете — отказ с причиной «Порт данных не разрешает розжиг»; статус мягкой остановки от порта — своя строка.
4. Рецепт (`ModRecipesIdeasSC.om5`): свинцовый блок + компаратор + контроллер + 2 редстоуна + титановая пластина → Порт данных. Текстура `blocks/dataPort.png` (+ `dataPort_side.png`: свинцовая панель с разъёмом и лампами STOP/PERMIT; скрипт `tools/gen_dataport_tex.py`), подсказка TooltipSC, NEI — рецепт сам.
5. WAILA (`compat/WailaSC`, по желанию): порт — режим и реактор; машины — уровень компаратора не нужен.
6. Справочник (`BookContent`, энергия/реакторы): что выдаёт компаратор у каждого вида; Порт данных — где ставить, два режима.

**Числа.** Обновление компаратора раз в 20 тиков; порт — раз в 10.

**Новое.** Блок «dataPort» (есть); NBT порта `scDataPort` {mode} (есть) + {rx, ry, rz} привязки (дописать).

**Тексты (ключ = ru | en).**
- tile.siliconage.dataPort.name = Порт данных | Data Port
- sc.tooltip.dataPort = Связь реактора с редстоуном | A reactor's link to redstone
- sc.tooltip.dataPort.details = Ставится в стену Токамака XV или Сингулярного реактора вместо свинцового блока. Компаратор рядом читает стабильность плазмы или массу дыры. | Goes into the wall of a Tokamak XV or a Singular Reactor in place of a lead block. A comparator next to it reads the plasma's stability or the hole's mass.
- sc.tooltip.dataPort.howto = Режим „Стоп“: импульс редстоуна - мягкая остановка. Режим „Разрешение“: розжиг с экрана возможен, только пока порт запитан. ПКМ - сменить режим. | “Stop” mode: a redstone pulse - a soft stop. “Permit” mode: lighting from the screen only while the port is powered. Right-click - change the mode.
- sc.dataport.mode.stop = Порт данных: режим „Стоп“ - импульс останавливает реактор. | Data Port: “Stop” mode - a pulse stops the reactor.
- sc.dataport.mode.permit = Порт данных: режим „Разрешение“ - розжиг только при сигнале. | Data Port: “Permit” mode - lighting only with a signal.
- sc.dataport.unbound = Порт не в стене реактора. | The port isn't in a reactor's wall.
- sc.gen.port.noPermit = Порт данных не разрешает розжиг - подайте на него сигнал. | A Data Port doesn't permit lighting - power it.
- sc.gen.port.stopped = Аварийная остановка по Порту данных. | Emergency stop by a Data Port.
- sc.manual.energy.comparator = Компаратор читает: у машины - ход операции, у генератора - буфер, у реактора - разгон или стабильность плазмы (Токамак XV), у Сингулярного реактора - массу дыры, у карьера - пройденную площадь, у Экзо-установки - буфер. | A comparator reads: a machine's operation, a generator's buffer, a reactor's ramp or the plasma's stability (Tokamak XV), the Singular Reactor's hole mass, a quarry's area done, the Exo rig's buffer.
- sc.manual.energy.dataPort = Порт данных в стене Токамака XV или Сингулярного реактора: импульс - мягкая остановка („Стоп“) или розжиг только при сигнале („Разрешение“). Сам розжиг - по-прежнему кнопкой на экране. | A Data Port in a Tokamak XV's or a Singular Reactor's wall: a pulse - a soft stop (“Stop”), or lighting only with a signal (“Permit”). The lighting itself is still the screen's button.

**Проверки.** Самотест `om5`: `ComparatorSC.level` (0 → 0, 1/1000 → 1, половина → 8, всё → 15, max 0 → 0); режимы порта NBT туда-обратно. Мировой тест `WorldTestComparatorSC` (Верхний мир, k = 13): машина с рецептом — уровень растёт; Токамак XV (постройка как в `WorldTestTokamakSC`) с портом в стене — структура цела, порт привязан, импульс (redstone_block рядом) — SOFT_STOP; PERMIT без сигнала — розжиг отказан.

**Файлы:** `block/BlockMachineSC.java`, `block/BlockGeneratorSC.java`, `block/BlockQuarrySC.java`, `block/BlockDataPortSC.java`, `tileentity/TileEntityMachineSC.java`, `tileentity/TileEntityGeneratorSC.java`, `tileentity/SingularReactorSC.java`, `tileentity/TileEntityQuarrySC.java`, `tileentity/TileEntityDataPortSC.java`, `inventory/GuiTokamakXVSC.java`, `inventory/GuiSingularSC.java`, `compat/WailaSC.java`, `init/ModRecipesIdeasSC.java` (om5), `manual/BookContent.java`, `tools/gen_dataport_tex.py` (новый), текстуры `blocks/dataPort*.png` (новые), `debug/SelfTestIdeasSC.java`, `debug/WorldTestComparatorSC.java` (новый), `debug/WorldTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-6. Разборка лишних трофеев

**★** А. На Станции обслуживания брони разбираются трофеи, а также свои старые части брони и оружие: примерно 25–35% компонентов крафта, чипы и модули из своих частей — целыми. Разборка никогда не выгоднее восстановления; заряд и газы теряются.

**Что сделать.**

1. `tileentity/TileEntityArmorStationSC`: слот разборки `SALVAGE_SLOT = 8` (дописан в конец: `ALL_SLOTS` 8 → 9; старый NBT читается), принимает только `SalvageSC.refusal(s) == OK`, стопка 1; автоматика (`ISidedInventory`) его не видит; содержимое выпадает при поломке, в предмет станции не уходит.
2. `item/SalvageSC` (сигнатуры есть): что разбирается — трофей (`MobItemsSC.trophy`) кроме Сингулярных и ПЭ-1М (`TrophyRestoreSC.singular`); свои части `ItemArmorSC` Нано/Квант/Экзо, клинки `ItemBladeSC`, буры `ItemDrillSC` (не Сингулярные), оружие `ItemWeaponSC`, ПЭ-1 (не ПЭ-1М). `result(s)`: рецепт крафта настоящего предмета (`ItemTrophySC.realStack(meta)` для трофея; свой — его же предмет без NBT) — найти в CraftingManager (ShapedOreRecipe / ShapelessOreRecipe / ванильные; первый подходящий вход ore-списка), взять каждую сторону x SHARE (по SHARE_MIN..SHARE_MAX по хэшу предмета, округление вниз; хотя бы одна единица самой дорогой стороны), кроме самой брони/клинка предыдущего уровня в рецепте (её не возвращать); своё: + чипы брони (`ItemArmorChipSC` из NBT части), модули ПЭ-1 (`EmpModule` из NBT) — целыми. Заряд, газы, сингулярный прогресс — теряются.
3. «Никогда не выгоднее восстановления»: для трофея результат по каждой стороне ≤ того, что `TrophyRestoreSC.parts(meta)` + чипы Оружейника; самотест сверяет все меты.
4. `inventory/ContainerArmorStationSC`: слот 8 в контейнере (рядом с частями; координаты — по экрану), кнопка `BTN_SALVAGE = 40`: `enchantItem` → `te.salvage(player)`: STATION_EU 5000 из буфера (нет — `sc.salvage.refuse.noEu`), приватное поле чужого — отказ (как `TrophyRestoreSC.C_PRIVATE`), результат — в инвентарь игрока (что не влезло — к ногам), слот пуст, строка `sc.salvage.done`. `inventory/GuiArmorStationSC`: слот, кнопка «Разобрать» с двойным щелчком (первый «вооружает», как `GuiBigSC.ClearButton`), подсказка над слотом — список того, что выйдет (`SalvageSC.result` на клиенте), предупреждение «заряд и газы пропадут».
5. Подсказки: трофей (`mob/ItemTrophySC` / `sc.tooltip.trophy.howto`) — «или разобрать на Станции»; справочник — абзац о разборке в статье Станции (`BookContent`).

**Числа (TODO(balance)).** SHARE 0.25..0.35, STATION_EU 5000.

**Новое.** Слот 8 станции (NBT слотов по индексу — дописан), кнопка 40. Предметов нет.

**Тексты (ключ = ru | en).**
- sc.salvage.slot = Разборка | Salvage
- sc.salvage.slot.tip = Трофей или своя старая часть брони, клинок, бур, оружие. Выйдет %s-%s%% деталей крафта; чипы и модули своих частей - целыми; заряд и газы пропадут. | A trophy or your own old armour piece, blade, drill, gun. Gives %s-%s%% of the crafting parts; your parts' chips and modules come back whole; the charge and gases are lost.
- sc.salvage.btn = Разобрать | Salvage
- sc.salvage.confirm = Ещё раз - разобрать | Again - salvage
- sc.salvage.preview = Выйдет: | You get:
- sc.salvage.done = Разобрано: %s - получено %s предметов. | Salvaged: %s - %s items received.
- sc.salvage.refuse.nothing = Это не разбирается. | That can't be salvaged.
- sc.salvage.refuse.singular = Сингулярное не разбирается - его восстанавливает Сингулярная станция. | Singular things aren't salvaged - the Singular station restores them.
- sc.salvage.refuse.noEu = Станции не хватает %s EU. | The station is %s EU short.
- sc.tooltip.trophy.howto (добавка в конец) = … Лишний трофей можно разобрать на Станции обслуживания брони. | … A spare trophy can be salvaged at an Armour Service Station.
- sc.manual.armor.station.salvage = Слот „Разборка“ станции: трофей или своя старая часть брони, клинок, бур, оружие разбираются на %s-%s%% деталей крафта за %s EU. Чипы и модули своих частей возвращаются целыми, заряд и газы пропадают. Разборка никогда не выгоднее восстановления. | The station's “Salvage” slot: a trophy or your own old armour piece, blade, drill, gun is broken into %s-%s%% of its crafting parts for %s EU. Your parts' chips and modules come back whole, the charge and gases are lost. Salvage is never worth more than restoring.

**Проверки.** Самотест `om6`: `refusal` — Сингулярный трофей и ПЭ-1М — R_SINGULAR; камень — R_NOTHING; Нано-шлем — OK; `result` Квантового нагрудника с 2 чипами — чипы целыми + 25..35% деталей; для всех трофейных мет 0..23 (кроме сингулярных) результат ≤ деталей восстановления; слот 8 из старого NBT пуст. Мировой тест `WorldTestSalvageSC` (Верхний мир, k = 14): станция с буфером, трофей в слоте 8, `salvage(тест-игрок)` — детали у игрока, −5000 EU.

**Файлы:** `tileentity/TileEntityArmorStationSC.java`, `inventory/ContainerArmorStationSC.java`, `inventory/GuiArmorStationSC.java`, `item/SalvageSC.java`, `mob/ItemTrophySC.java`, `mob/TrophyRestoreSC.java` (если нужна общая таблица цены), `manual/BookContent.java`, `debug/SelfTestIdeasSC.java`, `debug/WorldTestSalvageSC.java` (новый), `debug/WorldTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## ОМ-7. Сингулярный энергобанк

**★** А. Мультиблок-банк, после ЭУФ-литографа: от 3 x 3 x 3 до 7 x 7 x 7, ячейки на 4 и 16 млрд EU, порты LV–SV, график притока и расхода.

**Что сделать.**

1. Структура (`TileEntityEnergyBankSC`, скан раз в 20 тиков, `EnergyBankMathSC` — числа есть): контроллер стоит на грани куба (не на ребре); перебрать N ∈ {3, 5, 7} и шесть направлений «внутрь» — куб N x N x N, где: рёбра — CASING, грани — CASING / GLASS / порты / ровно один CONTROLLER (этот), всё внутри — ячейки CELL_4 / CELL_16 (любая смесь, без пустот); первый годный. Чистая проверка — `EnergyBankMathSC.check(int n, int[] kinds)` (виды блоков по индексу x + n·(y + n·z)) для самотеста. Чанки куба не загружены — скан ждёт (не разбирать). Не годен — «разобран»: энергия остаётся в контроллере, ввод/вывод стоят; экран и подсказка пишут причину (первый неверный блок и его координаты).
2. Энергия: `energy` (long), `capacity` = `capacity(small, big)` x ConfigSC.storageCapacity; ячейка сломана — ёмкость меньше, энергия не пропадает: пока energy > capacity — только вывод. Порты (`TileEntityEnergyBankPortSC`, тир по мете, буфер — Tier.getBuffer, в сеть — как накопитель того же тира: IC2 и своя сеть через TileEntityEnergyBase): режим IN — принимает из сети, контроллер каждый тик забирает до `portRate` из буфера порта; OUT — контроллер кладёт до `portRate` в буфер, порт отдаёт в сеть (sourceTier = тир порта). Режим — ПКМ ключом (`BlockConduitSC.isWrench`) по порту, строка; NBT порта `scBankPort` {out, cx, cy, cz}. Взрывы и перенапряжение — правила TileEntityEnergyBase (порт тира).
3. История: каждую секунду средние EU/t притока и расхода → кольцевые массивы HISTORY (60); не в NBT (после загрузки — заново).
4. Экран (`GuiEnergyBankSC` / `ContainerEnergyBankSC`, GUI 22 есть): заряд (полоса, число с разрядами), ёмкость, размер N x N x N, ячейки (4 млрд x a, 16 млрд x b), порты (по тирам, IN/OUT), сейчас +EU/t / −EU/t, график 60 с двумя линиями (приток зелёный, расход оранжевый, шкала по максимуму), «разобран: причина». Синхронизация — `IntSyncSC` (0–1 энергия, 2–3 ёмкость, 4 размер, 5–6 ячейки, 7–8 порты IN/OUT, 9–10 сейчас, 11–70 приток, 71–130 расход). Стиль — как `GuiEnergyStorageSC` / `GuiBigSC` (TextFitSC для подписей).
5. Контроллер сломан ключом/киркой — предмет несёт `scBank` (энергию; ёмкость — заново при сборке); подсказка показывает заряд. Компаратор у контроллера — `comparatorLevel()` (есть).
6. Рецепты (`ModRecipesIdeasSC.om7`, TODO(balance), после ОМ-4): Корпус x4 — 4 W-Ti пластины + 4 слитка гафния + 1 титановый корпус; Стекло x4 — 4 свинцовых стекла + 1 корпус банка; Контроллер — 1 корпус банка + 2 Экзо-процессора + 1 Сингулярная матрица + 1 накопитель SV; Ячейка 4 млрд — 2 накопителя SV + 2 Процессора + 1 корпус банка; Ячейка 16 млрд — 4 ячейки 4 млрд + 1 Сингулярная матрица; Порт тира T — 1 корпус банка + 1 трансформатор (или кабель) тира T + 1 контроллер (кремниевый).
7. Текстуры (`tools/gen_energybank_tex.py`): `blocks/energyBank_casing.png`, `_glass.png`, `_controller.png` (экран с графиком), `_cell4.png`, `_cell16.png`, `_portLV.png` … `_portSV.png` (цвет тира); иконки по мете (`BlockEnergyBankSC.registerBlockIcons/getIcon`), стекло — прозрачное (renderPass 1 для меты GLASS или отдельная логика `isOpaqueCube` по мете — проверить свет). Подсказки TooltipSC по мете. WAILA (по желанию): заряд контроллера.
8. Справочник: глава энергии (`BookContent`) — статья «Сингулярный энергобанк» (размеры, ячейки, порты, график, разборка без потерь); «Путь развития» (`BookPathSC`) — SV.

**Числа (TODO(balance)).** CELL_EU 4·10⁹ / 16·10⁹, PORT_AMPS 4, HISTORY 60, размеры 3/5/7.

**Новое.** Всё зарегистрировано координатором; NBT `scBank`, `scBankPort`.

**Тексты (ключ = ru | en).**
- tile.siliconage.energyBank.casing.name = Корпус энергобанка | Energy Bank Casing
- tile.siliconage.energyBank.glass.name = Стекло энергобанка | Energy Bank Glass
- tile.siliconage.energyBank.controller.name = Контроллер энергобанка | Energy Bank Controller
- tile.siliconage.energyBank.cell4.name = Ячейка энергобанка 4 млрд EU | Energy Bank Cell, 4 billion EU
- tile.siliconage.energyBank.cell16.name = Ячейка энергобанка 16 млрд EU | Energy Bank Cell, 16 billion EU
- tile.siliconage.energyBank.portLV.name … portSV.name = Порт энергобанка LV … SV | Energy Bank Port LV … SV
- sc.tooltip.energyBank.casing = Рёбра и грани энергобанка | The Energy Bank's edges and faces
- sc.tooltip.energyBank.controller = Сердце энергобанка: экран, график, компаратор | The Energy Bank's heart: the screen, the graph, the comparator
- sc.tooltip.energyBank.cell = Внутрь куба банка: %s EU | Inside the bank's cube: %s EU
- sc.tooltip.energyBank.port = Вход или выход банка уровня %s: до %s EU/t. Ключ - сменить направление. | The bank's %s input or output: up to %s EU/t. Wrench - switch the direction.
- sc.tooltip.energyBank.details = Полый куб от %s до %s блоков: рёбра - корпус, грани - корпус, стекло, порты и один контроллер, внутри - только ячейки. | A hollow cube of %s to %s blocks: the edges casing, the faces casing, glass, ports and one controller, the inside cells only.
- sc.tooltip.energyBank.howto = ПКМ по контроллеру - экран. Сломанная ячейка энергию не уносит: банк лишь перестаёт принимать. | Right-click the controller - the screen. A broken cell takes no energy with it: the bank just stops taking more.
- sc.tooltip.energyBank.stored = Заряд: %s EU | Charge: %s EU
- sc.gui.bank.title = Сингулярный энергобанк | Singular Energy Bank
- sc.gui.bank.charge = Заряд: %s из %s EU | Charge: %s of %s EU
- sc.gui.bank.size = Размер: %s x %s x %s | Size: %s x %s x %s
- sc.gui.bank.cells = Ячейки: 4 млрд x %s, 16 млрд x %s | Cells: 4 billion x %s, 16 billion x %s
- sc.gui.bank.ports = Порты: вход %s, выход %s | Ports: in %s, out %s
- sc.gui.bank.flow = Приток %s EU/t, расход %s EU/t | In %s EU/t, out %s EU/t
- sc.gui.bank.graph = Последние %s с | The last %s s
- sc.gui.bank.unformed = Банк не собран: %s (%s %s %s) | The bank isn't formed: %s (%s %s %s)
- sc.gui.bank.reason.edge = на ребре должен быть корпус | an edge must be casing
- sc.gui.bank.reason.face = на грани - корпус, стекло, порт или контроллер | a face takes casing, glass, a port or the controller
- sc.gui.bank.reason.inside = внутри должны быть только ячейки | the inside must be cells only
- sc.gui.bank.reason.size = нет куба 3, 5 или 7 блоков | no cube of 3, 5 or 7 blocks
- sc.gui.bank.reason.controllers = второй контроллер | a second controller
- sc.bank.port.in = Порт: вход в банк. | Port: into the bank.
- sc.bank.port.out = Порт: выход из банка. | Port: out of the bank.
- sc.manual.energy.bank.title = Сингулярный энергобанк | The Singular Energy Bank
- sc.manual.energy.bank.1 = Мультиблок от %s x %s x %s до %s x %s x %s: внутри - ячейки по 4 и 16 млрд EU (7 x 7 x 7 из ячеек 16 млрд - %s EU), на гранях - порты LV-SV (до %s x напряжение уровня за тик каждый). | A multiblock of %s x %s x %s to %s x %s x %s: inside - cells of 4 and 16 billion EU (a 7 x 7 x 7 of 16-billion cells - %s EU), on the faces - LV-SV ports (up to %s x the tier's voltage a tick each).
- sc.manual.energy.bank.2 = Экран контроллера показывает заряд, порты и график притока и расхода за минуту; компаратор у контроллера читает заряд. | The controller's screen shows the charge, the ports and a minute's graph of the inflow and outflow; a comparator at the controller reads the charge.

**Проверки.** Самотест `om7`: `validSize` (3/5/7 да, 4/9 нет), `cellSlots` (1, 27, 125), `capacity` (125 x 16 млрд = 2·10¹² без переполнения), `portRate` (SV 524288), `check` на сетке 3 x 3 x 3 (годная; ячейка на ребре — нет; два контроллера — нет; дыра внутри — нет). Мировой тест `WorldTestEnergyBankSC` (Верхний мир, k = 15): куб 3 x 3 x 3 (контроллер, порт IN LV, порт OUT LV, ячейка 4 млрд) — собран; энергия через IN растёт; компаратор > 0; ячейка сломана — энергия не пропала, ввод стоит.

**Файлы:** `energy/EnergyBankMathSC.java`, `block/BlockEnergyBankSC.java`, `block/ItemBlockEnergyBankSC.java`, `tileentity/TileEntityEnergyBankSC.java`, `tileentity/TileEntityEnergyBankPortSC.java`, `inventory/ContainerEnergyBankSC.java`, `inventory/GuiEnergyBankSC.java`, `inventory/IntSyncSC.java` (только если нужен публичный доступ), `compat/WailaSC.java`, `init/ModRecipesIdeasSC.java` (om7), `manual/BookContent.java`, `manual/BookPathSC.java`, `tools/gen_energybank_tex.py` (новый), текстуры `blocks/energyBank_*.png` (новые), `debug/SelfTestIdeasSC.java`, `debug/WorldTestEnergyBankSC.java` (новый), `debug/WorldTestIdeasSC.java`.

---------------------------------------------------------------------------------------------------

## Решения координатора (не было в предложении)

- Зачёт звена (ПД-2) требует добившего (MobEventsSC.killerOf, ≥ 25% урона): без него убийство не засчитывается никому, как сейчас; доля напарника — от всего урона цели.
- «Тяжёлая ночь» (ПД-3) — услуга Старейшины только там, где служит Капитан (Город всегда, Посёлок 30%): у стражей нет экрана обмена, «у Капитана» понято так. Обычная «Тревога» — у любого Старейшины Подложки. Ожидающий вызов отменяется той же строкой с возвратом залога; срока у вызова нет. Итог налёта приходит после любого налёта, не только вызванного.
- ПД-1: доска Подложки — 3 общих + 1 местный + 1 заказ + Большой раз в неделю (при прежних 3 взятых в сутки). Контроллеры в заказах оставлены, хотя Технолог их продаёт: заказ платит меньше покупки (6 контроллеров за 12 КЧ против 2 за 12 КЧ у Технолога) — перепродажи нет.
- ПД-4: розыскной — новая сущность-наследник Штурмовика (id 26), не метка на обычном мобе: нужен свой слот DataWatcher для имени и второго модификатора; запись — в RareRegistrySC («wanted»). Подсказка Картографа — услуга обмена (OUT_WANTED_HINT); жетоны — близнецы услуг восстановления Оружейника.
- ПД-5: Собиратель — наследник Наладчика (id 27) с видом Газовщика (+ мешок), без слота в составе (как мастер каравана), NpcKindSC не меняется; дроны и мотыльки — клиентские частицы, без сущностей и пакетов; переключатель — одна клиентская настройка `ambientLife`.
- ПД-6: протоколы крафтятся из Матрицы Пробоя (даёт ей применение) и добычи Подложки, тратятся с Ключом; запрещённые пары — «Глухие катушки» + «Помехи», «Короткий фитиль» + «Тень Затмения»; схема «Пробой» — новая SingularScheme N, открывается сразу (без предмета).
- ПД-7: пост — запись реестра поселений нового размера RELAY (защита, Причал, налёты, цели моста — готовыми механизмами); `SettlementRegistrySC.remove` — только для RELAY. Проигранная оборона Ретранслятора не уничтожает его (выпадает предметом); проигранный налёт на построенный пост — уничтожает пост. Нельзя над Хранилищем (люк в площади).
- ПД-8: тоннели — структура на ячейку плато (старт в средней чанке, оба тоннеля в 8 чанках), не в ячейках спавна, Разлома, поселений и литографий; вход не открывается у лагеря.
- ОМ-2: ремонт пастой — растущая цена (1, 2, 3 пасты), не больше 3 ремонтов по 25%; конденсат → аргон в центрифуге (если у центрифуги нет жидкого выхода — другая машина с ним).
- ОМ-3: числа подобраны на «≈ 2,4 раза лучше» на руду и «≈ 27 мин на слиток»: хим. реактор 4 пыли + 500 мБ Cl → 500 мБ; нефтезавод 4000 мБ → 8 пыли + малая кучка Hf + 2000 мБ Cl.
- ОМ-4: ЭУФ-литограф — 33-й тип машины, на третьем блоке машин; цепочка: металлизированная пластина → пластина процессоров → Процессор (Упаковщик) → Экзо-процессор (Станция улучшений EV) → Матрица (литограф, два пути); «выгодные» рецепты — 4 контроллера : 1 процессор по уровню, прочее −25%.
- ОМ-5: «прогресс/буфер/стабильность/масса» разложены по видам (см. п. 1); у реакторов без стабильности (Термоядерный, Токамак, Экзо) — разгон. Порт данных — два режима (Стоп / Разрешение) вместо одного сигнала, чтобы «импульс — остановка» и «сигнал — разрешение» не спорили.
- ОМ-6: разборка — слот 8 станции и кнопка с двойным щелчком (щелчок предметом по станции уже занят восстановлением трофея).
- ОМ-7: один блок «energyBank» на 13 мет; энергия при поломке ячейки не теряется; график — через IntSyncSC контейнера, без нового пакета.
- Новых сетевых дискриминаторов не заведено: всем пунктам хватает существующих пакетов; свободные 6, 7 канала «SiliconAgeDim» — через координатора.
