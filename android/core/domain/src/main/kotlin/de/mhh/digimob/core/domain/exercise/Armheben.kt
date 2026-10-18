package de.mhh.digimob.core.domain.exercise

/**
 * Abduktion, Seitheben, Flexion und Mobilisation Schulter: Winkel Huefte-Schulter-Ellbogen je Arm.
 * Eine Wiederholung ist unten -> ueber maxAngle (oben) -> wieder unter minAngle (unten).
 * Die Uebungen unterscheiden sich nur in Texten und Zielhoehe (maxAngle).
 */
abstract class Armheben(target: Int?, val maxAngle: Double) : Exercise(target) {

    private enum class Stage { DOWN, UP }

    // Unten = RUECKWEG Grad unter der Zielhoehe: Schwerkraft und Bett bringen den Arm ohnehin
    // zurueck, wie weit, haengt aber vom Koerper ab (Ruhewinkel 11-21 Grad in den ersten
    // Aufnahmen, 36-50 bei der Pflege). 15 Grad halten Abstand zum Zittern um die Zielhoehe.
    val minAngle: Double get() = maxAngle - RUECKWEG

    override val thresholds: Map<String, Double> get() = mapOf("max_angle" to maxAngle)

    private val stage = mutableMapOf<Side, Stage?>().apply { sides.forEach { put(it, null) } }
    private val recent = sides.associateWith { mutableListOf<Double>() }
    private val angle = mutableMapOf<Side, Double>()
    private val visible = mutableMapOf<Side, Boolean>()

    override fun measure(pose: Pose) {
        angle.clear()
        visible.clear()
        for (side in sides) {
            val points = listOf(
                Landmarks.HUEFTE.getValue(side),
                Landmarks.SCHULTER.getValue(side),
                Landmarks.ELLBOGEN.getValue(side)
            )
            val raw = ExerciseMath.angle(pose.xy(points[0]), pose.xy(points[1]), pose.xy(points[2]))
            // Eine Seite, deren schwaechster Punkt unsicherer ist, zaehlt in diesem Frame nicht: ein
            // verdeckter Ellbogen springt von Frame zu Frame, und der Winkel mit ihm (seitenheben/rec8)
            visible[side] = points.minOf { pose.landmarks[it].visibilityOrOne } >= MIN_VISIBILITY
            val values = recent.getValue(side)
            if (visible.getValue(side)) {
                values += raw
                if (values.size > MEDIAN_FRAMES) values.removeAt(0)
            }
            angle[side] = if (values.isNotEmpty()) values.median() else raw
        }
    }

    override fun count(t: Double) {
        for (side in sides) {
            if (!visible.getValue(side)) continue
            val a = angle.getValue(side)
            if (a < minAngle) {
                if (stage[side] == Stage.UP) countRepetition(side)
                stage[side] = Stage.DOWN
            } else if (a > maxAngle && stage[side] != null) {
                // UP erst, nachdem der Arm unten war (Startposition): am Anfang einer Aufnahme
                // liegt die Erkennung oft fuer einige Frames daneben (seitenheben/rec8 aus dem Video)
                stage[side] = Stage.UP
            }
        }
    }

    // Fortschritt dieses Arms: leer bis BAR_START, 1 an der Zielhoehe, darueber bis OVER_TARGET
    override fun fill(side: Side): Double =
        ExerciseMath.progress(angle.getValue(side), BAR_START, maxAngle, OVER_TARGET)

    override fun back(side: Side): Boolean = stage[side] == Stage.UP    // oben gewesen, zaehlt erst unten

    companion object {
        const val RUECKWEG = 15.0
        const val BAR_START = 20.0          // Balken leer bis hier (Arm haengt)
        const val MIN_VISIBILITY = 0.6      // knapp: ein ruhender Ellbogen hat oft nur 0.6-0.8
        const val MEDIAN_FRAMES = 5         // einzelne Ausreisser fallen heraus, ~0.07 s Verzoegerung
    }
}

class Abduktion(target: Int?, preset: Preset) :
    Armheben(target, maxAngle = if (preset == Preset.EINFACH) 120.0 else 150.0) {
    override val id = ExerciseId.ABDUKTION
}

// Einfach = Voll: die Zielhoehe liegt schon knapp ueber dem Ruhewinkel der Pflege-Aufnahmen (bis 50
// Grad), tiefer laege "unten" (Zielhoehe - RUECKWEG) im Ruhewinkel
class Seitenheben(target: Int?, @Suppress("UNUSED_PARAMETER") preset: Preset) :
    Armheben(target, maxAngle = 70.0) {
    override val id = ExerciseId.SEITENHEBEN
}

class FlexionSchulter(target: Int?, preset: Preset) :
    Armheben(target, maxAngle = if (preset == Preset.EINFACH) 110.0 else 140.0) {
    override val id = ExerciseId.FLEXION_SCHULTER
}

// Einfach = Voll, wie Seitheben
class MobilisationSchulter(target: Int?, @Suppress("UNUSED_PARAMETER") preset: Preset) :
    Armheben(target, maxAngle = 70.0) {
    override val id = ExerciseId.MOBILISATION_SCHULTER
}
