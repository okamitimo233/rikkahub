# Technical Design: Custom Branch Rebuild

## Overview

重建策略采用**分类批量迁移**（Categorized Batch Migration）方法，从上游 master HEAD 创建新分支，按功能类别逐批应用定制修改，而不是逐个 cherry-pick 81 个历史提交。

## Architecture Decisions

### AD1: 迁移策略选择

**选项评估**：

| 策略 | 优势 | 劣势 | 决策 |
|------|------|------|------|
| 交互式 rebase | 保留完整历史 | 需要解决大量冲突，耗时 2-3 小时 | ❌ 拒绝 |
| Cherry-pick 逐个应用 | 可选择性保留提交 | 81 个提交，仍有大量冲突 | ❌ 拒绝 |
| 分类批量迁移 | 清晰历史，减少冲突，快速完成 | 失去细粒度历史 | ✅ 采用 |
| 完全重置为上游 | 最干净 | 丢失所有定制 | ❌ 拒绝 |

**最终决策**：分类批量迁移

**原因**：
- 81 个历史提交中有大量重复修改（多次 Firebase 冲突解决、构建配置调整）
- 保留完整历史的价值低于快速完成的价值
- 分类后的 5-6 个提交足以表达定制意图
- 未来同步冲突会显著减少

---

### AD2: 分支替换策略

**备选方案**：

1. **保留双分支** (`custom-old` + `custom-rebuild`)
   - 优势：可以长期对比
   - 劣势：占用分支名，增加认知负担
   
2. **直接替换** (删除 `custom`，重命名 `custom-rebuild`)
   - 优势：清晰，符合 fork 策略
   - 劣势：依赖备份 commit hash

**决策**：直接替换 + 记录备份 hash

**保障措施**：
- 在 `backup.txt` 中记录旧 custom 的 HEAD hash
- 验证通过后才执行替换
- 30 天内可通过 reflog 恢复（Git 默认保留期）

---

### AD3: Firebase 移除的持续性

**背景**：
- 上游持续使用 Firebase Crashlytics
- 每次同步都会在 3-4 个文件产生冲突
- 个人 fork 不需要云端崩溃报告

**技术影响分析**：

| 影响维度 | 详情 |
|----------|------|
| APK 体积 | 减少约 2-3 MB |
| 编译时间 | 减少约 5-10 秒（避免 Firebase 插件处理） |
| 同步成本 | 每次需手动解决 `app/build.gradle.kts`, `gradle/libs.versions.toml` 冲突 |
| 功能损失 | 无云端崩溃报告（本地 logcat 足够） |

**决策**：继续移除

**替代方案**：
- 如果未来需要崩溃报告，考虑 Sentry / Bugsnag（更轻量）
- 或使用 ACRA（开源，本地存储）

---

### AD4: NDK ABI 限制

**当前状况**：
- 上游可能支持多个 ABI：`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
- 定制限制为仅 `arm64-v8a`

**影响分析**：

| ABI | 设备覆盖率 | APK 体积贡献 | 决策 |
|-----|-----------|-------------|------|
| arm64-v8a | ~85% (2024+) | 基准 | ✅ 保留 |
| armeabi-v7a | ~10% (老设备) | +30-40% | ❌ 移除 |
| x86 / x86_64 | <5% (模拟器/平板) | +30-40% | ❌ 移除 |

**决策理由**：
- 个人使用，目标设备明确（现代 Android 手机）
- 减少 APK 体积 40-60%
- 编译时间减少约 30%

**风险**：
- 无法在 32 位 ARM 老设备运行（可接受）
- x86 模拟器需使用 ARM translation（性能略降）

---

### AD5: Gradle 构建优化保留策略

**定制项清单**：

| 优化项 | 上游状态 | 冲突风险 | 决策 |
|--------|---------|---------|------|
| JVM heap (`-Xmx4096m`) | 可能不同 | 低（单行） | ✅ 保留 |
| `org.gradle.parallel=true` | 可能已有 | 低 | ✅ 保留 |
| `org.gradle.caching=true` | 可能已有 | 低 | ✅ 保留 |
| `org.gradle.configuration-cache=true` | 可能已有 | 低 | ✅ 保留 |
| `web/build.gradle.kts` hasZsh 检测 | 上游可能不同 | 中（逻辑块） | ✅ 保留 |

**合并策略**：
- 如果上游已有相同配置，保留上游值（避免无意义冲突）
- 如果上游更激进（如更大 heap），采用上游值
- 只保留上游缺失的优化项

---

## Data Flow

### 定制应用流程

```
master (upstream HEAD)
    |
    v
[1. 创建 custom-rebuild 分支]
    |
    v
[2. 应用 Trellis 集成]
    | - 检出 custom 的 .trellis/, .claude/hooks/, .codex/hooks/, .opencode/commands/
    | - 提交 "feat(trellis): 集成 Trellis v0.6.4"
    v
[3. 移除 Firebase]
    | - 编辑 app/build.gradle.kts (移除插件)
    | - 编辑 gradle/libs.versions.toml (移除依赖)
    | - 删除 app/src/main/res/xml/remote_config_defaults.xml
    | - 提交 "chore: 移除 Firebase 依赖"
    v
