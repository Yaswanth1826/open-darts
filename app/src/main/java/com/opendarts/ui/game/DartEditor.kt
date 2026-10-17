package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.Palette

/**
 * What to do with one dart slot: for a dart that is there, mark it a miss, remove it or pick another score;
 * for the next empty slot, enter a dart by hand. [onPick] gets the new score, or null to remove the dart.
 * A dart the camera could not see because it sits behind another one is added with [onBehind] (index of the front dart);
 * when the front dart is removed, [onLookBehind] asks the camera to score whatever dart is still visible there.
 *
 * The score pad adapts to the room: on a wide screen all twenty numbers sit in two rows beside the multipliers, so nothing scrolls.
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
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 560
    val key = if (configuration.screenHeightDp < 500) 36.dp else 44.dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp), color = Palette.KeyLight,
            modifier = Modifier.padding(8.dp).widthIn(max = if (wide) 660.dp else 380.dp).fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.94f).dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (current == null) "Dart ${slot + 1}: enter score" else "Dart ${slot + 1}: $current",
                        color = Palette.Ink, fontFamily = Condensed, fontSize = 22.sp, modifier = Modifier.weight(1f),
                    )
                    Text("CANCEL", color = Palette.Ink.copy(alpha = 0.6f), fontFamily = Condensed, fontSize = 16.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(8.dp))
                }
                if (!choosing) {
                    val actions = buildList<Pair<String, () -> Unit>> {
                        add("Change score" to { choosing = true })
                        add("Make it a miss" to { done(DartHit.MISS) })
                        add("Remove this dart" to { done(null) })
                        if (canLookBehind) add("Remove \u2013 a dart is behind it" to { onLookBehind(); onDismiss() })
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions.forEachIndexed { i, (label, run) -> Action(label, i == 0, Modifier.weight(1f), run) } }
                    } else {
                        actions.forEachIndexed { i, (label, run) -> Action(label, i == 0, Modifier.fillMaxWidth(), run) }
                    }
                } else {
                    if (current == null && front.isNotEmpty()) {
                        Text("Hidden behind a dart?", color = Palette.Ink.copy(alpha = 0.7f), fontFamily = Condensed, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            front.forEachIndexed { i, hit -> Key("Dart ${i + 1}: $hit", Modifier.weight(1f), key, Palette.KeyDark, 17) { onBehind(i); onDismiss() } }
                        }
                    }
                    val multipliers: @Composable (Modifier) -> Unit = { modifier ->
                        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Multiplier.values().forEach { m ->
                                val selected = m == multiplier
                                Key(m.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f), key, if (selected) Palette.Red else Palette.KeyMid, 18, if (selected) Color.White else Palette.Ink) { multiplier = m }
                            }
                        }
                    }
                    val specials: @Composable (Modifier) -> Unit = { modifier ->
                        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Key("Miss", Modifier.weight(1f), key, Palette.KeyMid) { done(DartHit.MISS) }
                            Key("25", Modifier.weight(1f), key, Palette.KeyMid) { done(DartHit.OUTER_BULL) }
                            Key("Bull", Modifier.weight(1f), key, Palette.KeyMid) { done(DartHit.INNER_BULL) }
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { multipliers(Modifier.weight(1f)); specials(Modifier.weight(1f)) }
                    } else {
                        multipliers(Modifier)
                    }
                    (1..20).chunked(if (wide) 10 else 5).forEach { numbers ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            numbers.forEach { n -> Key(n.toString(), Modifier.weight(1f), key, Palette.KeyDark.copy(alpha = 0.55f)) { done(DartHit(n, multiplier)) } }
                        }
                    }
                    if (!wide) specials(Modifier)
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (primary) Palette.Red else Palette.KeyMid).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (primary) Color.White else Palette.Ink, fontFamily = Condensed, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 18.sp) }
}

@Composable
private fun Key(label: String, modifier: Modifier, height: androidx.compose.ui.unit.Dp, tint: Color, size: Int = 20, ink: Color = Palette.Ink, onClick: () -> Unit) {
    Box(modifier.height(height).clip(RoundedCornerShape(10.dp)).background(tint).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = ink, fontFamily = Condensed, fontSize = size.sp, maxLines = 1)
    }
}
