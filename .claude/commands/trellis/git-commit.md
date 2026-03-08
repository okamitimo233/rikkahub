# Git Smart Commit

Analyze all uncommitted changes, intelligently split them into logical commits following the project's commit convention, and push to origin.

**Goal**: Clean git working directory with all changes committed and pushed.

## Steps

### 1. Gather Context

```bash
# Get current branch
git branch --show-current

# Get all uncommitted changes (staged + unstaged + untracked)
git status

# Get detailed diff for staged and unstaged changes
git diff
git diff --cached

# Get recent commit style for reference
git log --oneline -15
```

### 2. Analyze and Group Changes

Review all changes and group them into logical commits based on:

- **Scope**: Files in the same module/feature area
- **Type**: feat / fix / docs / refactor / test / chore / i18n / perf / tools
- **Dependency**: Changes that logically belong together (e.g., a new feature + its tests)

**Grouping rules**:
- Related changes across multiple files = 1 commit (e.g., adding a UI page + its ViewModel)
- Unrelated changes in the same file = split if possible, otherwise group by primary purpose
- Config/build changes = separate commit unless directly tied to a feature
- Trellis workflow files (.trellis/, .claude/) = separate `chore` or `docs` commit

### 3. Present Commit Plan

Before executing, present the plan to the user:

```
## Commit Plan

### Commit 1: type(scope): description
Files:
- path/to/file1
- path/to/file2
Reason: <why these are grouped>

### Commit 2: type(scope): description
Files:
- path/to/file3
Reason: <why these are grouped>

---
Total: N commits

Proceed with this plan? [Approve / Modify / Cancel]
```

**Interactive**: If grouping is ambiguous, ask the user:
- "Should X and Y be in the same commit or separate?"
- "What scope should this change use: A or B?"

### 4. Execute Commits

For each commit in the plan (in dependency order):

```bash
# Stage specific files for this commit
git add <file1> <file2> ...

# Commit with conventional message
git commit -m "type(scope): description"
```

**Commit message format** (match project convention):
- `feat(scope): add new feature`
- `fix(scope): fix specific issue`
- `docs: update documentation`
- `chore: maintenance task`
- `refactor(scope): restructure code`
- `perf(scope): performance improvement`
- `i18n: localization update`
- `tools: tooling/workflow changes`

**Language**: Match the project's existing style - use English for type/scope, description can be Chinese or English based on content (reference recent commits).

### 5. Push to Origin

```bash
# Push current branch to origin
git push origin HEAD
```

If push fails:
1. Check if remote has new commits: `git fetch origin`
2. If behind, ask user: "Remote has new commits. Rebase or merge?"
3. Handle conflicts interactively if they arise
4. Retry push after resolution

### 6. Verify Clean State

```bash
git status
git log --oneline -N  # Show the N new commits
```

Confirm:
- Working directory is clean
- All commits are pushed
- No untracked files remain (unless intentionally ignored)

## Edge Cases

- **Empty changes**: If no uncommitted changes, report "Working directory already clean" and exit
- **Only untracked files**: Ask user which ones to commit vs. add to `.gitignore`
- **Large binary files**: Warn user before committing, suggest `.gitignore` if appropriate
- **Sensitive files** (`.env`, credentials): **NEVER commit** - warn user immediately
