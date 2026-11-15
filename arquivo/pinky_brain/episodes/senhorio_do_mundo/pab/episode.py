"""The episode: staging, acting, camera, sound and music, scene by scene.

Everything is timed relative to the actual length of each voice line, so
changing a line in script.py (or its prosody) automatically re-flows the
whole film.  Read this file like a shooting script.
"""
from __future__ import annotations

import math
import random
import re

from .anim import Timeline
from .lab import FLOOR_Y
from .voices import load_clips
from .characters import BRAIN_ARM, BRAIN_DEFAULTS, PINKY_ARM, PINKY_DEFAULTS, ik

FPS = 24

# ---------------------------------------------------------------------------
# Staging
# ---------------------------------------------------------------------------
BRAIN_X = 720.0
PINKY_X = 1140.0
PINKY_START = 2200.0

# camera presets: (cx, cy, zoom) for the 16:9 and the 9:16 cuts
CAM = {
    "monitors":  ((1195, 330, 1.9), (1195, 380, 1.55)),
    "wide":      ((960, 540, 1.0), (930, 640, 1.32)),
    "two":       ((935, 650, 1.32), (930, 665, 1.4)),
    "two_tight": ((935, 640, 1.55), (930, 670, 1.55)),
    "brain_med": ((770, 680, 1.8), (745, 690, 1.8)),
    "brain_cu":  ((745, 655, 2.7), (738, 655, 2.5)),
    "brain_xcu": ((750, 660, 3.6), (742, 660, 3.3)),
    "pinky_med": ((1095, 630, 1.75), (1100, 640, 1.8)),
    "pinky_cu":  ((1070, 575, 2.6), (1075, 590, 2.4)),
    "brain_map": ((560, 520, 1.25), (600, 600, 1.35)),
    "map":       ((480, 300, 1.75), (470, 330, 1.55)),
}

# arm poses (shoulder, elbow) — angles from "hanging down", positive = forward.
# Poses that must land on an exact spot (top of the head, chest...) use IK.
def _ik(arm, front, target, elbow="out"):
    sh = arm[0] if front else arm[1]
    sols = [ik(sh, target, arm[2], arm[3], b) for b in (1.0, -1.0)]
    if elbow == "out":
        key = lambda a: sh[0] + arm[2] * math.sin(a[0])      # noqa: E731
    elif elbow == "back":
        key = lambda a: -(sh[0] + arm[2] * math.sin(a[0]))   # noqa: E731
    else:
        key = lambda a: sh[1] + arm[2] * math.cos(a[0])      # noqa: E731
    return max(sols, key=key)


BR = dict(
    rest_f=(0.3, 0.6), rest_b=(-0.25, 0.5),
    fist_up=(2.75, 0.35), point_up=(1.5, 0.9), explain=(1.05, 0.7), explain_b=(0.75, 0.9),
    rub_f=_ik(BRAIN_ARM, True, (56, -124), "down"), rub_b=_ik(BRAIN_ARM, False, (48, -122), "down"),
    spread_f=(1.75, 0.35), spread_b=(-1.75, -0.35),
    map_hi=(2.55, 0.0), map_mid=(2.3, 0.0), map_lo=(2.05, 0.05),
    tense_f=(0.15, 0.05), tense_b=(-0.1, 0.05),
    count=(1.45, 1.25), dismiss=(1.6, -0.2),
)
PK = dict(
    rest_f=(0.15, 0.25), rest_b=(-0.1, 0.2),
    eager_f=_ik(PINKY_ARM, True, (72, -268), "down"), eager_b=_ik(PINKY_ARM, False, (62, -262), "down"),
    point=(1.62, 0.08), scratch=_ik(PINKY_ARM, True, (-52, -428), "back"),
    shrug_f=(0.55, 1.45), shrug_b=(-0.55, -1.45),
    chin=_ik(PINKY_ARM, True, (86, -316), "down"),
)

BRAIN_PAN, PINKY_PAN = -0.18, 0.18


_GLUE = {"a", "o", "e", "de", "do", "da", "que", "em", "um", "uma", "os", "as", "se", "não",
         "por", "para", "no", "na", "nas", "nos", "mais", "vai", "vais", "sua", "seu",
         "suas", "seus", "pelo", "pela"}


