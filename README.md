<p align="center"><img src="docs/icon.png" width="160" alt="Silicon Age"></p>

# Silicon Age: From Wafer to ExoTech

![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-green) ![Forge 10.13.4.1614](https://img.shields.io/badge/Forge-10.13.4.1614-orange) ![Status: alpha](https://img.shields.io/badge/status-alpha-red)

**[Русский](#русский) | [English](#english)**

---

## Русский

Технический мод для Minecraft 1.7.10: путь от кремниевой пластины до экзотехнологий. Вы добываете руды, строите производство полупроводников и энергосеть, а в конце собираете энергетическую броню, клинки, буры и защитные поля.

> ⚠️ **Альфа-версия.** Возможны ошибки и изменения, ломающие миры. Делайте резервные копии миров.

### Возможности
- **Производство полупроводников:** цепочка от кварца и руд до кремниевых пластин, кристаллов, чипов и контроллеров. 32 машины уровней LV–IV, улучшения машин, брак и побочные продукты.
- **Руды и переработка:** 16 руд, дробление, промывка, центрифуга, химия и жидкости. **22 блока металлов** из слитков мода (9 слитков ↔ блок, основание маяка).
- **Электропечь (LV) и индукционная печь (MV):** плавят всё, что обычная печь, без топлива; индукционная — два предмета сразу и разогрев до x3; опыт копится в печи.
- **Электролиз и тяжёлая вода:** водород и кислород из воды, тяжёлая вода и дейтерий для термоядерных реакторов.
- **Энергия LV → SV:** восемь уровней напряжения — LV, MV, HV, EV, **IV**, **QV (Квант)**, **XV (Экзо)** и **SV (Сингулярный)** до 131 072 EU/t.
  - Кабели каждого уровня с потерями, от медного до **сингулярного**; подсказка показывает пакет и общую пропускную способность; кабель под слишком высоким напряжением сгорает с дымом (и под IC2 — сразу).
  - Трансформаторы до XV↔SV, энергонакопители до ~2,1 млрд EU, **зарядные плиты**, модуль трансформатора и **универсальный модуль трансформатора** (машина или накопитель принимает любое напряжение).
- **Генераторы (22):** твердотопливный, водяное колесо, ветрогенератор, РИТЭГ, термоэлектрический, геотермальный, водородный топливный элемент, внутреннего сгорания (дизель, нефть, биотопливо…), солнечные панели от кремниевых до Нано / Квант / Экзо, паровая и газовая турбины, плазменный генератор, термоядерный реактор, **токамак**, **Токамак XV**, **экзо-сингулярный реактор** и **Сингулярный реактор**. Модули «Форсаж» и «Экономайзер».
- **Токамак XV — мультиблок 7x7x3:** 65 536 EU/t (XV). 24 катушки, оболочка из свинца, порты в стене: баки мода (жидкий гелий, водород, аргон, дейтерий) и энергохранилища. Стабильность плазмы, мягкая остановка аргоном, срыв с выбросом катушек и вспышкой излучения. Своё большое окно: схема постройки по слоям, чек-лист перед розжигом, индикаторы баков, сводка. Рядом с приёмником SV отдаёт SV одним пакетом.
- **Сингулярный реактор — мультиблок 7x7x5:** 131 072 EU/t (SV). Микро-чёрная дыра в двух кольцах гравитационных катушек; розжиг 700 млн EU из хранилищ-портов, дейтерий и капсула. Масса дыры: окно 40–70% — норма, лёгкая дыра мощнее, но может испариться; подача капсул вручную или «Авто». Гелий 12 мБ/т, удержание, мягкая остановка аргоном, выброс и испарение без разрушения мира. Своё окно и [схема постройки](docs/singular-build-layers.png) ([объёмный вид](docs/singular-build-3d.png)).
- **Компрессор материи (IV):** сжимает обычные предметы в «массу» (тяжёлые металлы — x4), 576 массы — капсула сжатой материи, топливо Сингулярного реактора; ценное и предметы с данными не принимает. Режим **«жидкая материя»**: вместо капсулы — 100 мБ сингулярной материи во внутренний бак (трубы, ведро, ячейка). Работающий Сингулярный реактор тоже понемногу собирает сингулярную материю.
- **Беспроводная энергия:** передатчики и приёмники LV–SV (связь картой, дальность и потери по уровню) и **квантовый транслятор** — до 32 768 EU/t без потерь в любое измерение на паре запутанных кристаллов.
- **Радиация:** РИТЭГ и реакторы излучают, свинец и вода экранируют, доза копится и вызывает эффекты. Защита: свинцовые блоки и стекло, свинцовый кожух, свинцовый костюм, радиационный щит брони и поля, дозиметр, радиопротектор, дезактивационный душ.
- **Переносные аккумуляторы LV–SV:** от кремниевой батарейки (40 000 EU) до «Сингулярного ядра» (16 млрд EU); режимы Shift + ПКМ — заряжать броню, предмет в руке или всё с собой. **Слот аккумулятора** под шкалой энергии у всех машин, карьеров и генератора поля (питает их) и у генераторов (заряжается выработкой); автоматика кладёт и забирает аккумуляторы.
- **Кнопка питания и красный камень** у машин, генераторов, накопителей, карьеров и генератора поля: выключенный блок не берёт энергию и не работает. Машины, накопители, генераторы и реакторы, генератор поля, карьер, душ и передатчики **ставятся выключенными** — длинную линию можно строить без риска взрыва.
- **Кремниевый карьер LV–EV и Экзо-буровая установка (XV):**
  - Своё меню из 7 вкладок, область с картой и лазерной рамкой в мире, сменные головки бура.
  - **24 модуля**: скорость, удача, шёлковое касание, дробление, промывка, центрифуга, насос, магнит, радиус, жила, двойной бур, утилизатор, защита от жидкостей, мягкий режим, ремонт головки, энергоэкономия, **расширенный бак**, **насосная жила**, **удержание чанков** и другие. Слоты модулей растут с уровнем.
  - **Насос с баком из отсеков** (до 4 жидкостей) и вкладка **«Баки»**: сторона выдачи, очистка за энергию, закрепление жидкости, автовыдача, фильтр жидкостей, действие при полном баке. Насос забирает водоём целиком, «бесконечная» вода не восстанавливается.
  - Экзо-установка лазером поднимает руду из недр; линзы руды, резонатор, стабилизатор и глубинный сканер (руды других модов).
- **Ключи трёх уровней:** поворот машин, демонтаж с сохранением заряда и содержимого, копирование настроек.
- **Логистика:** связки кабелей, труб и пневмотрубок в одном блоке (как в Ender IO), фильтры предметов, переносные баки.
- **Энергоброня Нано / Квант / Экзо:** больше 20 функций (полёт, ночное зрение, сканеры руд и существ, солнечная плёнка, щит, восстановление, импульс уничтожения и другие), чипы, режимы питания, нагрев, бонусы полного комплекта.
  - У каждого комплекта свой стиль и объёмные детали на модели.
  - Огни брони светятся в темноте и показывают заряд. Цвет огней выбирается, есть аура полного комплекта.
  - Все функции включаются и назначаются на клавиши в меню **K**.
- **Газы брони («Система жизнеобеспечения»):** жидкий гелий (охлаждение), кислород (дыхание, в том числе в космосе Galacticraft), водород (полёт и рывок), аргон, криптон, тяжёлая вода и дейтерий. **Строгие газы:** каждая функция работает только при своём газе, без него она серая с подсказкой. Квант и Экзо без гелия уходят в **аварийный режим** (только HUD, защита как у железной брони). Заправка — из ячеек и вёдер в меню K или на станции.
- **Меню брони (K) переделано:** строка состояния (заряд, нагрев, режим, аварийный режим), все 4 части с полосками газов, у каждой функции — её газ и кнопка клавиши, вкладка «Газы» с запасом времени и кнопкой «Заправить всё»; подстраивается под маленькие экраны.
- **Станция обслуживания брони (MV):** 4 слота брони, 8 баков газов, заряжает и заправляет броню в слотах и того, кто стоит на станции; трубы, вёдра, ячейки, модули ускорения, трансформатора и расширенного бака.
- **Сингулярная броня — четвёртый костюм:** защита 5/10/8/5, 64 млн EU в каждой части, зарядка SV, все функции Экзо на 20% меньше газа и **около 20 своих функций**: гравитационный полёт, магнит, гравитационный якорь, антигравитация и спасение из Пустоты, горизонт событий, фазовый рывок, гравитационный пресс и захват, замедление времени, чёрная дыра, гравитационный купол, режим сингулярности, резонанс, гравитационный сканер, чувство угрозы, анализатор и другие. Новый газ — **сингулярная материя**.
  - **Уровни 1–5** у каждой части: очки за газы, полёт, бои и исследование мира, задания на каждый уровень, бонусы уровня и синхронизации, ветки выбора на уровнях 3 и 5.
  - **11 цветовых схем**, **профили функций** «Бой / Шахта / Полёт» (клавиша **P**), откаты функций на экране, вкладка «Уровень» в меню K.
- **Сингулярная станция обслуживания (SV):** всё от станции брони плюс **модернизация** частей до следующего уровня, **преобразование** Экзо-брони в сингулярную, смена ветки, **перенос** уровня на новую часть, **синхронизация** отстающих частей, выбор цветовой схемы. **Гравитационные стабилизаторы** рядом (до 4) и работающий Сингулярный реактор ускоряют процесс. Голограмма брони, вращающиеся кольца.
- **Наземный и Космический мост:** кольцо из гравитационных катушек открывает портал **мгновенно в любую точку** (Космический — в другое измерение), на земле или **в воздухе** с мягкой посадкой. Контроллер, конденсатор сингулярности, энергопорт и газовый порт, модули (навигационный компьютер, компенсатор массы, охладитель кольца, щит портала, маяк-приёмник, межпространственный якорь).
  - **Пульты** с пятью режимами («Домой», «От меня в точку», «С базы удалённо», «Забрать друга», «Ко мне»), **координатор** точек, закладки, друзья и согласие на перенос.
  - **Связь с сингулярной бронёй:** модуль в шлеме, вкладка «Мост» в меню K, клавиши **H** / **J**.
  - Износ и ремонт кольца, стабильность, перегрев, помехи, «знакомые места» и разброс выхода.
- **Преобразователь энергии:** EU ⇄ **RF**, **джоули Mekanism** и **gJ Galacticraft** (с картами Mekanism и Galacticraft), курсы и потери в конфиге, режим каждой грани, модули. Необязателен: без этих модов у него нет рецепта, а поставленный работает как буфер EU.
- **Энергоклинки** трёх уровней: режущая кромка, энергоблок, широкий взмах, энергетическая волна, «Добыча» до V.
- **Электробуры** трёх уровней: площадь 3x3 и 5x5, туннель, жила, шёлковое касание, удача до V, автоплавка, лазер, связь с сундуком. Питаются от надетой брони.
- **Генератор поля:** кластер до 8 узлов, 5 форм поля.
  - Защита от мобов, снарядов и взрывов.
  - Запрет спавна, приватная зона со списком доступа (защищены и животные, рамки, вагонетки), лечение союзников; поле нельзя развернуть поверх чужого.
  - **Беспроводная зарядка** предметов мода, IC2 и RF: модуль усилителя, приоритет, резерв буфера, искры.
  - Модули энергонакопителя и трансформатора, управление редстоуном, схема поля в экране генератора.
  - Вкладка **«Зона»**: радиус, высота, смещение, центр и форма поля с предпросмотром; граница, бегущий пунктир, анимация, яркость, лучи между узлами, гудение, три цвета RGB и 24 готовых цвета.
  - **Защита от дождя:** внутри поля нет дождя и снега, вода не замерзает, молнии гасятся.
  - Режимы беспроводной зарядки: всё сразу, сначала броня / рука, самые разряженные или почти полные, порог «заряжать ниже N%».
- **Меню-голоэкраны:** у каждой машины и генератора свой экран с анимированной сценой процесса (дробилка, CVD, степпер, турбины, реакторы, солнечные панели…), шкала энергии с процентами, полноразмерные баки с текстурой жидкости; тот же стиль у карьера, генератора поля и страниц NEI.
- **Энергонакопители:** компаратор, выход поворачивается ключом, слот разрядки, модули (трансформатор, объём, форсаж, **расширитель выхода** — до 3 выходных граней, **адаптивный трансформатор** — повышает выход до уровня самого слабого потребителя, **универсальный трансформатор**), до 4 слотов зарядки на старших уровнях.
- **Жидкости:** вёдра для всех 22 жидкостей мода, заливка и слив ведром или капсулой по машине, модуль расширенного бака и очистка баков за энергию.
- **Иллюстрированный справочник инженера:** 14 разделов плитками, больше 175 статей с иконками предметов, сетками крафта, рецептами машин, картами руд, схемами мультиблоков и рисунками; поиск, закладки, «первые шаги» с галочками. **«Путь развития»** с маршрутами по уровням энергии, реакторам и броне, раздел **«Компоненты»** (все детали и материалы: где делаются и куда идут), **«Справочная»** (клавиши, совместимость, жидкости и газы, все модули), отдельный раздел о мосте. У каждого предмета мода есть статья. Выдаётся при первом крафте любого предмета мода.
- **Звуки** работающих машин и генераторов, **WAILA** для всех блоков, **настройки баланса** в конфиге (скорость и расход машин, ёмкости, дальность беспроводной энергии и другое); на сервере настройки приходят игрокам с сервера. Блоки с содержимым ломаются только киркой — рукой ничего не потеряешь.

### Управление
| Клавиша | Действие |
|---|---|
| **K** | Меню брони, клинка, бура и режима питания: функции и их клавиши |
| **R** | Рывок (Экзо-поножи) |
| **P** | Следующий профиль функций (Сингулярная броня) |
| **H** / **J** | Мост «Домой» / к последней цели (шлем со связью с мостом), Shift + J — запомнить место |
| **G** | Статья справочника о предмете под курсором в инвентаре (или в руке) |
| **Гаечный ключ** | ПКМ — повернуть механизм, Shift + ПКМ в воздух — сменить режим (у электро- и квантового ключа) |
| **Shift / Ctrl** | Подробности и управление в подсказках предметов |

### Требования и установка
1. Minecraft **1.7.10**, Forge **10.13.4.1614** (или новее для 1.7.10), Java 8.
2. Скачайте `SiliconAgeAlpha-0.1.7.jar` на странице [Releases](../../releases) и положите в папку `.minecraft/mods`.

### Моды, которые помогут (необязательны)
| Мод | Что даёт вместе с Silicon Age |
|---|---|
| **IndustrialCraft 2 Experimental** (2.2.828+) | Общая энергосеть с машинами и кабелями IC2, зарядка брони, клинков и буров в зарядниках IC2, зарядные плиты и генератор поля заряжают и предметы IC2 |
| **Not Enough Items** + **CodeChickenCore** | Страницы рецептов всех машин и генераторов (с жидкостями, шансами и EU/t), переход к рецептам из экрана машины |
| **WAILA** (1.5.10) | Подсказка при наведении: заряд, выработка, прогресс машин, жидкости в баках, беспроводная связь, излучение |
| **Industrial Upgrade** | «Форсаж» и расширитель выхода работают и в накопителе, который питает сеть IC2 |
| **Galacticraft** | Шлем с включённым дыханием и кислородом в баке заменяет кислородное снаряжение; преобразователь энергии меняет EU на gJ |
| **BuildCraft**, **Thermal Expansion** (CoFH), **Ender IO** и другие RF-моды | Их гаечные ключи поворачивают машины и работают на трубах и связках Silicon Age; генератор поля заряжает их RF-предметы; преобразователь энергии меняет EU на RF |
| **Mekanism** (не проверено) | Преобразователь энергии меняет EU на джоули Mekanism и работает с его кабелями. Совместимость написана по API, в игре с Mekanism не проверялась |

MJ, Universal Electricity и Botania не поддерживаются.

### Сборка из исходников
1. Нужны JDK 8 и Gradle 2.14 (ForgeGradle 1.2).
2. Положите в папку `libs/` jar-файлы для компиляции (в репозиторий они не входят, в мод не упаковываются):
   - `industrialcraft-2-2.2.828-experimental.jar`, `NotEnoughItems-1.7.10-1.0.5.120-universal.jar`, `CodeChickenCore-1.7.10-1.0.7.48-universal.jar`, `CodeChickenLib-1.7.10-1.1.3.141-universal.jar`, `Waila-1.5.10_1.7.10.jar`;
   - `GalacticraftCore-1.7-3.0.12.504.jar`;
   - `rf-api-compileonly.jar` — классы `cofh/api/energy` из любого мода, который включает RF API (например, Industrial Upgrade или CoFH Core): распакуйте папку `cofh/api/energy` и упакуйте её в jar (`jar cf libs/rf-api-compileonly.jar cofh/api/energy`);
   - `mekanism-api-stub-compileonly.jar` — заглушка API Mekanism, собирается скриптом `tools/mekanism-api-stub/build.sh` (из корня проекта, после первой сборки ForgeGradle).
3. Выполните `gradle build`. Готовый jar появится в `build/libs/`.

### Лицензия
© 2026 Aleksandr. Все права защищены. Код открыт для просмотра; копирование, изменение и распространение (в том числе в сборках) — только с разрешения автора.

---

## English

A tech mod for Minecraft 1.7.10: the way from a silicon wafer to ExoTech. You mine ores, build semiconductor production and an energy network, and end up with energy suits, blades, drills and protective fields.

> ⚠️ **Alpha.** Expect bugs and world-breaking changes. Back up your worlds.

### Features
- **Semiconductor fabrication:** a chain from quartz and ores to silicon wafers, crystals, chips and controllers. 32 machines from LV to IV, machine upgrades, defects and by-products.
- **Ores and processing:** 16 ores, crushing, washing, centrifuge, chemistry and fluids. **22 metal blocks** from the mod's ingots (9 ingots ↔ a block, a beacon base).
- **Electric Furnace (LV) and Induction Furnace (MV):** they smelt everything a furnace does, without fuel; the induction one takes two items at once and heats up to x3; smelting XP is stored in the furnace.
- **Electrolysis and heavy water:** hydrogen and oxygen from water, heavy water and deuterium for the fusion reactors.
- **Energy LV → SV:** eight voltage tiers - LV, MV, HV, EV, **IV**, **QV (Quantum)**, **XV (Exo)** and **SV (Singular)**, up to 131,072 EU/t.
  - Cables for every tier with loss, from copper to **singular**; the tooltip shows the packet size and the total throughput; a cable under too high a voltage burns out with smoke (under IC2 too, at once).
  - Transformers up to XV↔SV, energy storages up to ~2.1B EU, **charge pads**, the transformer upgrade and the **universal transformer upgrade** (a machine or a storage takes any voltage).
- **Generators (22):** solid fuel, water wheel, wind turbine, RTG, thermoelectric, geothermal, hydrogen fuel cell, combustion (diesel, crude oil, biofuel...), solar panels from silicon up to Nano / Quantum / Exo, steam and gas turbines, plasma generator, fusion reactor, **tokamak**, **Tokamak XV**, the **Exo singularity reactor** and the **Singular Reactor**. Overdrive and Economizer upgrades.
- **Tokamak XV, a 7x7x3 multiblock:** 65,536 EU/t (XV). 24 coils, a lead shell, ports in the wall: the mod's tanks (liquid helium, hydrogen, argon, deuterium) and energy storages. Plasma stability, a soft stop with argon, a breakdown that throws coils out with a radiation burst. Its own large screen: the build's layers, a pre-ignition checklist, tank gauges, a summary. Next to an SV taker it gives SV as one packet.
- **Singular Reactor, a 7x7x5 multiblock:** 131,072 EU/t (SV). A micro black hole held by two rings of gravity coils; ignition takes 700 million EU from the port storages, deuterium and a capsule. The hole's mass: the 40-70% window is normal, a light hole gives more but may evaporate; capsules fed by hand or on Auto. Helium 12 mB/t, containment, a soft stop with argon, ejection and evaporation that never break the world. Its own screen and a [build guide](docs/singular-build-layers.png) ([3D view](docs/singular-build-3d.png)).
- **Matter Compressor (IV):** squeezes ordinary items into "mass" (heavy metals x4), 576 mass make a compressed matter capsule, the Singular Reactor's fuel; it refuses valuables and items with data. A **liquid matter** mode: 100 mB of Singular Matter into an inner tank instead of a capsule (pipes, bucket, cell). A running Singular Reactor slowly gathers Singular Matter too.
- **Wireless energy:** transmitters and receivers LV-SV (linked with a card, range and loss by tier) and the **quantum translator** - up to 32,768 EU/t lossless into any dimension through a pair of entangled crystals.
- **Radiation:** RTGs and reactors emit it, lead and water shield, the dose builds up and has effects. Protection: lead blocks and glass, the lead casing, the lead suit, the armour's and the field's radiation shields, a dosimeter, a radioprotector, a decontamination shower.
- **Portable batteries LV-SV:** from the silicon cell (40,000 EU) to the Singular Core (16B EU); sneak + right-click modes charge your armour, the held item or everything you carry. **A battery slot** under the energy gauge of every machine, quarry and the field generator (it powers them) and of the generators (their output charges it); automation puts batteries in and takes them out.
- **A power switch and redstone control** on machines, generators, storages, quarries and the field generator: a switched-off block takes no energy and does nothing. Machines, storages, generators and reactors, the field generator, the quarry, the shower and the transmitters are **placed switched off**, so a long line can be built with no risk of blowing up.
- **Silicon Quarry LV-EV and the Exo Drilling Rig (XV):**
  - A seven-tab screen, an area with a map and a laser frame in the world, swappable drill heads.
  - **24 modules**: speed, fortune, silk touch, crushing, washing, centrifuge, pump, magnet, radius, vein miner, twin drill, trash disposal, fluid guard, gentle mode, head repair, energy saver, **tank extension**, **fluid vein**, **chunk keeping** and more. Module slots grow with the tier.
  - **A pump with a compartment tank** (up to 4 fluids) and a **Tanks** tab: output side, clearing for energy, pinning a fluid, auto output, a fluid filter, what to do when full. The pump takes a whole body of water, so "infinite" water doesn't refill.
  - The rig's laser brings ore up from the deep; ore lenses, resonator, stabilizer and a deep scanner (other mods' ores).
- **Wrenches in three tiers:** turning machines, dismantling with charge and contents kept, copying settings.
- **Logistics:** cable, pipe and pneumatic tube bundles in one block (Ender IO style), item filters, portable tanks.
- **Nano / Quantum / Exo energy suits:** 20+ functions (flight, night vision, ore and life scanners, solar film, shield, regeneration, annihilation pulse and more), chips, power modes, heat, full-set bonuses.
  - Each suit has its own style and 3D parts on the worn model.
  - The armour lights glow in the dark and show the charge. The light colour can be picked; a full set has an aura.
  - Every function is switched and key-bound in the **K** screen.
- **Suit gases ("Life Support"):** liquid helium (cooling), oxygen (breathing, in Galacticraft space too), hydrogen (flight and dash), argon, krypton, heavy water and deuterium. **Strict gases:** every function works only with its gas, without it the function is greyed out with a hint. Quantum and Exo without helium drop into **emergency mode** (HUD only, iron-armour protection). Refilling from cells and buckets in the K screen or at a station.
- **Armour screen (K) redesigned:** a status strip (charge, heat, power mode, emergency mode), all 4 pieces with their gas bars, every function shows its gas and a key button, a Gases tab with the time left and "Refuel all"; fits small screens.
- **Armour Service Station (MV):** 4 armour slots, 8 gas tanks, charges and refuels the suits in its slots and whoever stands on it; pipes, buckets, cells, overclocker, transformer and tank extension upgrades.
- **Singular armour, the fourth suit:** protection 5/10/8/5, 64M EU in each piece, SV charging, every Exo function on 20% less gas and **about 20 functions of its own**: gravitational flight, magnet, gravitational anchor, antigravity and void rescue, event horizon, phase dash, gravity press and grab, time slowing, black hole, gravity dome, Singularity mode, resonance, gravity scanner, threat sense, analyzer and more. A new gas, **Singular Matter**.
  - **Levels 1-5** for each piece: points for gases, flight, fights and exploring, tasks for every level, level and sync bonuses, branch choices at levels 3 and 5.
  - **11 colour schemes**, **function profiles** Combat / Mining / Flight (the **P** key), cooldowns on screen, a Level tab in the K screen.
- **Singular Service Station (SV):** all of the Armour Service Station plus **modernisation** of pieces to the next level, **conversion** of Exo armour into Singular, branch change, level **transfer** to a new piece, **sync** of lagging pieces, colour scheme choice. **Gravitational Stabilisers** nearby (up to 4) and a running Singular Reactor speed it up. A hologram of the armour, turning rings.
- **Ground and Space Bridge:** a ring of gravity coils opens a portal **instantly to any point** (the Space Bridge - into another dimension), on the ground or **in the air** with a soft landing. Controller, Singularity Capacitor, energy and gas ports, modules (navigation computer, mass compensator, ring cooler, portal shield, receiver beacon, interdimensional anchor).
  - **Remotes** with five modes (Home, From me to a point, From base remotely, Fetch a friend, To me), a **coordinator** for points, bookmarks, friends and consent to be moved.
  - **Singular armour link:** a module in the helmet, a Bridge tab in the K screen, the **H** / **J** keys.
  - Ring wear and repair, stability, overheating, interference, familiar places and exit scatter.
- **Energy Converter:** EU ⇄ **RF**, **Mekanism joules** and **Galacticraft gJ** (with the Mekanism and Galacticraft cards), rates and loss in the config, a mode for every face, upgrades. Optional: without those mods it has no recipe, and one already placed works as an EU buffer.
- **Energy blades** in three tiers: cutting edge, energy block, wide sweep, energy wave, Looting up to V.
- **Electric drills** in three tiers: 3x3 and 5x5 areas, tunnel, vein, silk touch, fortune up to V, autosmelt, laser, chest link. Powered by the worn suit.
- **Field generator:** a cluster of up to 8 nodes, 5 field shapes.
  - Protection from mobs, projectiles and explosions.
  - No spawning, a private zone with an access list (animals, frames and carts protected too), healing of allies; a field can't be deployed over someone else's.
  - **Wireless charging** of the mod's, IC2 and RF items: a charge booster upgrade, priority, a buffer reserve, sparks.
  - Energy storage and transformer upgrades, redstone control, a map of the field on its screen.
  - A **Zone** tab: the field's radius, height, offset, centre and shape with a preview; outline, running dashes, animation, brightness, node beams, hum, three RGB colours and 24 ready colours.
  - **Rain shield:** no rain or snow inside the field, no water freezing, lightning put out.
  - Wireless charging modes: all at once, armour / held item first, emptiest or nearly full first, a "charge below N%" threshold.
- **Holo-screen menus:** every machine and generator has its own screen with an animated scene of its process (crusher, CVD, stepper, turbines, reactors, solar panels...), an energy gauge with the percentage and full-size tank gauges in the fluid's texture; the quarry, the field generator and the NEI pages share the style.
- **Energy storages:** comparator output, the output face turned with a wrench, a discharge slot, upgrades (transformer, capacity, overdrive, **output splitter** - up to 3 output faces, **adaptive transformer** - raises the output up to the weakest consumer's tier, **universal transformer**), up to 4 charge slots on the higher tiers.
- **Fluids:** buckets for all 22 of the mod's fluids, filling and draining machines with a bucket or a cell, a tank extension upgrade and clearing tanks for energy.
- **Illustrated engineer's handbook:** 14 chapter tiles, 175+ articles with item icons, crafting grids, machine recipes, ore cards, multiblock layouts and pictures; search, bookmarks, "first steps" with ticks. A **Progression** chapter with routes through the energy tiers, reactors and suits, a **Components** chapter (every part and material: where it is made and what it goes into), a **Reference** chapter (keys, compatibility, fluids and gases, every module), a chapter on the bridge. Every item of the mod has an article. Given on the first craft of any item of the mod.
- **Sounds** for working machines and generators, **WAILA** for every block, **balance settings** in the config (machine speed and energy, capacities, wireless range and more); on a server the players get the server's settings. Blocks that hold things break only with a pickaxe - nothing is lost by hand.

### Controls
| Key | Action |
|---|---|
| **K** | Suit, blade, drill and power mode screen: functions and their keys |
| **R** | Dash (Exo leggings) |
| **P** | Next function profile (Singular armour) |
| **H** / **J** | Bridge "Home" / to the last target (helmet with the bridge link), sneak + J remembers the spot |
| **G** | The handbook article of the item under the cursor in an inventory (or in hand) |
| **Wrench** | Right-click turns a machine, sneak + right-click in the air switches the mode (electric and quantum wrench) |
| **Shift / Ctrl** | Details and controls in item tooltips |

### Requirements and installation
1. Minecraft **1.7.10**, Forge **10.13.4.1614** (or newer for 1.7.10), Java 8.
2. Download `SiliconAgeAlpha-0.1.7.jar` from [Releases](../../releases) and put it into `.minecraft/mods`.

### Mods that help (optional)
| Mod | What it adds with Silicon Age |
|---|---|
| **IndustrialCraft 2 Experimental** (2.2.828+) | One energy network with IC2 machines and cables, suits, blades and drills charge in IC2 chargers, charge pads and the field generator charge IC2 items too |
| **Not Enough Items** + **CodeChickenCore** | Recipe pages for every machine and generator (fluids, chances, EU/t), recipes straight from a machine's screen |
| **WAILA** (1.5.10) | Look-at info: charge, output, machine progress, tank contents, wireless links, radiation |
| **Industrial Upgrade** | The overdrive and the output splitter also work in a storage that feeds an IC2 network |
| **Galacticraft** | A suit helmet with breathing on and oxygen in its tank replaces the oxygen gear; the Energy Converter exchanges EU for gJ |
| **BuildCraft**, **Thermal Expansion** (CoFH), **Ender IO** and other RF mods | Their wrenches turn machines and work on Silicon Age pipes and bundles; the field generator charges their RF items; the Energy Converter exchanges EU for RF |
| **Mekanism** (untested) | The Energy Converter exchanges EU for Mekanism joules and works with its cables. Written against the API, not yet tested in game with Mekanism |

MJ, Universal Electricity and Botania are not supported.

### Building from source
1. JDK 8 and Gradle 2.14 (ForgeGradle 1.2).
2. Put the compile-only jars into `libs/` (they are not part of this repository and are not packed into the mod):
   - `industrialcraft-2-2.2.828-experimental.jar`, `NotEnoughItems-1.7.10-1.0.5.120-universal.jar`, `CodeChickenCore-1.7.10-1.0.7.48-universal.jar`, `CodeChickenLib-1.7.10-1.1.3.141-universal.jar`, `Waila-1.5.10_1.7.10.jar`;
   - `GalacticraftCore-1.7-3.0.12.504.jar`;
   - `rf-api-compileonly.jar` - the `cofh/api/energy` classes from any mod that ships the RF API (e.g. Industrial Upgrade or CoFH Core): extract the `cofh/api/energy` folder and pack it into a jar (`jar cf libs/rf-api-compileonly.jar cofh/api/energy`);
   - `mekanism-api-stub-compileonly.jar` - a Mekanism API stub, built by `tools/mekanism-api-stub/build.sh` (from the project root, after the first ForgeGradle build).
3. Run `gradle build`. The jar is written to `build/libs/`.

### License
© 2026 Aleksandr. All rights reserved. The source is open to read; copying, modifying and redistributing it (modpacks included) only with the author's permission.
