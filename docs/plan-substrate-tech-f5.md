# Подложка (Si-14) — техплан Ф5 «Финал» и разбивка на пакеты (2026-10-17)

Дизайн: `docs/plan-substrate.md` (утверждён, все ★; коды правил Ф5 — там, §43–§53). Источник чисел: `docs/idea-dimension-substrate.md` §1, §2, §3, §4, §7, §9, §10. Техпланы Ф1–Ф4: `docs/plan-substrate-tech.md`, `-f2.md`, `-f3.md`, `-f4.md` (их правила работы, сборка и запреты действуют и здесь).
Здесь — архитектура Ф5 под существующий код и разбивка на 6 параллельных пакетов (A, B, J, K, P, D) с **непересекающимися файлами**. Ф5 — последняя фаза измерения: в конце D делает сквозную сверку Ф1–Ф5 (справочник, NEI, README, CHANGELOG).
Java 6 (без лямбд, diamond, try-with-resources, switch по String, без API Java 7+), MCP-имена, Forge 1.7.10-10.13.4.1614.
Сборка:
`T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /c/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q`
Строки «Read timed out» и 404 безвредны. runServer не запускать, сборка — всегда clean build. Проверка reobf: `unzip -p build/libs/SiliconAge-0.2.1.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` печатает 1. Каждый пакет обязан собираться сам по себе поверх ядра.

## 0. Ядро (уже написано, собирается)

Правит **только координатор**. Нужна правка ядра или чужого файла — описать её в отчёте (файл, место, текст). Заготовки (стабы) принадлежат пакетам, указанным в их Javadoc: сигнатуры, имена, меты, слоты DataWatcher и ключи NBT в них фиксированы, тела — пакетов.

| Файл (`com/sc/…`) | Что |
|---|---|
| `dim/SubstrateConfigSC.java` | Правила `requireSignal` (false) и `caravans` (true); **не правило** `customSky` (false) — приватное поле с геттером: вид неба у каждого клиента свой, сервер его не присылает (§2.1) |
| `dim/SubstrateSignalSC.java` | `requireSignal` (ПВ-13), написан целиком: чистое `visible(require, hasRecord, bridgeInSubstrate)`; `knows(p)` / `grant(p)` — флаг `scSi14Nav` в постоянном NBT игрока; `openTo(viewer, bridgeDim)`, `refuses(p, toDim, bridgeDim)`, `filterDims(list, viewer, bridgeDim)` |
| `tileentity/TileEntityBridgeControllerSC` | Список измерений в `writeState` и `writeFarState` — через `filterDims` (каждому зрителю свой); `plan()` — отказ `sc.bridge.refuse.noSignal` для конца в Подложке в обеих ветках (точка, игрок); `si14Targets` — пусто без записи. При `requireSignal = false` поведение прежнее |
| `dim/WorldProviderSubstrateSC.getSkyRenderer` | Своё небо (ПМ-19): `dim/client/SubstrateSkySC.renderer()`, иначе ванильное. Заготовка `SubstrateSkySC` (P) уже отвечает null при выключенном `customSky` и при ShadersModCore (класс `shadersmodcore.client.Shaders`) |
| `mob/MobEntitiesSC.java` | Пул 22 `breakdownWarden`, 23 `containmentCoil`, 24 `caravanMaster`, 25 `caravanGuard`; `NAMES`, `EGG_COLORS` (26); `hasEgg(id)` — нет яиц у 23–25; `BOSS_TRACKING` 128 для босса и катушек |
| `mob/ItemSpawnEggSC.getSubItems` | Только `hasEgg` |
| `mob/SpawnRulesSC` | Все 8 таблиц по пулу — длиной 26 (у 22–25 нули: директор их не ставит) |
| `mob/SpawnDirectorSC.spotAt` | Никого в боксе арены ± 8 (`ArenaLayoutSC.arenaNear`, ПС-57) |
| `mob/IMobActionSC.java` | Телеграф Ф5: `ACT_SILENCE` 22, `ACT_OVERHEAT` 23, `ACT_CORE_RESET` 24, `ACT_SUMMON` 25 (пресс — прежний `ACT_PRESS`) |
| `mob/IOwnDropsSC.java` + `MobDropsSC.onDrops` | Класс со своей добычей (босс) получает событие, доли урона всех игроков и `doMobLoot` вместо обычного пути (ПТ-21) |
| `mob/MobDropsSC` | Элита (`executeImmune()`) при засчитанном убийстве — Координатор арены `ARENA_COORDINATOR_CHANCE` 2% (ПС-23) |
| `mob/MobEventsSC.damageShares(mob)` | Копия долей урона настоящих игроков (UUID → 0..1) |
| `mob/WardenRulesSC.java` | **Все числа и чистые решения босса** (§5), написаны целиком |
| `mob/EntityBreakdownWardenSC.java` | Заготовка B: `IBossDisplayData`, `IOwnDropsSC`, слоты 26–30, NBT `scFight` / `scWarden`, `summon(w, arena, players)`, `bindTo`, `arena()`, клиентские `phase()`, `phaseFlag`, `highlight()`, `coilsLit()`, `timer()`; вне переписи, `alwaysElite`, не исчезает; устаревший бой — `setDead` (раз в 100 тиков спрашивает `ArenaWorldSC.fightCurrent`) |
| `mob/EntityContainmentCoilSC.java` | Заготовка B: `EntityLiving` + `IEmpReactiveSC` + `ISubstrateNativeSC`, слоты 12–15, состояния LIT / DARK / BROKEN |
| `mob/WardenLootSC.java` | Заготовка B: отложенная добыча (вход, возрождение) |
| `mob/client/BossClientSC.java` | Заготовка B: рендеры, `onFx`, виды `FX_*` |
| `arena/ArenaLayoutSC.java` | **Размещение и устройство арены** (§4.1), чистое, написано целиком |
| `arena/ArenaDataSC.java` | WorldSavedData `SCSi14ArenaRec` (§4.3; не `SCSi14Arena` — это имя данных структуры арены в том же хранилище), написан целиком |
| `arena/ArenaSC.java` | Имена структур арены и обломков (`init`), `generates()` |
| `arena/MapGenArenaSC.java` | Заготовка A: старт `SCSi14Arena` с NBT `ArenaI` / `ArenaJ`, `carve`, `covers`, `startAt` (стаб арен не ставит) |
| `arena/ArenaWorldSC.java` | Заготовка A: `start`, `bossDied`, `fightCurrent` (тело уже есть), `clearAll`, тик мира |
| `arena/ArenaBlocksSC.java` | Регистрация `arenaSC` (меты 0–5) и TE `SiliconAge.breakdownConsole`; `isFloor` |
| `block/BlockArenaSC.java`, `block/ItemBlockArenaSC.java`, `tileentity/TileEntityBreakdownConsoleSC.java`, `arena/ItemArenaCoordinatorSC.java` | Заготовки A: неломаемый блок с TE консоли и путём клика; консоль — `setup(ri, rj)`, `arena()`, `click(p)`, NBT `scArena`, пакет описания с ключом |
| `ruin/ItemRelicSC.java` | Меты 3 `breakdownKey` (стек 1, epic), 4 `breakdownMatrix` (стек 16, epic) |
| `lore/JournalSC.java` | Журналы (§6.1): 12, меты, источники и шансы, `pick`, `roll`; написан целиком |
| `lore/WreckLayoutSC.java` | Размещение обломков моста (§6.2), чистое, написано целиком |
| `lore/SignalRulesSC.java`, `lore/SignalDataSC.java` | Числа и чистые решения Сигнала, WorldSavedData `SCSi14Signal` (§7) |
| `lore/ItemJournalSC.java`, `lore/MapGenWreckSC.java`, `lore/SignalSC.java`, `lore/client/SignalClientSC.java`, `lore/client/JournalClientSC.java` | Заготовки J |
| `settle/caravan/CaravanRulesSC.java` | Числа и чистые решения караванов (§8), написан целиком |
| `settle/caravan/CaravanSC.java` | Заготовка K; запись редкой сделки написана целиком: `dealOpen(p)`, `grantDeal(p)`, `useDeal(p)` (PlayerRec.extra «V») |
| `settle/caravan/EntityCaravanMasterSC.java`, `EntityCaravanGuardSC.java`, `settle/client/CaravanClientSC.java` | Заготовки K: наследники `EntityTunerSC` / `EntityWardenSC`, слоты 27 / 28 |
| `settle/ReputationSC` | `SRC_CARAVAN` = 10 (без суточного потолка) |
| `finale/FinaleItemsSC.java` | Регистрация 5 предметов Ф5 и стеки-помощники (§2.2); `isLithoModule` |
| `finale/FinaleRecipesSC.java` | Два рецепта (§2.3), написаны целиком |
| `finale/FinaleRulesSC.java` | Модуль Литографов (`UPKEEP_SHARE` 0,5, `upkeep`), «Бирюза Наладчиков» (`TUNER_DOME_RGB`), меты схем окраски, маска открытых схем в постоянном NBT (`scSchemes`, `unlock`, `unlockedSchemes`, `open`) |
| `finale/FinaleNetSC.java`, `finale/client/FinaleClientSC.java` | Канал `SiliconAgeDim`, дискриминатор 4 → клиент; виды `SIGNAL_STATE` 1 (J), `BOSS_FX` 2 (B); раздача по клиентским классам пакетов |
| `finale/ItemPaintSchemeSC.java`, `ItemLithoModuleSC.java`, `ItemNavRecordSC.java` | Заготовки P |
| `util/SingularScheme.java` | Дописаны `L` «Литограф» (0x1C1A24 / 0xF0E2B4), `M` «Наладчик» (0x24423F / 0x3CD2C8); `unlockable()`, `unlockBit()` |
| `util/SingularStationMath.cycle` | Старая `cycle(s, dir)` обходит только 11 открытых всем схем (поведение и тест прежние); новая `cycle(s, dir, unlockedMask)` |
| `util/SingularLevel.java` | Четвёртая графа заданий (`TASKS` = 4): к уровню 3 — «Подавить ПЭ-1 25 бронированных врагов» (`C_EMP_KILLS`, код 33), к уровню 5 — «Победить Смотрителя Пробоя» (`C_WARDEN`, код 53), у уровней 2 и 4 — 0 (нет задания). Три прежние графы не тронуты |
| `item/SingularProgressSC` | `onDeath`: +1 `C_EMP_KILLS` за бронированного местного с подавленной частью (`suppressedArmoured` — та же проверка, что у контракта Старейшины); `wardenDefeated(p)` — для B. `C_KILLS` и так считает бронированных (они IMob) |
| `client/GuiArmorSC`, `item/ItemArmorSC`, `inventory/GuiSingularStationSC` | Циклы по заданиям пропускают несуществующее (нужно 0) |
| `tileentity/TileEntityFieldGeneratorSC` | `PRESETS[24]` = «Бирюза Наладчиков»; `upkeepPerTick` × Модуль (`hasLithoModule`); `isFieldUpgrade` принимает Модуль Литографов (свой предмет, не `UpgradeType`) |
| `ruin/MapGenDeadNodeSC.canSpawnStructureAtCoords` | На перекрёстке арены Узел не ставится |
| `dim/MapGenSubstrateCavesSC` | `keepOut(arenas, wrecks)`: пещеры и колодцы не режут бокс арены и обломков |
| `dim/ChunkProviderSubstrateSC` | Поля `arenas`, `wrecks` и геттеры; provideChunk: старты (после Узлов и Хранилищ) → карвер → `arenas.carve` → пруд; populate: арены и обломки после Хранилищ, до поселений; `ruinCovers` += оба; `func_147416_a`, `recreateStructures` |

### 0.1 Правки ядра в общих файлах (готово, остальным их не трогать)
- `SCMod`: в preInit — `ArenaWorldSC.INSTANCE`, `SignalSC.INSTANCE`, `CaravanSC.INSTANCE` на шинах FML и Forge, `WardenLootSC.INSTANCE` на FML; в `registerRecipes()` — `FinaleRecipesSC.init()`; в init — `ArenaSC.init()` после `RuinSC.init()`; в `serverStopped` — `clearAll()` у `ArenaWorldSC`, `WardenLootSC`, `SignalSC`, `CaravanSC`; при `-Dsc.worldtest` — `WorldTestFinaleSC` на FML.
- `ModItems.init`: в конце — `FinaleItemsSC.init()`. `ModBlocks.init`: после `CampBlocksSC.init()` — `ArenaBlocksSC.init()`.
- `SubstrateNetSC.init`: `FinaleNetSC.register(CHANNEL)` (дискриминатор 4).
- `CommonProxy` / `ClientProxy`: `finaleMessage(kind, nbt)` → `FinaleClientSC.onMessage`; `openJournal(number)` → `JournalClientSC.open`; в `ClientProxy.preInit` — `BossClientSC.register()`, `SignalClientSC.register()`, `CaravanClientSC.register()`.
- `debug/SelfTestSC.run`: вызов `SelfTestFinaleSC.run()` после `SelfTestHazardSC`; проверка числа схем Сингулярной брони 11 → 13. `debug/SelfTestSubstrateSC.RULES` += `requireSignal`, `caravans`; `IDS` += `customSky` (иначе самотест Ф1 падал бы на составе раздела).

