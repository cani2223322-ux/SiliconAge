# ПЭ-1 — техплан и разбивка на пакеты (2026-10-10)

Дизайн: `docs/plan-pe1.md` (утверждён). Здесь — архитектура под существующий код и раздел работы на
параллельные пакеты с **непересекающимися файлами**. Java 6 (без лямбд/diamond/try-with-resources/switch по String),
MCP-имена, Forge 1.7.10. Сборка:
`T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd /c/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q`
(строки «Read timed out»/404 безвредны). Каждый пакет обязан собираться сам по себе поверх ядра.

## 0. Ядро (уже написано, собирается)

Пакет `com.sc.emp` (+ `com.sc.emp.client`). **Только координатор** правит файлы ядра; пакету нужна
правка ядра — описать её в отчёте.

| Файл | Что |
|---|---|
| `emp/EmpMode.java` | POINT / PULSE / SIPHON, цвета (110,215,255) / (186,132,255) / (130,236,100), `key()` |
| `emp/EmpModule.java` | CAPACITY, FOCUS, COOLING, EFFICIENT, WIDE; `bit()`, `key()` |
| `emp/EmpMathSC.java` | все числа плана + чистая математика (длительности, зоны, нагрев, выкипание, сифон, нагрев пушки). Числа без источника помечены `TODO(balance)` |
| `emp/EmpStateSC.java` | состояние игрока: `until[4]`, `immuneUntil[4]` (= until + невосприимчивость, ставится сразу — тик-наблюдатель не нужен), `boilMul/boilUntil`, `streamUntil/drainBy`, `lastHeatHit`, `aimedUntil/aimedBy`, `rebootReadyAt`; NBT write/read |
| `emp/EmpSC.java` | хранилище состояния (сервер: entity data `scEmp` — переживает релог, после смерти пропадает; клиент: копия по entityId) и **все публичные хуки** (ниже) |
| `emp/EmpNetSC.java` | сообщения на `ArmorNetSC.CHANNEL`, дискриминаторы 7..10 (регистрация из `ArmorNetSC.init`) |
| `emp/EmpActionsSC.java` | сервер: разбор ActionMessage (режим, настройки — сам; модули/отмена — в A; перезагрузка — в B) |
| `emp/EmpEventsSC.java` | логин/респаун/смена измерения/StartTracking → синк; PlayerTick → `EmpGunLogicSC.playerTick`; логаут → `forget` |
| `emp/ItemEmpGunSC.java` | предмет (регистрация `empGun`, meta 1 = ПЭ-1М), NBT-аксессоры, зарядка (Shift+ПКМ по машине, IC2), делегаты в `EmpGunLogicSC` |
| `emp/ItemEmpModuleSC.java` | модули (`empModule`, meta = ordinal) |
| `emp/client/EmpClientSC.java` | клиентская проводка: вызывает `register()` пакетов C/D, чистит копии при выходе с сервера |
| заглушки | `EmpGunLogicSC` (A), `EmpArmorSC` (B), `EmpRulesSC` (E), `EmpTooltipSC` (F), `client/RenderEmpGunSC`, `EmpHudSC`, `EmpFxSC` (D), `client/EmpKeysSC` (C), `manual/BookEmpSC`, `debug/SelfTestEmpSC`, `debug/WorldTestEmpSC` (F) — сигнатуры фиксированы, тела пакетов |

Правки ядра в общих файлах (готово): `ConfigSC` (раздел `weapons.pe1`, поля `empEnabled, empAffectsPlayers,
empDurationMultiplier, empSiphonRateMultiplier, empSiphonLoss, empImmunitySeconds, empHeatMultiplier,
empHeliumBoilMultiplier` — синкаются сами через ConfigSyncSC), `ModItems` (`empGun`, `empModule`), `SCMod`
(события, `serverStopped`, world test), `ClientProxy` (`EmpClientSC.register()`), `ArmorNetSC.init`
(`EmpNetSC.register`), `BladeLogicSC.claimed` → public, `BookContent` (вызов `BookEmpSC.add` после статьи «Оружие»),
`SelfTestSC.run` (вызов `SelfTestEmpSC.run`), зарядка пушки: `ArmorElectricManagerSC` (IC2), `ItemChargeSC`
(переносные батареи), `TileEntityEnergyStorageSC.chargeItem/tierAllowsAt` (накопители и зарядная плита),
`ArmorLogicSC.chargeWeapons` (функция «Зарядник» нагрудника; не во время использования).

