package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.Palette

/**
 * What to do with one dart slot: for a dart that is there, mark it a miss, remove it or pick another score;
 * for the next empty slot, enter a dart by hand. [onPick] gets the new score, or null to remove the dart.
 * A dart the camera could not see because it sits behind another one is added with [onBehind] (index of the front dart);
 * when the front dart is removed, [onLookBehind] asks the camera to score whatever dart is still visible there.
 */
@Composable
fun DartEditor(
    slot: Int,
    current: DartHit?,
    front: List<DartHit>,
    canLookBehind: Boolean,
    onPick: (DartHit?) -> Unit,
    onBehind: (Int) -> Unit,
    onLookBehind: () -> Unit,
    onDismiss: () -> Unit,
) {
    var choosing by remember { mutableStateOf(current == null) }
    var multiplier by remember { mutableStateOf(current?.multiplier?.takeIf { current.base in 1..20 } ?: Multiplier.SINGLE) }
    val done: (DartHit?) -> Unit = { onPick(it); onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Dart ${slot + 1}: enter score" else "Dart ${slot + 1}: $current") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!choosing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text("Change score") }
                    OutlinedButton(onClick = { done(DartHit.MISS) }, modifier = Modifier.fillMaxWidth()) { Text("Make it a miss") }
                    OutlinedButton(onClick = { done(null) }, modifier = Modifier.fillMaxWidth()) { Text("Remove this dart") }
                    if (canLookBehind) OutlinedButton(onClick = { onLookBehind(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Remove \u2013 a dart is behind it") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current == null && front.isNotEmpty()) {
                        Text("Hidden behind a dart?", color = Palette.Ink.copy(alpha = 0.7f), fontFamily = Condensed, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            front.forEachIndexed { i, hit ->
                                Box(
                                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.KeyDark).clickable { onBehind(i); onDismiss() },
                                    contentAlignment = Alignment.Center,
                                ) { Text("Dart ${i + 1}: $hit", color = Palette.Ink, fontFamily = Condensed, fontSize = 17.sp, maxLines = 1) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Multiplier.values().forEach { m ->
                            val selected = m == multiplier
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Palette.Red else Palette.Sand).clickable { multiplier = m },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (selected) androidx.compose.ui.graphics.Color.White else Palette.Ink, fontFamily = Condensed, fontSize = 18.sp)
                            }
                        }
                    }
                    (1..20).chunked(5).forEach { numbers ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            numbers.forEach { n -> NumberKey(n.toString(), Modifier.weight(1f)) { done(DartHit(n, multiplier)) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberKey("Miss", Modifier.weight(1f)) { done(DartHit.MISS) }
                        NumberKey("25", Modifier.weight(1f)) { done(DartHit.OUTER_BULL) }
                        NumberKey("Bull", Modifier.weight(1f)) { done(DartHit.INNER_BULL) }
                    }
                }
            }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(42.dp).clip(RoundedCornerShape(10.dp)).background(Palette.KeyLight).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Palette.Ink, fontFamily = Condensed, fontSize = 20.sp)
    }
}
