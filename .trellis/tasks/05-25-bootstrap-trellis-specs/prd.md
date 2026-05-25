# Bootstrap Trellis specs from codebase

## Goal

Refresh `.trellis/spec/` so it accurately describes the current RikkaHub repository and gives future coding agents practical, source-backed guidance for Android/Kotlin, embedded web server, and `web-ui` TypeScript work.

## Confirmed Facts

- RikkaHub is a multi-module Android project with `:app`, `:ai`, `:search`, `:speech`, `:document`, `:highlight`, `:common`, `:web`, and `:material3` modules declared in `settings.gradle.kts`.
- The app uses Kotlin, Jetpack Compose, Navigation 3, Koin, Room, DataStore, OkHttp/SSE, Ktor, kotlinx.serialization, and a React/TypeScript `web-ui` embedded behind the Android web server.
- Existing `.trellis/spec/backend` and `.trellis/spec/frontend` indexes are template placeholders; guide files need to be rewritten with real project patterns.
- Existing `.trellis/spec/guides` contains generic thinking guides that can remain if their index is made consistent with the final spec set.

## Requirements

- Replace template wording in `.trellis/spec/` with concrete guidance derived from current source files.
- Preserve or reshape the existing backend/frontend/guides split only where it matches the codebase.
- Cover Android/Kotlin module layout, data persistence, provider/message transformation flow, Compose UI/ViewModel patterns, web server/API boundaries, and `web-ui` React/TypeScript patterns.
- Include real file paths and examples or anti-patterns for important rules.
- Update all affected `index.md` files so they match the final spec file set.
- Avoid generic boilerplate, empty headings, placeholder status fields, or speculative conventions not visible in the repo.

## Acceptance Criteria

- [ ] `.trellis/spec/backend` documents Android/Kotlin backend/data/service/API conventions with source-backed examples.
- [ ] `.trellis/spec/frontend` documents Compose UI and `web-ui` frontend conventions with source-backed examples.
- [ ] `.trellis/spec/guides` indexes remain accurate and do not contradict project-specific specs.
- [ ] Every retained spec file has actionable guidance, real file paths, and no `TBD`, `To fill`, placeholder text, or copied template instructions.
- [ ] Index files link only to existing spec files and describe their actual content.
- [ ] Verification checks confirm placeholder removal and link consistency.

## Out of Scope

- Changing application source code or tests.
- Reorganizing production modules.
- Adding new Trellis workflow behavior outside `.trellis/spec/`.
- Creating exhaustive API docs for every route or class.
