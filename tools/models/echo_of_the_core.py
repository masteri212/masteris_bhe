"""The Echo of the Core's textures and Blockbench model (matching client/EchoModel.java).

Run from the project root:
    python -I tools/models/echo_of_the_core.py [--preview preview.png]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from boxkit import Canvas, faces, write_bbmodel, boxes_of, preview  # noqa: E402

TEX = 'src/main/resources/assets/masteris_bhe/textures/entity/'
HERE = os.path.dirname(os.path.abspath(__file__))
MID = (0, 8, 0)
PODS, POD_RING = 4, 14


def cage():
    boxes = []
    for x in (-6, 5):
        for z in (-6, 5):
            boxes.append((f'post_{x}_{z}', (x, 8 - 6, z), (1, 12, 1), (0, 0), MID, False))
    for y in (-6, 5):
        for z in (-6, 5):
            boxes.append((f'xbar_{y}_{z}', (-5, 8 + y, z), (10, 1, 1), (4, 0), MID, False))
        for x in (-6, 5):
            boxes.append((f'zbar_{y}_{x}', (x, 8 + y, -5), (1, 1, 10), (4, 2), MID, False))
    return boxes


def pods():
    # Blockbench gets the pods unrotated, laid out around the ring; the Java model turns each to face the middle.
    out = []
    for i in range(PODS):
        a = 2 * math.pi * i / PODS
        px, pz = round(math.cos(a) * POD_RING, 3), round(math.sin(a) * POD_RING, 3)
        pivot = (px, 8, pz)
        out.append((f'pod{i}', pivot, [
            (f'pod{i}', (px - 2, 8 - 2, pz - 3), (4, 4, 6), (0, 16), pivot, False),
            (f'lens{i}', (px - 1, 8 - 1, pz - 4), (2, 2, 1), (20, 16), pivot, False),
        ]))
    return out


GROUPS = [('cage', MID, cage()), ('pods', MID, pods())]


def paint():
    base, glow = Canvas(), Canvas()
    # The cage: spectral, a pale cold blue going violet, glowing faintly all over.
    for uv, size in (((0, 0), (1, 12, 1)), ((4, 0), (10, 1, 1)), ((4, 2), (1, 1, 10))):
        for name, rect in faces(uv, size).items():
            base.fill(rect, (150, 200, 255, 170), (120, 110, 240, 160), grain=0.3)
            glow.fill(rect, (50, 80, 150, 255), (60, 40, 140, 255), grain=0.3)
    # The pods: dark stabilizer casing, edged in violet; their lenses burn gold.
    for name, (a, b, c, d) in faces((0, 16), (4, 4, 6)).items():
        base.fill((a, b, c, d), (80, 60, 125, 230), (45, 32, 75, 230), grain=0.3)
        for x in range(a, c):
            base.put(x, b, (150, 120, 220, 240))
    for name, rect in faces((20, 16), (2, 2, 1)).items():
        base.fill(rect, (255, 220, 150, 255))
        glow.fill(rect, (255, 200, 110, 255))
    return base, glow


def main():
    base, glow = paint()
    base.write(TEX + 'echo_of_the_core.png')
    glow.write(TEX + 'echo_of_the_core_glow.png')
    write_bbmodel(os.path.join(HERE, 'echo_of_the_core.bbmodel'), 'echo_of_the_core', GROUPS, TEX + 'echo_of_the_core.png')
    if '--preview' in sys.argv:
        preview(base, boxes_of(GROUPS), sys.argv[sys.argv.index('--preview') + 1], glow=glow)
    print('wrote echo_of_the_core.png, echo_of_the_core_glow.png, echo_of_the_core.bbmodel')


if __name__ == '__main__':
    main()
