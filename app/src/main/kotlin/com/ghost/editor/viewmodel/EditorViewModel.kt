package com.ghost.editor.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ghost.editor.container
import com.ghost.editor.data.EditorSettings
import com.ghost.editor.data.FileRef
import com.ghost.editor.editor.EditorController
import com.ghost.editor.python.PythonRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

enum class SaveStatus { SAVED, UNSAVED, SAVING, ERROR }

enum class ConsoleKind { OUT, ERR, INPUT, INFO }

data class ConsoleSegment(val kind: ConsoleKind, val text: String)

private sealed interface RunnerEvent {
    data class Output(val text: String, val isError: Boolean) : RunnerEvent
    data class InputRequested(val prompt: String) : RunnerEvent
    data class Finished(val exitCode: Int, val internalError: String?) : RunnerEvent
}

class EditorViewModel(app: Application, val ref: FileRef) : AndroidViewModel(app) {

    private val container = app.container
    private val runner = PythonRunner(app)
    val controller = EditorController()

    val settings: StateFlow<EditorSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettings())

    // ------------------------------------------------------------ archivo
    var fileName by mutableStateOf(
        when (ref) {
            is FileRef.Internal -> ref.relPath.substringAfterLast('/')
            is FileRef.External -> "archivo.py"
        }
    )
        private set
    val locationLabel: String
        get() = when (ref) {
            is FileRef.Internal -> ref.relPath.substringBeforeLast('/', "").ifEmpty { "Ghost Proyectos" }
            is FileRef.External -> "Archivo externo"
        }
    val isExternal: Boolean get() = ref is FileRef.External

    var initialText by mutableStateOf<String?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    var saveStatus by mutableStateOf(SaveStatus.SAVED)
        private set
    var message by mutableStateOf<String?>(null)

    private var changeCounter = 0
    private var autoSaveJob: Job? = null
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            when (ref) {
                is FileRef.Internal -> {
                    initialText = container.projects.read(ref.relPath)
                }

                is FileRef.External -> {
                    val resolver = getApplication<Application>().contentResolver
                    val (text, name) = withContext(Dispatchers.IO) {
                        val content = resolver.openInputStream(ref.uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        } ?: error("No se pudo abrir el archivo")
                        content to queryDisplayName(ref.uri)
                    }
                    fileName = name
                    initialText = text
                    container.recents.add(ref.uri.toString(), name)
                }
            }
        } catch (t: Throwable) {
            loadError = t.message ?: "No se pudo abrir el archivo"
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "archivo.py"
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/') ?: "archivo.py"
        }
    }

    // ---------------------------------------------------------- guardado

    /** Llamado por el editor tras cada cambio de contenido. */
    fun onContentChanged() {
        changeCounter++
        saveStatus = SaveStatus.UNSAVED
        autoSaveJob?.cancel()
        if (settings.value.autoSave) {
            // Debounce: solo se escribe cuando el usuario deja de teclear brevemente.
            autoSaveJob = viewModelScope.launch {
                delay(AUTOSAVE_DELAY_MS)
                saveNow()
            }
        }
    }

    /** Guarda el texto actual del editor. Devuelve el texto guardado (o null si no hay editor). */
    suspend fun saveNow(force: Boolean = false): String? {
        val text = controller.text() ?: return null
        if (saveStatus == SaveStatus.SAVED && !force) return text
        val ticket = changeCounter
        saveStatus = SaveStatus.SAVING
        val ok = saveMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) { writeText(text) }
        }
        saveStatus = when {
            !ok -> SaveStatus.ERROR
            ticket == changeCounter -> SaveStatus.SAVED
            else -> SaveStatus.UNSAVED
        }
        return text
    }

    private suspend fun writeText(text: String): Boolean = try {
        when (ref) {
            is FileRef.Internal -> container.projects.write(ref.relPath, text)
            is FileRef.External -> {
                val resolver = getApplication<Application>().contentResolver
                // "wt" = escribir y truncar para no dejar restos del contenido anterior.
                resolver.openOutputStream(ref.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("No se pudo abrir el archivo para escribir")
            }
        }
        true
    } catch (t: Throwable) {
        message = if (ref is FileRef.External) {
            "No se pudo guardar en el archivo original (${t.message}). " +
                "Usa «Guardar copia en Ghost Proyectos» para no perder tus cambios."
        } else {
            "No se pudo guardar: ${t.message}"
        }
        false
    }

    /** Guarda de inmediato si hay cambios pendientes (al salir o pasar a segundo plano). */
    fun flush() {
        if (saveStatus == SaveStatus.UNSAVED || saveStatus == SaveStatus.ERROR) {
            viewModelScope.launch { saveNow(force = true) }
        }
    }

    val hasUnsavedChanges: Boolean
        get() = saveStatus == SaveStatus.UNSAVED || saveStatus == SaveStatus.ERROR

    /** Copia el contenido actual a Ghost Proyectos/Importados (para archivos externos de solo lectura). */
    fun saveCopyToProjects() {
        viewModelScope.launch {
            val text = controller.text() ?: return@launch
            val name = fileName.let { if (it.endsWith(".py", true)) it else "$it.py" }
            val result = container.projects.createPythonFile("Importados", name, text)
            message = result.fold(
                onSuccess = { "Copia guardada en Ghost Proyectos/$it" },
                onFailure = { "No se pudo guardar la copia: ${it.message}" }
            )
        }
    }

    // --------------------------------------------------------- ejecución

    var consoleVisible by mutableStateOf(false)
    var consoleSegments by mutableStateOf<List<ConsoleSegment>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var stopping by mutableStateOf(false)
        private set
    var awaitingInput by mutableStateOf(false)
        private set

    private val events = ConcurrentLinkedQueue<RunnerEvent>()
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)
    private var consoleChars = 0

    private val listener = object : PythonRunner.Listener {
        override fun onOutput(text: String, isError: Boolean) {
            events.add(RunnerEvent.Output(text, isError))
            flushSignal.trySend(Unit)
        }

        override fun onInputRequested(prompt: String) {
            events.add(RunnerEvent.InputRequested(prompt))
            flushSignal.trySend(Unit)
        }

        override fun onFinished(exitCode: Int, internalError: String?) {
            events.add(RunnerEvent.Finished(exitCode, internalError))
            flushSignal.trySend(Unit)
        }
    }

    init {
        // Agrupa la salida de Python para no recomponer la UI en cada print().
        viewModelScope.launch {
            for (signal in flushSignal) {
                drainEvents()
                delay(35)
            }
        }
    }

    private fun drainEvents() {
        var segments = consoleSegments
        while (true) {
            val event = events.poll() ?: break
            when (event) {
                is RunnerEvent.Output ->
                    segments = append(segments, if (event.isError) ConsoleKind.ERR else ConsoleKind.OUT, event.text)

                is RunnerEvent.InputRequested -> awaitingInput = true

                is RunnerEvent.Finished -> {
                    val note = when {
                        event.internalError != null ->
                            "\n[No se pudo ejecutar Python: ${event.internalError}]\n"
                        event.exitCode == 0 -> "\n[Proceso finalizado]\n"
                        event.exitCode == -2 -> "\n[Ejecución detenida]\n"
                        else -> "\n[Proceso finalizado con código ${event.exitCode}]\n"
                    }
                    segments = append(segments, ConsoleKind.INFO, note)
                    running = false
                    stopping = false
                    awaitingInput = false
                }
            }
        }
        consoleSegments = segments
    }

    private fun append(list: List<ConsoleSegment>, kind: ConsoleKind, text: String): List<ConsoleSegment> {
        if (text.isEmpty()) return list
        val result = ArrayList(list)
        val last = result.lastOrNull()
        if (last != null && last.kind == kind) {
            result[result.size - 1] = ConsoleSegment(kind, last.text + text)
        } else {
            result.add(ConsoleSegment(kind, text))
        }
        consoleChars += text.length
        // Límite de memoria: se descarta el principio si la salida es enorme.
        while (consoleChars > MAX_CONSOLE_CHARS && result.size > 1) {
            consoleChars -= result.removeAt(0).text.length
        }
        if (consoleChars > MAX_CONSOLE_CHARS && result.size == 1) {
            val only = result[0]
            val trimmed = only.text.takeLast(MAX_CONSOLE_CHARS / 2)
            consoleChars = trimmed.length
            result[0] = only.copy(text = trimmed)
        }
        return result
    }

    fun toggleConsole() {
        consoleVisible = !consoleVisible
    }

    fun clearConsole() {
        consoleSegments = emptyList()
        consoleChars = 0
    }

    /** Guarda, ejecuta el archivo con el intérprete real y muestra el resultado en la consola. */
    fun run() {
        if (running) return
        viewModelScope.launch {
            val text = saveNow(force = true) ?: controller.text() ?: return@launch
            val file: File = try {
                withContext(Dispatchers.IO) {
                    when (ref) {
                        is FileRef.Internal -> container.projects.resolve(ref.relPath)
                        is FileRef.External -> {
                            // Python necesita un archivo real: se ejecuta una copia en la caché.
                            val dir = File(getApplication<Application>().cacheDir, "external_run")
                            dir.mkdirs()
                            File(dir, fileName.ifBlank { "main.py" }).also {
                                it.writeText(text, Charsets.UTF_8)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                message = "No se pudo preparar el archivo: ${t.message}"
                return@launch
            }

            consoleVisible = true
            clearConsole()
            consoleSegments = append(emptyList(), ConsoleKind.INFO, "▶ $fileName\n")
            running = true
            awaitingInput = false
            if (!runner.start(file, listener)) {
                running = false
                message = "Ya hay un programa en ejecución"
            }
        }
    }

    fun stop() {
        if (!running) return
        stopping = true
        runner.stop()
    }

    /** Envía a input() la línea escrita en la consola. */
    fun submitInput(line: String) {
        if (!awaitingInput) return
        awaitingInput = false
        consoleSegments = append(consoleSegments, ConsoleKind.INPUT, line + "\n")
        runner.sendInput(line)
    }

    override fun onCleared() {
        runner.stop()
        super.onCleared()
    }

    class Factory(private val app: Application, private val ref: FileRef) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, ref) as T
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 800L
        const val MAX_CONSOLE_CHARS = 120_000
    }
}
