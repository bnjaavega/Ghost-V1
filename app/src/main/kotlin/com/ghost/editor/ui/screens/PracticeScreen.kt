package com.ghost.editor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.editor.data.PracticeContent
import com.ghost.editor.data.PracticeTopic
import com.ghost.editor.editor.PythonVocabulary
import com.ghost.editor.ui.components.BackHeader
import com.ghost.editor.ui.components.pressScale
import com.ghost.editor.ui.theme.CodeFont
import com.ghost.editor.ui.theme.GhostColors

/** Biblioteca educativa de conceptos de Python (acceso desde el menú de tres puntos). */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onTry: (PracticeTopic) -> Unit
) {
    Column(Modifier.fillMaxSize().background(GhostColors.Bg)) {
        BackHeader(title = "Práctica", onBack = onBack)
        Text(
            "Consulta conceptos básicos de Python. Pulsa un concepto para ver el ejemplo.",
            style = MaterialTheme.typography.bodyMedium,
            color = GhostColors.TextSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(PracticeContent.topics, key = { it.name }) { topic ->
                TopicCard(topic, onTry)
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun TopicCard(topic: PracticeTopic, onTry: (PracticeTopic) -> Unit) {
    var expanded by rememberSaveable(topic.name) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val clipboard = LocalClipboardManager.current
    val arrow by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GhostColors.Card1)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(source, 0.99f)
                .clickable(interactionSource = source, indication = null) { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(topic.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontFamily = CodeFont)
                if (!expanded) {
                    Text(
                        topic.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = GhostColors.TextSecondary,
                modifier = Modifier.rotate(arrow)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(topic.summary, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD1D1D6))
                Spacer(Modifier.height(12.dp))
                Label("Ejemplo")
                CodeBlock(highlightPython(topic.code.trimEnd()))
                if (topic.output != null) {
                    Spacer(Modifier.height(12.dp))
                    Label("Resultado")
                    CodeBlock(AnnotatedString(topic.output), color = Color(0xFFB5F2C0))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Copiar", Icons.Outlined.ContentCopy) {
                        clipboard.setText(AnnotatedString(topic.code))
                    }
                    SmallButton("Probar en el editor", Icons.Outlined.PlayArrow) { onTry(topic) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = GhostColors.TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun CodeBlock(text: AnnotatedString, color: Color = Color(0xFFECECEC)) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GhostColors.CodeBg)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(text, fontFamily = CodeFont, fontSize = 13.sp, lineHeight = 19.sp, color = color)
    }
}

@Composable
private fun SmallButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(source)
            .clip(RoundedCornerShape(12.dp))
            .background(GhostColors.Card2)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

// ------------------------------------------------- resaltado sencillo de ejemplos

private val TOKEN = Regex(
    "(#.*$)|(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')|(\\b\\d+(?:\\.\\d+)?\\b)|([A-Za-z_][A-Za-z0-9_]*)",
    RegexOption.MULTILINE
)

private val KEYWORD_COLOR = Color(0xFFFC5FA3)
private val STRING_COLOR = Color(0xFFFC8F5D)
private val NUMBER_COLOR = Color(0xFFD9C97C)
private val COMMENT_COLOR = Color(0xFF6C7986)
private val BUILTIN_COLOR = Color(0xFFB48BFF)
private val FUNCTION_COLOR = Color(0xFF6BDFFF)
private val IDENT_COLOR = Color(0xFFA9D5FF)

fun highlightPython(code: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TOKEN.findAll(code)) {
        if (m.range.first > last) {
            withStyle(SpanStyle(color = Color(0xFFECECEC))) { append(code.substring(last, m.range.first)) }
        }
        val text = m.value
        val color = when {
            m.groups[1] != null -> COMMENT_COLOR
            m.groups[2] != null -> STRING_COLOR
            m.groups[3] != null -> NUMBER_COLOR
            text in PythonVocabulary.keywords -> KEYWORD_COLOR
            text in PythonVocabulary.builtins -> BUILTIN_COLOR
            code.getOrNull(m.range.last + 1) == '(' -> FUNCTION_COLOR
            else -> IDENT_COLOR
        }
        withStyle(SpanStyle(color = color)) { append(text) }
        last = m.range.last + 1
    }
    if (last < code.length) {
        withStyle(SpanStyle(color = Color(0xFFECECEC))) { append(code.substring(last)) }
    }
}
