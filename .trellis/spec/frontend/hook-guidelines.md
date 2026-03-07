# Hook Guidelines

> Custom Composable hooks and effect patterns in this project.

---

## Overview

The project uses a `ui/hooks/` directory for React-style custom composable "hooks" — composable functions that encapsulate reusable stateful logic. Two naming conventions are used: `remember*` for state-returning hooks and `use*` for side-effect or complex state hooks.

---

## Custom Hook Patterns

### Naming Conventions

| Prefix | Purpose | Example |
|--------|---------|---------|
| `remember*` | Returns remembered state, tied to composition lifecycle | `rememberUserSettingsState()`, `rememberAvatarShape()` |
| `use*` | Complex state machines or side-effect-driven logic | `useEditState<T>()`, `useDebounce<T>()`, `useThrottle<T>()` |

### Hook Inventory

| Hook | File | Returns | Purpose |
|------|------|---------|---------|
| `rememberUserSettingsState()` | `hooks/Settings.kt` | `State<Settings>` | Collects SettingsStore flow as lifecycle-aware state |
| `rememberSharedPreferenceString()` | `hooks/SharedPreferences.kt` | `MutableState<String>` | Reactive SharedPreferences string |
| `rememberSharedPreferenceBoolean()` | `hooks/SharedPreferences.kt` | `MutableState<Boolean>` | Reactive SharedPreferences boolean |
| `rememberColorMode()` | `hooks/ColorMode.kt` | Color mode state | Color theme preference |
| `rememberAmoledDarkMode()` | `hooks/ColorMode.kt` | Amoled mode state | AMOLED dark mode flag |
| `rememberAssistantState()` | `hooks/UseAssistant.kt` | Current assistant state | Selected assistant |
| `rememberAppLifecycleState()` | `hooks/Lifecycle.kt` | `Lifecycle.State` | Android lifecycle as Compose state |
| `rememberAvatarShape()` | `hooks/AvatarShape.kt` | Animated shape | Circle-to-cookie animation during loading |
| `rememberCustomTtsState()` | `hooks/TTS.kt` | TTS state | TTS engine lifecycle management |
| `rememberIsPlayStoreVersion()` | `hooks/PlayStore.kt` | `Boolean` | Install source check |
| `useEditState<T>()` | `hooks/UseEditState.kt` | `EditState<T>` | Edit dialog state machine (open/confirm/dismiss) |
| `useDebounce<T>()` | `hooks/Debounce.kt` | Debounced callback | Debounced value updates |
| `useThrottle<T>()` | `hooks/Debounce.kt` | Throttled callback | Throttled value updates |
| `ImeLazyListAutoScroller()` | `hooks/ImeAutoScroller.kt` | - | Auto-scroll LazyList when keyboard opens |
| `Modifier.heroAnimation()` | `hooks/HeroAnimation.kt` | `Modifier` | Shared element transition |

### Creating a New Hook

```kotlin
// hooks/MyHook.kt

@Composable
fun rememberMyState(param: String): MyState {
    val state = remember(param) { MyState(param) }

    DisposableEffect(param) {
        state.start()
        onDispose { state.stop() }
    }

    return state
}
```

---

## Key Hook: `useEditState<T>()`

The most widely used custom hook. Manages the state of create/edit dialogs.

**File**: `ui/hooks/UseEditState.kt`

```kotlin
// Create the state (onConfirm is called when user confirms)
val editState = useEditState<ProviderSetting> { confirmedValue ->
    viewModel.save(confirmedValue)
}

// Open the dialog with initial state
editState.open(ProviderSetting.OpenAI())

// Access current editing value
editState.current  // T?

// Check if editing
editState.isEditing  // Boolean

// Update the editing value
editState.update(newValue)

// Confirm or dismiss
editState.confirm()
editState.dismiss()
```

**Used in**: `SettingProviderPage.kt`, `SettingMcpPage.kt`, `AssistantBasicPage.kt`, and many other settings pages.

---

## Data Fetching

Data fetching is done **exclusively through ViewModels**. Composables never make direct network calls.

### Pattern: ViewModel + StateFlow + collectAsStateWithLifecycle

```kotlin
// In ViewModel
class ChatVM(...) : ViewModel() {
    val conversation: StateFlow<Conversation?> = conversationRepo
        .getConversation(id)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

// In Composable
@Composable
fun ChatPage(vm: ChatVM = koinViewModel()) {
    val conversation by vm.conversation.collectAsStateWithLifecycle()

    // Use conversation data
    conversation?.let { conv ->
        // Render UI
    }
}
```

### Pattern: One-shot Operations

```kotlin
// In ViewModel
fun deleteConversation(id: Uuid) {
    viewModelScope.launch {
        conversationRepo.delete(id)
    }
}

// In Composable
Button(onClick = { vm.deleteConversation(conversationId) }) {
    Text("Delete")
}
```

---

## Effect Patterns

### LaunchedEffect

Used for one-time side effects tied to keys:

```kotlin
LaunchedEffect(conversationId) {
    vm.loadConversation(conversationId)
}
```

### DisposableEffect

Used for lifecycle-bound resources:

```kotlin
DisposableEffect(Unit) {
    val listener = registerListener()
    onDispose { listener.unregister() }
}
```

### derivedStateOf

Used for computed state:

```kotlin
val isValid by remember {
    derivedStateOf { name.isNotBlank() && url.isNotBlank() }
}
```

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Fetching data directly in composable | Use ViewModel with StateFlow, collect with `collectAsStateWithLifecycle()` |
| Creating stateful logic inline | Extract to a `remember*` or `use*` hook in `ui/hooks/` |
| Using `collectAsState()` | Use `collectAsStateWithLifecycle()` to respect Android lifecycle |
| Forgetting `onDispose` in `DisposableEffect` | Always clean up resources |
| Creating a hook without `remember` | State must be remembered across recompositions |
