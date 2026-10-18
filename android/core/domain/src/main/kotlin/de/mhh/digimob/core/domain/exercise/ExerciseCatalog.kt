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

    /** Eine frische Uebung. target = null: nur zaehlen (Aufnahmen testen). */
    fun create(id: ExerciseId, target: Int?, preset: Preset): Exercise = when (id) {
        ExerciseId.ABDUKTION -> Abduktion(target, preset)
        ExerciseId.BIZEPSCURLS -> Bizepscurls(target, preset)
        ExerciseId.BUTTERFLY -> Butterfly(target, preset)
        ExerciseId.FLEXION_SCHULTER -> FlexionSchulter(target, preset)
        ExerciseId.HALSWIRBELSAEULE -> Halswirbelsaeule(target, preset)
        ExerciseId.MOBILISATION_SCHULTER -> MobilisationSchulter(target, preset)
        ExerciseId.ROTATION -> Rotation(target, preset)
        ExerciseId.SEITENHEBEN -> Seitenheben(target, preset)
    }

    /** Sind Voll und Einfach gleich (Seitheben, Mobilisation)? Die Auswahlseite zeigt dann "Voll = Einfach". */
    fun presetsEqual(id: ExerciseId): Boolean =
        create(id, null, Preset.VOLL).thresholds == create(id, null, Preset.EINFACH).thresholds
}
