package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

class MilestonesTest {

    private fun announcements(target: Int, totals: List<Int>): List<Announcement> {
        val speaker = FakeSpeaker()
        val milestones = Milestones(speaker)
        totals.forEach { milestones.update(it, target) }
        return speaker.said
    }

    @Test
    fun `jede Ansage einmal, eine je Aufruf, in der Reihenfolge der Desktop-App`() {
        assertThat(announcements(5, listOf(0, 0, 1, 2, 3, 3, 4, 5, 5, 5))).containsExactly(
            Announcement.Start(5),
            Announcement.Remaining(4),
            Announcement.Remaining(3),
            Announcement.Halfway,           // 3 von 5: die Haelfte hat Vorrang vor "noch 2"
            Announcement.Remaining(2),      // ... das kommt beim naechsten Bild
            Announcement.Last,
            Announcement.Done(5)
        )
    }

    @Test
    fun `Ziel 1 sagt auch die Haelfte, wie die Desktop-App`() {
        assertThat(announcements(1, listOf(0, 1, 1))).containsExactly(
            Announcement.Start(1), Announcement.Halfway, Announcement.Done(1)
        )
    }

    @Test
    fun `Sprung ueber mehrere Wiederholungen sagt nur die aktuelle`() {
        assertThat(announcements(10, listOf(0, 7))).containsExactly(Announcement.Start(10), Announcement.Halfway)
    }

    @Test
    fun `reset erlaubt die Ansagen fuer die naechste Uebung neu`() {
        val speaker = FakeSpeaker()
        val milestones = Milestones(speaker)
        milestones.update(0, 5)
        milestones.reset()
        milestones.update(0, 5)
        assertThat(speaker.said).containsExactly(Announcement.Start(5), Announcement.Start(5))
    }

    @Test
    fun `ohne Fortschritt nach dem Start bleibt es still`() {
        val speaker = FakeSpeaker()
        val milestones = Milestones(speaker)
        milestones.update(0, 5)
        speaker.said.clear()
        repeat(100) { milestones.update(0, 5) }
        assertThat(speaker.said).isEmpty()
    }
}
