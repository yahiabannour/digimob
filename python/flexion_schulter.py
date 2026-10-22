"""
******************************************************************************
 file:      flexion_schulter.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Flexion Schulter
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs:
 info:      Gemeinsamer Ablauf aller Uebungen und Zaehler: uebung.py (Armheben)
******************************************************************************
"""
from uebung import Armheben


class FlexionSchulter(Armheben):
    title = "Flexion Schulter"
    demo = "demos/flexion_schulter.mp4"
    intro = ("Sie fangen jetzt mit der Übung Flexion Schulter an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    max_angle = 140     # Arm vorne oben
    # Einfach: Patient muss rund 20 % weniger hoch heben (mit der medizinischen Beratung abstimmen)
    einfach = {"max_angle": 110}
