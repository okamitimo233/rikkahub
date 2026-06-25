# Implementation Plan

## Execution Strategy

使用**分类批量应用**策略：从 master HEAD 创建新分支，逐类应用定制修改。

## Pre-Implementation Checklist

- [ ] 确认当前工作区干净（`git status` 无未提交改动）
- [ ] 确认 master 分支已是最新（已执行 fetch upstream）
- [ ] 记录当前 custom 分支的 HEAD（备份参考点）

## Implementation Steps

### 1. 创建工作分支

```bash
# 记录当前 custom 的 HEAD
CUSTOM_BACKUP=$(git rev-parse custom)
echo "Custom backup: $CUSTOM_BACKUP" > .trellis/tasks/06-25-rebuild-custom-on-upstream/backup.txt

# 从 master HEAD 创建新分支
git checkout master
git checkout -b custom-rebuild
```

**验证**：`git log --oneline -1` 应显示 master 的最新提交

---

### 2. 应用 Trellis 工作流集成

**来源**：custom 分支最新 3 个提交
- `90c71eb9` - chore(trellis): 升级版本至 0.6.4 并更新模板哈希
- `858a00e0` - docs(trellis): 重构工作流文档，简化为通用架构
- `41a0c2d3` - feat(trellis): 添加 Kiro 平台支持

**操作**：
```bash
# 检查这些文件在 master 中的状态
git diff master..custom -- .claude/hooks/inject-workflow-state.py .claude/hooks/session-start.py .codex/hooks/inject-workflow-state.py .trellis/.version .trellis/.template-hashes.json .trellis/workflow.md .opencode/commands/trellis/start.md

# 如果差异合理，批量应用
git checkout custom -- .claude/hooks/inject-workflow-state.py
git checkout custom -- .claude/hooks/session-start.py  
git checkout custom -- .codex/hooks/inject-workflow-state.py
git checkout custom -- .trellis/.version
git checkout custom -- .trellis/.template-hashes.json
git checkout custom -- .trellis/workflow.md
git checkout custom -- .opencode/commands/trellis/start.md

git add .claude/hooks/ .codex/hooks/ .trellis/ .opencode/commands/trellis/
git commit -m "feat(trellis): 集成 Trellis 工作流 v0.6.4 with Kiro 平台支持"
```

**验证**：检查文件权限和内容

---

### 3. 移除 Firebase 依赖

**目标文件**：
- `app/build.gradle.kts` - 移除 Firebase 插件
- `gradle/libs.versions.toml` - 移除 Firebase 版本声明
- `app/src/main/res/xml/remote_config_defaults.xml` - 删除（如果存在）

**操作**：
```bash
# 检查 master 中的 Firebase 配置
grep -n "firebase" app/build.gradle.kts || echo "Not found"
grep -n "firebase" gradle/libs.versions.toml || echo "Not found"

# 手动编辑这两个文件，移除 Firebase 相关行
# app/build.gradle.kts: 移除 google-services 和 firebase-crashlytics 插件
# gradle/libs.versions.toml: 移除 google-services, firebase-bom, firebase-crashlytics 版本

# 删除 remote config 文件（如果存在）
rm -f app/src/main/res/xml/remote_config_defaults.xml

git add app/build.gradle.kts gradle/libs.versions.toml
git add app/src/main/res/xml/ || true
git commit -m "chore: 移除 Firebase 依赖以减小 APK 体积"
```

**验证**：
```bash
grep -i firebase app/build.gradle.kts && echo "Firebase still present!" || echo "Firebase removed"
```

---

### 4. 应用 Gradle 构建优化

**目标文件**：
- `gradle.properties` - JVM 参数、并行构建、configuration cache
- `app/build.gradle.kts` - 可能的构建配置
- `web/build.gradle.kts` - pnpm 执行配置

