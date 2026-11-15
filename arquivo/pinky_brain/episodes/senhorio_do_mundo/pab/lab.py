"""The laboratory set: static background (cached) + animated layers.

World coordinates: the 16:9 wide shot shows x 0..1920, y 0..1080.  The set is
painted beyond that (WORLD rect) so cameras can pan and the vertical cut has
room above and below.  The mice live on top of a giant lab table whose back
edge is at y = TABLE_Y.
"""
from __future__ import annotations

import math
import random

from . import fonts  # noqa: F401  (registers fonts)
import cairo

from .anim import noise1, clamp
from .script import SET
from .draw import INK, blob_path, ellipse_path, hexc, mix, src, text_center

WORLD = (-460.0, -520.0, 2840.0, 2140.0)   # x, y, w, h
TABLE_Y = 860.0
TABLE_FRONT = 1210.0
FLOOR_Y = 960.0                              # where the characters stand

MAP_RECT = (70.0, 70.0, 830.0, 500.0)        # x, y, w, h
MON = {
    "news": (960.0, 96.0, 228.0, 176.0),
    "chart": (1206.0, 96.0, 228.0, 176.0),
    "alert": (1018.0, 300.0, 356.0, 230.0),
}
BOARD_RECT = (1480.0, 80.0, 400.0, 470.0)

WALL_TOP = hexc("#1d1f45")
WALL_BOT = hexc("#33356e")

# --------------------------------------------------------------------------
# Continents (lon, lat), deliberately simplified/cartoony
# --------------------------------------------------------------------------
CONTINENTS = {
    "namerica": [(-168, 65), (-158, 71), (-140, 70), (-120, 71), (-95, 72), (-80, 70), (-65, 61),
                 (-56, 52), (-66, 45), (-75, 38), (-81, 31), (-80, 25), (-90, 29), (-97, 26),
                 (-97, 20), (-90, 16), (-83, 9), (-78, 8), (-86, 14), (-105, 20), (-112, 28),
                 (-117, 32), (-124, 40), (-124, 48), (-132, 55), (-150, 60), (-165, 60)],
    "greenland": [(-52, 61), (-42, 61), (-22, 70), (-20, 80), (-40, 83), (-62, 80), (-70, 77), (-56, 70)],
    "samerica": [(-78, 9), (-72, 12), (-62, 10), (-50, 1), (-35, -6), (-38, -15), (-48, -26),
                 (-58, -35), (-65, -42), (-68, -54), (-74, -50), (-73, -40), (-71, -30),
                 (-70, -18), (-76, -12), (-81, -5), (-80, 1)],
    "europe": [(-10, 36), (-9, 43), (-2, 44), (-5, 48), (0, 50), (5, 53), (8, 55), (10, 58),
               (5, 61), (12, 66), (20, 70), (30, 71), (40, 67), (42, 60), (35, 50), (30, 45),
               (28, 41), (24, 38), (20, 40), (15, 38), (12, 44), (8, 44), (3, 42), (-5, 36)],
    "uk": [(-5, 50), (1, 51), (1, 53), (-2, 56), (-5, 58.5), (-6.5, 55), (-3, 54)],
    "africa": [(-17, 21), (-12, 28), (-6, 35), (10, 37), (20, 33), (32, 31), (35, 28), (43, 12),
               (51, 12), (42, 0), (40, -15), (35, -25), (28, -34), (18, -35), (13, -20),
               (9, -2), (5, 5), (-8, 5), (-15, 10)],
    "madagascar": [(44, -13), (50, -15), (47, -25), (44, -22)],
    "asia": [(28, 41), (35, 36), (36, 32), (44, 30), (50, 25), (57, 25), (60, 22), (67, 25),
             (73, 20), (77, 8), (80, 14), (88, 22), (92, 21), (98, 16), (100, 8), (104, 1),
             (106, 10), (110, 20), (118, 24), (122, 30), (121, 40), (130, 42), (140, 47),
             (142, 54), (155, 58), (162, 62), (178, 66), (178, 71), (150, 72), (130, 73),
             (110, 77), (90, 76), (70, 73), (55, 69), (42, 66), (40, 67), (42, 60), (35, 50),
             (30, 45)],
    "japan": [(130, 31), (135, 34), (140, 36), (142, 40), (141.5, 45), (139.5, 42), (136, 36.5), (131, 34)],
    "indonesia": [(95, 5), (105, -6), (115, -8), (120, -9), (114, -4), (108, -1), (100, 2)],
    "borneo": [(109, 1), (114, 4), (118, 6), (119, 0), (116, -4), (110, -3)],
    "australia": [(114, -22), (122, -18), (130, -12), (137, -12), (142, -11), (146, -19),
                  (153, -26), (150, -37), (141, -38), (135, -34), (129, -32), (115, -34)],
    "nz": [(172, -35), (178, -38), (174, -42), (168, -46), (171, -42)],
}

# crisis hot-spots on the map (lon, lat, appear-order 0..1) – generic, no real leaders
HOTSPOTS = [(-100, 40, 0.05), (10, 50, 0.12), (30, 0, 0.2), (100, 35, 0.27), (-60, -15, 0.35),
            (45, 30, 0.42), (135, -25, 0.5), (75, 55, 0.58), (-120, 55, 0.66), (20, 20, 0.72),
            (110, 15, 0.8), (-75, 5, 0.86), (60, 45, 0.92), (-40, 70, 0.97)]


