**Deutsch** | [English](README.en.md)

# 🏥 DigiMob – Kamerabasierte Reha-Übungen 🤸

![Python](https://img.shields.io/badge/python-3.12-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)
![MediaPipe](https://img.shields.io/badge/mediapipe-pose-0097A7?style=for-the-badge&logo=google&logoColor=white)
![OpenCV](https://img.shields.io/badge/opencv-%235C3EE8.svg?style=for-the-badge&logo=opencv&logoColor=white)
![Blender](https://img.shields.io/badge/blender-%23F5792A.svg?style=for-the-badge&logo=blender&logoColor=white)
![pytest](https://img.shields.io/badge/tests-94%20passing-0A9EDC?style=for-the-badge&logo=pytest&logoColor=white)

Eine Desktop-App, die Patientinnen und Patienten nach Bauch- oder Brust-Operationen durch Reha-Übungen führt. Eine
Webcam erfasst den Körper mit **MediaPipe-Posenerkennung**, die App **zählt Wiederholungen anhand von
Gelenkwinkeln** für jede Seite, zeigt den Fortschritt als Balken neben dem Livebild und gibt **gesprochenes
Feedback** – ohne Patientendaten zu speichern.

| 🎥 Übung mit erkannten Körperpunkten | ⚙️ Übungsauswahl mit einstellbaren Schwellen |
|---|---|
| ![Übungsseite](docs/uebung.jpg) | ![Auswahlseite](docs/auswahl.jpg) |

*Die Screenshots verwenden das 3D-Demovideo als Kamerabild, daher ist keine echte Person zu sehen.*

---

## ✨ Funktionen

* **🏋️ 8 Übungen**: Abduktion Schulter, Seitheben, Flexion Schulter, Mobilisation Schulter, Bizepscurls,
  Butterfly, Rotation im Sitzen und Halswirbelsäule
* **🎬 3D-Demo vor jeder Übung**: ein gerendertes Video zeigt den Bewegungsablauf
* **📊 Live-Fortschritt**: ein Balken pro Arm mit Countdown und Ziellinie
* **🔊 Gesprochenes Feedback** mit Piper Text-to-Speech
* **🎚️ Einstellbare Schwellen** mit den Voreinstellungen „Voll“ und „Einfach“ für schwächere Patienten
* **🔒 Datenschutz**: keine Benutzerprofile, kein gespeicherter Fortschritt, keine Patientendaten

---

## 📖 Hintergrund

DigiMob ist ein Projekt von **Prof. Hanno Homann** an der Hochschule Hannover. **Tanja Wysinsky**, Pflegekraft an
der Medizinischen Hochschule Hannover, hat die App in ihrer Bachelorarbeit (Sommersemester 2025) eingesetzt und die
Übungen medizinisch beraten. Sie nutzt die App bis heute weiter.

Ich habe das Projekt im Herbst 2026 erweitert. Die Commit-Historie zeigt, wer was beigetragen hat.

---

## 🚀 Meine Beiträge

### 🧠 Zählalgorithmen
* **Zählalgorithmen für alle acht Übungen neu entwickelt** – Gelenkwinkel bzw. Abstände, Hysterese, Haltezeiten
  und Medianfilter, getrennt für jede Körperseite
* **Live-Tests mit Frau Wysinsky (MHH)** – gemeinsam aufgenommene Übungen, auch mit nachgestellten
  Patientenbewegungen; an diesen Aufnahmen habe ich die Algorithmen und Schwellen entwickelt und geprüft

### 🎬 3D-Anleitungsvideos mit Blender
* Vorher zeigte die App nur Beispielfotos – jetzt gibt es für jede Übung ein **gerendertes Video**: eine 3D-Figur
  halb sitzend im Krankenbett, in einem nachgebauten Krankenzimmer
* Bewegungsabläufe **in Python gescriptet**, das Tempo stammt aus den echten Aufnahmen

### 🏗️ Architektur & Performance
* **Übungslogik refaktoriert** – eine gemeinsame Basisklasse (`uebung.py`); jede Übungsdatei enthält nur noch Texte
  und Schwellen, und `uebungen.py` ist die einzige Liste aller Übungen
* **Übungsablauf von der Oberfläche getrennt** (`uebungslauf.py`), Posenerkennung im Hintergrund-Thread –
  **6,6 → 9,8 fps** (Abduktion) und **6,0 → 13,7 fps** (Halswirbelsäule)
* **Kamera-Abstraktion** (`camera/`) – Live-Webcam oder Wiedergabe aufgezeichneter Videos

### 🧪 Tests
* **94 automatisierte Tests** mit pytest – Unit-Tests für Mathematik, Schwellen, Kamera und Thread-Ablauf
* **Systemtest**, der 58 Aufnahmen in die Zähler einspielt und die **exakte** Wiederholungszahl verlangt
* **Aufnahme-Werkzeuge** (`tools/`) – Übungen mit von Hand eingetragener Soll-Anzahl aufnehmen, Körperpunkte als
  JSONL

### 🔧 Weiteres
* Schwellen ausfallsicher gespeichert
* Benutzerdatenbank und gespeicherten Fortschritt entfernt (**Datensparsamkeit**)
* Sprachausgabe für Python 3.12 repariert und gesprochene Meldungen abbrechbar gemacht

---

## ⚙️ Funktionsweise

```
Kamerabild ─► Zoom (optional) ─► MediaPipe-Pose ─► uebung.update(pose)
                                                          │
       Oberfläche: Video + Balken ◄── Sprachausgabe ◄─────┘
```

Jede Übung berechnet aus der Pose einen Messwert (zum Beispiel den Winkel Hüfte–Schulter–Ellbogen) und zählt eine
Wiederholung, wenn das Ziel erreicht wird und die Bewegung zurückkehrt. Die Übungen sind reines Python ohne
MediaPipe-Import, deshalb können die Tests aufgezeichnete Körperpunkte ohne Kamera abspielen.

---

## 🛠️ Starten

**Voraussetzungen:** Python 3.12, eine Webcam und eine Audioausgabe.

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

---

## 🧰 Technologien

Python 3.12 · MediaPipe · OpenCV · Tkinter · Piper TTS · NumPy · pytest · Blender
