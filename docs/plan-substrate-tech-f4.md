# Подложка (Si-14) — техплан Ф4 «Опасный мир» и разбивка на пакеты (2026-10-17)

Дизайн: `docs/plan-substrate.md` (утверждён, все ★; коды правил Ф4 — там, §31–§42). Источник чисел: `docs/idea-dimension-substrate.md` §3, §8, §9, §10, §12. Техпланы Ф1–Ф3: `docs/plan-substrate-tech.md`, `-f2.md`, `-f3.md` (их правила работы, сборка и запреты действуют и здесь).
Здесь — архитектура Ф4 под существующий код и разбивка на 6 параллельных пакетов (W, S, M, N, C, D) с **непересекающимися файлами**.
Java 6 (без лямбд, diamond, try-with-resources, switch по String, без API Java 7+), MCP-имена, Forge 1.7.10-10.13.4.1614.
Сборка:
`T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /c/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q`
Строки «Read timed out» и 404 безвредны. runServer не запускать, сборка — всегда clean build. Проверка reobf: `unzip -p build/libs/SiliconAge-0.2.1.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` печатает 1. Каждый пакет обязан собираться сам по себе поверх ядра.

## 0. Ядро (уже написано, собирается)

Правит **только координатор**. Нужна правка ядра или чужого файла — описать её в отчёте (файл, место, текст). Заготовки (стабы) принадлежат пакетам, указанным в их Javadoc: сигнатуры, имена, меты, числа, слоты DataWatcher и ключи NBT в них фиксированы, тела — пакетов.

| Файл (`com/sc/…`) | Что |
|---|---|
| `dim/SubstrateConfigSC.java` | Id `biomeIdRidges` 242, `biomeIdOxide` 243, `biomeIdRift` 244 (приватные, с геттерами, `writeBiomeId`); правила `empStorms`, `raids` (оба true) — §2.1 |
| `dim/SubstrateSC.java` | Пять биомов: `pickBiomeId(slots, want, int[] notThese)` (старая сигнатура делегирует), отказ с именем биома, `ridges()`, `oxideFields()`, `rift()`, `biome(kind)`; `loadComplete` проверяет все пять слотов |
| `dim/BiomeSubstrateSC.java` | Виды `RIDGES` 2, `OXIDE` 3, `RIFT` 4, `KINDS` 5, `NAMES`, `KEYS`; имя и цвет по виду (остальное — W) |
| `dim/SubstrateNetSC.java` | Сверка всех пяти id биомов; три новых id — **после правил** в сообщении (клиент Ф1–Ф3 их не читает); регистрация `HazardNetSC` |
| `dim/SubstrateCellsSC.java` | Клетки и их вид (ПМ-10, ПМ-11), §3.1: чистые `pureKind`, `columnKind`, `biomeKindPure`, `inner`, `chunkHasInner`, `cellOf`, `key`; мировые `cellKind`, `biomeKind`, `legacy`, `forget`; WorldSavedData `SCSi14Cells` |
| `dim/WorldChunkManagerSubstrateSC.java` | Биом колонки — `SubstrateCellsSC.biomeKind(world, x, z)`; хранит World |
| `dim/ChunkProviderSubstrateSC.java` | Вид клетки чанка (один на чанк), рельеф её внутреннего квадрата — `SubstrateWildTerrainSC.column` (W), биомы колонок — `columnKind`; populate → `SubstrateWildGenSC.populate` (W) после друз; поля и вызовы `MapGenCampSC`, `MapGenVaultSC` (C) в provideChunk / recreateStructures / populate / `func_147416_a`; лагеря в `ruinCovers`; геттеры `camps()`, `vaults()` |
| `dim/WorldProviderSubstrateSC.java` | Небо и туман буря тянет к фиолетовому по `StormStateSC.skyFactor` (до 70%) |
| `dim/SubstrateBlocksSC.java` | Мета 9 породы `MONOCRYSTAL` («Монокристалл» жеод), `ROCK_METAS` 10, `ROCK_NAMES` += `monocrystal` |
| `dim/SubstrateWildBlocksSC.java` | Регистрация блоков W: `singularGlass`, `substrateFlora`, `substrateVent`; TE `SiliconAge.substrateVent`, `SiliconAge.arcCrack`; меты и имена (§2.2) |
| `dim/SubstrateRadiationSC.java` | Фон радиации (ПА-1): `backgroundLevel(kind)` (Окисные поля 1, Разлом 2), `background(p)` по биому колонки, ×3 в Разломе при Выбросе. W может уточнить тело (доля столба) |
| `radiation/RadiationSC.levelAt` | Одна строка: `+ SubstrateRadiationSC.background(p)` |
| `hazard/StormStateSC.java` | Фазы `CALM` / `WARNING` / `STORM`, клиентская копия бури, мглы и Выброса для обеих сторон без клиентских классов: `skyFactor`, `hazeFactor`, `clientPhase`, `clientTicksLeft`, `clientSincePulse`, `clientEmission`, `clientReset` |
| `hazard/StormDataSC.java` | WorldSavedData `SCSi14Weather`: буря (день броска, начало, конец, следующий импульс, импульсы, `forced`), мгла (день, окно), Выброс (неделя, окно) |
| `hazard/HazardNetSC.java` | Канал `SiliconAgeDim`, дискриминатор 3 → клиент: виды `STORM_STATE` 1, `STORM_PULSE` 2, `HAZE_STATE` 3, `EMISSION_STATE` 4; `send`, `sendToWorld`, `reachable`; обработчик → `proxy.hazardMessage` |
| `hazard/StormRulesSC.java` | Чистые числа и решения бури, мглы и Выброса (§5.2) — написаны координатором, S уточняет только тела |
| `hazard/RaidRulesSC.java` | Чистые числа и решения налёта (§6.1) — то же |
| `settle/CrumbLimitSC.java` | Суточный лимит Крупиц (ПТ-17): `DAY_CAP` 3, `allowed`, `left`, `take`; `PlayerRec.extra` «K» |
| `settle/ReputationSC.java` | `SRC_RAID` = 9 |
| `mob/IFactionSC.java`, `mob/FactionSC.java` | Фракция существа (ПЗ-3): `RENEGADES`, `SHELLS`, `NONE`; `of`, `is`, `defaultOf` (классы Ф1–Ф3 по классу) |
| `mob/EntityArmoredMobSC.java`, `mob/EntitySubstrateMobSC.java` | `implements IFactionSC`, `faction()` = `FactionSC.defaultOf(this)` |
| `mob/IMobActionSC.java` | Телеграф Ф4: `ACT_PRESS` 12, `ACT_BLINK` 13, `ACT_FLEE` 14, `ACT_STOMP` 15, `ACT_THROW` 16, `ACT_ARC` 17, `ACT_COLLAPSED` 18, `ACT_WRAP` 19, `ACT_STUNNED` 20, `ACT_BURROW` 21 |
| `mob/MobArmorSC.stormHit(piece, ticks)` | Импульс бури по части моба: подавление на `ticks`, невосприимчивость после, без Медлительности |
| `emp/EmpSC.stormHit(p, slot, ticks, boilMul, boilTicks)` | То же для игрока: подавление, невосприимчивость, подкипание гелия, строка «Ваша броня подавлена ЭМИ», синхронизация. Пути ПЭ-1 не тронуты |
| `emp/IEmpUselessSC.java` | Маркер «ПЭ-1 бесполезен» (Колосс) |
| `mob/RareRegistrySC.java` | WorldSavedData `SCSi14Rare`: редкие по ключу — `ECLIPSE`, `vaultKey(i, j)`, `campKey(axis, line, seg)`; `alive`, `died`, `seen`, `missed` (LOST_CHECKS 10), `mayRespawn`, `tag` / `keyOf` (NBT `scRareKey`) |
| `mob/MobEntitiesSC.java` | Пул 12–21 (§2.2), `NAMES`, `EGG_COLORS` (22 вида — яйца появились сами) |
| `mob/ItemCombatLootSC.LootKind` | Дописаны 7 `boiledLoop`, 8 `inductionThread`, 9 `plasmaCondensate`, 10 `singularScrap`, 11 `naturalWafer` |
| `mob/SpawnDirectorSC` | `spawnAt(w, kind, x, y, z, rollElite, rift)` — одно существо вне попыток и лимитов директора (налёт, Эхо, жители лагеря и Хранилища, Утилизатор); `create` знает все 22 id |
| `tileentity/TileEntityFieldGeneratorSC.java` | `protected boolean deflects(Entity)` (тело — прежний `isHostileProjectile`) и `protected static Entity projectileShooter(Entity)`; `protectRegion` зовёт `deflects`. Поле игрока работает как раньше |
| `tileentity/TileEntityStolenFieldSC.java` | **Инварианты** Краденого генератора поля (§8.3) — написаны координатором; `campTick()` пишет C |
| `tileentity/TileEntitySettlementCoreSC.FLAGS` | += `F_RADIATION` (ПА-3) |
| `tileentity/TileEntityGeneratorSC.updateSolar` | Одна проверка: `EmpStormSC.blocksSolar(...)` → `NO_SUNLIGHT` (ПФ-5) |
| `tileentity/TileEntityBridgeControllerSC.enter` | После переноса игрока: `BridgeEchoSC.crossed(p, from, to, x, y, z, scan.shield)` (ПН-9…ПН-11) |
| `ruin/RuinSC.init` | Имена `MapGenCampSC`, `MapGenVaultSC` |
| `ruin/CampBlocksSC.java` | Регистрация блоков C: `stolenField` (+ TE `SiliconAge.stolenField`), `campSC` (+ TE `SiliconAge.stolenSolar`); меты, `isStolenPad` |
| `ruin/RelicItemsSC.java` | Регистрация предметов C: `relicSC`, `ruinFinder` |
| `ruin/CampLayoutSC.java`, `ruin/VaultLayoutSC.java` | Чистая раскладка лагерей и Хранилищ (§8.1, §8.5) — написана координатором, C может менять числа, не контракт |

Заготовки пакетов (сигнатуры фиксированы, тела — пакетов):
- **W** — `dim/SubstrateWildTerrainSC`, `dim/SubstrateWildGenSC`, `block/BlockSingularGlassSC`, `block/BlockSubstrateFloraSC`, `block/BlockSubstrateVentSC`, `block/ItemBlockSubstrateWildSC`, `tileentity/TileEntitySubstrateVentSC`, `tileentity/TileEntityArcCrackSC`.
- **S** — `hazard/EmpStormSC`, `hazard/client/StormClientSC` (приём сообщений уже работает), `hazard/RaidSC`, `hazard/BridgeEchoSC`.
- **M** — `mob/EntityOverheatedSC`, `EntityWarlordSC`, `EntitySentinelSC`, `EntityEclipseSC`.
- **N** — `mob/EntityPhantomSC`, `EntityLeechSC`, `EntityColossusSC`, `EntityStriderSC`, `EntityScrapperSC`, `EntityWaferCrabSC`, `mob/ScrapperSC`, `mob/client/NoiseClientSC`.
- **C** — `ruin/MapGenCampSC`, `ruin/MapGenVaultSC`, `ruin/RuinWorldSC`, `block/BlockStolenFieldSC`, `block/BlockCampSC`, `block/ItemBlockCampSC`, `tileentity/TileEntityStolenSolarSC`, `ruin/ItemRelicSC`, `ruin/ItemRuinFinderSC`.
- **D** — `debug/SelfTestHazardSC`, `debug/WorldTestHazardSC`, `debug/WorldTestWildSC`.

### 0.1 Правки ядра в общих файлах (готово, остальным их не трогать)
- `SCMod`: в preInit — `EmpStormSC.INSTANCE`, `RaidSC.INSTANCE`, `RuinWorldSC.INSTANCE`, `ScrapperSC.INSTANCE` на шинах FML и Forge, `BridgeEchoSC.INSTANCE` на FML; в `serverStopped` — `clearAll()` у `SubstrateCellsSC`, `EmpStormSC`, `RaidSC`, `BridgeEchoSC`, `RuinWorldSC`, `ScrapperSC`; при `-Dsc.worldtest` — `WorldTestHazardSC`, `WorldTestWildSC` на FML.
- `ModBlocks.init`: после `RuinBlocksSC.init()` — `SubstrateWildBlocksSC.init()`, `CampBlocksSC.init()`. `ModItems.init`: в конце — `RelicItemsSC.init()`.
- `ClientProxy.preInit`: `StormClientSC.register()` (S), `NoiseClientSC.register()` (N); `CommonProxy` / `ClientProxy.hazardMessage(kind, nbt)` → `StormClientSC.onMessage`.
- `debug/SelfTestSC.run`: вызов `SelfTestHazardSC.run()`; `debug/SelfTestSubstrateSC.RULES` += `empStorms`, `raids`, `IDS` += три id биомов (иначе самотест Ф1 падал бы на составе раздела).

Пока пакеты не готовы, всё регистрируется: биомы (вид клеток решается и запоминается, но рельеф новых клеток — плато Ф1), блоки, предметы, 10 новых сущностей (яйца есть; мобы стоят без AI), имена структур, сообщения. Фон радиации уже работает по биому, Солнечные панели и Эхо спрашивают заготовки (буря никогда не начинается), лагерей и Хранилищ нет (`canSpawnStructureAtCoords` → false).

## 1. Регистрация и порядок

| Этап | Что | Где |
|---|---|---|
| preInit | `SubstrateConfigSC.load`: id трёх биомов, правила `empStorms`, `raids` | ядро |
| preInit | Блоки `SubstrateWildBlocksSC.init`, `CampBlocksSC.init` (после руин); предметы `RelicItemsSC.init` | ядро |
| preInit | Канал `SiliconAgeDim`: дискр. 3 (`HazardNetSC`); обработчики S, C, N | ядро |
| preInit, клиент | `StormClientSC` (S), `NoiseClientSC` (N), `MobClientSC` (M — рендеры своих), `SubstrateClientSC` (W — туманы), `RuinClientSC` (C — если понадобится) | пакеты |
| init (обе стороны) | `SubstrateSC.init`: пять биомов (каждый мимо уже выбранных id), BiomeDictionary; `MobEntitiesSC.init`: пул 12–21; `RuinSC.init`: `SCSi14Camp`, `SCSi14Vault` — всегда, даже при выключенной Подложке | ядро + пакеты |
| мир | provideChunk: вид клетки → рельеф W; старты лагерей и Хранилищ. populate: Ф1 → `SubstrateWildGenSC` → Узлы → литографии → лагеря → Хранилища → поселения | ядро + W + C |
| жизнь | буря, мгла, Выброс, налёты, Эхо (S); жители лагерей и Хранилищ, их возвращение (C); спавн, редкие (M); Утилизатор (N) | пакеты |

