"""Textures for the Event Horizon's blocks and items, in the same pixel-art style as gen.py (and using its helpers).

Run from the project root:
    python -I tools/textures/horizon.py src/main/resources/assets/masteris_bhe/textures --preview preview.png
"""
import colorsys
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngio  # noqa: E402
from gen import (Img, hx, shade, tint, mix, h, plate, stone, lens, outline_item, crystal, corner_rivets,  # noqa: E402
                 VIOLET, LAVENDER, CYAN, TEAL, MINT)

NEUTRON = hx('a8dcff')       # neutronium: pale, cold blue-white
NEUTRON_DARK = hx('3a5a7a')
DEEP = hx('3a3a44')          # deepslate-ish
MAGENTA = hx('e05ad8')       # mass shards
GATE = hx('1a1626')          # gate casing
WHITE = (255, 255, 255, 255)


# ---------------------------------------------------------------- blocks

def neutronium_ore():
    img = stone(DEEP, 31)
    for k in range(7):
        cx, cy = 1 + int(h(k, 3, 31) * 13), 1 + int(h(k, 4, 31) * 13)
        img.set(cx, cy, NEUTRON)
        img.set(cx + 1, cy, shade(NEUTRON, 0.7))
        img.set(cx, cy + 1, shade(NEUTRON, 0.55))
        img.add(cx, cy, NEUTRON, 0.5)
    return img


def neutronium_block():
    img = plate(NEUTRON, 32, 0.05)
    img.inset(2, 2, 13, 13, NEUTRON)
    for y in range(3, 13):          # dense fine lines: packed matter
        for x in range(3, 13):
            if (x + y * 2) % 4 == 0:
                img.set(x, y, shade(NEUTRON, 0.8))
    corner_rivets(img, NEUTRON_DARK, 0)
    return img


def mass_shard_cluster():
    img = Img()
    for k, (bx, top, w) in enumerate(((7, 1, 2), (3, 6, 2), (11, 4, 2), (5, 9, 1), (10, 8, 1))):
        for y in range(top, 16):
            half = max(0, int(round(w * min(1, (y - top) / 4.0))))
            for x in range(bx - half, bx + half + 1):
                c = MAGENTA if x <= bx else shade(MAGENTA, 0.6)
                if x == bx - half:
                    c = tint(MAGENTA, 0.5)
                img.set(x, y, c)
        img.set(bx, top, WHITE)
    return img


def horizon_gate_side():
    img = plate(GATE, 33, 0.05)
    img.inset(2, 2, 13, 13, GATE)
    for x in range(3, 13):              # a violet seam glowing round the middle
        img.set(x, 7, shade(VIOLET, 0.9))
        img.add(x, 8, VIOLET, 0.4)
    corner_rivets(img, GATE)
    return img


def horizon_frame():
    img = plate(GATE, 38, 0.05)
    img.inset(1, 1, 14, 14, GATE)
    for t in range(40):                 # a curved violet conduit, part of the ring's circle
        a = t / 40 * math.pi / 2
        x, y = int(1.5 + math.cos(a) * 12), int(1.5 + math.sin(a) * 12)
        img.set(x, y, shade(VIOLET, 0.9))
        img.add(x, y, LAVENDER, 0.35)
    for x, y in ((4, 4), (11, 11)):
        img.set(x, y, LAVENDER)
        img.add(x, y, VIOLET, 0.5)
    return img


def horizon_gate_top():
    img = plate(GATE, 34, 0.05)
    lens(img, 8, 8, 5.2, GATE, VIOLET, 1.0)
    img.disc(8, 8, 2.6, hx('020104'))   # the black hole's shadow in the lens
    return img


def graviton_well_side():
    img = stone(hx('2a3640'), 35, CYAN, 0.35)
    return img


