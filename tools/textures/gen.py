"""Procedural pixel-art textures for Masteri's Black Hole Energy.

Every texture is 16 px wide; animated ones stack frames vertically (same frame counts as before).
Light comes from the top left. Part families keep their colors: ring/reactor steel blue, stabilizer purple,
laser/lance amber, console gunmetal, research teal, zone dark violet, bomb red.

Run from the project root:
    python -I tools/textures/gen.py src/main/resources/assets/masteris_bhe/textures --preview preview.png
Add --dry to only write the preview.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngio  # noqa: E402

CLEAR = (0, 0, 0, 0)


def hx(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3])


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(4))


def tint(c, t):
    return mix(c, (255, 255, 255, c[3]), t)


def h(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


class Img:
    def __init__(self, w=16, hgt=16, fill=CLEAR):
        self.w, self.h = w, hgt
        self.p = [[fill] * w for _ in range(hgt)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def get(self, x, y):
        return self.p[y][x]

    def blend(self, x, y, c, a=None):
        """Alpha-composite c over the pixel (a overrides c's alpha, 0..1)."""
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        a = c[3] / 255.0 if a is None else a
        if a <= 0:
            return
        d = self.p[y][x]
        if d[3] == 0:
            self.p[y][x] = (c[0], c[1], c[2], clamp(255 * a))
            return
        out_a = a + d[3] / 255.0 * (1 - a)
        self.p[y][x] = (clamp((c[0] * a + d[0] * (1 - a))), clamp((c[1] * a + d[1] * (1 - a))),
                        clamp((c[2] * a + d[2] * (1 - a))), clamp(255 * out_a))

    def add(self, x, y, c, a):
        """Additive glow."""
        if not (0 <= x < self.w and 0 <= y < self.h) or a <= 0:
            return
        d = self.p[y][x]
        self.p[y][x] = (clamp(d[0] + c[0] * a), clamp(d[1] + c[1] * a), clamp(d[2] + c[2] * a), max(d[3], clamp(255 * min(1, a))))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def bevel(self, x0, y0, x1, y1, light, dark):
        for x in range(x0, x1 + 1):
            self.set(x, y0, light)
            self.set(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.set(x0, y, light if y < y1 else dark)
            self.set(x1, y, dark if y > y0 else light)

    def inset(self, x0, y0, x1, y1, base):
        """A recessed panel: dark top/left lip, light bottom/right lip."""
        self.bevel(x0, y0, x1, y1, shade(base, 0.55), tint(base, 0.18))

    def rivet(self, x, y, base):
        self.set(x, y, tint(base, 0.45))
        self.set(x + 1, y + 1, shade(base, 0.45))
        self.set(x + 1, y, shade(base, 0.85))
        self.set(x, y + 1, shade(base, 0.85))

    def disc(self, cx, cy, r, c, a=1.0):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.blend(x, y, c, a * min(1.0, r - d + 0.5))

    def glow(self, cx, cy, r, c, strength=1.0, power=2.0):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < r:
                    self.add(x, y, c, strength * (1 - d / r) ** power)

    def stack(self, frames):
        out = Img(16, 16 * len(frames))
        for i, f in enumerate(frames):
            for y in range(16):
                out.p[i * 16 + y] = list(f.p[y])
        return out


# ---------------------------------------------------------------- building blocks

def plate(base, seed, grain=0.07):
    """Brushed metal with a soft top-left light, a bright top/left edge and a dark bottom/right edge."""
    img = Img()
    for y in range(16):
        for x in range(16):
            light = 1.06 - 0.12 * (x + y) / 30.0
            streak = 1 + grain * (h(x // 3, y, seed) - 0.5) + 0.5 * grain * (h(x, y, seed + 1) - 0.5)
            img.set(x, y, shade(base, light * streak))
    img.bevel(0, 0, 15, 15, tint(base, 0.3), shade(base, 0.45))
    return img


def corner_rivets(img, base, inset=1):
    for x, y in ((inset, inset), (14 - inset, inset), (inset, 14 - inset), (14 - inset, 14 - inset)):
        img.rivet(x, y, base)


def lens(img, cx, cy, r, rim, core, glow_strength=1.0):
    """A round lens: dark housing ring, a colored glass body brighter in the middle, a glint top-left."""
    img.disc(cx, cy, r + 1.2, shade(rim, 0.35))
    img.disc(cx, cy, r + 0.4, shade(rim, 0.8))
    img.disc(cx, cy, r - 0.3, shade(core, 0.55))
    img.glow(cx, cy, r, core, 0.9 * glow_strength, 1.2)
    img.glow(cx, cy, r * 0.5, (255, 255, 255, 255), 0.6 * glow_strength, 1.5)
    gx, gy = int(cx - r * 0.45), int(cy - r * 0.45)
    img.blend(gx, gy, (255, 255, 255, 255), 0.85)
    img.blend(gx + 1, gy, (255, 255, 255, 255), 0.35)
    img.blend(gx, gy + 1, (255, 255, 255, 255), 0.35)


def hazard(img, y0, y1, a=hx('f2c230'), b=hx('1c1c1f'), offset=0):
    for y in range(y0, y1 + 1):
        for x in range(16):
            c = a if ((x + y + offset) // 2) % 2 == 0 else b
            img.set(x, y, shade(c, 1.05 - 0.1 * (y - y0) / max(1, y1 - y0)))


def stone(base, seed, crack_color=None, crack_glow=0.0):
    img = Img()
    for y in range(16):
        for x in range(16):
            n = 0.55 * h(x // 2, y // 2, seed) + 0.3 * h(x, y, seed + 7) + 0.15 * h(x // 4, y // 4, seed + 3)
            img.set(x, y, shade(base, 0.72 + 0.5 * n))
    # cracks: short random walks, dark with an optional faint glow
    for k in range(3):
        x, y = int(h(k, 1, seed) * 14) + 1, int(h(k, 2, seed) * 14) + 1
        for step in range(7):
            img.set(x, y, shade(base, 0.4))
            if crack_color:
                img.add(x, y, crack_color, crack_glow * (0.6 + 0.4 * h(step, k, seed)))
            x += [-1, 0, 1][int(h(step, k, seed + 11) * 3)]
            y += [0, 1, 1][int(h(step, k, seed + 13) * 3)]
            if not (0 <= x < 16 and 0 <= y < 16):
                break
    return img


# ---------------------------------------------------------------- palettes

STEEL = hx('4a5f86')       # reactor / ring
STEEL_DARK = hx('2c3850')
CYAN = hx('5fe6ff')
VIOLET = hx('9a5cff')
LAVENDER = hx('d2b8ff')
PURPLE = hx('5a3a8c')      # stabilizers
PURPLE_DARK = hx('34224f')
AMBER = hx('ff9a2e')       # laser / lance
BRONZE = hx('5a4632')
GUN = hx('3c424c')         # console
TEAL = hx('2f7f78')        # research
MINT = hx('62ffc4')
VOIDSTONE = hx('3d3449')   # zone
RED = hx('a8262c')         # bomb


# ---------------------------------------------------------------- blocks

def ring_frame():
    img = plate(STEEL, 1)
    for x in range(1, 15):          # horizontal seam
        img.set(x, 7, shade(STEEL, 0.5))
        img.set(x, 8, tint(STEEL, 0.2))
    img.set(0, 7, shade(STEEL, 0.4)); img.set(15, 8, shade(STEEL, 0.4))
    for y in (3, 12):               # bolt rows
        for x in (3, 12):
            img.rivet(x, y, STEEL)
    for x in range(5, 11):          # blue sheen strip
        img.blend(x, 4, CYAN, 0.12)
        img.blend(x, 11, CYAN, 0.12)
    return img


def ring_beam_port():
    img = plate(STEEL, 2)
    img.inset(2, 2, 13, 13, STEEL)
    img.rect(3, 3, 12, 12, shade(STEEL_DARK, 0.9))
    lens(img, 8, 8, 3.6, STEEL, CYAN)
    for x, y in ((8, 1), (8, 14), (1, 8), (14, 8)):  # alignment ticks
        img.set(x, y, tint(CYAN, 0.2)); img.set(x - (y in (1, 14)) * 1 if False else x, y, tint(CYAN, 0.2))
    return img


def reactor_port():
    img = plate(STEEL, 3)
    img.inset(2, 2, 13, 13, STEEL)
    img.rect(3, 3, 12, 12, STEEL_DARK)
    for y in range(5, 11, 2):       # connector pins
        for x in range(5, 11, 2):
            img.set(x, y, shade(hx('c9a24a'), 1.0))
            img.set(x + 1, y + 1, shade(hx('c9a24a'), 0.5))
    img.set(4, 4, hx('ff4d4d')); img.add(4, 4, hx('ff4d4d'), 0.4)
    img.set(11, 4, CYAN); img.add(11, 4, CYAN, 0.4)
    corner_rivets(img, STEEL, 0)
    return img


def reactor_core_cage():
    img = Img()
    frame = hx('3a4258')
    for y in range(16):
        for x in range(16):
            edge = x <= 1 or y <= 1 or x >= 14 or y >= 14
            if edge:
                light = 1.15 if (x <= 0 or y <= 0) else 0.55 if (x >= 15 or y >= 15) else 0.9
                img.set(x, y, shade(frame, light))
    for i in range(2, 14):          # thin inner rim, glowing faintly violet
        for x, y in ((i, 2), (2, i), (i, 13), (13, i)):
            img.set(x, y, shade(frame, 0.7))
            img.add(x, y, VIOLET, 0.12)
    for x, y in ((0, 0), (12, 0), (0, 12), (12, 12)):  # corner brackets
        img.rect(x, y, x + 3, y + 3, shade(frame, 1.25))
        img.rivet(x + 1, y + 1, frame)
    return img


def plasma(frame, frames, energy):
    """Swirling violet plasma ball, turning a little each frame."""
    img = Img(fill=hx('07030d'))
    t = frame / frames * math.tau
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            arms = 0.5 + 0.5 * math.sin(3 * a - r * 0.9 + t)
            core = max(0.0, 1 - r / 8.5)
            v = core ** 1.4 * (0.55 + 0.45 * arms) * energy
            c = mix(hx('2a0f55'), VIOLET, v * 1.4)
            c = mix(c, LAVENDER, max(0, v - 0.55) * 2)
            c = mix(c, (255, 255, 255, 255), max(0, v - 0.85) * 3)
            img.set(x, y, c)
    return img


def reactor_core_inner():
    return Img().stack([plasma(i, 4, 0.75) for i in range(4)])


def reactor_core_inner_formed():
    return Img().stack([plasma(i, 4, 1.15) for i in range(4)])


def stabilizer_casing():
    img = plate(PURPLE, 4)
    img.inset(3, 3, 12, 12, PURPLE)
    img.rect(4, 4, 11, 11, shade(PURPLE, 0.8))
    for y in range(5, 11, 2):       # vent slots
        for x in range(5, 11):
            img.set(x, y, shade(PURPLE_DARK, 0.7))
            img.set(x, y + 1, shade(PURPLE, 0.95))
    corner_rivets(img, PURPLE)
    return img


def stabilizer_coil_frame(frame):
    img = Img()
    copper = hx('7a4ab8')
    for y in range(16):
        band = y % 4
        for x in range(16):
            base = shade(copper, [1.25, 1.0, 0.75, 0.5][band] * (1.03 - 0.06 * x / 15))
            img.set(x, y, base)
    for y in range(16):             # side rails
        for x in (0, 15):
            img.set(x, y, shade(PURPLE_DARK, 0.9 if x == 0 else 0.6))
    pulse = (frame * 4) % 16        # energy pulse sliding down the windings
    for y in range(16):
        d = min(abs(y - pulse), 16 - abs(y - pulse))
        for x in range(1, 15):
            img.add(x, y, LAVENDER, 0.5 * max(0, 1 - d / 3.0))
    return img


def stabilizer_coil():
    return Img().stack([stabilizer_coil_frame(i) for i in range(4)])


def stabilizer_emitter_front():
    img = plate(PURPLE, 5)
    img.inset(1, 1, 14, 14, PURPLE)
    img.rect(2, 2, 13, 13, shade(PURPLE_DARK, 1.0))
    lens(img, 8, 8, 4.6, PURPLE, VIOLET, 1.1)
    for a in range(8):              # focusing fins
        ang = a / 8 * math.tau
        x, y = int(round(8 + math.cos(ang) * 6.2 - 0.5)), int(round(8 + math.sin(ang) * 6.2 - 0.5))
        img.set(x, y, tint(PURPLE, 0.3))
    return img


def mass_input_top():
    img = plate(PURPLE, 6)
    img.inset(2, 2, 13, 13, PURPLE)
    img.rect(3, 3, 12, 12, hx('120a1c'))
    for y in range(3, 13):          # grate
        for x in range(3, 13):
            if x % 3 == 0 or y % 3 == 0:
                img.set(x, y, shade(PURPLE, 0.9 if (x + y) % 2 else 0.75))
    img.glow(8, 8, 6, VIOLET, 0.5, 1.5)  # the mass glowing below
    corner_rivets(img, PURPLE, 0)
    return img


def laser_casing():
    img = plate(BRONZE, 7)
    for x in range(2, 14):          # amber trim
        img.set(x, 2, shade(AMBER, 0.9))
        img.set(x, 13, shade(AMBER, 0.6))
    for y in range(5, 11, 2):
        for x in range(3, 13):
            img.set(x, y, shade(BRONZE, 0.5))
    corner_rivets(img, BRONZE, 0)
    return img


def bending_laser_front():
    img = laser_casing()
    img.rect(2, 3, 13, 12, shade(BRONZE, 0.6))
    img.inset(2, 3, 13, 12, BRONZE)
    lens(img, 8, 8, 3.8, BRONZE, AMBER, 1.1)
    hazard(img, 14, 14)
    return img


def lance_side():
    img = plate(hx('6a4a2e'), 8)
    for x in (4, 11):               # glowing conduits
        for y in range(1, 15):
            img.set(x, y, shade(hx('2a1a10'), 1))
            img.set(x + 1 - 2 * (x == 11), y, shade(hx('6a4a2e'), 0.7))
            img.add(x, y, AMBER, 0.55 + 0.25 * math.sin(y * 0.8))
    for y in (1, 7, 13):
        img.rivet(7, y, hx('6a4a2e'))
    return img


def lance_top():
    img = plate(hx('6a4a2e'), 9)
    img.inset(1, 1, 14, 14, hx('6a4a2e'))
    img.rect(2, 2, 13, 13, hx('1a120c'))
    for r in (6.2, 4.6):            # concentric focusing rings
        for y in range(16):
            for x in range(16):
                if abs(math.hypot(x + 0.5 - 8, y + 0.5 - 8) - r) < 0.5:
                    img.set(x, y, shade(AMBER, 0.55))
    lens(img, 8, 8, 3.2, hx('6a4a2e'), hx('ffd27a'), 1.3)
    return img


def console_side():
    img = plate(GUN, 10)
    img.inset(2, 2, 13, 13, GUN)
    for y in range(4, 12, 2):
        for x in range(4, 12):
            img.set(x, y, shade(GUN, 0.55))
    img.set(12, 12, hx('40d080')); img.add(12, 12, hx('40d080'), 0.4)
    corner_rivets(img, GUN, 0)
    return img


def console_screen():
    img = Img(fill=GUN)
    img.bevel(0, 0, 15, 15, tint(GUN, 0.3), shade(GUN, 0.45))
    img.rect(1, 1, 14, 14, hx('071a1e'))
    for y in range(1, 15):          # scanlines
        if y % 2 == 0:
            for x in range(1, 15):
                img.blend(x, y, (0, 0, 0, 255), 0.25)
    for x in range(2, 14):          # waveform
        y = int(round(5 + 2 * math.sin(x * 0.9)))
        img.set(x, y, hx('5fe6ff')); img.add(x, y, CYAN, 0.25)
    for i, (w, c) in enumerate(((9, hx('40d080')), (6, hx('40d080')), (7, hx('f2c230')))):
        for x in range(2, 2 + w):
            img.set(x, 9 + i * 2, c)
    img.set(12, 12, hx('ff4d4d')); img.add(12, 12, hx('ff4d4d'), 0.5)
    for i in range(4):              # glare
        img.blend(10 + i, 2 + i, (255, 255, 255, 255), 0.12)
    return img


def alarm_light_base():
    img = plate(hx('2a2d33'), 11)
    hazard(img, 12, 14, offset=1)
    corner_rivets(img, hx('2a2d33'), 0)
    return img


def alarm_lamp(on):
    img = Img(fill=hx('3a0a0c'))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            v = max(0.0, 1 - d / 10.5)
            img.set(x, y, mix(hx('4a0d10'), hx('ff3b30') if on else hx('8a1a1c'), v * 1.2))
    if on:
        img.glow(8, 8, 6, hx('ffb0a0'), 0.7, 1.6)
    for x in range(16):             # lens ribs
        if x % 4 == 1:
            for y in range(16):
                img.blend(x, y, (0, 0, 0, 255), 0.18)
    img.blend(4, 3, (255, 255, 255, 255), 0.7 if on else 0.35)
    img.blend(5, 3, (255, 255, 255, 255), 0.3)
    return img


def alarm_light_lamp():
    return Img().stack([alarm_lamp(True), alarm_lamp(False)])


def black_hole():
    img = Img(fill=hx('05020a'))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            ring = math.exp(-((d - 6.0) / 1.1) ** 2)
            photon = math.exp(-((d - 4.3) / 0.45) ** 2)
            c = mix(hx('05020a'), VIOLET, ring * 0.9)
            c = mix(c, hx('ffb36b'), ring * 0.35 * (0.5 + 0.5 * math.sin(math.atan2(y - 8, x - 8) * 2)))
            c = mix(c, LAVENDER, photon)
            if d < 4.0:
                c = (0, 0, 0, 255)
            img.set(x, y, c)
    return img


def containment_box():
    img = Img()
    frame = hx('5b6478')
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0 or x == 15 or y == 15:
                img.set(x, y, shade(frame, 1.2 if (x == 0 or y == 0) else 0.6))
            elif x == 1 or y == 1 or x == 14 or y == 14:
                img.set(x, y, shade(frame, 0.85))
            else:
                img.set(x, y, (170, 190, 255, 38))       # faintly tinted glass
    for i in range(5):              # reflections
        img.set(3 + i, 8 - i, (230, 240, 255, 120))
    for i in range(3):
        img.set(9 + i, 12 - i, (230, 240, 255, 80))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        img.rivet(x, y, frame)
    return img


def bomb_side():
    img = plate(RED, 12)
    hazard(img, 1, 2)
    hazard(img, 13, 14)
    img.inset(4, 4, 11, 11, RED)
    img.rect(5, 5, 10, 10, hx('05020a'))
    img.disc(8, 8, 2.2, hx('000000'))
    img.glow(8, 8, 3.6, VIOLET, 0.6, 1.3)
    img.blend(6, 6, (255, 255, 255, 255), 0.5)
    return img


def bomb_top():
    img = plate(RED, 13)
    img.disc(8, 8, 6.4, shade(RED, 0.55))
    img.disc(8, 8, 5.4, hx('2a0a0e'))
    img.disc(8, 8, 3.0, hx('000000'))
    img.glow(8, 8, 5.5, VIOLET, 0.5, 1.2)
    for a in range(6):              # bolts round the hatch
        ang = a / 6 * math.tau
        img.rivet(int(7.5 + math.cos(ang) * 6.6), int(7.5 + math.sin(ang) * 6.6), RED)
    return img


def corrupted_stone():
    return stone(VOIDSTONE, 14, VIOLET, 0.75)


def dark_ore_frame(frame):
    img = stone(VOIDSTONE, 15, VIOLET, 0.45)
    pulse = 0.55 + 0.45 * math.sin(frame / 4 * math.tau)
    for k, (cx, cy) in enumerate(((4, 4), (11, 6), (6, 11), (12, 12))):
        size = 1.6 + 0.6 * h(k, 0, 15)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if abs(dx) + abs(dy) * 0.7 <= size:     # little diamond crystals
                    lit = 0.6 + 0.4 * (dy < 0) - 0.2 * (dx > 0)
                    img.set(x, y, shade(mix(VIOLET, LAVENDER, 0.3), lit))
        img.glow(cx, cy, 3.5, VIOLET, 0.45 * pulse, 1.4)
        img.blend(int(cx - 1), int(cy - 1), (255, 255, 255, 255), 0.6 * pulse)
    return img


def unstable_dark_ore():
    return Img().stack([dark_ore_frame(i) for i in range(4)])


def research_casing():
    img = plate(TEAL, 16, 0.04)
    img.inset(2, 2, 13, 13, TEAL)
    for x in range(3, 13):
        img.set(x, 7, shade(TEAL, 0.6))
        img.set(x, 8, tint(TEAL, 0.15))
    img.set(3, 3, MINT); img.add(3, 3, MINT, 0.3)
    img.set(12, 12, MINT); img.add(12, 12, MINT, 0.3)
    return img


def research_core():
    img = plate(TEAL, 17, 0.04)
    img.inset(1, 1, 14, 14, TEAL)
    img.rect(2, 2, 13, 13, shade(TEAL, 0.45))
    lens(img, 8, 8, 4.4, TEAL, MINT, 1.1)
    img.disc(8, 8, 1.4, hx('062a20'))           # pupil
    return img


# ---------------------------------------------------------------- items

def outline_item(img, color=(20, 10, 30, 255)):
    """1 px dark outline around the item's opaque pixels, like vanilla items."""
    src = [row[:] for row in img.p]
    for y in range(16):
        for x in range(16):
            if src[y][x][3] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[ny][nx][3] > 128:
                    img.set(x, y, color)
                    break
    return img


def crystal(colors, glints, seed, tall=True):
    """A faceted crystal: light left facet, dark right facet, bright edge, glints."""
    img = Img()
    lo, mid, hi = colors
    for y in range(2, 14):
        half = int(round((1 - abs(y - 7.5) / 6.5) * (3.5 if tall else 4.5)))
        for x in range(8 - half, 8 + half):
            left = x < 8
            c = mid if left else lo
            if x == 8 - half:
                c = hi
            if left and (y + x) % 5 == 0:
                c = tint(mid, 0.2)
            img.set(x, y, c)
    for x, y in glints:
        img.set(x, y, (255, 255, 255, 255))
    return outline_item(img)


def charged_dark_matter_crystal():
    img = crystal((hx('5a2fb0'), hx('9a5cff'), hx('e6d4ff')), ((6, 5), (7, 9)), 1)
    img.glow(8, 8, 7, CYAN, 0.25, 1.5)
    img.set(12, 3, CYAN); img.set(3, 12, CYAN); img.set(13, 10, LAVENDER)
    return img


def dark_matter_shard():
    img = Img()
    pts = ((6, 2), (10, 5), (11, 10), (7, 14), (5, 9))
    for y in range(16):
        for x in range(16):
            inside = True
            for i in range(len(pts)):
                ax, ay = pts[i]
                bx, by = pts[(i + 1) % len(pts)]
                if (bx - ax) * (y + 0.5 - ay) - (by - ay) * (x + 0.5 - ax) < 0:
                    inside = False
                    break
            if inside:
                c = hx('3a2160') if x + 0.5 > 8 - (y - 8) * 0.2 else hx('5d3a96')
                img.set(x, y, c)
    for i in range(5):
        img.set(6 + i // 2, 3 + i, hx('9a7cd0'))
    img.set(7, 6, (255, 255, 255, 255))
    return outline_item(img)


def configurator():
    img = Img()
    handle, grip = hx('6b7487'), hx('2c2f38')
    for i in range(10):             # diagonal shaft
        x, y = 2 + i, 13 - i
        img.set(x, y, handle)
        img.set(x + 1, y, shade(handle, 0.6))
        if i < 4:
            img.set(x, y, grip); img.set(x + 1, y, shade(grip, 0.6))
    img.disc(12.5, 3.5, 2.6, shade(VIOLET, 0.8))  # crystal tip in a claw
    img.glow(12.5, 3.5, 3.5, LAVENDER, 0.7, 1.4)
    img.set(11, 2, (255, 255, 255, 255))
    for x, y in ((9, 2), (10, 1), (14, 6), (15, 5)):
        img.set(x, y, handle)
    return outline_item(img)


def event_horizon_fragment():
    img = Img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            a = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
            if 3.2 <= d <= 6.6 and -2.6 < a < 1.6:         # a broken arc of the ring
                t = (d - 3.2) / 3.4
                c = mix(hx('ffd08a'), VIOLET, t)
                img.set(x, y, shade(c, 1.1 - 0.3 * t))
            elif d < 3.0:
                img.set(x, y, hx('050208'))
    img.set(6, 4, (255, 255, 255, 255))
    return outline_item(img)


def graviton_core():
    img = Img()
    img.disc(8, 8, 3.6, hx('1d6f8a'))
    img.glow(8, 8, 4.5, CYAN, 0.9, 1.3)
    img.blend(6, 6, (255, 255, 255, 255), 0.9)
    for t in range(40):             # two orbit rings
        a = t / 40 * math.tau
        for tilt, col in ((0.35, LAVENDER), (-0.35, CYAN)):
            x = 8 + math.cos(a) * 6.6
            y = 8 + math.sin(a) * 6.6 * 0.38 + math.cos(a) * 6.6 * tilt
            px, py = int(x), int(y)
            if not (5 <= px <= 10 and 5 <= py <= 10 and math.sin(a) < 0):  # passes behind the core
                img.set(px, py, col)
    return outline_item(img)


def reactor_manual():
    img = Img()
    cover, spine = hx('3d2470'), hx('24143f')
    img.rect(3, 1, 13, 14, cover)
    img.rect(2, 1, 3, 14, spine)
    for y in range(2, 14):          # page edge
        img.set(14, y, hx('e8dcc0'))
    img.bevel(3, 1, 13, 14, tint(cover, 0.25), shade(cover, 0.6))
    img.disc(8.5, 7.5, 3.4, hx('9a5cff'))      # black hole emblem
    img.disc(8.5, 7.5, 2.2, hx('050208'))
    for x, y in ((4, 2), (12, 2), (4, 13), (12, 13)):   # gold corners
        img.set(x, y, hx('e2b84a'))
    img.set(7, 12, hx('e2b84a')); img.set(8, 12, hx('e2b84a')); img.set(9, 12, hx('e2b84a'))
    return outline_item(img)


def singularity_glass():
    img = Img()
    for y in range(2, 15):
        for x in range(2, 15):
            if x + y < 6 or x + y > 25:
                continue
            d = (x - y) / 12.0
            img.set(x, y, (int(150 + 60 * d), 110, 255, 170))
    for i in range(7):
        img.set(4 + i, 10 - i, (255, 255, 255, 230))
    img.set(11, 12, (255, 255, 255, 200)); img.set(12, 11, (255, 255, 255, 200))
    return outline_item(img, (30, 15, 60, 255))


def unstable_dark_residue():
    img = Img()
    img.disc(8, 9.5, 4.6, hx('8a1f6e'))
    img.disc(6.5, 7.5, 2.6, hx('c03a98'))
    img.glow(8, 9, 5.5, hx('ff5ad2'), 0.45, 1.4)
    img.set(6, 6, (255, 255, 255, 255))
    for x, y in ((12, 4), (3, 13), (13, 12)):   # spatters
        img.set(x, y, hx('c03a98'))
    return outline_item(img)


def void_dust():
    img = Img()
    for y in range(9, 15):
        half = int((y - 8) * 1.2) + 1
        for x in range(8 - half, 8 + half):
            if 0 <= x < 16:
                n = h(x, y, 21)
                img.set(x, y, shade(hx('3a2a55'), 0.6 + 0.6 * n))
    for x, y in ((7, 9), (5, 12), (10, 11), (12, 13), (4, 6), (11, 6), (8, 4)):  # sparkles
        img.set(x, y, LAVENDER)
    return outline_item(img)


BLOCKS = {
    'alarm_light_base': alarm_light_base, 'alarm_light_lamp': alarm_light_lamp,
    'bending_laser_front': bending_laser_front, 'black_hole': black_hole,
    'black_hole_bomb_side': bomb_side, 'black_hole_bomb_top': bomb_top,
    'console_screen': console_screen, 'console_side': console_side, 'containment_box': containment_box,
    'corrupted_stone': corrupted_stone, 'lance_side': lance_side, 'lance_top': lance_top,
    'laser_casing': laser_casing, 'mass_input_top': mass_input_top, 'reactor_core_cage': reactor_core_cage,
    'reactor_core_inner': reactor_core_inner, 'reactor_core_inner_formed': reactor_core_inner_formed,
    'reactor_port': reactor_port, 'research_casing': research_casing, 'research_core': research_core,
    'ring_beam_port': ring_beam_port, 'ring_frame': ring_frame, 'stabilizer_casing': stabilizer_casing,
    'stabilizer_coil': stabilizer_coil, 'stabilizer_emitter_front': stabilizer_emitter_front,
    'unstable_dark_ore': unstable_dark_ore,
}
ITEMS = {
    'charged_dark_matter_crystal': charged_dark_matter_crystal, 'configurator': configurator,
    'dark_matter_shard': dark_matter_shard, 'event_horizon_fragment': event_horizon_fragment,
    'graviton_core': graviton_core, 'reactor_manual': reactor_manual, 'singularity_glass': singularity_glass,
    'unstable_dark_residue': unstable_dark_residue, 'void_dust': void_dust,
}


def main():
    out = sys.argv[1]
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    write = '--dry' not in sys.argv
    images = []
    for folder, table in (('block', BLOCKS), ('item', ITEMS)):
        for name, fn in table.items():
            img = fn()
            path = os.path.join(out, folder, name + '.png')
            old = pngio.read(path)
            assert len(old) == img.h, (name, len(old), img.h)   # same frame count as before
            if write:
                pngio.write(path, img.p)
            images.append((name, img.p))
    if preview:
        pngio.write(preview, pngio.sheet(images, scale=6, cols=9))
    print(len(images), 'textures', 'written' if write else '(dry run)')


if __name__ == '__main__':
    main()
