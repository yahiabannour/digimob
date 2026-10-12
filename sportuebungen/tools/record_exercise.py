"""
******************************************************************************
 file:      tools/record_exercise.py
 brief:     Nimmt Sportuebungen mit der Kamera auf: Video (mp4, sonst webm) fuer den
            Videomodus der App (videomodus.txt), eine Zusammenfassung mit Ground Truth und pro Frame die
            MediaPipe-Pose-Keypoints fuer Systemtests. Ohne Tkinter, Sprachausgabe und Zoom.
 author(s): Yahia Bannour
 date:      10.10.2026
 software:  Python (Version: 3.12)
 ext. libs: OpenCV (Version: 5.0.0.93), Mediapipe (Version: 1.0.1, Tasks-API)
 info:      Aufruf aus sportuebungen/:
              python tools/record_exercise.py bizepscurls [--cam 1]
            Uebungen: UEBUNGEN in uebungen.py, Dateiformat: tools/recording_format.py.
            --cam: Kamera-Index, z.B. fuer eine externe Webcam.
            --preset einfach: mit den gelockerten Schwellen nachzaehlen (Bewegung wie Patienten),
                     Standard voll. Landet als "preset" in summary.json.
            Welche Indizes es gibt:
              python tools/record_exercise.py --list-cams
            Vorschaufenster:
              SPACE  Aufnahme starten / beenden
              danach Anzahl Wiederholungen eintippen, die die App zaehlen soll (Ground Truth),
                     z.B. "5" fuer beide Seiten oder "5 0" fuer links und rechts (Sicht des
                     Patienten, nur bei Uebungen mit zwei Seiten), ENTER speichert, ESC verwirft
                     die Aufnahme
              q      beenden (nur ohne offene Aufnahme)
            Jede Aufnahme in der Startposition der Uebung beginnen und beenden (z.B. Arme unten):
            Systemtest und Videomodus zaehlen ab dem ersten Frame.
            Alles andere ist fest:
              - jede Aufnahme bekommt einen eigenen Ordner sportuebungen/recordings/<uebung>/rec<N>/
                (rec1, rec2, ...) mit video.mp4, summary.json und keypoints_<modell>.jsonl, <modell> ist
                das Pose-Modell der App (mediapipe_modelle.POSE_MODEL), z.B. full
              - immer 640x480 wie die App (Begruendung bei FRAME_WIDTH)
              - die Bildrate des Videos wird vor jeder Aufnahme in der Vorschau an der Kamera gemessen
              - keine Laengenbegrenzung
******************************************************************************
"""
import argparse
import collections
import datetime
import os
import queue
import shutil
import sys
import threading
import time

import cv2

# Das Skript liegt in sportuebungen/tools/, die Module der App eine Ebene hoeher
APP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, APP_DIR)

from camera.camera import Camera
from mediapipe_modelle import POSE_MODEL, MediapipeModelle, draw_pose
from tools.recording_format import (REC_DIR_PREFIX, SUMMARY_FILE, VIDEO_FORMATS, VIDEO_NAME, landmarks_to_list,
                                    model_name, write_recording)
from uebung import PRESETS
from uebungen import UEBUNGEN

# Unabhaengig vom aktuellen Ordner
RECORDINGS_DIR = os.path.join(APP_DIR, "recordings")

# Steckt im Namen der Keypoints-Datei, z.B. "full" fuer pose_landmarker_full.task -> keypoints_full.jsonl
POSE_MODEL_NAME = model_name(POSE_MODEL)

# Immer 640x480 wie die App (Hauptprogramm.py), auch bei FHD/UHD-Webcams: Die Landmarks sind auf
# Bildbreite und -hoehe normiert, calculate_angle() rechnet direkt damit. In 16:9 ergaeben dieselben
# Bewegungen andere Winkel (an den Bizepscurls-Schwellwerten bis ~8 Grad). Mehr Pixel machen die
# Pose nicht genauer (MediaPipe verkleinert intern auf ~256 px), und UHD schafft keine 30 fps.
FRAME_WIDTH, FRAME_HEIGHT = 640, 480

