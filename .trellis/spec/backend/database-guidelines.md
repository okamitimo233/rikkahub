# Database Guidelines

RikkaHub uses Room for relational app data and DataStore Preferences for settings. Database code must preserve conversation/message integrity, avoid loading large message blobs unnecessarily, and keep Kotlin/Web DTO contracts synchronized.

## Room Database Ownership

`app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` registers all Room entities and DAOs. Current entities include:

- `ConversationEntity`
- `MessageNodeEntity`
- `MemoryEntity`
- `GenMediaEntity`
- `ManagedFileEntity`
- `FavoriteEntity`

The database version is declared in `AppDatabase.kt` and migrations are listed in the `@Database(autoMigrations = [...])` annotation. When a schema changes, update the version, add an auto or manual migration, and keep `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` output in mind.

## Entity and DAO Patterns

Use Room annotations close to the storage concern:

- Entities live in `data/db/entity` and use explicit `@ColumnInfo` names for persisted API stability. Example: `ConversationEntity.kt` maps `assistantId` to `assistant_id` and `createAt` to `create_at`.
- Foreign-key relationships belong in the child entity. `MessageNodeEntity.kt` declares `conversation_id` as a foreign key to `ConversationEntity.id` with `ForeignKey.CASCADE` and an index.
- DAOs live in `data/db/dao` and return `Flow` for observed lists, `PagingSource` for paged lists, and `suspend` functions for one-shot operations. `ConversationDAO.kt` demonstrates all three.
- Keep lightweight projections for list screens. `ConversationDAO.getConversationsOfAssistantPaging` selects only id/title/pin/time fields into `LightConversationEntity` so list screens do not load full message nodes.

## Repository Mapping and Transactions

Repositories own domain/entity conversion and multi-table operations. Do not duplicate mapper logic in route or UI code.

`ConversationRepository.kt` is the model pattern:

- `conversationToConversationEntity` serializes complex fields with `JsonInstant` and rejects base64 message parts before persistence with `require(conversation.messageNodes.none { ... hasBase64Part() })`.
- `conversationEntityToConversation` parses UUIDs, restores `Instant`, decodes JSON fields, and converts blank optional strings to `null`.
- `insertConversation` and `updateConversation` wrap conversation and message-node writes in `database.withTransaction { ... }`.
- `deleteConversation` deletes FTS entries, relies on `message_node` cascade deletion, and then cleans managed chat files through `FilesManager`.

When adding repository operations that mutate multiple tables or a table plus an index, use `database.withTransaction`. Keep side effects that are not DB writes, such as file deletion, ordered deliberately around the transaction as `ConversationRepository.deleteConversation` does.

## Message Node Storage

Conversation messages are no longer stored in the `ConversationEntity.nodes` column for active data. `ConversationRepository.conversationToConversationEntity` writes `nodes = "[]"`, and actual branch data is stored in `message_node.messages` as serialized `List<UIMessage>`.

Guidelines:

- Preserve node order with `node_index`, as `MessageNodeDAO.getNodesOfConversationPaged` orders by `node_index ASC`.
- Keep message-node loading paged. `ConversationRepository.loadMessageNodes` reads pages of 64 nodes and catches `SQLiteBlobTooBigException`/`IllegalStateException` to skip problematic pages instead of crashing the whole conversation load.
- Do not add new large columns to list queries. Use lightweight projections for list and search views.
- Keep branch data as `MessageNode`/`UIMessage` in domain code and convert at repository boundaries.

## Full-Text Search and Statistics

Message search is managed by `data/db/fts/MessageFtsManager.kt`, with repository entry points such as `ConversationRepository.searchMessages` and `rebuildAllIndexes`.

`MessageNodeDAO.kt` also uses `@RawQuery` with SQLite `json_each()` for token and daily message statistics. Raw queries are acceptable when Room cannot validate SQLite virtual table functions, but keep them centralized in DAO extension functions such as `getTokenStats()` and `getMessageCountPerDay(...)`.

## DataStore Settings

`app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` owns preference keys, defaults, `Settings`, cleanup, and update helpers.

Patterns to follow:

- Define all preference keys in `SettingsStore.Companion` with typed keys (`booleanPreferencesKey`, `stringPreferencesKey`, `intPreferencesKey`).
- Decode complex settings through `JsonInstant.decodeFromString` and provide safe defaults (`emptyList()`, `SearchCommonOptions()`, `BackupReminderConfig()`, etc.).
- Keep normalization in the settings flow. Existing code adds missing built-in providers/assistants, refreshes built-in metadata, removes invalid assistant references, de-duplicates providers/models, and invalidates the Pebble template cache.
- Write changes through `SettingsStore.update(settings)` or helper functions such as `updateAssistantModel`, not by directly editing DataStore from UI code.
- Never persist `Settings.dummy()`: `SettingsStore.update` checks `settings.init` and logs a warning instead of writing dummy defaults.

When adding a setting, update all four places if applicable: key, decode/default in `settingsFlowRaw`, write in `update`, and `Settings` data class. Add a DataStore migration under `data/datastore/migration` when older serialized structures require transformation.

## Migrations

Room migrations live in `data/db/migrations` and follow `Migration_<from>_<to>.kt`. Existing examples include manual/auto migration specs such as `Migration_8_9.kt` and `Migration_16_17.kt`.

Migration rules:

- Increment `AppDatabase` version when the Room schema changes.
- Prefer Room auto migrations for additive/simple schema changes already supported by Room.
- Use a migration spec or manual migration for renames, data backfills, JSON transformations, FTS changes, or destructive-looking operations.
- Keep migrations idempotent where possible and do not assume user data is clean.
- Update repository mappers and default values in entities together with schema changes.

## Web DTO and Type Synchronization

When persistent data is exposed through the embedded web API:

- Update `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt` request/response/event DTOs and conversion extensions.
- Update `web-ui/app/types/*.ts` mirrors. For example, `WebDto.kt` has `ConversationDto`, `MessageNodeDto`, and `MessageDto`; `web-ui/app/types/dto.ts` mirrors these shapes.
- Check serializers: Ktor uses `JsonInstant` in `WebApiModule.kt`, and app/domain types often use kotlinx serialization with `@SerialName` discriminators.

## Common Mistakes to Avoid

- Loading full `message_node.messages` blobs for conversation lists. Use DAO projections like `LightConversationEntity`.
- Updating a Room entity field without adding it to repository conversion functions.
- Adding a field to Kotlin DTOs without updating TypeScript types and UI consumers.
- Writing `Settings.dummy()` or bypassing `SettingsStore.update`.
- Forgetting to re-index FTS after conversation message changes.
- Storing `data:` base64 image parts in conversations. Existing repository code requires local-file conversion before persistence.
