"""
******************************************************************************
 file:      uebungslauf.py
 brief:     Die eine Schleife der App. Pro Tick: Frame -> Zoom -> Pose -> Uebung -> Anzeige.
            Fuehrt eine Warteschlange von Uebungen durch Einfuehrung, Zaehlen und Fertig.
 version:   1.1.9
 date:      25.09.2026
 software:  Python (Version: 3.12)
 info:      Kennt weder Tkinter noch eine bestimmte Uebung. Hauptprogramm.py ruft tick() alle
            ~10 ms auf (window.after), live mit der Webcam und im Aufnahmen-Test mit einem
            Video (aufnahmen_test.py) - der Unterschied steckt nur in den Argumenten von start().
******************************************************************************
"""
import logging
import time

from camera.file_camera import FileCamera

INTRO_SECONDS = 10      # 3D-Demo (oder Beispielbild) + Ansage
DONE_SECONDS = 5        # "Prima, gute Leistung!" stehen lassen, dann die naechste Uebung
DEMO_SIZE = (1200, 900) # die Demo in der Groesse, in der die GUI sie zeigt (nicht wie die Kamera 640x480)


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

    def start(self, cam, uebungen, on_source_end, on_finished=None, intro=True):
        """cam: Camera oder FileCamera (getFrame() liefert RGB). on_source_end(uebung): keine Bilder
        mehr (Kamera ausgefallen, Video zu Ende). on_finished(): alle Uebungen fertig.
        intro=False: ohne Beispielbild und Ansage, gezaehlt wird ab dem ersten Frame."""
        self.cam, self.queue, self.intro = cam, list(uebungen), intro
        self.on_source_end, self.on_finished = on_source_end, on_finished
        self.running = True
        self.next()

    def stop(self):
        self.running = False
        self.close_demo()

    def next(self):
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
            if now - self.phase_since >= INTRO_SECONDS:
                self.close_demo()
                self.set_phase("counting")
            return

        frame = self.cam.getFrame()
        if frame is None:           # das Ende der Bildquelle erkennt der naechste Tick
            return

        image_bgr, image_rgb = self.bild.zoom(frame)
        pose = self.pose.detect(image_rgb)
        uebung.update(pose, now)
        # Ansagen nur waehrend gezaehlt wird, ohne Ziel (Aufnahmen-Test) nie
        if self.phase == "counting" and uebung.valid and uebung.target is not None:
            self.sprecher.milestones(uebung.total, uebung.target)

        balken = None if self.phase == "done" else uebung.progress()
        self.show(self.bild.overlay(image_bgr, pose, uebung), balken, uebung.bar_style)

        if self.phase == "counting" and uebung.done:
            self.set_phase("done")
        elif self.phase == "done" and now - self.phase_since >= DONE_SECONDS:
            self.next()
