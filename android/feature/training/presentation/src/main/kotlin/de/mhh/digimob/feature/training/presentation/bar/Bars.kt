package de.mhh.digimob.feature.training.presentation.bar

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mhh.digimob.core.designsystem.theme.DigiMobColors
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.OVER_TARGET
import de.mhh.digimob.feature.training.presentation.R

/**
 * Senkrechter Balken: oben "noch" und gross die Zahl, darunter der Balken, der sich von unten
 * fuellt. Die Ziellinie liegt bei 1/OVER_TARGET der Hoehe, darueber geht der Balken noch weiter.
 * Pfeil nach unten = Ziel erreicht, jetzt zurueck; Haken = Wiederholung gezaehlt; blaue Linie =
 * hoechster Punkt.
 */
@Composable
internal fun VerticalBar(bar: BarUi, modifier: Modifier = Modifier) {
    val colors = DigiMobTheme.colors
    val description = barDescription(bar)
    Column(
        modifier = modifier.semantics { description?.let { contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BarCount(bar)
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
            val barWidth = size.width * 0.77f
            val x = (size.width - barWidth) / 2
            val bottom = size.height
            fun level(fill: Double) = bottom - size.height * (minOf(fill, OVER_TARGET) / OVER_TARGET).toFloat()

            if (bar.confirm) drawRect(colors.barConfirm, Offset(x, 0f), Size(barWidth, bottom))
            bar.fill?.takeIf { it > 0 }?.let { fill ->
                drawRect(barColor(colors, fill), Offset(x, level(fill)), Size(barWidth, bottom - level(fill)))
            }
            bar.peak?.let { peak ->
                drawLine(colors.barPeak, Offset(x, level(peak)), Offset(x + barWidth, level(peak)), strokeWidth = 6.dp.toPx())
            }
            drawRect(colors.barLine, Offset(x, 0f), Size(barWidth, bottom), style = Stroke(2.dp.toPx()))

            // Ziellinie ueber die ganze Breite, mit Spitzen am Rand
            val y = level(1.0)
            val tip = 12.dp.toPx()
            drawLine(colors.barLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 5.dp.toPx())
            drawPath(Path().apply { moveTo(0f, y - tip); lineTo(tip, y); lineTo(0f, y + tip); close() }, colors.barLine)
            drawPath(
                Path().apply { moveTo(size.width, y - tip); lineTo(size.width - tip, y); lineTo(size.width, y + tip); close() },
                colors.barLine
            )

            val center = Offset(size.width / 2, 0f)
            if (bar.confirm) {
                drawCheck(colors, center.copy(y = bottom / 2), barWidth * 0.8f)
            } else if (bar.back) {
                drawArrow(colors, center.copy(y = (y + bottom) / 2), barWidth * 0.8f)
            }
        }
    }
}

/**
 * Waagerechter Balken (Kopfdrehung): links fuellt von der Mitte nach links, rechts nach rechts,
 * je eine Ziellinie; ueber den Enden die Zahl je Seite (im Balken laege sie auf der Ziellinie).
 */
@Composable
internal fun CenterBar(left: BarUi, right: BarUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            CenterBarLabel(left)
            Spacer(modifier = Modifier.weight(1f))
            CenterBarLabel(right)
        }
        HorizontalBarCanvas(left, right, Modifier.fillMaxWidth().height(40.dp))
    }
}

/**
 * Ein Balken unter dem Video (Butterfly, Rotation): waechst aus der Mitte nach beiden Seiten, wie
 * die Arme beim Oeffnen, mit einer Ziellinie je Seite. Ziel erreicht: die Pfeile zeigen zur Mitte,
 * die Haende wieder zusammen. Darueber mittig die Zahl.
 */
@Composable
internal fun SpreadBar(bar: BarUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CenterBarLabel(bar)
        HorizontalBarCanvas(bar, bar, Modifier.fillMaxWidth().height(56.dp))
    }
}

