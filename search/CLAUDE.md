[根目录](../CLAUDE.md) > **search**

# search 模块

## 模块职责

搜索功能 SDK，为 AI 聊天提供联网搜索和网页抓取能力。集成了 14 种搜索服务提供商，统一抽象为 `SearchService` 接口，支持搜索和页面抓取两种操作。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.search`），不包含独立入口。由 `app` 模块的 `SearchTools` 注册为 AI 工具调用。

## 架构说明

### 目录结构

```
search/src/main/java/me/rerere/search/
├── SearchService.kt           # 核心接口 + 数据模型 + 服务选项
├── BingSearchService.kt       # Bing 本地搜索（无 API Key）
├── BochaSearchService.kt      # 博查搜索
├── BraveSearchService.kt      # Brave 搜索
├── ExaSearchService.kt        # Exa 搜索
├── FirecrawlSearchService.kt  # Firecrawl 抓取服务
├── JinaSearchService.kt       # Jina 搜索/阅读器
├── LinkUpService.kt           # LinkUp 搜索
├── MetasoSearchService.kt     # 秘塔搜索
├── OllamaSearchService.kt     # Ollama 搜索
├── PerplexitySearchService.kt # Perplexity 搜索
├── RikkaHubSearchService.kt   # RikkaHub 内置搜索
├── SearXNGService.kt          # SearXNG 自建搜索
├── TavilySearchService.kt     # Tavily 搜索
└── ZhipuSearchService.kt      # 智谱搜索
```

### SearchService 接口

```kotlin
interface SearchService<T : SearchServiceOptions> {
    val name: String
    val parameters: InputSchema?          // 搜索参数 schema
    val scrapingParameters: InputSchema?  // 抓取参数 schema
    fun Description()                     // Compose UI 描述
    suspend fun search(params, commonOptions, serviceOptions): Result<SearchResult>
    suspend fun scrape(params, commonOptions, serviceOptions): Result<ScrapedResult>
}
```

### 服务提供商列表

| 服务 | 配置项 | 搜索 | 抓取 |
|------|--------|------|------|
| Bing (本地) | 无需 API Key | 支持 | 不支持 |
| RikkaHub | apiKey, depth | 支持 | 支持 |
| Tavily | apiKey, depth | 支持 | 支持 |
| Exa | apiKey | 支持 | 不支持 |
| 智谱 (Zhipu) | apiKey | 支持 | 不支持 |
| SearXNG | url, engines, language, auth | 支持 | 不支持 |
| LinkUp | apiKey, depth | 支持 | 不支持 |
| Brave | apiKey | 支持 | 不支持 |
| 秘塔 (Metaso) | apiKey | 支持 | 不支持 |
| Ollama | apiKey | 支持 | 不支持 |
| Perplexity | apiKey, maxTokensPerPage | 支持 | 不支持 |
| Firecrawl | apiKey | 支持 | 支持 |
| Jina | apiKey | 支持 | 支持 |
| 博查 (Bocha) | apiKey, summary | 支持 | 不支持 |

### 数据模型

- `SearchResult` -- 搜索结果（可选 answer + items 列表）
  - `SearchResultItem(title, url, text)`
- `ScrapedResult` -- 抓取结果
  - `ScrapedResultUrl(url, content, metadata?)`
- `SearchCommonOptions(resultSize)` -- 通用搜索选项
- `SearchServiceOptions` -- 密封类，14 种服务配置

## 对外接口

| 接口 | 说明 |
|------|------|
| `SearchService.getService(options)` | 根据配置获取对应的搜索服务实例 |
| `SearchService.search(...)` | 执行搜索 |
| `SearchService.scrape(...)` | 执行网页抓取 |
| `SearchServiceOptions.TYPES` | 所有可用服务类型映射 |
| `SearchServiceOptions.DEFAULT` | 默认搜索配置（Bing 本地） |

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `:ai` | InputSchema 类型定义 |
| `okhttp` | HTTP 客户端 |
| `kotlinx-serialization-json` | JSON 序列化 |
| `kotlinx-coroutines-core` | 协程支持 |
| `jsoup` | HTML 解析（export as `api`） |
| Compose BOM + Material3 | 服务描述 UI |

## 测试与质量

- `src/test/java/me/rerere/search/ExampleUnitTest.kt` -- 示例单元测试

缺口：各搜索服务缺少 Mock 测试，依赖外部 API 的集成测试也缺失。

## 常见问题 (FAQ)

**Q: 如何新增搜索服务？**
A: 1) 在 `SearchServiceOptions` 中添加新的配置子类；2) 实现 `SearchService` 接口；3) 在 `SearchService.getService()` 和 `SearchServiceOptions.TYPES` 中注册。

**Q: Bing 本地搜索如何工作？**
A: 通过直接请求 Bing 搜索页面并解析 HTML 结果，无需 API Key，但可能受限于反爬机制。

## 相关文件清单

- `search/build.gradle.kts` -- 构建配置
- `search/src/main/java/me/rerere/search/` -- 主要源码（15 个文件）
- `search/src/test/` -- 单元测试
- `search/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
