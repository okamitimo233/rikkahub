# Quality Guidelines

> Code quality standards for backend (data layer) development.

---

## Overview

The project uses Kotlin with Gradle (KTS), targeting JVM 17 / Android SDK 36 (min 26). Quality is maintained through consistent patterns, Koin dependency injection, Room annotations processed by KSP, and unit/instrumented tests.

---

## Forbidden Patterns

| Pattern | Why | Alternative |
|---------|-----|------------|
| Hilt/Dagger for DI | Project uses Koin | Register in existing Koin modules (`di/`) |
| Nullable collections (`List<T>?`) | Ambiguous null vs empty | Use `emptyList()` default |
| Raw SQL without `@RawQuery` | Bypasses Room safety | Use Room annotations; `@RawQuery` only for virtual table ops |
| Catching all exceptions in repositories | Masks errors | Let exceptions propagate to ViewModel |
| `GlobalScope.launch` | Lifecycle leak | Use `viewModelScope` or structured concurrency |
| Storing base64 images in database | Bloats database | Save to local files first (see `Base64ImageToLocalFileTransformer`) |
| Creating new Koin modules per feature | Module bloat | Add to existing: `AppModule`, `DataSourceModule`, `RepositoryModule`, `ViewModelModule` |

---

## Required Patterns

| Pattern | Where | Reference |
|---------|-------|-----------|
| `@ColumnInfo(name = "snake_case")` | All Room entity fields | `ConversationEntity.kt`, `MessageNodeEntity.kt` |
| `database.withTransaction {}` | Multi-table writes | `ConversationRepository.kt` |
| `DatabaseMigrationTracker.onMigrationStart/End()` | Manual migrations | `Migration_11_12.kt` |
| `@Serializable` + `@SerialName` | All serialized sealed class variants | `UIMessagePart`, `ProviderSetting`, `Avatar` |
| Default values on all data class fields | All domain models | `Assistant.kt` (30+ fields, all with defaults) |
| `Flow<List<T>>` for reactive queries | DAO list queries powering UI | `ConversationDAO.kt` |
| `suspend fun` for one-shot operations | DAO CRUD operations | `ConversationDAO.kt` |

---

## Dependency Injection (Koin)

### Module Structure

| Module | File | Registration Pattern |
|--------|------|---------------------|
| `AppModule` | `di/AppModule.kt` | `single { ... }` for app-level singletons |
| `DataSourceModule` | `di/DataSourceModule.kt` | `single { ... }` for DB, OkHttp, DAOs, stores |
| `RepositoryModule` | `di/RepositoryModule.kt` | `singleOf(::Repository)` for all repositories |
| `ViewModelModule` | `di/ViewModelModule.kt` | `viewModelOf(::VM)` or `viewModel { ... }` with params |

### Rules

- All data layer components (repositories, managers, stores) are registered as `single`
- ViewModels use `viewModelOf()` for simple cases, `viewModel { }` for parameterized ones
- Constructor injection — no field injection

---

## Repository Layer

### Structure

```kotlin
class MyRepository(
    private val myDao: MyDAO,
    private val database: AppDatabase,  // Only if transactions needed
) {
    // Reactive: returns Flow for UI observation
    fun getAll(): Flow<List<MyModel>> = myDao.getAll().map { entities ->
        entities.map { it.toDomainModel() }
    }

    // One-shot: suspend for CRUD
    suspend fun save(model: MyModel) {
        myDao.upsert(model.toEntity())
    }
}
```

### Entity-to-Domain Mapping

- Room entities are flat data classes with primitive types
- Domain models use rich types (Uuid, Instant, sealed classes)
- Conversion happens in repository layer
- Timestamps: `Long` (epoch millis) in entity → `Instant` in domain model
- UUIDs: `String` in entity → `Uuid` in domain model
- Complex objects: JSON `String` in entity → deserialized type in domain model

---

## Build Configuration

```
compileSdk = 36
minSdk = 26
targetSdk = 36
jvmTarget = 17

# Kotlin
indent_size = 4
max_line_length = 120

# R8/ProGuard
isMinifyEnabled = true (release)
isShrinkResources = true (release)
```

---

## Testing Requirements

### What Must Be Tested

| Category | Test Type | Example |
|----------|----------|---------|
| Database migrations | Instrumented (`androidTest`) | `Migration_11_12_Test.kt` |
| Message transformers | Unit test | `PromptInjectionTransformerTest.kt` |
| Provider message formatting | Unit test | `ClaudeProviderMessageTest.kt` |
| Model registry matching | Unit test | `ModelRegistryTest.kt` |
| JSON serialization | Unit test | `JsonTest.kt` |

### Test Framework

- JUnit 4
- `androidx.room.testing` for `MigrationTestHelper`
- Test files in `src/test/` (unit) and `src/androidTest/` (instrumented)

### Adding Tests

1. Unit tests: `<module>/src/test/java/me/rerere/.../`
2. Instrumented tests: `<module>/src/androidTest/java/me/rerere/.../`
3. Migration tests require exported schema files in `app/schemas/`

---

## Code Review Checklist

- [ ] Room entities use `@ColumnInfo(name = "snake_case")`
- [ ] Data classes have default values for all fields
- [ ] Sealed classes use `@Serializable` + `@SerialName`
- [ ] Multi-table writes wrapped in `database.withTransaction {}`
- [ ] Manual migrations call `DatabaseMigrationTracker`
- [ ] New repositories/services registered in appropriate Koin module
- [ ] ViewModels use `viewModelScope.launch` (not GlobalScope)
- [ ] `Flow` for reactive queries, `suspend` for one-shot ops
- [ ] No nullable collections — use `emptyList()` / `emptySet()`
- [ ] No base64 images stored in database
- [ ] Errors propagate from repository (no silent catch)

---

## Skill-Assisted Review

After significant code changes, use the `/simplify` skill for automated quality review:

- **What it checks**: Code reuse opportunities, quality issues, efficiency improvements
- **When to use**: After implementing a feature or making multi-file changes
- **What it does**: Reviews changed code and self-fixes any issues found

> `/simplify` complements manual review — it catches mechanical issues so you can focus on architecture and logic.
