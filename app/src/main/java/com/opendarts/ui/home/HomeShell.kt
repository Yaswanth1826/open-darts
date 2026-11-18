package com.opendarts.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Pal

enum class Tab(val label: String, val icon: ImageVector) {
    PLAY("Play", Icons.Default.PlayArrow),
    STATS("Stats", Icons.Default.BarChart),
    PLAYERS("Players", Icons.Default.Group),
    SETTINGS("Settings", Icons.Default.Settings),
}

/** The top-level frame: the page content with a bottom bar in portrait, or a side rail in landscape where height is scarce. */
@Composable
fun HomeShell(tab: Tab, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    ScreenBox {
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxHeight().width(92.dp).background(Pal.Surface).padding(vertical = 12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Tab.values().forEach { NavItem(it, it == tab) { onTab(it) } }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                Row(Modifier.fillMaxWidth().background(Pal.Surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tab.values().forEach { NavItem(it, it == tab, Modifier.weight(1f)) { onTab(it) } }
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Pal.Lime.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp)) {
            Icon(tab.icon, null, tint = if (selected) Pal.Lime else Pal.Muted)
        }
        Text(tab.label, color = if (selected) Pal.Text else Pal.Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
    }
}