def graviton_well_top():
    img = stone(hx('2a3640'), 36)
    for t in range(60):                 # an inward spiral
        a = t / 60 * math.tau * 2.2
        r = 7 * (1 - t / 60)
        x, y = int(7.5 + math.cos(a) * r), int(7.5 + math.sin(a) * r)
        img.set(x, y, mix(CYAN, WHITE, t / 60))
    img.glow(8, 8, 4, CYAN, 0.8, 1.5)
    return img


def hawking_cell_frame(frame):
    img = plate(TEAL, 37, 0.04)
    img.inset(2, 2, 13, 13, TEAL)
    img.rect(3, 3, 12, 12, hx('041016'))
    pulse = 0.6 + 0.4 * math.sin(frame / 4 * math.tau)
    img.glow(8, 8, 5.5, hx('bfefff'), 0.9 * pulse, 1.3)
    img.glow(8, 8, 2.2, WHITE, 1.0, 1.0)
    for k in range(4):                  # motes evaporating off it
        a = k / 4 * math.tau + frame * 0.6
        img.add(int(8 + math.cos(a) * 3.5), int(8 + math.sin(a) * 3.5), WHITE, 0.6 * pulse)
    corner_rivets(img, TEAL, 0)
    return img


def hawking_cell():
    return Img().stack([hawking_cell_frame(i) for i in range(4)])


# ---------------------------------------------------------------- items

def raw_neutronium():
    img = Img()
    img.disc(8, 9, 5, NEUTRON_DARK)
    img.disc(7, 8, 3.4, shade(NEUTRON, 0.75))
    for x, y in ((6, 6), (9, 10), (5, 10)):
        img.set(x, y, NEUTRON)
    img.set(6, 6, WHITE)
    return outline_item(img)


