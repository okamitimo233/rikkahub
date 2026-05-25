# Implementation Plan

## Checklist

1. Inspect all existing `.trellis/spec/**/*.md` files for placeholders and current structure.
2. Rewrite backend specs with source-backed Android/Kotlin guidance:
   - module and package ownership
   - Room/DataStore/repository patterns
   - provider/message transformer/service boundaries
   - Ktor web API route patterns
   - logging, error handling, and quality rules
3. Rewrite frontend specs with source-backed UI guidance:
   - Compose page/component/ViewModel patterns
   - local state hooks and lifecycle-aware collection
   - `web-ui` React components, hooks, stores, API client, and DTO types
   - UI quality, type-safety, and localization rules
4. Update backend/frontend/guides indexes to reflect final files and remove template status columns.
5. Verify:
   - no `TBD`, `To fill`, `Fill in`, or placeholder template language remains
   - index links point to existing files
   - specs mention real paths and concrete examples
   - git diff only touches `.trellis/spec/` plus this task's planning files

## Validation Commands

- `python ./.trellis/scripts/task.py current --source`
- Search `.trellis/spec` for placeholder terms.
- Check markdown links in `.trellis/spec/*/index.md` against existing files.
- `git diff -- .trellis/spec .trellis/tasks/05-25-bootstrap-trellis-specs`

## Risk and Rollback Points

- Main risk: writing generic guidance unsupported by code. Mitigation: every major rule should cite representative file paths.
- Main rollback point: documentation-only git diff; revert affected `.trellis/spec/*.md` files if needed.

## Review Gate Before Start

Planning is ready when `prd.md`, `design.md`, and `implement.md` exist and the user approves implementation.
