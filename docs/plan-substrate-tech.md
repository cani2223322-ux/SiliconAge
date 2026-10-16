# Подложка (Si-14) — техплан Ф1 и разбивка на пакеты (2026-10-15)

Дизайн: `docs/plan-substrate.md` (утверждён, все ★; коды правил ПК/ПМ/ПВ/ПБ/ПС/ПТ — оттуда). Источник идей: `docs/idea-dimension-substrate.md`.
Здесь — архитектура Ф1 под существующий код и разбивка оставшейся работы на 5 параллельных пакетов (W, A, M, E, D) с **непересекающимися файлами**.
Java 6 (без лямбд, diamond, try-with-resources, switch по String, без API Java 7+ вроде `Math.floorMod`, `Objects`, `String.join`), MCP-имена, Forge 1.7.10-10.13.4.1614.
Сборка:
`T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /c/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q`
Строки «Read timed out» и 404 безвредны. runServer не запускать, сборка — всегда clean build. Каждый пакет обязан собираться сам по себе поверх ядра.

## 0. Ядро (уже написано, собирается)

Правит **только координатор**. Если пакету нужна правка ядра или чужого файла — описать её в отчёте.

| Файл | Что |
|---|---|
| `dim/SubstrateConfigSC.java` | Холдер раздела `dimension` (§2): id — приватные с геттерами, правила — `public static`. `load(Configuration)` вызывается из `ConfigSC.load` с тем же Configuration; `writeBiomeId` — запись сдвинутого id биома |
| `dim/SubstrateSC.java` | Регистрация (§1) и вопросы о мире: `init()`, `loadComplete()`, `active()`, `offReason()`, `dimId()`, `providerId()`, `is(World)`, `is(Entity)`, `isDim(int)`, `plateaus()`, `canyons()`, `respawnDimension()`, `bridgeCostMultiplier(int)`, `isProvider(WorldProvider)`; чистые `dimensionRefusal(int)`, `pickBiomeId(BiomeGenBase[], want, notThis)` |
| `dim/SubstrateNetSC.java` | Канал `SiliconAgeDim`: при входе сервер шлёт свои id и правила; клиент применяет правила (восстанавливает свои при выходе), сравнивает id, при расхождении — лог и строка в чате, неизвестный id измерения сервера регистрирует на сессию. `ruleFields()` открыт для самотеста |
| `dim/client/SubstrateIdsClientSC.java` | Клиент: показывает предупреждение о несовпадении id (ключ `sc.dim.ids.mismatch`) |
| `dim/INamedDimensionSC.java` | `String nameKey()` — lang-ключ имени для списка моста |
| `emp/IEmpTargetSC.java` | Цель ПЭ-1, не игрок (§4.5) |
| `mob/IArmoredMobSC.java` | `MobArmorSC mobArmor()` |
| `mob/IExecuteImmuneSC.java` | Маркер: «Казнь» не действует |
| `mob/ISubstrateNativeSC.java` | Маркер: существо Подложки (мост не пропускает, ПВ-8) |
| `mob/BornInSubstrateSC.java` | Флаг «рождён в Si-14»: `mark`, `isBorn`, `clear`. Ключ `scBornSi14` в `getEntityData()` |

Заготовки пакетов (сигнатуры фиксированы, тела — пакетов). Владелец указан в Javadoc каждого файла: W — `WorldProviderSubstrateSC`, `WorldChunkManagerSubstrateSC`, `ChunkProviderSubstrateSC`, `BiomeSubstrateSC`, `SubstrateBlocksSC`, `SubstrateCompatSC`, `SubstrateRecipesSC`, `SubstrateTerrainSC`, `SubstrateOreTableSC`, `client/SubstrateClientSC`. A — `mob/MobArmorSC`, `mob/EntityArmoredMobSC`, `mob/MobArmorMathSC`. M — `mob/MobEntitiesSC` (регистрация уже работает), `EntityLinemanSC`, `EntityMarauderSC`, `SpawnDirectorSC`, `MobEventsSC`, `MobItemsSC`, `MobRecipesSC`, `TrophyTableSC`, `SpawnRulesSC`, `client/MobClientSC`. E — `dim/SubstrateEntrySC`, `dim/TeleportQueueSC`, `dim/EntryRulesSC`. D — `debug/WorldTestSubstrateSC`.

Правки ядра в общих файлах (готово, остальным их не трогать):
- `SCMod`:
  - зависимость `after:GalacticraftCore`;
  - в preInit: `SubstrateNetSC.init()`, регистрация `SubstrateEntrySC.INSTANCE` на шинах Forge и FML, `TeleportQueueSC.INSTANCE` на FML, `MobEventsSC.INSTANCE` на Forge и FML, `SpawnDirectorSC.INSTANCE` на FML;
  - в `registerRecipes()`: `SubstrateRecipesSC.init()` и `MobRecipesSC.init()`;
  - в init: `SubstrateSC.init()` и `MobEntitiesSC.init()` после `registerWorldGenerator(OreGenSC)`;
  - новый `@Mod.EventHandler loadComplete(FMLLoadCompleteEvent)` → `SubstrateSC.loadComplete()`;
  - в `serverStopped`: `TeleportQueueSC.clear()`, `SubstrateEntrySC.clearAll()`, `SpawnDirectorSC.clearAll()`;
  - в `serverStarting` при `-Dsc.worldtest`: `WorldTestSubstrateSC` на шине FML.
- `ConfigSC.load`: в конце try — `com.sc.dim.SubstrateConfigSC.load(config)`.
- `ModBlocks.init`: перед `registerOreDict()` — `com.sc.dim.SubstrateBlocksSC.init()`.
- `ModItems.init`: в конце — `com.sc.mob.MobItemsSC.init()`.
- `ClientProxy.preInit`: после `CarryTooltipSC.register()` — `SubstrateClientSC.register()`, `MobClientSC.register()`, `SubstrateIdsClientSC.register()`.
- `docs/plan-pe1.md` §4: пометка о решении `siphonMobs`.

Пока пакеты не готовы, ядро уже регистрирует измерение. Плоская заглушка-мир, мост видит «Si-14 Substrate», мобы регистрируются без рендера.

## 1. Регистрация и порядок

| Этап | Что | Где |
|---|---|---|
| preInit | `ConfigSC.load` → `SubstrateConfigSC.load` | ядро |
| preInit | Блоки: `ModBlocks.init` → `SubstrateBlocksSC.init`. Предметы: `ModItems.init` → `MobItemsSC.init` | W, M |
| preInit | Канал `SiliconAgeDim`, обработчики событий E и M | ядро |
| preInit, клиент | `ClientProxy`: рендеры мобов, клиент мира, предупреждение id | M, W, ядро |
| init (обе стороны) | `SubstrateSC.init()`: (1) `enabled`; (2) `dimensionRefusal` — не 0/1/−1, не положительный, не зарегистрирован; (3) `registerProviderType(providerId, WorldProviderSubstrateSC.class, keepLoaded)` — false → выкл.; (4) id биомов через `pickBiomeId` (сдвиг → запись в конфиг с WARN; нет свободных → `unregisterProviderType`, выкл.); (5) `registerDimension` в try (IllegalArgumentException → `unregisterProviderType`, выкл.); (6) `new BiomeSubstrateSC(id, kind)` ×2 и явные типы BiomeDictionary; (7) лог «dimension …, provider …, biomes …»; (8) `SubstrateCompatSC.sendImc(dim)` | ядро + W |
| init | `MobEntitiesSC.init()`: `registerModEntity` (пул SiliconAge: 0 lineman, 1 marauder) | M |
| init | Рецепты: `SubstrateRecipesSC`, `MobRecipesSC` (из `registerRecipes`, то же и в пути самотеста preInit) | W, M |
| loadComplete | `SubstrateSC.loadComplete()`: GalaxyRegistry по рефлексии, слоты биомов всё ещё наши, тип провайдера наш (WARN/SEVERE), `SubstrateCompatSC.loadComplete(dim)` — AE2 | ядро + W |
| вход игрока | `SubstrateNetSC`: id и правила клиенту | ядро |

Любой отказ — одна строка `[Silicon Age] Si-14 Substrate is OFF: …` уровня SEVERE. Мира нет, блоки и предметы остаются. Самотест (`-Dsc.selftest`) идёт в preInit, до init. Поэтому регистрацию проверяет мировой тест (§8.2), а самотест — только чистые функции.

## 2. Конфиг-холдер

