"""The Spaghettifier's textures and Blockbench model (matching client/SpaghettifierModel.java).

Run from the project root:
    python -I tools/models/spaghettifier.py [--preview preview.png]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from boxkit import Canvas, faces, write_bbmodel, boxes_of, preview  # noqa: E402

TEX = 'src/main/resources/assets/masteris_bhe/textures/entity/'
HERE = os.path.dirname(os.path.abspath(__file__))

# A chain: head, six segments thinning toward the tail, a tail fin. Each part pivots at its front end.
Y, Z0 = 18, -14
HEAD = (0, Y, Z0)
SEGMENTS = [  # (size, uv, length to the next pivot)
    ((4, 4, 5), (0, 10), 5), ((4, 4, 5), (0, 10), 5),
    ((3, 3, 5), (0, 20), 5), ((3, 3, 5), (0, 20), 5),
    ((2, 2, 4), (0, 30), 4), ((2, 2, 4), (0, 30), 4),
]
FIN = ((1, 5, 4), (20, 0))


def chain():
    """Nested groups from the tail up, so each segment sits inside the one before it."""
    z = Z0
    pivots = []
    for size, uv, length in SEGMENTS:
        pivots.append(z)
        z += length
    fin_pivot = z
    w, h, d = FIN[0]
    node = ('fin', (0, Y, fin_pivot), [('fin', (-w / 2, Y - h / 2, fin_pivot), FIN[0], FIN[1], (0, Y, fin_pivot), False)])
    for i in range(len(SEGMENTS) - 1, -1, -1):
        (w, h, d), uv, _ = SEGMENTS[i]
        p = (0, Y, pivots[i])
        node = ('segment%d' % i, p, [('segment%d' % i, (-w / 2, Y - h / 2, pivots[i]), (w, h, d), uv, p, False), node])
    head_boxes = [
        ('head', (-2.5, Y - 2.5, Z0 - 5), (5, 5, 5), (0, 0), HEAD, False),
        ('whisker_right', (-3.5, Y + 0.5, Z0 - 5), (1, 1, 4), (32, 0), HEAD, False),
        ('whisker_left', (2.5, Y + 0.5, Z0 - 5), (1, 1, 4), (32, 0), HEAD, True),
    ]
    return [('head', HEAD, head_boxes + [node])]


GROUPS = chain()

SKIN_TOP, SKIN_BOTTOM = (120, 40, 60, 255), (40, 12, 30, 255)
STREAK = (255, 140, 60, 255)


def paint():
    base, glow = Canvas(), Canvas()

    def body(uv, size, seed):
        for name, rect in faces(uv, size).items():
            if name == 'down':
                base.fill(rect, (60, 20, 40, 255), (40, 12, 30, 255), grain=0.2)
            else:
                base.fill(rect, SKIN_TOP, SKIN_BOTTOM, grain=0.25)
            u0, v0, u1, v1 = rect
            # Stretched streaks running the length of the body, glowing.
            if name in ('up', 'east', 'west'):
                for x in range(u0, u1):
                    for y in range(v0, v1):
                        if (x * 3 + y * 5 + seed) % 7 == 0 or (name == 'up' and x == (u0 + u1) // 2):
                            base.put(x, y, STREAK)
                            glow.put(x, y, (255, 120, 40, 255))
    body((0, 0), (5, 5, 5), 1)
    for size, uv, _ in SEGMENTS[::2]:
        body(uv, size, uv[1])
    # Face: two small burning eyes and a ring mouth on the front.
    u0, v0, u1, v1 = faces((0, 0), (5, 5, 5))['north']
    for x, y in ((u0 + 1, v0 + 1), (u1 - 2, v0 + 1)):
        base.put(x, y, (255, 230, 150, 255))
        glow.put(x, y, (255, 220, 120, 255))
    for x in range(u0 + 1, u1 - 1):
        base.put(x, v1 - 2, (20, 0, 10, 255))
        glow.put(x, v1 - 2, (255, 80, 30, 255))
    # Tail fin and whiskers: thin, glowing at the edges.
    for rect in faces(FIN[1], FIN[0]).values():
        base.fill(rect, (200, 80, 60, 230), (90, 25, 40, 230), grain=0.2)
        glow.fill(rect, (180, 60, 30, 255), (60, 15, 10, 255))
    for rect in faces((32, 0), (1, 1, 4)).values():
        base.fill(rect, (220, 120, 70, 255), (150, 60, 40, 255))
        glow.fill(rect, (200, 90, 40, 255))
    return base, glow


def main():
    base, glow = paint()
    base.write(TEX + 'spaghettifier.png')
    glow.write(TEX + 'spaghettifier_glow.png')
    write_bbmodel(os.path.join(HERE, 'spaghettifier.bbmodel'), 'spaghettifier', GROUPS, TEX + 'spaghettifier.png')
    if '--preview' in sys.argv:
        preview(base, boxes_of(GROUPS), sys.argv[sys.argv.index('--preview') + 1], glow=glow)
    print('wrote spaghettifier.png, spaghettifier_glow.png, spaghettifier.bbmodel')


if __name__ == '__main__':
    main()
