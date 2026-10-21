# Silicon Age — план работ по недоработкам 2026-10-20, вариант ★ (все 74 пункта)

Источник: `docs/audit-2026-10-20-nedorabotki.md`. Решение пользователя (2026-10-21): «все ★». Для каждого пункта ниже — ровно вариант ★: что менять, где, числа, тексты (ключ = ru | en), проверки. Пути — от корня проекта; `src/main/java/com/sc/…` сокращено до пути класса.

## Общие правила для исполнителей

- Minecraft 1.7.10, Forge 10.13.4.1614, исходники на уровне Java 6: без лямбд, `<>`, try-with-resources и `switch` по строкам; имена MCP. Стиль — как в соседнем коде (английские комментарии, `TODO(...)`, коды пунктов в комментариях).
- Никогда не вызывать методы `@SideOnly(CLIENT)` из общего/серверного кода; FakePlayer не получает пакеты (не слать ему сообщений-пакетов).
- Аддон `C:/Users/Aleksandr/Documents/MineMod/SiliconAgeVehicles` не трогать. Файлы не удалять. Папку `.minecraft` и `javaw` пользователя не трогать.
- lang-файлы `ru_RU.lang` / `en_US.lang` — CRLF; кавычки в значениях: ru „…“, en “…” (или уже принятые в строке ASCII), никогда « » (SelfTestSC.langQuotes). Одинаковое число и порядок аргументов в ru и en. Новые ключи — в оба файла, рядом с родственными.
- У каждого нового предмета/блока — подсказка TooltipSC сразу (в этом плане новых предметов нет). Рецепты — только ванильные предметы и предметы мода.
- NBT, meta, enum — только дописывать (старые миры должны читаться): новый ключ NBT с разумным значением при отсутствии; новые константы enum/кодов — в конец.
- Новые настройки конфига — только через координатора (ConfigSC / SubstrateConfigSC / ConfigSyncSC уже правлены, см. ниже), с ru/en комментариями.
- Не коммитить. Резервная копия до правок уже есть: `C:/Users/Aleksandr/Documents/MineMod/backups/2026-10-21_2205_before-audit-20261020-star/` (jar, src, build.gradle).
- Сборка: `T=/c/Users/Aleksandr/Documents/MineMod/.build-tools; export JAVA_HOME=$T/jdk8/jdk8u504-b01 PATH=$T/jdk8/jdk8u504-b01/bin:$T/gradle/gradle-2.14/bin:$PATH; cd C:/Users/Aleksandr/Documents/MineMod/SiliconAge; gradle clean build --offline -q` («Read timed out»/404 — не ошибка; runServer не запускать). Проверка реобфускации: `unzip -p build/libs/SiliconAge-0.2.2.jar com/sc/block/BlockOreSC.class | grep -a -c field_151576_e` = 1.
- Пользователь играет по LAN с другом: проверки владельца и «чужого» игрока — с этим в виду (на клиенте UUID локального игрока может не совпадать с серверным — сравнивать по имени или брать ответ сервера).
- CHANGELOG.md («Unreleased») и SESSIONS.md ведёт тот, кто сводит работу; в списках файлов ниже они и lang-файлы не перечислены.

## Что уже сделал координатор (общие файлы — исполнителям их не править, кроме указанного в пунктах)

- `util/ConfigSC.java`: `balance.quarryEnergy` (float 1, 0.1–10) и `balance.quarryChunkLoading` (true) — КР-8; раздел `generators`: `generatorOutput`, `generatorFuel` (float 1, 0.1–10), `combustionFuels` (String[], по умолчанию `defaultCombustionFuels()`: diesel=16, fuel=24, biodiesel=20, ethanol=12, bioethanol=12, biofuel=10, crudeoil=5, oil=5, ic2biogas=4, hootch=10, rocket_fuel=24, fire_water=20, coal=12, biomass=5) с помощниками `combustionEuPerMb(fluid)`, `combustionFuelNames()`, `parseFuels(String[])` — ГН-6; `stamp()` учитывает String[]; комментарии: balance двуязычный, русская половина у всех одноязычных (СТ-5 сделан полностью), machineEnergy — порог ~0,87 с 4 экономайзерами (ГН-2), bladeExecutePlayers — «Добивание» (ОР-2).
- `handler/ConfigSyncSC.java`: передача клиенту всех public static non-final `String[]` полей ConfigSC (сейчас `combustionFuels`) — последними в пакете, после измерений руд (клиент/сервер 0.2.2 совместимы в обе стороны); на выходе с сервера — свои значения обратно.
- `dim/SubstrateConfigSC.java`: правила раздела dimension (ПМ-6): `stormChanceMultiplier` (1, 0–5), `hazeChanceMultiplier` (1, 0–4), `signalChanceMultiplier` (1, 0–6), `wardenHealthMultiplier` (1, 0.1–10), `wardenLootCooldownDays` (3, 0–30), `backgroundRadiationMultiplier` (1, 0–10); раздела embassy (ПС-8): `flightPriceMultiplier` (1, 0.1–10), `flightsPerDay` (2, 1–20). Это «правила» — SubstrateNetSC отправляет их клиентам сам.
- `util/OwnerSC.java` (новый общий помощник для ЭН-3, на него опираются ЭН-4 и МС-1): владелец = имя + UUID; `set(String, World)`, `set(EntityPlayer)`, `matches(EntityPlayer, TileEntity)` (по UUID; по имени у старых блоков, первое действие дописывает UUID; переименование обновляет имя; на клиенте — только по имени), `nameIs`, `profile()` (для BreakEvent-проверок приватов: сохранённый UUID → игрок в сети → кэш профилей online-сервера без веб-запроса → offline-UUID), `read/write(nbt, nameKey, idKey)`.
- Самотесты под новые правила: число правил SubstrateNetSC 20 → 28 (SelfTestCombatSC, SelfTestFinaleSC, SelfTestHazardSC, SelfTestEmbassySC), список RULES в SelfTestSubstrateSC, RULES/DEFAULTS раздела embassy в SelfTestEmbassySC (+flightPriceMultiplier, flightsPerDay); проверка config sync в SelfTestSC учитывает новый хвост пакета и проверяет передачу `combustionFuels`.
- Сборка после этих правок прошла (`gradle clean build --offline -q`), проверка реобфускации = 1.

## Пересечения файлов (править аккуратно, сливая с соседними пунктами)

| Файл | Пункты |
|---|---|
| `debug/SelfTestSC.java` | ЭН-1, ЭН-3, ЭН-5, ЭН-8, ЭН-9, МШ-1, МШ-5, МШ-6, МШ-7, МШ-8, ГН-2, ГН-5, ГН-6, КР-3, КР-5, БР-1, БР-2, БР-3, ОР-5, ОР-6, ОР-7, МС-4, СТ-1, СТ-2 |
| `tileentity/TileEntityQuarrySC.java` | КР-2, КР-5, КР-6, КР-7, КР-8, КР-9 |
| `README.md` | ГН-6, КР-8, ПМ-2, ПМ-6, ПС-8 |
| `tileentity/TileEntityGeneratorSC.java` | ГН-2, ГН-3, ГН-4, ГН-5, ГН-6 |
| `debug/SelfTestEmbassySC.java` | ПС-3, ПС-5, ПС-7, ПС-8 |
| `debug/SelfTestHazardSC.java` | ПМ-3, ПМ-4, ПМ-6, МБ-2 |
| `inventory/GuiGeneratorSC.java` | МШ-3, ГН-3, ГН-5, ГН-6 |
| `manual/BookContent.java` | ЭН-9, ГН-6, КР-5, СТ-3 |
| `settle/client/GuiTradeSC.java` | ПС-3, ПС-4, ПС-5, ПС-8 |
| `tileentity/TileEntityBridgeControllerSC.java` | ЭН-3, МС-1, МС-4, МС-5 |
| `tileentity/TileEntityMachineSC.java` | МШ-1, МШ-4, МШ-5, МШ-6 |
| `docs/plan-substrate.md` | МБ-2, МБ-3, МБ-4 |
| `SCMod.java` | МШ-7, МШ-8, СТ-2 |
| `inventory/GuiQuarrySC.java` | КР-5, КР-6, КР-8 |
| `ShieldEventHandler.java` | БР-1, БР-3 |
| `block/BlockBridgeSC.java` | ЭН-3, МС-1 |
| `block/BlockMachineSC.java` | МШ-2, КР-4 |
| `block/BlockQuarrySC.java` | КР-1, КР-4 |
| `client/GuiBridgeControllerSC.java` | МС-1, МС-5 |
| `embassy/FlightRunSC.java` | ПС-3, ПС-8 |
| `emp/EmpGunLogicSC.java` | ОР-1, ОР-3 |
| `init/ModRecipesCrafting.java` | ЭН-8, КР-3 |
| `inventory/GuiMachineSC.java` | МШ-3, МШ-5 |
| `inventory/GuiSingularSC.java` | ГН-3, ГН-4 |
| `inventory/GuiTokamakXVSC.java` | ГН-3, ГН-4 |
| `item/ArmorLogicSC.java` | БР-1, БР-2 |
| `item/BladeSingularSC.java` | ОР-2, ОР-7 |
| `item/ItemWrenchSC.java` | КР-7, МС-1 |
| `item/SingularProgressSC.java` | БР-3, ОР-7 |
| `machine/MachineRecipe.java` | МШ-7, МШ-8 |
| `manual/BookWorldSC.java` | ПМ-6, МБ-4 |
| `mob/SpawnDirectorSC.java` | МБ-2, МБ-3 |
| `radiation/RadiationSC.java` | ГН-1, БР-2 |
| `tileentity/TileEntityConduitBundleSC.java` | МС-2, СТ-1 |
| `tileentity/TileEntityEnergyConverterSC.java` | ЭН-3, ЭН-4 |
| `tileentity/TileEntityWirelessSC.java` | ЭН-3, ЭН-4 |
| `util/SingularLevel.java` | БР-3, ОР-7 |

Строки lang, которые правят несколько пунктов: `sc.manual.machine.matter_compressor` и `sc.gui.comp.hint.2` — делает МШ-1 (МШ-9 их не трогает); `sc.machine.tooltip.howto` — МШ-9 (с учётом МШ-2); `sc.manual.world.elites.2` — ОР-2 и МБ-5 (окончательный текст — в МБ-5); `SelfTestHazardSC.MESSAGE_ARGS` — ПМ-3 и ПМ-4.

Рекомендуемый порядок внутри общих файлов: ЭН-3 → ЭН-4 → МС-1 (владельцы); МШ-1 → МШ-4 → МШ-9; ГН-2 → ГН-6 (множители генератора); КР-8 → КР-2 (чанки); ОР-2 → МБ-5.

## ЭН — Энергосеть (9)

### ЭН-1. Красная кнопка «линия сильнее входа» не замечает генераторы, накопители и кабели IC2

**★** А. Учитывать в проверке источники IC2 (MFSU, генераторы, трансформаторы: их уровень выхода) и кабели IC2 (уровень по номиналу кабеля); код IC2 — в отдельном классе, который загружается только при IC2.

**Что сделать.**

1. energy/Tier.java: чистый `public static Tier ofCapacity(double eu)` — наименьший уровень, у которого getVoltage() >= eu; eu <= 0 → null; больше SV → SV.
2. Новый класс energy/Ic2LineSC.java (final, private-конструктор, импорты ic2.api.energy.tile.*; вызывается только под Loader.isModLoaded(Reference.IC2_MODID), как NetViewNetSC.Ic2): `static Tier tierTowards(TileEntity te, TileEntity receiver, ForgeDirection sideOfTe)`, всё в try/catch(RuntimeException) → null:
   - te instanceof IEnergyConductor и ((IEnergyEmitter) te).emitsEnergyTo(receiver, sideOfTe) → Tier.ofCapacity(getConductorBreakdownEnergy() − 1) (IC2 отдаёт номинал+1: олово 33 → LV, медь 129 → MV, золото 513 → HV, HV-кабель 2049 → EV, стекловолокно 8193 → IV);
   - иначе te instanceof IEnergySource и emitsEnergyTo(receiver, sideOfTe) → Tier.fromIc2Tier(getSourceTier()) (MFSU, генераторы, трансформаторы — их текущий выход).
3. energy/TileEntityEnergyBase.lineTier(): после ветки `te instanceof TileEntityEnergyBase` добавить `else if (te != null && Loader.isModLoaded(Reference.IC2_MODID) && !(te instanceof TileEntityEnergyBase) && !(te instanceof com.sc.tileentity.TileEntityConduitBundleSC)) t = Ic2LineSC.tierTowards(te, this, dir.getOpposite());` (связка мода сама реализует IEnergyConductor — её уже учитывает своя ветка). Метод работает и на клиенте: блоки IC2 есть там же (уровень трансформатора IC2 на клиенте может отставать — допустимо).
4. Ничего в NBT и сети не меняется; GuiPowerSC и GuiSingularStationSC уже берут lineTooStrong()/lineTier().
5. Справочник: в sc.manual.energy.power.2 уточнить, что учитываются и блоки IC2.

**Файлы:** `energy/Tier.java`, `energy/TileEntityEnergyBase.java`, `energy/Ic2LineSC.java (новый)`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.manual.energy.power.2 = в нынешнем тексте «Если рядом кабель или источник выше входа блока» → «Если рядом кабель или источник выше входа блока (мода или IC2)» | "With a cable or source above the block's input nearby" → "With a cable or source (the mod's or IC2's) above the block's input nearby"

**Проверки:** SelfTestSC (раздел энергии): Tier.ofCapacity(32)==LV, (128)==MV, (512)==HV, (2048)==EV, (8192)==IV, (16384)==QV, (32768)==XV, (1e6)==SV, (0)==null. Ручная проверка с IC2: машина LV вплотную к выходу MFSU, к генератору IC2 и к стекловолоконному кабелю — кнопка питания красная, подсказка «Линия IV/EV выше входа LV», включение двойным нажатием.

### ЭН-2. Справочник обещает «взрыва нет» при прямом контакте, но с IC2 машина взрывается

**★** А. Переписать строку (ru/en) так, чтобы она описывала оба случая: без IC2 по прямому контакту энергия не идёт и взрыва нет, с IC2 машина взрывается, как от кабеля.

**Что сделать.**

Только текст. Заменить значение sc.manual.energy.intro.1 в обоих lang-файлах целиком (CRLF, кавычки „…“ / “…”, без « »). Начало строки (про кабели, трубы, пневмотрубы) сохранить как есть, меняется последнее предложение. Код не трогать.

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- sc.manual.energy.intro.1 = Кабель соединяется с соседними кабелями, машинами и генераторами (с IC2 - и с его блоками). Труба - с любым хранилищем жидкости, пневмотруба - с инвентарями. Генератор или накопитель, поставленный вплотную к машине выходной гранью, питает её и без кабеля. Если напряжение выше, чем выдерживает машина: без IC2 по прямому контакту энергия просто не идёт (взрыва нет), с IC2 машина взрывается, как от кабеля. | A cable connects to neighbouring cables, machines and generators (and IC2 blocks when IC2 is installed). A pipe connects to any fluid container, the pneumatic tube to inventories. A generator or storage placed right against a machine with its output face powers it without a cable. If the voltage is higher than the machine takes: without IC2 nothing flows through direct contact (no explosion); with IC2 the machine blows up, as from a cable.

**Проверки:** SelfTestSC.langQuotes и проверка ключей ru/en проходят (оба файла, одинаковое число аргументов — их 0).

### ЭН-3. Владелец беспроводных блоков, преобразователя, зарядной плиты, моста и генератора поля — по нику, а не по UUID

**★** А. Как у карьера: хранить UUID владельца рядом с именем (имя обновлять, когда владелец что-то делает с блоком), проверять по UUID, а по нику — только у старых блоков без UUID (при первом действии игрока с тем же ником дописывать ему UUID). Приваты других модов спрашивать с сохранённым UUID, у моста — ещё и через кэш профилей, как у поля. Списки друзей моста и доступа поля оставить по именам.

**Что сделать.**

Координатор уже добавил общий помощник util/OwnerSC.java (имя + UUID, matches(p, te) с переименованием и дописыванием UUID на сервере, на клиенте — только по имени, profile() для BreakEvent-проверок: сохранённый UUID → игрок в сети → кэш профилей online-сервера без веб-запроса → offline-UUID имени, read/write(nbt, nameKey, idKey)). Перевести на него 5 тайлов; во всех местах этих файлов, где `owner` — строка (equals/isEmpty/length/equalsIgnoreCase/передача в сообщения), заменить на owner.name()/owner.isEmpty()/owner.matches(...):
1. TileEntityWirelessSC: `private final OwnerSC owner = new OwnerSC();` getOwner() → owner.name(); setOwner(String) → owner.set(name, worldObj); новый setOwner(EntityPlayer) → owner.set(p); allowed(p) → `owner.isEmpty() || owner.matches(p, this) || p.capabilities.isCreativeMode`; NBT: owner.read(nbt, "Owner", "OwnerId") / owner.write(nbt, "Owner", "OwnerId"). BlockWirelessSC.onBlockPlacedBy (~стр. 112) → w.setOwner((EntityPlayer) placer) (FakePlayer → без владельца, как сейчас).
2. TileEntityEnergyConverterSC: то же, ключи "ConvOwner"/"ConvOwnerId" (писать, как сейчас, только если владелец есть). BlockEnergyConverterSC (~стр. 98) → setOwner(EntityPlayer).
3. TileEntityChargePadSC: ключи "PadOwner"/"PadOwnerId"; mayCharge: `!ownerOnly || owner.isEmpty() || owner.matches(p, this)`; команда — по owner.name(). BlockChargePadSC (~стр. 44) → setOwner(EntityPlayer).
4. TileEntityBridgeControllerSC: ключи "Owner"/"OwnerId"; isOwner(p) → `p == null || owner.matches(p, this)`; ownerless() → owner.isEmpty(); claim(p) → owner.set(p); setOwner(String) (стр. ~155) → owner.set(o, worldObj); claimProfile(p) для непустого владельца → owner.profile() (вместо offline-UUID из ника — теперь сохранённый UUID / игрок в сети / кэш профилей). Друзья (isFriend) — по именам без изменений. BlockBridgeSC.onBlockPlacedBy → setOwner(EntityPlayer).
5. TileEntityFieldGeneratorSC: ключи "Owner"/"OwnerId" (проверить фактический ключ имени в read/writeToNBT); isOwner(p) → owner.matches(p, this); allowedName(n) → owner.nameIs(n) || access…; ownerProfile() → owner.profile() (метод 648-669 убрать или оставить обёрткой). Если matches() сменил имя (переименование) — вызвать changed(), чтобы запись зоны в ZoneRegistry получила новое имя (сравнить owner.name() до и после). Список доступа — по именам. BlockFieldGeneratorSC (~стр. 235) → setOwner(EntityPlayer).
Совместимость: старые блоки без UUID проверяются по нику (без учёта регистра), первое действие владельца дописывает UUID. Ключи имени не меняются; новый ключ UUID только добавляется. Описания блоков в пакетах описания могут нести UUID — на клиенте он не используется.
Связи: ЭН-4 (allowed() тех же тайлов) и МС-1 (trusted/isOwner моста) опираются на эти методы — делать ЭН-3 раньше или аккуратно слить.

**Файлы:** `tileentity/TileEntityWirelessSC.java`, `tileentity/TileEntityEnergyConverterSC.java`, `tileentity/TileEntityChargePadSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `tileentity/TileEntityFieldGeneratorSC.java`, `block/BlockWirelessSC.java`, `block/BlockEnergyConverterSC.java`, `block/BlockChargePadSC.java`, `block/BlockBridgeSC.java`, `block/BlockFieldGeneratorSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSC: чистый тест OwnerSC: read из {Owner:"Alex"} → name "Alex", id ""; write без UUID не пишет "OwnerId"; read из {Owner:"Alex", OwnerId:"<uuid>"} → id сохранён; set("Bob", null) → nameIs("bob") true; set((EntityPlayer) null) → isEmpty; matches(null, null) false. Ручная проверка по LAN: друг не открывает и не ломает мои блоки; мой старый блок после перезахода остаётся моим, в NBT появился OwnerId.

### ЭН-4. Shift + ПКМ бронёй или оружием забирает энергию из чужого или выключенного беспроводного блока и преобразователя

**★** А. У беспроводных блоков и преобразователя проверять владельца: чужому — сообщение «не ваш блок» (ключи sc.chat.wl.notmine и sc.conv.msg.owneronly уже есть) и отказ. Выключенный блок энергию не отдаёт, как накопитель.

**Что сделать.**

1. TileEntityWirelessSC: @Override canItemCharge(EntityPlayer p): `if (!allowed(p)) { p.addChatComponentMessage(new ChatComponentTranslation("sc.chat.wl.notmine", getOwner())); return false; } return super.canItemCharge(p);` и @Override extractForItemCharging(int max): `return switchedOn() ? super.extractForItemCharging(max) : 0;` (как TileEntityEnergyStorageSC, стр. ~698).
2. TileEntityEnergyConverterSC: то же с ключом "sc.conv.msg.owneronly" (аргумент — владелец) и switchedOn() (у преобразователя он переопределён, с кэшем красного камня).
3. Вызывающие (ItemArmorSC ~512, ItemBladeSC ~486, ItemDrillSC ~458, ItemWeaponSC ~154, emp/ItemEmpGunSC ~401) уже спрашивают canItemCharge до извлечения и только на сервере — их не трогать.
4. Блок без владельца (поставлен автоматикой) и творческий режим — как allowed() сейчас.
Зависит от ЭН-3 (allowed() переводится на UUID) — правки в тех же файлах.

