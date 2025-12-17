"""
******************************************************************************
 file:      user_database.py
 brief:     Software zur datenbank der Nutzerverwaltung
 author(s): Fanuel Bimo Setiadi
 adviser:   
 version:   1.1.8
 date:      10.12.2025
 software:  Python (Version: 3.12.3)
 int. libs: sqlite3
 ext. libs: 
 info:      
******************************************************************************
"""
# user_database.py
import sqlite3
import os
from typing import List, Optional, Tuple
import logging
from logger import LoggingConfigurator

class UserDatabase:
    """
    SQLite-DB für Nutzer und Fortschritt:
      - users(id, name)
      - progress(id, user_id, exercise_name, postop_day, date)
    Besonderheiten:
      - PRAGMA foreign_keys=ON (Kaskadenlöschung)
      - WAL + busy_timeout für robustes Schreiben
      - Migration entfernt alte Spalten (repetitions_done/goal), falls vorhanden
    """

    def __init__(self, db_name: str = "users.db", subdir: str = "Benutzerdaten"):
        # Basisverzeichnis (hier: Verzeichnis der aktuellen Datei)
        base_dir = os.path.dirname(os.path.abspath(__file__))

        # Unterordner "Benutzerdaten"
        data_dir = os.path.join(base_dir, subdir)

        # Ordner anlegen, falls er noch nicht existiert
        os.makedirs(data_dir, exist_ok=True)

        # Pfad zur Datenbank-Datei im Unterordner
        self.db_path = os.path.join(data_dir, db_name)
        print("[DB] using:", self.db_path)

        # Verbindung herstellen
        self.conn = sqlite3.connect(self.db_path, timeout=5.0)

    # def __init__(self, db_name: str = "users.db"):
    #     self.db_path = os.path.abspath(db_name)
    #     print("[DB] using:", self.db_path)

    #     self.conn = sqlite3.connect(self.db_path, timeout=5.0)
        self.conn.execute("PRAGMA foreign_keys = ON")
        self.conn.execute("PRAGMA journal_mode = WAL")
        self.conn.execute("PRAGMA synchronous = NORMAL")
        self.conn.execute("PRAGMA busy_timeout = 5000")
        self.cursor = self.conn.cursor()

        self._init_schema()
        self._ensure_postop_day_column()     
        self._migrate_remove_repetitions()   
        self._ensure_indexes()

    # ---------- Schema ----------
    def _init_schema(self) -> None:
        self.cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id   INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            )
        """)
        # Neu-schema: ohne repetitions_*, mit postop_day
        self.cursor.execute("""
            CREATE TABLE IF NOT EXISTS progress (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id       INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                postop_day    TEXT,
                date          TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        self.conn.commit()

    def _ensure_postop_day_column(self) -> None:
        """Fügt postop_day hinzu, falls progress aus alter Version stammt."""
        self.cursor.execute("PRAGMA table_info(progress)")
        cols = {row[1] for row in self.cursor.fetchall()}
        if "postop_day" not in cols:
            self.cursor.execute("ALTER TABLE progress ADD COLUMN postop_day TEXT")
            self.conn.commit()

    def _migrate_remove_repetitions(self) -> None:
        """
        Entfernt alte Spalten repetitions_done / repetitions_goal, falls sie existieren,
        indem progress neu angelegt und die relevanten Daten übernommen werden.
        """
        self.cursor.execute("PRAGMA table_info(progress)")
        cols = {row[1] for row in self.cursor.fetchall()}
        if "repetitions_done" not in cols and "repetitions_goal" not in cols:
            return  # nichts zu tun

        print("[DB] Migration: entferne repetitions_* Spalten ...")
        # neue Tabelle ohne repetitions_* anlegen
        self.cursor.execute("""
            CREATE TABLE IF NOT EXISTS progress_new (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id       INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                postop_day    TEXT,
                date          TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        # Daten übernehmen (nur relevante Spalten, vorhandene postop_day bewahren)
        # posteop_day könnte in altem Schema fehlen → COALESCE auf NULL
        try:
            self.cursor.execute("""
                INSERT INTO progress_new (id, user_id, exercise_name, postop_day, date)
                SELECT id, user_id, exercise_name,
                       CASE
                         WHEN (SELECT COUNT(*) FROM pragma_table_info('progress') WHERE name='postop_day') > 0
                         THEN postop_day
                         ELSE NULL
                       END AS postop_day,
                       date
                FROM progress
            """)
        except sqlite3.OperationalError:
            # Fallback, wenn obiger SELECT in sehr alten SQLite-Versionen zickt:
            self.cursor.execute("""
                INSERT INTO progress_new (id, user_id, exercise_name, postop_day, date)
                SELECT id, user_id, exercise_name, NULL, date
                FROM progress
            """)

        self.cursor.execute("DROP TABLE progress")
        self.cursor.execute("ALTER TABLE progress_new RENAME TO progress")
        self._ensure_indexes()
        self.conn.commit()
        print("[DB] Migration abgeschlossen.")

    def _ensure_indexes(self) -> None:
        self.cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_progress_user_exercise_date
            ON progress(user_id, exercise_name, date DESC)
        """)
        self.conn.commit()

    # ---------- Lifecycle ----------
    def close(self) -> None:
        try:
            self.conn.close()
        except Exception:
            pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.close()

    # ---------- Users ----------
    def add_user(self, name: str) -> bool:
        """True, wenn angelegt; False, wenn Name existiert/Fehler."""
        try:
            self.cursor.execute("INSERT INTO users (name) VALUES (?)", (name.strip(),))
            self.conn.commit()
            return True
        except sqlite3.IntegrityError:
            return False

    def delete_user(self, name: str) -> int:
        """Löscht Nutzer (und per CASCADE alle Fortschritte). Rückgabe: 1=gelöscht, 0=nicht gefunden."""
        self.cursor.execute("DELETE FROM users WHERE name = ?", (name.strip(),))
        self.conn.commit()
        return self.cursor.rowcount

    def get_users(self) -> List[str]:
        self.cursor.execute("SELECT name FROM users ORDER BY name")
        return [row[0] for row in self.cursor.fetchall()]

    def get_user_id(self, name: str) -> Optional[int]:
        self.cursor.execute("SELECT id FROM users WHERE name = ?", (name.strip(),))
        row = self.cursor.fetchone()
        return row[0] if row else None

    # ---------- Progress (ohne Wiederholungen) ----------
    def save_progress(
        self,
        user_name: str,
        exercise_name: str,
        postop_day: Optional[str] = None
    ) -> Optional[int]:
        """
        Speichert eine Fortschritts-Zeile (nur Übung + PostOP-Tag).
        Gibt neue progress.id zurück oder None (wenn Nutzer fehlt).
        """
        uid = self.get_user_id(user_name)
        if not uid:
            print(f"[DB] WARN: user not found -> '{user_name}'")
            return None
        self.cursor.execute("""
            INSERT INTO progress (user_id, exercise_name, postop_day)
            VALUES (?, ?, ?)
        """, (uid, exercise_name, postop_day))
        self.conn.commit()
        new_id = self.cursor.lastrowid
        #print(f"[DB] progress saved: user={user_name}, ex={exercise_name}, postop_day={postop_day}")
        logging.info(f"[DB] progress saved: user={user_name}, ex={exercise_name}, postop_day={postop_day}")
        return new_id

    def get_exercise_days_with_date(
        self,
        user_name: str,
        exercise_name: Optional[str] = None
    ) -> List[Tuple[str, str, str]]:
        """
        Liefert (exercise_name, postop_tag, day_disp) je (Übung, Kalendertag),
        sortiert nach jüngstem Timestamp absteigend.
        - postop_tag: bevorzugt 'postop_day', sonst Datum (YYYY-MM-DD)
        - day_disp: Datum formatiert als DD.MM.YYYY
        """
        uid = self.get_user_id(user_name)
        if not uid:
            return []

        # Tag-Anzeige + formatiertes Datum
        tag_expr = "COALESCE(postop_day, substr(date,1,10)) AS postop_tag"
        day_disp_expr = "strftime('%d.%m.%Y', date) AS day_disp"

        if exercise_name:
            self.cursor.execute(f"""
                SELECT exercise_name, {tag_expr}, {day_disp_expr}, MAX(date) AS max_ts
                FROM progress
                WHERE user_id = ? AND exercise_name = ?
                GROUP BY exercise_name, postop_tag, day_disp
                ORDER BY max_ts DESC
            """, (uid, exercise_name))
        else:
            self.cursor.execute(f"""
                SELECT exercise_name, {tag_expr}, {day_disp_expr}, MAX(date) AS max_ts
                FROM progress
                WHERE user_id = ?
                GROUP BY exercise_name, postop_tag, day_disp
                ORDER BY max_ts DESC
            """, (uid,))

        rows = self.cursor.fetchall()
        # nur die 3 Anzeigefelder zurückgeben
        return [(r[0], r[1], r[2]) for r in rows]