def map_xy(lon, lat):
    x, y, w, h = MAP_RECT
    mx, my = x + 34, y + 40
    mw, mh = w - 68, h - 92
    return mx + (lon + 180) / 360 * mw, my + (82 - lat) / 140 * mh


# --------------------------------------------------------------------------
# Static background
# --------------------------------------------------------------------------
def draw_static(ctx):
    rnd = random.Random(7)
    wx, wy, ww, wh = WORLD

    # wall
    g = cairo.LinearGradient(0, wy, 0, TABLE_Y)
    g.add_color_stop_rgb(0, *WALL_TOP)
    g.add_color_stop_rgb(1, *WALL_BOT)
    ctx.rectangle(wx, wy, ww, TABLE_Y - wy + 2)
    ctx.set_source(g)
    ctx.fill()
    # wall tiles / panels
    ctx.set_line_width(2)
    for i in range(-3, 22):
        x = i * 140 - 20
        ctx.move_to(x, wy)
        ctx.line_to(x, TABLE_Y)
    src(ctx, (*hexc("#141533"), 0.35))
    ctx.stroke()
    for j in range(-3, 8):
        y = j * 140 + 40
        ctx.move_to(wx, y)
        ctx.line_to(wx + ww, y)
    src(ctx, (*hexc("#141533"), 0.22))
    ctx.stroke()
    # wainscot rail
    ctx.rectangle(wx, 640, ww, 18)
    src(ctx, hexc("#24264f"))
    ctx.fill()
    ctx.rectangle(wx, 658, ww, TABLE_Y - 658)
    src(ctx, hexc("#2a2b5c"))
    ctx.fill()
    for i in range(-3, 30):
        x = i * 110
        ctx.rectangle(x + 12, 676, 86, TABLE_Y - 690)
    src(ctx, (*hexc("#1a1b40"), 0.45))
    ctx.set_line_width(3)
    ctx.stroke()

    # pipes on the wall (lab feel)
    for (y, col) in ((-60, "#5d6a8a"), (-28, "#4e5a7a")):
        ctx.rectangle(wx, y, ww, 18)
        src(ctx, hexc(col))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(3)
        ctx.stroke()
        for x in range(-400, 2400, 260):
            ctx.rectangle(x, y - 4, 16, 26)
            src(ctx, hexc("#3b4563"))
            ctx.fill()

    _draw_map(ctx)
    _draw_monitor_casings(ctx)
    _draw_board(ctx)
    _draw_calendar(ctx)
    _draw_shelf_props(ctx)
    _draw_table(ctx, rnd)


def _frame(ctx, x, y, w, h, wood="#6b3f22", width=18):
    ctx.rectangle(x - width, y - width, w + 2 * width, h + 2 * width)
    src(ctx, hexc(wood))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    ctx.rectangle(x - width + 5, y - width + 5, w + 2 * width - 10, h + 2 * width - 10)
    src(ctx, (1, 0.85, 0.6, 0.18))
    ctx.set_line_width(2)
    ctx.stroke()


def _draw_map(ctx):
    x, y, w, h = MAP_RECT
    # drop shadow
    ctx.rectangle(x + 14, y + 18, w, h)
    src(ctx, (0, 0, 0.05, 0.35))
    ctx.fill()
    _frame(ctx, x, y, w, h)
    g = cairo.LinearGradient(x, y, x + w, y + h)
    g.add_color_stop_rgb(0, *hexc("#8fd0d6"))
    g.add_color_stop_rgb(1, *hexc("#5fb1c2"))
    ctx.rectangle(x, y, w, h)
    ctx.set_source(g)
    ctx.fill()
    # grid
    ctx.set_line_width(1.2)
    for lon in range(-180, 181, 30):
        a = map_xy(lon, 82)
        b = map_xy(lon, -58)
        ctx.move_to(*a)
        ctx.line_to(*b)
    for lat in range(-60, 90, 20):
        a = map_xy(-180, lat)
        b = map_xy(180, lat)
        ctx.move_to(*a)
        ctx.line_to(*b)
    src(ctx, (1, 1, 1, 0.28))
    ctx.stroke()
    # continents
    for name, pts in CONTINENTS.items():
        xy = [map_xy(*q) for q in pts]
        ctx.new_path()
        blob_path(ctx, xy, tension=0.35)
        src(ctx, hexc("#e9d9a6"))
        ctx.fill_preserve()
        src(ctx, hexc("#7a5a32"))
        ctx.set_line_width(2.5)
        ctx.stroke()
    # title strip
    ctx.rectangle(x, y + h - 46, w, 46)
    src(ctx, hexc("#f3ead0"))
    ctx.fill()
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(34)
    src(ctx, hexc("#7a1f1f"))
    text_center(ctx, SET["map_title"], x + w / 2, y + h - 23)
    # red string & pins (the "plan")
    pins = [map_xy(-100, 40), map_xy(10, 50), map_xy(100, 35), map_xy(30, 0), map_xy(135, -25)]
    ctx.move_to(*pins[0])
    for q in pins[1:]:
        ctx.line_to(*q)
    src(ctx, (0.75, 0.08, 0.1, 0.8))
    ctx.set_line_width(2.5)
    ctx.stroke()
    for q in pins:
        ctx.arc(q[0], q[1] - 3, 7, 0, 2 * math.pi)
        src(ctx, hexc("#d62828"))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(2)
        ctx.stroke()
    # big question mark scrawl
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(64)
    src(ctx, (0.7, 0.05, 0.08, 0.75))
    text_center(ctx, "?", *map_xy(-30, 25))


