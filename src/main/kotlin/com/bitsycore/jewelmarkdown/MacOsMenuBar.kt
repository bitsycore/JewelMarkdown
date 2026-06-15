package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.SwingUtilities

// Whether the JVM is running on macOS. Used to decide whether to install a screen-menu-bar and
// to hide the duplicate in-app menus when it's active.
val kIsMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

// Installs a native Swing JMenuBar on the current window for the duration of the calling
// composable. Apple's "screen menu bar" mode (set in main() before any UI is built) routes it
// straight to the macOS menu bar, matching the typical macOS UX. Works in both JBR-decorated
// and OS-decorated window modes because both produce a ComposeWindow (a JFrame subclass).
@Composable
fun FrameWindowScope.InstallMacOsMenuBar(inState: AppState) {
	if (!kIsMac) return
	val vFrame = window
	DisposableEffect(inState, vFrame) {
		val vMenuBar = buildMenuBar(inState)
		SwingUtilities.invokeLater { vFrame.jMenuBar = vMenuBar }
		onDispose {
			SwingUtilities.invokeLater { vFrame.jMenuBar = null }
		}
	}
}

// Constructs the full File / Edit / View / Help menu hierarchy, wiring each item to the same
// runShortcutAction handler used by the in-app menus so behavior stays in sync.
private fun buildMenuBar(inState: AppState): JMenuBar {
	val vBar = JMenuBar()
	vBar.add(
		menu("File") {
			actionItem(it, inState, ShortcutAction.NewFile)
			actionItem(it, inState, ShortcutAction.OpenFile)
			actionItem(it, inState, ShortcutAction.OpenFolder)
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.Save)
			actionItem(it, inState, ShortcutAction.SaveAs)
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.CloseTab)
			rawItem(it, "Close all tabs") { inState.closeAll() }
		}
	)
	vBar.add(
		menu("Edit") {
			actionItem(it, inState, ShortcutAction.Bold)
			actionItem(it, inState, ShortcutAction.Italic)
			actionItem(it, inState, ShortcutAction.InlineCode)
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.Heading)
			actionItem(it, inState, ShortcutAction.BulletList)
			actionItem(it, inState, ShortcutAction.Quote)
			actionItem(it, inState, ShortcutAction.Link)
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.DuplicateLine)
			actionItem(it, inState, ShortcutAction.DeleteLine)
			actionItem(it, inState, ShortcutAction.MoveLineUp)
			actionItem(it, inState, ShortcutAction.MoveLineDown)
			actionItem(it, inState, ShortcutAction.SelectLine)
		}
	)
	vBar.add(
		menu("View") {
			actionItem(it, inState, ShortcutAction.ViewEditor)
			actionItem(it, inState, ShortcutAction.ViewSplit)
			actionItem(it, inState, ShortcutAction.ViewPreview)
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.ToggleProjectPanel)
			rawItem(it, "Status Bar") { inState.settings.showStatusBar = !inState.settings.showStatusBar }
			it.addSeparator()
			actionItem(it, inState, ShortcutAction.OpenSettings)
		}
	)
	vBar.add(
		menu("Help") {
			rawItem(it, "Open example") { inState.openDemo() }
			it.addSeparator()
			rawItem(it, "View on GitHub") { openUrl("https://github.com/bitsycore/JewelMarkdown") }
		}
	)
	return vBar
}

// Builds and returns a JMenu populated by the given block.
private fun menu(inLabel: String, inBlock: (JMenu) -> Unit): JMenu {
	val vMenu = JMenu(inLabel)
	inBlock(vMenu)
	return vMenu
}

// Adds a menu item bound to a ShortcutAction. The action's currently-bound shortcut label is
// appended to the title as a hint — we deliberately do NOT set a Swing accelerator, because
// the Compose key handler already dispatches these and a double binding would fire twice.
private fun actionItem(inMenu: JMenu, inState: AppState, inAction: ShortcutAction) {
	val vBinding = inState.keymap[inAction]?.label()
	val vLabel = if (vBinding == null) inAction.displayName else "${inAction.displayName}   $vBinding"
	val vItem = JMenuItem(vLabel)
	vItem.addActionListener { runShortcutAction(inState, inAction) }
	inMenu.add(vItem)
}

// Adds a menu item that runs an arbitrary block on click.
private fun rawItem(inMenu: JMenu, inLabel: String, inOnClick: () -> Unit) {
	val vItem = JMenuItem(inLabel)
	vItem.addActionListener { inOnClick() }
	inMenu.add(vItem)
}
