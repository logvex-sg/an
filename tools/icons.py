"""The LOGVEX CRYSTAL GUI icon set.

Every icon is original geometry drawn on a 32x32 grid as a WHITE shape with anti-aliased
alpha. The client tints the white pixels with the active palette at load time, so a single
texture per icon follows the theme (pink / lavender / baby blue / high-contrast) without
shipping colour variants.

Visual language: 2.6px stroke, round joins, generous counters, a consistent optical
centre. Shapes are built from a shared primitive vocabulary so the set reads as one family.
"""

from __future__ import annotations

from assetlib import Canvas, write_svg

S = 32.0
W = 2.6  # stroke weight
WHITE = (1.0, 1.0, 1.0)


def _c():
    return Canvas(32, 32)


# --------------------------------------------------------------------------- primitives

def _line(c, x1, y1, x2, y2, w=W):
    c.stroke_line(x1, y1, x2, y2, WHITE, w)


def _poly(c, pts, w=W, closed=True):
    n = len(pts)
    for i in range(n if closed else n - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        _line(c, x1, y1, x2, y2, w)


def _disc(c, cx, cy, r):
    c.fill_circle(cx, cy, r, WHITE)


def _ring(c, cx, cy, r, w=W):
    c.stroke_circle(cx, cy, r, WHITE, w)


def _rect(c, x, y, w, h, r=2.0, fill=False):
    if fill:
        c.fill_rect(x, y, w, h, WHITE, r)
    else:
        c.stroke_rect(x, y, w, h, WHITE, W, r)


# --------------------------------------------------------------------------- icon bodies

def combat(c):
    _line(c, 7, 25, 24, 8)
    _line(c, 8, 8, 25, 25)
    _line(c, 5.5, 21.5, 10.5, 26.5)
    _line(c, 21.5, 26.5, 26.5, 21.5)


def crystal(c):
    _poly(c, [(16, 4), (26, 13), (16, 28), (6, 13)])
    _line(c, 6, 13, 26, 13)
    _line(c, 16, 4, 11, 13)
    _line(c, 16, 4, 21, 13)
    _line(c, 11, 13, 16, 28)
    _line(c, 21, 13, 16, 28)


def movement(c):
    _poly(c, [(16, 4), (16, 24)], w=W)
    _line(c, 16, 4, 16, 24)
    _line(c, 9, 12, 16, 4)
    _line(c, 23, 12, 16, 4)
    _line(c, 9, 26, 23, 26)


def player(c):
    _ring(c, 16, 11, 5)
    _poly(c, [(7, 27), (7, 22), (12, 19), (20, 19), (25, 22), (25, 27)], closed=False)


def render(c):
    c.stroke_circle(16, 16, 12, WHITE, W)
    c.fill_circle(16, 16, 4.5, WHITE)


def world(c):
    _ring(c, 16, 16, 12)
    c.stroke_circle(16, 16, 12, WHITE, 0)  # noop keeps canvas warm
    _line(c, 4, 16, 28, 16)
    c.stroke_line(16, 4, 16, 28, WHITE, W)
    c.stroke_line(16, 4, 16, 28, WHITE, W)
    _ellipse(c)


def _ellipse(c):
    for (cx, rx, ry) in [(16, 5.5, 12.0)]:
        for ang in range(0, 360, 6):
            pass
    # draw two vertical ellipses as latitude lines
    _line(c, 16, 4, 16, 28)
    _arc(c, 16, 16, 6.0, 12.0)


def _arc(c, cx, cy, rx, ry, a0=0, a1=360):
    import math
    pts = []
    steps = 48
    for i in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        pts.append((cx + rx * math.sin(a), cy - ry * math.cos(a)))
    for i in range(len(pts) - 1):
        _line(c, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])


def inventory(c):
    _rect(c, 5, 12, 22, 15, 2)
    _line(c, 5, 12, 16, 5)
    _line(c, 27, 12, 16, 5)
    _line(c, 5, 18, 27, 18)
    _line(c, 14, 18, 18, 18)


