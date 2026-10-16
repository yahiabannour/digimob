**English** | [Deutsch](README.de.md)

# 🏥 DigiMob – Camera-Based Rehab Trainer 🤸

An intelligent rehabilitation assistant built with **Python**, **MediaPipe**, and **OpenCV**. It watches patients
through a webcam, counts every repetition from their joint angles, and coaches them with spoken feedback and live
progress bars – so patients can train safely after abdominal or chest surgery, without any personal data ever being
stored.

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
* **🩺 Live Tests at the MHH**: Recorded the exercises together with Ms. Wysinsky, including simulated patient
  movements, and tuned and validated every algorithm on these recordings.
* **🎬 3D Instruction Videos**: Replaced the old example photos with videos rendered in **Blender** – a 3D figure
  half-sitting in a hospital bed in a modelled hospital room, with movements scripted in Python and timed after the
  real recordings.
* **⚡ Faster Pipeline**: Moved pose detection to a background thread – **6.6 → 9.8 fps** (abduction) and
  **6.0 → 13.7 fps** (neck rotation).
* **🏗️ Clean Architecture**: One shared base class for all exercises, the exercise loop separated from the GUI,
  and a camera abstraction that runs on a live webcam or replays recorded videos.
* **🧪 94 Automated Tests**: Unit tests plus a system test that replays 58 real recordings and requires the
  **exact** repetition count.
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

## 🚀 Getting Started

### 📋 Prerequisites

Python 3.12, a webcam and audio output.

### 1. Install Dependencies

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Download the Voice Model

The German voice model is too large for git. Download `de_DE-thorsten-low.onnx` from
[Piper voices](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/low) into
`sportuebungen/stimmen/`.

### 3. Run the App

```bash
cd sportuebungen
python Hauptprogramm.py
```

Start it with `--entwickler` to unlock the **"Aufnahmen testen"** button, which replays recordings instead of the
webcam.

### 4. Run the Tests

```bash
cd sportuebungen
pytest
```

No camera, window or audio needed.

---

## 🕹️ User Controls

1. **Main menu** → *Übungen wählen* (choose exercises)
2. **Type of surgery** → abdominal or chest surgery, plus the post-operative day
3. **Selection** → tick the exercises and adjust the thresholds, or start all of them automatically
4. **Exercise page** → *Pause*, *Keypoints* (show the detected body points), *Zoom* and *Stop*

The user interface is in German.

---

## 📂 Project Structure

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
