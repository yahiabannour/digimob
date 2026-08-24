"""
******************************************************************************
 file:      mediapipe_tasks_compat.py
 brief:     Duenne Kompatibilitaetsschicht auf die neue MediaPipe Tasks API
            (mp.tasks.vision). Ersetzt das entfallene mp.solutions-Modul und
            liefert Ergebnisobjekte im gewohnten Format
            (results.pose_landmarks.landmark bzw. results.multi_face_landmarks).
 author(s): Ezaldeen Al-Shahethi, Joerg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 version:   1.1.8
 software:  Python (Version: 3.12.3)
 ext. libs: Mediapipe (Version: 1.0.1)
******************************************************************************
"""
import os
import time

import numpy as np
import mediapipe as mp

vision = mp.tasks.vision
BaseOptions = mp.tasks.BaseOptions

drawing_utils = vision.drawing_utils
drawing_styles = vision.drawing_styles
POSE_CONNECTIONS = vision.PoseLandmarksConnections.POSE_LANDMARKS
FACEMESH_CONTOURS = vision.FaceLandmarksConnections.FACE_LANDMARKS_CONTOURS

# Modelldateien (.task) liegen neben den Quelldateien im Ordner "models".
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
POSE_MODEL = os.path.join(MODEL_DIR, "pose_landmarker_lite.task")
FACE_MODEL = os.path.join(MODEL_DIR, "face_landmarker.task")


def _check_model(path):
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"MediaPipe-Modell nicht gefunden: {path}\n"
            "Bitte die .task-Dateien nach 'models/' herunterladen, z.B.:\n"
            "  https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
            "pose_landmarker_lite/float16/1/pose_landmarker_lite.task\n"
            "  https://storage.googleapis.com/mediapipe-models/face_landmarker/"
            "face_landmarker/float16/1/face_landmarker.task"
        )
    return path


class _LandmarkList:
    """Ersatz fuer NormalizedLandmarkList aus der alten Solutions-API."""

    def __init__(self, landmarks):
        self.landmark = landmarks

    def __iter__(self):
        return iter(self.landmark)

    def __len__(self):
        return len(self.landmark)


class _PoseResult:
    def __init__(self, landmarks=None, world_landmarks=None):
        self.pose_landmarks = _LandmarkList(landmarks) if landmarks else None
        self.pose_world_landmarks = _LandmarkList(world_landmarks) if world_landmarks else None


class _FaceResult:
    def __init__(self, faces=None):
        self.multi_face_landmarks = [_LandmarkList(f) for f in faces] if faces else None


class _VideoTask:
    """Gemeinsame Basis: haelt einen Landmarker im VIDEO-Modus und erzeugt
    monoton steigende Zeitstempel fuer detect_for_video()."""

    def __init__(self, landmarker):
        self._landmarker = landmarker
        self._t0 = time.monotonic()
        self._last_ts = -1

    def _timestamp_ms(self):
        ts = int((time.monotonic() - self._t0) * 1000)
        if ts <= self._last_ts:
            ts = self._last_ts + 1
        self._last_ts = ts
        return ts

    def _detect(self, image_rgb):
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB,
                            data=np.ascontiguousarray(image_rgb))
        return self._landmarker.detect_for_video(mp_image, self._timestamp_ms())

    def close(self):
        try:
            self._landmarker.close()
        except Exception:
            pass


class PoseTask(_VideoTask):
    def __init__(self, min_detection_confidence=0.5, min_tracking_confidence=0.5,
                 num_poses=1):
        options = vision.PoseLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=_check_model(POSE_MODEL)),
            running_mode=vision.RunningMode.VIDEO,
            num_poses=num_poses,
            min_pose_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        )
        super().__init__(vision.PoseLandmarker.create_from_options(options))

    def process(self, image_rgb):
        """Wie mp.solutions.pose.Pose.process(): erwartet ein RGB-Bild."""
        result = self._detect(image_rgb)
        landmarks = result.pose_landmarks[0] if result.pose_landmarks else None
        world = result.pose_world_landmarks[0] if result.pose_world_landmarks else None
        return _PoseResult(landmarks, world)


class FaceTask(_VideoTask):
    def __init__(self, max_num_faces=1, min_detection_confidence=0.5,
                 min_tracking_confidence=0.5):
        options = vision.FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=_check_model(FACE_MODEL)),
            running_mode=vision.RunningMode.VIDEO,
            num_faces=max_num_faces,
            min_face_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        )
        super().__init__(vision.FaceLandmarker.create_from_options(options))

    def process(self, image_rgb):
        """Wie mp.solutions.face_mesh.FaceMesh.process(): erwartet ein RGB-Bild."""
        result = self._detect(image_rgb)
        return _FaceResult(result.face_landmarks)


def draw_landmarks(image, landmark_list, connections=None,
                   landmark_drawing_spec=drawing_utils.DrawingSpec(),
                   connection_drawing_spec=drawing_utils.DrawingSpec()):
    """Zeichnet Landmarks; akzeptiert _LandmarkList oder eine reine Liste."""
    landmarks = getattr(landmark_list, "landmark", landmark_list)
    if not landmarks:
        return
    drawing_utils.draw_landmarks(
        image=image,
        landmark_list=landmarks,
        connections=connections,
        landmark_drawing_spec=landmark_drawing_spec,
        connection_drawing_spec=connection_drawing_spec,
    )


DrawingSpec = drawing_utils.DrawingSpec