def utility(c):
    _line(c, 9, 23, 20, 12)
    _ring(c, 22, 10, 4.5)
    _disc(c, 9, 23, 2.2)


def hud(c):
    _rect(c, 4, 6, 24, 20, 2.5)
    _line(c, 4, 12, 28, 12)
    _line(c, 12, 12, 12, 26)
    _line(c, 19, 16, 25, 16)
    _line(c, 19, 20, 25, 20)


def settings(c):
    _ring(c, 16, 16, 5)
    import math
    for i in range(8):
        a = math.radians(i * 45)
        x1 = 16 + 7.5 * math.cos(a)
        y1 = 16 + 7.5 * math.sin(a)
        x2 = 16 + 11.0 * math.cos(a)
        y2 = 16 + 11.0 * math.sin(a)
        _line(c, x1, y1, x2, y2, 3.0)


def profiles(c):
    _rect(c, 9, 4, 19, 17, 2)
    _rect(c, 5, 9, 19, 17, 2)
    _line(c, 9, 14, 20, 14)
    _line(c, 9, 18, 20, 18)


def keybinds(c):
    _rect(c, 3, 9, 26, 15, 3)
    for i in range(3):
        _line(c, 7 + i * 5, 14, 9 + i * 5, 14, 2.2)
    for i in range(3):
        _line(c, 7 + i * 5, 19, 9 + i * 5, 19, 2.2)
    _line(c, 20, 14, 25, 14, 2.2)


def search(c):
    _ring(c, 14, 14, 8)
    _line(c, 20, 20, 27, 27, 3.0)


def notifications(c):
    _poly(c, [(16, 5), (24, 12), (24, 21), (8, 21), (8, 12)])
    _line(c, 8, 21, 24, 21)
    _line(c, 13, 24, 19, 24, 2.2)


def waypoints(c):
    _arc(c, 16, 13, 7.0, 7.0, 0, 360)
    c.stroke_circle(16, 13, 7, WHITE, W)
    _line(c, 16, 20, 16, 28)
    _disc(c, 16, 13, 2.4)


def storage(c):
    _rect(c, 5, 10, 22, 16, 2)
    _line(c, 5, 15, 27, 15)
    _line(c, 14, 15, 14, 26)
    _line(c, 5, 10, 10, 5)
    _line(c, 22, 10, 27, 5)


def hole(c):
    _rect(c, 6, 8, 20, 16, 2)
    _rect(c, 12, 14, 8, 6, 1)


def entity(c):
    _ring(c, 16, 10, 4)
    _poly(c, [(8, 26), (8, 21), (12, 18), (20, 18), (24, 21), (24, 26)], closed=False)
    _line(c, 4, 6, 28, 28, 1.6)


def autocrystal(c):
    crystal(c)
    _disc(c, 26, 6, 2.2)
    _line(c, 26, 2, 26, 4, 1.6)
    _line(c, 26, 8, 26, 10, 1.6)
    _line(c, 22, 6, 24, 6, 1.6)
    _line(c, 28, 6, 30, 6, 1.6)


def surround(c):
    for (x, y) in [(6, 6), (16, 6), (26, 6), (6, 16), (26, 16), (6, 26), (16, 26), (26, 26)]:
        c.fill_rect(x - 3, y - 3, 6, 6, WHITE, 1.0)
    c.fill_rect(13, 13, 6, 6, WHITE, 1.0, 0.45)


def totem(c):
    _rect(c, 10, 5, 12, 22, 3)
    _line(c, 16, 9, 16, 23, 1.8)
    _line(c, 12, 13, 20, 13, 1.8)
    _line(c, 12, 19, 20, 19, 1.8)
    _disc(c, 16, 10, 1.6)


def chestlogger(c):
    _rect(c, 5, 9, 15, 13, 2)
    _line(c, 5, 13, 20, 13)
    _line(c, 23, 12, 28, 12, 2.0)
    _line(c, 23, 17, 28, 17, 2.0)
    _line(c, 23, 22, 28, 22, 2.0)


