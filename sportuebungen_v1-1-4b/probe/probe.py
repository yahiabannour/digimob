import tkinter as tk
from tkinter import ttk
import cv2
from PIL import Image, ImageTk

class CameraZoomApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Webcam mit Zoom und statischem Text")
        
        # Webcam initialisieren
        self.cap = cv2.VideoCapture(0)
        if not self.cap.isOpened():
            raise IOError("Kamera konnte nicht geöffnet werden!")
        
        self.zoom_factor = 1.0
        self.text = "Statischer Text"
        
        # GUI-Elemente
        self.setup_gui()
        
        # Kamera-Update starten
        self.update_camera()
    
    def setup_gui(self):
        # Haupt-Frame
        self.main_frame = ttk.Frame(self.root)
        self.main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Zoom-Buttons
        self.button_frame = ttk.Frame(self.main_frame)
        self.button_frame.pack(side=tk.TOP, fill=tk.X, pady=5)
        
        self.zoom_in_btn = ttk.Button(self.button_frame, text="Zoom In (+)", command=lambda: self.zoom(1.2))
        self.zoom_in_btn.pack(side=tk.LEFT, padx=5)
        
        self.zoom_out_btn = ttk.Button(self.button_frame, text="Zoom Out (-)", command=lambda: self.zoom(0.8))
        self.zoom_out_btn.pack(side=tk.LEFT, padx=5)
        
        self.reset_btn = ttk.Button(self.button_frame, text="Reset Zoom", command=lambda: self.zoom(1.0, reset=True))
        self.reset_btn.pack(side=tk.LEFT, padx=5)
        
        # Canvas für Kamera-Bild + Text
        self.canvas = tk.Canvas(self.main_frame, bg="black")
        self.canvas.pack(fill=tk.BOTH, expand=True)
        
        # Statischer Text (wird später überlagert)
        self.text_label = ttk.Label(
            self.canvas,
            text=self.text,
            font=('Arial', 16),
            background="black",
            foreground="white"
        )
        self.canvas.create_window(20, 20, window=self.text_label, anchor=tk.NW)
    
    def zoom(self, factor, reset=False):
        if reset:
            self.zoom_factor = 1.0
        else:
            self.zoom_factor *= factor
            self.zoom_factor = max(0.1, min(self.zoom_factor, 5.0))  # Zoom begrenzen
    
    def update_camera(self):
        ret, frame = self.cap.read()
        if ret:
            # Bild in RGB konvertieren
            frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            
            # Zoom anwenden (falls nicht 1.0)
            if self.zoom_factor != 1.0:
                h, w = frame.shape[:2]
                new_h, new_w = int(h / self.zoom_factor), int(w / self.zoom_factor)
                start_x, start_y = (w - new_w) // 2, (h - new_h) // 2
                cropped = frame[start_y:start_y+new_h, start_x:start_x+new_w]
                frame = cv2.resize(cropped, (w, h))
            
            # Bild in Tkinter-Format konvertieren
            img = Image.fromarray(frame)
            imgtk = ImageTk.PhotoImage(image=img)
            
            # Bild auf Canvas anzeigen
            self.canvas.imgtk = imgtk  # Referenz halten (verhindert Garbage Collection)
            self.canvas.create_image(0, 0, anchor=tk.NW, image=imgtk)
            
            # Text-Label bleibt oben links (20,20)
            self.text_label.config(text=f"{self.text} (Zoom: {self.zoom_factor:.1f}x)")
        
        # Nächsten Frame nach 10ms laden
        self.root.after(10, self.update_camera)
    
    def __del__(self):
        if self.cap.isOpened():
            self.cap.release()

if __name__ == "__main__":
    root = tk.Tk()
    app = CameraZoomApp(root)
    root.geometry("800x600")
    root.mainloop()