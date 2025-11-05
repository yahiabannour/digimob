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
## [1.1.7] 2025-09-14

### Added
- 

### Fixed
- Fixing the layouts of Nutzerverwaltung (Position and Fonts)

### ToDo
- Show the active profile in every page

---

## [1.1.6] 2025-09-13

### Added
- Hauptmenü -> Einstellung: Remove the button for visualize keypoints and move it to exercise page
- Zoom-Button changed to on/off function with zoom factor 1x and 2x
- Zoom-Function with face tracking (by facemesh/posemesh)

### Fixed
- Bug in number of repetitions
- Font and Font Size as a Variable

### ToDo
- Fixing the layouts of Nutzerverwaltung (Position and Fonts)

---

## [1.1.5] 2025-09-10

### Added
- Hauptmenü -> additional Page : Nutzerverwaltung created: User managament for add, delete user profile and to show progress
- Zoom-Button with slider

### Fixed
- Standardization of Camera (Normierung der Kamera)

### ToDo
- Zoom Function with face tracking

---

## [1.1.4] 2025-05-09

### Added
- Hauptmenü -> Einstellungen: 'Keypoints an/aus', to show the keypoints
- In every exercise there is zoom-button (2x) to adjust camera

### ToDo
- Zoom-Button with slider

### Fixed
- bug in Sprachausgabe -- Halfway 
- remove umlauts from all text in every exercise class ("Jetzt koennen Sie mit dieser Uebung starten")

---


## [1.1.3] 2025-04-17

### Changed
- Layout of Buttons: moved to column 0 (left side)
- Font sizes increased (16 -> 24, 28 -> 32)
- button labels shortended (e.g. 'Zurück zum Hauptmenü' -> 'Zurück')
- Video frame and bargraph enlarged

### Fixed
- bug in 'Pause'-Button (converted to 2-color toggle button)

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
