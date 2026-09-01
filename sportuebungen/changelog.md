# Changelog (Projekt Reha-Sportübungen)

---

### Use:
- __Added__      for new features.
- __Changed__     for changes in existing functionality.
- __Deprecated__  for soon-to-be removed features.
- __Removed__     for now removed features.
- __Fixed__       for any bug fixes.
- __Security__    in case of vulnerabilities.

---

## [1.1.9] 2026-09-01

### Fixed
- sprachausgabe.py: TTS was completely silent (every `speak()` raised
  `wave.Error: # channels not specified`). Rewrote `speak()` for the piper-tts 1.7.0
  API: iterate the `AudioChunk`s from `voice.synthesize(text)`, concatenate their
  `audio_int16_array`, play at `sample_rate` via sounddevice. Dropped the unused
  `wave` / `io.BytesIO` imports.

### Changed
- Hauptmenü: moved "Übungen wählen" (grid row 2 -> 7) and "Bedienungsanleitung"
  (row 3 -> 8) further down the page
- moved the Thorsten voice model into a subfolder: `stimmen/de_DE-thorsten-low.onnx`
  (+ `.onnx.json`); updated the paths in sprachausgabe.py and Hauptprogramm.py
- exercises now start without selecting a user: a fixed default user "Gast" is
  created at startup and used for all progress saving; removed the "Bitte zuerst
  einen Nutzer auswaehlen" gates in `start_exercise_menu()` and
  `_bind_user_to_all_exercises()`

### Removed
- deleted all TTS voices except Thorsten (Eva, Kerstin, Ramona, Karlsson, Pavoque,
  MLS -- 12 files)
- Hauptmenü: removed the "Einstellungen" and "Nutzerverwaltung" buttons
- Einstellungen: removed the voice-selection dropdown, the "Anhören" test button,
  and the `voice_changed()` / `voice_test()` methods (page now holds only a back
  button)
- removed the entire Nutzerverwaltung code from Hauptprogramm.py (~244 lines):
  `user_frame`, `configure_user_frame`, add/delete/select-user views,
  `show_progress_window`, `load_progress_data` and the related helper methods and
  StringVars. `user_database.py` is unchanged; its `delete_user`, `get_users` and
  `get_exercise_days_with_date` are now unused.

### ToDo
- scale the program window, menues, font sizes, video frames etc. to the screen resolution
- check the calculation of arm and body positions for proper training movements

---

## [1.1.8] 2025-12-17

### Changed
- changed storage location of user database to subfolder '/Benutzerdaten/'
- refreshed all version numbers in source code

### Removed
- removed notification after user selection ("Du hast ... gewählt")
- removed unused code (zoom-slider, Sportübung unterbrechen, Visualisierung-Button, ...)

### ToDo
- Show the active profile name on every page
- Keep the selected user when opening the user progress list
- scale the program window, menues, font sizes, video frames etc. to the screen resolution
- check the calculation of arm and body positions for proper training movements

---

## [1.1.7] 2025-09-14

### Fixed
- Fixing the layouts of Nutzerverwaltung (position and fonts)

### ToDo
- Show the active profile name on every page

---

## [1.1.6] 2025-09-13

### Added
- zoom-function with face tracking (by facemesh/posemesh)

### Changed
- zoom-button changed to on/off function with zoom factor 1x and 2x
- Hauptmenü -> Einstellung: Remove the button for visualize keypoints and move it to exercise page

### Fixed
- Bug in number of repetitions
- font and font size as a variable

### ToDo
- Fixing the layouts of Nutzerverwaltung (Position and Fonts)

---

## [1.1.5] 2025-09-10

### Added
- Hauptmenü -> additional Page : Nutzerverwaltung created: User managament for add, delete user profile and to show progress
- zoom-button with slider

### Fixed
- Standardization of Camera (Normierung der Kamera)

### ToDo
- zoom function with face tracking

---

## [1.1.4] 2025-05-09

### Added
- Hauptmenü -> Einstellungen: button 'Keypoints an/aus' to show the keypoints inside the video
- zoom-button (2x) to adjust camera in every exercise

### ToDo
- zoom-button with slider

### Fixed
- bug in Sprachausgabe -- Halfway 
- remove umlauts from all text in every exercise class ("Jetzt koennen Sie mit dieser Uebung starten")

---

## [1.1.3] 2025-04-17

### Changed
- layout of buttons: moved to column 0 (left side)
- font sizes increased (16 -> 24, 28 -> 32)
- button labels shortended (e.g. 'Zurück zum Hauptmenü' -> 'Zurück')
- video frame and bargraph enlarged

### Fixed
- bug in 'Pause'-button (converted to 2-color toggle button)

### ToDo
- Hauptmenü -> Einstellungen: 'Stimme testen' can be clicked many times while test is running
- when stopping exercise, post-op day value is always returned to 5 even if dropdown menu still shows other value

---

## [1.1.2] 2025-04-16

### Added
- Hauptmenü: 'Pause'-Button -> pauses update_frame()
- Hauptmenü -> Einstellungen: 'Stimme testen'

### ToDo
- Hauptmenü -> Einstellungen: 'Stimme testen' can be clicked many times while test is running

---

## [1.1.1] 2025-04-15

### Changed
- Hauptmenü: 'Zurück zum Hauptmenü' aborts and resets the exercises
- Hauptmenü: stop-kamera() split into: stop-camera(), reset-bargraph(), reset-exercises()
- initialisierung.py: initialisierung-start() renamed to: camera-start()

### ToDo
- Hauptmenü: 'Pause'-Button pauses, but does not continue the exercise

---

## [1.1.0] 2025-04-14

### Added
- Hauptprogramm: additional page 'Einstellungen' created
- Hauptprogramm: 'Einstellungen': dopdown menu to select different tts-voice models

### Fixed
- sprachausgabe: threading.Lock() to avoid crash caused by overlapping voice calls
- unified height, width and font size of buttons

### Changed
- halswirblesaeule -> halswirbelsaeule
- butterfly.py: changed word 'butterfly' to "batterflei' for german voices
- some minor spell corrections

### Removed
- "Sportübung unterbrechen" in all exercises turned off (commented out)
