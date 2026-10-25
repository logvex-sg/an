"""Branding and decorative artwork for LOGVEX CRYSTAL (original designs)."""

from __future__ import annotations

from assetlib import Canvas, hex_rgb, lerp_rgb
import icons

# The canonical LOGVEX CRYSTAL pastel palette (mirrors theme/BrandPalette.java).
PINK = 0xFF7FB0
COTTON = 0xFFA6C9
LAVENDER = 0xD4B0F0
LILAC = 0xB9A0F0
BABYBLUE = 0x8FE3FF
CYAN = 0x7FE3F0
MIDNIGHT = 0x14101F
MIDNIGHT2 = 0x1E1630
WHITE = 0xFFFFFF

PALETTE_ORDER = [
    ("midnight", MIDNIGHT), ("deep", MIDNIGHT2), ("pink", PINK), ("cotton", COTTON),
    ("lavender", LAVENDER), ("lilac", LILAC), ("babyblue", BABYBLUE), ("cyan", CYAN),
    ("white", WHITE),
]


def _rgb(v):
    return hex_rgb(v)


# --------------------------------------------------------------------------- motif stamps

def stamp_ears(c, cx, cy, size, color, alpha=1.0):
    """A pair of cat ears, centred at (cx, cy) with the given total width."""
    half = size / 2
    left = [(cx - half, cy + size * 0.35), (cx - half * 0.75, cy - size * 0.55),
            (cx - half * 0.05, cy + size * 0.1)]
    right = [(cx + half, cy + size * 0.35), (cx + half * 0.75, cy - size * 0.55),
             (cx + half * 0.05, cy + size * 0.1)]
    c.fill_poly(left, _rgb(color), alpha)
    c.fill_poly(right, _rgb(color), alpha)


def stamp_bow(c, cx, cy, size, color, alpha=1.0):
    c.fill_poly([(cx, cy), (cx - size, cy - size * 0.7), (cx - size, cy + size * 0.7)], _rgb(color), alpha)
    c.fill_poly([(cx, cy), (cx + size, cy - size * 0.7), (cx + size, cy + size * 0.7)], _rgb(color), alpha)
    c.fill_circle(cx, cy, size * 0.28, _rgb(color), alpha)


def stamp_sparkle(c, cx, cy, size, color, alpha=1.0):
    c.fill_poly([(cx, cy - size), (cx + size * 0.28, cy - size * 0.28), (cx + size, cy),
                 (cx + size * 0.28, cy + size * 0.28), (cx, cy + size),
                 (cx - size * 0.28, cy + size * 0.28), (cx - size, cy),
                 (cx - size * 0.28, cy - size * 0.28)], _rgb(color), alpha)


def stamp_heart(c, cx, cy, size, color, alpha=1.0):
    c.fill_circle(cx - size * 0.42, cy - size * 0.25, size * 0.46, _rgb(color), alpha)
    c.fill_circle(cx + size * 0.42, cy - size * 0.25, size * 0.46, _rgb(color), alpha)
    c.fill_poly([(cx - size * 0.86, cy - size * 0.1), (cx + size * 0.86, cy - size * 0.1),
                 (cx, cy + size)], _rgb(color), alpha)


# --------------------------------------------------------------------------- branding

def logo(width=384, height=128, with_ears=True, transparent=True):
    c = Canvas(width, height)
    if not transparent:
        c.fill_rect_gradient(0, 0, width, height, _rgb(MIDNIGHT), _rgb(MIDNIGHT2))
    # Crystal mark on the left.
    cs = 78
    cx, cy = 66, height / 2
    icon = Canvas(32, 32)
    icons.crystal(icon)
    # tint the crystal with a pink->lavender vertical gradient
    tinted = Canvas(32, 32)
    for y in range(32):
        col = lerp_rgb(_rgb(PINK), _rgb(LAVENDER), y / 31)
        for x in range(32):
            a = icon.px[y, x, 3]
            if a > 0:
                tinted.px[y, x] = [col[0], col[1], col[2], a]
    # upscale nearest-neighbour into the logo
    big = 96
    scale = big / 32
    for y in range(big):
        for x in range(big):
            sy, sx = int(y / scale), int(x / scale)
            tinted_src = tinted.px[sy, sx]
            if tinted_src[3] > 0:
                c.px[int(cy - big / 2) + y, int(cx - big / 2) + x] = tinted_src
    # Wordmark.
    _wordmark(c, 138, 33, "LOGVEX", "CRYSTAL", 2.0)
    if with_ears:
        stamp_ears(c, cx, cy - 52, 34, PINK, 0.95)
    stamp_sparkle(c, width - 40, 26, 9, BABYBLUE, 0.9)
    stamp_sparkle(c, width - 62, 100, 6, LAVENDER, 0.8)
    stamp_heart(c, 118, height - 22, 7, COTTON, 0.8)
    return c


