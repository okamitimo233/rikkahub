[根目录](../CLAUDE.md) > **tts**

# tts 模块

## 模块职责

文本转语音（Text-to-Speech）模块，为 AI 聊天的助手回复提供语音朗读能力。集成了 6 种 TTS 提供商，实现了完整的文本分片、预取合成、排队播放与状态管理流水线。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.tts`），不包含独立入口。由 `app` 模块创建 `TtsController` 实例供 UI 层使用。

## 架构说明

### 目录结构

```
tts/src/main/java/me/rerere/tts/
├── controller/               # 播放控制器
│   ├── TtsController.kt      # TTS 主控制器（分片、预取、排队播放）
│   ├── TextChunker.kt        # 文本分片器
│   ├── TtsSynthesizer.kt     # 合成调度器
│   └── AudioPlayer.kt        # 音频播放器（ExoPlayer）
├── model/                    # 数据模型
│   ├── TTSRequest.kt         # TTS 请求（text）+ AudioFormat 枚举
│   ├── TTSResponse.kt        # TTS 响应（audioData, format, sampleRate）
│   └── PlaybackState.kt      # 播放状态（status, position, duration, speed, chunks）
└── provider/                 # TTS 提供商
    ├── TTSProvider.kt        # Provider 接口
    ├── TTSManager.kt         # Provider 管理器（路由分发）
    ├── TTSProviderSetting.kt # Provider 配置密封类
    └── providers/            # 具体实现
        ├── OpenAITTSProvider.kt
        ├── GeminiTTSProvider.kt
        ├── SystemTTSProvider.kt     # Android 系统 TTS
        ├── MiniMaxTTSProvider.kt
        ├── QwenTTSProvider.kt
        └── GroqTTSProvider.kt
```

### TtsController（核心控制器）

播放流水线：

```
speak(text) -> TextChunker.split() -> 分片队列
                                       ↓
                              prefetchFrom() -> TtsSynthesizer 异步预取
                                       ↓
                              startWorker() -> 逐片播放
                                       ↓
                              AudioPlayer.play() -> ExoPlayer
```

特性：
- 文本自动分片（最大 160 字符/片）
- 预取窗口（默认 4 片）
- 支持暂停/恢复/快进/跳过/变速
- 统一的 `PlaybackState` 状态流

### TTSProvider 接口

```kotlin
interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(context, providerSetting, request): Flow<AudioChunk>
}
```

### TTS 提供商列表

| 提供商 | 配置项 | 说明 |
|--------|--------|------|
| OpenAI | apiKey, baseUrl, model, voice | OpenAI TTS API |
| Gemini | apiKey, baseUrl, model, voiceName | Google Gemini TTS |
| System | speechRate, pitch | Android 系统 TTS（无需 API） |
| MiniMax | apiKey, baseUrl, model, voiceId, emotion, speed | MiniMax 语音合成 |
| Qwen | apiKey, baseUrl, model, voice, languageType | 通义千问 TTS |
| Groq | apiKey, baseUrl, model, voice | Groq TTS |

### 播放状态

```kotlin
enum PlaybackStatus { Idle, Buffering, Playing, Paused, Ended, Error }

data class PlaybackState(
    val status: PlaybackStatus,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val currentChunkIndex: Int,  // 1-based
    val totalChunks: Int,
    val errorMessage: String?
)
```

## 对外接口

### TtsController API

| 方法 | 说明 |
|------|------|
| `setProvider(provider)` | 设置/清除 TTS 提供商 |
| `speak(text, flush)` | 朗读文本（flush=true 重置，false 追加） |
| `pause()` / `resume()` | 暂停/恢复 |
| `fastForward(ms)` | 快进指定毫秒 |
| `setSpeed(speed)` | 设置播放速度 |
| `skipNext()` | 跳过下一段 |
| `stop()` | 停止并清空 |
| `dispose()` | 释放资源 |

### 状态流

| StateFlow | 类型 | 说明 |
|-----------|------|------|
| `isAvailable` | `Boolean` | 是否有可用 Provider |
| `isSpeaking` | `Boolean` | 是否正在朗读 |
| `error` | `String?` | 错误信息 |
| `currentChunk` | `Int` | 当前分片索引 |
| `totalChunks` | `Int` | 总分片数 |
| `playbackState` | `PlaybackState` | 统一播放状态 |

### TTSManager

| 方法 | 说明 |
|------|------|
| `generateSpeech(providerSetting, request)` | 根据配置自动路由到对应 Provider |

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `:common` | 通用工具库 |
| `okhttp` | HTTP 客户端（API 调用） |
| `kotlinx-serialization-json` | JSON 序列化 |
| `kotlinx-coroutines-core` | 协程 |
| `androidx.media3.exoplayer` | 音频播放 |
| `androidx.media3.ui` / `common` | Media3 UI 与通用模块 |
| Compose BOM + Material3 | Compose UI |

## 数据模型

- `TTSRequest(text)` -- TTS 请求
- `TTSResponse(audioData, format, sampleRate, duration, metadata)` -- TTS 响应
- `AudioChunk(data, format, sampleRate, isLast, metadata)` -- 音频数据块（流式）
- `AudioFormat` -- 枚举：MP3, WAV, OGG, AAC, OPUS, PCM
- `PlaybackState` -- 播放状态

## 测试与质量

- `src/test/java/me/rerere/tts/ExampleUnitTest.kt` -- 示例单元测试
- `src/androidTest/` -- 仪器测试

缺口：TtsController 的状态机逻辑、TextChunker 分片策略缺少单元测试。

## 常见问题 (FAQ)

**Q: 如何新增 TTS 提供商？**
A: 1) 在 `TTSProviderSetting` 中添加配置子类；2) 实现 `TTSProvider` 接口；3) 在 `TTSManager` 中注册路由；4) 在 `TTSProviderSetting.Types` 中添加类引用。

**Q: 系统 TTS 和第三方 TTS 有什么区别？**
A: 系统 TTS 使用 Android `TextToSpeech` API，不需要网络和 API Key，但语音质量较低。第三方 TTS 通过网络 API 调用，语音质量更高但需要配置。

## 相关文件清单

- `tts/build.gradle.kts` -- 构建配置
- `tts/src/main/java/me/rerere/tts/` -- 主要源码（12 个文件）
- `tts/src/test/` -- 单元测试
- `tts/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
