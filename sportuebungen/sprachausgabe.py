"""
******************************************************************************
 file:      sprachausgabe.py
 brief:     Software zur Sprachausgabe
 author(s): Ezaldeen Al-Shahethi, Jörg Schneider, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.8
 date:      10.12.2025
 software:  Python (3.11.2)
 int. libs: threading, io, wave
 ext. libs: sounddevice (0.5.1)
            numpy (1.26.4)
            piper-tts (1.2.0)            
 info:      using Lock() to avoid overlapping tts-voice with program crash
******************************************************************************
"""
import numpy as np
import sounddevice as sd
import threading
import wave
from piper import PiperVoice
from io import BytesIO


# Set default voice (same as in 'Hauptprogramm.py', otherwise Visual Studio Code shows: 'voice' is undefined variable)
default_voice_model = "de_DE-thorsten-low.onnx"
voice = PiperVoice.load(default_voice_model)


def load_voice(voice_model_name):
    # Lädt das Piper-Stimmenmodell.
    return PiperVoice.load(voice_model_name)

audio_lock = threading.Lock()

def speak(voice, text):
    # Synthetisiert Sprache mit Piper und spielt sie ab.
    with audio_lock:
        with BytesIO() as wav_stream:
            with wave.open(wav_stream, 'wb') as wav_file:
                voice.synthesize(text, wav_file)

            wav_stream.seek(0)

            with wave.open(wav_stream, 'rb') as wav_file:
                frames = wav_file.readframes(wav_file.getnframes())
                audio_data = np.frombuffer(frames, dtype=np.int16)
                samplerate = wav_file.getframerate()

        sd.play(audio_data, samplerate)
        sd.wait()


class SprachAusgabe:
    def __init__(self):
        self.total_repetitions = 0
        self.repetitions = 0
        self.executed = set()  # Speichert bereits gesprochene Nachrichten
        self.lock = threading.Lock()


    def activate_thread(self, message):
        threading.Thread(target=speak, args=(voice, message)).start()


    def speak(self):
        remaining = self.repetitions - self.total_repetitions

        if self.total_repetitions == 0 and "start" not in self.executed:
            self.activate_thread(f"Super, versuchen Sie {self.repetitions} Wiederholungen zu schaffen")
            self.executed.add("start")

        elif self.total_repetitions >= self.repetitions / 2 and "halfway" not in self.executed:
            self.activate_thread("Super, die Hälfte ist geschafft")
            self.executed.add("halfway")

        elif remaining > 0 and remaining not in self.executed and not self.total_repetitions == 0:
            if remaining == 1:
                self.activate_thread("Letzte Wiederholung")
                self.executed.add(remaining)
            elif remaining > 1:
                self.activate_thread(f"noch {remaining}")
                self.executed.add(remaining)

        elif remaining <= 0 and "done" not in self.executed:
            self.activate_thread(f"Tolle Arbeit! Sie haben alle {self.repetitions} Wiederholungen geschafft")
            self.executed.add("done")
