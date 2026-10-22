package de.mhh.digimob.core.domain.exercise

import kotlin.math.atan2

/**
 * Kopf nach links/rechts drehen. Kein Gelenkwinkel: headRotation() aus den Pose-Landmarks 0-6.
 * Jeder Wechsel der Richtung ueber maxAngle hinaus zaehlt fuer diese Seite.
 */
class Halswirbelsaeule(target: Int?, preset: Preset = Preset.VOLL, values: Thresholds = emptyMap()) : Exercise(target) {
    override val id = ExerciseId.HALSWIRBELSAEULE
    override val barStyle = BarStyle.CENTER     // ein Balken unter dem Video, waechst zur Drehrichtung

    /** Ab hier wird die Drehung gezaehlt (Balken voll). Einfach: rund 20 % weniger weit. */
    val maxAngle: Double = THRESHOLDS.valueOf(MAX_ANGLE, preset, values)

    override val thresholds: Thresholds get() = mapOf(MAX_ANGLE to maxAngle)

    private var direction: Side? = null          // aktuelle Richtung, bleibt bis zur Gegenseite
    private var previousDirection: Side? = null  // zuletzt gezaehlte Richtung
    private var rotation = 0.0

    override fun measure(pose: Pose) {
        // Kopfdrehung in Grad: 0 = Blick in die Kamera, > 0 nach links, < 0 nach rechts
        rotation = headRotation(pose.landmarks, pose.width.toDouble(), pose.height.toDouble())
    }

    override fun count(t: Double) {
        if (rotation > maxAngle) {
            direction = Side.LEFT
        } else if (rotation < -maxAngle) {
            direction = Side.RIGHT
        }
        // Gezaehlt wird jeder Wechsel zur Gegenseite
        val current = direction
        if (current != null && current != previousDirection) {
            countRepetition(current)
            previousDirection = current
        }
    }

    // Leer in der Mitte (bis MIN_ANGLE) und bei Drehung zur Gegenseite, voll ab maxAngle
    override fun fill(side: Side): Double {
        val turn = if (side == Side.LEFT) rotation else -rotation
        return ExerciseMath.progress(turn, MIN_ANGLE, maxAngle, OVER_TARGET)
    }

    companion object {
        const val MAX_ANGLE = "max_angle"
        val LANDMARKS = Landmarks.GESICHT
        val THRESHOLDS = listOf(
            // ueber MIN_ANGLE (Balkenanfang)
            Threshold(MAX_ANGLE, voll = 35.0, einfach = 28.0, range = 20.0..80.0, unit = ThresholdUnit.DEGREES)
        )

        const val MIN_ANGLE = 15.0  // bis hier gilt der Kopf als mittig (Balken leer)

        /**
         * Kopfdrehung (links/rechts) in Grad aus den Pose-Landmarks 0-6, in Pixeln; z hat laut
         * MediaPipe etwa den Massstab von x, daher ebenfalls * Bildbreite.
         * Vektor 1 laeuft zwischen den Augenmitten, Vektor 2 senkrecht dazu durch die Nase,
         * Vektor 3 = ihr Kreuzprodukt, die Blickrichtung. Gemessen wird der Winkel von Vektor 3
         * gegen die Kamerablickrichtung (-z) in der x-z-Ebene. Nicht "verbessern" zu
         * angle(v2, v3): der ist als Normale immer 90 Grad.
         */
        fun headRotation(landmarks: List<Landmark>, imageWidth: Double, imageHeight: Double): Double {
            val p = landmarks.take(7).map { doubleArrayOf(it.x * imageWidth, it.y * imageHeight, it.z * imageWidth) }
            val nose = p[0]
            val leftEye = DoubleArray(3) { (p[1][it] + p[2][it] + p[3][it]) / 3 }    // 1-3: linkes Auge
            val rightEye = DoubleArray(3) { (p[4][it] + p[5][it] + p[6][it]) / 3 }   // 4-6: rechtes Auge

            // Vektor 1: zwischen den Augen (rechtes -> linkes Auge)
            val v1 = DoubleArray(3) { leftEye[it] - rightEye[it] }
            require(v1.any { it != 0.0 }) { "Kopfdrehung nicht bestimmbar: beide Augen auf einem Punkt" }
            // Vektor 2: vom Lotfusspunkt auf der Augenlinie zur Nase
            val noseFromEye = DoubleArray(3) { nose[it] - rightEye[it] }
            val s = dot(noseFromEye, v1) / dot(v1, v1)
            val foot = DoubleArray(3) { rightEye[it] + s * v1[it] }
            val v2 = DoubleArray(3) { nose[it] - foot[it] }
            // Vektor 3: Normale auf Vektor 1 und 2 = Blickrichtung des Gesichts
            val v3 = doubleArrayOf(
                v2[1] * v1[2] - v2[2] * v1[1],
                v2[2] * v1[0] - v2[0] * v1[2],
                v2[0] * v1[1] - v2[1] * v1[0]
            )
            // Das Kamerabild ist ungespiegelt, x waechst zur linken Seite des Patienten -> > 0 = links
            return Math.toDegrees(atan2(v3[0], -v3[2]))
        }

        private fun dot(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }
}
