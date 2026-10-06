🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен (общая энергосеть). Заменяет `SiliconAgeAlpha-0.1.7.jar`: старый jar из папки `mods` уберите. Миры 0.1.7 открываются без изменений.

**Новое**

*Конфиг*
- **`worldgen.oreDimensions`** — список измерений, в которых генерируются руды мода.

*Генераторы и реакторы*
- Нагрев работающего реактора больше не доходит до предела: перегрев только при переполненном буфере.
- Незажжённый реактор в режиме розжига принимает **любое напряжение** — без предупреждения «взорвётся».
- Под IC2 без Industrial Upgrade лишний «Форсаж» не работает, в окне есть подсказка; Трансформатор возвращает его.
- Предмет генератора хранит реальный буфер и все баки (с расширениями и вторым баком), подсказка показывает их.

*Карьер, поля и броня*
- Карьер копает блок дороже своего буфера, когда буфер полон; своих соседей не трогает; магнит не тянет предметы из чужого приватного поля.
- Приватное поле нельзя включить поверх чужого привата. Свой конь — питомец, оседланного своими поле не трогает.
- Станция брони обслуживает только игроков с доступом и охлаждает броню в слотах.
- Клинки по площади уважают чужие приваты, сундук бура перепроверяет доступ, газовый чип не вставить туда, где он не работает, «глубокий скан» берёт только руды.

*Разное*
- Фильтры трубок и пара квантового транслятора кэшируются — меньше нагрузка на сервер.
- «мБ» во всех русских строках, вихрь моста скрыт в NEI, координаты моста в WAILA — только при доступе, новые пояснения в справочнике.

**Исправленные баги**
- Полная проверка мода: **7 ошибок**, плюс больше 20 исправлений и доработок по её итогам.
- **Дюпы и вылеты:** трубки предметов, выталкиватель, затягиватель машин, выдача карьера и бура в сундуки удваивали предметы или роняли сервер на инвентарях других модов.
- **Потери:** при поломке Компрессора материи с большим баком терялась часть сингулярной материи; Компрессор не принимал «Расширение бака».
- **Конфиг и справочник:** книга не пересобиралась после смены конфига (показывала ёмкости другого сервера); значение «NaN» в конфиге обходило ограничения.
- **Энергия:** IC2 не узнавал новый уровень накопителя, если трансформатор докладывали в стопку; сети перестраивались от редстоуна рядом с кабелями.
- Полёт брони навсегда менял скорость полёта от других модов; подсказка предмета машины показывала неверный объём бака.

Полный список — в `CHANGELOG.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional (one energy network). Replaces `SiliconAgeAlpha-0.1.7.jar`: remove the old jar from `mods`. 0.1.7 worlds open as they are.

**New**

*Config*
- **`worldgen.oreDimensions`** - the list of dimensions where the mod's ores generate.

*Generators and reactors*
- A running reactor's heat no longer reaches the limit: it overheats only with a full buffer.
- An unlit reactor in ignition mode accepts **any voltage**, with no "will explode" warning.
- Under IC2 without Industrial Upgrade surplus Overdrive is idle, with a hint in the screen; a Transformer brings it back.
- A generator item keeps its real buffer and every tank (with extensions and the second tank), and its tooltip shows them.

*Quarry, fields and armour*
- The quarry digs a block dearer than its buffer once the buffer is full, leaves its own neighbours alone, and its magnet skips other players' private fields.
- A private field can't be switched on over another claim. Your own horse counts as a pet, and a mount ridden by allowed players is left alone.
- The armour station serves only players with access and cools the armour in its slots.
- Blade area attacks respect other claims, the drill's chest re-checks access, a gas chip can't go where it doesn't work, the deep scan takes only ores.

*Misc*
- Tube filters and the quantum translator's partner are cached - less server load.
- "мБ" in every Russian string, the bridge vortex hidden in NEI, bridge coordinates in WAILA only with access, new handbook notes.

**Fixed bugs**
- A full bug check of the mod: **7 bugs**, plus 20+ fixes and refinements that followed it.
- **Dupes and crashes:** item tubes, the machine ejector and puller, quarry and drill chest delivery duplicated items or crashed the server with other mods' inventories.
- **Losses:** breaking a Matter Compressor with a big tank lost part of its Singular Matter; the Compressor didn't take Tank Extensions.
- **Config and handbook:** the handbook wasn't rebuilt after a config change (showed another server's capacities); "NaN" in the config slipped past the limits.
- **Energy:** IC2 didn't see a storage's new tier when a transformer was added to the stack; networks were rebuilt by redstone next to cables.
- Suit flight permanently changed the flight speed given by other mods; a machine item's tooltip showed the wrong tank size.

The full list is in `CHANGELOG.md`.