**操作**：
```bash
# 从 custom 分支提取构建优化
git show custom:gradle.properties > /tmp/custom-gradle.properties
git show master:gradle.properties > /tmp/master-gradle.properties

# 对比差异
diff /tmp/master-gradle.properties /tmp/custom-gradle.properties

# 手动合并关键优化：
# - org.gradle.jvmargs（调优的 JVM 参数）
# - org.gradle.parallel=true
# - org.gradle.caching=true  
# - org.gradle.configuration-cache=true
# - 其他性能相关配置

# 编辑 gradle.properties
# 编辑 web/build.gradle.kts 添加 hasZsh 检测逻辑（如果 master 没有）

git add gradle.properties web/build.gradle.kts
git commit -m "build: 优化 Gradle 构建性能配置"
```

**验证**：
```bash
./gradlew --version
```

---

### 5. 限制 NDK ABI 为 arm64-v8a

**目标文件**：
- `app/build.gradle.kts` - ndk.abiFilters

**操作**：
```bash
# 检查 master 的 NDK 配置
grep -A 5 "ndk {" app/build.gradle.kts

# 如果包含多个 ABI，修改为只保留 arm64-v8a
# 在 defaultConfig 块中：
# ndk {
#     abiFilters += listOf("arm64-v8a")
# }

git add app/build.gradle.kts
git commit -m "build(app): 限制 NDK ABI 为 arm64-v8a"
```

**验证**：
```bash
grep -A 3 "ndk {" app/build.gradle.kts
```

---

### 6. 配置签名和发布

**目标文件**：
- `.gitignore` - 添加 release keystore 忽略
- `app/build.gradle.kts` - 签名配置读取逻辑（如果需要）

**操作**：
```bash
# 检查 .gitignore 是否已包含 keystore 忽略
grep -n "keystore" .gitignore || echo "# Signing\n*.keystore\napp/app.key\nrikkahub-release.keystore" >> .gitignore

# 检查 app/build.gradle.kts 的 signingConfigs
# 确保有从 local.properties 读取签名配置的逻辑

git add .gitignore app/build.gradle.kts
git commit -m "chore: 配置 release 签名和 gitignore"
```

**验证**：
```bash
grep keystore .gitignore
```

---

### 7. 验证构建

```bash
# 清理并构建
./gradlew clean
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest
```

**预期结果**：
- 编译成功，生成 APK
- 所有单元测试通过

**如果失败**：
- 检查错误日志
- 修复问题
- 重新验证

---

### 8. 替换分支

```bash
# 确认验证通过后执行
git checkout master  # 或任何非 custom 的分支
git branch -D custom
git branch -m custom-rebuild custom
git push --force-with-lease origin custom
```

**验证**：
```bash
git branch
git log --oneline -5
```

---

## Rollback Points

### 如果步骤 2-6 某一步失败
- 保留 custom-rebuild 分支
- 检查并修复问题
- 继续从失败步骤执行

### 如果验证失败（步骤 7）
- 保留 custom-rebuild 分支
- 返回步骤 2-6 检查遗漏的定制
- 或中止任务，custom 分支保持不变

### 如果替换后发现问题（步骤 8 之后）
- 从备份 commit 恢复：
  ```bash
  git branch custom-old <backup-commit-hash>
  git reset --hard custom-old
  git push --force-with-lease origin custom
  ```

---

## Validation Commands

### 编译验证
```bash
./gradlew :app:assembleDebug
```

### 单元测试验证
```bash
./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest
```

### 快速健康检查
```bash
./gradlew tasks | head -20  # 确保 Gradle 配置正确
git log --oneline -10       # 确认提交历史清晰
git diff master..custom     # 确认定制范围合理
```

---

## Post-Implementation Notes

记录以下信息到 `migration-log.md`：

- 应用的定制列表和对应的新提交 hash
- 跳过的定制（Firebase 移除除外）及原因
- 上游已包含的功能（Markdown 表格、custom params）
- 验证结果
- 遇到的问题和解决方案

---

## Estimated Time

- 步骤 1-6：20-30 分钟
- 步骤 7（验证）：5-10 分钟
- 步骤 8（替换）：2 分钟
- **总计**：30-45 分钟
