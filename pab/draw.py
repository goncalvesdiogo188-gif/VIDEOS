"""Cairo drawing helpers: cartoon outlines, cel shading, colours."""
from __future__ import annotations

import math

import cairo

INK = (0.13, 0.08, 0.16)          # outline colour (dark aubergine, 90s ink)
WHITE = (0.98, 0.97, 0.99)
FUR_SHADE = (0.74, 0.72, 0.86)   # cool lilac shade on white fur
PINK = (0.95, 0.58, 0.66)
PINK_SHADE = (0.80, 0.40, 0.50)
NOSE_RED = (0.90, 0.10, 0.14)
NOSE_SHADE = (0.62, 0.05, 0.12)


def hexc(h: str, a: float | None = None):
    h = h.lstrip("#")
    if len(h) == 3:
        h = "".join(ch * 2 for ch in h)
    c = tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return c if a is None else (*c, a)


def mix(c1, c2, u):
    return tuple(a + (b - a) * u for a, b in zip(c1, c2))


def src(ctx, c):
    if len(c) == 4:
        ctx.set_source_rgba(*c)
    else:
        ctx.set_source_rgb(*c)


def ellipse_path(ctx, cx, cy, rx, ry, rot=0.0):
    ctx.save()
    ctx.translate(cx, cy)
    if rot:
        ctx.rotate(rot)
    ctx.scale(max(rx, 1e-3), max(ry, 1e-3))
    ctx.new_sub_path()
    ctx.arc(0, 0, 1, 0, 2 * math.pi)
    ctx.restore()


def blob_path(ctx, pts, closed=True, tension=0.5):
    """Smooth Catmull-Rom spline through points, emitted as cubic beziers."""
    n = len(pts)
    if n < 2:
        return
    ctx.move_to(*pts[0])
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        p0 = pts[(i - 1) % n] if closed or i > 0 else pts[i]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n] if closed or i + 2 < n else pts[(i + 1) % n]
        k = tension / 3 * 2
        c1 = (p1[0] + (p2[0] - p0[0]) * k / 2, p1[1] + (p2[1] - p0[1]) * k / 2)
        c2 = (p2[0] - (p3[0] - p1[0]) * k / 2, p2[1] - (p3[1] - p1[1]) * k / 2)
        ctx.curve_to(*c1, *c2, *p2)
    if closed:
        ctx.close_path()


def toon(ctx, path_fn, base, shade=None, light=(-0.5, -0.8), shade_amt=14.0,
         lw=4.0, ink=INK, union=True):
    """Draw a filled shape with cel shading and an ink outline.

    path_fn(ctx) must emit the path (several overlapping sub-paths are
    allowed: they are merged into one silhouette, outlined only on the outside).
    Shading: fill whole shape with `shade`, then clip to the shape and fill a
    copy offset toward the light in `base`.
    """
    ctx.save()
    ctx.set_fill_rule(cairo.FILL_RULE_WINDING)
    ctx.new_path()
    path_fn(ctx)
    if union and lw > 0:
        # outline drawn first at double width; the fill then covers the inner
        # half, so overlapping parts merge into a single clean silhouette.
        src(ctx, ink)
        ctx.set_line_width(lw * 2)
        ctx.set_line_join(cairo.LINE_JOIN_ROUND)
        ctx.stroke_preserve()
        lw = 0
    if shade is not None:
        src(ctx, shade)
        ctx.fill_preserve()
        ctx.save()
        ctx.clip()
        ctx.new_path()
        ctx.translate(light[0] * shade_amt, light[1] * shade_amt)
        path_fn(ctx)
        src(ctx, base)
        ctx.fill()
        ctx.restore()
        ctx.new_path()
        path_fn(ctx)
    else:
        src(ctx, base)
        ctx.fill_preserve()
    if lw > 0:
        src(ctx, ink)
        ctx.set_line_width(lw)
        ctx.set_line_join(cairo.LINE_JOIN_ROUND)
        ctx.stroke()
    ctx.new_path()
    ctx.restore()


def limb(ctx, pts, width, color=WHITE, lw=4.0, ink=INK, cap=True):
    """Thick tube limb through points with an ink outline (stroke twice)."""
    ctx.save()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.new_path()
    if len(pts) == 3:
        ctx.move_to(*pts[0])
        # gentle curve through the joint
        ctx.curve_to(*pts[1], *pts[1], *pts[2])
    else:
        ctx.move_to(*pts[0])
        for p in pts[1:]:
            ctx.line_to(*p)
    src(ctx, ink)
    ctx.set_line_width(width + 2 * lw)
    ctx.stroke_preserve()
    src(ctx, color)
    ctx.set_line_width(width)
    ctx.stroke()
    ctx.restore()


def polar(cx, cy, ang, r):
    return cx + math.cos(ang) * r, cy + math.sin(ang) * r


def arm_points(sx, sy, a1, a2, l1, l2):
    """Two-segment arm from shoulder angles (radians, 0 = pointing down)."""
    ex = sx + math.sin(a1) * l1
    ey = sy + math.cos(a1) * l1
    hx = ex + math.sin(a1 + a2) * l2
    hy = ey + math.cos(a1 + a2) * l2
    return (sx, sy), (ex, ey), (hx, hy), a1 + a2


def text_center(ctx, txt, x, y):
    ext = ctx.text_extents(txt)
    ctx.move_to(x - ext.width / 2 - ext.x_bearing, y - ext.height / 2 - ext.y_bearing)
    ctx.show_text(txt)