## 2. Общие решения

### 2.1 Конфиг (раздел `dimension`, холдер `SubstrateConfigSC`)
| Ключ | По умолчанию | Вид | Что |
|---|---|---|---|
| `biomeIdRidges` / `biomeIdOxide` / `biomeIdRift` | 242 / 243 / 244 | id | ПК-17: как у Ф1 — занятый сдвигается на свободный (240…254, потом вниз до 40) и пишется в конфиг; каждый мимо id, выбранных до него |
| `empStorms` | true | правило | ПФ-1: своя погода Подложки — ЭМИ-буря, Кремнезёмная мгла, Сингулярный выброс. Выкл. — ни одна не начинается, идущая кончается на следующей проверке |
| `raids` | true | правило | ПН-1: налёты на поселения. Выкл. — новых нет |

Правила — `public static`, уходят клиенту через `SubstrateNetSC` сами (`ruleFields()` — теперь 12). Числа баланса — константы в классах с TODO(balance).

### 2.2 Реестровые имена и id (фиксированы, только дописываются)
| Что | Имя / id | Владелец |
|---|---|---|
| Биомы | 242 «Quartzite Ridges», 243 «Oxide Fields», 244 «The Rift» (`BiomeSubstrateSC.RIDGES` / `OXIDE` / `RIFT`) | W (ядро регистрирует) |
| Перегретый | пул 12, `SiliconAge.overheated` (`EntityOverheatedSC`) | M |
| Вожак лагеря | пул 13, `SiliconAge.warlord` | M |
| Сингулярный часовой | пул 14, `SiliconAge.sentinel` | M |
| «Затмение» | пул 15, `SiliconAge.eclipse` | M |
| Наводка | пул 16, `SiliconAge.phantom` | N |
| Токоед | пул 17, `SiliconAge.leech` | N |
| Кварцитовый колосс | пул 18, `SiliconAge.colossus` | N |
| Дуговой ходок | пул 19, `SiliconAge.strider` | N |
| Утилизатор | пул 20, `SiliconAge.scrapper` | N |
| Подложник (мирный) | пул 21, `SiliconAge.waferCrab` | N |
| Порода | `substrateRock` мета 9 `monocrystal` | W |
| Сингулярное стекло | `singularGlass`: 0 `singularGlass`, 1 `pillarCore` (свет 12) | W |
| Флора | `substrateFlora`: 0 `wireweed`, 1 `diodeMoss` (свет 3) | W |
| Источники | `substrateVent`: 0 `fumarole`, 1 `arcCrack`, 2 `ionGeyser`, 3 `crackedRtg`; TE `SiliconAge.substrateVent` (0, 2, 3), `SiliconAge.arcCrack` (1) | W |
| Краденый генератор поля | `stolenField`, TE `SiliconAge.stolenField` | C |
| Блоки лагеря | `campSC`: 0 `stolenSolar` (TE `SiliconAge.stolenSolar`), 1 `stolenPad`, 2 `campSafe`, 3 `campSafeOpen` | C |
| Руинные блоки Ф4 | `ruinSC` меты 4 `vaultPlating`, 5 `vaultLamp`, 6 `campTarp` (C дописывает в `BlockRuinSC.NAMES`) | C |
| Реликвии | `relicSC`: 0 `campKey`, 1 `keyFragment`, 2 `sealedCrumb` | C |
| Пеленгатор руин | `ruinFinder` | C |
| Боевая добыча | `combatLoot` меты 7–11 (§0) | иконки: M (7, 10), N (8, 9, 11) |
| Структуры | MapGenStructureIO: старт `SCSi14Camp`, куски `SCSi14Cmp*` (база `SCSi14CmpPart`); старт `SCSi14Vault`, куски `SCSi14Vlt*` (база `SCSi14VltPart`). Данные — ванильные `DIM-1414/data/SCSi14Camp.dat`, `SCSi14Vault.dat` | C |

Пул 0–11 (Ф1–Ф3) не меняется; следующий свободный — 22. Имена кусков не пересекаются с `SCSi14Set*`, `SCSi14Node*`, `SCSi14Lit*`.

### 2.3 Перечисления и коды
`BiomeSubstrateSC`: 0 PLATEAUS, 1 CANYONS, 2 RIDGES, 3 OXIDE, 4 RIFT (KINDS 5). `SpawnRulesSC` (M): места дописываются — `PLACE_RIDGES` 3, `PLACE_OXIDE` 4, `PLACE_RIFT` 5; таблицы весов — длиной в пул (22). `ItemCombatLootSC.LootKind` — §0. `StormStateSC`: 0 CALM, 1 WARNING, 2 STORM. `FactionSC`: «renegades», «shells», «». `RareRegistrySC`: NONE 0, ALIVE 1, DEAD 2. `TradeServerSC.RESULT_KEYS` (C дописывает в конец): `R_RAID` «raid» (торговля закрыта после проигранного налёта), `R_CRUMBS` «crumbs» (лимит Крупиц на сегодня).

### 2.4 Хранилище
| Что | Где | Владелец |
|---|---|---|
| Виды клеток | `SCSi14Cells` — perWorldStorage Подложки (`DIM-1414/data/SCSi14Cells.dat`); только клетки, чей чистый вид новый: вид и бит LEGACY | ядро |
| Погода | `SCSi14Weather` (perWorldStorage) — `StormDataSC` | данные ядра, пишет S |
| Редкие | `SCSi14Rare` (perWorldStorage) — `RareRegistrySC`; `extra` записи — поля пакета-владельца (C: лагерь, M: Затмение) | данные ядра, пишут M, C |
| Налёты | реестр поселений, `Settlement.extra` «R»: `rolled` (последняя ночь броска), `last` (день последнего налёта), `closed` (торговля закрыта до, время мира) | S |
| Крупицы | реестр поселений, `PlayerRec.extra` «K»: `d` (день), `n` (сколько распечатано) | ядро (`CrumbLimitSC`) |

Почему perWorldStorage для клеток, погоды и редких: это свойства самой Подложки; удалённая `DIM-1414` начинается с чистого листа. Репутация и лимиты игроков — в глобальном реестре, как в Ф2.

### 2.5 Сеть
| Дискр. | Сообщение | Направление | Виды |
|---|---|---|---|
| 0 | `SubstrateNetSC.Message` | → клиент | id и правила; Ф4: три id биомов после правил |
| 1, 2 | `SettleNetSC` (Ф2) | | без изменений |
| 3 | `HazardNetSC.ToClient` | → клиент | `STORM_STATE` {p, t, w}, `STORM_PULSE` {}, `HAZE_STATE` {on, t}, `EMISSION_STATE` {on, t} (S) |

Обработчик клиента может прийти не в клиентском потоке — `StormStateSC` пишет только volatile-поля. FakePlayer пакетов не получает (`send` проверяет). Свободны: канал `SiliconAgeDim` — 4 и дальше; `ArmorNetSC` — 11 и дальше.

### 2.6 DataWatcher и телеграф
| Класс | Слоты |
|---|---|
| `EntityArmoredMobSC` и наследники (Перегретый, Вожак, Часовой, Затмение) | 20–25 ядра (Ф1–Ф3), 26–31 — классам (M) |
| `EntitySubstrateMobSC` и наследники (Наводка, Токоед, Колосс, Ходок, Утилизатор) | 25 `DW_ACTION`; 20–24, 26–31 — классам (N) |
| `EntityWaferCrabSC` (`EntityCreature`) | 12–31 — классу (N); телеграфа нет — если понадобится, свой слот |

Телеграф — `IMobActionSC` (§0): сервер ставит в начале замаха / набора и возвращает 0, рисуют все клиенты (ПС-9).

### 2.7 Ключи NBT (фиксированы)
Сущности: `scRareKey` (string, ключ `RareRegistrySC`), `scRaid` (int, id поселения — налётчик), `scEcho` (long, время рассеяния частицы Эха), `scHome` (как у X в Ф3: {x, y, z, r} — жители лагерей, Часовой), `scCampSafe` (int[3] — Вожак: сейф его лагеря, ставит C), `scBag` (Утилизатор: 27 ячеек, тот же ключ, что у Мародёра), `scDeathDrop` (string UUID владельца) и `scDeathAt` (long) — у EntityItem вещей погибшего игрока. Предметы: Ключ лагеря — `scCamp` {x, y, z}. Старты: `CampAxis`, `CampLine`, `CampSeg` (лагерь), `VaultI`, `VaultJ` (Хранилище). TE: `scRadius`, `scCamp` (Краденый генератор). Игрок (`getEntityData`, не персистентный): `scEchoCd` (long — откат Эха), `scScrapperTold` по желанию N.

### 2.8 GUI
Новых окон нет: сейф, пеленгатор, Краденый генератор — щелчком и строкой в чат. Свободные id: 16–19, 22+.

## 3. Мир — пакет W (ПМ-10…ПМ-18)

### 3.1 Клетки (`SubstrateCellsSC`, ядро; ПМ-10, ПМ-11)
- Клетка — квадрат 256 × 256 между Скрайб-линиями (`x >> 8`); 16 × 16 чанков, чанк всегда в одной клетке. Каньонный пояс (±24 от линии) остаётся каньонами, **полоса до 40 блоков от линии (`INNER_MARGIN`) — плато Ф1 во всех клетках**; новый рельеф — только во «внутреннем квадрате» (отступ 41…215). Поэтому стены каньонов, Мёртвые Узлы, лагеря и соседние клетки всегда встречают плато Ф1.
- Чистый вид клетки (`pureKind`): плато — клетки в кольце 1 вокруг (0, 0) (спавн и «Первый причал» в (1, 0)), клетки с поселением (`SettlementLayoutSC.site`) и с литографией (`RuinLayoutSC.lithoSite`); иначе по хешу: Разлом 8% (только если середина клетки ≥ 1500 блоков от точки спавна), Гряды 30%, Окисные поля 25%, остальное — плато. TODO(balance). Итог по всем клеткам ≈ 18% Гряд, 15% Окисных полей, ≈ 5% Разлома вдали.
- **ПМ-11 (без разрывов земли Ф1–Ф3)**: на сервере вид решается один раз — когда он впервые нужен (первый чанк с внутренними колонками или любой вопрос о биоме там) — и хранится в `SCSi14Cells`. Клетка с новым чистым видом, у которой внутренние чанки уже есть на диске (мир Ф1–Ф3), навсегда остаётся плато (бит LEGACY, строка в лог). Половинных гряд и обрывов у старых чанков нет. Клиент (без сида и данных) отвечает `pureKind`; он и так читает биом из массива чанка.
- Если `DIM-1414/data` удалить, а регионы оставить, клетки с новым рельефом решатся заново как LEGACY — шов возможен; это та же оговорка, что у структур (ПП-2).

### 3.2 Рельеф (`SubstrateWildTerrainSC`, W; ПМ-12…ПМ-14)
`column(kind, seed, wx, wz, col, blocks, metas)` — колонка в массивы чанка (индекс `col + y`, как у Ф1); false — кладётся колонка Ф1 (полоса у линий и всё, пока стоит заглушка). Ни одного `Blocks.stone`; бедрок на Y 0; Кристаллит; окисная корка 2–6 блоков под верхом. Чистые функции (`ridgeY`, `duneY`, `riftVoid`, `islandAt`, `pillar`) — для самотеста и спавна.
- **Кварцитовые гряды** (ПМ-12): хребты до Y 140 (`RIDGE_MAX`), рост от полосы на `RIDGE_BLEND` 32 блока (плавная ступень), гребни — ridged-шум; верх — Кварцевый дёрн на пологих местах, Кристаллит и полосы оксида на крутых; больше пещер (карвер); жилы богаче (`SubstrateOreGenSC` ×`RIDGE_VEIN_MUL` 1,5 в колонках Гряд). TODO(balance).
- **Окисные поля** (ПМ-13): переливчатые дюны Y 64–80 (`DUNE_MIN` / `DUNE_MAX`), верх — все четыре корки пятнами по шуму (не полосами по Y), без дёрна; фумаролы и треснувшие РИТЭГи (§4).
- **Разлом** (ПМ-14): за ободом `RIFT_RIM` 16 — обрыв до дна; дно — Сингулярное стекло Y 1–2 (`RIFT_FLOOR`) над бедроком, между — пустота; 6–12 парящих островов (`ISLANDS_*`) на Y 40–100 (линзы Кристаллита, сверху оксид, иногда друза, ионные гейзеры); в середине клетки — «столб прокола»: Сингулярное стекло радиуса 2 до Y 160 со светящейся сердцевиной (мета 1, ставится в populate через `setBlock` + `glow()`: светящееся в массивах свет не получает — так же, как узлы трасс Ф1). Падение на дно с острова смертельно — это и есть опасность «пустота» (ПА-8).
- `BiomeSubstrateSC` (W): верх / наполнитель и цвета новых видов; спавн-листы пусты; типы BiomeDictionary — те же WASTELAND, DRY, SPARSE (MOUNTAIN / HILLS позвали бы чужие горные генераторы).
- Карвер `MapGenSubstrateCavesSC` (W): пещер ×2 в клетках Гряд; ни колодцев, ни пещер в пустоте Разлома (там нечего резать), в боксах Хранилищ (`VaultLayoutSC.vaultNear`) и лагерей (`CampLayoutSC.siteNear`).
- Пруды, трассы, друзы Ф1 (`ChunkProviderSubstrateSC`, W): в новых клетках — по усмотрению W (пруды только на плато и дюнах; трассы в Разломе — только на ободе и островах).

