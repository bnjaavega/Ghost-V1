package com.ghost.editor.ui.screens

import android.app.Application
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.editor.data.RecentFile
import com.ghost.editor.ui.components.BAR_HEIGHT
import com.ghost.editor.ui.components.FloatingNavBar
import com.ghost.editor.ui.components.NameDialog
import com.ghost.editor.ui.components.NavTab
import com.ghost.editor.ui.nav.navBarBottomPadding
import com.ghost.editor.ui.theme.GhostColors
import com.ghost.editor.viewmodel.FolderViewModel
import com.ghost.editor.viewmodel.HomeViewModel
import com.ghost.editor.viewmodel.NewFileViewModel

/** Contenedor de las pestañas principales con la navegación inferior flotante. */
@Composable
fun MainScreen(
    onOpenProject: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onSettings: () -> Unit,
    onPractice: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app))
    val rootVm: FolderViewModel = viewModel(key = "folder:", factory = FolderViewModel.Factory(app, ""))
    val newFileVm: NewFileViewModel = viewModel(factory = NewFileViewModel.Factory(app))

    // Pestaña cuyo contenido se muestra. "Archivos" cambia la selección de la barra
    // pero no el contenido: es un espacio reservado para una función futura.
    var contentTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var navSelected by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }

    // Altura de la barra flotante + márgenes + inset del sistema.
    val bottomPadding = BAR_HEIGHT + 24.dp + 16.dp + navBarBottomPadding()

    Box(Modifier.fillMaxSize().background(GhostColors.Bg)) {
        Crossfade(targetState = contentTab, animationSpec = tween(220), label = "tabs") { tab ->
            when (tab) {
                NavTab.PROJECTS -> FolderScreen(
                    vm = rootVm,
                    title = "Proyectos",
                    bottomPadding = bottomPadding,
                    onBack = null,
                    onOpenFolder = onOpenProject,
                    onOpenFile = onOpenFile,
                    mainHeader = MainHeaderActions(
                        onSettings = onSettings,
                        onPractice = onPractice,
                        onNewProject = { showNewProject = true },
                        onOpenExternal = onOpenExternal
                    )
                )

                else -> HomeScreen(
                    vm = homeVm,
                    bottomPadding = bottomPadding,
                    onOpenProject = onOpenProject,
                    onOpenRecent = onOpenRecent,
                    onSettings = onSettings,
                    onPractice = onPractice,
                    onOpenExternal = onOpenExternal,
                    onNewProject = { showNewProject = true }
                )
            }
        }

        FloatingNavBar(
            selected = navSelected,
            onSelect = { tab ->
                navSelected = tab
                when (tab) {
                    NavTab.HOME -> contentTab = NavTab.HOME
                    NavTab.PROJECTS -> contentTab = NavTab.PROJECTS
                    NavTab.FILES -> Unit // reservado: sin pantalla ni función por ahora
                }
            },
            onAdd = { showNewFile = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showNewFile) {
        NewFileSheet(
            vm = newFileVm,
            onDismiss = { showNewFile = false },
            onCreated = { rel ->
                showNewFile = false
                onOpenFile(rel)
            }
        )
    }

    if (showNewProject) {
        NameDialog(
            title = "Nuevo proyecto",
            label = "Nombre del proyecto",
            confirmLabel = "Crear",
            onConfirm = { name ->
                showNewProject = false
                homeVm.createProject(name) { rel -> onOpenProject(rel) }
            },
            onDismiss = { showNewProject = false }
        )
    }
}
