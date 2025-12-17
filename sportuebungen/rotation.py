"""
******************************************************************************
 file:      rotation.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Rotation
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
from calculate_angle_distance import calculate_angle, calculate_distance
from sprachausgabe import SprachAusgabe

class RotationExercise:
    def __init__ (self, mediapipe_modelle, bild_operationen, user_db, current_user, repetitions=5):
        self.mediapipe_modelle = mediapipe_modelle
        self.bild_operationen = bild_operationen

        self.user_db = user_db             # Datenbankobjekt
        self.current_user = current_user   # aktuell ausgewählter Nutzer

        self.counter = 0
        self.stage = None

        self.total_repetitions = 0 

        self.counter_started = False
        self.message = "Bitte Startposition, beide Hände unten"
        self.exercise_completed = False

        self.sport_exercise_name = "Rotation im Sitzen"
        self.repetitions = repetitions      # Standardwert für Wiederholungen

        self.sport_exercise_example = 0
        self.program_end = 0
        self.wait_time = None
        self.wait_1 =0

        self.exercise_cancel_time = None

        self.elbows_distance = 0
        self.wrists_distance = 0

        self.min_distance = 1.1
        self.max_distance = 1.4

        # SprachAusgabe-Objekt initialisieren
        self.sprach_ausgabe = SprachAusgabe()


    def set_repetitions(self, reps):
        self.repetitions = reps

    def start_rotation_exercise(self):

        if self.exercise_completed:
                self.program_end = 1

        if self.sport_exercise_example == 0:
            example_image = "bilder/Rotation_im_Stand.jpg"

            if self.wait_1 ==0 :
                self.sprach_ausgabe.activate_thread ("Sie fangen jetzt mit der Übung Rotation an. Bitte legen Sie die Hände runter neben den Körper, damit die Übung starten kann.")
                self.wait_1 = 1

            if self.wait_time is None:
                self.wait_time = time.time()
            elif time.time() - self.wait_time >= 10:
                    self.sport_exercise_example = 1
                    self.wait_time = None
                    self.counter_started = True
                    self.message = "Jetzt koennen Sie mit dieser Uebung starten"
            return example_image, self.program_end  # Bild anzeigen, während Audio läuft
        

        # results, image_BGR = self.mediapipe_modelle.whole_body_recognition()
        results, image_BGR, normalized_pose = self.mediapipe_modelle.whole_body_recognition()
        if results is None or image_BGR is None:
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
                self.wrist_left      = [norm_points[15][0], norm_points[15][1]]
                hip_left        = [norm_points[23][0], norm_points[23][1]]

                shoulder_right  = [norm_points[12][0], norm_points[12][1]]
                elbow_right     = [norm_points[14][0], norm_points[14][1]]
                self.wrist_right     = [norm_points[16][0], norm_points[16][1]]
                hip_right       = [norm_points[24][0], norm_points[24][1]]

                self.elbows_distance = calculate_distance(elbow_right, elbow_left)
                self.wrists_distance = calculate_distance(self.wrist_right, self.wrist_left)
                
                self.angle_shoulder_left = calculate_angle(hip_left, shoulder_left, elbow_left)     
                self.angle_shoulder_right = calculate_angle(hip_right, shoulder_right, elbow_right)

                
                self.update_counters()
                
            except Exception as e:
                print(f"Fehler: {e}")
            

        counter = self.repetitions - self.counter

        frame = self.bild_operationen.show_exercise_details(
            self.sport_exercise_name, image_BGR, 
            self.angle_shoulder_left, self.angle_shoulder_right, 
            counter, counter, 
            self.stage, self.stage, 
            self.message
        )


        if self.sport_exercise_example == 1:
            return frame ,self.program_end
        
    
    def update_counters(self):
        if self.angle_shoulder_left < 75 and self.angle_shoulder_right < 75 and self.counter_started and not self.exercise_completed:
            if self.wrists_distance > (self.elbows_distance * self.max_distance) and self.stage != "MAX":
                self.stage = "MAX"

            if self.wrists_distance < (self.elbows_distance * self.min_distance) and self.stage == "MAX":
                self.stage = "MIN"
                self.counter += 1


            if self.counter >= self.repetitions:
                self.message = "Prima, gute Leistung!"
                self.exercise_completed = True

                # nur speichern, wenn Nutzer gesetzt
                if self.current_user:
                    self.user_db.save_progress(
                        user_name=self.current_user,
                        exercise_name=self.sport_exercise_name,
                        postop_day=getattr(self, "postop_day", None)  # z.B. "Tag 2"
                    )


            else:
                # self.counter_started = True
                self.message = f"Versuchen Sie {self.repetitions} mal zu schaffen!"

        
            # Aufruf der Sprach-Ausgabe-Methode
            self.total_repetitions = self.counter
            self.sprach_ausgabe.total_repetitions = self.total_repetitions
            self.sprach_ausgabe.repetitions = self.repetitions
            self.sprach_ausgabe.speak()
        
        
            
    def balken(self):
        if self.counter_started == True:
            if self.wrists_distance < (self.elbows_distance * self.min_distance):
                return 1
            if (self.wrists_distance > (self.elbows_distance * self.min_distance)) and self.wrists_distance < (self.elbows_distance * self.max_distance):
                return 2
            if self.wrists_distance > (self.elbows_distance * self.max_distance):
                return 3



