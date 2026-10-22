#!/usr/bin/env python3
"""Generate Armor HUD's textures: the toughness icon, and the mod menu icon.

The toughness icon is vanilla's armor icon, read out of the cached client jar, recoloured
in netherite's tones: the same chestplate, so it reads as armor, in the material that made
toughness matter.

The mod menu icon is the pieces the HUD draws: four pieces of armor, each the game's own
item texture over its durability bar in the inventory's green-to-red, from whole down to one
hit from breaking. Two by two rather than stacked, since a stack of four does not fit the
icon's height at a size that still reads; each item is drawn at three times, so the four fill
the tile the way the rest of the suite's icons do.

Pure stdlib PNG reader and writer (zlib + struct), no Pillow, deterministic:
re-running produces identical bytes. Nearest neighbour throughout, never smoothed.

Usage: python3 generate_textures.py
"""

import colorsys
import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "src/client/resources/assets/armor-hud-justfatlard")
OUT = os.path.join(ASSETS, "icon.png")
TOUGHNESS_OUT = os.path.join(ASSETS, "textures/gui/sprites/hud/toughness.png")

# armor_full's three greys, light to dark, onto netherite's
NETHERITE = {
    (0xE6, 0xE7, 0xF2): (0x9A, 0x8C, 0x93),
    (0xB8, 0xB9, 0xC4): (0x5E, 0x53, 0x58),
    (0x69, 0x6A, 0x70): (0x3A, 0x32, 0x36),
}

CANVAS = 128                     # the size the rest of the suite uses
SCALE = 3                        # each item pixel, and each bar pixel

BACKGROUND = (0x1D, 0x1F, 0x27, 255)
TRACK = (0, 0, 0, 255)

# (item texture, durability left)
PIECES = [
    ("diamond_helmet", 1.0),
    ("iron_chestplate", 0.8),
    ("netherite_leggings", 0.45),
    ("golden_boots", 0.08),
]

ITEM = 16
BAR_HEIGHT = 2
BAR_OFFSET = 15                  # under the item, over its blank bottom row, as the HUD has it
CELL_X = (10, 70)                # head and chest above, legs and feet below
CELL_Y = (5, 66)


def durability_colour(left):
    """ItemStack.getBarColor: hue from red at nothing left to green at whole."""
    r, g, b = colorsys.hsv_to_rgb(max(0.0, left) / 3.0, 1.0, 1.0)
    return (int(r * 255), int(g * 255), int(b * 255), 255)


def client_jar():
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/*/minecraft-client*.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit("no Loom client jar cached - run a build first")
    return jars[-1]


def read_png(data):
    """Decode a non-interlaced PNG to rows of RGBA tuples: 8-bit, or a palette packed below 8."""
    pos, idat, palette, trns = 8, b"", None, None
    width = height = depth = colour = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(idat)
    # Item textures are small palettes packed several pixels to a byte; the filters still work
    # byte by byte, a whole byte back.
    stride = (width * channels * depth + 7) // 8
    bpp = max(1, channels * depth // 8)
    out, prev = [], bytearray(stride)

    for y in range(height):
        head = y * (stride + 1)
        filt = raw[head]
        line = bytearray(raw[head + 1:head + 1 + stride])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if filt == 1: line[i] = (line[i] + a) & 0xFF
            elif filt == 2: line[i] = (line[i] + b) & 0xFF
            elif filt == 3: line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        prev = line

        if depth < 8:
            per_byte = 8 // depth
            mask = (1 << depth) - 1
            line = bytearray((line[x // per_byte] >> (8 - depth * (x % per_byte + 1))) & mask for x in range(width))

        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if colour == 6: row.append(tuple(px))
            elif colour == 2:
                # A PNG with no alpha channel names one colour as transparent instead.
                rgb = tuple(px[:3])
                clear = trns and len(trns) == 6 and rgb == struct.unpack(">HHH", trns)
                row.append(rgb + (0 if clear else 255,))
            elif colour == 3:
                i = px[0]
                alpha = trns[i] if trns and i < len(trns) else 255
                row.append((palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2], alpha))
            elif colour == 0:
                clear = trns and len(trns) == 2 and px[0] == struct.unpack(">H", trns)[0]
                row.append((px[0], px[0], px[0], 0 if clear else 255))
            else: row.append((px[0], px[0], px[0], px[1]))
        out.append(row)
    return out


def rect(canvas, x0, y0, x1, y1, colour):
    for y in range(y0, y1):
        for x in range(x0, x1):
            canvas[y][x] = colour


def toughness():
    with zipfile.ZipFile(client_jar()) as jar:
        armor = read_png(jar.read("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))
    out = []
    for row in armor:
        line = []
        for px in row:
            rgb = NETHERITE.get(px[:3])
            line.append(rgb + (px[3],) if rgb and px[3] else px)
        out.append(line)
    return out


def build():
    canvas = [[BACKGROUND] * CANVAS for _ in range(CANVAS)]
    with zipfile.ZipFile(client_jar()) as jar:
        for i, (name, left) in enumerate(PIECES):
            x, y = CELL_X[i % 2], CELL_Y[i // 2]
            item = read_png(jar.read("assets/minecraft/textures/item/%s.png" % name))
            for row in range(ITEM):
                for col in range(ITEM):
                    if item[row][col][3]:
                        rect(canvas, x + col * SCALE, y + row * SCALE,
                             x + (col + 1) * SCALE, y + (row + 1) * SCALE, item[row][col])
            bar_top = y + BAR_OFFSET * SCALE
            bar_bottom = bar_top + BAR_HEIGHT * SCALE
            rect(canvas, x, bar_top, x + ITEM * SCALE, bar_bottom, TRACK)
            filled = max(1, round(left * ITEM)) * SCALE
            rect(canvas, x, bar_top, x + filled, bar_bottom, durability_colour(left))
    return canvas


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(path)


if __name__ == "__main__":
    write_png(TOUGHNESS_OUT, toughness())
    write_png(OUT, build())
