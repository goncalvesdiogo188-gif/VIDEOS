"""Procedural 2D rigs for the two lab mice.

Both characters are drawn entirely from vector primitives with cairo, in a
local coordinate system whose origin is the point between the feet (y grows
downward, so the body lives in negative y).  The rig faces right by default;
`facing = -1` mirrors it.

All parameters are read from the timeline with a prefix ("brain." / "pinky."),
so every expression and pose element is independently animatable.
"""
from __future__ import annotations

import math

import cairo

from .anim import noise1
from .draw import (INK, NOSE_RED, NOSE_SHADE, PINK, PINK_SHADE, WHITE, FUR_SHADE,
                   arm_points, ellipse_path, limb, src, toon, mix)

# ---------------------------------------------------------------------------
# Shared defaults
# ---------------------------------------------------------------------------
COMMON = dict(
    x=0.0, y=0.0, scale=1.0, facing=1.0, lean=0.0, bob=0.0, squash=1.0,
    head_tilt=0.0, head_turn=0.35, head_dx=0.0, head_dy=0.0,
    brow_l=0.0, brow_r=0.0, brow_tilt=0.0,
    lid=0.0, lid_low=0.0, blink=0.0, look=(0.0, 0.0), pupil=1.0,
    mouth_open=0.0, mouth_wide=0.5, smile=0.0, smirk=0.0, jaw=0.0,
    arm_f=(0.25, 0.35), arm_b=(-0.15, 0.3), hand_f="relaxed", hand_b="relaxed",
    walk=0.0, walk_phase=0.0, stride=1.0,
    sweat=0.0, twitch=0.0, scratch=0.0, breathe=1.0, visible=1.0, shadow=1.0, talk=0.0,
)

BRAIN_DEFAULTS = dict(COMMON, lid=0.42, brow_tilt=0.25, arm_f=(0.3, 0.6),
                      arm_b=(-0.25, 0.5), pointer=0.0)
PINKY_DEFAULTS = dict(COMMON, lid=0.08, head_turn=0.4, arm_f=(0.15, 0.25),
                      arm_b=(-0.1, 0.2), teeth=1.0)

IRIS_BRAIN = (0.86, 0.36, 0.45)
IRIS_PINKY = (0.25, 0.52, 0.93)
MOUTH_IN = (0.38, 0.05, 0.12)
LID = (0.86, 0.76, 0.86)
TONGUE = (0.93, 0.42, 0.50)


BRAIN_ARM = ((24.0, -146.0), (-30.0, -150.0), 44.0, 40.0)    # front shoulder, back shoulder, l1, l2
PINKY_ARM = ((18.0, -318.0), (-22.0, -322.0), 72.0, 66.0)


def ik(shoulder, target, l1, l2, bend=1.0):
    """2-bone IK: (a1, a2) angles that put the hand at `target` (local coords).
    bend=+1 puts the elbow below/outside, -1 the other way."""
    dx, dy = target[0] - shoulder[0], target[1] - shoulder[1]
    d = min(math.hypot(dx, dy), l1 + l2 - 1e-3)
    d = max(d, abs(l1 - l2) + 1e-3)
    th = math.atan2(dx, dy)
    al = math.acos(max(-1, min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))))
    be = math.acos(max(-1, min(1, (l1 * l1 + l2 * l2 - d * d) / (2 * l1 * l2))))
    a1 = th + bend * al
    a2 = -bend * (math.pi - be)
    return (a1, a2)


def read_params(s, prefix, defaults):
    p = {}
    for k, v in defaults.items():
        val = s.get(prefix + k, None)
        p[k] = v if val is None else val
    return p


