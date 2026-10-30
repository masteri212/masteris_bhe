package com.masteri.bhe.horizon.ruins;

import com.masteri.bhe.entity.EchoOfTheCoreEntity;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The builders for the Event Horizon's ruins (see Ruin, Build). Each is drawn from its seed: the same seed always
 * gives the same ruin, and no two seeds give quite the same one. Origins: a Frozen Reactor's is the middle of its
 * ring; the others' is the floor or ground they stand on.
 */
final class Ruins {
    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState pick(Build b, Block... blocks) {
        return blocks[b.random.nextInt(blocks.length)].defaultBlockState();
    }

    private static BlockState hangingLantern() {
        return Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    // ------------------------------------------------------------------ Frozen Reactor

    /**
     * A reactor caught mid-collapse: a broken ring round a cracked core, its stabilizers standing or toppled, debris
     * hanging in a spiral round the core, a control room with the reactor's last log, and its Echo still on guard.
     * Three kinds, by how it died: overspun (the ring sheared in two), starved (thin and full of gaps, the core
     * shrunk) or breached (a whole arc blown outward and frozen in flight).
     */
    static void frozenReactor(Build b) {
        int kind = b.random.nextInt(3); // 0 overspun, 1 starved, 2 breached
        BlockState ring = s(ModBlocks.RING_FRAME.get()), casing = s(ModBlocks.STABILIZER_CASING.get());
        b.clear(16.5, -5, 13);
        b.island(0, -6, 0, 13, 9, s(Blocks.DEEPSLATE_TILES), s(Blocks.DEEPSLATE));
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z), n = b.noise(x, z, 3);
                if (d > 12 || (d > 9 && n < 0.25)) continue;
                b.set(x, -5, z, n > 0.8 ? s(Blocks.CRACKED_DEEPSLATE_TILES) : s(Blocks.POLISHED_DEEPSLATE));
            }
        }
        // Stabilizers, one each side; a couple toppled in the breached and starved ones.
        int[][] sides = {{10, 0}, {0, 10}, {-10, 0}, {0, -10}};
        for (int i = 0; i < 4; i++) {
            int dx = sides[i][0], dz = sides[i][1];
            boolean toppled = (kind == 2 && i % 2 == 0) || (kind == 1 && i == 3);
            if (toppled) {
                for (int k = 0; k < 5; k++) b.set(dx + Integer.signum(dx) * k, -4, dz + Integer.signum(dz) * k, casing);
                b.set(dx + Integer.signum(dx) * 5, -4, dz + Integer.signum(dz) * 5, s(ModBlocks.STABILIZER_COIL.get()));
            } else {
                b.fill(dx, -4, dz, dx, -1, dz, casing);
                b.set(dx, 0, dz, s(ModBlocks.STABILIZER_COIL.get()));
                b.set(dx - Integer.signum(dx), 0, dz - Integer.signum(dz), s(ModBlocks.STABILIZER_EMITTER.get()));
                b.set(dx, 1, dz, s(Blocks.END_ROD));
            }
        }
        // The ring.
        switch (kind) {
            case 0 -> {
                b.torus(0, 0, 0, 7, 1.2, ring, a -> Math.cos(a) > 0.12);
                b.torus(0, 3, 2, 7, 1.2, ring, a -> Math.cos(a) < -0.12); // the sheared half, thrown up and over
            }
            case 1 -> b.torus(0, 0, 0, 7, 0.8, ring, a -> Math.sin(a * 5) < 0.55);
            default -> {
                b.torus(0, 0, 0, 7, 1.2, ring, a -> !(a > 0.3 && a < 1.6));
                for (int i = 0; i < 40; i++) { // the blown-out arc, frozen in flight
                    double a = 0.3 + b.random.nextDouble() * 1.3, d = 8 + b.random.nextDouble() * 7.5;
                    b.set((int) Math.round(Math.cos(a) * d), b.random.nextInt(10) - 3, (int) Math.round(Math.sin(a) * d),
                            b.random.nextInt(4) == 0 ? casing : ring);
                }
            }
        }
        // The core: black, its containment cracked open.
        double core = kind == 1 ? 1.5 : 2.2;
        b.sphere(0, 0, 0, core, s(Blocks.BLACK_CONCRETE));
        int shell = Mth.ceil(core + 1.5);
        for (int x = -shell; x <= shell; x++) {
            for (int y = -shell; y <= shell; y++) {
                for (int z = -shell; z <= shell; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > core + 0.5 && d <= core + 1.5 && b.noise(x * 7 + y, z * 7 - y, 2) > 0.45) {
                        b.set(x, y, z, s(Blocks.PURPLE_STAINED_GLASS));
                    }
                }
            }
        }
        // Debris spiralling in toward the core, stopped mid-fall.
        for (int i = 0; i < 44; i++) {
            double a = i * 0.55, r = 3.6 + i * 0.2;
            b.set((int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(i * 0.7) * 3), (int) Math.round(Math.sin(a) * r),
                    pick(b, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.BLACK_CONCRETE, ModBlocks.RING_FRAME.get(), ModBlocks.STABILIZER_CASING.get()));
        }
        // The control room, off to one side, with a walkway in.
        BlockState wall = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.room(5, -5, -14, 11, -1, -9, wall);
        b.fill(7, -5, -9, 9, -5, -6, s(Blocks.POLISHED_DEEPSLATE));
        b.air(8, -4, -9);
        b.air(8, -3, -9);
        for (int z = -13; z <= -11; z += 2) {
            b.set(5, -3, z, s(Blocks.PURPLE_STAINED_GLASS));
            b.set(11, -3, z, s(Blocks.PURPLE_STAINED_GLASS));
        }
        b.chest(6, -4, -13, Direction.SOUTH, "frozen_reactor");
        b.chest(10, -4, -13, Direction.SOUTH, "frozen_reactor");
        b.set(8, -4, -13, s(Blocks.LECTERN));
        b.set(8, -2, -11, hangingLantern());
        // Its Echo, still on guard.
        b.entity(0, -4, 4, ModEntities.ECHO_OF_THE_CORE.get(), echo -> {
            echo.setHome(b.pos(0, -4, 4));
            echo.getPersistentData().putInt(EchoOfTheCoreEntity.REACTOR_KIND, kind + 1);
        });
    }

    // ------------------------------------------------------------------ Swallowed Chunk

    /**
     * A slice of the overworld, torn off and floating: grass on top, dirt, then stone with ores, its edges ragged.
     * On it a little forest, a farm plot with a hut, a dungeon buried inside, or a stretch of mineshaft running
     * through it.
     */
    static void swallowedChunk(Build b) {
        int variant = b.random.nextInt(4); // 0 forest, 1 farm, 2 dungeon, 3 mineshaft
        b.clear(8.5, 1, 9);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                boolean edge = Math.abs(x) == 6 || Math.abs(z) == 6;
                if (edge && b.noise(x, z, 2) < 0.4) continue;
                int depth = 4 + (int) (b.noise(x + 50, z, 3) * 6) + (Math.abs(x) < 4 && Math.abs(z) < 4 ? 3 : 0);
                if (variant == 2 && Math.abs(x) <= 4 && Math.abs(z) <= 4) depth = Math.max(depth, 10);
                for (int y = 0; y <= depth; y++) {
                    BlockState state;
                    if (y == 0) state = s(Blocks.GRASS_BLOCK);
                    else if (y <= 3) state = s(Blocks.DIRT);
                    else state = stoneWithOre(b, y);
                    b.set(x, -y, z, state);
                }
                if (variant != 1 && !edge && b.random.nextFloat() < 0.3F) {
                    b.set(x, 1, z, b.random.nextInt(10) == 0 ? pick(b, Blocks.POPPY, Blocks.DANDELION) : s(Blocks.SHORT_GRASS));
                }
            }
        }
        switch (variant) {
            case 0 -> {
                tree(b, -2, -1, 4 + b.random.nextInt(3));
                if (b.random.nextBoolean()) tree(b, 3, 3, 4 + b.random.nextInt(2));
            }
            case 1 -> {
                for (int x = -5; x <= -1; x++) {
                    for (int z = -2; z <= 2; z++) {
                        if (x == -3 && z == 0) {
                            b.set(x, 0, z, s(Blocks.WATER));
                            b.air(x, 1, z);
                        } else {
                            b.set(x, 0, z, s(Blocks.FARMLAND));
                            b.set(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, b.random.nextInt(8)));
                        }
                    }
                }
                b.room(1, 0, -2, 5, 4, 2, s(Blocks.OAK_PLANKS));
                b.air(3, 1, -2);
                b.air(3, 2, -2);
                b.set(1, 2, 0, s(Blocks.GLASS));
                b.set(5, 2, 0, s(Blocks.GLASS));
                b.chest(4, 1, 1, Direction.WEST, "minecraft:chests/village/village_plains_house");
                b.set(2, 1, 1, s(Blocks.LANTERN));
            }
            case 2 -> {
                b.room(-3, -8, -3, 3, -4, 3, s(Blocks.COBBLESTONE));
                for (int i = 0; i < 25; i++) {
                    int x = b.random.nextInt(7) - 3, y = -8 + b.random.nextInt(5), z = b.random.nextInt(7) - 3;
                    if (Math.abs(x) == 3 || Math.abs(z) == 3 || y == -8 || y == -4) b.set(x, y, z, s(Blocks.MOSSY_COBBLESTONE));
                }
                b.spawner(0, -7, 0, b.random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON);
                b.chest(2, -7, 0, Direction.WEST, "minecraft:chests/simple_dungeon");
                b.chest(-2, -7, 0, Direction.EAST, "minecraft:chests/simple_dungeon");
            }
            default -> {
                b.fill(-6, -4, -1, 6, -2, 1, s(Blocks.AIR));
                for (int x = -6; x <= 6; x++) {
                    b.set(x, -4, 0, s(Blocks.RAIL));
                    if (Math.floorMod(x, 3) == 0) {
                        b.fill(x, -4, -1, x, -3, -1, s(Blocks.OAK_FENCE));
                        b.fill(x, -4, 1, x, -3, 1, s(Blocks.OAK_FENCE));
                        b.fill(x, -2, -1, x, -2, 1, s(Blocks.OAK_PLANKS));
                    } else if (b.random.nextInt(4) == 0) {
                        b.set(x, -2, b.random.nextBoolean() ? -1 : 1, s(Blocks.COBWEB));
                    }
                }
                b.chest(-5, -4, 1, Direction.NORTH, "minecraft:chests/abandoned_mineshaft");
            }
        }
    }

    private static BlockState stoneWithOre(Build b, int depth) {
        boolean deep = depth > 8;
        float r = b.random.nextFloat();
        if (r < 0.035F) return s(deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE);
        if (r < 0.06F) return s(deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE);
        if (r < 0.08F) return s(deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE);
        if (r < 0.086F) return s(deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE);
        if (r < 0.088F) return s(deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE);
        if (r < 0.15F) return s(deep ? Blocks.TUFF : Blocks.ANDESITE);
        return s(deep ? Blocks.DEEPSLATE : Blocks.STONE);
    }

    private static void tree(Build b, int x, int z, int height) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = height - 2; dy <= height + 1; dy++) {
                    int r = dy > height ? 1 : 2;
                    if (Math.abs(dx) <= r && Math.abs(dz) <= r && !(Math.abs(dx) == 2 && Math.abs(dz) == 2 && b.random.nextBoolean())) {
                        b.set(x + dx, dy, z + dz, leaves);
                    }
                }
            }
        }
        b.fill(x, 1, z, x, height, z, s(Blocks.OAK_LOG));
    }

    // ------------------------------------------------------------------ Lost Expedition Camp

    /**
     * Where the first expedition camped and never left: two tents, a cold campfire with log benches, lanterns on
     * posts, their supplies and journal, and the wreck of the Horizon Gate they came through (with frames to
     * salvage).
     */
    static void expeditionCamp(Build b) {
        b.clear(10.5, 1, 7);
        b.island(0, 0, 0, 9, 7, s(Blocks.COARSE_DIRT), s(ModBlocks.CORRUPTED_STONE.get()));
        for (int i = 0; i < 20; i++) {
            int x = b.random.nextInt(15) - 7, z = b.random.nextInt(15) - 7;
            if (x * x + z * z < 49) b.set(x, 0, z, pick(b, Blocks.GRAVEL, ModBlocks.CORRUPTED_STONE.get(), Blocks.PODZOL));
        }
        tent(b, -7, -4, s(Blocks.WHITE_WOOL));
        tent(b, 3, -5, s(Blocks.PURPLE_WOOL));
        b.chest(-5, 1, -3, Direction.SOUTH, "expedition_camp");
        if (b.random.nextBoolean()) b.chest(5, 1, -4, Direction.SOUTH, "expedition_camp");
        // The campfire, long cold, with log benches.
        b.set(0, 1, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        b.set(-2, 1, 2, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        b.set(2, 1, 2, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        b.set(0, 1, 4, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        for (int[] post : new int[][] {{-4, 4}, {4, 1}}) {
            b.fill(post[0], 1, post[1], post[0], 2, post[1], s(Blocks.OAK_FENCE));
            b.set(post[0], 3, post[1], s(Blocks.SOUL_LANTERN));
        }
        // The broken gate: its base, a few frames still standing, the rest strewn about.
        b.set(3, 1, 6, s(ModBlocks.HORIZON_GATE.get()));
        BlockState frame = s(ModBlocks.HORIZON_FRAME.get());
        b.set(2, 1, 6, frame);
        b.set(4, 1, 6, frame);
        b.set(1, 2, 6, frame);
        b.set(-1, 1, 7, frame);
        b.set(6, 1, 4, frame);
        if (b.random.nextBoolean()) b.set(5, 2, 6, frame);
    }

    /** An A-frame tent along z: open at the front ({@code z0}), {@code x0}..x0+4 wide. */
    private static void tent(Build b, int x0, int z0, BlockState cloth) {
        for (int z = z0; z <= z0 + 4; z++) {
            b.set(x0, 1, z, cloth);
            b.set(x0 + 4, 1, z, cloth);
            b.set(x0 + 1, 2, z, cloth);
            b.set(x0 + 3, 2, z, cloth);
            b.set(x0 + 2, 3, z, cloth);
            b.fill(x0 + 1, 1, z, x0 + 3, 1, z, s(Blocks.AIR));
            b.air(x0 + 2, 2, z);
        }
        b.set(x0 + 2, 1, z0 + 4, cloth); // closed at the back
    }

    // ------------------------------------------------------------------ Observatory Wreck

    /**
     * A broken observatory: a copper-ringed deck, the telescope (its lens cracked: see TelescopeBlock) and its long
     * tube pointing at the sky, the ribs of a dome with a few panes left, and the astronomers' things.
     */
    static void observatory(Build b) {
        b.clear(9.5, 0, 12);
        b.island(0, -1, 0, 8, 8, s(Blocks.DEEPSLATE), s(Blocks.DEEPSLATE));
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 6.5) b.set(x, 0, z, d > 5.5 ? s(Blocks.OXIDIZED_CUT_COPPER) : s(Blocks.POLISHED_DEEPSLATE));
            }
        }
        // The dome: eight copper ribs and a few panes of glass left between them.
        for (int x = -6; x <= 6; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d < 5.5 || d > 6.5) continue;
                    double a = Math.atan2(z, x);
                    boolean rib = Math.abs(Math.sin(a * 4)) < 0.2;
                    if (rib) b.set(x, y, z, s(Blocks.WEATHERED_CUT_COPPER));
                    else if (b.noise(x * 3 + y, z * 3, 2) > 0.6) b.set(x, y, z, s(Blocks.GLASS));
                }
            }
        }
        // The telescope and its tube, angled up through a gap in the dome.
        b.set(0, 1, 0, s(ModBlocks.TELESCOPE.get()));
        for (int i = 0; i < 8; i++) {
            int x = (int) Math.round(i * 0.75), y = 2 + (int) Math.round(i * 0.75);
            b.set(x, y, 0, i == 7 ? s(Blocks.TINTED_GLASS) : s(Blocks.COPPER_BLOCK));
            if (y >= 5) b.air(x, y, 1);
        }
        b.chest(-3, 1, 2, Direction.EAST, "observatory");
        b.set(-3, 1, -2, s(Blocks.CARTOGRAPHY_TABLE));
        b.set(3, 1, -2, s(Blocks.LECTERN));
        b.set(-2, 4, 0, hangingLantern());
        b.set(-2, 5, 0, s(Blocks.CHAIN));
    }

    // ------------------------------------------------------------------ Recording Archive

    /**
     * A three-floor tower where someone kept recordings of everything the black hole did: walls of shelves, a
     * ladder up the middle, purple windows, a glass dome, a chest on every floor, and Lensing Stalkers in the
     * stacks.
     */
    static void recordingArchive(Build b) {
        b.clear(9.5, 0, 31);
        b.island(0, -1, 0, 8, 8, s(Blocks.DEEPSLATE), s(Blocks.DEEPSLATE));
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 6.5) continue;
                for (int y = 0; y <= 24; y++) {
                    boolean floor = y % 8 == 0;
                    if (d > 5.5) {
                        double a = Math.atan2(z, x);
                        boolean window = y % 8 >= 3 && y % 8 <= 5 && Math.sin(a * 4) > 0.75;
                        b.set(x, y, z, window ? s(Blocks.PURPLE_STAINED_GLASS) : s(Blocks.DEEPSLATE_BRICKS));
                    } else if (floor) {
                        b.set(x, y, z, s(Blocks.POLISHED_DEEPSLATE));
                    } else if (d > 4.5 && y % 8 <= 3) {
                        b.set(x, y, z, s(Blocks.BOOKSHELF));
                    } else {
                        b.air(x, y, z);
                    }
                }
            }
        }
        // The glass dome.
        for (int x = -6; x <= 6; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= 6.5) b.set(x, 24 + y, z, d > 5.5 ? s(Blocks.TINTED_GLASS) : s(Blocks.AIR));
                }
            }
        }
        // The way in, and the way up: a pillar with a ladder, holes through the floors.
        b.fill(0, 1, 6, 0, 2, 6, s(Blocks.AIR));
        b.fill(0, 1, 5, 0, 2, 5, s(Blocks.AIR));
        b.fill(0, 1, 0, 0, 23, 0, s(Blocks.DEEPSLATE_BRICKS));
        b.fill(1, 1, 0, 1, 23, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        b.chest(-3, 1, -3, Direction.SOUTH, "recording_archive");
        b.chest(3, 9, 3, Direction.NORTH, "recording_archive");
        b.chest(-3, 17, 3, Direction.NORTH, "recording_archive");
        for (int floor = 0; floor < 3; floor++) {
            b.set(2, floor * 8 + 7, -2, hangingLantern());
            b.set(-2, floor * 8 + 7, 2, hangingLantern());
        }
        b.entity(-2, 9, -2, ModEntities.LENSING_STALKER.get(), e -> {});
        b.entity(2, 17, -1, ModEntities.LENSING_STALKER.get(), e -> {});
    }

    // ------------------------------------------------------------------ Citadel

    /**
     * Whoever built the first reactor built this, just above the singularity: a great round platform with spokes
     * and a broken rim, eight pillars (cover from the Hawking burst), a ring hanging overhead, open sky for the fight,
     * and in the middle, on a dais, the Singularity Altar.
     */
    static void citadel(Build b) {
        b.island(0, -1, 0, 19, 12, s(Blocks.POLISHED_BLACKSTONE), s(Blocks.BLACKSTONE));
        for (int x = -19; x <= 19; x++) {
            for (int z = -19; z <= 19; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 19.5) continue;
                double a = Math.atan2(z, x);
                boolean spoke = d > 6 && Math.abs(Math.sin(a * 4)) < 2.0 / d;
                b.set(x, 0, z, d <= 6 ? s(Blocks.POLISHED_DEEPSLATE) : spoke ? s(Blocks.CHISELED_POLISHED_BLACKSTONE) : s(Blocks.POLISHED_BLACKSTONE_BRICKS));
                for (int y = 1; y <= 26; y++) b.air(x, y, z);
                if (d > 18.5 && Math.abs(Math.sin(a * 4)) > 0.25) b.fill(x, 1, z, x, 2, z, s(ModBlocks.RING_FRAME.get()));
            }
        }
        // Pillars: cover.
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 13), pz = (int) Math.round(Math.sin(a) * 13);
            b.fill(px - 1, 1, pz - 1, px + 1, 9, pz + 1, s(Blocks.POLISHED_DEEPSLATE));
            b.fill(px - 1, 10, pz - 1, px + 1, 10, pz + 1, s(Blocks.CHISELED_DEEPSLATE));
            b.set(px, 11, pz, s(Blocks.END_ROD));
        }
        // The ring overhead, a few segments fallen out.
        b.torus(0, 22, 0, 17, 0.8, s(ModBlocks.RING_FRAME.get()), a -> Math.sin(a * 3 + 1) > -0.85);
        // The dais and the altar.
        b.fill(-2, 1, -2, 2, 1, 2, s(Blocks.POLISHED_DEEPSLATE));
        b.set(0, 2, 0, s(ModBlocks.SINGULARITY_ALTAR.get()));
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.set(c[0], 2, c[1], s(Blocks.SOUL_LANTERN));
        b.chest(-4, 1, 0, Direction.EAST, "citadel");
        b.chest(4, 1, 0, Direction.WEST, "citadel");
    }

    // ------------------------------------------------------------------ Light Trap

    /**
     * A glass sphere round a tiny black hole (the core) that holds light in orbit. Inside, everyone gets an echo of
     * themselves from five seconds ago (TrapCoreBlockEntity). The vault under the core is sealed in Light Seals that
     * only fall when both Light Pads, far apart on the floor, are stood on at once: stand on one, then walk to the
     * other before your echo leaves the first.
     */
    static void lightTrap(Build b) {
        b.clear(10.5, 0, 18);
        b.island(0, -1, 0, 9, 7, s(Blocks.DEEPSLATE), s(Blocks.DEEPSLATE));
        for (int x = -10; x <= 10; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = -10; z <= 10; z++) {
                    double d = Math.sqrt(x * x + (y - 5) * (y - 5) + z * z);
                    if (d > 8.5 && d <= 9.5) b.set(x, y, z, b.noise(x * 5 + y, z * 5 - y, 2) > 0.72 ? s(Blocks.SEA_LANTERN) : s(Blocks.TINTED_GLASS));
                }
            }
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 8.6) b.set(x, 0, z, d > 7.5 ? s(Blocks.LIGHT_BLUE_STAINED_GLASS) : s(Blocks.SMOOTH_QUARTZ));
            }
        }
        b.fill(8, 1, -1, 10, 3, 1, s(Blocks.AIR)); // the way in
        // The vault, sealed, with the core hanging over it.
        b.fill(-2, 1, -2, 2, 5, 2, s(ModBlocks.LIGHT_SEAL.get()));
        b.fill(-1, 2, -1, 1, 4, 1, s(Blocks.AIR));
        b.chest(0, 2, 0, Direction.NORTH, "light_trap");
        b.set(0, 8, 0, s(ModBlocks.TRAP_CORE.get()));
        // The two pads, too far apart to stand on both.
        b.set(-6, 0, 0, s(ModBlocks.LIGHT_PAD.get()));
        b.set(6, 0, 0, s(ModBlocks.LIGHT_PAD.get()));
    }

    // ------------------------------------------------------------------ Hawking Garden

    /**
     * A cluster of small islands of Evaporating Rock round a bigger one, Hawking Blooms growing on them and Hawking
     * Wisps drifting between. The rock fades away under your feet and around you, so pick fast and keep moving.
     */
    static void hawkingGarden(Build b) {
        BlockState rock = s(ModBlocks.EVAPORATING_ROCK.get());
        b.clear(16.5, -8, 10);
        b.island(0, 0, 0, 5, 5, rock, rock);
        blooms(b, 0, 0, 0, 5);
        b.chest(0, 1, 0, Direction.NORTH, "hawking_garden");
        int count = 5 + b.random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + b.random.nextDouble() * 0.5;
            double d = 9 + b.random.nextDouble() * 5;
            int cx = (int) Math.round(Math.cos(a) * d), cz = (int) Math.round(Math.sin(a) * d), cy = b.random.nextInt(11) - 5;
            double r = 2.5 + b.random.nextDouble() * 2;
            b.island(cx, cy, cz, r, r + 1, rock, rock);
            blooms(b, cx, cy, cz, r);
        }
        for (int i = 0; i < 4; i++) {
            b.entity(b.random.nextInt(17) - 8, 3 + b.random.nextInt(4), b.random.nextInt(17) - 8, ModEntities.HAWKING_WISP.get(), w -> {});
        }
    }

    private static void blooms(Build b, int cx, int top, int cz, double r) {
        int ri = (int) Math.floor(r * 0.6);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                if (b.random.nextFloat() < 0.3F && !(x == 0 && z == 0)) b.set(cx + x, top + 1, cz + z, s(ModBlocks.HAWKING_BLOOM.get()));
            }
        }
    }

    // ------------------------------------------------------------------ Debris Ring

    /**
     * Two rings of rubble orbiting an empty point, the outer one way, the inner the other, round a small island
     * with a chest. Land on the pier, ride the outer ring, hop onto the inner one as it passes, and step off onto
     * the middle. The platforms are DebrisPlatformEntity: they really move, and carry you.
     */
    static void debrisRing(Build b) {
        b.clear(18, -4, 7);
        b.island(0, 2, 0, 5.5, 5, s(Blocks.DEEPSLATE_TILES), s(Blocks.DEEPSLATE));
        b.chest(0, 3, 0, Direction.NORTH, "debris_ring");
        b.set(2, 3, 2, s(Blocks.SOUL_LANTERN));
        b.set(-2, 3, -2, s(Blocks.SOUL_LANTERN));
        b.island(20, 0, 0, 3, 4, s(ModBlocks.CORRUPTED_STONE.get()), s(ModBlocks.CORRUPTED_STONE.get()));
        b.fill(20, 1, 2, 20, 2, 2, s(Blocks.OAK_FENCE));
        b.set(20, 3, 2, s(Blocks.SOUL_LANTERN));
        orbit(b, 9, 14.5F, 0.0035F, 1, 3, 4);
        orbit(b, 6, 9.5F, -0.0055F, 2, 3, 3);
    }

    /** {@code count} platforms evenly round one orbit, their tops at {@code topY}. */
    private static void orbit(Build b, int count, float radius, float speed, int topY, int minWidth, int maxWidth) {
        net.minecraft.core.BlockPos center = b.pos(0, topY + 1, 0);
        for (int i = 0; i < count; i++) {
            float phase = (float) (i * Math.PI * 2 / count + b.random.nextDouble() * 0.3);
            int width = minWidth + b.random.nextInt(maxWidth - minWidth + 1), style = b.random.nextInt(1000);
            // All spawned at the middle (one chunk); on its first tick each jumps to its place on the orbit.
            b.entity(0, topY + 1, 0, ModEntities.DEBRIS_PLATFORM.get(), p -> p.setOrbit(center, radius, phase, speed, width, style, false));
        }
    }

    private Ruins() {}
}
