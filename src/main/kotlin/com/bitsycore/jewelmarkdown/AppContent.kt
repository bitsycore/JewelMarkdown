package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*

// Window body below the title bar: toolbar, editor/preview split, and status bar.
// The root is filled with the theme's panel background so every pane (toolbar, preview,
// status bar, gaps) is uniformly themed; the editor keeps its own editor-field background.
@Composable
fun AppBody(inState: AppState) {
	Column(
		Modifier
			.fillMaxSize()
			.background(JewelTheme.globalColors.panelBackground)
	) {
		Toolbar(inState)
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
		EditorAndPreview(inState, Modifier.weight(1f).fillMaxWidth())
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
		StatusBar(inState)
	}
}

// Document actions (New/Open/Save/Save As) on the left, view-mode switch on the right.
@Composable
private fun Toolbar(inState: AppState) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		OutlinedButton(onClick = { inState.loadText("", null) }) { Text("New") }
		OutlinedButton(onClick = { onOpen(inState) }) { Text("Open") }
		OutlinedButton(onClick = { onSave(inState) }) { Text("Save") }
		OutlinedButton(onClick = { onSaveAs(inState) }) { Text("Save As") }
		Spacer(Modifier.weight(1f))
		ViewModeSwitch(inState)
	}
}

// Three-way switch between Editor, Split and Preview layouts.
@Composable
private fun ViewModeSwitch(inState: AppState) {
	Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
		ModeButton("Editor", inState.viewMode == ViewMode.Editor) { inState.viewMode = ViewMode.Editor }
		ModeButton("Split", inState.viewMode == ViewMode.Split) { inState.viewMode = ViewMode.Split }
		ModeButton("Preview", inState.viewMode == ViewMode.Preview) { inState.viewMode = ViewMode.Preview }
	}
}

// A single view-mode button; the active mode is shown as a filled (default) button.
@Composable
private fun ModeButton(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
	if (inSelected) {
		DefaultButton(onClick = inOnClick) { Text(inLabel) }
	} else {
		OutlinedButton(onClick = inOnClick) { Text(inLabel) }
	}
}

// Editor and preview side by side, honoring the current view mode.
@Composable
private fun EditorAndPreview(inState: AppState, inModifier: Modifier) {
	Row(inModifier) {
		if (inState.viewMode != ViewMode.Preview) {
			EditorPane(inState, Modifier.weight(1f).fillMaxHeight())
		}
		if (inState.viewMode == ViewMode.Split) {
			Divider(Orientation.Vertical, Modifier.fillMaxHeight())
		}
		if (inState.viewMode != ViewMode.Editor) {
			MarkdownPreview(inState.text, inState.isDark, Modifier.weight(1f).fillMaxHeight())
		}
	}
}

// Raw Markdown editor, rendered in a monospace font.
@Composable
private fun EditorPane(inState: AppState, inModifier: Modifier) {
	val vEditorStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace)
	TextArea(
		state = inState.editorState,
		modifier = inModifier.padding(8.dp),
		textStyle = vEditorStyle,
		placeholder = { Text("Write some Markdown…") },
	)
}

// Bottom status bar: file path, dirty state and document metrics.
@Composable
private fun StatusBar(inState: AppState) {
	val vText = inState.text
	val vLineCount = if (vText.isEmpty()) 0 else vText.count { it == '\n' } + 1
	val vWordCount = if (vText.isBlank()) 0 else vText.trim().split(Regex("\\s+")).size
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(inState.currentFile?.absolutePath ?: "Unsaved document")
		Spacer(Modifier.weight(1f))
		Text(if (inState.isDirty) "Modified" else "Saved")
		Text("$vLineCount lines")
		Text("$vWordCount words")
		Text("${vText.length} chars")
	}
}

// ==================
// MARK: File actions
// ==================

// Opens a Markdown file into the editor; ignores read failures.
private fun onOpen(inState: AppState) {
	val vFile = chooseOpenFile() ?: return
	runCatching { vFile.readText() }.onSuccess { inState.loadText(it, vFile) }
}

// Saves to the current file, falling back to "Save As" when there is none.
private fun onSave(inState: AppState) {
	val vFile = inState.currentFile
	if (vFile == null) {
		onSaveAs(inState)
		return
	}
	runCatching { vFile.writeText(inState.text) }.onSuccess { inState.markSaved(vFile) }
}

// Prompts for a destination and writes the document there.
private fun onSaveAs(inState: AppState) {
	val vSuggested = inState.currentFile?.name ?: "untitled.md"
	val vFile = chooseSaveFile(vSuggested) ?: return
	runCatching { vFile.writeText(inState.text) }.onSuccess { inState.markSaved(vFile) }
}
