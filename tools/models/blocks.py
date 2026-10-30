"""Block models for the reactor's parts: frames, recessed panels, louvres, lens housings, a ribbed coil and an open
core cage, all inside the normal block bounds (collision and culling stay those of a full block).

Each model is painted into a 16x16x16 voxel grid, one material per voxel, then merged into as few boxes as possible.
UVs are left to Minecraft (taken from each face's position), so every face shows the matching part of its texture
and the painted bevels and insets of the textures line up with the real ones.

Run from the project root:
    python -I tools/models/blocks.py src/main/resources/assets/masteris_bhe/models/block
"""
import json
import os
import sys

N = 16
DIRS = {  # name: (axis, sign)
    'west': (0, -1), 'east': (0, 1), 'down': (1, -1), 'up': (1, 1), 'north': (2, -1), 'south': (2, 1),
}
NS = 'masteris_bhe:block/'


class Mat:
    """A material: which texture variable each face direction uses, and whether it glows."""

    def __init__(self, name, side, up=None, down=None, north=None, glow=False):
        self.name, self.glow = name, glow
        self.tex = {d: side for d in DIRS}
        if up: self.tex['up'] = up
        if down: self.tex['down'] = down
        if north: self.tex['north'] = north


class Grid:
    def __init__(self):
        self.v = [[[None] * N for _ in range(N)] for _ in range(N)]  # v[x][y][z]

    def fill(self, x0, y0, z0, x1, y1, z1, mat):
        for x in range(max(0, x0), min(N, x1)):
            for y in range(max(0, y0), min(N, y1)):
                for z in range(max(0, z0), min(N, z1)):
                    self.v[x][y][z] = mat

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)

    def face(self, side, a0, b0, a1, b1, d0, d1, mat=None):
        """Fill (or clear, with mat None) a slab on one side: a/b are the face's in-plane coordinates (x/y for
        north/south, z/y for east/west, x/z for up/down), d0..d1 the depth from that side's surface."""
        axis, sign = DIRS[side]
        lo, hi = (d0, d1) if sign < 0 else (N - d1, N - d0)
        if axis == 0:
            self.fill(lo, b0, a0, hi, b1, a1, mat)
        elif axis == 1:
            self.fill(a0, lo, b0, a1, hi, b1, mat)
        else:
            self.fill(a0, b0, lo, a1, b1, hi, mat)

    def at(self, x, y, z):
        if 0 <= x < N and 0 <= y < N and 0 <= z < N:
            return self.v[x][y][z]
        return None


SIDES4 = ('north', 'south', 'east', 'west')


def framed(g, mat, sides, w, d):
    """Recess the middle of each listed side by d, leaving a frame w wide."""
    for s in sides:
        g.face(s, w, w, N - w, N - w, 0, d)


def louvres(g, sides, rows=(5, 7, 9), a0=5, a1=11, depth=2):
    """Horizontal vent slots cut into a recessed side panel (matching the casing texture's dark slots)."""
    for s in sides:
        for r in rows:
            g.face(s, a0, r, a1, r + 1, 0, depth)


def lens_housing(g, side, mat_wall, mat_lens, open_lo=2, open_hi=14, depth=3, ring=(4, 12)):
    """A round lens set into a deep housing: the housing opening, then a stepped dome of lens material."""
    g.face(side, open_lo, open_lo, open_hi, open_hi, 0, depth)
    lo, hi = ring
    g.face(side, lo + 1, lo + 1, hi - 1, hi - 1, 1, depth, mat_lens)
    g.face(side, lo, lo + 2, hi, hi - 2, 2, depth, mat_lens)
    g.face(side, lo + 2, lo, hi - 2, hi, 2, depth, mat_lens)


# ---------------------------------------------------------------- meshing

