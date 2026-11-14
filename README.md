# Microcosm

A NeoForge mod for Minecraft 26.1.2: every block is made of 16×16×16 microblocks, and those of 16×16×16
smaller ones, down to three levels deep. Look into any block with a microscope, carve and build inside it with
manipulators or by entering it in person, and fabricate items and complete machine blocks from 3D recipes,
automated with whatever mods your pack provides.

## Installation

- Minecraft **26.1.2**, NeoForge **26.1.2.126** or newer, Java 25
- Put `microcosm-<version>.jar` into the `mods` folder (client and server)
- Optional: JEI 29.53+ to browse fabrication recipes

## Features (v0.7.0)

### Scales

| Level | Scale | Name | Unit |
|---|---|---|---|
| 0 | 1 | Block | Bk |
| 1 | 1/16 | Milliblock | mBk |
| 2 | 1/256 | Microblock | µBk |
| 3 | 1/4096 | Nanoblock | nBk |

The units end in "Bk" so they are not mistaken for millibuckets (mB). Recipe files keep using level numbers.

### Material scales

- **Items have a scale too.** One block is 4096 microblocks of the next smaller scale, so a Milliblock gold block
  is worth 1/4096 of a normal one. Items of another scale show a small prefix badge (m, µ, n, B) and a tooltip line.
- **Only matching material fits:** a normal block cannot be placed inside a Milliblock room (it would be 16 times
  too big), and Milliblock material cannot be placed in the normal world. This holds for players, manipulators and
  machines (fake players) alike.
- **Items keep their scale wherever they go:** through ports, in a manipulator's inventory, or carried by a player
  who shrinks or grows with the Size Shifter.
- **Scale Converter** (`microcosm:scale_converter`): in *shrink* mode one item becomes 4096 items of the next smaller
  scale (handed out a stack at a time); in *grow* mode it collects 4096 items of one kind and scale and turns them
  into one of the next larger scale. Items go in from the top or sides and come out at the bottom, so hoppers and
  pipes work; by hand, right-click with an item to insert it, with an empty hand to take the output, and
  sneak-right-click to switch modes.
- Mining a microblock (by hand inside a room or with a manipulator) gives material of that scale.

### Microblocks and rooms

- **Every plain block is divisible:** any full block without a block entity can be opened up (exceptions can be
  added to the block tag `microcosm:indivisible`). Looking at it changes nothing; the first change inside it, or
  entering it, divides it: it becomes a *Divided Block* whose room in `microcosm:level_1` is filled with 4096
  microblocks of its material. Blocks inside a level-1 room have rooms in level 2, and so on down to level 3.
- **Collapsing back:** when a room is uniform again (one material everywhere, or empty) and nothing is inside it,
  the Divided Block turns back into an ordinary block of that material, or into air.
- **Mirrored layout:** divided blocks that touch each other within the same 16³ section have rooms that touch as
  well, with no wall between them, so machines in neighbouring rooms can build into a room.
- **Chained chunk loading:** a room stays loaded as long as its block is loaded.
- **Break protection:** a Divided Block cannot be broken by hand; change it from the inside instead.
- **Miniature:** a Divided Block is a glass-framed case that shows its room inside at 1/16 scale, one room block per pixel,
  with the blocks' own face textures. The server checks rooms for changes about once per second
  (`preview_interval_ticks` in the server config, 0 turns miniatures off) and only sends updates when something
  changed.
- **Looking out:** from inside a room you see the world around its block one level up, 16 times enlarged: each of
  those blocks is a 16×16×16 block around the room, so one texture pixel is one room block. The room is built like its
  block seen from inside: the walls are clear glass and only the room's twelve edges carry a solid frame (connected
  textures follow the room's shape).
  Neighbouring divided blocks in the same section show their real rooms instead, since the mirrored layout
  puts them exactly there. `outside_view_radius` in the server config sets how far (default 4 blocks, 0 = off).
- **Sky and time of day:** rooms share the overworld's clock, sky and day cycle, so you see the sun, moon and stars
  through the glass, daylight falls into rooms, and the enlarged world outside gets dark at night.

### Ports

The **Cell Port** (`microcosm:port`) is placed inside a room against a wall. It connects that side of the room with
the same side of its block one level up. One port per side is active.