Пока пакеты не готовы, всё регистрируется: 4 сущности (яйцо — только у босса; босс из яйца стоит без AI), блок арены и TE, 5 предметов и две меты реликвий, рецепт Ключа Пробоя из 4 Фрагментов и Навигационной записи, имена двух структур, сообщение, правила. Арен и обломков нет (`canSpawnStructureAtCoords` → false), Узел на перекрёстке арены тоже не ставится. `requireSignal` уже работает (по умолчанию выкл.), Модуль Литографов уже делит содержание поля, пресет 24 уже в списке, задания прогресса уже считаются.

## 1. Регистрация и порядок

| Этап | Что | Где |
|---|---|---|
| preInit | `SubstrateConfigSC.load`: правила `requireSignal`, `caravans`, клиентская опция `customSky` | ядро |
| preInit | Блоки `ArenaBlocksSC.init` (после лагерей); предметы `FinaleItemsSC.init`; ruinSC мета 7 `burntCoil` — J дописывает в `BlockRuinSC.NAMES` | ядро + J |
| preInit | Канал `SiliconAgeDim`: дискр. 4 (`FinaleNetSC`); обработчики A, B, J, K | ядро |
| preInit, клиент | `BossClientSC` (B), `SignalClientSC` (J), `CaravanClientSC` (K); небо — `SubstrateSkySC` (P, спрашивает провайдер) | пакеты |
| init (обе стороны) | `MobEntitiesSC.init`: пул 22–25; `ArenaSC.init`: `SCSi14Arena`, `SCSi14Wreck` — всегда, даже при выключенной Подложке; рецепты `FinaleRecipesSC` | ядро + пакеты |
| мир | provideChunk: старты (… Хранилища → арены → обломки) → карвер → вырез ямы арены (A) → пруд. populate: Ф1 → `SubstrateWildGenSC` → Узлы → литографии → лагеря → Хранилища → арены → обломки → поселения | ядро + A + J |
| жизнь | арены и сброс (A), бой и добыча (B), Сигнал (J), караваны (K) | пакеты |

## 2. Общие решения

### 2.1 Конфиг (раздел `dimension`, холдер `SubstrateConfigSC`)
| Ключ | По умолчанию | Вид | Что |
|---|---|---|---|
| `requireSignal` | false | правило | ПВ-13: Подложка видна в списке Космического моста и мост открывает туда конец только игроку, применившему «Навигационную запись Si-14» (по UUID, у друга по LAN своя). Мост, стоящий в самой Подложке, видит её всегда |
| `caravans` | true | правило | ПН-12: караваны раз в 2 игровых дня. Выкл. — новых нет |
| `customSky` | false | **опция клиента** (не правило) | ПМ-19, ПК-22: своё небо Подложки. Каждый клиент решает сам; при ShadersModCore выключено само |

Правила — `public static`, уходят клиенту через `SubstrateNetSC` (`ruleFields()` — теперь 14). `customSky` — приватное поле с геттером, как id: его не трогают ни `SubstrateNetSC`, ни `ConfigSyncSC`. Числа баланса — константы в классах с TODO(balance).

### 2.2 Реестровые имена и id (фиксированы, только дописываются)
| Что | Имя / id | Владелец |
|---|---|---|
| Смотритель Пробоя | пул 22, `SiliconAge.breakdownWarden` (`EntityBreakdownWardenSC`), трекинг 128 | B |
| Катушка удержания | пул 23, `SiliconAge.containmentCoil` (`EntityContainmentCoilSC`), трекинг 128, обновление 20 тиков, без скорости; без яйца | B |
| Караванщик | пул 24, `SiliconAge.caravanMaster` (`EntityCaravanMasterSC extends EntityTunerSC`); без яйца | K |
| Страж каравана | пул 25, `SiliconAge.caravanGuard` (`EntityCaravanGuardSC extends EntityWardenSC`); без яйца | K |
| Блоки арены | `arenaSC`: 0 `breakdownConsole` (TE `SiliconAge.breakdownConsole`), 1 `coilPedestal`, 2 `arenaColumn`, 3 `arenaFloor`, 4 `arenaWall`, 5 `arenaLamp` (свет 12; консоль 7) | A |
| Руинный блок Ф5 | `ruinSC` мета 7 `burntCoil` «Выгоревшая катушка» (J дописывает в `BlockRuinSC.NAMES` и `ItemBlockRuinSC`) | J |
| Реликвии | `relicSC` 3 `breakdownKey`, 4 `breakdownMatrix` | A (подсказки, иконки) |
| Предметы | `arenaCoordinator` (A), `journalSC` меты 0–11 = №1–№12 (J), `paintScheme` 0 `lithographer` / 1 `tuner` (P), `lithoModule` (P), `navRecord` (P) | |
| Схемы окраски | `SingularScheme` L, M (ординалы 11, 12) | P (текстуры) |
| Пресет купола | `TileEntityFieldGeneratorSC.PRESETS[24]` = 0x48F0DC | D (имя) |
| Задания | `SingularLevel` графа 3: коды 33 (`C_EMP_KILLS`, 25), 53 (`C_WARDEN`, 1) | D (тексты) |
| Структуры | старт `SCSi14Arena`, куски `SCSi14Arn*` (база — `ComponentArenaSC` «SCSi14ArnPart», A); старт `SCSi14Wreck`, куски `SCSi14Wrk*` (база `ComponentWreckSC` «SCSi14WrkPart», J). Данные — `DIM-1414/data/SCSi14Arena.dat`, `SCSi14Wreck.dat` (ванильные структуры) | A, J |

Пул 0–21 (Ф1–Ф4) не меняется; следующий свободный — 26. Имена кусков не пересекаются с `SCSi14Set*`, `SCSi14Node*`, `SCSi14Lit*`, `SCSi14Cmp*`, `SCSi14Vlt*`.

### 2.3 Рецепты (`FinaleRecipesSC`, ядро)
- Ключ Пробоя = 4 × Фрагмент Ключа Пробоя, бесформенный.
- Навигационная запись Si-14 = Половинка запутанного кристалла (мета 1, NBT пары не важен) + Чип памяти + Контроллер, бесформенный.
- Других рецептов в Ф5 нет: Координатор, Модуль, схемы — сделки и добыча; журналы — находки. `-Dsc.packcheck`: пересечений быть не должно (предметы мода с метами).

### 2.4 Перечисления и коды
`ArenaDataSC`: EMPTY 0, READY 1, FIGHT 2, KEYED 3. `EntityContainmentCoilSC`: LIT 0, DARK 1, BROKEN 2. `WardenRulesSC`: фазы 1 `PHASE_HOLD`, 2 `PHASE_BREAKDOWN`, 3 `PHASE_OVERLOAD`. `JournalSC`: источники SRC_NODE 0 … SRC_WARDEN 6. `FinaleNetSC`: SIGNAL_STATE 1, BOSS_FX 2. `BossClientSC`: FX_CORE_RESET 1, FX_SILENCE 2, FX_COIL_DARK 3, FX_COIL_BROKEN 4, FX_COIL_LIT 5. `IMobActionSC`: 22–25. `ReputationSC.SRC_CARAVAN` 10. `TradeTableSC` (P дописывает ключи): `cartArenaCoordinator`, `elderLithoModule`, `elderTunerScheme`, `armModWide`; `TradeServerSC.RESULT_KEYS` (P, в конец): `caravanDeal` (редкая сделка не открыта).

### 2.5 Хранилище
| Что | Где | Владелец |
|---|---|---|
| Арены | `SCSi14ArenaRec` (не `SCSi14Arena`: так называются данные MapGenStructure арены в том же perWorldStorage) — perWorldStorage Подложки: запись на регион 4096 × 4096 (состояние, ключ в Консоли, бой: id, UUID босса, начало, последний игрок рядом, максимум игроков; трещины; `extra` «A», «B») | данные ядра; пишут A (состояние, ключ, бой, трещины — закрытие) и B (последний игрок, максимум игроков, открытые трещины) |
| Сигнал | `SCSi14Signal` — perWorldStorage: день броска, включён ли, перекрёсток, основание луча, окно, тайник сделан | данные ядра, пишет J |
| Добыча босса | глобальный реестр поселений, `PlayerRec.extra` «W»: `d` (день последней добычи), `k` (побед), `f` (первая победа была), `p` (отложенная добыча — список стеков) | B |
| Караваны | глобальный реестр: мировой `extra` «V» `{day}` (день последнего каравана); `PlayerRec.extra` «V» `{deal, used}` (редкая сделка) | K (ядро — запись сделки) |
| Навигационная запись | постоянный NBT игрока `scSi14Nav` (boolean) | ядро (`SubstrateSignalSC`), P зовёт `grant` |
| Открытые схемы | постоянный NBT игрока `scSchemes` (int, маска `SingularScheme.unlockBit`) | ядро (`FinaleRulesSC`), P зовёт `unlock` |
| Задания | `scSingLv` (прежнее хранилище уровней), `empKills`, `warden` | ядро |

Почему арены и Сигнал в perWorldStorage: это свойства самой Подложки (удалённая `DIM-1414` начинается заново). Всё, что у игрока, — по UUID: в глобальном реестре или в его постоянном NBT (переживает смерть, у друга по LAN своё).

### 2.6 Сеть
| Дискр. | Сообщение | Направление | Виды |
|---|---|---|---|
| 0 | `SubstrateNetSC.Message` | → клиент | id и правила (теперь 14) |
| 1, 2 | `SettleNetSC` | | без изменений |
| 3 | `HazardNetSC` | | без изменений |
| 4 | `FinaleNetSC.ToClient` | → клиент | `SIGNAL_STATE` {on, x, y, z, t} (J), `BOSS_FX` {k, x, y, z} (B) |

Обработчик клиента может прийти не в клиентском потоке — классы пакетов пишут только volatile-поля или свою очередь, которую разбирает клиентский тик. FakePlayer пакетов не получает (`send` проверяет). Свободны: канал `SiliconAgeDim` — 5 и дальше; `ArmorNetSC` — 11 и дальше. На сервер сообщений нет: журнал открывается на клиенте сам, всё остальное — щелчки, которые видит сервер. Список измерений моста фильтруется сервером на каждого зрителя — отдельной синхронизации Навигационной записи не нужно.

### 2.7 DataWatcher и телеграф
| Класс | Слоты |
|---|---|
| `EntityBreakdownWardenSC` (наследник `EntityArmoredMobSC`) | 20–25 ядра; 26 `DW_PHASE` (byte: биты 0–1 — фаза, 4 `FLAG_SHIELDED`, 8 `FLAG_WINDOW`, 16 `FLAG_OVERHEAT`, 32 `FLAG_RESET_ARMED`), 27 `DW_HIGHLIGHT` (byte: −1, 0–3, 4 `SET_ALL`), 28 `DW_COILS` (byte, биты катушек), 29 `DW_TIMER` (int, тики), 30 `DW_SCALE` (byte, игроки шкалы HP); 31 — B |
| `EntityContainmentCoilSC` (`EntityLiving`) | 12 `DW_BOSS` (int, id босса), 13 `DW_STATE` (byte), 14 `DW_RESERVE` (int ×10), 15 `DW_INDEX` (byte); 16–31 — B |
| `EntityCaravanMasterSC` (наследник `EntityTunerSC`) | 24–26 Наладчика; 27 `DW_CARAVAN` (byte: 0 идёт, 1 засада, 2 пришёл); 20–23, 28–31 — K |
| `EntityCaravanGuardSC` (наследник `EntityWardenSC`) | 20–27 стража; 28 `DW_CARAVAN` (int: id караванщика + 1); 29–31 — K |

Телеграф (`IMobActionSC`): `ACT_SILENCE` 22 — 3 с гула «Импульса глушения»; `ACT_OVERHEAT` 23 — 4 с без защиты (нагрудник раскалён добела); `ACT_CORE_RESET` 24 — последние 5 с перед «сбросом ядра»; `ACT_SUMMON` 25 — зов волны; пресс — `ACT_PRESS`. Сервер ставит в начале и возвращает 0, рисуют все клиенты (ПС-9).