### 3.3 Блоки и populate (`SubstrateWildGenSC`, W; ПМ-15…ПМ-18)
- Окно populate то же, что у Ф1 (чанк + 8 … + 23): ничего не грузится, каскада нет. Светящиеся — `setBlock` и `ChunkProviderSubstrateSC.glow`, немного.
- **Флора** (ПМ-15): Проволочник — на дёрне плато и Гряд, до 6 попыток на чанк (`WIREWEED_TRIES`); в ЭМИ-бурю искрит (клиент: `randomDisplayTick` по `StormStateSC.clientPhase()`, свет не меняется). Диодный мох (свет 3) — на дне каньонов и в пещерах Гряд, не больше 4 на чанк (`MOSS_MAX`). Без коллизии, крестом, только на породе, не тикают и не растут. Дроп — ничего (ножницы — сам блок), TODO(balance).
- **Монокристаллические жеоды** (ПМ-16): в кремниевом основании Y 5–18, 1 на 12 чанков, радиус 3–4, целиком в окне: оболочка Кристаллита / корки, выстилка — мета 9 «Монокристалл», внутри воздух. Монокристалл: кирка 2, дроп — Металлургический кремний 1–3 (+удача; цепочку очистки не обходит), Шёлковое касание — сам.
- **Источники** (§4) — в своих биомах.
- Текстуры — `tools/gen_substrate4_tex.py`: `substrate_singularGlass`, `substrate_pillarCore`, `substrate_wireweed`, `substrate_diodeMoss`, `substrate_fumarole`, `substrate_arcCrack`, `substrate_ionGeyser`, `substrate_crackedRtg`, иконка меты 9 породы (по схеме имён `BlockSubstrateRockSC`).
- Подсказки TooltipSC у каждой меты (`ItemBlockSubstrateWildSC`: `sc.tooltip.wild.<имя>` + .details + .howto; монокристалл — у `ItemBlockSubstrateRockSC`).

### 3.4 Руды
`SubstrateOreGenSC` / `SubstrateOreTableSC` (W): в колонках Гряд жил ×1,5 («самые богатые жилы»), в Разломе — только на островах (по Кристаллиту, как везде). Множители Ф1 прежние.

### 3.5 Клиент (`dim/client/SubstrateClientSC`, W; ПМ-17)
`EntityViewRenderEvent.FogColors` и `FogDensity`: в колонке Разлома — фиолетово-чёрный туман (≈ #1A0A2A), плотнее у дна; Кремнезёмная мгла (§5.5) — белёсый туман с видимостью ≈ 24 блока по `StormStateSC.hazeFactor`; при Выбросе в Разломе туман краснеет. Один обработчик на событие — у W (S туман не трогает). Небо бури — в провайдере (ядро).

## 4. Опасности среды — пакет W (ПА-1…ПА-8)

### 4.1 Фоновая радиация (`SubstrateRadiationSC`, ПА-1…ПА-3)
- Один хук в `RadiationSC.levelAt` (ядро). Работает всё, что у радиации уже есть: свинцовый костюм, D2O в поножах, радиационный щит Кванта и Экзо, поле с F_RADIATION, дозиметр, Дезактивационный душ; урон радиации проходит сквозь щит Экзо (`exoStops` её не останавливает).
- По биому колонки игрока: Окисные поля 1, Разлом 2, прочие 0 (ПА-1). Сверху — обычные источники `RadiationSC.report` (блоки на пути режут): треснувший РИТЭГ 1 в радиусе 12 (у РИТЭГа до 2), столб прокола 1 в радиусе 32 (у столба до 3 — W может посчитать его в `background`), Перегретый 2 в радиусе 4 (M) — ПА-2. Выброс (§5.6) — фон Разлома ×3.
- ПА-3: купола поселений получили F_RADIATION (бесплатно, `pay()` всегда да) — под куполом фона нет.

### 4.2 Гелиевая фумарола (ПА-4)
`TileEntitySubstrateVentSC`, мета 0: раз в секунду 1–2 мБ жидкого гелия (`ModFluids.liquidHelium`) — только в соседнюю трубу мода или бак (`TileEntityPipeSC` / `TileEntityTankSC`, `IFluidHandler.fill`); сама ничего не хранит, извне не сливается. Неломаемая, без дропа. Частицы пара (клиент). Рой её не трогает (энергии нет). Возобновляемый гелий для Экзо- и Сингулярной брони вместе с Газовщиком (его продажа ячеек уже есть).

### 4.3 Пробойная трещина (ПА-5)
`TileEntityArcCrackSC extends TileEntityEnergyBase(LV)`: 32 EU/т бесплатно — источник только для кабеля мода рядом (`isEnergySource`, `outputFaces`, сеть `EnergyNetSC` и IC2 через базовый класс), ничего не принимает, предметы не заряжает, рою не отдаёт (`canItemCharge` / `extractForItemCharging` — нет: зафиксировано ядром). В радиусе 2 раз в секунду: игрок в броне мода теряет 0,5% заряда каждой надетой части (`ItemArmorSC.discharge`), без неё — 1 «Пробой» (`BreakdownDamageSC.WORLD`); живые мобы — 1 «Пробой». Неломаемая, без дропа. Где: дно каньонов, Гряды, Окисные поля, 1 на 40 чанков (TODO(balance)).

### 4.4 Ионный гейзер (ПА-6)
Мета 2, Разлом (на островах 1–2): разряд раз в 6–10 с (120–200 тиков) в радиусе 3 — 3 «Пробоя» (`BreakdownDamageSC.WORLD`) и −2% заряда каждой части брони мода; перед разрядом 1 с искр (видно всем — частицы из TE на клиенте или `func_147487_a` сервера).

### 4.5 Треснувший РИТЭГ (ПА-7)
Мета 3, Окисные поля, 1 на 8 чанков: `RadiationSC.report(..., 1, 12)` раз в секунду; неломаемый.

### 4.6 Душ поселения и пустота (ПА-3, ПА-8)
- Витрина «Дезактивационный душ» поселения (мета 15 `showcaseSC`, ворота): игрок, стоящий на ней, теряет 2% дозы в секунду (как `TileEntityShowerSC.DOSE_PER_SECOND`), бесплатно (W, `TileEntityShowcaseSC` — только эта ветка).
- Пустота Разлома и каньоны — опасность сами по себе; `BridgeSoftLandSC` уже ловит прибытие над пустотой.
- AE2 Spatial IO: источники (`substrateVent`) — в IMC `blacklist-block-spatial` (W, `SubstrateCompatSC.sendImc`), иначе трещину и фумаролу унесли бы домой.

## 5. Погода — пакет S (ПФ-1…ПФ-10)

### 5.1 Расписание (`EmpStormSC`, `StormDataSC`, `StormRulesSC`)
- Время — `getWorldTime` (часы Overworld через DerivedWorldInfo, как `SettlementSC.day`). Раз в игровые сутки — бросок `rolls(seed, day)` (20%); `rolledDay` в данных — повторного броска не бывает ни после перезапуска, ни от второго игрока. Буря — с `startOf` на `durationOf` (3–5 мин), предупреждение за 600 тиков (30 с); буря целиком внутри суток.
- Тик мира Подложки (END, раз в 20 тиков — расписание; импульсы — в свой тик): смена фазы → `HazardNetSC.STORM_STATE` всем в Подложке; вошедшему в Подложку, вошедшему на сервер в ней, возродившемуся там — текущая фаза. Строки в чат игрокам Подложки: начало предупреждения `sc.storm.warning`, начало `sc.storm.start`, конец `sc.storm.end`.
- Ничего не моделируется без игрока: окно, прошедшее без игроков, просто прошло.
- `startNow` / `stopNow` / `pulse` — для мирового теста (и, по желанию, команды оператора — не обязательно).

### 5.2 Числа (`StormRulesSC`, фиксированы)
Шанс 20% в сутки; 3600–6000 тиков; предупреждение 600; импульс каждые 400–800; подавление части 60–80 тиков; гелий кипит ×2 (`BOIL_MUL` = `EmpMathSC.BOIL_MUL_POINT`) 200 тиков (TODO(balance)); моб без игрока в 128 блоках импульса не чувствует; укрытие — 3 твёрдых блока над головой, смотреть до 48 вверх; перезагрузка Оболочки 60 тиков; рои ×3; трофеи ×1,5 (`TrophyTableSC.STORM_MUL`, с элитой не больше ×2,5 — ПТ-4).

### 5.3 Импульс (ПФ-2, ПФ-3)
- Игроки Подложки (не креатив, живые): не в укрытии (`coveredFrom`) → случайная надетая часть мода → `EmpSC.stormHit(p, slot, 60..80, BOIL_MUL, BOIL_TICKS)` (невосприимчивость соблюдается: иммунная часть — ничего). Без брони мода — ничего (не Медлительность).
- Бронированные мобы (`IEmpTargetSC`, `empArmoured()`) в 128 блоках от любого игрока: не жители и не стражи (`ISettlementMemberSC`), не питомцы (`IEntityOwnable` с владельцем); не в укрытии → личный ЭМИ-экран берёт импульс (`screenTakes`, как выстрел ПЭ-1), иначе случайная надетая часть → `MobArmorSC.stormHit`. `EntityHollowShellSC` → `reboot(60)` (ПБ-21). Подавленный нагрудник Экзо / Сингулярного моба гасит щит — как Точка.
- Укрытие `coveredFrom(w, e)`: 3 блока, у которых `getMaterial().blocksMovement()`, над головой (до верха колонки по heightmap, не дальше 48); или поле с F_EMP_SCREEN вокруг (`TileEntityFieldGeneratorSC.fieldWith`) — поле игрока платит `EmpMathSC.SCREEN_EU_PULSE` (15 000 EU) один раз на импульс (нет EU — укрытия нет), Ядро поселения — всегда.
- `HazardNetSC.STORM_PULSE` всем в Подложке; звук импульса у каждого попавшего (`emp.pulse`, существующий).

### 5.4 Клиент (`StormClientSC`, S; ПФ-4)
Небо и туман уже тянутся к фиолетовому (ядро). S: «строки развёртки» — полупрозрачные горизонтальные полосы, бегущие сверху вниз, только когда игрок видит небо (`canBlockSeeTheSky` у головы), сила по `skyFactor`; вспышка импульса (белёсо-фиолетовая, 10 тиков) и треск; строка HUD «ЭМИ-буря через N с» / «ЭМИ-буря: М:СС» (ключи `sc.storm.hud.*`); сброс `StormStateSC.clientReset()` при выходе с сервера и при уходе из Подложки. Искры Проволочника — у W (блок спрашивает `StormStateSC`).

### 5.5 Кремнезёмная мгла (ПФ-9; предложение §3 «Погода»)
25% суток, 2–3 мин (2400–3600 тиков), в той из половин суток, которую не трогает буря (`hazeWindow`); видимость ≈ 24 блока: клиентский туман (W, по `hazeFactor`) и прицел мобов — `EmpStormSC.sightRange(w, обычная)` = не дальше 24 (M — `EntityAIBeamAttackSC`, `EntityAIEmpShooterSC` / `EmpMobGunSC`; N — `EntityAISentryDroneBeamSC`). Сообщение `HAZE_STATE` в те же моменты; строка в чат `sc.haze.start` / `sc.haze.end`. TODO(balance): шанс.

### 5.6 Сингулярный выброс (ПФ-10; предложение §9 событие 6)
Раз в игровую неделю (`SettlementSC.week`) на 10 мин (12 000 тиков) в окне `emissionWindow`: радиация Разлома ×3 (`SubstrateRadiationSC`, ядро), шанс Опечатанных Крупиц с Часового и Затмения ×2 (M; суточный лимит остаётся — он на распечатывании), Затмение появляется в 3 раза чаще (M). `EMISSION_STATE` клиентам, в Разломе строка HUD и красноватый туман (W), в чат всем в Подложке `sc.emission.start` / `.end`.

### 5.7 Сирена и жители (ПФ-4)
В WARNING — сирена у Ядра каждого поселения с загруженным Ядром раз в 5 с (существующие звуки мода или ванильные; `sounds.json` — только S, только дописывая), слышно за 64; Наладчики идут домой и в бурю сидят дома (`EntityAITunerHomeSC`, S — только это условие). Жителей и стражей буря не задевает (§5.3).

## 6. События — пакет S (ПН-1…ПН-11)

### 6.1 Налёт на поселение (`RaidSC`, `RaidRulesSC`; предложение §9 событие 2)
- **Когда** (ПН-1): ночью (13000–23000 тиков суток), правило `raids`, поселение с загруженным Ядром (`blockExists`), налёт не идёт, рядом защитник — настоящий игрок не в креативе с «Доверием» от «Знакомого» (≥ 100; не «Враг» этого поселения) в 64 (Пост) / 80 (Посёлок) / 96 (Город) блоках от Ядра. Один бросок за ночь (`extra` «R».`rolled` = день), 10% (`rolls(seed, id, day)`), не раньше 5 дней после прошлого налёта (`last`). Проверка — раз в 100 тиков (`CHECK_TICKS`).
- **Волны** (ПН-2): `RaidRulesSC.wave(n, size, rand)` — 1) Мародёры 2 + размер и Обходчики 2; 2) Штурмовики 1 + размер и Глушитель; 3) с шансом 50% — элита (первый бронированный волны, `EliteSC.apply`) с Глушителем и Мародёрами, иначе колонна Оболочек 2 + размер с двумя Ремонтными дронами (`EntityRepairDroneSC.bind`). Появляются в 24–40 блоках за краем купола со стороны ворот (точка P_GATE), по `SpawnDirectorSC.spawnAt` (рождены в Si-14, элита по обычному броску), с NBT `scRaid`, не исчезают, пока идёт налёт (`func_110163_bv`), цель — ближайший защитник. Под купол не войдут (IMob выталкивается) — бой у ворот и Причала; стражи бьют налётчиков сами (Ф2), Наладчики бегут к Ядру (Ф2).
- **Ход**: следующая волна — когда текущая выбита (через 10 с) или через 2400 тиков в любом случае (`WAVE_TICKS`). Строки защитникам: `sc.raid.start` (имя поселения), `sc.raid.wave` (N из 3), сирена у Ядра.
- **Победа** (ПН-3) — третья волна выбита: каждому защитнику (онлайн, в reach + 32) — «Доверие» 30 / 40 / 50 по размеру (`ReputationSC.give(rec, n, SRC_RAID, day)`), 6 / 9 / 12 КЧ (в инвентарь, иначе под ноги; в суточный лимит 64 не входит — как Пропуск), «сундук Старейшины» — 2 / 3 / 4 броска `RuinLootSC.nodeChest` в инвентарь; `sc.raid.won`.
- **Поражение** (ПН-4) — 12 000 тиков прошло или ни одного защитника в reach + 32 дольше 1200 тиков: торговля поселения закрыта на сутки (`extra` «R».`closed` = время + 24000; `RaidSC.tradeClosed` — C отказывает в `TradeServerSC` с `sc.trade.refuse.raid`); `sc.raid.lost`; налётчики теряют `scRaid` и постоянство (обычный деспавн).
- ПН-5: без игрока ничего не симулируется — налёт без защитников не начинается, брошенный кончается поражением, жители и стражи не погибают «за кадром». Перезапуск сервера посреди налёта — налёт просто кончился (в памяти), день `last` уже записан.
- ПН-6: убийства налётчиков считаются в «Доверие» и контракты как обычно (они рождены в Si-14, `EconomyEventsSC` Ф2).

