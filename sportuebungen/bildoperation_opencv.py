"""
******************************************************************************
 file:      bildoperation_opencv.py
 brief:     Software zur Anzeige der wichtigsten Daten im Videobild, z.B. Winkel, Wiederholungen für alle Sportübungen
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: OpenCV (Version: 4.10.0.84), Mediapipe (Version: 1.0.1, Tasks-API)
 hardware:
 info:
******************************************************************************
"""

import cv2
import numpy as np
import mediapipe_tasks_compat as mp_tasks

class Bildoperationen:
    """
    Zoom auf Gesicht basierend auf FaceMesh oder Pose, mit EMA-Glättung.
    API bleibt kompatibel: self.zoom_factor und apply_zoom() sind erhalten.
    Modes: 'facemesh' oder 'posemesh'.
    """
    def __init__(self, resolution, cap,
                #  mode='facemesh',                   # kann facemesh oder posemesh wie gewünscht sein
                 mode='posemesh',
                 smooth_factor=0.2,
                 init_zoom=1.0,
                 max_zoom=3.0,
                 ):
        self.resolution = resolution
        self.cap = cap
        self.mode = mode  # 'facemesh' | 'posemesh'
        self.smooth_factor = float(smooth_factor)
        self.zoom_factor = float(init_zoom)   # bleibt erhalten
        self.max_zoom = float(max_zoom)
        

        # Glättungszustand
        self._prev_cx = None
        self._prev_cy = None
        self._prev_cw = None
        self._prev_ch = None

        # MediaPipe initialisieren (Tasks-API)
        self._mp_draw = mp_tasks.drawing_utils
        self._mp_style = mp_tasks.drawing_styles

        self._face_mesh = mp_tasks.FaceTask(
            max_num_faces=1,
            min_detection_confidence=0.6,
            min_tracking_confidence=0.5
        )
        self._pose = mp_tasks.PoseTask(
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )

    # ---------- Public API ----------
    def set_mode(self, mode: str):
        self.mode = mode.lower().strip()  # 'facemesh' oder 'posemesh'

    def set_zoom(self, z: float):
        self.zoom_factor = float(np.clip(z, 1.0, self.max_zoom))

    def inc_zoom(self, dz: float):
        self.set_zoom(self.zoom_factor + dz)

    def toggle_keypoints(self):
        self.draw_keypoints = not self.draw_keypoints

    def reset_smoothing(self):
        self._prev_cx = self._prev_cy = self._prev_cw = self._prev_ch = None

    def close(self):
        try: self._face_mesh.close()
        except: pass
        try: self._pose.close()
        except: pass

    # ---------- Hauptpipeline ----------
    def video_frame(self):
        """
        Liest ein Frame, berechnet facemesh-/posemesh-basierten, geglätteten Crop
        und gibt (image_zoomed, image_mediapipe) zurück.
        """
        ret, frame_bgr = self.cap.read()
        if not ret or frame_bgr is None:
            return None, None

        # frame_bgr = cv2.flip(frame_bgr, 1)
        h, w = frame_bgr.shape[:2]
        rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)

        # Standardwerte
        cx, cy = w // 2, h // 2
        cw, ch = int(w * 0.5), int(h * 0.5)

        if self.mode == 'facemesh':
            cx, cy, cw, ch = self._from_facemesh(rgb, frame_bgr)
        elif self.mode == 'posemesh':
            cx, cy, cw, ch = self._from_posemesh(rgb, frame_bgr)

        # Glättung
        cx, cy, cw, ch = self._smooth(cx, cy, cw, ch)

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


    # neue Funktionen zur Glättung und BoundingBox --> EMA-Funktion
    def _smooth(self, cx, cy, cw, ch):
        if self._prev_cx is None:
            self._prev_cx, self._prev_cy, self._prev_cw, self._prev_ch = cx, cy, cw, ch
        else:
            a = self.smooth_factor
            self._prev_cx = int((1 - a) * self._prev_cx + a * cx)
            self._prev_cy = int((1 - a) * self._prev_cy + a * cy)
            self._prev_cw = int((1 - a) * self._prev_cw + a * cw)
            self._prev_ch = int((1 - a) * self._prev_ch + a * ch)
        return self._prev_cx, self._prev_cy, self._prev_cw, self._prev_ch

    def _from_facemesh(self, rgb, frame_bgr):
        h, w = frame_bgr.shape[:2]
        res = self._face_mesh.process(rgb)
        if not res.multi_face_landmarks:
            return w//2, h//2, int(w*0.5), int(h*0.5)
        face_lm = res.multi_face_landmarks[0]
        
        cx, cy, bw, bh = self._landmarks_to_bbox(face_lm.landmark, w, h, margin=0.20)
        cw = int(bw * 1.4)
        ch = int(bh * 1.6)
        return cx, cy, cw, ch

    def _from_posemesh(self, rgb, frame_bgr):
        h, w = frame_bgr.shape[:2]
        res = self._pose.process(rgb)
        if not res.pose_landmarks:
            return w//2, h//2, int(w*0.5), int(h*0.5)
        
        cx, cy, bw, bh = self._pose_to_bbox(res.pose_landmarks, w, h)
        cw = int(bw)
        ch = int(bh)
        return cx, cy, cw, ch

    # BoundingBox mit FaceMesh erstellen
    @staticmethod
    def _landmarks_to_bbox(landmarks, w, h, margin=0.20):
        xs = [lm.x for lm in landmarks]
        ys = [lm.y for lm in landmarks]
        min_x = max(0.0, min(xs)); max_x = min(1.0, max(xs))
        min_y = max(0.0, min(ys)); max_y = min(1.0, max(ys))

        cx = (min_x + max_x) / 2.0
        cy = (min_y + max_y) / 2.0
        bw = (max_x - min_x) * (1.0 + margin * 2)
        bh = (max_y - min_y) * (1.0 + margin * 2)

        min_x = max(0.0, cx - bw/2.0); max_x = min(1.0, cx + bw/2.0)
        min_y = max(0.0, cy - bh/2.0); max_y = min(1.0, cy + bh/2.0)

        px_min = int(min_x * w); px_max = int(max_x * w)
        py_min = int(min_y * h); py_max = int(max_y * h)

        bw_px = px_max - px_min; bh_px = py_max - py_min
        cx_px = px_min + bw_px // 2; cy_px = py_min + bh_px // 2
        return cx_px, cy_px, bw_px, bh_px

    # BoundingBox mit PoseMesh erstellen
    @staticmethod
    def _pose_to_bbox(pose_landmarks, w, h):
        lms = pose_landmarks.landmark
        NOSE=0; LEFT_EYE=1; RIGHT_EYE=2; LEFT_EAR=7; RIGHT_EAR=8
        def to_px(lm): return int(lm.x*w), int(lm.y*h)
        nx, ny = to_px(lms[NOSE])

        le, re = lms[LEFT_EAR], lms[RIGHT_EAR]
        def visible(lm):
            v = getattr(lm, 'visibility', None)
            return True if v is None else v > 0.5
        le_vis = visible(le)
        re_vis = visible(re)
        if le_vis and re_vis:
            lex, ley = to_px(le); rex, rey = to_px(re)
            head_w = int(max(20, np.hypot(lex - rex, ley - rey)))
        else:
            lx, ly = to_px(lms[LEFT_EYE]); rx, ry = to_px(lms[RIGHT_EYE])
            head_w = int(max(20, np.hypot(lx - rx, ly - ry)))
        bw = int(head_w * 1.8)
        bh = int(head_w * 2.2)
        return nx, ny, bw, bh


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
    
