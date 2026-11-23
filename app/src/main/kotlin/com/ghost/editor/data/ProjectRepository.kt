package com.ghost.editor.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestiona la carpeta "Ghost Proyectos".
 *
 * Ubicación: <Documentos de la app>/Ghost Proyectos, es decir
 * Android/data/com.ghost.editor/files/Documents/Ghost Proyectos.
 * Se usa el directorio de documentos propio de la app porque es la única ubicación
 * de "Documentos" donde Android moderno permite leer/escribir archivos con la API
 * de File (necesaria para que Python pueda importar módulos hermanos) sin pedir
 * permisos de almacenamiento amplios (MANAGE_EXTERNAL_STORAGE).
 */
class ProjectRepository(context: Context) {

    val root: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
        ROOT_NAME
    ).also { it.mkdirs() }

    private val canonicalRoot: File = root.canonicalFile

    private val _revision = MutableStateFlow(0)

    /** Se incrementa tras cada cambio para que las pantallas recarguen sus listas. */
    val revision: StateFlow<Int> = _revision

    private fun bump() = _revision.update { it + 1 }

    // ---------------------------------------------------------------- rutas

    fun resolve(relPath: String): File {
        val file = (if (relPath.isEmpty()) canonicalRoot else File(canonicalRoot, relPath)).canonicalFile
        val inside = file.path == canonicalRoot.path ||
            file.path.startsWith(canonicalRoot.path + File.separator)
        require(inside) { "Ruta fuera de Ghost Proyectos" }
        return file
    }

    private fun relative(file: File): String =
        file.canonicalFile.relativeTo(canonicalRoot).path.replace(File.separatorChar, '/')

    private fun isVisible(f: File) = !f.name.startsWith(".") && f.name != "__pycache__"

    private fun latestModified(dir: File): Long =
        dir.walkTopDown().maxDepth(3).filter { isVisible(it) || it == dir }
            .maxOfOrNull { it.lastModified() } ?: dir.lastModified()

    // ------------------------------------------------------------- lecturas

    suspend fun list(relPath: String): List<Entry> = withContext(Dispatchers.IO) {
        val dir = resolve(relPath)
        val children = dir.listFiles()?.filter { isVisible(it) } ?: emptyList()
        children
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                if (f.isDirectory) {
                    Entry(
                        name = f.name,
                        relPath = relative(f),
                        isDirectory = true,
                        lastModified = latestModified(f),
                        count = (f.listFiles()?.count { isVisible(it) } ?: 0).toLong()
                    )
                } else {
                    Entry(f.name, relative(f), false, f.lastModified(), f.length())
                }
            }
    }

    /** Todas las carpetas (hasta 3 niveles) para elegir destino de un archivo nuevo. */
    suspend fun allFolders(): List<String> = withContext(Dispatchers.IO) {
        canonicalRoot.walkTopDown().maxDepth(3)
            .filter { it.isDirectory && it != canonicalRoot && isVisible(it) }
            .map { relative(it) }
            .sortedBy { it.lowercase() }
            .toList()
    }

    suspend fun read(relPath: String): String = withContext(Dispatchers.IO) {
        resolve(relPath).readText(Charsets.UTF_8)
    }

    // ----------------------------------------------------------- escrituras

    suspend fun write(relPath: String, text: String) = withContext(Dispatchers.IO) {
        val target = resolve(relPath)
        target.parentFile?.mkdirs()
        // Escritura segura: primero a un temporal y luego se reemplaza el original.
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
        bump()
    }

    suspend fun createProject(rawName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = cleanName(rawName)
            val dir = resolve(name)
            require(!dir.exists()) { "Ya existe un proyecto llamado \"$name\"" }
            check(dir.mkdirs()) { "No se pudo crear la carpeta" }
            bump()
            relative(dir)
        }
    }

    suspend fun createFolder(parentRel: String, rawName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = cleanName(rawName)
                val dir = File(resolve(parentRel), name).canonicalFile
                resolve(relative(dir)) // valida que siga dentro de la raíz
                require(!dir.exists()) { "Ya existe \"$name\"" }
                check(dir.mkdirs()) { "No se pudo crear la carpeta" }
                bump()
                relative(dir)
            }
        }

    /** Crea un archivo .py (añade la extensión si falta) y devuelve su ruta relativa. */
    suspend fun createPythonFile(
        parentRel: String,
        rawName: String,
        content: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var name = cleanName(rawName)
            if (!name.endsWith(".py", ignoreCase = true)) name += ".py"
            val parent = resolve(parentRel)
            parent.mkdirs()
            val file = File(parent, name).canonicalFile
            resolve(relative(file))
            require(!file.exists()) { "Ya existe \"$name\"" }
            file.writeText(content ?: "# $name\n\n", Charsets.UTF_8)
            bump()
            relative(file)
        }
    }

    suspend fun rename(relPath: String, rawNewName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = resolve(relPath)
                require(source != canonicalRoot) { "No se puede renombrar la carpeta raíz" }
                var name = cleanName(rawNewName)
                if (source.isFile && source.extension.equals("py", true) &&
                    !name.endsWith(".py", ignoreCase = true)
                ) name += ".py"
                val target = File(source.parentFile, name).canonicalFile
                resolve(relative(target))
                require(!target.exists() || target == source) { "Ya existe \"$name\"" }
                check(source.renameTo(target)) { "No se pudo renombrar" }
                bump()
                relative(target)
            }
        }

    suspend fun delete(relPath: String): Boolean = withContext(Dispatchers.IO) {
        val target = resolve(relPath)
        if (target == canonicalRoot) return@withContext false
        val ok = target.deleteRecursively()
        bump()
        ok
    }

    /** Crea un proyecto de bienvenida la primera vez que se abre Ghost. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val marker = File(canonicalRoot, ".ghost_seeded")
        if (marker.exists()) return@withContext
        val empty = canonicalRoot.listFiles()?.none { isVisible(it) } ?: true
        if (empty) {
            val dir = File(canonicalRoot, "Primeros pasos").also { it.mkdirs() }
            File(dir, "main.py").writeText(SAMPLE, Charsets.UTF_8)
        }
        marker.createNewFile()
        bump()
    }

    // ----------------------------------------------------------- utilidades

    private fun cleanName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "El nombre no puede estar vacío" }
        require(name.length <= 80) { "El nombre es demasiado largo" }
        require(!name.startsWith(".")) { "El nombre no puede empezar por punto" }
        require(name.none { it in "/\\:*?\"<>|" || it.isISOControl() }) {
            "El nombre no puede contener / \\ : * ? \" < > |"
        }
        return name
    }

    companion object {
        const val ROOT_NAME = "Ghost Proyectos"

        private const val SAMPLE = """# Bienvenido a Ghost
nombre = input("¿Cómo te llamas? ")
print("Hola", nombre)

for i in range(1, 4):
    print("Contando:", i)
"""
    }
}