# ---------------------------------------------------------------------------
# Pieces shared by both rigs
# ---------------------------------------------------------------------------
def draw_hand(ctx, x, y, ang, kind, r=10.0, lw=3.5):
    """Pink cartoon hand at (x, y), pointing along `ang` (0 = down)."""
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(-ang)
    fingers = []
    if kind == "point":
        fingers = [(0.0, 2.3, 0.42)]           # (angle offset, length, width)
        thumb = (0.9, 1.0)
    elif kind == "open":
        fingers = [(-0.55, 1.45, 0.42), (-0.18, 1.6, 0.42), (0.2, 1.55, 0.42), (0.6, 1.3, 0.42)]
        thumb = None
    elif kind == "fist":
        fingers = []
        thumb = (0.7, 0.7)
    else:  # relaxed
        fingers = [(-0.35, 1.25, 0.45), (0.0, 1.4, 0.45), (0.35, 1.25, 0.45)]
        thumb = None

    def path(c):
        c.new_sub_path()
        c.arc(0, 0, r, 0, 2 * math.pi)
        for a, ln, w in fingers:
            ellipse_path(c, math.sin(a) * r * ln * 0.75, math.cos(a) * r * ln * 0.75,
                         r * w, r * ln * 0.55, -a)
        if thumb:
            a, ln = thumb
            ellipse_path(c, math.sin(a) * r * 0.95, math.cos(a) * r * 0.6,
                         r * 0.38, r * 0.6 * ln, -a)

    toon(ctx, path, PINK, PINK_SHADE, shade_amt=3, lw=lw)
    ctx.restore()


def draw_eye(ctx, cx, cy, rx, ry, p, iris_col, iris_r, lid_base, t, seed, heavy=False, lw=4.0):
    """Sclera + iris + pupil + animated upper/lower lids."""
    lx, ly = p["look"]
    lid = min(1.0, lid_base + p["blink"] * (1.0 - lid_base))
    if p["twitch"] > 0:
        lid = min(1.0, max(0.0, lid + p["twitch"] * 0.12 * noise1(t * 22.0, seed)))
    low = min(1.0 - lid, p["lid_low"])

    ctx.save()
    # sclera
    ellipse_path(ctx, cx, cy, rx, ry)
    src(ctx, WHITE)
    ctx.fill_preserve()
    ctx.save()
    ctx.clip()
    # iris / pupil
    ix = cx + lx * rx * 0.45
    iy = cy + ly * ry * 0.38 + ry * 0.08
    if iris_col is not None:
        ellipse_path(ctx, ix, iy, iris_r * 0.92, iris_r)
        src(ctx, iris_col)
        ctx.fill()
    pr = iris_r * 0.55 * p["pupil"]
    if iris_col is None:
        pr = iris_r * p["pupil"]
    ellipse_path(ctx, ix, iy, pr * 0.9, pr)
    src(ctx, INK)
    ctx.fill()
    if p["pupil"] > 0.5:
        ellipse_path(ctx, ix - iris_r * 0.3, iy - iris_r * 0.35, iris_r * 0.22, iris_r * 0.22)
        src(ctx, (1, 1, 1))
        ctx.fill()
    # upper lid (fur coloured), with a curved edge
    edge = cy - ry + 2 * ry * lid
    if lid > 0.01:
        ctx.move_to(cx - rx * 1.5, cy - ry * 1.5)
        ctx.line_to(cx + rx * 1.5, cy - ry * 1.5)
        ctx.line_to(cx + rx * 1.5, edge - ry * 0.05)
        ctx.curve_to(cx + rx * 0.5, edge + ry * 0.12, cx - rx * 0.5, edge + ry * 0.12,
                     cx - rx * 1.5, edge - ry * 0.05)
        ctx.close_path()
        src(ctx, LID)
        ctx.fill()
        if lid < 0.98:
            ctx.move_to(cx - rx * 1.2, edge)
            ctx.curve_to(cx - rx * 0.5, edge + ry * 0.12, cx + rx * 0.5, edge + ry * 0.12,
                         cx + rx * 1.2, edge)
            src(ctx, INK)
            ctx.set_line_width(lw * (1.4 if heavy else 1.0))
            ctx.stroke()
    if low > 0.01:
        ledge = cy + ry - 2 * ry * low
        ctx.move_to(cx - rx * 1.5, cy + ry * 1.5)
        ctx.line_to(cx - rx * 1.5, ledge)
        ctx.curve_to(cx - rx * 0.5, ledge - ry * 0.1, cx + rx * 0.5, ledge - ry * 0.1,
                     cx + rx * 1.5, ledge)
        ctx.line_to(cx + rx * 1.5, cy + ry * 1.5)
        ctx.close_path()
        src(ctx, LID)
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(lw * 0.8)
        ctx.stroke()
    ctx.restore()
    ellipse_path(ctx, cx, cy, rx, ry)
    src(ctx, INK)
    ctx.set_line_width(lw)
    ctx.stroke()
    ctx.restore()


