# Director API — writing `pab/episode.py`

Everything below is available inside `build()` in `pab/episode.py`. The bundled
example (`O Plano Perfeito`) uses every feature. Read it before you write a new
episode and copy its patterns.

## Contents
1. Time & dialogue
2. Acting: character parameters
3. Arm poses (`BR`, `PK`) and IK
4. Staging & cameras (`CAM`)
5. Set, light & screen effects (timeline tracks)
6. Sound effects & music cues
7. Recipes (beats that work)
8. Pitfalls learned the hard way

---

## 1. Time & dialogue

`d = Director(clips)` keeps a cursor `d.t` in seconds.

| Call | Effect |
|---|---|
| `t0, t1 = d.say("b03")` | Places line `b03` at `d.t` (or `at=`). It adds lip-sync, head-bob and subtitle cues, and moves `d.t` to the end of the line. |
| `d.wait(0.3)` | Advances the cursor (a pause / reaction beat). |
| `clips["b03"].duration` | Real duration of the voiced line. Time gestures as fractions of it. |
| `clips["b13"].words` | `(start, end, word)` timings relative to the clip. Use them to hit a gesture on an exact word. |
| `d.finish(end)` + `d.duration = end` | Must close `build()`. It adds automatic blinks and fixes subtitle overlaps. |

Gaps between lines: use 0.1–0.3 s normally, 0.4–0.6 s for a smug beat, and 2–3.5 s
of silence for the big realisation. A 60–90 s video holds ~45–65 s of speech
(≈ 20–24 lines).

## 2. Acting: character parameters

`d.act(who, t, dur, ease="inout", **params)` animates from the current value
to the new one. Use `dur=0` for an instant change. `who` is `"brain"` or `"pinky"`.
Eases: `linear`, `in`, `out`, `inout`, `sine`, `back` (overshoot snap),
`elastic`, `step`.

| Param | Range / meaning |
|---|---|
| `x`, `y` | Feet position in the world. Defaults: Brain x=720, Pinky x=1140, floor y=960. |
| `facing` | `1` = looks right (Brain toward Pinky), `-1` = looks left (Pinky toward Brain; Brain toward the map). Use `d.turn(who, t, facing)` to flip it with a squash + swish. |
| `lean`, `bob`, `squash` | Body tilt (rad), vertical offset (px, negative = up), squash/stretch (1 = none). |
| `head_tilt`, `head_turn` | Head roll (rad, ±0.25), 3/4 turn of the face (0–0.5). |
| `brow_l`, `brow_r` | −1 (furrowed) … 1 (raised), one per side. One raised = sceptical. |
| `brow_tilt` | Brain's grumpy V (0.3 default, 0.9 evil, −0.3 worried). |
| `lid`, `lid_low` | Upper lid 0 (wide open) … 1 (closed); lower lid "squint" 0 … 0.4. Brain's default is 0.42 (heavy lids). |
| `look` | `(x, y)` pupils −1…1, x forward. |
| `pupil` | 1 normal, 0.3 = tiny "brain freeze", 1.2 = cute. |
| `smile`, `smirk` | −1 frown … 1 grin; asymmetric corner. |
| `jaw`, `mouth_wide` | Extra mouth opening on top of lip-sync (slack jaw, open grin); mouth width. |
| `arm_f`, `arm_b` | Front/back arm `(shoulder, elbow)` angles. Use the presets below. |
| `hand_f`, `hand_b` | `"relaxed"`, `"open"`, `"point"`, `"fist"`. |
| `pointer` | Brain holds the map pointer (1/0). Use it with the `map_*` arm poses. |
| `scratch` | 1 = jiggle the front arm (head scratch). |
| `sweat`, `twitch` | Sweat drop 0…1; eyelid twitch 0…1. |
| `walk`, `walk_phase` | Driven by `d.walk(who, t0, x0, x1, dur)`, which also adds footsteps. |
| `d.blink(who, t)` | A forced blink. Add `(t0, t1)` to `d.noblink[who]` to freeze a stare. |

