"""
******************************************************************************
 file:      halswirbelsaeule.py
 brief:     Software zur Bestimmung und Zählung von Kopfbewegungen für die Sportübung Halswirbelsäule
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: Numpy (Version: 2.5.2)
 info:      Kopfdrehung aus den Pose-Landmarks 0-6 (Nase, Augen) als 3D-Winkel, siehe
            head_rotation(). Gemeinsamer Ablauf aller Uebungen: uebung.py
******************************************************************************
"""
import numpy as np
from calculate_angle_distance import calculate_progress
from uebung import UEBER_ZIEL, Uebung


class Halswirbelsaeule(Uebung):
    title = "Halswirbelsäule"
    name = "Halswirbelsaeule"
    demo = "demos/halswirbelsaeule.mp4"
    intro = ("Sie fangen jetzt mit der Übung Halswirbelsäule an. Bitte halten Sie den Kopf ruhig "
             "in der Mitte, damit die Übung starten kann.")

    bar_style = "center"    # ein Balken unter dem Video, waechst aus der Mitte zur Drehrichtung

    # Schwellwerte der Kopfdrehung in Grad, gelten für beide Seiten
    min_angle = 15      # bis hier gilt der Kopf als mittig (Balken leer)
    max_angle = 35      # ab hier wird die Drehung gezählt (Balken voll)
    # Regler: ab welcher Drehung gezaehlt wird (Ziel). min_angle ist nur der Balkenanfang
    schwellen = {"max_angle": "Drehung zählt ab"}
    bereich = {"max_angle": (20, 80)}          # ueber min_angle (Balkenanfang)
    # Einfach: rund 20 % weniger weit drehen (mit der medizinischen Beratung abstimmen)
    einfach = {"max_angle": 28}

    def __init__(self, target=5, schwellen=None):
        super().__init__(target, schwellen)
        self.direction = None             # aktuelle Richtung ("links"/"rechts"), bleibt bis zur Gegenseite
        self.previous_direction = None    # zuletzt gezählte Richtung

    def measure(self, pose):
        # Kopfdrehung in Grad: 0 = Blick in die Kamera, > 0 nach links, < 0 nach rechts (aus Sicht des Patienten)
        width, height = pose.size
        self.rotation = self.head_rotation(pose.landmarks, width, height)

    def count(self, t):
        if self.rotation > self.max_angle:
            self.direction = "links"
        elif self.rotation < -self.max_angle:
            self.direction = "rechts"

        # Gezählt wird jeder Wechsel zur Gegenseite
        if self.direction and self.direction != self.previous_direction:
            side = "left" if self.direction == "links" else "right"
            self.counter[side] += 1
            self.previous_direction = self.direction
        self.stage["left"] = self.stage["right"] = self.direction

    def fill(self, side):
        # Fortschritt der Kopfdrehung zu dieser Seite: leer in der Mitte (bis min_angle) und bei
        # Drehung zur Gegenseite, voll ab der Zählschwelle (max_angle)
        turn = self.rotation if side == "left" else -self.rotation
        return calculate_progress(turn, self.min_angle, self.max_angle, UEBER_ZIEL)

    @staticmethod
    def head_rotation(landmarks, image_width, image_height):
        """Berechnet die Kopfdrehung (links/rechts) in Grad aus den Pose-Landmarks 0-6."""
        # Pixelkoordinaten; z hat laut MediaPipe etwa den Maßstab von x, daher ebenfalls * Bildbreite
        points = np.array([[lm.x * image_width, lm.y * image_height, lm.z * image_width] for lm in landmarks[:7]])
        nose = points[0]
        left_eye = points[1:4].mean(axis=0)     # 1-3: linkes Auge innen, Mitte, außen
        right_eye = points[4:7].mean(axis=0)    # 4-6: rechtes Auge innen, Mitte, außen

        # Vektor 1: zwischen den Augen (rechtes -> linkes Auge)
        vector_1 = left_eye - right_eye
        if not np.any(vector_1):
            raise ValueError("Kopfdrehung nicht bestimmbar: beide Augen auf einem Punkt")
        # Vektor 2: senkrecht zu Vektor 1 durch die Nase (vom Lotfußpunkt auf der Augenlinie zur Nase)
        foot = right_eye + np.dot(nose - right_eye, vector_1) / np.dot(vector_1, vector_1) * vector_1
        vector_2 = nose - foot
        # Vektor 3: Normale auf Vektor 1 und 2 = Blickrichtung des Gesichts (Blick in die Kamera: -z)
        vector_3 = np.cross(vector_2, vector_1)

        # Der Winkel zwischen Vektor 2 und 3 ist als Normale immer 90 Grad. Gemessen wird deshalb der
        # Winkel zwischen Vektor 3 und der Kamerablickrichtung (-z) in der Horizontalebene (x-z).
        # Das Kamerabild ist ungespiegelt, x wächst zur linken Seite des Patienten -> > 0 = nach links.
        return float(np.degrees(np.arctan2(vector_3[0], -vector_3[2])))