### 2.8 Ключи NBT (фиксированы)
Сущности: `scFight` {ri, rj, id} у босса и катушек (+ `k` — индекс катушки), `scWarden` (состояние фаз B), `scCaravan` (id каравана — после перезапуска сущность уходит). TE: `scArena` {ri, rj} у Консоли. Старты: `ArenaI`, `ArenaJ`, `WreckCX`, `WreckCZ`. Игрок (постоянный NBT): `scSi14Nav`, `scSchemes`.

### 2.9 GUI
Новых окон нет: Консоль, Координатор, Навигационная запись, схема окраски — щелчком и строкой в чат; журнал — экран справочника (`GuiBook`) на своей статье, открывается на клиенте без контейнера. Свободные id: 16–19, 22+.

## 3. Правила Ф5 (коды — `docs/plan-substrate.md` §43–§53)
ПУ-8…ПУ-12 — арена; ПС-50…ПС-58 — босс; ПТ-21…ПТ-26 — его добыча, схемы окраски, ПЭ-1М; ПЛ-1…ПЛ-8 — журналы, обломки моста, Сигнал Литографов; ПН-12…ПН-16 — караваны; ПО-17…ПО-22 — сделки и Модуль Литографов; ПВ-13 — `requireSignal`; ПМ-19 — `customSky`; ПК-21…ПК-25 — конфиг, прогресс, совместимость.

## 4. Арена — пакет A (ПУ-8…ПУ-12, ПС-56)

### 4.1 Размещение (`ArenaLayoutSC`, ядро; ПУ-8…ПУ-10)
- Регион — 16 × 16 перекрёстков (4096 × 4096 блоков). Арена — на одном перекрёстке региона, «главные линии»: 4…11 линий внутрь по каждой оси (две арены не ближе 2304 блоков), по хешу; в регионе спавна (0, 0) — только 6…11 по обеим осям (≥ 2,1 км от точки спавна); никогда на перекрёстке с Хранилищем (`VaultLayoutSC.hasVault` — бункер сидел бы под ямой): до 8 попыток, иначе в регионе арены нет. TODO(balance).
- Арена **заменяет Мёртвый Узел** этого перекрёстка (ядро: `MapGenDeadNodeSC` его там не ставит).
- Яма: диск пола радиуса `PIT_R` 28 на высоте пола перекрёстка (`nodeFloorY`, 24…34 — каньоны входят в неё вровень: их дно меняется от Узла к Узлу линейно, на краю ямы разница ≤ 1 блока), кольцо стены до `RIM_R` 30 высотой `WALL_H` 12 над полом, над ним порода срезана до неба. Бокс ± 30 — внутри полосы Ф1 (40 от линий): биомы клеток Ф4 сюда не доходят; 4 × 4 чанка вокруг угла перекрёстка.
- Внутри: Консоль (`CONSOLE_DX` 12, `CONSOLE_DZ` −21 — к северо-северо-востоку, мимо устья северного каньона), 4 постамента катушек на диагоналях (`COIL_R` 22 → (±16, ±16)), 8 колонн 2 × 2 высотой `COLUMN_H` 7 на радиусе 17 под углами 22,5° + k·45° (между устьями и диагоналями), 8 ламп на стене (`LAMP_R` 27). `coilSpot`, `columnSpot`, `lampSpot`, `inColumn`, `nearest`, `arenaNear`, `arenaAtChunk`.

### 4.2 Структура (`MapGenArenaSC`, A; ПУ-9)
- Старт — в чанке угла перекрёстка (`arenaAtChunk`); `canSpawnStructureAtCoords` — `ArenaSC.generates()` и `arenaAtChunk != null`.
- `getStructureStart`: **сначала запись** `ArenaDataSC.make(i, j, floorY, built)`; запись уже есть (файл самой структуры потерян) — решает она: построенная арена снова с кусками, EMPTY — пустой старт; иначе пустой старт (EMPTY), если площадь ± 30 задевает уже созданный чанк (`StructureRuinStartSC.explored`, мир 0.2.x) **или** на этом перекрёстке уже есть старт Узла с кусками (`ChunkProviderSubstrateSC.of(w).nodes().startAt(i·16, j·16)` — Узел, созданный в прошлой сессии, чьи чанки ещё не построены). Иначе — куски.
- `carve(cx, cz, blocks, metas)` (вызов ядра в provideChunk, после старта и карвера): только если чанк лежит в боксе старта с кусками — внутри `RIM_R` всё выше пола → воздух до верха; диск пола — `arenaSC` FLOOR на `floorY` (под ним порода как была); кольцо стены — `arenaSC` WALL от пола до `floorY + WALL_H`; выше кольца — уступами наружу (воронка, TODO(balance) уклон) — чтобы со стен ямы не падала порода и было видно небо. В массивах — ни одного светящегося блока и ни одного `Blocks.stone`.
- Куски (populate, окно чанка + 8, с правилами `ComponentRuinSC`: свой сид, флаг 2, без каскада, свет вручную через `ChunkProviderSubstrateSC.glow`): колонны (COLUMN), постаменты (PEDESTAL на `floorY + 1`), Консоль (CONSOLE на `floorY + 1` → сразу `TileEntityBreakdownConsoleSC.setup(ri, rj)`), лампы (LAMP), шрамы Пробоя на полу (узор из FLOOR / WALL — по желанию). Узлов, остовов, сундуков нет — добыча здесь только с босса.
- `registerStructures`: A дописывает свои куски («SCSi14ArnPart» и др.). `covers` и `startAt` — ядро.

### 4.3 Запись арены (`ArenaDataSC`, ядро; ПУ-11)
Имя данных — `SCSi14ArenaRec` (файл `DIM-1414/data/SCSi14ArenaRec.dat`): `SCSi14Arena` занят данными самой структуры арены (MapGenStructureData пишет в тот же perWorldStorage под именем старта; совпадение дало бы ClassCastException при генерации). Поля и кто их пишет — Javadoc класса. A: `state`, `keyIn`, `fightId`, `bossUuid`, `fightStartedAt`, закрытие трещин (`takeCracks`). B: `lastPlayerAt`, `maxPlayers`, `addCrack`. Время — `getTotalWorldTime()` (часы Overworld через DerivedWorldInfo: идут и при выгруженной Подложке).

### 4.4 Консоль и Ключ (`TileEntityBreakdownConsoleSC`, `ArenaWorldSC.start`, A; ПУ-11, ПС-50)
- ПКМ по Консоли (сервер):
  - мирная сложность — отказ `sc.arena.peaceful` (босс-EntityMob исчез бы вместе с ключом);
  - запись FIGHT — `sc.arena.busy` (идёт бой);
  - READY: Ключ Пробоя в руке — ключ уходит в Консоль (`keyIn`, не креатив — тратится из руки), иначе — `sc.arena.nokey` (подсказка: 4 Фрагмента с Часовых);
  - READY с ключом или KEYED (ключ уже внутри) — отсчёт `COUNTDOWN_TICKS` 100 (5 с) строками `sc.arena.countdown` игрокам в 48 блоках, затем `fightId` = новый (время мира), `state` FIGHT, `fightStartedAt` / `lastPlayerAt` = сейчас, `maxPlayers` = игроков в 48, `EntityBreakdownWardenSC.summon(w, a, players)` (B; null — запись обратно в KEYED, `sc.arena.blocked`).
- Консоль без записи (поставлена руками) — `sc.arena.noarena`. FakePlayer — никогда. Пакет описания — светится ли ключ (`clientKey`; A перерисовывает блок).
- Ключ живёт в записи (`keyIn`), а не в инвентаре TE: сломанный чанк и `/setblock` его не размножат.

### 4.5 Координатор арены (`ItemArenaCoordinatorSC`, A; ПУ-12)
ПКМ в Подложке: ближайшая арена (`ArenaLayoutSC.nearest` в `SEARCH_REGIONS` 1 регионе вокруг; регион с записью EMPTY пропускается — берётся следующий) — направление (8 румбов, как Пеленгатор: `ItemRuinFinderSC.DIRS`, `sc.finder.dir.*`), расстояние, координаты и состояние (`sc.coordinator.arena` / `.fight` / `.keyed`); ничего — `sc.coordinator.none`; вне Подложки — `sc.coordinator.offworld`. Не расходуется. Картограф продаёт (P), элита роняет 2% (ядро).

### 4.6 Сброс и конец боя (`ArenaWorldSC`, A; ПС-56)
- Тик мира Подложки (END, раз в 20 тиков), по записям FIGHT, чей чанк Консоли загружен: `WardenRulesSC.resetDue(lastPlayerAt, now)` (B обновляет `lastPlayerAt`, пока босс тикает; у выгруженной арены оно старое — мир, загруженный через час, сбрасывается сразу) → сброс: босс и катушки этого боя удаляются, если загружены (и сами уходят при загрузке — `fightCurrent` ложь), трещины закрываются (`takeCracks` → FLOOR, только если там всё ещё трещина), запись KEYED (ключ в Консоли), `sc.arena.reset` игрокам рядом.
- FIGHT, но босса нет `LOST_CHECKS` 10 проверок подряд при загруженном чанке середины (мирная сложность включена посреди боя, `/kill`) — тот же сброс.
- `bossDied` (из `onDeath` B, после добычи): READY, `keyIn` false (ключ израсходован), трещины закрыты, `sc.arena.won`. Следующий бой — новым ключом.
- Первый тик Консоли после загрузки — та же проверка сброса (на случай, если тик мира ещё не дошёл).

### 4.7 Блоки, текстуры, совместимость (A)
`BlockArenaSC`: всё неломаемое (твёрдость −1, стойкость 6 000 000), без дропа, без pick block, никто не спавнится; иконки `arena_<name>.png` — `tools/gen_arena_tex.py` (тёмная обшивка Литографов, фиолетово-белые шрамы Пробоя; Консоль — тёмная плита со щелью ключа и бледным свечением реактора; постамент — медное кольцо с синей сердцевиной; колонны полосами оксида; лампа — холодная белая полоса); иконки реликвий `relic_breakdownKey.png`, `relic_breakdownMatrix.png`, `arenaCoordinator.png`. AE2 Spatial IO: `arenaSC` — в IMC `blacklist-block-spatial` (`SettlementCompatSC.sendImc`). Подсказки TooltipSC: блоки (`sc.tooltip.arena.<name>` + .details + .howto), Ключ, Матрица, Координатор (ключи — §11.3).

## 5. Смотритель Пробоя — пакет B (ПС-50…ПС-58, ПТ-21…ПТ-24)

Все числа — `WardenRulesSC` (ядро). TODO(balance) — открытые.

### 5.1 Существо (`EntityBreakdownWardenSC`)
| Что | Число |
|---|---|
| Размер | 1,4 × 3,5 (ПС-50) |
| HP | `maxHealth(n)` = 600 × (1 + 0,6 × (n − 1)), n — игроки в 48 блоках от середины арены и все настоящие игроки из журнала урона (стрелок издалека — тоже участник), наибольшее за бой (HP не уменьшается, когда кто-то ушёл), без потолка; при росте текущее HP сохраняет долю (`rescaled`). n = 1 → 600, 2 → 960, 3 → 1320, 4 → 1680 |
| Броня | Сингулярный комплект высшего уровня (`MobCombatSC.dressSingular`; схема — на выбор B: L «Литограф» или H «Пустота»), гелий (щит в фазах 1–2), запас 250–300 блокируемого урона, подзарядка 4 ед./с через 5 с без урона (TODO(balance)) |
| Оружие | Сингулярный клинок — 16 (потолок босса, §8 предложения); ПЭ-1М (`EmpMobGunSC`, `upgraded = true`): Точка по игроку с сильнейшей бронёй раз в 8–15 с после 1 с предупреждения, по правилам ПБ-17 (видимость, иммунитет, ЭМИ-экран поля) — во всех фазах |
| Поводок | 26 блоков от середины арены (`LEASH`) |
| Прочее | `IBossDisplayData` (единственный в моде), `alwaysElite` («Казнь» не действует), вне переписи директора, не исчезает, иммунен к огню, отбрасывание 0, опыт 500; ПТ-1: рождён в Si-14 (`summon`), босс из яйца — без катушек, без добычи |

Вид (§9 предложения): торс — огромный Сингулярный нагрудник-реактор с кольцами тёмных частиц; четыре руки, в двух — Сингулярный клинок и ПЭ-1М; «юбка» из кабелей; 3,5 блока. Модель `ModelBreakdownWardenSC`, рендер `RenderBreakdownWardenSC` (полоса здоровья — `BossStatus.setBossStatus(this, true)` в `doRender`, как у Иссушителя), текстура — `tools/gen_finale_boss_tex.py`.

