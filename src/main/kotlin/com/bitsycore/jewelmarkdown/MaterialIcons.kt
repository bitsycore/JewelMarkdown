package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

// Google's Material Icons Outlined font, bundled by the downloadMaterialIcons Gradle task and
// loaded from the classpath at first use. The font supports ligatures: writing the icon's
// name (e.g. "folder", "settings", "close") with this family in a Text composable renders
// the corresponding glyph at the requested font size. One source of icon truth for the app —
// avoids both bundling SVGs and hand-drawing canvas glyphs.
val MaterialIconsFont: FontFamily by lazy {
	FontFamily(
		Font(
			resource = "material-icons-outlined.otf",
			weight = FontWeight.Normal,
			style = FontStyle.Normal,
		)
	)
}

// Convenience: renders a Material Icons glyph via the ligature name (e.g. "folder", "menu").
// Defaults to a 16sp glyph in the theme's normal text color so a bare call works inside any
// button or row without manual styling. Pass inSize / inTint to customise.
@Composable
fun MaterialIcon(
	inName: String,
	inSize: TextUnit = 16.sp,
	inTint: Color = JewelTheme.globalColors.text.normal,
	inModifier: Modifier = Modifier,
) {
	Text(
		text = inName,
		fontFamily = MaterialIconsFont,
		fontSize = inSize,
		color = inTint,
		modifier = inModifier,
	)
}
