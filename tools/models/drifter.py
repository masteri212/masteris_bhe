"""The Drifter's textures and Blockbench model, from one set of boxes (matching client/DrifterModel.java).

Run from the project root:
    python -I tools/models/drifter.py [--preview preview.png]
Writes textures/entity/drifter.png and drifter_glow.png (64x64, box UV) and tools/models/drifter.bbmodel.
"""
import base64
import json
import math
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'textures'))
import pngio  # noqa: E402

TEX = 'src/main/resources/assets/masteris_bhe/textures/entity/'
W = H = 64
CLEAR = (0, 0, 0, 0)

# Boxes in Java model space (y down, 24 = feet), as in DrifterModel: name, (x, y, z), (w, h, d), uv offset.
BODY_Y = 14
BELL = [
    ('cap', (-3, BODY_Y - 10, -3), (6, 2, 6), (40, 0)),
    ('top', (-5, BODY_Y - 8, -5), (10, 5, 10), (0, 0)),
    ('rim', (-6, BODY_Y - 3, -6), (12, 3, 12), (0, 15)),
    ('core', (-2, BODY_Y - 6, -2), (4, 4, 4), (0, 30)),
]
TENTACLES, RING = 8, 4.5
UPPER = ((1, 5, 1), (16, 30))
LOWER = ((1, 4, 1), (20, 30))


