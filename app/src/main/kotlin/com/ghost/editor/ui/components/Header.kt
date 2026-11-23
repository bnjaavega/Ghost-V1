package com.ghost.editor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghost.editor.ui.theme.GhostColors

/** Encabezado principal: logotipo + nombre, ajustes y menú de tres puntos (con Práctica). */
@Composable
fun GhostHeader(
    onSettings: () -> Unit,
    onPractice: () -> Unit,
    onNewProject: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostLogo(height = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Ghost",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
        GhostIconButton(Icons.Outlined.Settings, "Ajustes", onSettings)
        Box {
            GhostIconButton(Icons.Outlined.MoreVert, "Más opciones", { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = GhostColors.Card2
            ) {
                MenuItem("Práctica", Icons.Outlined.School) { menuOpen = false; onPractice() }
                MenuItem("Nuevo proyecto", Icons.Outlined.CreateNewFolder) { menuOpen = false; onNewProject() }
                MenuItem("Abrir archivo .py", Icons.Outlined.FileOpen) { menuOpen = false; onOpenExternal() }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, color = Color.White) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

/** Encabezado simple para pantallas secundarias: flecha atrás + título. */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GhostIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
