package com.opendarts.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val tips = listOf(
    "Put the phone on a stand or shelf beside the board, a little to one side and level with it, so it looks across the board without the thrower getting in the way. Any angle from straight-on up to about 70\u00B0 to the side works.",
    "Stand the phone 1-2 m from the board, with the whole board in view and a little space around it.",
    "Even light with no glare or shadows across the board works best.",
    "Start with the board clear of darts and keep the phone still while you play.",
    "A dart read wrongly? Open the menu and use Undo, then switch to the keypad and tap the right score.",
)

@Composable
fun SetupTipsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera setup tips") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tips.forEach { Text("\u2022 $it") } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}