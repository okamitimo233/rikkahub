# Localization Guidelines

> How to manage Android string resources using `locale-tui`.

---

## Overview

RikkaHub supports 5 locales. When the user **explicitly requests** localization, use the `locale-tui` tool to manage string resources across all locales consistently.

---

## Supported Locales

| Locale | Directory | Description |
|--------|-----------|-------------|
| `values` | `app/src/main/res/values/` | English (default/source) |
| `values-zh` | `app/src/main/res/values-zh/` | Simplified Chinese |
| `values-zh-rTW` | `app/src/main/res/values-zh-rTW/` | Traditional Chinese |
| `values-ja` | `app/src/main/res/values-ja/` | Japanese |
| `values-ko-rKR` | `app/src/main/res/values-ko-rKR/` | Korean |

---

## When to Localize

| Situation | Action |
|-----------|--------|
| User does NOT request localization | Use hardcoded English strings: `Text("Hello")` |
| User explicitly requests localization | Use `/locale-tui-localization` skill |
| Adding a new feature with strings | Prioritize functionality first, localize later if asked |

---

## locale-tui Commands

### Add a New String

```bash
# Add with automatic translation to all locales
uv run --directory locale-tui src/main.py add <key> "<English Value>"

# Add to specific module
uv run --directory locale-tui src/main.py add <key> "<English Value>" -m app

# Add without translation (English only)
uv run --directory locale-tui src/main.py add <key> "<English Value>" --skip-translate
```

### Set a Specific Translation

```bash
# Set value for a specific language
uv run --directory locale-tui src/main.py set <key> "<Value>" -l <locale>

# Examples
uv run --directory locale-tui src/main.py set hello_world "Hello, World!" -l values
uv run --directory locale-tui src/main.py set hello_world "你好，世界！" -l values-zh
```

### List Existing Keys

```bash
uv run --directory locale-tui src/main.py list-keys [-m app]
```

---

## Naming Conventions

| Scope | Prefix | Example |
|-------|--------|---------|
| Page-specific | `<page>_page_` | `setting_page_title`, `chat_page_empty` |
| Common/shared | `common_` | `common_cancel`, `common_confirm` |
| Feature-specific | `<feature>_` | `assistant_name`, `provider_url` |

---

## Usage in Compose

```kotlin
import me.rerere.rikkahub.R

// In composable
Text(stringResource(R.string.setting_page_title))

// With format arguments
Text(stringResource(R.string.welcome_message, userName))
```

---

## Workflow

1. Implement the feature with hardcoded English strings first
2. When localization is requested, identify all user-facing strings
3. Use `locale-tui add` for each string key
4. Replace hardcoded strings with `stringResource(R.string.key)`
5. Verify all `values-*/strings.xml` files were updated

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Editing `strings.xml` files manually | Use `locale-tui` for consistent multi-locale updates |
| Localizing without being asked | Prioritize functionality; localize only when explicitly requested |
| Forgetting a locale | `locale-tui add` handles all configured locales automatically |
| Using non-English source values | Input to `locale-tui add` must be English |