- **Capabilities in both directions** (items, fluids, energy): from outside, the block's side exposes the block
  behind the port inside the room; from inside, the port exposes the block next to the block's side. Items keep
  their scale on the way. This works with
  every mod that uses NeoForge capabilities (pipes, hoppers, AE2, Mekanism, …). More capabilities can be added in
  `Microcosm.registerCapabilities`.
- **Redstone in one direction**, toggled by right-clicking: `input` (green) carries the signal from outside into the
  room, `output` (orange) from the room to the outside. Each crossing takes 1 tick.
- Sides with a port do not trigger the cube recipe check; only sides without a port do.

### Access devices

They let you see and work inside blocks without entering them. Viewers sit **on top** of the block they look into,
like a microscope above its specimen; manipulators work on the block their tool points at (on placement they turn
toward a neighbouring Divided Block if there is one). All devices have their own 3D models.

| Device | Tier | Function |
|---|---|---|
| Optical Microscope | 1 | Live colour view of rooms on level 1 |
| Manipulator Arm | 1 | 9 slots; places, breaks and uses items in level 1 rooms |
| Electron Microscope | 2 | Grayscale view refreshed every 3 s, needs energy, can zoom into blocks inside a room (level 2) |
| Nano Probe | 2 | Like the arm but slower, needs energy, reaches level 2 |
| Field Manipulator | 3 | Viewer and manipulator in one, **no live image**: works from scans only (energy, 5 s), reaches level 3 |

- **Controls:** right-click a viewer to open the room view. Left click runs the selected mode (Place, Break, Use),
  right click on a block zooms into it, the mouse wheel changes the layer. Using a tool on a structure triggers local
  fabrication recipes.
- **3D view:** a button switches between the layer view and an isometric 3D view, which can be rotated in 90° steps.
  The mouse wheel then sets the cut height: everything above it is hidden so you can look into the room. Clicks hit
  the face under the mouse; Place puts the block onto that face, Break and Use act on the block itself. Tier 1 shows
  real block icons, tier 2 shaded grayscale cubes, tier 3 cyan cubes from the last scan.
- **Inventory:** right-click the arm or probe; sneak + right-click the field manipulator. Hoppers and pipes can fill
  it; energy comes in through the normal NeoForge energy capability.
- Manipulators place only material of the room's scale; feed them through a Scale Converter or with microblocks
  they mined themselves.
- **Progression:** Nano Lenses are fabricated on level 1 (local recipe), Quantum Lattices on level 2. Tier 2 devices
  need Nano Lenses, the Field Manipulator needs Quantum Lattices.

### Entering rooms

The **Size Shifter** (`microcosm:size_shifter`, late game: needs Quantum Lattices, Nano Lenses and a Nether Star)
lets you enter blocks in person. Right-click a block to shrink and land on a free spot inside it (it is divided
first if needed). Sneak + right-click inside a room to grow back out next to the block. Everything you carry shrinks
and grows with you. This works nested down to level 3.

### Configuration

`serverconfig/microcosm-server.toml` contains all timings, energy costs and buffer sizes of the devices, an option
to forbid players building inside rooms by hand, and the `[entering]` section (enable entering, maximum depth,
cooldown). All recipes can be replaced through data packs or KubeJS.

### Debug commands (operators)

`/microcosm enter [pos]`, `/microcosm exit`, `/microcosm check [pos]`, `/microcosm info`,
`/microcosm act <manipulator> <place|break|use> <slot> <x> <y> <z> ["x y z;..." nested blocks]`.

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
- **`cube`:** the whole room of a Divided Block is the pattern, in 4 rotations. A redstone pulse on the block (or
  `/microcosm check`) checks it. On a match the recipe blocks are consumed, every other block drops next to it,
  and the block turns into the result block (non-block results drop as an item).

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

Smoke test on the dedicated server (36 checks: rooms, recipes, ports, devices, dividing, scales, converter):

```bash
rm -rf run/world && mkdir -p run/world/datapacks && cp -r dev/smoketest-datapack run/world/datapacks/mctest
echo "eula=true" > run/eula.txt
./gradlew runServer      # prints PASS/FAIL lines and stops afterwards
```

## Next steps

1. EMI integration once there is an official EMI release for 26.1 (its official maven currently ends at 1.21.1;
   26.1 only has the unofficial "SEMI" port)