### 5.2 Катушки удержания (`EntityContainmentCoilSC`; ПС-52)
Четыре на постаментах (`coilSpot`), ставит `summon`. Не существо: без AI, без гравитации и отбрасывания, не IMob, местные (мост не пропускает), не исчезают. LIT — питает щит (клиент рисует луч к груди босса); DARK — Точка ПЭ-1 (`empStruck`, режим POINT) на `COIL_OFF_TICKS` 300 (15 с); Импульс — ничего (false: старая Медлительность, безвредна); BROKEN — запас `COIL_RESERVE` 100 сбит любым уроном (`attackEntityFrom` списывает с запаса, не с HP). Устаревший бой — уходит. Без `empMobTargets` Точка катушки не гасит — только урон.

### 5.3 Фазы
| Фаза | HP | Механика |
|---|---|---|
| 1 «Удержание» | > 60% | Неуязвим, пока горит хоть одна катушка (`attackEntityFrom` → false, искры щита, `sc.warden.shielded` стрелку не чаще раза в 3 с). Все 4 погасли (`windowOpens`) — окно `WINDOW_TICKS` 200 (10 с): `mobArmor().emergency` (очки железной брони), щита нет, `FLAG_WINDOW`; по концу окна все катушки снова LIT (сломанные — с полным запасом). Волны Пустых оболочек: раз в `WAVE_EVERY` 600 (30 с) `waveSize(n)` = 2 + (n − 1), не больше `WAVE_MAX` 6 живых, из устьев каньонов (`SpawnDirectorSC.spawnAt`, рождены в Si-14, цель — ближайший игрок), `ACT_SUMMON` |
| 2 «Пробой» | 60–25% | Катушки гаснут насовсем. Каждые `HIGHLIGHT_EVERY` 300 (15 с) одна часть брони светится белым `HIGHLIGHT_TICKS` 100 (5 с, TODO(balance)) — `DW_HIGHLIGHT`; Точка в неё (`empHit`) — подавление ×1,5 (`highlightTicks`) и −40 запаса; подсвечен нагрудник — Точка гасит весь комплект на `SET_TICKS` 120 (6 с, `SET_ALL`). Гравитационный пресс: раз в 30 с, 3 с гула (`ACT_PRESS`), игроки в `PRESS_RADIUS` 8 прижаты 5 с (как у Часового: вниз каждый тик, Медлительность IV, без прыжка). «Импульс глушения»: раз в 30 с, 3 с гула (`ACT_SILENCE`, слышно за 16), потом каждый игрок в 16 блоках, которого босс видит (укрытие по линии взгляда спасает), получает Импульс ПЭ-1 по правилам: длительности по костюму, невосприимчивость 20 с, ЭМИ-экран поля, `BOSS_FX` FX_SILENCE. Пробойные трещины: раз в `CRACK_EVERY` 200 одна в полу (блок `substrateVent` мета ARC_CRACK вместо FLOOR, `Arena.addCrack`), не больше 8. Рои нанитов: раз в `SWARM_EVERY` 400 рой у края ямы под общим потолком частиц (12 на рой и на область 32 × 32) |
| 3 «Перегрузка» | < 25% | Щита нет (гелий выкипел: `helium = false`, без аварийного режима — очки Сингулярной брони, пока не перегрет). Каждые `OVERHEAT_EVERY` 300 последние `OVERHEAT_TICKS` 80 (4 с) — защиты нет (`overheated`: броня и щит не работают, урон в HP целиком), `ACT_OVERHEAT`, `FLAG_OVERHEAT`. Часы `FINISH_TICKS` 1200 (60 с, `DW_TIMER`): `coreResetDue` — `ACT_CORE_RESET` на 100 тиков (5 с, `FLAG_RESET_ARMED`), затем «сброс ядра»: 20 «Пробоем» (`BreakdownDamageSC.of(this)`) каждому игроку в `CORE_RESET_RADIUS` = 48 от середины (яма, край, уступы воронки — стрелку с края тоже нужно укрытие; TODO(balance)), между которым и грудью босса нет твёрдого блока (колонна, своя стена спасают), `BOSS_FX` FX_CORE_RESET; блоки не ломаются; часы снова |

Переходы фаз — по HP (`phaseOf`), строка в чат игрокам арены (`sc.warden.phase2`, `sc.warden.phase3`). ПЭ-1 по боссу (Точка игрока) — всегда по обычным правилам `MobArmorSC`; в фазе 1 подавление работает, урон — нет.

### 5.4 Без ПЭ-1 у мобов
`EmpMobGunSC.allowed()` ложь (`mobsUseEmp` выкл., `weapons.pe1.enabled` / `affectsPlayers` выкл.): у босса нет ПЭ-1М (ни Точек, ни «Импульса глушения»), нет категории трофея ПЭ-1М; остальное прежнее.

### 5.5 Учёт боя (B; ПС-56)
Раз в 20 тиков: игроки в 48 от середины (не креатив, не наблюдатели, живые) → `Arena.lastPlayerAt` = сейчас; они и все из журнала урона → `maxPlayers` = max, шкала HP; поводок; на первом тике и раз в 100 тиков — `fightCurrent` (ядро; иначе `setDead` до AI и боя: выгруженный устаревший босс не воюет ни тика). Катушки — так же по `scFight`. Журнал урона (`MobEventsSC.exportLog` / `restoreLog`) хранится в `scWarden` — доли переживают выгрузку и перезапуск. `DW_TIMER` — в секундах.

### 5.6 Добыча (`ownDrops`, `WardenLootSC`, B; ПТ-21…ПТ-24)
- Получатели: каждый игрок с долей урона ≥ 10% (`lootEligible`; доли — `MobEventsSC.damageShares`, удары по гелиевому щиту брони входят; удар, отбитый щитом катушек, урона не наносит и в доли не входит — иначе бесконечные удары по щиту перевесили бы весь бой). ПТ-1: рождён в Si-14, бой — текущий бой арены (`arenaWarden`: устаревший босс после сброса — только опыт, ключ остаётся в Консоли), умер в Подложке, `doMobLoot`. Очки прогресса босса (500) — тоже только с такого; из яйца — как за моба. Доля ниже — только опыт.
- Не чаще раза в 3 игровых дня (`lootDue`, `PlayerRec.extra` «W».`d`): раньше — `sc.warden.loot.cooldown` (сколько дней осталось), только опыт.
- Каждому своё (ПТ-22): Матрица Пробоя 1, Крупицы сингулярности 4–8 (**настоящие**, мимо суточного лимита: `CrumbLimitSC` не спрашивается), Сгустки сингулярности 1–2, Лом Сингулярной брони 4–8 (добыча мета 10); 25% — Схема окраски «Литограф» (ПТ-23); один бросок трофея `trophyMeta(roll, gearDropMultiplier × Добыча)`: Сингулярный нагрудник 5%, Сингулярный клинок 3%, ПЭ-1М 2% (трофеи меты 13, 19, 24); первая победа игрока (`W`.`f`) — Журнал Литографа №12 (ПТ-24).
- Доставка: игрок онлайн, жив и в Подложке — в инвентарь (не влезло — под ноги, его на 300 тиков: `MobEventsSC.lockTrophy`); иначе — в `W`.`p`, `WardenLootSC.deliverPending` отдаст при следующем входе или возрождении (где угодно), `sc.warden.loot.pending`.
- Каждому такому игроку — `SingularProgressSC.wardenDefeated(p)` (задание «Победить Смотрителя Пробоя»). 500 очков прогресса — убийце, по прежнему правилу `IBossDisplayData` (`SingularProgressSC.onDeath`).
- После раздачи — `ArenaWorldSC.bossDied(w, arena, this)`. Босс из яйца (`bound` ложь) — только опыт.

### 5.7 Трофеи (`TrophyTableSC`, B)
`SHOWN` += `META_PE1M` (24) → 25 мет в творческой вкладке и NEI. Восстановление ПЭ-1М — P (§9.6).

### 5.8 Клиент (`BossClientSC`, B)
Рендеры босса и катушек, лучи катушек к груди, белое свечение подсвеченной части, раскалённый нагрудник, частицы телеграфов, `onFx`: вспышка «сброса ядра» над ареной, волна «Импульса глушения», катушка погасла / сломана / зажглась. Звуки — по желанию B, `sounds.json` (только дописывать): гул удержания, гул глушения, сирена сброса. WAILA (`compat/WailaEntitySC`, только новые ветки): у босса — фаза, у катушки — состояние и запас.

## 6. Журналы и обломки моста — пакет J (ПЛ-1…ПЛ-4)

### 6.1 Журналы Литографа (`JournalSC`, ядро; `ItemJournalSC`, `JournalClientSC`, J)
- 12 журналов, мета = номер − 1, стек 1; №12 — epic. Имя «Журнал Литографа №%s». ПКМ на клиенте — `SCMod.proxy.openJournal(n)` → `JournalClientSC.open`: экран справочника (`GuiBook`) на статье, собранной на лету (`BookEntry` «journal.<n>» главы WORLD, которой нет в списках `BookContent`: журналы читают, найдя их, — ни указателя, ни поиска) — заголовок, строка даты (тусклая), страницы-абзацы `sc.journal.<n>.p0 …` (`JournalSC.PAGES`), иконка обложки.
- Где (ПЛ-2): №1, №2 — сундуки Мёртвых Узлов (6% сундука), №3, №4 — литографии (10%), №5–№8 — сундуки Хранилищ (30%) и Часовой (50% — `EntitySentinelSC.factionLoot`), №9, №10 — обломки моста (в каждом), №11 — тайник Сигнала (всегда), №12 — первая победа над Смотрителем. TODO(balance).
- J правит `RuinLootSC` (`nodeChest`, литографии, `vaultChest`/`fillVault`) — только добавить бросок `JournalSC.roll(SRC_*)`, и `EntitySentinelSC.factionLoot` — одна строка.
- Тексты (D): «последние дни Литографов их словами» — §1 и §10 предложения: протравленная планета, Сингулярный реактор, «Пробой», Наладчики, Оболочки, Отступники, мосты; №12 — о последнем мосте, который вёл в «зелёный мир с одной луной». Без чисел механики, без имён реальных людей.

### 6.2 Обломки моста Литографов (`WreckLayoutSC`, ядро; `MapGenWreckSC`, J; ПЛ-3, ПЛ-4)
- Где: клетка плато (чистый вид PLATEAUS — земля всегда Ф1), не клетка спавна, не клетка поселения, шанс 20%; середина — на 76 блоков от середины клетки к одному из углов (52 от линий: мимо каньонного пояса, арен, Узлов, хижины литографии). TODO(balance).
- Что: кольцо Выгоревших катушек (`ruinSC` мета 7 `burntCoil` — J дописывает: руинный, ломается, дроп — Лом Нано 30% / Прошивка, без рецептов, как остальные руинные) радиуса 9–12 (≈ 30 блоков в поперечнике), наклонено по оси (`axis`) — одна половина утоплена в террасу, другая поднята до 6 (`lean`), с разрывами; вокруг — Выгоревшие панели и остов машины; под поднятой стороной один сундук (`Blocks.chest`): Журнал №9 или №10 (100%), Лом Нано 2–4, Прошивка 1–3, 30% — компонент (Контроллер / Транзистор). TODO(balance).
- Структура: старт в чанке середины (`siteAtChunk`), пустой при открытой земле; кусок — `ComponentWreckSC` («SCSi14WrkPart») по чистым высотам (`terraceY`), с правилами `ComponentRuinSC`. Бокс ± 14.
- Текстура `ruin_burntCoil.png` (`tools/gen_finale_lore_tex.py`), подсказки `sc.tooltip.ruin.burntCoil` (+ .details, .howto).

## 7. Сигнал Литографов — пакет J (ПЛ-5…ПЛ-8)

### 7.1 Расписание (`SignalRulesSC`, ядро; `SignalSC`, J)
- Раз в игровые сутки (`SettlementSC.day`) — бросок 15% (`rolls`), день помнит `SignalDataSC.rolledDay` (ни повторного броска после перезапуска, ни второго от второго игрока). Начало — `startOf` (1000…20 000 тиков суток); в момент начала в Подложке должен быть игрок, иначе сигнал этого дня просто прошёл (ничего не моделируется без игрока). Длится 12 000 тиков (10 мин). TODO(balance).
- Узел (ПЛ-6): `crossingFor(seed, day, x, z)` от случайного настоящего игрока в Подложке — перекрёсток в 300…800 блоках, не в кольце спавна, не арена. Основание луча — дно перекрёстка (`nodeFloorY + 1`; в старой земле без Узла это то же дно каньона).
- Включение: `SIGNAL_STATE` {on: true, x, y, z, t} всем в Подложке; `sc.signal.start` с направлением (8 румбов) и расстоянием каждому. Конец (`endAt`): {on: false}, `sc.signal.end` игрокам в 128 блоках. Вошедшему в Подложку, вошедшему на сервер в ней, возродившемуся там — текущее состояние (`playerUpdate`).

