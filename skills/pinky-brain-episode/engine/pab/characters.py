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
                   arm_points, blob_path, ellipse_path, limb, src, toon, mix)

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


def legs(ctx, hip_l, hip_r, foot_dx, leg_len, p, foot_len, width, lw, t, pink_shins=False):
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
        if pink_shins:
            ax, ay = kx + (fx - kx) * 0.45, ky + (fy - 6 - ky) * 0.45
            limb(ctx, [(ax, ay), ((ax + fx) / 2, (ay + fy) / 2), (fx, fy - 6)], width * 0.85,
                 PINK if side == "f" else PINK_SHADE, lw)
            limb(ctx, [hip, (kx, ky), (ax, ay)], width, col, lw)
        else:
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


def leaf_path(c, cx, cy, w, h, rot, point=0.12):
    """Big cartoon ear: rounded leaf with a soft point at the tip (tip = -y)."""
    c.save()
    c.translate(cx, cy)
    c.rotate(rot)
    c.move_to(0, h * 0.5)
    c.curve_to(w * 0.66, h * 0.42, w * 0.62, -h * 0.30, w * point, -h * 0.5)
    c.curve_to(-w * 0.12, -h * 0.58, -w * 0.66, -h * 0.30, -w * 0.62, h * 0.05)
    c.curve_to(-w * 0.58, h * 0.36, -w * 0.25, h * 0.48, 0, h * 0.5)
    c.close_path()
    c.restore()


def almond_eye(ctx, cx, cy, rx, ry, rot, p, t, seed, sclera, lw, iris=None, iris_r=0.0,
               pupil_r=5.5, look_scale=(0.45, 0.35)):
    """Almond-shaped eye with heavy fur lid (Brain) — inner corner given by rot."""
    lx, ly = p["look"]
    base = p["lid"] * 0.8
    lid = min(1.0, base + p["blink"] * (1.0 - base))
    if p["twitch"] > 0:
        lid = min(1.0, max(0.0, lid + p["twitch"] * 0.15 * noise1(t * 22.0, seed)))
    low = min(1.0 - lid, p["lid_low"])

    def path(c):
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        c.move_to(-rx, 0)
        c.curve_to(-rx * 0.55, -ry * 1.35, rx * 0.55, -ry * 1.35, rx, 0)
        c.curve_to(rx * 0.55, ry * 1.3, -rx * 0.55, ry * 1.3, -rx, 0)
        c.close_path()
        c.restore()

    ctx.save()
    path(ctx)
    src(ctx, sclera)
    ctx.fill_preserve()
    ctx.save()
    ctx.clip()
    px = cx + lx * rx * look_scale[0]
    py = cy + ly * ry * look_scale[1] + ry * 0.12
    if iris is not None:
        ellipse_path(ctx, px, py, iris_r * 0.9, iris_r)
        src(ctx, iris)
        ctx.fill()
    pr = pupil_r * p["pupil"]
    ellipse_path(ctx, px, py, pr * 0.85, pr)
    src(ctx, INK)
    ctx.fill()
    if iris is not None and p["pupil"] > 0.5:
        ellipse_path(ctx, px - pr * 0.35, py - pr * 0.45, pr * 0.3, pr * 0.3)
        src(ctx, (1, 1, 1))
        ctx.fill()
    top, bot = cy - ry * 1.05, cy + ry * 1.0
    if lid > 0.01:
        edge = top + (bot - top) * lid
        ctx.save()
        ctx.translate(cx, edge)
        ctx.rotate(rot)
        ctx.move_to(-rx * 1.6, -ry * 3)
        ctx.line_to(rx * 1.6, -ry * 3)
        ctx.line_to(rx * 1.6, -ry * 0.1)
        ctx.curve_to(rx * 0.5, ry * 0.18, -rx * 0.5, ry * 0.18, -rx * 1.6, -ry * 0.1)
        ctx.close_path()
        src(ctx, BRAIN_LID)
        ctx.fill()
        if lid < 0.97:
            ctx.move_to(-rx * 1.3, -ry * 0.06)
            ctx.curve_to(-rx * 0.5, ry * 0.2, rx * 0.5, ry * 0.2, rx * 1.3, -ry * 0.06)
            src(ctx, INK)
            ctx.set_line_width(lw * 1.25)
            ctx.stroke()
        ctx.restore()
    if low > 0.01:
        ledge = bot - (bot - top) * low
        ctx.save()
        ctx.translate(cx, ledge)
        ctx.rotate(rot)
        ctx.move_to(-rx * 1.6, ry * 3)
        ctx.line_to(-rx * 1.6, 0)
        ctx.curve_to(-rx * 0.5, -ry * 0.15, rx * 0.5, -ry * 0.15, rx * 1.6, 0)
        ctx.line_to(rx * 1.6, ry * 3)
        ctx.close_path()
        src(ctx, BRAIN_LID)
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(lw * 0.7)
        ctx.stroke()
        ctx.restore()
    ctx.restore()
    path(ctx)
    src(ctx, INK)
    ctx.set_line_width(lw)
    ctx.stroke()
    ctx.restore()


