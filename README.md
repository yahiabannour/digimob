# DigiMob – kamerabasierte Reha-Übungen

Eine Desktop-Anwendung, die Patientinnen und Patienten nach Bauch- oder Brust-Operationen durch Reha-Übungen führt.
Eine Webcam erfasst den Körper mit **MediaPipe-Posenerkennung**. Die App **zählt Wiederholungen anhand von
Gelenkwinkeln**, getrennt für jede Seite, zeigt den Fortschritt als Balken neben dem Livebild und gibt
**gesprochenes Feedback** (Piper Text-to-Speech). Es werden keine Patientendaten gespeichert.

| Übung mit erkannten Körperpunkten | Übungsauswahl mit einstellbaren Schwellen |
|---|---|
| ![Übungsseite](docs/uebung.jpg) | ![Auswahlseite](docs/auswahl.jpg) |

*Die Screenshots verwenden das 3D-Demovideo als Kamerabild, daher ist keine echte Person zu sehen.*

Die App umfasst acht Übungen: Abduktion Schulter, Seitheben, Flexion Schulter, Mobilisation Schulter,
Bizepscurls, Butterfly, Rotation im Sitzen und Halswirbelsäule. Vor jeder Übung zeigt ein gerendertes
3D-Video den Bewegungsablauf.

## Hintergrund

DigiMob ist ein Projekt von Prof. Hanno Homann an der Hochschule Hannover. Tanja Wysinsky, Pflegekraft an der
Medizinischen Hochschule Hannover, hat die App in ihrer Bachelorarbeit (Sommersemester 2025) eingesetzt und die
Übungen medizinisch beraten.

Ich habe das Projekt im Herbst 2026 erweitert. Die Commit-Historie zeigt, wer was beigetragen hat.

## Meine Beiträge

- **Zählalgorithmen für alle acht Übungen neu entwickelt** – Messung über Gelenkwinkel bzw. Abstände, Zählung
  mit Hysterese, Haltezeiten und Medianfilter, getrennt für jede Körperseite
- **Live-Tests mit Frau Wysinsky (MHH)** – gemeinsam aufgenommene Übungen, auch mit nachgestellten
  Patientenbewegungen; an diesen Aufnahmen habe ich die Algorithmen und Schwellen entwickelt und geprüft, und der
  Systemtest spielt sie bei jedem Lauf erneut ab
- **3D-Anleitungsvideos mit Blender** – vorher zeigte die App nur Beispielfotos. Jetzt gibt es für jede Übung
  ein gerendertes Video: eine 3D-Figur halb sitzend im Krankenbett, in einem nachgebauten Krankenzimmer. Die
  Bewegungsabläufe sind in Python gescriptet, das Tempo stammt aus den echten Aufnahmen
- **Übungslogik refaktoriert** – die acht Übungsklassen teilen sich eine Basisklasse (`uebung.py`); jede
  Übungsdatei enthält nur noch Texte und Schwellen, und `uebungen.py` ist die einzige Liste aller Übungen
- **Übungsablauf von der Oberfläche getrennt** (`uebungslauf.py`) und die Posenerkennung in einen
  Hintergrund-Thread verlegt – die Bildrate stieg von 6,6 auf 9,8 fps (Abduktion) und von 6,0 auf 13,7 fps
  (Halswirbelsäule)
- **Kamera-Abstraktion** (`camera/`) – die App läuft mit einer Live-Webcam oder spielt aufgezeichnete Videos ab
- **Aufnahme-Werkzeuge** (`tools/`) – Übungen mit von Hand eingetragener Soll-Anzahl aufnehmen und die
  Körperpunkte als JSONL speichern; 58 Aufnahmen aller acht Übungen sind enthalten
- **Automatisierte Tests** (94 Tests mit pytest)
  - Unit-Tests für die Mathematik, die Schwellen, die Kamera und den Thread-Ablauf
  - ein Systemtest, der jede Aufnahme in die Zähler einspielt und die exakte Wiederholungszahl verlangt
- **Einstellbare Schwellen** pro Übung mit den Voreinstellungen „Voll“ und „Einfach“ für schwächere Patienten,
  ausfallsicher gespeichert
- **Datensparsamkeit** – Benutzerdatenbank und gespeicherter Fortschritt entfernt, die App speichert keine
  Patientendaten
- **Neue Oberfläche** – ein Fortschrittsbalken pro Arm mit Countdown und Ziellinie
- Sprachausgabe für Python 3.12 repariert und gesprochene Meldungen abbrechbar gemacht

## Funktionsweise

```
Kamerabild ─► Zoom (optional) ─► MediaPipe-Pose ─► uebung.update(pose)
                                                          │
       Oberfläche: Video + Balken ◄── Sprachausgabe ◄─────┘
```

Jede Übung berechnet aus der Pose einen Messwert (zum Beispiel den Winkel Hüfte–Schulter–Ellbogen) und zählt eine
Wiederholung, wenn das Ziel erreicht wird und die Bewegung zurückkehrt. Die Übungen sind reines Python ohne
MediaPipe-Import, deshalb können die Tests aufgezeichnete Körperpunkte ohne Kamera abspielen.

## Starten

Voraussetzungen: Python 3.12, eine Webcam und eine Audioausgabe.

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

Das deutsche Stimmmodell ist zu groß für git. `de_DE-thorsten-low.onnx` von
[Piper voices](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/low) herunterladen und nach
`sportuebungen/stimmen/` legen.

```bash
cd sportuebungen
python Hauptprogramm.py              # mit --entwickler lassen sich Aufnahmen statt der Webcam abspielen
pytest                               # Tests ausführen (keine Kamera nötig)
```

## Technologien

Python 3.12 · MediaPipe · OpenCV · Tkinter · Piper TTS · NumPy · pytest · Blender
