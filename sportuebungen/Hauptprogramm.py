"""
******************************************************************************
 file:      Hauptprogramm.py
 brief:     Software für Sportübungen: die Oberfläche (Seiten, Knöpfe, Videobild, Balken)
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.11.2)
 int. libs: tkinter
 ext. libs: OpenCV (4.11.0.86)
            pillow (11.1.0)
 info:      Die Übungen laufen in uebungslauf.py, "Aufnahmen testen" in aufnahmen_test.py,
            die Liste der Übungen steht in uebungen.py.
******************************************************************************
"""
import logging
import subprocess
import tkinter as tk
from tkinter import ttk, messagebox, Canvas

import cv2
from PIL import Image, ImageTk

from aufnahmen_test import AufnahmenTest
from bildoperation_opencv import Bildoperationen
from camera.camera import Camera
from logger import LoggingConfigurator
from mediapipe_modelle import MediapipeModelle
from sprachausgabe import SprachAusgabe
from uebungen import OP_ARTEN, TAG_WIEDERHOLUNGEN, UEBUNGEN
from uebungslauf import Uebungslauf


class SportApp:
    def __init__(self, window):
        self.window = window
        self.window.title("Reha-Sportübungen")

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


    # ---------- Seiten ----------

    # Neue Seite mit Überschrift und festem Raster
    def new_page(self, title, rows=14):
        logging.debug(f"Lade GUI-Seite: {title}")
        frame = ttk.Frame(self.window)
        frame.grid(row=0, column=0, sticky="nsew")
        ttk.Label(frame, text=title, font=self.title_font).grid(row=0, column=0, pady=5)
        for i in range(rows):
            frame.columnconfigure(i, minsize=200)
            frame.rowconfigure(i, minsize=50)
        return frame

    # Ein Knopf im Stil der App
    def button(self, parent, text, command, color="lightyellow", **grid):
        button = tk.Button(parent, text=text, command=command, font=self.font,
                           height=2, width=20, background=color)
        button.grid(**grid)
        return button

    def configure_main_menu_frame(self):
        self.main_menu_frame = frame = self.new_page("Hauptmenü")
        self.button(frame, "Übungen wählen", lambda: self.show_frame(self.operation_frame), row=7, column=0, pady=5, sticky="w")
        self.button(frame, "Bedienungsanleitung", self.open_pdf_window, row=8, column=0, pady=5, sticky="w")
        # Testfunktion: Aufnahmen statt Live-Kamera ("Übungen wählen" ist immer live)
        self.button(frame, "Aufnahmen testen", self.aufnahmen_test.start, "light grey", row=12, column=0, pady=5, sticky="w")
        self.button(frame, "Beenden", self.quit_program, "red", row=13, column=0, pady=5, sticky="w")

    def configure_operation_frame(self):
        self.operation_frame = frame = self.new_page("Operationsart")
        for row, op_art in enumerate(OP_ARTEN, start=2):
            self.button(frame, op_art, lambda op_art=op_art: self.show_frame(self.auswahl_frames[op_art]), row=row, column=0, pady=5)
        self.button(frame, "Zurück", lambda: self.show_frame(self.main_menu_frame), "lightblue", row=14, column=0, pady=5)

        # Postoperativer Tag -> Anzahl Wiederholungen jeder Übung
        tk.Label(frame, text="Postoperativer Tag:", font=self.font).grid(row=6, column=0, pady=5)
        self.selected_day = tk.StringVar(value="Tag 1")
        dropdown = tk.OptionMenu(frame, self.selected_day, *TAG_WIEDERHOLUNGEN)
        dropdown.grid(row=7, column=0, pady=5)
        dropdown.config(font=self.font, height=2, width=18)
        dropdown["menu"].config(font=self.font)

    # Eine Auswahlseite je OP-Art: Übungen ankreuzen oder alle nacheinander starten
    def configure_auswahl_frame(self, op_art, namen):
        frame = self.new_page(f"Nach {op_art}")
        tk.Label(frame, text="Übungen wählen:", font=self.font).grid(row=2, column=0, pady=1, sticky="w")
        checked = {name: tk.BooleanVar(value=False) for name in namen}
        for name in namen:
            tk.Checkbutton(frame, text=UEBUNGEN[name].title, variable=checked[name], font=self.font).grid(column=0, pady=1, sticky="w")

        self.button(frame, "Speichern und starten",
                    lambda: self.start_exercises([name for name in namen if checked[name].get()]), row=12, column=0, pady=5)
        self.button(frame, "Alle automatisch starten", lambda: self.start_exercises(namen), row=12, column=1, pady=5)
        self.button(frame, "Zurück", lambda: self.show_frame(self.operation_frame), "lightblue", row=14, column=0, pady=5)
        return frame

    def configure_exercise_frame(self):
        self.exercise_frame = frame = self.new_page("Reha-Übungen", rows=12)

        self.button_pause = self.button(frame, "Pause", self.toggle_pause, "light yellow", row=1, column=0, sticky="n", pady=5, padx=5)
        self.button_visualize = self.button(frame, "Keypoints aus", self.toggle_visualize, "light yellow", row=1, column=0, sticky="n", pady=100, padx=5)
        self.zoom_button = self.button(frame, "Zoom aus", self.toggle_zoom, "light yellow", row=1, column=0, sticky="n", pady=200, padx=5)
        self.button(frame, "Stop", self.back_to_menu, "orange red", row=1, column=0, sticky="s")

        # Videobild mit den Balken daneben/darunter, je nach bar_style der Übung:
        # "split" links + rechts, "center" darunter, "single" rechts (wie bisher)
        video = tk.Frame(frame)
        video.grid(row=1, column=1, columnspan=2, sticky="nw")
        self.video_label = tk.Label(video)
        self.video_label.grid(row=0, column=1)

        # Senkrechte Balken: Fortschritt der aktuellen Wiederholung. "split" links/rechts aus Sicht
        # des Patienten (die Anzeige ist gespiegelt, seine linke Seite ist links im Bild), schmaler
        # als "single", damit beide neben Knöpfe und Video auf 1920 px passen
        self.y, self.width, self.height = 50, 100, 800      # Balkenposition und Größe
        self.bar_single = Canvas(video, width=200, height=800, bg="white")
        self.bar_single.grid(row=0, column=3, padx=25, sticky="nsew")
        self.bar_left = Canvas(video, width=130, height=800, bg="white")
        self.bar_left.grid(row=0, column=0, padx=10, sticky="nsew")
        self.bar_right = Canvas(video, width=130, height=800, bg="white")
        self.bar_right.grid(row=0, column=2, padx=10, sticky="nsew")
        for canvas in (self.bar_single, self.bar_left, self.bar_right):
            x = self.bar_x(canvas)
            canvas.create_rectangle(x, self.y, x + self.width, self.y + self.height, outline="black")

        # Waagerechter Balken unter dem Video, wächst aus der Mitte nach links/rechts (Kopfdrehung).
        # Flach, damit Video + Balken + Titel in die Fensterhöhe passen und "Stop" sichtbar bleibt
        self.hx, self.hy, self.hwidth, self.hheight = 0, 4, 1200, 26     # Balkenposition und Größe
        self.bar_center = Canvas(video, width=1200, height=34, bg="white", highlightthickness=0)
        self.bar_center.grid(row=1, column=1, pady=(3, 0))
        self.bar_center.create_rectangle(self.hx, self.hy, self.hx + self.hwidth - 1, self.hy + self.hheight, outline="black")
        middle = self.hx + self.hwidth // 2
        self.bar_center.create_line(middle, 0, middle, self.hy * 2 + self.hheight, width=3, tags="middle")

        self.bar_style = None
        self.update_bar(None, "single")

    # Zeigt das angegebene Frame an
    def show_frame(self, frame):
        frame.tkraise()


    # ---------- Übungen ----------

    # Startet die gewählten Übungen nacheinander, live mit der Webcam
    def start_exercises(self, namen):
        if not namen:
            messagebox.showwarning("Keine Übung ausgewählt", "Bitte wähle mindestens eine Übung aus.")
            return
        day = self.selected_day.get()
        reps = TAG_WIEDERHOLUNGEN[day]
        logging.info(f"Ausgewählter Tag: {day}, Reps: {reps}")

        self.lauf.start(self.live_cam, [UEBUNGEN[name](target=reps) for name in namen],
                        on_source_end=self.camera_failed, on_finished=self.back_to_menu)
        self.show_frame(self.exercise_frame)
        self.loop()

    # Die Schleife: ein Tick alle ~10 ms, solange Übungen laufen
    def loop(self):
        self.lauf.tick()
        if self.lauf.running:
            self.window.after(10, self.loop)

    # Bild (BGR) ins Videofeld, Füllstand je Seite in die Balken
    def show_video(self, image, balken, bar_style):
        if image is not None:
            image = cv2.resize(image, (1200, 900))            # Bildgröße anpassen
            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            imgtk = ImageTk.PhotoImage(image=Image.fromarray(image))
            self.video_label.imgtk = imgtk
            self.video_label.configure(image=imgtk)
        self.update_bar(balken, bar_style)

    # Füllstand je Seite, z.B. {"left": 0..1, "right": 0..1}; None (kein Messwert) = leere Balken.
    # bar_style der Übung: "split", "center" oder "single" (siehe uebung.py)
    def update_bar(self, balken, bar_style=None):
        if bar_style and bar_style != self.bar_style:
            self.bar_style = bar_style
            self.bar_left.grid() if bar_style == "split" else self.bar_left.grid_remove()
            self.bar_right.grid() if bar_style == "split" else self.bar_right.grid_remove()
            self.bar_center.grid() if bar_style == "center" else self.bar_center.grid_remove()
            self.bar_single.grid() if bar_style == "single" else self.bar_single.grid_remove()

        values = balken or {}
        if self.bar_style == "split":
            self.draw_vertical(self.bar_left, values.get("left"))
            self.draw_vertical(self.bar_right, values.get("right"))
        elif self.bar_style == "center":
            self.draw_horizontal(values.get("left"), values.get("right"))
        else:
            self.draw_vertical(self.bar_single, next(iter(values.values()), None))

    # Farbverlauf passend zum Füllstand: Gelb (255, 255, 0) -> Grün (0, 128, 0)
    @staticmethod
    def bar_color(fill_percentage):
        red = round(255 * (1 - fill_percentage))
        green = round(255 - 127 * fill_percentage)
        return f"#{red:02x}{green:02x}00"

    # Füllstand 0..1 (None = leer), auf 0..1 begrenzt
    @staticmethod
    def clamp(value):
        return min(max(value or 0, 0.0), 1.0)

    # Senkrechte Balken stehen mittig in ihrem Canvas
    def bar_x(self, canvas):
        return (int(canvas["width"]) - self.width) // 2

    # Senkrechter Balken, füllt sich von unten
    def draw_vertical(self, canvas, value):
        fill_percentage = self.clamp(value)
        fill_height = int(self.height * fill_percentage)
        x = self.bar_x(canvas)
        canvas.delete("bar")
        canvas.create_rectangle(
            x, self.y + (self.height - fill_height), x + self.width, self.y + self.height,
            fill=self.bar_color(fill_percentage), tags="bar"
        )

    # Waagerechter Balken: linke Seite füllt von der Mitte nach links, rechte nach rechts
    def draw_horizontal(self, left, right):
        middle, half = self.hx + self.hwidth // 2, self.hwidth // 2
        self.bar_center.delete("bar")
        for value, direction in ((left, -1), (right, 1)):
            fill_percentage = self.clamp(value)
            end = middle + direction * int(half * fill_percentage)
            self.bar_center.create_rectangle(
                min(middle, end), self.hy, max(middle, end), self.hy + self.hheight,
                fill=self.bar_color(fill_percentage), tags="bar"
            )
        self.bar_center.tag_raise("middle")

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
        self.video_label.imgtk = None
        self.video_label.configure(image="")
        self.update_bar(None)
        self.show_frame(self.main_menu_frame)
        logging.info("Die Übungen werden beendet, zurück zum Hauptmenü")


    # ---------- Knöpfe der Übungsseite ----------

    def toggle_pause(self):
        self.lauf.paused = not self.lauf.paused
        if self.lauf.paused:
            self.button_pause.config(relief="sunken", text="Angehalten", background="yellow")
            logging.info("Die Übung wird angehalten")
        else:
            self.button_pause.config(relief="raised", text="Pause", background="light yellow")
            logging.info("Die Übung wird weiter durchgeführt")

    def toggle_zoom(self):
        if self.bild_operationen.zoom_factor <= 1.0:
            self.bild_operationen.zoom_factor = 2.0
            self.zoom_button.config(text="Zoom an", bg="light green")
        else:
            self.bild_operationen.zoom_factor = 1.0
            self.zoom_button.config(text="Zoom aus", bg="light yellow")
        logging.info(f"Zoom Factor = {self.bild_operationen.zoom_factor}")

    # Keypoints ins Videobild zeichnen
    def toggle_visualize(self):
        self.bild_operationen.show_keypoints = not self.bild_operationen.show_keypoints
        if self.bild_operationen.show_keypoints:
            self.button_visualize.config(text="Keypoints an", background="light green")
            logging.info("Keypoints an")
        else:
            self.button_visualize.config(text="Keypoints aus", background="light yellow")
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
    app = SportApp(window)

    window.mainloop()