### 7.2 Тайник и патруль (J; ПЛ-7)
Пока сигнал горит, настоящий игрок ближе 24 к перекрёстку (чанк загружен) — один раз: сундук на середине площади (на поверхности дна, только вместо воздуха — постройку игрока не трогает): Чипы памяти 1–3, Контроллеры 1–2, Опечатанная Крупица 1 (лимит 3 в сутки — при распечатывании), Журнал №11; патруль Пустых оболочек `patrolSize(n)` = 3 + (n − 1) (`spawnAt`, рождены в Si-14, дом — площадь), `sc.signal.cache`. `cacheDone` — второго тайника нет.

### 7.3 Луч (`SignalClientSC`, J; ПЛ-8)
Вертикальный столб бледно-фиолетово-белого света от дна до Y 256 в (x, z), рисуется в `RenderWorldLastEvent`, пока сигнал горит и клиент в Подложке; виден за 1024 блока (дальше дальности прорисовки: без тумана, ширина растёт с расстоянием, аддитивное смешивание, тест глубины — холмы его прячут), медленная пульсация. Сброс — выход с сервера, уход из Подложки.

## 8. Караваны — пакет K (ПН-12…ПН-16)

### 8.1 Когда и откуда (`CaravanRulesSC`, ядро; `CaravanSC`, K)
- Правило `caravans`; раз в 2 игровых дня (`caravanDay`), днём (1000…9000 тиков суток), проверка раз в 200 тиков; не больше одного каравана в мире за раз; день каравана помнит мировой `extra` «V».`day`.
- Цель — первое найденное поселение с загруженным Ядром, у которого в 96 блоках от Ядра есть настоящий игрок не в креативе; без такого игрока каравана нет (долг не копится).
- Караван «идёт из» ближайшего соседнего поселения по реестру (в 1024 блоках; нет — со стороны Причала): появляется в 40…64 блоках за краем купола с той стороны, на плато той же клетки (каньонов не пересекает — по лору Наладчики переходят Скрайб-линии короткими проколами своих Причалов), и идёт к воротам.

### 8.2 Состав и движение (K)
Караванщик (`EntityCaravanMasterSC` — Наладчик с рамой и ящиками на спине) и 2 стража (`EntityCaravanGuardSC` — Постовые: настоящая броня, честный бой; убийство — как убийство стража, ПГ-8). Не привязаны к слотам поселения (их смерть не опустошает слот). Движение — помощником движения к точкам пути на плато (без поиска пути на сотни блоков), `stepHeight` под ступени террас 2 блока, стражи держатся в 6 блоках от караванщика и бьют засаду и любых враждебных в 16; в засаде караванщик стоит за стражами. Жители и стражи под куполом и в поясе 24 защищены от игроков как прежде (ПЖ-2).

### 8.3 Засада и награда (K; ПН-14, ПН-15)
- 40% караванов (`ambushRoll` по дню) — засада на полпути: `ambushSize(n)` = 3 + (n − 1) Отступников (Мародёры, со второго — Штурмовик) в 16 блоках от пути, рождены в Si-14, цель — караван; `sc.caravan.ambush` игрокам рядом.
- Отбита (`defended`: все засадники мертвы, караванщик жив; хоть один снят живым — мирная сложность, чистильщик — засада снята без награды) — каждому настоящему игроку в 48 блоках от караванщика: +20 «Доверия» (`ReputationSC.give(rec, 20, SRC_CARAVAN, day)`, без суточного потолка), `CaravanSC.grantDeal(p)` — редкая сделка Оружейника открыта на 3 игровых дня, `sc.caravan.defended`. Караванщик убит — `sc.caravan.lost`, ничего.
- Убийства засадников — обычные (контракты, «Доверие» за убийства у купола, трофеи).

### 8.4 Конец (K; ПН-16)
Дошёл до ворот (6 блоков) или прошло 6000 тиков (5 мин) — караван уходит в купол (сущности исчезают с искрами Наладчиков), `sc.caravan.arrived`. Перезапуск сервера забывает идущий караван (в памяти, как налёт): сущности с устаревшим `scCaravan` уходят на первом тике. Рендеры — по умолчанию Наладчика и стража (RenderManager находит рендер родителя); свои — по желанию K (`CaravanClientSC`).

## 9. Прогресс, косметика, сделки — пакет P (ПТ-25, ПТ-26, ПО-17…ПО-22, ПК-23)

### 9.1 Схемы окраски «Литограф» и «Наладчик» (ПТ-25)
- Схемы `SingularScheme.L`, `M` (ядро) — «открываемые»: Станция Сингулярной брони предлагает их в ◄ ► только игроку, который их открыл. P: `ContainerSingularStationSC` (кнопки ◄ ►) передаёт игрока → `TileEntitySingularStationSC.cycleScheme(dir, player)` (новая перегрузка; старую оставить) → `SingularStationMath.cycle(s, dir, FinaleRulesSC.unlockedSchemes(player))`.
- `ItemPaintSchemeSC`: ПКМ (сервер, не FakePlayer) — `FinaleRulesSC.unlock(p, scheme)`: новая — `sc.paint.unlocked`, предмет тратится (не креатив); уже открыта — `sc.paint.known`, предмет остаётся.
- Текстуры обеих схем — **все**, которые проверяет `SelfTestSC` («textures of every scheme»: 4 иконки частей, 2 слоя модели и их слои свечения — 8 кадров, белый и красный) и инструменты (иконки Сингулярного клинка по формам и бура — `ItemBladeSC.formIconName` / `ItemDrillSC`): строки палитры в `tools/gen_singular_tex.py` (и в генераторе инструментов, если он отдельный). Иконки предмета схемы — `paintScheme_<name>.png`.

### 9.2 Задания Сингулярного прогресса (ПК-23; ядро — данные и счёт)
Готово в ядре: четвёртая графа `TASK_NEED` (3.3 — 25, 5.3 — 1), счётчики `C_EMP_KILLS` (в `SingularProgressSC.onDeath`: бронированный местный с подавленной частью, убийца — в костюме, как остальные задания) и `C_WARDEN` (`wardenDefeated`, зовёт B). P ничего не пишет; D — тексты `sc.levelgui.task.3.3` / `.5.3`, `sc.tooltip.armor.singular.task.3.3` / `.5.3` (с `%s / %s`). C_KILLS и так считает бронированных врагов Подложки (они IMob) — D проверяет это в мировом тесте.

### 9.3 Пресет купола «Бирюза Наладчиков»
`PRESETS[24]` = 0x48F0DC (ядро; цвет каждого пресета уникален — экран находит пресет по цвету). Свой цвет поле принимает и так, поэтому пресет не запирается. D — имя `sc.fieldzone.preset.24`.

### 9.4 Модуль Литографов (ПО-20)
Ядро: слот улучшений Генератора поля принимает его, содержание ×0,5 (`FinaleRulesSC.upkeep`: вверх, работающее поле не бесплатно; дождевой щит — от уменьшенного); один достаточно. Платит хозяин кластера — модуль в его слоте. P: `ItemLithoModuleSC` (иконка, подсказка), `GuiFieldGeneratorSC` — строка содержания с модулем (сейчас экран считает `upkeepFor` без него).

### 9.5 Сделки (`TradeTableSC`, `TradeServerSC`, `TradeOfferSC` по необходимости, P; ПО-17…ПО-22; TODO(balance))
| Житель | Игрок отдаёт | Получает | Уровень | Лимит | Ключ |
|---|---|---|---|---|---|
| Картограф | 16 КЧ + 1 Крупица сингулярности | Координатор арены | Почётный | 1 в сутки | `cartArenaCoordinator` |
| Старейшина | Матрица Пробоя + 48 КЧ | Модуль Литографов | Свой | без своего (Матрица и так редка) | `elderLithoModule` |
| Старейшина | 3 Крупицы + 32 КЧ | Схема окраски «Наладчик» | Почётный | 1 в сутки | `elderTunerScheme` |
| Оружейник | 24 КЧ + 2 Контроллера | Модуль ПЭ-1: Широкий канал | Знакомый | одна, пока `CaravanSC.dealOpen(p)`; после сделки `useDeal(p)`; закрыта — отказ `R_CARAVAN_DEAL` | `armModWide` |

ПО-7 соблюдается: Крупица и Матрица — только часть цены покупки (`boughtAndSold` их не считает); Координатор, Модуль, схема, Широкий канал — только продаются; `forbiddenOut` их не запрещает (не броня, не клинок, не ПЭ-1). Крупица, отданная в цене, в суточный лимит не идёт (лимит — на получение). Заменить TODO(Ф5) в `TradeTableSC`.

### 9.6 Восстановление ПЭ-1М (`TrophyRestoreSC`, P; ПТ-26)
Мета 24 (ПЭ-1М) — Сингулярного уровня (`tier` уже так говорит): только Сингулярная станция обслуживания, 1 Сгусток сингулярности + `SINGULAR_STATION_EU`; результат — `ModItems.empGun` мета 1 без модулей, заряд 0, настройки по умолчанию (NBT трофея не копируется). У Оружейника — отказ `R_SINGULAR_ONLY`, как у Сингулярных частей. `refusal`, `restored`, `metas`, `singular` — дописать ПЭ-1М; старые ответы прежние.

## 10. Небо и `requireSignal` — пакет P (ПМ-19, ПВ-13, ПК-22)

### 10.1 Своё небо (`SubstrateSkySC`, P)
`renderer()` уже отвечает null при выключенном `customSky` и при ShadersModCore (ядро). P пишет `IRenderHandler`, рисуемый вместо `RenderGlobal.renderSky`: цвет неба — от провайдера (с фиолетом бури), звёзды — сеткой микросхемы (короткие прямые отрезки между яркими точками по постоянному сиду — каждую ночь те же), тусклое кольцо низко над горизонтом («аккреционный диск»), маленькое резкое белое солнце и ванильные фазы луны, закатные цвета из `calcSunriseSunsetColors`; списки отображения строятся один раз (и заново после перезагрузки ресурсов), без выделения памяти в кадре. Только клиент.

### 10.2 Навигационная запись Si-14 (`ItemNavRecordSC`, P; ПВ-13)
ПКМ (сервер, не FakePlayer): `SubstrateSignalSC.grant(p)` — новая: `sc.navrecord.learned`, предмет тратится; уже есть: `sc.navrecord.known`, остаётся. При выключенном `requireSignal` запись всё равно ставится (включат позже — игрок уже «видит» мир) и строка `sc.navrecord.open` («мир и так открыт всем»). Подсказка говорит, включено ли правило на этом сервере (правило синхронизировано). Рецепт — ядро. Фильтр моста — ядро (§0). Правило — для каждого путника: мост (и чужой, открытый другом) не пропускает в Подложку игрока без своей записи (`TileEntityBridgeControllerSC.enter`, `sc.bridge.msg.noSignal`).

## 11. Тексты, справочник, NEI, README, CHANGELOG, тесты — пакет D

