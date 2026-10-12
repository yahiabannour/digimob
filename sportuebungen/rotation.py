"""
******************************************************************************
 file:      rotation.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Rotation
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs:
 info:      Gemeinsamer Ablauf aller Uebungen: uebung.py
******************************************************************************
"""
from calculate_angle_distance import calculate_angle, calculate_distance, calculate_progress
from pose import ELLBOGEN, HANDGELENK, HUEFTE, SCHULTER
from uebung import UEBER_ZIEL, Uebung


class Rotation(Uebung):
    title = "Rotation im Sitzen"
    name = "Rotation im Sitzen"
    demo = "demos/rotation.mp4"
    intro = ("Sie fangen jetzt mit der Übung Rotation an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    sides = ("both",)           # beide Arme zusammen, ein Zähler
    bar_style = "single"        # und ein Balken
    target_message = "Versuchen Sie {} mal zu schaffen!"
    max_shoulder = 75           # Oberarme am Körper, sonst wird nicht gezählt
    # Handgelenkabstand als Vielfaches des Ellenbogenabstands
    min_distance = 1.1          # Unterarme vorne (Startposition)
    max_distance = 1.4          # Unterarme nach außen gedreht
    # Regler: wie weit die Unterarme nach aussen drehen (Ziel). Vorne (min_distance) bleibt fest
    schwellen = {"max_distance": "Unterarme außen über"}
    bereich = {"max_distance": (1.2, 3.0)}     # ueber min_distance, sonst zaehlt jedes Bild
    # Einfach: weniger weit nach aussen (mit der medizinischen Beratung abstimmen)
    einfach = {"max_distance": 1.3}

    def measure(self, pose):
        self.shoulder = {side: calculate_angle(pose.xy(HUEFTE[side]), pose.xy(SCHULTER[side]),
                                               pose.xy(ELLBOGEN[side]))
                         for side in ("left", "right")}
        self.elbows_distance = calculate_distance(pose.xy(ELLBOGEN["right"]), pose.xy(ELLBOGEN["left"]))
        self.wrists_distance = calculate_distance(pose.xy(HANDGELENK["right"]), pose.xy(HANDGELENK["left"]))

    def ready(self):
        return all(angle < self.max_shoulder for angle in self.shoulder.values())

    def count(self, t):
        if self.wrists_distance > self.elbows_distance * self.max_distance and self.stage["both"] != "MAX":
            self.stage["both"] = "MAX"
        if self.wrists_distance < self.elbows_distance * self.min_distance and self.stage["both"] == "MAX":
            self.stage["both"] = "MIN"
            self.counter["both"] += 1

    def fill(self, side):       # side: immer "both"
        # Fortschritt der Rotation: Handgelenkabstand von min_distance (leer) bis max_distance (voll) x Ellenbogenabstand
        return calculate_progress(self.wrists_distance, self.elbows_distance * self.min_distance,
                                  self.elbows_distance * self.max_distance, UEBER_ZIEL)

    def zurueck(self, side):
        return self.stage["both"] == "MAX"  # offen gewesen, zaehlt erst wieder vorne
