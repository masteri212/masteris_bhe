# Masteri's Black Hole Energy

**So you want a black hole as a power plant. Cool. Try not to fall in.**

This is a tech mod for NeoForge 1.21.1. You build a giant ring, trap a black hole in the middle, and keep it from
eating you while it spits out more RF than anything else in your base. It's not a "place it and forget it" generator
though. It stalls, it overspins, it gets hungry, and sometimes it just wants out. You can save it every time, but if
you don't, you get a crater and a very weird new neighborhood.

---

## The reactor

- It's big. **55 by 55 blocks**: the Reactor Core, a Containment Ring, four Stabilizers, and a Control Console.
- **It eats blocks.** Any block is fuel. Pipe it in, or just throw stuff at the black hole (careful, it
  likes to rip loose and grab the blocks around it too).
- **Spin it up and it pays you back.** Tilt the stabilizers and the disc goes faster and faster, around 200,000 RF/t
  at 4,000 rpm. Or shoot the Bending Laser around the black hole and get more power back than you put in. It costs
  you mass though.
- Keep it in the sweet spot, stop it wobbling, and keep an eye on stability and ring stress.

## When it goes wrong (it will)

Disc stalls, overspin, running out of mass, ignition faults, stabilizers falling out of sync, cracked lenses, the
black hole bouncing back... Every problem has a clever fix, a safe-but-pricey fix, and a timer. They can pile up on
each other, so stay calm.

Miss the timer and you hit **pre-meltdown**, then a two-stage **meltdown** where the black hole starts pulling your
reactor apart block by block. At that point your only way out is the **Venting Lance**, a giant beam that dumps all
that energy straight up into the sky.

Lose it completely and it **collapses** ccreating a Crater plus a **Dark Energy Zone** hundreds of blocks wide.

## Dark Energy Zones

- Low gravity, corrupted ground, buffed-up corrupted mobs, and **Unstable Dark Ore**, which is the only place to get
  some of the rare stuff.
- **Drifters** float around like jellyfish. **Lensing Stalkers** only move when nobody's looking. Yeah.
- Calm down the zone's nodes to shrink it, and for the last one you'll have to fight the **Echo of the Core**, the
  ghost of the reactor you blew up.

## Research and the bomb

- Catch a tiny black hole and run experiments on it in the **Research Chamber** to record how reactors fail. Those
  Recordings are what the late-game parts are made from.
- Or stuff it full of mass and make a **Black Hole Bomb**. It does exactly what you think.

## The Event Horizon

Build a **Horizon Ring**, charge it up, and watch the frames light up one by one until it rips open. On the other
side is the world *inside* a black hole:

- **Floating islands** over an endless void, with a huge black hole and its glowing disc filling the sky.
- **Gravity changes the deeper you go.** You float near the top and feel twice as heavy near the bottom. Time speeds
  up down there too. And below the spaghettification line? You're not coming back.
- **Neutronium**, so heavy one ingot counts as 200 blocks of mass. Also Mass Shards, Graviton Wells, mobs frozen
  mid-fall, and the **Hawking Cell**, which just makes power on its own forever.
- **Spaghettifiers** that grab you and drag you down, and **Hawking Wisps** that fade away if you don't catch them
  fast.
- **The Singularity.** You can't hit it. Seriously, weapons go right through it. To win you have to feed it unstable
  stuff until it falls apart.

### Ruins

Nine kinds, all different:

- **Swallowed Chunks**: bits of the overworld that got torn off and are just floating there, sometimes with a farm,
  a dungeon or a mineshaft still attached.
- **Expedition Camp**: where the first explorers set up camp. They didn't make it home.
- **Observatory**: fix the telescope and it points you to the nearest Frozen Reactor.
- **Frozen Reactors**: reactors stuck mid-collapse, each one guarded by an Echo that drops a Reactor Key.
- **Recording Archive**: a tower full of Recordings... and Lensing Stalkers between the shelves.
- **Light Trap**: in here you see yourself from five seconds ago, and your past self can help you open the vault.
- **Hawking Garden**: islands that literally evaporate under you.
- **Debris Ring**: chunks of rubble orbiting in the void. Hop on and ride them.
- **The Citadel**: bring all three Reactor Keys and summon the Singularity.

---

## Works with

- **CC: Tweaked** (optional): run the whole reactor from a computer. Every gauge, every button.
- **Sable** (optional): the black hole eats Sable contraptions too, and they float in low gravity.
- **JEI** (optional): recipes and info pages for everything.
- **Iris shaders**: the glows and the Event Horizon sky still show up with shader packs on.

## Requirements

- NeoForge **21.1.262** or newer
- **Patchouli** (required)
- Install on both client and server.

## Config

Pretty much every number is in the server config with a description: costs, timers, power, whether collapses
break blocks, zone size, and the Event Horizon's gravity, time speed and boss. That makes it great for modpacks:
balance the costs and power against the rest of your pack however you like.

---

## Heads up: this mod is vibe coded

I made this with a lot of help from AI (Claude Code). I came up with the ideas, tested everything in game and
decided what stays, and the AI wrote most of the code. It's been tested a lot, but if something breaks or acts
weird, please open an issue on GitHub.

*Made by Masteri. Inspired by [jona23](https://www.youtube.com/@jona2395)'s [DEM](https://www.youtube.com/watch?v=C904h0VJX0Y), and [Matter Overdrive](https://www.curseforge.com/minecraft/mc-mods/matteroverdrive-17-edition)*
