"""Builds a single frame: background -> animated set -> characters -> lighting
-> screen-space overlays (letterbox, subtitles, titles, fades)."""
from __future__ import annotations

import math

from . import fonts
import cairo

from .anim import clamp, noise1
from .characters import draw_brain, draw_pinky
from . import lab
from .script import END_TEXT, EPISODE

FORMATS = {
    "horizontal": (1920, 1080),
    "vertical": (1080, 1920),
}

SPEAKER_COLORS = {
    "BRAIN": (1.0, 1.0, 1.0),
    "PINKY": (1.0, 0.86, 0.42),
}


class Compositor:
    def __init__(self, fmt: str, res_scale: float = 1.0, bg_scale: float = 2.0):
        self.fmt = fmt
        bw, bh = FORMATS[fmt]
        self.W, self.H = int(bw * res_scale), int(bh * res_scale)
        self.res_scale = res_scale
        self.bg_scale = bg_scale
        self._bg = None
        self.cues = []

    # ------------------------------------------------------------------
    def background(self):
        if self._bg is None:
            wx, wy, ww, wh = lab.WORLD
            k = self.bg_scale
            surf = cairo.ImageSurface(cairo.FORMAT_RGB24, int(ww * k), int(wh * k))
            c = cairo.Context(surf)
            c.scale(k, k)
            c.translate(-wx, -wy)
            lab.draw_static(c)
            surf.flush()
            self._bg = surf
        return self._bg

    def camera(self, s, t):
        cx, cy, zoom = s.get("cam." + ("h" if self.fmt == "horizontal" else "v"))
        shake = s.get("cam.shake", 0.0)
        if shake > 0:
            cx += noise1(t * 18, 1) * 14 * shake
            cy += noise1(t * 18, 2) * 10 * shake
        # gentle handheld drift keeps shots alive
        drift = s.get("cam.drift", 1.0)
        cx += noise1(t * 0.35, 9) * 6 * drift
        cy += noise1(t * 0.3, 10) * 4 * drift
        return cx, cy, zoom

    def render(self, s, t) -> cairo.ImageSurface:
        W, H = self.W, self.H
        surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, W, H)
        ctx = cairo.Context(surf)
        ctx.set_source_rgb(0, 0, 0)
        ctx.paint()

        cx, cy, zoom = self.camera(s, t)
        z = zoom * self.res_scale
        ctx.save()
        ctx.translate(W / 2, H / 2)
        ctx.scale(z, z)
        ctx.translate(-cx, -cy)

        # cached static set
        bg = self.background()
        wx, wy, _, _ = lab.WORLD
        ctx.save()
        ctx.translate(wx, wy)
        ctx.scale(1 / self.bg_scale, 1 / self.bg_scale)
        pat = cairo.SurfacePattern(bg)
        pat.set_filter(cairo.FILTER_GOOD)
        ctx.set_source(pat)
        ctx.paint()
        ctx.restore()

        lab.draw_dynamic(ctx, s, t)

        # characters, back to front by y (same y -> brain first)
        order = sorted((("brain", s.get("brain.y", 0) - 0.1), ("pinky", s.get("pinky.y", 0))),
                       key=lambda kv: kv[1])
        for name, _ in order:
            (draw_brain if name == "brain" else draw_pinky)(ctx, s, t)

        lab.draw_lighting(ctx, s, t)
        ctx.restore()

        self._screen_fx(ctx, s, t)
        surf.flush()
        return surf

    # ------------------------------------------------------------------
    def _screen_fx(self, ctx, s, t):
        W, H = self.W, self.H
        r = self.res_scale
        # vignette
        g = cairo.RadialGradient(W / 2, H / 2, min(W, H) * 0.35, W / 2, H / 2, max(W, H) * 0.75)
        g.add_color_stop_rgba(0, 0, 0, 0, 0)
        g.add_color_stop_rgba(1, 0, 0, 0.03, 0.55)
        ctx.set_source(g)
        ctx.paint()

        flash = s.get("fx.flash", 0.0)
        if flash > 0:
            ctx.set_source_rgba(0.9, 0.93, 1.0, flash)
            ctx.paint()

        bars = s.get("fx.bars", 0.0)
        if bars > 0:
            bh = (H * 0.11 if self.fmt == "horizontal" else H * 0.07) * bars
            ctx.set_source_rgb(0, 0, 0)
            ctx.rectangle(0, 0, W, bh)
            ctx.rectangle(0, H - bh, W, bh)
            ctx.fill()

        black = s.get("fx.black", 0.0)
        if black > 0:
            ctx.set_source_rgba(0, 0, 0, clamp(black))
            ctx.paint()

        self._title(ctx, s, t)
        self._end_card(ctx, s, t)
        self._subtitles(ctx, t)

    def _title(self, ctx, s, t):
        a = s.get("title.a", 0.0)
        if a <= 0:
            return
        W, H, r = self.W, self.H, self.res_scale
        vert = self.fmt == "vertical"
        pop = s.get("title.pop", 1.0)
        ctx.save()
        cy = H * (0.2 if vert else 0.42)
        band = (300 if not vert else 420) * r
        g = cairo.LinearGradient(0, cy - band, 0, cy + band)
        g.add_color_stop_rgba(0, 0, 0, 0, 0)
        g.add_color_stop_rgba(0.5, 0.02, 0.0, 0.06, 0.55 * a)
        g.add_color_stop_rgba(1, 0, 0, 0, 0)
        ctx.rectangle(0, cy - band, W, 2 * band)
        ctx.set_source(g)
        ctx.fill()
        ctx.translate(W / 2, cy)
        ctx.scale(pop, pop)
        ctx.select_font_face(fonts.TITLE)
        size1 = (150 if not vert else 118) * r
        size2 = (84 if not vert else 72) * r
        lines = [("PINKY & THE BRAIN", size1, (1.0, 0.85, 0.3), 0)]
        if vert:
            lines = [("PINKY", size1, (1.0, 0.85, 0.3), -size1 * 1.05),
                     ("& THE BRAIN", size1, (1.0, 0.85, 0.3), 0)]
        lines.append((EPISODE["title"], size2, (1, 1, 1), size1 * 0.95))
        for txt, size, col, dy in lines:
            ctx.set_font_size(size)
            ext = ctx.text_extents(txt)
            x = -ext.width / 2 - ext.x_bearing
            y = dy - ext.height / 2 - ext.y_bearing
            ctx.move_to(x + 6 * r, y + 8 * r)
            ctx.text_path(txt)
            ctx.set_source_rgba(0, 0, 0, 0.6 * a)
            ctx.fill()
            ctx.move_to(x, y)
            ctx.text_path(txt)
            ctx.set_source_rgba(0.12, 0.05, 0.2, a)
            ctx.set_line_width(14 * r)
            ctx.set_line_join(cairo.LINE_JOIN_ROUND)
            ctx.stroke_preserve()
            ctx.set_source_rgba(*col, a)
            ctx.fill()
        ctx.restore()

    def _end_card(self, ctx, s, t):
        a = s.get("end.a", 0.0)
        if a <= 0:
            return
        W, H, r = self.W, self.H, self.res_scale
        vert = self.fmt == "vertical"
        ctx.save()
        ctx.select_font_face(fonts.SUBS)
        size = (64 if not vert else 66) * r
        ctx.set_font_size(size)
        maxw = W * (0.8 if not vert else 0.84)
        text = END_TEXT
        lines = wrap(ctx, text, maxw)
        lh = size * 1.3
        y0 = H / 2 - (len(lines) - 1) * lh / 2
        for i, ln in enumerate(lines):
            ext = ctx.text_extents(ln)
            ctx.move_to(W / 2 - ext.width / 2 - ext.x_bearing, y0 + i * lh - ext.height / 2 - ext.y_bearing)
            ctx.set_source_rgba(1, 1, 1, a)
            ctx.show_text(ln)
        b = s.get("end.b", 0.0)
        if b > 0:
            ctx.select_font_face(fonts.TITLE)
            ctx.set_font_size(54 * r)
            ext = ctx.text_extents("FIM")
            ctx.move_to(W / 2 - ext.width / 2 - ext.x_bearing, y0 + len(lines) * lh + 70 * r)
            ctx.set_source_rgba(1.0, 0.85, 0.3, b)
            ctx.show_text("FIM")
        ctx.restore()

    def _subtitles(self, ctx, t):
        cue = None
        for c in self.cues:
            if c["start"] - 0.001 <= t < c["end"]:
                cue = c
                break
        if cue is None:
            return
        W, H, r = self.W, self.H, self.res_scale
        vert = self.fmt == "vertical"
        a = clamp((t - cue["start"]) / 0.08) * clamp((cue["end"] - t) / 0.08)
        ctx.save()
        ctx.select_font_face(fonts.SUBS)
        size = (50 if not vert else 48) * r
        ctx.set_font_size(size)
        maxw = W * (0.78 if not vert else 0.86)
        lines = wrap(ctx, cue["text"], maxw)[:3]
        lh = size * 1.22
        base = (H - 62 * r) if not vert else (H * 0.795)
        y_last = base
        col = SPEAKER_COLORS.get(cue["speaker"], (1, 1, 1))
        if vert:  # soft backdrop: phones are watched small and in daylight
            bw = max(ctx.text_extents(ln).x_advance for ln in lines) + 56 * r
            top = y_last - (len(lines) - 1) * lh - size * 1.05
            ctx.new_path()
            rr = 22 * r
            x0, y0, x1, y1 = W / 2 - bw / 2, top, W / 2 + bw / 2, y_last + size * 0.42
            ctx.arc(x1 - rr, y0 + rr, rr, -math.pi / 2, 0)
            ctx.arc(x1 - rr, y1 - rr, rr, 0, math.pi / 2)
            ctx.arc(x0 + rr, y1 - rr, rr, math.pi / 2, math.pi)
            ctx.arc(x0 + rr, y0 + rr, rr, math.pi, 1.5 * math.pi)
            ctx.close_path()
            ctx.set_source_rgba(0.04, 0.02, 0.08, 0.42 * a)
            ctx.fill()
        for i, ln in enumerate(lines):
            y = y_last - (len(lines) - 1 - i) * lh
            ext = ctx.text_extents(ln)
            x = W / 2 - ext.width / 2 - ext.x_bearing
            ctx.move_to(x + 3 * r, y + 4 * r)
            ctx.text_path(ln)
            ctx.set_source_rgba(0, 0, 0, 0.55 * a)
            ctx.fill()
            ctx.move_to(x, y)
            ctx.text_path(ln)
            ctx.set_source_rgba(0.08, 0.04, 0.12, a)
            ctx.set_line_width(9 * r)
            ctx.set_line_join(cairo.LINE_JOIN_ROUND)
            ctx.stroke_preserve()
            ctx.set_source_rgba(*col, a)
            ctx.fill()
        ctx.restore()


def wrap(ctx, text, maxw):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if ctx.text_extents(trial).x_advance <= maxw or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    # balance two-line cues so the lines have similar length
    if len(lines) == 2:
        allw = text.split()
        best = None
        for k in range(1, len(allw)):
            a, b = " ".join(allw[:k]), " ".join(allw[k:])
            wa, wb = ctx.text_extents(a).x_advance, ctx.text_extents(b).x_advance
            if wa <= maxw and wb <= maxw:
                score = abs(wa - wb)
                if best is None or score < best[0]:
                    best = (score, [a, b])
        if best:
            lines = best[1]
    return lines
