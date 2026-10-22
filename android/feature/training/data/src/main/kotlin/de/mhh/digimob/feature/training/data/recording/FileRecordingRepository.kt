package de.mhh.digimob.feature.training.data.recording

import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.feature.training.domain.RecordedFrame
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.RecordingSummary
import de.mhh.digimob.feature.training.domain.Take
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Die Aufnahmen auf dem Tablet, wie python/recordings/: [root]/<uebung>/rec<N>/ mit video.mp4,
 * keypoints_full.jsonl und summary.json. [root] ist der App-Ordner auf dem Tablet
 * (Android/data/de.mhh.digimob/files/recordings), dort holt android/tools/pull_recordings.sh sie ab.
 * Fertig ist eine Aufnahme mit ihrer summary.json; ein Ordner ohne (App beendet waehrend der
 * Aufnahme) wird bei der naechsten Aufnahme dieser Uebung entfernt.
 */
class FileRecordingRepository(
    private val root: File,
    private val cameraName: String,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecordingRepository {

    private val log = Logger.withTag("Aufnahme")
    private val json = Json { prettyPrint = true; explicitNulls = false }
    private val startedAt = mutableMapOf<String, String>()

    override suspend fun newTake(exercise: ExerciseId): Result<Take, DataError.Local> = withContext(ioDispatcher) {
        try {
            val dir = root.resolve(exercise.key)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("$dir nicht angelegt")
            takeDirs(dir).filterNot { it.resolve(SUMMARY_FILE).exists() }.forEach {
                log.w { "Unfertige Aufnahme ${it.name} entfernt" }
                it.deleteRecursively()
            }
            val number = (takeDirs(dir).mapNotNull { it.name.removePrefix(PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1
            val takeDir = dir.resolve("$PREFIX$number")
            // mkdir ohne vorhandenen Ordner: eine Aufnahme wird nie ueberschrieben
            if (!takeDir.mkdir()) throw IOException("$takeDir nicht angelegt")
            val take = Take(name = "${exercise.key}/${takeDir.name}", videoPath = takeDir.resolve(VIDEO_FILE).path)
            startedAt[take.name] = now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            Result.Success(take)
        } catch (e: IOException) {
            log.e(e) { "Aufnahme ${exercise.key} nicht angelegt" }
            Result.Error(e.toDataError())
        }
    }

    override suspend fun save(
        take: Take,
        frames: List<RecordedFrame>,
        summary: RecordingSummary
    ): EmptyResult<DataError.Local> = withContext(ioDispatcher) {
        try {
            val dir = root.resolve(take.name)
            dir.resolve(KEYPOINTS_FILE).bufferedWriter().use { writer ->
                frames.forEach { writer.write(it.toKeypointLine()); writer.newLine() }
            }
            val hasVideo = summary.hasVideo && dir.resolve(VIDEO_FILE).length() > 0
            val dto = summary.copy(hasVideo = hasVideo).toDto(startedAt[take.name].orEmpty(), cameraName)
            // summary.json zuletzt: erst mit ihr ist die Aufnahme fertig
            dir.resolve(SUMMARY_FILE).writeText(json.encodeToString(RecordingSummaryDto.serializer(), dto) + "\n")
            startedAt -= take.name
            log.i { "Aufnahme ${take.name}: ${dto.expectedReps} Wiederholungen, ${dto.framesRecorded} Frames, ${dto.measuredFps} fps" }
            Result.Success(Unit)
        } catch (e: IOException) {
            log.e(e) { "Aufnahme ${take.name} nicht gespeichert" }
            Result.Error(e.toDataError())
        }
    }

    override suspend fun discard(take: Take) {
        withContext(ioDispatcher) {
            root.resolve(take.name).deleteRecursively()
            startedAt -= take.name
        }
    }

    private fun takeDirs(dir: File): List<File> =
        dir.listFiles()?.filter { it.isDirectory && it.name.startsWith(PREFIX) }.orEmpty()

    private fun IOException.toDataError(): DataError.Local =
        if (generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains("ENOSPC") == true }) {
            DataError.Local.DISK_FULL
        } else {
            DataError.Local.UNKNOWN
        }

    private companion object {
        const val PREFIX = "rec"
    }
}