### 2.1 Почему не ConfigSC
`ConfigSyncSC.syncedFields()` берёт все `public static` нефинальные примитивы **ConfigSC.class** и после входа перезаписывает их значениями сервера. Id измерения и биомов, попав туда, сменились бы у клиента уже после того, как он зарегистрировал мир со своими. Поэтому:
- id живут в `SubstrateConfigSC` как **private static** с геттерами. ConfigSyncSC их не видит: класс другой, поля не публичные. Записать их может только `load` и `writeBiomeId`;
- правила (`strictEntry`, `respawnDimension`, `bridgeCostMultiplier`, `nativesCrossBridge`, `empMobTargets`, `siphonMobs`, `gearDropMultiplier`) — `public static`. Их передаёт свой `SubstrateNetSC` тем же способом, что ConfigSyncSC: по имени, через рефлексию. Новое правило — просто новое `public static` поле. После применения и при восстановлении сбрасывается кэш справочника (`BookContent.invalidate`). `ConfigSC.stamp()` правила холдера не видит;
- самотест (D) проверяет: в `SubstrateConfigSC` нет `public` полей-id; `SubstrateNetSC.ruleFields()` — ровно 7 правил Ф1; в `ConfigSC` нет полей с именами id.

### 2.2 Сверка id клиента и сервера (LAN, сервер)
В 1.7.10 Forge 1614 сервер **не** присылает клиенту регистрацию измерений: `DimensionMessageHandler` есть, но его никто не вызывает. WorldClient строит провайдер через `DimensionManager.createProviderFor(dim)`, и на клиенте это измерение должно быть зарегистрировано. Отсюда:
- при `PlayerLoggedInEvent` сервер шлёт `active, dim, provider, biomePlateaus, biomeCanyons` и правила;
- клиент на удалённом сервере (`proxy.playsOnRemoteServer()`) применяет правила и сравнивает id. Если id измерения сервера клиенту неизвестен, а свой провайдер у клиента есть, — `registerDimension(serverDim, ourProvider)` на сессию: вход не падает. Любое расхождение — WARN в лог и строка в чате;
- остаётся одно ограничение: игрок, который вышел **внутри** Подложки и заходит на сервер с другим id, получит `S01PacketJoinGame` раньше нашего пакета. Поэтому README требует одинаковых id;
- одиночная игра и хост LAN делят поля со своим сервером — там ничего не меняется.

### 2.3 Ключи Ф1
Таблица — `docs/plan-substrate.md` §8. Раздел `dimension` с `setCategoryRequiresMcRestart`. Комментарии ru/en, в комментарии раздела — «id менять только до первого входа». Ключи следующих фаз заранее не добавлять.

## 3. Мир — пакет W

### 3.1 Провайдер `WorldProviderSubstrateSC` (уже есть, W дописывает)
- Фиксировано: наследник `WorldProvider`, не `WorldProviderSurface`; без интерфейсов GC; лёгкий конструктор; `NAME` = «Si-14 Substrate», `NAME_KEY` = `sc.dim.substrate.name`; `canRespawnHere` = false; `getRespawnDimension` → `SubstrateSC.respawnDimension()`; `isSurfaceWorld` = true; погода всегда 0 (`updateWeather`, `calculateInitialWeather`, `canDoLightning`, `canDoRainSnowIce`, `canBlockFreeze`, `canSnowAt`).
- W: цвета (все `@SideOnly(Side.CLIENT)`): `getSkyColor(Entity, float)` — днём ≈ #A8C9C6 × яркость по углу солнца, ночью к ≈ #0B1430; `getFogColor(float, float)` ≈ #C9DEDC днём; `calcSunriseSunsetColors` — медно-фиолетовые (≈ 0,85 / 0,45 / 0,55) в окне шире ванильного (±0,55 вместо ±0,4); `getStarBrightness` ×1,5 (не больше 1); `getCloudHeight` = 200; `isSkyColored` = true. Своего рендера неба нет.
- W: `getSpawnPoint()` / `getRandomizedSpawnPoint()` — центр плато (128, `SubstrateTerrainSC.surfaceY(seed, 128, 128) + 1`, 128): DerivedWorldInfo иначе отдаёт координаты спавна Overworld. `canCoordinateBeSpawn` — дёрн или кристаллит сверху. `getWorldTime` и `calculateCelestialAngle` — ванильные (сутки 20 мин).

