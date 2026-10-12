"""
******************************************************************************
 file:      uebungslauf.py
 brief:     Die eine Schleife der App. Pro Tick: Frame -> Zoom -> Pose -> Uebung -> Anzeige.
            Fuehrt eine Warteschlange von Uebungen durch Einfuehrung, Zaehlen und Fertig.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 info:      Kennt weder Tkinter noch eine bestimmte Uebung. Hauptprogramm.py ruft tick() alle
            ~10 ms auf (window.after), live mit der Webcam und im Aufnahmen-Test mit einem
            Video (aufnahmen_test.py) - der Unterschied steckt nur in den Argumenten von start().
            Live laeuft die Pose-Erkennung in einem eigenen Thread (PoseThread), damit sie die
            Oberflaeche nicht blockiert; der Aufnahmen-Test rechnet jedes Bild der Reihe nach im
            Takt der Oberflaeche (gleiches Ergebnis bei jedem Durchlauf).
******************************************************************************
"""
import logging
import queue
import threading
import time

from camera.file_camera import FileCamera

INTRO_SECONDS = 10      # Einfuehrung mindestens so lange, ohne Demo nur das Beispielbild + Ansage
DEMO_PLAYS = 1          # so oft laeuft die 3D-Demo ganz durch, bevor gezaehlt wird: jede Demo zeigt 2-3
                        # Wiederholungen, so viele sollen die Patienten sehen (eine Demo dauert 9-18 s)
DONE_SECONDS = 5        # "Prima, gute Leistung!" stehen lassen, dann die naechste Uebung
DEMO_SIZE = (1200, 900) # die Demo in der Groesse, in der die GUI sie zeigt (nicht wie die Kamera 640x480)
POSE_FEHLER = 50        # so viele Bilder hintereinander scheitert die Pose-Erkennung, dann gibt der PoseThread auf


class PoseThread:
    """Live: liest die Kamera und erkennt die Pose im Hintergrund, behaelt nur das neueste
    Ergebnis (image_bgr, pose, zeit). Die Pose-Erkennung blockierte sonst die Oberflaeche
    (~80 ms je Bild); so zeichnet die Oberflaeche ein Bild, waehrend das naechste erkannt wird.
    Solange er laeuft, benutzt nur dieser Thread Kamera, Zoom und Pose-Modell."""

    def __init__(self, cam, bild, pose, paused):
        self.cam, self.bild, self.pose, self.paused = cam, bild, pose, paused
        self.results = queue.Queue(maxsize=1)
        self.error = None           # gesetzt, wenn die Erkennung POSE_FEHLER-mal in Folge scheiterte
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._run, name="PoseThread", daemon=True)
        self.thread.start()

    def _run(self):
        failures = 0
        while not self.stop_event.is_set():
            if self.paused():
                time.sleep(0.05)
                continue
            frame = self.cam.getFrame()         # wartet auf das naechste Kamerabild
            if frame is None:
                if self.cam.stop_event.is_set():
                    return                      # Kamera aus: tick() meldet das Ende
                continue
            now = time.time()
            try:
                image_bgr, image_rgb = self.bild.zoom(frame)
                pose = self.pose.detect(image_rgb)
            except Exception as e:
                logging.exception("Pose-Erkennung: Bild uebersprungen")
                failures += 1
                if failures >= POSE_FEHLER:
                    # Sonst stuende das letzte Bild ohne Meldung: tick() wirft den Fehler weiter
                    self.error = e
                    return
                continue
            failures = 0
            try:
                self.results.get_nowait()       # aelteres, nicht abgeholtes Ergebnis verwerfen
            except queue.Empty:
                pass
            self.results.put((image_bgr, pose, now))

    def latest(self):
        """Neuestes Ergebnis oder None, ohne zu warten."""
        try:
            return self.results.get_nowait()
        except queue.Empty:
            return None

    def stop(self):
        self.stop_event.set()
        self.thread.join(timeout=2)


