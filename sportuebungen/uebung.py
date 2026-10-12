"""
******************************************************************************
 file:      uebung.py
 brief:     Basisklasse aller Uebungen. Eine Uebung rechnet nur: Pose rein, Zaehlerstand raus.
            Kamera, Bild, Ton, Einfuehrung und Wechsel zur naechsten Uebung macht der
            Uebungslauf (uebungslauf.py).
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 info:      Eine Uebung fuellt measure(), count() und fill() aus, bei Bedarf ready() (Gate,
            z.B. Oberarme am Koerper) und lost() (kein gueltiger Messwert). fill(side) liefert
            den Fuellstand eines Balkens, zurueck(side), ob die Bewegung nach dem Ziel erst zurueck
            muss; progress() fasst beides mit der angezeigten Zahl zu Balken zusammen, bar_style
            sagt der Oberflaeche, wie sie sie zeichnet.
            Schwellen: die Klassenattribute sind das Preset "Voll", einfach die Abweichungen fuer
            "Einfach", schwellen die Werte, die das Auswahlmenue zum Einstellen anbietet.
            Armheben: gemeinsamer Zaehler von Abduktion, Seitheben, Flexion und Mobilisation.
******************************************************************************
"""
import logging
from collections import namedtuple

from calculate_angle_distance import calculate_angle, calculate_progress
from pose import ELLBOGEN, HUEFTE, SCHULTER

# Presets der Schwellen: "voll" = Klassenattribute, "einfach" = gelockert fuer Patienten
PRESETS = ("voll", "einfach")

# Was die Oberflaeche je Balken zeichnet:
#   fill     0 .. UEBER_ZIEL, 1 = Ziel erreicht (die Zielinie), None = kein Messwert (leer)
#   zurueck  Ziel erreicht, die Bewegung muss erst zurueck, bevor sie zaehlt (Pfeil)
#   zahl     die grosse Zahl im Balken, wort steht darueber ("noch", im Aufnahmen-Test "gezählt")
Balken = namedtuple("Balken", "fill zurueck zahl wort")
UEBER_ZIEL = 1.25       # der Balken reicht ein Viertel ueber das Ziel: die Ziellinie liegt bei 80 %


