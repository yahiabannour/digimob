[English](README.md) | **Deutsch**

# 🏥 DigiMob – Kamerabasierter Reha-Trainer 🤸

Ein intelligenter Reha-Assistent mit **MediaPipe**-Posenerkennung. Er beobachtet Patientinnen und Patienten über
eine Kamera, zählt jede Wiederholung anhand der Gelenkwinkel und begleitet das Training mit gesprochenem Feedback
und Live-Fortschrittsbalken – damit nach einer Bauch- oder Brust-OP sicher trainiert werden kann, ohne dass jemals
persönliche Daten gespeichert werden.

DigiMob gibt es in zwei Versionen mit derselben Zähllogik:

* 🖥️ **Desktop-App** (`python/`): **Python**, **OpenCV** und **Tkinter**, für einen PC mit Webcam.
* 📱 **Android-App** (`android/`): **Kotlin** und **Jetpack Compose**, für ein Klinik-Tablet mit Frontkamera.

| 🎥 Übung mit erkannten Körperpunkten | ⚙️ Übungsauswahl mit einstellbaren Schwellen |
|---|---|
| ![Übungsseite](docs/uebung.jpg) | ![Auswahlseite](docs/auswahl.jpg) |

*Die Screenshots verwenden das 3D-Demovideo als Kamerabild, daher ist keine echte Person zu sehen.*

---

## ✨ Funktionen

### 🏋️ Geführte Übungen
* **8 Reha-Übungen**: Abduktion Schulter, Seitheben, Flexion Schulter, Mobilisation Schulter, Bizepscurls,
  Butterfly, Rotation im Sitzen und Halswirbelsäule.
* **🎬 3D-Demovideos**: Vor jeder Übung zeigt ein gerendertes 3D-Video genau, wie die Bewegung ausgeführt wird.
* **📋 Trainingspläne**: OP-Art und postoperativen Tag wählen – die App legt die Anzahl der Wiederholungen
  automatisch fest.

---

### 🧠 Intelligente Zählung
* **Gelenkwinkel-Erfassung**: Misst Gelenkwinkel und Abstände für jede Körperseite getrennt.
* **Robuste Erkennung**: Hysterese, Haltezeiten, Medianfilter und Sichtbarkeitsprüfung halten die Zählung stabil,
  auch wenn die Posenerkennung zittert.
* **Fürs Krankenbett gemacht**: Armwinkel werden gegen eine virtuelle Hüfte gemessen, die aus beiden Schultern
  berechnet wird – die Kamera muss nur Kopf, Schultern und Arme sehen.

---

### 📊 Live-Feedback
* **Fortschrittsbalken**: Ein Balken pro Arm mit Countdown, Ziellinie und grünem Haken für jede Wiederholung.
* **🔊 Sprachbegleitung**: Gesprochene Meldungen kündigen das Ziel, die Hälfte, die letzte Wiederholung und das
  Ende an.

---

### 🎚️ Anpassbar & datensparsam
* **Voll & Einfach**: Niedrigere Ziele für schwächere Patienten, pro Übung per Schieberegler einstellbar und
  ausfallsicher gespeichert.
* **🔒 Datenschutz zuerst**: Keine Benutzerprofile, kein gespeicherter Fortschritt, keine Patientendaten.

---

## 📖 Hintergrund

DigiMob ist ein Projekt von **Prof. Hanno Homann** an der Hochschule Hannover. **Tanja Wysinsky**, Pflegekraft an
der Medizinischen Hochschule Hannover, hat die App in ihrer Bachelorarbeit (Sommersemester 2025) eingesetzt und die
Übungen medizinisch beraten. Sie nutzt die App bis heute weiter.

Ich habe das Projekt im Herbst 2026 erweitert. Die Commit-Historie zeigt, wer was beigetragen hat.

---

## 🚀 Meine Beiträge

