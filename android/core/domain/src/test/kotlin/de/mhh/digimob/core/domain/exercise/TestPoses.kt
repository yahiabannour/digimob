package de.mhh.digimob.core.domain.exercise

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kuenstliche Pose wie in einheitentest.py: Oberkoerper senkrecht, je Arm der Winkel virtuelle
 * Huefte-Schulter-Ellbogen (arm, [Pose.hip]) und Handgelenk-Ellbogen-Schulter (elbow) in Grad.
 * hipInPicture = false: die echte Huefte liegt unter dem Bildrand und ist unsichtbar (Tablet).
 */
fun armPose(
    arm: Pair<Double, Double> = 20.0 to 20.0,
    elbow: Pair<Double, Double> = 180.0 to 180.0,
    visibility: Double? = 1.0,
    leftVisibility: Double? = visibility,
    hipInPicture: Boolean = true
): Pose {
    val points = MutableList(33) { Point(0.5, 0.5) }
    val vis = MutableList(33) { visibility }
    val shoulders = mapOf(Side.LEFT to Point(0.4, 0.4), Side.RIGHT to Point(0.6, 0.4))
    listOf(Side.LEFT, Side.RIGHT).forEachIndexed { k, side ->
        val shoulder = shoulders.getValue(side)
        val other = shoulders.getValue(if (side == Side.LEFT) Side.RIGHT else Side.LEFT)
        val sign = if (side == Side.LEFT) -1 else 1     // Arme gehen nach aussen
        // Richtung Schulter -> virtuelle Huefte, um arm Grad nach aussen gedreht
        val width = ExerciseMath.distance(shoulder, other)
        val hx = HIP_INWARD * (other.x - shoulder.x)
        val hy = HIP_DOWN * width
        val length = sqrt(hx * hx + hy * hy)
        val a = Math.toRadians(-sign * if (k == 0) arm.first else arm.second)
        val upperX = (hx * cos(a) - hy * sin(a)) / length
        val upperY = (hx * sin(a) + hy * cos(a)) / length   // Schulter -> Ellbogen, y nach unten
        val el = Point(shoulder.x + 0.2 * upperX, shoulder.y + 0.2 * upperY)
        // Unterarm: um (180 - elbow) Grad gegen den Oberarm gedreht
        val b = Math.toRadians(180 - if (k == 0) elbow.first else elbow.second)
        val fx = upperX * cos(b) - upperY * sin(b)
        val fy = upperX * sin(b) + upperY * cos(b)
        val wr = Point(el.x + 0.2 * fx, el.y + 0.2 * fy)
        points[Landmarks.SCHULTER.getValue(side)] = shoulder
        points[Landmarks.ELLBOGEN.getValue(side)] = el
        points[Landmarks.HANDGELENK.getValue(side)] = wr
        // Die echte Huefte: im Bild unter der Schulter, sonst wie am Tablet (y 1.4, Sichtbarkeit 0)
        val hip = REAL_HIP.getValue(side)
        points[hip] = Point(shoulder.x, if (hipInPicture) 0.8 else 1.4)
        if (!hipInPicture) vis[hip] = 0.0
        if (side == Side.LEFT) {
            listOf(Landmarks.SCHULTER, Landmarks.ELLBOGEN, Landmarks.HANDGELENK)
                .forEach { vis[it.getValue(side)] = leftVisibility }
        }
    }
    return Pose(points.mapIndexed { i, p -> Landmark(p.x, p.y, 0.0, vis[i], 1.0) }, 640, 480)
}

/** MediaPipes Huefte; die Uebungen nutzen sie nicht ([Pose.hip]). */
val REAL_HIP = mapOf(Side.LEFT to 23, Side.RIGHT to 24)

fun arms(left: Double, right: Double = left, visibility: Double? = 1.0) =
    armPose(arm = left to right, visibility = visibility)

/**
 * Gesicht (Landmarks 0-6), um [degrees] um die senkrechte Achse gedreht; > 0 dreht die Nase zur
 * linken Seite des Patienten (im ungespiegelten Bild nach rechts, x waechst).
 */
fun facePose(degrees: Double, eyesApart: Boolean = true): Pose {
    val w = 640.0
    val half = if (eyesApart) 0.05 else 0.0
    // Kopfmitte bei x = 0.5; Augen auf Hoehe 0.4, Nase darunter und naeher an der Kamera (z < 0)
    val face = listOf(
        Triple(0.0, 0.45, -0.05),                                          // 0 Nase
        Triple(half - 0.01, 0.4, 0.0), Triple(half, 0.4, 0.0), Triple(half + 0.01, 0.4, 0.0),        // 1-3 linkes Auge
        Triple(-half + 0.01, 0.4, 0.0), Triple(-half, 0.4, 0.0), Triple(-half - 0.01, 0.4, 0.0)      // 4-6 rechtes Auge
    )
    val r = Math.toRadians(degrees)
    val rotated = face.map { (x, y, z) ->
        // Drehung in der x-z-Ebene (z in Einheiten der Bildbreite, wie x)
        Landmark(0.5 + x * cos(r) - z * sin(r), y, x * sin(r) + z * cos(r), 1.0, 1.0)
    }
    val rest = List(33 - 7) { Landmark(0.5, 0.8, 0.0, 1.0, 1.0) }
    return Pose(rotated + rest, w.toInt(), 480)
}

/** Frames im Abstand 1/fps in die Uebung; null = keine Pose. */
fun <E : Exercise> E.run(frames: List<Pose?>, fps: Double = 30.0, t0: Double = 0.0): E {
    frames.forEachIndexed { i, pose -> update(pose, t0 + i / fps) }
    return this
}

operator fun Pose.times(n: Int): List<Pose> = List(n) { this }
