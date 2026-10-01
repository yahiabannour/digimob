"""
******************************************************************************
 file:      sprachausgabe.py
 brief:     Software zur Sprachausgabe
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
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

# Pfad relativ zum Arbeitsverzeichnis: die App laeuft aus sportuebungen/
VOICE_MODEL = "stimmen/de_DE-thorsten-low.onnx"

audio_lock = threading.Lock()


def speak(voice, text):
    # Synthetisiert Sprache mit Piper und spielt sie ab.
    with audio_lock:
        chunks = list(voice.synthesize(text))
        if not chunks:
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
        # Vor jeder Uebung: jede Meldung wird pro Uebung nur einmal gesprochen
        self.executed = set()

    def say(self, message):
        # Eigener Thread, damit die Bildverarbeitung waehrend der Ansage weiterlaeuft
        threading.Thread(target=speak, args=(self.voice, message)).start()

    def milestones(self, total_repetitions, repetitions):
        remaining = repetitions - total_repetitions

        if total_repetitions == 0 and "start" not in self.executed:
            self.say(f"Super, versuchen Sie {repetitions} Wiederholungen zu schaffen")
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
