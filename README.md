# Jewel Markdown

A **Compose for Desktop** Markdown editor and live visualizer, built with the JetBrains
**Jewel** UI toolkit (the IntelliJ look-and-feel) and a custom **decorated window**.
Dark theme by default.

## Features

- **Decorated window** with a custom Jewel title bar (theme toggle lives there).
- **Split view**: raw Markdown editor on the left, live rendered preview on the right.
  Switch between **Editor / Split / Preview** from the toolbar.
- **Live preview** rendered by Jewel's own Markdown renderer, so it matches the IntelliJ
  theme. GitHub-flavored Markdown is enabled: **tables, alerts, ~~strikethrough~~,
  autolinks**, plus the usual headings/lists/code/quotes/links.
- **Dark theme by default**, with a one-click light/dark toggle.
- **File actions**: New, Open, Save, Save As (native dialogs), with an unsaved-changes
  indicator and a status bar (path, line/word/char counts).

## Requirements

This app uses Jewel's `DecoratedWindow`, which **only works on the JetBrains Runtime
(JBR)** — on a stock JDK it throws at startup. You don't need to install JBR yourself:
the build declares a `vendor = JETBRAINS` Java toolchain and the **foojay** resolver
downloads JBR 21 automatically on first build. Both compilation and `gradlew run` use it
(`run` is pinned to the JBR via `compose.desktop.application.javaHome`).

## Run

```bash
./gradlew run
```

## Package a native installer

```bash
./gradlew packageDistributionForCurrentOS   # .msi on Windows, .dmg on macOS, .deb on Linux
```

## Tech stack

| Component        | Version                  |
|------------------|--------------------------|
| Kotlin           | 2.2.0                    |
| Compose Desktop  | 1.10.3                   |
| Jewel            | 0.34.0-253.32098.101     |
| Gradle           | 9.4.1                    |
| Runtime          | JetBrains Runtime 21     |

## Project structure

```
src/main/kotlin/com/bitsycore/jewelmarkdown/
  Main.kt             application(), Jewel theme, DecoratedWindow + custom TitleBar
  AppContent.kt       toolbar, view-mode switch, editor/preview split, status bar
  MarkdownPreview.kt  ProvideMarkdownStyling + LazyMarkdown with GFM, off-thread parsing
  AppState.kt         document state (TextFieldState), dirty tracking, theme + view mode
  FileOps.kt          native open/save dialogs, open-URL helper
  SampleContent.kt    default showcase document
```

See `PROGRESS.md` for the design decisions and how the exact Jewel API/coordinates were
verified.
