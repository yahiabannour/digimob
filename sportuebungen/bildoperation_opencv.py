"""
******************************************************************************
 file:      bildoperation_opencv.py
 brief:     Software zur Anzeige der wichtigsten Daten im Videobild, z.B. Wiederholungen für alle
            Sportübungen, und Zoom auf den Kopf
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.10.2026
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: OpenCV (Version: 4.10.0.84); MediaPipe nur ueber mediapipe_modelle.py
 hardware:
 info:      Liest selbst kein Kamerabild: der Uebungslauf gibt jedes Bild herein.
******************************************************************************
"""

import cv2
import numpy as np

from mediapipe_modelle import MediapipeModelle, draw_pose

class Bildoperationen:
    """
    Zoom auf den Kopf basierend auf den Pose-Landmarks, mit EMA-Glättung, und die
    Beschriftung des Videobilds.
    """
    def __init__(self, smooth_factor=0.2):
        self.smooth_factor = float(smooth_factor)
        self.zoom_factor = 1.0          # <= 1: kein Zoom ("Zoom an" setzt 2.0)
        self.show_keypoints = False     # Knopf "Keypoints an/aus"

        # Glättungszustand
        self._prev_cx = None
        self._prev_cy = None

        # Eigene Pose-Erkennung nur fuer den Zoom-Mittelpunkt (nicht die der Uebung). Erst beim
        # ersten Zoom-Bild geladen: das Modell kostet ~0.2 s und ~70 MB, Zoom ist selten an
        self._pose = None

    def reset_smoothing(self):
        self._prev_cx = self._prev_cy = None

    # ---------- Hauptpipeline ----------
    def zoom(self, frame_rgb):
        """
        Berechnet aus dem Kamerabild (RGB) einen posemesh-basierten, geglätteten Crop und gibt
        (image_zoomed, image_mediapipe) zurück: BGR für Anzeige und Zeichnen, RGB für MediaPipe.
        """
        frame_bgr = cv2.cvtColor(frame_rgb, cv2.COLOR_RGB2BGR)

        # Ohne Zoom wird kein Bildausschnitt benoetigt -> keine eigene Pose-
        # Erkennung (die Uebung erkennt ohnehin auf demselben Frame). Glaettung
        # zuruecksetzen, damit beim Einschalten des Zooms nicht von einer alten
        # Position aus nachgezogen wird.
        if self.zoom_factor <= 1.0:
            self.reset_smoothing()
            return frame_bgr, frame_rgb

        cx, cy = self._from_posemesh(frame_rgb, frame_bgr)

        # Glättung
        cx, cy = self._smooth(cx, cy)

        image_zoomed = self.apply_zoom(frame_bgr, self.zoom_factor, center=(cx, cy))

        image_mediapipe = cv2.cvtColor(image_zoomed, cv2.COLOR_BGR2RGB)
        return image_zoomed, image_mediapipe


    def apply_zoom(self, frame, zoom_factor, center=None):
        """
        Verbessertes Zoom mit EMA-Funktion:
        - hält immer das Seitenverhältnis des Frames
        - verschiebt das Zentrum automatisch so, dass der Crop vollständig im Bild liegt
        - vermeidet "komisches" Cropping am Rand
        """
        if zoom_factor <= 1.0:
            return frame  # Kein Zoom: nichts tun

        h, w = frame.shape[:2]

        crop_w = max(1, int(round(w / zoom_factor)))
        crop_h = max(1, int(round(h / zoom_factor)))

        # gewünschtes Zentrum (falls None -> Bildmitte)
        if center is None:
            cx, cy = w // 2, h // 2
        else:
            cx, cy = int(center[0]), int(center[1])

        # --- WICHTIG: Zentrum so "clampen", dass der Crop vollständig im Bild liegt ---
        half_w_left  = crop_w // 2
        half_w_right = crop_w - half_w_left  # deckt ungerade Breiten sauber ab
        half_h_top   = crop_h // 2
        half_h_bot   = crop_h - half_h_top

        # zulässiger Bereich fürs Zentrum:
        min_cx = half_w_left
        max_cx = w - half_w_right
        min_cy = half_h_top
        max_cy = h - half_h_bot

        # Clamping (verhindert, dass der Crop über den Rand hinausragt)
        cx = int(np.clip(cx, min_cx, max_cx))
        cy = int(np.clip(cy, min_cy, max_cy))

        # finaler, vollständig gültiger Crop
        x1 = cx - half_w_left
        x2 = cx + half_w_right
        y1 = cy - half_h_top
        y2 = cy + half_h_bot

        # (Sicherheits-Clamp; sollte durch das Center-Clamping schon passen)
        x1 = max(0, min(x1, w - crop_w)); x2 = x1 + crop_w
        y1 = max(0, min(y1, h - crop_h)); y2 = y1 + crop_h

        cropped = frame[y1:y2, x1:x2]

        # Für Zoom (>1) wird kleiner Ausschnitt auf volle Größe hochskaliert
        # INTER_LINEAR ist für Upscaling okay; bei Bedarf auf INTER_CUBIC wechseln.
        return cv2.resize(cropped, (w, h), interpolation=cv2.INTER_LINEAR)


    # Glättung des Zoom-Mittelpunkts --> EMA-Funktion
    def _smooth(self, cx, cy):
        if self._prev_cx is None:
            self._prev_cx, self._prev_cy = cx, cy
        else:
            a = self.smooth_factor
            self._prev_cx = int((1 - a) * self._prev_cx + a * cx)
            self._prev_cy = int((1 - a) * self._prev_cy + a * cy)
        return self._prev_cx, self._prev_cy

    # Zoom-Mittelpunkt: die Nase in Pixeln, ohne erkannte Pose die Bildmitte
    def _from_posemesh(self, rgb, frame_bgr):
        h, w = frame_bgr.shape[:2]
        if self._pose is None:
            self._pose = MediapipeModelle()
        pose = self._pose.detect(rgb)
        if pose is None:
            return w//2, h//2

        nose = pose.landmarks[0]
        return int(nose.x*w), int(nose.y*h)


    # ---------- Anzeige ----------
    @staticmethod
    def demo_frame(frame_rgb):
        """Ein Bild der 3D-Demo (RGB von FileCamera, schon gespiegelt) als BGR fuer die Anzeige."""
        return cv2.cvtColor(frame_rgb, cv2.COLOR_RGB2BGR)

    def overlay(self, image_zoomed, pose, uebung):
        """Keypoints (falls an), dann gespiegelt mit Name und Meldung. Die Wiederholungen je Seite
        zeigen die Balken der Oberflaeche (uebung.progress())."""
        if self.show_keypoints and pose is not None:
            draw_pose(image_zoomed, pose.landmarks)

        image_zoomed = cv2.flip(image_zoomed, 1)

        cv2.putText(image_zoomed, uebung.name, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 3, cv2.LINE_AA)
        cv2.putText(image_zoomed, uebung.message, (10, 65), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2, cv2.LINE_AA)

        return image_zoomed
