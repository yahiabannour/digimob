package de.mhh.digimob.core.presentation

import androidx.annotation.StringRes
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.ExerciseMessage
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.PostOpDay
import de.mhh.digimob.core.domain.exercise.Preset

/** Titel im Menue und im Videobild. */
@get:StringRes
val ExerciseId.titleRes: Int
    get() = when (this) {
        ExerciseId.ABDUKTION -> R.string.exercise_abduktion
        ExerciseId.BIZEPSCURLS -> R.string.exercise_bizepscurls
        ExerciseId.BUTTERFLY -> R.string.exercise_butterfly
        ExerciseId.FLEXION_SCHULTER -> R.string.exercise_flexion_schulter
        ExerciseId.HALSWIRBELSAEULE -> R.string.exercise_halswirbelsaeule
        ExerciseId.MOBILISATION_SCHULTER -> R.string.exercise_mobilisation_schulter
        ExerciseId.ROTATION -> R.string.exercise_rotation
        ExerciseId.SEITENHEBEN -> R.string.exercise_seitenheben
    }

@get:StringRes
val Preset.labelRes: Int
    get() = when (this) {
        Preset.VOLL -> R.string.preset_voll
        Preset.EINFACH -> R.string.preset_einfach
    }

@get:StringRes
val OpType.labelRes: Int
    get() = when (this) {
        OpType.BAUCH_OP -> R.string.op_bauch
        OpType.BRUST_OP -> R.string.op_brust
    }

@get:StringRes
val PostOpDay.labelRes: Int
    get() = when (this) {
        PostOpDay.TAG_1 -> R.string.day_1
        PostOpDay.TAG_2 -> R.string.day_2
        PostOpDay.TAG_3 -> R.string.day_3
        PostOpDay.TAG_4 -> R.string.day_4
        PostOpDay.AB_TAG_5 -> R.string.day_5
    }

fun ExerciseMessage.toUiText(): UiText = when (this) {
    ExerciseMessage.Start -> UiText.StringResource(R.string.message_start)
    is ExerciseMessage.Target -> UiText.StringResource(
        if (praise) R.string.message_target_praise else R.string.message_target,
        arrayOf(repetitions)
    )
    ExerciseMessage.Done -> UiText.StringResource(R.string.message_done)
    ExerciseMessage.CountOnly -> UiText.StringResource(R.string.message_count_only)
}

fun DataError.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
}