def neutronium_ingot():
    img = Img()
    for y in range(6, 12):
        for x in range(2 + (11 - y) // 2, 14 - (y - 6) // 2):
            c = NEUTRON if y < 8 else shade(NEUTRON, 0.8 - 0.05 * (y - 8))
            img.set(x, y, c)
    for x in range(5, 12):
        img.set(x, 6, tint(NEUTRON, 0.6))
    img.set(6, 7, WHITE)
    return outline_item(img)


def mass_shard():
    img = crystal((shade(MAGENTA, 0.5), MAGENTA, tint(MAGENTA, 0.6)), ((7, 5),), 3, tall=True)
    img.glow(8, 8, 6, MAGENTA, 0.2, 1.5)
    return img


def hawking_mote():
    img = Img()
    img.glow(8, 8, 6, hx('bfefff'), 0.8, 1.4)
    img.disc(8, 8, 2.2, WHITE)
    for x, y in ((3, 4), (12, 5), (4, 12), (13, 12)):
        img.set(x, y, hx('bfefff'))
    return img


def singularity_lure():
    img = Img()
    img.disc(8, 8, 6.2, hx('ffb050'), 0.9)
    img.disc(8, 8, 4.8, hx('ff6a20'))
    img.disc(8, 8, 3.4, hx('050208'))
    for a in range(12):                 # spikes: it calls, it doesn't hold
        ang = a / 12 * math.tau
        img.set(int(7.5 + math.cos(ang) * 7.2), int(7.5 + math.sin(ang) * 7.2), hx('ffd890'))
    img.set(6, 6, (255, 255, 255, 160))
    return outline_item(img)


def singularity_heart():
    img = Img()
    img.glow(8, 8, 7.5, hx('ffd890'), 0.7, 1.2)
    img.disc(8, 8, 4.2, hx('050208'))
    for t in range(50):
        a = t / 50 * math.tau
        img.set(int(7.5 + math.cos(a) * 4.6), int(7.5 + math.sin(a) * 4.6), hx('fff4dc'))
    img.set(6, 6, (200, 180, 255, 255))
    return outline_item(img)


def recolor(source, hue, sat=1.0, light=0.0):
    """An existing recording, its colors swung to another hue (the dark slits keep theirs)."""
    src = pngio.read(source)
    img = Img(len(src[0]), len(src))
    for y, row in enumerate(src):
        for x, c in enumerate(row):
            if c[3] == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(c[0] / 255, c[1] / 255, c[2] / 255)
            if ss > 0.25:
                r, g, b = colorsys.hls_to_rgb(hue / 360, min(1, ll + light), min(1, ss * sat))
                c = (int(r * 255), int(g * 255), int(b * 255), c[3])
            img.set(x, y, c)
    return img


BLOCKS = {
    'neutronium_ore': neutronium_ore, 'neutronium_block': neutronium_block, 'mass_shard_cluster': mass_shard_cluster,
    'horizon_gate_side': horizon_gate_side, 'horizon_gate_top': horizon_gate_top, 'horizon_frame': horizon_frame,
    'graviton_well_side': graviton_well_side, 'graviton_well_top': graviton_well_top, 'hawking_cell': hawking_cell,
}
ITEMS = {
    'raw_neutronium': raw_neutronium, 'neutronium_ingot': neutronium_ingot, 'mass_shard': mass_shard,
    'hawking_mote': hawking_mote, 'singularity_lure': singularity_lure, 'singularity_heart': singularity_heart,
}
RECORDINGS = {'recording_spaghettification': (25, 1.0, 0.0), 'recording_hawking_radiation': (190, 0.8, 0.12),
              'escape_recording': (135, 0.9, 0.0)}


# ---------------------------------------------------------------- ruins: telescope, altar, keys

COPPER = hx('3f8f7a')        # weathered copper
ALTAR = hx('1c1a22')
KEY_COLORS = {'overspun': hx('ff8a3a'), 'starved': hx('8ad8ff'), 'breached': hx('ff4a6a')}


def telescope_side():
    img = plate(COPPER, 41, 0.06)
    for y in (4, 11):
        for x in range(16):
            img.set(x, y, shade(COPPER, 0.6))
    corner_rivets(img, COPPER, 0)
    return img


def telescope_top(cracked):
    img = plate(COPPER, 42, 0.06)
    lens(img, 8, 8, 5.0, COPPER, CYAN, 0.5 if cracked else 1.0)
    if cracked:
        for i in range(9):              # a crack across the lens
            img.set(4 + i, 5 + (i * 2) // 3 + (i % 2), (30, 30, 36, 255))
    return img


def altar_side():
    img = plate(ALTAR, 43, 0.04)
    img.inset(2, 2, 13, 13, ALTAR)
    for x in range(4, 12):              # gold inlay
        img.set(x, 8, hx('e2b84a'))
    img.set(7, 6, hx('e2b84a')); img.set(8, 10, hx('e2b84a'))
    return img


def altar_top(keys):
    img = plate(ALTAR, 44, 0.04)
    img.disc(8, 8, 3.2, hx('050208'))
    img.glow(8, 8, 4.5, hx('ffb050'), 0.25 + 0.25 * keys, 1.4)
    for i, (x, y) in enumerate(((8, 2), (3, 12), (13, 12))):   # three sockets
        lit = i < keys
        img.disc(x + 0.5, y + 0.5, 1.6, hx('ffd890') if lit else hx('3a3440'))
        if lit:
            img.glow(x + 0.5, y + 0.5, 3, hx('ffb050'), 0.6, 1.4)
    return img


def reactor_key(color):
    def make():
        img = Img()
        img.disc(5.5, 5.5, 3.6, hx('2a2f3a'))
        img.disc(5.5, 5.5, 2.0, color)
        img.glow(5.5, 5.5, 3.5, color, 0.6, 1.4)
        for i in range(8):              # the shaft, down-right
            img.set(7 + i, 7 + i, hx('6b7487'))
            img.set(8 + i, 7 + i, shade(hx('6b7487'), 0.6))
        for x, y in ((12, 14), (13, 13), (14, 11)):  # teeth
            img.set(x, y, hx('6b7487'))
        img.set(4, 4, (255, 255, 255, 255))
        return outline_item(img)
    return make


BLOCKS.update({
    'telescope_side': telescope_side, 'telescope_top': lambda: telescope_top(False),
    'telescope_top_cracked': lambda: telescope_top(True), 'singularity_altar_side': altar_side,
    'singularity_altar_top_0': lambda: altar_top(0), 'singularity_altar_top_1': lambda: altar_top(1),
    'singularity_altar_top_2': lambda: altar_top(2), 'singularity_altar_top_3': lambda: altar_top(3),
})
ITEMS.update({'reactor_key_' + k: reactor_key(c) for k, c in KEY_COLORS.items()})


# ---------------------------------------------------------------- second ruins: light trap, hawking garden

PALE = hx('bfe6ff')


def light_seal():
    img = Img()
    for y in range(16):
        for x in range(16):
            n = 0.6 * h(x // 2, y // 2, 51) + 0.4 * h(x, y, 52)
            img.set(x, y, mix(hx('5aa8e0'), PALE, n))
    for i in range(16):                 # bright diagonal lattice
        img.set(i, i, (255, 255, 255, 255))
        img.set(15 - i, i, tint(PALE, 0.5))
    img.frame(0, 0, 15, 15, hx('3a78b8'))
    return img


def light_pad(lit):
    img = plate(hx('2a3a4a'), 53, 0.05)
    img.disc(8, 8, 5.2, hx('3a78b8'))
    img.disc(8, 8, 3.6, PALE if lit else hx('5aa8e0'))
    if lit:
        img.glow(8, 8, 7, PALE, 0.8, 1.3)
    for a in range(4):
        ang = a / 4 * math.tau + math.pi / 4
        img.set(int(7.5 + math.cos(ang) * 6.3), int(7.5 + math.sin(ang) * 6.3), PALE)
    return img


def trap_core():
    img = Img(fill=hx('08060c'))
    img.glow(8, 8, 7, PALE, 0.7, 1.2)
    img.disc(8, 8, 3, hx('020104'))
    return img


def evaporating_rock(fading):
    img = stone(hx('7fa6b8'), 54, PALE, 0.9 if fading else 0.3)
    if fading:
        for y in range(16):
            for x in range(16):
                if h(x, y, 55) > 0.82:
                    img.set(x, y, (255, 255, 255, 255))
    return img


def hawking_bloom():
    img = Img()
    for y in range(9, 16):              # stem
        img.set(8, y, hx('4a8a9a'))
    img.set(7, 12, hx('4a8a9a')); img.set(6, 11, hx('6ab0c0')); img.set(9, 13, hx('6ab0c0'))
    for a in range(6):                  # petals of light
        ang = a / 6 * math.tau
        img.disc(8 + math.cos(ang) * 2.6, 6 + math.sin(ang) * 2.6, 1.6, PALE)
    img.disc(8, 6, 1.6, (255, 255, 255, 255))
    img.glow(8, 6, 5, PALE, 0.5, 1.4)
    return img


BLOCKS.update({
    'light_seal': light_seal, 'light_pad': lambda: light_pad(False), 'light_pad_lit': lambda: light_pad(True),
    'trap_core': trap_core, 'evaporating_rock': lambda: evaporating_rock(False),
    'evaporating_rock_fading': lambda: evaporating_rock(True), 'hawking_bloom': hawking_bloom,
})


def main():
    out = sys.argv[1]
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    images = []
    for folder, table in (('block', BLOCKS), ('item', ITEMS)):
        for name, fn in table.items():
            img = fn()
            pngio.write(os.path.join(out, folder, name + '.png'), img.p)
            images.append((name, img.p))
    for name, (hue, sat, light) in RECORDINGS.items():
        img = recolor(os.path.join(out, 'item', 'recording_breach.png'), hue, sat, light)
        pngio.write(os.path.join(out, 'item', name + '.png'), img.p)
        images.append((name, img.p))
    with open(os.path.join(out, 'block', 'hawking_cell.png.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": true\n  }\n}\n')
    if preview:
        pngio.write(preview, pngio.sheet(images, scale=6, cols=9))
    print(len(images), 'textures written')


if __name__ == '__main__':
    main()
