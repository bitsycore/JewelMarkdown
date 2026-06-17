package com.bitsycore.topazmd

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

// Native "open file" dialog. Returns the selected file, or null if cancelled.
fun chooseOpenFile(): File? {
	val vDialog = FileDialog(null as Frame?, "Open Markdown", FileDialog.LOAD)
	vDialog.isVisible = true
	val vDir = vDialog.directory ?: return null
	val vName = vDialog.file ?: return null
	return File(vDir, vName)
}

// Native "save file" dialog seeded with a suggested name. Returns the target, or null.
fun chooseSaveFile(inSuggestedName: String): File? {
	val vDialog = FileDialog(null as Frame?, "Save Markdown", FileDialog.SAVE)
	vDialog.file = inSuggestedName
	vDialog.isVisible = true
	val vDir = vDialog.directory ?: return null
	val vName = vDialog.file ?: return null
	return File(vDir, vName)
}

// "Choose folder" dialog. On macOS the AWT FileDialog can pick directories natively when the
// `apple.awt.fileDialogForDirectories` system property is true — that flips it to a native
// NSOpenPanel in directory mode. Everywhere else we fall back to Swing's JFileChooser since
// FileDialog can't pick directories on Windows/Linux. Returns the chosen folder, or null.
fun chooseFolder(): File? {
	if (kIsMac) {
		val vPrevious = System.getProperty("apple.awt.fileDialogForDirectories")
		System.setProperty("apple.awt.fileDialogForDirectories", "true")
		try {
			val vDialog = FileDialog(null as Frame?, "Open Folder", FileDialog.LOAD)
			vDialog.isVisible = true
			val vDir = vDialog.directory ?: return null
			val vName = vDialog.file ?: return null
			return File(vDir, vName)
		} finally {
			if (vPrevious == null) System.clearProperty("apple.awt.fileDialogForDirectories")
			else System.setProperty("apple.awt.fileDialogForDirectories", vPrevious)
		}
	}
	val vChooser = JFileChooser()
	vChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
	vChooser.dialogTitle = "Open Folder"
	return if (vChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile else null
}

// Opens a URL in the user's default browser, swallowing any failure.
fun openUrl(inUrl: String) {
	runCatching {
		if (Desktop.isDesktopSupported()) {
			Desktop.getDesktop().browse(URI(inUrl))
		}
	}
}