### 0.1 NBT пушки (корень стака)
`ChargeSC` int EU · `EmpMode` byte · `EmpOff` byte (биты выключенных режимов, все три выключить нельзя) ·
`EmpMods` int (биты модулей) · `HeatSC` int 0..100, `OverheatSC` bool (блок Точки/Импульса до <70; Сифон — мягкий перегрев) · `EmpHeatAt` long ·
`EmpPulseAt` long · `EmpZone` byte (0 по попаданию / 1 шлем / 2 нагрудник / 3 ноги) · `EmpRecv` int
(порядок приёмников, base-4: r0 + 4·r1 + 16·r2; 0 = 0,1,2) · `EmpRecvOff` byte · `EmpLimit` byte (изменено 2026-10-11: 0 = без
предела, избыток сбрасывается; 1..100 = пока моя броня не наберёт N%; 101 = пока приёмник не полон) · `EmpNoWarn` bool. ПЭ-1М = meta 1.

### 0.2 Публичные хуки `EmpSC` (обе стороны, если не сказано иное)
- `isSuppressed(p, slot)`, `anySuppressed(p)`, `suppressedTicks(p, slot)`, `immuneTicks(p, slot)` — slot = armorType 0 шлем..3 ботинки.
- `boilMultiplier(p)` (1/2/3), `boilTicks(p)`; сервер: `boilExtraMbPerSecond(p, loopCapacityMb)`.
- `drained(p)`, `drainedBy(p)`; `aimedAt(p)`, `aimedBy(p)`; `rebootCooldownTicks(p)`.
- факты: `modPiece(p, slot)`, `wearsModArmour(p)`, `defenseTier(p)`, `hasSensorChip(p)` (чипы в этом моде живут в нагруднике — «Сенсорный чип в шлеме» из плана = Сенсорный чип в надетом нагруднике), `heliumInLoop(p)`.
- сервер: `applyHit(target, mode, upgraded, slots)` → `HitResult` (подавление + нагрев с убыванием/невосприимчивостью/Сингулярной/Защитным чипом/конфигом + выкипание + синк; без брони мода в зоне → Медлительность 2 с), `applyNoArmour(living)`, `streamSecond(target, shooter)` (раз в секунду потока: нагрев 3%/с, выкипание 1%/с, хвост ×2 10 с, «Энергия утекает!»), `markAimed(target, shooter)`, `clearSuppression(target)` (перезагрузка: подавление → невосприимчивость сейчас, кулдаун 60 с), `sync(p)`, `syncTo(about, to)`.
- звуки: `EmpSC.SND_*` = `emp.charge, emp.hum, emp.shot, emp.pulse, emp.siphon, emp.overheat, emp.suppress, emp.reboot` (полное имя `siliconage:` + имя).

### 0.3 Сеть (`ArmorNetSC.CHANNEL`)
- 7 `ActionMessage` (клиент→сервер, `EmpNetSC.send(action, a, b)`): `SELECT_MODE a=mode`, `CYCLE_MODE a=±1`,
  `TOGGLE_MODE a=mode b=on`, `SET_ZONE a`, `SET_RECEIVER_ORDER a=packed`, `TOGGLE_RECEIVER a b`, `SET_LIMIT a`,
  `SET_WARN b`, `MODULE_INSTALL a=module b=слот инвентаря`, `MODULE_REMOVE a`, `CANCEL_CHARGE`, `EMERGENCY_REBOOT`.
- 8 `StateMessage` — EmpStateSC игрока всем в 64 блоках (и ему самому) → `EmpSC.clientState(id)`.
- 9 `AimMessage` — стрелку каждые 10 тиков, пока ПЭ-1 в руке (шлёт A): цель, причина «Нельзя» (`EmpNetSC.OK/NO_PVP/NO_CLAIM/NO_OWN/NO_DISABLED/NO_PLAYERS`), комплект, биты подавленных/невосприимчивых, время, заряд цели % (−1 без Сенсорного чипа), приёмник (−1 = «Некуда принимать») и место, поток идёт / принято EU → `EmpNetSC.lastAim/lastAimAt`.
- 10 `FxMessage` (`EmpNetSC.sendFx`) — `FX_POINT, FX_PULSE` (луч до x,y,z), `FX_STREAM_ON/OFF`, `FX_ABSORBED` (граница ЭМИ-экрана), `FX_SUPPRESSED` (искры), `FX_REFLECTED` → очередь `EmpNetSC.FX` (D разбирает в клиентском тике).

### 0.4 Жёсткие правила для всех
1. **Не менять NBT пушки на сервере, пока она в использовании** (`p.isUsingItem()` и это текущий предмет): в 1.7.10
   изменённый стак приходит клиенту новым объектом, клиент сбрасывает `itemInUse` — набор/поток рвутся, отпускание
   ПКМ не доходит до сервера. Нагрев, принятый в пушку заряд и т. п. копить в серверной карте A и записывать при
   выстреле/конце потока (выстрел и так завершает использование). Оплата Точки/Импульса — в момент выстрела.