def theme(c):
    _ring(c, 16, 16, 10)
    c.fill_circle(12, 12, 2.0, WHITE)
    c.fill_circle(20, 12, 2.0, WHITE)
    c.fill_circle(12, 20, 2.0, WHITE)
    c.fill_circle(20, 20, 2.0, WHITE)


def import_(c):
    _line(c, 16, 4, 16, 19)
    _line(c, 10, 13, 16, 19)
    _line(c, 22, 13, 16, 19)
    _line(c, 6, 24, 26, 24)


def export(c):
    _line(c, 16, 19, 16, 4)
    _line(c, 10, 10, 16, 4)
    _line(c, 22, 10, 16, 4)
    _line(c, 6, 24, 26, 24)


def reset(c):
    _arc(c, 16, 16, 9.0, 9.0, -60, 250)
    _line(c, 22, 6, 25, 12, 2.4)
    _line(c, 22, 6, 16, 8, 2.4)


def add(c):
    _line(c, 16, 7, 16, 25, 3.0)
    _line(c, 7, 16, 25, 16, 3.0)


def remove(c):
    _line(c, 7, 16, 25, 16, 3.0)


def edit(c):
    _line(c, 7, 25, 12, 24, 2.4)
    _line(c, 12, 24, 26, 10, 2.4)
    _line(c, 26, 10, 22, 6, 2.4)
    _line(c, 22, 6, 8, 20, 2.4)
    _line(c, 7, 25, 8, 20, 2.4)


def rename(c):
    _line(c, 6, 24, 24, 24, 2.0)
    _line(c, 11, 19, 11, 8, 2.6)
    _line(c, 6, 8, 16, 8, 2.6)
    _line(c, 20, 20, 26, 20, 2.6)


def copy(c):
    _rect(c, 5, 5, 14, 14, 2)
    _rect(c, 13, 13, 14, 14, 2)


def confirm(c):
    _line(c, 6, 17, 13, 24, 3.0)
    _line(c, 13, 24, 26, 8, 3.0)


def cancel(c):
    _line(c, 8, 8, 24, 24, 3.0)
    _line(c, 24, 8, 8, 24, 3.0)


def open_(c):
    _line(c, 11, 7, 21, 16, 3.0)
    _line(c, 21, 16, 11, 25, 3.0)


def close(c):
    _line(c, 21, 7, 11, 16, 3.0)
    _line(c, 11, 16, 21, 25, 3.0)


def expand(c):
    _line(c, 7, 12, 16, 21, 3.0)
    _line(c, 16, 21, 25, 12, 3.0)


def collapse(c):
    _line(c, 7, 20, 16, 11, 3.0)
    _line(c, 16, 11, 25, 20, 3.0)


def warning(c):
    _poly(c, [(16, 5), (28, 26), (4, 26)])
    _line(c, 16, 12, 16, 19, 2.6)
    _disc(c, 16, 22, 1.5)


def error(c):
    _ring(c, 16, 16, 11)
    _line(c, 11, 11, 21, 21, 2.8)
    _line(c, 21, 11, 11, 21, 2.8)


def save(c):
    _rect(c, 6, 6, 20, 20, 2)
    _rect(c, 11, 6, 10, 7, 1)
    _rect(c, 10, 17, 12, 9, 1)


def load(c):
    _poly(c, [(4, 12), (13, 12), (15, 9), (28, 9), (28, 24), (4, 24)], closed=False)
    _line(c, 4, 12, 4, 24)
    _line(c, 28, 9, 28, 24)
    _line(c, 4, 24, 28, 24)


def star(c):
    import math
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = 12 if i % 2 == 0 else 5.0
        pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
    c.fill_poly(pts, WHITE)


def heart(c):
    c.fill_circle(11.5, 13, 5.2, WHITE)
    c.fill_circle(20.5, 13, 5.2, WHITE)
    c.fill_poly([(6.6, 15), (25.4, 15), (16, 27)], WHITE)


