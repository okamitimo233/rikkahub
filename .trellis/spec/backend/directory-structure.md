# Directory Structure

> How backend (data layer) code is organized in this project.

---

## Overview

RikkaHub is a multi-module Android project. The "backend" is the data layer within the `app` module plus several feature modules (`ai`, `search`, `tts`, `common`, `document`, `web`). There is no separate server — the data layer handles local persistence, AI provider communication, and sync.

---

## Directory Layout

### Multi-Module Structure

```
rikkahub/
├── app/                    # Main application (UI + data layer)
├── ai/                     # AI SDK abstraction (providers, models, messages)
├── common/                 # Shared utilities (logging, caching, HTTP)
├── document/               # Document parsing (PDF, DOCX, PPTX)
├── highlight/              # Code syntax highlighting
├── search/                 # Web search service integrations (16 providers)
├── tts/                    # Text-to-speech providers (6 providers)
└── web/                    # Embedded Ktor web server
```

### App Module Data Layer (`app/.../data/`)

```
data/
├── ai/                       # AI integration logic
│   ├── mcp/                  # MCP (Model Context Protocol) client
│   │   ├── transport/        # SSE and StreamableHTTP transports
│   │   ├── McpConfig.kt
│   │   ├── McpManager.kt
│   │   └── McpStatus.kt
│   ├── prompts/              # Prompt templates (compress, OCR, title, translation)
│   ├── tools/                # Tool implementations (local tools, memory, search)
│   ├── transformers/         # Message transformer pipeline
│   ├── AILogging.kt          # In-memory AI request logging
│   ├── GenerationHandler.kt  # Main AI generation pipeline
│   └── RequestLoggingInterceptor.kt
├── api/                      # External API clients (Retrofit)
├── datastore/                # Android DataStore preferences
│   ├── migration/            # DataStore versioned migrations
│   ├── DefaultProviders.kt   # Default provider/assistant configs
│   └── PreferencesStore.kt   # SettingsStore (main preferences)
├── db/                       # Room database
│   ├── dao/                  # Data Access Objects (6 DAOs)
│   ├── entity/               # Room entities (6 entities)
│   ├── fts/                  # Full-text search (FTS5 + jieba)
│   ├── migrations/           # Manual database migrations
│   ├── AppDatabase.kt        # Database definition (version 17)
│   └── DatabaseMigrationTracker.kt
├── event/                    # App event bus
├── export/                   # Data export/import serialization
├── favorite/                 # Favorite adapters
├── files/                    # File management (FilesManager)
├── model/                    # Domain models (Assistant, Conversation, etc.)
├── repository/               # Repository layer (5 repositories)
└── sync/                     # Cloud sync (S3, WebDAV)
    ├── importer/             # External data importers
    ├── s3/                   # S3 client + AWS SigV4
    └── webdav/               # WebDAV client + sync
```

### AI Module (`ai/.../`)

```
ai/
├── core/           # Core types: MessageRole, Reasoning, Tool, Usage
├── provider/       # Provider interface, Model, ProviderSetting, ProviderManager
│   └── providers/  # Concrete providers: OpenAI, Google, Claude
│       ├── openai/ # ChatCompletionsAPI, ResponseAPI
│       └── vertex/ # Vertex AI auth
├── registry/       # Model registry with DSL for capability matching
├── ui/             # UIMessage, Image types
└── util/           # ErrorParser, Json, SSE, KeyRoulette, FileEncoder
```

### Common Module (`common/.../`)

```
common/
├── android/    # ContextUtil, Logging (ring buffer)
├── cache/      # LruCache, PerKeyFileCacheStore, SingleFileCacheStore
└── http/       # AcceptLang, JsonExpression, Request helpers, SSE
```

---

## Module Organization

### When to Put Code in Each Module

| Module | Criteria | Example |
|--------|----------|---------|
| `app/data/model/` | Domain models used by UI and data layers | `Assistant.kt`, `Conversation.kt` |
| `app/data/repository/` | Data access combining DAOs, network, and business logic | `ConversationRepository.kt` |
| `app/data/db/entity/` | Room database entities (persistence schema) | `ConversationEntity.kt` |
| `app/data/ai/` | AI-specific integration (transformers, tools, generation) | `GenerationHandler.kt` |
| `ai/` | Provider-agnostic AI abstractions reusable across apps | `Provider.kt`, `UIMessage.kt` |
| `common/` | Utilities with no Android/AI dependency | `Logging.kt`, `LruCache.kt` |
| Feature modules | Self-contained feature packages | `search/`, `tts/`, `document/` |

### Adding a New AI Provider

1. Add provider setting variant to `ProviderSetting` sealed class (`ai/.../ProviderSetting.kt`)
2. Create provider class implementing `Provider<T>` (`ai/.../providers/`)
3. Register in `ProviderManager` (`ai/.../ProviderManager.kt`)
4. Add default configuration in `DefaultProviders.kt` (`app/.../datastore/`)

### Adding a New Feature Module

1. Create module directory with standard Gradle structure
2. Define module-scoped types (sealed class hierarchies with `@SerialName`)
3. Add module dependency in `app/build.gradle.kts`
4. Register Koin bindings in appropriate DI module

---

## Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Entity | `<Name>Entity.kt` | `ConversationEntity.kt` |
| DAO | `<Name>DAO.kt` | `ConversationDAO.kt` |
| Repository | `<Name>Repository.kt` | `ConversationRepository.kt` |
| Domain model | `<Name>.kt` | `Assistant.kt`, `Conversation.kt` |
| Migration | `Migration_<from>_<to>.kt` | `Migration_6_7.kt` |
| Transformer | `<Name>Transformer.kt` | `ThinkTagTransformer.kt` |
| Provider | `<Name>Provider.kt` | `OpenAIProvider.kt` |

---

## Examples

- **Well-structured module**: `ai/` — Clean separation of core types, provider interface, concrete implementations, and utilities
- **Complex data flow**: `data/repository/ConversationRepository.kt` — Transaction management, entity mapping, paging, FTS indexing
- **Feature module**: `search/` — Self-contained with sealed class hierarchy for 16 search providers
- **Migration example**: `data/db/migrations/Migration_11_12.kt` — Extracting embedded JSON to separate table
