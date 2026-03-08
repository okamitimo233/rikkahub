---
name: trellis-local
description: |
  Project-specific Trellis customizations for RikkaHub.
  This skill documents modifications made to the vanilla Trellis system
  in this project. Inherits from trellis-meta for base documentation.
---

# Trellis Local - RikkaHub

## Base Version

Trellis version: 0.3.0
Date initialized: 2026-03-07

## Integrated Skills

### Project-Level Skills

| Skill | Target | Spec File | Date |
|-------|--------|-----------|------|
| `find-hugeicons` | frontend | `spec/frontend/icon-search-guide.md` | 2026-03-07 |
| `locale-tui-localization` | frontend | `spec/frontend/localization-guidelines.md` | 2026-03-07 |
| `simplify` | frontend + backend | Updated `quality-guidelines.md` in both | 2026-03-07 |
| `publish-release` | guides | `spec/guides/release-workflow.md` | 2026-03-07 |

### User-Level Skills (Integrated as Supplementary Execution Path)

| Skill | Location | Role | Date |
|-------|----------|------|------|
| `codeagent` | `~/.claude/skills/codeagent/` | Supplementary agent backend via `codeagent-bridge.py` | 2026-03-07 |

## Customizations

### Commands Added

#### /publish-release

- **File**: `.claude/commands/publish-release.md`
- **Purpose**: Generate bilingual changelog from git log and create GitHub release with APK upload
- **Added**: Pre-existing

### Agents Modified

(none)

### Hooks Changed

(none)

### Specs Customized

#### frontend/icon-search-guide.md

- **Purpose**: HugeIcons search workflow using `find-hugeicons` skill
- **Added**: 2026-03-07

#### frontend/localization-guidelines.md

- **Purpose**: Android i18n workflow using `locale-tui` tool
- **Added**: 2026-03-07

#### frontend/quality-guidelines.md

- **Change**: Added `/simplify` skill reference in code review section
- **Modified**: 2026-03-07

#### backend/quality-guidelines.md

- **Change**: Added `/simplify` skill reference in code review section
- **Modified**: 2026-03-07

#### guides/release-workflow.md

- **Purpose**: Release process using `publish-release` command
- **Added**: 2026-03-07

### Workflow Changes

#### Codeagent as Supplementary Execution Path

- **Bridge script**: `.trellis/scripts/codeagent_bridge.py`
- **Role**: Alternative agent backend for dispatch — uses GPT (Codex) or Gemini (OpenCode) instead of Claude Code's built-in Agent tool
- **Added**: 2026-03-07

**Backend mapping:**

| Backend | CLI Tool | Model | Best For |
|---------|----------|-------|----------|
| `codex` | Codex CLI | GPT | Deep code analysis, large-scale refactoring |
| `gemini` | OpenCode CLI | Gemini | UI prototyping, design system |
| `claude` | _(not used)_ | — | Already inside Claude Code, redundant |

**When to use codeagent vs built-in Agent:**

| Scenario | Use | Why |
|----------|-----|-----|
| Standard implement/check/debug | Built-in Agent | Hook injection + Ralph Loop quality gate |
| Deep analysis needing GPT reasoning | codeagent (codex) | GPT's strength in code analysis |
| UI/design prototyping | codeagent (gemini) | Gemini's visual understanding |
| Research with specific model preference | codeagent (codex/gemini) | Model diversity |
| Parallel multi-backend tasks | codeagent --parallel | Native parallel support with per-task backend |

**Usage from dispatch:**

```bash
# Preview what will be sent
python3 .trellis/scripts/codeagent_bridge.py preview implement --backend codex

# Execute
python3 .trellis/scripts/codeagent_bridge.py run implement --backend codex

# With extra instructions
python3 .trellis/scripts/codeagent_bridge.py run implement --backend gemini --extra-prompt "Focus on Compose UI"
```

**Limitations:**
- No Ralph Loop (SubagentStop hook) — codeagent runs via Bash, not Agent tool
- No automatic context injection via hooks — bridge script handles context manually
- Check phase recommended to use built-in Agent for quality enforcement

---

## Changelog

### 2026-03-07

- Initial setup
- Integrated `find-hugeicons`, `locale-tui-localization`, `simplify`, `publish-release` skills
- Integrated `codeagent` as supplementary execution path with `codeagent_bridge.py`
  - Backend mapping: codex→GPT, gemini→OpenCode, claude→skip (redundant)
  - Bridge script reads Trellis JSONL context and converts to `@file` references