FPS_WINDOW = 30     # Vorschau-Frames, ueber die die Bildrate der Kamera gemessen wird
POSE_BACKLOG = 60   # Frames (~2 s), laeuft die Pose-Erkennung weiter hinterher, bremst die Aufnahme
MAX_REPS_DIGITS = 3

V4L2_SYSFS = "/sys/class/video4linux"   # Linux: Namen der Kameras, fuer --list-cams und die Zusammenfassung

WINDOW_NAME = "DigiMob Aufnahme"
KEY_SPACE = 32
KEY_ESC = 27
KEYS_ENTER = (13, 10)
KEYS_BACKSPACE = (8, 127)


class PoseWorker(threading.Thread):
    """Wertet die aufgenommenen Frames in Aufnahmereihenfolge aus. Laeuft parallel zur Aufnahme:
    Pose-Erkennung (~27 ms) plus Schreiben und Vorschau passen nicht in die 33 ms eines Frames
    bei 30 fps, im selben Thread gingen Kamerabilder verloren."""

    def __init__(self, backlog):
        super().__init__(daemon=True)
        # Dieselben Einstellungen wie MediapipeModelle in der App
        self.pose = MediapipeModelle()
        # Begrenzt: laeuft die Erkennung dauerhaft hinterher, bremst put() die Aufnahme,
        # statt den Speicher mit Frames zu fuellen
        self.jobs = queue.Queue(maxsize=backlog)
        self.latest_landmarks = None   # fuer die Vorschau

    def run(self):
        while True:
            job = self.jobs.get()
            if job is None:
                self.jobs.task_done()
                return
            recording, frame_rgb = job
            try:
                pose = self.detect(frame_rgb)
            except Exception as e:
                # Ein fehlerhafter Frame darf die Aufnahme nicht abbrechen -> Frame ohne Pose
                print(f"Fehler bei der Pose-Erkennung: {e}")
                self.latest_landmarks = None
                pose = None
            recording.poses.append(pose)
            self.jobs.task_done()

    def detect(self, frame_rgb):
        """Pose-Landmarks als Liste aus landmarks_to_list(), None ohne Pose."""
        pose = self.pose.detect(frame_rgb)
        self.latest_landmarks = pose.landmarks if pose is not None else None
        return landmarks_to_list(self.latest_landmarks)

    def stop(self):
        self.jobs.put(None)
        self.join()


def measure_fps(frame_times):
    """Bildrate aus den Zeitpunkten der letzten Vorschau-Frames. Direkt nach dem Start (weniger als
    10 Frames) 30 fps, die uebliche Webcam-Rate."""
    if len(frame_times) < 10:
        return 30.0
    return round((len(frame_times) - 1) / (frame_times[-1] - frame_times[0]), 2)


def new_rec_dir(exercise_dir):
    """Legt den Ordner der naechsten Aufnahme an und gibt seinen Pfad zurueck: rec<N>, N eins hoeher
    als die hoechste vorhandene Nummer (rec1, rec2, ...)."""
    numbers = [int(name[len(REC_DIR_PREFIX):]) for name in os.listdir(exercise_dir)
               if name.startswith(REC_DIR_PREFIX) and name[len(REC_DIR_PREFIX):].isdecimal()]
    rec_dir = os.path.join(exercise_dir, f"{REC_DIR_PREFIX}{max(numbers, default=0) + 1}")
    os.mkdir(rec_dir)   # ohne exist_ok: eine vorhandene Aufnahme wird nie ueberschrieben
    return rec_dir


