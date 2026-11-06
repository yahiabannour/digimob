"""
******************************************************************************
 file:      camera/camera.py
 brief:     Live-Webcam als Bildquelle: ein Hintergrund-Thread liest die Kamera, getFrame()
            liefert das neueste Bild (RGB, ungespiegelt). Gleiche Schnittstelle wie FileCamera.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 ext. libs: OpenCV
******************************************************************************
"""
import logging

import cv2
import threading
import queue
import os
import sys

# The Qt in opencv-python ships without fonts and 'import cv2' points QT_QPA_FONTDIR at its
# empty font folder -> empty tooltips in the imshow window. Must be set after 'import cv2'
# and before the first imshow. /usr/share/fonts/TTF is the Arch path, other distros differ.
if sys.platform.startswith('linux') and os.path.isdir('/usr/share/fonts/TTF'):
    os.environ['QT_QPA_FONTDIR'] = '/usr/share/fonts/TTF'

class Camera():
    def __init__(self, cam_id, mirrored = True, frame_width=None, frame_height=None):
        self.cam_id = cam_id
        self.mirrored = mirrored
        self.cap = None
        # Before open(): if it fails, __del__ -> close() still finds them
        self.stop_event = threading.Event()
        self.thread = None

        self.open(frame_width, frame_height)

        self.queue = queue.Queue(maxsize=1)  # always store the latest frame
        self.thread = threading.Thread(target=self._read_frames, daemon=True)
        self.thread.start()

    def __del__(self):
        self.close()

    def __enter__(self):
        return self

    def __exit__(self, a, b, c):
        self.close()

    def open(self, frame_width=None, frame_height=None) -> None:
        self.cap = cv2.VideoCapture(self.cam_id)
        if not self.cap.isOpened():
            raise Exception(f"camera {self.cam_id} open failed")

        if frame_width is not None:
            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, frame_width)
        if frame_height is not None:
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, frame_height)

    def close(self) -> None:
        # Stop the reader thread first, then release the camera: release() while the thread is
        # inside cap.read() can crash in OpenCV
        self.stop_event.set()
        if self.thread is not None:
            self.thread.join(timeout=2)  # Wait up to 2 seconds for thread to finish
            self.thread = None

        if self.cap is not None:
            self.cap.release()
            self.cap = None

    def _read_frames(self):
        while not self.stop_event.is_set():
            try:
                if self.cap is None:
                    raise Exception("camera capture failed (1)")
                    
                if not self.cap.isOpened():
                    raise IOError("Error accessing the camera.")
    
                ret, frame = self.cap.read()
                if not ret:
                    if self.stop_event.is_set():
                        break
                    raise Exception("camera capture failed (2)")
    
                try:
                    # remove old frame if any
                    self.queue.get_nowait()
                except queue.Empty:
                    pass
                
                self.queue.put((ret, frame))
            except Exception as e:
                # Ins Log statt nur in die Konsole; die App kann die Kamera beim nächsten Start
                # einer Übung neu öffnen (SportApp.ensure_live_camera)
                logging.error(f"Kamera {self.cam_id}: {e}")
                self.stop_event.set()
                
    def getFrame(self) -> None:
        # None once the reader thread stopped (camera error), instead of waiting forever for a frame
        while True:
            try:
                ret, img = self.queue.get(timeout=0.5)
                break
            except queue.Empty:
                if self.stop_event.is_set():
                    return None

        if self.mirrored:
            img = cv2.flip(img, 1) # mirror horizontally
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

        return img_rgb

if __name__ == '__main__':
    with Camera(cam_id=0, mirrored = False) as cam:
        try:
            while not cam.stop_event.is_set():
                img_rgb = cam.getFrame()
                if img_rgb is None:
                    break
                img_bgr = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
    
                cv2.imshow('frame', img_bgr)
                key = cv2.waitKey(1)
                if key & 0xFF == ord('q'):
                    break
                elif key & 0xFF == ord('s'):
                    img_path = os.path.dirname(os.path.abspath(__file__))
                    img_filename = os.path.join(img_path, 'test.png')
                    print(f'Writing image: {img_filename}')
                    if cv2.imwrite(img_filename, img_bgr):
                        print(f'Image written: {img_filename}')
                    else:
                        print(f'Error: failed to write image: {img_filename}')

        except Exception as e:
            print(f"HH Exception: {e}")
            cam.close() 
            
    cv2.destroyAllWindows()
