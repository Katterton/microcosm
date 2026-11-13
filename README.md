# Microcosm

A NeoForge mod for Minecraft 26.1.2: blocks that contain a 16×16×16 room one scale level down, nested up to
three levels deep. Inside these rooms you fabricate items and complete machine blocks from 3D recipes, automated
with whatever mods your pack provides.

## Installation

- Minecraft **26.1.2**, NeoForge **26.1.2.126** or newer, Java 25
- Put `microcosm-<version>.jar` into the `mods` folder (client and server)
- Optional: JEI 29.53+ to browse fabrication recipes

## Features (v0.2.0)

### Cells and rooms

- **Microcosm Cell** (`microcosm:cell`): every cell owns a 16³ room in `microcosm:level_1`. Cells placed in
  level 1 have rooms in level 2, and so on down to level 3.
- **Mirrored layout:** cells that touch each other within the same 16³ section have rooms that touch as well.
  There is no wall between them, so machines in neighbouring rooms can build into a room. All other sides are
  closed off with unbreakable `microcosm:cell_wall`.
- **Chained chunk loading:** a room stays loaded as long as its cell is loaded.
- **Break protection:** a cell cannot be broken while its room contains blocks or players.
- **Miniature:** a cell is a glass-framed case that shows its room inside at 1/16 scale, one room block per pixel,
  with the blocks' own face textures. The server checks rooms for changes about once per second
  (`preview_interval_ticks` in the server config, 0 turns miniatures off) and only sends updates when something
  changed.

### Ports

The **Cell Port** (`microcosm:port`) is placed inside a room against a wall. It connects that side of the room with
the same side of the cell one level up. One port per side is active.

- **Capabilities in both directions** (items, fluids, energy): from outside, the cell's side exposes the block
  behind the port inside the room; from inside, the port exposes the block next to the cell's side. This works with
  every mod that uses NeoForge capabilities (pipes, hoppers, AE2, Mekanism, …). More capabilities can be added in
  `Microcosm.registerCapabilities`.
- **Redstone in one direction**, toggled by right-clicking: `input` (green) carries the signal from outside into the
  room, `output` (orange) from the room to the outside. Each crossing takes 1 tick.
- Sides with a port do not trigger the cube recipe check; only sides without a port do.

### Access devices

Placed next to a cell. They let you see and work in rooms without entering them:

| Device | Tier | Function |
|---|---|---|
| Optical Microscope | 1 | Live colour view of rooms on level 1 |
| Manipulator Arm | 1 | 9 slots; places, breaks and uses items in level 1 rooms |
| Electron Microscope | 2 | Grayscale view refreshed every 3 s, needs energy, can zoom into nested cells (level 2) |
| Nano Probe | 2 | Like the arm but slower, needs energy, reaches level 2 |
| Field Manipulator | 3 | Viewer and manipulator in one, **no live image**: works from scans only (energy, 5 s), reaches level 3 |

- **Controls:** right-click a viewer to open the room view. Left click runs the selected mode (Place, Break, Use),
  right click on a cell zooms into it, the mouse wheel changes the layer. Using a tool on a structure triggers local
  fabrication recipes.
- **3D view:** a button switches between the layer view and an isometric 3D view, which can be rotated in 90° steps.
  The mouse wheel then sets the cut height: everything above it is hidden so you can look into the room. Clicks hit
  the face under the mouse; Place puts the block onto that face, Break and Use act on the block itself. Tier 1 shows
  real block icons, tier 2 shaded grayscale cubes, tier 3 cyan cubes from the last scan.
- **Inventory:** right-click the arm or probe; sneak + right-click the field manipulator. Hoppers and pipes can fill
  it; energy comes in through the normal NeoForge energy capability.
- **Progression:** Nano Lenses are fabricated on level 1 (local recipe), Quantum Lattices on level 2. Tier 2 devices
  need Nano Lenses, the Field Manipulator needs Quantum Lattices.

### Entering rooms

The **Size Shifter** (`microcosm:size_shifter`, late game: needs Quantum Lattices, Nano Lenses and a Nether Star)
lets you enter rooms in person. Right-click a cell to shrink and land on a free spot near the middle of its floor.
Sneak + right-click inside a room to grow back out next to the cell. This works nested down to level 3.

### Configuration

`serverconfig/microcosm-server.toml` contains all timings, energy costs and buffer sizes of the devices, an option
to forbid players building inside rooms by hand, and the `[entering]` section (enable entering, maximum depth,
cooldown). All recipes can be replaced through data packs or KubeJS.

### Debug commands (operators)

`/microcosm enter [pos]`, `/microcosm exit`, `/microcosm check [pos]`, `/microcosm info`,
`/microcosm act <manipulator> <place|break|use> <slot> <x> <y> <z> ["x y z;..." nested cells]`.

## Fabrication recipes

Location: `data/<namespace>/microcosm/fabrication/<name>.json` (reloads with `/reload`).

| Field | Meaning |
|---|---|
| `mode` | `local` or `cube` |
| `scale` | Number or list: levels the recipe works on (0 = normal world, 1–3). Missing: all levels. |
| `pattern` | Layers bottom to top → rows (north→south) → characters (west→east). `' '` = air, `'.'` = anything. |
| `key` | Character → block id, block tag (`#c:…`) or list |
| `default` | `cube` only: character for cells not covered otherwise (default `.`) |
| `fill` | `cube` only: `[{"from":[x,y,z],"to":[x,y,z],"key":"I"}]` |
| `structure` | `cube` only: structure `.nbt` (`data/<ns>/structure/…`). Blocks must match exactly, air must be air, structure voids mean anything. |
| `tool`, `tool_damage` | `local` only: tool ingredient and durability cost |
| `result` | `{"id": "...", "count": n}` |

- **`local`:** the structure can be anywhere, in all 4 rotations. Using the tool on one of its blocks (by a player
  or a fake player such as Create's deployer) consumes it and drops the result in place.
- **`cube`:** the whole room of a cell is the pattern, in 4 rotations. A redstone pulse on the cell (or
  `/microcosm check`) checks it. On a match the recipe blocks are consumed, every other block drops next to the cell,
  and the cell turns into the result block (non-block results drop as an item).

Examples: `src/main/resources/data/microcosm/microcosm/fabrication/`.

**JEI:** with JEI installed, a "Microcosm Fabrication" category shows each pattern layer by layer (cycling
automatically), the material counts, the tool and the result. The server sends recipes to clients on login and after
`/reload`, with structure recipes already resolved.

**KubeJS:** recipes are plain data pack files. Put them in `kubejs/data/<namespace>/microcosm/fabrication/<name>.json`;
`/reload` picks them up.

## Development

Requires Java 25.

```bash
./gradlew build          # jar in build/libs
./gradlew runClient      # test client (with JEI)
```

Open a world directly: `./gradlew runClient -PquickPlay=<world name>`.

Smoke test on the dedicated server (27 checks: rooms, recipes, ports, devices):

```bash
rm -rf run/world && mkdir -p run/world/datapacks && cp -r dev/smoketest-datapack run/world/datapacks/mctest
echo "eula=true" > run/eula.txt
./gradlew runServer      # prints PASS/FAIL lines and stops afterwards
```

## Next steps

1. EMI integration once there is an official EMI release for 26.1 (its official maven currently ends at 1.21.1;
   26.1 only has the unofficial "SEMI" port)
