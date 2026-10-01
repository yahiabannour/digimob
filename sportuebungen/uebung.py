"""
******************************************************************************
 file:      uebung.py
 brief:     Basisklasse aller Uebungen. Eine Uebung rechnet nur: Pose rein, Zaehlerstand raus.
            Kamera, Bild, Ton, Einfuehrung und Wechsel zur naechsten Uebung macht der
            Uebungslauf (uebungslauf.py).
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12)
 info:      Eine Uebung fuellt measure(), count() und fill() aus, bei Bedarf ready() (Gate,
            z.B. Oberarme am Koerper) und lost() (kein gueltiger Messwert). fill(side) liefert
            den Balken einer Seite, bar_style sagt der Oberflaeche, wie sie ihn zeichnet.
******************************************************************************
"""
import logging


class Uebung:
    title = ""                  # im Menue, z.B. "Abduktion Schulter"
    name = ""                   # im Videobild; ohne Umlaute, cv2.putText() kann keine zeichnen
    image = ""                  # Beispielbild waehrend der Einfuehrung
    demo = ""                   # 3D-Demo (Video, tools/blender_demos.py), ersetzt das Beispielbild
    intro = ""                  # Ansage waehrend der Einfuehrung
    sides = ("left", "right")   # Butterfly und Rotation zaehlen nur eine Bewegung: ("both",)
    target_message = "Super, versuchen Sie {} mal zu schaffen!"
    # Balken: "split" = ein senkrechter Balken je Seite, "center" = ein waagerechter Balken, der
    # aus der Mitte nach links/rechts waechst (Kopfdrehung), "single" = ein Balken (sides ("both",))
    bar_style = "split"

    def __init__(self, target=5):
        self.target = target    # None = nur zaehlen (Aufnahmen testen)
        self.counter = {side: 0 for side in self.sides}
        self.stage = {side: None for side in self.sides}
        self.valid = False      # hatte der letzte Frame einen gueltigen Messwert?
        self.message = "Jetzt koennen Sie mit dieser Uebung starten"

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
        """Balken je Seite, z.B. {"left": 0..1, "right": 0..1}; None = dieser Balken leer. Gleiches
        Gate wie beim Zaehlen, per Konstruktion: ungueltiger Frame -> alle None."""
        return {side: self.fill(side) if self.valid else None for side in self.sides}

    def shown_count(self, side):
        """Im Videobild: verbleibende Wiederholungen, im Aufnahmen-Test die gezaehlten."""
        n = self.counter[side]
        return n if self.target is None else self.target - n

    # --- Das fuellt jede Uebung aus ------------------------------------------
    def measure(self, pose): raise NotImplementedError   # Winkel/Abstaende berechnen
    def ready(self):         return True                 # Gate, z.B. Oberarme am Koerper
    def count(self, t):      raise NotImplementedError   # Zustandsautomat -> self.counter
    def fill(self, side):    raise NotImplementedError   # Balken 0..1 einer Seite aus ihren Messwerten
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
