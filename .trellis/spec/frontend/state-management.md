# State Management

> How state is managed in this project.

---

## Overview

State management follows a layered architecture:
- **ViewModel** + **StateFlow**: Primary state holder for each page
- **CompositionLocal**: Cross-tree shared state (settings, navigation, toaster)
- **DataStore**: Persistent app preferences
- **Room**: Persistent structured data
- **Koin**: Dependency injection

---

## State Categories

| Category | Tool | Scope | Example |
|----------|------|-------|---------|
| UI-local state | `remember { mutableStateOf() }` | Single composable | Dialog visibility, text field value |
| Page state | ViewModel + StateFlow | Screen lifetime | Conversation data, loading state |
| App-wide state | CompositionLocal | Entire app | Settings, navigation, toaster |
| Persistent preferences | DataStore (SettingsStore) | Disk | Provider config, display settings |
| Persistent data | Room database | Disk | Conversations, messages, memories |

---

## ViewModel Pattern

### Simple ViewModel

```kotlin
// SettingVM.kt
class SettingVM(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }
}
```

### Complex ViewModel (with parameters)

```kotlin
// ChatVM.kt
class ChatVM(
    id: String,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    // Reactive state from repository
    val conversation: StateFlow<Conversation?> = conversationRepo
        .getConversation(Uuid.parse(id))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Compose-observed UI flag (for simple booleans owned by VM)
    var chatListInitialized by mutableStateOf(false)

    // One-shot action
    fun sendMessage(content: String) {
        viewModelScope.launch { /* ... */ }
    }
}
```

### State Exposure Rules

| Data Source | Exposure Pattern |
|------------|-----------------|
| Repository Flow | `.stateIn(viewModelScope, SharingStarted.*, default)` → `StateFlow<T>` |
| One-shot data | `MutableStateFlow<T>` with manual updates |
| Simple UI flag | `var x by mutableStateOf(false)` (Compose-observed) |

### Consuming ViewModel State

```kotlin
@Composable
fun MyPage(vm: MyVM = koinViewModel()) {
    // StateFlow → Compose State (lifecycle-aware)
    val data by vm.dataFlow.collectAsStateWithLifecycle()

    // Direct mutableStateOf (already Compose-observed)
    val isReady = vm.chatListInitialized
}
```

---

## CompositionLocal Providers

All defined in `ui/context/` and provided at root level in `RouteActivity.AppRoutes()`:

| Local | Type | Purpose |
|-------|------|---------|
| `LocalNavController` | `Navigator` | App navigation (push, pop, clear) |
| `LocalSettings` | `Settings` | Current app settings |
| `LocalToaster` | `ToasterState` | Toast messages |
| `LocalTTSState` | `CustomTtsState` | TTS engine state |
| `LocalSharedTransitionScope` | `SharedTransitionScope` | Shared element transitions |
| `LocalHighlighter` | `Highlighter` | Code syntax highlighting |
| `LocalDarkMode` | `Boolean` | Current dark mode state |
| `LocalExtendColors` | Extended palette | Custom color extensions |

### Usage

```kotlin
val navController = LocalNavController.current
val settings = LocalSettings.current
val toaster = LocalToaster.current
```

### When to Use CompositionLocal

- Data needed by many deeply nested composables (avoids prop drilling)
- App-wide singletons (settings, navigation, theme)
- **Do NOT** use for page-specific state (use ViewModel instead)

---

## Dependency Injection (Koin)

### Module Organization

| Module | File | Contents |
|--------|------|----------|
| `AppModule` | `di/AppModule.kt` | App-level singletons (Highlighter, EventBus, UpdateChecker, etc.) |
| `DataSourceModule` | `di/DataSourceModule.kt` | Database, OkHttpClient, SettingsStore, DAOs |
| `RepositoryModule` | `di/RepositoryModule.kt` | All 5 repositories + FilesManager |
| `ViewModelModule` | `di/ViewModelModule.kt` | All ViewModels |

### Registering a ViewModel

```kotlin
// In ViewModelModule.kt
val viewModelModule = module {
    // Simple ViewModel
    viewModelOf(::SettingVM)

    // ViewModel with runtime parameters
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get<String>(),
            conversationRepo = get(),
            settingsStore = get(),
        )
    }
}
```

### Injecting in Composables

```kotlin
// ViewModel injection (scoped to navigation entry)
val vm: SettingVM = koinViewModel()

// ViewModel with parameters
val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })

// Non-ViewModel injection
val filesManager: FilesManager = koinInject()
```

---

## DataStore (SettingsStore)

**File**: `data/datastore/PreferencesStore.kt`

The `SettingsStore` wraps Android DataStore Preferences into a single `Settings` data class.

```kotlin
// Reading settings (reactive)
val settings: StateFlow<Settings> = settingsStore.settingsFlow

// Updating settings
settingsStore.update(currentSettings.copy(displaySettings = ...))
```

Key characteristics:
- All settings fields have default values
- A `dummy()` sentinel prevents accidental saving of uninitialized data
- The flow applies post-processing: merges default providers, deduplicates, cleans up stale references
- Versioned migrations in `datastore/migration/`

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Storing page state in CompositionLocal | Use ViewModel for page-scoped state |
| Using `MutableStateFlow` when repository already provides Flow | Use `.stateIn()` to convert repository Flow to StateFlow |
| Collecting Flow without lifecycle awareness | Use `collectAsStateWithLifecycle()`, not `collectAsState()` |
| Accessing SettingsStore directly from composables | Inject via ViewModel, or use `LocalSettings.current` for read-only access |
| Creating new Koin modules for each feature | Add to existing module files (AppModule, RepositoryModule, ViewModelModule) |
