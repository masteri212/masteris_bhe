"""The Event Horizon's worldgen data: dimension type, dimension, terrain (noise settings), biomes and features.

Run from the project root:
    python -I tools/worldgen/event_horizon.py

Layout, top to bottom (the world is y 0..383):
    horizon_shallows  y ~260+    sparse rubble, weakest gravity
    debris_drift      y ~150-260 the main floating islands; patches of torn-off overworld
    tidal_depths      y ~40-150  deepslate islands (thickest around y 85-110) and spaghetti spires, Neutronium;
                                 time runs fast
    (below ~40)                  nothing: the singularity's pull (see EventHorizon / HorizonEffects)
"""
import json
import os

NS = 'masteris_bhe'
DATA = 'src/main/resources/data/' + NS + '/'
HEIGHT = 384


def write(path, obj):
    full = DATA + path + '.json'
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def ns(name):
    return NS + ':' + name


# ---------------------------------------------------------------- density functions

def noise(name, xz, y):
    return {'type': 'minecraft:noise', 'noise': ns(name), 'xz_scale': xz, 'y_scale': y}


def add(a, b):
    return {'type': 'minecraft:add', 'argument1': a, 'argument2': b}


def grad(y0, y1, v0, v1):
    return {'type': 'minecraft:y_clamped_gradient', 'from_y': y0, 'to_y': y1, 'from_value': v0, 'to_value': v1}


def maximum(a, b):
    return {'type': 'minecraft:max', 'argument1': a, 'argument2': b}


# Islands: big flattened blobs of noise. Most in the drift (y 175-210), a second layer in the deep (y 85-110), a
# thinner gap between, fading out toward the top and the singularity.
ISLAND_BIAS = add(add(grad(30, 85, -1.2, -0.08), grad(110, 140, 0.0, -0.4)), add(grad(140, 175, 0.0, 0.4), grad(210, 330, 0.0, -1.6)))
ISLANDS = add(add(noise('islands', 1.0, 2.0), ISLAND_BIAS), -0.32)
# Rubble: small, sparse lumps in the shallows.
RUBBLE = add(add(noise('rubble', 1.0, 1.0), -0.6), add(grad(215, 245, -1.0, 0.0), grad(320, 355, 0.0, -1.5)))
# Spires: noise stretched tall and thin, only in the deep, thinning toward the singularity (spaghettified).
SPIRES = add(add(noise('spires', 3.0, 0.15), -0.55), add(grad(25, 55, -1.5, 0.0), grad(115, 175, 0.0, -1.5)))
TERRAIN = {'type': 'minecraft:interpolated', 'argument': maximum(maximum(ISLANDS, RUBBLE), SPIRES)}
# Biomes go by height: depth is high at the bottom, low at the top.
DEPTH = grad(0, HEIGHT, 1.5, -1.5)


def depth_at(y):
    return 1.5 - 3.0 * y / HEIGHT


def block(name):
    return {'type': 'minecraft:block', 'result_state': {'Name': name}}


def cond(test, then):
    return {'type': 'minecraft:condition', 'if_true': test, 'then_run': then}


def seq(*rules):
    return {'type': 'minecraft:sequence', 'sequence': list(rules)}


def biome_is(*names):
    return {'type': 'minecraft:biome', 'biome_is': [ns(n) for n in names]}


def depth_check(offset, surface='floor'):
    return {'type': 'minecraft:stone_depth', 'offset': offset, 'surface_type': surface,
            'add_surface_depth': False, 'secondary_depth_range': 0}


SURFACE = seq(
    cond(biome_is('tidal_depths'), seq(
        cond(depth_check(0), block('minecraft:blackstone')),
        block('minecraft:deepslate'))),
    cond(biome_is('horizon_shallows'), seq(
        cond(depth_check(0), block('minecraft:calcite')),
        cond(depth_check(0, 'ceiling'), block('minecraft:tuff')))),
    # Torn-off overworld: patches of grass and dirt on top of the drift's islands.
    cond(biome_is('debris_drift'), cond(
        {'type': 'minecraft:noise_threshold', 'noise': ns('torn_patches'), 'min_threshold': 0.2, 'max_threshold': 10.0},
        seq(cond(depth_check(0), block('minecraft:grass_block')),
            cond(depth_check(3), block('minecraft:dirt'))))),
)


