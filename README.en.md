[Deutsch](README.md) | **English**

# DigiMob – camera-based rehabilitation exercises

A desktop application that guides patients through rehabilitation exercises after abdominal or chest surgery.
A webcam tracks the patient's body with **MediaPipe pose estimation**. The app **counts repetitions from joint
angles**, separately for each side, shows the progress as bars next to the live video, and gives **spoken
feedback** (Piper text-to-speech). No patient data is stored.

| Exercise with detected body keypoints | Exercise selection with adjustable thresholds |
|---|---|
| ![Exercise page](docs/uebung.jpg) | ![Selection page](docs/auswahl.jpg) |

*The screenshots use the 3D demo video as the camera image, so no real person is shown.*

The app covers eight exercises: shoulder abduction, lateral raise, shoulder flexion, shoulder mobilisation,
biceps curls, butterfly, seated rotation and neck rotation. Before each exercise a rendered 3D video shows the
movement. The user interface is in German.

## Background

DigiMob is a project by Prof. Hanno Homann at Hochschule Hannover (University of Applied Sciences and Arts).
Tanja Wysinsky, a nurse at Hannover Medical School (MHH), used the app in her bachelor thesis (summer 2025) and gave
medical advice on the exercises. She still uses the app today.

I extended the project in autumn 2026. The commit history shows who contributed what.

## My contributions

- **New counting algorithms for all eight exercises** – measurement via joint angles or distances, counting with
  hysteresis, hold times and a median filter, separately for each side of the body
- **Live tests with Ms. Wysinsky (MHH)** – exercises recorded together, including simulated patient movements; I
  developed and validated the algorithms and thresholds on these recordings, and the system test replays them on
  every run
- **3D instruction videos with Blender** – the app used to show only example photos. Now every exercise has a
  rendered video: a 3D figure half-sitting in a hospital bed in a modelled hospital room. The movements are
  scripted in Python, and their timing comes from the real recordings
- **Refactored the exercise logic** – the eight exercise classes share one base class (`uebung.py`); each exercise
  file only holds its texts and thresholds, and `uebungen.py` is the single list of exercises
- **Separated the exercise loop from the GUI** (`uebungslauf.py`) and moved pose detection to a background
  thread – the frame rate went from 6.6 to 9.8 fps (abduction) and from 6.0 to 13.7 fps (neck rotation)
- **Camera abstraction** (`camera/`) – the app runs on a live webcam or replays recorded videos
- **Recording tools** (`tools/`) – record exercises with a hand-entered target count and save the body keypoints
  as JSONL; 58 recordings of all eight exercises are included
- **Automated tests** (94 tests with pytest)
  - unit tests for the maths, the thresholds, the camera and the threaded loop
  - a system test that replays every recording into the counters and requires the exact repetition count
- **Adjustable thresholds** per exercise with "full" and "easy" presets for weaker patients, saved safely
  against power loss
- **Data minimisation** – removed the user database and stored progress; the app keeps no patient data
- **New interface** – a progress bar per arm with countdown and target line
- Fixed the speech output for Python 3.12 and made spoken messages cancellable

## How it works

```
camera frame ─► zoom (optional) ─► MediaPipe pose ─► uebung.update(pose)
                                                           │
           GUI: video + progress bars ◄── speech feedback ◄┘
```

Each exercise turns the pose into a measurement (for example the hip–shoulder–elbow angle) and counts a
repetition when the target is reached and the movement returns. The exercises are pure Python without a MediaPipe
import, so the tests can replay recorded keypoints without a camera.

## Running it

Requirements: Python 3.12, a webcam and audio output.

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

## Tech stack

Python 3.12 · MediaPipe · OpenCV · Tkinter · Piper TTS · NumPy · pytest · Blender
