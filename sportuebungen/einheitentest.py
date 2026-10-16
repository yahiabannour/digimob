"""
******************************************************************************
 file:      einheitentest.py
 brief:     Einheitentests: Rechenfunktionen, Zaehler mit kuenstlichen Posen, Schwellen und ihre
            Datei, Balkenanzeige, Videoquelle, Kamera-Fehlerfall und die Schleife mit dem
            Pose-Thread. Ergaenzt systest.py (echte Aufnahmen) um Grenz- und Fehlerfaelle.

            Aufruf (aus sportuebungen/):  pytest   (laeuft zusammen mit systest.py)
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 ext. libs: pytest, numpy, opencv
 info:      Ohne Kamera (bis auf den Fehlerfall einer nicht vorhandenen), Fenster, Ton und
            Pose-Modell. Posen werden aus Winkeln gebaut, wie sie MediaPipe liefern wuerde.
******************************************************************************
"""
import math
import subprocess
import sys
import threading
import time
from collections import namedtuple

import cv2
import numpy as np
import pytest

from abduktion import Abduktion
from balken import BESTAETIGUNG_S, HALTEN_S, BalkenZustand
from bizepscurls import Bizepscurls
from calculate_angle_distance import calculate_angle, calculate_progress
from camera.file_camera import FileCamera
from halswirbelsaeule import Halswirbelsaeule
from pose import ELLBOGEN, HANDGELENK, HUEFTE, SCHULTER, Pose
import schwellen
from uebung import PRESETS, Balken, Haltezeit, Uebung
from uebungslauf import Uebungslauf

Landmark = namedtuple("Landmark", "x y z visibility presence")


def pose(arm=(20, 20), elbow=(180, 180), visibility=1.0):
    """Kuenstliche Pose: Oberkoerper senkrecht, je Arm der Winkel Huefte-Schulter-Ellbogen (arm)
    und Handgelenk-Ellbogen-Schulter (elbow) in Grad, Reihenfolge (links, rechts)."""
    points = [(0.5, 0.5)] * 33
    points = list(points)
    for k, side in enumerate(("left", "right")):
        sx = 0.4 if side == "left" else 0.6
        sign = -1 if side == "left" else 1      # Arme gehen nach aussen
        shoulder, hip = (sx, 0.4), (sx, 0.8)
        a = math.radians(arm[k])
        upper = (sign * math.sin(a), math.cos(a))           # Richtung Schulter -> Ellbogen (y nach unten)
        el = (shoulder[0] + 0.2 * upper[0], shoulder[1] + 0.2 * upper[1])
        # Unterarm: um (180 - elbow) Grad gegen den Oberarm gedreht
        b = math.radians(180 - elbow[k])
        fx = upper[0] * math.cos(b) - upper[1] * math.sin(b)
        fy = upper[0] * math.sin(b) + upper[1] * math.cos(b)
        wr = (el[0] + 0.2 * fx, el[1] + 0.2 * fy)
        points[HUEFTE[side]], points[SCHULTER[side]] = hip, shoulder
        points[ELLBOGEN[side]], points[HANDGELENK[side]] = el, wr
    return Pose([Landmark(x, y, 0.0, visibility, 1.0) for x, y in points], (640, 480))


def run(uebung, frames, fps=30, t0=0.0):
    """frames: Liste von Posen (oder None); Zeit in Schritten von 1/fps."""
    for i, p in enumerate(frames):
        uebung.update(p, t0 + i / fps)
    return uebung


# --- Rechenfunktionen ------------------------------------------------------------------------

def test_winkel():
    assert calculate_angle((0, 1), (0, 0), (1, 0)) == pytest.approx(90)
    assert calculate_angle((-1, 0), (0, 0), (1, 0)) == pytest.approx(180)
    assert calculate_angle((1, 0), (0, 0), (2, 0)) == pytest.approx(0)


def test_winkel_ohne_strecke_ist_fehler():
    with pytest.raises(ValueError):
        calculate_angle((0, 0), (0, 0), (1, 1))


def test_fortschritt():
    assert calculate_progress(85, 20, 150) == pytest.approx(0.5)
    assert calculate_progress(130, 160, 110) == pytest.approx(0.6)      # Ziel unter dem Start
    assert calculate_progress(10, 20, 150) == 0.0
    assert calculate_progress(200, 20, 150) == 1.0
    assert calculate_progress(200, 20, 150, upper=1.25) == 1.25
    assert calculate_progress(5, 3, 3) == 0.0                           # kein Bereich
    assert calculate_progress(float("nan"), 20, 150) == 0.0             # kein Messwert