/**
 * Die Flaeche der waagerechten Balken: [left] fuellt von der Mitte nach links, [right] nach
 * rechts. back: ein Pfeil in der Haelfte zeigt zur Mitte zurueck.
 */
@Composable
private fun HorizontalBarCanvas(left: BarUi, right: BarUi, modifier: Modifier = Modifier) {
    val colors = DigiMobTheme.colors
    Canvas(modifier = modifier) {
        val middle = size.width / 2
        val half = size.width / 2
        for ((bar, direction) in listOf(left to -1, right to 1)) {
            fun at(fill: Double) = middle + direction * half * (minOf(fill, OVER_TARGET) / OVER_TARGET).toFloat()
            if (bar.confirm) {
                drawRect(colors.barConfirm, Offset(minOf(middle, at(OVER_TARGET)), 0f), Size(half, size.height))
            }
            bar.fill?.takeIf { it > 0 }?.let { fill ->
                drawRect(barColor(colors, fill), Offset(minOf(middle, at(fill)), 0f), Size(kotlin.math.abs(at(fill) - middle), size.height))
            }
            bar.peak?.let { peak ->
                drawLine(colors.barPeak, Offset(at(peak), 0f), Offset(at(peak), size.height), strokeWidth = 5.dp.toPx())
            }
            drawLine(colors.barLine, Offset(at(1.0), 0f), Offset(at(1.0), size.height), strokeWidth = 4.dp.toPx())
            if (bar.confirm) {
                drawCheck(colors, Offset(at(1.0) + direction * 30.dp.toPx(), size.height / 2), size.height * 0.9f)
            } else if (bar.back) {
                // Pfeil nach unten gedreht: links zeigt er nach rechts, rechts nach links (zur Mitte)
                drawArrow(colors, Offset((middle + at(1.0)) / 2, size.height / 2), size.height * 0.7f, direction * 90f)
            }
        }
        drawRect(colors.barLine, Offset.Zero, size, style = Stroke(1.dp.toPx()))
        drawLine(colors.barLine, Offset(middle, 0f), Offset(middle, size.height), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun BarCount(bar: BarUi) {
    Text(
        text = bar.count?.let { stringResource(if (bar.countOnly) R.string.word_counted else R.string.word_remaining) } ?: "",
        style = MaterialTheme.typography.bodyLarge
    )
    Text(
        text = bar.count?.toString() ?: "",
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CenterBarLabel(bar: BarUi, modifier: Modifier = Modifier) {
    val count = bar.count ?: return
    Text(
        text = stringResource(if (bar.countOnly) R.string.word_counted else R.string.word_remaining) + " $count",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** "noch 3" fuer die Bedienungshilfen, null ohne Zahl. */
@Composable
private fun barDescription(bar: BarUi): String? {
    val count = bar.count ?: return null
    return stringResource(
        R.string.bar_description,
        stringResource(if (bar.countOnly) R.string.word_counted else R.string.word_remaining),
        count
    )
}

/** Gelb (leer) -> Gruen (voll), ab dem Ziel Gruen; linear in RGB wie auf dem Desktop. */
private fun barColor(colors: DigiMobColors, fill: Double): Color {
    val f = fill.coerceIn(0.0, 1.0).toFloat()
    val a = colors.barEmpty
    val b = colors.barFull
    return Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)
}

// Haken und Pfeil als Formen mit weissem Rand: auf Gruen und Gelb sichtbar
private fun DrawScope.drawCheck(colors: DigiMobColors, center: Offset, size: Float) {
    val path = Path().apply {
        moveTo(center.x - size / 2, center.y)
        lineTo(center.x - size / 6, center.y + size * 0.35f)
        lineTo(center.x + size / 2, center.y - size * 0.35f)
    }
    drawPath(path, Color.White, style = Stroke(maxOf(8f, size / 4), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, colors.barCheck, style = Stroke(maxOf(4f, size / 7), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pfeil nach unten, um [degrees] im Uhrzeigersinn um [center] gedreht (90 = nach links). */
private fun DrawScope.drawArrow(colors: DigiMobColors, center: Offset, size: Float, degrees: Float = 0f) {
    val s = size / 2
    val shaft = size / 6
    // Schaft und Spitze um center herum: so dreht sich der Pfeil auf der Stelle
    val top = center.y - s * 1.35f
    val path = Path().apply {
        moveTo(center.x - shaft, top)
        lineTo(center.x + shaft, top)
        lineTo(center.x + shaft, top + s * 1.6f)
        lineTo(center.x + s, top + s * 1.6f)
        lineTo(center.x, top + s * 2.7f)
        lineTo(center.x - s, top + s * 1.6f)
        lineTo(center.x - shaft, top + s * 1.6f)
        close()
    }
    rotate(degrees, pivot = center) {
        drawPath(path, colors.barArrow)
        drawPath(path, Color.White, style = Stroke(3f))
    }
}

@Composable
private fun VerticalBarPreviewBox(bar: BarUi) {
    DigiMobTheme {
        Box(modifier = Modifier.background(DigiMobTheme.colors.barBackground).width(130.dp).height(600.dp)) {
            VerticalBar(bar = bar, modifier = Modifier.fillMaxHeight())
        }
    }
}

@Preview(name = "Leer (Einfuehrung)")
@Composable
private fun VerticalBarEmptyPreview() = VerticalBarPreviewBox(BarUi())

@Preview(name = "Halb, noch 4")
@Preview(name = "Halb dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VerticalBarHalfPreview() = VerticalBarPreviewBox(BarUi(fill = 0.5, count = 4))

@Preview(name = "Ueber dem Ziel, Pfeil zurueck")
@Composable
private fun VerticalBarBackPreview() = VerticalBarPreviewBox(BarUi(fill = 1.2, back = true, count = 4))

@Preview(name = "Gezaehlt, Haken")
@Composable
private fun VerticalBarConfirmPreview() = VerticalBarPreviewBox(BarUi(fill = 0.3, confirm = true, count = 3))

@Preview(name = "Hoechster Punkt")
@Composable
private fun VerticalBarPeakPreview() = VerticalBarPreviewBox(BarUi(fill = 0.4, peak = 0.9, count = 3))

@Preview(name = "Ohne Ziel: gezaehlt")
@Composable
private fun VerticalBarCountOnlyPreview() = VerticalBarPreviewBox(BarUi(fill = 0.7, count = 12, countOnly = true))

@Preview(name = "Kopfdrehung links, rechts gezaehlt", widthDp = 900)
@Preview(name = "Kopfdrehung dunkel", widthDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CenterBarPreview() {
    DigiMobTheme {
        Row(modifier = Modifier.background(DigiMobTheme.colors.barBackground)) {
            CenterBar(
                left = BarUi(fill = 0.7, peak = 1.0, count = 3),
                right = BarUi(fill = 0.0, confirm = true, count = 2),
                modifier = Modifier.fillMaxWidth().height(90.dp)
            )
        }
    }
}

@Preview(name = "Kopfdrehung leer", widthDp = 900)
@Composable
private fun CenterBarEmptyPreview() {
    DigiMobTheme {
        CenterBar(left = BarUi(), right = BarUi(), modifier = Modifier.fillMaxWidth().height(90.dp))
    }
}

@Preview(name = "Butterfly offen ueber dem Ziel, Pfeile zur Mitte", widthDp = 800)
@Preview(name = "Butterfly dunkel", widthDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SpreadBarBackPreview() {
    DigiMobTheme {
        SpreadBar(
            bar = BarUi(fill = 1.15, back = true, count = 4),
            modifier = Modifier.background(DigiMobTheme.colors.barBackground).fillMaxWidth()
        )
    }
}

@Preview(name = "Butterfly halb, hoechster Punkt", widthDp = 800)
@Composable
private fun SpreadBarHalfPreview() {
    DigiMobTheme {
        SpreadBar(
            bar = BarUi(fill = 0.5, peak = 0.8, count = 3),
            modifier = Modifier.background(DigiMobTheme.colors.barBackground).fillMaxWidth()
        )
    }
}