BRAIN_EAR_IN = (0.74, 0.30, 0.38)
BRAIN_LID = (0.95, 0.93, 0.97)
BRAIN_SCLERA = (0.93, 0.56, 0.62)


def _brain_head(ctx, p, t, lw, light):
    """Brain's head (front-ish view), drawn around the neck pivot (0, 0).

    Model notes from the reference: huge dome with an overhanging brow fold,
    the outline pinches at the temples and puffs out again at the cheeks,
    small round chin; almond pink eyes under heavy lids, close together;
    a big red nose between and below the eyes; creases from the nose down to
    a small pouty mouth; enormous leaf-shaped ears high on the sides.
    """
    turn = p["head_turn"] - 0.15
    hx, hy = 0.0, -112.0
    f = turn * 26.0            # facial features slide toward the facing side
    sq = 1.0

    # ears (behind the head)
    for (ex, ey, w, h, rot, col_in, k) in ((-152 + turn * 12, -128, 140, 205, -0.62, BRAIN_EAR_IN, 0.95),
                                           (152 + turn * 16, -130, 144, 210, 0.62, BRAIN_EAR_IN, 1.0)):
        def ear(c, ex=ex, ey=ey, w=w, h=h, rot=rot):
            leaf_path(c, hx + ex, hy + ey, w, h, rot)
        toon(ctx, ear, WHITE, FUR_SHADE, light, shade_amt=10, lw=lw)
        sgn = 1 if ex > 0 else -1
        leaf_path(ctx, hx + ex + sgn * 8, hy + ey + 8, w * 0.74, h * 0.80, rot)
        src(ctx, mix(col_in, (0.55, 0.2, 0.28), 0.0 if k == 1.0 else 0.25))
        ctx.fill()

    pts = [(0, -140), (76, -128), (124, -92), (140, -36), (126, 6), (116, 26),
           (124, 56), (114, 90), (82, 114), (40 + f * 0.4, 130), (f * 0.4, 135),
           (-40 + f * 0.4, 130), (-82, 114), (-114, 90), (-124, 56), (-116, 26),
           (-126, 6), (-140, -36), (-124, -92), (-76, -128)]
    pts = [(hx + x, hy + y * sq) for x, y in pts]

    def head(c):
        blob_path(c, pts, tension=0.55)
    toon(ctx, head, WHITE, FUR_SHADE, light, shade_amt=20, lw=lw)

    crease = mix(FUR_SHADE, INK, 0.45)
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    # the overhanging forehead fold (moves with the brows)
    bl, br, tilt = p["brow_l"], p["brow_r"], p["brow_tilt"]
    yl = hy - 30 - bl * 13
    yr = hy - 30 - br * 13
    yc = hy - 16 + tilt * 10 - (bl + br) * 4
    ctx.move_to(hx - 112 + f * 0.3, hy - 12 - bl * 6)
    ctx.curve_to(hx - 90 + f, yl - 14, hx - 40 + f, yl - 18, hx - 14 + f, yc)
    ctx.curve_to(hx - 6 + f, yc + 4, hx + 6 + f, yc + 4, hx + 14 + f, yc)
    ctx.curve_to(hx + 40 + f, yr - 18, hx + 90 + f, yr - 14, hx + 112 + f * 0.3, hy - 12 - br * 6)
    src(ctx, INK)
    ctx.set_line_width(lw * 0.9)
    ctx.stroke()
    # soft shadow under the fold
    ctx.move_to(hx - 96 + f, yl - 2)
    ctx.curve_to(hx - 60 + f, yl - 12, hx - 30 + f, yl - 8, hx - 16 + f, yc + 8)
    ctx.move_to(hx + 96 + f, yr - 2)
    ctx.curve_to(hx + 60 + f, yr - 12, hx + 30 + f, yr - 8, hx + 16 + f, yc + 8)
    ctx.set_source_rgba(*FUR_SHADE, 0.9)
    ctx.set_line_width(7)
    ctx.stroke()
    ctx.restore()

    # eyes: almond, inner corners low (grumpy by default via brow_tilt)
    e_y = hy + 4
    erot = 0.10 + 0.22 * tilt
    almond_eye(ctx, hx - 45 + f, e_y, 36, 19, erot, p, t, 3, BRAIN_SCLERA, lw * 0.85)
    almond_eye(ctx, hx + 45 + f, e_y, 36, 19, -erot, p, t, 7, BRAIN_SCLERA, lw * 0.85)
    # bags under the eyes
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    src(ctx, crease)
    ctx.set_line_width(2.4)
    for sgn in (-1, 1):
        ex = hx + sgn * 44 + f
        ctx.move_to(ex - 26, e_y + 24)
        ctx.curve_to(ex - 10, e_y + 31, ex + 10, e_y + 31, ex + 24, e_y + 24)
        ctx.stroke()
    ctx.restore()

    # nose: big red ball between and below the eyes
    nx, ny = hx + f * 1.1, hy + 34

    def nose(c):
        ellipse_path(c, nx, ny, 25, 19)
    toon(ctx, nose, NOSE_RED, NOSE_SHADE, light, shade_amt=5, lw=lw * 0.9)
    ellipse_path(ctx, nx - 8, ny - 7, 6, 4)
    ctx.set_source_rgba(1, 1, 1, 0.8)
    ctx.fill()

    # creases: philtrum + smile lines down to the mouth
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    src(ctx, crease)
    ctx.set_line_width(2.6)
    ctx.move_to(nx, ny + 20)
    ctx.line_to(nx + 1, ny + 44)
    ctx.stroke()
    for sgn in (-1, 1):
        ctx.move_to(nx + sgn * 20, ny + 14)
        ctx.curve_to(nx + sgn * 30, ny + 30, nx + sgn * 32, ny + 48, nx + sgn * 26, ny + 62)
        ctx.stroke()
    ctx.restore()

    # small pouty mouth + chin pout
    mx, my = nx + 1, ny + 58
    draw_mouth(ctx, mx, my, 40, p, depth=30, lw=lw * 0.9)
    if p["mouth_open"] + p["jaw"] < 0.06:
        ctx.save()
        ctx.set_line_cap(cairo.LINE_CAP_ROUND)
        ctx.move_to(mx - 10, my + 13)
        ctx.curve_to(mx - 4, my + 18, mx + 4, my + 18, mx + 10, my + 13)
        src(ctx, crease)
        ctx.set_line_width(2.4)
        ctx.stroke()
        ctx.restore()
    draw_sweat(ctx, hx - 112 + f * 0.3, hy - 4, p["sweat"], lw)


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
    legs(ctx, (-14, -160), (14, -160), 14, 150, p, 62, 11, lw, t, pink_shins=True)
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
    draw_hand(ctx, *h, ha, p["hand_b"], 13, lw * 0.85)

    tb = p["talk"]
    hdx, hdy = 4 + p["head_dx"], -334 + breath * 0.6 + p["head_dy"] - 3.5 * tb
    nx2 = hdx + math.sin(p["head_tilt"]) * 6
    ny2 = hdy - math.cos(p["head_tilt"]) * 6
    limb(ctx, [(0, -310), ((hdx) * 0.5, (hdy - 310) * 0.5 - 4), (nx2, ny2 + 12)], 18, WHITE, lw)

    def body(c):
        ellipse_path(c, 2, -282 - breath * 0.3, 32, 56 + breath * 0.3)    # narrow chest
        ellipse_path(c, 14, -206, 54, 62 + breath * 0.3)                 # pot belly
        ellipse_path(c, 0, -176, 40, 30)
    toon(ctx, body, WHITE, FUR_SHADE, light, shade_amt=14, lw=lw)

    # neck + head
    tb = p["talk"]
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
    draw_hand(ctx, *h, ha, p["hand_f"], 13.5, lw * 0.85)

    ctx.restore()
    ctx.restore()