def test_haltezeit():
    h = Haltezeit(0.2)
    assert h.confirm("up", 0.0) is None
    assert h.confirm("up", 0.1) is None
    assert h.confirm("up", 0.25) == "up"
    assert h.confirm("down", 0.3) is None       # neue Phase: Zeit beginnt neu
    h.reset()
    assert h.confirm("down", 0.31) is None


def test_kopfdrehung_ohne_augenabstand_ist_fehler():
    lm = [Landmark(0.5, 0.5, 0.0, 1.0, 1.0)] * 33
    with pytest.raises(ValueError):
        Halswirbelsaeule.head_rotation(lm, 640, 480)


# --- Gemeinsamer Ablauf (Uebung) -------------------------------------------------------------

def test_presets_und_pruefung():
    assert Abduktion.werte("voll") == {"max_angle": 150}
    assert Abduktion.werte("einfach") == {"max_angle": 120}
    assert Abduktion.preset_von({"max_angle": 120}) == "einfach"
    assert Abduktion.preset_von({"max_angle": 133}) is None
    assert Uebung.pruefe({"min_angle": 40, "max_angle": 150}) is None
    assert Uebung.pruefe({"min_angle": 80, "max_angle": 70}) is not None
    assert Uebung.pruefe({"max_angle": 0}) is not None


def test_schwellen_nur_im_bereich():
    from uebungen import UEBUNGEN
    from butterfly import Butterfly
    from rotation import Rotation
    for name, cls in UEBUNGEN.items():
        assert set(cls.bereich) == set(cls.schwellen), name                # jeder Regler hat einen Bereich
        assert all(cls.pruefe(cls.werte(p)) is None for p in PRESETS), name  # Voll und Einfach liegen darin
    # Ausserhalb zaehlte die Uebung bei jedem Bild (Rotation, Butterfly) oder nie (Abduktion, Bizepscurls)
    assert Rotation.pruefe({"max_distance": 0.5}) is not None
    assert Butterfly.pruefe({"max_distance": 0.9}) is not None
    assert Abduktion.pruefe({"max_angle": 10}) is not None
    assert Bizepscurls.pruefe({"min_angle": 170, "max_shoulder": 50}) is not None
    assert Abduktion.pruefe({"max_angle": float("nan")}) is not None


def test_verbleibende_wiederholungen_nie_negativ():
    links = [pose((20, 20))] * 6 + [pose((160, 20))] * 6
    u = run(Abduktion(target=2), links * 4 + [pose((20, 20))] * 6)
    assert u.counter == {"left": 4, "right": 0}
    assert {side: b.zahl for side, b in u.progress().items()} == {"left": 0, "right": 2}


def test_erwartet_je_seite():
    from tools.recording_format import erwartet_je_seite
    assert erwartet_je_seite(Abduktion(target=None), 5) == {"left": 5, "right": 5}
    assert erwartet_je_seite(Abduktion(target=None), 3, {"left": 3, "right": 5}) == {"left": 3, "right": 5}


def test_ohne_pose_ungueltig_und_leerer_balken():
    u = Abduktion(target=None)
    u.update(None, 0.0)
    assert not u.valid
    assert all(b.fill is None and not b.zurueck for b in u.progress().values())
    assert {b.zahl for b in u.progress().values()} == {0}


def test_fehler_in_measure_macht_frame_ungueltig():
    class Kaputt(Abduktion):
        def measure(self, p):
            raise RuntimeError("Test")
    u = Kaputt(target=None)
    u.update(pose(), 0.0)
    assert not u.valid


def test_schwellen_auf_der_instanz():
    u = Abduktion(target=5, schwellen={"max_angle": 120})
    assert u.max_angle == 120 and u.min_angle == 105 and Abduktion.max_angle == 150


# --- Armheben ----------------------------------------------------------------------------------

def test_armheben_zaehlt_unten_oben_unten():
    u = run(Abduktion(target=None), [pose((20, 20))] * 6 + [pose((160, 160))] * 6 + [pose((20, 20))] * 6)
    assert u.counter == {"left": 1, "right": 1}


def test_armheben_oben_am_anfang_zaehlt_nicht():
    u = run(Abduktion(target=None), [pose((160, 160))] * 6 + [pose((20, 20))] * 6)
    assert u.counter == {"left": 0, "right": 0}


def test_armheben_ziel_nicht_erreicht():
    u = run(Abduktion(target=None), [pose((20, 20))] * 6 + [pose((140, 140))] * 6 + [pose((20, 20))] * 6)
    assert u.counter == {"left": 0, "right": 0}


