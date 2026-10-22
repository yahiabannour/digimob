"""
******************************************************************************
 file:      bizepscurls.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Bizepscurls
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
from calculate_angle_distance import calculate_angle, calculate_progress
from pose import ARME, ELLBOGEN, HANDGELENK, SCHULTER
from uebung import UEBER_ZIEL, Haltezeit, Uebung


class Bizepscurls(Uebung):
    title = "Bizepscurls"
    landmarks = ARME
    demo = "demos/bizepscurls.mp4"
    intro = ("Sie fangen jetzt mit der Übung Bizepscurls an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    min_angle = 110             # Ziel: Ellbogen gebeugt unter -> "up" (Schieberegler)
    max_shoulder = 50           # Oberarme am Koerper, sonst wird nicht gezaehlt (Schieberegler)
    # Gestreckt ("down") = strecken Grad ueber dem Ziel, kein eigener Regler: der Unterarm faellt
    # von selbst zurueck, aber nicht immer bis 150 Grad (Pflege-Aufnahme rec10). Mit Median und
    # Haltezeit zaehlen 15 Grad alle Aufnahmen richtig, live und aus dem Video.
    strecken = 15
    bar_start = 160             # Balken leer bei gestrecktem Arm, voll ab dem Ziel

    # Eine Phase gilt erst, wenn sie confirm_time Sekunden ununterbrochen anliegt, und gemessen
    # wird der Median der letzten median_frames Ellbogenwinkel: der 2D-Ellbogenwinkel flackert,
    # wenn der Unterarm zur Kamera zeigt oder MediaPipe ein Handgelenk ausserhalb des Bildes raet
    # (Aufnahmen rec1-rec3). Auf allen 13 Aufnahmen (live + aus dem Video neu erkannt, beide Arme
    # einzeln geprueft) zaehlt nur 0.15 s alles richtig: 0.10 s zaehlt in rec5 rechts ein
    # Flackern mit, 0.20 s verpasst in rec6 (Video) eine Wiederholung. Knapp, siehe Changelog.
    confirm_time = 0.15
    median_frames = 5

    schwellen = {"min_angle": "Arm gebeugt unter", "max_shoulder": "Oberarm am Körper bis"}
    # Ziel bis 155: unter dem Balkenanfang, und gestreckt (Ziel + strecken) bleibt unter 180, erreichbar
    bereich = {"min_angle": (30, 155), "max_shoulder": (20, 90)}
    # Einfach: weniger weit beugen, und der Oberarm darf weiter vom Koerper weg (Pflege-Aufnahmen
    # rec8-rec13: beim Beugen 50-72 Grad). Mit der medizinischen Beratung abstimmen.
    einfach = {"min_angle": 120, "max_shoulder": 60}

    def __init__(self, target=5, schwellen=None):
        super().__init__(target, schwellen)
        self.hold = {side: Haltezeit(self.confirm_time) for side in self.sides}
        self.recent = {side: [] for side in self.sides}

    @property
    def max_angle(self):
        return self.min_angle + self.strecken

    def measure(self, pose):
        self.shoulder, self.elbow = {}, {}
        for side in self.sides:
            schulter, ellbogen = pose.xy(SCHULTER[side]), pose.xy(ELLBOGEN[side])
            self.shoulder[side] = calculate_angle(pose.huefte(side), schulter, ellbogen)
            angle = calculate_angle(pose.xy(HANDGELENK[side]), ellbogen, schulter)
            self.recent[side] = (self.recent[side] + [angle])[-self.median_frames:]
            self.elbow[side] = sorted(self.recent[side])[len(self.recent[side]) // 2]

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
        # Fortschritt dieses Arms: leer bei gestrecktem Arm (bar_start), 1 am Ziel, darueber hinaus
        return calculate_progress(self.elbow[side], self.bar_start, self.min_angle, UEBER_ZIEL)

    def zurueck(self, side):
        return self.stage[side] == "up"     # gebeugt gezaehlt, erst wieder strecken
