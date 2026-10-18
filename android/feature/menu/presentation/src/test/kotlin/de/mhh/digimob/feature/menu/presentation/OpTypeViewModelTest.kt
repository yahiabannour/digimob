package de.mhh.digimob.feature.menu.presentation

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.exercise.PostOpDay
import de.mhh.digimob.feature.menu.presentation.optype.OpTypeAction
import de.mhh.digimob.feature.menu.presentation.optype.OpTypeViewModel
import org.junit.jupiter.api.Test

class OpTypeViewModelTest {

    @Test
    fun `ohne gespeicherten Tag gilt Tag 1`() {
        assertThat(OpTypeViewModel(SavedStateHandle()).state.value.day).isEqualTo(PostOpDay.TAG_1)
    }

    @Test
    fun `gewaehlter Tag uebersteht das Ende des Prozesses`() {
        val handle = SavedStateHandle()
        OpTypeViewModel(handle).onAction(OpTypeAction.OnDaySelect(PostOpDay.TAG_4))
        assertThat(OpTypeViewModel(handle).state.value.day).isEqualTo(PostOpDay.TAG_4)
    }

    @Test
    fun `unbekannter gespeicherter Tag faellt auf Tag 1 zurueck`() {
        val viewModel = OpTypeViewModel(SavedStateHandle(mapOf("day" to "TAG_9")))
        assertThat(viewModel.state.value.day).isEqualTo(PostOpDay.TAG_1)
    }

    @Test
    fun `Tag waehlen schliesst das Menue`() {
        val viewModel = OpTypeViewModel(SavedStateHandle())
        viewModel.onAction(OpTypeAction.OnDayMenuClick)
        assertThat(viewModel.state.value.isDayMenuExpanded).isTrue()
        viewModel.onAction(OpTypeAction.OnDaySelect(PostOpDay.AB_TAG_5))
        assertThat(viewModel.state.value.isDayMenuExpanded).isFalse()
        assertThat(viewModel.state.value.day.repetitions).isEqualTo(10)
    }

    @Test
    fun `Menue-Knopf zweimal schliesst wieder`() {
        val viewModel = OpTypeViewModel(SavedStateHandle())
        viewModel.onAction(OpTypeAction.OnDayMenuClick)
        viewModel.onAction(OpTypeAction.OnDayMenuClick)
        assertThat(viewModel.state.value.isDayMenuExpanded).isFalse()
    }
}