### 3.2 Биомы и карта биомов
- `BiomeSubstrateSC(id, kind)`, `PLATEAUS` / `CANYONS`, `dictionaryTypes(kind)` — фиксированы: WASTELAND, DRY, SPARSE (W может уточнить, но без типов, которые зовут чужие декораторы). W дописывает `setColor`, `waterColorMultiplier` (бирюза ≈ #3FD3C8), цвета травы/листвы (не используются), `topBlock` / `fillerBlock` = порода (дёрн / кристаллит) с метами `field_150604_aj` / `field_76754_C`. Спавн-листы пусты (уже).
- `WorldChunkManagerSubstrateSC(World)` — конструктор фиксирован. W заменяет заглушку на сетку: `extends WorldChunkManager` (защищённый конструктор без аргументов), биом колонки — `SubstrateTerrainSC.biomeKind(x, z)`. Переопределить все методы, которые в базовом классе читают `genBiomes` / `biomeIndexLayer`: `getBiomeGenAt(int, int)`, `getBiomesForGeneration`, `loadBlockGeneratorData`, `getBiomeGenAt(BiomeGenBase[], …, boolean)`, `getRainfall` (нули), `getTemperatureAtHeight`, `areBiomesViable`, `findBiomePosition`, `getBiomesToSpawnIn`, `cleanupCache`. Образец — `WorldChunkManagerHell`.

### 3.3 Генерация `ChunkProviderSubstrateSC`
- Рельеф — в `provideChunk`, в `Block[65536]` и `byte[65536]`, индекс `(x * 16 + z) * 256 + y`. Потом `new Chunk(world, blocks, metas, cx, cz)`, биомы в `chunk.getBiomeArray()` по `biomeKind`, `generateSkylightMap()`. Ни одного `Blocks.stone` (заглушка ставит stone, пока нет породы, — убрать).
- `SubstrateTerrainSC` (чистые функции, сигнатуры фиксированы; самотест D их вызывает): `distanceToLine`, `inCanyonBelt`, `biomeKind`, `canyonHalfWidth(seed, line)` (4…7 → ширина 9–15), `surfaceY(seed, x, z)`. Плато — террасы Y 64–74 со ступенью 2 из `NoiseGeneratorPerlin` с сидом мира. Каньон — вдоль линии `x ≡ 0 (mod 256)` и `z ≡ 0`, дно на 30–50 ниже соседнего плато; стены полосами оксида по Y (4 цвета, полоса 3 блока); на пересечении линий — перекрёсток. Слои: 0 бедрок, 1–20 основание, 20–40 металлизация, 40–60 кристаллит, 60+ окисная корка 2–6 блоков, сверху на плато Кварцевый дёрн.
- Пруды: редкие мелкие (1–2) бирюзовые пруды на террасах, целиком внутри чанка (с отступом 1), вода прямо в массиве — источники `flowing_water` (мета 0), а не `water`: водные ульи ExtraBees (IWorldGenerator без проверки измерения) ставятся вместо `Blocks.water` над камнем; замкнутый пруд от первого случайного такта становится стоячей водой.
- Карвер `MapGenSubstrateCavesSC extends MapGenBase` (новый): переопределить `func_151538_a(World, int, int, int, int, Block[])`, вырезать только свою породу и руду (кроме бедрока и воды). Пещеры редкие. «Сквозные колодцы» — вертикальные шахты диаметром 3–5 от поверхности до Y 20, примерно 1 на 64 чанка, не в каньонах. Вызов — `carver.func_151539_a(this, world, cx, cz, blocks)` в `provideChunk`.
- `populate(provider, cx, cz)`: окно `x = cx*16 + 8 … +16`, без каскада (`BlockFalling.fallInstantly` не нужен). Порядок: руды (§3.5) → Медные трассы (прямые отрезки 8–40 по X или Z на поверхности и в стенах каньонов, примерно 1 на 2 чанка; узлы со светом через каждые ~12 блоков) → друзы (столбики 3–9, примерно 1 на 6 чанков). Светящиеся блоки — только через `setBlock`, остальное тоже через `setBlock`, но немного. Свет `setBlock` считает, только если загружены все чанки в 17 блоках (на краю генерации — нет), а поздние ванильные пересчёты берут `getLightValue()` без меты (у породы 0) — поэтому там, где `setBlock` свет пропустил, `glow()` разносит его сам по загруженным чанкам. `GameRegistry.generateWorld` не звать: это делает `ChunkProviderServer`.
- `getPossibleCreatures` — пустой список (уже), `func_147416_a` — null, `recreateStructures` — пусто (структуры с Ф2).

### 3.4 Блоки (`SubstrateBlocksSC.init`, реестр `substrateRock`, `substrateOre`, `crystalDruse`)
- `block/BlockSubstrateRockSC` + `ItemBlockSubstrateRockSC`: меты 0–7 (константы в `SubstrateBlocksSC`, фиксированы), `Material.rock`, кирка уровня 1, твёрдость 2,0, стойкость 10. Иконки по мете; у дёрна верх и бок отдельно. Медь: `getLightValue(IBlockAccess, x, y, z)` = 6 только для `TRACE_NODE`. Обязательно `isReplaceableOreGen(world, x, y, z, target)` → `this == target && meta == CRYSTALLITE`: ванильный WorldGenMinable мету не проверяет и иначе заменил бы оксид, дёрн и трассы. `getDrops`: дёрн → кристаллит; трассы → `ModItems.crushedOre.stackOf(Material.COPPER)` 1–2 плюс удача; остальное → само. `canSilkHarvest` — да. Подсказка TooltipSC (кратко + Shift/Ctrl) по мете.
- `block/BlockSubstrateOreSC` + `ItemBlockSubstrateOreSC`: 16 мет = OreEntry, уровень кирки = `OreEntry.tool.level`, твёрдость 3,0. `getDrops` → `new ItemStack(ModBlocks.oreSC, 1, meta)`: все цепочки, NEI, фильтры и карьер работают без изменений. `canSilkHarvest` = false: Шёлковое касание тоже даёт обычную руду. OreDictionary — те же `ore<Name>` (их отдаёт и `oreSC`). Имя — `sc.dim.ore.name` с именем руды. Иконка — фон кристаллита и узор руды. Подсказка: «руда Подложки, ломается в обычную руду».
- `block/BlockCrystalDruseSC`: отдельный блок, `isOpaqueCube` false, `renderAsNormalBlock` false, свет 10, звук стекла, твёрдость 1,0. Дроп — Кремнезёмный песок (`ModItems.siliconMaterial`, `SILICA_SAND`) 2–4, с Шёлковым касанием — сам. Подсказка.
- `SubstrateRecipesSC.init()`: `GameRegistry.addSmelting(кристаллит, кремнезёмный песок, 0.1F)`. Других рецептов в Ф1 нет. Только ванильные предметы и предметы мода.
- Текстуры — `textures/blocks/substrate_*.png`, 16×16, через новый скрипт `tools/gen_substrate_tex.py`, в стиле существующих (`tools/gen_*_tex.py`). Руды — фон кристаллита и узор, вынутый из `ore_<name>.png` (пиксели, отличные от палитры камня).

### 3.5 Руды Подложки
- `SubstrateOreTableSC` (чистый, сигнатуры фиксированы): `veinMultiplier(OreEntry)` — в заглушке уже числа плана; `minY` / `maxY` по слоям: редкие (танталит, сперрилит, бадделеит, аргиродит, вольфрамит, монацит) — 20–40 и немного 5–20; кварцит, сподумен, ильменит — 1–20; остальные — 10–55. TODO(balance).
- Генерация в `populate`: жил на чанк = `round(ConfigSC.settingsFor(ore).veinsPerChunk × veinMultiplier)` (дробная часть — шансом), размер — `settingsFor(ore).veinSize`. `WorldGenMinable(SubstrateBlocksSC.ore, meta, size, SubstrateBlocksSC.rock)` — с `isReplaceableOreGen` по мете (см. выше). `OreGenSC` и `ConfigSC.oreDimensions` не трогать.

### 3.6 Клиент
`dim/client/SubstrateClientSC.register()` — в Ф1 только если цветов провайдера не хватит (туман `EntityViewRenderEvent.FogColors`). Плотность тумана и Разлом — Ф4.

### 3.7 IMC и совместимость (`SubstrateCompatSC`)
- `sendImc(dim)` (init): `FMLInterModComms.sendMessage("Thaumcraft", "dimensionBlacklist", dim + ":0")`.
- `loadComplete(dim)`: при `Loader.isModLoaded("appliedenergistics2")` — по рефлексии `appeng.api.AEApi.instance().registries().worldgen().disableWorldGenForDimension(WorldGenType.X, WorldProviderSubstrateSC.class)` для `CertusQuartz`, `ChargedCertusQuartz`, `Meteorites`. Любая ошибка — одна строка FINE/WARN, без краша. IMC `blacklist-block-spatial` — только в Ф2 (Ядро и витринные блоки).
- В README-черновик (D): GC — ракеты, «Dimensions where rockets cannot launch».

## 4. Броня мобов и ПЭ-1 по мобам — пакет A

### 4.1 Модель
- `EntityArmoredMobSC extends EntityMob implements IArmoredMobSC, IEmpTargetSC, ISubstrateNativeSC` — уже есть. В нём: `setEquipmentDropChance(0..4, 0)`; `entityInit` → `MobArmorSC.addWatchers`; `damageEntity` → щит, потом super; `applyArmorCalculations` → `MobArmorSC.applyArmor`; `getTotalArmorValue` → `points()`; `onLivingUpdate` → `serverTick()`; NBT `scMobArmor`; все методы `IEmpTargetSC` делегируют в `MobArmorSC`. A может менять тела, добавлять protected-хелперы и переопределения (например, искры «села» на клиенте). Сигнатуры и порядок вызовов не менять. M наследует и обязан звать super.
- Почему `damageEntity`: в `attackEntityFrom` ванильные кадры неуязвимости уже срезали удар до реально прошедшего (`amount − lastDamage`), поэтому серия ударов не спишет запас дважды. Цепочка 1.7.10: `attackEntityFrom` → `damageEntity` → `ForgeHooks.onLivingHurt` → `applyArmorCalculations` → `applyPotionDamageCalculations` → поглощение → HP. Щит стоит до `LivingHurtEvent`. Если щит взял весь удар — `return` без super: HP целы, но вспышка, отдача и звук удара остаются от `attackEntityFrom`.
- `MobArmorSC` (поля и сигнатуры фиксированы, тела A): `reserve`, `reserveMax`, `helium`, `emergency`, `elite`, `regenPerSecond`, `regenDelayTicks`, `defenseTier`, `suppressedUntil[4]`, `immuneUntil[4]`, `streamUntil`, `lastDamageAt`, `streamDrawnEu`. Методы: `spawnReserve(min, max)`, `recharge(units)`, `down()`, `suit()`, статические `piece`, `modPiece`, `pieceSuit` (armorType → слот `4 − armorType`), `shield`, `applyArmor`, `points`, `serverTick`, `sync`, `suppressed`, `empHit`, `drainEu`, `streamSecond`, клиентские `clientCharge`, `clientFlag`, `clientSuppressed`, `clientImmune`, `write` / `read`.
- DataWatcher (фиксировано): 20 — запас ×10 (int), 21 — запас-максимум ×10 (int), 22 — флаги (byte: 1 щит, 2 аварийный, 4 Сифон тянет, 8 элита, 16 «села»), 23 — ЭМИ (byte: биты 0–3 подавлено, 4–7 невосприимчивость). 24–31 — за классами мобов (M). Обновлять только при изменении: DataWatcher и так шлёт только изменения.
- `MobArmorMathSC` (чистый; самотест D): `absorbShare`, `absorb(amount, points, reserve)` → {в HP, из запаса}, `shield(amount, reserve)` → {дальше, из запаса}, `ironPoints(armorType)`, `hasEmergencyMode(suit)`, `shieldUp(...)`, `unitsToEu` / `euToUnits`, константы `FULL_POINTS = 25`, `MOB_SIPHON_STREAM_CAP = 50000`, `TRACE_RECHARGE_PER_SECOND = 1`.

### 4.2 Правила (ПБ-2…ПБ-7)
- `applyArmor`: если `src.isUnblockable()` или `down()` — урон без изменений. Иначе `absorb(amount, points(), reserve)`, разница — из запаса.
- `points()`: сумма `damageReduceAmount` надетых частей мода. Подавленная часть, а при `emergency` (только если `hasEmergencyMode(suit)`) — все части дают `ironPoints`. Чужая броня — свой `damageReduceAmount`. При `down()` — 0.
- `shield`: при `shieldUp(...)` берёт любой урон, кроме `outOfWorld` и `RadiationSC`-источника: `shield(amount, reserve)`, остаток идёт дальше. В Ф1 щита ни у кого нет, но механика нужна целиком (тесты A и D).
- «Села»: переход через 0 — один раз `power.off` (`SoundsSC` или `worldObj.playSoundAtEntity` с `siliconage:power.off`) и искры (`WorldServer.func_147487_a`, «fireworksSpark»), флаг 16. Новых звуков A не добавляет (`sounds.json` у M).
- `serverTick`: подзарядка `regenPerSecond` после `regenDelayTicks` без урона и не во время потока Сифона (`streamUntil`). Конец подавления и невосприимчивости. `sync()` — раз в 10 тиков или по изменению.
- `recharge(units)` — для AI M (трасса): не во время потока; «села» гаснет, когда запас снова больше 0.

### 4.3 ПЭ-1 по мобам (ПБ-10…ПБ-13): точные места в коде
Путь «цель — EntityPlayer» во всех файлах остаётся **как есть**. Ветка мобов добавляется отдельной: после игрока, перед старым `applyNoArmour`, и только при включённом флаге.
- `EmpGunLogicSC.fire()`: заменить `else if (target instanceof EntityLivingBase) { applyNoArmour; say noarmour }` на:
  - `else if (SubstrateConfigSC.empMobTargets && target instanceof IEmpTargetSC && ((IEmpTargetSC) target).empArmoured())`:
    - Импульс по `INpc` или питомцу (`IEntityOwnable` с владельцем) → отказ `sc.emp.refuse.<NO_OWN>` без EU. Проверить до `pay()`, рядом с `refusal`;
    - слоты Точки — `EmpMathSC.pointPieces(zone, heightShare)` по боксу моба, как у игрока; Импульс — все 4;
    - `HitResult r = t.empHit(p, mode, upgraded, pieces)`; эффекты `FX_SUPPRESSED`, звук `emp.suppress` у цели;
    - отчёт стрелку `reportMob` с ключами `sc.emp.hit.*`, как у `report`.
  - Иначе старый `applyNoArmour`.
- `MobArmorSC.empHit`: для каждого слота с частью мода. Невосприимчив — пропустить (`r.immune`). Иначе `EmpMathSC.durationTicks(pieceSuit, mode, upgraded, defenseTier, helium, ConfigSC.empDurationMultiplier)`, `suppressedUntil = max(…, now + t)`, `immuneUntil = until + immunityTicks(ConfigSC.empImmunitySeconds)`. Нагрев и выкипание у мобов в Ф1 не моделируются. Подавленный нагрудник Экзо/Сингулярной гасит щит («Сердце комплекта»): `shieldUp` смотрит на `suppressed(1)`. `sync()`.
- `aimScan()`: для `IEmpTargetSC` цели — `readTargetMob`. Заполнить `m.suit` (`empSuit`), биты слотов, `suppressedTicks` / `immuneTicks`, `chargePct` (при Сенсорном чипе стрелка: `empChargeEu / empCapacityEu`). `markAimed` — только игрокам, как сейчас.
- `EmpSiphonSC`: сейчас всё типизировано под `EntityPlayer`. Вводится внутренний адаптер цели потока: игрок — существующие вызовы без изменений, моб — `IEmpTargetSC`. Внешний вид кода игрока сохранить; ветка выбирается по `instanceof EntityPlayer`.
  - `targetOf`: игрок как сейчас, иначе при `SubstrateConfigSC.siphonMobs && e instanceof IEmpTargetSC && empArmoured()` — моб;
  - скорость `EmpMathSC.siphonRate(empCapacityEu, empSuit, wide, empDefenseTier, ConfigSC.empSiphonRateMultiplier, STEP)`, потери `siphonLossPct(efficient, любой слот подавлен, ConfigSC.empSiphonLoss)`;
  - снятие `empDrainEu`. Доставка в приёмники — не больше `MOB_SIPHON_STREAM_CAP` за поток (`streamDrawnEu`), сверх — в искры (`FX_VENTED`), моб продолжает терять запас;
  - раз в 20 тиков `empStreamSecond(shooter)` вместо `EmpSC.streamSecond`. Поток рвётся, если запас моба 0, — как «броня цели пуста». «Страж» (`guardReversesSiphon`) — только для игроков.
- `EmpRulesSC.refusal` / `screenAbsorbs` уже общие (`Entity target`) — не трогать, кроме случая, когда A понадобится режим для фильтра INpc. Тогда — новая перегрузка, старую сигнатуру оставить.
- `EmpMathSC`: только новые константы (если нужно). Существующие числа не менять.
- Регресс (ПБ-13): `WorldTestEmpSC` (игроки) проходит без изменений. Флаги выкл. — поведение по мобам ровно старое. Тесты мобов — у D (§8).

### 4.4 «Казнь» (ПБ-8)
`BladeLogicSC.hit`: в условие «Казни» добавить `&& !(living instanceof com.sc.mob.IExecuteImmuneSC)` рядом с `IBossDisplayData`. Больше в `BladeLogicSC` ничего не менять. Аннигиляция Сингулярной брони (`ArmorLogicSC`) и умения Сингулярного клинка (`BladeSingularSC`) — Ф3, отдельной правкой.

### 4.5 Свечение (ПБ-14), `ModelArmorGlowSC`
- Заряд: для `entity instanceof IArmoredMobSC` — `MobArmorSC.clientCharge(entity)` вместо `ItemArmorSC.chargeOf(stack) / capacityOf`. «Села» — `clientFlag(FLAG_DOWN)` (как «пусто»: медленная красная пульсация или погасшие огни — на выбор A). Подавлено — `clientSuppressed(entity, armorType)` (сейчас только `wearer != null`). Сифон — `clientFlag(FLAG_DRAINED)`.
- Цвет — по-прежнему `ItemArmorSC.glowColor(stack)`: M ставит его стекам при спавне (Обходчик 3 — #FFE040, Мародёр 2 — #FF9A30). Частые изменения NBT стеков запрещены.
- Дальше 32 блоков от камеры у мобов второй проход свечения не рисуется.
- `functionsOn()` для мобов — всегда true: у них нет меню K.

## 5. Существа — пакет M

### 5.1 Регистрация (`MobEntitiesSC`, уже работает)
`registerModEntity(EntityLinemanSC.class, "lineman", 0, SCMod.instance, 80, 3, true)`, `EntityMarauderSC` — 1. Имена сущностей — «SiliconAge.lineman», lang — `entity.SiliconAge.lineman.name`. Новые мобы — только в конец пула.

### 5.2 Обходчик (`EntityLinemanSC`)
- Атрибуты: HP 20, скорость 0,25, follow 32, attackDamage 3 (кулак 3–4: +rand).
- Спавн (`onSpawnWithEgg`): 1–3 случайные Нано-части (`ModItems.ARMOR.get(NANO)`), каждой `ItemArmorSC.setGlowColor(stack, 3)`; запас `mobArmor().spawnReserveForDamage(20, 30)` — запас = урон × доля, которую гасят надетые части (`MobArmorMathSC.reserveFor`): броня садится от 20–30 ед. блокируемого урона при любом числе частей (2–3 выстрела Плазменной винтовки, 4–5 стрел; меньше частей — меньше запас, ПБ-5), `helium = false`, `emergency = false` (ПБ-5); 5% — `ModItems.WEAPONS.get(ION_CUTTER)` в руке.
- AI (`isAIEnabled` = true): `EntityAISwimming`, `EntityAIRechargeAtTraceSC` (новый, общий для M: при `down()` найти трассу в 16 блоках по `SubstrateBlocksSC.isTrace`, дойти, присесть (`setSneaking`), `recharge(TRACE_RECHARGE_PER_SECOND)` раз в 20 тиков до полного), `EntityAIBeamAttackSC` (новый: луч при Ионном резаке — один выстрел 4 раз в 40 тиков, последние 20 из них — видимый прицел с частицами; только при `canEntityBeSeen`; урон `DamageSource.causeMobDamage(this)`), `EntityAIAttackOnCollide` (кулак), `EntityAIWander`, `EntityAIWatchClosest`, `EntityAIHurtByTarget`, `EntityAINearestAttackableTarget(EntityPlayer)`. Ходит парами: группа спавнится рядом (§5.5).
- `attackEntityAsMob`: свой урон с потолком (ПС-4). Атрибут-модификаторы предмета в руке не прибавляются: клинок мода добавил бы 12–40.

### 5.3 Мародёр (`EntityMarauderSC`)
- Атрибуты: HP 24, скорость 0,28, attackDamage 4.
- Спавн: 2–4 Нано-части с `glowColor 2`; запас `spawnReserveForDamage(20, 40)` (как у Обходчика: садится от 20–40 ед. урона); в руке Нано-клинок 50% (урон 6), Ионный резак 30% (луч 4), иначе дубинка (`Items.stick`, 4).
- Мешок: 9 ячеек в NBT (`scBag`). `EntityAICollectLootSC` (новый) подбирает `EntityItem` в радиусе 8 (не младше 40 тиков и не закреплённые за игроком). Надетое не меняет, `setCanPickUpLoot(false)`. `canDespawn` = false, пока мешок не пуст. При смерти мешок выпадает целиком всегда (ПС-5) — в `dropFewItems` или в `LivingDropsEvent`.
- С пустым запасом — та же задача подзарядки у трассы. Голоса — в Ф1 не нужны (можно редкие реплики в чат игрокам в 16 блоках — ключи `sc.mob.marauder.say.0..3`, по желанию).

### 5.4 Урон мобов (ПС-4)
Константы в классах: кулак 3–4, дубинка 4, Нано-клинок 6, Ионный резак 4 (луч), позже Квант 8, Экзо 10–11, Сингулярный 13–14. Свой `attackEntityAsMob` без `EnchantmentHelper` по предмету в руке. Отдача — как у зомби.

### 5.5 SpawnDirector (`SpawnDirectorSC`, ПС-1, ПС-2)
- `TickEvent.PlayerTickEvent` (сервер, END) для игроков в `SubstrateSC.is(world)`. Не креатив; сложность не Peaceful. Раз в `SpawnRulesSC.PERIOD` тиков на игрока (счётчик по UUID — в карте, очищается в `clearAll`).
- Счёт: местные враждебные (`ISubstrateNativeSC && IMob`) в боксе ±64. Счёт по миру — один раз за тик мира по `loadedEntityList`, с кэшем на тик. `SpawnRulesSC.maySpawn(near, inWorld, group, peaceful)`.
- Выбор: биом колонки → веса (`PLATEAU_WEIGHTS` / `CANYON_WEIGHTS`), Обходчики — пара, Мародёры — 2–4. Точка — случайный угол на 24–48 от игрока, `blockExists`, верх по heightmap, твёрдый блок под ногами, 2 воздуха, не вода и не лава, не ближе 24 к любому игроку. Сущность: `setLocationAndAngles`, `onSpawnWithEgg(null)`, `BornInSubstrateSC.mark(e)`, `spawnEntityInWorld`.
- Свет не важен (мир «технический»): `getCanSpawnHere` не проверяется. Деспавн ванильный (ПС-3).

### 5.6 События (`MobEventsSC`)
- `EntityJoinWorldEvent`: сущность с флагом в мире не-Подложки → `BornInSubstrateSC.clear` (ПТ-3).
- `LivingHurtEvent`: для `EntityArmoredMobSC` запомнить урон по игроку. В карте моба по UUID — только настоящие `EntityPlayerMP`, не `FakePlayer`; источник `getEntity()`, стрелы — через `shootingEntity`. Карта transient, в NBT не пишется.
- `EntityInteractEvent`: предмет в руке — банка душ EnderIO (имя класса `crazypants.enderio.item.ItemSoulVessel`, проверка по строке, без зависимости), цель — `ISubstrateNativeSC` → отменить.
- `LivingDropsEvent` → `MobDropsSC.onDrops(event)` (§5.7).
- Снятие владельца трофея через 300 тиков — список EntityItem с временем, проверка в `WorldTickEvent`.

### 5.7 Предметы, трофеи и добыча
- `MobItemsSC.init()` (preInit), реестр фиксирован: `trophySC` → `ItemTrophySC`, `combatLoot` → `ItemCombatLootSC`, `spawnEggSC` → `ItemSpawnEggSC`. Все в `ModCreativeTab.TAB`, у всех TooltipSC: кратко, Shift — подробно, Ctrl — как получить и что делать.
- `ItemTrophySC`: меты по `TrophyTableSC` (0–15 броня, 16–19 клинки, 20–22 оружие, 23 ПЭ-1, 24 ПЭ-1М — фиксированы), stack 1, редкость uncommon.
  - иконка — иконка настоящего предмета (`ModItems.ARMOR`, `BLADES`, `WEAPONS`, `empGun`) плюс второй проход `trophy_overlay.png` (царапины, красноватый налёт);
  - имя — `item.siliconage.trophy.<key>.name` — у каждой меты своё, ради рода: «Трофейные Нано-поножи», «Трофейная Плазменная винтовка»;
  - `getSubItems` — только меты, которые уже выпадают: в Ф1 0–3, 16, 20 (ПТ-7);
  - подсказка: «нерабочий трофей с …», Ctrl — «восстановить можно будет у Оружейника поселения или на Станции обслуживания брони».
- `ItemCombatLootSC`: `LootKind` (порядок только дописывается): `SHELL_FIRMWARE` (`shellFirmware`), `NANO_SCRAP` (`nanoScrap`). Иконки — `textures/items/loot_*.png`.
- `ItemSpawnEggSC`: мета = id пула, ванильные иконки `spawn_egg` и `spawn_egg_overlay` с цветами (Обходчик #8A9BA8 / #FFE040, Мародёр #5A4A3A / #FF9A30). `onItemUse` → `EntityList.createEntityByName("SiliconAge." + name, world)`, `onSpawnWithEgg`, **без** флага. Имя — «Яйцо призыва: %s».
- `MobDropsSC.onDrops(LivingDropsEvent)` для `EntityArmoredMobSC`:
  1. Мешок Мародёра выпадает всегда;
  2. условия ПТ-1 — флаг, мир Подложки (`SubstrateSC.is`), убийца. Убийца — игрок с наибольшим уроном и долей не меньше 25%, онлайн, `EntityPlayerMP`, не FakePlayer. Нет такого — только опыт;
  3. фракционная добыча: Обходчик — 1 Прошивка + 0–2 Транзистора (`ModItems.siliconMaterial`, `TRANSISTOR`), Мародёр — Лом Нано 40%;
  4. трофей: категории по надетому и предмету в руке (`TrophyTableSC.PIECE / BLADE / WEAPON` по костюму), `chance(сумма, looting(killer), elite, false, SubstrateConfigSC.gearDropMultiplier, badLuckFactor)`; один бросок; выпал → выбор категории пропорционально шансам, часть — случайная из надетых;
  5. невезение (ПТ-5): счётчик в `EntityPlayer.PERSISTED_NBT_TAG` → `scTrophyDry` (int[4] по уровням); трофей уровня — сброс, иначе +1;
  6. трофей — `EntityItem` с владельцем `func_145797_a(killer name)` на 15 с (§5.6).
- `MobRecipesSC.init()`: применение добычи до Скупщика. Дробилка: 4 Прошивки оболочки → 1 медная пыль и Транзистор с шансом 20%; 4 Лома брони Нано → 1 титановый концентрат. (Сделано так вместо крошечной пыли из первой версии плана: у меди и титана в моде нет крошечной пыли, `Material.hasTinyDust` = false; ценность одной штуки та же — около 2,25 крошечной пыли.) Около 10% стоимости материалов, TODO(balance). Проверить пересечения с `-Dsc.packcheck`.

### 5.8 Рендер (`mob/client`)
- `MobClientSC.register()`: `RenderingRegistry.registerEntityRenderingHandler(EntityLinemanSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, "lineman"))`, так же Мародёр.
- `RenderSubstrateMobSC extends RenderBiped`: текстура `textures/entity/<name>.png` (раскладка 64×32, нарисована в 128×64, как HD-броня мода). Броня мода рисуется сама через `RenderBiped.shouldRenderPass` → `ForgeHooksClient.getArmorModel` → `ItemArmorSC.getArmorModel` → `ModelArmorGlowSC` (работает с любым EntityLivingBase). Предмет в руке — ванильный путь.
- Обходчик — «пустой корпус»: в скине почти всё прозрачно (альфа-тест у RendererLivingEntity есть), тёмная «внутренность» видна в прорехах, один жёлтый диод. Мародёр — худой, грязно-оранжевый комбинезон, аккумулятор на изоленте.
- Текстуры — новый скрипт `tools/gen_substrate_mobs_tex.py`; иконки добычи и `trophy_overlay` — там же.

### 5.9 Звуки
В Ф1 — ванильные (`mob.irongolem.walk`, `random.fizz` и т. п.) и существующие в моде (`power.off`). `sounds.json` принадлежит M: если M добавит свои звуки — только дописывая в конец.

## 6. Вход и мост — пакет E

### 6.1 API (фиксировано, `SubstrateEntrySC`)
`allowsArrival(EntityPlayer, String via)`, `grantArrival(EntityPlayer, String via)`, `mayCross(Entity, int toDim)`, `clearAll()`, константы `VIA_BRIDGE` = «bridge», `VIA_JUMP` = «sva.jump». Чистые решения — `EntryRulesSC.lawful / costMultiplier / mayCross` и константы `GRANT_TICKS`, `BUFFER_TICKS`, `PORTAL_CHECK`, `PORTAL_HOLD`.

### 6.2 strictEntry (ПВ-2, ПВ-3)
- Пропуск: `grantArrival` пишет в серверную карту по UUID `(via, worldTime + GRANT_TICKS)`. Сам `BridgeTeleportSC.teleport`, ветка `EntityPlayerMP`: если `SubstrateSC.isDim(dim)` и игрок не в Подложке — `SubstrateEntrySC.grantArrival(p, VIA_BRIDGE)` перед `transferPlayerToDimension`. Через этот же метод аддон Vehicles переносит седоков при Прыжке, поэтому Прыжок уже законен. Аддон не трогаем.
- `PlayerEvent.PlayerChangedDimensionEvent` (шина FML; срабатывает внутри `transferPlayerToDimension`): если `SubstrateSC.isDim(toDim)` и игрок — настоящий `EntityPlayerMP` → `EntryRulesSC.lawful(strictEntry, пропуск есть и не просрочен, creative, op)`. Op — `MinecraftServer.getConfigurationManager().func_152596_g(profile)`. Пропуск гасится.
  - Законно → `onArrival` (§6.5);
  - незаконно → `TeleportQueueSC.queue(p, fromDim, x, y, z, yaw, "sc.dim.entry.refused")`. Точка — последняя безопасная в `fromDim`: её пишет PlayerTick раз в 40 тиков вне Подложки, если игрок на земле, не в жидкости и не едет верхом. Иначе — спавн `fromDim` (`world.getSpawnPoint()` + верх), иначе — спавн dim 0.
- `allowsArrival(p, via)` = `lawful(strictEntry, true, …)` для известных via — хук для следующего релиза аддона. Описать в отчёте для аддона: «перед Прыжком в Подложку вызвать `allowsArrival(player, VIA_JUMP)` и `grantArrival`; телепорт седоков через `BridgeTeleportSC` и так даёт пропуск».

### 6.3 Портал и кровать
- ПВ-4, игроки: `PlayerTickEvent` (сервер, END), каждые `PORTAL_CHECK` тиков — `timeUntilPortal = max(timeUntilPortal, PORTAL_HOLD)` (поле public). Прочие сущности: `WorldTickEvent` (END) мира Подложки, каждые `PORTAL_CHECK` тиков — по `loadedEntityList` то же. Игрок стоит в `Blocks.portal` → не чаще раза в 60 тиков `sc.dim.portal.dead` и искры. Почему это работает: `Entity.setInPortal` при `timeUntilPortal > 0` только продлевает откат и не ставит `inPortal`.
- ПВ-10: `PlayerInteractEvent` `RIGHT_CLICK_BLOCK` в Подложке, `block.isBed(world, x, y, z, player)` → `setCanceled(true)`, `sc.dim.bed` (не чаще раза в 40 тиков). Пропускается только клик крадущегося игрока с предметом, у которого `doesSneakBypassUse` = false (тогда используется предмет, а не кровать); ключи (`ItemWrenchSC` и чужие) дают true — их клик отменяется. `PlayerSleepInBedEvent` в Подложке → `result = EnumStatus.OTHER_PROBLEM` и то же сообщение.

### 6.4 Очередь телепортов (`TeleportQueueSC`)
- Запись: UUID, dim, x, y, z, yaw, ключ причины. Обработка — `ServerTickEvent` END, не раньше следующего тика.
- Игрок не найден — выбросить. Мир — `server.worldServerForDimension(dim)`.
- Место — `SafeSpotSC.find(world, x, y, z, 8)` (новый у E): чанк загрузить, колонкой найти 2 воздуха над твёрдым не жидким блоком, не в чужом приватном поле (`TileEntityFieldGeneratorSC.privateRefusing(world, player, x + 0.5, y + 0.5, z + 0.5) == null` — тихая проверка; `ShieldEventHandler.privateFor` пишет в чат и для перебора мест не годится).
- Перенос — `BridgeTeleportSC.teleport`, сообщение причины. Не нашлось места — точка возрождения `respawnDimension`.
- Повторная постановка того же игрока заменяет старую запись.

### 6.5 Прибытие (ПВ-7)
- При каждом законном прибытии — `PotionEffect(Potion.resistance.id, BUFFER_TICKS, BUFFER_AMPLIFIER)`.
- Первое прибытие (персистентный NBT `scSi14Seen`) — `sc.dim.entry.welcome` и `sc.dim.entry.book`.
- Точка отправления — `scSi14From` {dim, x, y, z}: позиция игрока до переноса из `grantArrival`. Нужна Капсуле возврата в Ф2.
- Правила С7 моста и `BridgeSoftLandSC` уже работают — не трогать.

### 6.6 Мост (ПВ-5, ПВ-6, ПВ-8)
- `TileEntityBridgeControllerSC.buildDimList`: один провайдер на id (`dw != null ? dw.provider : createProviderFor(id)`); если он `INamedDimensionSC` — `d.setString("k", nameKey())`. Сырое имя `n` остаётся (старые клиенты, логи).
- `GuiBridgeControllerSC.dimName` и `GuiBridgeFarSC.dimName` (его наследуют `GuiRemoteSC` и `GuiArmorBridgeSC`): `k` есть и `StatCollector.canTranslate(k)` → `Lang.tr(k)`, иначе `n`.
- `plan()`: после общего множителя `ConfigSC.bridgeCostMultiplier` умножить на `EntryRulesSC.costMultiplier(a.dim != b.dim || farDim != ownDim(), SubstrateSC.isDim(a.dim) || SubstrateSC.isDim(b.dim) || SubstrateSC.isDim(ownDim()), SubstrateConfigSC.bridgeCostMultiplier)`. Перестроить блок масштабирования: `mul = общий × Подложки`, `if (mul != 1F) {…}`. Скидка якоря (`BridgeMarksSC.ANCHOR`, общая) — без изменений.
- Местные: в `enter()` — сразу после вычисления `dim`, до списания EU и до `scBridgeCd` — `if (!SubstrateEntrySC.mayCross(e, dim)) return;`. В `BridgeTeleportSC.teleport` — то же для не-игроков, как страховка для других вызывающих (аддон): вернуть null.
- `mayCross(e, toDim)` = `EntryRulesSC.mayCross(e instanceof EntityPlayer, e instanceof ISubstrateNativeSC, e.dimension != toDim, SubstrateConfigSC.nativesCrossBridge)`.

## 7. Тексты, справочник, NEI — пакет D

### 7.1 Lang
D — единственный владелец `ru_RU.lang` и `en_US.lang` (CRLF, UTF-8). Ключи ниже D вписывает сразу; новые ключи пакеты присылают в отчёте строкой `ключ=ru | en`, D дописывает их в конце.
- Ядро: `sc.dim.substrate.name` = «Подложка (Si-14)» | «Substrate (Si-14)»; `sc.dim.ids.mismatch` = «id Подложки не совпадают с сервером (%s): раздел dimension в config/SiliconAge.cfg должен быть как на сервере» | «The Substrate's ids differ from the server's (%s): the "dimension" section of config/SiliconAge.cfg must match the server's».
- Биомы (для справочника): `sc.dim.biome.plateaus` «Кремниевые плато» | «Silicon Plateaus», `sc.dim.biome.canyons` «Скрайб-каньоны» | «Scribe Canyons». Имена биомов в F3 — латиница из `biomeName`.
- W: `tile.siliconage.substrateRock.<crystallite|oxideViolet|oxideBlue|oxideGold|oxideGreen|copperTrace|traceNode|quartzTurf>.name`; `sc.dim.ore.name` = «%s в кристаллите» | «%s in Crystallite»; `tile.siliconage.crystalDruse.name` «Друза» | «Crystal Druse»; подсказки `sc.tooltip.substrateRock.<мета>`, `.details`, `.howto`; `sc.tooltip.substrateOre`, `.details`; `sc.tooltip.crystalDruse`, `.details`, `.howto`.
- M: `entity.SiliconAge.lineman.name` «Обходчик» | «Lineman»; `entity.SiliconAge.marauder.name` «Мародёр» | «Marauder»; `item.siliconage.trophy.<key>.name` для всех 25 мет (key — `nanoHelmet`… `singularBoots`, `bladeNano`… `bladeSingular`, `ionCutter`, `pulseEmitter`, `plasmaRifle`, `pe1`, `pe1m`); `item.siliconage.combatLoot.<shellFirmware|nanoScrap>.name`; `item.siliconage.spawnEgg.name` = «Яйцо призыва: %s» | «Spawn %s»; подсказки `sc.tooltip.trophy`, `.details`, `.howto`, `sc.tooltip.loot.<kind>`, `.details`, `.howto`, `sc.tooltip.spawnEgg`; по желанию `sc.mob.marauder.say.0..3`.
- E: `sc.dim.entry.welcome` («Канал стабилен. Вы по ту сторону прокола. Местные называют этот мир Подложкой.»), `sc.dim.entry.book` («Глава «Подложка» в справочнике — G»), `sc.dim.entry.refused` («Подложка отбросила вас назад: сюда ведёт только Космический мост»), `sc.dim.portal.dead` («Прокол глушит портал»), `sc.dim.bed` («Здесь не уснуть: прокол гудит всю ночь»).
- A: при необходимости `sc.emp.hit.mob.*` для отчёта стрелку. Остальное — существующие `sc.emp.*`.
- Справочник: `sc.book.ch.world` «Подложка» | «The Substrate», `sc.book.ch.world.desc`, `sc.manual.world.*`.

### 7.2 Справочник
- `BookChapter`: дописать `WORLD` **в конец** enum (после `RECIPES`).
- `BookContent`: в `build()` после `safety(list)` — `BookWorldSC.add(list)`; в `chapterIcon` — `case WORLD:` (кристаллит, пока `SubstrateBlocksSC.rock` есть, иначе компас).
- `manual/BookWorldSC` (новый, как `BookEmpSC`), статьи:
  - `world.substrate` «Подложка (Si-14)» — что это, как попасть, цена (из `BridgeMathSC` и обоих множителей), strictEntry, портал, кровать, смерть, «Буфер»;
  - `world.terrain` «Мир-микросхема» — сетка, биомы, слои, таблица блоков с иконками, таблица руд из `SubstrateOreTableSC`;
  - `world.armoured` «Бронированные враги» — запас, очки, щит, «села», таблицы ПБ из `MobArmorMathSC` и `EmpMathSC`, что работает (плазма, стрелы, ПЭ-1);
  - `world.lineman`, `world.marauder` — бестиарий: числа из констант классов;
  - `world.trophies` «Трофеи и добыча» — условия ПТ-1, таблица шансов из `TrophyTableSC`, невезение, добыча.

  G по блокам, мобам (яйцо) и предметам открывает статью (`BookEntry.about`). Все числа — из кода и конфига, не из текста.

### 7.3 NEI (`NEISiliconAgeConfig`, `ObtainHandlerSC`, `NeiCoverageSC`)
- Скрывать в Ф1 нечего: трофеи следующих фаз уже не видны через `getSubItems`. Яйца — видны.
- «Как получить»: порода (все меты), руды Подложки, друза — «генерируется в Подложке (Si-14)», с биомом и Y; трофеи — «падает с <моба> (шанс)»; добыча — «падает с <моба>»; яйца — «только в творческом режиме».
- `NeiCoverageSC` не должен отмечать новые предметы.

### 7.4 README
Черновик `docs/readme-substrate-draft.md` (сам README не трогать): что такое Подложка; одинаковые id `dimension` у сервера и клиентов; ракеты GC и как запретить взлёт («Dimensions where rockets cannot launch» += −1414); EnderIO и DE: внести `SiliconAge.lineman`, `SiliconAge.marauder` в чёрные списки банки душ и спавнеров; Thaumcraft выключен.

## 8. Тесты — пакет D

### 8.1 Самотест `debug/SelfTestSubstrateSC` (вызов из `SelfTestSC.run`, после `SelfTestEmpSC.run()`)
- Конфиг: значения по умолчанию (−1414, −1414, 240, 241, правила). Id не публичные поля холдера. `SubstrateNetSC.ruleFields()` — ровно 7 правил Ф1. В ConfigSC нет полей `dimensionId` / `providerId` / `biomeId*`.
- `SubstrateSC.pickBiomeId`: свободный слот — он сам; занятый → первый свободный в 240…254, потом от 239 вниз до 40 (255 — никогда: в чанке это метка «биом не вычислен»); `notThis` пропускается; всё занято → −1. `dimensionRefusal`: 0, 1, −1, положительный → отказ; свободный отрицательный → null.
- `SubstrateTerrainSC`: `distanceToLine` (0, 128, 255, −1), пояс каньона, `biomeKind`, `surfaceY` плато в 64–74, ширина каньона 9–15 и глубина 30–50 (по сетке точек, когда W напишет тела).
- `SubstrateOreTableSC`: множители танталита ×2, халькопирита ×0,5 и т. д.
- `MobArmorMathSC`: доли 80/88/96/100% при 20/22/24/28 очках; `absorb` с исчерпанием запаса; `shield` с переполнением; железо 2/6/5/2; `hasEmergencyMode(NANO)` = false; `shieldUp`; EU ↔ единицы.
- `TrophyTableSC`: меты (броня, клинки, оружие, 23, 24, всего 25), `badLuckFactor` (39 → 1, 40 → 2, 80 → 4 для Нано), `chance` — потолки ×1,3 и ×2,5, `gearDropMultiplier` 0 → 0, не больше 1.
- `SpawnRulesSC.maySpawn`: лимиты 8 и 40, Peaceful.
- `EntryRulesSC`: таблица `lawful`, `costMultiplier` (внутри мира — 1), `mayCross`.
- Предметы: у 25 мет трофея есть настоящий предмет и имя на обоих языках; `getSubItems` Ф1 — 6 мет.
- Тексты: все ключи §7.1 есть в обоих lang; глава WORLD строится без сырых ключей; в `SelfTestSC.bookPart2` поправить ожидание глав 14 → 15 (`chapters == 14` и текст «(14)»).

### 8.2 Мировой тест `debug/WorldTestSubstrateSC` (`-Dsc.worldtest=true`)
Вывод — `[SC-WORLDTEST] PASS / FAIL / SKIP substrate: …`, как у `WorldTestEmpSC`. SKIP, если `!SubstrateSC.active()`.
1. Регистрация: `DimensionManager.getProviderType(dim) == providerId`, `createProviderFor(dim)` — `WorldProviderSubstrateSC` и `INamedDimensionSC`, имя и ключ, слоты биомов наши, типы BiomeDictionary.
2. Мир: `server.worldServerForDimension(dim)`; `SubstrateSC.is(w)`; сгенерировать чанки (0,0), (8,8) и у линии (0, 5). В Y 0–120 нет `Blocks.stone`; верх на плато — дёрн; биом на линии — каньоны, в (128,128) — плато; сила дождя 0.
3. Броня моба: Обходчик с Нано-нагрудником, `spawnReserve(25, 25)`. Удар 10 от FakePlayer → HP и запас по `MobArmorMathSC`. Удар в обход брони → весь в HP. Запас до 0 → `down()`, `getTotalArmorValue() == 0`.
4. ПЭ-1 по мобу: `empHit(shooter, POINT, false, {1})` → `empSuppressedTicks(1) == 320` (Нано, 16 с). Повтор сразу — невосприимчив. `points()` упали до железа. С `empMobTargets = false` путь `EmpGunLogicSC` — Медлительность (если пушку можно выстрелить в тесте; иначе — только флаг в `fire`). Сифон: `empDrainEu` снимает запас по курсу 500 EU/ед.
5. Флаг и мост: `BornInSubstrateSC.mark` → `isBorn`. `BridgeTeleportSC.teleport(моб, 0, …)` при `nativesCrossBridge = false` → null; при true → проходит, флаг снят.
6. Дроп: убийство помеченного моба FakePlayer'ом → ни трофея, ни Прошивки, только опыт. Решающая функция `MobDropsSC` с «настоящим игроком» — отдельно.
7. Портал: сущность в Подложке, `timeUntilPortal = 0`, через `PORTAL_CHECK + 1` тиков ≥ 20.
8. Регресс ПЭ-1: `WorldTestEmpSC` идёт как раньше — проверить по логу.
9. По желанию: через ~600 тиков после теста, без игроков и форс-чанков, мир выгружен (только отчёт).

### 8.3 Проверка в реальной сборке (вручную, после пакетов)
По `docs/plan-substrate.md` §10. Лог сборки пользователя: TLauncher Forge, `-Dsc.packcheck`, см. память «Silicon Age pack check». Его javaw не закрывать.

## 9. Пакеты (файлы не пересекаются)

Общие правила владения. Каждый существующий файл правит ровно один пакет из этого списка или только координатор. Новые файлы создаёт только их пакет. Нужна правка чужого файла — в отчёт координатору: файл, место, текст.

### W — мир
- **Существующие (из ядра-заготовок):** `dim/WorldProviderSubstrateSC.java`, `dim/WorldChunkManagerSubstrateSC.java`, `dim/ChunkProviderSubstrateSC.java`, `dim/BiomeSubstrateSC.java`, `dim/SubstrateBlocksSC.java`, `dim/SubstrateCompatSC.java`, `dim/SubstrateRecipesSC.java`, `dim/SubstrateTerrainSC.java`, `dim/SubstrateOreTableSC.java`, `dim/client/SubstrateClientSC.java`. Других существующих файлов W не правит.
- **Новые:** `dim/MapGenSubstrateCavesSC.java`, `dim/SubstrateOreGenSC.java` (жилы в `populate`), `block/BlockSubstrateRockSC.java`, `block/ItemBlockSubstrateRockSC.java`, `block/BlockSubstrateOreSC.java`, `block/ItemBlockSubstrateOreSC.java`, `block/BlockCrystalDruseSC.java`, `assets/siliconage/textures/blocks/substrate_*.png`, `tools/gen_substrate_tex.py`.
- **Отдаёт:** ключи lang для D, константы мет (уже фиксированы), `SubstrateTerrainSC` и `SubstrateOreTableSC` с телами.

### A — броня мобов и ЭМИ
- **Существующие:** `mob/MobArmorSC.java`, `mob/EntityArmoredMobSC.java`, `mob/MobArmorMathSC.java` (заготовки ядра), `emp/EmpGunLogicSC.java`, `emp/EmpSiphonSC.java`, `emp/EmpSC.java` (только новые хелперы, существующие сигнатуры и поведение для игроков не менять), `emp/EmpRulesSC.java` (только новая перегрузка, если понадобится), `emp/EmpMathSC.java` (только новые константы), `emp/EmpShotSC.java` (только новые чистые функции), `emp/EmpNetSC.java` (только если `AimMessage` не хватит полей для моба), `item/BladeLogicSC.java` (только условие «Казни»), `client/ModelArmorGlowSC.java`.
- **Новые:** при необходимости `emp/EmpMobSC.java` (ветка мобов в одном месте) — по усмотрению A.
- **Не трогает:** `ItemArmorSC`, `ArmorLogicSC`, `ShieldEventHandler`, `debug/*` (тесты — у D), `WorldTestEmpSC`.

### M — существа
- **Существующие (из ядра-заготовок):** `mob/MobEntitiesSC.java` (только дописывать), `mob/EntityLinemanSC.java`, `mob/EntityMarauderSC.java`, `mob/SpawnDirectorSC.java`, `mob/MobEventsSC.java`, `mob/MobItemsSC.java`, `mob/MobRecipesSC.java`, `mob/TrophyTableSC.java`, `mob/SpawnRulesSC.java`, `mob/client/MobClientSC.java`, `assets/siliconage/sounds.json` (только дописывать в конец).
- **Новые:** `mob/ItemTrophySC.java`, `mob/ItemCombatLootSC.java`, `mob/ItemSpawnEggSC.java`, `mob/MobDropsSC.java`, `mob/ai/EntityAIBeamAttackSC.java`, `mob/ai/EntityAIRechargeAtTraceSC.java`, `mob/ai/EntityAICollectLootSC.java`, `mob/client/RenderSubstrateMobSC.java`, `assets/siliconage/textures/entity/lineman.png`, `marauder.png`, `assets/siliconage/textures/items/loot_*.png`, `trophy_overlay.png`, `tools/gen_substrate_mobs_tex.py`.
- **Зависит от:** `EntityArmoredMobSC` и `MobArmorSC` (A — только вызовы через фиксированные сигнатуры), `SubstrateBlocksSC.isTrace` (W), `BornInSubstrateSC`, `SubstrateSC`, `SubstrateConfigSC` (ядро).

### E — вход и мост
- **Существующие:** `dim/SubstrateEntrySC.java`, `dim/TeleportQueueSC.java`, `dim/EntryRulesSC.java` (заготовки ядра), `bridge/BridgeTeleportSC.java`, `tileentity/TileEntityBridgeControllerSC.java` (только `buildDimList`, `plan()` — блок множителя, `enter()` — проверка местных), `client/GuiBridgeControllerSC.java` (только `dimName`), `client/GuiBridgeFarSC.java` (только `dimName`).
- **Новые:** `dim/SafeSpotSC.java`, по желанию `dim/LastSpotSC.java`.
- **Раздел конфига `dimension`** живёт в холдере ядра. Если E нужно новое правило — прислать координатору строку для `SubstrateConfigSC.load` (публичное поле — синхронизируется само).

### D — тексты, справочник, NEI, тесты
- **Существующие:** `assets/siliconage/lang/ru_RU.lang`, `assets/siliconage/lang/en_US.lang` (CRLF), `manual/BookChapter.java`, `manual/BookContent.java` (только `build()` и `chapterIcon`), `nei/NEISiliconAgeConfig.java`, `nei/ObtainHandlerSC.java`, `nei/NeiCoverageSC.java`, `debug/SelfTestSC.java` (вызов `SelfTestSubstrateSC.run()` и ожидание 15 глав), `debug/WorldTestSubstrateSC.java` (заготовка ядра).
- **Новые:** `manual/BookWorldSC.java`, `debug/SelfTestSubstrateSC.java`, `docs/readme-substrate-draft.md`.
- **Порядок:** ключи §7.1 и каркас статей — сразу; числа и тесты на тела W/A/M/E — после их пакетов, в конце собрать ключи из отчётов.

### Только координатор
`SCMod.java`, `util/ConfigSC.java`, `init/ModBlocks.java`, `init/ModItems.java`, `proxy/ClientProxy.java`, `proxy/CommonProxy.java`, `handler/ConfigSyncSC.java`, `CommonEventHandler.java`, `ShieldEventHandler.java`, файлы ядра §0 (`SubstrateConfigSC`, `SubstrateSC`, `SubstrateNetSC`, `client/SubstrateIdsClientSC`, `INamedDimensionSC`, `IEmpTargetSC`, `IArmoredMobSC`, `IExecuteImmuneSC`, `ISubstrateNativeSC`, `BornInSubstrateSC`), `docs/plan-substrate*.md`, `docs/plan-pe1.md`, `CHANGELOG.md`, `README.md`. Правки, которые уже внесены, перечислены в §0; новые — по отчётам пакетов.

## 10. Жёсткие правила для всех
1. Аддон Vehicles (`MineMod/SiliconAgeVehicles`) не трогать. Файлы не удалять. Не коммитить.
2. Клиентский код — только в `client`-пакетах (`com.sc.dim.client`, `com.sc.mob.client`, `com.sc.client`, `com.sc.emp.client`). Общий код не касается клиентских классов. Методы провайдера с цветами — `@SideOnly(Side.CLIENT)`.
3. Мир Подложки определяется только через `SubstrateSC.is(world)` или `isDim` на сервере, никогда сравнением с `SubstrateConfigSC.dimensionId()` на клиенте.
4. Каждый новый предмет и блок — сразу с подсказкой TooltipSC (кратко, Shift — подробно, Ctrl — как получить и использовать).
5. Рецепты — только ванильные предметы и предметы мода.
6. Тексты добавляет только D. Остальные используют ключи §7.1, новый ключ присылают строкой `ключ=ru | en`. Тон — как в остальном UI мода.
7. Реестровые имена, id пула сущностей, меты, слоты DataWatcher, ключи NBT (`scBornSi14`, `scMobArmor`, `scBag`, `scTrophyDry`, `scSi14Seen`, `scSi14From`) — фиксированы. Добавлять только в конец.
8. Никаких изменений NBT стеков брони на мобе после спавна: S04 с полным NBT всем зрителям.
9. Генерация — без `Blocks.stone`, без каскада, светящиеся блоки — по счёту.
10. Производительность: AI проверяет видимость не чаще раза в 10 тиков, счёт сущностей по миру — раз в тик мира с кэшем, свечение мобов — до 32 блоков.

## 11. Неясно / TODO(balance)
- Обходчик: в предложении «Нано без гелия (аварийный режим)». По правилам брони игрока у Нано аварийного режима нет. Принято ★ ПБ-5: Обходчик — обычная Нано-броня; механика `emergency` остаётся для Кванта и выше. Если пользователь захочет «железного» Обходчика — M ставит `emergency = true` (одна строка), а `hasEmergencyMode` расширяется на Нано.
- В таблице §8 предложения у Обходчика «Сифон — 3–4 с». Это противоречит правилу «6% полного запаса в секунду» (≈ 17 с). Принято правило; цифра в таблице считается опиской.
- Применение добычи до Скупщика (§5.7) и множители руд (§3.5) — стартовые.
- Трофеи в Ф1 нельзя восстановить (Ф3). Подсказка говорит об этом в будущем времени.