def noise_settings():
    zero = 0.0
    return {
        'sea_level': 0,
        'disable_mob_generation': True,
        'aquifers_enabled': False,
        'ore_veins_enabled': False,
        'legacy_random_source': False,
        'default_block': {'Name': ns('corrupted_stone')},
        'default_fluid': {'Name': 'minecraft:air'},
        'noise': {'min_y': 0, 'height': HEIGHT, 'size_horizontal': 1, 'size_vertical': 2},
        'noise_router': {
            'barrier': zero, 'fluid_level_floodedness': zero, 'fluid_level_spread': zero, 'lava': zero,
            'temperature': zero, 'vegetation': zero, 'continents': zero, 'erosion': zero, 'ridges': zero,
            'depth': DEPTH,
            'initial_density_without_jaggedness': -1.0,
            'final_density': TERRAIN,
            'vein_toggle': zero, 'vein_ridged': zero, 'vein_gap': zero,
        },
        'spawn_target': [],
        'surface_rule': SURFACE,
    }


# ---------------------------------------------------------------- biomes

FULL = [-1.0, 1.0]


def point(name, lo, hi):
    return {'biome': ns(name), 'parameters': {
        'temperature': FULL, 'humidity': FULL, 'continentalness': FULL, 'erosion': FULL, 'weirdness': FULL,
        'depth': [lo, hi], 'offset': 0.0}}


def spawn(entity, weight, lo, hi):
    return {'type': entity, 'weight': weight, 'minCount': lo, 'maxCount': hi}


def biome(fog, particle_chance, sound, features, monsters=(), ambient=()):
    steps = [[] for _ in range(11)]
    for step, feature in features:
        steps[step].append(feature)
    spawners = {k: [] for k in ('monster', 'creature', 'ambient', 'axolotls', 'underground_water_creature',
                                'water_creature', 'water_ambient', 'misc')}
    spawners['monster'] = list(monsters)
    spawners['ambient'] = list(ambient)
    return {
        'has_precipitation': False,
        'temperature': 0.5,
        'downfall': 0.0,
        'effects': {
            'fog_color': fog, 'sky_color': 0, 'water_color': 0x3A1F6E, 'water_fog_color': 0x120828,
            'ambient_sound': sound,
            'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0},
            'particle': {'options': {'type': 'minecraft:reverse_portal'}, 'probability': particle_chance},
        },
        'spawners': spawners,
        'spawn_costs': {},
        'carvers': {},
        'features': steps,
    }


ORES, DECOR, VEGETATION = 6, 7, 9
WISP = ns('hawking_wisp')
SHALLOWS = biome(0x120A20, 0.002, 'minecraft:ambient.warped_forest.loop',
                 [(DECOR, ns('mass_shards_surface')), (DECOR, ns('mass_shards_scattered'))],
                 ambient=[spawn(WISP, 10, 1, 2), spawn(ns('drifter'), 4, 1, 1)])
DRIFT = biome(0x0E0818, 0.003, 'minecraft:ambient.warped_forest.loop',
              [(DECOR, ns('mass_shards_surface')), (DECOR, ns('mass_shards_scattered')),
               (DECOR, ns('graviton_well')), (DECOR, ns('frozen_mob')),
               (VEGETATION, 'minecraft:patch_grass_plain'), (VEGETATION, 'minecraft:trees_plains')],
              monsters=[spawn(ns('spaghettifier'), 2, 1, 1)],
              ambient=[spawn(WISP, 8, 1, 2), spawn(ns('drifter'), 8, 1, 2)])
DEPTHS = biome(0x14060A, 0.006, 'minecraft:ambient.basalt_deltas.loop',
               [(ORES, ns('neutronium_ore')), (DECOR, ns('mass_shards_scattered')),
                (DECOR, ns('graviton_well')), (DECOR, ns('frozen_mob'))],
               monsters=[spawn(ns('spaghettifier'), 5, 1, 1)],
               ambient=[spawn(WISP, 6, 1, 3)])


# ---------------------------------------------------------------- features

def placed(feature, *placement):
    return {'feature': feature, 'placement': list(placement) + [{'type': 'minecraft:biome'}]}


