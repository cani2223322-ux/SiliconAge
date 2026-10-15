🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен. Заменяет `SiliconAgeBeta-0.2.0.jar`: старый jar из папки `mods` уберите. Миры 0.2.0 открываются без изменений. Аддон Silicon Age: Vehicles работает с этой версией.

**Новое**

*ПЭ-1 «Подавитель электроники»* — энергопушка без урона, которая гасит энергоброню мода. Она дешевле Нано-нагрудника и даёт игроку в слабой броне шанс против Экзо и Сингулярной.
- **«Точка»** (набор 1 с, 32 блока, 50 000 EU) — подавляет часть брони, куда попал луч: голова — шлем, корпус — нагрудник, ноги — поножи и ботинки (полёт, рывок, смягчение падения).
- **«Импульс»** (набор 3 с с нарастающим гулом, 150 000 EU, перезарядка 20 с) — подавляет всю броню.
- **«Сифон»** (удерживать ПКМ, 16 блоков) — поток перекачивает заряд брони цели в ваш аккумулятор, броню или саму пушку: Нано 6%, Квант 5%, Экзо 4%, Сингулярная 3% полного заряда в секунду. Потери 25%, по подавленной броне — без потерь; что не влезает в приёмник — сбрасывается искрами.
- **Время подавления:** Нано 16/14 с («Точка»/«Импульс»), Квант 13/11, Экзо 11/8, Сингулярная 8/6, затем 20 с невосприимчивости. Подавленная часть защищает как железная, заряд и газы не теряются, дыхание остаётся, полёт заканчивается плавным спуском.
- **Нагрев и гелий:** попадания греют броню цели и ускоряют выкипание её гелия (×2–×3 на 15–20 с); перегрев цели — после 3–4 попаданий подряд.
- **Перегрев пушки мягкий:** ~35 с непрерывного потока (с «Охлаждением» ~50 с); на 100% Сифон слабеет до 25% и идёт, пока пушка не остынет ниже 70%.
- **Модули:** Ёмкость, Фокус, Охлаждение, Эффективный сифон, Широкий канал. **ПЭ-1М** — улучшение на Станции HV (быстрее «Импульс», дольше «Точка»).
- **Защита цели:** Защитный чип (меньше время, нагрев и скорость сифона), аварийная перезагрузка (аргон + 20% заряда, раз в 60 с), флаг генератора поля «ЭМИ-экран», шанс отражения у Сингулярной брони с веткой «Гравитационный купол».
- **Правила:** учитывает PvP сервера и «своих» (список доступа, команда), работает и в чужих приватах — сервер может запретить: `weapons.pe1.respectClaims`.
- **Интерфейс:** вкладка «ПЭ-1» в меню **K** (режимы, клавиши, настройки, модули, цель под прицелом), строка «ЭМИ» и кнопка перезагрузки у брони, HUD стрелка и цели, 3D-модель «Сифон-капсула», лучи и поток частиц, статьи справочника «ПЭ-1» и «Защита от ЭМИ», конфиг `weapons.pe1`.
- **Крафт:** Импульсный эмиттер + медная катушка ×2 + конденсатор ×2 + фокусирующая линза + сенсор + контроллер + энергоячейка HV (верстак).

**Исправленные баги**
- **Броня в транспорте:** в седле (самолёт аддона Vehicles, с Galacticraft — любой транспорт) ночное зрение брони гасло через 13 с, а ежесекундные функции брони, клинка, бура и свинцового костюма (нагрев, газы, заряд, лечение) могли не срабатывать. Теперь «раз в N тактов» считается по времени мира.

Полный список — в `CHANGELOG.md`, описание пушки — `docs/plan-pe1.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional. Replaces `SiliconAgeBeta-0.2.0.jar`: remove the old jar from `mods`. 0.2.0 worlds open as they are. The Silicon Age: Vehicles add-on works with this version.

**New**

*PE-1 "Electronics Suppressor"* - an energy gun that deals no damage but shuts down the mod's power armour. It costs less than a Nano chestplate and gives a player in weaker armour a chance against Exo and Singular.
- **Point** (1 s charge-up, 32 blocks, 50,000 EU) - suppresses the armour piece the beam hits: head - helmet, body - chestplate, legs - leggings and boots (flight, dash, fall damping).
- **Pulse** (3 s charge-up with a rising hum, 150,000 EU, 20 s cooldown) - suppresses the whole suit.
- **Siphon** (hold right-click, 16 blocks) - a stream pumps the target suit's charge into your battery, armour or the gun itself: Nano 6%, Quantum 5%, Exo 4%, Singular 3% of the full charge a second. 25% loss, none from suppressed armour; what the receiver can't hold is vented as sparks.
- **Suppression time:** Nano 16/14 s (Point/Pulse), Quantum 13/11, Exo 11/8, Singular 8/6, then 20 s immunity. A suppressed piece protects like iron, charge and gases are kept, breathing stays, flight ends in a soft descent.
- **Heat and helium:** hits heat the target's suit and boil its helium faster (×2-×3 for 15-20 s); the target overheats after 3-4 hits in a row.
- **Soft gun overheat:** ~35 s of continuous stream (~50 s with Cooling); at 100% the Siphon drops to 25% and keeps going until the gun cools below 70%.
- **Modules:** Capacity, Focus, Cooling, Efficient Siphon, Wide Channel. **PE-1M** - an upgrade on the HV Upgrade Station (faster Pulse, longer Point).
- **Target protection:** the Defense chip (shorter suppression, less heat and siphon speed), an emergency reboot (argon + 20% charge, once per 60 s), the field generator's "EMP screen" flag, a reflect chance for Singular armour with the Gravity Dome branch.
- **Rules:** respects the server's PvP and friends (access list, team), works in other players' claims too - a server can forbid it: `weapons.pe1.respectClaims`.
- **Interface:** a "PE-1" tab in the **K** screen (modes, keys, settings, modules, the target in the crosshair), an "EMP" line and reboot button on the armour tabs, shooter and target HUD, a 3D "Siphon capsule" model, beams and a particle stream, handbook articles "PE-1" and "EMP protection", config `weapons.pe1`.
- **Recipe:** Pulse Emitter + copper coil ×2 + capacitor ×2 + focus lens + sensor + controller + HV energy cell (workbench).

**Bugs fixed**
- **Armour while riding:** in a saddle (the Vehicles add-on's plane; with Galacticraft - any vehicle) the suit's night vision went out after 13 s and the once-a-second work of the suit, blade, drill and lead suit (heat, gases, charge, healing) could stop. "Every N ticks" now follows the world clock.

The full list is in `CHANGELOG.md`, the gun's design in `docs/plan-pe1.md`.