def draw_brow(ctx, cx, cy, half, raise_, tilt, inner_sign, width, lw):
    """A thick eyebrow.  inner_sign: +1 if the inner end is at +x."""
    dy = -raise_ * 14
    ix, ox = cx + inner_sign * half, cx - inner_sign * half
    iy = cy + dy + tilt * 12
    oy = cy + dy - tilt * 6
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.move_to(ox, oy)
    ctx.curve_to(ox + (ix - ox) * 0.35, oy - 9 - raise_ * 3, ox + (ix - ox) * 0.7, iy - 7,
                 ix, iy)
    src(ctx, INK)
    ctx.set_line_width(width)
    ctx.stroke()
    ctx.restore()


def draw_mouth(ctx, cx, cy, width, p, depth=34.0, lw=4.0, buck_teeth=None):
    open_ = max(0.0, min(1.2, p["mouth_open"] + p["jaw"]))
    wide = p["mouth_wide"]
    smile = p["smile"]
    smirk = p["smirk"]
    w = width * (0.62 + 0.6 * wide) * (1.0 - 0.25 * open_ * (1 - wide))
    lx, rx = cx - w / 2, cx + w / 2
    ly = cy - smile * 9 + smirk * 7
    ry = cy - smile * 9 - smirk * 7
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    if open_ < 0.06:
        ctx.move_to(lx, ly)
        ctx.curve_to(lx + w * 0.3, cy + smile * 9, rx - w * 0.3, cy + smile * 9, rx, ry)
        src(ctx, INK)
        ctx.set_line_width(lw * 1.1)
        ctx.stroke()
        if buck_teeth:
            buck_teeth(cy + smile * 6)
    else:
        h = depth * open_
        up = cy - h * 0.12 + smile * 2
        lo = cy + h + smile * 5
        def path():
            ctx.move_to(lx, ly)
            ctx.curve_to(lx + w * 0.25, up, rx - w * 0.25, up, rx, ry)
            ctx.curve_to(rx - w * 0.05, lo, lx + w * 0.05, lo, lx, ly)
            ctx.close_path()
        path()
        src(ctx, MOUTH_IN)
        ctx.fill_preserve()
        ctx.save()
        ctx.clip()
        ellipse_path(ctx, cx + w * 0.08, lo + h * 0.05, w * 0.36, h * 0.45)
        src(ctx, TONGUE)
        ctx.fill()
        ctx.restore()
        path()
        src(ctx, INK)
        ctx.set_line_width(lw)
        ctx.stroke()
        if buck_teeth:
            buck_teeth(up + 1)
    ctx.restore()


def draw_sweat(ctx, x, y, amt, lw):
    if amt <= 0.01:
        return
    ctx.save()
    ctx.translate(x, y + (1 - amt) * -10)
    s = 0.5 + 0.5 * amt
    ctx.scale(s, s)
    ctx.move_to(0, -26)
    ctx.curve_to(6, -12, 14, 0, 0, 10)
    ctx.curve_to(-14, 0, -6, -12, 0, -26)
    ctx.close_path()
    ctx.set_source_rgba(0.55, 0.8, 1.0, min(1, amt * 1.5))
    ctx.fill_preserve()
    ctx.set_source_rgba(*INK, min(1, amt * 1.5))
    ctx.set_line_width(lw * 0.8)
    ctx.stroke()
    ctx.restore()


def draw_tail(ctx, base, t, wag, length, lw, seed):
    x0, y0 = base
    pts = []
    n = 14
    for i in range(n + 1):
        u = i / n
        a = math.pi * 0.95 + u * 1.6 + math.sin(t * 2.2 + u * 3 + seed) * 0.25 * wag * u
        pts.append((x0 + math.cos(a) * u * length * 0.55 - u * length * 0.35,
                    y0 + u * u * 60 - math.sin(u * math.pi) * 25 + math.sin(a) * u * 8))
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.move_to(*pts[0])
    for q in pts[1:]:
        ctx.line_to(*q)
    src(ctx, INK)
    ctx.set_line_width(6 + 2 * lw)
    ctx.stroke_preserve()
    src(ctx, PINK)
    ctx.set_line_width(6)
    ctx.stroke()
    ctx.restore()


