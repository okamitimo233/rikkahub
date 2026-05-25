# Component Guidelines

Build UI components by following the surface's existing composition style: Material 3 Compose for Android, shadcn/Radix/Tailwind for `web-ui`. Keep components stateless unless they own local UI-only behavior.

## Compose Page Components

Compose pages generally follow the pattern visible in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`:

- Use `@Composable fun FeaturePage(vm: FeatureVM = koinViewModel())` for screen entry points.
- Collect ViewModel flows with `collectAsStateWithLifecycle`.
- Use `Scaffold` with a Material 3 top app bar (`LargeFlexibleTopAppBar` where appropriate), `BackButton`, `TopAppBarDefaults` scroll behavior, and `CustomColors.topBarColors` when matching settings pages.
- Keep local UI state with `remember { mutableStateOf(...) }` and derived lists with `remember(inputs) { ... }`.
- Use `rememberCoroutineScope` only for UI actions such as scrolling or launching pickers; business operations should go through the ViewModel.
- Use existing icons (`Lucide.*`, HugeIcons, or app components such as `AutoAIIcon`) consistently with nearby pages.

Example sources: `SettingProviderPage.kt`, `SettingWebPage.kt`, `AssistantDetailPage.kt`, and `ChatPage.kt`.

## Compose Reusable Components

Prefer existing components before creating new ones:

- Settings rows: `ui/components/ui/Form.kt` (`FormItem`) for label/description/tail/content layouts.
- Navigation: `ui/components/nav/BackButton.kt`.
- Errors: `ui/components/ui/ErrorCard.kt` (`ErrorCardsDisplay`, `ErrorCard`).
- Selection and inputs: `Select.kt`, `Switch.kt`, `TextArea.kt`, `TagList.kt`, `ListSelectableItem.kt`.
- Chat input and pickers: `ui/components/ai/ChatInput.kt`, `ModelList.kt`, `SearchPicker.kt`, `McpPicker.kt`, `ReasoningPicker.kt`.
- Message rendering: `ui/components/message/ChatMessage.kt` and adjacent action/reasoning/tool files.

Keep reusable components parameter-driven. Pass values and callbacks instead of reaching into ViewModels from deep components unless the existing component already follows that pattern.

## Compose Props and State

- Put required data and callbacks in parameters; give optional UI modifiers a default `Modifier = Modifier`.
- Use slot APIs for flexible layout. `FormItem` takes `label`, optional `description`, `tail`, and `content` composable lambdas.
- Keep state hoisted when parent screens must save/validate it. Local open/closed state for dialogs and popovers can remain in the component.
- For text editing that needs to survive recomposition and avoid bundle-size issues, follow `ChatInputState` instead of using many saved primitive states.

## Compose Accessibility and UX

- Use `contentDescription = null` only for decorative icons. Provide descriptions for interactive icon buttons, as `ErrorCard` does for copy/dismiss actions.
- Use `stringResource` for localized Android UI strings on localized pages. Existing settings/chat pages use `R.string.*` keys.
- Avoid blocking work in composables. Launch actions through ViewModels and show progress/loading state.
- Preserve IME and nested-scroll behavior in long forms/lists. `SettingProviderPage.kt` combines `imePadding`, lazy grid state, and top-app-bar nested scroll.

## React Component Patterns

The web UI uses function components, TypeScript props, Tailwind classes, and shadcn/Radix primitives.

Patterns to follow:

- Import base UI from `~/components/ui/*`.
- Use `cn` from `~/lib/utils` for conditional class names.
- Use `ComponentProps<"div">` or explicit interfaces for DOM-like wrapper components.
- Keep user interaction callbacks explicit in props.
- Use `React.memo` for frequently re-rendered pure components. `web-ui/app/components/message/message-part.tsx` wraps `MessageParts` in `React.memo` because streaming messages update often.
- Group feature components by domain (`components/message`, `components/input`, `components/markdown`, `components/workbench`).

## Web Message Components

Message rendering uses a dispatcher pattern:

- `components/message/message-part.tsx` groups reasoning/tool parts into chain-of-thought blocks with `groupMessageParts`.
- Content parts dispatch by `part.type` to `TextPart`, `ImagePart`, `VideoPart`, `AudioPart`, `DocumentPart`, `ReasoningPart`, or `ToolPart` components.
- Part type definitions live in `web-ui/app/types/parts.ts` and mirror `ai/src/main/java/me/rerere/ai/ui/Message.kt`.

When adding a message part, update the type union, create a part component, update the dispatcher, and update Kotlin/Android rendering at the same time.

## Web Styling

- Use Tailwind utility classes and existing shadcn component variants.
- Keep reusable visual variants in component files via `class-variance-authority` only when the component already needs variants; avoid creating a design system parallel to `components/ui`.
- Use CSS variables/theme classes provided by `theme-provider.tsx` and `app.css` rather than hard-coded theme colors.
- Use `lucide-react` for web icons, matching the existing `web-ui/package.json` dependency and component style.

## Web Accessibility

- Use Radix/shadcn primitives for dialogs, popovers, menus, tooltips, selects, drawers, and sheets instead of hand-rolled inaccessible overlays.
- Provide labels, aria attributes, or visible text for icon-only buttons.
- Keep keyboard interactions from base components intact; avoid wrapping them in elements that break focus/role behavior.

## Common Mistakes

- Creating a new form row component instead of using Compose `FormItem` or existing web `components/ui` primitives.
- Reading global stores in deeply nested web components when the parent can pass a focused prop.
- Adding a new `UIMessagePart` renderer on web but forgetting Android Compose message rendering or Kotlin serialization.
- Hard-coding display strings in areas that already use `stringResource` or i18next namespaces.
- Performing network or repository calls directly inside a composable/component render path.
