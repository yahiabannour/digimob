"""
******************************************************************************
 file:      tools/compare_models.py
 brief:     Vergleicht die Pose-Modelle an den Aufnahmen: jede keypoints_<modell>.jsonl einer Aufnahme
            wird wie im Systemtest in die Uebung gespielt (gezaehlt gegen Ground Truth), dazu
            Erkennungsrate, Sichtbarkeit, Zittern und Abweichung vom Modell der App.
 software:  Python (Version: 3.12)
 ext. libs: numpy
 info:      Aufruf aus sportuebungen/ (oder von ueberall):
              python tools/compare_models.py                     alle Aufnahmen
              python tools/compare_models.py bizepscurls         alle Aufnahmen einer Uebung
              python tools/compare_models.py recordings/bizepscurls/rec1 ...
            Weitere Modelle erzeugt tools/extract_keypoints.py aus dem Video.
            Spalten:
              gezaehlt  gezaehlt/erwartet (links, rechts), OK wie systest.py: total, und pro Seite nur,
                        wenn expected_per_side in summary.json steht
              Pose      Anteil der Frames mit erkannter Pose
              Sicht     mittlere visibility von Schultern, Ellbogen, Handgelenken und Hueften
              Zittern   mittlere zweite Differenz der Hauptpunkte in px (|p[t] - 2 p[t-1] + p[t-2]|):
                        eine ruhige Bewegung bleibt klein, Springen der Punkte macht sie gross
              Abw.      mittlerer Abstand der Hauptpunkte in px zur Referenz (*): die Keypoints-Datei
                        von der Aufnahme, also das Modell der App
******************************************************************************
"""
import argparse
import glob
import json
import os
import sys

import numpy as np

# Das Skript liegt in sportuebungen/tools/, die Module der App eine Ebene hoeher
APP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP_DIR)

from tools.recording_format import SUMMARY_FILE, read_poses
from uebungen import UEBUNGEN

RECORDINGS_DIR = os.path.join(APP_DIR, "recordings")

# Hauptpunkte: Nase, Schultern, Ellbogen, Handgelenke, Hueften
KEY_POINTS = [0, 11, 12, 13, 14, 15, 16, 23, 24]
BODY_POINTS = [11, 12, 13, 14, 15, 16, 23, 24]


def recordings(targets):
    """Aufnahme-Ordner aus den Argumenten: Uebungsnamen oder Ordner, ohne Argumente alle."""
    rec_dirs = []
    for target in targets or sorted(os.listdir(RECORDINGS_DIR)):
        if os.sep not in target:   # Uebung, z.B. bizepscurls
            rec_dirs += sorted(os.path.dirname(p) for p in
                               glob.glob(os.path.join(RECORDINGS_DIR, target, "*", SUMMARY_FILE)))
        else:
            rec_dirs.append(os.path.abspath(target))
    return rec_dirs


def keypoint_files(rec_dir, summary):
    """[(Name, Pfad)] aller Keypoints-Dateien der Aufnahme, die von der Aufnahme zuerst (Referenz)."""
    files = sorted(glob.glob(os.path.join(rec_dir, "keypoints_*.jsonl")),
                   key=lambda p: (os.path.basename(p) != summary["keypoints"], p))
    return [(os.path.basename(p)[len("keypoints_"):-len(".jsonl")], p) for p in files]


