# Проверка 2026-11-25: /security-review (весь мод) и /simplify (выпуск 0.2.4)

Только отчёт, правок в коде нет. Мод: 899 файлов Java, 254 115 строк (≈202 тыс. без пустых строк и комментариев,
из них тесты `com/sc/debug` — 36 450).

## Безопасность (весь мод)
Уязвимостей с уверенностью ≥ 8/10 нет. Проверены: все серверные обработчики пакетов (мост, броня, поле, карьер,
поселения и торговля, рейсы, лётное поле, ПЭ-1), команды `/scsquad`, `/scbridge`, `/scembassy` (уровень 2),
проверка обновлений (HTTPS без отключения сертификатов, из ответа — только OPEN_URL), файлы (пути только из
констант и чисел, нет ObjectInputStream), доверие к NBT предметов (пульты, карты связи, разобранные блоки — права
перепроверяются на сервере), модель владельца (UUID).

- **Н1 (надёжность, ниже порога безопасности).** `settle/SettleNetSC.java:136` — пакеты торговли, рейсов и лётного
  поля обрабатываются прямо в сетевом потоке (TradeServerSC, FlightServerSC, EmbassyAirServerSC), а не ставятся
  в очередь на серверный тик, как в EmpNetSC/ArmorNetSC (`BladeSingularSC.queueTask`). Возможны гонки с кликами по
  инвентарю (оплата «в недостаче») и падения при изменении реестров не из главного потока. Исправление — очередь.

## /simplify — выпуск 0.2.4 (v0.2.3-beta..v0.2.4-beta)

### Эффективность
- **Э1** `TileEntityEnergyBankSC.java:144, 240-366` — контроллер раз в секунду перечитывает весь куб (до 343 блоков) и пересобирает порты; несобранный пробует 3 размера × 6 сторон. → пересканировать по событию (поставили/сломали блок банка), редкий страховочный скан.
- **Э2** `GuiMachineSC.java:356-420` (+14 вызовов) — поиск рецепта 8–14 раз за кадр на экранах Станции и центрифуги. → один раз за кадр.
- **Э3** `settle/client/EntityDroneFxSC.java:97-117` (+EntityMothFxSC) — каждая частица дрона/мотылька рвёт пакет отрисовки, меняет текстуру и создаёт `new ResourceLocation` каждый кадр (до 30 частиц). → static final + отрисовка пачкой.
- **Э4** `mob/ai/EntityAITargetGathererSC.java:28-44` — каждый Линейщик/Мародёр 2 раза в секунду ищет сущности в коробке 41×9×41, хотя Собирателей почти никогда нет. → ранний выход, если вылазок нет.
- **Э5** `TileEntityGeneratorSC.java:932-964` (+SingularReactorSC:1222) — `bindPortsSC` второй раз обходит оболочку реактора сразу после `scanBig`. → собирать Порты данных в `scanBig`.
- **Э6** `dim/MapGenSubstrateCavesSC.java:126` → `MapGenTunnelSC.covers()` — 256 поисков на каждый новый чанк. → один раз на чанк.
- **Э7** `settle/client/AmbientLifeClientSC.java:110` — раз в секунду копия всего `loadedTileEntityList`. → без копии / по ближним чанкам.
- **Э8** `settle/client/AirTabSC.java:191-225` — вкладка «Лётное поле» пересобирает и переносит весь текст каждый кадр. → один раз при открытии.
- **Э9** `TileEntityQuarrySC.java:1797` — утилизатор сравнивает строки словаря руд для каждого добытого предмета. → закешировать id «stone»/«cobblestone».
- **Э10** `settle/SquadSC.java:221` — состав звена ищется перебором всех игроков, когда-либо заходивших. → индекс звено → участники.
- **Э11** `TileEntityGeneratorSC.java:2348` — Токамак XV в ожидании розжига каждый тик опрашивает Порты данных. → брать результат секундного скана.

