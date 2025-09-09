"""
******************************************************************************
 file:      mediapipe_modelle.py
 brief:     Software zur Anzeigen die wichtigesten Daten z.B. Winkel, Armwiederholungen für alle Sportübungen auf ein fenster
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi, Mazen Al-Hakami
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.4
 date:      09.05.2025
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

        # Keypoints visualisieren
        if self.visualize_active:
            if results.pose_landmarks:
                self.mp_drawing.draw_landmarks(
                    image_zoomed, results.pose_landmarks, self.mp_pose.POSE_CONNECTIONS,
                    self.mp_drawing.DrawingSpec(color=(0, 0, 255), thickness=2, circle_radius=2),
                    self.mp_drawing.DrawingSpec(color=(255, 255, 255), thickness=2, circle_radius=2)
                )

        return results, image_zoomed
    

    def face_recognition(self):
        image_zoomed, image_mediapipe = self.bildoperationen.video_frame()

        # Führe die Gesicht-Erkennung durch
        results = self.face_mesh.process(image_mediapipe)

        # Keypoints visualisieren
        if self.visualize_active:
            if results.multi_face_landmarks:
                for face_landmarks in results.multi_face_landmarks:
                    # Alle 468 Keypoints visualisieren
                    # self.mp_drawing.draw_landmarks(
                    #     image_BGR, face_landmarks, self.mp_face_mesh.FACEMESH_TESSELATION,
                    #     self.mp_drawing.DrawingSpec(color=(255, 255, 255), thickness=1, circle_radius=1),
                    #     self.mp_drawing.DrawingSpec(color=(0, 0, 0), thickness=1)
                    # )
                    self.mp_drawing.draw_landmarks(
                        image=image_zoomed,
                        landmark_list=face_landmarks,
                        connections=self.mp_face_mesh.FACEMESH_CONTOURS,
                        landmark_drawing_spec=None,
                        connection_drawing_spec=self.mp_drawing_styles
                        .get_default_face_mesh_contours_style()
                        )
                    # self.mp_drawing.draw_landmarks(
                    #     image=image_BGR,
                    #     landmark_list=face_landmarks,
                    #     connections=self.mp_face_mesh.FACEMESH_IRISES,
                    #     landmark_drawing_spec=None,
                    #     connection_drawing_spec=self.mp_drawing_styles
                    #     .get_default_face_mesh_iris_connections_style()
                    #     )
                    selected_indices = [
                        # Nase, linke Auge, rechte Auge
                        1, 33, 263
                    ]

                    for idx in selected_indices:
                        h, w, _ = image_zoomed.shape
                        x = int(face_landmarks.landmark[idx].x * w)
                        y = int(face_landmarks.landmark[idx].y * h)
                        cv2.circle(image_zoomed, (x, y), 5, (255, 0, 0), -1)

        return results, image_zoomed