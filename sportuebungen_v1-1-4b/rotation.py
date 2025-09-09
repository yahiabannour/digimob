"""
******************************************************************************
 file:      rotation.py
 brief:     Software zur Bestimmung und Zählen von Armbewegungen für die Sportübung Rotation
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.4
 date:      09.05.2025
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
    def __init__ (self, mediapipe_modelle, bild_operationen):
        self.mediapipe_modelle = mediapipe_modelle
        self.bild_operationen = bild_operationen

        self.counter = 0
        self.stage = None

        self.total_repetitions = 0 

        self.counter_started = False
        self.message = "Bitte Startposition, beide Hände unten"
        self.exercise_completed = False

        self.sport_exercise_name = "Rotation im Sitzen"
        self.repetitions = 5

        self.sport_exercise_example = 0
        self.program_end = 0
        self.wait_time = None
        self.wait_1 =0

        self.exercise_cancel_time = None

        self.elbows_distance = 0
        self.wrists_distance = 0

        self.min_distance = 1.1
        self.max_distance = 1.6

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
        

        results, image_BGR = self.mediapipe_modelle.whole_body_recognition()
        if results is None or image_BGR is None:
            print("Kamera konnte kein Bild erfassen.")
            return

        # Initialisieren der Variablen, falls die Erkennung fehlschlägt
        self.angle_shoulder_left = 0
        self.angle_shoulder_right = 0

        if results.pose_landmarks:
            try:
                landmarks = results.pose_landmarks.landmark
                shoulder_left = [landmarks[11].x, landmarks[11].y]
                elbow_left = [landmarks[13].x, landmarks[13].y]
                hip_left = [landmarks[23].x, landmarks[23].y]

                shoulder_right = [landmarks[12].x, landmarks[12].y]
                elbow_right = [landmarks[14].x, landmarks[14].y]
                hip_right = [landmarks[24].x, landmarks[24].y]

                self.wrist_left = [landmarks[15].x, landmarks[15].y]
                self.wrist_right = [landmarks[16].x, landmarks[16].y]

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
            else:
                # self.counter_started = True
                self.message = f"Versuchen Sie {self.repetitions} mal zu schaffen!"

        
            # Aufruf der Sprach-Ausgabe-Methode
            self.total_repetitions = self.counter
            self.sprach_ausgabe.total_repetitions = self.total_repetitions
            self.sprach_ausgabe.repetitions = self.repetitions
            self.sprach_ausgabe.speak()
        
        # Sportübung unterbrechen
        """
        if self.counter_started and ((self.angle_shoulder_left < 40 and self.angle_shoulder_right > 60) or (self.angle_shoulder_left > 60 and self.angle_shoulder_right < 60)):
            if self.exercise_cancel_time is None:
                self.exercise_cancel_time = time.time()
            elif time.time() - self.exercise_cancel_time >= 3:
                self.message = "Übung unterbrochen"
                if self.wait_1 == 1:
                    self.sprach_ausgabe.activate_thread ("Die Übung wurde unterbrochen.")
                    self.wait_1 += 1
                self.exercise_completed = True
        else:
            self.exercise_cancel_time = None
        """

            
    def balken(self):
        if self.counter_started == True:
            if self.wrists_distance < (self.elbows_distance * self.min_distance):
                return 1
            if (self.wrists_distance > (self.elbows_distance * self.min_distance)) and self.wrists_distance < (self.elbows_distance * self.max_distance):
                return 2
            if self.wrists_distance > (self.elbows_distance * self.max_distance):
                return 3



