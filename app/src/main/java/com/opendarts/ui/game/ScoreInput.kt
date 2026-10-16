package com.opendarts.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Point
import com.opendarts.ui.scoring.Dartboard

/** Manual score entry: tap the board or use the number pad. Always available, even in camera mode, to fix mistakes. */
@Composable
fun ScoreInput(marks: List<DartHit>, onHit: (DartHit) -> Unit) {
    var usePad by rememberSaveable { mutableStateOf(false) }
    var multiplier by rememberSaveable { mutableStateOf(Multiplier.SINGLE) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !usePad, onClick = { usePad = false }, label = { Text("Board") })
            FilterChip(selected = usePad, onClick = { usePad = true }, label = { Text("Number pad") })
        }
        if (!usePad) {
            val points: List<Point> = marks.map { BoardGeometry.centreOf(it) }
            Dartboard(Modifier.fillMaxWidth().padding(horizontal = 4.dp), marks = points, onHit = onHit)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Multiplier.values().forEach {
                    FilterChip(selected = multiplier == it, onClick = { multiplier = it }, label = { Text(when (it) { Multiplier.SINGLE -> "Single"; Multiplier.DOUBLE -> "Double"; Multiplier.TRIPLE -> "Treble" }) })
                }
            }
            (1..20).chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { n -> OutlinedButton(onClick = { onHit(DartHit(n, multiplier)) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(n.toString()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onHit(DartHit.MISS) }, modifier = Modifier.weight(1f)) { Text("Miss") }
                OutlinedButton(onClick = { onHit(DartHit.OUTER_BULL) }, modifier = Modifier.weight(1f)) { Text("25") }
                OutlinedButton(onClick = { onHit(DartHit.INNER_BULL) }, modifier = Modifier.weight(1f)) { Text("Bull") }
            }
        }
    }
}