def ground_shadow(ctx, rx, alpha):
    ctx.save()
    ctx.scale(1, 0.18)
    g = cairo.RadialGradient(0, 0, 0, 0, 0, rx)
    g.add_color_stop_rgba(0, 0.05, 0.02, 0.08, 0.45 * alpha)
    g.add_color_stop_rgba(1, 0.05, 0.02, 0.08, 0.0)
    ctx.set_source(g)
    ctx.arc(0, 0, rx, 0, 2 * math.pi)
    ctx.fill()
    ctx.restore()


def legs(ctx, hip_l, hip_r, foot_dx, leg_len, p, foot_len, width, lw, t):
    """Two legs with a simple walk cycle; returns nothing."""
    ph = p["walk_phase"]
    w = p["walk"]
    for side, hip, phase_off in (("b", hip_l, math.pi), ("f", hip_r, 0.0)):
        a = ph + phase_off
        swing = math.sin(a) * 34 * w * p["stride"]
        lift = max(0.0, math.cos(a)) * 22 * w
        fx = hip[0] + swing + (foot_dx if side == "f" else -foot_dx * 0.6)
        fy = -lift
        kx = (hip[0] + fx) / 2 + 10
        ky = (hip[1] + fy) / 2 - 4
        col = WHITE if side == "f" else mix(WHITE, FUR_SHADE, 0.45)
        limb(ctx, [hip, (kx, ky), (fx, fy - 6)], width, col, lw)
        # foot
        def foot(c, fx=fx, fy=fy):
            ellipse_path(c, fx + foot_len * 0.45, fy - 4, foot_len * 0.62, 8.5,
                         -0.08 + (0.25 * w * math.sin(a) if w else 0))
        toon(ctx, foot, PINK if side == "f" else PINK_SHADE, PINK_SHADE, shade_amt=3, lw=lw)


# ---------------------------------------------------------------------------
# BRAIN
# ---------------------------------------------------------------------------
def draw_brain(ctx, s, t, prefix="brain."):
    p = read_params(s, prefix, BRAIN_DEFAULTS)
    if p["visible"] <= 0:
        return
    lw = 4.2
    fac = 1.0 if p["facing"] >= 0 else -1.0
    light = (-0.45 * fac, -0.85)
    breath = math.sin(t * 2.1) * 1.6 * p["breathe"]

    ctx.save()
    ctx.translate(p["x"], p["y"])
    ctx.scale(p["scale"] * fac, p["scale"])
    if p["shadow"] > 0:
        ground_shadow(ctx, 95, p["shadow"])
    ctx.translate(0, p["bob"])
    ctx.scale(1.0 / p["squash"] ** 0.5, p["squash"])

    # tail + legs (pivot is feet)
    draw_tail(ctx, (-38, -62), t, 1.0, 190, lw, 1.3)
    legs(ctx, (-20, -62), (18, -62), 8, 60, p, 34, 13, lw, t)

    # upper body leans around the hips
    ctx.save()
    ctx.translate(0, -62)
    ctx.rotate(p["lean"])
    ctx.translate(0, 62)

    sh_f = (24, -146 + breath * 0.5)
    sh_b = (-30, -150 + breath * 0.5)

    # back arm
    (a, e, h, ha) = arm_points(*sh_b, p["arm_b"][0], p["arm_b"][1], 44, 40)
    limb(ctx, [a, e, h], 12, mix(WHITE, FUR_SHADE, 0.45), lw)
    draw_hand(ctx, *h, ha, p["hand_b"], 10, lw * 0.85)

    # body
    def body(c):
        ellipse_path(c, 0, -108 - breath * 0.3, 52, 60 + breath * 0.4)
        ellipse_path(c, 4, -82, 46, 36)
    toon(ctx, body, WHITE, FUR_SHADE, light, shade_amt=16, lw=lw)
    # belly line
    ctx.save()
    ctx.move_to(-10, -70)
    ctx.curve_to(4, -62, 22, -64, 34, -74)
    src(ctx, mix(FUR_SHADE, INK, 0.25))
    ctx.set_line_width(2.4)
    ctx.stroke()
    ctx.restore()

    # head
    ctx.save()
    tb = p["talk"]
    ctx.translate(0 + p["head_dx"], -168 + breath * 0.6 + p["head_dy"] - 3.5 * tb)
    ctx.rotate(p["head_tilt"] + 0.025 * tb * math.sin(t * 6.5))
    _brain_head(ctx, p, t, lw, light)
    ctx.restore()

    # front arm (+ optional pointer stick)
    (a, e, h, ha) = arm_points(*sh_f, p["arm_f"][0], p["arm_f"][1], 44, 40)
    if p["pointer"] > 0.5:
        ctx.save()
        tipx = h[0] + math.sin(ha) * 290
        tipy = h[1] + math.cos(ha) * 290
        ctx.set_line_cap(cairo.LINE_CAP_ROUND)
        ctx.move_to(h[0] - math.sin(ha) * 18, h[1] - math.cos(ha) * 18)
        ctx.line_to(tipx, tipy)
        src(ctx, INK)
        ctx.set_line_width(9 + 2 * lw)
        ctx.stroke_preserve()
        ctx.set_source_rgb(0.72, 0.47, 0.25)
        ctx.set_line_width(9)
        ctx.stroke()
        ctx.arc(tipx, tipy, 6.5, 0, 2 * math.pi)
        ctx.set_source_rgb(0.85, 0.15, 0.15)
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(3)
        ctx.stroke()
        ctx.restore()
    limb(ctx, [a, e, h], 12, WHITE, lw)
    draw_hand(ctx, *h, ha, "fist" if p["pointer"] > 0.5 else p["hand_f"], 10.5, lw * 0.85)

    ctx.restore()  # lean
    ctx.restore()


