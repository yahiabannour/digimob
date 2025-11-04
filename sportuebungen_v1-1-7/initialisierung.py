"""
******************************************************************************
 file:      initialisierung.py
 brief:     Software zur Kamerakonfiguration
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.4
 date:      09.05.2025
 software:  Python (Version: 3.9.2)
 int. libs:
 ext. libs: OpenCV (Version: 4.11.0)
 info:
******************************************************************************
"""
import cv2

def camera_start():
    # Kameraauflösung und anzahl von Frame(FPS)
    resolution = (640, 480)
   # resolution = (1280,720)
    #resolution = (1920,1080)
    target_fps = 30

    # Kamera initialisieren
    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, resolution[0])
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, resolution[1])
    cap.set(cv2.CAP_PROP_FPS, target_fps)

    return cap,resolution
