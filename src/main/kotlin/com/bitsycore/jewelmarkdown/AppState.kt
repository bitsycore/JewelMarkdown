package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

// The three layout modes offered by the toolbar's view switch.
enum class ViewMode { Editor, Split, Preview }

// A single open document: its editor buffer (text + caret), backing file and saved baseline.
@Stable
class Document(inText: String, inFile: File?) {
	// Editor buffer with selection/caret; bound to the editor's TextArea.
	var fieldValue by mutableStateOf(TextFieldValue(inText, TextRange(inText.length)))

	// Backing file of the document, or null for an unsaved scratch buffer.
	var file by mutableStateOf(inFile)
		private set

	// Text as last opened/saved; compared against the buffer to detect edits.
	var savedText by mutableStateOf(inText)
		private set

	val text: String get() = fieldValue.text
	val isDirty: Boolean get() = text != savedText
	val title: String get() = file?.name ?: "Untitled"

	// Records the current text as saved and binds the document to a file.
	fun markSaved(inFile: File) {
		file = inFile
		savedText = text
	}
}

// Top-level UI state: the set of open documents, the active one, theme, layout, settings and
// the project (folder) panel state.
@Stable
class AppState(inIsDark: Boolean) {
	// Open documents shown as tabs. May be empty — the welcome panel takes over in that case.
	val documents = mutableStateListOf<Document>()
	var activeIndex by mutableStateOf(0)

	var isDark by mutableStateOf(inIsDark)
	var viewMode by mutableStateOf(ViewMode.Split)

	// Editor/preview split position in Split mode (fraction given to the editor).
	var splitRatio by mutableStateOf(0.5f)

	// Ctrl held (tracked from the window key handler); preview links open only on Ctrl+Click.
	var isCtrlDown by mutableStateOf(false)

	// User-configurable UI settings.
	val settings = Settings()
	var showSettings by mutableStateOf(false)

	// Which title-bar menu (File/Edit/View/Help) is currently open, shared so hovering switches.
	var menuOpenName by mutableStateOf<String?>(null)

	// Keyboard shortcuts (action -> key combo) and the action currently being rebound.
	val keymap = defaultKeymap()
	var recordingAction by mutableStateOf<ShortcutAction?>(null)

	// Project (folder) panel.
	var projectRoot by mutableStateOf<File?>(null)
	var showProjectPanel by mutableStateOf(false)

	// Latched when the app should shut down (used to honor "exit on last tab close" from
	// inside a non-suspend action). Watched by main() via a LaunchedEffect.
	var pendingExit by mutableStateOf(false)

	// The currently focused document, or null when no tabs are open.
	val active: Document? get() = documents.getOrNull(activeIndex)

	// Opens a new empty scratch document and focuses it.
	fun newDocument() {
		documents.add(Document("", null))
		activeIndex = documents.lastIndex
	}

	// Opens a file in a tab, focusing an existing tab if the file is already open.
	fun openFile(inFile: File) {
		val vExisting = documents.indexOfFirst { it.file?.absolutePath == inFile.absolutePath }
		if (vExisting >= 0) {
			activeIndex = vExisting
			return
		}
		val vContent = runCatching { inFile.readText() }.getOrNull() ?: return
		documents.add(Document(vContent, inFile))
		activeIndex = documents.lastIndex
	}

	// Opens the bundled demo document as a fresh unsaved tab.
	fun openDemo() {
		documents.add(Document(kSampleMarkdown, null))
		activeIndex = documents.lastIndex
	}

	// Closes a tab. Leaves the list empty if it was the last one — the welcome panel handles
	// that. If the user opted in, closing the last tab also requests application exit.
	fun closeDocument(inIndex: Int) {
		if (inIndex !in documents.indices) return
		documents.removeAt(inIndex)
		if (documents.isEmpty()) {
			activeIndex = 0
			if (settings.exitOnLastTabClose) pendingExit = true
		} else {
			activeIndex = activeIndex.coerceAtMost(documents.lastIndex)
		}
	}

	// Closes every tab except the one at inIndex.
	fun closeOthers(inIndex: Int) {
		val vKeep = documents.getOrNull(inIndex) ?: return
		documents.clear()
		documents.add(vKeep)
		activeIndex = 0
	}

	// Closes all tabs, leaving the welcome panel. May also exit when the setting is on.
	fun closeAll() {
		documents.clear()
		activeIndex = 0
		if (settings.exitOnLastTabClose) pendingExit = true
	}

	// Moves the tab at inFrom to inTo, used for drag-to-reorder.
	fun moveDocument(inFrom: Int, inTo: Int) {
		if (inFrom !in documents.indices || inTo !in documents.indices || inFrom == inTo) return
		val vActive = active
		documents.add(inTo, documents.removeAt(inFrom))
		activeIndex = if (vActive == null) 0 else documents.indexOf(vActive).coerceAtLeast(0)
	}

	// Restores the keyboard shortcuts to the default Visual-Studio-style layout.
	fun resetKeymap() {
		keymap.clear()
		keymap.putAll(defaultKeymap())
	}

	// Window/title-bar caption for the active document; a dot marks unsaved changes.
	// Falls back to the app name when no tab is open.
	fun windowTitle(): String {
		val vDoc = active ?: return "Jewel Markdown"
		val vDirtyMark = if (vDoc.isDirty) "● " else ""
		return "$vDirtyMark${vDoc.title}  —  Jewel Markdown"
	}
}

// Creates and remembers the app state. Loads saved preferences, then conditionally restores
// the previous session and/or opens the bundled demo document.
@Composable
fun rememberAppState(inOpenDemo: Boolean = false): AppState =
	remember {
		AppState(inIsDark = true).apply {
			val vSession = Persistence.load(this)
			if (settings.restoreSession) {
				for (vPath in vSession.paths) {
					val vFile = File(vPath)
					if (vFile.exists()) openFile(vFile)
				}
				if (documents.isNotEmpty()) activeIndex = vSession.activeIndex.coerceIn(0, documents.lastIndex)
			}
			if (inOpenDemo) openDemo()
		}
	}