### 6.2 Детали
Налёты не трогают лимиты SpawnDirector (свои волны); один налёт на поселение; два игрока у одного поселения — защитники оба, награда каждому; игрок у двух поселений в одну ночь — бросок у каждого.

### 6.3 Эхо моста (`BridgeEchoSC`; ПН-9…ПН-11, предложение §9 событие 4)
- ПН-9 — домой: игрок вышел из Подложки в другой мир мостом **без Щита портала** (`scan.shield` — передаёт ядро) — 10%: у точки прибытия 3–6 частиц роя (новый рой там: `EntityNaniteSC.spawnEcho`, S дописывает в класс X — флаг `scEcho` со временем рассеяния 1200 тиков, не рождены в Si-14 — без добычи; вне Подложки они и так ничего не тянут — правило X); через 60 с рассеиваются искрами. Со Щитом — 0%.
- ПН-10 — «Эхо прокола»: игрок пришёл в Подложку — 10%, если не на Причале (`SettlementSC.dockZoneAt`) и не в куполе / поясе: через 1200 тиков в 16–28 блоках от точки прибытия — 2–4 Мародёра (`spawnAt`, рождены в Si-14, элита по броску), цель — этот игрок; `sc.echo.squad`. Не чаще раза в 12 000 тиков на игрока (`scEchoCd`).
- ПН-11: отряды ждут в памяти (UUID, мир, точка, время); перезапуск их забывает; чанк точки не загружен — отряд ждёт до 600 тиков, потом отменяется. Прыжок корабля Vehicles эхо не даёт (только `enter` моста).

## 7. Существа — пакеты M (7.1–7.4, 7.12–7.13) и N (7.5–7.11)

### 7.0 Общее
- Бронированные — наследники `EntityArmoredMobSC` (super во всём, что он переопределяет), шум — `EntitySubstrateMobSC`, мирный — `EntityCreature`. Все — `ISubstrateNativeSC` (мост, банка душ, флаг «рождён в Si-14»), фракция — `IFactionSC` (§0). Потолки урона — константы в классах, свой `attackEntityAsMob` × `damageMul()`; стрелковое — мгновенное с видимым телеграфом, снарядов-сущностей нет (ПС-4).
- Редкие (Вожак, Часовой, Затмение): `RareRegistrySC` — `alive` при появлении (ставит тот, кто ставит: C для Вожака и Часового, M для Затмения), в `onDeath` (только при `dead`) — `died(keyOf(this), now)`, раз в 100 тиков — `seen`. Не исчезают (`func_110163_bv`). Элита своего вида (`alwaysElite` в заготовке).
- Добыча — ПТ-1 через `IFactionLootSC`; трофеи — `MobDropsSC` (M).
- Рендер: M — `mob/client/MobClientSC` (свои), N — `mob/client/NoiseClientSC` (свои). Текстуры — свои скрипты `tools/gen_*_tex.py`.

### 7.1 Перегретый (`EntityOverheatedSC`, M; ПС-31)
| Что | Число |
|---|---|
| HP, скорость | 30, 0,2 (медленно идёт к источникам энергии: `TileEntityEnergyBase` в 16 блоках, раз в 100 тиков) |
| Броня | Квант 2–4 части, аварийный режим (без гелия), запас от 0–15 блокируемого урона; свечение раскалённое (оранжево-красное) |
| Удар | кулак 3–4 и поджог на 3 с (`setFire(3)`) |
| Радиация | `RadiationSC.report(..., 2, 4)` раз в 20 тиков (блоки режут) |
| Смерть | пар: 4 урона в радиусе 3 всем живым не-местным, частицы; блоки целы |
| Вода | ПКМ ведром воды (ведро пустеет, кроме креатива) или стоит в воде: 6 урона и −50% защиты на 10 с (половина того, что гасит броня, проходит) |
| ПЭ-1 | почти бесполезен — подавлять и выкачивать нечего (обычная ветка `IEmpTargetSC`) |
| Добыча | Прошивка оболочки 1, Выкипевший контур 20%; трофей: Квант-часть 1% |
| Где | Окисные поля (основной), Разлом |

### 7.2 Вожак лагеря (`EntityWarlordSC`, M; ПС-32)
| Что | Число |
|---|---|
| HP | 90; элита своего вида («Казнь» не действует) |
| Броня | Экзо-нагрудник с гелием (щит), остальное Квант; запас от 80–100; багровое свечение |
| Оружие | Квантовый клинок (8) вблизи, Ионный резак (луч 4) дальше 6 блоков — меняет в руке |
| Бой | как Штурмовик (рывок, удар в спину ×1,5 после рывка); Точка в нагрудник снимает щит |
| Добыча | Ключ лагеря 100% (реликвия `CAMP_KEY`, в NBT — сейф его лагеря из `scCampSafe`), Лом Кванта 4; трофеи: Квант-часть 2%, Экзо-нагрудник 0,5% |
| Где | только лагерь (C ставит; через 3 дня после смерти — снова) |

### 7.3 Сингулярный часовой (`EntitySentinelSC`, M; ПС-33)
| Что | Число |
|---|---|
| HP | 100; элита своего вида |
| Броня | Сингулярная уровня 1 (`SingularLevel` при одевании, один раз; у «старшего» — 20% Часовых, TODO(balance) — высшего уровня), щит, запас от 150–250, подзарядка 4 ед./с после 5 с без урона (`regenPerSecond` 4, `regenDelayTicks` 100); схема H «Пустота», кольцо тёмных частиц (клиент) |
| Оружие | Сингулярный клинок 30% (14), иначе Экзо-клинок (10) |
| ПЭ-1 | 5% носители (при `EmpMobGunSC.allowed()`): Точка и Импульс по правилам ПБ-17, дальность 32 (`EntityAIEmpShooterSC(this, true, true, false, 32)`); без правила — Ионный резак |
| Пресс | раз в 30 с: 3 с гула (`ACT_PRESS`), затем игроки в радиусе 6 на 5 с прижаты к земле (каждый тик вниз, Медлительность IV, без прыжка) |
| Поводок | не дальше 24 блоков от Хранилища (`scHome`), возвращается |
| Добыча | Прошивка 3–5, Опечатанная Крупица 25% (Выброс ×2), Фрагмент Ключа Пробоя 100%; Журнал 50% — Ф5; трофеи: Сингулярная часть 0,2%, Сингулярный клинок 0,1%, ПЭ-1 10% с носителя (≈ 0,5% на Часового) |
| Где | один на Хранилище (C) |

### 7.4 «Затмение» (`EntityEclipseSC`, M; ПС-34)
| Что | Число |
|---|---|
| HP | 140; редкая элита своего вида |
| Броня | Сингулярная высшего уровня, схема C «Багровая», щит, запас от 200–250 |
| Оружие | Сингулярный клинок (13); тяжёлый удар — 8 «Пробоем» после замаха 1 с (`ACT_WINDUP`), откат 100 тиков (TODO(balance)) |
| Охота | цель — игрок с самой сильной бронёй в 32 (очки брони / костюм) |
| «Мерцание» | на 8 блоков раз в 100–200 тиков (`ACT_BLINK`); никогда в поле с F_NO_ENDER (купол) — точка проверяется `fieldWith(F_NO_ENDER)` |
| Отход | HP ниже 25% — бежит (24+ блоков от игроков, `ACT_FLEE`), лечится 1 HP/с и подзаряжается до 60%, возвращается |
| Звук | рядом с ним звуки глохнут (клиент, по желанию M: `PlaySoundEvent17` в 16 блоках — громкость ×0,4) |
| Добыча | Лом Сингулярной брони 2–3, Опечатанная Крупица 30% (Выброс ×2); трофеи: Сингулярная часть уровня 1 0,2%, Сингулярный клинок 0,1% |
| Где | только Разлом; одно на всё измерение (`RareRegistrySC.ECLIPSE`): не ALIVE и через 3 игровых дня после смерти (TODO(balance)); бросок появления 2% на попытку директора в Разломе (Выброс ×3) |

### 7.5 Наводка (`EntityPhantomSC`, N; ПС-35, ПБ-22)
HP 20; без брони; летает и проходит сквозь блоки (`noClip`, своё движение без поиска пути); почти невидима — рисуется только игроку с ночным зрением шлема (`Potion.nightVision` и надетый шлем мода) или с сенсорами Сингулярного шлема (Ш1/Ш2 — N читает клиентское состояние шлема, правка кода брони — запросом координатору), иначе — лёгкое мерцание. Удар: шлем цели гаснет на 4 с (`EmpSC.stormHit(p, 0, 80, 1, 0)` — невосприимчивость 20 с) и 3 «Пробоя» (`BreakdownDamageSC.of(this)`), раз в 40 тиков. Любое попадание ПЭ-1 убивает сразу (`empStruck` → смерть от стрелка); обычное оружие — половина урона. Добыча: Индукционная нить 30%. Где: каньоны, Гряды, Разлом.

### 7.6 Токоед (`EntityLeechSC`, N; ПС-36, ПБ-23)
HP 30; змея из кабелей 3 блока (рендер — сегменты). Обвивает цель на 5 с (`ACT_WRAP`, держится у её ног): 1% заряда каждой части брони мода в секунду (`ItemArmorSC.discharge`), без брони — 2 урона в секунду; откат 100 тиков. Вода (стоит в воде или ведро ПКМ) — короткое замыкание: 10 урона и 3 с оглушения. Импульс ПЭ-1 (`empStruck`) и Сифон — лопается: разряд 6 урона в радиусе 3 всем живым (и стрелку), умирает. Сифон: N добавляет в `EmpSiphonSC` одну ветку — старт потока по `IEmpReactiveSC` зовёт `empStruck(p, SIPHON, …)`, поток не начинается. Добыча: Энергоячейка MV 40%, дроблёная медная руда 1–3. Где: каньоны, Гряды.

### 7.7 Кварцитовый колосс (`EntityColossusSC`, N; ПС-37, ПБ-26)
HP 120; размер 1,4 × 3,6; естественная броня 12 (`getTotalArmorValue`). Днём нейтрален (нападает, если ударили), ночью враждебен. Топот (`ACT_STOMP`, замах 20 тиков) — 6 урона в радиусе 4; бросок кристалла (`ACT_THROW`, прицел 20 тиков, затем мгновенное попадание до 12 блоков по прямой видимости, 7 урона, частицы дуги) — снарядов нет. Грани отражают стрелы (стрела разворачивается, урона нет) и вдвое ослабляют лучи (`MobCombatSC`/источники лучей игрока — N находит тип урона). Взрывы ×1,5, бур мода в руке игрока ×3, удар сбоку или сзади — полный, спереди ×0,7 (TODO(balance)). ПЭ-1 бесполезен: `IEmpUselessSC`; N добавляет в `EmpGunLogicSC.fire` одну ветку перед Медлительностью — EU потрачены, эффекта нет, `sc.emp.hit.useless`. Анализатор — флаг `EMP_USELESS`, WAILA — строка. Добыча: Кварцит 4–8 (руда-мета кварцита или его продукт — по `OreEntry`), Кремнезёмный песок 4–8, 50% — дроблёная танталовая или платиновая руда 1–3. В переписи — уровень Квант (`censusTier`). Где: плато, Гряды.

### 7.8 Дуговой ходок (`EntityStriderSC`, N; ПС-38, ПБ-24)
HP 60; тренога высотой 4. Только в ЭМИ-бурю (M зовёт его в любом месте, кроме Узла, пока буря идёт); буря кончилась — через 600 тиков рассыпается искрами (без добычи). Цепная молния (`ACT_ARC`, набор 20 тиков): 5 урона цели в броне мода и ещё до 3 целям в броне мода (игроки и бронированные мобы) в 6 блоках друг от друга; на цель без брони мода молния не прыгает (и не бьёт). Стрелы проходят сквозь дугу (урон стрелами 0, пока дуга цела). Импульс ПЭ-1 «схлопывает» дугу на 5 с (`ACT_COLLAPSED`): падает, урон ×2. Вода на электрод (ведро ПКМ) — 8. Добыча: Плазменный конденсат 50%, опыт 40. В переписи — уровень Квант.

