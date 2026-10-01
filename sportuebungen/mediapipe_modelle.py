"""
******************************************************************************
 file:      mediapipe_modelle.py
 brief:     Pose-Erkennung: aus einem Bild die Pose (Landmarks, roh und normiert). Das einzige
            Modul, das MediaPipe importiert: Modell, Tasks-API im VIDEO-Modus, Zeichnen der
            Keypoints. App, Zoom (bildoperation_opencv.py) und tools/record_exercise.py nutzen
            je eine eigene MediapipeModelle-Instanz.
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: Mediapipe (Version: 1.0.1, Tasks-API)
 info:      Liest selbst kein Bild: detect() bekommt das Bild vom Uebungslauf.
            Braucht models/pose_landmarker_full.task (liegt im Repo). lite und heavy (POSE_MODELS)
            nutzt nur tools/extract_keypoints.py, sie werden bei Bedarf heruntergeladen.
******************************************************************************
"""
import os
import time

import numpy as np
import mediapipe as mp

vision = mp.tasks.vision

# Bewusst das full-Modell, nicht lite: entspricht dem frueheren Solutions-Standard
# (model_complexity=1), auf den die Winkelgrenzen der Uebungen eingestellt sind.
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
POSE_MODELS = {name: os.path.join(MODEL_DIR, f"pose_landmarker_{name}.task") for name in ("lite", "full", "heavy")}
POSE_MODEL = POSE_MODELS["full"]

# Keypoints im Videobild: Punkte rot, Verbindungen weiss (BGR)
LANDMARK_STYLE = vision.drawing_utils.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2)
CONNECTION_STYLE = vision.drawing_utils.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)

# Verbindungen des Skeletts als (Landmark, Landmark), z.B. fuer tools/view_models.py
POSE_CONNECTIONS = [(c.start, c.end) for c in vision.PoseLandmarksConnections.POSE_LANDMARKS]

# Landmark-Nummern je Seite, aus Sicht des Patienten (das Kamerabild ist ungespiegelt)
SCHULTER   = {"left": 11, "right": 12}
ELLBOGEN   = {"left": 13, "right": 14}
HANDGELENK = {"left": 15, "right": 16}
HUEFTE     = {"left": 23, "right": 24}


class Pose:
    """Die Landmarks eines Bildes: roh (Pixelrechnung, z.B. Halswirbelsaeule) und normiert (Winkel)."""

    def __init__(self, landmarks, size):
        self.landmarks = landmarks      # 33 Landmarks mit .x .y .z, relativ zur Bildgroesse
        self.size = size                # (Breite, Hoehe) des Bildes
        self.norm = normalize_pose_landmarks(landmarks)

    def xy(self, index):
        """Normierter 2D-Punkt, wie ihn calculate_angle() erwartet."""
        return self.norm[index][:2]


class MediapipeModelle:
    def __init__(self, model_path=POSE_MODEL):
        # Pose-Erkennung im VIDEO-Modus (nutzt das vorige Bild) mit den Konfidenzwerten
        # für Erkennung und Tracking
        options = vision.PoseLandmarkerOptions(
            base_options=mp.tasks.BaseOptions(model_asset_path=_check_model(model_path)),
            running_mode=vision.RunningMode.VIDEO,
            num_poses=1,
            min_pose_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        self.landmarker = vision.PoseLandmarker.create_from_options(options)
        self._t0 = time.monotonic()
        self._last_ts = -1

    def _timestamp_ms(self, ts=None):
        # detect_for_video() verlangt streng steigende Zeitstempel
        if ts is None:
            ts = int((time.monotonic() - self._t0) * 1000)
        if ts <= self._last_ts:
            ts = self._last_ts + 1
        self._last_ts = ts
        return ts

    def detect(self, image_rgb, timestamp_ms=None):
        """Pose des Bildes (RGB), None ohne erkannte Person. timestamp_ms: Zeitpunkt des Bildes, z.B.
        aus einer Aufnahme (gleiche Zeitstempel -> gleiches Ergebnis), sonst die aktuelle Zeit."""
        image = mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(image_rgb))
        result = self.landmarker.detect_for_video(image, self._timestamp_ms(timestamp_ms))
        if not result.pose_landmarks:
            return None
        height, width = image_rgb.shape[:2]
        return Pose(result.pose_landmarks[0], (width, height))


def _check_model(path):
    if not os.path.exists(path):
        name = os.path.basename(path).removesuffix(".task")
        raise FileNotFoundError(
            f"MediaPipe-Modell nicht gefunden: {path}\n"
            "Bitte die .task-Datei nach 'models/' herunterladen:\n"
            f"  https://storage.googleapis.com/mediapipe-models/pose_landmarker/{name}/float16/latest/{name}.task"
        )
    return path


def draw_pose(image, landmarks):
    """Zeichnet die Keypoints (Pose.landmarks) mit ihren Verbindungen in das Bild (BGR)."""
    vision.drawing_utils.draw_landmarks(
        image=image,
        landmark_list=landmarks,
        connections=vision.PoseLandmarksConnections.POSE_LANDMARKS,
        landmark_drawing_spec=LANDMARK_STYLE,
        connection_drawing_spec=CONNECTION_STYLE,
    )


def normalize_pose_landmarks(landmarks):
    """Mittelpunkt aller Landmarks abziehen und durch die groesste Ausdehnung entlang einer Achse
    teilen. Die Winkelgrenzen aller Uebungen sind auf genau diese Normierung eingestellt."""
    # Liste (x,y,z) aller 33 Pose-Landmarks
    keypoints = [(lm.x, lm.y, lm.z) for lm in landmarks]

    # Normierung mit Mittelpunkt & globaler Skala
    x_coords = [x for x, y, z in keypoints]
    y_coords = [y for x, y, z in keypoints]
    z_coords = [z for x, y, z in keypoints]

    center_x = sum(x_coords) / len(x_coords)
    center_y = sum(y_coords) / len(y_coords)
    center_z = sum(z_coords) / len(z_coords)

    # Skala = größte Ausdehnung entlang einer Achse
    scale = max(
        max(x_coords) - min(x_coords),
        max(y_coords) - min(y_coords),
        max(z_coords) - min(z_coords)
    )
    scale = scale if scale > 0 else 1.0

    return [
        ((x - center_x) / scale,
         (y - center_y) / scale,
         (z - center_z) / scale)
        for (x, y, z) in keypoints
    ]