**Файлы:** `tileentity/TileEntityWirelessSC.java`, `tileentity/TileEntityEnergyConverterSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет (используются sc.chat.wl.notmine и sc.conv.msg.owneronly).

**Проверки:** Ручная по LAN: друг Shift + ПКМ бронёй по моему передатчику и преобразователю — сообщение «Это блок игрока …», заряд не идёт; мой выключенный передатчик отдаёт 0 EU (сообщение «заряжено 0»). WorldTestWirelessSC (по желанию): шаг «чужой заряжает предмет» — canItemCharge false, энергия блока не изменилась.

### ЭН-5. Модули ёмкости в накопителях QV и XV почти ничего не дают

**★** А. Не принимать модуль ёмкости, если после него ёмкость упрётся в предел (сейчас у XV больше 1 модуля, у QV больше 5, с учётом storageCapacity из конфига), в том числе когда кладут стопку целиком. В подсказке и справочнике указать предел накопителя.

**Что сделать.**

1. TileEntityEnergyStorageSC:
   - `public static int maxCapacityUpgrades(Tier t)`: base = capacityOf(t); base >= Integer.MAX_VALUE → 0; иначе наименьшее n >= 1, при котором base * (100 + 25n) / 100 >= Integer.MAX_VALUE (последний модуль, который ещё что-то прибавляет), но не больше UpgradeType.MAX_EFFECTIVE (16). При storageCapacity 1: XV → 1, QV → 5, IV и ниже → 16, SV → 0. Нестатический maxCapacityUpgrades() = для getTier().
   - `public int capacityUpgradeRoom(int slot)`: maxCapacityUpgrades() минус модули ENERGY_STORAGE в остальных слотах модулей (не меньше 0).
   - acceptsUpgradeHere(s): для ENERGY_STORAGE отказ, если upgradeCount-сумма (без MAX_EFFECTIVE-обрезки) >= maxCapacityUpgrades() (заменяет проверку «только SV»).
2. ContainerEnergyStorageSC, слоты модулей (~стр. 92-110): слот реализует SlotMergeSC.Limited: limitFor(stack) = модуль ёмкости ? capacityUpgradeRoom(index) + (в слоте уже модуль ёмкости ? его stackSize : 0) : 64; getSlotStackLimit(): то же для стопки в слоте или в курсоре (контейнер хранит InventoryPlayer, курсор — playerInv.getItemStack()). Тогда Shift-клик (SlotMergeSC.mergeValid), клик рукой (vanilla slotClick ограничивает getSlotStackLimit) и обмен с хотбаром (refuseHotbarSwap) кладут не больше разрешённого.
3. Уже стоящие сверх предела модули в старых мирах не выбрасываются (getMaxEnergyStored и так обрезает) — можно вынуть.
4. GuiEnergyStorageSC (~стр. 266-268, подсказка модулей): после hint.2 строка sc.storage.upgrades.hint.cap с maxCapacityUpgrades() этого накопителя (на клиенте storageCapacity приходит с сервера).
5. Справочник: дописать предел в sc.manual.energy.storage2.2.
Связь с ЭН-6 (тексты модуля энергонакопителя) — только lang, без пересечения кода.

**Файлы:** `tileentity/TileEntityEnergyStorageSC.java`, `inventory/ContainerEnergyStorageSC.java`, `inventory/GuiEnergyStorageSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.storage.upgrades.hint.2 = §7Накопитель: +25% ёмкости за каждый, до предела ~2,1 млрд EU | §7Energy Storage: +25% capacity each, up to the ~2.1 billion EU ceiling
- sc.storage.upgrades.hint.cap = §7Модулей ёмкости в этом накопителе - не больше %s | §7Capacity upgrades in this storage: %s at most
- sc.manual.energy.storage2.2 = дописать в конец: « Ёмкость накопителя не больше ~2,1 млрд EU: модуль ёмкости, который упёрся бы в этот предел, не ставится (по умолчанию у QV - до 5 модулей, у XV - 1, у SV - ни одного).» | append: " A storage never holds more than ~2.1 billion EU: a capacity upgrade that would hit that ceiling doesn't go in (by default QV takes up to 5, XV 1, SV none)."

**Проверки:** SelfTestSC: при storageCapacity == 1 (иначе info): maxCapacityUpgrades(XV)==1, (QV)==5, (IV)==16, (SV)==0; тестовый тайл XV с 1 модулем в слоте — acceptsUpgradeHere(модуль ёмкости)==false, без модулей — true; capacityUpgradeRoom на пустом XV == 1. Ручная: Shift-клик стопкой из 16 модулей в XV — встаёт 1.

### ЭН-6. Подсказки модулей «Трансформатор» и «Модуль энергонакопителя» описывают только машину и генератор поля

**★** А. Расписать в подсказке по Shift действие в каждом блоке, как у форсажа; в «Куда ставится» добавить преобразователь (ключ sc.book.modules.w.converter уже есть).

**Что сделать.**

1. lang: переписать sc.upgrade.tooltip.transformer и sc.upgrade.tooltip.energy_storage (это текст по Shift в ItemUpgradeSC.addInformation) — тексты ниже.
2. item/ItemUpgradeSC.where(stack): после проверки генератора поля добавить `if (com.sc.tileentity.TileEntityEnergyConverterSC.isModule(stack)) sb.append(", ").append(Lang.tr("sc.book.modules.w.converter"))` (с той же логикой запятых). Тогда «Куда ставится» у трансформатора, универсального трансформатора и модуля энергонакопителя называет и преобразователь.
Числа сверить с кодом перед записью: генератор и накопитель поднимают выход не выше Tier.outputRaiseCeiling() (XV); модуль энергонакопителя: +10 000 EU буфера машине/генератору/полю (TileEntityGeneratorSC ~1632), +25% ёмкости накопителю, в преобразователе оба буфера x4 и трансформатор +1 уровень EU (TileEntityEnergyConverterSC, модули).

**Файлы:** `item/ItemUpgradeSC.java`

**Тексты (ключ = ru | en):**

- sc.upgrade.tooltip.transformer = Машина и генератор поля: принимают напряжение на уровень выше за каждый. Генератор и накопитель: выдают на уровень выше за каждый (не выше XV) - проверьте кабель на выходе. Преобразователь: +1 уровень EU и выше пропускная способность | Machine and field generator: take one tier higher voltage each. Generator and storage: give one tier higher each (XV at most) - check the cable at the output. Energy Converter: +1 EU tier and more throughput
- sc.upgrade.tooltip.energy_storage = Машина, генератор и генератор поля: +10 000 EU к буферу за каждый. Накопитель: +25% ёмкости за каждый (до предела ~2,1 млрд EU). Преобразователь: оба буфера x4 | Machine, generator and field generator: +10,000 EU of buffer each. Storage: +25% capacity each (up to the ~2.1 billion EU ceiling). Energy Converter: both buffers x4

**Проверки:** SelfTestSC.langQuotes проходит; ручная: подсказка трансформатора по Shift — три блока, «Куда ставится: машины, генераторы, накопители, генератор поля, преобразователь энергии».

### ЭН-7. Снятые трансформатор и беспроводные блоки помнят режим и заряд, но подсказка этого не показывает

**★** А. Показывать в подсказке «Режим: повышающий» у трансформатора и «Заряд: N EU» у передатчика, приёмника и транслятора, как у накопителя.

**Что сделать.**

1. block/ItemBlockTransformerSC.addInformation: после строки граней, если stack.hasTagCompound() && getTagCompound().getBoolean("StepUp") — строка Lang.tr("sc.transformer.tooltip.stepup") (ключ уже пишет BlockTransformerSC.getDrops).
2. block/ItemBlockWirelessSC.addInformation: если в NBT есть "EnergySC" > 0 (TileEntityWirelessSC.writeToItem) — строка "§7" + Lang.tr("sc.wl.tooltip.charge", fmt(energy)) (число с пробелами, как ItemBatterySC.fmt — можно скопировать приватный помощник) для всех трёх видов, включая квантовый транслятор (перед return в его ветке).
NBT не меняется.

**Файлы:** `block/ItemBlockTransformerSC.java`, `block/ItemBlockWirelessSC.java`

**Тексты (ключ = ru | en):**

- sc.transformer.tooltip.stepup = §eРежим: повышающий | §eMode: step-up
- sc.wl.tooltip.charge = Заряд: %s EU | Charge: %s EU

**Проверки:** Ручная: снять ключом трансформатор в повышающем режиме — в подсказке «Режим: повышающий»; снять заряженный передатчик — «Заряд: … EU». SelfTestSC: ключи есть в обоих языках с одним аргументом (общая проверка).

### ЭН-8. Рецепт запутанного кристалла молча сжигает заряд квантовой ячейки

**★** А. Принимать только разряженную ячейку (рецепт не совпадает, если в ячейке есть заряд), как CarryRecipe.EMPTY. В подсказке кристалла и в справочнике написать, что ячейка должна быть пустой.

**Что сделать.**

1. init/ModRecipesCrafting: новый static final класс `EmptyBatteryRecipe extends ShapedOreRecipe` (рядом с CarryRecipe): matches(grid, world) = super.matches && для каждой стопки сетки с ItemBatterySC — ItemBatterySC.chargeOf(s) == 0. Чистый помощник `static boolean allEmpty(ItemStack[] stacks)` для теста. Ингредиенты переводить в имена словаря руд так же, как OreRecipes.shaped (OreRecipes.oreName(stack) для каждого ItemStack; квантовую ячейку оставить точной стопкой ModItems.battery meta 4).
2. Рецепт кристалла (стр. ~568-570) регистрировать через GameRegistry.addRecipe(new EmptyBatteryRecipe(...)) вместо OreRecipes.shaped.
3. item/ItemEntangledCrystalSC.addInformation (ветка целого кристалла, pair == 0): в howto (Ctrl) дописать "\n" + Lang.tr("sc.crystal.tooltip.craft").
4. Справочник: дописать предложение в sc.manual.energy.quantum.1.

**Файлы:** `init/ModRecipesCrafting.java`, `item/ItemEntangledCrystalSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.crystal.tooltip.craft = Крафт: квантовая ячейка в рецепте должна быть разряжена - её заряд пропал бы | Crafting: the quantum cell in the recipe must be empty - its charge would be lost
- sc.manual.energy.quantum.1 = дописать в конец: « Квантовая ячейка в рецепте кристалла должна быть пустой: с зарядом рецепт не складывается.» | append: " The quantum cell in the crystal's recipe must be empty: a charged one doesn't make the recipe."

**Проверки:** SelfTestSC: EmptyBatteryRecipe.allEmpty({пустая ячейка, жемчуг}) == true, ({ячейка с зарядом 1}) == false; рецепт кристалла найден среди CraftingManager как EmptyBatteryRecipe. Ручная: заряженная ячейка в сетке — результата нет, разряженная — кристалл.

### ЭН-9. Режимы аккумулятора молча не заряжают броню и инструменты выше его уровня

**★** А. Добавить в подсказку строку «Заряжает предметы уровня до X» и абзац в справочник. При включении режима, если в выбранной цели нет ни одного подходящего по уровню предмета, писать в чат, что для брони уровня Y нужен аккумулятор не ниже Y.

**Что сделать.**

1. item/ItemChargeSC: чистый по смыслу `public static Tier requiredTier(ItemStack s)` — тот же разбор, что charge(): ItemArmorSC → suit.chargeTier, ItemBladeSC/ItemDrillSC → type.chargeTier, ItemWeaponSC → type.tier, ПЭ-1 → EmpMathSC.CHARGE_TIER, ключ → tierOf(s).chargeTier, пульт моста → tierOf(s); IC2-предмет (в классе Ic2): Tier.fromIc2Tier(((IElectricItem) item).getTier(s)); прочее → null (не заряжается).
2. item/ItemBatterySC.onItemRightClick (Shift, на сервере, после установки режима mode != MODE_OFF): собрать предметы цели (броня / предмет в руке / всё — как onUpdate, без аккумуляторов); если среди них есть с requiredTier != null, но нет ни одного с requiredTier.ordinal() <= tierOf(stack).ordinal() — написать в чат sc.battery.toohigh(уровень аккумулятора, имя предмета с наименьшим требуемым уровнем через ChatComponentTranslation(item.getUnlocalizedName(s) + ".name"), этот уровень). Если подходящие есть или цель пуста — ничего.
3. ItemBatterySC.addInformation: после строки уровня — "§7" + Lang.tr("sc.battery.chargesupto", tierOf(stack).name()).
4. Справочник: новый абзац sc.manual.energy.battery.4 с аргументами уровней брони (ArmorSuit.NANO/QUANTUM/EXO/SINGULAR.chargeTier.name()) — в BookContent добавить его к статье аккумуляторов через BookEl.para(Lang.tr(key, ...)) (paras("sc.manual.energy.battery") аргументов не передаёт — проверить, не подхватит ли он .4 сам без аргументов; если подхватит — вынести .4 под отдельный ключ sc.manual.energy.batterytier).

**Файлы:** `item/ItemChargeSC.java`, `item/ItemBatterySC.java`, `manual/BookContent.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.battery.chargesupto = §7Заряжает предметы уровня до %s | §7Charges items up to tier %s
- sc.battery.toohigh = Аккумулятор %s не зарядит %s: нужен аккумулятор уровня %s или выше | A %s battery won't charge %s: it needs a battery of tier %s or higher
- sc.manual.energy.batterytier = Режимы аккумулятора заряжают только предметы его уровня и ниже: Нано-броне нужен аккумулятор не ниже %s, Квантовой - %s, Экзо - %s, Сингулярной - %s. Если в выбранной цели нет ни одного подходящего предмета, при включении режима чат скажет, какой уровень нужен. | A battery's modes charge only items of its tier or lower: Nano armour needs a %s battery or better, Quantum %s, Exo %s, Singular %s. With nothing of a fitting tier in the chosen target, switching a mode on says in the chat which tier it needs.

**Проверки:** SelfTestSC.batteries дополнить: requiredTier(нагрудник Квант)==HV, (Экзо-нагрудник)==EV, (ПЭ-1)==EmpMathSC.CHARGE_TIER, (булыжник)==null. Ручная: LV-аккумулятор, режим «броня» в Квантовой броне — сообщение «…нужен аккумулятор уровня HV или выше».

## МШ — Машины и переработка (9)

### МШ-1. Компрессор материи сжимает новое снаряжение и компоненты мода

**★** А. Не принимать предметы, которые не складываются в стопку, из любого мода (броня, инструменты, оружие, ПЭ-1, электрические предметы), а из предметов мода — всё, кроме сырья (слитки, пыль, мелкая пыль, дроблёная и очищенная руда, руда, блоки металлов, лом; по словарю руд или по своим классам). Добавить проверку в самотест, обновить подсказку компрессора (sc.gui.comp.hint.2) и справочник.

**Что сделать.**

1. tileentity/TileEntityMachineSC.matterMass(stack): после проверки NBT добавить два правила:
   - `stack.getMaxStackSize() <= 1` → 0 (любой мод: броня, инструменты, оружие, ПЭ-1, электрические предметы, вёдра);
   - предмет мода (`cpw.mods.fml.common.registry.GameRegistry.findUniqueIdentifierFor(stack.getItem())` с modId == Reference.MODID) и не isModRaw(stack) → 0.
2. Новый `private static boolean isModRaw(ItemStack s)`: true для ModItems.ingot, dust, dustTiny, crushedOre, purifiedCrushedOre (ItemMaterialSC; пыль лома Material.SCRAP входит в dust), ItemBlock блоков ModBlocks.oreSC, metalBlock, metalBlock2, leadBlock, limestoneSC; иначе — если у стопки есть имя словаря руд с приставкой ingot, dust, dustTiny, dustSmall, crushed, crushedPurified, ore, block, scrap (OreDictionary.getOreIDs). Всё остальное мода (кремниевые материалы, контроллеры, чипы, ядро реактора, компоненты, модули…) — 0.
3. Прежние правила (NBT, тара, редкость, ценности, блоки с содержимым, алмазное снаряжение) не трогать. Затягиватель и canInsertItem спрашивают matterMass — им ничего менять не надо; isItemValidForSlot тоже через него (проверить).
4. lang: sc.gui.comp.hint.2 и sc.manual.machine.matter_compressor — тексты ниже (эти же строки называет МШ-9: их делает МШ-1, МШ-9 их не трогает).
5. Самотест: см. ниже.
Связь: МШ-4 (выталкиватель) и МШ-9 (тексты) — тот же файл TileEntityMachineSC у МШ-4/5/6: правки в разных методах.

**Файлы:** `tileentity/TileEntityMachineSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.comp.hint.2 = §7Не берёт: то, что не складывается в стопку (броня, инструменты, оружие), из предметов мода - всё, кроме сырья (слитки, пыль, руда, дроблёная руда, блоки металлов, лом); вёдра с жидкостью, блоки с содержимым, редкое и ценное | §7Refuses: anything that doesn't stack (armour, tools, weapons), of the mod's own items everything but raw materials (ingots, dust, ore, crushed ore, metal blocks, scrap); fluid buckets, blocks that hold things, rare and valuable items
- sc.manual.machine.matter_compressor = Компрессор материи: глотает предметы и блоки из входа и копит их массу во внутреннем счётчике, из которой прессует Капсулы сжатой материи (а в режиме „жидкая материя“ наливает Сингулярную материю). Тяжёлые металлы (свинец, вольфрам, гафний, тантал, железо, золото - слитки, пыль, пластины, руда после дробления и их блоки) дают больше массы. Не берёт: предметы с данными (заряженное, настроенное, машины с начинкой); всё, что не складывается в стопку (броня, инструменты, оружие любого мода); из предметов мода - всё, кроме сырья (слитки, пыль, мелкая пыль, дроблёная и очищенная руда, руда, блоки металлов, лом): компоненты, чипы, модули, аккумуляторы и сами капсулы в массу не идут; всё в таре (вёдра и ячейки с жидкостью); машины, генераторы, накопители, баки и другие блоки любого мода с содержимым; алмазное снаряжение; редкие предметы (с цветным названием) и ценности - алмазы, изумруды и их блоки, жемчуг и око Края, звезду Нижнего мира, маяк, яйцо дракона. Масса в счётчике сохраняется, если машину сломать. | Matter Compressor: swallows items and blocks from its inputs and keeps their mass in an internal counter, from which it presses Compressed Matter Capsules (or, in the "liquid matter" mode, pours Singular Matter). Heavy metals (lead, tungsten, hafnium, tantalum, iron, gold - ingots, dust, plates, crushed ore and their blocks) give more mass. It refuses: items with data (charged, configured, machines with things inside); anything that doesn't stack (armour, tools and weapons of any mod); of the mod's own items everything but raw materials (ingots, dust, tiny dust, crushed and purified ore, ore, metal blocks, scrap): components, chips, upgrades, batteries and the capsules themselves never go into mass; anything in a container (buckets and cells with fluid); machines, generators, storages, tanks and other blocks of any mod that hold something; diamond gear; rare items (a coloured name) and valuables - diamonds, emeralds and their blocks, ender pearls and eyes, nether stars, beacons, the dragon egg. The mass in the counter stays with the machine when it is broken.

**Проверки:** SelfTestSC (рядом с прежней проверкой matterMass): matterMass(Нано-шлем без NBT)==0, (Экзо-клинок)==0, (бур)==0, (ПЭ-1)==0, (железный меч)==0, (контроллер ModItems.siliconMaterial CONTROLLER)==0, (ядро термоядерного реактора comp fusionCore)==0, (медный слиток мода)>0, (пыль мода)>0, (руда мода oreSC)>0, (булыжник)>0, (железный слиток)>0.

### МШ-2. Shift + ПКМ пустой рукой по машине с пустыми баками пишет «Входные баки очищены (0 мБ)» и не открывает меню

**★** А. Если все баки пусты, открывать экран машины вместо слива и сообщения, как в МШ-11 для ведра. Слив и сообщение оставить только для непустых баков.

**Что сделать.**

block/BlockMachineSC.onBlockActivated, ветка `player.isSneaking() && held == null` (~стр. 352-366): на сервере после вычисления `any` — если !any: `player.openGui(SCMod.instance, GuiHandlerSC.MACHINE_GUI_ID, world, x, y, z); return true;` (без ventInputTanks и без сообщения). На клиенте ветка по-прежнему просто возвращает true (сервер откроет экран сам). Остальное (подтверждение вторым нажатием, слив, сообщения) — без изменений.
Подсказку sc.machine.tooltip.howto об этом обновляет МШ-9.

**Файлы:** `block/BlockMachineSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная: Shift + ПКМ пустой рукой по дробилке (баков нет) и по пустому электролизёру — открывается экран, сообщения нет; по машине с водой — первое нажатие спрашивает подтверждение, второе сливает.

### МШ-3. Кнопка «×» над баком выливает его одним щелчком

**★** А. Двойной щелчок для всех кнопок «×» мода: первый щелчок только взводит кнопку на ~1 с, подсказка пишет «Нажмите ещё раз, чтобы вылить N мБ» (новый ключ ru/en), бак выливается со второго щелчка. Так уже работает кнопка питания (GuiPowerSC.allowClick). Общая правка в GuiBigSC.ClearButton.

**Что сделать.**

1. inventory/GuiBigSC.ClearButton: поле `private long armedAt;` константа ARM_MS = 1000; `public boolean fire()` — если System.currentTimeMillis() - armedAt <= ARM_MS → armedAt = 0, true; иначе armedAt = now, false; `public boolean armed()`. drawButton: во взведённом состоянии ярче (например, внутренний цвет 0xFFD04040, мигание по (now/150)%2).
2. GuiBigSC.clearTip: новая перегрузка clearTip(int amount, int cost, int have, boolean armed): при armed и amount > 0 первой строкой после заголовка "§e" + Lang.tr("sc.gui.tank.clear.again", amount). Старая сигнатура → armed=false.
3. В каждом экране с ClearButton: actionPerformed — `if (button instanceof GuiBigSC.ClearButton && !((GuiBigSC.ClearButton) button).fire()) return;` до отправки пакета; подсказка над кнопкой — передать b.armed(): GuiMachineSC (actionPerformed ~288, тултип ~2075), GuiGeneratorSC (кнопки ~163, тултип ~1833), GuiArmorStationSC (~77, ~309), GuiSingularStationSC (~543, ~1570). Серверная часть (clearTank и пакеты) не меняется.
Кнопки «Вылить» на вкладке баков карьера (GuiQuarrySC) — не ClearButton, в объём ★ не входят.

**Файлы:** `inventory/GuiBigSC.java`, `inventory/GuiMachineSC.java`, `inventory/GuiGeneratorSC.java`, `inventory/GuiArmorStationSC.java`, `inventory/GuiSingularStationSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.tank.clear.again = §eНажмите ещё раз, чтобы вылить %s мБ | §eClick again to pour out %s mB

**Проверки:** Ручная: в компрессоре с сингулярной материей один щелчок по «×» — бак цел, подсказка «Нажмите ещё раз…»; второй в течение 1 с — бак пуст; второй через 2 с — снова только взвод. То же в генераторе, станции брони и Сингулярной станции.

### МШ-4. Выталкиватель соседней машины молча отправляет её продукцию в компрессор материи

**★** А. Выталкиватель пропускает соседний компрессор, так же как затягиватель компрессора не трогает машины мода. В компрессор подают трубы в режиме «Помещать», воронки и его собственный затягиватель. Строку об этом добавить в подсказку выталкивателя.

**Что сделать.**

1. tileentity/TileEntityMachineSC.eject() (~стр. 1083): в цикле по граням для предметов — `if (te instanceof TileEntityMachineSC && ((TileEntityMachineSC) te).getMachineType().isCompressor()) continue;` до InvUtilSC.insert (жидкости в компрессор и так не идут; ветку жидкостей можно оставить).
2. lang: sc.upgrade.tooltip.ejector — дописать оговорку (текст ниже). Название режима пневмотрубы сверить с lang (в подсказке выталкивателя уже есть «Извлекать»; режим подачи называется так, как в sc.conduit.mode.* — подставить точное название).
Тот же файл, что МШ-1/5/6: другой метод.

**Файлы:** `tileentity/TileEntityMachineSC.java`

**Тексты (ключ = ru | en):**

- sc.upgrade.tooltip.ejector = Выталкивает продукты в соседние инвентари, баки и трубы (в пневмотрубу - нет: там ставьте разъём „Извлекать“). В соседний Компрессор материи не кладёт: его кормят трубы в режиме „Помещать“, воронки и его собственный затягиватель | Pushes the products into the inventories, tanks and pipes around (not into a pneumatic tube: set its connector to "Extract" instead). Never into a neighbouring Matter Compressor: feed that with pipes set to "Insert", hoppers or its own puller

**Проверки:** Ручная: дробилка с выталкивателем вплотную к компрессору — пыль остаётся в выходе дробилки (или уходит в другой сосед), в компрессор не идёт.

### МШ-5. Экран показывает шанс брака по рецепту, без модулей качества и без удвоения при перегреве

**★** А. Показывать фактический шанс: базовый × 0,5 за каждый модуль качества и × 2, если текущая операция уже перегревалась, например «брак 12% → 3%». Признак перегрева передавать на экран одним битом в статусе машины. У машин с нагревом добавить «×2 при перегреве».

**Что сделать.**

1. tileentity/TileEntityMachineSC: `public float effectiveDefect(MachineRecipe r)` = (overheatedThisRun ? min(1, r.defectChance*2) : r.defectChance) * 0.5^upgradeCount(QUALITY) — ровно формула finishRecipe; finishRecipe перевести на неё (поведение то же). `public boolean isOverheatedThisRun()` и клиентский сеттер `setOverheatedClient(boolean)`.
2. inventory/ContainerMachineSC: в default-ветке (статус) отправлять `machine.getStatus().ordinal() | (machine.isOverheatedThisRun() ? 1 << 8 : 0)`, при приёме — MachineStatus.byOrdinal(data & 0xFF) и setOverheatedClient((data & 256) != 0).
3. inventory/GuiMachineSC: помощник `private String defectText(MachineRecipe r)`: base = round(r.defectChance*100), eff = round(machine.effectiveDefect(r)*100); строка base + "%" при равенстве, иначе base + "% -> " + eff + "%" (ASCII «->», как в sc.gui.elec.run); если machineType.heatCapable и операция ещё не перегревалась — обернуть в Lang.tr("sc.gui.defect.heat", text). Во всех 15 местах (стр. ~1094, 1128, 1153, 1176, 1201, 1231, 1761, 1873, 2233, 2266, 2278, 2297, 2309, 2321, 2339) передавать defectText(r) вместо Math.round(r.defectChance * 100); форматы ключей ниже меняются с %d%% на %s. Ширину проверять smallFit там, где строка стала длиннее.
4. NEI (MachineRecipeHandlerSC) остаётся с базовым шансом — без изменений.
Тот же файл TileEntityMachineSC, что МШ-1/4/6.

**Файлы:** `tileentity/TileEntityMachineSC.java`, `inventory/ContainerMachineSC.java`, `inventory/GuiMachineSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.saw.defect = брак %s | defects %s
- sc.gui.step.dies = кадров %d/%d - брак %s | fields %d/%d - defects %s
- sc.gui.pack.run = кристаллов: %d - брак %s - %d с | dies: %d - defects %s - %d s
- sc.gui.cvd.defect = брак %s - %d с | defects %s - %d s
- sc.gui.defect.heat = %s (x2 при перегреве) | %s (x2 if overheated)