class Uebung:
    title = ""                  # im Menue, z.B. "Abduktion Schulter"
    name = ""                   # im Videobild; ohne Umlaute, cv2.putText() kann keine zeichnen
    demo = ""                   # 3D-Demo (Video), die Einfuehrung zeigt sie statt eines Bildes
    intro = ""                  # Ansage waehrend der Einfuehrung
    sides = ("left", "right")   # Butterfly und Rotation zaehlen nur eine Bewegung: ("both",)
    target_message = "Super, versuchen Sie {} mal zu schaffen!"
    # Balken: "split" = ein senkrechter Balken je Seite, "center" = ein waagerechter Balken, der
    # aus der Mitte nach links/rechts waechst (Kopfdrehung), "single" = ein Balken (sides ("both",))
    bar_style = "split"
    # Einstellbare Schwellen (Attribut -> Beschriftung im Auswahlmenue) und die Abweichungen des
    # Presets "Einfach" von "Voll" (= den Klassenattributen)
    schwellen = {}
    einfach = {}
    # Erlaubter Bereich je einstellbarer Schwelle (Attribut -> (von, bis)), zugleich der Bereich des
    # Schiebereglers. Technische Grenzen, keine medizinischen: ausserhalb passt die Schwelle nicht
    # mehr zu den festen Werten der Uebung (Ruecklinie, Balkenanfang) und es wird nie oder bei jedem
    # Frame gezaehlt (z.B. Rotation unter 1.1: jedes Bild eine Wiederholung)
    bereich = {}

    def __init__(self, target=5, schwellen=None):
        # Schwellen dieses Durchgangs (Preset oder Auswahlmenue) ueberdecken die Klassenattribute
        for key, value in (schwellen or {}).items():
            setattr(self, key, value)
        self.target = target    # None = nur zaehlen (Aufnahmen testen)
        self.counter = {side: 0 for side in self.sides}
        self.stage = {side: None for side in self.sides}
        self.valid = False      # hatte der letzte Frame einen gueltigen Messwert?
        self.message = "Jetzt koennen Sie mit dieser Uebung starten"

    @classmethod
    def werte(cls, preset="voll"):
        """Die einstellbaren Schwellen eines Presets, z.B. {"min_angle": 40, "max_angle": 150}."""
        werte = {key: getattr(cls, key) for key in cls.schwellen}
        if preset == "einfach":
            werte.update(cls.einfach)
        return werte

    @classmethod
    def preset_von(cls, werte):
        """Name des Presets mit genau diesen Werten, None wenn von Hand angepasst."""
        return next((preset for preset in PRESETS if cls.werte(preset) == werte), None)

    @classmethod
    def pruefe(cls, werte):
        """Fehlermeldung (mit den Beschriftungen aus schwellen), wenn die Schwellen nicht
        zusammenpassen, sonst None: alle groesser als 0 und in ihrem bereich, jede untere Schwelle
        min_X unter ihrer oberen max_X (sonst fehlt die Hysterese)."""
        label = lambda key: f'"{cls.schwellen.get(key, key)}"'
        for key, value in werte.items():
            if value <= 0:
                return f"{label(key)} muss größer als 0 sein"
            if key in cls.bereich:
                low, high = cls.bereich[key]
                if not low <= value <= high:    # auch NaN aus einer kaputten schwellen.json
                    return f"{label(key)} muss zwischen {low:g} und {high:g} liegen"
            upper = "max_" + key[len("min_"):]
            if key.startswith("min_") and upper in werte and value >= werte[upper]:
                return f"{label(key)} ({value:g}) muss kleiner sein als {label(upper)} ({werte[upper]:g})"
        return None

    @property
    def total(self):
        return min(self.counter.values())

    @property
    def done(self):
        return self.target is not None and self.total >= self.target

    def update(self, pose, t):
        """Ein Frame. pose: Pose oder None (keine Person erkannt), t: Zeit in Sekunden."""
        self.valid = False
        if pose is not None:
            try:
                self.measure(pose)
                self.valid = self.ready()
            except Exception as e:
                logging.warning(f"{self.title}: {e}")
        if not self.valid:
            self.lost()
            return
        if not self.done:
            self.count(t)

        if self.target is None:
            self.message = "Aufnahmen-Test: nur zaehlen"
        elif self.done:
            self.message = "Prima, gute Leistung!"
        else:
            self.message = self.target_message.format(self.target)

    def progress(self):
        """Ein Balken je Seite, z.B. {"left": Balken(...), "right": Balken(...)}. Gleiches Gate wie
        beim Zaehlen, per Konstruktion: ungueltiger Frame -> fill None (leer), zurueck False; die
        Zahl bleibt stehen."""
        wort = "gezählt" if self.target is None else "noch"
        return {side: Balken(self.fill(side) if self.valid else None,
                             self.valid and self.zurueck(side), self.shown_count(side), wort)
                for side in self.sides}

    def shown_count(self, side):
        """Im Videobild: verbleibende Wiederholungen, im Aufnahmen-Test die gezaehlten. Nie unter 0:
        eine Seite, die vorne liegt, zaehlt weiter, bis die andere das Ziel erreicht."""
        n = self.counter[side]
        return n if self.target is None else max(0, self.target - n)

    # --- Das fuellt jede Uebung aus ------------------------------------------
    def measure(self, pose): raise NotImplementedError   # Winkel/Abstaende berechnen
    def ready(self):         return True                 # Gate, z.B. Oberarme am Koerper
    def count(self, t):      raise NotImplementedError   # Zustandsautomat -> self.counter
    def fill(self, side):    raise NotImplementedError   # Balken 0..UEBER_ZIEL einer Seite aus ihren Messwerten
    def zurueck(self, side): return False                # Ziel erreicht, erst zurueck, dann zaehlt es
    def lost(self):          pass                        # kein Messwert, z.B. Haltezeit verwerfen


class Haltezeit:
    """Bestaetigt eine Phase erst, wenn sie confirm_time Sekunden ununterbrochen anliegt.
    Heute nur in Bizepscurls, laut Changelog-ToDo spaeter fuer alle Uebungen."""

    def __init__(self, confirm_time):
        self.confirm_time = confirm_time
        self.reset()

    def reset(self):
        self.candidate, self.since = None, None

    def confirm(self, candidate, t):
        # Neue Phase erkannt -> Haltezeit neu starten
        if candidate != self.candidate:
            self.candidate, self.since = candidate, t
        if candidate is not None and t - self.since >= self.confirm_time:
            return candidate
        return None


