# Masteri's Black Hole Energy

A NeoForge mod for Minecraft 1.21.1 about building a reactor around a black hole, running it for enormous
amounts of power, and dealing with what happens when it gets away from you.

Build a 55 x 55 containment ring around a Reactor Core, hold the black hole in place with four stabilizers, feed it
mass and spin it up for RF. Run it carelessly and it stalls, overspins, starves, breaches and melts down, and a
collapse leaves a crater and a dark energy zone behind. Go further and you can open a ring into the
**Event Horizon**: the world inside a black hole.

Everything is explained in-game in the **Reactor Manual** (a Patchouli book every player gets on their first join),
including multiblock previews you can project into the world and build over.

## Features

### The reactor
- A large multiblock: the core, a 35-block Containment Ring, four Stabilizers (5 x 5 x 7 each), two optional Axial
  Stabilizers, a Venting Lance and a 4 x 3 Control Console monitor wall.
- **Mass** is the fuel. Every block is 1 mass, fed by hand, hopper or pipe; a running black hole also tears loose and
  swallows blocks and items around it.
- **Power** two ways: angle the stabilizers to spin the disc (output grows with the square of the spin, about
  200,000 RF/t at 4,000 rpm), or fire the Bending Laser around the core for a gain paid for in mass.
- A resonance band to tune into, precession wobble to trim, and stability, ring stress and spin to keep an eye on.

### Things going wrong
- Disc stalls, overspin, starvation, ignition and shutdown faults, stabilizer desync, mass flash, cracked lenses and
  singularity rebounds. Every failure can be fixed skillfully, fixed safely at a cost, or left to run out.
- Failures stack: a new one doesn't wipe out the one you were already dealing with.
- Pre-meltdown, a two-phase meltdown that takes the reactor apart, and an emergency vent through the Venting Lance.
- A collapse carves a crater and leaves a **dark energy zone**: low gravity, corrupted terrain and mobs, Unstable
  Dark Ore, and its own creatures (Drifters, Lensing Stalkers that only move when you aren't looking), ending in a
  boss fight against the **Echo of the Core**.

### Research and the bomb
- A **Research Chamber** that recreates reactor failures on a captured black hole to make Recordings, the crafting
  components for the late-game parts.
- The **Black Hole Bomb**: feed it mass, light it, and get out.

### The Event Horizon
- A dimension inside a black hole, reached through a **Horizon Ring** that spins up and bursts open.
- Floating islands in three layers over a bottomless singularity, under a sky with a black hole and its accretion disc
  in it. Gravity rises the deeper you go, time runs faster in the deep, and below the spaghettification line nothing
  comes back.
- Neutronium (dense enough to be worth 200 mass an ingot), Mass Shards, Graviton Wells, Frozen Mobs and the
  Hawking Cell, a block that makes RF on its own.
- Spaghettifiers, Hawking Wisps, and **the Singularity**, a boss that weapons pass straight through: you beat it by
  feeding it unstable matter until it collapses.
- Nine kinds of procedurally built ruins: Swallowed Chunks, an Expedition Camp, an Observatory that points the way,
  Frozen Reactors guarded by Echoes, a Recording Archive, a Light Trap where your own echo from five seconds ago
  helps you open the vault, a Hawking Garden that evaporates under your feet, a Debris Ring of orbiting platforms,
  and the Citadel, where three Reactor Keys raise the Singularity.

### Compatibility
- **CC: Tweaked** (optional): the reactor is a `bhe_reactor` peripheral. Read every gauge and press every button
  from a computer.
- **Sable** (optional): the black hole pulls in and eats Sable contraptions, and they float in low gravity too.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.262 or newer |
| [Patchouli](https://modrinth.com/mod/patchouli) | 1.21.1-93 or newer (required) |
| [CC: Tweaked](https://modrinth.com/mod/cc-tweaked) | 1.120 or newer (optional) |
| Sable | 2.0.6 or newer (optional) |

Install it on both the client and the server.

## Configuration

Server settings live in `config/masteris_bhe-server.toml` (also editable from the Mods screen); a world can override
them with its own copy in `<world>/serverconfig/`. Every option has a description in the file. Among them:

- **Reactor:** ignition RF and minimum mass, the dimensions reactors may be ignited in, stability and recovery
  thresholds, spin, resonance and laser numbers, failure and meltdown timers, meltdown pull, link ranges
- **Collapses and zones:** whether collapses destroy blocks, crater and zone size, fallout, gravity, chunk loading,
  ore and discharge chances, corrupted mob bonuses, mob caps and the Echo's cooldown
- **Research and the bomb:** research step time; the bomb's fuse and radius, or turning bombs off
- **Event Horizon:** turning it off, which dimensions Horizon Rings work in, trip cost, ring open time, arrival
  height, gravity, time dilation, the spaghettification line, mass values, the Hawking Cell, Graviton Wells,
  Evaporating Rock, the Light Echo, the Singularity Lure and the Singularity
- **Mobs:** health and damage for every mob and boss, and Hawking Wisp lifetimes

**Modpacks:** ship a tuned `config/masteris_bhe-server.toml` with the pack. Only the server's copy counts in
multiplayer: it's sent to the players when they join.

**World generation** (the Event Horizon's terrain, biomes, ores, mob spawns, ruin frequency and ruin loot) is data,
so a datapack changes it, under `data/masteris_bhe/`:

- `worldgen/structure_set/<ruin>.json`: how often each ruin appears (`spacing` and `separation`, in chunks)
- `worldgen/structure/<ruin>.json`: which heights and biomes it appears in
- `worldgen/placed_feature/`: how much Neutronium, Mass Shards, Graviton Wells and Frozen Mobs generate
- `worldgen/biome/`: mob spawns and features per layer
- `loot_table/chests/`: what the ruins' chests hold
- `recipe/`: every recipe

`tools/worldgen/event_horizon.py` generates all of these; editing it and re-running it is the easiest way to make
bigger changes.

## Building from source

You need JDK 21.

```bash
./gradlew build
```

The jar ends up in `build/libs/`. To try it in a development client or server:

```bash
./gradlew runClient
./gradlew runServer
```

### Project layout

- `src/main/java/com/masteri/bhe/`
  - `block`, `item`, `entity`, `menu`: blocks, items, mobs, screens
  - `reactor`: the reactor's systems, dark energy zones and craters
  - `horizon`: the Event Horizon (travel, depth effects, the ring); `horizon/ruins` builds the ruins
  - `client`: renderers, models, the sky and screen effects
  - `compat`: CC: Tweaked and Sable (only loaded when those mods are present)
- `src/main/resources/`: assets, loot tables, recipes, worldgen and the manual
- `tools/`: Python generators (run from the project root)
  - `tools/textures/`: pixel-art block and item textures
  - `tools/models/`: mob textures and Blockbench models
  - `tools/worldgen/event_horizon.py`: the Event Horizon's terrain, biomes, features, ruin placement and ruin loot

## Credits

Made by Masteri. Inspired by jona23's DEM.

## License

Licensed under the [GNU Lesser General Public License v3.0](COPYING.LESSER) (LGPL-3.0-only), which builds on the
[GNU General Public License v3.0](COPYING). In short: you may use, study, change and share this mod, and use it in
modpacks and as a dependency of your own mods under any license; if you distribute a modified version of the mod
itself, you must publish your changes under the same license.
