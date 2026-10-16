# Подложка (Si-14) — черновик разделов README

Черновик пакета D (`docs/plan-substrate-tech.md` §7.4). Сам `README.md` не правится: при релизе координатор переносит пункт в «Возможности» и раздел в «Требования и установка» (русская и английская части). Числа — значения по умолчанию из конфига (раздел `dimension`).

---

## Русский

### Пункт для «Возможностей»
- **Подложка (Si-14) — своё измерение для поздней игры:** мир-микросхема по ту сторону прокола Космического моста. Прямые Скрайб-каньоны каждые 256 блоков, Кремниевые плато, своя порода (Кристаллит, окисная корка, Медные трассы, друзы) и руды мода — редкие богаче, чем дома. Ни камня, ни деревьев, ни ванильных руд: каждый поход — экспедиция. Враги в настоящей броне мода — Обходчики и Мародёры: у брони есть запас, её «сажают» стрелами, плазмой и ПЭ-1 (Точка, Импульс, Сифон). Трофейные части брони и оружия, фракционная добыча, глава «Подложка» в справочнике.

### Подложка (Si-14): сервер, мультиплеер, совместимость
- **Как попасть.** Только Космическим мостом (в списке измерений — «Подложка (Si-14)») и «Прыжком» Сингулярного корабля аддона Vehicles. Цена прокола туда и обратно — ×0,5 к обычной (`dimension.bridgeCostMultiplier`). Кто попал сюда другим путём (дислокатор Draconic Evolution, чужие телепорты), на следующем тике отправляется назад (`dimension.strictEntry`); творческий режим и операторы проходят. Адский портал там не работает, кровать не ставит точку возрождения (и не взрывается); после гибели игрок возрождается в `dimension.respawnDimension` (Верхний мир).
- **Одинаковые id у сервера и всех клиентов.** Раздел `dimension` в `config/SiliconAge.cfg` — `dimensionId`, `providerId`, `biomeIdPlateaus`, `biomeIdCanyons` — должен совпадать у сервера и у каждого игрока (в игре по LAN — у хоста и гостя). Если id разные, клиент пишет предупреждение в чат и подробности в лог, а биомы показываются неверно. Менять id можно только до первого входа в мир: их помнят папка `DIM-1414`, карты JourneyMap и каждый сохранённый чанк. Правила раздела (вход, цена, трофеи, ПЭ-1 по мобам) клиент получает от сервера сам — их совпадение не нужно.
- **Id по умолчанию:** измерение и провайдер −1414, биомы 240 («Кремниевые плато») и 241 («Скрайб-каньоны»). Если id измерения или провайдера уже занят другим модом, Подложка выключается и в логе одна строка `[Silicon Age] Si-14 Substrate is OFF: …` с причиной; чужие миры не трогаются, блоки и предметы мода остаются. Занятый id биома сдвигается на следующий свободный и записывается в конфиг (с предупреждением в логе). Положительные id не подходят: их раздают станциям Galacticraft и ячейкам AE2.
- **Galacticraft и GalaxySpace.** Подложка — не планета: ракеты сюда не садятся, кислород, гравитация и небо GC её не касаются. Взлететь отсюда на ракете можно (как по умолчанию в GC). Чтобы запретить взлёт, добавьте `-1414` в список «Dimensions where rockets cannot launch» в `config/Galacticraft/core.conf` — чужие конфиги Silicon Age сам не правит.
- **Ender IO и Draconic Evolution.** Мобы Подложки дают трофеи и добычу, только если их поставил сам мир, а убил там игрок: с яиц, спавнеров и мясорубок — только опыт, а банка душ Ender IO на них не срабатывает. Чтобы их нельзя было и размножать, внесите `SiliconAge.lineman` и `SiliconAge.marauder` в чёрные списки банки душ (`soulVesselBlackList` в конфиге Ender IO), Powered Spawner Ender IO и спавнера / Mob Grinder Draconic Evolution.
- **Thaumcraft и AE2.** В Подложке нет узлов ауры и генерации Thaumcraft (IMC `dimensionBlacklist`), метеоритов и кварца Certus AE2. Своя порода — не `minecraft:stone`, поэтому руды других модов там не появляются.
- **Мир и сервер.** Мир выгружается примерно через 20 с после ухода последнего игрока (`dimension.keepLoaded = false`); загрузчики чанков держат свои чанки. Существа Подложки не проходят через мост в другие миры (`dimension.nativesCrossBridge`). Мобов спавнит только сам мир рядом с игроками; на мирной сложности — никого.

| Ключ раздела `dimension` | По умолчанию | Что |
|---|---|---|
| `enabled` | true | Измерение есть |
| `dimensionId` / `providerId` | −1414 / −1414 | Id измерения и провайдера (совпадают у сервера и клиентов) |
| `biomeIdPlateaus` / `biomeIdCanyons` | 240 / 241 | Id биомов (совпадают у сервера и клиентов) |
| `keepLoaded` | false | Держать мир загруженным без игроков |
| `strictEntry` | true | Только мост и «Прыжок»; адский портал не работает |
| `respawnDimension` | 0 | Где возрождается погибший в Подложке |
| `bridgeCostMultiplier` | 0.5 | Цена Космического моста туда и обратно (поверх `bridge.costMultiplier`) |
| `nativesCrossBridge` | false | Местные существа проходят через мост |
| `empMobTargets` / `siphonMobs` | true / true | ПЭ-1 подавляет / выкачивает броню мобов Подложки |
| `gearDropMultiplier` | 1.0 | Множитель шансов трофеев (0 — трофеев нет) |

