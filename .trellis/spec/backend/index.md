# Backend Development Guidelines

RikkaHub backend work covers Android/Kotlin non-UI code, persistence, AI provider abstractions, message transformation, services, and the embedded Ktor web API.

## Pre-Development Checklist

- Identify the owning module before editing: modules are declared in `settings.gradle.kts` as `:app`, `:ai`, `:search`, `:speech`, `:document`, `:highlight`, `:common`, `:web`, and `:material3`.
- For app data or service changes, inspect `app/src/main/java/me/rerere/rikkahub/data`, `app/src/main/java/me/rerere/rikkahub/service`, `app/src/main/java/me/rerere/rikkahub/web`, and the Koin modules in `app/src/main/java/me/rerere/rikkahub/di`.
- For provider or message contract changes, inspect `ai/src/main/java/me/rerere/ai/provider`, `ai/src/main/java/me/rerere/ai/ui/Message.kt`, and all web DTO/type mirrors.
- For persistent data changes, inspect `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`, entities, DAOs, migrations, and `app/schemas`.
- For embedded web changes, inspect `web/src/main/java/me/rerere/rikkahub/web/Entry.kt` and `app/src/main/java/me/rerere/rikkahub/web/**`.

## Guidelines Index

- [Directory Structure](./directory-structure.md) - Module ownership, package boundaries, and where backend code belongs.
- [Database Guidelines](./database-guidelines.md) - Room entities, DAOs, repositories, migrations, FTS, and DataStore settings.
- [Error Handling](./error-handling.md) - Coroutine, provider, Ktor route, and UI-facing error patterns.
- [Logging Guidelines](./logging-guidelines.md) - Android Log, request logging, AI logging, and sensitive-data boundaries.
- [Quality Guidelines](./quality-guidelines.md) - Kotlin/service/provider/serialization quality rules and validation commands.

## Quality Check

- Confirm new backend code lives in the module/package that already owns the behavior.
- Confirm persistent model changes update every boundary: domain model, Room entity/DAO/migration, repository mapper, web DTO, and `web-ui/app/types` when exposed to the web UI.
- Confirm network/provider code runs blocking work on `Dispatchers.IO` or through existing async helpers such as `me.rerere.common.http.await`.
- Confirm Ktor routes validate path/query/body inputs and throw `ApiException` subclasses for client errors.
- Run focused Gradle checks for touched modules, for example `./gradlew :app:compileDebugKotlin`, `./gradlew :ai:compileDebugKotlin`, or `./gradlew :web:compileKotlin`.
