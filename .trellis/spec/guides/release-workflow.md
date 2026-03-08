# Release Workflow

> How to create a release using the `/publish-release` command.

---

## Overview

RikkaHub releases are created via the `/publish-release` slash command, which generates a bilingual changelog from git history and publishes a GitHub release with the APK artifact.

---

## When to Use

- A set of features/fixes is ready for release
- The user explicitly requests a release

---

## Process

### Step 1: Generate Changelog

The command automatically:

1. Reads git log from the last release tag to current HEAD
2. Summarizes changes into a concise bilingual (Chinese + English) changelog
3. Limits to **10 items max** — merges bug fixes and UI tweaks where possible
4. Avoids technical jargon in changelog entries

### Step 2: User Confirmation

The generated changelog is presented for review. **Release only proceeds after explicit user approval.**

### Step 3: Create GitHub Release

Using GitHub CLI (`gh`):

- **Release title**: Version number
- **Release body**: Bilingual changelog
- **Artifact**: `app/release/*.apk` (arm64 only, renamed with version number)

---

## Changelog Format

```markdown
更新内容:

- Feature/fix description in Chinese
- ...

Updates:

- Feature/fix description in English
- ...
```

---

## Key Rules

| Rule | Detail |
|------|--------|
| Max entries | 10 items |
| Language | Bilingual (Chinese + English) |
| Tone | User-friendly, no technical jargon |
| APK | arm64 only, renamed with version in filename |
| Confirmation | **Required** before publishing |
| Merge small changes | Combine related bug fixes and UI adjustments |

---

## Invocation

```
/publish-release
```

No arguments needed — the command handles everything interactively.