* **🧠 Neue Zählalgorithmen**: Die Zähllogik für alle acht Übungen neu entwickelt.
* **📱 Android-Tablet-App**: Die gesamte App in **Kotlin** auf Android portiert – Jetpack Compose, CameraX, MediaPipe
  und Android TextToSpeech, aufgebaut nach Clean Architecture in Feature-Modulen. Mit Aufnahmemodus, um Testdaten
  direkt auf dem Tablet aufzuzeichnen.
* **🩺 Live-Tests an der MHH**: Die Übungen gemeinsam mit Frau Wysinsky aufgenommen, auch mit nachgestellten
  Patientenbewegungen, und jeden Algorithmus an diesen Aufnahmen abgestimmt und geprüft.
* **🎬 3D-Anleitungsvideos**: Die alten Beispielfotos durch mit **Blender** gerenderte Videos ersetzt – eine
  3D-Figur halb sitzend im Krankenbett in einem nachgebauten Krankenzimmer, die Bewegungen in Python gescriptet und
  nach den echten Aufnahmen getaktet.
* **⚡ Schnellere Verarbeitung**: Posenerkennung in einen Hintergrund-Thread verlegt – **6,6 → 9,8 fps**
  (Abduktion) und **6,0 → 13,7 fps** (Halswirbelsäule).
* **🏗️ Saubere Architektur**: Eine gemeinsame Basisklasse für alle Übungen, der Übungsablauf von der Oberfläche
  getrennt und eine Kamera-Abstraktion für Live-Webcam oder aufgezeichnete Videos.
* **🧪 Über 360 automatisierte Tests**: 100 in Python und 267 in Kotlin, darunter Systemtests, die 58 Webcam- und
  18 Tablet-Aufnahmen abspielen und die **exakte** Wiederholungszahl verlangen – so zählen beide Versionen
  nachweislich gleich.
* **🔒 Datensparsamkeit**: Benutzerdatenbank und gespeicherten Fortschritt entfernt.
* **🔊 Sprachausgabe**: Für Python 3.12 repariert und gesprochene Meldungen abbrechbar gemacht.

---

## ⚙️ Funktionsweise

```
Kamerabild ─► Zoom (optional) ─► MediaPipe-Pose ─► uebung.update(pose)
                                                          │
       Oberfläche: Video + Balken ◄── Sprachausgabe ◄─────┘
```

Jede Übung berechnet aus der Pose einen Messwert – zum Beispiel den Winkel Hüfte–Schulter–Ellbogen – und zählt eine
Wiederholung, wenn das Ziel erreicht wird und die Bewegung zurückkehrt. Die Übungen sind reines Python ohne
MediaPipe-Import, deshalb können die Tests aufgezeichnete Körperpunkte ohne Kamera abspielen.

---

## 📱 Die Android-App

Die Tablet-Version ist eine 1:1-Portierung der Zähllogik der Desktop-App, daher zählen beide Apps jede Aufnahme
exakt gleich.

* **Technik**: Kotlin, Jetpack Compose (Material 3), CameraX, MediaPipe Pose Landmarker, ExoPlayer, DataStore, Koin
  und typsichere Navigation.
* **Architektur**: Clean Architecture mit Feature-Modulen in Schichten (`domain` / `data` / `presentation`) und dem
  MVI-Muster. Die Domain-Module sind reines Kotlin, deshalb wird die gesamte Zähllogik ohne Gerät getestet.
* **Trainingsseite**: Gespiegeltes Kamerabild, vor jeder Übung das 3D-Demovideo, Fortschrittsbalken, Keypoints,
  Kopf-Zoom und Sprachausgabe. Die Kamera schaltet sich aus, wenn die App nicht sichtbar ist.
* **Aufnehmen auf dem Tablet** (Debug-Build): Eine Übung mit ihrer echten Wiederholungszahl aufnehmen; die
  Aufnahmen werden auf den PC geholt und vom Tablet-Systemtest abgespielt.
* **Zielgerät**: Android-14-Tablet im Querformat.

Die vollständige Dokumentation – Module, Datenfluss und Tests – steht in [`android/README.md`](android/README.md)
(auf Englisch).

---