def open_video_writer(rec_dir, fps, frame_size):
    """(VideoWriter, Pfad) fuer das erste funktionierende Format aus VIDEO_FORMATS."""
    for ext, fourcc in VIDEO_FORMATS:
        path = os.path.join(rec_dir, VIDEO_NAME + ext)
        writer = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*fourcc), fps, frame_size)
        if writer.isOpened():
            return writer, path
        writer.release()
        print(f"Warnung: Video als {fourcc} ({ext}) nicht moeglich")
    raise RuntimeError("kein Videoformat verfuegbar (" +
                       ", ".join(f"{fourcc} {ext}" for ext, fourcc in VIDEO_FORMATS) + ")")


class Recording:
    """Eine Aufnahme: schreibt die Frames sofort ins Video und sammelt Zeitstempel und
    Pose-Ergebnisse (poses wird vom PoseWorker in derselben Reihenfolge gefuellt).
    Gespeichert wird erst mit der Ground Truth (save), sonst verworfen (discard).
    meta: exercise, camera, fps_target"""

    def __init__(self, exercise_dir, frame_size, meta):
        self.started_at = datetime.datetime.now()
        self.rec_dir = new_rec_dir(exercise_dir)
        try:
            self.writer, self.video_path = open_video_writer(self.rec_dir, meta["fps_target"], frame_size)
        except RuntimeError:
            shutil.rmtree(self.rec_dir)   # keinen leeren recN-Ordner liegen lassen
            raise
        self.frame_size = frame_size
        self.meta = meta
        self.t0 = None
        self.timestamps = []
        self.poses = []

    def elapsed(self, now):
        return 0.0 if self.t0 is None else now - self.t0

    def add_frame(self, now, frame_bgr):
        if self.t0 is None:
            self.t0 = now
        self.timestamps.append(round(now - self.t0, 6))   # Sekunden, auf Mikrosekunden gerundet
        self.writer.write(frame_bgr)

    def finish(self):
        # Erst nach release() ist das Video vollstaendig und abspielbar
        self.writer.release()

    def discard(self):
        self.writer.release()
        # Der Ordner wurde fuer diese Aufnahme neu angelegt und enthaelt bis zum save() nur das Video
        shutil.rmtree(self.rec_dir)

    def save(self, expected_reps, expected_per_side=None):
        """Speichert Zusammenfassung und Keypoints. Erst aufrufen, wenn der PoseWorker alle Frames
        dieser Aufnahme ausgewertet hat (worker.jobs.join()).
        expected_per_side: {"left": n, "right": m}, None wenn nur eine Zahl eingegeben wurde"""
        meta = dict(self.meta, expected_reps=expected_reps, expected_per_side=expected_per_side,
                    width=self.frame_size[0], height=self.frame_size[1],
                    started_at=self.started_at.isoformat(timespec="seconds"),
                    video=os.path.basename(self.video_path),
                    pose_model=POSE_MODEL_NAME)
        summary, keypoints_path = write_recording(self.rec_dir, self.timestamps, self.poses, meta)

        print(f"\nAufnahme gespeichert: {self.rec_dir}")
        print(f"  Video:           {os.path.basename(self.video_path)}")
        print(f"  Zusammenfassung: {SUMMARY_FILE}")
        print(f"  Keypoints:       {os.path.basename(keypoints_path)}")
        per_side = (f", links {expected_per_side['left']}, rechts {expected_per_side['right']}"
                    if expected_per_side else "")
        print(f"  Wiederholungen:  {expected_reps}{per_side} (Ground Truth, Preset {meta['preset']})")
        print(f"  Frames:          {summary['frames_recorded']} ({summary['measured_fps']} fps gemessen)")
        print(f"  Frames mit Pose: {summary['frames_with_pose']}")
        measured_fps = summary["measured_fps"]
        if measured_fps is not None and abs(measured_fps / meta["fps_target"] - 1) > 0.1:
            print(f"Warnung: {measured_fps:.1f} statt {meta['fps_target']:g} fps aufgenommen, das Video laeuft "
                  f"nicht in Echtzeit (die Zeitstempel in {os.path.basename(keypoints_path)} stimmen)")


