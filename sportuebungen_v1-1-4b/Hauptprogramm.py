"""
******************************************************************************
 file:      Hauptprogramm.py
 brief:     Software für Sportübungen
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.4.1
 date:      12.05.2025
 software:  Python (Version: 3.11.2)
 int. libs: time
            tkinter
 ext. libs: OpenCV (4.11.0.86)
            pillow (11.1.0)
            pymupdf (1.20.2)
            piper-tts (1.2.0)
            numpy (1.26.4)
            sounddevice (0.5.1)
 info:      
******************************************************************************
"""
import subprocess
import time
import tkinter as tk
from tkinter import ttk, messagebox, Canvas

import cv2
from PIL import Image, ImageTk
from piper import PiperVoice
import sprachausgabe

from abduktion import AbduktionExercise
from bildoperation_opencv import Bildoperationen
from bizepscurls import	BizepscurlsExercise
from butterfly import ButterflyExercise
from flexion_schulter import Flexion_SchulterbenExercise
from halswirbelsaeule import HalswirbelsaeuleExercise
from initialisierung import camera_start
from mediapipe_modelle import MediapipeModelle
from mobilisation_schulter import Mobilisation_SchulterExercise
from rotation import RotationExercise
from seitenheben import	SeitenhebenExercise


class SportApp:
    def __init__(self, window):
        self.window = window
        self.window.title("Reha-Sportübungen")

        # Bildschirmgröße abrufen
        self.screen_width = self.window.winfo_screenwidth()
        self.screen_height = self.window.winfo_screenheight()
        self.window.geometry(f"{self.screen_width}x{self.screen_height}")

        # Standard-Wert für Zoom-Funktion
        self.zoom_factor = 1.0

        # Frames erstellen
        self.create_frame()
        # Initialisiere die Module
        self.initialize_module()
        # Hauptmenü konfigurieren     
        self.configure_main_menu_frame()
        # Einstellungen_Ansicht konfigurieren   
        self.configure_settings_frame()
        # Übungsansicht konfigurieren   
        self.configure_exercise_frame()
        # Operation_ansicht konfigurieren  
        self.configure_operation_frame()
        # Bauch_ansicht konfigurieren  
        self.configure_belly_frame()
        # Brust_ansicht konfigurieren  
        self.configure_breast_frame()
        # Standardmäßig Hauptmenü anzeigen
        self.show_frame(self.main_menu_frame)
        # Kamera-Status
        self.running = False
        self.processing = False
        self.start_time = None
        self.value= 0
        self.pdf_images = []
        self.exercise_pause = False

        self.current_zoom = 1.0

        # Set default voice name ("Thorsten") -> see line 148 
        sprachausgabe.default_voice_model = "de_DE-thorsten-low.onnx"
        sprachausgabe.voice = PiperVoice.load(sprachausgabe.default_voice_model)

        self.last_time_frame = time.perf_counter()
    
    # Frames erstellen
    def create_frame(self):
        self.main_menu_frame = ttk.Frame(self.window)
        self.exercise_frame = ttk.Frame(self.window)
        self.operation_frame = ttk.Frame(self.window)
        self.settings_frame = ttk.Frame(self.window)
        self.belly_frame = ttk.Frame(self.window)
        self.breast_frame = ttk.Frame(self.window)
        for frame in (self.main_menu_frame, self.exercise_frame,self.operation_frame,self.settings_frame,self.belly_frame,self.breast_frame):
            frame.grid(row=0, column=0, sticky="nsew")


    # Initialisiere die Module
    def initialize_module(self):
        self.cap, self.resolution = camera_start()

        self.bild_operationen = Bildoperationen(self.resolution, self.cap)
        self.bild_operationen.zoom_factor = self.zoom_factor

        self.mediapipe_modelle = MediapipeModelle (self.bild_operationen)
        self.abduktion_exercise = AbduktionExercise (self.mediapipe_modelle, self.bild_operationen)
        self.seitenheben_exercise = SeitenhebenExercise(self.mediapipe_modelle, self.bild_operationen)
        self.bizepscurls_exercise = BizepscurlsExercise(self.mediapipe_modelle, self.bild_operationen)
        self.flexion_schulter_exercise =Flexion_SchulterbenExercise(self.mediapipe_modelle, self.bild_operationen)
        self.halswirbelsaeule_exercise = HalswirbelsaeuleExercise(self.mediapipe_modelle, self.bild_operationen)
        self.rotation_exercise = RotationExercise(self.mediapipe_modelle, self.bild_operationen)
        self.butterfly_exercise = ButterflyExercise(self.mediapipe_modelle, self.bild_operationen)
        self.mobilisation_schulter_exercise = Mobilisation_SchulterExercise(self.mediapipe_modelle, self.bild_operationen)


    # Hauptansicht konfigurieren
    def configure_main_menu_frame(self):
        tk.Label(self.main_menu_frame, text="Hauptmenü", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(14):
            self.main_menu_frame.columnconfigure(i, minsize=200)
            self.main_menu_frame.rowconfigure(i, minsize=50)

        self.button_start = tk.Button(self.main_menu_frame, text="Übungen wählen", font=("Arial", 24), height=2, width=20, background="lightyellow",command=lambda: self.show_frame(self.operation_frame))
        self.button_start.grid(row=2, column=0, pady=5, sticky="w")
        self.button_instructions = tk.Button(self.main_menu_frame, text="Bedienungsanleitung", font=("Arial", 24), height=2, width=20, background="lightyellow", command=self.open_pdf_window)
        self.button_instructions.grid(row=3, column=0, pady=5, sticky="w")
        self.button_settings = tk.Button(self.main_menu_frame, text="Einstellungen", font=("Arial", 24), height=2, width=20, background="lightyellow", command=lambda: self.show_frame(self.settings_frame))
        self.button_settings.grid(row=4, column=0, pady=5, sticky="w")
        self.button_exit = tk.Button(self.main_menu_frame, text="Beenden", font=("Arial", 24), height=2, width=20, background="red",command=self.quit_program)
        self.button_exit.grid(row=14, column=0, pady=5, sticky="w")


    # Einstellungen_Ansicht konfigurieren  
    def configure_settings_frame(self):
        ttk.Label(self.settings_frame, text="Einstellungen", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(14):
            self.settings_frame.columnconfigure(i, minsize=200)
            self.settings_frame.rowconfigure(i, minsize=50)

        # Auswahl der TTS-Stimmen
        tk.Label(self.settings_frame, text="Stimme wählen:", font=("Arial", 24)).grid(row=3, column=0, pady=5, sticky="w")
     
        # Zuordnung der Stimmennamen zu den Modellen
        self.voice_values = {"Eva":"de_DE-eva_k-x_low.onnx",
                            "Kerstin":"de_DE-kerstin-low.onnx",
                            "Ramona":"de_DE-ramona-low.onnx",
                            "Karlsson":"de_DE-karlsson-low.onnx",
                            "Thorsten":"de_DE-thorsten-low.onnx",
                            "Pavoque":"de_DE-pavoque-low.onnx"}

        # Standardwert für das Dropdown-Menü
        self.voice_name = tk.StringVar(value="Thorsten")

        # Dropdown-Menü erstellen
        self.voice_dropdown = tk.OptionMenu(self.settings_frame, self.voice_name, *self.voice_values.keys(), command=self.voice_changed)        
        self.voice_dropdown.grid(row=4, column=0, pady=5, sticky="w")
        self.voice_dropdown.config(font=("Arial", 24), height=2, width=20)
        self.voice_dropdown['menu'].config(font=("Arial", 24))

        # Voice-Test-Button erstellen
        self.button_voice_test = tk.Button(self.settings_frame, text="Anhören", font=("Arial", 24), height=2, width=20, background="lightyellow",command=self.voice_test)
        self.button_voice_test.grid(row=4, column=2, pady=5, sticky="w")

        # Visualisierung-Button
        self.button_visualize = tk.Button(self.settings_frame, text="Keypoints aus", font=("Arial", 24), height=2, width=20, background="light yellow", command=self.toggle_visualize)
        self.button_visualize.grid(row=5, column=0, sticky="n", pady=5)

        # Label für Info
        #ttk.Label(self.settings_frame, text="Aktuell keine weiteren Optionen verfügbar.", font=("Arial", 24)).grid(row=8, column=1, pady=5, sticky="w")
        
        # Zurück-Button
        self.button_back = tk.Button(self.settings_frame, text="Zurück", font=("Arial", 24), height=2, width=20, background="lightblue",command=lambda: self.show_frame(self.main_menu_frame))
        self.button_back.grid(row=12, column=0, pady=5, sticky="w")


    def voice_changed(self, *args):
        voice_name = str(self.voice_name.get())
        voice_model = str(self.voice_values.get(voice_name))
        print(f"Stimme gewählt: {voice_name}")
        print(f"Model gewählt: {voice_model}")
        sprachausgabe.voice = PiperVoice.load(voice_model)
        print("Stimme wurde geladen.")


    # zum Testen der ausgewählten Stimme
    def voice_test(self):
        text = f"Das ist die Stimme von {self.voice_name.get()}."
        print(text)
        sprachausgabe.speak(sprachausgabe.voice, text)


    # Übungsansicht konfigurieren   
    def configure_exercise_frame(self):
        ttk.Label(self.exercise_frame,text="Reha-Übungen", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(12):
            self.exercise_frame.columnconfigure(i, minsize=200)
            self.exercise_frame.rowconfigure(i, minsize=50)

        # Pause-Button
        self.button_pause = tk.Button(self.exercise_frame, text="Pause", font=("Arial", 24), height=2, width=20, background="light yellow", relief="raised", command=self.toggle_pause_button)
        self.button_pause.grid(row=1, column=0, sticky="n", pady=5, padx=5)

        # # Zoom-Button
        self.zoom_slider = tk.Scale(self.exercise_frame,
                                    from_=1.0, to=2.0, resolution=0.1,
                                    orient="horizontal", label="Zoom Factor",
                                    length=300,              # Länge des Sliders (in Pixel)
                                    #width=30,                # Breite des Sliders
                                    sliderlength=50,         # Größe des Schiebereglers (Knopf)
                                    troughcolor="white",   # Farbe der Hintergrundrinne
                                    fg="black",              # Schriftfarbe
                                    bg="yellow",
                                    font=("Arial", 24),
                                    command=self.set_zoom)
        self.zoom_slider.set(1.0)  # Startposition vom Slider
        self.zoom_slider.grid(row=1, column=0, sticky="n", pady=200, padx=5)
        
        # self.zoom_button = tk.Button(self.exercise_frame,
        #                      text="Zoom aus",
        #                      font=("Arial", 24),
        #                      fg="black",
        #                      bg="light yellow",
        #                      height=2, width=20,
        #                      command=self.toggle_zoom)
        # self.zoom_button.grid(row=1, column=0, sticky="n", pady=200, padx=5)        


        # # Visualisierung-Button
        # self.button_visualize = tk.Button(self.exercise_frame, text="Keypoint : anzeigen", font=("Arial", 24), height=2, width=20, background="light green", command=self.toggle_visualize)
        # self.button_visualize.grid(row=1, column=0, sticky="n", pady=100, padx=5)

        # Stop-Button
        self.button_back = tk.Button(self.exercise_frame, text="Stop", font=("Arial", 24), height=2, width=20, background="orange red", command=self.abort_exercise_and_return)
        self.button_back.grid(row=1, column=0, sticky="s")

        # Video-Label
        self.video_label = tk.Label(self.exercise_frame)
        self.video_label.grid(row=1, column=1)

        #canvas von Tkinter Balken mit grünem und roten Bereich für Visualisierung der Messwerte
        self.canvas = Canvas(self.exercise_frame, width=200, height=800, bg="white")
        self.canvas.grid(row=1, column=2, padx=25, sticky="nsew")
        self.x, self.y, self.width, self.height = 50, 50, 100, 800  # Balkenposition und Größe
        self.canvas.create_rectangle(self.x, self.y, self.x + self.width, self.y + self.height, outline="black")
        self.balken_zahl = 0

    def set_zoom(self, val):
        self.zoom_factor = float(val)
        self.bild_operationen.zoom_factor = self.zoom_factor
    
    def toggle_zoom(self):
        if self.current_zoom == 1.0:
            self.current_zoom = 2.0
            self.zoom_button.config(text="Zoom an", bg="yellow")
        else:
            self.current_zoom = 1.0
            self.zoom_button.config(text="Zoom aus", bg="light yellow")

        self.set_zoom(self.current_zoom)
        
    def abort_exercise_and_return(self):
        if self.exercise_pause == True:
            self.button_pause.invoke()
        print(self.exercise_pause)
        self.stop_camera()
        self.reset_bargraph()
        self.reset_exercises()
        self.show_frame(self.main_menu_frame)


    def toggle_pause_button(self):
        if self.button_pause.config('relief')[-1] == "raised":
            self.button_pause.config(relief="sunken", text="Angehalten", background="yellow")
            self.exercise_pause = True
        else:
            self.exercise_pause = False
            self.button_pause.config(relief="raised",  text="Pause", background="light yellow")

    # Toggle für Visualisierung der Keypoints
    def toggle_visualize(self):
        self.mediapipe_modelle.visualize_active = not self.mediapipe_modelle.visualize_active
        if self.mediapipe_modelle.visualize_active :
            self.button_visualize.config(text=f"Keypoints an", background="light green")
        else:
            self.button_visualize.config(text=f"Keypoints aus", background="light yellow")

    # Operation_ansicht konfigurieren  
    def configure_operation_frame(self):
        ttk.Label(self.operation_frame,text="Operationsart", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(14):
            self.operation_frame.columnconfigure(i, minsize=200)
            self.operation_frame.rowconfigure(i, minsize=50)
        
        self.button_belly_patients = tk.Button(self.operation_frame, text="Bauch-OP", font=("Arial", 24), height=2, width=20, background="lightyellow",command=lambda: self.show_frame(self.belly_frame))
        self.button_belly_patients.grid(row=2, column=0, pady=5)
        self.button_breast_patients = tk.Button(self.operation_frame, text="Brust-OP", font=("Arial", 24), height=2, width=20, background="lightyellow",command=lambda: self.show_frame(self.breast_frame))
        self.button_breast_patients.grid(row=3, column=0, pady=5)

        # Zurück-Button
        self.button_back = tk.Button(self.operation_frame, text="Zurück", font=("Arial", 24), height=2, width=20, background="lightblue",command=lambda: self.show_frame(self.main_menu_frame))
        self.button_back.grid(row=14, column=0, pady=5)

        tk.Label(self.operation_frame, text="Postoperativer Tag:", font=("Arial", 24)).grid(row=6, column=0, pady=5)
        
        # Zuordnung der Tage zu festen Zahlenwerten
        self.days_values = {"Tag 1":5, "Tag 2":6, "Tag 3":7, "Tag 4":8, "ab Tag 5":10}

        # Variable für das Dropdown-Menü
        self.selected_day = tk.StringVar(value="Tag 1")  # Standardwert

        # Dropdown-Menü erstellen
        self.dropdown = tk.OptionMenu(self.operation_frame, self.selected_day, *self.days_values.keys(), command=self.update_reps)
        self.dropdown.grid(row=7, column=0, pady=5)
        self.dropdown.config(font=("Arial", 24), height=2, width=18)
        self.dropdown['menu'].config(font=("Arial", 24))

        self.reps = tk.IntVar(value=self.days_values["Tag 1"])  # Standardwert von reps
        

    # Bauch_ansicht konfigurieren  
    def configure_belly_frame(self):
        ttk.Label(self.belly_frame,text="Nach Bauch-OP", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(14):
            self.belly_frame.columnconfigure(i, minsize=200)
            self.belly_frame.rowconfigure(i, minsize=50)

        # Optionen definieren für Bauch Operation
        self.options_belly = {
            "Halswirbelsäule": self.halswirbelsaeule_exercise.start_halswirbelsaeule_exercise,
            "Seitheben": self.seitenheben_exercise.start_seitenheben_exercise, 
            "Abduktion Schulter": self.abduktion_exercise.start_abduktion_exercise, 
            "Flexion Schulter": self.flexion_schulter_exercise.start_flexion_schulter_exercise,
            "Mobilisation Schulter": self.mobilisation_schulter_exercise.start_mobilisation_schulter_exercise,
            "Butterfly": self.butterfly_exercise.start_butterfly_exercise,
            "Rotation im Sitzen": self.rotation_exercise.start_rotation_exercise,
            "Bizepscurls": self.bizepscurls_exercise.start_bizepscurls_exercise,
        }
        
        # Variable für Checkbuttons
        self.check_vars_belly = {}
        for option in self.options_belly.keys():
            self.check_vars_belly[option] = tk.BooleanVar(value=False)
        
        # Checkbuttons erstellen
        tk.Label(self.belly_frame, text="Übungen wählen:", font=("Arial", 24)).grid(row=2, column=0, pady=1, sticky="w")
        for option, var in self.check_vars_belly.items():
            tk.Checkbutton(self.belly_frame, text=option, variable=var, font=("Arial", 24)).grid(column=0, pady=1, sticky="w")

        # Speichern-Button
        tk.Button(self.belly_frame, text="Speichern und starten", font=("Arial", 24), height=2, width=20, background="lightyellow", command=self.save_selection_belly).grid(row=12, column=0, pady=5)
        self.button_belly = tk.Button(self.belly_frame, text="Alle automatisch starten", font=("Arial", 24), height=2, width=20, background="lightyellow", command=self.start_belly_exercises)
        self.button_belly.grid(row=12, column=1, pady=5)
        self.button_back = tk.Button(self.belly_frame, text="Zurück", font=("Arial", 24), height=2, width=20, background="lightblue",command=lambda: self.show_frame(self.operation_frame))
        self.button_back.grid(row=14, column=0, pady=5)


    # Brust_ansicht konfigurieren  
    def configure_breast_frame(self):
        ttk.Label(self.breast_frame,text="Nach Brust-OP", font=("Arial", 32)).grid(row=0, column=0, pady=5)
        for i in range(14):
            self.breast_frame.columnconfigure(i, minsize=200)
            self.breast_frame.rowconfigure(i, minsize=50)

        # Optionen definieren für Brust Operation
        self.options_breast = {
            "Halswirbelsäule": self.halswirbelsaeule_exercise.start_halswirbelsaeule_exercise,
            "Butterfly": self.butterfly_exercise.start_butterfly_exercise,
            "Rotation im Sitzen": self.rotation_exercise.start_rotation_exercise,
        }
        
        # Variable für Checkbuttons
        self.check_vars_breast = {}
        for option in self.options_breast.keys():
            self.check_vars_breast[option] = tk.BooleanVar(value=False)
        
        # Checkbuttons erstellen
        tk.Label(self.breast_frame, text="Übungen auswählen:", font=("Arial", 24)).grid(row=2, column=0, pady=5, sticky="w")
        for option, var in self.check_vars_breast.items():
            tk.Checkbutton(self.breast_frame, text=option, variable=var,font=("Arial", 24)).grid(column=0, pady=5, sticky="w")

        # Speichern-Button
        tk.Button(self.breast_frame, text="Speichern und starten", font=("Arial", 24), width=20, background="lightyellow", height=2, command=self.save_selection_breast).grid(row=12, column=0, pady=5)
        self.button_breast = tk.Button(self.breast_frame, text="Alle automatisch starten", font=("Arial", 24), height=2, width=20, background="lightyellow", command=self.start_breast_exercises)
        self.button_breast.grid(row=12, column=1, pady=5)
        self.button_back = tk.Button(self.breast_frame, text="Zurück", font=("Arial", 24), height=2, width=20, background="lightblue", command=lambda: self.show_frame(self.operation_frame))
        self.button_back.grid(row=14, column=0, pady=5)


    # Aktualisierung des Balkens in der GUI
    def update_bar(self,canvas):

        colors = {
            1: ("red", 0.33),
            2: ("yellow", 0.66),
            3: ("green", 1.0),
        }

        color, fill_percentage = colors.get(self.balken_zahl, ("gray", 0))
        fill_height = int(self.height * fill_percentage)

        canvas.delete("bar")
        canvas.create_rectangle(
            self.x, self.y + (self.height - fill_height), self.x + self.width, self.y + self.height, fill=color, tags="bar"
        )

    # Periodische Aktualisierung des Balkens.
    def periodic_update(self):
        if self.running and self.processing and not self.value == 1:
            try:
                # Aktualisiere den Wert von balkenzahl vom entsprechendee Modul
                if self.current_exercise_index < len(self.exercises_queue):
                    current_function = self.exercises_queue[self.current_exercise_index]
                    self.balken_zahl = getattr(current_function.__self__, "balken", lambda: 0)()
                    
                self.update_bar(self.canvas)
            except Exception as e:
                print(f"Fehler bei der Balkenaktualisierung: {e}")
            
            # Plane die nächste Aktualisierung in 45ms
            self.window.after(45, self.periodic_update)


    def save_selection_belly(self):
        # Ausgewählte Optionen speichern
        self.exercises_queue = [func for name, func in self.options_belly.items() if self.check_vars_belly[name].get()]

        # Falls keine Übung ausgewählt wurde, zeige eine Warnung
        if not self.exercises_queue:
            messagebox.showwarning("Keine Übung ausgewählt", "Bitte wähle mindestens eine Übung aus.")
            return
        
        # Wechsle zum Übungsfenster
        if self.exercises_queue:
            self.show_frame(self.exercise_frame) 
            self.current_exercise_index = 0
            self.running = True
            self.processing = True
            self.start_next_exercise()
    

    def save_selection_breast(self):
        # Ausgewählte Optionen speichern
        self.exercises_queue = [func for name, func in self.options_breast.items() if self.check_vars_breast[name].get()]

        # Falls keine Übung ausgewählt wurde, zeige eine Warnung
        if not self.exercises_queue:
            messagebox.showwarning("Keine Übung ausgewählt", "Bitte wähle mindestens eine Übung aus.")
            return

        # Wechsle zum Übungsfenster
        if self.exercises_queue:
            self.show_frame(self.exercise_frame) 
            self.current_exercise_index = 0
            self.running = True
            self.processing = True
            self.start_next_exercise()


    def update_reps(self, event=None):
        #Aktualisiert den Wert von reps basierend auf der Auswahl.
        selected_day = self.selected_day.get()
        self.reps.set(self.days_values[selected_day])
        print(f"Ausgewählter Tag: {selected_day}, Reps: {self.reps.get()}")  # Ausgabe zur Kontrolle

        number = int(self.reps.get())  # Konvertiere die Wiederholungen in eine Zahl
        # Setze die Wiederholungsanzahl in alle Module
        for exercise in [
            self.abduktion_exercise, self.seitenheben_exercise, self.bizepscurls_exercise,
            self.flexion_schulter_exercise, self.halswirbelsaeule_exercise,
            self.mobilisation_schulter_exercise, self.rotation_exercise, self.butterfly_exercise
            ]:
            exercise.set_repetitions(number)
    

    #Zeigt das angegebene Frame an
    def show_frame(self, frame):
        frame.tkraise()


    # Brustübungen nacheinander Starten
    def start_breast_exercises(self):
        self.exercises_queue = [
            self.halswirbelsaeule_exercise.start_halswirbelsaeule_exercise,
            self.butterfly_exercise.start_butterfly_exercise,
            self.rotation_exercise.start_rotation_exercise
        ]
        self.show_frame(self.exercise_frame)
        self.current_exercise_index = 0
        self.running = True
        self.processing = True
        self.start_next_exercise()


    # Bauchübungen nacheinander Starten
    def start_belly_exercises(self):
        self.exercises_queue = [
            self.halswirbelsaeule_exercise.start_halswirbelsaeule_exercise,
            self.seitenheben_exercise.start_seitenheben_exercise, 
            self.abduktion_exercise.start_abduktion_exercise, 
            self.flexion_schulter_exercise.start_flexion_schulter_exercise,
            self.mobilisation_schulter_exercise.start_mobilisation_schulter_exercise,
            self.butterfly_exercise.start_butterfly_exercise,
            self.rotation_exercise.start_rotation_exercise,
            self.bizepscurls_exercise.start_bizepscurls_exercise
        ]
        self.show_frame(self.exercise_frame) 
        self.current_exercise_index = 0
        self.running = True
        self.processing = True
        self.start_next_exercise()


    def start_next_exercise(self):
        # Startet die nächste Übung in der Warteschlange
        if self.current_exercise_index < len(self.exercises_queue):
            current_exercise = self.exercises_queue[self.current_exercise_index]
            self.update_frame(current_exercise)

            # Starte die periodische Aktualisierung
            self.periodic_update()  
        else:
            self.abort_exercise_and_return()


    # Frame Aktualisieren
    def update_frame(self, current_exercise):

        # Bei 'Pause' keine weitere Bildverarbeitung durchführen
        if self.exercise_pause == True:
            self.window.after(30, lambda: self.update_frame(current_exercise))
        else:
            if self.running and self.processing and self.cap and self.cap.isOpened():
                current_time = time.perf_counter()
                delta_time = current_time - self.last_time_frame
                # print(f" Zeit zwischen zwei Frames: {delta_time} Sekunden")
                self.last_time_frame = current_time
                # Frame und Wert von der Kamerafunktion abrufen
                frame, self.value = current_exercise()
                if frame is None:
                    return
            
                # Wenn der Frame ein Bildpfad ist, lade das Bild
                if isinstance(frame, str):     # Prüfen, ob frame ein Pfad ist
                    frame = cv2.imread(frame)  # Bild laden
                if frame is None:              # Falls das Bild nicht geladen werden konnte, Fehler ausgeben und Abbruch
                    print("Fehler: Bild konnte nicht geladen werden.")
                    return
                
                # Zoom-Funktion
                zoom = getattr(self, "zoom_level", 1.0)  # Falls kein Zoom-Level existiert, standard 1.0
                frame = self.bild_operationen.apply_zoom(frame, zoom)

                #frame = cv2.resize(frame, (800, 600))             # Bildgröße anpassen
                frame = cv2.resize(frame, (1200, 900))             # Bildgröße anpassen
                frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)    # Frame konvertieren
                img = Image.fromarray(frame)
                imgtk = ImageTk.PhotoImage(image=img)

                # Bild im Label anzeigen
                self.video_label.imgtk = imgtk
                self.video_label.configure(image=imgtk)

                # Logik für Wert == 1: Übung abschließen und nächste starten
                if self.value == 1:
                    self.reset_bargraph()
                    if self.start_time is None:
                        self.start_time = time.time()

                    if time.time() - self.start_time >= 5:  # Nach 5 Sekunden
                        self.start_time = None 
                        self.current_exercise_index += 1  # Nächste Übung auswählen
                        self.start_next_exercise()
                        return  
                
                # Plane die nächste Aktualisierung
                self.window.after(10, lambda: self.update_frame(current_exercise))


    def reset_exercises(self):
        # Alle Übungen zurücksetzen
        for exercise in [
        self.abduktion_exercise, self.bizepscurls_exercise, self.seitenheben_exercise,
        self.flexion_schulter_exercise, self.butterfly_exercise, self.rotation_exercise, 
        self.mobilisation_schulter_exercise, self.halswirbelsaeule_exercise
        ]:
            exercise.__init__(self.mediapipe_modelle, self.bild_operationen)
    

    def stop_camera(self):
        # Stoppt die Verarbeitung von Frames.
        self.processing = False
        self.video_label.imgtk = None
        self.video_label.configure(image=None)


    # Balkenanzeige zurücksetzen
    def reset_bargraph(self):
        self.balken_zahl = 0  
        self.update_bar(self.canvas)


    # Beendet das Programm und schließt die Kamera
    def quit_program(self):
        if self.cap:
            self.cap.release()
            self.cap = None
        self.window.destroy()

    
    def open_pdf_window(self):
        subprocess.call(["xdg-open","Bedienungsanleitung/Bedienungsanleitung.pdf"])


if __name__ == "__main__":
    window = tk.Tk()
    app = SportApp(window)
    window.mainloop()