### Expression presets (copy these)
- **Brain smug:** `lid=0.5, brow_tilt=0.35, smile=0.15`
- **Brain evil grin:** `smile=0.9, lid=0.38, lid_low=0.18, brow_tilt=0.75`
- **Brain annoyed:** `lid=0.62, brow_tilt=0.75, brow_l=-0.3, brow_r=-0.3, smile=-0.35`
- **Brain doubt:** `smile=-0.25, lid=0.18, brow_l=0.55, brow_r=0.15, brow_tilt=-0.15`
- **Brain blank / brain freeze:** `lid=0.03, pupil=0.32, brow_l=0.95, brow_r=0.05, brow_tilt=-0.3, smile=-0.2, jaw=0.22` + `twitch=1, sweat=1`
- **Pinky delighted:** `smile=1.0, brow_l=0.9, brow_r=0.9, lid_low=0.15`
- **Pinky curious:** `head_tilt=-0.22, brow_l=0.8, brow_r=-0.1, look=(0.8,-0.6)`
- **Pinky innocent grin:** `smile=1.0, jaw=0.38, mouth_wide=1.0, lid=0, pupil=1.2, head_tilt=-0.18`

## 3. Arm poses and IK

`BR` (Brain): `rest_f/rest_b, fist_up, point_up, explain, explain_b, rub_f/rub_b`
(evil hand-rubbing), `spread_f/spread_b` (arms wide), `map_hi/map_mid/map_lo`
(pointer at the map; requires `facing=-1, pointer=1`), `tense_f/tense_b`, `count`,
`dismiss`.

`PK` (Pinky): `rest_f/rest_b, eager_f/eager_b` (fists at chest), `point`,
`scratch` (+ `scratch=1`), `shrug_f/shrug_b`, `chin` (thinking).

New poses: `_ik(PINKY_ARM or BRAIN_ARM, front=True/False, (x, y), elbow="out"|"back"|"down")`
puts the hand at a local point. The character's local origin is between the feet,
−y is up and +x is forward. Brain's head spans y ≈ −390…−145. Pinky's head is at
≈ (−50…+160, −460…−330) and his chest is at y ≈ −300.

## 4. Staging & cameras

`d.cam(name, t, dur=0)` cuts (dur=0) or moves (dur>0) **both** formats at once.
Every preset in `CAM` holds a 16:9 framing and a 9:16 framing:
`wide, two, two_tight, brain_med, brain_cu, brain_xcu, pinky_med, pinky_cu,
brain_map, map, monitors`. For a custom move pass a tuple:
`d.cam(((cx, cy, zoom), (cx_v, cy_v, zoom_v)), t, dur)`.
In 16:9, zoom 1 shows the whole 1920×1080 set. In 9:16, keep the characters
above y ≈ 1400 on screen, because the subtitles sit at 79 % of the height.

Editing grammar that works: an establishing wide shot, then a two-shot for
exchanges, a single on whoever delivers a punchline, inserts (map / monitors)
while Brain explains, a slow push-in (`dur` 2–4 s, `inout`) for tension, and
a punch-in (0.3 s, `out`) on big lines.

## 5. Set, light & screen effects (timeline tracks)

Use `tl.to(track, t0, dur, value)`, `tl.key(track, t, value, ease)` or `tl.set(track, t, value)`.

| Track | Meaning |
|---|---|
| `lab.crisis` 0…1 | Red hot-spots appear on the map; the charts fall; the alert bars rise. |
| `lab.mapdead` 0…1 | The map goes grey and cracks. |
| `lab.nosignal` 0…3 | Monitors die one by one ("SEM SINAL" + static). Pair each with `d.sfx("static")`. |
| `lab.flicker` | Monitors flash white. |
| `light.dark`, `light.red` | Dramatic dim, red under-light on Brain. |
| `fx.flash`, `fx.bars`, `fx.black` | Lightning flash, letterbox bars, fade/cut to black. |
| `cam.shake`, `cam.drift` | Impact shake; handheld drift (set 0 for frozen tension). |
| `title.a`, `title.pop` | Title card opacity and scale (the cold open). |
| `end.a`, `end.b` | End text (`END_TEXT`) and "FIM". |
| `amb.level`, `music.level` | Lab ambience and the music bus (0 = absolute silence). |

The texts on the props (map title, sticky notes, clippings, TV headline,
ticker, alert bars, month) come from `SET` in `pab/script.py`. Rewrite them so
they support each episode's joke.