2. Клиентский код — только в `com.sc.emp.client`/`com.sc.client`; общий код не трогает клиентские классы.
3. Язык: тексты добавляет **только F**. Остальные пакеты используют ключи из §8 этого плана; новый ключ — вписать
   в отчёт строкой `ключ=ru | en`.
4. Чужие файлы не править; нужна правка — в отчёт координатору.

## 1. Пакет A — поведение пушки, рецепты, NEI

**Файлы:** `emp/EmpGunLogicSC.java` (тела), `emp/ItemEmpGunSC.java` (только поведенческие методы, без смены NBT-ключей
и публичных сигнатур), `emp/ItemEmpModuleSC.java`, новые `emp/EmpSiphonSC.java`, `emp/EmpShotSC.java` (по желанию),
`init/ModRecipesCrafting.java` (только новый блок ПЭ-1 в конце), `init/ModRecipesUpgradeStation.java`,
`tileentity/TileEntityMachineSC.java` (только перенос NBT ПЭ-1 при улучшении, рядом с блоком клинка/бура ~стр. 1236–1262),
`nei/NeiCoverageSC.java` (только если покрытие NEI ругается).

**Вызывает:** `EmpSC.applyHit/applyNoArmour/streamSecond/markAimed/isSuppressed/defenseTier/hasSensorChip/modPiece`,
`EmpRulesSC.refusal/screenAbsorbs` (E), `EmpArmorSC.guardReflects/guardReversesSiphon` (B), `EmpMathSC.*`,
`BladeLogicSC.claimed` (внутри E), `ArmorLogicSC.chargeSuit`, `ItemBatterySC.isBattery/charge/tierOf`,
`ItemArmorSC.chargeOf/discharge/capacityOf`, `EmpNetSC.sendFx`, `EmpNetSC.channel().sendTo(AimMessage)`.

**Поведение:**
- ПКМ: режим выключен/пушка перегрета/мало EU (Точка 50 000, Импульс 150 000, Сифон 5 000)/кулдаун Импульса 20 с → отказ в action bar (`sc.emp.*`), без траты. `ConfigSC.empEnabled=false` → не стреляет.
- Набор через `setItemInUse`: Точка 20 тиков, Импульс 60 (ПЭ-1М 40) — выстрел автоматически по окончании набора; отпустили раньше → отмена без EU. Shift во время набора (Shift+ПКМ) и клавиша «Отменить набор» (`CANCEL_CHARGE`) → отмена без EU. Импульс: гул `emp.hum` слышен на 16 блоков; цель фиксируется в начале набора (если есть в прицеле); цель вне видимости > 10 тиков (`PULSE_LOS_GRACE`) → набор сбит.
- Hitscan как у `ItemWeaponSC.findTarget` (луч от глаз, хитбокс +0.3, видимость блоков); дальность `EmpMathSC.range(mode, focus)`: Точка 32 (Фокус +16 — TODO(balance)), Импульс 32, Сифон 16 (Фокус 24). Промах тратит EU (как оружие).
- Порядок проверки попадания: `EmpRulesSC.refusal(shooter, target, true)` (не OK → без EU, сообщение) → `EmpRulesSC.screenAbsorbs` (поглощено → EU потрачены, `FX_ABSORBED`, ничего не применяется) → цель-игрок и `!ConfigSC.empAffectsPlayers` → отказ `NO_PLAYERS` → Импульс и `EmpArmorSC.guardReflects` → применить к стрелку (если на нём броня мода; иначе ничего), `FX_REFLECTED` → иначе `EmpSC.applyHit(target, mode, ПЭ-1М, слоты)`. Слоты Точки: `EmpMathSC.pointPieces(zone, доля высоты попадания)` (голова ≥ 0.72 → {0}, корпус ≥ 0.40 → {1}, иначе {2,3}); Импульс {0,1,2,3}. Моб / игрок без брони мода → `applyNoArmour`. Звуки `emp.shot`/`emp.pulse`, у цели `emp.suppress`; эффекты `FX_POINT/FX_PULSE/FX_SUPPRESSED`.
- Сифон (удерживать ПКМ): без доступного приёмника — не стартует, «Некуда принимать». Приёмники в порядке `receiverOrder`, кроме выключенных: батарея мода в инвентаре с местом; своя броня мода не полная; сама пушка. Старт: 5 000 EU. Раз в 5 тиков (изменено 2026-10-11): скорость `EmpMathSC.siphonRate(полная ёмкость брони цели, костюм нагрудника цели, Широкий, defenseTier цели, empSiphonRateMultiplier, 5)` (Нано/Квант/Экзо/Сингулярная 6/5/4/3% полного заряда в секунду, Широкий ×1,5), при мягком перегреве ×25%; снимается с частей брони мода цели (сначала нагрудник, потом остальные), доходит `delivered(taken, siphonLossPct(Эффективный, цель подавлена, empSiphonLoss))` — в приёмники по порядку, сколько влезет, остальное сбрасывается (`EmpShotSC.streamStep(..., vent)`, `FX_VENTED`). Нагрев пушки — `EmpMathSC.streamHeat` (десятые доли, 2,9/с; на 100 — поток на 25%, остывание 3/с до <70). Раз в 20 тиков — `EmpSC.streamSecond`. Поток рвётся: отпустили ПКМ, цель дальше дальности/за блоком, приёмник полон (только с пределом «пока приёмник не полон») или предел «моя броня N%» достигнут, броня цели пуста, ЭМИ-экран, `refusal` ≠ OK. Только игроки в броне мода (мобы, машины, броня других модов — нет). «Страж» (`guardReversesSiphon`): поток идёт обратно — из брони стрелка в броню цели. Заряд в саму пушку — копить и записать при конце потока (правило 0.4.1). `FX_STREAM_ON/OFF`, звук `emp.siphon`.
- Нагрев пушки (`EmpMathSC`): Точка +10, Импульс +30, Сифон +11/с; Охлаждение ×0.7; максимум 100 → перегрев, блок до 0; остывание 8/с после 20 тиков без нагрева (≈12.5 с). Предупреждение за 2 с (`overheatWarn`, если `warnOn`), звук `emp.overheat`.
- Скан прицела каждые `AIM_SCAN_TICKS` у игрока с ПЭ-1 в руке: `AimMessage` стрелку; у цели с Сенсорным чипом — `EmpSC.markAimed`.
- Модули: `installModule` — из слота инвентаря, один каждого вида; `removeModule` — в инвентарь (или выбросить). Снятие Ёмкости режет заряд до 600 000 (делает `setModule`).
- Перенос NBT при ПЭ-1 → ПЭ-1М в станции (заряд, модули, настройки).
- `forget(p)`/`forget(null)` — чистить серверные карты (логаут, смерть, смена измерения, остановка сервера).

