package com.ghost.editor.editor

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ghost.editor.data.EditorSettings
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

/**
 * Editor de código real (Sora Editor) embebido en Compose.
 *
 * Incluye numeración de líneas, resaltado de sintaxis Python (TextMate), autocompletado,
 * indentación automática, selección/copiar/cortar/pegar, deshacer/rehacer y desplazamiento
 * vertical y horizontal.
 *
 * [initialText] solo se carga una vez; a partir de ahí el editor es la fuente de verdad.
 */
@Composable
fun GhostCodeEditor(
    initialText: String,
    settings: EditorSettings,
    controller: EditorController,
    onContentChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Evita notificar "cambios" mientras se carga el texto inicial.
    val loading = remember { BooleanArray(1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            EditorSetup.init(context)
            CodeEditor(context).apply {
                setTypefaceText(Typeface.MONOSPACE)
                setTypefaceLineNumber(Typeface.MONOSPACE)
                setColorScheme(EditorSetup.createColorScheme())
                setEditorLanguage(EditorSetup.createPythonLanguage())
                setLineNumberEnabled(true)
                setTabWidth(4)
                setNonPrintablePaintingFlags(0)
                setHorizontalScrollBarEnabled(true)
                setVerticalScrollBarEnabled(true)

                loading[0] = true
                setText(initialText)
                loading[0] = false

                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    controller.refreshHistory()
                    if (!loading[0]) onContentChanged()
                }
                controller.attach(this)
            }
        },
        update = { editor ->
            editor.setTextSize(settings.fontSize)
            editor.setWordwrap(settings.wordWrap)
            editor.getComponent(EditorAutoCompletion::class.java).setEnabled(settings.autoComplete)
            (editor.editorLanguage as? GhostPythonLanguage)?.autoIndent = settings.autoIndent
        },
        onRelease = { editor ->
            controller.detach(editor)
            editor.release()
        }
    )
}