PINKY_EAR_IN = (0.88, 0.47, 0.57)
IRIS_PINKY_REF = (0.62, 0.83, 0.96)


def _pinky_head(ctx, p, t, lw, light):
    """Pinky's head (3/4 profile), drawn around the neck pivot.

    Model notes from the reference: small round cranium, long snout tapering
    forward and slightly down to a big red ball nose; huge bulging eyes with
    pale-blue irises sitting ON TOP of the head line; tall pointed ears set
    back; a little tuft of hair; wide mouth under the snout with two buck teeth.
    """
    turn = p["head_turn"]
    hx, hy = 0.0, 0.0
    f = turn * 10.0

    # ears, tall and pointed, behind the head
    for (ex, ey, w, h, rot, col_in) in ((-34 + f * 0.5, -158, 74, 132, -0.32, mix(PINKY_EAR_IN, (0.6, 0.25, 0.35), 0.2)),
                                        (6 + f, -166, 80, 142, 0.12, PINKY_EAR_IN)):
        def ear(c, ex=ex, ey=ey, w=w, h=h, rot=rot):
            leaf_path(c, hx + ex, hy + ey, w, h, rot, point=0.18)
        toon(ctx, ear, WHITE, FUR_SHADE, light, shade_amt=8, lw=lw)
        leaf_path(ctx, hx + ex + 6, hy + ey + 8, w * 0.68, h * 0.8, rot, point=0.18)
        src(ctx, col_in)
        ctx.fill()

    pts = [(-8, -122), (30, -114), (62, -96), (96, -76), (126, -60), (142, -46),
           (132, -28), (104, -20), (72, -12), (40, -4), (8, -6), (-26, -20),
           (-50, -50), (-52, -90), (-36, -114)]
    pts = [(hx + x, hy + y) for x, y in pts]

    def head(c):
        blob_path(c, pts, tension=0.55)
    toon(ctx, head, WHITE, FUR_SHADE, light, shade_amt=12, lw=lw)

    # tuft of hair
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    src(ctx, INK)
    ctx.set_line_width(lw * 0.7)
    for (a, b) in ((-6, -28), (0, -36), (8, -26)):
        ctx.move_to(hx + a - 4, hy - 120)
        ctx.curve_to(hx + a - 2, hy - 126, hx + a + 4, hy + b - 100, hx + a + 10, hy + b - 102)
        ctx.stroke()
    ctx.restore()

    # mouth along the underside of the snout
    mx, my = hx + 92, hy - 20
    tx = mx + 20

    def teeth(ytop):
        if p["teeth"] <= 0:
            return
        for dx in (-7.0, 5.0):
            ctx.rectangle(tx + dx, ytop, 11, 15)
        src(ctx, (1, 1, 0.97))
        ctx.fill_preserve()
        src(ctx, INK)
        ctx.set_line_width(lw * 0.7)
        ctx.stroke()
    ctx.save()
    ctx.translate(mx, my)
    ctx.rotate(0.22)
    ctx.translate(-mx, -my)
    draw_mouth(ctx, mx, my, 66, p, depth=40, lw=lw * 0.9, buck_teeth=teeth)
    ctx.restore()

    # nose: big red ball on the tip of the snout
    nx, ny = hx + 140, hy - 46

    def nose(c):
        ellipse_path(c, nx, ny, 22, 19, 0.2)
    toon(ctx, nose, NOSE_RED, NOSE_SHADE, light, shade_amt=5, lw=lw * 0.9)
    ellipse_path(ctx, nx - 8, ny - 8, 7, 4.5, -0.4)
    ctx.set_source_rgba(1, 1, 1, 0.85)
    ctx.fill()

    # bulging eyes on top of the head line, with thin brows above
    far = (hx + 22 + f, hy - 114)
    near = (hx + 56 + f * 1.2, hy - 104)
    draw_eye(ctx, *far, 21, 28, p, IRIS_PINKY_REF, 15, p["lid"], t, 11, lw=lw * 0.85)
    draw_eye(ctx, *near, 24, 31, p, IRIS_PINKY_REF, 17, p["lid"], t, 13, lw=lw * 0.85)
    draw_brow(ctx, far[0] - 2, far[1] - 38, 13, p["brow_l"], p["brow_tilt"], +1, 4.0, lw)
    draw_brow(ctx, near[0] + 2, near[1] - 42, 15, p["brow_r"], p["brow_tilt"], -1, 4.5, lw)
    draw_sweat(ctx, hx - 52, hy - 70, p["sweat"], lw)
