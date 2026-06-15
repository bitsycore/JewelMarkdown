# Jewel Markdown

A **Compose for Desktop** Markdown editor with a live preview, built on the JetBrains
**Jewel** UI toolkit (IntelliJ IDE look-and-feel). Cross-platform — macOS, Windows, Linux —
runs on either the JetBrains Runtime (custom title bar, "Islands" look) or any standard JDK
21+ (OS-decorated window, same functionality).

## Highlights

- **Islands UI**. Title bar / activity bar / status bar sit flat on a per-theme vertical
  gradient. The project pane and the editor are two rounded "island" cards floating on top —
  the layout JetBrains shipped in IntelliJ 2025.
- **Multi-tab editing**. Open many files, drag-reorder tabs, right-click for Close / Close
  Others / Close All. Drop files onto the window to open them. The set of open tabs is
  persisted across runs (opt-out in Settings → Behavior).
- **Live preview** via Jewel's Markdown renderer with GitHub-flavored extensions: tables,
  alerts, ~~strikethrough~~, autolinks. **Mermaid diagrams** render inline through an
  embedded JavaFX WebView using a bundled offline `mermaid.min.js`. Ctrl+Click opens links.
- **Editor**: live Markdown syntax highlighting, change gutter (per-line stripes for edits
  since last save) and a line-number gutter that respects soft-wrapped lines (one number per
  logical source line). Word-wrap is a toggle (Settings → Editor).
- **Welcome / empty state** with New / Open / Open folder shortcuts.
- **Project panel** with a file tree of the opened folder; click a Markdown/text file to
  open it in a tab. Active file is highlighted.
- **Editor controls** (IntelliJ / VSCode conventions): duplicate line, delete line, move
  line up/down, select line — all support multi-line selections.
- **macOS native menu bar**: File / Edit / View / Help routed to the system menu bar via
  Swing's `JMenuBar`. Works in both JBR and OS-decorated window modes.
- **Right-click menu** themed to match the app (panel background, rounded outline, real
  divider before "Select All").
- **Settings dialog** (Appearance / Editor / Behavior / Shortcuts / About): theme,
  background gradient presets, panel corners & spacing, editor font and size, word wrap,
  status bar, heap-usage indicator, session restore, exit on last-tab-close, OS-decorated
  fallback. Shortcuts are fully rebindable; defaults follow the platform — `⌘…` on macOS,
  `Ctrl+…` everywhere else.
- **Status bar** with caret position, dirty state, document metrics, and JVM heap usage
  (clickable to GC).
- Preferences, keymap, gradient pick and split ratio all **persist across runs**.

## Running

```bash
./gradlew run
```

Pass `--demo` to open the bundled example document at startup:

```bash
./gradlew run --args="--demo"
```

## Packaging

Native installer for the current OS:

```bash
./gradlew packageReleaseDistributionForCurrentOS
```

Outputs land in `build/compose/binaries/main-release/`:

| OS      | Format(s) produced                         |
|---------|--------------------------------------------|
| macOS   | `.dmg` (+ `.app` bundle in `app/`)         |
| Windows | `.msi` (+ exe in `app/JewelMarkdown/`)     |
| Linux   | `.deb`, `.rpm` (+ runnable folder in `app/`) |

The standalone runnable folder/`.app` from `app/` works without an installer — just zip it
and ship.

## Releases

Tagging a commit `vX.Y.Z` (e.g. `v1.0.0`) triggers
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds installers
and standalone bundles for every desktop platform and attaches them to a GitHub Release:

- macOS: `.dmg` installer, zipped `.app` standalone
- Windows: `.msi` installer, zipped standalone folder
- Linux: `.deb` installer, `.rpm` installer, `.AppImage`, gzipped standalone tarball

## Requirements

The build declares a `vendor = JETBRAINS` Java toolchain and the **foojay** resolver
downloads JBR 21 automatically on first build — no manual JDK setup needed. The JBR is
required when running with Jewel's `DecoratedWindow` (custom title bar). On a non-JBR JDK
the app transparently falls back to a standard OS-decorated Compose `Window` with the same
menus and features.

## Tech stack

| Component        | Version                  |
|------------------|--------------------------|
| Kotlin           | 2.2.0                    |
| Compose Desktop  | 1.10.3                   |
| Jewel            | 0.34.0-253.32098.101     |
| JavaFX (Mermaid) | 21.0.4                   |
| Material Icons   | OTF font from Google     |
| Gradle           | 9.5.0                    |
| Runtime          | JetBrains Runtime 21     |

## Project structure

```
src/main/kotlin/com/bitsycore/jewelmarkdown/
  Main.kt             application(), Jewel theme, decorated/fallback window, log filters
  AppState.kt         documents/tabs, theme, view mode, session-restore wiring
  AppContent.kt       title-bar menus, tabs, editor & preview cards, gutters, settings dialog
  MarkdownPreview.kt  ProvideMarkdownStyling + LazyMarkdown with GFM extensions
  MarkdownActions.kt  Markdown text transforms + line ops (duplicate/delete/move/select)
  Mermaid.kt          JavaFX WebView pool, mermaid.js loader, Jewel custom-block renderer
  ContextMenu.kt      themed right-click menu (Jewel ContextMenuDivider aware)
  MacOsMenuBar.kt     installs the macOS system menu bar via Swing JMenuBar
  MaterialIcons.kt    Material Icons font family + MaterialIcon(name) helper
  Shortcuts.kt        ShortcutAction enum, Shortcut data class, defaultKeymap
  Settings.kt         Settings class, GradientPreset, EditorFont
  Persistence.kt      saves/loads settings, session, keymap to ~/.jewelmarkdown/
  ProjectPanel.kt     activity bar + file-tree project panel
  SampleContent.kt    example document
  SyntaxHighlighting.kt  in-editor Markdown VisualTransformation
  MarkdownStyles.kt   pane/code/alert styles for the preview
  AlertStyles.kt      GitHub-style alert styling
  FileOps.kt          native open/save dialogs, openUrl
```

## License

[MIT](LICENSE). © 2026 Bitsy.