def parse_reps(text, two_sides):
    """Ground Truth aus der Eingabe: (expected_reps, expected_per_side), None solange unvollstaendig.
    "5" gilt fuer beide Seiten (expected_per_side None), "5 0" = links rechts aus Sicht des Patienten,
    expected_reps ist dann die kleinere Zahl wie Uebung.total."""
    parts = text.split(" ")
    if not all(parts):   # leer oder Leerzeichen am Ende
        return None
    if len(parts) == 1:
        return int(parts[0]), None
    if two_sides:
        left, right = int(parts[0]), int(parts[1])
        return min(left, right), {"left": left, "right": right}
    return None


def preview_image(frame_bgr, recording, landmarks, now, reps_input, two_sides):
    """Vorschau, gespiegelt wie in der App (aufgenommen wird ungespiegelt). reps_input ist der bisher
    eingetippte Text, solange die Ground Truth abgefragt wird, sonst None.
    Zeichnet das Skelett direkt in frame_bgr, der Frame muss also schon ins Video geschrieben sein."""
    if recording is not None:
        if landmarks is not None:
            draw_pose(frame_bgr, landmarks)
        pose_text = "ja" if landmarks is not None else "nein"
        text = (f"REC {recording.elapsed(now):5.1f} s   "
                f"Frames {len(recording.timestamps)}   Pose {pose_text}   SPACE: Stopp")
        color = (0, 0, 255)
    elif reps_input is not None:
        hint = "  (links rechts, Sicht Patient)" if two_sides and " " in reps_input else ""
        text, color = f"Wiederholungen: {reps_input}_{hint}   ENTER speichern   ESC verwerfen", (0, 255, 255)
    else:
        text, color = "SPACE: Aufnahme starten   q: Beenden", (255, 255, 255)

    preview = cv2.flip(frame_bgr, 1)
    cv2.putText(preview, text, (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 4, cv2.LINE_AA)
    cv2.putText(preview, text, (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2, cv2.LINE_AA)
    return preview


def device_info(index, attribute):
    """Linux: Attribut von /dev/video<index>, "name" = Name des Geraets bzw. "index" = Nummer innerhalb
    des Geraets (0 = Bildstream). "" ohne das Geraet oder ausserhalb von Linux."""
    path = os.path.join(V4L2_SYSFS, f"video{index}", attribute)
    if not os.path.exists(path):
        return ""
    with open(path) as f:
        return f.read().strip()


def list_cameras():
    """--list-cams: oeffnet jede Kamera kurz und zeigt Index, Name und das gelieferte Bild."""
    if os.path.isdir(V4L2_SYSFS):
        # Linux: Jede Kamera hat mehrere /dev/videoN, die Metadaten-Geraete lassen sich nicht oeffnen.
        # Nur V4L2, denn die Ausweich-Backends von OpenCV stuerzen an diesen Geraeten teils ab.
        indexes = sorted(int(name[len("video"):]) for name in os.listdir(V4L2_SYSFS) if name.startswith("video"))
        backend = cv2.CAP_V4L2
    else:
        indexes, backend = range(5), cv2.CAP_ANY   # wie Camera.autoselect_cam_id()

    log_level = cv2.utils.logging.getLogLevel()
    cv2.utils.logging.setLogLevel(cv2.utils.logging.LOG_LEVEL_SILENT)   # Warnungen der Metadaten-Geraete
    cameras = []
    for index in indexes:
        cap = cv2.VideoCapture(index, backend)
        opened = cap.isOpened()
        frame = None
        if opened:
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)
            for _ in range(10):   # die ersten Bilder sind oft noch dunkel (Belichtung)
                ok, image = cap.read()
                frame = image if ok else frame
        cap.release()

        if not opened:
            # Metadaten-Geraete lassen sich nie oeffnen, ein Bildstream nur dann nicht, wenn er belegt ist
            if device_info(index, "index") != "0":
                continue
            size, notes = "-", ["laesst sich nicht oeffnen (von einem anderen Programm belegt?)"]
        elif frame is None:
            size, notes = "-", ["liefert kein Bild (von einem anderen Programm belegt?)"]
        elif frame.mean() < 10:
            # vor der Graustufen-Pruefung: ein schwarzes Bild hat auch gleiche Farbkanaele
            size, notes = f"{frame.shape[1]}x{frame.shape[0]}", ["Bild schwarz (Laptopdeckel zu?)"]
        else:
            size, notes = f"{frame.shape[1]}x{frame.shape[0]}", []
            if (frame[..., 0] == frame[..., 1]).all() and (frame[..., 1] == frame[..., 2]).all():
                notes.append("Graustufen, vermutlich Infrarot")
        cameras.append((index, size, device_info(index, "name"), ", ".join(notes)))
    cv2.utils.logging.setLogLevel(log_level)

    print(f"Gefundene Kameras: {len(cameras)}")
    for index, size, name, notes in cameras:
        print(f"  --cam {index:<3} {size:<10} {name}" + (f"   ({notes})" if notes else ""))


def parse_args():
    parser = argparse.ArgumentParser(
        description="Nimmt eine Sportuebung als Video plus Pose-Keypoints mit Ground Truth auf (640x480, "
                    "jede Aufnahme in sportuebungen/recordings/<uebung>/rec<N>/). Vorschaufenster: SPACE "
                    "startet/beendet eine Aufnahme, danach Wiederholungen eintippen und ENTER (ESC verwirft), "
                    "q beendet.")
    parser.add_argument("exercise", nargs="?", choices=sorted(UEBUNGEN), help="Uebung")
    parser.add_argument("--cam", type=int,
                        help="Kamera-Index, z.B. fuer eine externe Webcam (Standard: automatische Auswahl)")
    parser.add_argument("--preset", choices=PRESETS, default=PRESETS[0],
                        help="Schwellen, mit denen systest und Aufnahmen testen diese Aufnahmen zaehlen: "
                             "voll, oder einfach fuer Bewegungen wie von Patienten (Standard: voll)")
    parser.add_argument("--list-cams", action="store_true",
                        help="angeschlossene Kameras mit Index fuer --cam anzeigen und beenden")
    args = parser.parse_args()
    if args.exercise is None and not args.list_cams:
        parser.error("Uebung fehlt, z.B.: python tools/record_exercise.py bizepscurls")
    return args


def main():
    args = parse_args()
    if args.list_cams:
        list_cameras()
        return
    exercise_dir = os.path.join(RECORDINGS_DIR, args.exercise)
    two_sides = len(UEBUNGEN[args.exercise].sides) > 1
    os.makedirs(exercise_dir, exist_ok=True)

    # mirrored=False: Die App wertet das ungespiegelte Kamerabild aus (Uebungslauf.tick()),
    # gespiegelt waeren linke und rechte Landmarks vertauscht
    with Camera(cam_id=args.cam, mirrored=False, frame_width=FRAME_WIDTH, frame_height=FRAME_HEIGHT) as cam:
        height, width = cam.getFrame().shape[:2]
        if (width, height) != (FRAME_WIDTH, FRAME_HEIGHT):
            print(f"Warnung: Kamera liefert {width}x{height} statt {FRAME_WIDTH}x{FRAME_HEIGHT}, aufgenommen wird "
                  f"{width}x{height}. Bei anderem Seitenverhaeltnis weichen die Winkel von der App ab.")

        print("Lade Pose-Modell ...")
        worker = PoseWorker(backlog=POSE_BACKLOG)
        worker.start()

        # Name wie bei --list-cams, None ausserhalb von Linux
        camera = {"index": cam.cam_id, "name": device_info(cam.cam_id, "name") or None}
        meta = {"exercise": args.exercise, "preset": args.preset, "camera": camera}
        camera_name = f" ({camera['name']})" if camera["name"] else ""
        print(f"Kamera {cam.cam_id}{camera_name}, {width}x{height}, Pose-Modell {POSE_MODEL_NAME}, Preset {args.preset}, "
              f"Ausgabe: {exercise_dir}/{REC_DIR_PREFIX}<N>/")
        print("Vorschaufenster: SPACE startet/beendet die Aufnahme, danach Wiederholungen eintippen und ENTER "
              "(ESC verwirft), q beendet.")
        if two_sides:
            print('Wiederholungen pro Seite: "5 0" = links 5, rechts 0 (Sicht des Patienten), "5" = beide Seiten.')
        print("Jede Aufnahme in der Startposition der Uebung beginnen und beenden.")

        preview_times = collections.deque(maxlen=FPS_WINDOW)   # fuer die Bildrate des naechsten Videos
        recording = None    # laeuft gerade
        labeling = None     # beendet, wartet auf die Ground Truth
        reps_input = ""
        try:
            while not cam.stop_event.is_set():
                frame_rgb = cam.getFrame()
                if frame_rgb is None:   # Kamera ausgefallen
                    print("Fehler: Die Kamera liefert keine Bilder mehr.")
                    break
                now = time.monotonic()
                frame_bgr = cv2.cvtColor(frame_rgb, cv2.COLOR_RGB2BGR)

                if recording is not None:
                    recording.add_frame(now, frame_bgr)
                    worker.jobs.put((recording, frame_rgb))
                else:
                    preview_times.append(now)

                cv2.imshow(WINDOW_NAME, preview_image(frame_bgr, recording, worker.latest_landmarks, now,
                                                      reps_input if labeling is not None else None, two_sides))
                key = cv2.waitKey(1) & 0xFF
                if cv2.getWindowProperty(WINDOW_NAME, cv2.WND_PROP_VISIBLE) < 1:
                    break

                if recording is not None:
                    if key == KEY_SPACE:
                        worker.jobs.join()   # warten, bis alle Frames dieser Aufnahme ausgewertet sind
                        recording.finish()
                        labeling, recording, reps_input = recording, None, ""
                        print("Aufnahme beendet: Wiederholungen im Vorschaufenster eintippen, ENTER speichert, "
                              "ESC verwirft.")
                elif labeling is not None:
                    last = reps_input.split(" ")[-1]
                    if ord("0") <= key <= ord("9") and len(last) < MAX_REPS_DIGITS:
                        reps_input += chr(key)
                    elif key == KEY_SPACE and two_sides and last and " " not in reps_input:
                        reps_input += " "
                    elif key in KEYS_BACKSPACE:
                        reps_input = reps_input[:-1]
                    elif key in KEYS_ENTER and parse_reps(reps_input, two_sides) is not None:
                        labeling.save(*parse_reps(reps_input, two_sides))
                        labeling = None
                    elif key == KEY_ESC:
                        labeling.discard()
                        labeling = None
                        print("Aufnahme verworfen.")
                elif key == ord("q"):
                    break
                elif key == KEY_SPACE:
                    try:
                        recording = Recording(exercise_dir, (width, height),
                                              dict(meta, fps_target=measure_fps(preview_times)))
                    except RuntimeError as e:
                        print(f"Fehler: {e}")
                        continue
                    preview_times.clear()
                    worker.latest_landmarks = None
                    print(f"\nAufnahme laeuft ({recording.meta['fps_target']:g} fps): {recording.video_path}")
        except KeyboardInterrupt:
            pass
        finally:
            # Ohne Ground Truth wird nichts gespeichert
            for take in (recording, labeling):
                if take is not None:
                    take.discard()
                    print("Aufnahme ohne Wiederholungszahl beendet und verworfen.")
            worker.stop()
            cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