def test_armheben_unsichtbarer_arm_zaehlt_nicht():
    frames = [pose((20, 20))] * 6 + [pose((160, 160), visibility=0.3)] * 6 + [pose((20, 20))] * 6
    assert run(Abduktion(target=None), frames).counter == {"left": 0, "right": 0}


def test_armheben_einzelner_ausreisser_faellt_heraus():
    frames = [pose((20, 20))] * 6 + [pose((170, 170))] + [pose((20, 20))] * 6
    assert run(Abduktion(target=None), frames).counter == {"left": 0, "right": 0}


def test_armheben_balken():
    u = run(Abduktion(target=5), [pose((20, 20))] * 6 + [pose((160, 160))] * 6)
    b = u.progress()["left"]
    assert b.fill > 1.0 and b.zurueck and b.zahl == 5 and b.wort == "noch"


def test_ziel_erreicht_beendet_uebung():
    one = [pose((20, 20))] * 6 + [pose((160, 160))] * 6
    u = run(Abduktion(target=2), one * 3 + [pose((20, 20))] * 6)
    assert u.done and u.total == 2      # nach done wird nicht weiter gezaehlt


# --- Bizepscurls -------------------------------------------------------------------------------

def test_bizeps_kurzes_beugen_zaehlt_nicht_langes_schon():
    stretched, bent = pose((10, 10), (175, 175)), pose((10, 10), (40, 40))
    short = run(Bizepscurls(target=None), [stretched] * 15 + [bent] * 3 + [stretched] * 15)
    assert short.counter == {"left": 0, "right": 0}
    long_ = run(Bizepscurls(target=None), [stretched] * 15 + [bent] * 15 + [stretched] * 15)
    assert long_.counter == {"left": 1, "right": 1}


def test_bizeps_oberarm_weg_vom_koerper_zaehlt_nicht():
    stretched, bent = pose((70, 70), (175, 175)), pose((70, 70), (40, 40))
    u = run(Bizepscurls(target=None), [stretched] * 15 + [bent] * 15 + [stretched] * 15)
    assert u.counter == {"left": 0, "right": 0} and not u.valid


# --- Schwellen-Datei ---------------------------------------------------------------------------

def test_schwellen_speichern_und_laden(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    alle = schwellen.laden()
    assert alle["abduktion"] == {"max_angle": 150}                       # ohne Datei: Voll
    alle["abduktion"] = Abduktion.werte("einfach")
    alle["seitenheben"] = {"max_angle": 75}
    schwellen.speichern(alle)
    neu = schwellen.laden()
    assert neu["abduktion"] == {"max_angle": 120} and neu["seitenheben"] == {"max_angle": 75}
    assert (tmp_path / "schwellen.json").read_text(encoding="utf-8").count("einfach") == 1


def test_schwellen_speichern_ist_sicher(tmp_path, monkeypatch):
    # Bricht das Schreiben ab (Strom weg, Platte voll), bleibt die alte Datei ganz
    monkeypatch.chdir(tmp_path)
    alle = schwellen.laden()
    alle["abduktion"] = Abduktion.werte("einfach")
    schwellen.speichern(alle)
    alt = (tmp_path / "schwellen.json").read_text(encoding="utf-8")

    def abbruch(daten, f, **kw):
        f.write('{"abdukt')
        raise OSError("Platte voll")
    monkeypatch.setattr(schwellen.json, "dump", abbruch)
    alle["abduktion"] = {"max_angle": 130}
    with pytest.raises(OSError):
        schwellen.speichern(alle)
    assert (tmp_path / "schwellen.json").read_text(encoding="utf-8") == alt
    assert not (tmp_path / "schwellen.json.tmp").exists()
    monkeypatch.undo()
    monkeypatch.chdir(tmp_path)
    assert schwellen.laden()["abduktion"] == {"max_angle": 120}


def test_schwellen_kaputte_datei_gibt_voll(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    (tmp_path / "schwellen.json").write_text("{kaputt", encoding="utf-8")
    assert schwellen.laden()["abduktion"] == {"max_angle": 150}
    (tmp_path / "schwellen.json").write_text('{"abduktion": {"foo": 1}, "gibtsnicht": "voll"}', encoding="utf-8")
    assert schwellen.laden()["abduktion"] == {"max_angle": 150}


# --- Balkenanzeige -----------------------------------------------------------------------------

class Uhr:
    def __init__(self):
        self.t = 0.0

    def __call__(self):
        return self.t


def test_balken_bestaetigung_nach_gezaehlter_wiederholung():
    uhr = Uhr()
    z = BalkenZustand(uhr)
    assert z.update(Balken(0.5, False, 5, "noch"))[2] is False
    uhr.t = 1.0
    assert z.update(Balken(0.1, False, 4, "noch"))[2] is True             # Zahl geaendert
    uhr.t = 1.0 + BESTAETIGUNG_S + 0.01
    assert z.update(Balken(0.1, False, 4, "noch"))[2] is False


def test_balken_bleibt_kurz_stehen_ohne_messwert():
    uhr = Uhr()
    z = BalkenZustand(uhr)
    z.update(Balken(1.1, True, 5, "noch"))
    uhr.t = HALTEN_S / 2
    assert z.update(Balken(None, False, 5, "noch"))[:2] == (1.1, True)
    uhr.t = HALTEN_S + 0.01
    assert z.update(Balken(None, False, 5, "noch"))[:2] == (None, False)


def test_balken_hoechster_punkt():
    z = BalkenZustand(Uhr())
    peaks = [z.update(Balken(f, f >= 1.0, 5, "noch"))[3] for f in (0.2, 0.7, 0.5, 0.3)]
    assert peaks == [None, None, 0.7, 0.7]
    z.update(Balken(0.2, False, 4, "noch"))                              # gezaehlt: neu messen
    assert z.update(Balken(0.1, False, 4, "noch"))[3] is None
    assert z.update(Balken(None, False, 4, "noch"))[3] is None           # ohne Messwert keine Linie


# --- Videoquelle und Kamera ---------------------------------------------------------------------

def test_videodatei_jedes_bild_mit_zeit(tmp_path):
    path = str(tmp_path / "v.mp4")
    writer = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*"mp4v"), 10, (64, 48))
    for i in range(5):
        writer.write(np.full((48, 64, 3), i * 40, np.uint8))
    writer.release()
    cam = FileCamera(path, frame_width=64, frame_height=48, realtime=False)
    times = []
    while (frame := cam.getFrame()) is not None:
        assert frame.shape == (48, 64, 3)
        times.append(round(cam.timestamp, 3))
    assert times == [0.0, 0.1, 0.2, 0.3, 0.4]
    assert cam.stop_event.is_set() and cam.getFrame() is None
    cam.close()
    assert cam.getFrame() is None


