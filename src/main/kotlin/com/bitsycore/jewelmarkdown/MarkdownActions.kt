package com.bitsycore.jewelmarkdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// Editor text transformations used by the Markdown helper toolbar. Each returns a new
// TextFieldValue with an updated selection/caret so the editor stays usable afterwards.
object MarkdownActions {
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

	// Inserts inPrefix at the start of the line containing the caret (headings, lists, quotes).
	fun prefixLine(inValue: TextFieldValue, inPrefix: String): TextFieldValue {
		val vText = inValue.text
		val vCaret = inValue.selection.min
		val vSearchFrom = (vCaret - 1).coerceAtLeast(0)
		val vNewline = if (vText.isEmpty()) -1 else vText.lastIndexOf('\n', vSearchFrom)
		val vLineStart = if (vNewline < 0) 0 else vNewline + 1
		val vNewText = vText.substring(0, vLineStart) + inPrefix + vText.substring(vLineStart)
		return inValue.copy(text = vNewText, selection = TextRange(vCaret + inPrefix.length))
	}
}
