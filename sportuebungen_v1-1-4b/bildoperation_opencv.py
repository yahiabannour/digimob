"""
******************************************************************************
 file:      bildoperation_opencv.py
 brief:     Software zur Anzeige der wichtigsten Daten im Videobild, z.B. Winkel, Wiederholungen für alle Sportübungen
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.4
 date:      09.05.2025
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: OpenCV (Version: 4.10.0.84)
 hardware:
 info:
******************************************************************************
"""
import cv2                  # OpenCV-Bibliothek zur Bildverarbeitung

class Bildoperationen:
    def __init__(self, resolution, cap):
        self.resolution = resolution
        self.cap = cap
        self.zoom_factor = 1.0

    def video_frame(self):
        ret, image_cam = self.cap.read()
        if not ret:
            return None, None
        
        # Zoom-Funktionen vor Mediapipe
        image_zoomed = self.apply_zoom(image_cam, self.zoom_factor)
        
        # Konvertiere das Bild von BGR zu RGB für Mediapipe
        image_mediapipe = cv2.cvtColor(image_zoomed, cv2.COLOR_BGR2RGB)

        return image_zoomed, image_mediapipe


    def show_exercise_details(self, name, image_zoomed, angle_left, angle_right, counter_left, counter_right, stage_left, stage_right, message):
        image_zoomed.flags.writeable = True
        image_zoomed = cv2.flip(image_zoomed, 1)
        
        cv2.putText(image_zoomed, name , (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 3, cv2.LINE_AA)
        cv2.putText(image_zoomed, message, (10, 65), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2, cv2.LINE_AA)

        cv2.putText(image_zoomed, 'Phase', (10, 330), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2, cv2.LINE_AA)
        cv2.putText(image_zoomed, stage_left if stage_left else 'N/A', (10, 375), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (255, 255, 255), 3, cv2.LINE_AA)
        cv2.putText(image_zoomed, 'Anzahl linke Seite', (130, 330), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2, cv2.LINE_AA)
        cv2.putText(image_zoomed, str(counter_left), (220, 385), cv2.FONT_HERSHEY_SIMPLEX, 2, (255, 255, 255), 3, cv2.LINE_AA)

        cv2.putText(image_zoomed, 'Phase', (10, 420), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2, cv2.LINE_AA)
        cv2.putText(image_zoomed, stage_right if stage_right else 'N/A', (15, 465), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (255, 255, 255), 3, cv2.LINE_AA)
        cv2.putText(image_zoomed, 'Anzahl rechte Seite', (130, 420), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2, cv2.LINE_AA)
        cv2.putText(image_zoomed, str(counter_right), (220, 475), cv2.FONT_HERSHEY_SIMPLEX, 2, (255, 255, 255), 3, cv2.LINE_AA)
        
        return image_zoomed
    
    def apply_zoom(self, frame, zoom_factor):
        if zoom_factor <= 1.0:
            return frame  # Kein Zoom nötig

        h, w = frame.shape[:2]
        center_x, center_y = w // 2, h // 2
        zoom_w = int(w / zoom_factor)
        zoom_h = int(h / zoom_factor)

        x1 = max(center_x - zoom_w // 2, 0)
        y1 = max(center_y - zoom_h // 2, 0)
        x2 = min(center_x + zoom_w // 2, w)
        y2 = min(center_y + zoom_h // 2, h)

        cropped = frame[y1:y2, x1:x2]
        return cv2.resize(cropped, (w, h), interpolation=cv2.INTER_LINEAR)
        # return cv2.resize(cropped, (w, h))

