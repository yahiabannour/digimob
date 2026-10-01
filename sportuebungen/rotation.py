"""
******************************************************************************
 file:      rotation.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Rotation
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs:
 info:      Gemeinsamer Ablauf aller Uebungen: uebung.py
******************************************************************************
"""
from calculate_angle_distance import calculate_angle, calculate_distance, calculate_progress
from mediapipe_modelle import ELLBOGEN, HANDGELENK, HUEFTE, SCHULTER
from uebung import Uebung


class Rotation(Uebung):
    title = "Rotation im Sitzen"
    name = "Rotation im Sitzen"
    image = "bilder/Rotation_im_Stand.jpg"
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
                                  self.elbows_distance * self.max_distance)