def evaluate(path, summary):
    """Spielt die Keypoints in die Uebung (wie systest.py) und misst die Keypoints selbst."""
    uebung = UEBUNGEN[summary["exercise"]](target=None)
    width, height = summary["resolution"]
    points = []   # pro Frame die Hauptpunkte in px, None ohne Pose
    visibility = []
    for t, pose in read_poses(path, summary["resolution"]):
        uebung.update(pose, t)
        if pose is None:
            points.append(None)
            continue
        points.append(np.array([(pose.landmarks[i].x * width, pose.landmarks[i].y * height) for i in KEY_POINTS]))
        visibility.append(np.mean([pose.landmarks[i].visibility for i in BODY_POINTS]))

    jitter = [np.linalg.norm(a - 2 * b + c, axis=1).mean()
              for a, b, c in zip(points[2:], points[1:], points)
              if a is not None and b is not None and c is not None]

    per_side = summary.get("expected_per_side")
    ok = uebung.total == summary["expected_reps"] and (not per_side or uebung.counter == per_side)
    return {
        "total": uebung.total,
        "counter": dict(uebung.counter) if len(uebung.sides) > 1 else None,
        "ok": ok,
        "pose": sum(p is not None for p in points) / max(len(points), 1),
        "visibility": np.mean(visibility) if visibility else float("nan"),
        "jitter": np.mean(jitter) if jitter else float("nan"),
        "points": points,
    }


def deviation(points, reference):
    """Mittlerer Abstand der Hauptpunkte in px ueber die Frames, in denen beide eine Pose haben."""
    if len(points) != len(reference):
        return float("nan")
    d = [np.linalg.norm(a - b, axis=1).mean() for a, b in zip(points, reference) if a is not None and b is not None]
    return np.mean(d) if d else float("nan")


def parse_args():
    parser = argparse.ArgumentParser(description="Vergleicht die Pose-Modelle an den Aufnahmen.")
    parser.add_argument("targets", nargs="*", help="Uebungen oder Aufnahme-Ordner (Standard: alle)")
    return parser.parse_args()


def main():
    args = parse_args()
    totals = {}   # Modell -> Liste der Ergebnisse
    row = "  {:<18} {:<24} {:>5} {:>6} {:>9} {:>8}"
    for rec_dir in recordings(args.targets):
        with open(os.path.join(rec_dir, SUMMARY_FILE), encoding="utf-8") as f:
            summary = json.load(f)
        per_side = summary.get("expected_per_side")
        expected = str(summary["expected_reps"]) + (f" (links {per_side['left']}, rechts {per_side['right']})"
                                                    if per_side else "")
        label = "/".join(os.path.normpath(rec_dir).split(os.sep)[-2:])
        print(f"\n{label}   {summary['exercise']}, erwartet {expected}")
        print(row.format("Modell", "gezaehlt", "Pose", "Sicht", "Zittern", "Abw."))
        reference = None
        for name, path in keypoint_files(rec_dir, summary):
            result = evaluate(path, summary)
            if reference is None:
                reference, name = result, name + " *"
                result["deviation"] = float("nan")
            else:
                result["deviation"] = deviation(result["points"], reference["points"])
            counted = f"{result['total']}/{summary['expected_reps']}"
            if result["counter"]:
                counted += f" ({result['counter']['left']}, {result['counter']['right']})"
            counted += " OK" if result["ok"] else " FALSCH"
            dev = "-" if np.isnan(result["deviation"]) else f"{result['deviation']:.1f} px"
            print(row.format(name, counted, f"{result['pose']:.0%}", f"{result['visibility']:.2f}",
                             f"{result['jitter']:.2f} px", dev))
            totals.setdefault(name.removesuffix(" *"), []).append(result)

    print("\nZusammenfassung pro Modell (* = Referenz in der Aufnahme, meist das Modell der App)")
    print(row.format("Modell", "richtig gezaehlt", "Pose", "Sicht", "Zittern", "Abw."))
    for name, results in totals.items():
        devs = [r["deviation"] for r in results if not np.isnan(r["deviation"])]
        print(row.format(name, f"{sum(r['ok'] for r in results)}/{len(results)}",
                         f"{np.mean([r['pose'] for r in results]):.0%}",
                         f"{np.nanmean([r['visibility'] for r in results]):.2f}",
                         f"{np.nanmean([r['jitter'] for r in results]):.2f} px",
                         f"{np.mean(devs):.1f} px" if devs else "-"))


if __name__ == "__main__":
    main()