**Рецепты** (только предметы мода/ванилы; бесформенные через `OreRecipes.shapeless`):
- ПЭ-1: Импульсный эмиттер (существующее оружие `ModItems.WEAPONS.get(WeaponType.PULSE_EMITTER)` — в плане «Импульсный излучатель») ×1, `copperCoil` ×2, `capacitor` ×2, `focusLens` ×1, `sensor` ×1, Контроллер (`SiliconMaterial.CONTROLLER`) ×1, `energyCellHV` ×1.
- Ёмкость: `energyCellHV` + `capacitor` ×2 + `copperCoil`. Фокус: `focusLens` ×2 + `sensor`. Охлаждение: `copperCoil` ×2 + ведро воды (возвращается пустым — ванильный containerItem ведра) + `steelCasing`. Эффективный сифон: `tantalumCapacitor` ×2 + Контроллер. Широкий канал: `tantalumCapacitor` ×2 + `energyCellHV` + `copperCoil` ×2.
- ПЭ-1М: Станция улучшения HV: ПЭ-1 + `quartzEmitter` + `tantalumCapacitor` ×2 (как `registerBladeUpgrades`).
- Проверить пересечения: `-Dsc.packcheck` (RecipeConflictsSC) — отчитаться.
- NEI: верстак и станция показываются сами; проверить `NeiCoverageSC`.

**Тесты (код отдать F через отчёт или написать в своём файле `emp/EmpShotSC` статические чистые функции, которые F проверит):** выбор приёмника по порядку/выключениям; расчёт потока за тик; отказ без EU.

## 2. Пакет B — броня

**Файлы:** `item/ArmorLogicSC.java`, `item/ItemArmorSC.java`, `util/ArmorGasSC.java`, `CommonEventHandler.java`,
`util/ArmorFeature.java` (если нужно), `emp/EmpArmorSC.java`, `ShieldEventHandler.java` (если щит читается мимо `active()`).

**Вызывает:** `EmpSC.isSuppressed/anySuppressed/boilExtraMbPerSecond/clearSuppression/rebootCooldownTicks`, `EmpMathSC.REBOOT_*`, `GUARD_REFLECT_PCT`.

