"""
******************************************************************************
 file:      uebungen.py
 brief:     Die einzige Liste der Uebungen. Neue Uebung: Datei anlegen (Klasse von Uebung aus
            uebung.py ableiten), hier in UEBUNGEN und OP_ARTEN eintragen.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12)
 info:      Genutzt von Hauptprogramm.py (Menues, Wiederholungen), aufnahmen_test.py,
            systest.py und tools/record_exercise.py.
******************************************************************************
"""
from abduktion import Abduktion
from bizepscurls import Bizepscurls
from butterfly import Butterfly
from flexion_schulter import FlexionSchulter
from halswirbelsaeule import Halswirbelsaeule
from mobilisation_schulter import MobilisationSchulter
from rotation import Rotation
from seitenheben import Seitenheben

# Name = Modulname = Ordner unter recordings/ (summary.json: "exercise")
UEBUNGEN = {
    "abduktion": Abduktion,
    "bizepscurls": Bizepscurls,
    "butterfly": Butterfly,
    "flexion_schulter": FlexionSchulter,
    "halswirbelsaeule": Halswirbelsaeule,
    "mobilisation_schulter": MobilisationSchulter,
    "rotation": Rotation,
    "seitenheben": Seitenheben,
}

# Uebungen je OP-Art, in der Reihenfolge der Auswahlseite und von "Alle automatisch starten"
OP_ARTEN = {
    "Bauch-OP": ["halswirbelsaeule", "seitenheben", "abduktion", "flexion_schulter",
                 "mobilisation_schulter", "butterfly", "rotation", "bizepscurls"],
    "Brust-OP": ["halswirbelsaeule", "butterfly", "rotation"],
}

# Postoperativer Tag -> Wiederholungen
TAG_WIEDERHOLUNGEN = {"Tag 1": 5, "Tag 2": 6, "Tag 3": 7, "Tag 4": 8, "ab Tag 5": 10}