## 🚀 Erste Schritte

### 🖥️ Desktop-App

#### 📋 Voraussetzungen

Python 3.12, eine Webcam und eine Audioausgabe.

#### 1. Abhängigkeiten installieren

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r python/requirements.txt
```

#### 2. Stimmmodell herunterladen

Das deutsche Stimmmodell ist zu groß für git. `de_DE-thorsten-low.onnx` von
[Piper voices](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/low) herunterladen und nach
`python/stimmen/` legen.

#### 3. App starten

```bash
cd python
python Hauptprogramm.py
```

Mit `--entwickler` erscheint der Knopf **„Aufnahmen testen“**, der Aufnahmen statt der Webcam abspielt.

#### 4. Tests ausführen

```bash
cd python
pytest
```

Keine Kamera, kein Fenster und keine Audioausgabe nötig.

### 📱 Android-App

#### 📋 Voraussetzungen

Android Studio mit JDK 17 und ein Android-14-Tablet. Der Build kopiert die Demovideos und das Posenmodell aus
`python/`, deshalb müssen beide Ordner zusammen bleiben.

#### Bauen, installieren und testen

```bash
cd android
./gradlew installDebug                                   # bauen und auf das angeschlossene Tablet installieren
./gradlew testDebugUnitTest :core:domain:test :feature:training:domain:test   # Unit-Tests
./gradlew :core:domain:systemTest :core:domain:tabletTest                    # Aufnahmen abspielen
```

---

## 🕹️ Bedienung

Beide Apps funktionieren gleich:

1. **Hauptmenü** → *Übungen wählen*
2. **OP-Art** → Bauch-OP oder Brust-OP, dazu der postoperative Tag
3. **Auswahl** → Übungen ankreuzen und Schwellen anpassen, oder alle automatisch starten
4. **Übungsseite** → *Pause*, *Keypoints* (erkannte Körperpunkte zeigen), *Zoom* und *Stop*

Auf dem Tablet überspringt ein Doppeltipp auf das Demovideo die Einführung.

---

## 📂 Projektstruktur

* `python/`: Die Desktop-App.
  * `Hauptprogramm.py`: Die Tkinter-Oberfläche – Menüs, Übungsauswahl und Übungsseite.
  * `uebungslauf.py`: Der Übungsablauf – Kamera, Posenerkennung, Zählung und Sprache für jedes Bild.
  * `uebung.py`: Die gemeinsame Basisklasse aller Übungen.
  * `uebungen.py`: Die einzige Liste aller Übungen und Trainingspläne.
  * `abduktion.py`, `bizepscurls.py`, …: Eine Datei pro Übung mit Texten und Schwellen.
  * `camera/`: Live-Webcam und Videowiedergabe mit derselben Schnittstelle.
  * `pose.py`, `mediapipe_modelle.py`: Posendaten und der MediaPipe-Wrapper.
  * `sprachausgabe.py`: Sprachausgabe mit Piper TTS.
  * `schwellen.py`: Speichern und Laden der Schwellen.
  * `tools/`: Werkzeuge für Aufnahmen und Keypoint-Extraktion.
  * `einheitentest.py`, `systest.py`: Unit-Tests und Systemtest.
  * `recordings/`: 58 aufgezeichnete Übungen als Körperpunkte mit ihrer Soll-Anzahl.
  * `demos/`: Die gerenderten 3D-Anleitungsvideos.
* `android/`: Die Android-App.
  * `app/`: Application, Haupt-Activity, Navigation und Dependency Injection.
  * `core/`: Die Übungen und ihre Zähllogik (reines Kotlin), Speichern der Schwellen, Design-System und gemeinsame
    UI-Helfer.
  * `feature/menu/`: Hauptmenü, OP-Art und Übungsauswahl.
  * `feature/training/`: Trainingsablauf, Kamera, MediaPipe, Sprachausgabe und Trainingsseite.
  * `build-logic/`: Gradle-Convention-Plugins.
  * `recordings/`: 18 auf dem Tablet aufgenommene Übungen.
