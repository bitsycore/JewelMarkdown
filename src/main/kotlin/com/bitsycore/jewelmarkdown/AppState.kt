package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import java.io.File

// The three layout modes offered by the toolbar's view switch.
enum class ViewMode { Editor, Split, Preview }

// Holds the mutable UI state of the editor window: the document buffer, its backing
// file, the active theme and the chosen layout. Reading these inside composition
// drives recomposition (live preview, dirty indicator, theme switch).
@Stable
class AppState(val editorState: TextFieldState, inIsDark: Boolean) {
	// Backing file of the current document, or null for an unsaved buffer.
	var currentFile by mutableStateOf<File?>(null)

	// Dark theme is the default; toggled from the title bar.
	var isDark by mutableStateOf(inIsDark)

	// Editor-only, side-by-side or preview-only layout.
	var viewMode by mutableStateOf(ViewMode.Split)

	// Text as last opened/saved; compared against the buffer to detect edits.
	var savedText by mutableStateOf(editorState.text.toString())
		private set

	// Current buffer contents as a plain String.
	val text: String get() = editorState.text.toString()

	// True when the buffer differs from the last opened/saved content.
	val isDirty: Boolean get() = text != savedText

	// Replaces the whole buffer (New/Open) and resets the saved baseline.
	fun loadText(inNewText: String, inFile: File?) {
		editorState.setTextAndPlaceCursorAtEnd(inNewText)
		currentFile = inFile
		savedText = inNewText
	}

	// Records the current text as the saved baseline after a successful write.
	fun markSaved(inFile: File) {
		currentFile = inFile
		savedText = text
	}

	// Window/title-bar caption: a dot marks unsaved changes.
	fun windowTitle(): String {
		val vName = currentFile?.name ?: "Untitled"
		val vDirtyMark = if (isDirty) "● " else ""
		return "$vDirtyMark$vName  —  Jewel Markdown"
	}
}

// Creates and remembers an AppState seeded with the sample document, dark theme on.
@Composable
fun rememberAppState(): AppState {
	val vEditorState = rememberTextFieldState(kSampleMarkdown)
	return remember { AppState(vEditorState, inIsDark = true) }
}
