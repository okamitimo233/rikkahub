# Fork Workflow

This repository is a fork of [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub).
We maintain customizations downstream **without** sending pull requests upstream,
while continuously pulling upstream changes.

## Branch Strategy (Dual-Track Branches + Rebase)

| Branch | Role | Rules |
|---|---|---|
| `master` | Mirror of `upstream/master` | Fast-forward only. Never commit directly. Protected by GitHub ruleset: restrict deletions, require linear history, block force pushes. |
| `custom` | Main development branch | All fork-specific commits live here. Default branch on GitHub. Rebased onto `master` after each upstream sync. |

### Remotes

```
origin    git@github.com:okamitimo233/rikkahub.git    (your fork)
upstream  git@github.com:rikkahub/rikkahub.git        (original repo)
```

## Daily Development

- Always work on `custom`:
  ```bash
  git checkout custom
  # ... edit, commit, push as usual ...
  git push origin custom
  ```
- Never commit on `master`. If you accidentally do, move the commit:
  ```bash
  git stash       # if uncommitted
  # or
  git reset --soft HEAD~1 && git stash    # if already committed
  git checkout custom && git stash pop
  ```

## Syncing With Upstream

Run the sync script when upstream has new commits:

```bash
bash scripts/sync-upstream.sh           # interactive, asks before force-push
bash scripts/sync-upstream.sh --yes     # non-interactive
```

What it does:

1. `git fetch upstream`
2. Fast-forward `master` to `upstream/master`
3. Push `master` to `origin` (allowed by branch ruleset since it's fast-forward)
4. Rebase `custom` onto the updated `master`
5. Force-push `custom` to `origin` with `--force-with-lease`

### Resolving Conflicts During Sync

If rebase hits a conflict, the script stops and tells you. Resolve manually:

```bash
# Edit conflicting files, then:
git add <files>
git rebase --continue

# Or abort the entire sync:
git rebase --abort

# After successful continue, push manually:
git push --force-with-lease origin custom
```

**Never use `git push --force` (without `-with-lease`)** — it can overwrite teammates' work
without warning. `--force-with-lease` refuses to push if the remote moved since your last fetch.

## Minimizing Rebase Conflicts

Deep customizations are inevitable. To keep rebases manageable:

- **Isolate new features in their own modules or files.** The project is already split into
  multiple modules (`app`, `ai`, `common`, `document`, `highlight`, `material3`, `search`,
  `speech`, `web`). Add a new module (e.g. `custom`) for fork-specific code rather than
  scattering changes across upstream files.
- **Keep hook points minimal.** When you must touch upstream code, leave only a one-line
  injection: a function call, a single `if` branch, or a Koin module registration. The bulk
  of the logic lives in your isolated module.
- **Prefer extension points already in the project**:
  - Add an `InputMessageTransformer` / `OutputMessageTransformer` rather than modifying
    chat handling directly.
  - Register your `SearchProvider`, AI provider, TTS engine, etc. via the existing DI/registry
    mechanisms rather than patching the dispatch logic.
- **Avoid cosmetic edits to upstream files.** Renaming variables, reformatting, or moving
  code around in upstream files will conflict on every rebase for no real benefit.

## Versioning

Fork version numbers and release cadence are independent of upstream. Treat `versionCode` /
`versionName` in `app/build.gradle.kts` as fork-owned. Conflicts here during sync should
usually resolve in favor of the fork's version.

## GitHub Configuration

- **Default branch**: `custom`
- **Branch protection (rulesets) on `master`**:
  - Restrict deletions
  - Require linear history
  - Block force pushes
- **No protection on `custom`** (force-push-with-lease is required for rebase syncs).

## Related Files

- `scripts/sync-upstream.sh` — the sync automation
- `CLAUDE.md` — project-wide AI assistant guidance (mostly inherited from upstream)
