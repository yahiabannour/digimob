package de.mhh.digimob.core.domain

// Die App spricht mit keinem Server: es gibt nur lokale Fehler (Einstellungen)
sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        UNKNOWN
    }
}
