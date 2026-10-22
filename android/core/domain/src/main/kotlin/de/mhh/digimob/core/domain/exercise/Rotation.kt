package de.mhh.digimob.core.domain.exercise

/**
 * Handgelenkabstand als Vielfaches des Ellbogenabstands: MAX (> maxDistance) -> MIN
 * (< MIN_DISTANCE) zaehlt. Gate: beide Oberarme am Koerper (Schulterwinkel < MAX_SHOULDER).
 */
class Rotation(target: Int?, preset: Preset = Preset.VOLL, values: Thresholds = emptyMap()) :
    Exercise(target, sides = listOf(Side.BOTH)) {
    override val id = ExerciseId.ROTATION
    override val barStyle = BarStyle.SINGLE
    override val praise = false

    private enum class Stage { MIN, MAX }

    /** Unterarme nach aussen gedreht (Ziel). Einfach: weniger weit nach aussen. */
    val maxDistance: Double = THRESHOLDS.valueOf(MAX_DISTANCE_KEY, preset, values)

    override val thresholds: Thresholds get() = mapOf(MAX_DISTANCE_KEY to maxDistance)

    private var stage: Stage? = null
    private var shoulder = mapOf<Side, Double>()
    private var elbowsDistance = 0.0
    private var wristsDistance = 0.0

    override fun measure(pose: Pose) {
        shoulder = listOf(Side.LEFT, Side.RIGHT).associateWith { side ->
            ExerciseMath.angle(
                pose.hip(side),
                pose.xy(Landmarks.SCHULTER.getValue(side)),
                pose.xy(Landmarks.ELLBOGEN.getValue(side))
            )
        }
        elbowsDistance = ExerciseMath.distance(
            pose.xy(Landmarks.ELLBOGEN.getValue(Side.RIGHT)), pose.xy(Landmarks.ELLBOGEN.getValue(Side.LEFT))
        )
        wristsDistance = ExerciseMath.distance(
            pose.xy(Landmarks.HANDGELENK.getValue(Side.RIGHT)), pose.xy(Landmarks.HANDGELENK.getValue(Side.LEFT))
        )
    }

    override fun ready(): Boolean = shoulder.values.all { it < MAX_SHOULDER }

    override fun count(t: Double) {
        if (wristsDistance > elbowsDistance * maxDistance && stage != Stage.MAX) {
            stage = Stage.MAX
        }
        if (wristsDistance < elbowsDistance * MIN_DISTANCE && stage == Stage.MAX) {
            stage = Stage.MIN
            countRepetition(Side.BOTH)
        }
    }

    // Fortschritt der Rotation: von MIN_DISTANCE (leer) bis maxDistance (voll) x Ellbogenabstand
    override fun fill(side: Side): Double = ExerciseMath.progress(
        wristsDistance, elbowsDistance * MIN_DISTANCE, elbowsDistance * maxDistance, OVER_TARGET
    )

    override fun back(side: Side): Boolean = stage == Stage.MAX    // offen gewesen, zaehlt erst vorne

    companion object {
        const val MAX_DISTANCE_KEY = "max_distance"
        val LANDMARKS = Landmarks.ARME
        val THRESHOLDS = listOf(
            // ueber MIN_DISTANCE, sonst zaehlt jedes Bild
            Threshold(MAX_DISTANCE_KEY, voll = 1.4, einfach = 1.3, range = 1.2..3.0, unit = ThresholdUnit.TIMES)
        )

        const val MAX_SHOULDER = 75.0   // Oberarme am Koerper, sonst wird nicht gezaehlt
        const val MIN_DISTANCE = 1.1    // Unterarme vorne (Startposition)
    }
}
