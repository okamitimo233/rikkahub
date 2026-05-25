# Directory Structure

Backend code is split by Android app responsibilities and reusable modules. Put new code where the current owner already lives; do not create a parallel architecture for a feature that already has a package.

## Gradle Modules

Modules are declared in `settings.gradle.kts`:

- `app`: Android application, DI, UI-facing services, repositories, Room/DataStore, embedded web API glue, and app-level business logic.
- `ai`: provider-neutral message abstractions and provider implementations for OpenAI-compatible, Google, and Anthropic/Claude APIs.
- `search`: search provider SDKs and shared search option types used by app tools/settings.
- `speech`: TTS and ASR provider settings/implementations.
- `document`: document parsing for uploaded/attached documents.
- `highlight`: syntax highlighting used by rich text/code UI.
- `common`: reusable utilities such as Android logging and HTTP helpers.
- `web`: Ktor server entry and static-resource host for the built React UI.
- `material3`: Material color utility extensions.

`app/build.gradle.kts` is the best dependency map for app-layer capabilities: Compose, Navigation 3, DataStore, OkHttp/SSE, Ktor client, Room, Paging, Koin, kotlinx.serialization, and module dependencies.

## App Backend Packages

Use these existing packages under `app/src/main/java/me/rerere/rikkahub`:

- `data/model`: domain models used by UI and services, for example `data/model/Conversation.kt` and `data/model/Assistant.kt`.
- `data/db`: Room database, entities, DAOs, migrations, and FTS support. `data/db/AppDatabase.kt` owns database registration.
- `data/datastore`: DataStore-backed settings and migrations. `data/datastore/PreferencesStore.kt` owns `Settings`, keys, defaults, and update helpers.
- `data/repository`: persistence-facing repositories, for example `ConversationRepository.kt`, `MemoryRepository.kt`, and `FilesRepository.kt`.
- `data/ai`: app orchestration for AI generation, logging, tools, MCP, and message transformers.
- `data/ai/transformers`: input/output message transformer pipeline (`Transformer.kt`, `ThinkTagTransformer.kt`, `DocumentAsPromptTransformer.kt`, etc.).
- `data/files`: managed chat files and skills (`FilesManager.kt`, `SkillManager.kt`).
- `data/sync`: import/export and remote backup logic (S3/WebDAV/importers).
- `service`: long-lived app services and session state (`ChatService.kt`, `ConversationSession.kt`, `WebServerService.kt`).
- `web`: Ktor API configuration, DTOs, route modules, web server manager, and NSD registration.
- `di`: Koin modules. Register repositories/managers in files such as `di/RepositoryModule.kt` instead of constructing them ad hoc.
- `utils`: app-level helpers, JSON serializers, template-variable helpers, UI state, and Android utilities.

## AI Module Ownership

Provider-neutral and provider-specific code belongs under `ai/src/main/java/me/rerere/ai`:

- `provider/Provider.kt`: stateless provider interface and generation parameter contracts.
- `provider/ProviderSetting.kt`: serializable provider configuration variants.
- `provider/ProviderManager.kt`: registration and dispatch for provider implementations.
- `provider/providers/*`: concrete providers such as `OpenAIProvider.kt`, `GoogleProvider.kt`, `ClaudeProvider.kt`, and OpenAI sub-APIs.
- `ui/Message.kt`: the cross-provider `UIMessage`, `UIMessagePart`, streaming chunk merge, tool-state, and migration contracts.
- `core`: common roles, usage, reasoning, and tool contracts.
- `util`: provider/network serialization helpers such as SSE, JSON, file encoding, and key rotation.

Keep provider implementations stateless. `Provider.kt` documents the pattern: implementation methods receive `ProviderSetting` and params instead of retaining request-specific mutable state. `OpenAIProvider.kt` and `ClaudeProvider.kt` use an injected `OkHttpClient` plus a `KeyRoulette`, then transform `UIMessage` into provider-specific JSON at the boundary.

## Embedded Web API Ownership

The embedded web server is split across two modules:

- `web/src/main/java/me/rerere/rikkahub/web/Entry.kt` starts the Ktor CIO server, installs compression, CORS, SSE, default headers, and serves static files from `web/src/main/resources/static`.
- `app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt` installs app-specific JSON, auth, status pages, and `/api` routes.
- `app/src/main/java/me/rerere/rikkahub/web/routes/*Routes.kt` owns route groups. Use one route file per feature area (`ConversationRoutes.kt`, `SettingsRoutes.kt`, `FilesRoutes.kt`, `AIIconRoutes.kt`).
- `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt` owns request/response/SSE DTOs and conversion extensions from domain models.
- `app/src/main/java/me/rerere/rikkahub/web/WebServerManager.kt` owns server lifecycle, localhost-vs-LAN binding, mDNS registration, and state flow.

Do not put app dependencies into the `web` module. The `web` module only hosts the generic server and static resources; app dependencies are passed through `configureWebApi(...)` from `WebServerManager.kt`.

## Where New Code Belongs

- New Room table: `data/db/entity`, DAO in `data/db/dao`, migration in `data/db/migrations`, database registration in `AppDatabase.kt`, repository mapper in `data/repository`.
- New persistent setting: key and default in `PreferencesStore.kt`, migration under `data/datastore/migration` when needed, UI/web DTO updates if exposed.
- New chat-generation behavior: `data/ai/GenerationHandler.kt` for orchestration, `data/ai/tools` for app tools, or `data/ai/transformers` for message transformations.
- New provider implementation: `ai/provider/providers`, registration in `ProviderManager.kt`, setting variant in `ProviderSetting.kt`, and model/ability handling in the provider boundary.
- New web endpoint: route group under `app/.../web/routes`, DTO in `WebDto.kt`, validation with `BadRequestException`/`NotFoundException`, and TypeScript mirror in `web-ui/app/types` when consumed by web UI.
- New dependency-injected service/repository: register in `app/.../di` using Koin `single { ... }`/ViewModel patterns already used by the project.

## Naming Conventions

- Kotlin files use PascalCase matching the main type: `ConversationRepository.kt`, `MessageNodeEntity.kt`, `WebServerManager.kt`.
- Room DAOs use the existing `*DAO` suffix (`ConversationDAO.kt`, `MessageNodeDAO.kt`). Keep suffix casing consistent with current code.
- Room migration files use `Migration_<from>_<to>.kt`, for example `Migration_16_17.kt`.
- Web route files use feature names plus `Routes.kt`, for example `ConversationRoutes.kt`.
- DTOs exposed by Ktor use explicit request/response/event suffixes: `SendMessageRequest`, `ConversationDto`, `ConversationSnapshotEvent`.

## Anti-Patterns

- Do not bypass repositories from UI or route code to manipulate Room entities directly. `ConversationRoutes.kt` reads/writes conversations through `ConversationRepository` and `ChatService`.
- Do not add provider-specific fields to `UIMessage` unless every provider and web UI boundary can tolerate them. Prefer metadata or provider-specific conversion in the provider implementation.
- Do not create duplicate settings stores. `PreferencesStore.kt` is the single owner for app settings, defaults, cleanup, and update helpers.
- Do not put Android `Context`-dependent app API code into the `web` module. Keep the generic server in `web` and app routes in `app`.
