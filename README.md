**English** | [Deutsch](README.de.md)

# 🏥 DigiMob – Camera-Based Rehab Trainer 🤸

An intelligent rehabilitation assistant built with **MediaPipe** pose estimation. It watches patients through a
camera, counts every repetition from their joint angles, and coaches them with spoken feedback and live progress
bars – so patients can train safely after abdominal or chest surgery, without any personal data ever being stored.

DigiMob runs in two versions with the same counting logic:

* 🖥️ **Desktop app** (`python/`): **Python**, **OpenCV** and **Tkinter**, for a PC with a webcam.
* 📱 **Android app** (`android/`): **Kotlin** and **Jetpack Compose**, for a hospital tablet with its front camera.

| 🎥 Exercise with detected body keypoints | ⚙️ Exercise selection with adjustable thresholds |
|---|---|
| ![Exercise page](docs/uebung.jpg) | ![Selection page](docs/auswahl.jpg) |

*The screenshots use the 3D demo video as the camera image, so no real person is shown.*

---

## ✨ Features

### 🏋️ Guided Exercises
* **8 Rehab Exercises**: Shoulder abduction, lateral raise, shoulder flexion, shoulder mobilisation, biceps curls,
  butterfly, seated rotation and neck rotation.
* **🎬 3D Demo Videos**: Before every exercise, a rendered 3D video shows exactly how the movement is done.
* **📋 Training Plans**: Choose the type of surgery and the post-operative day – the app sets the number of
  repetitions automatically.

---

### 🧠 Smart Counting
* **Joint-Angle Tracking**: Measures joint angles and distances for each side of the body separately.
* **Robust Detection**: Hysteresis, hold times, median filtering and visibility checks keep the count stable, even
  when the pose estimation jitters.
* **Made for the Hospital Bed**: Arm angles are measured against a virtual hip computed from both shoulders, so the
  camera only needs to see the head, shoulders and arms.

---

### 📊 Live Feedback
* **Progress Bars**: One bar per arm with a countdown, a target line and a green check mark for every repetition.
* **🔊 Voice Coaching**: Spoken messages announce the goal, the half-way point, the last repetition and the finish.

---

### 🎚️ Adaptable & Private
* **Full & Easy Presets**: Lower targets for weaker patients, adjustable per exercise with sliders and saved safely
  against power loss.
* **🔒 Privacy First**: No user profiles, no stored progress, no patient data.

---

## 📖 Background

DigiMob is a project by **Prof. Hanno Homann** at Hochschule Hannover (University of Applied Sciences and Arts).
**Tanja Wysinsky**, a nurse at Hannover Medical School (MHH), used the app in her bachelor thesis (summer 2025) and
gave medical advice on the exercises. She still uses the app today.

I extended the project in autumn 2026. The commit history shows who contributed what.

---

## 🚀 My Contributions

* **🧠 New Counting Algorithms**: Redeveloped the counting logic for all eight exercises.
* **📱 Android Tablet App**: Ported the whole app to Android in **Kotlin** – Jetpack Compose, CameraX, MediaPipe and
  Android TextToSpeech, structured with Clean Architecture in feature modules. Includes a recording mode to capture
  test data directly on the tablet.
* **🩺 Live Tests at the MHH**: Recorded the exercises together with Ms. Wysinsky, including simulated patient
  movements, and tuned and validated every algorithm on these recordings.
* **🎬 3D Instruction Videos**: Replaced the old example photos with videos rendered in **Blender** – a 3D figure
  half-sitting in a hospital bed in a modelled hospital room, with movements scripted in Python and timed after the
  real recordings.
* **⚡ Faster Pipeline**: Moved pose detection to a background thread – **6.6 → 9.8 fps** (abduction) and
  **6.0 → 13.7 fps** (neck rotation).
* **🏗️ Clean Architecture**: One shared base class for all exercises, the exercise loop separated from the GUI,
  and a camera abstraction that runs on a live webcam or replays recorded videos.
* **🧪 Over 360 Automated Tests**: 100 in Python and 267 in Kotlin, including system tests that replay 58 webcam and
  18 tablet recordings and require the **exact** repetition count – so both versions are proven to count the same.
* **🔒 Data Minimisation**: Removed the user database and stored progress.
* **🔊 Speech Fixes**: Made the voice output work on Python 3.12 and made spoken messages cancellable.

---

## ⚙️ How It Works

```
camera frame ─► zoom (optional) ─► MediaPipe pose ─► uebung.update(pose)
                                                           │
           GUI: video + progress bars ◄── speech feedback ◄┘
```

