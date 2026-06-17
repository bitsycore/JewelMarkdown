package com.bitsycore.topazmd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import java.io.File

// File extensions shown in the project tree (text/Markdown documents).
private val kTextExtensions = setOf("md", "markdown", "mdx", "txt")

// ==================
// MARK: Activity bar
// ==================

// Thin far-left strip of tool-window toggles (IntelliJ-style). Currently a single button to
// show/hide the project files panel.
@Composable
internal fun ActivityBar(inState: AppState) {
	// Vertical padding matches the islands' top/bottom (2× edgeGap) so the first icon sits at
	// the same vertical position as the islands' top edge. Left padding (1× edgeGap) mirrors
	// the islands' `start` padding so the activity bar isn't flush against the window edge.
	val vEdge = inState.settings.edgeGapDp.dp
	Column(
		modifier = Modifier.fillMaxHeight().width(44.dp + vEdge).padding(start = vEdge, top = vEdge * 2, bottom = vEdge * 2),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		ActivityButton(
			inSelected = inState.showProjectPanel,
			inTooltip = "Project files",
			inOnClick = { inState.showProjectPanel = !inState.showProjectPanel },
		) { vTint -> MaterialIcon("folder", inSize = 20.sp, inTint = vTint) }
	}
}

// A square, highlight-on-select activity-bar button hosting a drawn icon.
@Composable
private fun ActivityButton(
	inSelected: Boolean,
	inTooltip: String,
	inOnClick: () -> Unit,
	inIcon: @Composable (Color) -> Unit,
) {
	val vInteraction = remember { MutableInteractionSource() }
	val vHovered by vInteraction.collectIsHoveredAsState()
	val vTint = if (inSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info
	val vBg =
		when {
			inSelected -> JewelTheme.globalColors.text.info.copy(alpha = 0.18f)
			vHovered -> JewelTheme.globalColors.text.info.copy(alpha = 0.10f)
			else -> Color.Transparent
		}
	Tooltip(tooltip = { Text(inTooltip) }) {
		Box(
			modifier =
				Modifier
					.size(32.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(vBg)
					.hoverable(vInteraction)
					.clickable(onClick = inOnClick),
			contentAlignment = Alignment.Center,
		) {
			inIcon(vTint)
		}
	}
}


// ==================
// MARK: Project panel
// ==================

// Collapsible left panel showing the opened folder's Markdown/text files as a tree.
@Composable
internal fun ProjectPanel(inState: AppState, inModifier: Modifier) {
	val vRoot = inState.projectRoot
	val vBorder = JewelTheme.globalColors.borders.normal
	Column(inModifier) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				(vRoot?.name ?: "No folder").uppercase(),
				color = JewelTheme.globalColors.text.info,
				fontSize = 11.sp,
				letterSpacing = 0.5.sp,
			)
			Spacer(Modifier.weight(1f))
			OutlinedButton(onClick = { chooseFolder()?.let { inState.projectRoot = it } }) { Text("Open…") }
		}
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)

		if (vRoot == null) {
			Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
				OutlinedButton(onClick = { chooseFolder()?.let { inState.projectRoot = it } }) { Text("Open Folder") }
			}
		} else {
			val vExpanded = remember(vRoot.absolutePath) { mutableStateMapOf<String, Boolean>() }
			VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
				Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
					for (vChild in childrenOf(vRoot)) {
						FileTreeNode(vChild, 0, vExpanded, inState)
					}
				}
			}
		}
	}
}

// One tree node: a directory (expandable) or a document file (opens in a tab when clicked).
@Composable
private fun FileTreeNode(inFile: File, inDepth: Int, inExpanded: SnapshotStateMap<String, Boolean>, inState: AppState) {
	if (inFile.isDirectory) {
		val vKey = inFile.absolutePath
		val vIsOpen = inExpanded[vKey] == true
		TreeRow(inDepth, inFile.name, inIsDir = true, inIsOpen = vIsOpen, inIsActive = false) {
			inExpanded[vKey] = !vIsOpen
		}
		if (vIsOpen) {
			for (vChild in childrenOf(inFile)) {
				FileTreeNode(vChild, inDepth + 1, inExpanded, inState)
			}
		}
	} else {
		val vIsActive = inState.active?.file?.absolutePath == inFile.absolutePath
		TreeRow(inDepth, inFile.name, inIsDir = false, inIsOpen = false, inIsActive = vIsActive) {
			inState.openFile(inFile)
		}
	}
}

// A single indented, clickable tree row with a disclosure triangle for directories. The active
// file is highlighted with a rounded background that's inset 4dp from the panel edges, matching
// IntelliJ's selection style.
@Composable
private fun TreeRow(
	inDepth: Int,
	inLabel: String,
	inIsDir: Boolean,
	inIsOpen: Boolean,
	inIsActive: Boolean,
	inOnClick: () -> Unit,
) {
	val vShape = RoundedCornerShape(6.dp)
	val vAccent = JewelTheme.globalColors.borders.focused
	val vBg = if (inIsActive) vAccent.copy(alpha = 0.18f) else Color.Transparent
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.padding(horizontal = 4.dp)
				.clip(vShape)
				.background(vBg)
				.clickable(onClick = inOnClick)
				.padding(start = (4 + inDepth * 14).dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
			if (inIsDir) {
				MaterialIcon(
					inName = if (inIsOpen) "expand_more" else "chevron_right",
					inSize = 14.sp,
					inTint = JewelTheme.globalColors.text.info,
				)
			}
		}
		Text(
			inLabel,
			fontSize = 13.sp,
			color = if (inIsDir || inIsActive) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info,
		)
	}
}

// Directory children to show: sub-directories and Markdown/text files, dirs first, sorted by
// name, hidden entries excluded.
private fun childrenOf(inDir: File): List<File> =
	(inDir.listFiles()?.asList().orEmpty())
		.filter { !it.isHidden && (it.isDirectory || it.extension.lowercase() in kTextExtensions) }
		.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