**Поведение:**
- `ArmorLogicSC.active(p, f)`: функция подавленной части не работает, **кроме `AIR`** (дыхание кислородом остаётся). Шлем → ночное зрение, сканеры, HUD (строки ЭМИ рисует D вне зависимости от HUD). Ноги: функции перемещения (`FLIGHT`, `GRAV_FLIGHT`, рывок, прыжки, смягчение падения и т. п. — составить список по `ArmorFeature`) гаснут, если подавлены поножи или ботинки, даже если сама функция в нагруднике. Пройти все места, где функции/чипы читаются мимо `active()` (sensorChip в `perSecond`, `regenOn`, `shieldActive`, …).
- Нагрудник подавлен → чипы без эффекта, без EU, без нагрева чипов (как аварийный режим в `CommonEventHandler.tickArmorHeat`), но нагрев от ПЭ-1 (`ArmorLogicSC.addHeat` из ядра) копится.
- Защита подавленной части — как у железной (тот же путь, что аварийный режим в `ItemArmorSC.getProperties/getArmorDisplay/armorPoints`). Заряд и газы не теряются.
- Полёт, срезанный подавлением, — плавный спуск 5 с (`softDescent`, как `flightCutByGas`); причина в `flightCutReason` → `sc.emp.warn.flightcut`.
- Выкипание: в `tickArmorHeat` после `heliumBoilOff` — новое `ArmorGasSC.heliumBoilExtra(worn, EmpSC.boilExtraMbPerSecond(p, ArmorGasSC.capacityOf(worn, HELIUM)), fusion)` через `heliumUse` (Рекуператор работает). Криоконтур — как сейчас. Нано без гелия только греется (само выходит).
- Аварийная перезагрузка `EmpArmorSC.emergencyReboot(p)`: ничего не подавлено → отказ без оплаты; кулдаун (`rebootCooldownTicks`) → отказ; аргон 100 мБ, если в комплекте есть бак аргона (Квант+), у Нано — без аргона (TODO(balance), подтвердить у пользователя); 20% заряда (`paySuitShare(0.2F)`); нет — отказ. Успех → `EmpSC.clearSuppression`, звук `emp.reboot`, чат.
- «Страж»: **у брони ветки «Страж» нет** (Р2: ур. 3 — пресс/захват, ур. 5 — чёрная дыра/купол; «Страж» есть только у Сингулярного клинка, `ToolLevelSC.BLADE_GUARDIAN`). Ближайший аналог — защитная ветка ур. 5 Б «Гравитационный купол» (`SingularLevel.branchChoice(chest, 5) == BRANCH_B`). Реализовать `guardReflects` (Сингулярный нагрудник с этой веткой, шанс 30% — TODO(balance)) и `guardReversesSiphon` (та же ветка, броня стрелка заряжена > 10%) через один приватный предикат, чтобы координатор мог поменять условие. **Вопрос пользователю** в отчёте.

**Тесты:** `active()` с подавленной частью (кроме AIR); ноги гасят полёт; броня = железо; выкипание с Рекуператором; перезагрузка — оплата, кулдаун, отказы.

## 3. Пакет C — меню K, клавиши, колесо

**Файлы:** `client/GuiArmorSC.java`, `client/ArmorKeyBindsSC.java`, `client/ToolWheelSC.java`, `emp/client/EmpKeysSC.java`,
новые `emp/client/GuiEmpTabSC.java` (логика вкладки, чтобы правка GuiArmorSC была тонкой), `client/GuiArmorBridgeSC.java` (только ряд вкладок, если нужно).

**Вызывает:** `ItemEmpGunSC.*` (чтение NBT), `EmpNetSC.send`, `EmpNetSC.lastAim`, `EmpSC.suppressedTicks/immuneTicks/rebootCooldownTicks/isSuppressed`, `EmpMathSC.*`.

