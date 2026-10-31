# Vertical Spawn

<div align="center">

**Random respawns anywhere inside a 3D area - not just on the highest block.**

[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-blue.svg)](https://files.minecraftforge.net/)

Vanilla random spawning only ever looks at the surface: players without a spawn point, and everyone hit by `/spreadplayers`, land on the highest block in the column. Vertical Spawn adds the missing dimension. Define an area with a minimum and maximum Y, and players land at a random safe spot anywhere inside it - in caves, under overhangs, inside buildings, on lower decks, or on the surface too.

Useful for PvP arenas, minigames, adventure and survival maps, cave or underground worlds, and any map that has more than one layer.

</div>

## Features

- **True 3D random spawn** - every safe spot between min Y and max Y can be picked, not just the top block
- **Whole border or custom box** - use everything inside the world border, or a fixed X/Z box (always clipped to the border)
- **Multiple zones** - define several named boxes (each with its own Y range and ceiling rule); each zone gets an equal share of spawns
- **Safe spots only** - solid floor under your feet, free space for feet and head, no liquids, slabs, fire, cobwebs, portals, magma or cactus
- **Respects real spawn points** - players with a working bed, respawn anchor or `/spawnpoint` respawn there like normal
- **Optional covered-only mode** - require a block somewhere overhead (caves, interiors, under overhangs)
- **`/spreadplayers` replacement** - same arguments, but uses the whole height range
- **Datapack friendly** - everything is a permission-level 2 command, settings are saved with the world
- **Server-side only** - vanilla clients can join, nothing to install for players
- **Off by default** - does nothing until you run `/verticalspawn enable`

## Requirements

- Minecraft **1.20.1**
- Forge **47.4.10+**

## Installation

1. Build the jar (see below) or grab it from [Releases](../../releases)
2. Drop it in the `mods` folder of your server (or your client for single-player)
3. Start the server, run the commands below

> Server-side mod - only the server needs it. Players can join with plain Forge or vanilla clients.

## Usage

All commands need permission level 2 (ops, command blocks, datapack functions).

| Command | What it does |
|---|---|
| `/verticalspawn enable` | Turn the 3D respawn on |
| `/verticalspawn disable` | Turn it off (vanilla respawn again) |
| `/verticalspawn area border <minY> <maxY>` | Use the whole world border horizontally, between the two Y levels |
| `/verticalspawn area box <x1 z1> <x2 z2> <minY> <maxY>` | Use a fixed X/Z box (corners in any order, `~` works) |
| `/verticalspawn ceiling <true\|false>` | Only use spots with a block somewhere overhead. Default `false` |
| `/verticalspawn teleport <targets>` | Teleport players to a random spot right now, even when disabled |
| `/verticalspawn zone add <name> <x1 z1> <x2 z2> <minY> <maxY> [ceiling]` | Add (or replace) a named zone. While any zone exists, zones are used instead of the single area |
| `/verticalspawn zone ceiling <name> <true\|false>` | Change one zone's ceiling rule |
| `/verticalspawn zone remove <name>` | Remove a zone |
| `/verticalspawn zone list` | Show all zones (same as `status`) |
| `/verticalspawn zone clear` | Remove all zones and go back to the single area |
| `/verticalspawn spread <center> <spreadDistance> <maxRange> [under <maxHeight>] <respectTeams> <targets>` | Drop-in replacement for `/spreadplayers` (see below) |
| `/verticalspawn status` | Show current settings and the effective area after clipping to the border |

`minY`/`maxY` are the Y of the player's **feet**. A floor at Y 63 means players stand at Y 64.

### Example: respawn anywhere from deep caves to the surface

```
/worldborder center 0 0
/worldborder set 300
/verticalspawn area border -50 120
/verticalspawn enable
```

### Example: only in covered spots inside one region

```
/verticalspawn area box -50 -50 50 50 0 100
/verticalspawn ceiling true
/verticalspawn enable
```

### Zones: spawn mostly inside specific structures

A single area with a few large structures in it mostly spawns players on the open ground, simply because there is more ground than structure. Zones fix that: each zone gets an **equal chance**, no matter its size.

Example - two towers and the ground around them:

```
/verticalspawn zone add tower_a -30 -10 -10 10 65 200 true
/verticalspawn zone add tower_b 10 -10 30 10 65 200 true
/verticalspawn zone add ground -50 -50 50 50 64 64
/verticalspawn enable
```

That's 1/3 tower A, 1/3 tower B, 1/3 ground. Leave out the `ground` zone and players only ever respawn in the towers. Want towers to come up more often? Add a zone twice under different names (e.g. `tower_a` and `tower_a2`) to double its share.

- Each zone has its **own Y range**, so structures standing on different levels work fine.
- The optional last argument is the zone's **ceiling rule** (`true` = only covered spots, keeps players off the roof). It defaults to `false`.
- If the picked zone has no free spot, the other zones are tried before falling back to vanilla.
- While zones exist, the single `area` and the global `ceiling` setting are ignored. `zone clear` brings them back.

### Replacing `/spreadplayers`

`/verticalspawn spread` takes exactly the same arguments as vanilla `/spreadplayers`, so you only change the command name:

```
spreadplayers 0 0 10 80 true @a
verticalspawn spread 0 0 10 80 true @a

spreadplayers 0 0 10 80 under 120 false @a
verticalspawn spread 0 0 10 80 under 120 false @a
```

Differences from vanilla:

- Spots are picked **anywhere** inside the Y range from `/verticalspawn area` (`under <maxHeight>` caps it further), not on the top block.
- **If zones exist, spread uses them**: only the parts of each zone inside the `center`/`maxRange` square are used, each zone with its own Y range and ceiling rule, and each team/player gets an equal chance of each zone.
- `spreadDistance` is measured in **3D**, so two players at different heights in the same column count as apart.
- Without zones, the `area` X/Z settings are ignored here - `center` and `maxRange` define the square, like vanilla. The world border and `ceiling` setting still apply.
- `respectTeams true` puts each team on one shared spot, like vanilla.
- Works whether Vertical Spawn is enabled or not, and in the dimension the command runs in.
- If not everyone fits, nobody is moved and the command fails, like vanilla.

### In a datapack

Put the commands in any function, for example `data/mymap/functions/start.mcfunction`:

```mcfunction
verticalspawn area border 0 120
verticalspawn ceiling false
verticalspawn enable
verticalspawn spread 0 0 10 80 true @a
```

## How it works

- When a player respawns **without a working spawn point** in the overworld, the mod picks a random zone (or the single area), then random X/Z columns inside it.
- In each column it collects **every** safe standing spot between min and max Y, then picks one at random from several columns, so every level of the map gets a fair chance.
- A spot is safe when:
  - the block below has a solid top face (full block, top slab, upside-down stairs... - not leaves, magma or cactus)
  - the feet and head blocks have no collision and no liquid (carpet and thin snow on the floor are fine)
  - neither is fire, a portal, a cobweb, powder snow, a berry bush, a wither rose or dripstone
  - with the ceiling rule on: there is a solid block somewhere above the head
- If no safe spot is found, the player keeps the normal vanilla respawn and a warning is written to the server log.

## Notes

- Leaving the End through the exit portal is not touched.
- First join of a brand-new player is not touched - only respawns after death. Use `/verticalspawn teleport` or `spread` from your datapack if you want that too.
- The search loads (and if needed generates) the chunks it samples. That's instant on small maps; on a huge border (millions of blocks) use a `box` area so it doesn't generate far-away chunks on every death.
- Settings live in `<world>/data/verticalspawn.dat`.

## Building from source

```bash
git clone https://github.com/lilDorito/Vertical-Spawn.git
cd Vertical-Spawn
./gradlew build
```

Built jar lands in `build/libs/`.

## License

See [LICENSE](LICENSE).

---

*If this kept your players from all spawning on the same rooftop, drop a ⭐ on the repo pls.*