**Проверки:** SelfTestSC: тестовый тайл машины с рецептом defectChance 0.12: effectiveDefect без модулей == 0.12, с 2 модулями качества == 0.03, с overheatedThisRun (через NBT "OverheatedRun") == 0.06; упаковка статуса: (ordinal | 256) & 0xFF == ordinal. Проверка lang-аргументов: ru/en совпадают (общая).

### МШ-6. Индукционная печь после Shift-клика и при подаче воронкой работает одним потоком из двух

**★** А. Печь сама выравнивает вход: если один входной слот пуст, а в другом 2 и больше предметов, половина переходит в пустой слот. Помогает и для рук, и для воронок и труб; прогресс плавки не сбрасывается.

**Что сделать.**

tileentity/TileEntityMachineSC.updateSmelter (~стр. 690): в начале, когда streams == 2 (machineType.smeltStreams()), до цикла по потокам — `balanceSmelterInputs()`: если slots[0] != null && slots[1] == null && slots[0].stackSize >= 2 && smeltResult(slots[0]) != null → slots[1] = slots[0].splitStack(slots[0].stackSize / 2); и зеркально 1 → 0. markDirty(). Слот, где шла плавка, сохраняет предмет и smeltKey/smeltProgress (ключ тот же — прогресс не сбрасывается); новый слот начинает с 0. Делать не чаще раза в 20 тиков (worldObj.getTotalWorldTime() % 20 == 0), чтобы не дёргать воронку каждый тик; только на сервере. Не трогать слоты при выключенной печи? — можно и при выключенной (безвредно), но проще в той же точке после проверки powerOn.
Тот же файл, что МШ-1/4/5.

**Файлы:** `tileentity/TileEntityMachineSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSC: тестовая индукционная печь, в слот 0 — 10 железной руды, слот 1 пуст: после вызова balanceSmelterInputs() (сделать package-private или public для теста) — 5 и 5; 1 предмет — не делится; неплавящийся предмет — не делится.

### МШ-7. Дробилка не берёт руды других модов

**★** А. Добавить рецепты дробилки для чужих руд по словарю руд (oreCopper, oreTin, oreLead, oreZinc, oreAluminum/oreAluminium/oreBauxite, oreTitanium, oreTungsten, orePlatinum, oreLithium и др., если у материала есть дроблёная форма): 2 дроблёные руды без побочных продуктов. Свои руды не дублировать. Регистрацию руд мода не менять, комментарий в ModBlocks поправить.

**Что сделать.**

1. init/ModRecipesOreProcessing: `public static void registerForeignOres()`: для каждого Material m с m.hasCrushedOre — имена "ore" + m.oreDictName, для ALUMINIUM ещё "oreAluminum" и "oreBauxite" (oreDictName у него "Aluminium"); для каждого имени — каждая стопка OreDictionary.getOres(name), кроме блоков мода (Block.getBlockFromItem(item) == ModBlocks.oreSC) и кроме уже имеющих рецепт дробилки (RecipeRegistry.findMatch(CRUSHER, {копия, stackSize 1}, null, null) != null — так пропускаются ванильные железо/золото и повторы); регистрировать MachineRecipe(CRUSHER, {копия стопки, 1 шт.}, null, null, {crushedOre m × 2}, null, null, CRUSHER_TICKS, 0f) без побочных.
2. Вызов из SCMod.postInit (после того как все моды зарегистрировали руды; перед построением кэшей NEI, если они есть).
3. machine/MachineRecipe.isSameIngredient: поддержать стопку-образец с уроном OreDictionary.WILDCARD_VALUE (32767): совпадает по предмету при любом уроне (некоторые моды регистрируют руды так).
4. init/ModBlocks, комментарий у registerOreDict (~стр. 223): руды мода регистрируются под именами минералов (oreChalcopyrite, oreCassiterite…), а не oreCopper; чужие oreCopper и т. п. дробит registerForeignOres.
5. Следствие (оставить как есть): карьер с модулем дробления теперь дробит и такие руды (после исправления этого круга он проверяет, есть ли рецепт дробилки) — это ожидаемо.
6. Справочник: дописать фразу в статью дробилки (ключ ниже).

**Файлы:** `init/ModRecipesOreProcessing.java`, `SCMod.java`, `machine/MachineRecipe.java`, `init/ModBlocks.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.manual.machine.crusher = дописать в конец: « Руды других модов из словаря руд (медь, олово, свинец, цинк, алюминий и боксит, титан, вольфрам, платина, литий и другие металлы, у которых в моде есть дроблёная руда) дробятся в 2 дроблёные руды, без побочных продуктов.» | append: " Other mods' ores from the ore dictionary (copper, tin, lead, zinc, aluminium and bauxite, titanium, tungsten, platinum, lithium and the other metals the mod has crushed ore for) are crushed into 2 crushed ores, no by-products."

**Проверки:** SelfTestSC: если в OreDictionary есть чужой "oreCopper" (IC2) — findMatch(CRUSHER, {он}) даёт 2 дроблёные медные руды; блок oreSC халькопирита по-прежнему находит свой рецепт (один); ванильная железная руда — по-прежнему её прежний рецепт; при отсутствии чужих руд — info. Чистый тест isSameIngredient(чужой предмет meta 3, образец того же предмета meta 32767) == true.

### МШ-8. Поиск рецепта каждый тик много раз обращается к словарю руд

**★** А. Запоминать имена ингредиентов в рецепте (считать при первом сравнении, сбрасывать при запуске сервера, как кэш плавки, чтобы подхватить /mt reload), а имя предмета в слоте считать не больше раза за поиск. Поведение не меняется.

**Что сделать.**

1. machine/MachineRecipe: кэш имён входов: `private String[] inputNames; private int namesGen = -1;` и статический `private static volatile int GEN;` + `public static void forgetNames() { GEN++; }`. `String inputName(int i)`: при namesGen != GEN пересчитать массив OreRecipes.sharedName(inputs[i]) для всех i (null — нет имени; отметить «посчитано» отдельным boolean[] или сентинелом).
2. Новый вариант сравнения `static boolean isSameIngredient(ItemStack provided, String providedName, ItemStack required, String requiredName)` (та же логика: сначала item+damage, затем равенство имён); старая сигнатура остаётся (её зовут TileEntityMachineSC.consumeInputs, isItemValidForSlot и др.) и делегирует, считая имена на месте.
3. matches(...)/assign...: перегрузка, принимающая `ProvidedNames` — маленький объект с ленивым кэшем sharedName для каждого входного слота (считается не больше раза за findMatch). machine/RecipeRegistry.findMatch создаёт его один раз на вызов и передаёт во все recipe.matches.
4. SCMod.serverStarting: рядом с TileEntityMachineSC.clearSmeltCache() вызвать MachineRecipe.forgetNames().
Важно сохранить правило «consumeInputs и matches сравнивают одинаково» (комментарий в MachineRecipe).

**Файлы:** `machine/MachineRecipe.java`, `machine/RecipeRegistry.java`, `SCMod.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSC: для набора входов (свой медный слиток, чужой медный слиток IC2 при наличии, булыжник, пусто) findMatch по всем типам машин даёт те же рецепты до и после MachineRecipe.forgetNames(); isSameIngredient(новая) == isSameIngredient(старая) на тех же парах.

### МШ-9. Подсказки машин и справочник отстают от рецептов

**★** А. Переписать эти строки (ru/en, CRLF, кавычки „…“ / “…”) под нынешние рецепты: все режимы электролизёра, платина или палладий и бонус церия и лантана у нефтезавода, полный список станций HV и EV, отказ компрессора (и то, что выбрано в МШ-1), «Shift + ПКМ пустой рукой дважды — слить баки».

**Что сделать.**

Только lang (код не трогать). Строки компрессора (sc.gui.comp.hint.2, sc.manual.machine.matter_compressor) переписывает МШ-1 — здесь их не трогать. Переписать: sc.gui.elec.hint, sc.gui.refi.hint, sc.gui.refi.now.0, sc.gui.refi.now.1, sc.manual.machine.refinery, sc.manual.machine.upgrade_station_hv, sc.manual.machine.upgrade_station_ev, sc.machine.tooltip.howto (с учётом МШ-2: при пустых баках Shift + ПКМ открывает меню). Перед записью сверить рецепты: электролизёр — ModRecipesComponents.registerChemistry (галит+вода → NaOH и хлор; дроблёная литиевая руда+вода → фтор), ModRecipesInfrastructure (сухой галит → водород и дейтерий; вода+NaOH → водород и кислород; тяжёлая вода → дейтерий и кислород), ModRecipesOreProcessing (пыль алюминия, пыль магния → слиток); нефтезавод — ModRecipesOreProcessing ~135-145 (палладий как платина; церий/лантан → 800 мБ дизеля и 3 резины); станции HV/EV — рецепты UPGRADE_STATION_HV/EV в ModRecipes* (HV: чипы III, Нано → Квант, Квантовые клинок и бур, ПЭ-1 → ПЭ-1М; EV: Квант → Экзо, ядро термоядерного реактора, универсальный трансформатор, Экзо-клинок, Экзо-бур). Если найдутся ещё рецепты — дописать.

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- sc.gui.elec.hint = §7Рецепт - по сырью во входе: галит с водой (NaOH и хлор), дроблёная литиевая руда с водой (фтор), сухой галит (водород и дейтерий), вода с NaOH (водород и кислород), тяжёлая вода (дейтерий и кислород), пыль алюминия или магния (слиток) | §7The recipe follows the input: halite and water (NaOH and chlorine), crushed lithium ore and water (fluorine), dry halite (hydrogen and deuterium), water with NaOH (hydrogen and oxygen), heavy water (deuterium and oxygen), aluminium or magnesium dust (an ingot)
- sc.gui.refi.hint = §7Катализатор во входе: пыль платины или палладия - вместо дизеля фоторезист и пластики; пыль церия или лантана - больше дизеля (800 мБ) и резины | §7A catalyst in the input: platinum or palladium dust - photoresist and plastics instead of diesel; cerium or lanthanum dust - more diesel (800 mB) and rubber
- sc.gui.refi.now.0 = Сейчас: дизель и резина (без катализатора; с церием или лантаном - больше) | Now: diesel and rubber (no catalyst; more with cerium or lanthanum)
- sc.gui.refi.now.1 = Сейчас: фоторезист, пластик и PTFE (платина или палладий) | Now: photoresist, plastic and PTFE (platinum or palladium)
- sc.manual.machine.refinery = Перегоняет нефть в дизель, полимеры, PTFE, фоторезист и каучук; катализатор - пыль платины или палладия, а пыль церия или лантана даёт больше дизеля и резины. | Refines oil into diesel, polymers, PTFE, photoresist and rubber; the catalyst is platinum or palladium dust, and cerium or lanthanum dust gives more diesel and rubber.
- sc.manual.machine.upgrade_station_hv = Станция модернизации HV: чипы тира III, Нано-костюм в Квант (чипы сохраняются), Квантовые клинок и бур, ПЭ-1 в ПЭ-1М. | HV Upgrade Station: tier III chips, the Nano suit into Quantum (the chips are kept), the Quantum blade and drill, the PE-1 into the PE-1M.
- sc.manual.machine.upgrade_station_ev = Станция модернизации EV: Квант в Экзо, ядро термоядерного реактора, универсальный трансформатор, Экзо-клинок и Экзо-бур. | EV Upgrade Station: Quantum into Exo, the fusion reactor core, the universal transformer, the Exo blade and the Exo drill.
- sc.machine.tooltip.howto = ПКМ - меню; ведро или ячейка - залить или набрать; ключ - повернуть (Shift - лицом к грани); Shift + ПКМ пустой рукой дважды - слить баки (баки пусты - откроется меню) | Right-click - screen; bucket or cell - fill or drain; wrench - turn (sneaking - to the clicked face); sneak + right-click with an empty hand twice - vent the tanks (empty tanks - the screen opens)
- (английские тексты нынешних строк сверить с en_US и сохранить их стиль; выше — образец)

**Проверки:** SelfTestSC.langQuotes и проверка ru/en аргументов (у этих строк аргументов нет). Ручная: подсказки электролизёра и нефтезавода на экране, статьи станций в справочнике.

## ГН — Генераторы и реакторы (6)

### ГН-1. Порты в стене Сингулярного реактора (и Токамака XV без свинцового кожуха) пропускают излучение

**★** А. Считать порты стоящей постройки частью оболочки: если на пути излучения порт постройки (TileEntityGeneratorSC.portHolder, уже публичный), он пропускает 5%, как свинцовое стекло. Работает только на сервере, поиск дешёвый и только для блоков с содержимым. Из big.2 убрать «порт экранирует хуже свинца».

**Что сделать.**

1. radiation/RadiationSC.through(...) (воксельный проход, ~стр. 230-258): вместо `f *= blockPasses(w.getBlock(x, y, z), meta)` — взять block/meta, `float pass = blockPasses(b, meta); if (pass > THROUGH_LEAD_GLASS && !w.isRemote && b.hasTileEntity(meta) && com.sc.tileentity.TileEntityGeneratorSC.portHolder(w, x, y, z) != null) pass = THROUGH_LEAD_GLASS; f *= pass;`. portHolder держит порты только пока постройка работает (зажжена / фаза сжатия Сингулярного — portsInUse), т. е. ровно когда есть излучение; он есть и у Сингулярного (тот же реестр PORT_OWNERS в TileEntityGeneratorSC).
2. blockPasses(Block[, meta]) без координат не меняется (его зовут справочник и WAILA).
3. lang: big.2 — убрать «Порт экранирует хуже свинца.», добавить «Порты стоящей постройки экранируют, как свинцовое стекло.»; sing.rad.1 и big.3 становятся правдой (можно уточнить «оболочка вместе с портами»).

**Файлы:** `radiation/RadiationSC.java`, `debug/WorldTestTokamakSC.java`

**Тексты (ключ = ru | en):**

- sc.manual.generator.big.2 = …в них идёт вся выработка. Порты стоящей постройки экранируют, как свинцовое стекло. (последнее предложение вместо «Порт экранирует хуже свинца.») | …all the output goes there. The ports of a standing build shield as lead glass does. (instead of "A port shields worse than lead.")

**Проверки:** WorldTestTokamakSC (или WorldTestSingularSC): у зажжённого Токамака XV through() от центра до точки за баком-портом ≈ 0,05 (не 1,0); у незажжённой постройки бак пропускает как раньше. Ручная: дозиметр у порта Сингулярного — уровень ниже 1.

### ГН-2. С экономайзерами электролиз воды и Топливный элемент (или Газовая турбина) дают энергию из воды

**★** А. Экономайзер: расход ×0,85 вместо ×0,7, выработка по-прежнему ×0,9. Тогда выгода не больше 1,26 раза при 4 модулях: элемент на газах электролиза даёт ≈62 900 EU против 72 000, турбина ≈40 200. Поправить подсказку экономайзера, справочник (gen.rules.1), комментарии рецепта и конфига и самотест.

**Что сделать.**

1. tileentity/TileEntityGeneratorSC: константы `public static final double ECONOMIZER_FUEL = 0.85, ECONOMIZER_OUTPUT = 0.9;` и чистый `public static double economizerFuel(int n) = Math.pow(ECONOMIZER_FUEL, n)`; fuelMultiplier(): Math.pow(0.7, …) → economizerFuel(upgradeCount(ECONOMIZER)); outputMultiplier()/outputWith() — 0.9 → ECONOMIZER_OUTPUT (значение то же).
2. init/ModRecipesInfrastructure, комментарий у электролиза воды (~стр. 67-74): «90 EU/t x 800 t = 72 000 EU против 50 000 у топливного элемента; с 4 экономайзерами (выработка x0,9, топливо x0,85 за каждый) — около 62 900: петли нет».
3. Комментарий machineEnergy в ConfigSC координатор уже обновил (порог ~0,87 с 4 экономайзерами) — не трогать.
4. GuiGeneratorSC/NEI берут fuelMultiplier() — ничего не менять; справочник — lang.
Тот же файл TileEntityGeneratorSC, что ГН-1/3/4/5/6.

**Файлы:** `tileentity/TileEntityGeneratorSC.java`, `init/ModRecipesInfrastructure.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.upgrade.tooltip.economizer = Только генераторы на топливе: расход топлива x0,85, выработка x0,9 за каждый (до 4) | Fuel generators only: fuel x0.85, output x0.9 each (up to 4)
- sc.manual.gen.rules.1 = в тексте «экономайзер: топливо x0,7 и выработка x0,9» → «экономайзер: топливо x0,85 и выработка x0,9» | "economizer: fuel x0.7 and output x0.9" → "economizer: fuel x0.85 and output x0.9" (остальное без изменений; en-формулировку сверить с en_US)

**Проверки:** SelfTestSC: economizerFuel(4) ≈ 0.522 (0.85^4), отношение выработки к расходу при 4 модулях (0.9/0.85)^4 < 72000/50000; тестовый топливный элемент с 4 экономайзерами: fuelMultiplier() ≈ 0.522.

### ГН-3. Трансформатор в генераторе и розжиг реактора молча сжигают слабый кабель

**★** А. В экране генератора следить за уровнем выхода, как у накопителя (вырос — предупредить тех, у кого открыт экран). При розжиге перед подключением к сети проверить выходные грани и, если кабель слабее выхода, предупредить ближних игроков (новый ключ sc.chat.gen.cableweak с уровнями). В чек-лист экрана реактора добавить строку «кабель X слабее выхода — сгорит при розжиге».

**Что сделать.**

1. inventory/ContainerGeneratorSC.detectAndSendChanges: как ContainerEnergyStorageSC (~стр. 140-155): поле `private Tier lastOutput;` out = generator.outputTier(); если lastOutput != null && out.ordinal() > lastOutput.ordinal() — для каждого crafters-игрока CableWarningSC.outputRaised(generator, player); lastOutput = out.
2. energy/CableWarningSC: `public static Tier weakestCableAt(TileEntityEnergyBase src, Tier out)` — по граням src.isOutputFace(d): кабель связки (TileEntityConduitBundleSC.getCable().tier) и TileEntityCableSC; вернуть самый слабый, у которого tier.excessTiersOf(out) > 0, иначе null; работает на обеих сторонах (на клиенте связки тоже есть). `public static void ignitionCheck(TileEntityEnergyBase src)` (сервер): weak = weakestCableAt(src, src.outputTier()); если weak != null — всем игрокам в 16 блоках sc.chat.gen.cableweak (out, weak, out).
3. tileentity/TileEntityGeneratorSC, место розжига (~стр. 2227, после `ignited = true; ramp = …; status = GENERATING;`, перед refreshEnergyNet()) — CableWarningSC.ignitionCheck(this). Только предупреждение, розжиг не отменяется.
4. Экраны реакторов (generatorType.needsIgnition() и ещё не зажжён): строка-предупреждение §c sc.gui.gen.st.cableweak (кабель, будущий выход), если weakestCableAt(generator, generator.outputTier()) != null: GuiGeneratorSC — термоядерный/Токамак/Экзо-реактор (в блоке подсказки этапов, ~стр. 753-830 и экзо ~1037-1100), GuiTokamakXVSC (список st.* незажжённого, рядом с porttaken ~790) и GuiSingularSC (~883). У Сингулярного выход всегда SV.
5. isOutputFace у незажжённого реактора должен отвечать по facing (проверить; если зависит от ignited — для проверки использовать грань выхода по facing).

**Файлы:** `inventory/ContainerGeneratorSC.java`, `energy/CableWarningSC.java`, `tileentity/TileEntityGeneratorSC.java`, `inventory/GuiGeneratorSC.java`, `inventory/GuiTokamakXVSC.java`, `inventory/GuiSingularSC.java`

**Тексты (ключ = ru | en):**

- sc.chat.gen.cableweak = §cРеактор зажжён: выход %s, а кабель на выходной грани - %s. Он сгорит - нужен кабель не ниже %s | §cThe reactor is lit: its output is %s, the cable at an output face is %s. It will burn out - use a cable of %s or better
- sc.gui.gen.st.cableweak = §cКабель %s слабее выхода %s - сгорит при розжиге | §cThe %s cable is weaker than the %s output - it burns out on lighting

**Проверки:** Ручная: Токамак с LV-кабелем на выходе — на экране до розжига строка «Кабель LV слабее выхода EV…», при розжиге сообщение в чат ближним игрокам; трансформатор в генератор при открытом экране — сообщение sc.cable.warn.raised. WorldTestTokamakSC (по желанию): weakestCableAt вернул LV.

### ГН-4. Лишний порт в стене (5-й бак, 3-е хранилище) экран показывает как дыру

**★** А. При лишнем баке или хранилище ставить отдельный признак (как для занятого порта) и выводить в чек-листе XV и Сингулярного строку «Лишний порт: в стене не больше 4 баков и 2 хранилищ» (числа из констант, новые ключи). Клетка на схеме остаётся дырой, постройка не готова.

**Что сделать.**

1. tileentity/TileEntityGeneratorSC.scanBig (~стр. 955-1000): флаг `extra` — бак при tanks >= PORT_TANKS_MAX или накопитель (не плита) при stores >= PORT_STORES_MAX, не занятый другой постройкой. Поле `private boolean extraPort;` (как portTaken, ~стр. 591), присваивается рядом с `portTaken = taken;` (~1039), геттер isExtraPort(). Синхронизация: bigSyncHead — бит 29 в v[3] (`| (extraPort ? 1 << 29 : 0)`; v[3] = capMissing | stability*10 << 8 | nearBuild << 30, биты 18-29 свободны), setBigClient — читать его. Флаги v[0] заняты все 8 бит — их не трогать.
2. tileentity/SingularReactorSC.scan (~стр. 1067-1132): то же условие по своим счётчикам портов (константы — те же PORT_TANKS_MAX/PORT_STORES_MAX или свои, если у Сингулярного другие); поле extraPort, sync v[0] бит 32768 (1 << 15; до 16384 заняты), setClient (~1203) — читать.
3. inventory/GuiTokamakXVSC (списки st.* незажжённого ~790 и зажжённого ~826) и inventory/GuiSingularSC (~883): `if (…isExtraPort()) add(k, l, 2, Lang.tr("sc.gui.big.st.extraport", PORT_TANKS_MAX, PORT_STORES_MAX));` Строка «Оболочка не закрыта» остаётся (клетка — дыра).
Тот же файл TileEntityGeneratorSC, что ГН-1/2/3/5/6.