def _split_words(words, maxc):
    """Split a line into subtitle chunks of <= maxc chars (two lines on screen),
    preferring breaks after punctuation and balanced lengths."""
    idx = list(range(len(words)))
    text = " ".join(words)
    if len(text) <= maxc or len(words) < 2:
        return [idx]
    best = None
    for k in range(1, len(words)):
        a, b = " ".join(words[:k]), " ".join(words[k:])
        score = abs(len(a) - len(b))
        if re.search(r"[.?!]$", words[k - 1]):
            score -= 30
        elif re.search(r"[,;:]$", words[k - 1]):
            score -= 18
        if len(a) < 14 or len(b) < 14:
            score += 60
        if words[k - 1].lower() in _GLUE:
            score += 45
        if best is None or score < best[0]:
            best = (score, k)
    k = best[1]
    left = _split_words(words[:k], maxc)
    right = _split_words(words[k:], maxc)
    return left + [[i + k for i in ch] for ch in right]


class Director:
    def __init__(self, clips):
        self.tl = Timeline()
        self.clips = clips
        self.t = 0.0
        self.cues = []
        self.voice_spans = []
        self.speaking = []           # (who, t0, t1)
        self.noblink = {"brain": [], "pinky": []}

    # -- time ------------------------------------------------------------
    def wait(self, dt):
        self.t += dt
        return self.t

    # -- dialogue --------------------------------------------------------
    def say(self, cid, at=None):
        clip = self.clips[cid]
        t0 = self.t if at is None else at
        who = clip.who.lower()
        pan = BRAIN_PAN if who == "brain" else PINKY_PAN
        self.voice_spans.append((cid, t0, pan))
        for (tt, o, w, talk) in clip.mouth_curves(FPS, t0):
            self.tl.key(f"{who}.mouth_open", tt, o, "linear")
            self.tl.key(f"{who}.mouth_wide", tt, w, "linear")
            self.tl.key(f"{who}.talk", tt, talk, "linear")
        t1 = t0 + clip.duration
        self.speaking.append((who, t0, t1))
        self._cues(clip, t0)
        self.t = t1
        return t0, t1

    def _cues(self, clip, t0):
        disp = clip.text.split()
        words = clip.words
        nd, nt = len(disp), max(1, len(words))
        chunks = _split_words(disp, 80)

        def wt(idx, end=False):
            k = min(nt - 1, int(round(idx * nt / nd)))
            if not words:
                return 0.0
            return words[k][1] if end else words[k][0]

        for ci, ch in enumerate(chunks):
            s = t0 + (wt(ch[0]) if ci else 0.0)
            if ci + 1 < len(chunks):
                e = t0 + wt(chunks[ci + 1][0]) - 0.02
            else:
                e = t0 + clip.duration + 0.35
            self.cues.append(dict(start=s, end=e, speaker=clip.who,
                                  text=" ".join(disp[j] for j in ch)))

    # -- acting ----------------------------------------------------------
    def act(self, who, t, dur=0.3, ease="inout", **params):
        for k, v in params.items():
            if dur <= 0:
                self.tl.set(f"{who}.{k}", t, v)
            else:
                self.tl.to(f"{who}.{k}", t, dur, v, ease)

    def cam(self, preset, t, dur=0.0, ease="inout"):
        h, v = CAM[preset] if isinstance(preset, str) else preset
        # a new cut/move always wins over a move that is still running
        for name in ("cam.h", "cam.v"):
            cur = self.tl.get(name, t)
            tr = self.tl.tracks.get(name)
            if tr is not None and tr.times and tr.times[-1] > t:
                tr.truncate_after(t)
                tr.key(t, cur, "linear")
        if dur <= 0:
            self.tl.set("cam.h", t, h)
            self.tl.set("cam.v", t, v)
        else:
            self.tl.to("cam.h", t, dur, h, ease)
            self.tl.to("cam.v", t, dur, v, ease)

    def sfx(self, name, t, gain=1.0, pan=0.0, amp=1.0):
        self.tl.event(t, "sfx", name=name, gain=gain, pan=pan, amp=amp)

    def music(self, name, t, **kw):
        self.tl.event(t, "music", name=name, **kw)

    def turn(self, who, t, facing, sound=True):
        """Snappy cartoon turn: squash + flip + whoosh."""
        self.tl.to(f"{who}.squash", t, 0.06, 0.88, "out")
        self.tl.set(f"{who}.facing", t + 0.06, facing)
        self.tl.to(f"{who}.squash", t + 0.06, 0.14, 1.0, "back")
        if sound:
            self.sfx("swish", t, 0.6, BRAIN_PAN if who == "brain" else PINKY_PAN)

    def walk(self, who, t0, x0, x1, dur, stride=105):
        steps = abs(x1 - x0) / stride
        self.tl.to(f"{who}.x", t0, 0.0, x0)
        self.tl.to(f"{who}.x", t0, dur, x1, "linear")
        self.tl.to(f"{who}.walk", t0, 0.15, 1.0)
        self.tl.to(f"{who}.walk", t0 + dur - 0.2, 0.25, 0.0, "out")
        self.tl.to(f"{who}.walk_phase", t0, 0.0, 0.0)
        self.tl.to(f"{who}.walk_phase", t0, dur, steps * math.pi, "linear")
        for k in range(1, int(steps) + 1):
            self.sfx("step", t0 + dur * k / steps, 0.5 + 0.5 * k / steps, PINKY_PAN)

    # -- finishing passes --------------------------------------------------
    def finish(self, end):
        rnd = random.Random(42)
        for who in ("brain", "pinky"):
            t = 0.8 + rnd.random()
            blinks = []
            while t < end:
                if not any(a <= t <= b for a, b in self.noblink[who]):
                    blinks.append(t)
                    if rnd.random() < 0.18:
                        blinks.append(t + 0.28)  # double blink
                t += rnd.uniform(2.2, 4.8)
            for b in blinks:
                self.blink(who, b)
        self.cues.sort(key=lambda c: c["start"])
        # make sure cues never overlap
        for a, b in zip(self.cues, self.cues[1:]):
            if a["end"] > b["start"] - 0.02:
                a["end"] = b["start"] - 0.02

    def blink(self, who, t, dur=0.16):
        n = f"{who}.blink"
        self.tl.key(n, t, 0.0, "linear")
        self.tl.key(n, t + dur * 0.4, 1.0, "out")
        self.tl.key(n, t + dur, 0.0, "inout")


