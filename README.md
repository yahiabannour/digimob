# DigiMob – camera-based rehabilitation exercises

A desktop application that guides patients through rehabilitation exercises after abdominal or chest surgery.
A webcam tracks the patient's body with **MediaPipe pose estimation**, the app **counts repetitions from joint
angles** per side, shows the progress as bars next to the live video, and gives **spoken feedback in German**
(Piper text-to-speech). No patient data is stored.

<!-- Add a screenshot here: docs/screenshot.png -->

The app covers eight exercises: shoulder abduction, lateral raise, shoulder flexion, shoulder mobilisation,
biceps curls, butterfly, seated shoulder rotation and neck rotation. Before each exercise a rendered 3D demo
shows the movement.

## Background

DigiMob was started in the bachelor thesis of Tanja Wysinsky (summer 2025) at Hochschule Hannover and
maintained by Jörg Schneider and Hanno Homann. I extended it in autumn 2026. The commit
history shows who did what.

## My contributions

- **Refactored the exercise logic** – the eight exercise classes now share one base class (`uebung.py`);
  each exercise file only holds its texts and thresholds, and `uebungen.py` is the single list of exercises
- **Separated the exercise loop from the GUI** (`uebungslauf.py`) and moved pose detection to a background
  thread – frame rate went from 6.6 to 9.8 fps (abduction) and from 6.0 to 13.7 fps (neck rotation)
- **Camera abstraction** (`camera/`) – the app runs on a live webcam or replays recorded videos
- **Recording tools** (`tools/`) – record exercises with hand-entered ground truth and store the pose keypoints
  as JSONL; 58 recordings of all eight exercises are included
- **Automated tests** (94 tests with pytest)
  - unit tests for the maths, the thresholds, the camera and the threaded loop
  - a system test that replays every recording into the counters and requires the exact repetition count
- **More robust counting** – median filtering, landmark visibility checks, hysteresis and hold times against
  MediaPipe jitter
- **Adjustable thresholds** per exercise with "full" and "easy" presets for weaker patients, saved atomically
- **Data minimisation** – removed the user database and stored progress, so the app keeps no patient data
- **GUI redesign** – a progress bar per arm with countdown and target line, plus rendered 3D instruction videos
  instead of photos
- Fixed the speech output for Python 3.12 and made spoken messages cancellable

## How it works

```
camera frame ─► zoom (optional) ─► MediaPipe pose ─► exercise.update(pose)
                                                           │
            GUI: video + progress bars ◄── speech feedback ◄┘
```

Each exercise turns the pose into a measurement (for example the hip–shoulder–elbow angle) and counts a
repetition when it crosses the target and comes back. The exercises are pure Python and don't import
MediaPipe, so the tests can replay recorded keypoints without a camera.

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

The user interface and the code comments are in German.

## Tech stack

Python 3.12 · MediaPipe · OpenCV · Tkinter · Piper TTS · NumPy · pytest