**Поведение:**
- Вкладка «ПЭ-1» (`EMP_TAB = 10`, ID кнопок с новой базы, не пересекаясь с существующими), когда пушка в руке; без брони мода K открывается сразу на ней (как бур).
- Шапка: заряд/ёмкость, нагрев % и время остывания (`coolSeconds`), режим и цена, куда пойдёт энергия Сифона и место (`lastAim.receiver/receiverRoom`; −1 → «Некуда принимать» красным).
- Режимы — строки как функции бура: галочка (`TOGGLE_MODE`), кнопка назначения клавиши через `ArmorKeyBindsSC` (добавить `EmpMode` в список, ключ конфига `emp_<key>`; модификаторы Ctrl/Shift/Alt, совпадение красным), подсказка: цена, дальность, время по комплектам (таблица `EmpMathSC.baseSeconds`) / набор, перезарядка / скорость, потери, приёмники. Нажатие клавиши режима → `SELECT_MODE`.
- Настройки: зона Точки (`SET_ZONE`), порядок/вкл приёмников (`SET_RECEIVER_ORDER`/`TOGGLE_RECEIVER`), предел (`SET_LIMIT`), предупреждение о перегреве (`SET_WARN`).
- Модули: 5 слотов-кнопок: есть в пушке → «Снять» (`MODULE_REMOVE`), есть в инвентаре → «Поставить» (`MODULE_INSTALL`, слот), итоговые цифры.
- Цель под прицелом из `lastAim` (свежее ~1 с): имя, комплект, «ЭМИ-невосприимчивость: N с», «Нельзя: PvP выключен / чужой приват / свой / ...», заряд цели — только если `chargePct ≥ 0`.
- Вкладка брони (нагрудник или каждая часть): строка «ЭМИ» («подавлено: шлем 6 с» / «невосприимчивость 12 с» / «норма») и кнопка «Аварийная перезагрузка» (`EMERGENCY_REBOOT`, серая при кулдауне). Функции подавленной части — серые с пометкой «ЭМИ».
- `EmpKeysSC.register()`: KeyBinding в «Управлении» (категория `key.categories.sc`, по умолчанию `KEY_NONE`): `key.sc.emp.point/pulse/siphon` (выбор режима), `key.sc.emp.cancel` (`CANCEL_CHARGE`), `key.sc.emp.reboot` (`EMERGENCY_REBOOT`, работает и без пушки).
- `ToolWheelSC`: Shift + колесо с пушкой → `CYCLE_MODE ±1` (выключенные пропускает сервер), подпись над хотбаром `sc.emp.wheel`.

**Тесты (F):** упаковка порядка приёмников (`ItemEmpGunSC.packOrder/unpackOrder`) — уже в ядре; ID кнопок не пересекаются.

## 4. Пакет D — модель, HUD, эффекты, звуки, текстуры

**Файлы:** `emp/client/RenderEmpGunSC.java`, `emp/client/EmpHudSC.java`, `emp/client/EmpFxSC.java` (+ новые `emp/client/*`),
`client/ArmorClientSC.java` (только: «×N расход: N с» рядом со шкалой гелия в `drawGases`; при желании строки ЭМИ под
аварийной строкой), `client/ModelArmorGlowSC.java` (огни: тусклее при `EmpSC.drained`, погашены и искрят при подавлении части),
`resources/.../sounds.json`, `resources/.../sounds/emp/*.ogg`, `resources/.../textures/items/empGun.png, empGunM.png, empModule_{capacity,focus,cooling,efficient,wide}.png`.

**Поведение:**
- `RenderEmpGunSC`: `IItemRenderer` для `ModItems.empGun` (INVENTORY, EQUIPPED, EQUIPPED_FIRST_PERSON, ENTITY) — модель «Сифон-капсула» по `docs/pe1-model-mockup.py` `m_capsule` и `docs/pe1-mockup3d.png`: корпус, капсула в клетке из прутьев, латунные кольца, игла-излучатель, клешни, провод к рукояти. Капсула fullbright (lightmap 240), цвет `EmpMode.rgb()` текущего режима, уровень наполнения = заряд/ёмкость, при потоке Сифона — анимация наполнения; ПЭ-1М — отличие (например, второй ряд колец). Образец IItemRenderer — аддон `SiliconAgeVehicles/.../RenderItemVehicleSVA` (только читать, не править).
- `EmpHudSC` (RenderGameOverlayEvent.Post, ALL): полоска набора над хотбаром (`p.getItemInUseDuration()` / `EmpMathSC.chargeTicks`), «Некуда принимать» (Сифон, `lastAim.receiver < 0`), предупреждение о перегреве, у стрелка при потоке — полоса заряда цели (если `chargePct ≥ 0`) и «принято N EU»; у цели: «Броня подавлена: шлем 6 с» (`EmpSC.suppressedTicks`), «ЭМИ-наведение» мигает со стрелкой на стрелка (`aimedAt`/`aimedBy`, только при `EmpSC.hasSensorChip(p)`), «Энергия утекает!» со стрелкой (`drained/drainedBy`).
- `EmpFxSC`: разбор `EmpNetSC.FX` в ClientTick; голубой/фиолетовый разряд Точки/Импульса (линия частиц или quad-луч в RenderWorldLastEvent, ~6 тиков), поток частиц Сифона цвета режима между стрелком и целью, пока `FX_STREAM_ON` (таймаут 2 с без продления), искры на подавленной цели, вспышка на границе ЭМИ-экрана.
- Звуки в `sounds.json`: `emp.charge, emp.hum, emp.shot, emp.pulse, emp.siphon, emp.overheat, emp.suppress, emp.reboot` (category `player`). Новые .ogg сгенерировать (синтез) или собрать из существующих (`field/zap`, `quarry/beam`, `machine/laser`, `power/on|off`) с `pitch` в sounds.json. Громкость — через `SoundsSC` (учитывать `ConfigSC.soundVolume`).

