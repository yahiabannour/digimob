"""
******************************************************************************
 file:      seitenheben.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Seitheben
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
 software:  Python (Version: 3.12.3)
 int. libs: time
 ext. libs:
 info:
******************************************************************************
"""
import time
from calculate_angle_distance import calculate_angle
from sprachausgabe import SprachAusgabe

class SeitenhebenExercise:
    def __init__ (self, mediapipe_modelle, bild_operationen, repetitions=5):
        self.mediapipe_modelle = mediapipe_modelle
        self.bild_operationen = bild_operationen

        self.counter_left = 0
        self.counter_right = 0
        self.stage_left = None
        self.stage_right = None

        self.total_repetitions = 0

        self.counter_started = False
        self.message = "Bitte Startposition, beide Haende unten"
        self.exercise_completed = False

        self.sport_exercise_name = "Seitheben"
        self.repetitions = repetitions  # Standardwert für Wiederholungen

        self.sport_exercise_example = 0
        self.program_end = 0
        self.wait_time = None
        self.wait_1 =0

        self.exercise_cancel_time = None

        self.min_angle = 40
        self.max_angle = 70

        # SprachAusgabe-Objekt initialisieren
        self.sprach_ausgabe = SprachAusgabe()


    def set_repetitions(self, reps):
        self.repetitions = reps


    def start_seitenheben_exercise(self):
            if self.exercise_completed:
                self.program_end = 1

            if self.sport_exercise_example == 0:
                example_image = "bilder/Seitheben.jpg"

                if self.wait_1 ==0 :
                    self.sprach_ausgabe.activate_thread ("Sie fangen jetzt mit der Übung Seitheben an. Bitte legen Sie die Hände runter neben den Körper, damit die Übung starten kann.")
                    self.wait_1 = 1

                if self.wait_time is None:
                    self.wait_time = time.time()
                elif time.time() - self.wait_time >= 10:
                    self.sport_exercise_example = 1
                    self.wait_time = None
                    self.counter_started = True
                    self.message = "Jetzt koennen Sie mit dieser Uebung starten"
                return example_image, self.program_end  
            

            # results, image_BGR = self.mediapipe_modelle.whole_body_recognition()
            results, image_zoomed, normalized_pose = self.mediapipe_modelle.whole_body_recognition()

                        
            
            if results is None or image_zoomed is None:
                print("Kamera konnte kein Bild erfassen.")
                return

            # Initialisieren der Variablen, falls die Erkennung fehlschlägt
            self.angle_shoulder_left = 0
            self.angle_shoulder_right = 0

            if results.pose_landmarks:
                try:
                    
                    # normalisierte Daten holen
                    norm_points = normalized_pose[0]

                    shoulder_left   = [norm_points[11][0], norm_points[11][1]]
                    elbow_left      = [norm_points[13][0], norm_points[13][1]]
                    hip_left        = [norm_points[23][0], norm_points[23][1]]

                    shoulder_right  = [norm_points[12][0], norm_points[12][1]]
                    elbow_right     = [norm_points[14][0], norm_points[14][1]]
                    hip_right       = [norm_points[24][0], norm_points[24][1]]

                    self.angle_shoulder_left = calculate_angle(hip_left, shoulder_left, elbow_left)
                    self.angle_shoulder_right = calculate_angle(hip_right, shoulder_right, elbow_right)

                    # Update Zähler- und Statuslogik
                    self.update_counters()

                except Exception as e:
                    print(f"Fehler: {e}")
            
            counter_left = self.repetitions - self.counter_left
            counter_right = self.repetitions - self.counter_right

            frame = self.bild_operationen.show_exercise_details(
                self.sport_exercise_name, image_zoomed, 
                self.angle_shoulder_left, self.angle_shoulder_right, 
                counter_left, counter_right, 
                self.stage_left, self.stage_right, 
                self.message
            )

            
            if self.sport_exercise_example == 1:
                return frame ,self.program_end
    
    # Methode für Zähler- und Statusaktualisierung
    def update_counters(self):
        if self.counter_started and not self.exercise_completed:
            if self.angle_shoulder_left > self.max_angle:
                self.stage_left = "up"
            if self.angle_shoulder_left < self.min_angle and self.stage_left == 'up':
                self.stage_left = "down"
                self.counter_left += 1

            if self.angle_shoulder_right > self.max_angle:
                self.stage_right = "up"
            if self.angle_shoulder_right < self.min_angle and self.stage_right == 'up':
                self.stage_right = "down"
                self.counter_right += 1
            
            if self.counter_left > 0 and self.counter_right > 0:
                self.total_repetitions = min(self.counter_left, self.counter_right)

            if self.total_repetitions >= self.repetitions:
                self.message = "Prima, gute Leistung!"
                self.exercise_completed = True

            else:
                self.message = f"Super, versuchen Sie {self.repetitions} mal zu schaffen!"

            # Aufruf der Sprach-Ausgabe-Methode
            self.sprach_ausgabe.total_repetitions = self.total_repetitions
            self.sprach_ausgabe.repetitions = self.repetitions
            self.sprach_ausgabe.speak()


        

    
    def balken(self):
        if self.counter_started == True:
            min_angle_left_right = min(self.angle_shoulder_left, self.angle_shoulder_right)
            if min_angle_left_right >=0 and min_angle_left_right <= self.min_angle:
                return 1
            if min_angle_left_right > self.min_angle and min_angle_left_right <= self.max_angle:
                return 2
            if min_angle_left_right > self.max_angle :
                return 3