# Quality Guidelines

> Code quality standards for frontend (Compose UI) development.

---

## Overview

The project uses Jetpack Compose with Material Design 3, targeting Android SDK 36 (min 26). Quality is maintained through consistent patterns, Koin DI, and unit tests for critical logic.

---

## Forbidden Patterns

| Pattern | Why | Alternative |
|---------|-----|------------|
| Using Lucide icons (`com.composables.icons.lucide`) | Not the icon library used | Use `HugeIcons` from `me.rerere.hugeicons` |
| `collectAsState()` | Doesn't respect Android lifecycle | Use `collectAsStateWithLifecycle()` |
| Direct network calls in composables | Violates separation of concerns | Use ViewModel + Repository |
| Hardcoded colors | Breaks theming | Use `MaterialTheme.colorScheme` or `LocalExtendColors` |
| Creating new Koin modules per feature | Module bloat | Add to existing modules in `di/` |
| `mutableStateOf` for repository data | Doesn't propagate updates | Use `StateFlow` with `.stateIn()` |
| Prop drilling through 3+ levels | Unreadable, fragile | Use CompositionLocal or restructure |

---

## Required Patterns

| Pattern | Where | Example |
|---------|-------|---------|
| `FormItem` for settings rows | All settings/form pages | `SettingSearchPage.kt`, `AssistantBasicPage.kt` |
| `BackButton()` in TopAppBar | Every sub-page | `SettingProviderPage.kt` |
| `LocalToaster` for user feedback | Toast/snackbar messages | `toaster.show("Success", type = ToastType.Success)` |
| `Scaffold` with `TopAppBar` | Every page | Standard page structure |
| `koinViewModel()` for VM injection | Every page composable | Default parameter: `vm: MyVM = koinViewModel()` |
| `Modifier = Modifier` as first optional param | Every reusable component | Component parameter convention |

---

## Page Structure Checklist

Every new page should follow this structure:

```kotlin
@Composable
fun FeaturePage(vm: FeatureVM = koinViewModel()) {
    // 1. Collect state
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current

    // 2. Scaffold with TopAppBar
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feature") },
                navigationIcon = { BackButton() },
            )
        }
    ) { innerPadding ->
        // 3. Content with innerPadding
        LazyColumn(contentPadding = innerPadding) {
            // Page content
        }
    }
}
```

---

## Testing Requirements

### What to Test

- **Transformers**: Input/output message transformers (`data/ai/transformers/`)
- **Data model logic**: Extension functions on domain types (e.g., `handleMessageChunk`, `limitContext`)
- **Serialization**: Complex data model serialization/deserialization
- **Migrations**: Room database migrations (instrumented tests)

### Existing Tests

| Module | Test Files | Coverage |
|--------|-----------|----------|
| `ai` | 10 test files | Model registry, provider message formatting, file encoding, JSON utils |
| `app` | 5 test files | Transformers, version comparison, provider config conversion |

### Test Framework

- JUnit 4
- `androidx.test.ext:junit` for Android tests
- `androidx.room.testing` for migration tests
- `androidx.compose.ui.test.junit4` for Compose UI tests

---

## Code Style

### EditorConfig Rules

```
indent_size = 4 (Kotlin)
indent_size = 2 (XML, JSON)
max_line_length = 120
trim_trailing_whitespace = true
insert_final_newline = true
```

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Page composable | `<Feature>Page` | `ChatPage`, `SettingPage` |
| ViewModel | `<Feature>VM` | `ChatVM`, `SettingVM` |
| Hook | `remember<Name>()` or `use<Name>()` | `rememberAvatarShape()`, `useEditState()` |
| CompositionLocal | `Local<Name>` | `LocalSettings`, `LocalToaster` |
| Sealed class variant | PascalCase | `Avatar.Emoji`, `McpStatus.Connecting` |

---

## Code Review Checklist

- [ ] Uses `collectAsStateWithLifecycle()` (not `collectAsState()`)
- [ ] Uses `HugeIcons` (not Lucide or Material icons)
- [ ] Uses `FormItem` for settings/form layouts
- [ ] Uses `LocalToaster` for user-facing messages
- [ ] ViewModel registered in `ViewModelModule.kt`
- [ ] New data classes have default values for all fields
- [ ] Sealed classes use `@SerialName` for serialized variants
- [ ] Page follows Scaffold + TopAppBar + BackButton pattern
- [ ] No hardcoded colors — uses MaterialTheme or LocalExtendColors
