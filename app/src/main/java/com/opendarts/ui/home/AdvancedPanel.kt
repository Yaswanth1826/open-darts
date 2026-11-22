package com.opendarts.ui.home

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.session.AdvancedStats
import com.opendarts.session.MatchRecord
import com.opendarts.session.advancedStats
import com.opendarts.session.toCsv
import com.opendarts.ui.common.OptionChip
import com.opendarts.ui.common.OutlineButton
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal
import java.util.Locale
import kotlin.math.sqrt

/** The premium part of the Stats tab: a heatmap, finishing per double, the first nine darts and the trend. Locked for free players. */
@Composable
fun AdvancedPanel(records: List<MatchRecord>, players: List<String>, premium: Boolean, onUpgrade: () -> Unit) {
    SectionLabel("Advanced stats", Modifier.padding(top = 6.dp))
    if (!premium) {
        Column(Modifier.fillMaxWidth().panel(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PREMIUM", color = Pal.Lime, fontFamily = Display, fontSize = 12.sp, letterSpacing = 2.sp)
            Text("See where your darts really go", color = Pal.Text, fontFamily = Display, fontSize = 18.sp)
            Text("A board heatmap, finishing per double, your first nine darts, an average trend and a spreadsheet export. Your basic stats stay free.", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
            PrimaryButton("Unlock", onUpgrade, Modifier.fillMaxWidth(), height = 48.dp)
        }
        return
    }
    val withDarts = remember(records, players) { players.mapNotNull { name -> records.advancedStats(name) } }
    if (withDarts.isEmpty()) {
        Column(Modifier.fillMaxWidth().panel(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nothing to show yet", color = Pal.Text, fontFamily = Display, fontSize = 18.sp)
            Text("These figures are built from the darts of games you finish from now on.", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
        }
        return
    }
    var picked by remember { mutableStateOf(withDarts.first().name) }
    val stats = withDarts.firstOrNull { it.name == picked } ?: withDarts.first()
    if (withDarts.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        withDarts.take(4).forEach { OptionChip(it.name, it.name == stats.name, { picked = it.name }) }
    }
    AdvancedCard(stats)
    val context = LocalContext.current
    OutlineButton("Export all games as CSV", {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Open Darts games").putExtra(Intent.EXTRA_TEXT, records.toCsv())
        context.startActivity(Intent.createChooser(send, "Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }, Modifier.fillMaxWidth(), height = 48.dp)
}

@Composable
fun AdvancedCard(s: AdvancedStats) {
    Column(Modifier.fillMaxWidth().panel(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fig("First 9", s.first9?.let { "%.1f".format(Locale.US, it) } ?: "-", Modifier.weight(1f))
            Fig("Darts", s.darts.toString(), Modifier.weight(1f))
            Fig("Trebles", s.trebles.toString(), Modifier.weight(1f))
            Fig("Doubles", s.doubleHits.toString(), Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("Where your darts land")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Heatmap(s.heat, Modifier.size(280.dp)) }
            Text("Brighter means more darts. Singles, trebles and doubles are compared among themselves.", color = Pal.Muted, fontFamily = Body, fontSize = 12.sp)
        }
        if (s.doubles.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("Finishing per double")
            s.doubles.take(8).forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(d.label, color = Pal.Text, fontFamily = Display, fontSize = 14.sp, modifier = Modifier.size(width = 44.dp, height = 20.dp))
                    Box(Modifier.weight(1f).height(8.dp).panel(4.dp, colour = Pal.Raised, edge = Pal.Raised)) {
                        Box(Modifier.fillMaxWidth((d.percent / 100f).coerceAtLeast(0.03f)).height(8.dp).panel(4.dp, colour = Pal.Lime, edge = Pal.Lime))
                    }
                    Text("${d.hits}/${d.attempts} \u00B7 ${d.percent}%", color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.size(width = 84.dp, height = 18.dp))
                }
            }
            val weak = s.weakestDoubles().take(3)
            if (weak.isNotEmpty()) Text("Weakest: " + weak.joinToString(", ") { "${it.label} ${it.percent}%" }, color = Pal.Muted, fontFamily = Body, fontSize = 12.sp)
        }
        if (s.trend.size >= 2) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("X01 average, last ${s.trend.size} games")
            Trend(s.trend, Modifier.fillMaxWidth().height(90.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Low %.0f".format(Locale.US, s.trend.min()), color = Pal.Muted, fontFamily = Body, fontSize = 12.sp)
                Text("High %.0f".format(Locale.US, s.trend.max()), color = Pal.Muted, fontFamily = Body, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, color = Pal.Text, fontFamily = Display, fontSize = 15.sp)

@Composable
private fun Fig(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Pal.Lime, fontFamily = Display, fontSize = 20.sp, maxLines = 1)
        Text(label, color = Pal.Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Trend(values: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        val low = values.min(); val high = values.max()
        val span = (high - low).coerceAtLeast(1.0)
        val pad = 8.dp.toPx()
        fun point(i: Int) = Offset(pad + (size.width - 2 * pad) * i / (values.size - 1), pad + (size.height - 2 * pad) * (1f - ((values[i] - low) / span).toFloat()))
        val path = Path().apply { values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
        drawPath(path, Pal.Lime, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        values.indices.forEach { drawCircle(Pal.Lime, 3.dp.toPx(), point(it)) }
    }
}

/** The board with every ring of every number coloured by how many darts landed there. */
@Composable
private fun Heatmap(heat: Map<DartHit, Int>, modifier: Modifier) {
    val singleMax = heat.filterKeys { it.multiplier == Multiplier.SINGLE && it.base != 0 }.values.maxOrNull() ?: 1
    val trebleMax = heat.filterKeys { it.isTreble }.values.maxOrNull() ?: 1
    val doubleMax = heat.filterKeys { it.isDouble }.values.maxOrNull() ?: 1
    fun colour(count: Int, max: Int) = lerp(Color(0xFF222A35), Pal.Lime, if (count == 0) 0f else (0.12f + 0.88f * sqrt(count.toFloat() / max)))
    Canvas(modifier) {
        val mm = size.minDimension / 2f / (BoardGeometry.DOUBLE_OUTER_RADIUS.toFloat() + 2f)
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun ring(from: Double, to: Double, i: Int, tint: Color) {
            val gap = 1.2
            val inner = from + gap; val outer = to - gap
            val mid = ((inner + outer) / 2).toFloat() * mm
            drawArc(
                tint, startAngle = -90f + i * 18f - 8.6f, sweepAngle = 17.2f, useCenter = false,
                topLeft = Offset(centre.x - mid, centre.y - mid), size = Size(mid * 2, mid * 2), style = Stroke(width = ((outer - inner) * mm).toFloat()),
            )
        }
        BoardGeometry.SEGMENT_ORDER.forEachIndexed { i, n ->
            val single = colour(heat[DartHit.single(n)] ?: 0, singleMax)
            ring(BoardGeometry.OUTER_BULL_RADIUS, BoardGeometry.TREBLE_INNER_RADIUS, i, single)
            ring(BoardGeometry.TREBLE_INNER_RADIUS, BoardGeometry.TREBLE_OUTER_RADIUS, i, colour(heat[DartHit.treble(n)] ?: 0, trebleMax))
            ring(BoardGeometry.TREBLE_OUTER_RADIUS, BoardGeometry.DOUBLE_INNER_RADIUS, i, single)
            ring(BoardGeometry.DOUBLE_INNER_RADIUS, BoardGeometry.DOUBLE_OUTER_RADIUS, i, colour(heat[DartHit.double(n)] ?: 0, doubleMax))
        }
        drawCircle(colour(heat[DartHit.OUTER_BULL] ?: 0, singleMax), (BoardGeometry.OUTER_BULL_RADIUS.toFloat() - 1.2f) * mm, centre)
        drawCircle(colour(heat[DartHit.INNER_BULL] ?: 0, doubleMax), (6.35f - 1.2f) * mm, centre)
    }
}