### Повторы (уже есть готовое)
- **П1** `tell/real/online/give` заново написаны в 6 новых файлах (BreachFightSC, EmbassyAirServiceSC, WantedSC, GathererSC, RelayPostSC, SquadSC, RaidAlarmSC) — и по-разному фильтруют FakePlayer. → общий util (`ChatSC`, `PlayersSC`), `HazardNetSC.reachable`, `FlightRunSC.online`, `TradeServerSC.give`.
- **П2** `item/SalvageSC.java:299, 351` — 4-я копия разбора рецептов верстака. → `BookMaterialsSC.ingredients/same`.
- **П3** `embassy/EmbassyAirSiteSC.java:336, 346` — копии `EmbassySiteSC.typed` и `EmbassyAirfieldSC.inAny`.
- **П4** `num(long)` — 4 копии форматирования чисел (GuiEnergyBankSC, ItemBlockEnergyBankSC, WailaSC, EmpTooltipSC). → один helper.
- **П5** `settle/RelayPostSC.java` — поиск «Ретранслятор в регионе» ×3, поиск обороны по координатам ×2, `natural()` повторяет `playerBlock()`, минуты считаются в обход `minutes()`; проверка дистанции до поселений — полным перебором вместо `SettlementRegistrySC.near`.
- **П6** `mix()` (splitmix) — ещё 3 копии (RelayPostSC, WantedRulesSC, TunnelLayoutSC), всего 14. → `EmbassySiteSC.mix`.
- **П7** сброс `persistenceRequired` через рефлексию ×4 (RelayPostSC, WantedSC, RaidSC, CaravanRunSC). → один util.
- **П8** привязка к контроллеру (bound + x/y/z + NBT) ×3 (DataPort, EnergyBankPort, StolenSolar). → маленький класс.
- **П9** `levelName` ×4; **П10** рамка окна энергобанка рисуется вручную вместо `GuiBigSC`; **П11** «кабель для уровня» ×3 (ModRecipesIdeasSC, ModRecipesCrafting).
- **П12** (тесты) WorldTestAirfieldSC/WorldTestContractsSC — свои `chips/held/give/count` вместо TradeServerSC.

### Упрощение
- **У1** 14 мёртвых методов без вызовов (WantedSC `postNow/spawnNow`, GathererSC `sendNow/watchNow`, ContractEventsSC `readingsNow`, TradeTableSC `tokenRestore`, RaidSC `killsBy/callNow`, ContractTableSC `killGoal`, SingularReactorSC `isPortStopPending`, EmbassyAirServiceSC `armourGasLeft/armourGasPrice`, ComponentEmbAirSC `tileq`, EmbassyAirfieldSC `Field.airInts`), пустой `ModRecipesIdeasSC.pd7()`, неиспользуемые `GathererSC.SAVED_REACH`, `EmbassyAirApiSC.ADDON_MODID`. → удалить (или подключить к тестам).
- **У2** решение поля `{kind, side, ils, version}` ходит голым `int[4]`, нормализуется в 5 местах. → класс `Decision`.
- **У3** `RelayPostSC.refusalWhy` возвращает `String[]`, числа гоняются int→String→int. → маленький класс-ответ.
- **У4** `TileEntityEnergyBankSC` — `portsIn/portsOut` выводятся из `portTiers`, но хранятся и синхронизируются отдельно.
- **У5** `EmbassyAirfieldSC.problem()/elements()` (~115 строк) нужны только самотесту — перенести в тест.
- **У6** `WantedSC` — два обработчика смерти с одинаковой выборкой. → один.

### Уровень решения (архитектура)
- **А1** `TileEntityMachineSC.java:1491/1511` — «инструмент тратится целиком» зашит проверкой типа (`madeFromTool`), хотя у рецепта уже есть флаг `freshTools()`. → один флаг рецепта; следующий такой рецепт иначе будет изнашивать вход.
- **А2** раскладка экранов машин — длинные списки типов в `ContainerMachineSC` и `GuiMachineSC`, должны совпадать. → свойство `MachineType.layout`.
- **А3** «Порт данных = свинцовая стена» вставлено в 3 места (RadiationSC, SingularReactorSC, TileEntityGeneratorSC). → один предикат.
- **А4** поддержка компаратора — 10 одинаковых кусков в 5 блоках и 5 тайлах. → общий helper/база.
- **А5** клик крадучись по колонке обслуживания разбирается внутри защиты поселений. → свой обработчик/интерфейс.
- **А6** лётное поле — третий вид зоны, вставленный в каждый обработчик защиты отдельно. → один `zoneAt`.
- **А7** состояние обороны Ретранслятора разнесено по памяти, тайлу и реестру; `markLost` грузит чанк ради флага. → запись в реестре — главная.
- **А8** `client/DataPortTooltipSC` — глобальный слушатель подсказок ради одного блока, вопреки правилу «TooltipSC на ItemBlock». → `ItemBlockDataPortSC`.
- **А9** ключ знает список разбираемых блоков; **А10** WAILA — ветки под каждый тайл; **А11** голограмма сети — особый случай порта банка; **А12** таблицы спавна — параллельные массивы по id.
