#!/usr/bin/env python3
"""Generate the LOGVEX CRYSTAL asset pack.

    python3 tools/gen_assets.py

Writes original artwork into src/main/resources/assets/logvex-crystal/ and a contact
sheet into docs/. Deterministic: re-running produces byte-identical PNGs. Requires only
Python 3 and numpy. All artwork is original to LOGVEX CRYSTAL.
"""

from __future__ import annotations

import os

from assetlib import Canvas, hex_rgb, write_svg
import branding
import icons

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "logvex-crystal")
DOCS = os.path.join(ROOT, "docs")

# (relative path, canvas, premultiplied) entries are collected so the contact sheet and
# manifest are generated from exactly what is written.
written: list[tuple[str, Canvas]] = []


def out(rel: str, canvas: Canvas, premultiplied=False):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    canvas.to_png(path, premultiplied=premultiplied)
    written.append((rel, canvas))


def gen_icons():
    for name in icons.ICONS:
        out(f"textures/gui/icons/{name}.png", icons.render_icon(name))
        # vector master for the interface graphics
        svg_path = os.path.join(ASSETS, "textures", "svg", f"{name}.svg")
        os.makedirs(os.path.dirname(svg_path), exist_ok=True)
        write_svg(svg_path, icons.svg_for(name), 32)


def gen_branding():
    out("icon.png", branding.app_icon(128))
    out("textures/gui/branding/logo.png", branding.logo(384, 128))
    out("textures/gui/branding/app_icon.png", branding.app_icon(128))
    out("textures/gui/branding/header_logo.png", branding.header_logo(220, 40))
    out("textures/gui/branding/loading.png", branding.loading_art(320, 180))
    out("textures/gui/branding/empty_state.png", branding.empty_state(160, 120))
    out("textures/gui/branding/watermark_cat.png", branding.watermark_cat(96, 48))
    for kind in ("success", "warn", "error", "info"):
        out(f"textures/gui/branding/status_{kind}.png", branding.status_icon(kind, 16))


def gen_decor():
    out("textures/gui/decor/particle_heart.png", branding.particle_heart(16))
    out("textures/gui/decor/particle_sparkle.png", branding.particle_sparkle(16))
    out("textures/gui/decor/particle_paw.png", branding.particle_paw(16))
    out("textures/gui/decor/particle_star.png", branding.particle_star(16))
    out("textures/gui/decor/gradient_pink_blue.png", branding.gradient_pink_blue(64, 64))
    out("textures/gui/decor/glow_soft.png", branding.glow_soft(64))
    out("textures/gui/decor/bg_pattern.png", branding.bg_pattern(64, 64))


def gen_hud():
    out("textures/gui/hud/frame.png", branding.hud_frame(180, 56))
    out("textures/gui/hud/health_bar.png", branding.health_bar(100, 8))
    out("textures/gui/hud/armor_bar.png", branding.armor_bar(100, 8))
    out("textures/gui/hud/totem.png", branding.totem_indicator(20))
    out("textures/gui/hud/crystal_marker.png", branding.crystal_marker(20))
    out("textures/gui/hud/marker_ring.png", branding.marker_ring(24, branding.PINK))
    out("textures/gui/hud/marker_ring_cyan.png", branding.marker_ring(24, branding.CYAN))
    out("textures/gui/hud/notification_badge.png", branding.notification_badge(16))
    out("textures/gui/hud/fps.png", branding.fps_ping_icon("fps", 14))
    out("textures/gui/hud/ping.png", branding.fps_ping_icon("ping", 14))


def gen_palette_swatch():
    c = Canvas(360, 40)
    x = 0
    for _, val in branding.PALETTE_ORDER:
        c.fill_rect(x, 0, 40, 40, hex_rgb(val))
        x += 40
    return c


def gen_cosmetics():
    out("textures/entity/cosmetic/player_accessories.png", branding.player_accessories_texture(64, 64))


def contact_sheet():
    """A review-only sheet: every asset on a midnight background with labels."""
    cols = 8
    cell = 96
    rows = (len(written) + cols - 1) // cols
    sheet = Canvas(cols * cell, rows * cell)
    sheet.fill_rect(0, 0, sheet.w, sheet.h, hex_rgb(branding.MIDNIGHT))
    for i, (rel, canvas) in enumerate(written):
        cx = (i % cols) * cell
        cy = (i // cols) * cell
        sheet.fill_rect(cx + 1, cy + 1, cell - 2, cell - 2, hex_rgb(branding.MIDNIGHT2), radius=6)
        # fit the asset into the cell (nearest-neighbour)
        s = min((cell - 20) / canvas.w, (cell - 20) / canvas.h, 2.0)
        dw, dh = max(1, int(canvas.w * s)), max(1, int(canvas.h * s))
        ox, oy = cx + (cell - dw) // 2, cy + (cell - dh) // 2
        for y in range(dh):
            for x in range(dw):
                px = canvas.px[int(y / s), int(x / s)]
                if px[3] > 0:
                    sheet.px[oy + y, ox + x] = px
    return sheet


def main():
    gen_icons()
    gen_branding()
    gen_decor()
    gen_hud()
    gen_cosmetics()
    out("textures/gui/branding/palette_swatch.png", gen_palette_swatch())

    os.makedirs(DOCS, exist_ok=True)
    sheet = contact_sheet()
    sheet.to_png(os.path.join(DOCS, "assets-contact-sheet.png"))

    total = 0
    for rel, _ in written:
        total += os.path.getsize(os.path.join(ASSETS, rel))
    print(f"wrote {len(written)} PNG assets ({total/1024:.1f} KiB) under {ASSETS}")
    print(f"contact sheet -> docs/assets-contact-sheet.png")


if __name__ == "__main__":
    main()
