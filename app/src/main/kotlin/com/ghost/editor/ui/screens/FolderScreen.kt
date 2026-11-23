package com.ghost.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghost.editor.data.Entry
import com.ghost.editor.ui.components.BackHeader
import com.ghost.editor.ui.components.ConfirmDialog
import com.ghost.editor.ui.components.GhostHeader
import com.ghost.editor.ui.components.GhostIconButton
import com.ghost.editor.ui.components.NameDialog
import com.ghost.editor.ui.components.ScreenTitle
import com.ghost.editor.ui.components.pressScale
import com.ghost.editor.ui.theme.GhostColors
import com.ghost.editor.util.formatSize
import com.ghost.editor.util.relativeDate
import com.ghost.editor.viewmodel.FolderViewModel

/**
 * Explorador de carpetas. Con [path] vacío es la pestaña "Proyectos" (raíz de Ghost Proyectos,
 * donde cada carpeta es un proyecto). Con una ruta es la vista de un proyecto o subcarpeta.
 */
@Composable
fun FolderScreen(
    vm: FolderViewModel,
    title: String,
    bottomPadding: Dp,
    onBack: (() -> Unit)?,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    mainHeader: MainHeaderActions? = null
) {
    val isRoot = vm.path.isEmpty()
    val snackbar = remember { SnackbarHostState() }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Entry?>(null) }
    var deleteTarget by remember { mutableStateOf<Entry?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (mainHeader != null) {
                GhostHeader(mainHeader.onSettings, mainHeader.onPractice, mainHeader.onNewProject, mainHeader.onOpenExternal)
            } else {
                BackHeader(title = title, onBack = onBack ?: {})
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column {
                        if (mainHeader != null) ScreenTitle(title, Modifier.padding(start = 4.dp, top = 8.dp))
                        Row(
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ActionChip(
                                label = if (isRoot) "Nuevo proyecto" else "Nueva carpeta",
                                icon = Icons.Outlined.CreateNewFolder,
                                onClick = { showNewFolder = true }
                            )
                            if (!isRoot) {
                                ActionChip("Nuevo archivo", Icons.Outlined.NoteAdd) { showNewFile = true }
                            }
                        }
                    }
                }

                items(vm.entries, key = { it.relPath }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = { if (entry.isDirectory) onOpenFolder(entry.relPath) else onOpenFile(entry.relPath) },
                        onRename = { renameTarget = entry },
                        onDelete = { deleteTarget = entry }
                    )
                }

                if (vm.loaded && vm.entries.isEmpty()) {
                    item {
                        Text(
                            if (isRoot) "No hay proyectos todavía." else "Esta carpeta está vacía.",
                            color = GhostColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding)
        )
    }

    if (showNewFile) {
        NameDialog(
            title = "Nuevo archivo Python",
            label = "Nombre (ej. main.py)",
            confirmLabel = "Crear",
            onConfirm = { name ->
                showNewFile = false
                vm.createFile(name) { rel -> onOpenFile(rel) }
            },
            onDismiss = { showNewFile = false }
        )
    }
    if (showNewFolder) {
        NameDialog(
            title = if (isRoot) "Nuevo proyecto" else "Nueva carpeta",
            label = "Nombre",
            confirmLabel = "Crear",
            onConfirm = { name -> showNewFolder = false; vm.createFolder(name) },
            onDismiss = { showNewFolder = false }
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "Renombrar",
            label = "Nuevo nombre",
            initial = target.name,
            onConfirm = { vm.rename(target.relPath, it); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = if (target.isDirectory) "Eliminar carpeta" else "Eliminar archivo",
            message = if (target.isDirectory) {
                "Se eliminará «${target.name}» con todo su contenido. Esta acción no se puede deshacer."
            } else {
                "Se eliminará «${target.name}». Esta acción no se puede deshacer."
            },
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = { vm.delete(target.relPath); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

/** Acciones del encabezado principal cuando FolderScreen se usa como pestaña "Proyectos". */
class MainHeaderActions(
    val onSettings: () -> Unit,
    val onPractice: () -> Unit,
    val onNewProject: () -> Unit,
    val onOpenExternal: () -> Unit
)

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(source)
            .clip(RoundedCornerShape(14.dp))
            .background(GhostColors.Card1)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val isPython = entry.name.endsWith(".py", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(GhostColors.Card1)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GhostColors.Card2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    entry.isDirectory -> Icons.Outlined.Folder
                    isPython -> Icons.Outlined.Code
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = if (entry.isDirectory) {
                "${entry.count} ${if (entry.count == 1L) "elemento" else "elementos"}"
            } else {
                formatSize(entry.count)
            }
            Text(
                "$detail · ${relativeDate(entry.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = GhostColors.TextSecondary,
                maxLines = 1
            )
        }
        Box {
            GhostIconButton(Icons.Outlined.MoreVert, "Opciones", { menu = true }, tint = GhostColors.TextSecondary, size = 36.dp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = GhostColors.Card2) {
                DropdownMenuItem(text = { Text("Renombrar", color = Color.White) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Eliminar", color = GhostColors.Danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
