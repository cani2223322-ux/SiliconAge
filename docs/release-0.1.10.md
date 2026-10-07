🇷🇺 **Русский**

> ⚠️ **Бета-версия.** Основное содержимое мода готово, дальше — доводка и исправление ошибок. Перед обновлением сделайте резервную копию мира.

**Требования:** Minecraft 1.7.10, Forge 10.13.4.1614 (или новее для 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) — необязателен (общая энергосеть). Заменяет `SiliconAgeBeta-0.1.9.jar`: старый jar из папки `mods` уберите. Миры 0.1.9 открываются без изменений.

**⚠️ Изменённые крафты**
- **Неизолированный медный кабель:** теперь **2 медных слитка рядом → 8 кабелей** (было: 1 слиток → 4). Выход на слиток тот же.
- **Подложка мишени:** 2 стальных слитка рядом → 2 подложки.
- **Базовый фильтр предметов:** снизу добавлен голый медный кабель.

**Новое**

*NEI*
- **Страницы «Как получить»** (клавиша R) для всего, что не крафтится: руды и известняк (высота, жила, биомы и нужная кирка — из конфига, путь переработки), крупица сингулярности, пыль брака, творческий генератор, жидкости других модов (сколько EU даёт генератор). Теперь у каждого предмета мода в NEI есть рецепт или такая страница.

*Сборки с другими модами*
- **Прокатный станок прессует блоки металлов** из 9 слитков любого мода (без формы).
- **Сборки с UniDict** (объединение металлов): реакторы строятся из свинцовых блоков любого мода, постройки проверяют блок металла по словарю руд, справочник в рецептах машин показывает подходящие предметы всех модов, в «Совместимости» — раздел про UniDict.
- Крафты мода проверены против большой сборки (Thermal, Ender IO, IC2, AE2, Forestry, Galacticraft и др.): пересечений нет, самопроверка следит, чтобы крафт мода не состоял из одного общего ингредиента.

*Удобства*
- **Обзор сети:** повторный клик по той же сети обновляет цифры, Shift + ПКМ ключом в воздух закрывает голограмму.
- **Сингулярная станция:** галочка «Инструмент тоже» в заголовке вкладки «Модерн.» — инструмент из слота модернизируется вместе с бронёй только по вашему выбору.
- **Поиск в справочнике:** колесо мыши двигает выделение вместе со списком, клик по результату выбирает его, однобуквенные слова не засоряют поиск.

**Исправленные баги**
- **Крафты пересекались с другими модами:** один медный слиток давал медные самородки других модов вместо кабеля; один стальной слиток — стальные самородки вместо подложки; фильтр предметов совпадал с базовым фильтром Ender IO.
- **Реакторы в сборках с UniDict:** наш свинцовый блок там не крафтился, а оболочки Токамака XV и Сингулярного реактора принимали только его — реакторы нельзя было построить.
- **Обзор сети** показывал сети внутри приватов других модов (FTB Utilities, GriefPrevention) и сообщал, сколько кабелей скрыто; после закрытия повторный клик ничего не показывал.
- **Сингулярная станция** при модернизации брони заодно забирала инструмент из слота и тратила на него крупицы, а без ядра для инструмента отказывала.
- **Справочник:** выбранная строка в результатах поиска сбивалась при прокрутке колесом.

Полный список — в `CHANGELOG.md`.

---

🇬🇧 **English**

> ⚠️ **Beta.** The mod's main content is in; what follows is polishing and bug fixing. Back up your world before updating.

**Requirements:** Minecraft 1.7.10, Forge 10.13.4.1614 (or newer for 1.7.10), Java 8. IndustrialCraft 2 Experimental (2.2.828+) is optional (one energy network). Replaces `SiliconAgeBeta-0.1.9.jar`: remove the old jar from `mods`. 0.1.9 worlds open as they are.

**⚠️ Changed recipes**
- **Bare copper cable:** now **2 copper ingots side by side → 8 cables** (was 1 ingot → 4). Same yield per ingot.
- **Sputter backing:** 2 steel ingots side by side → 2.
- **Basic item filter:** a bare copper cable at the bottom.

**New**

*NEI*
- **"How to get" pages** (R) for everything with no recipe: ores and limestone (height, vein, biomes and the pickaxe - from the config, the processing route), the singularity crumb, scrap dust, the creative generator, other mods' fluids (the generator's EU). Every item of the mod in NEI now has a recipe or such a page.

*Packs with other mods*
- **The Rolling Machine presses blocks of metal** from 9 ingots of any mod (no mold).
- **UniDict packs** (unified metals): reactors are built of any mod's blocks of lead, structures check a block of metal through the ore dictionary, the handbook's machine recipes show every mod's matching items, and "Compatibility" gets a UniDict section.
- The mod's recipes are checked against a large pack (Thermal, Ender IO, IC2, AE2, Forestry, Galacticraft and more): no clashes, and a self-test keeps every recipe of the mod from being one shared ingredient alone.

*Convenience*
- **Network overview:** another click on the same network refreshes the numbers, sneak + right-click with the wrench in the air closes the hologram.
- **Singular Station:** a "Tool too" toggle in the "Upgrade" tab's header - the tool in the slot is modernised with the armour only when you choose so.
- **Handbook search:** the mouse wheel moves the selection with the list, clicking a result selects it, one-letter words no longer flood the results.

**Fixed bugs**
- **Recipes clashed with other mods:** one copper ingot gave other mods' copper nuggets instead of cable; one steel ingot gave steel nuggets instead of a backing; the item filter matched Ender IO's basic filter.
- **Reactors in UniDict packs:** our block of lead couldn't be crafted there, and the Tokamak XV and Singular reactor shells took only it - the reactors couldn't be built.
- **Network overview** showed networks inside other mods' claims (FTB Utilities, GriefPrevention) and told how many cables were hidden; after closing, another click showed nothing.
- **Singular Station** took the tool in its slot along with the armour's modernisation and spent crumbs on it, refusing without a core for the tool.
- **Handbook:** the selected search result went stale when scrolling with the wheel.

The full list is in `CHANGELOG.md`.