class Uebungslauf:
    def __init__(self, pose, bild, sprecher, show):
        self.pose = pose            # MediapipeModelle
        self.bild = bild            # Bildoperationen: Zoom + Overlay
        self.sprecher = sprecher    # SprachAusgabe
        self.show = show            # Anzeige: show(bild_bgr oder None, balken, bar_style)
                                    # balken: {seite: 0..1 oder None} oder None (alle leer)
        self.running = False
        self.paused = False
        self.demo = None            # FileCamera der 3D-Demo, nur waehrend der Einfuehrung
        self.worker = None          # PoseThread, live nur waehrend Zaehlen/Fertig

    def start(self, cam, uebungen, on_source_end, on_finished=None, intro=True):
        """cam: Camera oder FileCamera (getFrame() liefert RGB). on_source_end(uebung): keine Bilder
        mehr (Kamera ausgefallen, Video zu Ende). on_finished(): alle Uebungen fertig.
        intro=False: ohne Beispielbild und Ansage, gezaehlt wird ab dem ersten Frame."""
        self.stop_worker()
        self.cam, self.queue, self.intro = cam, list(uebungen), intro
        self.on_source_end, self.on_finished = on_source_end, on_finished
        # Live (Kamera) im Hintergrund erkennen, eine Videodatei (Aufnahmen-Test) im Takt der
        # Oberflaeche: jedes Bild, mit seiner Zeit im Video
        self.threaded = cam is not None and not isinstance(cam, FileCamera)
        self.pose.reset()           # Erkennung ohne Gedaechtnis an den vorigen Lauf
        self.running = True
        self.next()

    def stop(self):
        self.running = False
        self.stop_worker()
        self.close_demo()
        self.sprecher.stop()        # Stop: keine Ansage mehr, auch keine wartende

    def next(self):
        self.stop_worker()
        self.close_demo()
        if not self.queue:
            self.running = False
            if self.on_finished:
                self.on_finished()
            return
        self.uebung = self.queue.pop(0)
        self.sprecher.reset()
        self.set_phase("intro" if self.intro else "counting")
        if self.intro:
            self.intro_image = self.bild.load(self.uebung.image)
            self.demo = self.open_demo(self.uebung.demo)
            self.sprecher.say(self.uebung.intro)

    @staticmethod
    def open_demo(path):
        """3D-Demo der Uebung als Endlosschleife, gespiegelt wie das Kamerabild. None ohne Demo:
        dann bleibt das Beispielbild."""
        if not path:
            return None
        try:
            demo = FileCamera(path, mirrored=True, frame_width=DEMO_SIZE[0], frame_height=DEMO_SIZE[1])
        except FileNotFoundError as e:
            logging.warning(f"Keine Demo, zeige das Beispielbild: {e}")
            return None
        demo.loop = True
        return demo

    def stop_worker(self):
        if self.worker is not None:
            self.worker.stop()
            self.worker = None

    def close_demo(self):
        if self.demo is not None:
            self.demo.close()
            self.demo = None

    def set_phase(self, phase):
        self.phase, self.phase_since = phase, time.time()

    def tick(self):
        if not self.running or self.paused:
            return
        uebung, now = self.uebung, time.time()

        # Keine Kamera, Kamera ausgefallen oder Video zu Ende
        if self.cam is None or self.cam.stop_event.is_set():
            self.on_source_end(uebung)
            return

        if self.phase == "intro":
            # 3D-Demo (sonst Beispielbild) zeigen, waehrend die Ansage laeuft; die Kamera wird
            # nicht gelesen. Die Demo steht bei Pause still (FileCamera: MAX_FRAME_GAP)
            if self.demo is not None:
                frame = self.demo.getFrame()
                if frame is not None:
                    self.intro_image = self.bild.demo_frame(frame)
            self.show(self.intro_image, None, uebung.bar_style)
            demo_done = self.demo is None or self.demo.plays >= DEMO_PLAYS
            if demo_done and now - self.phase_since >= INTRO_SECONDS:
                self.close_demo()
                self.set_phase("counting")
            return

        if self.threaded:
            # Live: das neueste Ergebnis des PoseThread, noch keins -> naechster Tick
            if self.worker is None:
                self.worker = PoseThread(self.cam, self.bild, self.pose, lambda: self.paused)
            if self.worker.error is not None:
                # Hauptprogramm.loop() zaehlt die Fehler und beendet die Uebung mit einer Meldung
                raise RuntimeError(f"Pose-Erkennung ausgefallen: {self.worker.error}")
            result = self.worker.latest()
            if result is None:
                return
            image_bgr, pose, frame_time = result
        else:
            frame = self.cam.getFrame()
            if frame is None:           # das Ende der Bildquelle erkennt der naechste Tick
                return
            # Zeit des Bildes: im Aufnahmen-Test die Zeit im Video (FileCamera mit realtime=False),
            # damit Pose und Haltezeit bei jedem Durchlauf gleich ausfallen; sonst die Uhr
            video_time = getattr(self.cam, "timestamp", None)
            frame_time = now if video_time is None else video_time
            image_bgr, image_rgb = self.bild.zoom(frame)
            pose = self.pose.detect(image_rgb, None if video_time is None else int(video_time * 1000))
        uebung.update(pose, frame_time)
        # Ansagen nur waehrend gezaehlt wird, ohne Ziel (Aufnahmen-Test) nie
        if self.phase == "counting" and uebung.valid and uebung.target is not None:
            self.sprecher.milestones(uebung.total, uebung.target)

        balken = None if self.phase == "done" else uebung.progress()
        self.show(self.bild.overlay(image_bgr, pose, uebung), balken, uebung.bar_style)

        if self.phase == "counting" and uebung.done:
            self.set_phase("done")
        elif self.phase == "done" and now - self.phase_since >= DONE_SECONDS:
            self.next()
