"""
******************************************************************************
 file:      abduktion.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Abduktion
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs:
 hardware:
 info:      Gemeinsamer Ablauf aller Uebungen: uebung.py
******************************************************************************
"""
from calculate_angle_distance import calculate_angle, calculate_progress
from mediapipe_modelle import ELLBOGEN, HUEFTE, SCHULTER
from uebung import Uebung


class Abduktion(Uebung):
    title = "Abduktion Schulter"
    name = "Abduktion"
    image = "bilder/Abduktion_Schulter.jpg"
    demo = "demos/abduktion.mp4"
    intro = ("Sie fangen jetzt mit der Übung Abduktion an. Bitte legen Sie die Hände unten "
             "neben den Körper, damit die Übung starten kann.")
    min_angle = 40      # Arm unten
    max_angle = 150     # Arm oben

    def measure(self, pose):
        self.angle = {side: calculate_angle(pose.xy(HUEFTE[side]), pose.xy(SCHULTER[side]),
                                            pose.xy(ELLBOGEN[side]))
                      for side in self.sides}

    def count(self, t):
        for side in self.sides:
            if self.angle[side] > self.max_angle:
                self.stage[side] = "up"
            if self.angle[side] < self.min_angle and self.stage[side] == "up":
                self.stage[side] = "down"
                self.counter[side] += 1

    def fill(self, side):
        # Fortschritt dieses Arms: leer bis min_angle, voll ab max_angle
        return calculate_progress(self.angle[side], self.min_angle, self.max_angle)
