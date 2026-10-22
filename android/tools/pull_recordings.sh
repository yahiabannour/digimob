#!/usr/bin/env bash
# Holt die fertigen Aufnahmen vom Tablet (Debug-Build, Hauptmenue "Aufnehmen") nach
# android/recordings/<uebung>/rec<N>/ und traegt ihre Keypoints in android/systest_tablet.txt ein.
# Fertig ist eine Aufnahme mit summary.json. Auf dem Tablet wird sie danach geloescht, damit sie
# nicht zweimal kommt; die Nummer vergibt dieses Skript neu (naechste freie hier).
#
# Aufruf (Tablet per USB, adb): android/tools/pull_recordings.sh
set -euo pipefail
cd "$(dirname "$0")/.."

REMOTE=/sdcard/Android/data/de.mhh.digimob/files/recordings
pulled=0
for summary in $(adb shell "ls $REMOTE/*/rec*/summary.json 2>/dev/null" || true); do
    remote_dir=$(dirname "$summary")
    exercise=$(basename "$(dirname "$remote_dir")")
    mkdir -p "recordings/$exercise"
    n=1
    while [ -e "recordings/$exercise/rec$n" ]; do n=$((n + 1)); done
    target="recordings/$exercise/rec$n"
    adb pull "$remote_dir" "$target" >/dev/null 2>&1 || true
    if [ ! -f "$target/summary.json" ]; then
        echo "Fehler: $remote_dir nicht vollstaendig geholt, bleibt auf dem Tablet" >&2
        rm -rf "$target"
        continue
    fi
    adb shell "rm -r '$remote_dir'"
    echo "$target/keypoints_full.jsonl" >> systest_tablet.txt
    echo "$target  ($(grep -o '"expected_reps": [0-9]*' "$target/summary.json"))"
    pulled=$((pulled + 1))
done
echo "$pulled Aufnahme(n) geholt. Test: ./gradlew :core:domain:tabletTest"
