"""
******************************************************************************
 file:      balken.py
 brief:     Was ein Fortschrittsbalken ausser dem Fuellstand zeigt, ohne Tkinter: Bestaetigung nach
            einer gezaehlten Wiederholung, kurzes Stehenlassen ohne Messwert, hoechster Punkt der
            laufenden Wiederholung. Hauptprogramm.py zeichnet nur, was update() liefert.
 author(s): Yahia Bannour
 date:      10.10.2026
 software:  Python (Version: 3.12)
 info:      Ein BalkenZustand je Balken (linker Arm, rechter Arm, ...). Die Uhr ist austauschbar,
            damit die Zeiten (Bestaetigung, Halten) ohne Warten testbar sind.
******************************************************************************
"""
import time

BESTAETIGUNG_S = 0.6    # so lange grün mit Häkchen, nachdem eine Wiederholung gezählt wurde
# Ohne Messwert (keine Pose, Gate) bleibt der letzte Stand so lange stehen: mit den Armen oben
# verliert MediaPipe die Pose oft für 1-5 Frames, der Balken flackerte grün/weiß (Aufnahmen:
# Seitheben 40-mal, Bizepscurls 20-mal). Erst danach wird er leer.
HALTEN_S = 0.4


class BalkenZustand:
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.zahl = None        # zuletzt angezeigte Zahl; ändert sie sich, zählte eine Wiederholung
        self.until = 0.0        # Bestätigung bis
        self.peak = None        # höchster Punkt der laufenden Wiederholung
        self.zurueck = False    # letzter gültiger Stand von Balken.zurueck
        self.last = None        # letzter gültiger Füllstand
        self.seen = 0.0         # wann er gemessen wurde

    def update(self, b):
        """b: uebung.Balken oder None (nichts anzeigen). Gibt (fill, zurueck, bestaetigung, peak)
        zurück: fill None = leer, peak None = keine Markierung."""
        if b is None:
            return None, False, False, None
        now = self.clock()
        counted = self.zahl is not None and b.zahl != self.zahl
        if counted:
            self.until = now + BESTAETIGUNG_S
        self.zahl = b.zahl
        if b.fill is not None:
            if counted or (self.zurueck and not b.zurueck):
                self.peak = None        # Wiederholung fertig: neu messen, sobald es wieder hochgeht
            if self.peak is not None:
                self.peak = max(self.peak, b.fill)
            elif self.last is not None and b.fill > self.last + 0.02:
                self.peak = b.fill
            self.zurueck, self.last, self.seen = b.zurueck, b.fill, now
            fill, zurueck = b.fill, b.zurueck
        elif self.last is not None and now - self.seen < HALTEN_S:
            fill, zurueck = self.last, self.zurueck     # kurz kein Messwert: stehen lassen
        else:
            fill, zurueck = None, False
        peak = self.peak
        if peak is None or peak < 0.05 or fill is None or peak <= fill + 0.03:
            peak = None     # nur zeigen, wenn der Arm gemessen und schon wieder tiefer ist als sein höchster Punkt
        return fill, zurueck, now < self.until, peak
