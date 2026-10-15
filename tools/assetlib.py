"""Original raster/vector primitives for the LOGVEX CRYSTAL asset pack.

Pure-stdlib (zlib + struct + numpy). No third-party imaging library is required, so the
asset pack can be regenerated on any machine with Python and numpy alone.

All artwork produced here is original to LOGVEX CRYSTAL.
"""

from __future__ import annotations

import math
import struct
import zlib

import numpy as np


# --------------------------------------------------------------------------- canvas

class Canvas:
    """A straight-alpha RGBA canvas (float 0..1, shape H x W x 4)."""

    def __init__(self, w: int, h: int):
        self.w = w
        self.h = h
        self.px = np.zeros((h, w, 4), dtype=np.float64)

    def copy(self) -> "Canvas":
        c = Canvas(self.w, self.h)
        c.px = self.px.copy()
        return c

    def clear(self) -> "Canvas":
        self.px[:] = 0
        return self

    # -- compositing -------------------------------------------------------

    def _blend(self, mask, color, alpha):
        """Source-over composite `color` where `mask` (HxW, 0..1) is non-zero."""
        r, g, b = color
        a = mask * alpha
        src = np.empty_like(self.px)
        src[..., 0] = r
        src[..., 1] = g
        src[..., 2] = b
        src[..., 3] = a
        dst = self.px
        out_a = src[..., 3] + dst[..., 3] * (1 - src[..., 3])
        safe = np.where(out_a > 0, out_a, 1.0)
        for i in range(3):
            out_c = (src[..., i] * src[..., 3] + dst[..., i] * dst[..., 3] * (1 - src[..., 3])) / safe
            dst[..., i] = np.where(out_a > 0, out_c, dst[..., i])
        dst[..., 3] = out_a

    # -- shape masks -------------------------------------------------------

    def _grid(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        return xs.astype(np.float64), ys.astype(np.float64)

    def rect_mask(self, x, y, w, h, radius=0.0):
        xs, ys = self._grid()
        x0, y0, x1, y1 = x, y, x + w, y + h
        inside = (xs >= x0) & (xs <= x1) & (ys >= y0) & (ys <= y1)
        if radius <= 0:
            return inside.astype(np.float64)
        cx0, cy0, cx1, cy1 = x0 + radius, y0 + radius, x1 - radius, y1 - radius
        dist = np.zeros_like(xs)
        left = xs < cx0
        right = xs > cx1
        top = ys < cy0
        bottom = ys > cy1
        corner = (left | right) & (top | bottom)
        ccx = np.where(left, cx0, cx1)
        ccy = np.where(top, cy0, cy1)
        dist = np.sqrt((xs - ccx) ** 2 + (ys - ccy) ** 2)
        rounded = inside & (~corner | (dist <= radius))
        # anti-alias the rounded edge by one pixel
        edge = np.clip(radius + 0.5 - dist, 0.0, 1.0)
        return np.where(corner, np.where(inside, edge, 0.0), inside.astype(np.float64))

    def circle_mask(self, cx, cy, r, aa=True):
        xs, ys = self._grid()
        d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
        if aa:
            return np.clip(r + 0.5 - d, 0.0, 1.0)
        return (d <= r).astype(np.float64)

    def ellipse_mask(self, cx, cy, rx, ry):
        xs, ys = self._grid()
        d = np.sqrt(((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2)
        return np.clip((1.0 - d) * min(rx, ry) + 0.5, 0.0, 1.0)

    def poly_mask(self, points):
        """Scanline point-in-polygon with a 1px anti-aliased edge (even-odd rule)."""
        xs, ys = self._grid()
        inside = np.zeros((self.h, self.w), dtype=np.float64)
        n = len(points)
        for i in range(n):
            x1, y1 = points[i]
            x2, y2 = points[(i + 1) % n]
            if y1 == y2:
                continue
            cond = ((ys >= np.minimum(y1, y2)) & (ys < np.maximum(y1, y2)))
            t = (ys - y1) / (y2 - y1)
            xint = x1 + t * (x2 - x1)
            inside = np.where(cond & (xs >= xint), 1.0 - inside, inside)
        return inside

    def line_mask(self, x1, y1, x2, y2, width):
        xs, ys = self._grid()
        dx, dy = x2 - x1, y2 - y1
        length2 = dx * dx + dy * dy
        if length2 == 0:
            return self.circle_mask(x1, y1, width / 2)
        t = np.clip(((xs - x1) * dx + (ys - y1) * dy) / length2, 0.0, 1.0)
        px, py = x1 + t * dx, y1 + t * dy
        d = np.sqrt((xs - px) ** 2 + (ys - py) ** 2)
        return np.clip(width / 2 + 0.5 - d, 0.0, 1.0)

    # -- fills -------------------------------------------------------------

    def fill(self, mask, color, alpha=1.0):
        self._blend(mask, color, alpha)
        return self

    def fill_rect(self, x, y, w, h, color, radius=0.0, alpha=1.0):
        return self.fill(self.rect_mask(x, y, w, h, radius), color, alpha)

    def stroke_rect(self, x, y, w, h, color, width=1.0, radius=0.0, alpha=1.0):
        outer = self.rect_mask(x, y, w, h, radius)
        inner = self.rect_mask(x + width, y + width, w - 2 * width, h - 2 * width,
                               max(0.0, radius - width))
        return self.fill(np.clip(outer - inner, 0.0, 1.0), color, alpha)

    def fill_poly(self, points, color, alpha=1.0):
        return self.fill(self.poly_mask(points), color, alpha)

    def stroke_line(self, x1, y1, x2, y2, color, width=1.0, alpha=1.0):
        return self.fill(self.line_mask(x1, y1, x2, y2, width), color, alpha)

    def fill_circle(self, cx, cy, r, color, alpha=1.0):
        return self.fill(self.circle_mask(cx, cy, r), color, alpha)

    def fill_ellipse(self, cx, cy, rx, ry, color, alpha=1.0):
        return self.fill(self.ellipse_mask(cx, cy, rx, ry), color, alpha)

    def stroke_circle(self, cx, cy, r, color, width=1.0, alpha=1.0):
        outer = self.circle_mask(cx, cy, r + width / 2)
        inner = self.circle_mask(cx, cy, max(0.0, r - width / 2))
        return self.fill(np.clip(outer - inner, 0.0, 1.0), color, alpha)

    # -- gradients ---------------------------------------------------------

    def fill_rect_gradient(self, x, y, w, h, top, bottom, radius=0.0, horizontal=False):
        mask = self.rect_mask(x, y, w, h, radius)
        xs, ys = self._grid()
        if horizontal:
            t = np.clip((xs - x) / max(1.0, w), 0.0, 1.0)
        else:
            t = np.clip((ys - y) / max(1.0, h), 0.0, 1.0)
        grad = np.zeros_like(self.px)
        for i in range(3):
            grad[..., i] = top[i] * (1 - t) + bottom[i] * t
        grad[..., 3] = 1.0
        self._blend(mask, (0, 0, 0), 0)  # no-op keeps dtype stable
        # composite gradient through the mask
        src = grad
        a = mask
        dst = self.px
        out_a = a + dst[..., 3] * (1 - a)
        safe = np.where(out_a > 0, out_a, 1.0)
        for i in range(3):
            out_c = (src[..., i] * a + dst[..., i] * dst[..., 3] * (1 - a)) / safe
            dst[..., i] = np.where(out_a > 0, out_c, dst[..., i])
        dst[..., 3] = out_a
        return self

    def glow(self, mask, color, radius=6.0, strength=0.8):
        """Adds a soft outer glow around `mask` using a separable box blur."""
        blur = _box_blur(mask, radius)
        ring = np.clip(blur - mask, 0.0, 1.0) * strength
        self._blend(ring, color, 1.0)
        return self

    def blur_self(self, radius):
        for i in range(4):
            self.px[..., i] = _box_blur(self.px[..., i], radius)
        return self

    def shift(self, dx, dy):
        out = Canvas(self.w, self.h)
        x0s, x1s = max(0, dx), min(self.w, self.w + dx)
        y0s, y1s = max(0, dy), min(self.h, self.h + dy)
        out.px[y0s:y1s, x0s:x1s] = self.px[y0s - dy:y1s - dy, x0s - dx:x1s - dx]
        self.px = out.px
        return self

    def paste(self, other: "Canvas", ox: int, oy: int):
        """Source-over composites `other` at (ox, oy)."""
        h = min(other.h, self.h - oy)
        w = min(other.w, self.w - ox)
        if h <= 0 or w <= 0:
            return self
        src = other.px[:h, :w]
        dst = self.px[oy:oy + h, ox:ox + w]
        out_a = src[..., 3] + dst[..., 3] * (1 - src[..., 3])
        safe = np.where(out_a > 0, out_a, 1.0)
        for i in range(3):
            out_c = (src[..., i] * src[..., 3] + dst[..., i] * dst[..., 3] * (1 - src[..., 3])) / safe
            dst[..., i] = np.where(out_a > 0, out_c, dst[..., i])
        dst[..., 3] = out_a
        return self

    # -- output ------------------------------------------------------------

    def to_png(self, path, premultiplied=False):
        arr = np.clip(self.px, 0.0, 1.0)
        a = arr[..., 3]
        rgb = arr[..., :3]
        if premultiplied:
            rgb = rgb * a[..., None]
        out = np.empty((self.h, self.w, 4), dtype=np.uint8)
        out[..., 0] = np.round(rgb[..., 0] * 255)
        out[..., 1] = np.round(rgb[..., 1] * 255)
        out[..., 2] = np.round(rgb[..., 2] * 255)
        out[..., 3] = np.round(a * 255)
        write_png(path, out)


# --------------------------------------------------------------------------- helpers

def hex_rgb(value: int):
    return ((value >> 16) & 0xFF) / 255.0, ((value >> 8) & 0xFF) / 255.0, (value & 0xFF) / 255.0


def lerp_rgb(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def _box_blur(channel, radius):
    if radius <= 0:
        return channel
    k = int(math.ceil(radius))
    # horizontal
    pad = np.pad(channel, ((0, 0), (k, k)), mode="edge")
    cs = np.cumsum(pad, axis=1)
    cs = np.pad(cs, ((0, 0), (1, 0)), mode="constant")
    w = 2 * k + 1
    h = (cs[:, w:] - cs[:, :-w]) / w
    # vertical
    pad = np.pad(h, ((k, k), (0, 0)), mode="edge")
    cs = np.cumsum(pad, axis=0)
    cs = np.pad(cs, ((1, 0), (0, 0)), mode="constant")
    v = (cs[w:, :] - cs[:-w, :]) / w
    return v


def write_png(path, rgba_uint8):
    h, w = rgba_uint8.shape[0], rgba_uint8.shape[1]
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type 0 (None)
        raw.extend(rgba_uint8[y].tobytes())

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", ihdr)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def write_svg(path, body, size):
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
        f'width="{size}" height="{size}" fill="none">\n' + body + "\n</svg>\n"
    )
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