def _draw_monitor_casings(ctx):
    # shelf behind the monitors
    ctx.rectangle(930, 548, 530, 20)
    src(ctx, hexc("#6b3f22"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    for bx in (960, 1400):
        ctx.move_to(bx, 568)
        ctx.line_to(bx, 610)
        ctx.line_to(bx + 30, 568)
        src(ctx, hexc("#4a2c18"))
        ctx.set_line_width(8)
        ctx.stroke()
    # cables
    ctx.move_to(1080, 272)
    ctx.curve_to(1060, 420, 1000, 600, 980, 860)
    ctx.move_to(1320, 272)
    ctx.curve_to(1420, 360, 1440, 600, 1470, 860)
    src(ctx, hexc("#121226"))
    ctx.set_line_width(6)
    ctx.stroke()
    for key, (x, y, w, h) in MON.items():
        pad = 18
        ctx.new_path()
        _rrect(ctx, x - pad, y - pad, w + 2 * pad, h + 2 * pad + 14, 22)
        src(ctx, hexc("#c9c2ae"))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(4)
        ctx.stroke()
        _rrect(ctx, x - pad + 6, y - pad + 6, w + 2 * pad - 12, h + 2 * pad + 2, 18)
        src(ctx, (1, 1, 1, 0.25))
        ctx.set_line_width(2)
        ctx.stroke()
        # knobs
        for i in range(2):
            ctx.arc(x + w - 14 - i * 22, y + h + 12, 5, 0, 2 * math.pi)
            src(ctx, hexc("#555"))
            ctx.fill()
        _rrect(ctx, x - 4, y - 4, w + 8, h + 8, 14)
        src(ctx, hexc("#111"))
        ctx.fill()


def _rrect(ctx, x, y, w, h, r):
    ctx.new_sub_path()
    ctx.arc(x + w - r, y + r, r, -math.pi / 2, 0)
    ctx.arc(x + w - r, y + h - r, r, 0, math.pi / 2)
    ctx.arc(x + r, y + h - r, r, math.pi / 2, math.pi)
    ctx.arc(x + r, y + r, r, math.pi, 1.5 * math.pi)
    ctx.close_path()


def _paper(ctx, x, y, w, h, rot, col="#f4efe0"):
    ctx.save()
    ctx.translate(x + w / 2, y + h / 2)
    ctx.rotate(rot)
    ctx.rectangle(-w / 2 + 5, -h / 2 + 7, w, h)
    src(ctx, (0, 0, 0, 0.3))
    ctx.fill()
    ctx.rectangle(-w / 2, -h / 2, w, h)
    src(ctx, hexc(col))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(2.5)
    ctx.stroke()
    return w, h


def _pin(ctx, x, y, col="#d62828"):
    ctx.arc(x, y, 7, 0, 2 * math.pi)
    src(ctx, hexc(col))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(2)
    ctx.stroke()


def _draw_board(ctx):
    x, y, w, h = BOARD_RECT
    ctx.rectangle(x + 12, y + 16, w, h)
    src(ctx, (0, 0, 0.05, 0.35))
    ctx.fill()
    _frame(ctx, x, y, w, h, "#5a3519", 16)
    ctx.rectangle(x, y, w, h)
    src(ctx, hexc("#c08a52"))
    ctx.fill()
    rnd = random.Random(3)
    for _ in range(420):
        ctx.arc(x + rnd.random() * w, y + rnd.random() * h, rnd.random() * 2 + 0.6, 0, 6.3)
        src(ctx, (0.45, 0.27, 0.12, 0.35))
        ctx.fill()

    # 1) falling chart
    pw, ph = _paper(ctx, x + 22, y + 24, 190, 150, -0.05)
    ctx.move_to(-80, -55)
    ctx.line_to(-80, 55)
    ctx.line_to(85, 55)
    src(ctx, INK)
    ctx.set_line_width(2)
    ctx.stroke()
    pts = [(-75, -40), (-50, -30), (-30, -38), (-5, -5), (15, -12), (40, 20), (60, 15), (80, 45)]
    ctx.move_to(*pts[0])
    for q in pts[1:]:
        ctx.line_to(*q)
    src(ctx, hexc("#c1121f"))
    ctx.set_line_width(4)
    ctx.stroke()
    ctx.move_to(80, 45)
    ctx.line_to(66, 44)
    ctx.line_to(76, 32)
    ctx.close_path()
    ctx.fill()
    ctx.select_font_face(fonts.SANS, cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(13)
    src(ctx, INK)
    text_center(ctx, SET["chart_title"], 0, -62)
    ctx.restore()
    _pin(ctx, x + 117, y + 30)

    # 2) newspaper clippings
    for (cx, cy, cw, chh, rot, head, sub) in (
            (x + 228, y + 34, 160, 150, 0.06, *SET["clippings"][0]),
            (x + 30, y + 200, 175, 140, 0.04, *SET["clippings"][1]),
            (x + 220, y + 210, 160, 130, -0.07, *SET["clippings"][2])):
        _paper(ctx, cx, cy, cw, chh, rot, "#ebe6d6")
        ctx.select_font_face(fonts.TITLE)
        ctx.set_font_size(26)
        src(ctx, INK)
        text_center(ctx, head, 0, -chh / 2 + 28)
        ctx.select_font_face(fonts.SANS, cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
        ctx.set_font_size(11)
        text_center(ctx, sub, 0, -chh / 2 + 52)
        # column lines
        for col in range(3):
            for ln in range(5):
                lx = -cw / 2 + 12 + col * (cw - 24) / 3
                ly = -chh / 2 + 70 + ln * 11
                if ly > chh / 2 - 8:
                    continue
                ctx.move_to(lx, ly)
                ctx.line_to(lx + (cw - 24) / 3 - 10, ly)
        src(ctx, (0.3, 0.3, 0.3, 0.6))
        ctx.set_line_width(2)
        ctx.stroke()
        ctx.restore()
        _pin(ctx, cx + cw / 2, cy + 8, "#2a6fdb")

    # 3) the plan, on sticky notes
    for i, (txt, col, rot) in enumerate(zip(SET["notes"][:3], ("#ffe066", "#ffd166", "#ff9f8a"),
                                            (-0.08, 0.05, -0.03))):
        nx = x + 26 + i * 124
        ny = y + 360
        _paper(ctx, nx, ny, 112, 92, rot, col)
        ctx.select_font_face(fonts.TITLE)
        ctx.set_font_size(23)
        src(ctx, hexc("#3a1d4a"))
        text_center(ctx, txt, 0, 0)
        ctx.restore()
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(26)
    src(ctx, hexc("#3a1d4a"))
    text_center(ctx, SET["board_title"], x + w / 2, y + 344)


def _draw_calendar(ctx):
    x, y = 1500.0, 590.0
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(0.02)
    ctx.rectangle(6, 8, 150, 120)
    src(ctx, (0, 0, 0, 0.3))
    ctx.fill()
    ctx.rectangle(0, 0, 150, 120)
    src(ctx, hexc("#f4efe0"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(3)
    ctx.stroke()
    ctx.rectangle(0, 0, 150, 28)
    src(ctx, hexc("#b5332e"))
    ctx.fill()
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(20)
    src(ctx, (1, 1, 1))
    text_center(ctx, SET["month"], 75, 15)
    ctx.set_font_size(11)
    src(ctx, INK)
    for i in range(28):
        cx = 12 + (i % 7) * 21
        cy = 42 + (i // 7) * 21
        text_center(ctx, str(i + 1), cx, cy)
    ctx.move_to(12 + 3 * 21 - 9, 42 + 9 - 9)
    ctx.line_to(12 + 3 * 21 + 9, 42 + 9 + 9)
    src(ctx, hexc("#d62828"))
    ctx.set_line_width(3)
    ctx.stroke()
    ctx.restore()


def _draw_shelf_props(ctx):
    # small bottles on the monitor shelf
    for i, (bx, col, hgt) in enumerate(((1380, "#7ad7a0", 70), (1418, "#e38bd1", 52))):
        ctx.rectangle(bx, 548 - hgt, 26, hgt)
        src(ctx, (*hexc(col), 0.85))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(3)
        ctx.stroke()
        ctx.rectangle(bx + 6, 548 - hgt - 16, 14, 16)
        src(ctx, hexc("#8a6a4a"))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.stroke()


def _draw_table(ctx, rnd):
    wx, wy, ww, wh = WORLD
    # table top in slight perspective
    g = cairo.LinearGradient(0, TABLE_Y, 0, TABLE_FRONT)
    g.add_color_stop_rgb(0, *hexc("#6e4127"))
    g.add_color_stop_rgb(1, *hexc("#a8693d"))
    ctx.rectangle(wx, TABLE_Y, ww, TABLE_FRONT - TABLE_Y)
    ctx.set_source(g)
    ctx.fill()
    # planks / grain
    for i in range(1, 7):
        y = TABLE_Y + (TABLE_FRONT - TABLE_Y) * (i / 7) ** 1.3
        ctx.move_to(wx, y)
        ctx.line_to(wx + ww, y)
        src(ctx, (0.25, 0.12, 0.05, 0.35))
        ctx.set_line_width(2 + i * 0.4)
        ctx.stroke()
    for _ in range(140):
        y = TABLE_Y + rnd.random() * (TABLE_FRONT - TABLE_Y)
        x = wx + rnd.random() * ww
        ln = 40 + rnd.random() * 160
        ctx.move_to(x, y)
        ctx.curve_to(x + ln * 0.3, y + 2, x + ln * 0.6, y - 2, x + ln, y)
        src(ctx, (0.3, 0.15, 0.06, 0.18))
        ctx.set_line_width(1.5)
        ctx.stroke()
    ctx.move_to(wx, TABLE_Y)
    ctx.line_to(wx + ww, TABLE_Y)
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    # contact shadow along the wall
    g = cairo.LinearGradient(0, TABLE_Y, 0, TABLE_Y + 40)
    g.add_color_stop_rgba(0, 0, 0, 0, 0.45)
    g.add_color_stop_rgba(1, 0, 0, 0, 0)
    ctx.rectangle(wx, TABLE_Y, ww, 40)
    ctx.set_source(g)
    ctx.fill()
    # table front edge and face
    ctx.rectangle(wx, TABLE_FRONT, ww, 26)
    src(ctx, hexc("#c27c47"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    g = cairo.LinearGradient(0, TABLE_FRONT + 26, 0, wy + wh)
    g.add_color_stop_rgb(0, *hexc("#5a3019"))
    g.add_color_stop_rgb(1, *hexc("#1f0f08"))
    ctx.rectangle(wx, TABLE_FRONT + 26, ww, wy + wh - TABLE_FRONT - 26)
    ctx.set_source(g)
    ctx.fill()

    # glass jar with cork (left)
    jx, jy, jw, jh = -10.0, 520.0, 230.0, 360.0
    ctx.save()
    ellipse_path(ctx, jx + jw / 2, TABLE_Y + 22, jw * 0.6, 18)
    src(ctx, (0, 0, 0, 0.35))
    ctx.fill()
    _rrect(ctx, jx, jy, jw, jh + 20, 40)
    src(ctx, (0.75, 0.88, 0.95, 0.22))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    # contents: green glowing liquid
    ctx.save()
    _rrect(ctx, jx, jy, jw, jh + 20, 40)
    ctx.clip()
    ctx.rectangle(jx, jy + 170, jw, jh)
    g = cairo.LinearGradient(0, jy + 170, 0, jy + jh)
    g.add_color_stop_rgba(0, 0.45, 1.0, 0.55, 0.75)
    g.add_color_stop_rgba(1, 0.1, 0.55, 0.3, 0.85)
    ctx.set_source(g)
    ctx.fill()
    ctx.restore()
    ctx.move_to(jx + 30, jy + 40)
    ctx.line_to(jx + 30, jy + jh - 30)
    src(ctx, (1, 1, 1, 0.45))
    ctx.set_line_width(10)
    ctx.stroke()
    ctx.rectangle(jx + 40, jy - 46, jw - 80, 50)
    src(ctx, hexc("#b88850"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    ctx.restore()

    # erlenmeyer flask (right, back of the table)
    fx, fy = 1700.0, TABLE_Y + 28
    ctx.save()
    ellipse_path(ctx, fx, fy, 110, 16)
    src(ctx, (0, 0, 0, 0.35))
    ctx.fill()
    ctx.move_to(fx - 30, fy - 300)
    ctx.line_to(fx - 30, fy - 190)
    ctx.line_to(fx - 110, fy - 10)
    ctx.curve_to(fx - 114, fy, fx - 100, fy + 6, fx - 90, fy + 6)
    ctx.line_to(fx + 90, fy + 6)
    ctx.curve_to(fx + 100, fy + 6, fx + 114, fy, fx + 110, fy - 10)
    ctx.line_to(fx + 30, fy - 190)
    ctx.line_to(fx + 30, fy - 300)
    ctx.close_path()
    src(ctx, (0.75, 0.88, 0.95, 0.22))
    ctx.fill_preserve()
    path = ctx.copy_path()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    ctx.save()
    ctx.append_path(path)
    ctx.clip()
    ctx.rectangle(fx - 130, fy - 110, 260, 130)
    src(ctx, (0.95, 0.35, 0.75, 0.75))
    ctx.fill()
    ctx.restore()
    ctx.rectangle(fx - 38, fy - 316, 76, 20)
    src(ctx, hexc("#d9d4c4"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(3)
    ctx.stroke()
    ctx.restore()

    # stacked giant books (far right)
    ycur = TABLE_Y + 6
    for col, w, h, off in (("#2f6690", 330, 70, 0), ("#9b2226", 300, 60, 18),
                           ("#3a7d44", 320, 64, -6)):
        bx = 1880 + off
        ycur -= h
        ctx.rectangle(bx, ycur, w, h)
        src(ctx, hexc(col))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(4)
        ctx.stroke()
        ctx.rectangle(bx + 12, ycur + h * 0.3, w - 24, 8)
        src(ctx, (1, 0.85, 0.4, 0.8))
        ctx.fill()

    # scattered newspapers on the table (in perspective)
    for (px, py, pw, ph, rot) in ((380, 1040, 360, 120, -0.06), (1050, 1080, 380, 110, 0.05),
                                  (720, 1150, 300, 90, 0.02), (-200, 1100, 330, 100, 0.08),
                                  (1500, 1140, 300, 90, -0.04)):
        ctx.save()
        ctx.translate(px + pw / 2, py + ph / 2)
        ctx.rotate(rot)
        ctx.rectangle(-pw / 2 + 4, -ph / 2 + 6, pw, ph)
        src(ctx, (0, 0, 0, 0.25))
        ctx.fill()
        ctx.rectangle(-pw / 2, -ph / 2, pw, ph)
        src(ctx, hexc("#e6e0cc"))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(2.5)
        ctx.stroke()
        ctx.rectangle(-pw / 2 + 14, -ph / 2 + 10, pw * 0.55, 14)
        src(ctx, (0.15, 0.15, 0.2, 0.75))
        ctx.fill()
        for ln in range(4):
            for col in range(3):
                lx = -pw / 2 + 14 + col * (pw - 28) / 3
                ly = -ph / 2 + 36 + ln * 13
                if ly < ph / 2 - 6:
                    ctx.move_to(lx, ly)
                    ctx.line_to(lx + (pw - 28) / 3 - 14, ly)
        src(ctx, (0.3, 0.3, 0.3, 0.5))
        ctx.set_line_width(2.5)
        ctx.stroke()
        ctx.restore()

    # giant pencil in the foreground of the table
    ctx.save()
    ctx.translate(140, 1185)
    ctx.rotate(-0.05)
    ctx.rectangle(0, -16, 520, 32)
    src(ctx, hexc("#f2b632"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.set_line_width(4)
    ctx.stroke()
    ctx.rectangle(0, -16, 520, 10)
    src(ctx, hexc("#ffd166"))
    ctx.fill()
    ctx.move_to(520, -16)
    ctx.line_to(590, 0)
    ctx.line_to(520, 16)
    ctx.close_path()
    src(ctx, hexc("#f1d3a8"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.stroke()
    ctx.move_to(572, -4)
    ctx.line_to(590, 0)
    ctx.line_to(572, 4)
    src(ctx, INK)
    ctx.fill()
    ctx.rectangle(-60, -16, 60, 32)
    src(ctx, hexc("#f28482"))
    ctx.fill_preserve()
    src(ctx, INK)
    ctx.stroke()
    ctx.restore()


# --------------------------------------------------------------------------
# Animated layers (drawn every frame, in world space)
# --------------------------------------------------------------------------
TICKER = SET["ticker"]


def draw_dynamic(ctx, s, t):
    """Monitors, map hot-spots, bubbling flask, ambient glows."""
    crisis = s.get("lab.crisis", 0.0)
    nosig = s.get("lab.nosignal", 0.0)      # 0..3 monitors dead
    flicker = s.get("lab.flicker", 0.0)
    _map_hotspots(ctx, t, crisis, s.get("lab.mapdead", 0.0))
    _screens(ctx, t, crisis, nosig, flicker)
    _bubbles(ctx, t)


def _map_hotspots(ctx, t, crisis, dead):
    if dead > 0:
        x, y, w, h = MAP_RECT
        ctx.rectangle(x, y, w, h - 46)
        src(ctx, (0.25, 0.2, 0.25, 0.55 * dead))
        ctx.fill()
        # cracks
        rnd = random.Random(11)
        ctx.save()
        ctx.rectangle(x, y, w, h - 46)
        ctx.clip()
        for k in range(7):
            px, py = x + rnd.random() * w, y + rnd.random() * (h - 46)
            ctx.move_to(px, py)
            for _ in range(5):
                px += (rnd.random() - 0.5) * 140
                py += (rnd.random() - 0.3) * 90
                ctx.line_to(px, py)
        src(ctx, (0.1, 0.05, 0.08, 0.8 * dead))
        ctx.set_line_width(3)
        ctx.stroke()
        ctx.restore()
    if crisis <= 0:
        return
    for i, (lon, lat, order) in enumerate(HOTSPOTS):
        a = clamp((crisis - order) / 0.08)
        if a <= 0:
            continue
        px, py = map_xy(lon, lat)
        pulse = 0.5 + 0.5 * math.sin(t * 6 + i)
        r = (16 + 10 * pulse) * (0.4 + 0.6 * a)
        g = cairo.RadialGradient(px, py, 0, px, py, r * 2.2)
        g.add_color_stop_rgba(0, 1, 0.2, 0.15, 0.55 * a)
        g.add_color_stop_rgba(1, 1, 0.2, 0.15, 0)
        ctx.set_source(g)
        ctx.arc(px, py, r * 2.2, 0, 2 * math.pi)
        ctx.fill()
        ctx.arc(px, py, 8 * a + 2, 0, 2 * math.pi)
        src(ctx, (0.85, 0.05, 0.08, a))
        ctx.fill_preserve()
        src(ctx, (*INK, a))
        ctx.set_line_width(2)
        ctx.stroke()


def _static_noise(ctx, x, y, w, h, t, seed):
    rnd = random.Random(int(t * 24) * 31 + seed)
    for _ in range(160):
        gx = x + rnd.random() * w
        gy = y + rnd.random() * h
        v = rnd.random()
        ctx.rectangle(gx, gy, 3 + rnd.random() * 10, 2)
        ctx.set_source_rgba(v, v, v, 0.8)
        ctx.fill()


def _screens(ctx, t, crisis, nosig, flicker):
    order = ["alert", "chart", "news"]
    for key, (x, y, w, h) in MON.items():
        dead = nosig > order.index(key) + 0.5 - 1e-6 if nosig else False
        ctx.save()
        _rrect(ctx, x, y, w, h, 12)
        ctx.clip()
        if dead:
            ctx.rectangle(x, y, w, h)
            src(ctx, hexc("#1a1a22"))
            ctx.fill()
            _static_noise(ctx, x, y, w, h, t, hash(key) % 97)
            ctx.rectangle(x, y + h / 2 - 22, w, 44)
            src(ctx, (0, 0, 0.6, 0.85))
            ctx.fill()
            ctx.select_font_face(fonts.TITLE)
            ctx.set_font_size(30)
            src(ctx, (1, 1, 1))
            text_center(ctx, "SEM SINAL", x + w / 2, y + h / 2)
        else:
            ctx.rectangle(x, y, w, h)
            src(ctx, hexc("#0c2a4a"))
            ctx.fill()
            if key == "news":
                _screen_news(ctx, x, y, w, h, t)
            elif key == "chart":
                _screen_chart(ctx, x, y, w, h, t, crisis)
            else:
                _screen_alert(ctx, x, y, w, h, t, crisis)
        # scanlines + glass
        for ly in range(int(y), int(y + h), 4):
            ctx.rectangle(x, ly, w, 1.5)
        src(ctx, (0, 0, 0, 0.22))
        ctx.fill()
        g = cairo.RadialGradient(x + w * 0.3, y + h * 0.2, 0, x + w * 0.5, y + h * 0.5, w * 0.8)
        g.add_color_stop_rgba(0, 1, 1, 1, 0.16)
        g.add_color_stop_rgba(1, 0, 0, 0, 0.35)
        ctx.rectangle(x, y, w, h)
        ctx.set_source(g)
        ctx.fill()
        if flicker > 0:
            ctx.rectangle(x, y, w, h)
            ctx.set_source_rgba(1, 1, 1, flicker * (0.5 + 0.5 * noise1(t * 30, 5)))
            ctx.fill()
        ctx.restore()
        # light spill on the wall
        g = cairo.RadialGradient(x + w / 2, y + h / 2, w * 0.3, x + w / 2, y + h / 2, w * 1.1)
        a = 0.10 if not dead else 0.04
        g.add_color_stop_rgba(0, 0.3, 0.7, 1, a)
        g.add_color_stop_rgba(1, 0.3, 0.7, 1, 0)
        ctx.set_source(g)
        ctx.arc(x + w / 2, y + h / 2, w * 1.1, 0, 2 * math.pi)
        ctx.fill()


def _screen_news(ctx, x, y, w, h, t):
    # header
    ctx.rectangle(x, y, w, 34)
    src(ctx, hexc("#c1121f"))
    ctx.fill()
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(24)
    src(ctx, (1, 1, 1))
    ctx.move_to(x + 10, y + 26)
    ctx.show_text("NOTÍCIAS 24H")
    if int(t * 2) % 2 == 0:
        ctx.arc(x + w - 46, y + 17, 6, 0, 2 * math.pi)
        ctx.fill()
        ctx.set_font_size(16)
        ctx.move_to(x + w - 36, y + 23)
        ctx.show_text("AO VIVO")
    # globe icon spinning
    gx, gy, gr = x + 60, y + 92, 40
    ctx.arc(gx, gy, gr, 0, 2 * math.pi)
    src(ctx, hexc("#2a7bd4"))
    ctx.fill_preserve()
    src(ctx, (1, 1, 1, 0.8))
    ctx.set_line_width(2)
    ctx.stroke()
    ctx.save()
    ctx.arc(gx, gy, gr, 0, 2 * math.pi)
    ctx.clip()
    off = (t * 18) % 80
    for k in range(-2, 3):
        ellipse_path(ctx, gx - off + k * 80, gy - 8, 18, 22)
        ellipse_path(ctx, gx - off + k * 80 + 34, gy + 14, 14, 12)
    src(ctx, hexc("#66c46a"))
    ctx.fill()
    ctx.restore()
    # headline bars
    ctx.set_font_size(18)
    src(ctx, (1, 1, 1))
    ctx.move_to(x + 112, y + 74)
    ctx.show_text(SET["news_headline"][0])
    ctx.move_to(x + 112, y + 96)
    ctx.show_text(SET["news_headline"][1])
    ctx.rectangle(x + 112, y + 106, 90, 6)
    src(ctx, (1, 1, 1, 0.5))
    ctx.fill()
    # ticker
    ctx.rectangle(x, y + h - 34, w, 34)
    src(ctx, hexc("#ffd166"))
    ctx.fill()
    ctx.select_font_face(fonts.SANS, cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(16)
    src(ctx, hexc("#111"))
    tw = ctx.text_extents(TICKER).x_advance
    off = (t * 70) % tw
    for k in range(3):
        ctx.move_to(x + w - off + (k - 1) * tw, y + h - 11)
        ctx.show_text(TICKER)


def _screen_chart(ctx, x, y, w, h, t, crisis):
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(22)
    src(ctx, hexc("#9ad1ff"))
    ctx.move_to(x + 10, y + 26)
    ctx.show_text(SET["economy_title"])
    # grid
    for i in range(1, 6):
        ctx.move_to(x, y + 30 + i * (h - 40) / 6)
        ctx.line_to(x + w, y + 30 + i * (h - 40) / 6)
    src(ctx, (0.6, 0.8, 1, 0.15))
    ctx.set_line_width(1)
    ctx.stroke()
    n = 40
    pts = []
    level = 0.25 + 0.5 * crisis
    for i in range(n):
        u = i / (n - 1)
        v = 0.25 + u * level + 0.08 * noise1(u * 9 + t * 1.2, 2) + 0.03 * math.sin(u * 30 + t * 4)
        pts.append((x + 8 + u * (w - 16), y + 40 + v * (h - 60)))
    ctx.move_to(*pts[0])
    for q in pts[1:]:
        ctx.line_to(*q)
    src(ctx, hexc("#ff4d4d"))
    ctx.set_line_width(3.5)
    ctx.stroke()
    lx, ly = pts[-1]
    ctx.move_to(lx, ly + 10)
    ctx.line_to(lx - 14, ly - 6)
    ctx.line_to(lx + 6, ly - 10)
    ctx.close_path()
    ctx.fill()
    pct = -2.1 - 9.0 * crisis - 0.3 * abs(math.sin(t * 2))
    ctx.select_font_face(fonts.SANS, cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(18)
    ctx.move_to(x + w - 82, y + 54)
    ctx.show_text(f"{pct:.1f}%".replace(".", ","))


def _screen_alert(ctx, x, y, w, h, t, crisis):
    blink = int(t * 2.5) % 2 == 0
    ctx.rectangle(x, y, w, 40)
    src(ctx, hexc("#c1121f") if blink else hexc("#7a0b13"))
    ctx.fill()
    ctx.select_font_face(fonts.TITLE)
    ctx.set_font_size(30)
    src(ctx, (1, 1, 1))
    text_center(ctx, f"! {SET['alert_title']} !" if blink else SET["alert_title"], x + w / 2, y + 21)
    names = SET["alert_bars"]
    labels = [(names[0], 0.55 + 0.4 * crisis), (names[1], 0.45 + 0.52 * crisis),
              (names[2], 0.6 + 0.38 * crisis), (names[3], 0.12 - 0.1 * crisis)]
    ctx.select_font_face(fonts.SANS, cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(16)
    for i, (lab, v) in enumerate(labels):
        by = y + 62 + i * 40
        v = clamp(v + 0.03 * math.sin(t * 3 + i))
        src(ctx, (0.85, 0.92, 1))
        ctx.move_to(x + 12, by + 14)
        ctx.show_text(lab)
        ctx.rectangle(x + 150, by, w - 170, 20)
        src(ctx, (1, 1, 1, 0.15))
        ctx.fill()
        ctx.rectangle(x + 150, by, (w - 170) * v, 20)
        col = mix(hexc("#ffd166"), hexc("#ff3b3b"), v) if lab != names[3] else hexc("#5cd65c")
        src(ctx, col)
        ctx.fill()


def _bubbles(ctx, t):
    fx, fy = 1700.0, TABLE_Y + 28
    for i in range(7):
        ph = (t * 0.6 + i / 7) % 1.0
        bx = fx + math.sin(i * 2.3 + t * 2) * 50 * (1 - ph)
        by = fy - 10 - ph * 100
        if by < fy - 105:
            continue
        ctx.arc(bx, by, 4 + 5 * (1 - ph), 0, 2 * math.pi)
        ctx.set_source_rgba(1, 0.8, 0.95, 0.75)
        ctx.fill()
    # vapour from the neck
    for i in range(4):
        ph = (t * 0.35 + i / 4) % 1.0
        ctx.arc(fx + math.sin(t + i) * 14 * ph, fy - 320 - ph * 120, 10 + 26 * ph, 0, 2 * math.pi)
        ctx.set_source_rgba(1, 0.85, 0.97, 0.22 * (1 - ph))
        ctx.fill()


# --------------------------------------------------------------------------
# Lighting passes (world space, after characters)
# --------------------------------------------------------------------------
def draw_lighting(ctx, s, t):
    wx, wy, ww, wh = WORLD
    dark = s.get("light.dark", 0.0)       # dramatic dimming (0..1)
    red = s.get("light.red", 0.0)         # sinister red under-light
    key = s.get("light.key", (930.0, 700.0))
    # cool ambient multiply with warm key pool
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_MULTIPLY)
    g = cairo.RadialGradient(key[0], key[1], 120, key[0], key[1], 1500)
    c_in = mix((1.0, 0.97, 0.92), (0.45, 0.4, 0.55), dark)
    c_out = mix((0.52, 0.56, 0.8), (0.18, 0.16, 0.3), dark)
    g.add_color_stop_rgb(0, *c_in)
    g.add_color_stop_rgb(1, *c_out)
    ctx.rectangle(wx, wy, ww, wh)
    ctx.set_source(g)
    ctx.paint()
    ctx.restore()
    if red > 0:
        ctx.save()
        ctx.set_operator(cairo.OPERATOR_ADD)
        bx = s.get("brain.x", 700.0)
        g = cairo.RadialGradient(bx, FLOOR_Y + 40, 20, bx, FLOOR_Y - 200, 600)
        g.add_color_stop_rgba(0, 0.9, 0.1, 0.05, 0.55 * red)
        g.add_color_stop_rgba(1, 0.9, 0.1, 0.05, 0)
        ctx.set_source(g)
        ctx.rectangle(wx, wy, ww, wh)
        ctx.fill()
        ctx.restore()
    # warm glow of the key light (adds a little bloom)
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_ADD)
    g = cairo.RadialGradient(key[0], key[1] - 150, 0, key[0], key[1] - 150, 900)
    g.add_color_stop_rgba(0, 0.25, 0.17, 0.08, 0.35 * (1 - dark))
    g.add_color_stop_rgba(1, 0, 0, 0, 0)
    ctx.set_source(g)
    ctx.rectangle(wx, wy, ww, wh)
    ctx.fill()
    ctx.restore()
