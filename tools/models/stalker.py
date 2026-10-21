"""The Lensing Stalker's textures and Blockbench model (matching client/StalkerModel.java).

Run from the project root:
    python -I tools/models/stalker.py [--preview preview.png]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from boxkit import Canvas, faces, write_bbmodel, boxes_of, preview  # noqa: E402

TEX = 'src/main/resources/assets/masteris_bhe/textures/entity/'
HERE = os.path.dirname(os.path.abspath(__file__))

# (name, origin, size, uv, pivot, mirror) in Java model space; groups as in StalkerModel.
HEAD = (0, -18, 0)
HALO = (0, -24, 0)
GROUPS = [
    ('head', HEAD, [
        ('head', (-4, -26, -4), (8, 8, 8), (0, 0), HEAD, False),
        ('halo', HALO, [
            ('halo_front', (-6, -24.5, -6), (12, 1, 1), (16, 16), HALO, False),
            ('halo_back', (-6, -24.5, 5), (12, 1, 1), (16, 16), HALO, False),
            ('halo_left', (-6, -24.5, -5), (1, 1, 10), (16, 18), HALO, False),
            ('halo_right', (5, -24.5, -5), (1, 1, 10), (16, 18), HALO, False),
        ]),
    ]),
    ('body', (0, -18, 0), [('body', (-4, -18, -2), (8, 12, 4), (32, 0), (0, -18, 0), False)]),
    ('right_arm', (-5, -16, 0), [('right_arm', (-6, -18, -1), (2, 28, 2), (0, 16), (-5, -16, 0), False)]),
    ('left_arm', (5, -16, 0), [('left_arm', (4, -18, -1), (2, 28, 2), (0, 16), (5, -16, 0), True)]),
    ('right_leg', (-2, -6, 0), [('right_leg', (-3, -6, -1), (2, 30, 2), (8, 16), (-2, -6, 0), False)]),
    ('left_leg', (2, -6, 0), [('left_leg', (1, -6, -1), (2, 30, 2), (8, 16), (2, -6, 0), True)]),
]

BLACK = (12, 8, 20, 255)
EDGE = (60, 40, 95, 255)
CRACK = (130, 90, 220, 255)


def paint():
    base, glow = Canvas(), Canvas()
    # Head: blank and black, two faint slits for eyes on the front, which glow.
    head = faces((0, 0), (8, 8, 8))
    for name, rect in head.items():
        base.fill(rect, (22, 15, 34, 255), BLACK, grain=0.3)
    u0, v0, u1, v1 = head['north']
    for x in (u0 + 2, u0 + 5):
        base.put(x, v0 + 4, (90, 70, 140, 255))
        glow.put(x, v0 + 4, (230, 210, 255, 255))
    # Body and limbs: black, edged faintly, with cracks of refracted light running down them.
    for uv, size in (((32, 0), (8, 12, 4)), ((0, 16), (2, 28, 2)), ((8, 16), (2, 30, 2))):
        for name, (a, b, c, d) in faces(uv, size).items():
            base.fill((a, b, c, d), (20, 14, 32, 255), BLACK, grain=0.25)
            if name in ('north', 'south', 'east', 'west'):
                for y in range(b, d):
                    base.put(a, y, EDGE)
                    if (y * 3 + a) % 11 == 0:
                        base.put(min(c - 1, a + (y % max(1, c - a))), y, CRACK)
                        glow.put(min(c - 1, a + (y % max(1, c - a))), y, (70, 45, 120, 255))
    # The ring of bent light: pale, and bright in the glow.
    for uv, size in (((16, 16), (12, 1, 1)), ((16, 18), (1, 1, 10))):
        for name, rect in faces(uv, size).items():
            base.fill(rect, (215, 205, 255, 210), (180, 160, 255, 200))
            glow.fill(rect, (200, 180, 255, 255), (150, 110, 255, 255))
    return base, glow


def main():
    base, glow = paint()
    base.write(TEX + 'lensing_stalker.png')
    glow.write(TEX + 'lensing_stalker_glow.png')
    write_bbmodel(os.path.join(HERE, 'lensing_stalker.bbmodel'), 'lensing_stalker', GROUPS, TEX + 'lensing_stalker.png')
    if '--preview' in sys.argv:
        preview(base, boxes_of(GROUPS), sys.argv[sys.argv.index('--preview') + 1], glow=glow)
    print('wrote lensing_stalker.png, lensing_stalker_glow.png, lensing_stalker.bbmodel')


if __name__ == '__main__':
    main()