**Файлы:** `tileentity/TileEntityGeneratorSC.java`, `tileentity/SingularReactorSC.java`, `inventory/GuiTokamakXVSC.java`, `inventory/GuiSingularSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.big.st.extraport = Лишний порт: в стене не больше %s баков и %s хранилищ | Extra port: no more than %s tanks and %s storages in the wall

**Проверки:** Ручная: 5-й бак в стене Токамака XV — в чек-листе «Лишний порт: …4 баков и 2 хранилищ»; убрать — строка пропала. WorldTestTokamakSC (по желанию): после scan с 5 баками isExtraPort()==true.

### ГН-5. Ветрогенератор не учитывает мир и местную погоду

**★** А. Высоту считать от среднего уровня земли мира (provider.getAverageGroundLevel). В мирах без неба ветра нет. В безвоздушных мирах Galacticraft ветра тоже нет (проверка атмосферы в отдельном классе, который загружается только с Galacticraft). Дождь и грозу проверять над ветряком, как у солнечной панели. Подпись «море y%d» брать из того же уровня.

**Что сделать.**

1. Новый класс compat/GcWorldSC.java (как GcOpsSC: импорты micdoodle8.mods.galacticraft.api.world.*, вызывать только под Loader.isModLoaded("GalacticraftCore")): `public static boolean airless(net.minecraft.world.WorldProvider p)` = p instanceof IGalacticraftWorldProvider && !hasBreathableAtmosphere() && ни один IAtmosphericGas.values() не isGasPresent; RuntimeException → false.
2. tileentity/TileEntityGeneratorSC.updateWind (~стр. 1978): `int ground = worldObj.provider.getAverageGroundLevel(); int height = yCoord - ground;`; если worldObj.provider.hasNoSky или (GC загружен и GcWorldSC.airless(worldObj.provider)) → cachedOutput = 0, infoA = 0, status NO_WIND (и return по существующей ветке). Погода: `boolean rainHere = worldObj.canLightningStrikeAt(xCoord, yCoord + 1, zCoord);` гроза: worldObj.isThundering() && rainHere → ×1,5; иначе rainHere → ×1,25 (в снежном биоме и под навесом бонуса нет).
3. inventory/GuiGeneratorSC (~1527): Lang.tr("sc.gui.wd.sea", generator.getWorldObj().provider.getAverageGroundLevel()); подсказка (~1581): если provider.hasNoSky → "sc.gui.wd.hint.nosky", если GC и GcWorldSC.airless → "sc.gui.wd.hint.airless", иначе как сейчас. (Клиентский provider даёт тот же уровень: тип мира приходит с сервера.)
4. Чистый помощник для теста: `public static double windFactor(int height, int blocked)` = clamp(height/96)·max(0, 1 − blocked·0,04).
Тот же файл TileEntityGeneratorSC, что ГН-1..6.

**Файлы:** `tileentity/TileEntityGeneratorSC.java`, `inventory/GuiGeneratorSC.java`, `compat/GcWorldSC.java (новый)`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.wd.sea = море y%d | sea y%d
- sc.gui.wd.hint = выше и свободнее - сильнее: полная мощность на 96 блоков выше уровня земли мира | higher and freer is stronger: full power 96 blocks above the world's ground level
- sc.gui.wd.hint.sea = на уровне земли мира и ниже ветра нет - нужна высота | no wind at the world's ground level or below - it needs height
- sc.gui.wd.hint.nosky = в мире без неба ветра нет | no wind in a world without a sky
- sc.gui.wd.hint.airless = в мире без атмосферы ветра нет | no wind in a world without air

**Проверки:** SelfTestSC: windFactor(96, 0)==1, (48, 0)==0.5, (0, 0)==0, (96, 25)==0. Ручная: на суперплоской карте ветряк на y 40 работает (уровень земли 4); в Аду — «ветра нет»; на Луне GC — «без атмосферы».

### ГН-6. Генераторы нельзя настроить в конфиге; генератор внутреннего сгорания не берёт топливо EnderIO, Thermal и Forestry

**★** А. Раздел конфига generators: множители generatorOutput и generatorFuel (с предупреждением о бесплатной энергии из электролиза) и список combustionFuels вида «имя=EU/мБ», по умолчанию нынешние топлива плюс EnderIO (hootch, rocket_fuel, fire_water), Thermal (coal) и Forestry (biomass). Генератор, NEI и справочник берут значения из конфига; список топлива передаётся клиентам (ConfigSyncSC).

**Что сделать.**

Координатор уже сделал: ConfigSC — раздел "generators": generatorOutput, generatorFuel (float, 1), combustionFuels (String[], по умолчанию defaultCombustionFuels(): diesel=16, fuel=24, biodiesel=20, ethanol=12, bioethanol=12, biofuel=10, crudeoil=5, oil=5, ic2biogas=4, hootch=10, rocket_fuel=24, fire_water=20, coal=12, biomass=5), помощники ConfigSC.combustionEuPerMb(fluid) и combustionFuelNames(), parseFuels(); ConfigSyncSC передаёт String[]-поля последними (старый клиент/сервер совместимы); ConfigSC.stamp() учитывает String[] (справочник пересобирается). Исполнителю — подключить:
1. energy/GeneratorType.euPerMb: ветка COMBUSTION → `return ConfigSC.combustionEuPerMb(fluid);`. COMBUSTION_FUELS (константа) заменить везде на ConfigSC.combustionFuelNames(): item/ItemFluidDropSC (~68), nei/GeneratorRecipeHandlerSC (~82), nei/ObtainHandlerSC (~535), manual/BookContent (статья генератора внутреннего сгорания — список топлив и EU/мБ из конфига), inventory/GuiGeneratorSC (COMB_FUELS ~116, 1696-1764: таблица строится из combustionFuelNames(), только жидкости, зарегистрированные в FluidRegistry; равные EU/мБ — в одну строку; не помещается — строка «…ещё N»). Константу COMBUSTION_FUELS оставить @Deprecated = defaultCombustionFuels-имена или удалить, если не нужна (самотест 1287 зовёт euPerMb("fuel") — остаётся верным).
2. tileentity/TileEntityGeneratorSC: ratedOutput()/outputWith() × ConfigSC.generatorOutput (все генераторы, включая пассивные и реакторы; Сингулярный — outputNow() × множитель); fuelMultiplier() × ConfigSC.generatorFuel (только burnsFuel). Округление как сейчас, не меньше 1 EU/t.
3. NEI (GeneratorRecipeHandlerSC) и справочник показывают числа с множителями (на клиенте значения сервера).
4. README.md: в «Настройки сервера» (ru и en) одна фраза про раздел generators.
Жидкости EnderIO/Thermal/Forestry — сверить имена в сборке пользователя нельзя (папку не открывать); неверное имя просто не совпадёт.

**Файлы:** `energy/GeneratorType.java`, `tileentity/TileEntityGeneratorSC.java`, `inventory/GuiGeneratorSC.java`, `nei/GeneratorRecipeHandlerSC.java`, `nei/ObtainHandlerSC.java`, `manual/BookContent.java`, `item/ItemFluidDropSC.java`, `README.md`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.gui.comb.more = …ещё %s | …%s more   (если таблице не хватит места)
- (подписи конфига уже двуязычные в ConfigSC; других новых строк не требуется, если справочник собирает список из имён жидкостей)

**Проверки:** SelfTestSC: ConfigSC.parseFuels({"a=5", " b = 2.5 ", "c", "=3", "d=0", "e=abc", "a=9"}) → {a:5, b:2.5}; combustionEuPerMb("diesel")==16 и ("hootch")==10 при умолчаниях; GeneratorType.COMBUSTION.euPerMb("rocket_fuel") > 0; синхронизация combustionFuels уже проверяется самотестом config sync (координатор).

## КР — Карьеры и буровая (9)

### КР-1. Взрыв обходит защиту владельца: чужой карьер выпадает целиком, и тот, кто его поставит, становится владельцем

**★** Б. Карьер с владельцем не ломается взрывом (сопротивление 6 000 000, как с универсальным трансформатором); ломать его могут только владелец и оператор, как сейчас.

**Что сделать.**

1. block/BlockQuarrySC.getExplosionResistance (~стр. 275): вернуть 6000000.0F, если `te instanceof TileEntityQuarrySC && (!((TileEntityQuarrySC) te).getOwner().isEmpty() || upgradeCount(UNIVERSAL_TRANSFORMER) > 0)`.
2. Там же @Override canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) (Forge; образец — BlockDockSC ~73): false для карьера с владельцем (иссушитель и дракон ломают блоки не взрывом). Иначе super.
3. Ломание игроком и ключом — без изменений (refusesBreak). Карьер без владельца (поставлен автоматикой) — как сейчас.
4. Справочник sc.book.mp.quarry.1 — дописать фразу.

**Файлы:** `block/BlockQuarrySC.java`

**Тексты (ключ = ru | en):**

- sc.book.mp.quarry.1 = дописать в конец: « Карьер с владельцем не разрушает ни один взрыв.» | append: " No explosion breaks a quarry that has an owner."

**Проверки:** Ручная: ТНТ вплотную к своему карьеру — карьер стоит; карьер без владельца (поставлен роботом) взрывом ломается как раньше; иссушитель не ломает карьер с владельцем.

### КР-2. Удержание чанков не держит соседний чанк, если сундук выдачи или труба стоят за границей чанка

**★** А. Держать в том же тикете и чанки четырёх соседей карьера, если они в другом чанке (обычно ни одного, на краю один, в углу два).

**Что сделать.**

tileentity/TileEntityQuarrySC.holdChunks (~стр. 1795-1837): после получения тикета (и при каждом проходе, если ещё не держатся) — для соседей (x±1, z) и (x, z±1): ChunkCoordIntPair n = (x>>4, z>>4); если n != own и ещё не в ticket.getChunkList() и ticket.getChunkList().size() < getMaxChunkListDepth() → ForgeChunkManager.forceChunk(ticket, n). Отпускание — существующий releaseChunks (весь тикет). При смене heldDig не отпускать соседний чанк, если он совпал с chunk курсора (unforceChunk(heldDig) только если heldDig не сосед). chunksHeld считает и их (на экране «Чанки удерживаются: N» станет больше).
Обратный вызов загрузки тикетов (LoadingCallback для Kind "quarry", SCMod/ChunkLoader…) — проверить, что он форсирует хотя бы own; соседей добавит первый holdChunks.
Тот же файл, что КР-3/5/6/7/8/9.

**Файлы:** `tileentity/TileEntityQuarrySC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная: карьер на краю чанка, сундук выдачи в соседнем, модуль «Удержание чанков», уйти за 200 блоков — карьер продолжает (сундук наполняется). Чистый помощник для SelfTestSC: `static List<ChunkCoordIntPair> neighbourChunks(int x, int z)` — для x=15,z=5 даёт один чанк (1,0); для x=15,z=15 — два; для x=5,z=5 — ни одного.

### КР-3. Экзо-установка, скрафченная из EV-карьера, получает его баки насоса, которыми не может управлять

**★** А. Как у модуля бака: не давать скрафтить Экзо-установку, пока в отсеках насоса карьера есть жидкость, и написать в подсказке рецепта или NEI «слейте баки насоса». Воду промывки переносить, обрезав до 32 000 мБ.

**Что сделать.**

1. init/ModRecipesCrafting.CarryRecipe: новый режим `COPY_DRY = 4` (дописать к константам, старые не трогать): как COPY, но
   - matches(): для переносимой стопки карьера (isOf(s, carry)) с тегом, где NBTTagList "Tanks" содержит хоть один отсек с Amount > 0 → false;
   - getCraftingResult(): после копирования тегов, если в результате есть "Water" с amount > TileEntityQuarrySC.WATER_BASE (32 000) — обрезать до WATER_BASE и записать обратно; "Tanks" убрать.
   carries() → true. Рецепт Экзо-установки (~стр. 397) регистрировать с COPY_DRY.
2. block/ItemBlockQuarrySC.addInformation, ветка Экзо (~стр. 33-35): в TooltipSC.more вторым аргументом (Ctrl) Lang.tr("sc.quarry.tooltip.exocraft") — сейчас там null.
3. Справочник/NEI-подсказка не обязательны; строки достаточно в Ctrl-подсказке.
Тот же класс CarryRecipe правит ЭН-8 (рядом новый класс) — не пересекается.

**Файлы:** `init/ModRecipesCrafting.java`, `block/ItemBlockQuarrySC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.quarry.tooltip.exocraft = Крафт из EV-карьера: сначала слейте его баки насоса - с жидкостью рецепт не складывается; вода промывки переходит, но не больше 32 000 мБ | Crafted from an EV quarry: empty its pump tanks first - with fluid in them the recipe doesn't form; the washing water comes along, 32,000 mB at most

**Проверки:** SelfTestSC: CarryRecipe COPY_DRY на сетке с EV-карьером, у которого "Tanks" = [{Tank:0, FluidName:water, Amount:1000}] → matches false; с пустыми отсеками и Water 160 000 → результат с Water 32 000. Ручная: в верстаке.

### КР-4. Воду для промывки нельзя залить ведром или капсулой: ПКМ по карьеру всегда открывает меню

**★** А. Как у машин после МШ-11 прошлой проверки: если в руке контейнер жидкости, карьер может его принять или наполнить, а игрок — владелец или оператор, заливать воду в бак промывки или наполнять пустое ведро или капсулу из первого непустого отсека. Иначе открывать меню.

**Что сделать.**

block/BlockQuarrySC.onBlockActivated (~стр. 180): после ветки ключа — если FluidHandSC.isContainer(held) && te instanceof TileEntityQuarrySC: на сервере q.allowed(player) (владелец или оператор; allowed уже включает оператора) и canExchange(q, held) — тот же симулированный обмен, что BlockMachineSC.canExchange (вынести его в FluidHandSC как public static canExchange(IFluidHandler, ItemStack) и звать из обоих блоков) → FluidHandSC.use(world, x, y, z, player, q, held); return true. Иначе — открыть меню, как сейчас. На клиенте: если контейнер, вернуть true без открытия (сервер решит; при отказе сервер откроет экран сам через openGui). TileEntityQuarrySC.fill принимает только воду в бак промывки (стр. ~2416), drain отдаёт первый непустой отсек — проверить, что drain(ForgeDirection.UNKNOWN, …) так и работает (sideAllows UNKNOWN), иначе дать ему путь «из руки».

**Файлы:** `block/BlockQuarrySC.java`, `block/BlockMachineSC.java`, `util/FluidHandSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет (сообщения FluidHandSC.use уже есть).

**Проверки:** Ручная: ведро воды по Экзо-установке — вода в баке промывки, ведро пустое; пустое ведро по карьеру с лавой в отсеке — ведро лавы; чужой игрок с ведром — открывается меню (и только просмотр).

### КР-5. Режим «Оставлять: как было» нигде не объяснён и в любом мире ставит обычный камень

**★** Б. В «как было» ставить самую частую твёрдую не-рудную породу среди шести соседей (в Аду — адский камень, на Луне — лунный грунт), а если такой нет — камень. Плюс подсказка на кнопку «Оставлять» (что делает каждый режим) и строка в справочник.

**Что сделать.**

1. tileentity/TileEntityQuarrySC.replaceAfter(x, y, z) (~стр. 910): для REPLACE_ASWAS — `Block[] b; int[] m` по шести соседям: учитывать блок, если isOpaqueCube(), !isOre(block, meta), !block.hasTileEntity(meta), block.getBlockHardness(world, …) >= 0 (не бедрок), не BlockFalling, не жидкость, не блок карьера; считать пары (block, meta), выбрать самую частую (при равенстве — первая по порядку DOWN, UP, NORTH, SOUTH, WEST, EAST), иначе Blocks.stone/0; worldObj.setBlock(x, y, z, with, meta, 3). Остальные режимы без изменений.
2. inventory/GuiQuarrySC: подсказка кнопки B_REPLACE (~стр. 143/286) — при наведении 4 строки sc.quarrygui.replace.tip.0..3, текущий режим выделить §e (найти, где экран собирает подсказки кнопок, например по id в tooltip-методе).
3. Справочник: новая строка sc.manual.machines.quarry.8; в BookContent статья карьера — проверить, собирает ли paras("sc.manual.machines.quarry") номера подряд (тогда .8 подхватится сам), иначе добавить.
Тот же файл TileEntityQuarrySC, что КР-2/3/6/7/8/9.

**Файлы:** `tileentity/TileEntityQuarrySC.java`, `inventory/GuiQuarrySC.java`, `manual/BookContent.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.quarrygui.replace.tip.0 = Яму: выкопанное ничем не заполняется | A pit: nothing fills what was dug
- sc.quarrygui.replace.tip.1 = Камень: на место каждого блока - камень | Stone: stone in place of every block
- sc.quarrygui.replace.tip.2 = Землю: на место каждого блока - земля | Dirt: dirt in place of every block
- sc.quarrygui.replace.tip.3 = Как было: копается только руда, на её место - самая частая порода вокруг (в Аду - адский камень), иначе камень | As it was: only ore is dug, the commonest rock around takes its place (netherrack in the Nether), else stone
- sc.manual.machines.quarry.8 = Кнопка „Оставлять“: яму, камень, землю или „как было“ - тогда карьер копает только руду, а на её место ставит самую частую твёрдую породу из шести соседей (в Аду - адский камень, на Луне - её грунт), если такой нет - камень. | The "Leave" button: a pit, stone, dirt or "as it was" - then the quarry digs only ore and puts the commonest solid rock of the six neighbours in its place (netherrack in the Nether, the Moon's own soil on the Moon), stone if there is none.

**Проверки:** SelfTestSC: вынести выбор в чистый `static int pickFill(Block[] blocks, int[] metas)` (индекс выбранного или −1): {netherrack×4, ore, air} → netherrack; {} → −1; равенство — первый. Ручная: «как было» в Аду — на месте руды адский камень.

### КР-6. Прогноз «осталось N бл. — ~N ч» считает все клетки, в том числе воздух, отфильтрованные блоки и углы вне круга

**★** А. На сервере копить долю реально копаемых клеток среди проверенных (скользящее среднее за последние слои), передавать её на экран и умножать на неё остаток при расчёте времени и «осталось бл.».

**Что сделать.**

1. tileentity/TileEntityQuarrySC: поля `private float digShare = 1F; private int shareSeen, shareDug;` В месте, где курсор проходит клетку (advance/обработка клетки): shareSeen++, а если клетка реально копалась/откачивалась (списана энергия) — shareDug++. При конце слоя (layerY--) или каждые 4096 проверенных: если shareSeen > 0 → digShare = 0.7F*digShare + 0.3F*(shareDug/(float)shareSeen); обнулить. Сохранять "DigShare" (float, дописать в NBT; нет ключа → 1). Сбрасывать в 1 при смене области (resetCursor).
2. inventory/ContainerQuarrySC: новый id 24 (COUNT 24 → 25) = Math.round(digShare*1000), клиент — setDigShareClient.
3. inventory/GuiQuarrySC (~стр. 965-980): `long est = Math.round(left * share)`; sec = est / bps; в sc.quarrygui.left2 передавать est. Процент «Готово» (pct) считать по-прежнему от left — не трогать.
Тот же файл, что КР-2/3/5/7/8/9.

**Файлы:** `tileentity/TileEntityQuarrySC.java`, `inventory/ContainerQuarrySC.java`, `inventory/GuiQuarrySC.java`

**Тексты (ключ = ru | en):**

- sc.quarrygui.left2 = осталось ~%s бл. - ~%d ч %d мин | ~%s blocks left - ~%dh %dmin

**Проверки:** Ручная: карьер с фильтром «только руда» на большом участке — прогноз времени в разы меньше, «Готово %» как раньше. SelfTestSC (по желанию): чистый `static float ema(float share, int seen, int dug)`.

### КР-7. Квантовый ключ не копирует и не вставляет настройки карьера

**★** А. Добавить в ключ вид «карьер»: копировать настройки без владельца и направления, вставлять только владельцу или оператору, карьер и Экзо считать разными видами, размеры при вставке ограничивать максимумом цели. Нужен ключ sc.wrench.kind.quarry.

**Что сделать.**

1. tileentity/TileEntityQuarrySC: `public void exportSettings(NBTTagCompound out)` = writeSettings(out) и out.removeTag("Owner"), "Facing", "OwnerId"; `public void importSettings(NBTTagCompound in)`: запомнить owner/ownerId/facing, readSettings(копия in без этих ключей), вернуть owner/ownerId/facing; sizeX/sizeZ ограничить 1..maxSize(), смещения — как экран (тот же путь проверок, что при приёме настроек экрана, если есть общий метод clamp/validate), resetCursor(), markDirty(), markBlockForUpdate.
2. item/ItemWrenchSC.copy (~стр. 481): ветка `te instanceof TileEntityQuarrySC` (allowed(player), иначе sc.chat.break.owneronly): Kind = isExo() ? "exo" : "quarry"; q.exportSettings(data). paste(): принимать TileEntityQuarrySC; fits — "quarry" ↔ не Экзо, "exo" ↔ Экзо; перед списанием — `q.allowed(player)` (владелец или оператор), иначе sc.chat.break.owneronly с владельцем; затем q.importSettings(data). Модули ключ не переносит (как у машин переносит — здесь не нужно).

**Файлы:** `tileentity/TileEntityQuarrySC.java`, `item/ItemWrenchSC.java`

**Тексты (ключ = ru | en):**

- sc.wrench.kind.quarry = карьер | quarry
- sc.wrench.kind.exo = Экзо-установка | Exo Drilling Rig

**Проверки:** SelfTestSC: экспорт/импорт на тестовом карьере: после importSettings владелец и направление прежние, sizeX ограничен maxSize(). Ручная: скопировать настройки LV-карьера ключом, вставить в другой — область/фильтр/функции совпали; вставить в Экзо — «не тот вид».

### КР-8. У карьера нет серверных настроек удержания чанков и расхода энергии

**★** А. Добавить balance.quarryChunkLoading (по умолчанию включено; если выключено, модуль не держит чанки, а подсказка и строка «Чанки» это показывают) и balance.quarryEnergy (множитель расхода).

**Что сделать.**

Координатор уже добавил ConfigSC.quarryChunkLoading (boolean, true) и ConfigSC.quarryEnergy (float 1, 0.1-10) с двуязычными комментариями; клиенты получают их с сервера (ConfigSyncSC). Исполнителю:
1. tileentity/TileEntityQuarrySC.costFor(hardness) (~стр. 305) и haulCost() (~744): итоговое eu × ConfigSC.quarryEnergy (до округления, не меньше 1). Ремонт головки/откачка (WATER_BODY_COST, FLUID_VEIN_COST, REPAIR_COST) — тоже × quarryEnergy (это расход карьера).
2. wantChunks(): `if (!com.sc.util.ConfigSC.quarryChunkLoading) return false;` в начале; holdChunks тогда отпускает тикет.
3. item/ItemQuarryModuleSC.addInformation: для Kind.CHUNK_LOADER при !ConfigSC.quarryChunkLoading — строка "§c" + Lang.tr("sc.quarry.chunks.off").
4. inventory/GuiQuarrySC (~стр. 1031): при !ConfigSC.quarryChunkLoading и модуле в карьере — строка sc.quarry.chunks.off (GuiHoloSC.WARN) вместо «Чанки удерживаются». Строка moduleWorkingLine для модуля — «не работает (выключено на сервере)».
5. README.md: одна фраза в «Настройки сервера» (ru/en) про balance.quarryEnergy и quarryChunkLoading.
Тот же файл, что КР-2/3/5/6/7/9.

**Файлы:** `tileentity/TileEntityQuarrySC.java`, `item/ItemQuarryModuleSC.java`, `inventory/GuiQuarrySC.java`, `README.md`

**Тексты (ключ = ru | en):**

- sc.quarry.chunks.off = Удержание чанков выключено на сервере (balance.quarryChunkLoading) | Chunk loading is off on this server (balance.quarryChunkLoading)

**Проверки:** SelfTestSC: при quarryEnergy == 1 costFor(1.5F) прежний; временно quarryEnergy = 2 → вдвое больше (вернуть значение в finally). Ручная: quarryChunkLoading=false — модуль в подсказке красной строкой, карьер без игрока замирает.

### КР-9. Насос, откачивая водоём, проверяет приваты на каждом блоке: сотни событий за тик

**★** А. В пределах одного откачивания запоминать ответ защитных модов для каждого чанка; дешёвую проверку полей мода оставить на каждый блок.

**Что сделать.**

tileentity/TileEntityQuarrySC:
1. Разделить forbidden(x, y, z) (~стр. 519) на fieldForbidden(x, y, z) (TileEntityFieldGeneratorSC.privateRefusingName — дёшево) и claimForbidden(x, y, z) (FakePlayer + BreakEvent); forbidden = field || claim — для копки как сейчас.
2. pumpBody (~стр. 1023): локальная `java.util.Map<Long, Boolean> claims = new java.util.HashMap<Long, Boolean>();` и вместо forbidden(x, y, z) — `fieldForbidden(x, y, z) || claimCached(claims, x, y, z)`, где ключ — ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL), значение — claimForbidden первой клетки этого чанка. Кэш живёт только в одном вызове pumpBody.
Поведение для приватов «по чанкам» не меняется; для приватов по блокам (редкость в 1.7.10) насос может взять клетку внутри чужой зоны в чанке, где первая проверенная клетка свободна — допустимо по ★.

**Файлы:** `tileentity/TileEntityQuarrySC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная (или WorldTest по желанию): откачка озера 2000 блоков — счётчик BreakEvent-ов от FakePlayer за тик (через временный слушатель в тесте) не больше числа затронутых чанков.

