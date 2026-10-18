package de.mhh.digimob.feature.menu.presentation.optype

import de.mhh.digimob.core.domain.exercise.PostOpDay

data class OpTypeState(
    val day: PostOpDay = PostOpDay.TAG_1,
    val isDayMenuExpanded: Boolean = false
)

sealed interface OpTypeAction {
    data object OnDayMenuClick : OpTypeAction
    data object OnDayMenuDismiss : OpTypeAction
    data class OnDaySelect(val day: PostOpDay) : OpTypeAction
}
