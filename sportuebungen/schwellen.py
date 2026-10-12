"""
******************************************************************************
 file:      schwellen.py
 brief:     Die im Auswahlmenue eingestellten Schwellen der Uebungen, gespeichert in schwellen.json
            (im Arbeitsverzeichnis sportuebungen/, nicht in git). Keine Patientendaten: nur, mit
            welchen Winkeln/Abstaenden jede Uebung zaehlt.
 author(s): Yahia Bannour
 version:   1.1.9
 date:      10.10.2026
 software:  Python (Version: 3.12)
 info:      Pro Uebung entweder der Name eines Presets ("einfach") oder die Werte, die von "Voll"
            abweichen ({"min_angle": 47}). Fehlt eine Uebung, gilt "Voll". Nur die App liest die
            Datei: systest.py, "Aufnahmen testen" und die Tools zaehlen mit dem Preset der Aufnahme.
******************************************************************************
"""
import json
import logging
import os

from uebungen import UEBUNGEN

DATEI = "schwellen.json"


def laden(pfad=DATEI):
    """{uebung: {schwelle: wert}} fuer alle Uebungen. Unbekanntes und Unpassendes wird mit einem
    Logeintrag uebergangen, dann gilt fuer diese Uebung "Voll"."""
    gespeichert = {}
    if os.path.exists(pfad):
        try:
            with open(pfad, encoding="utf-8") as f:
                gespeichert = json.load(f)
        except (OSError, ValueError) as e:
            logging.error(f"{pfad}: {e}, es gelten die Schwellen von Voll")
    if not isinstance(gespeichert, dict):
        logging.error(f"{pfad}: kein JSON-Objekt, es gelten die Schwellen von Voll")
        gespeichert = {}
    for name in gespeichert.keys() - UEBUNGEN.keys():
        logging.warning(f"{pfad}: Übung {name} unbekannt, übergangen")

    alle = {}
    for name, cls in UEBUNGEN.items():
        alle[name] = cls.werte("voll")
        eintrag = gespeichert.get(name)
        if eintrag is None:
            continue
        if isinstance(eintrag, str) and eintrag in ("voll", "einfach"):
            werte = cls.werte(eintrag)
        elif isinstance(eintrag, dict) and all(key in cls.schwellen and isinstance(value, (int, float))
                                              for key, value in eintrag.items()):
            werte = dict(cls.werte("voll"), **eintrag)
        else:
            logging.warning(f"{pfad}: {name}: {eintrag!r} unbekannt, es gilt Voll")
            continue
        fehler = cls.pruefe(werte)
        if fehler:
            logging.warning(f"{pfad}: {name}: {fehler}, es gilt Voll")
            continue
        alle[name] = werte
    return alle


def speichern(alle, pfad=DATEI):
    """Schreibt die Schwellen aller Uebungen: ein Preset als Name, von Hand angepasste Werte als
    Abweichung von Voll. Uebungen auf Voll fehlen in der Datei."""
    daten = {}
    for name, werte in alle.items():
        cls = UEBUNGEN[name]
        preset = cls.preset_von(werte)
        if preset == "voll":
            continue
        voll = cls.werte("voll")
        daten[name] = preset or {key: value for key, value in werte.items() if value != voll[key]}
    # Erst in eine Nebendatei schreiben, dann in einem Schritt ersetzen: faellt der Strom waehrend des
    # Speicherns aus, bleibt die alte Datei ganz (direkt geschrieben blieb sie leer oder halb, und
    # beim naechsten Start galt fuer alle Uebungen Voll)
    tmp = pfad + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(daten, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, pfad)
    except BaseException:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise
    logging.info(f"Schwellen gespeichert: {daten}")