### 7.9 Утилизатор (`EntityScrapperSC`, `ScrapperSC`, N; ПС-39, ПТ-19, ПБ-25)
HP 60; естественная броня 10; бункер сзади получает ×2 (`MobCombatSC.behind`). Падальщик: подбирает `EntityItem` в радиусе 6 в мешок (27 ячеек, NBT `scBag`), броню и оружие не надевает; не исчезает, пока мешок не пуст. Точка ПЭ-1 оглушает на 4 с (`ACT_STUNNED`). Смерть: мешок выпадает целиком всегда (ПТ-19 — там вещи игроков), сверху 2–6 дроблёных руд (по ПТ-1).
`ScrapperSC` (Forge `PlayerDropsEvent` в Подложке): EntityItem смерти получают `scDeathDrop` (UUID владельца) и `scDeathAt`. Тик мира (раз в 100): помеченные предметы старше 1200 тиков в загруженном чанке, которых не обслуживает Утилизатор, — Утилизатор в 16–24 блоках (`spawnAt`, вне лимитов, дом — место гибели, поводок 32), идёт и глотает. Владелец ближе 32 — один раз `sc.scrapper.took`. Метка гибели Ф2 ведёт к месту — Утилизатор там. Перезапуск ничего не теряет: метки и мешок — NBT. Обычные спавны — редко (§7.12).

### 7.10 Подложник (`EntityWaferCrabSC`, N; ПС-40)
HP 8; мирный (не IMob — купол его не выталкивает, Peaceful не убирает), убегает от игроков (`EntityAIAvoidEntity`), бродит. Добыча (ПТ-1): Природная пластина 5% — Технолог покупает. Спавн — мирная ветка директора (M, §7.12).

### 7.11 Клиент N
Рендеры шести существ (`NoiseClientSC`), текстуры `tools/gen_substrate_noise_tex.py`, иконки добычи `loot_inductionThread`, `loot_plasmaCondensate`, `loot_naturalWafer`.

### 7.12 SpawnDirector (ПС-41, ПС-42; `SpawnDirectorSC`, `SpawnRulesSC`, M)
- Место точки: зона Узла → таблица Узла; иначе вид биома колонки (`placeOf(node, kind)` — новая перегрузка; старую оставить). Никого в боксе Хранилища (`VaultLayoutSC.inVault`) и в зале литографии.
- Веса (по id пула, TODO(balance)):

| Место | Обх | Мар | Штр | Глу | Обол | Палач | Рой | Дрон-охр | Перегр | Навод | Токоед | Колосс | Утилиз |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Плато | 60 | 40 | — | — | — | — | — | — | — | — | — | 6 | 3 |
| Каньоны | 30 | 70 | 30 | 15 | — | 3 (≥ 1000) | 5 | — | — | 8 | 10 | — | 3 |
| Узел | — | 10 | — | — | 70 | — | 10 | 20 | — | — | — | — | — |
| Гряды | 15 | 25 | 30 | 10 | — | — | 15 | — | — | 8 | 10 | 12 | — |
| Окисные поля | — | 10 | 10 | 20 | — | — | — | — | 40 | — | — | — | 4 |
| Разлом | — | — | — | — | 20 | 15 | 10 | — | 10 | 10 | — | — | — |

  В бурю: Рой ×3 (`StormRulesSC.SWARM_MUL`), Дуговой ходок 12 во всех местах, кроме Узла. Затмение — свой бросок в Разломе (§7.4). Вожак и Часовой — никогда от директора.
- Группы: Перегретые 1–2, Наводка 1–2, Токоед 1, Колосс 1, Ходок 1, Утилизатор 1, Затмение 1.
- Перепись: Колосс, Ходок, Затмение, Часовой, Вожак — уровень Квант и выше; Затмение, Часовой, Вожак — элиты (лимит «1 элита в 128»).
- Элита в Разломе — 12% (`EliteModSC.chancePct(…, true)`, ПС-42).
- Мирная ветка (Подложник): раз в 400 тиков на игрока, не больше 4 в 64 блоках и 12 в мире, плато и Гряды, группа 1–3, `BornInSubstrateSC.mark`; в перепись враждебных не идёт (M правит `add`, чтобы `spawnAt` мирного не считал).
- `spawnAt` (ядро) — для налётов, Эха, жителей лагеря и Хранилища, Утилизатора; вне лимитов.

### 7.13 Палач у плиты, мгла (M)
`EntityExecutionerSC`: в 2 блоках от Краденой зарядной плиты (`CampBlocksSC.isStolenPad`) — `recharge(1)` раз в 20 тиков. Мгла: `EntityAIBeamAttackSC` и `EntityAIEmpShooterSC` / `EmpMobGunSC` — дальность прицела `EmpStormSC.sightRange`.

## 8. Лагеря и Хранилища — пакет C (ПЗ-1…ПЗ-7, ПХ-1…ПХ-6)

### 8.1 Лагерь: место (`CampLayoutSC`, ядро; ПЗ-1)
Отрезок каньона между соседними пересечениями (ось, линия, отрезок) — лагерь с шансом 25%; середина — на осевой каньона, середина отрезка ±48 (до пересечений ≥ 58 с учётом бокса — зона Узла ±24 не задевается); не ближе 400 к точке спавна; бокс 22 вдоль × 8 поперёк; купол поля радиуса 10; жители — 4–6 Мародёров и Вожак, в 30% лагерей дальше 1000 от спавна — Экзо-палач. TODO(balance). Поселения на плато его не касаются (пояс каньона).

### 8.2 Лагерь: структура (`MapGenCampSC`, C; ПЗ-6)
- Старт — в чанке середины (`siteAtChunk`); площадь, задевающая уже созданный чанк (`StructureRuinStartSC.explored`), — пустой старт. Куски — только в окне populate, по `canyonFloorY`; база `ComponentCampSC` («SCSi14CmpPart»), свои — «SCSi14Cmp*» в `registerStructures`.
- Состав: пол выровнен (Кристаллит), навесы (`ruinSC` campTarp) у стен, Краденый генератор поля в середине (`setBlock` → `setup(FIELD_RADIUS, campKey)`, буфер полный), 4 Краденые солнечные панели снаружи купола у концов бокса, Краденая зарядная плита у палача, Сейф Отступников под навесом в куполе. Ни одного настоящего блока мода (панели, плита, генератор — свои блоки C с ломом вместо дропа).
- Жители — один раз, в окне середины (флаг в NBT куска, как у литографии): Мародёры, Вожак (`RareRegistrySC.alive(campKey, …)`, NBT `scCampSafe`), Палач; все рождены в Si-14, постоянны (`func_110163_bv`), дом — бокс (`setHomeArea`, `scHome`), через `SpawnDirectorSC.spawnAt` (без элитного броска для Вожака — он элита сам).

### 8.3 Краденый генератор поля (`TileEntityStolenFieldSC`, инварианты — ядро; ПЗ-2…ПЗ-5)
- Владелец «[Отступники]»; DOME радиуса 10 от генератора; флаги только F_PRIVATE и F_EMP_SCREEN (+ F_SHOW — купол виден; защиты он не меняет); без выталкивания, F_DAMAGE и F_NO_SPAWN; без экрана, без зарядки предметов, без улучшений; багровая оболочка.
- **Исключение по фракции** (ПЗ-3): `targets()` — никто не выталкивается; `deflects()` — выстрел своей фракции (`FactionSC.RENEGADES`) не разворачивается, прочие не-игровые снаряды — как у любого поля. Поле игрока — прежнее (`deflects` базового класса = старая проверка).
- Питание по-настоящему (ПЗ-4): `TileEntityStolenSolarSC` раз в секунду под открытым небом даёт 16 EU/т днём и половину ночью (как панель мода), в бурю 0 (`EmpStormSC.blocksSolar`), прямо в генератор своего лагеря в 24 блоках (`feed`) — без кабеля, игроку не перехватить. Числа C подбирает так, чтобы содержание купола было больше ночного прихода: к полуночи поле садится (`sc.field.warn.down` базового класса). Можно разбить панели (дроп — Лом Нано) или дождаться ночи. Выстрелы ПЭ-1 снаружи поглощает ЭМИ-экран — за счёт буфера: стрельба по полю его сажает.
- Пока поле стоит, чужак не ломает и не ставит блоки внутри, не открывает TE (F_PRIVATE). Поле село — генератор ломается (твёрдость 5): Лом Нано 2 и 1–2 компонента (Контроллер / Транзистор), не рабочий Генератор поля (ПЗ-5). ПКМ — строка с зарядом `sc.camp.field.info`.
- `campTick` (C, раз в 100 тиков): запись лагеря, возвращение (§8.4).

### 8.4 Возвращение лагеря и сейф (`RuinWorldSC`, `BlockCampSC`, C; ПЗ-6, ПЗ-7)
- ПЗ-6: через 3 игровых дня после смерти Вожака (`RareRegistrySC.mayRespawn(state, diedAt, now, 72000)`), если в 48 блоках нет игроков и чанк середины загружен — Вожак и Мародёры до 4–6 живых (считаются по `scHome` в боксе), Палач (если он есть у лагеря), сейф снова заперт (мета 2), отсутствующие панели и генератор — на прежние места, только если там воздух (постройки игроков не трогаются). Тик — у генератора, а если его нет — `RuinWorldSC` (тик мира, раз в 200, по записям `camp:*`). «Потерянный» Вожак (снят без смерти): место загружено, сущности нет `LOST_CHECKS` раз — считается умершим.
- ПЗ-7, Сейф Отступников: неломаемый, в чёрном списке AE2 Spatial (C, `SettlementCompatSC.sendImc`). ПКМ: без Ключа своего лагеря в руке — `sc.camp.safe.locked`; поле лагеря стоит — `sc.camp.safe.powered`; иначе ключ тратится, из сейфа высыпается добыча (`RuinLootSC` — таблица лагеря: КЧ-товары, Лом Кванта, модуль ПЭ-1 5%, Чип брони 3%, Опечатанная Крупица 5%, TODO(balance)), мета 3 «вскрыт» до возвращения лагеря; `sc.camp.safe.opened`. FakePlayer — никогда.

### 8.5 Хранилище: место (`VaultLayoutSC`, ядро; ПХ-1)
Пересечения — блоками 4 × 4; в каждом блоке одно Хранилище под Узлом одного из его пересечений (по хешу); не под пересечениями в кольце 1 вокруг (0, 0) — тогда в этом блоке Хранилища нет. Бункер: середина — пересечение, ±8 (со стенами), пол — пол площади Узла минус 18 (не ниже Y 4), высота 8 (пол и потолок включительно). `nearest` — для пеленгатора.

### 8.6 Хранилище: структура и Часовой (`MapGenVaultSC`, `RuinWorldSC`, C; ПХ-2…ПХ-5)
- Старт — в чанке пересечения с Хранилищем (`hasVault`); площадь старого Узла (explored) — пустой старт. Строится в populate **после** Узлов: бункер из Плиты Хранилища (`ruinSC` vaultPlating: кирка 3, твёрдость 30, стойкость 2000; дроп — Лом Нано 50%), лампы (vaultLamp, свет 10 — немного, `glow`), 1–2 сундука (`Blocks.chest`, таблица Хранилища: Контроллеры, Чипы памяти, Пластины GaAs, модуль ПЭ-1 10%, Опечатанная Крупица 10%, TODO(balance)); шахта от середины площади вниз, запечатанная плитами («запечатанный бункер» — вскрывать киркой). C правит `ComponentNodePlazaSC`: середина площади Узла с Хранилищем свободна от остовов.
- ПХ-3: Часовой ставится один раз при генерации (окно с серединой бункера): рождён в Si-14, постоянен, дом — бункер + 24 (`scHome`), `RareRegistrySC.alive(vaultKey(i, j), …)`.
- ПХ-4: умер — через 7 игровых дней (`SENTINEL_RESPAWN_TICKS`, TODO(balance)), если в 48 блоках нет игроков и бункер загружен, `RuinWorldSC` ставит нового (тот же ключ). Так Фрагменты Ключа Пробоя добываются снова (для двух игроков и для повторов босса в Ф5).
- ПХ-5: Крупицы мира, кроме босса, приходят Опечатанными (§9.4).

### 8.7 Реликвии и пеленгатор (C; ПХ-6, ПЗ-7, ПТ-17)
- `ItemRelicSC`: Ключ лагеря (стек 1, NBT `scCamp` — сейф его лагеря), Фрагмент Ключа Пробоя (стек 4; «четыре складываются в Ключ Пробоя» — Ф5), Опечатанная Крупица: ПКМ — `CrumbLimitSC.take(p, 1)`: 1 → предмет становится Крупицей сингулярности (`ModItems.singularCrumb`), `sc.crumb.opened` (сколько из 3 сегодня); 0 → остаётся запечатанной, `sc.crumb.limit`. В любом мире (лимит общий на UUID). FakePlayer — никогда.
- `ItemRuinFinderSC` («Пеленгатор руин»): ПКМ в Подложке — направление (8 румбов) и расстояние до ближайшей литографии (`RuinLayoutSC.lithoSite` по клеткам в 8 клетках вокруг) или Хранилища (`VaultLayoutSC.nearest`, 2 блока вокруг), что ближе; 8 использований (износ). Вне Подложки — `sc.finder.offworld`. Картограф: 10 КЧ + 1 Фокусирующая линза, «Партнёр», 1 в сутки (§9.5).
- Подсказки TooltipSC у всех мет и у пеленгатора (ключи — §10).

## 9. Трофеи, добыча, Крупицы, обмен — пакеты M (9.1–9.3) и C (9.4–9.5)

### 9.1 Трофеи (ПТ-16; `TrophyTableSC`, `MobDropsSC`, M)
- `SHOWN` += Сингулярные части 12–15 и `bladeSingular` 19 → 24 меты (ПЭ-1М 24 — Ф5). Шансы Сингулярных — уже в таблице (0,2% / 0,1%).
- Свои категории: Вожак — Квант-часть 2% (из надетых Квант), Экзо-нагрудник 0,5%; Часовой — ПЭ-1 10% с носителя (вместо `TrophyTableSC.PE1`). M вводит способ задать категории по классу (свой интерфейс или поля), общий путь для остальных прежний.
- Буря: `trophyChance(..., storm = EmpStormSC.active(world))` (×1,5, с элитой ≤ ×2,5).

