"""
******************************************************************************
 file:      mobilisation_schulter.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Mobilisations Schulter
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.9.2)
 int. libs:
 ext. libs:
 info:      Gemeinsamer Ablauf aller Uebungen und Zaehler: uebung.py (Armheben)
******************************************************************************
"""
from uebung import Armheben


class MobilisationSchulter(Armheben):
    title = "Mobilisation Schulter"
    name = "Mobilisation Schulter"
    image = "bilder/Mobilisation_Schulter.jpg"
    demo = "demos/mobilisation_schulter.mp4"
    intro = ("Sie fangen jetzt mit der Übung Mobilisation Schulter an. Bitte legen Sie die Hände runter "
             "neben den Körper, damit die Übung starten kann.")
    max_angle = 70      # Arm gehoben
    # Einfach = Voll: die Zielhoehe liegt schon knapp ueber dem Ruhewinkel der Pflege-Aufnahmen
    # (bis 50 Grad), tiefer laege "unten" (Zielhoehe - rueckweg) im Ruhewinkel
    einfach = {}
