"""
******************************************************************************
 file:      systest.py
 brief:     Systemtest der Wiederholungszaehler auf den aufgezeichneten Keypoints. Statt Kamera,
            Video und Pose-Erkennung spielt der Test die keypoints_<modell>.jsonl einer Aufnahme
            von tools/record_exercise.py in die Uebung und vergleicht die gezaehlten
            Wiederholungen mit der Ground Truth (expected_reps in summary.json, und falls
            angegeben expected_per_side pro Seite).

            Aufruf (aus python/):  pytest        oder  python systest.py
            Welche Aufnahmen geprueft werden, steht in systest.txt.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 ext. libs: pytest
 info:      Ohne Kamera, Fenster und Sprachausgabe, und ohne Pose-Modell: die Keypoints sind
            schon aufgezeichnet. Die Uebung bekommt pro Frame genau das, was sie auch in der App
            bekommt: uebung.update(pose, t). Damit laeuft der Test schnell und immer gleich,
            waehrend "Aufnahmen testen" in der App dieselben Aufnahmen in Echtzeit ueber das
            Video prueft.
******************************************************************************
"""
import json
import os
import sys

import pytest

from tools.recording_format import SUMMARY_FILE, erwartet_je_seite, read_poses, uebung_fuer

ORDNER = os.path.dirname(os.path.abspath(__file__))

# systest.txt: eine Keypoints-Datei pro Zeile (relativ zu python/), # ueberspringt die Zeile
with open(os.path.join(ORDNER, "systest.txt"), encoding="utf-8") as f:
    AUFNAHMEN = [z.strip() for z in f if z.strip() and not z.strip().startswith("#")]
if not AUFNAHMEN:
    # Sonst ueberspringt pytest den Test nur und meldet trotzdem Erfolg
    raise ValueError("systest.txt: keine Aufnahme eingetragen (alle Zeilen mit #)")


@pytest.mark.parametrize("pfad", AUFNAHMEN, ids=["/".join(p.split("/")[-3:-1]) for p in AUFNAHMEN])
def test_zaehlt_wiederholungen(pfad, pytestconfig):
    keypoints = os.path.join(ORDNER, pfad)
    with open(os.path.join(os.path.dirname(keypoints), SUMMARY_FILE), encoding="utf-8") as f:
        summary = json.load(f)

    # Wie "Aufnahmen testen": nur zaehlen (target=None), gezaehlt wird ab dem ersten Frame, mit den
    # Schwellen des Presets der Aufnahme (summary.json "preset", ohne Angabe "voll")
    uebung = uebung_fuer(summary)

    for t, pose in read_poses(keypoints, summary["resolution"]):
        # Zeit aus der Aufnahme: das Ergebnis haengt nicht davon ab, wie schnell der Rechner
        # die Aufnahme abspielt (Haltezeit bei Bizepscurls)
        uebung.update(pose, t)

    gezaehlt, erwartet = uebung.total, summary["expected_reps"]
    # "<gezaehlt>/<erwartet> <preset>" in die Zeile der Aufnahme, vor PASSED/FAILED. write() ohne
    # Zeilenumbruch, sonst wiederholt pytest den Testnamen (darum auch -v in pytest.ini).
    # Uebungen mit zwei Seiten (Arme, Kopfdrehung) zeigen dahinter jede Seite; geprueft wird
    # total, die kleinere der beiden, und jede Seite (erwartet_je_seite). Butterfly und Rotation
    # haben nur einen Zaehler.
    ausgabe = f"{gezaehlt}/{erwartet} {summary.get('preset', 'voll')} "
    if len(uebung.sides) > 1:
        ausgabe += f"(links {uebung.counter['left']}, rechts {uebung.counter['right']}) "
    reporter = pytestconfig.pluginmanager.get_plugin("terminalreporter")
    if reporter:
        reporter.write(ausgabe)
    pro_seite = erwartet_je_seite(uebung, erwartet, summary.get("expected_per_side"))
    assert (gezaehlt, uebung.counter) == (erwartet, pro_seite)


if __name__ == "__main__":
    # "python systest.py" wuerde die Datei sonst nur einlesen und sich ohne Ausgabe beenden:
    # die Tests startet erst pytest.
    sys.exit(pytest.main([__file__]))