### 11.1 Самотест `debug/SelfTestFinaleSC` (из `SelfTestSC.run`, после `SelfTestHazardSC`)
Каждый раздел — сам по себе (незаконченное тело пакета роняет только свой раздел). Вывод — `[SC-TEST] PASS / FAIL finale: …`.
- Конфиг: 14 правил (`requireSignal` false, `caravans` true), `customSky` — приватное и не правило.
- `SubstrateSignalSC.visible`: таблица истинности; `filterDims` убирает только Подложку.
- `ArenaLayoutSC`: на выборке регионов (−8…8)² трёх сидов — в каждом ≤ 1 арена, никогда на перекрёстке Хранилища, в регионе спавна ≥ 2000 блоков от (128, 128), две арены ≥ 2304 блоков; `arenaAtChunk` / `arenaNear` / `isArena` / `nearest` согласованы; постаменты, колонны, Консоль и лампы — в диске ямы (`inPit`) и вне устьев каньонов (|dx| и |dz| > 7 для колонн и постаментов), колонны не пересекаются; бокс ± 30 ≤ 40 (полоса Ф1).
- `ArenaDataSC`: NBT туда-обратно (все поля, трещины), `make` не перезаписывает.
- `WardenRulesSC`: `maxHealth` 600 / 960 / 1320 / 1680; `phaseOf` на 60,01% / 60% / 25,01% / 25%; `overheated` — ровно 80 тиков из каждых 300; `coreResetDue(1199)` ложь, `(1200)` правда; `resetDue`; `lootEligible(0,0999)` ложь, `(0,1)` правда; `lootDue` (нет добычи, 2 дня, 3 дня); `trophyMeta` — доли 5 / 3 / 2% по выборке, ×mul, меты 13 / 19 / 24; `rescaled`.
- `JournalSC`: каждый номер 1–12 — в каком-нибудь пуле, №12 — только SRC_WARDEN, `metaOf` / `numberOf`, `roll` с шансом 0 и 1.
- `WreckLayoutSC`: детерминизм; никогда в клетке спавна, поселения, не-плато; бокс дальше 30 от линий (вне каньонного пояса, арены, Узла); доля клеток плато с обломками 20 ± 4% (выборка 4096 клеток).
- `SignalRulesSC`: доля дней 15 ± 3% (2000 дней), `startOf` в окне, `crossingFor` — не арена, не кольцо спавна, расстояние 300…800 (± 256 на округление до линии).
- `CaravanRulesSC`: `caravanDay`, `inWindow`, доля засад 40 ± 4%, `ambushSize`, `defended`, `dealOpen`.
- `FinaleRulesSC`: `upkeep` (0 → 0, 1 → 1, 7 → 4, 100 → 50, без модуля — прежнее), `open` / маска; `SingularScheme`: 13, L и M открываемые, `unlockBit` 1 и 2; `SingularStationMath.cycle`: двухаргументная — как раньше (A → B, A ← K, K → A), с маской 3 — A ← M, K → L.
- `SingularLevel`: `TASKS` 4, `taskNeed(3, 3)` 25, `(5, 3)` 1, `(2, 3)` и `(4, 3)` 0, три прежние графы как были; `taskValue` кодов 33 / 53 из `empKills` / `warden`; `LevelMessage` с 16 значениями.
- Пул: 26 имён, `nameOf(22)` «breakdownWarden» … `nameOf(25)` «caravanGuard», `nameOf(26)` null; `hasEgg` ложь у 23–25; таблицы весов длиной 26 с нулями у 22–25; HP заготовки босса 600.
- Реликвии: 5 мет, стеки 1 / 4 / 64 / 1 / 16; рецепт Ключа (4 Фрагмента) и Записи есть.
- Пресеты: 25, все цвета разные, `[24]` = `TUNER_DOME_RGB`.
- Тексты: все ключи §11.3 в обоих lang с теми же аргументами, без кириллицы и «» в en_US; глава WORLD строится без сырых ключей; статьи `F5_ARTICLES`.
- **Честные поправки Ф1–Ф4** (D, только они): `SelfTestHazardSC` — правил 12 → 14, пул 22 → 26 (`nameOf(22)` больше не null), `ItemRelicSC.NAMES` 3 → 5, `SHOWN` 24 → 25 (ПЭ-1М); `SelfTestCombatSC` — правил 12 → 14, пул 22 → 26; число трофеев SHOWN там, где его считают. Ни одна проверка ПЭ-1 (`SelfTestEmpSC`, `WorldTestEmpSC`) не правится.

### 11.2 Мировой тест `debug/WorldTestFinaleSC` (`-Dsc.worldtest=true`, после `WorldTestWildSC`)
Вывод — `[SC-WORLDTEST] PASS / FAIL / SKIP / INFO finale: …`. SKIP, если `!SubstrateSC.active()`. Чанки — тикетом, всё изменённое возвращается, сущности удаляются, FakePlayer в мир не спавнится (как в Ф3–Ф4). D добавляет в `WorldTestWildSC` флаг `finished` и ждёт его.
1. Регистрация: 4 новых имени в `EntityList`, структуры `SCSi14Arena`, `SCSi14Wreck`, блок `arenaSC` и TE, 5 предметов, меты реликвий 3–4.
2. Арена: ближайшая к (0, 0) по `nearest` — сгенерировать её чанки: пол `arenaSC` FLOOR на `floorY` в диске, воздух над ним, стена, Консоль с записью READY и `isSet`, 4 постамента, 8 колонн, ни одного `Blocks.stone` в боксе, старта Узла на перекрёстке нет; каскада нет (не загружен ни один чанк за «окно + 1»); арена, чей чанк создан заранее (тест сначала создаёт чанк при пустой записи), — EMPTY и пустой старт.
3. Консоль: на мирной сложности — отказ, ключ не взят; с ключом (FakePlayer с ключом в руке) — FIGHT, через `COUNTDOWN_TICKS` босс у середины и 4 катушки на постаментах, ключ ушёл в запись; второй щелчок — `busy`.
4. Бой (тестовый босс в арене): удар при горящей катушке — HP цел; Точка в катушку (через `empStruck`) — DARK; все 4 тёмные — окно, удар проходит с очками железа; конец окна — все LIT; урон до 59% — фаза 2, подсвеченная часть — Точка ×1,5 и −40 запаса; трещины появляются и записаны; до 24% — фаза 3: `overheated` окно — удар целиком в HP; часы 60 с (перевод) — «сброс ядра»: FakePlayer-цель за колонной — без урона, на открытом месте — 20 «Пробоя»; блоки целы.
5. Сброс: `lastPlayerAt` −6000 — тик A: KEYED, босс и катушки ушли, трещины закрыты (FLOOR), ключ в записи; щелчок — снова бой без ключа в руке.
6. Добыча (решающая функция B через `ownDrops` с подставленными долями двух «настоящих» записей реестра): 60% и 40% — добыча у обоих (Матрица, Крупицы 4–8 сверх лимита — `CrumbLimitSC.left` не изменился, Сгустки, Лом), 5% — ничего; повтор в тот же день — откат; первая победа — Журнал №12, вторая — нет; игрок не в сети — `W`.`p`, доставка при «возрождении»; `wardenDefeated` увеличил `warden`; `bossDied` — READY, `keyIn` ложь.
7. Обломки: ближайшая клетка с `WreckLayoutSC.site` — сгенерировать: Выгоревшие катушки кольцом, сундук с Журналом №9 или №10.
8. Сигнал: включить (через `SignalDataSC` / помощник J) — `SIGNAL_STATE`, FakePlayer рядом с перекрёстком — сундук с Журналом №11 и патруль Оболочек (рождены в Si-14), второй раз — нет; конец окна — выкл.
9. Караван: тестовое поселение с Ядром, FakePlayer-защитник — запуск (помощник K) — караванщик и 2 стража за куполом; засада: засадники убиты — +20 «Доверия», `dealOpen` правда, сделка `armModWide` у Оружейника проходит один раз; караванщик убит — ничего.
10. `requireSignal`: правило вкл. — `writeState` для зрителя без записи не содержит Подложку, `plan` в Подложку — `noSignal`; после `grant` — есть; мост в самой Подложке — всегда есть; правило вернуть.
11. Модуль Литографов: поле с модулем в слоте — `upkeepPerTick` вдвое меньше (вверх); без — прежнее.
12. ПЭ-1М: трофей мета 24 на Сингулярной станции со Сгустком — ПЭ-1М мета 1 без модулей; у Оружейника — отказ.
13. Прогресс: убийство бронированного моба с подавленной частью игроком в Сингулярной броне (через `SingularProgressSC.onDeath` с источником урона от «настоящего» игрока не удаётся — FakePlayer не считается: проверить `suppressedArmoured` на живом мобе и `C_KILLS` по классу как `IMob`).
14. Регресс: `WorldTestEmpSC`, `WorldTestSubstrateSC`, `WorldTestSettlementSC`, `WorldTestCombatSC`, `WorldTestArmourerSC`, `WorldTestHazardSC`, `WorldTestWildSC` — как раньше (по логу).

### 11.3 Lang (CRLF, UTF-8; ключи `ключ=ru | en` присылают пакеты)
Ключи ядра (D вписывает сразу):
- Мост: `sc.bridge.refuse.noSignal` = «Подложки нет в навигации моста: нужна «Навигационная запись Si-14»» | «The Substrate isn't in this bridge's navigation: a "Si-14 Navigation Record" is needed».
- Сущности: `entity.SiliconAge.breakdownWarden.name` «Смотритель Пробоя» | «Breakdown Warden», `containmentCoil` «Катушка удержания» | «Containment Coil», `caravanMaster` «Караванщик» | «Caravan Master», `caravanGuard` «Страж каравана» | «Caravan Guard».
- Предметы: `item.siliconage.relic.breakdownKey.name` «Ключ Пробоя» | «Breakdown Key», `breakdownMatrix` «Матрица Пробоя» | «Breakdown Matrix»; `item.siliconage.arenaCoordinator.name` «Координатор арены» | «Arena Coordinator»; `item.siliconage.journalSC.name` «Журнал Литографа №%s» | «Lithographer's Journal No. %s»; `item.siliconage.paintScheme.lithographer.name` «Схема окраски «Литограф»» | «Paint Scheme "Lithographer"», `.tuner.name` «Схема окраски «Наладчик»» | «Paint Scheme "Tuner"»; `item.siliconage.lithoModule.name` «Модуль Литографов» | «Lithographers' Module»; `item.siliconage.navRecord.name` «Навигационная запись Si-14» | «Si-14 Navigation Record».
- Блоки: `tile.siliconage.arena.<breakdownConsole|coilPedestal|arenaColumn|arenaFloor|arenaWall|arenaLamp>.name` «Консоль Пробоя», «Постамент катушки», «Колонна Эпицентра», «Плита Эпицентра», «Стена Эпицентра», «Лампа Эпицентра» | «Breakdown Console», «Coil Pedestal», «Epicentre Column», «Epicentre Floor Plate», «Epicentre Wall Plate», «Epicentre Lamp»; `tile.siliconage.ruin.burntCoil.name` «Выгоревшая катушка» | «Burnt Coil».
- Схемы, пресет: `sc.singular.scheme.l` «Литограф» | «Lithographer», `.m` «Наладчик» | «Tuner»; `sc.fieldzone.preset.24` «бирюза Наладчиков» | «Tuners' turquoise».
- Задания: `sc.levelgui.task.3.3` «Подавить ПЭ-1 бронированных врагов» | «Kill armoured enemies under a PE-1's suppression», `sc.levelgui.task.5.3` «Победить Смотрителя Пробоя» | «Defeat the Breakdown Warden»; `sc.tooltip.armor.singular.task.3.3` «Подавить ПЭ-1 и добить бронированных врагов: %s / %s» | …, `.5.3` «Победить Смотрителя Пробоя: %s / %s» | ….
- Подсказки (у каждого — кратко, `.details`, `.howto`): `sc.tooltip.relic.breakdownKey`, `sc.tooltip.relic.breakdownMatrix`, `sc.tooltip.arenaCoordinator`, `sc.tooltip.journal` (`.details`, `.howto`), `sc.tooltip.paint.lithographer`, `sc.tooltip.paint.tuner`, `sc.tooltip.lithoModule` (`.details` с %s — процент), `sc.tooltip.navRecord` (`.details.on`, `.details.off`, `.howto`), `sc.tooltip.arena.<name>` (6), `sc.tooltip.ruin.burntCoil`.
- Журналы: `sc.journal.<1..12>.title`, `.date`, `.p0` … (`JournalSC.PAGES`).
- Сообщения пакетов (присылают; ниже — ожидаемые): A — `sc.arena.peaceful`, `.nokey`, `.inserted`, `.countdown` (%s), `.busy`, `.blocked`, `.reset`, `.won`, `.noarena`; `sc.coordinator.arena` (направление, расстояние, x, z), `.fight`, `.keyed`, `.none`, `.offworld`. B — `sc.warden.shielded`, `.window`, `.phase2`, `.phase3`, `.reset.warn`, `.loot` , `.loot.cooldown` (%s), `.loot.pending`, `.journal`. J — `sc.signal.start` (направление, расстояние), `.end`, `.cache`. K — `sc.caravan.coming` (имя поселения), `.ambush`, `.defended`, `.lost`, `.arrived`. P — `sc.paint.unlocked` (%s), `.known` (%s), `sc.navrecord.learned`, `.known`, `.open`; `sc.trade.offer.<cartArenaCoordinator|elderLithoModule|elderTunerScheme|armModWide>`, `sc.trade.result.caravanDeal`, `sc.trade.refuse.caravanDeal`, `sc.trade.row.caravanDeal` (по схеме `RESULT_KEYS`).
- Смертей новых нет: «сброс ядра» — `death.attack.scBreakdown` Ф3.

