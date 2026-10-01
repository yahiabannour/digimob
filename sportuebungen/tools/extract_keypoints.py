"""
******************************************************************************
 file:      tools/extract_keypoints.py
 brief:     Erzeugt die Keypoints einer Aufnahme nachtraeglich aus ihrem Video, mit einem beliebigen
            Pose-Modell (lite, full, heavy). So lassen sich die Modelle an denselben Aufnahmen
            vergleichen (tools/compare_models.py), ohne neu aufzunehmen: das Video ist die
            Grundlage, die Keypoints sind daraus abgeleitet.
 software:  Python (Version: 3.12)
 ext. libs: OpenCV (Version: 5.0.0.93), Mediapipe (Version: 1.0.1, Tasks-API)
 info:      Aufruf aus sportuebungen/ (oder von ueberall):
              python tools/extract_keypoints.py lite                          alle Aufnahmen
              python tools/extract_keypoints.py lite recordings/bizepscurls/rec1 ...
            Schreibt keypoints_<modell>.jsonl neben das Video (Format: tools/recording_format.py),
            summary.json bleibt unveraendert.
              - jedes Frame des Videos wird ausgewertet (kein Echtzeit-Abspielen wie FileCamera)
              - die Zeitstempel kommen aus der Keypoints-Datei der Aufnahme (summary.json: keypoints),
                im VIDEO-Modus haengt das Ergebnis von ihnen ab. Gleiche Zeitstempel -> bei jedem
                Aufruf dieselbe Datei.
              - die bei der Aufnahme geschriebene Keypoints-Datei wird nie ueberschrieben, andere
                vorhandene nur mit --force
              --out DIR: schreibt nach DIR/<uebung>/rec<N>/ statt neben das Video
            Das Modell muss in models/ liegen, sonst steht der Download-Link in der Fehlermeldung.
******************************************************************************
"""
import argparse
import json
import os
import sys
import time

import cv2

# Das Skript liegt in sportuebungen/tools/, die Module der App eine Ebene hoeher
APP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP_DIR)

from mediapipe_modelle import POSE_MODELS, MediapipeModelle
from tools.recording_format import SUMMARY_FILE, keypoints_file, landmarks_to_list, write_keypoints

RECORDINGS_DIR = os.path.join(APP_DIR, "recordings")


def all_recordings():
    """Alle Aufnahme-Ordner unter recordings/ mit summary.json, sortiert."""
    found = []
    for exercise in sorted(os.listdir(RECORDINGS_DIR)):
        exercise_dir = os.path.join(RECORDINGS_DIR, exercise)
        if os.path.isdir(exercise_dir):
            found += [os.path.join(exercise_dir, rec) for rec in sorted(os.listdir(exercise_dir))
                      if os.path.exists(os.path.join(exercise_dir, rec, SUMMARY_FILE))]
    return found


def recorded_timestamps(rec_dir, summary):
    """Zeitstempel (s) aus der bei der Aufnahme geschriebenen Keypoints-Datei."""
    with open(os.path.join(rec_dir, summary["keypoints"]), encoding="utf-8") as f:
        return [json.loads(line)["timestamp"] for line in f]


def read_frames(video_path, scale=1.0):
    """Alle Frames des Videos als RGB, mit scale verkleinert (tools/view_models.py spart so Speicher)."""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"Video laesst sich nicht oeffnen: {video_path}")
    frames = []
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break
        if scale != 1.0:
            frame_bgr = cv2.resize(frame_bgr, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        frames.append(cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB))
    cap.release()
    return frames


def extract(rec_dir, model, out_path):
    """Wertet das Video der Aufnahme mit dem Modell aus, schreibt out_path und gibt ms pro Frame
    (nur die Pose-Erkennung) zurueck."""
    with open(os.path.join(rec_dir, SUMMARY_FILE), encoding="utf-8") as f:
        summary = json.load(f)
    timestamps = recorded_timestamps(rec_dir, summary)
    frames = read_frames(os.path.join(rec_dir, summary["video"]))
    if len(frames) != len(timestamps):
        raise RuntimeError(f"{len(frames)} Frames im Video, aber {len(timestamps)} Zeitstempel in "
                           f"{summary['keypoints']}")

    # Neue Instanz pro Aufnahme: der VIDEO-Modus glaettet ueber die vorigen Frames
    detector = MediapipeModelle(POSE_MODELS[model])
    poses = []
    start = time.perf_counter()
    for t, frame_rgb in zip(timestamps, frames):
        pose = detector.detect(frame_rgb, round(t * 1000))
        poses.append(landmarks_to_list(pose.landmarks) if pose is not None else None)
    ms_per_frame = (time.perf_counter() - start) * 1000 / max(len(frames), 1)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    write_keypoints(out_path, timestamps, poses)
    with_pose = sum(1 for p in poses if p is not None)
    print(f"{os.path.relpath(out_path, APP_DIR)}: {len(frames)} Frames, {with_pose} mit Pose, "
          f"{ms_per_frame:.1f} ms/Frame")
    return ms_per_frame


def parse_args():
    parser = argparse.ArgumentParser(
        description="Erzeugt keypoints_<modell>.jsonl nachtraeglich aus dem Video einer Aufnahme.")
    parser.add_argument("model", choices=list(POSE_MODELS), help="Pose-Modell")
    parser.add_argument("recordings", nargs="*",
                        help="Aufnahme-Ordner recordings/<uebung>/rec<N> (Standard: alle)")
    parser.add_argument("--force", action="store_true",
                        help="vorhandene Keypoints-Datei ueberschreiben (nie die von der Aufnahme)")
    parser.add_argument("--out", help="Ausgabe nach OUT/<uebung>/rec<N>/ statt neben das Video")
    return parser.parse_args()


def main():
    args = parse_args()
    rec_dirs = [os.path.abspath(d) for d in args.recordings] or all_recordings()
    times = []
    for rec_dir in rec_dirs:
        with open(os.path.join(rec_dir, SUMMARY_FILE), encoding="utf-8") as f:
            summary = json.load(f)
        name = keypoints_file(args.model)
        if args.out:
            out_dir = os.path.join(args.out, *os.path.normpath(rec_dir).split(os.sep)[-2:])
        else:
            out_dir = rec_dir
            if name == summary["keypoints"]:
                print(f"{os.path.relpath(rec_dir, APP_DIR)}: {name} stammt von der Aufnahme und wird nicht "
                      "ueberschrieben (--out schreibt in einen anderen Ordner)")
                continue
        out_path = os.path.join(out_dir, name)
        if os.path.exists(out_path) and not args.force:
            print(f"{os.path.relpath(out_path, APP_DIR)}: schon vorhanden (--force ueberschreibt)")
            continue
        try:
            times.append(extract(rec_dir, args.model, out_path))
        except RuntimeError as e:
            print(f"{os.path.relpath(rec_dir, APP_DIR)}: uebersprungen, {e}")
    if times:
        print(f"Modell {args.model}: im Mittel {sum(times) / len(times):.1f} ms/Frame")


if __name__ == "__main__":
    main()
