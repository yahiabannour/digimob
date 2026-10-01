# Changelog (Projekt Reha-Sportübungen)

---

### Use:
- __Added__      for new features.
- __Changed__     for changes in existing functionality.
- __Deprecated__  for soon-to-be removed features.
- __Removed__     for now removed features.
- __Fixed__       for any bug fixes.
- __Security__    in case of vulnerabilities.

---

## [1.1.9] 2026-09-01

### Added
- "Aufnahmen testen" (Hauptmenü, row 12): plays every recording listed in the new
  `videomodus.txt` one after another instead of the webcam, each with its own exercise, and
  only counts. "Übungen wählen" stays live; the app always starts live, so the file can't
  switch the kiosk to video.
  - `videomodus.txt`: one recording per line (relative to sportuebungen/), `#` skips a line.
    The exercise comes from the `summary.json` next to the video (`exercise`), otherwise
    from the folder `recordings/<uebung>/rec<N>/`, so only tools/record_exercise.py output
    works. Missing files and unknown exercises are listed in an error box before anything
    starts.
  - counting only: each exercise is re-initialized with `repetitions=None` (see Changed) and
    its intro (example image + announcement) is skipped, so counting starts at the first
    video frame. No target from the post-op day, no end at a target, no TTS.
  - a video that can't be opened is noted in the result and skipped. At the end a message
    box (also printed to the terminal and logged) lists per recording the counted reps,
    per side where the exercise has sides, and for comparison `expected_reps` from its
    `summary.json` with "OK"/"ABWEICHUNG". Then back to the main menu and the live camera.
    "Stop" aborts the run the same way (without a result).
  - window title during the run: "Reha-Sportübungen - Aufnahmen-Test <n>/<total>: <path>".
  - the video plays in real time like a camera: `getFrame()` waits for the next frame when
    called early and drops missed frames when called late. It only advances while the app
    reads frames, so a gap > 0.5 s (pause button) pauses it instead of skipping that part.
  - frames are scaled into 640x480 keeping the aspect ratio (black borders), so a 16:9
    video gives the same normalized landmark geometry as the 4:3 camera the thresholds
    were tuned on.
  - camera/file_camera.py (`FileCamera`): now reads video files as well as image folders.
    New `frame_width`/`frame_height`, `rewind()`, `close()`, `is_video`; `getFrame()`
    returns None after the last frame (it raised `IndexError` before). The constructor
    argument `folder` is now `path`.
  - tested headless on `recordings/bizepscurls/rec1` (ground truth 1 rep) through
    `FileCamera` -> `Bildoperationen` -> `MediapipeModelle` -> Bizepscurls: 1 rep with zoom
    off and on. Drove the real Tk app through a run of rec1 + rec2, a Stop mid-run, a broken
    video and the config errors; the live flow afterwards has its post-op-day target, intro
    and TTS back. rec1 counts 1 (OK). rec2 counts 0 against 1: it is 2.8 s long and curls
    before "down" (arm hanging, forearm visible) has been held for `confirm_time` -- the
    first confirmed "down" comes after the curl -- so the Bizepscurls rules correctly
    reject it. Recordings need ~1-2 s in the start position before the first rep.
- `systest.py`: pytest system test of the rep counters on the recorded keypoints. Run it from
  sportuebungen/ with `pytest`. It is the automated counterpart to "Aufnahmen testen": same
  recordings, same ground truth, but without the button, the window and the result list.
  - `systest.txt`: one `keypoints_<modell>.jsonl` per line (relative to sportuebungen/), `#`
    skips a line. The exercise and `expected_reps` come from the `summary.json` next to it, so
    only tools/record_exercise.py output works. Independent of `videomodus.txt`.
  - instead of camera, video and pose detection, one stand-in class feeds the recorded
    keypoints frame by frame into `whole_body_recognition()` -- the test sets one frame per
    tick, every exercise reads exactly one per tick. It normalizes with the app's own
    `normalize_pose_landmarks()` (called unbound, so no `PoseTask` and no pose model is
    loaded), so the exercises' angle thresholds see in the test exactly what they see live.
  - each exercise is set up as in `start_test_video()`: `repetitions=None` (count only),
    `sport_exercise_example = 1` and `counter_started = True`, counting from the first frame.
    The count must equal `expected_reps` exactly, otherwise the test fails.
  - the exercise's `time.time()` returns the timestamp of the current frame during the test.
    Needed for the Bizepscurls hold time (`confirm_time`): with the real clock a replay faster
    than real time counts 0 instead of 1. This keeps the result independent of the machine.
  - the speech output is replaced by an empty `SprachAusgabe` before the exercises are
    imported; sprachausgabe.py would otherwise load the Piper voice at import time. In count-only
    mode no exercise ever calls it.
  - a systest.txt without any entry aborts collection, so an empty manifest cannot pass silently
    (pytest would only skip the test). A missing file fails just that recording's test.
  - checked on `recordings/bizepscurls/rec1` and `recordings/halswirbelsaeule/rec1` (1 rep
    each): 2 tests in ~1 s, without camera, window, sound and pose model, and with the same
    result as the same recordings through the real video pipeline (`FileCamera` ->
    `Bildoperationen` -> `MediapipeModelle`), which needs ~13 s in real time for them. The
    videos are git-ignored, the keypoints are not.
  - `pytest.ini` (`testpaths`) so that a bare `pytest` collects `systest.py`;
    `pytest==9.1.1` added to requirements.txt.
  - `python systest.py` runs the tests too (via `pytest.main()`). Without that, running the
    file directly only imports it and exits silently with code 0, which looks like a pass.
  - each recording's own line carries `<counted>/<expected_reps>` in front of PASSED/FAILED,
    so both numbers are visible whether it passes or fails. Written without a line break:
    `write_line()` cuts pytest's own line, which it then repeats together with the test name.
    `-v` in pytest.ini for that: among the progress dots of the short format the numbers would
    run into each other.
  - `--tb=no` in pytest.ini: a deviation is fully described by that line and by the summary
    below it (`assert <counted> == <expected>`), the source of the test function adds nothing.
    `pytest --tb=short` brings the traceback back when an exercise actually raises.