def einheit(key):
    """Einheit einer Schwelle fuer das Auswahlmenue, aus ihrem Namen."""
    if key.endswith("_distance"):
        return "x"
    if key.endswith("_time"):
        return "s"
    return "°"


class Armheben(Uebung):
    """Abduktion, Seitheben, Flexion und Mobilisation Schulter: Winkel Huefte-Schulter-Ellbogen je
    Arm. Eine Wiederholung ist unten -> ueber max_angle (oben) -> wieder unter min_angle (unten).
    Die Uebungen unterscheiden sich nur in Texten und Zielhoehe (max_angle)."""
    max_angle = 150     # Zielhoehe: Arm oben, je Uebung (Schieberegler)
    # Unten = rueckweg Grad unter der Zielhoehe, kein eigener Regler: Schwerkraft und Bett bringen
    # den Arm ohnehin zurueck, wie weit, haengt aber vom Koerper ab (Ruhewinkel 11-21 Grad in den
    # ersten Aufnahmen, 36-50 bei der Pflege). Jede untere Schwelle zwischen Ruhewinkel und
    # Zielhoehe zaehlt richtig; 15 Grad halten Abstand zum Zittern um die Zielhoehe.
    rueckweg = 15
    bar_start = 20      # Balken leer bis hier (Arm haengt), voll ab max_angle
    schwellen = {"max_angle": "Arm oben über"}
    # Ab 40: unten (Zielhoehe - rueckweg) bleibt ueber dem Balkenanfang; bis 175: erreichbar (2D max. 180)
    bereich = {"max_angle": (40, 175)}

    # Eine Seite, deren schwaechster Punkt (Huefte, Schulter, Ellbogen) unsicherer ist, zaehlt in
    # diesem Frame nicht: ein verdeckter Ellbogen (z.B. auf der Bettdecke) springt von Frame zu
    # Frame, und der Winkel mit ihm (seitenheben/rec8: 14 statt 5). Knapp: ein ruhender Ellbogen
    # hat oft nur 0.6-0.8.
    min_visibility = 0.6
    # Gezaehlt wird mit dem Median der letzten sichtbaren Winkel: einzelne Ausreisser (Ellbogen
    # springt fuer 1-2 Frames) fallen heraus, 5 Frames verzoegern um ~0.07 s
    median_frames = 5

    def __init__(self, target=5, schwellen=None):
        super().__init__(target, schwellen)
        self.recent = {side: [] for side in self.sides}

    @property
    def min_angle(self):
        return self.max_angle - self.rueckweg

    def measure(self, pose):
        self.angle, self.visible = {}, {}
        for side in self.sides:
            points = (HUEFTE[side], SCHULTER[side], ELLBOGEN[side])
            angle = calculate_angle(*(pose.xy(i) for i in points))
            self.visible[side] = min(visibility(pose.landmarks[i]) for i in points) >= self.min_visibility
            if self.visible[side]:
                self.recent[side] = (self.recent[side] + [angle])[-self.median_frames:]
            self.angle[side] = sorted(self.recent[side])[len(self.recent[side]) // 2] if self.recent[side] else angle

    def count(self, t):
        for side in self.sides:
            if not self.visible[side]:
                continue
            if self.angle[side] < self.min_angle:
                if self.stage[side] == "up":
                    self.counter[side] += 1
                self.stage[side] = "down"
            # "up" erst, nachdem der Arm unten war (Startposition): am Anfang einer Aufnahme
            # liegt die Erkennung oft fuer einige Frames daneben (seitenheben/rec8 aus dem Video)
            elif self.angle[side] > self.max_angle and self.stage[side] is not None:
                self.stage[side] = "up"

    def fill(self, side):
        # Fortschritt dieses Arms: leer bis bar_start, 1 an der Zielhoehe, darueber bis UEBER_ZIEL
        return calculate_progress(self.angle[side], self.bar_start, self.max_angle, UEBER_ZIEL)

    def zurueck(self, side):
        return self.stage[side] == "up"     # oben gewesen, zaehlt erst unten


def visibility(landmark):
    # Ohne Angabe (None) gilt ein Punkt als sichtbar
    return 1.0 if landmark.visibility is None else landmark.visibility
