"""
******************************************************************************
 file:      calculate_angle_distance.py
 brief:     Software zur Berechnung von Winkel zwischen 3 Keypointer und Abstand zwischen 2 Keypointer 
 author(s): Ezaldeen Al-Shahethi, Abdulatif Mujali, Jörg Schneider
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.3
 date:      17.04.2025
 software:  Python (Version: 3.12.3)
 int. libs: 
 ext. libs: Numpy (Version: 1.26.4)
 info:
******************************************************************************
"""
import numpy as np          # NumPy-Bibliothek für numerische Operationen

# Funktion zur Berechnung des Winkels zwischen drei Punkten (Landmarken)
def calculate_angle(landmark_1, landmark_2, landmark_3):
    # Die Landmarken zu NumPy-Arrays Konvertieren
    joint_a = np.array(landmark_1)
    joint_b = np.array(landmark_2)
    joint_c = np.array(landmark_3)
    # Vektoren berechnen
    vector_ba = joint_a - joint_b
    vector_bc = joint_c - joint_b
    # Skalarprodukt berechnen
    dot_product = np.dot(vector_ba, vector_bc)
    # Beträge der Vektoren berechnen
    magnitude_ab = np.linalg.norm(vector_ba)
    magnitude_bc = np.linalg.norm(vector_bc)
    # Winkel in Grad berechnen
    cos_angle = np.clip(dot_product / (magnitude_ab * magnitude_bc), -1.0, 1.0)
    angle = np.degrees(np.arccos(cos_angle))

    return angle


# Funktion zur Berechnung von Abstand zwischen zwei Punkten (Landmarken)
def calculate_distance(landmark_1, landmark_2):
    joint_a = np.array(landmark_1)                  # Konvertiert landmark_1 in ein NumPy-Array
    joint_b = np.array(landmark_2)                 # Konvertiert landmark_2 in ein NumPy-Array
    return np.linalg.norm(joint_a - joint_b)   # Berechnet die euklidische Distanz