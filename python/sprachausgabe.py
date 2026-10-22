"""
******************************************************************************
 file:      sprachausgabe.py
 brief:     Software zur Sprachausgabe
 author(s): Yahia Bannour
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.10.2026
 software:  Python (3.11.2)
 int. libs: threading
 ext. libs: sounddevice (0.5.1)
            numpy (1.26.4)
            piper-tts (1.7.0)
 info:      using Lock() to avoid overlapping tts-voice with program crash.
            Die Stimme wird einmal geladen, wenn die App ihre SprachAusgabe erzeugt.
******************************************************************************
"""
import threading

import numpy as np
import sounddevice as sd
from piper import PiperVoice

# Pfad relativ zum Arbeitsverzeichnis: die App laeuft aus python/
VOICE_MODEL = "stimmen/de_DE-thorsten-low.onnx"

audio_lock = threading.Lock()


def speak(voice, text, still_current=lambda: True):
    # Synthetisiert Sprache mit Piper und spielt sie ab. still_current(): gilt die Ansage noch?
    # Eine Ansage wartet auf die vorige (audio_lock); ist sie inzwischen überholt (Stop, nächste
    # Übung), wird sie nicht mehr abgespielt statt verspätet.
    with audio_lock:
        if not still_current():
            return
        chunks = list(voice.synthesize(text))
        if not chunks or not still_current():
            return

        audio_data = np.concatenate([chunk.audio_int16_array for chunk in chunks])
        samplerate = chunks[0].sample_rate

        sd.play(audio_data, samplerate)
        sd.wait()


class SprachAusgabe:
    def __init__(self, voice_model=VOICE_MODEL):
        self.voice = PiperVoice.load(voice_model)
        self.reset()

    def reset(self):
        # Vor jeder Uebung: jede Meldung wird pro Uebung nur einmal gesprochen; was von der
        # vorigen Uebung noch laeuft oder wartet, verstummt
        self.executed = set()
        self.stop()

    def stop(self):
        # Laufende Ansage abbrechen (sounddevice.stop beendet play()), wartende verwerfen
        self.generation = getattr(self, "generation", 0) + 1
        sd.stop()

    def say(self, message):
        # Eigener Thread, damit die Bildverarbeitung waehrend der Ansage weiterlaeuft
        generation = self.generation
        threading.Thread(target=speak, args=(self.voice, message, lambda: generation == self.generation),
                         daemon=True).start()

    def milestones(self, total_repetitions, repetitions):
        remaining = repetitions - total_repetitions

        if total_repetitions == 0 and "start" not in self.executed:
            self.say(f"Okay, versuchen Sie {repetitions} Wiederholungen zu schaffen")
            self.executed.add("start")

        elif total_repetitions >= repetitions / 2 and "halfway" not in self.executed:
            self.say("Super, die Hälfte ist geschafft")
            self.executed.add("halfway")

        elif remaining > 0 and remaining not in self.executed and not total_repetitions == 0:
            if remaining == 1:
                self.say("Letzte Wiederholung")
                self.executed.add(remaining)
            elif remaining > 1:
                self.say(f"noch {remaining}")
                self.executed.add(remaining)

        elif remaining <= 0 and "done" not in self.executed:
            self.say(f"Tolle Arbeit! Sie haben alle {repetitions} Wiederholungen geschafft")
            self.executed.add("done")
