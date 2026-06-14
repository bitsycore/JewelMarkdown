package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color as FxColor
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.ui.component.Text

// Mermaid diagrams are rendered through an embedded JavaFX WebView (WebKit) — small footprint,
// no native conflict with JBR's bundled JCEF, fully offline once mermaid.min.js is bundled.
// mermaid.min.js is downloaded into resources at build time by the `downloadMermaid` Gradle task.

// Status of the JavaFX runtime, observed by the renderer to switch between a placeholder and an
// actual WebView-backed view.
enum class MermaidStatus { NotStarted, Initializing, Ready, Failed }

// Global handle to JavaFX readiness — JFXPanel implicitly boots the JavaFX platform on first
// construction, but constructing it has thread/AWT side effects we want to do exactly once.
object MermaidRuntime {
	var status by mutableStateOf(MermaidStatus.NotStarted)
		private set

	// While suppressed, MermaidView falls back to the placeholder instead of mounting a
	// JFXPanel. The Settings overlay sets this so the heavyweight WebView doesn't eat the
	// outside-tap that would otherwise dismiss the modal.
	var suppressed by mutableStateOf(false)

	// Cached mermaid.min.js body, loaded from resources on first use.
	private val mermaidJs: String by lazy {
		Mermaid::class.java.getResourceAsStream("/mermaid.min.js")
			?.bufferedReader()
			?.use { it.readText() }
			?: ""
	}

	// Triggers JavaFX initialization in the background. JFXPanel() must be created on the EDT;
	// once any JFXPanel exists the JavaFX Application Thread is running, and Platform.runLater
	// works for the rest of the JVM's lifetime.
	suspend fun ensureInitialized() {
		if (status != MermaidStatus.NotStarted) return
		status = MermaidStatus.Initializing
		withContext(Dispatchers.IO) {
			runCatching {
				// Keep JavaFX alive even when all JFXPanels are temporarily detached during recomposition.
				javax.swing.SwingUtilities.invokeAndWait { JFXPanel() }
				Platform.setImplicitExit(false)
				status = MermaidStatus.Ready
			}.onFailure { status = MermaidStatus.Failed }
		}
	}

	// Builds the HTML wrapper that renders a single Mermaid diagram via the bundled mermaid.js.
	fun buildHtml(inSource: String, inIsDark: Boolean): String {
		val vTheme = if (inIsDark) "dark" else "default"
		val vFg = if (inIsDark) "#e0e0e0" else "#1f1f1f"
		val vEscaped =
			inSource
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
		// The page itself is transparent so it composites onto the Compose pane behind the
		// WebView; that avoids the white flash when the pane scrolls under the heavyweight
		// JavaFX surface. The diagram's own colors come from Mermaid's chosen theme.
		return """
			<!doctype html>
			<html>
			<head>
				<meta charset="utf-8">
				<style>
					html, body { margin: 0; padding: 0; background: transparent; color: $vFg; overflow: hidden; }
					body { display: flex; align-items: center; justify-content: center; height: 100vh; }
					::-webkit-scrollbar { display: none; }
					.mermaid { font-family: -apple-system, system-ui, sans-serif; max-width: 100%; max-height: 100%; }
					.mermaid svg { max-width: 100%; max-height: 100vh; height: auto; display: block; }
				</style>
			</head>
			<body>
				<pre class="mermaid">$vEscaped</pre>
				<script>$mermaidJs</script>
				<script>mermaid.initialize({ startOnLoad: true, theme: '$vTheme', securityLevel: 'loose' });</script>
			</body>
			</html>
		""".trimIndent()
	}
}

// Convenient anchor for class-loader resource lookups.
private class Mermaid

// A MarkdownBlock that carries the raw Mermaid diagram source. Built by post-processing Jewel's
// parsed block list (replacing FencedCodeBlocks whose language is "mermaid").
class MermaidBlock(val source: String) : MarkdownBlock.CustomBlock

// Jewel extension entry point: contributes the block renderer below.
class MermaidJewelExtension(private val inIsDark: Boolean) : MarkdownRendererExtension {
	override val blockRenderer: MarkdownBlockRendererExtension = MermaidBlockRenderer(inIsDark)
}

// Renders MermaidBlocks by hosting a JavaFX WebView in a SwingPanel. While JavaFX is still
// warming up the block falls back to a plain code listing so the user can still see the source.
private class MermaidBlockRenderer(private val inIsDark: Boolean) : MarkdownBlockRendererExtension {
	override fun canRender(block: MarkdownBlock.CustomBlock): Boolean = block is MermaidBlock