## 6. Sound effects & music cues

`d.sfx(name, t, gain, pan)` takes these names: `whoosh, whoosh_big, swish, step,
knock, scratch, thunder, static, bwip, bwip_down, pop, plink`.
Pan: `BRAIN_PAN` (−0.18) / `PINKY_PAN` (+0.18).

`d.music(name, t, ...)` (all synthesised and original):

| Cue | Use |
|---|---|
| `intro` (dur) | Timpani roll + brass stab under the title (~4 s). |
| `theme` (dur, gain) | Sneaky comic D-minor loop under the dialogue (auto-ducked). |
| `drone` (dur) | Sinister build-up. |
| `hit` | Orchestral hit; pair it with `thunder`, a flash and a shake. |
| `suspense` (dur) | Quiet tremolo before a revelation. |
| `sting` | "Dun-dun-DUUUN" after the decisive line. |
| `sad_trombone` | Comic "wah-wah" on the defeat / realisation. |
| `outro` (dur) | Gentle button under the end card. |

To stop the music, key `music.level` down to 0. Remember to bring it back up
before the next cue.

## 7. Recipes

- **Cold open (0–4 s):** start with `fx.black` 1→0, the camera on `monitors`, then a move to `wide`, with `intro` music and the title card. Brain taps the map (`map_hi`/`map_lo` + `knock`).
- **Pinky enters:** `d.walk("pinky", t, 2200, 1140, 2.9)`, then the classic opening exchange.
- **Explanation montage:** cut to the `map` insert while `lab.crisis` rises with `pop` SFX, then the `monitors` insert, then back to `brain_map` with pointer taps on the listed words.
- **Villain climax:** turn `light.dark`/`light.red`/`fx.bars` on, add `drone` and a push to `brain_cu`. On the shouted word, use `spread_f/spread_b` + `hit` + `thunder` + `fx.flash` + `cam.shake`, then restore the lights.
- **Pinky's innocent question:** add `suspense`; Pinky points or shrugs; the world breaks down (`lab.nosignal`, `lab.mapdead`); then a `sting` on Brain.
- **Punchline:** absolute silence (`music.level` 0, `amb.level` 0, turns with `sound=False`). Brain looks at Pinky, then the map, then Pinky, with no blinking. Push in to `brain_xcu` into the brain-freeze face, then `sad_trombone`. Cut to `pinky_cu` with the innocent grin + `plink`, then `fx.black`=1 and the end card + `outro`.

## 8. Pitfalls learned the hard way

- **Hands in front of faces.** The heads are huge and the arms are short. Raised front arms cover the face. Use `spread_*` (horizontal at shoulder height) for "arms wide", `eager_*` (chest) for excitement, and IK with `elbow="back"` for the head scratch. Always check stills.
- **Hands over the mouth while talking.** Move a `chin` hand back to `rest_f` just before the line starts.
- **Silence must be silent.** `d.turn` plays a swish; pass `sound=False` inside silent beats. Check with `review.py audio` (< −45 dB).
- **Subtitles** are chunked automatically (≤ 80 chars, preferring punctuation). Keep each line ≤ ~140 characters, or split it into two `LINES` entries.
- **Pronunciation:** pt-PT TTS reads English names oddly. Use `tts="Brêin"` / `"Pínqui"`. A lowercase `tts` avoids shouted words being spelled out ("ENTRAREI" → `tts="Entrarei... em ação!"`).
- **Duration drifts** when lines change, because everything is timed from the real voice lengths. Print `d.duration` and trim pauses or the end card to stay ≤ 90 s.
- **Camera moves vs cuts:** a `d.cam` cut or move automatically cancels any camera move that is still running, so a long push-in can safely be interrupted by a cut. For other tracks (`d.act`, `tl.to`), a long transition that is still running when you author a later change for the same parameter will interpolate strangely. Keep transitions shorter than the gap to the next change.
- **Map insert in 9:16:** the subtitle band (≈ 70–80 % height) can cover Brain when he stands under the map. Frame the vertical map insert on the map only (`map` preset), or raise `cy_v`.
- **Discrete values** (`hand_f`, strings) switch at the *start* of an `act`.
- **Author chronologically** per track: `d.act` reads the current value at `t`, so keys written out of order give surprising interpolation.
