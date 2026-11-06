package de.mhh.digimob.feature.training.data.speech

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PickOtherTest {

    @Test
    fun `nie zweimal derselbe, und jeder andere kommt vor`() {
        val random = Random(1)
        var last: Int? = null
        val seen = mutableSetOf<Int>()
        repeat(200) {
            val next = pickOther(4, last, random)
            if (last != null) assertThat(next == last).isEqualTo(false)
            seen += next
            last = next
        }
        assertThat(seen.toList()).containsExactlyInAnyOrder(0, 1, 2, 3)
    }

    @Test
    fun `ein Satz, kein Vorgaenger oder ungueltiger Vorgaenger`() {
        assertThat(pickOther(1, 0)).isEqualTo(0)
        assertThat(pickOther(3, null, Random(2)) in 0..2).isEqualTo(true)
        assertThat(pickOther(3, 7, Random(2)) in 0..2).isEqualTo(true)
    }
}
