# Frontend Development Guidelines

> Best practices for frontend (Jetpack Compose) development in this project.

---

## Overview

This directory contains guidelines for frontend development. RikkaHub uses Jetpack Compose with Material Design 3, Koin for DI, and Navigation3 for routing.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | UI pages, components, hooks, theme organization | Done |
| [Component Guidelines](./component-guidelines.md) | Composable patterns, FormItem, icons, toasts, dialogs | Done |
| [Hook Guidelines](./hook-guidelines.md) | Custom hooks, effects, data fetching patterns | Done |
| [State Management](./state-management.md) | ViewModel, StateFlow, Koin DI, CompositionLocal, DataStore | Done |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns, testing | Done |
| [Type Safety](./type-safety.md) | Kotlin types, sealed classes, serialization, nullability | Done |
| [Icon Search Guide](./icon-search-guide.md) | HugeIcons search workflow via `/find-hugeicons` skill | Done |
| [Localization Guidelines](./localization-guidelines.md) | i18n workflow via `locale-tui` tool | Done |

---

## Quick Reference

### Key Conventions

- **Icons**: `HugeIcons` from `me.rerere.hugeicons` (NOT Lucide)
- **Toast**: `LocalToaster.current` from `com.dokar.sonner`
- **Forms**: `FormItem` for settings/form layouts
- **DI**: Koin — `koinViewModel()` for ViewModels, `koinInject()` for services
- **State**: `collectAsStateWithLifecycle()` for StateFlow in composables
- **Navigation**: Navigation3 with `Screen` sealed interface routes
- **Icon search**: Use `/find-hugeicons` skill to search icons by keyword — never guess names
- **Localization**: Use `/locale-tui-localization` skill when explicitly requested
- **Code review**: Use `/simplify` skill after significant code changes to check quality

---

**Language**: All documentation should be written in **English**.