## БР — Броня и станции (4)

### БР-1. Магнит Н7 тянет чужой посмертный дроп, предметы, брошенные другими игроками, и предметы из чужих приватных полей

**★** Б. Не тянуть предметы и опыт в чужом приватном поле (та же проверка, что у Н3 и магнита карьера), плюс магнит не трогает посмертный дроп другого игрока 60 с (сам погибший забирает свои вещи магнитом сразу), а предметы, брошенные другим игроком, — 3-5 с. Описание магнита дополнить.

**Что сделать.**

1. util/ArmorFeature: `MAGNET_DEATH_GRACE = 1200` (60 с), `MAGNET_OTHERS_GRACE = 80` (4 с) рядом с MAGNET_THROWN_GRACE.
2. ShieldEventHandler: новый @SubscribeEvent(priority = EventPriority.LOWEST) onPlayerDrops(PlayerDropsEvent e): на сервере для каждого EntityItem из e.drops — entityData.setString(ArmorLogicSC.DIED_BY, uuid погибшего) (новая константа "scDiedBy" в ArmorLogicSC рядом с TOSSED_BY). Метки Утилизатора Подложки (ScrapperSC) не трогать.
3. item/ArmorLogicSC.magnetPulls: новая чистая `magnetPulls(String me, String thrower, String dead, int age)`: dead непустой и != me → age >= MAGNET_DEATH_GRACE; dead == me → true; thrower == me → age >= MAGNET_THROWN_GRACE; thrower непустой (чужой) → age >= MAGNET_OTHERS_GRACE; иначе true. Старая 3-аргументная — делегирует с dead = "".
4. ArmorLogicSC.magnet (~стр. 1656): брать DIED_BY из entityData; для предметов и сфер опыта — пропускать, если com.sc.ShieldEventHandler.privateFieldAgainst(p.worldObj, p, floor(e.posX), floor(e.posY), floor(e.posZ)) != null (как guardField у Н3 в SingularPowersSC ~309).
5. lang: sc.armorfn.magnet.desc (числа «8 блоков», «1 мБ/мин», «20 EU/с» — сверить с кодом и сохранить).

**Файлы:** `util/ArmorFeature.java`, `item/ArmorLogicSC.java`, `ShieldEventHandler.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.armorfn.magnet.desc = Притягивает предметы и опыт в радиусе 8 блоков. Выброшенное вами самими секунду не трогает, брошенное другими - 4 с, чужой посмертный дроп - минуту (свой - сразу); из чужой приватной зоны ничего не тянет. Гелий 1 мБ/мин, 20 EU/с | Pulls items and experience within 8 blocks to you. What you threw yourself is left alone for a second, what others threw - for 4 s, someone else's death drop - for a minute (your own - at once); nothing out of someone else's private zone. Helium 1 mB/min, 20 EU/s

**Проверки:** SelfTestSC (~стр. 2021, рядом с прежними): прежние 3-аргументные проверки проходят; magnetPulls("a","","b",100)==false, ("a","","b",1200)==true, ("a","","a",0)==true, ("a","b","",79)==false, ("a","b","",80)==true, ("a","a","",19)==false. Ручная по LAN: друг погиб рядом — его вещи минуту не летят ко мне, к нему (после возрождения) — сразу.

### БР-2. «Щит брони не работает!» в HUD радиации не называет причину

**★** А. Определять причину на сервере в том же порядке, что и проверка щита (нет тяжёлой воды, перегрев, аварийный режим, ПЭ-1, мало заряда), передавать её в свободных битах и показывать в HUD: «Щит брони не работает: нет тяжёлой воды» и т. д.

**Что сделать.**

1. item/ArmorLogicSC: `public static int radShieldFailure(EntityPlayer p)` (сервер): 1 — в надетом комплекте нет тяжёлой воды (ArmorGasSC.amountOf(ArmorGasSC.wornSet(p), HEAVY_WATER) <= 0 — у брони, где щит её требует, gasOf(RAD_SHIELD) == HEAVY_WATER); 2 — overheated(p); 3 — аварийный режим (pieceEmergency / нет гелия — тот же признак, что gasAllows); 4 — com.sc.emp.EmpArmorSC.featureSuppressed(p, RAD_SHIELD); 5 — иначе (мало заряда / pay не прошёл).
2. radiation/RadiationSC: константы `F_REASON_SHIFT = 4, F_REASON_MASK = 7`; в ветке `flags |= F_ARMOR_FAIL` (~стр. 415) — `flags |= ArmorLogicSC.radShieldFailure(p) << F_REASON_SHIFT;` чистый `public static int reasonOf(int flags) = flags >> 4 & 7`. Проверить, что send() пишет flags байтом — 7 бит хватает (сейчас занято до 8).
3. client/RadiationClientSC (~стр. 113): reason = RadiationSC.reasonOf(flags); ключ reason > 0 ? "sc.radhud.armorfail." + reason : "sc.radhud.armorfail".

**Файлы:** `item/ArmorLogicSC.java`, `radiation/RadiationSC.java`, `client/RadiationClientSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.radhud.armorfail.1 = Щит брони не работает: нет тяжёлой воды | Armour shield off: no heavy water
- sc.radhud.armorfail.2 = Щит брони не работает: перегрев | Armour shield off: overheated
- sc.radhud.armorfail.3 = Щит брони не работает: аварийный режим | Armour shield off: emergency mode
- sc.radhud.armorfail.4 = Щит брони не работает: подавлен ПЭ-1 | Armour shield off: suppressed by a PE-1
- sc.radhud.armorfail.5 = Щит брони не работает: мало заряда | Armour shield off: low charge

**Проверки:** SelfTestSC: reasonOf(F_ARMOR_FAIL | 1 << 4) == 1, reasonOf(F_FIELD | 5 << 4) == 5, reasonOf(F_ARMOR) == 0; упакованное значение < 128. Ручная: Квантовая броня с пустым баком тяжёлой воды у РИТЭГа — HUD «…нет тяжёлой воды».

### БР-3. Очки ОЧ3 и расход EU за поглощённый урон завышены на мелких ударах; на кактусе можно поставить ферму очков

**★** Б. Считать ОЧ3 один раз за удар по фактическому урону (запоминать урон удара и начислять в сумме не больше), плюс лимит ОЧ3 в минуту, как у мобов (200, рядом с KILL_CAP). Расход EU на поглощение оставить по правилам Forge.

**Что сделать.**

1. util/SingularLevel: `ABSORB_CAP = 200` рядом с KILL_CAP; чистый `absorbPoints(int whole, int used) = max(0, min(whole, ABSORB_CAP - used))`.
2. item/SingularProgressSC:
   - `public static void noteHit(EntityPlayer p, float amount)`: entityData HIT_AT = world time, HIT_LEFT = amount (новые ключи, не сохраняются — getEntityData без PERSISTED).
   - absorbed(p, points): если HIT_AT == сейчас → credit = min(points, HIT_LEFT), HIT_LEFT -= credit, к ABS_PENDING прибавлять credit; если удара не было отмечено — как сейчас (points).
   - в секундном тике (ОЧ3, ~стр. 93-101): whole → absorbPoints(whole, used), где used — ABS_PTS за текущую минуту (ключи ABS_MIN/ABS_PTS в store рядом с KILL_MIN/KILL_PTS; сменилась минута — обнулить); остаток дробной части как сейчас, «срезанное» лимитом не копится.
3. ShieldEventHandler: обработчик LivingHurtEvent (priority LOWEST, только не отменённые; есть ли уже такой — дописать в него): игрок на сервере в любой Сингулярной части → SingularProgressSC.noteHit(p, event.ammount).
4. ItemArmorSC.damageArmor — без изменений (каждая часть зовёт absorbed; общая сумма за удар теперь ограничена уроном). horizonAbsorbed (Н2) идёт через absorbed — тот же лимит удара.
5. Справочник: где описано ОЧ3 (найти «поглощ» в sc.manual.singular.* / sc.armor.singular.*) — дописать «не больше 200 очков в минуту».

**Файлы:** `util/SingularLevel.java`, `item/SingularProgressSC.java`, `ShieldEventHandler.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- (одна фраза в найденную строку справочника про ОЧ3) « Очков за поглощённый урон - не больше 200 в минуту и не больше урона самого удара.» | " Absorbed damage gives at most 200 points a minute, and no more than the blow itself dealt."

**Проверки:** SelfTestSC: absorbPoints(5, 198)==2, (5, 200)==0, (3, 0)==3; кредит удара: noteHit(4) затем 4 вызова absorbed(1) — суммарно 4, пятый — 0 (чистый помощник `static float credit(float left, float points)`). Ручная: кактус — очки растут не быстрее 200 в минуту.

### БР-4. У газовых чипов нет своих иконок

**★** А. Нарисовать 4 текстуры газовых чипов в стиле существующих, по желанию с отметкой тира (тогда 12 иконок). В коде меняются только имена текстур и, при тирах, число иконок. Показать пользователю несколько вариантов на выбор.

**Что сделать.**

1. Новый скрипт tools/gen_gas_chip_tex.py (как другие gen_*_tex.py: PIL, без сети): читает палитру/рамку существующих chipDefense.png, chipUtility.png… (resources/assets/siliconage/textures/items) и рисует 16×16: chipCryoLoop (иней-голубой змеевик), chipCryoTank (баллон со снежинкой), chipOxygenRegen (пузырь O₂ со стрелкой по кругу), chipRecuperator (две круговые стрелки и капля гелия). По 3 варианта каждой (A/B/C: разные силуэт/цвет), лист предпросмотра tools/gas_chips_preview.png (×8, подписи), и отдельно вариант A с отметкой тира (I/II/III точками в углу) — tools/gas_chips_tier_preview.png.
2. Вариант A без тиров записать в textures/items (chipCryoLoop.png, chipCryoTank.png, chipOxygenRegen.png, chipRecuperator.png) — чтобы мод сразу собирался с иконками; выбор остаётся за пользователем (в отчёте исполнителя — пути к листам предпросмотра).
3. util/ChipType: имена текстур CRYO_LOOP("chipCryoLoop"), CRYO_TANK("chipCryoTank"), OXYGEN_REGEN("chipOxygenRegen"), RECUPERATOR("chipRecuperator"); убрать TODO(textures). Порядок/мета enum не менять (append-only соблюдено).
4. item/ItemArmorChipSC — без изменений (иконка по типу). Если пользователь потом выберет тиры — отдельная правка (12 иконок, getIconFromDamage по meta).
Файлы старых текстур не удалять.

**Файлы:** `util/ChipType.java`, `tools/gen_gas_chip_tex.py (новый)`, `src/main/resources/assets/siliconage/textures/items/chipCryoLoop.png (новый)`, `src/main/resources/assets/siliconage/textures/items/chipCryoTank.png (новый)`, `src/main/resources/assets/siliconage/textures/items/chipOxygenRegen.png (новый)`, `src/main/resources/assets/siliconage/textures/items/chipRecuperator.png (новый)`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Сборка: текстуры в jar (unzip -l | grep chipCryo). Ручная: в творческой вкладке 4 газовых чипа со своими иконками.

## ОР — Оружие и инструменты (7)

### ОР-1. Повторная Точка по ещё подавленной части продлевает подавление, и невосприимчивость не наступает

**★** А. Попадание по ещё подавленной части не продлевает время, а даёт только нагрев и выкипание; стрелок видит «уже подавлено: N с» (новый ключ). Невосприимчивость наступает строго через время подавления + 20 с после первого попадания, у мобов так же.

**Что сделать.**

1. emp/EmpSC.HitResult: `public final int[] already = new int[4];` и `anyAlready()`.
2. EmpSC.applyHit (игроки, ~стр. 275-292): в цикле по частям — если s.suppressed(slot, now): r.already[slot] = (int) (s.until[slot] - now); allImmune = false; continue (until и immuneUntil не трогать). Нагрев нагрудника и выкипание гелия ниже по методу — без изменений (они и так идут при попадании).
3. mob/MobArmorSC.empHit (~стр. 405-430): то же для suppressedUntil/immuneUntil; stormHit (~444) — тоже не продлевать уже подавленную часть (вернуть false).
4. Шторм по игрокам (EmpStormSC → EmpSC / EmpArmorSC путь с until = max(...)) — не продлевать подавленную часть (найти место, где буря подавляет часть игрока).
5. emp/EmpGunLogicSC.report/reportMob (~стр. 465-510): если !r.anySuppressed() && r.anyAlready() → say(p, "sc.emp.already", EmpShotSC.seconds(max already)); иначе как сейчас.
WardenFightSC.suppress (бой Смотрителя) — не трогать (своя механика).

**Файлы:** `emp/EmpSC.java`, `mob/MobArmorSC.java`, `emp/EmpGunLogicSC.java`, `hazard/EmpStormSC.java`, `debug/WorldTestEmpSC.java`

**Тексты (ключ = ru | en):**

- sc.emp.already = Эта часть уже подавлена: ещё %s с | That piece is suppressed already: %s s left

**Проверки:** WorldTestEmpSC (новый шаг): Точка по игроку-боту, через 100 тиков вторая — until и immuneUntil не изменились, ответ already > 0; после until + 20 с — immune(). То же для MobArmorSC на тестовом бронированном мобе. SelfTestEmpSC: ключ sc.emp.already с 1 аргументом.

### ОР-2. Неверные названия в текстах: «Страж» у отражения ПЭ-1, «Казнь» вместо «Добивания», неполный список исключений

**★** А. Исправить: «Импульс отражён гравитационным куполом!» / “The Pulse was turned back by the gravity dome!”; «Казнь» → «Добивание» во всех пяти строках, “Execution” → “Execute”; в описании «Добивания» — полный список исключений; в сообщение «Последнего шанса» подставлять фактический откат (мин:с). Описания оставить с базовыми 5 минутами.

**Что сделать.**

1. lang (CRLF, „…“ / “…”): sc.emp.reflected; sc.bladefn.execute.desc; «Казнь» → «Добивание» в sc.manual.world.wardens.suit.plating.desc, sc.manual.world.executioner.elite.desc, sc.manual.world.elites.2, sc.manual.world.warlord.weapon, sc.manual.world.warden.rules.desc (ru; падеж по месту: «„Добивание“ … не добивает» и т. п.); “Execution” → “Execute” в en тех же ключей, кроме wardens.suit.plating.desc (там уже Execute). Название сущности «Exo Executioner» не трогать.
2. sc.blade.lastchance получает аргумент: item/BladeSingularSC (~стр. 939): `new ChatComponentTranslation("sc.blade.lastchance", mmss(ToolLevelSC.cooldownTicks(p, BladeFeature.LAST_CHANCE_COOLDOWN)))`, mmss = "M:SS" (например «3:45»).
3. Комментарий ConfigSC.bladeExecutePlayers координатор уже исправил.
4. Связь с МБ-5: обе правят sc.manual.world.elites.2 — окончательный текст строки дан в МБ-5 (там уже «Добивание»); делать вместе или вторым — слить.

**Файлы:** `item/BladeSingularSC.java`

**Тексты (ключ = ru | en):**

- sc.emp.reflected = Импульс отражён гравитационным куполом! | The Pulse was turned back by the gravity dome!
- sc.bladefn.execute.desc = Моб, у которого после удара осталось меньше пятой части здоровья, умирает (кроме боссов и элитных врагов Подложки, её Часовых, вожаков и стражей купола; игроков - только если разрешено в конфиге) | A mob left under a fifth of its health after a hit dies (bosses and the Substrate's elites, its Sentinels, warlords and dome wardens excepted; players only if the config allows)
- sc.blade.lastchance = „Последний шанс“: клинок удержал вас на краю. Снова - через %s | "Last chance": the blade held you back from the edge. Again in %s
- sc.manual.world.wardens.suit.plating.desc, sc.manual.world.executioner.elite.desc, sc.manual.world.warlord.weapon, sc.manual.world.warden.rules.desc = замена слова «Казнь» → «Добивание» (ru) и “Execution” → “Execute” (en), остальной текст без изменений

**Проверки:** SelfTestSC.langQuotes; проверка ru/en аргументов (sc.blade.lastchance — теперь 1 у обоих). grep по ru_RU: «Казн» не встречается.

### ОР-3. Предупреждение «перегрев через 2 с» для Точки и Импульса почти никогда не срабатывает

**★** А. Для Точки и Импульса предупреждать, когда следующий выстрел текущего режима перегреет пушку: «Следующий выстрел перегреет ПЭ-1» (новый ключ). Для Сифона оставить «за 2 с». Одна проверка в EmpMathSC для чата и HUD.

**Что сделать.**

1. emp/EmpMathSC: `public static boolean overheatWarn(int heat, boolean cooling, EmpMode mode)`: SIPHON → overheatWarn(heat, cooling) (прежняя); POINT/PULSE → heat < GUN_HEAT_MAX && heat + gunHeat(mode, cooling) >= GUN_HEAT_MAX. Прежняя двухаргументная остаётся (сифон).
2. emp/EmpGunLogicSC.heatNews(p, gun, before, after) (~стр. 437): mode = ItemEmpGunSC.modeOf(gun); условие через новую функцию; ключ — mode == SIPHON ? "sc.emp.overheat.warn" : "sc.emp.overheat.next".
3. emp/client/EmpHudSC (~стр. 117): то же условие; ключ SIPHON → sc.emp.hud.overheat.warn, иначе sc.emp.hud.overheat.next.
4. Подсказка настройки sc.empgui.warn.tip — уточнить (ниже).

**Файлы:** `emp/EmpMathSC.java`, `emp/EmpGunLogicSC.java`, `emp/client/EmpHudSC.java`, `debug/SelfTestEmpSC.java`

**Тексты (ключ = ru | en):**

- sc.emp.overheat.next = ПЭ-1: следующий выстрел перегреет пушку | PE-1: the next shot will overheat the gun
- sc.emp.hud.overheat.next = ПЭ-1: следующий выстрел - перегрев! | PE-1: the next shot overheats!
- sc.empgui.warn.tip = Сообщение и звук: в Сифоне - за %s с до перегрева, в Точке и Импульсе - когда следующий выстрел перегреет пушку. | A message and a sound: in the Siphon %s s before the gun overheats, in the Point and the Pulse when the next shot would overheat it.

**Проверки:** SelfTestEmpSC: overheatWarn(90, false, POINT)==true, (80, false, POINT)==false, (70, false, PULSE)==true, (60, false, PULSE)==false; для SIPHON совпадает с прежней на 0..100.

### ОР-4. Настройки ПЭ-1 в меню K во время набора или потока молча не применяются и потом откатываются

**★** А. Пока пушка в использовании, кнопки настроек и модулей серые с подсказкой «Нельзя во время набора или потока» (новый ключ). Выбор режима и отмена работают как сейчас.

**Что сделать.**

emp/client/GuiEmpTabSC:
1. `private static boolean busy()` = Minecraft.getMinecraft().thePlayer != null && thePlayer.isUsingItem() && ItemEmpGunSC.isGun(thePlayer.getItemInUse()).
2. В обновлении кнопок (~стр. 140-160): при busy() — enabled = false у ID_ZONE, ID_RECV.., ID_RECV_UP.., ID_LIMIT, ID_WARN, ID_MODULE.. (режимы, отмена набора и аварийная перезагрузка не трогаются).
3. action(b) (~стр. 164): в начале — если busy() и id из списка выше → return (ничего не менять у себя и не слать).
4. Подсказка: там, где вкладка рисует подсказки своих кнопок (или GuiArmorSC передаёт наведение) — для отключённой по busy() кнопки строка "§c" + Lang.tr("sc.empgui.busy").
Сервер (EmpActionsSC) не меняется.

**Файлы:** `emp/client/GuiEmpTabSC.java`

**Тексты (ключ = ru | en):**

- sc.empgui.busy = §cНельзя во время набора или потока | §cNot while charging up or streaming

**Проверки:** Ручная: зажать ПКМ Сифоном, открыть K — кнопки зоны/приёмников/лимита/модулей серые, подсказка; режим переключается.

### ОР-5. Ионный резак, Импульсный эмиттер и Плазменная винтовка стреляют без звука, луча и темпа стрельбы

**★** А. Звук выстрела и луч частиц от ствола до точки попадания или конца дальности (как у Точки ПЭ-1 и луча мобов), плюс короткая перезарядка: резак 0,5 с, эмиттер 1 с, винтовка 1,5 с.

**Что сделать.**

1. util/WeaponType: `public int cooldownTicks()` — ION_CUTTER 10, PULSE_EMITTER 20, PLASMA_RIFLE 30 (метод со switch, конструктор enum не трогать); `public int beamColor()` — резак ионно-голубой (как EntityAIBeamAttackSC для резака), эмиттер фиолетовый, винтовка оранжевая (0xRRGGBB).
2. item/ItemWeaponSC.onItemRightClick (~стр. 165), только сервер:
   - перезарядка: key = "scWeaponShot." + type.name() в player.getEntityData() (не PERSISTED); если now − last < type.cooldownTicks() → return stack (ни EU, ни выстрела); после выстрела (и при промахе) записать now. Чистый `static boolean coolingDown(long last, long now, int cd)`.
   - звук: SoundsSC.play(world, глаза игрока, "weapon.shot", 0.8F, pitch по типу: 1.9 / 1.4 / 0.9).
   - луч: конец = точка цели (target.posY + height*0.6) или, при промахе, первая твёрдая клетка по взгляду (world.func_147447_a / rayTraceBlocks) либо глаза + look × дальность; частицы reddust (цвет как EntityAIBeamAttackSC.line: «velocity» = r,g,b) через WorldServer.func_147487_a, ~2 на блок, не больше 48; при попадании — fireworksSpark у цели. Не для FakePlayer.
3. resources/assets/siliconage/sounds.json: событие "weapon.shot" (category "player", звук "field/zap", как mob.beam.shot).

**Файлы:** `util/WeaponType.java`, `item/ItemWeaponSC.java`, `src/main/resources/assets/siliconage/sounds.json`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSC: WeaponType.ION_CUTTER.cooldownTicks()==10, PULSE_EMITTER==20, PLASMA_RIFLE==30; ItemWeaponSC.coolingDown(100, 105, 10)==true, (100, 110, 10)==false. Ручная: зажатая ПКМ с резаком — 2 выстрела в секунду, луч и звук, при промахе EU тратится не чаще перезарядки.

### ОР-6. Сингулярный бур тратит за блок больше Экзо, хотя «Наследие Экзо» обещает −20%

**★** А. Цена блока у Сингулярного бура — 240 EU (Экзо × 0,8): и одиночный блок, и площадь честно дешевле на 20%, чёрная дыра (× 0,5) — 120 EU за блок. Поправить самотест (200 → 120).

**Что сделать.**

1. util/DrillType: SINGULAR — euPerBlock 400 → 240 (пятый аргумент конструктора).
2. item/DrillLogicSC.extraCost (~стр. 370): у Сингулярного бура скидка −20% уже в базовой цене — не применять legacy() второй раз: `return t == DrillType.SINGULAR ? eu : legacy(drill, eu);` (с полным комплектом (240+1)/2 = 120 = Экзо 150 × 0,8; без комплекта 240 = 300 × 0,8). Прочие функции Наследия (если считают свою цену через legacy от цены Экзо) — не трогать.
3. Тексты с числом 400 EU за блок у Сингулярного бура (поиск «400» в sc.tooltip.tool.sing.* / sc.manual.singtools.*) — обновить, если есть; sc.tooltip.tool.sing.legacy остаётся верной.
4. debug/SelfTestSC: проверка holeEu(DrillType.SINGULAR.euPerBlock, HOLE_EU_MUL) == 200 → == 120 и подпись «black hole costs: 120 EU a block…» (~стр. 6290).

**Файлы:** `util/DrillType.java`, `item/DrillLogicSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- (только если найдутся тексты с 400 EU у Сингулярного бура — заменить на 240)