**Тесты:** визуальная проверка в клиенте (скриншоты слот/1-е/3-е лицо/на земле), отчёт.

## 5. Пакет E — правила сервера, ЭМИ-экран

**Файлы:** `emp/EmpRulesSC.java`, `tileentity/TileEntityFieldGeneratorSC.java`, `inventory/ContainerFieldGeneratorSC.java`,
`inventory/GuiFieldGeneratorSC.java`, `client/FieldRendererSC.java` (по желанию: вспышка оболочки).

**Поведение:**
- `refusal(shooter, target, fire)`: цель-игрок: `!ConfigSC.empAffectsPlayers` → `NO_PLAYERS`; `BladeLogicSC.pvpBlocked` (PvP сервера, команды без friendly fire) → `NO_PVP`; «свой» → `NO_OWN`: та же команда (`isOnSameTeam`), или цель в списке доступа приватного поля, владелец которого — стрелок, или наоборот (уточнить и описать правило); цель или стрелок в чужом приватном поле (`TileEntityFieldGeneratorSC.privateRefusing` для точки цели) → `NO_CLAIM`; `fire == true` → ещё `BladeLogicSC.claimed(shooter, target)` (AttackEntityEvent: FTB Utilities, GriefPrevention…) → `NO_CLAIM`. `!ConfigSC.empEnabled` → `NO_DISABLED`. Мобы: только claims.
- Флаг `F_EMP_SCREEN = 262144` (бит 18) в `TileEntityFieldGeneratorSC`, `ContainerFieldGeneratorSC.FLAG_COUNT` 18 → 19, кнопка во вкладке функций (как `F_NO_MECHANISMS`), подсказка.
- `screenAbsorbs(shooter, target, eu)`: активное поле с флагом, содержит цель (`fieldContains`), не содержит стрелка; платит `eu` из буфера поля (`EmpMathSC.screenCost`: Точка 5 000, Импульс 15 000, Сифон 500 за секунду — TODO(balance)); не хватает EU → не гасит. Возврат — точка пересечения луча с границей поля (приближённо: первая точка луча внутри поля шагом 0.25), иначе null.

**Тесты (F или свои чистые функции):** биты флагов не пересекаются; `FLAG_COUNT` покрывает бит 18; refusal при выключенном PvP.

## 6. Пакет F — тексты, справочник, подсказки, тесты

**Файлы:** `resources/assets/siliconage/lang/ru_RU.lang`, `en_US.lang` (UTF-8, **CRLF**, добавлять в конец блоком),
`emp/EmpTooltipSC.java`, `manual/BookEmpSC.java`, `debug/SelfTestEmpSC.java`, `debug/WorldTestEmpSC.java`.

**Поведение:**
- Все ключи §8 (ru + en), плюс ключи, присланные другими пакетами (через координатора).
- `EmpTooltipSC` (стиль `TooltipSC`: `page()`, `hintShift/hintCtrl`, `wrap`): кратко — что делает, заряд/ёмкость, режим, нагрев, модули; Shift — режимы и цифры по комплектам (таблица `EmpMathSC.baseSeconds`, ×1.25 ПЭ-1М), цены, дальности; Ctrl — как зарядить (Shift+ПКМ по машине, накопители, зарядная плита, зарядники IC2 от HV), из чего сделан, как защититься. Модули — каждый своя подсказка.
- Справочник: в `BookEmpSC.add` статьи «ПЭ-1» (таблица §2, режимы, нагрев/гелий, сифон, модули, ПЭ-1М; `about()` для пушки и модулей — G открывает) и «Защита от ЭМИ» (§7). Цифры — из `EmpMathSC`/`ConfigSC`, не текстом.
- `SelfTestEmpSC.run()`: `durationTicks` по таблице и модификаторам (ПЭ-1М, чип I/II/III, гелий, конфиг, минимум); `pointPieces` по высоте и настройке; `heat` (15/35/3%, убывание, невосприимчивость, Сингулярная, чип); `boilLoopPctPerSecond`/`mergeBoil` (не складываются, продление); `siphonRate`/`siphonLossPct`/`delivered`; `gunHeat`/`overheatWarn`/`coolSeconds`; `ItemEmpGunSC`: заряд/ёмкость с модулем, `step` пропускает выключенные, нельзя выключить все режимы, `pack/unpackOrder`; `EmpStateSC` write/read и `immune()` только после конца; `EmpNetSC` дискриминаторы 7..10 не заняты `ArmorNetSC`; ключи языка для всех `EmpMode/EmpModule` есть в обоих файлах; рецепты ПЭ-1 и модулей существуют (после A).
- `WorldTestEmpSC` (по образцу `WorldTestConverterSC`): FakePlayer-цель в Квант-комплекте с гелием и стрелок; `applyHit` Точкой по шлему → `isSuppressed(0)`, `active(NIGHT_VISION)` = false, `AIR` работает; Импульс → все части, длительность 11 с ×0.8 (гелий); невосприимчивость после конца; нагрев и выкипание идут; `clearSuppression`; сифон (через A) переносит EU с потерями 25%.