### 11.4 Справочник (глава WORLD, `BookWorldSC`, `BookContent`)
Новые статьи в конце главы (`F5_ARTICLES`): `world.lithographers` «Литографы» (лор §1 и §10: кто они, Пробой, три силы, мосты, журналы как источник; без спойлера №12), `world.arena` «Эпицентр Пробоя» (где, Координатор, Консоль, Ключ из 4 Фрагментов, мирная сложность, сброс через 5 мин), `world.warden` «Смотритель Пробоя» (вид, HP по игрокам — таблица из `WardenRulesSC.maxHealth`, три фазы с числами, катушки, подсветка, пресс, Импульс глушения, перегрузка, сброс ядра и колонны), `world.wardenLoot` «Добыча Смотрителя» (10%, 3 дня, гарантированное, 25%, трофеи, Журнал №12), `world.journals` «Журналы Литографа» (где какие номера — из `JournalSC`), `world.wrecks` «Обломки моста Литографов», `world.signal` «Сигнал Литографов» (`SignalRulesSC`), `world.caravans` «Караваны» (`CaravanRulesSC`), `world.cosmetics` «Схемы окраски и купол» (L, M, как открыть, пресет 24), `world.lithoModule` «Модуль Литографов» (−50%), `world.navrecord` «Навигационная запись Si-14» (`requireSignal`, рецепт), `world.progress` «Сингулярный прогресс в Подложке» (C_KILLS, два новых задания), `world.sky` «Небо Подложки» (`customSky`, ShadersModCore). Дописать: `world.substrate` (requireSignal, customSky — строкой), `world.trophies` (ПЭ-1М, 25 мет), `world.trade` (сделки Ф5 из `TradeTableSC`), `world.crumbs` (с босса — сверх лимита), `world.elites` (Координатор 2%), `world.vaults` (Журнал 50%, Фрагменты → Ключ), бестиарий — босс и катушка. Все числа — из кода. G по новым блокам, предметам и яйцу босса открывает статьи. Сквозная сверка Ф1–Ф5: каждая сущность пула, каждый блок и предмет измерения есть в какой-нибудь статье.

### 11.5 NEI
Скрыть: `arenaSC` (все меты — только в Эпицентре), яйца 23–25 (их и так нет в `getSubItems`). Показать: реликвии 3–4, Координатор, 12 журналов, 2 схемы, Модуль, Запись, руинную мету 7, трофей ПЭ-1М (по `SHOWN`), яйцо босса. «Как получить»: Ключ — рецепт + «Фрагменты — с Часовых»; Матрица — «добыча Смотрителя Пробоя»; Координатор — «Картограф, «Почётный»; элита 2%»; журналы — по номерам (`JournalSC`); схемы — «Литограф» — Смотритель 25%, «Наладчик» — Старейшина; Модуль — Старейшина за Матрицу; Запись — рецепт; ПЭ-1М-трофей — Смотритель 2%. `NeiCoverageSC` не должен отмечать новое.

### 11.6 README (черновик `docs/readme-substrate-draft.md`, ru + en)
Довести до релизного вида целиком (Ф1–Ф5): вход и цена, `requireSignal` и Навигационная запись, `customSky` и ShadersModCore, поселения и обмен, враги и элита, руины, опасности и погода, лагеря и Хранилища, арена и Смотритель (Ключ из 4 Фрагментов, мирная сложность, сброс), журналы, Сигнал, караваны, косметика и Модуль Литографов, лимит Крупиц и Крупицы босса; сервер и клиенты — одинаковые id (измерение, провайдер, 5 биомов); EnderIO / DE: новые `SiliconAge.breakdownWarden`, `containmentCoil`, `caravanMaster`, `caravanGuard` — в чёрные списки банки душ и спавнеров (вместе со всеми прежними); AE2 Spatial IO не берёт блоки арены; ракеты GC — как запретить взлёт.

### 11.7 CHANGELOG
Запись Ф5 в «Не выпущено» (ru и en), в стиле записей Ф1–Ф4: «Подложка (Si-14), фаза Ф5 «Финал»» — арена и Смотритель, журналы, обломки, Сигнал, караваны, косметика, Модуль, прогресс, `customSky`, `requireSignal`, ПЭ-1М-трофей. Только дописывать.

### 11.8 Только в игре (после пакетов)
Вид ямы Эпицентра и её стен, свет, колонны; Консоль и ключ вдвоём по LAN; бой: HP на двоих (960), лучи катушек, выключение катушек Точкой и уроном, окно, волны Оболочек, подсветка и Точка в неё, пресс, Импульс глушения и укрытие у одного и не у другого, трещины, рои, перегрузка, сброс ядра за колонной; полоса здоровья; персональная добыча обоим, откат 3 дня, Журнал №12 у каждого в первый раз; уход на 5 мин — сброс, ключ в Консоли; выгрузка мира посреди боя и возвращение; Координатор; журнал — чтение; обломки моста; луч Сигнала на горизонте днём и ночью, тайник и патруль; караван с засадой вдвоём, награда обоим, редкая сделка; схемы на Станции у одного открыты, у другого нет; пресет и Модуль на своём поле; своё небо (вкл. в конфиге клиента), с ShadersModCore — выкл.; `requireSignal` на сервере: у друга без Записи мира в мосте нет; FPS у арены.

## 12. Пакеты (файлы не пересекаются)

Общие правила владения — как в Ф1–Ф4: каждый существующий файл правит ровно один пакет или координатор; новые файлы создаёт только их пакет (новые имена — с префиксом своей темы); нужна правка чужого файла — в отчёт координатору (файл, место, текст). Заготовки ядра (§0) принадлежат пакетам, указанным в их Javadoc.

### A — арена: Эпицентр Пробоя, Консоль, Ключ, Координатор, сброс
- **Существующие:** заготовки `arena/MapGenArenaSC.java`, `arena/ArenaWorldSC.java`, `arena/ItemArenaCoordinatorSC.java`, `block/BlockArenaSC.java`, `block/ItemBlockArenaSC.java`, `tileentity/TileEntityBreakdownConsoleSC.java`; `ruin/ItemRelicSC.java` (только подсказки и иконки мет 3–4; ПКМ Опечатанной Крупицы не трогать), `settle/SettlementCompatSC.java` (IMC AE2 для `arenaSC`).
- **Новые:** `arena/ComponentArena*SC.java` («SCSi14Arn*»), `arena/client/*.java` (по необходимости: свечение Консоли), `assets/siliconage/textures/blocks/arena_*.png`, `textures/items/relic_breakdownKey.png`, `relic_breakdownMatrix.png`, `arenaCoordinator.png`, `tools/gen_arena_tex.py`.
- **Зависит от:** B (`EntityBreakdownWardenSC.summon`), ядро (`ArenaLayoutSC`, `ArenaDataSC`).
- **Не трогает:** `ArenaLayoutSC`, `ArenaDataSC`, `ArenaBlocksSC`, `ArenaSC` (ядро), `ChunkProviderSubstrateSC`, `MapGenDeadNodeSC`.

### B — Смотритель Пробоя: бой, катушки, добыча, рендер
- **Существующие:** заготовки `mob/EntityBreakdownWardenSC.java`, `mob/EntityContainmentCoilSC.java`, `mob/WardenLootSC.java`, `mob/client/BossClientSC.java`; `mob/TrophyTableSC.java` (`SHOWN` += 24), `compat/WailaEntitySC.java` (только ветки босса и катушки), `assets/siliconage/sounds.json` (только дописывать).
- **Новые:** `mob/ai/EntityAIWarden*SC.java`, `mob/WardenFight*SC.java` (по необходимости — фазы отдельно), `mob/client/RenderBreakdownWardenSC.java`, `ModelBreakdownWardenSC.java`, `RenderContainmentCoilSC.java`, `ModelContainmentCoilSC.java`, `assets/siliconage/textures/entity/breakdownWarden.png`, `containmentCoil.png`, `assets/siliconage/sounds/warden_*.ogg` (по желанию), `tools/gen_finale_boss_tex.py`.
- **Зависит от:** ядро (`WardenRulesSC`, `ArenaDataSC`, `ArenaWorldSC.fightCurrent` / `bossDied` — сигнатуры A), J (`FinaleItemsSC.journal(12)` — уже есть), P (`FinaleItemsSC.paintScheme`).
- **Не трогает:** `MobDropsSC`, `MobEventsSC`, `MobArmorSC`, `EntityArmoredMobSC`, `EmpMobGunSC`, `SingularProgressSC` (зовёт `wardenDefeated`), `CrumbLimitSC`.

### J — Литографы: журналы, обломки моста, Сигнал
- **Существующие:** заготовки `lore/ItemJournalSC.java`, `lore/MapGenWreckSC.java`, `lore/SignalSC.java`, `lore/client/SignalClientSC.java`, `lore/client/JournalClientSC.java`; `ruin/RuinLootSC.java` (только броски журналов), `mob/EntitySentinelSC.java` (только Журнал 50% в `factionLoot`), `block/BlockRuinSC.java`, `block/ItemBlockRuinSC.java` (мета 7 `burntCoil`).
- **Новые:** `lore/ComponentWreck*SC.java` («SCSi14Wrk*»), `lore/WreckLootSC.java`, `lore/SignalCache*SC.java` (по необходимости), `assets/siliconage/textures/items/journal.png`, `journal12.png`, `textures/blocks/ruin_burntCoil.png`, `tools/gen_finale_lore_tex.py`.
- **Не трогает:** `JournalSC`, `WreckLayoutSC`, `SignalRulesSC`, `SignalDataSC` (ядро), `ChunkProviderSubstrateSC`, `BookWorldSC` (статьи — D).

### K — караваны
- **Существующие:** заготовки `settle/caravan/CaravanSC.java` (тело тика; запись сделки ядра не менять), `settle/caravan/EntityCaravanMasterSC.java`, `EntityCaravanGuardSC.java`, `settle/client/CaravanClientSC.java`.
- **Новые:** `settle/caravan/CaravanRun*SC.java`, `settle/caravan/ai/EntityAICaravan*SC.java`, `settle/client/RenderCaravan*SC.java` (по желанию), текстуры `textures/entity/caravanMaster.png` (по желанию), `tools/gen_caravan_tex.py` (по желанию).
- **Не трогает:** `EntityTunerSC`, `EntityWardenSC` и их AI (только наследует; нужна правка — запрос), `SettlementRegistrySC`, `ReputationSC`, `TradeServerSC` (P).

### P — прогресс, косметика, сделки, небо, Навигационная запись, ПЭ-1М
- **Существующие:** заготовки `finale/ItemPaintSchemeSC.java`, `finale/ItemLithoModuleSC.java`, `finale/ItemNavRecordSC.java`, `dim/client/SubstrateSkySC.java`; `settle/trade/TradeTableSC.java`, `settle/trade/TradeServerSC.java`, `settle/trade/TradeOfferSC.java` и `settle/trade/TradeRulesSC.java` (по необходимости), `mob/TrophyRestoreSC.java`, `inventory/ContainerSingularStationSC.java` (кнопки схем), `tileentity/TileEntitySingularStationSC.java` (только `cycleScheme` с игроком), `inventory/GuiFieldGeneratorSC.java` (только строка содержания с Модулем), `tools/gen_singular_tex.py` (строки палитры L, M; и генератор инструментов, если отдельный).
- **Новые:** `dim/client/SubstrateSky*SC.java`, текстуры схем L, M (`textures/items/armorSingular*_l.png`, `_m.png`, `textures/models/armor/singular_l_*`, `singular_m_*`, иконки клинка и бура), `textures/items/paintScheme_*.png`, `lithoModule.png`, `navRecord.png`.
- **Не трогает:** `SingularScheme`, `SingularStationMath`, `SingularLevel`, `SingularProgressSC`, `FinaleRulesSC`, `SubstrateSignalSC`, `TileEntityFieldGeneratorSC`, `TileEntityBridgeControllerSC`, `WorldProviderSubstrateSC` (ядро), `CaravanSC` (K — только зовёт `dealOpen` / `useDeal`).

### D — тексты, справочник, NEI, тесты, README, CHANGELOG
- **Существующие:** `assets/siliconage/lang/ru_RU.lang`, `en_US.lang` (CRLF), `manual/BookWorldSC.java`, `manual/BookContent.java` (только `build()` / `chapterIcon` / ссылки G), `nei/NEISiliconAgeConfig.java`, `nei/ObtainHandlerSC.java`, `nei/NeiCoverageSC.java`, `debug/SelfTestSubstrateSC.java`, `debug/SelfTestSettlementSC.java`, `debug/SelfTestCombatSC.java`, `debug/SelfTestHazardSC.java`, `debug/WorldTestSubstrateSC.java`, `debug/WorldTestSettlementSC.java`, `debug/WorldTestCombatSC.java`, `debug/WorldTestArmourerSC.java`, `debug/WorldTestHazardSC.java`, `debug/WorldTestWildSC.java` (только честные поправки §11.1 и флаг `finished`), `docs/readme-substrate-draft.md`, `CHANGELOG.md` (только запись Ф5 в «Не выпущено»); заготовки `debug/SelfTestFinaleSC.java`, `debug/WorldTestFinaleSC.java`.
- **Не трогает:** `debug/SelfTestEmpSC.java`, `debug/WorldTestEmpSC.java` (регресс ПЭ-1), `debug/SelfTestSC.java` (вызов и поправка схем уже есть).
- **Порядок:** ключи §11.3 и каркас статей — сразу; числа и тесты на тела A/B/J/K/P — после их пакетов; в конце собрать ключи из отчётов; последним — сквозная сверка Ф1–Ф5 (справочник, NEI, README, CHANGELOG).

