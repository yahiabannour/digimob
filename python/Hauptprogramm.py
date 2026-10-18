"""
******************************************************************************
 file:      Hauptprogramm.py
 brief:     Software für Sportübungen: die Oberfläche (Seiten, Knöpfe, Videobild, Balken)
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.11.2)
 int. libs: tkinter
 ext. libs: OpenCV (4.11.0.86)
            pillow (11.1.0)
 info:      Die Übungen laufen in uebungslauf.py, "Aufnahmen testen" in aufnahmen_test.py
            (der Knopf erscheint nur mit "python Hauptprogramm.py --entwickler"),
            die Liste der Übungen steht in uebungen.py. Die Auswahlseite zeigt neben jeder angekreuzten
            Übung ihre Schwellen als Schieberegler (Voll/Einfach oder von Hand), gespeichert in
            schwellen.py.
******************************************************************************
"""
import logging
import subprocess
import sys
import tkinter as tk
from tkinter import messagebox, Canvas

import cv2
from PIL import Image, ImageTk

from aufnahmen_test import AufnahmenTest
from balken import BalkenZustand
from bildoperation_opencv import Bildoperationen
from camera.camera import Camera
from logger import LoggingConfigurator
from mediapipe_modelle import MediapipeModelle
import schwellen
from sprachausgabe import SprachAusgabe
from uebung import PRESETS, UEBER_ZIEL, einheit
from uebungen import OP_ARTEN, TAG_WIEDERHOLUNGEN, UEBUNGEN
from uebungslauf import Uebungslauf

# Farben der Oberfläche an einer Stelle: ruhig, hoher Kontrast (weiße Schrift auf PRIMAER,
# GEFAHR und AN je über 5:1), Rot nur für Beenden/Stop
HINTERGRUND = "#ffffff"
FLAECHE = "#f1f4f8"         # Karten, z.B. die Zeilen der Schieberegler
KOPF = "#1f4e79"            # Kopfleiste jeder Seite
TEXT = "#1d2125"
TEXT_HELL = "#5d6670"
AUSWAHL = "#d6e6f7"         # angekreuzte Übung
# Tastatur-Fokus (Tab): 2 px Rahmen in KOPF-Blau, ohne Fokus in der Farbe des Hintergrunds unsichtbar
FOKUS = {"highlightthickness": 2, "highlightcolor": KOPF}
KNOPF = {                   # Art -> (Hintergrund, Schrift, Hintergrund beim Drücken)
    "primaer": ("#1f6fb2", "#ffffff", "#185a91"),
    "sekundaer": ("#e3e9f0", TEXT, "#d0d9e4"),
    "gefahr": ("#c62828", "#ffffff", "#a31f1f"),
    "an": ("#2e7d32", "#ffffff", "#256628"),        # eingeschalteter Umschalter (Zoom an, ...)
    "angehalten": ("#f2a900", TEXT, "#d99700"),
}


