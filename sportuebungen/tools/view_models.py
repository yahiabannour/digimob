"""
******************************************************************************
 file:      tools/view_models.py
 brief:     Zeigt die Pose-Modelle einer Aufnahme uebereinander: das Skelett jedes Modells in
            eigener Farbe auf dem Video, darunter eine Zeitleiste, wann jedes Modell eine
            Wiederholung gezaehlt hat. So sieht man, wo und warum die Modelle auseinanderlaufen
            (Zahlen dazu: tools/compare_models.py).
 software:  Python (Version: 3.12)
 ext. libs: matplotlib (Version: 3.11.1), OpenCV (Version: 5.0.0.93), numpy
 info:      Aufruf aus sportuebungen/ (oder von ueberall):
              python tools/view_models.py recordings/bizepscurls/rec5
              python tools/view_models.py bizepscurls/rec5
            Gezeigt werden alle keypoints_<modell>.jsonl im Ordner der Aufnahme (full von der
            Aufnahme, lite und heavy von tools/extract_keypoints.py), wie tools/compare_models.py. Gezaehlt wird wie in
            systest.py. Bild und Skelette sind gespiegelt wie in der App: die linke Seite des
            Patienten ist links im Bild.
            Bedienung:
              Leertaste   abspielen / anhalten
              links/rechts  ein Frame zurueck / vor, hoch/runter 10 Frames
              v           Video ein/aus (nur Skelette)
              Klick in die Zeitleiste springt zu diesem Zeitpunkt, die Kaestchen blenden
              Modelle aus.
******************************************************************************
"""
import argparse
import json
import os
import sys

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.collections import LineCollection
from matplotlib.lines import Line2D
from matplotlib.widgets import CheckButtons, Slider

# Das Skript liegt in sportuebungen/tools/, die Module der App eine Ebene hoeher
APP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP_DIR)

from mediapipe_modelle import POSE_CONNECTIONS
from tools.compare_models import keypoint_files
from tools.extract_keypoints import read_frames
from tools.recording_format import SUMMARY_FILE, read_poses
from uebungen import UEBUNGEN

RECORDINGS_DIR = os.path.join(APP_DIR, "recordings")

# Feste Farbe pro Modell, damit sie in jeder Aufnahme gleich bleibt
COLORS = {"full": "tab:blue", "lite": "tab:green", "heavy": "tab:orange"}
OTHER_COLORS = ["tab:red", "tab:purple", "tab:brown", "tab:pink"]

# Zeitleiste: Markierung je Seite (Butterfly/Rotation haben nur "both")
MARKERS = {"left": "^", "right": "v", "both": "o"}

# Das Video nur als Hintergrund: halbe Groesse spart ~3/4 des Speichers
VIDEO_SCALE = 0.5

# Tasten, die matplotlib sonst selbst belegt (zurueck/vor in der Ansicht)
OWN_KEYS = ("left", "right", "v")


class Track:
    """Ein Modell einer Aufnahme: Skelett pro Frame (px, gespiegelt, None ohne Pose), Zaehlerstand
    pro Frame und die Frames, in denen eine Seite eine Wiederholung dazubekommt."""

    def __init__(self, label, color, path, summary):
        self.label, self.color, self.enabled = label, color, True
        width, height = summary["resolution"]
        uebung = UEBUNGEN[summary["exercise"]](target=None)
        self.timestamps, self.points, self.counters, self.events = [], [], [], []
        before = dict(uebung.counter)
        for i, (t, pose) in enumerate(read_poses(path, summary["resolution"])):
            uebung.update(pose, t)   # wie systest.py
            self.timestamps.append(t)
            self.points.append(None if pose is None else
                               np.array([((1 - lm.x) * width, lm.y * height) for lm in pose.landmarks]))
            counter = dict(uebung.counter)
            self.events += [(i, side) for side in counter if counter[side] > before[side]]
            self.counters.append(counter)
            before = counter

    def count_text(self, i):
        counter = self.counters[i]
        if len(counter) == 1:
            return f"{self.label}: {counter['both']}"
        return f"{self.label}: {min(counter.values())} (L {counter['left']}, R {counter['right']})"


