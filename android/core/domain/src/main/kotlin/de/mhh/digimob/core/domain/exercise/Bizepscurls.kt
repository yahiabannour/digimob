package de.mhh.digimob.core.domain.exercise

/**
 * Ellbogenwinkel (Handgelenk-Ellbogen-Schulter) je Arm, Median der letzten 5 Frames, mit zwei
 * Schwellen (Hysterese): gebeugt unter minAngle = UP, gestreckt ueber maxAngle = DOWN.
 * UP nach DOWN zaehlt. Eine Phase gilt erst nach CONFIRM_TIME s Haltezeit.
 */
class Bizepscurls(target: Int?, preset: Preset = Preset.VOLL, values: Thresholds = emptyMap()) : Exercise(target) {
    override val id = ExerciseId.BIZEPSCURLS

    private enum class Stage { DOWN, UP }

    /** Ziel: Ellbogen gebeugt unter (Einfach: weniger weit beugen). */
    val minAngle: Double = THRESHOLDS.valueOf(MIN_ANGLE, preset, values)

    /** Oberarme am Koerper, sonst wird nicht gezaehlt (Einfach: Pflege-Aufnahmen 50-72 Grad). */
    val maxShoulder: Double = THRESHOLDS.valueOf(MAX_SHOULDER, preset, values)

    // Gestreckt = STRECKEN Grad ueber dem Ziel: der Unterarm faellt von selbst zurueck, aber nicht
    // immer bis 150 Grad (Pflege-Aufnahme rec10)
    val maxAngle: Double get() = minAngle + STRECKEN

    override val thresholds: Thresholds
        get() = mapOf(MIN_ANGLE to minAngle, MAX_SHOULDER to maxShoulder)

    private val stage = mutableMapOf<Side, Stage?>().apply { sides.forEach { put(it, null) } }
    private val hold = sides.associateWith { HoldTime<Stage>(CONFIRM_TIME) }
    private val recent = sides.associateWith { mutableListOf<Double>() }
    private val shoulder = mutableMapOf<Side, Double>()
    private val elbow = mutableMapOf<Side, Double>()

    override fun measure(pose: Pose) {
        shoulder.clear()
        elbow.clear()
        for (side in sides) {
            val schulter = pose.xy(Landmarks.SCHULTER.getValue(side))
            val ellbogen = pose.xy(Landmarks.ELLBOGEN.getValue(side))
            shoulder[side] = ExerciseMath.angle(pose.hip(side), schulter, ellbogen)
            val angle = ExerciseMath.angle(pose.xy(Landmarks.HANDGELENK.getValue(side)), ellbogen, schulter)
            val values = recent.getValue(side)
            values += angle
            if (values.size > MEDIAN_FRAMES) values.removeAt(0)
            elbow[side] = values.median()
        }
    }

    override fun ready(): Boolean = shoulder.values.all { it <= maxShoulder }

    override fun count(t: Double) {
        for (side in sides) {
            val angle = elbow.getValue(side)
            val candidate = when {
                angle > maxAngle -> Stage.DOWN
                angle < minAngle -> Stage.UP
                else -> null
            }
            when (hold.getValue(side).confirm(candidate, t)) {
                Stage.DOWN -> stage[side] = Stage.DOWN
                Stage.UP -> if (stage[side] == Stage.DOWN) {
                    stage[side] = Stage.UP
                    countRepetition(side)
                }
                null -> Unit
            }
        }
    }

    // Ohne gueltige Messung (keine Pose, Fehler, Schultern ausserhalb der Startposition) darf eine
    // angefangene Phase nicht weiterlaufen
    override fun lost() {
        hold.values.forEach { it.reset() }
    }

    // Leer bei gestrecktem Arm (BAR_START), 1 am Ziel, darueber hinaus
    override fun fill(side: Side): Double =
        ExerciseMath.progress(elbow.getValue(side), BAR_START, minAngle, OVER_TARGET)

    override fun back(side: Side): Boolean = stage[side] == Stage.UP   // gebeugt gezaehlt, erst strecken

    companion object {
        const val MIN_ANGLE = "min_angle"
        const val MAX_SHOULDER = "max_shoulder"
        val LANDMARKS = Landmarks.ARME
        val THRESHOLDS = listOf(
            Threshold(MIN_ANGLE, voll = 110.0, einfach = 120.0, range = 30.0..155.0, unit = ThresholdUnit.DEGREES),
            Threshold(MAX_SHOULDER, voll = 50.0, einfach = 60.0, range = 20.0..90.0, unit = ThresholdUnit.DEGREES)
        )

        const val STRECKEN = 15.0
        const val BAR_START = 160.0
        // Auf allen 13 Aufnahmen zaehlt nur 0.15 s alles richtig: 0.10 s zaehlt in rec5 rechts ein
        // Flackern mit, 0.20 s verpasst in rec6 (Video) eine Wiederholung. Knapp.
        const val CONFIRM_TIME = 0.15
        const val MEDIAN_FRAMES = 5
    }
}
