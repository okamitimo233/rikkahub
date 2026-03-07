# Database Guidelines

> Room database patterns and conventions for this project.

---

## Overview

- **ORM**: Room (with KSP annotation processing)
- **Database**: `rikka_hub`, current version **17**
- **SQLite engine**: `RequerySQLiteOpenHelperFactory` with native `libsimple` extension (jieba Chinese tokenizer)
- **Full-text search**: FTS5 virtual table with jieba-based Chinese tokenization
- **Schema files**: `app/schemas/` (exported for migration testing)

**Reference**: `app/.../data/db/AppDatabase.kt`

---

## Entity Conventions

### Structure

```kotlin
@Entity(tableName = "my_table")
data class MyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
```

### Rules

| Rule | Convention |
|------|-----------|
| Table names | `snake_case` (use explicit `tableName` for new entities) |
| Column names | `snake_case` via `@ColumnInfo(name = ...)` |
| Primary keys | `String` (UUID) for domain entities, `Int`/`Long` (auto) for simple entities |
| Timestamps | Stored as `Long` (epoch millis), converted to `Instant` in repository |
| Complex types | Stored as JSON `String` (`TEXT`), deserialized in repository layer |
| Foreign keys | Use `@ForeignKey` with `CASCADE` delete where applicable |
| Indices | Add `@Index` for foreign keys and frequently queried columns |

### Current Entities

| Entity | Table | PK Type | Foreign Keys |
|--------|-------|---------|-------------|
| `ConversationEntity` | `conversationentity` | `String` (UUID) | — |
| `MessageNodeEntity` | `message_node` | `String` (UUID) | FK → ConversationEntity (CASCADE) |
| `MemoryEntity` | `memoryentity` | `Int` (auto) | — |
| `GenMediaEntity` | `genmediaentity` | `Int` (auto) | — |
| `ManagedFileEntity` | `managed_files` | `Long` (auto) | — |
| `FavoriteEntity` | `favorites` | `String` (UUID) | — |

---

## Query Patterns

### Reactive Queries (Flow)

Use `Flow<List<T>>` for continuously-observed list queries that power UI:

```kotlin
@Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId ORDER BY update_at DESC")
fun getConversationsOfAssistant(assistantId: String): Flow<List<ConversationEntity>>
```

### One-Shot Queries (suspend)

Use `suspend fun` for single-record lookups, inserts, updates, deletes:

```kotlin
@Query("SELECT * FROM conversationentity WHERE id = :id")
suspend fun getConversationById(id: String): ConversationEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsert(entity: ConversationEntity)

@Query("DELETE FROM conversationentity WHERE id = :id")
suspend fun deleteById(id: String)
```

### Paginated Queries (PagingSource)

Use `PagingSource<Int, T>` for large lists with Paging 3:

```kotlin
@Query("SELECT id, title, ... FROM conversationentity WHERE assistant_id = :assistantId ORDER BY update_at DESC")
fun getConversationsPaging(assistantId: String): PagingSource<Int, LightConversationEntity>
```

**Pager config**: Page size 20, initial load 40, prefetch distance default.

### Raw Queries

Use `@RawQuery` only when Room's compile-time checks don't support the SQL feature (e.g., `json_each()` virtual table):

```kotlin
@RawQuery
suspend fun rawQuery(query: SupportSQLiteQuery): List<TokenStatsResult>

// Wrapped in extension functions
suspend fun MessageNodeDAO.getTokenStats(): List<TokenStatsResult> {
    val query = SimpleSQLiteQuery("SELECT ... FROM message_node, json_each(...)")
    return rawQuery(query)
}
```

### Lightweight Projections

For list views that don't need full entity data, use projection classes:

```kotlin
data class LightConversationEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    // Excludes heavy fields: nodes, suggestions
)
```

---

## Transactions

Wrap multi-table operations in `database.withTransaction {}`:

```kotlin
suspend fun saveConversation(conversation: Conversation) {
    database.withTransaction {
        conversationDao.upsert(conversationEntity)
        messageNodeDao.deleteByConversationId(conversation.id.toString())
        for (node in conversation.messageNodes) {
            messageNodeDao.upsert(nodeEntity)
        }
    }
}
```

---

## TypeConverters

Only one TypeConverter exists (`AppDatabase.kt`):

```kotlin
object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String = JsonInstant.encodeToString(usage)
    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? = JsonInstant.decodeFromString(usage)
}
```

Other complex types are stored as JSON strings and deserialized manually in repositories using `JsonInstant.decodeFromString<T>()`.

---

## Migrations

### Strategy

- **Simple schema changes** (add column, delete column): Use `@AutoMigration`
- **Complex data transformations**: Use manual `Migration` objects

### AutoMigration Example

```kotlin
@Database(
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
    ]
)
```

For column deletions, create a spec class:
```kotlin
@DeleteColumn(tableName = "conversationentity", columnName = "usage")
class Migration_8_9 : AutoMigrationSpec
```

### Manual Migration Pattern

```kotlin
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(11, 12)
        try {
            // 1. Create new table
            db.execSQL("CREATE TABLE IF NOT EXISTS ...")
            // 2. Migrate data (iterate, transform JSON, insert)
            // 3. Clean up old columns/tables
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
```

### Migration Rules

1. Always call `DatabaseMigrationTracker.onMigrationStart/End()` in manual migrations
2. Use `Log.i(TAG, ...)` to log migration progress
3. Handle `SQLiteBlobTooBigException` gracefully (skip oversized rows, don't crash)
4. Register manual migrations in `DataSourceModule.kt` via `.addMigrations()`
5. Export schema files to `app/schemas/`

---

## Full-Text Search (FTS5)

**File**: `data/db/fts/MessageFtsManager.kt`

FTS5 virtual table `message_fts` uses `tokenize = 'simple'` (jieba Chinese tokenizer via native `libsimple`).

```kotlin
// Insert
fun insertMessage(conversationId: String, nodeId: String, content: String)

// Search
fun searchMessages(query: String): List<FtsSearchResult>  // Uses simple_snippet() and jieba_query()

// Delete
fun deleteConversation(conversationId: String)
```

FTS table is created in `RoomDatabase.Callback.onOpen()` (not managed by Room's entity system).

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Storing complex objects without JSON serialization | Use `TEXT` column + `JsonInstant.encodeToString()/decodeFromString()` |
| Forgetting `@ColumnInfo(name = "snake_case")` | Always use explicit snake_case column names |
| Using `Flow` for one-shot operations | Use `suspend fun` for lookups/inserts/deletes |
| Multi-table writes without transaction | Wrap in `database.withTransaction {}` |
| Creating migration without tracker | Always call `DatabaseMigrationTracker.onMigrationStart/End()` |
| Returning full entities for list views | Use lightweight projections to exclude heavy fields |