### 9.2 Добыча (ПТ-18; M — `MobDropsSC`, `ItemCombatLootSC`)
- Новые меты (§0) падают с: Перегретого (Выкипевший контур 20%), Наводки (Индукционная нить 30%), Ходока (Плазменный конденсат 50%), Затмения (Лом Сингулярной брони 2–3), Подложника (Природная пластина 5%).
- `MobDropsSC.onDrops`: фракционная добыча — и для мирных местных с `IFactionLootSC` (Подложник), не для существ поселения. Мешки Мародёра и Утилизатора — всегда.
- Опечатанные Крупицы (реликвия `SEALED_CRUMB`) — фракционная добыча Часового (25%) и Затмения (30%), при Выбросе ×2; лимит — при распечатывании (§8.7).

### 9.3 Утилизатор хранит вещи (ПТ-19) — §7.9.

### 9.4 Крупицы: суточный лимит (ПТ-17; ядро `CrumbLimitSC`, C)
Не больше 3 Крупиц в игровые сутки на игрока из всех источников мира, кроме босса (Ф5): Опечатанные Крупицы (сундуки Мёртвых Узлов — C меняет `RuinLootSC`: вместо Крупицы 3% — Опечатанная; Хранилища, сейфы лагерей; Часовой, Затмение) и Старейшина (`TradeServerSC`, C: сделка `elderCrumb` проверяет `CrumbLimitSC.take`, отказ `R_CRUMBS` — `sc.trade.refuse.crumbs`). Это закрывает TODO Ф3 «Крупица из сундука не знает игрока». Лимит по UUID — у друга по LAN свой.

### 9.5 Обмен Ф4 (ПО-14…ПО-16; `TradeTableSC`, `TradeServerSC`, C; TODO(balance))
| Житель | Игрок отдаёт | Получает | Уровень | Лимит |
|---|---|---|---|---|
| Газовщик | 1 Выкипевший контур | 3 КЧ | Чужак | 6 |
| Технолог | 4 Природные пластины | 2 КЧ | Чужак | 8 |
| Скупщик | 2 Лома Сингулярной брони | 8 КЧ | Чужак | 8 |
| Скупщик | 4 Индукционные нити | 2 КЧ | Чужак | 8 |
| Скупщик | 2 Плазменных конденсата | 3 КЧ | Чужак | 8 |
| Картограф | 10 КЧ + 1 Фокусирующая линза | Пеленгатор руин | Партнёр | 1 |

ПО-7 соблюдается: ни один предмет не продаётся и не покупается одновременно (пеленгатор только продаётся, добыча только покупается); ПО-16: Крупица Старейшины — в суточный лимит (§9.4); после проигранного налёта торговля поселения закрыта на сутки (`RaidSC.tradeClosed` → `R_RAID`). Заменить TODO в `TradeTableSC` (Ф2–Ф3) на сделки.

## 10. Тексты, справочник, NEI, README — пакет D

### 10.1 Lang (CRLF, UTF-8; ключи `ключ=ru | en` присылают пакеты)
- Биомы: `sc.dim.biome.ridges` «Кварцитовые гряды» | «Quartzite Ridges», `sc.dim.biome.oxide` «Окисные поля» | «Oxide Fields», `sc.dim.biome.rift` «Разлом» | «The Rift».
- Сущности: `entity.SiliconAge.overheated.name` «Перегретый» | «Overheated Shell», `warlord` «Вожак лагеря» | «Camp Warlord», `sentinel` «Сингулярный часовой» | «Singular Sentinel», `eclipse` «Затмение» | «Eclipse», `phantom` «Наводка» | «Induction Phantom», `leech` «Токоед» | «Current Leech», `colossus` «Кварцитовый колосс» | «Quartzite Colossus», `strider` «Дуговой ходок» | «Arc Strider», `scrapper` «Утилизатор» | «Scrapper Crab», `waferCrab` «Подложник» | «Wafer Crab».
- Добыча: `item.siliconage.combatLoot.boiledLoop.name` «Выкипевший контур» | «Boiled-out Loop», `inductionThread` «Индукционная нить» | «Induction Thread», `plasmaCondensate` «Плазменный конденсат» | «Plasma Condensate», `singularScrap` «Лом Сингулярной брони» | «Singular Armour Scrap», `naturalWafer` «Природная пластина» | «Natural Wafer»; `sc.tooltip.loot.<key>` (+ .details, .howto).
- Блоки: `tile.siliconage.wild.<singularGlass|pillarCore|wireweed|diodeMoss|fumarole|arcCrack|ionGeyser|crackedRtg>.name` «Сингулярное стекло», «Сердцевина прокола», «Проволочник», «Диодный мох», «Гелиевая фумарола», «Пробойная трещина», «Ионный гейзер», «Треснувший РИТЭГ» | «Singular Glass», «Puncture Core», «Wireweed», «Diode Moss», «Helium Fumarole», «Breakdown Crack», «Ion Geyser», «Cracked RTG»; `sc.tooltip.wild.<имя>` (+ .details, .howto); `tile.siliconage.substrateRock.monocrystal.name` «Монокристалл» | «Monocrystal» + подсказки породы; `tile.siliconage.camp.<stolenField|stolenSolar|stolenPad|campSafe|campSafeOpen>.name` «Краденый генератор поля», «Краденая солнечная панель», «Краденая зарядная плита», «Сейф Отступников», «Сейф Отступников (вскрыт)» | «Stolen Field Generator», «Stolen Solar Panel», «Stolen Charge Pad», «Renegade Safe», «Renegade Safe (opened)»; `sc.tooltip.camp.<имя>` (+ .details, .howto); `tile.siliconage.ruin.<vaultPlating|vaultLamp|campTarp>.name` «Плита Хранилища», «Лампа Хранилища», «Навес Отступников» | «Vault Plating», «Vault Lamp», «Renegade Tarp» + `sc.tooltip.ruin.*`.
- Предметы: `item.siliconage.relic.<campKey|keyFragment|sealedCrumb>.name` «Ключ лагеря», «Фрагмент Ключа Пробоя», «Опечатанная Крупица» | «Camp Key», «Breakdown Key Fragment», «Sealed Crumb»; `sc.tooltip.relic.*`; `item.siliconage.ruinFinder.name` «Пеленгатор руин» | «Ruin Direction Finder»; `sc.tooltip.ruinFinder` (+ .details, .howto с %s — осталось использований).
- Сообщения (S): `sc.storm.warning`, `sc.storm.start`, `sc.storm.end`, `sc.storm.hud.warn` («ЭМИ-буря через %s с»), `sc.storm.hud` («ЭМИ-буря: %s»), `sc.haze.start` / `.end`, `sc.emission.start` / `.end`, `sc.emission.hud`; `sc.raid.start` («Налёт на %s! Отступники идут к воротам»), `sc.raid.wave` («Волна %s из %s»), `sc.raid.won`, `sc.raid.lost`; `sc.echo.squad`, по желанию `sc.echo.swarm`.
- C: `sc.camp.field.info`, `sc.camp.safe.locked`, `sc.camp.safe.powered`, `sc.camp.safe.opened`; `sc.crumb.opened`, `sc.crumb.limit`; `sc.finder.litho`, `sc.finder.vault`, `sc.finder.none`, `sc.finder.offworld`, `sc.finder.dir.<n|ne|e|se|s|sw|w|nw>`; `sc.trade.result.raid`, `sc.trade.result.crumbs` (по схеме `RESULT_KEYS` T), `sc.trade.offer.<новые ключи>`.
- N: `sc.emp.hit.useless`, `sc.scrapper.took`, `sc.waila.mob.useless`, `sc.analyzer.mob.useless`, по желанию `sc.mob.<имя>.*`. M: по желанию реплики `sc.mob.eclipse.*`, `sc.mob.sentinel.*`.
- Смертей новых нет: «Пробой» — `death.attack.scBreakdown*` Ф3.

### 10.2 Справочник (глава WORLD, `BookWorldSC`, `BookContent`)
Новые статьи в конце главы: `world.biomes4` «Гряды, Окисные поля, Разлом» (клетки и доли из `SubstrateCellsSC`, «земля мира Ф1–Ф3 остаётся плато»), `world.radiation` «Фоновая радиация» (числа `SubstrateRadiationSC`, защита), `world.resources4` «Фумаролы, трещины, жеоды, гейзеры», `world.storm` «ЭМИ-буря» (числа `StormRulesSC`, укрытие), `world.haze` «Мгла и Выброс», `world.raids` «Налёты» (`RaidRulesSC`), `world.echo` «Эхо моста», `world.camps` «Лагеря Отступников» (поле, панели, ночь, ключ, сейф, 3 дня), `world.vaults` «Хранилища и Часовой» (Фрагменты, 7 дней), `world.eclipse` «Затмение», `world.noise4` «Наводка, Токоед, Колосс, Ходок», `world.scrapper` «Утилизатор и Метка гибели», `world.fauna` «Подложник», `world.crumbs` «Крупицы сингулярности» (лимит 3, Опечатанные). Дописать: `world.trophies` (Сингулярные, SHOWN), `world.trade` (сделки Ф4 — из `TradeTableSC`), `world.overheated` в бестиарий. Все числа — из кода. G по новым блокам, предметам и яйцам открывает статьи. Таблицы имён в `BookWorldSC` (`ROCK_NAMES`, `LOOT_NAMES` и т. п.) — дописать.

### 10.3 NEI
Скрыть: `substrateVent` (все меты), `campSC` меты 2–3 (сейф), `singularGlass` мета 1 (сердцевина), `stolenField` (ломается только в лагере — по желанию D показать со страницей «Как получить: только в лагерях Отступников»). Показать: флору, Сингулярное стекло, монокристалл, реликвии, пеленгатор, новые меты добычи, Сингулярные трофеи (по SHOWN), 10 яиц, руинные меты 4–6. «Как получить»: блоки — «генерируется в <биом> Подложки»; добыча — «падает с <моба> (шанс)»; реликвии — с Вожака / Часового / из сундуков; пеленгатор — «Картограф поселения»; трофеи — моб и шанс. `NeiCoverageSC` не должен отмечать новое.

### 10.4 README (черновик `docs/readme-substrate-draft.md`)
Новые биомы и правило «старая земля остаётся плато»; фоновая радиация и защита; ЭМИ-буря, мгла, Выброс и правило `empStorms`; налёты и `raids`; лагеря, Хранилища, Затмение; суточный лимит Крупиц; EnderIO / DE: `SiliconAge.overheated`, `warlord`, `sentinel`, `eclipse`, `phantom`, `leech`, `colossus`, `strider`, `scrapper`, `waferCrab` — в чёрные списки банки душ и спавнеров; AE2 Spatial IO не берёт источники и сейф; одинаковые id трёх новых биомов у сервера и клиентов.

## 11. Тесты — пакет D

### 11.1 Самотест `debug/SelfTestHazardSC` (из `SelfTestSC.run`, после `SelfTestCombatSC`)
Каждый раздел — сам по себе (незаконченное тело пакета роняет только свой раздел).
- Конфиг: 12 правил (`empStorms`, `raids` — true), три id биомов не публичные и по умолчанию 242–244.
- `SubstrateSC.pickBiomeId(slots, want, int[])`: пять подряд на пустом массиве — 240…244; занятые сдвигаются мимо выбранных; старая сигнатура — как раньше.
- `SubstrateCellsSC`: `columnKind` (пояс — каньоны, полоса 25…40 — плато, 41 — вид клетки), `inner`, `chunkHasInner` (смещения 0–1 и 14–15 — нет, 2–13 — да); `pureKind` — детерминизм, кольцо спавна — плато, клетка поселения и клетка литографии — плато, Разлом только дальше 1500, доли по выборке 4096 клеток (Гряды ≈ 18 ± 4%, Окисные ≈ 15 ± 4%, Разлом дальних ≈ 5 ± 2%).
- `SubstrateWildTerrainSC` (после W): `ridgeY` в 64…140 и равен `terraceY` на краю внутреннего квадрата; `duneY` в 64…80; `riftVoid` ложь на ободе; острова в 40…100; `pillar`.
- `SubstrateRadiationSC.backgroundLevel`: 0, 0, 0, 1, 2.
- `StormRulesSC`: доля дней с бурей 20 ± 3% (выборка 2000 дней), длительность 3600…6000, буря внутри суток, `phaseAt` (за 601 тик — CALM, за 600 — WARNING, в окне — STORM, на конце — CALM), `covered(2)` ложь / `(3)` правда; мгла не пересекает бурю и её предупреждение (2000 дней), её длина 2400…3600; Выброс внутри недели, 12 000.
- `RaidRulesSC`: `night`, `reach` (64 / 80 / 96), `mayRoll` (правило, ночь, уже бросали, откат 5 дней, защитник), доля бросков 10 ± 2%, `wave` (состав трёх волн по размерам, `eliteWave`), награды.
- `BridgeEchoSC.homeEcho` / `arrivalEcho`: таблица истинности (Щит, Причал, откат).
- `CrumbLimitSC.allowed` (0+1 из 3 → 1, 3 → 0, 2+5 → 1).
- `FactionSC.defaultOf` по классам (без мира — по `Class`: D может проверить через заглушки), `of` / `is`.
- `RareRegistrySC`: NBT туда-обратно, `mayRespawn`, `missed` после 10.
- `CampLayoutSC`: детерминизм, доля отрезков 25 ± 4%, бокс не задевает зону Узла и пояс поселения, не ближе 400 к спавну, `siteAtChunk` / `siteNear` согласованы с `site`. `VaultLayoutSC`: ровно ≤ 1 Хранилище в блоке 4 × 4, никогда в кольце спавна, `inVault` / `vaultNear`, `nearest`.
- `SpawnRulesSC` (M): веса шести мест — сумма > 0, длина = пулу (22); Затмение / Вожак / Часовой — 0 в таблицах; буря — рой ×3, ходок появляется.
- Пул: 22 имени, `nameOf(12)` = «overheated», `nameOf(21)` = «waferCrab», `nameOf(22)` = null; HP заготовок (30, 90, 100, 140, 20, 30, 120, 60, 60, 8); `IEmpReactiveSC` у Наводки, Токоеда, Ходока, Утилизатора; `IEmpUselessSC` у Колосса; фракции.
- Добыча: `LootKind` — 12 видов, порядок, последний `naturalWafer`, иконки и тексты; `SHOWN` — 24.
- Реликвии: 3 меты и пеленгатор — имена и подсказки на обоих языках; Опечатанная Крупица → Крупица.
- Тексты: все ключи §10.1 в обоих lang с теми же аргументами; глава WORLD строится без сырых ключей.
- **Честные поправки Ф1–Ф3** (D, только они): `SelfTestCombatSC.config` — 12 правил вместо 10; `SelfTestCombatSC` пул — 22 вместо 12; `lootKinds` — 12 видов, последний `naturalWafer`; `SelfTestSettlementSC` — `ROCK_METAS` 10 и `BookWorldSC.ROCK_NAMES`, `LOOT_NAMES`; число трофеев SHOWN; число яиц. Ни одна проверка ПЭ-1 Ф1 (`SelfTestEmpSC`) не правится.

