"""Keyframe animation core: easing functions, tracks and a timeline.

Every animated parameter in the episode (character poses, facial expressions,
camera, overlays) is a named Track inside one Timeline.  Tracks hold keyframes
(time, value, ease) and are evaluated at any time t.  Values may be floats or
tuples of floats (interpolated component-wise).
"""
from __future__ import annotations

import bisect
import math


# --------------------------------------------------------------------------
# Easing
# --------------------------------------------------------------------------
def _ease(name: str, u: float) -> float:
    if u <= 0.0:
        return 0.0
    if u >= 1.0:
        return 1.0
    if name == "linear":
        return u
    if name == "in":
        return u * u * u
    if name == "out":
        return 1 - (1 - u) ** 3
    if name == "inout":
        return 4 * u ** 3 if u < 0.5 else 1 - (-2 * u + 2) ** 3 / 2
    if name == "sine":
        return 0.5 - 0.5 * math.cos(math.pi * u)
    if name == "back":  # overshoot then settle (cartoon "snap")
        c1, c3 = 1.70158, 2.70158
        return 1 + c3 * (u - 1) ** 3 + c1 * (u - 1) ** 2
    if name == "elastic":
        return 2 ** (-10 * u) * math.sin((u * 10 - 0.75) * (2 * math.pi) / 3) + 1
    if name == "step":
        return 0.0
    raise ValueError(f"unknown ease {name}")


def ease(name: str, u: float) -> float:
    return _ease(name, u)


def lerp(a, b, u):
    if isinstance(a, str) or isinstance(b, str):
        return b if u > 0 else a          # discrete values switch immediately
    if isinstance(a, tuple):
        return tuple(x + (y - x) * u for x, y in zip(a, b))
    return a + (b - a) * u


def clamp(x, lo=0.0, hi=1.0):
    return lo if x < lo else hi if x > hi else x


def smoothstep(e0, e1, x):
    u = clamp((x - e0) / (e1 - e0)) if e1 != e0 else (1.0 if x >= e1 else 0.0)
    return u * u * (3 - 2 * u)


# --------------------------------------------------------------------------
# Tracks
# --------------------------------------------------------------------------
class Track:
    """Sorted keyframes.  The ease stored on a key shapes the segment that
    *arrives* at that key."""

    __slots__ = ("times", "values", "eases")

    def __init__(self, value=None):
        self.times: list[float] = []
        self.values: list = []
        self.eases: list[str] = []
        if value is not None:
            self.key(0.0, value, "linear")

    def key(self, t: float, v, e: str = "inout"):
        i = bisect.bisect_left(self.times, t)
        if i < len(self.times) and abs(self.times[i] - t) < 1e-9:
            self.values[i] = v
            self.eases[i] = e
            return
        self.times.insert(i, t)
        self.values.insert(i, v)
        self.eases.insert(i, e)

    def __call__(self, t: float):
        ts = self.times
        if not ts:
            return None
        if t <= ts[0]:
            return self.values[0]
        if t >= ts[-1]:
            return self.values[-1]
        i = bisect.bisect_right(ts, t)
        t0, t1 = ts[i - 1], ts[i]
        u = (t - t0) / (t1 - t0)
        return lerp(self.values[i - 1], self.values[i], _ease(self.eases[i], u))


class Timeline:
    """A bag of named tracks with convenience helpers for authoring."""

    def __init__(self):
        self.tracks: dict[str, Track] = {}
        self.defaults: dict[str, object] = {}
        self.events: list[tuple[float, str, dict]] = []  # discrete events (sfx, cuts...)

    # -- authoring -------------------------------------------------------
    def default(self, name, value):
        self.defaults[name] = value

    def key(self, name, t, v, e="inout"):
        tr = self.tracks.get(name)
        if tr is None:
            tr = self.tracks[name] = Track()
            if name in self.defaults and t > 0:
                tr.key(0.0, self.defaults[name], "linear")
        tr.key(t, v, e)

    def to(self, name, t0, dur, v, e="inout"):
        """Animate `name` from its current value at t0 to v over dur seconds."""
        cur = self.get(name, t0)
        self.key(name, t0, cur, "linear" if dur > 0 else "step")
        self.key(name, t0 + max(dur, 1e-4), v, e)

    def set(self, name, t, v):
        """Instant change (hard cut) at time t."""
        cur = self.get(name, t - 1e-4)
        self.key(name, t - 1e-4, cur, "linear")
        self.key(name, t, v, "step")

    def event(self, t, kind, **data):
        self.events.append((t, kind, data))

    # -- evaluation ------------------------------------------------------
    def get(self, name, t, default=None):
        tr = self.tracks.get(name)
        if tr is None or not tr.times:
            return self.defaults.get(name, default)
        return tr(t)

    def sampler(self, t):
        return _Sampler(self, t)


class _Sampler:
    """Read-only view of the timeline at a fixed time: s["brain.lid"]."""

    __slots__ = ("tl", "t")

    def __init__(self, tl, t):
        self.tl, self.t = tl, t

    def __getitem__(self, name):
        return self.tl.get(name, self.t)

    def get(self, name, default=None):
        v = self.tl.get(name, self.t, None)
        return default if v is None else v


# --------------------------------------------------------------------------
# Procedural helpers
# --------------------------------------------------------------------------
def noise1(x: float, seed: int = 0) -> float:
    """Cheap smooth 1D value noise in [-1, 1]."""
    i = math.floor(x)
    f = x - i

    def h(n):
        n = (n * 374761393 + seed * 668265263) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 32767.5 - 1.0

    u = f * f * (3 - 2 * f)
    return h(i) * (1 - u) + h(i + 1) * u


def blink_curve(t: float, blinks: list[float], dur: float = 0.16) -> float:
    """Return eyelid closure 0..1 for a list of blink start times."""
    i = bisect.bisect_right(blinks, t) - 1
    if i < 0:
        return 0.0
    dt = t - blinks[i]
    if dt > dur:
        return 0.0
    u = dt / dur
    # fast close, slightly slower open
    return math.sin(math.pi * (u ** 0.8))
