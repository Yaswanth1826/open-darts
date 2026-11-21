package com.opendarts.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.session.GameKind
import com.opendarts.ui.common.DartIcon
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** The Play tab: pick up a game in progress, start one fast, or choose a game mode. */
@Composable
fun PlayScreen(onMode: (GameKind) -> Unit, onQuick: () -> Unit, onResume: (() -> Unit)?, resumeDetail: String) {
    var tips by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    val top: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("OPEN DARTS", color = Pal.Lime, fontFamily = Display, fontSize = 13.sp, letterSpacing = 3.sp)
                Text("Let's throw.", color = Pal.Text, fontFamily = Display, fontSize = 34.sp)
            }
            if (onResume != null) ResumeCard(resumeDetail.ifBlank { "Pick up the game in progress." }, onResume)
            QuickCard(onQuick)
        }
    }
    val modes: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Game modes")
            ModeRow("X01", "301, 501, 701 or your own score. Count down to zero.", Icons.Default.Adjust, Pal.Lime) { onMode(GameKind.X01) }
            ModeRow("Cricket", "Close 15 to 20 and the bull. Standard, cut-throat or no-score.", Icons.Default.Grid3x3, Pal.Sky) { onMode(GameKind.CRICKET) }
            ModeRow("Practice", "Bob's 27, checkout practice, Around the Clock, Count Up and more.", Icons.Default.Timer, Pal.Amber) { onMode(GameKind.AROUND_THE_CLOCK) }
            ModeRow("Party", "Shanghai, Killer, Halve It and Baseball.", Icons.Default.Celebration, Pal.Violet) { onMode(GameKind.SHANGHAI) }
            TipsRow { tips = true }
        }
    }

    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { top() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { modes() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            top()
            modes()
        }
    }
    if (tips) SetupTipsDialog { tips = false }
}

@Composable
private fun ResumeCard(detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().panel(22.dp, Pal.Raised, Pal.Lime).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Resume game", color = Pal.Lime, fontFamily = Display, fontSize = 20.sp)
            Text(detail, color = Pal.Muted, fontFamily = Body, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Pal.Lime, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun QuickCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFC6F24E), Color(0xFF7FDB8A)))).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Quick game", color = Pal.OnLime, fontFamily = Display, fontSize = 26.sp)
            Text("501 scored by the camera", color = Pal.OnLime.copy(alpha = 0.75f), fontFamily = Body, fontSize = 15.sp)
        }
        DartIcon(Pal.OnLime, width = 84.dp, steel = Pal.OnLime.copy(alpha = 0.55f))
    }
}

@Composable
private fun ModeRow(title: String, note: String, icon: ImageVector, accent: Color, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    Row(
        Modifier.fillMaxWidth().panel(20.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = if (enabled) 0.16f else 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent.copy(alpha = if (enabled) 1f else 0.5f))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text.copy(alpha = if (enabled) 1f else 0.5f), fontFamily = Display, fontSize = 19.sp)
            Text(note, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Pal.Muted)
        else Text("SOON", color = Pal.Muted, fontFamily = Display, fontSize = 11.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun TipsRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Info, null, tint = Pal.Muted)
        Text("Camera setup tips", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
    }
}