**Проверки:** SelfTestSC: holeEu(...)==120; DrillType.SINGULAR.euPerBlock == DrillType.EXO.euPerBlock * 4 / 5; площадь с комплектом: (240+1)/2 == 120 < 150.

### ОР-7. Боссы дают клинку и броне по 500 очков без всякого лимита

**★** А. Не больше 3 боссов (1500 очков) за 10 минут на игрока, отдельно для клинка и брони. Счётчик хранить рядом с минутным лимитом мобов.

**Что сделать.**

1. util/SingularLevel: `BOSS_CAP = 3, BOSS_WINDOW_TICKS = 12000`; чистый `killPoints(boolean boss, int used, int bossesInWindow)`: boss && bossesInWindow < BOSS_CAP → BOSS_POINTS; boss сверх лимита → как обычный моб (min(KILL_POINTS, KILL_CAP − used)); не босс — как сейчас. Прежний killPoints(boss, used) = killPoints(boss, used, 0).
2. item/SingularProgressSC.onDeath (~стр. 225-250): в store s ключи BOSS_WIN (long, начало окна) и BOSS_N; если now − BOSS_WIN >= BOSS_WINDOW_TICKS → окно с now, N = 0; pts = killPoints(boss, KILL_PTS, N); если boss и N < BOSS_CAP → N++; если босс сверх лимита — его очки идут в KILL_PTS, как моб.
3. item/BladeSingularSC.killed (~стр. 868-901): то же в persisted(p) с ключами "scBladeBossWin"/"scBladeBossN" (переживают смерть, как KILL_MIN).
4. Тексты, где сказано «босс — 500 очков» (поиск «500» в sc.manual.singular*, sc.manual.singblade*, sc.bladefn.*harvest*) — дописать «не больше 3 боссов за 10 минут».

**Файлы:** `util/SingularLevel.java`, `item/SingularProgressSC.java`, `item/BladeSingularSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- (в найденные строки про очки за босса) « - не больше 3 боссов за 10 минут, дальше босс считается как обычный враг» | " - at most 3 bosses in 10 minutes, past that a boss counts as an ordinary enemy"

**Проверки:** SelfTestSC (~стр. 2325-2330): прежние проверки проходят; killPoints(true, 0, 0)==500, (true, 0, 2)==500, (true, 0, 3)==5, (true, 200, 3)==0, (false, 198, 0)==2.

## МС — Мост, поле, радиация, логистика (5)

### МС-1. Детали моста не защищены: чужой может сломать контроллер и унести баки

**★** А. Контроллер с владельцем и привязанные к нему детали с содержимым (конденсатор, энергопорт, газовый порт) ломают и разбирают ключом только владелец, друзья моста, творческий режим и оператор (сообщение sc.chat.break.owneronly). Плюс переключатель на экране контроллера «Выдавать газы через порты» (по умолчанию включён): владелец выключает его, и порт только принимает газы.

**Что сделать.**

1. block/BlockBridgeSC: `public static boolean refusesBreak(World w, EntityPlayer p, int x, int y, int z)` (сервер, как BlockQuarrySC.refusesBreak): контроллер — он сам; CAPACITOR/ENERGY_PORT/GAS_PORT — контроллер по IBridgePartSC.controllerPos(), если чанк загружен; отказ, если контроллер есть, !ownerless(), !trusted(p), !p.capabilities.isCreativeMode, !BlockQuarrySC.isOp(p). Непривязанные детали и маяки/якоря — свободно.
   getPlayerRelativeBlockHardness (~стр. 121): при отказе — BlockQuarrySC.tellRefused(p, "sc.chat.break.owneronly", владелец) и 0F; removedByPlayer (~240): при отказе — сообщение, markBlockForUpdate, false.
2. item/ItemWrenchSC.dismantle (~стр. 404-419): добавить `|| com.sc.block.BlockBridgeSC.refusesBreak(...)` и владельца для сообщения (контроллер привязки).
3. tileentity/TileEntityBridgeControllerSC: поле `boolean gasOut = true`, NBT "GasOut" (читать `!nbt.hasKey("GasOut") || nbt.getBoolean("GasOut")` — старые мосты выдают, как раньше), геттер; новое действие A_GAS_OUT (следующий свободный номер после A_REPAIR=25 — проверить занятые), только trusted (добавить в trustedOnly); попадает в синхронизацию экрана (флаг в том, что уже шлётся на экран).
4. tileentity/TileEntityBridgeGasPortSC (~стр. 72-92): drain(...)/canDrain(...) → отказ, если контроллер есть и !gasOut(); fill — как сейчас.
5. client/GuiBridgeControllerSC: на вкладке настроек (рядом с доступом/друзьями) кнопка-переключатель sc.bridge.gasout.on/off с подсказкой sc.bridge.gasout.tip; для недоверенных — серая.
Связь: ЭН-3 меняет isOwner/trusted контроллера на UUID — эту правку делать после или слить.

**Файлы:** `block/BlockBridgeSC.java`, `item/ItemWrenchSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `tileentity/TileEntityBridgeGasPortSC.java`, `client/GuiBridgeControllerSC.java`

**Тексты (ключ = ru | en):**

- sc.bridge.gasout.on = Выдавать газы через порты: да | Give gases out through the ports: yes
- sc.bridge.gasout.off = Выдавать газы через порты: нет | Give gases out through the ports: no
- sc.bridge.gasout.tip = Выключите, если мост стоит на открытом месте: газовый порт будет только принимать газы, и чужая труба их не выкачает | Switch it off if the bridge stands in the open: the gas port then only takes gases in, and a stranger's pipe can't drain them

**Проверки:** Ручная по LAN: друг ломает мой контроллер и конденсатор — отказ с сообщением; разбирает ключом — отказ; друг моста — может. Выключить «Выдавать газы» — труба друга к газовому порту ничего не тянет, заливка через порт работает. Старый мир: мост без "GasOut" — выдаёт.

### МС-2. Любая загрузка связки сбрасывает маршруты и паузы всех труб и пневмотруб сервера

**★** А. Как в ЭН-6 прошлой проверки: версии труб и пневмотруб хранить отдельно для каждого мира и повышать только версию своего мира и только если в связке есть труба или пневмотруба.

**Что сделать.**

tileentity/TileEntityConduitBundleSC:
1. Вместо `static int tubeVersion, pipeVersion` — `private static final java.util.Map<Integer, int[]> VERSIONS = new java.util.HashMap<Integer, int[]>();` (dim → {tube, pipe}); `private static int[] versions(World w)` (создаёт); `private int tubeVersion()` / `pipeVersion()` по worldObj; `private void bumpTube()`, `bumpPipe()` (только сервер).
2. networksMoved() (validate/invalidate/onChunkUnload, ~стр. 1209-1247): `if (tube) bumpTube(); if (pipe != null) bumpPipe();` — связка из одного кабеля ничего не трогает.
3. shapeChanged(kind) (~стр. 270) сделать нестатическим (по своему миру); onNeighbourChanged (~стр. 537-540) — свой мир.
4. Все сравнения idlePipeVersion/fluidRoutesVersion/idleTubeVersion/routesVersion (~стр. 636, 771, 795, 937, 1036, 1063) — с версией своего мира.
5. Мир выгружен — запись можно оставить (номера только растут); не очищать на остановке сервера посреди игры.

**Файлы:** `tileentity/TileEntityConduitBundleSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная: в Аду поставить/снять кабель — пневмотрубы Верхнего мира не перестраивают маршруты (пауза ЭН-5 сохраняется). SelfTestSC (по желанию): две версии для dim 0 и -1 растут независимо через пакетный помощник.

### МС-3. С явным Y мост открывает конец над потолком Ада

**★** А. В мирах без неба не открывать конец выше потолка: считать высоту мира не больше настоящей (в Аду 128, в Крае тоже 128), тогда проверка скажет «слишком высоко»; отдельная причина «над потолком мира».

**Что сделать.**

1. bridge/BridgeSpaceSC: реализация Cells.height() для мира (~стр. 433): `return w.provider.hasNoSky ? Math.min(w.getHeight(), w.provider.getActualHeight()) : w.getHeight();` Проверка столбца (~стр. 381 `y >= w.getHeight()`) — тоже через height().
2. check() (~стр. 69-74): если y + w > c.height() — ключ "sc.bridge.place.ceiling", когда c.noSky() (новый метод Cells.noSky(); у тестовых Cells — false), иначе прежний "sc.bridge.place.high".
3. Ввод Y контроллера (1-254, TileEntityBridgeControllerSC ~868 и ~1360) не меняется — отказ даёт проверка места.

**Файлы:** `bridge/BridgeSpaceSC.java`

**Тексты (ключ = ru | en):**

- sc.bridge.place.ceiling = над потолком мира | above the world's ceiling   (без аргументов, как соседний sc.bridge.place.high)

**Проверки:** SelfTestSC (BridgeSpaceSC на тестовых Cells): height 128, y 130, w 3 → free == false, причина ceiling при noSky. Ручная: в Аду Y 200 — «Над потолком мира».

### МС-4. Отметка «безопасно» координатора бесполезна для Космического моста

**★** А. Хранить две отметки — для 3x3 и для 5x5 — и показывать обе в подсказке («Наземный: да, Космический: тесно»). Из текста убрать «нет пола».

**Что сделать.**

1. bridge/BridgeItemDataSC: setPoint(s, x, y, z, dim, safe3, safe5) — "Safe" (3x3, как раньше) и новый "Safe5"; прежняя setPoint(…, safe) → safe5 = false и "Safe5" не писать; `safe5(ItemStack)`, `checked5(ItemStack)` (= есть ключ "Safe5"). copy (~стр. 67) переносит оба.
2. Места записи: item/ItemCoordinatorSC.record (~стр. 106), bridge/BridgeFarSC.toCoord (~393-399), TileEntityBridgeControllerSC (~3099-3112): считать check(...) для vortexSize(GROUND) и vortexSize(SPACE) (тот же y; для «Y авто» — autoY для 3, затем check 5 на найденном y) и писать обе. BridgeFarSC ~337 (c.setBoolean("safe", …)) — добавить "safe5", если это идёт на экран.
3. Подсказка ItemCoordinatorSC.addInformation (~стр. 163): при checked5 — строка Lang.tr("sc.tooltip.coordinator.check", fits(safe), fits(safe5)) где fits → sc.tooltip.coordinator.fits / .tight; без "Safe5" (старые предметы) — прежние строки.
4. Сообщение записи sc.coordinator.recorded.unsafe — без «нет пола»; при записи писать новую строку с обеими отметками (ключ ниже) вместо safe/unsafe.

**Файлы:** `bridge/BridgeItemDataSC.java`, `item/ItemCoordinatorSC.java`, `bridge/BridgeFarSC.java`, `tileentity/TileEntityBridgeControllerSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.tooltip.coordinator.check = Вихрь: Наземный мост - %s, Космический - %s | Vortex: Ground Bridge - %s, Space Bridge - %s
- sc.tooltip.coordinator.fits = помещается | fits
- sc.tooltip.coordinator.tight = тесно | too tight
- sc.coordinator.recorded.check = Координатор: записана точка %s %s %s - Наземный мост: %s, Космический: %s | Coordinator: point %s %s %s recorded - Ground Bridge: %s, Space Bridge: %s
- sc.coordinator.recorded.unsafe = Координатор: записана точка %s %s %s - тесно для вихря | Coordinator: point %s %s %s recorded - too tight for a vortex

**Проверки:** SelfTestSC (~4576): setPoint(c1, …, true, false) → safe true, safe5 false, checked5 true; старая setPoint(…, true) → checked5 false; copy переносит "Safe5".

### МС-5. На публичном мосту посторонние могут забить все закладки

**★** А. Добавлять закладки могут только доверенные; на публичном мосту посторонние вводят координаты и открывают портал, но закладок не создают. Кнопку «Добавить» для них скрыть.

**Что сделать.**

1. tileentity/TileEntityBridgeControllerSC: trustedOnly(a) (~стр. 2892) — добавить A_BM_ADD; addBookmark(p, …) (~3133): `if (!trusted(p)) return refuse(p, new BridgeMsgSC("sc.bridge.refuse.access", getOwner()));` (вместо allowed). Пульт и шлем и так только доверенным — их путь (тот же addBookmark) работает.
2. client/GuiBridgeControllerSC: кнопка «Добавить закладку» видима/доступна только доверенным (признак доверия на экране уже есть, раз удаление и переименование скрыты — тем же флагом).

**Файлы:** `tileentity/TileEntityBridgeControllerSC.java`, `client/GuiBridgeControllerSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная по LAN: публичный мост, друг не в списке — кнопки «Добавить» нет, портал по координатам открывает; пакет добавления от него — отказ.

## ПМ — Подложка: мир и события (6)

### ПМ-1. Арена в фазе «Удержание» — бесконечная ферма трофеев Экзо с Пустых оболочек

**★** А. Помечать Оболочки волны и не бросать с них трофей (и не трогать счётчик невезения); прошивка, гелий и опыт остаются.

**Что сделать.**

1. mob/WardenFightSC.wave() (~стр. 594): после успешного SpawnDirectorSC.spawnAt Оболочки — e.getEntityData().setBoolean(WAVE_TAG, true) (константа `public static final String WAVE_TAG = "scWardenWave"` в WardenFightSC; getEntityData сохраняется с мобом).
2. mob/MobDropsSC.onDrops (~стр. 110-150): для моба с entityData.getBoolean(WardenFightSC.WAVE_TAG) — не вызывать rollTrophy (ни трофея, ни изменения счётчика невезения ПТ-5); остальной дроп (прошивка, гелий, опыт, фракционный дроп) — как обычно. Проверить, что счётчик невезения меняется только внутри rollTrophy/вокруг него — пропустить весь этот блок.
3. Справочник арены (статья Смотрителя/«Удержание») — по желанию фраза ниже.

**Файлы:** `mob/WardenFightSC.java`, `mob/MobDropsSC.java`

**Тексты (ключ = ru | en):**

- sc.manual.world.warden.wave.note = Оболочки волн „Удержания“ трофеев не дают (прошивка и опыт - как обычно). | The Hollow Shells of the Hold's waves drop no trophies (firmware and experience as usual).   (по желанию — добавить в статью арены в BookWorldSC)

**Проверки:** WorldTestCombatSC/WorldTestFinaleSC (по желанию): Оболочка с WAVE_TAG, убитая игроком-ботом, — trophy == null, счётчик невезения не изменился. Ручная: на арене — трофеев с волн нет.

### ПМ-2. Правило requireSignal не закрывает вход в Подложку «Прыжком» корабля аддона Vehicles

**★** А. Сейчас добавить проверку записи Сигнала в SubstrateEntrySC.allowsArrival (творческий режим и операторы проходят, как сейчас). Прыжок закроется, когда следующий выпуск аддона начнёт спрашивать allowsArrival перед Прыжком. До тех пор написать в README, что с requireSignal Прыжок пока не проверяется.

**Что сделать.**

1. dim/SubstrateEntrySC.allowsArrival(p, via) (~стр. 121): после вычисления creative/op — `if (SubstrateConfigSC.requireSignal && p != null && !creative && !BlockQuarrySC.isOp(p) && !SubstrateSignalSC.knows(p)) return false;` затем прежний EntryRulesSC.lawful(...). BridgeTeleportSC.teleport (грант) не трогать — отказ там оставит корабль аддона без пилота.
2. Мост уже проверяет запись сам (TileEntityBridgeControllerSC ~2558) — поведение моста не меняется; рейсы посольств — FlightRunSC.signal.
3. README.md (ru и en, раздел о Подложке/requireSignal): «С requireSignal „Прыжок“ корабля Vehicles пока не проверяет запись Сигнала: он закроется, когда аддон начнёт спрашивать SubstrateEntrySC.allowsArrival».
Аддон не трогать.

**Файлы:** `dim/SubstrateEntrySC.java`, `README.md`, `debug/WorldTestFinaleSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** WorldTestFinaleSC (раздел 10, requireSignal): allowsArrival(бот без записи, VIA_JUMP) == false при requireSignal, == true без правила и после применения записи; оператор/творческий — true.

### ПМ-3. Пеленгатор руин раз за разом показывает одну и ту же, уже разграбленную литографию или Хранилище

**★** А. Пропускать руину ближе 48 блоков к игроку и показывать следующую ближайшую. В сообщения пеленгатора добавить X и Z и поднять число аргументов в самотесте (SelfTestHazardSC, 2 → 4).

**Что сделать.**

1. ruin/ItemRuinFinderSC.reading(world, px, pz) (~стр. 103-143): константа `SKIP_NEAR = 48`; в обоих циклах (литографии и Хранилища) пропускать точку с dist2(px, pz, x, z) < SKIP_NEAR²; выбор ближайшей из оставшихся — как сейчас. Если ничего — «нет» (sc.finder.none), использование, как и сейчас при null (проверить, тратится ли использование при none — не менять).
2. onItemRightClick (~стр. 72): к аргументам sc.finder.litho/vault добавить String.valueOf(at[1]) и String.valueOf(at[2]) (X, Z).
3. debug/SelfTestHazardSC.MESSAGE_ARGS (~стр. 139): {"sc.finder.litho", "4"}, {"sc.finder.vault", "4"}.
4. Мировой тест пеленгатора (WorldTestHazardSC/Finale, если вызывает reading у самой руины) — учесть пропуск 48 блоков.

**Файлы:** `ruin/ItemRuinFinderSC.java`, `debug/SelfTestHazardSC.java`

**Тексты (ключ = ru | en):**

- sc.finder.litho = Литография: %s, %s блоков (X %s, Z %s) | Lithograph: %s, %s blocks (X %s, Z %s)
- sc.finder.vault = Хранилище: %s, %s блоков (X %s, Z %s) | Vault: %s, %s blocks (X %s, Z %s)

**Проверки:** SelfTestHazardSC: аргументы 4 у обоих ключей ru/en. Ручная: в Хранилище пеленгатор показывает следующее, с координатами.

### ПМ-4. Предупреждение о ЭМИ-буре не говорит, какое укрытие нужно, а у мглы нет строки на экране

**★** А. Добавить числа в тексты: секунды и число твёрдых блоков над головой — в предупреждение о буре, интервал импульсов — в начало бури, видимость — в начало мглы, множитель и минуты — в Выброс. Обновить самотест. В HUD добавить строку «Мгла: М:СС» (новый ключ sc.haze.hud).

**Что сделать.**

1. lang: sc.storm.warning (2 аргумента: секунды, блоки — код уже передаёт), sc.storm.start (2: PULSE_MIN/20, PULSE_MAX/20), sc.haze.start (1: HAZE_SIGHT), sc.emission.start (2: множитель, минуты) — тексты ниже. Код EmpStormSC не меняется (числа уже передаются, стр. ~273-303).
2. hazard/client/StormClientSC.hud: если мгла идёт и известен её остаток (клиент получает его с HAZE_STATE — onState(haze, d.hazeEndAt - now)) — строка Lang.tr("sc.haze.hud", М:СС) тем же стилем и цветом, что строки бури/Выброса (порядок: буря, мгла, Выброс).
3. debug/SelfTestHazardSC.MESSAGE_ARGS (~стр. 135-136): {"sc.storm.warning","2"}, {"sc.storm.start","2"}, {"sc.haze.start","1"}, {"sc.emission.start","2"}, новый {"sc.haze.hud","1"}. (Тот же массив правит ПМ-3 — слить.)

**Файлы:** `hazard/client/StormClientSC.java`, `debug/SelfTestHazardSC.java`

**Тексты (ключ = ru | en):**

- sc.storm.warning = Небо Подложки лиловеет: через %s с ЭМИ-буря! Укройтесь: над головой не меньше %s твёрдых блоков - или поле с ЭМИ-экраном. | The Substrate's sky turns violet: an EMP storm in %s s! Take cover: at least %s solid blocks over your head - or a field with an EMP screen.
- sc.storm.start = ЭМИ-буря! Каждые %s-%s с импульс гасит часть брони мода под открытым небом. | EMP storm! Every %s-%s s a pulse suppresses a piece of the mod's armour under the open sky.
- sc.haze.start = Поднимается Кремнезёмная мгла: видно не дальше %s блоков. | A Silica Haze rises: nothing can be seen past %s blocks.
- sc.emission.start = Сингулярный выброс! Радиация в Разломе x%s ещё %s мин, а Затмение бродит чаще. | Singular Emission! The radiation in the Rift is x%s for %s min, and Eclipse roams more often.
- sc.haze.hud = Мгла: %s | Haze: %s

**Проверки:** SelfTestHazardSC: MESSAGE_ARGS с новыми числами проходят для ru и en. Ручная: /scstorm (или ожидание) — сообщения с числами, строка «Мгла: 2:31» в HUD.

### ПМ-5. Каждое новое решение о виде клетки Подложки синхронно записывает на диск все данные измерения

