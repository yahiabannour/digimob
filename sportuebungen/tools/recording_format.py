"""
******************************************************************************
 file:      tools/recording_format.py
 brief:     Dateiformat der Aufnahmen von tools/record_exercise.py. Jede Aufnahme bekommt einen eigenen
            Ordner recordings/<uebung>/rec<N>/ (rec1, rec2, ...) mit:
              video.mp4 (sonst video.webm)  Video
              summary.json                  Zusammenfassung, darunter expected_reps (Ground Truth): so viele
                                            Wiederholungen soll die App in der Aufnahme zaehlen (bei zwei
                                            Seiten die kleinere), expected_per_side: {"left": n, "right": m}
                                            nur wenn pro Seite eingegeben (Sicht des Patienten), preset:
                                            Schwellen, mit denen gezaehlt werden soll ("voll" oder
                                            "einfach", uebung.PRESETS; ohne Angabe "voll"), und
                                            camera: {"index": --cam, "name": wie bei --list-cams, null
                                            ausserhalb von Linux}
              keypoints_<modell>.jsonl      Pose-Keypoints pro Frame, fuer Systemtests ohne Kamera und Video.
                                            <modell> aus dem Dateinamen des Pose-Modells (z.B. full fuer
                                            pose_landmarker_full.task, das einzige Modell der App).
                                            Die bei der Aufnahme geschriebene Datei steht in summary.json
                                            (keypoints), weitere Modelle erzeugt tools/extract_keypoints.py
                                            nachtraeglich aus dem Video.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 ext. libs:
 info:      Die keypoints_<modell>.jsonl hat eine Zeile pro Frame:
              {"timestamp": 0.033412, "pose_landmarks": [[x, y, z, visibility, presence], ...]}
            timestamp: Sekunden seit dem ersten Frame. Noetig, weil die Uebungen mit time.time() messen
            (10 s Einfuehrung, Haltezeit bei Bizepscurls).
            pose_landmarks: die 33 Landmarks von Pose.landmarks in der App, Index wie dort (z.B.
            13 = linker Ellbogen). null ohne erkannte Pose.
              x, y        relativ zur Bildgroesse, ausserhalb des Bildes < 0 bzw. > 1
              z           Tiefe relativ zur Hueftmitte, kleiner = naeher an der Kamera, etwa im Massstab von x
              visibility  0..1, wie sicher der Punkt sichtbar (nicht verdeckt) ist
              presence    0..1, wie sicher der Punkt im Bild liegt
            Zum Zaehlen liest die App nur x, y und z (MediapipeModelle.normalize_pose_landmarks()).
            visibility und presence zeigen, wie verlaesslich ein Punkt ist, z.B. fuer spaetere Pruefungen.
******************************************************************************
"""
import json
import os
from collections import namedtuple

from pose import Pose
from uebung import PRESETS
from uebungen import UEBUNGEN

# Ordner einer Aufnahme: recordings/<uebung>/rec1, rec2, ...
REC_DIR_PREFIX = "rec"

# Dateinamen im Ordner einer Aufnahme
VIDEO_NAME = "video"   # plus Endung aus VIDEO_FORMATS
SUMMARY_FILE = "summary.json"

# Videoformate in Reihenfolge der Bevorzugung: (Dateiendung, FourCC)
VIDEO_FORMATS = [(".mp4", "mp4v"), (".webm", "VP80")]

# Ein Punkt der keypoints_<modell>.jsonl: [x, y, z, visibility, presence]
Landmark = namedtuple("Landmark", "x y z visibility presence")

# Nachkommastellen der Koordinaten: bei 640 px Bildbreite genauer als 0.001 px
DECIMALS = 6


def landmarks_to_list(landmarks):
    """Pose.landmarks eines Frames als [[x, y, z, visibility, presence], ...], None ohne Pose."""
    if not landmarks:
        return None
    return [[round(lm.x, DECIMALS), round(lm.y, DECIMALS), round(lm.z, DECIMALS),
             round(lm.visibility, DECIMALS), round(lm.presence, DECIMALS)] for lm in landmarks]