def _wordmark(c, x, cy, top, bottom, s):
    """A small hand-drawn blocky wordmark (5x7 font, original pixel forms)."""
    glyphs = _font5x7()
    px = 3 * s
    y0 = cy - 3.5 * px
    cx = x
    for ch in top:
        g = glyphs.get(ch)
        if g:
            _draw_glyph(c, g, cx, y0, px, _rgb(PINK))
        cx += 4 * px
    y1 = cy + 1.0 * px
    cx = x
    for ch in bottom:
        g = glyphs.get(ch)
        if g:
            _draw_glyph(c, g, cx, y1, px, _rgb(BABYBLUE))
        cx += 4 * px


def _draw_glyph(c, g, x, y, px, color):
    for row, line in enumerate(g):
        for col, on in enumerate(line):
            if on == "1":
                c.fill_rect(x + col * px, y + row * px, px, px, color)


def _font5x7():
    # Original 5x7 bitmap letterforms (subset used by the wordmark).
    F = {
        "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
        "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
        "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
        "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
        "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
        "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
        "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
        "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
        "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
        "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
        "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
        "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    }
    return F


def app_icon(size=128):
    c = Canvas(size, size)
    # rounded midnight tile with a pink->lavender gradient border
    c.fill_rect(0, 0, size, size, _rgb(MIDNIGHT), radius=size * 0.22)
    c.stroke_rect(1, 1, size - 2, size - 2, _rgb(PINK), 3, radius=size * 0.22, alpha=0.9)
    # crystal
    icon = Canvas(32, 32)
    icons.crystal(icon)
    big = int(size * 0.66)
    scale = big / 32
    off = (size - big) / 2
    for y in range(big):
        for x in range(big):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                col = lerp_rgb(_rgb(PINK), _rgb(BABYBLUE), y / max(1, big))
                c.px[int(off) + y, int(off) + x] = [col[0], col[1], col[2], a]
    stamp_ears(c, size / 2, size * 0.13, size * 0.34, PINK, 0.95)
    return c


def header_logo(width=220, height=40):
    c = Canvas(width, height)
    cs = 30
    icon = Canvas(32, 32)
    icons.crystal(icon)
    big = 30
    scale = big / 32
    for y in range(big):
        for x in range(big):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                col = lerp_rgb(_rgb(PINK), _rgb(LAVENDER), y / big)
                c.px[5 + y, 4 + x] = [col[0], col[1], col[2], a]
    _wordmark(c, 42, height / 2, "LOGVEX", "CRYSTAL", 1.0)
    stamp_sparkle(c, width - 16, 12, 5, BABYBLUE, 0.9)
    return c


def loading_art(width=320, height=180):
    c = Canvas(width, height)
    c.fill_rect_gradient(0, 0, width, height, _rgb(MIDNIGHT), _rgb(MIDNIGHT2))
    # soft radial glow
    glow = Canvas(width, height)
    glow.fill_circle(width / 2, height * 0.52, 62, _rgb(PINK), 0.5)
    glow.blur_self(26)
    c.paste(glow, 0, 0)
    icon = Canvas(32, 32)
    icons.crystal(icon)
    big = 92
    scale = big / 32
    ox, oy = int(width / 2 - big / 2), int(height * 0.52 - big / 2)
    for y in range(big):
        for x in range(big):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                col = lerp_rgb(_rgb(PINK), _rgb(BABYBLUE), y / big)
                c.px[oy + y, ox + x] = [col[0], col[1], col[2], a]
    stamp_ears(c, width / 2, oy - 6, 30, PINK, 0.95)
    for (x, y, s, col) in [(46, 40, 8, BABYBLUE), (272, 52, 6, LAVENDER),
                           (60, 140, 5, COTTON), (268, 132, 7, LILAC),
                           (160, 24, 5, WHITE), (110, 158, 4, PINK)]:
        stamp_sparkle(c, x, y, s, col, 0.85)
    for (x, y, s) in [(30, 90, 6), (292, 96, 6), (86, 30, 5)]:
        stamp_heart(c, x, y, s, COTTON, 0.7)
    return c


def empty_state(width=160, height=120):
    c = Canvas(width, height)
    icon = Canvas(32, 32)
    icons.paw(icon)
    big = 64
    scale = big / 32
    ox, oy = int(width / 2 - big / 2), 16
    for y in range(big):
        for x in range(big):
            a = icon.px[int(y / scale), int(x / scale), 3] * 0.35
            if a > 0:
                c.px[oy + y, ox + x] = [_rgb(LAVENDER)[0], _rgb(LAVENDER)[1], _rgb(LAVENDER)[2], a]
    stamp_sparkle(c, width / 2 - 46, 34, 5, BABYBLUE, 0.6)
    stamp_sparkle(c, width / 2 + 46, 44, 4, PINK, 0.6)
    return c


def watermark_cat(width=96, height=48):
    c = Canvas(width, height)
    stamp_ears(c, width / 2, 20, 34, PINK, 0.95)
    icon = Canvas(32, 32)
    icons.cat(icon)
    # small cat face
    for y in range(32):
        for x in range(32):
            a = icon.px[y, x, 3]
            if a > 0:
                c.px[y + 8, x + int(width / 2 - 16)] = [_rgb(COTTON)[0], _rgb(COTTON)[1], _rgb(COTTON)[2], a]
    return c


def status_icon(kind, size=16):
    c = Canvas(size, size)
    icon = Canvas(32, 32)
    {"success": icons.confirm, "warn": icons.warning, "error": icons.error,
     "info": icons.sparkle}[kind](icon)
    col = {"success": PINK, "warn": 0xFFC46B, "error": 0xFF6B85, "info": BABYBLUE}[kind]
    scale = size / 32
    for y in range(size):
        for x in range(size):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                c.px[y, x] = [_rgb(col)[0], _rgb(col)[1], _rgb(col)[2], a]
    return c


# --------------------------------------------------------------------------- decoration textures

def particle_heart(size=16):
    c = Canvas(size, size)
    stamp_heart(c, size / 2, size / 2 + 1, size * 0.42, PINK)
    return c


def particle_sparkle(size=16):
    c = Canvas(size, size)
    stamp_sparkle(c, size / 2, size / 2, size * 0.46, BABYBLUE)
    return c


def particle_paw(size=16):
    c = Canvas(size, size)
    icon = Canvas(32, 32)
    icons.paw(icon)
    scale = size / 32
    for y in range(size):
        for x in range(size):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                c.px[y, x] = [_rgb(COTTON)[0], _rgb(COTTON)[1], _rgb(COTTON)[2], a]
    return c


def particle_star(size=16):
    c = Canvas(size, size)
    icon = Canvas(32, 32)
    icons.star(icon)
    scale = size / 32
    for y in range(size):
        for x in range(size):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                c.px[y, x] = [_rgb(LAVENDER)[0], _rgb(LAVENDER)[1], _rgb(LAVENDER)[2], a]
    return c


def gradient_pink_blue(width=64, height=64):
    c = Canvas(width, height)
    c.fill_rect_gradient(0, 0, width, height, _rgb(PINK), _rgb(BABYBLUE))
    return c


def glow_soft(size=64):
    c = Canvas(size, size)
    c.fill_circle(size / 2, size / 2, size * 0.32, _rgb(PINK), 1.0)
    c.blur_self(9)
    return c


def bg_pattern(width=64, height=64):
    c = Canvas(width, height)
    c.fill_rect(0, 0, width, height, _rgb(MIDNIGHT), alpha=1.0)
    stamp_sparkle(c, 16, 16, 4, LAVENDER, 0.35)
    stamp_paw_soft(c, 46, 44, 7)
    stamp_sparkle(c, 48, 14, 3, BABYBLUE, 0.3)
    return c


def stamp_paw_soft(c, cx, cy, size):
    icon = Canvas(32, 32)
    icons.paw(icon)
    scale = (size * 2) / 32
    for y in range(int(size * 2)):
        for x in range(int(size * 2)):
            a = icon.px[int(y / scale), int(x / scale), 3] * 0.28
            if a > 0:
                px, py = int(cx - size + x), int(cy - size + y)
                if 0 <= px < c.w and 0 <= py < c.h:
                    c.px[py, px] = [_rgb(LAVENDER)[0], _rgb(LAVENDER)[1], _rgb(LAVENDER)[2], a]


def hud_frame(width=180, height=56):
    c = Canvas(width, height)
    c.fill_rect(0, 0, width, height, _rgb(MIDNIGHT2), radius=6, alpha=0.85)
    c.stroke_rect(1, 1, width - 2, height - 2, _rgb(PINK), 2, radius=6, alpha=0.9)
    stamp_ears(c, 22, 0, 12, PINK, 0.9)
    stamp_sparkle(c, width - 14, 10, 4, BABYBLUE, 0.8)
    return c


def health_bar(width=100, height=8):
    c = Canvas(width, height)
    c.fill_rect(0, 0, width, height, _rgb(0x000000), radius=height / 2, alpha=0.55)
    c.fill_rect_gradient(1, 1, width - 2, height - 2, _rgb(PINK), _rgb(LAVENDER), radius=(height - 2) / 2)
    return c


def armor_bar(width=100, height=8):
    c = Canvas(width, height)
    c.fill_rect(0, 0, width, height, _rgb(0x000000), radius=height / 2, alpha=0.55)
    c.fill_rect_gradient(1, 1, width - 2, height - 2, _rgb(BABYBLUE), _rgb(CYAN), radius=(height - 2) / 2)
    return c


def totem_indicator(size=20):
    c = Canvas(size, size)
    icon = Canvas(32, 32)
    icons.totem(icon)
    scale = size / 32
    for y in range(size):
        for x in range(size):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                c.px[y, x] = [_rgb(LAVENDER)[0], _rgb(LAVENDER)[1], _rgb(LAVENDER)[2], a]
    return c


def crystal_marker(size=20):
    c = Canvas(size, size)
    icon = Canvas(32, 32)
    icons.crystal(icon)
    scale = size / 32
    for y in range(size):
        for x in range(size):
            a = icon.px[int(y / scale), int(x / scale), 3]
            if a > 0:
                col = lerp_rgb(_rgb(PINK), _rgb(CYAN), y / size)
                c.px[y, x] = [col[0], col[1], col[2], a]
    return c


def marker_ring(size=24, color=PINK):
    c = Canvas(size, size)
    c.stroke_circle(size / 2, size / 2, size * 0.34, _rgb(color), 2.0, 0.9)
    c.fill_circle(size / 2, size / 2, size * 0.12, _rgb(color), 0.95)
    return c


def notification_badge(size=16, color=PINK):
    c = Canvas(size, size)
    c.fill_circle(size / 2, size / 2, size * 0.42, _rgb(color), 1.0)
    c.stroke_circle(size / 2, size / 2, size * 0.42, _rgb(WHITE), 1.5, 0.9)
    return c


def fps_ping_icon(kind, size=14):
    c = Canvas(size, size)
    col = _rgb(BABYBLUE if kind == "ping" else PINK)
    if kind == "fps":
        for i, h in enumerate([0.35, 0.6, 0.85]):
            c.fill_rect(2 + i * 4, size - 2 - h * (size - 4), 2.6, h * (size - 4), col)
    else:
        for i, r in enumerate([3.0, 5.0, 7.0]):
            c.stroke_circle(size * 0.3, size * 0.7, r, col, 1.6, 0.9 - i * 0.2)
        c.fill_circle(size * 0.3, size * 0.7, 1.6, col)
    return c


def _tile(c, x, y, w, h, color, radius=0.0):
    """Paint one flat skin-texture cell (no anti-aliasing, so UV seams stay crisp)."""
    c.fill_rect(x, y, w, h, _rgb(color), radius)


def player_accessories_texture(width=64, height=64):
    """The 64x64 skin-style texture for the cosmetic player accessories.

    Layout (matches CatCosmeticsModel's UVs). Each cell is sized to the full texture
    region a cuboid consumes, which is 2*(depth+width) wide and depth+height tall, so no
    face samples transparent texels:
      (0,0)  ear outer 8x4     (10,0) ear inner 5x3
      (0,6)  bow lobes 7x3     (10,6) bow knot 5x3
      (0,12) halo seg  6x2
    The whole sheet is first washed in cotton-candy pink so any face not otherwise painted
    still reads as pastel. All artwork is original to LOGVEX CRYSTAL.
    """
    c = Canvas(width, height)
    c.fill_rect(0, 0, width, height, _rgb(COTTON))
    # Ear outer: pink with a slightly deeper base band.
    _tile(c, 0, 0, 8, 4, COTTON)
    _tile(c, 0, 2, 8, 2, PINK)
    # Ear inner: soft lavender.
    _tile(c, 10, 0, 5, 3, LAVENDER)
    _tile(c, 10, 1, 5, 2, LILAC)
    # Bow lobes: baby blue with a cyan edge.
    _tile(c, 0, 6, 7, 3, BABYBLUE)
    _tile(c, 0, 8, 7, 1, CYAN)
    # Bow knot: lilac.
    _tile(c, 10, 6, 5, 3, LILAC)
    # Halo segment: lavender fading to cyan.
    _tile(c, 0, 12, 6, 2, LAVENDER)
    _tile(c, 0, 13, 6, 1, CYAN)
    return c
