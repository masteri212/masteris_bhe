"""Tiny RGBA PNG reader/writer (no Pillow available). Images are lists of rows of (r, g, b, a) tuples."""
import struct
import zlib


def read(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos, idat, w = 8, b'', 0
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
            assert depth == 8, depth
            channels = {6: 4, 2: 3}[ctype]
        elif kind == b'IDAT':
            idat += body
    raw = zlib.decompress(idat)
    stride = w * channels
    rows, prev = [], bytearray(stride)
    i = 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        prev = line
        row = []
        for x in range(w):
            px = line[x * channels:(x + 1) * channels]
            row.append(tuple(px) if channels == 4 else tuple(px) + (255,))
        rows.append(row)
    return rows


def write(path, rows):
    h, w = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + bytes(v for px in row for v in px) for row in rows)

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def sheet(images, scale=8, cols=8, bg=(40, 40, 48, 255), pad=2):
    """Contact sheet of (name, rows) images (only the first 16x16 frame of tall ones), upscaled."""
    cell = 16 * scale + pad * 2
    n = len(images)
    rws = (n + cols - 1) // cols
    out = [[bg] * (cols * cell) for _ in range(rws * cell)]
    for k, (_, img) in enumerate(images):
        ox, oy = (k % cols) * cell + pad, (k // cols) * cell + pad
        for y in range(16 * scale):
            for x in range(16 * scale):
                px = img[y // scale][x // scale]
                if px[3] < 128:
                    px = (90, 90, 100, 255) if ((x // 8 + y // 8) % 2) else (70, 70, 80, 255)
                out[oy + y][ox + x] = px[:3] + (255,)
    return out