	@Composable
	override fun RenderCustomBlock(
		block: MarkdownBlock.CustomBlock,
		blockRenderer: MarkdownBlockRenderer,
		inlineRenderer: InlineMarkdownRenderer,
		enabled: Boolean,
		modifier: Modifier,
		onUrlClick: (String) -> Unit,
	) {
		val vMermaid = block as MermaidBlock
		MermaidView(vMermaid.source, inIsDark, modifier)
	}
}

// Embeds a single Mermaid diagram. Recreates the WebView's content whenever the source or theme
// changes so the diagram tracks document edits and dark/light switches.
@Composable
fun MermaidView(inSource: String, inIsDark: Boolean, inModifier: Modifier = Modifier) {
	val vStatus = MermaidRuntime.status
	val vBorderColor = JewelTheme.globalColors.borders.normal
	val vShape = RoundedCornerShape(6.dp)

	Box(
		modifier =
			inModifier
				.fillMaxWidth()
				.height(280.dp)
				.clip(vShape)
				.border(1.dp, vBorderColor, vShape)
				.background(JewelTheme.globalColors.panelBackground),
	) {
		when {
			// While a modal overlay is up, render nothing so clicks fall through to Compose
			// (the heavyweight JFXPanel would otherwise swallow the outside-tap-to-dismiss).
			MermaidRuntime.suppressed -> {}
			vStatus == MermaidStatus.Ready -> MermaidWebView(inSource, inIsDark)
			vStatus == MermaidStatus.Failed -> MermaidPlaceholder(inSource, "WebView failed to start. Showing source instead.")
			else -> MermaidPlaceholder(inSource, "Preparing the Mermaid renderer…")
		}
	}
}

// Hosts the actual JavaFX WebView via SwingPanel. The panel itself is stable across
// recompositions; only the loaded HTML changes when the source or theme update. Every layer in
// the JFX stack is made transparent so any heavyweight repaint blends with the Compose pane
// behind it instead of flashing white during scroll.
@Composable
private fun MermaidWebView(inSource: String, inIsDark: Boolean) {
	val vHtml = remember(inSource, inIsDark) { MermaidRuntime.buildHtml(inSource, inIsDark) }
	val vPanel =
		remember {
			JFXPanel().apply {
				isOpaque = false
				background = java.awt.Color(0, 0, 0, 0)
			}
		}
	val vWebView = remember { arrayOfNulls<WebView>(1) }

	LaunchedEffect(vHtml) {
		Platform.runLater {
			val vExisting = vWebView[0]
			if (vExisting == null) {
				val vNew = WebView().apply {
					isContextMenuEnabled = false
					style = "-fx-background-color: transparent;"
				}
				vWebView[0] = vNew
				vPanel.scene = Scene(vNew).apply { fill = FxColor.TRANSPARENT }
				vNew.engine.loadContent(vHtml)
			} else {
				vExisting.engine.loadContent(vHtml)
			}
		}
	}

	SwingPanel(
		modifier = Modifier.fillMaxWidth(),
		factory = { vPanel },
	)
}

// Pre-render fallback content: shows the diagram source in muted text so the user still sees
// something useful while JavaFX warms up or if it failed entirely.
@Composable
private fun MermaidPlaceholder(inSource: String, inMessage: String) {
	Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
		val vMuted = JewelTheme.globalColors.text.info
		androidx.compose.foundation.layout.Column {
			Text(inMessage, color = vMuted, fontSize = 12.sp)
			Box(Modifier.padding(top = 8.dp)) {
				Text(inSource, color = vMuted, fontSize = 12.sp)
			}
		}
	}
}

// Walks the parsed Markdown blocks and swaps each fenced code block whose language is
// "mermaid" for a MermaidBlock. Block-quotes are recursed into so quoted diagrams still render.
fun transformMermaidBlocks(inBlocks: List<MarkdownBlock>): List<MarkdownBlock> {
	return inBlocks.map { vBlock ->
		when (vBlock) {
			is MarkdownBlock.CodeBlock.FencedCodeBlock ->
				if (vBlock.language.equals("mermaid", ignoreCase = true)) {
					MermaidBlock(vBlock.content)
				} else {
					vBlock
				}
			is MarkdownBlock.BlockQuote -> MarkdownBlock.BlockQuote(transformMermaidBlocks(vBlock.children))
			else -> vBlock
		}
	}
}

// Convenience used by Main: triggers JavaFX init in the application's coroutine scope so the
// preview is ready by the time the user types or opens a file containing a Mermaid block.
@Composable
fun RememberMermaidInit() {
	LaunchedEffect(Unit) {
		MermaidRuntime.ensureInitialized()
	}
}