def _brain_head(ctx, p, t, lw, light):
    """Head drawn around the neck pivot (0, 0); head centre ~ (0, -128)."""
    turn = p["head_turn"]
    hx, hy = 6, -128
    # ears (behind the head)
    for (ex, ey, r, rot, col_in) in ((-98 + turn * 18, -78, 60, -0.45, PINK_SHADE),
                                     (86 + turn * 22, -86, 64, 0.35, PINK)):
        def ear(c, ex=ex, ey=ey, r=r, rot=rot):
            ellipse_path(c, hx + ex, hy + ey, r, r * 1.05, rot)
        toon(ctx, ear, WHITE, FUR_SHADE, light, shade_amt=10, lw=lw)
        ellipse_path(ctx, hx + ex * 1.04 + turn * 6, hy + ey + 6, r * 0.7, r * 0.78, rot)
        src(ctx, col_in)
        ctx.fill()

    mx = hx + 28 + turn * 34     # muzzle centre
    my = hy + 74

    def head(c):
        ellipse_path(c, hx, hy - 6, 132, 104)          # cranium
        ellipse_path(c, hx + turn * 10, hy + 30, 112, 70)  # cheeks
        ellipse_path(c, mx, my, 60, 42, 0.05)           # muzzle
    toon(ctx, head, WHITE, FUR_SHADE, light, shade_amt=18, lw=lw)

    # cranium creases: the giant brain bulging under the fur
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    src(ctx, mix(FUR_SHADE, INK, 0.35))
    ctx.set_line_width(3.0)
    ctx.move_to(hx - 70 + turn * 20, hy - 70)
    ctx.curve_to(hx - 40 + turn * 20, hy - 92, hx + 10 + turn * 20, hy - 96, hx + 40 + turn * 20, hy - 82)
    ctx.stroke()
    ctx.move_to(hx - 52 + turn * 25, hy - 52)
    ctx.curve_to(hx - 30 + turn * 25, hy - 62, hx - 5 + turn * 25, hy - 64, hx + 12 + turn * 25, hy - 58)
    ctx.stroke()
    ctx.restore()

    # eyes
    e_y = hy + 16
    far = (hx - 26 + turn * 30, e_y)
    near = (hx + 36 + turn * 32, e_y)
    draw_eye(ctx, *far, 20, 27, p, IRIS_BRAIN, 11, p["lid"], t, 3, heavy=True, lw=lw * 0.9)
    draw_eye(ctx, *near, 24, 29, p, IRIS_BRAIN, 12.5, p["lid"], t, 7, heavy=True, lw=lw * 0.9)
    # brows
    draw_brow(ctx, far[0], far[1] - 36, 20, p["brow_l"], p["brow_tilt"], +1, 8.5, lw)
    draw_brow(ctx, near[0], near[1] - 38, 23, p["brow_r"], p["brow_tilt"], -1, 9.0, lw)

    # nose + mouth
    nx, ny = mx + 40, my - 4
    def nose(c):
        ellipse_path(c, nx, ny, 15, 12, 0.2)
    toon(ctx, nose, NOSE_RED, NOSE_SHADE, light, shade_amt=4, lw=lw * 0.9)
    ellipse_path(ctx, nx - 5, ny - 5, 4, 3)
    ctx.set_source_rgba(1, 1, 1, 0.85)
    ctx.fill()
    # whisker dots
    for (dx, dy) in ((-8, 14), (2, 18), (-18, 18)):
        ctx.arc(mx + 18 + dx, my + dy - 8, 2.0, 0, 2 * math.pi)
        src(ctx, mix(FUR_SHADE, INK, 0.4))
        ctx.fill()
    draw_mouth(ctx, mx + 14, my + 22, 54, p, depth=36, lw=lw * 0.9)
    draw_sweat(ctx, hx - 108 + turn * 10, hy - 20, p["sweat"], lw)


