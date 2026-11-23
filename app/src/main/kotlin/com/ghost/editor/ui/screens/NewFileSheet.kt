package com.ghost.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ghost.editor.data.ProjectRepository
import com.ghost.editor.ui.components.ghostFieldColors
import com.ghost.editor.ui.theme.GhostColors
import com.ghost.editor.viewmodel.NewFileViewModel

private const val NEW_PROJECT = "\u0000new_project"

/**
 * Ventana del botón +: crea un archivo Python nuevo, con nombre a elegir y ubicación
 * dentro de Ghost Proyectos (carpeta existente, raíz o un proyecto nuevo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFileSheet(
    vm: NewFileViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fileName by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<String?>(null) } // null = aún sin decidir
    var newProject by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadFolders() }
    LaunchedEffect(vm.folders) {
        if (destination == null) destination = vm.folders.firstOrNull() ?: NEW_PROJECT
    }

    val dest = destination ?: NEW_PROJECT
    val creatingProject = dest == NEW_PROJECT

    fun submit() {
        vm.clearError()
        vm.create(
            destination = if (creatingProject) "" else dest,
            newProject = if (creatingProject) newProject else "",
            fileName = fileName,
            onCreated = onCreated
        )
    }

    val canCreate = fileName.isNotBlank() && (!creatingProject || newProject.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GhostColors.Card1,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Nuevo archivo Python", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it; vm.clearError() },
                label = { Text("Nombre del archivo (ej. main.py)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = ghostFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) submit() })
            )

            Column {
                Text(
                    "Ubicación en ${ProjectRepository.ROOT_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GhostColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GhostColors.Card2)
                            .clickable { menuOpen = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(
                            text = locationLabel(dest),
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                            maxLines = 1
                        )
                        Icon(Icons.Outlined.ExpandMore, null, tint = GhostColors.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = GhostColors.Card3
                    ) {
                        DropdownMenuItem(
                            text = { Text("＋ Nuevo proyecto…") },
                            onClick = { destination = NEW_PROJECT; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("${ProjectRepository.ROOT_NAME} (raíz)") },
                            onClick = { destination = ""; menuOpen = false }
                        )
                        vm.folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder) },
                                onClick = { destination = folder; menuOpen = false }
                            )
                        }
                    }
                }
            }

            if (creatingProject) {
                OutlinedTextField(
                    value = newProject,
                    onValueChange = { newProject = it; vm.clearError() },
                    label = { Text("Nombre del proyecto nuevo") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = ghostFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            vm.error?.let {
                Text(it, color = GhostColors.Danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { submit() },
                enabled = canCreate,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = GhostColors.Card3,
                    disabledContentColor = GhostColors.TextSecondary
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Crear y abrir")
            }
        }
    }
}

private fun locationLabel(dest: String): String = when (dest) {
    NEW_PROJECT -> "Nuevo proyecto…"
    "" -> "${ProjectRepository.ROOT_NAME} (raíz)"
    else -> dest
}
