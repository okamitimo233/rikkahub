# Integrate codeagent into Trellis Workflow

## Goal

将 `codeagent-wrapper`（用户级别的多后端 AI 执行工具）作为 Trellis 定制化的一部分集成，使其能够在 Trellis 工作流中作为替代或补充的 agent 执行路径使用。

## What I already know

### codeagent-wrapper 核心能力

| 能力 | 详情 |
|------|------|
| 多后端 | Codex (OpenAI)、Claude (Anthropic)、Gemini (Google) |
| 文件引用 | `@file` 语法直接引用代码文件 |
| 并行执行 | `--parallel` 模式，支持任务依赖关系和 per-task backend |
| 会话恢复 | `resume <session_id>` 继续之前的会话 |
| HEREDOC | 支持复杂任务描述 |
| 超时控制 | `CODEX_TIMEOUT` 环境变量，默认 2 小时 |
| 并发限制 | `CODEAGENT_MAX_PARALLEL_WORKERS` |

### 各后端适用场景

| Backend | 强项 |
|---------|------|
| Codex | 深度代码分析、大规模重构、算法优化 |
| Claude | 快速功能实现、文档生成、prompt 工程 |
| Gemini | UI 组件原型、设计系统实现 |

### Trellis 现有 agent 系统

- 使用 Claude Code 内置 `Agent` tool 派生 subagent
- 通过 `inject-subagent-context.py` hook 注入上下文
- 支持 implement / check / debug / research 四种 agent
- `ralph-loop.py` 对 check agent 做质量门控
- 所有 agent 的上下文通过 JSONL 文件配置

### 关键差异

| 维度 | Trellis Agent (内置) | codeagent-wrapper |
|------|---------------------|-------------------|
| 执行方式 | Claude Code Agent tool | 外部进程 (Bash) |
| 上下文注入 | Hook 自动注入 | 需手动通过 `@file` 或 HEREDOC |
| 后端 | 仅 Claude | Codex / Claude / Gemini |
| 并行 | worktree-based | 原生 `--parallel` 模式 |
| 质量门控 | Ralph Loop (SubagentStop hook) | 无内置门控 |
| 会话恢复 | 不支持 | 支持 |

## Assumptions (temporary)

* codeagent-wrapper 已安装在用户 PATH 中 (confirmed: `/c/Users/Lin/bin/codeagent-wrapper`)
* 它是用户级别工具，不应作为项目依赖
* 集成应该是可选的，不破坏现有 Trellis 工作流

## Open Questions

1. **定位问题**：codeagent 在 Trellis 中应该扮演什么角色？
   - A) 替代现有 Agent 系统（全面替换）
   - B) 作为补充执行路径（某些场景使用 codeagent，某些用内置 Agent）
   - C) 仅用于 parallel 模式（`/trellis:parallel` 的底层实现）

## Requirements (evolving)

* 集成方案必须记录在 `trellis-local` skill 中
* 不修改 `trellis-meta` 原始文档
* 保持现有工作流向后兼容

## Acceptance Criteria (evolving)

* [ ] codeagent 集成方案记录在 trellis-local
* [ ] 相关 hook/command 文件创建或更新
* [ ] 使用文档清晰

## Definition of Done (team quality bar)

* trellis-local skill 更新
* 相关文件创建/修改完成
* 文档完整

## Out of Scope (explicit)

* 修改 codeagent-wrapper 本身
* 将 codeagent 作为项目依赖安装
* 修改 trellis-meta 原始文档

## Technical Notes

* codeagent-wrapper 位于: `/c/Users/Lin/bin/codeagent-wrapper` (Go binary)
* SKILL.md 位于: `~/.claude/skills/codeagent/SKILL.md`
* Trellis hook 入口: `.claude/hooks/inject-subagent-context.py`
* Trellis parallel: `.claude/commands/trellis/parallel.md`