def faces(uv, size):
    """Minecraft box UV: where each face of a w*h*d box sits on the texture, as (u0, v0, u1, v1)."""
    (u, v), (w, h, d) = uv, size
    return {
        'up': (u + d, v, u + d + w, v + d),
        'down': (u + d + w, v, u + d + 2 * w, v + d),
        'west': (u, v + d, u + d, v + d + h),
        'north': (u + d, v + d, u + d + w, v + d + h),
        'east': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def paint():
    base = [[CLEAR] * W for _ in range(H)]
    glow = [[CLEAR] * W for _ in range(H)]

    def put(img, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            img[y][x] = c

    def side(face, top_c, bottom_c, img=base):
        u0, v0, u1, v1 = face
        for y in range(v0, v1):
            t = (y - v0) / max(1, v1 - v0 - 1)
            for x in range(u0, u1):
                n = ((x * 7 + y * 13) % 5) / 40  # a little grain
                put(img, x, y, mix(top_c, bottom_c, min(1, t + n)))

    # Bell dome: deep violet, see-through, lighter toward the crown.
    top = faces(BELL[1][3], BELL[1][2])
    u0, v0, u1, v1 = top['up']
    for y in range(v0, v1):
        for x in range(u0, u1):
            r = math.hypot(x - (u0 + u1 - 1) / 2, y - (v0 + v1 - 1) / 2) / 5
            put(base, x, y, mix((190, 150, 255, 190), (90, 50, 170, 150), min(1, r)))
    side(top['down'], (40, 20, 70, 120), (40, 20, 70, 120))
    for f in ('north', 'south', 'east', 'west'):
        side(top[f], (150, 110, 240, 170), (80, 45, 160, 150))
    # Cap: the palest part of the dome.
    cap = faces(BELL[0][3], BELL[0][2])
    side(cap['up'], (210, 180, 255, 200), (190, 150, 255, 190))
    side(cap['down'], (40, 20, 70, 120), (40, 20, 70, 120))
    for f in ('north', 'south', 'east', 'west'):
        side(cap[f], (200, 165, 255, 190), (160, 120, 245, 175))
    # Rim: brighter and more solid, glowing along its lower edge.
    rim = faces(BELL[2][3], BELL[2][2])
    side(rim['up'], (110, 70, 200, 170), (110, 70, 200, 170))
    side(rim['down'], (60, 30, 110, 160), (60, 30, 110, 160))
    for f in ('north', 'south', 'east', 'west'):
        side(rim[f], (130, 85, 230, 200), (200, 150, 255, 230))
        a, b, c, d = rim[f]
        for x in range(a, c):
            put(glow, x, d - 1, (200, 140, 255, 255))
            if (x + d) % 3 == 0:
                put(glow, x, d - 2, (150, 90, 230, 255))
    # Core: nearly black, flecked with violet; its middle glows.
    core = faces(BELL[3][3], BELL[3][2])
    for f, (a, b, c, d) in core.items():
        for y in range(b, d):
            for x in range(a, c):
                fleck = (x * 5 + y * 3) % 7 == 0
                put(base, x, y, (120, 60, 200, 255) if fleck else (22, 10, 36, 255))
        put(glow, (a + c) // 2, (b + d) // 2, (240, 190, 255, 255))
        put(glow, (a + c) // 2 - 1, (b + d) // 2 - 1, (180, 110, 255, 255))
    # Tentacles: fading out toward the tips, which glow.
    for size, uv, top_c, bottom_c in ((UPPER[0], UPPER[1], (140, 90, 230, 190), (110, 70, 210, 160)),
                                      (LOWER[0], LOWER[1], (120, 80, 220, 160), (190, 140, 255, 110))):
        for f, face in faces(uv, size).items():
            side(face, top_c, bottom_c)
    for f, (a, b, c, d) in faces(LOWER[1], LOWER[0]).items():
        if f in ('north', 'south', 'east', 'west'):
            put(glow, a, d - 1, (220, 170, 255, 255))
    return base, glow


# ---------------------------------------------------------------------------- Blockbench

def bb_box(name, origin, size, uv, pivot):
    """A Java box to a Blockbench cube (y flipped: Blockbench's y = 24 - Java's)."""
    (x, y, z), (w, h, d) = origin, size
    f = faces(uv, size)
    return {
        'name': name, 'type': 'cube', 'box_uv': True, 'rescale': False, 'locked': False, 'autouv': 0, 'color': 0,
        'from': [x, 24 - (y + h), z], 'to': [x + w, 24 - y, z + d],
        'origin': pivot, 'uv_offset': list(uv),
        'faces': {k: {'uv': list(v), 'texture': 0} for k, v in f.items()},
        'uuid': str(uuid.uuid4()),
    }


def bb_group(name, origin, children):
    return {'name': name, 'origin': origin, 'rotation': [0, 0, 0], 'color': 0, 'uuid': str(uuid.uuid4()),
            'export': True, 'mirror_uv': False, 'isOpen': True, 'locked': False, 'visibility': True,
            'autouv': 0, 'children': children}


def bbmodel(texture_png):
    elements, body_children = [], []
    body_pivot = [0, 24 - BODY_Y, 0]
    bell = [bb_box(n, o, s, uv, body_pivot) for n, o, s, uv in BELL]
    elements += bell
    body_children.append(bb_group('bell', body_pivot, [e['uuid'] for e in bell]))
    for i in range(TENTACLES):
        a = 2 * math.pi * i / TENTACLES
        tx, tz = round(math.cos(a) * RING, 3), round(math.sin(a) * RING, 3)
        pivot = [tx, 24 - BODY_Y, tz]
        upper = bb_box(f'tentacle{i}', (tx - 0.5, BODY_Y, tz - 0.5), UPPER[0], UPPER[1], pivot)
        tip_pivot = [tx, 24 - BODY_Y - 5, tz]
        lower = bb_box(f'tip{i}', (tx - 0.5, BODY_Y + 5, tz - 0.5), LOWER[0], LOWER[1], tip_pivot)
        elements += [upper, lower]
        body_children.append(bb_group(f'tentacle{i}', pivot, [upper['uuid'], bb_group('tip', tip_pivot, [lower['uuid']])]))
    return {
        'meta': {'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True},
        'name': 'drifter', 'model_identifier': 'drifter', 'modded_entity_version': 'Mojmaps',
        'visible_box': [1, 1, 0], 'resolution': {'width': W, 'height': H},
        'elements': elements,
        'outliner': [bb_group('body', body_pivot, body_children)],
        'textures': [{
            'name': 'drifter.png', 'id': '0', 'width': W, 'height': H, 'uv_width': W, 'uv_height': H,
            'particle': True, 'render_mode': 'default', 'render_sides': 'auto', 'visible': True, 'internal': True,
            'saved': False, 'uuid': str(uuid.uuid4()),
            'source': 'data:image/png;base64,' + base64.b64encode(texture_png).decode(),
        }],
    }


# ---------------------------------------------------------------------------- preview

def preview(base, path, scale=8):
    """Front view (Java -z side), each box filled with its front face, tentacles at rest: for a quick look."""
    img = [[(40, 40, 48, 255)] * (24 * scale) for _ in range(26 * scale)]
    boxes = [(o, s, uv) for _, o, s, uv in BELL]
    for i in range(TENTACLES):
        a = 2 * math.pi * i / TENTACLES
        tx, tz = math.cos(a) * RING, math.sin(a) * RING
        boxes.append(((tx - 0.5, BODY_Y, tz - 0.5), UPPER[0], UPPER[1]))
        boxes.append(((tx - 0.5, BODY_Y + 5, tz - 0.5), LOWER[0], LOWER[1]))
    boxes.sort(key=lambda b: -b[0][2])  # far first
    for (x, y, z), (w, h, d), uv in boxes:
        u0, v0, u1, v1 = faces(uv, (w, h, d))['north']
        for py in range(int(y * scale), int((y + h) * scale)):
            for px in range(int((x + 12) * scale), int((x + w + 12) * scale)):
                tu = u0 + int((px / scale - (x + 12)) / w * (u1 - u0))
                tv = v0 + int((py / scale - y) / h * (v1 - v0))
                c = base[min(v1 - 1, tv)][min(u1 - 1, tu)]
                if c[3] == 0 or not (0 <= py < len(img) and 0 <= px < len(img[0])):
                    continue
                bg = img[py][px]
                t = c[3] / 255
                img[py][px] = tuple(int(bg[k] * (1 - t) + c[k] * t) for k in range(3)) + (255,)
    pngio.write(path, img)


def main():
    base, glow = paint()
    pngio.write(TEX + 'drifter.png', base)
    pngio.write(TEX + 'drifter_glow.png', glow)
    with open(TEX + 'drifter.png', 'rb') as f:
        model = bbmodel(f.read())
    with open(os.path.join(HERE, 'drifter.bbmodel'), 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
    if '--preview' in sys.argv:
        preview(base, sys.argv[sys.argv.index('--preview') + 1])
    print('wrote drifter.png, drifter_glow.png, drifter.bbmodel')


if __name__ == '__main__':
    main()