def model_name(model_path):
    """Kurzname des Pose-Modells, z.B. "full" fuer .../pose_landmarker_full.task."""
    return os.path.basename(model_path).removeprefix("pose_landmarker_").removesuffix(".task")


def keypoints_file(pose_model):
    """Name der Keypoints-Datei, z.B. keypoints_full.jsonl fuer pose_landmarker_full.task."""
    return f"keypoints_{pose_model}.jsonl"


def write_keypoints(path, timestamps, poses):
    """Schreibt die keypoints_<modell>.jsonl, eine Zeile pro Frame.
    poses: pro Frame die Liste aus landmarks_to_list(), None ohne Pose"""
    with open(path, "w", encoding="utf-8") as f:
        for timestamp, pose_landmarks in zip(timestamps, poses):
            f.write(json.dumps({"timestamp": timestamp, "pose_landmarks": pose_landmarks}) + "\n")


def read_poses(keypoints_path, resolution):
    """Liest eine keypoints_<modell>.jsonl und liefert pro Frame (timestamp, Pose), Pose None ohne
    erkannte Pose. resolution: (Breite, Hoehe) aus summary.json. Die Pose ist dieselbe wie in der
    App (Normierung aus pose.py); systest.py nutzt diese Funktion."""
    with open(keypoints_path, encoding="utf-8") as f:
        for line in f:
            frame = json.loads(line)
            points = frame["pose_landmarks"]    # null ohne erkannte Pose
            pose = Pose([Landmark(*p) for p in points], tuple(resolution)) if points else None
            yield frame["timestamp"], pose


def uebung_fuer(summary):
    """Die Uebung einer Aufnahme zum Nachzaehlen (systest.py, Aufnahmen testen, Tools): nur zaehlen
    (target=None), mit den Schwellen ihres Presets (summary["preset"], ohne Angabe "voll")."""
    cls = UEBUNGEN[summary["exercise"]]
    return cls(target=None, schwellen=cls.werte(summary.get("preset", PRESETS[0])))


def erwartet_je_seite(uebung, expected_reps, expected_per_side=None):
    """Erwartete Zaehlung je Seite (systest.py und Aufnahmen testen): expected_per_side, wenn in der
    summary.json angegeben, sonst expected_reps fuer jede Seite ("5" im Recorder heisst beide Seiten
    5). So faellt auch eine Seite auf, die zu viel zaehlt (total ist nur die kleinere)."""
    return expected_per_side or {side: expected_reps for side in uebung.sides}


def write_recording(rec_dir, timestamps, poses, meta):
    """Schreibt summary.json und keypoints_<modell>.jsonl in rec_dir (neben das Video) und gibt
    (Zusammenfassung, Pfad der Keypoints-Datei) zurueck.
    poses: pro Frame die Liste aus landmarks_to_list(), None ohne Pose
    meta: exercise, expected_reps, expected_per_side (None ohne Angabe pro Seite), preset, started_at,
    video, camera, width, height, fps_target, pose_model"""
    keypoints_path = os.path.join(rec_dir, keypoints_file(meta["pose_model"]))
    write_keypoints(keypoints_path, timestamps, poses)

    n = len(timestamps)
    measured_fps = (n - 1) / timestamps[-1] if n > 1 and timestamps[-1] > 0 else None
    summary = {
        "exercise": meta["exercise"],
        "expected_reps": meta["expected_reps"],
        **({"expected_per_side": meta["expected_per_side"]} if meta.get("expected_per_side") else {}),
        "preset": meta["preset"],
        "started_at": meta["started_at"],
        "pose_model": meta["pose_model"],
        "video": meta["video"],
        "keypoints": os.path.basename(keypoints_path),
        "camera": meta["camera"],
        "resolution": [meta["width"], meta["height"]],
        "fps_target": meta["fps_target"],
        "measured_fps": None if measured_fps is None else round(measured_fps, 2),
        "frames_recorded": n,
        "frames_with_pose": sum(1 for pose_landmarks in poses if pose_landmarks is not None),
    }
    with open(os.path.join(rec_dir, SUMMARY_FILE), "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    return summary, keypoints_path