[4. 应用构建优化]
    | - 对比并合并 gradle.properties
    | - 可能修改 web/build.gradle.kts
    | - 提交 "build: 优化 Gradle 构建配置"
    v
[5. 限制 NDK ABI]
    | - 编辑 app/build.gradle.kts 的 ndk.abiFilters
    | - 提交 "build(app): 限制 NDK ABI 为 arm64-v8a"
    v
[6. 配置签名]
    | - 更新 .gitignore
    | - 确保 app/build.gradle.kts 有签名配置读取逻辑
    | - 提交 "chore: 配置 release 签名"
    v
[7. 验证构建]
    | - ./gradlew clean
    | - ./gradlew :app:assembleDebug
    | - ./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest
    |
    | [成功] ────> [8. 替换分支]
    |                   | - git branch -D custom
    |                   | - git branch -m custom-rebuild custom
    |                   | - git push --force-with-lease origin custom
    |                   v
    |              [完成]
    |
    | [失败] ────> [回滚/修复]
                     | - 保留 custom-rebuild
                     | - 分析问题
                     | - 返回失败步骤
```

---

## Risk Analysis

### R1: 编译失败风险 [中等]

**场景**：
- Trellis 相关文件与上游代码不兼容
- Firebase 移除导致代码引用残留
- Gradle 配置冲突

**缓解措施**：
- 每个定制应用后立即编译验证
- 分步提交，失败时可精确定位问题
- 保留 custom-rebuild 分支，可重新尝试

**应对计划**：
- 如果步骤 2-6 失败：保留分支，修复后继续
- 如果步骤 7 失败：检查是否遗漏定制，或选择性跳过有问题的定制

---

### R2: 测试失败风险 [低]

**场景**：
- 上游新增的测试与定制修改冲突
- Firebase 移除后相关测试失败

**缓解措施**：
- 运行完整单元测试套件：`:app:testDebugUnitTest :ai:testDebugUnitTest`
- 测试失败时检查是否为 Firebase 相关测试

**应对计划**：
- 如果是 Firebase 测试：删除或注释相关测试
- 如果是功能测试：评估是否需要调整定制策略

---

### R3: 遗漏关键定制风险 [低]

**场景**：
- 81 个提交中有未识别的重要定制
- 功能性修改被归类为"可跳过"

**缓解措施**：
- PRD 中已分析所有 custom 分支提交
- 确认 Markdown 表格、custom params 已在上游
- 功能回退（3 个 Revert）已确认不需要

**应对计划**：
- 验证后对比 `git diff master..custom-rebuild` vs `git diff master..custom`
- 如果差异过大，手动检查缺失部分
- 30 天内可通过 reflog 找回旧 custom

---

### R4: 分支替换后发现问题 [低]

**场景**：
- 替换后发现某个功能丢失
- 用户习惯的某个行为改变

**缓解措施**：
- 在 `backup.txt` 记录旧 custom 的 HEAD hash
- Git reflog 保留 30 天

**应对计划**：
```bash
# 从备份恢复
BACKUP_HASH=$(cat .trellis/tasks/06-25-rebuild-custom-on-upstream/backup.txt | grep "Custom backup:" | cut -d' ' -f3)
git branch custom-old $BACKUP_HASH
git reset --hard custom-old
git push --force-with-lease origin custom
```

---

### R5: Force-push 风险 [低]

**场景**：
- `--force-with-lease` 失败（远程 custom 已被其他人更新）
- 强制推送覆盖了未拉取的远程提交

**缓解措施**：
- 这是个人 fork，无协作者
- 使用 `--force-with-lease` 而非 `--force`
- 推送前检查 `git status` 和远程状态

**应对计划**：
- 如果 `--force-with-lease` 失败：
  ```bash
  git fetch origin custom
  git log origin/custom..custom  # 检查差异
  # 确认后使用 --force (个人 fork 安全)
  ```

---

## Performance Considerations

### 构建时间预估

| 操作 | 预估时间 | 备注 |
|------|---------|------|
| 步骤 1-6 (应用定制) | 20-30 分钟 | 主要是手动编辑和验证 |
| 步骤 7 (编译验证) | 5-10 分钟 | 取决于机器性能 |
| 步骤 8 (分支替换) | <1 分钟 | Git 操作 |
| **总计** | **30-45 分钟** | 不含问题排查时间 |

### 优化措施

1. **并行验证**：
   - 不需要等待完整编译，可以在编译时准备下一步
   
2. **增量编译**：
   - 每步修改后只编译受影响的模块
   - 最后一次全量编译验证

3. **缓存利用**：
   - Gradle build cache 和 configuration cache 生效
   - 预计比首次编译快 40-60%

---

## Rollback Strategy

### 回滚点设计

```
Checkpoint 0: custom 分支当前 HEAD (90c71eb9)
              ↓
         创建 custom-rebuild
              ↓
Checkpoint 1: Trellis 集成完成
              ↓