---

## English

### Item for "Features"
- **The Substrate (Si-14) - a dimension of its own for the late game:** a chip-world beyond the Space Bridge's puncture. Straight Scribe Canyons every 256 blocks, Silicon Plateaus, its own rock (Crystallite, oxide crust, Copper Traces, druses) and the mod's ores - the rare ones richer than at home. No stone, no trees, no vanilla ores: every trip is an expedition. Enemies in the mod's real armour - Linemen and Marauders: the armour has a reserve and goes down to arrows, plasma and PE-1 (Point, Pulse, Siphon). Trophy armour pieces and weapons, faction loot, the handbook chapter "The Substrate".

### The Substrate (Si-14): server, multiplayer, compatibility
- **The way in.** Only by the Space Bridge (in the dimension list - "Substrate (Si-14)") and by the Singular ship's Jump of the Vehicles add-on. A puncture there and back costs x0.5 of the usual (`dimension.bridgeCostMultiplier`). Whoever got there another way (Draconic Evolution's dislocator, other mods' teleports) is sent back the next tick (`dimension.strictEntry`); creative mode and operators pass. The nether portal doesn't work there, a bed sets no spawn (and doesn't explode); after death a player respawns in `dimension.respawnDimension` (the Overworld).
- **The same ids on the server and every client.** The `dimension` section of `config/SiliconAge.cfg` - `dimensionId`, `providerId`, `biomeIdPlateaus`, `biomeIdCanyons` - must match on the server and on every player's game (on LAN - the host's and the guest's). If the ids differ, the client warns in the chat and logs the details, and the biomes show wrong. Change the ids only before anyone has entered the world: the `DIM-1414` folder, JourneyMap's maps and every saved chunk keep them. The section's rules (entry, price, trophies, PE-1 on mobs) come to the client from the server - they needn't match.
- **The default ids:** dimension and provider -1414, biomes 240 (Silicon Plateaus) and 241 (Scribe Canyons). If another mod already holds the dimension or provider id, the Substrate is switched off with one log line `[Silicon Age] Si-14 Substrate is OFF: ...` saying why; no other mod's world is touched, the mod's blocks and items stay. A taken biome id moves to the next free one and is written to the config (with a warning in the log). Positive ids won't do: they are handed out to Galacticraft stations and AE2 cells.
- **Galacticraft and GalaxySpace.** The Substrate is not a planet: rockets don't land there, GC's oxygen, gravity and sky leave it alone. A rocket can take off from it (as GC allows by default). To forbid that, add `-1414` to "Dimensions where rockets cannot launch" in `config/Galacticraft/core.conf` - Silicon Age never edits other mods' configs.
- **Ender IO and Draconic Evolution.** The Substrate's mobs give trophies and loot only when the world itself put them there and a player killed them there: from eggs, spawners and grinders - experience only, and Ender IO's soul vial doesn't work on them. To stop them being bred as well, add `SiliconAge.lineman` and `SiliconAge.marauder` to the blacklists of the soul vial (`soulVesselBlackList` in Ender IO's config), Ender IO's Powered Spawner and Draconic Evolution's spawner / Mob Grinder.
- **Thaumcraft and AE2.** The Substrate has no Thaumcraft aura nodes or world generation (IMC `dimensionBlacklist`), no AE2 meteorites or Certus Quartz. Its rock isn't `minecraft:stone`, so other mods' ores never appear there.
- **The world and the server.** The world unloads about 20 s after the last player leaves (`dimension.keepLoaded = false`); chunk loaders keep their chunks. The Substrate's creatures don't pass a bridge into other worlds (`dimension.nativesCrossBridge`). Only the world itself spawns mobs, near players; on Peaceful - none.

| Key of the `dimension` section | Default | What |
|---|---|---|
| `enabled` | true | The dimension exists |
| `dimensionId` / `providerId` | -1414 / -1414 | Dimension and provider id (the same on the server and clients) |
| `biomeIdPlateaus` / `biomeIdCanyons` | 240 / 241 | Biome ids (the same on the server and clients) |
| `keepLoaded` | false | Keep the world loaded with nobody there |
| `strictEntry` | true | Only the bridge and the Jump; the nether portal doesn't work |
| `respawnDimension` | 0 | Where a player who died in the Substrate respawns |
| `bridgeCostMultiplier` | 0.5 | The Space Bridge's price there and back (on top of `bridge.costMultiplier`) |
| `nativesCrossBridge` | false | The natives pass a bridge |
| `empMobTargets` / `siphonMobs` | true / true | PE-1 suppresses / drains the Substrate mobs' armour |
| `gearDropMultiplier` | 1.0 | Trophy chance multiplier (0 - no trophies) |