### 11.2 Мировой тест `debug/WorldTestHazardSC` (`-Dsc.worldtest=true`, после `WorldTestArmourerSC`)
Вывод — `[SC-WORLDTEST] PASS / FAIL / SKIP / INFO hazard: …`. SKIP, если `!SubstrateSC.active()`. Чанки — тикетом, всё изменённое возвращается, сущности удаляются, FakePlayer в мир не спавнится (как в Ф3).
1. Радиация: FakePlayer на колонке Окисных полей (биом массива) — `RadiationSC.levelAt` ≥ 1; в Разломе ≥ 2; в поле с F_RADIATION — защита 100% (`perSecond`: F_FIELD).
2. Буря: `startNow(w, 20, 400)` — фаза WARNING, затем STORM; `pulse(w)` — FakePlayer в Нано под небом: одна часть подавлена 60–80 тиков; под 3 блоками — нет; в поле игрока с F_EMP_SCREEN и 15 000 EU — нет, EU списаны; повтор сразу — невосприимчив; Обходчик под небом — часть подавлена; Оболочка — `rebooting()`; Наладчик и страж — не тронуты; Солнечная панель мода под небом — 0 EU (`blocksSolar`); `MobDropsSC.trophyChance` ×1,5; `stopNow` — CALM.
3. Мгла и Выброс: `sightRange` ≤ 24 в мгле; фон Разлома ×3 при Выбросе.
4. Налёт: тестовое поселение (или запись реестра с Ядром в загруженном чанке) и FakePlayer-защитник с «Доверием» 150 — `startNow`: волна 1 у ворот (налётчики с `scRaid`, рождены в Si-14); убить волны — победа: «Доверие» +rep, КЧ в инвентаре; второй налёт брошен (защитник ушёл) — `tradeClosed` правда, `TradeServerSC.execute` отказывает `R_RAID`.
5. Эхо: `crossed` из Подложки в dim 0 без Щита при броске 0 (тестовый Random или чистая функция + фабрика) — частицы у точки, через 1200 тиков исчезли; со Щитом — нет; в Подложку — отряд через 1200 тиков, у Причала — нет.
6. Крупицы: FakePlayer — `take` до 3 за день (FakePlayer → 0: проверить отказ), для «настоящего» — через запись реестра напрямую: `allowed`.
7. Регресс: `WorldTestEmpSC`, `WorldTestSubstrateSC`, `WorldTestSettlementSC`, `WorldTestCombatSC`, `WorldTestArmourerSC` — как раньше (по логу).

### 11.3 Мировой тест `debug/WorldTestWildSC` (после `WorldTestHazardSC`)
Вывод — `… wild: …`.
1. Регистрация: пять биомов в своих слотах, типы BiomeDictionary; 10 новых имён в `EntityList`; структуры `SCSi14Camp`, `SCSi14Vault`; блоки и TE.
2. Клетки: найти по `pureKind` ближайшие клетки Гряд, Окисных полей и Разлома; сгенерировать по чанку внутреннего квадрата — биом массива = вид, ни одного `Blocks.stone` в Y 0–160, высоты по чистым функциям; Разлом — дно из Сингулярного стекла, остров; клетка, чей внутренний чанк создан заранее (тест сначала создаёт чанк, `forget` решения) — LEGACY, плато; каскада нет (при генерации не загружен ни один чанк за «окно + 1»).
3. Источники: фумарола рядом с баком мода — за 100 тиков 5–10 мБ гелия; трещина рядом с кабелем — 32 EU/т в сеть, FakePlayer в Нано в 2 блоках — заряд −0,5%/с, без брони — урон «Пробоем»; гейзер — разряд в 6–10 с; РИТЭГ — источник радиации; жеода — монокристалл даёт Металлургический кремний.
4. Лагерь: сгенерировать ближайший к (0, 0) за 400 блоков — генератор с владельцем «[Отступники]», флаги ровно F_PRIVATE | F_EMP_SCREEN | F_SHOW; Мародёр внутри не выталкивается (позиция через 40 тиков в куполе); стрела с `shootingEntity` = Мародёр не развёрнута, стрела скелета — развёрнута; FakePlayer не ломает блок внутри; панели кормят генератор днём, ночью (`setWorldTime`) буфер убывает; сейф: без ключа отказ, с ключом при поле — отказ, поле село — добыча, мета 3; Вожак в реестре ALIVE; убить Вожака — DEAD; через 3 дня (перевод времени) без игроков — Вожак снова.
5. Хранилище: у ближайшего пересечения с `hasVault` — бункер из плит, Часовой внутри (ALIVE в реестре), сундук; смерть Часового FakePlayer'ом — Фрагмент не выпал (ПТ-1), «настоящий» убийца — решающая функция `MobDropsSC` с Фрагментом и Опечатанной Крупицей по шансу.
6. Существа: каждый из 10 через `onSpawnWithEgg` — HP, броня в границах (бронированные), потолки урона по FakePlayer; Наводка от Точки — мертва; Токоед от Импульса — лопнул (урон вокруг); Колосс — ПЭ-1 без эффекта и без Медлительности; Ходок без бури исчезает; Утилизатор: смерть FakePlayer'а с вещами в Подложке (`PlayerDropsEvent` вручную) — метки на предметах, через 1200 тиков Утилизатор рядом, вещи в мешке; убить — всё выпало.
7. Лимиты: SpawnDirector в каждом новом месте — не больше 8 в 64, Затмение не больше одного в мире (серия 200 попыток с подменой броска).

### 11.4 Только в игре (после пакетов)
Вид Гряд, Окисных полей и Разлома, швы у полос плато, туман Разлома и мгла; ЭМИ-буря: небо, строки развёртки, сирена в куполе, Наладчики уходят домой, импульсы вдвоём по LAN (укрытие у одного, нет у другого), панели мода на 0, Ходоки, Оболочки замирают; Выброс; радиация и защита (свинец, D2O, поле, душ поселения); налёт вдвоём у ворот Посёлка, победа и награды обоим, проигрыш и закрытая торговля; Эхо домой без Щита и в Подложку не у Причала; лагерь: ночью поле садится, разбить панели, сейф ключом; Хранилище: вскрыть, бой с Часовым (пресс, носитель ПЭ-1), Фрагмент; Затмение (мерцание, отход, приглушённые звуки); Наводка в ночном зрении и сенсорах; Токоед, Колосс (стрелы отскакивают), Утилизатор с вещами после смерти и Метка гибели; Подложник; модели и анимации; Крупицы: лимит 3 в день вдвоём; JourneyMap; FPS в Разломе.

## 12. Пакеты (файлы не пересекаются)

Общие правила владения — как в Ф1–Ф3: каждый существующий файл правит ровно один пакет или координатор; новые файлы создаёт только их пакет (новые имена — с префиксом своей темы); нужна правка чужого файла — в отчёт координатору (файл, место, текст). Заготовки ядра (§0) принадлежат пакетам, указанным в их Javadoc.

### W — мир: биомы, рельеф, блоки, источники, радиация, туманы
- **Существующие:** `dim/ChunkProviderSubstrateSC.java` (вызовы ядра оставить), `dim/SubstrateTerrainSC.java` (только новые чистые помощники; функции Ф1 — без изменений), `dim/BiomeSubstrateSC.java` (тела, без смены констант ядра), `dim/WorldChunkManagerSubstrateSC.java` (только если понадобится; вызов `SubstrateCellsSC` оставить), `dim/WorldProviderSubstrateSC.java` (не трогать строки бури ядра), `dim/MapGenSubstrateCavesSC.java`, `dim/SubstrateOreGenSC.java`, `dim/SubstrateOreTableSC.java`, `dim/SubstrateCompatSC.java` (IMC AE2 для источников), `dim/client/SubstrateClientSC.java`, `block/BlockSubstrateRockSC.java`, `block/ItemBlockSubstrateRockSC.java` (мета 9), `tileentity/TileEntityShowcaseSC.java` (только душ ворот, §4.6), `dim/SubstrateRadiationSC.java` (тело может уточнить), заготовки `dim/SubstrateWildTerrainSC.java`, `dim/SubstrateWildGenSC.java`, `block/BlockSingularGlassSC.java`, `block/BlockSubstrateFloraSC.java`, `block/BlockSubstrateVentSC.java`, `block/ItemBlockSubstrateWildSC.java`, `tileentity/TileEntitySubstrateVentSC.java`, `tileentity/TileEntityArcCrackSC.java`.
- **Новые:** `dim/SubstrateRidges*SC.java` / `SubstrateRift*SC.java` (по необходимости), `assets/siliconage/textures/blocks/substrate_*.png` (новые), `tools/gen_substrate4_tex.py`.
- **Не трогает:** `SubstrateCellsSC`, `SubstrateSC`, `SubstrateConfigSC`, `SubstrateWildBlocksSC`, `SubstrateBlocksSC` (константы ядра), `RadiationSC`, структуры C.

### S — погода и события: ЭМИ-буря, мгла, Выброс, налёты, Эхо моста
- **Существующие:** заготовки `hazard/EmpStormSC.java`, `hazard/client/StormClientSC.java`, `hazard/RaidSC.java`, `hazard/BridgeEchoSC.java`; `hazard/StormRulesSC.java`, `hazard/RaidRulesSC.java` (только тела, числа и сигнатуры — ядра); `settle/npc/ai/EntityAITunerHomeSC.java` (только условие бури); `mob/EntityNaniteSC.java` (только частицы Эха: `spawnEcho`, рассеяние по `scEcho`); `assets/siliconage/sounds.json` (только дописывать).
- **Новые:** `hazard/StormPulseSC.java`, `hazard/RaidRunSC.java` и т. п. (по необходимости), `hazard/client/*.java`, `assets/siliconage/textures/misc/storm_*.png`.
- **Не трогает:** `StormStateSC`, `StormDataSC`, `HazardNetSC` (ядро), `EmpSC`, `MobArmorSC` (зовёт `stormHit`), `TradeServerSC` (C), `SettlementRegistrySC`.

### M — бронированные Ф4, спавн, трофеи, добыча
- **Существующие:** заготовки `mob/EntityOverheatedSC.java`, `EntityWarlordSC.java`, `EntitySentinelSC.java`, `EntityEclipseSC.java`; `mob/SpawnDirectorSC.java` (помощник `spawnAt` ядра не ломать), `mob/SpawnRulesSC.java` (старые сигнатуры оставить), `mob/MobDropsSC.java`, `mob/TrophyTableSC.java`, `mob/ItemCombatLootSC.java` (подсказки; enum ядра не менять), `mob/MobEventsSC.java` (по необходимости), `mob/MobCombatSC.java`, `mob/EntityExecutionerSC.java` (только плита), `mob/ai/EntityAIBeamAttackSC.java` и `mob/ai/EntityAIEmpShooterSC.java`, `emp/EmpMobGunSC.java` (только дальность в мгле), `mob/client/MobClientSC.java`, `mob/client/RenderSubstrateMobSC.java`, `mob/MobRecipesSC.java` (по желанию: Дробилка для Лома Сингулярной брони).
- **Новые:** `mob/ai/EntityAISentinel*SC.java`, `EntityAIEclipse*SC.java`, `EntityAIOverheated*SC.java`, `EntityAIWarlord*SC.java`, `mob/client/Render*SC.java` / `Model*SC.java` для своих, `assets/siliconage/textures/entity/overheated.png`, `warlord.png`, `sentinel.png`, `eclipse.png`, `textures/items/loot_boiledLoop.png`, `loot_singularScrap.png`, `tools/gen_substrate_mobs4_tex.py`.
- **Зависит от:** N (классы шума — только конструкторы), C (`CampBlocksSC.isStolenPad`, `ItemRelicSC` меты, `VaultLayoutSC.inVault`), S (`EmpStormSC.active` / `emission` / `sightRange`).

### N — шум и мирный: Наводка, Токоед, Колосс, Ходок, Утилизатор, Подложник
- **Существующие:** заготовки `mob/EntityPhantomSC.java`, `EntityLeechSC.java`, `EntityColossusSC.java`, `EntityStriderSC.java`, `EntityScrapperSC.java`, `EntityWaferCrabSC.java`, `mob/ScrapperSC.java`, `mob/client/NoiseClientSC.java`; `emp/EmpGunLogicSC.java` (только ветка `IEmpUselessSC`), `emp/EmpSiphonSC.java` (только старт по `IEmpReactiveSC`), `mob/MobAnalysisSC.java`, `mob/client/MobAnalysisClientSC.java`, `compat/WailaEntitySC.java` (строки новых), `mob/ai/EntityAISentryDroneBeamSC.java` (только дальность в мгле).
- **Новые:** `mob/ai/EntityAIPhantom*SC.java`, `EntityAILeech*SC.java`, `EntityAIColossus*SC.java`, `EntityAIStrider*SC.java`, `EntityAIScrapper*SC.java`, `EntityAICrab*SC.java`, `mob/client/Render*SC.java` / `Model*SC.java` для своих, `assets/siliconage/textures/entity/phantom.png`, `leech.png`, `colossus.png`, `strider.png`, `scrapper.png`, `waferCrab.png`, `textures/items/loot_inductionThread.png`, `loot_plasmaCondensate.png`, `loot_naturalWafer.png`, `tools/gen_substrate_noise_tex.py`.
- **Не трогает:** `SpawnDirectorSC` (спавн шума — M по весам §7.12), `MobDropsSC` (M), игроковые ветки ПЭ-1 (PvP без изменений).

