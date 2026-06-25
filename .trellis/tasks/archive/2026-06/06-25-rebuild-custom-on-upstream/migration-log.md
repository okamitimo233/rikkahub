# Custom Branch Rebuild Migration Log

## Backup

- Old `custom` HEAD: `90c71eb99bae29b6e97171c7847ca024a2b9b9c0`
- Recorded in: `.trellis/tasks/06-25-rebuild-custom-on-upstream/backup.txt`

## New branch

- Created `custom-rebuild` from `master` HEAD `9d046020fa472a8f372cf7e226109873aa9c7926`.
- After validation, renamed `custom-rebuild` to `custom` and force-pushed to `origin` with `--force-with-lease`.

## New commit history on `custom`

```
0a4fa122 fix: stabilize build verification (time reminder & share sheet tests)
14dc5f09 fix(build): 恢复移除 Firebase 时误删的 version catalog 条目
4de59180 chore: 配置 release 签名和 gitignore
e54acd9d build: 优化 Gradle 构建性能配置
f28e59d6 chore: 移除 Firebase 依赖以减小 APK 体积
5bde5f9b feat(trellis): 集成 Trellis 工作流 v0.6.4 with Kiro 平台支持
9d046020 feat(provider): 新增随想AI网关赞助商及提供商推荐  <-- master/upstream HEAD
```

## Customizations applied

| Category | Status | Commit | Notes |
|----------|--------|--------|-------|
| Trellis workflow v0.6.4 + Kiro | Applied | `5bde5f9b` | Latest 3 custom commits combined: Kiro support, workflow doc rewrite, version bump. |
| Remove Firebase dependencies | Applied | `f28e59d6` | Removed plugins, libraries, code references, `remote_config_defaults.xml`, and root build plugin declarations. |
| Version catalog cleanup fix | Applied | `14dc5f09` | Restored `android-library`, `kotlin-serialization`, `lucide-icons`, `huge-icons`, `image-viewer` entries accidentally removed with Firebase entries. |
| Gradle build optimizations | Applied | `e54acd9d` | JVM args, parallel/cache/config-cache, build-tools pinning, Java 17/minSdk 26 across modules, dependency visibility tightening, web build/copy split. |
| NDK ABI filter arm64-v8a | Applied | Included in `f28e59d6`/`e54acd9d` | `app/build.gradle.kts` splits limited to `arm64-v8a`, `workspace/build.gradle.kts` `ndk.abiFilters` set to `arm64-v8a`. |
| Release signing / `.gitignore` | Applied | `4de59180` | Ignores `rikkahub-release.keystore`, `.codegraph/`, `.claude/settings.local.json`, `.claude/worktrees/`. |
| Build verification test fixes | Applied | `0a4fa122` | Time-reminder injection logic and ShareSheetTest expectations from custom `582121b7`. |

## Customizations skipped

| Category | Status | Reason |
|----------|--------|--------|
| Revert journal / left-swipe workspace / task archive | Skipped | Confirmed not needed per PRD D2. |
| Markdown table card layout + CSV export | Skipped | Already in upstream master (PRD D5). |
| Model custom params background fix | Skipped | Already in upstream master (PRD D6). |
| Aliyun Gradle mirror | Skipped | Evaluated; not required for build and not in the required customization list. |
| Kotlin/KSP version downgrade | Skipped | Kept upstream versions to avoid unnecessary compatibility risk. |

## Validation results

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (ran after each major step).
- `./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest` — **BUILD SUCCESSFUL**, all tests pass after applying test-stabilization fixes.

## Issues encountered

1. **Missing version catalog entries** — Removing Firebase entries accidentally removed adjacent `android-library`, `kotlin-serialization`, `lucide-icons`, `huge-icons`, and `image-viewer` entries. Fixed in `14dc5f09`.
2. **CMake / Ninja not installed** — The workspace native build required CMake 4.3.4 and Ninja. Installed via `sudo pacman -S --noconfirm cmake ninja`.
3. **Pre-existing unit test failures** — `TimeReminderTransformerTest` and `ShareSheetTest` failed on upstream master behavior. Applied custom fixes from commit `582121b7` to stabilize verification.


- Local branch: `master` (checked out)
- New `custom` branch points to `0a4fa122` and has been force-pushed to `origin`.
- Remote old `custom` (`90c71eb9`) replaced by new history.

## Post-implementation verification

- Re-ran `./gradlew :app:assembleDebug :app:testDebugUnitTest :ai:testDebugUnitTest` in a clean `git worktree` of the rebuilt `custom` branch (after `git submodule update --init --recursive`).
- Result: **BUILD SUCCESSFUL** — compile and unit tests pass.
- `./gradlew lint` on the same worktree reports **43 errors / 255 warnings**. The first errors are in `ChatMessageTranslation.kt` and other upstream files not touched by this rebuild; they appear to be pre-existing upstream lint issues, not caused by the customizations.

## Lessons learned

- When verifying a branch in a fresh `git worktree`, remember to initialize submodules (`material3/material-color-utilities`) or the `material3` module will fail with unresolved `dynamiccolor`/`DynamicScheme` references.
- The custom `.trellis/` integration replaced the old `.trellis/scripts/task.py` / `get_context.py` helpers; task lifecycle commands now rely on the new Trellis runtime/CLI, so the old scripts are only available in `.trellis/.backup-*/`.
