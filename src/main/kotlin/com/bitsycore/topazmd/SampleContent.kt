package com.bitsycore.topazmd

// Default document shown on first launch. It doubles as a feature showcase and
// exercises every renderer path used by the preview pane.
internal const val kSampleMarkdown = """# TopazMD

A **Compose for Desktop** Markdown editor built with the JetBrains **Jewel** UI toolkit.
Type on the left, see it rendered live on the right.

## Features

- Live preview powered by Jewel's own Markdown renderer
- Dark theme by default, with a light/dark toggle in the title bar
- Open, edit and save Markdown files
- GitHub-flavored Markdown: tables, alerts, ~~strikethrough~~ and autolinks

## Formatting showcase

You can use *italics*, **bold**, `inline code`, and [links](https://github.com/JetBrains/jewel).

> A blockquote sets some text apart from the rest of the document.

### Lists

1. First item
2. Second item
   - Nested bullet
   - Another bullet
3. Third item

### Code

```kotlin
fun greet(name: String) {
    println("Hello, " + name + "!")
}
```

### Table

| Feature   | Supported |
|-----------|:---------:|
| Headings  |    Yes    |
| Tables    |    Yes    |
| Alerts    |    Yes    |

### Alerts

> [!NOTE]
> Jewel renders GitHub-style alerts in the preview.

> [!WARNING]
> Edit this text and watch the preview update instantly.

### Diagrams

Mermaid blocks render inline through an embedded browser:

```mermaid
graph TD
    A[Type Markdown] --> B{Live preview}
    B --> C[Rendered text]
    B --> D[Mermaid diagram]
```

Bare links autolink too: https://www.jetbrains.com
"""
