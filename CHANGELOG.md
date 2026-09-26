# Changelog / Журнал изменений

Изменения, которые ещё не вышли в релизе, копятся в разделе «Не выпущено»; при следующем релизе он станет его описанием.
Changes not yet released collect under "Unreleased"; at the next release it becomes its notes.

## Не выпущено / Unreleased

### Русский
- Генератор поля: новая вкладка «Улучшения» на экране главного узла — 4 слота под модули энергонакопителя, каждый модуль +10 000 EU к буферу поля (считаются до 16, как в машинах). Там же шкала буфера и ваш инвентарь; Shift-клик кладёт модули в слоты. Вынимать модули могут только владелец и игроки из списка доступа. При объединении кластеров модули узла переходят к мастеру, при разрушении генератора выпадают. Справочник и подсказка модуля обновлены.
- Генератор поля принимает и модуль трансформатора: с ним главный узел (а через него и все узлы кластера) принимает EV без взрыва. На вкладке «Улучшения» показано, какое напряжение сейчас принимается.
- Три новых уровня энергии выше EV: **IV** (8 192 EU/t), **QV — Квант** (16 384 EU/t) и **XV — Экзо** (32 768 EU/t). Для каждого — энергонакопитель (IV — 300 млн EU, QV — 1 млрд EU, XV — 2 млрд EU), зарядная плита, свой кабель (ниобий-титановый, квантовый, экзо) и трансформатор (EV-IV, IV-QV, QV-XV). Накопители и плиты собираются из накопителя уровнем ниже и сохраняют его заряд. Модуль трансформатора теперь поднимает приём вплоть до XV. С IC2: IV — его уровень 5, QV и XV — уровень 6.
- Новый **Универсальный модуль трансформатора**: с одним таким модулем машина или генератор поля принимает любое напряжение от LV до XV без взрыва. Крафт очень дорогой: в станции модернизации (EV), 5 минут — 2 трансформатора QV-XV, 16 модулей трансформатора и 16 экзо-кабелей.
- Новый вид меню машин, генераторов и энергонакопителей: стальная панель с тёмной полосой заголовка (название белым, плашка уровня в ней), чёткая рамка со срезанными углами, более глубокие слоты и шкалы, желобок над инвентарём, заклёпки; панель улучшений в том же стиле. Плашки уровней IV / QV / XV получили свои цвета.

### English
- Field generator: a new Upgrades tab on the master's screen - 4 slots for energy storage upgrades, each +10,000 EU of field buffer (up to 16 count, as in machines). It also shows a buffer bar and your inventory; shift-click puts upgrades in. Only the owner and the access list can take them out. When clusters merge a node's upgrades go to the master; breaking a generator drops them. Handbook and the upgrade's tooltip updated.
- The field generator also takes the transformer upgrade: with it the master (and through it every node of the cluster) takes EV without exploding. The Upgrades tab shows the voltage it takes right now.
- Three new energy tiers above EV: **IV** (8,192 EU/t), **QV - Quantum** (16,384 EU/t) and **XV - Exo** (32,768 EU/t). Each has an energy storage (IV 300M EU, QV 1B EU, XV 2B EU), a charge pad, its own cable (niobium-titanium, quantum, exo) and a transformer (EV-IV, IV-QV, QV-XV). Storages and pads are built from the storage one tier below and keep its charge. The transformer upgrade now goes all the way to XV. With IC2: IV is its tier 5, QV and XV its tier 6.
- New **Universal Transformer Upgrade**: with just one a machine or field generator takes any voltage from LV to XV without exploding. Very expensive: EV Upgrade Station, 5 minutes - 2 QV-XV transformers, 16 transformer upgrades and 16 Exo cables.
- New look for machine, generator and energy storage screens: a steel panel with a dark title bar (white name, tier plate in it), a crisp outline with cut corners, deeper slots and gauges, a groove above the inventory, rivets; the upgrade panel matches. The IV / QV / XV tier plates have their own colours.

## 0.1.1-alpha — 2026-09-25

### Русский
**Новое**
- Квантовый нагрудник теперь умеет летать — вдвое медленнее Экзо (у Экзо скорость прежняя).
- Новый вид брони Нано / Квант / Экзо: иконки 32x32 и вдвое более детальная броня на игроке — пластины с фасками и швами, дорожки схем, светящийся кристалл на груди, переливающийся визор, светящиеся запястья и подошвы.
- Иконки 111 предметов (детали, чипы, пластины, кабели, формы, компоненты, ячейки, улучшения, оружие и др.) перерисованы в 32x32: объём, фаски, фактура и блики; кремниевые пластины нарисованы заново.
- Дроблёная и очищенная дроблёная руда (21 иконка) нарисована заново в 32x32: горка гранёных камней с прожилками и вкраплениями металла; очищенная — чистые металлические куски с бликами.
- Текстуры механизмов (74) в 32x32: лицевые панели всех машин, корпуса LV–EV, энергохранилища, трансформаторы, зарядные плиты и баки — с фасками и фактурой металла, стыкуются без швов.

**Исправлено**
- Вкладка творческого режима «Silicon Age» перемешивалась (особенно в старых мирах) — теперь предметы идут в постоянном порядке: руды → материалы → детали → инструменты и улучшения → машины → энергия → логистика → генератор поля → броня, клинок и бур каждого уровня → оружие.
- Меню брони (K): длинные описания функций во всплывающих подсказках уходили за край экрана — теперь переносятся по строкам.

### English
**New**
- The Quantum chestplate can fly now - at half the Exo speed (Exo flight is unchanged).
- New look for the Nano / Quantum / Exo suits: 32x32 icons and twice as detailed worn armour - bevelled plates and seams, circuit traces, a glowing die in the chest, an iridescent visor, glowing wrists and soles.
- Icons of 111 items (parts, chips, wafers, cables, moulds, components, cells, upgrades, weapons and more) redrawn at 32x32: volume, bevels, texture and highlights; silicon wafers drawn anew.
- Crushed and purified crushed ores (21 icons) drawn anew at 32x32: a heap of faceted rocks with metal veins and flecks; purified ones are clean, shiny metal chunks.
- Mechanism textures (74) at 32x32: all machine fronts, LV-EV casings, energy storage, transformers, charge pads and tanks - bevels and metal texture, tiling seamlessly.

**Fixed**
- The "Silicon Age" creative tab came out jumbled (especially in old worlds) - items are now listed in a fixed order: ores → materials → parts → tools and upgrades → machines → energy → logistics → field generator → each tier's suit, blade and drill → weapons.
- Armour screen (K): long function descriptions in tooltips ran off the screen - they now wrap onto several lines.

## 0.1.0-alpha — 2026-09-25

Первая альфа-версия / First alpha: производство полупроводников, энергосеть LV–EV и зарядные плиты, машины и логистика, энергоброня Нано / Квант / Экзо, энергоклинки, электробуры, генератор поля.
Semiconductor fabrication, LV–EV energy and charge pads, machines and logistics, Nano / Quantum / Exo energy suits, energy blades, electric drills, field generator.