def test_videodatei_in_schleife_zaehlt_durchlaeufe(tmp_path):
    path = str(tmp_path / "v.mp4")
    writer = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*"mp4v"), 10, (64, 48))
    for i in range(5):
        writer.write(np.full((48, 64, 3), i * 40, np.uint8))
    writer.release()
    cam = FileCamera(path, frame_width=64, frame_height=48, realtime=False)
    cam.loop = True
    plays = []
    for _ in range(12):
        assert cam.getFrame() is not None
        plays.append(cam.plays)
    assert plays == [0] * 5 + [1] * 5 + [2] * 2      # zaehlt beim Neustart, nicht beim letzten Bild
    cam.close()


def test_kamera_nicht_vorhanden_ohne_folgefehler(monkeypatch):
    from camera.camera import Camera
    unraisable = []
    monkeypatch.setattr(sys, "unraisablehook", lambda u: unraisable.append(u))
    with pytest.raises(Exception, match="open failed"):
        Camera(cam_id=99, frame_width=640, frame_height=480)
    import gc
    gc.collect()
    assert not unraisable       # frueher: AttributeError 'stop_event' in __del__


# --- Schleife mit Pose-Thread --------------------------------------------------------------------

class FakeCam:
    def __init__(self):
        self.stop_event = threading.Event()
        self.reads = 0

    def getFrame(self):
        time.sleep(0.01)
        self.reads += 1
        return np.zeros((48, 64, 3), np.uint8)


class FakeBild:
    def zoom(self, frame):
        return frame, frame

    def overlay(self, image, p, uebung):
        return image

    def demo_frame(self, frame):
        return frame


class FakePose:
    def __init__(self):
        self.calls, self.threads = 0, set()

    def reset(self):
        pass

    def detect(self, image, timestamp_ms=None):
        self.calls += 1
        self.threads.add(threading.current_thread().name)
        return pose((20, 20))


class FakeSprecher:
    def reset(self):
        pass

    def stop(self):
        pass

    def say(self, text):
        pass

    def milestones(self, *a):
        pass


