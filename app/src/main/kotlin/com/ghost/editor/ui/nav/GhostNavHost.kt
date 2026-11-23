package com.ghost.editor.ui.nav

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ghost.editor.container
import com.ghost.editor.data.FileRef
import com.ghost.editor.data.RecentFile
import com.ghost.editor.ui.screens.EditorScreen
import com.ghost.editor.ui.screens.FolderScreen
import com.ghost.editor.ui.screens.MainScreen
import com.ghost.editor.ui.screens.PracticeScreen
import com.ghost.editor.ui.screens.SettingsScreen
import com.ghost.editor.viewmodel.EditorViewModel
import com.ghost.editor.viewmodel.FolderViewModel
import com.ghost.editor.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val PRACTICE = "practice"
    const val SETTINGS = "settings"
    const val FOLDER = "folder?path={path}"
    const val EDITOR = "editor?ref={ref}"

    fun folder(path: String) = "folder?path=" + Uri.encode(path)
    fun editor(ref: FileRef) = "editor?ref=" + Uri.encode(ref.encode())
}

/** Selector nativo de archivos (Storage Access Framework), empezando en Descargas. */
class OpenPythonDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
} catch (_: Exception) {
    null
}

private fun persistPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Algunos proveedores solo conceden lectura; el guardado informará si falla.
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }
}

@Composable
fun navBarBottomPadding(extra: Dp = 0.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

@Composable
fun GhostNavHost(externalUri: Uri?, onExternalConsumed: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val container = context.container

    fun openInternal(rel: String) = nav.navigate(Routes.editor(FileRef.Internal(rel)))

    val openExternal: (Uri, Boolean) -> Unit = { uri, requirePy ->
        val name = displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        if (requirePy && !name.endsWith(".py", ignoreCase = true)) {
            Toast.makeText(context, "Selecciona un archivo .py", Toast.LENGTH_SHORT).show()
        } else {
            persistPermission(context, uri)
            nav.navigate(Routes.editor(FileRef.External(uri)))
        }
    }

    val picker = rememberLauncherForActivityResult(OpenPythonDocument()) { uri ->
        if (uri != null) openExternal(uri, true)
    }

    // Archivo .py abierto desde otra app (intent VIEW).
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            openExternal(externalUri, false)
            onExternalConsumed()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 10 } }
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenProject = { nav.navigate(Routes.folder(it)) },
                onOpenFile = { openInternal(it) },
                onOpenRecent = { recent: RecentFile -> openExternal(Uri.parse(recent.uri), false) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onPractice = { nav.navigate(Routes.PRACTICE) },
                onOpenExternal = { picker.launch(Unit) }
            )
        }

        composable(
            Routes.FOLDER,
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            val vm: FolderViewModel = viewModel(key = "folder:$path", factory = FolderViewModel.Factory(app, path))
            FolderScreen(
                vm = vm,
                title = path.substringAfterLast('/'),
                bottomPadding = navBarBottomPadding(24.dp),
                onBack = { nav.popBackStack() },
                onOpenFolder = { nav.navigate(Routes.folder(it)) },
                onOpenFile = { openInternal(it) }
            )
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("ref") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val encoded = entry.arguments?.getString("ref").orEmpty()
            val ref = FileRef.decode(encoded)
            if (ref != null) {
                val vm: EditorViewModel = viewModel(key = "editor:$encoded", factory = EditorViewModel.Factory(app, ref))
                EditorScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }

        composable(Routes.PRACTICE) {
            PracticeScreen(
                onBack = { nav.popBackStack() },
                onTry = { topic ->
                    scope.launch {
                        // Crea el ejemplo en Ghost Proyectos/Práctica y lo abre; si ya existe, lo abre tal cual.
                        val rel = container.projects
                            .createPythonFile("Práctica", topic.fileName, topic.code)
                            .getOrNull() ?: "Práctica/${topic.fileName}"
                        openInternal(rel)
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                vm = vm,
                projectsPath = container.projects.root.absolutePath,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
