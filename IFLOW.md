# RikkaHub 项目文档

## 项目概述

RikkaHub 是一个原生 Android LLM 聊天客户端，支持切换不同的 AI 供应商进行对话。该项目使用现代 Android 技术栈构建，采用 Material You 设计风格，提供丰富的功能包括多模态输入、MCP 支持、Markdown 渲染、搜索功能等。

### 主要技术栈

- **开发语言**: Kotlin 2.2.21
- **UI 框架**: Jetpack Compose + Material You
- **架构模式**: MVVM + 依赖注入
- **依赖注入**: Koin 4.1.1
- **数据存储**: Room 2.8.4 + DataStore 1.2.0
- **网络请求**: OkHttp 5.1.0 + Retrofit 3.0.0 + Ktor 3.3.3
- **JSON 序列化**: kotlinx.serialization 1.9.0
- **图片加载**: Coil 3.3.0
- **导航**: Navigation Compose 2.9.6
- **测试**: JUnit + AndroidX Test + Espresso

### 项目结构

这是一个多模块的 Android 项目，包含以下模块：

- **app**: 主应用模块，包含 UI、数据层、依赖注入配置
- **ai**: AI SDK 和供应商集成（OpenAI、Google、Anthropic）
- **highlight**: 代码高亮功能模块
- **search**: 搜索功能模块（支持 Exa、Tavily、Zhipu 等）
- **tts**: 文本转语音功能模块
- **document**: 文档处理模块
- **common**: 通用工具和共享代码
- **locale-tui**: Python TUI 工具，用于管理 Android 字符串资源翻译

### 应用配置

- **包名**: `me.rerere.rikkahub`
- **最低 SDK**: 26 (Android 8.0)
- **目标 SDK**: 36
- **编译 SDK**: 36
- **当前版本**: 1.7.8 (versionCode: 125)

## 构建和运行

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 36
- Gradle 8.13.0

### 构建前准备

1. **配置 Firebase**
   - 在 `app/` 目录下添加 `google-services.json` 文件
   - 该文件已在 `.gitignore` 中，不会被提交到仓库

2. **配置签名（可选）**
   - 在项目根目录创建 `local.properties` 文件
   - 添加以下配置：
     ```properties
     storeFile=/path/to/keystore.jks
     storePassword=your_store_password
     keyAlias=your_key_alias
     keyPassword=your_key_password
     ```

### 构建命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 构建 AAB（Android App Bundle）
./gradlew bundleRelease

# 同时构建 APK 和 AAB
./gradlew buildAll

# 清理构建产物
./gradlew clean
```

### 运行测试

```bash
# 运行单元测试
./gradlew test

# 运行 Android 设备测试
./gradlew connectedAndroidTest

# 运行特定模块的测试
./gradlew :app:test
./gradlew :ai:test
```

### 运行 locale-tui 工具

locale-tui 是一个 Python TUI 工具，用于管理 Android 字符串资源的翻译：

```bash
cd locale-tui

# 使用 uv 运行（推荐）
uv run python src/main.py

