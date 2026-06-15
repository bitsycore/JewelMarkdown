package com.bitsycore.jewelmarkdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// Editor text transformations used by the Markdown helper toolbar and the editor's line
// shortcuts. Each function takes a TextFieldValue and returns a new one with updated text
// and selection/caret so the editor stays usable afterwards. Multi-line selections are
// honored everywhere a line-scoped operation makes sense.
object MarkdownActions {
	// ==================
	// MARK: Inline / line prefix
	// ==================

	// Wraps the current selection in inPrefix/inSuffix; if nothing is selected, inserts
	// inPlaceholder between them and selects it.
	fun wrap(inValue: TextFieldValue, inPrefix: String, inSuffix: String, inPlaceholder: String): TextFieldValue {
		val vText = inValue.text
		val vStart = inValue.selection.min
		val vEnd = inValue.selection.max
		val vSelected = vText.substring(vStart, vEnd)
		val vInner = if (vSelected.isEmpty()) inPlaceholder else vSelected
		val vNewText = vText.substring(0, vStart) + inPrefix + vInner + inSuffix + vText.substring(vEnd)
		val vInnerStart = vStart + inPrefix.length
		val vInnerEnd = vInnerStart + vInner.length
		return inValue.copy(text = vNewText, selection = TextRange(vInnerStart, vInnerEnd))
	}

	// Inserts inPrefix at the start of every line the selection touches (or just the caret's
	// line when there is no selection). Used for headings, bullets and block-quotes — applying
	// "- " to a multi-line selection bullet-points each line in one shot.
	fun prefixLine(inValue: TextFieldValue, inPrefix: String): TextFieldValue {
		val vText = inValue.text
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(vText, inValue.selection)
		val vBlock = vText.substring(vBlockStart, vBlockEnd)
		val vLineCount = vBlock.count { it == '\n' } + 1
		val vNewBlock = vBlock.split('\n').joinToString("\n") { inPrefix + it }
		val vNewText = vText.substring(0, vBlockStart) + vNewBlock + vText.substring(vBlockEnd)

		// First line's prefix shifts both selection ends by exactly one prefix length; every
		// subsequent line inside the selection adds another prefix's worth to the end side.
		val vNewStart = inValue.selection.start + inPrefix.length
		val vNewEnd = inValue.selection.end + inPrefix.length * vLineCount
		return inValue.copy(text = vNewText, selection = TextRange(vNewStart, vNewEnd))
	}

	// ==================
	// MARK: Line operations
	// ==================

	// Inserts a copy of the line (or block of lines) immediately below the current selection.
	// The selection follows the duplicated copy so successive presses keep duplicating.
	fun duplicateLines(inValue: TextFieldValue): TextFieldValue {
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(inValue.text, inValue.selection)
		val vBlock = inValue.text.substring(vBlockStart, vBlockEnd)
		val vInserted = "\n" + vBlock
		val vNewText = inValue.text.substring(0, vBlockEnd) + vInserted + inValue.text.substring(vBlockEnd)
		val vShift = vInserted.length
		return inValue.copy(
			text = vNewText,
			selection = TextRange(inValue.selection.start + vShift, inValue.selection.end + vShift),
		)
	}

	// Removes the line (or block of lines) entirely, collapsing the trailing newline so a
	// middle line vanishes cleanly. The caret lands at the start of the next line, or the end
	// of the preceding line when the document's last line is deleted.
	fun deleteLines(inValue: TextFieldValue): TextFieldValue {
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(inValue.text, inValue.selection)
		val vText = inValue.text
		val vRemoveEnd = if (vBlockEnd < vText.length && vText[vBlockEnd] == '\n') vBlockEnd + 1 else vBlockEnd
		val vRemoveStart =
			if (vRemoveEnd >= vText.length && vBlockStart > 0) vBlockStart - 1 else vBlockStart
		val vNewText = vText.substring(0, vRemoveStart) + vText.substring(vRemoveEnd)
		val vCaret = vRemoveStart.coerceAtMost(vNewText.length)
		return inValue.copy(text = vNewText, selection = TextRange(vCaret))
	}

	// Swaps the line (or block of lines) with the line immediately above; no-op when already
	// at the top of the document.
	fun moveLinesUp(inValue: TextFieldValue): TextFieldValue {
		val vText = inValue.text
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(vText, inValue.selection)
		if (vBlockStart == 0) return inValue
		val vPrevLineStart = vText.lastIndexOf('\n', vBlockStart - 2) + 1
		val vPrevLineEnd = vBlockStart - 1
		val vBefore = vText.substring(0, vPrevLineStart)
		val vPrevLine = vText.substring(vPrevLineStart, vPrevLineEnd)
		val vBlock = vText.substring(vBlockStart, vBlockEnd)
		val vAfter = vText.substring(vBlockEnd)
		val vNewText = vBefore + vBlock + "\n" + vPrevLine + vAfter
		val vShift = -(vBlockStart - vPrevLineStart)
		return inValue.copy(
			text = vNewText,
			selection = TextRange(inValue.selection.start + vShift, inValue.selection.end + vShift),
		)
	}

	// Swaps the line (or block of lines) with the line immediately below; no-op when already
	// at the bottom of the document.
	fun moveLinesDown(inValue: TextFieldValue): TextFieldValue {
		val vText = inValue.text
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(vText, inValue.selection)
		if (vBlockEnd >= vText.length) return inValue
		val vNextLineStart = vBlockEnd + 1
		val vNextLineEndIdx = vText.indexOf('\n', vNextLineStart)
		val vNextLineEnd = if (vNextLineEndIdx == -1) vText.length else vNextLineEndIdx
		val vBefore = vText.substring(0, vBlockStart)
		val vBlock = vText.substring(vBlockStart, vBlockEnd)
		val vNextLine = vText.substring(vNextLineStart, vNextLineEnd)
		val vAfter = vText.substring(vNextLineEnd)
		val vNewText = vBefore + vNextLine + "\n" + vBlock + vAfter
		val vShift = vNextLine.length + 1
		return inValue.copy(
			text = vNewText,
			selection = TextRange(inValue.selection.start + vShift, inValue.selection.end + vShift),
		)
	}

	// Expands the current selection to cover every full line it touches.
	fun selectLines(inValue: TextFieldValue): TextFieldValue {
		val (vBlockStart, vBlockEnd) = lineBoundsForSelection(inValue.text, inValue.selection)
		return inValue.copy(selection = TextRange(vBlockStart, vBlockEnd))
	}

	// ==================
	// MARK: Helpers
	// ==================

	// Finds the [start, end) character offsets of the smallest contiguous range of full lines
	// that contains the selection. A selection that ends exactly on a line start (just after a
	// newline) does not include that following line — matching the IntelliJ/VSCode convention.
	private fun lineBoundsForSelection(inText: String, inSelection: TextRange): Pair<Int, Int> {
		val vMin = inSelection.min
		val vMax = inSelection.max
		val vStart = inText.lastIndexOf('\n', vMin - 1) + 1
		val vSearchFrom =
			if (vMax > vMin && vMax > 0 && inText[vMax - 1] == '\n') vMax - 1 else vMax
		val vEndIdx = inText.indexOf('\n', vSearchFrom)
		val vEnd = if (vEndIdx == -1) inText.length else vEndIdx
		return vStart to vEnd
	}
}
