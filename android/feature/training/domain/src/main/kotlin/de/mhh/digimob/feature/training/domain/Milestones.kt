package de.mhh.digimob.feature.training.domain

/**
 * Ansagen waehrend einer Uebung (sprachausgabe.milestones): Start, Haelfte, "noch N", letzte
 * Wiederholung, fertig. Jede hoechstens einmal je Uebung, eine je Aufruf.
 */
class Milestones(private val speaker: Speaker) {
    private val executed = mutableSetOf<Any>()

    /** Vor jeder Uebung. */
    fun reset() {
        executed.clear()
    }

    fun update(total: Int, target: Int) {
        val remaining = target - total
        when {
            total == 0 && "start" !in executed -> announce("start", Announcement.Start(target))
            total >= target / 2.0 && "halfway" !in executed -> announce("halfway", Announcement.Halfway)
            remaining > 0 && remaining !in executed && total != 0 -> announce(
                remaining,
                if (remaining == 1) Announcement.Last else Announcement.Remaining(remaining)
            )
            remaining <= 0 && "done" !in executed -> announce("done", Announcement.Done(target))
        }
    }

    private fun announce(key: Any, announcement: Announcement) {
        speaker.say(announcement)
        executed += key
    }
}
