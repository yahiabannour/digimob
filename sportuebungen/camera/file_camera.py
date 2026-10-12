import cv2
import os
import glob
import threading
import time

SUPPORTED_EXTENSIONS = ('.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.tif', '.webp')

# Longest gap between two getFrame() calls that counts as playing time of a video. A longer gap
# (e.g. the intro of an exercise or the pause button, where the app reads no frames) pauses the
# video instead of skipping that part.
MAX_FRAME_GAP = 0.5  # seconds


class FileCamera:
    """Mimics the Camera class, but reads frames from a video file or from image files in a folder.

    Video file: frames are delivered in real time like a live camera. getFrame() waits for the
    next frame when called too early and skips the missed frames when called too late. The
    video only advances while frames are requested (see MAX_FRAME_GAP).
    With realtime = False (Aufnahmen testen) every call returns the next frame instead, none is
    dropped and nothing waits, and timestamp holds that frame's time in the video (seconds). Then
    a test gives the same result on every run, however fast the computer is.
    Image folder: each call to getFrame() loads and returns the next image from disk in order.

    After the last frame stop_event is set and getFrame() returns None. With loop = True playback
    starts over at the first frame instead, and plays counts the finished passes (the intro shows
    the demo a fixed number of times). rewind() starts over at any time.

    frame_width, frame_height: every frame is scaled into this size, keeping its aspect ratio
    (black borders), like a camera opened with that size. None keeps the original size.

    Usage:
        with FileCamera('/path/to/video.mp4') as cam:
            while not cam.stop_event.is_set():
                frame = cam.getFrame()  # RGB numpy array, None after the last frame
                ...
    """

    def __init__(self, path: str, mirrored: bool = False, frame_width=None, frame_height=None,
                 verbose: bool = False, realtime: bool = True):
        self.path = path
        self.mirrored = mirrored
        self.realtime = realtime
        self.timestamp = None    # realtime=False: time of the last frame in the video (s), else None
        self.frame_size = (frame_width, frame_height) if frame_width and frame_height else None
        self.verbose = verbose
        self.loop = False
        self.plays = 0           # loop = True: how often the video has played to its end
        self.stop_event = threading.Event()
        self.current_file = None

        self.is_video = not os.path.isdir(path)
        self._cap = None
        if self.is_video:
            self._cap = cv2.VideoCapture(path)
            if not self._cap.isOpened():
                raise FileNotFoundError(f"FileCamera: cannot open video '{path}'")
            self.fps = self._cap.get(cv2.CAP_PROP_FPS) or 30.0  # 0 if the file has no frame rate
            self.current_file = path
            if verbose:
                width = int(self._cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                height = int(self._cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                print(f"FileCamera: video '{path}', {width}x{height}px @ {self.fps:g}fps")
        else:
            self._image_paths = self._discover_images(path)
            if not self._image_paths:
                raise FileNotFoundError(
                    f"No supported image files found in '{path}'. "
                    f"Supported extensions: {SUPPORTED_EXTENSIONS}"
                )
            if verbose:
                print(f"FileCamera: found {len(self._image_paths)} image(s) in '{path}'")
                for p in self._image_paths:
                    print(f"  {p}")

        self.rewind()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def close(self) -> None:
        self.stop_event.set()
        if self._cap is not None:
            self._cap.release()
            self._cap = None

    def rewind(self) -> None:
        """Start over at the first frame."""
        self._index = 0          # next image / next video frame
        self._video_time = 0.0   # playing time of the video in seconds
        self._last_call = None   # time of the last getFrame() call (video)
        if self._cap is not None:
            self._cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
        self.stop_event.clear()

    def getFrame(self):
        """Return the next frame as an RGB numpy array, None after the last frame."""
        if self.stop_event.is_set():
            return None

        if self.is_video:
            img = self._next_video_frame()
            if img is None and self.loop:
                self.plays += 1
                self.rewind()
                img = self._next_video_frame()
            if img is None:
                self.stop_event.set()
                return None
        else:
            img = self._next_image()

        if self.frame_size is not None:
            img = self._fit(img)

        if self.mirrored:
            img = cv2.flip(img, 1)

        return cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

    def _next_image(self):
        """Load the next image (BGR)."""
        path = self._image_paths[self._index]
        self.current_file = path

        self._index += 1
        if self._index >= len(self._image_paths):
            if self.loop:
                self._index = 0
            else:
                self.stop_event.set()

        img = cv2.imread(path)
        if img is None:
            raise IOError(f"FileCamera: cannot read '{path}'")
        return img

    def _next_video_frame(self):
        """Read the video frame that is due now (BGR), None at the end of the video."""
        if not self.realtime:
            # every frame in order, with its own time in the video
            ok, img = self._cap.read()
            self.timestamp = self._index / self.fps
            self._index += 1
            return img if ok else None

        now = time.monotonic()
        if self._last_call is not None:
            gap = now - self._last_call
            if gap <= MAX_FRAME_GAP:    # longer gap = paused: the video stands still
                self._video_time += gap

        due = int(self._video_time * self.fps)
        if due < self._index:
            # called before the next frame is due: wait for it like a live camera
            time.sleep(self._index / self.fps - self._video_time)
            self._video_time = self._index / self.fps
            due = self._index
            now = time.monotonic()
        self._last_call = now

        # called too late: drop the missed frames like a live camera
        while self._index < due and self._cap.grab():
            self._index += 1

        ok, img = self._cap.read()
        self._index += 1
        return img if ok else None

    def _fit(self, img):
        """Scale img into frame_size keeping the aspect ratio, pad the rest with black."""
        width, height = self.frame_size
        h, w = img.shape[:2]
        if (w, h) == (width, height):
            return img

        scale = min(width / w, height / h)
        new_w, new_h = round(w * scale), round(h * scale)
        interpolation = cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR
        img = cv2.resize(img, (new_w, new_h), interpolation=interpolation)

        top = (height - new_h) // 2
        left = (width - new_w) // 2
        return cv2.copyMakeBorder(img, top, height - new_h - top, left, width - new_w - left,
                                  cv2.BORDER_CONSTANT, value=0)

    @staticmethod
    def _discover_images(folder: str) -> list:
        """Return a sorted list of image file paths found in *folder*."""
        paths = []
        for ext in SUPPORTED_EXTENSIONS:
            paths.extend(glob.glob(os.path.join(folder, f'*{ext}')))
            paths.extend(glob.glob(os.path.join(folder, f'*{ext.upper()}')))
        return sorted(set(paths))


# ------------------------------------------------------------------
# Quick smoke-test  (usage: python file_camera.py /path/to/video.mp4
#                        or python file_camera.py /path/to/images)
# ------------------------------------------------------------------

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = r'C:\workspace\cairelab\python\eye_control\train_xgaze\img\persons\subject0001'

    with FileCamera(path, mirrored=False, verbose=True) as cam:
        delay = 1 if cam.is_video else 200  # a video is paced by getFrame() itself
        while not cam.stop_event.is_set():
            img_rgb = cam.getFrame()
            if img_rgb is None:
                break
            img_bgr = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
            cv2.imshow('FileCamera', img_bgr)
            if cv2.waitKey(delay) & 0xFF == ord('q'):
                break

    cv2.destroyAllWindows()
