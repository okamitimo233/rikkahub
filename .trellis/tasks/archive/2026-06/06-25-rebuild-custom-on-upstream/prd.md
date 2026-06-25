# 基于上游 HEAD 重建定制分支

## Goal

在上游最新代码（master 分支）的基础上重新建立 custom 分支的定制修改，解决当前 rebase 过程中大量冲突的问题。

## Background

- 这是 `rikkahub/rikkahub` 的下游 fork，使用双分支策略（`master` 镜像上游，`custom` 包含定制）
- 当前 `custom` 分支有 81 个定制提交
- 尝试通过 `scripts/sync-upstream.sh` 同步上游时，在 rebase 过程中遇到大量冲突：
  - Firebase 依赖移除 vs 上游新功能
  - Gradle 构建优化 vs 上游配置变更
  - Baseline profile 配置
  - 测试代码调整
  - 多个文件有重复冲突模式
- 上游新增了重要功能（workspace 模块等）和大量改进

## Confirmed Facts from Inspection

**Fork 策略**（来自 `FORK.md`）：
- `master`: 仅快进合并上游，永不直接提交
- `custom`: 所有定制代码，每次同步后 rebase 到 master
- 同步流程：fetch upstream → fast-forward master → rebase custom → force-push with lease

**当前 custom 分支的主要定制类型**（来自 git log）：
1. **Trellis 工作流相关**：最近 3 个提交（Kiro 平台支持、workflow 文档重构、版本升级）
2. **构建优化**：Gradle JVM 参数调优、NDK ABI 过滤、configuration cache
3. **依赖管理**：移除 Firebase、添加阿里云镜像
4. **功能回退**：3 个 Revert 提交（journal、左滑工作区、任务归档）
5. **签名配置**：ignore release keystore
6. **历史合并点**：774549aa 是一个上游合并提交

**冲突根源分析**：
- Firebase 移除与上游持续使用 Firebase 产生系统性冲突
- 构建配置的深度定制与上游优化方向不同
- 部分定制可能已被上游以不同方式实现

## Requirements

### 必须保留的定制

**R1**: Trellis 工作流集成（最新 3 个提交）
- Kiro 平台支持
- 简化的工作流文档
- 版本 0.6.4

**R2**: 构建配置优化
- 自定义 Gradle JVM 参数
- NDK ABI 限制为 arm64-v8a
- Configuration cache 配置

**R3**: 签名和发布配置
- Release keystore 的 gitignore
- 本地签名配置逻辑

### 需要评估的定制

**E1**: Firebase 依赖移除
- 是否仍需要移除？
- 上游是否有替代的崩溃报告方案？

**E2**: 阿里云 Gradle 镜像
- 是否仍需要？
- 对构建性能的影响？

**E3**: 功能回退（3 个 Revert）
- 为什么回退这些功能？
- 上游是否已修复相关问题？

**E4**: Markdown 表格功能增强（提交 11317613）
- 内容：表格改为卡片布局，新增复制和导出 CSV 功能
- 评估：这是 re-ovo（上游作者）的提交，可能已经在上游 master 中
- 需要确认：此功能是否已合并到上游？

**E5**: Model custom params 修复（提交 001f096f）
- 内容：修复后台生成时应用 model custom params
- 评估：这是来自 Muly Oved 的 PR #1282，可能已经在上游 master 中
- 需要确认：此修复是否已合并到上游？

### 操作要求

**O1**: 在新分支上重建，不破坏现有 custom 分支
**O2**: 逐个应用定制，每个定制都可独立验证
**O3**: 清理过时或冗余的定制
**O4**: 记录每个定制的保留/丢弃决策

## Acceptance Criteria

- [ ] 创建新的工作分支 `custom-rebuild` 从 `master` HEAD 开始
- [ ] 应用必须保留的定制：
  - [ ] Trellis 工作流集成（Kiro 平台支持、workflow 文档、版本 0.6.4）
  - [ ] 移除 Firebase 依赖
  - [ ] Gradle 构建优化（JVM 参数、configuration cache 等）
  - [ ] NDK ABI 限制为 arm64-v8a
  - [ ] 签名和发布配置
- [ ] 编译通过：`./gradlew :app:assembleDebug` 成功
- [ ] 单元测试通过：`./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest` 成功
- [ ] 记录每个定制的迁移状态（已应用/已跳过/上游已包含）
- [ ] 验证通过后，删除旧 custom 分支，将 custom-rebuild 重命名为 custom
- [ ] Force-push 新的 custom 分支到 origin

## Out of Scope

- 添加新的定制功能
- 修复上游代码的 bug
- 性能优化（除非是保留现有优化）
- 完整的功能测试（仅验证构建和单元测试）

## Decisions

### D1: Firebase 依赖 - 移除 ✓
**原因**：不需要云端崩溃报告功能（个人 fork）
**影响**：
- 减少 APK 体积约 2-3MB
- 每次上游同步需要解决 Firebase 相关冲突（通常 2-3 个文件）
- 移除文件：`app/build.gradle.kts` 中的 Firebase 插件、`gradle/libs.versions.toml` 中的版本声明、`app/src/main/res/xml/remote_config_defaults.xml`

### D2: 功能回退 - 不重新应用 ✓
**原因**：不需要以下功能：
- journal 记录功能
- 左滑工作区 hub 页面及动画
- 相关的任务归档
**影响**：
- 在新分支中不需要 revert 这些上游提交
- 如果上游仍包含这些功能，需要识别并 revert 或选择性地不合并相关提交

### D3: 重建策略 - 分类批量应用 ✓
**方案**：将 81 个历史提交按类型分类，每类合并为一个新提交手动应用
**分类计划**：
1. **Trellis 工作流集成**（最新 3 个提交）
2. **移除 Firebase 依赖**
3. **Gradle 构建优化**（JVM 参数、configuration cache、并行构建等）
4. **NDK 和构建工具配置**
5. **签名和发布配置**
6. **其他必要的定制**（待识别）

**优势**：
- 清晰的提交历史，每个提交代表一类完整定制
- 减少未来同步冲突
- 每类修改可独立验证

### D4: 分支替换策略 - 直接替换 ✓
**方案**：验证通过后，直接删除旧 custom 分支，将 custom-rebuild 重命名为 custom
**操作步骤**（在验证通过后执行）：
```bash
git branch -D custom
git branch -m custom-rebuild custom
git push --force-with-lease origin custom
git push origin :custom  # 删除远程旧分支（如果需要）
```

### D5: Markdown 表格功能 - 已在上游 ✓
**确认**：提交 `dd67ed32`（上游 master）与 `11317613`（custom）是同一个功能
**结论**：无需额外应用，上游已包含此功能

### D6: Model custom params 修复 - 已在上游 ✓
**确认**：提交 `e60edadd`（上游 master）与 `001f096f`（custom）是同一个 PR #1282
**结论**：无需额外应用，上游已包含此修复

### D7: 验证范围 - 最小验证 ✓
**方案**：快速验证，确保编译和单元测试通过
**验证步骤**：
1. `./gradlew :app:assembleDebug` - 编译通过
2. `./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest` - 单元测试通过
**预计时间**：5-10 分钟

## Open Questions
