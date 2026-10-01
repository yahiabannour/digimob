"""
******************************************************************************
 file:      bizepscurls.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Bizepscurls
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
from calculate_angle_distance import calculate_angle, calculate_progress
from mediapipe_modelle import ELLBOGEN, HANDGELENK, HUEFTE, SCHULTER
from uebung import Haltezeit, Uebung


class Bizepscurls(Uebung):
    title = "Bizepscurls"
    name = "Bizepscurls"
    image = "bilder/Bizepscurls.jpg"
    demo = "demos/bizepscurls.mp4"
    intro = ("Sie fangen jetzt mit der Übung Bizepscurls an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    min_angle = 110             # Ellbogen gebeugt -> "up"
    max_angle = 150             # Ellbogen gestreckt -> "down"
    max_shoulder = 50           # Oberarme am Koerper, sonst wird nicht gezaehlt

    # Eine Phase gilt erst, wenn sie confirm_time Sekunden ununterbrochen anliegt. Der
    # Ellbogenwinkel wird nur in 2D gemessen und flackert, wenn der Unterarm zur Kamera zeigt
    # oder MediaPipe ein Handgelenk ausserhalb des Bildes raet (Aufnahmen rec1-rec3).
    # Auf rec1-rec5 zaehlen 0.20-0.25 s richtig, kuerzer zaehlt Flackern, laenger verpasst
    # schnelle Wiederholungen.
    confirm_time = 0.25

    def __init__(self, target=5):
        super().__init__(target)
        self.hold = {side: Haltezeit(self.confirm_time) for side in self.sides}

    def measure(self, pose):
        self.shoulder, self.elbow = {}, {}
        for side in self.sides:
            schulter, ellbogen = pose.xy(SCHULTER[side]), pose.xy(ELLBOGEN[side])
            self.shoulder[side] = calculate_angle(pose.xy(HUEFTE[side]), schulter, ellbogen)
            self.elbow[side] = calculate_angle(pose.xy(HANDGELENK[side]), ellbogen, schulter)

    def ready(self):
        return all(angle <= self.max_shoulder for angle in self.shoulder.values())

    def count(self, t):
        # Zwei Schwellen (Hysterese): gestreckt ueber max_angle, gebeugt unter min_angle,
        # dazwischen aendert sich nichts. Gezaehlt wird "up" nach "down".
        for side in self.sides:
            angle = self.elbow[side]
            candidate = "down" if angle > self.max_angle else "up" if angle < self.min_angle else None
            stage = self.hold[side].confirm(candidate, t)
            if stage == "down":
                self.stage[side] = "down"
            elif stage == "up" and self.stage[side] == "down":
                self.stage[side] = "up"
                self.counter[side] += 1

    def lost(self):
        # Ohne gueltige Messung (keine Pose, Fehler, Schultern ausserhalb der Startposition)
        # darf eine angefangene Phase nicht weiterlaufen
        for hold in self.hold.values():
            hold.reset()

    def fill(self, side):
        # Fortschritt dieses Arms: leer bei gestrecktem Arm (max_angle), voll ab min_angle
        return calculate_progress(self.elbow[side], self.max_angle, self.min_angle)