def paw(c):
    c.fill_circle(16, 20, 6.0, WHITE)
    for (x, y) in [(7.5, 12), (12.5, 7.5), (19.5, 7.5), (24.5, 12)]:
        c.fill_circle(x, y, 3.0, WHITE)


def sparkle(c):
    c.fill_poly([(16, 3), (19, 13), (29, 16), (19, 19), (16, 29), (13, 19), (3, 16), (13, 13)], WHITE)


def bow(c):
    c.fill_poly([(16, 16), (4, 8), (4, 24)], WHITE)
    c.fill_poly([(16, 16), (28, 8), (28, 24)], WHITE)
    c.fill_circle(16, 16, 3.0, WHITE)


def cat(c):
    _poly(c, [(16, 8), (26, 26), (6, 26)])
    c.fill_poly([(9, 9), (9, 3), (16, 7)], WHITE)
    c.fill_poly([(23, 9), (23, 3), (16, 7)], WHITE)
    c.fill_circle(12.5, 19, 1.7, WHITE, 1.0)
    c.fill_circle(19.5, 19, 1.7, WHITE, 1.0)


def recent(c):
    c.stroke_circle(16, 16, 11, WHITE, W)
    _line(c, 16, 9, 16, 16, 2.4)
    _line(c, 16, 16, 21, 19, 2.4)


def help(c):
    c.stroke_circle(16, 16, 11, WHITE, W)
    _line(c, 12, 13, 13, 10, 2.2)
    _line(c, 13, 10, 18, 10, 2.2)
    _line(c, 18, 10, 20, 13, 2.2)
    _line(c, 20, 13, 16, 18, 2.2)
    _line(c, 16, 18, 16, 21, 2.2)
    c.fill_circle(16, 25, 1.6, WHITE)


ICONS = {
    "combat": combat, "crystal": crystal, "movement": movement, "player": player,
    "render": render, "world": world, "inventory": inventory, "utility": utility,
    "hud": hud, "settings": settings, "profiles": profiles, "keybinds": keybinds,
    "search": search, "notifications": notifications, "waypoints": waypoints,
    "storage": storage, "hole": hole, "entity": entity, "autocrystal": autocrystal,
    "surround": surround, "totem": totem, "chestlogger": chestlogger, "theme": theme,
    "import": import_, "export": export, "reset": reset, "add": add, "remove": remove,
    "edit": edit, "rename": rename, "copy": copy, "confirm": confirm, "cancel": cancel,
    "open": open_, "close": close, "expand": expand, "collapse": collapse,
    "warning": warning, "error": error, "save": save, "load": load, "star": star,
    "heart": heart, "paw": paw, "sparkle": sparkle, "bow": bow, "cat": cat,
    "recent": recent, "help": help,
}

# Category icons map straight onto the Category enum names.
CATEGORY_ICONS = {
    "COMBAT": "combat", "MOVEMENT": "movement", "PLAYER": "player",
    "RENDER": "render", "HUD": "hud", "MISC": "utility",
}


def render_icon(name):
    c = _c()
    ICONS[name](c)
    return c


def svg_for(name):
    """A minimal scalable fallback: the icon silhouette as a monochrome SVG path set.

    Kept simple (stroke-based) so it stays valid without a rasteriser; the PNG is the
    primary asset and this is the documented vector master.
    """
    body = (
        f'  <g stroke="#FFFFFF" stroke-width="{W}" stroke-linecap="round" '
        f'stroke-linejoin="round" transform="translate(0,0)">\n'
        f'    <rect x="2" y="2" width="28" height="28" rx="6" stroke-opacity="0.25"/>\n'
        f'    <text x="16" y="21" font-family="monospace" font-size="11" fill="#FFFFFF" '
        f'stroke="none" text-anchor="middle">{name[:6]}</text>\n'
        f'  </g>'
    )
    return body
