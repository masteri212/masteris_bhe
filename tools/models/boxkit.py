"""Shared helpers for the mob model scripts: box UV, Blockbench export and a quick front-view preview.

A box is (name, origin, size, uv, pivot, mirror): origin and size in Java model space (y down, 24 = feet), the
texture offset, and the pivot of the part it belongs to (for Blockbench). Groups are (name, pivot, [children]).
"""
import base64
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'textures'))
import pngio  # noqa: E402

CLEAR = (0, 0, 0, 0)


def faces(uv, size):
    """Minecraft box UV: where each face of a w*h*d box sits on the texture, as (u0, v0, u1, v1)."""
    (u, v), (w, h, d) = uv, size
    w, h, d = int(round(w)), int(round(h)), int(round(d))
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


class Canvas:
    def __init__(self, w=64, h=64):
        self.w, self.h = w, h
        self.px = [[CLEAR] * w for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def fill(self, rect, top, bottom=None, grain=0.0):
        """A vertical gradient over (u0, v0, u1, v1), with a little deterministic grain."""
        u0, v0, u1, v1 = rect
        bottom = bottom or top
        for y in range(v0, v1):
            t = (y - v0) / max(1, v1 - v0 - 1)
            for x in range(u0, u1):
                n = ((x * 7 + y * 13) % 5) / 5 * grain
                self.put(x, y, mix(top, bottom, min(1, t + n)))

    def write(self, path):
        pngio.write(path, self.px)


# ---------------------------------------------------------------------------- Blockbench

def bb_cube(box):
    name, (x, y, z), (w, h, d), uv, pivot, mirror = box
    return {
        'name': name, 'type': 'cube', 'box_uv': True, 'rescale': False, 'locked': False, 'autouv': 0, 'color': 0,
        'mirror_uv': bool(mirror),
        'from': [x, 24 - (y + h), z], 'to': [x + w, 24 - y, z + d],
        'origin': [pivot[0], 24 - pivot[1], pivot[2]], 'uv_offset': list(uv),
        'faces': {k: {'uv': list(v), 'texture': 0} for k, v in faces(uv, (w, h, d)).items()},
        'uuid': str(uuid.uuid4()),
    }


def bbmodel(name, groups, texture_png, size=(64, 64)):
    """groups: [(name, pivot, [box or group, ...])], in Java space."""
    elements = []

    def group(g):
        gname, pivot, children = g
        out = []
        for child in children:
            if len(child) == 3:
                out.append(group(child))
            else:
                cube = bb_cube(child)
                elements.append(cube)
                out.append(cube['uuid'])
        return {'name': gname, 'origin': [pivot[0], 24 - pivot[1], pivot[2]], 'rotation': [0, 0, 0], 'color': 0,
                'uuid': str(uuid.uuid4()), 'export': True, 'mirror_uv': False, 'isOpen': True, 'locked': False,
                'visibility': True, 'autouv': 0, 'children': out}

    outliner = [group(g) for g in groups]
    return {
        'meta': {'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True},
        'name': name, 'model_identifier': name, 'modded_entity_version': 'Mojmaps',
        'visible_box': [1, 1, 0], 'resolution': {'width': size[0], 'height': size[1]},
        'elements': elements, 'outliner': outliner,
        'textures': [{
            'name': name + '.png', 'id': '0', 'width': size[0], 'height': size[1], 'uv_width': size[0],
            'uv_height': size[1], 'particle': True, 'render_mode': 'default', 'render_sides': 'auto', 'visible': True,
            'internal': True, 'saved': False, 'uuid': str(uuid.uuid4()),
            'source': 'data:image/png;base64,' + base64.b64encode(texture_png).decode(),
        }],
    }


def write_bbmodel(path, name, groups, texture_path):
    with open(texture_path, 'rb') as f:
        model = bbmodel(name, groups, f.read())
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)


def boxes_of(groups):
    out = []
    for g in groups:
        for child in g[2]:
            out.extend(boxes_of([child]) if len(child) == 3 else [child])
    return out


# ---------------------------------------------------------------------------- preview

def preview(canvas, boxes, path, scale=6, glow=None):
    """Front view (from -z), each box filled with its north face, far boxes first; glow added on top."""
    x0 = min(b[1][0] for b in boxes) - 2
    x1 = max(b[1][0] + b[2][0] for b in boxes) + 2
    y0 = min(b[1][1] for b in boxes) - 2
    y1 = max(b[1][1] + b[2][1] for b in boxes) + 2
    w, h = int((x1 - x0) * scale), int((y1 - y0) * scale)
    img = [[(40, 40, 48, 255)] * w for _ in range(h)]
    for name, (x, y, z), (bw, bh, bd), uv, pivot, mirror in sorted(boxes, key=lambda b: -b[1][2]):
        u0, v0, u1, v1 = faces(uv, (bw, bh, bd))['north']
        for py in range(int((y - y0) * scale), int((y + bh - y0) * scale)):
            for px in range(int((x - x0) * scale), int((x + bw - x0) * scale)):
                tu = u0 + int((px / scale + x0 - x) / bw * (u1 - u0))
                tv = v0 + int((py / scale + y0 - y) / bh * (v1 - v0))
                tu, tv = min(u1 - 1, max(u0, tu)), min(v1 - 1, max(v0, tv))
                c = canvas.px[tv][tu]
                if glow is not None:
                    g = glow.px[tv][tu]
                    c = tuple(min(255, c[k] + g[k]) for k in range(3)) + (max(c[3], g[3] and 255),)
                if c[3] == 0 or not (0 <= py < h and 0 <= px < w):
                    continue
                bg = img[py][px]
                t = c[3] / 255
                img[py][px] = tuple(int(bg[k] * (1 - t) + c[k] * t) for k in range(3)) + (255,)
    pngio.write(path, img)