# ---------------------------------------------------------------------------
# PINKY
# ---------------------------------------------------------------------------
def draw_pinky(ctx, s, t, prefix="pinky."):
    p = read_params(s, prefix, PINKY_DEFAULTS)
    if p["visible"] <= 0:
        return
    lw = 4.0
    fac = 1.0 if p["facing"] >= 0 else -1.0
    light = (-0.45 * fac, -0.85)
    breath = math.sin(t * 2.4 + 1.0) * 1.8 * p["breathe"]
    walkbob = -abs(math.sin(p["walk_phase"])) * 14 * p["walk"]

    ctx.save()
    ctx.translate(p["x"], p["y"])
    ctx.scale(p["scale"] * fac, p["scale"])
    if p["shadow"] > 0:
        ground_shadow(ctx, 80, p["shadow"])
    ctx.translate(0, p["bob"])
    ctx.scale(1.0 / p["squash"] ** 0.5, p["squash"])

    draw_tail(ctx, (-30, -150 + walkbob), t + 0.7, 1.4, 230, lw, 2.1)
    ctx.save()
    ctx.translate(0, walkbob)
    legs(ctx, (-14, -160), (12, -160), 10, 150, p, 46, 11, lw, t)
    ctx.restore()

    ctx.save()
    ctx.translate(0, -160 + walkbob)
    ctx.rotate(p["lean"])
    ctx.translate(0, 160)

    sh_f = (18, -318 + breath * 0.5)
    sh_b = (-22, -322 + breath * 0.5)
    arm_b = p["arm_b"]
    (a, e, h, ha) = arm_points(*sh_b, arm_b[0], arm_b[1], PINKY_ARM[2], PINKY_ARM[3])
    limb(ctx, [a, e, h], 10, mix(WHITE, FUR_SHADE, 0.45), lw)
    draw_hand(ctx, *h, ha, p["hand_b"], 10, lw * 0.85)

    tb = p["talk"]
    hdx, hdy = 6 + p["head_dx"], -330 + breath * 0.6 + p["head_dy"] - 3.5 * tb
    nx2 = hdx + math.sin(p["head_tilt"]) * 30
    ny2 = hdy - math.cos(p["head_tilt"]) * 30
    limb(ctx, [(0, -300), ((hdx) * 0.5, (hdy - 300) * 0.5 - 6), (nx2, ny2)], 22, WHITE, lw)

    def body(c):
        ellipse_path(c, 0, -246 - breath * 0.3, 40, 96 + breath * 0.4)
        ellipse_path(c, 3, -196, 36, 46)
    toon(ctx, body, WHITE, FUR_SHADE, light, shade_amt=14, lw=lw)

    # neck + head
    tb = p["talk"]
    hdx, hdy = 6 + p["head_dx"], -330 + breath * 0.6 + p["head_dy"] - 3.5 * tb
    nx2 = hdx + math.sin(p["head_tilt"]) * 30
    ny2 = hdy - math.cos(p["head_tilt"]) * 30
    ctx.save()
    ctx.translate(hdx, hdy)
    ctx.rotate(p["head_tilt"] + 0.03 * tb * math.sin(t * 7.0 + 1))
    _pinky_head(ctx, p, t, lw, light)
    ctx.restore()

    # front arm; scratching jiggles the elbow
    arm_f = p["arm_f"]
    a1, a2 = arm_f
    if p["scratch"] > 0:
        a2 += math.sin(t * 38) * 0.22 * p["scratch"]
    (a, e, h, ha) = arm_points(*sh_f, a1, a2, PINKY_ARM[2], PINKY_ARM[3])
    limb(ctx, [a, e, h], 10, WHITE, lw)
    draw_hand(ctx, *h, ha, p["hand_f"], 10.5, lw * 0.85)

    ctx.restore()
    ctx.restore()


