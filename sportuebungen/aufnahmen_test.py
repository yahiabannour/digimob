"""
******************************************************************************
 file:      aufnahmen_test.py
 brief:     "Aufnahmen testen" (Knopf im Hauptmenue): spielt die Aufnahmen aus videomodus.txt
            nacheinander statt der Webcam ab, jede mit ihrer Uebung, und zaehlt nur. Am Ende
            zeigt eine Liste die gezaehlten Wiederholungen jeder Aufnahme neben expected_reps.
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12)
 info:      Kein Ziel aus dem OP-Tag, keine Einfuehrung, keine Sprachausgabe: jede Uebung laeuft
            mit target=None und intro=False im selben Uebungslauf wie live.
******************************************************************************
"""
import json
import logging
import os
from tkinter import messagebox

from camera.file_camera import FileCamera
from tools.recording_format import SUMMARY_FILE
from uebungen import UEBUNGEN

# Liste der Aufnahmen, die nacheinander abgespielt werden (Aufbau siehe Kommentare in der Datei)
VIDEO_CONFIG = "videomodus.txt"


def read_test_videos():
    """Aufnahmen aus videomodus.txt, jede mit ihrer Uebung: aus der summary.json neben dem Video
    (tools/record_exercise.py), sonst aus dem Ordner recordings/<uebung>/rec<N>/.
    Liste von {"path", "exercise", "expected"}; expected = Wiederholungen laut Aufnahme oder None."""
    with open(VIDEO_CONFIG, encoding="utf-8") as f:
        paths = [line.strip() for line in f if line.strip() and not line.strip().startswith("#")]

    videos, problems = [], []
    for path in paths:
        if not os.path.exists(path):
            problems.append(f"{path}: Datei nicht gefunden")
            continue
        rec_dir = os.path.dirname(path)
        summary = {}
        if os.path.exists(os.path.join(rec_dir, SUMMARY_FILE)):
            with open(os.path.join(rec_dir, SUMMARY_FILE), encoding="utf-8") as f:
                summary = json.load(f)
        exercise = summary.get("exercise") or os.path.basename(os.path.dirname(rec_dir))
        if exercise not in UEBUNGEN:
            problems.append(f"{path}: Übung unbekannt (erwartet recordings/<uebung>/rec<N>/)")
            continue
        videos.append({"path": path, "exercise": exercise, "expected": summary.get("expected_reps")})

    if problems:
        raise ValueError("\n".join(problems))
    return videos


class AufnahmenTest:
    def __init__(self, app):
        self.app = app          # SportApp: lauf, window, resolution, exercise_frame, show_frame, loop, back_to_menu
        self.active = False
        self.cam = None

    def start(self):
        try:
            self.videos = read_test_videos()
        except Exception as e:
            messagebox.showerror("Aufnahmen testen", f"{VIDEO_CONFIG}:\n{e}")
            return
        if not self.videos:
            messagebox.showwarning("Aufnahmen testen", f"In {VIDEO_CONFIG} ist keine Aufnahme eingetragen (alle Zeilen mit #).")
            return

        logging.info(f"Aufnahmen-Test mit {len(self.videos)} Aufnahme(n)")
        self.active = True
        self.results = []
        self.index = 0
        self.app.show_frame(self.app.exercise_frame)
        self.start_video()
        self.app.loop()

    # Naechstes Video oeffnen und seine Uebung frisch starten; nach dem letzten das Ergebnis
    def start_video(self):
        width, height = self.app.resolution
        while self.index < len(self.videos):
            video = self.videos[self.index]
            try:
                self.cam = FileCamera(video["path"], mirrored=False, frame_width=width, frame_height=height)
            except Exception as e:
                # Video laesst sich nicht oeffnen: im Ergebnis vermerken, weiter mit dem naechsten
                logging.error(f"Aufnahmen-Test: {video['path']}: {e}")
                self.results.append(f"{video['path']} ({video['exercise']}): Fehler: {e}")
                self.index += 1
                continue

            number = f"{self.index + 1}/{len(self.videos)}"
            self.app.window.title(f"Reha-Sportübungen - Aufnahmen-Test {number}: {video['path']}")
            logging.info(f"Aufnahmen-Test {number}: {video['path']} ({video['exercise']})")
            # Ohne Ziel (nur zaehlen) und ohne Einfuehrung: gezaehlt wird ab dem ersten Frame
            self.app.lauf.start(self.cam, [UEBUNGEN[video["exercise"]](target=None)],
                                on_source_end=self.video_finished, intro=False)
            return
        self.show_results()

    # Video zu Ende -> Ergebnis merken, weiter mit dem naechsten Video
    def video_finished(self, uebung):
        video = self.videos[self.index]
        counted = uebung.total
        result = f"{video['path']} ({video['exercise']}): {counted} gezählt"
        if uebung.sides == ("left", "right"):
            result += f" (links {uebung.counter['left']}, rechts {uebung.counter['right']})"
        if video["expected"] is not None:
            result += f", laut Aufnahme {video['expected']}"
            result += " - OK" if counted == video["expected"] else " - ABWEICHUNG"
        self.results.append(result)
        logging.info(f"Aufnahmen-Test: {result}")

        self.cam.close()
        self.index += 1
        self.start_video()

    # Ergebnis aller Videos anzeigen (auch im Terminal, zum Kopieren), dann zurueck ins Hauptmenue
    def show_results(self):
        self.app.lauf.stop()
        text = "\n".join(self.results)
        print(f"Ergebnis Aufnahmen-Test:\n{text}")
        messagebox.showinfo("Ergebnis Aufnahmen-Test", text)
        self.app.back_to_menu()

    # Test beenden (Ergebnis gezeigt oder "Stop"): Video schliessen, Fenstertitel zuruecksetzen
    def end(self):
        if self.cam is not None:
            self.cam.close()
            self.cam = None
        self.active = False
        self.app.window.title("Reha-Sportübungen")
