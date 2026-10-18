"""
******************************************************************************
 file:      butterfly.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Butterfly
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


class Butterfly(Uebung):
    title = "Butterfly"
    name = "Butterfly"
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
    # Regler: wie weit die Arme aufgehen (Ziel). Zusammen (min_distance) bleibt fest: dahin
    # kommen die Arme bei jeder Wiederholung ohnehin zurueck
    schwellen = {"max_distance": "Arme offen über"}
    bereich = {"max_distance": (1.2, 3.0)}     # ueber min_distance, sonst zaehlt jedes Bild
    # Einfach: rund 20 % weniger weit oeffnen (mit der medizinischen Beratung abstimmen)
    einfach = {"max_distance": 1.5}

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
                                  self.shoulders_distance * self.max_distance, UEBER_ZIEL)

    def zurueck(self, side):
        return self.stage["both"] == "MAX"  # offen gewesen, zaehlt erst wieder vorne
