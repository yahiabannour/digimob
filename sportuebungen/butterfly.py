"""
******************************************************************************
 file:      butterfly.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Butterfly
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


class Butterfly(Uebung):
    title = "Butterfly"
    name = "Butterfly"
    image = "bilder/Butterfly.jpg"
    demo = "demos/butterfly.mp4"
    # "Batterflei": so spricht die deutsche Stimme das Wort richtig aus
    intro = ("Sie fangen jetzt mit der Übung Batterflei an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    sides = ("both",)           # beide Arme zusammen, ein Zähler
    bar_style = "single"        # und ein Balken
    target_message = "Versuchen Sie {} mal zu schaffen!"
    min_shoulder = 70           # Oberarm so weit gehoben zählt als Butterfly-Haltung
    # Handgelenkabstand als Vielfaches der Schulterbreite
    min_distance = 1            # Arme vorne zusammen
    max_distance = 1.8          # Arme geöffnet

    def measure(self, pose):
        shoulder = {side: calculate_angle(pose.xy(HUEFTE[side]), pose.xy(SCHULTER[side]),
                                          pose.xy(ELLBOGEN[side]))
                    for side in ("left", "right")}
        # Butterfly-Haltung je Arm: Hand über dem Ellbogen (y wächst nach unten) oder Oberarm gehoben
        self.arm_up = {side: pose.xy(ELLBOGEN[side])[1] > pose.xy(HANDGELENK[side])[1]
                             or shoulder[side] > self.min_shoulder
                       for side in ("left", "right")}
        self.shoulders_distance = calculate_distance(pose.xy(SCHULTER["right"]), pose.xy(SCHULTER["left"]))
        self.wrists_distance = calculate_distance(pose.xy(HANDGELENK["right"]), pose.xy(HANDGELENK["left"]))

    # Die Haltung ist bewusst kein ready()-Gate: Meldung und Ansagen (z.B. "Super, versuchen Sie ...")
    # laufen ab der ersten erkannten Pose, gezählt und im Balken angezeigt wird nur in Haltung.
    def in_position(self):
        return all(self.arm_up.values())

    def count(self, t):
        if not self.in_position():
            return
        if self.wrists_distance > self.shoulders_distance * self.max_distance and self.stage["both"] != "MAX":
            self.stage["both"] = "MAX"
        if self.wrists_distance < self.shoulders_distance * self.min_distance and self.stage["both"] == "MAX":
            self.stage["both"] = "MIN"
            self.counter["both"] += 1

    def fill(self, side):       # side: immer "both"
        if not self.in_position():
            return None
        # Fortschritt der Öffnung: Handgelenkabstand von min_distance (leer) bis max_distance (voll) x Schulterbreite
        return calculate_progress(self.wrists_distance, self.shoulders_distance * self.min_distance,
                                  self.shoulders_distance * self.max_distance)