- Pose model comparison on the recordings: the video of a recording is the master data, and
  keypoints for any pose model can be derived from it later, so no new takes are needed to
  compare lite/full/heavy.
  - `tools/extract_keypoints.py <lite|full|heavy> [rec dirs]` (default: all recordings) runs
    the model over every frame of `video.mp4` (`cv2.VideoCapture`, not the real-time
    `FileCamera`) with the timestamps of the recording's own keypoints file, and writes
    `keypoints_<modell>.jsonl` next to it. `summary.json` stays unchanged. The file written
    during the recording (`summary["keypoints"]`) is never overwritten, other existing ones
    only with `--force`. `--out DIR` writes to `DIR/<uebung>/rec<N>/` instead. It prints ms
    per frame. Same timestamps give identical output: a second run is byte-identical.
  - `tools/compare_models.py [uebung | rec dirs]` replays every
    `keypoints_*.jsonl` of a recording into its exercise as systest does, and prints counted
    vs. expected (per side), share of frames with a pose, mean visibility of
    shoulders/elbows/wrists/hips, jitter (mean second difference of the main points in px)
    and the mean px deviation from the recording's own file (`*`), plus a summary per model.
  - `mediapipe_modelle.py`: `POSE_MODELS` (lite/full/heavy; `POSE_MODEL` stays full),
    `MediapipeModelle(model_path=POSE_MODEL)`, `detect(image_rgb, timestamp_ms=None)`
    (explicit timestamp, still strictly increasing), and the download link in the
    missing-model error is built from the model name. lite and heavy are git-ignored and only
    used by the tools. The app calls everything as before.
  - first result on the 5 recordings (all counted with the offline files): lite 5/5, heavy
    5/5, full 4/5, and full from the video counts 1/0 on bizepscurls/rec2 (the resting left
    arm counted once). Per frame: lite ~20 ms, full ~24 ms, heavy ~74 ms. Full from the video
    deviates 4-10 px on average from the live `keypoints_full.jsonl`: the live file has
    uncompressed frames and the recorder's processing-time timestamps, the offline one the
    mp4v frames and the capture timestamps. compare_models and view_models still compare
    against the live full file, i.e. what the app actually saw, so part of a lite/heavy vs.
    full difference comes from that input and not from the model. (A `--also DIR` option for
    offline extractions was added and removed again within this version.)
  - `tools/view_models.py <rec dir | uebung/rec<N>>`: matplotlib window that shows the models
    of one recording instead of numbers. Each model's skeleton in its own colour (full blue,
    lite green, heavy orange) over the video frame, mirrored like the app, with each model's
    running count in the legend. Below it, a timeline with one row per model marking where it
    counted a rep (▲ left, ▼ right, ● one counter), counted as in systest. Slider, space =
    play/pause, arrow keys = ±1/±10 frames, `v` = video on/off, a click in the timeline jumps
    there, and checkboxes hide models. The video is loaded at half size (`read_frames(path,
    scale)` in extract_keypoints.py). It shows the files in the recording folder only (full
    from the recording, lite, heavy), not `--out` extractions. `POSE_CONNECTIONS` (the 35
    skeleton lines) is now in `mediapipe_modelle.py`, and `matplotlib==3.11.1` is in
    requirements.txt (only this tool uses it).
- (3D world landmarks, `"pose_world_landmarks"` in the keypoints, were added for avatar demos
  and removed again within this version: the demos will use the normal x, y, z. The key was
  stripped from all keypoints files in `recordings/`; everything else in them is unchanged, and
  compare_models/systest give the same results.)
