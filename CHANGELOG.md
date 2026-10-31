# Changelog

## 1.0.0 (first release)

For Minecraft 1.21.1, NeoForge 21.1.262 or newer. Needs Patchouli.

### The reactor
- The Dark Energy Reactor: a 55 x 55 multiblock of a Reactor Core, a Containment Ring, four Stabilizers, optional
  Axial Stabilizers and a Venting Lance, run from a monitor-wall Control Console with buttons you press in the world.
- Finished pieces assemble into 3D models; break any block and they go back to blocks.
- Feed it mass (any block, Neutronium worth far more), charge it, ignite it.
- Power from spin (scales with the core's mass, needs both Axial Stabilizers) or from the Bending Laser, which
  fires RF round the black hole and gets more back, paid for in mass.
- Reactor Ports in the ring for power in and out, comparator output and computer control.
- The stabilizers run on the reactor's own power: 2,000 RF/t each.

### Failures
- Disc stalls, overspin, mass starvation, ignition and shutdown faults, desyncs, cracked lenses, singularity
  rebounds. Each has a skillful fix, a safe but costly fix and a countdown, and they stack.
- Pre-meltdown, then a two-phase meltdown. The Venting Lance is the way out; the alarm quiets down after 10 seconds
  of phase 2.
- A collapse leaves a crater and a Dark Energy Zone (whether it destroys blocks is a config option).

### Dark Energy Zones
- Low gravity, corrupted ground and mobs, Unstable Dark Ore, Drifters and Lensing Stalkers (which can't survive
  outside a zone).
- Stabilize the nodes to shrink a zone, and beat the Echo of the Core to close it.

### Research and the bomb
- Capture a tiny black hole and record reactor failures in the Research Chamber: the Recordings make the Venting
  Lance and the Axial Stabilizers.
- The Black Hole Bomb.

### The Event Horizon
- A dimension inside a black hole, reached through a Horizon Ring: floating islands, gravity and time that change
  with depth, Neutronium, Mass Shards, Graviton Wells, Hawking Cells.
- Spaghettifiers, Hawking Wisps and the Singularity boss.
- Nine kinds of ruins, from Swallowed Chunks to the Citadel.

### Everything else
- The Reactor Manual (Patchouli), given on first join, with multiblock previews to build over.
- Advancements for the whole progression, and every recipe unlocks in the recipe book.
- Common `c:` tags for Neutronium's ore, raw material, ingot and block.
- Optional support for CC: Tweaked (the `bhe_reactor` peripheral), JEI and Sable.
- Works with Iris shader packs: the glows and the Event Horizon's sky stay visible.
- Server config for almost every number. All of it is explained on the
  [wiki](https://github.com/masteri212/masteris_bhe/wiki).
