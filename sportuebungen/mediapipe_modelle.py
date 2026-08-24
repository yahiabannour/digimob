"""
******************************************************************************
 file:      mediapipe_modelle.py
 brief:     Software zur Anzeigen die wichtigesten Daten z.B. Winkel, Armwiederholungen für alle Sportübungen auf ein fenster
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: Mediapipe (Version: 1.0.1, Tasks-API)
 info:
******************************************************************************
"""
import cv2
import mediapipe_tasks_compat as mp_tasks   # Kompatibilitaetsschicht auf die MediaPipe Tasks API

class MediapipeModelle:
    def __init__(self, bildoperationen):
        self.bildoperationen = bildoperationen

        # Initialisiere Zeichen-Helfer (Tasks-API)
        self.mp_drawing = mp_tasks.drawing_utils
        self.mp_drawing_styles = mp_tasks.drawing_styles
        self.pose_connections = mp_tasks.POSE_CONNECTIONS
        self.face_contours = mp_tasks.FACEMESH_CONTOURS

        # Initialisiere Pose-Erkennung mit den Konfidenzwerten für Erkennung und Tracking
        self.pose = mp_tasks.PoseTask(min_detection_confidence=0.5, min_tracking_confidence=0.5)

        # Initialisiere Gesicht-Erkennung
        self.face_mesh = mp_tasks.FaceTask(max_num_faces=1)

        self.visualize_active = False


    def whole_body_recognition(self):
        image_zoomed, image_mediapipe = self.bildoperationen.video_frame()

        # Führe die Pose-Erkennung durch
        results = self.pose.process(image_mediapipe)

        normalized_pose = []

        
        if results.pose_landmarks:
           
            # Keypoints auf vergrößtes Bild zeichnen
            if self.visualize_active:
                 mp_tasks.draw_landmarks(
                    image_zoomed, results.pose_landmarks, self.pose_connections,
                    mp_tasks.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2),
                    mp_tasks.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)
                )

            # Normalisierung der Keypoints
            normalized_pose = self.normalize_pose_landmarks(results)

        return results, image_zoomed, normalized_pose
    
    def normalize_pose_landmarks(self, results):
        if not results.pose_landmarks:
            return []

        # Erstellt die Liste, die die normalisierten Landmarks enthalten soll
        normalizedLandmarks = []

        # Erstellt Liste (x,y,z) aller 33 Pose-Landmarks
        keypoints = [(lm.x, lm.y, lm.z) for lm in results.pose_landmarks.landmark]


        # Normierung mit Mittelpunkt & globaler Skala
        x_coords = [x for x, y, z in keypoints]
        y_coords = [y for x, y, z in keypoints]
        z_coords = [z for x, y, z in keypoints]

        center_x = sum(x_coords) / len(x_coords)
        center_y = sum(y_coords) / len(y_coords)
        center_z = sum(z_coords) / len(z_coords)

        # Skala = größte Ausdehnung entlang einer Achse
        scale = max(
            max(x_coords) - min(x_coords),
            max(y_coords) - min(y_coords),
            max(z_coords) - min(z_coords)
        )
        scale = scale if scale > 0 else 1.0

        normalizedData = [
            ((x - center_x) / scale,
             (y - center_y) / scale,
             (z - center_z) / scale)
            for (x, y, z) in keypoints
        ] 
        
        # Fügt die normalisierten Keypoints der Liste hinzu
        normalizedLandmarks.append(normalizedData)
        # Gibt die Liste der normalisierten Landmarks zurück
        return normalizedLandmarks



    def face_recognition(self):
        image_zoomed, image_mediapipe = self.bildoperationen.video_frame()

        # Führe die Gesicht-Erkennung durch
        results = self.face_mesh.process(image_mediapipe)

        normalized_face = []

        # Keypoints visualisieren
        if self.visualize_active:
            if results.multi_face_landmarks:
                # Normaliserung der Keypoints von Facemesh
                normalized_face = self.normalize_face_landmarks(results)

                for face_landmarks in results.multi_face_landmarks:
                    # Alle 468 Keypoints visualisieren
                   
                    mp_tasks.draw_landmarks(
                        image_zoomed,
                        face_landmarks,
                        self.face_contours,
                        landmark_drawing_spec=None,
                        connection_drawing_spec=self.mp_drawing_styles
                        .get_default_face_mesh_contours_style()
                        )
                   
                    selected_indices = [
                        # Nase, linke Auge, rechte Auge
                        1, 33, 263
                    ]

                    for idx in selected_indices:
                        h, w, _ = image_zoomed.shape
                        x = int(face_landmarks.landmark[idx].x * w)
                        y = int(face_landmarks.landmark[idx].y * h)
                        cv2.circle(image_zoomed, (x, y), 5, (255, 0, 0), -1)

            
        return results, image_zoomed, normalized_face
    
    
    def normalize_face_landmarks(self, results):
        if not results.multi_face_landmarks:
            return []

        # Erstellt die Liste, die die normalisierten Landmarks enthalten soll
        normalizedLandmarks = []

        # Erstellt Liste (x,y,z) aller 468 FaceMesh-Landmarks
        for face_landmarks in results.multi_face_landmarks:
            keypoints = [(lm.x, lm.y, lm.z) for lm in face_landmarks.landmark]

                # Normierung mit Mittelpunkt & globaler Skala
            x_coords = [x for x, y, z in keypoints]
            y_coords = [y for x, y, z in keypoints]
            z_coords = [z for x, y, z in keypoints]

            center_x = sum(x_coords) / len(x_coords)
            center_y = sum(y_coords) / len(y_coords)
            center_z = sum(z_coords) / len(z_coords)

            # Skala = größte Ausdehnung entlang einer Achse
            scale = max(
                max(x_coords) - min(x_coords),
                max(y_coords) - min(y_coords),
                max(z_coords) - min(z_coords)
            )
            scale = scale if scale > 0 else 1.0

            normalizedData = [
                ((x - center_x) / scale,
                (y - center_y) / scale,
                (z - center_z) / scale)
                for (x, y, z) in keypoints
            ] 
        

            # Fügt die normalisierten Keypoints der Liste hinzu
            normalizedLandmarks.append(normalizedData)
            # Gibt die Liste der normalisierten Landmarks zurück
        return normalizedLandmarks