Checkpoint 2: Firebase 移除完成
              ↓
Checkpoint 3: 构建优化完成
              ↓
Checkpoint 4: NDK/签名配置完成
              ↓
Checkpoint 5: 验证通过
              ↓
         替换 custom 分支
```

### 各阶段回滚方案

**阶段 1-4 失败** (应用定制过程中)：
```bash
# 停留在 custom-rebuild 分支
# 选项 A: 修复问题后继续
git add <fixed-files>
git commit --amend  # 或新建修复提交

# 选项 B: 重置到上一个 checkpoint
git reset --hard HEAD~1

# 选项 C: 完全中止
git checkout custom
git branch -D custom-rebuild
```

**阶段 5 失败** (验证失败)：
```bash
# 选项 A: 检查并补充遗漏的定制
# 分析构建错误 -> 识别缺失定制 -> 补充提交

# 选项 B: 中止任务
git checkout custom
git branch -D custom-rebuild
# custom 分支保持不变
```

**阶段 6 后发现问题** (已替换分支)：
```bash
# 从备份 hash 恢复
BACKUP_HASH="90c71eb9"  # 或从 backup.txt 读取
git branch custom-old $BACKUP_HASH
git checkout custom-old
git branch -D custom
git branch -m custom-old custom
git push --force-with-lease origin custom
```

---

## Testing Strategy

### 验证层次

```
Level 1: 语法和配置正确性
  └─> ./gradlew tasks
      (确保 Gradle 配置可解析)

Level 2: 编译成功
  └─> ./gradlew :app:assembleDebug
      (确保代码可编译)

Level 3: 单元测试通过
  └─> ./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest
      (确保核心逻辑正确)

Level 4: 快速健康检查 (可选)
  └─> git log --oneline -10
      git diff master..custom-rebuild
      (人工检查提交历史和差异范围)
```

### 验证范围说明

**包含**：
- ✅ Gradle 配置正确性
- ✅ 编译通过（Debug 变体）
- ✅ 单元测试通过
- ✅ 提交历史清晰

**不包含**：
- ❌ Release 构建（需要签名文件）
- ❌ UI 测试 / 集成测试（时间成本高）
- ❌ 功能完整性测试（手动验证，不在自动化范围）
- ❌ 性能测试

**理由**：
- 这是低风险的配置迁移，非功能开发
- Debug 构建 + 单元测试已覆盖 80% 的潜在问题
- 完整测试可在日常使用中逐步验证

---

## Alternative Approaches Considered

### 方案 A: 交互式 Rebase (rejected)

```bash
git rebase -i master
# 逐个处理 81 个提交
```

**拒绝原因**：
- 需要解决 50+ 个冲突
- 预计耗时 2-3 小时
- 历史提交质量参差不齐（多次临时修复）
- 投入产出比低

---

### 方案 B: Squash Merge (rejected)

```bash
git merge --squash custom
git commit -m "chore: apply all customizations"
```

**拒绝原因**：
- 单个巨大提交，难以理解
- 无法按类别验证
- 问题排查困难

---

### 方案 C: Git Filter-Repo (rejected)

使用 `git filter-repo` 重写历史，移除 Firebase 相关提交

**拒绝原因**：
- 过于复杂，风险高
- 仍需要手动处理其他定制
- 不适合小规模 fork

---

## Implementation Notes

### 关键文件清单

**必须检查的文件**：
```
.claude/hooks/inject-workflow-state.py
.claude/hooks/session-start.py
.codex/hooks/inject-workflow-state.py
.opencode/commands/trellis/start.md
.trellis/.version
.trellis/.template-hashes.json
.trellis/workflow.md
app/build.gradle.kts
gradle/libs.versions.toml
gradle.properties
web/build.gradle.kts
.gitignore
```

**可能需要删除的文件**：
```
app/src/main/res/xml/remote_config_defaults.xml
```

### 预期的 Git 历史

```
* feat(trellis): 集成 Trellis v0.6.4 with Kiro 平台支持
* chore: 移除 Firebase 依赖
* build: 优化 Gradle 构建配置
* build(app): 限制 NDK ABI 为 arm64-v8a
* chore: 配置 release 签名
* <-- master (upstream HEAD)
```

5-6 个清晰的提交，每个代表一类定制。

---

## Success Criteria

- [ ] 新分支 `custom-rebuild` 创建成功
- [ ] 所有 6 类定制按步骤应用
- [ ] 每个定制都有独立提交，提交信息清晰
- [ ] `./gradlew :app:assembleDebug` 成功
- [ ] `./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest` 全部通过
- [ ] `git diff master..custom-rebuild` 输出合理（仅包含定制修改）
- [ ] 旧 custom 的 HEAD hash 记录在 `backup.txt`
- [ ] 新 custom 分支已推送到 origin
- [ ] 旧 custom 分支已删除（本地和远程）

---

## Related Documents

- `prd.md` - 需求和决策记录
- `implement.md` - 详细实施步骤
- `FORK.md` - Fork 工作流文档
- `scripts/sync-upstream.sh` - 日常同步脚本
