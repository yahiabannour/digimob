package de.mhh.digimob.feature.training.data.recording

import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.feature.training.domain.RecordingList
import de.mhh.digimob.feature.training.domain.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Liest videomodus.txt (eine Video-Datei pro Zeile, relativ zu [root], # ueberspringt) und die
 * summary.json neben jedem Video (aufnahmen_test.read_test_videos). Auf dem Tablet ist [root] der
 * App-Ordner auf dem Speicher, die Videos kommen per adb push dorthin (sie sind nicht in git).
 */
class FileRecordingDataSource(private val root: File) : RecordingRepository {

    private val log = Logger.withTag("Aufnahmen")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun recordings(): Result<RecordingList, DataError.Local> = withContext(Dispatchers.IO) {
        val config = root.resolve(CONFIG)
        if (!config.isFile) {
            log.e { "$config fehlt" }
            return@withContext Result.Error(DataError.Local.NOT_FOUND)
        }
        val lines = try {
            config.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            log.e(e) { "$config nicht lesbar" }
            return@withContext Result.Error(DataError.Local.UNKNOWN)
        }
        val recordings = mutableListOf<de.mhh.digimob.feature.training.domain.Recording>()
        val problems = mutableListOf<String>()
        for (line in lines) {
            val video = root.resolve(line)
            val dir = video.parentFile
            if (!video.isFile || dir == null) {
                problems += "$line: Datei nicht gefunden"
                continue
            }
            val summary = try {
                dir.resolve(SUMMARY).takeIf { it.isFile }
                    ?.let { json.decodeFromString<RecordingSummaryDto>(it.readText()) }
                    ?: RecordingSummaryDto()
            } catch (e: Exception) {
                problems += "$line: summary.json nicht lesbar"
                continue
            }
            val name = "${dir.parentFile?.name}/${dir.name}"
            val recording = summary.toRecording(name, video.absolutePath, folderExercise = dir.parentFile?.name.orEmpty())
            if (recording == null) {
                problems += "$line: Übung oder Preset unbekannt (erwartet recordings/<uebung>/rec<N>/)"
                continue
            }
            recordings += recording
        }
        problems.forEach { log.w { it } }
        Result.Success(RecordingList(recordings, problems))
    }

    private companion object {
        const val CONFIG = "videomodus.txt"
        const val SUMMARY = "summary.json"
    }
}
