# Backend Development Guidelines

> Best practices for backend (data layer) development in this project.

---

## Overview

This directory contains guidelines for backend development. RikkaHub's "backend" is the data layer: Room database, repositories, DataStore preferences, AI provider integration, and sync clients.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Multi-module structure, data layer organization | Done |
| [Database Guidelines](./database-guidelines.md) | Room entities, DAOs, queries, migrations, FTS | Done |
| [Error Handling](./error-handling.md) | Error types, propagation, Result pattern | Done |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, DI, testing, forbidden patterns | Done |
| [Logging Guidelines](./logging-guidelines.md) | Log levels, structured logging, HTTP logging | Done |

---

## Quick Reference

### Key Conventions

- **Database**: Room v17, KSP for annotation processing, `snake_case` columns
- **DI**: Koin — singletons for data layer, `viewModelOf()` for VMs
- **Serialization**: `kotlinx.serialization` with `@SerialName` discriminators
- **Error handling**: Let exceptions propagate; `Result<T>` only for network clients
- **Migrations**: `AutoMigration` for simple changes, manual `Migration` for data transforms
- **Logging**: `android.util.Log` with `TAG` constant per file

---

**Language**: All documentation should be written in **English**.