**★** А. Записывать только файл клеток: собрать его так же, как это делает игра, и записать напрямую (DIM-1414/data/SCSi14Cells.dat). После записи снять пометку изменения, при ошибке оставить её и написать в лог.

**Что сделать.**

dim/SubstrateCellsSC.cellKind (~стр. 184-186): вместо `w.perWorldStorage.saveAllData()` — `saveCellsNow((WorldServer) w, d)`:
1. Файл: `new File(new File(((WorldServer) w).getChunkSaveLocation(), "data"), DATA + ".dat")` (Forge: getChunkSaveLocation — папка измерения DIM-1414; perWorldStorage пишет туда же). Папку data создать при отсутствии.
2. Как MapStorage.saveData: `NBTTagCompound inner = new NBTTagCompound(); d.writeToNBT(inner); NBTTagCompound root = new NBTTagCompound(); root.setTag("data", inner);` CompressedStreamTools.writeCompressed(root, new FileOutputStream(tmp)) во временный файл рядом (SCSi14Cells.dat.tmp), затем заменить основной (удалить старый и переименовать; при неудаче — записать прямо в основной). После успеха — d.setDirty(false). Исключение — FMLLog.warning и пометку оставить (игра сохранит при обычном сохранении).
3. Остальные данные измерения больше не пишутся посреди генерации.

**Файлы:** `dim/SubstrateCellsSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** WorldTestHazardSC/Substrate (по желанию): после нового решения файл DIM-1414/data/SCSi14Cells.dat существует и читается обратно (kinds совпадают), data.isDirty() == false. Ручная: полёт по новой земле Подложки — без рывков сохранения.

### ПМ-6. Числа погоды, Сигнала, Смотрителя и фона радиации Подложки нельзя настроить в конфиге

**★** А. Добавить в раздел dimension несколько множителей: частота ЭМИ-бури и мглы, шанс Сигнала, здоровье Смотрителя, откат его добычи в днях, по желанию фон радиации. Таблицы остаются в коде и умножаются на месте, справочник берёт числа оттуда же. Как в СБ-11.

**Что сделать.**

Координатор уже добавил в SubstrateConfigSC (раздел dimension, правила — уходят клиентам через SubstrateNetSC сами): stormChanceMultiplier (1, 0-5), hazeChanceMultiplier (1, 0-4), signalChanceMultiplier (1, 0-6), wardenHealthMultiplier (1, 0.1-10), wardenLootCooldownDays (3, 0-30), backgroundRadiationMultiplier (1, 0-10), с ru/en комментариями; самотесты числа правил (28) обновлены. Исполнителю — применить:
1. hazard/StormRulesSC: `public static int chancePct()` = min(100, round(CHANCE_PCT × stormChanceMultiplier)), `hazeChancePct()` так же от HAZE_CHANCE_PCT × hazeChanceMultiplier; rolls(...) и выбор мглы — через них (константы оставить: на них опираются тесты и комментарии).
2. lore/SignalRulesSC: `chancePct()` = min(100, round(CHANCE_PCT × signalChanceMultiplier)); дневной бросок Сигнала — через неё.
3. mob/WardenRulesSC: функция здоровья (BASE_HP и PER_PLAYER) × wardenHealthMultiplier (найти, где считается максимальное здоровье Смотрителя — WardenRulesSC или EntityBreakdownWardenSC/WardenFightSC); откат добычи — SubstrateConfigSC.wardenLootCooldownDays вместо LOOT_COOLDOWN_DAYS (константу оставить как значение по умолчанию).
4. dim/SubstrateRadiationSC: фон по биому (OXIDE, RIFT) и источники (RTG_LEVEL, PILLAR_LEVEL) × backgroundRadiationMultiplier (поверх ConfigSC.radiationMultiplier, который применяет RadiationSC).
5. Справочник (BookWorldSC и статьи погоды/Сигнала/Смотрителя) и подсказки, где печатаются эти числа (20%, 25%, 15%, 600 HP, 3 дня, уровни фона), — брать из новых функций.
6. README.md: одна фраза в «Настройки сервера» про новые множители dimension.

**Файлы:** `hazard/StormRulesSC.java`, `lore/SignalRulesSC.java`, `mob/WardenRulesSC.java`, `dim/SubstrateRadiationSC.java`, `manual/BookWorldSC.java`, `README.md`, `debug/SelfTestHazardSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет (комментарии конфига уже двуязычные).

**Проверки:** SelfTestHazardSC: при множителях 1 chancePct()==20, hazeChancePct()==25, SignalRulesSC.chancePct()==15; временно stormChanceMultiplier = 10 → 100 (вернуть в finally); здоровье Смотрителя на 1 игрока == 600 при множителе 1.

## МБ — Подложка: мобы и бой (6)

### МБ-1. Командир пишет приказ в чат каждые 5 секунд боя

**★** А. Писать в чат первый приказ в бою, дальше не чаще раза в 30 с для каждого игрока. Остальные приказы показывать звуком и телеграфом, как сейчас.

**Что сделать.**

1. mob/EliteModSC: `ORDER_CHAT_TICKS = 600` (30 с) рядом с ORDER_EVERY_TICKS.
2. mob/EliteSC.order (~стр. 225-245): звук и телеграф — как сейчас каждый приказ; строку в чат игроку p отправлять, только если now − p.getEntityData().getLong("scOrderChatAt") >= ORDER_CHAT_TICKS (или ключа нет / now < last), и тогда записать now. Ключ не PERSISTED (после перезахода первый приказ снова в чат — это «первый в бою»).

**Файлы:** `mob/EliteModSC.java`, `mob/EliteSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная: бой с Командиром — первая строка приказа сразу, следующие не чаще раза в 30 с; рог и телеграф каждые 5 с. SelfTestCombatSC (по желанию): чистый `static boolean orderChatDue(long last, long now)`.

### МБ-2. Затмение может навсегда остаться в выгруженном чанке, и новое не появится

**★** А. Срок давности: если «живое» Затмение не видели 3 игровых дня, считать его погибшим, и Разлом сможет дать новое. Если потом загрузится старая копия при живом новом, она исчезнет; если записи нет — снова станет «живой».

**Что сделать.**

1. mob/SpawnDirectorSC.eclipseWatch (~стр. 511-533): после проверки `r.state == ALIVE` и до проверки загруженности места — `if (now - r.seenAt >= ECLIPSE_STALE_TICKS) { reg.died(RareRegistrySC.ECLIPSE, r.seenAt); return; }` с `ECLIPSE_STALE_TICKS = 72000` (3 игровых дня, рядом с ECLIPSE_UNSEEN_TICKS). Смерть датируется последним появлением — откат возрождения (SpawnRulesSC.ECLIPSE_RESPAWN_TICKS, 3 дня) отсчитывается от него, и новое Затмение может появиться сразу.
2. Возврат старой копии уже обрабатывает SpawnDirectorSC.rareSeen (~стр. 550): запись ALIVE другого UUID → старая копия исчезает; запись не ALIVE → копия снова «живая». Ничего не менять, только проверить, что EntityEclipseSC зовёт rareSeen при загрузке (первый медленный тик).
3. docs/plan-substrate.md: в правило ПС-30 дописать срок давности 3 дня.

**Файлы:** `mob/SpawnDirectorSC.java`, `docs/plan-substrate.md`, `debug/SelfTestHazardSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestHazardSC (рядом с eclipseChancePct, ~стр. 868): чистый помощник `SpawnDirectorSC.stale(long seenAt, long now)` (now − seenAt >= 72000); после died(seenAt) eclipseChancePct(DEAD, seenAt, seenAt + 72000, false, true) > 0.

### МБ-3. Лимит 40 мобов на измерение не растёт с числом игроков, и в него входят постоянные жители построек

**★** А. Лимит на измерение считать как наибольшее из 40 и 8 × число игроков в Подложке, а постоянных жителей в него не включать (в лимите 8 рядом с игроком они остаются).

**Что сделать.**

1. mob/SpawnRulesSC: чистый `public static int worldMax(int players) = Math.max(WORLD_MAX, NEAR_MAX * players)`; перегрузки maySpawn/fitGroup с параметром cap (лимит измерения); прежние сигнатуры = cap WORLD_MAX (самотесты с 40 проходят).
2. mob/SpawnDirectorSC.takeCensus/add (~стр. 704-760): флаг точки F_RESIDENT для постоянных жителей — у моба есть MobCombatSC.HOME_NBT в entityData (жители лагерей и Хранилищ, их Часовые и Мародёры) или ключ RareRegistrySC.keyOf(e) (Часовые, Вожаки); счётчик `censusWorld` — точки без F_RESIDENT. near(...) по-прежнему считает всех.
3. Места проверки (~стр. 228 maySpawn(near, censusN, 1, false) и fitGroup ниже, ~589 мирные — не трогать): передавать censusWorld и cap = SpawnRulesSC.worldMax(число игроков в этом мире: w.playerEntities без FakePlayer).
4. docs/plan-substrate.md, правило ПС-7: «на измерение — max(40, 8 × игроков в Подложке); постоянные жители построек в этот лимит не входят».

**Файлы:** `mob/SpawnRulesSC.java`, `mob/SpawnDirectorSC.java`, `docs/plan-substrate.md`, `debug/SelfTestCombatSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestCombatSC (~стр. 240): прежние проверки maySpawn с 40 проходят; worldMax(1)==40, (5)==40, (6)==48; maySpawn(0, 45, 1, false, cap 48)==true, (0, 48, 1, false, cap 48)==false.

### МБ-4. Патруля из трёх Оболочек не бывает, хотя справочник обещает «патрули по 2-3»

**★** Б. Привести описание к коду: наибольшая группа Оболочек — 2; в справочнике «патрули по 2», в ПС-8 — «патруль 2». Поведение, ПС-7 и тесты не меняются.

**Что сделать.**

1. manual/BookWorldSC (~стр. 1414-1415, статья Оболочек): верхняя граница группы = min(GROUP_MAX Оболочки, SpawnRulesSC.QUANTUM_NEAR_MAX), нижняя = min(GROUP_MIN, верхней); если равны — ключ sc.manual.world.shell.where.one (одно число), иначе прежний sc.manual.world.shell.where (от-до). Сейчас выйдет «патрули по 2».
2. docs/plan-substrate.md, ПС-8: «Оболочки 70 (патруль 2–3, …)» → «Оболочки 70 (патруль 2: больше двух мобов уровня Квант в 64 блоках не бывает, ПС-7; у 30% — два ремонтных дрона)».
3. SpawnRulesSC.GROUP_MAX и тесты не трогать.

**Файлы:** `manual/BookWorldSC.java`, `docs/plan-substrate.md`

**Тексты (ключ = ru | en):**

- sc.manual.world.shell.where.one = Мёртвые Узлы (вес %s); патрули по %s. | The Dead Nodes (weight %s); patrols of %s.

**Проверки:** SelfTestSC/handbook-проверка ключей (ru/en, 2 аргумента). Ручная: статья «Пустые оболочки» — «патрули по 2».

### МБ-5. Справочник обещает ×2 к трофею всем элитам, а получают его только элиты с модификатором

**★** Б. Код не менять. В статье писать «у элиты с модификатором трофей выпадает ×2» и добавить, что у элит своего вида свои шансы и ×2 к ним не применяется.

**Что сделать.**

Только lang: sc.manual.world.elites.2 (ru/en). Число аргументов не меняется (2: имя, ×N, ×N-предел — проверить фактическое число %s в строке: сейчас 3 — „%s“, ×%s, ×%s). «Казнь» в этой же строке меняет ОР-2 — окончательный текст ниже уже с «Добиванием»; делать вместе с ОР-2 или после неё.

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- sc.manual.world.elites.2 = Над головой элиты - её имя („%s“), в WAILA - звёздочка. „Добивание“ Экзо-клинка элиту не добивает. У элиты с модификатором трофей выпадает x%s (вместе с ЭМИ-бурей - не больше x%s); у элит своего вида - Экзо-палача, Вожака лагеря, Сингулярного часового, Затмения - свои шансы, и это умножение к ним не применяется. | An elite's name hangs over its head ("%s"), WAILA shows a star. The Exo Blade's Execute doesn't finish an elite. An elite with a modifier drops its trophy x%s (with an EMP storm - at most x%s); elites of their own kind - the Exo Executioner, the Camp Warlord, the Singular Sentinel, Eclipse - have their own chances, and the multiplier doesn't apply to them.

**Проверки:** SelfTestSC.langQuotes и аргументы ru/en (3 у обоих). (Английские названия сущностей сверить с en_US: entity.SiliconAge.warlord/sentinel.name.)

### МБ-6. Лимит «одна элита в 128 блоках» не действует в лагерях, налётах, Эхе моста и засадах, а справочник подаёт его как общее правило

**★** Б. Код не менять. В статье уточнить: «Бродячие мобы становятся элитой, только если в 128 блоках нет другой; в лагерях, налётах, Эхе моста и засадах караванов этого лимита нет».

**Что сделать.**

Только lang: sc.manual.world.elites.3 (ru/en), аргументы сохранить (2: число, блоки).

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- sc.manual.world.elites.3 = Бродячие мобы становятся элитой, только если рядом нет другой: не больше %s элиты в %s блоках; в лагерях, налётах, Эхе моста, засадах караванов и тайниках Сигнала этого лимита нет. | Wandering mobs become elites only with no other near: at most %s elite within %s blocks; camps, raids, the bridge's Echo, caravan ambushes and Signal caches have no such cap.

**Проверки:** SelfTestSC.langQuotes и аргументы ru/en (2 у обоих).

## ПС — Поселения, торговля, посольства (8)

### ПС-1. Диспетчеры посольства убегают с Вокзала к Ядру от обычных мобов, а у Вокзала ночью появляются мобы

**★** А. В Верхнем мире запретить появление враждебных мобов по всему ровному кругу посольства (радиус + 31, туда входит весь Вокзал), как с отступом у Подложки. Для жителей на посту (Диспетчер) убежищем считать их пост, а не Ядро: у поста они не бегут.

**Что сделать.**

1. settle/SettlementSC: `public static SettlementRegistrySC.Settlement embassyGroundAt(World w, int x, int z)` — в мире посольств (EMBASSY_DIM, governs) запись посольства, центр которой ближе radius + EmbassyLayoutSC.FLAT_EXTRA (31; взять/завести константу ровного круга из EmbassyLayoutSC ~стр. 44/48/291) по горизонтали; иначе null. Поиск — как protecting() (по реестру/сетке, без перебора всех записей, если у реестра есть индекс).
2. settle/SettlementProtectSC.onCheckSpawn (~стр. 397): дополнительно `|| SettlementSC.embassyGroundAt(e.world, (int) e.x, (int) e.z) != null` → DENY (только IMob, как сейчас).
3. settle/npc/ai/EntityAITunerFleeSC.goal(d) (~стр. 41): если t — POSTED-вид (EntityTunerSC.posted(kind()), Диспетчер) и у слота есть пост (NpcLinkSC.post(sl)) — убежище = пост: у поста (NpcLinkSC.near(t, post, CORE_SAFE)) → null (не бежит), иначе бежит к посту.
4. settle/npc/EntityTunerSC (~стр. 126-127 приоритет бегства) — не менять приоритет; только если для этого нужен доступ к посту (метод posted / slot) — открыть.

**Файлы:** `settle/SettlementSC.java`, `settle/SettlementProtectSC.java`, `settle/npc/ai/EntityAITunerFleeSC.java`, `settle/npc/EntityTunerSC.java`, `embassy/EmbassyLayoutSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestEmbassySC: чистая проверка круга (embassyGroundAt по тестовой записи: radius+30 — да, radius+32 — нет). Ручная: ночь у Вокзала — мобы не появляются; зомби у будки Диспетчера — он остаётся у поста, экран рейсов не закрывается.

### ПС-2. Тексты Капсулы возврата, Карты поселений и Кредит-чипа не учитывают посольства и рейсы

**★** А. Поправить только тексты ru/en (все %s сохранить): Капсула — «перенос туда, откуда вы ушли в Подложку», в подробностях — «после рейса посольства — на его Вокзал», где продаётся — «в поселениях и посольствах»; Карта — «купленная в посольстве — вокруг „Первого причала“»; Кредит-чип — «Деньги Наладчиков (КЧ)», рейсы Диспетчеров и жители посольств.

**Что сделать.**

Только lang (TravelEventsSC не меняется; аргументы %s в каждой строке сохранить в том же числе и порядке). Ключи: sc.capsule.go, sc.tooltip.returnCapsule.details, sc.tooltip.returnCapsule.howto, sc.tooltip.settlementMap.details, sc.tooltip.creditChip, sc.tooltip.creditChip.details.

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- sc.capsule.go = Капсула сработала: перенос туда, откуда вы ушли в Подложку. | The capsule fired: back to where you left for the Substrate from.
- sc.tooltip.returnCapsule.details = ПКМ в Подложке: %s с подготовки (любой урон её сбивает), затем вы там, откуда ушли в Подложку (после моста - у его конца, после рейса посольства - на его Вокзале), на свободном месте, не в чужом поле. Нет такой точки - на спавне Верхнего мира. Только на выход. | Right-click in the Substrate: %s s of preparation (any damage knocks it down), then you are where you left for the Substrate from (after a bridge - at its end, after an embassy flight - at its Vokzal), in a free place, never in a stranger's field. No such point - the Overworld's spawn. Out only.
- sc.tooltip.returnCapsule.howto = Продаёт Картограф прокола в поселениях и посольствах за Кредит-чипы, уже „Чужаку“. | Sold by the Rift Cartographer in the settlements and embassies for Credit Chips, to a Stranger already.
- sc.tooltip.settlementMap.details = Записывает %s Причала, ближайших к поселению, где куплена (купленная в посольстве - вокруг „Первого причала“). ПКМ - координаты в чат, а Причалы - в список целей Космического моста. | Records the %s docks nearest to the settlement it was bought at (one bought at an embassy - round the “First Dock”). Right-click - the coordinates in the chat, the docks on the Space Bridge's target list.
- sc.tooltip.creditChip = Деньги Наладчиков (КЧ) | The Tuners' money (credit chips)
- sc.tooltip.creditChip.details = Не крафтится. Наладчики платят им за ваши товары и контракты, „Пропуск Наладчиков“ при первом визите даёт %s. Им же платят за Капсулу, Карту, металлы и газы, рейсы Диспетчеров и сделки с жителями посольств. | Never crafted. The Tuners pay it for your goods and contracts; the Tuners' Pass gives %s on the first visit. The Capsule, the Map, metals and gases, the Dispatchers' flights and the embassy settlers' deals are paid in it too.
- (название «Вокзал» и «Первый причал» — сверить с тем, как они названы в других строках lang)

**Проверки:** SelfTestSC.langQuotes; аргументы ru/en совпадают (SelfTestSubstrateSC/SettlementSC — проверки префиксов sc.tooltip.*).

### ПС-3. Свой неиспользованный талон нельзя сдать, а пока он ждёт рассвета, закрыты все рейсы

**★** А. У Диспетчера, который продал талон, вместо «Купить» показывать «Сдать талон» (как уже показывается «Отказаться»): сдача возвращает КЧ, рейс дня, ознакомительный рейс и обратную половину. В строке талона дописать «вернётся на рассвете».

**Что сделать.**

1. embassy/FlightRunSC: `public static int[] giveBack(Ctx c)` рядом с decline (~стр. 524): талон k = EmbassyTicketSC.talon(c.me.rec); условия — k != null, c.s != null, k.settlement == c.s.id и талон свой (k.payer пуст или == c.me.id); иначе res(R_NOT_HERE, 0). Затем FlightSC.unbook(c.me.id); refund(c.reg, c.me.id, c.me.rec, k, c.p, "sc.flight.giveback.done") — тот же возврат, что на рассвете (КЧ, счётчик рейсов дня, ознакомительный, обратная половина — проверить, что refund всё это делает; иначе дописать в refund); c.p.inventory.markDirty(); res(OK, k.chips).
2. Действие экрана: новый код действия рейсов (append) рядом с «decline» в обработчике FLIGHT_DO (найти, где разбирается decline — FlightRunSC/TradeServerSC/сеть) → giveBack.
3. settle/client/GuiTradeSC: у Диспетчера, где куплен свой талон (как условие показа «Отказаться» для талона поручителя), — кнопка sc.flight.giveback вместо «Купить», подсказка sc.flight.giveback.tip.
4. lang: sc.flight.talon и sc.flight.talon.other — дописать «вернётся на рассвете».
Тот же GuiTradeSC правят ПС-4, ПС-5, ПС-8.

**Файлы:** `embassy/FlightRunSC.java`, `settle/client/GuiTradeSC.java`, `debug/SelfTestEmbassySC.java`

**Тексты (ключ = ru | en):**

- sc.flight.giveback = Сдать талон | Hand the talon back
- sc.flight.giveback.tip = Вернуть неиспользованный талон: КЧ, рейс дня и обратная половина - назад. | Return the unused talon: the chips, the day's flight and the return half come back.
- sc.flight.giveback.done = Талон сдан: %s КЧ возвращены. | Talon handed back: %s chips returned.   (если refund сам пишет sc.flight.refund — этот ключ не нужен)
- sc.flight.talon = Талон: %s - ждёт окна; не использован - вернётся на рассвете. | Talon: %s - waiting for the window; unused, it comes back at dawn.
- sc.flight.talon.other = Талон: %s - у Диспетчера, где куплен: %s; не использован - вернётся на рассвете. | Talon: %s - at the Dispatcher it was bought from: %s; unused, it comes back at dawn.   (en нынешней строки сверить)

**Проверки:** SelfTestEmbassySC/WorldTestEmbassy (по желанию): купить талон ботом, giveBack у того же Диспетчера — КЧ вернулись, ridesToday уменьшился, талона нет; у другого Диспетчера — R_NOT_HERE.

### ПС-4. Интендант и контракт «Снабжение» забирают любые брёвна, в том числе ценные брёвна других модов

**★** А. Сначала брать ванильные брёвна (любых пород), потом прочие; в окне обмена обводить слоты, которые уйдут, как у услуг.

**Что сделать.**

1. settle/trade/TradeOfferSC.Side: чистый `public int[] takePlan(ItemStack[] inv, int n, int first)` — номера слотов в порядке взятия: slot first (если подходит), затем два прохода — сначала стопки ванильных предметов (GameRegistry.findUniqueIdentifierFor(item).modId == "minecraft"), потом остальные; каждый слот берётся до нужного количества. takeFrom(inv, n, first) — по этому плану (поведение для одного вида предмета не меняется). Только для стороны со словарём руд (ore != null); точная стопка — как сейчас.
2. ContractTableSC (~стр. 100, 155) и TradeTableSC (~289) зовут takeFrom — ничего не менять, если они идут через Side.takeFrom (проверить; иначе перевести).
3. settle/client/GuiTradeSC.frameTaken (~стр. 1046): обводить слоты по takePlan выбранной сделки (сторона «отдаёт игрок») и выбранного контракта, а не только услуги — тем же цветом рамки.

