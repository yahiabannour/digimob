"""
******************************************************************************
 file:      mediapipe_modelle.py
 brief:     Software zur Anzeigen die wichtigesten Daten z.B. Winkel, Armwiederholungen für alle Sportübungen auf ein fenster
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.5
 date:      20.08.2025
 software:  Python (Version: 3.12.3)
 int. libs:
 ext. libs: Mediapipe (Version: 0.10.21)
 info:
******************************************************************************
"""
import mediapipe as mp      # Mediapipe-Bibliothek zur Erkennung und Verarbeitung von Körper-Keypoints
import cv2

class MediapipeModelle:
    def __init__(self, bildoperationen):
        self.bildoperationen = bildoperationen

        # Initialisiere Keypoints
        self.mp_drawing = mp.solutions.drawing_utils
        self.mp_pose = mp.solutions.pose
        self.mp_face_mesh = mp.solutions.face_mesh
        self.mp_drawing_styles = mp.solutions.drawing_styles

        # Initialisiere Pose-Erkennung mit den Konfidenzwerten für Erkennung und Tracking
        self.pose = mp.solutions.pose.Pose(min_detection_confidence=0.5, min_tracking_confidence=0.5)

        # Initialisiere Gesicht-Erkennung
        self.face_mesh = mp.solutions.face_mesh.FaceMesh(static_image_mode=False, max_num_faces=1, refine_landmarks=True)

        self.visualize_active = False


    def whole_body_recognition(self):
        image_zoomed, image_mediapipe = self.bildoperationen.video_frame()

        # Führe die Pose-Erkennung durch
        results = self.pose.process(image_mediapipe)

        normalized_pose = []

        
        if results.pose_landmarks:
           
            # Keypoints auf vergrößtes Bild zeichnen
            if self.visualize_active:
                 self.mp_drawing.draw_landmarks(
                    image_zoomed, results.pose_landmarks, self.mp_pose.POSE_CONNECTIONS,
                    self.mp_drawing.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2),
                    self.mp_drawing.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)
                )

        # Keypoints visualisieren
        # if self.visualize_active:
        #     if results.pose_landmarks:
        #         self.mp_drawing.draw_landmarks(
        #             image_zoomed, results.pose_landmarks, self.mp_pose.POSE_CONNECTIONS,
        #             self.mp_drawing.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2),
        #             self.mp_drawing.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)
        #         )
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

        # # X-Koordinaten extrahieren
        # x_coordinates = [x for x, _, _ in keypoints]
        # # Y-Koordinaten extrahieren
        # y_coordinates = [y for _, y, _ in keypoints]

        # # Minimum und Maximum bestimmen (für x und y Koordinaten)
        # # Wird benötigt um die Größe der BoundingBox zu berechnen (siehe unten)
        # min_x, max_x = min(x_coordinates), max(x_coordinates)
        # min_y, max_y = min(y_coordinates), max(y_coordinates)

        # # Bounding Box mithilfe der ermittelten Werte berechnen
        # # Die BoundingBox wird hier benötigt, um eine gute Referenzgröße für die Normierung zu haben
        # bbox_width = max_x - min_x
        # bbox_height = max_y - min_y

        # # Falls die Bounding Box degeneriert (Breite oder Höhe = 0), überspringen
        # if bbox_width == 0 or bbox_height == 0:
        #     return []
    
        # # Keypoints normalisieren
        # # ACHTUNG: Besonders ist auf die Normierung der Z-Koordinate (siehe unten) zu achten
        # # Da Mediapipe die Z-Koordinaten nur schätzt, ist eine Normierung auf Wert/(z_max-z_min) nicht ratsam
        # # Hier ist es besser auf die dominante Größe der BoundingBox zu normieren, damit der Referenzwert zuverlässig ist
        # normalizedData = [
        #     ((x - min_x) / bbox_width, (y - min_y) / bbox_height, z / max(bbox_width, bbox_height))
        #     for x, y, z in keypoints
        # ]

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
                   
                    self.mp_drawing.draw_landmarks(
                        image=image_zoomed,
                        landmark_list=face_landmarks,
                        connections=self.mp_face_mesh.FACEMESH_CONTOURS,
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

            # # X-Koordinaten extrahieren
            # x_coordinates = [x for x, _, _ in keypoints]
            # # Y-Koordinaten extrahieren
            # y_coordinates = [y for _, y, _ in keypoints]

            # # Minimum und Maximum bestimmen (für x und y Koordinaten)
            # # Wird benötigt um die Größe der BoundingBox zu berechnen (siehe unten)
            # min_x, max_x = min(x_coordinates), max(x_coordinates)
            # min_y, max_y = min(y_coordinates), max(y_coordinates)

            # # Bounding Box mithilfe der ermittelten Werte berechnen
            # # Die BoundingBox wird hier benötigt, um eine gute Referenzgröße für die Normierung zu haben
            # bbox_width = max_x - min_x
            # bbox_height = max_y - min_y

            # # Falls die Bounding Box degeneriert (Breite oder Höhe = 0), überspringen
            # if bbox_width == 0 or bbox_height == 0:
            #     continue
        
            # # Keypoints normalisieren
            # # ACHTUNG: Besonders ist auf die Normierung der Z-Koordinate (siehe unten) zu achten
            # # Da Mediapipe die Z-Koordinaten nur schätzt, ist eine Normierung auf Wert/(z_max-z_min) nicht ratsam
            # # Hier ist es besser auf die dominante Größe der BoundingBox zu normieren, damit der Referenzwert zuverlässig ist
            # normalizedData = [
            #     ((x - min_x) / bbox_width, (y - min_y) / bbox_height, z / max(bbox_width, bbox_height))
            #     for x, y, z in keypoints
            # ]

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