Each exercise turns the pose into a measurement – for example the hip–shoulder–elbow angle – and counts a
repetition when the target is reached and the movement returns. The exercises are pure Python without a MediaPipe
import, so the tests can replay recorded keypoints without a camera.

---

## 📱 The Android App

The tablet version is a 1:1 port of the desktop counting logic, so both apps count every recording exactly the same.

* **Stack**: Kotlin, Jetpack Compose (Material 3), CameraX, MediaPipe Pose Landmarker, ExoPlayer, DataStore, Koin
  and type-safe Navigation.
* **Architecture**: Clean Architecture with feature-layered modules (`domain` / `data` / `presentation`) and the
  MVI pattern. The domain modules are pure Kotlin, so all counting logic is tested without a device.
* **Training Screen**: Mirrored camera image with the 3D demo video before each exercise, progress bars, keypoints,
  head zoom and voice output. The camera switches off when the app is hidden.
* **Recording on the Tablet** (debug build): Record an exercise with its real repetition count; the recordings are
  pulled to the PC and replayed by the tablet system test.
* **Target Device**: Android 14 tablet in landscape.

The full documentation – modules, data flow and testing – is in [`android/README.md`](android/README.md).

---

## 🚀 Getting Started

### 🖥️ Desktop App

#### 📋 Prerequisites

Python 3.12, a webcam and audio output.

#### 1. Install Dependencies

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r python/requirements.txt
```

#### 2. Download the Voice Model

The German voice model is too large for git. Download `de_DE-thorsten-low.onnx` from
[Piper voices](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/low) into
`python/stimmen/`.

#### 3. Run the App

```bash
cd python
python Hauptprogramm.py
```

Start it with `--entwickler` to unlock the **"Aufnahmen testen"** button, which replays recordings instead of the
webcam.

#### 4. Run the Tests

```bash
cd python
pytest
```

No camera, window or audio needed.

### 📱 Android App

#### 📋 Prerequisites

Android Studio with JDK 17 and an Android 14 tablet. The build copies the demo videos and the pose model from
`python/`, so keep both folders together.

#### Build, Install and Test

```bash
cd android
./gradlew installDebug                                   # build and install on the connected tablet
./gradlew testDebugUnitTest :core:domain:test :feature:training:domain:test   # unit tests
./gradlew :core:domain:systemTest :core:domain:tabletTest                    # replay the recordings
```

---

## 🕹️ User Controls

Both apps work the same way:

1. **Main menu** → *Übungen wählen* (choose exercises)
2. **Type of surgery** → abdominal or chest surgery, plus the post-operative day
3. **Selection** → tick the exercises and adjust the thresholds, or start all of them automatically
4. **Exercise page** → *Pause*, *Keypoints* (show the detected body points), *Zoom* and *Stop*

On the tablet, a double tap on the demo video skips the introduction. The user interface is in German.

---

## 📂 Project Structure

* `python/`: The desktop app.
  * `Hauptprogramm.py`: The Tkinter GUI – menus, exercise selection and the exercise page.
  * `uebungslauf.py`: The exercise loop – camera, pose detection, counting and speech for every frame.
  * `uebung.py`: The shared base class for all exercises.
  * `uebungen.py`: The single list of exercises and training plans.
  * `abduktion.py`, `bizepscurls.py`, …: One file per exercise with its texts and thresholds.
  * `camera/`: Live webcam and video file playback with the same interface.
  * `pose.py`, `mediapipe_modelle.py`: Pose data and the MediaPipe wrapper.
  * `sprachausgabe.py`: Voice output with Piper TTS.
  * `schwellen.py`: Saving and loading the thresholds.
  * `tools/`: Recording and keypoint extraction tools.
  * `einheitentest.py`, `systest.py`: Unit tests and the system test.
  * `recordings/`: 58 recorded exercises as keypoints with their expected repetition count.
  * `demos/`: The rendered 3D instruction videos.
* `android/`: The Android app.
  * `app/`: Application, main activity, navigation and dependency injection.
  * `core/`: The exercises and their counting logic (pure Kotlin), threshold storage, design system and shared UI
    helpers.
  * `feature/menu/`: Main menu, type of surgery and exercise selection.
  * `feature/training/`: Training session, camera, MediaPipe, voice output and the training screen.
  * `build-logic/`: Gradle convention plugins.
  * `recordings/`: 18 exercises recorded on the tablet.
