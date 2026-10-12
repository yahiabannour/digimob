"""
******************************************************************************
 file:      mediapipe_modelle.py
 brief:     Pose-Erkennung: aus einem Bild die Pose (Landmarks, roh und normiert, Klasse in pose.py).
            Das einzige Modul, das MediaPipe importiert: Modell, Tasks-API im VIDEO-Modus, Zeichnen
            der Keypoints. App, Zoom (bildoperation_opencv.py) und tools/record_exercise.py nutzen
            je eine eigene MediapipeModelle-Instanz.
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: Mediapipe (Version: 1.0.1, Tasks-API)
 info:      Liest selbst kein Bild: detect() bekommt das Bild vom Uebungslauf.
            Braucht models/pose_landmarker_full.task (liegt im Repo), das einzige Modell: lite und heavy
            wurden an allen Aufnahmen verglichen und entfernt (changelog 1.1.9).
******************************************************************************
"""
import os
import time

import numpy as np
import mediapipe as mp

# Pose, Normierung und Landmark-Nummern liegen in pose.py (ohne MediaPipe); hier weiterhin
# importierbar, damit bestehende Aufrufer (Tools) unveraendert funktionieren
from pose import ELLBOGEN, HANDGELENK, HUEFTE, SCHULTER, Pose, normalize_pose_landmarks  # noqa: F401

vision = mp.tasks.vision

# Bewusst das full-Modell, nicht lite: entspricht dem frueheren Solutions-Standard
# (model_complexity=1), auf den die Winkelgrenzen der Uebungen eingestellt sind.
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
POSE_MODELS = {"full": os.path.join(MODEL_DIR, "pose_landmarker_full.task")}
POSE_MODEL = POSE_MODELS["full"]

# Keypoints im Videobild: Punkte rot, Verbindungen weiss (BGR)
LANDMARK_STYLE = vision.drawing_utils.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2)
CONNECTION_STYLE = vision.drawing_utils.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)

class MediapipeModelle:
    def __init__(self, model_path=POSE_MODEL):
        # Pose-Erkennung im VIDEO-Modus (nutzt das vorige Bild) mit den Konfidenzwerten
        # für Erkennung und Tracking
        self.options = vision.PoseLandmarkerOptions(
            base_options=mp.tasks.BaseOptions(model_asset_path=_check_model(model_path)),
            running_mode=vision.RunningMode.VIDEO,
            num_poses=1,
            min_pose_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        self.reset()

    def reset(self):
        """Frische Erkennung ohne Gedaechtnis: der VIDEO-Modus verfolgt die Person von Bild zu Bild,
        ohne reset() haengt das Ergebnis vom vorigen Video ab (Aufnahmen testen)."""
        if getattr(self, "landmarker", None) is not None:
            self.landmarker.close()
        self.landmarker = vision.PoseLandmarker.create_from_options(self.options)
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

