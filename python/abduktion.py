"""
******************************************************************************
 file:      abduktion.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Abduktion
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs:
 hardware:
 info:      Gemeinsamer Ablauf aller Uebungen und Zaehler: uebung.py (Armheben)
******************************************************************************
"""
from uebung import Armheben


class Abduktion(Armheben):
    title = "Abduktion Schulter"
    demo = "demos/abduktion.mp4"
    intro = ("Sie fangen jetzt mit der Übung Abduktion an. Bitte legen Sie die Hände unten "
             "neben den Körper, damit die Übung starten kann.")
    max_angle = 150     # Arm oben
    # Einfach: Patient muss rund 20 % weniger hoch heben (mit der medizinischen Beratung abstimmen)
    einfach = {"max_angle": 120}