- Ground truth per side: after a take of an exercise with two sides, `tools/record_exercise.py`
  takes `5` (both sides) or `5 0` (left right, patient's point of view). The latter goes into
  `summary.json` as `expected_per_side: {"left": 5, "right": 0}`, and `expected_reps` is the
  smaller number as before, so "Aufnahmen testen" is unchanged. `systest.py` then also asserts
  each side.
  - `tools/recording_format.py` now holds the shared reader `read_poses(path, resolution)`
    (yields `(t, Pose | None)`, used by systest and compare_models), `Landmark`,
    `write_keypoints()` and `model_name()`.
- 3D demos: during the intro each exercise now shows a looping 3D demo
  (`demos/<uebung>.mp4`) instead of the example photo, while the spoken intro plays.
  - `tools/blender_demos.py` generates them with Blender 5.2 (runs in Blender's Python:
    `blender -b -P tools/blender_demos.py [-- uebung ...]`, ~4.3 s per frame on the Intel
    iGPU, ~3 h for all 8). A trainer from Mixamo (character "Ch37", pink zip top, black
    leggings, sneakers) with her own textures, under soft area lights (AgX), in a simple
    therapy room of flat-coloured shapes (plank floor, window, wall bars, plant, gym ball,
    blue mat). The room is provisional, a better background is still to be chosen. The
    character file is not downloadable by script: it lives git-ignored in
    `demos/assets/mixamo/Ch37_nonPBR.fbx` (mixamo.com, FBX Binary, With Skin, T-Pose).
    - The motion is keyframed per exercise (`EXERCISES`, joint angles in degrees on a rig of
      empties), not taken from the recordings: most takes show no legs, and
      bizepscurls/halswirbelsaeule no hips. Each Mixamo bone copies the world rotation of
      its joint, so the angles don't depend on the skeleton. Every demo starts and ends in
      the same pose and loops seamlessly, and the angles go clearly past the counters'
      thresholds (e.g. Abduktion 188° against 150°: the arms go past vertical, the hands end
      close together above the head).
    - The collarbones follow the arm (`girdle()`: lift above 45° elevation, forward/back
      with the arm) and a Corrective Smooth modifier relaxes the skin: without both, a
      raised arm pushed the shoulder into the back.
    - A `twist` joint turns the upper arm about its own axis before the shoulder lifts it
      (the forearm turns back, so the grip stays). Flexion uses -120° at the top: without it
      the upper arm ended 177° away from the T-pose the skin was made for (Abduktion: 103°)
      and the shoulder looked detached from the body. No counter angle changes.
    - Abduktion: on the way up the arm turns outward (`TURN_OUT`, split 45° upper arm, 90°
      forearm, 45° hand so the shoulder skin doesn't twist): the palms face up at shoulder
      height (a key halfway, `timeline(via=...)`) and face each other above the head.
    - The FBX import makes her materials 50 % metallic and reads a gloss map as roughness,
      so face and jacket shone; `matte()` sets metallic 0, specular 0.3, roughness 0.7.
    - She is made a little fuller and her pink top dark (the user asked; Mixamo has no
      fuller adult woman, Sketchfab only stylised ones). `fuller()` stretches the T-pose
      meshes away from their axis before the rig is built: belly, waist, hips and a little
      back from a vertical axis through the hips, thighs and upper arms from their bones
      (`FULLER`, proportional, so jacket, zipper and leggings keep their layer order; her
      skin is only head, neck and hands). `dark_jacket()` gives the top its own copy of the
      shared material with a Hue/Saturation node (`JACKET`).
    - Dumbbells sit in the fists for Bizepscurls/Butterfly, a stick in an overhand grip for
      Flexion. The stick is not attached to one hand (it tilted with that wrist at the top)
      but held by constraints: centred between two grip empties in the fists, pointing from
      one to the other, so it stays level.
    - The camera distance is fitted per exercise (`fit_camera()`): the whole figure, hands,
      feet and props stay in frame in every frame, also for Halswirbelsäule (no head
      close-up any more). Mobilisation is filmed 35° from her left.
    - 1200x900 (the size the GUI shows, so nothing is scaled up; 640x480 looked blurry in
      the app), 30 fps, H.264 at quality HIGH (MEDIUM smeared face and edges), 9-12 s each.
      Also writes `demos/demos.blend` for manual changes
      (git-ignored). Built and checked frame by frame in the running Blender via the
      Blender MCP.
    - Tried and dropped within this version: a mannequin of primitives (its script and
      videos are kept git-ignored in `demos/assets/mannequin/`), a realistic MakeHuman/MPFB
      woman in a Poly Haven room, a scanned Renderpeople woman on a photo backplate, and the
      Mixamo trainer in a toon/outline game look.
  - `Uebung.demo` (class attribute, next to `image`) names the video. `Uebungslauf` opens it
    with `FileCamera(mirrored=True, loop)` at `DEMO_SIZE` (1200x900, the GUI's video size;
    the camera stays 640x480), so it plays in real time, is mirrored like the
    camera image (the patient's left on screen-left) and stands still during Pause. It is
    closed when the intro ends, on `next()` and on `stop()`. A missing or broken video logs a
    warning and shows the photo as before. `Bildoperationen.demo_frame()` converts the frame
    to BGR. "Aufnahmen testen" has no intro, so it shows no demo.

### Fixed
- camera/file_camera.py: pausing "Aufnahmen testen" still advanced the video by
  `MAX_FRAME_GAP` (0.5 s): after the pause the first read dropped ~15 frames (30 fps), which
  could cut into a rep. The capped gap was still added to the video time; now a gap over
  `MAX_FRAME_GAP` adds nothing, and the video continues with the next frame. Checked with a
  simulated clock: 1 frame read after a 3 s pause (before: 15).
- bizepscurls.py: curling only the right arm also filled the left arm's bar and sometimes
  counted a left rep. With the hands hanging below the image edge, MediaPipe guesses the
  hidden wrist; when the other hand comes up it often puts it in front of the chest, and the
  resting arm's 2D elbow angle drops below 110° ("up"). Mostly the left arm (27 false bent
  streaks in rec2 vs. 1 in rec3). The streaks lasted up to 0.20 s in the recorded keypoints
  and 0.30 s in a fresh run over the same video (MediaPipe's tracking depends on real
  time), so the 0.25 s hold time only sometimes stopped them. Wrist visibility doesn't separate
  them (the guessed wrists reached 0.64-0.88).
  - new `max_wrist_shoulder = 0.75`: "up" needs the wrist within 0.75 upper-arm lengths of
    that arm's shoulder (`reach`, measured on `pose.xy` like `forearm`). In a real curl the
    hand reaches the shoulder; the guessed wrists sat 0.57-1.36 away (95 % above 0.67).
    `guessed(side)` = elbow bent but wrist not at the shoulder: no "up" candidate and an
    empty bar for that arm (`fill()` returns None), so bar and counter agree.
  - new recordings: `recordings/bizepscurls/rec2` (right arm only, 5 curls) and `rec3` (left
    arm only, 5 curls), both `expected_reps: 0`, added to `systest.txt`; rec2 replaces the
    old two-arm take. `pytest` 4/4. rec1 counts 1/1 per side, rec2 0/5, rec3 5/0; the still arm's bar
    is full in 6/448 (rec2, was 56) and 0/454 (rec3, was 5) frames, single frames only.
    A synthetic right-only curl (rec1 with the right image half frozen at rest) counted
    left 1 / right 1 before and 0 / 1 now.
  - margin: all real curls pass at 0.7 and 0.8; rec1's right rep (forearm towards the camera)
    needs >= 0.7, most others pass at 0.3. Framing the camera so the hanging hands are in the
    image removes the guessing itself.
- halswirbelsaeule.py: crashed with `AttributeError` when no camera frame came in
  (`results.pose_landmarks` on None). It now has the same "Kamera konnte kein Bild
  erfassen" guard as the other seven exercises.
- no camera frame (camera failure, end of video) crashed the whole chain:
  `whole_body_recognition()` passed None into the pose detection, and `update_frame()`
  unpacked the exercises' None return. Now `whole_body_recognition()` returns
  `(None, None, [])` and `update_frame()` retries on the next tick.
- sprachausgabe.py: TTS was completely silent (every `speak()` raised
  `wave.Error: # channels not specified`). Rewrote `speak()` for the piper-tts 1.7.0
  API: iterate the `AudioChunk`s from `voice.synthesize(text)`, concatenate their
  `audio_int16_array`, play at `sample_rate` via sounddevice. Dropped the unused
  `wave` / `io.BytesIO` imports.
- progress bar disagreed with the rep counter (already in the old 3-step bar, carried
  over into the continuous one). Rep counting itself is unchanged.
  - rotation.py: bar filled with the arms raised sideways, but the counter only counts
    while both shoulder angles are < 75°. `balken()` now uses the same condition.
  - butterfly.py: counter accepts "wrist above elbow OR shoulder angle > 70°" per side,
    the bar required wrist above elbow on both sides, so reps counted with an empty bar.
    `balken()` now uses the counter's condition.
  - bizepscurls.py, butterfly.py: `balken()` raised `AttributeError` (printed every
    45 ms as "Fehler bei der Balkenaktualisierung") until the first detection; the other
    pose exercises had the same gap for ~1 tick. Every value `balken()` reads now gets a
    starting value in `__init__`.
  - bizepscurls.py, butterfly.py, rotation.py, halswirbelsaeule.py: bar froze at its last
    fill when detection was lost, and after `reset_exercises()` could show the previous
    session's values (Bizepscurls: full bar with nobody in frame). These values are now
    reset every frame without a detection, like `angle_shoulder_*` already were. Elbow
    angles reset to 180° (straight arm) -- 0° means fully bent and would fill the bar.
- bizepscurls.py: false reps while holding the arms up, especially when alternating
  arms -- the stage flickered between "up" and "down" and every flicker counted.
  - cause: the elbow angle is measured in 2D (x/y). The patient faces the camera, so a
    curl moves the forearm toward the lens. Near a horizontal forearm it appears only a
    few pixels long, and the 2D angle goes from "down" (> 150°) to "up" (< 110°) within
    ~12° of real movement. A tired arm sinking from the curled hold sits right there, so
    tremor and landmark jitter crossed both thresholds. It also let "down" trigger with
    the arm only ~75° bent. On top, a single frame past a threshold changed the stage.
  - fix: "down" additionally needs the forearm to appear at least half as long as the
    upper arm in the image (`min_forearm_ratio = 0.5`, ~0.8 when hanging, ~0 when
    pointing at the camera). Every stage change must also hold for `confirm_time = 0.25`
    s (`detect_stage()`). The pending hold is dropped when detection is lost or the
    shoulder condition fails (`reset_pending_stages()`). `min_angle`/`max_angle`, the
    shoulder condition and `balken()` are unchanged.
  - behaviour change: a rep now needs a visibly straightened arm (real elbow angle
    ~125-130° or more); partial reps that stop around 90-120° no longer count. Lower
    `min_forearm_ratio` if that is too strict for patients.
  - simulated with noisy 2D landmarks (8 seeds): one curl, then a 20 s hold sinking to
    horizontal -- old counted 6-14 reps per arm, new 1; alternating with the other arm
    held up (6 real per arm) -- old 9-14, new 6; slow and fast (0.9 s) full reps counted
    correctly by both. Tested on camera: both hands at once and alternating now count
    correctly.

### Changed
- bizepscurls.py: simpler counting logic, and `bizepscurls/rec5` now passes (3/3; before 0/3).
  Counting is now the standard two-threshold state machine (hysteresis) that Ultralytics AIGym
  and the MediaPipe repetition counter use: elbow > `max_angle` (150°) = "down", < `min_angle`
  (110°) = "up", a rep on "up" after "down". The shoulder gate (`max_shoulder` 50°) and the
  `Haltezeit` of `confirm_time` 0.25 s per stage stay. Removed: `min_forearm_ratio`
  (forearm/upper-arm length for "down"), `max_wrist_shoulder` with `reach`/`guessed()` (wrist
  near the shoulder for "up"), and the `calculate_distance` import.
  - why: all 5 bizepscurls takes were replayed through variants of the logic. Plain angle
    thresholds 3/5 (flicker: rec2 counted 23 left reps), + gate 3/5, + gate + hold **5/5**,
    + forearm + reach (the old code) 4/5 (the reach check swallowed rec5's left reps), 3D angle
    with z at most 3/5 (MediaPipe documents z as not reliable), EMA smoothing instead of the
    hold time at most 5/5, but only at alpha 0.07, which undercounts the moving arm (rec2 right 1
    instead of 5). The hold time alone filters the flicker the two removed checks were added for.
  - margins: every threshold pair from 140-160° / 90-120° passes 5/5; the hold time only
    passes from 0.20 to 0.25 s (0.15 s lets rec2 flicker through, 0.30 s misses a quick rep
    in rec5). The gate is not needed by the recordings but kept for the posture.
  - trade-off: `fill()` no longer empties the bar of an arm whose wrist MediaPipe guesses
    outside the image, so in one-arm takes the resting arm's bar may flicker; its count stays 0.
- systest.py: exercises with two sides (all but Butterfly and Rotation) now also show each
  side's count after `<gezaehlt>/<erwartet>`, e.g. `0/0 (links 0, rechts 5)` for a right-arm-only
  Bizepscurls take. Only the display changed; the test still asserts `total`.
- progress bar per side instead of one bar: the old single bar hid alternating movement
  (Abduktion, Seitheben and Flexion filled it from the lower arm, so it stayed near empty while
  a patient alternated; Mobilisation used the higher arm; Halswirbelsäule showed `abs()`
  without the direction). Counting is unchanged.
  - uebung.py: hook `fill(side)` instead of `fill()`; `progress()` returns
    `{side: 0..1 or None}` for every entry of `sides`, all None when the frame is invalid. New
    class attribute `bar_style`: `"split"` (default), `"center"` or `"single"`.
  - abduktion.py, seitenheben.py, flexion_schulter.py, mobilisation_schulter.py: each arm's bar
    from its own shoulder angle; bizepscurls.py from its own elbow angle (`"split"`).
  - butterfly.py, rotation.py: `bar_style = "single"`, one shared movement, `fill(side)`
    ignores the side.
  - halswirbelsaeule.py: `bar_style = "center"`; left = turn to the left, right = turn to the
    right, each 15°..35°.
  - uebungslauf.py: `show(image, balken, bar_style)`, also during the intro (empty bars in the
    next exercise's layout).
  - Hauptprogramm.py: `update_bar(balken, bar_style)` draws "split" as two vertical bars left
    and right of the video (patient's left = screen-left, the display is mirrored), "center" as
    a horizontal bar under the video growing from the middle, "single" as the old bar. Same
    yellow -> green colour. The split bars are narrower (130 px) and the center bar flat (34 px)
    so each layout fits a 1920x1002 window: at the old bar sizes the right split bar ran off
    screen and the center bar pushed "Stop" below the window.
  - tested: `pytest` 2/2; both recordings replayed through all 8 exercises with `progress()`
    every frame (values 0..1 or None, keys = `sides`, Halswirbelsäule never both halves at
    once); the exercise page drawn in all three layouts at 1920x1080 and checked on screenshots.
- Hauptmenü: moved "Übungen wählen" (grid row 2 -> 7) and "Bedienungsanleitung"
  (row 3 -> 8) further down the page
- moved the Thorsten voice model into a subfolder: `stimmen/de_DE-thorsten-low.onnx`
  (+ `.onnx.json`); updated the paths in sprachausgabe.py and Hauptprogramm.py
- exercises now start without selecting a user: a fixed default user "Gast" is
  created at startup and used for all progress saving; removed the "Bitte zuerst
  einen Nutzer auswaehlen" gates in `start_exercise_menu()` and
  `_bind_user_to_all_exercises()`
- requirements.txt: pinned every dependency to the known-working version
  (`piper-tts==1.7.0`, `opencv-python==5.0.0.93`, `Pillow==12.3.0`,
  `sounddevice==0.5.6`, `mediapipe==1.0.1`, `piper==0.15.1`) and added an explicit
  `numpy==2.5.2`. `mediapipe` must stay `>=1.0` -- the `mediapipe_tasks_compat.py`
  shim reads symbols that only exist under `mp.tasks.vision` on the 1.x line.
- mediapipe_tasks_compat.py: pose model switched from `pose_landmarker_lite.task` to
  `pose_landmarker_full.task` (added under `models/`). The Tasks-API migration had
  silently downgraded to lite; the old `mp.solutions.pose.Pose` ran the full model
  (`model_complexity=1`), which the exercise angle thresholds were tuned on. Costs
  ~6-7 ms more per pose inference on the dev machine (~20 -> ~26 ms).
- bildoperation_opencv.py: `video_frame()` no longer runs its own zoom-centering
  pose/face detection while zoom is off (`zoom_factor <= 1`, the default). The crop
  center was computed and then discarded by `apply_zoom()`, so every frame ran pose
  detection twice. Returns the unchanged frame and resets the EMA smoothing, so turning
  zoom on starts at the detected position. Body exercises with zoom off: ~61 -> ~29 ms
  per frame on the dev machine. Zoom on is unchanged (still two detections per frame).
- progress bar is now continuous instead of three fixed steps (red 1/3, yellow 2/3,
  green full): `balken()` in all 8 exercise classes returns a 0..1 fill computed with the
  new `calculate_progress()` (calculate_angle_distance.py) between the exercise's own
  start and target thresholds -- empty in the start position, full once the rep's target
  is reached. `update_bar()` in Hauptprogramm.py draws that height and shades the colour
  from yellow to green; red is gone. The zones match the old steps (old red = empty, old
  green = full), so rep counting is unchanged.
- halswirbelsaeule.py: head rotation is now a 3D angle from the pose landmarks instead of
  a pixel offset from the face landmarks. The old value (nose x minus eye-midpoint x, in
  px, thresholds 5/12 px) changed with the distance to the camera and the zoom.
  - new `calculate_head_rotation()` on pose landmarks 0-6: vector 1 between the eye
    centers (mean of 1-3 and 4-6), vector 2 perpendicular to it through the nose (0),
    vector 3 = their cross product (the direction the face points). Coordinates in px, z
    scaled by the image width like x.
  - the angle between vectors 2 and 3 is always 90 deg (vector 3 is normal to vector 2),
    so the measured angle is vector 3 vs. the camera axis (-z) in the horizontal (x-z)
    plane, signed: > 0 left, < 0 right.
  - thresholds in degrees, same for both sides: `min_angle = 15` (bar empty),
    `max_angle = 35` (turn counted, bar full); replace the four
    `*_horizontal_inclination_*` values. Counting logic (alternating sides,
    total = min(left, right)) is unchanged.
  - behaviour change: "links"/"rechts" and the per-side counters are now from the
    patient's point of view, like the other exercises. The old code labelled a turn to
    the patient's left "rechts" (checked in videos/VIDEO_DIGIMOB_1.mp4).
  - tested on the Halswirbelsaeule part of videos/VIDEO_DIGIMOB_1.mp4 (bedridden
    patient, 5 turns per side). Against the Face Landmarker's head-pose matrix as
    reference: correlation 0.97, the pose angle reads ~1.2x the reference. Turn peaks were
    56-79 deg, and resting noise stayed below ~35 deg. The class ran on the recorded
    landmarks and counted 5 left + 5 right with no extra counts. Count thresholds of 30,
    35 and 40 deg all gave that result. Thresholds are still to be confirmed on camera
    with more patients.
  - tools/recording_format.py: Halswirbelsaeule added to `EXERCISES` -- it now counts
    from the pose landmarks that the recorder stores.
- Hauptprogramm.py opens the camera itself (`open_live_camera()`) through the `Camera` class
  (camera/camera.py) instead of a raw `cv2.VideoCapture` (suggestion HH). `self.cap` is now
  `self.cam`: the live `Camera` (`self.live_cam`), or during "Aufnahmen testen" the current
  `FileCamera`; `set_camera()` switches it for `Bildoperationen` too. Both deliver RGB frames.
  Still webcam 0 at 640x480, unmirrored.
- all 8 exercise classes: `repetitions=None` means "count only" (used by "Aufnahmen
  testen"). The overlay then shows the counted reps instead of the remaining ones,
  `update_counters()` sets the message "Aufnahmen-Test: nur zaehlen" and returns before the
  target check and the TTS milestones, so the exercise never completes on its own. With a
  number nothing changes. butterfly.py, rotation.py: `total_repetitions = self.counter` moved
  above the target check (same value as before).
  - bildoperation_opencv.py: `Bildoperationen(resolution, cam)` takes the camera object
    instead of the `VideoCapture`. `video_frame()` gets RGB from `cam.getFrame()` and
    converts once to BGR for display/drawing (before: BGR from `cap.read()`, converted to
    RGB for MediaPipe).
  - behaviour change: the frame rate is no longer forced to 30 fps (`Camera` does not set
    `CAP_PROP_FPS`; the webcam default at 640x480 is normally 30 fps). `Camera` reads frames
    in a background thread and always returns the newest one.
  - a camera that can't be opened at startup no longer goes unnoticed: an error box is
    shown, and starting an exercise shows it again instead of a blank screen. If the live
    camera stops delivering frames, processing stops with the last frame visible and an
    error box; "Stop" returns to the menu.
  - camera/camera.py: `getFrame()` returns None once the reader thread has stopped
    (camera error) instead of blocking forever -- in the app that would have frozen the
    whole Tk UI. tools/record_exercise.py stops on None.
- restructured the code, behaviour unchanged: shared parts exist once, the exercises only
  do arithmetic, and one flat loop pushes each frame through independent steps
  (architecture.html in the repo root explains every file and the data flow).
  - new `uebung.py`: base class `Uebung` with everything the 8 exercise classes had copied
    (count-only mode, completion, messages, remaining/counted display, bar gate). An
    exercise now only fills `measure(pose)`, `count(t)`, `fill()` and optionally `ready()`
    (gate) and `lost()`. Bar and counter share one `valid` flag, so they can't disagree.
    `Haltezeit` is the Bizepscurls hold time (`confirm_time`) as a small class.
  - the 8 exercise files shrink from 1,486 to ~450 lines; classes renamed without the
    `Exercise` suffix (`Abduktion`, `FlexionSchulter` -- fixes the typo
    `Flexion_SchulterbenExercise` --, ...). Each has `title` (menu) and `name` (video
    overlay, ASCII because `cv2.putText()` can't draw umlauts), `image`, `intro`, thresholds.
    Butterfly keeps its arm position check inside `count()`/`fill()` instead of `ready()`, so
    its message and milestones still start at the first detected pose.
  - new `uebungen.py`: the only list of exercises (`UEBUNGEN`), the exercises per OP type
    (`OP_ARTEN`) and day -> reps (`TAG_WIEDERHOLUNGEN`). Replaces 7 lists in
    Hauptprogramm.py and `EXERCISES` in tools/recording_format.py (the app no longer imports
    its exercise list from tools/).
  - new `uebungslauf.py`: `Uebungslauf.tick()`, the one loop: frame -> zoom -> pose ->
    `uebung.update(pose, t)` -> milestones -> overlay + bar. Owns the intro (10 s), done (5 s)
    and next-exercise phases that each class used to track with 6 flags. Replaces
    `update_frame()` and the separate 45 ms `periodic_update()` bar loop; the bar now
    redraws with each frame.
  - new `aufnahmen_test.py`: "Aufnahmen testen" moved out of Hauptprogramm.py. It runs each
    recording through the same `Uebungslauf` with `target=None` and `intro=False` instead of
    re-running `__init__` and setting `sport_exercise_example`/`counter_started` from outside.
  - Hauptprogramm.py (811 -> 285 lines) only builds the GUI: `new_page()` and `button()`
    helpers, one `configure_auswahl_frame()` for both OP types (was two copies, plus two
    save/start method pairs), `start_exercises()` creates fresh exercise instances per run
    (replaces `update_reps()`/`reset_exercises()`). The Brust-OP page now reads
    "Übungen wählen:" like the Bauch-OP page (was "Übungen auswählen:").
  - mediapipe_modelle.py: `MediapipeModelle.detect(image_rgb)` returns a `Pose` (raw
    landmarks, image size, normalized points, `xy(i)`) or None; it no longer fetches the
    frame. Landmark numbers per side as `SCHULTER`/`ELLBOGEN`/`HANDGELENK`/`HUEFTE`.
    `normalize_pose_landmarks(landmarks)` is a plain function (same math).
  - bildoperation_opencv.py: reads no camera any more. `zoom(frame_rgb)` (was
    `video_frame()`), `overlay(image, pose, uebung)` (was `show_exercise_details()`, now
    also draws the keypoints), `load(path)` for the example images. The pose is still
    detected on the zoomed image.
  - sprachausgabe.py: the voice is loaded once, by the app's single `SprachAusgabe()`
    (was: at import time and again in Hauptprogramm.py). `say()` (was `activate_thread()`),
    `milestones(total, target)` (was `speak()` with attributes), `reset()` per exercise.
  - systest.py calls `UEBUNGEN[name](target=None).update(pose, timestamp)` directly: no
    stand-in class, no `time.time()` patch, no `sprachausgabe` stub.
  - checked: old and new exercise classes fed the same 23 pose sequences (the 3 recordings
    plus random and targeted synthetic movements; every exercise counts 8-48 reps in them),
    each with target None/3/5/10 -- 1.6 million frames, identical counters, stages, totals,
    messages, completion, spoken milestones and bar values on every frame. `pytest` 2/2.
    The real Tk app (with message boxes and audio stubbed): "Aufnahmen testen" counts rec1
    of bizepscurls and halswirbelsaeule 1/1 OK each, same as the old code; a live run with
    a video as webcam goes through intro, counting, done and back to the menu; pause, zoom,
    keypoints, Stop and the camera-failed error work.

### Removed
- `sportuebungen/.mcp.json` is no longer tracked (`git rm --cached`; `.gitignore` already
  listed it): it holds a personal API key. Each machine keeps its own. Also git-ignored:
  `sportuebungen/videos/` (two old test videos, 72 MB, only named in older entries below),
  `sportuebungen/.playwright-mcp/` and `.claude/` (tool output and local settings).
- deleted `initialisierung.py` (`camera_start()`); the camera is opened in
  Hauptprogramm.py.
- deleted all TTS voices except Thorsten (Eva, Kerstin, Ramona, Karlsson, Pavoque,
  MLS -- 12 files)
- Hauptmenü: removed the "Einstellungen" and "Nutzerverwaltung" buttons
- Einstellungen: removed the voice-selection dropdown, the "Anhören" test button,
  and the `voice_changed()` / `voice_test()` methods (page now holds only a back
  button)
- removed the entire Nutzerverwaltung code from Hauptprogramm.py (~244 lines):
  `user_frame`, `configure_user_frame`, add/delete/select-user views,
  `show_progress_window`, `load_progress_data` and the related helper methods and
  StringVars. `user_database.py` is unchanged; its `delete_user`, `get_users` and
  `get_exercise_days_with_date` are now unused.
- deleted `user_database.py` and progress persistence entirely: the app no longer
  saves or reads exercise progress. Removed `user_db`/`current_user`/`postop_day`
  params and the `save_progress()` calls from all 8 exercise classes
  (`abduktion.py`, `seitenheben.py`, `bizepscurls.py`, `flexion_schulter.py`,
  `halswirbelsaeule.py`, `rotation.py`, `butterfly.py`, `mobilisation_schulter.py`),
  the `UserDatabase` import/instantiation and `_bind_user_to_all_exercises()` from
  `Hauptprogramm.py`, and the checked-in `users.db` / `Benutzerdaten/` database
  files. The "Tag nach OP" dropdown is unaffected -- it still sets rep counts,
  it just no longer gets recorded anywhere.
- removed the MediaPipe Face Landmarker entirely. After Halswirbelsaeule moved to pose
  landmarks nothing used it at runtime, but two instances were still loaded at startup.
  - mediapipe_modelle.py: `face_recognition()`, `normalize_face_landmarks()`, the
    `FaceTask` instance, `face_contours` and the now-unused `cv2` import.
  - bildoperation_opencv.py: the `'facemesh'` zoom mode (`_from_facemesh()`,
    `_landmarks_to_bbox()`, its `FaceTask`), plus the `mode` constructor argument and
    `set_mode()`. The zoom always follows the pose landmarks, as it already did by default
    (`mode='posemesh'`; `set_mode()` was never called).
  - mediapipe_tasks_compat.py: `FaceTask`, `_FaceResult`, `FACE_MODEL`,
    `FACEMESH_CONTOURS` and the face model URL in the missing-model error.
  - deleted `models/face_landmarker.task`.
  - smoke test on videos/VIDEO_DIGIMOB_1.mp4 through the real `Bildoperationen` ->
    `MediapipeModelle` -> Halswirbelsaeule chain: zoom off and zoom 2x both work,
    keypoints draw, 5 left + 5 right counted.

- dead code: `exercise_cancel_time` (6 classes), `wait_1`/`program_end`/
  `sport_exercise_example`/`counter_started` flags, `set_repetitions()`, `balken()` and the
  `start_<name>_exercise()` methods (replaced by `Uebung`/`Uebungslauf`); in
  Bildoperationen `set_zoom`, `inc_zoom`, `toggle_keypoints`, `close`, `_mp_draw`,
  `_mp_style`, the unused `resolution`/`cam`/`max_zoom` arguments; `load_voice`,
  `SprachAusgabe.lock` and the module-global voice; `mp_drawing_styles`; in
  Hauptprogramm `pdf_images`, `delta_time`, `set_camera()`, `livemode`, the no-op
  `apply_zoom(frame, zoom_level)` call and the unreachable "Einstellungen" page
  (`configure_settings_frame()`).
- deleted `models/pose_landmarker_lite.task` (unused, the app uses the full model).
- bildoperation_opencv.py: the zoom's head bounding box. `_pose_to_bbox()` computed a
  head width/height (from the ears, else the eyes) and `_smooth()` smoothed it, but
  `apply_zoom()` only ever used the center; the crop size comes from `zoom_factor`. The
  center is now read directly as the nose landmark in `_from_posemesh()`, and `_smooth()`
  smooths only `cx`/`cy`. Same crop as before.
- bildoperation_opencv.py: `image_zoomed.flags.writeable = True` in `overlay()`, a no-op
  left over from the `mp.solutions` pattern (nothing sets the flag to False anymore).
- deleted `mediapipe_tasks_compat.py`, the shim that imitated the removed `mp.solutions` API
  (`PoseTask.process()` -> `results.pose_landmarks.landmark`). `mediapipe_modelle.py` now owns
  all MediaPipe setup and is the only module that imports `mediapipe`: `POSE_MODEL` and the
  missing-model check with download URL, the `PoseLandmarker` options (full model, VIDEO mode,
  `num_poses=1`, detection/tracking confidence 0.5), the `mp.Image` conversion and the strictly
  increasing monotonic timestamps, all unchanged. New `draw_pose(image, landmarks)` there with
  the same connections and red/white specs.
  - the zoom (`Bildoperationen`, its own instance) and `tools/record_exercise.py`
    (`PoseWorker`) call `MediapipeModelle().detect()` like the app; the zoom takes the nose from
    the returned `Pose`, the recorder passes `pose.landmarks` to `landmarks_to_list()`.
  - dropped without replacement: `_LandmarkList`, `_PoseResult`, `pose_world_landmarks`,
    `drawing_styles`, `_VideoTask`, `close()` and the generic `draw_landmarks` wrapper.
  - checked: over all 131 frames of recordings/halswirbelsaeule/rec1 the old shim and the new
    `MediapipeModelle` give bit-identical landmarks at equal timestamps; the recorded keypoint
    format (33 x 5 values) is unchanged, so existing `keypoints_full.jsonl` stay valid. systest
    unchanged (5 passed; `bizepscurls/rec5` fails 0/3 as before, unrelated).

### ToDo
- Seitheben and Mobilisation Schulter have identical thresholds (40°/70°), so the counter
  can't tell the two movements apart -- check with the medical adviser
- scale the program window, menues, font sizes, video frames etc. to the screen resolution
- check the calculation of arm and body positions for proper training movements
- Bizepscurls hold time works on camera; confirm with patients, then add the
  same hold time to the other exercises' stage machines -- they still change stage on a
  single frame (most exposed: seitenheben.py / mobilisation_schulter.py 40°/70°,
  rotation.py 1.1/1.4)
- record longer recordings for `systest.py`. The present ones hold a single rep, which barely
  constrains the thresholds: measured by changing a threshold and re-running the test,
  `halswirbelsaeule/rec1` still counts 1 for every `max_angle` from 5° to 70° (real value 35°),
  and `bizepscurls/rec1` for every `min_angle` from 20° to 170° (real value 110°). One clearly
  exaggerated rep passes almost any threshold. What the test does catch today: crashes, a
  broken pipeline, and rules that are too strict (`confirm_time` 1.5 s makes it fail). It needs recordings with 5+ reps, and recordings that must count 0
  (movement too small, wrong exercise), before a green run means the thresholds are still right
- Bizepscurls `confirm_time` only passes all takes from 0.20 to 0.25 s, a narrow window on 5
  recordings: record more takes (quick reps, hands in and out of the image) and check the margin
- the pose model stays full (compared on 25 takes of all 8 exercises with compare_models and
  view_models). Back up `recordings/` outside the repo: the videos are git-ignored, and
  without them no keypoints can be re-extracted
- check the 3D demos with the medical adviser (movement range, speed, which side goes first)
- optionally add `expected_per_side` to bizepscurls rec2/rec3 (one-arm takes) after checking
  in the video which arm moved
- all Bizepscurls takes so far frame the patient from the chest up, so the hanging hands are
  outside the image and MediaPipe guesses them. Decide on a camera position that shows the
  hands and say so in the Bedienungsanleitung / the exercise intro

---

## [1.1.8] 2025-12-17

### Changed
- changed storage location of user database to subfolder '/Benutzerdaten/'
- refreshed all version numbers in source code

### Removed
- removed notification after user selection ("Du hast ... gewählt")
- removed unused code (zoom-slider, Sportübung unterbrechen, Visualisierung-Button, ...)

### ToDo
- Show the active profile name on every page
- Keep the selected user when opening the user progress list
- scale the program window, menues, font sizes, video frames etc. to the screen resolution
- check the calculation of arm and body positions for proper training movements

---

## [1.1.7] 2025-09-14

### Fixed
- Fixing the layouts of Nutzerverwaltung (position and fonts)

### ToDo
- Show the active profile name on every page

---

## [1.1.6] 2025-09-13

### Added
- zoom-function with face tracking (by facemesh/posemesh)

### Changed
- zoom-button changed to on/off function with zoom factor 1x and 2x
- Hauptmenü -> Einstellung: Remove the button for visualize keypoints and move it to exercise page

### Fixed
- Bug in number of repetitions
- font and font size as a variable

### ToDo
- Fixing the layouts of Nutzerverwaltung (Position and Fonts)

---

## [1.1.5] 2025-09-10

### Added
- Hauptmenü -> additional Page : Nutzerverwaltung created: User managament for add, delete user profile and to show progress
- zoom-button with slider

### Fixed
- Standardization of Camera (Normierung der Kamera)

### ToDo
- zoom function with face tracking

---

## [1.1.4] 2025-05-09

### Added
- Hauptmenü -> Einstellungen: button 'Keypoints an/aus' to show the keypoints inside the video
- zoom-button (2x) to adjust camera in every exercise

### ToDo
- zoom-button with slider

### Fixed
- bug in Sprachausgabe -- Halfway 
- remove umlauts from all text in every exercise class ("Jetzt koennen Sie mit dieser Uebung starten")

---

## [1.1.3] 2025-04-17

### Changed
- layout of buttons: moved to column 0 (left side)
- font sizes increased (16 -> 24, 28 -> 32)
- button labels shortended (e.g. 'Zurück zum Hauptmenü' -> 'Zurück')
- video frame and bargraph enlarged

### Fixed
- bug in 'Pause'-button (converted to 2-color toggle button)

### ToDo
- Hauptmenü -> Einstellungen: 'Stimme testen' can be clicked many times while test is running
- when stopping exercise, post-op day value is always returned to 5 even if dropdown menu still shows other value

---

## [1.1.2] 2025-04-16

### Added
- Hauptmenü: 'Pause'-Button -> pauses update_frame()
- Hauptmenü -> Einstellungen: 'Stimme testen'

### ToDo
- Hauptmenü -> Einstellungen: 'Stimme testen' can be clicked many times while test is running

---

## [1.1.1] 2025-04-15

### Changed
- Hauptmenü: 'Zurück zum Hauptmenü' aborts and resets the exercises
- Hauptmenü: stop-kamera() split into: stop-camera(), reset-bargraph(), reset-exercises()
- initialisierung.py: initialisierung-start() renamed to: camera-start()

### ToDo
- Hauptmenü: 'Pause'-Button pauses, but does not continue the exercise

---

## [1.1.0] 2025-04-14

### Added
- Hauptprogramm: additional page 'Einstellungen' created
- Hauptprogramm: 'Einstellungen': dopdown menu to select different tts-voice models

### Fixed
- sprachausgabe: threading.Lock() to avoid crash caused by overlapping voice calls
- unified height, width and font size of buttons

### Changed
- halswirblesaeule -> halswirbelsaeule
- butterfly.py: changed word 'butterfly' to "batterflei' for german voices
- some minor spell corrections

### Removed
- "Sportübung unterbrechen" in all exercises turned off (commented out)