def build(clips=None):
    clips = clips or load_clips()
    d = Director(clips)
    tl = d.tl
    for k, v in BRAIN_DEFAULTS.items():
        tl.default("brain." + k, v)
    for k, v in PINKY_DEFAULTS.items():
        tl.default("pinky." + k, v)
    for k, v in dict(light_dark=0.0, light_red=0.0, fx_bars=0.0, fx_flash=0.0, lab_mapdead=0.0,
                     lab_nosignal=0.0, lab_flicker=0.0, cam_shake=0.0, cam_drift=1.0,
                     end_a=0.0, end_b=0.0, title_a=0.0, title_pop=1.0).items():
        tl.default(k.replace("_", ".", 1), v)
    tl.default("brain.x", BRAIN_X)
    tl.default("brain.y", FLOOR_Y)
    tl.default("pinky.x", PINKY_START)
    tl.default("pinky.y", FLOOR_Y)
    tl.default("pinky.facing", -1.0)
    tl.default("brain.facing", -1.0)
    tl.default("brain.look", (0.4, -0.35))
    tl.default("pinky.look", (0.5, 0.2))
    tl.default("brain.pointer", 1.0)
    tl.default("brain.arm_f", BR["map_mid"])
    tl.default("cam.h", CAM["monitors"][0])
    tl.default("cam.v", CAM["monitors"][1])
    tl.default("fx.black", 1.0)
    tl.default("amb.level", 1.0)
    tl.default("music.level", 1.0)
    tl.default("lab.crisis", 0.15)

    # board insert: the clippings carry the housing jokes
    BOARD = ((1680, 300, 2.0), (1680, 320, 1.7))

    # =====================================================================
    # COLD OPEN + TITLE
    # =====================================================================
    d.music("intro", 0.0, dur=4.0)
    tl.to("fx.black", 0.0, 1.2, 0.0, "out")
    d.cam("wide", 0.4, 3.8, "inout")
    tl.key("title.a", 0.9, 0.0, "linear")
    tl.key("title.a", 1.3, 1.0, "out")
    tl.key("title.pop", 0.9, 0.6, "linear")
    tl.key("title.pop", 1.5, 1.0, "back")
    tl.key("title.a", 3.5, 1.0, "linear")
    tl.key("title.a", 3.95, 0.0, "inout")
    d.act("brain", 1.6, 0.25, arm_f=BR["map_hi"])
    d.sfx("knock", 1.85, 0.5, BRAIN_PAN)
    d.act("brain", 2.4, 0.25, arm_f=BR["map_lo"])
    d.sfx("knock", 2.65, 0.5, BRAIN_PAN)
    d.act("brain", 3.3, 0.3, arm_f=BR["map_mid"])

    # =====================================================================
    # CENA 1 — O NOVO PLANO
    # =====================================================================
    d.t = 4.0
    d.music("theme", 4.0, dur=40.0, gain=0.8)
    d.walk("pinky", 3.75, PINKY_START, PINKY_X, 2.9)
    d.act("pinky", 3.75, 0.3, smile=0.5, arm_f=(0.6, 0.3), arm_b=(-0.6, 0.3))
    t0, t1 = d.say("p01", at=4.85)
    d.act("pinky", t0, 0.3, brow_l=0.5, brow_r=0.5, head_tilt=0.12)
    d.act("pinky", 6.55, 0.25, arm_f=PK["rest_f"], arm_b=PK["rest_b"])
    tl.to("pinky.bob", 6.65, 0.08, 6.0, "out")
    tl.to("pinky.bob", 6.73, 0.25, 0.0, "back")
    d.turn("brain", t1 - 0.25, 1.0)
    d.act("brain", t1 - 0.2, 0.25, arm_f=BR["rest_f"], pointer=0.0, look=(0.5, -0.3),
          lid=0.5, brow_tilt=0.35, smile=0.15)
    d.wait(0.25)
    d.cam("two", d.t)
    t0, t1 = d.say("b01")
    d.act("brain", t0 + 0.3, 0.4, arm_f=BR["explain"], hand_f="open", head_tilt=-0.06)
    d.act("pinky", t0 + 0.2, 0.3, brow_l=0.2, brow_r=0.2, head_tilt=0.0)
    d.wait(0.08)
    d.cam("brain_med", d.t - 0.05, 0.35, "out")
    t0, t1 = d.say("b02")
    d.act("brain", t0, 0.22, arm_f=BR["fist_up"], hand_f="fist", lid=0.25, brow_tilt=0.6,
          ease="back")
    d.sfx("whoosh", t0, 0.6, BRAIN_PAN)
    d.act("brain", t0, 0.25, squash=1.06, ease="out")
    d.act("brain", t0 + 0.3, 0.25, squash=1.0)
    d.wait(0.3)
    # "Mas hoje descobri o negócio mais poderoso do planeta."
    d.act("brain", d.t, 0.35, arm_f=BR["point_up"], hand_f="point", smile=0.55, smirk=0.45,
          brow_r=0.6, brow_l=-0.1, lid=0.45)
    d.sfx("bwip", d.t + 0.1, 0.25, BRAIN_PAN)
    d.cam("brain_cu", d.t, clips["b03"].duration + 0.3, "inout")
    t0, t1 = d.say("b03")
    d.wait(0.2)
    # "Narf! É queijo?" — Pinky hopes for cheese
    d.cam("pinky_med", d.t)
    t0, t1 = d.say("p02")
    d.act("pinky", t0, 0.25, arm_f=PK["eager_f"], arm_b=PK["eager_b"], hand_f="fist",
          hand_b="fist", smile=1.0, brow_l=0.9, brow_r=0.9, lid_low=0.15, ease="back")
    d.sfx("bwip", t0 + 0.05, 0.3, PINKY_PAN)
    tl.to("pinky.squash", t0, 0.08, 0.9, "out")
    tl.to("pinky.squash", t0 + 0.08, 0.15, 1.08, "out")
    tl.to("pinky.bob", t0 + 0.08, 0.18, -38.0, "out")
    tl.to("pinky.bob", t0 + 0.26, 0.2, 0.0, "in")
    tl.to("pinky.squash", t0 + 0.42, 0.06, 0.92, "out")
    tl.to("pinky.squash", t0 + 0.48, 0.25, 1.0, "back")
    d.sfx("step", t0 + 0.46, 0.8, PINKY_PAN)
    d.wait(0.25)
    # "Pior, Pinky. É imobiliário." — evil hand rub
    d.cam("brain_cu", d.t)
    d.act("brain", d.t - 0.1, 0.3, arm_f=BR["rub_f"], arm_b=BR["rub_b"], hand_f="relaxed",
          hand_b="relaxed", smile=0.0, lid=0.55, brow_tilt=0.5, brow_r=0.0, brow_l=0.0, smirk=0.0)
    d.act("pinky", d.t, 0.4, arm_f=PK["rest_f"], arm_b=PK["rest_b"], lid_low=0.0, smile=0.6)
    t0, t1 = d.say("b04")
    d.act("brain", t0 + 1.5, 0.4, smile=0.9, lid=0.38, lid_low=0.18, brow_tilt=0.8)
    for k in range(6):
        tl.key("brain.arm_f", t0 + 1.6 + k * 0.17, (BR["rub_f"][0] + (0.08 if k % 2 else -0.06),
                                                    BR["rub_f"][1]), "sine")
    d.sfx("scratch", t0 + 1.55, 0.35, BRAIN_PAN)
    d.wait(0.3)

    # =====================================================================
    # CENA 2 — A ESTRATÉGIA
    # =====================================================================
    # turns to the map: every house he buys lights up
    d.cam("brain_map", d.t - 0.2)
    d.turn("brain", d.t - 0.15, -1.0)
    d.act("brain", d.t - 0.1, 0.3, pointer=1.0, arm_f=BR["map_mid"], hand_f="fist",
          arm_b=BR["rest_b"], look=(0.4, -0.5), smile=0.4, lid=0.42, lid_low=0.0, brow_tilt=0.4)
    d.act("pinky", d.t, 0.4, look=(0.6, -0.4), smile=0.3)
    t0, t1 = d.say("b05")
    dur = clips["b05"].duration
    tl.to("lab.crisis", t0, dur, 0.5, "inout")
    for k, frac in enumerate((0.15, 0.4, 0.62)):
        d.act("brain", t0 + dur * frac, 0.2, arm_f=(BR["map_hi"], BR["map_lo"], BR["map_mid"])[k])
        d.sfx("knock", t0 + dur * frac + 0.2, 0.5, BRAIN_PAN)
        d.sfx("pop", t0 + dur * frac + 0.25, 0.3, -0.3)
    d.wait(0.2)
    # "E quanto é razoável, Brain?"
    d.cam("pinky_med", d.t)
    d.act("pinky", d.t - 0.1, 0.3, head_tilt=-0.18, brow_l=0.7, brow_r=0.1, look=(0.5, 0.2),
          arm_f=PK["shrug_f"], arm_b=PK["shrug_b"], hand_f="open", hand_b="open")
    t0, t1 = d.say("p03")
    d.act("pinky", t1, 0.3, arm_f=PK["rest_f"], arm_b=PK["rest_b"], hand_f="relaxed",
          hand_b="relaxed")
    d.wait(0.2)
    # "Mil e cem euros por catorze metros quadrados..." — board insert, then Brain
    d.turn("brain", d.t - 0.2, 1.0, sound=False)
    d.act("brain", d.t - 0.2, 0.3, pointer=0.0, arm_f=BR["explain"], hand_f="open",
          look=(0.5, -0.3), smile=0.3, lid=0.5)
    d.cam(BOARD, d.t)
    t0, t1 = d.say("b06")
    dur = clips["b06"].duration
    d.cam("brain_med", t0 + dur * 0.55)
    for k, frac in enumerate((0.58, 0.7, 0.8)):   # "Cozinha, sala e quarto."
        tt = t0 + dur * frac
        d.act("brain", tt - 0.1, 0.15, arm_f=BR["count"], hand_f="point",
              head_tilt=(0.06 if k % 2 else -0.06), ease="out")
        d.act("brain", tt + 0.15, 0.15, arm_f=BR["explain"])
        d.sfx("swish", tt - 0.05, 0.35, BRAIN_PAN)
    d.act("brain", t0 + dur * 0.88, 0.3, arm_f=BR["dismiss"], hand_f="open", smile=0.6,
          smirk=0.4)
    d.wait(0.2)
    # "E a casa de banho?"
    d.cam("pinky_cu", d.t)
    d.act("pinky", d.t - 0.1, 0.25, head_tilt=0.15, brow_l=0.9, brow_r=0.9, smile=0.0,
          pupil=1.1, look=(0.5, 0.15))
    t0, t1 = d.say("p04")
    d.wait(0.35)
    # "Serviço opcional, Pinky. Mediante subscrição." — deadpan
    d.cam("brain_cu", d.t)
    d.act("brain", d.t - 0.1, 0.3, arm_f=BR["rest_f"], hand_f="relaxed", lid=0.62,
          brow_tilt=0.2, brow_l=-0.1, brow_r=0.5, smile=0.1, smirk=0.0, head_tilt=0.04)
    t0, t1 = d.say("b07")
    d.act("brain", t0 + 2.0, 0.4, smile=0.5, smirk=0.5, lid=0.5)
    d.act("pinky", t0, 0.3, pupil=1.0, head_tilt=0.0, brow_l=0.4, brow_r=0.4)
    d.wait(0.2)
    # "Caução de três rendas, fiador, e uma visita com oitenta candidatos..." — monitors
    d.cam("monitors", d.t)
    d.cam(((1195, 335, 2.1), (1195, 385, 1.7)), d.t + 0.01, clips["b08"].duration * 0.55, "linear")
    t0, t1 = d.say("b08")
    dur = clips["b08"].duration
    tl.to("lab.crisis", t0, dur, 0.85, "inout")
    for frac in (0.1, 0.3, 0.45):
        d.sfx("pop", t0 + dur * frac, 0.3, 0.2)
    d.cam("two", t0 + dur * 0.6)
    d.act("brain", t0 + dur * 0.6, 0.3, arm_f=BR["explain"], hand_f="open", lid=0.45, smile=0.5)
    d.act("pinky", t0 + dur * 0.6, 0.3, smile=0.2, lid=0.15, brow_l=0.6, brow_r=0.6)
    d.wait(0.2)
    # "Quando toda a gente depender de mim para ter um teto, o mundo será meu."
    d.cam("brain_med", d.t)
    d.act("brain", d.t, 0.35, arm_f=BR["rub_f"], arm_b=BR["rub_b"], hand_f="relaxed",
          smile=0.8, lid=0.4, brow_tilt=0.7)
    t0, t1 = d.say("b09")
    tl.to("lab.crisis", t0, clips["b09"].duration, 1.0, "inout")
    d.wait(0.2)

    # the sinister build-up
    t_build = d.t
    tl.key("music.level", t_build - 0.2, 1.0, "linear")
    tl.key("music.level", t_build + 0.4, 0.0, "inout")
    d.music("drone", t_build, dur=clips["b10"].duration + 0.9, gain=0.9)
    d.act("brain", t_build, 0.4, look=(0.05, 0.0), head_turn=0.1, lid=0.35, lid_low=0.2,
          brow_tilt=0.9, smile=0.75)
    d.act("pinky", t_build, 0.6, look=(0.5, 0.3), smile=0.2)
    d.cam("brain_cu", t_build + 0.05, clips["b10"].duration + 0.6, "inout")
    tl.to("light.dark", t_build, 1.5, 0.75)
    tl.to("light.red", t_build, 1.8, 1.0)
    tl.to("fx.bars", t_build, 1.2, 1.0)
    tl.to("amb.level", t_build, 1.0, 0.3)
    t0, t1 = d.say("b10", at=t_build + 0.15)
    d.wait(0.4)
    # "SERÁ MEU!"
    t0, t1 = d.say("b11")
    t_hit = t0 + clips["b11"].words[-1][0] if clips["b11"].words else t0 + 0.5
    d.act("brain", t0, 0.2, lid=0.12, lid_low=0.0, brow_tilt=0.9, smile=0.6, head_turn=0.2)
    d.act("brain", t_hit - 0.18, 0.18, arm_f=BR["spread_f"], arm_b=BR["spread_b"],
          hand_f="open", hand_b="open", squash=1.1, ease="back")
    d.act("brain", t_hit + 0.1, 0.3, squash=1.0)
    d.cam("brain_med", t_hit - 0.18)
    d.cam("two", t_hit + 0.9)
    d.music("hit", t_hit)
    d.sfx("thunder", t_hit, 0.9)
    d.sfx("whoosh_big", t_hit - 0.2, 0.7, BRAIN_PAN)
    for (dt, v) in ((0.0, 0.95), (0.08, 0.15), (0.16, 0.7), (0.5, 0.0)):
        tl.key("fx.flash", t_hit + dt, v, "linear")
    tl.key("fx.flash", t_hit - 0.01, 0.0, "linear")
    tl.key("lab.flicker", t_hit - 0.01, 0.0, "linear")
    tl.key("lab.flicker", t_hit + 0.05, 0.8, "linear")
    tl.key("lab.flicker", t_hit + 0.6, 0.0, "linear")
    tl.key("cam.shake", t_hit - 0.01, 0.0, "linear")
    tl.key("cam.shake", t_hit + 0.02, 1.0, "linear")
    tl.key("cam.shake", t_hit + 0.9, 0.0, "out")
    d.act("pinky", t_hit, 0.15, lean=0.12, arm_f=PK["eager_f"], arm_b=PK["eager_b"],
          hand_f="open", hand_b="open", smile=0.9, lid=0.0, brow_l=1.0, brow_r=1.0, ease="out")
    d.act("pinky", t_hit + 0.6, 0.6, lean=0.0)
    d.t = t_hit + 1.45
    tl.to("light.dark", d.t - 0.4, 0.8, 0.0)
    tl.to("light.red", d.t - 0.4, 0.8, 0.0)
    tl.to("fx.bars", d.t - 0.4, 0.8, 0.0)
    tl.to("amb.level", d.t - 0.4, 1.0, 1.0)
    d.act("brain", d.t - 0.3, 0.5, arm_f=BR["rest_f"], arm_b=BR["rest_b"], hand_f="relaxed",
          hand_b="relaxed", lid=0.42, brow_tilt=0.3, smile=0.5, head_turn=0.35, look=(0.5, -0.3))

    # =====================================================================
    # CENA 3 — A PERGUNTA DE PINKY
    # =====================================================================
    t_obs = d.t - 0.2
    d.cam("two", t_obs)
    d.act("pinky", t_obs, 0.4, arm_f=PK["rest_f"], arm_b=PK["rest_b"], hand_f="relaxed",
          hand_b="relaxed", smile=0.0, look=(0.8, -0.6), brow_l=0.3, brow_r=0.0, lid=0.1,
          head_turn=0.25)
    d.act("pinky", t_obs + 0.8, 0.35, arm_f=PK["scratch"], hand_f="relaxed", scratch=1.0)
    d.sfx("scratch", t_obs + 1.05, 0.5, PINKY_PAN)
    d.act("pinky", t_obs + 1.2, 0.4, head_tilt=-0.22, brow_l=0.8, brow_r=-0.1)
    d.sfx("bwip", t_obs + 1.3, 0.2, PINKY_PAN)
    d.act("pinky", t_obs + 1.9, 0.3, scratch=0.0, arm_f=PK["rest_f"], look=(0.5, 0.2))
    d.t = t_obs + 2.1
    d.cam("pinky_med", d.t)
    t0, t1 = d.say("p05")
    d.act("pinky", t0, 0.3, head_tilt=-0.15)
    d.wait(0.2)
    d.cam("brain_med", d.t)
    d.act("brain", d.t - 0.1, 0.25, lid=0.62, brow_tilt=0.75, brow_l=-0.3, brow_r=-0.3,
          smile=-0.35, arm_f=BR["tense_f"], arm_b=BR["tense_b"], hand_f="fist", hand_b="fist",
          look=(0.55, -0.35))
    t0, t1 = d.say("b12")
    d.act("brain", t0 + 0.3, 0.2, head_tilt=0.08)
    d.act("brain", t0 + 0.6, 0.3, head_tilt=-0.04)
    d.wait(0.2)
    # the question, sincere; suspense underneath
    d.cam("pinky_med", d.t)
    d.cam("pinky_cu", d.t + 0.01, clips["p06"].duration + 0.2, "inout")
    tl.key("music.level", d.t - 0.05, 0.0, "linear")
    tl.key("music.level", d.t + 0.05, 1.0, "linear")
    d.music("suspense", d.t, dur=clips["p06"].duration + clips["p07"].duration + 0.8, gain=0.7)
    d.act("pinky", d.t - 0.1, 0.4, arm_f=PK["point"], hand_f="point", head_tilt=-0.08,
          look=(0.5, 0.1), brow_l=0.4, brow_r=0.4, smile=0.0)
    t0, t1 = d.say("p06")
    d.act("pinky", t0 + 0.9, 0.5, arm_f=PK["rest_f"], hand_f="relaxed")
    d.wait(0.1)
    d.cam("two_tight", d.t)
    t0, t1 = d.say("p07")
    d.act("pinky", t0 + 0.1, 0.4, arm_f=PK["shrug_f"], arm_b=PK["shrug_b"], hand_f="open",
          hand_b="open", head_tilt=-0.12, look=(0.5, 0.25))
    d.act("brain", t0 + 0.3, 0.4, lid=0.4, brow_tilt=0.4, brow_l=0.0, brow_r=0.0, smile=0.1,
          arm_f=BR["rest_f"], arm_b=BR["rest_b"], hand_f="relaxed", hand_b="relaxed")
    # the empty world: monitors die, the map goes grey
    for k in range(3):
        tl.set("lab.nosignal", t0 + 0.4 + k * 0.32, float(k + 1))
        d.sfx("static", t0 + 0.4 + k * 0.32, 0.5, 0.2)
    tl.to("lab.mapdead", t0 + 0.4, 0.8, 1.0)
    tl.to("amb.level", t0 + 0.4, 1.0, 0.25)
    d.music("sting", t1 + 0.05)
    d.cam("brain_med", t1 + 0.05)
    d.cam("brain_cu", t1 + 0.1, 1.1, "out")
    d.act("brain", t1 + 0.05, 0.12, lid=0.2, brow_l=0.4, brow_r=0.4, brow_tilt=0.1, smile=0.0,
          ease="out")
    d.t = t1 + 1.25
    d.cam("pinky_med", d.t)
    t0, t1 = d.say("p08", at=d.t + 0.05)
    d.act("pinky", t0, 0.35, arm_f=PK["point"], arm_b=PK["rest_b"], hand_f="point",
          hand_b="relaxed", look=(0.6, -0.5), head_turn=0.25)
    d.act("pinky", t0 + 1.4, 0.4, arm_f=PK["shrug_f"], arm_b=PK["shrug_b"], hand_f="open",
          hand_b="open", look=(0.5, 0.25), head_tilt=-0.1)
    d.act("pinky", t1 - 0.3, 0.4, arm_f=PK["rest_f"], arm_b=PK["rest_b"], hand_f="relaxed",
          hand_b="relaxed", head_tilt=0.0)

    # =====================================================================
    # CENA 4 — O FINAL (absolute silence)
    # =====================================================================
    t_sil = t1 + 0.25
    tl.key("music.level", t_sil - 0.6, 1.0, "linear")
    tl.key("music.level", t_sil, 0.0, "inout")
    tl.to("amb.level", t_sil - 0.6, 0.6, 0.0)
    tl.key("cam.drift", t_sil - 0.01, 1.0, "linear")
    tl.key("cam.drift", t_sil, 0.0, "linear")
    d.cam("two_tight", t_sil)
    d.noblink["brain"].append((t_sil, t_sil + 20))
    d.act("brain", t_sil, 0.3, look=(0.6, -0.35), lid=0.3)
    d.turn("brain", t_sil + 0.9, -1.0, sound=False)
    d.act("brain", t_sil + 0.95, 0.9, look=(0.45, -0.6), head_turn=0.2)
    d.turn("brain", t_sil + 2.2, 1.0, sound=False)
    d.act("brain", t_sil + 2.25, 0.6, look=(0.6, -0.35), head_turn=0.35)
    d.act("brain", t_sil + 2.6, 1.0, smile=-0.25, lid=0.18, brow_l=0.55, brow_r=0.15,
          brow_tilt=-0.15)
    d.blink("brain", t_sil + 3.0, 0.14)
    d.blink("brain", t_sil + 3.25, 0.14)
    d.act("pinky", t_sil + 0.8, 0.5, look=(0.5, 0.25), smile=0.15)
    d.t = t_sil + 3.4
    # "Ao menos tens vista, Brain." — cheerful consolation, pointing at the map
    t0, t1 = d.say("p09")
    d.act("pinky", t0, 0.3, smile=0.8, head_tilt=-0.12, brow_l=0.6, brow_r=0.6,
          arm_f=PK["point"], hand_f="point", look=(0.6, -0.5))
    d.act("pinky", t1, 0.4, arm_f=PK["rest_f"], hand_f="relaxed", look=(0.5, 0.25))
    # Brain blue-screens
    t_push = t1 + 0.3
    d.cam("brain_med", t_push)
    d.cam("brain_xcu", t_push + 0.05, 3.2, "inout")
    d.act("brain", t_push + 0.4, 1.2, lid=0.03, pupil=0.32, brow_l=0.95, brow_r=0.05,
          brow_tilt=-0.3, smile=-0.2, jaw=0.22, mouth_wide=0.35, head_tilt=0.05,
          head_turn=0.15, look=(0.1, 0.0))
    d.act("brain", t_push + 1.4, 0.8, twitch=1.0, sweat=1.0)
    d.music("sad_trombone", t_push + 1.3)
    tl.key("music.level", t_push + 1.2, 1.0, "linear")
    t_cut = t_push + 3.6
    d.cam("pinky_cu", t_cut)
    d.act("pinky", t_cut - 0.05, 0.05, smile=1.0, jaw=0.38, mouth_wide=1.0, lid=0.0,
          lid_low=0.0, pupil=1.2, head_tilt=-0.18, look=(0.35, 0.15), brow_l=0.7, brow_r=0.7,
          arm_f=PK["rest_f"])
    d.sfx("plink", t_cut + 0.15, 0.5, PINKY_PAN)
    d.blink("pinky", t_cut + 0.7, 0.16)

    t_black = t_cut + 1.5
    tl.set("fx.black", t_black, 1.0)
    tl.to("end.a", t_black + 0.6, 0.8, 1.0)
    tl.to("end.b", t_black + 2.4, 0.6, 1.0)
    d.music("outro", t_black + 0.5, dur=4.0)
    end = t_black + 4.6
    tl.to("end.a", end - 0.8, 0.7, 0.0)
    tl.to("end.b", end - 0.8, 0.7, 0.0)

    d.finish(end)
    d.duration = end
    return d
