#!/usr/bin/env bash
# Sync the fork with upstream and rebase custom branch on top.
#
# Workflow (Plan B: dual-track branches + rebase):
#   - master         : mirror of upstream/master, fast-forward only
#   - custom         : main development branch with all customizations
#
# Steps:
#   1. Fetch upstream
#   2. Fast-forward master to upstream/master
#   3. Push master to origin
#   4. Rebase custom onto the updated master
#   5. Force-push custom (with --force-with-lease for safety)
#
# Usage:
#   bash scripts/sync-upstream.sh           # interactive, asks before force-push
#   bash scripts/sync-upstream.sh --yes     # non-interactive

set -euo pipefail

UPSTREAM_REMOTE="upstream"
UPSTREAM_BRANCH="master"
MIRROR_BRANCH="master"
CUSTOM_BRANCH="custom"
ORIGIN_REMOTE="origin"

YES=false
if [[ "${1:-}" == "--yes" || "${1:-}" == "-y" ]]; then
  YES=true
fi

require_clean_tree() {
  if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: Working tree is not clean. Commit or stash changes first." >&2
    exit 1
  fi
}

confirm() {
  if $YES; then return 0; fi
  read -r -p "$1 [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]]
}

original_branch=$(git rev-parse --abbrev-ref HEAD)
require_clean_tree

echo ">>> Fetching $UPSTREAM_REMOTE ..."
git fetch "$UPSTREAM_REMOTE" --prune

echo ">>> Updating $MIRROR_BRANCH (fast-forward only) ..."
git checkout "$MIRROR_BRANCH"
git merge --ff-only "$UPSTREAM_REMOTE/$UPSTREAM_BRANCH"
git push "$ORIGIN_REMOTE" "$MIRROR_BRANCH"

new_commits=$(git rev-list --count "$CUSTOM_BRANCH..$MIRROR_BRANCH")
echo ">>> $MIRROR_BRANCH is $new_commits commit(s) ahead of $CUSTOM_BRANCH base."

echo ">>> Rebasing $CUSTOM_BRANCH onto $MIRROR_BRANCH ..."
git checkout "$CUSTOM_BRANCH"
if ! git rebase "$MIRROR_BRANCH"; then
  echo
  echo "Rebase paused due to conflicts."
  echo "Resolve conflicts, then run:"
  echo "  git add <files> && git rebase --continue"
  echo "Or abort with:"
  echo "  git rebase --abort"
  exit 1
fi

echo
echo ">>> Rebase complete."
git --no-pager log --oneline "$ORIGIN_REMOTE/$CUSTOM_BRANCH..$CUSTOM_BRANCH" || true

if confirm "Force-push $CUSTOM_BRANCH to $ORIGIN_REMOTE (with --force-with-lease)?"; then
  git push --force-with-lease "$ORIGIN_REMOTE" "$CUSTOM_BRANCH"
  echo ">>> Pushed."
else
  echo ">>> Skipped push. Run manually when ready:"
  echo "    git push --force-with-lease $ORIGIN_REMOTE $CUSTOM_BRANCH"
fi

if [[ "$original_branch" != "$CUSTOM_BRANCH" && "$original_branch" != "HEAD" ]]; then
  git checkout "$original_branch"
fi
