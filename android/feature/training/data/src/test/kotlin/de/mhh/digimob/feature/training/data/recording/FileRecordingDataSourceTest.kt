package de.mhh.digimob.feature.training.data.recording

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.feature.training.domain.RecordingList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Mit echten Dateien im Temp-Ordner (Datei-I/O, ein Integrationstest). */
class FileRecordingDataSourceTest {

    @TempDir
    lateinit var root: File

    private fun recording(path: String, summary: String?) {
        val video = root.resolve(path).apply { parentFile.mkdirs(); writeText("kein echtes Video") }
        summary?.let { video.resolveSibling("summary.json").writeText(it) }
    }

    private suspend fun list(): RecordingList {
        val result = FileRecordingDataSource(root).recordings()
        assertThat(result).isInstanceOf<Result.Success<RecordingList>>()
        return (result as Result.Success).data
    }

    @Test
    fun `ohne videomodus txt NOT_FOUND`() = runTest {
        assertThat(FileRecordingDataSource(root).recordings()).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
    }

    @Test
    fun `Zeilen wie in der Desktop-App, Kommentare und Leerzeilen uebersprungen`() = runTest {
        recording("recordings/abduktion/rec1/video.mp4", """{"exercise": "abduktion", "expected_reps": 3, "camera": {"index": 0}}""")
        recording("recordings/rotation/rec2/video.mp4", null)
        root.resolve("videomodus.txt").writeText(
            """
            # Aufnahmen fuer den Test
            recordings/abduktion/rec1/video.mp4

              recordings/rotation/rec2/video.mp4  
            # recordings/rotation/rec3/video.mp4
            """.trimIndent()
        )
        val list = list()
        assertThat(list.recordings.map { it.name }).containsExactly("abduktion/rec1", "rotation/rec2")
        assertThat(list.recordings.first().videoPath).isEqualTo(root.resolve("recordings/abduktion/rec1/video.mp4").absolutePath)
        assertThat(list.problems).isEqualTo(emptyList<String>())
    }

    @Test
    fun `fehlendes Video, kaputte und unbekannte summary json werden gemeldet, der Rest laeuft`() = runTest {
        recording("recordings/abduktion/rec1/video.mp4", "{ kaputt")
        recording("recordings/abduktion/rec2/video.mp4", """{"exercise": "kniebeuge"}""")
        recording("recordings/abduktion/rec3/video.mp4", """{"expected_reps": 2}""")
        root.resolve("videomodus.txt").writeText(
            listOf(
                "recordings/abduktion/rec0/video.mp4",
                "recordings/abduktion/rec1/video.mp4",
                "recordings/abduktion/rec2/video.mp4",
                "recordings/abduktion/rec3/video.mp4"
            ).joinToString("\n")
        )
        val list = list()
        assertThat(list.recordings.map { it.name }).containsExactly("abduktion/rec3")
        assertThat(list.problems).hasSize(3)
    }
}