**Файлы:** `settle/trade/TradeOfferSC.java`, `settle/client/GuiTradeSC.java`, `settle/trade/ContractTableSC.java`, `settle/trade/TradeTableSC.java`, `debug/SelfTestSettlementSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSettlementSC: takePlan на инвентаре {слот0: брёвна чужого мода (фальшивый предмет с logWood — или пропуск, если нет), слот3: дуб 16, слот5: берёза 32} при n=32 — сначала 3 и 5; с одним ванильным видом — тот же порядок, что раньше.

### ПС-5. На экране посольства не видно потолка «Доверия» 599

**★** А. В посольстве показывать фактическую прибавку с учётом потолка («+39 (потолок 599)») в строках сделок и контрактов, а искупление, которое ничего не даст, закрыть с причиной (отказ на сервере и серая строка).

**Что сделать.**

1. settle/ReputationSC: чистый `public static int gainUnder(int current, int amount, int ceiling)` = amount <= 0 || ceiling >= MAX ? amount : max(0, min(amount, ceiling − current)) (как give(..., ceiling)).
2. settle/client/GuiTradeSC: в посольстве (SettlementSC.isEmbassy — признак есть на экране) везде, где печатается прибавка «Доверия» сделки/контракта (~стр. 958, 1079, 1106, 1541-1543): показывать gainUnder(текущее доверие игрока, прибавка, ReputationSC.EMBASSY_CEILING); если она меньше полной — Lang.tr("sc.trade.rep.capped", eff, ceiling), если 0 — серым.
3. settle/trade/TradeServerSC: в проверке отказа сделки (там, где собираются причины отказа до покупки) — для ELDER_PENANCE в посольстве при c.rec.reputation >= c.ceiling() → отказ "sc.trade.refuse.ceiling" (КЧ не списываются). Экран делает строку искупления серой по той же причине (тот же путь, по которому он показывает другие отказы).

**Файлы:** `settle/client/GuiTradeSC.java`, `settle/trade/TradeServerSC.java`, `settle/ReputationSC.java`, `debug/SelfTestEmbassySC.java`

**Тексты (ключ = ru | en):**

- sc.trade.rep.capped = +%s (потолок %s) | +%s (ceiling %s)
- sc.trade.refuse.ceiling = Здесь „Доверие“ выше %s не растёт - искупление ничего не даст | “Trust” doesn't grow past %s here - the penance would give nothing

**Проверки:** SelfTestEmbassySC: gainUnder(560, 50, 599)==39, (599, 50, 599)==0, (700, 50, 599)==0, (100, -20, 599)==-20; отказ искупления при доверии 599 в посольстве.

### ПС-6. «Достройка» стирает блоки игрока на полу углов Вокзала и улицы до купола

**★** Б. При «Достройке» куски улицы и Вокзала пропускают незащищённые клетки, как это уже делает земля посольства. Защиту не расширять.

**Что сделать.**

1. embassy/ComponentEmbVokzalSC.build (~стр. 50-70): `boolean repair = EmbassyRepairSC.repairing();` в цикле — клетки углов (d2 > zone2, вне зоны причала: «the corners: plain stone») при repair пропускать (continue). Остальные клетки Вокзала в зоне причала защищены — ставятся как сейчас.
2. embassy/ComponentEmbStreetSC.build (~стр. 40-50): ветка `!domed(a, p)` (улица вне купола) при repair — пропускать; если отрезок улицы входит в зону причала (защищён) — ставить (проверка зоны, как в ComponentEmbGroundSC: bx² + bz² <= DOCK_ZONE²).
3. settle/gen/ComponentSettlementSC.put не меняется.

**Файлы:** `embassy/ComponentEmbVokzalSC.java`, `embassy/ComponentEmbStreetSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Ручная: поставить сундук вместо плиты в углу Вокзала и на улице вне купола, /scembassy repair — сундук на месте. Внутри купола разбитая плита — восстанавливается.

### ПС-7. После выключения и включения правила embassy.embassies жители посольств возвращаются по одному в сутки

**★** А. Если житель убран по правилу (посольство в этом мире, правило выключено), сразу помечать его место как «ещё не было», без таймера Клон-капсулы. После включения все вернутся сразу, как в новом посольстве.

**Что сделать.**

1. settle/SettlementRegistrySC: `public void slotNever(int id, int slot)` — состояние NEVER (0), UUID очистить, diedAt 0, markDirty (рядом со slotDied/slotAlive ~стр. 326-340).
2. settle/npc/ai/NpcLinkSC.exiled(mob) (~стр. 78): до setDead — если мир посольств (w.provider.dimensionId == SettlementSC.EMBASSY_DIM) и правило выключено (!SubstrateConfigSC.embassies): запись (SettlementRegistrySC.get(w).byId(id)) и слот моба (номер слота, который знает связь NpcLinkSC/ISettlementMemberSC); если слот ALIVE с UUID этого моба → r.slotNever(id, slot). Затем setDead, как сейчас.
3. settle/RosterSC — проверить, что слоты NEVER заселяются при загрузке посольства сразу (как в новом), без cloneCapsule-ограничений (одного в сутки, игрок рядом); если нет — заселять NEVER тем же путём, что новое поселение.
4. Комментарий embassies в SubstrateConfigSC менять не нужно (★ А).

**Файлы:** `settle/SettlementRegistrySC.java`, `settle/npc/ai/NpcLinkSC.java`, `settle/RosterSC.java`, `debug/SelfTestEmbassySC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestEmbassySC: slotNever на тестовой записи — state NEVER, uuid пуст. Ручная: embassies=false → перезапуск → жители исчезли; embassies=true → перезапуск → все на местах в первые секунды.

### ПС-8. Цены и лимиты рейсов, потолок «Доверия» и лимиты Интенданта нельзя настроить в конфиге

**★** Б. Вынести два числа: множитель цен рейсов (embassy.flightPriceMultiplier) и рейсы в сутки (embassy.flightsPerDay). Таблицы остаются в коде, как в СБ-11.

**Что сделать.**

Координатор уже добавил в SubstrateConfigSC (раздел embassy, правила — уходят клиентам через SubstrateNetSC): flightPriceMultiplier (float 1, 0.1-10), flightsPerDay (int 2, 1-20) с ru/en комментариями; самотесты (28 правил, DEFAULTS SelfTestEmbassySC) обновлены. Исполнителю:
1. embassy/EmbassyRulesSC: `public static int base(int ride)` = RIDE_BASE[ride] <= 0 ? 0 : max(1, ceil(RIDE_BASE[ride] × SubstrateConfigSC.flightPriceMultiplier)); `public static int flightsPerDay()` = max(1, SubstrateConfigSC.flightsPerDay). price(...) (~стр. 160-167) — через base(ride); проверка лимита (~219) — flightsPerDay(). Константы RIDE_BASE и FLIGHTS_PER_DAY оставить (значения по умолчанию; их проверяет SelfTestEmbassySC ~647).
2. embassy/FlightRunSC (~стр. 224-225: "left"/"max") — flightsPerDay().
3. manual/BookEmbassySC (~стр. 215, 273-276, 309, 333) — base(ride) и flightsPerDay().
4. settle/client/GuiTradeSC (~стр. 157, 326: значение по умолчанию fmax) — flightsPerDay(); цены на экране приходят с сервера (проверить, что экран не считает цену сам; если считает — через base()).
5. README.md: одна фраза про embassy.flightPriceMultiplier и flightsPerDay.

**Файлы:** `embassy/EmbassyRulesSC.java`, `embassy/FlightRunSC.java`, `manual/BookEmbassySC.java`, `settle/client/GuiTradeSC.java`, `README.md`, `debug/SelfTestEmbassySC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestEmbassySC: при множителе 1 base(ride) == RIDE_BASE[ride] для всех; временно flightPriceMultiplier = 1.5 → base(RIDE_FIRST) == 36; flightsPerDay() == 2 по умолчанию (значения вернуть в finally).

## СТ — Сквозное: сеть, сохранения, производительность, совместимость, тексты (5)

### СТ-1. Нет подсказок у кремниевой цепочки, производственных инструментов и форм, жидкостных труб, пневмотрубы и справочника

**★** А. Добавить подсказки по схеме TooltipSC. Трубы: «до N мБ/т» и красная строка «едкие жидкости разъедают трубу за ~10 с — держит только PTFE», у PTFE зелёная «стойкая к агрессивной химии». Пневмотруба: что возит и что ставится в разъём. Кремниевые материалы и инструменты: строка «для чего», под Ctrl «делается в: …»; у инструментов ещё машина, где они работают, и износ. Справочник: «ПКМ — открыть, клавиша — …». Описания по Shift для остальных кабелей дописать.

**Что сделать.**

Все тексты — только в lang (ru/en, CRLF, „…“ / “…”). Классы:
1. block/ItemBlockPipeSC.addInformation: "§7" + Lang.tr("sc.pipe.tooltip.rate", type.throughput) (поле пропускной способности PipeType — 400/1000/300/1500); type.corrosionResistant (третий аргумент PipeType) ? "§a" + sc.pipe.tooltip.ptfe : "§c" + Lang.tr("sc.pipe.tooltip.corrosive", CORROSION_TICKS / 20) (сделать TileEntityConduitBundleSC.CORROSION_TICKS публичным); TooltipSC.more(list, null, sc.pipe.tooltip.howto).
2. block/ItemBlockTubeSC.addInformation: sc.tube.tooltip, TooltipSC.more(list, sc.tube.tooltip.details, sc.tube.tooltip.howto) (4 предмета за тик, фильтр предметов и модуль скорости в разъём, режимы разъёма).
3. block/ItemBlockCableSC: остаётся как есть (Lang.trOr("sc.cable.tooltip.<type>")) — дописать ключи sc.cable.tooltip.<type> для всех CableType, кроме singular (уже есть): copper_bare (без изоляции — бьёт током/теряет больше: сверить с кодом), copper_insulated, silver, tungsten, superconductor, niobium_titanium, quantum, exo.
4. item/ItemSiliconMaterialSC.addInformation: "§7" + Lang.tr("sc.si.tooltip." + key) (для чего) + TooltipSC.more(list, null, Lang.trOr("sc.si.tooltip." + key + ".howto", null)) — «делается в: …»; key — lower-case имя SiliconMaterial (18 штук: silica_sand, metallurgical_si, electronic_grade_si, si_ingot, si_wafer, oxidized_wafer, coated_wafer, exposed_wafer, etched_wafer, doped_wafer, annealed_wafer, metallized_wafer, die, transistor, memory_chip, controller, gaas_ingot, gaas_wafer). Тексты — по рецептам ModRecipesComponents (что из чего и где).
5. item/ItemToolSC.addInformation: "§7" + sc.tool.tooltip.<key> (для чего); строка Lang.tr("sc.tool.tooltip.worksin", машины) — машины вычислить один раз (кэш) обходом RecipeRegistry.recipesFor(type) по всем MachineType, где среди входов есть этот инструмент (MachineRecipe.isSameIngredient), имена — Lang.tr машины; строка Lang.tr("sc.tool.tooltip.wear", durability) (второй аргумент SCToolType: 64/200/16/32/256); под Ctrl — sc.tool.tooltip.<key>.howto «делается в: …». 14 ключей: diamond_wire, diamond_blade, seed_crystal, photomask, sputter_target_copper, sputter_target_aluminium, sputter_target_tungsten, mold_plate, mold_blade, mold_coil, mold_target, mold_lead_frame_3, mold_lead_frame_16, mold_lead_frame_40.
6. item/ItemSCManual.addInformation (новый метод): "§7" + Lang.tr("sc.manual.tooltip", GameSettings.getKeyDisplayString(BookKeySC.KEY_BOOK.getKeyCode())) — только на клиенте (addInformation и так клиентский; BookKeySC — @SideOnly(CLIENT): обращение к нему только внутри addInformation, у класса предмета не должно появиться полей/сигнатур с клиентскими типами).

**Файлы:** `block/ItemBlockPipeSC.java`, `block/ItemBlockTubeSC.java`, `item/ItemSiliconMaterialSC.java`, `item/ItemToolSC.java`, `item/ItemSCManual.java`, `tileentity/TileEntityConduitBundleSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- sc.pipe.tooltip.rate = §7До %s мБ/т | §7Up to %s mB/t
- sc.pipe.tooltip.corrosive = §cЕдкие жидкости разъедают трубу за ~%s с - держит только PTFE | §cCorrosive fluids eat through the pipe in ~%s s - only PTFE holds them
- sc.pipe.tooltip.ptfe = §aСтойкая к агрессивной химии | §aResists aggressive chemicals
- sc.pipe.tooltip.howto = Соединяется с хранилищами жидкостей; ключ по разъёму - режим (вход / выход / выкл.) | Connects to fluid containers; a wrench on a connector - its mode (in / out / off)
- sc.tube.tooltip = §7Возит предметы между инвентарями, до 4 за тик | §7Carries items between inventories, up to 4 a tick
- sc.tube.tooltip.details = Ближайшие получатели - первыми, по очереди. В разъём ставятся фильтр предметов и модуль скорости | The nearest receivers first, taking turns. An item filter and a speed upgrade go into a connector
- sc.tube.tooltip.howto = Ключ по разъёму - режим (Помещать / Извлекать / выкл.), ПКМ по разъёму - его фильтр | A wrench on a connector - its mode (Insert / Extract / off), right-click on a connector - its filter
- sc.si.tooltip.<18 ключей> = для чего материал (одна строка) | what it is for
- sc.si.tooltip.<ключ>.howto = Делается в: <машина> (из <сырья>) | Made in: <machine> (from <input>)
- sc.tool.tooltip.<14 ключей> = для чего инструмент/форма | what it is for
- sc.tool.tooltip.<ключ>.howto = Делается в: <машина/верстак> | Made in: <machine / crafting table>
- sc.tool.tooltip.worksin = §7Работает в: %s | §7Works in: %s
- sc.tool.tooltip.wear = §7Износ: %s операций | §7Wears out in %s operations
- sc.cable.tooltip.<copper_bare|copper_insulated|silver|tungsten|superconductor|niobium_titanium|quantum|exo> = описание по Shift (уровень, потери, особенности; образец — sc.cable.tooltip.singular) | the Shift description
- sc.manual.tooltip = §7ПКМ - открыть справочник; клавиша %s над предметом - его статья | §7Right-click - open the handbook; %s over an item - its article
- (точные русские/английские тексты для 18 + 14 + 8 ключей исполнитель пишет по рецептам, в стиле соседних подсказок; Material/машины — их названия из lang)

**Проверки:** SelfTestSC: для каждого SiliconMaterial и SCToolType есть ключ sc.si.tooltip.<k> / sc.tool.tooltip.<k> в обоих lang (Lang.has или StatCollector.canTranslate); для каждого CableType — sc.cable.tooltip.<type>; PipeType PTFE — устойчивая, остальные — нет; SelfTestSC.langQuotes. Ручная: подсказки в инвентаре.

### СТ-2. Журнал поставленных блоков для Сингулярного бура: до 200 000 меток на измерение, переписывается целиком при каждом автосохранении

**★** А. Хранить метки в данных чанка: в памяти — только для загруженных чанков, без общего предела и вытеснения; метки сохраняются вместе с чанком. Старый журнал прочитать один раз и оставить только для чтения, чтобы ничего не потерялось. Нужен самотест.

**Что сделать.**

1. Новый чистый util/PlacedChunkSetSC.java: множество позиций внутри чанка, ключ int = (x & 15) | (z & 15) << 4 | y << 8 (y 0..4095); открытая адресация int[] с ростом; add/remove/contains (true только при настоящем изменении), toArray/fromArray (int[] для NBT), size.
2. Новый item/PlacedChunksSC.java (сервер, обработчики на MinecraftForge.EVENT_BUS, регистрировать в SCMod рядом с DrillEventsSC): Map<Integer dim, Map<Long chunkKey, PlacedChunkSetSC>>;
   - ChunkDataEvent.Load: прочитать "SCPlaced" (int[]) из event.getData() в набор; если в данных нет "SCPlacedV" — перенести в набор метки старого журнала этого чанка (см. 3) и пометить чанк изменённым.
   - ChunkDataEvent.Save: записать "SCPlaced" и "SCPlacedV" = 1 (только если набор не пуст или был перенос).
   - ChunkEvent.Unload: убрать набор из памяти; WorldEvent.Unload: убрать измерение.
   - API: placed(w, x, y, z), mark(w, x, y, z) (если add вернул true — chunk.setChunkModified()), forget(...) — то же.
3. item/PlacedBlocksSC (старый журнал, "SiliconAgePlaced"): только чтение — больше никогда не markDirty и не дописывается; при первом обращении к измерению загружается и раскладывается по чанкам (Map<Long chunkKey, int[]>), отдаёт и стирает в памяти метки чанка при его загрузке (пункт 2). Сам файл не трогать и не удалять.
4. DrillEventsSC и все вызовы PlacedBlocksSC.mark/forget/placed → PlacedChunksSC (поршни раз в 2 тика больше не помечают журнал изменённым, если метка уже есть).
5. util/PlacedRecordSC — больше не нужен для новых меток (оставить класс для старого журнала/тестов или как есть).

**Файлы:** `item/PlacedBlocksSC.java`, `item/DrillEventsSC.java`, `util/PlacedChunkSetSC.java (новый)`, `item/PlacedChunksSC.java (новый)`, `SCMod.java`, `util/PlacedRecordSC.java`, `debug/SelfTestSC.java`

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** SelfTestSC: PlacedChunkSetSC — add/contains/remove (повторный add → false), рост за 10 000 позиций, toArray/fromArray туда-обратно; ключ упаковки для (15, 255, 15) и (0, 0, 0). WorldTest (по желанию): поставить блок, выгрузить/загрузить чанк — placed() true; сохранение чанка пишет "SCPlaced".

### СТ-3. Справочник показывает числа радиации, свинцового костюма и взрывов по умолчанию, хотя сервер может их менять

**★** А. Подставлять в справочник фактические значения: защиту за часть и за полный костюм (как в подсказке ItemLeadSuitSC), уровни источников с множителем радиации, силу взрыва с множителем; если радиация выключена — «на этом сервере радиация выключена», если взрывы выключены — «машины не взрываются, а дымят».

**Что сделать.**

manual/BookContent.safety (~стр. 1612-1640) и статья радиозащиты (radprot):
1. Уровни источников (radline) — base × ConfigSC.radiationMultiplier (RadiationSC.fmt); переносимые (капсула, монацит) — тоже.
2. Сила взрыва в sc.manual.safety.explosions — (int) (ExplosionLogic.EXPLOSION_BASE_POWER × ConfigSC.explosionPower) (или fmt с десятой).
3. Свинцовый костюм — числа как в подсказке ItemLeadSuitSC: за часть ConfigSC.leadSuitPartProtection, полный — min(100, 4 × part); в строках sc.manual.safety.radprot.* (там сейчас «22%», «88%», «РИТЭГ 10%», «Экзо 100%», «уровень 10») заменить жёсткие числа на %s и передавать значения из кода (RadiationSC / ArmorFeature / TileEntityGeneratorSC — те же, что видит WAILA).
4. !ConfigSC.radiation → в начало статьи радиации абзац sc.manual.safety.radiation.off; !ConfigSC.explosions → в статью взрывов абзац sc.manual.safety.explosions.off.
5. Пересборка справочника уже срабатывает по ConfigSC.stamp() — менять не нужно.

**Файлы:** `manual/BookContent.java`

**Тексты (ключ = ru | en):**

- sc.manual.safety.radiation.off = §cНа этом сервере радиация выключена: источники не облучают, доза не копится. | §cRadiation is off on this server: nothing irradiates, no dose builds up.
- sc.manual.safety.explosions.off = §cНа этом сервере взрывы выключены: перегруженная машина не взрывается, а дымит и не принимает энергию. | §cExplosions are off on this server: an overloaded machine doesn't blow up, it smokes and refuses the energy.
- sc.manual.safety.radprot.* и др. строки с жёсткими числами = те же тексты, числа → %s (число аргументов ru и en одинаковое)

**Проверки:** SelfTestSC: аргументы ru/en изменённых строк совпадают; справочник собирается без «Format error» (существующая проверка книги). Ручная: radiation.multiplier=0.5 на сервере — у клиента в справочнике уровни вдвое ниже, как в WAILA.

### СТ-4. Около 84 неиспользуемых строк в lang-файлах от старых версий экранов

**★** А. Дополнить проверку в tools/ обратной сверкой «ключ есть в lang, но код его не спрашивает», с учётом ключей, собранных из частей. Затем, после ручной проверки каждой группы, удалить подтверждённые лишние ключи из обоих lang-файлов одним набором (CRLF сохранить), сделав перед этим резервную копию.

**Что сделать.**

1. Новый tools/check_lang_unused.py (Python 3, без зависимостей; запуск python -I tools/check_lang_unused.py из корня проекта): собрать ключи ru_RU и en_US; собрать строковые литералы из src/main/java (и, для страховки, из аддона SiliconAgeVehicles — только чтение, путь ../SiliconAgeVehicles/src, если есть); ключ «используется», если встречается литералом целиком или его префикс до любой точки встречается литералом, оканчивающимся точкой/конкатенацией (`"sc.quarrygui.tab." + …`, `"sc.battery.mode." + mode`, `Lang.trOr(...)`, paras(base) → base.N, ключи сущностей/предметов/блоков item.*, tile.*, entity.*, key.*, itemGroup.*, death.* и т. п. — по шаблонам). Вывод — список неиспользуемых по группам (префикс до второй точки) и число.
2. Пройти группы вручную (сверить с кодом: sc.gui.field.*, sc.storage.gui.stored, sc.fieldgui.color, sc.wrench.tip.*, sc.gui.big.stability, sc.quarrygui.ores, sc.singStation.line.progress, sc.netview.hidden и др.; sc.quarrygui.tab.* — нужны). В скрипте — список исключений для ключей, собираемых нестандартно.
3. Резервная копия обоих lang-файлов в C:/Users/Aleksandr/Documents/MineMod/backups/<ГГГГ-ММ-ДД_ЧЧММ>_before-lang-unused/ ; затем удалить подтверждённые строки из обоих файлов одним проходом скрипта (опция --apply со списком), сохранив CRLF, UTF-8 без BOM и порядок остальных строк. Сами файлы не удалять.
4. tools/check_settle_lang.py — не трогать (или вызвать из нового).

**Файлы:** `tools/check_lang_unused.py (новый)`

**Тексты (ключ = ru | en):**

- Удаляются только подтверждённые неиспользуемые ключи (список — вывод скрипта после ручной проверки); новых нет.

**Проверки:** Скрипт без --apply ничего не меняет; после удаления: сборка, самотест в игре (проверки ключей SelfTest*.KEYS проходят), повторный запуск скрипта — 0 неиспользуемых (кроме исключений).

### СТ-5. Конфиг: устаревший совет в разделе balance и комментарии без русского перевода

**★** А. Заменить комментарий balance на двуязычный: «Multipliers on the mod's balance (1 = as designed); on a server players get these values on joining / Множители баланса мода (1 — как задумано); на сервере игроки получают эти значения при входе». Ко всем одноязычным комментариям дописать русскую половину в том же стиле. Поведение не меняется.

**Что сделать.**

Сделано координатором в util/ConfigSC.java (общий файл конфига): комментарий раздела balance заменён на двуязычный; русская половина дописана к minY/maxY/veinSize, sounds.machines/volume, radiation.enabled/multiplier, machineSpeed, machineEnergy, storageCapacity, batteryCapacity, armorCapacity, armorDamageCost, fieldUpkeep, quarrySpeed, wirelessRange, wirelessLoss, quantumUpkeep, quantumChunkLoading; «Казнь» в bladeExecutePlayers → «Добивание». Исполнителю — только проверить (сборка, файл конфига в run/config после запуска клиента: комментарии на месте) и при желании выровнять README (строки про настройки уже верны — правка не обязательна).

**Файлы:** только lang-файлы (или уже сделано координатором).

**Тексты (ключ = ru | en):**

- Новых ключей нет.

**Проверки:** Сборка проходит; после запуска dev-клиента run/config/SiliconAge.cfg содержит двуязычные комментарии balance и прочих опций.

