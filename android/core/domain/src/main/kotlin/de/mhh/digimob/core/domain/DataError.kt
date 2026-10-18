package de.mhh.digimob.core.domain

// Die App spricht mit keinem Server: es gibt nur lokale Fehler (Einstellungen, Aufnahmen-Dateien)
sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }
}
