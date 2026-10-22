package de.mhh.digimob.core.domain.exercise

/**
 * Handgelenkabstand als Vielfaches der Schulterbreite: MAX (> maxDistance) -> MIN (< MIN_DISTANCE)
 * zaehlt. Die Butterfly-Haltung ist bewusst kein ready()-Gate: Meldung und Ansagen laufen ab der
 * ersten erkannten Pose, gezaehlt und im Balken angezeigt wird nur in Haltung.
 */
class Butterfly(target: Int?, preset: Preset = Preset.VOLL, values: Thresholds = emptyMap()) :
    Exercise(target, sides = listOf(Side.BOTH)) {
    override val id = ExerciseId.BUTTERFLY
    override val barStyle = BarStyle.SINGLE
    override val praise = false

    private enum class Stage { MIN, MAX }

    /** Arme geoeffnet (Ziel). Einfach: rund 20 % weniger weit oeffnen. */
    val maxDistance: Double = THRESHOLDS.valueOf(MAX_DISTANCE_KEY, preset, values)

    override val thresholds: Thresholds get() = mapOf(MAX_DISTANCE_KEY to maxDistance)

    private var stage: Stage? = null
    private var armUp = mapOf<Side, Boolean>()
    private var shouldersDistance = 0.0
    private var wristsDistance = 0.0

    override fun measure(pose: Pose) {
        val arms = listOf(Side.LEFT, Side.RIGHT)
        val shoulder = arms.associateWith { side ->
            ExerciseMath.angle(
                pose.hip(side),
                pose.xy(Landmarks.SCHULTER.getValue(side)),
                pose.xy(Landmarks.ELLBOGEN.getValue(side))
            )
        }
        // Haltung je Arm: Hand ueber dem Ellbogen (y waechst nach unten) oder Oberarm gehoben
        armUp = arms.associateWith { side ->
            pose.xy(Landmarks.ELLBOGEN.getValue(side)).y > pose.xy(Landmarks.HANDGELENK.getValue(side)).y ||
                shoulder.getValue(side) > MIN_SHOULDER
        }
        shouldersDistance = ExerciseMath.distance(
            pose.xy(Landmarks.SCHULTER.getValue(Side.RIGHT)), pose.xy(Landmarks.SCHULTER.getValue(Side.LEFT))
        )
        wristsDistance = ExerciseMath.distance(
            pose.xy(Landmarks.HANDGELENK.getValue(Side.RIGHT)), pose.xy(Landmarks.HANDGELENK.getValue(Side.LEFT))
        )
    }

    private fun inPosition(): Boolean = armUp.values.all { it }

    override fun count(t: Double) {
        if (!inPosition()) return
        if (wristsDistance > shouldersDistance * maxDistance && stage != Stage.MAX) {
            stage = Stage.MAX
        }
        if (wristsDistance < shouldersDistance * MIN_DISTANCE && stage == Stage.MAX) {
            stage = Stage.MIN
            countRepetition(Side.BOTH)
        }
    }

    // Fortschritt der Oeffnung: von MIN_DISTANCE (leer) bis maxDistance (voll) x Schulterbreite
    override fun fill(side: Side): Double? {
        if (!inPosition()) return null
        return ExerciseMath.progress(
            wristsDistance, shouldersDistance * MIN_DISTANCE, shouldersDistance * maxDistance, OVER_TARGET
        )
    }

    override fun back(side: Side): Boolean = stage == Stage.MAX    // offen gewesen, zaehlt erst vorne

    companion object {
        const val MAX_DISTANCE_KEY = "max_distance"
        val LANDMARKS = Landmarks.ARME
        val THRESHOLDS = listOf(
            // ueber MIN_DISTANCE, sonst zaehlt jedes Bild
            Threshold(MAX_DISTANCE_KEY, voll = 1.8, einfach = 1.5, range = 1.2..3.0, unit = ThresholdUnit.TIMES)
        )

        const val MIN_SHOULDER = 70.0   // Oberarm so weit gehoben zaehlt als Butterfly-Haltung
        const val MIN_DISTANCE = 1.0    // Arme vorne zusammen
    }
}
