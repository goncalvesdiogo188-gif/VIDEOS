---
name: pinky-brain-episode
description: Produces a complete animated 2D comedy/satire short (60–90 s) starring the lab mice Pinky and Brain, from a one-line idea or a full script. It covers the script in Portuguese (Portugal), neural pt-PT voices with lip-sync, expressive character animation, camera direction, original synthesized music and SFX, burned-in subtitles, and MP4 exports in 16:9 (1920×1080) plus a 9:16 vertical cut for Reels/TikTok/Shorts. Use this skill whenever the user wants a new Pinky & Brain video or episode, a "plano para conquistar o mundo", a satirical cartoon short with these two mice, or a social-media animated sketch in their style — even if they only give a theme ("faz um episódio sobre a inflação"), ask for "outro vídeo como o anterior", or say "vídeo/curta/episódio/sketch animado do Pinky e do Cérebro".
---

# Pinky & Brain — episode factory

This skill bundles a complete animation engine (`engine/`). Every episode reuses
the same rigs (modelled on the classic look), the same night-lab set, the same
voices and the same renderer. **The creative work is only two files:**
`pab/script.py` (what is said and written on the props) and `pab/episode.py`
(how it is staged). Everything else is plumbing that already works. Keep your
effort on the jokes, the acting and the timing.

## Requirements

- Python 3.10+, `ffmpeg` with libx264, and internet access the first time each line is voiced (Microsoft neural TTS through `edge-tts`).
- `pip install -r requirements.txt` (numpy, scipy, pycairo, edge-tts).
- On machines behind a TLS-inspecting proxy, export `SSL_CERT_FILE=<proxy CA bundle>` before rendering; `voices.py` picks it up. In Claude Code cloud sessions that bundle is `/root/.ccr/ca-bundle.crt`.

## Workflow

### 1. Pin down the idea (briefly)
If the user gave only a theme, decide the comic premise yourself: what is Brain's
"brilliant" plan this time, and what innocent question or remark from Pinky
collapses it? Ask only if something essential is ambiguous (for example a real
event they want satirised). Satirise systems and behaviours, never named real
politicians or private people. Use generic leaders, summits, committees,
companies and influencers.

### 2. Create the project
```bash
python3 <skill>/scripts/new_project.py <project-dir>
cd <project-dir> && pip install -r requirements.txt
```
The copied `pab/script.py` and `pab/episode.py` hold the reference episode
*"O Plano Perfeito"*. Read both before writing; they are the best guide to the
style and the API.

### 3. Write the script — `pab/script.py`
- `EPISODE` holds the title on the title card and the `slug` used for output names. `END_TEXT` is the closing aphorism. `SET` holds the prop texts (map title, board title, 3 sticky notes forming the plan, 3 newspaper clippings, TV headline, ticker, chart/alert titles and 4 alert bars, month), all short and in capitals, all reinforcing the joke.
- `LINES` lists the dialogue: `id`, `who` ("BRAIN"/"PINKY"), `text` (subtitle). Add `tts` only for pronunciation (`"Brêin"`, `"Pínqui"`, lowercase for shouted lines), and `rate`/`pitch`/`volume` only for delivery (slower + lower for dramatic Brain lines, louder for the villain shout).
- Write European Portuguese: "estás a começar", "tu vais", "ecrã", "equipa", never Brazilian forms. Keep it natural and spoken.
- Shape (60–90 s, ~20–24 lines, ~45–65 s of speech):
  1. The ritual opener: "Brain, o que vamos fazer esta noite?" / "O mesmo que fazemos todas as noites, Pinky. Tentar conquistar o mundo!"
  2. The plan, explained with arrogant certainty; visual inserts carry the satire.
  3. The villain climax line (the shout).
  4. Pinky's genuine, naive question that exposes the flaw. He is not trying to be clever; that sincerity is the joke.
  5. Silence, Brain's realisation, the brain-freeze face, Pinky's innocent smile, black screen, end text.
- Split any line over ~140 characters into two entries; the subtitle chunks and the acting both read better that way.

### 4. Voice it and get the durations
```bash
python3 render.py --still 0
```
This synthesises any new or changed lines (cached in `assets/voices/`) and
prints the episode duration. Print `clips[id].duration` for each line if you
need them while staging.

### 5. Stage it — `pab/episode.py`
Rewrite `build()` scene by scene with the Director API. **Read
`references/director.md` first.** It lists every parameter, pose, camera, track,
SFX and music cue, plus recipes for each beat and the pitfalls already found
(hands covering faces, swishes breaking silence, etc.). Keep the example's
skeleton: defaults block → cold open + title → scenes → `d.finish(end)`;
`d.duration = end`.
Make the characters *act*. Every line deserves an expression change and usually
a gesture, the listener reacts too, and the camera cuts on story beats. Static
talking heads are the failure mode to avoid.

### 6. Review before the final render
Do not skip this step; it is where most problems are caught.
```bash
python3 render.py --preview --still 3 8 15 ...        # 10–16 key moments, both formats
python3 <skill>/scripts/review.py sheet sheet_h.png output/stills/horizontal_*
python3 <skill>/scripts/review.py sheet sheet_v.png output/stills/vertical_*
```
Look at the sheets (Read the PNGs). Check that the faces are not covered, that
the framing works in 9:16 (characters above the subtitle band), that the subtitles
are complete sentences and that the title/end cards read well. Then render a
preview and check the sound:
```bash
python3 render.py --preview
python3 <skill>/scripts/review.py audio
```
Dialogue seconds should sit around −14…−20 dB RMS, silence beats below −45 dB,
and the stings should appear where planned. Total duration should be ≤ 90 s.

### 7. Final render and delivery
```bash
python3 render.py            # both formats, ~3 min each on 4 cores
```
This outputs `output/<slug>_16x9.mp4`, `output/<slug>_9x16.mp4` and
`output/legendas_pt-PT.srt`. Send the videos to the user, summarise the
episode (premise, punchline, duration), and say how to tweak it (edit
`script.py` / `episode.py`, re-run `render.py`).

## Notes for the user-facing summary
- Pinky and Brain are Warner Bros. characters. Remind the user once that monetised or widely shared parodies may receive copyright claims; all music, SFX and art produced by the engine are original.
- You cannot listen to the audio. Say that the voices and the mix were checked by measurement (levels and silences), and invite the user to listen and ask for changes in delivery.
