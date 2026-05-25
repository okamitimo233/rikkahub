# Thinking Guides

These guides supplement the project-specific backend and frontend specs. Use them when a change crosses RikkaHub layers or when you are about to duplicate an existing pattern.

## Available Guides

- [Code Reuse Thinking Guide](./code-reuse-thinking-guide.md) - How to search for existing RikkaHub patterns before adding new utilities, components, DTOs, hooks, repositories, or provider logic.
- [Cross-Layer Thinking Guide](./cross-layer-thinking-guide.md) - How to map data and behavior across Kotlin domain models, Room/DataStore, Ktor DTOs, Compose UI, and `web-ui` TypeScript.

## Quick Triggers

Read the code reuse guide when:

- You are adding a helper, hook, component, repository method, route, DTO, provider conversion, or transformer.
- You see two files reading or constructing the same payload shape.
- You are tempted to copy a page/component/provider and change a few fields.
- You are modifying a constant, enum, serial name, route path, DataStore key, Room column, or message part type.

Read the cross-layer guide when:

- A change affects `UIMessage`, `UIMessagePart`, `Settings`, `Conversation`, provider settings, or web DTOs.
- A feature spans persistence, services, Ktor routes, Compose UI, and/or `web-ui`.
- You are adding an SSE event, REST request/response, prompt transformer, tool output, or file URL contract.
- You are changing data that must round-trip through Room/DataStore and render in both Android and web UI.

## Project-Specific Boundaries to Keep in Mind

- Kotlin serialized contracts in `ai/src/main/java/me/rerere/ai/ui/Message.kt`, `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`, and `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt` often have TypeScript mirrors under `web-ui/app/types`.
- Conversation data is persisted through `ConversationRepository` and Room message-node entities, but live generation state belongs to `ChatService`.
- The embedded web UI source is `web-ui/app`; `web/src/main/resources/static` is build output.
- Provider-specific API JSON belongs in `ai/provider/providers`, not in UI or app services.

## Tooling Reminder

Use CodeGraph for structural questions such as symbol definitions, callers/callees, and impact. Use the repository search tools for literal text. Do not rely on shell `grep` examples from generic templates when project tools provide safer searches.