def color_for(name, used):
    color = COLORS.get(name) or next(c for c in OTHER_COLORS + list(COLORS.values()) if c not in used)
    used.add(color)
    return color


def resolve(target):
    """Aufnahme-Ordner aus dem Argument: Pfad oder <uebung>/rec<N> unter recordings/."""
    for rec_dir in (target, os.path.join(RECORDINGS_DIR, target)):
        if os.path.exists(os.path.join(rec_dir, SUMMARY_FILE)):
            return os.path.abspath(rec_dir)
    raise SystemExit(f"Keine Aufnahme (ohne {SUMMARY_FILE}): {target}")


class Viewer:
    def __init__(self, rec_dir):
        with open(os.path.join(rec_dir, SUMMARY_FILE), encoding="utf-8") as f:
            summary = json.load(f)
        width, height = summary["resolution"]
        name = "/".join(os.path.normpath(rec_dir).split(os.sep)[-2:])

        used = set()
        self.tracks = []
        for model, path in keypoint_files(rec_dir, summary):
            label = model + (" (Aufnahme)" if os.path.basename(path) == summary["keypoints"] else "")
            self.tracks.append(Track(label, color_for(model, used), path, summary))
        self.timestamps = self.tracks[0].timestamps
        self.n = min(len(track.points) for track in self.tracks)

        # Ohne Video (git-ignored, z.B. auf einem anderen Rechner) nur die Skelette
        video = os.path.join(rec_dir, summary["video"])
        self.frames = [np.fliplr(f) for f in read_frames(video, VIDEO_SCALE)] if os.path.exists(video) else []

        per_side = summary.get("expected_per_side")
        expected = str(summary["expected_reps"]) + (f" (L {per_side['left']}, R {per_side['right']})"
                                                    if per_side else "")
        self.heading = f"{name}  ({summary['exercise']}, erwartet {expected})"
        fps = summary.get("measured_fps") or summary.get("fps_target") or 30

        for key in ("keymap.back", "keymap.forward"):
            plt.rcParams[key] = [k for k in plt.rcParams[key] if k not in OWN_KEYS]
        self.fig = plt.figure(figsize=(11, 9))
        self.fig.canvas.manager.set_window_title(f"Modelle: {name}")

        # Oben: Video mit den Skeletten
        self.ax_img = self.fig.add_axes((0.04, 0.34, 0.72, 0.6))
        self.ax_img.set_facecolor("0.92")
        self.ax_img.set_xlim(0, width)
        self.ax_img.set_ylim(height, 0)
        self.ax_img.set_xticks([])
        self.ax_img.set_yticks([])
        self.image = None
        if self.frames:
            self.image = self.ax_img.imshow(self.frames[0], extent=(0, width, height, 0))
        for track in self.tracks:
            track.lines = self.ax_img.add_collection(LineCollection([], colors=track.color, linewidths=2))
            track.dots, = self.ax_img.plot([], [], "o", color=track.color, markersize=3)
        self.legend = self.ax_img.legend(
            handles=[Line2D([], [], color=t.color, linewidth=3, label=t.label) for t in self.tracks],
            loc="upper left", fontsize=9, framealpha=0.8)

        # Unten: Zeitleiste der gezaehlten Wiederholungen, eine Zeile pro Modell
        self.ax_time = self.fig.add_axes((0.14, 0.11, 0.62, 0.17))
        rows = len(self.tracks)
        for row, track in enumerate(self.tracks):
            y = rows - 1 - row
            self.ax_time.axhline(y, color=track.color, linewidth=0.5, alpha=0.4)
            for side in MARKERS:
                times = [self.timestamps[i] for i, s in track.events if s == side and i < self.n]
                self.ax_time.scatter(times, [y] * len(times), marker=MARKERS[side], s=70, color=track.color,
                                     edgecolors="black", linewidths=0.5, zorder=3)
        self.ax_time.set_yticks(range(rows), [t.label for t in reversed(self.tracks)], fontsize=9)
        self.ax_time.set_ylim(-0.6, rows - 0.4)
        self.ax_time.set_xlim(0, self.timestamps[self.n - 1])
        self.ax_time.set_xlabel("Zeit (s)")
        self.ax_time.set_title("gezaehlte Wiederholungen (▲ links, ▼ rechts, ● beide Arme)", fontsize=9)
        self.cursor = self.ax_time.axvline(0, color="black", linewidth=1)

        # Frame-Regler, Modelle ein/aus, Hilfe
        self.slider = Slider(self.fig.add_axes((0.1, 0.03, 0.6, 0.03)), "Frame", 0, self.n - 1,
                             valinit=0, valstep=1, valfmt="%d")
        self.slider.vline.set_visible(False)   # Startmarke des Reglers, hier ohne Bedeutung
        self.slider.on_changed(lambda value: self.show(int(value)))
        self.checks = CheckButtons(self.fig.add_axes((0.79, 0.72, 0.19, 0.04 * rows + 0.02)),
                                   [t.label for t in self.tracks], [True] * rows,
                                   label_props={"color": [t.color for t in self.tracks]})
        self.checks.on_clicked(self.toggle_model)
        self.fig.text(0.79, 0.68, "Leertaste: abspielen/anhalten\n← →: 1 Frame   ↑ ↓: 10 Frames\n"
                                  "v: Video ein/aus\nKlick in die Zeitleiste: springen",
                      fontsize=9, va="top")

        self.timer = self.fig.canvas.new_timer(interval=round(1000 / fps))
        self.timer.add_callback(self.step)
        self.playing = False
        self.fig.canvas.mpl_connect("key_press_event", self.on_key)
        self.fig.canvas.mpl_connect("button_press_event", self.on_click)
        self.show(0)

    def show(self, i):
        """Zeichnet Frame i: Video, Skelette, Zaehlerstaende, Position in der Zeitleiste."""
        self.frame = i
        if self.image is not None:
            self.image.set_data(self.frames[min(i, len(self.frames) - 1)])
        for track, text in zip(self.tracks, self.legend.get_texts()):
            points = track.points[i]
            visible = track.enabled and points is not None
            if points is not None:
                track.lines.set_segments([points[[a, b]] for a, b in POSE_CONNECTIONS])
                track.dots.set_data(points[:, 0], points[:, 1])
            track.lines.set_visible(visible)
            track.dots.set_visible(visible)
            text.set_text(track.count_text(i) + ("" if points is not None else "  (keine Pose)"))
        t = self.timestamps[i]
        self.ax_img.set_title(f"{self.heading}   Frame {i + 1}/{self.n}   {t:.2f} s", fontsize=10)
        self.cursor.set_xdata([t, t])
        self.fig.canvas.draw_idle()

    def go(self, i):
        self.slider.set_val(max(0, min(self.n - 1, i)))   # ruft show() auf

    def step(self):
        if self.frame >= self.n - 1:
            self.toggle_play()
        else:
            self.go(self.frame + 1)

    def toggle_play(self):
        self.playing = not self.playing
        if self.playing:
            if self.frame >= self.n - 1:
                self.go(0)
            self.timer.start()
        else:
            self.timer.stop()

    def toggle_model(self, label):
        for track in self.tracks:
            if track.label == label:
                track.enabled = not track.enabled
        self.show(self.frame)

    def on_key(self, event):
        steps = {"left": -1, "right": 1, "down": -10, "up": 10}
        if event.key == " ":
            self.toggle_play()
        elif event.key in steps:
            self.go(self.frame + steps[event.key])
        elif event.key == "v" and self.image is not None:
            self.image.set_visible(not self.image.get_visible())
            self.fig.canvas.draw_idle()

    def on_click(self, event):
        if event.inaxes is self.ax_time and event.xdata is not None:
            self.go(int(np.argmin(np.abs(np.array(self.timestamps[:self.n]) - event.xdata))))


def main():
    parser = argparse.ArgumentParser(description="Zeigt die Skelette aller Pose-Modelle einer Aufnahme.")
    parser.add_argument("recording", help="Aufnahme-Ordner, z.B. recordings/bizepscurls/rec5 oder bizepscurls/rec5")
    viewer = Viewer(resolve(parser.parse_args().recording))
    plt.show()
    return viewer


if __name__ == "__main__":
    main()