def test_schleife_live_mit_pose_thread():
    shown, cam, model = [], FakeCam(), FakePose()
    lauf = Uebungslauf(model, FakeBild(), FakeSprecher(), lambda image, balken, style: shown.append(balken))
    lauf.start(cam, [Abduktion(target=None)], on_source_end=lambda u: None, intro=False)
    deadline = time.monotonic() + 3
    while len(shown) < 5 and time.monotonic() < deadline:
        lauf.tick()
        time.sleep(0.005)
    assert len(shown) >= 5 and model.threads == {"PoseThread"}       # erkannt im Hintergrund
    worker = lauf.worker.thread
    lauf.paused = True
    time.sleep(0.15)
    reads = cam.reads
    time.sleep(0.2)
    assert cam.reads == reads                                          # Pause: liest nicht weiter
    lauf.paused = False
    lauf.stop()
    assert not worker.is_alive() and lauf.worker is None


def test_pose_thread_gibt_nach_fehlern_auf():
    import uebungslauf

    class KaputtePose(FakePose):
        def detect(self, image, timestamp_ms=None):
            raise RuntimeError("Modell kaputt")

    lauf = Uebungslauf(KaputtePose(), FakeBild(), FakeSprecher(), lambda *a: None)
    lauf.start(FakeCam(), [Abduktion(target=None)], on_source_end=lambda u: None, intro=False)
    lauf.tick()                                                          # startet den PoseThread
    lauf.worker.thread.join(timeout=uebungslauf.POSE_FEHLER * 0.01 + 3)  # FakeCam: 10 ms je Bild
    with pytest.raises(RuntimeError, match="Pose-Erkennung ausgefallen"):
        lauf.tick()                     # Hauptprogramm.loop() beendet die Uebung dann mit einer Meldung
    lauf.stop()


def test_schleife_ende_der_kamera():
    ended, cam = [], FakeCam()
    lauf = Uebungslauf(FakePose(), FakeBild(), FakeSprecher(), lambda *a: None)
    lauf.start(cam, [Abduktion(target=None)], on_source_end=ended.append, intro=False)
    lauf.tick()
    cam.stop_event.set()
    lauf.tick()
    assert len(ended) == 1


class FakeDemo:
    def __init__(self):
        self.plays = 0

    def getFrame(self):
        return np.zeros((48, 64, 3), np.uint8)

    def close(self):
        pass


def test_einfuehrung_zeigt_die_demo_mehrmals(monkeypatch):
    import uebungslauf
    demo = FakeDemo()
    monkeypatch.setattr(uebungslauf, "INTRO_SECONDS", 0)
    monkeypatch.setattr(Uebungslauf, "open_demo", staticmethod(lambda path: demo))
    lauf = Uebungslauf(FakePose(), FakeBild(), FakeSprecher(), lambda *a: None)
    lauf.start(FakeCam(), [Abduktion(target=None)], on_source_end=lambda u: None)
    for plays in range(uebungslauf.DEMO_PLAYS):
        demo.plays = plays
        lauf.tick()
        assert lauf.phase == "intro"                    # die Demo ist noch nicht oft genug gelaufen
    demo.plays = uebungslauf.DEMO_PLAYS
    lauf.tick()
    assert lauf.phase == "counting"
    lauf.stop()


# --- Architektur ---------------------------------------------------------------------------------

def test_uebungen_ohne_mediapipe():
    # Die Zaehler rechnen nur mit pose.py; das Erkennungsmodell bleibt austauschbar
    code = "import sys, uebungen, tools.recording_format; print('mediapipe' in sys.modules)"
    out = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True,
                         cwd=__file__.rsplit("/", 1)[0], check=True)
    assert out.stdout.strip() == "False"


# --- Sprachausgabe -------------------------------------------------------------------------------

def test_ueberholte_ansage_wird_nicht_abgespielt(monkeypatch):
    import sprachausgabe
    played = []
    monkeypatch.setattr(sprachausgabe.sd, "play", lambda data, rate: played.append(len(data)))
    monkeypatch.setattr(sprachausgabe.sd, "wait", lambda: None)
    monkeypatch.setattr(sprachausgabe.sd, "stop", lambda: None)
    Chunk = namedtuple("Chunk", "audio_int16_array sample_rate")

    class Stimme:
        def synthesize(self, text):
            return [Chunk(np.zeros(100, np.int16), 16000)]

    sprachausgabe.speak(Stimme(), "noch 3", still_current=lambda: False)
    assert played == []                                                  # überholt: still
    sprachausgabe.speak(Stimme(), "noch 3")
    assert played == [100]

    s = sprachausgabe.SprachAusgabe.__new__(sprachausgabe.SprachAusgabe)
    s.voice = Stimme()
    s.reset()
    generation = s.generation
    s.stop()                                                             # Stop/nächste Übung
    assert s.generation == generation + 1
