[Deutsch](README.md) | **English**

# 🏥 DigiMob – Camera-Based Rehabilitation Exercises 🤸

![Python](https://img.shields.io/badge/python-3.12-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)
![MediaPipe](https://img.shields.io/badge/mediapipe-pose-0097A7?style=for-the-badge&logo=google&logoColor=white)
![OpenCV](https://img.shields.io/badge/opencv-%235C3EE8.svg?style=for-the-badge&logo=opencv&logoColor=white)
![Blender](https://img.shields.io/badge/blender-%23F5792A.svg?style=for-the-badge&logo=blender&logoColor=white)
![pytest](https://img.shields.io/badge/tests-94%20passing-0A9EDC?style=for-the-badge&logo=pytest&logoColor=white)

A desktop app that guides patients through rehabilitation exercises after abdominal or chest surgery. A webcam
tracks the body with **MediaPipe pose estimation**, the app **counts repetitions from joint angles** for each side,
shows the progress as bars next to the live video and gives **spoken feedback** – without storing any patient data.

| 🎥 Exercise with detected body keypoints | ⚙️ Exercise selection with adjustable thresholds |
|---|---|
| ![Exercise page](docs/uebung.jpg) | ![Selection page](docs/auswahl.jpg) |

*The screenshots use the 3D demo video as the camera image, so no real person is shown.*

---

## ✨ Features

* **🏋️ 8 exercises**: shoulder abduction, lateral raise, shoulder flexion, shoulder mobilisation, biceps curls,
  butterfly, seated rotation and neck rotation
* **🎬 3D demo before every exercise**: a rendered video shows the movement
* **📊 Live progress**: one bar per arm with countdown and target line
* **🔊 Spoken feedback** with Piper text-to-speech (German)
* **🎚️ Adjustable thresholds** with "full" and "easy" presets for weaker patients
* **🔒 Privacy**: no user profiles, no stored progress, no patient data

---

## 📖 Background

DigiMob is a project by **Prof. Hanno Homann** at Hochschule Hannover (University of Applied Sciences and Arts).
**Tanja Wysinsky**, a nurse at Hannover Medical School (MHH), used the app in her bachelor thesis (summer 2025) and
gave medical advice on the exercises. She still uses the app today.

I extended the project in autumn 2026. The commit history shows who contributed what.

---

## 🚀 My Contributions

### 🧠 Counting algorithms
* **New counting algorithms for all eight exercises** – joint angles or distances, hysteresis, hold times and a
  median filter, separately for each side of the body
* **Live tests with Ms. Wysinsky (MHH)** – exercises recorded together, including simulated patient movements; I
  developed and validated the algorithms and thresholds on these recordings

### 🎬 3D instruction videos with Blender
* The app used to show only example photos – now every exercise has a **rendered video**: a 3D figure
  half-sitting in a hospital bed in a modelled hospital room
* Movements **scripted in Python**, with their timing taken from the real recordings

### 🏗️ Architecture & performance
* **Refactored the exercise logic** – one shared base class (`uebung.py`); each exercise file only holds its texts
  and thresholds, and `uebungen.py` is the single list of exercises
* **Exercise loop separated from the GUI** (`uebungslauf.py`), pose detection in a background thread –
  **6.6 → 9.8 fps** (abduction) and **6.0 → 13.7 fps** (neck rotation)
* **Camera abstraction** (`camera/`) – live webcam or replay of recorded videos

### 🧪 Testing
* **94 automated tests** with pytest – unit tests for maths, thresholds, camera and the threaded loop
* **System test** that replays 58 recordings into the counters and requires the **exact** repetition count
* **Recording tools** (`tools/`) – record exercises with a hand-entered target count, keypoints saved as JSONL

### 🔧 More
* Thresholds saved safely against power loss
* Removed the user database and stored progress (**data minimisation**)
* Fixed the speech output for Python 3.12 and made spoken messages cancellable

---

## ⚙️ How It Works

```
camera frame ─► zoom (optional) ─► MediaPipe pose ─► uebung.update(pose)
                                                           │
           GUI: video + progress bars ◄── speech feedback ◄┘
```

Each exercise turns the pose into a measurement (for example the hip–shoulder–elbow angle) and counts a repetition
when the target is reached and the movement returns. The exercises are pure Python without a MediaPipe import, so
the tests can replay recorded keypoints without a camera.

---

## 🛠️ Getting Started

**Requirements:** Python 3.12, a webcam and audio output.

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

The German voice model is too large for git. Download `de_DE-thorsten-low.onnx` from
[Piper voices](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/low) into
`sportuebungen/stimmen/`.

```bash
cd sportuebungen
python Hauptprogramm.py              # add --entwickler to replay recordings instead of the webcam
pytest                               # run the tests (no camera needed)
```

The user interface is in German.

---

## 🧰 Tech Stack

Python 3.12 · MediaPipe · OpenCV · Tkinter · Piper TTS · NumPy · pytest · Blender
