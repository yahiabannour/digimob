"""
******************************************************************************
 file:      halswirbelsaeule.py
 brief:     Software zur Bestimmung und Zählung von Kopfbewegungen für die Sportübung Halswirbelsäule
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
from sprachausgabe import SprachAusgabe

class HalswirbelsaeuleExercise:
    def __init__(self, mediapipe_modelle, bild_operationen):
        self.mediapipe_modelle = mediapipe_modelle
        self.bild_operationen = bild_operationen

        self.counter_left = 0
        self.counter_right = 0
        self.previous_direction = None   #speichert die Richtung der vorherigen Kopfbewegung (links oder rechts)

        self.total_repetitions = 0

        self.counter_started = False
        self.message = "Bitte Startposition, ruhig in die Kamera schauen."
        self.exercise_completed = False

        # self.sport_exercise_name = " "
        self.sport_exercise_name = "Halswirbelsaeule"
        self.repetitions = 5  # Standardwert für Wiederholungen

        self.sport_exercise_example = 0
        self.program_end = 0
        self.wait_time = None
        self.wait_1 = 0

        self.exercise_cancel_time = None

        self.horizontal_inclination = 0
        self.direction = None

        self.positive_horizontal_inclination_min = 5 
        self.positive_horizontal_inclination_max = 12
        self.negative_horizontal_inclination_min = -5
        self.negative_horizontal_inclination_max = -12

        # SprachAusgabe-Objekt initialisieren
        self.sprach_ausgabe = SprachAusgabe()


    def set_repetitions(self, reps):
        self.repetitions = reps


    def start_halswirbelsaeule_exercise(self):
        if self.exercise_completed:
            self.program_end = 1

        if self.sport_exercise_example == 0:
            # Bild "Beispiel für die Sportübung Halswirbelsäule" anzeigen
            example_image = "bilder/Halswirbelsaeule.jpg"

            if self.wait_1 == 0:
                self.sprach_ausgabe.activate_thread ("Sie fangen jetzt mit der Übung Halswirbelsäule an. Bitte halten Sie den Kopf ruhig in der Mitte, damit die Übung starten kann.")
                self.wait_1 = 1

            if self.wait_time is None:
                self.wait_time = time.time()
            elif time.time() - self.wait_time >= 10:
                self.sport_exercise_example = 1
                self.wait_time = None
                self.counter_started = True
                self.message = "Jetzt koennen Sie mit dieser Uebung starten."
            return example_image, self.program_end  # Bild "Beispiel für Sportübung Halswirbelsäule" anzeigen, während Audio läuft
        
        results, image_zoomed = self.mediapipe_modelle.face_recognition()
        # results, image_BGR = self.mediapipe_modelle.whole_body_recognition()

        if results.multi_face_landmarks:
            for face_landmarks in results.multi_face_landmarks:
                image_height, image_width, _ = image_zoomed.shape
                self.horizontal_inclination = self.calculate_angle(face_landmarks.landmark, image_width)
                self.update_counters()

        # if results.pose_landmarks:
        #     image_height, image_width, _ = image_BGR.shape
        #     self.horizontal_inclination = self.calculate_angle(results.pose_landmarks.landmark, image_width)
        #     self.update_counters()

        
        counter_left = self.repetitions - self.counter_left
        counter_right = self.repetitions - self.counter_right

        # Anzeigen von Informationen auf dem Bildschirm
        frame = self.bild_operationen.show_exercise_details(
            self.sport_exercise_name, image_zoomed, self.horizontal_inclination, None,
            # self.counter_left, self.counter_right,
            counter_left, counter_right,
            self.direction, self.direction, self.message
        )

        if self.sport_exercise_example == 1:
            return frame, self.program_end
        

    def update_counters(self):
        if self.counter_started and not self.exercise_completed:
            # print(f"horizontal inclination : {self.horizontal_inclination}")

            """Aktualisiert die Zähler für Kopfbewegungen."""
            # direction = None
            if self.horizontal_inclination > self.positive_horizontal_inclination_max :
                self.direction = "rechts"
            elif self.horizontal_inclination < self.negative_horizontal_inclination_max:
                self.direction = "links"

            if self.direction and self.direction != self.previous_direction:
                if self.direction == "rechts":
                    self.counter_right += 1
                elif self.direction == "links":
                    self.counter_left += 1
                self.previous_direction = self.direction
            
            if self.counter_left > 0 and self.counter_right > 0:
                self.total_repetitions = min(self.counter_left, self.counter_right)

            if self.total_repetitions >= self.repetitions :
                self.message = "Prima, gute Leistung!"
                self.exercise_completed = True
            else:
                self.message = f"Super, versuchen Sie {self.repetitions} mal zu schaffen!"
            
            # Aufruf der Sprach-Ausgabe-Methode
            self.sprach_ausgabe.total_repetitions = self.total_repetitions
            self.sprach_ausgabe.repetitions = self.repetitions
            self.sprach_ausgabe.speak()

        # Sportübung unterbrechen
        """
        if self.counter_started and (self.horizontal_inclination < self.negative_horizontal_inclination_max or self.horizontal_inclination > self.positive_horizontal_inclination_max):
            if self.exercise_cancel_time is None:
                self.exercise_cancel_time = time.time()  
            elif time.time() - self.exercise_cancel_time >= 3:
                self.message = "Uebung unterbrochen"
                if self.wait_1 == 1:
                    self.sprach_ausgabe.activate_thread ("Die Übung wurde unterbrochen.")
                    self.wait_1 += 1
                self.exercise_completed = True
        else:
            self.exercise_cancel_time = None
        """
    

    def calculate_angle(self, landmarks, image_width):
        """Berechnet den horizontalen Kopfneigungswinkel."""
        # mit Face-Landmarks
        left_eye = landmarks[33]
        right_eye = landmarks[263]
        nose_tip = landmarks[1]

        # mit Pose-Landmarks
        # left_eye = landmarks[3]
        # right_eye = landmarks[6]
        # nose_tip = landmarks[0]

        left_eye_x = left_eye.x * image_width
        right_eye_x = right_eye.x * image_width
        nose_tip_x = nose_tip.x * image_width

        #print(f"left eye :{left_eye_x}; right eye :{right_eye_x}; nose tip :{nose_tip_x} ")

        horizontal_inclination_x = nose_tip_x - (left_eye_x + right_eye_x) / 2
        #print (f"horizontal inclination x:{horizontal_inclination_x}")
        return horizontal_inclination_x
    
    
    def balken(self):
        if self.counter_started == True:
            if  self.negative_horizontal_inclination_min <= self.horizontal_inclination <= self.positive_horizontal_inclination_min :
                return 1
            elif self.positive_horizontal_inclination_min < self.horizontal_inclination <= self.positive_horizontal_inclination_max  or self.negative_horizontal_inclination_max <= self.horizontal_inclination < self.negative_horizontal_inclination_min :
                return 2
            elif self.horizontal_inclination < self.negative_horizontal_inclination_max or self.horizontal_inclination > self.positive_horizontal_inclination_max :
                return 3
