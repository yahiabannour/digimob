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
    def __init__(self, cam_id = None, mirrored = True, frame_width=None, frame_height=None, verbose=False):
        self.resolutions = {
            'UHD': (3840, 2160), # 4K UHD
            'QHD': (2560, 1440), # QHD
            'FHD': (1920, 1080), # Full HD
            'HD':  (1280,  720), # HD
            'VGA': ( 640,  480), # VGA
        }
        self.verbose = verbose
        # directshow (DShow) is Windows camera API, while Media Foundation (MSMF) is a newer API.
        # The choice between the two can depend on the specific camera and system configuration.
        # DShow does not exist on Linux/macOS, there OpenCV picks the backend itself (e.g. V4L2).
        if sys.platform == 'win32':
            self.mode = cv2.CAP_DSHOW # cv2.CAP_MSMF
        else:
            self.mode = cv2.CAP_ANY
        if cam_id == None:
            self.autoselect_cam_id()
        else:
            self.cam_id = cam_id
            
        self.mirrored = mirrored
        self.cap = None
        
        self.prev_time = None
        
        self.open(frame_width, frame_height)

        self.valid_resolutions = []
        max_frame_width, max_frame_height = self.probe_resolutions()
        if max_frame_width != -1 and max_frame_height != -1 and frame_width is None and frame_height is None:
            #self.cap.set(cv2.CAP_PROP_FOURCC,
            #             cv2.VideoWriter_fourcc(*'MJPG')) # to get max. resolution on some webcams

            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH,  max_frame_width)
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, max_frame_height)
            actual_w = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            actual_h = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            
            if actual_w != max_frame_width or actual_h != max_frame_height:
                raise Exception("Failed to set frame size.")

        self.queue = queue.Queue(maxsize=1)  # always store the latest frame
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._read_frames, daemon=True)
        self.thread.start()

    def __del__(self):
        self.close()

    def __enter__(self):
        return self

    def __exit__(self, a, b, c):
        self.close()

    def open(self, frame_width=None, frame_height=None) -> None:
        self.cap = cv2.VideoCapture(self.cam_id) # , self.mode)
        if not self.cap.isOpened():
            raise Exception(f"camera {self.cam_id} open failed")

        if frame_width is not None:
            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, frame_width)
        if frame_height is not None:
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, frame_height)

    def close(self) -> None:
        if self.cap is not None:
            self.stop_event.set()
        
            self.cap.release()
            self.cap = None

        if self.thread is not None:
            self.thread.join(timeout=2)  # Wait up to 2 seconds for thread to finish
            self.thread = None

    def autoselect_cam_id(self):
        cam_id = None
        best_cam_width  = None
        #best_cam_height = None
        best_cam_fps    = None
        for i in range(5):
            cap = cv2.VideoCapture(i, self.mode)
            if not cap.isOpened():
                cap.release()
                continue
            
            width  = cap.get(cv2.CAP_PROP_FRAME_WIDTH)
            height = cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
            fps    = cap.get(cv2.CAP_PROP_FPS)
            cap.release()
            
            if self.verbose:
                print(f"- cam {i} : {width:.0f}x{height:.0f}px @ {fps}fps")
            
            if cam_id == None:
                cam_id = i
                best_cam_width  = width
                #best_cam_height = height
                best_cam_fps    = fps
            else:
                # chose better cam ...
                # cam 0 :  640x480px @ 30.0fps -> webcam
                # cam 1 : 1280x720px @ 30.0fps -> virtual cam
                # cam 0 : 2592x1680px @ 10.0fps -> Annastift dia scanner
                
                if fps == -1 and best_cam_fps != -1:
                    continue
                
                opt_width = 640
                if width == opt_width and best_cam_width != opt_width:
                    cam_id = i
                    best_cam_width  = width
                    #best_cam_height = height
                    #best_cam_fps    = fps
                pass
        
        if cam_id == None:
            msg = """Error: No cameras found.
                     Check if user is in group video, else run this and log out and in again.
                     sudo usermod -aG video $USER """
            raise Exception(msg)
        
        if self.verbose:
            print(f"Auto-selected camera {cam_id}")
            
        self.cam_id = cam_id
    
    def probe_resolutions(self):
        orig_w = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        orig_h = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        
        best_w = -1
        best_h = -1
        for key in self.resolutions.keys():
            w, h = self.resolutions[key]
            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, w)
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, h)
        
            actual_w = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            actual_h = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        
            success = actual_w==w and actual_h==h
            if success:
                self.valid_resolutions.append(key)
                if w > best_w and h > best_h:
                   best_w = w
                   best_h = h
                   
            if self.verbose:
                print(f"- {key:3s}: {w:4d}x{h:4d} - {success}")
            
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH,  orig_w)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, orig_h)
        return best_w, best_h
    
    def _read_frames(self):
        while not self.stop_event.is_set():
            try:
                if self.cap == None:
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
                print(f"H2 Exception: {e}")
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
    with Camera(mirrored = False) as cam:
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