class SportApp:
    # entwickler: zeigt "Aufnahmen testen" im Hauptmenü (Start mit --entwickler). Im Krankenhaus
    # fehlt der Knopf, dort gibt es die Aufnahmen-Videos nicht (git-ignoriert)
    def __init__(self, window, entwickler=False):
        self.window = window
        self.entwickler = entwickler
        self.window.title("Reha-Sportübungen")
        self.window.configure(bg=HINTERGRUND)
        # Jede Seite bekommt genau die Fenstergröße (sonst wäre sie so breit wie ihr Raster und
        # rechts abgeschnitten: Kopfleiste, mittige Menüs)
        self.window.columnconfigure(0, weight=1)
        self.window.rowconfigure(0, weight=1)

        # Bildschirmgröße abrufen
        self.screen_width = self.window.winfo_screenwidth()
        self.screen_height = self.window.winfo_screenheight()
        self.window.geometry(f"{self.screen_width}x{self.screen_height}")

        # Schriften
        self.title_font = ("Arial", 32)
        self.font = ("Arial", 24)

        # Kameraauflösung, auch für die Aufnahmen (Videos werden darauf skaliert)
        self.resolution = (640, 480)
        self.live_cam = self.open_live_camera()

        self.bild_operationen = Bildoperationen()
        self.lauf = Uebungslauf(MediapipeModelle(), self.bild_operationen, SprachAusgabe(), self.show_video)
        self.aufnahmen_test = AufnahmenTest(self)
        # Schwellen je Übung aus schwellen.json (ohne Datei: Voll), im Auswahlmenü einstellbar
        self.schwellen = schwellen.laden()
        self.schwellen_vars = {}        # je Übung {schwelle: DoubleVar}, gemeinsam für beide OP-Seiten
        self.schwellen_refresh = {}     # je Übung die Anzeigen (Preset, Knopffarben) ihrer Zeilen
        self.setting_schwellen = False

        self.configure_main_menu_frame()
        self.configure_operation_frame()
        # Eine Auswahlseite je OP-Art ("Bauch-OP", "Brust-OP")
        self.auswahl_frames = {op_art: self.configure_auswahl_frame(op_art, namen)
                               for op_art, namen in OP_ARTEN.items()}
        self.configure_exercise_frame()

        # Standardmäßig Hauptmenü anzeigen
        self.show_frame(self.main_menu_frame)


    # Öffnet die Webcam. Liefert wie FileCamera mit getFrame() RGB-Bilder, ungespiegelt
    # (gespiegelt wird nur die Anzeige). None, wenn sie sich nicht öffnen lässt.
    def open_live_camera(self):
        width, height = self.resolution
        try:
            # Kamera 0 für die Standardkamera, 1 für die zweite Kamera usw.
            return Camera(cam_id=0, mirrored=False, frame_width=width, frame_height=height)
        except Exception as e:
            logging.error(f"Kamera konnte nicht geöffnet werden: {e}")
            messagebox.showerror("Kamera", f"Die Kamera konnte nicht geöffnet werden:\n{e}")
            return None


    # Vor jedem Start: fehlt die Kamera (beim Programmstart nicht da) oder ist sie ausgefallen,
    # neu öffnen. So geht es ohne Neustart der App weiter, z.B. nach dem Einstecken der Kamera.
    def ensure_live_camera(self):
        if self.live_cam is not None and not self.live_cam.stop_event.is_set():
            return True
        if self.live_cam is not None:
            self.live_cam.close()
        logging.info("Kamera wird neu geöffnet")
        self.live_cam = self.open_live_camera()     # zeigt bei Fehler die Meldung
        return self.live_cam is not None

    # ---------- Seiten ----------

    # Neue Seite: Kopfleiste mit dem Titel, darunter der Inhalt mit festem Raster. Zurück kommt
    # der Inhalt (darin wird mit grid gebaut); show_frame() hebt die ganze Seite (frame.page).
    # top: Höhe der ersten Rasterzeile, 0 auf der Übungsseite (Video + Balken brauchen die Höhe)
    def new_page(self, title, rows=14, top=50):
        logging.debug(f"Lade GUI-Seite: {title}")
        page = tk.Frame(self.window, bg=HINTERGRUND)
        page.grid(row=0, column=0, sticky="nsew")
        header = tk.Frame(page, bg=KOPF)
        header.pack(fill="x")
        tk.Label(header, text=title, font=("Arial", 26, "bold"), bg=KOPF, fg="white",
                 padx=24, pady=6).pack(side="left")
        tk.Label(header, text="DigiMob · Reha-Übungen", font=("Arial", 16), bg=KOPF, fg="#c9d9ea",
                 padx=24).pack(side="right")
        frame = tk.Frame(page, bg=HINTERGRUND)
        frame.pack(fill="both", expand=True)
        frame.page = page
        for i in range(rows):
            frame.columnconfigure(i, minsize=200)
            frame.rowconfigure(i, minsize=50)
        if rows:
            frame.rowconfigure(0, minsize=top)
        return frame

    # Ein Knopf im Stil der App; kind: Schlüssel von KNOPF
    def button(self, parent, text, command, kind="sekundaer", **grid):
        button = tk.Button(parent, text=text, command=command, font=self.font, height=2, width=20,
                           relief="flat", bd=0, cursor="hand2", highlightbackground=HINTERGRUND, **FOKUS)
        self.style_button(button, kind)
        if grid:
            button.grid(**grid)
        return button

    @staticmethod
    def style_button(button, kind):
        bg, fg, active = KNOPF[kind]
        button.config(background=bg, foreground=fg, activebackground=active, activeforeground=fg)

    # Eine Spalte Knöpfe mittig auf der Seite (Hauptmenü, Operationsart)
    def centered_column(self, frame):
        column = tk.Frame(frame, bg=HINTERGRUND)
        column.place(relx=0.5, rely=0.45, anchor="center")
        return column

    def configure_main_menu_frame(self):
        self.main_menu_frame = frame = self.new_page("Hauptmenü", rows=0)
        column = self.centered_column(frame)
        self.button(column, "Übungen wählen", lambda: self.show_frame(self.operation_frame), "primaer").pack(pady=8)
        self.button(column, "Bedienungsanleitung", self.open_pdf_window).pack(pady=8)
        # Testfunktion: Aufnahmen statt Live-Kamera ("Übungen wählen" ist immer live), nur mit --entwickler
        if self.entwickler:
            self.button(column, "Aufnahmen testen", self.aufnahmen_test.start).pack(pady=8)
        self.button(column, "Beenden", self.quit_program, "gefahr").pack(pady=(48, 8))

    def configure_operation_frame(self):
        self.operation_frame = frame = self.new_page("Operationsart", rows=0)
        column = self.centered_column(frame)
        for op_art in OP_ARTEN:
            self.button(column, op_art, lambda op_art=op_art: self.show_frame(self.auswahl_frames[op_art]),
                        "primaer").pack(pady=8)

        # Postoperativer Tag -> Anzahl Wiederholungen jeder Übung
        tk.Label(column, text="Postoperativer Tag:", font=self.font, bg=HINTERGRUND, fg=TEXT).pack(pady=(40, 4))
        self.selected_day = tk.StringVar(value="Tag 1")
        dropdown = tk.OptionMenu(column, self.selected_day, *TAG_WIEDERHOLUNGEN)
        dropdown.pack(pady=4)
        bg, fg, active = KNOPF["sekundaer"]
        dropdown.config(font=self.font, height=2, width=18, relief="flat", bd=0, highlightbackground=HINTERGRUND, **FOKUS,
                        bg=bg, fg=fg, activebackground=active, cursor="hand2")
        dropdown["menu"].config(font=self.font, bg=HINTERGRUND, fg=TEXT, activebackground=KNOPF["primaer"][0])

        self.button(column, "Zurück", lambda: self.show_frame(self.main_menu_frame)).pack(pady=(48, 8))

    # Eine Auswahlseite je OP-Art: Übungen ankreuzen oder alle nacheinander starten. Neben jeder
    # Übung stehen immer ihre Schwellen als Schieberegler, angekreuzt oder nicht: die gespeicherten
    # gelten auch für "Alle automatisch starten"
    def configure_auswahl_frame(self, op_art, namen):
        frame = self.new_page(f"Nach {op_art}", top=20)    # weniger Abstand oben: alle Zeilen passen auf 1002 px
        tk.Label(frame, text="Übungen wählen:", font=self.font, bg=HINTERGRUND, fg=TEXT).grid(
            row=2, column=0, pady=1, padx=(24, 0), sticky="w")
        checked = {name: tk.BooleanVar(value=False) for name in namen}
        for row, name in enumerate(namen, start=3):
            # Große Auswahlflächen statt der kleinen Kästchen: angekreuzt = hellblau mit Haken
            title = UEBUNGEN[name].title
            box = tk.Checkbutton(frame, variable=checked[name], font=self.font, indicatoron=False,
                                 width=22, anchor="w", padx=12, relief="flat", offrelief="flat", bd=0,
                                 highlightbackground=HINTERGRUND, **FOKUS, cursor="hand2", bg=FLAECHE, fg=TEXT,
                                 selectcolor=AUSWAHL, activebackground=AUSWAHL)
            mark = lambda box=box, title=title, var=checked[name]: box.config(
                text=("✓  " if var.get() else "     ") + title)
            box.config(command=mark)
            mark()
            box.grid(row=row, column=0, pady=3, padx=(24, 0), sticky="w")
            self.schwellen_regler(frame, name).grid(row=row, column=1, columnspan=10, sticky="w", padx=(20, 0))

        self.button(frame, "Speichern und starten",
                    lambda: self.start_exercises([name for name in namen if checked[name].get()]), "primaer",
                    row=12, column=0, pady=5, padx=(24, 0), sticky="w")
        self.button(frame, "Alle automatisch starten", lambda: self.start_exercises(namen), "primaer",
                    row=12, column=1, pady=5, padx=(20, 0), sticky="w")
        self.button(frame, "Zurück", lambda: self.show_frame(self.operation_frame),
                    row=14, column=0, pady=5, padx=(24, 0), sticky="w")
        return frame

    # Die Schwellen einer Übung in einer Zeile: Knöpfe Voll/Einfach, Anzeige des Presets und ein
    # Schieberegler je Schwelle. Die Regler hängen an gemeinsamen Variablen (self.schwellen_vars),
    # die Zeilen beider OP-Seiten zeigen also immer dieselben Werte. Jede gültige Änderung gilt ab
    # dem nächsten Start und wird in schwellen.json gespeichert.
    def schwellen_regler(self, parent, name):
        cls = UEBUNGEN[name]
        small = ("Arial", 12)
        if name not in self.schwellen_vars:
            self.schwellen_vars[name] = {key: tk.DoubleVar(value=value) for key, value in self.schwellen[name].items()}
            self.schwellen_refresh[name] = []
        variables = self.schwellen_vars[name]

        # Eine helle Karte je Zeile; der Regler hat die Farbe der Karte (Tk färbt Regler und
        # Hintergrund gleich), die Schiene ist dunkler
        row = tk.Frame(parent, bg=FLAECHE, padx=8, pady=2)
        buttons = {preset: tk.Button(row, text=preset.capitalize(), font=small, width=7, relief="flat", bd=0,
                                     highlightbackground=FLAECHE, **FOKUS, cursor="hand2", pady=4,
                                     command=lambda preset=preset: self.set_schwellen(name, cls.werte(preset)))
                   for preset in PRESETS}
        for column, button in enumerate(buttons.values()):
            button.grid(row=0, column=column, padx=(0, 5))
        status = tk.Label(row, font=small, width=14, anchor="w", bg=FLAECHE)
        status.grid(row=0, column=2, padx=(0, 10))
        for column, (key, label) in enumerate(cls.schwellen.items(), start=3):
            unit = einheit(key)
            low, high = cls.bereich[key]    # nur Werte, mit denen die Übung zählen kann
            step = {"°": 1, "x": 0.05, "s": 0.05}[unit]
            tk.Scale(row, variable=variables[key], from_=low, to=high, resolution=step, orient="horizontal",
                     length=230, font=small, label=f"{label} ({unit})", width=12, sliderlength=24,
                     bg=FLAECHE, fg=TEXT, troughcolor="#c3cfdc", activebackground=KNOPF["primaer"][0],
                     highlightbackground=FLAECHE, **FOKUS, bd=0, sliderrelief="raised", cursor="hand2",
                     command=lambda value: self.read_schwellen(name)).grid(row=0, column=column, padx=5)

        def refresh(fehler=None):
            # Beide Presets mit diesen Werten (z.B. Seitheben: Einfach = Voll) leuchten beide
            active = [p for p in PRESETS if cls.werte(p) == self.schwellen[name]]
            if fehler:
                status.config(text="ungültig", fg=KNOPF["gefahr"][0])
            else:
                status.config(text=self.preset_text(name), fg=TEXT_HELL)
            for p, button in buttons.items():
                self.style_button(button, "primaer" if p in active and not fehler else "sekundaer")
        self.schwellen_refresh[name].append(refresh)
        refresh()
        return row

    # Mit welchen Schwellen die Übung läuft: "Voll", "Einfach", "Voll = Einfach" oder "angepasst"
    def preset_text(self, name):
        cls = UEBUNGEN[name]
        active = [p for p in PRESETS if cls.werte(p) == self.schwellen[name]]
        return " = ".join(p.capitalize() for p in active) or "angepasst"

    # Ein Regler wurde bewegt: Werte prüfen, übernehmen und speichern
    def read_schwellen(self, name):
        if self.setting_schwellen:      # set_schwellen() stellt die Regler gerade selbst um
            return
        cls = UEBUNGEN[name]
        werte = {key: (int(round(var.get())) if einheit(key) == "°" else round(var.get(), 2))
                 for key, var in self.schwellen_vars[name].items()}
        if werte == self.schwellen[name]:
            return
        fehler = cls.pruefe(werte)
        if fehler:
            # Ungültig (z.B. untere Schwelle über der oberen): nicht übernehmen, Zeile zeigt es
            logging.info(f"Schwellen {name} ungültig: {fehler}")
            for refresh in self.schwellen_refresh[name]:
                refresh(fehler)
            return
        self.set_schwellen(name, werte)

    def set_schwellen(self, name, werte):
        self.schwellen[name] = werte
        self.setting_schwellen = True
        for key, var in self.schwellen_vars[name].items():
            if var.get() != werte[key]:
                var.set(werte[key])
        self.setting_schwellen = False
        try:
            schwellen.speichern(self.schwellen)
        except OSError as e:
            logging.error(f"Schwellen nicht gespeichert: {e}")
            messagebox.showerror("Schwellen", f"Die Schwellen konnten nicht gespeichert werden:\n{e}")
        for refresh in self.schwellen_refresh[name]:
            refresh()

    def configure_exercise_frame(self):
        self.exercise_frame = frame = self.new_page("Reha-Übungen", rows=12, top=0)

        self.button_pause = self.button(frame, "Pause", self.toggle_pause, row=1, column=0, sticky="n", pady=5, padx=5)
        self.button_visualize = self.button(frame, "Keypoints aus", self.toggle_visualize, row=1, column=0, sticky="n", pady=100, padx=5)
        self.zoom_button = self.button(frame, "Zoom aus", self.toggle_zoom, row=1, column=0, sticky="n", pady=200, padx=5)
        self.button(frame, "Stop", self.back_to_menu, "gefahr", row=1, column=0, sticky="s", padx=5)

        # Videobild mit den Balken daneben/darunter, je nach bar_style der Übung:
        # "split" links + rechts, "center" darunter, "single" rechts (wie bisher)
        self.video_frame = video = tk.Frame(frame, bg=HINTERGRUND)
        video.grid(row=1, column=1, columnspan=2, sticky="nw")
        self.video_label = tk.Label(video, bg=HINTERGRUND, bd=0)
        self.video_label.grid(row=0, column=1)
        self.video_photo = None     # das eine PhotoImage, das show_video() füllt

        # Senkrechte Balken: Fortschritt der aktuellen Wiederholung, darüber groß die Zahl ("noch 3").
        # "split" links/rechts aus Sicht des Patienten (die Anzeige ist gespiegelt, seine linke Seite
        # ist links im Bild), schmaler als "single", damit beide neben Knöpfe und Video auf 1920 px passen
        self.y, self.width, self.height = 120, 100, 740     # Balken unter der Zahl, Größe
        self.bar_single = Canvas(video, width=200, height=900, bg="white")
        self.bar_single.grid(row=0, column=3, padx=25, sticky="nsew")
        self.bar_left = Canvas(video, width=130, height=900, bg="white")
        self.bar_left.grid(row=0, column=0, padx=10, sticky="nsew")
        self.bar_right = Canvas(video, width=130, height=900, bg="white")
        self.bar_right.grid(row=0, column=2, padx=10, sticky="nsew")

        # Waagerechter Balken unter dem Video, wächst aus der Mitte nach links/rechts (Kopfdrehung).
        # Flach, damit Video + Balken + Titel in die Fensterhöhe passen und "Stop" sichtbar bleibt
        self.hx, self.hy, self.hwidth, self.hheight = 0, 4, 1200, 26     # Balkenposition und Größe
        self.bar_center = Canvas(video, width=1200, height=34, bg="white", highlightthickness=0)
        self.bar_center.grid(row=1, column=1, pady=(3, 0))

        self.bar_style = None
        self.bar_state = {}     # je Balken ein balken.BalkenZustand (bar_feedback)
        self.bar_drawn = {}     # je Balken: was zuletzt gezeichnet wurde, gleich -> nicht neu zeichnen
        self.update_bar(None, "single")

    # Zeigt das angegebene Frame an
    def show_frame(self, frame):
        getattr(frame, "page", frame).tkraise()     # new_page(): die ganze Seite samt Kopfleiste


    # ---------- Übungen ----------

    # Startet die gewählten Übungen nacheinander, live mit der Webcam
    def start_exercises(self, namen):
        if not namen:
            messagebox.showwarning("Keine Übung ausgewählt", "Bitte wähle mindestens eine Übung aus.")
            return
        if not self.ensure_live_camera():
            return
        day = self.selected_day.get()
        reps = TAG_WIEDERHOLUNGEN[day]
        logging.info(f"Ausgewählter Tag: {day}, Reps: {reps}")

        for name in namen:
            logging.info(f"Schwellen {name}: {self.schwellen[name]}")
        self.lauf.start(self.live_cam, [UEBUNGEN[name](target=reps, schwellen=self.schwellen[name]) for name in namen],
                        on_source_end=self.camera_failed, on_finished=self.back_to_menu)
        self.show_frame(self.exercise_frame)
        self.loop()

    # Die Schleife: ein Tick alle ~10 ms, solange Übungen laufen
    # Ein Fehler in einem Tick darf die Schleife nicht beenden (Tkinter fängt die Ausnahme und
    # druckt sie nur, ohne after() stünde das Bild): ins Log und weiter. Scheitert jeder Tick
    # (FEHLER_IN_FOLGE hintereinander), Übung beenden und melden statt endlos weiterzuversuchen.
    FEHLER_IN_FOLGE = 50
    def loop(self):
        try:
            self.lauf.tick()
            self.loop_errors = 0
        except Exception:
            self.loop_errors = getattr(self, "loop_errors", 0) + 1
            logging.exception(f"Fehler in der Übungsschleife ({self.loop_errors}. in Folge)")
            if self.loop_errors >= self.FEHLER_IN_FOLGE:
                self.loop_errors = 0
                self.lauf.stop()
                messagebox.showerror("Fehler", "Die Übung wurde wegen eines Fehlers beendet (Details in Log.log).")
                self.back_to_menu()
                return
        if self.lauf.running:
            self.window.after(10, self.loop)

    # Bild (BGR) ins Videofeld, Füllstand je Seite in die Balken
    # Ein PhotoImage für alle Bilder, paste() ersetzt nur den Inhalt (Pillow: "paste on a displayed
    # image automatically updates the display"). Jedes Bild neu anzulegen kostete ~60 ms je Frame,
    # so ~30 ms; die Farbe wird vor dem Vergrößern umgerechnet (640x480 statt 1200x900)
    VIDEO_SIZE = (1200, 900)
    def show_video(self, image, balken, bar_style):
        if image is not None:
            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            if (image.shape[1], image.shape[0]) != self.VIDEO_SIZE:
                image = cv2.resize(image, self.VIDEO_SIZE)
            if self.video_photo is None:
                self.video_photo = ImageTk.PhotoImage("RGB", self.VIDEO_SIZE)
                self.video_label.configure(image=self.video_photo)
            self.video_photo.paste(Image.fromarray(image))
        self.update_bar(balken, bar_style)

    # Balken je Seite (uebung.Balken: Füllstand, zurück?, Zahl), None = alles leer (Einführung,
    # "Prima", Stop). bar_style der Übung: "split", "center" oder "single" (siehe uebung.py)
    def update_bar(self, balken, bar_style=None):
        if bar_style and bar_style != self.bar_style:
            self.bar_style = bar_style
            self.bar_drawn.clear()      # andere Balken sichtbar: neu zeichnen
            self.bar_left.grid() if bar_style == "split" else self.bar_left.grid_remove()
            self.bar_right.grid() if bar_style == "split" else self.bar_right.grid_remove()
            self.bar_center.grid() if bar_style == "center" else self.bar_center.grid_remove()
            self.bar_single.grid() if bar_style == "single" else self.bar_single.grid_remove()
            # "center" hat keine Balken neben dem Video: rechts bliebe ~320 px leer, also das Video
            # um die Hälfte davon nach rechts rücken, damit es mittig zwischen Knöpfen und Rand steht
            self.video_frame.grid_configure(padx=(self.CENTER_SHIFT if bar_style == "center" else 0, 0))

        if balken is None:
            self.bar_state.clear()      # nächste Übung: keine Bestätigung für den neuen Zielwert
        values = balken or {}
        if self.bar_style == "split":
            self.draw_vertical(self.bar_left, "left", values.get("left"))
            self.draw_vertical(self.bar_right, "right", values.get("right"))
        elif self.bar_style == "center":
            self.draw_horizontal(values.get("left"), values.get("right"))
        else:
            self.draw_vertical(self.bar_single, "single", next(iter(values.values()), None))

    # Farbverlauf passend zum Füllstand: Gelb (255, 255, 0) -> Grün (0, 128, 0), ab dem Ziel Grün
    @staticmethod
    def bar_color(fill_percentage):
        fill_percentage = min(max(fill_percentage, 0.0), 1.0)
        red = round(255 * (1 - fill_percentage))
        green = round(255 - 127 * fill_percentage)
        return f"#{red:02x}{green:02x}00"

    # Was der Balken außer dem Füllstand zeigt (Bestätigung, kurz Stehenlassen, höchster Punkt):
    # balken.BalkenZustand, einer je Balken
    CENTER_SHIFT = 160      # px, Video nach rechts bei bar_style "center" (Halswirbelsäule)
    def bar_feedback(self, key, b):
        return self.bar_state.setdefault(key, BalkenZustand()).update(b)

    # Senkrechter Balken: oben "noch" und groß die Zahl, darunter der Balken, der sich von unten füllt.
    # Die Ziellinie liegt bei 1/UEBER_ZIEL der Höhe, darüber geht der Balken noch etwas weiter.
    # Pfeil nach unten = Ziel erreicht, jetzt zurück; Häkchen = Wiederholung gezählt; blaue Linie =
    # höchster Punkt
    def bar_x(self, canvas):
        return (int(canvas["width"]) - self.width) // 2

    def draw_vertical(self, canvas, key, b):
        x, w, top, h = self.bar_x(canvas), self.width, self.y, self.height
        bottom, cw, cx = top + h, int(canvas["width"]), self.bar_x(canvas) + self.width / 2
        level = lambda fill: bottom - h * min(fill, UEBER_ZIEL) / UEBER_ZIEL
        fill, zurueck, flash, peak = self.bar_feedback(key, b)
        # Nur neu zeichnen, wenn sich etwas Sichtbares geändert hat (in Ruhe meist nicht)
        drawn = (None if fill is None else round(level(fill)), zurueck, flash,
                 None if peak is None else round(level(peak)), b and (b.zahl, b.wort))
        if self.bar_drawn.get(key) == drawn:
            return
        self.bar_drawn[key] = drawn
        canvas.delete("bar")

        if flash:
            canvas.create_rectangle(x, top, x + w, bottom, fill="#c8f0c8", outline="", tags="bar")
        if fill is not None and fill > 0:
            canvas.create_rectangle(x, level(fill), x + w, bottom, fill=self.bar_color(fill), outline="", tags="bar")
        if peak is not None:
            canvas.create_line(x, level(peak), x + w, level(peak), width=6, fill="#1f3c88", tags="bar")
        canvas.create_rectangle(x, top, x + w, bottom, outline="black", width=2, tags="bar")

        # Ziellinie über die ganze Breite, mit Spitzen am Rand
        y = level(1.0)
        canvas.create_line(0, y, cw, y, width=5, fill="black", tags="bar")
        canvas.create_polygon(0, y - 12, 14, y, 0, y + 12, fill="black", tags="bar")
        canvas.create_polygon(cw, y - 12, cw - 14, y, cw, y + 12, fill="black", tags="bar")

        if flash:
            self.draw_check(canvas, cx, (top + bottom) / 2, w * 0.8)
        elif zurueck:
            self.draw_arrow_down(canvas, cx, (y + bottom) / 2, w * 0.8)
        if b is not None:
            canvas.create_text(cw / 2, 24, text=b.wort, font=("Arial", 18), tags="bar")
            canvas.create_text(cw / 2, 78, text=str(b.zahl), font=("Arial", 56, "bold"), tags="bar")

    # Häkchen und Pfeil als Formen, nicht als Zeichen: sehen mit jeder Schrift gleich aus
    @staticmethod
    def draw_check(canvas, cx, cy, size):
        points = (cx - size / 2, cy, cx - size / 6, cy + size * 0.35, cx + size / 2, cy - size * 0.35)
        for color, width in (("white", max(8, size / 4)), ("#006400", max(4, size / 7))):  # weißer Rand: auf Grün sichtbar
            canvas.create_line(*points, width=width, fill=color, capstyle="round", joinstyle="round", tags="bar")

    @staticmethod
    def draw_arrow_down(canvas, cx, cy, size):
        s, shaft = size / 2, size / 6
        canvas.create_polygon(cx - shaft, cy - s * 1.6, cx + shaft, cy - s * 1.6, cx + shaft, cy, cx + s, cy,
                              cx, cy + s * 1.1, cx - s, cy, cx - shaft, cy, fill="#202020", outline="white",
                              width=3, tags="bar")

    # Waagerechter Balken (Kopfdrehung): linke Seite füllt von der Mitte nach links, rechte nach
    # rechts, je eine Ziellinie; an den Enden die Zahl je Seite
    def draw_horizontal(self, left, right):
        c = self.bar_center
        middle, half, top, bottom = self.hx + self.hwidth // 2, self.hwidth // 2, self.hy, self.hy + self.hheight
        sides = [(b, direction, *self.bar_feedback(key, b)) for b, direction, key in
                 ((left, -1, "center_left"), (right, 1, "center_right"))]
        drawn = tuple((None if fill is None else round(half * min(fill, UEBER_ZIEL)), flash,
                       None if peak is None else round(half * min(peak, UEBER_ZIEL)), b and (b.zahl, b.wort))
                      for b, _, fill, _, flash, peak in sides)
        if self.bar_drawn.get("center") == drawn:
            return
        self.bar_drawn["center"] = drawn
        c.delete("bar")
        for b, direction, fill, _, flash, peak in sides:
            at = lambda f, direction=direction: middle + direction * int(half * min(f, UEBER_ZIEL) / UEBER_ZIEL)
            if flash:
                c.create_rectangle(min(middle, at(UEBER_ZIEL)), top, max(middle, at(UEBER_ZIEL)), bottom,
                                   fill="#c8f0c8", outline="", tags="bar")
            if fill is not None and fill > 0:
                c.create_rectangle(min(middle, at(fill)), top, max(middle, at(fill)), bottom,
                                   fill=self.bar_color(fill), outline="", tags="bar")
            if peak is not None:
                c.create_line(at(peak), top, at(peak), bottom, width=5, fill="#1f3c88", tags="bar")
            c.create_line(at(1.0), 0, at(1.0), bottom + self.hy, width=4, fill="black", tags="bar")
            if b is not None:
                c.create_text(8 if direction < 0 else self.hwidth - 8, (top + bottom) / 2, text=f"{b.wort} {b.zahl}",
                              anchor="w" if direction < 0 else "e", font=("Arial", 16, "bold"), tags="bar")
                if flash:
                    self.draw_check(c, at(1.0) + direction * 30, (top + bottom) / 2, self.hheight * 0.9)
        c.create_rectangle(self.hx, top, self.hx + self.hwidth - 1, bottom, outline="black", tags="bar")
        c.create_line(middle, 0, middle, bottom + self.hy, width=3, tags="bar")

    # Live-Kamera liefert keine Bilder (mehr): Verarbeitung anhalten, das letzte Bild bleibt
    # stehen, "Stop" führt zurück
    def camera_failed(self, uebung):
        self.lauf.stop()
        if self.live_cam is None:
            messagebox.showerror("Kamera", "Die Kamera konnte nicht geöffnet werden.")
        else:
            logging.error("Die Kamera liefert keine Bilder mehr")
            messagebox.showerror("Kamera", "Die Kamera liefert keine Bilder mehr.")

    # "Stop", alle Übungen fertig oder Aufnahmen-Test zu Ende: zurück ins Hauptmenü
    def back_to_menu(self):
        self.lauf.stop()
        if self.lauf.paused:
            self.toggle_pause()
        if self.aufnahmen_test.active:
            self.aufnahmen_test.end()
        self.video_photo = None
        self.video_label.configure(image="")
        self.update_bar(None)
        self.show_frame(self.main_menu_frame)
        logging.info("Die Übungen werden beendet, zurück zum Hauptmenü")


    # ---------- Knöpfe der Übungsseite ----------

    def toggle_pause(self):
        self.lauf.paused = not self.lauf.paused
        if self.lauf.paused:
            self.button_pause.config(text="Angehalten")
            self.style_button(self.button_pause, "angehalten")
            logging.info("Die Übung wird angehalten")
        else:
            self.button_pause.config(text="Pause")
            self.style_button(self.button_pause, "sekundaer")
            logging.info("Die Übung wird weiter durchgeführt")

    def toggle_zoom(self):
        if self.bild_operationen.zoom_factor <= 1.0:
            self.bild_operationen.zoom_factor = 2.0
            self.zoom_button.config(text="Zoom an")
            self.style_button(self.zoom_button, "an")
        else:
            self.bild_operationen.zoom_factor = 1.0
            self.zoom_button.config(text="Zoom aus")
            self.style_button(self.zoom_button, "sekundaer")
        logging.info(f"Zoom Factor = {self.bild_operationen.zoom_factor}")

    # Keypoints ins Videobild zeichnen
    def toggle_visualize(self):
        self.bild_operationen.show_keypoints = not self.bild_operationen.show_keypoints
        if self.bild_operationen.show_keypoints:
            self.button_visualize.config(text="Keypoints an")
            self.style_button(self.button_visualize, "an")
            logging.info("Keypoints an")
        else:
            self.button_visualize.config(text="Keypoints aus")
            self.style_button(self.button_visualize, "sekundaer")
            logging.info("Keypoints aus")


    # ---------- Sonstiges ----------

    def open_pdf_window(self):
        subprocess.call(["xdg-open", "Bedienungsanleitung/Bedienungsanleitung.pdf"])

    # Beendet das Programm und schließt die Kamera
    def quit_program(self):
        self.lauf.stop()
        if self.aufnahmen_test.active:
            self.aufnahmen_test.end()
        if self.live_cam:
            self.live_cam.close()
            self.live_cam = None
        self.window.destroy()


if __name__ == "__main__":
    logger_config = LoggingConfigurator()
    logger_config.setLoggingMode(1)

    window = tk.Tk()
    app = SportApp(window, entwickler="--entwickler" in sys.argv)

    window.mainloop()