### Без владельца в Ф5
Файлы Ф1–Ф4, которые Ф5 не трогает (`settle/*` кроме перечисленных, `settle/npc/*`, `settle/gen/*`, `hazard/*`, `ruin/*` кроме перечисленных, `mob/*` кроме перечисленных, `dim/*` кроме перечисленных, `bridge/*`, `manual/BookChapter`). Нужна правка — запрос координатору.

### Только координатор
`SCMod.java`, `util/ConfigSC.java`, `init/ModBlocks.java`, `init/ModItems.java`, `init/ModCreativeTab.java`, `proxy/ClientProxy.java`, `proxy/CommonProxy.java`, `handler/GuiHandlerSC.java`, `handler/ConfigSyncSC.java`, `handler/ArmorNetSC.java`, `CommonEventHandler.java`, `ShieldEventHandler.java`, `item/ArmorLogicSC.java`, `item/ItemArmorSC.java`, `item/BladeLogicSC.java`, `item/SingularProgressSC.java`, `util/SingularLevel.java`, `util/SingularScheme.java`, `util/SingularStationMath.java`, `client/GuiArmorSC.java`, `inventory/GuiSingularStationSC.java`, `compat/WailaSC.java`, `radiation/RadiationSC.java`, `tileentity/TileEntityFieldGeneratorSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `tileentity/TileEntitySettlementCoreSC.java`, `emp/*` (кроме того, что ПЭ-1 пакетов не касается), `dim/SubstrateConfigSC.java`, `dim/SubstrateSC.java`, `dim/SubstrateNetSC.java`, `dim/SubstrateSignalSC.java`, `dim/WorldProviderSubstrateSC.java`, `dim/ChunkProviderSubstrateSC.java`, `dim/MapGenSubstrateCavesSC.java`, `mob/MobEntitiesSC.java`, `mob/ItemSpawnEggSC.java`, `mob/SpawnRulesSC.java`, `mob/SpawnDirectorSC.java`, `mob/MobDropsSC.java`, `mob/MobEventsSC.java`, `mob/IOwnDropsSC.java`, `mob/IMobActionSC.java`, `mob/WardenRulesSC.java`, `mob/MobArmorSC.java`, `mob/EntityArmoredMobSC.java`, `ruin/MapGenDeadNodeSC.java`, `ruin/RelicItemsSC.java`, `arena/ArenaLayoutSC.java`, `arena/ArenaDataSC.java`, `arena/ArenaSC.java`, `arena/ArenaBlocksSC.java`, `lore/JournalSC.java`, `lore/WreckLayoutSC.java`, `lore/SignalRulesSC.java`, `lore/SignalDataSC.java`, `settle/caravan/CaravanRulesSC.java`, `settle/ReputationSC.java`, `finale/FinaleItemsSC.java`, `finale/FinaleRecipesSC.java`, `finale/FinaleRulesSC.java`, `finale/FinaleNetSC.java`, `finale/client/FinaleClientSC.java`, `debug/SelfTestSC.java`, `docs/plan-substrate*.md`, `README.md`.

## 13. Жёсткие правила для всех
1. Аддон Vehicles не трогать. Файлы не удалять. Не коммитить.
2. Код брони игрока не трогать (`ItemArmorSC`, `ArmorLogicSC`, Сингулярные умения, `SingularLevel` / `SingularProgressSC` — ядро уже сделало своё); подавление игроков — только по правилам ПЭ-1 (`EmpMobGunSC`, `EmpSC`), заряд — только `ItemArmorSC.discharge`.
3. ПЭ-1 между игроками не меняется: игроковые ветки `EmpGunLogicSC`, `EmpSiphonSC`, `EmpRulesSC`, HUD — прежние; `SelfTestEmpSC` и `WorldTestEmpSC` — зелёные без правок.
4. Клиентский код — только в client-пакетах; общий код не зовёт `@SideOnly(CLIENT)`-методы (`getItemInUse` и т. п.); журнал открывается через `proxy.openJournal`; небо — только `SubstrateSkySC`. FakePlayer пакетов не получает, не открывает схем и записей, не получает добычи босса и наград каравана.
5. Мир Подложки — только `SubstrateSC.is(world)` / `isDim` на сервере.
6. Мультиплеер (игра по LAN вдвоём): всё решает сервер; состояние игроков — по UUID (откат добычи босса, первая победа, отложенная добыча, редкая сделка — в реестре; схемы и Навигационная запись — в постоянном NBT); два игрока одновременно: HP по n, добыча каждому со своей долей, награда каравана каждому защитнику, луч Сигнала каждому клиенту, тайник один на сигнал.
7. Генерация — без каскада: только окно populate и вырез в массивах своего чанка, высоты — из чистых функций; никакого `Blocks.stone`; светящиеся — по счёту и только в populate; старт на открытой земле — пустой; Узел, уже созданный на перекрёстке арены, остаётся Узлом. Земля уже созданных чанков не меняется никогда.
8. Ничего не моделируется без игрока: Сигнал без игрока не начинается, караван — только к поселению с игроком рядом, бой — только пока босс загружен (сброс — по времени при загрузке).
9. Каждый новый блок, мета и предмет — сразу с подсказкой TooltipSC (кратко, Shift, Ctrl) и строкой NEI (D).
10. Рецепты — только ванильные предметы и предметы мода (в Ф5 — два рецепта ядра; новых у пакетов нет).
11. Тексты добавляет только D; остальные присылают `ключ=ru | en`. Тон — как в остальном UI мода.
12. Реестровые имена, пул сущностей, меты, слоты DataWatcher, ключи NBT, имена MapGenStructureIO, порядок перечислений, виды сообщений — фиксированы (§2), только дописываются.
13. Производительность: AI — видимость не чаще раза в 10 тиков; учёт боя — раз в 20 тиков; «Импульс глушения» и «сброс ядра» — один проход по `playerEntities`; тики арен, Сигнала и караванов — медленные (20–200); луч — один на клиенте; `covers` обломков и арен — с дешёвой отсечкой (уже в ядре).
14. Конфликты с модами сборки (предложение §11): блоки арены — в чёрном списке AE2 Spatial; новые сущности — только `registerModEntity`; биомов и id измерений Ф5 не добавляет; своё небо — выкл. по умолчанию и при ShadersModCore; GC и GS не задеты (провайдер прежний).

## 14. Проверка Ф5 в реальной сборке
По §11.8 вместе с проверкой Ф1–Ф4 (`docs/plan-substrate.md` §10, техпланы Ф2 §11.3, Ф3 §11.4, Ф4 §11.4). Сборка пользователя: TLauncher Forge, `-Dsc.packcheck` (память «Silicon Age pack check»), его javaw не закрывать. LAN вдвоём: бой с боссом (HP 960, добыча обоим), караван, Сигнал, схемы и Запись у каждого свои.

## 15. Неясно / решено координатором
- **«Главные Скрайб-линии».** Отдельных главных линий в рельефе нет (каньоны всех линий одинаковы — менять рельеф существующих миров нельзя): «главные» — линии, на пересечении которых в регионе 4096 × 4096 стоит Эпицентр, выбраны по хешу в 4…11-й линиях региона (в регионе спавна — 6…11), не на перекрёстке с Хранилищем. Арена заменяет Мёртвый Узел своего перекрёстка; Узел, чей старт уже создан (мир Ф3–Ф4), остаётся — арены в этом регионе нет (запись EMPTY), Координатор указывает на следующую.
- **Яма вырезается в массивах чанка** (provideChunk), а не `setBlock` в populate: около 160 тыс. блоков воздуха setBlock'ом и пересчётом света тормозили бы генерацию; куски populate ставят только колонны, постаменты, Консоль, лампы.
- Арена открыта небу, блоки арены неломаемые; ставить свои блоки в яме можно — укрытие игрока от «сброса ядра» и «Импульса глушения» — честная тактика (поле-«купол» на арене не ставим: оно запретило бы и щелчок по Консоли).
- **Катушки — сущности**, а не блоки: Точка ПЭ-1 бьёт по сущностям (`IEmpReactiveSC`), луч к боссу рисуется по id. Импульс катушку не гасит (иначе один Импульс снимал бы все четыре). Сломанные катушки возвращаются с концом окна: фаза 1 — повторяющийся цикл «погасить 4 → окно 10 с», пока HP не упадёт до 60%; в фазе 2 катушки гаснут насовсем.
- HP растёт по **наибольшему** числу игроков, бывших в 48 блоках за бой (и ранивших босса издалека), и не падает, когда кто-то ушёл; текущее HP сохраняет долю.
- Подсвеченная часть держится 5 с (в предложении — «каждые 15 с одна часть подсвечивается»); запас босса — 250–300, подзарядка 4 ед./с через 5 с; радиус пресса — 8. TODO(balance).
- Фаза 3: «щита нет» — гелий выкипел; «защита выключается на 4 с» — броня и щит не работают совсем (урон целиком в HP), вне окна — очки Сингулярной брони и запас. «Сброс ядра» повторяется каждые 60 с, пока босс жив.
- **Добыча личная**: всё сразу в инвентарь; если игрок не в сети, мёртв или в другом мире — отложена в реестре и выдаётся при следующем входе или возрождении (иначе погибший в бою терял бы добычу). 500 очков прогресса — убийце (прежнее правило `IBossDisplayData`), задание «Победить Смотрителя» — каждому с добычей-долей. Крупицы босса — настоящие, мимо суточного лимита (предложение: «сверх суточного лимита»). Откат 3 дня — у каждого свой; задание засчитывается и в откат.
- **Первая победа** (Журнал №12) — у каждого игрока своя (личная добыча).
- Без ПЭ-1 у мобов (`mobsUseEmp` и т. п.) у босса нет ни ПЭ-1М, ни «Импульса глушения», ни трофея ПЭ-1М.
- **Журналы** открываются экраном справочника на своей статье, не входящей в главу: найденное читают, не найденное в справочнике не прочтёшь. Глава WORLD получает статью «Литографы» (лор) и «Журналы Литографа» (где искать). №12 — только с босса.
- **Караван идёт к поселению, у которого есть игрок**, из ближайшего соседнего — появляется за куполом на плато той же клетки и идёт к воротам: каньоны (до 50 блоков глубиной) караван не пересекает, а «ничего без игрока» иначе не выполнить. Один караван в мире за раз, раз в 2 дня. Редкая сделка — у Оружейника (у самого караванщика своего Терминала нет: он не привязан к поселению), открыта 3 дня, одна. Отдельное правило `caravans` (true) — как `raids`, чтобы событие можно было выключить.
- **Сигнал** — 15% в сутки (в предложении — «редко»), 10 мин; узел — в 300…800 блоках от случайного игрока; тайник ставится только вместо воздуха; луч виден за 1024 блока. TODO(balance).
- **Схемы «Литограф» и «Наладчик»** — новые схемы `SingularScheme` (L, M), открываются предметом навсегда для игрока; старая `cycle(s, dir)` их не предлагает (тесты Ф1–Ф4 прежние). Пресет «Бирюза Наладчиков» — новый цвет 0x48F0DC (ярче бирюзового пресета 11 = цвет куполов поселений, который не меняем); не запирается: свой цвет поле принимает любой.
- **Модуль Литографов** — свой предмет в слоте улучшений поля, а не новый `UpgradeType` (тот тянул бы рецепты, справочник улучшений и проверки всех машин).
- **Задания прогресса** — четвёртая графа к уровням 3 и 5 (рядом со «100 мобов» и «Драконом»); у уровней 2 и 4 четвёртого задания нет. «Подавить ПЭ-1 25 бронированных врагов» засчитывает убийство в костюме бронированного местного с подавленной частью — та же проверка, что у контракта Старейшины.
- **`customSky` — не правило**: вид неба — дело каждого клиента; сервер его не присылает. ShadersModCore ищется по классу (это coremod, Loader его не видит).
- **`requireSignal`** фильтрует список измерений, цели Подложки (причалы, Метка гибели) и `plan()`; мост в самой Подложке видит её всегда; запись ставится и при выключенном правиле.
- Звуков своих в Ф5 по-прежнему можно не делать (ванильные и мода); `sounds.json` — у B.