def _pinky_head(ctx, p, t, lw, light):
    turn = p["head_turn"]
    hx, hy = 8, -78
    for (ex, ey, r, rot, col_in) in ((-58 + turn * 10, -64, 54, -0.55, PINK_SHADE),
                                     (40 + turn * 18, -78, 58, 0.25, PINK)):
        def ear(c, ex=ex, ey=ey, r=r, rot=rot):
            ellipse_path(c, hx + ex, hy + ey, r * 0.92, r * 1.08, rot)
        toon(ctx, ear, WHITE, FUR_SHADE, light, shade_amt=9, lw=lw)
        ellipse_path(ctx, hx + ex * 1.05 + turn * 5, hy + ey + 6, r * 0.66, r * 0.82, rot)
        src(ctx, col_in)
        ctx.fill()

    sx, sy = hx + 58 + turn * 22, hy + 30   # snout centre

    def head(c):
        ellipse_path(c, hx, hy - 4, 70, 66)
        ellipse_path(c, sx, sy, 64, 33, 0.28)
        ellipse_path(c, hx + 10, hy + 28, 50, 34)
    toon(ctx, head, WHITE, FUR_SHADE, light, shade_amt=14, lw=lw)

    # big eyes
    e_y = hy - 14
    far = (hx - 16 + turn * 24, e_y)
    near = (hx + 34 + turn * 26, e_y - 2)
    draw_eye(ctx, *far, 22, 31, p, IRIS_PINKY, 14, p["lid"], t, 11, lw=lw * 0.9)
    draw_eye(ctx, *near, 26, 34, p, IRIS_PINKY, 15.5, p["lid"], t, 13, lw=lw * 0.9)
    draw_brow(ctx, far[0], far[1] - 42, 16, p["brow_l"], p["brow_tilt"], +1, 5.0, lw)
    draw_brow(ctx, near[0], near[1] - 45, 18, p["brow_r"], p["brow_tilt"], -1, 5.5, lw)

    # nose at the tip of the snout
    nx, ny = sx + 60, sy + 14
    def nose(c):
        ellipse_path(c, nx, ny, 18, 15, 0.2)
    toon(ctx, nose, NOSE_RED, NOSE_SHADE, light, shade_amt=5, lw=lw * 0.9)
    ellipse_path(ctx, nx - 6, ny - 6, 5, 3.5)
    ctx.set_source_rgba(1, 1, 1, 0.85)
    ctx.fill()

    mx, my = sx + 18, sy + 36

    def teeth(ytop):
        if p["teeth"] <= 0:
            return
        for dx in (-7.5, 4.5):
            ctx.rectangle(mx + 8 + dx, ytop, 11, 15)
        src(ctx, (1, 1, 0.97))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(lw * 0.7)
        ctx.stroke()
    draw_mouth(ctx, mx, my, 54, p, depth=38, lw=lw * 0.9, buck_teeth=teeth)
    draw_sweat(ctx, hx - 70, hy - 10, p["sweat"], lw)
