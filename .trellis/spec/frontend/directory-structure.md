# Directory Structure

> How frontend (Jetpack Compose UI) code is organized in this project.

---

## Overview

RikkaHub is a native Android app using Jetpack Compose for all UI. The UI layer lives under `app/src/main/java/me/rerere/rikkahub/ui/`. Features are organized by page, with shared components in a separate directory.

---

## Directory Layout

```
ui/
├── activity/                 # Non-route activities (e.g., ShortcutHandlerActivity)
├── components/               # Reusable shared components
│   ├── ai/                   # AI-specific components (ChatInput, ModelList, etc.)
│   ├── message/              # Chat message rendering (ChatMessage, actions, branching, tools)
│   ├── nav/                  # Navigation components (BackButton)
│   ├── richtext/             # Rich text rendering (Markdown, LaTeX, Mermaid, code blocks)
│   ├── table/                # Data table components
│   ├── ui/                   # Generic UI building blocks (Form, Dialog, Tag, Select, etc.)
│   │   ├── icons/            # Custom icon composables (DiscordIcon, Heart, etc.)
│   │   └── permission/       # Permission handling (state, dialog, manager)
│   └── webview/              # WebView wrapper
├── context/                  # CompositionLocal providers (settings, nav, toaster, TTS)
├── hooks/                    # Custom Composable "hooks" (state helpers, debounce, lifecycle)
├── modifier/                 # Custom Modifier extensions (Clickable, Shimmer)
├── pages/                    # Feature screens (each with Page + ViewModel)
│   ├── assistant/            # Assistant list and detail pages
│   ├── backup/               # Backup/restore (with tabs/ and components/ subdirs)
│   ├── chat/                 # Main chat interface
│   ├── history/              # Conversation history
│   ├── setting/              # Settings pages (with components/ subdir)
│   ├── ...                   # Other feature pages
│   └── webview/              # In-app web viewer
└── theme/                    # Material 3 theming (colors, typography, preset themes)
    └── presets/              # Custom theme presets (Sakura, Ocean, Autumn, etc.)
```

---

## Module Organization

### Page Structure

Each feature page has its own directory under `ui/pages/<feature>/`:

```
pages/<feature>/
├── <Feature>Page.kt          # Top-level @Composable screen function
├── <Feature>VM.kt             # ViewModel for the page
├── components/                # (Optional) Page-specific components
└── detail/                    # (Optional) Sub-pages / detail views
```

**Examples:**
- `pages/setting/` — `SettingPage.kt` + `SettingVM.kt` + `components/ProviderConfigure.kt`
- `pages/chat/` — `ChatPage.kt` + `ChatVM.kt` + `Background.kt` + `Export.kt`
- `pages/assistant/detail/` — Multiple sub-pages for assistant configuration

### Shared Components

Reusable components go in `ui/components/` organized by domain:
- **`ai/`** — AI-specific widgets (ChatInput, AssistantPicker, ModelList)
- **`message/`** — Chat message display components
- **`richtext/`** — Markdown, LaTeX, code blocks, mermaid diagrams
- **`ui/`** — Generic building blocks (FormItem, Dialog, Tag, Select, Switch)

### When to Create a New Subdirectory

- A new feature page: always gets its own directory under `pages/`
- A reusable component used by multiple pages: goes in `components/`
- A component only used by one page: lives in that page's directory or `components/` subdir

---

## Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Page file | `<Feature>Page.kt` | `ChatPage.kt`, `SettingPage.kt` |
| ViewModel file | `<Feature>VM.kt` | `ChatVM.kt`, `SettingVM.kt` |
| Component file | `PascalCase.kt` | `FormItem.kt`, `BackButton.kt` |
| Hook file | `PascalCase.kt` or `Use<Name>.kt` | `Debounce.kt`, `UseEditState.kt` |
| Modifier file | `PascalCase.kt` | `Shimmer.kt`, `Clickable.kt` |
| Context file | `Local<Name>.kt` or `<Name>Context.kt` | `LocalSettings.kt`, `ToasterContext.kt` |
| Theme file | `PascalCase.kt` | `Theme.kt`, `Color.kt`, `Type.kt` |

---

## Examples

- **Well-organized feature page**: `ui/pages/setting/` — Settings with sub-pages, shared ViewModel, and local components
- **Complex page with sub-pages**: `ui/pages/assistant/detail/` — Multiple assistant config screens sharing `AssistantDetailVM`
- **Shared component**: `ui/components/ui/Form.kt` — `FormItem` used across 12+ pages
- **Custom hook**: `ui/hooks/UseEditState.kt` — `useEditState<T>()` used across settings pages