# 或使用 pip 安装依赖后运行
pip install -e .
locale-tui
```

配置文件：
- `config.yml`: 模块列表、语言列表和翻译设置
- `.env`: OpenAI API 配置
  ```env
  OPENAI_API_KEY=your_api_key
  OPENAI_BASE_URL=https://api.openai.com/v1
  ```

## 开发规范

### 代码风格

- **缩进**: 4 空格
- **行长度限制**: 120 字符
- **命名规范**:
  - 类和对象: PascalCase（例如：`ChatViewModel`）
  - 函数和属性: camelCase（例如：`sendMessage`）
  - 资源文件: snake_case（例如：`ic_launcher.xml`）
  - Composable 函数: UpperCamelCase（例如：`ChatScreen`）
  - 页面组件: 以 `Page` 结尾（例如：`SettingProviderPage`）

### 文件组织

主应用模块 (`app`) 的包结构：

```
me.rerere.rikkahub/
├── data/           # 数据层（数据库、数据源、模型）
├── di/             # 依赖注入模块
├── service/        # 业务逻辑服务
├── ui/             # UI 层（Compose 组件、ViewModel）
├── utils/          # 工具类
└── RikkaHubApp.kt  # 应用入口
```

### Compose 开发规范

- 使用 Material 3 设计组件
- 启用实验性 API 时在编译器选项中声明
- 遵循单一职责原则，每个 Composable 负责单一功能
- 使用 Koin 进行依赖注入，避免手动创建 ViewModel

### 测试规范

- 单元测试放在 `src/test/` 目录
- Android 设备测试放在 `src/androidTest/` 目录
- 测试文件命名以 `Test.kt` 结尾（例如：`ChatViewModelTest.kt`）
- 测试应覆盖关键业务逻辑和数据解析

### 模块间依赖

- `app` 模块依赖所有功能模块（`ai`、`document`、`highlight`、`search`、`tts`、`common`）
- 功能模块应保持独立，通过 `common` 模块共享工具代码
- 避免循环依赖

## 贡献指南

### 提交规范

使用 Conventional Commits 格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

类型（type）：
- `feat`: 新功能
- `fix`: 修复 bug
- `chore`: 构建/工具链相关
- `refactor`: 重构
- `docs`: 文档更新
- `style`: 代码格式调整
- `test`: 测试相关

示例：
```
feat(chat): 添加消息分支功能

允许用户从历史消息创建新的对话分支，实现类似 ChatGPT 的分支功能。

Closes #123
```

### Pull Request 规范

- PR 应专注于单一功能或修复
- 提供清晰的描述，说明改动内容和原因
- UI 相关的改动应提供截图
- 关联相关的 Issue
- 确保所有测试通过
- 运行 `./gradlew test` 和 `./gradlew lint` 检查代码质量

### 个人分支说明

这是一个个人使用的分支仓库，你可以自由添加任何自定义功能，不受原项目 PR 限制约束。

可以自由进行以下操作：
- 添加新功能
- 修改现有功能
- 更新翻译或添加新语言
- 进行代码重构
- 任何你觉得需要改动的地方

### 安全注意事项

- **不要提交敏感信息**：
  - API 密钥
  - 签名文件（keystore）
  - `google-services.json`
  - `local.properties`

- **密钥管理**：
  - 使用 Android Keystore 存储敏感数据
  - API 密钥应通过安全存储获取，不要硬编码
  - 使用 `local.properties` 存储签名配置（已在 `.gitignore` 中）

## 依赖管理

项目使用 Gradle 版本目录（Version Catalog）管理依赖，配置文件位于 `gradle/libs.versions.toml`。

添加新依赖的步骤：

1. 在 `gradle/libs.versions.toml` 中添加版本和库定义
2. 在相应模块的 `build.gradle.kts` 中引用

示例：

```toml
# gradle/libs.versions.toml
[versions]
some-library = "1.0.0"

[libraries]
some-library = { group = "com.example", name = "some-library", version.ref = "some-library" }
```

```kotlin
// module/build.gradle.kts
dependencies {
    implementation(libs.some.library)
}
```

## 常见问题

### 构建失败

1. **缺少 `google-services.json`**
   - 从 Firebase 控制台下载配置文件
   - 放置在 `app/` 目录下

2. **依赖解析失败**
   - 检查网络连接
   - 尝试清理缓存：`./gradlew clean --no-daemon`

3. **KSP 生成失败**
   - 删除 `app/build/` 目录后重新构建
   - 确保 Room 数据库注解正确

### locale-tui 使用问题

1. **无法连接 OpenAI API**
   - 检查 `.env` 文件配置
   - 确认 API 密钥有效

2. **翻译条目显示异常**
   - 检查 `config.yml` 中的模块路径配置
   - 确保模块包含有效的 `strings.xml` 文件

## 相关资源

- **项目主页**: https://rikka-ai.com
- **GitHub 仓库**: https://github.com/re-ovo/rikkahub
- **Discord 社区**: https://discord.gg/9weBqxe5c4
- **QQ 群**: https://qm.qq.com/q/I8MSU0FkOu

## 许可证

本项目采用开源许可证，详见 [LICENSE](LICENSE) 文件。