def count(n):
    return {'type': 'minecraft:count', 'count': n}


def rarity(chance):
    return {'type': 'minecraft:rarity_filter', 'chance': chance}


SQUARE = {'type': 'minecraft:in_square'}


def heights(lo, hi):
    return {'type': 'minecraft:height_range',
            'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}}


def heightmap(kind):
    return {'type': 'minecraft:heightmap', 'heightmap': kind}


CONFIGURED = {
    'neutronium_ore': {'type': 'minecraft:ore', 'config': {'size': 5, 'discard_chance_on_air_exposure': 0.3, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'},
         'state': {'Name': ns('neutronium_ore')}},
        {'target': {'predicate_type': 'minecraft:block_match', 'block': ns('corrupted_stone')},
         'state': {'Name': ns('neutronium_ore')}}]}},
    'mass_shards': {'type': ns('mass_shards'), 'config': {}},
    'frozen_mob': {'type': ns('frozen_mob'), 'config': {}},
    'graviton_well': {'type': 'minecraft:simple_block', 'config': {
        'to_place': {'type': 'minecraft:simple_state_provider', 'state': {'Name': ns('graviton_well')}}}},
}
PLACED = {
    'neutronium_ore': placed(ns('neutronium_ore'), count(20), SQUARE, heights(25, 150)),
    'mass_shards_surface': placed(ns('mass_shards'), count(2), SQUARE, heightmap('WORLD_SURFACE_WG')),
    'mass_shards_scattered': placed(ns('mass_shards'), count(8), SQUARE, heights(40, 330)),
    'graviton_well': placed(ns('graviton_well'), rarity(6), SQUARE, heightmap('WORLD_SURFACE_WG')),
    'frozen_mob': placed(ns('frozen_mob'), rarity(5), SQUARE, heightmap('MOTION_BLOCKING')),
}


# ---------------------------------------------------------------- dimension

DIMENSION_TYPE = {
    'ultrawarm': False, 'natural': False, 'piglin_safe': False, 'respawn_anchor_works': False, 'bed_works': False,
    'has_raids': False, 'has_skylight': False, 'has_ceiling': False, 'coordinate_scale': 1.0,
    'ambient_light': 0.18, 'fixed_time': 18000, 'logical_height': HEIGHT, 'height': HEIGHT, 'min_y': 0,
    'effects': ns('event_horizon'), 'infiniburn': '#minecraft:infiniburn_end',
    'monster_spawn_light_level': {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 7},
    'monster_spawn_block_light_limit': 0,
}
DIMENSION = {
    'type': ns('event_horizon'),
    'generator': {
        'type': 'minecraft:noise',
        'settings': ns('event_horizon'),
        'biome_source': {'type': 'minecraft:multi_noise', 'biomes': [
            point('tidal_depths', round(depth_at(150), 3), 2.0),
            point('debris_drift', round(depth_at(260), 3), round(depth_at(150), 3)),
            point('horizon_shallows', -2.0, round(depth_at(260), 3)),
        ]},
    },
}
NOISES = {
    'islands': {'firstOctave': -7, 'amplitudes': [1.0, 1.0, 0.6, 0.3, 0.15]},
    'spires': {'firstOctave': -6, 'amplitudes': [1.0, 0.5]},
    'torn_patches': {'firstOctave': -5, 'amplitudes': [1.0, 1.0]},
    'rubble': {'firstOctave': -4, 'amplitudes': [1.0, 0.5]},
}


def main():
    write('dimension_type/event_horizon', DIMENSION_TYPE)
    write('dimension/event_horizon', DIMENSION)
    write('worldgen/noise_settings/event_horizon', noise_settings())
    for name, obj in NOISES.items():
        write('worldgen/noise/' + name, obj)
    for name, obj in (('horizon_shallows', SHALLOWS), ('debris_drift', DRIFT), ('tidal_depths', DEPTHS)):
        write('worldgen/biome/' + name, obj)
    for name, obj in CONFIGURED.items():
        write('worldgen/configured_feature/' + name, obj)
    for name, obj in PLACED.items():
        write('worldgen/placed_feature/' + name, obj)
    print('wrote Event Horizon worldgen')


if __name__ == '__main__':
    main()