def boxes(g):
    """Greedy-merge same-material voxels into boxes."""
    done = [[[False] * N for _ in range(N)] for _ in range(N)]
    out = []
    for y in range(N):
        for z in range(N):
            for x in range(N):
                m = g.v[x][y][z]
                if m is None or done[x][y][z]:
                    continue

                def ok(xx, yy, zz):
                    return g.v[xx][yy][zz] is m and not done[xx][yy][zz]

                x1 = x
                while x1 + 1 < N and ok(x1 + 1, y, z):
                    x1 += 1
                z1 = z
                while z1 + 1 < N and all(ok(i, y, z1 + 1) for i in range(x, x1 + 1)):
                    z1 += 1
                y1 = y
                while y1 + 1 < N and all(ok(i, y1 + 1, k) for i in range(x, x1 + 1) for k in range(z, z1 + 1)):
                    y1 += 1
                for i in range(x, x1 + 1):
                    for j in range(y, y1 + 1):
                        for k in range(z, z1 + 1):
                            done[i][j][k] = True
                out.append((m, (x, y, z), (x1 + 1, y1 + 1, z1 + 1)))
    return out


def face_cells(lo, hi, axis, sign):
    """Voxels on a box's face, each with the neighbour position just outside it."""
    plane = hi[axis] - 1 if sign > 0 else lo[axis]
    others = [a for a in range(3) if a != axis]
    for p in range(lo[others[0]], hi[others[0]]):
        for q in range(lo[others[1]], hi[others[1]]):
            c = [0, 0, 0]
            c[axis], c[others[0]], c[others[1]] = plane, p, q
            n = list(c)
            n[axis] += sign
            yield c, n