### C — лагеря, Хранилища, реликвии, обмен Ф4
- **Существующие:** заготовки `ruin/MapGenCampSC.java`, `ruin/MapGenVaultSC.java`, `ruin/RuinWorldSC.java`, `block/BlockStolenFieldSC.java`, `block/BlockCampSC.java`, `block/ItemBlockCampSC.java`, `tileentity/TileEntityStolenSolarSC.java`, `ruin/ItemRelicSC.java`, `ruin/ItemRuinFinderSC.java`; `tileentity/TileEntityStolenFieldSC.java` (только `campTick` и новое; инварианты ядра не ослаблять); `ruin/CampLayoutSC.java`, `ruin/VaultLayoutSC.java` (только числа); `ruin/RuinLootSC.java`, `ruin/ComponentNodePlazaSC.java` (только середина площади у Хранилища), `ruin/client/RuinClientSC.java` (по необходимости), `block/BlockRuinSC.java`, `block/ItemBlockRuinSC.java` (меты 4–6), `settle/trade/TradeTableSC.java`, `settle/trade/TradeServerSC.java`, `settle/trade/TradeRulesSC.java` (по необходимости), `settle/SettlementCompatSC.java` (IMC AE2 для сейфа).
- **Новые:** `ruin/ComponentCamp*SC.java` («SCSi14Cmp*»), `ruin/ComponentVault*SC.java` («SCSi14Vlt*»), `assets/siliconage/textures/blocks/camp_*.png`, `ruin_vaultPlating.png`, `ruin_vaultLamp.png`, `ruin_campTarp.png`, `textures/items/relic_*.png`, `ruinFinder.png`, `tools/gen_camps_tex.py`.
- **Зависит от:** M (классы Вожака, Часового, Палача — конструкторы; `spawnAt`), S (`RaidSC.tradeClosed`, `EmpStormSC.blocksSolar`).

### D — тексты, справочник, NEI, тесты, README
- **Существующие:** `assets/siliconage/lang/ru_RU.lang`, `en_US.lang` (CRLF), `manual/BookWorldSC.java`, `manual/BookContent.java` (только `build()` / `chapterIcon` / ссылки G), `nei/NEISiliconAgeConfig.java`, `nei/ObtainHandlerSC.java`, `nei/NeiCoverageSC.java`, `debug/SelfTestSubstrateSC.java`, `debug/SelfTestSettlementSC.java`, `debug/SelfTestCombatSC.java`, `debug/WorldTestSubstrateSC.java`, `debug/WorldTestSettlementSC.java`, `debug/WorldTestCombatSC.java`, `debug/WorldTestArmourerSC.java` (только честные поправки §11.1), `docs/readme-substrate-draft.md`; заготовки `debug/SelfTestHazardSC.java`, `WorldTestHazardSC.java`, `WorldTestWildSC.java`.
- **Не трогает:** `debug/SelfTestEmpSC.java`, `debug/WorldTestEmpSC.java` (регресс ПЭ-1), `debug/SelfTestSC.java` (вызов уже есть).
- **Порядок:** ключи §10.1 и каркас статей — сразу; числа и тесты на тела W/S/M/N/C — после их пакетов; в конце собрать ключи из отчётов.

### Без владельца в Ф4
Файлы Ф1–Ф3, которые Ф4 не трогает: `settle/SettlementProtectSC`, `GuardAlertSC`, `RosterSC`, `settle/travel/*`, `settle/npc/*` (кроме `EntityAITunerHomeSC`), `settle/gen/*`, `tileentity/TileEntitySettlementCoreSC` (кроме флагов ядра), `mob/EntityLinemanSC`, `EntityMarauderSC`, `EntityTrooperSC`, `EntityJammerSC`, `EntityHollowShellSC`, `EntityRepairDroneSC`, `EntitySentryDroneSC`, `EntityMimicSC`, `mob/EliteSC`, `EliteModSC`, `TrophyRestoreSC`, `ItemTrophySC`, `MobItemsSC`, `ItemSpawnEggSC`, `ruin/MapGenDeadNodeSC`, `MapGenLithographSC`, `RuinLayoutSC`, `dim/SubstrateEntrySC`, `TeleportQueueSC`, `bridge/*`, `manual/BookChapter`. Нужна правка — запрос координатору.

### Только координатор
`SCMod.java`, `util/ConfigSC.java`, `init/ModBlocks.java`, `init/ModItems.java`, `init/ModCreativeTab.java`, `proxy/ClientProxy.java`, `proxy/CommonProxy.java`, `handler/GuiHandlerSC.java`, `handler/ConfigSyncSC.java`, `handler/ArmorNetSC.java`, `CommonEventHandler.java`, `ShieldEventHandler.java`, `item/ArmorLogicSC.java`, `item/ItemArmorSC.java`, `item/BladeLogicSC.java`, `item/SingularSensesSC.java`, `client/SingularClientSC.java`, `util/SingularSenseData.java`, `compat/WailaSC.java`, `radiation/RadiationSC.java`, `tileentity/TileEntityFieldGeneratorSC.java`, `tileentity/TileEntityGeneratorSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `tileentity/TileEntitySettlementCoreSC.java`, `energy/TileEntityEnergyBase.java`, `emp/EmpSC.java`, `emp/IEmpTargetSC.java`, `emp/IEmpReactiveSC.java`, `emp/IEmpUselessSC.java`, `dim/SubstrateConfigSC.java`, `dim/SubstrateSC.java`, `dim/SubstrateNetSC.java`, `dim/SubstrateCellsSC.java`, `dim/SubstrateWildBlocksSC.java`, `dim/SubstrateBlocksSC.java` (константы), `hazard/StormStateSC.java`, `hazard/StormDataSC.java`, `hazard/HazardNetSC.java`, `mob/MobEntitiesSC.java`, `mob/MobArmorSC.java`, `mob/EntityArmoredMobSC.java`, `mob/EntitySubstrateMobSC.java`, `mob/IMobActionSC.java`, `mob/ICensusSC.java`, `mob/IFactionSC.java`, `mob/FactionSC.java`, `mob/RareRegistrySC.java`, `mob/BreakdownDamageSC.java`, файлы ядра Ф2 (`settle/SettlementSC`, `SettlementRegistrySC`, `NpcKindSC`, `SettlementSizeSC`, `ISettlement*SC`, `SettleNetSC`, `SettlementBlocksSC`, `SettlementItemsSC`, `ReputationSC`, `CrumbLimitSC`), `ruin/RuinSC.java`, `ruin/RuinBlocksSC.java`, `ruin/CampBlocksSC.java`, `ruin/RelicItemsSC.java`, `debug/SelfTestSC.java`, `docs/plan-substrate*.md`, `CHANGELOG.md`, `README.md`.

## 13. Жёсткие правила для всех
1. Аддон Vehicles не трогать. Файлы не удалять. Не коммитить.
2. Код брони игрока не трогать (`ItemArmorSC`, `ArmorLogicSC`, Сингулярные умения); заряд частей игроков — только `ItemArmorSC.discharge` / заряд; подавление бурей — только `EmpSC.stormHit`.
3. ПЭ-1 между игроками не меняется: игроковые ветки `EmpGunLogicSC`, `EmpSiphonSC`, `EmpRulesSC`, HUD — прежние; `SelfTestEmpSC` и `WorldTestEmpSC` — зелёные без правок. Новые ветки N — только для `IEmpUselessSC` и `IEmpReactiveSC`.
4. Клиентский код — только в client-пакетах; общий код не зовёт `@SideOnly(CLIENT)`-методы (`getItemInUse` и т. п.); туманы — W, оверлеи бури — S, рендеры — M, N. FakePlayer пакетов не получает.
5. Мир Подложки — только `SubstrateSC.is(world)` / `isDim` на сервере; биом колонки на клиенте — из массива чанка.
6. Мультиплеер: всё решает сервер; состояние игроков — по UUID (лимит Крупиц, откат Эха, награды налёта); два игрока одновременно (буря по каждому, награда налёта каждому, Опечатанные Крупицы у каждого свои 3).
7. Генерация — без каскада: только окно populate, высоты — из чистых функций; никакого `Blocks.stone`; светящиеся — по счёту; старт на открытой земле — пустой; клетка с открытой землёй — плато (ПМ-11). Земля уже созданных чанков не меняется никогда.
8. Каждый новый блок, мета и предмет — сразу с подсказкой TooltipSC (кратко, Shift, Ctrl) и строкой NEI (D).
9. Рецепты — только ванильные предметы и предметы мода (в Ф4 новых рецептов нет; Дробилка для Лома — по желанию M).
10. Тексты добавляет только D; остальные присылают `ключ=ru | en`. Тон — как в остальном UI мода.
11. Реестровые имена, пул сущностей, меты, слоты DataWatcher, ключи NBT, имена MapGenStructureIO, порядок перечислений, виды сообщений — фиксированы (§2), только дописываются.
12. Производительность: AI — видимость не чаще раза в 10 тиков; перепись — раз в тик мира; импульс бури — один проход по `loadedEntityList` и `playerEntities`; налёты и лагеря — медленные тики (100–200); `SubstrateCellsSC` — один вид на чанк; NBT стеков брони мобов после одевания не менять.

## 14. Проверка Ф4 в реальной сборке
По §11.4 вместе с проверкой Ф1–Ф3 (`docs/plan-substrate.md` §10, техпланы Ф2 §11.3, Ф3 §11.4). Сборка пользователя: TLauncher Forge, `-Dsc.packcheck` (память «Silicon Age pack check»), его javaw не закрывать. LAN вдвоём: буря и укрытие у каждого своё, налёт на двоих, лимит Крупиц у каждого свой.

## 15. Неясно / решено координатором
- **Клетки, а не шум биомов.** Новые биомы занимают целые клетки 256 × 256 (внутренний квадрат), каньоны и полоса 40 у линий остаются Ф1 — сетка «мира-микросхемы» цела, структуры Ф2–Ф3 (поселения, литографии — их клетки исключены) и лагеря / Хранилища Ф4 стоят на прежнем рельефе.
- **Земля Ф1–Ф3 не рвётся** (ПМ-11): клетка, внутренние чанки которой уже есть на диске, остаётся плато навсегда. В мире, где игроки много ходили, новых биомов вблизи не будет — только в неоткрытых местах. Это строже, чем «старые чанки не трогаются», но без обрывов в 70 блоков на границе.
- Литографии — только на плато (их клетки не меняют вид), хотя в предложении «Гряды и плато»: так генерация Ф3 не меняется.
- **Выброс (событие 6) перенесён из Ф5 в Ф4**: он целиком из механик Ф4 (Разлом, радиация, Крупицы, Затмение) и стоит немного. **Кремнезёмная мгла** — тоже Ф4 (ПМ-4 Ф1: «своя погода — мгла, ЭМИ-буря — Ф4»). Обе выключаются тем же правилом `empStorms` («своя погода»), чтобы не плодить ключей.
- Сигнал Литографов (событие 5), Караваны (7), журналы, обломки моста Литографов, Координатор арены, босс, Модуль Литографов, схемы окраски, `customSky`, `requireSignal`, новые задания Сингулярного прогресса — Ф5. Светодиодные мотыльки (декоративные) и Собиратели — не в Ф4.
- **Подложник** (мирный краб) — в Ф4: на него ссылалась сделка Технолога («TODO(Ф4)» в `TradeTableSC`), а мирная фауна ни в одну фазу не попала.
- Хранилище «запечатано» плитами: вскрыть киркой (уровень 3, твёрдость 30); отдельной двери и ключа к нему нет. Часовой возвращается через 7 дней — иначе 4 Фрагмента на двоих и повтор босса (недельный цикл предложения) невозможны. TODO(balance).
- Сейф лагеря открывается Ключом своего Вожака и только при погасшем поле: так «разбить панели или дождаться ночи» и «Ключ 100%» работают вместе.
- Краденый генератор: к двум флагам предложения добавлен F_SHOW (купол виден); защиты он не меняет.
- Краденые панели кормят генератор напрямую, без кабеля (игроку нечего снять и подключить к себе); в бурю — 0, как все панели мода.
- **Крупицы мира приходят Опечатанными** (сундуки, Часовой, Затмение): лимит 3 в сутки проверяется при распечатывании у конкретного игрока — так он работает и для сундуков, которые игрока не знают (TODO Ф3 закрыт). Крупица Старейшины — тоже в лимит.
- Импульс бури снимает личный ЭМИ-экран моба (как выстрел ПЭ-1), а не подавляет часть.
- Импульс бури без брони мода — ничего (не Медлительность): буря бьёт по электронике.
- Часовой-носитель ПЭ-1 подчиняется ПБ-17 (Импульс — только по группе, иначе Точка), а не «Импульс по любому с 32 блоков».
- Налёт: «сундук от Старейшины» — броски таблицы Мёртвого Узла прямо в инвентарь (ставить сундук в защищённом куполе бессмысленно: F_PRIVATE не даст его открыть). Брошенный налёт — поражение (торговля закрыта на сутки), жители и стражи не гибнут «за кадром». Перезапуск посреди налёта его кончает.
- Эхо прокола — по каждому переходу игрока в Подложку (не по открытию моста), не чаще раза в 10 мин на игрока; Прыжок корабля Vehicles эха не даёт.
- Новые биомы получили те же типы BiomeDictionary (WASTELAND, DRY, SPARSE): MOUNTAIN у Гряд позвал бы горные генераторы других модов.
- Купола поселений получили F_RADIATION всегда (предложение: «у края Окисных полей»): защита бесплатна, а поселения стоят только на плато — разница только в том, что внутри купола Перегретый тоже не облучает.
- Сообщение `SubstrateNetSC` несёт три новых id биомов после правил: клиент Ф1–Ф3 их просто не прочтёт; сервер Ф1–Ф3 для клиента Ф4 — расхождение в чате (версии должны совпадать).
