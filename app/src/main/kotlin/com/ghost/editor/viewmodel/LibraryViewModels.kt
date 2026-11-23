package com.ghost.editor.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ghost.editor.container
import com.ghost.editor.data.Entry
import com.ghost.editor.data.RecentFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantalla de inicio: cuadrícula de proyectos + archivos externos recientes. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.projects
    private val recentsRepo = app.container.recents

    var projects by mutableStateOf<List<Entry>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    val recents: StateFlow<List<RecentFile>> =
        recentsRepo.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            repo.revision.collect { reload() }
        }
    }

    private suspend fun reload() {
        projects = repo.list("").filter { it.isDirectory }
            .sortedByDescending { it.lastModified }
        loaded = true
    }

    fun rename(relPath: String, newName: String) {
        viewModelScope.launch {
            repo.rename(relPath, newName).onFailure { message = it.message }
        }
    }

    fun delete(relPath: String) {
        viewModelScope.launch {
            if (!repo.delete(relPath)) message = "No se pudo eliminar"
        }
    }

    fun createProject(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repo.createProject(name)
                .onSuccess(onCreated)
                .onFailure { message = it.message }
        }
    }

    fun forgetRecent(uri: String) {
        viewModelScope.launch { recentsRepo.remove(uri) }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(app) as T
    }
}

/** Explorador de una carpeta ("" = raíz de Ghost Proyectos): crear, renombrar, eliminar. */
class FolderViewModel(app: Application, val path: String) : AndroidViewModel(app) {
    private val repo = app.container.projects

    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            repo.revision.collect { reload() }
        }
    }

    private suspend fun reload() {
        entries = try {
            repo.list(path)
        } catch (_: Exception) {
            emptyList() // carpeta eliminada o inaccesible
        }
        loaded = true
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repo.createFolder(path, name).onFailure { message = it.message }
        }
    }

    fun createFile(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repo.createPythonFile(path, name)
                .onSuccess(onCreated)
                .onFailure { message = it.message }
        }
    }

    fun rename(relPath: String, newName: String) {
        viewModelScope.launch {
            repo.rename(relPath, newName).onFailure { message = it.message }
        }
    }

    fun delete(relPath: String) {
        viewModelScope.launch {
            if (!repo.delete(relPath)) message = "No se pudo eliminar"
        }
    }

    class Factory(private val app: Application, private val path: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderViewModel(app, path) as T
    }
}

/** Diálogo del botón +: nuevo archivo Python en una carpeta existente o en un proyecto nuevo. */
class NewFileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.projects

    var folders by mutableStateOf<List<String>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun loadFolders() {
        viewModelScope.launch { folders = repo.allFolders() }
    }

    fun clearError() {
        error = null
    }

    /**
     * Crea el archivo. [destination] es la ruta relativa de la carpeta ("" = raíz);
     * si [newProject] no está vacío, primero se crea ese proyecto y el archivo va dentro.
     */
    fun create(destination: String, newProject: String, fileName: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            var parent = destination
            if (newProject.isNotBlank()) {
                val created = repo.createProject(newProject)
                if (created.isFailure) {
                    error = created.exceptionOrNull()?.message
                    return@launch
                }
                parent = created.getOrThrow()
            }
            repo.createPythonFile(parent, fileName)
                .onSuccess(onCreated)
                .onFailure { error = it.message }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NewFileViewModel(app) as T
    }
}
