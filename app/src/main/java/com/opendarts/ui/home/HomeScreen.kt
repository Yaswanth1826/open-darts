package com.opendarts.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.session.Category
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette

@Composable
fun HomeScreen(onCategory: (Category) -> Unit, onQuick: () -> Unit, onResume: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    var tips by remember { mutableStateOf(false) }
    ScreenBox(Palette.Night) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Camera setup tips") }, onClick = { menu = false; tips = true })
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("OPEN DARTS", color = Color.White, fontFamily = Condensed, fontSize = 26.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Tile("X01", Brush.linearGradient(listOf(Palette.Red, Color(0xFF9E2423))), Modifier.weight(1f)) { onCategory(Category.X01) }
                Tile("CRICKET", Brush.linearGradient(listOf(Palette.Charcoal, Color(0xFF2B2B2B))), Modifier.weight(1f)) { onCategory(Category.CRICKET) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Tile("PRACTICE", Brush.linearGradient(listOf(Palette.Amber, Palette.Orange)), Modifier.weight(1f)) { onCategory(Category.PRACTICE) }
                Tile("PARTY", Brush.linearGradient(listOf(Palette.Teal, Color(0xFF2F7FD1), Color(0xFFE9C546))), Modifier.weight(1f)) { onCategory(Category.PARTY) }
            }
            if (onResume != null) WideCard("RESUME GAME", "Pick up the game in progress.", highlight = true, onClick = onResume)
            WideCard("QUICK GAME", "501 scored by the camera, two players.", onClick = onQuick)
            WideCard("CAMERA SETUP TIPS", "Place the phone so the AI reads every dart.", onClick = { tips = true })
            Text(
                "Free and offline. No ads, no paywall.", color = Color.White.copy(alpha = 0.45f), fontFamily = CondensedRegular,
                fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
    if (tips) SetupTipsDialog { tips = false }
}

@Composable
private fun Tile(label: String, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(brush).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width * 0.95f, size.height)
            listOf(0.9f, 0.65f, 0.4f).forEach { r ->
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * r, center = centre, style = Stroke(width = size.width * 0.07f))
            }
        }
        Text(label, color = Color.White, fontFamily = Condensed, fontSize = if (label.length > 5) 30.sp else 44.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun WideCard(title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (highlight) Palette.Teal.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(title, color = Color.White, fontFamily = Condensed, fontSize = 26.sp, letterSpacing = 1.sp)
        Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 16.sp)
    }
}
