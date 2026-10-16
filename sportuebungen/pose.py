"""
******************************************************************************
 file:      pose.py
 brief:     Die Pose eines Bildes ohne Bezug zur Erkennung: Landmark-Nummern je Seite, die Klasse
            Pose (Landmarks roh und normiert) und die Normierung. Reines Python, kein MediaPipe:
            die Uebungen, systest.py und die Aufnahme-Tools rechnen nur hiermit. Woher die
            Landmarks kommen (heute mediapipe_modelle.py), ist austauschbar.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 info:      Aus mediapipe_modelle.py hierher verschoben, unveraendert: die Winkelgrenzen der
            Uebungen sind auf genau diese Normierung eingestellt.
******************************************************************************
"""


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