def model(g, textures, render_type=None, occludes=True):
    elements = []
    for mat, lo, hi in boxes(g):
        faces = {}
        for d, (axis, sign) in DIRS.items():
            exposed, straight = False, True
            for c, n in face_cells(lo, hi, axis, sign):
                if g.at(*n) is not None:
                    continue
                exposed = True
                # Seen only through this side's opening if the way out along d is clear.
                p = list(n)
                while 0 <= p[axis] < N:
                    if g.at(*p) is not None:
                        straight = False
                        break
                    p[axis] += sign
            if not exposed:
                continue
            on_edge = (hi[axis] == N) if sign > 0 else (lo[axis] == 0)
            f = {'texture': '#' + mat.tex[d]}
            if on_edge or (occludes and straight):
                f['cullface'] = d
            faces[d] = f
        if not faces:
            continue
        el = {'from': list(lo), 'to': list(hi), 'faces': faces}
        if mat.glow:
            el['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        elements.append(el)
    out = {'parent': 'minecraft:block/block'}
    if render_type:
        out['render_type'] = render_type
    out['textures'] = {k: (v if ':' in v else NS + v) for k, v in textures.items()}
    out['elements'] = elements
    return out


# ---------------------------------------------------------------- the parts

def ring_frame():
    steel = Mat('steel', 'all')
    g = Grid()
    g.fill(0, 0, 0, N, N, N, steel)
    # Flanges top and bottom, posts at the corners, a recessed web with the seam cut deeper.
    for s in SIDES4:
        g.face(s, 2, 3, 14, 13, 0, 1)
        g.face(s, 2, 7, 14, 9, 0, 2)
    return {'ring_frame': model(g, {'particle': 'ring_frame', 'all': 'ring_frame'})}


def ring_beam_port():
    steel = Mat('steel', 'all')
    lens = Mat('lens', 'all', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, steel)
    framed(g, steel, DIRS, 2, 2)
    for s in DIRS:
        g.face(s, 4, 4, 12, 12, 1, 2, lens)
    return {'ring_beam_port': model(g, {'particle': 'ring_beam_port', 'all': 'ring_beam_port'})}


def reactor_port():
    steel = Mat('steel', 'all')
    g = Grid()
    g.fill(0, 0, 0, N, N, N, steel)
    framed(g, steel, DIRS, 2, 1)
    for s in DIRS:
        g.face(s, 3, 3, 13, 13, 0, 3)          # the socket
        for a in (5, 7, 9):                    # connector pins
            for b in (5, 7, 9):
                g.face(s, a, b, a + 1, b + 1, 1, 3, steel)
    return {'reactor_port': model(g, {'particle': 'reactor_port', 'all': 'reactor_port'})}


def casing(g, mat, sides):
    framed(g, mat, sides, 3, 1)
    louvres(g, sides)


def stabilizer_casing():
    m = Mat('casing', 'all')
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    casing(g, m, DIRS)
    return {'stabilizer_casing': model(g, {'particle': 'stabilizer_casing', 'all': 'stabilizer_casing'})}


def stabilizer_emitter():
    m = Mat('casing', 'side', north='front')
    lens = Mat('lens', 'front', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    casing(g, m, ('south', 'east', 'west', 'up', 'down'))
    lens_housing(g, 'north', m, lens)
    tex = {'particle': 'stabilizer_casing', 'side': 'stabilizer_casing', 'front': 'stabilizer_emitter_front'}
    return {'stabilizer_emitter': model(g, tex)}


def axial_emitter():
    m = Mat('casing', 'side', down='front')
    lens = Mat('lens', 'front', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    casing(g, m, ('north', 'south', 'east', 'west', 'up'))
    lens_housing(g, 'down', m, lens)
    tex = {'particle': 'stabilizer_casing', 'side': 'stabilizer_casing', 'front': 'stabilizer_emitter_front'}
    return {'axial_emitter': model(g, tex)}


def stabilizer_coil():
    frame = Mat('frame', 'casing')
    coil = Mat('coil', 'coil', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, frame)
    # Corner posts and end plates hold the windings, which sit a pixel back and are ribbed between turns.
    g.clear(2, 2, 0, 14, 14, N)
    g.clear(0, 2, 2, N, 14, 14)
    for y in range(2, 14):
        r = 2 if y % 4 == 3 else 1
        g.fill(r, y, r, N - r, y + 1, N - r, coil)
    tex = {'particle': 'stabilizer_coil', 'casing': 'stabilizer_casing', 'coil': 'stabilizer_coil'}
    return {'stabilizer_coil': model(g, tex)}


def mass_input_port():
    m = Mat('casing', 'side')
    grate = Mat('grate', 'side', up='top', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    casing(g, m, ('north', 'south', 'east', 'west', 'down'))
    g.face('up', 2, 2, 14, 14, 0, 2)           # a stepped hopper down to the grate
    g.face('up', 3, 3, 13, 13, 0, 4)
    g.face('up', 3, 3, 13, 13, 4, 5, grate)
    tex = {'particle': 'stabilizer_casing', 'side': 'stabilizer_casing', 'top': 'mass_input_top'}
    return {'mass_input_port': model(g, tex)}


def panel_block(front_depth):
    m = Mat('casing', 'side', north='front')
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    casing(g, m, ('south', 'east', 'west', 'up', 'down'))
    g.face('north', 2, 2, 14, 14, 0, front_depth)
    return g


def stabilizer_console():
    g = panel_block(1)
    tex = {'particle': 'stabilizer_casing', 'side': 'stabilizer_casing', 'front': 'stabilizer_console_front'}
    return {'stabilizer_console': model(g, tex)}


def breaker_panel():
    out = {}
    for name, front in (('breaker_panel', 'breaker_panel_front'), ('breaker_panel_on', 'breaker_panel_front_on')):
        g = panel_block(2)
        tex = {'particle': 'stabilizer_casing', 'side': 'stabilizer_casing', 'front': front}
        out[name] = model(g, tex)
    return out


def bending_laser():
    m = Mat('casing', 'side', north='front')
    lens = Mat('lens', 'front', glow=True)
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    framed(g, m, ('south', 'east', 'west', 'up', 'down'), 2, 1)
    louvres(g, ('south', 'east', 'west'), rows=(5, 7, 9), a0=3, a1=13, depth=2)
    lens_housing(g, 'north', m, lens, open_lo=3, open_hi=13, depth=3)
    tex = {'particle': 'laser_casing', 'side': 'laser_casing', 'front': 'bending_laser_front'}
    return {'bending_laser': model(g, tex)}


def lance_emitter():
    m = Mat('lance', 'side', up='top', down='bottom')
    g = Grid()
    g.fill(0, 0, 0, N, N, N, m)
    # Glowing conduits run the full height, so stacked emitters line up into one column.
    for s in SIDES4:
        for a in (4, 11):
            g.face(s, a, 0, a + 1, N, 0, 1)
    tex = {'particle': 'lance_side', 'side': 'lance_side', 'top': 'lance_top', 'bottom': 'laser_casing'}
    return {'lance_emitter': model(g, tex)}


def alarm_light():
    base = Mat('base', 'base')
    lamp = Mat('lamp', 'lamp', glow=True)
    g = Grid()
    g.fill(3, 0, 3, 13, 3, 13, base)
    g.fill(5, 3, 5, 11, 10, 11, lamp)
    # A wire guard round the lamp: four posts and a crossed cap.
    for x, z in ((4, 4), (11, 4), (4, 11), (11, 11)):
        g.fill(x, 3, z, x + 1, 11, z + 1, base)
    g.fill(4, 10, 4, 12, 11, 5, base)
    g.fill(4, 10, 11, 12, 11, 12, base)
    g.fill(4, 10, 5, 5, 11, 11, base)
    g.fill(11, 10, 5, 12, 11, 11, base)
    g.fill(7, 10, 5, 9, 11, 11, base)
    for y in (6,):
        g.fill(4, y, 5, 5, y + 1, 11, base)
        g.fill(11, y, 5, 12, y + 1, 11, base)
        g.fill(5, y, 4, 11, y + 1, 5, base)
        g.fill(5, y, 11, 11, y + 1, 12, base)
    tex = {'particle': 'alarm_light_lamp', 'base': 'alarm_light_base', 'lamp': 'alarm_light_lamp'}
    return {'alarm_light': model(g, tex, occludes=False)}


def reactor_core():
    out = {}
    for name, inner in (('reactor_core', 'reactor_core_inner'), ('reactor_core_formed', 'reactor_core_inner_formed')):
        cage = Mat('cage', 'cage')
        plasma = Mat('plasma', 'inner', glow=True)
        g = Grid()
        g.fill(0, 0, 0, N, N, N, cage)
        g.clear(2, 2, 0, 14, 14, N)            # open the faces, leaving the 12 edge beams
        g.clear(0, 2, 2, N, 14, 14)
        g.clear(2, 0, 2, 14, N, 14)
        for x in (0, 13):                      # heavier brackets at the corners
            for y in (0, 13):
                for z in (0, 13):
                    g.fill(x, y, z, x + 3, y + 3, z + 3, cage)
        g.fill(4, 4, 4, 12, 12, 12, plasma)
        tex = {'particle': 'reactor_core_cage', 'cage': 'reactor_core_cage', 'inner': inner}
        out[name] = model(g, tex, render_type='minecraft:cutout', occludes=False)
    return out


PARTS = [ring_frame, ring_beam_port, reactor_port, reactor_core, stabilizer_casing, stabilizer_emitter,
         axial_emitter, stabilizer_coil, mass_input_port, stabilizer_console, breaker_panel, bending_laser,
         lance_emitter, alarm_light]


def main():
    out_dir = sys.argv[1]
    for part in PARTS:
        for name, m in part().items():
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
                json.dump(m, f, indent=2)
                f.write('\n')
            print(f'{name}: {len(m["elements"])} elements')


if __name__ == '__main__':
    main()