## 7. Порядок и слияние

Все шесть пакетов идут параллельно (ядро уже собрано). Координатор после всех: сливает ключи языка от A–E в F
(или отдаёт F), проверяет сборку, `-Dsc.selftest=true`, `-Dsc.worldtest=true`, ручной прогон в клиенте, затем
`SESSIONS.md` + `CHANGELOG.md` «Unreleased».

Открытые вопросы пользователю: (1) ветка «Страж» у брони не существует — взять ур. 5 Б «Гравитационный купол»?
(2) аргон перезагрузки 100 мБ, Нано — без аргона? (3) Фокус для Точки +16 блоков? (4) «Импульсный излучатель» в рецепте
= существующий «Импульсный эмиттер». (5) Сенсорный чип — в нагруднике (у мода все чипы там).

## 8. Ключи языка (F добавляет, остальные используют)

Предметы: `item.siliconage.empGun.name` (ПЭ-1 «Подавитель электроники»), `item.siliconage.empGunM.name` (ПЭ-1М «Подавитель электроники»),
`item.siliconage.empModule.{capacity,focus,cooling,efficient,wide}.name` (Модуль ПЭ-1: Ёмкость / Фокус / Охлаждение / Эффективный сифон / Широкий канал).

Режимы: `sc.emp.mode.{point,pulse,siphon}` (Точка / Импульс / Сифон), `sc.emp.mode.{point,pulse,siphon}.desc`.
Модули: `sc.emp.module.{key}.desc`.

A (action bar / чат): `sc.emp.nocharge` (Мало заряда: нужно %s EU), `sc.emp.overheat` (ПЭ-1 перегрет: %s с),
`sc.emp.overheat.warn` (ПЭ-1: перегрев через 2 с), `sc.emp.cooldown` (Импульс перезаряжается: %s с), `sc.emp.modeoff` (Режим выключен в меню K),
`sc.emp.noreceiver` (Некуда принимать), `sc.emp.cancelled` (Набор отменён), `sc.emp.refuse.{1..5}` (Нельзя: PvP выключен / чужой приват / свой /
ПЭ-1 отключён на сервере / ПЭ-1 не действует на игроков), `sc.emp.absorbed` (Выстрел погашен ЭМИ-экраном), `sc.emp.reflected` (Импульс отражён «Стражем»!),
`sc.emp.immune` (Цель невосприимчива: %s с), `sc.emp.hit` (%s: подавлено %s на %s с), `sc.emp.noarmour` (Цель без брони мода: Медлительность),
`sc.emp.siphon.stop.{released,range,cover,full,limit,empty,overheat,screen,refused}`, `sc.emp.module.{installed,removed,already,missing,full}`, `sc.emp.wheel` (Режим ПЭ-1: %s).

B: `sc.emp.warn.flightcut` (Полёт отключён ЭМИ - плавный спуск), `sc.emp.suppressed.you` (Ваша броня подавлена ЭМИ: %s на %s с),
`sc.emp.reboot.{done,cooldown,noargon,nocharge,nothing}`.

C: `sc.empgui.tab` (ПЭ-1), `sc.empgui.{charge,heat,cool,mode,price,receiver,noreceiver,modes,settings,zone,zone.0..3,recv.0..2,limit,limit.full,limit.pct,warn,modules,module.install,module.remove,module.none,target,target.none,target.suit,target.immune,target.charge,target.refuse}`,
`sc.empgui.tip.{point,pulse,siphon}`, `sc.armorgui.emp` (ЭМИ), `sc.armorgui.emp.{suppressed,immune,normal,reboot,reboot.cd,mark}`,
`key.sc.emp.{point,pulse,siphon,cancel,reboot}`.

D: `sc.emp.hud.{charge,noreceiver,overheat,suppressed,boil,aimed,drain,target,received}` (например `sc.emp.hud.boil` = ×%s расход: %s с; `sc.emp.hud.suppressed` = Броня подавлена: %s %s с).

E: ключи флага по образцу существующих флагов поля в `GuiFieldGeneratorSC` (E называет точные ключи в отчёте), текст «ЭМИ-экран» + подсказка «лучи и поток сифона ПЭ-1 снаружи гаснут на границе поля; EU поля за каждое поглощение».

F (справочник/подсказки): `sc.manual.emp.*`, `sc.tooltip.emp.*` — на усмотрение F.
