package de.mhh.digimob.core.domain.exercise

/**
 * Die Uebungen. key = Name in der Python-App = Ordner unter recordings/ (summary.json "exercise").
 */
enum class ExerciseId(val key: String) {
    ABDUKTION("abduktion"),
    BIZEPSCURLS("bizepscurls"),
    BUTTERFLY("butterfly"),
    FLEXION_SCHULTER("flexion_schulter"),
    HALSWIRBELSAEULE("halswirbelsaeule"),
    MOBILISATION_SCHULTER("mobilisation_schulter"),
    ROTATION("rotation"),
    SEITENHEBEN("seitenheben");

    companion object {
        fun fromKey(key: String): ExerciseId? = entries.firstOrNull { it.key == key }
    }
}

/** Operationsart -> Uebungen, in der Reihenfolge der Auswahlseite und von "Alle automatisch starten". */
enum class OpType(val exercises: List<ExerciseId>) {
    BAUCH_OP(
        listOf(
            ExerciseId.HALSWIRBELSAEULE, ExerciseId.SEITENHEBEN, ExerciseId.ABDUKTION,
            ExerciseId.FLEXION_SCHULTER, ExerciseId.MOBILISATION_SCHULTER, ExerciseId.BUTTERFLY,
            ExerciseId.ROTATION, ExerciseId.BIZEPSCURLS
        )
    ),
    BRUST_OP(listOf(ExerciseId.HALSWIRBELSAEULE, ExerciseId.BUTTERFLY, ExerciseId.ROTATION))
}

/** Postoperativer Tag -> Wiederholungen jeder Uebung. */
enum class PostOpDay(val repetitions: Int) {
    TAG_1(5),
    TAG_2(6),
    TAG_3(7),
    TAG_4(8),
    AB_TAG_5(10)
}

/** uebungen.py: die einzige Stelle, die die Uebungen kennt. Neue Uebung: Klasse + Eintrag hier. */
object ExerciseCatalog {

    /** Eine frische Uebung mit den Werten eines Presets. target = null: nur zaehlen (Systemtest). */
    fun create(id: ExerciseId, target: Int?, preset: Preset): Exercise = create(id, target, values(id, preset))

    /** Eine frische Uebung mit eingestellten Schwellen (Auswahlseite); fehlende gelten wie Voll. */
    fun create(id: ExerciseId, target: Int?, values: Thresholds): Exercise = when (id) {
        ExerciseId.ABDUKTION -> Abduktion(target, values = values)
        ExerciseId.BIZEPSCURLS -> Bizepscurls(target, values = values)
        ExerciseId.BUTTERFLY -> Butterfly(target, values = values)
        ExerciseId.FLEXION_SCHULTER -> FlexionSchulter(target, values = values)
        ExerciseId.HALSWIRBELSAEULE -> Halswirbelsaeule(target, values = values)
        ExerciseId.MOBILISATION_SCHULTER -> MobilisationSchulter(target, values = values)
        ExerciseId.ROTATION -> Rotation(target, values = values)
        ExerciseId.SEITENHEBEN -> Seitenheben(target, values = values)
    }

    /** Die einstellbaren Schwellen einer Uebung, in der Reihenfolge ihrer Schieberegler. */
    fun thresholds(id: ExerciseId): List<Threshold> = when (id) {
        ExerciseId.ABDUKTION -> Abduktion.THRESHOLDS
        ExerciseId.BIZEPSCURLS -> Bizepscurls.THRESHOLDS
        ExerciseId.BUTTERFLY -> Butterfly.THRESHOLDS
        ExerciseId.FLEXION_SCHULTER -> FlexionSchulter.THRESHOLDS
        ExerciseId.HALSWIRBELSAEULE -> Halswirbelsaeule.THRESHOLDS
        ExerciseId.MOBILISATION_SCHULTER -> MobilisationSchulter.THRESHOLDS
        ExerciseId.ROTATION -> Rotation.THRESHOLDS
        ExerciseId.SEITENHEBEN -> Seitenheben.THRESHOLDS
    }

    /** Die Landmarks, aus denen die Uebung rechnet: nur diese zeigt die Keypoints-Anzeige. */
    fun landmarks(id: ExerciseId): Set<Int> = when (id) {
        ExerciseId.ABDUKTION, ExerciseId.FLEXION_SCHULTER, ExerciseId.MOBILISATION_SCHULTER,
        ExerciseId.SEITENHEBEN -> Armheben.LANDMARKS
        ExerciseId.BIZEPSCURLS -> Bizepscurls.LANDMARKS
        ExerciseId.BUTTERFLY -> Butterfly.LANDMARKS
        ExerciseId.HALSWIRBELSAEULE -> Halswirbelsaeule.LANDMARKS
        ExerciseId.ROTATION -> Rotation.LANDMARKS
    }

    /** Uebung.werte(preset): die Werte eines Presets. */
    fun values(id: ExerciseId, preset: Preset): Thresholds = thresholds(id).associate { it.key to it.value(preset) }

    /** Die Presets mit genau diesen Werten: eins, beide (Voll = Einfach) oder keins (von Hand angepasst). */
    fun presetsOf(id: ExerciseId, values: Thresholds): Set<Preset> =
        Preset.entries.filter { values(id, it) == values }.toSet()

    /** Sind Voll und Einfach gleich (Seitheben, Mobilisation)? Die Auswahlseite zeigt dann "Voll = Einfach". */
    fun presetsEqual(id: ExerciseId): Boolean = values(id, Preset.VOLL) == values(id, Preset.EINFACH)

    /**
     * Uebung.pruefe(): genau die Schwellen der Uebung, alle groesser als 0 und in ihrem Bereich, jede
     * untere Schwelle min_X unter ihrer oberen max_X. null = passt.
     */
    fun validate(id: ExerciseId, values: Thresholds): ThresholdError? {
        val thresholds = thresholds(id)
        values.keys.firstOrNull { key -> thresholds.none { it.key == key } }?.let { return ThresholdError.Unknown(it) }
        for (threshold in thresholds) {
            val value = values[threshold.key] ?: return ThresholdError.Missing(threshold.key)
            if (!(value > 0)) return ThresholdError.NotPositive(threshold.key)     // auch NaN
            if (value !in threshold.range) return ThresholdError.OutOfRange(threshold.key)
            val upper = "max_" + threshold.key.removePrefix("min_")
            val upperValue = values[upper]
            if (threshold.key.startsWith("min_") && upperValue != null && value >= upperValue) {
                return ThresholdError.MinNotBelowMax(threshold.key, upper)
            }
        }
        return null
    }
}
