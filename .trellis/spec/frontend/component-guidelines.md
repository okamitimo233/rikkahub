# Component Guidelines

> How Jetpack Compose components are built in this project.

---

## Overview

All UI is built with Jetpack Compose. Components follow Material Design 3 principles and use stateless composable patterns wherever possible. The project uses `MaterialExpressiveTheme` with custom color extensions.

---

## Component Structure

### Standard Composable Pattern

```kotlin
@Composable
fun MyComponent(
    modifier: Modifier = Modifier,        // Always first optional parameter
    // Data parameters
    title: String,
    // Optional parameters with defaults
    enabled: Boolean = true,
    // Callbacks
    onClick: () -> Unit,
    // Content slots (trailing lambda)
    content: @Composable () -> Unit = {},
) {
    // Implementation using Material 3 components
}
```

**Real example** — `Tag` (`ui/components/ui/Tag.kt`):
```kotlin
@Composable
fun Tag(
    modifier: Modifier = Modifier,
    type: TagType = TagType.DEFAULT,
    onClick: (() -> Unit)? = null,
    children: @Composable RowScope.() -> Unit
)
```

### Page Composable Pattern

Pages are top-level `@Composable` functions that receive their ViewModel via Koin:

```kotlin
@Composable
fun SettingProviderPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Title") },
                navigationIcon = { BackButton() },
            )
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            // Page content
        }
    }
}
```

---

## Parameter Conventions

| Parameter Type | Convention | Example |
|---------------|-----------|---------|
| Modifier | First optional param, default `Modifier` | `modifier: Modifier = Modifier` |
| State/Data | After modifier | `title: String`, `items: List<Item>` |
| Boolean flags | Default value provided | `enabled: Boolean = true` |
| Callbacks | Prefixed with `on` | `onClick: () -> Unit` |
| Nullable callbacks | For optional interactions | `onClick: (() -> Unit)? = null` |
| Content slots | Trailing lambda | `content: @Composable () -> Unit` |
| Scoped content | Typed receiver | `children: @Composable RowScope.() -> Unit` |

---

## FormItem Pattern

`FormItem` (`ui/components/ui/Form.kt`) is the standard layout for settings and form rows. Used across 12+ pages.

```kotlin
FormItem(
    label = { Text("Label") },
    description = { Text("Optional description") },
    tail = {
        // Right-side widget (Switch, Icon, etc.)
        Switch(checked = enabled, onCheckedChange = { ... })
    }
) {
    // Optional expandable content below
}
```

**Structure**: Row with label+description column (`weight(1f)`) on left, `tail` slot on right.

**Reference**: `ui/pages/setting/SettingSearchPage.kt`, `ui/pages/assistant/detail/AssistantBasicPage.kt`

---

## Icon Library

The project uses **HugeIcons** (`me.rerere.hugeicons`):

```kotlin
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01

Icon(imageVector = HugeIcons.ArrowLeft01, contentDescription = null)
```

Custom icons for brands/special purposes live in `ui/components/ui/icons/` (e.g., `DiscordIcon.kt`, `Heart.kt`).

---

## Toast Messages

Use `LocalToaster` (backed by `com.dokar.sonner` library):

```kotlin
val toaster = LocalToaster.current

// Show toast
toaster.show("Operation successful", type = ToastType.Success)
toaster.show("Something went wrong", type = ToastType.Error)
```

Provided at root level in `RouteActivity.AppRoutes()`. Available in all composables.

---

## Dialog Pattern

### Confirm Dialog

```kotlin
RikkaConfirmDialog(
    show = showDialog,
    title = "Delete?",
    confirmText = "Delete",
    dismissText = "Cancel",
    onConfirm = { /* action */ },
    onDismiss = { showDialog = false },
    text = { Text("Are you sure?") }
)
```

### Edit Dialog (with useEditState)

```kotlin
val dialogState = useEditState<ProviderSetting> { confirmed ->
    onSave(confirmed)
}

// Open dialog
dialogState.open(ProviderSetting.OpenAI())

// In UI
if (dialogState.isEditing) {
    AlertDialog(
        onDismissRequest = { dialogState.dismiss() },
        confirmButton = {
            TextButton(onClick = { dialogState.confirm() }) { Text("Save") }
        },
        text = {
            // Edit form using dialogState.current
        }
    )
}
```

---

## Styling Patterns

- Use `MaterialTheme.colorScheme` for colors
- Use `MaterialTheme.typography` for text styles
- Extended colors available via `LocalExtendColors.current` (code colors, custom palette)
- Dark mode flag via `LocalDarkMode.current`
- Custom shape animations via `rememberAvatarShape()` hook
- Preset themes in `ui/theme/presets/` (Sakura, Ocean, Autumn, Black, Spring)

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Using Lucide icons | Use `HugeIcons` from `me.rerere.hugeicons` |
| Creating dialogs without `useEditState` | Use `useEditState<T>()` for edit/create dialogs that need state management |
| Hardcoding colors | Use `MaterialTheme.colorScheme` or `LocalExtendColors` |
| Using `println` for debugging | Use `LocalToaster` for user-facing messages, `Log` for debugging |
| Creating form rows from scratch | Use `FormItem` for consistent settings/form layouts |
